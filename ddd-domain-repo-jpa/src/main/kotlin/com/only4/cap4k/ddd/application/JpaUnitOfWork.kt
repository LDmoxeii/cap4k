package com.only4.cap4k.ddd.application

import com.only4.cap4k.ddd.core.application.PersistIntent
import com.only4.cap4k.ddd.core.application.UnitOfWork
import com.only4.cap4k.ddd.core.application.UnitOfWorkInterceptor
import com.only4.cap4k.ddd.core.domain.id.IdentifierStrategyRegistry
import com.only4.cap4k.ddd.core.domain.id.MapBackedIdentifierStrategyRegistry
import com.only4.cap4k.ddd.core.domain.repo.AggregateLoadPlan
import com.only4.cap4k.ddd.core.domain.repo.PersistListenerManager
import com.only4.cap4k.ddd.core.domain.repo.PersistType
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import org.hibernate.Hibernate
import org.springframework.data.jpa.repository.support.JpaEntityInformationSupport
import org.springframework.data.repository.core.EntityInformation
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.util.concurrent.ConcurrentHashMap

private enum class UnitOfWorkEntryKind {
    CREATE,
    EXISTING,
    REMOVE,
}

private data class UnitOfWorkEntry(
    val entity: Any,
    val kind: UnitOfWorkEntryKind,
)

private class InsertionOrderedIdentitySet<E : Any> : AbstractMutableSet<E>() {
    private val entries = LinkedHashMap<ObjectIdentityKey, E>()

    override val size: Int
        get() = entries.size

    override fun add(element: E): Boolean =
        entries.put(ObjectIdentityKey(element), element) == null

    override fun contains(element: E): Boolean =
        entries.containsKey(ObjectIdentityKey(element))

    override fun iterator(): MutableIterator<E> = entries.values.iterator()

    override fun remove(element: E): Boolean =
        entries.remove(ObjectIdentityKey(element)) != null
}

private class PendingEntrySet {
    private val entries = LinkedHashMap<ObjectIdentityKey, UnitOfWorkEntry>()

    fun persist(entity: Any, intent: PersistIntent): UnitOfWorkEntry {
        val key = ObjectIdentityKey(entity)
        val next = intent.toUnitOfWorkEntryKind()
        val current = entries[key]
        val merged = when (current?.kind) {
            null -> UnitOfWorkEntry(entity, next)
            UnitOfWorkEntryKind.CREATE -> UnitOfWorkEntry(entity, UnitOfWorkEntryKind.CREATE)
            UnitOfWorkEntryKind.EXISTING -> when (next) {
                UnitOfWorkEntryKind.EXISTING -> UnitOfWorkEntry(entity, UnitOfWorkEntryKind.EXISTING)
                UnitOfWorkEntryKind.CREATE ->
                    error("UoW intent conflict: EXISTING cannot become CREATE for the same instance")
                UnitOfWorkEntryKind.REMOVE -> error("persist cannot register REMOVE intent")
            }
            UnitOfWorkEntryKind.REMOVE ->
                error("UoW intent conflict: REMOVE cannot become ${next.name} for the same instance")
        }
        entries[key] = merged
        return merged
    }

    fun remove(entity: Any) {
        val key = ObjectIdentityKey(entity)
        val current = entries[key]
        when (current?.kind) {
            null -> entries[key] = UnitOfWorkEntry(entity, UnitOfWorkEntryKind.REMOVE)
            UnitOfWorkEntryKind.CREATE -> entries.remove(key)
            UnitOfWorkEntryKind.EXISTING -> entries[key] = UnitOfWorkEntry(entity, UnitOfWorkEntryKind.REMOVE)
            UnitOfWorkEntryKind.REMOVE -> Unit
        }
    }

    fun drain(): List<UnitOfWorkEntry> {
        val changes = entries.values.toList()
        entries.clear()
        return changes
    }
}

private fun PersistIntent.toUnitOfWorkEntryKind(): UnitOfWorkEntryKind = when (this) {
    PersistIntent.CREATE -> UnitOfWorkEntryKind.CREATE
    PersistIntent.EXISTING -> UnitOfWorkEntryKind.EXISTING
}

private data class SaveInput(
    val entries: List<UnitOfWorkEntry>,
    val persistedEntities: Set<Any>,
    val removedEntities: Set<Any>,
    val processedEntities: Set<Any>,
)

private data class FlushResult(
    val created: InsertionOrderedIdentitySet<Any> = InsertionOrderedIdentitySet(),
    val existing: InsertionOrderedIdentitySet<Any> = InsertionOrderedIdentitySet(),
    val deleted: InsertionOrderedIdentitySet<Any> = InsertionOrderedIdentitySet(),
    val refreshList: MutableList<Any> = mutableListOf(),
    var needsFlush: Boolean = false,
)

private data class EntityIdentity(
    val entityType: Class<*>,
    val id: Any,
)

/**
 * 基于Jpa的UnitOfWork实现
 *
 * @author LD_moxeii
 * @date 2025/07/28
 */
open class JpaUnitOfWork(
    private val uowInterceptors: List<UnitOfWorkInterceptor>,
    private val persistListenerManager: PersistListenerManager,
    private val supportEntityInlinePersistListener: Boolean,
    idStrategyRegistry: IdentifierStrategyRegistry = MapBackedIdentifierStrategyRegistry(emptyList()),
) : UnitOfWork, JpaRepositoryObservationRecorder {

    constructor(
        uowInterceptors: List<UnitOfWorkInterceptor>,
        persistListenerManager: PersistListenerManager,
        supportEntityInlinePersistListener: Boolean,
    ) : this(
        uowInterceptors,
        persistListenerManager,
        supportEntityInlinePersistListener,
        MapBackedIdentifierStrategyRegistry(emptyList()),
    )

    @PersistenceContext
    lateinit var entityManager: EntityManager

    private val applicationSideIdSupport = JpaApplicationSideIdSupport(idStrategyRegistry)
    private val generatedStrongIdSupport = JpaGeneratedStrongIdSupport()
    private val ownedRelationTraversal = JpaGeneratedOwnedRelationTraversal()
    private val repositoryObservationBaseline: JpaRepositoryObservationBaseline
        get() = repositoryObservationBaselineThreadLocal.get()

    companion object {
        lateinit var instance: JpaUnitOfWork

        fun fixAopWrapper(unitOfWork: JpaUnitOfWork) {
            instance = unitOfWork
        }

        private val pendingEntriesThreadLocal = ThreadLocal.withInitial { PendingEntrySet() }
        private val processingEntitiesThreadLocal = ThreadLocal.withInitial { InsertionOrderedIdentitySet<Any>() }
        private val repositoryObservationBaselineThreadLocal =
            ThreadLocal.withInitial { JpaRepositoryObservationBaseline() }

        private val entityInformationCache = ConcurrentHashMap<Class<*>, EntityInformation<*, *>>()

        @JvmStatic
        fun reset() {
            pendingEntriesThreadLocal.remove()
            processingEntitiesThreadLocal.remove()
            repositoryObservationBaselineThreadLocal.remove()
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun getEntityInformation(entityClass: Class<*>): EntityInformation<Any, Any> =
        entityInformationCache.computeIfAbsent(entityClass) {
            JpaEntityInformationSupport.getEntityInformation(it, entityManager)
        } as EntityInformation<Any, Any>

    private fun persistentEntityClass(entity: Any): Class<*> = Hibernate.getClassLazy(entity)

    protected open fun onEntitiesFlushed(
        createdEntities: Set<Any>,
        updatedEntities: Set<Any>,
        deletedEntities: Set<Any>
    ) {
        if (!supportEntityInlinePersistListener) return

        createdEntities.forEach { persistListenerManager.onChange(it, PersistType.CREATE) }
        updatedEntities.forEach { persistListenerManager.onChange(it, PersistType.UPDATE) }
        deletedEntities.forEach { persistListenerManager.onChange(it, PersistType.DELETE) }
    }

    protected open fun dirtyExistingEntities(existingEntities: Set<Any>): Set<Any> =
        JpaHibernateDirtyInspector(entityManager).dirtyManagedEntities(existingEntities)

    override fun persist(entity: Any, intent: PersistIntent) {
        val entry = pendingEntriesThreadLocal.get().persist(entity, intent)
        completeIdsForEntry(entry)
    }

    override fun remove(entity: Any) {
        pendingEntriesThreadLocal.get().remove(entity)
    }

    override fun observeRepositoryLoad(root: Any, loadPlan: AggregateLoadPlan) {
        val observed = ownedRelationTraversal.reachableOwnedEntities(root)
            .map { entity -> JpaObservedEntity(entity, observedIdentityOf(entity)) }
        repositoryObservationBaseline.record(root, observed)
    }

    internal fun observedRepositoryBaseline(): JpaRepositoryObservationBaseline =
        repositoryObservationBaseline

    private fun observedIdentityOf(entity: Any): JpaObservedIdentity? {
        val entityClass = persistentEntityClass(entity)
        val entityInformation = getEntityInformation(entityClass)
        if (entityInformation.isNew(entity)) return null
        val id = entityInformation.getId(entity) ?: return null
        return JpaObservedIdentity(entityClass, id)
    }

    private fun pushProcessingEntity(
        entity: Any,
        currentProcessedPersistenceContextEntities: MutableSet<Any>
    ): Boolean {
        val processingEntities = processingEntitiesThreadLocal.get()
        val added = processingEntities.add(entity)
        if (added) currentProcessedPersistenceContextEntities.add(entity)
        return added
    }

    private fun popProcessingEntities(currentProcessedPersistenceContextEntities: Set<Any>): Boolean {
        if (currentProcessedPersistenceContextEntities.isEmpty()) return true
        return currentProcessedPersistenceContextEntities.fold(false) { removedAny, entity ->
            processingEntitiesThreadLocal.get().remove(entity) || removedAny
        }
    }

    override fun save(propagation: Propagation) {
        val currentProcessedEntitySet = InsertionOrderedIdentitySet<Any>()
        val pendingEntries = pendingEntriesThreadLocal.get().drain()
        pendingEntries.forEach { pushProcessingEntity(it.entity, currentProcessedEntitySet) }

        val persistEntitySet = pendingEntries
            .filter { it.kind == UnitOfWorkEntryKind.CREATE || it.kind == UnitOfWorkEntryKind.EXISTING }
            .mapTo(InsertionOrderedIdentitySet()) { it.entity }
        val deleteEntitySet = pendingEntries
            .filter { it.kind == UnitOfWorkEntryKind.REMOVE }
            .mapTo(InsertionOrderedIdentitySet()) { it.entity }

        try {
            prepareApplicationSideIds(pendingEntries)
            validateSameIdentityConflicts(pendingEntries)
            uowInterceptors.forEach { it.beforeTransaction(persistEntitySet, deleteEntitySet) }

            save(
                SaveInput(
                    entries = pendingEntries,
                    persistedEntities = persistEntitySet,
                    removedEntities = deleteEntitySet,
                    processedEntities = currentProcessedEntitySet,
                ),
                propagation,
            ) { input ->
                val results = FlushResult()
                uowInterceptors.forEach { it.preInTransaction(input.persistedEntities, input.removedEntities) }

                input.entries.forEach { entry ->
                    when (entry.kind) {
                        UnitOfWorkEntryKind.CREATE -> applyCreate(entry.entity, results)
                        UnitOfWorkEntryKind.EXISTING -> applyExisting(entry.entity, results)
                        UnitOfWorkEntryKind.REMOVE -> applyRemove(entry.entity, results)
                    }
                }

                if (results.needsFlush) {
                    val dirtyExisting = dirtyExistingEntities(results.existing)
                    entityManager.flush()
                    results.refreshList.forEach { entityManager.refresh(it) }
                    onEntitiesFlushed(results.created, dirtyExisting, results.deleted)
                }

                InsertionOrderedIdentitySet<Any>().apply {
                    addAll(input.persistedEntities)
                    addAll(input.removedEntities)
                    addAll(input.processedEntities)
                }.let { allEntities ->
                    uowInterceptors.forEach { it.postEntitiesPersisted(allEntities) }
                    uowInterceptors.forEach {
                        it.postInTransaction(input.persistedEntities, input.removedEntities)
                    }
                }
            }

            uowInterceptors.forEach { it.afterTransaction(persistEntitySet, deleteEntitySet) }
        } finally {
            repositoryObservationBaselineThreadLocal.remove()
            popProcessingEntities(currentProcessedEntitySet)
        }
    }

    private fun prepareApplicationSideIds(entries: List<UnitOfWorkEntry>) {
        entries.forEach(::completeIdsForEntry)
    }

    private fun completeIdsForEntry(entry: UnitOfWorkEntry) {
        when (entry.kind) {
            UnitOfWorkEntryKind.CREATE -> {
                applicationSideIdSupport.assignMissingIds(entry.entity)
                generatedStrongIdSupport.completeCreate(entry.entity, ownedRelationTraversal)
            }
            UnitOfWorkEntryKind.EXISTING -> {
                validateObservedIdentityConsistency(entry.entity)
                applicationSideIdSupport.assignMissingIdsToOwnedRelations(entry.entity)
                generatedStrongIdSupport.completeExisting(
                    root = entry.entity,
                    traversal = ownedRelationTraversal,
                    baseline = repositoryObservationBaseline,
                )
            }
            UnitOfWorkEntryKind.REMOVE -> Unit
        }
    }

    private fun validateObservedIdentityConsistency(root: Any) {
        ownedRelationTraversal.reachableOwnedEntities(root)
            .filter { repositoryObservationBaseline.isObservedObject(it) }
            .forEach { entity ->
                val observed = repositoryObservationBaseline.identityFor(entity) ?: return@forEach
                val current = observedIdentityOf(entity)
                check(current == observed) {
                    "Observed existing entity ${observed.entityType.name} changed identity " +
                        "from ${observed.id} to ${current?.id}"
                }
            }
    }

    private fun validateSameIdentityConflicts(entries: List<UnitOfWorkEntry>) {
        val identities = LinkedHashMap<EntityIdentity, UnitOfWorkEntry>()
        entries.forEach { entry ->
            val identity = identityOf(entry.entity) ?: return@forEach
            val previous = identities.putIfAbsent(identity, entry)
            if (previous != null && previous.entity !== entry.entity) {
                error(
                    "conflicting UnitOfWork registrations for ${identity.entityType.name} id ${identity.id}: " +
                        "${previous.kind} and ${entry.kind}"
                )
            }
        }
    }

    private fun identityOf(entity: Any): EntityIdentity? {
        applicationSideIdSupport.findApplicationSideId(entity)?.let { member ->
            if (applicationSideIdSupport.isDefaultId(member, entity)) return null
            val id = member.get(entity) ?: return null
            return EntityIdentity(member.ownerType, id)
        }

        val entityClass = persistentEntityClass(entity)
        val entityInformation = getEntityInformation(entityClass)
        if (entityInformation.isNew(entity)) return null
        val id = entityInformation.getId(entity) ?: return null
        return EntityIdentity(entityClass, id)
    }

    private fun applyCreate(entity: Any, results: FlushResult) {
        validateCreateApplicationSideId(entity)
        val entityClass = persistentEntityClass(entity)
        val refreshRequired =
            applicationSideIdSupport.findApplicationSideId(entity) == null &&
                getEntityInformation(entityClass).isNew(entity)
        if (!entityManager.contains(entity)) {
            entityManager.persist(entity)
        }
        if (refreshRequired) {
            results.refreshList.add(entity)
        }
        results.created.add(entity)
        results.needsFlush = true
    }

    private fun applyExisting(entity: Any, results: FlushResult) {
        validateObservedIdentityConsistency(entity)
        validateExistingRootIdentified(entity)
        val managed = if (entityManager.contains(entity)) entity else entityManager.merge(entity)
        results.existing.add(managed)
        results.needsFlush = true
    }

    private fun applyRemove(entity: Any, results: FlushResult) {
        when {
            entityManager.contains(entity) -> entityManager.remove(entity)
            else -> entityManager.merge(entity).also { merged ->
                entityManager.remove(merged)
            }
        }
        results.deleted.add(entity)
        results.needsFlush = true
    }

    private fun validateCreateApplicationSideId(entity: Any) {
        applicationSideIdSupport.findApplicationSideId(entity)?.let { member ->
            check(!applicationSideIdSupport.isDefaultId(member, entity)) {
                "Application-side ID remains default after assignment: " +
                    "${member.ownerType.name}.${member.field.name}"
            }
        }
    }

    private fun validateExistingRootIdentified(entity: Any) {
        applicationSideIdSupport.findApplicationSideId(entity)?.let { member ->
            check(!applicationSideIdSupport.isDefaultId(member, entity)) {
                "Existing-intent application-side ID is default: ${member.ownerType.name}.${member.field.name}"
            }
            return
        }

        val entityClass = persistentEntityClass(entity)
        check(!getEntityInformation(entityClass).isNew(entity)) {
            "Existing-intent entity appears new: ${entity.javaClass.name}"
        }
    }

    fun interface TransactionHandler<I, O> {
        fun exec(input: I): O
    }

    fun <I, O> save(input: I, propagation: Propagation, transactionHandler: TransactionHandler<I, O>): O =
        when (propagation) {
            Propagation.SUPPORTS -> instance.supports(input, transactionHandler)
            Propagation.NOT_SUPPORTED -> instance.notSupported(input, transactionHandler)
            Propagation.REQUIRES_NEW -> instance.requiresNew(input, transactionHandler)
            Propagation.MANDATORY -> instance.mandatory(input, transactionHandler)
            Propagation.NEVER -> instance.never(input, transactionHandler)
            Propagation.NESTED -> instance.nested(input, transactionHandler)
            else -> instance.required(input, transactionHandler)
        }

    @Transactional(rollbackFor = [Exception::class], propagation = Propagation.REQUIRED)
    open fun <I, O> required(input: I, transactionHandler: TransactionHandler<I, O>): O =
        transactionWrapper(input, transactionHandler)

    @Transactional(rollbackFor = [Exception::class], propagation = Propagation.REQUIRES_NEW)
    open fun <I, O> requiresNew(input: I, transactionHandler: TransactionHandler<I, O>): O =
        transactionWrapper(input, transactionHandler)

    @Transactional(rollbackFor = [Exception::class], propagation = Propagation.SUPPORTS)
    open fun <I, O> supports(input: I, transactionHandler: TransactionHandler<I, O>): O =
        transactionWrapper(input, transactionHandler)

    @Transactional(rollbackFor = [Exception::class], propagation = Propagation.NOT_SUPPORTED)
    open fun <I, O> notSupported(input: I, transactionHandler: TransactionHandler<I, O>): O =
        transactionWrapper(input, transactionHandler)

    @Transactional(rollbackFor = [Exception::class], propagation = Propagation.MANDATORY)
    open fun <I, O> mandatory(input: I, transactionHandler: TransactionHandler<I, O>): O =
        transactionWrapper(input, transactionHandler)

    @Transactional(rollbackFor = [Exception::class], propagation = Propagation.NEVER)
    open fun <I, O> never(input: I, transactionHandler: TransactionHandler<I, O>): O =
        transactionWrapper(input, transactionHandler)

    @Transactional(rollbackFor = [Exception::class], propagation = Propagation.NESTED)
    open fun <I, O> nested(input: I, transactionHandler: TransactionHandler<I, O>): O =
        transactionWrapper(input, transactionHandler)

    protected open fun <I, O> transactionWrapper(input: I, transactionHandler: TransactionHandler<I, O>): O =
        transactionHandler.exec(input)
}

package com.only4.cap4k.ddd.application

import com.only4.cap4k.ddd.core.application.PersistIntent
import com.only4.cap4k.ddd.core.application.UnitOfWork
import com.only4.cap4k.ddd.core.application.UnitOfWorkInterceptor
import com.only4.cap4k.ddd.core.domain.id.IdStrategyRegistry
import com.only4.cap4k.ddd.core.domain.id.MapBackedIdStrategyRegistry
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

private enum class UnitOfWorkIntent {
    CREATE,
    UPDATE,
    REMOVE,
}

private data class PendingChange(
    val entity: Any,
    val intent: UnitOfWorkIntent,
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

private class PendingChangeSet {
    private val entries = LinkedHashMap<ObjectIdentityKey, PendingChange>()

    fun persist(entity: Any, intent: PersistIntent) {
        val key = ObjectIdentityKey(entity)
        val next = intent.toUnitOfWorkIntent()
        val current = entries[key]
        entries[key] = when (current?.intent) {
            null -> PendingChange(entity, next)
            UnitOfWorkIntent.CREATE -> PendingChange(entity, UnitOfWorkIntent.CREATE)
            UnitOfWorkIntent.UPDATE -> when (next) {
                UnitOfWorkIntent.UPDATE -> PendingChange(entity, UnitOfWorkIntent.UPDATE)
                UnitOfWorkIntent.CREATE -> error("UoW intent conflict: UPDATE cannot become CREATE for the same instance")
                UnitOfWorkIntent.REMOVE -> error("persist cannot register REMOVE intent")
            }
            UnitOfWorkIntent.REMOVE ->
                error("UoW intent conflict: REMOVE cannot become ${next.name} for the same instance")
        }
    }

    fun remove(entity: Any) {
        val key = ObjectIdentityKey(entity)
        val current = entries[key]
        when (current?.intent) {
            null -> entries[key] = PendingChange(entity, UnitOfWorkIntent.REMOVE)
            UnitOfWorkIntent.CREATE -> entries.remove(key)
            UnitOfWorkIntent.UPDATE -> entries[key] = PendingChange(entity, UnitOfWorkIntent.REMOVE)
            UnitOfWorkIntent.REMOVE -> Unit
        }
    }

    fun drain(): List<PendingChange> {
        val changes = entries.values.toList()
        entries.clear()
        return changes
    }
}

private fun PersistIntent.toUnitOfWorkIntent(): UnitOfWorkIntent = when (this) {
    PersistIntent.CREATE -> UnitOfWorkIntent.CREATE
    PersistIntent.EXISTING -> UnitOfWorkIntent.UPDATE
}

private data class SaveInput(
    val changes: List<PendingChange>,
    val persistedEntities: Set<Any>,
    val removedEntities: Set<Any>,
    val processedEntities: Set<Any>,
)

private data class FlushResult(
    val created: InsertionOrderedIdentitySet<Any> = InsertionOrderedIdentitySet(),
    val updated: InsertionOrderedIdentitySet<Any> = InsertionOrderedIdentitySet(),
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
    idStrategyRegistry: IdStrategyRegistry = MapBackedIdStrategyRegistry(emptyList()),
) : UnitOfWork, JpaRepositoryObservationRecorder {

    constructor(
        uowInterceptors: List<UnitOfWorkInterceptor>,
        persistListenerManager: PersistListenerManager,
        supportEntityInlinePersistListener: Boolean,
    ) : this(
        uowInterceptors,
        persistListenerManager,
        supportEntityInlinePersistListener,
        MapBackedIdStrategyRegistry(emptyList()),
    )

    @PersistenceContext
    lateinit var entityManager: EntityManager

    private val applicationSideIdSupport = JpaApplicationSideIdSupport(idStrategyRegistry)
    private val ownedRelationTraversal = JpaGeneratedOwnedRelationTraversal()
    private val repositoryObservationBaseline = JpaRepositoryObservationBaseline()

    companion object {
        lateinit var instance: JpaUnitOfWork

        fun fixAopWrapper(unitOfWork: JpaUnitOfWork) {
            instance = unitOfWork
        }

        private val pendingChangesThreadLocal = ThreadLocal.withInitial { PendingChangeSet() }
        private val processingEntitiesThreadLocal = ThreadLocal.withInitial { InsertionOrderedIdentitySet<Any>() }

        private val entityInformationCache = ConcurrentHashMap<Class<*>, EntityInformation<*, *>>()

        @JvmStatic
        fun reset() {
            pendingChangesThreadLocal.remove()
            processingEntitiesThreadLocal.remove()
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

    override fun persist(entity: Any, intent: PersistIntent) {
        pendingChangesThreadLocal.get().persist(entity, intent)
    }

    override fun remove(entity: Any) {
        pendingChangesThreadLocal.get().remove(entity)
    }

    override fun observeRepositoryLoad(root: Any, loadPlan: AggregateLoadPlan) {
        val observed = ownedRelationTraversal.reachableOwnedEntities(root)
            .map { entity -> JpaObservedEntity(entity, observedIdentityOf(entity)) }
        repositoryObservationBaseline.record(root, observed)
    }

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
        val pendingChanges = pendingChangesThreadLocal.get().drain()
        pendingChanges.forEach { pushProcessingEntity(it.entity, currentProcessedEntitySet) }

        val persistEntitySet = pendingChanges
            .filter { it.intent == UnitOfWorkIntent.CREATE || it.intent == UnitOfWorkIntent.UPDATE }
            .mapTo(InsertionOrderedIdentitySet()) { it.entity }
        val deleteEntitySet = pendingChanges
            .filter { it.intent == UnitOfWorkIntent.REMOVE }
            .mapTo(InsertionOrderedIdentitySet()) { it.entity }

        try {
            prepareApplicationSideIds(pendingChanges)
            validateSameIdentityConflicts(pendingChanges)
            uowInterceptors.forEach { it.beforeTransaction(persistEntitySet, deleteEntitySet) }

            save(
                SaveInput(
                    changes = pendingChanges,
                    persistedEntities = persistEntitySet,
                    removedEntities = deleteEntitySet,
                    processedEntities = currentProcessedEntitySet,
                ),
                propagation,
            ) { input ->
                val results = FlushResult()
                uowInterceptors.forEach { it.preInTransaction(input.persistedEntities, input.removedEntities) }

                input.changes.forEach { change ->
                    when (change.intent) {
                        UnitOfWorkIntent.CREATE -> applyCreate(change.entity, results)
                        UnitOfWorkIntent.UPDATE -> applyUpdate(change.entity, results)
                        UnitOfWorkIntent.REMOVE -> applyRemove(change.entity, results)
                    }
                }

                if (results.needsFlush) {
                    entityManager.flush()
                    results.refreshList.forEach { entityManager.refresh(it) }
                    onEntitiesFlushed(results.created, results.updated, results.deleted)
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
            popProcessingEntities(currentProcessedEntitySet)
        }
    }

    private fun prepareApplicationSideIds(changes: List<PendingChange>) {
        changes.forEach { change ->
            when (change.intent) {
                UnitOfWorkIntent.CREATE -> applicationSideIdSupport.assignMissingIds(change.entity)
                UnitOfWorkIntent.UPDATE -> applicationSideIdSupport.assignMissingIdsToOwnedRelations(change.entity)
                UnitOfWorkIntent.REMOVE -> Unit
            }
        }
    }

    private fun validateSameIdentityConflicts(changes: List<PendingChange>) {
        val identities = LinkedHashMap<EntityIdentity, PendingChange>()
        changes.forEach { change ->
            val identity = identityOf(change.entity) ?: return@forEach
            val previous = identities.putIfAbsent(identity, change)
            if (previous != null && previous.entity !== change.entity) {
                error(
                    "conflicting UnitOfWork registrations for ${identity.entityType.name} id ${identity.id}: " +
                        "${previous.intent} and ${change.intent}"
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

    private fun applyUpdate(entity: Any, results: FlushResult) {
        validateUpdateRootIdentified(entity)
        if (!entityManager.contains(entity)) {
            entityManager.merge(entity)
        }
        results.updated.add(entity)
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

    private fun validateUpdateRootIdentified(entity: Any) {
        applicationSideIdSupport.findApplicationSideId(entity)?.let { member ->
            check(!applicationSideIdSupport.isDefaultId(member, entity)) {
                "Update-intent application-side ID is default: ${member.ownerType.name}.${member.field.name}"
            }
            return
        }

        val entityClass = persistentEntityClass(entity)
        check(!getEntityInformation(entityClass).isNew(entity)) {
            "Update-intent entity appears new: ${entity.javaClass.name}"
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

package com.only4.cap4k.ddd.application

import com.only4.cap4k.ddd.core.application.PersistIntent
import com.only4.cap4k.ddd.core.application.UnitOfWork
import com.only4.cap4k.ddd.core.application.UnitOfWorkInterceptor
import com.only4.cap4k.ddd.core.domain.id.GeneratedOwnIdRegistry
import com.only4.cap4k.ddd.core.domain.id.MapBackedGeneratedOwnIdRegistry
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
    generatedOwnIdRegistry: GeneratedOwnIdRegistry = MapBackedGeneratedOwnIdRegistry(emptyList()),
) : UnitOfWork, JpaRepositoryObservationRecorder {

    @PersistenceContext
    lateinit var entityManager: EntityManager

    private val generatedStrongIdSupport = JpaGeneratedStrongIdSupport(generatedOwnIdRegistry)
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
        validateStandaloneEnrollmentTarget(entity, "persist")
        val entry = pendingEntriesThreadLocal.get().persist(entity, intent)
        completeIdsForEntry(entry)
    }

    override fun remove(entity: Any) {
        validateStandaloneEnrollmentTarget(entity, "remove")
        pendingEntriesThreadLocal.get().remove(entity)
    }

    private fun validateStandaloneEnrollmentTarget(entity: Any, operation: String) {
        val observedRoot = repositoryObservationBaseline
            .observedRootForChild(entity, observedIdentityOf(entity))
            ?: return
        error(
            "UnitOfWork.$operation cannot register generated owned child " +
                "${persistentEntityClass(entity).name} as a standalone target; " +
                "persist the aggregate root ${persistentEntityClass(observedRoot).name} instead"
        )
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
        try {
            val drainedEntries = pendingEntriesThreadLocal.get().drain()
            val pendingEntries = reconcilePendingOwnedChildren(drainedEntries)
            pendingEntries.forEach { pushProcessingEntity(it.entity, currentProcessedEntitySet) }

            val persistEntitySet = pendingEntries
                .filter { it.kind == UnitOfWorkEntryKind.CREATE || it.kind == UnitOfWorkEntryKind.EXISTING }
                .mapTo(InsertionOrderedIdentitySet()) { it.entity }
            val deleteEntitySet = pendingEntries
                .filter { it.kind == UnitOfWorkEntryKind.REMOVE }
                .mapTo(InsertionOrderedIdentitySet()) { it.entity }

            completeGeneratedOwnIds(pendingEntries)
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
                val lateOwnership = analyzePendingOwnership(input.entries)
                validateNoSharedReachableOwnership(input.entries, lateOwnership)
                validateNoLatePendingOwnedChildEntries(input.entries)
                completeGeneratedOwnIds(input.entries)

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

    private fun completeGeneratedOwnIds(entries: List<UnitOfWorkEntry>) {
        entries.forEach(::completeIdsForEntry)
    }

    private fun completeIdsForEntry(entry: UnitOfWorkEntry) {
        when (entry.kind) {
            UnitOfWorkEntryKind.CREATE ->
                generatedStrongIdSupport.completeCreate(entry.entity, ownedRelationTraversal)
            UnitOfWorkEntryKind.EXISTING -> {
                validateExistingEvidence(entry.entity)
                validateObservedIdentityConsistency(entry.entity)
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
        repositoryObservationBaseline.entriesFor(root).forEach { entry ->
            val observed = entry.identity ?: return@forEach
            val current = observedIdentityOf(entry.entity)
            check(current == observed) {
                "Observed existing entity ${observed.entityType.name} changed identity " +
                    "from ${observed.id} to ${current?.id}"
            }
        }
    }

    private fun validateExistingEvidence(entity: Any) {
        check(repositoryObservationBaseline.hasBaselineFor(entity) || entityManager.contains(entity)) {
            "EXISTING persist for ${persistentEntityClass(entity).name} requires a repository observation " +
                "baseline or provider-managed existing state; detached unobserved instances cannot be merged safely"
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

    private data class PendingOwnership(
        val ownersByChildIndex: Map<Int, Set<Int>>,
        val reachableByOwnerIndex: Map<Int, List<Any>>,
        val reachableOwnerships: List<ReachableOwnership>,
    )

    private data class ReachableOwnership(
        val entity: Any,
        val ownerIndexes: Set<Int>,
    )

    private fun analyzePendingOwnership(entries: List<UnitOfWorkEntry>): PendingOwnership {
        val activeIndexes = entries.indices.filter { index ->
            entries[index].kind == UnitOfWorkEntryKind.CREATE ||
                entries[index].kind == UnitOfWorkEntryKind.EXISTING
        }
        val reachableByOwner = activeIndexes.associateWith { index ->
            ownedRelationTraversal.reachableOwnedEntities(entries[index].entity)
        }
        val ownersByChild = linkedMapOf<Int, LinkedHashSet<Int>>()

        activeIndexes.forEach { ownerIndex ->
            val descendants = reachableByOwner.getValue(ownerIndex).drop(1)
            activeIndexes.filter { it != ownerIndex }.forEach { childIndex ->
                if (descendants.any { samePersistentEntity(it, entries[childIndex].entity) }) {
                    ownersByChild.getOrPut(childIndex, ::linkedSetOf).add(ownerIndex)
                }
            }
        }

        val reachableEntities = mutableListOf<Any>()
        reachableByOwner.values.forEach { reachable ->
            reachable.drop(1).forEach { entity ->
                if (reachableEntities.none { samePersistentEntity(it, entity) }) {
                    reachableEntities += entity
                }
            }
        }
        val reachableOwnerships = reachableEntities.map { entity ->
            ReachableOwnership(
                entity = entity,
                ownerIndexes = activeIndexes.filterTo(linkedSetOf()) { ownerIndex ->
                    reachableByOwner.getValue(ownerIndex).any { samePersistentEntity(it, entity) }
                },
            )
        }

        return PendingOwnership(ownersByChild, reachableByOwner, reachableOwnerships)
    }

    private fun outermostOwners(
        ownerIndexes: Set<Int>,
        entries: List<UnitOfWorkEntry>,
        reachableByOwnerIndex: Map<Int, List<Any>>,
    ): List<Int> = ownerIndexes.filter { candidateIndex ->
        ownerIndexes.none { otherIndex ->
            otherIndex != candidateIndex &&
                reachableByOwnerIndex.getValue(otherIndex).drop(1).any {
                    samePersistentEntity(it, entries[candidateIndex].entity)
                }
        }
    }

    private fun reconcilePendingOwnedChildren(entries: List<UnitOfWorkEntry>): List<UnitOfWorkEntry> {
        val ownership = analyzePendingOwnership(entries)
        validateNoSharedReachableOwnership(entries, ownership)
        validateNoPendingOwnedChildRemoval(entries, ownership.reachableByOwnerIndex)
        val absorbedIndexes = linkedSetOf<Int>()

        ownership.ownersByChildIndex.forEach { (childIndex, ownerIndexes) ->
            val outermost = outermostOwners(
                ownerIndexes = ownerIndexes,
                entries = entries,
                reachableByOwnerIndex = ownership.reachableByOwnerIndex,
            )
            check(outermost.size == 1) {
                val childType = persistentEntityClass(entries[childIndex].entity).name
                val roots = outermost.joinToString { persistentEntityClass(entries[it].entity).name }
                "pending owned child $childType is reachable from multiple unrelated pending roots: $roots"
            }
            absorbedIndexes += childIndex
        }

        return entries.filterIndexed { index, _ -> index !in absorbedIndexes }
    }

    private fun validateNoSharedReachableOwnership(
        entries: List<UnitOfWorkEntry>,
        ownership: PendingOwnership,
    ) {
        ownership.reachableOwnerships.forEach { reachableOwnership ->
            val outermost = outermostOwners(
                ownerIndexes = reachableOwnership.ownerIndexes,
                entries = entries,
                reachableByOwnerIndex = ownership.reachableByOwnerIndex,
            )
            check(outermost.size <= 1) {
                val childType = persistentEntityClass(reachableOwnership.entity).name
                val roots = outermost.joinToString { persistentEntityClass(entries[it].entity).name }
                "pending owned child $childType is reachable from multiple unrelated pending roots: $roots"
            }
        }
    }

    private fun validateNoPendingOwnedChildRemoval(
        entries: List<UnitOfWorkEntry>,
        reachableByOwnerIndex: Map<Int, List<Any>>,
    ) {
        val removeIndexes = entries.indices.filter { entries[it].kind == UnitOfWorkEntryKind.REMOVE }
        removeIndexes.forEach { removeIndex ->
            val owners = reachableByOwnerIndex.filterValues { reachable ->
                reachable.drop(1).any { samePersistentEntity(it, entries[removeIndex].entity) }
            }.keys
            check(owners.isEmpty()) {
                "UnitOfWork.remove cannot register an owned child while its aggregate root is pending: " +
                    persistentEntityClass(entries[removeIndex].entity).name
            }
        }
    }

    private fun validateNoLatePendingOwnedChildEntries(entries: List<UnitOfWorkEntry>) {
        val rootEntries = entries.filter {
            it.kind == UnitOfWorkEntryKind.CREATE || it.kind == UnitOfWorkEntryKind.EXISTING
        }
        if (rootEntries.isEmpty() || entries.size < 2) return

        rootEntries.forEach { rootEntry ->
            val reachable = ownedRelationTraversal.reachableOwnedEntities(rootEntry.entity)
            val traversalRoot = reachable.firstOrNull() ?: return@forEach
            reachable.asSequence()
                .filterNot { it === traversalRoot }
                .forEach { child ->
                    if (entries.any { samePersistentEntity(it.entity, child) }) {
                        error("pending ownership changed after UnitOfWork interceptor input was constructed")
                    }
                }
        }
    }

    private fun samePersistentEntity(first: Any, second: Any): Boolean {
        if (first === second) return true
        val firstIdentity = identityOf(first) ?: return false
        return firstIdentity == identityOf(second)
    }

    private fun identityOf(entity: Any): EntityIdentity? {
        val entityClass = persistentEntityClass(entity)
        val entityInformation = getEntityInformation(entityClass)
        if (entityInformation.isNew(entity)) return null
        val id = entityInformation.getId(entity) ?: return null
        return EntityIdentity(entityClass, id)
    }

    private fun applyCreate(entity: Any, results: FlushResult) {
        val entityClass = persistentEntityClass(entity)
        val refreshRequired = getEntityInformation(entityClass).isNew(entity)
        if (!entityManager.contains(entity)) entityManager.persist(entity)
        if (refreshRequired) results.refreshList.add(entity)
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

    private fun validateExistingRootIdentified(entity: Any) {
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

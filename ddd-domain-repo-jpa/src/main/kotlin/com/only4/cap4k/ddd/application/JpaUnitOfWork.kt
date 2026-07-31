package com.only4.cap4k.ddd.application

import com.only4.cap4k.ddd.core.application.AggregatePersistenceIntentRecorder
import com.only4.cap4k.ddd.core.application.CommandUnitOfWorkCoordinator
import com.only4.cap4k.ddd.core.application.context.ExecutionContextAccessor
import com.only4.cap4k.ddd.core.application.context.ExecutionContextSnapshot
import com.only4.cap4k.ddd.core.application.event.IntegrationEventManager
import com.only4.cap4k.ddd.core.domain.aggregate.AggregateLifecycleInvoker
import com.only4.cap4k.ddd.core.domain.aggregate.impl.ReflectiveAggregateLifecycleInvoker
import com.only4.cap4k.ddd.core.domain.event.DomainEventManager
import com.only4.cap4k.ddd.core.domain.event.EventRuntimeContextManager
import com.only4.cap4k.ddd.core.domain.id.GeneratedOwnIdRegistry
import com.only4.cap4k.ddd.core.domain.id.MapBackedGeneratedOwnIdRegistry
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import org.hibernate.Hibernate
import org.hibernate.FlushMode
import org.hibernate.Session
import org.hibernate.engine.spi.SessionImplementor
import org.hibernate.engine.spi.Status
import org.hibernate.type.EntityType
import org.springframework.core.Ordered
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Lazy
import org.springframework.data.jpa.repository.support.JpaEntityInformationSupport
import org.springframework.data.repository.core.EntityInformation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import java.time.Clock
import java.util.concurrent.ConcurrentHashMap

private enum class UnitOfWorkEntryKind {
    CREATE,
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

    fun registerNew(entity: Any): UnitOfWorkEntry {
        val key = ObjectIdentityKey(entity)
        val current = entries[key]
        val merged = when (current?.kind) {
            null -> UnitOfWorkEntry(entity, UnitOfWorkEntryKind.CREATE)
            UnitOfWorkEntryKind.CREATE -> UnitOfWorkEntry(entity, UnitOfWorkEntryKind.CREATE)
            UnitOfWorkEntryKind.REMOVE ->
                error("UoW intent conflict: REMOVE cannot become CREATE for the same instance")
        }
        entries[key] = merged
        return merged
    }

    fun remove(entity: Any): Boolean {
        val key = ObjectIdentityKey(entity)
        val current = entries[key]
        return when (current?.kind) {
            null -> {
                entries[key] = UnitOfWorkEntry(entity, UnitOfWorkEntryKind.REMOVE)
                false
            }
            UnitOfWorkEntryKind.CREATE -> {
                entries.remove(key)
                true
            }
            UnitOfWorkEntryKind.REMOVE -> false
        }
    }

    fun isNotEmpty(): Boolean = entries.isNotEmpty()

    fun isPendingCreate(entity: Any): Boolean =
        entries[ObjectIdentityKey(entity)]?.kind == UnitOfWorkEntryKind.CREATE

    fun drain(): List<UnitOfWorkEntry> {
        val changes = entries.values.toList()
        entries.clear()
        return changes
    }
}

private data class FlushResult(
    val created: InsertionOrderedIdentitySet<Any> = InsertionOrderedIdentitySet(),
    val deleted: InsertionOrderedIdentitySet<Any> = InsertionOrderedIdentitySet(),
    val refreshList: MutableList<Any> = mutableListOf(),
    var needsFlush: Boolean = false,
)

private enum class JpaUnitOfWorkPhase {
    HANDLER,
    NESTED_COMMAND,
    INTEGRATION_RECORDS,
    NORMALIZE_INTENT,
    CANDIDATE_DETECTION,
    AUDIT_ENRICHMENT,
    FINAL_DETECTION,
    PROVIDER_FLUSH,
    DOMAIN_EVENT_FRONTIER,
    STABLE,
}

private class JpaUnitOfWorkContext(
    auditTime: java.time.Instant,
    executionContext: ExecutionContextSnapshot,
) {
    val pendingEntries = PendingEntrySet()
    val trackedRoots = InsertionOrderedIdentitySet<Any>()
    val deletedLifecycleRoots = InsertionOrderedIdentitySet<Any>()
    val repositoryObservationBaseline = JpaRepositoryObservationBaseline()
    val auditContext = JpaPersistenceAuditContext(auditTime, executionContext)
    var phase: JpaUnitOfWorkPhase = JpaUnitOfWorkPhase.HANDLER
    var flushCount: Int = 0
    var frontierCount: Int = 0
    var synchronousEventCount: Int = 0
    var nestedCommandCount: Int = 0
    var session: Session? = null
    var previousFlushMode: FlushMode? = null
}

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
    private val domainEventManager: DomainEventManager,
    private val integrationEventManager: IntegrationEventManager? = null,
    private val lifecycleInvoker: AggregateLifecycleInvoker = ReflectiveAggregateLifecycleInvoker(),
    generatedOwnIdRegistry: GeneratedOwnIdRegistry = MapBackedGeneratedOwnIdRegistry(emptyList()),
    private val auditEnrichers: List<JpaPersistenceAuditEnricher> = emptyList(),
    private val limits: JpaUnitOfWorkLimits = JpaUnitOfWorkLimits(),
    private val clock: Clock = Clock.systemUTC(),
    private val executionContextAccessor: ExecutionContextAccessor =
        ExecutionContextAccessor { ExecutionContextSnapshot.EMPTY },
) : CommandUnitOfWorkCoordinator, AggregatePersistenceIntentRecorder, JpaRepositoryObservationRecorder {

    @PersistenceContext
    lateinit var entityManager: EntityManager

    private val generatedStrongIdSupport = JpaGeneratedStrongIdSupport(generatedOwnIdRegistry)
    private val ownedRelationTraversal = JpaGeneratedOwnedRelationTraversal()
    private val repositoryObservationBaseline: JpaRepositoryObservationBaseline
        get() = currentContext().repositoryObservationBaseline
    @Autowired
    @Lazy
    private lateinit var self: JpaUnitOfWork

    companion object {
        private val contextThreadLocal = ThreadLocal<JpaUnitOfWorkContext>()

        private val entityInformationCache = ConcurrentHashMap<Class<*>, EntityInformation<*, *>>()

        @JvmStatic
        fun reset() {
            contextThreadLocal.remove()
            EventRuntimeContextManager.reset()
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun getEntityInformation(entityClass: Class<*>): EntityInformation<Any, Any> =
        entityInformationCache.computeIfAbsent(entityClass) {
            JpaEntityInformationSupport.getEntityInformation(it, entityManager)
        } as EntityInformation<Any, Any>

    private fun persistentEntityClass(entity: Any): Class<*> = Hibernate.getClassLazy(entity)

    override val active: Boolean
        get() = contextThreadLocal.get() != null

    private fun currentContext(): JpaUnitOfWorkContext =
        contextThreadLocal.get() ?: error("UnitOfWork operation requires an active Command Unit of Work")

    override fun <RESULT> execute(block: () -> RESULT): RESULT {
        if (active) {
            val context = currentContext()
            check(
                context.phase == JpaUnitOfWorkPhase.HANDLER ||
                    context.phase == JpaUnitOfWorkPhase.NESTED_COMMAND ||
                    context.phase == JpaUnitOfWorkPhase.DOMAIN_EVENT_FRONTIER
            ) {
                "Nested Command is not allowed during UnitOfWork phase ${context.phase}"
            }
            context.nestedCommandCount++
            checkLimit(
                context = context,
                withinLimit = context.nestedCommandCount <= limits.maxNestedCommands,
                limitName = "nested Commands",
            )
            val previousPhase = context.phase
            context.phase = JpaUnitOfWorkPhase.NESTED_COMMAND
            return try {
                block()
            } finally {
                context.phase = previousPhase
            }
        }
        check(!TransactionSynchronizationManager.isActualTransactionActive()) {
            "A Command Unit of Work cannot adopt an external transaction without an active Cap4k context"
        }
        return executeRequired(block)
    }

    protected open fun <RESULT> executeRequired(block: () -> RESULT): RESULT =
        self.required(block)

    @Transactional(rollbackFor = [Exception::class])
    open fun <RESULT> required(block: () -> RESULT): RESULT {
        check(!active) { "Only the outer Unit of Work may create a transaction context" }
        val context = JpaUnitOfWorkContext(clock.instant(), executionContextAccessor.current())
        contextThreadLocal.set(context)
        EventRuntimeContextManager.beginUnitOfWork()
        var cleanupDeferred = false
        return try {
            enterManualFlush(context)
            cleanupDeferred = registerAfterCompletionCleanup(context)
            val result = block()
            if (cleanupDeferred) {
                registerBeforeCommitFinalization(context)
            } else {
                stabilize(context)
            }
            result
        } finally {
            if (!cleanupDeferred) cleanupContext(context)
        }
    }

    private fun registerAfterCompletionCleanup(context: JpaUnitOfWorkContext): Boolean {
        val transactionActive = TransactionSynchronizationManager.isActualTransactionActive()
        val synchronizationActive = TransactionSynchronizationManager.isSynchronizationActive()
        check(!transactionActive || synchronizationActive) {
            "A Command Unit of Work requires transaction synchronization for commit-boundary finalization"
        }
        if (!transactionActive) return false

        TransactionSynchronizationManager.registerSynchronization(
            object : TransactionSynchronization {
                override fun getOrder(): Int = Ordered.HIGHEST_PRECEDENCE

                override fun afterCompletion(status: Int) {
                    cleanupContext(context)
                }
            },
        )
        return true
    }

    private fun registerBeforeCommitFinalization(context: JpaUnitOfWorkContext) {
        TransactionSynchronizationManager.registerSynchronization(
            object : TransactionSynchronization {
                override fun getOrder(): Int = Ordered.LOWEST_PRECEDENCE

                override fun beforeCommit(readOnly: Boolean) {
                    check(!readOnly) { "Command Unit of Work cannot commit through a read-only transaction" }
                    stabilize(context)
                }
            },
        )
    }

    private fun enterManualFlush(context: JpaUnitOfWorkContext) {
        val session = entityManager.unwrap(Session::class.java)
        context.session = session
        context.previousFlushMode = session.hibernateFlushMode
        session.hibernateFlushMode = FlushMode.MANUAL
    }

    private fun cleanupContext(context: JpaUnitOfWorkContext) {
        if (contextThreadLocal.get() !== context) return
        try {
            context.session?.let { session ->
                context.previousFlushMode?.let { previous ->
                    runCatching { session.hibernateFlushMode = previous }
                }
            }
            EventRuntimeContextManager.endUnitOfWork()
        } finally {
            contextThreadLocal.remove()
        }
    }

    override fun registerNew(root: Any) {
        ensureApplicationMutationPhase("registerNew")
        validateStandaloneEnrollmentTarget(root, "registerNew")
        val context = currentContext()
        val entry = context.pendingEntries.registerNew(root)
        context.trackedRoots.add(root)
        completeIdsForEntry(entry)
    }

    override fun registerDelete(root: Any) {
        ensureApplicationMutationPhase("registerDelete")
        validateStandaloneEnrollmentTarget(root, "registerDelete")
        val context = currentContext()
        check(context.pendingEntries.isPendingCreate(root) || entityManager.contains(root)) {
            "Aggregate root deletion requires a new-pending or currently managed root: ${persistentEntityClass(root).name}"
        }
        if (context.deletedLifecycleRoots.add(root)) lifecycleInvoker.onDeleted(root)
        if (context.pendingEntries.remove(root)) {
            context.trackedRoots.remove(root)
            domainEventManager.discard(root)
        } else {
            context.trackedRoots.add(root)
        }
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

    override fun observeRepositoryLoad(root: Any) {
        val context = contextThreadLocal.get() ?: return
        ensureApplicationMutationPhase("observe repository load", context)
        val observed = ownedRelationTraversal.reachableOwnedEntities(root)
            .map { entity -> JpaObservedEntity(entity, observedIdentityOf(entity)) }
        observed.forEach { entry ->
            val existingRoot = context.repositoryObservationBaseline
                .observedRootForChild(entry.entity, entry.identity)
                ?: return@forEach
            check(samePersistentEntity(existingRoot, root)) {
                "Persistent Entity ${persistentEntityClass(entry.entity).name} is owned by multiple aggregate roots: " +
                    "${persistentEntityClass(existingRoot).name}, ${persistentEntityClass(root).name}"
            }
        }
        context.repositoryObservationBaseline.record(root, observed)
        context.trackedRoots.add(root)
    }

    internal fun observedRepositoryBaseline(): JpaRepositoryObservationBaseline =
        currentContext().repositoryObservationBaseline

    private fun observedIdentityOf(entity: Any): JpaObservedIdentity? {
        val entityClass = persistentEntityClass(entity)
        val entityInformation = getEntityInformation(entityClass)
        if (entityInformation.isNew(entity)) return null
        val id = entityInformation.getId(entity) ?: return null
        return JpaObservedIdentity(entityClass, id)
    }

    private fun stabilize(context: JpaUnitOfWorkContext) {
        while (true) {
            context.phase = JpaUnitOfWorkPhase.INTEGRATION_RECORDS
            integrationEventManager?.release()
            synchronizePersistence(context)

            val pendingBefore = domainEventManager.pendingCount()
            if (pendingBefore > 0) {
                checkLimit(
                    context = context,
                    withinLimit = context.frontierCount < limits.maxFrontierRounds,
                    limitName = "Domain Event frontier rounds",
                )
                checkLimit(
                    context = context,
                    withinLimit = context.synchronousEventCount + pendingBefore <= limits.maxSynchronousEvents,
                    limitName = "synchronous Domain Events",
                )
                context.phase = JpaUnitOfWorkPhase.DOMAIN_EVENT_FRONTIER
                domainEventManager.release(context.trackedRoots.toSet())
                context.frontierCount++
                context.synchronousEventCount += pendingBefore
                continue
            }

            if (!hasPersistentWork(context)) {
                context.phase = JpaUnitOfWorkPhase.STABLE
                return
            }
        }
    }

    private fun hasPersistentWork(context: JpaUnitOfWorkContext): Boolean =
        context.pendingEntries.isNotEmpty() || entityManager.unwrap(Session::class.java).isDirty

    private fun synchronizePersistence(context: JpaUnitOfWorkContext): Boolean {
        context.phase = JpaUnitOfWorkPhase.NORMALIZE_INTENT
        val pendingEntries = reconcilePendingOwnedChildren(context.pendingEntries.drain())
        completeGeneratedOwnIds(pendingEntries)
        validateSameIdentityConflicts(pendingEntries)

        val lateOwnership = analyzePendingOwnership(pendingEntries)
        validateNoSharedReachableOwnership(pendingEntries, lateOwnership)
        validateNoLatePendingOwnedChildEntries(pendingEntries)
        completeGeneratedOwnIds(pendingEntries)
        completeAndValidateObservedRootIds(context)

        context.phase = JpaUnitOfWorkPhase.CANDIDATE_DETECTION
        val aggregateChanges = detectAggregateChanges(context, pendingEntries)
        if (aggregateChanges.isNotEmpty()) {
            val topologyBefore = capturePersistentTopology(context)
            val eventCountBefore = domainEventManager.pendingCount()
            context.phase = JpaUnitOfWorkPhase.AUDIT_ENRICHMENT
            auditEnrichers.forEach { enricher ->
                aggregateChanges.forEach { changeSet ->
                    enricher.enrich(changeSet, context.auditContext)
                }
            }
            check(capturePersistentTopology(context) == topologyBefore) {
                "Audit enrichment must not change persistent Entity or relation topology"
            }
            check(domainEventManager.pendingCount() == eventCountBefore) {
                "Audit enrichment must not produce Domain Events"
            }
        }

        val results = FlushResult()
        pendingEntries.forEach { entry ->
            context.trackedRoots.add(entry.entity)
            when (entry.kind) {
                UnitOfWorkEntryKind.CREATE -> applyCreate(entry.entity, results)
                UnitOfWorkEntryKind.REMOVE -> applyRemove(entry.entity, results)
            }
        }

        context.phase = JpaUnitOfWorkPhase.FINAL_DETECTION
        val providerDirty = entityManager.unwrap(Session::class.java).isDirty
        if (!results.needsFlush && !providerDirty) return false

        context.phase = JpaUnitOfWorkPhase.PROVIDER_FLUSH
        checkLimit(
            context = context,
            withinLimit = context.flushCount < limits.maxProviderFlushes,
            limitName = "Provider flushes",
        )
        entityManager.flush()
        results.refreshList.forEach { entityManager.refresh(it) }
        advanceRepositoryBaseline(context, results)
        context.flushCount++
        return true
    }

    private fun checkLimit(
        context: JpaUnitOfWorkContext,
        withinLimit: Boolean,
        limitName: String,
    ) {
        val causalPath = EventRuntimeContextManager.diagnosticCausalPath()
            .joinToString(" -> ")
            .ifBlank { "<unavailable>" }
        check(withinLimit) {
            "UnitOfWork limit exceeded: $limitName; " +
                "phase=${context.phase}; " +
                "frontierRounds=${context.frontierCount}; " +
                "synchronousEvents=${context.synchronousEventCount}; " +
                "nestedCommands=${context.nestedCommandCount}; " +
                "providerFlushes=${context.flushCount}; " +
                "causalPath=$causalPath"
        }
    }

    private fun ensureApplicationMutationPhase(
        operation: String,
        context: JpaUnitOfWorkContext = currentContext(),
    ) {
        check(
            context.phase == JpaUnitOfWorkPhase.HANDLER ||
                context.phase == JpaUnitOfWorkPhase.NESTED_COMMAND ||
                context.phase == JpaUnitOfWorkPhase.DOMAIN_EVENT_FRONTIER
        ) {
            "UnitOfWork.$operation is not allowed during phase ${context.phase}"
        }
    }

    private fun advanceRepositoryBaseline(
        context: JpaUnitOfWorkContext,
        results: FlushResult,
    ) {
        val roots = InsertionOrderedIdentitySet<Any>().apply {
            addAll(context.trackedRoots)
            addAll(results.created)
        }
        context.repositoryObservationBaseline.clear()
        roots.asSequence()
            .filter(entityManager::contains)
            .forEach { root ->
                val observed = ownedRelationTraversal.reachableOwnedEntities(root)
                    .map { entity -> JpaObservedEntity(entity, observedIdentityOf(entity)) }
                context.repositoryObservationBaseline.record(root, observed)
            }
    }

    private fun detectAggregateChanges(
        context: JpaUnitOfWorkContext,
        pendingEntries: List<UnitOfWorkEntry>,
    ): List<JpaAggregateChange> {
        val candidateTypes = LinkedHashMap<ObjectIdentityKey, JpaEntityChange>()
        val rootHints = LinkedHashMap<ObjectIdentityKey, Any>()

        fun add(entity: Any, type: JpaEntityChangeType, rootHint: Any? = null) {
            val key = ObjectIdentityKey(entity)
            if (rootHint != null) rootHints.putIfAbsent(key, rootHint)
            val current = candidateTypes[key]
            val effective = when {
                current == null -> type
                current.type == JpaEntityChangeType.DELETE || type == JpaEntityChangeType.DELETE ->
                    JpaEntityChangeType.DELETE
                current.type == JpaEntityChangeType.CREATE || type == JpaEntityChangeType.CREATE ->
                    JpaEntityChangeType.CREATE
                else -> JpaEntityChangeType.UPDATE
            }
            candidateTypes[key] = JpaEntityChange(entity, effective)
        }

        pendingEntries.forEach { entry ->
            when (entry.kind) {
                UnitOfWorkEntryKind.CREATE -> ownedRelationTraversal
                    .reachableOwnedEntities(entry.entity)
                    .forEach { add(it, JpaEntityChangeType.CREATE, entry.entity) }
                UnitOfWorkEntryKind.REMOVE -> ownedRelationTraversal
                    .reachableOwnedEntities(entry.entity, initializeOwnedCollections = true)
                    .forEach { add(it, JpaEntityChangeType.DELETE, entry.entity) }
            }
        }

        context.trackedRoots.forEach { root ->
            ownedRelationTraversal.reachableOwnedEntities(root)
                .filterNot { context.repositoryObservationBaseline.isObservedObject(it, observedIdentityOf(it)) }
                .filterNot { it === root && context.repositoryObservationBaseline.hasBaselineFor(root) }
                .filterNot(entityManager::contains)
                .forEach { add(it, JpaEntityChangeType.CREATE, root) }
        }

        detectRemovedObservedEntities(context).forEach { add(it, JpaEntityChangeType.DELETE) }

        // A Command with no observed or explicitly registered aggregate has no
        // persistence candidate for Cap4k to inspect. Provider dirtiness is
        // still checked by the final phase for unsupported direct JPA access.
        if (context.trackedRoots.isEmpty() && candidateTypes.isEmpty()) return emptyList()

        val session = entityManager.unwrap(SessionImplementor::class.java)
        session.persistenceContextInternal.reentrantSafeEntityEntries().forEach { (entity, entry) ->
            val root = aggregateRootForOrNull(context, entity) ?: return@forEach
            when {
                entry.status.isDeletedOrGone -> add(entity, JpaEntityChangeType.DELETE, root)
                entry.status == Status.SAVING || !entry.isExistsInDatabase ->
                    add(entity, JpaEntityChangeType.CREATE, root)
                entry.status == Status.MANAGED && isDirtyExistingEntity(session, entity, entry) ->
                    add(entity, JpaEntityChangeType.UPDATE, root)
            }
        }
        session.persistenceContextInternal.forEachCollectionEntry({ collection, entry ->
            val persister = entry.loadedPersister ?: return@forEachCollectionEntry
            if (!collection.isDirty && !collection.hasQueuedOperations()) return@forEachCollectionEntry
            val owner = collection.owner ?: return@forEachCollectionEntry
            val ownerRoot = aggregateRootForOrNull(context, owner) ?: return@forEachCollectionEntry
            val entityName = (persister.elementType as? EntityType)?.associatedEntityName
                ?: return@forEachCollectionEntry

            if (collection.hasQueuedOperations()) {
                collection.queuedAdditionIterator().forEachRemaining { addition ->
                    if (
                        addition != null &&
                        !context.repositoryObservationBaseline.isObservedObject(
                            addition,
                            observedIdentityOf(addition),
                        )
                    ) {
                        add(addition, JpaEntityChangeType.CREATE, ownerRoot)
                    }
                }
                if (persister.hasOrphanDelete()) {
                    collection.getQueuedOrphans(entityName).filterNotNull().forEach { orphan ->
                        add(orphan, JpaEntityChangeType.DELETE, ownerRoot)
                    }
                }
            }
            if (collection.isDirty && persister.hasOrphanDelete()) {
                entry.getOrphans(entityName, collection).filterNotNull().forEach { orphan ->
                    add(orphan, JpaEntityChangeType.DELETE, ownerRoot)
                }
            }
        }, true)

        val byRoot = LinkedHashMap<ObjectIdentityKey, Pair<Any, MutableList<JpaEntityChange>>>()
        candidateTypes.values.forEach { change ->
            val root = rootHints[ObjectIdentityKey(change.entity)] ?: aggregateRootFor(context, change.entity)
            byRoot.getOrPut(ObjectIdentityKey(root)) { root to mutableListOf() }.second += change
        }
        val rootOperations = pendingEntries.associate { entry ->
            ObjectIdentityKey(entry.entity) to when (entry.kind) {
                UnitOfWorkEntryKind.CREATE -> JpaAggregateRootOperation.CREATE
                UnitOfWorkEntryKind.REMOVE -> JpaAggregateRootOperation.DELETE
            }
        }
        return byRoot.map { (rootKey, pair) ->
            JpaAggregateChange(
                root = pair.first,
                rootOperation = rootOperations[rootKey] ?: JpaAggregateRootOperation.NONE,
                entityChanges = pair.second.toList(),
            )
        }
    }

    private fun aggregateRootForOrNull(context: JpaUnitOfWorkContext, entity: Any): Any? {
        val owners = context.trackedRoots.filter { root ->
            root === entity || ownedRelationTraversal.reachableOwnedEntities(root)
                .any { reachable -> samePersistentEntity(reachable, entity) }
        }
        check(owners.size <= 1) {
            val ownerNames = owners.joinToString { persistentEntityClass(it).name }
            "Changed persistent Entity ${persistentEntityClass(entity).name} must belong to exactly one tracked aggregate root; owners=[$ownerNames]"
        }
        owners.singleOrNull()?.let { return it }
        context.repositoryObservationBaseline
            .observedRootFor(entity, observedIdentityOf(entity))
            ?.let { return it }
        return null
    }

    private fun aggregateRootFor(context: JpaUnitOfWorkContext, entity: Any): Any =
        aggregateRootForOrNull(context, entity) ?: error(
            "Changed persistent Entity ${persistentEntityClass(entity).name} must belong to exactly one tracked aggregate root; owners=[]",
        )

    private fun capturePersistentTopology(context: JpaUnitOfWorkContext): Map<ObjectIdentityKey, List<ObjectIdentityKey>> =
        context.trackedRoots.associate { root ->
            ObjectIdentityKey(root) to ownedRelationTraversal.reachableOwnedEntities(root)
                .map(::ObjectIdentityKey)
        }

    private fun detectRemovedObservedEntities(context: JpaUnitOfWorkContext): List<Any> =
        context.repositoryObservationBaseline.observedRoots().flatMap { root ->
            val reachable = ownedRelationTraversal.reachableOwnedEntities(root)
            context.repositoryObservationBaseline.entriesFor(root)
                .asSequence()
                .filter { observed -> reachable.none { current -> samePersistentEntity(current, observed.entity) } }
                .map { it.entity }
                .toList()
        }

    private fun isDirtyExistingEntity(
        session: SessionImplementor,
        entity: Any,
        entry: org.hibernate.engine.spi.EntityEntry,
    ): Boolean {
        if (!entry.requiresDirtyCheck(entity)) return false
        val loadedState = entry.loadedState ?: return false
        val currentState = entry.persister.getValues(entity)
        return entry.persister.findDirty(currentState, loadedState, entity, session)?.isNotEmpty() == true
    }

    private fun completeGeneratedOwnIds(entries: List<UnitOfWorkEntry>) {
        entries.forEach(::completeIdsForEntry)
    }

    private fun completeAndValidateObservedRootIds(context: JpaUnitOfWorkContext) {
        context.trackedRoots
            .filter(context.repositoryObservationBaseline::hasBaselineFor)
            .forEach { root ->
                validateObservedIdentityConsistency(root)
                generatedStrongIdSupport.completeExisting(
                    root = root,
                    traversal = ownedRelationTraversal,
                    baseline = context.repositoryObservationBaseline,
                )
            }
    }

    private fun completeIdsForEntry(entry: UnitOfWorkEntry) {
        when (entry.kind) {
            UnitOfWorkEntryKind.CREATE ->
                generatedStrongIdSupport.completeCreate(entry.entity, ownedRelationTraversal)
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
            entries[index].kind == UnitOfWorkEntryKind.CREATE
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
            it.kind == UnitOfWorkEntryKind.CREATE
        }
        if (rootEntries.isEmpty() || entries.size < 2) return

        rootEntries.forEach { rootEntry ->
            val reachable = ownedRelationTraversal.reachableOwnedEntities(rootEntry.entity)
            val traversalRoot = reachable.firstOrNull() ?: return@forEach
            reachable.asSequence()
                .filterNot { it === traversalRoot }
                .forEach { child ->
                    if (entries.any { samePersistentEntity(it.entity, child) }) {
                        error("pending ownership changed while the persistence synchronization input was constructed")
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

    private fun applyRemove(entity: Any, results: FlushResult) {
        check(entityManager.contains(entity)) {
            "Detached aggregate root removal is unsupported: ${persistentEntityClass(entity).name}"
        }
        entityManager.remove(entity)
        results.deleted.add(entity)
        results.needsFlush = true
    }

}

package com.only4.cap4k.ddd.domain.repo.impl

import com.only4.cap4k.ddd.application.JpaRepositoryObservationRecorder
import com.only4.cap4k.ddd.core.application.AggregatePersistenceIntentRecorder
import com.only4.cap4k.ddd.core.application.invocation.InvocationKind
import com.only4.cap4k.ddd.core.application.invocation.InvocationScopeAccessor
import com.only4.cap4k.ddd.core.domain.aggregate.AggregateRootCatalog
import com.only4.cap4k.ddd.core.domain.repo.Predicate
import com.only4.cap4k.ddd.core.domain.repo.Repository
import com.only4.cap4k.ddd.core.domain.repo.RepositorySupervisor
import com.only4.cap4k.ddd.core.share.DomainException
import com.only4.cap4k.ddd.core.share.OrderInfo
import com.only4.cap4k.ddd.core.share.PageData
import com.only4.cap4k.ddd.core.share.PageParam
import com.only4.cap4k.ddd.core.share.PageParam.Companion.limit
import com.only4.cap4k.ddd.core.share.misc.resolveGenericTypeClass
import java.util.concurrent.ConcurrentHashMap

/**
 * Routes aggregate repository operations without changing persistence state.
 * Reads stay managed for the surrounding Command/Query transaction and are
 * observed only when a Command Unit of Work is active.
 */
class DefaultRepositorySupervisor(
    private val repositories: List<Repository<*>>,
    private val persistenceIntents: AggregatePersistenceIntentRecorder,
    private val invocationScopeAccessor: InvocationScopeAccessor,
    private val aggregateRootCatalog: AggregateRootCatalog,
    private val observationRecorder: JpaRepositoryObservationRecorder? =
        persistenceIntents as? JpaRepositoryObservationRecorder,
) : RepositorySupervisor {

    private data class ResolvedRepository(
        val index: Int,
        val entityClass: Class<*>,
        val predicateClass: Class<*>,
        val repository: Repository<*>,
    )

    private val repositoryMap: Map<Class<*>, Map<Class<*>, Repository<*>>> by lazy {
        val resolved = repositories.mapIndexed { index, repository ->
            var entityClass = resolveGenericTypeClass(repository, 0, Repository::class.java)
            if (Any::class.java == entityClass) {
                for ((repositoryClass, reflector) in repositoryClass2EntityClassReflector) {
                    if (repositoryClass.isAssignableFrom(repository.javaClass)) {
                        val reflectedClass = reflector(repository)
                        if (Any::class.java != reflectedClass) {
                            entityClass = reflectedClass
                            break
                        }
                    }
                }
            }
            ResolvedRepository(
                index = index,
                entityClass = entityClass,
                predicateClass = repository.supportPredicateClass(),
                repository = repository,
            )
        }

        val conflicts = resolved
            .groupBy { it.entityClass to it.predicateClass }
            .filterValues { it.size > 1 }
            .entries
            .sortedWith(compareBy({ it.key.first.name }, { it.key.second.name }))
        check(conflicts.isEmpty()) {
            conflicts.joinToString(
                prefix = "Duplicate Repository registrations: ",
                separator = "; ",
            ) { (route, registrations) ->
                val contributors = registrations
                    .sortedWith(compareBy({ it.repository.javaClass.name }, { it.index }))
                    .joinToString { "${it.repository.javaClass.name}[index=${it.index}]" }
                "entityClass=${route.first.name}, predicateClass=${route.second.name}, contributors=[$contributors]"
            }
        }

        resolved
            .groupBy { it.entityClass }
            .mapValues { (_, registrations) ->
                registrations.associate { it.predicateClass to it.repository }
            }
    }

    fun init() {
        repositoryMap
    }

    companion object {
        private val predicateClass2EntityClassReflector = ConcurrentHashMap<Class<*>, (Predicate<*>) -> Class<*>>()
        private val repositoryClass2EntityClassReflector = ConcurrentHashMap<Class<*>, (Repository<*>) -> Class<*>>()

        @JvmStatic
        fun registerPredicateEntityClassReflector(
            predicateClass: Class<*>,
            entityClassReflector: (Predicate<*>) -> Class<*>,
        ) {
            predicateClass2EntityClassReflector.putIfAbsent(predicateClass, entityClassReflector)
        }

        @JvmStatic
        fun registerRepositoryEntityClassReflector(
            repositoryClass: Class<*>,
            entityClassReflector: (Repository<*>) -> Class<*>,
        ) {
            repositoryClass2EntityClassReflector.putIfAbsent(repositoryClass, entityClassReflector)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun <ENTITY : Any> repo(entityClass: Class<ENTITY>, predicate: Predicate<*>): Repository<ENTITY> {
        val repos = repositoryMap[entityClass]
            ?: throw DomainException("仓储不存在：${entityClass.typeName}")
        val predicateClass = predicate.javaClass
        return repos[predicateClass] as? Repository<ENTITY>
            ?: throw DomainException("仓储不兼容断言条件：${predicateClass.name}")
    }

    @Suppress("UNCHECKED_CAST")
    private fun <ENTITY : Any> reflectEntityClass(predicate: Predicate<*>): Class<ENTITY> {
        val reflector = predicateClass2EntityClassReflector[predicate.javaClass]
            ?: throw DomainException("实体断言类型不支持：${predicate.javaClass.name}")
        return reflector(predicate) as Class<ENTITY>
    }

    private fun <ENTITY : Any> observeLoaded(entities: Iterable<ENTITY>) {
        entities.forEach { observationRecorder?.observeRepositoryLoad(it) }
    }

    private fun requireReadScope(): InvocationKind {
        val current = invocationScopeAccessor.current()
        check(current == InvocationKind.COMMAND || current == InvocationKind.QUERY) {
            "Repository reads require COMMAND or QUERY invocation scope; current=${current ?: "NONE"}"
        }
        return current
    }

    private fun requireWriteScope() {
        val current = invocationScopeAccessor.current()
        check(current == InvocationKind.COMMAND) {
            "Repository removal requires COMMAND invocation scope; current=${current ?: "NONE"}"
        }
    }

    private fun requireAggregateRoot(entityClass: Class<*>, operation: String) {
        check(aggregateRootCatalog.isAggregateRoot(entityClass)) {
            "Repository $operation in COMMAND requires an aggregate-root type; actual=${entityClass.name}"
        }
    }

    private fun <ENTITY : Any> repositoryForRead(predicate: Predicate<ENTITY>): Repository<ENTITY> {
        val entityClass = reflectEntityClass<ENTITY>(predicate)
        if (requireReadScope() == InvocationKind.COMMAND) {
            requireAggregateRoot(entityClass, "read")
        }
        return repo(entityClass, predicate)
    }

    private fun <ENTITY : Any> repositoryForRemoval(predicate: Predicate<ENTITY>): Repository<ENTITY> {
        requireWriteScope()
        val entityClass = reflectEntityClass<ENTITY>(predicate)
        requireAggregateRoot(entityClass, "removal")
        return repo(entityClass, predicate)
    }

    override fun <ENTITY : Any> find(
        predicate: Predicate<ENTITY>,
        orders: Collection<OrderInfo>,
    ): List<ENTITY> {
        return repositoryForRead(predicate)
            .find(predicate, orders)
            .also(::observeLoaded)
    }

    override fun <ENTITY : Any> find(
        predicate: Predicate<ENTITY>,
        pageParam: PageParam,
    ): List<ENTITY> {
        return repositoryForRead(predicate)
            .find(predicate, pageParam)
            .also(::observeLoaded)
    }

    override fun <ENTITY : Any> findOne(predicate: Predicate<ENTITY>): ENTITY? {
        return repositoryForRead(predicate)
            .findOne(predicate)
            ?.also { observationRecorder?.observeRepositoryLoad(it) }
    }

    override fun <ENTITY : Any> findFirst(
        predicate: Predicate<ENTITY>,
        orders: Collection<OrderInfo>,
    ): ENTITY? {
        return repositoryForRead(predicate)
            .findFirst(predicate, orders)
            ?.also { observationRecorder?.observeRepositoryLoad(it) }
    }

    override fun <ENTITY : Any> findPage(
        predicate: Predicate<ENTITY>,
        pageParam: PageParam,
    ): PageData<ENTITY> {
        return repositoryForRead(predicate)
            .findPage(predicate, pageParam)
            .apply { observeLoaded(list) }
    }

    override fun <ENTITY : Any> remove(predicate: Predicate<ENTITY>): List<ENTITY> {
        return repositoryForRemoval(predicate)
            .find(predicate, emptyList())
            .also(::observeLoaded)
            .onEach(persistenceIntents::registerDelete)
    }

    override fun <ENTITY : Any> remove(predicate: Predicate<ENTITY>, limit: Int): List<ENTITY> {
        return repositoryForRemoval(predicate)
            .findPage(predicate, limit(limit))
            .list
            .also(::observeLoaded)
            .onEach(persistenceIntents::registerDelete)
    }

    override fun <ENTITY : Any> count(predicate: Predicate<ENTITY>): Long {
        return repositoryForRead(predicate).count(predicate)
    }

    override fun <ENTITY : Any> exists(predicate: Predicate<ENTITY>): Boolean {
        return repositoryForRead(predicate).exists(predicate)
    }
}

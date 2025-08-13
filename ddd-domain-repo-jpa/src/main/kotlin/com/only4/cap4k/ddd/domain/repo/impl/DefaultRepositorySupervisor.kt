package com.only4.cap4k.ddd.domain.repo.impl

import com.only4.cap4k.ddd.core.application.UnitOfWork
import com.only4.cap4k.ddd.core.domain.repo.Predicate
import com.only4.cap4k.ddd.core.domain.repo.Repository
import com.only4.cap4k.ddd.core.domain.repo.RepositorySupervisor
import com.only4.cap4k.ddd.core.share.DomainException
import com.only4.cap4k.ddd.core.share.OrderInfo
import com.only4.cap4k.ddd.core.share.PageData
import com.only4.cap4k.ddd.core.share.PageParam
import com.only4.cap4k.ddd.core.share.PageParam.Companion.limit
import com.only4.cap4k.ddd.core.share.misc.resolveGenericTypeClass
import com.only4.cap4k.ddd.domain.aggregate.JpaAggregatePredicate
import com.only4.cap4k.ddd.domain.aggregate.JpaAggregatePredicateSupport
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * 默认仓储管理器
 *
 * @author LD_moxeii
 * @date 2025/08/03
 */
class DefaultRepositorySupervisor(
    private val repositories: List<Repository<*>>,
    private val unitOfWork: UnitOfWork
) : RepositorySupervisor {

    private val repositoryMap: Map<Class<*>, Map<Class<*>, Repository<*>>> by lazy {
        buildMap<Class<*>, MutableMap<Class<*>, Repository<*>>> {
            repositories.forEach { repository ->
                var entityClass = resolveGenericTypeClass(
                    repository, 0,
                    Repository::class.java
                )

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

                computeIfAbsent(entityClass) { mutableMapOf() }[repository.supportPredicateClass()] =
                    repository
            }
        }.toMap()
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
            entityClassReflector: (Predicate<*>) -> Class<*>
        ) {
            predicateClass2EntityClassReflector.putIfAbsent(predicateClass, entityClassReflector)
        }

        @JvmStatic
        fun registerRepositoryEntityClassReflector(
            repositoryClass: Class<*>,
            entityClassReflector: (Repository<*>) -> Class<*>
        ) {
            repositoryClass2EntityClassReflector.putIfAbsent(repositoryClass, entityClassReflector)
        }
    }


    @Suppress("UNCHECKED_CAST")
    private fun <ENTITY : Any> repo(entityClass: Class<ENTITY>, predicate: Predicate<*>): Repository<ENTITY> {
        val repos = repositoryMap[entityClass]
            ?: throw DomainException("仓储不存在：${entityClass.typeName}")

        if (repos.isEmpty()) {
            throw DomainException("仓储不存在：${entityClass.typeName}")
        }

        val predicateClass = when (predicate) {
            is JpaAggregatePredicate<*, *> -> JpaAggregatePredicateSupport.getPredicate(predicate).javaClass
            else -> predicate.javaClass
        }

        if (!repos.containsKey(predicateClass)) {
            throw DomainException("仓储不兼容断言条件：${predicateClass.name}")
        }

        return repos[predicateClass] as Repository<ENTITY>
    }

    @Suppress("UNCHECKED_CAST")
    private fun <ENTITY : Any> reflectEntityClass(predicate: Predicate<*>): Class<ENTITY> {
        val reflector = predicateClass2EntityClassReflector[predicate.javaClass]
            ?: throw DomainException("实体断言类型不支持：${predicate.javaClass.name}")

        return reflector(predicate) as Class<ENTITY>
    }

    override fun <ENTITY : Any> find(
        predicate: Predicate<ENTITY>,
        orders: Collection<OrderInfo>,
        persist: Boolean
    ): List<ENTITY> =
        repo(reflectEntityClass<ENTITY>(predicate), predicate).find(predicate, orders, persist)
            .also { if (persist) it.forEach(unitOfWork::persist) }


    override fun <ENTITY : Any> find(
        predicate: Predicate<ENTITY>,
        pageParam: PageParam,
        persist: Boolean
    ): List<ENTITY> = repo(reflectEntityClass<ENTITY>(predicate), predicate)
        .find(predicate, pageParam, persist)
        .also { if (persist) it.forEach(unitOfWork::persist) }

    override fun <ENTITY : Any> findOne(
        predicate: Predicate<ENTITY>,
        persist: Boolean
    ): Optional<ENTITY> = repo(reflectEntityClass<ENTITY>(predicate), predicate)
        .findOne(predicate, persist)
        .also { if (persist) it.ifPresent(unitOfWork::persist) }

    override fun <ENTITY : Any> findFirst(
        predicate: Predicate<ENTITY>,
        orders: Collection<OrderInfo>,
        persist: Boolean
    ): Optional<ENTITY> = repo(reflectEntityClass<ENTITY>(predicate), predicate)
        .findFirst(predicate, orders, persist)
        .also { if (persist) it.ifPresent(unitOfWork::persist) }

    override fun <ENTITY : Any> findPage(
        predicate: Predicate<ENTITY>,
        pageParam: PageParam,
        persist: Boolean
    ): PageData<ENTITY> =
        repo(reflectEntityClass<ENTITY>(predicate), predicate)
            .findPage(predicate, pageParam, persist)
            .apply { if (persist) list.forEach(unitOfWork::persist) }

    override fun <ENTITY : Any> remove(predicate: Predicate<ENTITY>): List<ENTITY> =
        repo(reflectEntityClass<ENTITY>(predicate), predicate)
            .find(predicate)
            .onEach(unitOfWork::remove)


    override fun <ENTITY : Any> remove(predicate: Predicate<ENTITY>, limit: Int): List<ENTITY> =
        repo(reflectEntityClass<ENTITY>(predicate), predicate)
            .findPage(predicate, limit(limit))
            .list
            .onEach(unitOfWork::remove)

    override fun <ENTITY : Any> count(predicate: Predicate<ENTITY>): Long =
        repo(reflectEntityClass<ENTITY>(predicate), predicate)
            .count(predicate)


    override fun <ENTITY : Any> exists(predicate: Predicate<ENTITY>): Boolean =
        repo(reflectEntityClass<ENTITY>(predicate), predicate).exists(predicate)
}

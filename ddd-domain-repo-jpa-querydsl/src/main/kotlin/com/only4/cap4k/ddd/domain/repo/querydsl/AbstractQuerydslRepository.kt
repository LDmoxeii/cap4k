package com.only4.cap4k.ddd.domain.repo.querydsl

import com.only4.cap4k.ddd.core.domain.repo.Predicate
import com.only4.cap4k.ddd.core.domain.repo.Repository
import com.only4.cap4k.ddd.core.share.OrderInfo
import com.only4.cap4k.ddd.core.share.PageData
import com.only4.cap4k.ddd.core.share.PageParam
import com.only4.cap4k.ddd.core.share.misc.resolveGenericTypeClass
import com.only4.cap4k.ddd.domain.repo.fromSpringData
import com.only4.cap4k.ddd.domain.repo.impl.DefaultRepositorySupervisor
import jakarta.annotation.PostConstruct
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import org.springframework.data.querydsl.QuerydslPredicateExecutor
import java.util.*

/**
 * 基于querydsl的仓储抽象类
 *
 * @author LD_moxeii
 * @date 2025/07/31
 */
open class AbstractQuerydslRepository<ENTITY : Any>(
    private val querydslPredicateExecutor: QuerydslPredicateExecutor<ENTITY>
) : Repository<ENTITY> {

    @PersistenceContext
    protected lateinit var entityManager: EntityManager

    @PostConstruct
    fun init() {
        DefaultRepositorySupervisor.registerPredicateEntityClassReflector(QuerydslPredicate::class.java) { predicate ->
            QuerydslPredicateSupport.reflectEntityClass(predicate)
        }
        DefaultRepositorySupervisor.registerRepositoryEntityClassReflector(AbstractQuerydslRepository::class.java) { repository ->
            resolveGenericTypeClass(
                repository, 0,
                AbstractQuerydslRepository::class.java
            )
        }
    }

    override fun supportPredicateClass(): Class<*> = QuerydslPredicate::class.java

    override fun find(predicate: Predicate<ENTITY>, orders: Collection<OrderInfo>, persist: Boolean): List<ENTITY> {
        val entities = querydslPredicateExecutor.findAll(
            QuerydslPredicateSupport.resumePredicate(predicate),
            QuerydslPredicateSupport.resumeSort(predicate, orders)
        )
        return entities.map { entity ->
            if (!persist) {
                entityManager.detach(entity)
            }
            entity
        }
    }

    override fun find(predicate: Predicate<ENTITY>, pageParam: PageParam, persist: Boolean): List<ENTITY> {
        val entities = querydslPredicateExecutor.findAll(
            QuerydslPredicateSupport.resumePredicate(predicate),
            QuerydslPredicateSupport.resumePageable(predicate, pageParam)
        )
        return entities.map { entity ->
            entity.apply {
                if (!persist) {
                    entityManager.detach(this)
                }
            }
        }.toList()
    }

    override fun findOne(predicate: Predicate<ENTITY>, persist: Boolean): Optional<ENTITY> {
        val entity = querydslPredicateExecutor.findOne(QuerydslPredicateSupport.resumePredicate(predicate))
        if (!persist) {
            entity.ifPresent(entityManager::detach)
        }
        return entity
    }

    override fun findFirst(
        predicate: Predicate<ENTITY>,
        orders: Collection<OrderInfo>,
        persist: Boolean
    ): Optional<ENTITY> {
        val pageParam = PageParam.limit(1)
        orders.forEach { order ->
            pageParam.orderBy(order.field, order.desc)
        }
        val entities = querydslPredicateExecutor.findAll(
            QuerydslPredicateSupport.resumePredicate(predicate),
            QuerydslPredicateSupport.resumePageable(predicate, pageParam)
        )
        val entity = entities.content.firstOrNull()?.let { Optional.of(it) } ?: Optional.empty()
        if (!persist) {
            entity.ifPresent(entityManager::detach)
        }
        return entity
    }

    override fun findPage(predicate: Predicate<ENTITY>, pageParam: PageParam, persist: Boolean): PageData<ENTITY> {
        val entities = querydslPredicateExecutor.findAll(
            QuerydslPredicateSupport.resumePredicate(predicate),
            QuerydslPredicateSupport.resumePageable(predicate, pageParam)
        )
        if (!persist) {
            entities.forEach(entityManager::detach)
        }
        return fromSpringData(entities)
    }

    override fun count(predicate: Predicate<ENTITY>): Long =
        querydslPredicateExecutor.count(QuerydslPredicateSupport.resumePredicate(predicate))

    override fun exists(predicate: Predicate<ENTITY>): Boolean =
        querydslPredicateExecutor.exists(QuerydslPredicateSupport.resumePredicate(predicate))
}

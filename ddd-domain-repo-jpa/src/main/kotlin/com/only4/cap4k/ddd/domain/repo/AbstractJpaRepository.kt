package com.only4.cap4k.ddd.domain.repo

import com.only4.cap4k.ddd.core.domain.repo.Predicate
import com.only4.cap4k.ddd.core.domain.repo.Repository
import com.only4.cap4k.ddd.core.share.OrderInfo
import com.only4.cap4k.ddd.core.share.PageData
import com.only4.cap4k.ddd.core.share.PageParam
import com.only4.cap4k.ddd.core.share.misc.resolveGenericTypeClass
import com.only4.cap4k.ddd.domain.repo.impl.DefaultRepositorySupervisor
import jakarta.annotation.PostConstruct
import jakarta.persistence.EntityManager
import org.springframework.transaction.annotation.Transactional

/**
 * 基于Jpa的仓储抽象类
 *
 * @author LD_moxeii
 * @date 2025/07/29
 */
open class AbstractJpaRepository<ENTITY : Any, ID : Any> internal constructor(
    private val provider: JpaRepositoryProvider<ENTITY, ID>,
) : Repository<ENTITY> {
    protected constructor(
        entityClass: Class<ENTITY>,
        entityManager: EntityManager,
    ) : this(EntityManagerJpaRepositoryProvider(entityClass, entityManager))

    @PostConstruct
    fun init() {
        DefaultRepositorySupervisor.registerPredicateEntityClassReflector(JpaPredicate::class.java) { predicate ->
            JpaPredicateSupport.reflectEntityClass(predicate)
        }
        DefaultRepositorySupervisor.registerRepositoryEntityClassReflector(AbstractJpaRepository::class.java) { repository ->
            resolveGenericTypeClass(
                repository, 0,
                AbstractJpaRepository::class.java
            )
        }
    }

    override fun supportPredicateClass(): Class<*> = JpaPredicate::class.java

    @Transactional(readOnly = true)
    override fun find(
        predicate: Predicate<ENTITY>,
        orders: Collection<OrderInfo>,
    ): List<ENTITY> {
        val ids = JpaPredicateSupport.resumeIds<ENTITY, ID>(predicate)
        val specification = JpaPredicateSupport.resumeSpecification(predicate)
        return when {
            ids != null -> provider.findAllById(ids, toSpringData(orders))
            specification != null -> provider.findAll(specification, toSpringData(orders))
            else -> emptyList()
        }
    }

    @Transactional(readOnly = true)
    override fun find(
        predicate: Predicate<ENTITY>,
        pageParam: PageParam,
    ): List<ENTITY> {
        val ids = JpaPredicateSupport.resumeIds<ENTITY, ID>(predicate)
        val specification = JpaPredicateSupport.resumeSpecification(predicate)
        return when {
            ids != null -> provider.findAllById(ids, toSpringData(pageParam)).content
            specification != null -> provider.findAll(specification, toSpringData(pageParam)).content
            else -> emptyList()
        }
    }

    @Transactional(readOnly = true)
    override fun findOne(
        predicate: Predicate<ENTITY>,
    ): ENTITY? {
        val id = JpaPredicateSupport.resumeId<ENTITY, ID>(predicate)
        val specification = JpaPredicateSupport.resumeSpecification(predicate)
        return when {
            id != null -> provider.findById(id).orElse(null)
            specification != null -> provider.findOne(specification).orElse(null)
            else -> null
        }
    }

    @Transactional(readOnly = true)
    override fun findFirst(
        predicate: Predicate<ENTITY>,
        orders: Collection<OrderInfo>,
    ): ENTITY? {
        val page = PageParam.limit(1).apply {
            orders.forEach { orderBy(it.field, it.desc) }
        }
        val ids = JpaPredicateSupport.resumeIds<ENTITY, ID>(predicate)
        val specification = JpaPredicateSupport.resumeSpecification(predicate)
        return when {
            ids != null -> provider.findAllById(ids, toSpringData(page)).content.firstOrNull()
            specification != null -> provider.findAll(specification, toSpringData(page)).content.firstOrNull()
            else -> null
        }
    }

    @Transactional(readOnly = true)
    override fun findPage(
        predicate: Predicate<ENTITY>,
        pageParam: PageParam,
    ): PageData<ENTITY> {
        val ids = JpaPredicateSupport.resumeIds<ENTITY, ID>(predicate)
        val specification = JpaPredicateSupport.resumeSpecification(predicate)
        return when {
            ids != null -> fromSpringData(provider.findAllById(ids, toSpringData(pageParam)))
            specification != null -> fromSpringData(provider.findAll(specification, toSpringData(pageParam)))
            else -> PageData.empty(pageParam.pageSize, pageParam.pageNum)
        }
    }

    @Transactional(readOnly = true)
    override fun count(predicate: Predicate<ENTITY>): Long {
        val ids = JpaPredicateSupport.resumeIds<ENTITY, ID>(predicate)
        val specification = JpaPredicateSupport.resumeSpecification(predicate)
        return when {
            ids != null -> provider.countByIds(ids)
            specification != null -> provider.count(specification)
            else -> 0
        }
    }

    @Transactional(readOnly = true)
    override fun exists(predicate: Predicate<ENTITY>): Boolean {
        val ids = JpaPredicateSupport.resumeIds<ENTITY, ID>(predicate)
        val specification = JpaPredicateSupport.resumeSpecification(predicate)
        return when {
            ids != null -> provider.existsByIds(ids)
            specification != null -> provider.exists(specification)
            else -> false
        }
    }
}

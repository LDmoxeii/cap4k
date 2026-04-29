package com.only4.cap4k.ddd.domain.repo

import com.only4.cap4k.ddd.core.domain.repo.AggregateLoadPlan
import com.only4.cap4k.ddd.core.domain.repo.Predicate
import com.only4.cap4k.ddd.core.domain.repo.Repository
import com.only4.cap4k.ddd.core.share.OrderInfo
import com.only4.cap4k.ddd.core.share.PageData
import com.only4.cap4k.ddd.core.share.PageParam
import com.only4.cap4k.ddd.core.share.misc.resolveGenericTypeClass
import com.only4.cap4k.ddd.domain.repo.impl.DefaultRepositorySupervisor
import jakarta.annotation.PostConstruct
import jakarta.persistence.EntityManager
import jakarta.persistence.OneToMany
import jakarta.persistence.PersistenceContext
import org.hibernate.Hibernate
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.transaction.annotation.Transactional

/**
 * 基于Jpa的仓储抽象类
 *
 * @author LD_moxeii
 * @date 2025/07/29
 */
open class AbstractJpaRepository<ENTITY : Any, ID>(
    private val jpaSpecificationExecutor: JpaSpecificationExecutor<ENTITY>,
    private val jpaRepository: JpaRepository<ENTITY, ID>
) : Repository<ENTITY> {

    @PersistenceContext
    lateinit var entityManager: EntityManager

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
        persist: Boolean
    ): List<ENTITY> =
        find(predicate, orders, persist, AggregateLoadPlan.DEFAULT)

    @Transactional(readOnly = true)
    override fun find(
        predicate: Predicate<ENTITY>,
        orders: Collection<OrderInfo>,
        persist: Boolean,
        loadPlan: AggregateLoadPlan
    ): List<ENTITY> {
        val entities = when {
            JpaPredicateSupport.resumeIds<ENTITY, ID>(predicate) != null -> {
                val ids = JpaPredicateSupport.resumeIds<ENTITY, ID>(predicate)!!
                if (ids.iterator().hasNext()) {
                    jpaRepository.findAllById(ids)
                } else {
                    emptyList()
                }
            }

            JpaPredicateSupport.resumeSpecification(predicate) != null -> {
                jpaSpecificationExecutor.findAll(
                    JpaPredicateSupport.resumeSpecification(predicate)!!,
                    toSpringData(orders)
                )
            }

            else -> emptyList()
        }

        applyLoadPlan(entities, loadPlan)

        if (!persist && entities.isNotEmpty()) {
            entities.forEach { entityManager.detach(it) }
        }
        return entities
    }

    @Transactional(readOnly = true)
    override fun find(
        predicate: Predicate<ENTITY>,
        pageParam: PageParam,
        persist: Boolean
    ): List<ENTITY> =
        find(predicate, pageParam, persist, AggregateLoadPlan.DEFAULT)

    @Transactional(readOnly = true)
    override fun find(
        predicate: Predicate<ENTITY>,
        pageParam: PageParam,
        persist: Boolean,
        loadPlan: AggregateLoadPlan
    ): List<ENTITY> {
        val entities = when {
            JpaPredicateSupport.resumeIds<ENTITY, ID>(predicate) != null -> {
                val ids = JpaPredicateSupport.resumeIds<ENTITY, ID>(predicate)!!
                if (ids.iterator().hasNext()) {
                    jpaRepository.findAllById(ids)
                } else {
                    emptyList()
                }
            }

            JpaPredicateSupport.resumeSpecification(predicate) != null -> {
                val page = jpaSpecificationExecutor.findAll(
                    JpaPredicateSupport.resumeSpecification(predicate)!!,
                    toSpringData(pageParam)
                )
                page.content
            }

            else -> emptyList()
        }

        applyLoadPlan(entities, loadPlan)

        if (!persist && entities.isNotEmpty()) {
            entities.forEach(entityManager::detach)
        }
        return entities
    }

    @Transactional(readOnly = true)
    override fun findOne(
        predicate: Predicate<ENTITY>,
        persist: Boolean
    ): ENTITY? =
        findOne(predicate, persist, AggregateLoadPlan.DEFAULT)

    @Transactional(readOnly = true)
    override fun findOne(
        predicate: Predicate<ENTITY>,
        persist: Boolean,
        loadPlan: AggregateLoadPlan
    ): ENTITY? {
        val entity = when {
            JpaPredicateSupport.resumeId<ENTITY, ID>(predicate) != null -> {
                jpaRepository.findById(JpaPredicateSupport.resumeId(predicate)!!).orElse(null)
            }

            JpaPredicateSupport.resumeSpecification(predicate) != null -> {
                jpaSpecificationExecutor.findOne(JpaPredicateSupport.resumeSpecification(predicate)!!).orElse(null)
            }

            else -> null
        }

        entity?.let { applyLoadPlan(it, loadPlan) }

        if (!persist && entity != null) {
            entityManager.detach(entity)
        }
        return entity
    }

    @Transactional(readOnly = true)
    override fun findFirst(
        predicate: Predicate<ENTITY>,
        orders: Collection<OrderInfo>,
        persist: Boolean
    ): ENTITY? =
        findFirst(predicate, orders, persist, AggregateLoadPlan.DEFAULT)

    @Transactional(readOnly = true)
    override fun findFirst(
        predicate: Predicate<ENTITY>,
        orders: Collection<OrderInfo>,
        persist: Boolean,
        loadPlan: AggregateLoadPlan
    ): ENTITY? {
        val entity = when {
            JpaPredicateSupport.resumeId<ENTITY, ID>(predicate) != null -> {
                jpaRepository.findById(JpaPredicateSupport.resumeId(predicate)!!).orElse(null)
            }

            JpaPredicateSupport.resumeSpecification(predicate) != null -> {
                val page = PageParam.limit(1).apply {
                    orders.forEach { orderBy(it.field, it.desc) }
                }
                jpaSpecificationExecutor.findAll(
                    JpaPredicateSupport.resumeSpecification(predicate)!!,
                    toSpringData(page)
                ).content.firstOrNull()
            }

            else -> null
        }

        entity?.let { applyLoadPlan(it, loadPlan) }

        if (!persist && entity != null) {
            entityManager.detach(entity)
        }
        return entity
    }

    @Transactional(readOnly = true)
    override fun findPage(
        predicate: Predicate<ENTITY>,
        pageParam: PageParam,
        persist: Boolean
    ): PageData<ENTITY> =
        findPage(predicate, pageParam, persist, AggregateLoadPlan.DEFAULT)

    @Transactional(readOnly = true)
    override fun findPage(
        predicate: Predicate<ENTITY>,
        pageParam: PageParam,
        persist: Boolean,
        loadPlan: AggregateLoadPlan
    ): PageData<ENTITY> {
        val pageData = when {
            JpaPredicateSupport.resumeIds<ENTITY, ID>(predicate) != null -> {
                val ids = JpaPredicateSupport.resumeIds<ENTITY, ID>(predicate)!!
                if (ids.iterator().hasNext()) {
                    val entities = jpaRepository.findAllById(ids)
                        .drop((pageParam.pageNum - 1) * pageParam.pageSize)
                        .take(pageParam.pageSize)
                    PageData.create(pageParam, entities.size.toLong(), entities)
                } else {
                    PageData.empty(pageParam.pageSize)
                }
            }

            JpaPredicateSupport.resumeSpecification(predicate) != null -> {
                val page = jpaSpecificationExecutor.findAll(
                    JpaPredicateSupport.resumeSpecification(predicate)!!,
                    toSpringData(pageParam)
                )
                fromSpringData(page)
            }

            else -> PageData.empty(pageParam.pageSize)
        }

        applyLoadPlan(pageData.list, loadPlan)

        if (!persist && pageData.list.isNotEmpty()) {
            pageData.list.forEach(entityManager::detach)
        }
        return pageData
    }

    private fun applyLoadPlan(entities: Iterable<ENTITY>, loadPlan: AggregateLoadPlan) {
        if (loadPlan != AggregateLoadPlan.WHOLE_AGGREGATE) return
        val visited = mutableSetOf<Int>()
        entities.forEach { entity -> initializeOwnedCollections(entity, visited) }
    }

    private fun applyLoadPlan(entity: ENTITY, loadPlan: AggregateLoadPlan) {
        if (loadPlan != AggregateLoadPlan.WHOLE_AGGREGATE) return
        initializeOwnedCollections(entity, mutableSetOf())
    }

    private fun initializeOwnedCollections(entity: Any, visited: MutableSet<Int>) {
        val identity = System.identityHashCode(entity)
        if (!visited.add(identity)) return

        for (field in persistentFields(Hibernate.getClass(entity))) {
            val oneToMany = field.getAnnotation(OneToMany::class.java) ?: continue
            if (!oneToMany.orphanRemoval && oneToMany.cascade.isEmpty()) continue

            field.isAccessible = true
            val value = field.get(entity) ?: continue
            Hibernate.initialize(value)

            if (value is Iterable<*>) {
                value.filterNotNull().forEach { child -> initializeOwnedCollections(child, visited) }
            }
        }
    }

    private fun persistentFields(type: Class<*>): Sequence<java.lang.reflect.Field> =
        generateSequence(type) { current ->
            current.superclass?.takeIf { it != Any::class.java }
        }.flatMap { current ->
            current.declaredFields.asSequence()
        }

    override fun count(predicate: Predicate<ENTITY>): Long {
        return when {
            JpaPredicateSupport.resumeId<ENTITY, ID>(predicate) != null -> {
                if (jpaRepository.findById(JpaPredicateSupport.resumeId(predicate)!!).isPresent) 1L else 0L
            }

            JpaPredicateSupport.resumeIds<ENTITY, ID>(predicate) != null -> {
                val ids = JpaPredicateSupport.resumeIds<ENTITY, ID>(predicate)!!
                if (!ids.iterator().hasNext()) {
                    0L
                } else {
                    jpaRepository.findAllById(ids).size.toLong()
                }
            }

            else -> {
                jpaSpecificationExecutor.count(JpaPredicateSupport.resumeSpecification(predicate)!!)
            }
        }
    }

    override fun exists(predicate: Predicate<ENTITY>): Boolean {
        return when {
            JpaPredicateSupport.resumeId<ENTITY, ID>(predicate) != null -> {
                jpaRepository.findById(JpaPredicateSupport.resumeId(predicate)!!).isPresent
            }

            JpaPredicateSupport.resumeIds<ENTITY, ID>(predicate) != null -> {
                val ids = JpaPredicateSupport.resumeIds<ENTITY, ID>(predicate)!!
                if (!ids.iterator().hasNext()) {
                    false
                } else {
                    jpaRepository.findAllById(ids).isNotEmpty()
                }
            }

            else -> {
                jpaSpecificationExecutor.exists(JpaPredicateSupport.resumeSpecification(predicate)!!)
            }
        }
    }
}

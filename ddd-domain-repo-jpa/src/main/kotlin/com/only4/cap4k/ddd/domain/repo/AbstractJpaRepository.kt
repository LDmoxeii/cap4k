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
        val entities = when {
            JpaPredicateSupport.resumeIds<ENTITY, ID>(predicate) != null -> {
                val ids = JpaPredicateSupport.resumeIds<ENTITY, ID>(predicate)!!
                if (ids.iterator().hasNext()) {
                    provider.findAllById(ids)
                } else {
                    emptyList()
                }
            }

            JpaPredicateSupport.resumeSpecification(predicate) != null -> {
                provider.findAll(
                    JpaPredicateSupport.resumeSpecification(predicate)!!,
                    toSpringData(orders)
                )
            }

            else -> emptyList()
        }

        return entities
    }

    @Transactional(readOnly = true)
    override fun find(
        predicate: Predicate<ENTITY>,
        pageParam: PageParam,
    ): List<ENTITY> {
        val entities = when {
            JpaPredicateSupport.resumeIds<ENTITY, ID>(predicate) != null -> {
                val ids = JpaPredicateSupport.resumeIds<ENTITY, ID>(predicate)!!
                if (ids.iterator().hasNext()) {
                    provider.findAllById(ids)
                } else {
                    emptyList()
                }
            }

            JpaPredicateSupport.resumeSpecification(predicate) != null -> {
                val page = provider.findAll(
                    JpaPredicateSupport.resumeSpecification(predicate)!!,
                    toSpringData(pageParam)
                )
                page.content
            }

            else -> emptyList()
        }

        return entities
    }

    @Transactional(readOnly = true)
    override fun findOne(
        predicate: Predicate<ENTITY>,
    ): ENTITY? {
        val entity = when {
            JpaPredicateSupport.resumeId<ENTITY, ID>(predicate) != null -> {
                provider.findById(JpaPredicateSupport.resumeId(predicate)!!).orElse(null)
            }

            JpaPredicateSupport.resumeSpecification(predicate) != null -> {
                provider.findOne(JpaPredicateSupport.resumeSpecification(predicate)!!).orElse(null)
            }

            else -> null
        }

        return entity
    }

    @Transactional(readOnly = true)
    override fun findFirst(
        predicate: Predicate<ENTITY>,
        orders: Collection<OrderInfo>,
    ): ENTITY? {
        val entity = when {
            JpaPredicateSupport.resumeId<ENTITY, ID>(predicate) != null -> {
                provider.findById(JpaPredicateSupport.resumeId(predicate)!!).orElse(null)
            }

            JpaPredicateSupport.resumeSpecification(predicate) != null -> {
                val page = PageParam.limit(1).apply {
                    orders.forEach { orderBy(it.field, it.desc) }
                }
                provider.findAll(
                    JpaPredicateSupport.resumeSpecification(predicate)!!,
                    toSpringData(page)
                ).content.firstOrNull()
            }

            else -> null
        }

        return entity
    }

    @Transactional(readOnly = true)
    override fun findPage(
        predicate: Predicate<ENTITY>,
        pageParam: PageParam,
    ): PageData<ENTITY> {
        val pageData = when {
            JpaPredicateSupport.resumeIds<ENTITY, ID>(predicate) != null -> {
                val ids = JpaPredicateSupport.resumeIds<ENTITY, ID>(predicate)!!
                if (ids.iterator().hasNext()) {
                    val entities = provider.findAllById(ids)
                        .drop((pageParam.pageNum - 1) * pageParam.pageSize)
                        .take(pageParam.pageSize)
                    PageData.create(pageParam, entities.size.toLong(), entities)
                } else {
                    PageData.empty(pageParam.pageSize)
                }
            }

            JpaPredicateSupport.resumeSpecification(predicate) != null -> {
                val page = provider.findAll(
                    JpaPredicateSupport.resumeSpecification(predicate)!!,
                    toSpringData(pageParam)
                )
                fromSpringData(page)
            }

            else -> PageData.empty(pageParam.pageSize)
        }

        return pageData
    }

    @Transactional(readOnly = true)
    override fun count(predicate: Predicate<ENTITY>): Long {
        return when {
            JpaPredicateSupport.resumeId<ENTITY, ID>(predicate) != null -> {
                if (provider.findById(JpaPredicateSupport.resumeId(predicate)!!).isPresent) 1L else 0L
            }

            JpaPredicateSupport.resumeIds<ENTITY, ID>(predicate) != null -> {
                val ids = JpaPredicateSupport.resumeIds<ENTITY, ID>(predicate)!!
                if (!ids.iterator().hasNext()) {
                    0L
                } else {
                    provider.findAllById(ids).size.toLong()
                }
            }

            else -> {
                provider.count(JpaPredicateSupport.resumeSpecification(predicate)!!)
            }
        }
    }

    @Transactional(readOnly = true)
    override fun exists(predicate: Predicate<ENTITY>): Boolean {
        return when {
            JpaPredicateSupport.resumeId<ENTITY, ID>(predicate) != null -> {
                provider.findById(JpaPredicateSupport.resumeId(predicate)!!).isPresent
            }

            JpaPredicateSupport.resumeIds<ENTITY, ID>(predicate) != null -> {
                val ids = JpaPredicateSupport.resumeIds<ENTITY, ID>(predicate)!!
                if (!ids.iterator().hasNext()) {
                    false
                } else {
                    provider.findAllById(ids).isNotEmpty()
                }
            }

            else -> {
                provider.exists(JpaPredicateSupport.resumeSpecification(predicate)!!)
            }
        }
    }
}

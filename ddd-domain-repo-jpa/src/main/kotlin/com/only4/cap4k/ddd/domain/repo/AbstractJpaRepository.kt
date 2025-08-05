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
import jakarta.persistence.PersistenceContext
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import java.util.*

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

    override fun find(predicate: Predicate<ENTITY>, orders: Collection<OrderInfo>, persist: Boolean): List<ENTITY> {
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

        if (!persist && entities.isNotEmpty()) {
            entities.forEach { entityManager.detach(it) }
        }
        return entities
    }

    override fun find(predicate: Predicate<ENTITY>, pageParam: PageParam, persist: Boolean): List<ENTITY> {
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

        if (!persist && entities.isNotEmpty()) {
            entities.forEach(entityManager::detach)
        }
        return entities
    }

    override fun findOne(predicate: Predicate<ENTITY>, persist: Boolean): Optional<ENTITY> {
        val entity = when {
            JpaPredicateSupport.resumeId<ENTITY, ID>(predicate) != null -> {
                jpaRepository.findById(JpaPredicateSupport.resumeId(predicate)!!)
            }

            JpaPredicateSupport.resumeSpecification(predicate) != null -> {
                jpaSpecificationExecutor.findOne(JpaPredicateSupport.resumeSpecification(predicate)!!)
            }

            else -> Optional.empty()
        }

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
        val entity = when {
            JpaPredicateSupport.resumeId<ENTITY, ID>(predicate) != null -> {
                jpaRepository.findById(JpaPredicateSupport.resumeId(predicate)!!)
            }

            JpaPredicateSupport.resumeSpecification(predicate) != null -> {
                val page = PageParam.limit(1).apply {
                    orders.forEach { orderBy(it.field, it.desc) }
                }
                jpaSpecificationExecutor.findAll(
                    JpaPredicateSupport.resumeSpecification(predicate)!!,
                    toSpringData(page)
                ).stream().findFirst()
            }

            else -> Optional.empty()
        }

        if (!persist) {
            entity.ifPresent(entityManager::detach)
        }
        return entity
    }

    override fun findPage(predicate: Predicate<ENTITY>, pageParam: PageParam, persist: Boolean): PageData<ENTITY> {
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

        if (!persist && pageData.list.isNotEmpty()) {
            pageData.list.forEach(entityManager::detach)
        }
        return pageData
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

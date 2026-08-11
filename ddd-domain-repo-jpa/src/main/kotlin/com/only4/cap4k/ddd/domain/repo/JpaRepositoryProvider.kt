package com.only4.cap4k.ddd.domain.repo

import jakarta.persistence.EntityManager
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.domain.Specification
import org.springframework.data.jpa.repository.support.JpaEntityInformation
import org.springframework.data.jpa.repository.support.JpaEntityInformationSupport
import org.springframework.data.jpa.repository.support.SimpleJpaRepository
import java.util.Optional

/**
 * Provider-private bridge between the cap4k repository contract and Spring Data JPA.
 *
 * Generated aggregate carriers depend only on [EntityManager]; Spring Data repository
 * interfaces remain an implementation detail of this module.
 */
internal interface JpaRepositoryProvider<ENTITY : Any, ID : Any> {
    fun findAllById(ids: Iterable<ID>, sort: Sort): List<ENTITY>

    fun findAllById(ids: Iterable<ID>, pageable: Pageable): Page<ENTITY>

    fun countByIds(ids: Iterable<ID>): Long

    fun existsByIds(ids: Iterable<ID>): Boolean

    fun findAll(specification: Specification<ENTITY>, sort: Sort): List<ENTITY>

    fun findAll(specification: Specification<ENTITY>, pageable: Pageable): Page<ENTITY>

    fun findById(id: ID): Optional<ENTITY>

    fun findOne(specification: Specification<ENTITY>): Optional<ENTITY>

    fun count(specification: Specification<ENTITY>): Long

    fun exists(specification: Specification<ENTITY>): Boolean
}

internal class EntityManagerJpaRepositoryProvider<ENTITY : Any, ID : Any>(
    entityClass: Class<ENTITY>,
    entityManager: EntityManager,
) : JpaRepositoryProvider<ENTITY, ID> {
    private val delegate = SimpleJpaRepository<ENTITY, ID>(entityClass, entityManager)

    @Suppress("UNCHECKED_CAST")
    private val entityInformation =
        JpaEntityInformationSupport.getEntityInformation(entityClass, entityManager) as JpaEntityInformation<ENTITY, ID>

    private val idAttributeNames = entityInformation.idAttributeNames.toList().also { names ->
        check(names.isNotEmpty()) {
            "JPA entity must declare at least one identifier attribute: ${entityClass.name}"
        }
    }

    override fun findAllById(ids: Iterable<ID>, sort: Sort): List<ENTITY> {
        val snapshot = snapshot(ids)
        return if (snapshot.isEmpty()) {
            emptyList()
        } else {
            delegate.findAll(idSpecification(snapshot), stableSort(sort))
        }
    }

    override fun findAllById(ids: Iterable<ID>, pageable: Pageable): Page<ENTITY> {
        val snapshot = snapshot(ids)
        return if (snapshot.isEmpty()) {
            PageImpl(emptyList(), pageable, 0)
        } else {
            delegate.findAll(idSpecification(snapshot), stablePageable(pageable))
        }
    }

    override fun countByIds(ids: Iterable<ID>): Long {
        val snapshot = snapshot(ids)
        return if (snapshot.isEmpty()) 0 else delegate.count(idSpecification(snapshot))
    }

    override fun existsByIds(ids: Iterable<ID>): Boolean {
        val snapshot = snapshot(ids)
        return snapshot.isNotEmpty() && delegate.exists(idSpecification(snapshot))
    }

    override fun findAll(specification: Specification<ENTITY>, sort: Sort): List<ENTITY> =
        delegate.findAll(specification, stableSort(sort))

    override fun findAll(specification: Specification<ENTITY>, pageable: Pageable): Page<ENTITY> =
        delegate.findAll(specification, stablePageable(pageable))

    override fun findById(id: ID): Optional<ENTITY> = delegate.findById(id)

    override fun findOne(specification: Specification<ENTITY>): Optional<ENTITY> = delegate.findOne(specification)

    override fun count(specification: Specification<ENTITY>): Long = delegate.count(specification)

    override fun exists(specification: Specification<ENTITY>): Boolean = delegate.exists(specification)

    private fun snapshot(ids: Iterable<ID>): List<ID> = ids.toList().distinct()

    private fun idSpecification(ids: Collection<ID>): Specification<ENTITY> = Specification { root, _, criteriaBuilder ->
        if (!entityInformation.hasCompositeId()) {
            root.get<Any>(idAttributeNames.single()).`in`(ids)
        } else {
            criteriaBuilder.or(
                *ids.map { id ->
                    criteriaBuilder.and(
                        *idAttributeNames.map { attributeName ->
                            criteriaBuilder.equal(
                                root.get<Any>(attributeName),
                                entityInformation.getCompositeIdAttributeValue(id, attributeName),
                            )
                        }.toTypedArray(),
                    )
                }.toTypedArray(),
            )
        }
    }

    private fun stableSort(sort: Sort): Sort {
        if (sort.isUnsorted) {
            return sort
        }
        return idAttributeNames.fold(sort) { current, attributeName ->
            if (current.getOrderFor(attributeName) == null) {
                current.and(Sort.by(Sort.Order.asc(attributeName)))
            } else {
                current
            }
        }
    }

    private fun stablePageable(pageable: Pageable): Pageable {
        if (pageable.isUnpaged || pageable.sort.isUnsorted) {
            return pageable
        }
        return PageRequest.of(pageable.pageNumber, pageable.pageSize, stableSort(pageable.sort))
    }
}

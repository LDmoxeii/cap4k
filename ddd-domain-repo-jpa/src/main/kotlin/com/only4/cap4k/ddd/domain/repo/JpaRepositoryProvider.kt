package com.only4.cap4k.ddd.domain.repo

import jakarta.persistence.EntityManager
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.domain.Specification
import org.springframework.data.jpa.repository.support.SimpleJpaRepository
import java.util.Optional

/**
 * Provider-private bridge between the cap4k repository contract and Spring Data JPA.
 *
 * Generated aggregate carriers depend only on [EntityManager]; Spring Data repository
 * interfaces remain an implementation detail of this module.
 */
internal interface JpaRepositoryProvider<ENTITY : Any, ID : Any> {
    fun findAllById(ids: Iterable<ID>): List<ENTITY>

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

    override fun findAllById(ids: Iterable<ID>): List<ENTITY> = delegate.findAllById(ids)

    override fun findAll(specification: Specification<ENTITY>, sort: Sort): List<ENTITY> =
        delegate.findAll(specification, sort)

    override fun findAll(specification: Specification<ENTITY>, pageable: Pageable): Page<ENTITY> =
        delegate.findAll(specification, pageable)

    override fun findById(id: ID): Optional<ENTITY> = delegate.findById(id)

    override fun findOne(specification: Specification<ENTITY>): Optional<ENTITY> = delegate.findOne(specification)

    override fun count(specification: Specification<ENTITY>): Long = delegate.count(specification)

    override fun exists(specification: Specification<ENTITY>): Boolean = delegate.exists(specification)
}

package com.only4.cap4k.ddd.domain.repo.schema

import jakarta.persistence.criteria.CriteriaBuilder
import jakarta.persistence.criteria.Expression
import jakarta.persistence.criteria.Predicate

class RelationOptionalField<E>(
    private val backingCollectionPath: Expression<Collection<E>>,
    private val criteriaBuilder: CriteriaBuilder,
) {
    fun isNull(): Predicate = criteriaBuilder.isEmpty(backingCollectionPath)

    fun isNotNull(): Predicate = criteriaBuilder.isNotEmpty(backingCollectionPath)
}

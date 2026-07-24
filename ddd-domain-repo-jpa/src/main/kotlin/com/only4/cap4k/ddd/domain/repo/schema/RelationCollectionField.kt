package com.only4.cap4k.ddd.domain.repo.schema

import jakarta.persistence.criteria.CriteriaBuilder
import jakarta.persistence.criteria.Expression
import jakarta.persistence.criteria.Predicate

class RelationCollectionField<E>(
    private val path: Expression<Collection<E>>,
    private val criteriaBuilder: CriteriaBuilder,
) {
    fun isEmpty(): Predicate = criteriaBuilder.isEmpty(path)

    fun isNotEmpty(): Predicate = criteriaBuilder.isNotEmpty(path)
}

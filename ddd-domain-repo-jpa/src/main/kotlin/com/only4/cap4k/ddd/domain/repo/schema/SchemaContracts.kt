package com.only4.cap4k.ddd.domain.repo.schema

import jakarta.persistence.criteria.CriteriaBuilder
import jakarta.persistence.criteria.CriteriaQuery
import jakarta.persistence.criteria.Expression
import jakarta.persistence.criteria.Order
import jakarta.persistence.criteria.Predicate
import jakarta.persistence.criteria.Subquery

fun interface SchemaSpecification<E, S> {
    fun toPredicate(schema: S, criteriaQuery: CriteriaQuery<*>, criteriaBuilder: CriteriaBuilder): Predicate?
}

fun interface SubqueryConfigure<E, S> {
    fun configure(subquery: Subquery<E>, schema: S)
}

fun interface ExpressionBuilder<S, T> {
    fun build(schema: S): Expression<T>
}

fun interface PredicateBuilder<S> {
    fun build(schema: S): Predicate
}

fun interface OrderBuilder<S> {
    fun build(schema: S): Order
}

package com.only4.cap4k.ddd.domain.repo.schema

import jakarta.persistence.criteria.CriteriaBuilder
import jakarta.persistence.criteria.Predicate
import org.hibernate.query.sqm.tree.predicate.SqmPredicate

fun and(vararg predicates: Predicate?): Predicate? =
    predicates.filterNotNull().let {
        when (it.size) {
            0 -> null
            1 -> it[0]
            else -> {
                val cb = getCriteriaBuilderFromPredicate(it[0])
                cb.and(*it.toTypedArray())
            }
        }
    }

fun or(vararg predicates: Predicate?): Predicate? =
    predicates.filterNotNull().let {
        when (it.size) {
            0 -> null
            1 -> it[0]
            else -> {
                val cb = getCriteriaBuilderFromPredicate(it[0])
                cb.or(*it.toTypedArray())
            }
        }
    }

infix fun Predicate.and(other: Predicate): Predicate {
    val cb = getCriteriaBuilderFromPredicate(this)
    return cb.and(this, other)
}

infix fun Predicate.or(other: Predicate): Predicate {
    val cb = getCriteriaBuilderFromPredicate(this)
    return cb.or(this, other)
}

fun Predicate.not(): Predicate {
    val cb = getCriteriaBuilderFromPredicate(this)
    return cb.not(this)
}

private fun getCriteriaBuilderFromPredicate(predicate: Predicate): CriteriaBuilder =
    when (predicate) {
        is SqmPredicate -> {
            predicate.nodeBuilder()
        }

        else -> {
            try {
                val method = predicate.javaClass.getMethod("criteriaBuilder")
                method.invoke(predicate) as CriteriaBuilder
            } catch (e: Exception) {
                throw IllegalStateException("Unable to read CriteriaBuilder from Predicate: ${predicate.javaClass}", e)
            }
        }
    }

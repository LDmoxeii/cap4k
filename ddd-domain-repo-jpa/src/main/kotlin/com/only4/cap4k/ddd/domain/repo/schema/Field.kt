package com.only4.cap4k.ddd.domain.repo.schema

import jakarta.persistence.criteria.CriteriaBuilder
import jakarta.persistence.criteria.Expression
import jakarta.persistence.criteria.Order
import jakarta.persistence.criteria.Path
import jakarta.persistence.criteria.Predicate
import org.hibernate.query.NullPrecedence
import org.hibernate.query.SortDirection
import org.hibernate.query.sqm.tree.domain.SqmBasicValuedSimplePath
import org.hibernate.query.sqm.tree.select.SqmSortSpecification

@Suppress("UNCHECKED_CAST")
class Field<T> {
    private val path: Path<T>?
    private val criteriaBuilder: CriteriaBuilder?
    private val name: String?

    constructor(path: Path<T>, criteriaBuilder: CriteriaBuilder) {
        this.path = path
        this.criteriaBuilder = criteriaBuilder
        this.name = if (path is SqmBasicValuedSimplePath<*>) {
            path.navigablePath.localName
        } else {
            null
        }
    }

    constructor(name: String) {
        this.name = name
        this.path = null
        this.criteriaBuilder = null
    }

    protected fun _criteriaBuilder(): CriteriaBuilder? = criteriaBuilder

    fun path(): Path<T>? = path

    override fun toString(): String = name ?: ""

    fun asc(): Order =
        SqmSortSpecification(path as SqmBasicValuedSimplePath<T>, SortDirection.ASCENDING, NullPrecedence.NONE)

    fun desc(): Order =
        SqmSortSpecification(path as SqmBasicValuedSimplePath<T>, SortDirection.DESCENDING, NullPrecedence.NONE)

    fun isTrue(): Predicate = criteriaBuilder!!.isTrue(path as Expression<Boolean>)

    fun isFalse(): Predicate = criteriaBuilder!!.isFalse(path as Expression<Boolean>)

    fun isEmpty(): Predicate = criteriaBuilder!!.isEmpty(path as Expression<Collection<*>>)

    fun isNotEmpty(): Predicate = criteriaBuilder!!.isNotEmpty(path as Expression<Collection<*>>)

    fun equal(value: Any?): Predicate = criteriaBuilder!!.equal(path, value)

    fun equal(value: Expression<*>): Predicate = criteriaBuilder!!.equal(path, value)

    fun notEqual(value: Any?): Predicate = criteriaBuilder!!.notEqual(path, value)

    fun notEqual(value: Expression<*>): Predicate = criteriaBuilder!!.notEqual(path, value)

    fun isNull(): Predicate = criteriaBuilder!!.isNull(path)

    fun isNotNull(): Predicate = criteriaBuilder!!.isNotNull(path)

    fun <Y : Comparable<Y>> greaterThan(value: Y): Predicate =
        criteriaBuilder!!.greaterThan(path as Expression<Y>, value)

    fun <Y : Comparable<Y>> greaterThan(value: Expression<out Y>): Predicate =
        criteriaBuilder!!.greaterThan(path as Expression<Y>, value)

    fun <Y : Comparable<Y>> greaterThanOrEqualTo(value: Y): Predicate =
        criteriaBuilder!!.greaterThanOrEqualTo(path as Expression<Y>, value)

    fun <Y : Comparable<Y>> greaterThanOrEqualTo(value: Expression<out Y>): Predicate =
        criteriaBuilder!!.greaterThanOrEqualTo(path as Expression<Y>, value)

    fun <Y : Comparable<Y>> lessThan(value: Y): Predicate =
        criteriaBuilder!!.lessThan(path as Expression<Y>, value)

    fun <Y : Comparable<Y>> lessThan(value: Expression<out Y>): Predicate =
        criteriaBuilder!!.lessThan(path as Expression<Y>, value)

    fun <Y : Comparable<Y>> lessThanOrEqualTo(value: Y): Predicate =
        criteriaBuilder!!.lessThanOrEqualTo(path as Expression<Y>, value)

    fun <Y : Comparable<Y>> lessThanOrEqualTo(value: Expression<out Y>): Predicate =
        criteriaBuilder!!.lessThanOrEqualTo(path as Expression<Y>, value)

    fun <Y : Comparable<Y>> between(value1: Y, value2: Y): Predicate =
        criteriaBuilder!!.between(path as Expression<Y>, value1, value2)

    fun <Y : Comparable<Y>> between(value1: Expression<out Y>, value2: Expression<out Y>): Predicate =
        criteriaBuilder!!.between(path as Expression<Y>, value1, value2)

    fun `in`(vararg values: Any?): Predicate = `in`(listOf(*values))

    fun `in`(values: Collection<*>): Predicate {
        val predicate = criteriaBuilder!!.`in`(path)
        values.forEach { value ->
            predicate.value(value as T)
        }
        return predicate
    }

    fun `in`(vararg expressions: Expression<*>): Predicate {
        val predicate = criteriaBuilder!!.`in`(path)
        expressions.forEach { expression ->
            predicate.value(expression as Expression<out T>)
        }
        return predicate
    }

    fun notIn(vararg values: Any?): Predicate = notIn(listOf(*values))

    fun notIn(values: Collection<*>): Predicate = criteriaBuilder!!.not(`in`(values))

    fun notIn(vararg expressions: Expression<*>): Predicate = criteriaBuilder!!.not(`in`(*expressions))

    fun like(value: Expression<String>): Predicate = criteriaBuilder!!.like(path as Expression<String>, value)

    fun notLike(value: Expression<String>): Predicate = criteriaBuilder!!.notLike(path as Expression<String>, value)

    infix fun eq(value: Any?): Predicate = equal(value)

    infix fun neq(value: Any?): Predicate = notEqual(value)

    infix fun <Y : Comparable<Y>> gt(value: Y): Predicate = greaterThan(value)

    infix fun <Y : Comparable<Y>> ge(value: Y): Predicate = greaterThanOrEqualTo(value)

    infix fun <Y : Comparable<Y>> lt(value: Y): Predicate = lessThan(value)

    infix fun <Y : Comparable<Y>> le(value: Y): Predicate = lessThanOrEqualTo(value)

    infix fun like(value: String): Predicate = criteriaBuilder!!.like(path as Expression<String>, value)

    infix fun notLike(value: String): Predicate = criteriaBuilder!!.notLike(path as Expression<String>, value)

    infix fun `eq?`(value: Any?): Predicate? = if (value == null) null else equal(value)

    infix fun `neq?`(value: Any?): Predicate? = if (value == null) null else notEqual(value)

    infix fun `like?`(value: String?): Predicate? = if (value == null) null else like(value)

    infix fun `notLike?`(value: String?): Predicate? = if (value == null) null else notLike(value)

    infix fun <Y : Comparable<Y>> `gt?`(value: Y?): Predicate? = if (value == null) null else greaterThan(value)

    infix fun <Y : Comparable<Y>> `ge?`(value: Y?): Predicate? = if (value == null) null else greaterThanOrEqualTo(value)

    infix fun <Y : Comparable<Y>> `lt?`(value: Y?): Predicate? = if (value == null) null else lessThan(value)

    infix fun <Y : Comparable<Y>> `le?`(value: Y?): Predicate? = if (value == null) null else lessThanOrEqualTo(value)

    infix fun `in?`(values: Collection<*>?): Predicate? = if (values.isNullOrEmpty()) null else `in`(values)

    infix fun `notIn?`(values: Collection<*>?): Predicate? = if (values.isNullOrEmpty()) null else notIn(values)
}

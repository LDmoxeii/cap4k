package com.only4.cap4k.ddd.domain.repo

import com.only4.cap4k.ddd.core.domain.aggregate.Aggregate
import com.only4.cap4k.ddd.core.domain.aggregate.AggregatePredicate
import com.only4.cap4k.ddd.core.domain.aggregate.ValueObject
import com.only4.cap4k.ddd.core.domain.repo.Predicate
import com.only4.cap4k.ddd.domain.aggregate.JpaAggregatePredicate
import org.springframework.data.jpa.domain.Specification

/**
 * Jpa仓储检索断言
 *
 * @author LD_moxeii
 * @date 2025/07/28
 */
class JpaPredicate<ENTITY : Any>(
    val entityClass: Class<ENTITY>,
    val spec: Specification<ENTITY>? = null,
    val ids: Iterable<Any>? = null,
    val valueObject: ValueObject<*>? = null
) : Predicate<ENTITY> {

    fun <AGGREGATE : Aggregate<ENTITY>> toAggregatePredicate(
        aggregateClass: Class<AGGREGATE>
    ): AggregatePredicate<AGGREGATE, ENTITY> = JpaAggregatePredicate.byPredicate(aggregateClass, this)


    companion object {
        fun <ENTITY : Any> byId(entityClass: Class<ENTITY>, id: Any): JpaPredicate<ENTITY> =
            JpaPredicate(entityClass, ids = listOf(id))


        fun <ENTITY : Any> byIds(entityClass: Class<ENTITY>, ids: Iterable<Any>): JpaPredicate<ENTITY> =
            JpaPredicate(entityClass, ids = ids)


        fun <VALUE_OBJECT : ValueObject<*>> byValueObject(valueObject: VALUE_OBJECT): JpaPredicate<VALUE_OBJECT> =
            JpaPredicate(
                valueObject.javaClass,
                ids = listOf(valueObject.hash()),
                valueObject = valueObject
            )


        fun <ENTITY : Any> bySpecification(
            entityClass: Class<ENTITY>,
            specification: Specification<ENTITY>
        ): JpaPredicate<ENTITY> = JpaPredicate(entityClass, specification)
    }
}

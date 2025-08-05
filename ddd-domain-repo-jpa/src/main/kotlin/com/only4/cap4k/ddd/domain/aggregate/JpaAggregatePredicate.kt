package com.only4.cap4k.ddd.domain.aggregate

import com.only4.cap4k.ddd.core.domain.aggregate.Aggregate
import com.only4.cap4k.ddd.core.domain.aggregate.AggregatePredicate
import com.only4.cap4k.ddd.core.domain.aggregate.ValueObject
import com.only4.cap4k.ddd.core.domain.repo.Predicate
import com.only4.cap4k.ddd.core.share.misc.resolveGenericTypeClass
import com.only4.cap4k.ddd.domain.repo.JpaPredicate
import org.springframework.data.jpa.domain.Specification

/**
 * Jpa聚合检索断言
 *
 * @author LD_moxeii
 * @date 2025/07/28
 */
class JpaAggregatePredicate<AGGREGATE : Aggregate<ENTITY>, ENTITY : Any>(
    val aggregateClass: Class<AGGREGATE>,
    val predicate: Predicate<ENTITY>
) : AggregatePredicate<AGGREGATE, ENTITY>, Predicate<ENTITY> {

    companion object {
        private fun <AGGREGATE : Aggregate<ENTITY>, ENTITY : Any> getEntityClass(
            aggregateClass: Class<AGGREGATE>
        ): Class<ENTITY> {
            @Suppress("UNCHECKED_CAST")
            return resolveGenericTypeClass(
                aggregateClass, 0,
                Aggregate::class.java, Aggregate.Default::class.java
            ) as Class<ENTITY>
        }

        @JvmStatic
        fun <AGGREGATE : Aggregate<ENTITY>, ENTITY : Any> byId(
            aggregateClass: Class<AGGREGATE>,
            id: Any
        ): AggregatePredicate<AGGREGATE, ENTITY> = JpaAggregatePredicate(
            aggregateClass,
            JpaPredicate.byId(getEntityClass(aggregateClass), id)
        )

        @JvmStatic
        fun <AGGREGATE : Aggregate<ENTITY>, ENTITY : Any> byIds(
            aggregateClass: Class<AGGREGATE>,
            ids: Iterable<Any>
        ): AggregatePredicate<AGGREGATE, ENTITY> =
            JpaAggregatePredicate(
                aggregateClass,
                JpaPredicate.byIds(getEntityClass(aggregateClass), ids)
            )

        @JvmStatic
        fun <AGGREGATE : Aggregate<VALUE_OBJECT>, VALUE_OBJECT : ValueObject<*>> byValueObject(
            valueObject: AGGREGATE
        ): AggregatePredicate<AGGREGATE, VALUE_OBJECT> =
            JpaAggregatePredicate(
                valueObject.javaClass,
                JpaPredicate.byValueObject(valueObject._unwrap())
            )

        @JvmStatic
        fun <AGGREGATE : Aggregate<ENTITY>, ENTITY : Any> bySpecification(
            aggregateClass: Class<AGGREGATE>,
            specification: Specification<ENTITY>
        ): AggregatePredicate<AGGREGATE, ENTITY> =
            JpaAggregatePredicate(
                aggregateClass,
                JpaPredicate.bySpecification(getEntityClass(aggregateClass), specification)
            )

        @JvmStatic
        fun <AGGREGATE : Aggregate<ENTITY>, ENTITY : Any> byPredicate(
            aggregateClass: Class<AGGREGATE>,
            predicate: Predicate<ENTITY>
        ): AggregatePredicate<AGGREGATE, ENTITY> =
            JpaAggregatePredicate(aggregateClass, predicate)
    }
}

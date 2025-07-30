package com.only4.cap4k.ddd.domain.repo

import com.only4.cap4k.ddd.core.domain.aggregate.Aggregate
import com.only4.cap4k.ddd.core.domain.aggregate.ValueObject
import com.only4.cap4k.ddd.core.domain.repo.AggregatePredicate
import com.only4.cap4k.ddd.core.domain.repo.Predicate
import org.springframework.data.jpa.domain.Specification

/**
 * Jpa仓储检索断言
 *
 * @author LD_moxeii
 * @date 2025/07/28
 */
class JpaPredicate<ENTITY : Any>(
    val entityClass: Class<ENTITY>,
    val spec: Specification<ENTITY>?,
    val ids: Iterable<Any>?,
    val valueObject: ValueObject<*>?
) : Predicate<ENTITY> {

    fun <AGGREGATE : Aggregate<ENTITY>> toAggregatePredicate(
        aggregateClass: Class<AGGREGATE>
    ): AggregatePredicate<AGGREGATE, ENTITY> = JpaAggregatePredicate.byPredicate(aggregateClass, this)


    companion object {
        fun <ENTITY : Any> byId(entityClass: Class<ENTITY>, id: Any): JpaPredicate<ENTITY> =
            JpaPredicate(entityClass, null, listOf(id), null)


        fun <ENTITY : Any> byIds(entityClass: Class<ENTITY>, ids: Iterable<Any>): JpaPredicate<ENTITY> =
            JpaPredicate(entityClass, null, ids, null)


        fun <VALUE_OBJECT : ValueObject<*>> byValueObject(valueObject: VALUE_OBJECT): JpaPredicate<VALUE_OBJECT> =
            JpaPredicate(
                valueObject.javaClass,
                null,
                listOf(valueObject.hash()),
                valueObject
            )


        fun <ENTITY : Any> bySpecification(
            entityClass: Class<ENTITY>,
            specification: Specification<ENTITY>
        ): JpaPredicate<ENTITY> = JpaPredicate(entityClass, specification, null, null)
    }
}

package com.only4.cap4k.ddd.domain.repo

import com.only4.cap4k.ddd.core.domain.aggregate.Aggregate
import com.only4.cap4k.ddd.core.domain.repo.AggregatePredicate
import com.only4.cap4k.ddd.core.domain.repo.Predicate

/**
 * Jpa聚合检索断言Support
 *
 * @author binking338
 * @date 2025/1/12
 */
object JpaAggregatePredicateSupport {

    /**
     * 获取实体仓储检索断言
     *
     * @param predicate
     * @return
     */
    fun <AGGREGATE : Aggregate<ENTITY>, ENTITY : Any> getPredicate(
        predicate: AggregatePredicate<AGGREGATE, ENTITY>
    ): Predicate<ENTITY> {
        return (predicate as JpaAggregatePredicate<AGGREGATE, ENTITY>).predicate
    }

    /**
     * 获取断言聚合类型
     *
     * @param predicate
     * @return
     */
    @Suppress("UNCHECKED_CAST")
    fun <AGGREGATE : Aggregate<*>> reflectAggregateClass(
        predicate: AggregatePredicate<AGGREGATE, *>
    ): Class<AGGREGATE> {
        return (predicate as JpaAggregatePredicate<AGGREGATE, *>).aggregateClass
    }
}

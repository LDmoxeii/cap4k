package com.only4.cap4k.ddd.domain.aggregate

import com.only4.cap4k.ddd.core.domain.aggregate.Aggregate
import com.only4.cap4k.ddd.core.domain.aggregate.AggregatePredicate
import com.only4.cap4k.ddd.core.domain.repo.Predicate

/**
 * Jpa聚合检索断言Support
 *
 * @author LD_moxeii
 * @date 2025/07/30
 */
object JpaAggregatePredicateSupport {

    /**
     * 获取实体仓储检索断言
     *
     * @param predicate
     * @return
     */
    @Suppress("UNCHECKED_CAST")
    fun <AGGREGATE : Aggregate<*>> getPredicate(predicate: AggregatePredicate<AGGREGATE, *>): Predicate<*> =
        (predicate as JpaAggregatePredicate<AGGREGATE, *>).predicate

    /**
     * 获取断言聚合类型
     *
     * @param predicate
     * @return
     */
    @Suppress("UNCHECKED_CAST")
    fun <AGGREGATE : Aggregate<*>> reflectAggregateClass(
        predicate: AggregatePredicate<AGGREGATE, *>
    ): Class<AGGREGATE> =
        (predicate as JpaAggregatePredicate<AGGREGATE, *>).aggregateClass
}

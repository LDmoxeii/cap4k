package com.only4.cap4k.ddd.domain.repo

import com.only4.cap4k.ddd.core.domain.aggregate.Aggregate
import com.only4.cap4k.ddd.core.domain.repo.AggregatePredicate
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
    fun <AGGREGATE : Aggregate<out Any>> getPredicate(predicate: AggregatePredicate<AGGREGATE, out Any>): Predicate<out Any> =
        (predicate as JpaAggregatePredicate<AGGREGATE, out Any>).predicate

    /**
     * 获取断言聚合类型
     *
     * @param predicate
     * @return
     */
    @Suppress("UNCHECKED_CAST")
    fun <AGGREGATE : Aggregate<out Any>> reflectAggregateClass(
        predicate: AggregatePredicate<AGGREGATE, out Any>
    ): Class<AGGREGATE> =
        (predicate as JpaAggregatePredicate<AGGREGATE, out Any>).aggregateClass
}

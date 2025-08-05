package com.only4.cap4k.ddd.domain.repo.querydsl

import com.only4.cap4k.ddd.core.domain.aggregate.Aggregate
import com.only4.cap4k.ddd.core.domain.aggregate.AggregatePredicate
import com.only4.cap4k.ddd.domain.aggregate.JpaAggregatePredicate
import com.querydsl.core.BooleanBuilder
import com.querydsl.core.types.OrderSpecifier
import com.querydsl.core.types.Predicate
import com.only4.cap4k.ddd.core.domain.repo.Predicate as DomainPredicate

/**
 * QueryDsl查询条件
 *
 * @author LD_moxeii
 * @date 2025/07/31
 */
class QuerydslPredicate<ENTITY : Any>(
    val entityClass: Class<ENTITY>,
    val predicate: BooleanBuilder = BooleanBuilder()
) : DomainPredicate<ENTITY> {

    val orderSpecifiers: MutableList<OrderSpecifier<*>> = mutableListOf()

    fun <AGGREGATE : Aggregate<ENTITY>> toAggregatePredicate(aggregateClass: Class<AGGREGATE>): AggregatePredicate<AGGREGATE, ENTITY> =
        JpaAggregatePredicate.byPredicate(aggregateClass, this)

    fun where(filter: Predicate): QuerydslPredicate<ENTITY> = apply {
        predicate.and(filter)
    }

    fun orderBy(vararg orderSpecifiers: OrderSpecifier<*>): QuerydslPredicate<ENTITY> = apply {
        this.orderSpecifiers.addAll(orderSpecifiers)
    }

    companion object {
        @JvmStatic
        fun <ENTITY : Any> of(entityClass: Class<ENTITY>): QuerydslPredicate<ENTITY> =
            QuerydslPredicate(entityClass)

        @JvmStatic
        fun <ENTITY : Any> byPredicate(entityClass: Class<ENTITY>, predicate: Predicate): QuerydslPredicate<ENTITY> =
            QuerydslPredicate(entityClass, BooleanBuilder(predicate))
    }
}

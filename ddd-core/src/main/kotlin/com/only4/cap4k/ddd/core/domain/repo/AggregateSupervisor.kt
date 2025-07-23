package com.only4.cap4k.ddd.core.domain.repo

import com.only4.cap4k.ddd.core.domain.aggregate.Aggregate
import com.only4.cap4k.ddd.core.domain.aggregate.AggregatePayload
import com.only4.cap4k.ddd.core.domain.aggregate.Id
import com.only4.cap4k.ddd.core.share.OrderInfo
import com.only4.cap4k.ddd.core.share.PageData
import com.only4.cap4k.ddd.core.share.PageParam
import java.util.*

/**
 * 聚合管理器
 *
 * @author binking338
 * @date 2025/1/12
 */
interface AggregateSupervisor {

    companion object {
        val instance: AggregateSupervisor = AggregateSupervisorSupport.instance
    }

    fun <AGGREGATE : Aggregate<ENTITY>, ENTITY_PAYLOAD : AggregatePayload<ENTITY>, ENTITY> create(
        clazz: Class<AGGREGATE>,
        payload: ENTITY_PAYLOAD
    ): AGGREGATE

    /**
     * 根据id获取聚合
     */
    fun <AGGREGATE : Aggregate<ENTITY>, ENTITY> getById(id: Id<AGGREGATE, *>): AGGREGATE? =
        getByIds(listOf(id), true).firstOrNull()

    /**
     * 根据id获取聚合
     */
    fun <AGGREGATE : Aggregate<ENTITY>, ENTITY> getById(id: Id<AGGREGATE, *>, persist: Boolean): AGGREGATE? =
        getByIds(listOf(id), persist).firstOrNull()

    /**
     * 根据id获取聚合
     */
    fun <AGGREGATE : Aggregate<ENTITY>, ENTITY> getByIds(ids: Iterable<Id<AGGREGATE, *>>): List<AGGREGATE> =
        getByIds(ids, true)

    /**
     * 根据id获取聚合
     */
    fun <AGGREGATE : Aggregate<ENTITY>, ENTITY> getByIds(vararg ids: Id<AGGREGATE, *>): List<AGGREGATE> =
        getByIds(ids.toList(), true)

    /**
     * 根据id获取聚合
     */
    fun <AGGREGATE : Aggregate<ENTITY>, ENTITY> getByIds(
        ids: Iterable<Id<AGGREGATE, *>>,
        persist: Boolean
    ): List<AGGREGATE>

    /**
     * 根据条件获取聚合列表
     */
    fun <AGGREGATE : Aggregate<*>> find(predicate: AggregatePredicate<AGGREGATE, *>): List<AGGREGATE> =
        find(predicate, null as Collection<OrderInfo>?, true)

    /**
     * 根据条件获取聚合列表
     */
    fun <AGGREGATE : Aggregate<*>> find(
        predicate: AggregatePredicate<AGGREGATE, *>,
        persist: Boolean
    ): List<AGGREGATE> =
        find(predicate, null as Collection<OrderInfo>?, persist)

    /**
     * 根据条件获取聚合列表
     */
    fun <AGGREGATE : Aggregate<*>> find(
        predicate: AggregatePredicate<AGGREGATE, *>,
        orders: Collection<OrderInfo>
    ): List<AGGREGATE> =
        find(predicate, orders, true)

    /**
     * 根据条件获取聚合列表
     */
    fun <AGGREGATE : Aggregate<*>> find(
        predicate: AggregatePredicate<AGGREGATE, *>,
        vararg orders: OrderInfo
    ): List<AGGREGATE> =
        find(predicate, orders.toList(), true)

    /**
     * 根据条件获取聚合列表
     */
    fun <AGGREGATE : Aggregate<*>> find(
        predicate: AggregatePredicate<AGGREGATE, *>,
        orders: Collection<OrderInfo>?,
        persist: Boolean
    ): List<AGGREGATE>

    /**
     * 根据条件获取聚合列表
     */
    fun <AGGREGATE : Aggregate<*>> find(
        predicate: AggregatePredicate<AGGREGATE, *>,
        pageParam: PageParam
    ): List<AGGREGATE> =
        find(predicate, pageParam, true)

    /**
     * 根据条件获取聚合列表
     */
    fun <AGGREGATE : Aggregate<*>> find(
        predicate: AggregatePredicate<AGGREGATE, *>,
        pageParam: PageParam,
        persist: Boolean
    ): List<AGGREGATE>

    /**
     * 根据条件获取单个实体
     */
    fun <AGGREGATE : Aggregate<*>> findOne(predicate: AggregatePredicate<AGGREGATE, *>): Optional<AGGREGATE> =
        findOne(predicate, true)

    /**
     * 根据条件获取单个实体
     */
    fun <AGGREGATE : Aggregate<*>> findOne(
        predicate: AggregatePredicate<AGGREGATE, *>,
        persist: Boolean
    ): Optional<AGGREGATE>

    /**
     * 根据条件获取实体
     */
    fun <AGGREGATE : Aggregate<*>> findFirst(
        predicate: AggregatePredicate<AGGREGATE, *>,
        orders: Collection<OrderInfo>,
        persist: Boolean
    ): Optional<AGGREGATE>

    /**
     * 根据条件获取实体
     */
    fun <AGGREGATE : Aggregate<*>> findFirst(
        predicate: AggregatePredicate<AGGREGATE, *>,
        orders: Collection<OrderInfo>
    ): Optional<AGGREGATE> =
        findFirst(predicate, orders, true)

    /**
     * 根据条件获取实体
     */
    fun <AGGREGATE : Aggregate<*>> findFirst(
        predicate: AggregatePredicate<AGGREGATE, *>,
        vararg orders: OrderInfo
    ): Optional<AGGREGATE> =
        findFirst(predicate, orders.toList(), true)

    /**
     * 根据条件获取实体
     */
    fun <AGGREGATE : Aggregate<*>> findFirst(
        predicate: AggregatePredicate<AGGREGATE, *>,
        persist: Boolean
    ): Optional<AGGREGATE> =
        findFirst(predicate, emptyList(), persist)

    /**
     * 根据条件获取实体
     */
    fun <AGGREGATE : Aggregate<*>> findFirst(predicate: AggregatePredicate<AGGREGATE, *>): Optional<AGGREGATE> =
        findFirst(predicate, true)

    /**
     * 根据条件获取实体分页列表
     * 自动调用 UnitOfWork::persist
     */
    fun <AGGREGATE : Aggregate<*>> findPage(
        predicate: AggregatePredicate<AGGREGATE, *>,
        pageParam: PageParam
    ): PageData<AGGREGATE> =
        findPage(predicate, pageParam, true)

    /**
     * 根据条件获取实体分页列表
     */
    fun <AGGREGATE : Aggregate<*>> findPage(
        predicate: AggregatePredicate<AGGREGATE, *>,
        pageParam: PageParam,
        persist: Boolean
    ): PageData<AGGREGATE>

    /**
     * 根据id删除聚合
     */
    fun <AGGREGATE : Aggregate<ENTITY>, ENTITY> removeById(id: Id<AGGREGATE, *>): AGGREGATE? =
        removeByIds(listOf(id)).firstOrNull()

    /**
     * 根据id删除聚合
     */
    fun <AGGREGATE : Aggregate<ENTITY>, ENTITY> removeByIds(vararg ids: Id<AGGREGATE, *>): List<AGGREGATE> =
        removeByIds(ids.toList())

    /**
     * 根据id删除聚合
     */
    fun <AGGREGATE : Aggregate<ENTITY>, ENTITY> removeByIds(ids: Iterable<Id<AGGREGATE, *>>): List<AGGREGATE>

    /**
     * 根据条件删除实体
     */
    fun <AGGREGATE : Aggregate<*>> remove(predicate: AggregatePredicate<AGGREGATE, *>): List<AGGREGATE>

    /**
     * 根据条件删除实体
     */
    fun <AGGREGATE : Aggregate<*>> remove(predicate: AggregatePredicate<AGGREGATE, *>, limit: Int): List<AGGREGATE>

    /**
     * 根据条件获取实体计数
     */
    fun <AGGREGATE : Aggregate<*>> count(predicate: AggregatePredicate<AGGREGATE, *>): Long

    /**
     * 根据条件判断实体是否存在
     */
    fun <AGGREGATE : Aggregate<*>> exists(predicate: AggregatePredicate<AGGREGATE, *>): Boolean
}

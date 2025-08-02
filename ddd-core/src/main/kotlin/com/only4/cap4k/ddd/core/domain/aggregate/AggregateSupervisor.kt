package com.only4.cap4k.ddd.core.domain.aggregate

import com.only4.cap4k.ddd.core.share.OrderInfo
import com.only4.cap4k.ddd.core.share.PageData
import com.only4.cap4k.ddd.core.share.PageParam
import java.util.*

/**
 * 聚合管理器
 *
 * @author LD_moxeii
 * @date 2025/07/27
 */
interface AggregateSupervisor {

    companion object {
        val instance: AggregateSupervisor = AggregateSupervisorSupport.instance
    }

    fun <AGGREGATE : Aggregate<ENTITY>, ENTITY_PAYLOAD : AggregatePayload<ENTITY>, ENTITY: Any> create(
        clazz: Class<AGGREGATE>,
        payload: ENTITY_PAYLOAD
    ): AGGREGATE

    /**
     * 根据id获取聚合
     */
    fun <AGGREGATE : Aggregate<ENTITY>, ENTITY: Any> getById(id: Id<AGGREGATE, out Any>): AGGREGATE? =
        getByIds(listOf(id), true).firstOrNull()

    /**
     * 根据id获取聚合
     */
    fun <AGGREGATE : Aggregate<ENTITY>, ENTITY: Any> getById(id: Id<AGGREGATE, out Any>, persist: Boolean): AGGREGATE? =
        getByIds(listOf(id), persist).firstOrNull()

    /**
     * 根据id获取聚合
     */
    fun <AGGREGATE : Aggregate<ENTITY>, ENTITY: Any> getByIds(vararg ids: Id<AGGREGATE, out Any>): List<AGGREGATE> =
        getByIds(ids.toList(), true)

    /**
     * 根据id获取聚合
     */
    fun <AGGREGATE : Aggregate<ENTITY>, ENTITY: Any> getByIds(
        ids: Iterable<Id<AGGREGATE, out Any>>,
        persist: Boolean = true
    ): List<AGGREGATE>

    /**
     * 根据条件获取聚合列表
     */
    fun <AGGREGATE : Aggregate<ENTITY>, ENTITY : Any> find(predicate: AggregatePredicate<AGGREGATE, ENTITY>): List<AGGREGATE> =
        find(predicate, null as Collection<OrderInfo>?, true)

    /**
     * 根据条件获取聚合列表
     */
    fun <AGGREGATE : Aggregate<ENTITY>, ENTITY : Any> find(
        predicate: AggregatePredicate<AGGREGATE, ENTITY>,
        persist: Boolean
    ): List<AGGREGATE> =
        find(predicate, null as Collection<OrderInfo>?, persist)

    /**
     * 根据条件获取聚合列表
     */
    fun <AGGREGATE : Aggregate<ENTITY>, ENTITY : Any> find(
        predicate: AggregatePredicate<AGGREGATE, ENTITY>,
        orders: Collection<OrderInfo>
    ): List<AGGREGATE> =
        find(predicate, orders, true)

    /**
     * 根据条件获取聚合列表
     */
    fun <AGGREGATE : Aggregate<ENTITY>, ENTITY : Any> find(
        predicate: AggregatePredicate<AGGREGATE, ENTITY>,
        vararg orders: OrderInfo
    ): List<AGGREGATE> =
        find(predicate, orders.toList(), true)

    /**
     * 根据条件获取聚合列表
     */
    fun <AGGREGATE : Aggregate<out Any>> find(
        predicate: AggregatePredicate<AGGREGATE, out Any>,
        orders: Collection<OrderInfo>?,
        persist: Boolean
    ): List<AGGREGATE>

    /**
     * 根据条件获取聚合列表
     */
    fun <AGGREGATE : Aggregate<ENTITY>, ENTITY : Any> find(
        predicate: AggregatePredicate<AGGREGATE, ENTITY>,
        pageParam: PageParam
    ): List<AGGREGATE> =
        find(predicate, pageParam, true)

    /**
     * 根据条件获取聚合列表
     */
    fun <AGGREGATE : Aggregate<out Any>> find(
        predicate: AggregatePredicate<AGGREGATE, out Any>,
        pageParam: PageParam,
        persist: Boolean
    ): List<AGGREGATE>

    /**
     * 根据条件获取单个实体
     */
    fun <AGGREGATE : Aggregate<ENTITY>, ENTITY : Any> findOne(predicate: AggregatePredicate<AGGREGATE, ENTITY>): Optional<AGGREGATE> =
        findOne(predicate, true)

    /**
     * 根据条件获取单个实体
     */
    fun <AGGREGATE : Aggregate<out Any>> findOne(
        predicate: AggregatePredicate<AGGREGATE, out Any>,
        persist: Boolean
    ): Optional<AGGREGATE>

    /**
     * 根据条件获取实体
     */
    fun <AGGREGATE : Aggregate<out Any>> findFirst(
        predicate: AggregatePredicate<AGGREGATE, out Any>,
        orders: Collection<OrderInfo>,
        persist: Boolean
    ): Optional<AGGREGATE>

    /**
     * 根据条件获取实体
     */
    fun <AGGREGATE : Aggregate<ENTITY>, ENTITY : Any> findFirst(
        predicate: AggregatePredicate<AGGREGATE, ENTITY>,
        orders: Collection<OrderInfo>
    ): Optional<AGGREGATE> =
        findFirst(predicate, orders, true)

    /**
     * 根据条件获取实体
     */
    fun <AGGREGATE : Aggregate<ENTITY>, ENTITY : Any> findFirst(
        predicate: AggregatePredicate<AGGREGATE, ENTITY>,
        vararg orders: OrderInfo
    ): Optional<AGGREGATE> =
        findFirst(predicate, orders.toList(), true)

    /**
     * 根据条件获取实体
     */
    fun <AGGREGATE : Aggregate<ENTITY>, ENTITY : Any> findFirst(
        predicate: AggregatePredicate<AGGREGATE, ENTITY>,
        persist: Boolean
    ): Optional<AGGREGATE> =
        findFirst(predicate, emptyList(), persist)

    /**
     * 根据条件获取实体
     */
    fun <AGGREGATE : Aggregate<ENTITY>, ENTITY : Any> findFirst(predicate: AggregatePredicate<AGGREGATE, ENTITY>): Optional<AGGREGATE> =
        findFirst(predicate, true)

    /**
     * 根据条件获取实体分页列表
     * 自动调用 UnitOfWork::persist
     */
    fun <AGGREGATE : Aggregate<ENTITY>, ENTITY : Any> findPage(
        predicate: AggregatePredicate<AGGREGATE, ENTITY>,
        pageParam: PageParam
    ): PageData<AGGREGATE> =
        findPage(predicate, pageParam, true)

    /**
     * 根据条件获取实体分页列表
     */
    fun <AGGREGATE : Aggregate<out Any>> findPage(
        predicate: AggregatePredicate<AGGREGATE, out Any>,
        pageParam: PageParam,
        persist: Boolean
    ): PageData<AGGREGATE>

    /**
     * 根据id删除聚合
     */
    fun <AGGREGATE : Aggregate<ENTITY>, ENTITY: Any> removeById(id: Id<AGGREGATE, out Any>): AGGREGATE? =
        removeByIds(listOf(id)).firstOrNull()

    /**
     * 根据id删除聚合
     */
    fun <AGGREGATE : Aggregate<ENTITY>, ENTITY: Any> removeByIds(vararg ids: Id<AGGREGATE, out Any>): List<AGGREGATE> =
        removeByIds(ids.toList())

    /**
     * 根据id删除聚合
     */
    fun <AGGREGATE : Aggregate<ENTITY>, ENTITY: Any> removeByIds(ids: Iterable<Id<AGGREGATE, out Any>>): List<AGGREGATE>

    /**
     * 根据条件删除实体
     */
    fun <AGGREGATE : Aggregate<out Any>> remove(predicate: AggregatePredicate<AGGREGATE, out Any>): List<AGGREGATE>

    /**
     * 根据条件删除实体
     */
    fun <AGGREGATE : Aggregate<out Any>> remove(
        predicate: AggregatePredicate<AGGREGATE, out Any>,
        limit: Int
    ): List<AGGREGATE>

    /**
     * 根据条件获取实体计数
     */
    fun <AGGREGATE : Aggregate<out Any>> count(predicate: AggregatePredicate<AGGREGATE, out Any>): Long

    /**
     * 根据条件判断实体是否存在
     */
    fun <AGGREGATE : Aggregate<out Any>> exists(predicate: AggregatePredicate<AGGREGATE, out Any>): Boolean
}

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

    fun <ENTITY : Any, AGGREGATE : Aggregate<ENTITY>, ENTITY_PAYLOAD : AggregatePayload<ENTITY>> create(
        clazz: Class<AGGREGATE>,
        payload: ENTITY_PAYLOAD
    ): AGGREGATE

    /**
     * 根据id获取聚合
     *
     * @param id
     * @param persist
     * @param <AGGREGATE>
     * @param <ENTITY>
     * @return
    </ENTITY></AGGREGATE> */
    fun <ENTITY : Any, AGGREGATE : Aggregate<ENTITY>> getById(
        id: Id<AGGREGATE, *>,
        persist: Boolean = true
    ): Optional<AGGREGATE> {
        return Optional.ofNullable(
            getByIds(listOf(id), persist).firstOrNull()
        )
    }

    /**
     * 根据id获取聚合
     *
     * @param ids
     * @param <AGGREGATE>
     * @param <ENTITY>
     * @return
    </ENTITY></AGGREGATE> */
    fun <ENTITY : Any, AGGREGATE : Aggregate<ENTITY>> getByIds(vararg ids: Id<AGGREGATE, *>): List<AGGREGATE> {
        return getByIds(listOf(*ids))
    }

    /**
     * 根据id获取聚合
     *
     * @param ids
     * @param <AGGREGATE>
     * @param <ENTITY>
     * @return
    </ENTITY></AGGREGATE> */
    fun <ENTITY : Any, AGGREGATE : Aggregate<ENTITY>> getByIds(
        ids: Iterable<Id<AGGREGATE, *>>,
        persist: Boolean = true
    ): List<AGGREGATE>

    /**
     * 根据条件获取聚合列表
     *
     * @param predicate
     * @param orders
     * @param <AGGREGATE>
     * @return
     */
    fun <ENTITY : Any, AGGREGATE : Aggregate<ENTITY>> find(
        predicate: AggregatePredicate<ENTITY, AGGREGATE>,
        vararg orders: OrderInfo,
        persist: Boolean = true
    ): List<AGGREGATE> {
        return find(predicate, listOf(*orders), persist)
    }

    /**
     * 根据条件获取聚合列表
     *
     * @param predicate
     * @param orders
     * @param <AGGREGATE>
     * @return
    </AGGREGATE> */
    fun <ENTITY : Any, AGGREGATE : Aggregate<ENTITY>> find(
        predicate: AggregatePredicate<ENTITY, AGGREGATE>,
        orders: Collection<OrderInfo> = emptyList(),
        persist: Boolean = true
    ): List<AGGREGATE>

    /**
     * 根据条件获取聚合列表
     *
     * @param predicate
     * @param pageParam
     * @param persist
     * @return
     */
    fun <ENTITY : Any, AGGREGATE : Aggregate<ENTITY>> find(
        predicate: AggregatePredicate<ENTITY, AGGREGATE>,
        pageParam: PageParam,
        persist: Boolean = true
    ): List<AGGREGATE>

    /**
     * 根据条件获取单个实体
     *
     * @param predicate
     * @param persist
     * @param <AGGREGATE>
     * @return
    </AGGREGATE> */
    fun <ENTITY : Any, AGGREGATE : Aggregate<ENTITY>> findOne(
        predicate: AggregatePredicate<ENTITY, AGGREGATE>,
        persist: Boolean = true
    ): Optional<AGGREGATE>

    /**
     * 根据条件获取实体
     *
     * @param predicate
     * @param orders
     * @param persist
     * @param <AGGREGATE>
     * @return
    </AGGREGATE> */
    fun <ENTITY : Any, AGGREGATE : Aggregate<ENTITY>> findFirst(
        predicate: AggregatePredicate<ENTITY, AGGREGATE>,
        vararg orders: OrderInfo,
        persist: Boolean = true
    ): Optional<AGGREGATE> {
        return findFirst(predicate, listOf(*orders), persist)
    }

    /**
     * 根据条件获取实体
     *
     * @param predicate
     * @param orders
     * @param persist
     * @param <AGGREGATE>
     * @return
    </AGGREGATE> */
    fun <ENTITY : Any, AGGREGATE : Aggregate<ENTITY>> findFirst(
        predicate: AggregatePredicate<ENTITY, AGGREGATE>,
        orders: Collection<OrderInfo> = emptyList(),
        persist: Boolean = true
    ): Optional<AGGREGATE>

    /**
     * 根据条件获取实体分页列表
     *
     * @param predicate
     * @param pageParam
     * @param persist
     * @param <AGGREGATE>
     * @return
    </AGGREGATE> */
    fun <ENTITY : Any, AGGREGATE : Aggregate<ENTITY>> findPage(
        predicate: AggregatePredicate<ENTITY, AGGREGATE>,
        pageParam: PageParam,
        persist: Boolean = true
    ): PageData<AGGREGATE>

    /**
     * 根据id删除聚合
     *
     * @param id
     * @param <AGGREGATE>
     * @param <ENTITY>
     * @return
    </ENTITY></AGGREGATE> */
    fun <ENTITY : Any, AGGREGATE : Aggregate<ENTITY>> removeById(id: Id<AGGREGATE, *>): Optional<AGGREGATE> {
        return Optional.ofNullable(removeByIds(listOf(id)).firstOrNull())
    }

    /**
     * 根据id删除聚合
     *
     * @param ids
     * @param <AGGREGATE>
     * @param <ENTITY>
     * @return
    </ENTITY></AGGREGATE> */
    fun <ENTITY : Any, AGGREGATE : Aggregate<ENTITY>> removeByIds(vararg ids: Id<AGGREGATE, *>): List<AGGREGATE> {
        return removeByIds(listOf(*ids))
    }

    /**
     * 根据id删除聚合
     *
     * @param ids
     * @param <AGGREGATE>
     * @param <ENTITY>
     * @return
    </ENTITY></AGGREGATE> */
    fun <ENTITY : Any, AGGREGATE : Aggregate<ENTITY>> removeByIds(ids: Iterable<Id<AGGREGATE, *>>): List<AGGREGATE>

    /**
     * 根据条件删除实体
     *
     * @param predicate
     * @param limit
     * @param <AGGREGATE>
     * @return
    </AGGREGATE> */
    fun <ENTITY : Any, AGGREGATE : Aggregate<ENTITY>> remove(
        predicate: AggregatePredicate<ENTITY, AGGREGATE>,
        limit: Int = 1
    ): List<AGGREGATE>

    /**
     * 根据条件获取实体计数
     *
     * @param predicate
     * @param <AGGREGATE>
     * @return
    </AGGREGATE> */
    fun <ENTITY : Any, AGGREGATE : Aggregate<ENTITY>> count(predicate: AggregatePredicate<ENTITY, AGGREGATE>): Long

    /**
     * 根据条件判断实体是否存在
     *
     * @param predicate
     * @param <AGGREGATE>
     * @return
    </AGGREGATE> */
    fun <ENTITY : Any, AGGREGATE : Aggregate<ENTITY>> exists(predicate: AggregatePredicate<ENTITY, AGGREGATE>): Boolean

    companion object {
        val instance: AggregateSupervisor
            get() = AggregateSupervisorSupport.instance
    }

}

package com.only4.cap4k.ddd.core.domain.repo

import com.only4.cap4k.ddd.core.share.OrderInfo
import com.only4.cap4k.ddd.core.share.PageData
import com.only4.cap4k.ddd.core.share.PageParam
import java.util.*


/**
 * 聚合仓储
 *
 * @author LD_moxeii
 * @date 2025/07/20
 */
interface Repository<ENTITY : Any> {
    /**
     * 支持条件类型
     *
     * @return
     */
    fun supportPredicateClass(): Class<*>

    /**
     * 根据条件获取实体列表
     *
     * @param predicate
     * @param orders
     * @param persist
     * @return
     */
    fun find(
        predicate: Predicate<ENTITY>,
        orders: Collection<OrderInfo> = emptyList(),
        persist: Boolean = true
    ): List<ENTITY>

    /**
     * 根据条件获取实体列表
     *
     * @param predicate
     * @param orders
     * @return
     */
    fun find(
        predicate: Predicate<ENTITY>,
        vararg orders: OrderInfo
    ): List<ENTITY> {
        return find(predicate, orders.toList())
    }

    /**
     * 根据条件获取实体列表
     *
     * @param predicate
     * @param pageParam
     * @param persist
     * @return
     */
    fun find(
        predicate: Predicate<ENTITY>,
        pageParam: PageParam,
        persist: Boolean = true
    ): List<ENTITY>

    /**
     * 根据条件获取实体
     *
     * @param predicate
     * @param persist
     * @return
     */
    fun findOne(predicate: Predicate<ENTITY>, persist: Boolean = true): Optional<ENTITY>

    /**
     * 根据条件获取实体
     *
     * @param predicate
     * @param orders
     * @param persist
     * @return
     */
    fun findFirst(
        predicate: Predicate<ENTITY>,
        orders: Collection<OrderInfo> = emptyList(),
        persist: Boolean = true
    ): Optional<ENTITY>

    /**
     * 根据条件获取实体列表
     *
     * @param predicate
     * @param orders
     * @return
     */
    fun findFirst(
        predicate: Predicate<ENTITY>,
        vararg orders: OrderInfo
    ): Optional<ENTITY> {
        return findFirst(predicate, orders.toList())
    }

    /**
     * 根据条件获取实体分页列表
     *
     * @param predicate
     * @param pageParam
     * @param persist
     * @return
     */
    fun findPage(
        predicate: Predicate<ENTITY>,
        pageParam: PageParam,
        persist: Boolean = true
    ): PageData<ENTITY>

    /**
     * 根据条件获取实体计数
     *
     * @param predicate
     * @return
     */
    fun count(predicate: Predicate<ENTITY>): Long

    /**
     * 根据条件判断实体是否存在
     *
     * @param predicate
     * @return
     */
    fun exists(predicate: Predicate<ENTITY>): Boolean

    //**
    //     * 通过ID判断实体是否存在
    //     * @param id
    //     * @return
    //     */
    //    boolean existsById(Object id);
}

package com.only4.cap4k.ddd.core.domain.repo

import com.only4.cap4k.ddd.core.share.OrderInfo
import com.only4.cap4k.ddd.core.share.PageData
import com.only4.cap4k.ddd.core.share.PageParam
import java.util.*

/**
 * 仓储管理器
 *
 * @author binking338
 * @date 2024/8/25
 */
interface RepositorySupervisor {

    companion object {
        val instance: RepositorySupervisor = RepositorySupervisorSupport.instance
    }

    /**
     * 根据条件获取实体列表
     */
    fun <ENTITY> find(predicate: Predicate<ENTITY>): List<ENTITY> =
        find(predicate, null as Collection<OrderInfo>?, true)

    /**
     * 根据条件获取实体列表
     */
    fun <ENTITY> find(predicate: Predicate<ENTITY>, persist: Boolean): List<ENTITY> =
        find(predicate, null as Collection<OrderInfo>?, persist)

    /**
     * 根据条件获取实体列表
     */
    fun <ENTITY> find(predicate: Predicate<ENTITY>, orders: Collection<OrderInfo>): List<ENTITY> =
        find(predicate, orders, true)

    /**
     * 根据条件获取实体列表
     */
    fun <ENTITY> find(predicate: Predicate<ENTITY>, vararg orders: OrderInfo): List<ENTITY> =
        find(predicate, orders.toList(), true)

    /**
     * 根据条件获取实体列表
     */
    fun <ENTITY> find(
        predicate: Predicate<ENTITY>,
        orders: Collection<OrderInfo>?,
        persist: Boolean
    ): List<ENTITY>

    /**
     * 根据条件获取实体列表
     */
    fun <ENTITY> find(predicate: Predicate<ENTITY>, pageParam: PageParam): List<ENTITY> =
        find(predicate, pageParam, true)

    /**
     * 根据条件获取实体列表
     */
    fun <ENTITY> find(
        predicate: Predicate<ENTITY>,
        pageParam: PageParam,
        persist: Boolean
    ): List<ENTITY>

    /**
     * 根据条件获取单个实体
     */
    fun <ENTITY> findOne(predicate: Predicate<ENTITY>): Optional<ENTITY> =
        findOne(predicate, true)

    /**
     * 根据条件获取单个实体
     */
    fun <ENTITY> findOne(predicate: Predicate<ENTITY>, persist: Boolean): Optional<ENTITY>

    /**
     * 根据条件获取实体
     */
    fun <ENTITY> findFirst(
        predicate: Predicate<ENTITY>,
        orders: Collection<OrderInfo>,
        persist: Boolean
    ): Optional<ENTITY>

    /**
     * 根据条件获取实体
     */
    fun <ENTITY> findFirst(
        predicate: Predicate<ENTITY>,
        orders: Collection<OrderInfo>
    ): Optional<ENTITY> =
        findFirst(predicate, orders, true)

    /**
     * 根据条件获取实体
     */
    fun <ENTITY> findFirst(predicate: Predicate<ENTITY>, vararg orders: OrderInfo): Optional<ENTITY> =
        findFirst(predicate, orders.toList(), true)

    /**
     * 根据条件获取实体
     */
    fun <ENTITY> findFirst(predicate: Predicate<ENTITY>, persist: Boolean): Optional<ENTITY> =
        findFirst(predicate, emptyList(), persist)

    /**
     * 根据条件获取实体
     */
    fun <ENTITY> findFirst(predicate: Predicate<ENTITY>): Optional<ENTITY> =
        findFirst(predicate, true)

    /**
     * 根据条件获取实体分页列表
     * 自动调用 UnitOfWork::persist
     */
    fun <ENTITY> findPage(predicate: Predicate<ENTITY>, pageParam: PageParam): PageData<ENTITY> =
        findPage(predicate, pageParam, true)

    /**
     * 根据条件获取实体分页列表
     */
    fun <ENTITY> findPage(
        predicate: Predicate<ENTITY>,
        pageParam: PageParam,
        persist: Boolean
    ): PageData<ENTITY>

    /**
     * 根据条件删除实体
     */
    fun <ENTITY> remove(predicate: Predicate<ENTITY>): List<ENTITY>

    /**
     * 根据条件删除实体
     */
    fun <ENTITY> remove(predicate: Predicate<ENTITY>, limit: Int): List<ENTITY>

    /**
     * 根据条件获取实体计数
     */
    fun <ENTITY> count(predicate: Predicate<ENTITY>): Long

    /**
     * 根据条件判断实体是否存在
     */
    fun <ENTITY> exists(predicate: Predicate<ENTITY>): Boolean
}

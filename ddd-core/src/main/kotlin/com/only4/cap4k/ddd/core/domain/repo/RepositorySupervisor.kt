package com.only4.cap4k.ddd.core.domain.repo

import com.only4.cap4k.ddd.core.share.OrderInfo
import com.only4.cap4k.ddd.core.share.PageData
import com.only4.cap4k.ddd.core.share.PageParam
import java.util.*

/**
 * 仓储管理器
 *
 * @author LD_moxeii
 * @date 2025/07/27
 */
interface RepositorySupervisor {

    companion object {
        val instance: RepositorySupervisor = RepositorySupervisorSupport.instance
    }

    /**
     * 根据条件获取实体列表
     */
    fun <ENTITY: Any> find(predicate: Predicate<ENTITY>): List<ENTITY> =
        find(predicate, null as Collection<OrderInfo>?, true)

    /**
     * 根据条件获取实体列表
     */
    fun <ENTITY: Any> find(predicate: Predicate<ENTITY>, persist: Boolean): List<ENTITY> =
        find(predicate, null as Collection<OrderInfo>?, persist)

    /**
     * 根据条件获取实体列表
     */
    fun <ENTITY: Any> find(predicate: Predicate<ENTITY>, orders: Collection<OrderInfo>): List<ENTITY> =
        find(predicate, orders, true)

    /**
     * 根据条件获取实体列表
     */
    fun <ENTITY: Any> find(predicate: Predicate<ENTITY>, vararg orders: OrderInfo): List<ENTITY> =
        find(predicate, orders.toList(), true)

    /**
     * 根据条件获取实体列表
     */
    fun <ENTITY: Any> find(
        predicate: Predicate<ENTITY>,
        orders: Collection<OrderInfo>?,
        persist: Boolean
    ): List<ENTITY>

    /**
     * 根据条件获取实体列表
     */
    fun <ENTITY: Any> find(
        predicate: Predicate<ENTITY>,
        pageParam: PageParam,
        persist: Boolean = true
    ): List<ENTITY>

    /**
     * 根据条件获取单个实体
     */
    fun <ENTITY: Any> findOne(predicate: Predicate<ENTITY>): Optional<ENTITY> =
        findOne(predicate, true)

    /**
     * 根据条件获取单个实体
     */
    fun <ENTITY: Any> findOne(predicate: Predicate<ENTITY>, persist: Boolean): Optional<ENTITY>

    /**
     * 根据条件获取实体
     */
    fun <ENTITY: Any> findFirst(
        predicate: Predicate<ENTITY>,
        orders: Collection<OrderInfo>,
        persist: Boolean
    ): Optional<ENTITY>

    /**
     * 根据条件获取实体
     */
    fun <ENTITY: Any> findFirst(
        predicate: Predicate<ENTITY>,
        orders: Collection<OrderInfo>
    ): Optional<ENTITY> =
        findFirst(predicate, orders, true)

    /**
     * 根据条件获取实体
     */
    fun <ENTITY: Any> findFirst(predicate: Predicate<ENTITY>, vararg orders: OrderInfo): Optional<ENTITY> =
        findFirst(predicate, orders.toList(), true)

    /**
     * 根据条件获取实体
     */
    fun <ENTITY: Any> findFirst(predicate: Predicate<ENTITY>, persist: Boolean): Optional<ENTITY> =
        findFirst(predicate, emptyList(), persist)

    /**
     * 根据条件获取实体
     */
    fun <ENTITY: Any> findFirst(predicate: Predicate<ENTITY>): Optional<ENTITY> =
        findFirst(predicate, true)

    /**
     * 根据条件获取实体分页列表
     * 自动调用 UnitOfWork::persist
     */
    fun <ENTITY: Any> findPage(predicate: Predicate<ENTITY>, pageParam: PageParam): PageData<ENTITY> =
        findPage(predicate, pageParam, true)

    /**
     * 根据条件获取实体分页列表
     */
    fun <ENTITY: Any> findPage(
        predicate: Predicate<ENTITY>,
        pageParam: PageParam,
        persist: Boolean
    ): PageData<ENTITY>

    /**
     * 根据条件删除实体
     */
    fun <ENTITY: Any> remove(predicate: Predicate<ENTITY>): List<ENTITY>

    /**
     * 根据条件删除实体
     */
    fun <ENTITY: Any> remove(predicate: Predicate<ENTITY>, limit: Int): List<ENTITY>

    /**
     * 根据条件获取实体计数
     */
    fun <ENTITY: Any> count(predicate: Predicate<ENTITY>): Long

    /**
     * 根据条件判断实体是否存在
     */
    fun <ENTITY: Any> exists(predicate: Predicate<ENTITY>): Boolean
}

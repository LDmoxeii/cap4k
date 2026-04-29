package com.only4.cap4k.ddd.core.domain.repo

import com.only4.cap4k.ddd.core.share.OrderInfo
import com.only4.cap4k.ddd.core.share.PageData
import com.only4.cap4k.ddd.core.share.PageParam

/**
 * 仓储管理器
 *
 * @author LD_moxeii
 * @date 2025/07/27
 */
interface RepositorySupervisor {

    companion object {
        @JvmStatic
        val instance: RepositorySupervisor by lazy { RepositorySupervisorSupport.instance }
    }

    /**
     * 根据条件获取实体列表
     */
    fun <ENTITY: Any> find(predicate: Predicate<ENTITY>, vararg orders: OrderInfo): List<ENTITY> =
        find(predicate, orders.toList())

    /**
     * 根据条件获取实体列表
     */
    fun <ENTITY: Any> find(
        predicate: Predicate<ENTITY>,
        orders: Collection<OrderInfo> = emptyList(),
        persist: Boolean = true
    ): List<ENTITY>

    fun <ENTITY: Any> find(
        predicate: Predicate<ENTITY>,
        orders: Collection<OrderInfo> = emptyList(),
        persist: Boolean = true,
        loadPlan: AggregateLoadPlan
    ): List<ENTITY> {
        rejectUnsupportedCompatibilityLoadPlan(loadPlan)
        return find(predicate, orders, persist)
    }

    /**
     * 根据条件获取实体列表
     */
    fun <ENTITY: Any> find(
        predicate: Predicate<ENTITY>,
        pageParam: PageParam,
        persist: Boolean = true
    ): List<ENTITY>

    fun <ENTITY: Any> find(
        predicate: Predicate<ENTITY>,
        pageParam: PageParam,
        persist: Boolean = true,
        loadPlan: AggregateLoadPlan
    ): List<ENTITY> {
        rejectUnsupportedCompatibilityLoadPlan(loadPlan)
        return find(predicate, pageParam, persist)
    }

    /**
     * 根据条件获取单个实体
     */
    fun <ENTITY : Any> findOne(
        predicate: Predicate<ENTITY>,
        persist: Boolean = true
    ): ENTITY?

    fun <ENTITY : Any> findOne(
        predicate: Predicate<ENTITY>,
        persist: Boolean = true,
        loadPlan: AggregateLoadPlan
    ): ENTITY? {
        rejectUnsupportedCompatibilityLoadPlan(loadPlan)
        return findOne(predicate, persist)
    }

    /**
     * 根据条件获取实体
     */
    fun <ENTITY: Any> findFirst(
        predicate: Predicate<ENTITY>,
        orders: Collection<OrderInfo> = emptyList(),
        persist: Boolean = true
    ): ENTITY?

    fun <ENTITY: Any> findFirst(
        predicate: Predicate<ENTITY>,
        orders: Collection<OrderInfo> = emptyList(),
        persist: Boolean = true,
        loadPlan: AggregateLoadPlan
    ): ENTITY? {
        rejectUnsupportedCompatibilityLoadPlan(loadPlan)
        return findFirst(predicate, orders, persist)
    }

    /**
     * 根据条件获取实体
     */
    fun <ENTITY: Any> findFirst(predicate: Predicate<ENTITY>, vararg orders: OrderInfo): ENTITY? =
        findFirst(predicate, orders.toList())

    /**
     * 根据条件获取实体分页列表
     */
    fun <ENTITY: Any> findPage(
        predicate: Predicate<ENTITY>,
        pageParam: PageParam,
        persist: Boolean = true
    ): PageData<ENTITY>

    fun <ENTITY: Any> findPage(
        predicate: Predicate<ENTITY>,
        pageParam: PageParam,
        persist: Boolean = true,
        loadPlan: AggregateLoadPlan
    ): PageData<ENTITY> {
        rejectUnsupportedCompatibilityLoadPlan(loadPlan)
        return findPage(predicate, pageParam, persist)
    }

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

    private fun rejectUnsupportedCompatibilityLoadPlan(loadPlan: AggregateLoadPlan) {
        if (loadPlan == AggregateLoadPlan.WHOLE_AGGREGATE) {
            throw UnsupportedOperationException(
                "AggregateLoadPlan.WHOLE_AGGREGATE requires repository-supervisor-specific support"
            )
        }
    }
}

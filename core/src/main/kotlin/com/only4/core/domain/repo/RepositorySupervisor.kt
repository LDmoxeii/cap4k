package com.only4.core.domain.repo

import com.only4.core.share.OrderInfo
import com.only4.core.share.PageData
import com.only4.core.share.PageParam
import java.util.*

/**
 * 仓储管理器
 *
 * @author binking338
 * @date 2024/8/25
 */
interface RepositorySupervisor {

    /**
     * 根据条件获取实体列表
     *
     * @param predicate
     * @param orders
     * @param <ENTITY>
     * @return
    </ENTITY> */
    fun <ENTITY> find(
        predicate: Predicate<ENTITY>,
        vararg orders: OrderInfo,
        persist: Boolean = true,
    ): List<ENTITY> {
        return find(predicate, listOf(*orders), persist)
    }

    /**
     * 根据条件获取实体列表
     *
     * @param predicate
     * @param orders
     * @param <ENTITY>
     * @return
    </ENTITY> */
    fun <ENTITY> find(
        predicate: Predicate<ENTITY>,
        orders: Collection<OrderInfo> = emptyList(),
        persist: Boolean = true,
    ): List<ENTITY>


    /**
     * 根据条件获取实体列表
     *
     * @param predicate
     * @param pageParam
     * @param persist
     * @return
     */
    fun <ENTITY> find(
        predicate: Predicate<ENTITY>,
        pageParam: PageParam,
        persist: Boolean = true,
    ): List<ENTITY>

    /**
     * 根据条件获取单个实体
     *
     * @param predicate
     * @param persist
     * @param <ENTITY>
     * @return
    </ENTITY> */
    fun <ENTITY> findOne(
        predicate: Predicate<ENTITY>,
        persist: Boolean = true
    ): Optional<ENTITY>

    /**
     * 根据条件获取实体
     *
     * @param predicate
     * @param orders
     * @param persist
     * @param <ENTITY>
     * @return
    </ENTITY> */
    fun <ENTITY> findFirst(
        predicate: Predicate<ENTITY>,
        orders: Collection<OrderInfo> = listOf(),
        persist: Boolean = true,
    ): Optional<ENTITY>

    /**
     * 根据条件获取实体
     *
     * @param predicate
     * @param orders
     * @param persist
     * @param <ENTITY>
     * @return
    </ENTITY> */
    fun <ENTITY> findFirst(
        predicate: Predicate<ENTITY>,
        vararg orders: OrderInfo,
        persist: Boolean = true,
    ): Optional<ENTITY> {
        return findFirst(predicate, listOf(*orders), persist)
    }

    /**
     * 根据条件获取实体分页列表
     * 自动调用 UnitOfWork::persist
     *
     * @param predicate
     * @param pageParam
     * @param persist
     * @param <ENTITY>
     * @return
    </ENTITY> */
    fun <ENTITY> findPage(
        predicate: Predicate<ENTITY>,
        pageParam: PageParam,
        persist: Boolean = true
    ): PageData<ENTITY>

    /**
     * 根据条件删除实体
     *
     * @param predicate
     * @param limit
     * @param <ENTITY>
     * @return
    </ENTITY> */
    fun <ENTITY> remove(predicate: Predicate<ENTITY>, limit: Int = 1): List<ENTITY>

    /**
     * 根据条件获取实体计数
     *
     * @param predicate
     * @param <ENTITY>
     * @return
    </ENTITY> */
    fun <ENTITY> count(predicate: Predicate<ENTITY>): Long

    /**
     * 根据条件判断实体是否存在
     *
     * @param predicate
     * @param <ENTITY>
     * @return
    </ENTITY> */
    fun <ENTITY> exists(predicate: Predicate<ENTITY>): Boolean

    companion object {
        val instance: RepositorySupervisor
            get() = RepositorySupervisorSupport.instance
    }
}

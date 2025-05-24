package com.only4.core.domain.repo

/**
 * 聚合管理器帮助类
 *
 * @author binking338
 * @date 2025/1/12
 */
object AggregateSupervisorSupport {
    lateinit var instance: AggregateSupervisor
    fun configure(aggregateSupervisor: AggregateSupervisor) {
        instance = aggregateSupervisor
    }
}

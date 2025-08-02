package com.only4.cap4k.ddd.core.domain.aggregate

/**
 * 聚合管理器帮助类
 *
 * @author LD_moxeii
 * @date 2025/07/20
 */
object AggregateSupervisorSupport {
    lateinit var instance: AggregateSupervisor
    fun configure(aggregateSupervisor: AggregateSupervisor) {
        instance = aggregateSupervisor
    }
}

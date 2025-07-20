package com.only4.cap4k.ddd.core.domain.aggregate

/**
 * 聚合工厂管理器配置
 *
 * @author LD_moxeii
 * @date 2025/07/20
 */
object AggregateFactorySupervisorSupport {
    lateinit var instance: AggregateFactorySupervisor

    fun configure(aggregateFactorySupervisor: AggregateFactorySupervisor) {
        instance = aggregateFactorySupervisor
    }
}

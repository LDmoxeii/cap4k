package com.only4.cap4k.ddd.core.domain.aggregate

/**
 * 聚合工厂管理器配置
 *
 * @author binking338
 * @date 2024/9/3
 */
object AggregateFactorySupervisorSupport {
    lateinit var instance: AggregateFactorySupervisor

    fun configure(aggregateFactorySupervisor: AggregateFactorySupervisor) {
        instance = aggregateFactorySupervisor
    }
}

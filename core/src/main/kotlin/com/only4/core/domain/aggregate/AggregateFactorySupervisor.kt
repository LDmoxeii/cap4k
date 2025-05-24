package com.only4.core.domain.aggregate

/**
 * 聚合工厂管理器
 *
 * @author binking338
 * @date 2024/9/3
 */
interface AggregateFactorySupervisor {
    /**
     * 创建新聚合实例
     *
     * @param entityPayload
     * @return
     * @param <ENTITY_PAYLOAD>
     * @param <ENTITY>
    </ENTITY></ENTITY_PAYLOAD> */
    fun <ENTITY_PAYLOAD : AggregatePayload<ENTITY>, ENTITY> create(entityPayload: ENTITY_PAYLOAD): ENTITY

    companion object {
        val instance: AggregateFactorySupervisor
            get() = AggregateFactorySupervisorSupport.instance
    }
}

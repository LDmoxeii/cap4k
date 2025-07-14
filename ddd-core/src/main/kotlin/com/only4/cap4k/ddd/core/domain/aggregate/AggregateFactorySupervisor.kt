package com.only4.cap4k.ddd.core.domain.aggregate

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
     * @param entityPayload 聚合载荷对象
     * @return 创建的聚合实例，如果找不到对应的工厂则返回null
     * @param ENTITY 聚合实体类型
     * @param ENTITY_PAYLOAD 聚合载荷类型
     */
    fun <ENTITY, ENTITY_PAYLOAD : AggregatePayload<ENTITY>> create(entityPayload: ENTITY_PAYLOAD): ENTITY

    companion object {
        /**
         * 获取聚合工厂管理器实例
         *
         * @return 聚合工厂管理器实例
         */
        val instance: AggregateFactorySupervisor
            get() = AggregateFactorySupervisorSupport.instance
    }
}

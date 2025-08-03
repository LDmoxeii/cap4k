package com.only4.cap4k.ddd.core.domain.aggregate

/**
 * 聚合工厂管理器
 *
 * @author LD_moxeii
 * @date 2025/07/20
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
    fun <ENTITY_PAYLOAD : AggregatePayload<out ENTITY>, ENTITY : Any> create(entityPayload: ENTITY_PAYLOAD): ENTITY

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

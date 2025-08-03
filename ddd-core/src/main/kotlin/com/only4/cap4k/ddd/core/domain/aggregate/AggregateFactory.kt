package com.only4.cap4k.ddd.core.domain.aggregate

/**
 * 聚合工厂
 *
 * @author LD_moxeii
 * @date 2025/07/20
 */
interface AggregateFactory<in ENTITY_PAYLOAD : AggregatePayload<out ENTITY>, ENTITY : Any> {
    /**
     * 创建新聚合实例
     *
     * @param entityPayload
     * @return
     */
    fun create(entityPayload: ENTITY_PAYLOAD): ENTITY
}

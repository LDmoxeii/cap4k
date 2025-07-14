package com.only4.cap4k.ddd.core.domain.aggregate

/**
 * 聚合工厂
 *
 * @author binking338
 * @date 2024/9/3
 */
interface AggregateFactory<ENTITY_PAYLOAD : AggregatePayload<ENTITY>, ENTITY> {
    /**
     * 创建新聚合实例
     *
     * @param entityPayload
     * @return
     */
    fun create(entityPayload: ENTITY_PAYLOAD): ENTITY
}

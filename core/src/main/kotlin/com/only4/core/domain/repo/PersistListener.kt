package com.only4.core.domain.repo

/**
 * 实体持久化监听接口
 *
 * @author binking338
 * @date 2024/1/31
 */
interface PersistListener<Entity : Any> {
    /**
     * 持久化变更
     * @param aggregate
     * @param type
     */
    fun onChange(aggregate: Entity, type: PersistType)

    /**
     * 异常
     * @param aggregate
     * @param type
     * @param e
     */
    fun onExcepton(aggregate: Entity, type: PersistType, e: Exception) {
    }
}

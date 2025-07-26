package com.only4.cap4k.ddd.core.domain.repo

/**
 * 实体持久化监听接口
 *
 * @author LD_moxeii
 * @date 2025/07/20
 */
fun interface PersistListener<Entity : Any> {
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
    fun onException(aggregate: Entity, type: PersistType, e: Exception) {
    }
}

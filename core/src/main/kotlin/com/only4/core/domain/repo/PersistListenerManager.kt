package com.only4.core.domain.repo

/**
 * 持久化监听管理器
 *
 * @author binking338
 * @date 2024/1/31
 */
interface PersistListenerManager {
    fun <Entity : Any> onChange(aggregate: Entity, type: PersistType)
}

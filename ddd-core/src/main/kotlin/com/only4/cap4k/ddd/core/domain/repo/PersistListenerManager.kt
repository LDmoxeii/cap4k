package com.only4.cap4k.ddd.core.domain.repo

/**
 * 持久化监听管理器
 *
 * @author LD_moxeii
 * @date 2025/07/20
 */
interface PersistListenerManager {
    fun <Entity : Any> onChange(aggregate: Entity, type: PersistType)
}

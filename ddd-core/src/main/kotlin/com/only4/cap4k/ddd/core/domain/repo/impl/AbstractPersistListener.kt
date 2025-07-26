package com.only4.cap4k.ddd.core.domain.repo.impl

import com.only4.cap4k.ddd.core.domain.repo.PersistListener
import com.only4.cap4k.ddd.core.domain.repo.PersistType

/**
 * 默认实体持久化监听抽象类
 *
 * @author LD_moxeii
 * @date 2025/07/26
 */
abstract class AbstractPersistListener<Entity : Any> : PersistListener<Entity> {

    /**
     * 持久化变更
     *
     * @param aggregate
     * @param type
     */
    override fun onChange(aggregate: Entity, type: PersistType) {
        when (type) {
            PersistType.CREATE -> onCreate(aggregate)
            PersistType.UPDATE -> onUpdate(aggregate)
            PersistType.DELETE -> onDelete(aggregate)
        }
    }

    abstract fun onCreate(entity: Entity)

    abstract fun onUpdate(entity: Entity)

    abstract fun onDelete(entity: Entity)
}

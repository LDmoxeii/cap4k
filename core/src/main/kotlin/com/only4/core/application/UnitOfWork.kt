package com.only4.core.application

import org.springframework.transaction.annotation.Propagation

/**
 * UnitOfWork模式
 *
 * @author binking338
 * @date 2023/8/5
 */
interface UnitOfWork {
    /**
     * 提交新增或更新实体持久化记录意图到UnitOfWork上下文
     *
     * @param entity 实体对象
     */
    fun persist(entity: Any)

    /**
     * 提交新增实体持久化记录意图到UnitOfWork上下文，如果实体已存在则不提交
     * @param entity
     * @return 是否提交
     */
    fun persistIfNotExist(entity: Any): Boolean

    /**
     * 提交移除实体持久化记录意图到UnitOfWork上下文
     *
     * @param entity 实体对象
     */
    fun remove(entity: Any)

    /**
     * 将持久化意图转换成持久化指令，并提交事务
     *
     * @param propagation 事务传播特性
     */
    fun save(propagation: Propagation = Propagation.REQUIRED)

    companion object {
        val instance: UnitOfWork
            get() = UnitOfWorkSupport.instance
    }
}

package com.only4.core.application

import org.springframework.transaction.annotation.Propagation

/**
 * 工作单元接口
 * 实现UnitOfWork模式，用于管理实体的持久化操作
 * 提供事务管理和实体状态追踪功能
 *
 * @author binking338
 * @date 2023/8/5
 */
interface UnitOfWork {
    /**
     * 提交实体持久化意图
     * 将实体的新增或更新操作意图添加到工作单元上下文中
     *
     * @param entity 需要持久化的实体对象
     * @throws IllegalArgumentException 当实体对象无效时
     */
    fun persist(entity: Any)

    /**
     * 条件提交实体持久化意图
     * 仅当实体不存在时才提交新增操作意图
     *
     * @param entity 需要持久化的实体对象
     * @return 是否成功提交持久化意图
     * @throws IllegalArgumentException 当实体对象无效时
     */
    fun persistIfNotExist(entity: Any): Boolean

    /**
     * 提交实体删除意图
     * 将实体的删除操作意图添加到工作单元上下文中
     *
     * @param entity 需要删除的实体对象
     * @throws IllegalArgumentException 当实体对象无效时
     */
    fun remove(entity: Any)

    /**
     * 执行持久化操作
     * 将工作单元上下文中的持久化意图转换为实际的持久化指令并提交事务
     *
     * @param propagation 事务传播特性，默认为REQUIRED
     * @throws IllegalStateException 当工作单元状态不允许执行持久化操作时
     */
    fun save(propagation: Propagation = Propagation.REQUIRED)

    companion object {
        /**
         * 获取工作单元实例
         * 通过UnitOfWorkSupport获取全局唯一的工作单元实例
         *
         * @return 工作单元实例
         */
        val instance: UnitOfWork
            get() = UnitOfWorkSupport.instance
    }
}

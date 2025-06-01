package com.only4.cap4k.ddd.core.application

/**
 * 工作单元配置支持类
 * 用于配置和管理工作单元实例
 * 提供全局访问点，支持在应用启动时进行配置
 *
 * @author binking338
 * @date 2024/8/25
 */
object UnitOfWorkSupport {
    /**
     * 工作单元实例
     * 负责管理实体的持久化操作和事务控制
     */
    lateinit var instance: UnitOfWork

    /**
     * 配置工作单元实例
     * 在应用启动时调用此方法进行配置
     *
     * @param unitOfWork 工作单元实例
     * @throws IllegalStateException 当实例已经被初始化时
     */
    fun configure(unitOfWork: UnitOfWork) {
        instance = unitOfWork
    }
}

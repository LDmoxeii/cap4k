package com.only4.core.application

/**
 * 工作单元配置
 *
 * @author binking338
 * @date 2024/8/25
 */
object UnitOfWorkSupport {
    lateinit var instance: UnitOfWork

    /**
     * 配置工作单元
     *
     * @param unitOfWork 工作单元
     */
    fun configure(unitOfWork: UnitOfWork) {
        instance = unitOfWork
    }
}

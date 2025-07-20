package com.only4.cap4k.ddd.core.application

/**
 * 请求管理器配置支持类
 * 用于配置和管理请求相关的组件实例
 * 提供全局访问点，支持在应用启动时进行配置
 *
 * @author LD_moxeii
 * @date 2025/07/20
 */
object RequestSupervisorSupport {
    /**
     * 请求监督者实例
     * 负责管理和控制请求的执行流程
     */
    lateinit var instance: RequestSupervisor

    /**
     * 请求管理器实例
     * 负责管理请求的执行、重试和归档
     */
    lateinit var requestManager: RequestManager

    /**
     * 配置请求监督者
     * 在应用启动时调用此方法进行配置
     *
     * @param requestSupervisor 请求监督者实例
     * @throws IllegalStateException 当实例已经被初始化时
     */
    fun configure(requestSupervisor: RequestSupervisor) {
        instance = requestSupervisor
    }

    /**
     * 配置请求管理器
     * 在应用启动时调用此方法进行配置
     *
     * @param requestManager 请求管理器实例
     * @throws IllegalStateException 当实例已经被初始化时
     */
    fun configure(requestManager: RequestManager) {
        RequestSupervisorSupport.requestManager = requestManager
    }
}

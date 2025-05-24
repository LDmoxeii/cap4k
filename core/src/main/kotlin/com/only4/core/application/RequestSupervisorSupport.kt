package com.only4.core.application

/**
 * 请求管理器配置
 *
 * @author binking338
 * @date 2024/8/24
 */
object RequestSupervisorSupport {
    lateinit var instance: RequestSupervisor
    lateinit var requestManager: RequestManager

    /**
     * 配置请求管理器
     *
     * @param requestSupervisor [RequestSupervisor]
     */
    fun configure(requestSupervisor: RequestSupervisor) {
        instance = requestSupervisor
    }

    /**
     * 配置请求管理器
     *
     * @param requestManager [RequestManager]
     */
    fun configure(requestManager: RequestManager) {
        RequestSupervisorSupport.requestManager = requestManager
    }
}

package com.only4.cap4k.ddd.core.application

import com.only4.cap4k.ddd.core.CapabilitySlot

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
    private val supervisorSlot = CapabilitySlot<RequestSupervisor>("requests", "cap4k-ddd-core-starter")

    val instance: RequestSupervisor
        get() = supervisorSlot.get()

    private val reliableSlot = CapabilitySlot<ReliableRequestSupervisor>(
        "reliable-requests",
        "cap4k-ddd-request-jpa-starter",
    )

    val reliable: ReliableRequestSupervisor
        get() = reliableSlot.get()

    /**
     * 请求管理器实例
     * 负责管理请求的执行、重试和归档
     */
    private val managerSlot = CapabilitySlot<RequestManager>(
        "request-manager",
        "cap4k-ddd-request-jpa-starter",
    )

    val requestManager: RequestManager
        get() = managerSlot.get()

    /**
     * 配置请求监督者
     * 在应用启动时调用此方法进行配置
     *
     * @param requestSupervisor 请求监督者实例
     * @throws IllegalStateException 当实例已经被初始化时
     */
    fun configure(requestSupervisor: RequestSupervisor) {
        supervisorSlot.configure(requestSupervisor)
    }

    fun configure(reliableRequestSupervisor: ReliableRequestSupervisor) {
        reliableSlot.configure(reliableRequestSupervisor)
    }

    /**
     * 配置请求管理器
     * 在应用启动时调用此方法进行配置
     *
     * @param requestManager 请求管理器实例
     * @throws IllegalStateException 当实例已经被初始化时
     */
    fun configure(requestManager: RequestManager) {
        managerSlot.configure(requestManager)
    }
}

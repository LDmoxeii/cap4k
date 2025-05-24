package com.only4.core.application.event

import java.time.Duration
import java.time.LocalDateTime

/**
 * 集成事件控制器
 *
 * @author binking338
 * @date 2024/8/25
 */
interface IntegrationEventSupervisor {

    /**
     * 附加事件到持久化上下文
     *
     * @param eventPayload 事件消息体
     * @param schedule     指定时间发送
     */
    fun <EVENT> attach(
        eventPayload: EVENT,
        schedule: LocalDateTime = LocalDateTime.now(),
        delay: Duration = Duration.ZERO
    )

    /**
     * 从持久化上下文剥离事件
     *
     * @param eventPayload 事件消息体
     */
    fun <EVENT> detach(eventPayload: EVENT)

    /**
     * 发布指定集成事件
     * @param eventPayload 集成事件负载
     * @param schedule     指定时间发送
     */
    fun <EVENT> publish(
        eventPayload: EVENT,
        schedule: LocalDateTime = LocalDateTime.now(),
        delay: Duration = Duration.ZERO
    )

    companion object {
        val instance: IntegrationEventSupervisor
            get() = IntegrationEventSupervisorSupport.instance

        val manager: IntegrationEventManager
            get() = IntegrationEventSupervisorSupport.manager
    }
}

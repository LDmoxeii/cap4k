package com.only4.cap4k.ddd.core.application.event

import java.time.Duration
import java.time.LocalDateTime

/**
 * 集成事件监督者接口
 * 负责管理和控制集成事件的生命周期，包括事件的附加、解除附加和发布
 *
 * @author binking338
 * @date 2024/8/25
 */
interface IntegrationEventSupervisor {
    /**
     * 附加事件到持久化上下文
     * 将事件添加到当前线程的上下文中，等待事务提交后发布
     *
     * @param eventPayload 事件消息体
     * @param schedule 指定时间发送
     */
    fun <EVENT> attach(
        eventPayload: EVENT,
        schedule: LocalDateTime = LocalDateTime.now(),
    )

    fun <EVENT> attach(
        eventPayload: EVENT,
        delay: Duration
    ) = attach(eventPayload, LocalDateTime.now().plus(delay))


    /**
     * 从持久化上下文解除事件
     * 将事件从当前线程的上下文中移除
     *
     * @param eventPayload 事件消息体
     */
    fun <EVENT> detach(eventPayload: EVENT)

    /**
     * 发布指定集成事件
     * 立即发布事件，不等待事务提交
     *
     * @param eventPayload 集成事件负载
     * @param schedule 指定时间发送
     * @param delay 延迟时间
     */
    fun <EVENT> publish(
        eventPayload: EVENT,
        schedule: LocalDateTime = LocalDateTime.now(),
    )

    fun <EVENT> publish(
        eventPayload: EVENT,
        delay: Duration
    ) = publish(eventPayload, LocalDateTime.now().plus(delay))

    companion object {
        /**
         * 获取集成事件监督者实例
         */
        val instance: IntegrationEventSupervisor
            get() = IntegrationEventSupervisorSupport.instance

        /**
         * 获取集成事件管理器实例
         */
        val manager: IntegrationEventManager
            get() = IntegrationEventSupervisorSupport.manager
    }
}



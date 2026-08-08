package com.only4.cap4k.ddd.core.application.event

import java.time.Duration
import java.time.LocalDateTime

/**
 * 集成事件监督者接口
 * 负责管理和控制集成事件的生命周期，包括事件的附加和解除附加
 *
 * @author LD_moxeii
 * @date 2025/07/20
 */
interface IntegrationEventSupervisor {
    fun <EVENT : Any> enqueue(eventPayload: EVENT) = schedule(eventPayload, LocalDateTime.now())

    fun <EVENT : Any> schedule(eventPayload: EVENT, schedule: LocalDateTime)

    fun <EVENT : Any> delay(eventPayload: EVENT, delay: Duration) =
        schedule(eventPayload, LocalDateTime.now().plus(delay))

    fun <EVENT : Any> enqueue(eventPayloadSupplier: () -> EVENT) =
        schedule(LocalDateTime.now(), eventPayloadSupplier)

    fun <EVENT : Any> schedule(schedule: LocalDateTime, eventPayloadSupplier: () -> EVENT)

    fun <EVENT : Any> delay(delay: Duration, eventPayloadSupplier: () -> EVENT) =
        schedule(LocalDateTime.now().plus(delay), eventPayloadSupplier)

    companion object {
        /**
         * 获取集成事件监督者实例
         */
        @JvmStatic
        val instance: IntegrationEventSupervisor
            get() = IntegrationEventSupervisorSupport.instance

        /**
         * 获取集成事件管理器实例
         */
        @JvmStatic
        val manager: IntegrationEventManager
            get() = IntegrationEventSupervisorSupport.manager
    }
}



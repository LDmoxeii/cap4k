package com.only4.cap4k.ddd.core.domain.event

import java.time.Duration
import java.time.LocalDateTime

/**
 * 领域事件管理器
 *
 * @author LD_moxeii
 * @date 2025/07/20
 */
interface DomainEventSupervisor {

    /**
     * 附加领域事件到持久化上下文
     * @param domainEventPayload 领域事件消息体
     * @param schedule 指定时间发送
     */
    context(entity: ENTITY)
    fun <DOMAIN_EVENT: Any, ENTITY: Any> attach(
        domainEventPayload: DOMAIN_EVENT,
        schedule: LocalDateTime = LocalDateTime.now(),
    )

    context(entity: ENTITY)
    fun <DOMAIN_EVENT: Any, ENTITY: Any> attach(
        domainEventPayload: DOMAIN_EVENT,
        delay: Duration,
    ) = attach(domainEventPayload, LocalDateTime.now().plus(delay))

    context(entity: ENTITY)
    fun <DOMAIN_EVENT : Any, ENTITY : Any> attach(
        schedule: LocalDateTime = LocalDateTime.now(),
        domainEventPayloadSupplier: () -> DOMAIN_EVENT,
    )

    context(entity: ENTITY)
    fun <DOMAIN_EVENT : Any, ENTITY : Any> attach(
        delay: Duration,
        domainEventPayloadSupplier: () -> DOMAIN_EVENT,
    ) = attach(LocalDateTime.now().plus(delay), domainEventPayloadSupplier)

    /**
     * 从持久化上下文剥离领域事件
     * @param domainEventPayload 领域事件消息体
     */
    context(entity: ENTITY)
    fun <DOMAIN_EVENT : Any, ENTITY : Any> detach(
        domainEventPayload: DOMAIN_EVENT,
    )

    companion object {
        @JvmStatic
        val instance: DomainEventSupervisor
            /**
             * 获取领域事件管理器
             * @return 领域事件管理器
             */
            get() = DomainEventSupervisorSupport.instance

        @JvmStatic
        val manager: DomainEventManager
            /**
             * 获取领域事件发布管理器
             * @return
             */
            get() = DomainEventSupervisorSupport.manager
    }
}

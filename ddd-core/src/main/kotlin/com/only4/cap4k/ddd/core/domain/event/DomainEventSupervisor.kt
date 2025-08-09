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
     * @param entity 绑定实体，该实体对象进入持久化上下文且事务提交时才会触发领域事件分发
     * @param schedule 指定时间发送
     */
    fun <DOMAIN_EVENT: Any, ENTITY: Any> attach(
        domainEventPayload: DOMAIN_EVENT,
        entity: ENTITY,
        schedule: LocalDateTime = LocalDateTime.now(),
    )

    fun <DOMAIN_EVENT: Any, ENTITY: Any> attach(
        domainEventPayload: DOMAIN_EVENT,
        entity: ENTITY,
        delay: Duration,
    ) {
        attach(domainEventPayload, entity, LocalDateTime.now().plus(delay))
    }

    /**
     * 附加领域事件到持久化上下文
     *
     * @param domainEventPayloadSupplier 领域事件消息体提供者
     * @param entity                     绑定实体，该实体对象进入持久化上下文且事务提交时才会触发领域事件分发
     * @param schedule                   指定时间发送
     * @param <DOMAIN_EVENT>             领域事件消息体类型
     * @param <ENTITY>                   实体类型
    </ENTITY></DOMAIN_EVENT> */
    fun <DOMAIN_EVENT : Any, ENTITY : Any> attach(
        entity: ENTITY,
        schedule: LocalDateTime = LocalDateTime.now(),
        domainEventPayloadSupplier: () -> (DOMAIN_EVENT)
    )

    fun <DOMAIN_EVENT : Any, ENTITY : Any> attach(
        entity: ENTITY,
        delay: Duration,
        domainEventPayloadSupplier: () -> (DOMAIN_EVENT)
    ) {
        attach(entity, LocalDateTime.now().plus(delay), domainEventPayloadSupplier)
    }

    /**
     * 从持久化上下文剥离领域事件
     * @param domainEventPayload 领域事件消息体
     * @param entity 关联实体
     */
    fun <DOMAIN_EVENT : Any, ENTITY : Any> detach(
        domainEventPayload: DOMAIN_EVENT,
        entity: ENTITY
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

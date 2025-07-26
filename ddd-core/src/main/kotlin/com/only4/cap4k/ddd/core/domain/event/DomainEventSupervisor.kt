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
    fun <DOMAIN_EVENT, ENTITY> attach(
        domainEventPayload: DOMAIN_EVENT & Any,
        entity: ENTITY & Any,
        schedule: LocalDateTime = LocalDateTime.now(),
    )

    fun <DOMAIN_EVENT, ENTITY> attach(
        domainEventPayload: DOMAIN_EVENT & Any,
        entity: ENTITY & Any,
        delay: Duration,
    ) {
        attach(domainEventPayload, entity, LocalDateTime.now().plus(delay))
    }

    /**
     * 从持久化上下文剥离领域事件
     * @param domainEventPayload 领域事件消息体
     * @param entity 关联实体
     */
    fun <DOMAIN_EVENT, ENTITY> detach(domainEventPayload: DOMAIN_EVENT & Any, entity: ENTITY & Any)

    companion object {
        val instance: DomainEventSupervisor
            /**
             * 获取领域事件管理器
             * @return 领域事件管理器
             */
            get() = DomainEventSupervisorSupport.instance

        val manager: DomainEventManager
            /**
             * 获取领域事件发布管理器
             * @return
             */
            get() = DomainEventSupervisorSupport.manager
    }
}

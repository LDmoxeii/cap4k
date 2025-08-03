package com.only4.cap4k.ddd.core.domain.event

import org.springframework.context.ApplicationEvent

/**
 * 领域事件所在事务正在提交事件
 *
 * @author LD_moxeii
 * @date 2025/07/20
 */
class DomainEventAttachedTransactionCommittingEvent(
    source: Any,
    val events: List<EventRecord>
) : ApplicationEvent(source)

package com.only4.core.domain.event

import org.springframework.context.ApplicationEvent

/**
 * 领域事件所在事务正在提交事件
 *
 * @author binking338
 * @date 2024/8/28
 */
class DomainEventAttachedTransactionCommittingEvent(
    source: Any,
    events: List<EventRecord>
) :
    ApplicationEvent(source) {
    val events: List<EventRecord> = events
}

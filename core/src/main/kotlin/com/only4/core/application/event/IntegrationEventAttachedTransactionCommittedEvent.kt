package com.only4.core.application.event

import com.only4.core.domain.event.EventRecord
import org.springframework.context.ApplicationEvent

/**
 * 集成事件所在事务成功提交事件
 *
 * @author binking338
 * @date 2024/8/28
 */
class IntegrationEventAttachedTransactionCommittedEvent(
    source: Any,
    events: List<EventRecord>
) :
    ApplicationEvent(source) {
    val events: List<EventRecord> = events
}

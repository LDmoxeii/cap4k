package com.only4.core.application.event

import com.only4.core.domain.event.EventRecord
import org.springframework.context.ApplicationEvent

/**
 * 集成事件事务提交事件
 * 当包含集成事件的事务成功提交时触发此事件
 *
 * @author binking338
 * @date 2024/8/28
 */
class IntegrationEventAttachedTransactionCommittedEvent(
    source: Any,
    /**
     * 事务中附加的集成事件列表
     */
    val events: List<EventRecord>
) : ApplicationEvent(source)

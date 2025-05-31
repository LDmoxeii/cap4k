package com.only4.core.domain.event

/**
 * 领域事件订阅接口
 *
 * @author binking338
 * @date 2023/8/5
 */
interface EventSubscriber<Event : Any> {
    /**
     * 领域事件消费逻辑
     *
     * @param event
     */
    fun onEvent(event: Event)
}

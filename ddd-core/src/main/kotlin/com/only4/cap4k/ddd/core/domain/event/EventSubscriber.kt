package com.only4.cap4k.ddd.core.domain.event

/**
 * 领域事件订阅接口
 *
 * @author LD_moxeii
 * @date 2025/07/20
 */
fun interface EventSubscriber<Event> {
    /**
     * 领域事件消费逻辑
     *
     * @param event 待处理的领域事件
     */
    fun onEvent(event: Event)
}

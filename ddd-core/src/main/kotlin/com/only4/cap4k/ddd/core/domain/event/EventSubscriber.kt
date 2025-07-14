package com.only4.cap4k.ddd.core.domain.event

/**
 * 领域事件订阅接口
 *
 * @author binking338
 * @date 2023/8/5
 */
fun interface EventSubscriber<Event> {
    /**
     * 领域事件消费逻辑
     *
     * @param event 待处理的领域事件
     */
    fun onEvent(event: Event)
}

/**
 * 领域事件订阅抽象类
 * 提供领域事件订阅的基础实现
 *
 * @author binking338
 * @date 2023/8/13
 */
abstract class AbstractEventSubscriber<Event> : EventSubscriber<Event> {
    /**
     * 领域事件消费逻辑
     * 子类必须实现此方法以处理具体的领域事件
     *
     * @param event 待处理的领域事件
     */
    abstract override fun onEvent(event: Event)
}

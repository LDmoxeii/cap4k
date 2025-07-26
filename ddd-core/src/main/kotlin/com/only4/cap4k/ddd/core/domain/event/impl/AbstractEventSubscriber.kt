package com.only4.cap4k.ddd.core.domain.event.impl

import com.only4.cap4k.ddd.core.domain.event.EventSubscriber

/**
 * 事件订阅者抽象类
 *
 * @author LD_moxeii
 * @date 2025/07/24
 */
abstract class AbstractEventSubscriber<Event : Any> : EventSubscriber<Event> {

    /**
     * 领域事件消费逻辑
     *
     * @param event 待处理的领域事件
     */
    abstract override fun onEvent(event: Event)
}

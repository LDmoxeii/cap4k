package com.only4.cap4k.ddd.core.domain.event

/**
 * 领域事件订阅管理器接口
 *
 * @author LD_moxeii
 * @date 2025/07/20
 */
interface EventSubscriberManager {
    /**
     * 订阅事件
     *
     * @param eventPayloadClass
     * @param subscriber
     * @return
     */
    fun subscribe(
        eventPayloadClass: Class<*>,
        subscriber: EventSubscriber<*>
    ): Boolean

    /**
     * 取消订阅
     *
     * @param eventPayloadClass
     * @param subscriber
     * @return
     */
    fun unsubscribe(
        eventPayloadClass: Class<*>,
        subscriber: EventSubscriber<*>
    ): Boolean

    /**
     * 分发事件到所有订阅者
     *
     * @param eventPayload
     */
    fun dispatch(eventPayload: Any)
}

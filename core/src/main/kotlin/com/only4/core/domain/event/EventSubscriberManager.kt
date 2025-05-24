package com.only4.core.domain.event

/**
 * 领域事件订阅管理器接口
 *
 * @author binking338
 * @date 2023/8/13
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

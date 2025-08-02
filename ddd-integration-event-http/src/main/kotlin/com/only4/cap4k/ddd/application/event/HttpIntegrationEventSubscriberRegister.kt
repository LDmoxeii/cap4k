package com.only4.cap4k.ddd.application.event

/**
 * 集成事件订阅注册器
 *
 * @author binking338
 * @date 2025/5/19
 */
interface HttpIntegrationEventSubscriberRegister {

    /**
     * 订阅
     *
     * @param event 事件
     * @param subscriber 订阅者
     * @param callbackUrl 回调地址
     * @return 订阅是否成功
     */
    fun subscribe(event: String, subscriber: String, callbackUrl: String): Boolean

    /**
     * 取消订阅
     *
     * @param event 事件
     * @param subscriber 订阅者
     * @return 取消订阅是否成功
     */
    fun unsubscribe(event: String, subscriber: String): Boolean

    /**
     * 获取事件列表
     *
     * @return 事件列表
     */
    fun events(): List<String>

    /**
     * 获取订阅者列表
     *
     * @param event 事件
     * @return 订阅者信息列表
     */
    fun subscribers(event: String): List<SubscriberInfo>

    /**
     * 订阅者信息
     */
    data class SubscriberInfo(
        val event: String,
        val subscriber: String,
        val callbackUrl: String
    )
}

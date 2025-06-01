package com.only4.cap4k.ddd.core.application.event.annotation

/**
 * 集成事件注解
 * 用于标记集成事件类，定义事件的发布和订阅规则
 *
 * @author binking338
 * @date 2024/8/27
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class IntegrationEvent(
    /**
     * 集成事件名称
     * 用于标识事件的唯一名称，通常作为消息队列的topic名称
     * 默认为空字符串
     */
    val value: String = "",

    /**
     * 订阅者标识
     * 用于标识事件的订阅者，通常作为消息队列的consumer group名称
     * 默认为NONE_SUBSCRIBER，表示没有特定订阅者
     */
    val subscriber: String = NONE_SUBSCRIBER
) {
    companion object {
        /**
         * 无订阅者标识
         * 用于表示该事件没有特定的订阅者
         */
        const val NONE_SUBSCRIBER: String = "[none]"
    }
}

package com.only4.core.application.event.annotation

/**
 * @author binking338
 * @date 2024/8/27
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class IntegrationEvent(
    /**
     * 集成事件名称
     * 通常作为MQ topic名称
     *
     * @return
     */
    val value: String = "",
    /**
     * 订阅者
     * 通常作为MQ consumer group名称
     *
     * @return
     */
    val subscriber: String = NONE_SUBSCRIBER
) {
    companion object {
        const val NONE_SUBSCRIBER: String = "[none]"
    }
}

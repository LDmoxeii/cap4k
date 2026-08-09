package com.only4.cap4k.ddd.core.application.event

import com.only4.cap4k.ddd.core.domain.event.EventRecord

/**
 * 集成事件发布器接口
 * 负责将集成事件发布到消息系统
 *
 * @author LD_moxeii
 * @date 2025/07/20
 */
interface IntegrationEventPublisher {
    /**
     * Publishes the canonical transport-neutral Integration Event envelope.
     *
     * The provider must report the provider-level handoff through [publishCallback].
     * A callback is not a global consumer acknowledgement; each destination/subscriber
     * owns its own acknowledgement boundary in the transport adapter.
     */
    fun publish(event: EventRecord, envelope: IntegrationEventEnvelope, publishCallback: PublishCallback)

    /**
     * 事件发布回调接口
     * 用于处理事件发布的结果
     */
    interface PublishCallback {
        /**
         * 事件发布成功回调
         *
         * @param event 成功发布的事件记录
         */
        fun onSuccess(event: EventRecord)

        /**
         * 事件发布异常回调
         *
         * @param event 发布失败的事件记录
         * @param throwable 导致失败的异常
         */
        fun onException(event: EventRecord, throwable: Throwable)
    }
}

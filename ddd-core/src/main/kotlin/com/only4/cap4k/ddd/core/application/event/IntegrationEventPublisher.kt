package com.only4.cap4k.ddd.core.application.event

import com.only4.cap4k.ddd.core.domain.event.EventRecord

/**
 * 集成事件发布器接口
 * 负责将集成事件发布到消息系统
 *
 * @author binking338
 * @date 2024/8/29
 */
interface IntegrationEventPublisher {
    /**
     * 发布集成事件
     *
     * @param event 要发布的事件记录
     * @param publishCallback 发布回调，用于处理发布结果
     */
    fun publish(event: EventRecord, publishCallback: PublishCallback)

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

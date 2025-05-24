package com.only4.core.application.event

import com.only4.core.domain.event.EventRecord

/**
 * 集成事件发布器
 *
 * @author binking338
 * @date 2024/8/29
 */
interface IntegrationEventPublisher {
    /**
     * 发布事件
     *
     * @param event
     * @param publishCallback
     */
    fun publish(event: EventRecord, publishCallback: PublishCallback)

    interface PublishCallback {
        /**
         * 发布成功
         *
         * @param event
         */
        fun onSuccess(event: EventRecord)

        /**
         * @param event
         * @param throwable
         */
        fun onException(event: EventRecord, throwable: Throwable)
    }
}

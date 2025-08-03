package com.only4.cap4k.ddd.core.domain.event.impl

import com.only4.cap4k.ddd.core.application.event.IntegrationEventInterceptorManager
import com.only4.cap4k.ddd.core.application.event.IntegrationEventPublisher
import com.only4.cap4k.ddd.core.domain.event.*
import com.only4.cap4k.ddd.core.share.Constants.HEADER_KEY_CAP4J_EVENT_TYPE
import com.only4.cap4k.ddd.core.share.Constants.HEADER_KEY_CAP4J_PERSIST
import com.only4.cap4k.ddd.core.share.Constants.HEADER_KEY_CAP4J_SCHEDULE
import com.only4.cap4k.ddd.core.share.Constants.HEADER_VALUE_CAP4J_EVENT_TYPE_DOMAIN
import com.only4.cap4k.ddd.core.share.Constants.HEADER_VALUE_CAP4J_EVENT_TYPE_INTEGRATION
import com.only4.cap4k.ddd.core.share.DomainException
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/**
 * 默认事件发布器
 *
 * @author LD_moxeii
 * @date 2025/07/24
 */
open class DefaultEventPublisher(
    private val eventSubscriberManager: EventSubscriberManager,
    private val integrationEventPublishers: List<IntegrationEventPublisher>,
    private val eventRecordRepository: EventRecordRepository,
    private val eventMessageInterceptorManager: EventMessageInterceptorManager,
    private val domainEventInterceptorManager: DomainEventInterceptorManager,
    private val integrationEventInterceptorManager: IntegrationEventInterceptorManager,
    private val integrationEventPublisherCallback: IntegrationEventPublisher.PublishCallback,
    private val threadPoolSize: Int
) : EventPublisher {

    companion object {
        private val log = LoggerFactory.getLogger(DefaultEventPublisher::class.java)
    }

    private val executor: ScheduledExecutorService by lazy {
        Executors.newScheduledThreadPool(threadPoolSize)
    }

    fun init() {
        // 预热执行器，触发lazy初始化
        executor
    }

    /**
     * 发布事件
     */
    override fun publish(event: EventRecord) {
        val message = event.message
        // 事件消息拦截器 - 初始化
        eventMessageInterceptorManager.orderedEventMessageInterceptors
            .forEach { interceptor -> interceptor.initPublish(message) }

        // 填入消息头
        val eventType = message.headers[HEADER_KEY_CAP4J_EVENT_TYPE] as? String
        var delay = Duration.ZERO

        if (message.headers.containsKey(HEADER_KEY_CAP4J_SCHEDULE)) {
            val scheduleAt = LocalDateTime.ofEpochSecond(
                message.headers[HEADER_KEY_CAP4J_SCHEDULE] as Long,
                0,
                ZoneOffset.UTC
            )
            if (scheduleAt != null) {
                delay = Duration.between(LocalDateTime.now(), scheduleAt)
            }
        }

        // 根据事件类型，选择不同的发布方式
        when (eventType) {
            HEADER_VALUE_CAP4J_EVENT_TYPE_INTEGRATION -> {
                if (delay.isNegative || delay.isZero) {
                    internalPublish4IntegrationEvent(event)
                } else {
                    executor.schedule({
                        internalPublish4IntegrationEvent(event)
                    }, delay.seconds, TimeUnit.SECONDS)
                }
            }

            HEADER_VALUE_CAP4J_EVENT_TYPE_DOMAIN, null -> {
                if (delay.isNegative || delay.isZero) {
                    val persist = message.headers[HEADER_KEY_CAP4J_PERSIST] as? Boolean ?: false
                    if (persist) {
                        executor.submit {
                            internalPublish4DomainEvent(event)
                        }
                    } else {
                        internalPublish4DomainEvent(event)
                    }
                } else {
                    executor.schedule({
                        internalPublish4DomainEvent(event)
                    }, delay.seconds, TimeUnit.SECONDS)
                }
            }
        }
    }

    override fun resume(eventRecord: EventRecord, minNextTryTime: LocalDateTime) {
        val now = LocalDateTime.now()
        val deliverTime = if (eventRecord.nextTryTime.isAfter(now)) {
            eventRecord.nextTryTime
        } else {
            now
        }

        eventRecord.beginDelivery(deliverTime)

        var maxTry = 65535
        while (eventRecord.nextTryTime.isBefore(minNextTryTime) && eventRecord.isValid) {
            eventRecord.beginDelivery(eventRecord.nextTryTime)
            if (maxTry-- <= 0) {
                throw DomainException("疑似死循环")
            }
        }

        eventRecordRepository.save(eventRecord)
        if (eventRecord.isDelivered) {
            eventRecord.markPersist(true)
            publish(eventRecord)
        }
    }

    override fun retry(uuid: String) {
        val eventRecord = eventRecordRepository.getById(uuid)
        eventRecord.markPersist(true)
        publish(eventRecord)
    }

    /**
     * 内部发布实现 - 领域事件
     */
    protected open fun internalPublish4DomainEvent(event: EventRecord) {
        try {
            val message = event.message
            val persist = message.headers[HEADER_KEY_CAP4J_PERSIST] as? Boolean ?: false

            domainEventInterceptorManager.orderedEventInterceptors4DomainEvent
                .forEach { interceptor -> interceptor.preRelease(event) }
            eventMessageInterceptorManager.orderedEventMessageInterceptors
                .forEach { interceptor -> interceptor.prePublish(message) }

            // 进程内消息
            val now = LocalDateTime.now()
            eventSubscriberManager.dispatch(event.payload)
            event.confirmedDelivery(now)

            if (persist) {
                domainEventInterceptorManager.orderedEventInterceptors4DomainEvent
                    .forEach { interceptor -> interceptor.prePersist(event) }
                eventRecordRepository.save(event)
                domainEventInterceptorManager.orderedEventInterceptors4DomainEvent
                    .forEach { interceptor -> interceptor.postPersist(event) }
            }

            eventMessageInterceptorManager.orderedEventMessageInterceptors
                .forEach { interceptor -> interceptor.postPublish(message) }
            domainEventInterceptorManager.orderedEventInterceptors4DomainEvent
                .forEach { interceptor -> interceptor.postRelease(event) }

        } catch (ex: Exception) {
            domainEventInterceptorManager.orderedEventInterceptors4DomainEvent
                .forEach { interceptor -> interceptor.onException(ex, event) }
            log.error("领域事件发布失败：${event.id}", ex)
            throw DomainException("领域事件发布失败：${event.id}", ex)
        }
    }

    /**
     * 内部发布实现 - 集成事件
     */
    protected open fun internalPublish4IntegrationEvent(event: EventRecord) {
        try {
            integrationEventInterceptorManager.orderedEventInterceptors4IntegrationEvent
                .forEach { interceptor -> interceptor.preRelease(event) }
            eventMessageInterceptorManager.orderedEventMessageInterceptors
                .forEach { interceptor -> interceptor.prePublish(event.message) }

            integrationEventPublishers.forEach { integrationEventPublisher ->
                integrationEventPublisher.publish(
                    event,
                    integrationEventPublisherCallback
                )
            }

        } catch (ex: Exception) {
            integrationEventInterceptorManager.orderedEventInterceptors4IntegrationEvent
                .forEach { interceptor -> interceptor.onException(ex, event) }
            log.error("集成事件发布失败：${event.id}", ex)
            throw DomainException("集成事件发布失败: ${event.id}", ex)
        }
    }
}

package com.only4.core.domain.event

import com.only4.core.application.event.IntegrationEventInterceptorManager
import com.only4.core.application.event.IntegrationEventPublisher
import com.only4.core.share.Constants.HEADER_KEY_CAP4J_EVENT_TYPE
import com.only4.core.share.Constants.HEADER_KEY_CAP4J_PERSIST
import com.only4.core.share.Constants.HEADER_KEY_CAP4J_SCHEDULE
import com.only4.core.share.Constants.HEADER_VALUE_CAP4J_EVENT_TYPE_DOMAIN
import com.only4.core.share.Constants.HEADER_VALUE_CAP4J_EVENT_TYPE_INTEGRATION
import com.only4.core.share.DomainException
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/**
 * 事件发布接口
 *
 * @author binking338
 * @date 2023/8/5
 */
interface EventPublisher {
    /**
     * 发布事件
     *
     * @param event
     */
    fun publish(event: EventRecord)

    /**
     * 重试事件
     *
     * @param event
     * @param minNextTryTime
     */
    fun retry(event: EventRecord, minNextTryTime: LocalDateTime)
}

/**
 * 默认事件发布器实现
 * 负责事件的发布和重试，支持领域事件和集成事件的发布
 *
 * @author binking338
 * @date 2023/8/13
 */
class DefaultEventPublisher(
    private val eventSubscriberManager: EventSubscriberManager,
    private val integrationEventPublisheres: List<IntegrationEventPublisher>,
    private val eventRecordRepository: EventRecordRepository,
    private val eventMessageInterceptorManager: EventMessageInterceptorManager,
    private val domainEventInterceptorManager: DomainEventInterceptorManager,
    private val integrationEventInterceptorManager: IntegrationEventInterceptorManager,
    private val threadPoolSize: Int
) : EventPublisher {

    private val log = LoggerFactory.getLogger(DefaultEventPublisher::class.java)

    /**
     * 线程池执行器
     * 使用lazy委托实现线程安全的延迟初始化
     */
    private val executor: ScheduledExecutorService by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        Executors.newScheduledThreadPool(threadPoolSize)
    }

    override fun publish(event: EventRecord) {
        val message = event.message
        // 事件消息拦截器 - 初始化
        eventMessageInterceptorManager.orderedEventMessageInterceptors.forEach { interceptor ->
            interceptor.initPublish(message)
        }

        // 填入消息头
        val eventType = message.headers[HEADER_KEY_CAP4J_EVENT_TYPE] as? String
        var delay = Duration.ZERO
        if (message.headers.containsKey(HEADER_KEY_CAP4J_SCHEDULE)) {
            val scheduleAt = LocalDateTime.ofEpochSecond(
                message.headers[HEADER_KEY_CAP4J_SCHEDULE] as Long,
                0,
                ZoneOffset.UTC
            )
            delay = Duration.between(LocalDateTime.now(), scheduleAt)
        }

        // 根据事件类型，选择不同的发布方式
        when (eventType) {
            HEADER_VALUE_CAP4J_EVENT_TYPE_INTEGRATION -> {
                if (delay.isNegative || delay.isZero) {
                    internalPublish4IntegrationEvent(event)
                } else {
                    executor.schedule({ internalPublish4IntegrationEvent(event) }, delay.seconds, TimeUnit.SECONDS)
                }
            }

            HEADER_VALUE_CAP4J_EVENT_TYPE_DOMAIN, null -> {
                if (delay.isNegative || delay.isZero) {
                    val persist = message.headers[HEADER_KEY_CAP4J_PERSIST] as? Boolean ?: false
                    if (persist) {
                        executor.submit { internalPublish4DomainEvent(event) }
                    } else {
                        internalPublish4DomainEvent(event)
                    }
                } else {
                    executor.schedule({ internalPublish4DomainEvent(event) }, delay.seconds, TimeUnit.SECONDS)
                }
            }
        }
    }

    override fun retry(event: EventRecord, minNextTryTime: LocalDateTime) {
        val now = LocalDateTime.now()
        val deliverTime = if (event.nextTryTime.isAfter(now)) event.nextTryTime else now

        val delivering = event.beginDelivery(deliverTime)

        var maxTry = 65535
        while (event.nextTryTime.isBefore(minNextTryTime) && event.isValid) {
            event.beginDelivery(event.nextTryTime)
            if (maxTry-- <= 0) {
                throw DomainException("疑似死循环")
            }
        }

        eventRecordRepository.save(event)
        if (delivering) {
            event.markPersist(true)
            publish(event)
        }
    }

    /**
     * 内部发布实现 - 领域事件
     *
     * @param event 事件记录
     */
    protected fun internalPublish4DomainEvent(event: EventRecord) {
        try {
            val message = event.message
            val persist = message.headers[HEADER_KEY_CAP4J_PERSIST] as? Boolean ?: false

            domainEventInterceptorManager.orderedEventInterceptors4DomainEvent.forEach { interceptor ->
                interceptor.preRelease(event)
            }
            eventMessageInterceptorManager.orderedEventMessageInterceptors.forEach { interceptor ->
                interceptor.prePublish(message)
            }

            // 进程内消息
            val now = LocalDateTime.now()
            eventSubscriberManager.dispatch(event.payload)
            event.confirmedDelivery(now)

            if (persist) {
                domainEventInterceptorManager.orderedEventInterceptors4DomainEvent.forEach { interceptor ->
                    interceptor.prePersist(event)
                }
                eventRecordRepository.save(event)
                domainEventInterceptorManager.orderedEventInterceptors4DomainEvent.forEach { interceptor ->
                    interceptor.postPersist(event)
                }
            }

            eventMessageInterceptorManager.orderedEventMessageInterceptors.forEach { interceptor ->
                interceptor.postPublish(message)
            }
            domainEventInterceptorManager.orderedEventInterceptors4DomainEvent.forEach { interceptor ->
                interceptor.postRelease(event)
            }
        } catch (ex: Exception) {
            domainEventInterceptorManager.orderedEventInterceptors4DomainEvent.forEach { interceptor ->
                interceptor.onException(ex, event)
            }
            log.error("领域事件发布失败：${event.id}", ex)
            throw DomainException("领域事件发布失败：${event.id}", ex)
        }
    }

    /**
     * 内部发布实现 - 集成事件
     *
     * @param event 事件记录
     */
    protected fun internalPublish4IntegrationEvent(event: EventRecord) {
        try {
            integrationEventInterceptorManager.orderedEventInterceptors4IntegrationEvent.forEach { interceptor ->
                interceptor.preRelease(event)
            }
            eventMessageInterceptorManager.orderedEventMessageInterceptors.forEach { interceptor ->
                interceptor.prePublish(event.message)
            }

            integrationEventPublisheres.forEach { integrationEventPublisher ->
                integrationEventPublisher.publish(
                    event,
                    IntegrationEventSendPublishCallback(
                        eventMessageInterceptorManager.orderedEventMessageInterceptors,
                        integrationEventInterceptorManager.orderedEventInterceptors4IntegrationEvent,
                        eventRecordRepository
                    )
                )
            }
        } catch (ex: Exception) {
            integrationEventInterceptorManager.orderedEventInterceptors4IntegrationEvent.forEach { interceptor ->
                interceptor.onException(ex, event)
            }
            log.error("集成事件发布失败：${event.id}", ex)
            throw DomainException("集成事件发布失败: ${event.id}", ex)
        }
    }

    /**
     * 集成事件发布回调实现
     */
    class IntegrationEventSendPublishCallback(
        private val orderedEventMessageInterceptors: Set<EventMessageInterceptor>,
        private val orderedIntegrationEventInterceptor: Set<EventInterceptor>,
        private val eventRecordRepository: EventRecordRepository
    ) : IntegrationEventPublisher.PublishCallback {

        override fun onSuccess(event: EventRecord) {
            val now = LocalDateTime.now()
            // 修改事件消费状态
            event.confirmedDelivery(now)

            orderedIntegrationEventInterceptor.forEach { interceptor ->
                interceptor.prePersist(event)
            }
            eventRecordRepository.save(event)
            orderedIntegrationEventInterceptor.forEach { interceptor ->
                interceptor.postPersist(event)
            }

            orderedEventMessageInterceptors.forEach { interceptor ->
                interceptor.postPublish(event.message)
            }
            orderedIntegrationEventInterceptor.forEach { interceptor ->
                interceptor.postRelease(event)
            }
        }

        override fun onException(event: EventRecord, throwable: Throwable) {
            val now = LocalDateTime.now()
            // 修改事件异常状态
            event.occurredException(now, throwable)

            orderedIntegrationEventInterceptor.forEach { interceptor ->
                interceptor.prePersist(event)
            }
            eventRecordRepository.save(event)
            orderedIntegrationEventInterceptor.forEach { interceptor ->
                interceptor.postPersist(event)
            }

            orderedIntegrationEventInterceptor.forEach { interceptor ->
                interceptor.onException(throwable, event)
            }
        }
    }
}

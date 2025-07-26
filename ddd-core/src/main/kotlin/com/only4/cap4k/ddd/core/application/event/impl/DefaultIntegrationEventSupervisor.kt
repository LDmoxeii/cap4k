package com.only4.cap4k.ddd.core.application.event.impl

import com.only4.cap4k.ddd.core.application.event.IntegrationEventAttachedTransactionCommittedEvent
import com.only4.cap4k.ddd.core.application.event.IntegrationEventInterceptorManager
import com.only4.cap4k.ddd.core.application.event.IntegrationEventManager
import com.only4.cap4k.ddd.core.application.event.IntegrationEventSupervisor
import com.only4.cap4k.ddd.core.application.event.annotation.IntegrationEvent
import com.only4.cap4k.ddd.core.domain.event.EventPublisher
import com.only4.cap4k.ddd.core.domain.event.EventRecord
import com.only4.cap4k.ddd.core.domain.event.EventRecordRepository
import com.only4.cap4k.ddd.core.share.DomainException
import org.springframework.context.ApplicationEventPublisher
import org.springframework.transaction.event.TransactionalEventListener
import java.time.Duration
import java.time.LocalDateTime

/**
 * 默认事件管理器
 *
 * @author LD_moxeii
 * @date 2025/07/26
 */
class DefaultIntegrationEventSupervisor(
    private val eventPublisher: EventPublisher,
    private val eventRecordRepository: EventRecordRepository,
    private val integrationEventInterceptorManager: IntegrationEventInterceptorManager,
    private val applicationEventPublisher: ApplicationEventPublisher,
    private val svcName: String
) : IntegrationEventSupervisor, IntegrationEventManager {

    companion object {
        private val TL_EVENT_PAYLOADS = ThreadLocal<MutableSet<Any>>()
        private val TL_EVENT_SCHEDULE_MAP = ThreadLocal<MutableMap<Any, LocalDateTime>>()
        private val EMPTY_EVENT_PAYLOADS: Set<Any> = emptySet()

        /**
         * 默认事件过期时间（分钟）
         * 一天 60*24 = 1440
         */
        private const val DEFAULT_EVENT_EXPIRE_MINUTES = 1440

        /**
         * 默认事件重试次数
         */
        private const val DEFAULT_EVENT_RETRY_TIMES = 200

        fun reset() {
            TL_EVENT_PAYLOADS.remove()
            TL_EVENT_SCHEDULE_MAP.remove()
        }
    }

    override fun <EVENT : Any> attach(eventPayload: EVENT, schedule: LocalDateTime) {
        // 判断集成事件，仅支持集成事件。
        if (!eventPayload::class.java.isAnnotationPresent(IntegrationEvent::class.java)) {
            throw DomainException("事件类型必须为集成事件")
        }

        val eventPayloads = TL_EVENT_PAYLOADS.get() ?: mutableSetOf<Any>().also {
            TL_EVENT_PAYLOADS.set(it)
        }

        eventPayloads.add(eventPayload)
        putDeliverTime(eventPayload, schedule)

        integrationEventInterceptorManager.orderedIntegrationEventInterceptors
            .forEach { interceptor -> interceptor.onAttach(eventPayload, schedule) }
    }

    override fun <EVENT : Any> detach(eventPayload: EVENT) {
        val eventPayloads = TL_EVENT_PAYLOADS.get() ?: return
        eventPayloads.remove(eventPayload)

        integrationEventInterceptorManager.orderedIntegrationEventInterceptors
            .forEach { interceptor -> interceptor.onDetach(eventPayload) }
    }

    override fun release() {
        val eventPayloads = popEvents().toMutableSet()
        val persistedEvents = mutableListOf<EventRecord>()

        for (eventPayload in eventPayloads) {
            val deliverTime = getDeliverTime(eventPayload)
            val event = eventRecordRepository.create().apply {
                init(
                    eventPayload,
                    svcName,
                    deliverTime,
                    Duration.ofMinutes(DEFAULT_EVENT_EXPIRE_MINUTES.toLong()),
                    DEFAULT_EVENT_RETRY_TIMES
                )
                markPersist(true)
            }

            integrationEventInterceptorManager.orderedEventInterceptors4IntegrationEvent
                .forEach { interceptor -> interceptor.prePersist(event) }

            eventRecordRepository.save(event)

            integrationEventInterceptorManager.orderedEventInterceptors4IntegrationEvent
                .forEach { interceptor -> interceptor.postPersist(event) }

            persistedEvents.add(event)
        }

        val integrationEventAttachedTransactionCommittedEvent =
            IntegrationEventAttachedTransactionCommittedEvent(this, persistedEvents)
        applicationEventPublisher.publishEvent(integrationEventAttachedTransactionCommittedEvent)
    }

    override fun <EVENT : Any> publish(eventPayload: EVENT, schedule: LocalDateTime) {
        val persistedEvents = mutableListOf<EventRecord>()
        val event = eventRecordRepository.create().apply {
            init(
                eventPayload,
                svcName,
                schedule,
                Duration.ofMinutes(DEFAULT_EVENT_EXPIRE_MINUTES.toLong()),
                DEFAULT_EVENT_RETRY_TIMES
            )
            markPersist(true)
        }

        integrationEventInterceptorManager.orderedEventInterceptors4IntegrationEvent
            .forEach { interceptor -> interceptor.prePersist(event) }

        eventRecordRepository.save(event)

        integrationEventInterceptorManager.orderedEventInterceptors4IntegrationEvent
            .forEach { interceptor -> interceptor.postPersist(event) }

        persistedEvents.add(event)

        val integrationEventAttachedTransactionCommittedEvent =
            IntegrationEventAttachedTransactionCommittedEvent(this, persistedEvents)
        applicationEventPublisher.publishEvent(integrationEventAttachedTransactionCommittedEvent)
    }

    @TransactionalEventListener(
        fallbackExecution = true,
        classes = [IntegrationEventAttachedTransactionCommittedEvent::class]
    )
    fun onTransactionCommitted(
        integrationEventAttachedTransactionCommittedEvent: IntegrationEventAttachedTransactionCommittedEvent
    ) {
        val events = integrationEventAttachedTransactionCommittedEvent.events
        events.takeIf { it.isNotEmpty() }?.forEach { event ->
            eventPublisher.publish(event)
        }
    }

    /**
     * 弹出事件列表
     *
     * @return 事件列表
     */
    protected fun popEvents(): Set<Any> {
        val eventPayloads = TL_EVENT_PAYLOADS.get()
        TL_EVENT_PAYLOADS.remove()
        return eventPayloads ?: EMPTY_EVENT_PAYLOADS
    }

    /**
     * 记录事件发送时间
     *
     * @param eventPayload 事件负载
     * @param schedule 调度时间
     */
    protected fun putDeliverTime(eventPayload: Any, schedule: LocalDateTime?) {
        val eventScheduleMap = TL_EVENT_SCHEDULE_MAP.get() ?: run {
            val newMap = mutableMapOf<Any, LocalDateTime>()
            TL_EVENT_SCHEDULE_MAP.set(newMap)
            newMap
        }
        schedule?.let { eventScheduleMap[eventPayload] = it }
    }

    protected fun getDeliverTime(eventPayload: Any): LocalDateTime {
        val eventScheduleMap = TL_EVENT_SCHEDULE_MAP.get()
        return eventScheduleMap?.get(eventPayload) ?: LocalDateTime.now()
    }
}

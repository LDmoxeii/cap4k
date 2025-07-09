package com.only4.cap4k.ddd.core.application.event.impl

import com.only4.cap4k.ddd.core.application.event.IntegrationEventAttachedTransactionCommittedEvent
import com.only4.cap4k.ddd.core.application.event.IntegrationEventInterceptorManager
import com.only4.cap4k.ddd.core.application.event.IntegrationEventManager
import com.only4.cap4k.ddd.core.application.event.IntegrationEventSupervisor
import com.only4.cap4k.ddd.core.application.event.annotation.IntegrationEvent
import com.only4.cap4k.ddd.core.domain.event.EventPublisher
import com.only4.cap4k.ddd.core.domain.event.EventRecord
import com.only4.cap4k.ddd.core.domain.event.EventRecordRepository
import org.springframework.context.ApplicationEventPublisher
import org.springframework.transaction.event.TransactionalEventListener
import java.time.Duration
import java.time.LocalDateTime

/**
 * 默认事件管理器
 *
 * @author binking338
 * @date 2024/8/28
 */
class DefaultIntegrationEventSupervisor(
    private val eventPublisher: EventPublisher,
    private val eventRecordRepository: EventRecordRepository,
    private val integrationEventInterceptorManager: IntegrationEventInterceptorManager,
    private val applicationEventPublisher: ApplicationEventPublisher,
    private val svcName: String,
) : IntegrationEventSupervisor, IntegrationEventManager {

    override fun <EVENT> attach(eventPayload: EVENT, schedule: LocalDateTime) {
        // 判断集成事件，仅支持集成事件
        requireNotNull(eventPayload) { "事件负载不能为空" }
        require(eventPayload.javaClass.isAnnotationPresent(IntegrationEvent::class.java)) { "事件类型必须为集成事件" }

        val eventPayloads = TL_EVENT_PAYLOADS.get() ?: HashSet<Any>().also { TL_EVENT_PAYLOADS.set(it) }
        eventPayloads.add(eventPayload)
        putDeliverTime(eventPayload, schedule)

        integrationEventInterceptorManager.orderedIntegrationEventInterceptors.forEach {
            it.onAttach(eventPayload, schedule)
        }
    }

    override fun <EVENT> detach(eventPayload: EVENT) {
        TL_EVENT_PAYLOADS.get()?.let { eventPayloads ->
            eventPayloads.remove(eventPayload!!)
            integrationEventInterceptorManager.orderedIntegrationEventInterceptors.forEach {
                it.onDetach(eventPayload)
            }
        }
    }

    override fun release() {
        val eventPayloads = HashSet<Any>().apply { addAll(popEvents()) }
        val persistedEvents = ArrayList<EventRecord>(eventPayloads.size)

        eventPayloads.forEach { eventPayload ->
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

            with(integrationEventInterceptorManager.orderedEventInterceptors4IntegrationEvent) {
                forEach { it.prePersist(event) }
                eventRecordRepository.save(event)
                forEach { it.postPersist(event) }
            }

            persistedEvents.add(event)
        }

        applicationEventPublisher.publishEvent(
            IntegrationEventAttachedTransactionCommittedEvent(this, persistedEvents)
        )
    }

    override fun <EVENT> publish(eventPayload: EVENT, schedule: LocalDateTime) {
        requireNotNull(eventPayload) { "事件负载不能为空" }

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

        with(integrationEventInterceptorManager.orderedEventInterceptors4IntegrationEvent) {
            forEach { it.prePersist(event) }
            eventRecordRepository.save(event)
            forEach { it.postPersist(event) }
        }

        applicationEventPublisher.publishEvent(
            IntegrationEventAttachedTransactionCommittedEvent(this, listOf(event))
        )
    }

    @TransactionalEventListener(
        fallbackExecution = true,
        classes = [IntegrationEventAttachedTransactionCommittedEvent::class]
    )
    fun onTransactionCommitted(event: IntegrationEventAttachedTransactionCommittedEvent) {
        event.events.takeIf { it.isNotEmpty() }?.forEach(eventPublisher::publish)
    }

    /**
     * 弹出事件列表
     * 获取并清除当前线程的事件列表
     *
     * @return 事件列表
     */
    protected fun popEvents(): Set<Any> {
        return TL_EVENT_PAYLOADS.get()?.also { TL_EVENT_PAYLOADS.remove() } ?: EMPTY_EVENT_PAYLOADS
    }

    /**
     * 记录事件发送时间
     *
     * @param eventPayload 事件负载
     * @param schedule 发送时间
     */
    protected fun putDeliverTime(eventPayload: Any, schedule: LocalDateTime) {
        val eventScheduleMap = TL_EVENT_SCHEDULE_MAP.get() ?: HashMap<Any, LocalDateTime>().also {
            TL_EVENT_SCHEDULE_MAP.set(it)
        }
        eventScheduleMap[eventPayload] = schedule
    }

    /**
     * 获取事件发送时间
     *
     * @param eventPayload 事件负载
     * @return 发送时间，如果未设置则返回当前时间
     */
    protected fun getDeliverTime(eventPayload: Any): LocalDateTime {
        return TL_EVENT_SCHEDULE_MAP.get()?.get(eventPayload) ?: LocalDateTime.now()
    }

    companion object {
        private val TL_EVENT_PAYLOADS = ThreadLocal<MutableSet<Any>>()
        private val TL_EVENT_SCHEDULE_MAP = ThreadLocal<MutableMap<Any, LocalDateTime>>()
        private val EMPTY_EVENT_PAYLOADS = emptySet<Any>()

        /**
         * 默认事件过期时间（分钟）
         * 一天 60*24 = 1440
         */
        private const val DEFAULT_EVENT_EXPIRE_MINUTES: Int = 1440

        /**
         * 默认事件重试次数
         */
        private const val DEFAULT_EVENT_RETRY_TIMES: Int = 200

        @JvmStatic
        fun reset() {
            TL_EVENT_PAYLOADS.remove()
            TL_EVENT_SCHEDULE_MAP.remove()
        }
    }
}

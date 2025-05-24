package com.only4.core.application.event

import com.only4.core.application.event.annotation.IntegrationEvent
import com.only4.core.domain.event.EventPublisher
import com.only4.core.domain.event.EventRecord
import com.only4.core.domain.event.EventRecordRepository
import org.springframework.context.ApplicationEventPublisher
import org.springframework.transaction.event.TransactionalEventListener
import java.time.Duration
import java.time.LocalDateTime

/**
 * 集成事件控制器
 *
 * @author binking338
 * @date 2024/8/25
 */
interface IntegrationEventSupervisor {

    /**
     * 附加事件到持久化上下文
     *
     * @param eventPayload 事件消息体
     * @param schedule     指定时间发送
     */
    fun <EVENT : Any> attach(
        eventPayload: EVENT,
        schedule: LocalDateTime = LocalDateTime.now(),
        delay: Duration = Duration.ZERO
    )

    /**
     * 从持久化上下文剥离事件
     *
     * @param eventPayload 事件消息体
     */
    fun <EVENT : Any> detach(eventPayload: EVENT)

    /**
     * 发布指定集成事件
     * @param eventPayload 集成事件负载
     * @param schedule     指定时间发送
     */
    fun <EVENT : Any> publish(
        eventPayload: EVENT,
        schedule: LocalDateTime = LocalDateTime.now(),
        delay: Duration = Duration.ZERO
    )

    companion object {
        val instance: IntegrationEventSupervisor
            get() = IntegrationEventSupervisorSupport.instance

        val manager: IntegrationEventManager
            get() = IntegrationEventSupervisorSupport.manager
    }
}

/**
 * 默认事件管理器
 *
 * @author binking338
 * @date 2024/8/28
 */
open class DefaultIntegrationEventSupervisor(
    private val eventPublisher: EventPublisher,
    private val eventRecordRepository: EventRecordRepository,
    private val integrationEventInterceptorManager: IntegrationEventInterceptorManager,
    private val applicationEventPublisher: ApplicationEventPublisher,
    private val svcName: String,
) : IntegrationEventSupervisor,
    IntegrationEventManager {
    override fun <EVENT : Any> attach(eventPayload: EVENT, schedule: LocalDateTime, delay: Duration) {
        require(!eventPayload.javaClass.isAnnotationPresent(IntegrationEvent::class.java)) { "事件类型必须为集成事件" }
        val eventPayloads = TL_EVENT_PAYLOADS.get() ?: HashSet<Any>().also { TL_EVENT_PAYLOADS.set(it) }
        eventPayloads.add(eventPayload)
        putDeliverTime(eventPayload, schedule)
        integrationEventInterceptorManager.orderedIntegrationEventInterceptors.forEach {
            it.onAttach(
                eventPayload,
                schedule
            )
        }
    }


    override fun <EVENT : Any> detach(eventPayload: EVENT) {
        val eventPayloads = TL_EVENT_PAYLOADS.get() ?: HashSet<Any>().also { TL_EVENT_PAYLOADS.set(it) }
        eventPayloads.remove(eventPayload)
        integrationEventInterceptorManager.orderedIntegrationEventInterceptors.forEach {
            it.onDetach(
                eventPayload
            )
        }
    }

    override fun release() {
        val eventPayloads = mutableSetOf(this.popEvents())
        val persistedEvents: MutableList<EventRecord> = ArrayList(eventPayloads.size)
        eventPayloads.forEach { eventPayload ->
            val deliverTime = this.getDeliverTime(eventPayload)
            val event = eventRecordRepository.create()
            event.init(
                eventPayload,
                this.svcName,
                deliverTime,
                Duration.ofMinutes(DEFAULT_EVENT_EXPIRE_MINUTES),
                DEFAULT_EVENT_RETRY_TIMES
            )
            event.markPersist(true)
            integrationEventInterceptorManager.orderedEventInterceptors4IntegrationEvent.forEach {
                it.prePersist(event)
            }
            eventRecordRepository.save(event)
            integrationEventInterceptorManager.orderedEventInterceptors4IntegrationEvent.forEach {
                it.postPersist(event)
            }
            persistedEvents.add(event)
        }
        val integrationEventAttachedTransactionCommittedEvent =
            IntegrationEventAttachedTransactionCommittedEvent(
                this, persistedEvents
            )
        applicationEventPublisher.publishEvent(integrationEventAttachedTransactionCommittedEvent)
    }

    override fun <EVENT : Any> publish(eventPayload: EVENT, schedule: LocalDateTime, delay: Duration) {
        val persistedEvents = ArrayList<EventRecord>(1)
        val evnet = eventRecordRepository.create()
        evnet.init(
            eventPayload,
            this.svcName,
            schedule,
            Duration.ofMinutes(DEFAULT_EVENT_EXPIRE_MINUTES),
            DEFAULT_EVENT_RETRY_TIMES
        )
        evnet.markPersist(true)
        integrationEventInterceptorManager.orderedEventInterceptors4IntegrationEvent.forEach {
            it.prePersist(evnet)
        }
        eventRecordRepository.save(evnet)
        integrationEventInterceptorManager.orderedEventInterceptors4IntegrationEvent.forEach {
            it.postPersist(evnet)
        }
        persistedEvents.add(evnet)
        val integrationEventAttachedTransactionCommittedEvent =
            IntegrationEventAttachedTransactionCommittedEvent(
                this, persistedEvents
            )
        applicationEventPublisher.publishEvent(integrationEventAttachedTransactionCommittedEvent)
    }

    @TransactionalEventListener(
        fallbackExecution = true,
        classes = [IntegrationEventAttachedTransactionCommittedEvent::class]
    )
    fun onTransactionCommitted(integrationEventAttachedTransactionCommittedEvent: IntegrationEventAttachedTransactionCommittedEvent) {
        val events = integrationEventAttachedTransactionCommittedEvent.events
        publish(events)
    }

    private fun publish(events: List<EventRecord>) {
        if (events.isNotEmpty()) {
            events.forEach(eventPublisher::publish)
        }
    }


    fun reset() {
        TL_EVENT_PAYLOADS.remove()
        TL_EVENT_SCHEDULE_MAP.remove()
    }

    /**
     * 弹出事件列表
     *
     * @return 事件列表
     */
    protected fun popEvents(): Set<Any> {
        val eventPayloads = TL_EVENT_PAYLOADS.get()
        TL_EVENT_PAYLOADS.remove()
        return eventPayloads
            ?: EMPTY_EVENT_PAYLOADS
    }


    /**
     * 记录事件发送时间
     *
     * @param eventPayload
     * @param schedule
     */
    protected fun putDeliverTime(eventPayload: Any, schedule: LocalDateTime) {
        val eventScheduleMap =
            TL_EVENT_SCHEDULE_MAP.get() ?: HashMap<Any, LocalDateTime>().also { TL_EVENT_SCHEDULE_MAP.set(it) }
        eventScheduleMap[eventPayload] = schedule
    }

    fun getDeliverTime(eventPayload: Any): LocalDateTime {
        val eventScheduleMap = TL_EVENT_SCHEDULE_MAP.get() ?: HashMap()
        return if (eventScheduleMap.containsKey(eventPayload)) eventScheduleMap[eventPayload]!!
        else LocalDateTime.now()
    }

    companion object {
        private val TL_EVENT_PAYLOADS = ThreadLocal<MutableSet<Any>>()
        private val TL_EVENT_SCHEDULE_MAP = ThreadLocal<MutableMap<Any, LocalDateTime>>()
        private val EMPTY_EVENT_PAYLOADS = emptySet<Any>()

        /**
         * 默认事件过期时间（分钟）
         * 一天 60*24 = 1440
         */
        private const val DEFAULT_EVENT_EXPIRE_MINUTES: Long = 1440L

        /**
         * 默认事件重试次数
         */
        private const val DEFAULT_EVENT_RETRY_TIMES: Int = 200
    }
}

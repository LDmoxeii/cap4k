package com.only4.cap4k.ddd.core.application.event

import com.only4.cap4k.ddd.core.application.event.annotation.IntegrationEvent
import com.only4.cap4k.ddd.core.domain.event.EventPublisher
import com.only4.cap4k.ddd.core.domain.event.EventRecord
import com.only4.cap4k.ddd.core.domain.event.EventRecordRepository
import org.springframework.context.ApplicationEventPublisher
import org.springframework.transaction.event.TransactionalEventListener
import java.time.Duration
import java.time.LocalDateTime

/**
 * 集成事件监督者接口
 * 负责管理和控制集成事件的生命周期，包括事件的附加、解除附加和发布
 *
 * @author binking338
 * @date 2024/8/25
 */
interface IntegrationEventSupervisor {
    /**
     * 附加事件到持久化上下文
     * 将事件添加到当前线程的上下文中，等待事务提交后发布
     *
     * @param eventPayload 事件消息体
     * @param schedule 指定时间发送
     * @param delay 延迟时间
     * @throws IllegalArgumentException 如果事件类型不是集成事件
     */
    fun <EVENT : Any> attach(
        eventPayload: EVENT,
        schedule: LocalDateTime = LocalDateTime.now(),
        delay: Duration = Duration.ZERO
    )

    /**
     * 从持久化上下文解除事件
     * 将事件从当前线程的上下文中移除
     *
     * @param eventPayload 事件消息体
     */
    fun <EVENT : Any> detach(eventPayload: EVENT)

    /**
     * 发布指定集成事件
     * 立即发布事件，不等待事务提交
     *
     * @param eventPayload 集成事件负载
     * @param schedule 指定时间发送
     * @param delay 延迟时间
     */
    fun <EVENT : Any> publish(
        eventPayload: EVENT,
        schedule: LocalDateTime = LocalDateTime.now(),
        delay: Duration = Duration.ZERO
    )

    companion object {
        /**
         * 获取集成事件监督者实例
         */
        val instance: IntegrationEventSupervisor
            get() = IntegrationEventSupervisorSupport.instance

        /**
         * 获取集成事件管理器实例
         */
        val manager: IntegrationEventManager
            get() = IntegrationEventSupervisorSupport.manager
    }
}

/**
 * 默认集成事件监督者实现
 * 提供集成事件的完整生命周期管理，包括事件的附加、解除附加、持久化和发布
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
        require(eventPayload.javaClass.isAnnotationPresent(IntegrationEvent::class.java)) { "事件类型必须为集成事件" }

        val eventPayloads = TL_EVENT_PAYLOADS.get() ?: HashSet<Any>().also { TL_EVENT_PAYLOADS.set(it) }
        eventPayloads.add(eventPayload)
        putDeliverTime(eventPayload, schedule)

        integrationEventInterceptorManager.orderedIntegrationEventInterceptors.forEach { interceptor ->
            interceptor.onAttach(eventPayload, schedule)
        }
    }

    override fun <EVENT : Any> detach(eventPayload: EVENT) {
        TL_EVENT_PAYLOADS.get()?.let { eventPayloads ->
            eventPayloads.remove(eventPayload)
            integrationEventInterceptorManager.orderedIntegrationEventInterceptors.forEach { interceptor ->
                interceptor.onDetach(eventPayload)
            }
        }
    }

    override fun release() {
        val eventPayloads = mutableSetOf(popEvents())
        val persistedEvents = ArrayList<EventRecord>(eventPayloads.size)

        eventPayloads.forEach { eventPayload ->
            val deliverTime = getDeliverTime(eventPayload)
            val event = eventRecordRepository.create().apply {
                init(
                    eventPayload,
                    svcName,
                    deliverTime,
                    Duration.ofMinutes(DEFAULT_EVENT_EXPIRE_MINUTES),
                    DEFAULT_EVENT_RETRY_TIMES
                )
                markPersist(true)
            }

            integrationEventInterceptorManager.orderedEventInterceptors4IntegrationEvent.forEach { it.prePersist(event) }
            eventRecordRepository.save(event)
            integrationEventInterceptorManager.orderedEventInterceptors4IntegrationEvent.forEach { it.postPersist(event) }
            persistedEvents.add(event)
        }

        applicationEventPublisher.publishEvent(
            IntegrationEventAttachedTransactionCommittedEvent(this, persistedEvents)
        )
    }

    override fun <EVENT : Any> publish(eventPayload: EVENT, schedule: LocalDateTime, delay: Duration) {
        val event = eventRecordRepository.create().apply {
            init(
                eventPayload,
                svcName,
                schedule,
                Duration.ofMinutes(DEFAULT_EVENT_EXPIRE_MINUTES),
                DEFAULT_EVENT_RETRY_TIMES
            )
            markPersist(true)
        }

        integrationEventInterceptorManager.orderedEventInterceptors4IntegrationEvent.forEach { it.prePersist(event) }
        eventRecordRepository.save(event)
        integrationEventInterceptorManager.orderedEventInterceptors4IntegrationEvent.forEach { it.postPersist(event) }

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

    fun reset() {
        TL_EVENT_PAYLOADS.remove()
        TL_EVENT_SCHEDULE_MAP.remove()
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
    fun getDeliverTime(eventPayload: Any): LocalDateTime =
        TL_EVENT_SCHEDULE_MAP.get()?.get(eventPayload) ?: LocalDateTime.now()

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

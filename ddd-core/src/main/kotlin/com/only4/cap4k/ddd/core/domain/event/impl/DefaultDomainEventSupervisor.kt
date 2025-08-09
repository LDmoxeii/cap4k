package com.only4.cap4k.ddd.core.domain.event.impl

import com.only4.cap4k.ddd.core.application.event.annotation.IntegrationEvent
import com.only4.cap4k.ddd.core.domain.aggregate.Aggregate
import com.only4.cap4k.ddd.core.domain.event.*
import com.only4.cap4k.ddd.core.domain.event.annotation.DomainEvent
import com.only4.cap4k.ddd.core.share.DomainException
import com.only4.cap4k.ddd.core.share.misc.findMethod
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.domain.AbstractAggregateRoot
import org.springframework.transaction.event.TransactionalEventListener
import java.time.Duration
import java.time.LocalDateTime

/**
 * 默认领域事件管理器
 *
 * @author LD_moxeii
 * @date 2025/07/24
 */
open class DefaultDomainEventSupervisor(
    private val eventRecordRepository: EventRecordRepository,
    private val domainEventInterceptorManager: DomainEventInterceptorManager,
    private val eventPublisher: EventPublisher,
    private val applicationEventPublisher: ApplicationEventPublisher,
    private val svcName: String
) : DomainEventSupervisor, DomainEventManager {

    companion object {
        private val TL_ENTITY_EVENT_PAYLOADS = ThreadLocal<MutableMap<Any, MutableSet<Any>>>()
        private val TL_EVENT_SCHEDULE_MAP = ThreadLocal<MutableMap<Any, LocalDateTime>>()
        private val EMPTY_EVENT_PAYLOADS: Set<Any> = emptySet()

        /**
         * 默认事件过期时间（分钟）
         */
        private const val DEFAULT_EVENT_EXPIRE_MINUTES = 30

        /**
         * 默认事件重试次数
         */
        private const val DEFAULT_EVENT_RETRY_TIMES = 16

        /**
         * 重置线程本地变量
         */
        @JvmStatic
        fun reset() {
            TL_ENTITY_EVENT_PAYLOADS.remove()
            TL_EVENT_SCHEDULE_MAP.remove()
        }
    }

    private fun unwrapEntity(entity: Any): Any = (entity as? Aggregate<*>)?._unwrap() ?: entity


    override fun <DOMAIN_EVENT : Any, ENTITY : Any> attach(
        domainEventPayload: DOMAIN_EVENT,
        entity: ENTITY,
        schedule: LocalDateTime
    ) {
        if (domainEventPayload::class.java.isAnnotationPresent(IntegrationEvent::class.java)) {
            throw DomainException("事件类型不能为集成事件")
        }

        val unwrappedEntity = unwrapEntity(entity)

        val entityEventPayloads = TL_ENTITY_EVENT_PAYLOADS.get() ?: mutableMapOf<Any, MutableSet<Any>>().also {
            TL_ENTITY_EVENT_PAYLOADS.set(it)
        }

        entityEventPayloads.computeIfAbsent(unwrappedEntity) { mutableSetOf() }
            .add(domainEventPayload)

        putDeliverTime(domainEventPayload, schedule)
        domainEventInterceptorManager.orderedDomainEventInterceptors
            .forEach { interceptor -> interceptor.onAttach(domainEventPayload, unwrappedEntity, schedule) }
    }

    override fun <DOMAIN_EVENT : Any, ENTITY : Any> attach(
        entity: ENTITY,
        schedule: LocalDateTime,
        domainEventPayloadSupplier: () -> DOMAIN_EVENT
    ) {
        attach(domainEventPayloadSupplier, entity, schedule)
    }

    override fun <DOMAIN_EVENT : Any, ENTITY : Any> detach(domainEventPayload: DOMAIN_EVENT, entity: ENTITY) {
        val entityEventPayloads = TL_ENTITY_EVENT_PAYLOADS.get() ?: return
        val unwrappedEntity = unwrapEntity(entity)
        val eventPayloads = entityEventPayloads[unwrappedEntity] ?: return

        eventPayloads.remove(domainEventPayload)
        domainEventInterceptorManager.orderedDomainEventInterceptors
            .forEach { interceptor -> interceptor.onDetach(domainEventPayload, unwrappedEntity) }
    }

    override fun release(entities: Set<Any>) {
        val eventPayloads = mutableSetOf<Any>()

        for (entity in entities) {
            eventPayloads.addAll(popEvents(entity))

            // 处理 Spring Data 的 AbstractAggregateRoot
            if (entity is AbstractAggregateRoot<*>) {
                val domainEventsMethod = findMethod(
                    AbstractAggregateRoot::class.java,
                    "domainEvents"
                ) { it.parameterCount == 0 }

                if (domainEventsMethod != null) {
                    domainEventsMethod.isAccessible = true
                    try {
                        val domainEvents = domainEventsMethod.invoke(entity)
                        if (domainEvents != null && domainEvents is Collection<*>) {
                            @Suppress("UNCHECKED_CAST")
                            eventPayloads.addAll(domainEvents as Collection<Any>)
                        }
                    } catch (throwable: Throwable) {
                        // 忽略异常，继续处理
                        continue
                    }

                    val clearDomainEventsMethod = findMethod(
                        AbstractAggregateRoot::class.java,
                        "clearDomainEvents"
                    ) { it.parameterCount == 0 }

                    try {
                        clearDomainEventsMethod?.invoke(entity)
                    } catch (throwable: Throwable) {
                        // 忽略异常，继续处理
                        continue
                    }
                }
            }
        }

        val persistedEvents = mutableListOf<EventRecord>()
        val transientEvents = mutableListOf<EventRecord>()
        val now = LocalDateTime.now()

        for (eventPayload in eventPayloads) {
            val deliverTime = getDeliverTime(eventPayload)
            val event = eventRecordRepository.create()
            event.init(
                eventPayload,
                svcName,
                deliverTime,
                Duration.ofMinutes(DEFAULT_EVENT_EXPIRE_MINUTES.toLong()),
                DEFAULT_EVENT_RETRY_TIMES
            )

            val isDelayDeliver = deliverTime.isAfter(now)
            if (!isDomainEventNeedPersist(eventPayload) && !isDelayDeliver) {
                event.markPersist(false)
                transientEvents.add(event)
            } else {
                event.markPersist(true)
                domainEventInterceptorManager.orderedEventInterceptors4DomainEvent
                    .forEach { interceptor -> interceptor.prePersist(event) }
                eventRecordRepository.save(event)
                domainEventInterceptorManager.orderedEventInterceptors4DomainEvent
                    .forEach { interceptor -> interceptor.postPersist(event) }
                persistedEvents.add(event)
            }
        }

        val domainEventAttachedTransactionCommittingEvent =
            DomainEventAttachedTransactionCommittingEvent(this, transientEvents)
        val domainEventAttachedTransactionCommittedEvent =
            DomainEventAttachedTransactionCommittedEvent(this, persistedEvents)

        onTransactionCommiting(domainEventAttachedTransactionCommittingEvent)
        applicationEventPublisher.publishEvent(domainEventAttachedTransactionCommittingEvent)
        applicationEventPublisher.publishEvent(domainEventAttachedTransactionCommittedEvent)
    }

    /**
     * 判断事件是否需要持久化
     * - 延迟或定时领域事件视情况进行持久化
     * - 显式指定persist=true的领域事件必须持久化
     */
    protected open fun isDomainEventNeedPersist(payload: Any): Boolean {
        val domainEvent = payload.javaClass.getAnnotation(DomainEvent::class.java)
        return domainEvent?.persist ?: false
    }

    protected open fun onTransactionCommiting(domainEventAttachedTransactionCommittingEvent: DomainEventAttachedTransactionCommittingEvent) {
        val events = domainEventAttachedTransactionCommittingEvent.events
        publish(events)
    }

    @TransactionalEventListener(
        fallbackExecution = true,
        classes = [DomainEventAttachedTransactionCommittedEvent::class]
    )
    fun onTransactionCommitted(domainEventAttachedTransactionCommittedEvent: DomainEventAttachedTransactionCommittedEvent) {
        val events = domainEventAttachedTransactionCommittedEvent.events
        publish(events)
    }

    private fun publish(events: List<EventRecord>) {
        if (events.isNotEmpty()) {
            events.forEach { event -> eventPublisher.publish(event) }
        }
    }

    /**
     * 弹出实体绑定的事件列表
     *
     * @param entity 关联实体
     * @return 事件列表
     */
    protected open fun popEvents(entity: Any): Set<Any> {
        val entityEventPayloads = TL_ENTITY_EVENT_PAYLOADS.get()
            ?: return EMPTY_EVENT_PAYLOADS

        if (!entityEventPayloads.containsKey(entity)) {
            return EMPTY_EVENT_PAYLOADS
        }

        val eventPayloads = entityEventPayloads.remove(entity)
        return eventPayloads?.map { if (it is Function0<*>) it.invoke()!! else it }?.toSet() ?: EMPTY_EVENT_PAYLOADS
    }

    /**
     * 记录事件发送时间
     */
    protected open fun putDeliverTime(eventPayload: Any, schedule: LocalDateTime) {
        var eventScheduleMap = TL_EVENT_SCHEDULE_MAP.get()
        if (eventScheduleMap == null) {
            eventScheduleMap = mutableMapOf()
            TL_EVENT_SCHEDULE_MAP.set(eventScheduleMap)
        }
        eventScheduleMap[eventPayload] = schedule
    }

    /**
     * 获取事件发送时间
     */
    fun getDeliverTime(eventPayload: Any): LocalDateTime {
        val eventScheduleMap = TL_EVENT_SCHEDULE_MAP.get()
        return if (eventScheduleMap != null && eventScheduleMap.containsKey(eventPayload)) {
            eventScheduleMap[eventPayload]!!
        } else {
            LocalDateTime.now()
        }
    }
}

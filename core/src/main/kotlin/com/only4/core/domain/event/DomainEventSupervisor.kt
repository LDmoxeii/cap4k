package com.only4.core.domain.event

import com.only4.core.application.event.annotation.IntegrationEvent
import com.only4.core.domain.aggregate.Aggregate
import com.only4.core.domain.event.annotation.DomainEvent
import com.only4.core.share.DomainException
import com.only4.core.share.misc.ClassUtils
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.domain.AbstractAggregateRoot
import org.springframework.transaction.event.TransactionalEventListener
import java.time.Duration
import java.time.LocalDateTime
import java.util.*

/**
 * 领域事件管理器
 *
 * @author binking338
 * @date 2023/8/12
 */
interface DomainEventSupervisor {

    /**
     * 附加领域事件到持久化上下文
     * @param domainEventPayload 领域事件消息体
     * @param entity 绑定实体，该实体对象进入持久化上下文且事务提交时才会触发领域事件分发
     * @param delay 延迟发送
     * @param schedule 指定时间发送
     */
    fun <DOMAIN_EVENT : Any, ENTITY : Any> attach(
        domainEventPayload: DOMAIN_EVENT,
        entity: ENTITY,
        schedule: LocalDateTime = LocalDateTime.now(),
    )

    fun <DOMAIN_EVENT : Any, ENTITY : Any> attach(
        domainEventPayload: DOMAIN_EVENT,
        entity: ENTITY,
        delay: Duration = Duration.ZERO,
    ) {
        attach(domainEventPayload, entity, LocalDateTime.now().plus(delay))
    }

    /**
     * 从持久化上下文剥离领域事件
     * @param domainEventPayload 领域事件消息体
     * @param entity 关联实体
     */
    fun <DOMAIN_EVENT : Any, ENTITY : Any> detach(domainEventPayload: DOMAIN_EVENT, entity: ENTITY)

    companion object {
        val instance: DomainEventSupervisor
            /**
             * 获取领域事件管理器
             * @return 领域事件管理器
             */
            get() = DomainEventSupervisorSupport.instance

        val manager: DomainEventManager
            /**
             * 获取领域事件发布管理器
             * @return
             */
            get() = DomainEventSupervisorSupport.manager
    }
}

/**
 * 默认领域事件管理器实现
 * 负责管理和发布领域事件，支持事件的附加、剥离和发布
 *
 * @author binking338
 * @date 2023/8/13
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
        const val DEFAULT_EVENT_EXPIRE_MINUTES: Int = 30

        /**
         * 默认事件重试次数
         */
        const val DEFAULT_EVENT_RETRY_TIMES: Int = 16

        /**
         * 重置线程本地变量
         */
        fun reset() {
            TL_ENTITY_EVENT_PAYLOADS.remove()
            TL_EVENT_SCHEDULE_MAP.remove()
        }
    }

    private fun unwrapEntity(entity: Any): Any =
        if (entity is Aggregate<*>) entity._unwrap() else entity

    override fun <DOMAIN_EVENT : Any, ENTITY : Any> attach(
        domainEventPayload: DOMAIN_EVENT,
        entity: ENTITY,
        schedule: LocalDateTime,
    ) {
        // 判断领域事件，不支持集成事件
        if (domainEventPayload.javaClass.isAnnotationPresent(IntegrationEvent::class.java)) {
            throw DomainException("事件类型不能为集成事件")
        }

        val unwrappedEntity = unwrapEntity(entity)
        val entityEventPayloads = TL_ENTITY_EVENT_PAYLOADS.get() ?: mutableMapOf<Any, MutableSet<Any>>().also {
            TL_ENTITY_EVENT_PAYLOADS.set(it)
        }

        entityEventPayloads.getOrPut(unwrappedEntity) { mutableSetOf() }.add(domainEventPayload)
        putDeliverTime(domainEventPayload, schedule)

        domainEventInterceptorManager.orderedDomainEventInterceptors.forEach { interceptor ->
            interceptor.onAttach(domainEventPayload, unwrappedEntity, schedule)
        }
    }

    override fun <DOMAIN_EVENT : Any, ENTITY : Any> detach(domainEventPayload: DOMAIN_EVENT, entity: ENTITY) {
        val entityEventPayloads = TL_ENTITY_EVENT_PAYLOADS.get() ?: return
        val unwrappedEntity = unwrapEntity(entity)
        val eventPayloads = entityEventPayloads[unwrappedEntity] ?: return

        eventPayloads.remove(domainEventPayload)
        domainEventInterceptorManager.orderedDomainEventInterceptors.forEach { interceptor ->
            interceptor.onDetach(domainEventPayload, unwrappedEntity)
        }
    }

    override fun release(entities: Set<Any>) {
        val eventPayloads = mutableSetOf<Any>()
        entities.forEach { entity ->
            eventPayloads.addAll(popEvents(entity))
            if (entity is AbstractAggregateRoot<*>) {
                ClassUtils.findMethod(AbstractAggregateRoot::class.java, "domainEvents") { it.parameterCount == 0 }
                    ?.let { method ->
                        method.isAccessible = true
                        method.invoke(entity)?.let { domainEvents ->
                            if (domainEvents is Collection<*>) {
                                eventPayloads.addAll(domainEvents.filterNotNull())
                            }
                        }
                    }

                ClassUtils.findMethod(AbstractAggregateRoot::class.java, "clearDomainEvents") { it.parameterCount == 0 }
                    ?.invoke(entity)
            }
        }

        val persistedEvents = mutableListOf<EventRecord>()
        val transientEvents = mutableListOf<EventRecord>()
        val now = LocalDateTime.now()

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
            }

            val isDelayDeliver = deliverTime.isAfter(now)
            if (!isDomainEventNeedPersist(eventPayload) && !isDelayDeliver) {
                event.markPersist(false)
                transientEvents.add(event)
            } else {
                event.markPersist(true)
                domainEventInterceptorManager.orderedEventInterceptors4DomainEvent.forEach { interceptor ->
                    interceptor.prePersist(event)
                }
                eventRecordRepository.save(event)
                domainEventInterceptorManager.orderedEventInterceptors4DomainEvent.forEach { interceptor ->
                    interceptor.postPersist(event)
                }
                persistedEvents.add(event)
            }
        }

        val committingEvent = DomainEventAttachedTransactionCommittingEvent(this, transientEvents)
        val committedEvent = DomainEventAttachedTransactionCommittedEvent(this, persistedEvents)

        onTransactionCommiting(committingEvent)
        applicationEventPublisher.publishEvent(committingEvent)
        applicationEventPublisher.publishEvent(committedEvent)
    }

    /**
     * 判断事件是否需要持久化
     * - 延迟或定时领域事件视情况进行持久化
     * - 显式指定persist=true的领域事件必须持久化
     *
     * @param payload 事件负载
     * @return 是否需要持久化
     */
    protected fun isDomainEventNeedPersist(payload: Any): Boolean =
        payload.javaClass.getAnnotation(DomainEvent::class.java)?.persist ?: false

    protected fun onTransactionCommiting(event: DomainEventAttachedTransactionCommittingEvent) {
        publish(event.events)
    }

    @TransactionalEventListener(
        fallbackExecution = true,
        classes = [DomainEventAttachedTransactionCommittedEvent::class]
    )
    fun onTransactionCommitted(event: DomainEventAttachedTransactionCommittedEvent) {
        publish(event.events)
    }

    private fun publish(events: List<EventRecord>) {
        events.forEach(eventPublisher::publish)
    }

    /**
     * 弹出实体绑定的事件列表
     *
     * @param entity 关联实体
     * @return 事件列表
     */
    protected fun popEvents(entity: Any): Set<Any> {
        val entityEventPayloads = TL_ENTITY_EVENT_PAYLOADS.get() ?: return EMPTY_EVENT_PAYLOADS
        return entityEventPayloads.remove(entity) ?: EMPTY_EVENT_PAYLOADS
    }

    /**
     * 记录事件发送时间
     *
     * @param eventPayload 事件负载
     * @param schedule 计划发送时间
     */
    protected fun putDeliverTime(eventPayload: Any, schedule: LocalDateTime) {
        val eventScheduleMap = TL_EVENT_SCHEDULE_MAP.get() ?: mutableMapOf<Any, LocalDateTime>().also {
            TL_EVENT_SCHEDULE_MAP.set(it)
        }
        eventScheduleMap[eventPayload] = schedule
    }

    fun getDeliverTime(eventPayload: Any): LocalDateTime =
        TL_EVENT_SCHEDULE_MAP.get()?.get(eventPayload) ?: LocalDateTime.now()
}

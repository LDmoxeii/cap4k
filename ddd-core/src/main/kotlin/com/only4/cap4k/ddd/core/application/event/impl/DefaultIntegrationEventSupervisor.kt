package com.only4.cap4k.ddd.core.application.event.impl

import com.only4.cap4k.ddd.core.application.event.IntegrationEventAttachedTransactionCommittedEvent
import com.only4.cap4k.ddd.core.application.event.IntegrationEventInterceptorManager
import com.only4.cap4k.ddd.core.application.event.IntegrationEventManager
import com.only4.cap4k.ddd.core.application.event.IntegrationEventSupervisor
import com.only4.cap4k.ddd.core.application.event.annotation.IntegrationEvent
import com.only4.cap4k.ddd.core.application.context.ExecutionContextAccessor
import com.only4.cap4k.ddd.core.application.context.ExecutionContextBoundary
import com.only4.cap4k.ddd.core.application.context.ExecutionContextCodecRegistry
import com.only4.cap4k.ddd.core.application.context.ExecutionContextSnapshot
import com.only4.cap4k.ddd.core.application.invocation.InvocationKind
import com.only4.cap4k.ddd.core.application.invocation.InvocationScopeAccessor
import com.only4.cap4k.ddd.core.domain.event.EventRecord
import com.only4.cap4k.ddd.core.domain.event.EventRecordRepository
import com.only4.cap4k.ddd.core.domain.event.EventRuntimeContextManager
import com.only4.cap4k.ddd.core.domain.event.ReliableEventCoordinator
import com.only4.cap4k.ddd.core.domain.event.impl.EventAttachment
import com.only4.cap4k.ddd.core.domain.event.impl.EventRuntimeContext
import com.only4.cap4k.ddd.core.domain.event.impl.EventRuntimeScopeType
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
open class DefaultIntegrationEventSupervisor(
    private val reliableEventCoordinator: ReliableEventCoordinator,
    private val eventRecordRepository: EventRecordRepository,
    private val integrationEventInterceptorManager: IntegrationEventInterceptorManager,
    private val applicationEventPublisher: ApplicationEventPublisher,
    private val svcName: String,
    private val executionContextAccessor: ExecutionContextAccessor = ExecutionContextAccessor {
        ExecutionContextSnapshot.EMPTY
    },
    private val executionContextCodecRegistry: ExecutionContextCodecRegistry = ExecutionContextCodecRegistry(emptyList()),
    private val invocationScopeAccessor: InvocationScopeAccessor,
) : IntegrationEventSupervisor, IntegrationEventManager {

    companion object {
        /**
         * 默认事件过期时间（分钟）
         * 一天 60*24 = 1440
         */
        private const val DEFAULT_EVENT_EXPIRE_MINUTES = 1440

        /**
         * 默认事件重试次数
         */
        private const val DEFAULT_EVENT_RETRY_TIMES = 200

        @JvmStatic
        fun reset() {
            EventRuntimeContextManager.reset()
        }
    }

    override fun <EVENT : Any> schedule(eventPayload: EVENT, schedule: LocalDateTime) {
        requireRegistrationScope()
        validateIntegrationEvent(eventPayload)
        EventRuntimeContext.attachmentScope()
            .attachIntegration(
                EventAttachment.eager(eventPayload, schedule, executionContextAccessor.current()),
            )

        integrationEventInterceptorManager.orderedIntegrationEventInterceptors
            .forEach { interceptor -> interceptor.onAttach(eventPayload, schedule) }
    }

    override fun <EVENT : Any> schedule(schedule: LocalDateTime, eventPayloadSupplier: () -> EVENT) {
        requireRegistrationScope()
        EventRuntimeContext.attachmentScope()
            .attachIntegration(
                EventAttachment.lazy(schedule, executionContextAccessor.current(), eventPayloadSupplier),
            )
    }

    private fun requireRegistrationScope() {
        val current = invocationScopeAccessor.current()
        check(current == InvocationKind.COMMAND || current == InvocationKind.DOMAIN_EVENT_HANDLER) {
            "Integration Event registration requires COMMAND or DOMAIN_EVENT_HANDLER invocation scope; " +
                "current=${current ?: "NONE"}"
        }
    }

    override fun release() {
        val scope = EventRuntimeContext.currentOrNull()
        val attachments = popEvents()
        if (attachments.isEmpty()) return
        val persistedEvents = mutableListOf<EventRecord>()

        for (attachment in attachments) {
            val eventPayload = attachment.resolve()
            validateIntegrationEvent(eventPayload)
            val event = persistEvent(eventPayload, attachment.schedule, attachment.executionContext)
            persistedEvents.add(event)
        }

        publishCommittedEvent(persistedEvents)
        if (scope?.type == EventRuntimeScopeType.AMBIENT && EventRuntimeContext.currentOrNull() === scope) {
            EventRuntimeContext.pop(scope)
        }
    }

    private fun persistEvent(
        eventPayload: Any,
        schedule: LocalDateTime,
        executionContext: ExecutionContextSnapshot,
    ): EventRecord {
        val event = eventRecordRepository.create().apply {
            init(
                eventPayload,
                svcName,
                schedule,
                Duration.ofMinutes(DEFAULT_EVENT_EXPIRE_MINUTES.toLong()),
                DEFAULT_EVENT_RETRY_TIMES,
                executionContextCodecRegistry.encode(
                    executionContext,
                    ExecutionContextBoundary.INTEGRATION_EVENT,
                ),
            )
            markPersist(true)
        }

        integrationEventInterceptorManager.orderedEventInterceptors4IntegrationEvent
            .forEach { interceptor -> interceptor.prePersist(event) }

        eventRecordRepository.save(event)

        integrationEventInterceptorManager.orderedEventInterceptors4IntegrationEvent
            .forEach { interceptor -> interceptor.postPersist(event) }

        return event
    }

    private fun publishCommittedEvent(events: List<EventRecord>) {
        val integrationEventAttachedTransactionCommittedEvent =
            IntegrationEventAttachedTransactionCommittedEvent(this, events)
        applicationEventPublisher.publishEvent(integrationEventAttachedTransactionCommittedEvent)
    }

    @TransactionalEventListener(
        fallbackExecution = true,
        classes = [IntegrationEventAttachedTransactionCommittedEvent::class]
    )
    fun onTransactionCommitted(event: IntegrationEventAttachedTransactionCommittedEvent) {
        if (event.events.isNotEmpty()) reliableEventCoordinator.wake()
    }

    private fun popEvents(): List<EventAttachment<Any>> {
        val attachments = (EventRuntimeContext.currentUnitOfWorkOrNull() ?: EventRuntimeContext.currentOrNull())
            ?.integrationAttachments ?: return emptyList()
        return attachments.toList().also {
            attachments.clear()
        }
    }

    private fun validateIntegrationEvent(eventPayload: Any) {
        if (!eventPayload::class.java.isAnnotationPresent(IntegrationEvent::class.java)) {
            throw DomainException("事件类型必须为集成事件")
        }
    }
}

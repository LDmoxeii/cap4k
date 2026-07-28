package com.only4.cap4k.ddd.domain.event

import com.only4.cap4k.ddd.core.domain.event.DomainEventAttachedTransactionCommittedEvent
import com.only4.cap4k.ddd.core.domain.event.DomainEventInterceptorManager
import com.only4.cap4k.ddd.core.domain.event.EventPublisher
import com.only4.cap4k.ddd.core.domain.event.EventRecord
import com.only4.cap4k.ddd.core.domain.event.EventRecordRepository
import com.only4.cap4k.ddd.core.domain.event.ReliableDomainEventProvider
import org.springframework.context.ApplicationEventPublisher
import org.springframework.transaction.event.TransactionalEventListener
import java.time.Duration
import java.time.LocalDateTime

/** JPA-backed reliable domain-event enrollment and after-commit delivery bridge. */
open class JpaReliableDomainEventProvider(
    private val eventRecordRepository: EventRecordRepository,
    private val domainEventInterceptorManager: DomainEventInterceptorManager,
    private val eventPublisher: EventPublisher,
    private val applicationEventPublisher: ApplicationEventPublisher,
    private val serviceName: String,
) : ReliableDomainEventProvider {
    companion object {
        private const val DEFAULT_EVENT_EXPIRE_MINUTES = 30L
        private const val DEFAULT_EVENT_RETRY_TIMES = 16
    }

    override fun publish(eventPayload: Any, schedule: LocalDateTime) {
        val event = eventRecordRepository.create().apply {
            init(
                eventPayload,
                serviceName,
                schedule,
                Duration.ofMinutes(DEFAULT_EVENT_EXPIRE_MINUTES),
                DEFAULT_EVENT_RETRY_TIMES,
            )
            markPersist(true)
        }
        persist(event)
        applicationEventPublisher.publishEvent(
            DomainEventAttachedTransactionCommittedEvent(this, listOf(event)),
        )
    }

    @TransactionalEventListener(
        fallbackExecution = true,
        classes = [DomainEventAttachedTransactionCommittedEvent::class],
    )
    fun onTransactionCommitted(event: DomainEventAttachedTransactionCommittedEvent) {
        event.events.forEach(eventPublisher::publish)
    }

    private fun persist(event: EventRecord) {
        domainEventInterceptorManager.orderedEventInterceptors4DomainEvent.forEach { it.prePersist(event) }
        eventRecordRepository.save(event)
        domainEventInterceptorManager.orderedEventInterceptors4DomainEvent.forEach { it.postPersist(event) }
    }
}

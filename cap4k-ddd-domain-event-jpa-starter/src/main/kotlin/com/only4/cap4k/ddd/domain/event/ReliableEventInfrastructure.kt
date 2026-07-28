package com.only4.cap4k.ddd.domain.event

import com.only4.cap4k.ddd.core.application.event.IntegrationEventInterceptor
import com.only4.cap4k.ddd.core.application.event.IntegrationEventInterceptorManager
import com.only4.cap4k.ddd.core.application.event.IntegrationEventPublisher
import com.only4.cap4k.ddd.core.domain.event.DomainEventInterceptor
import com.only4.cap4k.ddd.core.domain.event.EventInterceptor
import com.only4.cap4k.ddd.core.domain.event.EventMessageInterceptor
import com.only4.cap4k.ddd.core.domain.event.EventMessageInterceptorManager
import com.only4.cap4k.ddd.core.domain.event.EventRecord
import com.only4.cap4k.ddd.core.domain.event.EventRecordRepository
import org.springframework.core.Ordered
import org.springframework.core.annotation.OrderUtils
import java.time.LocalDateTime

class ReliableEventInfrastructure(
    messageInterceptors: List<EventMessageInterceptor>,
    eventInterceptors: List<EventInterceptor>,
    private val eventRecordRepository: EventRecordRepository,
) : EventMessageInterceptorManager, IntegrationEventInterceptorManager, IntegrationEventPublisher.PublishCallback {
    override val orderedEventMessageInterceptors: Set<EventMessageInterceptor> = messageInterceptors
        .sortedBy { OrderUtils.getOrder(it.javaClass, Ordered.LOWEST_PRECEDENCE) }
        .toCollection(LinkedHashSet())

    override val orderedIntegrationEventInterceptors: Set<IntegrationEventInterceptor> = eventInterceptors
        .filterIsInstance<IntegrationEventInterceptor>()
        .sortedBy { OrderUtils.getOrder(it.javaClass, Ordered.LOWEST_PRECEDENCE) }
        .toCollection(LinkedHashSet())

    override val orderedEventInterceptors4IntegrationEvent: Set<EventInterceptor> = eventInterceptors
        .filter { it !is DomainEventInterceptor || it is IntegrationEventInterceptor }
        .sortedBy { OrderUtils.getOrder(it.javaClass, Ordered.LOWEST_PRECEDENCE) }
        .toCollection(LinkedHashSet())

    override fun onSuccess(event: EventRecord) {
        event.endDelivery(LocalDateTime.now())
        persist(event)
        orderedEventMessageInterceptors.forEach { it.postPublish(event.message) }
        orderedEventInterceptors4IntegrationEvent.forEach { it.postRelease(event) }
    }

    override fun onException(event: EventRecord, throwable: Throwable) {
        event.occurredException(LocalDateTime.now(), throwable)
        persist(event)
        orderedEventInterceptors4IntegrationEvent.forEach { it.onException(throwable, event) }
    }

    private fun persist(event: EventRecord) {
        orderedEventInterceptors4IntegrationEvent.forEach { it.prePersist(event) }
        eventRecordRepository.save(event)
        orderedEventInterceptors4IntegrationEvent.forEach { it.postPersist(event) }
    }
}

package com.only4.cap4k.ddd.application.event

import com.only4.cap4k.ddd.core.application.event.IntegrationEventInterceptor
import com.only4.cap4k.ddd.core.application.event.IntegrationEventPayloadValidator
import com.only4.cap4k.ddd.core.application.event.IntegrationEventRouteResolver
import com.only4.cap4k.ddd.core.domain.event.EventRecord
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import java.net.URI
import java.time.LocalDateTime

/** Rejects an unusable outbound HTTP route before a reliable record is saved. */
@Order(Ordered.HIGHEST_PRECEDENCE)
class HttpIntegrationEventRouteInterceptor(
    private val routeResolver: IntegrationEventRouteResolver<URI>,
) : IntegrationEventInterceptor {
    override fun onAttach(eventPayload: Any, schedule: LocalDateTime) {
        routeResolver.resolve(IntegrationEventPayloadValidator.eventName(eventPayload))
    }

    override fun onDetach(eventPayload: Any) = Unit

    override fun prePersist(event: EventRecord) {
        routeResolver.resolve(event.type)
    }

    override fun postPersist(event: EventRecord) = Unit
    override fun preRelease(event: EventRecord) = Unit
    override fun postRelease(event: EventRecord) = Unit
    override fun onException(throwable: Throwable, event: EventRecord) = Unit
}

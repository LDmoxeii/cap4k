package com.only4.cap4k.ddd.application.event

import com.only4.cap4k.ddd.core.share.json.RuntimeJson
import com.only4.cap4k.ddd.core.application.context.DefaultExecutionContextManager
import com.only4.cap4k.ddd.core.application.context.ExecutionContextCodecRegistry
import com.only4.cap4k.ddd.core.application.event.IntegrationEventEnvelope
import com.only4.cap4k.ddd.core.application.event.IntegrationEventEnvelopeCodec
import com.only4.cap4k.ddd.core.application.event.annotation.IntegrationEvent
import com.only4.cap4k.ddd.core.domain.event.EventHandlerDispatcher
import com.only4.cap4k.ddd.core.domain.event.InboundIntegrationEventRegistrationView
import com.only4.cap4k.ddd.core.domain.event.ReliableEventDeliveryContext
import com.only4.cap4k.ddd.core.domain.event.ReliableEventDeliveryContextAccessor
import com.only4.cap4k.ddd.core.domain.event.impl.DefaultReliableEventDeliveryContextManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import java.time.Instant

class HttpIntegrationEventConsumeHandlerTest {
    @Test
    fun `consume endpoint uses canonical envelope body and ignores legacy metadata`() {
        val fixture = fixture()
        val handler = HttpIntegrationEventAutoConfiguration().httpIntegrationEventConsumeHandler(fixture.adapter)
        val publishedAt = Instant.parse("2026-08-04T00:00:00.123Z")
        val request = request(
            eventId = "event-from-query",
            eventName = "http.endpoint.event",
            publishedAt = publishedAt,
            payload = HttpEndpointEvent("payload"),
        ).apply {
            addParameter("eventId", "legacy-query-event")
            addParameter("event", "legacy-query-type")
            addHeader("cap4k-timestamp", "1")
        }
        val response = MockHttpServletResponse()

        handler.handleRequest(request, response)

        assertTrue(response.contentAsString.contains("\"success\":true"), response.contentAsString)
        val context = requireNotNull(fixture.observedContext)
        assertEquals("event-from-query", context.eventId)
        assertEquals("http.endpoint.event", context.eventName)
        assertEquals(publishedAt, context.publishedAt)
        assertNull(context.attempt)
        assertEquals("test-app", context.subscriberIdentity)
        assertEquals(
            com.only4.cap4k.ddd.core.domain.event.ReliableEventRedeliveryHint.UNKNOWN,
            context.redeliveryHint,
        )
        assertNull(fixture.deliveryAccessor.currentOrNull())
    }

    @Test
    fun `consume endpoint rejects malformed envelope metadata without dispatch`() {
        val malformed = fixture()
        val malformedHandler = HttpIntegrationEventAutoConfiguration().httpIntegrationEventConsumeHandler(malformed.adapter)
        val malformedRequest = rawRequest("not-json")
        val malformedResponse = MockHttpServletResponse()
        malformedHandler.handleRequest(malformedRequest, malformedResponse)
        assertFalse(malformedResponse.contentAsString.contains("\"success\":true"))
        assertNull(malformed.observedContext)
        assertNull(malformed.deliveryAccessor.currentOrNull())

        val invalidTime = fixture()
        val invalidTimeHandler =
            HttpIntegrationEventAutoConfiguration().httpIntegrationEventConsumeHandler(invalidTime.adapter)
        val invalidTimeRequest = rawRequest(
            """{"eventId":"event-2","eventType":"http.endpoint.event","originService":"test-source","publishedAt":"001","deliveryAttempt":null,"executionContext":[],"payloadJson":"{\"value\":\"payload\"}"}"""
        )
        val invalidTimeResponse = MockHttpServletResponse()
        invalidTimeHandler.handleRequest(invalidTimeRequest, invalidTimeResponse)
        assertFalse(invalidTimeResponse.contentAsString.contains("\"success\":true"))
        assertNull(invalidTime.observedContext)
        assertNull(invalidTime.deliveryAccessor.currentOrNull())

        val missingId = fixture()
        val missingIdHandler = HttpIntegrationEventAutoConfiguration().httpIntegrationEventConsumeHandler(missingId.adapter)
        val missingIdRequest = rawRequest(
            """{"eventType":"http.endpoint.event","originService":"test-source","publishedAt":"2026-08-04T00:00:00Z","deliveryAttempt":null,"executionContext":[],"payloadJson":"{\"value\":\"payload\"}"}"""
        )
        val missingIdResponse = MockHttpServletResponse()
        missingIdHandler.handleRequest(missingIdRequest, missingIdResponse)
        assertFalse(missingIdResponse.contentAsString.contains("\"success\":true"))
        assertNull(missingId.observedContext)
        assertNull(missingId.deliveryAccessor.currentOrNull())
    }

    private fun request(
        eventId: String,
        eventName: String,
        publishedAt: Instant,
        payload: HttpEndpointEvent,
    ) = rawRequest(
        IntegrationEventEnvelopeCodec().encode(
            IntegrationEventEnvelope(
                eventId = eventId,
                eventType = eventName,
                originService = "test-source",
                publishedAt = publishedAt,
                deliveryAttempt = null,
                executionContext = emptyList(),
                payloadJson = RuntimeJson.write(payload),
            )
        )
    )

    private fun rawRequest(body: String) = MockHttpServletRequest().apply {
        setContent(body.toByteArray(Charsets.UTF_8))
    }

    private fun fixture(): Fixture {
        val executionContexts = DefaultExecutionContextManager()
        val deliveryManager = DefaultReliableEventDeliveryContextManager(executionContexts, executionContexts)
        var observed: ReliableEventDeliveryContext? = null
        val dispatcher = EventHandlerDispatcher { observed = deliveryManager.currentOrNull() }
        val adapter = HttpIntegrationEventSubscriberAdapter(
            eventHandlerDispatcher = dispatcher,
            eventMessageInterceptors = emptyList(),
            eventTypeCatalog = EndpointEventCatalog,
            applicationName = "test-app",
            executionContextCodecRegistry = ExecutionContextCodecRegistry(emptyList()),
            executionContextScopeManager = executionContexts,
            reliableEventDeliveryContextScopeManager = deliveryManager,
        )
        return Fixture(adapter, deliveryManager, deliveryManager, { observed })
    }

    private data class Fixture(
        val adapter: HttpIntegrationEventSubscriberAdapter,
        val deliveryManager: DefaultReliableEventDeliveryContextManager,
        val deliveryAccessor: ReliableEventDeliveryContextAccessor,
        val observed: () -> ReliableEventDeliveryContext?,
    ) {
        val observedContext: ReliableEventDeliveryContext?
            get() = observed()
    }

    private object EndpointEventCatalog : InboundIntegrationEventRegistrationView {
        override fun integrationEventTypes(): Set<Class<*>> = setOf(HttpEndpointEvent::class.java)
    }
}

@IntegrationEvent("http.endpoint.event")
private data class HttpEndpointEvent(val value: String)

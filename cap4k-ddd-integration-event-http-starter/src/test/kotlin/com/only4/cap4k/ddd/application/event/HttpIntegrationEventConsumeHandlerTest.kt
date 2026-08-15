package com.only4.cap4k.ddd.application.event

import com.only4.cap4k.ddd.core.application.context.DefaultExecutionContextManager
import com.only4.cap4k.ddd.core.application.context.ExecutionContextAccessor
import com.only4.cap4k.ddd.core.application.context.ExecutionContextCodecRegistry
import com.only4.cap4k.ddd.core.application.event.IntegrationEventEnvelope
import com.only4.cap4k.ddd.core.application.event.IntegrationEventEnvelopeCodec
import com.only4.cap4k.contract.IntegrationEvent
import com.only4.cap4k.ddd.core.domain.event.EventHandlerDispatcher
import com.only4.cap4k.ddd.core.domain.event.InboundIntegrationEventRegistrationView
import com.only4.cap4k.ddd.core.domain.event.ReliableEventDeliveryContext
import com.only4.cap4k.ddd.core.domain.event.ReliableEventDeliveryContextAccessor
import com.only4.cap4k.ddd.core.domain.event.ReliableEventRedeliveryHint
import com.only4.cap4k.ddd.core.domain.event.impl.DefaultReliableEventDeliveryContextManager
import com.only4.cap4k.ddd.core.share.json.RuntimeJson
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import java.time.Instant

class HttpIntegrationEventConsumeHandlerTest {
    @Test
    fun `POST endpoint uses canonical envelope identity and clears delivery context`() {
        val fixture = fixture()
        val handler = HttpIntegrationEventAutoConfiguration().httpIntegrationEventConsumeHandler(fixture.adapter)
        val publishedAt = Instant.parse("2026-08-04T00:00:00.123Z")
        val request = request(
            eventId = "event-from-envelope",
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

        assertEquals(HttpStatus.OK.value(), response.status)
        assertTrue(response.contentAsString.contains("\"success\":true"), response.contentAsString)
        assertEquals(1, fixture.dispatchCount)
        val context = requireNotNull(fixture.observedContext)
        assertEquals("event-from-envelope", context.eventId)
        assertEquals("http.endpoint.event", context.eventName)
        assertEquals(publishedAt, context.publishedAt)
        assertNull(context.attempt)
        assertEquals(ReliableEventRedeliveryHint.UNKNOWN, context.redeliveryHint)
        assertNull(fixture.deliveryAccessor.currentOrNull())
        assertTrue(fixture.executionContextAccessor.current().isEmpty)
    }

    @Test
    fun `non POST request is rejected before decode or dispatch`() {
        val fixture = fixture()
        val handler = HttpIntegrationEventAutoConfiguration().httpIntegrationEventConsumeHandler(fixture.adapter)
        val request = request(
            eventId = "event-1",
            eventName = "http.endpoint.event",
            publishedAt = Instant.parse("2026-08-04T00:00:00Z"),
            payload = HttpEndpointEvent("payload"),
            method = HttpMethod.GET,
        )
        val response = MockHttpServletResponse()

        handler.handleRequest(request, response)

        assertEquals(HttpStatus.METHOD_NOT_ALLOWED.value(), response.status)
        assertEquals(HttpMethod.POST.name(), response.getHeader("Allow"))
        assertEquals(0, fixture.dispatchCount)
        assertNull(fixture.observedContext)
    }

    @Test
    fun `malformed envelope metadata returns non 2xx without dispatch`() {
        listOf(
            "not-json",
            """{"eventId":"event-2","eventType":"http.endpoint.event","originService":"test-source","publishedAt":"001","deliveryAttempt":null,"executionContext":[],"payloadJson":"{\"value\":\"payload\"}"}""",
            """{"eventType":"http.endpoint.event","originService":"test-source","publishedAt":"2026-08-04T00:00:00Z","deliveryAttempt":null,"executionContext":[],"payloadJson":"{\"value\":\"payload\"}"}""",
        ).forEach { body ->
            val fixture = fixture()
            val handler = HttpIntegrationEventAutoConfiguration().httpIntegrationEventConsumeHandler(fixture.adapter)
            val response = MockHttpServletResponse()

            handler.handleRequest(rawRequest(body), response)

            assertEquals(HttpStatus.BAD_REQUEST.value(), response.status)
            assertFalse(response.contentAsString.contains("\"success\":true"))
            assertEquals(0, fixture.dispatchCount)
            assertNull(fixture.deliveryAccessor.currentOrNull())
        }
    }

    @Test
    fun `unknown event without a local Handler returns non 2xx`() {
        val fixture = fixture()
        val handler = HttpIntegrationEventAutoConfiguration().httpIntegrationEventConsumeHandler(fixture.adapter)
        val response = MockHttpServletResponse()

        handler.handleRequest(
            request(
                eventId = "event-unknown",
                eventName = "http.unknown",
                publishedAt = Instant.parse("2026-08-04T00:00:00Z"),
                payload = HttpEndpointEvent("payload"),
            ),
            response,
        )

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY.value(), response.status)
        assertEquals(0, fixture.dispatchCount)
        assertNull(fixture.deliveryAccessor.currentOrNull())
    }

    @Test
    fun `Handler failure returns non 2xx and clears installed contexts`() {
        val fixture = fixture(failDispatch = true)
        val handler = HttpIntegrationEventAutoConfiguration().httpIntegrationEventConsumeHandler(fixture.adapter)
        val response = MockHttpServletResponse()

        handler.handleRequest(
            request(
                eventId = "event-failed",
                eventName = "http.endpoint.event",
                publishedAt = Instant.parse("2026-08-04T00:00:00Z"),
                payload = HttpEndpointEvent("business-secret"),
            ),
            response,
        )

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR.value(), response.status)
        assertEquals(1, fixture.dispatchCount)
        assertEquals("event-failed", fixture.observedContext?.eventId)
        assertNull(fixture.deliveryAccessor.currentOrNull())
        assertTrue(fixture.executionContextAccessor.current().isEmpty)
        assertFalse(response.contentAsString.contains("business-secret"))
    }

    @Test
    fun `duplicate envelope is dispatched again`() {
        val fixture = fixture()
        val handler = HttpIntegrationEventAutoConfiguration().httpIntegrationEventConsumeHandler(fixture.adapter)
        val requestBody = requireNotNull(request(
            eventId = "event-duplicate",
            eventName = "http.endpoint.event",
            publishedAt = Instant.parse("2026-08-04T00:00:00Z"),
            payload = HttpEndpointEvent("payload"),
        ).contentAsByteArray)

        repeat(2) {
            val response = MockHttpServletResponse()
            handler.handleRequest(rawRequest(requestBody.toString(Charsets.UTF_8)), response)
            assertEquals(HttpStatus.OK.value(), response.status)
        }

        assertEquals(2, fixture.dispatchCount)
    }

    private fun request(
        eventId: String,
        eventName: String,
        publishedAt: Instant,
        payload: HttpEndpointEvent,
        method: HttpMethod = HttpMethod.POST,
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
        ),
        method,
    )

    private fun rawRequest(
        body: String,
        method: HttpMethod = HttpMethod.POST,
    ) = MockHttpServletRequest(method.name(), HttpIntegrationEventAutoConfiguration.CONSUME_PATH).apply {
        setContent(body.toByteArray(Charsets.UTF_8))
    }

    private fun fixture(failDispatch: Boolean = false): Fixture {
        val executionContexts = DefaultExecutionContextManager()
        val deliveryManager = DefaultReliableEventDeliveryContextManager(executionContexts, executionContexts)
        var observed: ReliableEventDeliveryContext? = null
        var dispatchCount = 0
        val dispatcher = EventHandlerDispatcher {
            dispatchCount += 1
            observed = deliveryManager.currentOrNull()
            if (failDispatch) error("business-secret")
        }
        val adapter = HttpIntegrationEventSubscriberAdapter(
            eventHandlerDispatcher = dispatcher,
            eventMessageInterceptors = emptyList(),
            eventTypeCatalog = EndpointEventCatalog,
            executionContextCodecRegistry = ExecutionContextCodecRegistry(emptyList()),
            executionContextScopeManager = executionContexts,
            reliableEventDeliveryContextScopeManager = deliveryManager,
        )
        return Fixture(adapter, deliveryManager, executionContexts, { observed }, { dispatchCount })
    }

    private data class Fixture(
        val adapter: HttpIntegrationEventSubscriberAdapter,
        val deliveryAccessor: ReliableEventDeliveryContextAccessor,
        val executionContextAccessor: ExecutionContextAccessor,
        val observed: () -> ReliableEventDeliveryContext?,
        val dispatches: () -> Int,
    ) {
        val observedContext: ReliableEventDeliveryContext?
            get() = observed()
        val dispatchCount: Int
            get() = dispatches()
    }

    private object EndpointEventCatalog : InboundIntegrationEventRegistrationView {
        override fun integrationEventTypes(): Set<Class<*>> = setOf(HttpEndpointEvent::class.java)
    }
}

@IntegrationEvent("http.endpoint.event")
private data class HttpEndpointEvent(val value: String)

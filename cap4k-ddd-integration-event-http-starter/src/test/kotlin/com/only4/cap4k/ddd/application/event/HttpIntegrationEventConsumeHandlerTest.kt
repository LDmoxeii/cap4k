package com.only4.cap4k.ddd.application.event

import com.only4.cap4k.ddd.core.share.json.RuntimeJson
import com.only4.cap4k.ddd.application.event.HttpIntegrationEventAutoConfiguration.Companion.EVENT_ID_PARAM
import com.only4.cap4k.ddd.application.event.HttpIntegrationEventAutoConfiguration.Companion.EVENT_PARAM
import com.only4.cap4k.ddd.core.application.context.DefaultExecutionContextManager
import com.only4.cap4k.ddd.core.application.context.ExecutionContextCodecRegistry
import com.only4.cap4k.ddd.core.application.event.annotation.IntegrationEvent
import com.only4.cap4k.ddd.core.domain.event.EventHandlerDispatcher
import com.only4.cap4k.ddd.core.domain.event.EventTypeCatalog
import com.only4.cap4k.ddd.core.domain.event.ReliableEventDeliveryContext
import com.only4.cap4k.ddd.core.domain.event.ReliableEventDeliveryContextAccessor
import com.only4.cap4k.ddd.core.domain.event.impl.DefaultReliableEventDeliveryContextManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.core.env.Environment
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import java.time.Instant

class HttpIntegrationEventConsumeHandlerTest {
    @Test
    fun `consume endpoint passes strict independent parameters and payload class name`() {
        val fixture = fixture()
        val handler = HttpIntegrationEventAutoConfiguration().httpIntegrationEventConsumeHandler(fixture.adapter)
        val publishedAt = Instant.parse("2026-08-04T00:00:00.123Z")
        val request = request(
            eventId = "event-from-query",
            eventName = "http.endpoint.event",
            timestamp = publishedAt.toEpochMilli().toString(),
            payload = HttpEndpointEvent("payload"),
        ).apply {
            addHeader(EVENT_ID_PARAM, "event-from-generic-header")
            addHeader(EVENT_PARAM, "event-from-generic-header")
        }
        val response = MockHttpServletResponse()

        handler.handleRequest(request, response)

        assertTrue(response.contentAsString.contains("\"success\":true"), response.contentAsString)
        val context = requireNotNull(fixture.observedContext)
        assertEquals("event-from-query", context.eventId)
        assertEquals(HttpEndpointEvent::class.java.simpleName, context.eventName)
        assertEquals(publishedAt, context.publishedAt)
        assertNull(context.attempt)
        assertEquals(
            com.only4.cap4k.ddd.core.domain.event.ReliableEventRedeliveryHint.UNKNOWN,
            context.redeliveryHint,
        )
        assertNull(fixture.deliveryAccessor.currentOrNull())
    }

    @Test
    fun `consume endpoint rejects malformed and duplicate timestamp without dispatch`() {
        val malformed = fixture()
        val malformedHandler = HttpIntegrationEventAutoConfiguration().httpIntegrationEventConsumeHandler(malformed.adapter)
        val malformedRequest = request(
            eventId = "event-1",
            eventName = "http.endpoint.event",
            timestamp = "001",
            payload = HttpEndpointEvent("payload"),
        )
        val malformedResponse = MockHttpServletResponse()
        malformedHandler.handleRequest(malformedRequest, malformedResponse)
        assertFalse(malformedResponse.contentAsString.contains("\"success\":true"))
        assertNull(malformed.observedContext)
        assertNull(malformed.deliveryAccessor.currentOrNull())

        val duplicate = fixture()
        val duplicateHandler = HttpIntegrationEventAutoConfiguration().httpIntegrationEventConsumeHandler(duplicate.adapter)
        val duplicateRequest = request(
            eventId = "event-2",
            eventName = "http.endpoint.event",
            timestamp = "1000",
            payload = HttpEndpointEvent("payload"),
        ).apply {
            addHeader("cap4k-timestamp", "1001")
        }
        val duplicateResponse = MockHttpServletResponse()
        duplicateHandler.handleRequest(duplicateRequest, duplicateResponse)
        assertFalse(duplicateResponse.contentAsString.contains("\"success\":true"))
        assertNull(duplicate.observedContext)
        assertNull(duplicate.deliveryAccessor.currentOrNull())

        val duplicateParameter = fixture()
        val duplicateParameterHandler =
            HttpIntegrationEventAutoConfiguration().httpIntegrationEventConsumeHandler(duplicateParameter.adapter)
        val duplicateParameterRequest = request(
            eventId = "event-3",
            eventName = "http.endpoint.event",
            timestamp = "1000",
            payload = HttpEndpointEvent("payload"),
        ).apply {
            addParameter(EVENT_ID_PARAM, "event-override")
            addParameter(EVENT_PARAM, "http.endpoint.other")
        }
        val duplicateParameterResponse = MockHttpServletResponse()
        duplicateParameterHandler.handleRequest(duplicateParameterRequest, duplicateParameterResponse)
        assertFalse(duplicateParameterResponse.contentAsString.contains("\"success\":true"))
        assertNull(duplicateParameter.observedContext)
        assertNull(duplicateParameter.deliveryAccessor.currentOrNull())
    }

    private fun request(
        eventId: String,
        eventName: String,
        timestamp: String,
        payload: HttpEndpointEvent,
    ) = MockHttpServletRequest().apply {
        addParameter(EVENT_ID_PARAM, eventId)
        addParameter(EVENT_PARAM, eventName)
        addHeader("cap4k-timestamp", timestamp)
        setContent(RuntimeJson.write(payload).toByteArray(Charsets.UTF_8))
    }

    private fun fixture(): Fixture {
        val executionContexts = DefaultExecutionContextManager()
        val deliveryManager = DefaultReliableEventDeliveryContextManager(executionContexts, executionContexts)
        var observed: ReliableEventDeliveryContext? = null
        val dispatcher = EventHandlerDispatcher { observed = deliveryManager.currentOrNull() }
        val register = object : HttpIntegrationEventSubscriberRegister {
            override fun subscribe(event: String, subscriber: String, callbackUrl: String): Boolean = true
            override fun unsubscribe(event: String, subscriber: String): Boolean = true
            override fun events(): List<String> = emptyList()
            override fun subscribers(event: String): List<HttpIntegrationEventSubscriberRegister.SubscriberInfo> = emptyList()
        }
        val environment = object : Environment by org.springframework.mock.env.MockEnvironment() {}
        val adapter = HttpIntegrationEventSubscriberAdapter(
            eventHandlerDispatcher = dispatcher,
            eventMessageInterceptors = emptyList(),
            httpIntegrationEventSubscriberRegister = register,
            environment = environment,
            eventTypeCatalog = EndpointEventCatalog,
            applicationName = "test-app",
            httpBaseUrl = "http://localhost",
            httpSubscribePath = "/subscribe",
            httpConsumePath = "/consume",
            executionContextCodecRegistry = ExecutionContextCodecRegistry(emptyList()),
            executionContextScopeManager = executionContexts,
            reliableEventDeliveryContextScopeManager = deliveryManager,
        ).apply { init() }
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

    private object EndpointEventCatalog : EventTypeCatalog {
        override fun integrationEventTypes(): Set<Class<*>> = setOf(HttpEndpointEvent::class.java)
    }
}

@IntegrationEvent("http.endpoint.event", subscriber = "test-subscriber")
private data class HttpEndpointEvent(val value: String)

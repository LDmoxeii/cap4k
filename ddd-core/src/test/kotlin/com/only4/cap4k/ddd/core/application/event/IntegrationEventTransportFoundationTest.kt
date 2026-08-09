package com.only4.cap4k.ddd.core.application.event

import com.only4.cap4k.ddd.core.application.event.annotation.IntegrationEvent
import com.only4.cap4k.ddd.core.domain.event.InboundIntegrationEventRegistrationView
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class IntegrationEventTransportFoundationTest {
    @Test
    fun `static route resolver copies routes and resolves one explicit destination`() {
        val routes = linkedMapOf("content.published" to "http://localhost:8082")
        val resolver = StaticIntegrationEventRouteResolver(routes, "HTTP")

        routes["content.published"] = "http://localhost:9999"

        assertEquals("http://localhost:8082", resolver.resolve("content.published"))
        val failure = assertThrows<IntegrationEventRouteNotFoundException> {
            resolver.resolve("content.missing")
        }
        assertTrue(failure.message.orEmpty().contains("eventName=content.missing"))
    }

    @Test
    fun `static route resolver rejects malformed shared route facts`() {
        assertThrows<IllegalArgumentException> {
            StaticIntegrationEventRouteResolver(mapOf("event" to "route"), " ")
        }
        assertThrows<IllegalArgumentException> {
            StaticIntegrationEventRouteResolver(mapOf(" " to "route"), "HTTP")
        }
        val resolver = StaticIntegrationEventRouteResolver(mapOf("event" to "route"), "HTTP")
        assertThrows<IllegalArgumentException> { resolver.resolve(" ") }
    }

    @Test
    fun `stable event name lookup rejects two payload types with the same name`() {
        val view = object : InboundIntegrationEventRegistrationView {
            override fun integrationEventTypes(): Set<Class<*>> = setOf(
                FirstDuplicateEvent::class.java,
                SecondDuplicateEvent::class.java,
            )
        }

        val failure = assertThrows<IllegalStateException> { view.integrationEventTypesByName() }

        assertTrue(failure.message.orEmpty().contains("duplicate.event"))
        assertTrue(failure.message.orEmpty().contains(FirstDuplicateEvent::class.java.name))
        assertTrue(failure.message.orEmpty().contains(SecondDuplicateEvent::class.java.name))
    }

    @Test
    fun `stable event name lookup rejects blank event names`() {
        val view = object : InboundIntegrationEventRegistrationView {
            override fun integrationEventTypes(): Set<Class<*>> = setOf(BlankEventName::class.java)
        }

        val failure = assertThrows<IllegalArgumentException> { view.integrationEventTypesByName() }

        assertTrue(failure.message.orEmpty().contains("non-blank event name"))
    }

    @IntegrationEvent
    private data class BlankEventName(val value: String)

    @IntegrationEvent("duplicate.event")
    private data class FirstDuplicateEvent(val value: String)

    @IntegrationEvent("duplicate.event")
    private data class SecondDuplicateEvent(val value: String)
}

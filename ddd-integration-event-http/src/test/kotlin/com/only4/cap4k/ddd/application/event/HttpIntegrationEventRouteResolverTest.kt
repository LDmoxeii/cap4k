package com.only4.cap4k.ddd.application.event

import com.only4.cap4k.ddd.core.application.event.IntegrationEventRouteNotFoundException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.net.URI

class HttpIntegrationEventRouteResolverTest {
    @Test
    fun `route resolver preserves a base path and appends one fixed endpoint boundary`() {
        val resolver = HttpIntegrationEventRouteResolver(
            mapOf("content.published" to "http://localhost:8082/context/"),
        )

        val baseUri = resolver.resolve("content.published")

        assertEquals(URI("http://localhost:8082/context"), baseUri)
        assertEquals(
            URI("http://localhost:8082/context/cap4k/integration-events"),
            HttpIntegrationEventRouteResolver.endpoint(baseUri),
        )
    }

    @Test
    fun `route resolver rejects unusable HTTP route values without echoing them`() {
        val invalidRoutes = listOf(
            " " to "must not be blank",
            "/relative" to "absolute http or https",
            "ftp://localhost/events" to "absolute http or https",
            "http:///events" to "declare a host",
            "http://user:secret@localhost/events" to "must not contain user information",
            "http://localhost/events?token=secret" to "must not contain query or fragment",
            "http://localhost/events#fragment" to "must not contain query or fragment",
        )

        invalidRoutes.forEach { (route, expectedMessage) ->
            val failure = assertThrows<IllegalArgumentException> {
                HttpIntegrationEventRouteResolver(mapOf("content.published" to route))
            }
            assertEquals(false, failure.message.orEmpty().contains("secret"))
            assertEquals(true, failure.message.orEmpty().contains(expectedMessage), failure.message)
        }
    }

    @Test
    fun `route resolver rejects a missing event without fallback`() {
        val resolver = HttpIntegrationEventRouteResolver(emptyMap())

        assertThrows<IntegrationEventRouteNotFoundException> {
            resolver.resolve("content.missing")
        }
    }
}

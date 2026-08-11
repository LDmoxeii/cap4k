package com.only4.cap4k.ddd.application.event

import com.only4.cap4k.ddd.core.application.event.IntegrationEventRouteNotFoundException
import com.only4.cap4k.ddd.core.application.event.IntegrationEventRouteResolver
import java.net.URI

/** Immutable HTTP route table with provider-owned URI validation. */
class HttpIntegrationEventRouteResolver(
    routes: Map<String, String>,
) : IntegrationEventRouteResolver<URI> {
    private val routes: Map<String, URI> = routes
        .also { configured ->
            require(configured.keys.none(String::isBlank)) {
                "$PROVIDER_IDENTITY Integration Event route names must not be blank"
            }
        }
        .mapValues { (eventName, route) -> validateBaseUri(eventName, route) }
        .toMap()

    override fun resolve(eventName: String): URI {
        require(eventName.isNotBlank()) { "Integration Event event name must not be blank" }
        return routes[eventName]
            ?: throw IntegrationEventRouteNotFoundException(PROVIDER_IDENTITY, eventName)
    }

    companion object {
        const val PROVIDER_IDENTITY = "http"
        const val RECEIVE_PATH = "/cap4k/integration-events"

        fun endpoint(baseUri: URI): URI = URI.create(baseUri.toASCIIString() + RECEIVE_PATH)

        private fun validateBaseUri(eventName: String, route: String): URI {
            require(route.isNotBlank()) {
                "HTTP Integration Event route for eventName=$eventName must not be blank"
            }
            val parsed = try {
                URI(route)
            } catch (_: Exception) {
                throw IllegalArgumentException(
                    "HTTP Integration Event route for eventName=$eventName must be a valid absolute URI",
                )
            }
            val scheme = parsed.scheme?.lowercase()
            require(!parsed.isOpaque && parsed.isAbsolute && scheme in setOf("http", "https")) {
                "HTTP Integration Event route for eventName=$eventName must use absolute http or https URI"
            }
            require(!parsed.host.isNullOrBlank()) {
                "HTTP Integration Event route for eventName=$eventName must declare a host"
            }
            require(parsed.rawUserInfo == null) {
                "HTTP Integration Event route for eventName=$eventName must not contain user information"
            }
            require(parsed.rawQuery == null && parsed.rawFragment == null) {
                "HTTP Integration Event route for eventName=$eventName must not contain query or fragment"
            }

            val normalized = parsed.normalize()
            val path = normalized.rawPath.orEmpty().trimEnd('/')
            return URI("$scheme://${normalized.rawAuthority}$path")
        }
    }
}

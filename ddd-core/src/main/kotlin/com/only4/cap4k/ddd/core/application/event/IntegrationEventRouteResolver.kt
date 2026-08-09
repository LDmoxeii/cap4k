package com.only4.cap4k.ddd.core.application.event

/** Resolves provider-owned topology for one stable Integration Event name. */
fun interface IntegrationEventRouteResolver<ROUTE : Any> {
    fun resolve(eventName: String): ROUTE
}

/**
 * Immutable explicit route table used by transport starters.
 * Provider modules remain responsible for validating the route value itself.
 */
class StaticIntegrationEventRouteResolver<ROUTE : Any>(
    routes: Map<String, ROUTE>,
    private val providerIdentity: String,
) : IntegrationEventRouteResolver<ROUTE> {
    private val routes = routes.toMap().also { configured ->
        require(providerIdentity.isNotBlank()) { "Integration Event provider identity must not be blank" }
        require(configured.keys.none(String::isBlank)) {
            "$providerIdentity Integration Event route names must not be blank"
        }
    }

    override fun resolve(eventName: String): ROUTE {
        require(eventName.isNotBlank()) { "Integration Event event name must not be blank" }
        return routes[eventName]
            ?: throw IntegrationEventRouteNotFoundException(providerIdentity, eventName)
    }
}

class IntegrationEventRouteNotFoundException(
    providerIdentity: String,
    eventName: String,
) : IllegalStateException(
    "$providerIdentity Integration Event route is not configured for eventName=$eventName",
)

package com.only4.cap4k.ddd.endpoint.rpc.http

import com.only4.cap4k.ddd.endpoint.rpc.EndpointRpcFailureCategory
import com.only4.cap4k.ddd.endpoint.rpc.EndpointRemoteInvocationException
import java.net.URI

fun interface EndpointRpcRouteResolver { fun resolve(serviceId: String): URI }

class StaticEndpointRpcRouteResolver(routes: Map<String, String>) : EndpointRpcRouteResolver {
    private val routes = routes.mapValues { (serviceId, value) -> normalize(serviceId, value) }
    init { require(routes.keys.none(String::isBlank)) { "Endpoint RPC route serviceId must not be blank" } }
    override fun resolve(serviceId: String): URI {
        require(serviceId.isNotBlank()) { "Endpoint RPC serviceId must not be blank" }
        return routes[serviceId] ?: throw EndpointRemoteInvocationException(EndpointRpcFailureCategory.ROUTE, serviceId, "<unresolved>")
    }
    companion object {
        const val RPC_PATH = "/cap4k/endpoints/rpc"
        fun endpoint(baseUri: URI): URI = URI.create(baseUri.toASCIIString() + RPC_PATH)
        private fun normalize(serviceId: String, value: String): URI {
            require(value.isNotBlank()) { "Endpoint RPC route for serviceId=$serviceId must not be blank" }
            val parsed = runCatching { URI(value) }.getOrElse { throw IllegalArgumentException("Endpoint RPC route for serviceId=$serviceId must be a valid absolute URI") }
            val scheme = parsed.scheme?.lowercase()
            require(!parsed.isOpaque && parsed.isAbsolute && scheme in setOf("http", "https") && !parsed.host.isNullOrBlank()) {
                "Endpoint RPC route for serviceId=$serviceId must use absolute http or https URI"
            }
            require(parsed.rawUserInfo == null) { "Endpoint RPC route for serviceId=$serviceId must not contain user information" }
            require(parsed.rawQuery == null && parsed.rawFragment == null) { "Endpoint RPC route for serviceId=$serviceId must not contain query or fragment" }
            val normalized = parsed.normalize()
            val path = normalized.rawPath.orEmpty().trimEnd('/')
            return URI("$scheme://${normalized.rawAuthority}$path")
        }
    }
}

package com.only4.cap4k.ddd.endpoint.rpc.http

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class EndpointRpcRouteResolverTest {
    @Test fun `normalizes base URI and appends fixed route`() {
        val resolver = StaticEndpointRpcRouteResolver(mapOf("catalog" to "HTTPS://example.com/base/"))
        assertEquals("https://example.com/base", resolver.resolve("catalog").toString())
        assertEquals("https://example.com/base/cap4k/endpoints/rpc", StaticEndpointRpcRouteResolver.endpoint(resolver.resolve("catalog")).toString())
    }
    @Test fun `rejects unsafe routes`() {
        assertThrows<IllegalArgumentException> { StaticEndpointRpcRouteResolver(mapOf("catalog" to "https://user@example.com")) }
        assertThrows<IllegalArgumentException> { StaticEndpointRpcRouteResolver(mapOf("catalog" to "https://example.com?q=secret")) }
        assertThrows<IllegalArgumentException> { StaticEndpointRpcRouteResolver(mapOf("catalog" to "file:///tmp/rpc")) }
    }
}

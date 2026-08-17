package com.only4.cap4k.ddd.endpoint.rpc.http

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.only4.cap4k.contract.EndpointRequest
import com.only4.cap4k.ddd.core.application.context.ExecutionContextAccessor
import com.only4.cap4k.ddd.core.application.context.ExecutionContextCodecRegistry
import com.only4.cap4k.ddd.core.application.context.ExecutionContextSnapshot
import com.only4.cap4k.ddd.endpoint.rpc.ENDPOINT_RPC_PROTOCOL_VERSION
import com.only4.cap4k.ddd.endpoint.rpc.EndpointRemoteInvocationException
import com.only4.cap4k.ddd.endpoint.rpc.EndpointRpcFailure
import com.only4.cap4k.ddd.endpoint.rpc.EndpointRpcFailureCategory
import com.only4.cap4k.ddd.endpoint.rpc.EndpointRpcResponseEnvelope
import com.sun.net.httpserver.HttpServer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.net.InetSocketAddress
import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger

class HttpEndpointTransportInvokerTest {
    private val mapper = jacksonObjectMapper()

    @Test
    fun `real HTTP roundtrip decodes success and preserves Provider failure category`() {
        withServer { server ->
            val responses = ArrayDeque(
                listOf(
                    EndpointRpcResponseEnvelope(
                        codec = "json",
                        success = true,
                        payload = mapper.writeValueAsString(TestOperation.Response("ok")),
                    ),
                    EndpointRpcResponseEnvelope(
                        codec = "json",
                        success = false,
                        failure = EndpointRpcFailure(EndpointRpcFailureCategory.UNKNOWN_OPERATION, "unknown_operation", "safe-id"),
                    ),
                ),
            )
            server.createContext(StaticEndpointRpcRouteResolver.RPC_PATH) { exchange ->
                exchange.requestBody.use { it.readAllBytes() }
                val bytes = mapper.writeValueAsBytes(responses.removeFirst())
                exchange.responseHeaders.add("Content-Type", "application/json")
                exchange.sendResponseHeaders(200, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            }
            server.start()
            val invoker = invoker(server)

            assertEquals(
                TestOperation.Response("ok"),
                invoker.invoke("catalog", TestOperation.OPERATION_NAME, TestOperation.Request("Ada"), TestOperation.Request::class, TestOperation.Response::class),
            )
            val failure = assertThrows<EndpointRemoteInvocationException> {
                invoker.invoke("catalog", TestOperation.OPERATION_NAME, TestOperation.Request("Ada"), TestOperation.Request::class, TestOperation.Response::class)
            }
            assertEquals(EndpointRpcFailureCategory.UNKNOWN_OPERATION, failure.category)
            assertEquals("safe-id", failure.correlationId)
            assertTrue(failure.message!!.contains("unknown_operation").not())
        }
    }

    @Test
    fun `invalid response and non 2xx map to sanitized failures without retry`() {
        val count = AtomicInteger()
        withServer { server ->
            server.createContext(StaticEndpointRpcRouteResolver.RPC_PATH) { exchange ->
                count.incrementAndGet()
                exchange.requestBody.use { it.readAllBytes() }
                val bytes = if (count.get() == 1) {
                    mapper.writeValueAsBytes(
                        EndpointRpcResponseEnvelope(
                            protocolVersion = ENDPOINT_RPC_PROTOCOL_VERSION,
                            codec = "json",
                            success = false,
                            failure = EndpointRpcFailure(EndpointRpcFailureCategory.TIMEOUT, "forged_local_category"),
                        ),
                    )
                } else {
                    "upstream-secret".toByteArray()
                }
                val status = if (count.get() == 1) 200 else 503
                exchange.sendResponseHeaders(status, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            }
            server.start()
            val invoker = invoker(server)

            val protocol = assertThrows<EndpointRemoteInvocationException> {
                invoker.invoke("catalog", TestOperation.OPERATION_NAME, TestOperation.Request("Ada"), TestOperation.Request::class, TestOperation.Response::class)
            }
            assertEquals(EndpointRpcFailureCategory.PROTOCOL, protocol.category)
            assertNull(protocol.cause)

            val transport = assertThrows<EndpointRemoteInvocationException> {
                invoker.invoke("catalog", TestOperation.OPERATION_NAME, TestOperation.Request("Ada"), TestOperation.Request::class, TestOperation.Response::class)
            }
            assertEquals(EndpointRpcFailureCategory.TRANSPORT, transport.category)
            assertEquals(503, transport.status)
            assertTrue(transport.message!!.contains("upstream-secret").not())
            assertEquals(2, count.get())
        }
    }

    @Test
    fun `request customization failures are sanitized before network I O`() {
        withServer { server ->
            server.start()
            val invoker = invoker(
                server,
                EndpointRpcHttpRequestCustomizer { _, _, headers ->
                    headers["Authorization"] = "Bearer secret-token"
                    throw IllegalStateException("secret-token")
                },
            )

            val failure = assertThrows<EndpointRemoteInvocationException> {
                invoker.invoke("catalog", TestOperation.OPERATION_NAME, TestOperation.Request("Ada"), TestOperation.Request::class, TestOperation.Response::class)
            }
            assertEquals(EndpointRpcFailureCategory.TRANSPORT, failure.category)
            assertTrue(failure.message!!.contains("secret-token").not())
            assertNull(failure.cause)
        }
    }

    private fun invoker(
        server: HttpServer,
        customizer: EndpointRpcHttpRequestCustomizer = EndpointRpcHttpRequestCustomizer.NONE,
    ) = HttpEndpointTransportInvoker(
        StaticEndpointRpcRouteResolver(mapOf("catalog" to "http://127.0.0.1:${server.address.port}")),
        JacksonEndpointRpcCodec(mapper),
        mapper,
        ExecutionContextAccessor { ExecutionContextSnapshot.EMPTY },
        ExecutionContextCodecRegistry(emptyList()),
        Duration.ofSeconds(1),
        Duration.ofSeconds(2),
        customizer,
    )

    private fun withServer(block: (HttpServer) -> Unit) {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        try {
            block(server)
        } finally {
            server.stop(0)
        }
    }

    object TestOperation {
        const val OPERATION_NAME = "catalog.get"
        data class Request(val name: String) : EndpointRequest<Response>
        data class Response(val value: String)
    }
}

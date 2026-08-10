package com.only4.cap4k.ddd.application.event

import com.only4.cap4k.ddd.core.application.event.IntegrationEventEnvelope
import com.only4.cap4k.ddd.core.application.event.IntegrationEventEnvelopeCodec
import com.only4.cap4k.ddd.core.application.event.IntegrationEventPublisher
import com.only4.cap4k.ddd.core.application.event.IntegrationEventRouteResolver
import com.only4.cap4k.ddd.core.domain.event.EventRecord
import com.only4.cap4k.ddd.core.application.provider.InMemoryRuntimeProviderStateRegistry
import com.only4.cap4k.ddd.core.application.provider.RuntimeProviderState
import com.only4.cap4k.ddd.core.application.provider.RuntimeProviderStateReporter
import com.sun.net.httpserver.HttpServer
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.URI
import java.time.Duration
import java.time.Instant
import java.util.concurrent.Executor

class HttpIntegrationEventRealClientTest {
    private companion object {
        const val HTTP_PROVIDER_ID = "integration-event-transport.http"
    }
    @Test
    fun `real client performs one POST and completes only after 2xx`() {
        var method: String? = null
        var body: String? = null
        withServer { server ->
            server.createContext("/context/cap4k/integration-events") { exchange ->
                try {
                    method = exchange.requestMethod
                    body = exchange.requestBody.readBytes().toString(Charsets.UTF_8)
                    exchange.sendResponseHeaders(204, -1)
                } finally {
                    exchange.close()
                }
            }
            server.start()
            val event = event()
            val envelope = envelope(event)
            val callback = CapturingCallback()
            publisher(server, "/context").publish(event, envelope, callback)

            assertEquals("POST", method)
            assertEquals(envelope, IntegrationEventEnvelopeCodec().decode(requireNotNull(body)))
            assertEquals(1, callback.successCount)
            assertNull(callback.failure)
        }
    }

    @Test
    fun `real client maps non 2xx to one safe failure`() {
        withServer { server ->
            server.createContext("/cap4k/integration-events") { exchange ->
                try {
                    exchange.sendResponseHeaders(503, -1)
                } finally {
                    exchange.close()
                }
            }
            server.start()
            val event = event()
            val callback = CapturingCallback()

            publisher(server).publish(event, envelope(event), callback)

            assertEquals(0, callback.successCount)
            assertEquals(1, callback.failureCount)
            val failure = callback.failure as HttpIntegrationEventHandoffException
            assertEquals(HttpIntegrationEventFailureCategory.NON_2XX, failure.category)
            assertEquals(503, failure.statusCode)
        }
    }

    @Test
    fun `real client bounds response wait and reports timeout`() {
        withServer { server ->
            server.createContext("/cap4k/integration-events") { exchange ->
                try {
                    Thread.sleep(300)
                    exchange.sendResponseHeaders(204, -1)
                } finally {
                    exchange.close()
                }
            }
            server.start()
            val event = event()
            val callback = CapturingCallback()

            publisher(server, responseTimeout = Duration.ofMillis(50))
                .publish(event, envelope(event), callback)

            assertEquals(0, callback.successCount)
            assertEquals(1, callback.failureCount)
            assertEquals(
                HttpIntegrationEventFailureCategory.TIMEOUT,
                (callback.failure as HttpIntegrationEventHandoffException).category,
            )
        }
    }

    @Test
    fun `connection refusal reports one retryable provider failure`() {
        val unusedPort = ServerSocket(0).use { it.localPort }
        val event = event()
        val callback = CapturingCallback()
        val stateRegistry = InMemoryRuntimeProviderStateRegistry()
        val stateReporter = stateRegistry.register(HTTP_PROVIDER_ID)
        val refusalPublisher = HttpIntegrationEventPublisher(
            routeResolver = IntegrationEventRouteResolver { URI("http://127.0.0.1:$unusedPort") },
            executorOverride = Executor { it.run() },
            connectTimeout = Duration.ofMillis(100),
            responseTimeout = Duration.ofMillis(100),
            providerState = stateReporter,
        )

        refusalPublisher.publish(event, envelope(event), callback)

        assertEquals(0, callback.successCount)
        assertEquals(1, callback.failureCount)
        assertEquals(
            HttpIntegrationEventFailureCategory.CONNECTION,
            (callback.failure as HttpIntegrationEventHandoffException).category,
        )
        assertEquals(RuntimeProviderState.DEGRADED, stateRegistry.httpFact().state)
        assertEquals("CONNECTION", stateRegistry.httpFact().category)

        val server = HttpServer.create(InetSocketAddress("127.0.0.1", unusedPort), 0)
        try {
            server.createContext("/cap4k/integration-events") { exchange ->
                try {
                    exchange.requestBody.readBytes()
                    exchange.sendResponseHeaders(204, -1)
                } finally {
                    exchange.close()
                }
            }
            server.start()
            val recovery = CapturingCallback()
            val recoveryPublisher = HttpIntegrationEventPublisher(
                routeResolver = IntegrationEventRouteResolver { URI("http://127.0.0.1:$unusedPort") },
                executorOverride = Executor { it.run() },
                connectTimeout = Duration.ofSeconds(1),
                responseTimeout = Duration.ofSeconds(1),
                providerState = stateReporter,
            )

            recoveryPublisher.publish(event, envelope(event), recovery)

            assertEquals(1, recovery.successCount)
            assertEquals(0, recovery.failureCount)
            assertNull(recovery.failure)
            assertEquals(RuntimeProviderState.HEALTHY, stateRegistry.httpFact().state)
        } finally {
            server.stop(0)
            stateReporter.close()
        }
        assertEquals(emptyList<com.only4.cap4k.ddd.core.application.provider.RuntimeProviderStateFact>(), stateRegistry.snapshot())
    }

    private fun publisher(
        server: HttpServer,
        basePath: String = "",
        responseTimeout: Duration = Duration.ofSeconds(1),
    ): HttpIntegrationEventPublisher = HttpIntegrationEventPublisher(
        routeResolver = IntegrationEventRouteResolver {
            URI("http://127.0.0.1:${server.address.port}$basePath")
        },
        executorOverride = Executor { it.run() },
        connectTimeout = Duration.ofSeconds(1),
        responseTimeout = responseTimeout,
        providerState = NoOpStateReporter,
    )

    private fun withServer(block: (HttpServer) -> Unit) {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        try {
            block(server)
        } finally {
            server.stop(0)
        }
    }

    private fun event(): EventRecord = mockk(relaxed = true) {
        every { id } returns "event-1"
        every { type } returns "http.real.event"
        every { publishedAt } returns Instant.parse("2026-08-09T00:00:00Z")
    }

    private fun envelope(event: EventRecord) = IntegrationEventEnvelope(
        eventId = event.id,
        eventType = event.type,
        originService = "sender",
        publishedAt = event.publishedAt,
        deliveryAttempt = 1,
        executionContext = emptyList(),
        payloadJson = "{\"value\":\"payload\"}",
    )

    private fun InMemoryRuntimeProviderStateRegistry.httpFact() = snapshot().single {
        it.providerId == HTTP_PROVIDER_ID
    }

    private object NoOpStateReporter : RuntimeProviderStateReporter {
        override val providerId: String = HTTP_PROVIDER_ID
        override fun report(state: RuntimeProviderState, category: String?, observedAt: Instant) = Unit
        override fun close() = Unit
    }
    private class CapturingCallback : IntegrationEventPublisher.PublishCallback {
        var successCount: Int = 0
        var failureCount: Int = 0
        var failure: Throwable? = null

        override fun onSuccess(event: EventRecord) {
            successCount += 1
        }

        override fun onException(event: EventRecord, throwable: Throwable) {
            failureCount += 1
            failure = throwable
        }
    }
}

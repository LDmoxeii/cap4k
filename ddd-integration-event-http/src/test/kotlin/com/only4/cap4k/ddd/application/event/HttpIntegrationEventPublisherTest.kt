package com.only4.cap4k.ddd.application.event

import com.only4.cap4k.ddd.core.application.event.IntegrationEventEnvelope
import com.only4.cap4k.ddd.core.application.event.IntegrationEventEnvelopeCodec
import com.only4.cap4k.ddd.core.application.event.IntegrationEventPublisher
import com.only4.cap4k.ddd.core.application.event.IntegrationEventRouteResolver
import com.only4.cap4k.ddd.core.domain.event.EventRecord
import com.only4.cap4k.ddd.core.application.provider.RuntimeProviderState
import com.only4.cap4k.ddd.core.application.provider.RuntimeProviderStateReporter
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.net.URI
import java.time.Instant
import java.util.concurrent.Executor
import java.util.concurrent.RejectedExecutionException

@ExtendWith(MockKExtension::class)
@DisplayName("HTTP Integration Event publisher")
class HttpIntegrationEventPublisherTest {
    @MockK
    private lateinit var publishCallback: IntegrationEventPublisher.PublishCallback

    @BeforeEach
    fun setUp() {
        clearAllMocks()
        every { publishCallback.onSuccess(any()) } just runs
        every { publishCallback.onException(any(), any()) } just runs
    }

    @Test
    fun `route resolution failure reports one safe provider failure`() {
        val event = event("test-event", "user.created")
        val state = RecordingStateReporter()
        val publisher = publisher(
            routeResolver = IntegrationEventRouteResolver { error("missing route") },
            state = state,
            httpPoster = { _, _ -> error("HTTP must not be called") },
        )

        publisher.publish(event, envelope(event), publishCallback)

        assertFailure(event, HttpIntegrationEventFailureCategory.ROUTE)
        assertEquals(listOf("degraded:ROUTE"), state.transitions)
    }

    @Test
    fun `static route performs one canonical POST to the fixed endpoint`() {
        val event = event("test-event", "user.created")
        val envelope = envelope(event)
        val state = RecordingStateReporter()
        var postedUrl: URI? = null
        var postedBody: String? = null
        val publisher = publisher(
            routeResolver = IntegrationEventRouteResolver { URI("http://localhost:8080/context") },
            state = state,
            httpPoster = { url, body ->
                postedUrl = url
                postedBody = body
            },
        )

        publisher.publish(event, envelope, publishCallback)

        assertEquals(URI("http://localhost:8080/context/cap4k/integration-events"), postedUrl)
        assertEquals(envelope, IntegrationEventEnvelopeCodec().decode(requireNotNull(postedBody)))
        assertEquals(listOf("recovering:handoff-started", "healthy:handoff-succeeded"), state.transitions)
        verify(exactly = 1) { publishCallback.onSuccess(event) }
        verify(exactly = 0) { publishCallback.onException(any(), any()) }
    }

    @Test
    fun `HTTP handoff failure reports one safe terminal callback`() {
        val event = event("test-event", "user.created")
        val state = RecordingStateReporter()
        val publisher = publisher(
            routeResolver = IntegrationEventRouteResolver { URI("http://localhost:8080") },
            state = state,
            httpPoster = { _, _ -> error("business-secret") },
        )

        publisher.publish(event, envelope(event), publishCallback)

        val failure = assertFailure(event, HttpIntegrationEventFailureCategory.HANDOFF)
        assertEquals(false, failure.message.orEmpty().contains("business-secret"))
        assertEquals(listOf("recovering:handoff-started", "degraded:HANDOFF"), state.transitions)
    }

    @Test
    fun `executor rejection reports client execution failure without attempting HTTP`() {
        val event = event("test-event", "user.created")
        val state = RecordingStateReporter()
        val publisher = HttpIntegrationEventPublisher(
            routeResolver = IntegrationEventRouteResolver { URI("http://localhost:8080") },
            executorOverride = Executor { throw RejectedExecutionException("rejected") },
            providerState = state,
            httpPoster = { _, _ -> error("HTTP must not be called") },
        )

        publisher.publish(event, envelope(event), publishCallback)

        assertFailure(event, HttpIntegrationEventFailureCategory.CLIENT_EXECUTION)
        assertEquals(listOf("degraded:CLIENT_EXECUTION"), state.transitions)
    }

    private fun assertFailure(
        event: EventRecord,
        category: HttpIntegrationEventFailureCategory,
    ): HttpIntegrationEventHandoffException {
        val failure = slot<Throwable>()
        verify(exactly = 0) { publishCallback.onSuccess(any()) }
        verify(exactly = 1) { publishCallback.onException(event, capture(failure)) }
        return (failure.captured as HttpIntegrationEventHandoffException)
            .also { assertEquals(category, it.category) }
    }

    private fun publisher(
        routeResolver: IntegrationEventRouteResolver<URI>,
        state: RuntimeProviderStateReporter,
        httpPoster: (URI, String) -> Unit,
    ) = HttpIntegrationEventPublisher(
        routeResolver = routeResolver,
        threadPoolSize = 2,
        executorOverride = Executor { command -> command.run() },
        providerState = state,
        httpPoster = httpPoster,
    )

    private fun event(id: String, type: String): EventRecord = mockk(relaxed = true) {
        every { this@mockk.id } returns id
        every { this@mockk.type } returns type
        every { payload } returns mapOf("userId" to "123", "action" to "created")
        every { publishedAt } returns Instant.parse("2026-08-04T00:00:00Z")
    }

    private fun envelope(event: EventRecord): IntegrationEventEnvelope = IntegrationEventEnvelope(
        eventId = event.id,
        eventType = event.type,
        originService = "test-service",
        publishedAt = Instant.parse("2026-08-04T00:00:00Z"),
        deliveryAttempt = null,
        executionContext = emptyList(),
        payloadJson = "{\"action\":\"created\",\"userId\":\"123\"}",
    )

    private class RecordingStateReporter : RuntimeProviderStateReporter {
        override val providerId: String = "integration-event-transport.http"
        val transitions = mutableListOf<String>()

        override fun report(state: RuntimeProviderState, category: String?, observedAt: Instant) {
            transitions += "${state.name.lowercase()}:$category"
        }

        override fun close() = Unit
    }
}
package com.only4.cap4k.ddd.application.event

import com.only4.cap4k.ddd.core.application.event.IntegrationEventEnvelope
import com.only4.cap4k.ddd.core.application.event.IntegrationEventEnvelopeCodec
import com.only4.cap4k.ddd.core.application.event.IntegrationEventPublisher
import com.only4.cap4k.ddd.core.application.event.IntegrationEventRouteResolver
import com.only4.cap4k.ddd.core.domain.event.EventRecord
import io.mockk.*
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.time.Instant
import java.util.concurrent.Executor

@ExtendWith(MockKExtension::class)
@DisplayName("HTTP集成事件发布器测试")
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
    @DisplayName("路由解析失败时只报告一次发布失败")
    fun `should report route resolution failure exactly once`() {
        val eventRecord = createMockEventRecord("test-event", "user.created")
        val failure = IllegalStateException("missing route")
        val publisher = publisher(
            routeResolver = IntegrationEventRouteResolver { throw failure },
            httpPoster = { _, _ -> error("HTTP must not be called") },
        )

        publisher.publish(eventRecord, envelope(eventRecord), publishCallback)

        verify(exactly = 0) { publishCallback.onSuccess(any()) }
        verify(exactly = 1) { publishCallback.onException(eventRecord, failure) }
    }

    @Test
    @DisplayName("静态路由向固定端点执行一次 HTTP handoff")
    fun `should perform one HTTP handoff to fixed endpoint`() {
        val eventRecord = createMockEventRecord("test-event", "user.created")
        val envelope = envelope(eventRecord)
        var postedUrl: String? = null
        var postedBody: String? = null
        val publisher = publisher(
            routeResolver = IntegrationEventRouteResolver { "http://localhost:8080/" },
            httpPoster = { url, body ->
                postedUrl = url
                postedBody = body
            },
        )

        publisher.publish(eventRecord, envelope, publishCallback)

        assertEquals("http://localhost:8080/cap4k/integration-events", postedUrl)
        assertEquals(envelope, IntegrationEventEnvelopeCodec().decode(requireNotNull(postedBody)))
        verify(exactly = 1) { publishCallback.onSuccess(eventRecord) }
        verify(exactly = 0) { publishCallback.onException(any(), any()) }
    }

    @Test
    @DisplayName("HTTP handoff 失败时只报告一次发布失败")
    fun `should report HTTP handoff failure exactly once`() {
        val eventRecord = createMockEventRecord("test-event", "user.created")
        val failure = IllegalStateException("receiver unavailable")
        val publisher = publisher(
            routeResolver = IntegrationEventRouteResolver { "http://localhost:8080" },
            httpPoster = { _, _ -> throw failure },
        )

        publisher.publish(eventRecord, envelope(eventRecord), publishCallback)

        verify(exactly = 0) { publishCallback.onSuccess(any()) }
        verify(exactly = 1) { publishCallback.onException(eventRecord, failure) }
    }

    private fun publisher(
        routeResolver: IntegrationEventRouteResolver<String>,
        httpPoster: (String, String) -> Unit,
    ) = HttpIntegrationEventPublisher(
        routeResolver = routeResolver,
        threadPoolSize = 2,
        executorOverride = Executor { command -> command.run() },
        httpPoster = httpPoster,
    )

    private fun createMockEventRecord(id: String, type: String): EventRecord {
        return mockk<EventRecord>(relaxed = true) {
            every { this@mockk.id } returns id
            every { this@mockk.type } returns type
            every { payload } returns mapOf("userId" to "123", "action" to "created")
            every { publishedAt } returns Instant.parse("2026-08-04T00:00:00Z")
        }
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
}

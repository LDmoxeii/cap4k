package com.only4.cap4k.ddd.application.event

import com.only4.cap4k.ddd.core.application.event.IntegrationEventEnvelope
import com.only4.cap4k.ddd.core.application.event.IntegrationEventPublisher
import com.only4.cap4k.ddd.core.application.event.StaticIntegrationEventRouteResolver
import com.only4.cap4k.ddd.core.application.provider.RuntimeProviderState
import com.only4.cap4k.ddd.core.application.provider.RuntimeProviderStateReporter
import com.only4.cap4k.ddd.core.domain.event.EventRecord
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify
import org.apache.rocketmq.client.producer.SendCallback
import org.apache.rocketmq.client.producer.SendResult
import org.apache.rocketmq.client.producer.SendStatus
import org.apache.rocketmq.spring.core.RocketMQTemplate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.springframework.messaging.Message
import java.time.Instant

class RocketMqIntegrationEventPublisherTest {
    private val rocketMQTemplate = mockk<RocketMQTemplate>()
    private val publishCallback = mockk<IntegrationEventPublisher.PublishCallback>()
    private val eventRecord = mockk<EventRecord>()
    private lateinit var stateReporter: RecordingReporter
    private lateinit var publisher: RocketMqIntegrationEventPublisher

    @BeforeEach
    fun setup() {
        clearAllMocks()
        every { eventRecord.id } returns "event-123"
        every { eventRecord.type } returns EVENT_NAME
        every { publishCallback.onSuccess(eventRecord) } just runs
        every { publishCallback.onException(eventRecord, any()) } just runs
        stateReporter = RecordingReporter()
        publisher = RocketMqIntegrationEventPublisher(
            rocketMQTemplate,
            StaticIntegrationEventRouteResolver(
                mapOf(EVENT_NAME to RocketMqIntegrationEventRoute("content", "published")),
                "rocketmq",
            ),
            DELIVERY_TIMEOUT,
            stateReporter,
        ).apply { init() }
    }

    @Test
    fun `SEND_OK is the only positive handoff and uses explicit route with timeout`() {
        every {
            rocketMQTemplate.asyncSend(any<String>(), any<Message<Any>>(), any<SendCallback>(), any<Long>())
        } answers {
            thirdArg<SendCallback>().onSuccess(sendResult(SendStatus.SEND_OK))
        }

        publisher.publish(eventRecord, envelope(), publishCallback)

        verify(exactly = 1) {
            rocketMQTemplate.asyncSend("content:published", any<Message<Any>>(), any<SendCallback>(), DELIVERY_TIMEOUT)
        }
        verify(exactly = 1) { publishCallback.onSuccess(eventRecord) }
        verify(exactly = 0) { publishCallback.onException(any(), any()) }
        assertEquals(RuntimeProviderState.HEALTHY, stateReporter.lastState)
    }

    @ParameterizedTest
    @EnumSource(value = SendStatus::class, names = ["SEND_OK"], mode = EnumSource.Mode.EXCLUDE)
    fun `every non-positive SDK status is a provider failure`(status: SendStatus) {
        every {
            rocketMQTemplate.asyncSend(any<String>(), any<Message<Any>>(), any<SendCallback>(), any<Long>())
        } answers {
            thirdArg<SendCallback>().onSuccess(sendResult(status))
        }

        publisher.publish(eventRecord, envelope(), publishCallback)

        verify(exactly = 0) { publishCallback.onSuccess(any()) }
        verify(exactly = 1) { publishCallback.onException(eventRecord, any<RocketMqPublishResultException>()) }
        assertEquals(RuntimeProviderState.DEGRADED, stateReporter.lastState)
    }

    @Test
    fun `missing SDK result is terminal and late success cannot invert it`() {
        val callback = slot<SendCallback>()
        every {
            rocketMQTemplate.asyncSend(any<String>(), any<Message<Any>>(), capture(callback), any<Long>())
        } just runs

        publisher.publish(eventRecord, envelope(), publishCallback)
        callback.captured.onSuccess(null)
        callback.captured.onSuccess(sendResult(SendStatus.SEND_OK))

        verify(exactly = 1) { publishCallback.onException(eventRecord, any<RocketMqPublishResultException>()) }
        verify(exactly = 0) { publishCallback.onSuccess(any()) }
        assertEquals(RuntimeProviderState.DEGRADED, stateReporter.lastState)
    }

    @Test
    fun `asynchronous SDK failure remains retryable and degrades publisher component`() {
        val failure = IllegalStateException("secret broker response")
        every {
            rocketMQTemplate.asyncSend(any<String>(), any<Message<Any>>(), any<SendCallback>(), any<Long>())
        } answers {
            thirdArg<SendCallback>().onException(failure)
        }

        publisher.publish(eventRecord, envelope(), publishCallback)

        verify(exactly = 1) { publishCallback.onException(eventRecord, failure) }
        verify(exactly = 0) { publishCallback.onSuccess(any()) }
        assertEquals(RuntimeProviderState.DEGRADED, stateReporter.lastState)
    }

    @Test
    fun `synchronous SDK exception remains retryable and degrades publisher component`() {
        val failure = IllegalStateException("secret broker response")
        every {
            rocketMQTemplate.asyncSend(any<String>(), any<Message<Any>>(), any<SendCallback>(), any<Long>())
        } throws failure

        publisher.publish(eventRecord, envelope(), publishCallback)

        verify(exactly = 1) { publishCallback.onException(eventRecord, failure) }
        verify(exactly = 0) { publishCallback.onSuccess(any()) }
        assertEquals(RuntimeProviderState.DEGRADED, stateReporter.lastState)
    }

    @Test
    fun `publisher diagnostics expose only safe failure facts`() {
        val secret = "secret broker response and payload"
        every {
            rocketMQTemplate.asyncSend(any<String>(), any<Message<Any>>(), any<SendCallback>(), any<Long>())
        } throws IllegalStateException(secret)

        val logs = captureFormattedLogs(RocketMqIntegrationEventPublisher::class.java) {
            publisher.publish(eventRecord, envelope(), publishCallback)
        }.joinToString("\n")

        assertTrue(logs.contains("event-123"))
        assertTrue(logs.contains(EVENT_NAME))
        assertTrue(logs.contains(IllegalStateException::class.java.name))
        assertFalse(logs.contains(secret))
        assertFalse(logs.contains("content:published"))
        assertFalse(logs.contains("{\"value\":\"safe\"}"))
    }

    @Test
    fun `missing route fails before invoking RocketMQ and does not classify broker health`() {
        publisher = RocketMqIntegrationEventPublisher(
            rocketMQTemplate,
            StaticIntegrationEventRouteResolver(emptyMap(), "rocketmq"),
            DELIVERY_TIMEOUT,
            stateReporter,
        ).apply { init() }

        publisher.publish(eventRecord, envelope(), publishCallback)

        verify(exactly = 0) {
            rocketMQTemplate.asyncSend(any<String>(), any<Message<Any>>(), any<SendCallback>(), any<Long>())
        }
        verify(exactly = 1) { publishCallback.onException(eventRecord, any()) }
        assertEquals(RuntimeProviderState.RECOVERING, stateReporter.lastState)
    }

    @Test
    fun `publish result failure text does not expose route topology`() {
        val failure = RocketMqPublishResultException(SendStatus.FLUSH_DISK_TIMEOUT)

        assertFalse(failure.message.orEmpty().contains("content"))
        assertFalse(failure.message.orEmpty().contains("published"))
    }

    private fun sendResult(status: SendStatus): SendResult = mockk {
        every { sendStatus } returns status
    }

    private fun envelope(): IntegrationEventEnvelope = IntegrationEventEnvelope(
        eventId = "event-123",
        eventType = EVENT_NAME,
        originService = "content-service",
        publishedAt = Instant.parse("2026-08-09T00:00:00Z"),
        deliveryAttempt = null,
        executionContext = emptyList(),
        payloadJson = "{\"value\":\"safe\"}",
    )

    private class RecordingReporter : RuntimeProviderStateReporter {
        override val providerId: String = RocketMqIntegrationEventPublisher.PROVIDER_IDENTITY
        var lastState: RuntimeProviderState? = null

        override fun report(state: RuntimeProviderState, category: String?, observedAt: Instant) {
            lastState = state
        }

        override fun close() = Unit
    }

    private companion object {
        const val EVENT_NAME = "content.published"
        const val DELIVERY_TIMEOUT = 4_321L
    }
}

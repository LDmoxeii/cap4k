package com.only4.cap4k.ddd.application.event

import com.only4.cap4k.ddd.core.application.event.InMemoryRuntimeProviderStateRegistry
import com.only4.cap4k.ddd.core.application.event.IntegrationEventEnvelope
import com.only4.cap4k.ddd.core.application.event.IntegrationEventPublisher
import com.only4.cap4k.ddd.core.application.event.RuntimeProviderState
import com.only4.cap4k.ddd.core.application.event.StaticIntegrationEventRouteResolver
import com.only4.cap4k.ddd.core.domain.event.EventRecord
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.apache.rocketmq.client.producer.SendCallback
import org.apache.rocketmq.client.producer.SendResult
import org.apache.rocketmq.client.producer.SendStatus
import org.apache.rocketmq.spring.core.RocketMQTemplate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.springframework.messaging.Message

class RocketMqIntegrationEventPublisherTest {
    private val rocketMQTemplate = mockk<RocketMQTemplate>()
    private val publishCallback = mockk<IntegrationEventPublisher.PublishCallback>()
    private val eventRecord = mockk<EventRecord>()
    private lateinit var stateRegistry: InMemoryRuntimeProviderStateRegistry
    private lateinit var publisher: RocketMqIntegrationEventPublisher

    @BeforeEach
    fun setup() {
        clearAllMocks()
        every { eventRecord.id } returns "event-123"
        every { publishCallback.onSuccess(eventRecord) } just runs
        every { publishCallback.onException(eventRecord, any()) } just runs
        stateRegistry = InMemoryRuntimeProviderStateRegistry()
        publisher = RocketMqIntegrationEventPublisher(
            rocketMQTemplate,
            StaticIntegrationEventRouteResolver(
                mapOf(EVENT_NAME to RocketMqIntegrationEventRoute("content", "published")),
                "rocketmq",
            ),
            DELIVERY_TIMEOUT,
            stateRegistry,
        )
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
        assertEquals(
            RuntimeProviderState.HEALTHY,
            stateRegistry.state(RocketMqIntegrationEventPublisher.PROVIDER_IDENTITY)?.state,
        )
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
        assertEquals(
            RuntimeProviderState.DEGRADED,
            stateRegistry.state(RocketMqIntegrationEventPublisher.PROVIDER_IDENTITY)?.state,
        )
    }

    @Test
    fun `missing or malformed SDK result is a provider failure`() {
        val completion = com.only4.cap4k.ddd.core.application.event.IntegrationEventPublishCompletion(
            eventRecord,
            publishCallback,
        )
        val callback = RocketMqIntegrationEventPublisher.IntegrationEventSendCallback(
            eventRecord,
            RocketMqIntegrationEventRoute("content", "published"),
            completion,
            stateRegistry,
        )

        callback.onSuccess(null)
        callback.onSuccess(sendResult(SendStatus.SEND_OK))

        verify(exactly = 1) { publishCallback.onException(eventRecord, any<RocketMqPublishResultException>()) }
        verify(exactly = 0) { publishCallback.onSuccess(any()) }
        assertEquals(
            RuntimeProviderState.DEGRADED,
            stateRegistry.state(RocketMqIntegrationEventPublisher.PROVIDER_IDENTITY)?.state,
        )
    }

    @Test
    fun `synchronous SDK exception remains retryable and degrades provider`() {
        val failure = IllegalStateException("broker unavailable")
        every {
            rocketMQTemplate.asyncSend(any<String>(), any<Message<Any>>(), any<SendCallback>(), any<Long>())
        } throws failure

        publisher.publish(eventRecord, envelope(), publishCallback)

        verify(exactly = 1) { publishCallback.onException(eventRecord, failure) }
        verify(exactly = 0) { publishCallback.onSuccess(any()) }
        assertEquals(
            RuntimeProviderState.DEGRADED,
            stateRegistry.state(RocketMqIntegrationEventPublisher.PROVIDER_IDENTITY)?.state,
        )
    }

    @Test
    fun `late duplicate callback cannot invert completion or provider state`() {
        val failure = IllegalStateException("timeout")
        every {
            rocketMQTemplate.asyncSend(any<String>(), any<Message<Any>>(), any<SendCallback>(), any<Long>())
        } answers {
            thirdArg<SendCallback>().onException(failure)
            thirdArg<SendCallback>().onSuccess(sendResult(SendStatus.SEND_OK))
        }

        publisher.publish(eventRecord, envelope(), publishCallback)

        verify(exactly = 1) { publishCallback.onException(eventRecord, failure) }
        verify(exactly = 0) { publishCallback.onSuccess(any()) }
        assertEquals(
            RuntimeProviderState.DEGRADED,
            stateRegistry.state(RocketMqIntegrationEventPublisher.PROVIDER_IDENTITY)?.state,
        )
    }

    @Test
    fun `missing route fails before invoking RocketMQ and does not classify broker health`() {
        publisher = RocketMqIntegrationEventPublisher(
            rocketMQTemplate,
            StaticIntegrationEventRouteResolver(emptyMap(), "rocketmq"),
            DELIVERY_TIMEOUT,
            stateRegistry,
        )

        publisher.publish(eventRecord, envelope(), publishCallback)

        verify(exactly = 0) {
            rocketMQTemplate.asyncSend(any<String>(), any<Message<Any>>(), any<SendCallback>(), any<Long>())
        }
        verify(exactly = 1) { publishCallback.onException(eventRecord, any()) }
        assertEquals(
            RuntimeProviderState.RECOVERING,
            stateRegistry.state(RocketMqIntegrationEventPublisher.PROVIDER_IDENTITY)?.state,
        )
    }

    private fun sendResult(status: SendStatus): SendResult = mockk {
        every { sendStatus } returns status
        every { msgId } returns "msg-123"
    }

    private fun envelope(): IntegrationEventEnvelope = IntegrationEventEnvelope(
        eventId = "event-123",
        eventType = EVENT_NAME,
        originService = "content-service",
        publishedAt = java.time.Instant.parse("2026-08-09T00:00:00Z"),
        deliveryAttempt = null,
        executionContext = emptyList(),
        payloadJson = "{\"value\":\"safe\"}",
    )

    companion object {
        private const val EVENT_NAME = "content.published"
        private const val DELIVERY_TIMEOUT = 4_321L
    }
}

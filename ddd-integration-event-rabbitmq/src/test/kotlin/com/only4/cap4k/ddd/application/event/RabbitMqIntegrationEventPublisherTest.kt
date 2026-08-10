package com.only4.cap4k.ddd.application.event

import com.only4.cap4k.ddd.core.application.event.IntegrationEventEnvelope
import com.only4.cap4k.ddd.core.application.event.IntegrationEventPublisher
import com.only4.cap4k.ddd.core.application.event.IntegrationEventRouteNotFoundException
import com.only4.cap4k.ddd.core.application.event.IntegrationEventRouteResolver
import com.only4.cap4k.ddd.core.application.provider.RuntimeProviderStateReporter
import com.only4.cap4k.ddd.core.domain.event.EventRecord
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.amqp.core.Message
import org.springframework.amqp.core.MessagePostProcessor
import org.springframework.amqp.core.MessageProperties
import org.springframework.amqp.core.ReturnedMessage
import org.springframework.amqp.rabbit.connection.ConnectionFactory
import org.springframework.amqp.rabbit.connection.CorrelationData
import org.springframework.amqp.rabbit.core.RabbitTemplate
import java.time.Duration
import java.time.Instant
import java.util.Date
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit

class RabbitMqIntegrationEventPublisherTest {
    private val rabbitTemplate = mockk<RabbitTemplate>(relaxed = true)
    private val connectionFactory = mockk<ConnectionFactory>()
    private val publisherConnectionFactory = mockk<ConnectionFactory>()
    private val topologyManager = mockk<RabbitMqTopologyManager>(relaxed = true)
    private val stateReporter = mockk<RuntimeProviderStateReporter>(relaxed = true)
    private val route = RabbitMqIntegrationEventRoute("content", "published")

    @BeforeEach
    fun setUp() {
        every { connectionFactory.publisherConnectionFactory } returns publisherConnectionFactory
        every { publisherConnectionFactory.isPublisherConfirms } returns true
        every { publisherConnectionFactory.isSimplePublisherConfirms } returns false
        every { publisherConnectionFactory.isPublisherReturns } returns true
    }

    @AfterEach
    fun tearDown() = clearAllMocks()

    @Test
    fun `initialization requires correlated confirms and publisher returns`() {
        every { publisherConnectionFactory.isPublisherConfirms } returns false
        assertThrows<IllegalStateException> { publisher().init() }

        every { publisherConnectionFactory.isPublisherConfirms } returns true
        every { publisherConnectionFactory.isSimplePublisherConfirms } returns true
        assertThrows<IllegalStateException> { publisher().init() }

        every { publisherConnectionFactory.isSimplePublisherConfirms } returns false
        every { publisherConnectionFactory.isPublisherReturns } returns false
        assertThrows<IllegalStateException> { publisher().init() }

        every { publisherConnectionFactory.isPublisherReturns } returns true
        publisher().init()
        verify(exactly = 1) { rabbitTemplate.setMandatory(true) }
    }

    @Test
    fun `positive confirm without return completes provider handoff`() {
        val callback = mockk<IntegrationEventPublisher.PublishCallback>(relaxed = true)
        val event = eventRecord()
        val correlation = slot<CorrelationData>()
        every { rabbitTemplate.convertAndSend(any<String>(), any<String>(), any<String>(), any<MessagePostProcessor>(), capture(correlation)) } answers {
            correlation.captured.future.complete(CorrelationData.Confirm(true, null))
        }

        publisher().apply { init() }.publish(event, envelope(event), callback)

        verify(exactly = 1) { rabbitTemplate.convertAndSend("content", "published", any<String>(), any<MessagePostProcessor>(), any<CorrelationData>()) }
        verify(exactly = 1) { callback.onSuccess(event) }
        verify(exactly = 0) { callback.onException(any(), any()) }
    }

    @Test
    fun `negative confirm fails provider handoff`() {
        val callback = mockk<IntegrationEventPublisher.PublishCallback>(relaxed = true)
        val event = eventRecord()
        val correlation = slot<CorrelationData>()
        every { rabbitTemplate.convertAndSend(any<String>(), any<String>(), any<String>(), any<MessagePostProcessor>(), capture(correlation)) } answers {
            correlation.captured.future.complete(CorrelationData.Confirm(false, "broker-nack"))
        }

        publisher().apply { init() }.publish(event, envelope(event), callback)

        verify(exactly = 0) { callback.onSuccess(any()) }
        verify(exactly = 1) { callback.onException(event, any()) }
    }

    @Test
    fun `mandatory return fails even when confirm acknowledges`() {
        val callback = mockk<IntegrationEventPublisher.PublishCallback>(relaxed = true)
        val event = eventRecord()
        val correlation = slot<CorrelationData>()
        every { rabbitTemplate.convertAndSend(any<String>(), any<String>(), any<String>(), any<MessagePostProcessor>(), capture(correlation)) } answers {
            correlation.captured.returned = ReturnedMessage(
                Message(ByteArray(0), MessageProperties()),
                312,
                "NO_ROUTE",
                "content",
                "published",
            )
            correlation.captured.future.complete(CorrelationData.Confirm(true, null))
        }

        publisher().apply { init() }.publish(event, envelope(event), callback)

        verify(exactly = 0) { callback.onSuccess(any()) }
        verify(exactly = 1) { callback.onException(event, any()) }
    }

    @Test
    fun `confirm timeout fails once and ignores a late acknowledgement`() {
        val callback = mockk<IntegrationEventPublisher.PublishCallback>(relaxed = true)
        val event = eventRecord()
        val correlation = slot<CorrelationData>()
        every { rabbitTemplate.convertAndSend(any<String>(), any<String>(), any<String>(), any<MessagePostProcessor>(), capture(correlation)) } just runs

        publisher(Duration.ofMillis(5)).apply { init() }.publish(event, envelope(event), callback)
        correlation.captured.future.complete(CorrelationData.Confirm(true, null))

        verify(exactly = 0) { callback.onSuccess(any()) }
        verify(exactly = 1) { callback.onException(event, any()) }
    }

    @Test
    fun `synchronous send failure fails once`() {
        val callback = mockk<IntegrationEventPublisher.PublishCallback>(relaxed = true)
        val event = eventRecord()
        val failure = IllegalStateException("send failed")
        every { rabbitTemplate.convertAndSend(any<String>(), any<String>(), any<String>(), any<MessagePostProcessor>(), any<CorrelationData>()) } throws failure

        publisher().apply { init() }.publish(event, envelope(event), callback)

        verify(exactly = 0) { callback.onSuccess(any()) }
        verify(exactly = 1) { callback.onException(event, failure) }
    }

    @Test
    fun `exceptional confirm future fails provider handoff`() {
        val callback = mockk<IntegrationEventPublisher.PublishCallback>(relaxed = true)
        val event = eventRecord()
        val correlation = slot<CorrelationData>()
        every { rabbitTemplate.convertAndSend(any<String>(), any<String>(), any<String>(), any<MessagePostProcessor>(), capture(correlation)) } answers {
            correlation.captured.future.completeExceptionally(IllegalStateException("confirm failed"))
        }

        publisher().apply { init() }.publish(event, envelope(event), callback)

        verify(exactly = 0) { callback.onSuccess(any()) }
        verify(exactly = 1) { callback.onException(event, any()) }
    }

    @Test
    fun `executor rejection fails before Rabbit publish`() {
        val callback = mockk<IntegrationEventPublisher.PublishCallback>(relaxed = true)
        val event = eventRecord()
        val rejected = RejectedExecutionException("saturated")
        val executor = Executor { throw rejected }

        publisher(executorOverride = executor).apply { init() }.publish(event, envelope(event), callback)

        verify(exactly = 0) { rabbitTemplate.convertAndSend(any<String>(), any<String>(), any<String>(), any<MessagePostProcessor>(), any<CorrelationData>()) }
        verify(exactly = 1) { callback.onException(event, rejected) }
    }

    @Test
    fun `failure completion survives provider state reporting failure`() {
        val callback = mockk<IntegrationEventPublisher.PublishCallback>(relaxed = true)
        val event = eventRecord()
        val rejected = RejectedExecutionException("saturated")
        every {
            stateReporter.report(
                com.only4.cap4k.ddd.core.application.provider.RuntimeProviderState.DEGRADED,
                "publisher-executor-rejected",
                any(),
            )
        } throws IllegalStateException("reporter unavailable")

        assertDoesNotThrow {
            publisher(executorOverride = Executor { throw rejected })
                .apply { init() }
                .publish(event, envelope(event), callback)
        }

        verify(exactly = 1) { callback.onException(event, rejected) }
    }

    @Test
    fun `confirmed handoff survives provider state reporting failure`() {
        val callback = mockk<IntegrationEventPublisher.PublishCallback>(relaxed = true)
        val event = eventRecord()
        val correlation = slot<CorrelationData>()
        every { rabbitTemplate.convertAndSend(any<String>(), any<String>(), any<String>(), any<MessagePostProcessor>(), capture(correlation)) } answers {
            correlation.captured.future.complete(CorrelationData.Confirm(true, null))
        }
        every {
            stateReporter.report(
                com.only4.cap4k.ddd.core.application.provider.RuntimeProviderState.HEALTHY,
                "publisher-confirm-ack",
                any(),
            )
        } throws IllegalStateException("reporter unavailable")

        assertDoesNotThrow {
            publisher().apply { init() }.publish(event, envelope(event), callback)
        }

        verify(exactly = 1) { callback.onSuccess(event) }
        verify(exactly = 0) { callback.onException(any(), any()) }
    }

    @Test
    fun `close shuts down only the owned executor and remains idempotent`() {
        val callback = mockk<IntegrationEventPublisher.PublishCallback>(relaxed = true)
        val event = eventRecord()
        val completed = CountDownLatch(1)
        val correlation = slot<CorrelationData>()
        every { rabbitTemplate.convertAndSend(any<String>(), any<String>(), any<String>(), any<MessagePostProcessor>(), capture(correlation)) } answers {
            correlation.captured.future.complete(CorrelationData.Confirm(true, null))
        }
        every { callback.onSuccess(event) } answers { completed.countDown() }
        val publisher = publisher(executorOverride = null).apply { init() }

        publisher.publish(event, envelope(event), callback)
        assertTrue(completed.await(1, TimeUnit.SECONDS))
        val ownedExecutor = ownedExecutor(publisher)

        publisher.close()
        publisher.close()

        assertTrue(ownedExecutor.isShutdown)
        verify(exactly = 1) { callback.onSuccess(event) }
    }

    @Test
    fun `close does not initialize an unused owned executor and rejects later publishes`() {
        val callback = mockk<IntegrationEventPublisher.PublishCallback>(relaxed = true)
        val event = eventRecord()
        val publisher = publisher(executorOverride = null).apply { init() }
        val ownedExecutor = ownedExecutorLazy(publisher)
        assertFalse(ownedExecutor.isInitialized())

        publisher.close()
        publisher.close()
        publisher.publish(event, envelope(event), callback)

        assertFalse(ownedExecutor.isInitialized())
        verify(exactly = 0) { rabbitTemplate.convertAndSend(any<String>(), any<String>(), any<String>(), any<MessagePostProcessor>(), any<CorrelationData>()) }
        verify(exactly = 1) { callback.onException(event, any<RejectedExecutionException>()) }
    }

    @Test
    fun `close never shuts down an externally supplied executor`() {
        val callback = mockk<IntegrationEventPublisher.PublishCallback>(relaxed = true)
        val event = eventRecord()
        val externalExecutor = Executors.newSingleThreadExecutor()
        try {
            val publisher = publisher(executorOverride = externalExecutor).apply { init() }

            publisher.close()
            publisher.close()

            val accepted = CountDownLatch(1)
            externalExecutor.execute { accepted.countDown() }
            assertTrue(accepted.await(1, TimeUnit.SECONDS))
            assertFalse(externalExecutor.isShutdown)

            publisher.publish(event, envelope(event), callback)
            verify(exactly = 0) { rabbitTemplate.convertAndSend(any<String>(), any<String>(), any<String>(), any<MessagePostProcessor>(), any<CorrelationData>()) }
            verify(exactly = 1) { callback.onException(event, any<RejectedExecutionException>()) }
        } finally {
            externalExecutor.shutdownNow()
        }
    }

    @Test
    fun `missing explicit route fails before Rabbit publish`() {
        val callback = mockk<IntegrationEventPublisher.PublishCallback>(relaxed = true)
        val event = eventRecord()
        val missing = IntegrationEventRouteNotFoundException("rabbitmq", event.type)
        val publisher = publisher(routeResolver = IntegrationEventRouteResolver { throw missing })

        publisher.apply { init() }.publish(event, envelope(event), callback)

        verify(exactly = 0) { rabbitTemplate.convertAndSend(any<String>(), any<String>(), any<String>(), any<MessagePostProcessor>(), any<CorrelationData>()) }
        verify(exactly = 1) { callback.onException(event, missing) }
    }

    @Test
    fun `message post processor writes stable event metadata only`() {
        val event = eventRecord()
        val properties = MessageProperties()
        val message = Message(ByteArray(0), properties)

        assertEquals(message, RabbitMqIntegrationEventPublisher.IntegrationEventMessagePostProcessor(event).postProcessMessage(message))
        assertEquals(event.id, properties.messageId)
        assertEquals(Date.from(event.publishedAt), properties.timestamp)
    }

    private fun publisher(
        timeout: Duration = Duration.ofSeconds(1),
        routeResolver: IntegrationEventRouteResolver<RabbitMqIntegrationEventRoute> = IntegrationEventRouteResolver { route },
        executorOverride: Executor? = Executor(Runnable::run),
    ): RabbitMqIntegrationEventPublisher = RabbitMqIntegrationEventPublisher(
        rabbitTemplate = rabbitTemplate,
        connectionFactory = connectionFactory,
        routeResolver = routeResolver,
        topologyManager = topologyManager,
        stateReporter = stateReporter,
        threadPoolSize = 1,
        confirmTimeout = timeout,
        executorOverride = executorOverride,
    )

    private fun ownedExecutor(publisher: RabbitMqIntegrationEventPublisher): ExecutorService =
        ownedExecutorLazy(publisher).value

    @Suppress("UNCHECKED_CAST")
    private fun ownedExecutorLazy(publisher: RabbitMqIntegrationEventPublisher): Lazy<ExecutorService> =
        publisher.javaClass.getDeclaredField("ownedExecutor")
            .apply { isAccessible = true }
            .get(publisher) as Lazy<ExecutorService>

    private fun eventRecord(): EventRecord = mockk {
        every { id } returns "event-1"
        every { type } returns "content.published"
        every { publishedAt } returns Instant.parse("2026-08-10T00:00:00Z")
    }

    private fun envelope(event: EventRecord): IntegrationEventEnvelope = IntegrationEventEnvelope(
        eventId = event.id,
        eventType = event.type,
        originService = "content-service",
        publishedAt = event.publishedAt,
        deliveryAttempt = 1,
        executionContext = emptyList(),
        payloadJson = "{\"id\":\"content-1\"}",
    )
}

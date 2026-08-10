package com.only4.cap4k.ddd.application.event

import com.only4.cap4k.ddd.core.application.provider.RuntimeProviderState
import com.only4.cap4k.ddd.core.application.provider.RuntimeProviderStateReporter
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.amqp.AmqpConnectException
import org.springframework.amqp.AmqpException
import org.springframework.amqp.core.AmqpAdmin
import org.springframework.amqp.core.Binding
import org.springframework.amqp.core.DirectExchange
import org.springframework.amqp.core.Queue
import java.net.ConnectException

class RabbitMqTopologyManagerTest {
    private val admin = mockk<AmqpAdmin>(relaxed = true)
    private val reporter = RecordingReporter()
    private val route = RabbitMqIntegrationEventRoute("content", "published")

    @AfterEach
    fun tearDown() = clearAllMocks()

    @Test
    fun `declares durable exchange queue and binding in order`() {
        val manager = RabbitMqTopologyManager(admin, "direct", reporter)

        manager.register(route, "cap4k.media.content.hash")

        verifyOrder {
            admin.declareExchange(match<DirectExchange> { it.name == "content" && it.isDurable && !it.isAutoDelete })
            admin.declareQueue(match<Queue> { it.name == "cap4k.media.content.hash" && it.isDurable && !it.isExclusive && !it.isAutoDelete })
            admin.declareBinding(match<Binding> { it.exchange == "content" && it.routingKey == "published" })
        }
        assertEquals(RuntimeProviderState.HEALTHY, reporter.lastState)
    }

    @Test
    fun `temporary connection failure degrades without turning startup into static failure`() {
        every { admin.declareExchange(any()) } throws AmqpConnectException(ConnectException("offline"))
        val manager = RabbitMqTopologyManager(admin, "topic", reporter)

        assertDoesNotThrow { manager.register(route, "queue") }
        assertEquals(RuntimeProviderState.DEGRADED, reporter.lastState)
        assertEquals("topology-unavailable", reporter.lastCategory)
    }

    @Test
    fun `deterministic declaration failure is never swallowed as temporary unavailability`() {
        val failure = AmqpException("PRECONDITION_FAILED")
        every { admin.declareExchange(any()) } throws failure
        val manager = RabbitMqTopologyManager(admin, "direct", reporter)

        assertEquals(failure, assertThrows<AmqpException> { manager.register(route, "queue") })
        verify(exactly = 0) { admin.declareQueue(any()) }
    }

    @Test
    fun `contradictory queue registration and unsupported exchange type fail deterministically`() {
        val manager = RabbitMqTopologyManager(admin, "direct", reporter)
        manager.register(route, "queue")

        assertThrows<IllegalStateException> {
            manager.register(RabbitMqIntegrationEventRoute("other", "key"), "queue")
        }
        assertThrows<IllegalArgumentException> {
            RabbitMqTopologyManager(admin, "headers", reporter)
        }
    }

    @Test
    fun `route rejects invalid AMQP short strings before broker access`() {
        assertThrows<IllegalArgumentException> { RabbitMqIntegrationEventRoute("x".repeat(256), "key") }
        assertThrows<IllegalArgumentException> { RabbitMqIntegrationEventRoute("exchange", "y".repeat(256)) }
        assertThrows<IllegalArgumentException> { RabbitMqIntegrationEventRoute("bad\u0000exchange", "key") }
        verify(exactly = 0) { admin.declareExchange(any()) }
    }

    private class RecordingReporter : RuntimeProviderStateReporter {
        override val providerId: String = "integration-event-transport.rabbitmq"
        var lastState: RuntimeProviderState? = null
        var lastCategory: String? = null

        override fun report(state: RuntimeProviderState, category: String?, observedAt: java.time.Instant) {
            lastState = state
            lastCategory = category
        }

        override fun close() = Unit
    }
}

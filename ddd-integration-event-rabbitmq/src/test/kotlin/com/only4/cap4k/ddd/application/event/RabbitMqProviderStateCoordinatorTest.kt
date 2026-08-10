package com.only4.cap4k.ddd.application.event

import com.only4.cap4k.ddd.core.application.provider.RuntimeProviderState
import com.only4.cap4k.ddd.core.application.provider.RuntimeProviderStateReporter
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant

class RabbitMqProviderStateCoordinatorTest {
    @Test
    fun `healthy evidence from one component never masks another degraded component`() {
        val delegate = RecordingReporter()
        val coordinator = RabbitMqProviderStateCoordinator(delegate)

        coordinator.publisher.report(RuntimeProviderState.HEALTHY, "confirm-ack")
        coordinator.topology.report(RuntimeProviderState.HEALTHY, "declared")
        coordinator.subscriber.report(RuntimeProviderState.DEGRADED, "connection-failed")
        coordinator.publisher.report(RuntimeProviderState.HEALTHY, "confirm-ack-again")

        assertEquals(RuntimeProviderState.DEGRADED, delegate.lastState)
        assertEquals("subscriber:connection-failed", delegate.lastCategory)

        coordinator.subscriber.report(RuntimeProviderState.HEALTHY, "connection-ready")
        assertEquals(RuntimeProviderState.HEALTHY, delegate.lastState)
    }

    private class RecordingReporter : RuntimeProviderStateReporter {
        override val providerId: String = "integration-event-transport.rabbitmq"
        var lastState: RuntimeProviderState? = null
        var lastCategory: String? = null

        override fun report(state: RuntimeProviderState, category: String?, observedAt: Instant) {
            lastState = state
            lastCategory = category
        }

        override fun close() = Unit
    }
}

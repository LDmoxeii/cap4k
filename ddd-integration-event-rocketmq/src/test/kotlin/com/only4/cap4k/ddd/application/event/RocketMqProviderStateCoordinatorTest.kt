package com.only4.cap4k.ddd.application.event

import com.only4.cap4k.ddd.core.application.provider.RuntimeProviderState
import com.only4.cap4k.ddd.core.application.provider.RuntimeProviderStateReporter
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant

class RocketMqProviderStateCoordinatorTest {
    @Test
    fun `healthy subscriber cannot mask degraded publisher`() {
        val delegate = RecordingReporter()
        val coordinator = RocketMqProviderStateCoordinator(delegate)

        coordinator.publisher.report(RuntimeProviderState.DEGRADED, "send-exception")
        coordinator.subscriber.report(RuntimeProviderState.HEALTHY, "consumer-enrollment")

        assertEquals(RuntimeProviderState.DEGRADED, delegate.lastState)
        assertEquals("publisher:send-exception", delegate.lastCategory)
    }

    @Test
    fun `aggregate is healthy only after both components are healthy`() {
        val delegate = RecordingReporter()
        val coordinator = RocketMqProviderStateCoordinator(delegate)

        coordinator.publisher.report(RuntimeProviderState.HEALTHY, "publisher-confirm-ack")
        assertEquals(RuntimeProviderState.RECOVERING, delegate.lastState)

        coordinator.subscriber.report(RuntimeProviderState.HEALTHY, "consumer-enrollment")

        assertEquals(RuntimeProviderState.HEALTHY, delegate.lastState)
    }

    @Test
    fun `recovering component remains visible until it becomes healthy`() {
        val delegate = RecordingReporter()
        val coordinator = RocketMqProviderStateCoordinator(delegate)

        coordinator.publisher.report(RuntimeProviderState.HEALTHY, "publisher-confirm-ack")
        coordinator.subscriber.report(RuntimeProviderState.RECOVERING, "consumer-recovery-pending")

        assertEquals(RuntimeProviderState.RECOVERING, delegate.lastState)
        assertEquals("subscriber:consumer-recovery-pending", delegate.lastCategory)
    }

    @Test
    fun `unchanged aggregate state is not reported repeatedly`() {
        val delegate = RecordingReporter()
        val coordinator = RocketMqProviderStateCoordinator(delegate)
        val initialReports = delegate.reportCount

        coordinator.publisher.report(RuntimeProviderState.HEALTHY, "publisher-confirm-ack")
        coordinator.subscriber.report(RuntimeProviderState.HEALTHY, "consumer-enrollment")
        val healthyReports = delegate.reportCount
        coordinator.subscriber.report(RuntimeProviderState.HEALTHY, "consumer-enrollment")

        assertEquals(initialReports + 2, healthyReports)
        assertEquals(healthyReports, delegate.reportCount)
    }

    private class RecordingReporter : RuntimeProviderStateReporter {
        override val providerId: String = RocketMqIntegrationEventPublisher.PROVIDER_IDENTITY
        var lastState: RuntimeProviderState? = null
        var lastCategory: String? = null
        var reportCount: Int = 0

        override fun report(state: RuntimeProviderState, category: String?, observedAt: Instant) {
            lastState = state
            lastCategory = category
            reportCount += 1
        }

        override fun close() = Unit
    }
}

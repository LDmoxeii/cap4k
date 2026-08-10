package com.only4.cap4k.ddd.application.event

import com.only4.cap4k.ddd.core.application.event.IntegrationEventRouteResolver
import com.only4.cap4k.ddd.core.application.event.annotation.IntegrationEvent
import com.only4.cap4k.ddd.core.application.provider.RuntimeProviderState
import com.only4.cap4k.ddd.core.application.provider.RuntimeProviderStateReporter
import com.only4.cap4k.ddd.core.domain.event.EventHandlerDispatcher
import com.only4.cap4k.ddd.core.domain.event.InboundIntegrationEventRegistrationView
import com.only4.cap4k.ddd.core.domain.event.ReliableEventDeliveryContext
import com.only4.cap4k.ddd.core.domain.event.ReliableEventDeliveryContextScopeManager
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer
import org.apache.rocketmq.client.exception.MQClientException
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.time.Duration
import java.time.Instant

class RocketMqIntegrationEventSubscriberRecoveryTest {
    @Test
    fun `initial temporary failure recovers with one fresh consumer and healthy consumer is not duplicated`() {
        val first = mockk<DefaultMQPushConsumer>(relaxed = true)
        val replacement = mockk<DefaultMQPushConsumer>(relaxed = true)
        every { first.start() } throws MQClientException("offline", java.net.ConnectException("secret endpoint"))
        every { replacement.start() } just runs
        val factory = RecordingConsumerFactory(first, replacement)
        val scheduler = ManualRecoveryScheduler()
        val reporter = RecordingReporter()
        val adapter = adapter(factory, scheduler, reporter)

        assertDoesNotThrow { adapter.init() }
        assertEquals(1, factory.created.size)
        assertEquals(RuntimeProviderState.RECOVERING, reporter.lastState)
        verify(exactly = 1) { first.shutdown() }

        scheduler.runNext()

        assertEquals(2, factory.created.size)
        verify(exactly = 1) { replacement.start() }
        assertEquals(RuntimeProviderState.HEALTHY, reporter.lastState)

        scheduler.runNextOrNull()
        assertEquals(2, factory.created.size)
        verify(exactly = 1) { replacement.start() }
    }

    @Test
    fun `shutdown is terminal idempotent and prevents pending recovery`() {
        val failed = mockk<DefaultMQPushConsumer>(relaxed = true)
        every { failed.start() } throws MQClientException("offline", java.net.ConnectException("secret endpoint"))
        val factory = RecordingConsumerFactory(failed)
        val scheduler = ManualRecoveryScheduler()
        val adapter = adapter(factory, scheduler, RecordingReporter())

        adapter.init()
        adapter.shutdown()
        adapter.shutdown()
        scheduler.runNextOrNull()

        assertEquals(1, factory.created.size)
        assertEquals(1, scheduler.cancelCount)
        assertEquals(1, scheduler.closeCount)
        verify(exactly = 1) { failed.shutdown() }
    }

    @Test
    fun `shutdown before init creates no consumer`() {
        val factory = RecordingConsumerFactory(mockk(relaxed = true))
        val scheduler = ManualRecoveryScheduler()
        val adapter = adapter(factory, scheduler, RecordingReporter())

        adapter.shutdown()

        assertEquals(0, factory.created.size)
        assertThrows<IllegalStateException> { adapter.init() }
    }

    @Test
    fun `deterministic consumer startup failure is not retried`() {
        val consumer = mockk<DefaultMQPushConsumer>(relaxed = true)
        val failure = IllegalStateException("invalid client state")
        every { consumer.start() } throws failure
        val scheduler = ManualRecoveryScheduler()

        val actual = assertThrows<IllegalStateException> {
            adapter(RecordingConsumerFactory(consumer), scheduler, RecordingReporter()).init()
        }

        assertEquals(failure, actual)
        assertEquals(0, scheduler.pendingCount)
        verify(exactly = 1) { consumer.shutdown() }
    }

    @Test
    fun `deterministic failure closes every consumer activated earlier in the enrollment batch`() {
        val active = mockk<DefaultMQPushConsumer>(relaxed = true)
        val rejected = mockk<DefaultMQPushConsumer>(relaxed = true)
        every { active.start() } just runs
        every { rejected.start() } throws IllegalStateException("invalid client state")
        val scheduler = ManualRecoveryScheduler()
        val adapter = adapter(
            factory = RecordingConsumerFactory(active, rejected),
            scheduler = scheduler,
            reporter = RecordingReporter(),
            eventTypeCatalog = TwoEventCatalog,
        )

        assertThrows<IllegalStateException> { adapter.init() }

        verify(exactly = 1) { active.shutdown() }
        verify(exactly = 1) { rejected.shutdown() }
        assertEquals(0, scheduler.pendingCount)
        assertEquals(1, scheduler.closeCount)
        assertThrows<IllegalStateException> { adapter.init() }
    }

    @Test
    fun `recovery interval must be positive`() {
        val adapter = adapter(
            factory = RecordingConsumerFactory(mockk(relaxed = true)),
            scheduler = ManualRecoveryScheduler(),
            reporter = RecordingReporter(),
            recoveryInterval = Duration.ZERO,
        )

        assertThrows<IllegalArgumentException> { adapter.init() }
    }

    @Test
    fun `consumer finishing start after shutdown is immediately released`() {
        val consumer = mockk<DefaultMQPushConsumer>(relaxed = true)
        val enteredStart = CountDownLatch(1)
        val releaseStart = CountDownLatch(1)
        every { consumer.start() } answers {
            enteredStart.countDown()
            check(releaseStart.await(5, TimeUnit.SECONDS))
        }
        val adapter = adapter(
            RecordingConsumerFactory(consumer),
            ManualRecoveryScheduler(),
            RecordingReporter(),
        )
        val initThread = Thread { adapter.init() }

        initThread.start()
        check(enteredStart.await(5, TimeUnit.SECONDS))
        adapter.shutdown()
        releaseStart.countDown()
        initThread.join(5_000)

        verify(exactly = 1) { consumer.shutdown() }
        assertEquals(false, initThread.isAlive)
    }

    @Test
    fun `deterministic failure during recovery degrades and stops retrying`() {
        val first = mockk<DefaultMQPushConsumer>(relaxed = true)
        val second = mockk<DefaultMQPushConsumer>(relaxed = true)
        every { first.start() } throws MQClientException("offline", java.net.ConnectException("secret endpoint"))
        every { second.start() } throws IllegalArgumentException("secret topology")
        val scheduler = ManualRecoveryScheduler()
        val reporter = RecordingReporter()
        val adapter = adapter(RecordingConsumerFactory(first, second), scheduler, reporter)

        adapter.init()
        val logs = captureFormattedLogs(RocketMqIntegrationEventSubscriberAdapter::class.java) {
            scheduler.runNext()
        }.joinToString("\n")

        assertEquals(RuntimeProviderState.DEGRADED, reporter.lastState)
        assertEquals(0, scheduler.pendingCount)
        verify(exactly = 1) { second.shutdown() }
        assertFalse(logs.contains("secret topology"))
        assertFalse(logs.contains("secret-topic"))
        assertFalse(logs.contains("secret-tag"))
        assertFalse(logs.contains("secret-name-server"))
    }

    private fun adapter(
        factory: RocketMqConsumerFactory,
        scheduler: RocketMqRecoveryScheduler,
        reporter: RuntimeProviderStateReporter,
        recoveryInterval: Duration = Duration.ofMillis(10),
        eventTypeCatalog: InboundIntegrationEventRegistrationView = SingleEventCatalog,
    ) = RocketMqIntegrationEventSubscriberAdapter(
        eventHandlerDispatcher = mockk<EventHandlerDispatcher>(relaxed = true),
        eventMessageInterceptors = emptyList(),
        routeResolver = IntegrationEventRouteResolver { RocketMqIntegrationEventRoute("secret-topic", "secret-tag") },
        consumerGroupResolver = RocketMqConsumerGroupResolver(),
        eventTypeCatalog = eventTypeCatalog,
        applicationName = "content-worker",
        defaultNameSrv = "secret-name-server",
        msgCharset = "UTF-8",
        stateReporter = reporter,
        recoveryInterval = recoveryInterval,
        reliableEventDeliveryContextScopeManager = object : ReliableEventDeliveryContextScopeManager {
            override fun install(context: ReliableEventDeliveryContext) = AutoCloseable { }
            override fun suppress() = AutoCloseable { }
        },
        consumerFactory = factory,
        recoveryScheduler = scheduler,
    )

    private class RecordingConsumerFactory(
        vararg consumers: DefaultMQPushConsumer,
    ) : RocketMqConsumerFactory {
        private val consumers = ArrayDeque(consumers.toList())
        val created = mutableListOf<RocketMqConsumerSubscription>()

        override fun create(subscription: RocketMqConsumerSubscription): DefaultMQPushConsumer {
            created += subscription
            return consumers.removeFirst()
        }
    }

    private class ManualRecoveryScheduler : RocketMqRecoveryScheduler {
        private data class Scheduled(var cancelled: Boolean, val task: () -> Unit)

        private val scheduled = mutableListOf<Scheduled>()
        var cancelCount = 0
        var closeCount = 0
        val pendingCount: Int get() = scheduled.count { !it.cancelled }

        override fun schedule(delay: Duration, task: () -> Unit): AutoCloseable {
            val item = Scheduled(false, task)
            scheduled += item
            return AutoCloseable {
                if (!item.cancelled) {
                    item.cancelled = true
                    cancelCount += 1
                }
            }
        }

        fun runNext() {
            val next = scheduled.first { !it.cancelled }
            scheduled.remove(next)
            next.task()
        }

        fun runNextOrNull() {
            val next = scheduled.firstOrNull { !it.cancelled } ?: return
            scheduled.remove(next)
            next.task()
        }

        override fun close() {
            closeCount += 1
        }
    }

    private class RecordingReporter : RuntimeProviderStateReporter {
        override val providerId = RocketMqIntegrationEventPublisher.PROVIDER_IDENTITY
        var lastState: RuntimeProviderState? = null

        override fun report(state: RuntimeProviderState, category: String?, observedAt: Instant) {
            lastState = state
        }

        override fun close() = Unit
    }

    private object SingleEventCatalog : InboundIntegrationEventRegistrationView {
        override fun integrationEventTypes(): Set<Class<*>> = setOf(RecoverableEvent::class.java)
    }

    private object TwoEventCatalog : InboundIntegrationEventRegistrationView {
        override fun integrationEventTypes(): Set<Class<*>> = setOf(
            RecoverableEvent::class.java,
            SecondaryRecoverableEvent::class.java,
        )
    }

    @IntegrationEvent("content.published")
    private data class RecoverableEvent(val value: String)

    @IntegrationEvent("content.republished")
    private data class SecondaryRecoverableEvent(val value: String)
}

package com.only4.cap4k.ddd.core.application.event.impl

import com.only4.cap4k.ddd.core.application.event.IntegrationEventAttachedTransactionCommittedEvent
import com.only4.cap4k.ddd.core.application.event.IntegrationEventInterceptorManager
import com.only4.cap4k.ddd.core.application.event.annotation.IntegrationEvent
import com.only4.cap4k.ddd.core.application.invocation.InvocationKind
import com.only4.cap4k.ddd.core.application.invocation.InvocationScopeAccessor
import com.only4.cap4k.ddd.core.domain.event.EventRecord
import com.only4.cap4k.ddd.core.domain.event.EventRecordRepository
import com.only4.cap4k.ddd.core.domain.event.ReliableEventCoordinator
import com.only4.cap4k.ddd.core.share.DomainException
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.context.ApplicationEventPublisher
import java.time.Duration
import java.time.LocalDateTime

class DefaultIntegrationEventSupervisorTest {
    private val coordinator = mockk<ReliableEventCoordinator>(relaxed = true)
    private val repository = mockk<EventRecordRepository>()
    private val interceptors = mockk<IntegrationEventInterceptorManager>()
    private val applicationEvents = mockk<ApplicationEventPublisher>(relaxed = true)
    private val record = mockk<EventRecord>(relaxed = true)
    private var invocationKind: InvocationKind? = InvocationKind.COMMAND
    private lateinit var supervisor: DefaultIntegrationEventSupervisor

    @BeforeEach
    fun setUp() {
        every { repository.create() } returns record
        every { repository.save(any()) } just Runs
        every { interceptors.orderedIntegrationEventInterceptors } returns emptySet()
        every { interceptors.orderedEventInterceptors4IntegrationEvent } returns emptySet()
        supervisor = DefaultIntegrationEventSupervisor(
            coordinator,
            repository,
            interceptors,
            applicationEvents,
            "test-service",
            invocationScopeAccessor = InvocationScopeAccessor { invocationKind },
        )
        DefaultIntegrationEventSupervisor.reset()
    }

    @AfterEach
    fun tearDown() = DefaultIntegrationEventSupervisor.reset()

    @Test
    fun `enqueue creates one immediately due durable outbound event`() {
        val payload = TestIntegrationEvent("one")

        supervisor.enqueue(payload)
        supervisor.release()

        verify(exactly = 1) {
            record.init(
                payload,
                "test-service",
                any(),
                Duration.ofMinutes(1440),
                200,
                any(),
            )
        }
        verify { record.markPersist(true) }
        verify { repository.save(record) }
        verify {
            applicationEvents.publishEvent(
                match<IntegrationEventAttachedTransactionCommittedEvent> { it.events == listOf(record) },
            )
        }
    }

    @Test
    fun `schedule and delay preserve explicit due semantics`() {
        val scheduled = TestIntegrationEvent("scheduled")
        val delayed = TestIntegrationEvent("delayed")
        val dueAt = LocalDateTime.now().plusHours(1)

        supervisor.schedule(scheduled, dueAt)
        supervisor.delay(delayed, Duration.ofMinutes(5))
        supervisor.release()

        verify { record.init(scheduled, "test-service", dueAt, any(), any(), any()) }
        verify { record.init(delayed, "test-service", match { it.isAfter(LocalDateTime.now()) }, any(), any(), any()) }
        verify(exactly = 2) { repository.save(record) }
    }

    @Test
    fun `supplier remains lazy until release`() {
        var calls = 0
        supervisor.enqueue {
            calls += 1
            TestIntegrationEvent("lazy")
        }

        assertEquals(0, calls)
        supervisor.release()
        assertEquals(1, calls)
    }

    @Test
    fun `eager blank event name is rejected before repository creation`() {
        val failure = assertThrows(DomainException::class.java) {
            supervisor.enqueue(BlankNamedIntegrationEvent("eager"))
        }

        assertTrue(failure.message.orEmpty().contains("non-blank event name"))
        verify(exactly = 0) { repository.create() }
        verify(exactly = 0) { repository.save(any()) }
    }

    @Test
    fun `lazy blank event name is rejected on release before repository creation`() {
        var calls = 0
        supervisor.enqueue {
            calls += 1
            BlankNamedIntegrationEvent("lazy")
        }

        assertEquals(0, calls)
        val failure = assertThrows(DomainException::class.java) { supervisor.release() }

        assertEquals(1, calls)
        assertTrue(failure.message.orEmpty().contains("non-blank event name"))
        verify(exactly = 0) { repository.create() }
        verify(exactly = 0) { repository.save(any()) }
    }

    @Test
    fun `non integration payload and invalid invocation scope are rejected`() {
        assertThrows(DomainException::class.java) { supervisor.enqueue(RegularPayload("bad")) }

        invocationKind = InvocationKind.CAPABILITY
        assertThrows(IllegalStateException::class.java) { supervisor.enqueue(TestIntegrationEvent("blocked")) }
    }

    @Test
    fun `after commit callback only wakes coordinator`() {
        val committed = IntegrationEventAttachedTransactionCommittedEvent(this, listOf(record))

        assertDoesNotThrow { supervisor.onTransactionCommitted(committed) }

        verify(exactly = 1) { coordinator.wake() }
        verify(exactly = 0) { repository.save(any()) }
    }

    @IntegrationEvent("test.integration")
    data class TestIntegrationEvent(val value: String)

    @IntegrationEvent("   ")
    data class BlankNamedIntegrationEvent(val value: String)

    data class RegularPayload(val value: String)
}

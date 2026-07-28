package com.only4.cap4k.ddd.core.domain.event.impl

import com.only4.cap4k.ddd.core.CapabilityUnavailableException
import com.only4.cap4k.ddd.core.domain.event.DomainEventInterceptorManager
import com.only4.cap4k.ddd.core.domain.event.ReliableDomainEventProvider
import com.only4.cap4k.ddd.core.domain.event.annotation.DomainEvent
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.context.ApplicationEventPublisher
import java.time.LocalDateTime

class DefaultDomainEventSupervisorTest {
    private val interceptorManager = mockk<DomainEventInterceptorManager> {
        every { orderedDomainEventInterceptors } returns emptySet()
        every { orderedEventInterceptors4DomainEvent } returns emptySet()
    }
    private val applicationEventPublisher = mockk<ApplicationEventPublisher>()

    @AfterEach
    fun resetContext() = EventRuntimeContext.reset()

    @Test
    fun `immediate local event is published synchronously without an event repository`() {
        every { applicationEventPublisher.publishEvent(any<Any>()) } just runs
        val supervisor = DefaultDomainEventSupervisor(interceptorManager, applicationEventPublisher)
        val entity = Any()
        val event = LocalEvent("created")

        supervisor.attach(event, entity)
        supervisor.release(setOf(entity))

        verify(exactly = 1) { applicationEventPublisher.publishEvent(event) }
    }

    @Test
    fun `persistent event fails at release when reliable provider is absent`() {
        val supervisor = DefaultDomainEventSupervisor(interceptorManager, applicationEventPublisher)
        val entity = Any()
        supervisor.attach(PersistentEvent("created"), entity)

        val exception = assertThrows<CapabilityUnavailableException> { supervisor.release(setOf(entity)) }
        assertEquals("reliable-domain-events", exception.capability)
    }

    @Test
    fun `persistent event is routed to reliable provider and not local publisher`() {
        val reliableProvider = mockk<ReliableDomainEventProvider>()
        every { reliableProvider.publish(any(), any()) } just runs
        val supervisor = DefaultDomainEventSupervisor(
            interceptorManager,
            applicationEventPublisher,
            reliableProvider,
        )
        val entity = Any()
        val event = PersistentEvent("created")

        supervisor.attach(event, entity)
        supervisor.release(setOf(entity))

        verify(exactly = 1) { reliableProvider.publish(event, any()) }
        verify(exactly = 0) { applicationEventPublisher.publishEvent(any<Any>()) }
    }

    @Test
    fun `delayed event uses reliable provider`() {
        val reliableProvider = mockk<ReliableDomainEventProvider>()
        every { reliableProvider.publish(any(), any()) } just runs
        val supervisor = DefaultDomainEventSupervisor(
            interceptorManager,
            applicationEventPublisher,
            reliableProvider,
        )
        val entity = Any()
        val schedule = LocalDateTime.now().plusMinutes(1)
        val event = LocalEvent("later")

        supervisor.attach(event, entity, schedule)
        supervisor.release(setOf(entity))

        verify { reliableProvider.publish(event, schedule) }
    }

    data class LocalEvent(val value: String)

    @DomainEvent(persist = true)
    data class PersistentEvent(val value: String)
}

package com.only4.cap4k.ddd.core.domain.event.impl

import com.only4.cap4k.ddd.core.ProviderUnavailableException
import com.only4.cap4k.ddd.core.domain.event.DomainEventInterceptorManager
import com.only4.cap4k.ddd.core.domain.event.EventRuntimeContextManager
import com.only4.cap4k.ddd.core.domain.event.EventHandlerDispatcher
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
import java.time.LocalDateTime

class DefaultDomainEventSupervisorTest {
    private val interceptorManager = mockk<DomainEventInterceptorManager> {
        every { orderedDomainEventInterceptors } returns emptySet()
        every { orderedEventInterceptors4DomainEvent } returns emptySet()
    }
    private val eventHandlerDispatcher = mockk<EventHandlerDispatcher>()

    @AfterEach
    fun resetContext() = EventRuntimeContext.reset()

    @Test
    fun `immediate local event is published synchronously without an event repository`() {
        every { eventHandlerDispatcher.dispatch(any()) } just runs
        val supervisor = DefaultDomainEventSupervisor(interceptorManager, eventHandlerDispatcher)
        val entity = Any()
        val event = LocalEvent("created")

        supervisor.attach(event, entity)
        supervisor.release(setOf(entity))

        verify(exactly = 1) { eventHandlerDispatcher.dispatch(event) }
    }

    @Test
    fun `persistent event fails at release when reliable provider is absent`() {
        val supervisor = DefaultDomainEventSupervisor(interceptorManager, eventHandlerDispatcher)
        val entity = Any()
        supervisor.attach(PersistentEvent("created"), entity)

        val exception = assertThrows<ProviderUnavailableException> { supervisor.release(setOf(entity)) }
        assertEquals("reliable-domain-events", exception.providerName)
    }

    @Test
    fun `persistent event is routed to reliable provider and not local publisher`() {
        val reliableProvider = mockk<ReliableDomainEventProvider>()
        every { reliableProvider.publish(any(), any(), any()) } just runs
        val supervisor = DefaultDomainEventSupervisor(
            interceptorManager,
            eventHandlerDispatcher,
            reliableProvider,
        )
        val entity = Any()
        val event = PersistentEvent("created")

        supervisor.attach(event, entity)
        supervisor.release(setOf(entity))

        verify(exactly = 1) { reliableProvider.publish(event, any(), any()) }
        verify(exactly = 0) { eventHandlerDispatcher.dispatch(any()) }
    }

    @Test
    fun `delayed event uses reliable provider`() {
        val reliableProvider = mockk<ReliableDomainEventProvider>()
        every { reliableProvider.publish(any(), any(), any()) } just runs
        val supervisor = DefaultDomainEventSupervisor(
            interceptorManager,
            eventHandlerDispatcher,
            reliableProvider,
        )
        val entity = Any()
        val schedule = LocalDateTime.now().plusMinutes(1)
        val event = LocalEvent("later")

        supervisor.attach(event, entity, schedule)
        supervisor.release(setOf(entity))

        verify { reliableProvider.publish(event, schedule, any()) }
    }

    @Test
    fun `events produced by a handler remain for the next UoW frontier`() {
        val supervisor = DefaultDomainEventSupervisor(interceptorManager, eventHandlerDispatcher)
        val entity = Any()
        val first = LocalEvent("first")
        val derived = LocalEvent("derived")
        every { eventHandlerDispatcher.dispatch(first) } answers {
            supervisor.attach(derived, entity)
        }
        every { eventHandlerDispatcher.dispatch(derived) } just runs

        EventRuntimeContextManager.beginUnitOfWork()
        try {
            supervisor.attach(first, entity)

            supervisor.release(setOf(entity))

            assertEquals(1, supervisor.pendingCount())
            verify(exactly = 1) { eventHandlerDispatcher.dispatch(first) }
            verify(exactly = 0) { eventHandlerDispatcher.dispatch(derived) }

            supervisor.release(setOf(entity))

            assertEquals(0, supervisor.pendingCount())
            verify(exactly = 1) { eventHandlerDispatcher.dispatch(derived) }
        } finally {
            EventRuntimeContextManager.endUnitOfWork()
        }
    }

    @DomainEvent
    data class LocalEvent(val value: String)

    @DomainEvent(persist = true)
    data class PersistentEvent(val value: String)
}

package com.only4.cap4k.ddd.application

import com.only4.cap4k.ddd.core.application.event.IntegrationEventManager
import com.only4.cap4k.ddd.core.domain.event.DomainEventManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jakarta.persistence.EntityManager
import org.hibernate.Session
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class JpaUnitOfWorkTest {
    private val session = mockk<Session>()
    private val entityManager = mockk<EntityManager>(relaxed = true)

    @AfterEach
    fun cleanup() {
        JpaUnitOfWork.reset()
    }

    @Test
    fun `outer execute owns context and nested execute reuses it`() {
        val events = RecordingDomainEventManager()
        val unitOfWork = unitOfWork(events)

        val result = unitOfWork.execute {
            assertTrue(unitOfWork.active)
            unitOfWork.execute {
                assertTrue(unitOfWork.active)
                "nested"
            }
        }

        assertEquals("nested", result)
        assertFalse(unitOfWork.active)
    }

    @Test
    fun `explicit flush synchronizes persistence but does not drain events`() {
        val events = RecordingDomainEventManager(pending = 1)
        val integrationEvents = mockk<IntegrationEventManager>(relaxed = true)
        val unitOfWork = unitOfWork(events, integrationEvents)

        unitOfWork.execute {
            unitOfWork.flush()
            assertEquals(0, events.releaseCalls)
        }

        assertEquals(1, events.releaseCalls)
        verify(atLeast = 1) { integrationEvents.release() }
    }

    @Test
    fun `derived events are drained in later stabilization frontiers`() {
        val events = RecordingDomainEventManager(pending = 1, deriveOnce = true)
        val unitOfWork = unitOfWork(events)

        unitOfWork.execute { Unit }

        assertEquals(2, events.releaseCalls)
        assertEquals(0, events.pendingCount())
    }

    @Test
    fun `flush outside command Unit of Work fails`() {
        val unitOfWork = unitOfWork(RecordingDomainEventManager())

        assertThrows(IllegalStateException::class.java) {
            unitOfWork.flush()
        }
    }

    @Test
    fun `nested Command limit fails with phase and all transaction counters`() {
        val unitOfWork = unitOfWork(
            events = RecordingDomainEventManager(),
            limits = JpaUnitOfWorkLimits(maxNestedCommands = 0),
        )

        val error = assertThrows(IllegalStateException::class.java) {
            unitOfWork.execute {
                unitOfWork.execute { Unit }
            }
        }

        assertLimitDiagnostic(error, "nested Commands")
    }

    @Test
    fun `synchronous event limit fails before dispatching oversized frontier`() {
        val events = RecordingDomainEventManager(pending = 2)
        val unitOfWork = unitOfWork(
            events = events,
            limits = JpaUnitOfWorkLimits(maxSynchronousEvents = 1),
        )

        val error = assertThrows(IllegalStateException::class.java) {
            unitOfWork.execute { Unit }
        }

        assertEquals(0, events.releaseCalls)
        assertLimitDiagnostic(error, "synchronous Domain Events")
    }

    @Test
    fun `frontier round limit stops endlessly derived events`() {
        val unitOfWork = unitOfWork(
            events = RecordingDomainEventManager(pending = 1, deriveAlways = true),
            limits = JpaUnitOfWorkLimits(maxFrontierRounds = 1),
        )

        val error = assertThrows(IllegalStateException::class.java) {
            unitOfWork.execute { Unit }
        }

        assertLimitDiagnostic(error, "Domain Event frontier rounds")
    }

    @Test
    fun `provider flush limit fails before SQL synchronization`() {
        val unitOfWork = unitOfWork(
            events = RecordingDomainEventManager(),
            limits = JpaUnitOfWorkLimits(maxProviderFlushes = 0),
            dirty = true,
        )

        val error = assertThrows(IllegalStateException::class.java) {
            unitOfWork.execute { Unit }
        }

        assertLimitDiagnostic(error, "Provider flushes")
    }

    private fun assertLimitDiagnostic(error: IllegalStateException, limitName: String) {
        val message = error.message.orEmpty()
        assertTrue(message.contains(limitName))
        assertTrue(message.contains("phase="))
        assertTrue(message.contains("frontierRounds="))
        assertTrue(message.contains("synchronousEvents="))
        assertTrue(message.contains("nestedCommands="))
        assertTrue(message.contains("providerFlushes="))
        assertTrue(message.contains("causalPath="))
    }

    private fun unitOfWork(
        events: DomainEventManager,
        integrationEvents: IntegrationEventManager? = null,
        limits: JpaUnitOfWorkLimits = JpaUnitOfWorkLimits(),
        dirty: Boolean = false,
    ): JpaUnitOfWork {
        every { entityManager.unwrap(Session::class.java) } returns session
        every { session.isDirty } returns dirty
        return object : JpaUnitOfWork(events, integrationEvents, limits = limits) {
            override fun <RESULT> executeRequired(block: () -> RESULT): RESULT = required(block)
        }.also { it.entityManager = entityManager }
    }

    private class RecordingDomainEventManager(
        private var pending: Int = 0,
        private var deriveOnce: Boolean = false,
        private var deriveAlways: Boolean = false,
    ) : DomainEventManager {
        var releaseCalls: Int = 0

        override fun release(entities: Set<Any>) {
            releaseCalls++
            pending--
            if (deriveOnce || deriveAlways) {
                deriveOnce = false
                pending++
            }
        }

        override fun pendingCount(): Int = pending
    }
}

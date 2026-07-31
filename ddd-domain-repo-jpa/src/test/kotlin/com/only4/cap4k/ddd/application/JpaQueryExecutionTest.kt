package com.only4.cap4k.ddd.application

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jakarta.persistence.EntityManager
import org.hibernate.FlushMode
import org.hibernate.Session
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class JpaQueryExecutionTest {
    private val session = mockk<Session>(relaxed = true)
    private val entityManager = mockk<EntityManager>(relaxed = true)

    @Test
    fun `query execution uses manual flush and default read only for whole handler`() {
        val execution = execution()

        val result = execution.requiredReadOnly {
            assertTrue(execution.active)
            execution.execute {
                assertTrue(execution.active)
                "nested"
            }
        }

        assertEquals("nested", result)
        assertFalse(execution.active)
        verify { session.hibernateFlushMode = FlushMode.MANUAL }
        verify { session.isDefaultReadOnly = true }
        verify { session.isDefaultReadOnly = false }
        verify { session.hibernateFlushMode = FlushMode.AUTO }
    }

    private fun execution(): JpaQueryExecution {
        every { entityManager.unwrap(Session::class.java) } returns session
        every { session.hibernateFlushMode } returns FlushMode.AUTO
        every { session.isDefaultReadOnly } returns false
        return JpaQueryExecution().also { it.entityManager = entityManager }
    }
}

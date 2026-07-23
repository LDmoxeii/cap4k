package com.only4.cap4k.ddd.core.impl

import com.only4.cap4k.ddd.core.application.PersistIntent
import com.only4.cap4k.ddd.core.application.UnitOfWork
import com.only4.cap4k.ddd.core.application.UnitOfWorkSupport
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.transaction.annotation.Propagation

class DefaultMediatorTest {
    @BeforeEach
    fun setUp() {
        unitOfWork.reset()
        UnitOfWorkSupport.configure(unitOfWork)
    }

    @Test
    fun `default mediator persist forwards existing intent`() {
        val entity = Any()

        DefaultMediator().persist(entity)

        assertSame(entity, unitOfWork.persistedEntity)
        assertEquals(PersistIntent.EXISTING, unitOfWork.persistedIntent)
    }

    @Test
    fun `persist forwards entity and intent to configured unit of work`() {
        val entity = Any()

        DefaultMediator().persist(entity, PersistIntent.CREATE)

        assertSame(entity, unitOfWork.persistedEntity)
        assertEquals(PersistIntent.CREATE, unitOfWork.persistedIntent)
    }

    private class RecordingUnitOfWork : UnitOfWork {
        var persistedEntity: Any? = null
        var persistedIntent: PersistIntent? = null

        fun reset() {
            persistedEntity = null
            persistedIntent = null
        }

        override fun persist(entity: Any, intent: PersistIntent) {
            persistedEntity = entity
            persistedIntent = intent
        }

        override fun remove(entity: Any) = Unit

        override fun save(propagation: Propagation) = Unit
    }

    private companion object {
        val unitOfWork = RecordingUnitOfWork()
    }
}

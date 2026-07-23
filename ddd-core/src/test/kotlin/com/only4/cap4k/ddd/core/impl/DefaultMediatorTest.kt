package com.only4.cap4k.ddd.core.impl

import com.only4.cap4k.ddd.core.Mediator
import com.only4.cap4k.ddd.core.MediatorSupport
import com.only4.cap4k.ddd.core.application.PersistIntent
import com.only4.cap4k.ddd.core.application.UnitOfWork
import com.only4.cap4k.ddd.core.application.UnitOfWorkSupport
import com.only4.cap4k.ddd.core.domain.id.IdentifierGenerator
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.springframework.transaction.annotation.Propagation
import kotlin.reflect.KClass

class DefaultMediatorTest {

    @Test
    fun `persist forwards entity and intent to configured unit of work`() {
        val unitOfWork = RecordingUnitOfWork()
        UnitOfWorkSupport.configure(unitOfWork)
        val entity = Any()

        DefaultMediator(RecordingIdentifierGenerator()).persist(entity, PersistIntent.CREATE)

        assertSame(entity, unitOfWork.persistedEntity)
        assertEquals(PersistIntent.CREATE, unitOfWork.persistedIntent)
    }

    @Test
    fun `identifiers property delegates to configured generator`() {
        val generator = RecordingIdentifierGenerator()
        val mediator = DefaultMediator(generator)

        val id = mediator.identifiers.next("order-no", String::class)

        assertEquals("ID-1", id)
        assertEquals("order-no", generator.strategy)
        assertEquals(String::class, generator.type)
    }

    @Test
    fun `companion identifiers shortcut delegates to configured generator`() {
        val generator = RecordingIdentifierGenerator()
        MediatorSupport.configure(generator)

        val id = Mediator.identifiers.next("order-no", String::class.java)

        assertEquals("ID-1", id)
        assertEquals("order-no", generator.strategy)
        assertEquals(String::class, generator.type)
    }

    @Test
    fun `identifier generation does not touch unit of work`() {
        val unitOfWork = RecordingUnitOfWork()
        UnitOfWorkSupport.configure(unitOfWork)

        DefaultMediator(RecordingIdentifierGenerator()).identifiers.next("order-no", String::class)

        assertEquals(0, unitOfWork.persistCalls)
        assertEquals(0, unitOfWork.removeCalls)
        assertEquals(0, unitOfWork.saveCalls)
    }

    private class RecordingIdentifierGenerator : IdentifierGenerator {
        var strategy: String? = null
        var type: KClass<*>? = null

        override fun <T : Any> next(strategy: String, type: KClass<T>): T {
            this.strategy = strategy
            this.type = type
            @Suppress("UNCHECKED_CAST")
            return "ID-1" as T
        }
    }

    private class RecordingUnitOfWork : UnitOfWork {
        var persistedEntity: Any? = null
        var persistedIntent: PersistIntent? = null
        var persistCalls: Int = 0
        var removeCalls: Int = 0
        var saveCalls: Int = 0

        override fun persist(entity: Any, intent: PersistIntent) {
            persistCalls++
            persistedEntity = entity
            persistedIntent = intent
        }

        override fun remove(entity: Any) {
            removeCalls++
        }

        override fun save(propagation: Propagation) {
            saveCalls++
        }
    }
}

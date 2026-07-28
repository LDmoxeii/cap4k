package com.only4.cap4k.ddd.core

import com.only4.cap4k.ddd.core.domain.id.IdentifierGenerator
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import kotlin.reflect.KClass

class MediatorTest {
    @Test
    fun `mediator exposes configured identifier capability without a mediator instance`() {
        MediatorSupport.configure(RecordingIdentifierGenerator)

        assertEquals("order-id", Mediator.identifiers.next("order-id", String::class))
        val methodNames = Mediator::class.java.methods.map { it.name }.toSet()
        assertFalse("getInstance" in methodNames)
        assertFalse("getCmd" in methodNames)
        assertFalse("getRepo" in methodNames)
    }

    private object RecordingIdentifierGenerator : IdentifierGenerator {
        override fun <T : Any> next(strategy: String, type: KClass<T>): T {
            @Suppress("UNCHECKED_CAST")
            return strategy as T
        }
    }
}

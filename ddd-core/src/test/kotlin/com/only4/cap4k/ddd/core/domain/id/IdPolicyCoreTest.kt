package com.only4.cap4k.ddd.core.domain.id

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.util.UUID

class IdPolicyCoreTest {

    @Test
    fun `map backed registry returns registered strategy`() {
        val strategy = FixedUuidStrategy()
        val registry = MapBackedIdStrategyRegistry(listOf(strategy))

        assertSame(strategy, registry.get("fixed-uuid"))
    }

    @Test
    fun `registry rejects duplicate strategy names`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            MapBackedIdStrategyRegistry(listOf(FixedUuidStrategy(), FixedUuidStrategy()))
        }

        assertEquals("duplicate ID strategy: fixed-uuid", error.message)
    }

    @Test
    fun `allocator returns typed strategy value`() {
        val allocator = DefaultIdAllocator(MapBackedIdStrategyRegistry(listOf(FixedUuidStrategy())))

        assertEquals(UUID(1L, 2L), allocator.next("fixed-uuid", UUID::class))
    }

    @Test
    fun `allocator rejects output type mismatch`() {
        val allocator = DefaultIdAllocator(MapBackedIdStrategyRegistry(listOf(FixedUuidStrategy())))

        val error = assertThrows(IllegalArgumentException::class.java) {
            allocator.next("fixed-uuid", Long::class)
        }

        assertEquals("ID strategy fixed-uuid produces java.util.UUID, not kotlin.Long", error.message)
    }

    @Test
    fun `application side annotation exposes strategy name`() {
        val annotation = AnnotatedEntity::class.java.getDeclaredField("id")
            .getAnnotation(ApplicationSideId::class.java)

        assertEquals("fixed-uuid", annotation.strategy)
    }

    private class FixedUuidStrategy : IdStrategy {
        override val name: String = "fixed-uuid"
        override val kind: IdGenerationKind = IdGenerationKind.APPLICATION_SIDE
        override val outputType = UUID::class
        override val preassignable: Boolean = true
        override fun isDefaultValue(value: Any?): Boolean = value == UUID(0L, 0L)
        override fun next(): Any = UUID(1L, 2L)
    }

    private class AnnotatedEntity {
        @field:ApplicationSideId(strategy = "fixed-uuid")
        var id: UUID = UUID(0L, 0L)
    }
}

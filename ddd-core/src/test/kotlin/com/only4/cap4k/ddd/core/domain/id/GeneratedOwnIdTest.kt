package com.only4.cap4k.ddd.core.domain.id

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class GeneratedOwnIdTest {
    @Test
    fun `preserves an existing id without allocation or assignment`() {
        val existing = Any()
        var nextCalls = 0
        var assignCalls = 0

        val result = GeneratedOwnId.assignIfMissing(
            current = { existing },
            assign = { assignCalls++ },
            next = { nextCalls++; Any() },
            label = "Order.id",
        )

        assertSame(existing, result)
        assertEquals(0, nextCalls)
        assertEquals(0, assignCalls)
    }

    @Test
    fun `allocates assigns and reads back exactly once when missing`() {
        val generated = Any()
        var current: Any? = null
        var nextCalls = 0
        var assignCalls = 0

        val result = GeneratedOwnId.assignIfMissing(
            current = { current },
            assign = { current = it; assignCalls++ },
            next = { nextCalls++; generated },
            label = "Order.id",
        )

        assertSame(generated, result)
        assertEquals(1, nextCalls)
        assertEquals(1, assignCalls)
    }

    @Test
    fun `fails with label when assignment does not stick`() {
        val error = assertThrows(IllegalStateException::class.java) {
            GeneratedOwnId.assignIfMissing(
                current = { null },
                assign = {},
                next = { Any() },
                label = "Order.id",
            )
        }
        assertEquals("generated own ID assignment failed: Order.id", error.message)
    }

    @Test
    fun `readInitializedOrNull catches only lateinit access`() {
        class Holder { lateinit var value: String }
        val holder = Holder()

        assertEquals(null, readInitializedOrNull { holder.value })
        assertThrows(IllegalArgumentException::class.java) {
            readInitializedOrNull<String> { throw IllegalArgumentException("boom") }
        }
    }
}

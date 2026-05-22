package com.only4.cap4k.ddd.core.domain.id

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class StrongIdsTest {
    @Test
    fun `newUuidV7String returns canonical uuid v7`() {
        val value = StrongIds.newUuidV7String()

        assertEquals(value.lowercase(), value)
        assertEquals(36, value.length)
        assertEquals('7', value[14])
        assertEquals(value, StrongIds.requireUuidV7(value, "ContentId"))
    }

    @Test
    fun `newUuidV7String returns different values`() {
        assertNotEquals(StrongIds.newUuidV7String(), StrongIds.newUuidV7String())
    }

    @Test
    fun `requireUuidV7 rejects invalid values`() {
        val invalidValues = listOf(
            "",
            " ",
            "not-a-uuid",
            "0-0-7000-8000-0",
            "00000000-0000-0000-0000-000000000000",
            "550e8400-e29b-41d4-a716-446655440000",
        )

        invalidValues.forEach { value ->
            val error = assertThrows(IllegalArgumentException::class.java) {
                StrongIds.requireUuidV7(value, "ContentId")
            }
            assertTrue(error.message!!.contains("ContentId must be a UUIDv7 value"))
        }
    }
}

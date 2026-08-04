package com.only4.cap4k.ddd.core.domain.id

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

class StrongIdsTest {
    private val uuid7Text = "019c0000-0000-7000-8000-000000000001"

    @Test
    fun `uuid7 accepts canonical String and UUID backings`() {
        val uuid = UUID.fromString(uuid7Text)

        assertEquals(uuid7Text, StrongIds.requireUuidV7(uuid7Text, "OrderId"))
        assertEquals(uuid, StrongIds.requireUuidV7(uuid, "OrderId"))
    }

    @Test
    fun `uuid7 rejects non canonical or non v7 values`() {
        listOf(
            "",
            " $uuid7Text",
            uuid7Text.uppercase(),
            "00000000-0000-0000-0000-000000000000",
            "019c0000-0000-6000-8000-000000000001",
            "019c0000-0000-7000-0000-000000000001",
            "not-a-uuid",
        ).forEach { value ->
            val error = assertThrows(IllegalArgumentException::class.java) {
                StrongIds.requireUuidV7(value, "OrderId")
            }
            assertTrue(error.message!!.contains("OrderId must be a UUIDv7 value"))
        }
    }

    @Test
    fun `uuid7 UUID overload rejects wrong version and variant`() {
        listOf(
            UUID(0L, 0L),
            UUID.fromString("019c0000-0000-6000-8000-000000000001"),
            UUID.fromString("019c0000-0000-7000-0000-000000000001"),
        ).forEach { value ->
            assertThrows(IllegalArgumentException::class.java) {
                StrongIds.requireUuidV7(value, "OrderId")
            }
        }
    }

}

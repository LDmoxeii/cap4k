package com.only4.cap4k.ddd.core.domain.id

import java.util.UUID

object StrongIds {
    fun requireUuidV7(value: String, typeName: String): String {
        val uuid = runCatching { UUID.fromString(value) }.getOrNull()
        require(
            value == value.trim() &&
                value == value.lowercase() &&
                uuid != null &&
                uuid.toString() == value
        ) { "$typeName must be a UUIDv7 value: $value" }
        requireUuidV7(uuid, typeName)
        return value
    }

    fun requireUuidV7(value: UUID, typeName: String): UUID {
        require(value != UUID(0L, 0L) && value.version() == 7 && value.variant() == 2) {
            "$typeName must be a UUIDv7 value: $value"
        }
        return value
    }

}

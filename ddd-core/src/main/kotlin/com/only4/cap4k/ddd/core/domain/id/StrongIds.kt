package com.only4.cap4k.ddd.core.domain.id

import com.github.f4b6a3.uuid.UuidCreator
import java.util.UUID

object StrongIds {
    private val UUID_V7_PATTERN =
        Regex("^[0-9a-f]{8}-[0-9a-f]{4}-7[0-9a-f]{3}-[0-9a-f]{4}-[0-9a-f]{12}$")

    fun newUuidV7String(): String = UuidCreator.getTimeOrderedEpoch().toString()

    fun requireUuidV7(value: String, typeName: String): String {
        val normalized = value.trim().lowercase()
        require(normalized == value && UUID_V7_PATTERN.matches(normalized)) {
            "$typeName must be a UUIDv7 value: $value"
        }
        val uuid = runCatching { UUID.fromString(normalized) }.getOrNull()
        require(uuid != null && uuid.version() == 7 && uuid != UUID(0L, 0L)) {
            "$typeName must be a UUIDv7 value: $value"
        }
        return normalized
    }
}

package com.only4.cap4k.ddd.core.domain.id

import com.github.f4b6a3.uuid.UuidCreator
import java.util.UUID

object StrongIds {
    fun newUuidV7String(): String = UuidCreator.getTimeOrderedEpoch().toString()

    fun requireUuidV7(value: String, typeName: String): String {
        val normalized = value.trim().lowercase()
        require(normalized == value) {
            "$typeName must be a UUIDv7 value: $value"
        }
        val uuid = runCatching { UUID.fromString(normalized) }.getOrNull()
        require(uuid != null && uuid.version() == 7 && uuid != UUID(0L, 0L)) {
            "$typeName must be a UUIDv7 value: $value"
        }
        return normalized
    }
}

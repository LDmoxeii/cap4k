package com.only4.cap4k.ddd.domain.id

import com.github.f4b6a3.uuid.UuidCreator
import com.only4.cap4k.ddd.core.domain.id.IdGenerationKind
import com.only4.cap4k.ddd.core.domain.id.IdStrategy
import java.util.UUID

class Uuid7IdStrategy : IdStrategy {
    override val name: String = "uuid7"
    override val kind: IdGenerationKind = IdGenerationKind.APPLICATION_SIDE
    override val outputType = UUID::class
    override val preassignable: Boolean = true

    override fun isDefaultValue(value: Any?): Boolean =
        value == null || value == UUID(0L, 0L)

    override fun next(): Any = UuidCreator.getTimeOrderedEpoch()
}

package com.only4.cap4k.ddd.domain.id

import com.only4.cap4k.ddd.core.domain.id.IdGenerationKind
import com.only4.cap4k.ddd.core.domain.id.IdStrategy
import com.only4.cap4k.ddd.domain.distributed.snowflake.SnowflakeIdGenerator

class SnowflakeLongIdStrategy(
    private val snowflakeIdGenerator: SnowflakeIdGenerator
) : IdStrategy {
    override val name: String = "snowflake-long"
    override val kind: IdGenerationKind = IdGenerationKind.APPLICATION_SIDE
    override val outputType = Long::class
    override val preassignable: Boolean = true

    override fun isDefaultValue(value: Any?): Boolean =
        value == null || value == 0L

    override fun next(): Any = snowflakeIdGenerator.nextId()
}

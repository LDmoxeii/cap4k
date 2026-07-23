package com.only4.cap4k.ddd.domain.id

import com.only4.cap4k.ddd.core.domain.id.BuiltInIdentifierStrategies
import com.only4.cap4k.ddd.core.domain.id.IdentifierCapability
import com.only4.cap4k.ddd.core.domain.id.IdentifierStrategy
import com.only4.cap4k.ddd.domain.distributed.snowflake.SnowflakeIdGenerator
import kotlin.reflect.KClass

class SnowflakeIdentifierStrategy(
    private val snowflakeIdGenerator: SnowflakeIdGenerator
) : IdentifierStrategy {
    override val name: String = BuiltInIdentifierStrategies.SNOWFLAKE
    override val capabilities: Set<IdentifierCapability> =
        setOf(IdentifierCapability.ENTITY_ID_PREASSIGNMENT)

    override fun supports(type: KClass<*>): Boolean =
        type == Long::class || type == String::class

    override fun <T : Any> next(type: KClass<T>): T {
        require(supports(type)) { "identifier strategy $name does not support output type ${type.qualifiedName}" }
        val id = snowflakeIdGenerator.nextId()
        val value: Any = when (type) {
            Long::class -> id
            String::class -> id.toString()
            else -> error("unreachable unsupported output type: ${type.qualifiedName}")
        }

        @Suppress("UNCHECKED_CAST")
        return value as T
    }

    override fun isDefaultValue(value: Any?, type: KClass<*>): Boolean =
        when (type) {
            Long::class -> value == null || value == 0L
            String::class -> value == null || value == "" || value == "0"
            else -> value == null
        }
}

package com.only4.cap4k.ddd.domain.id

import com.github.f4b6a3.uuid.UuidCreator
import com.only4.cap4k.ddd.core.domain.id.BuiltInIdentifierStrategies
import com.only4.cap4k.ddd.core.domain.id.IdentifierCapability
import com.only4.cap4k.ddd.core.domain.id.IdentifierStrategy
import java.util.UUID
import kotlin.reflect.KClass

class Uuid7IdentifierStrategy : IdentifierStrategy {
    override val name: String = BuiltInIdentifierStrategies.UUID7
    override val capabilities: Set<IdentifierCapability> =
        setOf(IdentifierCapability.ENTITY_ID_PREASSIGNMENT)

    override fun supports(type: KClass<*>): Boolean =
        type == UUID::class || type == String::class

    override fun <T : Any> next(type: KClass<T>): T {
        require(supports(type)) { "identifier strategy $name does not support output type ${type.qualifiedName}" }
        val uuid = UuidCreator.getTimeOrderedEpoch()
        val value: Any = when (type) {
            UUID::class -> uuid
            String::class -> uuid.toString()
            else -> error("unreachable unsupported output type: ${type.qualifiedName}")
        }

        @Suppress("UNCHECKED_CAST")
        return value as T
    }

    override fun isDefaultValue(value: Any?, type: KClass<*>): Boolean =
        when (type) {
            UUID::class -> value == null || value == UUID(0L, 0L)
            String::class -> value == null || value == "" || value == UUID(0L, 0L).toString()
            else -> value == null
        }
}

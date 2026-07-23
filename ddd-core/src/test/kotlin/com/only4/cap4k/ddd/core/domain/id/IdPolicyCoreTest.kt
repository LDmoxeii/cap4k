package com.only4.cap4k.ddd.core.domain.id

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.reflect.KClass

class IdPolicyCoreTest {

    @Test
    fun `built in identifier strategy constants are stable`() {
        assertEquals("uuid7", BuiltInIdentifierStrategies.UUID7)
        assertEquals("snowflake", BuiltInIdentifierStrategies.SNOWFLAKE)
    }

    @Test
    fun `map backed registry returns registered strategy`() {
        val strategy = FixedStringStrategy()
        val registry = MapBackedIdentifierStrategyRegistry(listOf(strategy))

        assertSame(strategy, registry.get("fixed-string"))
    }

    @Test
    fun `registry rejects blank strategy names`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            MapBackedIdentifierStrategyRegistry(listOf(BlankNameStrategy()))
        }

        assertEquals("identifier strategy name must not be blank", error.message)
    }

    @Test
    fun `registry rejects duplicate strategy names`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            MapBackedIdentifierStrategyRegistry(listOf(FixedStringStrategy(), FixedStringStrategy()))
        }

        assertEquals("duplicate identifier strategy: fixed-string", error.message)
    }

    @Test
    fun `registry rejects unknown strategy names`() {
        val registry = MapBackedIdentifierStrategyRegistry(emptyList())

        val error = assertThrows(IllegalArgumentException::class.java) {
            registry.get("missing")
        }

        assertEquals("unknown identifier strategy: missing", error.message)
    }

    @Test
    fun `generator returns typed strategy value using KClass`() {
        val generator = DefaultIdentifierGenerator(
            MapBackedIdentifierStrategyRegistry(listOf(FixedStringStrategy()))
        )

        assertEquals("ORD-1", generator.next("fixed-string", String::class))
    }

    @Test
    fun `generator returns typed strategy value using Java Class`() {
        val generator = DefaultIdentifierGenerator(
            MapBackedIdentifierStrategyRegistry(listOf(FixedStringStrategy()))
        )

        assertEquals("ORD-1", generator.next("fixed-string", String::class.java))
    }

    @Test
    fun `generator rejects unsupported output type`() {
        val generator = DefaultIdentifierGenerator(
            MapBackedIdentifierStrategyRegistry(listOf(FixedStringStrategy()))
        )

        val error = assertThrows(IllegalArgumentException::class.java) {
            generator.next("fixed-string", Long::class)
        }

        assertEquals("identifier strategy fixed-string does not support output type kotlin.Long", error.message)
    }

    @Test
    fun `business code strategy without preassignment capability remains allocatable`() {
        val strategy = FixedStringStrategy()
        val generator = DefaultIdentifierGenerator(MapBackedIdentifierStrategyRegistry(listOf(strategy)))

        assertFalse(IdentifierCapability.ENTITY_ID_PREASSIGNMENT in strategy.capabilities)
        assertEquals("ORD-1", generator.next("fixed-string", String::class))
    }

    @Test
    fun `application side annotation still exposes strategy name`() {
        val annotation = AnnotatedEntity::class.java.getDeclaredField("id")
            .getAnnotation(ApplicationSideId::class.java)

        assertEquals("fixed-uuid", annotation.strategy)
    }

    private class FixedStringStrategy : IdentifierStrategy {
        override val name: String = "fixed-string"
        override val capabilities: Set<IdentifierCapability> = emptySet()
        override fun supports(type: KClass<*>): Boolean = type == String::class
        override fun <T : Any> next(type: KClass<T>): T {
            require(supports(type)) { "identifier strategy $name does not support output type ${type.qualifiedName}" }
            @Suppress("UNCHECKED_CAST")
            return "ORD-1" as T
        }
        override fun isDefaultValue(value: Any?, type: KClass<*>): Boolean = value == null || value == ""
    }

    private class BlankNameStrategy : IdentifierStrategy {
        override val name: String = " "
        override val capabilities: Set<IdentifierCapability> = emptySet()
        override fun supports(type: KClass<*>): Boolean = false
        override fun <T : Any> next(type: KClass<T>): T = error("not used")
        override fun isDefaultValue(value: Any?, type: KClass<*>): Boolean = value == null
    }

    private class AnnotatedEntity {
        @field:ApplicationSideId(strategy = "fixed-uuid")
        var id: UUID = UUID(0L, 0L)
    }
}

package com.only4.cap4k.ddd.domain.id

import com.only4.cap4k.ddd.core.domain.id.BuiltInIdentifierStrategies
import com.only4.cap4k.ddd.core.domain.id.IdentifierCapability
import com.only4.cap4k.ddd.core.domain.id.IdentifierGenerator
import com.only4.cap4k.ddd.core.domain.id.IdentifierStrategy
import com.only4.cap4k.ddd.core.domain.id.IdentifierStrategyRegistry
import com.only4.cap4k.ddd.domain.distributed.snowflake.SnowflakeIdGenerator
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import java.util.UUID
import kotlin.reflect.KClass

class IdPolicyAutoConfigurationTest {

    private val contextRunner = ApplicationContextRunner()
        .withUserConfiguration(IdPolicyAutoConfiguration::class.java)

    @Test
    fun `registry exposes uuid7 by default`() {
        contextRunner.run { context ->
            val registry = context.getBean(IdentifierStrategyRegistry::class.java)
            val strategy = registry.get(BuiltInIdentifierStrategies.UUID7)

            assertTrue(IdentifierCapability.ENTITY_ID_PREASSIGNMENT in strategy.capabilities)
            assertTrue(strategy.supports(UUID::class))
            assertTrue(strategy.supports(String::class))
            assertFalse(strategy.supports(Long::class))
            assertTrue(strategy.isDefaultValue(UUID(0L, 0L), UUID::class))
            assertTrue(strategy.isDefaultValue("", String::class))
        }
    }

    @Test
    fun `generator returns uuid7 UUID and String values without snowflake bean`() {
        contextRunner.run { context ->
            val generator = context.getBean(IdentifierGenerator::class.java)
            val first = generator.next(BuiltInIdentifierStrategies.UUID7, UUID::class)
            val second = generator.next(BuiltInIdentifierStrategies.UUID7, String::class)

            assertEquals(7, first.version())
            assertTrue(UUID.fromString(second).version() == 7)
            assertNotEquals(first.toString(), second)
        }
    }

    @Test
    fun `uuid7 rejects unsupported output type`() {
        contextRunner.run { context ->
            val generator = context.getBean(IdentifierGenerator::class.java)

            val error = assertThrows(IllegalArgumentException::class.java) {
                generator.next(BuiltInIdentifierStrategies.UUID7, Long::class)
            }

            assertEquals("identifier strategy uuid7 does not support output type kotlin.Long", error.message)
        }
    }

    @Test
    fun `registry exposes snowflake family when snowflake generator bean exists`() {
        ApplicationContextRunner()
            .withBean(SnowflakeIdGenerator::class.java, { SnowflakeIdGenerator(1L, 1L) })
            .withUserConfiguration(IdPolicyAutoConfiguration::class.java)
            .run { context ->
                val generator = context.getBean(IdentifierGenerator::class.java)
                val longId = generator.next(BuiltInIdentifierStrategies.SNOWFLAKE, Long::class)
                val stringId = generator.next(BuiltInIdentifierStrategies.SNOWFLAKE, String::class)

                assertTrue(longId > 0L)
                assertTrue(stringId.toLong() > 0L)
            }
    }

    @Test
    fun `snowflake rejects unsupported output type`() {
        ApplicationContextRunner()
            .withBean(SnowflakeIdGenerator::class.java, { SnowflakeIdGenerator(1L, 1L) })
            .withUserConfiguration(IdPolicyAutoConfiguration::class.java)
            .run { context ->
                val generator = context.getBean(IdentifierGenerator::class.java)

                val error = assertThrows(IllegalArgumentException::class.java) {
                    generator.next(BuiltInIdentifierStrategies.SNOWFLAKE, UUID::class)
                }

                assertEquals("identifier strategy snowflake does not support output type java.util.UUID", error.message)
            }
    }

    @Test
    fun `snowflake long legacy name is not registered`() {
        ApplicationContextRunner()
            .withBean(SnowflakeIdGenerator::class.java, { SnowflakeIdGenerator(1L, 1L) })
            .withUserConfiguration(IdPolicyAutoConfiguration::class.java)
            .run { context ->
                val registry = context.getBean(IdentifierStrategyRegistry::class.java)

                val error = assertThrows(IllegalArgumentException::class.java) {
                    registry.get("snowflake-long")
                }

                assertEquals("unknown identifier strategy: snowflake-long", error.message)
            }
    }

    @Test
    fun `application provided strategy bean is collected`() {
        ApplicationContextRunner()
            .withBean("orderNoStrategy", IdentifierStrategy::class.java, { OrderNoStrategy() })
            .withUserConfiguration(IdPolicyAutoConfiguration::class.java)
            .run { context ->
                val generator = context.getBean(IdentifierGenerator::class.java)

                assertEquals("ORD-1", generator.next("order-no", String::class))
            }
    }

    @Test
    fun `duplicate strategy names fail fast`() {
        ApplicationContextRunner()
            .withBean("duplicateUuid7Strategy", IdentifierStrategy::class.java, { DuplicateUuid7Strategy() })
            .withUserConfiguration(IdPolicyAutoConfiguration::class.java)
            .run { context ->
                assertNotNull(context.startupFailure)
                assertTrue(context.startupFailure!!.message!!.contains("duplicate identifier strategy: uuid7"))
            }
    }

    private open class OrderNoStrategy : IdentifierStrategy {
        override open val name: String = "order-no"
        override val capabilities: Set<IdentifierCapability> = emptySet()
        override fun supports(type: KClass<*>): Boolean = type == String::class
        override fun <T : Any> next(type: KClass<T>): T {
            require(supports(type)) { "identifier strategy $name does not support output type ${type.qualifiedName}" }
            @Suppress("UNCHECKED_CAST")
            return "ORD-1" as T
        }
        override fun isDefaultValue(value: Any?, type: KClass<*>): Boolean = value == null || value == ""
    }

    private class DuplicateUuid7Strategy : OrderNoStrategy() {
        override val name: String = "uuid7"
    }
}

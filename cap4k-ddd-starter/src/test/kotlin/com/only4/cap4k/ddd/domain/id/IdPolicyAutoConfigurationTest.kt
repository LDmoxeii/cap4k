package com.only4.cap4k.ddd.domain.id

import com.only4.cap4k.ddd.core.domain.id.IdAllocator
import com.only4.cap4k.ddd.core.domain.id.IdGenerationKind
import com.only4.cap4k.ddd.core.domain.id.IdStrategyRegistry
import com.only4.cap4k.ddd.domain.distributed.snowflake.SnowflakeIdGenerator
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import java.util.UUID

class IdPolicyAutoConfigurationTest {

    private val contextRunner = ApplicationContextRunner()
        .withUserConfiguration(IdPolicyAutoConfiguration::class.java)

    @Test
    fun `registry exposes uuid7 by default`() {
        contextRunner.run { context ->
            val registry = context.getBean(IdStrategyRegistry::class.java)
            val strategy = registry.get("uuid7")

            assertEquals(IdGenerationKind.APPLICATION_SIDE, strategy.kind)
            assertEquals(UUID::class, strategy.outputType)
            assertTrue(strategy.preassignable)
            assertTrue(strategy.isDefaultValue(UUID(0L, 0L)))
            assertFalse(strategy.isDefaultValue(strategy.next()))
        }
    }

    @Test
    fun `allocator returns uuid7 values without snowflake bean`() {
        contextRunner.run { context ->
            val allocator = context.getBean(IdAllocator::class.java)
            val first = allocator.next("uuid7", UUID::class)
            val second = allocator.next("uuid7", UUID::class)

            assertEquals(7, first.version())
            assertEquals(7, second.version())
            assertNotEquals(first, second)
        }
    }

    @Test
    fun `registry exposes snowflake long when snowflake generator bean exists`() {
        ApplicationContextRunner()
            .withBean(SnowflakeIdGenerator::class.java, { SnowflakeIdGenerator(1L, 1L) })
            .withUserConfiguration(IdPolicyAutoConfiguration::class.java)
            .run { context ->
                val allocator = context.getBean(IdAllocator::class.java)
                val id = allocator.next("snowflake-long", Long::class)

                assertTrue(id > 0L)
            }
    }
}

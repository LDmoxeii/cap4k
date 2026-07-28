package com.only4.cap4k.ddd.starter.snowflake

import com.only4.cap4k.ddd.core.domain.id.BuiltInIdentifierStrategies
import com.only4.cap4k.ddd.core.domain.id.IdentifierStrategy
import com.only4.cap4k.ddd.domain.distributed.SnowflakeAutoConfiguration
import com.only4.cap4k.ddd.domain.distributed.configure.SnowflakeProperties
import com.only4.cap4k.ddd.domain.distributed.snowflake.SnowflakeWorkerIdDispatcher
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.ApplicationContextRunner

class SnowflakeStarterTest {
    @Test
    fun `starter contributes Snowflake strategy without enable switch`() {
        val dispatcher = RecordingDispatcher()
        ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(SnowflakeAutoConfiguration::class.java))
            .withBean(SnowflakeWorkerIdDispatcher::class.java, { dispatcher })
            .run { context ->
                assertEquals(
                    BuiltInIdentifierStrategies.SNOWFLAKE,
                    context.getBean("snowflakeIdentifierStrategy", IdentifierStrategy::class.java).name,
                )
            }

        assertFalse(SnowflakeProperties::class.java.declaredFields.any { it.name == "enable" })
    }

    @Test
    fun `imports only Snowflake auto configuration and classpath excludes JPA and locker`() {
        assertEquals(
            setOf("com.only4.cap4k.ddd.domain.distributed.SnowflakeAutoConfiguration"),
            ownAutoConfigurationImports("SnowflakeAutoConfiguration"),
        )
        assertThrows(ClassNotFoundException::class.java) {
            Class.forName("com.only4.cap4k.ddd.application.JpaUnitOfWork")
        }
        assertThrows(ClassNotFoundException::class.java) {
            Class.forName("com.only4.cap4k.ddd.application.distributed.locker.JdbcLocker")
        }
    }

    private fun ownAutoConfigurationImports(marker: String): Set<String> =
        javaClass.classLoader
            .getResources("META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports")
            .toList()
            .map { resource -> resource.readText().lineSequence().filter(String::isNotBlank).toSet() }
            .single { imports -> imports.any { it.contains(marker) } }

    private class RecordingDispatcher : SnowflakeWorkerIdDispatcher {
        override fun acquire(workerId: Long?, datacenterId: Long?): Long = 33L
        override fun release() = Unit
        override fun pong(): Boolean = true
        override fun remind() = Unit
    }
}

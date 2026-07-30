package com.only4.cap4k.ddd.starter.event

import com.only4.cap4k.ddd.core.domain.event.EventRecordRepository
import com.only4.cap4k.ddd.domain.event.DomainEventJpaAutoConfiguration
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DomainEventJpaStarterBoundaryTest {
    @Test
    fun `imports only domain event JPA auto configuration`() {
        assertEquals(
            setOf("com.only4.cap4k.ddd.domain.event.DomainEventJpaAutoConfiguration"),
            ownAutoConfigurationImports("DomainEventJpaAutoConfiguration"),
        )
    }

    @Test
    fun `reliable event infrastructure requires an explicit EventRecordRepository provider`() {
        val parameterTypes = DomainEventJpaAutoConfiguration::class.java
            .getDeclaredMethod(
                "reliableEventInfrastructure",
                List::class.java,
                List::class.java,
                EventRecordRepository::class.java,
            )
            .parameterTypes

        assertTrue(parameterTypes.contains(EventRecordRepository::class.java))
    }

    @Test
    fun `domain event starter does not select command persistence or Snowflake`() {
        assertThrows(ClassNotFoundException::class.java) {
            Class.forName("com.only4.cap4k.ddd.application.command.JpaCommandRecordRepository")
        }
        assertThrows(ClassNotFoundException::class.java) {
            Class.forName("com.only4.cap4k.ddd.domain.distributed.snowflake.SnowflakeIdGenerator")
        }
    }

    private fun ownAutoConfigurationImports(marker: String): Set<String> =
        javaClass.classLoader
            .getResources("META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports")
            .toList()
            .map { resource -> resource.readText().lineSequence().filter(String::isNotBlank).toSet() }
            .single { imports -> imports.any { it.contains(marker) } }
}

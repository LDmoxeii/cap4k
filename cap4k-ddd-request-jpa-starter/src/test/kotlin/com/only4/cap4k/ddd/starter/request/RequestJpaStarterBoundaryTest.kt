package com.only4.cap4k.ddd.starter.request

import com.only4.cap4k.ddd.application.request.RequestJpaAutoConfiguration
import com.only4.cap4k.ddd.core.application.distributed.Locker
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RequestJpaStarterBoundaryTest {
    @Test
    fun `imports only request JPA auto configuration`() {
        assertEquals(
            setOf("com.only4.cap4k.ddd.application.request.RequestJpaAutoConfiguration"),
            ownAutoConfigurationImports("RequestJpaAutoConfiguration"),
        )
    }

    @Test
    fun `request scheduling requires an explicit Locker provider`() {
        val parameterTypes = RequestJpaAutoConfiguration::class.java
            .getDeclaredMethod(
                "jpaRequestScheduleService",
                com.only4.cap4k.ddd.core.application.RequestManager::class.java,
                Locker::class.java,
                String::class.java,
                String::class.java,
                com.only4.cap4k.ddd.application.request.configure.RequestScheduleProperties::class.java,
                org.springframework.jdbc.core.JdbcTemplate::class.java,
            )
            .parameterTypes

        assertTrue(parameterTypes.contains(Locker::class.java))
    }

    @Test
    fun `request starter does not select event persistence or Snowflake`() {
        assertThrows(ClassNotFoundException::class.java) {
            Class.forName("com.only4.cap4k.ddd.domain.event.JpaEventRecordRepository")
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

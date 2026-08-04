package com.only4.cap4k.ddd.starter.command

import com.only4.cap4k.ddd.application.command.CommandJpaAutoConfiguration
import com.only4.cap4k.ddd.core.application.distributed.Locker
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CommandJpaStarterBoundaryTest {
    @Test
    fun `imports only command JPA auto configuration`() {
        assertEquals(
            setOf("com.only4.cap4k.ddd.application.command.CommandJpaAutoConfiguration"),
            ownAutoConfigurationImports("CommandJpaAutoConfiguration"),
        )
    }

    @Test
    fun `command scheduling requires an explicit Locker provider`() {
        val parameterTypes = CommandJpaAutoConfiguration::class.java
            .getDeclaredMethod(
                "jpaCommandScheduleService",
                com.only4.cap4k.ddd.core.application.command.CommandManager::class.java,
                Locker::class.java,
                String::class.java,
                String::class.java,
                com.only4.cap4k.ddd.application.command.configure.CommandScheduleProperties::class.java,
                org.springframework.jdbc.core.JdbcTemplate::class.java,
            )
            .parameterTypes

        assertTrue(parameterTypes.contains(Locker::class.java))
    }

    @Test
    fun `command starter does not select event persistence`() {
        assertThrows(ClassNotFoundException::class.java) {
            Class.forName("com.only4.cap4k.ddd.domain.event.JpaEventRecordRepository")
        }
    }

    private fun ownAutoConfigurationImports(marker: String): Set<String> =
        javaClass.classLoader
            .getResources("META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports")
            .toList()
            .map { resource -> resource.readText().lineSequence().filter(String::isNotBlank).toSet() }
            .single { imports -> imports.any { it.contains(marker) } }
}

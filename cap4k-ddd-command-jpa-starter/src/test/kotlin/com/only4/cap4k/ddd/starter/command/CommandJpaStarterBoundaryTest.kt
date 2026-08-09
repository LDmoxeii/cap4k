package com.only4.cap4k.ddd.starter.command

import com.only4.cap4k.ddd.application.command.CommandJpaAutoConfiguration
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.context.SmartLifecycle

class CommandJpaStarterBoundaryTest {
    @Test
    fun `imports only command JPA auto configuration`() {
        assertEquals(
            setOf("com.only4.cap4k.ddd.application.command.CommandJpaAutoConfiguration"),
            ownAutoConfigurationImports("CommandJpaAutoConfiguration"),
        )
    }

    @Test
    fun `command worker is wired to the private substrate without Locker`() {
        val workerMethod = CommandJpaAutoConfiguration::class.java.declaredMethods
            .single { it.name == "jpaReliableCommandWorker" }
        val parameterTypeNames = workerMethod.parameterTypes.map(Class<*>::getName)

        assertTrue(parameterTypeNames.contains("com.only4.cap4k.ddd.application.command.JpaCommandExecutionSubstrate"))
        assertTrue(parameterTypeNames.contains("com.only4.cap4k.ddd.core.application.command.CommandSupervisor"))
        assertFalse(parameterTypeNames.contains("com.only4.cap4k.ddd.core.application.distributed.Locker"))

        val lifecycleMethod = CommandJpaAutoConfiguration::class.java.declaredMethods
            .single { it.name == "jpaReliableCommandWorkerLifecycle" }
        assertEquals(SmartLifecycle::class.java, lifecycleMethod.returnType)
    }

    @Test
    fun `legacy command manager and polling scheduler are absent`() {
        listOf(
            "com.only4.cap4k.ddd.core.application.command.CommandManager",
            "com.only4.cap4k.ddd.application.command.JpaCommandScheduleService",
            "com.only4.cap4k.ddd.application.command.configure.CommandScheduleProperties",
        ).forEach { className ->
            assertThrows(ClassNotFoundException::class.java) { Class.forName(className) }
        }
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

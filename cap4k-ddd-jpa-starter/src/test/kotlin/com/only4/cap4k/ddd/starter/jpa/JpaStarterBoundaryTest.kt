package com.only4.cap4k.ddd.starter.jpa

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class JpaStarterBoundaryTest {
    @Test
    fun `imports only JPA owned auto configurations`() {
        assertEquals(
            setOf(
                "com.only4.cap4k.ddd.domain.repo.JpaRepositoryAutoConfiguration",
                "com.only4.cap4k.ddd.domain.web.ClearDomainContextAutoConfiguration",
            ),
            ownAutoConfigurationImports("JpaRepositoryAutoConfiguration"),
        )
    }

    @Test
    fun `JPA starter does not select advanced implementations`() {
        val buildScript = Files.readString(Path.of("build.gradle.kts"))
        assertFalse(
            Regex("""(?m)^\s*(api|implementation)\(project\(\":ddd-distributed-snowflake\"\)\)""")
                .containsMatchIn(buildScript),
        )
        assertNotOnClasspath("com.only4.cap4k.ddd.application.distributed.locker.JdbcLocker")
        assertNotOnClasspath("com.only4.cap4k.ddd.application.saga.JpaSagaRecordRepository")
        assertNotOnClasspath("com.only4.cap4k.ddd.application.command.JpaCommandRecordRepository")
        assertNotOnClasspath("com.only4.cap4k.ddd.domain.event.JpaEventRecordRepository")
    }

    private fun ownAutoConfigurationImports(marker: String): Set<String> =
        javaClass.classLoader
            .getResources("META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports")
            .toList()
            .map { resource -> resource.readText().lineSequence().filter(String::isNotBlank).toSet() }
            .single { imports -> imports.any { it.contains(marker) } }

    private fun assertNotOnClasspath(className: String) {
        assertThrows(ClassNotFoundException::class.java) { Class.forName(className) }
    }
}

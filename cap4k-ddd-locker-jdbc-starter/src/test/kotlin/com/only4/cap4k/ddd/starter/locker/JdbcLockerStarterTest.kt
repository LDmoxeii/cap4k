package com.only4.cap4k.ddd.starter.locker

import com.only4.cap4k.ddd.application.distributed.JdbcLockerAutoConfiguration
import com.only4.cap4k.ddd.core.application.distributed.Locker
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource

class JdbcLockerStarterTest {
    @Test
    fun `starter installs JDBC locker when JdbcTemplate is available`() {
        ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(JdbcLockerAutoConfiguration::class.java))
            .withBean(
                JdbcTemplate::class.java,
                { JdbcTemplate(DriverManagerDataSource("jdbc:h2:mem:locker-starter;DB_CLOSE_DELAY=-1", "sa", "")) },
            )
            .run { context ->
                assertNotNull(context.getBean(Locker::class.java))
            }
    }

    @Test
    fun `installed starter fails when JdbcTemplate provider is missing`() {
        ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(JdbcLockerAutoConfiguration::class.java))
            .run { context ->
                assertNotNull(context.startupFailure)
                assertTrue(context.startupFailure!!.stackTraceToString().contains("JdbcTemplate"))
            }
    }

    @Test
    fun `imports only locker auto configuration and classpath excludes other implementations`() {
        assertEquals(
            setOf("com.only4.cap4k.ddd.application.distributed.JdbcLockerAutoConfiguration"),
            ownAutoConfigurationImports("JdbcLockerAutoConfiguration"),
        )
        assertThrows(ClassNotFoundException::class.java) {
            Class.forName("com.only4.cap4k.ddd.application.JpaUnitOfWork")
        }
    }

    private fun ownAutoConfigurationImports(marker: String): Set<String> =
        javaClass.classLoader
            .getResources("META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports")
            .toList()
            .map { resource -> resource.readText().lineSequence().filter(String::isNotBlank).toSet() }
            .single { imports -> imports.any { it.contains(marker) } }
}

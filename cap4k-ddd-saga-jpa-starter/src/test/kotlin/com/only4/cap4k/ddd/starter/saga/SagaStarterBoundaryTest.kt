package com.only4.cap4k.ddd.starter.saga

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class SagaStarterBoundaryTest {
    @Test
    fun `imports only Saga auto configuration`() {
        assertEquals(
            setOf("com.only4.cap4k.ddd.application.saga.SagaAutoConfiguration"),
            ownAutoConfigurationImports("SagaAutoConfiguration"),
        )
    }

    @Test
    fun `Saga starter does not select JDBC locker or reliable event implementation`() {
        assertThrows(ClassNotFoundException::class.java) {
            Class.forName("com.only4.cap4k.ddd.application.distributed.locker.JdbcLocker")
        }
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

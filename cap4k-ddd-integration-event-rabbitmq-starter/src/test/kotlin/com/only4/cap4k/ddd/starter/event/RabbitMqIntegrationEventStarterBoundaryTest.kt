package com.only4.cap4k.ddd.starter.event

import com.only4.cap4k.ddd.application.event.RabbitMqIntegrationEventAutoConfiguration
import com.only4.cap4k.ddd.core.domain.event.EventRecordRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RabbitMqIntegrationEventStarterBoundaryTest {
    @Test
    fun `imports only RabbitMQ integration event auto configuration`() {
        assertEquals(
            setOf("com.only4.cap4k.ddd.application.event.RabbitMqIntegrationEventAutoConfiguration"),
            ownAutoConfigurationImports("RabbitMqIntegrationEventAutoConfiguration"),
        )
    }

    @Test
    fun `transport supervisor requires an external EventRecordRepository provider`() {
        val parameterTypes = RabbitMqIntegrationEventAutoConfiguration::class.java
            .getDeclaredMethod(
                "integrationEventSupervisor",
                com.only4.cap4k.ddd.core.domain.event.EventPublisher::class.java,
                EventRecordRepository::class.java,
                com.only4.cap4k.ddd.core.application.event.IntegrationEventInterceptorManager::class.java,
                org.springframework.context.ApplicationEventPublisher::class.java,
                String::class.java,
            )
            .parameterTypes

        assertTrue(parameterTypes.contains(EventRecordRepository::class.java))
    }

    @Test
    fun `RabbitMQ transport starter does not select JPA or JDBC locker`() {
        assertThrows(ClassNotFoundException::class.java) {
            Class.forName("com.only4.cap4k.ddd.domain.event.JpaEventRecordRepository")
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
}

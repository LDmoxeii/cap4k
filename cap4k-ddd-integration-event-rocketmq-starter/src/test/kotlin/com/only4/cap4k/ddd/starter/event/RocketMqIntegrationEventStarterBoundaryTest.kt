package com.only4.cap4k.ddd.starter.event

import com.only4.cap4k.ddd.application.event.RocketMqIntegrationEventAutoConfiguration
import com.only4.cap4k.ddd.application.event.RocketMqIntegrationEventPublisher
import com.only4.cap4k.ddd.core.application.provider.InMemoryRuntimeProviderStateRegistry
import com.only4.cap4k.ddd.core.domain.event.EventRecordRepository
import com.only4.cap4k.ddd.core.domain.event.ReliableEventCoordinator
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RocketMqIntegrationEventStarterBoundaryTest {
    @Test
    fun `imports only RocketMQ integration event auto configuration`() {
        assertEquals(
            setOf("com.only4.cap4k.ddd.application.event.RocketMqIntegrationEventAutoConfiguration"),
            ownAutoConfigurationImports("RocketMqIntegrationEventAutoConfiguration"),
        )
    }

    @Test
    fun `transport supervisor requires the coordinator and EventRecordRepository providers`() {
        val parameterTypes = RocketMqIntegrationEventAutoConfiguration::class.java
            .getDeclaredMethod(
                "integrationEventSupervisor",
                ReliableEventCoordinator::class.java,
                EventRecordRepository::class.java,
                com.only4.cap4k.ddd.core.application.event.IntegrationEventInterceptorManager::class.java,
                org.springframework.context.ApplicationEventPublisher::class.java,
                com.only4.cap4k.ddd.core.application.context.ExecutionContextAccessor::class.java,
                com.only4.cap4k.ddd.core.application.context.ExecutionContextCodecRegistry::class.java,
                com.only4.cap4k.ddd.core.application.invocation.InvocationScopeAccessor::class.java,
                String::class.java,
            )
            .parameterTypes

        assertTrue(parameterTypes.contains(ReliableEventCoordinator::class.java))
        assertTrue(parameterTypes.contains(EventRecordRepository::class.java))
    }

    @Test
    fun `RocketMQ transport starter does not select JPA or JDBC locker`() {
        assertThrows(ClassNotFoundException::class.java) {
            Class.forName("com.only4.cap4k.ddd.domain.event.JpaEventRecordRepository")
        }
        assertThrows(ClassNotFoundException::class.java) {
            Class.forName("com.only4.cap4k.ddd.application.distributed.locker.JdbcLocker")
        }
    }

    @Test
    fun `provider state uses stable identity and close unregisters it`() {
        val registry = InMemoryRuntimeProviderStateRegistry()
        val reporter = RocketMqIntegrationEventAutoConfiguration()
            .rocketMqIntegrationEventProviderState(registry)

        assertEquals(
            RocketMqIntegrationEventPublisher.PROVIDER_IDENTITY,
            registry.snapshot().single().providerId,
        )

        reporter.close()

        assertTrue(registry.snapshot().isEmpty())
    }

    private fun ownAutoConfigurationImports(marker: String): Set<String> =
        javaClass.classLoader
            .getResources("META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports")
            .toList()
            .map { resource -> resource.readText().lineSequence().filter(String::isNotBlank).toSet() }
            .single { imports -> imports.any { it.contains(marker) } }
}

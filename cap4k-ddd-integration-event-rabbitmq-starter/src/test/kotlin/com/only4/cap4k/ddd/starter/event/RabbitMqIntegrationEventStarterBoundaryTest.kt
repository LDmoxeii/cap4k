package com.only4.cap4k.ddd.starter.event

import com.only4.cap4k.ddd.application.event.RabbitMqIntegrationEventAutoConfiguration
import com.only4.cap4k.ddd.application.event.configure.RabbitMqIntegrationEventAdapterProperties
import com.only4.cap4k.ddd.core.domain.event.EventRecordRepository
import com.only4.cap4k.ddd.core.domain.event.ReliableEventCoordinator
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Configuration

class RabbitMqIntegrationEventStarterBoundaryTest {
    private val propertyContext = ApplicationContextRunner()
        .withUserConfiguration(PropertyBindingConfiguration::class.java)

    @Test
    fun `imports only RabbitMQ integration event auto configuration`() {
        assertEquals(
            setOf("com.only4.cap4k.ddd.application.event.RabbitMqIntegrationEventAutoConfiguration"),
            ownAutoConfigurationImports("RabbitMqIntegrationEventAutoConfiguration"),
        )
    }

    @Test
    fun `transport supervisor requires the coordinator and EventRecordRepository providers`() {
        val parameterTypes = RabbitMqIntegrationEventAutoConfiguration::class.java
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
    fun `RabbitMQ transport starter does not select JPA or JDBC locker`() {
        assertThrows(ClassNotFoundException::class.java) {
            Class.forName("com.only4.cap4k.ddd.domain.event.JpaEventRecordRepository")
        }
        assertThrows(ClassNotFoundException::class.java) {
            Class.forName("com.only4.cap4k.ddd.application.distributed.locker.JdbcLocker")
        }
    }

    @Test
    fun `binds explicit event-name route and Rabbit runtime limits`() {
        propertyContext
            .withPropertyValues(
                "cap4k.ddd.integration-event.rabbitmq.routes[content.published].exchange=content",
                "cap4k.ddd.integration-event.rabbitmq.routes[content.published].routing-key=published",
                "cap4k.ddd.integration-event.rabbitmq.confirm-timeout=3s",
                "cap4k.ddd.integration-event.rabbitmq.recovery-interval=2s",
            )
            .run { context ->
                val properties = context.getBean(RabbitMqIntegrationEventAdapterProperties::class.java)
                assertEquals("content", properties.routes.getValue("content.published").exchange)
                assertEquals("published", properties.routes.getValue("content.published").routingKey)
                assertEquals(java.time.Duration.ofSeconds(3), properties.confirmTimeout)
                assertEquals(java.time.Duration.ofSeconds(2), properties.recoveryInterval)
            }
    }

    @Test
    fun `invalid static route fails property binding`() {
        propertyContext
            .withPropertyValues(
                "cap4k.ddd.integration-event.rabbitmq.routes[content.published].exchange=",
                "cap4k.ddd.integration-event.rabbitmq.routes[content.published].routing-key=published",
            )
            .run { context -> assertTrue(context.startupFailure != null) }
    }

    private fun ownAutoConfigurationImports(marker: String): Set<String> =
        javaClass.classLoader
            .getResources("META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports")
            .toList()
            .map { resource -> resource.readText().lineSequence().filter(String::isNotBlank).toSet() }
            .single { imports -> imports.any { it.contains(marker) } }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(RabbitMqIntegrationEventAdapterProperties::class)
    private class PropertyBindingConfiguration
}

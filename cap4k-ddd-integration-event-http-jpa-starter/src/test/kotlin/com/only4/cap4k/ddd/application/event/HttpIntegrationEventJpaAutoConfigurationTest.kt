package com.only4.cap4k.ddd.application.event

import com.only4.cap4k.ddd.application.event.persistence.EventHttpSubscriberJpaRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration
import org.springframework.boot.test.context.runner.ApplicationContextRunner

class HttpIntegrationEventJpaAutoConfigurationTest {
    private val contextRunner = ApplicationContextRunner()
        .withConfiguration(
            AutoConfigurations.of(
                DataSourceAutoConfiguration::class.java,
                HibernateJpaAutoConfiguration::class.java,
                HttpIntegrationEventJpaAutoConfiguration::class.java,
            ),
        )
        .withPropertyValues(
            "spring.datasource.url=jdbc:h2:mem:http-integration-event;DB_CLOSE_DELAY=-1",
            "spring.jpa.hibernate.ddl-auto=create-drop",
        )

    @Test
    fun `http jpa starter provides persistent subscriber register`() {
        contextRunner.run { context ->
            assertTrue(context.startupFailure == null, context.startupFailure?.stackTraceToString())
            assertEquals(1, context.getBeansOfType(EventHttpSubscriberJpaRepository::class.java).size)
            val registers = context.getBeansOfType(HttpIntegrationEventSubscriberRegister::class.java)
            assertEquals(1, registers.size)
            assertTrue(registers.values.single() is JpaHttpIntegrationEventSubscriberRegister)
        }
    }
}

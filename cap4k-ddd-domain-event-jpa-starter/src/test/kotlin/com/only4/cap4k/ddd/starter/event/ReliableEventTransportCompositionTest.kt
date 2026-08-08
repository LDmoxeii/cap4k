package com.only4.cap4k.ddd.starter.event

import com.only4.cap4k.ddd.application.event.HttpIntegrationEventAutoConfiguration
import com.only4.cap4k.ddd.core.application.context.DefaultExecutionContextManager
import com.only4.cap4k.ddd.core.application.context.ExecutionContextCodecRegistry
import com.only4.cap4k.ddd.core.application.event.IntegrationEventManager
import com.only4.cap4k.ddd.core.application.event.IntegrationEventPublisher
import com.only4.cap4k.ddd.core.application.invocation.DefaultInvocationScopeManager
import com.only4.cap4k.ddd.core.application.invocation.InvocationScopeAccessor
import com.only4.cap4k.ddd.core.domain.event.DomainEventInterceptorManager
import com.only4.cap4k.ddd.core.domain.event.EventHandlerDispatcher
import com.only4.cap4k.ddd.core.domain.event.EventPublisher
import com.only4.cap4k.ddd.core.domain.event.EventTypeCatalog
import com.only4.cap4k.ddd.core.domain.event.ReliableEventCoordinator
import com.only4.cap4k.ddd.core.domain.event.ReliableEventDeliveryContextScopeManager
import com.only4.cap4k.ddd.core.domain.event.impl.DefaultDomainEventInterceptorManager
import com.only4.cap4k.ddd.core.domain.event.impl.DefaultReliableEventDeliveryContextManager
import com.only4.cap4k.ddd.domain.event.DomainEventJpaAutoConfiguration
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

class ReliableEventTransportCompositionTest {
    private val contextRunner = ApplicationContextRunner()
        .withConfiguration(
            AutoConfigurations.of(
                DataSourceAutoConfiguration::class.java,
                HibernateJpaAutoConfiguration::class.java,
                JdbcTemplateAutoConfiguration::class.java,
                DomainEventJpaAutoConfiguration::class.java,
                HttpIntegrationEventAutoConfiguration::class.java,
            ),
        )
        .withUserConfiguration(SupportConfiguration::class.java)
        .withPropertyValues(
            "spring.application.name=reliable-event-composition",
            "spring.datasource.url=jdbc:h2:mem:reliable-event-transport;MODE=MySQL;DB_CLOSE_DELAY=-1",
            "spring.jpa.hibernate.ddl-auto=create-drop",
            "spring.jpa.open-in-view=false",
            "cap4k.ddd.domain.event.schedule.add-partition-enable=false",
        )

    @Test
    fun `JPA coordinator and HTTP transport start without a bean cycle`() {
        contextRunner.run { context ->
            assertNull(context.startupFailure, context.startupFailure?.stackTraceToString())
            assertEquals(1, context.getBeansOfType(EventPublisher::class.java).size)
            assertEquals(1, context.getBeansOfType(ReliableEventCoordinator::class.java).size)
            assertEquals(1, context.getBeansOfType(IntegrationEventManager::class.java).size)
            assertEquals(1, context.getBeansOfType(IntegrationEventPublisher::class.java).size)
        }
    }

    @Configuration(proxyBeanMethods = false)
    class SupportConfiguration {
        @Bean
        fun eventHandlerDispatcher(): EventHandlerDispatcher = mock(EventHandlerDispatcher::class.java)

        @Bean
        fun domainEventInterceptorManager(): DomainEventInterceptorManager =
            DefaultDomainEventInterceptorManager(emptyList())

        @Bean
        fun executionContextManager(): DefaultExecutionContextManager = DefaultExecutionContextManager()

        @Bean
        fun executionContextCodecRegistry(): ExecutionContextCodecRegistry =
            ExecutionContextCodecRegistry(emptyList())

        @Bean
        fun reliableEventDeliveryContextScopeManager(
            executionContextManager: DefaultExecutionContextManager,
        ): ReliableEventDeliveryContextScopeManager = DefaultReliableEventDeliveryContextManager(
            executionContextManager,
            executionContextManager,
        )

        @Bean
        fun invocationScopeAccessor(): InvocationScopeAccessor = DefaultInvocationScopeManager()

        @Bean
        fun eventTypeCatalog(): EventTypeCatalog = object : EventTypeCatalog {
            override fun integrationEventTypes(): Set<Class<*>> = emptySet()
        }
    }
}

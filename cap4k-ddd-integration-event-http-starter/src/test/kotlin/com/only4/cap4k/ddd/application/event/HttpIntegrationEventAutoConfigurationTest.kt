package com.only4.cap4k.ddd.application.event

import com.only4.cap4k.ddd.application.event.capabilities.IntegrationEventHttpCallbackTriggerCapability
import com.only4.cap4k.ddd.application.event.capabilities.IntegrationEventHttpSubscribeCapability
import com.only4.cap4k.ddd.application.event.capabilities.IntegrationEventHttpUnsubscribeCapability
import com.only4.cap4k.ddd.core.application.event.IntegrationEventInterceptorManager
import com.only4.cap4k.ddd.core.application.context.ExecutionContextAccessor
import com.only4.cap4k.ddd.core.application.context.ExecutionContextCodecRegistry
import com.only4.cap4k.ddd.core.application.context.ExecutionContextScopeManager
import com.only4.cap4k.ddd.core.application.context.ExecutionContextSnapshot
import com.only4.cap4k.ddd.core.application.invocation.InvocationScopeAccessor
import com.only4.cap4k.ddd.core.application.event.IntegrationEventPublisher
import com.only4.cap4k.ddd.core.application.event.IntegrationEventSupervisor
import com.only4.cap4k.ddd.core.domain.event.EventPublisher
import com.only4.cap4k.ddd.core.domain.event.EventRecordRepository
import com.only4.cap4k.ddd.core.domain.event.EventHandlerDispatcher
import com.only4.cap4k.ddd.core.domain.event.EventTypeCatalog
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.WebApplicationContextRunner
import java.util.function.Supplier

class HttpIntegrationEventAutoConfigurationTest {
    private fun contextRunner(includeEventRecordRepository: Boolean): WebApplicationContextRunner {
        var runner = WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(HttpIntegrationEventAutoConfiguration::class.java))
            .withBean(EventPublisher::class.java, Supplier { mock(EventPublisher::class.java) })
            .withBean(
                IntegrationEventInterceptorManager::class.java,
                Supplier { mock(IntegrationEventInterceptorManager::class.java) },
            )
            .withBean(EventHandlerDispatcher::class.java, Supplier { mock(EventHandlerDispatcher::class.java) })
            .withBean(
                ExecutionContextAccessor::class.java,
                Supplier { ExecutionContextAccessor { ExecutionContextSnapshot.EMPTY } },
            )
            .withBean(
                ExecutionContextScopeManager::class.java,
                Supplier { ExecutionContextScopeManager { AutoCloseable { } } },
            )
            .withBean(ExecutionContextCodecRegistry::class.java, Supplier { ExecutionContextCodecRegistry(emptyList()) })
            .withBean(InvocationScopeAccessor::class.java, Supplier { InvocationScopeAccessor { null } })
            .withBean(
                EventTypeCatalog::class.java,
                Supplier {
                    object : EventTypeCatalog {
                        override fun integrationEventTypes(): Set<Class<*>> = emptySet()
                    }
                },
            )
        if (includeEventRecordRepository) {
            runner = runner.withBean(
                EventRecordRepository::class.java,
                Supplier { mock(EventRecordRepository::class.java) },
            )
        }
        return runner
    }

    @Test
    fun `installed http starter fails when EventRecordRepository is missing`() {
        contextRunner(includeEventRecordRepository = false).run { context ->
            val failure = context.startupFailure
            assertNotNull(failure)
            val failureMessages = generateSequence(failure) { it.cause }
                .mapNotNull(Throwable::message)
                .joinToString("\n")
            assertTrue(failureMessages.contains(EventRecordRepository::class.java.name), failureMessages)
        }
    }

    @Test
    fun `http starter registers transport capability and all endpoints`() {
        contextRunner(includeEventRecordRepository = true).run { context ->
            assertTrue(context.startupFailure == null, context.startupFailure?.stackTraceToString())
            assertEquals(1, context.getBeansOfType(IntegrationEventSupervisor::class.java).size)
            assertEquals(1, context.getBeansOfType(IntegrationEventPublisher::class.java).size)
            assertEquals(1, context.getBeansOfType(HttpIntegrationEventSubscriberAdapter::class.java).size)
            assertEquals(1, context.getBeansOfType(HttpIntegrationEventSubscriberRegister::class.java).size)
            assertEquals(1, context.getBeansOfType(IntegrationEventHttpCallbackTriggerCapability.Handler::class.java).size)
            assertEquals(1, context.getBeansOfType(IntegrationEventHttpSubscribeCapability.Handler::class.java).size)
            assertEquals(1, context.getBeansOfType(IntegrationEventHttpUnsubscribeCapability.Handler::class.java).size)
            listOf(
                HttpIntegrationEventAutoConfiguration.SUBSCRIBE_PATH,
                HttpIntegrationEventAutoConfiguration.UNSUBSCRIBE_PATH,
                HttpIntegrationEventAutoConfiguration.EVENTS_PATH,
                HttpIntegrationEventAutoConfiguration.SUBSCRIBERS_PATH,
                HttpIntegrationEventAutoConfiguration.CONSUME_PATH,
            ).forEach { beanName -> assertTrue(context.containsBean(beanName), beanName) }
        }
    }
}

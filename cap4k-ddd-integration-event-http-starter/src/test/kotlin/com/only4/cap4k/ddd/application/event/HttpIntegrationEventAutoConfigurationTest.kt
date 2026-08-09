package com.only4.cap4k.ddd.application.event

import com.only4.cap4k.ddd.core.application.event.IntegrationEventInterceptorManager
import com.only4.cap4k.ddd.core.application.context.ExecutionContextAccessor
import com.only4.cap4k.ddd.core.application.context.ExecutionContextCodecRegistry
import com.only4.cap4k.ddd.core.application.context.ExecutionContextScopeManager
import com.only4.cap4k.ddd.core.application.context.ExecutionContextSnapshot
import com.only4.cap4k.ddd.core.application.invocation.InvocationScopeAccessor
import com.only4.cap4k.ddd.core.application.event.IntegrationEventPublisher
import com.only4.cap4k.ddd.core.application.event.IntegrationEventSupervisor
import com.only4.cap4k.ddd.core.application.event.IntegrationEventRouteResolver
import com.only4.cap4k.ddd.core.application.event.annotation.IntegrationEvent
import com.only4.cap4k.ddd.core.domain.event.ReliableEventCoordinator
import com.only4.cap4k.ddd.core.domain.event.EventRecordRepository
import com.only4.cap4k.ddd.core.domain.event.EventHandlerDispatcher
import com.only4.cap4k.ddd.core.domain.event.InboundIntegrationEventRegistrationView
import com.only4.cap4k.ddd.core.domain.event.ReliableEventDeliveryContextScopeManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.WebApplicationContextRunner
import java.util.function.Supplier

class HttpIntegrationEventAutoConfigurationTest {
    private fun contextRunner(
        includeEventRecordRepository: Boolean,
        registrationView: InboundIntegrationEventRegistrationView = EmptyRegistrationView,
    ): WebApplicationContextRunner {
        var runner = WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(HttpIntegrationEventAutoConfiguration::class.java))
            .withBean(ReliableEventCoordinator::class.java, Supplier { mock(ReliableEventCoordinator::class.java) })
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
            .withBean(
                ReliableEventDeliveryContextScopeManager::class.java,
                Supplier { mock(ReliableEventDeliveryContextScopeManager::class.java) },
            )
            .withBean(InvocationScopeAccessor::class.java, Supplier { InvocationScopeAccessor { null } })
            .withBean(
                InboundIntegrationEventRegistrationView::class.java,
                Supplier { registrationView },
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
    fun `active http transport rejects blank event names during startup`() {
        contextRunner(
            includeEventRecordRepository = true,
            registrationView = BlankRegistrationView,
        ).run { context ->
            val failureMessages = failureMessages(context.startupFailure)
            assertTrue(failureMessages.contains("must declare a non-blank event name"), failureMessages)
        }
    }

    @Test
    fun `active http transport rejects duplicate event names during startup`() {
        contextRunner(
            includeEventRecordRepository = true,
            registrationView = DuplicateRegistrationView,
        ).run { context ->
            val failureMessages = failureMessages(context.startupFailure)
            assertTrue(
                failureMessages.contains("Integration Event 'http.duplicate' resolves to multiple payload types"),
                failureMessages,
            )
        }
    }

    @Test
    fun `http starter registers static route publisher subscriber adapter and consume endpoint`() {
        contextRunner(includeEventRecordRepository = true).run { context ->
            assertTrue(context.startupFailure == null, context.startupFailure?.stackTraceToString())
            assertEquals(1, context.getBeansOfType(IntegrationEventSupervisor::class.java).size)
            assertEquals(1, context.getBeansOfType(IntegrationEventPublisher::class.java).size)
            assertEquals(1, context.getBeansOfType(HttpIntegrationEventSubscriberAdapter::class.java).size)
            assertEquals(1, context.getBeansOfType(IntegrationEventRouteResolver::class.java).size)
            assertTrue(context.containsBean(HttpIntegrationEventAutoConfiguration.CONSUME_PATH))
        }
    }

    private fun failureMessages(failure: Throwable?): String = generateSequence(requireNotNull(failure)) { it.cause }
        .mapNotNull(Throwable::message)
        .joinToString("\n")

    private object EmptyRegistrationView : InboundIntegrationEventRegistrationView {
        override fun integrationEventTypes(): Set<Class<*>> = emptySet()
    }

    private object BlankRegistrationView : InboundIntegrationEventRegistrationView {
        override fun integrationEventTypes(): Set<Class<*>> = setOf(BlankHttpEvent::class.java)
    }

    private object DuplicateRegistrationView : InboundIntegrationEventRegistrationView {
        override fun integrationEventTypes(): Set<Class<*>> = setOf(
            FirstDuplicateHttpEvent::class.java,
            SecondDuplicateHttpEvent::class.java,
        )
    }

    @IntegrationEvent("   ")
    private data class BlankHttpEvent(val value: String)

    @IntegrationEvent("http.duplicate")
    private data class FirstDuplicateHttpEvent(val value: String)

    @IntegrationEvent("http.duplicate")
    private data class SecondDuplicateHttpEvent(val value: String)
}

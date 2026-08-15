package com.only4.cap4k.ddd.application.event

import com.only4.cap4k.ddd.core.application.event.IntegrationEventInterceptorManager
import com.only4.cap4k.ddd.core.application.event.IntegrationEventInterceptor
import com.only4.cap4k.ddd.core.application.context.ExecutionContextAccessor
import com.only4.cap4k.ddd.core.application.context.ExecutionContextCodecRegistry
import com.only4.cap4k.ddd.core.application.context.ExecutionContextScopeManager
import com.only4.cap4k.ddd.core.application.context.ExecutionContextSnapshot
import com.only4.cap4k.ddd.core.application.invocation.InvocationScopeAccessor
import com.only4.cap4k.ddd.core.application.event.IntegrationEventPublisher
import com.only4.cap4k.ddd.core.application.event.IntegrationEventSupervisor
import com.only4.cap4k.ddd.core.application.event.IntegrationEventRouteResolver
import com.only4.cap4k.contract.IntegrationEvent
import com.only4.cap4k.ddd.core.domain.event.ReliableEventCoordinator
import com.only4.cap4k.ddd.core.domain.event.EventRecordRepository
import com.only4.cap4k.ddd.core.domain.event.EventHandlerDispatcher
import com.only4.cap4k.ddd.core.domain.event.InboundIntegrationEventRegistrationView
import com.only4.cap4k.ddd.core.domain.event.ReliableEventDeliveryContextScopeManager
import com.only4.cap4k.ddd.core.application.provider.InMemoryRuntimeProviderStateRegistry
import com.only4.cap4k.ddd.core.application.provider.RuntimeProviderStateRegistry
import com.only4.cap4k.ddd.core.application.provider.RuntimeProviderState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.WebApplicationContextRunner
import java.net.URI
import java.util.function.Supplier

class HttpIntegrationEventAutoConfigurationTest {
    private fun contextRunner(
        includeEventRecordRepository: Boolean,
        registrationView: InboundIntegrationEventRegistrationView = EmptyRegistrationView,
        runtimeProviderStateRegistry: RuntimeProviderStateRegistry = InMemoryRuntimeProviderStateRegistry(),
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
                RuntimeProviderStateRegistry::class.java,
                Supplier { runtimeProviderStateRegistry },
            )
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
            assertEquals(1, context.getBeansOfType(IntegrationEventInterceptor::class.java).size)
            assertTrue(context.containsBean(HttpIntegrationEventAutoConfiguration.CONSUME_PATH))
            val providerFact = context.getBean(RuntimeProviderStateRegistry::class.java).snapshot().single()
            assertEquals(HttpIntegrationEventAutoConfiguration.HTTP_PROVIDER_ID, providerFact.providerId)
            assertEquals(RuntimeProviderState.RECOVERING, providerFact.state)
            assertEquals("enrolled", providerFact.category)
        }
    }


    @Test
    fun `http provider registration closes with the Spring context and permits re-registration`() {
        val registry = InMemoryRuntimeProviderStateRegistry()

        contextRunner(
            includeEventRecordRepository = true,
            runtimeProviderStateRegistry = registry,
        ).run { context ->
            assertTrue(context.startupFailure == null, context.startupFailure?.stackTraceToString())
            val fact = registry.snapshot().single()
            assertEquals(HttpIntegrationEventAutoConfiguration.HTTP_PROVIDER_ID, fact.providerId)
            assertEquals(RuntimeProviderState.RECOVERING, fact.state)
            assertEquals("enrolled", fact.category)
        }

        assertTrue(registry.snapshot().isEmpty())
        registry.register(HttpIntegrationEventAutoConfiguration.HTTP_PROVIDER_ID).close()
        assertTrue(registry.snapshot().isEmpty())
    }

    @Test
    fun `http route map binds bracketed event names and normalizes the configured base path`() {
        contextRunner(includeEventRecordRepository = true)
            .withPropertyValues(
                "cap4k.ddd.integration.event.http.routes[content.published]=http://localhost:8082/context/",
            )
            .run { context ->
                assertTrue(context.startupFailure == null, context.startupFailure?.stackTraceToString())
                @Suppress("UNCHECKED_CAST")
                val resolver = context.getBean(IntegrationEventRouteResolver::class.java)
                    as IntegrationEventRouteResolver<URI>
                assertEquals(URI("http://localhost:8082/context"), resolver.resolve("content.published"))
            }
    }

    @Test
    fun `http route enrollment rejects invalid configured base URIs`() {
        listOf(
            " " to "must not be blank",
            "/relative" to "absolute http or https",
            "ftp://localhost/events" to "absolute http or https",
            "http://localhost/events?token=value" to "must not contain query or fragment",
        ).forEach { (route, expectedMessage) ->
            contextRunner(includeEventRecordRepository = true)
                .withPropertyValues("cap4k.ddd.integration.event.http.routes[content.published]=$route")
                .run { context ->
                    val failureMessages = failureMessages(context.startupFailure)
                    assertTrue(failureMessages.contains(expectedMessage), failureMessages)
                }
        }
    }

    @Test
    fun `http provider rejects invalid deterministic executor configuration during startup`() {
        contextRunner(includeEventRecordRepository = true)
            .withPropertyValues("cap4k.ddd.integration.event.http.publish-thread-pool-size=0")
            .run { context ->
                val failureMessages = failureMessages(context.startupFailure)
                assertTrue(failureMessages.contains("publish thread pool size must be positive"), failureMessages)
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

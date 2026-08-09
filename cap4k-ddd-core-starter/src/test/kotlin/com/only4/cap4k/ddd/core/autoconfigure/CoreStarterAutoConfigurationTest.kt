package com.only4.cap4k.ddd.core.autoconfigure

import com.only4.cap4k.ddd.core.ProviderUnavailableException
import com.only4.cap4k.ddd.core.Mediator
import com.only4.cap4k.ddd.core.application.CommandUnitOfWorkCoordinator
import com.only4.cap4k.ddd.core.application.capability.CapabilityCall
import com.only4.cap4k.ddd.core.application.capability.CapabilityHandler
import com.only4.cap4k.ddd.core.application.command.Command
import com.only4.cap4k.ddd.core.application.command.CommandHandler
import com.only4.cap4k.ddd.core.application.command.CommandRecordRepository
import com.only4.cap4k.ddd.core.application.event.IntegrationEventManager
import com.only4.cap4k.ddd.core.application.event.IntegrationEventSupervisorSupport
import com.only4.cap4k.ddd.core.application.event.IntegrationEventPublisher
import com.only4.cap4k.ddd.core.application.event.annotation.IntegrationEvent
import com.only4.cap4k.ddd.core.application.context.ExecutionContextSnapshot
import com.only4.cap4k.ddd.core.domain.event.DomainEventSupervisor
import com.only4.cap4k.ddd.core.domain.event.EventHandlerDispatcher
import com.only4.cap4k.ddd.core.domain.event.EventTypeCatalog
import com.only4.cap4k.ddd.core.domain.event.InboundIntegrationEventRegistrationView
import com.only4.cap4k.ddd.core.domain.event.impl.Cap4kEventHandlerRegistry
import com.only4.cap4k.ddd.core.domain.event.ReliableEventDeliveryContextAccessor
import com.only4.cap4k.ddd.core.domain.event.ReliableEventDeliveryContextScopeManager
import com.only4.cap4k.ddd.core.domain.event.ReliableDomainEventProvider
import com.only4.cap4k.ddd.core.domain.event.annotation.DomainEvent
import com.only4.cap4k.ddd.core.domain.repo.RepositorySupervisor
import com.only4.cap4k.ddd.core.application.query.Query
import com.only4.cap4k.ddd.core.application.query.QueryHandler
import com.only4.cap4k.ddd.core.application.query.QueryExecution
import com.only4.cap4k.ddd.core.application.async.ApplicationAsyncExecutor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.mock
import org.springframework.beans.factory.ListableBeanFactory
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.event.EventListener
import java.time.LocalDateTime

import java.util.function.Supplier
class CoreStarterAutoConfigurationTest {
    private val contextRunner = ApplicationContextRunner()
        .withConfiguration(
            AutoConfigurations.of(
                CoreIdAutoConfiguration::class.java,
                CoreRuntimeAutoConfiguration::class.java,
                CoreDomainEventAutoConfiguration::class.java,
            )
        )
        .withBean(TestCommandHandler::class.java)
        .withBean(TestUnitOfWork::class.java)
        .withBean(TestQueryExecution::class.java)
        .withBean(TestQueryHandler::class.java)
        .withBean(TestCapabilityHandler::class.java)
        .withBean(TestEventListener::class.java)

    @Test
    fun `core starter provides distinct application dispatchers uuid7 ioc and local event without reliable stores`() {
        contextRunner.run { context ->
            assertTrue(context.startupFailure == null)
            assertTrue(IntegrationEventSupervisorSupport.managerOrNull() == null)
            assertTrue(context.getBeansOfType(CommandRecordRepository::class.java).isEmpty())
            assertEquals(1, context.getBeansOfType(EventHandlerDispatcher::class.java).size)
            assertSame(
                context.getBean(ReliableEventDeliveryContextAccessor::class.java),
                context.getBean(ReliableEventDeliveryContextScopeManager::class.java),
            )
            assertEquals(
                setOf(TestIntegrationEvent::class.java),
                context.getBean(EventTypeCatalog::class.java).integrationEventTypes(),
            )
            assertEquals("command:ok", Mediator.commands.send(TestCommand("ok")))
            assertEquals("query:ok", Mediator.queries.ask(TestQuery("ok")))
            assertEquals("query:async", Mediator.queries.askAsync(TestQuery("async")).toCompletableFuture().get())
            assertEquals("capability:ok", Mediator.capabilities.call(TestCapability("ok")))
            assertEquals(
                "capability:async",
                Mediator.capabilities.callAsync(TestCapability("async")).toCompletableFuture().get(),
            )
            assertTrue(Mediator.identifiers.next("uuid7", String::class).isNotBlank())
            assertEquals(context, Mediator.ioc)
            assertNotSame(
                context.getBean(
                    CoreRuntimeAutoConfiguration.QUERY_ASYNC_EXECUTOR_BEAN,
                    ApplicationAsyncExecutor::class.java,
                ),
                context.getBean(
                    CoreRuntimeAutoConfiguration.CAPABILITY_ASYNC_EXECUTOR_BEAN,
                    ApplicationAsyncExecutor::class.java,
                ),
            )

            val listener = context.getBean(TestEventListener::class.java)
            val entity = Any()
            val event = TestEvent("created")
            DomainEventSupervisor.instance.attach(event, entity)
            DomainEventSupervisor.manager.release(setOf(entity))
            assertEquals(listOf(event), listener.events)

            val exception = assertThrows<ProviderUnavailableException> {
                Mediator.commands.enqueue(TestCommand("later"))
            }
            assertEquals("reliable-commands", exception.providerName)
        }
    }

    @Test
    fun `core starter fails startup when command dispatcher has multiple providers`() {
        ApplicationContextRunner()
            .withConfiguration(
                AutoConfigurations.of(
                    CoreIdAutoConfiguration::class.java,
                    CoreRuntimeAutoConfiguration::class.java,
                    CoreDomainEventAutoConfiguration::class.java,
                )
            )
            .withBean("commandA", ConflictingCommandSupervisor::class.java)
            .withBean("commandB", ConflictingCommandSupervisor::class.java)
            .run { context ->
                val failure = requireNotNull(context.startupFailure).stackTraceToString()
                assertTrue(failure.contains("cap4k provider 'commands' requires exactly one implementation"))
                assertTrue(failure.contains("found [commandA, commandB]"))
            }
    }

    @Test
    fun `runtime binder fails startup when a required provider is missing`() {
        ApplicationContextRunner()
            .withUserConfiguration(MissingRequiredProviderConfiguration::class.java)
            .run { context ->
                val failure = requireNotNull(context.startupFailure).stackTraceToString()
                assertTrue(failure.contains("cap4k provider 'identifiers' requires exactly one implementation"))
                assertTrue(failure.contains("found []"))
            }
    }

    @Test
    fun `inbound integration event view intersects catalog with real local handler descriptors`() {
        contextRunner
            .withBean(
                EventTypeCatalog::class.java,
                Supplier {
                    object : EventTypeCatalog {
                        override fun integrationEventTypes(): Set<Class<*>> = setOf(
                            TestIntegrationEvent::class.java,
                            OrphanIntegrationEvent::class.java,
                        )
                    }
                },
            )
            .run { context ->
                assertTrue(context.startupFailure == null)
                assertEquals(
                    setOf(TestIntegrationEvent::class.java),
                    context.getBean(InboundIntegrationEventRegistrationView::class.java).integrationEventTypes(),
                )
                val handlers = context.getBean(Cap4kEventHandlerRegistry::class.java)
                    .handlersFor(TestIntegrationEvent::class.java)
                assertEquals(
                    setOf("onIntegration", "onIntegrationSecond"),
                    handlers.map { it.descriptor.method.name }.toSet(),
                )
                assertTrue(context.getBean(Cap4kEventHandlerRegistry::class.java)
                    .handlersFor(OrphanIntegrationEvent::class.java)
                    .isEmpty())
            }
    }

    @Test
    fun `optional integration event manager conflict fails instead of degrading to absent`() {
        contextRunner
            .withBean("integrationManagerB", TestIntegrationEventManager::class.java)
            .withBean("integrationManagerA", TestIntegrationEventManager::class.java)
            .run { context ->
                val failure = requireNotNull(context.startupFailure).stackTraceToString()
                assertTrue(failure.contains("cap4k provider 'integration-event-manager' allows at most one implementation"))
                assertTrue(failure.contains("found [integrationManagerA, integrationManagerB]"))
            }
    }

    @Test
    fun `optional integration event publisher conflict reports sorted bean identities`() {
        contextRunner
            .withInitializer { context ->
                context.beanFactory.registerSingleton("publisherB", mock(IntegrationEventPublisher::class.java))
                context.beanFactory.registerSingleton("publisherA", mock(IntegrationEventPublisher::class.java))
            }
            .run { context ->
                val failure = requireNotNull(context.startupFailure).stackTraceToString()
                assertTrue(
                    failure.contains("cap4k provider 'integration-event-transport' allows at most one implementation")
                )
                assertTrue(failure.contains("found [publisherA, publisherB]"))
            }
    }

    @Test
    fun `optional reliable domain event provider conflict reports sorted bean identities`() {
        contextRunner
            .withBean("reliableProviderB", TestReliableDomainEventProvider::class.java)
            .withBean("reliableProviderA", TestReliableDomainEventProvider::class.java)
            .run { context ->
                val failure = requireNotNull(context.startupFailure).stackTraceToString()
                assertTrue(failure.contains("cap4k provider 'reliable-domain-events' allows at most one implementation"))
                assertTrue(failure.contains("found [reliableProviderA, reliableProviderB]"))
            }
    }

    @Test
    fun `optional repository provider conflict reports sorted bean identities`() {
        contextRunner
            .withInitializer { context ->
                context.beanFactory.registerSingleton("repositoryB", mock(RepositorySupervisor::class.java))
                context.beanFactory.registerSingleton("repositoryA", mock(RepositorySupervisor::class.java))
            }
            .run { context ->
                val failure = requireNotNull(context.startupFailure).stackTraceToString()
                assertTrue(failure.contains("cap4k provider 'repositories' allows at most one implementation"))
                assertTrue(failure.contains("found [repositoryA, repositoryB]"))
            }
    }

    @Test
    fun `one optional provider is bound for the context and released on close`() {
        contextRunner
            .withBean("integrationManager", TestIntegrationEventManager::class.java)
            .run { context ->
                assertTrue(context.startupFailure == null)
                assertSame(
                    context.getBean("integrationManager", IntegrationEventManager::class.java),
                    IntegrationEventSupervisorSupport.manager,
                )
            }

        assertTrue(IntegrationEventSupervisorSupport.managerOrNull() == null)
    }

    @Test
    fun `provider registrations are released between sequential application contexts`() {
        contextRunner.run { first ->
            assertTrue(first.startupFailure == null)
            assertEquals(first, Mediator.ioc)
        }

        contextRunner.run { second ->
            assertTrue(second.startupFailure == null)
            assertEquals(second, Mediator.ioc)
        }
    }

    @Test
    fun `a second active context cannot replace registry providers owned by the first`() {
        contextRunner.run { first ->
            assertTrue(first.startupFailure == null)
            assertEquals(first, Mediator.ioc)

            contextRunner.run { second ->
                val failure = requireNotNull(second.startupFailure).stackTraceToString()
                assertTrue(failure.contains("cap4k provider 'ioc' is already configured"))
                assertTrue(failure.contains("cannot register"))
                assertEquals(first, Mediator.ioc)
            }

            assertEquals(first, Mediator.ioc)
        }
    }

    data class TestCommand(val value: String) : Command<String>

    class TestCommandHandler : CommandHandler<TestCommand, String> {
        override fun handle(command: TestCommand): String = "command:${command.value}"
    }

    data class TestQuery(val value: String) : Query<String>

    class TestQueryHandler : QueryHandler<TestQuery, String> {
        override fun handle(query: TestQuery): String = "query:${query.value}"
    }

    data class TestCapability(val value: String) : CapabilityCall<String>

    class TestCapabilityHandler : CapabilityHandler<TestCapability, String> {
        override fun call(request: TestCapability): String = "capability:${request.value}"
    }

    class TestUnitOfWork : CommandUnitOfWorkCoordinator {
        private var depth = 0
        override val active: Boolean get() = depth > 0
        override fun <RESULT> execute(block: () -> RESULT): RESULT {
            depth++
            return try { block() } finally { depth-- }
        }
    }

    class TestQueryExecution : QueryExecution {
        private var depth = 0
        override val active: Boolean get() = depth > 0
        override fun <RESULT> execute(block: () -> RESULT): RESULT {
            depth++
            return try { block() } finally { depth-- }
        }
    }

    class ConflictingCommandSupervisor : com.only4.cap4k.ddd.core.application.command.CommandSupervisor {
        override fun <COMMAND : Command<RESULT>, RESULT : Any> send(command: COMMAND): RESULT =
            error("not invoked")
    }

    class TestIntegrationEventManager : IntegrationEventManager {
        override fun release() = Unit
    }

    class TestReliableDomainEventProvider : ReliableDomainEventProvider {
        override fun publish(
            eventPayload: Any,
            schedule: LocalDateTime,
            executionContext: ExecutionContextSnapshot,
        ) = Unit
    }

    @DomainEvent
    data class TestEvent(val value: String)

    @IntegrationEvent("test.integration")
    data class TestIntegrationEvent(val value: String)
    @IntegrationEvent("test.orphan")
    data class OrphanIntegrationEvent(val value: String)


    class TestEventListener {
        val events = mutableListOf<TestEvent>()

        @EventListener
        fun on(event: TestEvent) {
            events += event
        }

        @EventListener
        fun onIntegration(event: TestIntegrationEvent) = Unit

        @EventListener
        fun onIntegrationSecond(event: TestIntegrationEvent) = Unit
    }

    @Configuration(proxyBeanMethods = false)
    class MissingRequiredProviderConfiguration {
        @Bean
        fun runtimeProviderBinder(
            applicationContext: ApplicationContext,
            beanFactory: ListableBeanFactory,
        ): RuntimeProviderBinder = RuntimeProviderBinder(applicationContext, beanFactory)
    }
}

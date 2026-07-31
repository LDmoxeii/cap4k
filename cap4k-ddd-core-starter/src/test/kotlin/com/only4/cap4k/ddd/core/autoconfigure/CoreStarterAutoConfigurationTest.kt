package com.only4.cap4k.ddd.core.autoconfigure

import com.only4.cap4k.ddd.core.ProviderUnavailableException
import com.only4.cap4k.ddd.core.Mediator
import com.only4.cap4k.ddd.core.application.CommandUnitOfWorkCoordinator
import com.only4.cap4k.ddd.core.application.capability.CapabilityCall
import com.only4.cap4k.ddd.core.application.capability.CapabilityHandler
import com.only4.cap4k.ddd.core.application.command.Command
import com.only4.cap4k.ddd.core.application.command.CommandHandler
import com.only4.cap4k.ddd.core.application.command.CommandRecordRepository
import com.only4.cap4k.ddd.core.domain.event.DomainEventSupervisor
import com.only4.cap4k.ddd.core.domain.event.EventSubscriberManager
import com.only4.cap4k.ddd.core.application.query.Query
import com.only4.cap4k.ddd.core.application.query.QueryHandler
import com.only4.cap4k.ddd.core.application.query.QueryExecution
import com.only4.cap4k.ddd.core.application.async.ApplicationAsyncExecutor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.event.EventListener

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
            assertTrue(context.getBeansOfType(CommandRecordRepository::class.java).isEmpty())
            assertEquals(1, context.getBeansOfType(EventSubscriberManager::class.java).size)
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
                assertTrue(failure.contains("commandA"))
                assertTrue(failure.contains("commandB"))
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

    data class TestEvent(val value: String)

    class TestEventListener {
        val events = mutableListOf<TestEvent>()

        @EventListener
        fun on(event: TestEvent) {
            events += event
        }
    }
}

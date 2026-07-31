package com.only4.cap4k.ddd.core.application.impl

import com.only4.cap4k.ddd.core.application.async.ApplicationAsyncExecutor
import com.only4.cap4k.ddd.core.application.async.BoundedApplicationAsyncExecutor
import com.only4.cap4k.ddd.core.application.CommandUnitOfWorkCoordinator
import com.only4.cap4k.ddd.core.application.capability.CapabilityCall
import com.only4.cap4k.ddd.core.application.capability.CapabilityHandler
import com.only4.cap4k.ddd.core.application.capability.impl.DefaultCapabilitySupervisor
import com.only4.cap4k.ddd.core.application.command.Command
import com.only4.cap4k.ddd.core.application.command.CommandHandler
import com.only4.cap4k.ddd.core.application.command.CommandInterceptor
import com.only4.cap4k.ddd.core.application.command.impl.DefaultCommandSupervisor
import com.only4.cap4k.ddd.core.application.context.DefaultExecutionContextManager
import com.only4.cap4k.ddd.core.application.context.ExecutionContextElement
import com.only4.cap4k.ddd.core.application.context.ExecutionContextKey
import com.only4.cap4k.ddd.core.application.context.ExecutionContextPropagation
import com.only4.cap4k.ddd.core.application.context.ExecutionContextSnapshot
import com.only4.cap4k.ddd.core.application.invocation.DefaultInvocationScopeManager
import com.only4.cap4k.ddd.core.application.invocation.InvocationKind
import com.only4.cap4k.ddd.core.application.invocation.InvocationNotAllowedException
import com.only4.cap4k.ddd.core.application.invocation.InvocationPolicy
import com.only4.cap4k.ddd.core.application.query.Query
import com.only4.cap4k.ddd.core.application.query.QueryExecution
import com.only4.cap4k.ddd.core.application.query.QueryHandler
import com.only4.cap4k.ddd.core.application.query.impl.DefaultQuerySupervisor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.concurrent.ExecutionException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class DefaultApplicationSupervisorsTest {
    @Test
    fun `command dispatcher invokes matching interceptors around handler`() {
        val calls = mutableListOf<String>()
        val handler = object : CommandHandler<TestCommand, String> {
            override fun handle(command: TestCommand): String {
                calls += "handle:${command.value}"
                return "result:${command.value}"
            }
        }
        val interceptor = object : CommandInterceptor<TestCommand, String> {
            override fun beforeCommand(command: TestCommand) {
                calls += "before:${command.value}"
            }

            override fun afterCommand(command: TestCommand, result: String) {
                calls += "after:$result"
            }
        }
        val runtime = TestRuntime()
        val unitOfWork = RecordingUnitOfWork()
        val supervisor = runtime.command(listOf(handler), listOf(interceptor), unitOfWork)

        assertEquals("result:ok", supervisor.send(TestCommand("ok")))
        assertEquals(listOf("before:ok", "handle:ok", "after:result:ok"), calls)
        assertEquals(1, unitOfWork.executionCount)
        assertNull(runtime.invocationScopes.current())
    }

    @Test
    fun `query and capability use independent dispatchers and one blocking handler shape`() {
        TestRuntime().use { runtime ->
            val querySupervisor = runtime.query(
                listOf(object : QueryHandler<TestQuery, String> {
                    override fun handle(query: TestQuery): String = "query:${query.value}"
                }),
            )
            val capabilitySupervisor = runtime.capability(
                listOf(object : CapabilityHandler<TestCapability, String> {
                    override fun call(request: TestCapability): String = "capability:${request.value}"
                }),
            )

            assertEquals("query:ok", querySupervisor.ask(TestQuery("ok")))
            assertEquals("capability:ok", capabilitySupervisor.call(TestCapability("ok")))
            assertEquals("query:async", querySupervisor.askAsync(TestQuery("async")).toCompletableFuture().get())
            assertEquals(
                "capability:async",
                capabilitySupervisor.callAsync(TestCapability("async")).toCompletableFuture().get(),
            )
        }
    }

    @Test
    fun `command cannot query and query cannot command`() {
        TestRuntime().use { runtime ->
            lateinit var querySupervisor: DefaultQuerySupervisor
            querySupervisor = runtime.query(
                listOf(object : QueryHandler<TestQuery, String> {
                    override fun handle(query: TestQuery): String = query.value
                }),
            )
            val commandCallingQuery = runtime.command(
                listOf(object : CommandHandler<TestCommand, String> {
                    override fun handle(command: TestCommand): String =
                        querySupervisor.ask(TestQuery(command.value))
                }),
            )

            val commandFailure = assertThrows<InvocationNotAllowedException> {
                commandCallingQuery.send(TestCommand("blocked"))
            }
            assertEquals(InvocationKind.COMMAND, commandFailure.currentKind)
            assertEquals(InvocationKind.QUERY, commandFailure.requestedKind)

            lateinit var commandSupervisor: DefaultCommandSupervisor
            commandSupervisor = runtime.command(listOf(object : CommandHandler<TestCommand, String> {
                override fun handle(command: TestCommand): String = command.value
            }))
            val queryCallingCommand = runtime.query(
                listOf(object : QueryHandler<TestQuery, String> {
                    override fun handle(query: TestQuery): String =
                        commandSupervisor.send(TestCommand(query.value))
                }),
            )

            val queryFailure = assertThrows<InvocationNotAllowedException> {
                queryCallingCommand.ask(TestQuery("blocked"))
            }
            assertEquals(InvocationKind.QUERY, queryFailure.currentKind)
            assertEquals(InvocationKind.COMMAND, queryFailure.requestedKind)
        }
    }

    @Test
    fun `nested async query fails through CompletionStage`() {
        TestRuntime().use { runtime ->
            lateinit var supervisor: DefaultQuerySupervisor
            supervisor = runtime.query(
                listOf(object : QueryHandler<TestQuery, String> {
                    override fun handle(query: TestQuery): String {
                        return if (query.value == "outer") {
                            supervisor.askAsync(TestQuery("inner")).toCompletableFuture().get()
                        } else {
                            query.value
                        }
                    }
                }),
            )

            val failure = assertThrows<ExecutionException> {
                supervisor.ask(TestQuery("outer"))
            }
            val policyFailure = failure.cause as InvocationNotAllowedException
            assertEquals(InvocationKind.QUERY, policyFailure.currentKind)
            assertTrue(policyFailure.asynchronous)
        }
    }

    @Test
    fun `caller runs installs Capability scope above Command scope`() {
        TestRuntime().use { runtime ->
            val capabilitySupervisor = runtime.capability(
                handlers = listOf(object : CapabilityHandler<TestCapability, String> {
                    override fun call(request: TestCapability): String {
                        assertEquals(InvocationKind.CAPABILITY, runtime.invocationScopes.current())
                        return request.value
                    }
                }),
                asyncExecutor = DirectAsyncExecutor,
            )
            val commandSupervisor = runtime.command(
                listOf(object : CommandHandler<TestCommand, String> {
                    override fun handle(command: TestCommand): String {
                        assertEquals(InvocationKind.COMMAND, runtime.invocationScopes.current())
                        return capabilitySupervisor.callAsync(TestCapability(command.value)).toCompletableFuture().get()
                    }
                }),
            )

            assertEquals("inline", commandSupervisor.send(TestCommand("inline")))
            assertNull(runtime.invocationScopes.current())
        }
    }

    @Test
    fun `async invocation propagates execution context but closes target scopes before completion`() {
        val runtime = TestRuntime()
        runtime.use {
            val actorKey = ExecutionContextKey("actor", Actor::class.java)
            val handlerStarted = CountDownLatch(1)
            val releaseHandler = CountDownLatch(1)
            val supervisor = runtime.capability(
                listOf(object : CapabilityHandler<TestCapability, String> {
                    override fun call(request: TestCapability): String {
                        assertEquals(InvocationKind.CAPABILITY, runtime.invocationScopes.current())
                        val actor = runtime.executionContexts.current()[actorKey]!!.name
                        handlerStarted.countDown()
                        releaseHandler.await(5, TimeUnit.SECONDS)
                        return actor
                    }
                }),
            )
            val snapshot = ExecutionContextSnapshot.builder().put(actorKey, Actor("alice")).build()
            val outer = runtime.executionContexts.install(snapshot)
            val stage = try {
                supervisor.callAsync(TestCapability("ignored"))
            } finally {
                outer.close()
            }
            assertTrue(handlerStarted.await(5, TimeUnit.SECONDS))
            val completionContext = AtomicReference<ExecutionContextSnapshot>()
            val completionInvocation = AtomicReference<InvocationKind?>()
            val observed = stage.whenComplete { _, _ ->
                completionContext.set(runtime.executionContexts.current())
                completionInvocation.set(runtime.invocationScopes.current())
            }
            releaseHandler.countDown()

            assertEquals("alice", observed.toCompletableFuture().get(5, TimeUnit.SECONDS))
            assertTrue(completionContext.get().isEmpty)
            assertNull(completionInvocation.get())
            assertTrue(runtime.executionContexts.current().isEmpty)
            assertNull(runtime.invocationScopes.current())
        }
    }

    @Test
    fun `dispatcher resolves handler generic through an intermediate interface`() {
        TestRuntime().use { runtime ->
            val supervisor = runtime.command(listOf(IndirectCommandHandler()))
            assertEquals("indirect:ok", supervisor.send(TestCommand("ok")))
        }
    }

    @Test
    fun `duplicate handlers fail during initialization`() {
        TestRuntime().use { runtime ->
            val error = assertThrows<IllegalStateException> {
                runtime.command(listOf(FirstCommandHandler(), SecondCommandHandler()))
            }

            assertTrue(error.message.orEmpty().contains("Multiple command handlers"))
            assertTrue(error.message.orEmpty().contains(TestCommand::class.java.name))
        }
    }

    data class TestCommand(val value: String) : Command<String>

    data class TestQuery(val value: String) : Query<String>

    data class TestCapability(val value: String) : CapabilityCall<String>

    data class Actor(val name: String) : ExecutionContextElement

    interface IntermediateCommandHandler<C : Command<R>, R : Any> : CommandHandler<C, R>

    class IndirectCommandHandler : IntermediateCommandHandler<TestCommand, String> {
        override fun handle(command: TestCommand): String = "indirect:${command.value}"
    }

    class FirstCommandHandler : CommandHandler<TestCommand, String> {
        override fun handle(command: TestCommand): String = command.value
    }

    class SecondCommandHandler : CommandHandler<TestCommand, String> {
        override fun handle(command: TestCommand): String = command.value
    }

    private class TestRuntime : AutoCloseable {
        val executionContexts = DefaultExecutionContextManager()
        val executionContextPropagation = ExecutionContextPropagation(executionContexts, executionContexts)
        val invocationScopes = DefaultInvocationScopeManager()
        val invocationPolicy = InvocationPolicy(invocationScopes)
        private val queryExecutor = BoundedApplicationAsyncExecutor(1, 8, threadNamePrefix = "query-test-")
        private val capabilityExecutor = BoundedApplicationAsyncExecutor(1, 8, threadNamePrefix = "capability-test-")
        private val queryExecution = RecordingQueryExecution()

        fun command(
            handlers: List<CommandHandler<*, *>>,
            interceptors: List<CommandInterceptor<*, *>> = emptyList(),
            unitOfWork: CommandUnitOfWorkCoordinator = RecordingUnitOfWork(),
        ): DefaultCommandSupervisor = DefaultCommandSupervisor(
            handlers,
            interceptors,
            null,
            unitOfWorkProvider = { unitOfWork },
            invocationPolicy = invocationPolicy,
            invocationScopeManager = invocationScopes,
        ).apply { init() }

        fun query(handlers: List<QueryHandler<*, *>>): DefaultQuerySupervisor = DefaultQuerySupervisor(
            handlers = handlers,
            interceptors = emptyList(),
            validator = null,
            invocationPolicy = invocationPolicy,
            invocationScopeManager = invocationScopes,
            executionContextAccessor = executionContexts,
            executionContextPropagation = executionContextPropagation,
            asyncExecutor = queryExecutor,
            queryExecutionProvider = { queryExecution },
        ).apply { init() }

        fun capability(
            handlers: List<CapabilityHandler<*, *>>,
            asyncExecutor: ApplicationAsyncExecutor = capabilityExecutor,
        ): DefaultCapabilitySupervisor =
            DefaultCapabilitySupervisor(
                handlers = handlers,
                interceptors = emptyList(),
                validator = null,
                invocationPolicy = invocationPolicy,
                invocationScopeManager = invocationScopes,
                executionContextAccessor = executionContexts,
                executionContextPropagation = executionContextPropagation,
                asyncExecutor = asyncExecutor,
            ).apply { init() }

        override fun close() {
            queryExecutor.close()
            capabilityExecutor.close()
        }
    }

    private class RecordingQueryExecution : QueryExecution {
        private var depth = 0
        override val active: Boolean
            get() = depth > 0

        override fun <RESULT> execute(block: () -> RESULT): RESULT {
            depth++
            return try {
                block()
            } finally {
                depth--
            }
        }
    }

    private class RecordingUnitOfWork : CommandUnitOfWorkCoordinator {
        private var depth = 0
        override val active: Boolean
            get() = depth > 0
        var executionCount: Int = 0

        override fun <RESULT> execute(block: () -> RESULT): RESULT {
            executionCount += 1
            depth++
            return try {
                block()
            } finally {
                depth--
            }
        }
    }

    private object DirectAsyncExecutor : ApplicationAsyncExecutor {
        override fun <RESULT : Any> submit(task: () -> RESULT): CompletionStage<RESULT> {
            val result = CompletableFuture<RESULT>()
            try {
                result.complete(task())
            } catch (ex: Throwable) {
                result.completeExceptionally(ex)
            }
            return result
        }

        override fun close() = Unit
    }
}

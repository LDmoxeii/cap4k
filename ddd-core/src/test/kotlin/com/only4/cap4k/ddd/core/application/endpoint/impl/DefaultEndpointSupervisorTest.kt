package com.only4.cap4k.ddd.core.application.endpoint.impl

import com.only4.cap4k.contract.EndpointRequest
import com.only4.cap4k.ddd.core.Mediator
import com.only4.cap4k.ddd.core.application.CommandUnitOfWorkCoordinator
import com.only4.cap4k.ddd.core.application.async.ApplicationAsyncExecutor
import com.only4.cap4k.ddd.core.application.capability.CapabilityCall
import com.only4.cap4k.ddd.core.application.capability.CapabilityHandler
import com.only4.cap4k.ddd.core.application.capability.impl.DefaultCapabilitySupervisor
import com.only4.cap4k.ddd.core.application.command.Command
import com.only4.cap4k.ddd.core.application.command.CommandHandler
import com.only4.cap4k.ddd.core.application.command.impl.DefaultCommandSupervisor
import com.only4.cap4k.ddd.core.application.context.DefaultExecutionContextManager
import com.only4.cap4k.ddd.core.application.context.ExecutionContextElement
import com.only4.cap4k.ddd.core.application.context.ExecutionContextKey
import com.only4.cap4k.ddd.core.application.context.ExecutionContextSnapshot
import com.only4.cap4k.ddd.core.application.context.ExecutionContextPropagation
import com.only4.cap4k.ddd.core.application.endpoint.EndpointHandler
import com.only4.cap4k.ddd.core.application.endpoint.EndpointSupervisorSupport
import com.only4.cap4k.ddd.core.application.invocation.DefaultInvocationScopeManager
import com.only4.cap4k.ddd.core.application.invocation.InvocationKind
import com.only4.cap4k.ddd.core.application.invocation.InvocationNotAllowedException
import com.only4.cap4k.ddd.core.application.invocation.InvocationPolicy
import com.only4.cap4k.ddd.core.application.query.Query
import com.only4.cap4k.ddd.core.application.query.QueryExecution
import com.only4.cap4k.ddd.core.application.query.QueryHandler
import com.only4.cap4k.ddd.core.application.query.impl.DefaultQuerySupervisor
import io.mockk.every
import io.mockk.mockk
import jakarta.validation.ConstraintViolation
import jakarta.validation.ConstraintViolationException
import jakarta.validation.Validator
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.ExecutionException

class DefaultEndpointSupervisorTest {
    @Test
    fun `sync and async dispatch select the concrete endpoint handler`() {
        val runtime = Runtime()
        val endpoint = runtime.endpoint(listOf(object : EndpointHandler<TestEndpoint, String> {
            override fun handle(request: TestEndpoint): String = "endpoint:${request.value}"
        }))
        assertEquals("endpoint:sync", endpoint.send(TestEndpoint("sync")))
        assertEquals("endpoint:async", endpoint.sendAsync(TestEndpoint("async")).toCompletableFuture().get())
    }

    @Test
    fun `capability may invoke endpoint and endpoint may invoke command or query`() {
        val runtime = Runtime()
        lateinit var endpoint: DefaultEndpointSupervisor
        val command = runtime.command(listOf(object : CommandHandler<TestCommand, String> {
            override fun handle(command: TestCommand): String = "command:${command.value}"
        }))
        val query = runtime.query(listOf(object : QueryHandler<TestQuery, String> {
            override fun handle(query: TestQuery): String = "query:${query.value}"
        }))
        endpoint = runtime.endpoint(listOf(object : EndpointHandler<TestEndpoint, String> {
            override fun handle(request: TestEndpoint): String =
                if (request.value == "command") command.send(TestCommand(request.value))
                else query.ask(TestQuery(request.value))
        }))
        val capability = runtime.capability(listOf(object : CapabilityHandler<TestCapability, String> {
            override fun call(request: TestCapability): String = endpoint.send(TestEndpoint(request.value))
        }))

        assertEquals("command:command", capability.call(TestCapability("command")))
        assertEquals("query:query", capability.call(TestCapability("query")))
    }


    @Test
    fun `missing duplicate validation and async failures stay explicit`() {
        val runtime = Runtime()
        val missing = runtime.endpoint(emptyList())
        val missingFailure = assertThrows<IllegalStateException> {
            missing.send(TestEndpoint("missing"))
        }
        assertTrue(missingFailure.message.orEmpty().contains("No endpoint handler"))

        val duplicateFailure = assertThrows<IllegalStateException> {
            runtime.endpoint(
                listOf(
                    object : EndpointHandler<TestEndpoint, String> {
                        override fun handle(request: TestEndpoint): String = request.value
                    },
                    object : EndpointHandler<TestEndpoint, String> {
                        override fun handle(request: TestEndpoint): String = request.value
                    },
                ),
            )
        }
        assertTrue(duplicateFailure.message.orEmpty().contains("Multiple endpoint handlers"))
        assertTrue(duplicateFailure.message.orEmpty().contains(TestEndpoint::class.java.name))

        val validator = mockk<Validator>()
        val violation = mockk<ConstraintViolation<TestEndpoint>>(relaxed = true)
        every { validator.validate(any<TestEndpoint>()) } returns setOf(violation)
        val validated = runtime.endpoint(
            handlers = listOf(object : EndpointHandler<TestEndpoint, String> {
                override fun handle(request: TestEndpoint): String = request.value
            }),
            validator = validator,
        )
        assertThrows<ConstraintViolationException> { validated.send(TestEndpoint("invalid")) }

        val expected = IllegalArgumentException("remote failure")
        val failed = runtime.endpoint(
            listOf(object : EndpointHandler<TestEndpoint, String> {
                override fun handle(request: TestEndpoint): String = throw expected
            }),
        )
        val asyncFailure = assertThrows<ExecutionException> {
            failed.sendAsync(TestEndpoint("async-failure")).toCompletableFuture().get()
        }
        assertSame(expected, asyncFailure.cause)
    }

    @Test
    fun `async endpoint preserves execution context and installs endpoint invocation scope`() {
        val runtime = Runtime()
        val actorKey = ExecutionContextKey("actor", Actor::class.java)
        val endpoint = runtime.endpoint(listOf(object : EndpointHandler<TestEndpoint, String> {
            override fun handle(request: TestEndpoint): String {
                assertEquals(InvocationKind.ENDPOINT, runtime.scopes.current())
                return runtime.contexts.current()[actorKey]!!.name
            }
        }))
        val installed = runtime.contexts.install(
            ExecutionContextSnapshot.builder().put(actorKey, Actor("alice")).build(),
        )
        val stage = try {
            endpoint.sendAsync(TestEndpoint("context"))
        } finally {
            installed.close()
        }

        assertEquals("alice", stage.toCompletableFuture().get())
        assertTrue(runtime.contexts.current().isEmpty)
        assertEquals(null, runtime.scopes.current())
    }

    @Test
    fun `provider handler and consumer proxy handler are both reached only through Mediator endpoints`() {
        val runtime = Runtime()
        val provider = runtime.endpoint(
            listOf(object : EndpointHandler<TestEndpoint, String> {
                override fun handle(request: TestEndpoint): String = "provider:${request.value}"
            }),
        )
        EndpointSupervisorSupport.configure(provider)
        try {
            assertEquals("provider:local", Mediator.endpoints.send(TestEndpoint("local")))
        } finally {
            EndpointSupervisorSupport.release(provider)
        }

        var proxyCalls = 0
        val consumerProxy = runtime.endpoint(
            listOf(object : EndpointHandler<TestEndpoint, String> {
                override fun handle(request: TestEndpoint): String {
                    proxyCalls += 1
                    return "remote:${request.value}"
                }
            }),
        )
        EndpointSupervisorSupport.configure(consumerProxy)
        try {
            assertEquals("remote:direct", Mediator.endpoints.send(TestEndpoint("direct")))
            val capability = runtime.capability(
                listOf(object : CapabilityHandler<TestCapability, String> {
                    override fun call(request: TestCapability): String =
                        Mediator.endpoints.send(TestEndpoint("acl:${request.value}"))
                }),
            )
            assertEquals("remote:acl:booking", capability.call(TestCapability("booking")))
            assertEquals(2, proxyCalls)
        } finally {
            EndpointSupervisorSupport.release(consumerProxy)
        }
    }

    @Test
    fun `endpoint cannot invoke capability`() {
        val runtime = Runtime()
        val capability = runtime.capability(listOf(CapabilityHandler<TestCapability, String> { it.value }))
        val endpoint = runtime.endpoint(listOf(object : EndpointHandler<TestEndpoint, String> {
            override fun handle(request: TestEndpoint): String = capability.call(TestCapability(request.value))
        }))
        val failure = assertThrows<InvocationNotAllowedException> { endpoint.send(TestEndpoint("blocked")) }
        assertEquals(InvocationKind.ENDPOINT, failure.currentKind)
        assertEquals(InvocationKind.CAPABILITY, failure.requestedKind)
    }

    data class TestEndpoint(val value: String) : EndpointRequest<String>
    data class TestCapability(val value: String) : CapabilityCall<String>
    data class TestCommand(val value: String) : Command<String>
    data class TestQuery(val value: String) : Query<String>
    data class Actor(val name: String) : ExecutionContextElement

    private class Runtime {
        val contexts = DefaultExecutionContextManager()
        private val propagation = ExecutionContextPropagation(contexts, contexts)
        val scopes = DefaultInvocationScopeManager()
        private val policy = InvocationPolicy(scopes)
        private val executor = DirectExecutor

        fun endpoint(
            handlers: List<EndpointHandler<*, *>>,
            validator: Validator? = null,
        ) = DefaultEndpointSupervisor(
            handlers, validator, policy, scopes, contexts, propagation, executor,
        ).apply { init() }

        fun capability(handlers: List<CapabilityHandler<*, *>>) = DefaultCapabilitySupervisor(
            handlers, emptyList(), null, policy, scopes, contexts, propagation, executor,
        ).apply { init() }

        fun command(handlers: List<CommandHandler<*, *>>) = DefaultCommandSupervisor(
            handlers, emptyList(), null, { DirectUnitOfWork }, policy, scopes,
        ).apply { init() }

        fun query(handlers: List<QueryHandler<*, *>>) = DefaultQuerySupervisor(
            handlers, emptyList(), null, policy, scopes, contexts, propagation, executor, { DirectQueryExecution },
        ).apply { init() }
    }

    private object DirectExecutor : ApplicationAsyncExecutor {
        override fun <RESULT : Any> submit(task: () -> RESULT): CompletionStage<RESULT> =
            CompletableFuture<RESULT>().also { future ->
                try { future.complete(task()) } catch (failure: Throwable) { future.completeExceptionally(failure) }
            }
        override fun close() = Unit
    }

    private object DirectUnitOfWork : CommandUnitOfWorkCoordinator {
        override val active: Boolean = false
        override fun <RESULT> execute(block: () -> RESULT): RESULT = block()
    }

    private object DirectQueryExecution : QueryExecution {
        override val active: Boolean = false
        override fun <RESULT> execute(block: () -> RESULT): RESULT = block()
    }
}

package com.only4.cap4k.ddd.core.domain.event.impl

import com.only4.cap4k.ddd.core.application.event.annotation.IntegrationEvent
import com.only4.cap4k.ddd.core.application.async.BoundedApplicationAsyncExecutor
import com.only4.cap4k.ddd.core.application.context.DefaultExecutionContextManager
import com.only4.cap4k.ddd.core.application.context.ExecutionContextPropagation
import com.only4.cap4k.ddd.core.application.invocation.DefaultInvocationScopeManager
import com.only4.cap4k.ddd.core.application.invocation.InvocationPolicy
import com.only4.cap4k.ddd.core.application.query.Query
import com.only4.cap4k.ddd.core.application.query.QueryExecution
import com.only4.cap4k.ddd.core.application.query.QueryHandler
import com.only4.cap4k.ddd.core.application.query.impl.DefaultQuerySupervisor
import com.only4.cap4k.ddd.core.domain.event.annotation.DomainEvent
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.support.BeanDefinitionBuilder
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.context.event.EventListener
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.scheduling.annotation.Async
import org.springframework.transaction.event.TransactionalEventListener
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class Cap4kEventListenerFactoryTest {
    private val resolver = Cap4kEventHandlerDescriptorResolver()

    @AfterEach
    fun resetRuntimeContext() = EventRuntimeContext.reset()

    @Test
    fun `factory claims only concrete cap4k event methods`() {
        val factory = Cap4kEventListenerFactory(
            resolver,
            Cap4kEventHandlerRegistry(),
            DefaultInvocationScopeManager(),
        )

        assertTrue(factory.supportsMethod(method(ValidMethods::class.java, "domain", DomainPayload::class.java)))
        assertTrue(factory.supportsMethod(method(ValidMethods::class.java, "integration", IntegrationPayload::class.java)))
        assertFalse(factory.supportsMethod(method(OrdinaryMethods::class.java, "ordinary", OrdinaryPayload::class.java)))
        assertEquals(Ordered.HIGHEST_PRECEDENCE, factory.order)
    }

    @Test
    fun `resolver preserves condition order and composed listener semantics`() {
        val descriptor = requireNotNull(
            resolver.resolve(
                "validMethods",
                ValidMethods::class.java,
                method(ValidMethods::class.java, "composed", DomainPayload::class.java),
            ),
        )

        assertEquals(DomainPayload::class.java, descriptor.eventPayloadClass)
        assertEquals(Cap4kEventKind.DOMAIN, descriptor.eventKind)
        assertEquals(-20, descriptor.order)
        assertEquals("", descriptor.condition)
    }

    @Test
    fun `resolver rejects unsupported cap4k handler shapes at discovery`() {
        val invalidMethods = listOf(
            "transactional" to "@TransactionalEventListener",
            "async" to "@Async",
            "returning" to "Unit/void",
            "multipleClasses" to "exactly one concrete cap4k event",
            "mismatchedClass" to "exactly match",
            "defaultExecutionDisabled" to "defaultExecution=false",
            "suspending" to "suspend",
            "abstractPayload" to "concrete event type",
        )

        invalidMethods.forEach { (methodName, expectedMessage) ->
            val reflected = InvalidMethods::class.java.declaredMethods.first { it.name == methodName }
            val failure = assertThrows<IllegalArgumentException>(methodName) {
                resolver.resolve("invalidMethods", InvalidMethods::class.java, reflected)
            }
            assertTrue(failure.message.orEmpty().contains(expectedMessage), failure.message)
        }
    }

    @Test
    fun `transactional cap4k listener fails during Spring listener discovery`() {
        AnnotationConfigApplicationContext().use { context ->
            val scopes = DefaultInvocationScopeManager()
            context.beanFactory.registerSingleton(
                "cap4kEventListenerFactory",
                Cap4kEventListenerFactory(resolver, Cap4kEventHandlerRegistry(), scopes),
            )
            context.registerBeanDefinition(
                "invalidTransactionalHandler",
                BeanDefinitionBuilder.genericBeanDefinition(InvalidTransactionalHandler::class.java).beanDefinition,
            )

            val failure = assertThrows<RuntimeException> { context.refresh() }

            assertTrue(failure.stackTraceToString().contains("@TransactionalEventListener"))
        }
    }

    @Test
    fun `dispatcher evaluates conditions orders handlers and stops after first failure`() {
        withRuntimeContext(OrderedHandlers::class.java) { context, dispatcher ->
            val handlers = context.getBean(OrderedHandlers::class.java)

            val failure = assertThrows<EventListenerInvocationException> {
                dispatcher.dispatch(DomainPayload("run", enabled = false))
            }

            assertEquals(listOf("first", "failing"), handlers.calls)
            assertSame(OrderedHandlers.failure, failure.cause)
            assertEquals("failing", failure.listenerMethod.name)
        }
    }

    @Test
    fun `condition can select a second method on the same bean`() {
        withRuntimeContext(ConditionalHandlers::class.java) { context, dispatcher ->
            val handlers = context.getBean(ConditionalHandlers::class.java)

            dispatcher.dispatch(DomainPayload("skip", enabled = false))
            dispatcher.dispatch(DomainPayload("run", enabled = true))

            assertEquals(listOf("always:skip", "conditional:run", "always:run"), handlers.calls)
        }
    }

    @Test
    fun `ordinary Spring event remains owned by the default Spring listener path`() {
        withRuntimeContext(OrdinaryMethods::class.java) { context, dispatcher ->
            val listener = context.getBean(OrdinaryMethods::class.java)

            context.publishEvent(OrdinaryPayload("spring"))
            dispatcher.dispatch(DomainPayload("cap4k"))

            assertEquals(listOf("spring"), listener.values)
        }
    }

    @Test
    fun `event handler waits for ignored Mediator managed query tasks and propagates their failure`() {
        val scopes = DefaultInvocationScopeManager()
        val executionContexts = DefaultExecutionContextManager()
        val executor = BoundedApplicationAsyncExecutor(2, 8, threadNamePrefix = "event-query-test-")
        val queryStarted = CountDownLatch(1)
        val releaseQuery = CountDownLatch(1)
        val taskFailure = IllegalStateException("query failed")
        val querySupervisor = DefaultQuerySupervisor(
            handlers = listOf(object : QueryHandler<ManagedQuery, String> {
                override fun handle(query: ManagedQuery): String = when (query.value) {
                    "wait" -> {
                        queryStarted.countDown()
                        releaseQuery.await(5, TimeUnit.SECONDS)
                        "done"
                    }
                    else -> throw taskFailure
                }
            }),
            interceptors = emptyList(),
            validator = null,
            invocationPolicy = InvocationPolicy(scopes),
            invocationScopeManager = scopes,
            executionContextAccessor = executionContexts,
            executionContextPropagation = ExecutionContextPropagation(executionContexts, executionContexts),
            asyncExecutor = executor,
            queryExecutionProvider = { ImmediateQueryExecution },
        ).apply { init() }
        ManagedAsyncHandler.querySupervisor = querySupervisor

        try {
            withRuntimeContext(ManagedAsyncHandler::class.java, scopes) { _, dispatcher ->
                val delivery = CompletableFuture.runAsync { dispatcher.dispatch(DomainPayload("wait")) }
                assertTrue(queryStarted.await(5, TimeUnit.SECONDS))
                assertFalse(delivery.isDone)
                releaseQuery.countDown()
                delivery.get(5, TimeUnit.SECONDS)

                val failure = assertThrows<EventListenerInvocationException> {
                    dispatcher.dispatch(DomainPayload("fail"))
                }
                assertSame(taskFailure, failure.cause)
            }
        } finally {
            executor.close()
        }
    }

    private fun withRuntimeContext(
        listenerClass: Class<*>,
        scopes: DefaultInvocationScopeManager = DefaultInvocationScopeManager(),
        block: (AnnotationConfigApplicationContext, DefaultEventHandlerDispatcher) -> Unit,
    ) {
        AnnotationConfigApplicationContext().use { context ->
            val registry = Cap4kEventHandlerRegistry()
            context.beanFactory.registerSingleton("cap4kInvocationScopes", scopes)
            context.beanFactory.registerSingleton("cap4kEventHandlerDescriptorResolver", resolver)
            context.beanFactory.registerSingleton("cap4kEventHandlerRegistry", registry)
            context.beanFactory.registerSingleton(
                "cap4kEventListenerFactory",
                Cap4kEventListenerFactory(resolver, registry, scopes),
            )
            context.registerBeanDefinition(
                "listenerUnderTest",
                BeanDefinitionBuilder.genericBeanDefinition(listenerClass).beanDefinition,
            )
            context.refresh()
            block(context, DefaultEventHandlerDispatcher(registry))
        }
    }

    private fun method(owner: Class<*>, name: String, vararg parameters: Class<*>) =
        owner.getDeclaredMethod(name, *parameters)

    @DomainEvent
    data class DomainPayload(
        val value: String,
        val enabled: Boolean = true,
    )

    @IntegrationEvent("integration.payload")
    data class IntegrationPayload(val value: String)

    data class OrdinaryPayload(val value: String)

    @DomainEvent
    abstract class AbstractDomainPayload

    @Target(AnnotationTarget.FUNCTION)
    @Retention(AnnotationRetention.RUNTIME)
    @EventListener
    annotation class DomainReaction

    class ValidMethods {
        @EventListener
        fun domain(event: DomainPayload) = Unit

        @EventListener
        fun integration(event: IntegrationPayload) = Unit

        @DomainReaction
        @Order(-20)
        fun composed(event: DomainPayload) = Unit
    }

    class OrdinaryMethods {
        val values = mutableListOf<String>()

        @EventListener
        fun ordinary(event: OrdinaryPayload): DomainPayload {
            values += event.value
            return DomainPayload(event.value)
        }
    }

    class InvalidMethods {
        @TransactionalEventListener
        fun transactional(event: DomainPayload) = Unit

        @Async
        @EventListener
        fun async(event: DomainPayload) = Unit

        @EventListener
        fun returning(event: DomainPayload): String = event.value

        @EventListener(classes = [DomainPayload::class, IntegrationPayload::class])
        fun multipleClasses(event: DomainPayload) = Unit

        @EventListener(classes = [IntegrationPayload::class])
        fun mismatchedClass(event: DomainPayload) = Unit

        @EventListener(defaultExecution = false)
        fun defaultExecutionDisabled(event: DomainPayload) = Unit

        @EventListener
        suspend fun suspending(event: DomainPayload) = Unit

        @EventListener
        fun abstractPayload(event: AbstractDomainPayload) = Unit
    }

    class InvalidTransactionalHandler {
        @TransactionalEventListener
        fun handle(event: DomainPayload) = Unit
    }

    class OrderedHandlers {
        val calls = mutableListOf<String>()

        @Order(0)
        @EventListener
        fun first(event: DomainPayload) {
            calls += "first"
        }

        @Order(10)
        @EventListener
        fun failing(event: DomainPayload) {
            calls += "failing"
            throw failure
        }

        @Order(20)
        @EventListener
        fun skipped(event: DomainPayload) {
            calls += "skipped"
        }

        companion object {
            val failure = IllegalStateException("handler failed")
        }
    }

    class ConditionalHandlers {
        val calls = mutableListOf<String>()

        @Order(0)
        @EventListener(condition = "#root.args[0].enabled")
        fun conditional(event: DomainPayload) {
            calls += "conditional:${event.value}"
        }

        @Order(10)
        @EventListener
        fun always(event: DomainPayload) {
            calls += "always:${event.value}"
        }
    }

    data class ManagedQuery(val value: String) : Query<String>

    class ManagedAsyncHandler {
        @EventListener
        fun handle(event: DomainPayload) {
            querySupervisor.askAsync(ManagedQuery(event.value))
        }

        companion object {
            lateinit var querySupervisor: DefaultQuerySupervisor
        }
    }

    private object ImmediateQueryExecution : QueryExecution {
        override val active: Boolean = false

        override fun <RESULT> execute(block: () -> RESULT): RESULT = block()
    }
}

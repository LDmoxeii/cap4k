package com.only4.cap4k.ddd.application.event

import com.alibaba.fastjson.JSON
import com.only4.cap4k.ddd.core.application.context.DefaultExecutionContextManager
import com.only4.cap4k.ddd.core.application.context.ExecutionContextCodecRegistry
import com.only4.cap4k.ddd.core.application.event.annotation.IntegrationEvent
import com.only4.cap4k.ddd.core.application.invocation.DefaultInvocationScopeManager
import com.only4.cap4k.ddd.core.domain.event.EventHandlerDispatcher
import com.only4.cap4k.ddd.core.domain.event.EventMessageInterceptor
import com.only4.cap4k.ddd.core.domain.event.EventTypeCatalog
import com.only4.cap4k.ddd.core.domain.event.ReliableEventDeliveryContext
import com.only4.cap4k.ddd.core.domain.event.ReliableEventDeliveryContextAccessor
import com.only4.cap4k.ddd.core.domain.event.impl.Cap4kEventHandlerDescriptorResolver
import com.only4.cap4k.ddd.core.domain.event.impl.Cap4kEventHandlerRegistry
import com.only4.cap4k.ddd.core.domain.event.impl.Cap4kEventListenerFactory
import com.only4.cap4k.ddd.core.domain.event.impl.DefaultEventHandlerDispatcher
import com.only4.cap4k.ddd.core.domain.event.impl.DefaultReliableEventDeliveryContextManager
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.support.BeanDefinitionBuilder
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.context.event.EventListener
import org.springframework.core.env.Environment
import java.time.Instant

class HttpIntegrationEventDeliveryContextTest {
    @Test
    fun `delivery context surrounds dispatcher but not pre and post interceptors`() {
        val observed = DeliveryObservations()
        lateinit var fixture: Fixture
        val dispatcher = mockk<EventHandlerDispatcher>()
        every { dispatcher.dispatch(any()) } answers {
            observed.inDispatcher = fixture.deliveryManager.currentOrNull()
        }
        fixture = adapter(dispatcher) { accessor ->
            listOf(observingInterceptor(observed, accessor))
        }
        val publishedAt = Instant.parse("2026-08-04T00:00:00.123Z")

        assertTrue(
            fixture.adapter.consume(
                eventId = "event-123",
                eventName = "http.delivery.event",
                publishedAt = publishedAt,
                payloadJsonStr = JSON.toJSONString(HttpDeliveryEvent("payload")),
            ),
        )

        val context = requireNotNull(observed.inDispatcher)
        assertEquals("event-123", context.eventId)
        assertEquals(HttpDeliveryEvent::class.java.simpleName, context.eventName)
        assertEquals(publishedAt, context.publishedAt)
        assertNull(context.attempt)
        assertEquals(com.only4.cap4k.ddd.core.domain.event.ReliableEventRedeliveryHint.UNKNOWN, context.redeliveryHint)
        assertNull(observed.inPreInterceptor)
        assertNull(observed.inPostInterceptor)
        assertNull(fixture.deliveryManager.currentOrNull())
        assertThrows<IllegalStateException> { fixture.deliveryAccessor.current() }
    }

    @Test
    fun `handler failure clears delivery context and skips post interceptor`() {
        val observed = DeliveryObservations()
        val failure = IllegalStateException("handler failed")
        lateinit var fixture: Fixture
        val dispatcher = mockk<EventHandlerDispatcher>()
        every { dispatcher.dispatch(any()) } answers {
            observed.inDispatcher = fixture.deliveryManager.currentOrNull()
            throw failure
        }
        fixture = adapter(dispatcher) { accessor ->
            listOf(observingInterceptor(observed, accessor))
        }

        assertFalse(
            fixture.adapter.consume(
                eventId = "event-failure",
                eventName = "http.delivery.event",
                publishedAt = Instant.parse("2026-08-04T00:00:00Z"),
                payloadJsonStr = JSON.toJSONString(HttpDeliveryEvent("payload")),
            ),
        )

        assertTrue(observed.inDispatcher != null)
        assertNull(observed.inPreInterceptor)
        assertNull(observed.inPostInterceptor)
        assertNull(fixture.deliveryManager.currentOrNull())
    }

    @Test
    fun `same thread sequential HTTP deliveries do not reuse or leak context`() {
        val observed = mutableListOf<ReliableEventDeliveryContext?>()
        val dispatcher = mockk<EventHandlerDispatcher>()
        val fixture = adapter(dispatcher)
        every { dispatcher.dispatch(any()) } answers {
            observed += fixture.deliveryManager.currentOrNull()
        }

        assertTrue(fixture.adapter.consume(
            eventId = "first",
            eventName = "http.delivery.event",
            publishedAt = Instant.parse("2026-08-04T00:00:00Z"),
            payloadJsonStr = JSON.toJSONString(HttpDeliveryEvent("first")),
        ))
        assertNull(fixture.deliveryManager.currentOrNull())
        assertTrue(fixture.adapter.consume(
            eventId = "second",
            eventName = "http.delivery.event",
            publishedAt = Instant.parse("2026-08-04T00:00:01Z"),
            payloadJsonStr = JSON.toJSONString(HttpDeliveryEvent("second")),
        ))
        assertNull(fixture.deliveryManager.currentOrNull())

        assertEquals(listOf("first", "second"), observed.map { it?.eventId })
        assertEquals(listOf("HttpDeliveryEvent", "HttpDeliveryEvent"), observed.map { it?.eventName })
    }

    @Test
    fun `condition skipped handler leaves no delivery context`() {
        AnnotationConfigApplicationContext().use { context ->
            val resolver = Cap4kEventHandlerDescriptorResolver()
            val registry = Cap4kEventHandlerRegistry()
            val scopes = DefaultInvocationScopeManager()
            context.beanFactory.registerSingleton("cap4kEventHandlerDescriptorResolver", resolver)
            context.beanFactory.registerSingleton("cap4kEventHandlerRegistry", registry)
            context.beanFactory.registerSingleton(
                "cap4kEventListenerFactory",
                Cap4kEventListenerFactory(resolver, registry, scopes),
            )
            context.registerBeanDefinition(
                "conditionalHttpHandler",
                BeanDefinitionBuilder.genericBeanDefinition(ConditionalHttpHandler::class.java).beanDefinition,
            )
            context.refresh()

            val fixture = adapter(DefaultEventHandlerDispatcher(registry))
            assertTrue(
                fixture.adapter.consume(
                    eventId = "condition-skip",
                    eventName = "http.delivery.event",
                    publishedAt = Instant.parse("2026-08-04T00:00:00Z"),
                    payloadJsonStr = JSON.toJSONString(HttpDeliveryEvent("skip", enabled = false)),
                ),
            )

            assertEquals(0, context.getBean(ConditionalHttpHandler::class.java).calls)
            assertNull(fixture.deliveryManager.currentOrNull())
        }
    }

    private fun observingInterceptor(
        observed: DeliveryObservations,
        accessor: ReliableEventDeliveryContextAccessor,
    ): EventMessageInterceptor = mockk {
        every { initPublish(any()) } just runs
        every { prePublish(any()) } just runs
        every { postPublish(any()) } just runs
        every { preSubscribe(any()) } answers { observed.inPreInterceptor = accessor.currentOrNull() }
        every { postSubscribe(any()) } answers { observed.inPostInterceptor = accessor.currentOrNull() }
    }

    private fun adapter(
        dispatcher: EventHandlerDispatcher,
        interceptors: (ReliableEventDeliveryContextAccessor) -> List<EventMessageInterceptor> = { emptyList() },
    ): Fixture {
        val executionContexts = DefaultExecutionContextManager()
        val deliveryManager = DefaultReliableEventDeliveryContextManager(executionContexts, executionContexts)
        val subscriberRegister = mockk<HttpIntegrationEventSubscriberRegister>()
        every { subscriberRegister.subscribe(any(), any(), any()) } returns true
        val environment = mockk<Environment>()
        every { environment.resolvePlaceholders(any()) } answers { firstArg() }
        val adapter = HttpIntegrationEventSubscriberAdapter(
            eventHandlerDispatcher = dispatcher,
            eventMessageInterceptors = interceptors(deliveryManager),
            httpIntegrationEventSubscriberRegister = subscriberRegister,
            environment = environment,
            eventTypeCatalog = SingleHttpEventCatalog,
            applicationName = "test-app",
            httpBaseUrl = "http://localhost",
            httpSubscribePath = "/subscribe",
            httpConsumePath = "/consume",
            executionContextCodecRegistry = ExecutionContextCodecRegistry(emptyList()),
            executionContextScopeManager = executionContexts,
            reliableEventDeliveryContextScopeManager = deliveryManager,
        ).apply { init() }
        return Fixture(adapter, deliveryManager, deliveryManager)
    }

    private data class Fixture(
        val adapter: HttpIntegrationEventSubscriberAdapter,
        val deliveryManager: DefaultReliableEventDeliveryContextManager,
        val deliveryAccessor: ReliableEventDeliveryContextAccessor,
    )

    private class DeliveryObservations {
        var inPreInterceptor: ReliableEventDeliveryContext? = null
        var inDispatcher: ReliableEventDeliveryContext? = null
        var inPostInterceptor: ReliableEventDeliveryContext? = null
    }

    private object SingleHttpEventCatalog : EventTypeCatalog {
        override fun integrationEventTypes(): Set<Class<*>> = setOf(HttpDeliveryEvent::class.java)
    }

}

@IntegrationEvent("http.delivery.event", subscriber = "test-subscriber")
internal data class HttpDeliveryEvent(
    val value: String,
    val enabled: Boolean = true,
)

internal class ConditionalHttpHandler {
    var calls: Int = 0

    @EventListener(condition = "#root.args[0].enabled")
    fun handle(event: HttpDeliveryEvent) {
        calls++
    }
}

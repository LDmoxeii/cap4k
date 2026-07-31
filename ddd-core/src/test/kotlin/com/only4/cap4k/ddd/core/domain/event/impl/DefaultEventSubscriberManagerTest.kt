package com.only4.cap4k.ddd.core.domain.event.impl

import com.only4.cap4k.ddd.core.application.invocation.DefaultInvocationScopeManager
import com.only4.cap4k.ddd.core.application.invocation.InvocationKind
import com.only4.cap4k.ddd.core.domain.event.EventSubscriber
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.context.ApplicationEventPublisher
import org.springframework.scheduling.annotation.Async

class DefaultEventSubscriberManagerTest {
    @Test
    fun `dispatch uses explicit subscribers and Spring event publication without package scanning`() {
        val received = mutableListOf<TestEvent>()
        val invocationScopes = DefaultInvocationScopeManager()
        val subscriber = object : EventSubscriber<TestEvent> {
            override fun onEvent(event: TestEvent) {
                assertEquals(InvocationKind.DOMAIN_EVENT_HANDLER, invocationScopes.current())
                received += event
            }
        }
        val publisher = mockk<ApplicationEventPublisher>()
        every { publisher.publishEvent(any<Any>()) } just runs
        val manager = DefaultEventSubscriberManager(listOf(subscriber), publisher, invocationScopes).apply { init() }
        val event = TestEvent("created")

        manager.dispatch(event)

        assertEquals(listOf(event), received)
        assertEquals(null, invocationScopes.current())
        verify(exactly = 1) { publisher.publishEvent(event) }
    }

    @Test
    fun `dispatch stops at first failing subscriber`() {
        val calls = mutableListOf<String>()
        val failing = object : EventSubscriber<TestEvent> {
            override fun onEvent(event: TestEvent) {
                calls += "failing"
                error("boom")
            }
        }
        val skipped = object : EventSubscriber<TestEvent> {
            override fun onEvent(event: TestEvent) {
                calls += "skipped"
            }
        }
        val publisher = mockk<ApplicationEventPublisher>(relaxed = true)
        val invocationScopes = DefaultInvocationScopeManager()
        val manager = DefaultEventSubscriberManager(listOf(failing, skipped), publisher, invocationScopes).apply { init() }

        assertThrows<EventDispatchException> {
            manager.dispatch(TestEvent("created"))
        }

        assertEquals(listOf("failing"), calls)
        assertEquals(null, invocationScopes.current())
        verify(exactly = 0) { publisher.publishEvent(any<Any>()) }
    }

    @Test
    fun `subscriber class cannot use Async`() {
        val publisher = mockk<ApplicationEventPublisher>(relaxed = true)

        val error = assertThrows<IllegalStateException> {
            DefaultEventSubscriberManager(
                listOf(AsyncSubscriber()),
                publisher,
                DefaultInvocationScopeManager(),
            ).init()
        }

        assertEquals(true, error.message.orEmpty().contains("cannot use @Async"))
    }

    @Test
    fun `subscriber method cannot use Async`() {
        val publisher = mockk<ApplicationEventPublisher>(relaxed = true)

        val error = assertThrows<IllegalStateException> {
            DefaultEventSubscriberManager(
                listOf(AsyncMethodSubscriber()),
                publisher,
                DefaultInvocationScopeManager(),
            ).init()
        }

        assertEquals(true, error.message.orEmpty().contains("cannot use @Async"))
    }

    private data class TestEvent(val value: String)

    @Async
    private class AsyncSubscriber : EventSubscriber<TestEvent> {
        override fun onEvent(event: TestEvent) = Unit
    }

    private class AsyncMethodSubscriber : EventSubscriber<TestEvent> {
        @Async
        override fun onEvent(event: TestEvent) = Unit
    }
}

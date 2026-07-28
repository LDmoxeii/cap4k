package com.only4.cap4k.ddd.core.domain.event.impl

import com.only4.cap4k.ddd.core.domain.event.EventSubscriber
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.context.ApplicationEventPublisher

class DefaultEventSubscriberManagerTest {
    @Test
    fun `dispatch uses explicit subscribers and Spring event publication without package scanning`() {
        val received = mutableListOf<TestEvent>()
        val subscriber = object : EventSubscriber<TestEvent> {
            override fun onEvent(event: TestEvent) {
                received += event
            }
        }
        val publisher = mockk<ApplicationEventPublisher>()
        every { publisher.publishEvent(any<Any>()) } just runs
        val manager = DefaultEventSubscriberManager(listOf(subscriber), publisher).apply { init() }
        val event = TestEvent("created")

        manager.dispatch(event)

        assertEquals(listOf(event), received)
        verify(exactly = 1) { publisher.publishEvent(event) }
    }

    private data class TestEvent(val value: String)
}

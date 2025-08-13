package com.only4.cap4k.ddd.core.domain.event.impl

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * AbstractEventSubscriber测试
 *
 * @author LD_moxeii
 * @date 2025/07/24
 */
@DisplayName("AbstractEventSubscriber 测试")
class AbstractEventSubscriberTest {

    @Nested
    @DisplayName("基本功能测试")
    inner class BasicFunctionalityTests {

        @Test
        @DisplayName("应该能够创建具体的事件订阅者实现")
        fun `should create concrete event subscriber implementation`() {
            // given
            val testEvent = TestEvent("test message")
            var receivedEvent: TestEvent? = null

            val subscriber = object : AbstractEventSubscriber<TestEvent>() {
                override fun onEvent(event: TestEvent) {
                    receivedEvent = event
                }
            }

            // when
            subscriber.onEvent(testEvent)

            // then
            assertEquals(testEvent, receivedEvent)
            assertEquals("test message", receivedEvent?.message)
        }

        @Test
        @DisplayName("应该能够处理不同类型的事件")
        fun `should handle different types of events`() {
            // given
            val stringSubscriber = object : AbstractEventSubscriber<String>() {
                override fun onEvent(event: String) {
                    assertEquals("test string", event)
                }
            }

            val intSubscriber = object : AbstractEventSubscriber<Int>() {
                override fun onEvent(event: Int) {
                    assertEquals(42, event)
                }
            }

            // when & then
            stringSubscriber.onEvent("test string")
            intSubscriber.onEvent(42)
        }
    }

    @Nested
    @DisplayName("事件处理测试")
    inner class EventHandlingTests {

        @Test
        @DisplayName("应该能够处理复杂事件对象")
        fun `should handle complex event objects`() {
            // given
            val complexEvent = ComplexEvent(
                id = "123",
                data = mapOf("key1" to "value1", "key2" to "value2"),
                timestamp = System.currentTimeMillis()
            )

            var processedEvent: ComplexEvent? = null
            val subscriber = object : AbstractEventSubscriber<ComplexEvent>() {
                override fun onEvent(event: ComplexEvent) {
                    processedEvent = event
                }
            }

            // when
            subscriber.onEvent(complexEvent)

            // then
            assertEquals(complexEvent.id, processedEvent?.id)
            assertEquals(complexEvent.data, processedEvent?.data)
            assertEquals(complexEvent.timestamp, processedEvent?.timestamp)
        }
    }

    @Nested
    @DisplayName("异常处理测试")
    inner class ExceptionHandlingTests {

        @Test
        @DisplayName("事件处理过程中的异常应该被抛出")
        fun `should propagate exceptions from event handling`() {
            // given
            val subscriber = object : AbstractEventSubscriber<String>() {
                override fun onEvent(event: String) {
                    throw RuntimeException("Processing failed")
                }
            }

            // when & then
            assertThrows<RuntimeException> {
                subscriber.onEvent("test")
            }
        }

        @Test
        @DisplayName("应该能够处理事件处理过程中的业务异常")
        fun `should handle business exceptions during event processing`() {
            // given
            val subscriber = object : AbstractEventSubscriber<BusinessEvent>() {
                override fun onEvent(event: BusinessEvent) {
                    if (event.shouldFail) {
                        throw IllegalStateException("Business rule violation")
                    }
                }
            }

            val validEvent = BusinessEvent(shouldFail = false)
            val invalidEvent = BusinessEvent(shouldFail = true)

            // when & then
            // 有效事件应该正常处理
            subscriber.onEvent(validEvent) // 不应该抛出异常

            // 无效事件应该抛出异常
            assertThrows<IllegalStateException> {
                subscriber.onEvent(invalidEvent)
            }
        }
    }

    @Nested
    @DisplayName("继承和多态测试")
    inner class InheritanceAndPolymorphismTests {

        @Test
        @DisplayName("应该支持继承层次结构")
        fun `should support inheritance hierarchy`() {
            // given
            var baseEventProcessed = false
            var derivedEventProcessed = false

            val baseSubscriber = object : AbstractEventSubscriber<BaseEvent>() {
                override fun onEvent(event: BaseEvent) {
                    baseEventProcessed = true
                }
            }

            val derivedSubscriber = object : AbstractEventSubscriber<DerivedEvent>() {
                override fun onEvent(event: DerivedEvent) {
                    derivedEventProcessed = true
                }
            }

            // when
            baseSubscriber.onEvent(BaseEvent("base"))
            derivedSubscriber.onEvent(DerivedEvent("derived", 100))

            // then
            assertTrue(baseEventProcessed)
            assertTrue(derivedEventProcessed)
        }

        @Test
        @DisplayName("应该能够创建多个不同的订阅者实例")
        fun `should create multiple different subscriber instances`() {
            // given
            val events = mutableListOf<String>()

            val subscriber1 = object : AbstractEventSubscriber<String>() {
                override fun onEvent(event: String) {
                    events.add("subscriber1: $event")
                }
            }

            val subscriber2 = object : AbstractEventSubscriber<String>() {
                override fun onEvent(event: String) {
                    events.add("subscriber2: $event")
                }
            }

            // when
            subscriber1.onEvent("test1")
            subscriber2.onEvent("test2")

            // then
            assertEquals(2, events.size)
            assertTrue(events.contains("subscriber1: test1"))
            assertTrue(events.contains("subscriber2: test2"))
        }
    }

    @Nested
    @DisplayName("性能和并发测试")
    inner class PerformanceAndConcurrencyTests {

        @Test
        @DisplayName("应该能够处理大量事件")
        fun `should handle large number of events`() {
            // given
            var eventCount = 0
            val subscriber = object : AbstractEventSubscriber<Int>() {
                override fun onEvent(event: Int) {
                    eventCount++
                }
            }

            // when
            repeat(10000) { i ->
                subscriber.onEvent(i)
            }

            // then
            assertEquals(10000, eventCount)
        }

        @Test
        @DisplayName("应该是线程安全的")
        fun `should be thread safe`() {
            // given
            val processedEvents = mutableSetOf<Int>()
            val subscriber = object : AbstractEventSubscriber<Int>() {
                override fun onEvent(event: Int) {
                    synchronized(processedEvents) {
                        processedEvents.add(event)
                    }
                }
            }

            // when
            val threads = (1..10).map { threadId ->
                Thread {
                    repeat(100) { i ->
                        subscriber.onEvent(threadId * 100 + i)
                    }
                }
            }

            threads.forEach { it.start() }
            threads.forEach { it.join() }

            // then
            assertEquals(1000, processedEvents.size)
        }
    }

    // 测试用的事件类
    data class TestEvent(val message: String)

    data class ComplexEvent(
        val id: String,
        val data: Map<String, String>,
        val timestamp: Long
    )

    data class BusinessEvent(val shouldFail: Boolean)

    open class BaseEvent(val name: String)

    class DerivedEvent(name: String, val value: Int) : BaseEvent(name)
}

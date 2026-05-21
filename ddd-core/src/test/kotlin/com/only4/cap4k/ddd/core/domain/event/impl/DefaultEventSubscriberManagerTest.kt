package com.only4.cap4k.ddd.core.domain.event.impl

import com.only4.cap4k.ddd.core.application.RequestParam
import com.only4.cap4k.ddd.core.application.RequestSupervisor
import com.only4.cap4k.ddd.core.application.event.IntegrationEventSupervisor
import com.only4.cap4k.ddd.core.application.event.annotation.AutoRelease
import com.only4.cap4k.ddd.core.application.event.annotation.AutoRequest
import com.only4.cap4k.ddd.core.application.event.annotation.IntegrationEvent
import com.only4.cap4k.ddd.core.domain.event.EventSubscriber
import com.only4.cap4k.ddd.core.domain.event.annotation.DomainEvent
import io.mockk.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNotNull
import org.junit.jupiter.api.assertThrows
import org.springframework.context.ApplicationEventPublisher
import org.springframework.core.Ordered
import org.springframework.core.convert.converter.Converter
import java.time.LocalDateTime

/**
 * DefaultEventSubscriberManager测试
 *
 * @author LD_moxeii
 * @date 2025/07/24
 */
@DisplayName("DefaultEventSubscriberManager 测试")
class DefaultEventSubscriberManagerTest {

    private lateinit var applicationEventPublisher: ApplicationEventPublisher
    private lateinit var manager: DefaultEventSubscriberManager

    private val scanPath = "com.only4.cap4k.ddd.core.domain.event.impl"

    @BeforeEach
    fun setUp() {
        applicationEventPublisher = mockk()
        every { applicationEventPublisher.publishEvent(any<Any>()) } just Runs

        // Mock 静态方法
        mockkObject(RequestSupervisor)
        every { RequestSupervisor.instance } returns mockk<RequestSupervisor>()
        every { RequestSupervisor.instance.send(any<RequestParam<*>>()) } returns "mocked-response"

        mockkObject(IntegrationEventSupervisor)
        every { IntegrationEventSupervisor.instance } returns mockk<IntegrationEventSupervisor>()
        every { IntegrationEventSupervisor.instance.attach(any<Any>(), any<LocalDateTime>()) } just Runs
        every { IntegrationEventSupervisor.manager } returns mockk()
        every { IntegrationEventSupervisor.manager.release() } just Runs
    }

    @Nested
    @DisplayName("初始化和基本功能测试")
    inner class InitializationTests {

        @Test
        @DisplayName("应该能够创建管理器实例")
        fun `should create manager instance`() {
            // given
            val subscribers = emptyList<EventSubscriber<*>>()

            // when
            manager = DefaultEventSubscriberManager(
                subscribers,
                applicationEventPublisher,
                scanPath
            )

            // then
            assertNotNull(manager)
        }

        @Test
        @DisplayName("应该能够初始化订阅者")
        fun `should initialize subscribers`() {
            // given
            val subscriber1 = TestEventSubscriber1()
            val subscriber2 = TestEventSubscriber2()
            val subscribers = listOf<EventSubscriber<*>>(subscriber1, subscriber2)

            manager = DefaultEventSubscriberManager(
                subscribers,
                applicationEventPublisher,
                scanPath
            )

            // when
            manager.init()

            // then - 初始化应该成功完成
            assertTrue(true)
        }

        @Test
        @DisplayName("多次初始化应该是安全的")
        fun `multiple initializations should be safe`() {
            // given
            val subscribers = emptyList<EventSubscriber<*>>()
            manager = DefaultEventSubscriberManager(
                subscribers,
                applicationEventPublisher,
                scanPath
            )

            // when
            manager.init()
            manager.init()
            manager.init()

            // then - 不应该抛出异常
            assertTrue(true)
        }
    }

    @Nested
    @DisplayName("订阅者管理测试")
    inner class SubscriberManagementTests {

        @BeforeEach
        fun setUp() {
            manager = DefaultEventSubscriberManager(
                emptyList(),
                applicationEventPublisher,
                scanPath
            )
            manager.init()
        }

        @Test
        @DisplayName("应该能够订阅事件")
        fun `should subscribe to events`() {
            // given
            val subscriber = TestEventSubscriber1()

            // when
            val result = manager.subscribe(String::class.java, subscriber)

            // then
            assertTrue(result)
        }

        @Test
        @DisplayName("应该能够取消订阅事件")
        fun `should unsubscribe from events`() {
            // given
            val subscriber = TestEventSubscriber1()
            manager.subscribe(String::class.java, subscriber)

            // when
            val result = manager.unsubscribe(String::class.java, subscriber)

            // then
            assertTrue(result)
        }

        @Test
        @DisplayName("取消订阅不存在的订阅者应该返回false")
        fun `unsubscribing non-existent subscriber should return false`() {
            // given
            val subscriber = TestEventSubscriber1()

            // when
            val result = manager.unsubscribe(String::class.java, subscriber)

            // then
            assertFalse(result)
        }

        @Test
        @DisplayName("应该能够为同一事件类型添加多个订阅者")
        fun `should add multiple subscribers for same event type`() {
            // given
            val subscriber1 = TestEventSubscriber1()
            val subscriber2 = TestEventSubscriber1()

            // when
            val result1 = manager.subscribe(String::class.java, subscriber1)
            val result2 = manager.subscribe(String::class.java, subscriber2)

            // then
            assertTrue(result1)
            assertTrue(result2)
        }
    }

    @Nested
    @DisplayName("事件分发测试")
    inner class EventDispatchTests {

        @Test
        @DisplayName("应该分发事件到正确的订阅者")
        fun `should dispatch events to correct subscribers`() {
            // given
            var stringEventReceived = false
            var intEventReceived = false

            val stringSubscriber = object : EventSubscriber<String> {
                override fun onEvent(event: String) {
                    stringEventReceived = true
                    assertEquals("test string", event)
                }
            }

            val intSubscriber = object : EventSubscriber<Int> {
                override fun onEvent(event: Int) {
                    intEventReceived = true
                    assertEquals(42, event)
                }
            }

            manager = DefaultEventSubscriberManager(
                emptyList(),
                applicationEventPublisher,
                scanPath
            )
            manager.init()
            manager.subscribe(String::class.java, stringSubscriber)
            manager.subscribe(Integer::class.java, intSubscriber)

            // when
            manager.dispatch("test string")
            manager.dispatch(42)

            // then
            assertTrue(stringEventReceived)
            assertTrue(intEventReceived)
        }

        @Test
        @DisplayName("应该处理没有订阅者的事件")
        fun `should handle events with no subscribers`() {
            // given
            manager = DefaultEventSubscriberManager(
                emptyList(),
                applicationEventPublisher,
                scanPath
            )

            // when & then - 不应该抛出异常
            manager.dispatch("unsubscribed event")
        }

        @Test
        @DisplayName("应该按照优先级顺序调用订阅者")
        fun `should call subscribers in priority order`() {
            // given
            val callOrder = mutableListOf<String>()

            val highPrioritySubscriber = TestHighPrioritySubscriber { callOrder.add("high") }
            val lowPrioritySubscriber = TestLowPrioritySubscriber { callOrder.add("low") }

            manager = DefaultEventSubscriberManager(
                listOf(lowPrioritySubscriber, highPrioritySubscriber), // 故意颠倒顺序
                applicationEventPublisher,
                scanPath
            )

            // when
            manager.dispatch("test")

            // then
            assertEquals(listOf("high", "low"), callOrder)
        }
    }

    @Nested
    @DisplayName("自动注解处理测试")
    inner class AutoAnnotationProcessingTests {

        // 注意：这些测试需要模拟扫描功能，实际实现可能需要更复杂的setup
        @Test
        @DisplayName("应该处理Spring事件发布")
        fun `should handle Spring event publishing`() {
            // given
            manager = DefaultEventSubscriberManager(
                emptyList(),
                applicationEventPublisher,
                scanPath
            )

            // when
            manager.init()

            // then - Spring事件发布器应该被调用过（通过init过程中的自动配置）
            // 这个测试主要验证初始化过程不会出错
            assertTrue(true)
        }

        @Test
        @DisplayName("AutoRequest注解不应该在初始化或分发时自动发送请求")
        fun `AutoRequest annotations should not send requests automatically during initialization or dispatch`() {
            // given
            manager = DefaultEventSubscriberManager(
                emptyList(),
                applicationEventPublisher,
                scanPath
            )

            // when
            manager.init()
            manager.dispatch(TestAutoRequestEvent("test"))

            // then
            verify(exactly = 0) { RequestSupervisor.instance.send(any<RequestParam<*>>()) }
            verify { applicationEventPublisher.publishEvent(TestAutoRequestEvent("test")) }
        }
    }

    @Nested
    @DisplayName("异常处理测试")
    inner class ExceptionHandlingTests {

        @Test
        @DisplayName("订阅者异常不应该影响其他订阅者")
        fun `subscriber exceptions should not affect other subscribers`() {
            // given
            var successfulSubscriberCalled = false

            val failingSubscriber = object : EventSubscriber<String> {
                override fun onEvent(event: String) {
                    throw RuntimeException("Subscriber failed")
                }
            }

            val successfulSubscriber = object : EventSubscriber<String> {
                override fun onEvent(event: String) {
                    successfulSubscriberCalled = true
                }
            }

            manager = DefaultEventSubscriberManager(
                emptyList(),
                applicationEventPublisher,
                scanPath
            )
            manager.init()
            manager.subscribe(String::class.java, failingSubscriber)
            manager.subscribe(String::class.java, successfulSubscriber)

            // when
            try {
                manager.dispatch("test")
            } catch (e: Exception) {
                // 异常被抛出是预期的
            }

            // then
            assertTrue(successfulSubscriberCalled)
        }

        @Test
        @DisplayName("多个失败订阅者应该聚合为一个诊断异常")
        fun `multiple failing subscribers produce one EventDispatchException with two failures`() {
            // given
            val firstSubscriber = FailingStringSubscriber("first failure")
            val secondSubscriber = AnotherFailingStringSubscriber("second failure")

            manager = DefaultEventSubscriberManager(
                emptyList(),
                applicationEventPublisher,
                scanPath
            )
            manager.init()
            manager.subscribe(String::class.java, firstSubscriber)
            manager.subscribe(String::class.java, secondSubscriber)

            // when
            val exception = assertThrows<EventDispatchException> {
                manager.dispatch("test")
            }

            // then
            assertEquals(String::class.java, exception.eventPayloadClass)
            assertEquals(2, exception.failures.size)
            assertEquals(FailingStringSubscriber::class.java, exception.failures[0].subscriberClass)
            assertEquals(AnotherFailingStringSubscriber::class.java, exception.failures[1].subscriberClass)
            assertEquals("first failure", exception.failures[0].cause.message)
            assertEquals("second failure", exception.failures[1].cause.message)
        }

        @Test
        @DisplayName("订阅者失败后仍应调用成功订阅者")
        fun `successful subscriber still runs when another subscriber fails`() {
            // given
            var successfulSubscriberCalled = false
            val failingSubscriber = FailingStringSubscriber("failed")
            val successfulSubscriber = object : EventSubscriber<String> {
                override fun onEvent(event: String) {
                    successfulSubscriberCalled = true
                }
            }

            manager = DefaultEventSubscriberManager(
                emptyList(),
                applicationEventPublisher,
                scanPath
            )
            manager.init()
            manager.subscribe(String::class.java, failingSubscriber)
            manager.subscribe(String::class.java, successfulSubscriber)

            // when
            assertThrows<EventDispatchException> {
                manager.dispatch("test")
            }

            // then
            assertTrue(successfulSubscriberCalled)
        }

        @Test
        @DisplayName("诊断异常消息应该包含事件载荷和失败订阅者类型")
        fun `exception includes event payload class and failing subscriber class`() {
            // given
            val failingSubscriber = FailingStringSubscriber("failed")

            manager = DefaultEventSubscriberManager(
                emptyList(),
                applicationEventPublisher,
                scanPath
            )
            manager.init()
            manager.subscribe(String::class.java, failingSubscriber)

            // when
            val exception = assertThrows<EventDispatchException> {
                manager.dispatch("test")
            }

            // then
            assertTrue(exception.message!!.contains(String::class.java.name))
            assertTrue(exception.message!!.contains(FailingStringSubscriber::class.java.name))
        }

        @Test
        @DisplayName("诊断快照应该在作用域丢弃后保留附件计数")
        fun `diagnostic snapshot keeps attachment counts after runtime scope is discarded`() {
            // given
            val scope = EventRuntimeContext.push(EventRuntimeScopeType.REQUEST)
            val entity = TestEntity("entity")
            scope.attachDomain(entity, EventAttachment.eager(TestDomainEvent("domain-1")))
            scope.attachDomain(entity, EventAttachment.eager(TestDomainEvent("domain-2")))
            scope.attachIntegration(EventAttachment.eager(TestIntegrationEvent("integration")))

            try {
                manager = DefaultEventSubscriberManager(
                    emptyList(),
                    applicationEventPublisher,
                    scanPath
                )
                manager.init()
                manager.subscribe(String::class.java, FailingStringSubscriber("failed"))

                // when
                val exception = assertThrows<EventDispatchException> {
                    manager.dispatch("test")
                }
                EventRuntimeContext.discard(scope)

                // then
                assertEquals("REQUEST", exception.diagnosticContext?.scopeType)
                assertEquals(2, exception.diagnosticContext?.domainAttachmentCount)
                assertEquals(1, exception.diagnosticContext?.integrationAttachmentCount)
            } finally {
                if (EventRuntimeContext.currentOrNull() === scope) {
                    EventRuntimeContext.pop(scope)
                }
            }
        }

        @Test
        @DisplayName("诊断异常应该防御性复制失败列表")
        fun `failures list cannot be externally mutated when constructed from mutable list`() {
            // given
            val failures = mutableListOf(
                EventSubscriberFailure(FailingStringSubscriber::class.java, IllegalStateException("failed"))
            )

            // when
            val exception = EventDispatchException(String::class.java, null, failures)
            failures.add(
                EventSubscriberFailure(AnotherFailingStringSubscriber::class.java, IllegalArgumentException("later"))
            )

            // then
            assertEquals(1, exception.failures.size)
            assertEquals(FailingStringSubscriber::class.java, exception.failures.single().subscriberClass)
        }
    }

    @Nested
    @DisplayName("并发测试")
    inner class ConcurrencyTests {

        @Test
        @DisplayName("应该支持并发订阅和分发")
        fun `should support concurrent subscription and dispatch`() {
            // given
            manager = DefaultEventSubscriberManager(
                emptyList(),
                applicationEventPublisher,
                scanPath
            )
            manager.init()

            val processedEvents = mutableSetOf<String>()
            val subscriber = object : EventSubscriber<String> {
                override fun onEvent(event: String) {
                    synchronized(processedEvents) {
                        processedEvents.add(event)
                    }
                }
            }

            manager.subscribe(String::class.java, subscriber)

            // when
            val threads = (1..10).map { threadId ->
                Thread {
                    repeat(10) { i ->
                        manager.dispatch("event-$threadId-$i")
                    }
                }
            }

            threads.forEach { it.start() }
            threads.forEach { it.join() }

            // then
            assertEquals(100, processedEvents.size)
        }

        @Test
        @DisplayName("并发初始化应该是安全的")
        fun `concurrent initialization should be safe`() {
            // given
            manager = DefaultEventSubscriberManager(
                emptyList(),
                applicationEventPublisher,
                scanPath
            )

            // when
            val threads = (1..10).map {
                Thread {
                    manager.init()
                }
            }

            threads.forEach { it.start() }
            threads.forEach { it.join() }

            // then - 不应该抛出异常
            assertTrue(true)
        }
    }

    // 测试用的订阅者类
    class TestEventSubscriber1 : AbstractEventSubscriber<String>() {
        override fun onEvent(event: String) {
            // Test implementation
        }
    }

    class TestEventSubscriber2 : AbstractEventSubscriber<Int>() {
        override fun onEvent(event: Int) {
            // Test implementation
        }
    }

    @org.springframework.core.annotation.Order(Ordered.HIGHEST_PRECEDENCE)
    class TestHighPrioritySubscriber(private val callback: () -> Unit) : AbstractEventSubscriber<String>() {
        override fun onEvent(event: String) {
            callback()
        }
    }

    @org.springframework.core.annotation.Order(Ordered.LOWEST_PRECEDENCE)
    class TestLowPrioritySubscriber(private val callback: () -> Unit) : AbstractEventSubscriber<String>() {
        override fun onEvent(event: String) {
            callback()
        }
    }

    class FailingStringSubscriber(private val failureMessage: String) : AbstractEventSubscriber<String>() {
        override fun onEvent(event: String) {
            throw IllegalStateException(failureMessage)
        }
    }

    class AnotherFailingStringSubscriber(private val failureMessage: String) : AbstractEventSubscriber<String>() {
        override fun onEvent(event: String) {
            throw IllegalArgumentException(failureMessage)
        }
    }

    // 测试用的事件类
    @DomainEvent
    data class TestDomainEvent(val message: String)

    @IntegrationEvent
    data class TestIntegrationEvent(val message: String)

    data class TestEntity(val id: String)

    @AutoRequest(targetRequestClass = TestRequest::class)
    @DomainEvent
    data class TestAutoRequestEvent(val message: String)

    @AutoRelease(sourceDomainEventClass = TestDomainEvent::class, delayInSeconds = 0)
    @IntegrationEvent
    data class TestAutoReleaseEvent(val message: String)

    data class TestRequest(val message: String) : RequestParam<String>

    // 测试用的转换器
    class TestEventConverter : Converter<TestAutoRequestEvent, TestRequest> {
        override fun convert(source: TestAutoRequestEvent): TestRequest {
            return TestRequest(source.message)
        }
    }
}

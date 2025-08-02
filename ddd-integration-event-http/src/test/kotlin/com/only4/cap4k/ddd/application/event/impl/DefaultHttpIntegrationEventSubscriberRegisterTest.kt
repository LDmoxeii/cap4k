package com.only4.cap4k.ddd.application.event.impl

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@DisplayName("默认HTTP集成事件订阅注册器测试")
class DefaultHttpIntegrationEventSubscriberRegisterTest {

    private lateinit var register: DefaultHttpIntegrationEventSubscriberRegister

    @BeforeEach
    fun setUp() {
        register = DefaultHttpIntegrationEventSubscriberRegister()
    }

    @Nested
    @DisplayName("订阅功能测试")
    inner class SubscribeTest {

        @Test
        @DisplayName("成功订阅新事件")
        fun `should subscribe to new event successfully`() {
            // Act
            val result = register.subscribe("user.created", "service-a", "http://localhost:8080/webhook")

            // Assert
            assertTrue(result)
            assertEquals(listOf("user.created"), register.events())

            val subscribers = register.subscribers("user.created")
            assertEquals(1, subscribers.size)
            assertEquals("user.created", subscribers[0].event)
            assertEquals("service-a", subscribers[0].subscriber)
            assertEquals("http://localhost:8080/webhook", subscribers[0].callbackUrl)
        }

        @Test
        @DisplayName("同一事件可以有多个订阅者")
        fun `should allow multiple subscribers for same event`() {
            // Act
            val result1 = register.subscribe("user.created", "service-a", "http://localhost:8080/webhook-a")
            val result2 = register.subscribe("user.created", "service-b", "http://localhost:8080/webhook-b")

            // Assert
            assertTrue(result1)
            assertTrue(result2)

            val subscribers = register.subscribers("user.created")
            assertEquals(2, subscribers.size)

            val subscriberNames = subscribers.map { it.subscriber }.toSet()
            assertTrue(subscriberNames.contains("service-a"))
            assertTrue(subscriberNames.contains("service-b"))
        }

        @Test
        @DisplayName("重复订阅同一事件和订阅者应该失败")
        fun `should fail when subscribing duplicate event and subscriber`() {
            // Arrange
            register.subscribe("user.created", "service-a", "http://localhost:8080/webhook")

            // Act
            val result = register.subscribe("user.created", "service-a", "http://localhost:8080/webhook-new")

            // Assert
            assertFalse(result)

            val subscribers = register.subscribers("user.created")
            assertEquals(1, subscribers.size)
            assertEquals("http://localhost:8080/webhook", subscribers[0].callbackUrl) // 保持原来的URL
        }

        @Test
        @DisplayName("同一订阅者可以订阅不同事件")
        fun `should allow same subscriber to subscribe to different events`() {
            // Act
            val result1 = register.subscribe("user.created", "service-a", "http://localhost:8080/webhook")
            val result2 = register.subscribe("user.updated", "service-a", "http://localhost:8080/webhook")

            // Assert
            assertTrue(result1)
            assertTrue(result2)

            val events = register.events().toSet()
            assertTrue(events.contains("user.created"))
            assertTrue(events.contains("user.updated"))

            assertEquals(1, register.subscribers("user.created").size)
            assertEquals(1, register.subscribers("user.updated").size)
        }
    }

    @Nested
    @DisplayName("取消订阅功能测试")
    inner class UnsubscribeTest {

        @BeforeEach
        fun setUpSubscriptions() {
            register.subscribe("user.created", "service-a", "http://localhost:8080/webhook-a")
            register.subscribe("user.created", "service-b", "http://localhost:8080/webhook-b")
            register.subscribe("user.updated", "service-a", "http://localhost:8080/webhook-a")
        }

        @Test
        @DisplayName("成功取消现有订阅")
        fun `should unsubscribe existing subscription successfully`() {
            // Act
            val result = register.unsubscribe("user.created", "service-a")

            // Assert
            assertTrue(result)

            val subscribers = register.subscribers("user.created")
            assertEquals(1, subscribers.size)
            assertEquals("service-b", subscribers[0].subscriber)

            // 其他事件的订阅不受影响
            assertEquals(1, register.subscribers("user.updated").size)
        }

        @Test
        @DisplayName("取消不存在的事件订阅应该失败")
        fun `should fail when unsubscribing non-existent event`() {
            // Act
            val result = register.unsubscribe("non.existent", "service-a")

            // Assert
            assertFalse(result)
        }

        @Test
        @DisplayName("取消不存在的订阅者应该失败")
        fun `should fail when unsubscribing non-existent subscriber`() {
            // Act
            val result = register.unsubscribe("user.created", "non-existent-service")

            // Assert
            assertFalse(result)

            // 原有订阅不受影响
            assertEquals(2, register.subscribers("user.created").size)
        }

        @Test
        @DisplayName("取消最后一个订阅者后事件仍然存在")
        fun `should keep event after unsubscribing last subscriber`() {
            // Arrange
            register.unsubscribe("user.created", "service-a")
            register.unsubscribe("user.created", "service-b")

            // Act & Assert
            assertTrue(register.events().contains("user.created"))
            assertTrue(register.subscribers("user.created").isEmpty())
        }
    }

    @Nested
    @DisplayName("查询功能测试")
    inner class QueryTest {

        @BeforeEach
        fun setUpSubscriptions() {
            register.subscribe("user.created", "service-a", "http://localhost:8080/webhook-a")
            register.subscribe("user.created", "service-b", "http://localhost:8080/webhook-b")
            register.subscribe("user.updated", "service-a", "http://localhost:8080/webhook-a")
            register.subscribe("order.created", "service-c", "http://localhost:8080/webhook-c")
        }

        @Test
        @DisplayName("正确返回所有事件列表")
        fun `should return all events correctly`() {
            // Act
            val events = register.events()

            // Assert
            assertEquals(3, events.size)
            val eventSet = events.toSet()
            assertTrue(eventSet.contains("user.created"))
            assertTrue(eventSet.contains("user.updated"))
            assertTrue(eventSet.contains("order.created"))
        }

        @Test
        @DisplayName("正确返回指定事件的订阅者")
        fun `should return subscribers for specific event correctly`() {
            // Act
            val subscribers = register.subscribers("user.created")

            // Assert
            assertEquals(2, subscribers.size)

            val subscriberInfos = subscribers.associateBy { it.subscriber }

            val serviceA = subscriberInfos["service-a"]!!
            assertEquals("user.created", serviceA.event)
            assertEquals("service-a", serviceA.subscriber)
            assertEquals("http://localhost:8080/webhook-a", serviceA.callbackUrl)

            val serviceB = subscriberInfos["service-b"]!!
            assertEquals("user.created", serviceB.event)
            assertEquals("service-b", serviceB.subscriber)
            assertEquals("http://localhost:8080/webhook-b", serviceB.callbackUrl)
        }

        @Test
        @DisplayName("查询不存在事件的订阅者返回空列表")
        fun `should return empty list for non-existent event subscribers`() {
            // Act
            val subscribers = register.subscribers("non.existent.event")

            // Assert
            assertTrue(subscribers.isEmpty())
        }

        @Test
        @DisplayName("初始状态下事件列表为空")
        fun `should return empty events list initially`() {
            // Arrange
            val emptyRegister = DefaultHttpIntegrationEventSubscriberRegister()

            // Act & Assert
            assertTrue(emptyRegister.events().isEmpty())
        }
    }

    @Nested
    @DisplayName("并发安全测试")
    inner class ConcurrencyTest {

        @Test
        @DisplayName("并发订阅同一事件应该线程安全")
        fun `should handle concurrent subscriptions to same event safely`() {
            // Arrange
            val threads = mutableListOf<Thread>()
            val results = mutableListOf<Boolean>()
            val subscriberCount = 100

            // Act
            repeat(subscriberCount) { index ->
                val thread = Thread {
                    val result =
                        register.subscribe("test.event", "service-$index", "http://localhost:8080/webhook-$index")
                    synchronized(results) {
                        results.add(result)
                    }
                }
                threads.add(thread)
                thread.start()
            }

            threads.forEach { it.join() }

            // Assert
            assertEquals(subscriberCount, results.size)
            assertTrue(results.all { it }) // 所有订阅都应该成功
            assertEquals(subscriberCount, register.subscribers("test.event").size)
        }

        @Test
        @DisplayName("并发取消订阅应该线程安全")
        fun `should handle concurrent unsubscriptions safely`() {
            // Arrange
            val subscriberCount = 50
            repeat(subscriberCount) { index ->
                register.subscribe("test.event", "service-$index", "http://localhost:8080/webhook-$index")
            }

            val threads = mutableListOf<Thread>()
            val results = mutableListOf<Boolean>()

            // Act
            repeat(subscriberCount) { index ->
                val thread = Thread {
                    val result = register.unsubscribe("test.event", "service-$index")
                    synchronized(results) {
                        results.add(result)
                    }
                }
                threads.add(thread)
                thread.start()
            }

            threads.forEach { it.join() }

            // Assert
            assertEquals(subscriberCount, results.size)
            assertTrue(results.all { it }) // 所有取消订阅都应该成功
            assertTrue(register.subscribers("test.event").isEmpty())
        }
    }
}

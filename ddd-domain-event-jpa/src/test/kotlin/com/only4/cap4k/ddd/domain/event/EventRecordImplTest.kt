package com.only4.cap4k.ddd.domain.event

import com.only4.cap4k.ddd.core.share.Constants
import com.only4.cap4k.ddd.domain.event.persistence.InventoryUpdatedEvent
import com.only4.cap4k.ddd.domain.event.persistence.OrderSubmittedEvent
import com.only4.cap4k.ddd.domain.event.persistence.TestEvent
import com.only4.cap4k.ddd.domain.event.persistence.UserCreatedEvent
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.messaging.Message
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.*

@DisplayName("EventRecordImpl实现类测试")
class EventRecordImplTest {

    private lateinit var eventRecord: EventRecordImpl
    private val testTime: LocalDateTime = LocalDateTime.of(2025, 1, 15, 10, 30, 0)

    @BeforeEach
    fun setUp() {
        eventRecord = EventRecordImpl()
    }

    @Nested
    @DisplayName("初始化测试")
    inner class InitializationTest {

        @Test
        @DisplayName("初始化简单事件")
        fun `should initialize simple event correctly`() {
            // Given
            val payload = TestEvent("test message", 123456789L)
            val svcName = "test-service"
            val expireAfter = Duration.ofMinutes(30)
            val retryTimes = 3

            // When
            eventRecord.init(payload, svcName, testTime, expireAfter, retryTimes)

            // Then
            assertNotNull(eventRecord.id)
            assertTrue(eventRecord.id.isNotEmpty())
            assertEquals("", eventRecord.type) // TestEvent没有注解
            assertEquals(payload, eventRecord.payload)
            assertEquals(testTime, eventRecord.scheduleTime)
            assertNotNull(eventRecord.nextTryTime)
            assertFalse(eventRecord.isPersist) // 默认为false
        }

        @Test
        @DisplayName("初始化IntegrationEvent事件")
        fun `should initialize IntegrationEvent correctly`() {
            // Given
            val payload = UserCreatedEvent("user123", "john", "john@test.com")

            // When
            eventRecord.init(payload, "user-service", testTime, Duration.ofHours(1), 5)

            // Then
            assertEquals("user.created", eventRecord.type)
            assertEquals(payload, eventRecord.payload)
        }

        @Test
        @DisplayName("初始化DomainEvent事件")
        fun `should initialize DomainEvent correctly`() {
            // Given
            val payload = OrderSubmittedEvent("order123", 299.99, "customer456")

            // When
            eventRecord.init(payload, "order-service", testTime, Duration.ofMinutes(15), 3)

            // Then
            assertEquals("order.submitted", eventRecord.type)
            assertEquals(payload, eventRecord.payload)
        }

        @Test
        @DisplayName("初始化后Event应该被正确设置")
        fun `should set event correctly after initialization`() {
            // Given
            val payload = TestEvent("test", 12345)

            // When
            eventRecord.init(payload, "test-service", testTime, Duration.ofMinutes(10), 2)

            // Then
            assertNotNull(eventRecord.event)
            assertEquals(eventRecord.event.payload, payload)
            assertEquals(eventRecord.event.svcName, "test-service")
            assertEquals(eventRecord.event.createAt, testTime)
        }
    }

    @Nested
    @DisplayName("属性访问测试")
    inner class PropertyAccessTest {

        @BeforeEach
        fun setUp() {
            val payload = TestEvent("test", 12345)
            eventRecord.init(payload, "test-service", testTime, Duration.ofMinutes(10), 3)
        }

        @Test
        @DisplayName("id属性应该返回event的eventUuid")
        fun `should return event uuid as id`() {
            // When
            val id = eventRecord.id

            // Then
            assertEquals(eventRecord.event.eventUuid, id)
            assertTrue(id.isNotEmpty())
        }

        @Test
        @DisplayName("eventTopic属性应该返回event的eventType")
        fun `should return event type as eventTopic`() {
            // When
            val eventTopic = eventRecord.type

            // Then
            assertEquals(eventRecord.event.eventType, eventTopic)
        }

        @Test
        @DisplayName("payload属性应该返回event的payload")
        fun `should return event payload`() {
            // When
            val payload = eventRecord.payload

            // Then
            assertEquals(eventRecord.event.payload, payload)
            assertTrue(payload is TestEvent)
        }

        @Test
        @DisplayName("scheduleTime属性应该返回event的createAt")
        fun `should return event createAt as scheduleTime`() {
            // When
            val scheduleTime = eventRecord.scheduleTime

            // Then
            assertEquals(eventRecord.event.createAt, scheduleTime)
            assertEquals(testTime, scheduleTime)
        }

        @Test
        @DisplayName("nextTryTime属性应该返回event的nextTryTime")
        fun `should return event nextTryTime`() {
            // When
            val nextTryTime = eventRecord.nextTryTime

            // Then
            assertEquals(eventRecord.event.nextTryTime, nextTryTime)
            assertNotNull(nextTryTime)
        }
    }

    @Nested
    @DisplayName("持久化标记测试")
    inner class PersistMarkTest {

        @Test
        @DisplayName("默认持久化标记应该为false")
        fun `should have default persist mark as false`() {
            // Then
            assertFalse(eventRecord.isPersist)
        }

        @Test
        @DisplayName("应该能够设置持久化标记为true")
        fun `should be able to mark persist as true`() {
            // When
            eventRecord.markPersist(true)

            // Then
            assertTrue(eventRecord.isPersist)
        }

        @Test
        @DisplayName("应该能够设置持久化标记为false")
        fun `should be able to mark persist as false`() {
            // Given
            eventRecord.markPersist(true)

            // When
            eventRecord.markPersist(false)

            // Then
            assertFalse(eventRecord.isPersist)
        }
    }

    @Nested
    @DisplayName("Message构建测试")
    inner class MessageBuildingTest {

        @Test
        @DisplayName("应该为简单事件构建正确的Message")
        fun `should build correct message for simple event`() {
            // Given
            val payload = TestEvent("test", 12345)
            eventRecord.init(payload, "test-service", testTime, Duration.ofMinutes(10), 3)

            // When
            val message = eventRecord.message

            // Then
            assertNotNull(message)
            assertEquals(payload, message.payload)

            // 验证消息头
            val headers = message.headers
            assertNotNull(headers[Constants.HEADER_KEY_CAP4J_EVENT_ID])
            assertEquals(Constants.HEADER_VALUE_CAP4J_EVENT_TYPE_DOMAIN, headers[Constants.HEADER_KEY_CAP4J_EVENT_TYPE])
            assertEquals(false, headers[Constants.HEADER_KEY_CAP4J_PERSIST])
            assertNotNull(headers[Constants.HEADER_KEY_CAP4J_TIMESTAMP])
        }

        @Test
        @DisplayName("应该为IntegrationEvent构建正确的Message")
        fun `should build correct message for integration event`() {
            // Given
            val payload = UserCreatedEvent("user123", "john", "john@test.com")
            eventRecord.init(payload, "user-service", testTime, Duration.ofMinutes(10), 3)

            // When
            val message = eventRecord.message

            // Then
            val headers = message.headers
            assertEquals(
                Constants.HEADER_VALUE_CAP4J_EVENT_TYPE_INTEGRATION,
                headers[Constants.HEADER_KEY_CAP4J_EVENT_TYPE]
            )
        }

        @Test
        @DisplayName("应该为DomainEvent构建正确的Message")
        fun `should build correct message for domain event`() {
            // Given
            val payload = OrderSubmittedEvent("order123", 299.99, "customer456")
            eventRecord.init(payload, "order-service", testTime, Duration.ofMinutes(10), 3)

            // When
            val message = eventRecord.message

            // Then
            val headers = message.headers
            assertEquals(Constants.HEADER_VALUE_CAP4J_EVENT_TYPE_DOMAIN, headers[Constants.HEADER_KEY_CAP4J_EVENT_TYPE])
        }

        @Test
        @DisplayName("应该在持久化标记为true时设置正确的消息头")
        fun `should set correct headers when persist is true`() {
            // Given
            val payload = TestEvent("test", 12345)
            eventRecord.init(payload, "test-service", testTime, Duration.ofMinutes(10), 3)
            eventRecord.markPersist(true)

            // When
            val message = eventRecord.message

            // Then
            assertEquals(true, message.headers[Constants.HEADER_KEY_CAP4J_PERSIST])
        }

        @Test
        @DisplayName("应该为未来的调度时间设置SCHEDULE头")
        fun `should set schedule header for future schedule time`() {
            // Given
            val payload = TestEvent("test", 12345)
            val futureTime = LocalDateTime.now().plusHours(1)
            eventRecord.init(payload, "test-service", futureTime, Duration.ofMinutes(10), 3)

            // When
            val message = eventRecord.message

            // Then
            val scheduleHeader = message.headers[Constants.HEADER_KEY_CAP4J_SCHEDULE]
            assertNotNull(scheduleHeader)
            assertEquals(futureTime.toEpochSecond(ZoneOffset.UTC), scheduleHeader)
        }

        @Test
        @DisplayName("应该缓存Message实例")
        fun `should cache message instance`() {
            // Given
            val payload = TestEvent("test", 12345)
            eventRecord.init(payload, "test-service", testTime, Duration.ofMinutes(10), 3)

            // When
            val message1 = eventRecord.message
            val message2 = eventRecord.message

            // Then
            assertSame(message1, message2)
        }

        @Test
        @DisplayName("应该设置正确的消息ID")
        fun `should set correct message id`() {
            // Given
            val payload = TestEvent("test", 12345)
            eventRecord.init(payload, "test-service", testTime, Duration.ofMinutes(10), 3)

            // When
            val message = eventRecord.message

            // Then
            val messageId = message.headers.id
            assertEquals(UUID.fromString(eventRecord.id), messageId)
        }
    }

    @Nested
    @DisplayName("事件状态管理测试")
    inner class EventStateManagementTest {

        @BeforeEach
        fun setUp() {
            val payload = TestEvent("test", 12345)
            eventRecord.init(payload, "test-service", testTime, Duration.ofMinutes(30), 3)
        }

        @Test
        @DisplayName("新创建的事件应该是有效的")
        fun `should be valid when newly created`() {
            // Then
            assertTrue(eventRecord.isValid)
            assertFalse(eventRecord.isInvalid)
            assertFalse(eventRecord.isDelivered)
        }

        @Test
        @DisplayName("应该能够开始发送")
        fun `should be able to begin delivery`() {
            // When
            val result = eventRecord.beginDelivery(testTime.plusMinutes(1))

            // Then
            assertTrue(result)
        }

        @Test
        @DisplayName("应该能够取消发送")
        fun `should be able to cancel delivery`() {
            // When
            val result = eventRecord.cancelDelivery(testTime.plusMinutes(1))

            // Then
            assertTrue(result)
            assertTrue(eventRecord.isInvalid)
        }

        @Test
        @DisplayName("应该能够记录异常")
        fun `should be able to record exception`() {
            // Given
            val exception = RuntimeException("Test exception")

            // When
            eventRecord.occurredException(testTime.plusMinutes(1), exception)

            // Then
            assertNotNull(eventRecord.event.exception)
            assertTrue(eventRecord.event.exception!!.contains("Test exception"))
        }

        @Test
        @DisplayName("应该能够确认发送")
        fun `should be able to confirm delivery`() {
            // When
            eventRecord.confirmedDelivery(testTime.plusMinutes(1))

            // Then
            assertTrue(eventRecord.isDelivered)
            assertFalse(eventRecord.isValid)
        }
    }

    @Nested
    @DisplayName("事件恢复测试")
    inner class EventResumeTest {

        @Test
        @DisplayName("应该能够从现有Event恢复")
        fun `should be able to resume from existing event`() {
            // Given
            val payload = TestEvent("original", 12345)
            val originalEventRecord = EventRecordImpl()
            originalEventRecord.init(payload, "test-service", testTime, Duration.ofMinutes(10), 3)
            val originalEvent = originalEventRecord.event

            // When
            val newEventRecord = EventRecordImpl()
            newEventRecord.resume(originalEvent)

            // Then
            assertEquals(originalEvent, newEventRecord.event)
            assertEquals(originalEventRecord.id, newEventRecord.id)
            assertEquals(originalEventRecord.payload, newEventRecord.payload)
            assertEquals(originalEventRecord.type, newEventRecord.type)
        }

        @Test
        @DisplayName("恢复后应该能够正常访问所有属性")
        fun `should access all properties correctly after resume`() {
            // Given
            val payload = UserCreatedEvent("user123", "john", "john@test.com")
            val originalEventRecord = EventRecordImpl()
            originalEventRecord.init(payload, "user-service", testTime, Duration.ofMinutes(30), 5)
            originalEventRecord.markPersist(true)

            // When
            val newEventRecord = EventRecordImpl()
            newEventRecord.resume(originalEventRecord.event)

            // Then
            assertEquals(originalEventRecord.id, newEventRecord.id)
            assertEquals("user.created", newEventRecord.type)
            assertEquals(payload, newEventRecord.payload)
            assertEquals(testTime, newEventRecord.scheduleTime)
            assertFalse(newEventRecord.isPersist) // persist标记不会被恢复
        }
    }

    @Nested
    @DisplayName("toString方法测试")
    inner class ToStringTest {

        @Test
        @DisplayName("toString应该返回event的字符串表示")
        fun `should return event string representation`() {
            // Given
            val payload = TestEvent("test", 12345)
            eventRecord.init(payload, "test-service", testTime, Duration.ofMinutes(10), 3)

            // When
            val result = eventRecord.toString()

            // Then
            assertNotNull(result)
            assertEquals(eventRecord.event.toString(), result)
            assertTrue(result.contains("eventUuid"))
        }
    }

    @Nested
    @DisplayName("边界条件测试")
    inner class EdgeCaseTest {

        @Test
        @DisplayName("应该处理空字符串服务名")
        fun `should handle empty service name`() {
            // Given
            val payload = TestEvent("test", 12345)

            // When & Then
            assertDoesNotThrow {
                eventRecord.init(payload, "", testTime, Duration.ofMinutes(10), 3)
            }
            assertEquals("", eventRecord.event.svcName)
        }

        @Test
        @DisplayName("应该处理零重试次数")
        fun `should handle zero retry times`() {
            // Given
            val payload = TestEvent("test", 12345)

            // When & Then
            assertDoesNotThrow {
                eventRecord.init(payload, "test-service", testTime, Duration.ofMinutes(10), 0)
            }
            assertEquals(0, eventRecord.event.tryTimes)
        }

        @Test
        @DisplayName("应该处理极短的过期时间")
        fun `should handle very short expire duration`() {
            // Given
            val payload = TestEvent("test", 12345)

            // When & Then
            assertDoesNotThrow {
                eventRecord.init(payload, "test-service", testTime, Duration.ofMillis(1), 3)
            }
        }

        @Test
        @DisplayName("应该处理复杂对象payload")
        fun `should handle complex object payload`() {
            // Given
            val location = InventoryUpdatedEvent.Location("warehouse-a", "zone-1", "shelf-100")
            val details = mapOf("operator" to "admin", "reason" to "stock adjustment")
            val payload = InventoryUpdatedEvent("product123", 50, location, details)

            // When
            eventRecord.init(payload, "inventory-service", testTime, Duration.ofMinutes(15), 3)

            // Then
            assertEquals("inventory.updated", eventRecord.type)
            assertEquals(payload, eventRecord.payload)

            val retrievedPayload = eventRecord.payload as InventoryUpdatedEvent
            assertEquals("product123", retrievedPayload.productId)
            assertEquals(50, retrievedPayload.quantity)
            assertEquals("warehouse-a", retrievedPayload.location.warehouse)
            assertEquals("admin", retrievedPayload.details["operator"])
        }
    }

    @Nested
    @DisplayName("并发安全测试")
    inner class ConcurrencyTest {

        @Test
        @DisplayName("Message缓存应该是线程安全的")
        fun `message caching should be thread safe`() {
            // Given
            val payload = TestEvent("test", 12345)
            eventRecord.init(payload, "test-service", testTime, Duration.ofMinutes(10), 3)

            // When - 多线程同时访问message
            val messages = mutableSetOf<Message<Any>>()
            val threads = (1..10).map {
                Thread {
                    synchronized(messages) {
                        messages.add(eventRecord.message)
                    }
                }
            }

            // 启动所有线程
            threads.forEach { it.start() }
            // 等待所有线程完成
            threads.forEach { it.join() }

            // Then - 所有访问应该返回同一个实例
            assertEquals(1, messages.size, "All threads should get the same message instance")
            val firstMessage = eventRecord.message
            messages.forEach { message ->
                assertSame(firstMessage, message)
            }
        }
    }
}

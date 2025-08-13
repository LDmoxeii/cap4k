package com.only4.cap4k.ddd.domain.event.persistence

import com.alibaba.fastjson.JSON
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Duration
import java.time.LocalDateTime

@DisplayName("Event实体类测试")
class EventTest {

    private lateinit var event: Event
    private val testTime: LocalDateTime = LocalDateTime.of(2025, 1, 15, 10, 30, 0)

    @BeforeEach
    fun setUp() {
        event = Event()
    }

    @Nested
    @DisplayName("初始化测试")
    inner class InitializationTest {

        @Test
        @DisplayName("初始化简单事件")
        fun `should initialize event with simple payload`() {
            // Given
            val payload = TestEvent("test message", 123456789L)
            val svcName = "test-service"
            val expireAfter = Duration.ofMinutes(30)
            val retryTimes = 3

            // When
            event.init(payload, svcName, testTime, expireAfter, retryTimes)

            // Then
            assertNotNull(event.eventUuid)
            assertEquals(svcName, event.svcName)
            assertEquals("test.event", event.eventType) // TestEvent now has @DomainEvent("test.event") annotation
            assertEquals(testTime, event.createAt)
            assertEquals(testTime.plusSeconds(1800), event.expireAt) // 30分钟
            assertEquals(Event.EventState.INIT, event.eventState)
            assertEquals(retryTimes, event.tryTimes)
            assertEquals(1, event.triedTimes)
            assertEquals(testTime, event.lastTryTime)
            assertNotNull(event.nextTryTime)
            assertEquals(payload, event.payload)
            assertNotNull(event.data)
            assertEquals(payload.javaClass.name, event.dataType)
        }

        @Test
        @DisplayName("初始化带IntegrationEvent注解的事件")
        fun `should initialize event with IntegrationEvent annotation`() {
            // Given
            val payload = UserCreatedEvent("user123", "john_doe", "john@example.com")

            // When
            event.init(payload, "user-service", testTime, Duration.ofHours(1), 5)

            // Then
            assertEquals("user.created", event.eventType)
            assertEquals(payload.javaClass.name, event.dataType)
            assertTrue(event.data!!.contains("user123"))
            assertTrue(event.data!!.contains("john_doe"))
        }

        @Test
        @DisplayName("初始化带DomainEvent注解的事件")
        fun `should initialize event with DomainEvent annotation`() {
            // Given
            val payload = OrderSubmittedEvent("order123", 299.99, "customer456")

            // When
            event.init(payload, "order-service", testTime, Duration.ofMinutes(15), 3)

            // Then
            assertEquals("order.submitted", event.eventType)
            assertEquals(payload.javaClass.name, event.dataType)
        }

        @Test
        @DisplayName("初始化带Retry注解的事件")
        fun `should initialize event with Retry annotation`() {
            // Given
            val payload = PaymentProcessedEvent("payment789", 100.0, "completed")

            // When
            event.init(payload, "payment-service", testTime, Duration.ofMinutes(10), 2)

            // Then
            assertEquals(5, event.tryTimes) // 应该使用Retry注解的值
            assertEquals(testTime.plusMinutes(30), event.expireAt) // 应该使用Retry注解的值
        }

        @Test
        @DisplayName("初始化无注解事件")
        fun `should throw exception when initializing event without annotations`() {
            // Given
            val payload = SimpleEvent("simple123", "simple value")

            // When & Then
            assertThrows<com.only4.cap4k.ddd.core.share.DomainException> {
                event.init(payload, "simple-service", testTime, Duration.ofMinutes(5), 1)
            }
        }

        @Test
        @DisplayName("初始化null payload应该抛出异常")
        fun `should throw exception when initializing with null payload`() {
            // When & Then
            assertThrows<NullPointerException> {
                @Suppress("NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
                event.init(null as Any, "test-service", testTime, Duration.ofMinutes(10), 3)
            }
        }
    }

    @Nested
    @DisplayName("Payload懒加载测试")
    inner class PayloadLazyLoadingTest {

        @Test
        @DisplayName("直接设置的payload应该直接返回")
        fun `should return directly set payload`() {
            // Given
            val originalPayload = TestEvent("original", 12345)
            event.init(originalPayload, "test", testTime, Duration.ofMinutes(10), 3)

            // When
            val retrievedPayload = event.payload

            // Then
            assertSame(originalPayload, retrievedPayload)
        }

        @Test
        @DisplayName("从JSON反序列化payload")
        fun `should deserialize payload from JSON when not directly set`() {
            // Given - 模拟从数据库加载的情况
            val originalPayload = UserCreatedEvent("user123", "john", "john@test.com")
            event.data = JSON.toJSONString(originalPayload)
            event.dataType = UserCreatedEvent::class.java.name

            // When
            val retrievedPayload = event.payload

            // Then - FastJSON with kotlin-reflect should properly deserialize Kotlin data classes
            assertNotNull(retrievedPayload)
            assertTrue(retrievedPayload is UserCreatedEvent)
            val userEvent = retrievedPayload as UserCreatedEvent
            assertEquals("user123", userEvent.userId)
            assertEquals("john", userEvent.username)
            assertEquals("john@test.com", userEvent.email)
        }

        @Test
        @DisplayName("无效的dataType应该抛出异常")
        fun `should throw exception for invalid dataType`() {
            // Given
            event.data = "{\"test\": \"value\"}"
            event.dataType = "com.invalid.ClassName"

            // When & Then
            assertThrows<com.only4.cap4k.ddd.core.share.DomainException> {
                event.payload
            }
        }

        @Test
        @DisplayName("空dataType应该抛出异常")
        fun `should throw exception for empty dataType`() {
            // Given
            event.data = "{\"test\": \"value\"}"
            event.dataType = ""

            // When & Then
            assertThrows<com.only4.cap4k.ddd.core.share.DomainException> {
                event.payload
            }
        }

        @Test
        @DisplayName("懒加载应该缓存结果")
        fun `should cache deserialized payload`() {
            // Given - 设置需要反序列化的payload
            val originalPayload = TestEvent("test", 12345)
            event.data = JSON.toJSONString(originalPayload)
            event.dataType = TestEvent::class.java.name

            // When - 多次访问payload
            val payload1 = event.payload
            val payload2 = event.payload

            // Then - 应该是同一个实例（缓存生效）
            assertSame(payload1, payload2)
            assertNotNull(payload1)
            assertTrue(payload1 is TestEvent)
            assertEquals("test", (payload1 as TestEvent).message)
        }
    }

    @Nested
    @DisplayName("事件状态转换测试")
    inner class EventStateTransitionTest {

        @BeforeEach
        fun setUp() {
            val payload = TestEvent("test", 12345)
            event.init(payload, "test", testTime, Duration.ofMinutes(30), 3)
        }

        @Test
        @DisplayName("isValid方法测试")
        fun `should return correct isValid status`() {
            // INIT状态
            event.eventState = Event.EventState.INIT
            assertTrue(event.isValid)

            // DELIVERING状态
            event.eventState = Event.EventState.DELIVERING
            assertTrue(event.isValid)

            // EXCEPTION状态
            event.eventState = Event.EventState.EXCEPTION
            assertTrue(event.isValid)

            // DELIVERED状态
            event.eventState = Event.EventState.DELIVERED
            assertFalse(event.isValid)

            // CANCEL状态
            event.eventState = Event.EventState.CANCEL
            assertFalse(event.isValid)
        }

        @Test
        @DisplayName("isInvalid方法测试")
        fun `should return correct isInvalid status`() {
            // CANCEL状态
            event.eventState = Event.EventState.CANCEL
            assertTrue(event.isInvalid)

            // EXPIRED状态
            event.eventState = Event.EventState.EXPIRED
            assertTrue(event.isInvalid)

            // EXHAUSTED状态
            event.eventState = Event.EventState.EXHAUSTED
            assertTrue(event.isInvalid)

            // INIT状态
            event.eventState = Event.EventState.INIT
            assertFalse(event.isInvalid)
        }

        @Test
        @DisplayName("isDelivered方法测试")
        fun `should return correct isDelivered status`() {
            // DELIVERED状态
            event.eventState = Event.EventState.DELIVERED
            assertTrue(event.isDelivered)

            // 其他状态
            Event.EventState.entries.forEach { state ->
                if (state != Event.EventState.DELIVERED) {
                    event.eventState = state
                    assertFalse(event.isDelivered, "State $state should not be delivered")
                }
            }
        }

        @Test
        @DisplayName("确认发送")
        fun `should confirm delivery`() {
            // Given
            val confirmTime = testTime.plusMinutes(5)

            // When
            event.endDelivery(confirmTime)

            // Then
            assertEquals(Event.EventState.DELIVERED, event.eventState)
        }

        @Test
        @DisplayName("取消发送 - 有效状态")
        fun `should cancel delivery when in valid state`() {
            // Given
            event.eventState = Event.EventState.INIT
            val cancelTime = testTime.plusMinutes(5)

            // When
            val result = event.cancelDelivery(cancelTime)

            // Then
            assertTrue(result)
            assertEquals(Event.EventState.CANCEL, event.eventState)
        }

        @Test
        @DisplayName("取消发送 - 已发送状态")
        fun `should not cancel delivery when already delivered`() {
            // Given
            event.eventState = Event.EventState.DELIVERED
            val cancelTime = testTime.plusMinutes(5)

            // When
            val result = event.cancelDelivery(cancelTime)

            // Then
            assertFalse(result)
            assertEquals(Event.EventState.DELIVERED, event.eventState) // 状态不变
        }

        @Test
        @DisplayName("取消发送 - 无效状态")
        fun `should not cancel delivery when in invalid state`() {
            // Given
            event.eventState = Event.EventState.EXPIRED
            val cancelTime = testTime.plusMinutes(5)

            // When
            val result = event.cancelDelivery(cancelTime)

            // Then
            assertFalse(result)
            assertEquals(Event.EventState.EXPIRED, event.eventState) // 状态不变
        }
    }

    @Nested
    @DisplayName("发送状态控制测试")
    inner class DeliveryStateControlTest {

        @BeforeEach
        fun setUp() {
            val payload = TestEvent("test", 12345)
            event.init(payload, "test", testTime, Duration.ofMinutes(30), 3)
        }

        @Test
        @DisplayName("成功获取发送状态")
        fun `should successfully hold state for delivery`() {
            // Given
            val deliveryTime = testTime.plusMinutes(5)
            event.nextTryTime = deliveryTime.minusMinutes(1) // 已到重试时间

            // When
            val result = event.beginDelivery(deliveryTime)

            // Then
            assertTrue(result)
            assertEquals(Event.EventState.DELIVERING, event.eventState)
            assertEquals(deliveryTime, event.lastTryTime)
            assertEquals(2, event.triedTimes) // 增加了1次
            assertNotNull(event.nextTryTime)
        }

        @Test
        @DisplayName("超过重试次数时获取发送状态失败")
        fun `should fail to hold state when retry times exceeded`() {
            // Given
            event.triedTimes = 3 // 等于tryTimes
            val deliveryTime = testTime.plusMinutes(5)

            // When
            val result = event.beginDelivery(deliveryTime)

            // Then
            assertFalse(result)
            assertEquals(Event.EventState.EXHAUSTED, event.eventState)
        }

        @Test
        @DisplayName("事件过期时获取发送状态失败")
        fun `should fail to hold state when event expired`() {
            // Given
            event.expireAt = testTime.plusMinutes(10)
            val deliveryTime = testTime.plusMinutes(15) // 超过过期时间

            // When
            val result = event.beginDelivery(deliveryTime)

            // Then
            assertFalse(result)
            assertEquals(Event.EventState.EXPIRED, event.eventState)
        }

        @Test
        @DisplayName("未到重试时间时获取发送状态失败")
        fun `should fail to hold state when not yet time to retry`() {
            // Given
            val deliveryTime = testTime.plusMinutes(5)
            event.nextTryTime = deliveryTime.plusMinutes(1) // 还未到重试时间
            event.nextTryTime = LocalDateTime.of(2025, 1, 15, 11, 0, 0) // 设置为具体时间

            // When
            val result = event.beginDelivery(deliveryTime)

            // Then
            assertFalse(result)
            assertEquals(Event.EventState.INIT, event.eventState) // 状态不变
        }

        @Test
        @DisplayName("特殊时间值应该立即执行")
        fun `should allow immediate execution for special time value`() {
            // Given
            val deliveryTime = testTime.plusMinutes(5)
            event.nextTryTime = LocalDateTime.of(1, 1, 1, 0, 0, 0) // 特殊值

            // When
            val result = event.beginDelivery(deliveryTime)

            // Then
            assertTrue(result) // 特殊值应该允许立即执行
        }

        @Test
        @DisplayName("无效状态时获取发送状态失败")
        fun `should fail to hold state when in invalid state`() {
            // Given
            event.eventState = Event.EventState.CANCEL
            val deliveryTime = testTime.plusMinutes(5)

            // When
            val result = event.beginDelivery(deliveryTime)

            // Then
            assertFalse(result)
            assertEquals(Event.EventState.CANCEL, event.eventState) // 状态不变
        }
    }

    @Nested
    @DisplayName("异常处理测试")
    inner class ExceptionHandlingTest {

        @BeforeEach
        fun setUp() {
            val payload = TestEvent("test", 12345)
            event.init(payload, "test", testTime, Duration.ofMinutes(30), 3)
        }

        @Test
        @DisplayName("记录异常信息")
        fun `should record exception information`() {
            // Given
            val exception = RuntimeException("Test exception message")
            val errorTime = testTime.plusMinutes(5)

            // When
            event.occurredException(errorTime, exception)

            // Then
            assertEquals(Event.EventState.EXCEPTION, event.eventState)
            assertNotNull(event.exception)
            assertTrue(event.exception!!.contains("Test exception message"))
            assertTrue(event.exception!!.contains("RuntimeException"))
        }

        @Test
        @DisplayName("已发送的事件不记录异常")
        fun `should not record exception for delivered event`() {
            // Given
            event.eventState = Event.EventState.DELIVERED
            val exception = RuntimeException("Test exception")
            val errorTime = testTime.plusMinutes(5)
            val originalException = event.exception

            // When
            event.occurredException(errorTime, exception)

            // Then
            assertEquals(Event.EventState.DELIVERED, event.eventState) // 状态不变
            assertEquals(originalException, event.exception) // 异常信息不变
        }

        @Test
        @DisplayName("异常信息包含堆栈跟踪")
        fun `should include stack trace in exception`() {
            // Given
            val exception = RuntimeException("Test exception")
            val errorTime = testTime.plusMinutes(5)

            // When
            event.occurredException(errorTime, exception)

            // Then
            assertNotNull(event.exception)
            assertTrue(event.exception!!.contains("EventTest")) // 应该包含调用堆栈
        }
    }

    @Nested
    @DisplayName("重试机制测试")
    inner class RetryMechanismTest {

        @Test
        @DisplayName("默认重试间隔计算")
        fun `should calculate default retry intervals`() {
            // Given
            val payload = TestEvent("test", 12345)

            // Test different tried times
            val testCases = mapOf(
                1 to 1L,    // triedTimes=1, 计算时为2: <= 10: 1分钟
                5 to 1L,    // triedTimes=5, 计算时为6: <= 10: 1分钟
                10 to 5L,   // triedTimes=10, 计算时为11: > 10, <= 20: 5分钟
                11 to 5L,   // triedTimes=11, 计算时为12: > 10, <= 20: 5分钟
                20 to 10L,  // triedTimes=20, 计算时为21: > 20: 10分钟
                21 to 10L   // triedTimes=21, 计算时为22: > 20: 10分钟
            )

            testCases.forEach { (initialTriedTimes, expectedMinutes) ->
                // Reset event for each test case
                event = Event()
                event.init(payload, "test", testTime, Duration.ofMinutes(30), 50) // Set high tryTimes
                event.triedTimes = initialTriedTimes

                val currentTime = testTime.plusMinutes(10)

                event.beginDelivery(currentTime)

                val expectedNextTry = currentTime.plusMinutes(expectedMinutes)
                assertEquals(
                    expectedNextTry, event.nextTryTime,
                    "Failed for triedTimes=$initialTriedTimes, expected ${expectedMinutes}min"
                )
            }
        }

        @Test
        @DisplayName("自定义重试间隔计算")
        fun `should calculate custom retry intervals from annotation`() {
            // Given - PaymentProcessedEvent有自定义重试间隔 [1, 2, 5, 10, 15] 和 retryTimes=5
            val payload = PaymentProcessedEvent("payment123", 100.0, "completed")

            // Since the Retry annotation sets retryTimes=5, we need to test with triedTimes < 5
            val testCases = mapOf(
                1 to 2L,   // triedTimes=1, index=1: intervals[1] = 2
                2 to 5L,   // triedTimes=2, index=2: intervals[2] = 5
                3 to 10L,  // triedTimes=3, index=3: intervals[3] = 10
                4 to 15L   // triedTimes=4, index=4: intervals[4] = 15
                // Cannot test triedTimes=5 because it would be >= tryTimes(5) and would fail
            )

            testCases.forEach { (initialTriedTimes, expectedMinutes) ->
                // Reset event for each test case and ensure payload is set correctly
                event = Event()
                event.init(payload, "test", testTime, Duration.ofMinutes(30), 50) // This will be overridden by @Retry

                // Verify the payload is properly set and annotation is accessible
                assertNotNull(event.payload)
                val retryAnnotation =
                    event.payload!!.javaClass.getAnnotation(com.only4.cap4k.ddd.core.share.annotation.Retry::class.java)
                assertNotNull(retryAnnotation, "Retry annotation should be found on PaymentProcessedEvent")
                assertEquals(5, retryAnnotation.retryTimes)
                assertEquals(5, event.tryTimes, "Event tryTimes should be overridden by Retry annotation")

                event.triedTimes = initialTriedTimes

                val currentTime = testTime.plusMinutes(10)

                val result = event.beginDelivery(currentTime)
                assertTrue(
                    result,
                    "beginDelivery should succeed for triedTimes=$initialTriedTimes (< tryTimes=${event.tryTimes})"
                )

                val expectedNextTry = currentTime.plusMinutes(expectedMinutes)
                assertEquals(
                    expectedNextTry, event.nextTryTime,
                    "Failed for triedTimes=$initialTriedTimes, expected ${expectedMinutes}min, actual was ${event.nextTryTime}"
                )
            }
        }

        @Test
        @DisplayName("边界情况 - triedTimes为0")
        fun `should handle edge case when triedTimes is zero`() {
            // Given
            val payload = PaymentProcessedEvent("payment123", 100.0, "completed")
            event.init(payload, "test", testTime, Duration.ofMinutes(30), 10)
            event.triedTimes = 0

            // When
            val currentTime = testTime.plusMinutes(10)
            event.beginDelivery(currentTime)

            // Then - 应该使用第一个间隔值
            val expectedNextTry = currentTime.plusMinutes(1)
            assertEquals(expectedNextTry, event.nextTryTime)
        }
    }

    @Nested
    @DisplayName("EventState枚举测试")
    inner class EventStateEnumTest {

        @Test
        @DisplayName("EventState.valueOf正确工作")
        fun `should correctly convert value to EventState`() {
            assertEquals(Event.EventState.INIT, Event.EventState.valueOf(0))
            assertEquals(Event.EventState.DELIVERED, Event.EventState.valueOf(1))
            assertEquals(Event.EventState.DELIVERING, Event.EventState.valueOf(-1))
            assertEquals(Event.EventState.CANCEL, Event.EventState.valueOf(-2))
            assertEquals(Event.EventState.EXPIRED, Event.EventState.valueOf(-3))
            assertEquals(Event.EventState.EXHAUSTED, Event.EventState.valueOf(-4))
            assertEquals(Event.EventState.EXCEPTION, Event.EventState.valueOf(-9))
        }

        @Test
        @DisplayName("EventState.valueOf处理无效值")
        fun `should return null for invalid value`() {
            assertNull(Event.EventState.valueOf(999))
            assertNull(Event.EventState.valueOf(-999))
        }

        @Test
        @DisplayName("EventState.Converter正确工作")
        fun `should correctly convert EventState using converter`() {
            val converter = Event.EventState.Converter()

            // 测试转换为数据库列值
            assertEquals(0, converter.convertToDatabaseColumn(Event.EventState.INIT))
            assertEquals(1, converter.convertToDatabaseColumn(Event.EventState.DELIVERED))
            assertEquals(-1, converter.convertToDatabaseColumn(Event.EventState.DELIVERING))

            // 测试从数据库列值转换
            assertEquals(Event.EventState.INIT, converter.convertToEntityAttribute(0))
            assertEquals(Event.EventState.DELIVERED, converter.convertToEntityAttribute(1))
            assertEquals(Event.EventState.DELIVERING, converter.convertToEntityAttribute(-1))
            assertNull(converter.convertToEntityAttribute(999))
        }
    }

    @Nested
    @DisplayName("复杂场景测试")
    inner class ComplexScenarioTest {

        @Test
        @DisplayName("复杂对象序列化和反序列化")
        fun `should handle complex object serialization and deserialization`() {
            // Given
            val location = InventoryUpdatedEvent.Location("warehouse-a", "zone-1", "shelf-100")
            val details = mapOf(
                "operator" to "admin",
                "reason" to "stock adjustment",
                "timestamp" to System.currentTimeMillis()
            )
            val payload = InventoryUpdatedEvent("product123", 50, location, details)

            // When - 初始化事件，这会直接设置payload
            event.init(payload, "inventory-service", testTime, Duration.ofMinutes(15), 3)

            // Then - 验证直接访问payload（不涉及反序列化）
            val retrievedPayload = event.payload
            assertNotNull(retrievedPayload)
            assertTrue(retrievedPayload is InventoryUpdatedEvent)

            val inventoryEvent = retrievedPayload as InventoryUpdatedEvent
            assertEquals("product123", inventoryEvent.productId)
            assertEquals(50, inventoryEvent.quantity)
            assertEquals("warehouse-a", inventoryEvent.location.warehouse)
            assertEquals("zone-1", inventoryEvent.location.zone)
            assertEquals("shelf-100", inventoryEvent.location.shelf)
            assertEquals("admin", inventoryEvent.details["operator"])

            // Also verify that JSON serialization worked correctly
            assertNotNull(event.data)
            assertTrue(event.data!!.contains("product123"))
            assertTrue(event.data!!.contains("warehouse-a"))
            assertEquals(InventoryUpdatedEvent::class.java.name, event.dataType)
        }

        @Test
        @DisplayName("完整的事件生命周期")
        fun `should handle complete event lifecycle`() {
            // Given - 初始化事件
            val payload = UserCreatedEvent("user123", "john", "john@test.com")
            event.init(payload, "user-service", testTime, Duration.ofHours(2), 5)

            // 验证初始状态
            assertEquals(Event.EventState.INIT, event.eventState)
            assertTrue(event.isValid)
            assertFalse(event.isDelivered)

            // When - 第一次尝试发送
            val firstTry = testTime.plusMinutes(1)
            assertTrue(event.beginDelivery(firstTry))
            assertEquals(Event.EventState.DELIVERING, event.eventState)
            assertEquals(2, event.triedTimes)

            // 发送失败，记录异常
            event.occurredException(firstTry, RuntimeException("Network timeout"))
            assertEquals(Event.EventState.EXCEPTION, event.eventState)
            assertNotNull(event.exception)

            // When - 第二次尝试发送
            val secondTry = firstTry.plusMinutes(2)
            assertTrue(event.beginDelivery(secondTry))
            assertEquals(Event.EventState.DELIVERING, event.eventState)
            assertEquals(3, event.triedTimes)

            // 发送成功
            event.endDelivery(secondTry)
            assertEquals(Event.EventState.DELIVERED, event.eventState)
            assertTrue(event.isDelivered)
            assertFalse(event.isValid)

            // 之后的操作应该无效
            assertFalse(event.beginDelivery(secondTry.plusMinutes(1)))
            assertFalse(event.cancelDelivery(secondTry.plusMinutes(1)))
        }

        @Test
        @DisplayName("事件过期场景")
        fun `should handle event expiration scenario`() {
            // Given
            val payload = TestEvent("test", 12345)
            event.init(payload, "test", testTime, Duration.ofMinutes(10), 3)

            // When - 事件过期后尝试发送
            val expiredTime = testTime.plusMinutes(15)
            val result = event.beginDelivery(expiredTime)

            // Then
            assertFalse(result)
            assertEquals(Event.EventState.EXPIRED, event.eventState)
            assertTrue(event.isInvalid)
        }

        @Test
        @DisplayName("重试次数耗尽场景")
        fun `should handle retry exhaustion scenario`() {
            // Given
            val payload = TestEvent("test", 12345)
            event.init(payload, "test", testTime, Duration.ofHours(1), 2)

            // When - 执行所有重试
            // 第一次重试（triedTimes从1变为2）
            assertTrue(event.beginDelivery(testTime.plusMinutes(1)))
            assertEquals(2, event.triedTimes)

            // 第二次重试会失败，因为triedTimes(2) >= tryTimes(2)
            val result = event.beginDelivery(testTime.plusMinutes(2))

            // Then
            assertFalse(result)
            assertEquals(Event.EventState.EXHAUSTED, event.eventState)
            assertTrue(event.isInvalid)
        }
    }

    @Nested
    @DisplayName("toString方法测试")
    inner class ToStringTest {

        @Test
        @DisplayName("toString应该返回JSON格式")
        fun `should return JSON format string`() {
            // Given
            val payload = TestEvent("test message", 12345)
            event.init(payload, "test-service", testTime, Duration.ofMinutes(10), 3)

            // When
            val result = event.toString()

            // Then
            assertNotNull(result)
            assertTrue(result.contains("eventUuid"))
            assertTrue(result.contains("test-service"))
            assertTrue(result.contains("eventState"))
            // payload字段应该被排除（@JSONField(serialize = false)）
            assertFalse(result.contains("payload"))
        }
    }
}

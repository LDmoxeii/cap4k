package com.only4.cap4k.ddd.application.event.persistence

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@DisplayName("EventHttpSubscriber实体测试")
class EventHttpSubscriberTest {

    @Nested
    @DisplayName("构造函数测试")
    inner class ConstructorTest {

        @Test
        @DisplayName("应该正确创建具有所有属性的实体")
        fun `should create entity with all properties correctly`() {
            // Arrange & Act
            val entity = EventHttpSubscriber(
                id = 123L,
                event = "user.registered",
                subscriber = "notification-service",
                callbackUrl = "http://notification:8080/webhooks/user",
                version = 1
            )

            // Assert
            assertEquals(123L, entity.id)
            assertEquals("user.registered", entity.event)
            assertEquals("notification-service", entity.subscriber)
            assertEquals("http://notification:8080/webhooks/user", entity.callbackUrl)
            assertEquals(1, entity.version)
        }

        @Test
        @DisplayName("应该正确创建具有默认版本的实体")
        fun `should create entity with default version`() {
            // Arrange & Act
            val entity = EventHttpSubscriber(
                id = null,
                event = "order.shipped",
                subscriber = "tracking-service",
                callbackUrl = "https://tracking.example.com/api/events"
            )

            // Assert
            assertNull(entity.id)
            assertEquals("order.shipped", entity.event)
            assertEquals("tracking-service", entity.subscriber)
            assertEquals("https://tracking.example.com/api/events", entity.callbackUrl)
            assertEquals(0, entity.version)
        }

        @Test
        @DisplayName("应该正确创建新实体（ID为null）")
        fun `should create new entity with null id`() {
            // Arrange & Act
            val entity = EventHttpSubscriber(
                event = "payment.failed",
                subscriber = "retry-service",
                callbackUrl = "http://retry:9090/payment-retry",
                version = 0
            )

            // Assert
            assertNull(entity.id)
            assertEquals("payment.failed", entity.event)
            assertEquals("retry-service", entity.subscriber)
            assertEquals("http://retry:9090/payment-retry", entity.callbackUrl)
            assertEquals(0, entity.version)
        }
    }

    @Nested
    @DisplayName("常量字段测试")
    inner class CompanionObjectTest {

        @Test
        @DisplayName("应该定义正确的字段常量")
        fun `should define correct field constants`() {
            // Assert
            assertEquals("id", EventHttpSubscriber.F_ID)
            assertEquals("event", EventHttpSubscriber.F_EVENT)
            assertEquals("subscriber", EventHttpSubscriber.F_SUBSCRIBER)
            assertEquals("callbackUrl", EventHttpSubscriber.F_CALLBACK_URL)
        }
    }

    @Nested
    @DisplayName("数据类特性测试")
    inner class DataClassFeaturesTest {

        @Test
        @DisplayName("相同属性的实体应该相等")
        fun `should be equal when entities have same properties`() {
            // Arrange
            val entity1 = EventHttpSubscriber(
                id = 1L,
                event = "user.updated",
                subscriber = "search-service",
                callbackUrl = "http://search:8080/index",
                version = 2
            )

            val entity2 = EventHttpSubscriber(
                id = 1L,
                event = "user.updated",
                subscriber = "search-service",
                callbackUrl = "http://search:8080/index",
                version = 2
            )

            // Act & Assert
            assertEquals(entity1, entity2)
            assertEquals(entity1.hashCode(), entity2.hashCode())
        }

        @Test
        @DisplayName("不同属性的实体应该不相等")
        fun `should not be equal when entities have different properties`() {
            // Arrange
            val entity1 = EventHttpSubscriber(
                id = 1L,
                event = "user.created",
                subscriber = "email-service",
                callbackUrl = "http://email:8080/send",
                version = 0
            )

            val entity2 = EventHttpSubscriber(
                id = 2L,
                event = "user.created",
                subscriber = "email-service",
                callbackUrl = "http://email:8080/send",
                version = 0
            )

            // Act & Assert
            assertNotEquals(entity1, entity2)
        }

        @Test
        @DisplayName("toString应该包含所有属性")
        fun `toString should contain all properties`() {
            // Arrange
            val entity = EventHttpSubscriber(
                id = 42L,
                event = "product.created",
                subscriber = "catalog-service",
                callbackUrl = "http://catalog:8080/products",
                version = 3
            )

            // Act
            val toString = entity.toString()

            // Assert
            assertTrue(toString.contains("42"))
            assertTrue(toString.contains("product.created"))
            assertTrue(toString.contains("catalog-service"))
            assertTrue(toString.contains("http://catalog:8080/products"))
            assertTrue(toString.contains("3"))
        }

        @Test
        @DisplayName("copy方法应该正确工作")
        fun `copy method should work correctly`() {
            // Arrange
            val original = EventHttpSubscriber(
                id = 1L,
                event = "order.cancelled",
                subscriber = "inventory-service",
                callbackUrl = "http://inventory:8080/restore",
                version = 1
            )

            // Act
            val copied = original.copy(callbackUrl = "http://inventory:8080/cancel")

            // Assert
            assertEquals(original.id, copied.id)
            assertEquals(original.event, copied.event)
            assertEquals(original.subscriber, copied.subscriber)
            assertEquals("http://inventory:8080/cancel", copied.callbackUrl)
            assertEquals(original.version, copied.version)
        }
    }

    @Nested
    @DisplayName("边界值测试")
    inner class BoundaryValueTest {

        @Test
        @DisplayName("应该处理空字符串")
        fun `should handle empty strings`() {
            // Arrange & Act
            val entity = EventHttpSubscriber(
                event = "",
                subscriber = "",
                callbackUrl = ""
            )

            // Assert
            assertEquals("", entity.event)
            assertEquals("", entity.subscriber)
            assertEquals("", entity.callbackUrl)
            assertEquals(0, entity.version)
        }

        @Test
        @DisplayName("应该处理长字符串")
        fun `should handle long strings`() {
            // Arrange
            val longEvent = "a".repeat(255)
            val longSubscriber = "b".repeat(255)
            val longCallbackUrl = "http://example.com/" + "c".repeat(1000)

            // Act
            val entity = EventHttpSubscriber(
                event = longEvent,
                subscriber = longSubscriber,
                callbackUrl = longCallbackUrl
            )

            // Assert
            assertEquals(longEvent, entity.event)
            assertEquals(longSubscriber, entity.subscriber)
            assertEquals(longCallbackUrl, entity.callbackUrl)
        }

        @Test
        @DisplayName("应该处理特殊字符")
        fun `should handle special characters`() {
            // Arrange & Act
            val entity = EventHttpSubscriber(
                event = "用户.创建", // Chinese characters
                subscriber = "邮件-服务", // Chinese with dash
                callbackUrl = "http://localhost:8080/webhook?token=abc123&type=中文"
            )

            // Assert
            assertEquals("用户.创建", entity.event)
            assertEquals("邮件-服务", entity.subscriber)
            assertEquals("http://localhost:8080/webhook?token=abc123&type=中文", entity.callbackUrl)
        }
    }
}

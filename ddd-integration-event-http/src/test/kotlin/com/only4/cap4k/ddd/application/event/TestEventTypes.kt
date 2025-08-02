package com.only4.cap4k.ddd.application.event

import com.only4.cap4k.ddd.core.application.event.annotation.IntegrationEvent

/**
 * 测试用集成事件基类和工具函数
 */

// 测试用的集成事件类
@IntegrationEvent("test.event.basic", subscriber = "test-subscriber")
data class BasicTestEvent(
    val id: String,
    val name: String,
    val timestamp: Long = System.currentTimeMillis()
)

@IntegrationEvent("test.event.remote@http://remote:8080/register", subscriber = "remote-subscriber")
data class RemoteTestEvent(
    val id: String,
    val action: String,
    val data: Map<String, Any> = emptyMap()
)

@IntegrationEvent("test.event.no-subscriber", subscriber = "NONE_SUBSCRIBER")
data class NoSubscriberEvent(
    val id: String,
    val message: String
)

@IntegrationEvent("test.event.default-subscriber", subscriber = "")
data class DefaultSubscriberEvent(
    val id: String,
    val type: String
)

@IntegrationEvent("\${app.events.user-created}", subscriber = "\${app.services.user-service}")
data class PlaceholderEvent(
    val userId: String,
    val username: String,
    val email: String
)

// 复杂事件用于测试序列化/反序列化
@IntegrationEvent("test.event.complex", subscriber = "complex-subscriber")
data class ComplexTestEvent(
    val id: String,
    val metadata: EventMetadata,
    val items: List<EventItem>,
    val tags: Set<String> = emptySet(),
    val properties: Map<String, Any> = emptyMap()
)

data class EventMetadata(
    val version: Int,
    val source: String,
    val timestamp: Long,
    val correlationId: String? = null
)

data class EventItem(
    val itemId: String,
    val itemType: String,
    val quantity: Int,
    val attributes: Map<String, String> = emptyMap()
)

// 测试用的工具函数
object TestEventUtils {

    fun createBasicTestEvent(id: String = "test-${System.currentTimeMillis()}"): BasicTestEvent {
        return BasicTestEvent(
            id = id,
            name = "Test Event $id"
        )
    }

    fun createRemoteTestEvent(id: String = "remote-${System.currentTimeMillis()}"): RemoteTestEvent {
        return RemoteTestEvent(
            id = id,
            action = "test-action",
            data = mapOf(
                "param1" to "value1",
                "param2" to 123,
                "param3" to true
            )
        )
    }

    fun createComplexTestEvent(id: String = "complex-${System.currentTimeMillis()}"): ComplexTestEvent {
        return ComplexTestEvent(
            id = id,
            metadata = EventMetadata(
                version = 1,
                source = "test-service",
                timestamp = System.currentTimeMillis(),
                correlationId = "corr-$id"
            ),
            items = listOf(
                EventItem(
                    itemId = "item-1",
                    itemType = "product",
                    quantity = 2,
                    attributes = mapOf("color" to "red", "size" to "large")
                ),
                EventItem(
                    itemId = "item-2",
                    itemType = "service",
                    quantity = 1,
                    attributes = mapOf("duration" to "30min", "priority" to "high")
                )
            ),
            tags = setOf("urgent", "customer-order", "premium"),
            properties = mapOf(
                "totalAmount" to 199.99,
                "currency" to "USD",
                "discountApplied" to true,
                "customerTier" to "gold"
            )
        )
    }

    fun createLargeTestEvent(
        id: String = "large-${System.currentTimeMillis()}",
        itemCount: Int = 100
    ): ComplexTestEvent {
        val items = (1..itemCount).map { index ->
            EventItem(
                itemId = "item-$index",
                itemType = "type-${index % 5}",
                quantity = index,
                attributes = mapOf(
                    "attribute1" to "value$index",
                    "attribute2" to "data".repeat(10),
                    "attribute3" to index.toString()
                )
            )
        }

        val properties = (1..50).associate { index ->
            "property$index" to "value$index".repeat(20)
        }

        return ComplexTestEvent(
            id = id,
            metadata = EventMetadata(
                version = 2,
                source = "load-test-service",
                timestamp = System.currentTimeMillis(),
                correlationId = "load-test-$id"
            ),
            items = items,
            tags = setOf("load-test", "performance", "large-payload"),
            properties = properties
        )
    }
}

// 测试常量
object TestConstants {
    const val DEFAULT_TEST_URL = "http://localhost:8080"
    const val DEFAULT_WEBHOOK_PATH = "/webhook"
    const val DEFAULT_SUBSCRIBE_PATH = "/subscribe"
    const val DEFAULT_UNSUBSCRIBE_PATH = "/unsubscribe"
    const val DEFAULT_CONSUME_PATH = "/consume"

    const val TEST_APPLICATION_NAME = "test-app"
    const val TEST_SERVICE_NAME = "test-service"
    const val TEST_SUBSCRIBER_NAME = "test-subscriber"

    const val DEFAULT_EVENT_PARAM_NAME = "event"
    const val DEFAULT_EVENT_ID_PARAM_NAME = "eventId"
    const val DEFAULT_SUBSCRIBER_PARAM_NAME = "subscriber"

    const val SCAN_PATH = "com.only4.cap4k.ddd.application.event"

    // 测试用的JSON字符串
    const val BASIC_EVENT_JSON = """{"id":"test-123","name":"Test Event","timestamp":1234567890}"""
    const val INVALID_JSON = """{"invalid":"json structure"""
    const val EMPTY_JSON = "{}"

    // 测试用的URL
    const val CALLBACK_URL = "$DEFAULT_TEST_URL$DEFAULT_WEBHOOK_PATH"
    const val SUBSCRIBE_URL = "$DEFAULT_TEST_URL$DEFAULT_SUBSCRIBE_PATH"
    const val UNSUBSCRIBE_URL = "$DEFAULT_TEST_URL$DEFAULT_UNSUBSCRIBE_PATH"
    const val CONSUME_URL = "$DEFAULT_TEST_URL$DEFAULT_CONSUME_PATH"
}

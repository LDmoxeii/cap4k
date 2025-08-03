package com.only4.cap4k.ddd.domain.event.persistence

import com.only4.cap4k.ddd.core.application.event.annotation.IntegrationEvent
import com.only4.cap4k.ddd.core.domain.event.annotation.DomainEvent
import com.only4.cap4k.ddd.core.share.annotation.Retry

/**
 * 测试用的事件数据类
 */
@DomainEvent("test.event")
data class TestEvent(
    val message: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * 带IntegrationEvent注解的测试事件
 */
@IntegrationEvent("user.created")
data class UserCreatedEvent(
    val userId: String = "",
    val username: String = "",
    val email: String = ""
)

/**
 * 带DomainEvent注解的测试事件
 */
@DomainEvent("order.submitted")
data class OrderSubmittedEvent(
    val orderId: String = "",
    val amount: Double = 0.0,
    val customerId: String = ""
)

/**
 * 带Retry注解的测试事件
 */
@IntegrationEvent("payment.processed")
@Retry(retryTimes = 5, expireAfter = 30, retryIntervals = [1, 2, 5, 10, 15])
data class PaymentProcessedEvent(
    val paymentId: String = "",
    val amount: Double = 0.0,
    val status: String = ""
)

/**
 * 复杂的测试事件（包含嵌套对象）
 */
@DomainEvent("inventory.updated")
data class InventoryUpdatedEvent(
    val productId: String = "",
    val quantity: Int = 0,
    val location: Location = Location(),
    val details: Map<String, Any> = emptyMap()
) {
    data class Location(
        val warehouse: String = "",
        val zone: String = "",
        val shelf: String = ""
    )
}

/**
 * 简单的无注解事件
 */
data class SimpleEvent(
    val id: String = "",
    val value: String = ""
)

package com.only4.cap4k.ddd.application.event.persistence

/**
 * 测试数据工厂类
 * 为测试用例提供标准的测试数据
 */
object TestDataFactory {

    /**
     * 创建基本的EventHttpSubscriber实体
     */
    fun createEventHttpSubscriber(
        id: Long? = null,
        event: String = "test.event",
        subscriber: String = "test-service",
        callbackUrl: String = "http://localhost:8080/webhook",
        version: Int = 0
    ): EventHttpSubscriber {
        return EventHttpSubscriber(
            id = id,
            event = event,
            subscriber = subscriber,
            callbackUrl = callbackUrl,
            version = version
        )
    }

    /**
     * 创建用户相关事件的订阅者
     */
    fun createUserEventSubscriber(
        id: Long? = null,
        eventType: String = "user.created",
        serviceName: String = "email-service"
    ): EventHttpSubscriber {
        return EventHttpSubscriber(
            id = id,
            event = eventType,
            subscriber = serviceName,
            callbackUrl = "http://$serviceName:8080/webhooks/user",
            version = 0
        )
    }

    /**
     * 创建订单相关事件的订阅者
     */
    fun createOrderEventSubscriber(
        id: Long? = null,
        eventType: String = "order.placed",
        serviceName: String = "inventory-service"
    ): EventHttpSubscriber {
        return EventHttpSubscriber(
            id = id,
            event = eventType,
            subscriber = serviceName,
            callbackUrl = "http://$serviceName:9090/events/order",
            version = 0
        )
    }

    /**
     * 创建多个不同事件类型的订阅者列表
     */
    fun createMultipleSubscribers(): List<EventHttpSubscriber> {
        return listOf(
            createUserEventSubscriber(1L, "user.created", "email-service"),
            createUserEventSubscriber(2L, "user.created", "notification-service"),
            createUserEventSubscriber(3L, "user.updated", "search-service"),
            createOrderEventSubscriber(4L, "order.placed", "inventory-service"),
            createOrderEventSubscriber(5L, "order.shipped", "tracking-service")
        )
    }

    /**
     * 创建具有相同事件类型但不同订阅者的列表
     */
    fun createSameEventMultipleSubscribers(
        event: String = "user.created"
    ): List<EventHttpSubscriber> {
        return listOf(
            EventHttpSubscriber(
                id = 1L,
                event = event,
                subscriber = "email-service",
                callbackUrl = "http://email:8080/webhook"
            ),
            EventHttpSubscriber(
                id = 2L,
                event = event,
                subscriber = "audit-service",
                callbackUrl = "http://audit:9090/events"
            ),
            EventHttpSubscriber(
                id = 3L,
                event = event,
                subscriber = "analytics-service",
                callbackUrl = "http://analytics:8080/track"
            )
        )
    }

    /**
     * 创建带有特殊字符的订阅者
     */
    fun createSubscriberWithSpecialCharacters(): EventHttpSubscriber {
        return EventHttpSubscriber(
            id = 999L,
            event = "用户.创建",
            subscriber = "邮件-服务",
            callbackUrl = "http://localhost:8080/webhook?token=abc123&type=中文",
            version = 1
        )
    }

    /**
     * 创建用于边界值测试的订阅者
     */
    fun createBoundaryValueSubscriber(): EventHttpSubscriber {
        return EventHttpSubscriber(
            event = "a".repeat(255),
            subscriber = "b".repeat(255),
            callbackUrl = "http://example.com/" + "c".repeat(900)
        )
    }
}
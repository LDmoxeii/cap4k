package com.only4.cap4k.ddd.application.event

/**
 * 集成测试常量定义
 */
object TestConstants {

    // 事件类型常量
    object Events {
        const val USER_CREATED = "user.created"
        const val USER_UPDATED = "user.updated"
        const val USER_DELETED = "user.deleted"
        const val ORDER_PLACED = "order.placed"
        const val ORDER_SHIPPED = "order.shipped"
        const val ORDER_CANCELLED = "order.cancelled"
        const val PAYMENT_PROCESSED = "payment.processed"
        const val PAYMENT_FAILED = "payment.failed"
        const val INVENTORY_UPDATED = "inventory.updated"
        const val PRODUCT_CREATED = "product.created"
    }

    // 服务名称常量
    object Services {
        const val EMAIL_SERVICE = "email-service"
        const val NOTIFICATION_SERVICE = "notification-service"
        const val AUDIT_SERVICE = "audit-service"
        const val SEARCH_SERVICE = "search-service"
        const val INVENTORY_SERVICE = "inventory-service"
        const val TRACKING_SERVICE = "tracking-service"
        const val BILLING_SERVICE = "billing-service"
        const val ANALYTICS_SERVICE = "analytics-service"
        const val REPORTING_SERVICE = "reporting-service"
        const val CATALOG_SERVICE = "catalog-service"
    }

    // 回调URL常量
    object CallbackUrls {
        const val EMAIL_WEBHOOK = "http://email:8080/webhook"
        const val NOTIFICATION_EVENTS = "http://notification:9090/events"
        const val AUDIT_LOG = "http://audit:8080/log"
        const val SEARCH_INDEX = "http://search:8080/index"
        const val INVENTORY_EVENTS = "http://inventory:9090/events"
        const val TRACKING_WEBHOOK = "http://tracking:8080/webhook"
        const val BILLING_WEBHOOK = "http://billing:8080/webhook"
        const val ANALYTICS_TRACK = "http://analytics:8080/track"
        const val REPORTING_WEBHOOK = "http://reporting:8080/webhook"
        const val CATALOG_WEBHOOK = "http://catalog:8080/webhook"

        const val LOCALHOST_WEBHOOK = "http://localhost:8080/webhook"
        const val SECURE_WEBHOOK = "https://secure.example.com/webhook"
        const val WEBHOOK_WITH_TOKEN = "http://service:8080/webhook?token=abc123"
    }

    // 测试数据常量
    object TestData {
        const val DEFAULT_VERSION = 0
        const val UPDATED_VERSION = 1

        // 长字符串用于边界值测试
        val LONG_EVENT_NAME = "event." + "a".repeat(250)
        val LONG_SUBSCRIBER_NAME = "service-" + "b".repeat(248)
        val LONG_CALLBACK_URL = "http://example.com/webhook/" + "c".repeat(950)

        // 特殊字符测试
        const val CHINESE_EVENT = "用户.创建"
        const val CHINESE_SERVICE = "邮件-服务"
        const val UNICODE_CALLBACK = "http://localhost:8080/webhook?type=测试"

        // 空值和极端值
        const val EMPTY_STRING = ""
        const val SINGLE_CHAR = "a"
        const val MAX_ID = Long.MAX_VALUE
        const val MIN_ID = 1L
    }
}

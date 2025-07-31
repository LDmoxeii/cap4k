package com.only4.cap4k.ddd.application.persistence

import com.only4.cap4k.ddd.core.application.RequestParam

/**
 * 测试用的请求数据类
 */
data class TestRequestParam(
    val action: String = "",
    val data: Map<String, Any> = emptyMap(),
    val timestamp: Long = System.currentTimeMillis()
) : RequestParam<TestRequestParam>

/**
 * 用户创建请求参数
 */
data class CreateUserRequestParam(
    val username: String = "",
    val email: String = "",
    val role: String = "USER"
) : RequestParam<CreateUserRequestParam>

/**
 * 订单处理请求参数
 */
data class ProcessOrderRequestParam(
    val orderId: String = "",
    val customerId: String = "",
    val amount: Double = 0.0,
    val items: List<OrderItem> = emptyList()
) : RequestParam<ProcessOrderRequestParam> {
    data class OrderItem(
        val productId: String = "",
        val quantity: Int = 0,
        val price: Double = 0.0
    )
}

/**
 * 复杂的计算请求参数
 */
data class ComplexCalculationRequestParam(
    val calculationType: String = "",
    val parameters: Map<String, Any> = emptyMap(),
    val options: CalculationOptions = CalculationOptions()
) : RequestParam<ComplexCalculationRequestParam> {
    data class CalculationOptions(
        val precision: Int = 2,
        val timeout: Long = 30000,
        val enableCache: Boolean = true
    )
}

/**
 * 简单的请求参数
 */
data class SimpleRequestParam(
    val id: String = "",
    val value: String = ""
) : RequestParam<SimpleRequestParam>

/**
 * 测试结果数据类
 */
data class TestRequestResult(
    val success: Boolean = false,
    val message: String = "",
    val data: Any? = null,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * 用户创建结果
 */
data class CreateUserResult(
    val userId: String = "",
    val username: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * 订单处理结果
 */
data class ProcessOrderResult(
    val orderId: String = "",
    val status: String = "",
    val totalAmount: Double = 0.0,
    val processedAt: Long = System.currentTimeMillis()
)
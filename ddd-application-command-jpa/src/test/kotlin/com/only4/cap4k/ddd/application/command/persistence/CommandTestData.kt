package com.only4.cap4k.ddd.application.command.persistence

import com.only4.cap4k.ddd.core.application.command.Command

/**
 * 测试用的命令数据类
 */
data class TestCommand(
    val action: String = "",
    val data: Map<String, Any> = emptyMap(),
    val timestamp: Long = System.currentTimeMillis()
) : Command<TestCommand>

/**
 * 用户创建命令参数
 */
data class CreateUserCommand(
    val username: String = "",
    val email: String = "",
    val role: String = "USER"
) : Command<CreateUserCommand>

/**
 * 订单处理命令参数
 */
data class ProcessOrderCommand(
    val orderId: String = "",
    val customerId: String = "",
    val amount: Double = 0.0,
    val items: List<OrderItem> = emptyList()
) : Command<ProcessOrderCommand> {
    data class OrderItem(
        val productId: String = "",
        val quantity: Int = 0,
        val price: Double = 0.0
    )
}

/**
 * 复杂的计算命令参数
 */
data class ComplexCalculationCommand(
    val calculationType: String = "",
    val parameters: Map<String, Any> = emptyMap(),
    val options: CalculationOptions = CalculationOptions()
) : Command<ComplexCalculationCommand> {
    data class CalculationOptions(
        val precision: Int = 2,
        val timeout: Long = 30000,
        val enableCache: Boolean = true
    )
}

/**
 * 简单的命令参数
 */
data class SimpleCommand(
    val id: String = "",
    val value: String = ""
) : Command<SimpleCommand>

/**
 * 测试结果数据类
 */
data class TestCommandResult(
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
package com.only4.cap4k.ddd.application.saga

import com.only4.cap4k.ddd.core.application.saga.SagaParam

// 测试用Saga参数类
data class TestSagaParam(
    val action: String,
    val data: Map<String, Any>,
    val timestamp: Long = System.currentTimeMillis()
) : SagaParam<Any>

// 复杂Saga参数类
data class ComplexSagaParam(
    val orderId: String,
    val userId: String,
    val amount: Double,
    val items: List<SagaItem>
) : SagaParam<Any> {

    data class SagaItem(
        val productId: String,
        val quantity: Int
    )
}

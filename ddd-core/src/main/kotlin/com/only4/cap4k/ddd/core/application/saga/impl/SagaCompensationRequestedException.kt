package com.only4.cap4k.ddd.core.application.saga.impl

/**
 * Saga内部显式请求补偿时抛出的异常
 *
 * @author LD_moxeii
 * @date 2026/05/22
 */
internal class SagaCompensationRequestedException(
    val code: String,
    override val message: String
) : RuntimeException(message)

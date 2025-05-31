package com.only4.core.application.saga

import com.only4.core.application.RequestParam

/**
 * Saga参数接口
 * 用于定义Saga事务流程中的请求参数
 * 作为所有Saga流程参数的基接口，确保参数类型安全
 *
 * @author binking338
 * @date 2024/10/12
 *
 * @param RESULT 结果类型，表示Saga流程执行后的返回结果
 */
interface SagaParam<RESULT : Any> : RequestParam<RESULT>

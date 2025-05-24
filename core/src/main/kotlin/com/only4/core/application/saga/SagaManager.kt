package com.only4.core.application.saga

import java.time.LocalDateTime

/**
 * saga管理器
 *
 * @author binking338
 * @date 2025/5/16
 */
interface SagaManager {
    /**
     * 重新执行Saga流程
     *
     * @param saga
     * @return
     */
    fun resume(saga: SagaRecord)


    /**
     * 获取指定时间前需重试的请求
     *
     * @param maxNextTryTime 指定时间
     * @param limit          限制数量
     * @return
     */
    fun getByNextTryTime(maxNextTryTime: LocalDateTime, limit: Int): List<SagaRecord>

    /**
     * 归档指定时间前需重试的请求
     *
     * @param maxExpireAt 指定时间
     * @param limit       限制数量
     * @return
     */
    fun archiveByExpireAt(maxExpireAt: LocalDateTime, limit: Int): Int

    companion object {
        val instance: SagaManager
            /**
             * 获取请求管理器
             *
             * @return 请求管理器
             */
            get() = SagaSupervisorSupport.sagaManager
    }
}

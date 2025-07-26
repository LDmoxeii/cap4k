package com.only4.cap4k.ddd.core.application.saga

import java.time.LocalDateTime

/**
 * saga管理器
 *
 * @author LD_moxeii
 * @date 2025/07/26
 */
interface SagaManager {

    companion object {
        /**
         * 获取请求管理器
         *
         * @return 请求管理器实例
         */
        val instance: SagaManager
            get() = SagaSupervisorSupport.sagaManager
    }

    /**
     * 重新执行Saga流程
     *
     * @param saga Saga记录
     */
    fun resume(saga: SagaRecord)

    /**
     * 获取指定时间前需重试的请求
     *
     * @param maxNextTryTime 指定时间
     * @param limit          限制数量
     * @return Saga记录列表
     */
    fun getByNextTryTime(maxNextTryTime: LocalDateTime, limit: Int): List<SagaRecord>

    /**
     * 归档指定时间前需重试的请求
     *
     * @param maxExpireAt 指定时间
     * @param limit       限制数量
     * @return 归档数量
     */
    fun archiveByExpireAt(maxExpireAt: LocalDateTime, limit: Int): Int
}

package com.only4.cap4k.ddd.core.application.saga

import java.time.LocalDateTime

/**
 * Saga管理器接口
 * 负责管理Saga事务流程的执行、重试和归档
 * 提供Saga流程的生命周期管理功能
 *
 * @author binking338
 * @date 2025/5/16
 */
interface SagaManager {
    /**
     * 重新执行Saga流程
     * 用于恢复执行失败的Saga流程
     *
     * @param saga 需要重新执行的Saga记录
     * @return 重新执行的结果
     */
    fun resume(saga: SagaRecord)

    /**
     * 获取指定时间前需重试的Saga记录
     * 用于查询需要重试的Saga流程
     *
     * @param maxNextTryTime 最大下次重试时间，只获取在此时间之前的记录
     * @param limit 返回记录的最大数量
     * @return 需要重试的Saga记录列表
     */
    fun getByNextTryTime(maxNextTryTime: LocalDateTime, limit: Int): List<SagaRecord>

    /**
     * 归档指定时间前过期的Saga记录
     * 用于清理过期的Saga流程记录
     *
     * @param maxExpireAt 最大过期时间，只归档在此时间之前的记录
     * @param limit 单次归档的最大记录数
     * @return 实际归档的记录数量
     */
    fun archiveByExpireAt(maxExpireAt: LocalDateTime, limit: Int): Int

    companion object {
        /**
         * 获取Saga管理器实例
         * 通过SagaSupervisorSupport获取全局唯一的Saga管理器实例
         *
         * @return Saga管理器实例
         */
        val instance: SagaManager
            get() = SagaSupervisorSupport.sagaManager
    }
}

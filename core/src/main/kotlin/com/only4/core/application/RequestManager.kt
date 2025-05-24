package com.only4.core.application

import java.time.LocalDateTime

/**
 * 请求管理器
 *
 * @author binking338
 * @date 2025/5/17
 */
interface RequestManager {
    /**
     * 重新执行Saga流程
     *
     * @param request
     * @return
     */
    fun resume(request: RequestRecord)

    /**
     * 获取指定时间前需重试的请求
     *
     * @param maxNextTryTime 指定时间
     * @param limit          限制数量
     * @return
     */
    fun getByNextTryTime(maxNextTryTime: LocalDateTime, limit: Int): List<RequestRecord>

    /**
     * 归档指定时间前需重试的请求
     *
     * @param maxExpireAt   指定时间
     * @param limit         限制数量
     * @return
     */
    fun archiveByExpireAt(maxExpireAt: LocalDateTime, limit: Int): Int

    companion object {
        val instance: RequestManager
            /**
             * 获取请求管理器
             *
             * @return 请求管理器
             */
            get() = RequestSupervisorSupport.requestManager
    }
}

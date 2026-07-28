package com.only4.cap4k.ddd.core.application

import java.time.LocalDateTime

/**
 * 请求管理器接口
 * 负责管理和控制请求的执行、重试和归档
 * 提供请求生命周期管理功能
 *
 * @author LD_moxeii
 * @date 2025/07/20
 */
interface RequestManager {
    /**
     * 重新执行请求
     * 对指定的请求记录进行重新执行处理
     *
     * @param request 需要重新执行的请求记录
     * @throws IllegalStateException 当请求状态不允许重新执行时
     */
    fun resume(request: RequestRecord, minNextTryTime: LocalDateTime)

    /**
     * 重试请求
     *
     * @param uuid
     * @return
     */
    fun retry(uuid: String)

    /**
     * 获取需要重试的请求列表
     * 根据指定的时间获取需要重试的请求记录
     *
     * @param maxNextTryTime 最大下次执行时间，只获取在此时间之前的记录
     * @param limit 返回记录的最大数量
     * @return 需要重试的请求记录列表
     */
    fun getByNextTryTime(maxNextTryTime: LocalDateTime, limit: Int): List<RequestRecord>

    /**
     * 归档过期的请求记录
     * 将指定时间之前过期的请求记录进行归档处理
     *
     * @param maxExpireAt 最大过期时间，只归档在此时间之前的记录
     * @param limit 单次归档的最大记录数
     * @return 实际归档的记录数量
     */
    fun archiveByExpireAt(maxExpireAt: LocalDateTime, limit: Int): Int

    companion object {
        /**
         * 获取请求管理器实例
         * 通过RequestSupervisorSupport获取全局唯一的请求管理器实例
         *
         * @return 请求管理器实例
         */
        @JvmStatic
        val instance: RequestManager
            get() = RequestSupervisorSupport.requestManager
    }
}

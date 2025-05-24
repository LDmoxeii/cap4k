package com.only4.core.application

import java.time.LocalDateTime
import java.util.*

/**
 * RequestRecord仓储
 *
 * @author binking338
 * @date 2025/5/15
 */
interface RequestRecordRepository {
    /**
     * 创建
     *
     * @return [RequestRecord]
     */
    fun create(): RequestRecord

    /**
     * 保存
     *
     * @param requestRecord 请求记录
     */
    fun save(requestRecord: RequestRecord)

    /**
     * 通过id获取
     *
     * @param id 请求id
     * @return [RequestRecord]
     */
    fun getById(id: String): Optional<RequestRecord>

    /**
     * 通过下次尝试时间获取
     *
     * @param svcName        服务名
     * @param maxNextTryTime 最大下次尝试时间
     * @param limit          获取数量限制
     * @return [RequestRecord]列表
     */
    fun getByNextTryTime(
        svcName: String,
        maxNextTryTime: LocalDateTime,
        limit: Int
    ): List<RequestRecord>

    /**
     * 批量归档
     *
     * @param svcName     服务名
     * @param maxExpireAt 最大过期时间
     * @param limit       限制
     * @return 删除数量
     */
    fun archiveByExpireAt(svcName: String, maxExpireAt: LocalDateTime, limit: Int): Int
}

package com.only4.core.application.saga

import java.time.LocalDateTime

/**
 * SagaRecord仓储
 *
 * @author binking338
 * @date 2024/10/12
 */
interface SagaRecordRepository {
    /**
     * 创建SagaRecord
     *
     * @return SagaRecord
     */
    fun create(): SagaRecord

    /**
     * 保存SagaRecord
     *
     * @param sagaRecord SagaRecord
     */
    fun save(sagaRecord: SagaRecord)

    /**
     * 根据id获取SagaRecord
     *
     * @param id id
     * @return SagaRecord
     */
    fun getById(id: String): SagaRecord

    /**
     * 根据下次执行时间获取SagaRecord
     *
     * @param svcName        服务名
     * @param maxNextTryTime 最大下次执行时间
     * @param limit          限制数量
     * @return SagaRecord列表
     */
    fun getByNextTryTime(
        svcName: String,
        maxNextTryTime: LocalDateTime,
        limit: Int
    ): List<SagaRecord>

    /**
     * 根据过期时间归档SagaRecord
     *
     * @param svcName      服务名
     * @param maxExpireAt  最大过期时间
     * @param limit        限制数量
     * @return 归档数量
     */
    fun archiveByExpireAt(svcName: String, maxExpireAt: LocalDateTime, limit: Int): Int
}

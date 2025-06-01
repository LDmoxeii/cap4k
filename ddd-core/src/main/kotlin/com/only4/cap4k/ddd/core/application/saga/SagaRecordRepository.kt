package com.only4.cap4k.ddd.core.application.saga

import java.time.LocalDateTime

/**
 * Saga记录仓储接口
 * 负责Saga记录的持久化存储和检索
 * 提供Saga记录的基本CRUD操作和归档功能
 *
 * @author binking338
 * @date 2024/10/12
 */
interface SagaRecordRepository {
    /**
     * 创建新的Saga记录实例
     * 返回一个未初始化的Saga记录对象
     *
     * @return 新的Saga记录实例
     */
    fun create(): SagaRecord

    /**
     * 保存Saga记录
     * 将Saga记录持久化到存储中
     *
     * @param sagaRecord 要保存的Saga记录
     */
    fun save(sagaRecord: SagaRecord)

    /**
     * 根据ID获取Saga记录
     * 从存储中检索指定ID的Saga记录
     *
     * @param id Saga记录的唯一标识
     * @return 找到的Saga记录，如果不存在则抛出异常
     */
    fun getById(id: String): SagaRecord

    /**
     * 获取需要重试的Saga记录
     * 根据服务名和下次执行时间查询需要重试的Saga记录
     *
     * @param svcName 服务名称，用于筛选特定服务的Saga记录
     * @param maxNextTryTime 最大下次执行时间，只获取在此时间之前的记录
     * @param limit 返回记录的最大数量
     * @return 需要重试的Saga记录列表
     */
    fun getByNextTryTime(
        svcName: String,
        maxNextTryTime: LocalDateTime,
        limit: Int
    ): List<SagaRecord>

    /**
     * 归档过期的Saga记录
     * 将指定时间之前过期的Saga记录进行归档处理
     *
     * @param svcName 服务名称，用于筛选特定服务的Saga记录
     * @param maxExpireAt 最大过期时间，只归档在此时间之前的记录
     * @param limit 单次归档的最大记录数
     * @return 实际归档的记录数量
     */
    fun archiveByExpireAt(svcName: String, maxExpireAt: LocalDateTime, limit: Int): Int
}

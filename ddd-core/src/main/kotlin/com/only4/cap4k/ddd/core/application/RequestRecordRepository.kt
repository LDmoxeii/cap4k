package com.only4.cap4k.ddd.core.application

import java.time.LocalDateTime
import java.util.*

/**
 * 请求记录仓储接口
 * 负责请求记录的持久化存储和检索
 * 提供请求记录的基本CRUD操作和归档功能
 *
 * @author binking338
 * @date 2025/5/15
 */
interface RequestRecordRepository {
    /**
     * 创建新的请求记录实例
     * 返回一个未初始化的请求记录对象
     *
     * @return 新的请求记录实例
     * @throws IllegalStateException 当创建失败时
     */
    fun create(): RequestRecord

    /**
     * 保存请求记录
     * 将请求记录持久化到存储中
     *
     * @param requestRecord 要保存的请求记录
     * @throws IllegalArgumentException 当请求记录无效时
     */
    fun save(requestRecord: RequestRecord)

    /**
     * 根据ID获取请求记录
     * 从存储中检索指定ID的请求记录
     *
     * @param id 请求记录的唯一标识
     * @return 找到的请求记录，如果不存在则返回空
     */
    fun getById(id: String): Optional<RequestRecord>

    /**
     * 获取需要重试的请求记录
     * 根据服务名和下次执行时间查询需要重试的请求记录
     *
     * @param svcName 服务名称，用于筛选特定服务的请求记录
     * @param maxNextTryTime 最大下次执行时间，只获取在此时间之前的记录
     * @param limit 返回记录的最大数量
     * @return 需要重试的请求记录列表
     */
    fun getByNextTryTime(
        svcName: String,
        maxNextTryTime: LocalDateTime,
        limit: Int
    ): List<RequestRecord>

    /**
     * 归档过期的请求记录
     * 将指定时间之前过期的请求记录进行归档处理
     *
     * @param svcName 服务名称，用于筛选特定服务的请求记录
     * @param maxExpireAt 最大过期时间，只归档在此时间之前的记录
     * @param limit 单次归档的最大记录数
     * @return 实际归档的记录数量
     */
    fun archiveByExpireAt(svcName: String, maxExpireAt: LocalDateTime, limit: Int): Int
}

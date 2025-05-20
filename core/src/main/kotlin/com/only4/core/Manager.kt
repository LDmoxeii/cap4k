package com.only4.core

import com.only4.core.application.RequestRecord
import com.only4.core.application.saga.SagaRecord
import java.time.LocalDateTime

/**
 * 领域事件发布管理器
 *
 * @author binking338
 * @date 2024/9/11
 */
interface DomainEventManager {
    /**
     * 发布附加到指定实体以及所有未附加到实体的领域事件
     * @param entities 指定实体集合
     */
    fun release(entities: Set<Any>)
}

/**
 * 集成事件管理器
 *
 * @author binking338
 * @date 2024/9/11
 */
interface IntegrationEventManager {
    /**
     * 发布附加到持久化上下文的所有集成事件
     */
    fun release()
}

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

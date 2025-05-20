package com.only4.core

import com.only4.core.application.RequestRecord
import com.only4.core.application.saga.SagaRecord
import com.only4.core.domain.aggregate.Specification
import com.only4.core.domain.event.EventSubscriber
import com.only4.core.domain.repo.PersistType
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

/**
 * 实体规格约束管理器
 *
 * @author binking338
 * @date 2023/8/5
 */
interface SpecificationManager {
    /**
     * 校验实体是否符合规格约束
     *
     * @param entity
     * @param <Entity>
     * @return
    </Entity> */
    fun <Entity> specifyInTransaction(entity: Entity): Specification.Result

    /**
     * 校验实体是否符合规格约束（事务开启前）
     *
     * @param entity
     * @param <Entity>
     * @return
    </Entity> */
    fun <Entity> specifyBeforeTransaction(entity: Entity): Specification.Result
}

/**
 * 持久化监听管理器
 *
 * @author binking338
 * @date 2024/1/31
 */
interface PersistListenerManager {
    fun <Entity> onChange(aggregate: Entity, type: PersistType)
}

/**
 * 领域事件拦截器管理器
 *
 * @author binking338
 * @date 2024/9/12
 */
interface DomainEventInterceptorManager {
    /**
     * 拦截器基于 [org.springframework.core.annotation.Order] 排序
     * @return
     */
    val orderedDomainEventInterceptors: Set<Any>

    /**
     *
     * 拦截器基于 [org.springframework.core.annotation.Order] 排序
     * @return
     */
    val orderedEventInterceptors4DomainEvent: Set<Any>
}

/**
 * 事件消息拦截器管理器
 *
 * @author binking338
 * @date 2024/9/12
 */
interface EventMessageInterceptorManager {
    /**
     * 拦截器基于 [org.springframework.core.annotation.Order] 排序
     * @return
     */
    val orderedEventMessageInterceptors: Set<Any>
}

/**
 * 领域事件订阅管理器接口
 *
 * @author binking338
 * @date 2023/8/13
 */
interface EventSubscriberManager {
    /**
     * 订阅事件
     *
     * @param eventPayloadClass
     * @param subscriber
     * @return
     */
    fun subscribe(
        eventPayloadClass: Class<*>,
        subscriber: EventSubscriber<*>
    ): Boolean

    /**
     * 取消订阅
     *
     * @param eventPayloadClass
     * @param subscriber
     * @return
     */
    fun unsubscribe(
        eventPayloadClass: Class<*>,
        subscriber: EventSubscriber<*>
    ): Boolean

    /**
     * 分发事件到所有订阅者
     *
     * @param eventPayload
     */
    fun dispatch(eventPayload: Any)
}

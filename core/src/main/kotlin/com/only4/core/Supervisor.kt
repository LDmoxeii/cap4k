package com.only4.core

import com.only4.core.application.RequestParam
import com.only4.core.application.saga.SagaParam
import com.only4.core.domain.aggregate.Aggregate
import com.only4.core.domain.aggregate.AggregatePayload
import com.only4.core.domain.aggregate.Id
import com.only4.core.domain.repo.AggregatePredicate
import com.only4.core.domain.repo.Predicate
import com.only4.core.share.OrderInfo
import com.only4.core.share.PageData
import com.only4.core.share.PageParam
import org.springframework.transaction.annotation.Propagation
import java.time.Duration
import java.time.LocalDateTime
import java.util.*

/**
 * 聚合工厂管理器
 *
 * @author binking338
 * @date 2024/9/3
 */
interface AggregateFactorySupervisor {
    /**
     * 创建新聚合实例
     *
     * @param entityPayload
     * @return
     * @param <ENTITY_PAYLOAD>
     * @param <ENTITY>
    </ENTITY></ENTITY_PAYLOAD> */
    fun <ENTITY_PAYLOAD : AggregatePayload<ENTITY>, ENTITY> create(entityPayload: ENTITY_PAYLOAD): ENTITY

    companion object {
        val instance: AggregateFactorySupervisor
            get() = AggregateFactorySupervisorSupport.instance
    }
}

/**
 * 仓储管理器
 *
 * @author binking338
 * @date 2024/8/25
 */
interface RepositorySupervisor {

    /**
     * 根据条件获取实体列表
     *
     * @param predicate
     * @param orders
     * @param <ENTITY>
     * @return
    </ENTITY> */
    fun <ENTITY> find(
        predicate: Predicate<ENTITY>,
        vararg orders: OrderInfo,
        persist: Boolean = true,
    ): List<ENTITY> {
        return find(predicate, listOf(*orders), persist)
    }

    /**
     * 根据条件获取实体列表
     *
     * @param predicate
     * @param orders
     * @param <ENTITY>
     * @return
    </ENTITY> */
    fun <ENTITY> find(
        predicate: Predicate<ENTITY>,
        orders: Collection<OrderInfo> = emptyList(),
        persist: Boolean = true,
    ): List<ENTITY>


    /**
     * 根据条件获取实体列表
     *
     * @param predicate
     * @param pageParam
     * @param persist
     * @return
     */
    fun <ENTITY> find(
        predicate: Predicate<ENTITY>,
        pageParam: PageParam,
        persist: Boolean = true,
    ): List<ENTITY>

    /**
     * 根据条件获取单个实体
     *
     * @param predicate
     * @param persist
     * @param <ENTITY>
     * @return
    </ENTITY> */
    fun <ENTITY> findOne(
        predicate: Predicate<ENTITY>,
        persist: Boolean = true
    ): Optional<ENTITY>

    /**
     * 根据条件获取实体
     *
     * @param predicate
     * @param orders
     * @param persist
     * @param <ENTITY>
     * @return
    </ENTITY> */
    fun <ENTITY> findFirst(
        predicate: Predicate<ENTITY>,
        orders: Collection<OrderInfo> = listOf(),
        persist: Boolean = true,
    ): Optional<ENTITY>

    /**
     * 根据条件获取实体
     *
     * @param predicate
     * @param orders
     * @param persist
     * @param <ENTITY>
     * @return
    </ENTITY> */
    fun <ENTITY> findFirst(
        predicate: Predicate<ENTITY>,
        vararg orders: OrderInfo,
        persist: Boolean = true,
    ): Optional<ENTITY> {
        return findFirst(predicate, listOf(*orders), persist)
    }

    /**
     * 根据条件获取实体分页列表
     * 自动调用 UnitOfWork::persist
     *
     * @param predicate
     * @param pageParam
     * @param persist
     * @param <ENTITY>
     * @return
    </ENTITY> */
    fun <ENTITY> findPage(
        predicate: Predicate<ENTITY>,
        pageParam: PageParam,
        persist: Boolean = true
    ): PageData<ENTITY>

    /**
     * 根据条件删除实体
     *
     * @param predicate
     * @param limit
     * @param <ENTITY>
     * @return
    </ENTITY> */
    fun <ENTITY> remove(predicate: Predicate<ENTITY>, limit: Int = 1): List<ENTITY>

    /**
     * 根据条件获取实体计数
     *
     * @param predicate
     * @param <ENTITY>
     * @return
    </ENTITY> */
    fun <ENTITY> count(predicate: Predicate<ENTITY>): Long

    /**
     * 根据条件判断实体是否存在
     *
     * @param predicate
     * @param <ENTITY>
     * @return
    </ENTITY> */
    fun <ENTITY> exists(predicate: Predicate<ENTITY>): Boolean

    companion object {
        val instance: RepositorySupervisor
            get() = RepositorySupervisorSupport.instance
    }
}

/**
 * 领域事件管理器
 *
 * @author binking338
 * @date 2023/8/12
 */
interface DomainEventSupervisor {

    /**
     * 附加领域事件到持久化上下文
     * @param domainEventPayload 领域事件消息体
     * @param entity 绑定实体，该实体对象进入持久化上下文且事务提交时才会触发领域事件分发
     * @param delay 延迟发送
     * @param schedule 指定时间发送
     */
    fun <DOMAIN_EVENT, ENTITY> attach(
        domainEventPayload: DOMAIN_EVENT,
        entity: ENTITY,
        schedule: LocalDateTime = LocalDateTime.now(),
        delay: Duration = Duration.ZERO
    )

    /**
     * 从持久化上下文剥离领域事件
     * @param domainEventPayload 领域事件消息体
     * @param entity 关联实体
     */
    fun <DOMAIN_EVENT, ENTITY> detach(domainEventPayload: DOMAIN_EVENT, entity: ENTITY)

    companion object {
        val instance: DomainEventSupervisor
            /**
             * 获取领域事件管理器
             * @return 领域事件管理器
             */
            get() = DomainEventSupervisorSupport.instance

        val manager: DomainEventManager
            /**
             * 获取领域事件发布管理器
             * @return
             */
            get() = DomainEventSupervisorSupport.manager
    }
}

/**
 * 聚合管理器
 *
 * @author binking338
 * @date 2025/1/12
 */
interface AggregateSupervisor {

    fun <AGGREGATE : Aggregate<ENTITY>, ENTITY_PAYLOAD : AggregatePayload<ENTITY>, ENTITY> create(
        clazz: Class<AGGREGATE>,
        payload: ENTITY_PAYLOAD
    ): AGGREGATE

    /**
     * 根据id获取聚合
     *
     * @param id
     * @param persist
     * @param <AGGREGATE>
     * @param <ENTITY>
     * @return
    </ENTITY></AGGREGATE> */
    fun <AGGREGATE : Aggregate<ENTITY>, ENTITY> getById(
        id: Id<AGGREGATE, *>,
        persist: Boolean = true
    ): Optional<AGGREGATE> {
        return Optional.ofNullable(
            getByIds(listOf(id), persist).firstOrNull()
        )
    }

    /**
     * 根据id获取聚合
     *
     * @param ids
     * @param <AGGREGATE>
     * @param <ENTITY>
     * @return
    </ENTITY></AGGREGATE> */
    fun <AGGREGATE : Aggregate<ENTITY>, ENTITY> getByIds(vararg ids: Id<AGGREGATE, *>): List<AGGREGATE> {
        return getByIds(listOf(*ids))
    }

    /**
     * 根据id获取聚合
     *
     * @param ids
     * @param <AGGREGATE>
     * @param <ENTITY>
     * @return
    </ENTITY></AGGREGATE> */
    fun <AGGREGATE : Aggregate<ENTITY>, ENTITY> getByIds(
        ids: Iterable<Id<AGGREGATE, *>>,
        persist: Boolean = true
    ): List<AGGREGATE>

    /**
     * 根据条件获取聚合列表
     *
     * @param predicate
     * @param orders
     * @param <AGGREGATE>
     * @return
     */
    fun <ENTITY, AGGREGATE : Aggregate<ENTITY>> find(
        predicate: AggregatePredicate<ENTITY, AGGREGATE>,
        vararg orders: OrderInfo,
        persist: Boolean = true
    ): List<AGGREGATE> {
        return find(predicate, listOf(*orders), persist)
    }

    /**
     * 根据条件获取聚合列表
     *
     * @param predicate
     * @param orders
     * @param <AGGREGATE>
     * @return
    </AGGREGATE> */
    fun <ENTITY, AGGREGATE : Aggregate<ENTITY>> find(
        predicate: AggregatePredicate<ENTITY, AGGREGATE>,
        orders: Collection<OrderInfo> = emptyList(),
        persist: Boolean = true
    ): List<AGGREGATE>

    /**
     * 根据条件获取聚合列表
     *
     * @param predicate
     * @param pageParam
     * @param persist
     * @return
     */
    fun <ENTITY, AGGREGATE : Aggregate<ENTITY>> find(
        predicate: AggregatePredicate<ENTITY, AGGREGATE>,
        pageParam: PageParam,
        persist: Boolean = true
    ): List<AGGREGATE>

    /**
     * 根据条件获取单个实体
     *
     * @param predicate
     * @param persist
     * @param <AGGREGATE>
     * @return
    </AGGREGATE> */
    fun <ENTITY, AGGREGATE : Aggregate<ENTITY>> findOne(
        predicate: AggregatePredicate<ENTITY, AGGREGATE>,
        persist: Boolean = true
    ): Optional<AGGREGATE>

    /**
     * 根据条件获取实体
     *
     * @param predicate
     * @param orders
     * @param persist
     * @param <AGGREGATE>
     * @return
    </AGGREGATE> */
    fun <ENTITY, AGGREGATE : Aggregate<ENTITY>> findFirst(
        predicate: AggregatePredicate<ENTITY, AGGREGATE>,
        vararg orders: OrderInfo,
        persist: Boolean = true
    ): Optional<AGGREGATE> {
        return findFirst(predicate, listOf(*orders), persist)
    }

    /**
     * 根据条件获取实体
     *
     * @param predicate
     * @param orders
     * @param persist
     * @param <AGGREGATE>
     * @return
    </AGGREGATE> */
    fun <ENTITY, AGGREGATE : Aggregate<ENTITY>> findFirst(
        predicate: AggregatePredicate<ENTITY, AGGREGATE>,
        orders: Collection<OrderInfo> = emptyList(),
        persist: Boolean = true
    ): Optional<AGGREGATE>

    /**
     * 根据条件获取实体分页列表
     *
     * @param predicate
     * @param pageParam
     * @param persist
     * @param <AGGREGATE>
     * @return
    </AGGREGATE> */
    fun <ENTITY, AGGREGATE : Aggregate<ENTITY>> findPage(
        predicate: AggregatePredicate<ENTITY, AGGREGATE>,
        pageParam: PageParam,
        persist: Boolean = true
    ): PageData<AGGREGATE>

    /**
     * 根据id删除聚合
     *
     * @param id
     * @param <AGGREGATE>
     * @param <ENTITY>
     * @return
    </ENTITY></AGGREGATE> */
    fun <ENTITY, AGGREGATE : Aggregate<ENTITY>> removeById(id: Id<AGGREGATE, *>): Optional<AGGREGATE> {
        return Optional.ofNullable(removeByIds(listOf(id)).firstOrNull())
    }

    /**
     * 根据id删除聚合
     *
     * @param ids
     * @param <AGGREGATE>
     * @param <ENTITY>
     * @return
    </ENTITY></AGGREGATE> */
    fun <ENTITY, AGGREGATE : Aggregate<ENTITY>> removeByIds(vararg ids: Id<AGGREGATE, *>): List<AGGREGATE> {
        return removeByIds(listOf(*ids))
    }

    /**
     * 根据id删除聚合
     *
     * @param ids
     * @param <AGGREGATE>
     * @param <ENTITY>
     * @return
    </ENTITY></AGGREGATE> */
    fun <ENTITY, AGGREGATE : Aggregate<ENTITY>> removeByIds(ids: Iterable<Id<AGGREGATE, *>>): List<AGGREGATE>

    /**
     * 根据条件删除实体
     *
     * @param predicate
     * @param limit
     * @param <AGGREGATE>
     * @return
    </AGGREGATE> */
    fun <ENTITY, AGGREGATE : Aggregate<ENTITY>> remove(
        predicate: AggregatePredicate<ENTITY, AGGREGATE>,
        limit: Int = 1
    ): List<AGGREGATE>

    /**
     * 根据条件获取实体计数
     *
     * @param predicate
     * @param <AGGREGATE>
     * @return
    </AGGREGATE> */
    fun <ENTITY, AGGREGATE : Aggregate<ENTITY>> count(predicate: AggregatePredicate<ENTITY, AGGREGATE>): Long

    /**
     * 根据条件判断实体是否存在
     *
     * @param predicate
     * @param <AGGREGATE>
     * @return
    </AGGREGATE> */
    fun <ENTITY, AGGREGATE : Aggregate<ENTITY>> exists(predicate: AggregatePredicate<ENTITY, AGGREGATE>): Boolean

    companion object {
        val instance: AggregateSupervisor
            get() = AggregateSupervisorSupport.instance
    }

}

/**
 * 领域服务管理器
 *
 * @author binking338
 * @date 2024/9/4
 */
interface DomainServiceSupervisor {
    /**
     * 获取领域服务
     * @param domainServiceClass
     * @return
     * @param <DOMAIN_SERVICE>
    </DOMAIN_SERVICE> */
    fun <DOMAIN_SERVICE> getService(domainServiceClass: Class<DOMAIN_SERVICE>): DOMAIN_SERVICE

    companion object {
        val instance: DomainServiceSupervisor
            get() = DomainServiceSupervisorSupport.instance
    }
}

/**
 * UnitOfWork模式
 *
 * @author binking338
 * @date 2023/8/5
 */
interface UnitOfWork {
    /**
     * 提交新增或更新实体持久化记录意图到UnitOfWork上下文
     *
     * @param entity 实体对象
     */
    fun persist(entity: Any)

    /**
     * 提交新增实体持久化记录意图到UnitOfWork上下文，如果实体已存在则不提交
     * @param entity
     * @return 是否提交
     */
    fun persistIfNotExist(entity: Any): Boolean

    /**
     * 提交移除实体持久化记录意图到UnitOfWork上下文
     *
     * @param entity 实体对象
     */
    fun remove(entity: Any)

    /**
     * 将持久化意图转换成持久化指令，并提交事务
     *
     * @param propagation 事务传播特性
     */
    fun save(propagation: Propagation = Propagation.REQUIRED)

    companion object {
        val instance: UnitOfWork
            get() = UnitOfWorkSupport.instance
    }
}

/**
 * 集成事件控制器
 *
 * @author binking338
 * @date 2024/8/25
 */
interface IntegrationEventSupervisor {

    /**
     * 附加事件到持久化上下文
     *
     * @param eventPayload 事件消息体
     * @param schedule     指定时间发送
     */
    fun <EVENT> attach(
        eventPayload: EVENT,
        schedule: LocalDateTime = LocalDateTime.now(),
        delay: Duration = Duration.ZERO
    )

    /**
     * 从持久化上下文剥离事件
     *
     * @param eventPayload 事件消息体
     */
    fun <EVENT> detach(eventPayload: EVENT)

    /**
     * 发布指定集成事件
     * @param eventPayload 集成事件负载
     * @param schedule     指定时间发送
     */
    fun <EVENT> publish(
        eventPayload: EVENT,
        schedule: LocalDateTime = LocalDateTime.now(),
        delay: Duration = Duration.ZERO
    )

    companion object {
        val instance: IntegrationEventSupervisor
            get() = IntegrationEventSupervisorSupport.instance

        val manager: IntegrationEventManager
            get() = IntegrationEventSupervisorSupport.manager
    }
}

/**
 * 请求管理器
 *
 * @author binking338
 * @date 2024/8/24
 */
interface RequestSupervisor {
    /**
     * 执行请求
     *
     * @param request    请求参数
     * @param <REQUEST>  请求参数类型
     * @param <RESPONSE> 响应参数类型
    </RESPONSE></REQUEST> */
    fun <RESPONSE, REQUEST : RequestParam<RESPONSE>> send(request: REQUEST): RESPONSE

    /**
     * 异步执行请求
     *
     * @param request    请求参数
     * @param <REQUEST>  请求参数类型
     * @param <RESPONSE> 响应参数类型
     * @return 请求ID
    </RESPONSE></REQUEST> */
    fun <RESPONSE, REQUEST : RequestParam<RESPONSE>> async(request: REQUEST): String {
        return schedule(request, LocalDateTime.now())
    }

    /**
     * 延迟执行请求
     *
     * @param request    请求参数
     * @param schedule   计划时间
     * @param <REQUEST>  请求参数类型
     * @param <RESPONSE> 响应参数类型
     * @return 请求ID
    </RESPONSE></REQUEST> */
    fun <RESPONSE, REQUEST : RequestParam<RESPONSE>> schedule(
        request: REQUEST,
        schedule: LocalDateTime
    ): String

    /**
     * 延迟执行请求
     *
     * @param request    请求参数
     * @param delay      延迟时间
     * @param <REQUEST>  请求参数类型
     * @param <RESPONSE> 响应参数类型
     * @return 请求ID
    </RESPONSE></REQUEST> */
    fun <RESPONSE, REQUEST : RequestParam<RESPONSE>> delay(
        request: REQUEST,
        delay: Duration
    ): String {
        return schedule(request, LocalDateTime.now().plus(delay))
    }

    /**
     * 获取请求结果
     *
     * @param requestId    请求ID
     * @param requestClass 请求参数类型
     * @param <REQUEST>    请求参数类型
     * @param <RESPONSE>   响应参数类型
     * @return 请求结果
    </RESPONSE></REQUEST> */
    @Suppress("UNCHECKED_CAST")
    fun <RESPONSE, REQUEST : RequestParam<RESPONSE>> result(
        requestId: String,
        requestClass: Class<REQUEST> = Any::class.java as Class<REQUEST>
    ): Optional<RESPONSE>

    companion object {
        val instance: RequestSupervisor
            /**
             * 获取请求管理器
             *
             * @return 请求管理器
             */
            get() = RequestSupervisorSupport.instance
    }
}

/**
 * Saga子环节执行器
 *
 * @author binking338
 * @date 2024/10/14
 */
interface SagaProcessSupervisor {
    /**
     * 执行Saga子环节
     *
     * @param processCode Saga子环节标识
     * @param request     请求参数
     * @param <REQUEST>   请求参数类型
     * @param <RESPONSE>  响应参数类型
     * @return
    </RESPONSE></REQUEST> */
    fun <RESPONSE, REQUEST : RequestParam<RESPONSE>> sendProcess(
        processCode: String,
        request: REQUEST
    ): RESPONSE

    companion object {
        val instance: SagaProcessSupervisor
            /**
             * 获取Saga子环节执行管理器
             *
             * @return
             */
            get() = SagaSupervisorSupport.sagaProcessSupervisor
    }
}

/**
 * Saga控制器
 *
 * @author binking338
 * @date 2024/10/12
 */
interface SagaSupervisor {
    /**
     * 执行Saga流程
     *
     * @param request   请求参数
     * @param <REQUEST> 请求参数类型
    </REQUEST> */
    fun <RESPONSE, REQUEST : SagaParam<RESPONSE>> send(request: REQUEST): RESPONSE

    /**
     * 异步执行Saga流程
     *
     * @param request
     * @param <REQUEST>
     * @param <RESPONSE> 响应参数类型
     * @return Saga ID
    </RESPONSE></REQUEST> */
    fun <RESPONSE, REQUEST : SagaParam<RESPONSE>> async(request: REQUEST): String {
        return schedule(request, LocalDateTime.now())
    }

    /**
     * 延迟执行请求
     *
     * @param request    请求参数
     * @param schedule   计划时间
     * @param <REQUEST>  请求参数类型
     * @param <RESPONSE> 响应参数类型
     * @return 请求ID
    </RESPONSE></REQUEST> */
    fun <RESPONSE, REQUEST : SagaParam<RESPONSE>> schedule(request: REQUEST, schedule: LocalDateTime, delay: Duration = Duration.ZERO): String

    /**
     * 获取Saga结果
     *
     * @param id  Saga ID
     * @param <R>
     * @return 请求结果
    </R> */
    fun <R> result(id: String): R

    /**
     * 获取Saga结果
     *
     * @param requestId    请求ID
     * @param requestClass 请求参数类型
     * @param <REQUEST>    请求参数类型
     * @param <RESPONSE>   响应参数类型
     * @return 请求结果
    </RESPONSE></REQUEST> */
    @Suppress("UNCHECKED_CAST")
    fun <RESPONSE, REQUEST : SagaParam<RESPONSE>> result(
        requestId: String,
        requestClass: Class<REQUEST> = Any::class.java as Class<REQUEST>
    ): Optional<RESPONSE>

    companion object {
        val instance: SagaSupervisor
            /**
             * 获取请求管理器
             *
             * @return 请求管理器
             */
            get() = SagaSupervisorSupport.instance
    }
}

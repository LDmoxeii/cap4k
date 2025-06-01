package com.only4.cap4k.ddd.core

import com.only4.cap4k.ddd.core.application.RequestParam
import com.only4.cap4k.ddd.core.application.RequestSupervisor
import com.only4.cap4k.ddd.core.application.UnitOfWork
import com.only4.cap4k.ddd.core.application.event.IntegrationEventSupervisor
import com.only4.cap4k.ddd.core.application.saga.SagaSupervisor
import com.only4.cap4k.ddd.core.domain.aggregate.Aggregate
import com.only4.cap4k.ddd.core.domain.aggregate.AggregateFactorySupervisor
import com.only4.cap4k.ddd.core.domain.aggregate.AggregatePayload
import com.only4.cap4k.ddd.core.domain.aggregate.Id
import com.only4.cap4k.ddd.core.domain.repo.AggregatePredicate
import com.only4.cap4k.ddd.core.domain.repo.AggregateSupervisor
import com.only4.cap4k.ddd.core.domain.repo.Predicate
import com.only4.cap4k.ddd.core.domain.repo.RepositorySupervisor
import com.only4.cap4k.ddd.core.domain.service.DomainServiceSupervisor
import com.only4.cap4k.ddd.core.share.OrderInfo
import com.only4.cap4k.ddd.core.share.PageData
import com.only4.cap4k.ddd.core.share.PageParam
import org.springframework.context.ApplicationContext
import org.springframework.transaction.annotation.Propagation
import java.time.Duration
import java.time.LocalDateTime
import java.util.*

/**
 * 中介者
 *
 * @author binking338
 * @date 2024/8/24
 */
interface Mediator : AggregateFactorySupervisor,
    RepositorySupervisor,
    AggregateSupervisor,
    DomainServiceSupervisor,
    UnitOfWork,
    IntegrationEventSupervisor,
    RequestSupervisor {

    val applicationContext: ApplicationContext
        get() = MediatorSupport.ioc

    val aggregateFactorySupervisor: AggregateFactorySupervisor
        get() = AggregateFactorySupervisor.instance


    val repositorySupervisor: RepositorySupervisor
        get() = RepositorySupervisor.instance

    val aggregateSupervisor: AggregateSupervisor
        get() = AggregateSupervisor.instance

    val unitOfWork: UnitOfWork
        get() = UnitOfWork.instance

    val integrationEventSupervisor: IntegrationEventSupervisor
        get() = IntegrationEventSupervisor.instance

    val requestSupervisor: RequestSupervisor
        get() = RequestSupervisor.instance

    val sagaSupervisor: SagaSupervisor
        get() = SagaSupervisor.instance

    companion object {
        val instance: Mediator
            get() = MediatorSupport.instance

        /**
         * 获取IOC容器
         *
         * @return
         */
        fun ioc(): ApplicationContext = MediatorSupport.ioc

        /**
         * 获取聚合工厂管理器
         *
         * @return
         */
        fun factories(): AggregateFactorySupervisor = AggregateFactorySupervisor.instance

        /**
         * 获取聚合仓储管理器
         *
         * @return
         */
        fun repositories(): RepositorySupervisor = RepositorySupervisor.instance

        /**
         * 获取聚合管理器
         *
         * @return
         */
        fun aggregates(): AggregateSupervisor = AggregateSupervisor.instance

        /**
         * 获取领域服务管理器
         *
         * @return
         */
        fun services(): DomainServiceSupervisor = DomainServiceSupervisor.instance

        /**
         * 获取集成事件管理器
         *
         * @return
         */
        fun events(): IntegrationEventSupervisor = IntegrationEventSupervisor.instance


        /**
         * 获取请求管理器
         * 兼容 cmd() qry()，当前三者实现一致。
         *
         * @return
         */
        fun requests(): RequestSupervisor = RequestSupervisor.instance

        /**
         * 获取命令管理器
         *
         * @return
         */
        fun commands(): RequestSupervisor = RequestSupervisor.instance

        /**
         * 获取Saga管理器
         *
         * @return
         */
        fun sagas(): SagaSupervisor = SagaSupervisor.instance

        /**
         * 获取查询管理器
         *
         * @return
         */
        fun queries(): RequestSupervisor = RequestSupervisor.instance


        /**
         * 获取聚合工厂管理器(shortcut for factories)
         *
         * @return
         */
        fun fac(): AggregateFactorySupervisor = AggregateFactorySupervisor.instance

        /**
         * 获取聚合仓储管理器(shortcut for repositories)
         *
         * @return
         */
        fun repo(): RepositorySupervisor = RepositorySupervisor.instance

        /**
         * 获取聚合管理器(shortcut for aggregates)
         *
         * @return
         */
        fun agg(): AggregateSupervisor = AggregateSupervisor.instance

        /**
         * 获取领域服务管理器(shortcut for services)
         *
         * @return
         */
        fun svc(): DomainServiceSupervisor = DomainServiceSupervisor.instance

        /**
         * 获取单元工作单元
         *
         * @return
         */
        fun uow(): UnitOfWork = UnitOfWork.instance

        /**
         * 获取请求管理器(shortcut for requests)
         *
         * @return
         */
        fun req(): RequestSupervisor = RequestSupervisor.instance

        /**
         * 获取命令管理器(shortcut for commands)
         *
         * @return
         */
        fun cmd(): RequestSupervisor = RequestSupervisor.instance

        /**
         * 获取查询管理器(shortcut for queries)
         *
         * @return
         */
        fun qry(): RequestSupervisor = RequestSupervisor.instance
    }
}

/**
 * Mediator的短别名引用
 *
 * @author binking338
 * @date 2025/4/7
 */
interface X {
    companion object {
        /**
         * 获取IOC容器
         *
         * @return
         */
        fun ioc(): ApplicationContext = MediatorSupport.ioc

        /**
         * 获取聚合工厂管理器
         *
         * @return
         */
        fun factories(): AggregateFactorySupervisor = AggregateFactorySupervisor.instance

        /**
         * 获取聚合仓储管理器
         *
         * @return
         */
        fun repositories(): RepositorySupervisor = RepositorySupervisor.instance

        /**
         * 获取聚合管理器
         *
         * @return
         */
        fun aggregates(): AggregateSupervisor = AggregateSupervisor.instance

        /**
         * 获取领域服务管理器
         *
         * @return
         */
        fun services(): DomainServiceSupervisor = DomainServiceSupervisor.instance

        /**
         * 获取单元工作单元
         *
         * @return
         */
        fun unitOfWork(): UnitOfWork = UnitOfWork.instance

        /**
         * 获取集成事件管理器
         *
         * @return
         */
        fun events(): IntegrationEventSupervisor = IntegrationEventSupervisor.instance


        /**
         * 获取请求管理器
         * 兼容 cmd() qry()，当前三者实现一致。
         *
         * @return
         */
        fun requests(): RequestSupervisor = RequestSupervisor.instance

        /**
         * 获取命令管理器
         *
         * @return
         */
        fun commands(): RequestSupervisor = RequestSupervisor.instance

        /**
         * 获取Saga管理器
         *
         * @return
         */
        fun sagas(): SagaSupervisor = SagaSupervisor.instance

        /**
         * 获取查询管理器
         *
         * @return
         */
        fun queries(): RequestSupervisor = RequestSupervisor.instance

        /**
         * 获取聚合工厂管理器(shortcut for factories)
         *
         * @return
         */
        fun fac(): AggregateFactorySupervisor = AggregateFactorySupervisor.instance

        /**
         * 获取聚合仓储管理器(shortcut for repositories)
         *
         * @return
         */
        fun repo(): RepositorySupervisor = RepositorySupervisor.instance

        /**
         * 获取聚合管理器(shortcut for aggregates)
         *
         * @return
         */
        fun agg(): AggregateSupervisor = AggregateSupervisor.instance

        /**
         * 获取领域服务管理器(shortcut for services)
         *
         * @return
         */
        fun svc(): DomainServiceSupervisor = DomainServiceSupervisor.instance

        /**
         * 获取单元工作单元
         *
         * @return
         */
        fun uow(): UnitOfWork = UnitOfWork.instance

        /**
         * 获取请求管理器(shortcut for requests)
         *
         * @return
         */
        fun req(): RequestSupervisor = RequestSupervisor.instance

        /**
         * 获取命令管理器(shortcut for commands)
         *
         * @return
         */
        fun cmd(): RequestSupervisor = RequestSupervisor.instance

        /**
         * 获取查询管理器(shortcut for queries)
         *
         * @return
         */
        fun qry(): RequestSupervisor = RequestSupervisor.instance
    }
}

/**
 * 默认中介者
 *
 * @author binking338
 * @date 2024/8/24
 */
class DefaultMediator : Mediator {
    override fun <ENTITY : Any, ENTITY_PAYLOAD : AggregatePayload<ENTITY>> create(entityPayload: ENTITY_PAYLOAD): ENTITY =
        AggregateFactorySupervisor.instance.create(entityPayload)

    override fun <ENTITY : Any> find(
        predicate: Predicate<ENTITY>,
        orders: Collection<OrderInfo>,
        persist: Boolean
    ): List<ENTITY> = RepositorySupervisor.instance.find(predicate, orders, persist)

    override fun <ENTITY : Any> find(
        predicate: Predicate<ENTITY>,
        pageParam: PageParam,
        persist: Boolean
    ): List<ENTITY> =
        RepositorySupervisor.instance.find(predicate, pageParam, persist)

    override fun <ENTITY : Any> findOne(predicate: Predicate<ENTITY>, persist: Boolean): Optional<ENTITY> =
        RepositorySupervisor.instance.findOne(predicate, persist)

    override fun <ENTITY : Any> findFirst(
        predicate: Predicate<ENTITY>,
        orders: Collection<OrderInfo>,
        persist: Boolean
    ): Optional<ENTITY> = RepositorySupervisor.instance.findFirst(predicate, orders, persist)

    override fun <ENTITY : Any> findPage(
        predicate: Predicate<ENTITY>,
        pageParam: PageParam,
        persist: Boolean
    ): PageData<ENTITY> = RepositorySupervisor.instance.findPage(predicate, pageParam, persist)

    override fun <ENTITY : Any> remove(predicate: Predicate<ENTITY>, limit: Int): List<ENTITY> =
        RepositorySupervisor.instance.remove(predicate, limit)

    override fun <ENTITY : Any> count(predicate: Predicate<ENTITY>): Long =
        RepositorySupervisor.instance.count(predicate)

    override fun <ENTITY : Any> exists(predicate: Predicate<ENTITY>): Boolean =
        RepositorySupervisor.instance.exists(predicate)

    override fun <ENTITY : Any, AGGREGATE : Aggregate<ENTITY>, ENTITY_PAYLOAD : AggregatePayload<ENTITY>> create(
        clazz: Class<AGGREGATE>,
        payload: ENTITY_PAYLOAD
    ): AGGREGATE = AggregateSupervisor.instance.create(clazz, payload)

    override fun <ENTITY : Any, AGGREGATE : Aggregate<ENTITY>> getByIds(
        ids: Iterable<Id<AGGREGATE, *>>,
        persist: Boolean
    ): List<AGGREGATE> = AggregateSupervisor.instance.getByIds(ids, persist)

    override fun <ENTITY : Any, AGGREGATE : Aggregate<ENTITY>> find(
        predicate: AggregatePredicate<ENTITY, AGGREGATE>,
        orders: Collection<OrderInfo>,
        persist: Boolean
    ): List<AGGREGATE> = AggregateSupervisor.instance.find(predicate, orders, persist)

    override fun <ENTITY : Any, AGGREGATE : Aggregate<ENTITY>> find(
        predicate: AggregatePredicate<ENTITY, AGGREGATE>,
        pageParam: PageParam,
        persist: Boolean
    ): List<AGGREGATE> = AggregateSupervisor.instance.find(predicate, pageParam, persist)

    override fun <ENTITY : Any, AGGREGATE : Aggregate<ENTITY>> findOne(
        predicate: AggregatePredicate<ENTITY, AGGREGATE>,
        persist: Boolean
    ): Optional<AGGREGATE> = AggregateSupervisor.instance.findOne(predicate, persist)

    override fun <ENTITY : Any, AGGREGATE : Aggregate<ENTITY>> findFirst(
        predicate: AggregatePredicate<ENTITY, AGGREGATE>,
        orders: Collection<OrderInfo>,
        persist: Boolean
    ): Optional<AGGREGATE> = AggregateSupervisor.instance.findFirst(predicate, orders, persist)

    override fun <ENTITY : Any, AGGREGATE : Aggregate<ENTITY>> findPage(
        predicate: AggregatePredicate<ENTITY, AGGREGATE>,
        pageParam: PageParam,
        persist: Boolean
    ): PageData<AGGREGATE> = AggregateSupervisor.instance.findPage(predicate, pageParam, persist)

    override fun <ENTITY : Any, AGGREGATE : Aggregate<ENTITY>> removeByIds(ids: Iterable<Id<AGGREGATE, *>>): List<AGGREGATE> =
        AggregateSupervisor.instance.removeByIds(ids)

    override fun <ENTITY : Any, AGGREGATE : Aggregate<ENTITY>> remove(
        predicate: AggregatePredicate<ENTITY, AGGREGATE>,
        limit: Int
    ): List<AGGREGATE> = AggregateSupervisor.instance.remove(predicate, limit)

    override fun <ENTITY : Any, AGGREGATE : Aggregate<ENTITY>> count(predicate: AggregatePredicate<ENTITY, AGGREGATE>): Long =
        AggregateSupervisor.instance.count(predicate)

    override fun <ENTITY : Any, AGGREGATE : Aggregate<ENTITY>> exists(predicate: AggregatePredicate<ENTITY, AGGREGATE>): Boolean =
        AggregateSupervisor.instance.exists(predicate)

    override fun <DOMAIN_SERVICE : Any> getService(domainServiceClass: Class<DOMAIN_SERVICE>): DOMAIN_SERVICE =
        DomainServiceSupervisor.instance.getService(domainServiceClass)

    override fun persist(entity: Any) = UnitOfWork.instance.persist(entity)

    override fun persistIfNotExist(entity: Any): Boolean = UnitOfWork.instance.persistIfNotExist(entity)

    override fun remove(entity: Any) = UnitOfWork.instance.remove(entity)

    override fun save(propagation: Propagation) = UnitOfWork.instance.save(propagation)

    override fun <EVENT : Any> attach(eventPayload: EVENT, schedule: LocalDateTime, delay: Duration) =
        IntegrationEventSupervisor.instance.attach(eventPayload, schedule, delay)

    override fun <EVENT : Any> detach(eventPayload: EVENT) = IntegrationEventSupervisor.instance.detach(eventPayload)

    override fun <EVENT : Any> publish(eventPayload: EVENT, schedule: LocalDateTime, delay: Duration) =
        IntegrationEventSupervisor.instance.publish(eventPayload, schedule, delay)

    override fun <RESPONSE : Any, REQUEST : RequestParam<RESPONSE>> send(request: REQUEST): RESPONSE =
        RequestSupervisor.instance.send(request)

    override fun <RESPONSE : Any, REQUEST : RequestParam<RESPONSE>> schedule(
        request: REQUEST,
        schedule: LocalDateTime
    ): String = RequestSupervisor.instance.schedule(request, schedule)

    override fun <RESPONSE : Any, REQUEST : RequestParam<RESPONSE>> result(
        requestId: String,
        requestClass: Class<REQUEST>
    ): Optional<RESPONSE> = RequestSupervisor.instance.result(requestId, requestClass)

}

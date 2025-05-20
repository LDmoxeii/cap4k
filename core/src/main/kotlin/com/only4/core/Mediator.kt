package com.only4.core

import org.springframework.context.ApplicationContext

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

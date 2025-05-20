package com.only4.core

import org.springframework.context.ApplicationContext

/**
 * 中介者配置
 *
 * @author binking338
 * @date 2024/8/24
 */
object MediatorSupport {
    lateinit var instance: Mediator

    lateinit var ioc: ApplicationContext

    fun configure(mediator: Mediator) {
        instance = mediator
    }

    fun configure(applicationContext: ApplicationContext) {
        ioc = applicationContext
    }
}

/**
 * 聚合工厂管理器配置
 *
 * @author binking338
 * @date 2024/9/3
 */
object AggregateFactorySupervisorSupport {
    lateinit var instance: AggregateFactorySupervisor

    fun configure(aggregateFactorySupervisor: AggregateFactorySupervisor) {
        instance = aggregateFactorySupervisor
    }
}

/**
 * 仓储管理器配置
 *
 * @author binking338
 * @date 2024/8/25
 */
object RepositorySupervisorSupport {
    lateinit var instance: RepositorySupervisor

    fun configure(repositorySupervisor: RepositorySupervisor) {
        instance = repositorySupervisor
    }
}

/**
 * 领域事件管理器配置
 *
 * @author binking338
 * @date 2024/8/24
 */
object DomainEventSupervisorSupport {
    lateinit var instance: DomainEventSupervisor
    lateinit var manager: DomainEventManager

    /**
     * 配置领域事件管理器
     * @param domainEventSupervisor [DomainEventSupervisor]
     */
    fun configure(domainEventSupervisor: DomainEventSupervisor) {
        instance = domainEventSupervisor
    }

    /**
     * 配置领域事件发布管理器
     * @param domainEventManager [DomainEventManager]
     */
    fun configure(domainEventManager: DomainEventManager) {
        manager = domainEventManager
    }

    /**
     * for entity import static
     *
     * @return
     */
    fun events(): DomainEventSupervisor {
        return instance
    }
}

/**
 * 聚合管理器帮助类
 *
 * @author binking338
 * @date 2025/1/12
 */
object AggregateSupervisorSupport {
    lateinit var instance: AggregateSupervisor
    fun configure(aggregateSupervisor: AggregateSupervisor) {
        instance = aggregateSupervisor
    }
}

/**
 * 领域服务管理
 *
 * @author binking338
 * @date 2024/9/4
 */
object DomainServiceSupervisorSupport {
    lateinit var instance: DomainServiceSupervisor

    fun configure(domainServiceSupervisor: DomainServiceSupervisor) {
        instance = domainServiceSupervisor
    }
}

/**
 * 工作单元配置
 *
 * @author binking338
 * @date 2024/8/25
 */
object UnitOfWorkSupport {
    lateinit var instance: UnitOfWork

    /**
     * 配置工作单元
     *
     * @param unitOfWork 工作单元
     */
    fun configure(unitOfWork: UnitOfWork) {
        instance = unitOfWork
    }
}

/**
 * 事件管理器配置
 *
 * @author binking338
 * @date 2024/8/26
 */
object IntegrationEventSupervisorSupport {
    lateinit var instance: IntegrationEventSupervisor

    lateinit var manager: IntegrationEventManager


    /**
     * 配置事件管理器
     *
     * @param integrationEventSupervisor [IntegrationEventSupervisor]
     */
    fun configure(integrationEventSupervisor: IntegrationEventSupervisor) {
        instance = integrationEventSupervisor
    }

    /**
     * 配置事件管理器
     *
     * @param integrationEventManager [IntegrationEventManager]
     */
    fun configure(integrationEventManager: IntegrationEventManager) {
        manager = integrationEventManager
    }
}

/**
 * 请求管理器配置
 *
 * @author binking338
 * @date 2024/8/24
 */
object RequestSupervisorSupport {
    lateinit var instance: RequestSupervisor
    lateinit var requestManager: RequestManager

    /**
     * 配置请求管理器
     *
     * @param requestSupervisor [RequestSupervisor]
     */
    fun configure(requestSupervisor: RequestSupervisor) {
        instance = requestSupervisor
    }

    /**
     * 配置请求管理器
     *
     * @param requestManager [RequestManager]
     */
    fun configure(requestManager: RequestManager) {
        RequestSupervisorSupport.requestManager = requestManager
    }
}

/**
 * Saga 管理器配置
 *
 * @author binking338
 * @date 2024/10/12
 */
object SagaSupervisorSupport {
    lateinit var instance: SagaSupervisor
    lateinit var sagaProcessSupervisor: SagaProcessSupervisor
    lateinit var sagaManager: SagaManager

    /**
     * 配置 Saga 管理器
     *
     * @param sagaSupervisor
     */
    fun configure(sagaSupervisor: SagaSupervisor) {
        instance = sagaSupervisor
    }

    /**
     * 配置 Saga 子执行器
     *
     * @param sagaProcessSupervisor
     */
    fun configure(sagaProcessSupervisor: SagaProcessSupervisor) {
        SagaSupervisorSupport.sagaProcessSupervisor = sagaProcessSupervisor
    }

    /**
     * 配置 Saga 管理器
     *
     * @param sagaManager
     */
    fun configure(sagaManager: SagaManager) {
        SagaSupervisorSupport.sagaManager = sagaManager
    }
}

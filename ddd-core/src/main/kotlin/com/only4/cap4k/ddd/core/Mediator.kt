package com.only4.cap4k.ddd.core

import com.only4.cap4k.ddd.core.application.RequestSupervisor
import com.only4.cap4k.ddd.core.application.UnitOfWork
import com.only4.cap4k.ddd.core.application.event.IntegrationEventSupervisor
import com.only4.cap4k.ddd.core.domain.aggregate.AggregateFactorySupervisor
import com.only4.cap4k.ddd.core.domain.aggregate.AggregateSupervisor
import com.only4.cap4k.ddd.core.domain.repo.RepositorySupervisor
import com.only4.cap4k.ddd.core.domain.service.DomainServiceSupervisor
import org.springframework.context.ApplicationContext

/**
 * 中介者
 *
 * @author LD_moxeii
 * @date 2025/07/22
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

    companion object {
        val instance: Mediator
            get() = MediatorSupport.instance

        val ioc: ApplicationContext = MediatorSupport.ioc
        val factories: AggregateFactorySupervisor = AggregateFactorySupervisor.instance
        val repositories: RepositorySupervisor = RepositorySupervisor.instance
        val aggregates: AggregateSupervisor = AggregateSupervisor.instance
        val services: DomainServiceSupervisor = DomainServiceSupervisor.instance
        val events: IntegrationEventSupervisor = IntegrationEventSupervisor.instance
        val requests: RequestSupervisor = RequestSupervisor.instance
        val commands: RequestSupervisor = requests
        val queries: RequestSupervisor = requests

        // Shortcuts
        val fac: AggregateFactorySupervisor = factories
        val repo: RepositorySupervisor = repositories
        val agg: AggregateSupervisor = aggregates
        val svc: DomainServiceSupervisor = services
        val uow: UnitOfWork = UnitOfWork.instance
        val req: RequestSupervisor = requests
        val cmd: RequestSupervisor = requests
        val qry: RequestSupervisor = requests
    }
}

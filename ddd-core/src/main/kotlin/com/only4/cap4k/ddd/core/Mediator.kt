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
        @JvmStatic
        val instance: Mediator
            get() = MediatorSupport.instance

        @JvmStatic
        val ioc: ApplicationContext = MediatorSupport.ioc

        @JvmStatic
        val factories: AggregateFactorySupervisor = AggregateFactorySupervisor.instance

        @JvmStatic
        val repositories: RepositorySupervisor = RepositorySupervisor.instance

        @JvmStatic
        val aggregates: AggregateSupervisor = AggregateSupervisor.instance

        @JvmStatic
        val services: DomainServiceSupervisor = DomainServiceSupervisor.instance

        @JvmStatic
        val events: IntegrationEventSupervisor = IntegrationEventSupervisor.instance

        @JvmStatic
        val requests: RequestSupervisor = RequestSupervisor.instance

        @JvmStatic
        val commands: RequestSupervisor = requests

        @JvmStatic
        val queries: RequestSupervisor = requests

        // Shortcuts
        @JvmStatic
        val fac: AggregateFactorySupervisor = factories

        @JvmStatic
        val repo: RepositorySupervisor = repositories

        @JvmStatic
        val agg: AggregateSupervisor = aggregates

        @JvmStatic
        val svc: DomainServiceSupervisor = services

        @JvmStatic
        val uow: UnitOfWork = UnitOfWork.instance

        @JvmStatic
        val req: RequestSupervisor = requests

        @JvmStatic
        val cmd: RequestSupervisor = requests

        @JvmStatic
        val qry: RequestSupervisor = requests
    }
}

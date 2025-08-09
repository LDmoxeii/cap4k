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
        val instance: Mediator by lazy { MediatorSupport.instance }

        @JvmStatic
        val ioc: ApplicationContext by lazy { MediatorSupport.ioc }

        @JvmStatic
        val factories: AggregateFactorySupervisor by lazy { AggregateFactorySupervisor.instance }

        @JvmStatic
        val repositories: RepositorySupervisor by lazy { RepositorySupervisor.instance }

        @JvmStatic
        val aggregates: AggregateSupervisor by lazy { AggregateSupervisor.instance }

        @JvmStatic
        val services: DomainServiceSupervisor by lazy { DomainServiceSupervisor.instance }

        @JvmStatic
        val events: IntegrationEventSupervisor by lazy { IntegrationEventSupervisor.instance }

        @JvmStatic
        val requests: RequestSupervisor by lazy { RequestSupervisor.instance }

        @JvmStatic
        val commands: RequestSupervisor by lazy { requests }

        @JvmStatic
        val queries: RequestSupervisor by lazy { requests }

        // Shortcuts
        @JvmStatic
        val fac: AggregateFactorySupervisor by lazy { factories }

        @JvmStatic
        val repo: RepositorySupervisor by lazy { repositories }

        @JvmStatic
        val agg: AggregateSupervisor by lazy { aggregates }

        @JvmStatic
        val svc: DomainServiceSupervisor by lazy { services }

        @JvmStatic
        val uow: UnitOfWork by lazy { UnitOfWork.instance }

        @JvmStatic
        val req: RequestSupervisor by lazy { requests }

        @JvmStatic
        val cmd: RequestSupervisor by lazy { requests }

        @JvmStatic
        val qry: RequestSupervisor by lazy { requests }
    }
}

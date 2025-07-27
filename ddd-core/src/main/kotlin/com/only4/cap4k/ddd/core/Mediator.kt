package com.only4.cap4k.ddd.core

import com.only4.cap4k.ddd.core.application.RequestSupervisor
import com.only4.cap4k.ddd.core.application.UnitOfWork
import com.only4.cap4k.ddd.core.application.event.IntegrationEventSupervisor
import com.only4.cap4k.ddd.core.domain.aggregate.AggregateFactorySupervisor
import com.only4.cap4k.ddd.core.domain.repo.AggregateSupervisor
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

        fun ioc(): ApplicationContext = MediatorSupport.ioc
        fun factories(): AggregateFactorySupervisor = AggregateFactorySupervisor.instance
        fun repositories(): RepositorySupervisor = RepositorySupervisor.instance
        fun aggregates(): AggregateSupervisor = AggregateSupervisor.instance
        fun services(): DomainServiceSupervisor = DomainServiceSupervisor.instance
        fun events(): IntegrationEventSupervisor = IntegrationEventSupervisor.instance
        fun requests(): RequestSupervisor = RequestSupervisor.instance
        fun commands(): RequestSupervisor = requests()
        fun queries(): RequestSupervisor = requests()

        // Shortcuts
        fun fac(): AggregateFactorySupervisor = factories()
        fun repo(): RepositorySupervisor = repositories()
        fun agg(): AggregateSupervisor = aggregates()
        fun svc(): DomainServiceSupervisor = services()
        fun uow(): UnitOfWork = UnitOfWork.instance
        fun req(): RequestSupervisor = requests()
        fun cmd(): RequestSupervisor = requests()
        fun qry(): RequestSupervisor = requests()
    }
}

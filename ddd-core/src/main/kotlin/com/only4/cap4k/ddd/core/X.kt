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
 * Mediator的短别名引用
 *
 * @author LD_moxeii
 * @date 2025/07/27
 */
interface X {
    companion object {
        fun ioc(): ApplicationContext = Mediator.ioc()
        fun factories(): AggregateFactorySupervisor = Mediator.factories()
        fun repositories(): RepositorySupervisor = Mediator.repositories()
        fun aggregates(): AggregateSupervisor = Mediator.aggregates()
        fun services(): DomainServiceSupervisor = Mediator.services()
        fun unitOfWork(): UnitOfWork = Mediator.uow()
        fun events(): IntegrationEventSupervisor = Mediator.events()
        fun requests(): RequestSupervisor = Mediator.requests()
        fun commands(): RequestSupervisor = Mediator.commands()
        fun queries(): RequestSupervisor = Mediator.queries()
        fun fac(): AggregateFactorySupervisor = Mediator.fac()
        fun repo(): RepositorySupervisor = Mediator.repo()
        fun agg(): AggregateSupervisor = Mediator.agg()
        fun svc(): DomainServiceSupervisor = Mediator.svc()
        fun uow(): UnitOfWork = Mediator.uow()
        fun req(): RequestSupervisor = Mediator.req()
        fun cmd(): RequestSupervisor = Mediator.cmd()
        fun qry(): RequestSupervisor = Mediator.qry()
    }
}

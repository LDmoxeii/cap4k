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
        val ioc: ApplicationContext = Mediator.ioc
        val factories: AggregateFactorySupervisor = Mediator.factories
        val repositories: RepositorySupervisor = Mediator.repositories
        val aggregates: AggregateSupervisor = Mediator.aggregates
        val services: DomainServiceSupervisor = Mediator.services
        val unitOfWork: UnitOfWork = Mediator.uow
        val events: IntegrationEventSupervisor = Mediator.events
        val requests: RequestSupervisor = Mediator.requests
        val commands: RequestSupervisor = Mediator.commands
        val queries: RequestSupervisor = Mediator.queries
        val fac: AggregateFactorySupervisor = Mediator.fac
        val repo: RepositorySupervisor = Mediator.repo
        val agg: AggregateSupervisor = Mediator.agg
        val svc: DomainServiceSupervisor = Mediator.svc
        val uow: UnitOfWork = Mediator.uow
        val req: RequestSupervisor = Mediator.req
        val cmd: RequestSupervisor = Mediator.cmd
        val qry: RequestSupervisor = Mediator.qry
    }
}

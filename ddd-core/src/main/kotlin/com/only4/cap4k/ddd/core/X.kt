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
        val ioc: ApplicationContext by lazy { Mediator.ioc }
        val factories: AggregateFactorySupervisor by lazy { Mediator.factories }
        val repositories: RepositorySupervisor by lazy { Mediator.repositories }
        val aggregates: AggregateSupervisor by lazy { Mediator.aggregates }
        val services: DomainServiceSupervisor by lazy { Mediator.services }
        val unitOfWork: UnitOfWork by lazy { Mediator.uow }
        val events: IntegrationEventSupervisor by lazy { Mediator.events }
        val requests: RequestSupervisor by lazy { Mediator.requests }
        val commands: RequestSupervisor by lazy { Mediator.commands }
        val queries: RequestSupervisor by lazy { Mediator.queries }
        val fac: AggregateFactorySupervisor by lazy { Mediator.fac }
        val repo: RepositorySupervisor by lazy { Mediator.repo }
        val agg: AggregateSupervisor by lazy { Mediator.agg }
        val svc: DomainServiceSupervisor by lazy { Mediator.svc }
        val uow: UnitOfWork by lazy { Mediator.uow }
        val req: RequestSupervisor by lazy { Mediator.req }
        val cmd: RequestSupervisor by lazy { Mediator.cmd }
        val qry: RequestSupervisor by lazy { Mediator.qry }
    }
}

package com.only4.cap4k.ddd.core

import com.only4.cap4k.ddd.core.application.RequestSupervisor
import com.only4.cap4k.ddd.core.application.UnitOfWork
import com.only4.cap4k.ddd.core.application.event.IntegrationEventSupervisor
import com.only4.cap4k.ddd.core.domain.aggregate.AggregateFactorySupervisor
import com.only4.cap4k.ddd.core.domain.id.IdentifierGenerator
import com.only4.cap4k.ddd.core.domain.repo.RepositorySupervisor
import com.only4.cap4k.ddd.core.domain.service.DomainServiceSupervisor
import org.springframework.context.ApplicationContext

/**
 * 中介者
 *
 * @author LD_moxeii
 * @date 2025/07/22
 */
class Mediator private constructor() {
    companion object {
        @JvmStatic
        val ioc: ApplicationContext
            get() = MediatorSupport.ioc

        @JvmStatic
        @get:JvmName("getIdentifierGenerator")
        val identifiers: IdentifierGenerator
            get() = MediatorSupport.identifiers

        @JvmStatic
        val factories: AggregateFactorySupervisor
            get() = AggregateFactorySupervisor.instance

        @JvmStatic
        val repositories: RepositorySupervisor
            get() = RepositorySupervisor.instance

        @JvmStatic
        val services: DomainServiceSupervisor
            get() = DomainServiceSupervisor.instance

        @JvmStatic
        val events: IntegrationEventSupervisor
            get() = IntegrationEventSupervisor.instance

        @JvmStatic
        val requests: RequestSupervisor
            get() = RequestSupervisor.instance

        @JvmStatic
        val commands: RequestSupervisor
            get() = requests

        @JvmStatic
        val queries: RequestSupervisor
            get() = requests

        @JvmStatic
        val uow: UnitOfWork
            get() = UnitOfWork.instance
    }
}

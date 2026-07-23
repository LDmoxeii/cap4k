package com.only4.cap4k.ddd.core.impl

import com.only4.cap4k.ddd.core.Mediator
import com.only4.cap4k.ddd.core.MediatorSupport
import com.only4.cap4k.ddd.core.application.PersistIntent
import com.only4.cap4k.ddd.core.application.RequestParam
import com.only4.cap4k.ddd.core.application.RequestSupervisor
import com.only4.cap4k.ddd.core.application.UnitOfWork
import com.only4.cap4k.ddd.core.application.event.IntegrationEventSupervisor
import com.only4.cap4k.ddd.core.domain.aggregate.AggregateFactorySupervisor
import com.only4.cap4k.ddd.core.domain.aggregate.AggregatePayload
import com.only4.cap4k.ddd.core.domain.id.IdentifierGenerator
import com.only4.cap4k.ddd.core.domain.repo.AggregateLoadPlan
import com.only4.cap4k.ddd.core.domain.repo.Predicate
import com.only4.cap4k.ddd.core.domain.repo.RepositorySupervisor
import com.only4.cap4k.ddd.core.domain.service.DomainServiceSupervisor
import com.only4.cap4k.ddd.core.share.OrderInfo
import com.only4.cap4k.ddd.core.share.PageData
import com.only4.cap4k.ddd.core.share.PageParam
import org.springframework.transaction.annotation.Propagation
import java.time.LocalDateTime

/**
 * 默认中介者
 *
 * @author LD_moxeii
 * @date 2025/07/23
 */
class DefaultMediator(
    override val identifiers: IdentifierGenerator = MediatorSupport.identifiers
) : Mediator {

    // AggregateFactorySupervisor methods
    override fun <ENTITY_PAYLOAD : AggregatePayload<ENTITY>, ENTITY : Any> create(entityPayload: ENTITY_PAYLOAD): ENTITY =
        AggregateFactorySupervisor.instance.create(entityPayload)

    // RepositorySupervisor methods
    override fun <ENTITY: Any> find(
        predicate: Predicate<ENTITY>,
        orders: Collection<OrderInfo>,
        persist: Boolean,
    ): List<ENTITY> =
        RepositorySupervisor.instance.find(predicate, orders, persist)

    override fun <ENTITY: Any> find(
        predicate: Predicate<ENTITY>,
        orders: Collection<OrderInfo>,
        persist: Boolean,
        loadPlan: AggregateLoadPlan,
    ): List<ENTITY> =
        RepositorySupervisor.instance.find(predicate, orders, persist, loadPlan)

    override fun <ENTITY: Any> find(
        predicate: Predicate<ENTITY>,
        pageParam: PageParam,
        persist: Boolean,
    ): List<ENTITY> =
        RepositorySupervisor.instance.find(predicate, pageParam, persist)

    override fun <ENTITY: Any> find(
        predicate: Predicate<ENTITY>,
        pageParam: PageParam,
        persist: Boolean,
        loadPlan: AggregateLoadPlan,
    ): List<ENTITY> =
        RepositorySupervisor.instance.find(predicate, pageParam, persist, loadPlan)

    override fun <ENTITY: Any> findOne(
        predicate: Predicate<ENTITY>,
        persist: Boolean,
    ): ENTITY? =
        RepositorySupervisor.instance.findOne(predicate, persist)

    override fun <ENTITY: Any> findOne(
        predicate: Predicate<ENTITY>,
        persist: Boolean,
        loadPlan: AggregateLoadPlan,
    ): ENTITY? =
        RepositorySupervisor.instance.findOne(predicate, persist, loadPlan)

    override fun <ENTITY: Any> findFirst(
        predicate: Predicate<ENTITY>,
        orders: Collection<OrderInfo>,
        persist: Boolean,
    ): ENTITY? =
        RepositorySupervisor.instance.findFirst(predicate, orders, persist)

    override fun <ENTITY: Any> findFirst(
        predicate: Predicate<ENTITY>,
        orders: Collection<OrderInfo>,
        persist: Boolean,
        loadPlan: AggregateLoadPlan,
    ): ENTITY? =
        RepositorySupervisor.instance.findFirst(predicate, orders, persist, loadPlan)

    override fun <ENTITY: Any> findPage(
        predicate: Predicate<ENTITY>,
        pageParam: PageParam,
        persist: Boolean,
    ): PageData<ENTITY> =
        RepositorySupervisor.instance.findPage(predicate, pageParam, persist)

    override fun <ENTITY: Any> findPage(
        predicate: Predicate<ENTITY>,
        pageParam: PageParam,
        persist: Boolean,
        loadPlan: AggregateLoadPlan,
    ): PageData<ENTITY> =
        RepositorySupervisor.instance.findPage(predicate, pageParam, persist, loadPlan)

    override fun <ENTITY: Any> remove(predicate: Predicate<ENTITY>): List<ENTITY> =
        RepositorySupervisor.instance.remove(predicate)

    override fun <ENTITY: Any> remove(predicate: Predicate<ENTITY>, limit: Int): List<ENTITY> =
        RepositorySupervisor.instance.remove(predicate, limit)

    override fun <ENTITY: Any> count(predicate: Predicate<ENTITY>): Long =
        RepositorySupervisor.instance.count(predicate)

    override fun <ENTITY: Any> exists(predicate: Predicate<ENTITY>): Boolean =
        RepositorySupervisor.instance.exists(predicate)

    // DomainServiceSupervisor methods
    override fun <DOMAIN_SERVICE : Any> getService(domainServiceClass: Class<DOMAIN_SERVICE>): DOMAIN_SERVICE =
        DomainServiceSupervisor.instance.getService(domainServiceClass)

    // UnitOfWork methods
    override fun persist(entity: Any, intent: PersistIntent) {
        UnitOfWork.instance.persist(entity, intent)
    }

    override fun remove(entity: Any) {
        UnitOfWork.instance.remove(entity)
    }

    override fun save(propagation: Propagation) {
        UnitOfWork.instance.save(propagation)
    }

    // RequestSupervisor methods
    override fun <REQUEST : RequestParam<RESPONSE>, RESPONSE : Any> send(request: REQUEST): RESPONSE =
        RequestSupervisor.instance.send(request)

    override fun <REQUEST : RequestParam<RESPONSE>, RESPONSE : Any> schedule(
        request: REQUEST,
        schedule: LocalDateTime
    ): String =
        RequestSupervisor.instance.schedule(request, schedule)

    override fun <R : Any> result(requestId: String): R? =
        RequestSupervisor.instance.result(requestId)

    // IntegrationEventSupervisor methods
    override fun <EVENT : Any> attach(eventPayload: EVENT, schedule: LocalDateTime) {
        IntegrationEventSupervisor.instance.attach(eventPayload, schedule)
    }

    override fun <EVENT : Any> attach(schedule: LocalDateTime, eventPayloadSupplier: () -> EVENT) {
        IntegrationEventSupervisor.instance.attach(schedule, eventPayloadSupplier)
    }

    override fun <EVENT : Any> detach(eventPayload: EVENT) {
        IntegrationEventSupervisor.instance.detach(eventPayload)
    }
}

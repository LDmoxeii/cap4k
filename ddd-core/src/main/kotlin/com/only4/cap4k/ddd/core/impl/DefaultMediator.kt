package com.only4.cap4k.ddd.core.impl

import com.only4.cap4k.ddd.core.Mediator
import com.only4.cap4k.ddd.core.application.RequestParam
import com.only4.cap4k.ddd.core.application.RequestSupervisor
import com.only4.cap4k.ddd.core.application.UnitOfWork
import com.only4.cap4k.ddd.core.application.event.IntegrationEventSupervisor
import com.only4.cap4k.ddd.core.domain.aggregate.Aggregate
import com.only4.cap4k.ddd.core.domain.aggregate.AggregateFactorySupervisor
import com.only4.cap4k.ddd.core.domain.aggregate.AggregatePayload
import com.only4.cap4k.ddd.core.domain.aggregate.Id
import com.only4.cap4k.ddd.core.domain.repo.AggregatePredicate
import com.only4.cap4k.ddd.core.domain.repo.AggregateSupervisor
import com.only4.cap4k.ddd.core.domain.repo.Predicate
import com.only4.cap4k.ddd.core.domain.repo.RepositorySupervisor
import com.only4.cap4k.ddd.core.domain.service.DomainServiceSupervisor
import com.only4.cap4k.ddd.core.share.OrderInfo
import com.only4.cap4k.ddd.core.share.PageData
import com.only4.cap4k.ddd.core.share.PageParam
import org.springframework.transaction.annotation.Propagation
import java.time.LocalDateTime
import java.util.*

/**
 * 默认中介者
 *
 * @author LD_moxeii
 * @date 2025/07/23
 */
class DefaultMediator : Mediator {

    override fun <ENTITY_PAYLOAD : AggregatePayload<ENTITY>, ENTITY : Any> create(entityPayload: ENTITY_PAYLOAD): ENTITY =
        AggregateFactorySupervisor.instance.create(entityPayload)

    override fun <ENTITY: Any> find(
        predicate: Predicate<ENTITY>,
        orders: Collection<OrderInfo>?,
        persist: Boolean
    ): List<ENTITY> =
        RepositorySupervisor.instance.find(predicate, orders, persist)

    override fun <ENTITY: Any> find(predicate: Predicate<ENTITY>, pageParam: PageParam, persist: Boolean): List<ENTITY> =
        RepositorySupervisor.instance.find(predicate, pageParam, persist)

    override fun <ENTITY: Any> findOne(predicate: Predicate<ENTITY>, persist: Boolean): Optional<ENTITY> =
        RepositorySupervisor.instance.findOne(predicate, persist)

    override fun <ENTITY: Any> findFirst(
        predicate: Predicate<ENTITY>,
        orders: Collection<OrderInfo>,
        persist: Boolean
    ): Optional<ENTITY> =
        RepositorySupervisor.instance.findFirst(predicate, orders, persist)

    override fun <ENTITY: Any> findPage(
        predicate: Predicate<ENTITY>,
        pageParam: PageParam,
        persist: Boolean
    ): PageData<ENTITY> =
        RepositorySupervisor.instance.findPage(predicate, pageParam, persist)

    override fun <ENTITY: Any> remove(predicate: Predicate<ENTITY>): List<ENTITY> =
        RepositorySupervisor.instance.remove(predicate)

    override fun <ENTITY: Any> remove(predicate: Predicate<ENTITY>, limit: Int): List<ENTITY> =
        RepositorySupervisor.instance.remove(predicate, limit)

    override fun <ENTITY: Any> count(predicate: Predicate<ENTITY>): Long =
        RepositorySupervisor.instance.count(predicate)

    override fun <ENTITY: Any> exists(predicate: Predicate<ENTITY>): Boolean =
        RepositorySupervisor.instance.exists(predicate)

    override fun <DOMAIN_SERVICE> getService(domainServiceClass: Class<DOMAIN_SERVICE>): DOMAIN_SERVICE? =
        DomainServiceSupervisor.instance.getService(domainServiceClass)

    override fun persist(entity: Any) {
        UnitOfWork.instance.persist(entity)
    }

    override fun persistIfNotExist(entity: Any): Boolean =
        UnitOfWork.instance.persistIfNotExist(entity)

    override fun remove(entity: Any) {
        UnitOfWork.instance.remove(entity)
    }

    override fun save() {
        UnitOfWork.instance.save()
    }

    override fun save(propagation: Propagation) {
        UnitOfWork.instance.save(propagation)
    }

    override fun <REQUEST : RequestParam<RESPONSE>, RESPONSE> send(request: REQUEST): RESPONSE =
        RequestSupervisor.instance.send(request)

    override fun <REQUEST : RequestParam<RESPONSE>, RESPONSE> schedule(
        request: REQUEST,
        schedule: LocalDateTime
    ): String =
        RequestSupervisor.instance.schedule(request, schedule)

    override fun <R> result(requestId: String): R =
        RequestSupervisor.instance.result(requestId)

    override fun <EVENT : Any> attach(eventPayload: EVENT, schedule: LocalDateTime) {
        IntegrationEventSupervisor.instance.attach(eventPayload, schedule)
    }

    override fun <EVENT : Any> detach(eventPayload: EVENT) {
        IntegrationEventSupervisor.instance.detach(eventPayload)
    }

    override fun <EVENT : Any> publish(eventPayload: EVENT, schedule: LocalDateTime) {
        IntegrationEventSupervisor.instance.publish(eventPayload, schedule)
    }

    override fun <AGGREGATE : Aggregate<ENTITY>, ENTITY_PAYLOAD : AggregatePayload<ENTITY>, ENTITY: Any> create(
        clazz: Class<AGGREGATE>,
        payload: ENTITY_PAYLOAD
    ): AGGREGATE =
        AggregateSupervisor.instance.create(clazz, payload)

    override fun <AGGREGATE : Aggregate<ENTITY>, ENTITY: Any> getByIds(
        ids: Iterable<Id<AGGREGATE, *>>,
        persist: Boolean
    ): List<AGGREGATE> =
        AggregateSupervisor.instance.getByIds(ids, persist)

    override fun <AGGREGATE : Aggregate<ENTITY>, ENTITY : Any> find(
        predicate: AggregatePredicate<AGGREGATE, ENTITY>,
        orders: Collection<OrderInfo>?,
        persist: Boolean
    ): List<AGGREGATE> =
        AggregateSupervisor.instance.find(predicate, orders, persist)

    override fun <AGGREGATE : Aggregate<ENTITY>, ENTITY : Any> find(
        predicate: AggregatePredicate<AGGREGATE, ENTITY>,
        pageParam: PageParam,
        persist: Boolean
    ): List<AGGREGATE> =
        AggregateSupervisor.instance.find(predicate, pageParam, persist)

    override fun <AGGREGATE : Aggregate<ENTITY>, ENTITY : Any> findOne(
        predicate: AggregatePredicate<AGGREGATE, ENTITY>,
        persist: Boolean
    ): Optional<AGGREGATE> =
        AggregateSupervisor.instance.findOne(predicate, persist)

    override fun <AGGREGATE : Aggregate<ENTITY>, ENTITY : Any> findFirst(
        predicate: AggregatePredicate<AGGREGATE, ENTITY>,
        orders: Collection<OrderInfo>,
        persist: Boolean
    ): Optional<AGGREGATE> =
        AggregateSupervisor.instance.findFirst(predicate, orders, persist)

    override fun <AGGREGATE : Aggregate<ENTITY>, ENTITY : Any> findPage(
        predicate: AggregatePredicate<AGGREGATE, ENTITY>,
        pageParam: PageParam,
        persist: Boolean
    ): PageData<AGGREGATE> =
        AggregateSupervisor.instance.findPage(predicate, pageParam, persist)

    override fun <AGGREGATE : Aggregate<ENTITY>, ENTITY: Any> removeByIds(ids: Iterable<Id<AGGREGATE, *>>): List<AGGREGATE> =
        AggregateSupervisor.instance.removeByIds(ids)

    override fun <AGGREGATE : Aggregate<ENTITY>, ENTITY : Any> remove(predicate: AggregatePredicate<AGGREGATE, ENTITY>): List<AGGREGATE> =
        AggregateSupervisor.instance.remove(predicate)

    override fun <AGGREGATE : Aggregate<ENTITY>, ENTITY : Any> remove(
        predicate: AggregatePredicate<AGGREGATE, ENTITY>,
        limit: Int
    ): List<AGGREGATE> =
        AggregateSupervisor.instance.remove(predicate, limit)

    override fun <AGGREGATE : Aggregate<ENTITY>, ENTITY : Any> count(predicate: AggregatePredicate<AGGREGATE, ENTITY>): Long =
        AggregateSupervisor.instance.count(predicate)

    override fun <AGGREGATE : Aggregate<ENTITY>, ENTITY : Any> exists(predicate: AggregatePredicate<AGGREGATE, ENTITY>): Boolean =
        AggregateSupervisor.instance.exists(predicate)
}

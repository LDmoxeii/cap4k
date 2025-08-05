package com.only4.cap4k.ddd.domain.aggregate.impl

import com.only4.cap4k.ddd.core.application.UnitOfWork
import com.only4.cap4k.ddd.core.domain.aggregate.*
import com.only4.cap4k.ddd.core.domain.repo.RepositorySupervisor
import com.only4.cap4k.ddd.core.share.OrderInfo
import com.only4.cap4k.ddd.core.share.PageData
import com.only4.cap4k.ddd.core.share.PageParam
import com.only4.cap4k.ddd.core.share.misc.resolveGenericTypeClass
import com.only4.cap4k.ddd.domain.aggregate.JpaAggregatePredicate
import com.only4.cap4k.ddd.domain.aggregate.JpaAggregatePredicateSupport
import java.util.*

/**
 * 默认聚合管理器
 *
 * @author binking338
 * @date 2025/1/12
 */
class DefaultAggregateSupervisor(
    private val repositorySupervisor: RepositorySupervisor,
    private val unitOfWork: UnitOfWork

) : AggregateSupervisor {

    companion object {
        @Suppress("UNCHECKED_CAST")
        private fun <AGGREGATE : Aggregate<*>> newInstance(
            clazz: Class<AGGREGATE>,
            entity: Any
        ): AGGREGATE =
            try {
                val aggregate = clazz.getDeclaredConstructor().newInstance() as Aggregate<Any>
                aggregate._wrap(entity)
                aggregate as AGGREGATE
            } catch (ex: Exception) {
                throw RuntimeException(ex)
            }

        private fun <AGGREGATE : Aggregate<*>, PAYLOAD> newInstanceByPayload(
            clazz: Class<AGGREGATE>,
            payloadClass: Class<PAYLOAD>,
            payload: PAYLOAD
        ): AGGREGATE =
            try {
                clazz.getConstructor(payloadClass).newInstance(payload)
            } catch (ex: Exception) {
                throw RuntimeException(ex)
            }
    }

    override fun <AGGREGATE : Aggregate<ENTITY>, ENTITY_PAYLOAD : AggregatePayload<ENTITY>, ENTITY : Any> create(
        clazz: Class<AGGREGATE>,
        payload: ENTITY_PAYLOAD
    ): AGGREGATE {
        val aggregate = newInstanceByPayload(clazz, payload::class.java as Class<ENTITY_PAYLOAD>, payload)
        unitOfWork.persist(aggregate)
        return aggregate
    }

    override fun <AGGREGATE : Aggregate<ENTITY>, ENTITY : Any> getByIds(
        ids: Iterable<Id<AGGREGATE, *>>,
        persist: Boolean
    ): List<AGGREGATE> {
        if (!ids.iterator().hasNext()) {
            return emptyList()
        }

        @Suppress("UNCHECKED_CAST")
        val aggregateClass = resolveGenericTypeClass(
            ids.iterator().next(), 0, Id::class.java, Id.Default::class.java
        ) as Class<AGGREGATE>

        val aggregatePredicate = JpaAggregatePredicate.byIds(
            aggregateClass,
            ids.map { it.value }
        )
        return find(predicate = aggregatePredicate, persist = persist)
    }

    override fun <AGGREGATE : Aggregate<*>> find(
        predicate: AggregatePredicate<AGGREGATE, *>,
        orders: Collection<OrderInfo>,
        persist: Boolean
    ): List<AGGREGATE> {
        val clazz = JpaAggregatePredicateSupport.reflectAggregateClass(predicate)
        val pred = JpaAggregatePredicateSupport.getPredicate(predicate)
        val entities = repositorySupervisor.find(pred, orders, persist)
        return entities.map { entity -> newInstance(clazz, entity) }
    }

    override fun <AGGREGATE : Aggregate<*>> find(
        predicate: AggregatePredicate<AGGREGATE, *>,
        pageParam: PageParam,
        persist: Boolean
    ): List<AGGREGATE> {
        val clazz = JpaAggregatePredicateSupport.reflectAggregateClass(predicate)
        val pred = JpaAggregatePredicateSupport.getPredicate(predicate)
        val entities = repositorySupervisor.find(pred, pageParam, persist)
        return entities.map { entity -> newInstance(clazz, entity) }
    }

    override fun <AGGREGATE : Aggregate<*>> findOne(
        predicate: AggregatePredicate<AGGREGATE, *>,
        persist: Boolean
    ): Optional<AGGREGATE> {
        val clazz = JpaAggregatePredicateSupport.reflectAggregateClass(predicate)
        val pred = JpaAggregatePredicateSupport.getPredicate(predicate)
        val entity = repositorySupervisor.findOne(pred, persist)
        return if (entity.isPresent) {
            Optional.of(newInstance(clazz, entity.get()))
        } else {
            Optional.empty()
        }
    }

    override fun <AGGREGATE : Aggregate<*>> findFirst(
        predicate: AggregatePredicate<AGGREGATE, *>,
        orders: Collection<OrderInfo>,
        persist: Boolean
    ): Optional<AGGREGATE> {
        val clazz = JpaAggregatePredicateSupport.reflectAggregateClass(predicate)
        val pred = JpaAggregatePredicateSupport.getPredicate(predicate)
        val entity = repositorySupervisor.findFirst(pred, orders, persist)
        return if (entity.isPresent) {
            Optional.of(newInstance(clazz, entity.get()))
        } else {
            Optional.empty()
        }
    }

    override fun <AGGREGATE : Aggregate<*>> findPage(
        predicate: AggregatePredicate<AGGREGATE, *>,
        pageParam: PageParam,
        persist: Boolean
    ): PageData<AGGREGATE> {
        val clazz = JpaAggregatePredicateSupport.reflectAggregateClass(predicate)
        val pred = JpaAggregatePredicateSupport.getPredicate(predicate)
        val entities = repositorySupervisor.findPage(pred, pageParam, persist)
        return entities.transform { entity -> newInstance(clazz, entity) }
    }

    override fun <AGGREGATE : Aggregate<ENTITY>, ENTITY: Any> removeByIds(
        ids: Iterable<Id<AGGREGATE, *>>
    ): List<AGGREGATE> {
        if (!ids.iterator().hasNext()) {
            return emptyList()
        }

        @Suppress("UNCHECKED_CAST")
        val aggregateClass = resolveGenericTypeClass(
            ids.iterator().next(), 0, Id::class.java, Id.Default::class.java
        ) as Class<AGGREGATE>

        val aggregatePredicate = JpaAggregatePredicate.byIds(
            aggregateClass,
            ids.map { it.value }
        )
        return remove(aggregatePredicate)
    }

    override fun <AGGREGATE : Aggregate<*>> remove(
        predicate: AggregatePredicate<AGGREGATE, *>
    ): List<AGGREGATE> {
        val clazz = JpaAggregatePredicateSupport.reflectAggregateClass(predicate)
        val pred = JpaAggregatePredicateSupport.getPredicate(predicate)
        val entities = repositorySupervisor.remove(pred)
        return entities.map { entity -> newInstance(clazz, entity) }
    }

    override fun <AGGREGATE : Aggregate<*>> remove(
        predicate: AggregatePredicate<AGGREGATE, *>,
        limit: Int
    ): List<AGGREGATE> {
        val clazz = JpaAggregatePredicateSupport.reflectAggregateClass(predicate)
        val pred = JpaAggregatePredicateSupport.getPredicate(predicate)
        val entities = repositorySupervisor.remove(pred, limit)
        return entities.map { entity -> newInstance(clazz, entity) }
    }

    override fun <AGGREGATE : Aggregate<*>> count(
        predicate: AggregatePredicate<AGGREGATE, *>
    ): Long {
        val pred = JpaAggregatePredicateSupport.getPredicate(predicate)
        return repositorySupervisor.count(pred)
    }

    override fun <AGGREGATE : Aggregate<*>> exists(
        predicate: AggregatePredicate<AGGREGATE, *>
    ): Boolean {
        val pred = JpaAggregatePredicateSupport.getPredicate(predicate)
        return repositorySupervisor.exists(pred)
    }
}

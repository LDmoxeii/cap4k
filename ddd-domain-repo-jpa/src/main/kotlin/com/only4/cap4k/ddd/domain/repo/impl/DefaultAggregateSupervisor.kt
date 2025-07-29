package com.only4.cap4k.ddd.domain.repo.impl

import com.only4.cap4k.ddd.core.domain.aggregate.Aggregate
import com.only4.cap4k.ddd.core.domain.aggregate.AggregateFactorySupervisor
import com.only4.cap4k.ddd.core.domain.aggregate.AggregatePayload
import com.only4.cap4k.ddd.core.domain.aggregate.Id
import com.only4.cap4k.ddd.core.domain.repo.AggregatePredicate
import com.only4.cap4k.ddd.core.domain.repo.AggregateSupervisor
import com.only4.cap4k.ddd.core.domain.repo.RepositorySupervisor
import com.only4.cap4k.ddd.core.share.OrderInfo
import com.only4.cap4k.ddd.core.share.PageData
import com.only4.cap4k.ddd.core.share.PageParam
import com.only4.cap4k.ddd.core.share.misc.resolveGenericTypeClass
import com.only4.cap4k.ddd.domain.repo.JpaAggregatePredicate
import com.only4.cap4k.ddd.domain.repo.JpaAggregatePredicateSupport
import java.util.*

/**
 * 默认聚合管理器
 *
 * @author LD_moxeii
 * @date 2025/07/29
 */
class DefaultAggregateSupervisor(
    private val repositorySupervisor: RepositorySupervisor
) : AggregateSupervisor {

    private inline fun <reified AGGREGATE : Aggregate<*>> newInstance(entity: Any): AGGREGATE {
        return newInstance(AGGREGATE::class.java, entity)
    }

    private fun <AGGREGATE : Aggregate<*>> newInstance(clazz: Class<AGGREGATE>, entity: Any): AGGREGATE {
        return try {
            val aggregate = clazz.getDeclaredConstructor().newInstance()
            @Suppress("UNCHECKED_CAST")
            (aggregate as Aggregate<Any>)._wrap(entity)
            aggregate
        } catch (ex: Exception) {
            throw RuntimeException(ex)
        }
    }

    override fun <AGGREGATE : Aggregate<ENTITY>, ENTITY_PAYLOAD : AggregatePayload<ENTITY>, ENTITY : Any> create(
        clazz: Class<AGGREGATE>,
        payload: ENTITY_PAYLOAD
    ): AGGREGATE {
        val entity = AggregateFactorySupervisor.instance.create(payload)!!
        return newInstance(clazz, entity)
    }

    override fun <AGGREGATE : Aggregate<ENTITY>, ENTITY : Any> getByIds(
        ids: Iterable<Id<AGGREGATE, *>>,
        persist: Boolean
    ): List<AGGREGATE> {
        if (!ids.iterator().hasNext()) {
            return emptyList()
        }

        val firstId = ids.first()

        @Suppress("UNCHECKED_CAST")
        val aggregateClass = resolveGenericTypeClass(
            firstId, 0,
            Id::class.java, Id.Default::class.java
        ) as Class<AGGREGATE>

        val aggregatePredicate = JpaAggregatePredicate.byIds(
            aggregateClass,
            ids.map { it.value }
        )
        return find(aggregatePredicate, persist)
    }

    override fun <AGGREGATE : Aggregate<*>> find(
        aggregatePredicate: AggregatePredicate<AGGREGATE, *>,
        orders: Collection<OrderInfo>?,
        persist: Boolean
    ): List<AGGREGATE> {
        val clazz = JpaAggregatePredicateSupport.reflectAggregateClass(aggregatePredicate)
        @Suppress("UNCHECKED_CAST")
        val predicate = (aggregatePredicate as JpaAggregatePredicate<AGGREGATE, Any>).predicate
        val entities = repositorySupervisor.find(predicate, orders, persist)
        return entities.map { newInstance(clazz, it) }
    }

    override fun <AGGREGATE : Aggregate<*>> find(
        aggregatePredicate: AggregatePredicate<AGGREGATE, *>,
        pageParam: PageParam,
        persist: Boolean
    ): List<AGGREGATE> {
        val clazz = JpaAggregatePredicateSupport.reflectAggregateClass(aggregatePredicate)
        @Suppress("UNCHECKED_CAST")
        val predicate = (aggregatePredicate as JpaAggregatePredicate<AGGREGATE, Any>).predicate
        val entities = repositorySupervisor.find(predicate, pageParam, persist)
        return entities.map { newInstance(clazz, it) }
    }

    override fun <AGGREGATE : Aggregate<*>> findOne(
        aggregatePredicate: AggregatePredicate<AGGREGATE, *>,
        persist: Boolean
    ): Optional<AGGREGATE> {
        val clazz = JpaAggregatePredicateSupport.reflectAggregateClass(aggregatePredicate)
        @Suppress("UNCHECKED_CAST")
        val predicate = (aggregatePredicate as JpaAggregatePredicate<AGGREGATE, Any>).predicate
        val entity = repositorySupervisor.findOne(predicate, persist)
        return entity.map { newInstance(clazz, it) }
    }

    override fun <AGGREGATE : Aggregate<*>> findFirst(
        aggregatePredicate: AggregatePredicate<AGGREGATE, *>,
        orders: Collection<OrderInfo>,
        persist: Boolean
    ): Optional<AGGREGATE> {
        val clazz = JpaAggregatePredicateSupport.reflectAggregateClass(aggregatePredicate)
        @Suppress("UNCHECKED_CAST")
        val predicate = (aggregatePredicate as JpaAggregatePredicate<AGGREGATE, Any>).predicate
        val entity = repositorySupervisor.findFirst(predicate, orders, persist)
        return entity.map { newInstance(clazz, it) }
    }

    override fun <AGGREGATE : Aggregate<*>> findPage(
        aggregatePredicate: AggregatePredicate<AGGREGATE, *>,
        pageParam: PageParam,
        persist: Boolean
    ): PageData<AGGREGATE> {
        val clazz = JpaAggregatePredicateSupport.reflectAggregateClass(aggregatePredicate)
        @Suppress("UNCHECKED_CAST")
        val predicate = (aggregatePredicate as JpaAggregatePredicate<AGGREGATE, Any>).predicate
        val entities = repositorySupervisor.findPage(predicate, pageParam, persist)
        return entities.transform { newInstance(clazz, it) }
    }

    override fun <AGGREGATE : Aggregate<ENTITY>, ENTITY: Any> removeByIds(
        ids: Iterable<Id<AGGREGATE, *>>
    ): List<AGGREGATE> {
        if (!ids.iterator().hasNext()) {
            return emptyList()
        }

        val firstId = ids.first()

        @Suppress("UNCHECKED_CAST")
        val aggregateClass = resolveGenericTypeClass(
            firstId, 0,
            Id::class.java, Id.Default::class.java
        ) as Class<AGGREGATE>

        val aggregatePredicate = JpaAggregatePredicate.byIds(
            aggregateClass,
            ids.map { it.value }
        )
        return remove(aggregatePredicate)
    }

    override fun <AGGREGATE : Aggregate<*>> remove(
        aggregatePredicate: AggregatePredicate<AGGREGATE, *>
    ): List<AGGREGATE> {
        val clazz = JpaAggregatePredicateSupport.reflectAggregateClass(aggregatePredicate)
        @Suppress("UNCHECKED_CAST")
        val predicate = (aggregatePredicate as JpaAggregatePredicate<AGGREGATE, Any>).predicate
        val entities = repositorySupervisor.remove(predicate)
        return entities.map { newInstance(clazz, it) }
    }

    override fun <AGGREGATE : Aggregate<*>> remove(
        aggregatePredicate: AggregatePredicate<AGGREGATE, *>,
        limit: Int
    ): List<AGGREGATE> {
        val clazz = JpaAggregatePredicateSupport.reflectAggregateClass(aggregatePredicate)
        @Suppress("UNCHECKED_CAST")
        val predicate = (aggregatePredicate as JpaAggregatePredicate<AGGREGATE, Any>).predicate
        val entities = repositorySupervisor.remove(predicate, limit)
        return entities.map { newInstance(clazz, it) }
    }

    override fun <AGGREGATE : Aggregate<*>> count(
        aggregatePredicate: AggregatePredicate<AGGREGATE, *>
    ): Long {
        @Suppress("UNCHECKED_CAST")
        val predicate = (aggregatePredicate as JpaAggregatePredicate<AGGREGATE, Any>).predicate
        return repositorySupervisor.count(predicate)
    }

    override fun <AGGREGATE : Aggregate<*>> exists(
        aggregatePredicate: AggregatePredicate<AGGREGATE, *>
    ): Boolean {
        @Suppress("UNCHECKED_CAST")
        val predicate = (aggregatePredicate as JpaAggregatePredicate<AGGREGATE, Any>).predicate
        return repositorySupervisor.exists(predicate)
    }
}

package com.only4.cap4k.ddd.domain.repo

import com.only4.cap4k.ddd.core.domain.repo.AggregateLoadPlan
import jakarta.persistence.OneToMany
import org.hibernate.Hibernate

object JpaAggregateLoadPlanSupport {

    fun apply(entities: Iterable<Any>, loadPlan: AggregateLoadPlan) {
        if (loadPlan != AggregateLoadPlan.WHOLE_AGGREGATE) return
        val visited = mutableSetOf<Int>()
        entities.forEach { entity -> initializeOwnedCollections(entity, visited) }
    }

    fun apply(entity: Any, loadPlan: AggregateLoadPlan) {
        if (loadPlan != AggregateLoadPlan.WHOLE_AGGREGATE) return
        initializeOwnedCollections(entity, mutableSetOf())
    }

    private fun initializeOwnedCollections(entity: Any, visited: MutableSet<Int>) {
        val identity = System.identityHashCode(entity)
        if (!visited.add(identity)) return

        for (field in persistentFields(Hibernate.getClass(entity))) {
            val oneToMany = field.getAnnotation(OneToMany::class.java) ?: continue
            if (!oneToMany.orphanRemoval && oneToMany.cascade.isEmpty()) continue

            field.isAccessible = true
            val value = field.get(entity) ?: continue
            Hibernate.initialize(value)

            if (value is Iterable<*>) {
                value.filterNotNull().forEach { child -> initializeOwnedCollections(child, visited) }
            }
        }
    }

    private fun persistentFields(type: Class<*>): Sequence<java.lang.reflect.Field> =
        generateSequence(type) { current ->
            current.superclass?.takeIf { it != Any::class.java }
        }.flatMap { current ->
            current.declaredFields.asSequence()
        }
}

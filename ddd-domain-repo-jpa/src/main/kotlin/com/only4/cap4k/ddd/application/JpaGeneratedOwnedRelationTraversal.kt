package com.only4.cap4k.ddd.application

import jakarta.persistence.CascadeType
import jakarta.persistence.JoinColumn
import jakarta.persistence.OneToMany
import org.hibernate.Hibernate
import java.lang.reflect.Field
import java.util.Collections
import java.util.IdentityHashMap

internal class JpaGeneratedOwnedRelationTraversal {
    fun reachableOwnedEntities(root: Any): List<Any> {
        val visited = Collections.newSetFromMap(IdentityHashMap<Any, Boolean>())
        val result = mutableListOf<Any>()
        visit(root, visited, result)
        return result
    }

    private fun visit(entity: Any, visited: MutableSet<Any>, result: MutableList<Any>) {
        if (!visited.add(entity)) return
        result += entity
        persistentFields(Hibernate.getClassLazy(entity)).forEach { field ->
            val oneToMany = field.getAnnotation(OneToMany::class.java) ?: return@forEach
            if (oneToMany.mappedBy.isNotBlank()) return@forEach
            if (field.getAnnotation(JoinColumn::class.java) == null) return@forEach
            val cascades = oneToMany.cascade.toSet()
            if (CascadeType.PERSIST !in cascades || CascadeType.MERGE !in cascades) return@forEach
            if (!oneToMany.orphanRemoval) return@forEach
            field.isAccessible = true
            val value = field.get(entity) ?: return@forEach
            if (!Hibernate.isInitialized(value)) return@forEach
            if (value is Iterable<*>) {
                value.filterNotNull().forEach { visit(it, visited, result) }
            }
        }
    }

    private fun persistentFields(type: Class<*>): Sequence<Field> =
        generateSequence(type) { current -> current.superclass?.takeIf { it != Any::class.java } }
            .flatMap { it.declaredFields.asSequence() }
}

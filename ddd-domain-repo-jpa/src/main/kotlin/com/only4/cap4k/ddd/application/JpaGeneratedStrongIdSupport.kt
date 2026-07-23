package com.only4.cap4k.ddd.application

import com.only4.cap4k.ddd.core.domain.id.StrongId
import jakarta.persistence.EmbeddedId
import org.hibernate.Hibernate
import java.lang.reflect.Field

internal class JpaGeneratedStrongIdSupport {
    fun completeCreate(root: Any, traversal: JpaGeneratedOwnedRelationTraversal) {
        traversal.reachableOwnedEntities(root).forEach(::completeMissingOwnStrongId)
    }

    fun completeExisting(
        root: Any,
        traversal: JpaGeneratedOwnedRelationTraversal,
        baseline: JpaRepositoryObservationBaseline,
    ) {
        traversal.reachableOwnedEntities(root)
            .filterNot { baseline.isObservedObject(it) }
            .forEach(::completeMissingOwnStrongId)
        validateObservedStrongIds(root, traversal, baseline)
    }

    private fun completeMissingOwnStrongId(entity: Any) {
        ownStrongIdField(entity)?.let { field ->
            field.isAccessible = true
            if (field.get(entity) == null) {
                field.set(entity, newStrongId(field.type))
            }
        }
    }

    private fun validateObservedStrongIds(
        root: Any,
        traversal: JpaGeneratedOwnedRelationTraversal,
        baseline: JpaRepositoryObservationBaseline,
    ) {
        traversal.reachableOwnedEntities(root)
            .filter { baseline.isObservedObject(it) }
            .forEach { entity ->
                ownStrongIdField(entity)?.let { field ->
                    field.isAccessible = true
                    check(field.get(entity) != null) {
                        "Observed existing entity ${Hibernate.getClassLazy(entity).name}.${field.name} has missing Strong ID"
                    }
                }
            }
    }

    private fun ownStrongIdField(entity: Any): Field? =
        persistentFields(Hibernate.getClassLazy(entity)).firstOrNull { field ->
            field.getAnnotation(EmbeddedId::class.java) != null &&
                StrongId::class.java.isAssignableFrom(field.type)
        }

    private fun newStrongId(type: Class<*>): Any {
        val companion = type.getField("Companion").get(null)
        val newMethod = companion.javaClass.methods.firstOrNull { method ->
            method.name == "new" && method.parameterCount == 0
        } ?: error("Generated Strong ID ${type.name} must expose companion new() for own ID completion")
        return newMethod.invoke(companion)
    }

    private fun persistentFields(type: Class<*>): Sequence<Field> =
        generateSequence(type) { current -> current.superclass?.takeIf { it != Any::class.java } }
            .flatMap { it.declaredFields.asSequence() }
}

package com.only4.cap4k.ddd.application

import com.only4.cap4k.ddd.core.domain.id.StrongId
import jakarta.persistence.EmbeddedId
import org.hibernate.Hibernate
import org.hibernate.proxy.HibernateProxy
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
        validateExistingRootStrongId(root)
        val reachable = traversal.reachableOwnedEntities(root)
        validateObservedStrongIds(reachable, baseline)
        reachable.asSequence()
            .filterNot { it === root }
            .filterNot { baseline.isObservedObject(it) }
            .forEach(::completeMissingOwnStrongId)
    }

    private fun validateExistingRootStrongId(root: Any) {
        ownStrongIdField(root)?.let { field ->
            field.isAccessible = true
            check(ownStrongIdValue(root, field) != null) {
                "Existing-intent root ${Hibernate.getClassLazy(root).name}.${field.name} has missing Strong ID"
            }
        }
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
        reachable: Iterable<Any>,
        baseline: JpaRepositoryObservationBaseline,
    ) {
        reachable
            .filter { baseline.isObservedObject(it) }
            .forEach { entity ->
                ownStrongIdField(entity)?.let { field ->
                    field.isAccessible = true
                    val currentId = ownStrongIdValue(entity, field)
                    check(currentId != null) {
                        "Observed existing entity ${Hibernate.getClassLazy(entity).name}.${field.name} has missing Strong ID"
                    }
                    baseline.identityFor(entity)?.let { observed ->
                        check(currentId == observed.id) {
                            "Observed existing entity ${observed.entityType.name} changed identity " +
                                "from ${observed.id} to $currentId"
                        }
                    }
                }
            }
    }

    private fun ownStrongIdValue(entity: Any, field: Field): Any? =
        (entity as? HibernateProxy)?.hibernateLazyInitializer?.identifier ?: field.get(entity)

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

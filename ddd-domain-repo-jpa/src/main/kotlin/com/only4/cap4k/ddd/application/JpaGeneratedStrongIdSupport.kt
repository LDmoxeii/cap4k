package com.only4.cap4k.ddd.application

import com.only4.cap4k.ddd.core.domain.id.GeneratedOwnIdAccessor
import com.only4.cap4k.ddd.core.domain.id.GeneratedOwnIdRegistry
import org.hibernate.Hibernate

internal class JpaGeneratedStrongIdSupport(
    private val registry: GeneratedOwnIdRegistry,
) {
    fun completeCreate(root: Any, traversal: JpaGeneratedOwnedRelationTraversal) {
        traversal.reachableOwnedEntities(root).forEach(::assignIfRegistered)
    }

    fun completeExisting(
        root: Any,
        traversal: JpaGeneratedOwnedRelationTraversal,
        baseline: JpaRepositoryObservationBaseline,
    ) {
        val reachable = traversal.reachableOwnedEntities(root)
        val traversalRoot = reachable.firstOrNull() ?: root
        validateExistingRoot(traversalRoot)
        validateObservedIdentities(reachable, baseline)
        reachable.asSequence()
            .filterNot { it === traversalRoot }
            .filterNot { baseline.isObservedObject(it) }
            .forEach(::assignIfRegistered)
    }

    private fun assignIfRegistered(entity: Any) {
        accessorFor(entity)?.assignIfMissing(entity)
    }

    private fun validateExistingRoot(root: Any) {
        accessorFor(root)?.let { accessor ->
            check(accessor.current(root) != null) {
                "Existing-intent root ${Hibernate.getClassLazy(root).name} has missing generated own ID"
            }
        }
    }

    private fun validateObservedIdentities(
        reachable: Iterable<Any>,
        baseline: JpaRepositoryObservationBaseline,
    ) {
        reachable.filter { baseline.isObservedObject(it) }.forEach { entity ->
            accessorFor(entity)?.let { accessor ->
                val current = accessor.current(entity)
                check(current != null) {
                    "Observed existing entity ${Hibernate.getClassLazy(entity).name} has missing generated own ID"
                }
                baseline.identityFor(entity)?.let { observed ->
                    check(current == observed.id) {
                        "Observed existing entity ${observed.entityType.name} changed identity " +
                            "from ${observed.id} to $current"
                    }
                }
            }
        }
    }

    private fun accessorFor(entity: Any): GeneratedOwnIdAccessor<Any, Any>? =
        registry.accessorFor(Hibernate.getClassLazy(entity).kotlin)
}

package com.only4.cap4k.ddd.application

import jakarta.persistence.EntityManager
import org.hibernate.engine.spi.SharedSessionContractImplementor
import org.hibernate.engine.spi.Status

internal class JpaHibernateDirtyInspector(
    private val entityManager: EntityManager,
) {
    fun dirtyManagedEntities(candidates: Iterable<Any>): Set<Any> {
        val session = entityManager.unwrap(SharedSessionContractImplementor::class.java)
        return candidates.filterTo(LinkedHashSet()) { entity ->
            isDirty(session, entity)
        }
    }

    private fun isDirty(session: SharedSessionContractImplementor, entity: Any): Boolean {
        val entry = session.persistenceContextInternal.getEntry(entity) ?: return false
        if (entry.status != Status.MANAGED) return false
        val loadedState = entry.loadedState ?: return true
        val persister = entry.persister
        val currentState = persister.getValues(entity)
        return persister.findDirty(currentState, loadedState, entity, session)?.isNotEmpty() == true
    }
}

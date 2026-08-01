package com.only4.cap4k.ddd.application

import com.only4.cap4k.ddd.core.application.context.ExecutionContextSnapshot
import com.only4.cap4k.ddd.core.domain.managed.ManagedFieldHandle
import java.time.Instant

enum class JpaAggregateRootOperation {
    NONE,
    CREATE,
    DELETE,
}

enum class JpaEntityChangeType {
    CREATE,
    UPDATE,
    DELETE,
}

data class JpaEntityChange(
    val entity: Any,
    val type: JpaEntityChangeType,
)

/** Net persistent effect for one aggregate in the current stabilization round. */
data class JpaAggregateChange(
    val root: Any,
    val rootOperation: JpaAggregateRootOperation,
    val entityChanges: List<JpaEntityChange>,
)

data class JpaPersistenceEnrichmentContext(
    val timestamp: Instant,
    val executionContext: ExecutionContextSnapshot,
)

enum class JpaManagedOperation {
    CREATE,
    UPDATE,
}

data class JpaManagedEntityFields(
    val entity: Any,
    val operation: JpaManagedOperation,
    val handles: List<ManagedFieldHandle>,
)

interface JpaManagedFieldSet : Iterable<JpaManagedEntityFields> {
    fun forEntity(entity: Any): JpaManagedEntityFields?
}

interface JpaPersistenceEnricher {
    val qualifiers: Set<String>

    fun enrich(
        change: JpaAggregateChange,
        context: JpaPersistenceEnrichmentContext,
        fields: JpaManagedFieldSet,
    )
}

class JpaAuditTimePersistenceEnricher : JpaPersistenceEnricher {
    override val qualifiers: Set<String> = setOf(QUALIFIER)

    override fun enrich(
        change: JpaAggregateChange,
        context: JpaPersistenceEnrichmentContext,
        fields: JpaManagedFieldSet,
    ) {
        fields.forEach { entityFields ->
            entityFields.handles.forEach { handle ->
                when (handle.handlerSlot) {
                    CREATED_AT -> check(entityFields.operation == JpaManagedOperation.CREATE) {
                        "audit created-at handle must only participate in CREATE"
                    }
                    UPDATED_AT -> Unit
                    else -> error(
                        "unsupported audit-time slot '${handle.handlerSlot}' for ${handle.policyKey}",
                    )
                }
                handle.assignSemantic(context.timestamp)
            }
        }
    }

    companion object {
        const val QUALIFIER = "enrichment.audit-time"
        const val CREATED_AT = "created-at"
        const val UPDATED_AT = "updated-at"
    }
}

internal class DefaultJpaManagedFieldSet(
    private val entries: List<JpaManagedEntityFields>,
) : JpaManagedFieldSet, Iterable<JpaManagedEntityFields> by entries {
    override fun forEntity(entity: Any): JpaManagedEntityFields? =
        entries.singleOrNull { it.entity === entity }
}

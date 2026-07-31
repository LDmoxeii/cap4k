package com.only4.cap4k.ddd.application

import com.only4.cap4k.ddd.core.application.context.ExecutionContextSnapshot
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

/** Stable audit input captured once for the outer Command Unit of Work. */
data class JpaPersistenceAuditContext(
    val auditTime: Instant,
    val executionContext: ExecutionContextSnapshot,
)

/**
 * Enriches aggregate-oriented persistence changes before Hibernate performs
 * final dirty detection. Implementations may update scalar or embedded audit
 * values only and must be idempotent across stabilization rounds.
 */
fun interface JpaPersistenceAuditEnricher {
    fun enrich(
        changeSet: JpaAggregateChange,
        context: JpaPersistenceAuditContext,
    )
}

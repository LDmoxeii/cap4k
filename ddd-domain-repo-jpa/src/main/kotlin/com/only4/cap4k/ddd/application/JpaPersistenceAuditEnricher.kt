package com.only4.cap4k.ddd.application

import java.time.Instant

enum class JpaPersistenceChangeType {
    CREATE,
    UPDATE,
    DELETE,
}

/**
 * Stable audit data captured once for the outer transaction-level Unit of Work.
 *
 * Provider integrations may extend the attributes with actor, tenant, and environment
 * information without exposing an unrestricted Unit of Work callback surface.
 */
data class JpaPersistenceAuditContext(
    val timestamp: Instant,
    val attributes: Map<String, Any?> = emptyMap(),
)

data class JpaPersistenceAuditCandidate(
    val entity: Any,
    val type: JpaPersistenceChangeType,
)

/**
 * Enriches provider-detected persistence candidates before Hibernate performs its final
 * dirty detection and flush.
 *
 * Implementations must only enrich the supplied candidates. They must not flush, publish
 * events, start Commands, or complete the surrounding transaction.
 */
fun interface JpaPersistenceAuditEnricher {
    fun enrich(
        candidates: List<JpaPersistenceAuditCandidate>,
        context: JpaPersistenceAuditContext,
    )
}

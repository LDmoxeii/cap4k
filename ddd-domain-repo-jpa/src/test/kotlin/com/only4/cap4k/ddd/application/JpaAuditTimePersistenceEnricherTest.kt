package com.only4.cap4k.ddd.application

import com.only4.cap4k.ddd.core.application.context.ExecutionContextSnapshot
import com.only4.cap4k.ddd.core.domain.managed.ManagedFieldHandle
import com.only4.cap4k.ddd.core.domain.managed.ManagedFieldRuntimeSupport
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.reflect.KClass

class JpaAuditTimePersistenceEnricherTest {
    @Test
    fun `create assigns created and updated slots from the stable context timestamp`() {
        val created = RecordingHandle(JpaAuditTimePersistenceEnricher.CREATED_AT)
        val updated = RecordingHandle(JpaAuditTimePersistenceEnricher.UPDATED_AT)
        val entity = Any()
        val fields = DefaultJpaManagedFieldSet(
            listOf(JpaManagedEntityFields(entity, JpaManagedOperation.CREATE, listOf(created, updated)))
        )
        val timestamp = Instant.parse("2026-07-31T00:00:00Z")

        JpaAuditTimePersistenceEnricher().enrich(
            JpaAggregateChange(entity, JpaAggregateRootOperation.CREATE, emptyList()),
            JpaPersistenceEnrichmentContext(timestamp, ExecutionContextSnapshot.EMPTY),
            fields,
        )

        assertEquals(timestamp, created.value)
        assertEquals(timestamp, updated.value)
    }

    @Test
    fun `created slot is rejected for update`() {
        val entity = Any()
        val fields = DefaultJpaManagedFieldSet(
            listOf(
                JpaManagedEntityFields(
                    entity,
                    JpaManagedOperation.UPDATE,
                    listOf(RecordingHandle(JpaAuditTimePersistenceEnricher.CREATED_AT)),
                )
            )
        )

        assertThrows(IllegalStateException::class.java) {
            JpaAuditTimePersistenceEnricher().enrich(
                JpaAggregateChange(entity, JpaAggregateRootOperation.NONE, emptyList()),
                JpaPersistenceEnrichmentContext(Instant.EPOCH, ExecutionContextSnapshot.EMPTY),
                fields,
            )
        }
    }

    private class RecordingHandle(
        override val handlerSlot: String?,
    ) : ManagedFieldHandle {
        var value: Any? = null
        override val fieldName = handlerSlot.orEmpty()
        override val persistencePropertyName = fieldName
        override val policyKey = "enrichment.audit-time.$fieldName"
        override val handlerQualifier = JpaAuditTimePersistenceEnricher.QUALIFIER
        override val semanticValueType: KClass<*> = Instant::class
        override val targetType: KClass<*> = Instant::class
        override val nullable = false
        override val runtimeSupport: ManagedFieldRuntimeSupport? = null
        override val mutationFootprint: Set<String> = setOf(fieldName)
        override fun readTarget(): Any? = value
        override fun adaptSemantic(value: Any?): Any? = value
        override fun matchesSemantic(value: Any?): Boolean = this.value == value
        override fun assignSemantic(value: Any?) {
            this.value = value
        }
    }
}

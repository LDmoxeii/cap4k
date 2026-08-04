package com.only4.cap4k.ddd.core.domain.managed

import com.only4.cap4k.ddd.core.application.context.ExecutionContextAccessor
import com.only4.cap4k.ddd.core.application.context.ExecutionContextElement
import com.only4.cap4k.ddd.core.application.context.ExecutionContextKey
import com.only4.cap4k.ddd.core.application.context.ExecutionContextSnapshot
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ManagedEntityAdmissionTest {
    @Test
    fun `application identifier allocates at admission and not again on repeated admission`() {
        var sequence = 0L
        val coordinator = coordinator(
            identifierBinding(
                allocate = { ++sequence },
                validate = { require((it as Long) > 0) },
            )
        )
        val entity = IdentifiedEntity()

        coordinator.admit(entity, ManagedEntityAdmissionKind.AGGREGATE_ROOT)
        coordinator.admit(entity, ManagedEntityAdmissionKind.AGGREGATE_ROOT)

        assertEquals(1L, entity.id)
        assertEquals(1L, sequence)
    }

    @Test
    fun `application identifier preserves and validates an explicit value`() {
        var allocations = 0
        val coordinator = coordinator(
            identifierBinding(
                allocate = { allocations++; 99L },
                validate = { require((it as Long) == 42L) },
            )
        )
        val entity = IdentifiedEntity(42L)

        coordinator.admit(entity, ManagedEntityAdmissionKind.OWNED_CHILD)

        assertEquals(42L, entity.id)
        assertEquals(0, allocations)
    }

    @Test
    fun `assigned identifier requires a caller value and never allocates`() {
        val binding = identifierBinding(
            allocate = { error("assigned identity must not allocate") },
            validate = { require((it as Long) > 0) },
        ).copy(
            policyKey = "identifier.assigned",
            handlerQualifier = "identifier.assigned",
            explicitValue = ManagedExplicitValuePolicy.REQUIRE,
            persistence = PersistenceParticipation(ManagedValueAuthority.CALLER, ManagedValueAuthority.NONE),
            runtimeSupport = ManagedFieldRuntimeSupport.ApplicationIdentifier(
                isAbsent = { it == null || it == 0L },
                allocateTarget = null,
                validateTarget = { require((it as Long) > 0) },
            ),
        )
        val coordinator = coordinator(binding)

        val failure = assertThrows(IllegalStateException::class.java) {
            coordinator.admit(IdentifiedEntity(), ManagedEntityAdmissionKind.AGGREGATE_ROOT)
        }
        val explicit = IdentifiedEntity(42L)
        coordinator.admit(explicit, ManagedEntityAdmissionKind.AGGREGATE_ROOT)

        assertTrue(failure.message.orEmpty().contains("requires an explicit value"))
        assertEquals(42L, explicit.id)
    }

    @Test
    fun `soft delete admission fills only the active sentinel and rejects a tombstone`() {
        val binding = ManagedFieldBinding(
            entityType = SoftDeleteEntity::class,
            fieldName = "deleted",
            persistencePropertyName = "deleted",
            columnName = "deleted",
            targetType = Boolean::class,
            nullable = true,
            policyKey = "soft-delete",
            role = ManagedFieldRole.SOFT_DELETE,
            explicitValue = ManagedExplicitValuePolicy.PRESERVE_IF_VALID,
            lifecycles = setOf(ManagedFieldLifecycle.ENTITY_ADMISSION, ManagedFieldLifecycle.PERSISTENCE_PROVIDER),
            handlerQualifier = "soft-delete",
            handlerSlot = null,
            semanticValueType = Boolean::class,
            valueAdapterQualifier = null,
            persistence = PersistenceParticipation(ManagedValueAuthority.FRAMEWORK, ManagedValueAuthority.PERSISTENCE_PROVIDER),
            runtimeSupport = ManagedFieldRuntimeSupport.SoftDelete(activeSentinel = false),
        )
        val coordinator = coordinator(binding)
        val active = SoftDeleteEntity()
        val explicitActive = SoftDeleteEntity(false)

        coordinator.admit(active, ManagedEntityAdmissionKind.AGGREGATE_ROOT)
        coordinator.admit(active, ManagedEntityAdmissionKind.AGGREGATE_ROOT)
        coordinator.admit(explicitActive, ManagedEntityAdmissionKind.OWNED_CHILD)
        coordinator.admit(explicitActive, ManagedEntityAdmissionKind.OWNED_CHILD)
        val failure = assertThrows(IllegalStateException::class.java) {
            coordinator.admit(SoftDeleteEntity(true), ManagedEntityAdmissionKind.OWNED_CHILD)
        }

        assertEquals(false, active.deleted)
        assertEquals(false, explicitActive.deleted)
        assertTrue(failure.message.orEmpty().contains("active soft-delete sentinel"))
    }

    @Test
    fun `admission resolves bindings declared for a generated entity superclass`() {
        val coordinator = coordinator(
            identifierBinding(
                allocate = { 43L },
                validate = { require((it as Long) > 0) },
            )
        )
        val entity = DerivedIdentifiedEntity()

        coordinator.admit(entity, ManagedEntityAdmissionKind.AGGREGATE_ROOT)

        assertEquals(43L, entity.id)
    }

    @Test
    fun `admission managed identity cannot be replaced after admission`() {
        val coordinator = coordinator(
            identifierBinding(
                allocate = { 44L },
                validate = { require((it as Long) > 0) },
            )
        )
        val entity = IdentifiedEntity()
        coordinator.admit(entity, ManagedEntityAdmissionKind.AGGREGATE_ROOT)
        entity.id = 45L

        val failure = assertThrows(IllegalStateException::class.java) {
            coordinator.validate(entity, ExecutionContextSnapshot.EMPTY)
        }

        assertTrue(failure.message.orEmpty().contains("immutable admission fields"))
    }

    @Test
    fun `uow validation never performs late identifier allocation`() {
        var allocations = 0
        val coordinator = coordinator(
            identifierBinding(
                allocate = { allocations++; 1L },
                validate = { require((it as Long) > 0) },
            )
        )
        val bypassed = IdentifiedEntity()

        assertThrows(IllegalStateException::class.java) {
            coordinator.validate(bypassed, ExecutionContextSnapshot.EMPTY)
        }
        assertEquals(0L, bypassed.id)
        assertEquals(0, allocations)
    }

    @Test
    fun `database identity rejects explicit values without requiring an initializer`() {
        val binding = ManagedFieldBinding(
            entityType = DatabaseIdentityEntity::class,
            fieldName = "id",
            persistencePropertyName = "id",
            columnName = "id",
            targetType = Long::class,
            nullable = false,
            policyKey = "identifier.database-identity",
            role = ManagedFieldRole.IDENTIFIER,
            explicitValue = ManagedExplicitValuePolicy.FORBID,
            lifecycles = setOf(ManagedFieldLifecycle.DATABASE),
            handlerQualifier = null,
            handlerSlot = null,
            semanticValueType = Long::class,
            valueAdapterQualifier = null,
            persistence = PersistenceParticipation(ManagedValueAuthority.DATABASE, ManagedValueAuthority.NONE),
            runtimeSupport = ManagedFieldRuntimeSupport.ForbiddenExplicitValue { value -> value == null },
        )
        val registry = DefaultManagedFieldRegistry(
            catalogs = listOf(object : ManagedFieldCatalog {
                override val bindings = listOf(binding)
            }),
        )
        val coordinator = DefaultManagedEntityAdmissionCoordinator(
            registry,
            ExecutionContextAccessor { ExecutionContextSnapshot.EMPTY },
        )

        coordinator.admit(DatabaseIdentityEntity(), ManagedEntityAdmissionKind.AGGREGATE_ROOT)
        val failure = assertThrows(IllegalStateException::class.java) {
            coordinator.validate(DatabaseIdentityEntity(42L), ExecutionContextSnapshot.EMPTY)
        }

        assertEquals(
            "managed field id[identifier.database-identity] forbids an explicit value before persistence",
            failure.message,
        )

        val primitiveBinding = binding.copy(
            entityType = PrimitiveDatabaseIdentityEntity::class,
            runtimeSupport = ManagedFieldRuntimeSupport.ForbiddenExplicitValue { value ->
                value == null || (value as? Number)?.toLong() == 0L
            },
        )
        val primitiveCoordinator = DefaultManagedEntityAdmissionCoordinator(
            DefaultManagedFieldRegistry(catalogs = listOf(catalog(primitiveBinding))),
            ExecutionContextAccessor { ExecutionContextSnapshot.EMPTY },
        )
        primitiveCoordinator.admit(PrimitiveDatabaseIdentityEntity(), ManagedEntityAdmissionKind.AGGREGATE_ROOT)
        assertThrows(IllegalStateException::class.java) {
            primitiveCoordinator.admit(
                PrimitiveDatabaseIdentityEntity(42L),
                ManagedEntityAdmissionKind.AGGREGATE_ROOT,
            )
        }
    }

    @Test
    fun `context bound initializer fills absence accepts equality and rejects missing or mismatched context`() {
        var currentContext = tenantContext("tenant-a")
        val binding = ManagedFieldBinding(
            entityType = TenantEntity::class,
            fieldName = "tenantId",
            persistencePropertyName = "tenantId",
            columnName = "tenant_id",
            targetType = String::class,
            nullable = false,
            policyKey = "scope.tenant",
            role = ManagedFieldRole.SCOPE,
            explicitValue = ManagedExplicitValuePolicy.REQUIRE_CONTEXT_MATCH,
            lifecycles = setOf(ManagedFieldLifecycle.ENTITY_ADMISSION),
            handlerQualifier = "scope.tenant",
            handlerSlot = null,
            semanticValueType = String::class,
            valueAdapterQualifier = null,
            persistence = PersistenceParticipation(ManagedValueAuthority.MANAGED_HANDLER, ManagedValueAuthority.NONE),
        )
        val registry = DefaultManagedFieldRegistry(
            catalogs = listOf(catalog(binding)),
            initializers = listOf(TenantContextInitializer),
        )
        val coordinator = DefaultManagedEntityAdmissionCoordinator(
            registry,
            ExecutionContextAccessor { currentContext },
        )

        val filled = TenantEntity()
        coordinator.admit(filled, ManagedEntityAdmissionKind.AGGREGATE_ROOT)
        coordinator.admit(TenantEntity("tenant-a"), ManagedEntityAdmissionKind.OWNED_CHILD)

        assertEquals("tenant-a", filled.tenantId)
        assertThrows(IllegalStateException::class.java) {
            coordinator.admit(TenantEntity("tenant-b"), ManagedEntityAdmissionKind.OWNED_CHILD)
        }
        currentContext = ExecutionContextSnapshot.EMPTY
        assertThrows(IllegalArgumentException::class.java) {
            coordinator.admit(TenantEntity(), ManagedEntityAdmissionKind.AGGREGATE_ROOT)
        }
    }

    @Test
    fun `same entity cannot switch admission kind`() {
        val coordinator = coordinator(identifierBinding(allocate = { 1L }, validate = {}))
        val entity = IdentifiedEntity()
        coordinator.admit(entity, ManagedEntityAdmissionKind.OWNED_CHILD)

        assertThrows(IllegalStateException::class.java) {
            coordinator.admit(entity, ManagedEntityAdmissionKind.AGGREGATE_ROOT)
        }
    }

    @Test
    fun `failed admission does not mark the entity initialized and can be retried`() {
        var attempts = 0
        val coordinator = coordinator(
            identifierBinding(
                allocate = {
                    attempts++
                    check(attempts > 1) { "temporary allocation failure" }
                    7L
                },
                validate = {},
            )
        )
        val entity = IdentifiedEntity()

        assertThrows(IllegalStateException::class.java) {
            coordinator.admit(entity, ManagedEntityAdmissionKind.OWNED_CHILD)
        }
        coordinator.admit(entity, ManagedEntityAdmissionKind.OWNED_CHILD)

        assertEquals(7L, entity.id)
        assertEquals(2, attempts)
    }

    @Test
    fun `initializer validation receives handles that cannot repair managed state`() {
        val binding = ManagedFieldBinding(
            entityType = IdentifiedEntity::class,
            fieldName = "id",
            persistencePropertyName = "id",
            columnName = "id",
            targetType = Long::class,
            nullable = false,
            policyKey = "initialization.test",
            role = ManagedFieldRole.INITIALIZATION,
            explicitValue = ManagedExplicitValuePolicy.OVERWRITE,
            lifecycles = setOf(ManagedFieldLifecycle.ENTITY_ADMISSION),
            handlerQualifier = "initialization.test",
            handlerSlot = null,
            semanticValueType = Long::class,
            valueAdapterQualifier = null,
            persistence = PersistenceParticipation(
                ManagedValueAuthority.MANAGED_HANDLER,
                ManagedValueAuthority.NONE,
            ),
        )
        val initializer = object : ManagedEntityInitializer {
            override val qualifiers = setOf("initialization.test")

            override fun initialize(
                admission: ManagedEntityAdmissionKind,
                context: ManagedEntityInitializationContext,
                fields: ManagedEntityFieldSet,
            ) = Unit

            override fun validate(
                context: ManagedEntityInitializationContext,
                fields: ManagedEntityFieldSet,
            ) {
                fields.single().assignSemantic(9L)
            }
        }
        val registry = DefaultManagedFieldRegistry(
            catalogs = listOf(object : ManagedFieldCatalog {
                override val bindings = listOf(binding)
            }),
            initializers = listOf(initializer),
        )
        val coordinator = DefaultManagedEntityAdmissionCoordinator(
            registry,
            ExecutionContextAccessor { ExecutionContextSnapshot.EMPTY },
        )
        val entity = IdentifiedEntity()

        val failure = assertThrows(IllegalStateException::class.java) {
            coordinator.validate(entity, ExecutionContextSnapshot.EMPTY)
        }

        assertEquals(0L, entity.id)
        assertEquals(
            "managed field id[initialization.test] cannot be assigned during validation",
            failure.message,
        )
    }

    private fun coordinator(binding: ManagedFieldBinding): DefaultManagedEntityAdmissionCoordinator {
        val registry = DefaultManagedFieldRegistry(
            catalogs = listOf(catalog(binding)),
            initializers = listOf(StandardManagedEntityInitializer()),
        )
        return DefaultManagedEntityAdmissionCoordinator(
            registry,
            ExecutionContextAccessor { ExecutionContextSnapshot.EMPTY },
        )
    }

    private fun catalog(binding: ManagedFieldBinding): ManagedFieldCatalog = object : ManagedFieldCatalog {
        override val bindings = listOf(binding)
    }

    private fun tenantContext(tenantId: String): ExecutionContextSnapshot =
        ExecutionContextSnapshot.builder()
            .put(TenantContextKey, TenantContext(tenantId))
            .build()

    private fun identifierBinding(
        allocate: () -> Any,
        validate: (Any) -> Unit,
    ): ManagedFieldBinding = ManagedFieldBinding(
        entityType = IdentifiedEntity::class,
        fieldName = "id",
        persistencePropertyName = "id",
        columnName = "id",
        targetType = Long::class,
        nullable = false,
        policyKey = "identifier.uuid7",
        role = ManagedFieldRole.IDENTIFIER,
        explicitValue = ManagedExplicitValuePolicy.PRESERVE_IF_VALID,
        lifecycles = setOf(ManagedFieldLifecycle.ENTITY_ADMISSION),
        handlerQualifier = "identifier.uuid7",
        handlerSlot = null,
        semanticValueType = Long::class,
        valueAdapterQualifier = null,
        persistence = PersistenceParticipation(ManagedValueAuthority.FRAMEWORK, ManagedValueAuthority.NONE),
        runtimeSupport = ManagedFieldRuntimeSupport.ApplicationIdentifier(
            isAbsent = { it == null || it == 0L },
            allocateTarget = allocate,
            validateTarget = validate,
        ),
    )

    private open class IdentifiedEntity(
        var id: Long = 0,
    )

    private class DerivedIdentifiedEntity : IdentifiedEntity()

    private class DatabaseIdentityEntity(
        var id: Long? = null,
    )

    private class PrimitiveDatabaseIdentityEntity(
        var id: Long = 0L,
    )

    private class TenantEntity(
        var tenantId: String? = null,
    )

    private class SoftDeleteEntity(
        var deleted: Boolean? = null,
    )

    private data class TenantContext(
        val id: String,
    ) : ExecutionContextElement

    private object TenantContextInitializer : ManagedEntityInitializer {
        override val qualifiers = setOf("scope.tenant")

        override fun initialize(
            admission: ManagedEntityAdmissionKind,
            context: ManagedEntityInitializationContext,
            fields: ManagedEntityFieldSet,
        ) {
            val tenant = requireNotNull(context.executionContext[TenantContextKey]) {
                "tenant context is required"
            }
            fields.forEach { field ->
                if (field.readTarget() == null) {
                    field.assignSemantic(tenant.id)
                } else {
                    check(field.matchesSemantic(tenant.id)) { "tenant context does not match managed field" }
                }
            }
        }

        override fun validate(
            context: ManagedEntityInitializationContext,
            fields: ManagedEntityFieldSet,
        ) {
            val tenant = requireNotNull(context.executionContext[TenantContextKey]) {
                "tenant context is required"
            }
            fields.forEach { field ->
                check(field.matchesSemantic(tenant.id)) { "tenant context does not match managed field" }
            }
        }
    }

    private companion object {
        val TenantContextKey = ExecutionContextKey("tenant", TenantContext::class.java)
    }
}

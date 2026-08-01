package com.only4.cap4k.ddd.core.domain.managed

import com.only4.cap4k.ddd.core.application.context.ExecutionContextAccessor
import com.only4.cap4k.ddd.core.application.context.ExecutionContextSnapshot
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import kotlin.reflect.KClass

class ManagedFieldRegistryTest {
    @Test
    fun `reflective binding resolves a private field declared by a user superclass`() {
        val registry = registry(binding(InheritedEntity::class, "tenantId", String::class))
        val entity = InheritedEntity()

        val handle = registry.handles(
            entity,
            ManagedFieldLifecycle.ENTITY_ADMISSION,
            setOf(TEST_QUALIFIER),
        ).single()
        handle.assignSemantic("tenant-a")

        assertEquals("tenant-a", handle.readTarget())
    }

    @Test
    fun `ambiguous field shadowing fails instead of selecting the nearest declaration`() {
        val failure = assertThrows(IllegalArgumentException::class.java) {
            registry(binding(ShadowingEntity::class, "tenantId", String::class))
        }

        assertEquals(true, failure.message.orEmpty().contains("exactly one matching field declaration"))
    }

    @Test
    fun `exact custom accessor takes precedence over ambiguous field shadowing`() {
        var stored: String? = null
        val binding = binding(ShadowingEntity::class, "tenantId", String::class)
        val accessor = object : ManagedFieldAccessor {
            override val entityType = ShadowingEntity::class
            override val fieldName = "tenantId"
            override val policyKey = TEST_POLICY
            override val mutationFootprint = setOf("tenantId")
            override fun readRaw(entity: Any): Any? = stored
            override fun writeRaw(entity: Any, value: Any?) {
                stored = value as String
            }
        }
        val registry = DefaultManagedFieldRegistry(
            catalogs = listOf(catalog(binding)),
            initializers = listOf(NoOpInitializer),
            accessors = listOf(accessor),
        )

        val handle = registry.handles(
            ShadowingEntity(),
            ManagedFieldLifecycle.ENTITY_ADMISSION,
            setOf(TEST_QUALIFIER),
        ).single()
        handle.assignSemantic("tenant-a")

        assertEquals("tenant-a", handle.readTarget())
    }

    @Test
    fun `semantic conversion requires one exact compatible adapter`() {
        val binding = binding(
            entityType = EpochEntity::class,
            fieldName = "createdAt",
            targetType = Long::class,
            semanticType = java.time.Instant::class,
            adapterQualifier = "time.epoch-millis",
        )
        val registry = DefaultManagedFieldRegistry(
            catalogs = listOf(catalog(binding)),
            initializers = listOf(NoOpInitializer),
            adapters = listOf(EpochMillisAdapter),
        )
        val entity = EpochEntity()
        val handle = registry.handles(
            entity,
            ManagedFieldLifecycle.ENTITY_ADMISSION,
            setOf(TEST_QUALIFIER),
        ).single()

        handle.assignSemantic(java.time.Instant.ofEpochMilli(1234))

        assertEquals(1234L, entity.createdAt)
        assertEquals(true, handle.matchesSemantic(java.time.Instant.ofEpochMilli(1234)))
    }

    @Test
    fun `missing or duplicate semantic adapters fail registry assembly`() {
        val binding = binding(
            entityType = EpochEntity::class,
            fieldName = "createdAt",
            targetType = Long::class,
            semanticType = java.time.Instant::class,
            adapterQualifier = "time.epoch-millis",
        )

        val missing = assertThrows(IllegalArgumentException::class.java) {
            DefaultManagedFieldRegistry(
                catalogs = listOf(catalog(binding)),
                initializers = listOf(NoOpInitializer),
            )
        }
        val duplicate = assertThrows(IllegalArgumentException::class.java) {
            DefaultManagedFieldRegistry(
                catalogs = listOf(catalog(binding)),
                initializers = listOf(NoOpInitializer),
                adapters = listOf(EpochMillisAdapter, DuplicateEpochMillisAdapter),
            )
        }

        assertEquals(true, missing.message.orEmpty().contains("references missing adapter"))
        assertEquals(true, duplicate.message.orEmpty().contains("duplicate ManagedValueAdapter qualifier"))
    }

    @Test
    fun `incompatible semantic adapter fails registry assembly`() {
        val binding = binding(
            entityType = EpochEntity::class,
            fieldName = "createdAt",
            targetType = Long::class,
            semanticType = java.time.Instant::class,
            adapterQualifier = "time.epoch-millis",
        )

        val failure = assertThrows(IllegalArgumentException::class.java) {
            DefaultManagedFieldRegistry(
                catalogs = listOf(catalog(binding)),
                initializers = listOf(NoOpInitializer),
                adapters = listOf(IncompatibleEpochMillisAdapter),
            )
        }

        assertEquals(true, failure.message.orEmpty().contains("is incompatible"))
        assertEquals(true, failure.message.orEmpty().contains("java.time.Instant -> kotlin.Long"))
    }

    @Test
    fun `used admission qualifier without a runtime initializer fails registry assembly`() {
        val failure = assertThrows(IllegalArgumentException::class.java) {
            DefaultManagedFieldRegistry(
                catalogs = listOf(catalog(binding(InheritedEntity::class, "tenantId", String::class))),
            )
        }

        assertEquals(true, failure.message.orEmpty().contains("has no ManagedEntityInitializer"))
    }

    @Test
    fun `multiple fields for one qualifier require complete unique slots`() {
        val failure = assertThrows(IllegalArgumentException::class.java) {
            DefaultManagedFieldRegistry(
                catalogs = listOf(
                    catalog(
                        binding(AuditEntity::class, "createdAt", java.time.Instant::class, slot = "created-at"),
                        binding(AuditEntity::class, "updatedAt", java.time.Instant::class, slot = null),
                    )
                ),
                initializers = listOf(NoOpInitializer),
            )
        }

        assertEquals(true, failure.message.orEmpty().contains("must use slots for every field"))
    }

    @Test
    fun `custom accessor must declare a mutation footprint`() {
        val failure = assertThrows(IllegalArgumentException::class.java) {
            DefaultManagedFieldRegistry(
                catalogs = listOf(catalog(binding(InheritedEntity::class, "tenantId", String::class))),
                initializers = listOf(NoOpInitializer),
                accessors = listOf(
                    object : ManagedFieldAccessor {
                        override val entityType = InheritedEntity::class
                        override val fieldName = "tenantId"
                        override val policyKey = TEST_POLICY
                        override val mutationFootprint: Set<String> = emptySet()
                        override fun readRaw(entity: Any): Any? = null
                        override fun writeRaw(entity: Any, value: Any?) = Unit
                    }
                ),
            )
        }

        assertEquals(true, failure.message.orEmpty().contains("non-empty mutation footprint"))
    }

    private fun registry(vararg bindings: ManagedFieldBinding): DefaultManagedFieldRegistry =
        DefaultManagedFieldRegistry(
            catalogs = listOf(catalog(*bindings)),
            initializers = listOf(NoOpInitializer),
        )

    private fun catalog(vararg values: ManagedFieldBinding): ManagedFieldCatalog =
        object : ManagedFieldCatalog {
            override val bindings = values.toList()
        }

    private fun binding(
        entityType: KClass<*>,
        fieldName: String,
        targetType: KClass<*>,
        semanticType: KClass<*> = targetType,
        adapterQualifier: String? = null,
        slot: String? = null,
    ): ManagedFieldBinding = ManagedFieldBinding(
        entityType = entityType,
        fieldName = fieldName,
        persistencePropertyName = fieldName,
        columnName = fieldName,
        targetType = targetType,
        nullable = false,
        policyKey = TEST_POLICY,
        role = ManagedFieldRole.SCOPE,
        explicitValue = ManagedExplicitValuePolicy.REQUIRE_CONTEXT_MATCH,
        lifecycles = setOf(ManagedFieldLifecycle.ENTITY_ADMISSION),
        handlerQualifier = TEST_QUALIFIER,
        handlerSlot = slot,
        semanticValueType = semanticType,
        valueAdapterQualifier = adapterQualifier,
        persistence = PersistenceParticipation(ManagedValueAuthority.MANAGED_HANDLER, ManagedValueAuthority.NONE),
    )

    private open class InheritedBase {
        private lateinit var tenantId: String
    }

    private class InheritedEntity : InheritedBase()

    private open class ShadowedBase {
        @Suppress("unused")
        private lateinit var tenantId: String
    }

    private class ShadowingEntity : ShadowedBase() {
        @Suppress("unused")
        private lateinit var tenantId: String
    }

    private class EpochEntity {
        var createdAt: Long = 0
    }

    private class AuditEntity {
        lateinit var createdAt: java.time.Instant
        lateinit var updatedAt: java.time.Instant
    }

    private object NoOpInitializer : ManagedEntityInitializer {
        override val qualifiers = setOf(TEST_QUALIFIER)
        override fun initialize(
            admission: ManagedEntityAdmissionKind,
            context: ManagedEntityInitializationContext,
            fields: ManagedEntityFieldSet,
        ) = Unit

        override fun validate(
            context: ManagedEntityInitializationContext,
            fields: ManagedEntityFieldSet,
        ) = Unit
    }

    private object EpochMillisAdapter : ManagedValueAdapter {
        override val qualifier = "time.epoch-millis"
        override val sourceType = java.time.Instant::class
        override fun supports(targetType: KClass<*>): Boolean = targetType == Long::class
        override fun adapt(value: Any, targetType: KClass<*>): Any = (value as java.time.Instant).toEpochMilli()
    }

    private object DuplicateEpochMillisAdapter : ManagedValueAdapter {
        override val qualifier = EpochMillisAdapter.qualifier
        override val sourceType = EpochMillisAdapter.sourceType
        override fun supports(targetType: KClass<*>): Boolean = EpochMillisAdapter.supports(targetType)
        override fun adapt(value: Any, targetType: KClass<*>): Any = EpochMillisAdapter.adapt(value, targetType)
    }

    private object IncompatibleEpochMillisAdapter : ManagedValueAdapter {
        override val qualifier = EpochMillisAdapter.qualifier
        override val sourceType = String::class
        override fun supports(targetType: KClass<*>): Boolean = targetType == Long::class
        override fun adapt(value: Any, targetType: KClass<*>): Any = value
    }

    private companion object {
        const val TEST_POLICY = "scope.test"
        const val TEST_QUALIFIER = "scope.test"
    }
}

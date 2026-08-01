package com.only4.cap4k.plugin.pipeline.core

import com.only4.cap4k.plugin.pipeline.api.DbColumnSnapshot
import com.only4.cap4k.plugin.pipeline.api.DbTableSnapshot
import com.only4.cap4k.plugin.pipeline.api.EntityModel
import com.only4.cap4k.plugin.pipeline.api.FieldModel
import com.only4.cap4k.plugin.pipeline.api.ManagedCreationInputPolicy
import com.only4.cap4k.plugin.pipeline.api.ManagedExplicitValuePolicy
import com.only4.cap4k.plugin.pipeline.api.ManagedFieldDefaultsConfig
import com.only4.cap4k.plugin.pipeline.api.ManagedFieldLifecycle
import com.only4.cap4k.plugin.pipeline.api.ManagedFieldPolicyDefinition
import com.only4.cap4k.plugin.pipeline.api.ManagedFieldRole
import com.only4.cap4k.plugin.pipeline.api.ManagedPolicyDefinitionOwner
import com.only4.cap4k.plugin.pipeline.api.ManagedPolicySelectionProvenance
import com.only4.cap4k.plugin.pipeline.api.ManagedSemanticTypeRef
import com.only4.cap4k.plugin.pipeline.api.ManagedValueAuthority
import com.only4.cap4k.plugin.pipeline.api.OwnedManagedFieldPolicyDefinition
import com.only4.cap4k.plugin.pipeline.api.PersistenceParticipation
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ManagedFieldPolicyResolverTest {

    @Test
    fun `explicit annotation wins over exact column and identifier defaults`() {
        val (entity, table) = fixture(
            columns = listOf(
                column("id", "Long", primaryKey = true, policyKey = "identifier.assigned"),
                column("revision", "Long"),
            ),
        )

        val policy = ManagedFieldPolicyResolver.resolve(
            config = config(
                identifierDefault = "identifier.uuid7",
                columnDefaults = mapOf("id" to "identifier.snowflake", "revision" to "version"),
            ),
            entities = listOf(entity),
            tables = listOf(table),
            contributedDefinitions = emptyList(),
        ).single()

        val identifier = policy.requireIdentifier()
        assertEquals("identifier.assigned", identifier.policyKey)
        assertInstanceOf(ManagedPolicySelectionProvenance.ExplicitColumnAnnotation::class.java, identifier.selection)
        val version = policy.fields.single { it.fieldName == "revision" }
        assertEquals("version", version.policyKey)
        assertInstanceOf(ManagedPolicySelectionProvenance.ExactColumnDefault::class.java, version.selection)
        assertEquals(listOf("id"), policy.writeSurface.createAllowedFields)
        assertEquals(emptyList<String>(), policy.writeSurface.updateAllowedFields)
    }

    @Test
    fun `preserves extension definition ownership in canonical policy`() {
        val (entity, table) = fixture(
            columns = listOf(
                column("id", "Long", primaryKey = true),
                column("tenant_id", "String"),
            ),
        )
        val owner = ManagedPolicyDefinitionOwner.Extension("sample-extension", "tenant-policy")

        val policy = ManagedFieldPolicyResolver.resolve(
            config = config(columnDefaults = mapOf("tenant_id" to "scope.external")),
            entities = listOf(entity),
            tables = listOf(table),
            contributedDefinitions = listOf(
                OwnedManagedFieldPolicyDefinition(
                    definition = definition(
                        key = "scope.external",
                        role = ManagedFieldRole.SCOPE,
                        lifecycle = ManagedFieldLifecycle.ENTITY_ADMISSION,
                        qualifier = "scope.external",
                        insert = ManagedValueAuthority.MANAGED_HANDLER,
                    ),
                    owner = owner,
                )
            ),
        ).single()

        val tenant = policy.fields.single { it.fieldName == "tenantId" }
        assertEquals(owner, tenant.definitionOwner)
        assertEquals("scope.external", tenant.handlerQualifier)
        assertEquals("String", tenant.semanticValueType)
    }

    @Test
    fun `rejects unresolved selected custom policy with selection provenance`() {
        val (entity, table) = fixture(
            columns = listOf(column("id", "Long", primaryKey = true, policyKey = "identifier.external")),
        )

        val error = assertThrows(IllegalArgumentException::class.java) {
            ManagedFieldPolicyResolver.resolve(config(), listOf(entity), listOf(table), emptyList())
        }

        assertTrue(error.message!!.contains("unresolved managed field policy identifier.external"), error.message)
        assertTrue(error.message!!.contains("sample.id#comment:@Managed"), error.message)
    }

    @Test
    fun `rejects duplicate contributed key against built in owner`() {
        val (entity, table) = fixture(listOf(column("id", "Long", primaryKey = true)))
        val owner = ManagedPolicyDefinitionOwner.Extension("sample-extension", "duplicate")
        val duplicate = OwnedManagedFieldPolicyDefinition(
            definition = definition(
                key = "scope.tenant",
                role = ManagedFieldRole.SCOPE,
                lifecycle = ManagedFieldLifecycle.ENTITY_ADMISSION,
                qualifier = "scope.tenant",
                insert = ManagedValueAuthority.MANAGED_HANDLER,
            ),
            owner = owner,
        )

        val error = assertThrows(IllegalArgumentException::class.java) {
            ManagedFieldPolicyResolver.resolve(config(), listOf(entity), listOf(table), listOf(duplicate))
        }

        assertEquals(
            "duplicate managed field policy definition scope.tenant: built-in, extension sample-extension/duplicate",
            error.message,
        )
    }

    @Test
    fun `requires adapter when semantic and target field types differ`() {
        val (entity, table) = fixture(
            listOf(
                column("id", "Long", primaryKey = true),
                column("audit_time", "String"),
            )
        )
        val external = OwnedManagedFieldPolicyDefinition(
            definition = definition(
                key = "enrichment.external-time",
                role = ManagedFieldRole.ENRICHMENT,
                lifecycle = ManagedFieldLifecycle.PERSISTENCE_ENRICHMENT,
                qualifier = "enrichment.external-time",
                insert = ManagedValueAuthority.MANAGED_HANDLER,
                semanticType = ManagedSemanticTypeRef.FixedFqn("java.time.Instant"),
            ),
            owner = ManagedPolicyDefinitionOwner.Extension("sample-extension", "external-time"),
        )

        val error = assertThrows(IllegalArgumentException::class.java) {
            ManagedFieldPolicyResolver.resolve(
                config(columnDefaults = mapOf("audit_time" to "enrichment.external-time")),
                listOf(entity),
                listOf(table),
                listOf(external),
            )
        }

        assertTrue(error.message!!.contains("valueAdapterQualifier is required"), error.message)
    }

    @Test
    fun `audit time keeps Instant semantics and requires its explicit adapter for LocalDateTime storage`() {
        val (entity, table) = fixture(
            listOf(
                column("id", "Long", primaryKey = true),
                column(
                    "created_at",
                    "java.time.LocalDateTime",
                    policyKey = "enrichment.audit-time.created-at",
                ),
            )
        )

        val createdAt = ManagedFieldPolicyResolver.resolve(
            config(),
            listOf(entity),
            listOf(table),
            emptyList(),
        ).single().fields.single { it.fieldName == "createdAt" }

        assertEquals("java.time.LocalDateTime", createdAt.fieldType)
        assertEquals("java.time.Instant", createdAt.semanticValueType)
        assertEquals("enrichment.audit-time", createdAt.valueAdapterQualifier)
    }

    @Test
    fun `same short class name with different fqns still requires an adapter`() {
        val (rawEntity, table) = fixture(
            listOf(
                column("id", "Long", primaryKey = true),
                column("actor", "Actor", policyKey = "enrichment.external-actor"),
            )
        )
        val entity = rawEntity.copy(
            fields = rawEntity.fields.map { field ->
                if (field.name == "actor") field.copy(typeBinding = "com.acme.persistence.Actor") else field
            }
        )
        val external = OwnedManagedFieldPolicyDefinition(
            definition = definition(
                key = "enrichment.external-actor",
                role = ManagedFieldRole.ENRICHMENT,
                lifecycle = ManagedFieldLifecycle.PERSISTENCE_ENRICHMENT,
                qualifier = "enrichment.external-actor",
                insert = ManagedValueAuthority.MANAGED_HANDLER,
                semanticType = ManagedSemanticTypeRef.FixedFqn("com.acme.semantic.Actor"),
            ),
            owner = ManagedPolicyDefinitionOwner.Extension("sample-extension", "external-actor"),
        )

        val error = assertThrows(IllegalArgumentException::class.java) {
            ManagedFieldPolicyResolver.resolve(config(), listOf(entity), listOf(table), listOf(external))
        }

        assertTrue(error.message!!.contains("semantic type com.acme.semantic.Actor"), error.message)
        assertTrue(error.message!!.contains("type com.acme.persistence.Actor"), error.message)
        assertTrue(error.message!!.contains("valueAdapterQualifier is required"), error.message)
    }

    @Test
    fun `requires slots when one qualifier handles multiple fields`() {
        val (entity, table) = fixture(
            listOf(
                column("id", "Long", primaryKey = true),
                column("created_by", "String"),
                column("updated_by", "String"),
            )
        )
        val owner = ManagedPolicyDefinitionOwner.Extension("sample-extension", "actor")
        val definitions = listOf("created", "updated").map { suffix ->
            OwnedManagedFieldPolicyDefinition(
                definition(
                    key = "enrichment.external-actor.$suffix",
                    role = ManagedFieldRole.ENRICHMENT,
                    lifecycle = ManagedFieldLifecycle.PERSISTENCE_ENRICHMENT,
                    qualifier = "enrichment.external-actor",
                    insert = ManagedValueAuthority.MANAGED_HANDLER,
                ),
                owner,
            )
        }

        val error = assertThrows(IllegalArgumentException::class.java) {
            ManagedFieldPolicyResolver.resolve(
                config(
                    columnDefaults = mapOf(
                        "created_by" to "enrichment.external-actor.created",
                        "updated_by" to "enrichment.external-actor.updated",
                    )
                ),
                listOf(entity),
                listOf(table),
                definitions,
            )
        }

        assertTrue(error.message!!.contains("every field must declare a nonblank handler slot"), error.message)
    }

    @Test
    fun `rejects creation input that is not visible to application insert authority`() {
        val (entity, table) = fixture(
            listOf(
                column("id", "Long", primaryKey = true),
                column("external_value", "String"),
            )
        )
        val definition = ManagedFieldPolicyDefinition(
            key = "database.external-value",
            role = ManagedFieldRole.DATABASE_GENERATED,
            creationInput = ManagedCreationInputPolicy.REQUIRED,
            explicitValue = ManagedExplicitValuePolicy.REQUIRE,
            lifecycles = setOf(ManagedFieldLifecycle.DATABASE),
            persistence = PersistenceParticipation(
                ManagedValueAuthority.DATABASE,
                ManagedValueAuthority.NONE,
            ),
        )

        val error = assertThrows(IllegalArgumentException::class.java) {
            ManagedFieldPolicyResolver.resolve(
                config(columnDefaults = mapOf("external_value" to definition.key)),
                listOf(entity),
                listOf(table),
                listOf(
                    OwnedManagedFieldPolicyDefinition(
                        definition,
                        ManagedPolicyDefinitionOwner.Extension("sample-extension", "external-value"),
                    )
                ),
            )
        }

        assertTrue(error.message!!.contains("application-visible INSERT authority"), error.message)
        assertTrue(error.message!!.contains("DATABASE"), error.message)
    }

    @Test
    fun `rejects optional creation input that still requires an explicit value`() {
        val (entity, table) = fixture(
            listOf(
                column("id", "Long", primaryKey = true),
                column("external_value", "String"),
            )
        )
        val definition = ManagedFieldPolicyDefinition(
            key = "initialization.external-value",
            role = ManagedFieldRole.INITIALIZATION,
            creationInput = ManagedCreationInputPolicy.OPTIONAL,
            explicitValue = ManagedExplicitValuePolicy.REQUIRE,
            lifecycles = setOf(ManagedFieldLifecycle.ENTITY_ADMISSION),
            handlerQualifier = "initialization.external-value",
            persistence = PersistenceParticipation(
                ManagedValueAuthority.MANAGED_HANDLER,
                ManagedValueAuthority.NONE,
            ),
        )

        val error = assertThrows(IllegalArgumentException::class.java) {
            ManagedFieldPolicyResolver.resolve(
                config(columnDefaults = mapOf("external_value" to definition.key)),
                listOf(entity),
                listOf(table),
                listOf(
                    OwnedManagedFieldPolicyDefinition(
                        definition,
                        ManagedPolicyDefinitionOwner.Extension("sample-extension", "external-value"),
                    )
                ),
            )
        }

        assertTrue(error.message!!.contains("OPTIONAL creation input"), error.message)
    }

    private fun config(
        identifierDefault: String = "identifier.assigned",
        columnDefaults: Map<String, String> = emptyMap(),
    ) = ProjectConfig(
        managedFields = ManagedFieldDefaultsConfig(
            identifierDefaultPolicy = identifierDefault,
            columnPolicyDefaults = columnDefaults,
        )
    )

    private fun fixture(columns: List<DbColumnSnapshot>): Pair<EntityModel, DbTableSnapshot> {
        val fields = columns.map { column ->
            FieldModel(
                name = when (column.name) {
                    "tenant_id" -> "tenantId"
                    "audit_time" -> "auditTime"
                    "created_at" -> "createdAt"
                    "created_by" -> "createdBy"
                    "updated_by" -> "updatedBy"
                    "external_value" -> "externalValue"
                    else -> column.name
                },
                type = column.kotlinType,
                nullable = column.nullable,
                columnName = column.name,
            )
        }
        val id = fields.single { it.columnName == "id" }
        return EntityModel(
            name = "Sample",
            packageName = "com.acme",
            tableName = "sample",
            comment = "sample",
            fields = fields,
            idField = id,
        ) to DbTableSnapshot(
            tableName = "sample",
            comment = "sample",
            columns = columns,
            primaryKey = listOf("id"),
            uniqueConstraints = emptyList(),
        )
    }

    private fun column(
        name: String,
        kotlinType: String,
        primaryKey: Boolean = false,
        policyKey: String? = null,
    ) = DbColumnSnapshot(
        name = name,
        dbType = kotlinType,
        kotlinType = kotlinType,
        nullable = false,
        isPrimaryKey = primaryKey,
        managedPolicyKey = policyKey,
    )

    private fun definition(
        key: String,
        role: ManagedFieldRole,
        lifecycle: ManagedFieldLifecycle,
        qualifier: String,
        insert: ManagedValueAuthority,
        semanticType: ManagedSemanticTypeRef = ManagedSemanticTypeRef.TargetField,
    ) = ManagedFieldPolicyDefinition(
        key = key,
        role = role,
        creationInput = ManagedCreationInputPolicy.OMIT,
        explicitValue = ManagedExplicitValuePolicy.OVERWRITE,
        lifecycles = setOf(lifecycle),
        handlerQualifier = qualifier,
        semanticValueType = semanticType,
        persistence = PersistenceParticipation(insert, ManagedValueAuthority.NONE),
    )
}

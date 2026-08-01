package com.only4.cap4k.plugin.pipeline.generator.aggregate

import com.only4.cap4k.plugin.pipeline.api.AggregateIdPolicyControl
import com.only4.cap4k.plugin.pipeline.api.AggregateIdPolicyKind
import com.only4.cap4k.plugin.pipeline.api.AggregatePersistenceProviderControl
import com.only4.cap4k.plugin.pipeline.api.CanonicalModel
import com.only4.cap4k.plugin.pipeline.api.EntityModel
import com.only4.cap4k.plugin.pipeline.api.FieldModel
import com.only4.cap4k.plugin.pipeline.api.ManagedCreationInputPolicy
import com.only4.cap4k.plugin.pipeline.api.ManagedExplicitValuePolicy
import com.only4.cap4k.plugin.pipeline.api.ManagedFieldLifecycle
import com.only4.cap4k.plugin.pipeline.api.ManagedFieldRole
import com.only4.cap4k.plugin.pipeline.api.ManagedPolicyDefinitionOwner
import com.only4.cap4k.plugin.pipeline.api.ManagedPolicySelectionProvenance
import com.only4.cap4k.plugin.pipeline.api.ManagedValueAuthority
import com.only4.cap4k.plugin.pipeline.api.PersistenceParticipation
import com.only4.cap4k.plugin.pipeline.api.ResolvedManagedEntityPolicy
import com.only4.cap4k.plugin.pipeline.api.ResolvedManagedFieldPolicy
import com.only4.cap4k.plugin.pipeline.api.ResolvedWriteSurfacePolicy
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AggregateEntrustedFieldPlanningTest {

    @Test
    fun `classifies database identity and version as provider assigned fields`() {
        val entity = entity()

        val fields = AggregateEntrustedFieldPlanning.resolve(
            entity,
            model(
                entity,
                policy(entity, identifierKey = "identifier.database-identity", versionFieldName = "revision"),
                idControl(entity),
                providerControl(entity, versionFieldName = "revision"),
            ),
        )

        assertEquals("id", fields.databaseIdentityFieldName)
        assertEquals("revision", fields.versionFieldName)
        assertTrue(fields.isProviderAssigned("id"))
        assertTrue(fields.isProviderAssigned("revision"))
        assertFalse(fields.isProviderAssigned("name"))
    }

    @Test
    fun `does not classify application identifier or generic managed field`() {
        val entity = entity(idType = "ArticleId")
        val resolved = policy(entity, identifierKey = "identifier.uuid7").copy(
            fields = policy(entity, identifierKey = "identifier.uuid7").fields +
                managedField(
                    fieldName = "auditStamp",
                    columnName = "audit_stamp",
                    fieldType = "String",
                    policyKey = "enrichment.audit-actor.created-by",
                    role = ManagedFieldRole.ENRICHMENT,
                    insert = ManagedValueAuthority.MANAGED_HANDLER,
                ),
        )

        val fields = AggregateEntrustedFieldPlanning.resolve(
            entity,
            model(
                entity,
                resolved,
                idControl(entity, kind = AggregateIdPolicyKind.APPLICATION_SIDE),
            ),
        )

        assertNull(fields.databaseIdentityFieldName)
        assertFalse(fields.isProviderAssigned("auditStamp"))
    }

    @Test
    fun `does not infer version from a conventional field name`() {
        val entity = entity()

        val fields = AggregateEntrustedFieldPlanning.resolve(
            entity,
            model(
                entity,
                policy(entity, identifierKey = "identifier.uuid7"),
                idControl(entity, kind = AggregateIdPolicyKind.APPLICATION_SIDE),
                providerControl(entity, versionFieldName = "version"),
            ),
        )

        assertFalse(fields.isVersion("version"))
    }

    @Test
    fun `rejects database identity ID control field mismatch`() {
        val entity = entity()

        val error = assertThrows(IllegalArgumentException::class.java) {
            AggregateEntrustedFieldPlanning.resolve(
                entity,
                model(
                    entity,
                    policy(entity, identifierKey = "identifier.database-identity"),
                    idControl(entity, idFieldName = "legacyId"),
                ),
            )
        }

        assertEquals("resolved database identity projection mismatch for com.acme.demo.Article.id", error.message)
    }

    @Test
    fun `rejects database identity application side ID control`() {
        val entity = entity()

        val error = assertThrows(IllegalArgumentException::class.java) {
            AggregateEntrustedFieldPlanning.resolve(
                entity,
                model(
                    entity,
                    policy(entity, identifierKey = "identifier.database-identity"),
                    idControl(entity, kind = AggregateIdPolicyKind.APPLICATION_SIDE),
                ),
            )
        }

        assertEquals("resolved database identity projection mismatch for com.acme.demo.Article.id", error.message)
    }

    @Test
    fun `rejects provider version projection mismatch`() {
        val entity = entity()

        val error = assertThrows(IllegalArgumentException::class.java) {
            AggregateEntrustedFieldPlanning.resolve(
                entity,
                model(
                    entity,
                    policy(entity, identifierKey = "identifier.uuid7", versionFieldName = "revision"),
                    idControl(entity, kind = AggregateIdPolicyKind.APPLICATION_SIDE),
                    providerControl(entity, versionFieldName = "version"),
                ),
            )
        }

        assertEquals(
            "resolved version projection mismatch for com.acme.demo.Article: resolved=revision, provider=version",
            error.message,
        )
    }

    @Test
    fun `returns no provider assigned fields without a managed policy`() {
        val entity = entity()

        val fields = AggregateEntrustedFieldPlanning.resolve(
            entity,
            CanonicalModel(
                entities = listOf(entity),
                aggregateIdPolicyControls = listOf(idControl(entity)),
                aggregatePersistenceProviderControls = listOf(providerControl(entity, versionFieldName = "version")),
            ),
        )

        assertNull(fields.databaseIdentityFieldName)
        assertNull(fields.versionFieldName)
    }

    private fun model(
        entity: EntityModel,
        resolvedPolicy: ResolvedManagedEntityPolicy,
        idControl: AggregateIdPolicyControl? = null,
        providerControl: AggregatePersistenceProviderControl? = null,
    ) = CanonicalModel(
        entities = listOf(entity),
        managedFieldPolicies = listOf(resolvedPolicy),
        aggregateIdPolicyControls = listOfNotNull(idControl),
        aggregatePersistenceProviderControls = listOfNotNull(providerControl),
    )

    private fun entity(idType: String = "Long"): EntityModel {
        val id = FieldModel(name = "id", type = idType, columnName = "id")
        return EntityModel(
            name = "Article",
            packageName = "com.acme.demo",
            tableName = "article",
            comment = "article",
            fields = listOf(
                id,
                FieldModel(name = "name", type = "String", columnName = "name"),
                FieldModel(name = "revision", type = "Int", columnName = "revision"),
                FieldModel(name = "version", type = "Int", columnName = "version"),
                FieldModel(name = "auditStamp", type = "String", columnName = "audit_stamp"),
            ),
            idField = id,
        )
    }

    private fun policy(
        entity: EntityModel,
        identifierKey: String,
        versionFieldName: String? = null,
    ) = ResolvedManagedEntityPolicy(
        entityName = entity.name,
        entityPackageName = entity.packageName,
        tableName = entity.tableName,
        fields = listOfNotNull(
            managedField(
                fieldName = entity.idField.name,
                columnName = entity.idField.columnName!!,
                fieldType = entity.idField.type,
                policyKey = identifierKey,
                role = ManagedFieldRole.IDENTIFIER,
                insert = if (identifierKey == "identifier.database-identity") {
                    ManagedValueAuthority.DATABASE
                } else {
                    ManagedValueAuthority.FRAMEWORK
                },
            ),
            versionFieldName?.let {
                managedField(
                    fieldName = it,
                    columnName = it,
                    fieldType = "Int",
                    policyKey = "version",
                    role = ManagedFieldRole.VERSION,
                    insert = ManagedValueAuthority.PERSISTENCE_PROVIDER,
                    update = ManagedValueAuthority.PERSISTENCE_PROVIDER,
                )
            },
        ),
        writeSurface = ResolvedWriteSurfacePolicy(),
    )

    private fun managedField(
        fieldName: String,
        columnName: String,
        fieldType: String,
        policyKey: String,
        role: ManagedFieldRole,
        insert: ManagedValueAuthority,
        update: ManagedValueAuthority = ManagedValueAuthority.NONE,
    ) = ResolvedManagedFieldPolicy(
        fieldName = fieldName,
        columnName = columnName,
        fieldType = fieldType,
        nullable = false,
        selection = ManagedPolicySelectionProvenance.ExplicitColumnAnnotation("test"),
        definitionOwner = ManagedPolicyDefinitionOwner.BuiltIn,
        policyKey = policyKey,
        role = role,
        creationInput = ManagedCreationInputPolicy.OMIT,
        explicitValue = ManagedExplicitValuePolicy.FORBID,
        lifecycles = emptySet<ManagedFieldLifecycle>(),
        handlerQualifier = null,
        handlerSlot = null,
        semanticValueType = fieldType,
        valueAdapterQualifier = null,
        persistence = PersistenceParticipation(insert = insert, update = update),
    )

    private fun idControl(
        entity: EntityModel,
        idFieldName: String = entity.idField.name,
        kind: AggregateIdPolicyKind = AggregateIdPolicyKind.DATABASE_SIDE,
    ) = AggregateIdPolicyControl(
        entityName = entity.name,
        entityPackageName = entity.packageName,
        tableName = entity.tableName,
        idFieldName = idFieldName,
        idFieldType = entity.idField.type,
        strategy = if (kind == AggregateIdPolicyKind.DATABASE_SIDE) "database-identity" else "uuid7",
        kind = kind,
    )

    private fun providerControl(entity: EntityModel, versionFieldName: String? = null) =
        AggregatePersistenceProviderControl(
            entityName = entity.name,
            entityPackageName = entity.packageName,
            tableName = entity.tableName,
            idFieldName = entity.idField.name,
            versionFieldName = versionFieldName,
        )
}

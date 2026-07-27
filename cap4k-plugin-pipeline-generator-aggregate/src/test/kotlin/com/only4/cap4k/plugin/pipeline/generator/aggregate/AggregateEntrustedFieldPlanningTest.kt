package com.only4.cap4k.plugin.pipeline.generator.aggregate

import com.only4.cap4k.plugin.pipeline.api.AggregateIdPolicyControl
import com.only4.cap4k.plugin.pipeline.api.AggregateIdPolicyKind
import com.only4.cap4k.plugin.pipeline.api.AggregatePersistenceFieldControl
import com.only4.cap4k.plugin.pipeline.api.AggregatePersistenceProviderControl
import com.only4.cap4k.plugin.pipeline.api.AggregateSpecialFieldResolvedPolicy
import com.only4.cap4k.plugin.pipeline.api.CanonicalModel
import com.only4.cap4k.plugin.pipeline.api.DbManagedRole
import com.only4.cap4k.plugin.pipeline.api.EntityModel
import com.only4.cap4k.plugin.pipeline.api.FieldModel
import com.only4.cap4k.plugin.pipeline.api.ResolvedIdPolicy
import com.only4.cap4k.plugin.pipeline.api.ResolvedManagedFieldPolicy
import com.only4.cap4k.plugin.pipeline.api.ResolvedMarkerPolicy
import com.only4.cap4k.plugin.pipeline.api.SpecialFieldSource
import com.only4.cap4k.plugin.pipeline.api.SpecialFieldWritePolicy
import com.only4.cap4k.plugin.pipeline.api.StrongIdKind
import com.only4.cap4k.plugin.pipeline.api.StrongIdModel
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AggregateEntrustedFieldPlanningTest {

    @Test
    fun `classifies database identity and explicit version as the only provider assigned fields`() {
        val entity = entity()

        val fields = AggregateEntrustedFieldPlanning.resolve(
            entity = entity,
            model = model(
                entity = entity,
                resolvedPolicy = resolvedPolicy(entity = entity, versionFieldName = "revision"),
                idControl = idControl(entity),
                providerControl = providerControl(entity, versionFieldName = "revision"),
            ),
        )

        assertEquals("id", fields.databaseIdentityFieldName)
        assertEquals("revision", fields.versionFieldName)
        assertTrue(fields.isProviderAssigned("id"))
        assertTrue(fields.isProviderAssigned("revision"))
        assertFalse(fields.isProviderAssigned("name"))
    }

    @Test
    fun `classifies DSL default version without persistence field version marker`() {
        val entity = entity()

        val fields = AggregateEntrustedFieldPlanning.resolve(
            entity = entity,
            model = model(
                entity = entity,
                resolvedPolicy = resolvedPolicy(
                    entity = entity,
                    versionFieldName = "revision",
                    versionSource = SpecialFieldSource.DSL_DEFAULT,
                ),
                idControl = idControl(entity),
                providerControl = providerControl(entity, versionFieldName = "revision"),
                fieldControls = listOf(
                    AggregatePersistenceFieldControl(
                        entityName = entity.name,
                        entityPackageName = entity.packageName,
                        fieldName = "revision",
                        columnName = "revision",
                        version = false,
                    )
                ),
            ),
        )

        assertTrue(fields.isVersion("revision"))
    }

    @Test
    fun `does not classify application side Strong ID but still classifies resolved version`() {
        val entity = entity(idType = "ArticleId")

        val fields = AggregateEntrustedFieldPlanning.resolve(
            entity = entity,
            model = model(
                entity = entity,
                resolvedPolicy = resolvedPolicy(
                    entity = entity,
                    idKind = AggregateIdPolicyKind.APPLICATION_SIDE,
                    idWritePolicy = SpecialFieldWritePolicy.CREATE_ONLY,
                    versionFieldName = "revision",
                ),
                idControl = idControl(entity, kind = AggregateIdPolicyKind.APPLICATION_SIDE),
                providerControl = providerControl(entity, versionFieldName = "revision"),
                strongIds = listOf(
                    StrongIdModel(
                        typeName = "ArticleId",
                        packageName = entity.packageName,
                        kind = StrongIdKind.OWN_ID,
                        ownerEntityName = entity.name,
                        ownerEntityPackageName = entity.packageName,
                    )
                ),
            ),
        )

        assertNull(fields.databaseIdentityFieldName)
        assertTrue(fields.isVersion("revision"))
    }

    @Test
    fun `does not grant roles to generic managed read only fields`() {
        val entity = entity()
        val policy = resolvedPolicy(
            entity = entity,
            idKind = AggregateIdPolicyKind.APPLICATION_SIDE,
            idWritePolicy = SpecialFieldWritePolicy.CREATE_ONLY,
        ).copy(
            managedFields = listOf(
                ResolvedManagedFieldPolicy(
                    fieldName = "auditStamp",
                    columnName = "audit_stamp",
                    writePolicy = SpecialFieldWritePolicy.READ_ONLY,
                    source = SpecialFieldSource.DB_EXPLICIT,
                    managedRole = DbManagedRole.SYSTEM,
                )
            )
        )

        val fields = AggregateEntrustedFieldPlanning.resolve(
            entity = entity,
            model = model(entity = entity, resolvedPolicy = policy),
        )

        assertFalse(fields.isProviderAssigned("auditStamp"))
    }

    @Test
    fun `does not classify an unmarked conventional version field`() {
        val entity = entity()

        val fields = AggregateEntrustedFieldPlanning.resolve(
            entity = entity,
            model = model(
                entity = entity,
                resolvedPolicy = resolvedPolicy(
                    entity = entity,
                    idKind = AggregateIdPolicyKind.APPLICATION_SIDE,
                    idWritePolicy = SpecialFieldWritePolicy.CREATE_ONLY,
                ),
                providerControl = providerControl(entity, versionFieldName = "version"),
            ),
        )

        assertFalse(fields.isVersion("version"))
    }

    @Test
    fun `rejects non read only database identity`() {
        val entity = entity()
        val error = assertThrows(IllegalArgumentException::class.java) {
            AggregateEntrustedFieldPlanning.resolve(
                entity = entity,
                model = model(
                    entity = entity,
                    resolvedPolicy = resolvedPolicy(
                        entity = entity,
                        idWritePolicy = SpecialFieldWritePolicy.CREATE_ONLY,
                    ),
                    idControl = idControl(entity),
                ),
            )
        }

        assertEquals(
            "resolved database identity projection mismatch for com.acme.demo.Article.id",
            error.message,
        )
    }

    @Test
    fun `rejects non read only resolved version`() {
        val entity = entity()
        val error = assertThrows(IllegalArgumentException::class.java) {
            AggregateEntrustedFieldPlanning.resolve(
                entity = entity,
                model = model(
                    entity = entity,
                    resolvedPolicy = resolvedPolicy(
                        entity = entity,
                        idKind = AggregateIdPolicyKind.APPLICATION_SIDE,
                        idWritePolicy = SpecialFieldWritePolicy.CREATE_ONLY,
                        versionFieldName = "revision",
                        versionWritePolicy = SpecialFieldWritePolicy.CREATE_ONLY,
                    ),
                    providerControl = providerControl(entity, versionFieldName = "revision"),
                ),
            )
        }

        assertEquals(
            "resolved version projection mismatch for com.acme.demo.Article: resolved=revision, provider=revision",
            error.message,
        )
    }

    @Test
    fun `rejects provider version projection mismatch`() {
        val entity = entity()
        val error = assertThrows(IllegalArgumentException::class.java) {
            AggregateEntrustedFieldPlanning.resolve(
                entity = entity,
                model = model(
                    entity = entity,
                    resolvedPolicy = resolvedPolicy(
                        entity = entity,
                        idKind = AggregateIdPolicyKind.APPLICATION_SIDE,
                        idWritePolicy = SpecialFieldWritePolicy.CREATE_ONLY,
                        versionFieldName = "revision",
                    ),
                    providerControl = providerControl(entity, versionFieldName = "version"),
                ),
            )
        }

        assertEquals(
            "resolved version projection mismatch for com.acme.demo.Article: resolved=revision, provider=version",
            error.message,
        )
    }

    @Test
    fun `rejects database identity ID control mismatch`() {
        val entity = entity()
        val error = assertThrows(IllegalArgumentException::class.java) {
            AggregateEntrustedFieldPlanning.resolve(
                entity = entity,
                model = model(
                    entity = entity,
                    resolvedPolicy = resolvedPolicy(entity = entity),
                    idControl = idControl(entity, idFieldName = "legacyId"),
                ),
            )
        }

        assertEquals(
            "resolved database identity projection mismatch for com.acme.demo.Article.id",
            error.message,
        )
    }

    @Test
    fun `returns no roles for projection only model without resolved policy`() {
        val entity = entity()

        val fields = AggregateEntrustedFieldPlanning.resolve(
            entity = entity,
            model = CanonicalModel(
                entities = listOf(entity),
                aggregateIdPolicyControls = listOf(idControl(entity)),
                aggregatePersistenceProviderControls = listOf(providerControl(entity, versionFieldName = "version")),
                aggregatePersistenceFieldControls = listOf(
                    AggregatePersistenceFieldControl(
                        entityName = entity.name,
                        entityPackageName = entity.packageName,
                        fieldName = "version",
                        columnName = "version",
                        version = true,
                    )
                ),
            ),
        )

        assertNull(fields.databaseIdentityFieldName)
        assertNull(fields.versionFieldName)
    }

    private fun model(
        entity: EntityModel,
        resolvedPolicy: AggregateSpecialFieldResolvedPolicy,
        idControl: AggregateIdPolicyControl? = null,
        providerControl: AggregatePersistenceProviderControl? = null,
        fieldControls: List<AggregatePersistenceFieldControl> = emptyList(),
        strongIds: List<StrongIdModel> = emptyList(),
    ): CanonicalModel =
        CanonicalModel(
            entities = listOf(entity),
            aggregateSpecialFieldResolvedPolicies = listOf(resolvedPolicy),
            aggregateIdPolicyControls = listOfNotNull(idControl),
            aggregatePersistenceProviderControls = listOfNotNull(providerControl),
            aggregatePersistenceFieldControls = fieldControls,
            strongIds = strongIds,
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
                FieldModel(
                    name = "auditStamp",
                    type = "String",
                    columnName = "audit_stamp",
                    managedRole = DbManagedRole.VERSION,
                ),
            ),
            idField = id,
        )
    }

    private fun resolvedPolicy(
        entity: EntityModel,
        idKind: AggregateIdPolicyKind = AggregateIdPolicyKind.DATABASE_SIDE,
        idWritePolicy: SpecialFieldWritePolicy = SpecialFieldWritePolicy.READ_ONLY,
        versionFieldName: String? = null,
        versionSource: SpecialFieldSource = SpecialFieldSource.DB_EXPLICIT,
        versionWritePolicy: SpecialFieldWritePolicy = SpecialFieldWritePolicy.READ_ONLY,
    ): AggregateSpecialFieldResolvedPolicy =
        AggregateSpecialFieldResolvedPolicy(
            entityName = entity.name,
            entityPackageName = entity.packageName,
            tableName = entity.tableName,
            id = ResolvedIdPolicy(
                fieldName = entity.idField.name,
                columnName = entity.idField.columnName!!,
                strategy = if (idKind == AggregateIdPolicyKind.DATABASE_SIDE) "identity" else "uuid7",
                kind = idKind,
                source = SpecialFieldSource.DB_EXPLICIT,
                writePolicy = idWritePolicy,
            ),
            deleted = ResolvedMarkerPolicy(
                enabled = false,
                source = SpecialFieldSource.NONE,
            ),
            version = ResolvedMarkerPolicy(
                enabled = versionFieldName != null,
                fieldName = versionFieldName,
                columnName = versionFieldName,
                source = if (versionFieldName == null) SpecialFieldSource.NONE else versionSource,
                writePolicy = versionWritePolicy,
            ),
        )

    private fun idControl(
        entity: EntityModel,
        idFieldName: String = entity.idField.name,
        kind: AggregateIdPolicyKind = AggregateIdPolicyKind.DATABASE_SIDE,
    ): AggregateIdPolicyControl =
        AggregateIdPolicyControl(
            entityName = entity.name,
            entityPackageName = entity.packageName,
            tableName = entity.tableName,
            idFieldName = idFieldName,
            idFieldType = entity.idField.type,
            strategy = if (kind == AggregateIdPolicyKind.DATABASE_SIDE) "identity" else "uuid7",
            kind = kind,
        )

    private fun providerControl(
        entity: EntityModel,
        versionFieldName: String? = null,
    ): AggregatePersistenceProviderControl =
        AggregatePersistenceProviderControl(
            entityName = entity.name,
            entityPackageName = entity.packageName,
            tableName = entity.tableName,
            idFieldName = entity.idField.name,
            versionFieldName = versionFieldName,
        )
}

package com.only4.cap4k.plugin.pipeline.generator.aggregate

import com.only4.cap4k.plugin.pipeline.api.ArtifactOutputKind
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
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig
import com.only4.cap4k.plugin.pipeline.api.ResolvedManagedEntityPolicy
import com.only4.cap4k.plugin.pipeline.api.ResolvedManagedFieldPolicy
import com.only4.cap4k.plugin.pipeline.api.ResolvedWriteSurfacePolicy
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ManagedFieldCatalogArtifactPlannerTest {

    @Test
    fun `plans build owned catalog with exact runtime binding coordinates`() {
        val entity = entity()
        val item = ManagedFieldCatalogArtifactPlanner().plan(
            config = ProjectConfig(
                basePackage = "com.acme.demo",
                modules = mapOf("domain" to "demo-domain"),
            ),
            model = CanonicalModel(
                entities = listOf(entity),
                managedFieldPolicies = listOf(
                    ResolvedManagedEntityPolicy(
                        entityName = entity.name,
                        entityPackageName = entity.packageName,
                        tableName = entity.tableName,
                        fields = listOf(
                            managedField(
                                fieldName = "id",
                                columnName = "article_id",
                                fieldType = "Long",
                                policyKey = "identifier.assigned",
                                role = ManagedFieldRole.IDENTIFIER,
                                explicitValue = ManagedExplicitValuePolicy.REQUIRE,
                                lifecycles = setOf(ManagedFieldLifecycle.ENTITY_ADMISSION),
                                qualifier = "identifier.assigned",
                                insert = ManagedValueAuthority.CALLER,
                            ),
                            managedField(
                                fieldName = "tenantId",
                                columnName = "tenant_id",
                                fieldType = "String",
                                policyKey = "scope.tenant",
                                role = ManagedFieldRole.SCOPE,
                                explicitValue = ManagedExplicitValuePolicy.REQUIRE_CONTEXT_MATCH,
                                lifecycles = setOf(ManagedFieldLifecycle.ENTITY_ADMISSION),
                                qualifier = "scope.tenant",
                                insert = ManagedValueAuthority.MANAGED_HANDLER,
                            ),
                            managedField(
                                fieldName = "revision",
                                columnName = "revision",
                                fieldType = "Long",
                                policyKey = "version",
                                role = ManagedFieldRole.VERSION,
                                explicitValue = ManagedExplicitValuePolicy.FORBID,
                                lifecycles = setOf(ManagedFieldLifecycle.PERSISTENCE_PROVIDER),
                                qualifier = null,
                                insert = ManagedValueAuthority.PERSISTENCE_PROVIDER,
                                update = ManagedValueAuthority.PERSISTENCE_PROVIDER,
                            ),
                            managedField(
                                fieldName = "createdAt",
                                columnName = "created_at",
                                fieldType = "java.time.Instant",
                                policyKey = "enrichment.audit-time.created-at",
                                role = ManagedFieldRole.ENRICHMENT,
                                lifecycles = setOf(ManagedFieldLifecycle.PERSISTENCE_ENRICHMENT),
                                qualifier = "enrichment.audit-time",
                                slot = "created-at",
                                insert = ManagedValueAuthority.MANAGED_HANDLER,
                            ),
                            managedField(
                                fieldName = "updatedBy",
                                columnName = "updated_by",
                                fieldType = "String",
                                nullable = true,
                                policyKey = "enrichment.audit-actor.updated-by",
                                role = ManagedFieldRole.ENRICHMENT,
                                lifecycles = setOf(ManagedFieldLifecycle.PERSISTENCE_ENRICHMENT),
                                qualifier = "enrichment.audit-actor",
                                slot = "updated-by",
                                insert = ManagedValueAuthority.MANAGED_HANDLER,
                                update = ManagedValueAuthority.MANAGED_HANDLER,
                            ),
                        ),
                        writeSurface = ResolvedWriteSurfacePolicy(),
                    )
                ),
            ),
        ).single()

        assertEquals("aggregate/managed_field_catalog.kt.peb", item.templateId)
        assertEquals(ArtifactOutputKind.GENERATED_SOURCE, item.outputKind)
        assertEquals("ManagedFieldCatalogContribution", item.context["typeName"])
        @Suppress("UNCHECKED_CAST")
        val bindings = item.context["bindings"] as List<Map<String, Any?>>
        assertEquals(
            listOf("createdAt", "id", "revision", "tenantId", "updatedBy"),
            bindings.map { it["fieldName"] },
        )

        val tenant = bindings.single { it["fieldName"] == "tenantId" }
        assertEquals("com.acme.demo.domain.Article::class", tenant["entityTypeExpression"])
        assertEquals("\"tenantId\"", tenant["persistencePropertyNameKotlinStringLiteral"])
        assertEquals("\"tenant_id\"", tenant["columnNameKotlinStringLiteral"])
        assertEquals("String::class", tenant["targetTypeExpression"])
        assertEquals("\"scope.tenant\"", tenant["policyKeyKotlinStringLiteral"])
        assertEquals("SCOPE", tenant["role"])
        assertEquals("\"scope.tenant\"", tenant["handlerQualifierKotlinStringLiteral"])
        assertNull(tenant["handlerSlotKotlinStringLiteral"])
        assertEquals("MANAGED_HANDLER", tenant["insertAuthority"])
        assertEquals("NONE", tenant["updateAuthority"])

        val createdAt = bindings.single { it["fieldName"] == "createdAt" }
        assertEquals("java.time.Instant::class", createdAt["targetTypeExpression"])
        assertEquals("java.time.Instant::class", createdAt["semanticTypeExpression"])
        assertEquals("\"enrichment.audit-time\"", createdAt["handlerQualifierKotlinStringLiteral"])
        assertEquals("\"created-at\"", createdAt["handlerSlotKotlinStringLiteral"])
        assertEquals(listOf("PERSISTENCE_ENRICHMENT"), createdAt["lifecycles"])

        @Suppress("UNCHECKED_CAST")
        val versionSupport = bindings.single { it["fieldName"] == "revision" }["runtimeSupport"]
            as Map<String, Any?>
        assertEquals("FORBIDDEN_EXPLICIT_VALUE", versionSupport["kind"])
        assertEquals(true, versionSupport["allowsIntegralZero"])

        val updatedBy = bindings.single { it["fieldName"] == "updatedBy" }
        assertEquals(true, updatedBy["nullable"])
        assertEquals("MANAGED_HANDLER", updatedBy["updateAuthority"])
        assertTrue(item.outputPath.endsWith("/domain/_share/managed/ManagedFieldCatalogContribution.kt"))
    }

    private fun entity(): EntityModel {
        val id = FieldModel("id", "Long", columnName = "article_id")
        return EntityModel(
            name = "Article",
            packageName = "com.acme.demo.domain",
            tableName = "article",
            comment = "article",
            fields = listOf(
                id,
                FieldModel("tenantId", "String", columnName = "tenant_id"),
                FieldModel("revision", "Long", columnName = "revision"),
                FieldModel("createdAt", "java.time.Instant", columnName = "created_at"),
                FieldModel("updatedBy", "String", nullable = true, columnName = "updated_by"),
            ),
            idField = id,
        )
    }

    private fun managedField(
        fieldName: String,
        columnName: String,
        fieldType: String,
        nullable: Boolean = false,
        policyKey: String,
        role: ManagedFieldRole,
        explicitValue: ManagedExplicitValuePolicy = ManagedExplicitValuePolicy.OVERWRITE,
        lifecycles: Set<ManagedFieldLifecycle>,
        qualifier: String?,
        slot: String? = null,
        insert: ManagedValueAuthority,
        update: ManagedValueAuthority = ManagedValueAuthority.NONE,
    ) = ResolvedManagedFieldPolicy(
        fieldName = fieldName,
        columnName = columnName,
        fieldType = fieldType,
        nullable = nullable,
        selection = ManagedPolicySelectionProvenance.ExplicitColumnAnnotation("test"),
        definitionOwner = ManagedPolicyDefinitionOwner.BuiltIn,
        policyKey = policyKey,
        role = role,
        creationInput = ManagedCreationInputPolicy.OMIT,
        explicitValue = explicitValue,
        lifecycles = lifecycles,
        handlerQualifier = qualifier,
        handlerSlot = slot,
        semanticValueType = fieldType,
        valueAdapterQualifier = null,
        persistence = PersistenceParticipation(insert, update),
    )
}

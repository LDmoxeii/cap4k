package com.only4.cap4k.plugin.pipeline.api

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files

class PipelineModelsTest {

    @Test
    fun `aggregate column does not infer nested converter from converted type`() {
        val column = AggregateColumnJpaModel(
            fieldName = "payload",
            columnName = "payload",
            isId = false,
            converterTypeFqn = "com.acme.Payload",
        )

        assertNull(column.converterClassFqn)
    }

    @Test
    fun `canonical API omits domain parent ref and automatic inverse relation models`() {
        assertFalse(FieldModel::class.java.declaredFields.any { it.name == "parentRef" })
        assertFalse(CanonicalModel::class.java.declaredFields.any { it.name == "aggregate" + "InverseRelations" })
        assertThrows(ClassNotFoundException::class.java) {
            Class.forName("com.only4.cap4k.plugin.pipeline.api.Aggregate" + "InverseRelationModel")
        }
    }

    @Test
    fun `unique constraint metadata distinguishes complete unconditional indexes`() {
        val unconditional = UniqueConstraintModel(
            physicalName = "uk_video_post_slug",
            columns = listOf("slug"),
        )
        val filtered = UniqueConstraintModel(
            physicalName = "uk_video_post_slug_active",
            columns = listOf("slug"),
            complete = false,
            filterCondition = "deleted = 0",
        )

        assertTrue(unconditional.complete)
        assertNull(unconditional.filterCondition)
        assertFalse(filtered.complete)
        assertEquals("deleted = 0", filtered.filterCondition)
    }

    @Test
    fun `db column snapshot carries parent ref and raw managed policy key`() {
        val column = DbColumnSnapshot(
            name = "parent_id",
            dbType = "BIGINT",
            kotlinType = "Long",
            nullable = false,
            parentRef = true,
            managedPolicyKey = "scope.tenant",
        )

        assertTrue(column.parentRef)
        assertEquals("scope.tenant", column.managedPolicyKey)
    }

    @Test
    fun `db snapshot preserves exact identifier policy`() {
        val column = DbColumnSnapshot(
            name = "id",
            dbType = "varchar(36)",
            kotlinType = "String",
            nullable = false,
            isPrimaryKey = true,
            managedPolicyKey = "identifier.uuid7",
        )

        assertEquals("identifier.uuid7", column.managedPolicyKey)
    }

    @Test
    fun `built in catalog exposes UUID7 as the only framework allocated identifier`() {
        val frameworkAllocatedIdentifiers = BuiltInManagedFieldPolicies.definitions
            .filter { definition ->
                definition.role == ManagedFieldRole.IDENTIFIER &&
                    definition.persistence.insert == ManagedValueAuthority.FRAMEWORK
            }
            .map { it.key }

        assertEquals(listOf("identifier.uuid7"), frameworkAllocatedIdentifiers)
    }

    @Test
    fun `design block stores artifact selections`() {
        val block = DesignBlockModel(
            tag = "query",
            packageName = "order.read",
            name = "FindOrderPage",
            description = "Find order page",
            aggregates = listOf("Order"),
            artifacts = listOf(
                ArtifactSelectionModel(family = "query", variant = "page"),
                ArtifactSelectionModel(family = "query-handler"),
            ),
            request = semanticValue(
                name = "FindOrderPage.Request",
                role = SemanticValueRole.QUERY_REQUEST,
                fields = listOf(
                    SemanticValueField(
                        name = "keyword",
                        type = SemanticBuiltinTypeRef(SemanticBuiltinType.STRING, nullable = true),
                    )
                ),
            ),
            response = semanticValue(
                name = "FindOrderPage.Response",
                role = SemanticValueRole.QUERY_RESPONSE,
                fields = listOf(
                    SemanticValueField(
                        name = "orderNo",
                        type = SemanticBuiltinTypeRef(SemanticBuiltinType.STRING),
                    )
                ),
            ),
        )

        assertEquals("query", block.tag)
        assertEquals(listOf("Order"), block.aggregates)
        assertEquals("page", block.artifacts.first().variant)
        assertEquals("query-handler", block.artifacts.last().family)
        assertEquals("keyword", block.fields.single().name)
        assertEquals("orderNo", block.resultFields.single().name)
    }

    @Test
    fun `design block preserves explicit empty artifacts for drawing board output`() {
        val omittedArtifacts = DesignBlockModel(
            tag = "query",
            packageName = "order.read",
            name = "FindOrder",
            artifacts = listOf(
                ArtifactSelectionModel(family = "query"),
                ArtifactSelectionModel(family = "query-handler"),
            ),
            artifactsDeclared = false,
            request = semanticValue("FindOrder.Request", SemanticValueRole.QUERY_REQUEST),
        )
        val explicitEmptyArtifacts = DesignBlockModel(
            tag = "query",
            packageName = "order.read",
            name = "FindOrder",
            artifacts = emptyList(),
            artifactsDeclared = true,
            request = semanticValue("FindOrder.Request", SemanticValueRole.QUERY_REQUEST),
        )

        assertFalse(omittedArtifacts.includeDesignJsonArtifacts)
        assertTrue(explicitEmptyArtifacts.includeDesignJsonArtifacts)
        assertEquals(emptyList<ArtifactSelectionModel>(), explicitEmptyArtifacts.designJsonArtifacts)
    }

    @Test
    fun `design spec entry exposes public v2 fields and nullable artifact selections`() {
        val requestFields = listOf(SemanticFieldSnapshot(name = "keyword", typeExpression = "String?"))
        val responseFields = listOf(SemanticFieldSnapshot(name = "orderNo", typeExpression = "String"))
        val entryWithOmittedArtifacts = DesignSpecEntry(
            tag = "query",
            packageName = "order.read",
            name = "FindOrderPage",
            description = "find order page",
            aggregates = listOf("Order"),
            fields = requestFields,
            resultFields = responseFields,
        )
        val entryWithExplicitEmptyArtifacts = entryWithOmittedArtifacts.copy(artifacts = emptyList())

        assertNull(entryWithOmittedArtifacts.artifacts)
        assertEquals(requestFields, entryWithOmittedArtifacts.fields)
        assertEquals(responseFields, entryWithOmittedArtifacts.resultFields)
        assertEquals(emptyList<ArtifactSelectionModel>(), entryWithExplicitEmptyArtifacts.artifacts)
    }

    @Test
    fun `canonical model defaults design blocks to empty list`() {
        val model = CanonicalModel()

        assertEquals(emptyList<DesignBlockModel>(), model.designBlocks)
    }

    private fun semanticValue(
        name: String,
        role: SemanticValueRole,
        fields: List<SemanticValueField> = emptyList(),
    ): SemanticValueDefinition = SemanticValueDefinition(
        identity = CanonicalTypeIdentity(
            packageName = "order.read",
            typePath = name.split('.'),
            kind = CanonicalTypeKind.NESTED_VALUE,
        ),
        role = role,
        fields = fields,
    )

    @Test
    fun `aggregate persistence provider control carries semantic soft delete policy`() {
        val softDelete = AggregateSoftDeletePolicy(
            fieldName = "deleted",
            columnName = "deleted",
            storageKind = AggregateIdStorageKind.CHARACTER,
            activeSentinel = SoftDeleteActiveSentinel.NIL_UUID,
            tombstoneStrategy = SoftDeleteTombstoneStrategy.SELF_ID,
        )
        val control = AggregatePersistenceProviderControl(
            entityName = "VideoPost",
            entityPackageName = "com.acme.demo.domain.aggregates.video_post",
            tableName = "video_post",
            softDelete = softDelete,
            idFieldName = "id",
            versionFieldName = "version",
        )

        assertEquals(softDelete, control.softDelete)
        assertEquals(AggregateIdStorageKind.CHARACTER, control.softDelete?.storageKind)
        assertEquals(SoftDeleteActiveSentinel.NIL_UUID, control.softDelete?.activeSentinel)
        assertEquals(SoftDeleteTombstoneStrategy.SELF_ID, control.softDelete?.tombstoneStrategy)

        val domainFields = AggregateSoftDeletePolicy::class.java.declaredFields
            .filterNot { it.isSynthetic }
            .map { it.name }
        assertEquals(
            setOf("fieldName", "columnName", "storageKind", "activeSentinel", "tombstoneStrategy"),
            domainFields.toSet(),
        )
        assertFalse(domainFields.contains("activeValue"))
        assertFalse(domainFields.contains("activePredicateSql"))
        assertFalse(domainFields.contains("deleteAssignmentSql"))
    }

    @Test
    fun `soft delete storage semantics expose stable public enum order`() {
        assertEquals(
            listOf("INTEGRAL", "CHARACTER", "NATIVE_UUID"),
            AggregateIdStorageKind.entries.map { it.name },
        )
        assertEquals(
            listOf("ZERO", "NIL_UUID"),
            SoftDeleteActiveSentinel.entries.map { it.name },
        )
    }

    @Test
    fun `aggregate relation model carries owned cardinality separately from persistence type`() {
        val relation = AggregateRelationModel(
            ownerEntityName = "VideoPost",
            ownerEntityPackageName = "com.acme.demo.domain.aggregates.video_post",
            fieldName = "files",
            targetEntityName = "VideoPostFile",
            targetEntityPackageName = "com.acme.demo.domain.aggregates.video_post",
            relationType = AggregateRelationType.ONE_TO_MANY,
            joinColumn = "video_post_id",
            fetchType = AggregateFetchType.LAZY,
            nullable = false,
            owned = true,
            parentRefColumn = "video_post_id",
            ownedCardinality = OwnedRelationCardinality.ONE,
            persistenceShape = OwnedRelationPersistenceShape.ONE_TO_MANY_JOIN_COLUMN,
            backingCollectionName = "files",
            singleAccessorName = "file",
        )

        assertEquals(AggregateRelationType.ONE_TO_MANY, relation.relationType)
        assertTrue(relation.owned)
        assertEquals("video_post_id", relation.parentRefColumn)
        assertEquals(OwnedRelationCardinality.ONE, relation.ownedCardinality)
        assertEquals(OwnedRelationPersistenceShape.ONE_TO_MANY_JOIN_COLUMN, relation.persistenceShape)
        assertEquals("files", relation.backingCollectionName)
        assertEquals("file", relation.singleAccessorName)
    }

    @Test
    fun `Analyzer source identity is stable across checkout roots and redacts external paths`() {
        val firstProject = Files.createTempDirectory("analyzer-source-project-a")
        val secondProject = Files.createTempDirectory("analyzer-source-project-b")
        val relativeInput = "modules/orders/build/cap4k-code-analysis"
        val firstInput = firstProject.resolve(relativeInput).also { Files.createDirectories(it) }
        val secondInput = secondProject.resolve(relativeInput).also { Files.createDirectories(it) }

        val first = analyzerSourceIdentity(firstInput.toString(), firstProject.toString())
        val second = analyzerSourceIdentity(secondInput.toString(), secondProject.toString())
        val configuredRelative = analyzerSourceIdentity(relativeInput, firstProject.toString())
        val externalInput = Files.createTempDirectory("analyzer-source-external")
        val external = analyzerSourceIdentity(externalInput.toString(), firstProject.toString())

        assertEquals("project:modules/orders/build/cap4k-code-analysis", first.id)
        assertEquals(first.id, second.id)
        assertEquals(first.id, configuredRelative.id)
        assertTrue(external.id.startsWith("external-"))
        assertFalse(external.id.contains(externalInput.toString().replace('\\', '/')))
    }

    @Test
    fun `Analyzer catalog exposes three isolated partitions and deterministic status aggregation`() {
        assertEquals(
            listOf("aggregateStructure", "designProjection", "graph"),
            AnalyzerContractCatalog.partitions.map { partition -> partition.id }.sorted(),
        )
        assertEquals(
            listOf("pipeline.generator.flow"),
            AnalyzerContractCatalog.GRAPH.consumerCapabilityIds,
        )
        assertEquals(
            listOf("drawing_board_aggregate_elements.json"),
            AnalyzerContractCatalog.AGGREGATE_STRUCTURE.outputIds,
        )
        assertEquals(AgentSnapshotStatus.UNAVAILABLE, analyzerSnapshotStatus(emptyList()))
        assertEquals(
            AgentSnapshotStatus.PARTIAL,
            analyzerSnapshotStatus(listOf(AgentSnapshotStatus.OK, AgentSnapshotStatus.UNAVAILABLE)),
        )
        assertEquals(
            AgentSnapshotStatus.INVALID,
            analyzerSnapshotStatus(listOf(AgentSnapshotStatus.PARTIAL, AgentSnapshotStatus.INVALID)),
        )
        assertEquals(
            AgentSnapshotStatus.OK,
            analyzerSnapshotStatus(listOf(AgentSnapshotStatus.OK, AgentSnapshotStatus.OK)),
        )
    }
}

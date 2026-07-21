package com.only4.cap4k.plugin.pipeline.generator.aggregate

import com.only4.cap4k.plugin.pipeline.api.AggregateColumnJpaModel
import com.only4.cap4k.plugin.pipeline.api.AggregateEntityJpaModel
import com.only4.cap4k.plugin.pipeline.api.AggregateFetchType
import com.only4.cap4k.plugin.pipeline.api.AggregateRelationModel
import com.only4.cap4k.plugin.pipeline.api.AggregateRelationType
import com.only4.cap4k.plugin.pipeline.api.ArtifactOutputKind
import com.only4.cap4k.plugin.pipeline.api.CanonicalModel
import com.only4.cap4k.plugin.pipeline.api.ConflictPolicy
import com.only4.cap4k.plugin.pipeline.api.EntityModel
import com.only4.cap4k.plugin.pipeline.api.FieldModel
import com.only4.cap4k.plugin.pipeline.api.GeneratorConfig
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig
import com.only4.cap4k.plugin.pipeline.api.ProjectLayout
import com.only4.cap4k.plugin.pipeline.api.StrongIdKind
import com.only4.cap4k.plugin.pipeline.api.StrongIdModel
import com.only4.cap4k.plugin.pipeline.api.TemplateConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AggregateProjectionArtifactPlannerTest {

    @Test
    fun `plans adapter generated scalar projection with relation metadata`() {
        val category = EntityModel(
            name = "Category",
            packageName = "com.acme.demo.domain.aggregates.catalog.category",
            tableName = "category",
            comment = "Category",
            fields = listOf(
                FieldModel("id", "Long"),
                FieldModel("parentId", "Long", nullable = true),
                FieldModel("name", "String"),
                FieldModel("version", "Int"),
            ),
            idField = FieldModel("id", "Long"),
        )
        val parent = EntityModel(
            name = "Category",
            packageName = "com.acme.demo.domain.aggregates.catalog.parent_category",
            tableName = "parent_category",
            comment = "Parent category",
            fields = listOf(FieldModel("id", "Long")),
            idField = FieldModel("id", "Long"),
        )
        val model = CanonicalModel(
            entities = listOf(category),
            aggregateEntityJpa = listOf(
                AggregateEntityJpaModel(
                    entityName = category.name,
                    entityPackageName = category.packageName,
                    entityEnabled = true,
                    tableName = "category",
                    columns = listOf(
                        AggregateColumnJpaModel("id", "id", isId = true),
                        AggregateColumnJpaModel("parentId", "parent_id", isId = false),
                        AggregateColumnJpaModel("name", "name", isId = false),
                        AggregateColumnJpaModel("version", "version", isId = false),
                    ),
                )
            ),
            aggregateRelations = listOf(
                AggregateRelationModel(
                    ownerEntityName = category.name,
                    ownerEntityPackageName = category.packageName,
                    fieldName = "parent",
                    targetEntityName = parent.name,
                    targetEntityPackageName = parent.packageName,
                    relationType = AggregateRelationType.MANY_TO_ONE,
                    joinColumn = "parent_id",
                    fetchType = AggregateFetchType.LAZY,
                    nullable = true,
                )
            ),
        )

        val item = AggregateProjectionArtifactPlanner().plan(projectionConfig(), model).single()

        assertEquals("aggregate-projection", item.generatorId)
        assertEquals("adapter", item.moduleRole)
        assertEquals("aggregate_projection/entity.kt.peb", item.templateId)
        assertEquals(
            "demo-adapter/build/generated/cap4k/main/kotlin/com/acme/demo/adapter/application/projections/catalog/category/CategoryProjection.kt",
            item.outputPath,
        )
        assertEquals(ConflictPolicy.OVERWRITE, item.conflictPolicy)
        assertEquals(ArtifactOutputKind.GENERATED_SOURCE, item.outputKind)
        assertEquals("demo-adapter/build/generated/cap4k/main/kotlin", item.resolvedOutputRoot)
        assertEquals("com.acme.demo.adapter.application.projections.catalog.category", item.context["packageName"])
        assertEquals("CategoryProjection", item.context["typeName"])
        assertEquals("Category", item.context["sourceTypeName"])
        assertEquals(category.packageName, item.context["sourcePackageName"])
        @Suppress("UNCHECKED_CAST")
        val aggregateElement = item.context["aggregateElement"] as? Map<String, Any?>
        assertEquals("Category", aggregateElement?.get("aggregate"))
        assertEquals("CategoryProjection", aggregateElement?.get("name"))
        assertEquals("com.acme.demo.adapter.application.projections.catalog.category", aggregateElement?.get("packageName"))
        assertEquals("Category", aggregateElement?.get("description"))
        assertEquals("projection", aggregateElement?.get("type"))
        assertEquals(false, aggregateElement?.get("root"))

        val scalarFields = item.context["scalarFields"] as List<Map<String, Any?>>
        assertEquals(listOf("id", "parentId", "name", "version"), scalarFields.map { it["name"] })
        assertFalse(scalarFields.any { it["name"] == "parent" })
        assertEquals(true, scalarFields.single { it["name"] == "id" }["isId"])

        val relationFields = item.context["relationFields"] as List<Map<String, Any?>>
        val relation = relationFields.single()
        assertEquals("parent", relation["name"])
        assertEquals("CategoryProjection", relation["targetType"])
        assertEquals(
            "com.acme.demo.adapter.application.projections.catalog.parent_category",
            relation["targetPackageName"],
        )
        assertEquals(
            "com.acme.demo.adapter.application.projections.catalog.parent_category.CategoryProjection",
            relation["targetTypeRef"],
        )
        assertEquals(relationFields, item.context["relations"])
    }

    @Test
    fun `fails when aggregate jpa metadata is missing for scalar field`() {
        val entity = EntityModel(
            name = "Category",
            packageName = "com.acme.demo.domain.aggregates.category",
            tableName = "category",
            comment = "Category",
            fields = listOf(FieldModel("id", "Long"), FieldModel("name", "String")),
            idField = FieldModel("id", "Long"),
        )
        val model = CanonicalModel(
            entities = listOf(entity),
            aggregateEntityJpa = listOf(
                AggregateEntityJpaModel(
                    entityName = entity.name,
                    entityPackageName = entity.packageName,
                    entityEnabled = true,
                    tableName = entity.tableName,
                    columns = listOf(AggregateColumnJpaModel("id", "id", isId = true)),
                )
            ),
        )

        val error = assertThrows(IllegalArgumentException::class.java) {
            AggregateProjectionArtifactPlanner().plan(projectionConfig(), model)
        }

        assertEquals(
            "missing aggregate JPA metadata for com.acme.demo.domain.aggregates.category.Category.name",
            error.message,
        )
    }

    @Test
    fun `includes scalar imports for short uuid projection fields`() {
        val entity = EntityModel(
            name = "Category",
            packageName = "com.acme.demo.domain.aggregates.category",
            tableName = "category",
            comment = "Category",
            fields = listOf(FieldModel("id", "UUID")),
            idField = FieldModel("id", "UUID"),
        )
        val model = CanonicalModel(
            entities = listOf(entity),
            aggregateEntityJpa = listOf(
                AggregateEntityJpaModel(
                    entityName = entity.name,
                    entityPackageName = entity.packageName,
                    entityEnabled = true,
                    tableName = entity.tableName,
                    columns = listOf(AggregateColumnJpaModel("id", "id", isId = true)),
                )
            ),
        )

        val item = AggregateProjectionArtifactPlanner().plan(projectionConfig(), model).single()

        assertEquals(listOf("java.util.UUID"), item.context["imports"])
    }

    @Test
    fun `includes rendered scalar imports for strong ids and qualified projection fields`() {
        val entity = EntityModel(
            name = "MediaProcessingTask",
            packageName = "com.acme.demo.domain.aggregates.media_processing_task",
            tableName = "media_processing_task",
            comment = "Media processing task",
            fields = listOf(
                FieldModel("id", "Long"),
                FieldModel("contentId", "ContentId"),
                FieldModel(
                    "processingStatus",
                    "com.acme.demo.domain.aggregates.media_processing_task.enums.MediaProcessingStatus",
                ),
                FieldModel("dbUpdatedAt", "java.time.LocalDateTime"),
            ),
            idField = FieldModel("id", "Long"),
        )
        val model = CanonicalModel(
            entities = listOf(entity),
            aggregateEntityJpa = listOf(
                AggregateEntityJpaModel(
                    entityName = entity.name,
                    entityPackageName = entity.packageName,
                    entityEnabled = true,
                    tableName = entity.tableName,
                    columns = listOf(
                        AggregateColumnJpaModel("id", "id", isId = true),
                        AggregateColumnJpaModel("contentId", "content_id", isId = false),
                        AggregateColumnJpaModel("processingStatus", "processing_status", isId = false),
                        AggregateColumnJpaModel("dbUpdatedAt", "db_updated_at", isId = false),
                    ),
                )
            ),
            strongIds = listOf(
                StrongIdModel(
                    typeName = "ContentId",
                    packageName = "com.acme.demo.domain.aggregates.content",
                    kind = StrongIdKind.AGGREGATE_ROOT,
                    ownerAggregateName = "Content",
                    ownerAggregatePackageName = "com.acme.demo.domain.aggregates.content",
                )
            ),
        )

        val item = AggregateProjectionArtifactPlanner().plan(projectionConfig(), model).single()

        @Suppress("UNCHECKED_CAST")
        val scalarFields = item.context["scalarFields"] as List<Map<String, Any?>>
        assertEquals(
            listOf(
                "com.acme.demo.domain.aggregates.content.ContentId",
                "com.acme.demo.domain.aggregates.media_processing_task.enums.MediaProcessingStatus",
                "java.time.LocalDateTime",
            ),
            item.context["imports"],
        )
        assertEquals("ContentId", scalarFields.single { it["name"] == "contentId" }["renderedType"])
        assertEquals(
            listOf("com.acme.demo.domain.aggregates.content.ContentId"),
            scalarFields.single { it["name"] == "contentId" }["typeImports"],
        )
        assertEquals(
            "MediaProcessingStatus",
            scalarFields.single { it["name"] == "processingStatus" }["renderedType"],
        )
        assertEquals("LocalDateTime", scalarFields.single { it["name"] == "dbUpdatedAt" }["renderedType"])
    }

    private fun projectionConfig(): ProjectConfig =
        ProjectConfig(
            basePackage = "com.acme.demo",
            layout = ProjectLayout.MULTI_MODULE,
            modules = mapOf("adapter" to "demo-adapter"),
            sources = emptyMap(),
            generators = mapOf("aggregate-projection" to GeneratorConfig()),
            templates = TemplateConfig("ddd-default", emptyList(), ConflictPolicy.SKIP),
        )
}

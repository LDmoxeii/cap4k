package com.only4.cap4k.plugin.pipeline.generator.aggregate

import com.only4.cap4k.plugin.pipeline.api.CanonicalModel
import com.only4.cap4k.plugin.pipeline.api.ConflictPolicy
import com.only4.cap4k.plugin.pipeline.api.EntityModel
import com.only4.cap4k.plugin.pipeline.api.FieldModel
import com.only4.cap4k.plugin.pipeline.api.GeneratorConfig
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig
import com.only4.cap4k.plugin.pipeline.api.ProjectLayout
import com.only4.cap4k.plugin.pipeline.api.RepositoryModel
import com.only4.cap4k.plugin.pipeline.api.SchemaModel
import com.only4.cap4k.plugin.pipeline.api.TemplateConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.nio.file.Path

class AggregateArtifactPlannerTest {

    @Test
    fun `plans schema entity and repository artifacts into domain and adapter modules`() {
        val config = ProjectConfig(
            basePackage = "com.acme.demo",
            layout = ProjectLayout.MULTI_MODULE,
            modules = mapOf(
                "domain" to "demo-domain",
                "adapter" to "demo-adapter",
            ),
            sources = emptyMap(),
            generators = mapOf("aggregate" to GeneratorConfig(enabled = true)),
            templates = TemplateConfig("ddd-default", emptyList(), ConflictPolicy.SKIP),
        )

        val model = CanonicalModel(
            schemas = listOf(
                SchemaModel(
                    name = "SVideoPost",
                    packageName = "com.acme.demo.domain._share.meta.video_post",
                    entityName = "VideoPost",
                    comment = "Video post schema",
                    fields = listOf(FieldModel("id", "Long")),
                )
            ),
            entities = listOf(
                EntityModel(
                    name = "VideoPost",
                    packageName = "com.acme.demo.domain.aggregates.video_post",
                    tableName = "video_post",
                    comment = "Video post entity",
                    fields = listOf(FieldModel("id", "Long")),
                    idField = FieldModel("id", "Long"),
                )
            ),
            repositories = listOf(
                RepositoryModel(
                    name = "VideoPostRepository",
                    packageName = "com.acme.demo.adapter.domain.repositories",
                    entityName = "VideoPost",
                    idType = "Long",
                )
            ),
        )

        val planItems = AggregateArtifactPlanner().plan(config, model)

        assertEquals(5, planItems.size)
        assertEquals(
            "demo-domain/src/main/kotlin/com/acme/demo/domain/_share/meta/video_post/SVideoPost.kt",
            planItems.first { it.templateId == "aggregate/schema.kt.peb" }.outputPath,
        )
        assertEquals(
            "Video post schema",
            planItems.first { it.templateId == "aggregate/schema.kt.peb" }.context["comment"],
        )
        assertEquals(
            "com.acme.demo.domain.aggregates.video_post.VideoPost",
            planItems.first { it.templateId == "aggregate/schema.kt.peb" }.context["entityTypeFqn"],
        )
        assertEquals(
            "com.acme.demo.domain.aggregates.video_post.QVideoPost",
            planItems.first { it.templateId == "aggregate/schema.kt.peb" }.context["qEntityTypeFqn"],
        )
        assertEquals(
            "demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/VideoPost.kt",
            planItems.first { it.templateId == "aggregate/entity.kt.peb" }.outputPath,
        )
        val entityContext = planItems.first { it.templateId == "aggregate/entity.kt.peb" }.context
        assertEquals(false, entityContext.containsKey("entityTypeFqn"))
        assertEquals(false, entityContext.containsKey("qEntityTypeFqn"))
        assertEquals(
            "Video post entity",
            entityContext["comment"],
        )
        assertEquals(
            FieldModel("id", "Long"),
            entityContext["idField"],
        )
        assertEquals(
            "demo-adapter/src/main/kotlin/com/acme/demo/adapter/domain/repositories/VideoPostRepository.kt",
            planItems.first { it.templateId == "aggregate/repository.kt.peb" }.outputPath,
        )
        val repositoryContext = planItems.first { it.templateId == "aggregate/repository.kt.peb" }.context
        assertEquals(false, repositoryContext.containsKey("entityTypeFqn"))
        assertEquals(false, repositoryContext.containsKey("qEntityTypeFqn"))
        assertEquals(
            "demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/factory/VideoPostFactory.kt",
            planItems.first { it.templateId == "aggregate/factory.kt.peb" }.outputPath,
        )
        assertEquals(
            "demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/specification/VideoPostSpecification.kt",
            planItems.first { it.templateId == "aggregate/specification.kt.peb" }.outputPath,
        )
        val factoryContext = planItems.first { it.templateId == "aggregate/factory.kt.peb" }.context
        assertEquals("com.acme.demo.domain.aggregates.video_post.factory", factoryContext["packageName"])
        assertEquals("VideoPostFactory", factoryContext["typeName"])
        assertEquals("Payload", factoryContext["payloadTypeName"])
        assertEquals("VideoPost", factoryContext["entityName"])
        assertEquals("com.acme.demo.domain.aggregates.video_post.VideoPost", factoryContext["entityTypeFqn"])
        assertEquals("VideoPost", factoryContext["aggregateName"])
        val specificationContext = planItems.first { it.templateId == "aggregate/specification.kt.peb" }.context
        assertEquals("com.acme.demo.domain.aggregates.video_post.specification", specificationContext["packageName"])
        assertEquals("VideoPostSpecification", specificationContext["typeName"])
        assertEquals("VideoPost", specificationContext["entityName"])
        assertEquals("com.acme.demo.domain.aggregates.video_post.VideoPost", specificationContext["entityTypeFqn"])
        assertEquals("VideoPost", specificationContext["aggregateName"])
    }

    @Test
    fun `derived aggregate references are exposed only to schema factory and specification contexts`() {
        val config = aggregateConfig()
        val model = CanonicalModel(
            schemas = listOf(
                SchemaModel(
                    name = "SVideoPost",
                    packageName = "com.acme.demo.domain._share.meta.video_post",
                    entityName = "VideoPost",
                    comment = "Video post schema",
                    fields = listOf(FieldModel("id", "Long")),
                )
            ),
            entities = listOf(
                EntityModel(
                    name = "VideoPost",
                    packageName = "com.acme.demo.domain.aggregates.video_post",
                    tableName = "video_post",
                    comment = "Video post entity",
                    fields = listOf(FieldModel("id", "Long")),
                    idField = FieldModel("id", "Long"),
                )
            ),
            repositories = listOf(
                RepositoryModel(
                    name = "VideoPostRepository",
                    packageName = "com.acme.demo.adapter.domain.repositories",
                    entityName = "VideoPost",
                    idType = "Long",
                )
            ),
        )

        val planItems = AggregateArtifactPlanner().plan(config, model)
        val schemaContext = planItems.first { it.templateId == "aggregate/schema.kt.peb" }.context
        val factoryContext = planItems.first { it.templateId == "aggregate/factory.kt.peb" }.context
        val specificationContext = planItems.first { it.templateId == "aggregate/specification.kt.peb" }.context
        val entityContext = planItems.first { it.templateId == "aggregate/entity.kt.peb" }.context
        val repositoryContext = planItems.first { it.templateId == "aggregate/repository.kt.peb" }.context

        assertEquals("com.acme.demo.domain.aggregates.video_post.VideoPost", schemaContext["entityTypeFqn"])
        assertEquals("com.acme.demo.domain.aggregates.video_post.QVideoPost", schemaContext["qEntityTypeFqn"])
        assertEquals("com.acme.demo.domain.aggregates.video_post.VideoPost", factoryContext["entityTypeFqn"])
        assertEquals("com.acme.demo.domain.aggregates.video_post.VideoPost", specificationContext["entityTypeFqn"])
        assertEquals(false, entityContext.containsKey("entityTypeFqn"))
        assertEquals(false, repositoryContext.containsKey("entityTypeFqn"))
    }

    @Test
    fun `schema is ambiguity-safe and factory plus specification planners use the current entity when names collide`() {
        val config = aggregateConfig()
        val primaryEntity = EntityModel(
            name = "VideoPost",
            packageName = "com.acme.demo.domain.aggregates.primary_video_post",
            tableName = "primary_video_post",
            comment = "Primary video post entity",
            fields = listOf(FieldModel("id", "Long")),
            idField = FieldModel("id", "Long"),
        )
        val secondaryEntity = EntityModel(
            name = "VideoPost",
            packageName = "com.acme.demo.domain.aggregates.secondary_video_post",
            tableName = "secondary_video_post",
            comment = "Secondary video post entity",
            fields = listOf(FieldModel("id", "Long")),
            idField = FieldModel("id", "Long"),
        )
        val model = CanonicalModel(
            schemas = listOf(
                SchemaModel(
                    name = "SVideoPost",
                    packageName = "com.acme.demo.domain._share.meta.video_post",
                    entityName = "VideoPost",
                    comment = "Video post schema",
                    fields = listOf(FieldModel("id", "Long")),
                )
            ),
            entities = listOf(primaryEntity, secondaryEntity),
            repositories = emptyList(),
        )

        val planItems = AggregateArtifactPlanner().plan(config, model)
        val schemaContext = planItems.first { it.templateId == "aggregate/schema.kt.peb" }.context
        val primaryFactoryContext = planItems.first {
            it.templateId == "aggregate/factory.kt.peb" &&
                it.context["packageName"] == "com.acme.demo.domain.aggregates.primary_video_post.factory"
        }.context
        val secondaryFactoryContext = planItems.first {
            it.templateId == "aggregate/factory.kt.peb" &&
                it.context["packageName"] == "com.acme.demo.domain.aggregates.secondary_video_post.factory"
        }.context
        val primarySpecificationContext = planItems.first {
            it.templateId == "aggregate/specification.kt.peb" &&
                it.context["packageName"] == "com.acme.demo.domain.aggregates.primary_video_post.specification"
        }.context
        val secondarySpecificationContext = planItems.first {
            it.templateId == "aggregate/specification.kt.peb" &&
                it.context["packageName"] == "com.acme.demo.domain.aggregates.secondary_video_post.specification"
        }.context

        assertEquals("", schemaContext["entityTypeFqn"])
        assertEquals("", schemaContext["qEntityTypeFqn"])
        assertEquals(
            "com.acme.demo.domain.aggregates.primary_video_post.VideoPost",
            primaryFactoryContext["entityTypeFqn"],
        )
        assertEquals(
            "com.acme.demo.domain.aggregates.secondary_video_post.VideoPost",
            secondaryFactoryContext["entityTypeFqn"],
        )
        assertEquals(
            "com.acme.demo.domain.aggregates.primary_video_post.VideoPost",
            primarySpecificationContext["entityTypeFqn"],
        )
        assertEquals(
            "com.acme.demo.domain.aggregates.secondary_video_post.VideoPost",
            secondarySpecificationContext["entityTypeFqn"],
        )
    }

    @Test
    fun `leaves derived schema type references blank when schema entity is missing`() {
        val config = aggregateConfig()
        val model = CanonicalModel(
            schemas = listOf(
                SchemaModel(
                    name = "SUnknown",
                    packageName = "com.acme.demo.domain._share.meta.unknown",
                    entityName = "Unknown",
                    comment = "Unknown schema",
                    fields = listOf(FieldModel("id", "Long")),
                )
            ),
            entities = emptyList(),
            repositories = emptyList(),
        )

        val planItems = AggregateArtifactPlanner().plan(config, model)
        val schemaContext = planItems.first { it.templateId == "aggregate/schema.kt.peb" }.context

        assertEquals("", schemaContext["entityTypeFqn"])
        assertEquals("", schemaContext["qEntityTypeFqn"])
    }

    @Test
    fun `rejects missing required module`() {
        val config = aggregateConfig(domainModule = null)

        val ex = assertThrows(IllegalStateException::class.java) {
            requireRelativeModule(config, "domain")
        }

        assertEquals("domain module is required", ex.message)
    }

    @Test
    fun `rejects invalid shared module paths`() {
        val invalidPaths = listOf(
            "" to "domain module must be a valid relative filesystem path: ",
            ":demo-domain" to "domain module must be a valid relative filesystem path: :demo-domain",
            "../demo-domain" to "domain module must be a valid relative filesystem path: ../demo-domain",
        )

        invalidPaths.forEach { (modulePath, message) ->
            val config = aggregateConfig(domainModule = modulePath)

            val ex = assertThrows(IllegalArgumentException::class.java) {
                requireRelativeModule(config, "domain")
            }

            assertEquals(message, ex.message)
        }

        val absolutePath = Path.of("absolute-demo-domain").toAbsolutePath().toString()
        val absoluteConfig = aggregateConfig(domainModule = absolutePath)

        val absoluteEx = assertThrows(IllegalArgumentException::class.java) {
            requireRelativeModule(absoluteConfig, "domain")
        }

        assertEquals("domain module must be a valid relative filesystem path: $absolutePath", absoluteEx.message)

        val rootedRelativePath = Path.of("\\demo-domain")
        if (rootedRelativePath.root != null && !rootedRelativePath.isAbsolute) {
            val rootedPath = rootedRelativePath.toString()
            val rootedConfig = aggregateConfig(domainModule = rootedPath)

            val rootedEx = assertThrows(IllegalArgumentException::class.java) {
                requireRelativeModule(rootedConfig, "domain")
            }

            assertEquals("domain module must be a valid relative filesystem path: $rootedPath", rootedEx.message)
        }
    }

    private fun aggregateConfig(
        domainModule: String? = "demo-domain",
        adapterModule: String? = "demo-adapter",
    ): ProjectConfig =
        ProjectConfig(
            basePackage = "com.acme.demo",
            layout = ProjectLayout.MULTI_MODULE,
            modules = buildMap {
                domainModule?.let { put("domain", it) }
                adapterModule?.let { put("adapter", it) }
            },
            sources = emptyMap(),
            generators = mapOf("aggregate" to GeneratorConfig(enabled = true)),
            templates = TemplateConfig("ddd-default", emptyList(), ConflictPolicy.SKIP),
        )
}

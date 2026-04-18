package com.only4.cap4k.plugin.pipeline.generator.aggregate

import com.only4.cap4k.plugin.pipeline.api.AggregateFetchType
import com.only4.cap4k.plugin.pipeline.api.AggregateRelationModel
import com.only4.cap4k.plugin.pipeline.api.AggregateRelationType
import com.only4.cap4k.plugin.pipeline.api.CanonicalModel
import com.only4.cap4k.plugin.pipeline.api.ConflictPolicy
import com.only4.cap4k.plugin.pipeline.api.EntityModel
import com.only4.cap4k.plugin.pipeline.api.EnumItemModel
import com.only4.cap4k.plugin.pipeline.api.FieldModel
import com.only4.cap4k.plugin.pipeline.api.GeneratorConfig
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig
import com.only4.cap4k.plugin.pipeline.api.ProjectLayout
import com.only4.cap4k.plugin.pipeline.api.RepositoryModel
import com.only4.cap4k.plugin.pipeline.api.SchemaModel
import com.only4.cap4k.plugin.pipeline.api.SharedEnumDefinition
import com.only4.cap4k.plugin.pipeline.api.TemplateConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Path

class AggregateArtifactPlannerTest {

    @Test
    fun `entity planner surfaces relation fields separately from scalar fields and preserves fields alias`() {
        val plan = AggregateArtifactPlanner().plan(
            aggregateConfig(),
            CanonicalModel(
                entities = listOf(
                    EntityModel(
                        name = "VideoPost",
                        packageName = "com.acme.demo.domain.aggregates.video_post",
                        tableName = "video_post",
                        comment = "video post",
                        fields = listOf(
                            FieldModel("id", "Long"),
                            FieldModel("title", "String"),
                        ),
                        idField = FieldModel("id", "Long"),
                    )
                ),
                aggregateRelations = listOf(
                    AggregateRelationModel(
                        ownerEntityName = "VideoPost",
                        ownerEntityPackageName = "com.acme.demo.domain.aggregates.video_post",
                        fieldName = "author",
                        targetEntityName = "UserProfile",
                        targetEntityPackageName = "com.acme.demo.domain.identity.user",
                        relationType = AggregateRelationType.MANY_TO_ONE,
                        joinColumn = "author_id",
                        fetchType = AggregateFetchType.LAZY,
                    )
                )
            )
        )

        val entityItem = plan.single { it.templateId == "aggregate/entity.kt.peb" }
        @Suppress("UNCHECKED_CAST")
        val scalarFields = entityItem.context["scalarFields"] as List<Map<String, Any?>>
        @Suppress("UNCHECKED_CAST")
        val fields = entityItem.context["fields"] as List<Map<String, Any?>>
        @Suppress("UNCHECKED_CAST")
        val relationFields = entityItem.context["relationFields"] as List<Map<String, Any?>>
        @Suppress("UNCHECKED_CAST")
        val imports = entityItem.context["imports"] as List<String>

        assertEquals(listOf("id", "title"), scalarFields.map { it["name"] })
        assertEquals(listOf("id", "title"), fields.map { it["name"] })
        assertEquals(listOf("author"), relationFields.map { it["name"] })
        assertEquals("MANY_TO_ONE", relationFields.single()["relationType"])
        assertEquals("UserProfile", relationFields.single()["targetType"])
        assertEquals("com.acme.demo.domain.identity.user", relationFields.single()["targetPackageName"])
        assertEquals(listOf("com.acme.demo.domain.identity.user.UserProfile"), imports)
    }

    @Test
    fun `plans schema entity and repository artifacts into domain and adapter modules`() {
        val config = ProjectConfig(
            basePackage = "com.acme.demo",
            layout = ProjectLayout.MULTI_MODULE,
            modules = mapOf(
                "domain" to "demo-domain",
                "application" to "demo-application",
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

        assertEquals(6, planItems.size)
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
        assertEquals(
            "demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/AggVideoPost.kt",
            planItems.first { it.templateId == "aggregate/wrapper.kt.peb" }.outputPath,
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
        val wrapperContext = planItems.first { it.templateId == "aggregate/wrapper.kt.peb" }.context
        assertEquals("com.acme.demo.domain.aggregates.video_post", wrapperContext["packageName"])
        assertEquals("AggVideoPost", wrapperContext["typeName"])
        assertEquals("VideoPost", wrapperContext["entityName"])
        assertEquals("com.acme.demo.domain.aggregates.video_post.VideoPost", wrapperContext["entityTypeFqn"])
        assertEquals("VideoPostFactory", wrapperContext["factoryTypeName"])
        assertEquals(
            "com.acme.demo.domain.aggregates.video_post.factory.VideoPostFactory",
            wrapperContext["factoryTypeFqn"],
        )
        assertEquals("Long", wrapperContext["idType"])
        assertEquals("Video post entity", wrapperContext["comment"])
    }

    @Test
    fun `plans unique query handler and validator artifacts from entity unique constraints`() {
        val config = ProjectConfig(
            basePackage = "com.acme.demo",
            layout = ProjectLayout.MULTI_MODULE,
            modules = mapOf(
                "domain" to "demo-domain",
                "application" to "demo-application",
                "adapter" to "demo-adapter",
            ),
            sources = emptyMap(),
            generators = mapOf("aggregate" to GeneratorConfig(enabled = true)),
            templates = TemplateConfig("ddd-default", emptyList(), ConflictPolicy.SKIP),
        )

        val model = CanonicalModel(
            entities = listOf(
                EntityModel(
                    name = "VideoPost",
                    packageName = "com.acme.demo.domain.aggregates.video_post",
                    tableName = "Video_Post",
                    comment = "Video post entity",
                    fields = listOf(
                        FieldModel("id", "Long"),
                        FieldModel("slug", "String", nullable = true),
                        FieldModel("title", "String"),
                    ),
                    idField = FieldModel("id", "Long"),
                    uniqueConstraints = listOf(listOf("slug")),
                )
            )
        )

        val planItems = AggregateArtifactPlanner().plan(config, model)

        val query = planItems.first { it.templateId == "aggregate/unique_query.kt.peb" }
        val handler = planItems.first { it.templateId == "aggregate/unique_query_handler.kt.peb" }
        val validator = planItems.first { it.templateId == "aggregate/unique_validator.kt.peb" }

        assertEquals(
            "demo-application/src/main/kotlin/com/acme/demo/application/queries/video_post/unique/UniqueVideoPostSlugQry.kt",
            query.outputPath,
        )
        assertEquals(
            "demo-adapter/src/main/kotlin/com/acme/demo/adapter/queries/video_post/unique/UniqueVideoPostSlugQryHandler.kt",
            handler.outputPath,
        )
        assertEquals(
            "demo-application/src/main/kotlin/com/acme/demo/application/validators/video_post/unique/UniqueVideoPostSlug.kt",
            validator.outputPath,
        )

        assertEquals("UniqueVideoPostSlugQry", query.context["typeName"])
        assertEquals("UniqueVideoPostSlugQryHandler", handler.context["typeName"])
        assertEquals("UniqueVideoPostSlug", validator.context["typeName"])
        assertEquals("Long", query.context["idType"])
        assertEquals("excludeVideoPostId", query.context["excludeIdParamName"])
        assertEquals(
            "com.acme.demo.application.queries.video_post.unique.UniqueVideoPostSlugQry",
            handler.context["queryTypeFqn"],
        )
        assertEquals(
            "com.acme.demo.application.queries.video_post.unique.UniqueVideoPostSlugQry",
            validator.context["queryTypeFqn"],
        )
    }

    @Test
    fun `aggregate planner emits shared enum local enum and translation artifacts with stable ownership`() {
        val planner = AggregateArtifactPlanner()
        val config = aggregateConfig()
        val model = CanonicalModel(
            sharedEnums = listOf(
                SharedEnumDefinition(
                    typeName = "Status",
                    packageName = "shared",
                    generateTranslation = true,
                    items = listOf(
                        EnumItemModel(0, "DRAFT", "Draft"),
                        EnumItemModel(1, "PUBLISHED", "Published"),
                    ),
                )
            ),
            schemas = listOf(
                SchemaModel(
                    name = "VideoPostSchema",
                    packageName = "com.acme.demo.domain._share.meta.video_post",
                    entityName = "VideoPost",
                    comment = "video post schema",
                    fields = listOf(
                        FieldModel(name = "status", type = "Int", typeBinding = "Status"),
                        FieldModel(
                            name = "visibility",
                            type = "Int",
                            typeBinding = "VideoPostVisibility",
                            enumItems = listOf(EnumItemModel(0, "HIDDEN", "Hidden")),
                        ),
                    ),
                )
            ),
            entities = listOf(
                EntityModel(
                    name = "VideoPost",
                    packageName = "com.acme.demo.domain.aggregates.video_post",
                    tableName = "video_post",
                    comment = "video post",
                    fields = listOf(
                        FieldModel(name = "status", type = "Int", typeBinding = "Status"),
                        FieldModel(
                            name = "visibility",
                            type = "Int",
                            typeBinding = "VideoPostVisibility",
                            enumItems = listOf(EnumItemModel(0, "HIDDEN", "Hidden")),
                        ),
                    ),
                    idField = FieldModel(name = "id", type = "Long"),
                )
            ),
            repositories = listOf(
                RepositoryModel(
                    name = "VideoPostRepository",
                    packageName = "com.acme.demo.domain.aggregates.video_post.repo",
                    entityName = "VideoPost",
                    idType = "Long",
                )
            )
        )

        val items = planner.plan(config, model)

        assertTrue(items.any { it.templateId == "aggregate/enum.kt.peb" && it.outputPath.endsWith("/domain/shared/enums/Status.kt") })
        assertTrue(items.any { it.templateId == "aggregate/enum.kt.peb" && it.outputPath.endsWith("/domain/aggregates/video_post/enums/VideoPostVisibility.kt") })
        assertTrue(items.any { it.templateId == "aggregate/enum_translation.kt.peb" && it.outputPath.endsWith("/domain/translation/shared/StatusTranslation.kt") })
        assertTrue(items.any { it.templateId == "aggregate/enum_translation.kt.peb" && it.outputPath.endsWith("/domain/translation/video_post/VideoPostVisibilityTranslation.kt") })
        val sharedTranslationPlan = items.single {
            it.templateId == "aggregate/enum_translation.kt.peb" &&
                it.outputPath.endsWith("/domain/translation/shared/StatusTranslation.kt")
        }
        val localTranslationPlan = items.single {
            it.templateId == "aggregate/enum_translation.kt.peb" &&
                it.outputPath.endsWith("/domain/translation/video_post/VideoPostVisibilityTranslation.kt")
        }

        val entityPlan = items.single { it.templateId == "aggregate/entity.kt.peb" }
        val schemaPlan = items.single { it.templateId == "aggregate/schema.kt.peb" }
        @Suppress("UNCHECKED_CAST")
        val entityFields = entityPlan.context.getValue("scalarFields") as List<Map<String, Any?>>
        @Suppress("UNCHECKED_CAST")
        val schemaFields = schemaPlan.context.getValue("fields") as List<Map<String, Any?>>
        assertEquals("com.acme.demo.domain.shared.enums.Status", entityFields.single { it["name"] == "status" }["type"])
        assertEquals(
            "com.acme.demo.domain.aggregates.video_post.enums.VideoPostVisibility",
            entityFields.single { it["name"] == "visibility" }["type"]
        )
        assertEquals("com.acme.demo.domain.shared.enums.Status", schemaFields.single { it["name"] == "status" }["type"])
        assertEquals(
            "com.acme.demo.domain.aggregates.video_post.enums.VideoPostVisibility",
            schemaFields.single { it["name"] == "visibility" }["type"]
        )
        assertEquals("STATUS_CODE_TO_DESC", sharedTranslationPlan.context["translationTypeConst"])
        assertEquals("status_code_to_desc", sharedTranslationPlan.context["translationTypeValue"])
        assertEquals("VIDEO_POST_VIDEO_POST_VISIBILITY_CODE_TO_DESC", localTranslationPlan.context["translationTypeConst"])
        assertEquals("video_post_video_post_visibility_code_to_desc", localTranslationPlan.context["translationTypeValue"])
    }

    @Test
    fun `local enum translations under different aggregate owners use different translation keys`() {
        val planner = AggregateArtifactPlanner()
        val config = aggregateConfig()
        val model = CanonicalModel(
            entities = listOf(
                EntityModel(
                    name = "VideoPost",
                    packageName = "com.acme.demo.domain.aggregates.video_post",
                    tableName = "video_post",
                    comment = "video post",
                    fields = listOf(
                        FieldModel(
                            name = "visibility",
                            type = "Int",
                            typeBinding = "Visibility",
                            enumItems = listOf(EnumItemModel(0, "HIDDEN", "Hidden")),
                        ),
                    ),
                    idField = FieldModel(name = "id", type = "Long"),
                ),
                EntityModel(
                    name = "ArticlePost",
                    packageName = "com.acme.demo.domain.aggregates.article_post",
                    tableName = "article_post",
                    comment = "article post",
                    fields = listOf(
                        FieldModel(
                            name = "visibility",
                            type = "Int",
                            typeBinding = "Visibility",
                            enumItems = listOf(EnumItemModel(1, "PUBLIC", "Public")),
                        ),
                    ),
                    idField = FieldModel(name = "id", type = "Long"),
                ),
            ),
        )

        val items = planner.plan(config, model)
        val videoTranslation = items.single {
            it.templateId == "aggregate/enum_translation.kt.peb" &&
                it.outputPath.endsWith("/domain/translation/video_post/VisibilityTranslation.kt")
        }
        val articleTranslation = items.single {
            it.templateId == "aggregate/enum_translation.kt.peb" &&
                it.outputPath.endsWith("/domain/translation/article_post/VisibilityTranslation.kt")
        }

        assertNotEquals(
            videoTranslation.context["translationTypeConst"],
            articleTranslation.context["translationTypeConst"],
        )
        assertNotEquals(
            videoTranslation.context["translationTypeValue"],
            articleTranslation.context["translationTypeValue"],
        )
        assertEquals("VIDEO_POST_VISIBILITY_CODE_TO_DESC", videoTranslation.context["translationTypeConst"])
        assertEquals("video_post_visibility_code_to_desc", videoTranslation.context["translationTypeValue"])
        assertEquals("ARTICLE_POST_VISIBILITY_CODE_TO_DESC", articleTranslation.context["translationTypeConst"])
        assertEquals("article_post_visibility_code_to_desc", articleTranslation.context["translationTypeValue"])
    }

    @Test
    fun `shared enum planning stays stable when no entities are present`() {
        val planner = AggregateArtifactPlanner()
        val config = aggregateConfig()
        val items = planner.plan(
            config,
            CanonicalModel(
                sharedEnums = listOf(
                    SharedEnumDefinition(
                        typeName = "Status",
                        packageName = "shared",
                        generateTranslation = true,
                        items = listOf(
                            EnumItemModel(0, "DRAFT", "Draft"),
                            EnumItemModel(1, "PUBLISHED", "Published"),
                        ),
                    )
                )
            )
        )

        val enumPlan = items.single { it.templateId == "aggregate/enum.kt.peb" }
        val translationPlan = items.single { it.templateId == "aggregate/enum_translation.kt.peb" }

        assertEquals(
            "demo-domain/src/main/kotlin/com/acme/demo/domain/shared/enums/Status.kt",
            enumPlan.outputPath,
        )
        assertEquals("com.acme.demo.domain.shared.enums", enumPlan.context["packageName"])
        assertEquals(
            "demo-adapter/src/main/kotlin/com/acme/demo/domain/translation/shared/StatusTranslation.kt",
            translationPlan.outputPath,
        )
        assertEquals("com.acme.demo.domain.translation.shared", translationPlan.context["packageName"])
    }

    @Test
    fun `shared unique planning normalizes raw constraint identifiers while preserving composite order and family names`() {
        val planning = AggregateUniqueConstraintPlanning.from(
            entity = EntityModel(
                name = "VideoPost",
                packageName = "com.acme.demo.domain.aggregates.video_post",
                tableName = "video_post",
                comment = "Video post entity",
                fields = listOf(
                    FieldModel("id", "Long"),
                    FieldModel("tenant_id", "Long"),
                    FieldModel("slug", "String"),
                ),
                idField = FieldModel("id", "Long"),
                uniqueConstraints = listOf(listOf("tenant_id", "slug")),
            )
        )

        val selection = planning.single()
        assertEquals("TenantIdSlug", selection.suffix)
        assertEquals(listOf("tenantId", "slug"), selection.requestProps.map { it.name })
        assertEquals("UniqueVideoPostTenantIdSlugQry", selection.queryTypeName)
        assertEquals("UniqueVideoPostTenantIdSlugQryHandler", selection.queryHandlerTypeName)
        assertEquals("UniqueVideoPostTenantIdSlug", selection.validatorTypeName)
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
        val primaryWrapperContext = planItems.first {
            it.templateId == "aggregate/wrapper.kt.peb" &&
                it.context["packageName"] == "com.acme.demo.domain.aggregates.primary_video_post"
        }.context
        val secondaryWrapperContext = planItems.first {
            it.templateId == "aggregate/wrapper.kt.peb" &&
                it.context["packageName"] == "com.acme.demo.domain.aggregates.secondary_video_post"
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
        assertEquals(
            "com.acme.demo.domain.aggregates.primary_video_post.VideoPost",
            primaryWrapperContext["entityTypeFqn"],
        )
        assertEquals(
            "com.acme.demo.domain.aggregates.primary_video_post.factory.VideoPostFactory",
            primaryWrapperContext["factoryTypeFqn"],
        )
        assertEquals(
            "com.acme.demo.domain.aggregates.secondary_video_post.VideoPost",
            secondaryWrapperContext["entityTypeFqn"],
        )
        assertEquals(
            "com.acme.demo.domain.aggregates.secondary_video_post.factory.VideoPostFactory",
            secondaryWrapperContext["factoryTypeFqn"],
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
        applicationModule: String? = "demo-application",
        adapterModule: String? = "demo-adapter",
    ): ProjectConfig =
        ProjectConfig(
            basePackage = "com.acme.demo",
            layout = ProjectLayout.MULTI_MODULE,
            modules = buildMap {
                domainModule?.let { put("domain", it) }
                applicationModule?.let { put("application", it) }
                adapterModule?.let { put("adapter", it) }
            },
            sources = emptyMap(),
            generators = mapOf("aggregate" to GeneratorConfig(enabled = true)),
            templates = TemplateConfig("ddd-default", emptyList(), ConflictPolicy.SKIP),
        )
}

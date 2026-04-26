package com.only4.cap4k.plugin.pipeline.generator.aggregate

import com.only4.cap4k.plugin.pipeline.api.AggregateFetchType
import com.only4.cap4k.plugin.pipeline.api.AggregateColumnJpaModel
import com.only4.cap4k.plugin.pipeline.api.AggregateEntityJpaModel
import com.only4.cap4k.plugin.pipeline.api.AggregateInverseRelationModel
import com.only4.cap4k.plugin.pipeline.api.AggregateIdGeneratorControl
import com.only4.cap4k.plugin.pipeline.api.AggregatePersistenceFieldControl
import com.only4.cap4k.plugin.pipeline.api.AggregateRelationModel
import com.only4.cap4k.plugin.pipeline.api.AggregateRelationType
import com.only4.cap4k.plugin.pipeline.api.ArtifactLayoutConfig
import com.only4.cap4k.plugin.pipeline.api.CanonicalModel
import com.only4.cap4k.plugin.pipeline.api.ConflictPolicy
import com.only4.cap4k.plugin.pipeline.api.EntityModel
import com.only4.cap4k.plugin.pipeline.api.EnumItemModel
import com.only4.cap4k.plugin.pipeline.api.FieldModel
import com.only4.cap4k.plugin.pipeline.api.GeneratorConfig
import com.only4.cap4k.plugin.pipeline.api.PackageLayout
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig
import com.only4.cap4k.plugin.pipeline.api.ProjectLayout
import com.only4.cap4k.plugin.pipeline.api.RepositoryModel
import com.only4.cap4k.plugin.pipeline.api.SchemaModel
import com.only4.cap4k.plugin.pipeline.api.SharedEnumDefinition
import com.only4.cap4k.plugin.pipeline.api.TemplateConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Path

class AggregateArtifactPlannerTest {

    @Test
    fun `aggregate planner keeps fixed baseline families when optional artifacts are disabled`() {
        val entity = EntityModel(
            name = "UserMessage",
            packageName = "com.acme.demo.domain.aggregates.user_message",
            tableName = "user_message",
            comment = "user message",
            fields = listOf(
                FieldModel("id", "Long", columnName = "id"),
                FieldModel("tenantId", "Long", columnName = "tenant_id"),
                FieldModel("slug", "String", columnName = "slug"),
            ),
            idField = FieldModel("id", "Long", columnName = "id"),
            uniqueConstraints = listOf(listOf("tenant_id", "slug")),
        )
        val plan = AggregateArtifactPlanner().plan(
            aggregateConfig(
                options = mapOf(
                    "artifact.factory" to false,
                    "artifact.specification" to false,
                    "artifact.wrapper" to false,
                    "artifact.unique" to false,
                    "artifact.enumTranslation" to false,
                )
            ),
            CanonicalModel(
                entities = listOf(entity),
                schemas = listOf(
                    SchemaModel(
                        name = "SUserMessage",
                        packageName = "com.acme.demo.domain._share.meta.user_message",
                        entityName = "UserMessage",
                        comment = "user message",
                        fields = entity.fields,
                    )
                ),
                repositories = listOf(
                    RepositoryModel(
                        name = "UserMessageRepository",
                        packageName = "com.acme.demo.adapter.domain.repositories",
                        entityName = "UserMessage",
                        idType = "Long",
                    )
                ),
                aggregateEntityJpa = listOf(defaultAggregateEntityJpa(entity)),
            )
        )

        assertTrue(plan.any { it.templateId == "aggregate/entity.kt.peb" })
        assertTrue(plan.any { it.templateId == "aggregate/schema.kt.peb" })
        assertTrue(plan.any { it.templateId == "aggregate/repository.kt.peb" })
        assertFalse(plan.any { it.templateId == "aggregate/factory.kt.peb" })
        assertFalse(plan.any { it.templateId == "aggregate/specification.kt.peb" })
        assertFalse(plan.any { it.templateId == "aggregate/wrapper.kt.peb" })
        assertFalse(plan.any { it.templateId == "aggregate/unique_query.kt.peb" })
        assertFalse(plan.any { it.templateId == "aggregate/unique_query_handler.kt.peb" })
        assertFalse(plan.any { it.templateId == "aggregate/unique_validator.kt.peb" })
        assertFalse(plan.any { it.templateId == "aggregate/enum_translation.kt.peb" })
    }

    @Test
    fun `aggregate planner expands unique capability into three concrete artifact items`() {
        val entity = EntityModel(
            name = "UserMessage",
            packageName = "com.acme.demo.domain.aggregates.user_message",
            tableName = "user_message",
            comment = "user message",
            fields = listOf(
                FieldModel("id", "Long", columnName = "id"),
                FieldModel("tenantId", "Long", columnName = "tenant_id"),
                FieldModel("slug", "String", columnName = "slug"),
            ),
            idField = FieldModel("id", "Long", columnName = "id"),
            uniqueConstraints = listOf(listOf("tenant_id", "slug")),
        )
        val plan = AggregateArtifactPlanner().plan(
            aggregateConfig(
                options = mapOf(
                    "artifact.factory" to false,
                    "artifact.specification" to false,
                    "artifact.wrapper" to false,
                    "artifact.unique" to true,
                    "artifact.enumTranslation" to false,
                )
            ),
            CanonicalModel(
                entities = listOf(entity),
                schemas = listOf(
                    SchemaModel(
                        name = "SUserMessage",
                        packageName = "com.acme.demo.domain._share.meta.user_message",
                        entityName = "UserMessage",
                        comment = "user message",
                        fields = entity.fields,
                    )
                ),
                repositories = listOf(
                    RepositoryModel(
                        name = "UserMessageRepository",
                        packageName = "com.acme.demo.adapter.domain.repositories",
                        entityName = "UserMessage",
                        idType = "Long",
                    )
                ),
                aggregateEntityJpa = listOf(defaultAggregateEntityJpa(entity)),
            )
        )

        assertEquals(1, plan.count { it.templateId == "aggregate/unique_query.kt.peb" })
        assertEquals(1, plan.count { it.templateId == "aggregate/unique_query_handler.kt.peb" })
        assertEquals(1, plan.count { it.templateId == "aggregate/unique_validator.kt.peb" })
        assertFalse(plan.any { it.templateId == "aggregate/factory.kt.peb" })
        assertFalse(plan.any { it.templateId == "aggregate/specification.kt.peb" })
        assertFalse(plan.any { it.templateId == "aggregate/wrapper.kt.peb" })
    }

    @Test
    fun `aggregate planner routes custom canonical layout through artifact layout`() {
        val artifactLayout = ArtifactLayoutConfig(
            aggregate = PackageLayout("domain.model"),
            aggregateSchema = PackageLayout("domain.meta"),
            aggregateRepository = PackageLayout("adapter.persistence.repositories"),
            aggregateUniqueQuery = PackageLayout("application.readmodels", packageSuffix = "unique"),
            aggregateUniqueQueryHandler = PackageLayout("adapter.readmodels", packageSuffix = "unique"),
            aggregateUniqueValidator = PackageLayout("application.rules", packageSuffix = "unique"),
        )
        val entity = EntityModel(
            name = "UserMessage",
            packageName = "com.acme.demo.domain.model.user_message",
            tableName = "user_message",
            comment = "user message",
            fields = listOf(
                FieldModel("id", "Long", columnName = "id"),
                FieldModel("tenantId", "Long", columnName = "tenant_id"),
                FieldModel("slug", "String", columnName = "slug"),
            ),
            idField = FieldModel("id", "Long", columnName = "id"),
            uniqueConstraints = listOf(listOf("tenant_id", "slug")),
        )
        val schema = SchemaModel(
            name = "SUserMessage",
            packageName = "com.acme.demo.domain.meta.user_message",
            entityName = "UserMessage",
            comment = "user message",
            fields = entity.fields,
        )
        val repository = RepositoryModel(
            name = "UserMessageRepository",
            packageName = "com.acme.demo.adapter.persistence.repositories",
            entityName = "UserMessage",
            idType = "Long",
        )

        val plan = AggregateArtifactPlanner().plan(
            aggregateConfig(artifactLayout = artifactLayout),
            CanonicalModel(
                entities = listOf(entity),
                schemas = listOf(schema),
                repositories = listOf(repository),
                aggregateEntityJpa = listOf(defaultAggregateEntityJpa(entity)),
            )
        )

        val entityItem = plan.single { it.templateId == "aggregate/entity.kt.peb" }
        val factory = plan.single { it.templateId == "aggregate/factory.kt.peb" }
        val specification = plan.single { it.templateId == "aggregate/specification.kt.peb" }
        val repositoryItem = plan.single { it.templateId == "aggregate/repository.kt.peb" }
        val schemaItem = plan.single { it.templateId == "aggregate/schema.kt.peb" }
        val uniqueQuery = plan.single { it.templateId == "aggregate/unique_query.kt.peb" }
        val uniqueQueryHandler = plan.single { it.templateId == "aggregate/unique_query_handler.kt.peb" }
        val uniqueValidator = plan.single { it.templateId == "aggregate/unique_validator.kt.peb" }

        assertFalse(plan.any { it.templateId == "aggregate/schema_base.kt.peb" })
        assertEquals(
            "demo-domain/src/main/kotlin/com/acme/demo/domain/model/user_message/UserMessage.kt",
            entityItem.outputPath,
        )
        assertEquals(
            "demo-domain/src/main/kotlin/com/acme/demo/domain/model/user_message/factory/UserMessageFactory.kt",
            factory.outputPath,
        )
        assertEquals(
            "demo-domain/src/main/kotlin/com/acme/demo/domain/model/user_message/specification/UserMessageSpecification.kt",
            specification.outputPath,
        )
        assertEquals(
            "demo-adapter/src/main/kotlin/com/acme/demo/adapter/persistence/repositories/UserMessageRepository.kt",
            repositoryItem.outputPath,
        )
        assertEquals("com.only4.cap4k.ddd.domain.repo.schema", schemaItem.context["schemaRuntimePackage"])
        assertEquals(false, schemaItem.context.containsKey("schemaBasePackage"))
        assertEquals("com.acme.demo.domain.model.user_message", entityItem.context["packageName"])
        assertEquals("com.acme.demo.domain.model.user_message.factory", factory.context["packageName"])
        assertEquals(
            "com.acme.demo.domain.model.user_message.specification",
            specification.context["packageName"],
        )
        assertEquals("com.acme.demo.adapter.persistence.repositories", repositoryItem.context["packageName"])
        assertEquals(
            "demo-application/src/main/kotlin/com/acme/demo/application/readmodels/user_message/unique/UniqueUserMessageTenantIdSlugQry.kt",
            uniqueQuery.outputPath,
        )
        assertEquals(
            "demo-adapter/src/main/kotlin/com/acme/demo/adapter/readmodels/user_message/unique/UniqueUserMessageTenantIdSlugQryHandler.kt",
            uniqueQueryHandler.outputPath,
        )
        assertEquals(
            "demo-application/src/main/kotlin/com/acme/demo/application/rules/user_message/unique/UniqueUserMessageTenantIdSlug.kt",
            uniqueValidator.outputPath,
        )
        assertEquals("com.acme.demo.application.readmodels.user_message.unique", uniqueQuery.context["packageName"])
        assertEquals("com.acme.demo.adapter.readmodels.user_message.unique", uniqueQueryHandler.context["packageName"])
        assertEquals("com.acme.demo.application.rules.user_message.unique", uniqueValidator.context["packageName"])
        assertEquals(
            "com.acme.demo.application.readmodels.user_message.unique.UniqueUserMessageTenantIdSlugQry",
            uniqueQueryHandler.context["queryTypeFqn"],
        )
        assertEquals(
            "com.acme.demo.application.readmodels.user_message.unique.UniqueUserMessageTenantIdSlugQry",
            uniqueValidator.context["queryTypeFqn"],
        )
    }

    @Test
    fun `aggregate planner uses framework schema runtime instead of project schema base artifact`() {
        val entity = EntityModel(
            name = "UserMessage",
            packageName = "com.acme.demo.domain.aggregates.user_message",
            tableName = "user_message",
            comment = "user message",
            fields = listOf(FieldModel("id", "Long", columnName = "id")),
            idField = FieldModel("id", "Long", columnName = "id"),
        )
        val plan = AggregateArtifactPlanner().plan(
            aggregateConfig(),
            CanonicalModel(
                entities = listOf(entity),
                schemas = listOf(
                    SchemaModel(
                        name = "SUserMessage",
                        packageName = "com.acme.demo.domain._share.meta.user_message",
                        entityName = "UserMessage",
                        comment = "user message",
                        fields = entity.fields,
                    )
                ),
                aggregateEntityJpa = listOf(defaultAggregateEntityJpa(entity)),
            )
        )

        val schema = plan.single { it.templateId == "aggregate/schema.kt.peb" }

        assertFalse(plan.any { it.templateId == "aggregate/schema_base.kt.peb" })
        assertEquals("com.only4.cap4k.ddd.domain.repo.schema", schema.context["schemaRuntimePackage"])
        assertEquals(false, schema.context.containsKey("schemaBasePackage"))
        assertEquals(
            "com.acme.demo.domain.aggregates.user_message.UserMessage",
            schema.context["entityTypeFqn"],
        )
        assertEquals(
            "com.acme.demo.domain.aggregates.user_message.AggUserMessage",
            schema.context["aggregateTypeFqn"],
        )
        assertEquals(true, schema.context["wrapperEnabled"])
        assertEquals(false, schema.context["repositorySupportQuerydsl"])
    }

    @Test
    fun `schema planner disables wrapper dependent render model when wrapper artifact is disabled`() {
        val entity = EntityModel(
            name = "UserMessage",
            packageName = "com.acme.demo.domain.aggregates.user_message",
            tableName = "user_message",
            comment = "user message",
            fields = listOf(FieldModel("id", "Long", columnName = "id")),
            idField = FieldModel("id", "Long", columnName = "id"),
        )
        val plan = AggregateArtifactPlanner().plan(
            aggregateConfig(
                options = mapOf(
                    "artifact.factory" to false,
                    "artifact.specification" to false,
                    "artifact.wrapper" to false,
                    "artifact.unique" to false,
                    "artifact.enumTranslation" to false,
                )
            ),
            CanonicalModel(
                entities = listOf(entity),
                schemas = listOf(
                    SchemaModel(
                        name = "SUserMessage",
                        packageName = "com.acme.demo.domain._share.meta.user_message",
                        entityName = "UserMessage",
                        comment = "user message",
                        fields = entity.fields,
                    )
                ),
                aggregateEntityJpa = listOf(defaultAggregateEntityJpa(entity)),
            )
        )

        val schema = plan.single { it.templateId == "aggregate/schema.kt.peb" }

        assertEquals(false, schema.context["wrapperEnabled"])
        assertEquals("", schema.context["aggregateTypeFqn"])
    }

    @Test
    fun `repository planner exposes JPA repository adapter render model`() {
        val entity = EntityModel(
            name = "UserMessage",
            packageName = "com.acme.demo.domain.aggregates.user_message",
            tableName = "user_message",
            comment = "user message",
            fields = listOf(FieldModel("id", "Long", columnName = "id")),
            idField = FieldModel("id", "Long", columnName = "id"),
        )
        val plan = RepositoryArtifactPlanner().plan(
            aggregateConfig(),
            CanonicalModel(
                entities = listOf(entity),
                repositories = listOf(
                    RepositoryModel(
                        name = "UserMessageRepository",
                        packageName = "com.acme.demo.adapter.domain.repositories",
                        entityName = "UserMessage",
                        idType = "Long",
                    )
                ),
            )
        )

        val repository = plan.single()

        assertEquals("UserMessageRepository", repository.context["typeName"])
        assertEquals("UserMessage", repository.context["entityName"])
        assertEquals(
            "com.acme.demo.domain.aggregates.user_message.UserMessage",
            repository.context["entityTypeFqn"],
        )
        assertEquals("Long", repository.context["idType"])
        assertEquals(false, repository.context["supportQuerydsl"])
    }

    @Test
    fun `repository planner fails fast when repository entity is missing`() {
        val ex = assertThrows(IllegalStateException::class.java) {
            RepositoryArtifactPlanner().plan(
                aggregateConfig(),
                CanonicalModel(
                    repositories = listOf(
                        RepositoryModel(
                            name = "UserMessageRepository",
                            packageName = "com.acme.demo.adapter.domain.repositories",
                            entityName = "UserMessage",
                            idType = "Long",
                        )
                    ),
                )
            )
        }

        assertEquals(
            "repository UserMessageRepository requires exactly one entity named UserMessage, but found 0",
            ex.message,
        )
    }

    @Test
    fun `repository planner fails fast when repository entity is ambiguous`() {
        val ex = assertThrows(IllegalStateException::class.java) {
            RepositoryArtifactPlanner().plan(
                aggregateConfig(),
                CanonicalModel(
                    entities = listOf(
                        EntityModel(
                            name = "UserMessage",
                            packageName = "com.acme.demo.domain.aggregates.primary_user_message",
                            tableName = "primary_user_message",
                            comment = "primary user message",
                            fields = listOf(FieldModel("id", "Long")),
                            idField = FieldModel("id", "Long"),
                        ),
                        EntityModel(
                            name = "UserMessage",
                            packageName = "com.acme.demo.domain.aggregates.secondary_user_message",
                            tableName = "secondary_user_message",
                            comment = "secondary user message",
                            fields = listOf(FieldModel("id", "Long")),
                            idField = FieldModel("id", "Long"),
                        ),
                    ),
                    repositories = listOf(
                        RepositoryModel(
                            name = "UserMessageRepository",
                            packageName = "com.acme.demo.adapter.domain.repositories",
                            entityName = "UserMessage",
                            idType = "Long",
                        )
                    ),
                )
            )
        }

        assertEquals(
            "repository UserMessageRepository requires exactly one entity named UserMessage, but found 2",
            ex.message,
        )
    }

    @Test
    fun `entity planner surfaces bounded aggregate JPA metadata`() {
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
                            FieldModel("status", "Status"),
                        ),
                        idField = FieldModel("id", "Long"),
                    )
                ),
                aggregateEntityJpa = listOf(
                    AggregateEntityJpaModel(
                        entityName = "VideoPost",
                        entityPackageName = "com.acme.demo.domain.aggregates.video_post",
                        entityEnabled = false,
                        tableName = "video_post_override",
                        columns = listOf(
                            AggregateColumnJpaModel("id", "id", true, null),
                            AggregateColumnJpaModel("title", "title", false, null),
                            AggregateColumnJpaModel(
                                "status",
                                "status",
                                false,
                                "com.acme.demo.domain.shared.enums.Status",
                            ),
                        ),
                    )
                )
            )
        )

        val entityItem = plan.single { it.templateId == "aggregate/entity.kt.peb" }
        @Suppress("UNCHECKED_CAST")
        val entityJpa = entityItem.context["entityJpa"] as Map<String, Any?>
        @Suppress("UNCHECKED_CAST")
        val scalarFields = entityItem.context["scalarFields"] as List<Map<String, Any?>>

        assertEquals(false, entityJpa["entityEnabled"])
        assertEquals("video_post_override", entityJpa["tableName"])
        assertEquals(true, entityItem.context["hasConverterFields"])
        assertEquals(true, scalarFields.single { it["name"] == "id" }["isId"])
        assertEquals("id", scalarFields.single { it["name"] == "id" }["columnName"])
        assertEquals(
            "com.acme.demo.domain.shared.enums.Status",
            scalarFields.single { it["name"] == "status" }["converterTypeRef"]
        )
    }

    @Test
    fun `entity planner fails fast when scalar aggregate JPA metadata is missing`() {
        val ex = assertThrows(IllegalArgumentException::class.java) {
            AggregateArtifactPlanner().plan(
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
                    aggregateEntityJpa = listOf(
                        AggregateEntityJpaModel(
                            entityName = "VideoPost",
                            entityPackageName = "com.acme.demo.domain.aggregates.video_post",
                            entityEnabled = true,
                            tableName = "video_post",
                            columns = listOf(
                                AggregateColumnJpaModel("id", "id", true, null),
                            ),
                        )
                    )
                )
            )
        }

        assertEquals(
            "missing aggregate JPA metadata for com.acme.demo.domain.aggregates.video_post.VideoPost.title",
            ex.message,
        )
    }

    @Test
    fun `entity planner exposes explicit persistence field behavior in render model`() {
        val entity = EntityModel(
            name = "VideoPost",
            packageName = "com.acme.demo.domain.aggregates.video_post",
            tableName = "video_post",
            comment = "video post",
            fields = listOf(
                FieldModel("id", "Long"),
                FieldModel("version", "Long"),
                FieldModel("created_by", "String"),
                FieldModel("updated_by", "String"),
            ),
            idField = FieldModel("id", "Long"),
        )
        val plan = AggregateArtifactPlanner().plan(
            aggregateConfig(),
            CanonicalModel(
                entities = listOf(entity),
                aggregateEntityJpa = listOf(
                    defaultAggregateEntityJpa(entity)
                ),
                aggregatePersistenceFieldControls = listOf(
                    AggregatePersistenceFieldControl(
                        "VideoPost",
                        "com.acme.demo.domain.aggregates.video_post",
                        "id",
                        "id",
                        generatedValueStrategy = "IDENTITY",
                    ),
                    AggregatePersistenceFieldControl(
                        "VideoPost",
                        "com.acme.demo.domain.aggregates.video_post",
                        "version",
                        "version",
                        version = true,
                    ),
                    AggregatePersistenceFieldControl(
                        "VideoPost",
                        "com.acme.demo.domain.aggregates.video_post",
                        "created_by",
                        "created_by",
                        insertable = false,
                    ),
                    AggregatePersistenceFieldControl(
                        "VideoPost",
                        "com.acme.demo.domain.aggregates.video_post",
                        "updated_by",
                        "updated_by",
                        updatable = false,
                    ),
                ),
            )
        )

        val entityArtifact = plan.single { it.outputPath.endsWith("/VideoPost.kt") }
        @Suppress("UNCHECKED_CAST")
        val scalarFields = entityArtifact.context["fields"] as List<Map<String, Any?>>

        assertEquals(true, entityArtifact.context["hasGeneratedValueFields"])
        assertEquals(true, entityArtifact.context["hasVersionFields"])
        assertEquals("IDENTITY", scalarFields.single { it["fieldName"] == "id" }["generatedValueStrategy"])
        assertEquals(true, scalarFields.single { it["fieldName"] == "version" }["isVersion"])
        assertEquals(false, scalarFields.single { it["fieldName"] == "created_by" }["insertable"])
        assertEquals(true, scalarFields.single { it["fieldName"] == "created_by" }["updatable"])
        assertEquals(true, scalarFields.single { it["fieldName"] == "updated_by" }["insertable"])
        assertEquals(false, scalarFields.single { it["fieldName"] == "updated_by" }["updatable"])
    }

    @Test
    fun `entity planner exposes custom generator render keys on id field`() {
        val entity = EntityModel(
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
        val plan = AggregateArtifactPlanner().plan(
            aggregateConfig(),
            CanonicalModel(
                entities = listOf(entity),
                aggregateEntityJpa = listOf(
                    defaultAggregateEntityJpa(entity)
                ),
                aggregateIdGeneratorControls = listOf(
                    AggregateIdGeneratorControl(
                        entityName = "VideoPost",
                        entityPackageName = "com.acme.demo.domain.aggregates.video_post",
                        tableName = "video_post",
                        idFieldName = "id",
                        entityIdGenerator = "snowflakeIdGenerator",
                    )
                ),
            )
        )

        val entityArtifact = plan.single { it.outputPath.endsWith("/VideoPost.kt") }
        @Suppress("UNCHECKED_CAST")
        val scalarFields = entityArtifact.context["fields"] as List<Map<String, Any?>>
        val idField = scalarFields.single { it["fieldName"] == "id" }

        assertEquals("snowflakeIdGenerator", idField["generatedValueGenerator"])
        assertEquals("snowflakeIdGenerator", idField["genericGeneratorName"])
        assertEquals("snowflakeIdGenerator", idField["genericGeneratorStrategy"])
        assertEquals(false, entityArtifact.context["hasGeneratedValueFields"])
        assertEquals(true, entityArtifact.context["hasGenericGeneratorFields"])
    }

    @Test
    fun `entity planner clears identity strategy when custom generator control is present on id field`() {
        val entity = EntityModel(
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
        val plan = AggregateArtifactPlanner().plan(
            aggregateConfig(),
            CanonicalModel(
                entities = listOf(entity),
                aggregateEntityJpa = listOf(
                    defaultAggregateEntityJpa(entity)
                ),
                aggregatePersistenceFieldControls = listOf(
                    AggregatePersistenceFieldControl(
                        "VideoPost",
                        "com.acme.demo.domain.aggregates.video_post",
                        "id",
                        "id",
                        generatedValueStrategy = "IDENTITY",
                    )
                ),
                aggregateIdGeneratorControls = listOf(
                    AggregateIdGeneratorControl(
                        entityName = "VideoPost",
                        entityPackageName = "com.acme.demo.domain.aggregates.video_post",
                        tableName = "video_post",
                        idFieldName = "id",
                        entityIdGenerator = "snowflakeIdGenerator",
                    )
                ),
            )
        )

        val entityArtifact = plan.single { it.outputPath.endsWith("/VideoPost.kt") }
        @Suppress("UNCHECKED_CAST")
        val scalarFields = entityArtifact.context["fields"] as List<Map<String, Any?>>
        val idField = scalarFields.single { it["fieldName"] == "id" }

        assertEquals(null, idField["generatedValueStrategy"])
        assertEquals("snowflakeIdGenerator", idField["generatedValueGenerator"])
        assertEquals("snowflakeIdGenerator", idField["genericGeneratorName"])
        assertEquals("snowflakeIdGenerator", idField["genericGeneratorStrategy"])
        assertEquals(false, entityArtifact.context["hasGeneratedValueFields"])
        assertEquals(true, entityArtifact.context["hasGenericGeneratorFields"])
    }

    @Test
    fun `entity planner excludes relation backed join columns from scalar JPA fields while preserving fields alias`() {
        val entity = EntityModel(
            name = "VideoPost",
            packageName = "com.acme.demo.domain.aggregates.video_post",
            tableName = "video_post",
            comment = "video post",
            fields = listOf(
                FieldModel("id", "Long"),
                FieldModel("title", "String"),
                FieldModel("author_id", "Long", nullable = true),
            ),
            idField = FieldModel("id", "Long"),
        )
        val plan = AggregateArtifactPlanner().plan(
            aggregateConfig(),
            CanonicalModel(
                entities = listOf(entity),
                aggregateEntityJpa = listOf(
                    defaultAggregateEntityJpa(entity)
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
                        nullable = false,
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
        @Suppress("UNCHECKED_CAST")
        val jpaImports = entityItem.context["jpaImports"] as List<String>

        assertEquals(listOf("id", "title"), scalarFields.map { it["name"] })
        assertEquals(listOf("id", "title"), fields.map { it["name"] })
        assertEquals(listOf("author"), relationFields.map { it["name"] })
        assertEquals("MANY_TO_ONE", relationFields.single()["relationType"])
        assertEquals("author_id", relationFields.single()["joinColumn"])
        assertEquals("UserProfile", relationFields.single()["targetType"])
        assertEquals("com.acme.demo.domain.identity.user", relationFields.single()["targetPackageName"])
        assertEquals(false, relationFields.single()["nullable"])
        assertEquals(listOf("com.acme.demo.domain.identity.user.UserProfile"), imports)
        assertEquals(
            listOf(
                "jakarta.persistence.FetchType",
                "jakarta.persistence.JoinColumn",
                "jakarta.persistence.ManyToOne",
            ),
            jpaImports,
        )
        assertEquals(false, entityItem.context["hasConverterFields"])
    }

    @Test
    fun `entity planner excludes relation backed scalar fields when field name differs from JPA column name`() {
        val entity = EntityModel(
            name = "VideoPost",
            packageName = "com.acme.demo.domain.aggregates.video_post",
            tableName = "video_post",
            comment = "video post",
            fields = listOf(
                FieldModel("id", "Long"),
                FieldModel("title", "String"),
                FieldModel("authorId", "Long", nullable = true),
            ),
            idField = FieldModel("id", "Long"),
        )
        val plan = AggregateArtifactPlanner().plan(
            aggregateConfig(),
            CanonicalModel(
                entities = listOf(entity),
                aggregateEntityJpa = listOf(
                    AggregateEntityJpaModel(
                        entityName = entity.name,
                        entityPackageName = entity.packageName,
                        entityEnabled = true,
                        tableName = entity.tableName,
                        columns = listOf(
                            AggregateColumnJpaModel("id", "id", true, null),
                            AggregateColumnJpaModel("title", "title", false, null),
                            AggregateColumnJpaModel("authorId", "author_id", false, null),
                        ),
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
                        nullable = false,
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

        assertEquals(listOf("id", "title"), scalarFields.map { it["name"] })
        assertEquals(listOf("id", "title"), fields.map { it["name"] })
        assertEquals(listOf("id", "title"), scalarFields.map { it["columnName"] })
        assertEquals("author_id", relationFields.single()["joinColumn"])
        assertEquals("author", relationFields.single()["name"])
    }

    @Test
    fun `entity planner keeps scalar foreign key and adds inverse read only relation`() {
        val entity = EntityModel(
            name = "VideoPostItem",
            packageName = "com.acme.demo.domain.aggregates.video_post_item",
            tableName = "video_post_item",
            comment = "video post item",
            fields = listOf(
                FieldModel("id", "Long"),
                FieldModel("label", "String"),
                FieldModel("videoPostId", "Long"),
            ),
            idField = FieldModel("id", "Long"),
        )
        val plan = AggregateArtifactPlanner().plan(
            aggregateConfig(),
            CanonicalModel(
                entities = listOf(entity),
                aggregateEntityJpa = listOf(
                    AggregateEntityJpaModel(
                        entityName = entity.name,
                        entityPackageName = entity.packageName,
                        entityEnabled = true,
                        tableName = entity.tableName,
                        columns = listOf(
                            AggregateColumnJpaModel("id", "id", true, null),
                            AggregateColumnJpaModel("label", "label", false, null),
                            AggregateColumnJpaModel("videoPostId", "video_post_id", false, null),
                        ),
                    )
                ),
                aggregateInverseRelations = listOf(
                    AggregateInverseRelationModel(
                        ownerEntityName = "VideoPostItem",
                        ownerEntityPackageName = "com.acme.demo.domain.aggregates.video_post_item",
                        fieldName = "videoPost",
                        targetEntityName = "VideoPost",
                        targetEntityPackageName = "com.acme.demo.domain.aggregates.video_post",
                        relationType = AggregateRelationType.MANY_TO_ONE,
                        joinColumn = "video_post_id",
                        fetchType = AggregateFetchType.LAZY,
                        nullable = false,
                        insertable = false,
                        updatable = false,
                    )
                ),
            )
        )

        val entityItem = plan.single { it.templateId == "aggregate/entity.kt.peb" }
        @Suppress("UNCHECKED_CAST")
        val scalarFields = entityItem.context["scalarFields"] as List<Map<String, Any?>>
        @Suppress("UNCHECKED_CAST")
        val relationFields = entityItem.context["relationFields"] as List<Map<String, Any?>>
        @Suppress("UNCHECKED_CAST")
        val imports = entityItem.context["imports"] as List<String>
        @Suppress("UNCHECKED_CAST")
        val jpaImports = entityItem.context["jpaImports"] as List<String>

        val relation = relationFields.single()
        assertEquals(listOf("id", "label", "videoPostId"), scalarFields.map { it["name"] })
        assertEquals(listOf("id", "label", "video_post_id"), scalarFields.map { it["columnName"] })
        assertEquals(listOf("videoPost"), relationFields.map { it["name"] })
        assertEquals("MANY_TO_ONE", relation["relationType"])
        assertEquals("video_post_id", relation["joinColumn"])
        assertEquals("LAZY", relation["fetchType"])
        assertEquals(false, relation["nullable"])
        assertEquals(true, relation["readOnly"])
        assertEquals(false, relation["insertable"])
        assertEquals(false, relation["updatable"])
        assertEquals(listOf("com.acme.demo.domain.aggregates.video_post.VideoPost"), imports)
        assertEquals(
            listOf(
                "jakarta.persistence.FetchType",
                "jakarta.persistence.JoinColumn",
                "jakarta.persistence.ManyToOne",
            ),
            jpaImports,
        )
    }

    @Test
    fun `entity planner still drops scalar field for owner side relation join columns`() {
        val entity = EntityModel(
            name = "VideoPostItem",
            packageName = "com.acme.demo.domain.aggregates.video_post_item",
            tableName = "video_post_item",
            comment = "video post item",
            fields = listOf(
                FieldModel("id", "Long"),
                FieldModel("label", "String"),
                FieldModel("videoPostId", "Long"),
            ),
            idField = FieldModel("id", "Long"),
        )
        val plan = AggregateArtifactPlanner().plan(
            aggregateConfig(),
            CanonicalModel(
                entities = listOf(entity),
                aggregateEntityJpa = listOf(
                    AggregateEntityJpaModel(
                        entityName = entity.name,
                        entityPackageName = entity.packageName,
                        entityEnabled = true,
                        tableName = entity.tableName,
                        columns = listOf(
                            AggregateColumnJpaModel("id", "id", true, null),
                            AggregateColumnJpaModel("label", "label", false, null),
                            AggregateColumnJpaModel("videoPostId", "video_post_id", false, null),
                        ),
                    )
                ),
                aggregateRelations = listOf(
                    AggregateRelationModel(
                        ownerEntityName = "VideoPostItem",
                        ownerEntityPackageName = "com.acme.demo.domain.aggregates.video_post_item",
                        fieldName = "videoPost",
                        targetEntityName = "VideoPost",
                        targetEntityPackageName = "com.acme.demo.domain.aggregates.video_post",
                        relationType = AggregateRelationType.MANY_TO_ONE,
                        joinColumn = "video_post_id",
                        fetchType = AggregateFetchType.LAZY,
                        nullable = false,
                    )
                ),
            )
        )

        val entityItem = plan.single { it.templateId == "aggregate/entity.kt.peb" }
        @Suppress("UNCHECKED_CAST")
        val scalarFields = entityItem.context["scalarFields"] as List<Map<String, Any?>>
        @Suppress("UNCHECKED_CAST")
        val relationFields = entityItem.context["relationFields"] as List<Map<String, Any?>>

        assertEquals(listOf("id", "label"), scalarFields.map { it["name"] })
        assertEquals(listOf("id", "label"), scalarFields.map { it["columnName"] })
        assertEquals(listOf("videoPost"), relationFields.map { it["name"] })
        assertEquals("video_post_id", relationFields.single()["joinColumn"])
    }

    @Test
    fun `entity planner keeps persistence import flags false when explicit controls are absent`() {
        val entity = EntityModel(
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
        val plan = AggregateArtifactPlanner().plan(
            aggregateConfig(),
            CanonicalModel(
                entities = listOf(entity),
                aggregateEntityJpa = listOf(defaultAggregateEntityJpa(entity)),
            )
        )

        val entityArtifact = plan.single { it.outputPath.endsWith("/VideoPost.kt") }

        assertEquals(false, entityArtifact.context["hasGeneratedValueFields"])
        assertEquals(false, entityArtifact.context["hasVersionFields"])
    }

    @Test
    fun `entity planner exposes provider specific persistence contract`() {
        val entity = EntityModel(
            name = "VideoPost",
            packageName = "com.acme.demo.domain.aggregates.video_post",
            tableName = "video_post",
            comment = "video post",
            fields = listOf(
                FieldModel("id", "Long"),
                FieldModel("version", "Long"),
                FieldModel("deleted", "Int"),
            ),
            idField = FieldModel("id", "Long"),
        )
        val artifact = AggregateArtifactPlanner().plan(
            aggregateConfig(),
            CanonicalModel(
                entities = listOf(entity),
                aggregateEntityJpa = listOf(defaultAggregateEntityJpa(entity)),
                aggregatePersistenceProviderControls = listOf(
                    com.only4.cap4k.plugin.pipeline.api.AggregatePersistenceProviderControl(
                        entityName = "VideoPost",
                        entityPackageName = "com.acme.demo.domain.aggregates.video_post",
                        tableName = "video_post",
                        dynamicInsert = true,
                        dynamicUpdate = true,
                        softDeleteColumn = "deleted",
                        idFieldName = "id",
                        versionFieldName = "version",
                    )
                ),
            )
        ).single { it.templateId == "aggregate/entity.kt.peb" }

        assertEquals(true, artifact.context["dynamicInsert"])
        assertEquals(true, artifact.context["dynamicUpdate"])
        assertEquals(
            "update \"video_post\" set \"deleted\" = 1 where \"id\" = ? and \"version\" = ?",
            artifact.context["softDeleteSql"],
        )
        assertEquals("\"deleted\" = 0", artifact.context["softDeleteWhereClause"])
    }

    @Test
    fun `entity planner composes soft delete sql with physical id and version column names`() {
        val entity = EntityModel(
            name = "VideoPost",
            packageName = "com.acme.demo.domain.aggregates.video_post",
            tableName = "video_post",
            comment = "video post",
            fields = listOf(
                FieldModel("id", "Long"),
                FieldModel("lockVersion", "Long"),
                FieldModel("deleted", "Int"),
            ),
            idField = FieldModel("id", "Long"),
        )
        val artifact = AggregateArtifactPlanner().plan(
            aggregateConfig(),
            CanonicalModel(
                entities = listOf(entity),
                aggregateEntityJpa = listOf(
                    AggregateEntityJpaModel(
                        entityName = entity.name,
                        entityPackageName = entity.packageName,
                        entityEnabled = true,
                        tableName = entity.tableName,
                        columns = listOf(
                            AggregateColumnJpaModel("id", "video_post_id", true, null),
                            AggregateColumnJpaModel("lockVersion", "lock_version", false, null),
                            AggregateColumnJpaModel("deleted", "deleted", false, null),
                        ),
                    )
                ),
                aggregatePersistenceProviderControls = listOf(
                    com.only4.cap4k.plugin.pipeline.api.AggregatePersistenceProviderControl(
                        entityName = "VideoPost",
                        entityPackageName = "com.acme.demo.domain.aggregates.video_post",
                        tableName = "video_post",
                        softDeleteColumn = "deleted",
                        idFieldName = "id",
                        versionFieldName = "lockVersion",
                    )
                ),
            )
        ).single { it.templateId == "aggregate/entity.kt.peb" }

        assertEquals(
            "update \"video_post\" set \"deleted\" = 1 where \"video_post_id\" = ? and \"lock_version\" = ?",
            artifact.context["softDeleteSql"],
        )
    }

    @Test
    fun `entity planner composes versionless soft delete sql with physical id column only`() {
        val entity = EntityModel(
            name = "VideoPost",
            packageName = "com.acme.demo.domain.aggregates.video_post",
            tableName = "video_post",
            comment = "video post",
            fields = listOf(
                FieldModel("aggregateId", "Long"),
                FieldModel("deleted", "Int"),
            ),
            idField = FieldModel("aggregateId", "Long"),
        )
        val artifact = AggregateArtifactPlanner().plan(
            aggregateConfig(),
            CanonicalModel(
                entities = listOf(entity),
                aggregateEntityJpa = listOf(
                    AggregateEntityJpaModel(
                        entityName = entity.name,
                        entityPackageName = entity.packageName,
                        entityEnabled = true,
                        tableName = entity.tableName,
                        columns = listOf(
                            AggregateColumnJpaModel("aggregateId", "video_post_id", true, null),
                            AggregateColumnJpaModel("deleted", "deleted", false, null),
                        ),
                    )
                ),
                aggregatePersistenceProviderControls = listOf(
                    com.only4.cap4k.plugin.pipeline.api.AggregatePersistenceProviderControl(
                        entityName = "VideoPost",
                        entityPackageName = "com.acme.demo.domain.aggregates.video_post",
                        tableName = "video_post",
                        softDeleteColumn = "deleted",
                        idFieldName = "aggregateId",
                        versionFieldName = null,
                    )
                ),
            )
        ).single { it.templateId == "aggregate/entity.kt.peb" }

        assertEquals(
            "update \"video_post\" set \"deleted\" = 1 where \"video_post_id\" = ?",
            artifact.context["softDeleteSql"],
        )
        assertEquals("\"deleted\" = 0", artifact.context["softDeleteWhereClause"])
    }

    @Test
    fun `entity planner keeps scalar fields when one to many join column matches owner column name`() {
        val entity = EntityModel(
            name = "VideoPost",
            packageName = "com.acme.demo.domain.aggregates.video_post",
            tableName = "video_post",
            comment = "video post",
            fields = listOf(
                FieldModel("id", "Long"),
                FieldModel("videoPostId", "Long"),
                FieldModel("title", "String"),
            ),
            idField = FieldModel("id", "Long"),
        )
        val plan = AggregateArtifactPlanner().plan(
            aggregateConfig(),
            CanonicalModel(
                entities = listOf(entity),
                aggregateEntityJpa = listOf(
                    AggregateEntityJpaModel(
                        entityName = entity.name,
                        entityPackageName = entity.packageName,
                        entityEnabled = true,
                        tableName = entity.tableName,
                        columns = listOf(
                            AggregateColumnJpaModel("id", "id", true, null),
                            AggregateColumnJpaModel("videoPostId", "video_post_id", false, null),
                            AggregateColumnJpaModel("title", "title", false, null),
                        ),
                    )
                ),
                aggregateRelations = listOf(
                    AggregateRelationModel(
                        ownerEntityName = "VideoPost",
                        ownerEntityPackageName = "com.acme.demo.domain.aggregates.video_post",
                        fieldName = "items",
                        targetEntityName = "VideoPostItem",
                        targetEntityPackageName = "com.acme.demo.domain.aggregates.video_post_item",
                        relationType = AggregateRelationType.ONE_TO_MANY,
                        joinColumn = "video_post_id",
                        fetchType = AggregateFetchType.LAZY,
                        nullable = false,
                    )
                )
            )
        )

        val entityItem = plan.single { it.templateId == "aggregate/entity.kt.peb" }
        @Suppress("UNCHECKED_CAST")
        val scalarFields = entityItem.context["scalarFields"] as List<Map<String, Any?>>
        @Suppress("UNCHECKED_CAST")
        val relationFields = entityItem.context["relationFields"] as List<Map<String, Any?>>

        assertEquals(listOf("id", "videoPostId", "title"), scalarFields.map { it["name"] })
        assertEquals(listOf("id", "video_post_id", "title"), scalarFields.map { it["columnName"] })
        assertEquals("ONE_TO_MANY", relationFields.single()["relationType"])
        assertEquals("video_post_id", relationFields.single()["joinColumn"])
    }

    @Test
    fun `entity planner relation nullability is sourced from canonical relation model`() {
        val entity = EntityModel(
            name = "VideoPost",
            packageName = "com.acme.demo.domain.aggregates.video_post",
            tableName = "video_post",
            comment = "video post",
            fields = listOf(
                FieldModel("id", "Long"),
                FieldModel("author_id", "Long", nullable = false),
            ),
            idField = FieldModel("id", "Long"),
        )
        val plan = AggregateArtifactPlanner().plan(
            aggregateConfig(),
            CanonicalModel(
                entities = listOf(entity),
                aggregateEntityJpa = listOf(
                    defaultAggregateEntityJpa(entity)
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
                        nullable = true,
                    )
                )
            )
        )

        val entityItem = plan.single { it.templateId == "aggregate/entity.kt.peb" }
        @Suppress("UNCHECKED_CAST")
        val relationFields = entityItem.context["relationFields"] as List<Map<String, Any?>>

        assertEquals(true, relationFields.single()["nullable"])
    }

    @Test
    fun `entity planner exposes bounded relation side controls across supported relation types`() {
        val entity = EntityModel(
            name = "VideoPost",
            packageName = "com.acme.demo.domain.aggregates.video_post",
            tableName = "video_post",
            comment = "video post",
            fields = listOf(
                FieldModel("id", "Long"),
                FieldModel("author_id", "Long"),
                FieldModel("cover_profile_id", "Long"),
            ),
            idField = FieldModel("id", "Long"),
        )
        val plan = AggregateArtifactPlanner().plan(
            aggregateConfig(),
            CanonicalModel(
                entities = listOf(entity),
                aggregateEntityJpa = listOf(
                    defaultAggregateEntityJpa(entity)
                ),
                aggregateRelations = listOf(
                    AggregateRelationModel(
                        ownerEntityName = "VideoPost",
                        ownerEntityPackageName = "com.acme.demo.domain.aggregates.video_post",
                        fieldName = "author",
                        targetEntityName = "Author",
                        targetEntityPackageName = "com.acme.demo.domain.identity.author",
                        relationType = AggregateRelationType.MANY_TO_ONE,
                        joinColumn = "author_id",
                        fetchType = AggregateFetchType.LAZY,
                        nullable = false,
                        joinColumnNullable = false,
                    ),
                    AggregateRelationModel(
                        ownerEntityName = "VideoPost",
                        ownerEntityPackageName = "com.acme.demo.domain.aggregates.video_post",
                        fieldName = "coverProfile",
                        targetEntityName = "CoverProfile",
                        targetEntityPackageName = "com.acme.demo.domain.media.cover",
                        relationType = AggregateRelationType.ONE_TO_ONE,
                        joinColumn = "cover_profile_id",
                        fetchType = AggregateFetchType.LAZY,
                        nullable = false,
                        joinColumnNullable = true,
                    ),
                    AggregateRelationModel(
                        ownerEntityName = "VideoPost",
                        ownerEntityPackageName = "com.acme.demo.domain.aggregates.video_post",
                        fieldName = "items",
                        targetEntityName = "VideoPostItem",
                        targetEntityPackageName = "com.acme.demo.domain.aggregates.video_post_item",
                        relationType = AggregateRelationType.ONE_TO_MANY,
                        joinColumn = "video_post_id",
                        fetchType = AggregateFetchType.LAZY,
                        nullable = false,
                        cascadeAll = true,
                        orphanRemoval = true,
                        joinColumnNullable = false,
                    ),
                )
            )
        )

        val entityItem = plan.single { it.templateId == "aggregate/entity.kt.peb" }
        @Suppress("UNCHECKED_CAST")
        val relationFields = entityItem.context["relationFields"] as List<Map<String, Any?>>
        @Suppress("UNCHECKED_CAST")
        val jpaImports = entityItem.context["jpaImports"] as List<String>

        val author = relationFields.single { it["name"] == "author" }
        val coverProfile = relationFields.single { it["name"] == "coverProfile" }
        val items = relationFields.single { it["name"] == "items" }

        assertEquals(false, author["cascadeAll"])
        assertEquals(false, author["orphanRemoval"])
        assertEquals(false, author["joinColumnNullable"])
        assertEquals(false, coverProfile["cascadeAll"])
        assertEquals(false, coverProfile["orphanRemoval"])
        assertEquals(true, coverProfile["joinColumnNullable"])
        assertEquals(true, items["cascadeAll"])
        assertEquals(true, items["orphanRemoval"])
        assertEquals(false, items["joinColumnNullable"])
        assertTrue(jpaImports.contains("jakarta.persistence.CascadeType"))
    }

    @Test
    fun `entity planner skips cascade type import when only direct relations are planned`() {
        val entity = EntityModel(
            name = "VideoPost",
            packageName = "com.acme.demo.domain.aggregates.video_post",
            tableName = "video_post",
            comment = "video post",
            fields = listOf(
                FieldModel("id", "Long"),
                FieldModel("author_id", "Long"),
                FieldModel("cover_profile_id", "Long"),
            ),
            idField = FieldModel("id", "Long"),
        )
        val plan = AggregateArtifactPlanner().plan(
            aggregateConfig(),
            CanonicalModel(
                entities = listOf(entity),
                aggregateEntityJpa = listOf(
                    defaultAggregateEntityJpa(entity)
                ),
                aggregateRelations = listOf(
                    AggregateRelationModel(
                        ownerEntityName = "VideoPost",
                        ownerEntityPackageName = "com.acme.demo.domain.aggregates.video_post",
                        fieldName = "author",
                        targetEntityName = "Author",
                        targetEntityPackageName = "com.acme.demo.domain.identity.author",
                        relationType = AggregateRelationType.MANY_TO_ONE,
                        joinColumn = "author_id",
                        fetchType = AggregateFetchType.LAZY,
                        nullable = false,
                        joinColumnNullable = false,
                    ),
                    AggregateRelationModel(
                        ownerEntityName = "VideoPost",
                        ownerEntityPackageName = "com.acme.demo.domain.aggregates.video_post",
                        fieldName = "coverProfile",
                        targetEntityName = "CoverProfile",
                        targetEntityPackageName = "com.acme.demo.domain.media.cover",
                        relationType = AggregateRelationType.ONE_TO_ONE,
                        joinColumn = "cover_profile_id",
                        fetchType = AggregateFetchType.LAZY,
                        nullable = false,
                        joinColumnNullable = true,
                    ),
                )
            )
        )

        val entityItem = plan.single { it.templateId == "aggregate/entity.kt.peb" }
        @Suppress("UNCHECKED_CAST")
        val relationFields = entityItem.context["relationFields"] as List<Map<String, Any?>>
        @Suppress("UNCHECKED_CAST")
        val jpaImports = entityItem.context["jpaImports"] as List<String>

        val author = relationFields.single { it["name"] == "author" }
        val coverProfile = relationFields.single { it["name"] == "coverProfile" }

        assertTrue(author.containsKey("joinColumnNullable"))
        assertTrue(author.containsKey("cascadeAll"))
        assertTrue(author.containsKey("orphanRemoval"))
        assertEquals(false, author["joinColumnNullable"])
        assertEquals(false, author["cascadeAll"])
        assertEquals(false, author["orphanRemoval"])
        assertTrue(coverProfile.containsKey("joinColumnNullable"))
        assertTrue(coverProfile.containsKey("cascadeAll"))
        assertTrue(coverProfile.containsKey("orphanRemoval"))
        assertEquals(true, coverProfile["joinColumnNullable"])
        assertEquals(false, coverProfile["cascadeAll"])
        assertEquals(false, coverProfile["orphanRemoval"])
        assertEquals(false, jpaImports.contains("jakarta.persistence.CascadeType"))
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
            generators = mapOf("aggregate" to GeneratorConfig(enabled = true, options = allAggregateArtifactsEnabled())),
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
            aggregateEntityJpa = listOf(
                AggregateEntityJpaModel(
                    entityName = "VideoPost",
                    entityPackageName = "com.acme.demo.domain.aggregates.video_post",
                    entityEnabled = true,
                    tableName = "video_post",
                    columns = listOf(
                        AggregateColumnJpaModel("id", "id", true, null),
                    ),
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
        assertFalse(planItems.any { it.templateId == "aggregate/schema_base.kt.peb" })
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
            "com.only4.cap4k.ddd.domain.repo.schema",
            planItems.first { it.templateId == "aggregate/schema.kt.peb" }.context["schemaRuntimePackage"],
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
        assertEquals("com.acme.demo.domain.aggregates.video_post.VideoPost", repositoryContext["entityTypeFqn"])
        assertEquals("VideoPost", repositoryContext["aggregateName"])
        assertEquals(false, repositoryContext["supportQuerydsl"])
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
    fun `planner keeps child entities and schemas in root aggregate package without root only artifacts`() {
        val rootEntity = EntityModel(
            name = "Video",
            packageName = "com.acme.demo.domain.aggregates.video",
            tableName = "video",
            comment = "video aggregate",
            fields = listOf(FieldModel("id", "Long")),
            idField = FieldModel("id", "Long"),
            aggregateRoot = true,
        )
        val childEntity = EntityModel(
            name = "VideoFile",
            packageName = "com.acme.demo.domain.aggregates.video",
            tableName = "video_file",
            comment = "video file entity",
            fields = listOf(FieldModel("id", "Long"), FieldModel("videoId", "Long", columnName = "video_id")),
            idField = FieldModel("id", "Long"),
            aggregateRoot = false,
            parentEntityName = "Video",
        )
        val model = CanonicalModel(
            schemas = listOf(
                SchemaModel(
                    name = "SVideo",
                    packageName = "com.acme.demo.domain._share.meta.video",
                    entityName = "Video",
                    comment = "video schema",
                    fields = rootEntity.fields,
                ),
                SchemaModel(
                    name = "SVideoFile",
                    packageName = "com.acme.demo.domain._share.meta.video",
                    entityName = "VideoFile",
                    comment = "video file schema",
                    fields = childEntity.fields,
                ),
            ),
            entities = listOf(rootEntity, childEntity),
            aggregateEntityJpa = listOf(
                defaultAggregateEntityJpa(rootEntity),
                defaultAggregateEntityJpa(childEntity),
            ),
            repositories = listOf(
                RepositoryModel(
                    name = "VideoRepository",
                    packageName = "com.acme.demo.adapter.domain.repositories",
                    entityName = "Video",
                    idType = "Long",
                )
            ),
        )

        val planItems = AggregateArtifactPlanner().plan(aggregateConfig(), model)

        assertTrue(planItems.any {
            it.outputPath == "demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video/VideoFile.kt"
        })
        assertTrue(planItems.any {
            it.outputPath == "demo-domain/src/main/kotlin/com/acme/demo/domain/_share/meta/video/SVideoFile.kt"
        })
        assertFalse(planItems.any { it.outputPath.endsWith("/VideoFileRepository.kt") })
        assertFalse(planItems.any { it.outputPath.endsWith("/VideoFileFactory.kt") })
        assertFalse(planItems.any { it.outputPath.endsWith("/VideoFileSpecification.kt") })
        assertFalse(planItems.any { it.outputPath.endsWith("/AggVideoFile.kt") })

        val rootSchemaContext = planItems.single {
            it.templateId == "aggregate/schema.kt.peb" && it.context["typeName"] == "SVideo"
        }.context
        val childSchemaContext = planItems.single {
            it.templateId == "aggregate/schema.kt.peb" && it.context["typeName"] == "SVideoFile"
        }.context
        assertEquals(true, rootSchemaContext["isAggregateRoot"])
        assertEquals("com.acme.demo.domain.aggregates.video.AggVideo", rootSchemaContext["aggregateTypeFqn"])
        assertEquals(false, childSchemaContext["isAggregateRoot"])
        assertEquals("", childSchemaContext["aggregateTypeFqn"])
    }

    @Test
    fun `unique planners keep child entity artifacts in root aggregate package without child repository`() {
        val rootEntity = EntityModel(
            name = "Video",
            packageName = "com.acme.demo.domain.aggregates.video",
            tableName = "video",
            comment = "video aggregate",
            fields = listOf(FieldModel("id", "Long")),
            idField = FieldModel("id", "Long"),
            aggregateRoot = true,
        )
        val childEntity = EntityModel(
            name = "VideoFile",
            packageName = "com.acme.demo.domain.aggregates.video",
            tableName = "video_file",
            comment = "video file entity",
            fields = listOf(FieldModel("id", "Long"), FieldModel("videoId", "Long", columnName = "video_id")),
            idField = FieldModel("id", "Long"),
            uniqueConstraints = listOf(listOf("video_id")),
            aggregateRoot = false,
            parentEntityName = "Video",
        )
        val model = CanonicalModel(
            schemas = listOf(
                SchemaModel(
                    name = "SVideo",
                    packageName = "com.acme.demo.domain._share.meta.video",
                    entityName = "Video",
                    comment = "video schema",
                    fields = rootEntity.fields,
                ),
                SchemaModel(
                    name = "SVideoFile",
                    packageName = "com.acme.demo.domain._share.meta.video",
                    entityName = "VideoFile",
                    comment = "video file schema",
                    fields = childEntity.fields,
                ),
            ),
            entities = listOf(rootEntity, childEntity),
            aggregateEntityJpa = listOf(
                defaultAggregateEntityJpa(rootEntity),
                defaultAggregateEntityJpa(childEntity),
            ),
            repositories = listOf(
                RepositoryModel(
                    name = "VideoRepository",
                    packageName = "com.acme.demo.adapter.domain.repositories",
                    entityName = "Video",
                    idType = "Long",
                )
            ),
        )

        val planItems = AggregateArtifactPlanner().plan(aggregateConfig(), model)

        val query = planItems.single { it.context["typeName"] == "UniqueVideoFileVideoIdQry" }
        val handler = planItems.single { it.context["typeName"] == "UniqueVideoFileVideoIdQryHandler" }
        val validator = planItems.single { it.context["typeName"] == "UniqueVideoFileVideoId" }
        assertEquals(
            "demo-application/src/main/kotlin/com/acme/demo/application/queries/video/unique/UniqueVideoFileVideoIdQry.kt",
            query.outputPath,
        )
        assertEquals(
            "demo-adapter/src/main/kotlin/com/acme/demo/adapter/queries/video/unique/UniqueVideoFileVideoIdQryHandler.kt",
            handler.outputPath,
        )
        assertEquals(
            "demo-application/src/main/kotlin/com/acme/demo/application/validators/video/unique/UniqueVideoFileVideoId.kt",
            validator.outputPath,
        )
        assertEquals(null, handler.context["repositoryTypeName"])
        assertEquals(null, handler.context["repositoryTypeFqn"])
        assertEquals("com.acme.demo.domain.aggregates.video.VideoFile", handler.context["entityTypeFqn"])
        assertEquals("com.acme.demo.domain._share.meta.video.SVideoFile", handler.context["schemaTypeFqn"])
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
            generators = mapOf("aggregate" to GeneratorConfig(enabled = true, options = allAggregateArtifactsEnabled())),
            templates = TemplateConfig("ddd-default", emptyList(), ConflictPolicy.SKIP),
        )

        val entity = EntityModel(
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
        val model = CanonicalModel(
            entities = listOf(entity),
            schemas = listOf(
                SchemaModel(
                    name = "SVideoPost",
                    packageName = "com.acme.demo.domain._share.meta.video_post",
                    entityName = "VideoPost",
                    comment = "Video post schema",
                    fields = entity.fields,
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
            aggregateEntityJpa = listOf(
                AggregateEntityJpaModel(
                    entityName = "VideoPost",
                    entityPackageName = "com.acme.demo.domain.aggregates.video_post",
                    entityEnabled = true,
                    tableName = "Video_Post",
                    columns = listOf(
                        AggregateColumnJpaModel("id", "id", true, null),
                        AggregateColumnJpaModel("slug", "slug", false, null),
                        AggregateColumnJpaModel("title", "title", false, null),
                    ),
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
    fun `unique planners expose business validator and repository backed handler context`() {
        val entity = EntityModel(
            name = "UserMessage",
            packageName = "com.acme.demo.domain.aggregates.user_message",
            tableName = "user_message",
            comment = "user message",
            fields = listOf(
                FieldModel("id", "Long", columnName = "id"),
                FieldModel("messageKey", "String", columnName = "message_key"),
            ),
            idField = FieldModel("id", "Long", columnName = "id"),
            uniqueConstraints = listOf(listOf("message_key")),
        )
        val model = CanonicalModel(
            entities = listOf(entity),
            schemas = listOf(
                SchemaModel(
                    name = "SUserMessage",
                    packageName = "com.acme.demo.domain._share.meta.user_message",
                    entityName = "UserMessage",
                    comment = "user message",
                    fields = entity.fields,
                )
            ),
            repositories = listOf(
                RepositoryModel(
                    name = "UserMessageRepository",
                    packageName = "com.acme.demo.adapter.domain.repositories",
                    entityName = "UserMessage",
                    idType = "Long",
                )
            ),
            aggregateEntityJpa = listOf(defaultAggregateEntityJpa(entity)),
        )

        val planItems = AggregateArtifactPlanner().plan(aggregateConfig(), model)
        val validator = planItems.single { it.templateId == "aggregate/unique_validator.kt.peb" }
        val handler = planItems.single { it.templateId == "aggregate/unique_query_handler.kt.peb" }

        assertEquals("UniqueUserMessageMessageKey", validator.context["typeName"])
        assertEquals("UniqueUserMessageMessageKeyQry", validator.context["queryTypeName"])
        @Suppress("UNCHECKED_CAST")
        val validatorRequestProps = validator.context["requestProps"] as List<Map<String, Any?>>
        val validatorRequestProp = validatorRequestProps.single()
        assertEquals("messageKey", validatorRequestProp["name"])
        assertEquals("String", validatorRequestProp["type"])
        assertEquals(true, validatorRequestProp["isString"])
        assertEquals("messageKeyField", validatorRequestProp["param"])
        assertEquals("messageKeyProperty", validatorRequestProp["varName"])
        @Suppress("UNCHECKED_CAST")
        val fieldParams = validator.context["fieldParams"] as List<Map<String, Any?>>
        assertEquals(mapOf("param" to "messageKeyField", "default" to "messageKey"), fieldParams.single())
        assertEquals("userMessageIdField", validator.context["entityIdParam"])
        assertEquals("userMessageId", validator.context["entityIdDefault"])
        assertEquals("userMessageIdProperty", validator.context["entityIdVar"])

        assertEquals("UserMessageRepository", handler.context["repositoryTypeName"])
        assertEquals(
            "com.acme.demo.adapter.domain.repositories.UserMessageRepository",
            handler.context["repositoryTypeFqn"],
        )
        assertEquals("SUserMessage", handler.context["schemaTypeName"])
        assertEquals(
            "com.acme.demo.domain._share.meta.user_message.SUserMessage",
            handler.context["schemaTypeFqn"],
        )
        assertEquals("id", handler.context["idPropName"])
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
            aggregateEntityJpa = listOf(
                AggregateEntityJpaModel(
                    entityName = "VideoPost",
                    entityPackageName = "com.acme.demo.domain.aggregates.video_post",
                    entityEnabled = true,
                    tableName = "video_post",
                    columns = listOf(
                        AggregateColumnJpaModel("status", "status", false, null),
                        AggregateColumnJpaModel("visibility", "visibility", false, null),
                    ),
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
    fun `aggregate planner routes custom enum layouts through artifact layout`() {
        val artifactLayout = ArtifactLayoutConfig(
            aggregate = PackageLayout("domain.model"),
            aggregateSchema = PackageLayout("domain.meta"),
            aggregateSharedEnum = PackageLayout(
                packageRoot = "domain.catalog",
                defaultPackage = "shared",
                packageSuffix = "types",
            ),
            aggregateEnumTranslation = PackageLayout("adapter.enum_text"),
        )
        val entity = EntityModel(
            name = "VideoPost",
            packageName = "com.acme.demo.domain.model.video_post",
            tableName = "video_post",
            comment = "video post",
            fields = listOf(
                FieldModel(name = "id", type = "Long"),
                FieldModel(name = "status", type = "Int", typeBinding = "Status"),
                FieldModel(
                    name = "visibility",
                    type = "Int",
                    typeBinding = "Visibility",
                    enumItems = listOf(EnumItemModel(0, "HIDDEN", "Hidden")),
                ),
            ),
            idField = FieldModel(name = "id", type = "Long"),
        )
        val model = CanonicalModel(
            sharedEnums = listOf(
                SharedEnumDefinition(
                    typeName = "Status",
                    packageName = "",
                    generateTranslation = true,
                    items = listOf(
                        EnumItemModel(0, "DRAFT", "Draft"),
                        EnumItemModel(1, "PUBLISHED", "Published"),
                    ),
                )
            ),
            schemas = listOf(
                SchemaModel(
                    name = "SVideoPost",
                    packageName = "com.acme.demo.domain.meta.video_post",
                    entityName = "VideoPost",
                    comment = "video post schema",
                    fields = entity.fields,
                )
            ),
            entities = listOf(entity),
            aggregateEntityJpa = listOf(defaultAggregateEntityJpa(entity)),
        )

        val items = AggregateArtifactPlanner().plan(
            aggregateConfig(artifactLayout = artifactLayout),
            model,
        )

        val sharedEnum = items.single {
            it.templateId == "aggregate/enum.kt.peb" &&
                it.context["typeName"] == "Status"
        }
        val localEnum = items.single {
            it.templateId == "aggregate/enum.kt.peb" &&
                it.context["typeName"] == "Visibility"
        }
        val sharedTranslation = items.single {
            it.templateId == "aggregate/enum_translation.kt.peb" &&
                it.context["typeName"] == "StatusTranslation"
        }
        val localTranslation = items.single {
            it.templateId == "aggregate/enum_translation.kt.peb" &&
                it.context["typeName"] == "VisibilityTranslation"
        }
        val entityPlan = items.single { it.templateId == "aggregate/entity.kt.peb" }
        val schemaPlan = items.single { it.templateId == "aggregate/schema.kt.peb" }
        @Suppress("UNCHECKED_CAST")
        val entityFields = entityPlan.context.getValue("scalarFields") as List<Map<String, Any?>>
        @Suppress("UNCHECKED_CAST")
        val schemaFields = schemaPlan.context.getValue("fields") as List<Map<String, Any?>>

        assertEquals(
            "demo-domain/src/main/kotlin/com/acme/demo/domain/catalog/shared/types/Status.kt",
            sharedEnum.outputPath,
        )
        assertEquals("com.acme.demo.domain.catalog.shared.types", sharedEnum.context["packageName"])
        assertEquals(
            "demo-domain/src/main/kotlin/com/acme/demo/domain/model/video_post/enums/Visibility.kt",
            localEnum.outputPath,
        )
        assertEquals("com.acme.demo.domain.model.video_post.enums", localEnum.context["packageName"])
        assertEquals(
            "demo-adapter/src/main/kotlin/com/acme/demo/adapter/enum_text/shared/StatusTranslation.kt",
            sharedTranslation.outputPath,
        )
        assertEquals("com.acme.demo.adapter.enum_text.shared", sharedTranslation.context["packageName"])
        assertEquals(
            "demo-adapter/src/main/kotlin/com/acme/demo/adapter/enum_text/video_post/VisibilityTranslation.kt",
            localTranslation.outputPath,
        )
        assertEquals("com.acme.demo.adapter.enum_text.video_post", localTranslation.context["packageName"])
        assertEquals(
            "com.acme.demo.domain.catalog.shared.types.Status",
            entityFields.single { it["name"] == "status" }["type"],
        )
        assertEquals(
            "com.acme.demo.domain.model.video_post.enums.Visibility",
            entityFields.single { it["name"] == "visibility" }["type"],
        )
        assertEquals(
            "com.acme.demo.domain.catalog.shared.types.Status",
            schemaFields.single { it["name"] == "status" }["type"],
        )
        assertEquals(
            "com.acme.demo.domain.model.video_post.enums.Visibility",
            schemaFields.single { it["name"] == "visibility" }["type"],
        )
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
            aggregateEntityJpa = listOf(
                AggregateEntityJpaModel(
                    entityName = "VideoPost",
                    entityPackageName = "com.acme.demo.domain.aggregates.video_post",
                    entityEnabled = true,
                    tableName = "video_post",
                    columns = listOf(
                        AggregateColumnJpaModel("visibility", "visibility", false, null),
                    ),
                ),
                AggregateEntityJpaModel(
                    entityName = "ArticlePost",
                    entityPackageName = "com.acme.demo.domain.aggregates.article_post",
                    entityEnabled = true,
                    tableName = "article_post",
                    columns = listOf(
                        AggregateColumnJpaModel("visibility", "visibility", false, null),
                    ),
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
                    FieldModel("tenantId", "Long", columnName = "tenant_id"),
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
            aggregateEntityJpa = listOf(
                AggregateEntityJpaModel(
                    entityName = "VideoPost",
                    entityPackageName = "com.acme.demo.domain.aggregates.video_post",
                    entityEnabled = true,
                    tableName = "video_post",
                    columns = listOf(
                        AggregateColumnJpaModel("id", "id", true, null),
                    ),
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
        assertEquals("com.acme.demo.domain.aggregates.video_post.VideoPost", repositoryContext["entityTypeFqn"])
        assertEquals(false, repositoryContext["supportQuerydsl"])
    }

    @Test
    fun `schema planner fails fast when schema entity is ambiguous`() {
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
            aggregateEntityJpa = listOf(
                defaultAggregateEntityJpa(primaryEntity),
                defaultAggregateEntityJpa(secondaryEntity),
            ),
            repositories = emptyList(),
        )

        val ex = assertThrows(IllegalStateException::class.java) {
            AggregateArtifactPlanner().plan(config, model)
        }

        assertEquals(
            "schema SVideoPost requires exactly one entity named VideoPost, but found 2",
            ex.message,
        )
    }

    @Test
    fun `factory specification and wrapper planners use the current entity when names collide`() {
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
            schemas = emptyList(),
            entities = listOf(primaryEntity, secondaryEntity),
            aggregateEntityJpa = listOf(
                defaultAggregateEntityJpa(primaryEntity),
                defaultAggregateEntityJpa(secondaryEntity),
            ),
            repositories = emptyList(),
        )

        val planItems = AggregateArtifactPlanner().plan(config, model)
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
    fun `schema planner fails fast when schema entity is missing`() {
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

        val ex = assertThrows(IllegalStateException::class.java) {
            AggregateArtifactPlanner().plan(config, model)
        }

        assertEquals(
            "schema SUnknown requires exactly one entity named Unknown, but found 0",
            ex.message,
        )
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
        artifactLayout: ArtifactLayoutConfig = ArtifactLayoutConfig(),
        options: Map<String, Any?> = allAggregateArtifactsEnabled(),
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
            generators = mapOf("aggregate" to GeneratorConfig(enabled = true, options = options)),
            templates = TemplateConfig("ddd-default", emptyList(), ConflictPolicy.SKIP),
            artifactLayout = artifactLayout,
        )

    private fun allAggregateArtifactsEnabled(): Map<String, Any?> =
        mapOf(
            "artifact.factory" to true,
            "artifact.specification" to true,
            "artifact.wrapper" to true,
            "artifact.unique" to true,
            "artifact.enumTranslation" to true,
        )

    private fun defaultAggregateEntityJpa(entity: EntityModel): AggregateEntityJpaModel =
        AggregateEntityJpaModel(
            entityName = entity.name,
            entityPackageName = entity.packageName,
            entityEnabled = true,
            tableName = entity.tableName,
            columns = entity.fields.map { field ->
                AggregateColumnJpaModel(
                    fieldName = field.name,
                    columnName = field.name,
                    isId = field.name == entity.idField.name,
                    converterTypeFqn = null,
                )
            },
        )
}

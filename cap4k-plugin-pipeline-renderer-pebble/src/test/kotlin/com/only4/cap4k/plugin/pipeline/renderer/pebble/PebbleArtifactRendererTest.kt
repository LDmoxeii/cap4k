package com.only4.cap4k.plugin.pipeline.renderer.pebble

import com.google.gson.JsonParser
import com.only4.cap4k.plugin.pipeline.api.*
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.writeText
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class PebbleArtifactRendererTest {
    private val legacyAggregateAnnotationFq =
        listOf("com.only4.cap4k.ddd.core.domain", "aggregate.annotation.Aggregate").joinToString(".")
    private val legacyAggregateCall = "@" + "Aggregate("

    /** Template-shaped fixture; canonical-to-template projection is covered by DrawingBoardArtifactPlannerTest. */
    private data class DrawingBoardFieldModel(
        val name: String,
        val type: String,
        val defaultValue: String? = null,
    )

    private data class DrawingBoardElementModel(
        val tag: String,
        val packageName: String,
        val name: String,
        val description: String,
        val aggregates: List<String> = emptyList(),
        val artifacts: List<ArtifactSelectionModel> = emptyList(),
        val artifactsDeclared: Boolean = artifacts.isNotEmpty(),
        val persist: Boolean? = null,
        val fields: List<DrawingBoardFieldModel> = emptyList(),
        val resultFields: List<DrawingBoardFieldModel> = emptyList(),
        val eventName: String? = null,
    ) {
        val designJsonArtifacts: List<ArtifactSelectionModel>
            get() = artifacts.sortedWith(compareBy<ArtifactSelectionModel> { it.family }.thenBy { it.variant })

        val includeDesignJsonArtifacts: Boolean
            get() = artifactsDeclared && designJsonArtifacts != defaultArtifacts(tag)
    }

    private companion object {
        fun defaultArtifacts(tag: String): List<ArtifactSelectionModel> = when (tag) {
            "command" -> listOf(ArtifactSelectionModel("command"))
            "query" -> listOf(ArtifactSelectionModel("query"), ArtifactSelectionModel("query-handler"))
            "capability" -> listOf(ArtifactSelectionModel("capability"), ArtifactSelectionModel("capability-handler"))
            "api_payload" -> listOf(ArtifactSelectionModel("api-payload"))
            "domain_event" -> listOf(
                ArtifactSelectionModel("domain-event"),
                ArtifactSelectionModel("domain-subscriber"),
            )
            "integration_event" -> listOf(ArtifactSelectionModel("integration-event", "outbound"))
            "domain_service" -> listOf(ArtifactSelectionModel("domain-service"))
            else -> emptyList()
        }
    }

    private fun entityScalarFields(vararg fields: Map<String, Any?>): List<Map<String, Any?>> =
        fields.toList().also { scalarFields ->
            scalarFields.forEach { field ->
                require("propertyNullable" in field) {
                    "missing propertyNullable for entity scalar field ${field["name"] ?: field["fieldName"]}"
                }
            }
        }

    private fun assertReadableKotlin(content: String) {
        assertFalse(Regex("""(?m)[ \t]+$""").containsMatchIn(content), "Generated Kotlin must not contain trailing whitespace.")
        assertFalse(Regex("""\n{3,}""").containsMatchIn(content), "Generated Kotlin must not contain three or more consecutive newlines.")
    }

    private fun schemaRelationJoinTestContext(): Map<String, Any?> = mapOf(
        "packageName" to "com.acme.demo.domain._share.meta.video_post",
        "typeName" to "SVideoPost",
        "entityName" to "VideoPost",
        "schemaRuntimePackage" to "com.only4.cap4k.ddd.domain.repo.schema",
        "entityTypeFqn" to "com.acme.demo.domain.aggregates.video_post.VideoPost",
        "isAggregateRoot" to false,
        "imports" to emptyList<String>(),
        "fields" to emptyList<Map<String, String>>(),
        "relationJoins" to listOf(
            mapOf(
                "domainName" to "items",
                "persistencePathName" to "_items",
                "methodName" to "joinItems",
                "relationKind" to "OWNED_MANY",
                "targetEntityName" to "VideoPostItem",
                "targetEntityTypeFqn" to "com.acme.demo.domain.aggregates.video_post.VideoPostItem",
                "targetSchemaName" to "SVideoPostItem",
                "targetSchemaFqn" to "com.acme.demo.domain._share.meta.video_post.SVideoPostItem",
            ),
        ),
    )

    private val schemaRelationJoinStubSources = listOf(
        SourceFile.kotlin(
            "CriteriaStubs.kt",
            """
            package jakarta.persistence.criteria

            interface Expression<T>
            interface Path<T> : Expression<T> { fun <Y> get(name: String): Path<Y> }
            interface From<Z, X> : Path<X> { fun <Y, T> join(name: String, joinType: JoinType): Join<Y, T> }
            interface Join<Z, X> : From<Z, X>
            interface Predicate : Expression<Boolean>
            interface CriteriaBuilder {
                fun and(vararg predicates: Predicate): Predicate
                fun or(vararg predicates: Predicate): Predicate
                fun not(predicate: Predicate): Predicate
            }
            interface CriteriaQuery<T> {
                fun where(predicate: Predicate): CriteriaQuery<T>
                fun distinct(distinct: Boolean): CriteriaQuery<T>
                fun orderBy(orders: List<Any>): CriteriaQuery<T>
                fun <E> subquery(resultClass: Class<E>): Subquery<E>
            }
            interface Subquery<T> : CriteriaQuery<T> {
                fun <E> from(entityClass: Class<E>): From<Any, E>
                fun select(expression: Expression<T>): Subquery<T>
            }
            enum class JoinType { INNER, LEFT, RIGHT }
            """.trimIndent(),
        ),
        SourceFile.kotlin(
            "SpecificationStub.kt",
            """
            package org.springframework.data.jpa.domain

            import jakarta.persistence.criteria.CriteriaBuilder
            import jakarta.persistence.criteria.CriteriaQuery
            import jakarta.persistence.criteria.From
            import jakarta.persistence.criteria.Predicate

            fun interface Specification<T> {
                fun toPredicate(root: From<Any, T>, query: CriteriaQuery<*>, builder: CriteriaBuilder): Predicate?
            }
            """.trimIndent(),
        ),
        SourceFile.kotlin(
            "SchemaRuntimeStubs.kt",
            """
            package com.only4.cap4k.ddd.domain.repo.schema

            import jakarta.persistence.criteria.CriteriaBuilder
            import jakarta.persistence.criteria.CriteriaQuery
            import jakarta.persistence.criteria.Expression
            import jakarta.persistence.criteria.Path
            import jakarta.persistence.criteria.Predicate
            import jakarta.persistence.criteria.Subquery

            fun interface SchemaSpecification<E, S> {
                fun toPredicate(schema: S, query: CriteriaQuery<*>, builder: CriteriaBuilder): Predicate?
            }
            fun interface PredicateBuilder<S> { fun build(schema: S): Predicate }
            fun interface OrderBuilder<S> { fun build(schema: S): Any }
            fun interface ExpressionBuilder<S, E> { fun build(schema: S): Expression<E> }
            fun interface SubqueryConfigure<E, S> { fun configure(subquery: Subquery<E>, schema: S) }
            open class Field<T>(path: Path<T>, criteriaBuilder: CriteriaBuilder)
            class RelationCollectionField<T>(path: Path<Collection<T>>, criteriaBuilder: CriteriaBuilder)
            class RelationOptionalField<T>(path: Path<Collection<T>>, criteriaBuilder: CriteriaBuilder)
            enum class JoinType {
                INNER, LEFT, RIGHT;
                fun toJpaJoinType(): jakarta.persistence.criteria.JoinType =
                    jakarta.persistence.criteria.JoinType.valueOf(name)
            }
            """.trimIndent(),
        ),
        SourceFile.kotlin(
            "VideoPostEntities.kt",
            """
            package com.acme.demo.domain.aggregates.video_post

            class VideoPost
            class VideoPostItem
            """.trimIndent(),
        ),
        SourceFile.kotlin(
            "SVideoPostItem.kt",
            """
            package com.acme.demo.domain._share.meta.video_post

            import com.acme.demo.domain.aggregates.video_post.VideoPostItem
            import jakarta.persistence.criteria.CriteriaBuilder
            import jakarta.persistence.criteria.From

            class SVideoPostItem(val root: From<*, VideoPostItem>, val criteriaBuilder: CriteriaBuilder)
            """.trimIndent(),
        ),
        SourceFile.kotlin(
            "SchemaRelationJoinBehavior.kt",
            """
            package com.acme.demo.domain._share.meta.video_post

            import com.acme.demo.domain.aggregates.video_post.VideoPost
            import jakarta.persistence.criteria.CriteriaBuilder
            import jakarta.persistence.criteria.From
            import jakarta.persistence.criteria.Join
            import jakarta.persistence.criteria.JoinType
            import jakarta.persistence.criteria.Path
            import jakarta.persistence.criteria.Predicate

            object SchemaRelationJoinBehavior {
                @JvmStatic
                fun verify() {
                    val root = RecordingRoot()
                    val schema = SVideoPost(root, RecordingCriteriaBuilder())

                    val first = schema.joinItems()
                    val second = schema.joinItems()

                    check(first === second)
                    check(first.root === second.root)
                    check(root.joinCalls == 1)
                    val failure = runCatching { schema.joinItems(com.only4.cap4k.ddd.domain.repo.schema.JoinType.LEFT) }.exceptionOrNull()
                    check(failure is IllegalStateException)
                    check(failure.message!!.contains("schema relation items is already joined as INNER"))
                    check(root.joinCalls == 1)
                }
            }

            private class RecordingRoot : From<Any, VideoPost> {
                var joinCalls = 0

                override fun <Y> get(name: String): Path<Y> = error("not used")

                override fun <Y, T> join(name: String, joinType: JoinType): Join<Y, T> {
                    check(name == "_items")
                    check(joinType == JoinType.INNER)
                    joinCalls += 1
                    return RecordingJoin()
                }
            }

            private class RecordingJoin<Z, X> : Join<Z, X> {
                override fun <Y> get(name: String): Path<Y> = error("not used")
                override fun <Y, T> join(name: String, joinType: JoinType): Join<Y, T> = error("not used")
            }

            private class RecordingCriteriaBuilder : CriteriaBuilder {
                override fun and(vararg predicates: Predicate): Predicate = error("not used")
                override fun or(vararg predicates: Predicate): Predicate = error("not used")
                override fun not(predicate: Predicate): Predicate = error("not used")
            }
            """.trimIndent(),
        ),
    )

    private fun assertMaintainableTemplateSource(templateId: String) {
        val content = Files.readString(Path.of("src/main/resources/presets/ddd-default", templateId))
        val compressedPatterns = listOf(
            Regex("""(?m)^\s*\{%-?\s*endif\s*-?%}\{%-?\s*else\s*-?%}"""),
            Regex("""(?m)^\s*\{%-?\s*endif\s*-?%}\{%-?\s*if"""),
            Regex("""(?m)^\s*\{%-?\s*if [^%]+-?%}[ \t]*\S"""),
            Regex("""(?m)^\s*\{%-?\s*else\s*-?%}[ \t]*\S"""),
            Regex("""(?m)^\s*\{%-?\s*for [^%]+-?%}[ \t]*\S"""),
            Regex("""(?m)^\s*\{%-?\s*endfor\s*-?%}[ \t]*\S"""),
        )

        compressedPatterns.forEach { pattern ->
            assertFalse(
                pattern.containsMatchIn(content),
                "Template $templateId must keep Pebble control tags separate from Kotlin output.",
            )
        }
    }

    private fun String.normalizedLineEndings(): String = replace("\r\n", "\n")

    private fun renderTemplate(
        templateId: String,
        outputPath: String,
        context: Map<String, Any?>,
    ): String =
        PebbleArtifactRenderer(
            templateResolver = PresetTemplateResolver("ddd-default", emptyList())
        ).render(
            planItems = listOf(
                ArtifactPlanItem(
                    generatorId = "test",
                    moduleRole = "domain",
                    templateId = templateId,
                    outputPath = outputPath,
                    context = if (templateId == "aggregate/entity.kt.peb") {
                        mapOf(
                            "aggregateName" to context["typeName"],
                            "aggregateRoot" to true,
                        ) + context
                    } else {
                        context
                    },
                    conflictPolicy = ConflictPolicy.SKIP,
                )
            ),
            config = ProjectConfig(
                basePackage = "com.acme.demo",
                layout = ProjectLayout.MULTI_MODULE,
                modules = emptyMap(),
                sources = emptyMap(),
                generators = emptyMap(),
                templates = TemplateConfig("ddd-default", emptyList(), ConflictPolicy.SKIP),
            ),
        ).single().content

    private fun aggregateElementContext(
        aggregate: String,
        name: String,
        type: String,
        root: Boolean = false,
        packageName: String = "",
        description: String = "",
    ): Map<String, Any?> =
        mapOf(
            "aggregate" to aggregate,
            "aggregateKotlinStringLiteral" to aggregate.toTestKotlinStringLiteral(),
            "name" to name,
            "nameKotlinStringLiteral" to name.toTestKotlinStringLiteral(),
            "packageName" to packageName,
            "packageNameKotlinStringLiteral" to packageName.toTestKotlinStringLiteral(),
            "description" to description,
            "descriptionKotlinStringLiteral" to description.toTestKotlinStringLiteral(),
            "type" to type,
            "typeKotlinStringLiteral" to type.toTestKotlinStringLiteral(),
            "root" to root,
        )

    private fun buildingBlockContext(
        tag: String,
        name: String,
        family: String,
        packageName: String = "",
        description: String = "",
        aggregates: List<String> = emptyList(),
        eventName: String = "",
        variant: String = "",
    ): Map<String, Any?> =
        mapOf(
            "tag" to tag,
            "tagKotlinStringLiteral" to tag.toTestKotlinStringLiteral(),
            "name" to name,
            "nameKotlinStringLiteral" to name.toTestKotlinStringLiteral(),
            "packageName" to packageName,
            "packageNameKotlinStringLiteral" to packageName.toTestKotlinStringLiteral(),
            "description" to description,
            "descriptionKotlinStringLiteral" to description.toTestKotlinStringLiteral(),
            "aggregates" to aggregates,
            "aggregateKotlinStringLiterals" to aggregates.map { it.toTestKotlinStringLiteral() },
            "eventName" to eventName,
            "eventNameKotlinStringLiteral" to eventName.toTestKotlinStringLiteral(),
            "family" to family,
            "familyKotlinStringLiteral" to family.toTestKotlinStringLiteral(),
            "variant" to variant,
            "variantKotlinStringLiteral" to variant.toTestKotlinStringLiteral(),
        )

    private fun assertBuildingBlockAnnotation(
        content: String,
        tag: String,
        name: String,
        family: String,
        variant: String = "",
        aggregates: List<String> = listOf("Order"),
        eventName: String = "",
    ) {
        assertTrue(content.contains("import com.only4.cap4k.analysis.metadata.DesignBlockMetadata"))
        assertTrue(content.contains("@DesignBlockMetadata("))
        assertTrue(content.contains("tag = \"$tag\""))
        assertTrue(content.contains("name = \"$name\""))
        assertTrue(content.contains("aggregates = [${aggregates.joinToString(", ") { "\"$it\"" }}]"))
        assertTrue(content.contains("family = \"$family\""))
        if (eventName.isBlank()) {
            assertFalse(content.contains("eventName = "))
        } else {
            assertTrue(content.contains("eventName = ${eventName.toTestKotlinStringLiteral()}"))
        }
        if (variant.isBlank()) {
            assertFalse(content.contains("variant = \"\""))
        } else {
            assertTrue(content.contains("variant = \"$variant\""))
        }
    }

    private fun String.toTestKotlinStringLiteral(): String {
        val escaped = buildString {
            this@toTestKotlinStringLiteral.forEach { char ->
                when (char) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    '\b' -> append("\\b")
                    '\u000C' -> append("\\u000c")
                    '$' -> append("\\$")
                    else -> {
                        if (char.code in 0x00..0x1F) {
                            append("\\u")
                            append(char.code.toString(16).padStart(4, '0'))
                        } else {
                            append(char)
                        }
                    }
                }
            }
        }
        return "\"$escaped\""
    }

    @Test
    fun `value object template renders pure semantic value without persistence imports`() {
        val content = renderTemplate(
            templateId = ProjectConfig().artifactLayout.valueObject.id,
            outputPath = "demo-domain/src/main/kotlin/com/acme/demo/domain/shared/values/Money.kt",
            context = mapOf(
                "packageName" to "com.acme.demo.domain.shared.values",
                "typeName" to "Money",
                "name" to "Money",
                "buildingBlock" to buildingBlockContext(
                    tag = "value_object",
                    name = "Money",
                    packageName = "com.acme.demo.domain.shared.values",
                    description = "money */ \"amount\" \\value ${'$'}currency",
                    aggregates = listOf("Order"),
                    family = "value-object",
                ),
                "imports" to listOf(
                    "com.acme.demo.domain.shared.types.CurrencyCode",
                    "java.math.BigDecimal",
                ),
                "fields" to listOf(
                    mapOf("name" to "amount", "renderedType" to "BigDecimal", "defaultValue" to null),
                    mapOf("name" to "currency", "renderedType" to "CurrencyCode?", "defaultValue" to "null"),
                ),
                "nestedTypes" to emptyList<Map<String, Any?>>(),
            ),
        )

        assertReadableKotlin(content)
        assertTrue(content.contains("package com.acme.demo.domain.shared.values"))
        assertFalse(content.contains("com.fasterxml.jackson"))
        assertFalse(content.contains("jakarta.persistence"))
        assertFalse(content.contains("ObjectMapper"))
        assertFalse(content.contains("AttributeConverter"))
        assertTrue(content.contains("import com.only4.cap4k.analysis.metadata.DesignBlockMetadata"))
        assertTrue(content.contains("import com.acme.demo.domain.shared.types.CurrencyCode"))
        assertTrue(content.contains("import java.math.BigDecimal"))
        assertTrue(content.contains("@DesignBlockMetadata("))
        assertTrue(content.contains("tag = \"value_object\""))
        assertTrue(content.contains("name = \"Money\""))
        assertTrue(content.contains("packageName = \"com.acme.demo.domain.shared.values\""))
        assertTrue(content.contains("description = \"money */ \\\"amount\\\" \\\\value \\${'$'}currency\""))
        assertTrue(content.contains("aggregates = [\"Order\"]"))
        assertFalse(content.contains("eventName = "))
        assertTrue(content.contains("family = \"value-object\""))
        assertFalse(content.contains("variant = \"\""))
        assertTrue(content.contains("data class Money("))
        assertTrue(content.contains("val amount: BigDecimal,"))
        assertTrue(content.contains("val currency: CurrencyCode? = null"))
        assertFalse(content.contains("val amount: java.math.BigDecimal"))
    }

    @Test
    fun `value object template renders flattened recursive nested values`() {
        val content = renderTemplate(
            templateId = ProjectConfig().artifactLayout.valueObject.id,
            outputPath = "demo-domain/src/main/kotlin/com/acme/demo/domain/shared/values/OrderAddress.kt",
            context = mapOf(
                "packageName" to "com.acme.demo.domain.shared.values",
                "typeName" to "OrderAddress",
                "name" to "OrderAddress",
                "imports" to emptyList<String>(),
                "fields" to listOf(
                    mapOf("name" to "address", "renderedType" to "Address", "defaultValue" to null),
                    mapOf("name" to "items", "renderedType" to "List<Item>", "defaultValue" to null),
                ),
                "nestedTypes" to listOf(
                    mapOf(
                        "name" to "Address",
                        "fields" to listOf(
                            mapOf("name" to "city", "renderedType" to "String", "defaultValue" to null),
                        ),
                    ),
                    mapOf(
                        "name" to "Item",
                        "fields" to listOf(
                            mapOf("name" to "name", "renderedType" to "String", "defaultValue" to null),
                            mapOf("name" to "details", "renderedType" to "Details", "defaultValue" to null),
                        ),
                    ),
                    mapOf(
                        "name" to "Details",
                        "fields" to listOf(
                            mapOf("name" to "code", "renderedType" to "String?", "defaultValue" to "null"),
                        ),
                    ),
                ),
            ),
        )

        assertReadableKotlin(content)
        assertTrue(content.contains("val address: Address,"))
        assertTrue(content.contains("val items: List<Item>"))
        assertTrue(content.contains("data class Address("))
        assertTrue(content.contains("data class Item("))
        assertTrue(content.contains("val details: Details"))
        assertTrue(content.contains("data class Details("))
        assertTrue(content.contains("val code: String? = null"))
    }

    @Test
    fun `value object json converter template renders top level generated adapter`() {
        val content = renderTemplate(
            templateId = ProjectConfig().artifactLayout.valueObjectJsonConverter.id,
            outputPath = "demo-domain/build/generated/cap4k/main/kotlin/com/acme/demo/domain/shared/values/MoneyJsonAttributeConverter.kt",
            context = mapOf(
                "packageName" to "com.acme.demo.domain.shared.values",
                "typeName" to "MoneyJsonAttributeConverter",
                "valueObjectTypeName" to "Money",
                "valueObjectTypeFqn" to "com.acme.demo.domain.shared.values.Money",
                "imports" to emptyList<String>(),
            ),
        )

        assertReadableKotlin(content)
        assertTrue(content.contains("import com.fasterxml.jackson.databind.ObjectMapper"))
        assertTrue(content.contains("import com.fasterxml.jackson.module.kotlin.readValue"))
        assertTrue(content.contains("import jakarta.persistence.AttributeConverter"))
        assertTrue(content.contains("@jakarta.persistence.Converter(autoApply = false)"))
        assertTrue(content.contains("class MoneyJsonAttributeConverter : AttributeConverter<Money, String>"))
        assertTrue(content.contains("mapper.writeValueAsString(attribute)"))
        assertTrue(content.contains("mapper.readValue<Money>(it)"))
        assertFalse(content.contains("DesignBlockMetadata"))
    }

    @Test
    fun `design domain service skeleton template id resolves through default preset`() {
        val config = ProjectConfig()

        val domainService = renderTemplate(
            templateId = config.artifactLayout.designDomainService.id,
            outputPath = "demo-domain/src/main/kotlin/content/domain/ContentPublicationPolicy.kt",
            context = mapOf(
                "packageName" to "content.domain",
                "name" to "ContentPublicationPolicy",
                "description" to "publication policy",
                "aggregates" to listOf("Content"),
            ),
        )
        assertTrue(domainService.contains("import com.only4.cap4k.ddd.core.domain.service.annotation.DomainService"))
        assertTrue(domainService.contains("class ContentPublicationPolicy"))
        assertReadableKotlin(domainService)

    }

    @Test
    fun `aggregate factory template renders semantic payload metadata and filtered payload fields`() {
        val content = renderTemplate(
            templateId = "aggregate/factory.kt.peb",
            outputPath = "demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/factory/VideoPostFactory.kt",
            context = mapOf(
                "packageName" to "com.acme.demo.domain.aggregates.video_post.factory",
                "typeName" to "VideoPostFactory",
                "payloadTypeName" to "Payload",
                "payloadMetadataName" to "VideoPostPayload",
                "payloadFields" to listOf(
                    mapOf("name" to "title", "renderedType" to "String?", "defaultValue" to null),
                ),
                "rootConstructorFields" to listOf(mapOf("name" to "title")),
                "rootRelations" to emptyList<Map<String, Any?>>(),
                "helpers" to emptyList<Map<String, Any?>>(),
                "entityName" to "VideoPost",
                "entityTypeFqn" to "com.acme.demo.domain.aggregates.video_post.VideoPost",
                "aggregateName" to "VideoPost",
                "imports" to listOf("com.acme.demo.domain.aggregates.video_post.VideoPost"),
            ),
        )

        assertFalse(content.contains(legacyAggregateCall))
        assertTrue(content.contains("data class Payload("))
        assertTrue(content.contains("val title: String?"))
        assertFalse(content.contains("val id: Long"))
        assertFalse(content.contains("val name: String"))
    }

    @Test
    fun `aggregate templates render aggregate element only when context is present`() {
        val entityContent = renderTemplate(
            templateId = "aggregate/entity.kt.peb",
            outputPath = "demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/VideoPost.kt",
            context = mapOf(
                "packageName" to "com.acme.demo.domain.aggregates.video_post",
                "typeName" to "VideoPost",
                "comment" to "video post",
                "aggregateRoot" to true,
                "aggregateElement" to aggregateElementContext(
                    aggregate = "VideoPost",
                    name = "VideoPost",
                    type = "entity",
                    root = true,
                    packageName = "video_post",
                    description = "video post",
                ),
                "entityJpa" to mapOf("entityEnabled" to false),
                "hasStrongIdFields" to false,
                "hasEmbeddedStrongIdFields" to false,
                "hasGeneratedValueFields" to false,
                "hasEmbeddedIdFields" to false,
                "hasVersionFields" to false,
                "hasConverterFields" to false,
                "dynamicInsert" to false,
                "dynamicUpdate" to false,
                "softDeleteSql" to null,
                "softDeleteWhereClause" to null,
                "jpaImports" to emptyList<String>(),
                "imports" to emptyList<String>(),
                "scalarFields" to emptyList<Map<String, Any?>>(),
                "relationFields" to emptyList<Map<String, Any?>>(),
            ),
        )
        val factoryContent = renderTemplate(
            templateId = "aggregate/factory.kt.peb",
            outputPath = "demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/factory/VideoPostFactory.kt",
            context = mapOf(
                "packageName" to "com.acme.demo.domain.aggregates.video_post.factory",
                "typeName" to "VideoPostFactory",
                "payloadTypeName" to "Payload",
                "payloadMetadataName" to "VideoPostPayload",
                "payloadFields" to emptyList<Map<String, Any?>>(),
                "rootConstructorFields" to emptyList<Map<String, Any?>>(),
                "rootRelations" to emptyList<Map<String, Any?>>(),
                "helpers" to emptyList<Map<String, Any?>>(),
                "entityName" to "VideoPost",
                "entityTypeFqn" to "com.acme.demo.domain.aggregates.video_post.VideoPost",
                "aggregateName" to "VideoPost",
                "imports" to listOf("com.acme.demo.domain.aggregates.video_post.VideoPost"),
                "aggregateElement" to aggregateElementContext(
                    aggregate = "VideoPost",
                    name = "VideoPostFactory",
                    type = "factory",
                ),
            ),
        )

        assertTrue(entityContent.contains("import com.only4.cap4k.analysis.metadata.AggregateElementMetadata"))
        assertTrue(entityContent.contains("@AggregateElementMetadata("))
        assertTrue(entityContent.contains("""aggregate = "VideoPost""""))
        assertTrue(entityContent.contains("""packageName = "video_post""""))
        assertTrue(entityContent.contains("""type = "entity""""))
        assertTrue(entityContent.contains("root = true"))
        assertTrue(factoryContent.contains("@AggregateElementMetadata("))
        assertTrue(factoryContent.contains("""type = "factory""""))
        assertFalse(factoryContent.contains("""name = "VideoPostPayload""""))
        assertFalse(factoryContent.contains("factory-payload"))
        assertFalse(entityContent.contains(legacyAggregateCall))
        assertFalse(factoryContent.contains(legacyAggregateCall))
    }

    @Test
    @OptIn(org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi::class)
    fun `aggregate templates omit generated own id construction and compile`() {
        val entityContent = renderTemplate(
            templateId = "aggregate/entity.kt.peb",
            outputPath = "demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/content/Content.kt",
            context = mapOf(
                "packageName" to "com.acme.demo.domain.aggregates.content",
                "typeName" to "Content",
                "entityJpa" to mapOf("entityEnabled" to true, "tableName" to "content"),
                "hasStrongIdFields" to true,
                "hasEmbeddedStrongIdFields" to false,
                "hasGeneratedValueFields" to false,
                "hasEmbeddedIdFields" to true,
                "hasVersionFields" to false,
                "hasConverterFields" to false,
                "jpaImports" to emptyList<String>(),
                "imports" to listOf(
                    "com.acme.demo.domain.aggregates.content.ContentId",
                    "com.acme.demo.domain.shared.ids.AuthorId",
                ),
                "constructorFields" to listOf(
                    mapOf("name" to "title", "type" to "String", "nullable" to false, "defaultValue" to null),
                    mapOf("name" to "authorId", "type" to "AuthorId", "nullable" to false, "defaultValue" to null),
                ),
                "scalarFields" to entityScalarFields(
                    mapOf(
                        "name" to "id",
                        "type" to "ContentId",
                        "propertyInitializer" to "id",
                        "nullable" to false,
                        "propertyNullable" to false,
                        "columnName" to "id",
                        "isId" to true,
                        "strongId" to true,
                        "embeddedId" to true,
                        "generatedOwnId" to true,
                        "attributeOverrideNullable" to false,
                        "attributeOverrideInsertable" to null,
                        "attributeOverrideUpdatable" to false,
                    ),
                    mapOf(
                        "name" to "title",
                        "type" to "String",
                        "propertyInitializer" to "title",
                        "nullable" to false,
                        "propertyNullable" to false,
                        "columnName" to "title",
                    ),
                    mapOf(
                        "name" to "authorId",
                        "type" to "AuthorId",
                        "propertyInitializer" to "authorId",
                        "nullable" to false,
                        "propertyNullable" to false,
                        "columnName" to "author_id",
                    ),
                ),
                "relationFields" to emptyList<Map<String, Any?>>(),
            ),
        )
        val factoryContent = renderTemplate(
            templateId = "aggregate/factory.kt.peb",
            outputPath = "demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/content/factory/ContentFactory.kt",
            context = mapOf(
                "packageName" to "com.acme.demo.domain.aggregates.content.factory",
                "typeName" to "ContentFactory",
                "payloadTypeName" to "Payload",
                "payloadMetadataName" to "ContentPayload",
                "payloadFields" to listOf(
                    mapOf("name" to "title", "renderedType" to "String", "defaultValue" to null),
                    mapOf("name" to "authorId", "renderedType" to "AuthorId", "defaultValue" to null),
                ),
                "rootConstructorFields" to listOf(
                    mapOf("name" to "title"),
                    mapOf("name" to "authorId"),
                ),
                "rootRelations" to emptyList<Map<String, Any?>>(),
                "helpers" to emptyList<Map<String, Any?>>(),
                "entityName" to "Content",
                "entityTypeFqn" to "com.acme.demo.domain.aggregates.content.Content",
                "aggregateName" to "Content",
                "imports" to listOf(
                    "com.acme.demo.domain.aggregates.content.Content",
                    "com.acme.demo.domain.shared.ids.AuthorId",
                ),
            ),
        )

        assertReadableKotlin(entityContent)
        assertReadableKotlin(factoryContent)
        assertTrue(entityContent.contains("class Content internal constructor(\n    title: String,\n    authorId: AuthorId\n)"))
        assertTrue(entityContent.contains("lateinit var id: ContentId\n        internal set"))
        assertFalse(factoryContent.contains("import com.acme.demo.domain.aggregates.content.ContentId"))
        assertTrue(factoryContent.contains("import com.acme.demo.domain.shared.ids.AuthorId"))
        assertTrue(factoryContent.contains("override fun create(entityPayload: Payload): Content ="))
        assertTrue(factoryContent.contains("Content("))
        assertTrue(
            factoryContent.normalizedLineEndings().contains(
                "override fun create(entityPayload: Payload): Content =\n        Content("
            )
        )
        assertFalse(factoryContent.contains("id ="))
        assertFalse(factoryContent.contains(".new()"))
        assertTrue(factoryContent.contains("title = entityPayload.title"))
        assertTrue(factoryContent.contains("authorId = entityPayload.authorId"))
        assertTrue(factoryContent.contains("data class Payload("))
        assertTrue(factoryContent.contains("val title: String,"))
        assertTrue(factoryContent.contains("val authorId: AuthorId"))
        assertFalse(factoryContent.contains("val id: ContentId"))

        val result = KotlinCompilation().apply {
            sources = listOf(
                SourceFile.kotlin("Content.kt", entityContent),
                SourceFile.kotlin("ContentFactory.kt", factoryContent),
                SourceFile.kotlin(
                    "AggregateContracts.kt",
                    """
                    package com.only4.cap4k.ddd.core.domain.aggregate

                    interface AggregatePayload<ENTITY : Any>
                    interface AggregateFactory<PAYLOAD : AggregatePayload<ENTITY>, ENTITY : Any> {
                        fun create(entityPayload: PAYLOAD): ENTITY
                    }
                    """.trimIndent(),
                ),
                SourceFile.kotlin(
                    "Service.kt",
                    """
                    package org.springframework.stereotype

                    @Target(AnnotationTarget.CLASS)
                    annotation class Service
                    """.trimIndent(),
                ),
                SourceFile.kotlin(
                    "Jpa.kt",
                    """
                    package jakarta.persistence

                    @Target(AnnotationTarget.CLASS)
                    annotation class Entity
                    @Target(AnnotationTarget.CLASS)
                    annotation class Table(val name: String)
                    @Target(AnnotationTarget.PROPERTY, AnnotationTarget.FIELD)
                    annotation class EmbeddedId
                    @Target(AnnotationTarget.PROPERTY, AnnotationTarget.FIELD)
                    annotation class AttributeOverride(val name: String, val column: Column)
                    @Target(AnnotationTarget.PROPERTY, AnnotationTarget.FIELD)
                    annotation class Column(
                        val name: String,
                        val nullable: Boolean = true,
                        val insertable: Boolean = true,
                        val updatable: Boolean = true,
                    )
                    """.trimIndent(),
                ),
                SourceFile.kotlin(
                    "Ids.kt",
                    """
                    package com.acme.demo.domain.aggregates.content

                    class ContentId
                    """.trimIndent(),
                ),
                SourceFile.kotlin(
                    "AuthorId.kt",
                    """
                    package com.acme.demo.domain.shared.ids

                    class AuthorId
                    """.trimIndent(),
                ),
            )
            inheritClassPath = true
            supportsK2 = true
        }.compile()

        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
    }

    @Test
    @OptIn(org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi::class)
    fun `provider assigned identity and version stay outside entity and factory construction and compile`() {
        val entityContent = renderTemplate(
            templateId = "aggregate/entity.kt.peb",
            outputPath = "demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/VideoPost.kt",
            context = mapOf(
                "packageName" to "com.acme.demo.domain.aggregates.video_post",
                "typeName" to "VideoPost",
                "entityJpa" to mapOf("entityEnabled" to true, "tableName" to "video_post"),
                "hasStrongIdFields" to false,
                "hasEmbeddedStrongIdFields" to false,
                "hasGeneratedValueFields" to true,
                "hasEmbeddedIdFields" to false,
                "hasVersionFields" to true,
                "hasConverterFields" to false,
                "jpaImports" to emptyList<String>(),
                "imports" to emptyList<String>(),
                "constructorFields" to listOf(
                    mapOf("name" to "title", "type" to "String", "nullable" to false, "defaultValue" to null),
                ),
                "scalarFields" to entityScalarFields(
                    mapOf(
                        "name" to "id",
                        "type" to "Long",
                        "propertyInitializer" to "null",
                        "nullable" to false,
                        "propertyNullable" to true,
                        "columnName" to "id",
                        "isId" to true,
                        "generatedValueStrategy" to "IDENTITY",
                        "isVersion" to false,
                    ),
                    mapOf(
                        "name" to "version",
                        "type" to "Long",
                        "propertyInitializer" to "null",
                        "nullable" to false,
                        "propertyNullable" to true,
                        "columnName" to "version",
                        "isId" to false,
                        "isVersion" to true,
                    ),
                    mapOf(
                        "name" to "title",
                        "type" to "String",
                        "propertyInitializer" to "title",
                        "nullable" to false,
                        "propertyNullable" to false,
                        "columnName" to "title",
                        "isId" to false,
                        "isVersion" to false,
                    ),
                ),
                "relationFields" to emptyList<Map<String, Any?>>(),
            ),
        )
        val factoryContent = renderTemplate(
            templateId = "aggregate/factory.kt.peb",
            outputPath =
                "demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/factory/VideoPostFactory.kt",
            context = mapOf(
                "packageName" to "com.acme.demo.domain.aggregates.video_post.factory",
                "typeName" to "VideoPostFactory",
                "payloadTypeName" to "Payload",
                "payloadMetadataName" to "VideoPostPayload",
                "payloadFields" to listOf(
                    mapOf("name" to "title", "renderedType" to "String", "defaultValue" to null)
                ),
                "rootConstructorFields" to listOf(mapOf("name" to "title")),
                "rootRelations" to emptyList<Map<String, Any?>>(),
                "helpers" to emptyList<Map<String, Any?>>(),
                "entityName" to "VideoPost",
                "entityTypeFqn" to "com.acme.demo.domain.aggregates.video_post.VideoPost",
                "aggregateName" to "VideoPost",
                "imports" to listOf("com.acme.demo.domain.aggregates.video_post.VideoPost"),
            ),
        )

        assertReadableKotlin(entityContent)
        assertReadableKotlin(factoryContent)
        val result = KotlinCompilation().apply {
            sources = listOf(
                SourceFile.kotlin("VideoPost.kt", entityContent),
                SourceFile.kotlin("VideoPostFactory.kt", factoryContent),
                SourceFile.kotlin(
                    "AggregateContracts.kt",
                    """
                    package com.only4.cap4k.ddd.core.domain.aggregate

                    interface AggregatePayload<ENTITY : Any>
                    interface AggregateFactory<PAYLOAD : AggregatePayload<ENTITY>, ENTITY : Any> {
                        fun create(entityPayload: PAYLOAD): ENTITY
                    }
                    """.trimIndent(),
                ),
                SourceFile.kotlin(
                    "Service.kt",
                    """
                    package org.springframework.stereotype

                    @Target(AnnotationTarget.CLASS)
                    annotation class Service
                    """.trimIndent(),
                ),
                SourceFile.kotlin(
                    "Jpa.kt",
                    """
                    package jakarta.persistence

                    @Target(AnnotationTarget.CLASS)
                    annotation class Entity
                    @Target(AnnotationTarget.CLASS)
                    annotation class Table(val name: String)
                    @Target(AnnotationTarget.PROPERTY, AnnotationTarget.FIELD)
                    annotation class Id
                    @Target(AnnotationTarget.PROPERTY, AnnotationTarget.FIELD)
                    annotation class GeneratedValue(val strategy: GenerationType)
                    enum class GenerationType { IDENTITY }
                    @Target(AnnotationTarget.PROPERTY, AnnotationTarget.FIELD)
                    annotation class Version
                    @Target(AnnotationTarget.PROPERTY, AnnotationTarget.FIELD)
                    annotation class Column(
                        val name: String,
                        val insertable: Boolean = true,
                        val updatable: Boolean = true,
                    )
                    """.trimIndent(),
                ),
            )
            inheritClassPath = true
            supportsK2 = true
        }.compile()

        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
        assertTrue(
            entityContent.contains(
                "class VideoPost internal constructor(\n    title: String\n)"
            ),
            entityContent,
        )
        assertTrue(entityContent.contains("var id: Long? = null"), entityContent)
        assertTrue(entityContent.contains("var version: Long? = null"), entityContent)
        assertTrue(entityContent.contains("@GeneratedValue(strategy = GenerationType.IDENTITY)"), entityContent)
        assertTrue(entityContent.contains("@Version"), entityContent)
        assertFalse(entityContent.substringBefore(") {").contains("id:"), entityContent)
        assertFalse(entityContent.substringBefore(") {").contains("version:"), entityContent)
        assertTrue(factoryContent.contains("VideoPost("), factoryContent)
        assertTrue(factoryContent.contains("title = entityPayload.title"), factoryContent)
        assertFalse(factoryContent.contains("id ="), factoryContent)
        assertFalse(factoryContent.contains("version ="), factoryContent)
        assertFalse(factoryContent.contains("TODO(\"Implement aggregate construction\")"), factoryContent)
    }

    @Test
    @OptIn(org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi::class)
    fun `soft delete entity matrix initializes raw storage outside constructors and factories compile`() {
        data class MatrixCell(
            val typeName: String,
            val backingType: String,
            val deletedType: String,
            val propertyInitializer: String,
            val storageKind: String,
            val activeSentinel: String,
            val sqlActiveLiteral: String,
            val applicationSideId: Boolean,
            val needsUuidImport: Boolean = false,
        ) {
            val packageName: String =
                "com.acme.demo.domain.aggregates.soft_delete.${typeName.replace(Regex("([a-z])([A-Z])"), "$1_$2").lowercase()}"
            val tableName: String = typeName.replace(Regex("([a-z])([A-Z])"), "$1_$2").lowercase()
            val idType: String = if (applicationSideId) "${typeName}Id" else backingType
        }

        data class RenderedCell(
            val cell: MatrixCell,
            val entityContent: String,
            val factoryContent: String?,
            val sqlDelete: String,
            val whereClause: String,
        )

        fun kotlinStringLiteral(value: String): String = buildString {
            append('"')
            value.forEach { char ->
                when (char) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    '$' -> append("\\$")
                    else -> append(char)
                }
            }
            append('"')
        }

        val nilUuid = "00000000-0000-0000-0000-000000000000"
        val cells = listOf(
            MatrixCell(
                typeName = "IdentityLongRecord",
                backingType = "Long",
                deletedType = "Long",
                propertyInitializer = "0L",
                storageKind = "INTEGRAL",
                activeSentinel = "ZERO",
                sqlActiveLiteral = "0",
                applicationSideId = false,
            ),
            MatrixCell(
                typeName = "SnowflakeLongRecord",
                backingType = "Long",
                deletedType = "Long",
                propertyInitializer = "0L",
                storageKind = "INTEGRAL",
                activeSentinel = "ZERO",
                sqlActiveLiteral = "0",
                applicationSideId = true,
            ),
            MatrixCell(
                typeName = "SnowflakeStringRecord",
                backingType = "String",
                deletedType = "String",
                propertyInitializer = "\"0\"",
                storageKind = "CHARACTER",
                activeSentinel = "ZERO",
                sqlActiveLiteral = "'0'",
                applicationSideId = true,
            ),
            MatrixCell(
                typeName = "Uuid7StringRecord",
                backingType = "String",
                deletedType = "String",
                propertyInitializer = "\"$nilUuid\"",
                storageKind = "CHARACTER",
                activeSentinel = "NIL_UUID",
                sqlActiveLiteral = "'$nilUuid'",
                applicationSideId = true,
            ),
            MatrixCell(
                typeName = "Uuid7NativeRecord",
                backingType = "UUID",
                deletedType = "UUID",
                propertyInitializer = "UUID(0L, 0L)",
                storageKind = "NATIVE_UUID",
                activeSentinel = "NIL_UUID",
                sqlActiveLiteral = "CAST('$nilUuid' AS UUID)",
                applicationSideId = true,
                needsUuidImport = true,
            ),
        )

        val compilationSources = mutableListOf<SourceFile>()
        val renderedCells = cells.map { cell ->
            val sqlDelete =
                "update \"${cell.tableName}\" set \"deleted\" = \"id\" where \"id\" = ?"
            val whereClause = "\"deleted\" = ${cell.sqlActiveLiteral}"
            val constructorFields = buildList {
                if (!cell.applicationSideId) {
                    add(
                        mapOf(
                            "name" to "id",
                            "type" to "Long",
                            "nullable" to false,
                            "defaultValue" to "0L",
                        )
                    )
                }
                add(
                    mapOf(
                        "name" to "title",
                        "type" to "String",
                        "nullable" to false,
                        "defaultValue" to null,
                    )
                )
            }
            val idField = mapOf(
                "fieldName" to "id",
                "fieldType" to cell.idType,
                "name" to "id",
                "type" to cell.idType,
                "propertyInitializer" to "id",
                "nullable" to false,
                "propertyNullable" to !cell.applicationSideId,
                "defaultValue" to if (cell.applicationSideId) null else "0L",
                "strongId" to cell.applicationSideId,
                "embeddedId" to cell.applicationSideId,
                "generatedOwnId" to cell.applicationSideId,
                "columnName" to "id",
                "columnNameKotlinStringLiteral" to kotlinStringLiteral("\"id\""),
                "isId" to true,
                "generatedValueStrategy" to if (cell.applicationSideId) null else "IDENTITY",
                "isVersion" to false,
                "attributeOverrideNullable" to false,
                "attributeOverrideInsertable" to true,
                "attributeOverrideUpdatable" to false,
                "attributeOverrideLength" to if (cell.backingType == "String") 36 else null,
            )
            val titleField = mapOf(
                "fieldName" to "title",
                "fieldType" to "String",
                "name" to "title",
                "type" to "String",
                "propertyInitializer" to "title",
                "nullable" to false,
                "propertyNullable" to false,
                "defaultValue" to null,
                "strongId" to false,
                "embeddedId" to false,
                "generatedOwnId" to false,
                "columnName" to "title",
                "columnNameKotlinStringLiteral" to kotlinStringLiteral("\"title\""),
                "isId" to false,
                "isVersion" to false,
            )
            val deletedField = mapOf(
                "fieldName" to "deleted",
                "fieldType" to cell.deletedType,
                "name" to "deleted",
                "type" to cell.deletedType,
                "propertyInitializer" to cell.propertyInitializer,
                "nullable" to false,
                "propertyNullable" to false,
                "defaultValue" to null,
                "strongId" to false,
                "embeddedId" to false,
                "generatedOwnId" to false,
                "columnName" to "deleted",
                "columnNameKotlinStringLiteral" to kotlinStringLiteral("\"deleted\""),
                "isId" to false,
                "isVersion" to false,
                "insertable" to true,
                "updatable" to false,
                "writePolicy" to "SYSTEM_TRANSITION_ONLY",
            )
            val entityContent = renderTemplate(
                templateId = "aggregate/entity.kt.peb",
                outputPath =
                    "demo-domain/src/main/kotlin/${cell.packageName.replace('.', '/')}/${cell.typeName}.kt",
                context = mapOf(
                    "packageName" to cell.packageName,
                    "typeName" to cell.typeName,
                    "entityJpa" to mapOf(
                        "entityEnabled" to true,
                        "tableName" to cell.tableName,
                        "tableNameKotlinStringLiteral" to
                            kotlinStringLiteral("\"${cell.tableName}\""),
                    ),
                    "hasStrongIdFields" to cell.applicationSideId,
                    "hasEmbeddedStrongIdFields" to false,
                    "hasGeneratedValueFields" to !cell.applicationSideId,
                    "hasEmbeddedIdFields" to cell.applicationSideId,
                    "hasVersionFields" to false,
                    "hasConverterFields" to false,
                    "softDelete" to mapOf(
                        "enabled" to true,
                        "columnName" to "deleted",
                        "storageKind" to cell.storageKind,
                        "activeSentinel" to cell.activeSentinel,
                        "tombstoneStrategy" to "SELF_ID",
                    ),
                    "softDeleteSql" to sqlDelete,
                    "softDeleteWhereClause" to whereClause,
                    "softDeleteSqlKotlinStringLiteral" to kotlinStringLiteral(sqlDelete),
                    "softDeleteWhereClauseKotlinStringLiteral" to kotlinStringLiteral(whereClause),
                    "jpaImports" to emptyList<String>(),
                    "imports" to if (cell.needsUuidImport) listOf("java.util.UUID") else emptyList(),
                    "constructorFields" to constructorFields,
                    "scalarFields" to entityScalarFields(idField, titleField, deletedField),
                    "relationFields" to emptyList<Map<String, Any?>>(),
                ),
            )

            val constructorBlock = entityContent
                .substringAfter("class ${cell.typeName} internal constructor(")
                .substringBefore(") {")
            assertFalse(constructorBlock.contains("deleted"), cell.typeName)
            if (cell.applicationSideId) {
                assertFalse(constructorBlock.contains(cell.idType), cell.typeName)
                assertTrue(entityContent.contains("lateinit var id: ${cell.idType}"), cell.typeName)
            } else {
                assertTrue(constructorBlock.contains("id: Long = 0L"), cell.typeName)
            }
            assertTrue(
                entityContent.contains("@SQLDelete(sql = ${kotlinStringLiteral(sqlDelete)})"),
                cell.typeName,
            )
            assertTrue(
                entityContent.contains("@Where(clause = ${kotlinStringLiteral(whereClause)})"),
                cell.typeName,
            )
            if (cell.needsUuidImport) {
                assertEquals(
                    1,
                    Regex("(?m)^import java\\.util\\.UUID$").findAll(entityContent.normalizedLineEndings()).count(),
                    cell.typeName,
                )
            }

            compilationSources += SourceFile.kotlin("${cell.typeName}.kt", entityContent)

            val factoryContent = if (cell.applicationSideId) {
                val renderedFactory = renderTemplate(
                    templateId = "aggregate/factory.kt.peb",
                    outputPath =
                        "demo-domain/src/main/kotlin/${cell.packageName.replace('.', '/')}/factory/${cell.typeName}Factory.kt",
                    context = mapOf(
                        "packageName" to "${cell.packageName}.factory",
                        "typeName" to "${cell.typeName}Factory",
                        "payloadTypeName" to "Payload",
                        "payloadMetadataName" to "${cell.typeName}Payload",
                        "payloadFields" to listOf(
                            mapOf("name" to "title", "renderedType" to "String", "defaultValue" to null)
                        ),
                        "rootConstructorFields" to listOf(mapOf("name" to "title")),
                        "rootRelations" to emptyList<Map<String, Any?>>(),
                        "helpers" to emptyList<Map<String, Any?>>(),
                        "entityName" to cell.typeName,
                        "entityTypeFqn" to "${cell.packageName}.${cell.typeName}",
                        "aggregateName" to cell.typeName,
                        "imports" to listOf("${cell.packageName}.${cell.typeName}"),
                    ),
                )
                assertTrue(renderedFactory.contains("${cell.typeName}("), cell.typeName)
                assertTrue(renderedFactory.contains("title = entityPayload.title"), cell.typeName)
                assertFalse(renderedFactory.contains("TODO(\"Implement aggregate construction\")"), cell.typeName)
                assertFalse(renderedFactory.contains("deleted"), cell.typeName)
                assertFalse(renderedFactory.contains(cell.idType), cell.typeName)
                compilationSources += SourceFile.kotlin("${cell.typeName}Factory.kt", renderedFactory)
                compilationSources += SourceFile.kotlin(
                    "${cell.typeName}Id.kt",
                    """
                    package ${cell.packageName}

                    import com.only4.cap4k.ddd.core.domain.id.StrongId
                    ${if (cell.needsUuidImport) "import java.util.UUID" else ""}

                    class ${cell.idType}(override val value: ${cell.backingType}) : StrongId<${cell.backingType}>
                    """.trimIndent(),
                )
                compilationSources += SourceFile.kotlin(
                    "${cell.typeName}FactoryUsage.kt",
                    """
                    package ${cell.packageName}

                    import ${cell.packageName}.factory.${cell.typeName}Factory

                    fun create${cell.typeName}(factory: ${cell.typeName}Factory): ${cell.typeName} =
                        factory.create(${cell.typeName}Factory.Payload(title = "demo"))
                    """.trimIndent(),
                )
                renderedFactory
            } else {
                compilationSources += SourceFile.kotlin(
                    "${cell.typeName}Construction.kt",
                    """
                    package ${cell.packageName}

                    fun construct${cell.typeName}(): ${cell.typeName} = ${cell.typeName}(title = "demo")
                    """.trimIndent(),
                )
                null
            }

            RenderedCell(cell, entityContent, factoryContent, sqlDelete, whereClause)
        }

        compilationSources += listOf(
            SourceFile.kotlin(
                "AggregateContracts.kt",
                """
                package com.only4.cap4k.ddd.core.domain.aggregate

                interface AggregatePayload<ENTITY : Any>
                interface AggregateFactory<PAYLOAD : AggregatePayload<ENTITY>, ENTITY : Any> {
                    fun create(entityPayload: PAYLOAD): ENTITY
                }
                """.trimIndent(),
            ),
            SourceFile.kotlin(
                "StrongId.kt",
                """
                package com.only4.cap4k.ddd.core.domain.id

                interface StrongId<T> {
                    val value: T
                }
                """.trimIndent(),
            ),
            SourceFile.kotlin(
                "Service.kt",
                """
                package org.springframework.stereotype

                @Target(AnnotationTarget.CLASS)
                annotation class Service
                """.trimIndent(),
            ),
            SourceFile.kotlin(
                "JpaSoftDelete.kt",
                """
                package jakarta.persistence

                @Target(AnnotationTarget.CLASS)
                annotation class Entity
                @Target(AnnotationTarget.CLASS)
                annotation class Table(val name: String)
                @Target(AnnotationTarget.PROPERTY, AnnotationTarget.FIELD)
                annotation class Id
                @Target(AnnotationTarget.PROPERTY, AnnotationTarget.FIELD)
                annotation class EmbeddedId
                @Target(AnnotationTarget.PROPERTY, AnnotationTarget.FIELD)
                annotation class Embedded
                @Target(AnnotationTarget.PROPERTY, AnnotationTarget.FIELD)
                annotation class GeneratedValue(val strategy: GenerationType)
                enum class GenerationType { IDENTITY }
                @Target(AnnotationTarget.PROPERTY, AnnotationTarget.FIELD)
                annotation class AttributeOverride(val name: String, val column: Column)
                @Target(AnnotationTarget.PROPERTY, AnnotationTarget.FIELD, AnnotationTarget.ANNOTATION_CLASS)
                annotation class Column(
                    val name: String,
                    val nullable: Boolean = true,
                    val insertable: Boolean = true,
                    val updatable: Boolean = true,
                    val length: Int = 255,
                )
                """.trimIndent(),
            ),
            SourceFile.kotlin(
                "HibernateSoftDelete.kt",
                """
                package org.hibernate.annotations

                @Target(AnnotationTarget.CLASS)
                annotation class SQLDelete(val sql: String)
                @Target(AnnotationTarget.CLASS)
                annotation class Where(val clause: String)
                """.trimIndent(),
            ),
        )

        val result = KotlinCompilation().apply {
            sources = compilationSources
            inheritClassPath = true
            supportsK2 = true
        }.compile()

        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)

        renderedCells.forEach { rendered ->
            val cell = rendered.cell
            assertTrue(
                rendered.entityContent.contains(
                    "var deleted: ${cell.deletedType} = ${cell.propertyInitializer}"
                ),
                cell.typeName,
            )
            if (cell.applicationSideId) {
                assertFalse(
                    rendered.entityContent.contains("var deleted: ${cell.idType}"),
                    cell.typeName,
                )
                assertFalse(rendered.factoryContent.orEmpty().contains(cell.idType), cell.typeName)
            }
            assertFalse(rendered.entityContent.contains("= deleted"), cell.typeName)
            assertTrue(
                rendered.entityContent.contains(kotlinStringLiteral(rendered.sqlDelete)),
                cell.typeName,
            )
            assertTrue(
                rendered.entityContent.contains(kotlinStringLiteral(rendered.whereClause)),
                cell.typeName,
            )
        }
    }

    @Test
    fun `aggregate factory template renders normalized payload types with stable indentation`() {
        val content = renderTemplate(
            templateId = "aggregate/factory.kt.peb",
            outputPath = "demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/factory/VideoPostFactory.kt",
            context = mapOf(
                "packageName" to "com.acme.demo.domain.aggregates.video_post.factory",
                "typeName" to "VideoPostFactory",
                "payloadTypeName" to "Payload",
                "payloadMetadataName" to "VideoPostPayload",
                "payloadFields" to listOf(
                    mapOf(
                        "name" to "status",
                        "type" to "com.acme.demo.domain.aggregates.video_post.enums.VideoPostStatus",
                        "renderedType" to "VideoPostStatus",
                        "nullable" to false,
                    ),
                    mapOf(
                        "name" to "snapshot",
                        "type" to "com.acme.demo.domain.aggregates.video_post.values.VideoPostSnapshot",
                        "renderedType" to "VideoPostSnapshot?",
                        "nullable" to true,
                    ),
                    mapOf(
                        "name" to "updatedAt",
                        "type" to "java.time.LocalDateTime",
                        "renderedType" to "LocalDateTime",
                        "nullable" to false,
                    ),
                ),
                "rootConstructorFields" to emptyList<Map<String, Any?>>(),
                "rootRelations" to emptyList<Map<String, Any?>>(),
                "helpers" to emptyList<Map<String, Any?>>(),
                "entityName" to "VideoPost",
                "entityTypeFqn" to "com.acme.demo.domain.aggregates.video_post.VideoPost",
                "aggregateName" to "VideoPost",
                "imports" to listOf(
                    "com.acme.demo.domain.aggregates.video_post.VideoPost",
                    "com.acme.demo.domain.aggregates.video_post.enums.VideoPostStatus",
                    "com.acme.demo.domain.aggregates.video_post.values.VideoPostSnapshot",
                    "java.time.LocalDateTime",
                ),
            ),
        )

        val normalized = content.normalizedLineEndings()
        assertReadableKotlin(content)
        assertTrue(content.contains("import com.acme.demo.domain.aggregates.video_post.enums.VideoPostStatus"))
        assertTrue(content.contains("import com.acme.demo.domain.aggregates.video_post.values.VideoPostSnapshot"))
        assertTrue(content.contains("import java.time.LocalDateTime"))
        assertTrue(
            normalized.contains(
                """
                |    data class Payload(
                |        val status: VideoPostStatus,
                |        val snapshot: VideoPostSnapshot?,
                |        val updatedAt: LocalDateTime
                |    ) : AggregatePayload<VideoPost>
                """.trimMargin()
            )
        )
        assertFalse(content.contains("val status: com.acme.demo"))
        assertFalse(content.contains("\nval status:"))
    }

    @Test
    fun `aggregate projection and schema templates render normalized field-like types`() {
        val projectionContent = renderTemplate(
            templateId = "aggregate_projection/entity.kt.peb",
            outputPath = "demo-adapter/build/generated/cap4k/main/kotlin/com/acme/demo/adapter/application/projections/video_post/VideoPostProjection.kt",
            context = mapOf(
                "packageName" to "com.acme.demo.adapter.application.projections.video_post",
                "typeName" to "VideoPostProjection",
                "entityJpa" to mapOf(
                    "entityEnabled" to true,
                    "tableName" to "video_post",
                ),
                "hasConverterFields" to false,
                "hasVersionFields" to false,
                "imports" to listOf("com.acme.demo.domain.aggregates.video_post.enums.VideoPostStatus"),
                "scalarFields" to entityScalarFields(
                    mapOf(
                        "name" to "status",
                        "type" to "com.acme.demo.domain.aggregates.video_post.enums.VideoPostStatus",
                        "renderedType" to "VideoPostStatus",
                        "nullable" to false,
                        "propertyNullable" to false,
                        "columnName" to "status",
                        "isId" to false,
                        "isVersion" to false,
                        "converterClassRef" to null,
                    ),
                ),
                "relationFields" to emptyList<Map<String, Any?>>(),
            ),
        )
        val schemaContent = renderTemplate(
            templateId = "aggregate/schema.kt.peb",
            outputPath = "demo-domain/src/main/kotlin/com/acme/demo/domain/_share/meta/video_post/SVideoPost.kt",
            context = mapOf(
                "packageName" to "com.acme.demo.domain._share.meta.video_post",
                "typeName" to "SVideoPost",
                "entityName" to "VideoPost",
                "schemaRuntimePackage" to "com.only4.cap4k.ddd.domain.repo.schema",
                "entityTypeFqn" to "com.acme.demo.domain.aggregates.video_post.VideoPost",
                "isAggregateRoot" to true,
                "imports" to listOf("com.acme.demo.domain.aggregates.video_post.enums.VideoPostStatus"),
                "fields" to listOf(
                    mapOf(
                        "name" to "status",
                        "fieldName" to "status",
                        "fieldType" to "com.acme.demo.domain.aggregates.video_post.enums.VideoPostStatus",
                        "type" to "com.acme.demo.domain.aggregates.video_post.enums.VideoPostStatus",
                        "renderedType" to "VideoPostStatus",
                        "comment" to "",
                    ),
                ),
            ),
        )
        assertReadableKotlin(projectionContent)
        assertReadableKotlin(schemaContent)
        assertTrue(projectionContent.contains("status: VideoPostStatus"))
        assertTrue(projectionContent.contains("var status: VideoPostStatus = status"))
        assertTrue(schemaContent.contains("val status: Field<VideoPostStatus>"))
        assertFalse(projectionContent.contains("status: com.acme.demo"))
        assertFalse(schemaContent.contains("Field<com.acme.demo"))
    }

    @Test
    fun `design command template renders strong id request field imports`() {
        val content = renderTemplate(
            templateId = "design/command.kt.peb",
            outputPath = "demo-application/src/main/kotlin/com/acme/demo/application/commands/content/CreateContentCmd.kt",
            context = mapOf(
                "packageName" to "com.acme.demo.application.commands.content",
                "typeName" to "CreateContentCmd",
                "description" to "create content",
                "descriptionText" to "create content",
                "descriptionCommentText" to "create content",
                "descriptionKotlinStringLiteral" to "\"create content\"",
                "aggregateName" to null,
                "imports" to listOf("com.acme.demo.domain.shared.ids.AuthorId"),
                "fields" to listOf(
                    mapOf("name" to "authorId", "renderedType" to "AuthorId", "nullable" to false),
                ),
                "resultFields" to emptyList<Map<String, Any?>>(),
                "nestedTypes" to emptyList<Map<String, Any?>>(),
                "resultNestedTypes" to emptyList<Map<String, Any?>>(),
                "pageRequest" to false,
            ),
        )

        assertReadableKotlin(content)
        assertTrue(content.contains("import com.acme.demo.domain.shared.ids.AuthorId"))
        assertTrue(content.contains("val authorId: AuthorId"))
    }

    @Test
    fun `aggregate factory template renders resolved empty payload as valid empty class`() {
        val content = renderTemplate(
            templateId = "aggregate/factory.kt.peb",
            outputPath = "demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/factory/VideoPostFactory.kt",
            context = mapOf(
                "packageName" to "com.acme.demo.domain.aggregates.video_post.factory",
                "typeName" to "VideoPostFactory",
                "payloadTypeName" to "Payload",
                "payloadMetadataName" to "VideoPostPayload",
                "payloadFields" to emptyList<Map<String, Any?>>(),
                "rootConstructorFields" to emptyList<Map<String, Any?>>(),
                "rootRelations" to emptyList<Map<String, Any?>>(),
                "helpers" to emptyList<Map<String, Any?>>(),
                "entityName" to "VideoPost",
                "entityTypeFqn" to "com.acme.demo.domain.aggregates.video_post.VideoPost",
                "aggregateName" to "VideoPost",
                "imports" to listOf("com.acme.demo.domain.aggregates.video_post.VideoPost"),
            ),
        )

        assertFalse(content.contains(legacyAggregateCall))
        assertTrue(content.contains("class Payload : AggregatePayload<VideoPost>"))
        assertFalse(content.contains("data class Payload("))
        assertFalse(content.contains("val name: String"))
    }

    @Test
    @OptIn(org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi::class)
    fun `managed field catalog renders identifier admission binding and compiles`() {
        val catalogFqn = "com.acme.demo.domain._share.managed.ManagedFieldCatalogContribution"
        val catalogSource = renderTemplate(
            templateId = "aggregate/managed_field_catalog.kt.peb",
            outputPath = "demo-domain/build/generated/cap4k/main/kotlin/${catalogFqn.replace('.', '/')}.kt",
            context = mapOf(
                "packageName" to catalogFqn.substringBeforeLast('.'),
                "typeName" to catalogFqn.substringAfterLast('.'),
                "beanName" to "com.acme.demo.domain._share.managed.managedFieldCatalogContribution",
                "bindings" to listOf(
                    mapOf(
                        "entityTypeExpression" to "com.acme.demo.domain.orders.Order::class",
                        "fieldName" to "id",
                        "fieldNameKotlinStringLiteral" to "\"id\"",
                        "persistencePropertyNameKotlinStringLiteral" to "\"id\"",
                        "columnNameKotlinStringLiteral" to "\"id\"",
                        "targetTypeExpression" to "com.acme.demo.domain.orders.OrderId::class",
                        "targetTypeCheck" to "com.acme.demo.domain.orders.OrderId",
                        "nullable" to false,
                        "policyKeyKotlinStringLiteral" to "\"identifier.uuid7\"",
                        "role" to "IDENTIFIER",
                        "explicitValue" to "PRESERVE_IF_VALID",
                        "lifecycles" to listOf("ENTITY_ADMISSION"),
                        "handlerQualifierKotlinStringLiteral" to null,
                        "handlerSlotKotlinStringLiteral" to null,
                        "semanticTypeExpression" to "com.acme.demo.domain.orders.OrderId::class",
                        "valueAdapterQualifierKotlinStringLiteral" to null,
                        "insertAuthority" to "FRAMEWORK",
                        "updateAuthority" to "NONE",
                        "runtimeSupport" to mapOf(
                            "kind" to "APPLICATION_IDENTIFIER",
                            "allocateExpression" to
                                "com.acme.demo.domain.orders.OrderId.of(" +
                                    "com.only4.cap4k.ddd.core.Mediator.identifiers.next(" +
                                    "\"uuid7\", String::class))",
                            "validateExpression" to "com.acme.demo.domain.orders.OrderId.of(value.value)",
                        ),
                    ),
                ),
                "imports" to emptyList<String>(),
            ),
        )

        assertReadableKotlin(catalogSource)
        assertTrue(catalogSource.contains("class ManagedFieldCatalogContribution : ManagedFieldCatalog"))
        assertTrue(catalogSource.contains("ManagedFieldLifecycle.ENTITY_ADMISSION"))
        assertTrue(catalogSource.contains("ManagedFieldRuntimeSupport.ApplicationIdentifier("))
        assertTrue(catalogSource.contains("Mediator.identifiers.next(\"uuid7\", String::class)"))
        assertFalse(catalogSource.contains("GeneratedOwnId"))

        val result = KotlinCompilation().apply {
            sources = listOf(
                SourceFile.kotlin("ManagedFieldCatalogContribution.kt", catalogSource),
                SourceFile.kotlin(
                    "Order.kt",
                    """
                    package com.acme.demo.domain.orders

                    class Order {
                        lateinit var id: OrderId
                            internal set
                    }

                    class OrderId private constructor(val value: String) {
                        companion object {
                            fun of(value: String): OrderId = OrderId(value)
                        }
                    }
                    """.trimIndent(),
                ),
                SourceFile.kotlin(
                    "Component.kt",
                    """
                    package org.springframework.stereotype

                    @Target(AnnotationTarget.CLASS)
                    annotation class Component(val value: String = "")
                    """.trimIndent(),
                ),
            )
            inheritClassPath = true
            messageOutputStream = System.out
            jvmTarget = "17"
            supportsK2 = true
        }.compile()

        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
        val catalog = result.classLoader
            .loadClass(catalogFqn)
            .getDeclaredConstructor()
            .newInstance() as com.only4.cap4k.ddd.core.domain.managed.ManagedFieldCatalog
        assertEquals(listOf("id"), catalog.bindings.map { it.fieldName })
        assertEquals(
            com.only4.cap4k.ddd.core.domain.managed.ManagedFieldRole.IDENTIFIER,
            catalog.bindings.single().role,
        )
    }

    @Test
    @OptIn(org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi::class)
    fun `aggregate strong id template renders four storage nearest variants with scalar string json`() {
        fun renderStrongId(
            packageName: String,
            valueType: String,
            validationKind: String,
            stringBacked: Boolean,
            uuidBacked: Boolean,
            longBacked: Boolean,
        ): String =
            renderTemplate(
                templateId = "aggregate/strong_id.kt.peb",
                outputPath = "demo-domain/build/generated/cap4k/main/kotlin/${packageName.replace('.', '/')}/OrderId.kt",
                context = mapOf(
                    "packageName" to packageName,
                    "typeName" to "OrderId",
                    "valueType" to valueType,
                    "validationKind" to validationKind,
                    "stringBacked" to stringBacked,
                    "uuidBacked" to uuidBacked,
                    "longBacked" to longBacked,
                    "imports" to emptyList<String>(),
                ),
            )

        val uuidText = renderStrongId("com.acme.demo.ids.uuidtext", "String", "UUID7", true, false, false)
        val uuidNative = renderStrongId("com.acme.demo.ids.uuidnative", "UUID", "UUID7", false, true, false)
        val snowflakeText = renderStrongId("com.acme.demo.ids.snowflaketext", "String", "SNOWFLAKE", true, false, false)
        val snowflakeLong = renderStrongId("com.acme.demo.ids.snowflakelong", "Long", "SNOWFLAKE", false, false, true)

        assertTrue(uuidText.contains("StrongId<String>"))
        assertTrue(uuidText.contains("fun of(value: String): OrderId"))
        assertTrue(uuidNative.contains("StrongId<UUID>"))
        assertTrue(uuidNative.contains("fun of(value: UUID): OrderId"))
        assertTrue(snowflakeText.contains("StrongIds.requireSnowflake(value, \"OrderId\")"))
        assertTrue(snowflakeLong.contains("override var value: Long = 0L"))
        assertTrue(snowflakeLong.contains("fun jsonValue(): String = value.toString()"))
        listOf(uuidText, uuidNative, snowflakeText, snowflakeLong).forEach { source ->
            assertReadableKotlin(source)
            assertFalse(source.contains("fun new("))
            assertFalse(source.contains("AttributeConverter"))
            assertFalse(source.contains("length ="))
            assertTrue(source.contains("value.isTextual"))
            assertTrue(source.contains("@JvmStatic\n        fun from(value: String): OrderId = parse(value)"))
            assertTrue(
                source.contains(
                    "@JsonCreator(mode = JsonCreator.Mode.DISABLED)\n    private constructor(value:"
                )
            )
        }
        assertMaintainableTemplateSource("aggregate/strong_id.kt.peb")

        val generatedSources = listOf(
            SourceFile.kotlin("UuidTextOrderId.kt", uuidText),
            SourceFile.kotlin("UuidNativeOrderId.kt", uuidNative),
            SourceFile.kotlin("SnowflakeTextOrderId.kt", snowflakeText),
            SourceFile.kotlin("SnowflakeLongOrderId.kt", snowflakeLong),
        )
        val result = KotlinCompilation().apply {
            sources = generatedSources + strongIdCompileStubs
            inheritClassPath = true
            supportsK2 = true
        }.compile()

        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
    }

    private val strongIdCompileStubs = listOf(
        SourceFile.kotlin(
            "StrongId.kt",
            """
            package com.only4.cap4k.ddd.core.domain.id

            interface StrongId<T> {
                val value: T
            }
            """.trimIndent(),
        ),
        SourceFile.kotlin(
            "StrongIds.kt",
            """
            package com.only4.cap4k.ddd.core.domain.id

            import java.util.UUID

            object StrongIds {
                fun requireUuidV7(value: String, typeName: String): String = value
                fun requireUuidV7(value: UUID, typeName: String): UUID = value
                fun requireSnowflake(value: String, typeName: String): String = value
                fun requireSnowflake(value: Long, typeName: String): Long = value
            }
            """.trimIndent(),
        ),
        SourceFile.kotlin(
            "JacksonAnnotationStubs.kt",
            """
            package com.fasterxml.jackson.annotation

            @Target(AnnotationTarget.FUNCTION, AnnotationTarget.CONSTRUCTOR)
            annotation class JsonCreator(val mode: Mode = Mode.DEFAULT) {
                enum class Mode { DEFAULT, DELEGATING, DISABLED }
            }

            @Target(AnnotationTarget.FUNCTION)
            annotation class JsonValue
            """.trimIndent(),
        ),
        SourceFile.kotlin(
            "JsonNodeStub.kt",
            """
            package com.fasterxml.jackson.databind

            class JsonNode {
                val isTextual: Boolean = true
                fun textValue(): String = ""
            }
            """.trimIndent(),
        ),
        SourceFile.kotlin(
            "JakartaPersistenceStubs.kt",
            """
            package jakarta.persistence

            @Target(AnnotationTarget.CLASS)
            annotation class Embeddable

            @Target(AnnotationTarget.PROPERTY, AnnotationTarget.FIELD)
            annotation class Column(
                val name: String,
                val nullable: Boolean = true,
                val updatable: Boolean = true,
            )
            """.trimIndent(),
        ),
    )

    @Test
    fun `renderer preserves artifact output ownership metadata`() {
        val overrideDir = Files.createTempDirectory("cap4k-renderer-output-kind")
        val overrideAggregateDir = Files.createDirectories(overrideDir.resolve("aggregate"))
        overrideAggregateDir.resolve("behavior.kt.peb").writeText(
            "package {{ packageName }}\n"
        )
        val renderer = PebbleArtifactRenderer(
            templateResolver = PresetTemplateResolver(
                preset = "ddd-default",
                overrideDirs = listOf(overrideDir.toString()),
            )
        )

        val rendered = renderer.render(
            planItems = listOf(
                ArtifactPlanItem(
                    generatorId = "aggregate",
                    moduleRole = "domain",
                    templateId = "aggregate/behavior.kt.peb",
                    outputPath = "demo-domain/build/generated/cap4k/main/kotlin/com/acme/demo/Category.kt",
                    context = mapOf("packageName" to "com.acme.demo"),
                    conflictPolicy = ConflictPolicy.SKIP,
                    outputKind = ArtifactOutputKind.GENERATED_SOURCE,
                    resolvedOutputRoot = "demo-domain/build/generated/cap4k/main/kotlin",
                )
            ),
            config = ProjectConfig(
                basePackage = "com.acme.demo",
                layout = ProjectLayout.MULTI_MODULE,
                modules = emptyMap(),
                sources = emptyMap(),
                generators = emptyMap(),
                templates = TemplateConfig(
                    preset = "ddd-default",
                    overrideDirs = listOf(overrideDir.toString()),
                    conflictPolicy = ConflictPolicy.SKIP,
                )
            )
        )

        assertEquals(ArtifactOutputKind.GENERATED_SOURCE, rendered.single().outputKind)
        assertEquals("demo-domain/build/generated/cap4k/main/kotlin", rendered.single().resolvedOutputRoot)
    }

    @Test
    fun `aggregate entity template renders behavior safe mutable class shape`() {
        val content = renderTemplate(
            templateId = "aggregate/entity.kt.peb",
            outputPath = "demo-domain/build/generated/cap4k/main/kotlin/com/acme/demo/domain/aggregates/category/Category.kt",
            context = mapOf(
                "packageName" to "com.acme.demo.domain.aggregates.category",
                "typeName" to "Category",
                "entityJpa" to mapOf(
                    "entityEnabled" to true,
                    "tableName" to "category",
                ),
                "hasConverterFields" to false,
                "hasGeneratedValueFields" to false,
                "hasVersionFields" to false,
                "dynamicInsert" to false,
                "dynamicUpdate" to false,
                "softDeleteSql" to null,
                "softDeleteWhereClause" to null,
                "jpaImports" to listOf(
                    "jakarta.persistence.CascadeType",
                    "jakarta.persistence.FetchType",
                    "jakarta.persistence.JoinColumn",
                    "jakarta.persistence.OneToMany",
                ),
                "imports" to emptyList<String>(),
                "constructorFields" to listOf(
                    mapOf("name" to "id", "type" to "Long", "nullable" to false, "defaultValue" to "0L"),
                    mapOf("name" to "name", "type" to "String", "nullable" to false, "defaultValue" to "\"\""),
                ),
                "scalarFields" to entityScalarFields(
                    mapOf(
                        "name" to "id",
                        "type" to "Long",
                        "propertyInitializer" to "id",
                        "nullable" to false,
                        "propertyNullable" to false,
                        "defaultValue" to "0L",
                        "columnName" to "id",
                        "isId" to true,
                        "isVersion" to false,
                        "insertable" to null,
                        "updatable" to null,
                        "converterClassRef" to null,
                    ),
                    mapOf(
                        "name" to "name",
                        "type" to "String",
                        "propertyInitializer" to "name",
                        "nullable" to false,
                        "propertyNullable" to false,
                        "defaultValue" to "\"\"",
                        "columnName" to "name",
                        "isId" to false,
                        "isVersion" to false,
                        "insertable" to null,
                        "updatable" to null,
                        "converterClassRef" to null,
                    ),
                ),
                "relationFields" to listOf(
                    mapOf(
                        "relationType" to "ONE_TO_MANY",
                        "name" to "children",
                        "targetTypeRef" to "Category",
                        "fetchType" to "LAZY",
                        "cascadeTypes" to listOf("PERSIST", "MERGE", "REMOVE"),
                        "orphanRemoval" to true,
                        "joinColumn" to "parent_id",
                        "joinColumnNullable" to false,
                    )
                ),
            ),
        )

        assertTrue(content.contains("class Category internal constructor("))
        assertTrue(content.normalizedLineEndings().contains("class Category internal constructor(\n    id: Long = 0L,"))
        assertFalse(content.normalizedLineEndings().contains("class Category(\n    id: Long = 0L,"))
        assertFalse(content.contains("data class Category("))
        assertFalse(content.contains("val name: String"))
        assertTrue(content.contains("name: String = \"\""))
        assertTrue(content.contains("@Column(name = \"name\")\n    var name: String = name\n        internal set"))
        assertTrue(content.contains("val children: MutableList<Category> = mutableListOf()"))
        assertFalse(content.contains("managed-begin"))
    }

    @Test
    fun `aggregate entity template renders owned one as hidden collection plus transient single property`() {
        val content = renderTemplate(
            templateId = "aggregate/entity.kt.peb",
            outputPath = "demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/VideoPost.kt",
            context = mapOf(
                "packageName" to "com.acme.demo.domain.aggregates.video_post",
                "typeName" to "VideoPost",
                "comment" to "video post",
                "aggregateName" to "VideoPost",
                "aggregateRoot" to true,
                "entityJpa" to mapOf(
                    "entityEnabled" to true,
                    "tableName" to "video_post",
                ),
                "hasConverterFields" to false,
                "hasGeneratedValueFields" to false,
                "hasEmbeddedIdFields" to false,
                "hasStrongIdFields" to false,
                "hasEmbeddedStrongIdFields" to false,
                "hasVersionFields" to false,
                "softDelete" to mapOf("enabled" to false),
                "softDeleteSql" to null,
                "softDeleteWhereClause" to null,
                "softDeleteSqlKotlinStringLiteral" to null,
                "softDeleteWhereClauseKotlinStringLiteral" to null,
                "jpaImports" to listOf(
                    "jakarta.persistence.FetchType",
                    "jakarta.persistence.JoinColumn",
                    "jakarta.persistence.CascadeType",
                    "jakarta.persistence.OneToMany",
                    "jakarta.persistence.Transient",
                ),
                "imports" to listOf(
                    "com.acme.demo.domain.aggregates.video_post.VideoPostFile",
                    "com.only4.cap4k.ddd.core.domain.aggregate.OwnedEntityList",
                ),
                "idField" to mapOf("name" to "id", "type" to "Long"),
                "fields" to listOf(
                    mapOf(
                        "name" to "id",
                        "fieldName" to "id",
                        "fieldType" to "Long",
                        "renderedType" to "Long",
                        "nullable" to false,
                        "defaultValue" to null,
                        "columnName" to "id",
                        "isId" to true,
                        "embeddedId" to false,
                        "strongId" to false,
                        "isVersion" to false,
                        "converterClassRef" to null,
                    ),
                ),
                "scalarFields" to entityScalarFields(
                    mapOf(
                        "name" to "id",
                        "fieldName" to "id",
                        "fieldType" to "Long",
                        "renderedType" to "Long",
                        "nullable" to false,
                        "propertyNullable" to false,
                        "defaultValue" to null,
                        "columnName" to "id",
                        "isId" to true,
                        "embeddedId" to false,
                        "strongId" to false,
                        "isVersion" to false,
                        "converterClassRef" to null,
                    ),
                ),
                "relationFields" to listOf(
                    mapOf(
                        "name" to "files",
                        "targetType" to "VideoPostFile",
                        "targetTypeRef" to "VideoPostFile",
                        "targetPackageName" to "com.acme.demo.domain.aggregates.video_post",
                        "relationType" to "ONE_TO_MANY",
                        "fetchType" to "LAZY",
                        "joinColumn" to "video_post_id",
                        "nullable" to false,
                        "cascadeTypes" to listOf("PERSIST", "MERGE", "REMOVE"),
                        "orphanRemoval" to true,
                        "joinColumnNullable" to false,
                        "owned" to true,
                        "parentRefColumn" to "video_post_id",
                        "ownedCardinality" to "ONE",
                        "persistenceShape" to "ONE_TO_MANY_JOIN_COLUMN",
                        "domainName" to "file",
                        "persistencePathName" to "_files",
                        "backingCollectionName" to "_files",
                        "singleAccessorName" to "file",
                    )
                ),
            ),
        )

        assertReadableKotlin(content)
        assertTrue(content.contains("import jakarta.persistence.Transient"))
        assertTrue(content.contains("import com.only4.cap4k.ddd.core.domain.aggregate.OwnedEntityList"))
        assertTrue(content.contains("class VideoPost internal constructor("))
        assertTrue(content.contains("@OneToMany(fetch = FetchType.LAZY, cascade = [CascadeType.PERSIST, CascadeType.MERGE, CascadeType.REMOVE], orphanRemoval = true)"))
        assertTrue(content.contains("@JoinColumn(name = \"video_post_id\", nullable = false)"))
        assertTrue(content.contains("private var _files: MutableList<VideoPostFile> = mutableListOf()"))
        assertFalse(content.normalizedLineEndings().contains("\n    val files: MutableList<VideoPostFile> = mutableListOf()"))
        assertTrue(content.contains("@get:Transient"))
        assertTrue(content.contains("var file: VideoPostFile?"))
        assertTrue(content.contains("get() = OwnedEntityList.of(_files, VideoPostFile::class, \"VideoPost.file\")"))
        assertTrue(content.contains(".singleOrNull()"))
        assertTrue(content.contains("set(value)"))
        assertTrue(content.contains("OwnedEntityList.of(_files, VideoPostFile::class, \"VideoPost.file\")"))
        assertTrue(content.contains(".replace(value)"))
        assertFalse(content.contains("_files.clear()"))
        assertFalse(content.contains("_files.add(value)"))
    }

    @Test
    fun `aggregate entity template renders owned many as private backing collection plus facade`() {
        val content = renderTemplate(
            templateId = "aggregate/entity.kt.peb",
            outputPath = "demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/VideoPost.kt",
            context = mapOf(
                "packageName" to "com.acme.demo.domain.aggregates.video_post",
                "typeName" to "VideoPost",
                "comment" to "video post",
                "aggregateName" to "VideoPost",
                "aggregateRoot" to true,
                "entityJpa" to mapOf("entityEnabled" to true, "tableName" to "video_post"),
                "hasConverterFields" to false,
                "hasGeneratedValueFields" to false,
                "hasEmbeddedIdFields" to false,
                "hasStrongIdFields" to false,
                "hasEmbeddedStrongIdFields" to false,
                "hasVersionFields" to false,
                "softDelete" to mapOf("enabled" to false),
                "softDeleteSql" to null,
                "softDeleteWhereClause" to null,
                "softDeleteSqlKotlinStringLiteral" to null,
                "softDeleteWhereClauseKotlinStringLiteral" to null,
                "jpaImports" to listOf(
                    "jakarta.persistence.FetchType",
                    "jakarta.persistence.JoinColumn",
                    "jakarta.persistence.CascadeType",
                    "jakarta.persistence.OneToMany",
                    "jakarta.persistence.Transient",
                ),
                "imports" to listOf(
                    "com.acme.demo.domain.aggregates.video_post.VideoPostItem",
                    "com.only4.cap4k.ddd.core.domain.aggregate.OwnedEntityList",
                ),
                "idField" to mapOf("name" to "id", "type" to "Long"),
                "fields" to emptyList<Map<String, Any?>>(),
                "scalarFields" to emptyList<Map<String, Any?>>(),
                "relationFields" to listOf(
                    mapOf(
                        "name" to "items",
                        "targetType" to "VideoPostItem",
                        "targetTypeRef" to "VideoPostItem",
                        "targetPackageName" to "com.acme.demo.domain.aggregates.video_post",
                        "relationType" to "ONE_TO_MANY",
                        "fetchType" to "LAZY",
                        "joinColumn" to "video_post_id",
                        "nullable" to false,
                        "cascadeTypes" to listOf("PERSIST", "MERGE", "REMOVE"),
                        "orphanRemoval" to true,
                        "joinColumnNullable" to false,
                        "owned" to true,
                        "parentRefColumn" to "video_post_id",
                        "ownedCardinality" to "MANY",
                        "persistenceShape" to "ONE_TO_MANY_JOIN_COLUMN",
                        "domainName" to "items",
                        "persistencePathName" to "_items",
                        "backingCollectionName" to "_items",
                        "singleAccessorName" to null,
                    )
                ),
            ),
        )

        assertReadableKotlin(content)
        assertTrue(content.contains("import jakarta.persistence.Transient"))
        assertTrue(content.contains("import com.only4.cap4k.ddd.core.domain.aggregate.OwnedEntityList"))
        assertTrue(content.contains("class VideoPost internal constructor("))
        assertTrue(content.contains("private var _items: MutableList<VideoPostItem> = mutableListOf()"))
        assertTrue(content.contains("val items: OwnedEntityList<VideoPostItem>"))
        assertTrue(content.contains("get() = OwnedEntityList.of(_items, VideoPostItem::class, \"VideoPost.items\")"))
        assertFalse(content.normalizedLineEndings().contains("\n    val items: MutableList<VideoPostItem> = mutableListOf()"))
        assertFalse(content.contains("private val items: MutableList<VideoPostItem>"))
        assertFalse(content.contains("var item: VideoPostItem?"))
    }

    @Test
    @OptIn(org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi::class)
    fun `aggregate entity delegates owned child admission to OwnedEntityList and compiles`() {
        val packageName = "com.acme.demo.domain.aggregates.order"
        val entityContent = renderTemplate(
            templateId = "aggregate/entity.kt.peb",
            outputPath = "demo-domain/src/main/kotlin/${packageName.replace('.', '/')}/Order.kt",
            context = mapOf(
                "packageName" to packageName,
                "typeName" to "Order",
                "entityJpa" to mapOf("entityEnabled" to true, "tableName" to "orders"),
                "hasConverterFields" to false,
                "hasGeneratedValueFields" to false,
                "hasEmbeddedIdFields" to false,
                "hasStrongIdFields" to false,
                "hasEmbeddedStrongIdFields" to false,
                "hasVersionFields" to false,
                "softDelete" to mapOf("enabled" to false),
                "softDeleteSql" to null,
                "softDeleteWhereClause" to null,
                "softDeleteSqlKotlinStringLiteral" to null,
                "softDeleteWhereClauseKotlinStringLiteral" to null,
                "jpaImports" to listOf(
                    "jakarta.persistence.CascadeType",
                    "jakarta.persistence.FetchType",
                    "jakarta.persistence.JoinColumn",
                    "jakarta.persistence.OneToMany",
                    "jakarta.persistence.Transient",
                ),
                "imports" to listOf("com.only4.cap4k.ddd.core.domain.aggregate.OwnedEntityList"),
                "constructorFields" to emptyList<Map<String, Any?>>(),
                "scalarFields" to emptyList<Map<String, Any?>>(),
                "relationFields" to listOf(
                    mapOf(
                        "name" to "lines",
                        "targetType" to "OrderLine",
                        "targetTypeRef" to "OrderLine",
                        "targetPackageName" to packageName,
                        "relationType" to "ONE_TO_MANY",
                        "fetchType" to "LAZY",
                        "joinColumn" to "order_id",
                        "nullable" to false,
                        "cascadeTypes" to listOf("PERSIST", "MERGE", "REMOVE"),
                        "orphanRemoval" to true,
                        "joinColumnNullable" to false,
                        "owned" to true,
                        "ownedCardinality" to "MANY",
                        "domainName" to "lines",
                        "backingCollectionName" to "_lines",
                        "singleAccessorName" to null,
                    ),
                    mapOf(
                        "name" to "primaryLines",
                        "targetType" to "PrimaryOrderLine",
                        "targetTypeRef" to "PrimaryOrderLine",
                        "targetPackageName" to packageName,
                        "relationType" to "ONE_TO_MANY",
                        "fetchType" to "LAZY",
                        "joinColumn" to "primary_order_id",
                        "nullable" to false,
                        "cascadeTypes" to listOf("PERSIST", "MERGE", "REMOVE"),
                        "orphanRemoval" to true,
                        "joinColumnNullable" to false,
                        "owned" to true,
                        "ownedCardinality" to "ONE",
                        "domainName" to "primaryLine",
                        "backingCollectionName" to "_primaryLines",
                        "singleAccessorName" to "primaryLine",
                    ),
                ),
            ),
        )

        assertReadableKotlin(entityContent)
        assertFalse(entityContent.contains("GeneratedOwnId"))
        assertFalse(entityContent.contains("assignIfMissing"))
        assertFalse(entityContent.contains("prepare ="))
        assertTrue(
            entityContent.contains(
                "OwnedEntityList.of(_lines, OrderLine::class, \"Order.lines\")"
            )
        )
        assertEquals(
            2,
            Regex("OwnedEntityList\\.of\\(_primaryLines, PrimaryOrderLine::class, \"Order\\.primaryLine\"\\)")
                .findAll(entityContent)
                .count(),
        )

        val result = KotlinCompilation().apply {
            sources = listOf(
                SourceFile.kotlin("Order.kt", entityContent),
                SourceFile.kotlin(
                    "OwnedChildren.kt",
                    """
                    package $packageName

                    class OrderLine
                    class PrimaryOrderLine
                    """.trimIndent(),
                ),
                SourceFile.kotlin(
                    "Jpa.kt",
                    """
                    package jakarta.persistence

                    @Target(AnnotationTarget.CLASS)
                    annotation class Entity
                    @Target(AnnotationTarget.CLASS)
                    annotation class Table(val name: String)
                    @Target(AnnotationTarget.PROPERTY, AnnotationTarget.FIELD)
                    annotation class Id
                    @Target(AnnotationTarget.PROPERTY, AnnotationTarget.FIELD)
                    annotation class Column(val name: String)
                    enum class FetchType { LAZY, EAGER }
                    enum class CascadeType { PERSIST, MERGE, REMOVE }
                    @Target(AnnotationTarget.PROPERTY, AnnotationTarget.FIELD)
                    annotation class OneToMany(
                        val fetch: FetchType,
                        val cascade: Array<CascadeType> = [],
                        val orphanRemoval: Boolean = false,
                    )
                    @Target(AnnotationTarget.PROPERTY, AnnotationTarget.FIELD)
                    annotation class JoinColumn(val name: String, val nullable: Boolean = true)
                    @Target(AnnotationTarget.PROPERTY_GETTER)
                    annotation class Transient
                    """.trimIndent(),
                ),
            )
            inheritClassPath = true
            messageOutputStream = System.out
            jvmTarget = "17"
            supportsK2 = true
        }.compile()

        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
    }

    @Test
    fun `aggregate entity template omits aggregate element when metadata context is missing`() {
        val content = renderTemplate(
            templateId = "aggregate/entity.kt.peb",
            outputPath = "demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/category/Category.kt",
            context = mapOf(
                "packageName" to "com.acme.demo.domain.aggregates.category",
                "typeName" to "Category",
                "comment" to "category",
                "entityJpa" to mapOf(
                    "entityEnabled" to true,
                    "tableName" to "category",
                ),
                "hasStrongIdFields" to false,
                "hasEmbeddedStrongIdFields" to false,
                "hasEmbeddedIdFields" to false,
                "hasConverterFields" to false,
                "hasGeneratedValueFields" to false,
                "hasVersionFields" to false,
                "dynamicInsert" to false,
                "dynamicUpdate" to false,
                "softDeleteSql" to null,
                "softDeleteWhereClause" to null,
                "jpaImports" to emptyList<String>(),
                "imports" to emptyList<String>(),
                "scalarFields" to emptyList<Map<String, Any?>>(),
                "fields" to emptyList<Map<String, Any?>>(),
                "relationFields" to emptyList<Map<String, Any?>>(),
            ),
        )

        assertTrue(content.contains("@Entity"))
        assertFalse(content.contains("@AggregateElementMetadata("))
        assertFalse(content.contains(legacyAggregateCall))
    }

    @Test
    fun `aggregate projection template renders scalar jpa projection without relation graph`() {
        val content = renderTemplate(
            templateId = "aggregate_projection/entity.kt.peb",
            outputPath = "demo-adapter/build/generated/cap4k/main/kotlin/com/acme/demo/adapter/application/projections/category/CategoryProjection.kt",
            context = mapOf(
                "packageName" to "com.acme.demo.adapter.application.projections.category",
                "typeName" to "CategoryProjection",
                "entityJpa" to mapOf(
                    "entityEnabled" to true,
                    "tableName" to "category",
                ),
                "hasConverterFields" to true,
                "hasVersionFields" to true,
                "imports" to emptyList<String>(),
                "scalarFields" to entityScalarFields(
                    mapOf(
                        "name" to "id",
                        "type" to "Long",
                        "nullable" to false,
                        "propertyNullable" to false,
                        "columnName" to "id",
                        "isId" to true,
                        "isVersion" to false,
                        "converterTypeRef" to null,
                        "converterClassRef" to null,
                    ),
                    mapOf(
                        "name" to "name",
                        "type" to "String",
                        "nullable" to false,
                        "propertyNullable" to false,
                        "columnName" to "name",
                        "isId" to false,
                        "isVersion" to false,
                        "converterTypeRef" to null,
                        "converterClassRef" to "com.acme.demo.NameConverter",
                    ),
                    mapOf(
                        "name" to "version",
                        "type" to "Int",
                        "nullable" to false,
                        "propertyNullable" to false,
                        "columnName" to "version",
                        "isId" to false,
                        "isVersion" to true,
                        "converterTypeRef" to null,
                        "converterClassRef" to null,
                    ),
                ),
                "relationFields" to listOf(
                    mapOf(
                        "name" to "parent",
                        "relationType" to "MANY_TO_ONE",
                        "targetTypeRef" to "CategoryProjection",
                    )
                ),
            ),
        )

        assertReadableKotlin(content)
        assertMaintainableTemplateSource("aggregate_projection/entity.kt.peb")
        assertTrue(content.contains("@Entity"))
        assertTrue(content.contains("""@Table(name = "category")"""))
        assertTrue(content.contains("@Id"))
        assertTrue(content.contains("@Version"))
        assertTrue(content.contains("""@Column(name = "name")"""))
        assertTrue(content.contains("import com.acme.demo.NameConverter"))
        assertTrue(content.contains("@Convert(converter = NameConverter::class)"))
        assertTrue(
            content.contains(
                """@Column(name = "name")
    @Convert(converter = NameConverter::class)
    var name: String = name"""
            ),
            "Projection converter annotation should stay adjacent to the column and property declaration."
        )
        assertFalse(content.contains("@Convert(converter = com.acme.demo.NameConverter::class)"))
        assertTrue(content.contains("var name: String = name"))
        assertFalse(content.contains("ManyToOne"))
        assertFalse(content.contains("OneToMany"))
        assertFalse(content.contains("OneToOne"))
        assertFalse(content.contains("var parent"))
    }

    @Test
    fun `aggregate projection template respects disabled jpa entity metadata`() {
        val content = renderTemplate(
            templateId = "aggregate_projection/entity.kt.peb",
            outputPath = "demo-adapter/build/generated/cap4k/main/kotlin/com/acme/demo/adapter/application/projections/category/CategoryProjection.kt",
            context = mapOf(
                "packageName" to "com.acme.demo.adapter.application.projections.category",
                "typeName" to "CategoryProjection",
                "entityJpa" to mapOf(
                    "entityEnabled" to false,
                    "tableName" to "category",
                ),
                "hasConverterFields" to true,
                "hasVersionFields" to true,
                "imports" to emptyList<String>(),
                "scalarFields" to entityScalarFields(
                    mapOf(
                        "name" to "id",
                        "type" to "Long",
                        "nullable" to false,
                        "propertyNullable" to false,
                        "columnName" to "id",
                        "isId" to true,
                        "isVersion" to false,
                        "converterClassRef" to null,
                    ),
                    mapOf(
                        "name" to "name",
                        "type" to "String",
                        "nullable" to false,
                        "propertyNullable" to false,
                        "columnName" to "name",
                        "isId" to false,
                        "isVersion" to true,
                        "converterClassRef" to "com.acme.demo.NameConverter",
                    ),
                ),
                "relationFields" to emptyList<Map<String, Any?>>(),
            ),
        )

        assertReadableKotlin(content)
        assertFalse(content.contains("jakarta.persistence"))
        assertFalse(content.contains("@Entity"))
        assertFalse(content.contains("@Table"))
        assertFalse(content.contains("@Id"))
        assertFalse(content.contains("@Version"))
        assertFalse(content.contains("@Column"))
        assertFalse(content.contains("@Convert"))
        assertTrue(content.contains("class CategoryProjection("))
        assertTrue(content.contains("var name: String = name"))
    }

    @Test
    fun `aggregate entity template keeps explicit column flags for application side id fields`() {
        val content = renderTemplate(
            templateId = "aggregate/entity.kt.peb",
            outputPath = "demo-domain/build/generated/cap4k/main/kotlin/com/acme/demo/domain/aggregates/category/Category.kt",
            context = mapOf(
                "packageName" to "com.acme.demo.domain.aggregates.category",
                "typeName" to "Category",
                "entityJpa" to mapOf(
                    "entityEnabled" to true,
                    "tableName" to "category",
                ),
                "hasConverterFields" to false,
                "hasGeneratedValueFields" to false,
                "hasEmbeddedIdFields" to false,
                "hasVersionFields" to false,
                "dynamicInsert" to false,
                "dynamicUpdate" to false,
                "softDeleteSql" to null,
                "softDeleteWhereClause" to null,
                "jpaImports" to emptyList<String>(),
                "imports" to emptyList<String>(),
                "scalarFields" to entityScalarFields(
                    mapOf(
                        "name" to "id",
                        "type" to "java.util.UUID",
                        "nullable" to false,
                        "propertyNullable" to false,
                        "defaultValue" to null,
                        "columnName" to "id",
                        "isId" to true,
                        "writePolicy" to "CREATE_ONLY",
                        "isVersion" to false,
                        "insertable" to true,
                        "updatable" to false,
                        "converterClassRef" to null,
                    )
                ),
                "relationFields" to emptyList<Map<String, Any?>>(),
            ),
        )

        assertTrue(content.contains("@Column(name = \"id\", insertable = true, updatable = false)"))
    }

    @Test
    fun `aggregate entity template renders strong id override length only for string backing`() {
        fun renderStrongIdEntity(
            typeName: String,
            columnName: String,
            attributeOverrideLength: Int?,
            embeddedId: Boolean,
        ): String = renderTemplate(
            templateId = "aggregate/entity.kt.peb",
            outputPath = "demo-domain/build/generated/cap4k/main/kotlin/com/acme/demo/$typeName.kt",
            context = mapOf(
                "packageName" to "com.acme.demo",
                "typeName" to "${typeName}Entity",
                "entityJpa" to mapOf(
                    "entityEnabled" to true,
                    "tableName" to columnName,
                ),
                "hasConverterFields" to false,
                "hasGeneratedValueFields" to false,
                "hasEmbeddedIdFields" to embeddedId,
                "hasStrongIdFields" to true,
                "hasEmbeddedStrongIdFields" to !embeddedId,
                "hasVersionFields" to false,
                "dynamicInsert" to false,
                "dynamicUpdate" to false,
                "softDeleteSql" to null,
                "softDeleteWhereClause" to null,
                "jpaImports" to emptyList<String>(),
                "imports" to emptyList<String>(),
                "scalarFields" to entityScalarFields(
                    mapOf(
                        "name" to "id",
                        "type" to typeName,
                        "nullable" to false,
                        "propertyNullable" to false,
                        "defaultValue" to null,
                        "columnName" to columnName,
                        "isId" to embeddedId,
                        "strongId" to true,
                        "embeddedId" to embeddedId,
                        "writePolicy" to if (embeddedId) "CREATE_ONLY" else "READ_WRITE",
                        "isVersion" to false,
                        "insertable" to null,
                        "updatable" to null,
                        "attributeOverrideNullable" to false,
                        "attributeOverrideInsertable" to null,
                        "attributeOverrideUpdatable" to !embeddedId,
                        "attributeOverrideLength" to attributeOverrideLength,
                        "converterClassRef" to null,
                    )
                ),
                "relationFields" to emptyList<Map<String, Any?>>(),
            ),
        )

        val uuidTextEntity = renderStrongIdEntity("UuidTextId", "uuid_text", 40, embeddedId = true)
        val uuidNativeEntity = renderStrongIdEntity("UuidNativeId", "uuid_native", null, embeddedId = false)
        val snowflakeTextEntity = renderStrongIdEntity("SnowflakeTextId", "snowflake_text", 24, embeddedId = false)
        val snowflakeLongEntity = renderStrongIdEntity("SnowflakeLongId", "snowflake_long", null, embeddedId = false)

        assertTrue(uuidTextEntity.contains("updatable = false, length = 40"))
        assertFalse(uuidNativeEntity.contains("length ="))
        assertFalse(snowflakeLongEntity.contains("length ="))
        assertTrue(snowflakeTextEntity.contains("length = 24"))
    }

    @Test
    fun `aggregate entity template renders aggregate root strong id as embedded id`() {
        val content = renderTemplate(
            templateId = "aggregate/entity.kt.peb",
            outputPath = "demo-domain/build/generated/cap4k/main/kotlin/com/acme/demo/domain/aggregates/content/Content.kt",
            context = mapOf(
                "packageName" to "com.acme.demo.domain.aggregates.content",
                "typeName" to "Content",
                "entityJpa" to mapOf(
                    "entityEnabled" to true,
                    "tableName" to "content",
                ),
                "hasConverterFields" to false,
                "hasGeneratedValueFields" to false,
                "hasEmbeddedIdFields" to true,
                "hasStrongIdFields" to true,
                "hasEmbeddedStrongIdFields" to true,
                "hasVersionFields" to false,
                "dynamicInsert" to false,
                "dynamicUpdate" to false,
                "softDeleteSql" to null,
                "softDeleteWhereClause" to null,
                "jpaImports" to emptyList<String>(),
                "imports" to listOf(
                    "com.acme.demo.domain.aggregates.content.ContentId",
                    "com.acme.demo.domain.shared.ids.AuthorId",
                    "com.acme.demo.domain.aggregates.media_processing_task.MediaProcessingTaskId",
                ),
                "scalarFields" to entityScalarFields(
                    mapOf(
                        "name" to "id",
                        "type" to "ContentId",
                        "propertyInitializer" to "id",
                        "nullable" to false,
                        "propertyNullable" to false,
                        "defaultValue" to null,
                        "columnName" to "id",
                        "isId" to true,
                        "strongId" to true,
                        "embeddedId" to true,
                        "writePolicy" to "CREATE_ONLY",
                        "isVersion" to false,
                        "insertable" to null,
                        "updatable" to null,
                        "attributeOverrideNullable" to false,
                        "attributeOverrideInsertable" to null,
                        "attributeOverrideUpdatable" to false,
                        "attributeOverrideLength" to 36,
                        "converterClassRef" to null,
                    ),
                    mapOf(
                        "name" to "title",
                        "type" to "String",
                        "propertyInitializer" to "title",
                        "nullable" to false,
                        "propertyNullable" to false,
                        "defaultValue" to null,
                        "columnName" to "title",
                        "isId" to false,
                        "strongId" to false,
                        "embeddedId" to false,
                        "writePolicy" to "READ_WRITE",
                        "isVersion" to false,
                        "insertable" to null,
                        "updatable" to null,
                        "converterClassRef" to null,
                    ),
                    mapOf(
                        "name" to "authorId",
                        "type" to "AuthorId",
                        "propertyInitializer" to "authorId",
                        "nullable" to false,
                        "propertyNullable" to false,
                        "defaultValue" to null,
                        "columnName" to "author_id",
                        "isId" to false,
                        "strongId" to true,
                        "embeddedId" to false,
                        "writePolicy" to "READ_WRITE",
                        "isVersion" to false,
                        "insertable" to null,
                        "updatable" to null,
                        "attributeOverrideNullable" to false,
                        "attributeOverrideInsertable" to null,
                        "attributeOverrideUpdatable" to true,
                        "attributeOverrideLength" to 36,
                        "converterClassRef" to null,
                    ),
                    mapOf(
                        "name" to "mediaProcessingTaskId",
                        "type" to "MediaProcessingTaskId",
                        "propertyInitializer" to "mediaProcessingTaskId",
                        "nullable" to true,
                        "propertyNullable" to true,
                        "defaultValue" to null,
                        "columnName" to "media_processing_task_id",
                        "isId" to false,
                        "strongId" to true,
                        "embeddedId" to false,
                        "writePolicy" to "READ_WRITE",
                        "isVersion" to false,
                        "insertable" to null,
                        "updatable" to null,
                        "attributeOverrideNullable" to true,
                        "attributeOverrideInsertable" to null,
                        "attributeOverrideUpdatable" to true,
                        "attributeOverrideLength" to 36,
                        "converterClassRef" to null,
                    ),
                ),
                "relationFields" to emptyList<Map<String, Any?>>(),
            ),
        )

        assertReadableKotlin(content)
        assertTrue(content.contains("import jakarta.persistence.AttributeOverride"))
        assertTrue(content.contains("import jakarta.persistence.Embedded"))
        assertTrue(content.contains("import jakarta.persistence.EmbeddedId"))
        assertTrue(content.contains("import com.acme.demo.domain.aggregates.content.ContentId"))
        assertTrue(content.contains("import com.acme.demo.domain.shared.ids.AuthorId"))
        assertTrue(content.contains("import com.acme.demo.domain.aggregates.media_processing_task.MediaProcessingTaskId"))
        assertTrue(content.contains("id: ContentId"))
        assertTrue(
            content.contains(
                """@EmbeddedId
    @AttributeOverride(name = "value", column = Column(name = "id", nullable = false, updatable = false, length = 36))
    var id: ContentId = id"""
            )
        )
        assertTrue(
            content.contains(
                """@Embedded
    @AttributeOverride(name = "value", column = Column(name = "author_id", nullable = false, updatable = true, length = 36))
    var authorId: AuthorId = authorId"""
            )
        )
        assertTrue(
            content.contains(
                """@Embedded
    @AttributeOverride(name = "value", column = Column(name = "media_processing_task_id", nullable = true, updatable = true, length = 36))
    var mediaProcessingTaskId: MediaProcessingTaskId? = mediaProcessingTaskId"""
            )
        )
        assertFalse(content.contains("@Id"))
        assertFalse(content.contains("UUID(" + "0L, 0L)"))
        assertFalse(content.contains("@Column(name = \"id\")"))
        assertFalse(content.contains("@Column(name = \"author_id\")"))
        assertFalse(content.contains("@Column(name = \"media_processing_task_id\")"))
    }

    @Test
    fun `aggregate entity template renders owned child strong id as embedded id`() {
        val content = renderTemplate(
            templateId = "aggregate/entity.kt.peb",
            outputPath = "demo-domain/build/generated/cap4k/main/kotlin/com/demo/domain/order/OrderLine.kt",
            context = mapOf(
                "packageName" to "com.demo.domain.order",
                "typeName" to "OrderLine",
                "entityJpa" to mapOf(
                    "entityEnabled" to true,
                    "tableName" to "order_line",
                ),
                "hasConverterFields" to false,
                "hasGeneratedValueFields" to false,
                "hasEmbeddedIdFields" to true,
                "hasStrongIdFields" to true,
                "hasEmbeddedStrongIdFields" to false,
                "hasVersionFields" to false,
                "dynamicInsert" to false,
                "dynamicUpdate" to false,
                "softDeleteSql" to null,
                "softDeleteWhereClause" to null,
                "jpaImports" to emptyList<String>(),
                "imports" to listOf("com.demo.domain.order.OrderLineId"),
                "scalarFields" to entityScalarFields(
                    mapOf(
                        "name" to "id",
                        "type" to "OrderLineId",
                        "propertyInitializer" to "id",
                        "nullable" to false,
                        "propertyNullable" to false,
                        "defaultValue" to null,
                        "columnName" to "id",
                        "isId" to true,
                        "strongId" to true,
                        "embeddedId" to true,
                        "writePolicy" to "CREATE_ONLY",
                        "isVersion" to false,
                        "insertable" to null,
                        "updatable" to null,
                        "attributeOverrideNullable" to false,
                        "attributeOverrideInsertable" to null,
                        "attributeOverrideUpdatable" to false,
                        "converterClassRef" to null,
                    )
                ),
                "relationFields" to emptyList<Map<String, Any?>>(),
            ),
        )

        assertReadableKotlin(content)
        assertTrue(content.contains("import jakarta.persistence.EmbeddedId"))
        assertTrue(content.contains("@EmbeddedId"))
        assertTrue(content.contains("var id: OrderLineId = id"))
    }

    @Test
    fun `aggregate entity template renders imported scalar types with short names`() {
        val content = renderTemplate(
            templateId = "aggregate/entity.kt.peb",
            outputPath = "demo-domain/build/generated/cap4k/main/kotlin/com/acme/demo/domain/aggregates/content/Content.kt",
            context = mapOf(
                "packageName" to "com.acme.demo.domain.aggregates.content",
                "typeName" to "Content",
                "entityJpa" to mapOf(
                    "entityEnabled" to true,
                    "tableName" to "content",
                ),
                "hasConverterFields" to true,
                "hasGeneratedValueFields" to false,
                "hasEmbeddedIdFields" to false,
                "hasStrongIdFields" to false,
                "hasEmbeddedStrongIdFields" to false,
                "hasVersionFields" to false,
                "dynamicInsert" to false,
                "dynamicUpdate" to false,
                "softDeleteSql" to null,
                "softDeleteWhereClause" to null,
                "jpaImports" to emptyList<String>(),
                "imports" to listOf("com.acme.demo.domain.aggregates.content.enums.ReviewStatus"),
                "scalarFields" to entityScalarFields(
                    mapOf(
                        "name" to "id",
                        "type" to "Long",
                        "renderedType" to "Long",
                        "propertyInitializer" to "id",
                        "nullable" to false,
                        "propertyNullable" to false,
                        "defaultValue" to null,
                        "columnName" to "id",
                        "isId" to true,
                        "strongId" to false,
                        "embeddedId" to false,
                        "writePolicy" to "CREATE_ONLY",
                        "isVersion" to false,
                        "insertable" to null,
                        "updatable" to null,
                        "converterTypeRef" to null,
                        "converterClassRef" to null,
                    ),
                    mapOf(
                        "name" to "reviewStatus",
                        "type" to "com.acme.demo.domain.aggregates.content.enums.ReviewStatus",
                        "renderedType" to "ReviewStatus",
                        "propertyInitializer" to "reviewStatus",
                        "nullable" to false,
                        "propertyNullable" to false,
                        "defaultValue" to null,
                        "columnName" to "review_status",
                        "isId" to false,
                        "strongId" to false,
                        "embeddedId" to false,
                        "writePolicy" to "READ_WRITE",
                        "isVersion" to false,
                        "insertable" to null,
                        "updatable" to null,
                        "converterTypeRef" to "com.acme.demo.domain.aggregates.content.enums.ReviewStatus",
                        "converterClassRef" to "com.acme.demo.domain.aggregates.content.enums.ReviewStatus.Converter",
                    ),
                ),
                "relationFields" to emptyList<Map<String, Any?>>(),
            ),
        )

        assertReadableKotlin(content)
        assertTrue(content.contains("import com.acme.demo.domain.aggregates.content.enums.ReviewStatus"))
        assertTrue(content.contains("reviewStatus: ReviewStatus"))
        assertTrue(content.contains("var reviewStatus: ReviewStatus = reviewStatus"))
        assertTrue(content.contains("@Convert(converter = ReviewStatus.Converter::class)"))
        assertTrue(
            content.contains(
                """@Column(name = "review_status")
    @Convert(converter = ReviewStatus.Converter::class)
    var reviewStatus: ReviewStatus = reviewStatus"""
            )
        )
        assertFalse(content.contains("@Convert(converter = com.acme.demo.domain.aggregates.content.enums.ReviewStatus.Converter::class)"))
        assertFalse(content.contains("reviewStatus: com.acme.demo.domain.aggregates.content.enums.ReviewStatus"))
        assertFalse(content.contains("var reviewStatus: com.acme.demo.domain.aggregates.content.enums.ReviewStatus"))
    }

    @Test
    fun `aggregate behavior template renders checked in scaffold without generated business body`() {
        val content = renderTemplate(
            templateId = "aggregate/behavior.kt.peb",
            outputPath = "demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/category/CategoryBehavior.kt",
            context = mapOf(
                "packageName" to "com.acme.demo.domain.aggregates.category",
                "rootName" to "Category",
            ),
        )

        assertTrue(content.startsWith("package com.acme.demo.domain.aggregates.category"))
        assertTrue(content.contains("Place behavior for Category"))
        assertTrue(content.contains("fun Category.onCreate()"))
        assertTrue(content.contains("fun Category.onDeleted()"))
        assertFalse(content.contains("fun Category.onUpdate()"))
        assertFalse(content.contains("fun Category.onDelete()"))
        assertFalse(content.contains("fun Category.onRemove()"))
        assertFalse(content.contains("managed-begin"))
    }

    @Test
    fun `renderer normalizes kotlin artifact whitespace without mutating template semantics`() {
        val overrideDir = Files.createTempDirectory("cap4k-renderer-kotlin-hygiene")
        val overrideDesignDir = Files.createDirectories(overrideDir.resolve("design"))
        overrideDesignDir.resolve("query.kt.peb").writeText(
            "package demo\r\n\r\n\r\nimport java.util.UUID   \r\n\r\nclass Demo(   \r\n    val id: UUID   \r\n)\r\n\r\n\r\n"
        )

        val renderer = PebbleArtifactRenderer(
            templateResolver = PresetTemplateResolver(
                preset = "ddd-default",
                overrideDirs = listOf(overrideDir.toString())
            )
        )

        val rendered = renderer.render(
            planItems = listOf(
                ArtifactPlanItem(
                    generatorId = "query",
                    moduleRole = "application",
                    templateId = "design/query.kt.peb",
                    outputPath = "demo-application/src/main/kotlin/com/acme/demo/application/queries/Demo.kt",
                    context = emptyMap(),
                    conflictPolicy = ConflictPolicy.SKIP
                )
            ),
            config = ProjectConfig(
                basePackage = "com.acme.demo",
                layout = ProjectLayout.MULTI_MODULE,
                modules = emptyMap(),
                sources = emptyMap(),
                generators = emptyMap(),
                templates = TemplateConfig(
                    preset = "ddd-default",
                    overrideDirs = listOf(overrideDir.toString()),
                    conflictPolicy = ConflictPolicy.SKIP
                )
            )
        )

        val content = rendered.single().content
        assertReadableKotlin(content)
        assertEquals(
            "package demo\n\nimport java.util.UUID\n\nclass Demo(\n    val id: UUID\n)\n",
            content
        )
    }

    @Test
    fun `aggregate repository template imports and uses strong id type`() {
        val content = renderTemplate(
            templateId = "aggregate/repository.kt.peb",
            outputPath = "demo-adapter/src/main/kotlin/com/acme/demo/adapter/domain/repositories/ContentRepository.kt",
            context = mapOf(
                "packageName" to "com.acme.demo.adapter.domain.repositories",
                "typeName" to "ContentRepository",
                "entityName" to "Content",
                "entityTypeFqn" to "com.acme.demo.domain.aggregates.content.Content",
                "aggregateName" to "Content",
                "idType" to "ContentId",
                "idTypeFqn" to "com.acme.demo.domain.aggregates.content.ContentId",
                "imports" to emptyList<String>(),
            ),
        )

        assertReadableKotlin(content)
        assertTrue(content.contains("import com.acme.demo.domain.aggregates.content.Content"))
        assertTrue(content.contains("import com.acme.demo.domain.aggregates.content.ContentId"))
        assertTrue(
            content.contains(
                "interface ContentRepository : JpaRepository<Content, ContentId>, JpaSpecificationExecutor<Content>"
            )
        )
        assertTrue(content.contains("jpaRepository: JpaRepository<Content, ContentId>"))
        assertTrue(content.contains("AbstractJpaRepository<Content, ContentId>"))
        assertFalse(content.contains("QuerydslPredicateExecutor"))
        assertFalse(content.contains("AbstractQuerydslRepository"))
        assertFalse(content.contains("QuerydslRepositoryAdapter"))
    }

    @Test
    fun `renderer keeps non kotlin artifacts free from kotlin specific whitespace cleanup`() {
        val overrideDir = Files.createTempDirectory("cap4k-renderer-non-kotlin-hygiene")
        val overrideDesignDir = Files.createDirectories(overrideDir.resolve("design"))
        overrideDesignDir.resolve("query.kt.peb").writeText("{\r\n  \"value\": 1  \r\n\r\n\r\n}\r\n")

        val renderer = PebbleArtifactRenderer(
            templateResolver = PresetTemplateResolver(
                preset = "ddd-default",
                overrideDirs = listOf(overrideDir.toString())
            )
        )

        val rendered = renderer.render(
            planItems = listOf(
                ArtifactPlanItem(
                    generatorId = "query",
                    moduleRole = "application",
                    templateId = "design/query.kt.peb",
                    outputPath = "demo-application/src/main/resources/demo.json",
                    context = emptyMap(),
                    conflictPolicy = ConflictPolicy.SKIP
                )
            ),
            config = ProjectConfig(
                basePackage = "com.acme.demo",
                layout = ProjectLayout.MULTI_MODULE,
                modules = emptyMap(),
                sources = emptyMap(),
                generators = emptyMap(),
                templates = TemplateConfig(
                    preset = "ddd-default",
                    overrideDirs = listOf(overrideDir.toString()),
                    conflictPolicy = ConflictPolicy.SKIP
                )
            )
        )

        assertEquals(
            "{\n  \"value\": 1  \n\n\n}\n",
            rendered.single().content
        )
    }

    @Test
    fun `aggregate repository and schema templates restore default contracts`() {
        val renderer = PebbleArtifactRenderer(
            templateResolver = PresetTemplateResolver("ddd-default", emptyList())
        )

        val rendered = renderer.render(
            planItems = listOf(
                ArtifactPlanItem(
                    generatorId = "aggregate",
                    moduleRole = "adapter",
                    templateId = "aggregate/repository.kt.peb",
                    outputPath = "demo-adapter/src/main/kotlin/com/acme/demo/adapter/domain/repositories/UserMessageRepository.kt",
                    context = mapOf(
                        "packageName" to "com.acme.demo.adapter.domain.repositories",
                        "typeName" to "UserMessageRepository",
                        "entityName" to "UserMessage",
                        "entityTypeFqn" to "com.acme.demo.domain.aggregates.user_message.UserMessage",
                        "aggregateName" to "UserMessage",
                        "idType" to "Long",
                    ),
                    conflictPolicy = ConflictPolicy.SKIP
                ),
                ArtifactPlanItem(
                    generatorId = "aggregate",
                    moduleRole = "domain",
                    templateId = "aggregate/schema.kt.peb",
                    outputPath = "demo-domain/src/main/kotlin/com/acme/demo/domain/_share/meta/user_message/SUserMessage.kt",
                    context = mapOf(
                        "packageName" to "com.acme.demo.domain._share.meta.user_message",
                        "typeName" to "SUserMessage",
                        "entityName" to "UserMessage",
                        "schemaRuntimePackage" to "com.only4.cap4k.ddd.domain.repo.schema",
                        "entityTypeFqn" to "com.acme.demo.domain.aggregates.user_message.UserMessage",
                        "isAggregateRoot" to true,
                        "fields" to listOf(
                            mapOf(
                                "name" to "messageKey",
                                "fieldName" to "messageKey",
                                "columnName" to "message_key",
                                "fieldType" to "String",
                                "type" to "String",
                                "comment" to "message key",
                            )
                        ),
                        "relationFields" to listOf(
                            mapOf(
                                "name" to "sender",
                                "targetTypeRef" to "UserProfile",
                            )
                        ),
                    ),
                    conflictPolicy = ConflictPolicy.SKIP
                ),
            ),
            config = ProjectConfig(
                basePackage = "com.acme.demo",
                layout = ProjectLayout.MULTI_MODULE,
                modules = emptyMap(),
                sources = emptyMap(),
                generators = emptyMap(),
                templates = TemplateConfig("ddd-default", emptyList(), ConflictPolicy.SKIP),
            )
        )

        val repositoryContent = rendered.single { it.outputPath.endsWith("UserMessageRepository.kt") }.content
        val schemaContent = rendered.single { it.outputPath.endsWith("SUserMessage.kt") }.content

        assertTrue(repositoryContent.contains("@Repository"))
        assertTrue(
            repositoryContent.contains(
                "interface UserMessageRepository : JpaRepository<UserMessage, Long>, JpaSpecificationExecutor<UserMessage>"
            )
        )
        assertTrue(repositoryContent.contains("class UserMessageJpaRepositoryAdapter("))
        assertTrue(repositoryContent.contains("AbstractJpaRepository<UserMessage, Long>"))
        assertFalse(repositoryContent.contains("QuerydslPredicateExecutor"))
        assertFalse(repositoryContent.contains("AbstractQuerydslRepository"))
        assertFalse(repositoryContent.contains("QuerydslRepositoryAdapter"))
        assertTrue(schemaContent.contains("import com.only4.cap4k.ddd.domain.repo.schema.SchemaSpecification"))
        assertTrue(schemaContent.contains("import com.only4.cap4k.ddd.domain.repo.schema.Field"))
        assertTrue(schemaContent.contains("import com.acme.demo.domain.aggregates.user_message.UserMessage"))
        assertFalse(schemaContent.contains("import {{"))
        assertTrue(schemaContent.contains("class SUserMessage("))
        assertTrue(schemaContent.contains("fun specify(builder: PredicateBuilder<SUserMessage>): Specification<UserMessage>"))
        assertTrue(schemaContent.contains("fun predicateById(id: Any): JpaPredicate<UserMessage>"))
        assertTrue(schemaContent.contains("fun predicate(builder: PredicateBuilder<SUserMessage>): JpaPredicate<UserMessage>"))
        assertFalse(schemaContent.contains("AggUserMessage"))
        assertFalse(schemaContent.contains("AggregatePredicate"))
        assertTrue(schemaContent.contains("val messageKey: Field<String>"))
        assertFalse(schemaContent.contains("val message_key"))
        assertFalse(schemaContent.contains("Field<Any>"))
    }

    @Test
    fun `aggregate schema template always renders root predicates against JpaPredicate`() {
        val renderer = PebbleArtifactRenderer(
            templateResolver = PresetTemplateResolver("ddd-default", emptyList())
        )

        val rendered = renderer.render(
            planItems = listOf(
                ArtifactPlanItem(
                    generatorId = "aggregate",
                    moduleRole = "domain",
                    templateId = "aggregate/schema.kt.peb",
                    outputPath = "demo-domain/src/main/kotlin/com/acme/demo/domain/_share/meta/user_message/SUserMessage.kt",
                    context = mapOf(
                        "packageName" to "com.acme.demo.domain._share.meta.user_message",
                        "typeName" to "SUserMessage",
                        "entityName" to "UserMessage",
                        "schemaRuntimePackage" to "com.only4.cap4k.ddd.domain.repo.schema",
                        "entityTypeFqn" to "com.acme.demo.domain.aggregates.user_message.UserMessage",
                        "isAggregateRoot" to true,
                        "fields" to listOf(
                            mapOf(
                                "name" to "messageKey",
                                "fieldName" to "messageKey",
                                "columnName" to "message_key",
                                "fieldType" to "String",
                                "type" to "String",
                                "comment" to "message key",
                            )
                        ),
                        "relationFields" to emptyList<Map<String, Any?>>(),
                    ),
                    conflictPolicy = ConflictPolicy.SKIP
                )
            ),
            config = ProjectConfig(
                basePackage = "com.acme.demo",
                layout = ProjectLayout.MULTI_MODULE,
                modules = emptyMap(),
                sources = emptyMap(),
                generators = emptyMap(),
                templates = TemplateConfig("ddd-default", emptyList(), ConflictPolicy.SKIP),
            )
        )

        val schemaContent = rendered.single().content

        assertFalse(schemaContent.contains("AggUserMessage"))
        assertFalse(schemaContent.contains("AggregatePredicate"))
        assertTrue(schemaContent.contains("fun predicateById(id: Any): JpaPredicate<UserMessage>"))
        assertTrue(schemaContent.contains("fun predicate(builder: PredicateBuilder<SUserMessage>): JpaPredicate<UserMessage>"))
    }

    @Test
    fun `aggregate child schema keeps criteria helpers without root predicate helpers`() {
        val renderer = PebbleArtifactRenderer(
            templateResolver = PresetTemplateResolver("ddd-default", emptyList())
        )

        val rendered = renderer.render(
            planItems = listOf(
                ArtifactPlanItem(
                    generatorId = "aggregate",
                    moduleRole = "domain",
                    templateId = "aggregate/schema.kt.peb",
                    outputPath = "demo-domain/src/main/kotlin/com/acme/demo/domain/_share/meta/video/SVideoFile.kt",
                    context = mapOf(
                        "packageName" to "com.acme.demo.domain._share.meta.video",
                        "typeName" to "SVideoFile",
                        "entityName" to "VideoFile",
                        "schemaRuntimePackage" to "com.only4.cap4k.ddd.domain.repo.schema",
                        "entityTypeFqn" to "com.acme.demo.domain.aggregates.video.VideoFile",
                        "isAggregateRoot" to false,
                        "fields" to listOf(
                            mapOf(
                                "name" to "videoId",
                                "fieldName" to "videoId",
                                "columnName" to "video_id",
                                "fieldType" to "Long",
                                "type" to "Long",
                                "comment" to "video id",
                            )
                        ),
                    ),
                    conflictPolicy = ConflictPolicy.SKIP
                )
            ),
            config = ProjectConfig(
                basePackage = "com.acme.demo",
                layout = ProjectLayout.MULTI_MODULE,
                modules = emptyMap(),
                sources = emptyMap(),
                generators = emptyMap(),
                templates = TemplateConfig("ddd-default", emptyList(), ConflictPolicy.SKIP),
            )
        )

        val schemaContent = rendered.single().content

        assertTrue(schemaContent.contains("class SVideoFile("))
        assertTrue(schemaContent.contains("fun specify(builder: PredicateBuilder<SVideoFile>): Specification<VideoFile>"))
        assertTrue(schemaContent.contains("val videoId: Field<Long>"))
        assertFalse(schemaContent.contains("JpaPredicate"))
        assertFalse(schemaContent.contains("AggregatePredicate"))
        assertFalse(schemaContent.contains("AggVideoFile"))
        assertFalse(schemaContent.contains("fun predicateById("))
        assertFalse(schemaContent.contains("fun predicate(builder: PredicateBuilder<SVideoFile>)"))
    }

    @Test
    fun `aggregate schema template renders owned relation fields joins constants and distinct predicate`() {
        val content = renderTemplate(
            templateId = "aggregate/schema.kt.peb",
            outputPath = "demo-domain/src/main/kotlin/com/acme/demo/domain/_share/meta/video_post/SVideoPost.kt",
            context = mapOf(
                "packageName" to "com.acme.demo.domain._share.meta.video_post",
                "typeName" to "SVideoPost",
                "entityName" to "VideoPost",
                "schemaRuntimePackage" to "com.only4.cap4k.ddd.domain.repo.schema",
                "entityTypeFqn" to "com.acme.demo.domain.aggregates.video_post.VideoPost",
                "isAggregateRoot" to true,
                "imports" to emptyList<String>(),
                "fields" to listOf(
                    mapOf(
                        "name" to "title",
                        "fieldName" to "title",
                        "columnName" to "title",
                        "fieldType" to "String",
                        "type" to "String",
                        "renderedType" to "String",
                        "comment" to "title",
                    )
                ),
                "relationJoins" to listOf(
                    mapOf(
                        "domainName" to "items",
                        "persistencePathName" to "_items",
                        "methodName" to "joinItems",
                        "relationKind" to "OWNED_MANY",
                        "targetEntityName" to "VideoPostItem",
                        "targetEntityTypeFqn" to "com.acme.demo.domain.aggregates.video_post.VideoPostItem",
                        "targetSchemaName" to "SVideoPostItem",
                        "targetSchemaFqn" to "com.acme.demo.domain._share.meta.video_post.SVideoPostItem",
                        "relationFieldType" to "RelationCollectionField",
                        "nullable" to false,
                        "ownedCardinality" to "MANY",
                        "persistenceShape" to "ONE_TO_MANY_JOIN_COLUMN",
                    ),
                    mapOf(
                        "domainName" to "file",
                        "persistencePathName" to "_files",
                        "methodName" to "joinFile",
                        "relationKind" to "OWNED_ONE",
                        "targetEntityName" to "VideoPostFile",
                        "targetEntityTypeFqn" to "com.acme.demo.domain.aggregates.video_post.VideoPostFile",
                        "targetSchemaName" to "SVideoPostFile",
                        "targetSchemaFqn" to "com.acme.demo.domain._share.meta.video_post.SVideoPostFile",
                        "relationFieldType" to "RelationOptionalField",
                        "nullable" to false,
                        "ownedCardinality" to "ONE",
                        "persistenceShape" to "ONE_TO_MANY_JOIN_COLUMN",
                    ),
                ),
            ),
        )

        assertReadableKotlin(content)
        assertTrue(content.contains("import jakarta.persistence.criteria.From"))
        assertTrue(content.contains("import jakarta.persistence.criteria.Join"))
        assertTrue(content.contains("import com.only4.cap4k.ddd.domain.repo.schema.JoinType"))
        assertTrue(content.contains("import com.only4.cap4k.ddd.domain.repo.schema.RelationCollectionField"))
        assertTrue(content.contains("import com.only4.cap4k.ddd.domain.repo.schema.RelationOptionalField"))
        assertTrue(content.contains("import com.acme.demo.domain.aggregates.video_post.VideoPost"))
        assertTrue(content.contains("import com.acme.demo.domain.aggregates.video_post.VideoPostItem"))
        assertTrue(content.contains("import com.acme.demo.domain.aggregates.video_post.VideoPostFile"))
        assertTrue(content.contains("import com.acme.demo.domain._share.meta.video_post.SVideoPostItem"))
        assertTrue(content.contains("import com.acme.demo.domain._share.meta.video_post.SVideoPostFile"))
        assertTrue(content.contains("private val root: From<*, VideoPost>"))
        assertTrue(content.contains("val title: Field<String>"))
        assertTrue(content.contains("class PROPERTY_NAMES"))
        assertTrue(content.contains("val title = \"title\""))
        assertTrue(content.contains("class RELATION_NAMES"))
        assertTrue(content.contains("val items = \"items\""))
        assertTrue(content.contains("val file = \"file\""))
        assertTrue(content.contains("val props = PROPERTY_NAMES()"))
        assertTrue(content.contains("val relations = RELATION_NAMES()"))
        assertTrue(content.contains("fun predicate(builder: PredicateBuilder<SVideoPost>): JpaPredicate<VideoPost>"))
        assertTrue(content.contains("return predicate(false, builder)"))
        assertTrue(content.contains("fun predicate(distinct: Boolean, builder: PredicateBuilder<SVideoPost>): JpaPredicate<VideoPost>"))
        assertTrue(content.contains("return JpaPredicate.bySpecification(VideoPost::class.java, specify(builder, distinct))"))
        assertTrue(content.contains("val items: RelationCollectionField<VideoPostItem>"))
        assertTrue(content.contains("RelationCollectionField(root.get<Collection<VideoPostItem>>(\"_items\"), criteriaBuilder)"))
        assertTrue(content.contains("val file: RelationOptionalField<VideoPostFile>"))
        assertTrue(content.contains("RelationOptionalField(root.get<Collection<VideoPostFile>>(\"_files\"), criteriaBuilder)"))
        assertTrue(content.contains("fun joinItems(): SVideoPostItem = joinItems(JoinType.INNER)"))
        assertTrue(content.contains("fun joinItems(joinType: JoinType): SVideoPostItem"))
        assertTrue(content.contains("val join = _join<VideoPostItem>(\"items\", \"_items\", joinType)"))
        assertTrue(content.contains("SVideoPostItem(join, criteriaBuilder)"))
        assertTrue(content.contains("fun joinFile(): SVideoPostFile = joinFile(JoinType.INNER)"))
        assertTrue(content.contains("fun joinFile(joinType: JoinType): SVideoPostFile"))
        assertTrue(content.contains("val join = _join<VideoPostFile>(\"file\", \"_files\", joinType)"))
        assertTrue(content.contains("SVideoPostFile(join, criteriaBuilder)"))
        assertTrue(content.contains("private data class JoinCacheKey"))
        assertTrue(content.contains("private val joinTypesByPath = mutableMapOf<JoinCacheKey, JoinType>()"))
        assertTrue(content.contains("root.join<VideoPost, T>(persistencePathName, joinType.toJpaJoinType())"))
        assertTrue(content.contains("schema relation $" + "domainName is already joined as $" + "existingType"))
        assertFalse(content.contains("val _items: RelationCollectionField"))
        assertFalse(content.contains("val _files: RelationOptionalField"))
        assertFalse(content.contains("fun join_items"))
        assertFalse(content.contains("fun join_files"))
        assertFalse(content.contains("val _items = \"_items\""))
        assertFalse(content.contains("val _files = \"_files\""))
    }

    @Test
    fun `aggregate child schema template renders chained owned joins without aggregate root predicates`() {
        val content = renderTemplate(
            templateId = "aggregate/schema.kt.peb",
            outputPath = "demo-domain/src/main/kotlin/com/acme/demo/domain/_share/meta/video_post/SVideoPostItem.kt",
            context = mapOf(
                "packageName" to "com.acme.demo.domain._share.meta.video_post",
                "typeName" to "SVideoPostItem",
                "entityName" to "VideoPostItem",
                "schemaRuntimePackage" to "com.only4.cap4k.ddd.domain.repo.schema",
                "entityTypeFqn" to "com.acme.demo.domain.aggregates.video_post.VideoPostItem",
                "isAggregateRoot" to false,
                "imports" to emptyList<String>(),
                "fields" to listOf(
                    mapOf(
                        "name" to "label",
                        "fieldName" to "label",
                        "columnName" to "label",
                        "fieldType" to "String",
                        "type" to "String",
                        "renderedType" to "String",
                        "comment" to "label",
                    )
                ),
                "relationJoins" to listOf(
                    mapOf(
                        "domainName" to "adjustments",
                        "persistencePathName" to "_adjustments",
                        "methodName" to "joinAdjustments",
                        "relationKind" to "OWNED_MANY",
                        "targetEntityName" to "VideoPostItemAdjustment",
                        "targetEntityTypeFqn" to "com.acme.demo.domain.aggregates.video_post.VideoPostItemAdjustment",
                        "targetSchemaName" to "SVideoPostItemAdjustment",
                        "targetSchemaFqn" to "com.acme.demo.domain._share.meta.video_post.SVideoPostItemAdjustment",
                        "relationFieldType" to "RelationCollectionField",
                        "nullable" to false,
                        "ownedCardinality" to "MANY",
                        "persistenceShape" to "ONE_TO_MANY_JOIN_COLUMN",
                    ),
                ),
            ),
        )

        assertReadableKotlin(content)
        assertTrue(content.contains("private val root: From<*, VideoPostItem>"))
        assertTrue(content.contains("val label: Field<String>"))
        assertTrue(content.contains("val adjustments: RelationCollectionField<VideoPostItemAdjustment>"))
        assertTrue(content.contains("fun joinAdjustments(): SVideoPostItemAdjustment = joinAdjustments(JoinType.INNER)"))
        assertTrue(content.contains("fun joinAdjustments(joinType: JoinType): SVideoPostItemAdjustment"))
        assertTrue(content.contains("SVideoPostItemAdjustment(join, criteriaBuilder)"))
        assertFalse(content.contains("fun predicateById("))
        assertFalse(content.contains("fun predicate(builder: PredicateBuilder<SVideoPostItem>): JpaPredicate<VideoPostItem>"))
    }

    @Test
    @OptIn(org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi::class)
    fun `aggregate schema join methods reuse schema wrapper and reject conflicting join type`() {
        val content = renderTemplate(
            templateId = "aggregate/schema.kt.peb",
            outputPath = "demo-domain/src/main/kotlin/com/acme/demo/domain/_share/meta/video_post/SVideoPost.kt",
            context = schemaRelationJoinTestContext(),
        )

        val result = KotlinCompilation().apply {
            sources = listOf(SourceFile.kotlin("SVideoPost.kt", content)) + schemaRelationJoinStubSources
            inheritClassPath = true
            supportsK2 = true
        }.compile()

        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
        val behavior = result.classLoader.loadClass(
            "com.acme.demo.domain._share.meta.video_post.SchemaRelationJoinBehavior"
        )
        behavior.getMethod("verify").invoke(null)
    }

    @Test
    fun `type helper reads renderedType from object and passes through string input`() {
        val overrideDir = Files.createTempDirectory("cap4k-override-helper-type")
        val overrideDesignDir = Files.createDirectories(overrideDir.resolve("design"))
        overrideDesignDir.resolve("query.kt.peb")
            .writeText(
                """
                {{ type(field) | raw }}
                {{ type("String") | raw }}
                """.trimIndent()
            )

        val renderer = PebbleArtifactRenderer(
            templateResolver = PresetTemplateResolver(
                preset = "ddd-default",
                overrideDirs = listOf(overrideDir.toString())
            )
        )

        val rendered = renderer.render(
            planItems = listOf(
                ArtifactPlanItem(
                    generatorId = "command",
                    moduleRole = "application",
                    templateId = "design/query.kt.peb",
                    outputPath = "demo-application/src/main/kotlin/com/acme/demo/application/queries/FindOrderQry.kt",
                    context = mapOf(
                        "field" to RenderedTypeCarrier("List<com.foo.Status?>")
                    ),
                    conflictPolicy = ConflictPolicy.SKIP
                )
            ),
            config = ProjectConfig(
                basePackage = "com.acme.demo",
                layout = ProjectLayout.MULTI_MODULE,
                modules = emptyMap(),
                sources = emptyMap(),
                generators = emptyMap(),
                templates = TemplateConfig(
                    preset = "ddd-default",
                    overrideDirs = listOf(overrideDir.toString()),
                    conflictPolicy = ConflictPolicy.SKIP
                )
            )
        )

        assertEquals(
            "List<com.foo.Status?>",
            rendered.single().content.substringBefore("String").trim()
        )
        assertTrue(rendered.single().content.contains("String"))
    }

    @Test
    fun `imports helper accepts direct list input and normalizes whitespace variants`() {
        val overrideDir = Files.createTempDirectory("cap4k-override-helper-imports")
        val overrideDesignDir = Files.createDirectories(overrideDir.resolve("design"))
        overrideDesignDir.resolve("query.kt.peb")
            .writeText(
                """
                {{ imports(importValues) | json | raw }}
                """.trimIndent()
            )

        val renderer = PebbleArtifactRenderer(
            templateResolver = PresetTemplateResolver(
                preset = "ddd-default",
                overrideDirs = listOf(overrideDir.toString())
            )
        )

        val rendered = renderer.render(
            planItems = listOf(
                ArtifactPlanItem(
                    generatorId = "command",
                    moduleRole = "application",
                    templateId = "design/query.kt.peb",
                    outputPath = "demo-application/src/main/kotlin/com/acme/demo/application/queries/FindOrderQry.kt",
                    context = mapOf(
                        "importValues" to listOf(
                            "  java.time.LocalDateTime  ",
                            "\tjava.util.UUID",
                            "java.time.LocalDateTime",
                            "java.util.UUID  ",
                            "  ",
                        )
                    ),
                    conflictPolicy = ConflictPolicy.SKIP
                )
            ),
            config = ProjectConfig(
                basePackage = "com.acme.demo",
                layout = ProjectLayout.MULTI_MODULE,
                modules = emptyMap(),
                sources = emptyMap(),
                generators = emptyMap(),
                templates = TemplateConfig(
                    preset = "ddd-default",
                    overrideDirs = listOf(overrideDir.toString()),
                    conflictPolicy = ConflictPolicy.SKIP
                )
            )
        )

        assertEquals(
            """["java.time.LocalDateTime","java.util.UUID"]""",
            rendered.single().content.trim()
        )
    }

    @Test
    fun `imports helper preserves order and removes blank and duplicate values from carrier map`() {
        val overrideDir = Files.createTempDirectory("cap4k-override-helper-imports-map")
        val overrideDesignDir = Files.createDirectories(overrideDir.resolve("design"))
        overrideDesignDir.resolve("query.kt.peb")
            .writeText(
                """
                {{ imports(importCarrier) | json | raw }}
                """.trimIndent()
            )

        val renderer = PebbleArtifactRenderer(
            templateResolver = PresetTemplateResolver(
                preset = "ddd-default",
                overrideDirs = listOf(overrideDir.toString())
            )
        )

        val rendered = renderer.render(
            planItems = listOf(
                ArtifactPlanItem(
                    generatorId = "command",
                    moduleRole = "application",
                    templateId = "design/query.kt.peb",
                    outputPath = "demo-application/src/main/kotlin/com/acme/demo/application/queries/FindOrderQry.kt",
                    context = mapOf(
                        "importCarrier" to mapOf(
                            "imports" to listOf(
                                "java.time.LocalDateTime",
                                "",
                                "java.util.UUID",
                                "java.time.LocalDateTime",
                                "  ",
                                "java.util.UUID",
                            )
                        )
                    ),
                    conflictPolicy = ConflictPolicy.SKIP
                )
            ),
            config = ProjectConfig(
                basePackage = "com.acme.demo",
                layout = ProjectLayout.MULTI_MODULE,
                modules = emptyMap(),
                sources = emptyMap(),
                generators = emptyMap(),
                templates = TemplateConfig(
                    preset = "ddd-default",
                    overrideDirs = listOf(overrideDir.toString()),
                    conflictPolicy = ConflictPolicy.SKIP
                )
            )
        )

        assertEquals(
            """["java.time.LocalDateTime","java.util.UUID"]""",
            rendered.single().content.trim()
        )
    }

    @Test
    fun `imports helper returns empty list for null and empty carrier input`() {
        val overrideDir = Files.createTempDirectory("cap4k-override-helper-imports-empty")
        val overrideDesignDir = Files.createDirectories(overrideDir.resolve("design"))
        overrideDesignDir.resolve("query.kt.peb")
            .writeText(
                """
                {{ imports(emptyCarrier) | json | raw }}|{{ imports(maybeImports) | json | raw }}
                """.trimIndent()
            )

        val renderer = PebbleArtifactRenderer(
            templateResolver = PresetTemplateResolver(
                preset = "ddd-default",
                overrideDirs = listOf(overrideDir.toString())
            )
        )

        val rendered = renderer.render(
            planItems = listOf(
                ArtifactPlanItem(
                    generatorId = "command",
                    moduleRole = "application",
                    templateId = "design/query.kt.peb",
                    outputPath = "demo-application/src/main/kotlin/com/acme/demo/application/queries/FindOrderQry.kt",
                    context = mapOf(
                        "emptyCarrier" to emptyMap<String, Any?>(),
                        "maybeImports" to null,
                    ),
                    conflictPolicy = ConflictPolicy.SKIP
                )
            ),
            config = ProjectConfig(
                basePackage = "com.acme.demo",
                layout = ProjectLayout.MULTI_MODULE,
                modules = emptyMap(),
                sources = emptyMap(),
                generators = emptyMap(),
                templates = TemplateConfig(
                    preset = "ddd-default",
                    overrideDirs = listOf(overrideDir.toString()),
                    conflictPolicy = ConflictPolicy.SKIP
                )
            )
        )

        assertEquals("[]|[]", rendered.single().content.trim())
    }

    @Test
    fun `imports helper fails fast when argument is missing`() {
        val overrideDir = Files.createTempDirectory("cap4k-override-helper-imports-missing")
        val overrideDesignDir = Files.createDirectories(overrideDir.resolve("design"))
        overrideDesignDir.resolve("query.kt.peb")
            .writeText("""{{ imports() }}""")

        val exception = assertThrows<Exception> {
            PebbleArtifactRenderer(
                templateResolver = PresetTemplateResolver(
                    preset = "ddd-default",
                    overrideDirs = listOf(overrideDir.toString())
                )
            ).render(
                planItems = listOf(
                    ArtifactPlanItem(
                        generatorId = "command",
                        moduleRole = "application",
                        templateId = "design/query.kt.peb",
                        outputPath = "demo.kt",
                        context = emptyMap(),
                        conflictPolicy = ConflictPolicy.SKIP
                    )
                ),
                config = ProjectConfig(
                    basePackage = "com.acme.demo",
                    layout = ProjectLayout.MULTI_MODULE,
                    modules = emptyMap(),
                    sources = emptyMap(),
                    generators = emptyMap(),
                    templates = TemplateConfig(
                        preset = "ddd-default",
                        overrideDirs = listOf(overrideDir.toString()),
                        conflictPolicy = ConflictPolicy.SKIP
                    )
                )
            )
        }

        val illegalArgument = generateSequence<Throwable>(exception) { it.cause }
            .filterIsInstance<IllegalArgumentException>()
            .firstOrNull()

        assertTrue(illegalArgument != null)
        assertTrue(illegalArgument!!.message!!.contains("imports() requires an argument."))
    }

    @Test
    fun `type helper fails fast on unsupported input`() {
        val overrideDir = Files.createTempDirectory("cap4k-override-helper-type-invalid")
        val overrideDesignDir = Files.createDirectories(overrideDir.resolve("design"))
        overrideDesignDir.resolve("query.kt.peb")
            .writeText("""{{ type(badValue) }}""")

        val exception = assertThrows<Exception> {
            PebbleArtifactRenderer(
                templateResolver = PresetTemplateResolver(
                    preset = "ddd-default",
                    overrideDirs = listOf(overrideDir.toString())
                )
            ).render(
                planItems = listOf(
                    ArtifactPlanItem(
                        generatorId = "command",
                        moduleRole = "application",
                        templateId = "design/query.kt.peb",
                        outputPath = "demo.kt",
                        context = mapOf("badValue" to mapOf("name" to "missingRenderedType")),
                        conflictPolicy = ConflictPolicy.SKIP
                    )
                ),
                config = ProjectConfig(
                    basePackage = "com.acme.demo",
                    layout = ProjectLayout.MULTI_MODULE,
                    modules = emptyMap(),
                    sources = emptyMap(),
                    generators = emptyMap(),
                    templates = TemplateConfig(
                        preset = "ddd-default",
                        overrideDirs = listOf(overrideDir.toString()),
                        conflictPolicy = ConflictPolicy.SKIP
                    )
                )
            )
        }

        val illegalArgument = generateSequence<Throwable>(exception) { it.cause }
            .filterIsInstance<IllegalArgumentException>()
            .firstOrNull()

        assertTrue(illegalArgument != null)
        assertTrue(illegalArgument!!.message!!.contains("type()"))
    }

    @Test
    fun `imports helper fails fast on unsupported input`() {
        val overrideDir = Files.createTempDirectory("cap4k-override-helper-imports-invalid")
        val overrideDesignDir = Files.createDirectories(overrideDir.resolve("design"))
        overrideDesignDir.resolve("query.kt.peb")
            .writeText("""{{ imports(badValue) }}""")

        val exception = assertThrows<Exception> {
            PebbleArtifactRenderer(
                templateResolver = PresetTemplateResolver(
                    preset = "ddd-default",
                    overrideDirs = listOf(overrideDir.toString())
                )
            ).render(
                planItems = listOf(
                    ArtifactPlanItem(
                        generatorId = "command",
                        moduleRole = "application",
                        templateId = "design/query.kt.peb",
                        outputPath = "demo.kt",
                        context = mapOf("badValue" to 123),
                        conflictPolicy = ConflictPolicy.SKIP
                    )
                ),
                config = ProjectConfig(
                    basePackage = "com.acme.demo",
                    layout = ProjectLayout.MULTI_MODULE,
                    modules = emptyMap(),
                    sources = emptyMap(),
                    generators = emptyMap(),
                    templates = TemplateConfig(
                        preset = "ddd-default",
                        overrideDirs = listOf(overrideDir.toString()),
                        conflictPolicy = ConflictPolicy.SKIP
                    )
                )
            )
        }

        val illegalArgument = generateSequence<Throwable>(exception) { it.cause }
            .filterIsInstance<IllegalArgumentException>()
            .firstOrNull()

        assertTrue(illegalArgument != null)
        assertTrue(illegalArgument!!.message!!.contains("imports()"))
    }

    @Test
    fun `prefers override template over preset template`() {
        val overrideDir = Files.createTempDirectory("cap4k-override")
        val overrideDesignDir = Files.createDirectories(overrideDir.resolve("design"))
        overrideDesignDir.resolve("query.kt.peb")
            .writeText(
                """
                package {{ packageName }}
                class {{ typeName }}Override
                """.trimIndent()
            )

        val renderer = PebbleArtifactRenderer(
            templateResolver = PresetTemplateResolver(
                preset = "ddd-default",
                overrideDirs = listOf(overrideDir.toString())
            )
        )

        val rendered = renderer.render(
            planItems = listOf(
                ArtifactPlanItem(
                    generatorId = "command",
                    moduleRole = "application",
                    templateId = "design/query.kt.peb",
                    outputPath = "demo-application/src/main/kotlin/com/acme/demo/application/queries/FindOrderQry.kt",
                    context = mapOf(
                        "packageName" to "com.acme.demo.application.queries",
                        "typeName" to "FindOrderQry",
                        "imports" to emptyList<String>(),
                        "fields" to listOf(
                            mapOf("name" to "orderId", "type" to "Long", "nullable" to false),
                        ),
                        "nestedTypes" to emptyList<Map<String, Any?>>(),
                        "resultFields" to listOf(
                            mapOf("name" to "status", "type" to "String", "nullable" to false),
                        ),
                        "resultNestedTypes" to emptyList<Map<String, Any?>>(),
                    ),
                    conflictPolicy = ConflictPolicy.SKIP
                )
            ),
            config = ProjectConfig(
                basePackage = "com.acme.demo",
                layout = ProjectLayout.MULTI_MODULE,
                modules = emptyMap(),
                sources = emptyMap(),
                generators = emptyMap(),
                templates = TemplateConfig(
                    preset = "ddd-default",
                    overrideDirs = listOf(overrideDir.toString()),
                    conflictPolicy = ConflictPolicy.SKIP
                )
            )
        )

        assertTrue(rendered.single().content.contains("FindOrderQryOverride"))
    }

    @Test
    fun `falls back to preset template when override does not exist`() {
        val overrideDir = Files.createTempDirectory("cap4k-override-empty")
        val renderer = PebbleArtifactRenderer(
            templateResolver = PresetTemplateResolver(
                preset = "ddd-default",
                overrideDirs = listOf(overrideDir.toString())
            )
        )

        val rendered = renderer.render(
            planItems = listOf(
                ArtifactPlanItem(
                    generatorId = "command",
                    moduleRole = "application",
                    templateId = "design/query.kt.peb",
                    outputPath = "demo-application/src/main/kotlin/com/acme/demo/application/queries/FindOrderQry.kt",
                    context = mapOf(
                        "packageName" to "com.acme.demo.application.queries",
                        "typeName" to "FindOrderQry"
                    ),
                    conflictPolicy = ConflictPolicy.SKIP
                )
            ),
            config = ProjectConfig(
                basePackage = "com.acme.demo",
                layout = ProjectLayout.MULTI_MODULE,
                modules = emptyMap(),
                sources = emptyMap(),
                generators = emptyMap(),
                templates = TemplateConfig(
                    preset = "ddd-default",
                    overrideDirs = listOf(overrideDir.toString()),
                    conflictPolicy = ConflictPolicy.SKIP
                )
            )
        )

        val content = rendered.single().content
        assertTrue(content.contains("package com.acme.demo.application.queries"))
        assertTrue(content.contains("object FindOrderQry"))
    }

    @Test
    fun `falls back to preset design templates and renders imports rendered types and nested types`() {
        val overrideDir = Files.createTempDirectory("cap4k-override-empty-design-rich")
        val renderer = PebbleArtifactRenderer(
            templateResolver = PresetTemplateResolver(
                preset = "ddd-default",
                overrideDirs = listOf(overrideDir.toString())
            )
        )

        val rendered = renderer.render(
            planItems = listOf(
                ArtifactPlanItem(
                    generatorId = "command",
                    moduleRole = "application",
                    templateId = "design/command.kt.peb",
                    outputPath = "demo-application/src/main/kotlin/com/acme/demo/application/commands/order/submit/SubmitOrderCmd.kt",
                    context = mapOf(
                        "packageName" to "com.acme.demo.application.commands.order.submit",
                        "typeName" to "SubmitOrderCmd",
                        "imports" to listOf("java.time.LocalDateTime", "java.util.UUID"),
                        "fields" to listOf(
                            mapOf("name" to "orderId", "renderedType" to "Long", "nullable" to false),
                            mapOf("name" to "address", "renderedType" to "Address?", "nullable" to true),
                            mapOf("name" to "createdAt", "renderedType" to "LocalDateTime", "nullable" to false),
                            mapOf("name" to "requestStatus", "renderedType" to "com.foo.Status", "nullable" to false),
                        ),
                        "nestedTypes" to listOf(
                            mapOf(
                                "name" to "Address",
                                "fields" to listOf(
                                    mapOf("name" to "city", "renderedType" to "String", "nullable" to false),
                                    mapOf("name" to "trackingId", "renderedType" to "UUID", "nullable" to false),
                                ),
                            ),
                        ),
                        "resultFields" to listOf(
                            mapOf("name" to "item", "renderedType" to "Item?", "nullable" to true),
                            mapOf("name" to "responseStatus", "renderedType" to "com.bar.Status", "nullable" to false),
                        ),
                        "resultNestedTypes" to listOf(
                            mapOf(
                                "name" to "Item",
                                "fields" to listOf(
                                    mapOf("name" to "id", "renderedType" to "Long", "nullable" to false),
                                ),
                            ),
                        ),
                    ),
                    conflictPolicy = ConflictPolicy.SKIP
                )
            ),
            config = ProjectConfig(
                basePackage = "com.acme.demo",
                layout = ProjectLayout.MULTI_MODULE,
                modules = emptyMap(),
                sources = emptyMap(),
                generators = emptyMap(),
                templates = TemplateConfig(
                    preset = "ddd-default",
                    overrideDirs = listOf(overrideDir.toString()),
                    conflictPolicy = ConflictPolicy.SKIP
                )
            )
        )

        val content = rendered.single().content
        assertFalse(content.contains("package com.acme.demo.application.commands.order.submitimport "))
        assertTrue(content.contains("package com.acme.demo.application.commands.order.submit"))
        assertTrue(content.contains("import java.time.LocalDateTime"))
        assertTrue(content.contains("import java.util.UUID"))
        assertFalse(content.contains("import com.foo.Status"))
        assertFalse(content.contains("import com.bar.Status"))
        assertTrue(content.contains("object SubmitOrderCmd"))
        assertTrue(content.contains("data class Request("))
        assertFalse(content.contains("val orderId: Long,        val address: Address?"))
        assertFalse(content.contains("val address: Address?,        val createdAt: LocalDateTime"))
        assertTrue(content.contains("val address: Address?"))
        assertFalse(content.contains("val address: Address??"))
        assertTrue(content.contains("val createdAt: LocalDateTime"))
        assertTrue(content.contains("val requestStatus: com.foo.Status"))
        assertTrue(content.contains("data class Address("))
        assertTrue(content.contains("val trackingId: UUID"))
        assertTrue(content.contains("data class Response("))
        assertTrue(content.contains("val item: Item?"))
        assertFalse(content.contains("val item: Item??"))
        assertTrue(content.contains("val responseStatus: com.bar.Status"))
        assertTrue(content.contains("data class Item("))
    }

    @Test
    fun `design templates render readable request response classes`() {
        val renderer = PebbleArtifactRenderer(
            templateResolver = PresetTemplateResolver("ddd-default", emptyList())
        )
        val templateIds = listOf(
            "design/query.kt.peb",
            "design/command.kt.peb",
            "design/capability.kt.peb",
            "design/api_payload.kt.peb",
        )
        val commonRequestFields = listOf(
            mapOf("name" to "messageKey", "renderedType" to "String", "nullable" to false),
        )
        val commonResponseFields = listOf(
            mapOf("name" to "content", "renderedType" to "String", "nullable" to false),
        )
        val commonFields = mapOf(
            "imports" to emptyList<String>(),
            "fields" to commonRequestFields,
            "nestedTypes" to emptyList<Map<String, Any?>>(),
            "resultFields" to commonResponseFields,
            "resultNestedTypes" to emptyList<Map<String, Any?>>(),
        )

        val rendered = renderer.render(
            planItems = listOf(
                ArtifactPlanItem(
                    generatorId = "query",
                    moduleRole = "application",
                    templateId = "design/query.kt.peb",
                    outputPath = "demo-application/src/main/kotlin/edu/only4/danmaku/application/queries/message/read/FindUserMessageQry.kt",
                    context = commonFields + mapOf(
                        "packageName" to "edu.only4.danmaku.application.queries.message.read",
                        "typeName" to "FindUserMessageQry",
                    ),
                    conflictPolicy = ConflictPolicy.OVERWRITE
                ),
                ArtifactPlanItem(
                    generatorId = "command",
                    moduleRole = "application",
                    templateId = "design/command.kt.peb",
                    outputPath = "demo-application/src/main/kotlin/edu/only4/danmaku/application/commands/message/create/CreateUserMessageCmd.kt",
                    context = commonFields + mapOf(
                        "packageName" to "edu.only4.danmaku.application.commands.message.create",
                        "typeName" to "CreateUserMessageCmd",
                    ),
                    conflictPolicy = ConflictPolicy.OVERWRITE
                ),
                ArtifactPlanItem(
                    generatorId = "capability",
                    moduleRole = "application",
                    templateId = "design/capability.kt.peb",
                    outputPath = "demo-application/src/main/kotlin/edu/only4/danmaku/application/capabilities/message/delivery/PublishUserMessage.kt",
                    context = commonFields + mapOf(
                        "packageName" to "edu.only4.danmaku.application.capabilities.message.delivery",
                        "typeName" to "PublishUserMessage",
                    ),
                    conflictPolicy = ConflictPolicy.OVERWRITE
                ),
                ArtifactPlanItem(
                    generatorId = "api-payload",
                    moduleRole = "adapter",
                    templateId = "design/api_payload.kt.peb",
                    outputPath = "demo-adapter/src/main/kotlin/edu/only4/danmaku/adapter/portal/api/payload/message/CreateUserMessagePayload.kt",
                    context = mapOf(
                        "packageName" to "edu.only4.danmaku.adapter.portal.api.payload.message",
                        "typeName" to "CreateUserMessagePayload",
                        "imports" to emptyList<String>(),
                        "fields" to listOf(
                            mapOf("name" to "messageKey", "renderedType" to "String", "nullable" to false),
                            mapOf("name" to "body", "renderedType" to "Body?", "nullable" to true),
                        ),
                        "nestedTypes" to listOf(
                            mapOf(
                                "name" to "Body",
                                "fields" to listOf(
                                    mapOf("name" to "content", "renderedType" to "String", "nullable" to false),
                                ),
                            ),
                        ),
                        "resultFields" to listOf(
                            mapOf("name" to "receipt", "renderedType" to "Receipt?", "nullable" to true),
                        ),
                        "resultNestedTypes" to listOf(
                            mapOf(
                                "name" to "Receipt",
                                "fields" to listOf(
                                    mapOf("name" to "messageKey", "renderedType" to "String", "nullable" to false),
                                ),
                            ),
                        ),
                    ),
                    conflictPolicy = ConflictPolicy.OVERWRITE
                ),
            ),
            config = ProjectConfig(
                basePackage = "edu.only4.danmaku",
                layout = ProjectLayout.MULTI_MODULE,
                modules = emptyMap(),
                sources = emptyMap(),
                generators = emptyMap(),
                templates = TemplateConfig("ddd-default", emptyList(), ConflictPolicy.OVERWRITE),
            )
        )

        templateIds.forEach(::assertMaintainableTemplateSource)
        rendered.forEach {
            assertReadableKotlin(it.content)
            assertFalse(Regex("""package .+import """).containsMatchIn(it.content))
            assertFalse(Regex("""}\s*object """).containsMatchIn(it.content))
            assertFalse(Regex("""\) : (Command|Query|CapabilityCall)<Response>[ \t]+data class Response""").containsMatchIn(it.content))
        }

        val renderedByFile = rendered.associateBy { it.outputPath.substringAfterLast("/") }
        val queryContent = renderedByFile.getValue("FindUserMessageQry.kt").content
        val commandContent = renderedByFile.getValue("CreateUserMessageCmd.kt").content
        val capabilityContent = renderedByFile.getValue("PublishUserMessage.kt").content
        val apiPayloadContent = renderedByFile.getValue("CreateUserMessagePayload.kt").content

        assertTrue(queryContent.contains("import com.only4.cap4k.ddd.core.application.query.Query"))
        assertTrue(queryContent.contains(") : Query<Response>"))
        assertTrue(queryContent.normalizedLineEndings().contains(") : Query<Response>\n\n    data class Response("))
        assertTrue(commandContent.contains("import com.only4.cap4k.ddd.core.application.command.Command"))
        assertTrue(commandContent.contains(") : Command<Response>"))
        assertTrue(commandContent.normalizedLineEndings().contains(") : Command<Response>\n\n    data class Response("))
        assertTrue(capabilityContent.contains("import com.only4.cap4k.ddd.core.application.capability.CapabilityCall"))
        assertTrue(capabilityContent.contains(") : CapabilityCall<Response>"))
        assertTrue(capabilityContent.normalizedLineEndings().contains(") : CapabilityCall<Response>\n\n    data class Response("))
        listOf(queryContent, commandContent, capabilityContent).forEach { content ->
            assertFalse(content.contains("\n\n\n"))
        }

        assertFalse(commandContent.contains("import com.only4.cap4k.ddd.core.Mediator"))
        assertTrue(commandContent.contains("import com.only4.cap4k.ddd.core.application.command.Command"))
        assertTrue(commandContent.contains("import com.only4.cap4k.ddd.core.application.command.CommandHandler"))
        assertTrue(commandContent.contains("import org.springframework.stereotype.Service"))
        assertTrue(commandContent.contains("@Service\n    class Handler : CommandHandler<Request, Response>"))
        assertTrue(commandContent.contains("override fun handle(command: Request): Response"))
        assertFalse(commandContent.contains("Mediator.uow.save()"))
        assertTrue(
            commandContent.normalizedLineEndings().contains(
                "            return Response(\n" +
                    "                content = TODO(\"set content\")\n" +
                    "            )"
            )
        )

        val requestIndex = apiPayloadContent.indexOf("data class Request(")
        val responseIndex = apiPayloadContent.indexOf("data class Response(")
        assertTrue(requestIndex >= 0, "Request class must be rendered.")
        assertTrue(responseIndex >= 0, "Response class must be rendered.")
        val requestSection = apiPayloadContent.substring(requestIndex, responseIndex)
        val responseSection = apiPayloadContent.substring(responseIndex)
        assertTrue(requestSection.contains("        data class Body("))
        assertTrue(requestSection.contains("val content: String"))
        assertFalse(requestSection.contains("data class Receipt("))
        assertTrue(responseSection.contains("        data class Receipt("))
        assertTrue(responseSection.contains("val messageKey: String"))
        assertFalse(Regex("^ {4}data class Body\\(", RegexOption.MULTILINE).containsMatchIn(apiPayloadContent))
        assertFalse(Regex("^ {4}data class Receipt\\(", RegexOption.MULTILINE).containsMatchIn(apiPayloadContent))
        assertTrue(apiPayloadContent.normalizedLineEndings().contains("    }\n\n    data class Response("))
    }

    @Test
    fun `renders field default values in preset command design template`() {
        val overrideDir = Files.createTempDirectory("cap4k-override-empty-command-defaults")
        val renderer = PebbleArtifactRenderer(
            templateResolver = PresetTemplateResolver(
                preset = "ddd-default",
                overrideDirs = listOf(overrideDir.toString())
            )
        )

        val rendered = renderer.render(
            planItems = listOf(
                ArtifactPlanItem(
                    generatorId = "command",
                    moduleRole = "application",
                    templateId = "design/command.kt.peb",
                    outputPath = "demo-application/src/main/kotlin/com/acme/demo/application/commands/order/submit/SubmitOrderCmd.kt",
                    context = mapOf(
                        "packageName" to "com.acme.demo.application.commands.order.submit",
                        "typeName" to "SubmitOrderCmd",
                        "imports" to emptyList<String>(),
                        "fields" to listOf(
                            mapOf("name" to "title", "renderedType" to "String", "nullable" to false, "defaultValue" to "\"demo\""),
                            mapOf("name" to "retryCount", "renderedType" to "Long", "nullable" to false, "defaultValue" to "1L"),
                        ),
                        "nestedTypes" to listOf(
                            mapOf(
                                "name" to "Metadata",
                                "fields" to listOf(
                                    mapOf("name" to "source", "renderedType" to "String", "nullable" to false, "defaultValue" to "\"api\""),
                                ),
                            ),
                        ),
                        "resultFields" to listOf(
                            mapOf("name" to "enabled", "renderedType" to "Boolean", "nullable" to false, "defaultValue" to "true"),
                        ),
                        "resultNestedTypes" to listOf(
                            mapOf(
                                "name" to "Result",
                                "fields" to listOf(
                                    mapOf("name" to "status", "renderedType" to "String", "nullable" to false, "defaultValue" to "\"OK\""),
                                ),
                            ),
                        ),
                    ),
                    conflictPolicy = ConflictPolicy.SKIP
                )
            ),
            config = ProjectConfig(
                basePackage = "com.acme.demo",
                layout = ProjectLayout.MULTI_MODULE,
                modules = emptyMap(),
                sources = emptyMap(),
                generators = emptyMap(),
                templates = TemplateConfig(
                    preset = "ddd-default",
                    overrideDirs = listOf(overrideDir.toString()),
                    conflictPolicy = ConflictPolicy.SKIP
                )
            )
        )

        val content = rendered.single().content
        assertTrue(content.contains("val title: String = \"demo\""))
        assertTrue(content.contains("val retryCount: Long = 1L"))
        assertTrue(content.contains("val source: String = \"api\""))
        assertTrue(content.contains("val enabled: Boolean = true"))
        assertTrue(content.contains("val status: String = \"OK\""))
    }

    @Test
    fun `renders field default values in preset query design template`() {
        val overrideDir = Files.createTempDirectory("cap4k-override-empty-query-defaults")
        val renderer = PebbleArtifactRenderer(
            templateResolver = PresetTemplateResolver(
                preset = "ddd-default",
                overrideDirs = listOf(overrideDir.toString())
            )
        )

        val rendered = renderer.render(
            planItems = listOf(
                ArtifactPlanItem(
                    generatorId = "command",
                    moduleRole = "application",
                    templateId = "design/query.kt.peb",
                    outputPath = "demo-application/src/main/kotlin/com/acme/demo/application/queries/FindOrderQry.kt",
                    context = mapOf(
                        "packageName" to "com.acme.demo.application.queries",
                        "typeName" to "FindOrderQry",
                        "imports" to emptyList<String>(),
                        "fields" to listOf(
                            mapOf("name" to "status", "renderedType" to "String", "nullable" to false, "defaultValue" to "\"ACTIVE\""),
                        ),
                        "nestedTypes" to listOf(
                            mapOf(
                                "name" to "Criteria",
                                "fields" to listOf(
                                    mapOf("name" to "priority", "renderedType" to "Long", "nullable" to false, "defaultValue" to "1L"),
                                ),
                            ),
                        ),
                        "resultFields" to listOf(
                            mapOf("name" to "fallback", "renderedType" to "Boolean", "nullable" to false, "defaultValue" to "false"),
                        ),
                        "resultNestedTypes" to listOf(
                            mapOf(
                                "name" to "Result",
                                "fields" to listOf(
                                    mapOf("name" to "code", "renderedType" to "String", "nullable" to false, "defaultValue" to "\"DONE\""),
                                ),
                            ),
                        ),
                    ),
                    conflictPolicy = ConflictPolicy.SKIP
                )
            ),
            config = ProjectConfig(
                basePackage = "com.acme.demo",
                layout = ProjectLayout.MULTI_MODULE,
                modules = emptyMap(),
                sources = emptyMap(),
                generators = emptyMap(),
                templates = TemplateConfig(
                    preset = "ddd-default",
                    overrideDirs = listOf(overrideDir.toString()),
                    conflictPolicy = ConflictPolicy.SKIP
                )
            )
        )

        val content = rendered.single().content
        assertTrue(content.contains("val status: String = \"ACTIVE\""))
        assertTrue(content.contains("val priority: Long = 1L"))
        assertTrue(content.contains("val fallback: Boolean = false"))
        assertTrue(content.contains("val code: String = \"DONE\""))
    }

    @Test
    fun `default query preset uses query contract`() {
        val overrideDir = Files.createTempDirectory("cap4k-override-empty-query-contract")
        val renderer = PebbleArtifactRenderer(
            templateResolver = PresetTemplateResolver(
                preset = "ddd-default",
                overrideDirs = listOf(overrideDir.toString())
            )
        )

        val rendered = renderer.render(
            planItems = listOf(
                ArtifactPlanItem(
                    generatorId = "command",
                    moduleRole = "application",
                    templateId = "design/query.kt.peb",
                    outputPath = "demo-application/src/main/kotlin/com/acme/demo/application/queries/FindOrderQry.kt",
                    context = mapOf(
                        "packageName" to "com.acme.demo.application.queries",
                        "typeName" to "FindOrderQry",
                        "imports" to listOf(
                            "java.time.LocalDateTime",
                            "java.util.UUID",
                        ),
                        "fields" to listOf(
                            mapOf("name" to "lookupId", "renderedType" to "UUID", "nullable" to false),
                            mapOf("name" to "requestStatus", "renderedType" to "com.foo.Status", "nullable" to false),
                            mapOf("name" to "createdAfter", "renderedType" to "LocalDateTime", "nullable" to false),
                        ),
                        "nestedTypes" to emptyList<Map<String, Any?>>(),
                        "resultFields" to listOf(
                            mapOf("name" to "responseStatus", "renderedType" to "com.bar.Status", "nullable" to false),
                        ),
                        "resultNestedTypes" to emptyList<Map<String, Any?>>(),
                    ),
                    conflictPolicy = ConflictPolicy.SKIP
                )
            ),
            config = ProjectConfig(
                basePackage = "com.acme.demo",
                layout = ProjectLayout.MULTI_MODULE,
                modules = emptyMap(),
                sources = emptyMap(),
                generators = emptyMap(),
                templates = TemplateConfig(
                    preset = "ddd-default",
                    overrideDirs = listOf(overrideDir.toString()),
                    conflictPolicy = ConflictPolicy.SKIP
                )
            )
        )

        val content = rendered.single().content
        assertTrue(content.contains("import com.only4.cap4k.ddd.core.application.query.Query"))
        assertTrue(content.contains("import java.time.LocalDateTime"))
        assertTrue(content.contains("import java.util.UUID"))
        assertFalse(content.contains("import com.foo.Status"))
        assertFalse(content.contains("import com.bar.Status"))
        assertTrue(content.contains("object FindOrderQry"))
        assertTrue(content.contains("data class Request("))
        assertTrue(content.contains(") : Query<Response>"))
        assertTrue(content.contains("val lookupId: UUID"))
        assertTrue(content.contains("val requestStatus: com.foo.Status"))
        assertTrue(content.contains("val responseStatus: com.bar.Status"))
    }

    @Test
    fun `renders query page request with complete response envelope`() {
        val overrideDir = Files.createTempDirectory("cap4k-override-empty-query-page-contract")
        val renderer = PebbleArtifactRenderer(
            templateResolver = PresetTemplateResolver(
                preset = "ddd-default",
                overrideDirs = listOf(overrideDir.toString())
            )
        )

        val rendered = renderer.render(
            planItems = listOf(
                ArtifactPlanItem(
                    generatorId = "command",
                    moduleRole = "application",
                    templateId = "design/query.kt.peb",
                    outputPath = "demo-application/src/main/kotlin/com/acme/demo/application/queries/FindOrderPageQry.kt",
                    context = mapOf(
                        "packageName" to "com.acme.demo.application.queries",
                        "typeName" to "FindOrderPageQry",
                        "imports" to listOf(
                            "com.only4.cap4k.ddd.core.share.PageData",
                        ),
                        "pageRequest" to true,
                        "fields" to listOf(
                            mapOf("name" to "keyword", "renderedType" to "String?", "nullable" to true),
                        ),
                        "nestedTypes" to emptyList<Map<String, Any?>>(),
                        "resultFields" to listOf(
                            mapOf("name" to "page", "renderedType" to "PageData<Item>", "nullable" to false),
                        ),
                        "resultNestedTypes" to listOf(
                            mapOf(
                                "name" to "Item",
                                "fields" to listOf(
                                    mapOf("name" to "orderId", "renderedType" to "Long", "nullable" to false),
                                    mapOf("name" to "title", "renderedType" to "String", "nullable" to false),
                                ),
                            ),
                        ),
                    ),
                    conflictPolicy = ConflictPolicy.SKIP
                )
            ),
            config = ProjectConfig(
                basePackage = "com.acme.demo",
                layout = ProjectLayout.MULTI_MODULE,
                modules = emptyMap(),
                sources = emptyMap(),
                generators = emptyMap(),
                templates = TemplateConfig(
                    preset = "ddd-default",
                    overrideDirs = listOf(overrideDir.toString()),
                    conflictPolicy = ConflictPolicy.SKIP
                )
            )
        )

        val pageContent = rendered.single().content
        assertTrue(pageContent.contains("import com.only4.cap4k.ddd.core.application.query.PageRequest"))
        assertTrue(pageContent.contains("import com.only4.cap4k.ddd.core.application.query.Query"))
        assertTrue(pageContent.contains("data class Request("))
        assertTrue(pageContent.contains("override val pageNum: Int = 1"))
        assertTrue(pageContent.contains("override val pageSize: Int = 10"))
        assertTrue(pageContent.contains("val keyword: String?"))
        assertTrue(
            Regex(
                "override val pageNum: Int = 1,\\n\\s*" +
                    "override val pageSize: Int = 10,\\n\\s*" +
                    "val keyword: String?"
            ).containsMatchIn(pageContent.normalizedLineEndings()),
            pageContent
        )
        assertTrue(pageContent.contains(") : PageRequest, Query<Response>"))
        assertTrue(pageContent.contains("val page: PageData<Item>"))
        assertTrue(pageContent.contains("data class Item("))
        assertTrue(pageContent.contains("val orderId: Long"))
        assertTrue(pageContent.contains("val title: String"))
    }

    @Test
    fun `renders api payload page request without request param`() {
        val overrideDir = Files.createTempDirectory("cap4k-override-empty-design-api-payload-page")
        val renderer = PebbleArtifactRenderer(
            templateResolver = PresetTemplateResolver(
                preset = "ddd-default",
                overrideDirs = listOf(overrideDir.toString())
            )
        )

        val rendered = renderer.render(
            planItems = listOf(
                ArtifactPlanItem(
                    generatorId = "api-payload",
                    moduleRole = "adapter",
                    templateId = "design/api_payload.kt.peb",
                    outputPath = "demo-adapter/src/main/kotlin/com/acme/demo/adapter/payload/FindOrderPage.kt",
                    context = mapOf(
                        "packageName" to "com.acme.demo.adapter.payload",
                        "typeName" to "FindOrderPage",
                        "imports" to listOf("com.only4.cap4k.ddd.core.share.PageData"),
                        "pageRequest" to true,
                        "fields" to listOf(
                            mapOf("name" to "keyword", "renderedType" to "String?", "nullable" to true),
                        ),
                        "nestedTypes" to emptyList<Map<String, Any?>>(),
                        "resultFields" to listOf(
                            mapOf("name" to "page", "renderedType" to "PageData<Item>", "nullable" to false),
                        ),
                        "resultNestedTypes" to listOf(
                            mapOf(
                                "name" to "Item",
                                "fields" to listOf(
                                    mapOf("name" to "orderId", "renderedType" to "Long", "nullable" to false),
                                ),
                            ),
                        ),
                    ),
                    conflictPolicy = ConflictPolicy.SKIP
                )
            ),
            config = ProjectConfig(
                basePackage = "com.acme.demo",
                layout = ProjectLayout.MULTI_MODULE,
                modules = emptyMap(),
                sources = emptyMap(),
                generators = emptyMap(),
                templates = TemplateConfig(
                    preset = "ddd-default",
                    overrideDirs = listOf(overrideDir.toString()),
                    conflictPolicy = ConflictPolicy.SKIP
                )
            )
        )

        val content = rendered.single().content
        assertTrue(content.contains("import com.only4.cap4k.ddd.core.application.query.PageRequest"))
        assertTrue(content.contains("data class Request("))
        assertTrue(content.contains("override val pageNum: Int = 1"))
        assertTrue(content.contains("override val pageSize: Int = 10"))
        assertTrue(content.contains("val keyword: String?"))
        assertTrue(
            Regex(
                "override val pageNum: Int = 1,\\n\\s*" +
                    "override val pageSize: Int = 10,\\n\\s*" +
                    "val keyword: String?"
            ).containsMatchIn(content.normalizedLineEndings()),
            content
        )
        assertTrue(content.contains(") : PageRequest"))
        assertFalse(content.contains("RequestParam<Response>"))
        assertTrue(content.contains("val page: PageData<Item>"))
        assertTrue(content.contains("data class Item("))
        assertTrue(content.contains("val orderId: Long"))
    }

    @Test
    fun `renders empty request as contract class and response as stable object`() {
        val overrideDir = Files.createTempDirectory("cap4k-override-empty-design-empty")
        val renderer = PebbleArtifactRenderer(
            templateResolver = PresetTemplateResolver(
                preset = "ddd-default",
                overrideDirs = listOf(overrideDir.toString())
            )
        )

        val rendered = renderer.render(
            planItems = listOf(
                ArtifactPlanItem(
                    generatorId = "command",
                    moduleRole = "application",
                    templateId = "design/query.kt.peb",
                    outputPath = "demo-application/src/main/kotlin/com/acme/demo/application/queries/order/read/FindOrderQry.kt",
                    context = mapOf(
                        "packageName" to "com.acme.demo.application.queries.order.read",
                        "typeName" to "FindOrderQry",
                        "imports" to emptyList<String>(),
                        "fields" to emptyList<Map<String, Any?>>(),
                        "nestedTypes" to emptyList<Map<String, Any?>>(),
                        "resultFields" to emptyList<Map<String, Any?>>(),
                        "resultNestedTypes" to emptyList<Map<String, Any?>>(),
                    ),
                    conflictPolicy = ConflictPolicy.SKIP
                )
            ),
            config = ProjectConfig(
                basePackage = "com.acme.demo",
                layout = ProjectLayout.MULTI_MODULE,
                modules = emptyMap(),
                sources = emptyMap(),
                generators = emptyMap(),
                templates = TemplateConfig(
                    preset = "ddd-default",
                    overrideDirs = listOf(overrideDir.toString()),
                    conflictPolicy = ConflictPolicy.SKIP
                )
            )
        )

        val content = rendered.single().content
        assertTrue(content.contains("object FindOrderQry"))
        assertTrue(content.contains("import com.only4.cap4k.ddd.core.application.query.Query"))
        assertTrue(content.contains("class Request : Query<Response>"))
        assertTrue(content.contains("data object Response"))
    }

    @Test
    fun `falls back to preset aggregate templates and renders aggregate content`() {
        val overrideDir = Files.createTempDirectory("cap4k-override-empty-aggregate")
        val renderer = PebbleArtifactRenderer(
            templateResolver = PresetTemplateResolver(
                preset = "ddd-default",
                overrideDirs = listOf(overrideDir.toString())
            )
        )

        val rendered = renderer.render(
            planItems = listOf(
                ArtifactPlanItem(
                    generatorId = "aggregate",
                    moduleRole = "domain",
                    templateId = "aggregate/schema.kt.peb",
                    outputPath = "demo-domain/src/main/kotlin/com/acme/demo/domain/_share/meta/order/SOrder.kt",
                    context = mapOf(
                        "packageName" to "com.acme.demo.domain._share.meta.order",
                        "typeName" to "SOrder",
                        "entityName" to "Order",
                        "schemaRuntimePackage" to "com.only4.cap4k.ddd.domain.repo.schema",
                        "entityTypeFqn" to "com.acme.demo.domain.aggregates.order.Order",
                        "isAggregateRoot" to true,
                        "fields" to listOf(
                            mapOf(
                                "name" to "id",
                                "fieldName" to "id",
                                "columnName" to "id",
                                "fieldType" to "Long",
                                "type" to "Long",
                                "nullable" to false,
                            ),
                            mapOf(
                                "name" to "orderNo",
                                "fieldName" to "orderNo",
                                "columnName" to "order_no",
                                "fieldType" to "String",
                                "type" to "String",
                                "nullable" to true,
                            )
                        )
                    ),
                    conflictPolicy = ConflictPolicy.SKIP
                ),
                ArtifactPlanItem(
                    generatorId = "aggregate",
                    moduleRole = "domain",
                    templateId = "aggregate/entity.kt.peb",
                    outputPath = "demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/order/Order.kt",
                    context = mapOf(
                        "packageName" to "com.acme.demo.domain.aggregates.order",
                        "typeName" to "Order",
                        "comment" to "Order aggregate",
                        "aggregateName" to "Order",
                        "aggregateRoot" to true,
                        "idField" to FieldModel("id", "Long"),
                        "jpaImports" to emptyList<String>(),
                        "imports" to emptyList<String>(),
                        "scalarFields" to entityScalarFields(
                            mapOf(
                                "name" to "id",
                                "type" to "Long",
                                "propertyInitializer" to "id",
                                "nullable" to false,
                                "propertyNullable" to false,
                            ),
                            mapOf(
                                "name" to "orderNo",
                                "type" to "String",
                                "propertyInitializer" to "orderNo",
                                "nullable" to true,
                                "propertyNullable" to true,
                            )
                        ),
                        "fields" to listOf(
                            mapOf("name" to "id", "type" to "Long", "nullable" to false),
                            mapOf("name" to "orderNo", "type" to "String", "nullable" to true)
                        ),
                        "relationFields" to emptyList<Map<String, Any?>>()
                    ),
                    conflictPolicy = ConflictPolicy.SKIP
                ),
                ArtifactPlanItem(
                    generatorId = "aggregate",
                    moduleRole = "adapter",
                    templateId = "aggregate/repository.kt.peb",
                    outputPath = "demo-adapter/src/main/kotlin/com/acme/demo/adapter/domain/repositories/OrderRepository.kt",
                    context = mapOf(
                        "packageName" to "com.acme.demo.adapter.domain.repositories",
                        "typeName" to "OrderRepository",
                        "entityName" to "Order",
                        "entityTypeFqn" to "com.acme.demo.domain.aggregates.order.Order",
                        "aggregateName" to "Order",
                        "idType" to "Long",
                    ),
                    conflictPolicy = ConflictPolicy.SKIP
                ),
                ArtifactPlanItem(
                    generatorId = "aggregate",
                    moduleRole = "domain",
                    templateId = "aggregate/factory.kt.peb",
                    outputPath = "demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/order/factory/OrderFactory.kt",
                    context = mapOf(
                        "packageName" to "com.acme.demo.domain.aggregates.order.factory",
                        "typeName" to "OrderFactory",
                        "payloadTypeName" to "Payload",
                        "payloadMetadataName" to "OrderPayload",
                        "payloadFields" to listOf(
                            mapOf("name" to "orderNo", "renderedType" to "String?", "defaultValue" to "null"),
                        ),
                        "rootConstructorFields" to listOf(mapOf("name" to "orderNo")),
                        "rootRelations" to emptyList<Map<String, Any?>>(),
                        "helpers" to emptyList<Map<String, Any?>>(),
                        "entityName" to "Order",
                        "entityTypeFqn" to "com.acme.demo.domain.aggregates.order.Order",
                        "aggregateName" to "Order",
                        "comment" to "Order aggregate",
                        "imports" to listOf("com.acme.demo.domain.aggregates.order.Order"),
                    ),
                    conflictPolicy = ConflictPolicy.SKIP
                ),
            ),
            config = ProjectConfig(
                basePackage = "com.acme.demo",
                layout = ProjectLayout.MULTI_MODULE,
                modules = emptyMap(),
                sources = emptyMap(),
                generators = emptyMap(),
                templates = TemplateConfig(
                    preset = "ddd-default",
                    overrideDirs = listOf(overrideDir.toString()),
                    conflictPolicy = ConflictPolicy.SKIP
                )
            )
        )

        val aggregateArtifacts = rendered.reversed()

        assertEquals(4, aggregateArtifacts.size)

        fun contentFor(pathSuffix: String): String = aggregateArtifacts.single {
            it.outputPath.endsWith(pathSuffix)
        }.content

        val schemaContent = contentFor("/_share/meta/order/SOrder.kt")
        val entityContent = contentFor("/aggregates/order/Order.kt")
        val repositoryContent = contentFor("/adapter/domain/repositories/OrderRepository.kt")
        val factoryContent = contentFor("/factory/OrderFactory.kt")

        assertTrue(schemaContent.contains("import com.only4.cap4k.ddd.domain.repo.schema.SchemaSpecification"))
        assertTrue(schemaContent.contains("import com.only4.cap4k.ddd.domain.repo.schema.Field"))
        assertTrue(schemaContent.contains("class SOrder("))
        assertTrue(schemaContent.contains("fun specify(builder: PredicateBuilder<SOrder>): Specification<Order>"))
        assertTrue(schemaContent.contains("val orderNo: Field<String>"))
        assertTrue(entityContent.contains("class Order internal constructor("))
        assertFalse(entityContent.contains("data class Order("))
        assertTrue(entityContent.contains("orderNo: String?"))
        assertTrue(entityContent.contains("var orderNo: String? = orderNo"))
        assertFalse(entityContent.contains("jakarta.persistence"))
        assertTrue(repositoryContent.contains("@Repository"))
        assertTrue(repositoryContent.contains("interface OrderRepository : JpaRepository<Order, Long>, JpaSpecificationExecutor<Order>"))
        assertTrue(factoryContent.contains("import com.only4.cap4k.ddd.core.domain.aggregate.AggregateFactory"))
        assertTrue(factoryContent.contains("import com.only4.cap4k.ddd.core.domain.aggregate.AggregatePayload"))
        assertFalse(factoryContent.contains(legacyAggregateAnnotationFq))
        assertFalse(factoryContent.contains(legacyAggregateCall))
        assertTrue(factoryContent.contains("import org.springframework.stereotype.Service"))
        assertTrue(factoryContent.contains("import com.acme.demo.domain.aggregates.order.Order"))
        assertTrue(factoryContent.contains("class OrderFactory : AggregateFactory<OrderFactory.Payload, Order>"))
        assertFalse(factoryContent.contains("""TODO("Implement aggregate construction")"""))
        assertTrue(factoryContent.contains("orderNo = entityPayload.orderNo"))
        assertTrue(factoryContent.contains("data class Payload("))
        assertTrue(factoryContent.contains("val orderNo: String? = null"))
        assertTrue(schemaContent.contains("fun predicateById(id: Any): JpaPredicate<Order>"))
        assertTrue(schemaContent.contains("fun predicate(builder: PredicateBuilder<SOrder>): JpaPredicate<Order>"))
        assertFalse(schemaContent.contains("AggregatePredicate"))
        assertFalse(schemaContent.contains("AggOrder"))
    }

    @Test
    fun `aggregate entity preset renders bounded relation-side jpa controls`() {
        val overrideDir = Files.createTempDirectory("cap4k-override-empty-aggregate-relation")
        val renderer = PebbleArtifactRenderer(
            templateResolver = PresetTemplateResolver(
                preset = "ddd-default",
                overrideDirs = listOf(overrideDir.toString())
            )
        )

        val rendered = renderer.render(
            planItems = listOf(
                ArtifactPlanItem(
                    generatorId = "aggregate",
                    moduleRole = "domain",
                    templateId = "aggregate/entity.kt.peb",
                    outputPath = "demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/VideoPost.kt",
                    context = mapOf(
                        "packageName" to "com.acme.demo.domain.aggregates.video_post",
                        "typeName" to "VideoPost",
                        "comment" to "video post",
                        "aggregateName" to "VideoPost",
                        "aggregateRoot" to true,
                        "entityJpa" to mapOf(
                            "entityEnabled" to true,
                            "tableName" to "video_post",
                            "tableNameKotlinStringLiteral" to "\"\\\"video_post\\\"\"",
                        ),
                        "jpaImports" to listOf(
                            "jakarta.persistence.CascadeType",
                            "jakarta.persistence.FetchType",
                            "jakarta.persistence.JoinColumn",
                            "jakarta.persistence.ManyToOne",
                            "jakarta.persistence.OneToMany",
                            "jakarta.persistence.OneToOne",
                            "jakarta.persistence.Transient",
                        ),
                        "imports" to listOf(
                            "com.acme.demo.domain.identity.user.UserProfile",
                            "com.acme.demo.domain.identity.user.CoverProfile",
                            "com.acme.demo.domain.aggregates.video_post.item.VideoPostItem",
                            "com.only4.cap4k.ddd.core.domain.aggregate.OwnedEntityList",
                        ),
                        "constructorFields" to listOf(
                            mapOf("name" to "id", "type" to "Long", "nullable" to false),
                        ),
                        "scalarFields" to entityScalarFields(
                            mapOf(
                                "name" to "id",
                                "type" to "Long",
                                "propertyInitializer" to "id",
                                "nullable" to false,
                                "propertyNullable" to false,
                                "columnName" to "id",
                                "isId" to true,
                                "converterTypeRef" to null,
                            )
                        ),
                        "fields" to listOf(
                            mapOf("name" to "id", "type" to "Long", "nullable" to false)
                        ),
                        "relationFields" to listOf(
                            mapOf(
                                "name" to "author",
                                "targetType" to "UserProfile",
                                "targetTypeRef" to "UserProfile",
                                "targetPackageName" to "com.acme.demo.domain.identity.user",
                                "relationType" to "MANY_TO_ONE",
                                "fetchType" to "LAZY",
                                "joinColumn" to "author_id",
                                "nullable" to true,
                                "joinColumnNullable" to false,
                            ),
                            mapOf(
                                "name" to "coverProfile",
                                "targetType" to "CoverProfile",
                                "targetTypeRef" to "CoverProfile",
                                "targetPackageName" to "com.acme.demo.domain.identity.user",
                                "relationType" to "ONE_TO_ONE",
                                "fetchType" to "LAZY",
                                "joinColumn" to "cover_profile_id",
                                "nullable" to true,
                                "joinColumnNullable" to true,
                            ),
                            mapOf(
                                "name" to "items",
                                "targetType" to "VideoPostItem",
                                "targetTypeRef" to "VideoPostItem",
                                "targetPackageName" to "com.acme.demo.domain.aggregates.video_post.item",
                                "relationType" to "ONE_TO_MANY",
                                "fetchType" to "LAZY",
                                "joinColumn" to "video_post_id",
                                "cascadeTypes" to listOf("PERSIST", "MERGE", "REMOVE"),
                                "orphanRemoval" to true,
                                "joinColumnNullable" to false,
                                "owned" to true,
                                "parentRefColumn" to "video_post_id",
                                "ownedCardinality" to "MANY",
                                "persistenceShape" to "ONE_TO_MANY_JOIN_COLUMN",
                                "domainName" to "items",
                                "persistencePathName" to "_items",
                                "backingCollectionName" to "_items",
                                "singleAccessorName" to null,
                            )
                        ),
                    ),
                    conflictPolicy = ConflictPolicy.SKIP,
                )
            ),
            config = ProjectConfig(
                basePackage = "com.acme.demo",
                layout = ProjectLayout.MULTI_MODULE,
                modules = emptyMap(),
                sources = emptyMap(),
                generators = emptyMap(),
                templates = TemplateConfig(
                    preset = "ddd-default",
                    overrideDirs = listOf(overrideDir.toString()),
                    conflictPolicy = ConflictPolicy.SKIP
                )
            )
        )

        val content = rendered.single().content
        val constructorSection = content.substringBefore(") {")
        val bodySection = content.substringAfter(") {")
        assertTrue(content.contains("import jakarta.persistence.CascadeType"))
        assertTrue(content.contains("import jakarta.persistence.FetchType"))
        assertTrue(content.contains("import jakarta.persistence.JoinColumn"))
        assertTrue(content.contains("import jakarta.persistence.ManyToOne"))
        assertTrue(content.contains("import jakarta.persistence.OneToMany"))
        assertTrue(content.contains("import jakarta.persistence.OneToOne"))
        assertTrue(content.contains("@Entity"))
        assertTrue(content.contains("@Table(name = \"video_post\")"))
        assertTrue(content.contains("import com.acme.demo.domain.identity.user.UserProfile"))
        assertTrue(content.contains("import com.acme.demo.domain.identity.user.CoverProfile"))
        assertTrue(content.contains("import com.acme.demo.domain.aggregates.video_post.item.VideoPostItem"))
        assertTrue(content.contains("class VideoPost internal constructor("))
        assertFalse(content.contains("data class VideoPost("))
        assertTrue(content.contains(") {"))
        assertTrue(constructorSection.contains("id: Long"))
        assertFalse(constructorSection.contains("val id: Long"))
        assertFalse(constructorSection.contains("author"))
        assertFalse(constructorSection.contains("coverProfile"))
        assertFalse(constructorSection.contains("items"))
        assertTrue(content.contains("@ManyToOne(fetch = FetchType.LAZY)"))
        assertTrue(content.contains("@JoinColumn(name = \"author_id\", nullable = false)"))
        assertTrue(bodySection.contains("var author: UserProfile? = null"))
        assertTrue(content.contains("@OneToOne(fetch = FetchType.LAZY)"))
        assertTrue(bodySection.contains("@JoinColumn(name = \"cover_profile_id\", nullable = true)"))
        assertTrue(bodySection.contains("var coverProfile: CoverProfile? = null"))
        assertTrue(content.contains("@OneToMany(fetch = FetchType.LAZY, cascade = [CascadeType.PERSIST, CascadeType.MERGE, CascadeType.REMOVE], orphanRemoval = true)"))
        assertFalse(content.contains("CascadeType.ALL"))
        assertTrue(content.contains("@JoinColumn(name = \"video_post_id\", nullable = false)"))
        assertFalse(content.contains("mappedBy ="))
        assertTrue(bodySection.contains("private var _items: MutableList<VideoPostItem> = mutableListOf()"))
        assertTrue(bodySection.contains("val items: OwnedEntityList<VideoPostItem>"))
        assertTrue(bodySection.contains("OwnedEntityList.of(_items, VideoPostItem::class, \"VideoPost.items\")"))
        assertFalse(bodySection.contains("val items: MutableList<VideoPostItem> = mutableListOf()"))
    }

    @Test
    fun `aggregate entity preset renders inverse read only many to one relation controls`() {
        val overrideDir = Files.createTempDirectory("cap4k-override-empty-aggregate-inverse-relation")
        val renderer = PebbleArtifactRenderer(
            templateResolver = PresetTemplateResolver(
                preset = "ddd-default",
                overrideDirs = listOf(overrideDir.toString())
            )
        )

        val rendered = renderer.render(
            planItems = listOf(
                ArtifactPlanItem(
                    generatorId = "aggregate",
                    moduleRole = "domain",
                    templateId = "aggregate/entity.kt.peb",
                    outputPath = "demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post_item/VideoPostItem.kt",
                    context = mapOf(
                        "packageName" to "com.acme.demo.domain.aggregates.video_post_item",
                        "typeName" to "VideoPostItem",
                        "comment" to "video post item",
                        "aggregateName" to "VideoPostItem",
                        "aggregateRoot" to true,
                        "entityJpa" to mapOf(
                            "entityEnabled" to true,
                            "tableName" to "video_post_item",
                        ),
                        "jpaImports" to listOf(
                            "jakarta.persistence.FetchType",
                            "jakarta.persistence.JoinColumn",
                            "jakarta.persistence.ManyToOne",
                        ),
                        "imports" to listOf("com.acme.demo.domain.aggregates.video_post.VideoPost"),
                        "scalarFields" to entityScalarFields(
                            mapOf(
                                "name" to "id",
                                "type" to "Long",
                                "nullable" to false,
                                "propertyNullable" to false,
                                "columnName" to "id",
                                "isId" to true,
                                "converterTypeRef" to null,
                            ),
                            mapOf(
                                "name" to "videoPostId",
                                "type" to "Long",
                                "propertyInitializer" to "videoPostId",
                                "nullable" to false,
                                "propertyNullable" to false,
                                "columnName" to "video_post_id",
                                "isId" to false,
                                "converterTypeRef" to null,
                                "insertable" to false,
                                "updatable" to false,
                            ),
                        ),
                        "fields" to listOf(
                            mapOf("name" to "id", "type" to "Long", "nullable" to false),
                            mapOf("name" to "videoPostId", "type" to "Long", "nullable" to false),
                        ),
                        "relationFields" to listOf(
                            mapOf(
                                "name" to "videoPost",
                                "targetType" to "VideoPost",
                                "targetTypeRef" to "VideoPost",
                                "targetPackageName" to "com.acme.demo.domain.aggregates.video_post",
                                "relationType" to "MANY_TO_ONE",
                                "fetchType" to "LAZY",
                                "joinColumn" to "video_post_id",
                                "nullable" to false,
                                "joinColumnNullable" to false,
                                "readOnly" to true,
                                "insertable" to false,
                                "updatable" to false,
                            )
                        ),
                    ),
                    conflictPolicy = ConflictPolicy.SKIP,
                )
            ),
            config = ProjectConfig(
                basePackage = "com.acme.demo",
                layout = ProjectLayout.MULTI_MODULE,
                modules = emptyMap(),
                sources = emptyMap(),
                generators = emptyMap(),
                templates = TemplateConfig(
                    preset = "ddd-default",
                    overrideDirs = listOf(overrideDir.toString()),
                    conflictPolicy = ConflictPolicy.SKIP
                )
            )
        )

        val content = rendered.single().content

        assertTrue(content.contains("@Column(name = \"video_post_id\", insertable = false, updatable = false)"))
        assertTrue(content.contains("var videoPostId: Long = videoPostId"))
        assertTrue(content.contains("@ManyToOne(fetch = FetchType.LAZY)"))
        assertTrue(
            content.contains(
                "@JoinColumn(name = \"video_post_id\", nullable = false, insertable = false, updatable = false)"
            )
        )
        assertTrue(content.contains("lateinit var videoPost: VideoPost"))
        assertFalse(content.contains("@JoinColumn(name = \"video_post_id\", nullable = false)\n    lateinit var videoPost: VideoPost"))
        assertFalse(content.contains("mappedBy ="))
        assertFalse(content.contains("JoinTable"))
        assertFalse(content.contains("ManyToMany"))
    }

    @Test
    fun `aggregate entity preset does not render cascade type import for direct-only relation controls`() {
        val overrideDir = Files.createTempDirectory("cap4k-override-empty-aggregate-direct-relation")
        val renderer = PebbleArtifactRenderer(
            templateResolver = PresetTemplateResolver(
                preset = "ddd-default",
                overrideDirs = listOf(overrideDir.toString())
            )
        )

        val rendered = renderer.render(
            planItems = listOf(
                ArtifactPlanItem(
                    generatorId = "aggregate",
                    moduleRole = "domain",
                    templateId = "aggregate/entity.kt.peb",
                    outputPath = "demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/VideoPost.kt",
                    context = mapOf(
                        "packageName" to "com.acme.demo.domain.aggregates.video_post",
                        "typeName" to "VideoPost",
                        "comment" to "video post",
                        "aggregateName" to "VideoPost",
                        "aggregateRoot" to true,
                        "entityJpa" to mapOf(
                            "entityEnabled" to true,
                            "tableName" to "video_post",
                        ),
                        "jpaImports" to listOf(
                            "jakarta.persistence.FetchType",
                            "jakarta.persistence.JoinColumn",
                            "jakarta.persistence.ManyToOne",
                            "jakarta.persistence.OneToOne",
                        ),
                        "imports" to listOf(
                            "com.acme.demo.domain.identity.user.UserProfile",
                            "com.acme.demo.domain.identity.user.CoverProfile",
                        ),
                        "scalarFields" to entityScalarFields(
                            mapOf(
                                "name" to "id",
                                "type" to "Long",
                                "nullable" to false,
                                "propertyNullable" to false,
                                "columnName" to "id",
                                "isId" to true,
                                "converterTypeRef" to null,
                            )
                        ),
                        "fields" to listOf(
                            mapOf("name" to "id", "type" to "Long", "nullable" to false)
                        ),
                        "relationFields" to listOf(
                            mapOf(
                                "name" to "author",
                                "targetType" to "UserProfile",
                                "targetTypeRef" to "UserProfile",
                                "targetPackageName" to "com.acme.demo.domain.identity.user",
                                "relationType" to "MANY_TO_ONE",
                                "fetchType" to "LAZY",
                                "joinColumn" to "author_id",
                                "nullable" to false,
                                "joinColumnNullable" to false,
                            ),
                            mapOf(
                                "name" to "coverProfile",
                                "targetType" to "CoverProfile",
                                "targetTypeRef" to "CoverProfile",
                                "targetPackageName" to "com.acme.demo.domain.identity.user",
                                "relationType" to "ONE_TO_ONE",
                                "fetchType" to "LAZY",
                                "joinColumn" to "cover_profile_id",
                                "nullable" to true,
                                "joinColumnNullable" to true,
                            )
                        ),
                    ),
                    conflictPolicy = ConflictPolicy.SKIP,
                )
            ),
            config = ProjectConfig(
                basePackage = "com.acme.demo",
                layout = ProjectLayout.MULTI_MODULE,
                modules = emptyMap(),
                sources = emptyMap(),
                generators = emptyMap(),
                templates = TemplateConfig(
                    preset = "ddd-default",
                    overrideDirs = listOf(overrideDir.toString()),
                    conflictPolicy = ConflictPolicy.SKIP
                )
            )
        )

        val content = rendered.single().content

        assertTrue(content.contains("import jakarta.persistence.ManyToOne"))
        assertTrue(content.contains("import jakarta.persistence.OneToOne"))
        assertFalse(content.contains("import jakarta.persistence.CascadeType"))
        assertTrue(content.contains("@JoinColumn(name = \"author_id\", nullable = false)"))
        assertTrue(content.contains("@JoinColumn(name = \"cover_profile_id\", nullable = true)"))
        assertFalse(content.contains("@OneToMany("))
    }

    @Test
    fun `aggregate entity preset renders one-to-many controls from planner flags`() {
        val overrideDir = Files.createTempDirectory("cap4k-override-empty-aggregate-collection-controls")
        val renderer = PebbleArtifactRenderer(
            templateResolver = PresetTemplateResolver(
                preset = "ddd-default",
                overrideDirs = listOf(overrideDir.toString())
            )
        )

        val rendered = renderer.render(
            planItems = listOf(
                ArtifactPlanItem(
                    generatorId = "aggregate",
                    moduleRole = "domain",
                    templateId = "aggregate/entity.kt.peb",
                    outputPath = "demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/VideoPost.kt",
                    context = mapOf(
                        "packageName" to "com.acme.demo.domain.aggregates.video_post",
                        "typeName" to "VideoPost",
                        "comment" to "video post",
                        "aggregateName" to "VideoPost",
                        "aggregateRoot" to true,
                        "entityJpa" to mapOf(
                            "entityEnabled" to true,
                            "tableName" to "video_post",
                        ),
                        "jpaImports" to listOf(
                            "jakarta.persistence.FetchType",
                            "jakarta.persistence.JoinColumn",
                            "jakarta.persistence.OneToMany",
                        ),
                        "imports" to listOf(
                            "com.acme.demo.domain.aggregates.video_post.item.VideoPostItem",
                        ),
                        "scalarFields" to entityScalarFields(
                            mapOf(
                                "name" to "id",
                                "type" to "Long",
                                "nullable" to false,
                                "propertyNullable" to false,
                                "columnName" to "id",
                                "isId" to true,
                                "converterTypeRef" to null,
                            )
                        ),
                        "fields" to listOf(
                            mapOf("name" to "id", "type" to "Long", "nullable" to false)
                        ),
                        "relationFields" to listOf(
                            mapOf(
                                "name" to "items",
                                "targetType" to "VideoPostItem",
                                "targetTypeRef" to "VideoPostItem",
                                "targetPackageName" to "com.acme.demo.domain.aggregates.video_post.item",
                                "relationType" to "ONE_TO_MANY",
                                "fetchType" to "LAZY",
                                "joinColumn" to "video_post_id",
                                "cascadeTypes" to emptyList<String>(),
                                "orphanRemoval" to false,
                                "joinColumnNullable" to false,
                            )
                        ),
                    ),
                    conflictPolicy = ConflictPolicy.SKIP,
                )
            ),
            config = ProjectConfig(
                basePackage = "com.acme.demo",
                layout = ProjectLayout.MULTI_MODULE,
                modules = emptyMap(),
                sources = emptyMap(),
                generators = emptyMap(),
                templates = TemplateConfig(
                    preset = "ddd-default",
                    overrideDirs = listOf(overrideDir.toString()),
                    conflictPolicy = ConflictPolicy.SKIP
                )
            )
        )

        val content = rendered.single().content

        assertFalse(content.contains("import jakarta.persistence.CascadeType"))
        assertTrue(content.contains("@OneToMany(fetch = FetchType.LAZY, orphanRemoval = false)"))
        assertFalse(content.contains("cascade = [CascadeType."))
        assertTrue(content.contains("@JoinColumn(name = \"video_post_id\", nullable = false)"))
    }

    @Test
    fun `aggregate entity preset does not double map relation join columns as scalar fields`() {
        val overrideDir = Files.createTempDirectory("cap4k-override-empty-aggregate-relation-scalar-boundary")
        val renderer = PebbleArtifactRenderer(
            templateResolver = PresetTemplateResolver(
                preset = "ddd-default",
                overrideDirs = listOf(overrideDir.toString())
            )
        )

        val rendered = renderer.render(
            planItems = listOf(
                ArtifactPlanItem(
                    generatorId = "aggregate",
                    moduleRole = "domain",
                    templateId = "aggregate/entity.kt.peb",
                    outputPath = "demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/VideoPost.kt",
                    context = mapOf(
                        "packageName" to "com.acme.demo.domain.aggregates.video_post",
                        "typeName" to "VideoPost",
                        "comment" to "video post",
                        "aggregateName" to "VideoPost",
                        "aggregateRoot" to true,
                        "entityJpa" to mapOf(
                            "entityEnabled" to true,
                            "tableName" to "video_post",
                        ),
                        "hasConverterFields" to false,
                        "jpaImports" to listOf(
                            "jakarta.persistence.FetchType",
                            "jakarta.persistence.JoinColumn",
                            "jakarta.persistence.ManyToOne",
                        ),
                        "imports" to listOf("com.acme.demo.domain.identity.user.UserProfile"),
                        "scalarFields" to entityScalarFields(
                            mapOf(
                                "name" to "id",
                                "type" to "Long",
                                "nullable" to false,
                                "propertyNullable" to false,
                                "columnName" to "id",
                                "isId" to true,
                                "converterTypeRef" to null,
                            ),
                            mapOf(
                                "name" to "title",
                                "type" to "String",
                                "nullable" to false,
                                "propertyNullable" to false,
                                "columnName" to "title",
                                "isId" to false,
                                "converterTypeRef" to null,
                            ),
                        ),
                        "fields" to listOf(
                            mapOf("name" to "id", "type" to "Long", "nullable" to false),
                            mapOf("name" to "title", "type" to "String", "nullable" to false),
                        ),
                        "relationFields" to listOf(
                            mapOf(
                                "name" to "author",
                                "targetType" to "UserProfile",
                                "targetTypeRef" to "UserProfile",
                                "targetPackageName" to "com.acme.demo.domain.identity.user",
                                "relationType" to "MANY_TO_ONE",
                                "fetchType" to "LAZY",
                                "joinColumn" to "author_id",
                                "nullable" to false,
                                "joinColumnNullable" to false,
                            ),
                        ),
                    ),
                    conflictPolicy = ConflictPolicy.SKIP,
                )
            ),
            config = ProjectConfig(
                basePackage = "com.acme.demo",
                layout = ProjectLayout.MULTI_MODULE,
                modules = emptyMap(),
                sources = emptyMap(),
                generators = emptyMap(),
                templates = TemplateConfig(
                    preset = "ddd-default",
                    overrideDirs = listOf(overrideDir.toString()),
                    conflictPolicy = ConflictPolicy.SKIP
                )
            )
        )

        val content = rendered.single().content

        assertTrue(content.contains("@JoinColumn(name = \"author_id\", nullable = false)"))
        assertTrue(content.contains("lateinit var author: UserProfile"))
        assertFalse(content.contains("@Column(name = \"author_id\")"))
        assertFalse(content.contains("val author_id:"))
    }

    @Test
    fun `aggregate entity preset does not double map join column when scalar field name differs from column name`() {
        val overrideDir = Files.createTempDirectory("cap4k-override-empty-aggregate-relation-column-boundary")
        val renderer = PebbleArtifactRenderer(
            templateResolver = PresetTemplateResolver(
                preset = "ddd-default",
                overrideDirs = listOf(overrideDir.toString())
            )
        )

        val rendered = renderer.render(
            planItems = listOf(
                ArtifactPlanItem(
                    generatorId = "aggregate",
                    moduleRole = "domain",
                    templateId = "aggregate/entity.kt.peb",
                    outputPath = "demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/VideoPost.kt",
                    context = mapOf(
                        "packageName" to "com.acme.demo.domain.aggregates.video_post",
                        "typeName" to "VideoPost",
                        "comment" to "video post",
                        "aggregateName" to "VideoPost",
                        "aggregateRoot" to true,
                        "entityJpa" to mapOf(
                            "entityEnabled" to true,
                            "tableName" to "video_post",
                        ),
                        "hasConverterFields" to false,
                        "jpaImports" to listOf(
                            "jakarta.persistence.FetchType",
                            "jakarta.persistence.JoinColumn",
                            "jakarta.persistence.ManyToOne",
                        ),
                        "imports" to listOf("com.acme.demo.domain.identity.user.UserProfile"),
                        "scalarFields" to entityScalarFields(
                            mapOf(
                                "name" to "id",
                                "type" to "Long",
                                "nullable" to false,
                                "propertyNullable" to false,
                                "columnName" to "id",
                                "isId" to true,
                                "converterTypeRef" to null,
                            ),
                            mapOf(
                                "name" to "title",
                                "type" to "String",
                                "nullable" to false,
                                "propertyNullable" to false,
                                "columnName" to "title",
                                "isId" to false,
                                "converterTypeRef" to null,
                            ),
                        ),
                        "fields" to listOf(
                            mapOf("name" to "id", "type" to "Long", "nullable" to false),
                            mapOf("name" to "title", "type" to "String", "nullable" to false),
                        ),
                        "relationFields" to listOf(
                            mapOf(
                                "name" to "author",
                                "targetType" to "UserProfile",
                                "targetTypeRef" to "UserProfile",
                                "targetPackageName" to "com.acme.demo.domain.identity.user",
                                "relationType" to "MANY_TO_ONE",
                                "fetchType" to "LAZY",
                                "joinColumn" to "author_id",
                                "nullable" to false,
                                "joinColumnNullable" to false,
                            ),
                        ),
                    ),
                    conflictPolicy = ConflictPolicy.SKIP,
                )
            ),
            config = ProjectConfig(
                basePackage = "com.acme.demo",
                layout = ProjectLayout.MULTI_MODULE,
                modules = emptyMap(),
                sources = emptyMap(),
                generators = emptyMap(),
                templates = TemplateConfig(
                    preset = "ddd-default",
                    overrideDirs = listOf(overrideDir.toString()),
                    conflictPolicy = ConflictPolicy.SKIP
                )
            )
        )

        val content = rendered.single().content

        assertTrue(content.contains("@JoinColumn(name = \"author_id\", nullable = false)"))
        assertTrue(content.contains("lateinit var author: UserProfile"))
        assertFalse(content.contains("@Column(name = \"author_id\")"))
        assertFalse(content.contains("val authorId:"))
    }

    @Test
    fun `aggregate entity preset does not render unsupported relation types`() {
        val overrideDir = Files.createTempDirectory("cap4k-override-empty-aggregate-unsupported-relation")
        val renderer = PebbleArtifactRenderer(
            templateResolver = PresetTemplateResolver(
                preset = "ddd-default",
                overrideDirs = listOf(overrideDir.toString())
            )
        )

        val rendered = renderer.render(
            planItems = listOf(
                ArtifactPlanItem(
                    generatorId = "aggregate",
                    moduleRole = "domain",
                    templateId = "aggregate/entity.kt.peb",
                    outputPath = "demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/VideoPost.kt",
                    context = mapOf(
                        "packageName" to "com.acme.demo.domain.aggregates.video_post",
                        "typeName" to "VideoPost",
                        "comment" to "video post",
                        "aggregateName" to "VideoPost",
                        "aggregateRoot" to true,
                        "tableName" to "video_post",
                        "jpaImports" to emptyList<String>(),
                        "imports" to listOf("com.acme.demo.domain.identity.user.UserProfile"),
                        "scalarFields" to entityScalarFields(
                            mapOf("name" to "id", "type" to "Long", "nullable" to false, "propertyNullable" to false)
                        ),
                        "fields" to listOf(
                            mapOf("name" to "id", "type" to "Long", "nullable" to false)
                        ),
                        "relationFields" to listOf(
                            mapOf(
                                "name" to "authors",
                                "targetType" to "UserProfile",
                                "targetTypeRef" to "UserProfile",
                                "targetPackageName" to "com.acme.demo.domain.identity.user",
                                "relationType" to "MANY_TO_MANY",
                                "fetchType" to "LAZY",
                                "joinColumn" to "author_id",
                            )
                        ),
                    ),
                    conflictPolicy = ConflictPolicy.SKIP,
                )
            ),
            config = ProjectConfig(
                basePackage = "com.acme.demo",
                layout = ProjectLayout.MULTI_MODULE,
                modules = emptyMap(),
                sources = emptyMap(),
                generators = emptyMap(),
                templates = TemplateConfig(
                    preset = "ddd-default",
                    overrideDirs = listOf(overrideDir.toString()),
                    conflictPolicy = ConflictPolicy.SKIP
                )
            )
        )

        val content = rendered.single().content
        assertFalse(content.contains("val authors:"))
        assertFalse(content.contains("MANY_TO_MANY"))
    }

    @Test
    fun `aggregate entity preset renders bounded Jakarta baseline annotations`() {
        val overrideDir = Files.createTempDirectory("cap4k-override-empty-aggregate-jakarta-baseline")
        val renderer = PebbleArtifactRenderer(
            templateResolver = PresetTemplateResolver(
                preset = "ddd-default",
                overrideDirs = listOf(overrideDir.toString())
            )
        )

        val rendered = renderer.render(
            planItems = listOf(
                ArtifactPlanItem(
                    generatorId = "aggregate",
                    moduleRole = "domain",
                    templateId = "aggregate/entity.kt.peb",
                    outputPath = "demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/VideoPost.kt",
                    context = mapOf(
                        "packageName" to "com.acme.demo.domain.aggregates.video_post",
                        "typeName" to "VideoPost",
                        "comment" to "video post",
                        "aggregateName" to "VideoPost",
                        "aggregateRoot" to true,
                        "entityJpa" to mapOf(
                            "entityEnabled" to true,
                            "tableName" to "video_post",
                        ),
                        "hasConverterFields" to true,
                        "scalarFields" to entityScalarFields(
                            mapOf(
                                "name" to "id",
                                "type" to "Long",
                                "nullable" to false,
                                "propertyNullable" to false,
                                "columnName" to "id",
                                "isId" to true,
                                "converterTypeRef" to null,
                                "converterClassRef" to null,
                            ),
                            mapOf(
                                "name" to "status",
                                "type" to "com.acme.demo.domain.shared.enums.Status",
                                "nullable" to false,
                                "propertyNullable" to false,
                                "columnName" to "status",
                                "isId" to false,
                                "converterTypeRef" to "com.acme.demo.domain.shared.enums.Status",
                                "converterClassRef" to "com.acme.demo.domain.shared.enums.Status.Converter",
                            ),
                        ),
                        "relationFields" to emptyList<Map<String, Any?>>(),
                        "imports" to emptyList<String>(),
                        "jpaImports" to emptyList<String>(),
                    ),
                    conflictPolicy = ConflictPolicy.SKIP,
                )
            ),
            config = ProjectConfig(
                basePackage = "com.acme.demo",
                layout = ProjectLayout.MULTI_MODULE,
                modules = emptyMap(),
                sources = emptyMap(),
                generators = emptyMap(),
                templates = TemplateConfig(
                    preset = "ddd-default",
                    overrideDirs = listOf(overrideDir.toString()),
                    conflictPolicy = ConflictPolicy.SKIP
                )
            )
        )

        val content = rendered.single().content

        assertTrue(content.contains("@Entity"))
        assertTrue(content.contains("@Table(name = \"video_post\")"))
        assertTrue(content.contains("@Id"))
        assertTrue(content.contains("@Column(name = \"id\")"))
        assertTrue(content.contains("@Column(name = \"status\")"))
        assertTrue(content.contains("import jakarta.persistence.Convert"))
        assertTrue(content.contains("import com.acme.demo.domain.shared.enums.Status"))
        assertTrue(content.contains("@Convert(converter = Status.Converter::class)"))
        assertFalse(content.contains("@Convert(converter = com.acme.demo.domain.shared.enums.Status.Converter::class)"))
        assertTrue(content.contains("class VideoPost internal constructor("))
        assertFalse(content.contains("data class VideoPost("))
        assertFalse(content.contains("@GeneratedValue"))
        assertFalse(content.contains("@Version"))
        assertFalse(content.contains("@DynamicInsert"))
        assertFalse(content.contains("@DynamicUpdate"))
        assertFalse(content.contains("@SQLDelete"))
        assertFalse(content.contains("@Where"))
        assertFalse(content.contains("@Generic" + "Generator"))
    }

    @Test
    fun `aggregate entity template renders explicit persistence field behavior`() {
        val overrideDir = Files.createTempDirectory("cap4k-override-empty-aggregate-persistence-field-behavior")
        val renderer = PebbleArtifactRenderer(
            templateResolver = PresetTemplateResolver(
                preset = "ddd-default",
                overrideDirs = listOf(overrideDir.toString())
            )
        )

        val rendered = renderer.render(
            planItems = listOf(
                ArtifactPlanItem(
                    generatorId = "aggregate",
                    moduleRole = "domain",
                    templateId = "aggregate/entity.kt.peb",
                    outputPath = "demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/VideoPost.kt",
                    context = mapOf(
                        "packageName" to "com.acme.demo.domain.aggregates.video_post",
                        "typeName" to "VideoPost",
                        "comment" to "video post",
                        "aggregateName" to "VideoPost",
                        "aggregateRoot" to true,
                        "entityJpa" to mapOf(
                            "entityEnabled" to true,
                            "tableName" to "video_post",
                        ),
                        "hasConverterFields" to false,
                        "hasGeneratedValueFields" to true,
                        "hasVersionFields" to true,
                        "scalarFields" to entityScalarFields(
                            mapOf(
                                "fieldName" to "id",
                                "fieldType" to "Long",
                                "name" to "id",
                                "type" to "Long",
                                "propertyNullable" to true,
                                "columnName" to "id",
                                "isId" to true,
                                "generatedValueStrategy" to "IDENTITY",
                            ),
                            mapOf(
                                "fieldName" to "version",
                                "fieldType" to "Long",
                                "name" to "version",
                                "type" to "Long",
                                "propertyNullable" to true,
                                "columnName" to "version",
                                "isVersion" to true,
                            ),
                            mapOf(
                                "fieldName" to "title",
                                "fieldType" to "String",
                                "name" to "title",
                                "type" to "String",
                                "propertyNullable" to false,
                                "columnName" to "title",
                                "generatedValueStrategy" to "IDENTITY",
                            ),
                            mapOf(
                                "fieldName" to "created_by",
                                "fieldType" to "String",
                                "name" to "created_by",
                                "type" to "String",
                                "propertyNullable" to false,
                                "columnName" to "created_by",
                                "insertable" to false,
                                "updatable" to true,
                            ),
                            mapOf(
                                "fieldName" to "updated_by",
                                "fieldType" to "String",
                                "name" to "updated_by",
                                "type" to "String",
                                "propertyNullable" to false,
                                "columnName" to "updated_by",
                                "insertable" to true,
                                "updatable" to false,
                            ),
                            mapOf(
                                "fieldName" to "computed_label",
                                "fieldType" to "String",
                                "name" to "computed_label",
                                "type" to "String",
                                "propertyNullable" to true,
                                "propertyInitializer" to "null",
                                "constructorIncluded" to false,
                                "columnName" to "computed_label",
                                "insertable" to false,
                                "updatable" to false,
                                "generatedEvents" to listOf("INSERT", "UPDATE"),
                            ),
                        ),
                        "fields" to listOf(
                            mapOf("fieldName" to "id", "fieldType" to "Long"),
                            mapOf("fieldName" to "version", "fieldType" to "Long"),
                            mapOf("fieldName" to "title", "fieldType" to "String"),
                            mapOf("fieldName" to "created_by", "fieldType" to "String"),
                            mapOf("fieldName" to "updated_by", "fieldType" to "String"),
                            mapOf("fieldName" to "computed_label", "fieldType" to "String"),
                        ),
                        "relationFields" to emptyList<Map<String, Any?>>(),
                        "imports" to emptyList<String>(),
                        "jpaImports" to emptyList<String>(),
                    ),
                    conflictPolicy = ConflictPolicy.SKIP,
                )
            ),
            config = ProjectConfig(
                basePackage = "com.acme.demo",
                layout = ProjectLayout.MULTI_MODULE,
                modules = emptyMap(),
                sources = emptyMap(),
                generators = emptyMap(),
                templates = TemplateConfig(
                    preset = "ddd-default",
                    overrideDirs = listOf(overrideDir.toString()),
                    conflictPolicy = ConflictPolicy.SKIP
                )
            )
        )

        val content = rendered.single().content

        assertFalse(content.contains("\n\n\n"), content)
        assertFalse(Regex("(?m)^\\s+$").containsMatchIn(content), content)
        assertTrue(content.startsWith("package com.acme.demo.domain.aggregates.video_post\n\nimport "))
        assertFalse(content.contains(legacyAggregateCall), content)
        assertFalse(content.contains(legacyAggregateAnnotationFq), content)
        assertTrue(content.contains("\n@Entity"), content)
        assertTrue(content.contains("import jakarta.persistence.GeneratedValue"))
        assertTrue(content.contains("import jakarta.persistence.GenerationType"))
        assertFalse(content.contains("import org.hibernate.annotations.Generic" + "Generator"))
        assertTrue(content.contains("import jakarta.persistence.Version"))
        assertTrue(content.contains("@GeneratedValue(strategy = GenerationType.IDENTITY)"))
        assertFalse(content.contains("@GeneratedValue(" + "generator ="))
        assertFalse(content.contains("@Generic" + "Generator("))
        assertTrue(content.contains("@Version"))
        assertTrue(content.contains("@Column(name = \"version\")"))
        assertTrue(content.contains("@Column(name = \"title\")"))
        assertTrue(content.contains("@Column(name = \"created_by\", insertable = false, updatable = true)"))
        assertTrue(content.contains("@Column(name = \"updated_by\", insertable = true, updatable = false)"))
        assertTrue(
            content.contains(
                "@org.hibernate.annotations.Generated(event = [org.hibernate.generator.EventType.INSERT, " +
                    "org.hibernate.generator.EventType.UPDATE])\n" +
                    "    @Column(name = \"computed_label\", insertable = false, updatable = false)"
            ),
            content,
        )
        assertFalse(content.contains("@GeneratedValue(strategy = GenerationType.IDENTITY)\n    @Column(name = \"title\")"))
    }

    @Test
    fun `aggregate entity template omits application side id annotation and sentinel default`() {
        val overrideDir = Files.createTempDirectory("cap4k-override-empty-aggregate-application-side-id")
        val renderer = PebbleArtifactRenderer(
            templateResolver = PresetTemplateResolver(
                preset = "ddd-default",
                overrideDirs = listOf(overrideDir.toString())
            )
        )

        val rendered = renderer.render(
            planItems = listOf(
                ArtifactPlanItem(
                    generatorId = "aggregate",
                    moduleRole = "domain",
                    templateId = "aggregate/entity.kt.peb",
                    outputPath = "demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/VideoPost.kt",
                    context = mapOf(
                        "packageName" to "com.acme.demo.domain.aggregates.video_post",
                        "typeName" to "VideoPost",
                        "comment" to "video post",
                        "aggregateName" to "VideoPost",
                        "aggregateRoot" to true,
                        "entityJpa" to mapOf(
                            "entityEnabled" to true,
                            "tableName" to "video_post",
                        ),
                        "hasConverterFields" to false,
                        "hasGeneratedValueFields" to false,
                        "hasEmbeddedIdFields" to false,
                        "hasVersionFields" to false,
                        "scalarFields" to entityScalarFields(
                            mapOf(
                                "fieldName" to "id",
                                "fieldType" to "UUID",
                                "name" to "id",
                                "type" to "UUID",
                                "propertyNullable" to false,
                                "defaultValue" to null,
                                "columnName" to "id",
                                "isId" to true,
                                "insertable" to true,
                                "updatable" to false,
                            ),
                            mapOf(
                                "fieldName" to "title",
                                "fieldType" to "String",
                                "name" to "title",
                                "type" to "String",
                                "propertyNullable" to false,
                                "columnName" to "title",
                            ),
                        ),
                        "fields" to listOf(
                            mapOf("fieldName" to "id", "fieldType" to "UUID"),
                            mapOf("fieldName" to "title", "fieldType" to "String"),
                        ),
                        "relationFields" to emptyList<Map<String, Any?>>(),
                        "imports" to listOf("java.util.UUID"),
                        "jpaImports" to emptyList<String>(),
                    ),
                    conflictPolicy = ConflictPolicy.SKIP,
                )
            ),
            config = ProjectConfig(
                basePackage = "com.acme.demo",
                layout = ProjectLayout.MULTI_MODULE,
                modules = emptyMap(),
                sources = emptyMap(),
                generators = emptyMap(),
                templates = TemplateConfig(
                    preset = "ddd-default",
                    overrideDirs = listOf(overrideDir.toString()),
                    conflictPolicy = ConflictPolicy.SKIP
                )
            )
        )

        val content = rendered.single().content

        assertTrue(content.contains("import java.util.UUID"))
        assertFalse(content.contains("import jakarta.persistence.GeneratedValue"))
        assertFalse(content.contains("import org.hibernate.annotations.Generic" + "Generator"))
        assertFalse(content.contains("import jakarta.persistence.GenerationType"))
        assertFalse(content.contains("@GeneratedValue(" + "generator ="))
        assertFalse(content.contains("@Generic" + "Generator("))
        assertFalse(content.contains("@GeneratedValue(strategy = GenerationType.IDENTITY)"))
        assertFalse(content.contains("UUID(" + "0L, 0L)"))
        assertTrue(content.contains("id: UUID"))
        assertTrue(content.contains("@Id"))
        assertTrue(content.contains("@Column(name = \"id\", insertable = true, updatable = false)"))
    }

    @Test
    fun `aggregate entity template keeps bounded imports and plain column when persistence controls are absent`() {
        val overrideDir = Files.createTempDirectory("cap4k-override-empty-aggregate-persistence-import-gating")
        val renderer = PebbleArtifactRenderer(
            templateResolver = PresetTemplateResolver(
                preset = "ddd-default",
                overrideDirs = listOf(overrideDir.toString())
            )
        )

        val rendered = renderer.render(
            planItems = listOf(
                ArtifactPlanItem(
                    generatorId = "aggregate",
                    moduleRole = "domain",
                    templateId = "aggregate/entity.kt.peb",
                    outputPath = "demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/VideoPost.kt",
                    context = mapOf(
                        "packageName" to "com.acme.demo.domain.aggregates.video_post",
                        "typeName" to "VideoPost",
                        "comment" to "video post",
                        "aggregateName" to "VideoPost",
                        "aggregateRoot" to true,
                        "entityJpa" to mapOf(
                            "entityEnabled" to true,
                            "tableName" to "video_post",
                        ),
                        "hasConverterFields" to false,
                        "hasGeneratedValueFields" to false,
                        "hasVersionFields" to false,
                        "scalarFields" to entityScalarFields(
                            mapOf(
                                "fieldName" to "id",
                                "fieldType" to "Long",
                                "name" to "id",
                                "type" to "Long",
                                "propertyNullable" to false,
                                "columnName" to "id",
                                "isId" to true,
                            ),
                            mapOf(
                                "fieldName" to "title",
                                "fieldType" to "String",
                                "name" to "title",
                                "type" to "String",
                                "propertyNullable" to false,
                                "columnName" to "title",
                            ),
                        ),
                        "fields" to listOf(
                            mapOf("fieldName" to "id", "fieldType" to "Long"),
                            mapOf("fieldName" to "title", "fieldType" to "String"),
                        ),
                        "relationFields" to emptyList<Map<String, Any?>>(),
                        "imports" to emptyList<String>(),
                        "jpaImports" to emptyList<String>(),
                    ),
                    conflictPolicy = ConflictPolicy.SKIP,
                )
            ),
            config = ProjectConfig(
                basePackage = "com.acme.demo",
                layout = ProjectLayout.MULTI_MODULE,
                modules = emptyMap(),
                sources = emptyMap(),
                generators = emptyMap(),
                templates = TemplateConfig(
                    preset = "ddd-default",
                    overrideDirs = listOf(overrideDir.toString()),
                    conflictPolicy = ConflictPolicy.SKIP
                )
            )
        )

        val content = rendered.single().content

        assertFalse(content.contains("import jakarta.persistence.GeneratedValue"))
        assertFalse(content.contains("import jakarta.persistence.GenerationType"))
        assertFalse(content.contains("import jakarta.persistence.Version"))
        assertFalse(content.contains("import org.hibernate.annotations.DynamicInsert"))
        assertFalse(content.contains("import org.hibernate.annotations.DynamicUpdate"))
        assertFalse(content.contains("import org.hibernate.annotations.SQLDelete"))
        assertFalse(content.contains("import org.hibernate.annotations.Where"))
        assertFalse(content.contains("@GeneratedValue"))
        assertFalse(content.contains("@Version"))
        assertTrue(content.contains("@Column(name = \"title\")"))
    }

    @Test
    fun `aggregate entity template renders structured soft delete context`() {
        val overrideDir = Files.createTempDirectory("cap4k-override-empty-aggregate-provider-persistence")
        val renderer = PebbleArtifactRenderer(
            templateResolver = PresetTemplateResolver(
                preset = "ddd-default",
                overrideDirs = listOf(overrideDir.toString())
            )
        )

        val rendered = renderer.render(
            planItems = listOf(
                ArtifactPlanItem(
                    generatorId = "aggregate",
                    moduleRole = "domain",
                    templateId = "aggregate/entity.kt.peb",
                    outputPath = "demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/VideoPost.kt",
                    context = mapOf(
                        "packageName" to "com.acme.demo.domain.aggregates.video_post",
                        "typeName" to "VideoPost",
                        "comment" to "video post",
                        "aggregateName" to "VideoPost",
                        "aggregateRoot" to true,
                        "entityJpa" to mapOf(
                            "entityEnabled" to true,
                            "tableName" to "video_post",
                        ),
                        "hasConverterFields" to false,
                        "hasGeneratedValueFields" to false,
                        "hasVersionFields" to false,
                        "softDelete" to mapOf(
                            "enabled" to true,
                            "columnName" to "deleted",
                            "storageKind" to "INTEGRAL",
                            "activeSentinel" to "ZERO",
                            "tombstoneStrategy" to "SELF_ID",
                        ),
                        "softDeleteSql" to "update \"video_post\" set \"deleted\" = \"id\" where \"id\" = ? and \"version\" = ?",
                        "softDeleteWhereClause" to "\"deleted\" = 0",
                        "softDeleteSqlKotlinStringLiteral" to "\"update \\\"video_post\\\" set \\\"deleted\\\" = \\\"id\\\" where \\\"id\\\" = ? and \\\"version\\\" = ?\"",
                        "softDeleteWhereClauseKotlinStringLiteral" to "\"\\\"deleted\\\" = 0\"",
                        "scalarFields" to emptyList<Map<String, Any?>>(),
                        "fields" to emptyList<Map<String, Any?>>(),
                        "relationFields" to emptyList<Map<String, Any?>>(),
                        "imports" to emptyList<String>(),
                        "jpaImports" to emptyList<String>(),
                    ),
                    conflictPolicy = ConflictPolicy.SKIP,
                )
            ),
            config = ProjectConfig(
                basePackage = "com.acme.demo",
                layout = ProjectLayout.MULTI_MODULE,
                modules = emptyMap(),
                sources = emptyMap(),
                generators = emptyMap(),
                templates = TemplateConfig(
                    preset = "ddd-default",
                    overrideDirs = listOf(overrideDir.toString()),
                    conflictPolicy = ConflictPolicy.SKIP
                )
            )
        )

        val content = rendered.single().content

        assertTrue(content.contains("import org.hibernate.annotations.SQLDelete"))
        assertTrue(content.contains("import org.hibernate.annotations.Where"))
        assertTrue(content.contains("""@SQLDelete(sql = "update \"video_post\" set \"deleted\" = \"id\" where \"id\" = ? and \"version\" = ?")"""))
        assertTrue(content.contains("""@Where(clause = "\"deleted\" = 0")"""))
        assertFalse(content.contains("import org.hibernate.annotations.DynamicInsert"))
        assertFalse(content.contains("import org.hibernate.annotations.DynamicUpdate"))
        assertFalse(content.contains("@DynamicInsert"))
        assertFalse(content.contains("@DynamicUpdate"))
    }

    @Test
    fun `aggregate entity template renders exact quoted jpa identifiers for supported dialects`() {
        data class Case(
            val name: String,
            val quote: (String) -> String,
        )

        val cases = listOf(
            Case("h2") { value -> "\"$value\"" },
            Case("postgresql") { value -> "\"$value\"" },
            Case("mysql") { value -> "`$value`" },
        )

        cases.forEach { case ->
            fun literal(value: String): String = case.quote(value).toTestKotlinStringLiteral()

            val content = renderTemplate(
                templateId = "aggregate/entity.kt.peb",
                outputPath =
                    "demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/mixed_case/MixedCase.kt",
                context = mapOf(
                    "packageName" to "com.acme.demo.domain.aggregates.mixed_case",
                    "typeName" to "MixedCase",
                    "entityJpa" to mapOf(
                        "entityEnabled" to true,
                        "tableName" to "MixedCase",
                        "tableNameKotlinStringLiteral" to literal("MixedCase"),
                    ),
                    "hasConverterFields" to false,
                    "hasGeneratedValueFields" to false,
                    "hasVersionFields" to false,
                    "hasStrongIdFields" to false,
                    "hasEmbeddedIdFields" to false,
                    "hasEmbeddedStrongIdFields" to false,
                    "softDelete" to mapOf("enabled" to true),
                    "softDeleteSqlKotlinStringLiteral" to "update quoted".toTestKotlinStringLiteral(),
                    "softDeleteWhereClauseKotlinStringLiteral" to "quoted = 0".toTestKotlinStringLiteral(),
                    "constructorFields" to emptyList<Map<String, Any?>>(),
                    "scalarFields" to entityScalarFields(
                        mapOf(
                            "name" to "id",
                            "type" to "Long",
                            "propertyInitializer" to "0L",
                            "nullable" to false,
                            "propertyNullable" to false,
                            "columnName" to "PhysicalId",
                            "columnNameKotlinStringLiteral" to literal("PhysicalId"),
                            "isId" to true,
                        ),
                        mapOf(
                            "name" to "deleted",
                            "type" to "Long",
                            "propertyInitializer" to "0L",
                            "nullable" to false,
                            "propertyNullable" to false,
                            "columnName" to "DeletedMarker",
                            "columnNameKotlinStringLiteral" to literal("DeletedMarker"),
                        ),
                    ),
                    "relationFields" to listOf(
                        mapOf(
                            "relationType" to "MANY_TO_ONE",
                            "name" to "author",
                            "targetTypeRef" to "Author",
                            "fetchType" to "LAZY",
                            "joinColumn" to "AuthorId",
                            "joinColumnKotlinStringLiteral" to literal("AuthorId"),
                            "nullable" to true,
                        ),
                        mapOf(
                            "relationType" to "ONE_TO_ONE",
                            "name" to "profile",
                            "targetTypeRef" to "Profile",
                            "fetchType" to "LAZY",
                            "joinColumn" to "ProfileId",
                            "joinColumnKotlinStringLiteral" to literal("ProfileId"),
                            "nullable" to true,
                        ),
                        mapOf(
                            "relationType" to "ONE_TO_MANY",
                            "name" to "items",
                            "targetTypeRef" to "MixedCaseItem",
                            "fetchType" to "LAZY",
                            "cascadeTypes" to emptyList<String>(),
                            "orphanRemoval" to false,
                            "joinColumn" to "OwnerId",
                            "joinColumnKotlinStringLiteral" to literal("OwnerId"),
                            "joinColumnNullable" to false,
                            "owned" to false,
                        ),
                    ),
                    "imports" to emptyList<String>(),
                    "jpaImports" to emptyList<String>(),
                ),
            )

            assertTrue(
                content.contains("@Table(name = ${literal("MixedCase")})"),
                case.name,
            )
            assertTrue(
                content.contains("@Column(name = ${literal("PhysicalId")})"),
                case.name,
            )
            assertTrue(
                content.contains("@Column(name = ${literal("DeletedMarker")})"),
                case.name,
            )
            listOf("AuthorId", "ProfileId", "OwnerId").forEach { joinColumn ->
                assertTrue(
                    content.contains("@JoinColumn(name = ${literal(joinColumn)}"),
                    "${case.name}: $joinColumn",
                )
            }
        }
    }

    @Test
    fun `falls back to preset aggregate enum template`() {
        val overrideDir = Files.createTempDirectory("cap4k-override-empty-aggregate-enum")
        val renderer = PebbleArtifactRenderer(
            templateResolver = PresetTemplateResolver(
                preset = "ddd-default",
                overrideDirs = listOf(overrideDir.toString())
            )
        )

        val rendered = renderer.render(
            planItems = listOf(
                ArtifactPlanItem(
                    generatorId = "aggregate",
                    moduleRole = "domain",
                    templateId = "aggregate/enum.kt.peb",
                    outputPath = "demo-domain/src/main/kotlin/com/acme/demo/domain/shared/enums/Status.kt",
                    context = mapOf(
                        "packageName" to "com.acme.demo.domain.shared.enums",
                        "typeName" to "Status",
                        "aggregateName" to null,
                        "items" to listOf(
                            mapOf("value" to 0, "name" to "DRAFT", "description" to "Draft"),
                            mapOf("value" to 1, "name" to "PUBLISHED", "description" to "Published"),
                        ),
                    ),
                    conflictPolicy = ConflictPolicy.SKIP,
                ),
            ),
            config = ProjectConfig(
                basePackage = "com.acme.demo",
                layout = ProjectLayout.MULTI_MODULE,
                modules = emptyMap(),
                sources = emptyMap(),
                generators = emptyMap(),
                templates = TemplateConfig(
                    preset = "ddd-default",
                    overrideDirs = listOf(overrideDir.toString()),
                    conflictPolicy = ConflictPolicy.SKIP
                )
            )
        )

        val enumContent = rendered.single { it.outputPath.endsWith("/domain/shared/enums/Status.kt") }.content

        assertTrue(enumContent.contains("enum class Status("))
        assertTrue(enumContent.contains("DRAFT(0, \"Draft\")"))
        assertTrue(enumContent.contains("import jakarta.persistence.AttributeConverter"))
        assertFalse(enumContent.contains("import jakarta.persistence.Converter"))
        assertTrue(enumContent.contains("@jakarta.persistence.Converter(autoApply = false)"))
        assertTrue(enumContent.contains("class Converter : AttributeConverter<Status, Int>"))
        assertTrue(enumContent.contains("override fun convertToDatabaseColumn(attribute: Status?): Int?"))
        assertTrue(enumContent.contains("return attribute?.value"))
        assertTrue(enumContent.contains("override fun convertToEntityAttribute(dbData: Int?): Status?"))
        assertTrue(enumContent.contains("return valueOfOrNull(dbData)"))
    }

    @Test
    fun `falls back to preset flow templates and renders flow artifacts`() {
        val overrideDir = Files.createTempDirectory("cap4k-override-empty-flow")
        val renderer = PebbleArtifactRenderer(
            templateResolver = PresetTemplateResolver(
                preset = "ddd-default",
                overrideDirs = listOf(overrideDir.toString())
            )
        )

        val rendered = renderer.render(
            planItems = listOf(
                ArtifactPlanItem(
                    generatorId = "flow",
                    moduleRole = "project",
                    templateId = "flow/entry.json.peb",
                    outputPath = "flows/OrderController_submit.json",
                    context = mapOf(
                        "jsonContent" to """{"entryId":"OrderController::submit","edgeCount":2}"""
                    ),
                    conflictPolicy = ConflictPolicy.SKIP
                ),
                ArtifactPlanItem(
                    generatorId = "flow",
                    moduleRole = "project",
                    templateId = "flow/entry.mmd.peb",
                    outputPath = "flows/OrderController_submit.mmd",
                    context = mapOf(
                        "mermaidText" to """
                            flowchart TD
                              N1[OrderController::submit]
                              N1 -->|ControllerMethodToCommand| N2[SubmitOrderCmd]
                        """.trimIndent()
                    ),
                    conflictPolicy = ConflictPolicy.SKIP
                ),
                ArtifactPlanItem(
                    generatorId = "flow",
                    moduleRole = "project",
                    templateId = "flow/index.json.peb",
                    outputPath = "flows/index.json",
                    context = mapOf(
                        "jsonContent" to """{"flowCount":1,"inputDirs":["app/build/cap4k-code-analysis"]}"""
                    ),
                    conflictPolicy = ConflictPolicy.SKIP
                )
            ),
            config = ProjectConfig(
                basePackage = "com.acme.demo",
                layout = ProjectLayout.MULTI_MODULE,
                modules = emptyMap(),
                sources = emptyMap(),
                generators = mapOf(
                    "flow" to GeneratorConfig(
                        options = mapOf("outputDir" to "flows"),
                    ),
                ),
                templates = TemplateConfig(
                    preset = "ddd-default",
                    overrideDirs = listOf(overrideDir.toString()),
                    conflictPolicy = ConflictPolicy.SKIP
                )
            )
        )

        assertEquals("""{"entryId":"OrderController::submit","edgeCount":2}""", rendered[0].content.trim())
        assertEquals(
            """
            flowchart TD
              N1[OrderController::submit]
              N1 -->|ControllerMethodToCommand| N2[SubmitOrderCmd]
            """.trimIndent(),
            rendered[1].content.trim(),
        )
        assertEquals("""{"flowCount":1,"inputDirs":["app/build/cap4k-code-analysis"]}""", rendered[2].content.trim())
    }

    @Test
    fun `falls back to preset drawing board template and renders valid json`() {
        val overrideDir = Files.createTempDirectory("cap4k-override-empty-drawing-board")
        val renderer = PebbleArtifactRenderer(
            templateResolver = PresetTemplateResolver(
                preset = "ddd-default",
                overrideDirs = listOf(overrideDir.toString())
            )
        )

        val rendered = renderer.render(
            planItems = listOf(
                ArtifactPlanItem(
                    generatorId = "drawing-board",
                    moduleRole = "project",
                    templateId = "drawing-board/document.json.peb",
                    outputPath = "design/cmd.json",
                    context = mapOf(
                        "drawingBoardTag" to "cmd",
                        "elements" to listOf(
                            DrawingBoardElementModel(
                                tag = "cmd",
                                packageName = "orders.api",
                                name = "Submit\"Order",
                                description = "line1\nline2",
                                aggregates = listOf("Order", "Ops\\Audit"),
                                persist = true,
                                fields = listOf(
                                    DrawingBoardFieldModel(
                                        name = "remark",
                                        type = "String?",
                                        defaultValue = "say \"hi\""
                                    )
                                ),
                                resultFields = listOf(
                                    DrawingBoardFieldModel(
                                        name = "status",
                                        type = "String",
                                    )
                                ),
                            )
                        ),
                    ),
                    conflictPolicy = ConflictPolicy.SKIP
                )
            ),
            config = ProjectConfig(
                basePackage = "com.acme.demo",
                layout = ProjectLayout.MULTI_MODULE,
                modules = emptyMap(),
                sources = emptyMap(),
                generators = emptyMap(),
                templates = TemplateConfig(
                    preset = "ddd-default",
                    overrideDirs = listOf(overrideDir.toString()),
                    conflictPolicy = ConflictPolicy.SKIP
                )
            )
        )

        val content = rendered.single().content
        val element = JsonParser.parseString(content).asJsonArray.single().asJsonObject

        assertEquals("cmd", element["tag"].asString)
        assertEquals("orders.api", element["package"].asString)
        assertEquals("Submit\"Order", element["name"].asString)
        assertEquals("line1\nline2", element["description"].asString)
        assertEquals("Ops\\Audit", element["aggregates"].asJsonArray[1].asString)
        assertEquals(true, element["persist"].asBoolean)
        assertEquals("say \"hi\"", element["fields"].asJsonArray[0].asJsonObject["defaultValue"].asString)
        assertEquals("status", element["resultFields"].asJsonArray[0].asJsonObject["name"].asString)
    }

    @Test
    fun `throws clear error when template is missing`() {
        val resolver = PresetTemplateResolver(
            preset = "ddd-default",
            overrideDirs = emptyList()
        )

        val exception = assertThrows<IllegalStateException> {
            resolver.resolve("design/not-exists.kt.peb")
        }

        assertTrue(exception.message!!.contains("Template not found: presets/ddd-default/design/not-exists.kt.peb"))
    }

    @Test
    fun `preserves outputPath and conflictPolicy in rendered artifact`() {
        val overrideDir = Files.createTempDirectory("cap4k-override-meta")
        val overrideDesignDir = Files.createDirectories(overrideDir.resolve("design"))
        overrideDesignDir.resolve("query.kt.peb")
            .writeText("class {{ typeName }}")

        val renderer = PebbleArtifactRenderer(
            templateResolver = PresetTemplateResolver(
                preset = "ddd-default",
                overrideDirs = listOf(overrideDir.toString())
            )
        )

        val outputPath = "demo-application/src/main/kotlin/com/acme/demo/application/queries/FindOrderQry.kt"
        val conflictPolicy = ConflictPolicy.FAIL
        val rendered = renderer.render(
            planItems = listOf(
                ArtifactPlanItem(
                    generatorId = "command",
                    moduleRole = "application",
                    templateId = "design/query.kt.peb",
                    outputPath = outputPath,
                    context = mapOf("typeName" to "FindOrderQry"),
                    conflictPolicy = conflictPolicy
                )
            ),
            config = ProjectConfig(
                basePackage = "com.acme.demo",
                layout = ProjectLayout.MULTI_MODULE,
                modules = emptyMap(),
                sources = emptyMap(),
                generators = emptyMap(),
                templates = TemplateConfig(
                    preset = "ddd-default",
                    overrideDirs = listOf(overrideDir.toString()),
                    conflictPolicy = ConflictPolicy.SKIP
                )
            )
        )

        val artifact = rendered.single()
        assertTrue(artifact.outputPath == outputPath)
        assertTrue(artifact.conflictPolicy == conflictPolicy)
    }

    @Test
    fun `renders drawing board json with optional entity persist and field metadata`() {
        val renderer = PebbleArtifactRenderer(
            templateResolver = PresetTemplateResolver(
                preset = "ddd-default",
                overrideDirs = emptyList()
            )
        )

        val outputPath = "design/cmd.json"
        val conflictPolicy = ConflictPolicy.SKIP
        val rendered = renderer.render(
            planItems = listOf(
                ArtifactPlanItem(
                    generatorId = "drawing-board",
                    moduleRole = "project",
                    templateId = "drawing-board/document.json.peb",
                    outputPath = outputPath,
                    context = mapOf(
                        "elements" to listOf(
                            DrawingBoardElementModel(
                                tag = "cmd",
                                packageName = "orders",
                                name = "SubmitOrder",
                                description = "submit order",
                                aggregates = listOf("Order"),
                                persist = true,
                                fields = listOf(
                                    DrawingBoardFieldModel(
                                        name = "id",
                                        type = "Long",
                                        defaultValue = null
                                    )
                                ),
                                resultFields = listOf(
                                    DrawingBoardFieldModel(
                                        name = "accepted",
                                        type = "Boolean?",
                                        defaultValue = "false"
                                    )
                                )
                            ),
                            DrawingBoardElementModel(
                                tag = "qry",
                                packageName = "orders",
                                name = "FindOrder",
                                description = "find order",
                                aggregates = emptyList(),
                                fields = emptyList(),
                                resultFields = emptyList()
                            )
                        )
                    ),
                    conflictPolicy = conflictPolicy
                )
            ),
            config = ProjectConfig(
                basePackage = "com.acme.demo",
                layout = ProjectLayout.MULTI_MODULE,
                modules = emptyMap(),
                sources = emptyMap(),
                generators = emptyMap(),
                templates = TemplateConfig(
                    preset = "ddd-default",
                    overrideDirs = emptyList(),
                    conflictPolicy = ConflictPolicy.SKIP
                )
            )
        )

        val artifact = rendered.single()
        assertTrue(artifact.outputPath == outputPath)
        assertTrue(artifact.conflictPolicy == conflictPolicy)

        val content = artifact.content
        assertTrue(content.startsWith("["))
        assertTrue(content.contains("\"tag\": \"cmd\""))
        assertTrue(content.contains("\"package\": \"orders\""))
        assertTrue(content.contains("\"name\": \"SubmitOrder\""))
        assertTrue(content.contains("\"description\": \"submit order\""))
        assertTrue(content.contains("\"aggregates\": [\"Order\"]"))
        assertTrue(content.contains("\"persist\": true"))
        assertTrue(content.contains("\"fields\": ["))
        assertTrue(content.contains("\"name\": \"id\""))
        assertTrue(content.contains("\"resultFields\": ["))
        assertTrue(content.contains("\"name\": \"accepted\""))
        assertTrue(content.contains("\"type\": \"Boolean?\""))
        assertTrue(content.contains("\"defaultValue\": \"false\""))
        assertTrue(!content.contains("\"nullable\""))
        assertTrue(
            content.contains(
                """
                |    "fields": [
                |      { "name": "id", "type": "Long" }
                |    ],
                """.trimMargin()
            )
        )
        assertTrue(
            content.contains(
                """
                |    "resultFields": [
                |      { "name": "accepted", "type": "Boolean?", "defaultValue": "false" }
                |    ]
                """.trimMargin()
            )
        )
        assertTrue(!content.contains("\"entity\": null"))
        assertTrue(!content.contains("\"persist\": null"))
        assertTrue(!content.contains("\"defaultValue\": null"))
    }

    @Test
    fun `renders drawing board json with formal block keys and default artifact omission`() {
        val renderer = PebbleArtifactRenderer(
            templateResolver = PresetTemplateResolver(
                preset = "ddd-default",
                overrideDirs = emptyList()
            )
        )

        val rendered = renderer.render(
            planItems = listOf(
                ArtifactPlanItem(
                    generatorId = "drawing-board",
                    moduleRole = "project",
                    templateId = "drawing-board/document.json.peb",
                    outputPath = "design/drawing_board_query.json",
                    context = mapOf(
                        "elements" to listOf(
                            DrawingBoardElementModel(
                                tag = "query",
                                packageName = "orders.queries",
                                name = "ReadOrder",
                                description = "read order",
                                fields = listOf(
                                    DrawingBoardFieldModel(name = "orderId", type = "Long"),
                                ),
                                resultFields = listOf(
                                    DrawingBoardFieldModel(name = "status", type = "String"),
                                ),
                            ),
                            DrawingBoardElementModel(
                                tag = "query",
                                packageName = "orders.queries",
                                name = "PageOrders",
                                description = "page orders",
                                artifacts = listOf(
                                    ArtifactSelectionModel(family = "query", variant = "page"),
                                ),
                                fields = emptyList(),
                                resultFields = emptyList(),
                            ),
                            DrawingBoardElementModel(
                                tag = "integration_event",
                                packageName = "orders.events",
                                name = "OrderCreated",
                                description = "order created",
                                fields = listOf(
                                    DrawingBoardFieldModel(name = "orderId", type = "Long"),
                                ),
                                resultFields = emptyList(),
                            ),
                            DrawingBoardElementModel(
                                tag = "integration_event",
                                packageName = "orders.events",
                                name = "OrderSynced",
                                description = "order synced",
                                artifacts = listOf(
                                    ArtifactSelectionModel(family = "integration-event", variant = "inbound"),
                                    ArtifactSelectionModel(family = "integration-subscriber"),
                                ),
                                fields = listOf(
                                    DrawingBoardFieldModel(name = "orderId", type = "Long"),
                                ),
                                resultFields = emptyList(),
                            ),
                            DrawingBoardElementModel(
                                tag = "query",
                                packageName = "orders.queries",
                                name = "QueryWithoutArtifacts",
                                description = "query without generated artifacts",
                                artifacts = emptyList(),
                                artifactsDeclared = true,
                                fields = emptyList(),
                                resultFields = emptyList(),
                            ),
                        )
                    ),
                    conflictPolicy = ConflictPolicy.SKIP
                )
            ),
            config = ProjectConfig(
                basePackage = "com.acme.demo",
                layout = ProjectLayout.MULTI_MODULE,
                modules = emptyMap(),
                sources = emptyMap(),
                generators = emptyMap(),
                templates = TemplateConfig(
                    preset = "ddd-default",
                    overrideDirs = emptyList(),
                    conflictPolicy = ConflictPolicy.SKIP
                )
            )
        )

        val content = rendered.single().content
        val elements = JsonParser.parseString(content).asJsonArray

        assertEquals("query", elements[0].asJsonObject["tag"].asString)
        assertEquals("orders.queries", elements[0].asJsonObject["package"].asString)
        assertEquals("ReadOrder", elements[0].asJsonObject["name"].asString)
        assertEquals("read order", elements[0].asJsonObject["description"].asString)
        assertTrue(elements[0].asJsonObject["fields"].asJsonArray.size() == 1)
        assertTrue(elements[0].asJsonObject["resultFields"].asJsonArray.size() == 1)
        assertFalse(elements[0].asJsonObject.has("desc"))
        assertFalse(elements[0].asJsonObject.has("requestFields"))
        assertFalse(elements[0].asJsonObject.has("responseFields"))
        assertFalse(elements[0].asJsonObject.has("traits"))
        assertFalse(elements[0].asJsonObject.has("role"))
        assertFalse(elements[0].asJsonObject.has("eventName"))
        assertFalse(elements[0].asJsonObject.has("entity"))
        assertFalse(elements[0].asJsonObject.has("message"))
        assertFalse(elements[0].asJsonObject.has("targets"))
        assertFalse(elements[0].asJsonObject.has("valueType"))
        assertFalse(elements[0].asJsonObject.has("artifacts"))

        val pageQuery = elements[1].asJsonObject
        assertEquals("PageOrders", pageQuery["name"].asString)
        assertTrue(pageQuery.has("artifacts"))
        assertEquals("query", pageQuery["artifacts"].asJsonArray[0].asJsonObject["family"].asString)
        assertEquals("page", pageQuery["artifacts"].asJsonArray[0].asJsonObject["variant"].asString)

        val inboundIntegration = elements[2].asJsonObject
        assertFalse(inboundIntegration.has("artifacts"))

        val explicitIntegration = elements[3].asJsonObject
        assertEquals(2, explicitIntegration["artifacts"].asJsonArray.size())
        assertEquals("integration-event", explicitIntegration["artifacts"].asJsonArray[0].asJsonObject["family"].asString)
        assertEquals("inbound", explicitIntegration["artifacts"].asJsonArray[0].asJsonObject["variant"].asString)
        assertEquals("integration-subscriber", explicitIntegration["artifacts"].asJsonArray[1].asJsonObject["family"].asString)
        assertFalse(explicitIntegration["artifacts"].asJsonArray[1].asJsonObject.has("variant"))

        val explicitEmptyArtifacts = elements[4].asJsonObject
        assertEquals("QueryWithoutArtifacts", explicitEmptyArtifacts["name"].asString)
        assertTrue(explicitEmptyArtifacts.has("artifacts"))
        assertEquals(0, explicitEmptyArtifacts["artifacts"].asJsonArray.size())
    }

    @Test
    fun `renders drawing board json without html escaping`() {
        val renderer = PebbleArtifactRenderer(
            templateResolver = PresetTemplateResolver(
                preset = "ddd-default",
                overrideDirs = emptyList()
            )
        )

        val rendered = renderer.render(
            planItems = listOf(
                ArtifactPlanItem(
                    generatorId = "drawing-board",
                    moduleRole = "project",
                    templateId = "drawing-board/document.json.peb",
                    outputPath = "design/command.json",
                    context = mapOf(
                        "elements" to listOf(
                            DrawingBoardElementModel(
                                tag = "command",
                                packageName = "demo.application.workflow",
                                name = "SubmitDefaults",
                                description = "Map<String, String> <raw> & stable",
                                aggregates = listOf("Content"),
                            )
                        )
                    ),
                    conflictPolicy = ConflictPolicy.SKIP
                )
            ),
            config = ProjectConfig(
                basePackage = "com.acme.demo",
                layout = ProjectLayout.MULTI_MODULE,
                modules = emptyMap(),
                sources = emptyMap(),
                generators = emptyMap(),
                templates = TemplateConfig(
                    preset = "ddd-default",
                    overrideDirs = emptyList(),
                    conflictPolicy = ConflictPolicy.SKIP
                )
            )
        )

        val content = rendered.single().content
        assertFalse(content.contains("\\u003c"))
        assertFalse(content.contains("\\u003e"))
        assertFalse(content.contains("\\u0026"))

        val element = JsonParser.parseString(content).asJsonArray.single().asJsonObject
        assertEquals("Map<String, String> <raw> & stable", element["description"].asString)
        assertEquals("Content", element["aggregates"].asJsonArray.single().asString)
    }

    @Test
    fun `renders drawing board request and response field default values unchanged`() {
        val renderer = PebbleArtifactRenderer(
            templateResolver = PresetTemplateResolver(
                preset = "ddd-default",
                overrideDirs = emptyList()
            )
        )

        val rendered = renderer.render(
            planItems = listOf(
                ArtifactPlanItem(
                    generatorId = "drawing-board",
                    moduleRole = "project",
                    templateId = "drawing-board/document.json.peb",
                    outputPath = "design/command.json",
                    context = mapOf(
                        "elements" to listOf(
                            DrawingBoardElementModel(
                                tag = "command",
                                packageName = "demo.application.shared",
                                name = "IssueCaptcha",
                                description = "issue captcha",
                                fields = listOf(
                                    DrawingBoardFieldModel(
                                        name = "channels",
                                        type = "Set<String>",
                                        defaultValue = "emptySet()",
                                    ),
                                    DrawingBoardFieldModel(
                                        name = "metadata",
                                        type = "Map<String, String>?",
                                        defaultValue = "null",
                                    ),
                                ),
                                resultFields = listOf(
                                    DrawingBoardFieldModel(
                                        name = "channel",
                                        type = "SharedCaptchaChannel",
                                        defaultValue = "demo.application.shared.defaults.SharedCaptchaChannel.IMAGE",
                                    ),
                                    DrawingBoardFieldModel(
                                        name = "title",
                                        type = "String",
                                        defaultValue = "demo.application.shared.defaults.SHARED_FIELD_DEFAULT_TITLE",
                                    ),
                                ),
                            )
                        )
                    ),
                    conflictPolicy = ConflictPolicy.SKIP
                )
            ),
            config = ProjectConfig(
                basePackage = "com.acme.demo",
                layout = ProjectLayout.MULTI_MODULE,
                modules = emptyMap(),
                sources = emptyMap(),
                generators = emptyMap(),
                templates = TemplateConfig(
                    preset = "ddd-default",
                    overrideDirs = emptyList(),
                    conflictPolicy = ConflictPolicy.SKIP
                )
            )
        )

        val element = JsonParser.parseString(rendered.single().content).asJsonArray.single().asJsonObject
        val fields = element["fields"].asJsonArray
        val resultFields = element["resultFields"].asJsonArray

        assertEquals("emptySet()", fields[0].asJsonObject["defaultValue"].asString)
        assertEquals("null", fields[1].asJsonObject["defaultValue"].asString)
        assertEquals(
            "demo.application.shared.defaults.SharedCaptchaChannel.IMAGE",
            resultFields[0].asJsonObject["defaultValue"].asString,
        )
        assertEquals(
            "demo.application.shared.defaults.SHARED_FIELD_DEFAULT_TITLE",
            resultFields[1].asJsonObject["defaultValue"].asString,
        )
    }

    @Test
    fun `renders domain event drawing board json without reserved entity fields`() {
        val renderer = PebbleArtifactRenderer(
            templateResolver = PresetTemplateResolver(
                preset = "ddd-default",
                overrideDirs = emptyList()
            )
        )

        val rendered = renderer.render(
            planItems = listOf(
                ArtifactPlanItem(
                    generatorId = "drawing-board",
                    moduleRole = "project",
                    templateId = "drawing-board/document.json.peb",
                    outputPath = "design/domain_event.json",
                    context = mapOf(
                        "elements" to listOf(
                            DrawingBoardElementModel(
                                tag = "domain_event",
                                packageName = "orders",
                                name = "OrderCreated",
                                description = "order created",
                                aggregates = listOf("Order"),
                                persist = false,
                                fields = listOf(
                                    DrawingBoardFieldModel(name = "reason", type = "String"),
                                ),
                                resultFields = emptyList(),
                            )
                        )
                    ),
                    conflictPolicy = ConflictPolicy.SKIP
                )
            ),
            config = ProjectConfig(
                basePackage = "com.acme.demo",
                layout = ProjectLayout.MULTI_MODULE,
                modules = emptyMap(),
                sources = emptyMap(),
                generators = emptyMap(),
                templates = TemplateConfig(
                    preset = "ddd-default",
                    overrideDirs = emptyList(),
                    conflictPolicy = ConflictPolicy.SKIP
                )
            )
        )

        val element = JsonParser.parseString(rendered.single().content).asJsonArray.single().asJsonObject
        assertTrue(!element.has("entity"))
        assertEquals(1, element["fields"].asJsonArray.size())
        assertEquals("reason", element["fields"].asJsonArray[0].asJsonObject["name"].asString)
    }

    @Test
    fun `renders integration event drawing board metadata`() {
        val renderer = PebbleArtifactRenderer(
            templateResolver = PresetTemplateResolver(
                preset = "ddd-default",
                overrideDirs = emptyList()
            )
        )

        val rendered = renderer.render(
            planItems = listOf(
                ArtifactPlanItem(
                    generatorId = "drawing-board",
                    moduleRole = "project",
                    templateId = "drawing-board/document.json.peb",
                    outputPath = "design/drawing_board_integration_event.json",
                    context = mapOf(
                        "elements" to listOf(
                            DrawingBoardElementModel(
                                tag = "integration_event",
                                packageName = "contentstudio.events",
                                name = "MediaProcessingSucceeded",
                                description = "media processing succeeded",
                                fields = listOf(
                                    DrawingBoardFieldModel(name = "mediaId", type = "Long"),
                                ),
                                resultFields = emptyList(),
                                artifacts = listOf(ArtifactSelectionModel("integration-event", "inbound")),
                                eventName = "cap4k.reference.contentstudio.media-processing.succeeded",
                            )
                        )
                    ),
                    conflictPolicy = ConflictPolicy.SKIP
                )
            ),
            config = ProjectConfig(
                basePackage = "com.acme.demo",
                layout = ProjectLayout.MULTI_MODULE,
                modules = emptyMap(),
                sources = emptyMap(),
                generators = emptyMap(),
                templates = TemplateConfig(
                    preset = "ddd-default",
                    overrideDirs = emptyList(),
                    conflictPolicy = ConflictPolicy.SKIP
                )
            )
        )

        val content = rendered.single().content
        assertTrue(content.contains("\"eventName\": \"cap4k.reference.contentstudio.media-processing.succeeded\""))

        val element = JsonParser.parseString(content).asJsonArray.single().asJsonObject
        assertTrue(!element.has("role"))
        assertEquals("cap4k.reference.contentstudio.media-processing.succeeded", element["eventName"].asString)
    }

    @Test
    fun `use helper merges explicit imports with computed imports`() {
        val overrideDir = Files.createTempDirectory("cap4k-override-helper-use-merge")
        val overrideDesignDir = Files.createDirectories(overrideDir.resolve("design"))
        overrideDesignDir.resolve("query.kt.peb")
            .writeText(
                """
                {{ imports(imports) | json | raw }}
                {{ use("java.time.LocalDateTime") -}}
                """.trimIndent()
            )

        val renderer = PebbleArtifactRenderer(
            templateResolver = PresetTemplateResolver(
                preset = "ddd-default",
                overrideDirs = listOf(overrideDir.toString())
            )
        )

        val rendered = renderer.render(
            planItems = listOf(
                ArtifactPlanItem(
                    generatorId = "command",
                    moduleRole = "application",
                    templateId = "design/query.kt.peb",
                    outputPath = "demo-application/src/main/kotlin/com/acme/demo/application/queries/FindOrderQry.kt",
                    context = mapOf(
                        "imports" to listOf("java.util.UUID")
                    ),
                    conflictPolicy = ConflictPolicy.SKIP
                )
            ),
            config = ProjectConfig(
                basePackage = "com.acme.demo",
                layout = ProjectLayout.MULTI_MODULE,
                modules = emptyMap(),
                sources = emptyMap(),
                generators = emptyMap(),
                templates = TemplateConfig(
                    preset = "ddd-default",
                    overrideDirs = listOf(overrideDir.toString()),
                    conflictPolicy = ConflictPolicy.SKIP
                )
            )
        )

        assertEquals("""["java.time.LocalDateTime","java.util.UUID"]""", rendered.single().content.trim())
    }

    @Test
    fun `use helper merges explicit imports with carrier map imports`() {
        val overrideDir = Files.createTempDirectory("cap4k-override-helper-use-map-merge")
        val overrideDesignDir = Files.createDirectories(overrideDir.resolve("design"))
        overrideDesignDir.resolve("query.kt.peb")
            .writeText(
                """
                {{ use("java.time.LocalDateTime") -}}
                {{ imports(importCarrier) | json | raw }}
                """.trimIndent()
            )

        val renderer = PebbleArtifactRenderer(
            templateResolver = PresetTemplateResolver(
                preset = "ddd-default",
                overrideDirs = listOf(overrideDir.toString())
            )
        )

        val rendered = renderer.render(
            planItems = listOf(
                ArtifactPlanItem(
                    generatorId = "command",
                    moduleRole = "application",
                    templateId = "design/query.kt.peb",
                    outputPath = "demo-application/src/main/kotlin/com/acme/demo/application/queries/FindOrderQry.kt",
                    context = mapOf(
                        "importCarrier" to mapOf(
                            "imports" to listOf("java.util.UUID")
                        )
                    ),
                    conflictPolicy = ConflictPolicy.SKIP
                )
            ),
            config = ProjectConfig(
                basePackage = "com.acme.demo",
                layout = ProjectLayout.MULTI_MODULE,
                modules = emptyMap(),
                sources = emptyMap(),
                generators = emptyMap(),
                templates = TemplateConfig(
                    preset = "ddd-default",
                    overrideDirs = listOf(overrideDir.toString()),
                    conflictPolicy = ConflictPolicy.SKIP
                )
            )
        )

        assertEquals("""["java.time.LocalDateTime","java.util.UUID"]""", rendered.single().content.trim())
    }

    @Test
    fun `design helper session is cleared between artifacts`() {
        val overrideDir = Files.createTempDirectory("cap4k-override-helper-design-session")
        val overrideDesignDir = Files.createDirectories(overrideDir.resolve("design"))
        overrideDesignDir.resolve("first.kt.peb")
            .writeText("""{{ use("java.time.LocalDateTime") -}}""")
        overrideDesignDir.resolve("second.kt.peb")
            .writeText(
                """
                {{ imports(importCarrier) | json | raw }}
                """.trimIndent()
            )

        val renderer = PebbleArtifactRenderer(
            templateResolver = PresetTemplateResolver(
                preset = "ddd-default",
                overrideDirs = listOf(overrideDir.toString())
            )
        )

        val rendered = renderer.render(
            planItems = listOf(
                ArtifactPlanItem(
                    generatorId = "command",
                    moduleRole = "application",
                    templateId = "design/first.kt.peb",
                    outputPath = "demo-application/src/main/kotlin/com/acme/demo/application/queries/First.kt",
                    context = emptyMap(),
                    conflictPolicy = ConflictPolicy.SKIP
                ),
                ArtifactPlanItem(
                    generatorId = "command",
                    moduleRole = "application",
                    templateId = "design/second.kt.peb",
                    outputPath = "demo-application/src/main/kotlin/com/acme/demo/application/queries/Second.kt",
                    context = mapOf(
                        "importCarrier" to mapOf(
                            "imports" to listOf("java.util.UUID")
                        )
                    ),
                    conflictPolicy = ConflictPolicy.SKIP
                )
            ),
            config = ProjectConfig(
                basePackage = "com.acme.demo",
                layout = ProjectLayout.MULTI_MODULE,
                modules = emptyMap(),
                sources = emptyMap(),
                generators = emptyMap(),
                templates = TemplateConfig(
                    preset = "ddd-default",
                    overrideDirs = listOf(overrideDir.toString()),
                    conflictPolicy = ConflictPolicy.SKIP
                )
            )
        )

        assertEquals(2, rendered.size)
        assertEquals("", rendered[0].content.trim())
        assertEquals("""["java.util.UUID"]""", rendered[1].content.trim())
    }

    @Test
    fun `regular aggregate override templates can use helper`() {
        val overrideDir = Files.createTempDirectory("cap4k-override-helper-use-aggregate")
        val overrideAggregateDir = Files.createDirectories(overrideDir.resolve("aggregate"))
        overrideAggregateDir.resolve("schema.kt.peb")
            .writeText(
                """
                {{ use("java.time.LocalDateTime") -}}
                {% for importValue in imports %}
                import {{ importValue }}
                {% endfor %}
                class {{ typeName }}
                """.trimIndent()
            )

        val rendered = PebbleArtifactRenderer(
            templateResolver = PresetTemplateResolver(
                preset = "ddd-default",
                overrideDirs = listOf(overrideDir.toString())
            )
        ).render(
            planItems = listOf(
                ArtifactPlanItem(
                    generatorId = "aggregate",
                    moduleRole = "domain",
                    templateId = "aggregate/schema.kt.peb",
                    outputPath = "demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/order/OrderSchema.kt",
                    context = mapOf(
                        "packageName" to "com.acme.demo.domain.aggregates.order",
                        "typeName" to "OrderSchema",
                        "entityName" to "Order",
                        "imports" to listOf("java.util.UUID")
                    ),
                    conflictPolicy = ConflictPolicy.SKIP
                )
            ),
            config = ProjectConfig(
                basePackage = "com.acme.demo",
                layout = ProjectLayout.MULTI_MODULE,
                modules = emptyMap(),
                sources = emptyMap(),
                generators = emptyMap(),
                templates = TemplateConfig(
                    preset = "ddd-default",
                    overrideDirs = listOf(overrideDir.toString()),
                    conflictPolicy = ConflictPolicy.SKIP
                )
            )
        )

        val content = rendered.single().content
        assertTrue(content.contains("import java.time.LocalDateTime"))
        assertTrue(content.contains("import java.util.UUID"))
        assertTrue(content.contains("class OrderSchema"))
    }

    @Test
    fun `regular aggregate use helper renders deterministically sorted imports`() {
        val overrideDir = Files.createTempDirectory("cap4k-override-helper-use-aggregate-sorted")
        val overrideAggregateDir = Files.createDirectories(overrideDir.resolve("aggregate"))
        overrideAggregateDir.resolve("schema.kt.peb")
            .writeText(
                """
                {{ use("java.time.ZonedDateTime") -}}
                {{ use("java.time.LocalDateTime") -}}
                {% for importValue in imports %}
                {{ importValue }}
                {% endfor %}
                """.trimIndent()
            )

        val rendered = PebbleArtifactRenderer(
            templateResolver = PresetTemplateResolver(
                preset = "ddd-default",
                overrideDirs = listOf(overrideDir.toString())
            )
        ).render(
            planItems = listOf(
                ArtifactPlanItem(
                    generatorId = "aggregate",
                    moduleRole = "domain",
                    templateId = "aggregate/schema.kt.peb",
                    outputPath = "demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/order/OrderSchema.kt",
                    context = mapOf(
                        "packageName" to "com.acme.demo.domain.aggregates.order",
                        "typeName" to "OrderSchema",
                        "entityName" to "Order",
                        "imports" to listOf(
                            "java.util.UUID",
                            " java.time.Instant ",
                            "java.time.LocalDateTime",
                            "java.util.UUID",
                            ""
                        )
                    ),
                    conflictPolicy = ConflictPolicy.SKIP
                )
            ),
            config = ProjectConfig(
                basePackage = "com.acme.demo",
                layout = ProjectLayout.MULTI_MODULE,
                modules = emptyMap(),
                sources = emptyMap(),
                generators = emptyMap(),
                templates = TemplateConfig(
                    preset = "ddd-default",
                    overrideDirs = listOf(overrideDir.toString()),
                    conflictPolicy = ConflictPolicy.SKIP
                )
            )
        )

        val imports = rendered.single().content
            .lineSequence()
            .map { line -> line.trim() }
            .filter { line -> line.isNotEmpty() }
            .toList()

        assertEquals(
            listOf(
                "java.time.Instant",
                "java.time.LocalDateTime",
                "java.time.ZonedDateTime",
                "java.util.UUID"
            ),
            imports
        )
    }

    @Test
    fun `regular aggregate use helper fails fast on import conflicts`() {
        val overrideDir = Files.createTempDirectory("cap4k-override-helper-use-aggregate-conflict")
        val overrideAggregateDir = Files.createDirectories(overrideDir.resolve("aggregate"))
        overrideAggregateDir.resolve("schema.kt.peb")
            .writeText("""{{ use("com.bar.Status") -}}{{ imports(imports) | json | raw }}""")

        val exception = assertThrows<Exception> {
            PebbleArtifactRenderer(
                templateResolver = PresetTemplateResolver(
                    preset = "ddd-default",
                    overrideDirs = listOf(overrideDir.toString())
                )
            ).render(
                planItems = listOf(
                    ArtifactPlanItem(
                        generatorId = "aggregate",
                        moduleRole = "domain",
                        templateId = "aggregate/schema.kt.peb",
                        outputPath = "demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/order/OrderSchema.kt",
                        context = mapOf(
                            "packageName" to "com.acme.demo.domain.aggregates.order",
                            "typeName" to "OrderSchema",
                            "entityName" to "Order",
                            "imports" to listOf("com.foo.Status")
                        ),
                        conflictPolicy = ConflictPolicy.SKIP
                    )
                ),
                config = ProjectConfig(
                    basePackage = "com.acme.demo",
                    layout = ProjectLayout.MULTI_MODULE,
                    modules = emptyMap(),
                    sources = emptyMap(),
                    generators = emptyMap(),
                    templates = TemplateConfig(
                        preset = "ddd-default",
                        overrideDirs = listOf(overrideDir.toString()),
                        conflictPolicy = ConflictPolicy.SKIP
                    )
                )
            )
        }

        val illegalArgument = generateSequence<Throwable>(exception) { it.cause }
            .filterIsInstance<IllegalArgumentException>()
            .firstOrNull()

        assertTrue(illegalArgument != null)
        assertTrue(illegalArgument!!.message!!.contains("use() import conflict"))
    }

    @Test
    fun `regular aggregate ignores non-list imports payload when helper contract is unused`() {
        val overrideDir = Files.createTempDirectory("cap4k-override-helper-use-aggregate-compat-plain")
        val overrideAggregateDir = Files.createDirectories(overrideDir.resolve("aggregate"))
        overrideAggregateDir.resolve("schema.kt.peb")
            .writeText(
                """
                package {{ packageName }}
                class {{ typeName }}
                """.trimIndent()
            )

        val rendered = PebbleArtifactRenderer(
            templateResolver = PresetTemplateResolver(
                preset = "ddd-default",
                overrideDirs = listOf(overrideDir.toString())
            )
        ).render(
            planItems = listOf(
                ArtifactPlanItem(
                    generatorId = "aggregate",
                    moduleRole = "domain",
                    templateId = "aggregate/schema.kt.peb",
                    outputPath = "demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/order/OrderSchema.kt",
                    context = mapOf(
                        "packageName" to "com.acme.demo.domain.aggregates.order",
                        "typeName" to "OrderSchema",
                        "entityName" to "Order",
                        "imports" to "not-a-list"
                    ),
                    conflictPolicy = ConflictPolicy.SKIP
                )
            ),
            config = ProjectConfig(
                basePackage = "com.acme.demo",
                layout = ProjectLayout.MULTI_MODULE,
                modules = emptyMap(),
                sources = emptyMap(),
                generators = emptyMap(),
                templates = TemplateConfig(
                    preset = "ddd-default",
                    overrideDirs = listOf(overrideDir.toString()),
                    conflictPolicy = ConflictPolicy.SKIP
                )
            )
        )

        val content = rendered.single().content
        assertTrue(content.contains("package com.acme.demo.domain.aggregates.order"))
        assertTrue(content.contains("class OrderSchema"))
    }

    @Test
    fun `regular aggregate map carrier preserves extra payload while merging collected imports`() {
        val overrideDir = Files.createTempDirectory("cap4k-override-helper-use-aggregate-map-shape")
        val overrideAggregateDir = Files.createDirectories(overrideDir.resolve("aggregate"))
        overrideAggregateDir.resolve("schema.kt.peb")
            .writeText(
                """
                {{ use("java.time.LocalDateTime") -}}
                extra={{ imports.extra }}
                merged={{ imports(imports) | json | raw }}
                """.trimIndent()
            )

        val rendered = PebbleArtifactRenderer(
            templateResolver = PresetTemplateResolver(
                preset = "ddd-default",
                overrideDirs = listOf(overrideDir.toString())
            )
        ).render(
            planItems = listOf(
                ArtifactPlanItem(
                    generatorId = "aggregate",
                    moduleRole = "domain",
                    templateId = "aggregate/schema.kt.peb",
                    outputPath = "demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/order/OrderSchema.kt",
                    context = mapOf(
                        "packageName" to "com.acme.demo.domain.aggregates.order",
                        "typeName" to "OrderSchema",
                        "entityName" to "Order",
                        "imports" to mapOf(
                            "imports" to listOf("java.util.UUID"),
                            "extra" to "expected-extra"
                        )
                    ),
                    conflictPolicy = ConflictPolicy.SKIP
                )
            ),
            config = ProjectConfig(
                basePackage = "com.acme.demo",
                layout = ProjectLayout.MULTI_MODULE,
                modules = emptyMap(),
                sources = emptyMap(),
                generators = emptyMap(),
                templates = TemplateConfig(
                    preset = "ddd-default",
                    overrideDirs = listOf(overrideDir.toString()),
                    conflictPolicy = ConflictPolicy.SKIP
                )
            )
        )

        val content = rendered.single().content
        assertTrue(content.contains("extra=expected-extra"))
        assertTrue(content.contains("""merged=["java.time.LocalDateTime","java.util.UUID"]"""))
    }

    @Test
    fun `use helper deduplicates repeated explicit imports`() {
        val overrideDir = Files.createTempDirectory("cap4k-override-helper-use-dedupe")
        val overrideDesignDir = Files.createDirectories(overrideDir.resolve("design"))
        overrideDesignDir.resolve("query.kt.peb")
            .writeText(
                """
                {{ use("java.time.LocalDateTime") -}}
                {{ use("java.time.LocalDateTime") -}}
                {{ imports(imports) | json | raw }}
                """.trimIndent()
            )

        val renderer = PebbleArtifactRenderer(
            templateResolver = PresetTemplateResolver(
                preset = "ddd-default",
                overrideDirs = listOf(overrideDir.toString())
            )
        )

        val rendered = renderer.render(
            planItems = listOf(
                ArtifactPlanItem(
                    generatorId = "command",
                    moduleRole = "application",
                    templateId = "design/query.kt.peb",
                    outputPath = "demo-application/src/main/kotlin/com/acme/demo/application/queries/FindOrderQry.kt",
                    context = mapOf(
                        "imports" to emptyList<String>()
                    ),
                    conflictPolicy = ConflictPolicy.SKIP
                )
            ),
            config = ProjectConfig(
                basePackage = "com.acme.demo",
                layout = ProjectLayout.MULTI_MODULE,
                modules = emptyMap(),
                sources = emptyMap(),
                generators = emptyMap(),
                templates = TemplateConfig(
                    preset = "ddd-default",
                    overrideDirs = listOf(overrideDir.toString()),
                    conflictPolicy = ConflictPolicy.SKIP
                )
            )
        )

        assertEquals("""["java.time.LocalDateTime"]""", rendered.single().content.trim())
    }

    @Test
    fun `use helper does not affect type helper output`() {
        val overrideDir = Files.createTempDirectory("cap4k-override-helper-use-type")
        val overrideDesignDir = Files.createDirectories(overrideDir.resolve("design"))
        overrideDesignDir.resolve("query.kt.peb")
            .writeText(
                """
                {{ use("java.time.LocalDateTime") -}}
                {{ type(field) | raw }}
                """.trimIndent()
            )

        val renderer = PebbleArtifactRenderer(
            templateResolver = PresetTemplateResolver(
                preset = "ddd-default",
                overrideDirs = listOf(overrideDir.toString())
            )
        )

        val rendered = renderer.render(
            planItems = listOf(
                ArtifactPlanItem(
                    generatorId = "command",
                    moduleRole = "application",
                    templateId = "design/query.kt.peb",
                    outputPath = "demo-application/src/main/kotlin/com/acme/demo/application/queries/FindOrderQry.kt",
                    context = mapOf(
                        "field" to RenderedTypeCarrier("List<com.foo.Status?>")
                    ),
                    conflictPolicy = ConflictPolicy.SKIP
                )
            ),
            config = ProjectConfig(
                basePackage = "com.acme.demo",
                layout = ProjectLayout.MULTI_MODULE,
                modules = emptyMap(),
                sources = emptyMap(),
                generators = emptyMap(),
                templates = TemplateConfig(
                    preset = "ddd-default",
                    overrideDirs = listOf(overrideDir.toString()),
                    conflictPolicy = ConflictPolicy.SKIP
                )
            )
        )

        assertEquals("List<com.foo.Status?>", rendered.single().content.trim())
    }

    @Test
    fun `migration style override template composes helper contract`() {
        val overrideDir = Files.createTempDirectory("cap4k-override-helper-migration-contract")
        val overrideDesignDir = Files.createDirectories(overrideDir.resolve("design"))
        overrideDesignDir.resolve("command.kt.peb")
            .writeText(
                """
                {{ use("java.io.Serializable") -}}
                {% for importValue in imports(imports) %}
                import {{ importValue }}
                {% endfor %}
                {% for field in fields %}
                val {{ field.name }}: {{ type(field) | raw }} = {{ field.defaultValue }}
                {% endfor %}
                """.trimIndent()
            )

        val renderer = PebbleArtifactRenderer(
            templateResolver = PresetTemplateResolver(
                preset = "ddd-default",
                overrideDirs = listOf(overrideDir.toString())
            )
        )

        val rendered = renderer.render(
            planItems = listOf(
                ArtifactPlanItem(
                    generatorId = "command",
                    moduleRole = "application",
                    templateId = "design/command.kt.peb",
                    outputPath = "demo-application/src/main/kotlin/com/acme/demo/application/commands/SubmitOrderCmd.kt",
                    context = mapOf(
                        "imports" to listOf(
                            "java.time.LocalDateTime",
                            "java.util.UUID",
                        ),
                        "fields" to listOf(
                            mapOf("name" to "retryCount", "renderedType" to "Long", "nullable" to false, "defaultValue" to "1L"),
                            mapOf("name" to "createdAt", "renderedType" to "LocalDateTime", "nullable" to false, "defaultValue" to "java.time.LocalDateTime.MIN"),
                            mapOf("name" to "enabled", "renderedType" to "Boolean", "nullable" to false, "defaultValue" to "true"),
                            mapOf("name" to "requestStatus", "renderedType" to "com.foo.Status", "nullable" to false, "defaultValue" to "com.foo.Status.ACTIVE"),
                            mapOf("name" to "responseStatus", "renderedType" to "com.bar.Status", "nullable" to false, "defaultValue" to "com.bar.Status.PENDING"),
                        ),
                    ),
                    conflictPolicy = ConflictPolicy.SKIP
                )
            ),
            config = ProjectConfig(
                basePackage = "com.acme.demo",
                layout = ProjectLayout.MULTI_MODULE,
                modules = emptyMap(),
                sources = emptyMap(),
                generators = emptyMap(),
                templates = TemplateConfig(
                    preset = "ddd-default",
                    overrideDirs = listOf(overrideDir.toString()),
                    conflictPolicy = ConflictPolicy.SKIP
                )
            )
        )

        val content = rendered.single().content
        assertTrue(content.contains("import java.io.Serializable"))
        assertTrue(content.contains("import java.time.LocalDateTime"))
        assertTrue(content.contains("import java.util.UUID"))
        assertFalse(content.contains("import com.foo.Status"))
        assertFalse(content.contains("import com.bar.Status"))
        assertTrue(content.contains("val retryCount: Long = 1L"))
        assertTrue(content.contains("val createdAt: LocalDateTime = java.time.LocalDateTime.MIN"))
        assertTrue(content.contains("val enabled: Boolean = true"))
        assertTrue(content.contains("val requestStatus: com.foo.Status = com.foo.Status.ACTIVE"))
        assertTrue(content.contains("val responseStatus: com.bar.Status = com.bar.Status.PENDING"))
    }

    @Test
    fun `use helper fails fast when argument is missing`() {
        val overrideDir = Files.createTempDirectory("cap4k-override-helper-use-missing")
        val overrideDesignDir = Files.createDirectories(overrideDir.resolve("design"))
        overrideDesignDir.resolve("query.kt.peb")
            .writeText("""{{ use() }}""")

        val exception = assertThrows<Exception> {
            PebbleArtifactRenderer(
                templateResolver = PresetTemplateResolver(
                    preset = "ddd-default",
                    overrideDirs = listOf(overrideDir.toString())
                )
            ).render(
                planItems = listOf(
                    ArtifactPlanItem(
                        generatorId = "command",
                        moduleRole = "application",
                        templateId = "design/query.kt.peb",
                        outputPath = "demo.kt",
                        context = emptyMap(),
                        conflictPolicy = ConflictPolicy.SKIP
                    )
                ),
                config = ProjectConfig(
                    basePackage = "com.acme.demo",
                    layout = ProjectLayout.MULTI_MODULE,
                    modules = emptyMap(),
                    sources = emptyMap(),
                    generators = emptyMap(),
                    templates = TemplateConfig(
                        preset = "ddd-default",
                        overrideDirs = listOf(overrideDir.toString()),
                        conflictPolicy = ConflictPolicy.SKIP
                    )
                )
            )
        }

        val illegalArgument = generateSequence<Throwable>(exception) { it.cause }
            .filterIsInstance<IllegalArgumentException>()
            .firstOrNull()

        assertTrue(illegalArgument != null)
        assertTrue(illegalArgument!!.message!!.contains("use() requires exactly one argument"))
    }

    @Test
    fun `use helper fails fast when argument is not a string`() {
        val overrideDir = Files.createTempDirectory("cap4k-override-helper-use-non-string")
        val overrideDesignDir = Files.createDirectories(overrideDir.resolve("design"))
        overrideDesignDir.resolve("query.kt.peb")
            .writeText("""{{ use(badValue) }}""")

        val exception = assertThrows<Exception> {
            PebbleArtifactRenderer(
                templateResolver = PresetTemplateResolver(
                    preset = "ddd-default",
                    overrideDirs = listOf(overrideDir.toString())
                )
            ).render(
                planItems = listOf(
                    ArtifactPlanItem(
                        generatorId = "command",
                        moduleRole = "application",
                        templateId = "design/query.kt.peb",
                        outputPath = "demo.kt",
                        context = mapOf("badValue" to 123),
                        conflictPolicy = ConflictPolicy.SKIP
                    )
                ),
                config = ProjectConfig(
                    basePackage = "com.acme.demo",
                    layout = ProjectLayout.MULTI_MODULE,
                    modules = emptyMap(),
                    sources = emptyMap(),
                    generators = emptyMap(),
                    templates = TemplateConfig(
                        preset = "ddd-default",
                        overrideDirs = listOf(overrideDir.toString()),
                        conflictPolicy = ConflictPolicy.SKIP
                    )
                )
            )
        }

        val illegalArgument = generateSequence<Throwable>(exception) { it.cause }
            .filterIsInstance<IllegalArgumentException>()
            .firstOrNull()

        assertTrue(illegalArgument != null)
        assertTrue(illegalArgument!!.message!!.contains("use() requires a string fully qualified type name"))
    }

    @Test
    fun `use helper fails fast when argument is a short name`() {
        val overrideDir = Files.createTempDirectory("cap4k-override-helper-use-short-name")
        val overrideDesignDir = Files.createDirectories(overrideDir.resolve("design"))
        overrideDesignDir.resolve("query.kt.peb")
            .writeText("""{{ use("LocalDateTime") }}""")

        val exception = assertThrows<Exception> {
            PebbleArtifactRenderer(
                templateResolver = PresetTemplateResolver(
                    preset = "ddd-default",
                    overrideDirs = listOf(overrideDir.toString())
                )
            ).render(
                planItems = listOf(
                    ArtifactPlanItem(
                        generatorId = "command",
                        moduleRole = "application",
                        templateId = "design/query.kt.peb",
                        outputPath = "demo.kt",
                        context = emptyMap(),
                        conflictPolicy = ConflictPolicy.SKIP
                    )
                ),
                config = ProjectConfig(
                    basePackage = "com.acme.demo",
                    layout = ProjectLayout.MULTI_MODULE,
                    modules = emptyMap(),
                    sources = emptyMap(),
                    generators = emptyMap(),
                    templates = TemplateConfig(
                        preset = "ddd-default",
                        overrideDirs = listOf(overrideDir.toString()),
                        conflictPolicy = ConflictPolicy.SKIP
                    )
                )
            )
        }

        val illegalArgument = generateSequence<Throwable>(exception) { it.cause }
            .filterIsInstance<IllegalArgumentException>()
            .firstOrNull()

        assertTrue(illegalArgument != null)
        assertTrue(illegalArgument!!.message!!.contains("use() requires a fully qualified type name"))
    }

    @Test
    fun `use helper fails fast when argument is a wildcard import`() {
        val overrideDir = Files.createTempDirectory("cap4k-override-helper-use-wildcard")
        val overrideDesignDir = Files.createDirectories(overrideDir.resolve("design"))
        overrideDesignDir.resolve("query.kt.peb")
            .writeText("""{{ use("java.time.*") }}""")

        val exception = assertThrows<Exception> {
            PebbleArtifactRenderer(
                templateResolver = PresetTemplateResolver(
                    preset = "ddd-default",
                    overrideDirs = listOf(overrideDir.toString())
                )
            ).render(
                planItems = listOf(
                    ArtifactPlanItem(
                        generatorId = "command",
                        moduleRole = "application",
                        templateId = "design/query.kt.peb",
                        outputPath = "demo.kt",
                        context = emptyMap(),
                        conflictPolicy = ConflictPolicy.SKIP
                    )
                ),
                config = ProjectConfig(
                    basePackage = "com.acme.demo",
                    layout = ProjectLayout.MULTI_MODULE,
                    modules = emptyMap(),
                    sources = emptyMap(),
                    generators = emptyMap(),
                    templates = TemplateConfig(
                        preset = "ddd-default",
                        overrideDirs = listOf(overrideDir.toString()),
                        conflictPolicy = ConflictPolicy.SKIP
                    )
                )
            )
        }

        val illegalArgument = generateSequence<Throwable>(exception) { it.cause }
            .filterIsInstance<IllegalArgumentException>()
            .firstOrNull()

        assertTrue(illegalArgument != null)
        assertTrue(illegalArgument!!.message!!.contains("use() requires a fully qualified type name"))
    }

    @Test
    fun `use helper fails fast when import name is malformed`() {
        val overrideDir = Files.createTempDirectory("cap4k-override-helper-use-malformed")
        val overrideDesignDir = Files.createDirectories(overrideDir.resolve("design"))
        overrideDesignDir.resolve("query.kt.peb")
            .writeText("""{{ use("java.time.Local-DateTime") }}""")

        val exception = assertThrows<Exception> {
            PebbleArtifactRenderer(
                templateResolver = PresetTemplateResolver(
                    preset = "ddd-default",
                    overrideDirs = listOf(overrideDir.toString())
                )
            ).render(
                planItems = listOf(
                    ArtifactPlanItem(
                        generatorId = "command",
                        moduleRole = "application",
                        templateId = "design/query.kt.peb",
                        outputPath = "demo.kt",
                        context = emptyMap(),
                        conflictPolicy = ConflictPolicy.SKIP
                    )
                ),
                config = ProjectConfig(
                    basePackage = "com.acme.demo",
                    layout = ProjectLayout.MULTI_MODULE,
                    modules = emptyMap(),
                    sources = emptyMap(),
                    generators = emptyMap(),
                    templates = TemplateConfig(
                        preset = "ddd-default",
                        overrideDirs = listOf(overrideDir.toString()),
                        conflictPolicy = ConflictPolicy.SKIP
                    )
                )
            )
        }

        val illegalArgument = generateSequence<Throwable>(exception) { it.cause }
            .filterIsInstance<IllegalArgumentException>()
            .firstOrNull()

        assertTrue(illegalArgument != null)
        assertTrue(illegalArgument!!.message!!.contains("use() requires a fully qualified type name"))
    }

    @Test
    fun `use helper fails fast on collisions with computed imports`() {
        val overrideDir = Files.createTempDirectory("cap4k-override-helper-use-computed-collision")
        val overrideDesignDir = Files.createDirectories(overrideDir.resolve("design"))
        overrideDesignDir.resolve("query.kt.peb")
            .writeText(
                """
                {{ use("com.bar.Status") -}}
                {{ imports(imports) | json | raw }}
                """.trimIndent()
            )

        val exception = assertThrows<Exception> {
            PebbleArtifactRenderer(
                templateResolver = PresetTemplateResolver(
                    preset = "ddd-default",
                    overrideDirs = listOf(overrideDir.toString())
                )
            ).render(
                planItems = listOf(
                    ArtifactPlanItem(
                        generatorId = "command",
                        moduleRole = "application",
                        templateId = "design/query.kt.peb",
                        outputPath = "demo.kt",
                        context = mapOf(
                            "imports" to listOf("com.foo.Status")
                        ),
                        conflictPolicy = ConflictPolicy.SKIP
                    )
                ),
                config = ProjectConfig(
                    basePackage = "com.acme.demo",
                    layout = ProjectLayout.MULTI_MODULE,
                    modules = emptyMap(),
                    sources = emptyMap(),
                    generators = emptyMap(),
                    templates = TemplateConfig(
                        preset = "ddd-default",
                        overrideDirs = listOf(overrideDir.toString()),
                        conflictPolicy = ConflictPolicy.SKIP
                    )
                )
            )
        }

        val illegalArgument = generateSequence<Throwable>(exception) { it.cause }
            .filterIsInstance<IllegalArgumentException>()
            .firstOrNull()

        assertTrue(illegalArgument != null)
        assertTrue(illegalArgument!!.message!!.contains("use() import conflict"))
    }

    @Test
    fun `use helper fails fast on collisions between explicit imports`() {
        val overrideDir = Files.createTempDirectory("cap4k-override-helper-use-explicit-collision")
        val overrideDesignDir = Files.createDirectories(overrideDir.resolve("design"))
        overrideDesignDir.resolve("query.kt.peb")
            .writeText(
                """
                {{ use("com.foo.Status") -}}
                {{ use("com.bar.Status") -}}
                {{ imports(imports) | json | raw }}
                """.trimIndent()
            )

        val exception = assertThrows<Exception> {
            PebbleArtifactRenderer(
                templateResolver = PresetTemplateResolver(
                    preset = "ddd-default",
                    overrideDirs = listOf(overrideDir.toString())
                )
            ).render(
                planItems = listOf(
                    ArtifactPlanItem(
                        generatorId = "command",
                        moduleRole = "application",
                        templateId = "design/query.kt.peb",
                        outputPath = "demo.kt",
                        context = mapOf(
                            "imports" to emptyList<String>()
                        ),
                        conflictPolicy = ConflictPolicy.SKIP
                    )
                ),
                config = ProjectConfig(
                    basePackage = "com.acme.demo",
                    layout = ProjectLayout.MULTI_MODULE,
                    modules = emptyMap(),
                    sources = emptyMap(),
                    generators = emptyMap(),
                    templates = TemplateConfig(
                        preset = "ddd-default",
                        overrideDirs = listOf(overrideDir.toString()),
                        conflictPolicy = ConflictPolicy.SKIP
                    )
                )
            )
        }

        val illegalArgument = generateSequence<Throwable>(exception) { it.cause }
            .filterIsInstance<IllegalArgumentException>()
            .firstOrNull()

        assertTrue(illegalArgument != null)
        assertTrue(illegalArgument!!.message!!.contains("use() import conflict"))
    }

    @Test
    fun `default query handler preset renders service stub`() {
        val overrideDir = Files.createTempDirectory("cap4k-override-empty-query-handler-contract")
        val renderer = PebbleArtifactRenderer(
            templateResolver = PresetTemplateResolver(
                preset = "ddd-default",
                overrideDirs = listOf(overrideDir.toString())
            )
        )

        val rendered = renderer.render(
            planItems = listOf(
                ArtifactPlanItem(
                    generatorId = "query-handler",
                    moduleRole = "adapter",
                    templateId = "design/query_handler.kt.peb",
                    outputPath = "demo-adapter/src/main/kotlin/com/acme/demo/adapter/queries/order/read/FindOrderQryHandler.kt",
                    context = mapOf(
                        "packageName" to "com.acme.demo.adapter.queries.order.read",
                        "typeName" to "FindOrderQryHandler",
                        "description" to "find order query",
                        "queryTypeName" to "FindOrderQry",
                        "imports" to listOf("com.acme.demo.application.queries.order.read.FindOrderQry"),
                        "resultFields" to listOf(
                            mapOf("name" to "responseStatus"),
                            mapOf("name" to "snapshot"),
                        ),
                    ),
                    conflictPolicy = ConflictPolicy.SKIP
                )
            ),
            config = ProjectConfig(
                basePackage = "com.acme.demo",
                layout = ProjectLayout.MULTI_MODULE,
                modules = emptyMap(),
                sources = emptyMap(),
                generators = emptyMap(),
                templates = TemplateConfig(
                    preset = "ddd-default",
                    overrideDirs = listOf(overrideDir.toString()),
                    conflictPolicy = ConflictPolicy.SKIP
                )
            )
        )

        val content = rendered.single().content
        assertTrue(content.contains("import org.springframework.stereotype.Service"))
        assertTrue(content.contains("import com.only4.cap4k.ddd.core.application.query.QueryHandler"))
        assertTrue(content.contains("import com.acme.demo.application.queries.order.read.FindOrderQry"))
        assertTrue(content.contains("class FindOrderQryHandler : QueryHandler<FindOrderQry.Request, FindOrderQry.Response>"))
        assertTrue(content.contains("override fun handle(query: FindOrderQry.Request): FindOrderQry.Response"))
        assertTrue(content.contains("responseStatus = TODO(\"set responseStatus\")"))
        assertTrue(content.contains("snapshot = TODO(\"set snapshot\")"))
        val normalizedContent = content.normalizedLineEndings()
        assertTrue(normalizedContent.contains("package com.acme.demo.adapter.queries.order.read\n\nimport"))
        assertTrue(
            normalizedContent.contains(
                "        return FindOrderQry.Response(\n" +
                    "            responseStatus = TODO(\"set responseStatus\"),\n" +
                    "            snapshot = TODO(\"set snapshot\")\n" +
                    "        )"
            )
        )
        assertFalse(Regex("""Response\(\n\s*\n""").containsMatchIn(normalizedContent))
        assertFalse(Regex("""TODO\("set responseStatus"\),\n\s*\n\s*snapshot""").containsMatchIn(normalizedContent))
        assertFalse(Regex("""TODO\("set snapshot"\)\n\s*\n\s*\)""").containsMatchIn(normalizedContent))
    }

    @Test
    fun `query handler preset renders unified contracts for collection shaped responses`() {
        val overrideDir = Files.createTempDirectory("cap4k-override-empty-query-handler-unified-contracts")
        val renderer = PebbleArtifactRenderer(
            templateResolver = PresetTemplateResolver(
                preset = "ddd-default",
                overrideDirs = listOf(overrideDir.toString())
            )
        )

        val rendered = renderer.render(
            planItems = listOf(
                ArtifactPlanItem(
                    generatorId = "query-handler",
                    moduleRole = "adapter",
                    templateId = "design/query_handler.kt.peb",
                    outputPath = "demo-adapter/src/main/kotlin/com/acme/demo/adapter/queries/order/read/FindOrderListQryHandler.kt",
                    context = mapOf(
                        "packageName" to "com.acme.demo.adapter.queries.order.read",
                        "typeName" to "FindOrderListQryHandler",
                        "description" to "find order list query",
                        "queryTypeName" to "FindOrderListQry",
                        "imports" to listOf("com.acme.demo.application.queries.order.read.FindOrderListQry"),
                        "resultFields" to listOf(
                            mapOf("name" to "responseStatus"),
                        ),
                    ),
                    conflictPolicy = ConflictPolicy.SKIP
                ),
                ArtifactPlanItem(
                    generatorId = "query-handler",
                    moduleRole = "adapter",
                    templateId = "design/query_handler.kt.peb",
                    outputPath = "demo-adapter/src/main/kotlin/com/acme/demo/adapter/queries/order/read/FindOrderPageQryHandler.kt",
                    context = mapOf(
                        "packageName" to "com.acme.demo.adapter.queries.order.read",
                        "typeName" to "FindOrderPageQryHandler",
                        "description" to "find order page query",
                        "queryTypeName" to "FindOrderPageQry",
                        "imports" to listOf("com.acme.demo.application.queries.order.read.FindOrderPageQry"),
                        "resultFields" to listOf(
                            mapOf("name" to "responseStatus"),
                        ),
                    ),
                    conflictPolicy = ConflictPolicy.SKIP
                )
            ),
            config = ProjectConfig(
                basePackage = "com.acme.demo",
                layout = ProjectLayout.MULTI_MODULE,
                modules = emptyMap(),
                sources = emptyMap(),
                generators = emptyMap(),
                templates = TemplateConfig(
                    preset = "ddd-default",
                    overrideDirs = listOf(overrideDir.toString()),
                    conflictPolicy = ConflictPolicy.SKIP
                )
            )
        )

        val listContent = rendered[0].content
        assertTrue(listContent.normalizedLineEndings().contains("package com.acme.demo.adapter.queries.order.read\n\nimport"))
        assertTrue(listContent.contains("import com.only4.cap4k.ddd.core.application.query.QueryHandler"))
        assertTrue(listContent.contains("import com.acme.demo.application.queries.order.read.FindOrderListQry"))
        assertTrue(listContent.contains("class FindOrderListQryHandler : QueryHandler<FindOrderListQry.Request, FindOrderListQry.Response>"))
        assertTrue(listContent.contains("override fun handle(query: FindOrderListQry.Request): FindOrderListQry.Response"))
        assertTrue(listContent.contains("return FindOrderListQry.Response("))
        assertTrue(listContent.contains("responseStatus = TODO(\"set responseStatus\")"))
        assertTrue(
            listContent.normalizedLineEndings().contains(
                "        return FindOrderListQry.Response(\n" +
                    "            responseStatus = TODO(\"set responseStatus\")\n" +
                    "        )"
            )
        )

        val pageContent = rendered[1].content
        assertTrue(pageContent.normalizedLineEndings().contains("package com.acme.demo.adapter.queries.order.read\n\nimport"))
        assertTrue(pageContent.contains("import com.only4.cap4k.ddd.core.application.query.QueryHandler"))
        assertTrue(pageContent.contains("import com.acme.demo.application.queries.order.read.FindOrderPageQry"))
        assertTrue(pageContent.contains("class FindOrderPageQryHandler : QueryHandler<FindOrderPageQry.Request, FindOrderPageQry.Response>"))
        assertTrue(pageContent.contains("override fun handle(query: FindOrderPageQry.Request): FindOrderPageQry.Response"))
        assertTrue(pageContent.contains("return FindOrderPageQry.Response("))
        assertTrue(pageContent.contains("responseStatus = TODO(\"set responseStatus\")"))
        assertTrue(
            pageContent.normalizedLineEndings().contains(
                "        return FindOrderPageQry.Response(\n" +
                    "            responseStatus = TODO(\"set responseStatus\")\n" +
                    "        )"
            )
        )
    }

    @Test
    fun `query handler presets return object response when response fields are empty`() {
        val overrideDir = Files.createTempDirectory("cap4k-override-empty-query-handler-empty-response")
        val renderer = PebbleArtifactRenderer(
            templateResolver = PresetTemplateResolver(
                preset = "ddd-default",
                overrideDirs = listOf(overrideDir.toString())
            )
        )

        val rendered = renderer.render(
            planItems = listOf(
                ArtifactPlanItem(
                    generatorId = "query-handler",
                    moduleRole = "adapter",
                    templateId = "design/query_handler.kt.peb",
                    outputPath = "demo-adapter/src/main/kotlin/com/acme/demo/adapter/queries/order/read/FindOrderQryHandler.kt",
                    context = mapOf(
                        "packageName" to "com.acme.demo.adapter.queries.order.read",
                        "typeName" to "FindOrderQryHandler",
                        "description" to "find order query",
                        "queryTypeName" to "FindOrderQry",
                        "imports" to listOf("com.acme.demo.application.queries.order.read.FindOrderQry"),
                        "resultFields" to emptyList<Map<String, Any?>>(),
                    ),
                    conflictPolicy = ConflictPolicy.SKIP
                ),
                ArtifactPlanItem(
                    generatorId = "query-handler",
                    moduleRole = "adapter",
                    templateId = "design/query_handler.kt.peb",
                    outputPath = "demo-adapter/src/main/kotlin/com/acme/demo/adapter/queries/order/read/FindOrderListQryHandler.kt",
                    context = mapOf(
                        "packageName" to "com.acme.demo.adapter.queries.order.read",
                        "typeName" to "FindOrderListQryHandler",
                        "description" to "find order list query",
                        "queryTypeName" to "FindOrderListQry",
                        "imports" to listOf("com.acme.demo.application.queries.order.read.FindOrderListQry"),
                        "resultFields" to emptyList<Map<String, Any?>>(),
                    ),
                    conflictPolicy = ConflictPolicy.SKIP
                ),
                ArtifactPlanItem(
                    generatorId = "query-handler",
                    moduleRole = "adapter",
                    templateId = "design/query_handler.kt.peb",
                    outputPath = "demo-adapter/src/main/kotlin/com/acme/demo/adapter/queries/order/read/FindOrderPageQryHandler.kt",
                    context = mapOf(
                        "packageName" to "com.acme.demo.adapter.queries.order.read",
                        "typeName" to "FindOrderPageQryHandler",
                        "description" to "find order page query",
                        "queryTypeName" to "FindOrderPageQry",
                        "imports" to listOf("com.acme.demo.application.queries.order.read.FindOrderPageQry"),
                        "resultFields" to emptyList<Map<String, Any?>>(),
                    ),
                    conflictPolicy = ConflictPolicy.SKIP
                )
            ),
            config = ProjectConfig(
                basePackage = "com.acme.demo",
                layout = ProjectLayout.MULTI_MODULE,
                modules = emptyMap(),
                sources = emptyMap(),
                generators = emptyMap(),
                templates = TemplateConfig(
                    preset = "ddd-default",
                    overrideDirs = listOf(overrideDir.toString()),
                    conflictPolicy = ConflictPolicy.SKIP
                )
            )
        )

        val defaultContent = rendered[0].content
        assertTrue(defaultContent.contains("return FindOrderQry.Response"))
        assertFalse(defaultContent.contains("return FindOrderQry.Response("))

        val listContent = rendered[1].content
        assertTrue(listContent.contains("return FindOrderListQry.Response"))
        assertFalse(listContent.contains("return FindOrderListQry.Response("))

        val pageContent = rendered[2].content
        assertTrue(pageContent.contains("return FindOrderPageQry.Response"))
        assertFalse(pageContent.contains("return FindOrderPageQry.Response("))
    }

    @Test
    fun `default capability preset uses capability call contract and helper-driven fields`() {
        val overrideDir = Files.createTempDirectory("cap4k-override-empty-design-capability-contract")
        val renderer = PebbleArtifactRenderer(
            templateResolver = PresetTemplateResolver(
                preset = "ddd-default",
                overrideDirs = listOf(overrideDir.toString())
            )
        )

        val rendered = renderer.render(
            planItems = listOf(
                ArtifactPlanItem(
                    generatorId = "capability",
                    moduleRole = "application",
                    templateId = "design/capability.kt.peb",
                    outputPath = "demo-application/src/main/kotlin/com/acme/demo/application/capabilities/authorize/IssueToken.kt",
                    context = mapOf(
                        "packageName" to "com.acme.demo.application.capabilities.authorize",
                        "typeName" to "IssueToken",
                        "imports" to listOf(
                            "java.time.LocalDateTime",
                            "java.util.UUID",
                        ),
                        "fields" to listOf(
                            mapOf("name" to "account", "renderedType" to "String", "nullable" to false, "defaultValue" to "\"guest\""),
                            mapOf("name" to "issuedAt", "renderedType" to "LocalDateTime", "nullable" to false),
                            mapOf("name" to "requestStatus", "renderedType" to "com.foo.Status", "nullable" to false),
                            mapOf("name" to "profile", "renderedType" to "Profile?", "nullable" to true),
                        ),
                        "nestedTypes" to listOf(
                            mapOf(
                                "name" to "Profile",
                                "fields" to listOf(
                                    mapOf("name" to "profileId", "renderedType" to "UUID", "nullable" to false),
                                    mapOf("name" to "source", "renderedType" to "String", "nullable" to false, "defaultValue" to "\"web\""),
                                ),
                            ),
                        ),
                        "resultFields" to listOf(
                            mapOf("name" to "token", "renderedType" to "String", "nullable" to false, "defaultValue" to "\"demo-token\""),
                            mapOf("name" to "expiresAt", "renderedType" to "LocalDateTime", "nullable" to false),
                            mapOf("name" to "responseStatus", "renderedType" to "com.bar.Status", "nullable" to false),
                            mapOf("name" to "payload", "renderedType" to "Payload?", "nullable" to true),
                        ),
                        "resultNestedTypes" to listOf(
                            mapOf(
                                "name" to "Payload",
                                "fields" to listOf(
                                    mapOf("name" to "traceId", "renderedType" to "UUID", "nullable" to false),
                                ),
                            ),
                        ),
                    ),
                    conflictPolicy = ConflictPolicy.SKIP
                )
            ),
            config = ProjectConfig(
                basePackage = "com.acme.demo",
                layout = ProjectLayout.MULTI_MODULE,
                modules = emptyMap(),
                sources = emptyMap(),
                generators = emptyMap(),
                templates = TemplateConfig(
                    preset = "ddd-default",
                    overrideDirs = listOf(overrideDir.toString()),
                    conflictPolicy = ConflictPolicy.SKIP
                )
            )
        )

        val content = rendered.single().content
        assertTrue(content.contains("import com.only4.cap4k.ddd.core.application.capability.CapabilityCall"))
        assertTrue(content.contains("import java.time.LocalDateTime"))
        assertTrue(content.contains("import java.util.UUID"))
        assertFalse(content.contains("import com.foo.Status"))
        assertFalse(content.contains("import com.bar.Status"))
        assertTrue(content.contains("object IssueToken"))
        assertTrue(content.contains(") : CapabilityCall<Response>"))
        assertTrue(content.contains("val account: String = \"guest\""))
        assertTrue(content.contains("val issuedAt: LocalDateTime"))
        assertTrue(content.contains("val requestStatus: com.foo.Status"))
        assertTrue(content.contains("val profile: Profile?"))
        assertFalse(content.contains("val profile: Profile??"))
        assertTrue(content.contains("data class Profile("))
        assertTrue(content.contains("val profileId: UUID"))
        assertTrue(content.contains("val source: String = \"web\""))
        assertTrue(content.contains("val token: String = \"demo-token\""))
        assertTrue(content.contains("val expiresAt: LocalDateTime"))
        assertTrue(content.contains("val responseStatus: com.bar.Status"))
        assertTrue(content.contains("val payload: Payload?"))
        assertFalse(content.contains("val payload: Payload??"))
        assertTrue(content.contains("data class Payload("))
        assertTrue(content.contains("val traceId: UUID"))
    }

    @Test
    fun `default capability handler preset renders capability handler contract and import list type`() {
        val overrideDir = Files.createTempDirectory("cap4k-override-empty-design-capability-handler-contract")
        val renderer = PebbleArtifactRenderer(
            templateResolver = PresetTemplateResolver(
                preset = "ddd-default",
                overrideDirs = listOf(overrideDir.toString())
            )
        )

        val rendered = renderer.render(
            planItems = listOf(
                ArtifactPlanItem(
                    generatorId = "capability-handler",
                    moduleRole = "adapter",
                    templateId = "design/capability_handler.kt.peb",
                    outputPath = "demo-adapter/src/main/kotlin/com/acme/demo/adapter/application/capabilities/authorize/IssueTokenHandler.kt",
                    context = mapOf(
                        "packageName" to "com.acme.demo.adapter.application.capabilities.authorize",
                        "typeName" to "IssueTokenHandler",
                        "capabilityTypeName" to "IssueToken",
                        "imports" to listOf("com.acme.demo.application.capabilities.authorize.IssueToken"),
                        "resultFields" to listOf(
                            mapOf("name" to "token"),
                            mapOf("name" to "expiresAt"),
                        ),
                    ),
                    conflictPolicy = ConflictPolicy.SKIP
                )
            ),
            config = ProjectConfig(
                basePackage = "com.acme.demo",
                layout = ProjectLayout.MULTI_MODULE,
                modules = emptyMap(),
                sources = emptyMap(),
                generators = emptyMap(),
                templates = TemplateConfig(
                    preset = "ddd-default",
                    overrideDirs = listOf(overrideDir.toString()),
                    conflictPolicy = ConflictPolicy.SKIP
                )
            )
        )

        val content = rendered.single().content
        assertTrue(content.contains("import org.springframework.stereotype.Service"))
        assertTrue(content.contains("import com.only4.cap4k.ddd.core.application.capability.CapabilityHandler"))
        assertTrue(content.contains("import com.acme.demo.application.capabilities.authorize.IssueToken"))
        assertTrue(content.contains("class IssueTokenHandler : CapabilityHandler<IssueToken.Request, IssueToken.Response>"))
        assertTrue(content.contains("override fun call(request: IssueToken.Request): IssueToken.Response"))
        assertTrue(content.contains("token = TODO(\"set token\")"))
        assertTrue(content.contains("expiresAt = TODO(\"set expiresAt\")"))
        val normalizedContent = content.normalizedLineEndings()
        assertTrue(normalizedContent.contains("package com.acme.demo.adapter.application.capabilities.authorize\n\nimport"))
        assertTrue(
            normalizedContent.contains(
                "        return IssueToken.Response(\n" +
                    "            token = TODO(\"set token\"),\n" +
                    "            expiresAt = TODO(\"set expiresAt\")\n" +
                    "        )"
            )
        )
        assertFalse(Regex("""Response\(\n\s*\n""").containsMatchIn(normalizedContent))
        assertFalse(Regex("""TODO\("set token"\),\n\s*\n\s*expiresAt""").containsMatchIn(normalizedContent))
        assertFalse(Regex("""TODO\("set expiresAt"\)\n\s*\n\s*\)""").containsMatchIn(normalizedContent))
    }

    @Test
    fun `capability presets keep empty response output valid for request side and handler side`() {
        val overrideDir = Files.createTempDirectory("cap4k-override-empty-design-capability-empty-response")
        val renderer = PebbleArtifactRenderer(
            templateResolver = PresetTemplateResolver(
                preset = "ddd-default",
                overrideDirs = listOf(overrideDir.toString())
            )
        )

        val rendered = renderer.render(
            planItems = listOf(
                ArtifactPlanItem(
                    generatorId = "capability",
                    moduleRole = "application",
                    templateId = "design/capability.kt.peb",
                    outputPath = "demo-application/src/main/kotlin/com/acme/demo/application/capabilities/authorize/IssueToken.kt",
                    context = mapOf(
                        "packageName" to "com.acme.demo.application.capabilities.authorize",
                        "typeName" to "IssueToken",
                        "imports" to emptyList<String>(),
                        "fields" to listOf(
                            mapOf("name" to "account", "renderedType" to "String", "nullable" to false),
                        ),
                        "nestedTypes" to emptyList<Map<String, Any?>>(),
                        "resultFields" to emptyList<Map<String, Any?>>(),
                        "resultNestedTypes" to emptyList<Map<String, Any?>>(),
                    ),
                    conflictPolicy = ConflictPolicy.SKIP
                ),
                ArtifactPlanItem(
                    generatorId = "capability-handler",
                    moduleRole = "adapter",
                    templateId = "design/capability_handler.kt.peb",
                    outputPath = "demo-adapter/src/main/kotlin/com/acme/demo/adapter/application/capabilities/authorize/IssueTokenHandler.kt",
                    context = mapOf(
                        "packageName" to "com.acme.demo.adapter.application.capabilities.authorize",
                        "typeName" to "IssueTokenHandler",
                        "capabilityTypeName" to "IssueToken",
                        "imports" to listOf("com.acme.demo.application.capabilities.authorize.IssueToken"),
                        "resultFields" to emptyList<Map<String, Any?>>(),
                    ),
                    conflictPolicy = ConflictPolicy.SKIP
                )
            ),
            config = ProjectConfig(
                basePackage = "com.acme.demo",
                layout = ProjectLayout.MULTI_MODULE,
                modules = emptyMap(),
                sources = emptyMap(),
                generators = emptyMap(),
                templates = TemplateConfig(
                    preset = "ddd-default",
                    overrideDirs = listOf(overrideDir.toString()),
                    conflictPolicy = ConflictPolicy.SKIP
                )
            )
        )

        val capabilityContent = rendered[0].content
        assertTrue(capabilityContent.contains(") : CapabilityCall<Response>"))
        assertTrue(capabilityContent.contains("data object Response"))

        val handlerContent = rendered[1].content
        assertTrue(handlerContent.contains("class IssueTokenHandler : CapabilityHandler<IssueToken.Request, IssueToken.Response>"))
        assertTrue(handlerContent.contains("return IssueToken.Response"))
        assertFalse(handlerContent.contains("return IssueToken.Response("))
    }

    @Test
    fun `api payload preset renders outer object helper imports nested request and response hierarchy and defaults`() {
        val overrideDir = Files.createTempDirectory("cap4k-override-empty-design-api-payload-contract")
        val renderer = PebbleArtifactRenderer(
            templateResolver = PresetTemplateResolver(
                preset = "ddd-default",
                overrideDirs = listOf(overrideDir.toString())
            )
        )

        val rendered = renderer.render(
            planItems = listOf(
                ArtifactPlanItem(
                    generatorId = "api-payload",
                    moduleRole = "adapter",
                    templateId = "design/api_payload.kt.peb",
                    outputPath = "demo-adapter/src/main/kotlin/com/acme/demo/adapter/portal/api/payload/account/BatchSaveAccountList.kt",
                    context = mapOf(
                        "packageName" to "com.acme.demo.adapter.portal.api.payload.account",
                        "typeName" to "BatchSaveAccountList",
                        "imports" to listOf(
                            "  java.time.LocalDateTime  ",
                            "\tjava.util.UUID",
                            "java.time.LocalDateTime",
                            "java.util.UUID  ",
                            "  ",
                        ),
                        "fields" to listOf(
                            mapOf("name" to "address", "renderedType" to "Address?", "nullable" to true),
                            mapOf("name" to "note", "renderedType" to "String", "nullable" to false, "defaultValue" to "\"demo\""),
                            mapOf("name" to "requestedAt", "renderedType" to "LocalDateTime", "nullable" to false),
                        ),
                        "nestedTypes" to listOf(
                            mapOf(
                                "name" to "Address",
                                "fields" to listOf(
                                    mapOf("name" to "city", "renderedType" to "String", "nullable" to false),
                                    mapOf("name" to "zipCode", "renderedType" to "String", "nullable" to false, "defaultValue" to "\"000000\""),
                                ),
                            ),
                        ),
                        "resultFields" to listOf(
                            mapOf("name" to "result", "renderedType" to "Result?", "nullable" to true),
                            mapOf("name" to "code", "renderedType" to "String", "nullable" to false, "defaultValue" to "\"ok\""),
                            mapOf("name" to "responseId", "renderedType" to "UUID", "nullable" to false),
                        ),
                        "resultNestedTypes" to listOf(
                            mapOf(
                                "name" to "Result",
                                "fields" to listOf(
                                    mapOf("name" to "success", "renderedType" to "Boolean", "nullable" to false, "defaultValue" to "true"),
                                ),
                            ),
                        ),
                    ),
                    conflictPolicy = ConflictPolicy.SKIP
                )
            ),
            config = ProjectConfig(
                basePackage = "com.acme.demo",
                layout = ProjectLayout.MULTI_MODULE,
                modules = emptyMap(),
                sources = emptyMap(),
                generators = emptyMap(),
                templates = TemplateConfig(
                    preset = "ddd-default",
                    overrideDirs = listOf(overrideDir.toString()),
                    conflictPolicy = ConflictPolicy.SKIP
                )
            )
        )

        val content = rendered.single().content
        val importLines = Regex("^import .+$", RegexOption.MULTILINE).findAll(content).map { it.value }.toList()
        val responseIndex = content.indexOf("    data class Response(")
        val requestSection = content.substring(
            startIndex = content.indexOf("    data class Request("),
            endIndex = responseIndex
        )
        val responseSection = content.substring(responseIndex)
        val nestedAddressCount = Regex("^ {8}data class Address\\(", RegexOption.MULTILINE).findAll(content).count()
        val outerAddressCount = Regex("^ {4}data class Address\\(", RegexOption.MULTILINE).findAll(content).count()
        val nestedResultCount = Regex("^ {8}data class Result\\(", RegexOption.MULTILINE).findAll(content).count()
        val outerResultCount = Regex("^ {4}data class Result\\(", RegexOption.MULTILINE).findAll(content).count()

        assertTrue(content.contains("package com.acme.demo.adapter.portal.api.payload.account"))
        assertEquals(
            listOf(
                "import java.time.LocalDateTime",
                "import java.util.UUID",
            ),
            importLines
        )
        assertTrue(content.contains("object BatchSaveAccountList"))
        assertTrue(responseIndex >= 0)
        assertTrue(content.contains("val address: Address?"))
        assertFalse(content.contains("val address: Address??"))
        assertTrue(content.contains("val note: String = \"demo\""))
        assertTrue(content.contains("val requestedAt: LocalDateTime"))
        assertTrue(content.contains("val result: Result?"))
        assertFalse(content.contains("val result: Result??"))
        assertTrue(content.contains("val code: String = \"ok\""))
        assertTrue(content.contains("val responseId: UUID"))
        assertEquals(1, nestedAddressCount)
        assertEquals(0, outerAddressCount)
        assertTrue(requestSection.contains("data class Address("))
        assertTrue(requestSection.contains("val city: String"))
        assertTrue(requestSection.contains("val zipCode: String = \"000000\""))
        assertFalse(requestSection.contains("data class Result("))
        assertEquals(1, nestedResultCount)
        assertEquals(0, outerResultCount)
        assertTrue(responseSection.contains("data class Result("))
        assertTrue(responseSection.contains("val success: Boolean = true"))
        assertFalse(responseSection.contains("data class Address("))
    }

    @Test
    fun `api payload preset supports override template resolution for design api payload`() {
        val overrideDir = Files.createTempDirectory("cap4k-override-design-api-payload")
        val overrideDesignDir = Files.createDirectories(overrideDir.resolve("design"))
        overrideDesignDir.resolve("api_payload.kt.peb").writeText(
            """
            // override: renderer api payload template
            package {{ packageName }}
            object {{ typeName }}Override
            """.trimIndent()
        )

        val renderer = PebbleArtifactRenderer(
            templateResolver = PresetTemplateResolver(
                preset = "ddd-default",
                overrideDirs = listOf(overrideDir.toString())
            )
        )

        val rendered = renderer.render(
            planItems = listOf(
                ArtifactPlanItem(
                    generatorId = "api-payload",
                    moduleRole = "adapter",
                    templateId = "design/api_payload.kt.peb",
                    outputPath = "demo-adapter/src/main/kotlin/com/acme/demo/adapter/portal/api/payload/account/BatchSaveAccountList.kt",
                    context = mapOf(
                        "packageName" to "com.acme.demo.adapter.portal.api.payload.account",
                        "typeName" to "BatchSaveAccountList",
                        "imports" to emptyList<String>(),
                        "fields" to emptyList<Map<String, Any?>>(),
                        "nestedTypes" to emptyList<Map<String, Any?>>(),
                        "resultFields" to emptyList<Map<String, Any?>>(),
                        "resultNestedTypes" to emptyList<Map<String, Any?>>(),
                    ),
                    conflictPolicy = ConflictPolicy.SKIP
                )
            ),
            config = ProjectConfig(
                basePackage = "com.acme.demo",
                layout = ProjectLayout.MULTI_MODULE,
                modules = emptyMap(),
                sources = emptyMap(),
                generators = emptyMap(),
                templates = TemplateConfig(
                    preset = "ddd-default",
                    overrideDirs = listOf(overrideDir.toString()),
                    conflictPolicy = ConflictPolicy.SKIP
                )
            )
        )

        val content = rendered.single().content
        assertTrue(content.contains("// override: renderer api payload template"))
        assertTrue(content.contains("object BatchSaveAccountListOverride"))
    }

    @Test
    fun `domain event preset resolves domain event template and renders helper-first contract`() {
        val overrideDir = Files.createTempDirectory("cap4k-override-empty-design-domain-event")
        val renderer = PebbleArtifactRenderer(
            templateResolver = PresetTemplateResolver(
                preset = "ddd-default",
                overrideDirs = listOf(overrideDir.toString())
            )
        )

        val rendered = renderer.render(
            planItems = listOf(
                ArtifactPlanItem(
                    generatorId = "domain-event",
                    moduleRole = "domain",
                    templateId = "design/domain_event.kt.peb",
                    outputPath = "demo-domain/src/main/kotlin/com/acme/demo/domain/order/events/OrderCreatedDomainEvent.kt",
                    context = mapOf(
                        "packageName" to "com.acme.demo.domain.order.events",
                        "typeName" to "OrderCreatedDomainEvent",
                        "description" to "order */ \"created\" event",
                        "descriptionText" to "order */ \"created\" event",
                        "descriptionCommentText" to "order * / \"created\" event",
                        "descriptionKotlinStringLiteral" to "\"order */ \\\"created\\\" event\"",
                        "eventName" to "order.\"created\"\\${'$'}event",
                        "eventNameKotlinStringLiteral" to "order.\"created\"\\${'$'}event".toTestKotlinStringLiteral(),
                        "persist" to true,
                        "imports" to listOf("java.util.UUID"),
                        "fields" to listOf(
                            mapOf(
                                "name" to "reason",
                                "renderedType" to "String",
                                "nullable" to false,
                                "defaultValue" to "\"manual\"",
                            ),
                            mapOf(
                                "name" to "snapshot",
                                "renderedType" to "Snapshot?",
                                "nullable" to true,
                                "defaultValue" to "null",
                            ),
                        ),
                        "nestedTypes" to listOf(
                            mapOf(
                                "name" to "Snapshot",
                                "fields" to listOf(
                                    mapOf(
                                        "name" to "traceId",
                                        "renderedType" to "UUID",
                                        "nullable" to false,
                                        "defaultValue" to "UUID(0L, 0L)",
                                    ),
                                ),
                            ),
                        ),
                    ),
                    conflictPolicy = ConflictPolicy.SKIP
                )
            ),
            config = ProjectConfig(
                basePackage = "com.acme.demo",
                layout = ProjectLayout.MULTI_MODULE,
                modules = emptyMap(),
                sources = emptyMap(),
                generators = emptyMap(),
                templates = TemplateConfig(
                    preset = "ddd-default",
                    overrideDirs = listOf(overrideDir.toString()),
                    conflictPolicy = ConflictPolicy.SKIP
                )
            )
        )

        val content = rendered.single().content
        val normalizedContent = content.replace("\r\n", "\n")
        assertTrue(content.contains("@DomainEvent"))
        assertTrue(
            content.contains(
                "@DomainEvent(value = ${"order.\"created\"\\${'$'}event".toTestKotlinStringLiteral()}, persist = true)",
            ),
        )
        assertFalse(content.contains("@DesignBlockMetadata("))
        assertFalse(content.contains(legacyAggregateCall))
        assertTrue(content.contains("* order * / \"created\" event"))
        assertFalse(content.contains("* order */ \"created\" event"))
        assertFalse(content.contains("description = "))
        assertFalse(content.contains("&quot;"))
        assertTrue(content.contains("class OrderCreatedDomainEvent("))
        assertTrue(content.contains("val reason: String = \"manual\""))
        assertTrue(content.contains("val snapshot: Snapshot? = null"))
        assertFalse(content.contains("val entity:"))
        assertFalse(content.contains("import com.acme.demo.domain.order.Order"))
        assertTrue(content.contains("data class Snapshot("))
        assertTrue(content.contains("val traceId: UUID = UUID(0L, 0L)"))
        assertTrue(
            normalizedContent.contains("package com.acme.demo.domain.order.events\n\nimport"),
            "domain event should keep one blank line between package and imports"
        )
        val importBlock = normalizedContent.substringAfter("package com.acme.demo.domain.order.events\n").substringBefore("/**")
        assertFalse(importBlock.contains("\n\nimport"), "domain event imports should not contain blank lines between imports")
        assertFalse(
            normalizedContent.contains("class OrderCreatedDomainEvent(\n\n    val reason: String"),
            "domain event constructor fields should not start with a blank line"
        )
    }

    @Test
    fun `domain event preset renders an empty explicit payload as a marker event`() {
        val content = renderTemplate(
            templateId = "design/domain_event.kt.peb",
            outputPath = "demo-domain/src/main/kotlin/com/acme/demo/domain/order/events/OrderReconciledDomainEvent.kt",
            context = mapOf(
                "packageName" to "com.acme.demo.domain.order.events",
                "typeName" to "OrderReconciledDomainEvent",
                "descriptionCommentText" to "order reconciled event",
                "persist" to false,
                "imports" to emptyList<String>(),
                "fields" to emptyList<Map<String, Any?>>(),
                "nestedTypes" to emptyList<Map<String, Any?>>(),
            ),
        )

        assertTrue(content.contains("class OrderReconciledDomainEvent {"))
        assertFalse(content.contains("class OrderReconciledDomainEvent("))
        assertFalse(content.contains("val entity:"))
        assertFalse(content.contains("import com.acme.demo.domain.order.Order"))
    }

    @Test
    fun `domain event preset resolves domain event handler template and renders event listener contract`() {
        val overrideDir = Files.createTempDirectory("cap4k-override-empty-domain-subscriber")
        val renderer = PebbleArtifactRenderer(
            templateResolver = PresetTemplateResolver(
                preset = "ddd-default",
                overrideDirs = listOf(overrideDir.toString())
            )
        )

        val rendered = renderer.render(
            planItems = listOf(
                ArtifactPlanItem(
                    generatorId = "domain-subscriber",
                    moduleRole = "application",
                    templateId = "design/domain_event_handler.kt.peb",
                    outputPath = "demo-application/src/main/kotlin/com/acme/demo/application/subscribers/domain/order/OrderCreatedDomainEventSubscriber.kt",
                    context = mapOf(
                        "packageName" to "com.acme.demo.application.subscribers.domain.order",
                        "typeName" to "OrderCreatedDomainEventSubscriber",
                        "domainEventTypeName" to "OrderCreatedDomainEvent",
                        "domainEventType" to "com.acme.demo.domain.order.events.OrderCreatedDomainEvent",
                        "aggregateName" to "Order",
                        "description" to "order */ created event",
                        "descriptionCommentText" to "order * / created event",
                        "imports" to listOf("com.acme.demo.domain.order.events.OrderCreatedDomainEvent"),
                    ),
                    conflictPolicy = ConflictPolicy.SKIP
                )
            ),
            config = ProjectConfig(
                basePackage = "com.acme.demo",
                layout = ProjectLayout.MULTI_MODULE,
                modules = emptyMap(),
                sources = emptyMap(),
                generators = emptyMap(),
                templates = TemplateConfig(
                    preset = "ddd-default",
                    overrideDirs = listOf(overrideDir.toString()),
                    conflictPolicy = ConflictPolicy.SKIP
                )
            )
        )

        val content = rendered.single().content
        val normalizedContent = content.replace("\r\n", "\n")
        assertTrue(content.contains("@Service"))
        assertTrue(content.contains("@EventListener(OrderCreatedDomainEvent::class)"))
        assertTrue(content.contains("* order * / created event"))
        assertFalse(content.contains("* order */ created event"))
        assertTrue(content.contains("class OrderCreatedDomainEventSubscriber"))
        assertTrue(content.contains("import com.acme.demo.domain.order.events.OrderCreatedDomainEvent"))
        assertTrue(
            normalizedContent.contains("package com.acme.demo.application.subscribers.domain.order\n\nimport"),
            "domain event handler should keep one blank line between package and imports"
        )
        assertFalse(
            normalizedContent.contains("OrderCreatedDomainEvent\n\nimport"),
            "domain event handler imports should not contain blank lines between imports"
        )
    }

    @Test
    fun `domain event preset renders building block metadata when provided`() {
        val overrideDir = Files.createTempDirectory("cap4k-override-building-block-design-domain-event")
        val renderer = PebbleArtifactRenderer(
            templateResolver = PresetTemplateResolver(
                preset = "ddd-default",
                overrideDirs = listOf(overrideDir.toString())
            )
        )

        val rendered = renderer.render(
            planItems = listOf(
                ArtifactPlanItem(
                    generatorId = "domain-event",
                    moduleRole = "domain",
                    templateId = "design/domain_event.kt.peb",
                    outputPath = "demo-domain/src/main/kotlin/com/acme/demo/domain/order/events/OrderCreatedDomainEvent.kt",
                    context = mapOf(
                        "packageName" to "com.acme.demo.domain.order.events",
                        "typeName" to "OrderCreatedDomainEvent",
                        "buildingBlock" to buildingBlockContext(
                            tag = "domain_event",
                            name = "OrderCreatedDomainEvent",
                            family = "domain-event",
                            packageName = "or\"der\\pkg ${'$'}status",
                            description = "order */ \"created\" \\event ${'$'}status",
                            aggregates = listOf("Or\"der\\${'$'}status"),
                            eventName = "order.\"created\"\\${'$'}event",
                        ),
                        "description" to "order */ \"created\" \\event ${'$'}status",
                        "descriptionText" to "order */ \"created\" \\event ${'$'}status",
                        "descriptionCommentText" to "order * / \"created\" \\event ${'$'}status",
                        "descriptionKotlinStringLiteral" to "\"order */ \\\"created\\\" \\\\event \\${'$'}status\"",
                        "eventName" to "order.\"created\"\\${'$'}event",
                        "eventNameKotlinStringLiteral" to "order.\"created\"\\${'$'}event".toTestKotlinStringLiteral(),
                        "persist" to true,
                        "imports" to listOf("java.util.UUID"),
                        "fields" to listOf(
                            mapOf("name" to "reason", "renderedType" to "String", "nullable" to false),
                        ),
                        "nestedTypes" to emptyList<Map<String, Any?>>(),
                    ),
                    conflictPolicy = ConflictPolicy.SKIP
                )
            ),
            config = ProjectConfig(
                basePackage = "com.acme.demo",
                layout = ProjectLayout.MULTI_MODULE,
                modules = emptyMap(),
                sources = emptyMap(),
                generators = emptyMap(),
                templates = TemplateConfig(
                    preset = "ddd-default",
                    overrideDirs = listOf(overrideDir.toString()),
                    conflictPolicy = ConflictPolicy.SKIP
                )
            )
        )

        val content = rendered.single().content
        assertTrue(content.contains("import com.only4.cap4k.analysis.metadata.DesignBlockMetadata"))
        assertTrue(content.contains("@DesignBlockMetadata("))
        assertTrue(content.contains("tag = \"domain_event\""))
        assertTrue(content.contains("name = \"OrderCreatedDomainEvent\""))
        assertTrue(content.contains("packageName = ${"or\"der\\pkg ${'$'}status".toTestKotlinStringLiteral()}"))
        assertTrue(content.contains("description = \"order */ \\\"created\\\" \\\\event \\${'$'}status\""))
        assertTrue(content.contains("aggregates = [${"Or\"der\\${'$'}status".toTestKotlinStringLiteral()}]"))
        assertTrue(content.contains("eventName = ${"order.\"created\"\\${'$'}event".toTestKotlinStringLiteral()}"))
        assertTrue(
            content.contains(
                "@DomainEvent(value = ${"order.\"created\"\\${'$'}event".toTestKotlinStringLiteral()}, persist = true)",
            ),
        )
        assertTrue(content.contains("family = \"domain-event\""))
        assertFalse(content.contains("variant = \"\""))
        assertFalse(content.contains("&quot;"))
    }

    @Test
    fun `design authoring templates render building block metadata when provided`() {
        val payload = renderTemplate(
            templateId = "design/api_payload.kt.peb",
            outputPath = "demo-application/src/main/kotlin/com/acme/demo/application/api/order/OrderPayload.kt",
            context = mapOf(
                "packageName" to "com.acme.demo.application.api.order",
                "typeName" to "OrderPayload",
                "pageRequest" to true,
                "fields" to listOf(mapOf("name" to "keyword", "renderedType" to "String?", "nullable" to true)),
                "nestedTypes" to emptyList<Map<String, Any?>>(),
                "resultFields" to listOf(mapOf("name" to "orderNo", "renderedType" to "String", "nullable" to false)),
                "resultNestedTypes" to emptyList<Map<String, Any?>>(),
                "imports" to emptyList<String>(),
                "buildingBlock" to buildingBlockContext(
                    tag = "api_payload",
                    name = "OrderPayload",
                    packageName = "order",
                    family = "api-payload",
                    variant = "page",
                    aggregates = listOf("Order"),
                ),
            ),
        )
        assertBuildingBlockAnnotation(payload, tag = "api_payload", name = "OrderPayload", family = "api-payload", variant = "page")

        val domainSubscriber = renderTemplate(
            templateId = "design/domain_event_handler.kt.peb",
            outputPath = "demo-application/src/main/kotlin/com/acme/demo/application/subscribers/domain/order/OrderCreatedDomainEventSubscriber.kt",
            context = mapOf(
                "packageName" to "com.acme.demo.application.subscribers.domain.order",
                "typeName" to "OrderCreatedDomainEventSubscriber",
                "domainEventTypeName" to "OrderCreatedDomainEvent",
                "domainEventType" to "com.acme.demo.domain.order.events.OrderCreatedDomainEvent",
                "descriptionCommentText" to "order created",
                "imports" to listOf("com.acme.demo.domain.order.events.OrderCreatedDomainEvent"),
                "buildingBlock" to buildingBlockContext(
                    tag = "domain_event",
                    name = "OrderCreatedDomainEvent",
                    packageName = "order",
                    family = "domain-subscriber",
                    aggregates = listOf("Order"),
                ),
            ),
        )
        assertBuildingBlockAnnotation(
            domainSubscriber,
            tag = "domain_event",
            name = "OrderCreatedDomainEvent",
            family = "domain-subscriber",
        )

        val integrationEvent = renderTemplate(
            templateId = "design/integration_event.kt.peb",
            outputPath = "demo-application/src/main/kotlin/com/acme/demo/application/events/integration/inbound/order/OrderAcceptedIntegrationEvent.kt",
            context = mapOf(
                "packageName" to "com.acme.demo.application.events.integration.inbound.order",
                "typeName" to "OrderAcceptedIntegrationEvent",
                "descriptionCommentText" to "order accepted",
                "eventName" to "order.accepted",
                "eventNameKotlinStringLiteral" to "\"order.accepted\"",
                "inbound" to true,
                "outbound" to false,
                "imports" to emptyList<String>(),
                "fields" to listOf(mapOf("name" to "orderId", "renderedType" to "String", "nullable" to false)),
                "nestedTypes" to emptyList<Map<String, Any?>>(),
                "buildingBlock" to buildingBlockContext(
                    tag = "integration_event",
                    name = "OrderAcceptedIntegrationEvent",
                    packageName = "order",
                    eventName = "order.accepted",
                    family = "integration-event",
                    variant = "inbound",
                    aggregates = listOf("Order"),
                ),
            ),
        )
        assertBuildingBlockAnnotation(
            integrationEvent,
            tag = "integration_event",
            name = "OrderAcceptedIntegrationEvent",
            family = "integration-event",
            variant = "inbound",
            eventName = "order.accepted",
        )

        val integrationSubscriber = renderTemplate(
            templateId = "design/integration_event_subscriber.kt.peb",
            outputPath = "demo-application/src/main/kotlin/com/acme/demo/application/subscribers/integration/inbound/order/OrderAcceptedIntegrationEventSubscriber.kt",
            context = mapOf(
                "packageName" to "com.acme.demo.application.subscribers.integration.inbound.order",
                "typeName" to "OrderAcceptedIntegrationEventSubscriber",
                "eventTypeName" to "OrderAcceptedIntegrationEvent",
                "eventType" to "com.acme.demo.application.events.integration.inbound.order.OrderAcceptedIntegrationEvent",
                "descriptionCommentText" to "order accepted",
                "imports" to listOf("com.acme.demo.application.events.integration.inbound.order.OrderAcceptedIntegrationEvent"),
                "buildingBlock" to buildingBlockContext(
                    tag = "integration_event",
                    name = "OrderAcceptedIntegrationEvent",
                    packageName = "order",
                    eventName = "order.accepted",
                    family = "integration-subscriber",
                    aggregates = listOf("Order"),
                ),
            ),
        )
        assertBuildingBlockAnnotation(
            integrationSubscriber,
            tag = "integration_event",
            name = "OrderAcceptedIntegrationEvent",
            family = "integration-subscriber",
        )

        val domainService = renderTemplate(
            templateId = "design/domain_service.kt.peb",
            outputPath = "demo-domain/src/main/kotlin/com/acme/demo/domain/order/OrderPublicationPolicy.kt",
            context = mapOf(
                "packageName" to "com.acme.demo.domain.order",
                "name" to "OrderPublicationPolicy",
                "imports" to emptyList<String>(),
                "buildingBlock" to buildingBlockContext(
                    tag = "domain_service",
                    name = "OrderPublicationPolicy",
                    packageName = "order",
                    family = "domain-service",
                    aggregates = listOf("Order"),
                ),
            ),
        )
        assertBuildingBlockAnnotation(domainService, tag = "domain_service", name = "OrderPublicationPolicy", family = "domain-service")

    }

    @Test
    fun `enum template renders building block only when manifest context is supplied`() {
        val manifestEnum = renderTemplate(
            templateId = "aggregate/enum.kt.peb",
            outputPath = "demo-domain/src/main/kotlin/com/acme/demo/domain/order/enums/OrderStatus.kt",
            context = mapOf(
                "packageName" to "com.acme.demo.domain.order.enums",
                "typeName" to "OrderStatus",
                "imports" to emptyList<String>(),
                "items" to listOf(
                    mapOf("value" to 0, "name" to "DRAFT", "description" to "Draft"),
                    mapOf("value" to 1, "name" to "SUBMITTED", "description" to "Submitted"),
                ),
                "buildingBlock" to buildingBlockContext(
                    tag = "enum",
                    name = "OrderStatus",
                    packageName = "order",
                    family = "enum",
                    aggregates = listOf("Order"),
                ),
            ),
        )
        assertBuildingBlockAnnotation(manifestEnum, tag = "enum", name = "OrderStatus", family = "enum")

        val dbDerivedEnum = renderTemplate(
            templateId = "aggregate/enum.kt.peb",
            outputPath = "demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/order/enums/Visibility.kt",
            context = mapOf(
                "packageName" to "com.acme.demo.domain.aggregates.order.enums",
                "typeName" to "Visibility",
                "imports" to emptyList<String>(),
                "items" to listOf(mapOf("value" to 0, "name" to "HIDDEN", "description" to "Hidden")),
            ),
        )
        assertFalse(dbDerivedEnum.contains("import com.only4.cap4k.analysis.metadata.DesignBlockMetadata"))
        assertFalse(dbDerivedEnum.contains("@DesignBlockMetadata("))
    }

    @Test
    fun `integration event preset renders event annotation with literal event name and variant subscribers`() {
        val overrideDir = Files.createTempDirectory("cap4k-override-empty-design-integration-event")
        val renderer = PebbleArtifactRenderer(
            templateResolver = PresetTemplateResolver(
                preset = "ddd-default",
                overrideDirs = listOf(overrideDir.toString())
            )
        )

        val rendered = renderer.render(
            planItems = listOf(
                ArtifactPlanItem(
                    generatorId = "integration-event",
                    moduleRole = "application",
                    templateId = "design/integration_event.kt.peb",
                    outputPath = "demo-application/src/main/kotlin/com/acme/demo/application/events/integration/inbound/order/OrderCreatedIntegrationEvent.kt",
                    context = mapOf(
                        "packageName" to "com.acme.demo.application.events.integration.inbound.order",
                        "typeName" to "OrderCreatedIntegrationEvent",
                        "description" to "order */ \"created\" event",
                        "descriptionText" to "order */ \"created\" event",
                        "descriptionCommentText" to "order * / \"created\" event",
                        "descriptionKotlinStringLiteral" to "\"order */ \\\"created\\\" event\"",
                        "buildingBlock" to buildingBlockContext(
                            tag = "integration_event",
                            name = "OrderCreated",
                            family = "integration-event",
                            packageName = "order",
                            description = "order */ \"created\" event",
                            eventName = "order.created",
                            variant = "inbound",
                        ),
                        "variant" to "inbound",
                        "eventName" to "order.created",
                        "eventNameKotlinStringLiteral" to "\"order.created\"",
                        "inbound" to true,
                        "outbound" to false,
                        "imports" to listOf("java.util.UUID"),
                        "fields" to listOf(
                            mapOf(
                                "name" to "orderId",
                                "renderedType" to "UUID",
                                "nullable" to false,
                                "defaultValue" to "UUID(0L, 0L)",
                            ),
                        ),
                        "nestedTypes" to listOf(
                            mapOf(
                                "name" to "Details",
                                "fields" to listOf(
                                    mapOf(
                                        "name" to "source",
                                        "renderedType" to "String",
                                        "nullable" to false,
                                        "defaultValue" to "\"api\"",
                                    ),
                                ),
                            ),
                        ),
                    ),
                    conflictPolicy = ConflictPolicy.SKIP
                ),
                ArtifactPlanItem(
                    generatorId = "integration-event",
                    moduleRole = "application",
                    templateId = "design/integration_event.kt.peb",
                    outputPath = "demo-application/src/main/kotlin/com/acme/demo/application/events/integration/outbound/billing/InvoicePaidIntegrationEvent.kt",
                    context = mapOf(
                        "packageName" to "com.acme.demo.application.events.integration.outbound.billing",
                        "typeName" to "InvoicePaidIntegrationEvent",
                        "description" to "invoice paid event",
                        "descriptionText" to "invoice paid event",
                        "descriptionCommentText" to "invoice paid event",
                        "descriptionKotlinStringLiteral" to "\"invoice paid event\"",
                        "variant" to "outbound",
                        "eventName" to "invoice.\$paid\\completed",
                        "eventNameKotlinStringLiteral" to "\"invoice.\\\$paid\\\\completed\"",
                        "inbound" to false,
                        "outbound" to true,
                        "imports" to listOf("java.util.UUID"),
                        "fields" to listOf(
                            mapOf("name" to "invoiceId", "renderedType" to "UUID", "nullable" to false),
                        ),
                        "nestedTypes" to emptyList<Map<String, Any?>>(),
                    ),
                    conflictPolicy = ConflictPolicy.SKIP
                )
            ),
            config = ProjectConfig(
                basePackage = "com.acme.demo",
                layout = ProjectLayout.MULTI_MODULE,
                modules = emptyMap(),
                sources = emptyMap(),
                generators = emptyMap(),
                templates = TemplateConfig(
                    preset = "ddd-default",
                    overrideDirs = listOf(overrideDir.toString()),
                    conflictPolicy = ConflictPolicy.SKIP
                )
            )
        )

        val inboundContent = rendered[0].content
        assertTrue(inboundContent.contains("import com.only4.cap4k.ddd.core.application.event.annotation.IntegrationEvent"))
        assertTrue(inboundContent.contains("import com.only4.cap4k.analysis.metadata.DesignBlockMetadata"))
        assertTrue(inboundContent.contains("import java.util.UUID"))
        assertTrue(inboundContent.contains("@IntegrationEvent("))
        assertTrue(inboundContent.contains("value = \"order.created\""))
        assertFalse(inboundContent.contains("value = EVENT_NAME"))
        assertTrue(inboundContent.contains("subscriber = \"\\\${spring.application.name:}\""))
        assertTrue(inboundContent.contains("const val EVENT_NAME = \"order.created\""))
        assertTrue(inboundContent.contains("data class OrderCreatedIntegrationEvent("))
        assertTrue(inboundContent.contains("val orderId: UUID = UUID(0L, 0L)"))
        assertTrue(inboundContent.contains("val source: String = \"api\""))
        assertTrue(inboundContent.contains("eventName = \"order.created\""))

        val outboundContent = rendered[1].content
        assertTrue(outboundContent.contains("import com.only4.cap4k.ddd.core.application.event.annotation.IntegrationEvent"))
        assertTrue(outboundContent.contains("import java.util.UUID"))
        assertTrue(outboundContent.contains("value = \"invoice.\\\$paid\\\\completed\""))
        assertTrue(outboundContent.contains("const val EVENT_NAME = \"invoice.\\\$paid\\\\completed\""))
        assertTrue(outboundContent.contains("val invoiceId: UUID"))
        assertTrue(outboundContent.contains("subscriber = IntegrationEvent.NONE_SUBSCRIBER"))
    }

    @Test
    fun `integration event subscriber preset renders spring event listener without EventSubscriber contract`() {
        val overrideDir = Files.createTempDirectory("cap4k-override-empty-integration-subscriber")
        val renderer = PebbleArtifactRenderer(
            templateResolver = PresetTemplateResolver(
                preset = "ddd-default",
                overrideDirs = listOf(overrideDir.toString())
            )
        )

        val rendered = renderer.render(
            planItems = listOf(
                ArtifactPlanItem(
                    generatorId = "integration-subscriber",
                    moduleRole = "application",
                    templateId = "design/integration_event_subscriber.kt.peb",
                    outputPath = "demo-application/src/main/kotlin/com/acme/demo/application/subscribers/integration/inbound/order/OrderCreatedIntegrationEventSubscriber.kt",
                    context = mapOf(
                        "packageName" to "com.acme.demo.application.subscribers.integration.inbound.order",
                        "typeName" to "OrderCreatedIntegrationEventSubscriber",
                        "eventTypeName" to "OrderCreatedIntegrationEvent",
                        "eventType" to "com.acme.demo.application.events.integration.inbound.order.OrderCreatedIntegrationEvent",
                        "eventName" to "order.created",
                        "variant" to "inbound",
                        "inbound" to true,
                        "outbound" to false,
                        "description" to "order */ created event",
                        "descriptionCommentText" to "order * / created event",
                        "imports" to listOf("com.acme.demo.application.events.integration.inbound.order.OrderCreatedIntegrationEvent"),
                    ),
                    conflictPolicy = ConflictPolicy.SKIP
                )
            ),
            config = ProjectConfig(
                basePackage = "com.acme.demo",
                layout = ProjectLayout.MULTI_MODULE,
                modules = emptyMap(),
                sources = emptyMap(),
                generators = emptyMap(),
                templates = TemplateConfig(
                    preset = "ddd-default",
                    overrideDirs = listOf(overrideDir.toString()),
                    conflictPolicy = ConflictPolicy.SKIP
                )
            )
        )

        val content = rendered.single().content
        assertTrue(content.contains("import com.acme.demo.application.events.integration.inbound.order.OrderCreatedIntegrationEvent"))
        assertTrue(content.contains("import org.springframework.context.event.EventListener"))
        assertTrue(content.contains("import org.springframework.stereotype.Service"))
        assertTrue(content.contains("@Service"))
        assertTrue(content.contains("@EventListener(OrderCreatedIntegrationEvent::class)"))
        assertTrue(content.contains("fun on(event: OrderCreatedIntegrationEvent)"))
        assertFalse(content.contains("import com.only4.cap4k.ddd.core.application.event.EventSubscriber"))
        assertFalse(content.contains(": EventSubscriber"))
    }

    @Test
    fun `domain event presets support override template resolution for event and handler templates`() {
        val overrideDir = Files.createTempDirectory("cap4k-override-design-domain-event-family")
        val overrideDesignDir = Files.createDirectories(overrideDir.resolve("design"))
        overrideDesignDir.resolve("domain_event.kt.peb").writeText(
            """
            // override: renderer domain event template
            package {{ packageName }}
            class {{ typeName }}Override
            """.trimIndent()
        )
        overrideDesignDir.resolve("domain_event_handler.kt.peb").writeText(
            """
            // override: renderer domain event handler template
            package {{ packageName }}
            class {{ typeName }}Override
            """.trimIndent()
        )

        val renderer = PebbleArtifactRenderer(
            templateResolver = PresetTemplateResolver(
                preset = "ddd-default",
                overrideDirs = listOf(overrideDir.toString())
            )
        )

        val rendered = renderer.render(
            planItems = listOf(
                ArtifactPlanItem(
                    generatorId = "domain-event",
                    moduleRole = "domain",
                    templateId = "design/domain_event.kt.peb",
                    outputPath = "demo-domain/src/main/kotlin/com/acme/demo/domain/order/events/OrderCreatedDomainEvent.kt",
                    context = mapOf(
                        "packageName" to "com.acme.demo.domain.order.events",
                        "typeName" to "OrderCreatedDomainEvent",
                        "description" to "order created event",
                        "persist" to true,
                        "imports" to emptyList<String>(),
                        "fields" to emptyList<Map<String, Any?>>(),
                        "nestedTypes" to emptyList<Map<String, Any?>>(),
                    ),
                    conflictPolicy = ConflictPolicy.SKIP
                ),
                ArtifactPlanItem(
                    generatorId = "domain-subscriber",
                    moduleRole = "application",
                    templateId = "design/domain_event_handler.kt.peb",
                    outputPath = "demo-application/src/main/kotlin/com/acme/demo/application/subscribers/domain/order/OrderCreatedDomainEventSubscriber.kt",
                    context = mapOf(
                        "packageName" to "com.acme.demo.application.subscribers.domain.order",
                        "typeName" to "OrderCreatedDomainEventSubscriber",
                        "domainEventTypeName" to "OrderCreatedDomainEvent",
                        "domainEventType" to "com.acme.demo.domain.order.events.OrderCreatedDomainEvent",
                        "aggregateName" to "Order",
                        "description" to "order created event",
                        "imports" to emptyList<String>(),
                    ),
                    conflictPolicy = ConflictPolicy.SKIP
                )
            ),
            config = ProjectConfig(
                basePackage = "com.acme.demo",
                layout = ProjectLayout.MULTI_MODULE,
                modules = emptyMap(),
                sources = emptyMap(),
                generators = emptyMap(),
                templates = TemplateConfig(
                    preset = "ddd-default",
                    overrideDirs = listOf(overrideDir.toString()),
                    conflictPolicy = ConflictPolicy.SKIP
                )
            )
        )

        val eventContent = rendered[0].content
        val handlerContent = rendered[1].content
        assertTrue(eventContent.contains("// override: renderer domain event template"))
        assertTrue(eventContent.contains("class OrderCreatedDomainEventOverride"))
        assertTrue(handlerContent.contains("// override: renderer domain event handler template"))
        assertTrue(handlerContent.contains("class OrderCreatedDomainEventSubscriberOverride"))
    }

}

private data class RenderedTypeCarrier(
    val renderedType: String
)

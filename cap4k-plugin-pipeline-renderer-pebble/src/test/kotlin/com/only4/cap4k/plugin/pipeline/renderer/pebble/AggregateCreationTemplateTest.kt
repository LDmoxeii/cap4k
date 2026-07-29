package com.only4.cap4k.plugin.pipeline.renderer.pebble

import com.only4.cap4k.plugin.pipeline.api.ArtifactPlanItem
import com.only4.cap4k.plugin.pipeline.api.ConflictPolicy
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig
import com.only4.cap4k.plugin.pipeline.api.ProjectLayout
import com.only4.cap4k.plugin.pipeline.api.TemplateConfig
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AggregateCreationTemplateTest {
    @Test
    @OptIn(org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi::class)
    fun `creation values and recursive factory render immutable defaults attach through facades and compile`() {
        val aggregatePackage = "com.acme.demo.domain.aggregates.booking_demand"
        val cargoLineCreation = renderCreation(
            aggregatePackage,
            "DemandCargoLineCreation",
            listOf(field("cargoName", "String")),
        )
        val fileVariantCreation = renderCreation(
            aggregatePackage,
            "DemandFileVariantCreation",
            listOf(field("variantKey", "String")),
        )
        val fileCreation = renderCreation(
            aggregatePackage,
            "DemandFileCreation",
            listOf(
                field("storageKey", "String"),
                field("variants", "List<DemandFileVariantCreation>", "emptyList()"),
            ),
        )
        val factory = render(
            templateId = "aggregate/factory.kt.peb",
            outputPath = "demo-domain/src/main/kotlin/${aggregatePackage.replace('.', '/')}/factory/BookingDemandFactory.kt",
            context = mapOf(
                "packageName" to "$aggregatePackage.factory",
                "typeName" to "BookingDemandFactory",
                "payloadTypeName" to "Payload",
                "payloadMetadataName" to "BookingDemandPayload",
                "entityName" to "BookingDemand",
                "entityTypeFqn" to "$aggregatePackage.BookingDemand",
                "aggregateName" to "BookingDemand",
                "payloadFields" to listOf(
                    field("title", "String"),
                    field("cargoLines", "List<DemandCargoLineCreation>", "emptyList()"),
                    field("file", "DemandFileCreation?", "null"),
                ),
                "rootConstructorFields" to listOf(field("title", "String")),
                "rootRelations" to listOf(
                    relation("cargoLines", "cargoLines", "MANY", "createDemandCargoLine"),
                    relation("file", "file", "ONE", "createDemandFile"),
                ),
                "helpers" to listOf(
                    helper(
                        entityName = "DemandCargoLine",
                        valueTypeName = "DemandCargoLineCreation",
                        constructorFields = listOf(field("cargoName", "String")),
                    ),
                    helper(
                        entityName = "DemandFile",
                        valueTypeName = "DemandFileCreation",
                        constructorFields = listOf(field("storageKey", "String")),
                        relations = listOf(
                            relation("variants", "variants", "MANY", "createDemandFileVariant")
                        ),
                    ),
                    helper(
                        entityName = "DemandFileVariant",
                        valueTypeName = "DemandFileVariantCreation",
                        constructorFields = listOf(field("variantKey", "String")),
                    ),
                ),
                "imports" to listOf(
                    "$aggregatePackage.BookingDemand",
                    "$aggregatePackage.DemandCargoLine",
                    "$aggregatePackage.DemandCargoLineCreation",
                    "$aggregatePackage.DemandFile",
                    "$aggregatePackage.DemandFileCreation",
                    "$aggregatePackage.DemandFileVariant",
                    "$aggregatePackage.DemandFileVariantCreation",
                ),
            ),
        )

        assertTrue(cargoLineCreation.contains("data class DemandCargoLineCreation("))
        assertTrue(fileCreation.contains("val variants: List<DemandFileVariantCreation> = emptyList()"))
        assertTrue(factory.contains("val cargoLines: List<DemandCargoLineCreation> = emptyList()"))
        assertTrue(factory.contains("val file: DemandFileCreation? = null"))
        assertTrue(factory.contains("aggregate.cargoLines.add(createDemandCargoLine(childCreation))"))
        assertTrue(factory.contains("aggregate.file = createDemandFile(childCreation)"))
        assertTrue(factory.contains("entity.variants.add(createDemandFileVariant(childCreation))"))
        assertTrue(factory.contains("private fun createDemandFile("))
        assertFalse(factory.contains("fun DemandFileCreation.toEntity"))
        assertFalse(factory.contains("DemandCargoLineFactory"))

        val result = KotlinCompilation().apply {
            sources = listOf(
                SourceFile.kotlin("DemandCargoLineCreation.kt", cargoLineCreation),
                SourceFile.kotlin("DemandFileCreation.kt", fileCreation),
                SourceFile.kotlin("DemandFileVariantCreation.kt", fileVariantCreation),
                SourceFile.kotlin("BookingDemandFactory.kt", factory),
                SourceFile.kotlin(
                    "SpringService.kt",
                    """
                    package org.springframework.stereotype

                    annotation class Service
                    """.trimIndent(),
                ),
                SourceFile.kotlin(
                    "BookingDemandBehavior.kt",
                    """
                    package $aggregatePackage

                    class BookingDemandBehavior {
                        fun normalize(creation: DemandCargoLineCreation): DemandCargoLineCreation = creation
                    }
                    """.trimIndent(),
                ),
                SourceFile.kotlin(
                    "AggregateEntities.kt",
                    """
                    package $aggregatePackage

                    class BookingDemand internal constructor(val title: String) {
                        val cargoLines = mutableListOf<DemandCargoLine>()
                        var file: DemandFile? = null
                    }

                    class DemandCargoLine internal constructor(val cargoName: String)

                    class DemandFile internal constructor(val storageKey: String) {
                        val variants = mutableListOf<DemandFileVariant>()
                    }

                    class DemandFileVariant internal constructor(val variantKey: String)
                    """.trimIndent(),
                ),
            )
            inheritClassPath = true
            messageOutputStream = System.out
        }.compile()

        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
    }

    @Test
    fun `empty creation value renders as immutable data object`() {
        val content = renderCreation(
            packageName = "com.acme.demo.domain.aggregates.booking_demand",
            typeName = "EmptyMarkerCreation",
            fields = emptyList(),
        )

        assertTrue(content.contains("data object EmptyMarkerCreation"))
        assertFalse(content.contains("class EmptyMarkerCreation("))
    }

    private fun renderCreation(
        packageName: String,
        typeName: String,
        fields: List<Map<String, Any?>>,
    ): String = render(
        templateId = "aggregate/creation.kt.peb",
        outputPath = "demo-domain/src/main/kotlin/${packageName.replace('.', '/')}/$typeName.kt",
        context = mapOf(
            "packageName" to packageName,
            "typeName" to typeName,
            "fields" to fields,
            "empty" to fields.isEmpty(),
            "imports" to emptyList<String>(),
        ),
    )

    private fun render(
        templateId: String,
        outputPath: String,
        context: Map<String, Any?>,
    ): String = PebbleArtifactRenderer(
        PresetTemplateResolver("ddd-default", emptyList())
    ).render(
        planItems = listOf(
            ArtifactPlanItem(
                generatorId = "test",
                moduleRole = "domain",
                templateId = templateId,
                outputPath = outputPath,
                context = context,
                conflictPolicy = ConflictPolicy.SKIP,
            )
        ),
        config = ProjectConfig(
            basePackage = "com.acme.demo",
            layout = ProjectLayout.MULTI_MODULE,
            templates = TemplateConfig("ddd-default", emptyList(), ConflictPolicy.SKIP),
        ),
    ).single().content

    private fun field(
        name: String,
        renderedType: String,
        defaultValue: String? = null,
    ): Map<String, Any?> = mapOf(
        "name" to name,
        "renderedType" to renderedType,
        "defaultValue" to defaultValue,
    )

    private fun relation(
        fieldName: String,
        attachmentAccessorName: String,
        cardinality: String,
        targetHelperName: String,
    ): Map<String, Any?> = mapOf(
        "fieldName" to fieldName,
        "attachmentAccessorName" to attachmentAccessorName,
        "cardinality" to cardinality,
        "targetHelperName" to targetHelperName,
    )

    private fun helper(
        entityName: String,
        valueTypeName: String,
        constructorFields: List<Map<String, Any?>>,
        relations: List<Map<String, Any?>> = emptyList(),
    ): Map<String, Any?> = mapOf(
        "helperName" to "create$entityName",
        "entityName" to entityName,
        "valueTypeName" to valueTypeName,
        "constructorFields" to constructorFields,
        "relations" to relations,
    )
}

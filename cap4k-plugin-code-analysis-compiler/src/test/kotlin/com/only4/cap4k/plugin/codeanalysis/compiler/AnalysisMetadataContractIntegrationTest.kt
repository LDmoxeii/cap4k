@file:OptIn(org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi::class)

package com.only4.cap4k.plugin.codeanalysis.compiler

import com.only4.cap4k.plugin.pipeline.api.AgentSnapshotStatus
import com.only4.cap4k.plugin.pipeline.api.ArtifactPlanItem
import com.only4.cap4k.plugin.pipeline.api.ConflictPolicy
import com.only4.cap4k.plugin.pipeline.api.GeneratorConfig
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig
import com.only4.cap4k.plugin.pipeline.api.ProjectLayout
import com.only4.cap4k.plugin.pipeline.api.SourceConfig
import com.only4.cap4k.plugin.pipeline.api.TemplateConfig
import com.only4.cap4k.plugin.pipeline.renderer.pebble.PebbleArtifactRenderer
import com.only4.cap4k.plugin.pipeline.renderer.pebble.PresetTemplateResolver
import com.only4.cap4k.plugin.pipeline.source.ir.IrAnalysisSourceProvider
import com.tschuchort.compiletesting.SourceFile
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files

class AnalysisMetadataContractIntegrationTest {

    @Test
    fun `custom design template without metadata fails drawing board after real analyzer compilation`() {
        val overrideDir = Files.createTempDirectory("cap4k-design-metadata-opt-out")
        val templateDir = Files.createDirectories(overrideDir.resolve("design"))
        templateDir.resolve("command.kt.peb").toFile().writeText(
            """
            package demo.application.commands

            import com.only4.cap4k.ddd.core.application.command.Command

            object OptedOutCmd {
                data class Request(val id: Long) : Command<Response>
                data class Response(val accepted: Boolean)
            }
            """.trimIndent()
        )

        val generated = renderOverride(
            overrideDir = overrideDir.toString(),
            templateId = "design/command.kt.peb",
            outputPath = "application/src/main/kotlin/demo/application/commands/OptedOutCmd.kt",
        )
        val analysisDir = compileWithCap4kPlugin(
            listOf(
                SourceFile.kotlin(
                    "Command.kt",
                    """
                    package com.only4.cap4k.ddd.core.application.command
                    interface Command<RESULT : Any>
                    """.trimIndent(),
                ),
                SourceFile.kotlin("OptedOutCmd.kt", generated),
            )
        )

        val nodes = analysisDir.resolve("nodes.json").toFile().readText()
        assertTrue(nodes.contains("demo.application.commands.OptedOutCmd.Request"), nodes)
        assertTrue(nodes.contains("com.only4.cap4k.analysis.metadata.DesignBlockMetadata"), nodes)
        val snapshot = IrAnalysisSourceProvider().collect(analysisConfig(analysisDir.toString(), "drawing-board"))
        assertEquals(AgentSnapshotStatus.INVALID, snapshot.designProjection.status)
        assertTrue(snapshot.designProjection.diagnostics.any { it.message.contains("demo.application.commands.OptedOutCmd") })
        assertTrue(snapshot.designProjection.diagnostics.any { it.message.contains("DesignBlockMetadata") })
    }

    @Test
    fun `custom aggregate template without metadata fails flow after real analyzer compilation`() {
        val overrideDir = Files.createTempDirectory("cap4k-aggregate-metadata-opt-out")
        val templateDir = Files.createDirectories(overrideDir.resolve("aggregate"))
        templateDir.resolve("entity.kt.peb").toFile().writeText(
            """
            package demo.domain.aggregates.order

            import jakarta.persistence.Entity

            @Entity
            class Order
            """.trimIndent()
        )

        val generated = renderOverride(
            overrideDir = overrideDir.toString(),
            templateId = "aggregate/entity.kt.peb",
            outputPath = "domain/src/main/kotlin/demo/domain/aggregates/order/Order.kt",
        )
        val analysisDir = compileWithCap4kPlugin(
            listOf(
                SourceFile.kotlin(
                    "Entity.kt",
                    """
                    package jakarta.persistence
                    annotation class Entity
                    """.trimIndent(),
                ),
                SourceFile.kotlin("Order.kt", generated),
            )
        )

        val nodes = analysisDir.resolve("nodes.json").toFile().readText()
        assertTrue(nodes.contains("demo.domain.aggregates.order.Order"), nodes)
        assertTrue(nodes.contains("com.only4.cap4k.analysis.metadata.AggregateElementMetadata"), nodes)
        val snapshot = IrAnalysisSourceProvider().collect(analysisConfig(analysisDir.toString(), "flow"))
        assertEquals(AgentSnapshotStatus.OK, snapshot.graph.status)
        assertEquals(AgentSnapshotStatus.INVALID, snapshot.aggregateStructure.status)
        assertTrue(snapshot.aggregateStructure.diagnostics.any { it.message.contains("demo.domain.aggregates.order.Order") })
        assertTrue(snapshot.aggregateStructure.diagnostics.any { it.message.contains("AggregateElementMetadata") })
    }

    private fun renderOverride(
        overrideDir: String,
        templateId: String,
        outputPath: String,
    ): String {
        val config = ProjectConfig(
            basePackage = "demo",
            layout = ProjectLayout.MULTI_MODULE,
            modules = emptyMap(),
            sources = emptyMap(),
            generators = emptyMap(),
            templates = TemplateConfig(
                preset = "ddd-default",
                overrideDirs = listOf(overrideDir),
                conflictPolicy = ConflictPolicy.SKIP,
            ),
        )
        return PebbleArtifactRenderer(
            PresetTemplateResolver(
                preset = "ddd-default",
                overrideDirs = listOf(overrideDir),
            )
        ).render(
            listOf(
                ArtifactPlanItem(
                    generatorId = "test",
                    moduleRole = "application",
                    templateId = templateId,
                    outputPath = outputPath,
                    conflictPolicy = ConflictPolicy.SKIP,
                )
            ),
            config,
        ).single().content
    }

    private fun analysisConfig(inputDir: String, generatorId: String): ProjectConfig = ProjectConfig(
        basePackage = "demo",
        layout = ProjectLayout.MULTI_MODULE,
        modules = emptyMap(),
        sources = mapOf(
            "ir-analysis" to SourceConfig(options = mapOf("inputDirs" to listOf(inputDir)))
        ),
        generators = mapOf(generatorId to GeneratorConfig()),
        templates = TemplateConfig("ddd-default", emptyList(), ConflictPolicy.SKIP),
    )
}

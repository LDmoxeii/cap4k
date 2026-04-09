package com.only4.cap4k.plugin.pipeline.core

import com.only4.cap4k.plugin.pipeline.api.ArtifactPlanItem
import com.only4.cap4k.plugin.pipeline.api.CanonicalModel
import com.only4.cap4k.plugin.pipeline.api.ConflictPolicy
import com.only4.cap4k.plugin.pipeline.api.DesignSpecSnapshot
import com.only4.cap4k.plugin.pipeline.api.GeneratorConfig
import com.only4.cap4k.plugin.pipeline.api.GeneratorProvider
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig
import com.only4.cap4k.plugin.pipeline.api.ProjectLayout
import com.only4.cap4k.plugin.pipeline.api.RenderedArtifact
import com.only4.cap4k.plugin.pipeline.api.SourceConfig
import com.only4.cap4k.plugin.pipeline.api.SourceProvider
import com.only4.cap4k.plugin.pipeline.api.SourceSnapshot
import com.only4.cap4k.plugin.pipeline.api.TemplateConfig
import com.only4.cap4k.plugin.pipeline.renderer.api.ArtifactRenderer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.nio.file.Files

class DefaultPipelineRunnerTest {

    @Test
    fun `run executes collect normalize plan and render in order and writes artifacts`() {
        val callOrder = mutableListOf<String>()

        val sourceProvider = object : SourceProvider {
            override val id: String = "design-json"

            override fun collect(config: ProjectConfig): SourceSnapshot {
                callOrder += "collect"
                return DesignSpecSnapshot(entries = emptyList())
            }
        }

        val generatorProvider = object : GeneratorProvider {
            override val id: String = "design"

            override fun plan(config: ProjectConfig, model: CanonicalModel): List<ArtifactPlanItem> {
                callOrder += "plan"
                return listOf(
                    ArtifactPlanItem(
                        generatorId = id,
                        moduleRole = "app",
                        templateId = "template-1",
                        outputPath = "generated/Request.kt",
                        conflictPolicy = ConflictPolicy.OVERWRITE,
                    )
                )
            }
        }

        val assembler = object : CanonicalAssembler {
            override fun assemble(config: ProjectConfig, snapshots: List<SourceSnapshot>): CanonicalModel {
                callOrder += "normalize"
                return CanonicalModel()
            }
        }

        val renderer = object : ArtifactRenderer {
            override fun render(planItems: List<ArtifactPlanItem>, config: ProjectConfig): List<RenderedArtifact> {
                callOrder += "render"
                return listOf(
                    RenderedArtifact(
                        outputPath = "generated/Request.kt",
                        content = "class Request",
                        conflictPolicy = ConflictPolicy.OVERWRITE,
                    )
                )
            }
        }

        val exporter = FilesystemArtifactExporter(Files.createTempDirectory("pipeline-runner-test"))

        val runner = DefaultPipelineRunner(
            sources = listOf(sourceProvider),
            generators = listOf(generatorProvider),
            assembler = assembler,
            renderer = renderer,
            exporter = exporter,
        )

        val result = runner.run(
            ProjectConfig(
                basePackage = "com.only4.cap4k.sample",
                layout = ProjectLayout.SINGLE_MODULE,
                modules = mapOf("app" to "sample-app"),
                sources = mapOf("design-json" to SourceConfig(enabled = true)),
                generators = mapOf("design" to GeneratorConfig(enabled = true)),
                templates = TemplateConfig(
                    preset = "default",
                    overrideDirs = emptyList(),
                    conflictPolicy = ConflictPolicy.OVERWRITE,
                ),
            )
        )

        assertEquals(listOf("collect", "normalize", "plan", "render"), callOrder)
        assertEquals(1, result.writtenPaths.size)
    }
}

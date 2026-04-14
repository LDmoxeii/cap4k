package com.only4.cap4k.plugin.pipeline.core

import com.only4.cap4k.plugin.pipeline.api.ArtifactPlanItem
import com.only4.cap4k.plugin.pipeline.api.CanonicalModel
import com.only4.cap4k.plugin.pipeline.api.CanonicalAssemblyResult
import com.only4.cap4k.plugin.pipeline.api.ConflictPolicy
import com.only4.cap4k.plugin.pipeline.api.DesignSpecSnapshot
import com.only4.cap4k.plugin.pipeline.api.DbColumnSnapshot
import com.only4.cap4k.plugin.pipeline.api.DbSchemaSnapshot
import com.only4.cap4k.plugin.pipeline.api.DbTableSnapshot
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
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class DefaultPipelineRunnerTest {

    @Test
    fun `run executes enabled providers in order and returns expected pipeline result`() {
        val callOrder = mutableListOf<String>()
        val tempRoot = Files.createTempDirectory("pipeline-runner-test")
        val skippedPath = tempRoot.resolve("generated/Skipped.kt")
        Files.createDirectories(skippedPath.parent)
        Files.writeString(skippedPath, "existing-content")

        val enabledSourceProvider = object : SourceProvider {
            override val id: String = "design-json"

            override fun collect(config: ProjectConfig): SourceSnapshot {
                callOrder += "collect"
                return DesignSpecSnapshot(entries = emptyList())
            }
        }
        val disabledSourceProvider = object : SourceProvider {
            override val id: String = "disabled-source"

            override fun collect(config: ProjectConfig): SourceSnapshot {
                callOrder += "collect-disabled"
                return DesignSpecSnapshot(id = "disabled-source", entries = emptyList())
            }
        }

        val expectedPlanItems = listOf(
            ArtifactPlanItem(
                generatorId = "design",
                moduleRole = "app",
                templateId = "template-overwrite",
                outputPath = "generated/Request.kt",
                conflictPolicy = ConflictPolicy.OVERWRITE,
            ),
            ArtifactPlanItem(
                generatorId = "design",
                moduleRole = "app",
                templateId = "template-skip",
                outputPath = "generated/Skipped.kt",
                conflictPolicy = ConflictPolicy.SKIP,
            ),
        )

        val enabledGeneratorProvider = object : GeneratorProvider {
            override val id: String = "design"

            override fun plan(config: ProjectConfig, model: CanonicalModel): List<ArtifactPlanItem> {
                callOrder += "plan"
                return expectedPlanItems
            }
        }
        val disabledGeneratorProvider = object : GeneratorProvider {
            override val id: String = "disabled-generator"

            override fun plan(config: ProjectConfig, model: CanonicalModel): List<ArtifactPlanItem> {
                callOrder += "plan-disabled"
                return emptyList()
            }
        }

        val assembler = object : CanonicalAssembler {
            override fun assemble(config: ProjectConfig, snapshots: List<SourceSnapshot>): CanonicalAssemblyResult {
                callOrder += "normalize"
                return CanonicalAssemblyResult(CanonicalModel())
            }
        }

        var rendererReceivedPlanItems: List<ArtifactPlanItem> = emptyList()
        val renderedArtifacts = listOf(
            RenderedArtifact(
                outputPath = "generated/Request.kt",
                content = "class Request",
                conflictPolicy = ConflictPolicy.OVERWRITE,
            ),
            RenderedArtifact(
                outputPath = "generated/Skipped.kt",
                content = "class Skipped",
                conflictPolicy = ConflictPolicy.SKIP,
            ),
        )
        val renderer = object : ArtifactRenderer {
            override fun render(planItems: List<ArtifactPlanItem>, config: ProjectConfig): List<RenderedArtifact> {
                callOrder += "render"
                rendererReceivedPlanItems = planItems
                return renderedArtifacts
            }
        }

        val exporter = FilesystemArtifactExporter(tempRoot)

        val runner = DefaultPipelineRunner(
            sources = listOf(enabledSourceProvider, disabledSourceProvider),
            generators = listOf(enabledGeneratorProvider, disabledGeneratorProvider),
            assembler = assembler,
            renderer = renderer,
            exporter = exporter,
        )

        val result = runner.run(
            ProjectConfig(
                basePackage = "com.only4.cap4k.sample",
                layout = ProjectLayout.SINGLE_MODULE,
                modules = mapOf("app" to "sample-app"),
                sources = mapOf(
                    "design-json" to SourceConfig(enabled = true),
                    "disabled-source" to SourceConfig(enabled = false),
                ),
                generators = mapOf(
                    "design" to GeneratorConfig(enabled = true),
                    "disabled-generator" to GeneratorConfig(enabled = false),
                ),
                templates = TemplateConfig(
                    preset = "default",
                    overrideDirs = emptyList(),
                    conflictPolicy = ConflictPolicy.OVERWRITE,
                ),
            )
        )

        assertEquals(listOf("collect", "normalize", "plan", "render"), callOrder)
        assertEquals(expectedPlanItems, rendererReceivedPlanItems)
        assertEquals(expectedPlanItems, result.planItems)
        assertEquals(renderedArtifacts, result.renderedArtifacts)
        assertEquals(emptyList<String>(), result.warnings)
        assertEquals(null, result.diagnostics)
        assertEquals(1, result.writtenPaths.size)
        val writtenPath = Path.of(result.writtenPaths.first())
        assertTrue(Files.exists(writtenPath))
        assertEquals("class Request", Files.readString(writtenPath))
        assertEquals("existing-content", Files.readString(skippedPath))
    }

    @Test
    fun `run fails fast when enabled generator has no registered provider`() {
        val callOrder = mutableListOf<String>()
        val sourceProvider = object : SourceProvider {
            override val id: String = "design-json"

            override fun collect(config: ProjectConfig): SourceSnapshot {
                callOrder += "collect"
                return DesignSpecSnapshot(entries = emptyList())
            }
        }
        val assembler = object : CanonicalAssembler {
            override fun assemble(config: ProjectConfig, snapshots: List<SourceSnapshot>): CanonicalAssemblyResult {
                callOrder += "normalize"
                return CanonicalAssemblyResult(CanonicalModel())
            }
        }
        val renderer = object : ArtifactRenderer {
            override fun render(planItems: List<ArtifactPlanItem>, config: ProjectConfig): List<RenderedArtifact> {
                callOrder += "render"
                return emptyList()
            }
        }

        val runner = DefaultPipelineRunner(
            sources = listOf(sourceProvider),
            generators = emptyList(),
            assembler = assembler,
            renderer = renderer,
            exporter = NoopArtifactExporter(),
        )

        val error = assertThrows(IllegalArgumentException::class.java) {
            runner.run(
                ProjectConfig(
                    basePackage = "com.only4.cap4k.sample",
                    layout = ProjectLayout.SINGLE_MODULE,
                    modules = mapOf("app" to "sample-app"),
                    sources = mapOf("design-json" to SourceConfig(enabled = true)),
                    generators = mapOf("missing-generator" to GeneratorConfig(enabled = true)),
                    templates = TemplateConfig(
                        preset = "default",
                        overrideDirs = emptyList(),
                        conflictPolicy = ConflictPolicy.OVERWRITE,
                    ),
                )
            )
        }

        assertEquals("enabled generators have no registered providers: missing-generator", error.message)
        assertEquals(emptyList<String>(), callOrder)
    }

    @Test
    fun `run fails when rendered artifact output path escapes export root`() {
        val runner = runnerWithSingleArtifact(
            RenderedArtifact(
                outputPath = "../outside.kt",
                content = "class Outside",
                conflictPolicy = ConflictPolicy.OVERWRITE,
            )
        )

        val error = assertThrows(IllegalArgumentException::class.java) {
            runner.run(enabledConfig())
        }

        assertTrue(error.message?.contains("outside") == true)
    }

    @Test
    fun `run fails when conflict policy is FAIL and target already exists`() {
        val tempRoot = Files.createTempDirectory("pipeline-runner-fail-test")
        val existingFile = tempRoot.resolve("generated/Existing.kt")
        Files.createDirectories(existingFile.parent)
        Files.writeString(existingFile, "existing")

        val runner = runnerWithSingleArtifact(
            RenderedArtifact(
                outputPath = "generated/Existing.kt",
                content = "class Existing",
                conflictPolicy = ConflictPolicy.FAIL,
            ),
            tempRoot = tempRoot,
        )

        assertThrows(IllegalStateException::class.java) {
            runner.run(enabledConfig())
        }
    }

    @Test
    fun `pipeline result carries aggregate diagnostics`() {
        val result = DefaultPipelineRunner(
            sources = listOf(
                object : SourceProvider {
                    override val id: String = "db"

                    override fun collect(config: ProjectConfig): SourceSnapshot =
                        DbSchemaSnapshot(
                            tables = listOf(
                                DbTableSnapshot(
                                    "audit_log",
                                    "",
                                    columns = listOf(
                                        DbColumnSnapshot("tenant_id", "BIGINT", "Long", false, null, "", true),
                                        DbColumnSnapshot("event_id", "VARCHAR", "String", false, null, "", true),
                                    ),
                                    primaryKey = listOf("tenant_id", "event_id"),
                                    uniqueConstraints = emptyList(),
                                ),
                                DbTableSnapshot(
                                    "video_post",
                                    "",
                                    columns = listOf(
                                        DbColumnSnapshot("id", "BIGINT", "Long", false, null, "", true),
                                    ),
                                    primaryKey = listOf("id"),
                                    uniqueConstraints = emptyList(),
                                ),
                            ),
                            discoveredTables = listOf("audit_log", "video_post"),
                            includedTables = listOf("audit_log", "video_post"),
                            excludedTables = emptyList(),
                        )
                }
            ),
            generators = listOf(
                object : GeneratorProvider {
                    override val id: String = "aggregate"
                    override fun plan(config: ProjectConfig, model: CanonicalModel): List<ArtifactPlanItem> = emptyList()
                }
            ),
            assembler = DefaultCanonicalAssembler(),
            renderer = object : ArtifactRenderer {
                override fun render(planItems: List<ArtifactPlanItem>, config: ProjectConfig): List<RenderedArtifact> = emptyList()
            },
            exporter = NoopArtifactExporter(),
        ).run(
            ProjectConfig(
                basePackage = "com.acme.demo",
                layout = ProjectLayout.MULTI_MODULE,
                modules = mapOf("domain" to "demo-domain", "adapter" to "demo-adapter"),
                sources = mapOf("db" to SourceConfig(enabled = true)),
                generators = mapOf(
                    "aggregate" to GeneratorConfig(
                        enabled = true,
                        options = mapOf("unsupportedTablePolicy" to "SKIP"),
                    )
                ),
                templates = TemplateConfig("ddd-default", emptyList(), ConflictPolicy.SKIP),
            )
        )

        assertEquals(listOf("video_post"), result.diagnostics!!.aggregate!!.supportedTables)
        assertEquals("composite_primary_key", result.diagnostics!!.aggregate!!.unsupportedTables.single().reason)
    }

    private fun runnerWithSingleArtifact(
        artifact: RenderedArtifact,
        tempRoot: Path = Files.createTempDirectory("pipeline-runner-single-artifact-test"),
    ): DefaultPipelineRunner {
        val sourceProvider = object : SourceProvider {
            override val id: String = "design-json"

            override fun collect(config: ProjectConfig): SourceSnapshot = DesignSpecSnapshot(entries = emptyList())
        }

        val generatorProvider = object : GeneratorProvider {
            override val id: String = "design"

            override fun plan(config: ProjectConfig, model: CanonicalModel): List<ArtifactPlanItem> {
                return listOf(
                    ArtifactPlanItem(
                        generatorId = id,
                        moduleRole = "app",
                        templateId = "template-1",
                        outputPath = artifact.outputPath,
                        conflictPolicy = artifact.conflictPolicy,
                    )
                )
            }
        }

        val assembler = object : CanonicalAssembler {
            override fun assemble(config: ProjectConfig, snapshots: List<SourceSnapshot>): CanonicalAssemblyResult =
                CanonicalAssemblyResult(CanonicalModel())
        }

        val renderer = object : ArtifactRenderer {
            override fun render(planItems: List<ArtifactPlanItem>, config: ProjectConfig): List<RenderedArtifact> = listOf(artifact)
        }

        return DefaultPipelineRunner(
            sources = listOf(sourceProvider),
            generators = listOf(generatorProvider),
            assembler = assembler,
            renderer = renderer,
            exporter = FilesystemArtifactExporter(tempRoot),
        )
    }

    private fun enabledConfig(): ProjectConfig {
        return ProjectConfig(
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
    }
}

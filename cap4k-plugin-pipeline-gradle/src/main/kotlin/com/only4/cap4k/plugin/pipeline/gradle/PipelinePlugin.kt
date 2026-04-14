package com.only4.cap4k.plugin.pipeline.gradle

import com.only4.cap4k.plugin.pipeline.api.ConflictPolicy
import com.only4.cap4k.plugin.pipeline.api.GeneratorConfig
import com.only4.cap4k.plugin.pipeline.api.PipelineRunner
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig
import com.only4.cap4k.plugin.pipeline.core.DefaultCanonicalAssembler
import com.only4.cap4k.plugin.pipeline.core.DefaultPipelineRunner
import com.only4.cap4k.plugin.pipeline.core.FilesystemArtifactExporter
import com.only4.cap4k.plugin.pipeline.core.NoopArtifactExporter
import com.only4.cap4k.plugin.pipeline.generator.aggregate.AggregateArtifactPlanner
import com.only4.cap4k.plugin.pipeline.generator.design.DesignArtifactPlanner
import com.only4.cap4k.plugin.pipeline.generator.design.DesignQueryHandlerArtifactPlanner
import com.only4.cap4k.plugin.pipeline.generator.drawingboard.DrawingBoardArtifactPlanner
import com.only4.cap4k.plugin.pipeline.generator.flow.FlowArtifactPlanner
import com.only4.cap4k.plugin.pipeline.renderer.pebble.PebbleArtifactRenderer
import com.only4.cap4k.plugin.pipeline.renderer.pebble.PresetTemplateResolver
import com.only4.cap4k.plugin.pipeline.source.db.DbSchemaSourceProvider
import com.only4.cap4k.plugin.pipeline.source.designjson.DesignJsonSourceProvider
import com.only4.cap4k.plugin.pipeline.source.ir.IrAnalysisSourceProvider
import com.only4.cap4k.plugin.pipeline.source.ksp.KspMetadataSourceProvider
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import java.nio.file.Path

class PipelinePlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val extension = project.extensions.create("cap4k", Cap4kExtension::class.java)
        val configFactory = Cap4kProjectConfigFactory()

        val planTask = project.tasks.register("cap4kPlan", Cap4kPlanTask::class.java) { task ->
            task.group = "cap4k"
            task.description = "Plans Cap4k pipeline artifacts."
            task.extension = extension
            task.configFactory = configFactory
        }
        val generateTask = project.tasks.register("cap4kGenerate", Cap4kGenerateTask::class.java) { task ->
            task.group = "cap4k"
            task.description = "Generates artifacts from the Cap4k pipeline."
            task.extension = extension
            task.configFactory = configFactory
        }

        project.gradle.projectsEvaluated {
            val config = configFactory.build(project, extension)
            val inferredDependencies = inferDependencies(project, config)
            if (inferredDependencies.isNotEmpty()) {
                planTask.configure { task -> task.dependsOn(inferredDependencies) }
                generateTask.configure { task -> task.dependsOn(inferredDependencies) }
            }
        }
    }
}

internal fun inferDependencies(project: Project, config: ProjectConfig): List<Task> {
    val inferredDependencies = linkedSetOf<Task>()
    val allProjects = project.rootProject.allprojects

    val shouldDependOnKsp = config.enabledGeneratorIds().contains("design") &&
        config.enabledSourceIds().contains("ksp-metadata")
    if (shouldDependOnKsp) {
        val kspInputDir = config.sources["ksp-metadata"]
            ?.options
            ?.get("inputDir")
            ?.toString()
        if (kspInputDir != null) {
            inferredDependencies += relevantTasksForInputDir(allProjects, kspInputDir, "kspKotlin")
        }
    }

    val shouldDependOnCompileKotlin = config.enabledSourceIds().contains("ir-analysis") &&
        config.enabledGeneratorIds().any { it == "flow" || it == "drawing-board" }
    if (shouldDependOnCompileKotlin) {
        val inputDirs = config.sources["ir-analysis"]
            ?.options
            ?.get("inputDirs")
            .asStringList()
        inputDirs.forEach { inputDir ->
            inferredDependencies += relevantTasksForInputDir(allProjects, inputDir, "compileKotlin")
        }
    }

    return inferredDependencies.toList()
}

private fun relevantTasksForInputDir(allProjects: Iterable<Project>, inputDir: String, taskName: String): List<Task> {
    val normalizedInputDir = inputDir.toNormalizedPath()
    return allProjects.mapNotNull { candidate ->
        val task = candidate.tasks.findByName(taskName) ?: return@mapNotNull null
        val candidateBuildDir = candidate.layout.buildDirectory.get().asFile.toPath().toAbsolutePath().normalize()
        if (normalizedInputDir.startsWith(candidateBuildDir)) {
            task
        } else {
            null
        }
    }
}

private fun Any?.asStringList(): List<String> =
    when (this) {
        null -> emptyList()
        is Iterable<*> -> this.mapNotNull { it?.toString() }
        is Array<*> -> this.mapNotNull { it?.toString() }
        else -> listOf(this.toString())
    }

private fun String.toNormalizedPath(): Path =
    Path.of(this).toAbsolutePath().normalize()

internal fun buildRunner(project: Project, config: ProjectConfig, exportEnabled: Boolean): PipelineRunner {
    return DefaultPipelineRunner(
        sources = listOf(
            DbSchemaSourceProvider(),
            DesignJsonSourceProvider(),
            KspMetadataSourceProvider(),
            IrAnalysisSourceProvider(),
        ),
        generators = listOf(
            DesignArtifactPlanner(),
            DesignQueryHandlerArtifactPlanner(),
            AggregateArtifactPlanner(),
            DrawingBoardArtifactPlanner(),
            FlowArtifactPlanner(),
        ),
        assembler = DefaultCanonicalAssembler(),
        renderer = PebbleArtifactRenderer(
            PresetTemplateResolver(
                preset = config.templates.preset,
                overrideDirs = config.templates.overrideDirs,
            )
        ),
        exporter = if (exportEnabled) {
            FilesystemArtifactExporter(project.projectDir.toPath())
        } else {
            NoopArtifactExporter()
        },
    )
}

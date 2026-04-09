package com.only4.cap4k.plugin.pipeline.gradle

import com.only4.cap4k.plugin.pipeline.api.ConflictPolicy
import com.only4.cap4k.plugin.pipeline.api.GeneratorConfig
import com.only4.cap4k.plugin.pipeline.api.PipelineRunner
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig
import com.only4.cap4k.plugin.pipeline.api.ProjectLayout
import com.only4.cap4k.plugin.pipeline.api.SourceConfig
import com.only4.cap4k.plugin.pipeline.api.TemplateConfig
import com.only4.cap4k.plugin.pipeline.core.DefaultCanonicalAssembler
import com.only4.cap4k.plugin.pipeline.core.DefaultPipelineRunner
import com.only4.cap4k.plugin.pipeline.core.FilesystemArtifactExporter
import com.only4.cap4k.plugin.pipeline.core.NoopArtifactExporter
import com.only4.cap4k.plugin.pipeline.generator.design.DesignArtifactPlanner
import com.only4.cap4k.plugin.pipeline.renderer.pebble.PebbleArtifactRenderer
import com.only4.cap4k.plugin.pipeline.renderer.pebble.PresetTemplateResolver
import com.only4.cap4k.plugin.pipeline.source.designjson.DesignJsonSourceProvider
import com.only4.cap4k.plugin.pipeline.source.ksp.KspMetadataSourceProvider
import org.gradle.api.Plugin
import org.gradle.api.Project

class PipelinePlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val extension = project.extensions.create("cap4kPipeline", PipelineExtension::class.java)

        val planTask = project.tasks.register("cap4kPlan", Cap4kPlanTask::class.java) { task ->
            task.group = "cap4k"
            task.description = "Plans Cap4k pipeline artifacts."
            task.extension = extension
        }
        val generateTask = project.tasks.register("cap4kGenerate", Cap4kGenerateTask::class.java) { task ->
            task.group = "cap4k"
            task.description = "Generates artifacts from the Cap4k pipeline."
            task.extension = extension
        }

        project.afterEvaluate {
            val kspTasks = project.rootProject.allprojects
                .mapNotNull { candidate -> candidate.tasks.findByName("kspKotlin") }
            if (kspTasks.isNotEmpty()) {
                planTask.configure { task -> task.dependsOn(kspTasks) }
                generateTask.configure { task -> task.dependsOn(kspTasks) }
            }
        }
    }
}

internal fun buildConfig(project: Project, extension: PipelineExtension): ProjectConfig {
    return ProjectConfig(
        basePackage = extension.basePackage.get(),
        layout = ProjectLayout.MULTI_MODULE,
        modules = mapOf("application" to extension.applicationModulePath.get()),
        sources = mapOf(
            "design-json" to SourceConfig(
                enabled = true,
                options = mapOf(
                    "files" to extension.designFiles.files.map { file -> file.absolutePath }
                )
            ),
            "ksp-metadata" to SourceConfig(
                enabled = true,
                options = mapOf(
                    "inputDir" to project.file(extension.kspMetadataDir.get()).absolutePath
                )
            ),
        ),
        generators = mapOf(
            "design" to GeneratorConfig(enabled = true)
        ),
        templates = TemplateConfig(
            preset = "ddd-default",
            overrideDirs = listOf(project.file(extension.templateOverrideDir.get()).absolutePath),
            conflictPolicy = ConflictPolicy.SKIP,
        ),
    )
}

internal fun buildRunner(project: Project, config: ProjectConfig, exportEnabled: Boolean): PipelineRunner {
    return DefaultPipelineRunner(
        sources = listOf(
            DesignJsonSourceProvider(),
            KspMetadataSourceProvider(),
        ),
        generators = listOf(DesignArtifactPlanner()),
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

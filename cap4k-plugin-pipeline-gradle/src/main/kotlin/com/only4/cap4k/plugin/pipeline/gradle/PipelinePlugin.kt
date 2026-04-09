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
import com.only4.cap4k.plugin.pipeline.generator.aggregate.AggregateArtifactPlanner
import com.only4.cap4k.plugin.pipeline.generator.design.DesignArtifactPlanner
import com.only4.cap4k.plugin.pipeline.renderer.pebble.PebbleArtifactRenderer
import com.only4.cap4k.plugin.pipeline.renderer.pebble.PresetTemplateResolver
import com.only4.cap4k.plugin.pipeline.source.db.DbSchemaSourceProvider
import com.only4.cap4k.plugin.pipeline.source.designjson.DesignJsonSourceProvider
import com.only4.cap4k.plugin.pipeline.source.ksp.KspMetadataSourceProvider
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.provider.Property

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

        project.gradle.projectsEvaluated {
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
    val modules = buildMap {
        extension.applicationModulePath.optionalValue()?.let { put("application", it) }
        extension.domainModulePath.optionalValue()?.let { put("domain", it) }
        extension.adapterModulePath.optionalValue()?.let { put("adapter", it) }
    }
    val designJsonEnabled = extension.designFiles.files.isNotEmpty()
    val kspMetadataDir = extension.kspMetadataDir.optionalValue()
    val dbUrl = extension.dbUrl.optionalValue()
    val aggregateEnabled = dbUrl != null && "domain" in modules && "adapter" in modules

    return ProjectConfig(
        basePackage = extension.basePackage.get(),
        layout = ProjectLayout.MULTI_MODULE,
        modules = modules,
        sources = buildMap {
            if (designJsonEnabled) {
                put(
                    "design-json",
                    SourceConfig(
                        enabled = true,
                        options = mapOf(
                            "files" to extension.designFiles.files.map { file -> file.absolutePath }
                        )
                    )
                )
            }
            if (kspMetadataDir != null) {
                put(
                    "ksp-metadata",
                    SourceConfig(
                        enabled = true,
                        options = mapOf(
                            "inputDir" to project.file(kspMetadataDir).absolutePath
                        )
                    )
                )
            }
            if (dbUrl != null) {
                put(
                    "db",
                    SourceConfig(
                        enabled = true,
                        options = mapOf(
                            "url" to dbUrl,
                            "username" to extension.dbUsername.orNull.orEmpty(),
                            "password" to extension.dbPassword.orNull.orEmpty(),
                            "schema" to extension.dbSchema.orNull.orEmpty(),
                            "includeTables" to extension.dbIncludeTables.orNull.orEmpty(),
                            "excludeTables" to extension.dbExcludeTables.orNull.orEmpty(),
                        )
                    )
                )
            }
        },
        generators = buildMap {
            if (designJsonEnabled) {
                put("design", GeneratorConfig(enabled = true))
            }
            if (aggregateEnabled) {
                put("aggregate", GeneratorConfig(enabled = true))
            }
        },
        templates = TemplateConfig(
            preset = "ddd-default",
            overrideDirs = listOf(project.file(extension.templateOverrideDir.get()).absolutePath),
            conflictPolicy = ConflictPolicy.SKIP,
        ),
    )
}

private fun Property<String>.optionalValue(): String? = orNull?.trim()?.takeIf { it.isNotEmpty() }

internal fun buildRunner(project: Project, config: ProjectConfig, exportEnabled: Boolean): PipelineRunner {
    return DefaultPipelineRunner(
        sources = listOf(
            DbSchemaSourceProvider(),
            DesignJsonSourceProvider(),
            KspMetadataSourceProvider(),
        ),
        generators = listOf(
            DesignArtifactPlanner(),
            AggregateArtifactPlanner(),
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

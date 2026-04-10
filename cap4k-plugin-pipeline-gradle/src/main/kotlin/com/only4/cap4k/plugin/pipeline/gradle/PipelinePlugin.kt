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
import com.only4.cap4k.plugin.pipeline.generator.flow.FlowArtifactPlanner
import com.only4.cap4k.plugin.pipeline.renderer.pebble.PebbleArtifactRenderer
import com.only4.cap4k.plugin.pipeline.renderer.pebble.PresetTemplateResolver
import com.only4.cap4k.plugin.pipeline.source.db.DbSchemaSourceProvider
import com.only4.cap4k.plugin.pipeline.source.designjson.DesignJsonSourceProvider
import com.only4.cap4k.plugin.pipeline.source.ir.IrAnalysisSourceProvider
import com.only4.cap4k.plugin.pipeline.source.ksp.KspMetadataSourceProvider
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.provider.ListProperty
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
            validateAggregateConfig(extension)
            val allProjects = project.rootProject.allprojects
            val kspTasks = allProjects
                .mapNotNull { candidate -> candidate.tasks.findByName("kspKotlin") }
            if (kspTasks.isNotEmpty()) {
                planTask.configure { task -> task.dependsOn(kspTasks) }
                generateTask.configure { task -> task.dependsOn(kspTasks) }
            }
            val compileKotlinTasks = allProjects
                .mapNotNull { candidate -> candidate.tasks.findByName("compileKotlin") }
            if (extension.irAnalysisInputDirs.files.isNotEmpty() && compileKotlinTasks.isNotEmpty()) {
                planTask.configure { task -> task.dependsOn(compileKotlinTasks) }
                generateTask.configure { task -> task.dependsOn(compileKotlinTasks) }
            }
        }
    }
}

internal fun buildConfig(project: Project, extension: PipelineExtension): ProjectConfig {
    val aggregateConfig = aggregateConfigState(extension)
    validateAggregateConfig(aggregateConfig)

    val modules = buildMap {
        extension.applicationModulePath.optionalValue()?.let { put("application", it) }
        aggregateConfig.domainModulePath?.let { put("domain", it) }
        aggregateConfig.adapterModulePath?.let { put("adapter", it) }
    }
    val designJsonEnabled = extension.designFiles.files.isNotEmpty()
    val kspMetadataDir = extension.kspMetadataDir.optionalValue()
    val irInputDirs = extension.irAnalysisInputDirs.files.map { it.absolutePath }.sorted()
    val aggregateEnabled = aggregateConfig.dbUrl != null && "domain" in modules && "adapter" in modules
    val flowEnabled = irInputDirs.isNotEmpty()

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
            if (aggregateConfig.dbUrl != null) {
                put(
                    "db",
                    SourceConfig(
                        enabled = true,
                        options = mapOf(
                            "url" to aggregateConfig.dbUrl,
                            "username" to extension.dbUsername.orNull.orEmpty(),
                            "password" to extension.dbPassword.orNull.orEmpty(),
                            "schema" to extension.dbSchema.orNull.orEmpty(),
                            "includeTables" to extension.dbIncludeTables.orNull.orEmpty(),
                            "excludeTables" to extension.dbExcludeTables.orNull.orEmpty(),
                        )
                    )
                )
            }
            if (flowEnabled) {
                put(
                    "ir-analysis",
                    SourceConfig(
                        enabled = true,
                        options = mapOf("inputDirs" to irInputDirs),
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
            if (flowEnabled) {
                put(
                    "flow",
                    GeneratorConfig(
                        enabled = true,
                        options = mapOf(
                            "outputDir" to extension.flowOutputDir.optionalValue().orEmpty().ifBlank { "flows" },
                        ),
                    )
                )
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

private fun aggregateConfigState(extension: PipelineExtension): AggregateConfigState =
    AggregateConfigState(
        dbUrl = extension.dbUrl.optionalValue(),
        domainModulePath = extension.domainModulePath.optionalValue(),
        adapterModulePath = extension.adapterModulePath.optionalValue(),
        hasSignals = listOf(
            extension.dbUrl.isPresent,
            extension.domainModulePath.isPresent,
            extension.adapterModulePath.isPresent,
            extension.dbUsername.isPresent,
            extension.dbPassword.isPresent,
            extension.dbSchema.isPresent,
            extension.dbIncludeTables.hasConfiguredValues(),
            extension.dbExcludeTables.hasConfiguredValues(),
        ).any { it }
    )

private data class AggregateConfigState(
    val dbUrl: String?,
    val domainModulePath: String?,
    val adapterModulePath: String?,
    val hasSignals: Boolean,
)

private fun ListProperty<String>.hasConfiguredValues(): Boolean =
    orNull?.any { it.isNotBlank() } == true

private fun validateAggregateConfig(extension: PipelineExtension) {
    validateAggregateConfig(aggregateConfigState(extension))
}

private fun validateAggregateConfig(config: AggregateConfigState) {
    if (!config.hasSignals) {
        return
    }

    val missingFields = listOf(
        "dbUrl" to config.dbUrl,
        "domainModulePath" to config.domainModulePath,
        "adapterModulePath" to config.adapterModulePath,
    ).filter { (_, value) -> value == null }
        .joinToString(", ") { (name, _) -> name }
    if (missingFields.isEmpty()) {
        return
    }

    error(
        "Aggregate pipeline config requires dbUrl, domainModulePath, and adapterModulePath when any are set. " +
            "Missing: $missingFields."
    )
}

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
            AggregateArtifactPlanner(),
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

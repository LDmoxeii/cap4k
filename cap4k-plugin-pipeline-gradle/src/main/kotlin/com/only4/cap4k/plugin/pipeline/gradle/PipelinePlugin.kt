package com.only4.cap4k.plugin.pipeline.gradle

import com.only4.cap4k.plugin.pipeline.api.ConflictPolicy
import com.only4.cap4k.plugin.pipeline.api.GeneratorConfig
import com.only4.cap4k.plugin.pipeline.api.BootstrapRunner
import com.only4.cap4k.plugin.pipeline.api.BootstrapConfig
import com.only4.cap4k.plugin.pipeline.api.PipelineRunner
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig
import com.only4.cap4k.plugin.pipeline.core.DefaultCanonicalAssembler
import com.only4.cap4k.plugin.pipeline.core.DefaultBootstrapRunner
import com.only4.cap4k.plugin.pipeline.core.DefaultPipelineRunner
import com.only4.cap4k.plugin.pipeline.core.BootstrapFilesystemArtifactExporter
import com.only4.cap4k.plugin.pipeline.core.BootstrapRootStateGuard
import com.only4.cap4k.plugin.pipeline.core.FilesystemArtifactExporter
import com.only4.cap4k.plugin.pipeline.core.NoopArtifactExporter
import com.only4.cap4k.plugin.pipeline.bootstrap.DddMultiModuleBootstrapPresetProvider
import com.only4.cap4k.plugin.pipeline.generator.aggregate.AggregateArtifactPlanner
import com.only4.cap4k.plugin.pipeline.generator.design.DesignApiPayloadArtifactPlanner
import com.only4.cap4k.plugin.pipeline.generator.design.DesignClientArtifactPlanner
import com.only4.cap4k.plugin.pipeline.generator.design.DesignClientHandlerArtifactPlanner
import com.only4.cap4k.plugin.pipeline.generator.design.DesignCommandArtifactPlanner
import com.only4.cap4k.plugin.pipeline.generator.design.DesignDomainEventArtifactPlanner
import com.only4.cap4k.plugin.pipeline.generator.design.DesignDomainEventHandlerArtifactPlanner
import com.only4.cap4k.plugin.pipeline.generator.design.DesignQueryArtifactPlanner
import com.only4.cap4k.plugin.pipeline.generator.design.DesignQueryHandlerArtifactPlanner
import com.only4.cap4k.plugin.pipeline.generator.design.DesignValidatorArtifactPlanner
import com.only4.cap4k.plugin.pipeline.generator.drawingboard.DrawingBoardArtifactPlanner
import com.only4.cap4k.plugin.pipeline.generator.flow.FlowArtifactPlanner
import com.only4.cap4k.plugin.pipeline.renderer.pebble.PebbleArtifactRenderer
import com.only4.cap4k.plugin.pipeline.renderer.pebble.PebbleBootstrapRenderer
import com.only4.cap4k.plugin.pipeline.renderer.pebble.PresetTemplateResolver
import com.only4.cap4k.plugin.pipeline.source.db.DbSchemaSourceProvider
import com.only4.cap4k.plugin.pipeline.source.designjson.DesignJsonSourceProvider
import com.only4.cap4k.plugin.pipeline.source.enummanifest.EnumManifestSourceProvider
import com.only4.cap4k.plugin.pipeline.source.ir.IrAnalysisSourceProvider
import com.only4.cap4k.plugin.pipeline.source.ksp.KspMetadataSourceProvider
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import java.io.File
import java.nio.file.Path

class PipelinePlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val extension = project.extensions.create("cap4k", Cap4kExtension::class.java)
        val configFactory = Cap4kProjectConfigFactory()
        val bootstrapConfigFactory = Cap4kBootstrapConfigFactory()

        project.tasks.register("cap4kBootstrapPlan", Cap4kBootstrapPlanTask::class.java) { task ->
            task.group = "cap4k"
            task.description = "Plans bootstrap skeleton files."
            task.extension = extension
            task.configFactory = bootstrapConfigFactory
        }
        project.tasks.register("cap4kBootstrap", Cap4kBootstrapTask::class.java) { task ->
            task.group = "cap4k"
            task.description = "Generates bootstrap skeleton files."
            task.extension = extension
            task.configFactory = bootstrapConfigFactory
        }

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
        val analysisPlanTask = project.tasks.register("cap4kAnalysisPlan", Cap4kAnalysisPlanTask::class.java) { task ->
            task.group = "cap4k"
            task.description = "Plans Cap4k analysis export artifacts."
            task.extension = extension
            task.configFactory = configFactory
        }
        val analysisGenerateTask = project.tasks.register("cap4kAnalysisGenerate", Cap4kAnalysisGenerateTask::class.java) { task ->
            task.group = "cap4k"
            task.description = "Generates artifacts from analysis snapshots."
            task.extension = extension
            task.configFactory = configFactory
        }

        project.gradle.projectsEvaluated {
            if (!shouldInferPipelineDependencies(extension)) {
                return@projectsEvaluated
            }
            val config = configFactory.build(project, extension)
            ensureAggregateDomainJpaDependency(project, config)
            val inferredSourceDependencies = inferSourceDependencies(project, config)
            if (inferredSourceDependencies.isNotEmpty()) {
                planTask.configure { task -> task.dependsOn(inferredSourceDependencies) }
                generateTask.configure { task -> task.dependsOn(inferredSourceDependencies) }
            }
            val inferredAnalysisDependencies = inferAnalysisDependencies(project, config)
            if (inferredAnalysisDependencies.isNotEmpty()) {
                analysisPlanTask.configure { task -> task.dependsOn(inferredAnalysisDependencies) }
                analysisGenerateTask.configure { task -> task.dependsOn(inferredAnalysisDependencies) }
            }
        }
    }
}

internal fun shouldInferPipelineDependencies(extension: Cap4kExtension): Boolean =
    hasEnabledRegularSource(extension) || hasEnabledRegularGenerator(extension)

private const val JAKARTA_PERSISTENCE_GROUP = "jakarta.persistence"
private const val JAKARTA_PERSISTENCE_NAME = "jakarta.persistence-api"
private const val JAKARTA_PERSISTENCE_COORDINATE = "$JAKARTA_PERSISTENCE_GROUP:$JAKARTA_PERSISTENCE_NAME:3.1.0"
private val SOURCE_TASK_SOURCE_IDS = setOf("db", "enum-manifest", "design-json", "ksp-metadata")
private val SOURCE_TASK_GENERATOR_IDS = setOf(
    "design-command",
    "design-query",
    "design-query-handler",
    "design-client",
    "design-client-handler",
    "design-validator",
    "design-api-payload",
    "design-domain-event",
    "design-domain-event-handler",
    "aggregate",
)
private val ANALYSIS_TASK_SOURCE_IDS = setOf("ir-analysis")
private val ANALYSIS_TASK_GENERATOR_IDS = setOf("flow", "drawing-board")

private fun hasEnabledRegularSource(extension: Cap4kExtension): Boolean = listOf(
    extension.sources.designJson.enabled,
    extension.sources.kspMetadata.enabled,
    extension.sources.db.enabled,
    extension.sources.enumManifest.enabled,
    extension.sources.irAnalysis.enabled,
).any { it.orNull == true }

private fun hasEnabledRegularGenerator(extension: Cap4kExtension): Boolean = listOf(
    extension.generators.designCommand.enabled,
    extension.generators.designQuery.enabled,
    extension.generators.designQueryHandler.enabled,
    extension.generators.designClient.enabled,
    extension.generators.designClientHandler.enabled,
    extension.generators.designValidator.enabled,
    extension.generators.designApiPayload.enabled,
    extension.generators.designDomainEvent.enabled,
    extension.generators.designDomainEventHandler.enabled,
    extension.generators.aggregate.enabled,
    extension.generators.drawingBoard.enabled,
    extension.generators.flow.enabled,
).any { it.orNull == true }

internal fun sourceTaskConfig(config: ProjectConfig): ProjectConfig =
    config.copy(
        sources = config.sources.filterKeys { it in SOURCE_TASK_SOURCE_IDS },
        generators = config.generators.filterKeys { it in SOURCE_TASK_GENERATOR_IDS },
    )

internal fun analysisTaskConfig(config: ProjectConfig): ProjectConfig =
    config.copy(
        sources = config.sources.filterKeys { it in ANALYSIS_TASK_SOURCE_IDS },
        generators = config.generators.filterKeys { it in ANALYSIS_TASK_GENERATOR_IDS },
    )

internal fun ensureAggregateDomainJpaDependency(project: Project, config: ProjectConfig) {
    if (!config.enabledGeneratorIds().contains("aggregate")) {
        return
    }
    val domainModulePath = config.modules["domain"] ?: return
    val domainProject = resolveModuleProject(project.rootProject, domainModulePath) ?: return
    val implementationConfiguration = domainProject.configurations.findByName("implementation") ?: return
    val hasDependency = implementationConfiguration.dependencies.any { dependency ->
        dependency.group == JAKARTA_PERSISTENCE_GROUP && dependency.name == JAKARTA_PERSISTENCE_NAME
    }
    if (!hasDependency) {
        domainProject.dependencies.add("implementation", JAKARTA_PERSISTENCE_COORDINATE)
    }
}

internal fun inferDependencies(project: Project, config: ProjectConfig): List<Task> {
    val mergedDependencies = linkedSetOf<Task>()
    mergedDependencies += inferSourceDependencies(project, config)
    mergedDependencies += inferAnalysisDependencies(project, config)
    return mergedDependencies.toList()
}

internal fun inferSourceDependencies(project: Project, config: ProjectConfig): List<Task> {
    val inferredDependencies = linkedSetOf<Task>()
    val allProjects = project.rootProject.allprojects

    val enabledGenerators = config.enabledGeneratorIds()
    val shouldDependOnKsp = enabledGenerators.any {
        it == "design-command" || it == "design-query" || it == "design-domain-event"
    } &&
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

    return inferredDependencies.toList()
}

internal fun inferAnalysisDependencies(project: Project, config: ProjectConfig): List<Task> {
    val inferredDependencies = linkedSetOf<Task>()
    val allProjects = project.rootProject.allprojects
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

private fun resolveModuleProject(rootProject: Project, modulePath: String): Project? {
    val normalizedModulePath = modulePath.trim()
    if (normalizedModulePath.isEmpty()) {
        return null
    }

    val gradleProjectPath = normalizedModulePath.toGradleProjectPath()
    rootProject.findProject(gradleProjectPath)?.let { return it }

    val normalizedRelativePath = normalizedModulePath.trimStart(':')
        .replace(':', '/')
        .replace('\\', '/')
    if (normalizedRelativePath.isEmpty()) {
        return rootProject
    }

    val expectedProjectDir = rootProject.projectDir.toPath().toAbsolutePath().normalize()
        .resolve(normalizedRelativePath)
        .normalize()
    return rootProject.allprojects.firstOrNull { candidate ->
        candidate.projectDir.toPath().toAbsolutePath().normalize() == expectedProjectDir
    }
}

private fun String.toGradleProjectPath(): String {
    val normalized = trim()
    if (normalized.startsWith(":")) {
        return normalized
    }
    val modulePath = normalized.trim('/').replace('\\', '/').replace('/', ':')
    return if (modulePath.isEmpty()) ":" else ":$modulePath"
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

internal fun buildSourceRunner(project: Project, config: ProjectConfig, exportEnabled: Boolean): PipelineRunner {
    return DefaultPipelineRunner(
        sources = listOf(
            DbSchemaSourceProvider(),
            EnumManifestSourceProvider(),
            DesignJsonSourceProvider(),
            KspMetadataSourceProvider(),
        ),
        generators = listOf(
            DesignCommandArtifactPlanner(),
            DesignQueryArtifactPlanner(),
            DesignQueryHandlerArtifactPlanner(),
            DesignClientArtifactPlanner(),
            DesignClientHandlerArtifactPlanner(),
            DesignValidatorArtifactPlanner(),
            DesignApiPayloadArtifactPlanner(),
            DesignDomainEventArtifactPlanner(),
            DesignDomainEventHandlerArtifactPlanner(),
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

internal fun buildAnalysisRunner(project: Project, config: ProjectConfig, exportEnabled: Boolean): PipelineRunner {
    return DefaultPipelineRunner(
        sources = listOf(
            IrAnalysisSourceProvider(),
        ),
        generators = listOf(
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

internal fun buildRunner(project: Project, config: ProjectConfig, exportEnabled: Boolean): PipelineRunner =
    buildSourceRunner(project, config, exportEnabled)

internal fun buildBootstrapRunner(project: Project, config: BootstrapConfig, exportEnabled: Boolean): BootstrapRunner {
    val rootStateGuard = BootstrapRootStateGuard(project.projectDir.toPath())
    val rebasedOverrideDirs = config.templates.overrideDirs.map { overrideDir ->
        if (File(overrideDir).isAbsolute) {
            overrideDir
        } else {
            project.projectDir.toPath().resolve(overrideDir).normalize().toString()
        }
    }
    return DefaultBootstrapRunner(
        providers = listOf(DddMultiModuleBootstrapPresetProvider()),
        renderer = PebbleBootstrapRenderer(
            PresetTemplateResolver(
                preset = config.templates.preset,
                overrideDirs = rebasedOverrideDirs,
            )
        ),
        exporter = if (exportEnabled) {
            BootstrapFilesystemArtifactExporter(project.projectDir.toPath(), config)
        } else {
            NoopArtifactExporter()
        },
        preRunValidation = rootStateGuard::validate,
    )
}

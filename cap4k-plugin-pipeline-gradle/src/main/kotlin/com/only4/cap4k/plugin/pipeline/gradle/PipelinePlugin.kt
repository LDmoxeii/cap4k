package com.only4.cap4k.plugin.pipeline.gradle

import com.google.gson.GsonBuilder
import com.only4.cap4k.plugin.pipeline.api.ArtifactLayoutResolver
import com.only4.cap4k.plugin.pipeline.api.ArtifactOutputKind
import com.only4.cap4k.plugin.pipeline.api.ArtifactPlanItem
import com.only4.cap4k.plugin.pipeline.api.ConflictPolicy
import com.only4.cap4k.plugin.pipeline.api.GeneratorConfig
import com.only4.cap4k.plugin.pipeline.api.BootstrapRunner
import com.only4.cap4k.plugin.pipeline.api.BootstrapConfig
import com.only4.cap4k.plugin.pipeline.api.PipelineRunner
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig
import com.only4.cap4k.plugin.pipeline.api.SourceConfig
import com.only4.cap4k.plugin.pipeline.core.DefaultCanonicalAssembler
import com.only4.cap4k.plugin.pipeline.core.DefaultBootstrapRunner
import com.only4.cap4k.plugin.pipeline.core.DefaultPipelineRunner
import com.only4.cap4k.plugin.pipeline.core.BootstrapFilesystemArtifactExporter
import com.only4.cap4k.plugin.pipeline.core.BootstrapRootStateGuard
import com.only4.cap4k.plugin.pipeline.core.FilteringArtifactExporter
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
import org.gradle.api.file.FileCollection
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.TaskProvider
import java.io.File
import java.nio.file.Path
import java.security.MessageDigest

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
        val generateSourcesTask = project.tasks.register("cap4kGenerateSources", Cap4kGenerateSourcesTask::class.java) { task ->
            task.group = "cap4k"
            task.description = "Generates build-owned Kotlin sources from the Cap4k pipeline."
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
            val generatedSourceConfig = generatedSourceTaskConfig(config)
            val inferredGeneratedSourceDependencies = inferSourceDependencies(project, generatedSourceConfig)
            if (inferredGeneratedSourceDependencies.isNotEmpty()) {
                generateSourcesTask.configure { task -> task.dependsOn(inferredGeneratedSourceDependencies) }
            }
            registerGeneratedKotlinSourceSets(project.rootProject, config)
            wireGeneratedSourceCompilation(project.rootProject, config, generateSourcesTask)
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
private val GENERATED_SOURCE_TASK_SOURCE_IDS = setOf("db", "enum-manifest")
private val GENERATED_SOURCE_TASK_GENERATOR_IDS = setOf("aggregate")
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

internal fun generatedSourceTaskConfig(config: ProjectConfig): ProjectConfig =
    config.copy(
        sources = config.sources.filterKeys { it in GENERATED_SOURCE_TASK_SOURCE_IDS },
        generators = config.generators.filterKeys { it in GENERATED_SOURCE_TASK_GENERATOR_IDS },
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

internal fun generatedSourceModuleRoles(config: ProjectConfig): Set<String> {
    val aggregate = config.generators["aggregate"] ?: return emptySet()
    if (!aggregate.enabled) {
        return emptySet()
    }

    val roles = linkedSetOf("domain", "adapter")
    if (aggregate.options["artifact.unique"] as? Boolean == true) {
        roles += "application"
    }
    return roles.filterTo(linkedSetOf()) { role -> role in config.modules }
}

internal fun generatedKotlinSourceRoot(config: ProjectConfig, moduleRole: String): String {
    val moduleRoot = requireNotNull(config.modules[moduleRole]) {
        "$moduleRole module is required"
    }
    return ArtifactLayoutResolver(config.basePackage, config.artifactLayout)
        .generatedKotlinSourceRoot(moduleRoot)
}

internal fun resolvedGeneratedKotlinSourceRoot(
    rootProject: Project,
    config: ProjectConfig,
    moduleRole: String,
): String? =
    generatedKotlinSourceDirectory(rootProject, config, moduleRole)
        ?.toRootRelativeSlash(rootProject)

internal fun generatedSourceOutputDirectories(rootProject: Project, config: ProjectConfig): List<File> =
    generatedSourceModuleRoles(config).mapNotNull { role ->
        generatedKotlinSourceDirectory(rootProject, config, role)
    }

private fun generatedKotlinSourceDirectory(rootProject: Project, config: ProjectConfig, moduleRole: String): File? {
    val modulePath = config.modules[moduleRole] ?: return null
    val moduleProject = resolveModuleProject(rootProject, modulePath) ?: return null
    return moduleProject.layout.buildDirectory.dir("generated/cap4k/main/kotlin").get().asFile
}

internal fun registerGeneratedKotlinSourceSets(rootProject: Project, config: ProjectConfig) {
    generatedSourceModuleRoles(config).forEach { role ->
        val modulePath = config.modules[role] ?: return@forEach
        val moduleProject = resolveModuleProject(rootProject, modulePath) ?: return@forEach
        moduleProject.plugins.withId("org.jetbrains.kotlin.jvm") {
            registerGeneratedKotlinSourceDir(moduleProject)
        }
    }
}

private fun registerGeneratedKotlinSourceDir(moduleProject: Project) {
    val kotlinExtension = moduleProject.extensions.findByName("kotlin") ?: return
    val sourceSets = kotlinExtension.javaClass.methods
        .singleOrNull { method -> method.name == "getSourceSets" && method.parameterCount == 0 }
        ?.invoke(kotlinExtension) as? NamedDomainObjectContainer<*>
        ?: return
    sourceSets.named("main").configure { sourceSet ->
        val kotlinSourceDirectorySet = sourceSet.javaClass.methods
            .singleOrNull { method -> method.name == "getKotlin" && method.parameterCount == 0 }
            ?.invoke(sourceSet)
            ?: return@configure
        val srcDir = kotlinSourceDirectorySet.javaClass.methods
            .firstOrNull { method -> method.name == "srcDir" && method.parameterCount == 1 }
            ?: return@configure
        srcDir.invoke(kotlinSourceDirectorySet, moduleProject.layout.buildDirectory.dir("generated/cap4k/main/kotlin"))
    }
}

internal fun wireGeneratedSourceCompilation(
    rootProject: Project,
    config: ProjectConfig,
    generateSourcesTask: TaskProvider<out Task>,
) {
    generatedSourceModuleRoles(config).forEach { role ->
        val modulePath = config.modules[role] ?: return@forEach
        val moduleProject = resolveModuleProject(rootProject, modulePath) ?: return@forEach
        moduleProject.tasks.matching { it.name in GENERATED_SOURCE_CONSUMER_TASK_NAMES }.configureEach { task ->
            task.dependsOn(generateSourcesTask)
        }
    }
}

private val GENERATED_SOURCE_CONSUMER_TASK_NAMES = setOf("compileKotlin", "kspKotlin")

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

internal fun generatedSourceTaskInputSnapshot(rootProject: Project, config: ProjectConfig): String {
    val generatedRoots = generatedSourceModuleRoles(config)
        .sorted()
        .associateWith { role -> resolvedGeneratedKotlinSourceRoot(rootProject, config, role).orEmpty() }
    return GsonBuilder()
        .serializeNulls()
        .create()
        .toJson(
            linkedMapOf(
                "basePackage" to config.basePackage,
                "modules" to config.modules.toSortedMap(),
                "typeRegistry" to config.typeRegistry.toSortedMap(),
                "sources" to linkedMapOf(
                    "db" to sanitizedDbSourceSnapshot(config.sources["db"]),
                    "enumManifest" to sanitizedSourceSnapshot(config.sources["enum-manifest"]),
                ),
                "generators" to linkedMapOf(
                    "aggregate" to sanitizedGeneratorSnapshot(config.generators["aggregate"]),
                ),
                "artifactLayout" to config.artifactLayout,
                "templates" to linkedMapOf(
                    "preset" to config.templates.preset,
                    "overrideDirs" to config.templates.overrideDirs,
                    "conflictPolicy" to config.templates.conflictPolicy,
                ),
                "generatedSourceRoots" to generatedRoots,
            )
        )
}

private fun sanitizedDbSourceSnapshot(source: SourceConfig?): Map<String, Any?>? {
    if (source == null || !source.enabled) {
        return null
    }
    val options = source.options
    val snapshot = linkedMapOf<String, Any?>("enabled" to true)
    listOf("url", "username", "schema", "includeTables", "excludeTables").forEach { key ->
        if (options.containsKey(key)) {
            snapshot[key] = options[key]
        }
    }
    options["password"]?.toString()?.let { password ->
        snapshot["passwordHash"] = sha256Hex(password)
    }
    return snapshot
}

private fun sanitizedSourceSnapshot(source: SourceConfig?): Map<String, Any?>? {
    if (source == null || !source.enabled) {
        return null
    }
    return linkedMapOf(
        "enabled" to true,
        "options" to source.options.toSortedMap(),
    )
}

private fun sanitizedGeneratorSnapshot(generator: GeneratorConfig?): Map<String, Any?>? {
    if (generator == null || !generator.enabled) {
        return null
    }
    return linkedMapOf(
        "enabled" to true,
        "options" to generator.options.toSortedMap(),
    )
}

private fun sha256Hex(value: String): String =
    MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }

internal fun generatedSourceTaskInputFiles(
    project: Project,
    extension: Cap4kExtension,
    config: ProjectConfig,
): FileCollection {
    val inputs = mutableListOf<Any>()
    config.sources["enum-manifest"]
        ?.options
        ?.get("files")
        .asStringList()
        .mapTo(inputs) { project.file(it) }
    extension.types.registryFile.orNull?.let { registryFile ->
        inputs += project.file(registryFile)
    }
    config.sources["db"]
        ?.options
        ?.get("url")
        ?.toString()
        ?.let { dbUrl -> inputs.addAll(dbRunScriptInputFiles(project, dbUrl)) }
    config.templates.overrideDirs
        .map { project.file(it) }
        .filter { it.exists() }
        .mapTo(inputs) { overrideDir -> project.fileTree(overrideDir) }
    return project.files(inputs)
}

internal fun generatedSourceTaskHasUntrackedLiveDbInput(project: Project, config: ProjectConfig): Boolean {
    val dbSource = config.sources["db"] ?: return false
    if (!dbSource.enabled) {
        return false
    }
    val dbUrl = dbSource.options["url"]?.toString().orEmpty()
    return dbRunScriptInputFiles(project, dbUrl).isEmpty()
}

private fun dbRunScriptInputFiles(project: Project, dbUrl: String): List<File> {
    val runScriptPattern = Regex("""(?i)RUNSCRIPT\s+FROM\s+'([^']+)'""")
    return runScriptPattern.findAll(dbUrl)
        .map { match -> project.file(match.groupValues[1]) }
        .filter { file -> file.exists() }
        .toList()
}

private fun rebaseGeneratedSourcePlanItem(
    rootProject: Project,
    config: ProjectConfig,
    item: ArtifactPlanItem,
): ArtifactPlanItem {
    if (item.outputKind != ArtifactOutputKind.GENERATED_SOURCE) {
        return item
    }
    val moduleRoot = config.modules[item.moduleRole] ?: return item
    val plannedRoot = ArtifactLayoutResolver(config.basePackage, config.artifactLayout)
        .generatedKotlinSourceRoot(moduleRoot)
        .toSlashPath()
    val resolvedRoot = resolvedGeneratedKotlinSourceRoot(rootProject, config, item.moduleRole)
        ?: return item
    val normalizedOutputPath = item.outputPath.toSlashPath()
    if (normalizedOutputPath != plannedRoot && !normalizedOutputPath.startsWith("$plannedRoot/")) {
        return item.copy(resolvedOutputRoot = resolvedRoot)
    }
    val suffix = normalizedOutputPath.removePrefix(plannedRoot).trimStart('/')
    val rebasedOutputPath = listOf(resolvedRoot, suffix)
        .filter { it.isNotBlank() }
        .joinToString("/")
    return item.copy(
        outputPath = rebasedOutputPath,
        resolvedOutputRoot = resolvedRoot,
    )
}

private fun File.toRootRelativeSlash(rootProject: Project): String {
    val rootPath = rootProject.projectDir.canonicalFile.toPath().normalize()
    val filePath = canonicalFile.toPath().normalize()
    require(filePath.startsWith(rootPath)) {
        "Generated source root must stay under the root project directory: $filePath"
    }
    return rootPath.relativize(filePath).toString().toSlashPath()
}

private fun String.toSlashPath(): String =
    replace('\\', '/').trim('/')

internal fun buildSourceRunner(
    project: Project,
    config: ProjectConfig,
    exportEnabled: Boolean,
    generatedSourcesOnly: Boolean = false,
): PipelineRunner {
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
            val filesystemExporter = FilesystemArtifactExporter(project.projectDir.toPath())
            if (generatedSourcesOnly) {
                FilteringArtifactExporter(filesystemExporter) { artifact ->
                    artifact.outputKind == ArtifactOutputKind.GENERATED_SOURCE
                }
            } else {
                filesystemExporter
            }
        } else {
            NoopArtifactExporter()
        },
        transformPlanItem = { item -> rebaseGeneratedSourcePlanItem(project.rootProject, config, item) },
        includePlanItem = if (generatedSourcesOnly) {
            { item -> item.outputKind == ArtifactOutputKind.GENERATED_SOURCE }
        } else {
            { true }
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

internal fun buildGeneratedSourceRunner(project: Project, config: ProjectConfig): PipelineRunner =
    buildSourceRunner(project, config, exportEnabled = true, generatedSourcesOnly = true)

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

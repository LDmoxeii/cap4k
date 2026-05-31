package com.only4.cap4k.plugin.pipeline.gradle

import com.google.gson.GsonBuilder
import com.only4.cap4k.plugin.pipeline.api.ArtifactAddonProvider
import com.only4.cap4k.plugin.pipeline.api.ArtifactLayoutResolver
import com.only4.cap4k.plugin.pipeline.api.ArtifactOutputKind
import com.only4.cap4k.plugin.pipeline.api.ArtifactPlanItem
import com.only4.cap4k.plugin.pipeline.api.ConflictPolicy
import com.only4.cap4k.plugin.pipeline.api.GeneratorConfig
import com.only4.cap4k.plugin.pipeline.api.BootstrapRunner
import com.only4.cap4k.plugin.pipeline.api.BootstrapConfig
import com.only4.cap4k.plugin.pipeline.api.PipelineResult
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
import com.only4.cap4k.plugin.pipeline.generator.aggregate.EnumManifestArtifactPlanner
import com.only4.cap4k.plugin.pipeline.generator.design.DesignApiPayloadArtifactPlanner
import com.only4.cap4k.plugin.pipeline.generator.design.DesignClientArtifactPlanner
import com.only4.cap4k.plugin.pipeline.generator.design.DesignClientHandlerArtifactPlanner
import com.only4.cap4k.plugin.pipeline.generator.design.DesignCommandArtifactPlanner
import com.only4.cap4k.plugin.pipeline.generator.design.DesignDomainEventArtifactPlanner
import com.only4.cap4k.plugin.pipeline.generator.design.DesignDomainEventHandlerArtifactPlanner
import com.only4.cap4k.plugin.pipeline.generator.design.DesignDomainServiceArtifactPlanner
import com.only4.cap4k.plugin.pipeline.generator.design.DesignIntegrationEventArtifactPlanner
import com.only4.cap4k.plugin.pipeline.generator.design.DesignIntegrationEventSubscriberArtifactPlanner
import com.only4.cap4k.plugin.pipeline.generator.design.DesignQueryArtifactPlanner
import com.only4.cap4k.plugin.pipeline.generator.design.DesignQueryHandlerArtifactPlanner
import com.only4.cap4k.plugin.pipeline.generator.design.DesignSagaArtifactPlanner
import com.only4.cap4k.plugin.pipeline.generator.drawingboard.DrawingBoardArtifactPlanner
import com.only4.cap4k.plugin.pipeline.generator.flow.FlowArtifactPlanner
import com.only4.cap4k.plugin.pipeline.generator.types.ValueObjectArtifactPlanner
import com.only4.cap4k.plugin.pipeline.renderer.pebble.PebbleArtifactRenderer
import com.only4.cap4k.plugin.pipeline.renderer.pebble.PebbleBootstrapRenderer
import com.only4.cap4k.plugin.pipeline.renderer.pebble.PresetTemplateResolver
import com.only4.cap4k.plugin.pipeline.source.db.DbSchemaSourceProvider
import com.only4.cap4k.plugin.pipeline.source.designjson.DesignJsonSourceProvider
import com.only4.cap4k.plugin.pipeline.source.enummanifest.EnumManifestSourceProvider
import com.only4.cap4k.plugin.pipeline.source.ir.IrAnalysisSourceProvider
import com.only4.cap4k.plugin.pipeline.source.valueobject.ValueObjectManifestSourceProvider
import com.only4.cap4k.plugin.pipeline.generator.aggregate.AggregateProjectionArtifactPlanner
import org.gradle.api.file.FileCollection
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.TaskProvider
import java.io.File
import java.net.URLClassLoader
import java.nio.file.Path
import java.security.MessageDigest

class PipelinePlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val extension = project.extensions.create("cap4k", Cap4kExtension::class.java)
        val configFactory = Cap4kProjectConfigFactory()
        val bootstrapConfigFactory = Cap4kBootstrapConfigFactory()

        project.configurations.create(CAP4K_ADDON_CONFIGURATION_NAME) { configuration ->
            configuration.isCanBeConsumed = false
            configuration.isCanBeResolved = true
            configuration.description = "Build-time cap4k artifact addon dependencies."
        }

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
            ensureAggregateProjectionAdapterJpaDependency(project, config)
            ensureEnumManifestDomainDependencies(project, config)
            ensureValueObjectDomainDependencies(project, config)
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
private const val JACKSON_ANNOTATIONS_GROUP = "com.fasterxml.jackson.core"
private const val JACKSON_ANNOTATIONS_NAME = "jackson-annotations"
private const val JACKSON_ANNOTATIONS_COORDINATE = "$JACKSON_ANNOTATIONS_GROUP:$JACKSON_ANNOTATIONS_NAME:2.17.2"
private const val JACKSON_DATABIND_GROUP = "com.fasterxml.jackson.core"
private const val JACKSON_DATABIND_NAME = "jackson-databind"
private const val JACKSON_DATABIND_COORDINATE = "$JACKSON_DATABIND_GROUP:$JACKSON_DATABIND_NAME:2.17.2"
private const val JACKSON_MODULE_KOTLIN_GROUP = "com.fasterxml.jackson.module"
private const val JACKSON_MODULE_KOTLIN_NAME = "jackson-module-kotlin"
private const val JACKSON_MODULE_KOTLIN_COORDINATE =
    "$JACKSON_MODULE_KOTLIN_GROUP:$JACKSON_MODULE_KOTLIN_NAME:2.17.2"
private const val CAP4K_ADDON_CONFIGURATION_NAME = "cap4kAddon"
private val SOURCE_TASK_SOURCE_IDS = setOf("db", "design-json", "enum-manifest", "value-object-manifest")
private val SOURCE_TASK_GENERATOR_IDS = setOf(
    "command",
    "query",
    "query-handler",
    "client",
    "client-handler",
    "api-payload",
    "domain-event",
    "domain-subscriber",
    "domain-service",
    "saga",
    "integration-event",
    "integration-subscriber",
    "types-value-object",
    "aggregate",
    "aggregate-projection",
)
private val GENERATED_SOURCE_TASK_SOURCE_IDS = setOf("db", "enum-manifest")
private val GENERATED_SOURCE_TASK_GENERATOR_IDS = setOf("aggregate", "aggregate-projection")
private val ANALYSIS_TASK_SOURCE_IDS = setOf("ir-analysis")
private val ANALYSIS_TASK_GENERATOR_IDS = setOf("flow", "drawing-board")

internal fun artifactAddonClasspath(project: Project): FileCollection =
    project.configurations.findByName(CAP4K_ADDON_CONFIGURATION_NAME)
        ?: project.files()

private fun hasEnabledRegularSource(extension: Cap4kExtension): Boolean = listOf(
    extension.sources.db.enabled,
).any { it.orNull == true } ||
    extension.sources.designJson.manifestFile.orNull?.isNotBlank() == true ||
    !extension.sources.designJson.files.isEmpty ||
    !extension.sources.irAnalysis.inputDirs.isEmpty ||
    !extension.types.enumManifest.files.isEmpty ||
    !extension.types.valueObjectManifest.files.isEmpty

private fun hasEnabledRegularGenerator(extension: Cap4kExtension): Boolean =
    extension.generators.aggregate.configured ||
        extension.generators.aggregateProjection.configured

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
    if ("aggregate" !in config.generators) {
        return
    }
    ensureJpaDependency(project, config, moduleRole = "domain")
    ensureJacksonAnnotationsDependency(project, config, moduleRole = "domain")
}

internal fun ensureAggregateProjectionAdapterJpaDependency(project: Project, config: ProjectConfig) {
    if ("aggregate-projection" !in config.generators) {
        return
    }
    ensureJpaDependency(project, config, moduleRole = "adapter")
}

internal fun ensureEnumManifestDomainDependencies(project: Project, config: ProjectConfig) {
    if ("enum-manifest" !in config.sources) {
        return
    }
    ensureJpaDependency(project, config, moduleRole = "domain")
}

internal fun ensureValueObjectDomainDependencies(project: Project, config: ProjectConfig) {
    if ("value-object-manifest" !in config.sources) {
        return
    }
    ensureJpaDependency(project, config, moduleRole = "domain")
    ensureJacksonDatabindDependency(project, config, moduleRole = "domain")
    ensureJacksonModuleKotlinDependency(project, config, moduleRole = "domain")
}

private fun ensureJpaDependency(project: Project, config: ProjectConfig, moduleRole: String) {
    ensureImplementationDependency(
        project = project,
        config = config,
        moduleRole = moduleRole,
        group = JAKARTA_PERSISTENCE_GROUP,
        name = JAKARTA_PERSISTENCE_NAME,
        coordinate = JAKARTA_PERSISTENCE_COORDINATE,
    )
}

private fun ensureJacksonAnnotationsDependency(project: Project, config: ProjectConfig, moduleRole: String) {
    ensureImplementationDependency(
        project = project,
        config = config,
        moduleRole = moduleRole,
        group = JACKSON_ANNOTATIONS_GROUP,
        name = JACKSON_ANNOTATIONS_NAME,
        coordinate = JACKSON_ANNOTATIONS_COORDINATE,
    )
}

private fun ensureJacksonDatabindDependency(project: Project, config: ProjectConfig, moduleRole: String) {
    ensureImplementationDependency(
        project = project,
        config = config,
        moduleRole = moduleRole,
        group = JACKSON_DATABIND_GROUP,
        name = JACKSON_DATABIND_NAME,
        coordinate = JACKSON_DATABIND_COORDINATE,
    )
}

private fun ensureJacksonModuleKotlinDependency(project: Project, config: ProjectConfig, moduleRole: String) {
    ensureImplementationDependency(
        project = project,
        config = config,
        moduleRole = moduleRole,
        group = JACKSON_MODULE_KOTLIN_GROUP,
        name = JACKSON_MODULE_KOTLIN_NAME,
        coordinate = JACKSON_MODULE_KOTLIN_COORDINATE,
    )
}

private fun ensureImplementationDependency(
    project: Project,
    config: ProjectConfig,
    moduleRole: String,
    group: String,
    name: String,
    coordinate: String,
) {
    val modulePath = config.modules[moduleRole] ?: return
    val moduleProject = resolveModuleProject(project.rootProject, modulePath) ?: return
    val implementationConfiguration = moduleProject.configurations.findByName("implementation") ?: return
    val hasDependency = implementationConfiguration.dependencies.any { dependency ->
        dependency.group == group && dependency.name == name
    }
    if (!hasDependency) {
        moduleProject.dependencies.add("implementation", coordinate)
    }
}

internal fun generatedSourceModuleRoles(config: ProjectConfig): Set<String> {
    val roles = linkedSetOf<String>()
    val aggregate = config.generators["aggregate"]
    if (aggregate != null) {
        roles += "domain"
        roles += "adapter"
        if (aggregate.options["artifact.unique"] as? Boolean == true) {
            roles += "application"
        }
    }
    if ("aggregate-projection" in config.generators) {
        roles += "adapter"
    }
    if ("enum-manifest" in config.sources) {
        roles += "domain"
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

private val GENERATED_SOURCE_CONSUMER_TASK_NAMES = setOf("compileKotlin")

internal fun inferDependencies(project: Project, config: ProjectConfig): List<Task> {
    val mergedDependencies = linkedSetOf<Task>()
    mergedDependencies += inferSourceDependencies(project, config)
    mergedDependencies += inferAnalysisDependencies(project, config)
    return mergedDependencies.toList()
}

internal fun inferSourceDependencies(
    @Suppress("UNUSED_PARAMETER") project: Project,
    @Suppress("UNUSED_PARAMETER") config: ProjectConfig,
): List<Task> {
    return emptyList()
}

internal fun inferAnalysisDependencies(project: Project, config: ProjectConfig): List<Task> {
    val inferredDependencies = linkedSetOf<Task>()
    val allProjects = project.rootProject.allprojects
    val shouldDependOnCompileKotlin = config.sources.containsKey("ir-analysis")
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
                "typeRegistry" to linkedMapOf(
                    "entries" to config.typeRegistry.entries.toSortedMap(),
                    "registryFile" to config.typeRegistry.registryFile,
                ),
                "sources" to linkedMapOf(
                    "db" to sanitizedDbSourceSnapshot(config.sources["db"]),
                ),
                "typeManifests" to linkedMapOf(
                    "enumManifestFiles" to config.typeRegistry.enumManifestFiles,
                    "valueObjectManifestFiles" to config.typeRegistry.valueObjectManifestFiles,
                ),
                "generators" to linkedMapOf(
                    "aggregate" to sanitizedGeneratorSnapshot(config.generators["aggregate"]),
                    "aggregateProjection" to sanitizedGeneratorSnapshot(config.generators["aggregate-projection"]),
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
    if (source == null) {
        return null
    }
    val options = source.options
    val snapshot = linkedMapOf<String, Any?>()
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
    if (source == null) {
        return null
    }
    return linkedMapOf(
        "options" to source.options.toSortedMap(),
    )
}

private fun sanitizedGeneratorSnapshot(generator: GeneratorConfig?): Map<String, Any?>? {
    if (generator == null) {
        return null
    }
    return linkedMapOf(
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
    config.typeRegistry.enumManifestFiles.mapTo(inputs) { project.file(it) }
    config.typeRegistry.valueObjectManifestFiles.mapTo(inputs) { project.file(it) }
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
    val addonRuntime = loadArtifactAddonRuntime(project)
    val runner = DefaultPipelineRunner(
        sources = listOf(
            DbSchemaSourceProvider(),
            EnumManifestSourceProvider(),
            ValueObjectManifestSourceProvider(),
            DesignJsonSourceProvider(),
        ),
        generators = listOf(
            DesignCommandArtifactPlanner(),
            DesignQueryArtifactPlanner(),
            DesignQueryHandlerArtifactPlanner(),
            DesignClientArtifactPlanner(),
            DesignClientHandlerArtifactPlanner(),
            DesignApiPayloadArtifactPlanner(),
            DesignDomainEventArtifactPlanner(),
            DesignDomainEventHandlerArtifactPlanner(),
            DesignDomainServiceArtifactPlanner(),
            DesignSagaArtifactPlanner(),
            DesignIntegrationEventArtifactPlanner(),
            DesignIntegrationEventSubscriberArtifactPlanner(),
            ValueObjectArtifactPlanner(),
            EnumManifestArtifactPlanner(),
            AggregateArtifactPlanner(),
            AggregateProjectionArtifactPlanner(),
        ),
        assembler = DefaultCanonicalAssembler(),
        renderer = PebbleArtifactRenderer(
            PresetTemplateResolver(
                preset = config.templates.preset,
                overrideDirs = config.templates.overrideDirs,
                addonTemplateClassLoaders = addonRuntime.templateClassLoaders,
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
        addonProviders = addonRuntime.providers,
    )
    return ValueObjectManifestSourceConfigPipelineRunner(project, runner.closeAfterRun(addonRuntime))
}

private class ValueObjectManifestSourceConfigPipelineRunner(
    private val project: Project,
    private val delegate: PipelineRunner,
) : PipelineRunner {
    override fun run(config: ProjectConfig): PipelineResult =
        delegate.run(config.withValueObjectManifestSourceConfig(project))
}

private fun ProjectConfig.withValueObjectManifestSourceConfig(project: Project): ProjectConfig {
    if (typeRegistry.valueObjectManifestFiles.isEmpty()) {
        return this
    }
    val files = typeRegistry.valueObjectManifestFiles.map { path -> project.file(path).absolutePath }
    val sourceConfig = SourceConfig(
        options = mapOf("files" to files),
    )
    return copy(
        sources = sources + ("value-object-manifest" to sourceConfig)
    )
}

internal fun buildAnalysisRunner(project: Project, config: ProjectConfig, exportEnabled: Boolean): PipelineRunner {
    val addonRuntime = loadArtifactAddonRuntime(project)
    val runner = DefaultPipelineRunner(
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
                addonTemplateClassLoaders = addonRuntime.templateClassLoaders,
            )
        ),
        exporter = if (exportEnabled) {
            FilesystemArtifactExporter(project.projectDir.toPath())
        } else {
            NoopArtifactExporter()
        },
        addonProviders = addonRuntime.providers,
    )
    return runner.closeAfterRun(addonRuntime)
}

internal data class ArtifactAddonRuntime(
    val providers: List<ArtifactAddonProvider>,
    val templateClassLoaders: Map<String, ClassLoader>,
    val closeables: List<AutoCloseable>,
)

private fun loadArtifactAddonRuntime(project: Project): ArtifactAddonRuntime {
    val configuration = project.configurations.findByName(CAP4K_ADDON_CONFIGURATION_NAME)
        ?: return emptyArtifactAddonRuntime()
    return loadArtifactAddonRuntime(
        files = configuration.files,
        parent = ArtifactAddonLoader::class.java.classLoader,
    )
}

internal fun loadArtifactAddonRuntime(
    files: Collection<File>,
    parent: ClassLoader,
    classLoaderFactory: (Collection<File>, ClassLoader) -> URLClassLoader = ArtifactAddonLoader::classLoader,
    providerLoader: (ClassLoader) -> List<ArtifactAddonProvider> = ArtifactAddonLoader::load,
    templateClassLoaderFactory: (ArtifactAddonProvider) -> URLClassLoader = ArtifactAddonLoader::templateClassLoader,
): ArtifactAddonRuntime {
    if (files.isEmpty()) {
        return emptyArtifactAddonRuntime()
    }
    val classLoader = classLoaderFactory(files, parent)
    val providers = try {
        providerLoader(classLoader)
    } catch (failure: Throwable) {
        closeAfterLoadFailure(classLoader, failure)
        throw failure
    }
    val closeables = mutableListOf<AutoCloseable>(classLoader)
    val templateClassLoaders = linkedMapOf<String, ClassLoader>()
    try {
        providers.forEach { provider ->
            val templateClassLoader = templateClassLoaderFactory(provider)
            closeables += templateClassLoader
            templateClassLoaders[provider.id] = templateClassLoader
        }
    } catch (failure: Throwable) {
        closeAfterLoadFailure(closeables.asReversed(), failure)
        throw failure
    }
    return ArtifactAddonRuntime(
        providers = providers,
        templateClassLoaders = templateClassLoaders,
        closeables = closeables,
    )
}

private fun emptyArtifactAddonRuntime(): ArtifactAddonRuntime =
    ArtifactAddonRuntime(
        providers = emptyList(),
        templateClassLoaders = emptyMap(),
        closeables = emptyList(),
    )

private fun closeAfterLoadFailure(closeable: AutoCloseable, failure: Throwable) {
    closeAfterLoadFailure(listOf(closeable), failure)
}

private fun closeAfterLoadFailure(closeables: Iterable<AutoCloseable>, failure: Throwable) {
    closeables.forEach { closeable ->
        try {
            closeable.close()
        } catch (closeFailure: Throwable) {
            failure.addSuppressed(closeFailure)
        }
    }
}

private fun PipelineRunner.closeAfterRun(runtime: ArtifactAddonRuntime): PipelineRunner =
    if (runtime.closeables.isEmpty()) {
        this
    } else {
        CloseablePipelineRunner(this, runtime.closeables)
    }

internal class CloseablePipelineRunner(
    private val delegate: PipelineRunner,
    private val closeables: List<AutoCloseable>,
) : PipelineRunner {
    override fun run(config: ProjectConfig): PipelineResult {
        var primaryFailure: Throwable? = null
        try {
            return delegate.run(config)
        } catch (failure: Throwable) {
            primaryFailure = failure
            throw failure
        } finally {
            closeAll(primaryFailure)
        }
    }

    private fun closeAll(primaryFailure: Throwable?) {
        var closeFailure: Throwable? = null
        closeables.asReversed().forEach { closeable ->
            try {
                closeable.close()
            } catch (failure: Throwable) {
                if (primaryFailure != null) {
                    primaryFailure.addSuppressed(failure)
                } else if (closeFailure == null) {
                    closeFailure = failure
                } else {
                    closeFailure.addSuppressed(failure)
                }
            }
        }
        if (primaryFailure == null && closeFailure != null) {
            throw closeFailure
        }
    }
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

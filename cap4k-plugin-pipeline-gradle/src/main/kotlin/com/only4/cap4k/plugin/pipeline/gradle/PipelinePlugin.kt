package com.only4.cap4k.plugin.pipeline.gradle

import com.only4.cap4k.plugin.pipeline.json.PipelineJson
import com.only4.cap4k.plugin.pipeline.api.ArtifactAddonProvider
import com.only4.cap4k.plugin.pipeline.api.ArtifactLayoutResolver
import com.only4.cap4k.plugin.pipeline.api.ArtifactOutputKind
import com.only4.cap4k.plugin.pipeline.api.ArtifactPlanItem
import com.only4.cap4k.plugin.pipeline.api.ConflictPolicy
import com.only4.cap4k.plugin.pipeline.api.GeneratorConfig
import com.only4.cap4k.plugin.pipeline.api.GeneratorProvider
import com.only4.cap4k.plugin.pipeline.api.PipelineResult
import com.only4.cap4k.plugin.pipeline.api.PipelineContribution
import com.only4.cap4k.plugin.pipeline.api.PipelineContributionBinding
import com.only4.cap4k.plugin.pipeline.api.PipelineExtensionProvider
import com.only4.cap4k.plugin.pipeline.api.ManagedFieldPolicyProvider
import com.only4.cap4k.plugin.pipeline.api.PipelineRunner
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig
import com.only4.cap4k.plugin.pipeline.api.PipelinePublicTasks
import com.only4.cap4k.plugin.pipeline.api.SourceConfig
import com.only4.cap4k.plugin.pipeline.api.SourceProvider
import com.only4.cap4k.plugin.pipeline.core.DefaultCanonicalAssembler
import com.only4.cap4k.plugin.pipeline.core.DefaultPipelineRunner
import com.only4.cap4k.plugin.pipeline.core.FilteringArtifactExporter
import com.only4.cap4k.plugin.pipeline.core.FilesystemArtifactExporter
import com.only4.cap4k.plugin.pipeline.core.NoopArtifactExporter
import com.only4.cap4k.plugin.pipeline.generator.aggregate.AggregateArtifactPlanner
import com.only4.cap4k.plugin.pipeline.generator.aggregate.EnumManifestArtifactPlanner
import com.only4.cap4k.plugin.pipeline.generator.design.DesignApiPayloadArtifactPlanner
import com.only4.cap4k.plugin.pipeline.generator.design.DesignCapabilityArtifactPlanner
import com.only4.cap4k.plugin.pipeline.generator.design.DesignCapabilityHandlerArtifactPlanner
import com.only4.cap4k.plugin.pipeline.generator.design.DesignCommandArtifactPlanner
import com.only4.cap4k.plugin.pipeline.generator.design.DesignDomainEventArtifactPlanner
import com.only4.cap4k.plugin.pipeline.generator.design.DesignDomainEventHandlerArtifactPlanner
import com.only4.cap4k.plugin.pipeline.generator.design.DesignDomainServiceArtifactPlanner
import com.only4.cap4k.plugin.pipeline.generator.design.DesignEndpointArtifactPlanner
import com.only4.cap4k.plugin.pipeline.generator.design.DesignIntegrationEventArtifactPlanner
import com.only4.cap4k.plugin.pipeline.generator.design.DesignIntegrationEventSubscriberArtifactPlanner
import com.only4.cap4k.plugin.pipeline.generator.design.DesignQueryArtifactPlanner
import com.only4.cap4k.plugin.pipeline.generator.design.DesignQueryHandlerArtifactPlanner
import com.only4.cap4k.plugin.pipeline.generator.drawingboard.DrawingBoardArtifactPlanner
import com.only4.cap4k.plugin.pipeline.generator.flow.FlowArtifactPlanner
import com.only4.cap4k.plugin.pipeline.generator.types.ValueObjectArtifactPlanner
import com.only4.cap4k.plugin.pipeline.renderer.pebble.PebbleArtifactRenderer
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
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

class PipelinePlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val extension = project.extensions.create("cap4k", Cap4kExtension::class.java)
        val configFactory = Cap4kProjectConfigFactory()

        project.configurations.create(CAP4K_PIPELINE_EXTENSION_CONFIGURATION_NAME) { configuration ->
            configuration.isCanBeConsumed = false
            configuration.isCanBeResolved = true
            configuration.description = "Build-time Cap4k Pipeline Extension dependencies."
        }

        val planTask = project.tasks.register(PipelinePublicTasks.PLAN, Cap4kPlanTask::class.java) { task ->
            task.group = "cap4k"
            task.description = "Plans Cap4k pipeline artifacts."
            task.extension = extension
            task.configFactory = configFactory
        }
        val generateTask = project.tasks.register(PipelinePublicTasks.GENERATE, Cap4kGenerateTask::class.java) { task ->
            task.group = "cap4k"
            task.description = "Generates artifacts from the Cap4k pipeline."
            task.extension = extension
            task.configFactory = configFactory
        }
        val generateSourcesTask = project.tasks.register(PipelinePublicTasks.GENERATE_SOURCES, Cap4kGenerateSourcesTask::class.java) { task ->
            task.group = "cap4k"
            task.description = "Generates build-owned Kotlin sources from the Cap4k pipeline."
            task.extension = extension
            task.configFactory = configFactory
        }
        val analysisPlanTask = project.tasks.register(PipelinePublicTasks.ANALYSIS_PLAN, Cap4kAnalysisPlanTask::class.java) { task ->
            task.group = "cap4k"
            task.description = "Plans Cap4k analysis export artifacts."
            task.extension = extension
            task.configFactory = configFactory
        }
        val analysisGenerateTask = project.tasks.register(PipelinePublicTasks.ANALYSIS_GENERATE, Cap4kAnalysisGenerateTask::class.java) { task ->
            task.group = "cap4k"
            task.description = "Generates artifacts from analysis snapshots."
            task.extension = extension
            task.configFactory = configFactory
        }
        project.tasks.register(PipelinePublicTasks.AGENT_SNAPSHOT, Cap4kAgentSnapshotTask::class.java) { task ->
            task.group = "cap4k"
            task.description = "Writes a read-only, manifest-first Cap4k project snapshot for agents."
            task.extension = extension
            task.configFactory = configFactory
        }

        project.gradle.projectsEvaluated {
            if (!shouldInferPipelineDependencies(extension)) {
                return@projectsEvaluated
            }
            val config = try {
                configFactory.build(project, extension)
            } catch (failure: RuntimeException) {
                project.logger.info(
                    "Cap4k dependency inference was skipped because project configuration is invalid; " +
                        "task execution will report the validation failure: ${failure.message}"
                )
                return@projectsEvaluated
            }
            ensureAggregateDomainJpaDependency(project, config)
            ensureAggregateProjectionAdapterJpaDependency(project, config)
            ensureEnumManifestDomainDependencies(project, config)
            ensureValueObjectDomainDependencies(project, config)
            ensureAnalysisMetadataCompileOnlyDependencies(project, config)
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
    hasConfiguredProjectLayout(extension) ||
        hasEnabledRegularSource(extension) ||
        hasEnabledRegularGenerator(extension)

private const val CAP4K_ANALYSIS_METADATA_GROUP = "io.github.ldmoxeii"
private const val CAP4K_ANALYSIS_METADATA_NAME = "cap4k-analysis-metadata"
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
private const val CAP4K_PIPELINE_EXTENSION_CONFIGURATION_NAME = "cap4kPipelineExtension"
private const val GENERATED_SOURCE_MANAGED_ROOTS_STATE_VERSION = 1
private const val GENERATED_SOURCE_MANAGED_ROOTS_STATE_PATH = "cap4k/generated-source-managed-roots.json"
private val GENERATED_SOURCE_MANAGED_ROLES = setOf("domain", "adapter")
private val SOURCE_TASK_SOURCE_IDS = setOf("db", "design-json", "enum-manifest", "value-object-manifest")
private val SOURCE_TASK_GENERATOR_IDS = setOf(
    "command",
    "query",
    "query-handler",
    "capability",
    "capability-handler",
    "api-payload",
    "endpoint",
    "domain-event",
    "domain-subscriber",
    "domain-service",
    "integration-event",
    "integration-subscriber",
    "types-value-object",
    "aggregate",
    "aggregate-projection",
)
private val GENERATED_SOURCE_TASK_SOURCE_IDS = setOf("db", "enum-manifest", "value-object-manifest")
private val GENERATED_SOURCE_TASK_GENERATOR_IDS = setOf("types-value-object", "aggregate", "aggregate-projection")

internal fun pipelineExtensionClasspath(project: Project): FileCollection =
    project.configurations.findByName(CAP4K_PIPELINE_EXTENSION_CONFIGURATION_NAME)
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

private fun hasConfiguredProjectLayout(extension: Cap4kExtension): Boolean =
    extension.project.basePackage.isPresent && listOf(
        extension.project.contractModulePath,
        extension.project.domainModulePath,
        extension.project.applicationModulePath,
        extension.project.adapterModulePath,
    ).any { it.isPresent }

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

internal fun analysisTaskConfig(config: ProjectConfig): ProjectConfig {
    val sourceIds = builtInAnalysisSourceProviders().mapTo(linkedSetOf(), SourceProvider::id)
    val generatorIds = builtInAnalysisGeneratorProviders().mapTo(linkedSetOf(), GeneratorProvider::id)
    return config.copy(
        sources = config.sources.filterKeys(sourceIds::contains),
        generators = config.generators.filterKeys(generatorIds::contains),
    )
}


internal fun ensureAggregateDomainJpaDependency(project: Project, config: ProjectConfig) {
    if ("aggregate" !in config.generators) {
        return
    }
    ensureJpaDependency(project, config, moduleRole = "domain")
    ensureJacksonAnnotationsDependency(project, config, moduleRole = "domain")
    ensureJacksonDatabindDependency(project, config, moduleRole = "domain")
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
    if (!hasJsonValueObjectPersistenceProjection(project, config)) {
        return
    }
    ensureJpaDependency(project, config, moduleRole = "domain")
    ensureJacksonDatabindDependency(project, config, moduleRole = "domain")
    ensureJacksonModuleKotlinDependency(project, config, moduleRole = "domain")
}

internal fun ensureAnalysisMetadataCompileOnlyDependencies(project: Project, config: ProjectConfig) {
    val moduleRoles = analysisMetadataModuleRoles(config)
    if (moduleRoles.isEmpty()) {
        return
    }
    val coordinate = analysisMetadataCoordinate()
    moduleRoles.forEach { moduleRole ->
        ensureCompileOnlyModuleDependency(
            project = project,
            config = config,
            moduleRole = moduleRole,
            group = CAP4K_ANALYSIS_METADATA_GROUP,
            name = CAP4K_ANALYSIS_METADATA_NAME,
            coordinate = coordinate,
        )
    }
}

internal fun analysisMetadataModuleRoles(config: ProjectConfig): Set<String> {
    val roles = linkedSetOf<String>()
    if ("design-json" in config.sources) {
        roles += "domain"
        roles += "application"
        roles += "adapter"
    }
    if ("enum-manifest" in config.sources) {
        roles += "domain"
    }
    if ("value-object-manifest" in config.sources) {
        roles += "domain"
    }
    if ("aggregate" in config.generators) {
        roles += "domain"
        roles += "adapter"
    }
    if ("aggregate-projection" in config.generators) {
        roles += "adapter"
    }
    return roles.filterTo(linkedSetOf()) { role -> role in config.modules }
}

private fun hasJsonValueObjectPersistenceProjection(project: Project, config: ProjectConfig): Boolean {
    val source = config.sources["value-object-manifest"] ?: return false
    val configuredFiles = source.options["files"].asStringList()
    val files = configuredFiles
        .ifEmpty { config.typeRegistry.valueObjectManifestFiles }
        .map { path -> project.file(path).toPath() }
    if (files.isEmpty()) {
        return false
    }
    return ValueObjectManifestSourceProvider()
        .load(files)
        .declarations
        .any { declaration -> declaration.persistence?.kind.equals("json", ignoreCase = true) }
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

private fun analysisMetadataCoordinate(): String {
    val version = PipelinePlugin::class.java.`package`.implementationVersion
        ?.takeIf { value -> value.isNotBlank() }
        ?: "development"
    return "$CAP4K_ANALYSIS_METADATA_GROUP:$CAP4K_ANALYSIS_METADATA_NAME:$version"
}

private fun ensureCompileOnlyModuleDependency(
    project: Project,
    config: ProjectConfig,
    moduleRole: String,
    group: String,
    name: String,
    coordinate: String,
) {
    val modulePath = config.modules[moduleRole] ?: return
    val moduleProject = resolveModuleProject(project.rootProject, modulePath) ?: return
    val compileOnlyConfiguration = moduleProject.configurations.findByName("compileOnly") ?: return
    val hasDependency = compileOnlyConfiguration.dependencies.any { dependency ->
        dependency.group == group && dependency.name == name
    }
    if (!hasDependency) {
        moduleProject.dependencies.add("compileOnly", coordinate)
    }
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
    }
    if ("aggregate-projection" in config.generators) {
        roles += "adapter"
    }
    if ("enum-manifest" in config.sources) {
        roles += "domain"
    }
    if ("value-object-manifest" in config.sources) {
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

internal fun generatedSourceManagedOutputDirectories(rootProject: Project, config: ProjectConfig): List<File> =
    (generatedSourceOutputDirectories(rootProject, config) + readManagedGeneratedSourceOutputDirectories(rootProject))
        .distinctBy { directory -> directory.canonicalFile.path }

internal fun cleanGeneratedSourceOutputDirectories(rootProject: Project, config: ProjectConfig) {
    generatedSourceManagedOutputDirectories(rootProject, config).forEach { outputDirectory ->
        validateGeneratedSourceCleanupTarget(rootProject, outputDirectory)
        rootProject.delete(outputDirectory)
    }
}

internal fun ensureGeneratedSourceOutputDirectories(rootProject: Project, config: ProjectConfig) {
    generatedSourceOutputDirectories(rootProject, config).forEach { outputDirectory ->
        require(outputDirectory.mkdirs() || outputDirectory.isDirectory) {
            "Failed to create Cap4k generated Kotlin source root: ${outputDirectory.absolutePath}"
        }
    }
}

internal fun recordManagedGeneratedSourceOutputDirectories(rootProject: Project, config: ProjectConfig) {
    val roots = generatedSourceModuleRoles(config)
        .sorted()
        .mapNotNull { role ->
            generatedKotlinSourceDirectory(rootProject, config, role)
                ?.let { directory -> role to directory.toRootRelativeSlash(rootProject) }
        }
        .toMap()
    val stateFile = generatedSourceManagedRootsStateFile(rootProject)
    require(stateFile.parentFile.mkdirs() || stateFile.parentFile.isDirectory) {
        "Failed to create Cap4k generated source state directory: ${stateFile.parentFile.absolutePath}"
    }
    val mapper = PipelineJson.newMapper()
    val content = PipelineJson.prettyWriter(mapper)
        .writeValueAsString(GeneratedSourceManagedRootsState(roots = roots)) + "\n"
    val temporaryFile = File(stateFile.parentFile, ".${stateFile.name}.tmp")
    temporaryFile.writeText(content, Charsets.UTF_8)
    try {
        Files.move(
            temporaryFile.toPath(),
            stateFile.toPath(),
            StandardCopyOption.ATOMIC_MOVE,
            StandardCopyOption.REPLACE_EXISTING,
        )
    } catch (_: AtomicMoveNotSupportedException) {
        Files.move(temporaryFile.toPath(), stateFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }
}

internal fun generatedSourceManagedRootsStateFile(rootProject: Project): File =
    rootProject.layout.buildDirectory.file(GENERATED_SOURCE_MANAGED_ROOTS_STATE_PATH).get().asFile

internal fun readManagedGeneratedSourceOutputRoots(rootProject: Project): Map<String, String> {
    val stateFile = generatedSourceManagedRootsStateFile(rootProject)
    if (!stateFile.isFile) return emptyMap()
    val state = try {
        requireNotNull(
            PipelineJson.newMapper().readValue(
                stateFile.readText(Charsets.UTF_8),
                GeneratedSourceManagedRootsState::class.java,
            )
        ) {
            "Managed-root state must contain a JSON object"
        }
    } catch (ex: RuntimeException) {
        throw IllegalStateException("Invalid Cap4k generated source managed-root state: ${stateFile.absolutePath}", ex)
    }
    require(state.version == GENERATED_SOURCE_MANAGED_ROOTS_STATE_VERSION) {
        "Unsupported Cap4k generated source managed-root state version ${state.version}: ${stateFile.absolutePath}"
    }
    val roots = requireNotNull(state.roots) {
        "Invalid Cap4k generated source managed-root state without roots: ${stateFile.absolutePath}"
    }
    return roots.toSortedMap().mapValues { (role, relativePath) ->
        require(role in GENERATED_SOURCE_MANAGED_ROLES) {
            "Invalid Cap4k generated source managed role $role in ${stateFile.absolutePath}"
        }
        val path = Path.of(relativePath).normalize()
        require(relativePath.isNotBlank() && !path.isAbsolute && path.root == null &&
            (path.nameCount == 0 || path.getName(0).toString() != "..")) {
            "Invalid Cap4k generated source managed root for $role: $relativePath"
        }
        rootProject.projectDir.resolve(path.toString()).also { outputDirectory ->
            validateGeneratedSourceCleanupTarget(rootProject, outputDirectory)
        }.toRootRelativeSlash(rootProject)
    }
}

private fun readManagedGeneratedSourceOutputDirectories(rootProject: Project): List<File> =
    readManagedGeneratedSourceOutputRoots(rootProject).values.map { path -> rootProject.file(path) }

private fun validateGeneratedSourceCleanupTarget(rootProject: Project, outputDirectory: File) {
    val rootPath = rootProject.projectDir.canonicalFile.toPath().normalize()
    val normalized = outputDirectory.canonicalFile.toPath().normalize()
    require(normalized.startsWith(rootPath)) {
        "Generated source cleanup target must stay under the root project directory: $normalized"
    }
    require(normalized.endsWith(Path.of("build", "generated", "cap4k", "main", "kotlin"))) {
        "Generated source cleanup target is not a Cap4k generated Kotlin root: $normalized"
    }
}

private data class GeneratedSourceManagedRootsState(
    val version: Int = GENERATED_SOURCE_MANAGED_ROOTS_STATE_VERSION,
    val roots: Map<String, String>? = emptyMap(),
)

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
    config.modules.keys.forEach { role ->
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
    return PipelineJson.newMapper(includeNulls = true)
        .writeValueAsString(
            linkedMapOf(
                "basePackage" to config.basePackage,
                "modules" to config.modules.toSortedMap(),
                "typeRegistry" to linkedMapOf(
                    "entries" to config.typeRegistry.entries.toSortedMap(),
                    "registryFile" to config.typeRegistry.registryFile,
                ),
                "sources" to linkedMapOf(
                    "db" to sanitizedDbSourceSnapshot(config.sources["db"]),
                    "valueObjectManifest" to sanitizedFileSourceSnapshot(
                        config.sources["value-object-manifest"]
                    ),
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

private fun sanitizedFileSourceSnapshot(source: SourceConfig?): Map<String, Any?>? {
    if (source == null) {
        return null
    }
    return linkedMapOf(
        "files" to source.options["files"].asStringList().sorted(),
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
    config.sources["value-object-manifest"]
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
    val dbUrl = dbSource.options["url"]?.toString().orEmpty()
    return dbRunScriptInputFiles(project, dbUrl).isEmpty()
}

internal fun dbRunScriptInputFiles(project: Project, dbUrl: String): List<File> {
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
    val extensionRuntime = loadPipelineExtensionRuntime(project, config)
    val runner = DefaultPipelineRunner(
        sources = builtInAuthoringSourceProviders(),
        generators = builtInAuthoringGeneratorProviders(),
        assembler = DefaultCanonicalAssembler(),
        renderer = PebbleArtifactRenderer(
            PresetTemplateResolver(
                preset = config.templates.preset,
                overrideDirs = config.templates.overrideDirs,
                addonTemplateClassLoaders = extensionRuntime.addonTemplateClassLoaders,
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
        artifactAddons = extensionRuntime.artifactAddons,
        managedFieldPolicies = extensionRuntime.managedFieldPolicies,
    )
    return ValueObjectManifestSourceConfigPipelineRunner(project, runner.closeAfterRun(extensionRuntime))
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
    val extensionRuntime = loadPipelineExtensionRuntime(project, config)
    val runner = DefaultPipelineRunner(
        sources = builtInAnalysisSourceProviders(),
        generators = builtInAnalysisGeneratorProviders(),
        assembler = DefaultCanonicalAssembler(),
        renderer = PebbleArtifactRenderer(
            PresetTemplateResolver(
                preset = config.templates.preset,
                overrideDirs = config.templates.overrideDirs,
                addonTemplateClassLoaders = extensionRuntime.addonTemplateClassLoaders,
            )
        ),
        exporter = if (exportEnabled) {
            FilesystemArtifactExporter(project.projectDir.toPath())
        } else {
            NoopArtifactExporter()
        },
        artifactAddons = extensionRuntime.artifactAddons,
    )
    return runner.closeAfterRun(extensionRuntime)
}

internal fun builtInAuthoringSourceProviders(): List<SourceProvider> = listOf(
    DbSchemaSourceProvider(),
    EnumManifestSourceProvider(),
    ValueObjectManifestSourceProvider(),
    DesignJsonSourceProvider(),
)

internal fun builtInAnalysisSourceProviders(): List<SourceProvider> = listOf(
    IrAnalysisSourceProvider(),
)

internal fun builtInAuthoringGeneratorProviders(): List<GeneratorProvider> = listOf(
    DesignCommandArtifactPlanner(),
    DesignQueryArtifactPlanner(),
    DesignQueryHandlerArtifactPlanner(),
    DesignCapabilityArtifactPlanner(),
    DesignCapabilityHandlerArtifactPlanner(),
    DesignApiPayloadArtifactPlanner(),
    DesignEndpointArtifactPlanner(),
    DesignDomainEventArtifactPlanner(),
    DesignDomainEventHandlerArtifactPlanner(),
    DesignDomainServiceArtifactPlanner(),
    DesignIntegrationEventArtifactPlanner(),
    DesignIntegrationEventSubscriberArtifactPlanner(),
    ValueObjectArtifactPlanner(),
    EnumManifestArtifactPlanner(),
    AggregateArtifactPlanner(),
    AggregateProjectionArtifactPlanner(),
)

internal fun builtInAnalysisGeneratorProviders(): List<GeneratorProvider> = listOf(
    DrawingBoardArtifactPlanner(),
    FlowArtifactPlanner(),
)

internal fun builtInCapabilityDescriptors() =
    (builtInAuthoringSourceProviders() + builtInAnalysisSourceProviders()).map(SourceProvider::descriptor) +
        (builtInAuthoringGeneratorProviders() + builtInAnalysisGeneratorProviders()).map(GeneratorProvider::descriptor)

internal data class PipelineExtensionRuntime(
    val providers: List<PipelineExtensionProvider>,
    val contributions: List<PipelineContributionBinding<PipelineContribution>>,
    val artifactAddons: List<PipelineContributionBinding<ArtifactAddonProvider>>,
    val managedFieldPolicies: List<PipelineContributionBinding<ManagedFieldPolicyProvider>>,
    val addonTemplateClassLoaders: Map<String, ClassLoader>,
    val closeables: List<AutoCloseable>,
)

internal fun loadPipelineExtensionRuntime(project: Project, config: ProjectConfig): PipelineExtensionRuntime {
    val configuration = project.configurations.findByName(CAP4K_PIPELINE_EXTENSION_CONFIGURATION_NAME)
        ?: return emptyPipelineExtensionRuntime()
    return loadPipelineExtensionRuntime(
        files = configuration.files,
        parent = PipelineExtensionLoader::class.java.classLoader,
        config = config,
    )
}

internal fun loadPipelineExtensionRuntime(
    files: Collection<File>,
    parent: ClassLoader,
    config: ProjectConfig = ProjectConfig(),
    classLoaderFactory: (Collection<File>, ClassLoader) -> URLClassLoader = PipelineExtensionLoader::classLoader,
    extensionLoader: (ClassLoader) -> LoadedPipelineExtensions = PipelineExtensionLoader::load,
    templateClassLoaderFactory: (ArtifactAddonProvider) -> URLClassLoader = PipelineExtensionLoader::templateClassLoader,
): PipelineExtensionRuntime {
    if (files.isEmpty()) {
        require(config.pipelineExtensions.isEmpty()) {
            "Configured Pipeline Extensions are not installed: ${config.pipelineExtensions.keys.sorted().joinToString(", ")}"
        }
        return emptyPipelineExtensionRuntime()
    }
    val classLoader = classLoaderFactory(files, parent)
    val loaded = try {
        extensionLoader(classLoader).also { extensions ->
            validatePipelineExtensionConfiguration(config, extensions)
        }
    } catch (failure: Throwable) {
        closeAfterLoadFailure(classLoader, failure)
        throw failure
    }
    val closeables = mutableListOf<AutoCloseable>(classLoader)
    val templateClassLoaders = linkedMapOf<String, ClassLoader>()
    try {
        loaded.artifactAddons.forEach { binding ->
            val provider = binding.contribution
            val templateClassLoader = templateClassLoaderFactory(provider)
            closeables += templateClassLoader
            templateClassLoaders[provider.id] = templateClassLoader
        }
    } catch (failure: Throwable) {
        closeAfterLoadFailure(closeables.asReversed(), failure)
        throw failure
    }
    return PipelineExtensionRuntime(
        providers = loaded.providers,
        contributions = loaded.contributions,
        artifactAddons = loaded.artifactAddons,
        managedFieldPolicies = loaded.managedFieldPolicies,
        addonTemplateClassLoaders = templateClassLoaders,
        closeables = closeables,
    )
}

private fun emptyPipelineExtensionRuntime(): PipelineExtensionRuntime =
    PipelineExtensionRuntime(
        providers = emptyList(),
        contributions = emptyList(),
        artifactAddons = emptyList(),
        managedFieldPolicies = emptyList(),
        addonTemplateClassLoaders = emptyMap(),
        closeables = emptyList(),
    )

internal fun validatePipelineExtensionConfiguration(
    config: ProjectConfig,
    loaded: LoadedPipelineExtensions,
) {
    val providersById = loaded.providers.associateBy { it.descriptor.id }
    val contributionsByExtension = loaded.contributions.groupBy { it.extensionId }
    config.pipelineExtensions.forEach { (extensionKey, extensionConfig) ->
        require(extensionKey == extensionConfig.id) {
            "Configured pipeline extension key does not match extension id: $extensionKey != ${extensionConfig.id}"
        }
        require(extensionKey in providersById) {
            "Configured pipeline extension is not loaded: $extensionKey"
        }
        val contributionIds = contributionsByExtension[extensionKey]
            .orEmpty()
            .map { binding -> PipelineExtensionLoader.contributionId(binding.contribution) }
            .toSet()
        extensionConfig.contributions.forEach { (contributionKey, contributionConfig) ->
            require(contributionKey == contributionConfig.id) {
                "Configured pipeline contribution key does not match contribution id in " +
                    "$extensionKey: $contributionKey != ${contributionConfig.id}"
            }
            require(contributionKey in contributionIds) {
                "Configured pipeline contribution is not loaded: $extensionKey/$contributionKey"
            }
        }
    }
}

internal fun PipelineExtensionRuntime.close() {
    var firstFailure: Throwable? = null
    closeables.asReversed().forEach { closeable ->
        try {
            closeable.close()
        } catch (failure: Throwable) {
            if (firstFailure == null) {
                firstFailure = failure
            } else {
                firstFailure.addSuppressed(failure)
            }
        }
    }
    firstFailure?.let { throw it }
}

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

private fun PipelineRunner.closeAfterRun(runtime: PipelineExtensionRuntime): PipelineRunner =
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

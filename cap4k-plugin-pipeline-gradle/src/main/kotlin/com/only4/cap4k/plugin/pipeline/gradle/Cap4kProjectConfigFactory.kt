package com.only4.cap4k.plugin.pipeline.gradle

import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.only4.cap4k.plugin.pipeline.api.AddonProviderConfig
import com.only4.cap4k.plugin.pipeline.api.AggregateSpecialFieldDefaultsConfig
import com.only4.cap4k.plugin.pipeline.api.ArtifactLayoutConfig
import com.only4.cap4k.plugin.pipeline.api.ArtifactLayoutResolver
import com.only4.cap4k.plugin.pipeline.api.ConflictPolicy
import com.only4.cap4k.plugin.pipeline.api.GeneratorConfig
import com.only4.cap4k.plugin.pipeline.api.OutputRootLayout
import com.only4.cap4k.plugin.pipeline.api.PackageLayout
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig
import com.only4.cap4k.plugin.pipeline.api.ProjectLayout
import com.only4.cap4k.plugin.pipeline.api.SourceConfig
import com.only4.cap4k.plugin.pipeline.api.TemplateConfig
import com.only4.cap4k.plugin.pipeline.api.TypeRegistryConfig
import com.only4.cap4k.plugin.pipeline.api.TypeRegistryConverter
import com.only4.cap4k.plugin.pipeline.api.TypeRegistryEntry
import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import java.io.File
import kotlin.io.path.invariantSeparatorsPathString
import java.util.Locale

class Cap4kProjectConfigFactory {

    fun build(project: Project, extension: Cap4kExtension): ProjectConfig {
        val basePackage = extension.project.basePackage.required("project.basePackage")

        val sourceStates = SourceStates(
            designJsonEnabled = extension.sources.designJson.enabled.get(),
            kspMetadataEnabled = extension.sources.kspMetadata.enabled.get(),
            dbEnabled = extension.sources.db.enabled.get(),
            irAnalysisEnabled = extension.sources.irAnalysis.enabled.get(),
            enumManifestEnabled = !extension.types.enumManifest.files.isEmpty,
            valueObjectManifestEnabled = !extension.types.valueObjectManifest.files.isEmpty,
        )
        val generatorStates = GeneratorStates(
            aggregateEnabled = extension.generators.aggregate.enabled.get(),
            aggregateProjectionEnabled = extension.generators.aggregateProjection.enabled.get(),
            flowEnabled = extension.generators.flow.enabled.get(),
            drawingBoardEnabled = extension.generators.drawingBoard.enabled.get(),
        )

        validateProjectRules(extension, generatorStates)
        val modules = buildModules(extension, sourceStates, generatorStates)
        val sources = buildSources(project, extension, sourceStates)
        val generators = buildGenerators(extension, sourceStates, generatorStates)
        validateGeneratorDependencies(sourceStates, generatorStates)
        val typeRegistry = buildTypeRegistry(project, extension)
        val artifactLayout = buildArtifactLayout(basePackage, extension)
        val aggregateSpecialFieldDefaults = buildAggregateSpecialFieldDefaults(extension)
        val addons = buildAddons(extension)

        return ProjectConfig(
            basePackage = basePackage,
            layout = ProjectLayout.MULTI_MODULE,
            modules = modules,
            typeRegistry = typeRegistry,
            sources = sources,
            generators = generators,
            templates = TemplateConfig(
                preset = extension.templates.preset.normalized().ifEmpty { "ddd-default" },
                overrideDirs = resolveTemplateOverrideDirs(project, extension),
                conflictPolicy = ConflictPolicy.valueOf(
                    extension.templates.conflictPolicy.normalized().ifEmpty { "SKIP" }
                ),
                templateConflictPolicies = buildTemplateConflictPolicies(extension),
            ),
            artifactLayout = artifactLayout,
            aggregateSpecialFieldDefaults = aggregateSpecialFieldDefaults,
            addons = addons,
        )
    }

    private fun validateProjectRules(extension: Cap4kExtension, generators: GeneratorStates) {
        if (generators.aggregateEnabled) {
            val missingDomain = extension.project.domainModulePath.optionalValue() == null
            val missingApplication = extension.project.applicationModulePath.optionalValue() == null
            val missingAdapter = extension.project.adapterModulePath.optionalValue() == null
            if (missingDomain || missingApplication || missingAdapter) {
                throw IllegalArgumentException(
                    "project.domainModulePath, project.applicationModulePath, and project.adapterModulePath are required when aggregate is enabled."
                )
            }
        }
        if (generators.aggregateProjectionEnabled) {
            extension.project.adapterModulePath.requiredWhenEnabled(
                "project.adapterModulePath",
                "aggregateProjection"
            )
        }
    }

    private fun buildModules(
        extension: Cap4kExtension,
        sources: SourceStates,
        generators: GeneratorStates,
    ): Map<String, String> = buildMap {
        if (sources.designJsonEnabled) {
            extension.project.domainModulePath.optionalValue()?.let { put("domain", it) }
            extension.project.applicationModulePath.optionalValue()?.let { put("application", it) }
            extension.project.adapterModulePath.optionalValue()?.let { put("adapter", it) }
        }
        if (generators.aggregateEnabled) {
            put("domain", extension.project.domainModulePath.required("project.domainModulePath"))
            put("application", extension.project.applicationModulePath.required("project.applicationModulePath"))
            put("adapter", extension.project.adapterModulePath.required("project.adapterModulePath"))
        }
        if (generators.aggregateProjectionEnabled) {
            put("adapter", extension.project.adapterModulePath.required("project.adapterModulePath"))
        }
        if (sources.valueObjectManifestEnabled) {
            put("domain", extension.project.domainModulePath.required("project.domainModulePath"))
        }
    }

    private fun buildSources(
        project: Project,
        extension: Cap4kExtension,
        states: SourceStates,
    ): Map<String, SourceConfig> = buildMap {
        if (states.designJsonEnabled) {
            val manifestFile = extension.sources.designJson.manifestFile.optionalValue()?.let { project.file(it).absolutePath }
            if (manifestFile != null) {
                put(
                    "design-json",
                    SourceConfig(
                        enabled = true,
                        options = mapOf(
                            "manifestFile" to manifestFile,
                            "projectDir" to project.projectDir.absolutePath,
                        )
                    )
                )
            } else {
                val files = extension.sources.designJson.files.files.map(File::getAbsolutePath).sorted()
                if (files.isEmpty()) {
                    throw IllegalArgumentException("sources.designJson.files must not be empty when designJson is enabled.")
                }
                put("design-json", SourceConfig(enabled = true, options = mapOf("files" to files)))
            }
        }

        if (states.kspMetadataEnabled) {
            put(
                "ksp-metadata",
                SourceConfig(
                    enabled = true,
                    options = mapOf(
                        "inputDir" to project.file(
                            extension.sources.kspMetadata.inputDir.requiredWhenEnabled(
                                "sources.kspMetadata.inputDir",
                                "kspMetadata"
                            )
                        ).absolutePath
                    ),
                )
            )
        }

        if (states.dbEnabled) {
            val url = extension.sources.db.url.requiredWhenEnabled("sources.db.url", "db")
            val username = extension.sources.db.username.requiredWhenEnabled("sources.db.username", "db")
            val password = extension.sources.db.password.requiredRawWhenEnabled("sources.db.password", "db")
            val options = linkedMapOf<String, Any?>(
                "url" to url,
                "username" to username,
                "password" to password,
            )
            extension.sources.db.schema.optionalValue()?.let { options["schema"] = it }
            extension.sources.db.includeTables.normalizedValues().takeIf { it.isNotEmpty() }?.let { options["includeTables"] = it }
            extension.sources.db.excludeTables.normalizedValues().takeIf { it.isNotEmpty() }?.let { options["excludeTables"] = it }
            put("db", SourceConfig(enabled = true, options = options))
        }

        if (states.irAnalysisEnabled) {
            val inputDirs = extension.sources.irAnalysis.inputDirs.files.map(File::getAbsolutePath).sorted()
            if (inputDirs.isEmpty()) {
                throw IllegalArgumentException("sources.irAnalysis.inputDirs must not be empty when irAnalysis is enabled.")
            }
            put("ir-analysis", SourceConfig(enabled = true, options = mapOf("inputDirs" to inputDirs)))
        }
        if (states.enumManifestEnabled) {
            val files = extension.types.enumManifest.files.files.map(File::getAbsolutePath).sorted()
            put("enum-manifest", SourceConfig(enabled = true, options = mapOf("files" to files)))
        }
        if (states.valueObjectManifestEnabled) {
            put("value-object-manifest", SourceConfig(enabled = true))
        }
    }

    private fun buildGenerators(
        extension: Cap4kExtension,
        sources: SourceStates,
        states: GeneratorStates,
    ): Map<String, GeneratorConfig> = buildMap {
        if (sources.designJsonEnabled) {
            DESIGN_PLANNER_GENERATOR_IDS.forEach { generatorId ->
                put(generatorId, GeneratorConfig(enabled = true))
            }
        }
        if (sources.valueObjectManifestEnabled) {
            put("types-value-object", GeneratorConfig(enabled = true))
        }
        if (states.aggregateEnabled) {
            val aggregate = extension.generators.aggregate
            put(
                "aggregate",
                GeneratorConfig(
                    enabled = true,
                    options = mapOf(
                        "unsupportedTablePolicy" to aggregate.unsupportedTablePolicy
                            .normalized()
                            .uppercase(Locale.ROOT)
                            .ifEmpty { "FAIL" },
                        "artifact.factory" to aggregate.artifacts.factory.get(),
                        "artifact.specification" to aggregate.artifacts.specification.get(),
                        "artifact.unique" to aggregate.artifacts.unique.get(),
                    ),
                )
            )
        }
        if (states.aggregateProjectionEnabled) {
            put("aggregate-projection", GeneratorConfig(enabled = true))
        }
        if (states.flowEnabled) {
            put("flow", GeneratorConfig(enabled = true))
        }
        if (states.drawingBoardEnabled) {
            put("drawing-board", GeneratorConfig(enabled = true))
        }
    }

    private fun buildArtifactLayout(basePackage: String, extension: Cap4kExtension): ArtifactLayoutConfig {
        val artifactLayout = ArtifactLayoutConfig(
            aggregate = extension.layout.aggregate.toPackageLayout("aggregate"),
            aggregateSchema = extension.layout.aggregateSchema.toPackageLayout("aggregateSchema"),
            aggregateRepository = extension.layout.aggregateRepository.toPackageLayout("aggregateRepository"),
            aggregateSharedEnum = extension.layout.aggregateSharedEnum.toPackageLayout("aggregateSharedEnum"),
            aggregateUniqueQuery = extension.layout.aggregateUniqueQuery.toPackageLayout("aggregateUniqueQuery"),
            aggregateUniqueQueryHandler = extension.layout.aggregateUniqueQueryHandler.toPackageLayout(
                "aggregateUniqueQueryHandler"
            ),
            aggregateUniqueValidator = extension.layout.aggregateUniqueValidator.toPackageLayout(
                "aggregateUniqueValidator"
            ),
            flow = extension.layout.flow.toOutputRootLayout("flow"),
            drawingBoard = extension.layout.drawingBoard.toOutputRootLayout("drawing-board"),
            designCommand = extension.layout.designCommand.toPackageLayout("designCommand"),
            designQuery = extension.layout.designQuery.toPackageLayout("designQuery"),
            designClient = extension.layout.designClient.toPackageLayout("designClient"),
            designQueryHandler = extension.layout.designQueryHandler.toPackageLayout("designQueryHandler"),
            designClientHandler = extension.layout.designClientHandler.toPackageLayout("designClientHandler"),
            designApiPayload = extension.layout.designApiPayload.toPackageLayout("designApiPayload"),
            designDomainEvent = extension.layout.designDomainEvent.toPackageLayout("designDomainEvent"),
            designDomainEventHandler = extension.layout.designDomainEventHandler.toPackageLayout(
                "designDomainEventHandler"
            ),
            designIntegrationEvent = extension.layout.designIntegrationEvent.toPackageLayout("designIntegrationEvent"),
            designIntegrationEventSubscriber = extension.layout.designIntegrationEventSubscriber.toPackageLayout(
                "designIntegrationEventSubscriber"
            ),
        )
        ArtifactLayoutResolver(basePackage = basePackage, artifactLayout = artifactLayout)
        return artifactLayout
    }

    private fun buildAggregateSpecialFieldDefaults(extension: Cap4kExtension): AggregateSpecialFieldDefaultsConfig {
        val specialFields = extension.generators.aggregate.specialFields
        return AggregateSpecialFieldDefaultsConfig(
            idDefaultStrategy = specialFields.idDefaultStrategy.normalized().ifEmpty { "uuid7" },
            deletedDefaultColumn = specialFields.deletedDefaultColumn.normalized(),
            versionDefaultColumn = specialFields.versionDefaultColumn.normalized(),
            managedDefaultColumns = specialFields.managedDefaultColumns.normalizedValues(),
        )
    }

    private fun buildTypeRegistry(project: Project, extension: Cap4kExtension): TypeRegistryConfig {
        val registryFile = extension.types.registryFile.optionalValue().orEmpty()
        val entries = if (registryFile.isEmpty()) {
            emptyMap()
        } else {
            loadTypeRegistryEntries(project, registryFile)
        }
        return TypeRegistryConfig(
            entries = entries,
            registryFile = registryFile,
            enumManifestFiles = extension.types.enumManifest.files.projectRelativePaths(project),
            valueObjectManifestFiles = extension.types.valueObjectManifest.files.projectRelativePaths(project),
        )
    }

    private fun loadTypeRegistryEntries(project: Project, registryFile: String): Map<String, TypeRegistryEntry> {
        val file = project.file(registryFile).absoluteFile
        require(file.exists()) {
            "types.registryFile does not exist: ${file.path}"
        }
        val registry = linkedMapOf<String, TypeRegistryEntry>()
        file.reader(Charsets.UTF_8).use { reader ->
            val jsonReader = JsonReader(reader)
            require(jsonReader.peek() == JsonToken.BEGIN_OBJECT) {
                "types.registryFile must contain a JSON object."
            }
            jsonReader.beginObject()

            val rawKeys = linkedSetOf<String>()
            while (jsonReader.hasNext()) {
                val rawKey = jsonReader.nextName()
                require(rawKeys.add(rawKey)) {
                    "types.registryFile contains duplicate type name: $rawKey"
                }

                val normalizedKey = rawKey.trim()
                require(normalizedKey.isNotEmpty()) {
                    "types.registryFile contains a blank type name."
                }
                require(!normalizedKey.contains('.')) {
                    "types.registryFile type name must be a simple name: $normalizedKey"
                }
                require(normalizedKey !in reservedTypeNames) {
                    "types.registryFile cannot override built-in type: $normalizedKey"
                }
                require(normalizedKey !in registry) {
                    "types.registryFile contains duplicate type name after normalization: $normalizedKey"
                }

                require(jsonReader.peek() == JsonToken.BEGIN_OBJECT) {
                    "types.registryFile value for $normalizedKey must be an object."
                }
                registry[normalizedKey] = jsonReader.nextTypeRegistryEntry(normalizedKey)
            }

            jsonReader.endObject()
        }

        return registry
    }

    private fun buildAddons(extension: Cap4kExtension): Map<String, AddonProviderConfig> =
        extension.addons.providers.associate { provider ->
            provider.id to AddonProviderConfig(
                id = provider.id,
                options = provider.options.getOrElse(emptyMap()),
            )
        }

    private fun validateGeneratorDependencies(sources: SourceStates, generators: GeneratorStates) {
        if (generators.aggregateEnabled && !sources.dbEnabled) {
            throw IllegalArgumentException("aggregate generator requires enabled db source.")
        }
        if (generators.aggregateProjectionEnabled && !sources.dbEnabled) {
            throw IllegalArgumentException("aggregateProjection generator requires enabled db source.")
        }
        if (generators.flowEnabled && !sources.irAnalysisEnabled) {
            throw IllegalArgumentException("flow generator requires enabled irAnalysis source.")
        }
        if (generators.drawingBoardEnabled && !sources.irAnalysisEnabled) {
            throw IllegalArgumentException("drawingBoard generator requires enabled irAnalysis source.")
        }
    }

    private fun resolveTemplateOverrideDirs(project: Project, extension: Cap4kExtension): List<String> {
        val overrideDirs = extension.templates.overrideDirs.files.map(File::getAbsolutePath)
        val bridgedDir = extension.templates.templateOverrideDir.optionalValue()?.let { project.file(it).absolutePath }
        return LinkedHashSet(overrideDirs + listOfNotNull(bridgedDir)).toList()
    }

    private fun buildTemplateConflictPolicies(extension: Cap4kExtension): Map<String, ConflictPolicy> =
        buildMap {
            extension.templates.templateConflictPolicies.get().forEach { (templateId, rawConflictPolicy) ->
                val normalizedTemplateId = templateId.trim()
                require(normalizedTemplateId.isNotEmpty()) {
                    "templates.templateConflictPolicies contains a blank template id."
                }
                require(normalizedTemplateId !in this) {
                    "templates.templateConflictPolicies contains duplicate template id after normalization: $normalizedTemplateId"
                }
                put(
                    normalizedTemplateId,
                    ConflictPolicy.valueOf(rawConflictPolicy.trim().uppercase(Locale.ROOT))
                )
            }
        }
}

private data class SourceStates(
    val designJsonEnabled: Boolean,
    val kspMetadataEnabled: Boolean,
    val dbEnabled: Boolean,
    val irAnalysisEnabled: Boolean,
    val enumManifestEnabled: Boolean,
    val valueObjectManifestEnabled: Boolean,
)

private data class GeneratorStates(
    val aggregateEnabled: Boolean,
    val aggregateProjectionEnabled: Boolean,
    val flowEnabled: Boolean,
    val drawingBoardEnabled: Boolean,
)

private val DESIGN_PLANNER_GENERATOR_IDS = listOf(
    "design-command",
    "design-query",
    "design-query-handler",
    "design-client",
    "design-client-handler",
    "design-api-payload",
    "design-domain-event",
    "design-domain-event-handler",
    "design-domain-service",
    "design-saga",
    "design-integration-event",
    "design-integration-event-subscriber",
)

private fun Property<String>.required(path: String): String =
    optionalValue() ?: throw IllegalArgumentException("$path is required.")

private fun Property<String>.requiredWhenEnabled(path: String, blockName: String): String =
    optionalValue() ?: throw IllegalArgumentException("$path is required when $blockName is enabled.")

private fun Property<String>.requiredRawWhenEnabled(path: String, blockName: String): String =
    orNull ?: throw IllegalArgumentException("$path is required when $blockName is enabled.")

private fun Property<String>.optionalValue(): String? =
    orNull?.trim()?.takeIf { it.isNotEmpty() }

private fun Property<String>.normalized(): String =
    orNull?.trim().orEmpty()

private fun PackageLayoutExtension.toPackageLayout(familyName: String): PackageLayout =
    PackageLayout(
        packageRoot = packageRoot.validPackageFragment("layout.$familyName.packageRoot"),
        packageSuffix = packageSuffix.validPackageFragment("layout.$familyName.packageSuffix"),
        defaultPackage = defaultPackage.validPackageFragment("layout.$familyName.defaultPackage"),
    )

private fun OutputRootLayoutExtension.toOutputRootLayout(familyName: String): OutputRootLayout =
    OutputRootLayout(
        outputRoot = ArtifactLayoutResolver.normalizeOutputRoot(outputRoot.rawValue(), familyName)
    )

private fun Property<String>.validPackageFragment(label: String): String {
    val value = rawValue()
    require(value == value.trim()) {
        "$label must be a valid relative Kotlin package fragment: $value"
    }
    return ArtifactLayoutResolver.validatePackageFragment(value, label, allowBlank = true).trim()
}

private fun Property<String>.rawValue(): String =
    orNull.orEmpty()

private fun ListProperty<String>.normalizedValues(): List<String> =
    orNull.orEmpty().mapNotNull { value -> value.trim().takeIf { it.isNotEmpty() } }

private fun ConfigurableFileCollection.projectRelativePaths(project: Project): List<String> {
    val projectRoot = project.projectDir.toPath().toAbsolutePath().normalize()
    return files
        .map { file -> project.file(file).toPath().toAbsolutePath().normalize() }
        .map { path ->
            if (path.startsWith(projectRoot)) {
                projectRoot.relativize(path).invariantSeparatorsPathString
            } else {
                path.invariantSeparatorsPathString
            }
        }
        .sorted()
}

private fun JsonReader.nextTypeRegistryEntry(key: String): TypeRegistryEntry {
    beginObject()
    val rawFields = linkedSetOf<String>()
    var fqn: String? = null
    var converter = TypeRegistryConverter.nested()

    while (hasNext()) {
        val rawField = nextName()
        require(rawFields.add(rawField)) {
            "types.registryFile value for $key contains duplicate field: $rawField"
        }
        when (val field = rawField.trim()) {
            "fqn" -> {
                require(peek() == JsonToken.STRING) {
                    "types.registryFile value for $key.fqn must be a fully qualified name."
                }
                fqn = nextString().asRegistryValue("$key.fqn")
            }
            "converter" -> {
                converter = nextTypeRegistryConverter("$key.converter")
            }
            else -> throw IllegalArgumentException(
                "types.registryFile value for $key contains unsupported field: $field"
            )
        }
    }

    endObject()
    return TypeRegistryEntry(
        fqn = requireNotNull(fqn) {
            "types.registryFile value for $key.fqn is required."
        },
        converter = converter,
    )
}

private fun JsonReader.nextTypeRegistryConverter(path: String): TypeRegistryConverter =
    when (peek()) {
        JsonToken.BOOLEAN -> {
            val enabled = nextBoolean()
            require(!enabled) {
                "types.registryFile value for $path must be false, \"nested\", or a converter FQN."
            }
            TypeRegistryConverter.none()
        }
        JsonToken.STRING -> {
            val value = nextString()
            when (value) {
                "nested" -> TypeRegistryConverter.nested()
                else -> TypeRegistryConverter.explicit(value.asRegistryValue(path))
            }
        }
        else -> throw IllegalArgumentException(
            "types.registryFile value for $path must be false, \"nested\", or a converter FQN."
        )
    }

private fun String.asRegistryValue(key: String): String {
    require(isNotBlank()) {
        "types.registryFile value for $key must not be blank."
    }
    require(this == trim()) {
        "types.registryFile value for $key must be a fully qualified name."
    }
    val segments = split('.')
    require(segments.size >= 2 && segments.all { it.isValidQualifiedNameSegment() }) {
        "types.registryFile value for $key must be a fully qualified name."
    }
    return this
}

private fun Char.isJavaIdentifierStartChar(): Boolean = Character.isJavaIdentifierStart(this)

private fun Char.isJavaIdentifierPartChar(): Boolean = Character.isJavaIdentifierPart(this)

private fun String.isValidQualifiedNameSegment(): Boolean {
    if (isEmpty()) {
        return false
    }
    if (!first().isJavaIdentifierStartChar()) {
        return false
    }
    return drop(1).all { it.isJavaIdentifierPartChar() }
}

private val reservedTypeNames = setOf(
    "Any",
    "Array",
    "Boolean",
    "Byte",
    "Char",
    "Collection",
    "Double",
    "Float",
    "Int",
    "Iterable",
    "List",
    "Long",
    "Map",
    "MutableCollection",
    "MutableIterable",
    "MutableList",
    "MutableMap",
    "MutableSet",
    "Nothing",
    "Number",
    "Pair",
    "Sequence",
    "Set",
    "Short",
    "String",
    "Triple",
    "Unit",
)

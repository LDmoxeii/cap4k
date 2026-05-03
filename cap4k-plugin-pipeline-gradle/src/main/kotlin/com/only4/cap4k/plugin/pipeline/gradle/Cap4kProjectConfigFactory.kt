package com.only4.cap4k.plugin.pipeline.gradle

import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
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
import com.only4.cap4k.plugin.pipeline.api.TypeRegistryConverter
import com.only4.cap4k.plugin.pipeline.api.TypeRegistryEntry
import org.gradle.api.Project
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import java.io.File
import java.util.Locale

class Cap4kProjectConfigFactory {

    fun build(project: Project, extension: Cap4kExtension): ProjectConfig {
        val basePackage = extension.project.basePackage.required("project.basePackage")

        val sourceStates = SourceStates(
            designJsonEnabled = extension.sources.designJson.enabled.get(),
            kspMetadataEnabled = extension.sources.kspMetadata.enabled.get(),
            dbEnabled = extension.sources.db.enabled.get(),
            enumManifestEnabled = extension.sources.enumManifest.enabled.get(),
            irAnalysisEnabled = extension.sources.irAnalysis.enabled.get(),
        )
        val generatorStates = GeneratorStates(
            designCommandEnabled = extension.generators.designCommand.enabled.get(),
            designQueryEnabled = extension.generators.designQuery.enabled.get(),
            designQueryHandlerEnabled = extension.generators.designQueryHandler.enabled.get(),
            designClientEnabled = extension.generators.designClient.enabled.get(),
            designClientHandlerEnabled = extension.generators.designClientHandler.enabled.get(),
            designValidatorEnabled = extension.generators.designValidator.enabled.get(),
            designApiPayloadEnabled = extension.generators.designApiPayload.enabled.get(),
            designDomainEventEnabled = extension.generators.designDomainEvent.enabled.get(),
            designDomainEventHandlerEnabled = extension.generators.designDomainEventHandler.enabled.get(),
            aggregateEnabled = extension.generators.aggregate.enabled.get(),
            flowEnabled = extension.generators.flow.enabled.get(),
            drawingBoardEnabled = extension.generators.drawingBoard.enabled.get(),
        )

        validateProjectRules(extension, generatorStates)
        val modules = buildModules(extension, generatorStates)
        val sources = buildSources(project, extension, sourceStates)
        val generators = buildGenerators(extension, generatorStates)
        validateGeneratorDependencies(sourceStates, generatorStates)
        val typeRegistry = buildTypeRegistry(project, extension)
        val artifactLayout = buildArtifactLayout(basePackage, extension)
        val aggregateSpecialFieldDefaults = buildAggregateSpecialFieldDefaults(extension)

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
            ),
            artifactLayout = artifactLayout,
            aggregateSpecialFieldDefaults = aggregateSpecialFieldDefaults,
        )
    }

    private fun validateProjectRules(extension: Cap4kExtension, generators: GeneratorStates) {
        if (generators.designCommandEnabled) {
            extension.project.applicationModulePath.requiredWhenEnabled(
                "project.applicationModulePath",
                "designCommand"
            )
        }
        if (generators.designQueryEnabled) {
            extension.project.applicationModulePath.requiredWhenEnabled(
                "project.applicationModulePath",
                "designQuery"
            )
        }
        if (generators.designQueryHandlerEnabled) {
            extension.project.adapterModulePath.requiredWhenEnabled(
                "project.adapterModulePath",
                "designQueryHandler"
            )
        }
        if (generators.designClientEnabled) {
            extension.project.applicationModulePath.requiredWhenEnabled(
                "project.applicationModulePath",
                "designClient"
            )
        }
        if (generators.designClientHandlerEnabled) {
            extension.project.adapterModulePath.requiredWhenEnabled(
                "project.adapterModulePath",
                "designClientHandler"
            )
        }
        if (generators.designValidatorEnabled) {
            extension.project.applicationModulePath.requiredWhenEnabled(
                "project.applicationModulePath",
                "designValidator"
            )
        }
        if (generators.designApiPayloadEnabled) {
            extension.project.adapterModulePath.requiredWhenEnabled(
                "project.adapterModulePath",
                "designApiPayload"
            )
        }
        if (generators.designDomainEventEnabled) {
            extension.project.domainModulePath.requiredWhenEnabled(
                "project.domainModulePath",
                "designDomainEvent"
            )
        }
        if (generators.designDomainEventHandlerEnabled) {
            extension.project.applicationModulePath.requiredWhenEnabled(
                "project.applicationModulePath",
                "designDomainEventHandler"
            )
        }
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
    }

    private fun buildModules(
        extension: Cap4kExtension,
        generators: GeneratorStates,
    ): Map<String, String> = buildMap {
        if (generators.designCommandEnabled) {
            put("application", extension.project.applicationModulePath.required("project.applicationModulePath"))
        }
        if (generators.designQueryEnabled) {
            put("application", extension.project.applicationModulePath.required("project.applicationModulePath"))
        }
        if (generators.designQueryHandlerEnabled) {
            put("adapter", extension.project.adapterModulePath.required("project.adapterModulePath"))
        }
        if (generators.designClientEnabled) {
            put("application", extension.project.applicationModulePath.required("project.applicationModulePath"))
        }
        if (generators.designClientHandlerEnabled) {
            put("adapter", extension.project.adapterModulePath.required("project.adapterModulePath"))
        }
        if (generators.designValidatorEnabled) {
            put("application", extension.project.applicationModulePath.required("project.applicationModulePath"))
        }
        if (generators.designApiPayloadEnabled) {
            put("adapter", extension.project.adapterModulePath.required("project.adapterModulePath"))
        }
        if (generators.designDomainEventEnabled) {
            put("domain", extension.project.domainModulePath.required("project.domainModulePath"))
        }
        if (generators.designDomainEventHandlerEnabled) {
            put("application", extension.project.applicationModulePath.required("project.applicationModulePath"))
        }
        if (generators.aggregateEnabled) {
            put("domain", extension.project.domainModulePath.required("project.domainModulePath"))
            put("application", extension.project.applicationModulePath.required("project.applicationModulePath"))
            put("adapter", extension.project.adapterModulePath.required("project.adapterModulePath"))
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

        if (states.enumManifestEnabled) {
            val files = extension.sources.enumManifest.files.files.map(File::getAbsolutePath).sorted()
            if (files.isEmpty()) {
                throw IllegalArgumentException("sources.enumManifest.files must not be empty when enumManifest is enabled.")
            }
            put("enum-manifest", SourceConfig(enabled = true, options = mapOf("files" to files)))
        }

        if (states.irAnalysisEnabled) {
            val inputDirs = extension.sources.irAnalysis.inputDirs.files.map(File::getAbsolutePath).sorted()
            if (inputDirs.isEmpty()) {
                throw IllegalArgumentException("sources.irAnalysis.inputDirs must not be empty when irAnalysis is enabled.")
            }
            put("ir-analysis", SourceConfig(enabled = true, options = mapOf("inputDirs" to inputDirs)))
        }
    }

    private fun buildGenerators(
        extension: Cap4kExtension,
        states: GeneratorStates,
    ): Map<String, GeneratorConfig> = buildMap {
        if (states.designCommandEnabled) {
            put("design-command", GeneratorConfig(enabled = true))
        }
        if (states.designQueryEnabled) {
            put("design-query", GeneratorConfig(enabled = true))
        }
        if (states.designQueryHandlerEnabled) {
            put("design-query-handler", GeneratorConfig(enabled = true))
        }
        if (states.designClientEnabled) {
            put("design-client", GeneratorConfig(enabled = true))
        }
        if (states.designClientHandlerEnabled) {
            put("design-client-handler", GeneratorConfig(enabled = true))
        }
        if (states.designValidatorEnabled) {
            put("design-validator", GeneratorConfig(enabled = true))
        }
        if (states.designApiPayloadEnabled) {
            put("design-api-payload", GeneratorConfig(enabled = true))
        }
        if (states.designDomainEventEnabled) {
            put("design-domain-event", GeneratorConfig(enabled = true))
        }
        if (states.designDomainEventHandlerEnabled) {
            put("design-domain-event-handler", GeneratorConfig(enabled = true))
        }
        if (states.aggregateEnabled) {
            val aggregate = extension.generators.aggregate
            if (aggregate.artifacts.wrapper.get() && !aggregate.artifacts.factory.get()) {
                throw IllegalArgumentException("aggregate wrapper artifact requires enabled aggregate factory artifact.")
            }
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
                        "artifact.wrapper" to aggregate.artifacts.wrapper.get(),
                        "artifact.unique" to aggregate.artifacts.unique.get(),
                        "artifact.enumTranslation" to aggregate.artifacts.enumTranslation.get(),
                    ),
                )
            )
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
            aggregateEnumTranslation = extension.layout.aggregateEnumTranslation.toPackageLayout(
                "aggregateEnumTranslation"
            ),
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
            designValidator = extension.layout.designValidator.toPackageLayout("designValidator"),
            designApiPayload = extension.layout.designApiPayload.toPackageLayout("designApiPayload"),
            designDomainEvent = extension.layout.designDomainEvent.toPackageLayout("designDomainEvent"),
            designDomainEventHandler = extension.layout.designDomainEventHandler.toPackageLayout(
                "designDomainEventHandler"
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
        )
    }

    private fun buildTypeRegistry(project: Project, extension: Cap4kExtension): Map<String, TypeRegistryEntry> {
        val registryFile = extension.types.registryFile.optionalValue() ?: return emptyMap()
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

    private fun validateGeneratorDependencies(sources: SourceStates, generators: GeneratorStates) {
        if (generators.designQueryHandlerEnabled && !generators.designQueryEnabled) {
            throw IllegalArgumentException("designQueryHandler generator requires enabled designQuery generator.")
        }
        if (generators.designClientEnabled && !sources.designJsonEnabled) {
            throw IllegalArgumentException("designClient generator requires enabled designJson source.")
        }
        if (generators.designClientHandlerEnabled && !generators.designClientEnabled) {
            throw IllegalArgumentException("designClientHandler generator requires enabled designClient generator.")
        }
        if (generators.designCommandEnabled && !sources.designJsonEnabled) {
            throw IllegalArgumentException("designCommand generator requires enabled designJson source.")
        }
        if (generators.designQueryEnabled && !sources.designJsonEnabled) {
            throw IllegalArgumentException("designQuery generator requires enabled designJson source.")
        }
        if (generators.designValidatorEnabled && !sources.designJsonEnabled) {
            throw IllegalArgumentException("designValidator generator requires enabled designJson source.")
        }
        if (generators.designApiPayloadEnabled && !sources.designJsonEnabled) {
            throw IllegalArgumentException("designApiPayload generator requires enabled designJson source.")
        }
        if (generators.designDomainEventEnabled && !sources.designJsonEnabled) {
            throw IllegalArgumentException("designDomainEvent generator requires enabled designJson source.")
        }
        if (generators.designDomainEventHandlerEnabled && !generators.designDomainEventEnabled) {
            throw IllegalArgumentException("designDomainEventHandler generator requires enabled designDomainEvent generator.")
        }
        if (generators.aggregateEnabled && !sources.dbEnabled) {
            throw IllegalArgumentException("aggregate generator requires enabled db source.")
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
}

private data class SourceStates(
    val designJsonEnabled: Boolean,
    val kspMetadataEnabled: Boolean,
    val dbEnabled: Boolean,
    val enumManifestEnabled: Boolean,
    val irAnalysisEnabled: Boolean,
)

private data class GeneratorStates(
    val designCommandEnabled: Boolean,
    val designQueryEnabled: Boolean,
    val designQueryHandlerEnabled: Boolean,
    val designClientEnabled: Boolean,
    val designClientHandlerEnabled: Boolean,
    val designValidatorEnabled: Boolean,
    val designApiPayloadEnabled: Boolean,
    val designDomainEventEnabled: Boolean,
    val designDomainEventHandlerEnabled: Boolean,
    val aggregateEnabled: Boolean,
    val flowEnabled: Boolean,
    val drawingBoardEnabled: Boolean,
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

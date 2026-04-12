package com.only4.cap4k.plugin.pipeline.gradle

import com.google.gson.JsonElement
import com.google.gson.JsonParser
import com.only4.cap4k.plugin.pipeline.api.ConflictPolicy
import com.only4.cap4k.plugin.pipeline.api.GeneratorConfig
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig
import com.only4.cap4k.plugin.pipeline.api.ProjectLayout
import com.only4.cap4k.plugin.pipeline.api.SourceConfig
import com.only4.cap4k.plugin.pipeline.api.TemplateConfig
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
            irAnalysisEnabled = extension.sources.irAnalysis.enabled.get(),
        )
        val generatorStates = GeneratorStates(
            designEnabled = extension.generators.design.enabled.get(),
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
        )
    }

    private fun validateProjectRules(extension: Cap4kExtension, generators: GeneratorStates) {
        if (generators.designEnabled) {
            extension.project.applicationModulePath.requiredWhenEnabled(
                "project.applicationModulePath",
                "design"
            )
        }
        if (generators.aggregateEnabled) {
            val missingDomain = extension.project.domainModulePath.optionalValue() == null
            val missingAdapter = extension.project.adapterModulePath.optionalValue() == null
            if (missingDomain || missingAdapter) {
                throw IllegalArgumentException(
                    "project.domainModulePath and project.adapterModulePath are required when aggregate is enabled."
                )
            }
        }
    }

    private fun buildModules(
        extension: Cap4kExtension,
        generators: GeneratorStates,
    ): Map<String, String> = buildMap {
        if (generators.designEnabled) {
            put("application", extension.project.applicationModulePath.required("project.applicationModulePath"))
        }
        if (generators.aggregateEnabled) {
            put("domain", extension.project.domainModulePath.required("project.domainModulePath"))
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
            val password = extension.sources.db.password.requiredWhenEnabled("sources.db.password", "db")
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
    }

    private fun buildGenerators(
        extension: Cap4kExtension,
        states: GeneratorStates,
    ): Map<String, GeneratorConfig> = buildMap {
        if (states.designEnabled) {
            put("design", GeneratorConfig(enabled = true))
        }
        if (states.aggregateEnabled) {
            put(
                "aggregate",
                GeneratorConfig(
                    enabled = true,
                    options = mapOf(
                        "unsupportedTablePolicy" to extension.generators.aggregate.unsupportedTablePolicy
                            .normalized()
                            .uppercase(Locale.ROOT)
                            .ifEmpty { "FAIL" }
                    ),
                )
            )
        }
        if (states.flowEnabled) {
            put(
                "flow",
                GeneratorConfig(
                    enabled = true,
                    options = mapOf(
                        "outputDir" to extension.generators.flow.outputDir.requiredWhenEnabled(
                            "generators.flow.outputDir",
                            "flow"
                        )
                    ),
                )
            )
        }
        if (states.drawingBoardEnabled) {
            put(
                "drawing-board",
                GeneratorConfig(
                    enabled = true,
                    options = mapOf(
                        "outputDir" to extension.generators.drawingBoard.outputDir.requiredWhenEnabled(
                            "generators.drawingBoard.outputDir",
                            "drawingBoard"
                        )
                    ),
                )
            )
        }
    }

    private fun buildTypeRegistry(project: Project, extension: Cap4kExtension): Map<String, String> {
        val registryFile = extension.types.registryFile.optionalValue() ?: return emptyMap()
        val file = project.file(registryFile).absoluteFile
        require(file.exists()) {
            "types.registryFile does not exist: ${file.path}"
        }

        val root = file.reader(Charsets.UTF_8).use { reader -> JsonParser.parseReader(reader) }
        require(root.isJsonObject) {
            "types.registryFile must contain a JSON object."
        }

        val registry = linkedMapOf<String, String>()
        for ((key, value) in root.asJsonObject.entrySet()) {
            val normalizedKey = key.trim()
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

            val normalizedValue = value.asRegistryValue(normalizedKey)
            registry[normalizedKey] = normalizedValue
        }

        return registry
    }

    private fun validateGeneratorDependencies(sources: SourceStates, generators: GeneratorStates) {
        if (generators.designEnabled && !sources.designJsonEnabled) {
            throw IllegalArgumentException("design generator requires enabled designJson source.")
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
    val irAnalysisEnabled: Boolean,
)

private data class GeneratorStates(
    val designEnabled: Boolean,
    val aggregateEnabled: Boolean,
    val flowEnabled: Boolean,
    val drawingBoardEnabled: Boolean,
)

private fun Property<String>.required(path: String): String =
    optionalValue() ?: throw IllegalArgumentException("$path is required.")

private fun Property<String>.requiredWhenEnabled(path: String, blockName: String): String =
    optionalValue() ?: throw IllegalArgumentException("$path is required when $blockName is enabled.")

private fun Property<String>.optionalValue(): String? =
    orNull?.trim()?.takeIf { it.isNotEmpty() }

private fun Property<String>.normalized(): String =
    orNull?.trim().orEmpty()

private fun ListProperty<String>.normalizedValues(): List<String> =
    orNull.orEmpty().mapNotNull { value -> value.trim().takeIf { it.isNotEmpty() } }

private fun JsonElement.asRegistryValue(key: String): String {
    require(isJsonPrimitive && asJsonPrimitive.isString) {
        "types.registryFile value for $key must be a string FQN."
    }
    val normalizedValue = asString.trim()
    require(normalizedValue.isNotEmpty()) {
        "types.registryFile value for $key must not be blank."
    }
    val segments = normalizedValue.split('.')
    require(segments.size >= 2 && segments.all { it.isNotBlank() }) {
        "types.registryFile value for $key must be a fully qualified name."
    }
    return normalizedValue
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

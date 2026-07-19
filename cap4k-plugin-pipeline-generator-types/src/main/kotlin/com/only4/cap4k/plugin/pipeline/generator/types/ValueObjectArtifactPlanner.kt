package com.only4.cap4k.plugin.pipeline.generator.types

import com.only4.cap4k.plugin.pipeline.api.ArtifactLayoutResolver
import com.only4.cap4k.plugin.pipeline.api.ArtifactOutputKind
import com.only4.cap4k.plugin.pipeline.api.ArtifactPlanItem
import com.only4.cap4k.plugin.pipeline.api.CanonicalModel
import com.only4.cap4k.plugin.pipeline.api.GeneratorProvider
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig
import com.only4.cap4k.plugin.pipeline.api.ValueObjectModel
import com.only4.cap4k.plugin.pipeline.api.ValueObjectStorage
import com.only4.cap4k.plugin.pipeline.generator.common.types.CanonicalTypeSymbolRegistryFactory
import com.only4.cap4k.plugin.pipeline.generator.common.types.TypeSymbolRegistry
import java.nio.file.InvalidPathException
import java.nio.file.Path

class ValueObjectArtifactPlanner : GeneratorProvider {
    override val id: String = "types-value-object"

    override fun plan(config: ProjectConfig, model: CanonicalModel): List<ArtifactPlanItem> {
        if (model.valueObjects.isEmpty()) {
            return emptyList()
        }

        val domainRoot = requireRelativeModuleRoot(config, "domain")
        val artifactLayout = ArtifactLayoutResolver(config.basePackage, config.artifactLayout)
        val typeRegistry = CanonicalTypeSymbolRegistryFactory.from(config, model, artifactLayout)

        return model.valueObjects.map { valueObject ->
            require(valueObject.storage == ValueObjectStorage.JSON) {
                "value object ${valueObject.name} storage is unsupported: ${valueObject.storage}"
            }
            require(valueObject.fields.isNotEmpty()) {
                "value object ${valueObject.name} must declare at least one field"
            }
            val renderModel = ValueObjectRenderModelFactory.create(valueObject, typeRegistry)
            ArtifactPlanItem(
                generatorId = id,
                moduleRole = "domain",
                templateId = config.artifactLayout.valueObject.id,
                outputPath = artifactLayout.kotlinSourcePath(domainRoot, valueObject.packageName, valueObject.name),
                context = renderModel.toContextMap(),
                conflictPolicy = config.templates.conflictPolicy,
                outputKind = ArtifactOutputKind.CHECKED_IN_SOURCE,
                resolvedOutputRoot = artifactLayout.kotlinSourceRoot(domainRoot),
            )
        }
    }
}

private data class ValueObjectRenderModel(
    val packageName: String,
    val typeName: String,
    val name: String,
    val description: String?,
    val aggregates: List<String>,
    val storage: String,
    val imports: List<String>,
    val fields: List<ValueObjectFieldRenderModel>,
) {
    fun toContextMap(): Map<String, Any?> = mapOf(
        "packageName" to packageName,
        "typeName" to typeName,
        "name" to name,
        "description" to description,
        "aggregates" to aggregates,
        "buildingBlock" to buildingBlockContext(),
        "storage" to storage,
        "imports" to imports,
        "fields" to fields.map { it.toContextMap() },
        "planner" to "ValueObjectArtifactPlanner",
    )

    private fun buildingBlockContext(): Map<String, Any?> = mapOf(
        "tag" to "value_object",
        "tagKotlinStringLiteral" to "value_object".toKotlinStringLiteral(),
        "name" to name,
        "nameKotlinStringLiteral" to name.toKotlinStringLiteral(),
        "packageName" to packageName,
        "packageNameKotlinStringLiteral" to packageName.toKotlinStringLiteral(),
        "description" to description,
        "descriptionKotlinStringLiteral" to description.orEmpty().toKotlinStringLiteral(),
        "aggregates" to aggregates,
        "aggregateKotlinStringLiterals" to aggregates.map { it.toKotlinStringLiteral() },
        "eventName" to "",
        "eventNameKotlinStringLiteral" to "".toKotlinStringLiteral(),
        "family" to "value-object",
        "familyKotlinStringLiteral" to "value-object".toKotlinStringLiteral(),
        "variant" to "",
        "variantKotlinStringLiteral" to "".toKotlinStringLiteral(),
    )
}

private data class ValueObjectFieldRenderModel(
    val name: String,
    val renderedType: String,
    val nullable: Boolean,
) {
    fun toContextMap(): Map<String, Any?> = mapOf(
        "name" to name,
        "type" to renderedType,
        "renderedType" to renderedType,
        "nullable" to nullable,
    )
}

private object ValueObjectRenderModelFactory {
    fun create(
        valueObject: ValueObjectModel,
        typeRegistry: TypeSymbolRegistry,
    ): ValueObjectRenderModel {
        val plannedFields = valueObject.fields.map { field ->
            val type = ValueObjectTypeParser.parse(field.type)
            val resolved = ValueObjectTypeResolver.resolve(
                type = type,
                symbolRegistry = typeRegistry,
                aggregateContext = valueObject.aggregates,
            )
            field to resolved.withNullability(field.nullable)
        }

        return ValueObjectRenderModel(
            packageName = valueObject.packageName,
            typeName = valueObject.name,
            name = valueObject.name,
            description = valueObject.description,
            aggregates = valueObject.aggregates,
            storage = valueObject.storage.name,
            imports = plannedFields.flatMap { (_, resolved) -> resolved.imports }.distinct().sorted(),
            fields = plannedFields.map { (field, resolved) ->
                ValueObjectFieldRenderModel(
                    name = field.name,
                    renderedType = resolved.renderedType,
                    nullable = field.nullable,
                )
            },
        )
    }
}

private fun requireRelativeModuleRoot(config: ProjectConfig, role: String): String {
    val moduleRoot = config.modules[role] ?: throw IllegalArgumentException("$role module is required")
    if (moduleRoot.isBlank()) {
        throw IllegalArgumentException("$role module must be a valid relative filesystem path: $moduleRoot")
    }
    if (moduleRoot.startsWith(":")) {
        throw IllegalArgumentException("$role module must be a valid relative filesystem path: $moduleRoot")
    }

    val path = try {
        Path.of(moduleRoot)
    } catch (ex: InvalidPathException) {
        throw IllegalArgumentException("$role module must be a valid relative filesystem path: $moduleRoot", ex)
    }

    if (path.isAbsolute || path.root != null) {
        throw IllegalArgumentException("$role module must be a valid relative filesystem path: $moduleRoot")
    }

    val normalized = path.normalize()
    if (normalized.nameCount > 0 && normalized.getName(0).toString() == "..") {
        throw IllegalArgumentException("$role module must be a valid relative filesystem path: $moduleRoot")
    }

    return moduleRoot
}

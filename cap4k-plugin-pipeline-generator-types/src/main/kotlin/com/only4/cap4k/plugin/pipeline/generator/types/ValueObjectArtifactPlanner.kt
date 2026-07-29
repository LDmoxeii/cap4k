package com.only4.cap4k.plugin.pipeline.generator.types

import com.only4.cap4k.plugin.pipeline.api.ArtifactLayoutResolver
import com.only4.cap4k.plugin.pipeline.api.ArtifactOutputKind
import com.only4.cap4k.plugin.pipeline.api.ArtifactPlanItem
import com.only4.cap4k.plugin.pipeline.api.CanonicalModel
import com.only4.cap4k.plugin.pipeline.api.ConflictPolicy
import com.only4.cap4k.plugin.pipeline.api.GeneratorProvider
import com.only4.cap4k.plugin.pipeline.api.JsonValuePersistenceProjection
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig
import com.only4.cap4k.plugin.pipeline.api.SemanticBuiltinTypeRef
import com.only4.cap4k.plugin.pipeline.api.SemanticListTypeRef
import com.only4.cap4k.plugin.pipeline.api.SemanticMapTypeRef
import com.only4.cap4k.plugin.pipeline.api.SemanticNamedTypeRef
import com.only4.cap4k.plugin.pipeline.api.SemanticSetTypeRef
import com.only4.cap4k.plugin.pipeline.api.SemanticTypeRef
import com.only4.cap4k.plugin.pipeline.api.SemanticValueField
import com.only4.cap4k.plugin.pipeline.api.ValueObjectModel
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

        return model.valueObjects.flatMap { valueObject ->
            require(valueObject.fields.isNotEmpty()) {
                "value object ${valueObject.name} must declare at least one field"
            }
            val renderModel = ValueObjectRenderModelFactory.create(valueObject)
            buildList {
                add(
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
                )
                when (val persistence = valueObject.persistence) {
                    null -> Unit
                    is JsonValuePersistenceProjection -> add(
                        jsonConverterArtifact(
                            config = config,
                            artifactLayout = artifactLayout,
                            domainRoot = domainRoot,
                            valueObject = valueObject,
                            projection = persistence,
                        )
                    )
                }
            }
        }
    }

    private fun jsonConverterArtifact(
        config: ProjectConfig,
        artifactLayout: ArtifactLayoutResolver,
        domainRoot: String,
        valueObject: ValueObjectModel,
        projection: JsonValuePersistenceProjection,
    ): ArtifactPlanItem {
        val converterFqn = projection.converterClassFqn.trim()
        require('.' in converterFqn && !converterFqn.endsWith('.')) {
            "value object ${valueObject.name} JSON converter FQN is invalid: ${projection.converterClassFqn}"
        }
        val converterPackageName = converterFqn.substringBeforeLast('.')
        val converterTypeName = converterFqn.substringAfterLast('.')
        val valueObjectFqn = valueObject.definition.identity.fqn
        return ArtifactPlanItem(
            generatorId = id,
            moduleRole = "domain",
            templateId = config.artifactLayout.valueObjectJsonConverter.id,
            outputPath = artifactLayout.generatedKotlinSourcePath(
                domainRoot,
                converterPackageName,
                converterTypeName,
            ),
            context = mapOf(
                "packageName" to converterPackageName,
                "typeName" to converterTypeName,
                "valueObjectTypeName" to valueObject.name,
                "valueObjectTypeFqn" to valueObjectFqn,
                "imports" to emptyList<String>(),
                "planner" to "ValueObjectArtifactPlanner",
            ),
            conflictPolicy = ConflictPolicy.OVERWRITE,
            outputKind = ArtifactOutputKind.GENERATED_SOURCE,
            resolvedOutputRoot = artifactLayout.generatedKotlinSourceRoot(domainRoot),
        )
    }
}

private data class ValueObjectRenderModel(
    val packageName: String,
    val typeName: String,
    val name: String,
    val description: String?,
    val aggregates: List<String>,
    val imports: List<String>,
    val fields: List<ValueObjectFieldRenderModel>,
    val nestedTypes: List<ValueObjectNestedTypeRenderModel>,
) {
    fun toContextMap(): Map<String, Any?> = mapOf(
        "packageName" to packageName,
        "typeName" to typeName,
        "name" to name,
        "description" to description,
        "aggregates" to aggregates,
        "buildingBlock" to buildingBlockContext(),
        "imports" to imports,
        "fields" to fields.map { it.toContextMap() },
        "nestedTypes" to nestedTypes.map { it.toContextMap() },
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

private data class ValueObjectNestedTypeRenderModel(
    val name: String,
    val fields: List<ValueObjectFieldRenderModel>,
) {
    fun toContextMap(): Map<String, Any?> = mapOf(
        "name" to name,
        "fields" to fields.map { it.toContextMap() },
    )
}

private data class ValueObjectFieldRenderModel(
    val name: String,
    val renderedType: String,
    val defaultValue: String?,
) {
    fun toContextMap(): Map<String, Any?> = mapOf(
        "name" to name,
        "type" to renderedType,
        "renderedType" to renderedType,
        "defaultValue" to defaultValue,
    )
}

private object ValueObjectRenderModelFactory {
    fun create(valueObject: ValueObjectModel): ValueObjectRenderModel {
        val nestedDefinitions = flattenNestedDefinitions(valueObject.definition)
        nestedDefinitions
            .groupBy { it.identity.simpleName }
            .entries
            .firstOrNull { (_, definitions) -> definitions.size > 1 }
            ?.let { (name, _) ->
                throw IllegalArgumentException(
                    "value object ${valueObject.definition.identity.fqn} has colliding flattened nested type $name"
                )
            }
        val localNestedTypeFqns = nestedDefinitions.mapTo(linkedSetOf()) { it.identity.fqn }
        val allFields = valueObject.fields + nestedDefinitions.flatMap { it.fields }
        val collidingNamedTypeFqns = (allFields
            .flatMap { collectNamedTypeFqns(it.type) }
            + localNestedTypeFqns
            + valueObject.definition.identity.fqn)
            .groupBy({ it.substringAfterLast('.') }, { it })
            .filterValues { fqns -> fqns.distinct().size > 1 }
            .values
            .flatten()
            .toSet()
        val imports = linkedSetOf<String>()
        fun renderField(field: SemanticValueField): ValueObjectFieldRenderModel {
            val rendered = renderType(
                type = field.type,
                ownerPackageName = valueObject.packageName,
                localNestedTypeFqns = localNestedTypeFqns,
                collidingNamedTypeFqns = collidingNamedTypeFqns,
            )
            imports += rendered.imports
            return ValueObjectFieldRenderModel(
                name = field.name,
                renderedType = rendered.text,
                defaultValue = field.defaultValue?.kotlinExpression,
            )
        }
        val renderedFields = valueObject.fields.map(::renderField)
        val renderedNestedTypes = nestedDefinitions.map { nested ->
            ValueObjectNestedTypeRenderModel(
                name = nested.identity.simpleName,
                fields = nested.fields.map(::renderField),
            )
        }
        return ValueObjectRenderModel(
            packageName = valueObject.packageName,
            typeName = valueObject.name,
            name = valueObject.name,
            description = valueObject.description,
            aggregates = valueObject.aggregates,
            imports = imports.sorted(),
            fields = renderedFields,
            nestedTypes = renderedNestedTypes,
        )
    }

    private fun renderType(
        type: SemanticTypeRef,
        ownerPackageName: String,
        localNestedTypeFqns: Set<String>,
        collidingNamedTypeFqns: Set<String>,
    ): RenderedSemanticType {
        val rendered = when (type) {
            is SemanticBuiltinTypeRef -> RenderedSemanticType(type.kind.name.toKotlinTypeName())
            is SemanticNamedTypeRef -> {
                val fqn = type.symbol.fqn
                val simpleName = type.symbol.simpleName
                when {
                    fqn in localNestedTypeFqns -> RenderedSemanticType(simpleName)
                    fqn in collidingNamedTypeFqns -> RenderedSemanticType(fqn)
                    type.symbol.packageName == ownerPackageName ->
                        RenderedSemanticType(type.symbol.typePath.joinToString("."))
                    type.symbol.packageName == "kotlin" || type.symbol.packageName == "java.lang" ->
                        RenderedSemanticType(type.symbol.typePath.joinToString("."))
                    else -> RenderedSemanticType(simpleName, setOf(fqn))
                }
            }
            is SemanticListTypeRef -> renderContainer(
                "List",
                listOf(type.elementType),
                ownerPackageName,
                localNestedTypeFqns,
                collidingNamedTypeFqns,
            )
            is SemanticSetTypeRef -> renderContainer(
                "Set",
                listOf(type.elementType),
                ownerPackageName,
                localNestedTypeFqns,
                collidingNamedTypeFqns,
            )
            is SemanticMapTypeRef -> renderContainer(
                "Map",
                listOf(type.keyType, type.valueType),
                ownerPackageName,
                localNestedTypeFqns,
                collidingNamedTypeFqns,
            )
        }
        return if (type.nullable) rendered.copy(text = "${rendered.text}?") else rendered
    }

    private fun renderContainer(
        name: String,
        arguments: List<SemanticTypeRef>,
        ownerPackageName: String,
        localNestedTypeFqns: Set<String>,
        collidingNamedTypeFqns: Set<String>,
    ): RenderedSemanticType {
        val renderedArguments = arguments.map { argument ->
            renderType(
                type = argument,
                ownerPackageName = ownerPackageName,
                localNestedTypeFqns = localNestedTypeFqns,
                collidingNamedTypeFqns = collidingNamedTypeFqns,
            )
        }
        return RenderedSemanticType(
            text = "$name<${renderedArguments.joinToString(", ") { it.text }}>",
            imports = renderedArguments.flatMapTo(linkedSetOf()) { it.imports },
        )
    }

    private fun collectNamedTypeFqns(type: SemanticTypeRef): List<String> = when (type) {
        is SemanticBuiltinTypeRef -> emptyList()
        is SemanticNamedTypeRef -> listOf(type.symbol.fqn)
        is SemanticListTypeRef -> collectNamedTypeFqns(type.elementType)
        is SemanticSetTypeRef -> collectNamedTypeFqns(type.elementType)
        is SemanticMapTypeRef -> collectNamedTypeFqns(type.keyType) + collectNamedTypeFqns(type.valueType)
    }

    private fun flattenNestedDefinitions(
        definition: com.only4.cap4k.plugin.pipeline.api.SemanticValueDefinition,
    ): List<com.only4.cap4k.plugin.pipeline.api.SemanticValueDefinition> =
        definition.nestedDefinitions.flatMap { nested -> listOf(nested) + flattenNestedDefinitions(nested) }
}

private data class RenderedSemanticType(
    val text: String,
    val imports: Set<String> = emptySet(),
)

private fun String.toKotlinTypeName(): String = lowercase().replaceFirstChar { it.uppercase() }

private fun requireRelativeModuleRoot(config: ProjectConfig, role: String): String {
    val moduleRoot = config.modules[role] ?: throw IllegalArgumentException("$role module is required")
    if (moduleRoot.isBlank() || moduleRoot.startsWith(":")) {
        throw IllegalArgumentException("$role module must be a valid relative filesystem path: $moduleRoot")
    }
    val path = try {
        Path.of(moduleRoot)
    } catch (ex: InvalidPathException) {
        throw IllegalArgumentException("$role module must be a valid relative filesystem path: $moduleRoot", ex)
    }
    val normalized = path.normalize()
    if (path.isAbsolute || path.root != null || (normalized.nameCount > 0 && normalized.getName(0).toString() == "..")) {
        throw IllegalArgumentException("$role module must be a valid relative filesystem path: $moduleRoot")
    }
    return moduleRoot
}

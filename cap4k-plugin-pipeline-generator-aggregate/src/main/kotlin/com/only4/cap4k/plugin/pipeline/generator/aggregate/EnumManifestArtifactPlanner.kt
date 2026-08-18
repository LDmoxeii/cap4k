package com.only4.cap4k.plugin.pipeline.generator.aggregate

import com.only4.cap4k.plugin.pipeline.api.ArtifactLayoutResolver
import com.only4.cap4k.plugin.pipeline.api.ArtifactOutputKind
import com.only4.cap4k.plugin.pipeline.api.ArtifactPlanItem
import com.only4.cap4k.plugin.pipeline.api.CanonicalEnumCatalog
import com.only4.cap4k.plugin.pipeline.api.CanonicalEnumDescriptor
import com.only4.cap4k.plugin.pipeline.api.CanonicalModel
import com.only4.cap4k.plugin.pipeline.api.CanonicalTypeIdentity
import com.only4.cap4k.plugin.pipeline.api.ConflictPolicy
import com.only4.cap4k.plugin.pipeline.api.EntityModel
import com.only4.cap4k.plugin.pipeline.api.EnumPropertyModel
import com.only4.cap4k.plugin.pipeline.api.GeneratorProvider
import com.only4.cap4k.plugin.pipeline.api.PipelineBoundaryAuthorities
import com.only4.cap4k.plugin.pipeline.api.PipelineBoundaryKind
import com.only4.cap4k.plugin.pipeline.api.PipelineCapabilityActivation
import com.only4.cap4k.plugin.pipeline.api.PipelineCapabilityBoundary
import com.only4.cap4k.plugin.pipeline.api.PipelineCapabilityDescriptor
import com.only4.cap4k.plugin.pipeline.api.PipelineCapabilityKind
import com.only4.cap4k.plugin.pipeline.api.PipelineExecutionLane
import com.only4.cap4k.plugin.pipeline.api.PipelineInputRequirement
import com.only4.cap4k.plugin.pipeline.api.PipelinePublicTasks
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig
import com.only4.cap4k.plugin.pipeline.api.SemanticBuiltinType
import com.only4.cap4k.plugin.pipeline.api.SemanticBuiltinTypeRef
import com.only4.cap4k.plugin.pipeline.api.SemanticEnumValue
import com.only4.cap4k.plugin.pipeline.api.SemanticNamedTypeRef
import com.only4.cap4k.plugin.pipeline.api.SemanticTypeRef
import com.only4.cap4k.plugin.pipeline.api.SharedEnumDefinition

class EnumManifestArtifactPlanner : GeneratorProvider {
    override val id: String = "enum"
    override val descriptor: PipelineCapabilityDescriptor = PipelineCapabilityDescriptor.builtIn(
        providerId = id,
        displayName = "Enum Generator",
        kind = PipelineCapabilityKind.GENERATOR,
        module = "cap4k-plugin-pipeline-generator-aggregate",
        activation = PipelineCapabilityActivation.INPUT_DRIVEN,
        tacticalCarriers = listOf("Enum"),
        executionLanes = listOf(PipelineExecutionLane.AUTHORING),
        tasks = listOf(PipelinePublicTasks.PLAN, PipelinePublicTasks.GENERATE),
        inputRequirements = listOf(
            PipelineInputRequirement(
                id = "enum-definitions",
                capabilityIds = listOf("pipeline.source.enum-manifest"),
            ),
        ),
        outputKinds = listOf(ArtifactOutputKind.CHECKED_IN_SOURCE),
        boundaries = listOf(
            PipelineCapabilityBoundary(PipelineBoundaryKind.GENERATION, PipelineBoundaryAuthorities.PIPELINE_GENERATOR),
        ),
    )

    override fun plan(config: ProjectConfig, model: CanonicalModel): List<ArtifactPlanItem> {
        if (model.sharedEnums.isEmpty()) return emptyList()

        val artifactLayout = ArtifactLayoutResolver(config.basePackage, config.artifactLayout)
        val catalog = AggregateEnumPlanning.from(model, artifactLayout, config.typeRegistry.entries)
        val manifestEnums = ManifestEnumCatalogSelection.from(model, catalog)
        return model.sharedEnums.flatMap { definition ->
            manifestEnums.descriptorsFor(definition).map { descriptor ->
                val packageName = descriptor.packageName()
                val rendering = EnumManifestRendering(packageName, definition)
                checkedInKotlinArtifact(
                    config = config,
                    artifactLayout = artifactLayout,
                    moduleRole = "domain",
                    templateId = "aggregate/enum.kt.peb",
                    packageName = packageName,
                    typeName = definition.typeName,
                    context = mapOf(
                        "packageName" to packageName,
                        "typeName" to definition.typeName,
                        "imports" to rendering.imports,
                        "properties" to rendering.properties,
                        "items" to rendering.items,
                        "buildingBlock" to mapOf(
                            "tag" to "enum",
                            "tagKotlinStringLiteral" to "enum".toKotlinStringLiteral(),
                            "name" to definition.typeName,
                            "nameKotlinStringLiteral" to definition.typeName.toKotlinStringLiteral(),
                            "packageName" to definition.packageName,
                            "packageNameKotlinStringLiteral" to definition.packageName.toKotlinStringLiteral(),
                            "description" to "",
                            "descriptionKotlinStringLiteral" to "".toKotlinStringLiteral(),
                            "aggregates" to definition.aggregates,
                            "aggregateKotlinStringLiterals" to definition.aggregates.map { it.toKotlinStringLiteral() },
                            "eventName" to "",
                            "eventNameKotlinStringLiteral" to "".toKotlinStringLiteral(),
                            "family" to "enum",
                            "familyKotlinStringLiteral" to "enum".toKotlinStringLiteral(),
                            "variant" to "",
                            "variantKotlinStringLiteral" to "".toKotlinStringLiteral(),
                        ),
                    ),
                    conflictPolicy = ConflictPolicy.SKIP,
                    generatorId = id,
                )
            }
        }
    }
}

private class EnumManifestRendering(
    private val currentPackage: String,
    definition: SharedEnumDefinition,
) {
    private val namedIdentities = buildList {
        definition.properties.mapNotNullTo(this) { (it.type as? SemanticNamedTypeRef)?.symbol }
        definition.items.flatMapTo(this) { item ->
            item.propertyValues.mapNotNull { (it as? SemanticEnumValue.EnumConstantValue)?.enumType }
        }
    }
    private val visibleFqnsBySimpleName = buildList {
        add("${currentPackage}.${definition.typeName}")
        add("jakarta.persistence.AttributeConverter")
        add("com.only4.cap4k.analysis.metadata.DesignBlockMetadata")
        add("java.math.BigInteger")
        add("java.math.BigDecimal")
        addAll(namedIdentities.map(CanonicalTypeIdentity::fqn))
    }.groupBy { it.substringAfterLast('.') }.mapValues { (_, fqns) -> fqns.distinct() }
    private val collidingFqns = visibleFqnsBySimpleName.values
        .filter { fqns -> fqns.size > 1 }
        .flatten()
        .toSet()
    private val collectedImports = linkedSetOf<String>()

    val properties: List<Map<String, Any?>> = definition.properties.map { property ->
        mapOf("name" to property.name, "renderedType" to renderType(property))
    }
    val items: List<Map<String, Any?>> = definition.items.map { item ->
        mapOf(
            "value" to item.value,
            "name" to item.name,
            "descriptionKotlinExpression" to item.description.toKotlinStringLiteral(),
            "propertyExpressions" to item.propertyValues.map(::renderValue),
        )
    }
    val imports: List<String>
        get() = collectedImports.sorted()

    private fun renderType(property: EnumPropertyModel): String = when (val type = property.type) {
        is SemanticBuiltinTypeRef -> renderBuiltin(type.kind) + if (type.nullable) "?" else ""
        is SemanticNamedTypeRef -> renderIdentity(type.symbol) + if (type.nullable) "?" else ""
        else -> error("enum property ${property.name} has unsupported canonical type ${type::class.simpleName}")
    }

    private fun renderIdentity(identity: CanonicalTypeIdentity): String {
        if (identity.fqn in collidingFqns) return identity.fqn
        if (identity.packageName == currentPackage || identity.packageName == "kotlin" || identity.packageName == "java.lang") {
            return identity.typePath.joinToString(".")
        }
        collectedImports += identity.fqn
        return identity.simpleName
    }

    private fun renderValue(value: SemanticEnumValue): String = when (value) {
        SemanticEnumValue.Null -> "null"
        is SemanticEnumValue.StringValue -> value.value.toKotlinStringLiteral()
        is SemanticEnumValue.BooleanValue -> value.value.toString()
        is SemanticEnumValue.ByteValue -> "${value.value}.toByte()"
        is SemanticEnumValue.ShortValue -> "${value.value}.toShort()"
        is SemanticEnumValue.IntValue -> value.value.toString()
        is SemanticEnumValue.LongValue -> "${value.value}L"
        is SemanticEnumValue.FloatValue -> "${value.value}f"
        is SemanticEnumValue.DoubleValue -> value.value.toString()
        is SemanticEnumValue.BigIntegerValue ->
            "${renderBuiltin(SemanticBuiltinType.BIG_INTEGER)}(${value.value.toString().toKotlinStringLiteral()})"
        is SemanticEnumValue.BigDecimalValue ->
            "${renderBuiltin(SemanticBuiltinType.BIG_DECIMAL)}(${value.value.toPlainString().toKotlinStringLiteral()})"
        is SemanticEnumValue.EnumConstantValue -> "${renderIdentity(value.enumType)}.${value.constantName}"
    }

    private fun renderBuiltin(kind: SemanticBuiltinType): String {
        val fqn = when (kind) {
            SemanticBuiltinType.BIG_INTEGER -> "java.math.BigInteger"
            SemanticBuiltinType.BIG_DECIMAL -> "java.math.BigDecimal"
            else -> return builtinName(kind)
        }
        if (fqn in collidingFqns) return fqn
        collectedImports += fqn
        return fqn.substringAfterLast('.')
    }

    private fun builtinName(kind: SemanticBuiltinType): String = when (kind) {
        SemanticBuiltinType.STRING -> "String"
        SemanticBuiltinType.BOOLEAN -> "Boolean"
        SemanticBuiltinType.BYTE -> "Byte"
        SemanticBuiltinType.SHORT -> "Short"
        SemanticBuiltinType.INT -> "Int"
        SemanticBuiltinType.LONG -> "Long"
        SemanticBuiltinType.FLOAT -> "Float"
        SemanticBuiltinType.DOUBLE -> "Double"
        SemanticBuiltinType.BIG_INTEGER -> "BigInteger"
        SemanticBuiltinType.BIG_DECIMAL -> "BigDecimal"
        else -> error("unsupported enum property builtin type $kind")
    }
}

private class ManifestEnumCatalogSelection(
    private val sharedByTypeName: Map<String, CanonicalEnumDescriptor>,
    private val localByKey: Map<ManifestLocalEnumKey, CanonicalEnumDescriptor>,
    private val entities: List<EntityModel>,
    private val aggregateRootNameByEntity: Map<ManifestEntityKey, String>,
) {
    fun descriptorsFor(definition: SharedEnumDefinition): List<CanonicalEnumDescriptor> =
        if (definition.aggregates.isEmpty()) {
            listOf(requireNotNull(sharedByTypeName[definition.typeName]) { "missing shared enum catalog entry for ${definition.typeName}" })
        } else {
            localOwnerKeys(definition).map { key ->
                requireNotNull(localByKey[key]) { "missing local enum catalog entry for ${key.ownerPackageName}.${key.typeName}" }
            }
        }

    private fun localOwnerKeys(definition: SharedEnumDefinition): List<ManifestLocalEnumKey> {
        val ownerAggregateName = requireNotNull(definition.aggregates.singleOrNull()) {
            "enum ${definition.typeName} may declare at most one aggregate"
        }
        return entities.filter { entity -> aggregateRootNameByEntity[entity.key()] == ownerAggregateName }
            .map { entity -> ManifestLocalEnumKey(entity.packageName, definition.typeName) }.distinct()
            .ifEmpty { listOf(ManifestLocalEnumKey(ownerAggregateName, definition.typeName)) }
    }

    companion object {
        fun from(model: CanonicalModel, catalog: CanonicalEnumCatalog): ManifestEnumCatalogSelection =
            ManifestEnumCatalogSelection(
                sharedByTypeName = catalog.sharedEnums.associateBy { it.typeName },
                localByKey = catalog.localEnums.mapNotNull { descriptor ->
                    descriptor.ownerPackageName?.let { ManifestLocalEnumKey(it, descriptor.typeName) to descriptor }
                }.toMap(),
                entities = model.entities,
                aggregateRootNameByEntity = buildAggregateRootNameByEntity(model.entities),
            )

        private fun buildAggregateRootNameByEntity(entities: List<EntityModel>): Map<ManifestEntityKey, String> {
            val entitiesByKey = entities.associateBy { it.key() }
            val entitiesByName = entities.groupBy { it.name }
            val resolving = mutableSetOf<ManifestEntityKey>()
            val resolved = linkedMapOf<ManifestEntityKey, String>()
            fun resolve(entity: EntityModel): String {
                val key = entity.key()
                resolved[key]?.let { return it }
                if (!resolving.add(key)) return entity.name
                val parentEntityName = entity.parentEntityName?.takeIf { it.isNotBlank() }
                val rootName = when {
                    entity.aggregateRoot -> entity.name
                    parentEntityName == null -> entity.name
                    else -> {
                        val parent = entitiesByKey[ManifestEntityKey(entity.packageName, parentEntityName)] ?: entitiesByName[parentEntityName]?.singleOrNull()
                        parent?.let { resolve(it) } ?: entity.name
                    }
                }
                resolving.remove(key)
                resolved[key] = rootName
                return rootName
            }
            entities.forEach { resolve(it) }
            return resolved
        }
    }
}

private data class ManifestLocalEnumKey(val ownerPackageName: String, val typeName: String)
private data class ManifestEntityKey(val packageName: String, val name: String)
private fun EntityModel.key(): ManifestEntityKey = ManifestEntityKey(packageName, name)
private fun CanonicalEnumDescriptor.packageName(): String = fqn.substringBeforeLast('.', missingDelimiterValue = "")

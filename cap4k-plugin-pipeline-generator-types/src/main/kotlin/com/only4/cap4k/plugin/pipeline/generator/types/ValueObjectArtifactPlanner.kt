package com.only4.cap4k.plugin.pipeline.generator.types

import com.only4.cap4k.plugin.pipeline.api.ArtifactLayoutResolver
import com.only4.cap4k.plugin.pipeline.api.ArtifactOutputKind
import com.only4.cap4k.plugin.pipeline.api.ArtifactPlanItem
import com.only4.cap4k.plugin.pipeline.api.CanonicalEnumCatalog
import com.only4.cap4k.plugin.pipeline.api.CanonicalModel
import com.only4.cap4k.plugin.pipeline.api.EntityModel
import com.only4.cap4k.plugin.pipeline.api.FieldModel
import com.only4.cap4k.plugin.pipeline.api.GeneratorProvider
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig
import com.only4.cap4k.plugin.pipeline.api.ValueObjectModel
import com.only4.cap4k.plugin.pipeline.api.ValueObjectStorage
import com.only4.cap4k.plugin.pipeline.api.ownerAggregate
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
        val typeBindings = ValueObjectTypeBindings.from(config, model, artifactLayout)

        return model.valueObjects.map { valueObject ->
            require(valueObject.storage == ValueObjectStorage.JSON) {
                "value object ${valueObject.name} storage is unsupported: ${valueObject.storage}"
            }
            require(valueObject.fields.isNotEmpty()) {
                "value object ${valueObject.name} must declare at least one field"
            }
            val renderModel = ValueObjectRenderModelFactory.create(
                valueObject,
                typeBindings.registryFor(valueObject),
            )
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
        typeRegistry: Map<String, String>,
    ): ValueObjectRenderModel {
        val plannedFields = valueObject.fields.map { field ->
            val type = ValueObjectTypeParser.parse(field.type)
            val resolved = ValueObjectTypeResolver.resolve(type, typeRegistry)
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

private class ValueObjectTypeBindings private constructor(
    private val baseRegistry: Map<String, String>,
    private val sharedEnumFqns: Map<String, String>,
    private val localEnumFqnsByAggregate: Map<String, Map<String, String>>,
) {
    fun registryFor(valueObject: ValueObjectModel): Map<String, String> {
        val registry = linkedMapOf<String, String>()
        registry.putAll(baseRegistry)
        mergeUnique(registry, sharedEnumFqns, "shared enum")

        val localEnums = valueObject.ownerAggregate
            ?.let { aggregateName -> localEnumFqnsByAggregate[aggregateName].orEmpty() }
            .orEmpty()
        mergeOwnerEnums(registry, localEnums, valueObject)

        return registry
    }

    private fun mergeUnique(
        target: MutableMap<String, String>,
        additions: Map<String, String>,
        sourceLabel: String,
    ) {
        additions.forEach { (typeName, fqn) ->
            val existing = target[typeName]
            require(existing == null || existing == fqn) {
                "ambiguous value object type binding for $typeName: $existing, $fqn ($sourceLabel)"
            }
            target[typeName] = fqn
        }
    }

    private fun mergeOwnerEnums(
        target: MutableMap<String, String>,
        additions: Map<String, String>,
        valueObject: ValueObjectModel,
    ) {
        additions.forEach { (typeName, fqn) ->
            val existing = target[typeName]
            val sharedFqn = sharedEnumFqns[typeName]
            require(existing == null || existing == fqn || existing == sharedFqn) {
                "ambiguous value object type binding for ${valueObject.name}.$typeName: $existing, $fqn"
            }
            target[typeName] = fqn
        }
    }

    companion object {
        fun from(
            config: ProjectConfig,
            model: CanonicalModel,
            artifactLayout: ArtifactLayoutResolver,
        ): ValueObjectTypeBindings {
            val sharedDefinitions = model.sharedEnums.filter { it.aggregates.isEmpty() }
            val localDefinitions = model.sharedEnums.filter { it.aggregates.isNotEmpty() }
            val sharedCatalog = CanonicalEnumCatalog.from(
                model.copy(sharedEnums = sharedDefinitions),
                artifactLayout,
                emptyMap(),
            )
            val localCatalog = CanonicalEnumCatalog.from(
                model.copy(sharedEnums = localDefinitions),
                artifactLayout,
                emptyMap(),
            )

            return ValueObjectTypeBindings(
                baseRegistry = config.valueObjectTypeRegistryFqns(model),
                sharedEnumFqns = sharedCatalog.sharedEnums.associate { descriptor ->
                    descriptor.typeName to descriptor.fqn
                },
                localEnumFqnsByAggregate = localEnumFqnsByAggregate(model, localCatalog),
            )
        }

        private fun localEnumFqnsByAggregate(
            model: CanonicalModel,
            localCatalog: CanonicalEnumCatalog,
        ): Map<String, Map<String, String>> {
            val aggregateRootNameByEntity = buildAggregateRootNameByEntity(model.entities)
            val entityPackagesByAggregate = model.entities
                .groupBy { entity -> aggregateRootNameByEntity[entity.key()] ?: entity.name }
                .mapValues { (_, entities) -> entities.map { entity -> entity.packageName }.distinct() }

            return model.sharedEnums
                .filter { definition -> definition.aggregates.isNotEmpty() }
                .flatMap { definition ->
                    val aggregateName = requireNotNull(definition.aggregates.singleOrNull()) {
                        "enum ${definition.typeName} may declare at most one aggregate"
                    }
                    val ownerPackages = entityPackagesByAggregate[aggregateName].orEmpty()
                        .ifEmpty { listOf(aggregateName) }
                    val descriptors = ownerPackages.mapNotNull { ownerPackage ->
                        localCatalog.localEnums.singleOrNull { descriptor ->
                            descriptor.ownerPackageName == ownerPackage && descriptor.typeName == definition.typeName
                        }
                    }.ifEmpty {
                        localCatalog.localEnums.filter { descriptor ->
                            descriptor.ownerScope == aggregateName && descriptor.typeName == definition.typeName
                        }
                    }
                    descriptors.map { descriptor -> aggregateName to (descriptor.typeName to descriptor.fqn) }
                }
                .groupBy({ it.first }, { it.second })
                .mapValues { (_, bindings) -> collapseBindings(bindings) }
        }

        private fun collapseBindings(bindings: List<Pair<String, String>>): Map<String, String> = bindings
            .groupBy({ it.first }, { it.second })
            .mapValues { (typeName, fqns) ->
                val distinctFqns = fqns.distinct()
                require(distinctFqns.size == 1) {
                    "ambiguous local enum binding for $typeName: ${distinctFqns.joinToString()}"
                }
                distinctFqns.single()
            }

        private fun buildAggregateRootNameByEntity(entities: List<EntityModel>): Map<EntityKey, String> {
            val entitiesByKey = entities.associateBy { entity -> entity.key() }
            val entitiesByName = entities.groupBy { entity -> entity.name }
            val resolving = mutableSetOf<EntityKey>()
            val resolved = linkedMapOf<EntityKey, String>()

            fun resolve(entity: EntityModel): String {
                val key = entity.key()
                resolved[key]?.let { return it }
                if (!resolving.add(key)) {
                    return entity.name
                }
                val parentEntityName = entity.parentEntityName?.takeIf { it.isNotBlank() }
                val rootName = when {
                    entity.aggregateRoot -> entity.name
                    parentEntityName == null -> entity.name
                    else -> {
                        val parent = entitiesByKey[EntityKey(entity.packageName, parentEntityName)]
                            ?: entitiesByName[parentEntityName]?.singleOrNull()
                        parent?.let { resolve(it) } ?: entity.name
                    }
                }
                resolving.remove(key)
                resolved[key] = rootName
                return rootName
            }

            entities.forEach { entity -> resolve(entity) }
            return resolved
        }
    }
}

private data class EntityKey(
    val packageName: String,
    val name: String,
)

private fun EntityModel.key(): EntityKey = EntityKey(packageName = packageName, name = name)

private data class ParsedType(
    val tokenText: String,
    val nullable: Boolean = false,
    val arguments: List<ParsedType> = emptyList(),
)

private data class ResolvedType(
    val renderedType: String,
    val imports: Set<String> = emptySet(),
) {
    fun withNullability(nullable: Boolean): ResolvedType =
        if (nullable && !renderedType.endsWith("?")) {
            copy(renderedType = "$renderedType?")
        } else {
            this
        }
}

private object ValueObjectTypeResolver {
    private val builtInTypeNames = setOf(
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

    fun resolve(type: ParsedType, typeRegistry: Map<String, String>): ResolvedType {
        val resolvedArguments = type.arguments.map { resolve(it, typeRegistry) }
        val base = resolveBase(type.tokenText, typeRegistry)
        val renderedWithArguments = if (resolvedArguments.isEmpty()) {
            base.renderedType
        } else {
            resolvedArguments.joinToString(
                separator = ", ",
                prefix = "${base.renderedType}<",
                postfix = ">",
            ) { it.renderedType }
        }
        val rendered = if (type.nullable && !renderedWithArguments.endsWith("?")) {
            "$renderedWithArguments?"
        } else {
            renderedWithArguments
        }
        return ResolvedType(
            renderedType = rendered,
            imports = base.imports + resolvedArguments.flatMap { it.imports },
        )
    }

    private fun resolveBase(tokenText: String, typeRegistry: Map<String, String>): ResolvedType {
        if (tokenText in builtInTypeNames) {
            return ResolvedType(tokenText)
        }

        if (tokenText.contains('.')) {
            return ResolvedType(
                renderedType = tokenText.substringAfterLast('.'),
                imports = setOf(tokenText),
            )
        }

        val registryFqn = typeRegistry[tokenText]
        if (registryFqn != null) {
            return ResolvedType(
                renderedType = tokenText,
                imports = setOf(registryFqn),
            )
        }

        return ResolvedType(tokenText)
    }
}

private object ValueObjectTypeParser {
    fun parse(type: String): ParsedType {
        val input = type.trim()
        require(input.isNotEmpty()) { "type must not be blank" }

        val parser = Parser(input)
        val parsed = parser.parseType()
        parser.skipWhitespace()
        if (!parser.isAtEnd()) {
            parser.failMismatchedAngles()
        }
        return parsed
    }

    private class Parser(
        private val input: String,
    ) {
        private var index = 0

        fun parseType(): ParsedType {
            skipWhitespace()
            val tokenText = parseTokenText()
            skipWhitespace()

            val arguments = if (peek() == '<') {
                index++
                parseArguments()
            } else {
                emptyList()
            }

            skipWhitespace()
            val nullable = if (peek() == '?') {
                index++
                true
            } else {
                false
            }

            return ParsedType(
                tokenText = tokenText,
                nullable = nullable,
                arguments = arguments,
            )
        }

        private fun parseArguments(): List<ParsedType> {
            val arguments = mutableListOf<ParsedType>()
            skipWhitespace()
            if (peek() == '>') {
                failEmptyGenericArgument()
            }

            while (true) {
                skipWhitespace()
                if (peek() == ',' || peek() == '>') {
                    failEmptyGenericArgument()
                }
                arguments += parseType()
                skipWhitespace()
                when (peek()) {
                    ',' -> {
                        index++
                        skipWhitespace()
                        if (peek() == ',' || peek() == '>') {
                            failEmptyGenericArgument()
                        }
                    }
                    '>' -> {
                        index++
                        return arguments
                    }
                    null -> failMismatchedAngles()
                    else -> failMismatchedAngles()
                }
            }
        }

        private fun parseTokenText(): String {
            val start = index
            while (true) {
                val char = peek() ?: break
                if (char == '<' || char == '>' || char == ',' || char == '?' || char.isWhitespace()) {
                    break
                }
                index++
            }
            require(index > start) {
                "expected type token in type: $input"
            }
            return input.substring(start, index)
        }

        fun skipWhitespace() {
            while (peek()?.isWhitespace() == true) {
                index++
            }
        }

        fun isAtEnd(): Boolean = index >= input.length

        private fun peek(): Char? = input.getOrNull(index)

        fun failMismatchedAngles(): Nothing {
            throw IllegalArgumentException("mismatched angle brackets in type: $input")
        }

        private fun failEmptyGenericArgument(): Nothing {
            throw IllegalArgumentException("empty generic argument in type: $input")
        }
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

private fun ProjectConfig.valueObjectTypeRegistryFqns(model: CanonicalModel): Map<String, String> =
    typeRegistryFqns() +
        model.manifestValueObjectTypeLookup() +
        model.strongIdTypeLookup()

private fun CanonicalModel.manifestValueObjectTypeLookup(): Map<String, String> =
    valueObjects
        .groupBy { it.name }
        .filterValues { matches -> matches.size == 1 }
        .mapValues { (_, matches) ->
            val valueObject = matches.single()
            "${valueObject.packageName}.${valueObject.name}"
        }

private fun CanonicalModel.strongIdTypeLookup(): Map<String, String> =
    strongIds
        .groupBy { it.typeName }
        .filterValues { matches -> matches.size == 1 }
        .mapValues { (_, matches) ->
            val strongId = matches.single()
            "${strongId.packageName}.${strongId.typeName}"
        }

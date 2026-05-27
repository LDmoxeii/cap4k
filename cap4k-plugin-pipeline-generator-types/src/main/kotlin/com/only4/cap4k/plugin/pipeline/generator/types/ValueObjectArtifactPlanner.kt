package com.only4.cap4k.plugin.pipeline.generator.types

import com.only4.cap4k.plugin.pipeline.api.ArtifactLayoutResolver
import com.only4.cap4k.plugin.pipeline.api.ArtifactOutputKind
import com.only4.cap4k.plugin.pipeline.api.ArtifactPlanItem
import com.only4.cap4k.plugin.pipeline.api.CanonicalModel
import com.only4.cap4k.plugin.pipeline.api.FieldModel
import com.only4.cap4k.plugin.pipeline.api.GeneratorProvider
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig
import com.only4.cap4k.plugin.pipeline.api.ValueObjectModel
import com.only4.cap4k.plugin.pipeline.api.ValueObjectStorage
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
        val typeRegistry = config.typeRegistryFqns() + model.manifestValueObjectTypeLookup()

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
    val scope: String,
    val aggregate: String?,
    val storage: String,
    val imports: List<String>,
    val fields: List<ValueObjectFieldRenderModel>,
) {
    fun toContextMap(): Map<String, Any?> = mapOf(
        "packageName" to packageName,
        "typeName" to typeName,
        "name" to name,
        "description" to description,
        "scope" to scope,
        "aggregate" to aggregate,
        "storage" to storage,
        "imports" to imports,
        "fields" to fields.map { it.toContextMap() },
        "planner" to "ValueObjectArtifactPlanner",
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
            scope = valueObject.scope.name,
            aggregate = valueObject.aggregate,
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

private fun CanonicalModel.manifestValueObjectTypeLookup(): Map<String, String> =
    valueObjects
        .groupBy { it.name }
        .filterValues { matches -> matches.size == 1 }
        .mapValues { (_, matches) ->
            val valueObject = matches.single()
            "${valueObject.packageName}.${valueObject.name}"
        }

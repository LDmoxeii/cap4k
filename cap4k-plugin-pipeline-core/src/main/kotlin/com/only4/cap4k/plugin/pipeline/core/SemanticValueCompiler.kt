package com.only4.cap4k.plugin.pipeline.core

import com.only4.cap4k.plugin.pipeline.api.CanonicalTypeIdentity
import com.only4.cap4k.plugin.pipeline.api.CanonicalTypeKind
import com.only4.cap4k.plugin.pipeline.api.SemanticBuiltinType
import com.only4.cap4k.plugin.pipeline.api.SemanticBuiltinTypeRef
import com.only4.cap4k.plugin.pipeline.api.SemanticDefaultExpression
import com.only4.cap4k.plugin.pipeline.api.SemanticFieldSnapshot
import com.only4.cap4k.plugin.pipeline.api.SemanticListTypeRef
import com.only4.cap4k.plugin.pipeline.api.SemanticMapTypeRef
import com.only4.cap4k.plugin.pipeline.api.SemanticNamedTypeRef
import com.only4.cap4k.plugin.pipeline.api.SemanticSetTypeRef
import com.only4.cap4k.plugin.pipeline.api.SemanticTypeRef
import com.only4.cap4k.plugin.pipeline.api.SemanticValueDefinition
import com.only4.cap4k.plugin.pipeline.api.SemanticValueEnvelope
import com.only4.cap4k.plugin.pipeline.api.SemanticValueField
import com.only4.cap4k.plugin.pipeline.api.SemanticValueRole
import java.util.Locale

/**
 * The only parser for manifest-authored semantic type expressions.
 *
 * Generators receive [SemanticTypeRef] and must never call this parser.
 */
object SemanticTypeExpressionParser {
    fun parse(
        expression: String,
        fieldPath: String,
    ): ParsedSemanticType {
        val source = expression.trim()
        require(source.isNotEmpty()) {
            "semantic field $fieldPath must declare a non-blank type expression"
        }
        return try {
            Parser(source).parse()
        } catch (error: IllegalArgumentException) {
            throw IllegalArgumentException(
                "invalid semantic type at $fieldPath: $expression (${error.message})",
                error,
            )
        }
    }

    private class Parser(
        private val source: String,
    ) {
        private var index: Int = 0

        fun parse(): ParsedSemanticType {
            val type = parseType()
            skipWhitespace()
            require(index == source.length) { "unexpected token '${source.substring(index)}'" }
            return type
        }

        private fun parseType(): ParsedSemanticType {
            skipWhitespace()
            val identifier = parseIdentifier()
            skipWhitespace()
            val arguments = if (peek() == '<') parseArguments() else emptyList()
            skipWhitespace()
            val nullable = if (peek() == '?') {
                index++
                true
            } else {
                false
            }
            skipWhitespace()
            require(peek() != '?') { "nullability marker may appear only once per type node" }

            val rawName = identifier.substringAfterLast('.')
            if (identifier != rawName && rawName in canonicalConstructors) {
                throw IllegalArgumentException(
                    "qualified container constructor is unsupported: $identifier; use $rawName",
                )
            }
            return when (identifier) {
                "List" -> ParsedSemanticType.ListType(requireArity(identifier, arguments, 1).single(), nullable)
                "Set" -> ParsedSemanticType.SetType(requireArity(identifier, arguments, 1).single(), nullable)
                "Map" -> {
                    val checked = requireArity(identifier, arguments, 2)
                    ParsedSemanticType.MapType(checked[0], checked[1], nullable)
                }
                in unsupportedConstructors -> throw IllegalArgumentException("unsupported type constructor: $identifier")
                else -> {
                    require(arguments.isEmpty()) { "arbitrary generic constructor is unsupported: $identifier" }
                    ParsedSemanticType.Named(identifier, nullable)
                }
            }
        }

        private fun parseArguments(): List<ParsedSemanticType> {
            require(peek() == '<') { "expected '<'" }
            index++
            skipWhitespace()
            require(peek() != '>') { "generic type arguments must not be empty" }
            val arguments = mutableListOf<ParsedSemanticType>()
            while (true) {
                require(peek() != '*' && !source.startsWith("in ", index) && !source.startsWith("out ", index)) {
                    "variance and star projections are unsupported"
                }
                arguments += parseType()
                skipWhitespace()
                when (peek()) {
                    ',' -> {
                        index++
                        skipWhitespace()
                    }
                    '>' -> {
                        index++
                        return arguments
                    }
                    else -> throw IllegalArgumentException("expected ',' or '>'")
                }
            }
        }

        private fun parseIdentifier(): String {
            val start = index
            while (index < source.length) {
                val character = source[index]
                if (character.isLetterOrDigit() || character == '_' || character == '.') {
                    index++
                } else {
                    break
                }
            }
            require(index > start) { "expected type name" }
            val identifier = source.substring(start, index)
            require(identifier.split('.').all { part -> identifierPattern.matches(part) }) {
                "invalid type name: $identifier"
            }
            return identifier
        }

        private fun requireArity(
            name: String,
            arguments: List<ParsedSemanticType>,
            expected: Int,
        ): List<ParsedSemanticType> {
            require(arguments.size == expected) {
                "$name requires exactly $expected type argument${if (expected == 1) "" else "s"}"
            }
            return arguments
        }

        private fun skipWhitespace() {
            while (index < source.length && source[index].isWhitespace()) {
                index++
            }
        }

        private fun peek(): Char? = source.getOrNull(index)
    }

    private val identifierPattern = Regex("[A-Za-z_][A-Za-z0-9_]*")
    private val canonicalConstructors = setOf("List", "Map", "Set")
    private val unsupportedConstructors = setOf(
        "Array",
        "Collection",
        "Iterable",
        "MutableCollection",
        "MutableIterable",
        "MutableList",
        "MutableMap",
        "MutableSet",
        "Pair",
        "Sequence",
        "Triple",
    )
}

sealed interface ParsedSemanticType {
    val nullable: Boolean

    data class Named(
        val token: String,
        override val nullable: Boolean,
    ) : ParsedSemanticType

    data class ListType(
        val elementType: ParsedSemanticType,
        override val nullable: Boolean,
    ) : ParsedSemanticType

    data class SetType(
        val elementType: ParsedSemanticType,
        override val nullable: Boolean,
    ) : ParsedSemanticType

    data class MapType(
        val keyType: ParsedSemanticType,
        val valueType: ParsedSemanticType,
        override val nullable: Boolean,
    ) : ParsedSemanticType
}

class CanonicalTypeCatalog(
    identities: Iterable<CanonicalTypeIdentity> = emptyList(),
    aliases: Map<String, CanonicalTypeIdentity> = emptyMap(),
    sourceTypeExpressions: Iterable<String> = emptyList(),
) {
    private val identitiesByFqn: Map<String, CanonicalTypeIdentity>
    private val identitiesBySimpleName: Map<String, List<CanonicalTypeIdentity>>
    private val identitiesByAlias: Map<String, CanonicalTypeIdentity>

    init {
        val canonicalDeclarations = identities.toList()
        val duplicates = canonicalDeclarations.groupBy { it.fqn }.filterValues { values -> values.distinct().size > 1 }
        require(duplicates.isEmpty()) {
            "duplicate canonical type identity: ${duplicates.keys.first()}"
        }
        val canonicalFqns = canonicalDeclarations.mapTo(mutableSetOf()) { it.fqn }
        val declarations = canonicalDeclarations + sourceTypeExpressions
            .flatMap(::explicitExternalIdentities)
            .filterNot { it.fqn in canonicalFqns }
        val normalized = declarations.distinct()
        identitiesByFqn = normalized.associateBy { it.fqn }
        identitiesBySimpleName = normalized.groupBy { it.simpleName }
        identitiesByAlias = aliases
    }

    fun plus(
        identities: Iterable<CanonicalTypeIdentity>,
        aliases: Map<String, CanonicalTypeIdentity> = emptyMap(),
    ): CanonicalTypeCatalog = CanonicalTypeCatalog(
        identities = identitiesByFqn.values + identities,
        aliases = identitiesByAlias + aliases,
    )

    fun compile(
        parsed: ParsedSemanticType,
        fieldPath: String,
        originalExpression: String,
        ownerPackageName: String? = null,
        aggregateContext: List<String> = emptyList(),
    ): SemanticTypeRef = when (parsed) {
        is ParsedSemanticType.Named -> builtin(parsed.token, parsed.nullable)
            ?: SemanticNamedTypeRef(
                symbol = resolveNamed(
                    token = parsed.token,
                    fieldPath = fieldPath,
                    originalExpression = originalExpression,
                    ownerPackageName = ownerPackageName,
                    aggregateContext = aggregateContext,
                ),
                nullable = parsed.nullable,
            )
        is ParsedSemanticType.ListType -> SemanticListTypeRef(
            elementType = compile(parsed.elementType, fieldPath, originalExpression, ownerPackageName, aggregateContext),
            nullable = parsed.nullable,
        )
        is ParsedSemanticType.SetType -> SemanticSetTypeRef(
            elementType = compile(parsed.elementType, fieldPath, originalExpression, ownerPackageName, aggregateContext),
            nullable = parsed.nullable,
        )
        is ParsedSemanticType.MapType -> SemanticMapTypeRef(
            keyType = compile(parsed.keyType, fieldPath, originalExpression, ownerPackageName, aggregateContext),
            valueType = compile(parsed.valueType, fieldPath, originalExpression, ownerPackageName, aggregateContext),
            nullable = parsed.nullable,
        )
    }

    fun resolveExpression(
        expression: String,
        fieldPath: String,
        ownerPackageName: String? = null,
        aggregateContext: List<String> = emptyList(),
    ): SemanticTypeRef = compile(
        parsed = SemanticTypeExpressionParser.parse(expression, fieldPath),
        fieldPath = fieldPath,
        originalExpression = expression,
        ownerPackageName = ownerPackageName,
        aggregateContext = aggregateContext,
    )

    private fun resolveNamed(
        token: String,
        fieldPath: String,
        originalExpression: String,
        ownerPackageName: String?,
        aggregateContext: List<String>,
    ): CanonicalTypeIdentity {
        identitiesByAlias[token]?.let { return it }
        if ('.' in token) {
            return identitiesByFqn[token] ?: externalIdentity(token)
        }

        val candidates = identitiesBySimpleName[token].orEmpty()
        val aggregateCandidates = candidates.filter { identity -> identity.ownerAggregateName in aggregateContext }
        selectUnique(aggregateCandidates)?.let { return it }

        val packageCandidates = ownerPackageName
            ?.let { packageName -> candidates.filter { identity -> identity.packageName == packageName } }
            .orEmpty()
        selectUnique(packageCandidates)?.let { return it }

        val sharedCandidates = candidates.filter { it.ownerAggregateName == null }
        selectUnique(sharedCandidates)?.let { return it }
        selectUnique(candidates)?.let { return it }

        val reason = if (candidates.isEmpty()) {
            "unknown short type: $token"
        } else {
            "ambiguous short type: $token matches ${candidates.joinToString { it.fqn }}"
        }
        throw IllegalArgumentException(
            "failed to resolve semantic type for field $fieldPath ($originalExpression): $reason",
        )
    }

    private fun selectUnique(candidates: List<CanonicalTypeIdentity>): CanonicalTypeIdentity? =
        candidates.distinctBy { it.fqn }.singleOrNull()

    private fun externalIdentity(fqn: String): CanonicalTypeIdentity {
        val normalized = fqn.trim('.')
        require('.' in normalized) { "external type must use an FQN: $fqn" }
        return CanonicalTypeIdentity(
            packageName = normalized.substringBeforeLast('.'),
            typePath = listOf(normalized.substringAfterLast('.')),
            kind = CanonicalTypeKind.EXTERNAL,
        )
    }

    private fun builtin(token: String, nullable: Boolean): SemanticBuiltinTypeRef? {
        val normalized = token.removePrefix("kotlin.").uppercase(Locale.ROOT)
        val kind = runCatching { SemanticBuiltinType.valueOf(normalized) }.getOrNull() ?: return null
        return SemanticBuiltinTypeRef(kind = kind, nullable = nullable)
    }

    private fun explicitExternalIdentities(expression: String): List<CanonicalTypeIdentity> {
        val parsed = runCatching {
            SemanticTypeExpressionParser.parse(expression, "source type evidence")
        }.getOrNull() ?: return emptyList()
        return parsed.namedTokens()
            .filter { token -> '.' in token }
            .map(::externalIdentity)
    }

    private fun ParsedSemanticType.namedTokens(): List<String> = when (this) {
        is ParsedSemanticType.Named -> listOf(token)
        is ParsedSemanticType.ListType -> elementType.namedTokens()
        is ParsedSemanticType.SetType -> elementType.namedTokens()
        is ParsedSemanticType.MapType -> keyType.namedTokens() + valueType.namedTokens()
    }
}

object SemanticDefaultCompiler {
    fun compile(
        sourceExpression: String?,
        type: SemanticTypeRef,
        fieldPath: String,
    ): SemanticDefaultExpression? {
        if (sourceExpression == null) return null
        val value = sourceExpression.trim()
        if (type !is SemanticBuiltinTypeRef || type.kind != SemanticBuiltinType.STRING) {
            require(value.isNotBlank()) {
                "invalid default value for semantic field $fieldPath: blank defaults are only supported for String"
            }
        }
        val kotlinExpression = when {
            value == "null" -> {
                require(type.nullable) {
                    "invalid default value for semantic field $fieldPath: null requires a nullable type"
                }
                value
            }
            type is SemanticBuiltinTypeRef -> compileBuiltin(
                value = if (type.kind == SemanticBuiltinType.STRING &&
                    !(value.startsWith('"') && value.endsWith('"'))
                ) {
                    sourceExpression
                } else {
                    value
                },
                kind = type.kind,
                fieldPath = fieldPath,
            )
            type is SemanticListTypeRef -> requireEmptyCollection(value, "emptyList()", fieldPath)
            type is SemanticSetTypeRef -> requireEmptyCollection(value, "emptySet()", fieldPath)
            type is SemanticMapTypeRef -> requireEmptyCollection(value, "emptyMap()", fieldPath)
            type is SemanticNamedTypeRef -> compileNamedConstant(value, type.symbol, fieldPath)
            else -> error("unsupported semantic default type: $type")
        }
        return SemanticDefaultExpression(
            kotlinExpression = kotlinExpression,
            sourceExpression = requireNotNull(sourceExpression),
        )
    }

    private fun compileBuiltin(
        value: String,
        kind: SemanticBuiltinType,
        fieldPath: String,
    ): String = when (kind) {
        SemanticBuiltinType.STRING -> normalizeString(value, fieldPath)
        SemanticBuiltinType.INT -> value.also {
            require(intLiteralPattern.matches(it) && it.toIntOrNull() != null) {
                "invalid default value for semantic field $fieldPath: $value is not an Int literal"
            }
        }
        SemanticBuiltinType.LONG -> value.also {
            require(longLiteralPattern.matches(it) && it.removeSuffix("l").removeSuffix("L").toLongOrNull() != null) {
                "invalid default value for semantic field $fieldPath: $value is not a Long literal"
            }
        }.let { if (it.endsWith("L")) it else it.removeSuffix("l") + "L" }
        SemanticBuiltinType.DOUBLE -> value.also {
            require(doubleLiteralPattern.matches(it)) {
                "invalid default value for semantic field $fieldPath: $value is not a Double literal"
            }
        }
        SemanticBuiltinType.FLOAT -> value.also {
            require(floatLiteralPattern.matches(it)) {
                "invalid default value for semantic field $fieldPath: $value is not a Float literal"
            }
        }
        SemanticBuiltinType.BOOLEAN -> value.also {
            require(it == "true" || it == "false") {
                "invalid default value for semantic field $fieldPath: Boolean defaults must be true or false"
            }
        }
        else -> throw IllegalArgumentException(
            "invalid default value for semantic field $fieldPath: defaults for ${kind.name} are unsupported",
        )
    }

    private fun requireEmptyCollection(value: String, supported: String, fieldPath: String): String {
        require(value == supported) {
            "invalid default value for semantic field $fieldPath: expected $supported but found $value"
        }
        return value
    }

    private fun compileNamedConstant(
        value: String,
        symbol: CanonicalTypeIdentity,
        fieldPath: String,
    ): String {
        require(constantExpressionPattern.matches(value)) {
            "invalid default value for semantic field $fieldPath: unsupported expression $value"
        }
        val owner = value.substringBeforeLast('.')
        require(owner == symbol.fqn || owner == symbol.simpleName) {
            "invalid default value for semantic field $fieldPath: $value does not belong to ${symbol.fqn}"
        }
        return "${symbol.fqn}.${value.substringAfterLast('.')}"
    }

    private fun normalizeString(value: String, fieldPath: String): String {
        if (value.length >= 2 && value.first() == '"' && value.last() == '"') {
            require(isValidQuotedStringLiteral(value)) {
                "invalid default value for semantic field $fieldPath: invalid String literal $value"
            }
            return value
        }
        return buildString {
            append('"')
            value.forEach { character ->
                when (character) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    '$' -> append("\\$")
                    else -> append(character)
                }
            }
            append('"')
        }
    }

    private fun isValidQuotedStringLiteral(value: String): Boolean {
        var index = 1
        while (index < value.lastIndex) {
            val character = value[index]
            if (character == '\n' || character == '\r' || character == '"' || character == '$') {
                return false
            }
            if (character != '\\') {
                index++
                continue
            }
            if (index + 1 >= value.lastIndex) return false
            val escape = value[index + 1]
            if (escape == 'u') {
                if (index + 5 >= value.lastIndex) return false
                if (!value.substring(index + 2, index + 6).all { digit -> digit.isDigit() || digit.lowercaseChar() in 'a'..'f' }) {
                    return false
                }
                index += 6
                continue
            }
            if (escape !in supportedStringEscapes) return false
            index += 2
        }
        return true
    }

    private val intLiteralPattern = Regex("-?\\d+")
    private val longLiteralPattern = Regex("-?\\d+[lL]?")
    private val doubleLiteralPattern = Regex("-?(?:(?:\\d+\\.\\d*|\\.\\d+)(?:[eE][+-]?\\d+)?|\\d+[eE][+-]?\\d+)")
    private val floatLiteralPattern = Regex("-?(?:\\d+(?:\\.\\d*)?|\\.\\d+)(?:[eE][+-]?\\d+)?[fF]")
    private val constantExpressionPattern = Regex("(?:[A-Za-z_][A-Za-z0-9_]*\\.)+[A-Z][A-Za-z0-9_]*")
    private val supportedStringEscapes = setOf('\\', '"', '\'', 'b', 'n', 'r', 't', '$')
}

class SemanticValueCompiler(
    private val catalog: CanonicalTypeCatalog,
) {
    fun compile(
        identity: CanonicalTypeIdentity,
        role: SemanticValueRole,
        fields: List<SemanticFieldSnapshot>,
        aggregateContext: List<String> = emptyList(),
        allowPageEnvelope: Boolean = false,
    ): SemanticValueDefinition {
        val root = PathNode(name = "<root>")
        fields.forEach { field -> insert(root, field) }

        val pageNode = root.children["page"]
        val pageDeclaration = pageNode?.declaration
        if (pageDeclaration != null && isPageDataExpression(pageDeclaration.typeExpression)) {
            require(allowPageEnvelope) {
                "PageData envelope is not enabled for ${identity.fqn} with semantic role $role"
            }
            require(root.children.size == 1) {
                "PageData response ${identity.fqn} may only declare the root field page"
            }
            return compilePageEnvelope(identity, role, pageNode, aggregateContext)
        }
        fields.firstOrNull { isPageDataExpression(it.typeExpression) }?.let { field ->
            throw IllegalArgumentException(
                "PageData envelope in ${identity.fqn} is only supported for root field page, found ${field.name}",
            )
        }

        return compileDefinition(
            identity = identity,
            namespaceIdentity = identity,
            role = role,
            node = root,
            aggregateContext = aggregateContext,
        )
    }

    private fun compilePageEnvelope(
        identity: CanonicalTypeIdentity,
        role: SemanticValueRole,
        pageNode: PathNode,
        aggregateContext: List<String>,
    ): SemanticValueDefinition {
        val declaration = requireNotNull(pageNode.declaration)
        val itemTypeName = parsePageItemType(declaration.typeExpression, declaration.sourcePath)
        require(pageNode.children.keys == setOf("list")) {
            "PageData field page in ${identity.fqn} must declare nested item fields only under list[]"
        }
        val listNode = requireNotNull(pageNode.children["list"])
        require(listNode.collectionHint && listNode.children.isNotEmpty() && listNode.declaration == null) {
            "PageData field page in ${identity.fqn} must declare nested item fields only under list[]"
        }
        val itemIdentity = nestedIdentity(identity, itemTypeName)
        val localCatalog = catalog.plus(listOf(itemIdentity) + collectNestedIdentities(identity, listNode))
        val itemDefinition = SemanticValueCompiler(localCatalog).compileDefinition(
            identity = itemIdentity,
            namespaceIdentity = identity,
            role = role,
            node = listNode,
            aggregateContext = aggregateContext,
        )
        return SemanticValueDefinition(
            identity = identity,
            role = role,
            envelope = SemanticValueEnvelope.Page(itemDefinition),
        )
    }

    private fun compileDefinition(
        identity: CanonicalTypeIdentity,
        namespaceIdentity: CanonicalTypeIdentity,
        role: SemanticValueRole,
        node: PathNode,
        aggregateContext: List<String>,
    ): SemanticValueDefinition {
        val nestedIdentities = node.children.values
            .filter { it.children.isNotEmpty() }
            .associateWith { child -> nestedIdentity(namespaceIdentity, resolveNestedTypeName(child)) }
        val flattenedNestedIdentities = nestedIdentities.values + nestedIdentities.flatMap { (child, _) ->
            collectNestedIdentities(namespaceIdentity, child)
        }
        flattenedNestedIdentities
            .groupBy { it.fqn }
            .entries
            .firstOrNull { (_, declarations) -> declarations.size > 1 }
            ?.let { (fqn, _) ->
                throw IllegalArgumentException(
                    "semantic value ${namespaceIdentity.fqn} has colliding flattened nested identity $fqn",
                )
            }
        val localCatalog = catalog.plus(flattenedNestedIdentities)

        val nestedDefinitions = nestedIdentities.map { (child, childIdentity) ->
            SemanticValueCompiler(localCatalog).compileDefinition(
                identity = childIdentity,
                namespaceIdentity = namespaceIdentity,
                role = role,
                node = child,
                aggregateContext = aggregateContext,
            )
        }
        val nestedByNode = nestedIdentities
        val compiledFields = node.children.values.map { child ->
            val fieldPath = child.declaration?.sourcePath ?: child.path
            val type = if (child.children.isNotEmpty()) {
                val nestedIdentity = requireNotNull(nestedByNode[child])
                compileStructuralFieldType(child, nestedIdentity, localCatalog, aggregateContext, fieldPath)
            } else {
                val declaration = requireNotNull(child.declaration) {
                    "semantic field ${child.path} must declare a type"
                }
                localCatalog.resolveExpression(
                    expression = declaration.typeExpression,
                    fieldPath = declaration.sourcePath,
                    ownerPackageName = identity.packageName,
                    aggregateContext = aggregateContext,
                )
            }
            SemanticValueField(
                name = child.name,
                type = type,
                defaultValue = SemanticDefaultCompiler.compile(child.declaration?.defaultValue, type, fieldPath),
                sourcePath = fieldPath,
            )
        }
        return SemanticValueDefinition(
            identity = identity,
            role = role,
            fields = compiledFields,
            nestedDefinitions = nestedDefinitions,
        )
    }

    private fun compileStructuralFieldType(
        node: PathNode,
        nestedIdentity: CanonicalTypeIdentity,
        localCatalog: CanonicalTypeCatalog,
        aggregateContext: List<String>,
        fieldPath: String,
    ): SemanticTypeRef {
        val declaration = node.declaration
        if (declaration == null) {
            val nested = SemanticNamedTypeRef(nestedIdentity)
            return if (node.collectionHint) SemanticListTypeRef(nested) else nested
        }
        val type = localCatalog.resolveExpression(
            expression = declaration.typeExpression,
            fieldPath = declaration.sourcePath,
            ownerPackageName = nestedIdentity.packageName,
            aggregateContext = aggregateContext,
        )
        val referencedIdentity = when (type) {
            is SemanticNamedTypeRef -> type.symbol
            is SemanticListTypeRef -> (type.elementType as? SemanticNamedTypeRef)?.symbol
            else -> null
        }
        require(referencedIdentity?.fqn == nestedIdentity.fqn) {
            "semantic field $fieldPath declares ${declaration.typeExpression}, but nested path requires ${nestedIdentity.fqn}"
        }
        require(node.collectionHint == (type is SemanticListTypeRef)) {
            "semantic field $fieldPath collection shape conflicts with nested [] path"
        }
        return type
    }

    private fun collectNestedIdentities(
        namespaceIdentity: CanonicalTypeIdentity,
        node: PathNode,
    ): List<CanonicalTypeIdentity> = buildList {
        node.children.values.filter { it.children.isNotEmpty() }.forEach { child ->
            val childIdentity = nestedIdentity(namespaceIdentity, resolveNestedTypeName(child))
            add(childIdentity)
            addAll(collectNestedIdentities(namespaceIdentity, child))
        }
    }

    private fun resolveNestedTypeName(node: PathNode): String {
        val declaration = node.declaration
        if (declaration != null) {
            val parsed = SemanticTypeExpressionParser.parse(declaration.typeExpression, declaration.sourcePath)
            val named = when (parsed) {
                is ParsedSemanticType.Named -> parsed
                is ParsedSemanticType.ListType -> parsed.elementType as? ParsedSemanticType.Named
                else -> null
            }
            require(named != null) {
                "semantic field ${declaration.sourcePath} nested paths require a named value type or List of a named value type"
            }
            return named.token.substringAfterLast('.')
        }
        val base = node.name
            .split(identifierBoundary)
            .filter { it.isNotBlank() }
            .joinToString("") { part -> part.replaceFirstChar { it.titlecase(Locale.ROOT) } }
        if (!node.collectionHint) return base

        val singular = base.removeSuffix("s")
        return if (singular.endsWith("Item")) singular else "${singular}Item"
    }

    private fun nestedIdentity(
        owner: CanonicalTypeIdentity,
        name: String,
    ): CanonicalTypeIdentity = CanonicalTypeIdentity(
        packageName = owner.packageName,
        typePath = owner.typePath + name,
        kind = CanonicalTypeKind.NESTED_VALUE,
        ownerAggregateName = owner.ownerAggregateName,
    )

    private fun insert(root: PathNode, field: SemanticFieldSnapshot) {
        val segments = parsePath(field.name, field.sourcePath)
        var current = root
        val traversed = mutableListOf<String>()
        segments.forEachIndexed { index, segment ->
            traversed += segment.name + if (segment.collection) "[]" else ""
            val child = current.children.getOrPut(segment.name) {
                PathNode(name = segment.name, path = traversed.joinToString("."))
            }
            if (segment.collection) child.collectionHint = true
            if (index == segments.lastIndex) {
                require(child.declaration == null) {
                    "duplicate semantic field path: ${field.name}"
                }
                child.declaration = field
            }
            current = child
        }
    }

    private fun parsePath(path: String, sourcePath: String): List<PathSegment> {
        val normalized = path.trim()
        require(normalized.isNotEmpty()) { "semantic field $sourcePath has a blank path" }
        return normalized.split('.').map { rawSegment ->
            val match = pathSegmentPattern.matchEntire(rawSegment)
                ?: throw IllegalArgumentException("invalid semantic field path at $sourcePath: $path")
            PathSegment(
                name = match.groupValues[1],
                collection = match.groupValues[2].isNotEmpty(),
            )
        }
    }

    private fun isPageDataExpression(expression: String): Boolean =
        pageDataPattern.matches(expression.trim())

    private fun parsePageItemType(expression: String, fieldPath: String): String {
        val match = pageDataPattern.matchEntire(expression.trim())
            ?: throw IllegalArgumentException("invalid PageData expression at $fieldPath: $expression")
        val itemType = match.groupValues[1].trim()
        require(simpleNamedTypePattern.matches(itemType)) {
            "PageData item type at $fieldPath must be a non-null named type: $itemType"
        }
        return itemType.substringAfterLast('.')
    }

    private data class PathSegment(
        val name: String,
        val collection: Boolean,
    )

    private data class PathNode(
        val name: String,
        val path: String = "",
        var collectionHint: Boolean = false,
        var declaration: SemanticFieldSnapshot? = null,
        val children: LinkedHashMap<String, PathNode> = linkedMapOf(),
    )

    private val pathSegmentPattern = Regex("([A-Za-z_][A-Za-z0-9_]*)(\\[\\])?")
    private val simpleNamedTypePattern = Regex("(?:[A-Za-z_][A-Za-z0-9_]*\\.)*[A-Za-z_][A-Za-z0-9_]*")
    private val pageDataPattern = Regex("(?:com\\.only4\\.cap4k\\.ddd\\.core\\.share\\.)?PageData\\s*<\\s*([^<>]+)\\s*>")
    private val identifierBoundary = Regex("(?<=[a-z0-9])(?=[A-Z])|[^A-Za-z0-9]+")
}

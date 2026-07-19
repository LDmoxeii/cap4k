package com.only4.cap4k.plugin.pipeline.generator.types

import com.only4.cap4k.plugin.pipeline.generator.common.types.EXPLICIT_FQCN_SOURCE
import com.only4.cap4k.plugin.pipeline.generator.common.types.PROJECT_TYPE_REGISTRY_SOURCE
import com.only4.cap4k.plugin.pipeline.generator.common.types.TypeSymbolIdentity
import com.only4.cap4k.plugin.pipeline.generator.common.types.TypeSymbolRegistry
import com.only4.cap4k.plugin.pipeline.generator.common.types.TypeSymbolSelector

internal data class ParsedValueObjectType(
    val tokenText: String,
    val nullable: Boolean = false,
    val arguments: List<ParsedValueObjectType> = emptyList(),
) {
    fun tokenTexts(): Set<String> = buildSet {
        add(tokenText)
        arguments.forEach { argument -> addAll(argument.tokenTexts()) }
    }
}

internal data class ResolvedValueObjectType(
    val renderedType: String,
    val imports: Set<String> = emptySet(),
) {
    fun withNullability(nullable: Boolean): ResolvedValueObjectType =
        if (nullable && !renderedType.endsWith("?")) {
            copy(renderedType = "$renderedType?")
        } else {
            this
        }
}

internal object ValueObjectTypeResolver {
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

    fun resolve(
        type: ParsedValueObjectType,
        symbolRegistry: TypeSymbolRegistry,
        aggregateContext: List<String>,
    ): ResolvedValueObjectType {
        val registry = TypeSymbolRegistry(symbolRegistry.allSymbols()).also { merged ->
            collectExplicitSymbols(type).forEach(merged::register)
        }
        return render(type, registry, aggregateContext)
    }

    private fun collectExplicitSymbols(type: ParsedValueObjectType): List<TypeSymbolIdentity> {
        val own = if (type.tokenText.contains('.')) {
            listOf(
                TypeSymbolIdentity(
                    packageName = type.tokenText.substringBeforeLast('.', missingDelimiterValue = ""),
                    typeName = type.tokenText.substringAfterLast('.'),
                    source = EXPLICIT_FQCN_SOURCE,
                )
            )
        } else {
            emptyList()
        }
        return own + type.arguments.flatMap(::collectExplicitSymbols)
    }

    private fun render(
        type: ParsedValueObjectType,
        symbolRegistry: TypeSymbolRegistry,
        aggregateContext: List<String>,
    ): ResolvedValueObjectType {
        val resolvedArguments = type.arguments.map { render(it, symbolRegistry, aggregateContext) }
        val base = resolveBase(type.tokenText, symbolRegistry, aggregateContext)
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
        return ResolvedValueObjectType(
            renderedType = rendered,
            imports = base.imports + resolvedArguments.flatMap { it.imports },
        )
    }

    private fun resolveBase(
        tokenText: String,
        symbolRegistry: TypeSymbolRegistry,
        aggregateContext: List<String>,
    ): ResolvedValueObjectType {
        if (tokenText in builtInTypeNames) {
            return ResolvedValueObjectType(renderedType = tokenText)
        }

        if (tokenText.contains('.')) {
            val simpleName = tokenText.substringAfterLast('.')
            val conflictingCandidates = symbolRegistry.findBySimpleName(simpleName)
                .filterNot { it.source == PROJECT_TYPE_REGISTRY_SOURCE }
                .filterNot { it.fqcn == tokenText }
            return if (conflictingCandidates.isEmpty()) {
                ResolvedValueObjectType(
                    renderedType = simpleName,
                    imports = setOf(tokenText),
                )
            } else {
                ResolvedValueObjectType(renderedType = tokenText)
            }
        }

        val selectedCandidates = TypeSymbolSelector.selectShortNameCandidates(
            candidates = symbolRegistry.findBySimpleName(tokenText),
            aggregateContext = aggregateContext,
        )
        return when (selectedCandidates.size) {
            0 -> throw UnknownValueObjectFieldTypeFailure(tokenText)
            1 -> ResolvedValueObjectType(
                renderedType = tokenText,
                imports = setOf(selectedCandidates.single().fqcn),
            )
            else -> throw AmbiguousValueObjectFieldTypeFailure(
                shortType = tokenText,
                candidates = selectedCandidates.map { it.fqcn },
            )
        }
    }
}

internal sealed class ValueObjectFieldTypeResolutionFailure(
    message: String,
) : IllegalArgumentException(message)

internal class UnknownValueObjectFieldTypeFailure(
    val shortType: String,
) : ValueObjectFieldTypeResolutionFailure(
    "unknown value object field type: $shortType; use a fully qualified name, declare a cap4k enum/value-object/Strong ID manifest type, or register an external type in types.registryFile"
)

internal class AmbiguousValueObjectFieldTypeFailure(
    val shortType: String,
    val candidates: List<String>,
) : ValueObjectFieldTypeResolutionFailure(
    "ambiguous value object field type: $shortType -> ${candidates.joinToString()}"
)

internal object ValueObjectTypeParser {
    fun parse(type: String): ParsedValueObjectType {
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

        fun parseType(): ParsedValueObjectType {
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

            return ParsedValueObjectType(
                tokenText = tokenText,
                nullable = nullable,
                arguments = arguments,
            )
        }

        private fun parseArguments(): List<ParsedValueObjectType> {
            val arguments = mutableListOf<ParsedValueObjectType>()
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

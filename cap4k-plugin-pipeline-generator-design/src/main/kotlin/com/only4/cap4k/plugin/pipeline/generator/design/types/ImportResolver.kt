package com.only4.cap4k.plugin.pipeline.generator.design.types

import com.only4.cap4k.plugin.pipeline.generator.design.DesignImportPlan
import com.only4.cap4k.plugin.pipeline.generator.design.DesignRenderedTypeModel
import com.only4.cap4k.plugin.pipeline.generator.design.DesignResolvedTypeKind
import com.only4.cap4k.plugin.pipeline.generator.design.DesignResolvedTypeModel

internal object ImportResolver {

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

    fun plan(
        types: List<DesignResolvedTypeModel>,
        innerTypeNames: Set<String> = emptySet(),
        symbolRegistry: DesignSymbolRegistry = DesignSymbolRegistry(),
    ): DesignImportPlan {
        val registry = DesignSymbolRegistry(symbolRegistry.allSymbols()).also { merged ->
            types.flatMap(::collectExplicitSymbols).forEach(merged::register)
        }

        val rendered = types.map { render(it, registry, innerTypeNames) }

        return DesignImportPlan(
            renderedTypes = rendered.map { result ->
                DesignRenderedTypeModel(
                    renderedText = result.renderedType,
                    qualifiedFallback = result.qualifiedFallback,
                )
            },
            imports = rendered.flatMap { it.imports }.distinct().sorted(),
        )
    }

    internal fun resolve(
        type: DesignResolvedTypeModel,
        innerTypeNames: Set<String> = emptySet(),
        symbolRegistry: DesignSymbolRegistry = DesignSymbolRegistry(),
    ): ImportResolutionResult {
        val registry = DesignSymbolRegistry(symbolRegistry.allSymbols()).also { merged ->
            collectExplicitSymbols(type).forEach(merged::register)
        }
        return render(type, registry, innerTypeNames)
    }

    private fun collectExplicitSymbols(type: DesignResolvedTypeModel): List<SymbolIdentity> {
        val own = if (type.kind == DesignResolvedTypeKind.EXPLICIT_FQCN) {
            listOf(
                SymbolIdentity(
                    packageName = type.rawText.substringBeforeLast('.', missingDelimiterValue = ""),
                    typeName = type.simpleName,
                    source = "explicit-fqcn",
                ),
            )
        } else {
            emptyList()
        }

        return own + type.arguments.flatMap(::collectExplicitSymbols)
    }

    private fun render(
        type: DesignResolvedTypeModel,
        symbolRegistry: DesignSymbolRegistry,
        innerTypeNames: Set<String>,
    ): ImportResolutionResult {
        val renderedArguments = type.arguments.map { render(it, symbolRegistry, innerTypeNames) }
        val base = resolveBase(type, symbolRegistry, innerTypeNames)
        val withArguments = if (renderedArguments.isEmpty()) {
            base.renderedType
        } else {
            base.renderedType + renderedArguments.joinToString(
                separator = ", ",
                prefix = "<",
                postfix = ">",
            ) { it.renderedType }
        }
        val renderedText = if (type.nullable && !withArguments.endsWith("?")) {
            "$withArguments?"
        } else {
            withArguments
        }

        return ImportResolutionResult(
            renderedType = renderedText,
            imports = buildSet {
                addAll(base.imports)
                renderedArguments.forEach { addAll(it.imports) }
            },
            qualifiedFallback = base.qualifiedFallback || renderedArguments.any { it.qualifiedFallback },
        )
    }

    private fun resolveBase(
        type: DesignResolvedTypeModel,
        symbolRegistry: DesignSymbolRegistry,
        innerTypeNames: Set<String>,
    ): ImportResolutionResult {
        return when (type.kind) {
            DesignResolvedTypeKind.BUILTIN,
            DesignResolvedTypeKind.INNER,
            -> ImportResolutionResult(
                renderedType = type.rawText,
                imports = emptySet(),
                qualifiedFallback = false,
            )

            DesignResolvedTypeKind.EXPLICIT_FQCN -> {
                val candidates = symbolRegistry.findBySimpleName(type.simpleName)
                val conflictingCandidates = candidates.filterNot { it.source == "project-type-registry" }
                val canImport = type.simpleName !in builtInTypeNames &&
                    type.simpleName !in innerTypeNames &&
                    conflictingCandidates.all { it.fqcn == type.rawText }

                if (canImport) {
                    ImportResolutionResult(
                        renderedType = type.simpleName,
                        imports = setOf(type.rawText),
                        qualifiedFallback = false,
                    )
                } else {
                    ImportResolutionResult(
                        renderedType = type.rawText,
                        imports = emptySet(),
                        qualifiedFallback = true,
                    )
                }
            }

            DesignResolvedTypeKind.UNRESOLVED -> {
                val candidates = symbolRegistry.findBySimpleName(type.simpleName)
                when {
                    candidates.isEmpty() -> throw IllegalArgumentException(
                        "unknown short type: ${type.rawText}",
                    )

                    candidates.size > 1 -> throw IllegalArgumentException(
                        "ambiguous short type: ${type.rawText} -> ${candidates.joinToString { it.fqcn }}",
                    )

                    else -> ImportResolutionResult(
                        renderedType = type.simpleName,
                        imports = setOf(candidates.single().fqcn),
                        qualifiedFallback = false,
                    )
                }
            }
        }
    }
}

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
        aggregateContext: List<String> = emptyList(),
    ): DesignImportPlan {
        val registry = DesignSymbolRegistry(symbolRegistry.allSymbols()).also { merged ->
            types.flatMap(::collectExplicitSymbols).forEach(merged::register)
        }

        val rendered = types.map { render(it, registry, innerTypeNames, aggregateContext) }

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
        aggregateContext: List<String> = emptyList(),
    ): ImportResolutionResult {
        val registry = DesignSymbolRegistry(symbolRegistry.allSymbols()).also { merged ->
            collectExplicitSymbols(type).forEach(merged::register)
        }
        return render(type, registry, innerTypeNames, aggregateContext)
    }

    private fun collectExplicitSymbols(type: DesignResolvedTypeModel): List<SymbolIdentity> {
        val own = if (type.kind == DesignResolvedTypeKind.EXPLICIT_FQCN) {
            listOf(
                SymbolIdentity(
                    packageName = type.rawText.substringBeforeLast('.', missingDelimiterValue = ""),
                    typeName = type.simpleName,
                    source = EXPLICIT_FQCN_SOURCE,
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
        aggregateContext: List<String>,
    ): ImportResolutionResult {
        val renderedArguments = type.arguments.map { render(it, symbolRegistry, innerTypeNames, aggregateContext) }
        val base = resolveBase(type, symbolRegistry, innerTypeNames, aggregateContext)
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
        aggregateContext: List<String>,
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
                val conflictingCandidates = candidates.filterNot { it.source == PROJECT_TYPE_REGISTRY_SOURCE }
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
                val selectedCandidates = selectShortTypeCandidates(
                    candidates = symbolRegistry.findBySimpleName(type.simpleName),
                    aggregateContext = aggregateContext,
                )

                when (selectedCandidates.size) {
                    0 -> throw UnknownShortTypeFailure(type.rawText)

                    1 -> ImportResolutionResult(
                        renderedType = type.simpleName,
                        imports = setOf(selectedCandidates.single().fqcn),
                        qualifiedFallback = false,
                    )

                    else -> throw AmbiguousShortTypeFailure(
                        shortType = type.rawText,
                        candidates = selectedCandidates.map { it.fqcn },
                    )
                }
            }
        }
    }

    private fun selectShortTypeCandidates(
        candidates: List<SymbolIdentity>,
        aggregateContext: List<String>,
    ): List<SymbolIdentity> {
        val uniqueCandidates = candidates.distinctBy { it.fqcn }
        val singleAggregate = singleAggregateContext(aggregateContext)
        if (singleAggregate != null) {
            val localManifestCandidates = uniqueCandidates.filter { candidate ->
                candidate.manifestOwned &&
                    !candidate.shared &&
                    candidate.ownerAggregateName == singleAggregate
            }
            if (localManifestCandidates.isNotEmpty()) {
                return localManifestCandidates
            }
        }
        return uniqueCandidates
    }

    private fun singleAggregateContext(aggregateContext: List<String>): String? {
        val names = aggregateContext
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
        return names.singleOrNull()
    }

    internal sealed class ShortTypeResolutionFailure(
        message: String,
    ) : IllegalArgumentException(message)

    internal class UnknownShortTypeFailure(
        val shortType: String,
    ) : ShortTypeResolutionFailure("unknown short type: $shortType")

    internal class AmbiguousShortTypeFailure(
        val shortType: String,
        val candidates: List<String>,
    ) : ShortTypeResolutionFailure("ambiguous short type: $shortType -> ${candidates.joinToString()}")
}

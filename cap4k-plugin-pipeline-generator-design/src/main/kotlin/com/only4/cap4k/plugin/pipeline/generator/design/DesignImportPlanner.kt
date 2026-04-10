package com.only4.cap4k.plugin.pipeline.generator.design

internal object DesignImportPlanner {

    fun plan(
        types: List<DesignResolvedTypeModel>,
        innerTypeNames: Set<String> = emptySet(),
    ): DesignImportPlan {
        val importCandidates = types.flatMap { flattenImportCandidates(it) }
        val importableFqcns = importCandidates
            .groupBy { it.simpleName }
            .values
            .filter { group ->
                val distinctFqcns = group.map { it.fqcn }.distinct()
                distinctFqcns.size == 1 && group.first().simpleName !in innerTypeNames
            }
            .map { it.first().fqcn }
            .toSet()

        return DesignImportPlan(
            renderedTypes = types.map { render(it, importableFqcns, innerTypeNames) },
            imports = importableFqcns.sorted(),
        )
    }

    private fun flattenImportCandidates(type: DesignResolvedTypeModel): List<ImportCandidate> {
        val own = when (type.kind) {
            DesignResolvedTypeKind.EXPLICIT_FQCN -> listOf(
                ImportCandidate(
                    fqcn = type.rawText,
                    simpleName = type.simpleName,
                ),
            )
            else -> emptyList()
        }

        return own + type.arguments.flatMap(::flattenImportCandidates)
    }

    private fun render(
        type: DesignResolvedTypeModel,
        importableFqcns: Set<String>,
        innerTypeNames: Set<String>,
    ): DesignRenderedTypeModel {
        val renderedArguments = type.arguments.map { render(it, importableFqcns, innerTypeNames) }
        val baseText = when (type.kind) {
            DesignResolvedTypeKind.BUILTIN,
            DesignResolvedTypeKind.INNER,
            DesignResolvedTypeKind.UNRESOLVED,
            -> type.rawText

            DesignResolvedTypeKind.EXPLICIT_FQCN -> when {
                type.rawText in importableFqcns && type.simpleName !in innerTypeNames -> type.simpleName
                else -> type.rawText
            }
        }

        val withArguments = if (renderedArguments.isEmpty()) {
            baseText
        } else {
            baseText + renderedArguments.joinToString(
                separator = ", ",
                prefix = "<",
                postfix = ">",
            ) { it.renderedText }
        }

        val renderedText = if (type.nullable) {
            "$withArguments?"
        } else {
            withArguments
        }

        return DesignRenderedTypeModel(
            renderedText = renderedText,
            qualifiedFallback = type.kind == DesignResolvedTypeKind.EXPLICIT_FQCN &&
                baseText == type.rawText || renderedArguments.any { it.qualifiedFallback },
        )
    }

    private data class ImportCandidate(
        val fqcn: String,
        val simpleName: String,
    )
}

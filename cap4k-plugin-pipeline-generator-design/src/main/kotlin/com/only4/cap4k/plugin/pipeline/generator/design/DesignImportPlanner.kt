package com.only4.cap4k.plugin.pipeline.generator.design

internal object DesignImportPlanner {

    private val reservedSimpleNames = setOf(
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
    ): DesignImportPlan {
        val references = types.flatMap { flattenReferences(it) }
        val importableFqcns = references
            .groupBy { it.simpleName }
            .values
            .filter { group ->
                group.all { it.fqcn != null } &&
                    group.mapNotNull { it.fqcn }.distinct().size == 1 &&
                    group.first().simpleName !in innerTypeNames &&
                    group.first().simpleName !in reservedSimpleNames
            }
            .map { it.first().fqcn!! }
            .toSet()

        return DesignImportPlan(
            renderedTypes = types.map { render(it, importableFqcns, innerTypeNames) },
            imports = importableFqcns.sorted(),
        )
    }

    private fun flattenReferences(type: DesignResolvedTypeModel): List<TypeReference> {
        val own = TypeReference(
            simpleName = type.simpleName,
            fqcn = when (type.kind) {
                DesignResolvedTypeKind.EXPLICIT_FQCN -> type.rawText
                else -> null
            },
        )

        return listOf(own) + type.arguments.flatMap(::flattenReferences)
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

    private data class TypeReference(
        val simpleName: String,
        val fqcn: String?,
    )

}

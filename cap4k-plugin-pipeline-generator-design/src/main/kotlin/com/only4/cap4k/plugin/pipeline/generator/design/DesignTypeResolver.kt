package com.only4.cap4k.plugin.pipeline.generator.design

internal object DesignTypeResolver {

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
        "Sequence",
        "Pair",
        "Triple",
        "Set",
        "Short",
        "String",
        "Unit",
    )

    fun resolve(
        type: DesignTypeModel,
        innerTypeNames: Set<String> = emptySet(),
    ): DesignResolvedTypeModel {
        val rawText = type.tokenText
        val simpleName = rawText.substringAfterLast('.')
        val kind = when {
            rawText in builtInTypeNames -> DesignResolvedTypeKind.BUILTIN
            rawText in innerTypeNames -> DesignResolvedTypeKind.INNER
            rawText.contains('.') -> DesignResolvedTypeKind.EXPLICIT_FQCN
            else -> DesignResolvedTypeKind.UNRESOLVED
        }

        return DesignResolvedTypeModel(
            kind = kind,
            rawText = rawText,
            simpleName = simpleName,
            nullable = type.nullable,
            arguments = type.arguments.map { resolve(it, innerTypeNames) },
            importCandidates = when (kind) {
                DesignResolvedTypeKind.EXPLICIT_FQCN -> setOf(rawText)
                else -> emptySet()
            },
        )
    }
}

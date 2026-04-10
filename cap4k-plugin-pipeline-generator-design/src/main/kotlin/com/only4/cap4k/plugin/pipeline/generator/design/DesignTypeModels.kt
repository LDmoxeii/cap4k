package com.only4.cap4k.plugin.pipeline.generator.design

internal data class DesignTypeModel(
    val tokenText: String,
    val nullable: Boolean = false,
    val arguments: List<DesignTypeModel> = emptyList(),
)

internal enum class DesignResolvedTypeKind {
    BUILTIN,
    INNER,
    EXPLICIT_FQCN,
    UNRESOLVED,
}

internal data class DesignResolvedTypeModel(
    val kind: DesignResolvedTypeKind,
    val rawText: String,
    val simpleName: String,
    val nullable: Boolean = false,
    val arguments: List<DesignResolvedTypeModel> = emptyList(),
    val importCandidates: Set<String> = emptySet(),
)

internal data class DesignRenderedTypeModel(
    val renderedText: String,
    val qualifiedFallback: Boolean,
)

internal data class DesignImportPlan(
    val renderedTypes: List<DesignRenderedTypeModel>,
    val imports: List<String>,
)

package com.only4.cap4k.plugin.pipeline.generator.design.types

internal data class ImportResolutionResult(
    val renderedType: String,
    val imports: Set<String>,
    val qualifiedFallback: Boolean,
)

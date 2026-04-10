package com.only4.cap4k.plugin.pipeline.generator.design

internal data class DesignTypeModel(
    val tokenText: String,
    val nullable: Boolean = false,
    val arguments: List<DesignTypeModel> = emptyList(),
)

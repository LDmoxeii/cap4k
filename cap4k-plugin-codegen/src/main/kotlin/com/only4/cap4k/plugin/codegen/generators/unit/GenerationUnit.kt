package com.only4.cap4k.plugin.codegen.generators.unit

import com.only4.cap4k.plugin.codegen.template.TemplateNode

data class GenerationUnit(
    val id: String,
    val tag: String,
    val name: String,
    val order: Int,
    val deps: List<String> = emptyList(),
    val templateNodes: List<TemplateNode>,
    val context: Map<String, Any?>,
    val exportTypes: Map<String, String> = emptyMap(),
)

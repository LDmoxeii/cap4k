package com.only4.cap4k.plugin.codegen.generators.drawingboard

data class DrawingBoardDocument(
    val tag: String,
    val generatorName: String,
    val content: String,
    val context: Map<String, Any?> = emptyMap()
)

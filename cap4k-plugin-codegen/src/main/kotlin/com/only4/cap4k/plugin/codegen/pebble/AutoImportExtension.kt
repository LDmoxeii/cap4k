package com.only4.cap4k.plugin.codegen.pebble

import io.pebbletemplates.pebble.extension.AbstractExtension
import io.pebbletemplates.pebble.extension.Function
import io.pebbletemplates.pebble.template.EvaluationContext
import io.pebbletemplates.pebble.template.PebbleTemplate

class AutoImportExtension : AbstractExtension() {
    override fun getFunctions(): Map<String, Function> = mapOf(
        "type" to TypeFunction(),
        "use" to UseFunction(),
        "imports" to ImportsFunction(),
    )
}

private class TypeFunction : Function {
    override fun getArgumentNames(): List<String> = listOf("value")

    override fun execute(
        args: Map<String, Any?>,
        self: PebbleTemplate,
        context: EvaluationContext,
        lineNumber: Int,
    ): Any {
        val raw = args["value"]?.toString().orEmpty()
        val renderContext = RenderContextHolder.get() ?: return raw
        val typeRef = renderContext.typeResolver.resolve(raw)
        typeRef.collectImports(renderContext.importCollector)
        return typeRef.render()
    }
}

private class UseFunction : Function {
    override fun getArgumentNames(): List<String> = listOf("value")

    override fun execute(
        args: Map<String, Any?>,
        self: PebbleTemplate,
        context: EvaluationContext,
        lineNumber: Int,
    ): Any {
        val raw = args["value"]?.toString().orEmpty()
        if (raw.isBlank()) return ""
        val renderContext = RenderContextHolder.get() ?: return ""
        val typeRef = renderContext.typeResolver.resolve(raw)
        typeRef.collectImports(renderContext.importCollector)
        return ""
    }
}

private class ImportsFunction : Function {
    override fun getArgumentNames(): List<String> = emptyList()

    override fun execute(
        args: Map<String, Any?>,
        self: PebbleTemplate,
        context: EvaluationContext,
        lineNumber: Int,
    ): Any {
        return RenderContextHolder.IMPORTS_PLACEHOLDER
    }
}

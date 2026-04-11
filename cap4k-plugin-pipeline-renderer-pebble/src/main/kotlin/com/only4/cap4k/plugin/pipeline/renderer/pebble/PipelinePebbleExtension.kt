package com.only4.cap4k.plugin.pipeline.renderer.pebble

import com.google.gson.Gson
import io.pebbletemplates.pebble.extension.AbstractExtension
import io.pebbletemplates.pebble.extension.Filter
import io.pebbletemplates.pebble.extension.Function
import io.pebbletemplates.pebble.template.EvaluationContext
import io.pebbletemplates.pebble.template.PebbleTemplate

internal class PipelinePebbleExtension : AbstractExtension() {
    override fun getFilters(): Map<String, Filter> = mapOf(
        "json" to JsonFilter()
    )

    override fun getFunctions(): Map<String, Function> = mapOf(
        "type" to TypeFunction(),
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
        val value = args["value"] ?: throw IllegalArgumentException(
            "type() requires a String or a field-like object/map exposing renderedType."
        )
        return extractRenderedType(value) ?: throw IllegalArgumentException(
            "type() requires a String or a field-like object/map exposing renderedType."
        )
    }

    private fun extractRenderedType(value: Any): String? = when (value) {
        is String -> value
        is Map<*, *> -> value["renderedType"] as? String
        else -> readStringProperty(value, "renderedType")
    }
}

private class ImportsFunction : Function {
    override fun getArgumentNames(): List<String> = listOf("value")

    override fun execute(
        args: Map<String, Any?>,
        self: PebbleTemplate,
        context: EvaluationContext,
        lineNumber: Int,
    ): Any {
        val value = args["value"] ?: return emptyList<String>()
        return normalizeImports(extractImports(value))
    }

    private fun extractImports(value: Any): List<String> = when (value) {
        is List<*> -> value.map { entry ->
            entry as? String ?: throw IllegalArgumentException(
                "imports() requires String entries in the provided list."
            )
        }

        is Map<*, *> -> {
            val imports = value["imports"] ?: return emptyList()
            extractImports(imports)
        }

        else -> throw IllegalArgumentException(
            "imports() requires a List<String> or a map exposing imports."
        )
    }

    private fun normalizeImports(imports: List<String>): List<String> {
        val uniqueImports = LinkedHashSet<String>()
        for (import in imports) {
            val normalizedImport = import.trim()
            if (normalizedImport.isNotBlank()) {
                uniqueImports.add(normalizedImport)
            }
        }
        return uniqueImports.toList()
    }
}

private class JsonFilter(
    private val gson: Gson = Gson(),
) : Filter {
    override fun getArgumentNames(): List<String> = emptyList()

    override fun apply(
        input: Any?,
        args: MutableMap<String, Any>?,
        self: PebbleTemplate?,
        context: EvaluationContext?,
        lineNumber: Int,
    ): Any = gson.toJson(input)
}

private fun readStringProperty(value: Any, propertyName: String): String? {
    val getterName = "get" + propertyName.replaceFirstChar { it.uppercaseChar() }
    val getter = value.javaClass.methods.firstOrNull { method ->
        method.name == getterName && method.parameterCount == 0
    }
    val getterValue = getter?.invoke(value)
    if (getterValue is String) {
        return getterValue
    }

    val field = runCatching { value.javaClass.getDeclaredField(propertyName) }.getOrNull() ?: return null
    field.isAccessible = true
    return field.get(value) as? String
}

private fun readProperty(value: Any, propertyName: String): Any? {
    val getterName = "get" + propertyName.replaceFirstChar { it.uppercaseChar() }
    val getter = value.javaClass.methods.firstOrNull { method ->
        method.name == getterName && method.parameterCount == 0
    }
    getter?.let { return it.invoke(value) }

    val field = runCatching { value.javaClass.getDeclaredField(propertyName) }.getOrNull() ?: return null
    field.isAccessible = true
    return field.get(value)
}

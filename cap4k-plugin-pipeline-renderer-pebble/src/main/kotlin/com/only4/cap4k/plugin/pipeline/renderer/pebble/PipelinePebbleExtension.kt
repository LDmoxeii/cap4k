package com.only4.cap4k.plugin.pipeline.renderer.pebble

import com.google.gson.Gson
import io.pebbletemplates.pebble.extension.AbstractExtension
import io.pebbletemplates.pebble.extension.Filter
import io.pebbletemplates.pebble.extension.Function
import io.pebbletemplates.pebble.template.EvaluationContext
import io.pebbletemplates.pebble.template.PebbleTemplate

internal class PipelinePebbleExtension(
    private val sessionProvider: () -> PebbleRenderSession?,
    private val enableUseHelper: Boolean,
) : AbstractExtension() {
    override fun getFilters(): Map<String, Filter> = mapOf(
        "json" to JsonFilter()
    )

    override fun getFunctions(): Map<String, Function> = mapOf(
        "type" to TypeFunction(),
        "imports" to ImportsFunction(sessionProvider),
    ) + if (enableUseHelper) {
        mapOf("use" to UseFunction(sessionProvider))
    } else {
        emptyMap()
    }
}

internal class ExplicitImportCollector {
    private val explicitImports = LinkedHashSet<String>()
    private val explicitImportsBySimpleName = LinkedHashMap<String, String>()

    fun register(rawImport: String) {
        val normalizedImport = rawImport.trim()
        validateExplicitImport(normalizedImport)

        val simpleName = normalizedImport.substringAfterLast('.')
        val existingImport = explicitImportsBySimpleName.putIfAbsent(simpleName, normalizedImport)
        if (existingImport != null && existingImport != normalizedImport) {
            throw IllegalArgumentException(
                "use() import conflict: $simpleName is already bound to $existingImport, cannot also import $normalizedImport"
            )
        }

        explicitImports.add(normalizedImport)
    }

    fun mergedWith(baseImports: List<String>): List<String> {
        val normalizedBaseImports = normalizeImports(baseImports)
        val mergedImports = LinkedHashSet<String>(normalizedBaseImports)
        val simpleNameToImport = LinkedHashMap<String, String>()

        for (baseImport in normalizedBaseImports) {
            simpleNameToImport.putIfAbsent(baseImport.substringAfterLast('.'), baseImport)
        }

        for (explicitImport in explicitImports) {
            val simpleName = explicitImport.substringAfterLast('.')
            val existingImport = simpleNameToImport[simpleName]
            if (existingImport != null && existingImport != explicitImport) {
                throw IllegalArgumentException(
                    "use() import conflict: $simpleName is already bound to $existingImport, cannot also import $explicitImport"
                )
            }

            simpleNameToImport.putIfAbsent(simpleName, explicitImport)
            mergedImports.add(explicitImport)
        }

        return mergedImports.toList()
    }

    fun explicitImports(): List<String> = explicitImports.toList()
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
    private val sessionProvider: () -> PebbleRenderSession?

    constructor(sessionProvider: () -> PebbleRenderSession?) {
        this.sessionProvider = sessionProvider
    }

    override fun getArgumentNames(): List<String> = listOf("value")

    override fun execute(
        args: Map<String, Any?>,
        self: PebbleTemplate,
        context: EvaluationContext,
        lineNumber: Int,
    ): Any {
        if (!args.containsKey("value")) {
            throw IllegalArgumentException("imports() requires an argument.")
        }

        val value = args["value"] ?: return emptyList<String>()
        val baseImports = extractImports(value)
        return sessionProvider()?.explicitImportCollector?.mergedWith(baseImports)
            ?: normalizeImports(baseImports)
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
}

private class UseFunction(
    private val sessionProvider: () -> PebbleRenderSession?,
) : Function {
    override fun getArgumentNames(): List<String> = listOf("value")

    override fun execute(
        args: Map<String, Any?>,
        self: PebbleTemplate,
        context: EvaluationContext,
        lineNumber: Int,
    ): Any {
        if (!args.containsKey("value")) {
            throw IllegalArgumentException("use() requires exactly one argument.")
        }

        val value = args["value"]
            ?: throw IllegalArgumentException("use() requires a string fully qualified type name.")
        val importName = value as? String
            ?: throw IllegalArgumentException("use() requires a string fully qualified type name.")

        val normalizedImport = importName.trim()
        validateExplicitImport(normalizedImport)
        val session = sessionProvider()
        if (session?.phase != RenderPhase.COLLECTING) {
            return ""
        }

        session.explicitImportCollector.register(normalizedImport)
        return ""
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

private fun validateExplicitImport(importName: String) {
    if (!EXPLICIT_IMPORT_PATTERN.matches(importName)) {
        throw IllegalArgumentException("use() requires a fully qualified type name: $importName")
    }
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

private val EXPLICIT_IMPORT_PATTERN = Regex(
    """^([A-Za-z_$][A-Za-z0-9_$]*\.)+[A-Za-z_$][A-Za-z0-9_$]*$"""
)

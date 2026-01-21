package com.only4.cap4k.plugin.codegen.pebble

import com.only4.cap4k.plugin.codegen.imports.ImportCollector
import com.only4.cap4k.plugin.codegen.imports.TypeResolver
import com.only4.cap4k.plugin.codegen.misc.concatPackage

data class RenderContext(
    val typeResolver: TypeResolver,
    val importCollector: ImportCollector,
)

object RenderContextHolder {
    const val IMPORTS_PLACEHOLDER = "__CAP4K_IMPORTS__"

    private val holder = ThreadLocal<RenderContext?>()

    fun set(ctx: RenderContext?) {
        holder.set(ctx)
    }

    fun get(): RenderContext? = holder.get()

    fun clear() {
        holder.remove()
    }

    fun build(context: Map<String, Any?>): RenderContext? {
        val basePackage = context["basePackage"]?.toString()?.trim().orEmpty()
        if (basePackage.isBlank()) return null

        val templatePackage = context["templatePackage"]?.toString().orEmpty()
        val pkg = context["package"]?.toString().orEmpty()
        val currentPackage = concatPackage(basePackage, templatePackage, pkg)

        val typeMapping = buildMap<String, String> {
            val raw = context["typeMapping"]
            if (raw is Map<*, *>) {
                raw.forEach { (k, v) ->
                    val key = k?.toString()?.trim().orEmpty()
                    val value = v?.toString()?.trim().orEmpty()
                    if (key.isNotEmpty() && value.isNotEmpty()) {
                        put(key, value)
                    }
                }
            }
        }

        val collector = ImportCollector()
        val importLines = when (val raw = context["imports"]) {
            is Iterable<*> -> raw.filterIsInstance<String>()
            else -> emptyList()
        }
        collector.addImportLines(importLines)

        return RenderContext(
            typeResolver = TypeResolver(typeMapping, currentPackage),
            importCollector = collector,
        )
    }
}

package com.only4.cap4k.plugin.codegen.context.drawingboard

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.only4.cap4k.plugin.codegen.context.ContextBuilder
import com.only4.cap4k.plugin.codeanalysis.core.model.DesignElement
import com.only4.cap4k.plugin.codeanalysis.core.model.DesignField
import java.io.File

class DrawingBoardContextBuilder : ContextBuilder<MutableDrawingBoardContext> {
    override val order: Int = 10

    override fun build(context: MutableDrawingBoardContext) {
        val moduleRoots = listOf(
            context.adapterPath,
            context.applicationPath,
            context.domainPath
        ).distinct()
            .filter { it.isNotBlank() }
            .map(::File)
        val elements = collectElements(moduleRoots)

        context.elements.clear()
        context.elements.addAll(elements)
        context.elementsByTag.clear()
        elements.groupBy { it.tag }.forEach { (tag, items) ->
            context.elementsByTag[tag] = items.toMutableList()
        }
    }

    private fun collectElements(moduleRoots: List<File>): List<DesignElement> {
        val elementsByKey = linkedMapOf<String, DesignElement>()
        moduleRoots.forEach { moduleRoot ->
            val inputFile = File(moduleRoot, "build/cap4k-code-analysis/design-elements.json")
            if (!inputFile.exists()) return@forEach
            parseElements(inputFile.readText())
                .forEach { element ->
                    val key = "${element.tag}|${element.`package`}|${element.name}"
                    elementsByKey.putIfAbsent(key, element)
                }
        }
        return elementsByKey.values.toList()
    }

    private fun parseElements(content: String): List<DesignElement> {
        val root = JsonParser.parseString(content)
        val array = root.takeIf { it.isJsonArray }?.asJsonArray ?: return emptyList()
        return array.mapNotNull { element -> parseElement(element) }
    }

    private fun parseElement(element: JsonElement): DesignElement? {
        val obj = element as? JsonObject ?: return null
        val tag = obj.stringOrNull("tag") ?: return null
        val pkg = obj.stringOrEmpty("package")
        val name = obj.stringOrEmpty("name")
        val desc = obj.stringOrEmpty("desc")
        val aggregates = obj.getAsJsonArray("aggregates")?.mapNotNull { it.asStringOrNull() }.orEmpty()
        val entity = obj.stringOrNull("entity")
        val persist = obj.booleanOrNull("persist")
        val requestFields = parseFields(obj.getAsJsonArray("requestFields"))
        val responseFields = parseFields(obj.getAsJsonArray("responseFields"))
        return DesignElement(
            tag = tag,
            `package` = pkg,
            name = name,
            desc = desc,
            aggregates = aggregates,
            entity = entity,
            persist = persist,
            requestFields = requestFields,
            responseFields = responseFields
        )
    }

    private fun parseFields(array: JsonArray?): List<DesignField> {
        if (array == null) return emptyList()
        return array.mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            val name = obj.stringOrNull("name") ?: return@mapNotNull null
            val type = obj.stringOrEmpty("type")
            val nullable = obj.booleanOrNull("nullable") ?: false
            val defaultValue = obj.stringOrNull("defaultValue")
            DesignField(
                name = name,
                type = type,
                nullable = nullable,
                defaultValue = defaultValue
            )
        }
    }

    private fun JsonObject.stringOrEmpty(name: String): String =
        stringOrNull(name).orEmpty()

    private fun JsonObject.stringOrNull(name: String): String? =
        get(name)?.asStringOrNull()

    private fun JsonObject.booleanOrNull(name: String): Boolean? {
        val element = get(name) ?: return null
        return if (element.isJsonPrimitive && element.asJsonPrimitive.isBoolean) {
            element.asBoolean
        } else {
            null
        }
    }

    private fun JsonElement.asStringOrNull(): String? {
        if (!isJsonPrimitive) return null
        val prim = asJsonPrimitive
        return if (prim.isString) prim.asString else prim.toString()
    }
}

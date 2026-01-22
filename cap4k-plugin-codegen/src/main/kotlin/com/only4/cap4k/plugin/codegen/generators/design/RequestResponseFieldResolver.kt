package com.only4.cap4k.plugin.codegen.generators.design

import com.only4.cap4k.plugin.codegen.context.design.DesignContext
import com.only4.cap4k.plugin.codegen.context.design.models.BaseDesign
import com.only4.cap4k.plugin.codegen.context.design.models.common.PayloadField
import com.only4.cap4k.plugin.codegen.ksp.models.FieldMetadata
import com.only4.cap4k.plugin.codegen.misc.toUpperCamelCase

data class ResolvedRequestResponseFields(
    val requestFieldsForTemplate: List<Map<String, String>>,
    val responseFieldsForTemplate: List<Map<String, String>>,
    val nestedTypesForTemplate: List<Map<String, Any>>,
)

context(ctx: DesignContext)
fun resolveRequestResponseFields(
    design: BaseDesign,
    requestFields: List<PayloadField>,
    responseFields: List<PayloadField>,
): ResolvedRequestResponseFields {
    fun inferType(field: PayloadField): String {
        field.type?.takeIf { it.isNotBlank() }?.let { return it }

        design.aggregates.forEach { aggName ->
            val agg = ctx.aggregateMap[aggName] ?: return@forEach
            lookupFieldType(agg.aggregateRoot.fields, field.name)?.let { return it }
            agg.valueObjects.forEach { vo ->
                lookupFieldType(vo.fields, field.name)?.let { return it }
            }
            agg.entities.forEach { entity ->
                lookupFieldType(entity.fields, field.name)?.let { return it }
            }
        }
        return "kotlin.String"
    }

    fun renderType(rawType: String, nullable: Boolean): String {
        val trimmed = rawType.trim()
        return if (nullable && !trimmed.endsWith("?")) "${trimmed}?" else trimmed
    }

    fun formatDefaultValue(typeForCode: String, rawDefaultValue: String): String {
        val trimmed = rawDefaultValue.trim()
        if (trimmed.isEmpty() || trimmed == "null") return trimmed

        val baseType = typeForCode.removeSuffix("?").trim().substringAfterLast('.')
        return when (baseType) {
            "String" -> {
                if ((trimmed.startsWith("\"") && trimmed.endsWith("\"")) ||
                    (trimmed.startsWith("'") && trimmed.endsWith("'"))
                ) {
                    trimmed
                } else {
                    "\"$trimmed\""
                }
            }
            "Long" -> {
                if (trimmed.endsWith("L") || trimmed.endsWith("l")) {
                    trimmed
                } else if (isLongLiteral(trimmed)) {
                    "${trimmed}L"
                } else {
                    trimmed
                }
            }
            else -> trimmed
        }
    }

    fun extractNestedFields(fields: List<PayloadField>): Pair<List<PayloadField>, Map<String, List<PayloadField>>> {
        val topLevel = mutableListOf<PayloadField>()
        val nested = linkedMapOf<String, MutableList<PayloadField>>()
        val regex = Regex("^(.+?)\\[\\]\\.(.+)$")

        fields.forEach { field ->
            val match = regex.matchEntire(field.name.trim())
            if (match == null) {
                topLevel.add(field)
                return@forEach
            }

            val containerName = match.groupValues[1].trim()
            val nestedName = match.groupValues[2].trim()
            if (containerName.isEmpty() || nestedName.isEmpty() || nestedName.contains('.')) {
                topLevel.add(field)
                return@forEach
            }

            nested.getOrPut(containerName) { mutableListOf() }.add(field.copy(name = nestedName))
        }

        nested.keys.forEach { containerName ->
            if (topLevel.none { it.name == containerName }) {
                topLevel.add(PayloadField(name = containerName))
            }
        }

        return topLevel to nested
    }

    fun splitTopLevelArgs(text: String): List<String> {
        val trimmed = text.trim()
        val lt = trimmed.indexOf('<')
        if (lt < 0) return emptyList()

        val args = mutableListOf<String>()
        val current = StringBuilder()
        var depth = 0
        var i = lt + 1
        while (i < trimmed.length) {
            val c = trimmed[i]
            when (c) {
                '<' -> {
                    depth++
                    current.append(c)
                }
                '>' -> {
                    if (depth == 0) {
                        val arg = current.toString().trim()
                        if (arg.isNotEmpty()) args.add(arg)
                        break
                    }
                    depth--
                    current.append(c)
                }
                ',' -> {
                    if (depth == 0) {
                        val arg = current.toString().trim()
                        if (arg.isNotEmpty()) args.add(arg)
                        current.setLength(0)
                    } else {
                        current.append(c)
                    }
                }
                else -> current.append(c)
            }
            i++
        }
        return args
    }

    fun resolveNestedTypeName(containerName: String, containerType: String?): String {
        val rawType = containerType?.trim().orEmpty()
        val arg = splitTopLevelArgs(rawType).firstOrNull()?.trim().orEmpty()
        if (arg.isNotBlank()) {
            val clean = arg.removePrefix("out ").removePrefix("in ").removeSuffix("?").trim()
            val simple = clean.substringAfterLast('.')
            if (simple.isNotBlank()) {
                return simple
            }
        }

        val base = toUpperCamelCase(containerName) ?: containerName
        return if (base.endsWith("Item")) base else "${base}Item"
    }

    fun toTemplateFields(
        fields: List<PayloadField>,
        nestedTypeNameByContainer: Map<String, String>,
    ): List<Map<String, String>> =
        fields.map { f ->
            val rawType = if (nestedTypeNameByContainer.containsKey(f.name)) {
                f.type?.takeIf { it.isNotBlank() } ?: "List<${nestedTypeNameByContainer.getValue(f.name)}>"
            } else {
                inferType(f)
            }
            val typeForCode = renderType(rawType, f.nullable)
            val defaultValue = f.defaultValue?.takeIf { it.isNotBlank() }
            val formattedDefaultValue = defaultValue?.let { formatDefaultValue(typeForCode, it) }
            buildMap {
                put("name", f.name)
                put("type", typeForCode)
                formattedDefaultValue?.let { put("defaultValue", it) }
            }
        }

    fun resolveFields(fields: List<PayloadField>): Pair<List<Map<String, String>>, List<Map<String, Any>>> {
        val (topLevel, nested) = extractNestedFields(fields)
        val nestedTypeNameByContainer = nested.mapValues { (name, _) ->
            val containerType = topLevel.firstOrNull { it.name == name }?.type
            resolveNestedTypeName(name, containerType)
        }

        val nestedTypesForTemplate = nested.map { (containerName, nestedFields) ->
            val nestedTypeName = nestedTypeNameByContainer.getValue(containerName)
            mapOf(
                "name" to nestedTypeName,
                "fields" to toTemplateFields(nestedFields, emptyMap()),
            )
        }

        val fieldsForTemplate = toTemplateFields(topLevel, nestedTypeNameByContainer)
        return fieldsForTemplate to nestedTypesForTemplate
    }

    val (request, requestNestedTypes) = resolveFields(requestFields)
    val (response, responseNestedTypes) = resolveFields(responseFields)
    val nestedTypes = mergeNestedTypes(requestNestedTypes, responseNestedTypes)

    return ResolvedRequestResponseFields(
        requestFieldsForTemplate = request,
        responseFieldsForTemplate = response,
        nestedTypesForTemplate = nestedTypes,
    )
}

private fun mergeNestedTypes(
    request: List<Map<String, Any>>,
    response: List<Map<String, Any>>,
): List<Map<String, Any>> {
    val result = linkedMapOf<String, Map<String, Any>>()

    fun merge(list: List<Map<String, Any>>) {
        list.forEach { nested ->
            val name = nested["name"] as? String ?: return@forEach
            val fields = nested["fields"] as? List<*> ?: return@forEach
            val current = result[name]
            if (current == null) {
                result[name] = nested
                return@forEach
            }

            val currentFields = current["fields"] as? List<*> ?: emptyList<Any>()
            val mergedFields = mergeFieldMaps(currentFields, fields)
            result[name] = mapOf(
                "name" to name,
                "fields" to mergedFields,
            )
        }
    }

    merge(request)
    merge(response)

    return result.values.toList()
}

private fun mergeFieldMaps(
    current: List<*>,
    incoming: List<*>,
): List<Map<String, String>> {
    val result = linkedMapOf<String, Map<String, String>>()
    current.forEach { item ->
        val map = item as? Map<*, *> ?: return@forEach
        val name = map["name"] as? String ?: return@forEach
        @Suppress("UNCHECKED_CAST")
        result[name] = map as Map<String, String>
    }
    incoming.forEach { item ->
        val map = item as? Map<*, *> ?: return@forEach
        val name = map["name"] as? String ?: return@forEach
        if (!result.containsKey(name)) {
            @Suppress("UNCHECKED_CAST")
            result[name] = map as Map<String, String>
        }
    }
    return result.values.toList()
}

private fun isLongLiteral(value: String): Boolean {
    val decimal = Regex("^-?[0-9_]+$")
    val hex = Regex("^-?0[xX][0-9a-fA-F_]+$")
    val binary = Regex("^-?0[bB][01_]+$")
    return decimal.matches(value) || hex.matches(value) || binary.matches(value)
}

private fun lookupFieldType(fields: List<FieldMetadata>, name: String): String? {
    val match = fields.firstOrNull { it.name.equals(name, ignoreCase = false) } ?: return null
    return match.type
}

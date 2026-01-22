package com.only4.cap4k.plugin.codegen.generators.design

import com.only4.cap4k.plugin.codegen.context.design.DesignContext
import com.only4.cap4k.plugin.codegen.context.design.models.BaseDesign
import com.only4.cap4k.plugin.codegen.context.design.models.common.PayloadField
import com.only4.cap4k.plugin.codegen.ksp.models.FieldMetadata
import com.only4.cap4k.plugin.codegen.misc.toUpperCamelCase

private data class PathSegment(
    val name: String,
    val isList: Boolean,
)

private data class PathNode(
    val name: String,
    var isList: Boolean = false,
    var explicitField: PayloadField? = null,
    val children: LinkedHashMap<String, PathNode> = linkedMapOf(),
)

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

    fun resolveNestedTypeName(containerName: String, containerType: String?, isList: Boolean): String {
        val rawType = containerType?.trim().orEmpty()
        if (rawType.isNotBlank()) {
            val arg = splitTopLevelArgs(rawType).firstOrNull()?.trim().orEmpty()
            val candidate = if (arg.isNotBlank()) arg else rawType
            val clean = candidate.removePrefix("out ").removePrefix("in ").removeSuffix("?").trim()
            val simple = clean.substringAfterLast('.')
            if (simple.isNotBlank()) {
                return simple
            }
        }

        val base = toUpperCamelCase(containerName) ?: containerName
        return if (isList && !base.endsWith("Item")) "${base}Item" else base
    }

    fun parseSegments(name: String): List<PathSegment>? {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return null
        val parts = trimmed.split('.')
        if (parts.any { it.isBlank() }) return null
        return parts.map { part ->
            val isList = part.endsWith("[]")
            val base = if (isList) part.removeSuffix("[]") else part
            if (base.isBlank()) return null
            PathSegment(base, isList)
        }
    }

    fun resolveNodeTypeName(node: PathNode): String =
        resolveNestedTypeName(node.name, node.explicitField?.type, node.isList)

    fun resolveContainerFieldType(node: PathNode): String {
        val typeName = resolveNodeTypeName(node)
        return if (node.isList) "List<$typeName>" else typeName
    }

    fun resolveLeafFieldType(node: PathNode): String {
        val rawType = node.explicitField?.type?.takeIf { it.isNotBlank() }
            ?: inferType(node.explicitField ?: PayloadField(name = node.name))
        return if (node.isList && !isListType(rawType)) {
            "List<$rawType>"
        } else {
            rawType
        }
    }

    fun toFieldMap(
        name: String,
        rawType: String,
        nullable: Boolean,
        defaultValue: String?,
    ): Map<String, String> {
        val typeForCode = renderType(rawType, nullable)
        val formattedDefaultValue = defaultValue?.let { formatDefaultValue(typeForCode, it) }
        return buildMap {
            put("name", name)
            put("type", typeForCode)
            formattedDefaultValue?.let { put("defaultValue", it) }
        }
    }

    fun resolveFields(fields: List<PayloadField>): Pair<List<Map<String, String>>, List<Map<String, Any>>> {
        val root = PathNode(name = "__root__")

        fields.forEach { field ->
            val trimmedName = field.name.trim()
            if (trimmedName.isEmpty()) return@forEach

            val segments = parseSegments(trimmedName)
            val isNested = segments != null && (segments.size > 1 || segments.any { it.isList })
            if (!isNested) {
                val node = root.children.getOrPut(trimmedName) { PathNode(name = trimmedName) }
                if (node.explicitField == null) {
                    node.explicitField = field
                }
                if (isListType(field.type?.trim().orEmpty())) {
                    node.isList = true
                }
                return@forEach
            }

            var current = root
            segments!!.forEach { segment ->
                val child = current.children.getOrPut(segment.name) { PathNode(name = segment.name) }
                if (segment.isList) {
                    child.isList = true
                }
                current = child
            }

            if (current.explicitField == null) {
                current.explicitField = field.copy(name = segments.last().name)
            }
            if (isListType(field.type?.trim().orEmpty())) {
                current.isList = true
            }
        }

        val fieldsForTemplate = root.children.values.map { node ->
            val explicit = node.explicitField
            val rawType = when {
                explicit?.type?.isNotBlank() == true -> {
                    val explicitType = explicit.type.trim()
                    if (node.isList && !isListType(explicitType)) {
                        "List<$explicitType>"
                    } else {
                        explicitType
                    }
                }
                node.children.isNotEmpty() || node.isList -> resolveContainerFieldType(node)
                else -> inferType(explicit ?: PayloadField(name = node.name))
            }
            toFieldMap(
                name = node.name,
                rawType = rawType,
                nullable = explicit?.nullable ?: false,
                defaultValue = explicit?.defaultValue?.takeIf { it.isNotBlank() },
            )
        }

        val nestedTypesForTemplate = mutableListOf<Map<String, Any>>()
        fun visit(node: PathNode) {
            if (node.children.isNotEmpty()) {
                val typeName = resolveNodeTypeName(node)
                val nestedFields = node.children.values.map { child ->
                    val rawType = if (child.children.isNotEmpty()) {
                        resolveContainerFieldType(child)
                    } else {
                        resolveLeafFieldType(child)
                    }
                    toFieldMap(
                        name = child.name,
                        rawType = rawType,
                        nullable = child.explicitField?.nullable ?: false,
                        defaultValue = child.explicitField?.defaultValue?.takeIf { it.isNotBlank() },
                    )
                }
                nestedTypesForTemplate.add(
                    mapOf(
                        "name" to typeName,
                        "fields" to nestedFields,
                    )
                )
            }
            node.children.values.forEach { visit(it) }
        }
        root.children.values.forEach { visit(it) }

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

private fun isListType(value: String): Boolean {
    val trimmed = value.trim()
    return trimmed.startsWith("List<") ||
        trimmed.startsWith("MutableList<") ||
        trimmed.startsWith("kotlin.collections.List<") ||
        trimmed.startsWith("kotlin.collections.MutableList<")
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

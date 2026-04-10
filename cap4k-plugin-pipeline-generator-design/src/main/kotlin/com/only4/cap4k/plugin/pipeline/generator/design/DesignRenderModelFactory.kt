package com.only4.cap4k.plugin.pipeline.generator.design

import com.only4.cap4k.plugin.pipeline.api.FieldModel
import com.only4.cap4k.plugin.pipeline.api.RequestModel

internal object DesignRenderModelFactory {
    private val builtInTypes = setOf(
        "Any",
        "Array",
        "Boolean",
        "Byte",
        "Char",
        "Collection",
        "Double",
        "Float",
        "Int",
        "Iterable",
        "List",
        "Long",
        "Map",
        "MutableCollection",
        "MutableList",
        "MutableMap",
        "MutableSet",
        "Nothing",
        "Number",
        "Pair",
        "Sequence",
        "Set",
        "Short",
        "String",
        "Triple",
        "Unit",
    )

    private val collectionWrapperTypes = setOf(
        "Collection",
        "Iterable",
        "List",
        "MutableCollection",
        "MutableList",
        "MutableSet",
        "Set",
    )

    fun create(packageName: String, request: RequestModel): DesignRenderModel {
        val fqcnCollisionNames = collectCollidingFqcnSimpleNames(
            request.requestFields + request.responseFields,
        )
        val requestNamespace = buildNamespace(request.requestFields, "request", fqcnCollisionNames)
        val responseNamespace = buildNamespace(request.responseFields, "response", fqcnCollisionNames)

        return DesignRenderModel(
            packageName = packageName,
            typeName = request.typeName,
            description = request.description,
            aggregateName = request.aggregateName,
            imports = (requestNamespace.imports + responseNamespace.imports).distinct(),
            requestFields = requestNamespace.fields,
            responseFields = responseNamespace.fields,
            requestNestedTypes = requestNamespace.nestedTypes,
            responseNestedTypes = responseNamespace.nestedTypes,
        )
    }

    private fun buildNamespace(
        fields: List<FieldModel>,
        namespace: String,
        collidingFqcnSimpleNames: Set<String>,
    ): NamespaceRenderResult {
        val imports = linkedSetOf<String>()
        val directFields = linkedMapOf<String, FieldModel>()
        val directFieldDeclarations = linkedMapOf<String, MutableList<FieldModel>>()
        val nestedGroups = linkedMapOf<String, NestedGroup>()
        val nestedTypeNames = linkedSetOf<String>()
        val nestedRootsByTypeName = linkedMapOf<String, String>()

        fields.forEach { field ->
            val parts = field.name.split('.')
            if (parts.size == 1) {
                directFields.putIfAbsent(field.name, field)
                directFieldDeclarations.getOrPut(field.name) { mutableListOf() }.add(field)
                return@forEach
            }

            require(parts.size == 2) {
                "nested field paths must have exactly one level in $namespace namespace: ${field.name}"
            }

            val rootName = parts[0]
            val nestedTypeName = toNestedTypeName(rootName)
            val previousRoot = nestedRootsByTypeName.putIfAbsent(nestedTypeName, rootName)
            if (previousRoot != null && previousRoot != rootName) {
                throw IllegalArgumentException("duplicate nested type name in $namespace namespace: $nestedTypeName")
            }

            val group = nestedGroups.getOrPut(
                rootName,
            ) {
                NestedGroup(rootName = rootName, nestedTypeName = nestedTypeName)
            }
            if (group.nestedTypeName != nestedTypeName) {
                throw IllegalArgumentException("duplicate nested type name in $namespace namespace: $nestedTypeName")
            }

            group.fields += field.toRenderField(name = parts[1])
        }

        nestedGroups.values.forEach { group ->
            val directRootDeclarations = directFieldDeclarations[group.rootName].orEmpty()
            val directRootField = when (directRootDeclarations.size) {
                0 -> throw IllegalArgumentException(
                    "missing compatible direct root field for nested type ${group.nestedTypeName} in $namespace namespace",
                )
                1 -> directRootDeclarations.single()
                else -> throw IllegalArgumentException(
                    "duplicate direct root declarations for ${group.rootName} in $namespace namespace",
                )
            }

            if (!isCompatibleRootField(directRootField.type, group.nestedTypeName)) {
                throw IllegalArgumentException(
                    "direct root field ${group.rootName} in $namespace namespace must point to nested type ${group.nestedTypeName}",
                )
            }

            nestedTypeNames += group.nestedTypeName
        }

        val renderedDirectFields = directFields.values.map { field ->
            field.toRenderField(
                type = renderType(
                    type = field.type,
                    imports = imports,
                    reservedSimpleNames = nestedTypeNames,
                    collidingFqcnSimpleNames = collidingFqcnSimpleNames,
                ),
            )
        }

        val renderedNestedTypes = nestedGroups.values.map { group ->
            DesignRenderNestedTypeModel(
                name = group.nestedTypeName,
                fields = group.fields.map { field ->
                    field.copy(
                        type = renderType(
                            type = field.type,
                            imports = imports,
                            reservedSimpleNames = nestedTypeNames,
                            collidingFqcnSimpleNames = collidingFqcnSimpleNames,
                        ),
                    )
                },
            )
        }

        return NamespaceRenderResult(
            fields = renderedDirectFields,
            nestedTypes = renderedNestedTypes,
            imports = imports.toList(),
        )
    }

    private fun renderType(
        type: String,
        imports: MutableSet<String>,
        reservedSimpleNames: Set<String>,
        collidingFqcnSimpleNames: Set<String>,
    ): String {
        val trimmed = type.trim()
        if (trimmed.isEmpty()) {
            return trimmed
        }

        val rendered = renderGenericType(trimmed, imports, reservedSimpleNames, collidingFqcnSimpleNames)
        return rendered ?: trimmed
    }

    private fun renderGenericType(
        type: String,
        imports: MutableSet<String>,
        reservedSimpleNames: Set<String>,
        collidingFqcnSimpleNames: Set<String>,
    ): String? {
        val genericStart = type.indexOf('<')
        if (genericStart < 0) {
            return renderSimpleType(type, imports, reservedSimpleNames, collidingFqcnSimpleNames)
        }

        val genericEnd = type.lastIndexOf('>')
        if (genericEnd <= genericStart) {
            return null
        }

        val rootType = type.substring(0, genericStart).trim()
        val argsText = type.substring(genericStart + 1, genericEnd)
        val renderedRootType = renderSimpleType(
            rootType,
            imports,
            reservedSimpleNames,
            collidingFqcnSimpleNames,
        )
        val renderedArgs = splitGenericArguments(argsText).map { argument ->
            renderType(argument, imports, reservedSimpleNames, collidingFqcnSimpleNames)
        }

        return "$renderedRootType<${renderedArgs.joinToString(", ")}>"
    }

    private fun renderSimpleType(
        type: String,
        imports: MutableSet<String>,
        reservedSimpleNames: Set<String>,
        collidingFqcnSimpleNames: Set<String>,
    ): String {
        val trimmed = type.trim()
        if (trimmed.isEmpty()) {
            return trimmed
        }

        if (trimmed in builtInTypes) {
            return trimmed
        }

        val simpleName = trimmed.substringAfterLast('.')
        if (simpleName != trimmed) {
            if (simpleName in reservedSimpleNames || simpleName in collidingFqcnSimpleNames) {
                return trimmed
            }

            imports += trimmed
            return simpleName
        }

        return trimmed
    }

    private fun splitGenericArguments(text: String): List<String> {
        if (text.isBlank()) {
            return emptyList()
        }

        val result = mutableListOf<String>()
        var depth = 0
        var start = 0
        text.forEachIndexed { index, char ->
            when (char) {
                '<' -> depth++
                '>' -> depth--
                ',' -> if (depth == 0) {
                    result += text.substring(start, index).trim()
                    start = index + 1
                }
            }
        }

        result += text.substring(start).trim()
        return result.filter { it.isNotEmpty() }
    }

    private fun toNestedTypeName(rawName: String): String {
        return rawName
            .split(Regex("[^A-Za-z0-9]+"))
            .filter { it.isNotBlank() }
            .joinToString("") { part ->
                part.replaceFirstChar { it.uppercaseChar() }
            }
    }

    private fun isCompatibleRootField(type: String, nestedTypeName: String): Boolean {
        val trimmed = type.trim()
        if (trimmed.isBlank()) {
            return false
        }

        val genericStart = trimmed.indexOf('<')
        if (genericStart < 0) {
            return simpleTypeName(trimmed) == nestedTypeName
        }

        val genericEnd = trimmed.lastIndexOf('>')
        if (genericEnd <= genericStart) {
            return false
        }

        val rootType = trimmed.substring(0, genericStart).trim()
        if (rootType !in collectionWrapperTypes) {
            return false
        }

        val args = splitGenericArguments(trimmed.substring(genericStart + 1, genericEnd))
        return args.size == 1 && simpleTypeName(args.single()) == nestedTypeName
    }

    private fun simpleTypeName(type: String): String = type.trim().substringAfterLast('.')

    private fun collectCollidingFqcnSimpleNames(fields: List<FieldModel>): Set<String> {
        val fqcnBySimpleName = linkedMapOf<String, MutableSet<String>>()

        fields.forEach { field ->
            collectFqcnTypes(field.type).forEach { fqcn ->
                val simpleName = simpleTypeName(fqcn)
                fqcnBySimpleName.getOrPut(simpleName) { linkedSetOf() }.add(fqcn)
            }
        }

        return fqcnBySimpleName
            .filterValues { it.size > 1 }
            .keys
    }

    private fun collectFqcnTypes(type: String): List<String> {
        val trimmed = type.trim()
        if (trimmed.isEmpty()) {
            return emptyList()
        }

        val genericStart = trimmed.indexOf('<')
        if (genericStart < 0) {
            return if (trimmed.contains('.')) listOf(trimmed) else emptyList()
        }

        val genericEnd = trimmed.lastIndexOf('>')
        if (genericEnd <= genericStart) {
            return if (trimmed.contains('.')) listOf(trimmed) else emptyList()
        }

        val rootType = trimmed.substring(0, genericStart).trim()
        val args = splitGenericArguments(trimmed.substring(genericStart + 1, genericEnd))
        return buildList {
            addAll(collectFqcnTypes(rootType))
            args.forEach { addAll(collectFqcnTypes(it)) }
        }
    }

    private fun FieldModel.toRenderField(
        name: String = this.name,
        type: String = this.type,
    ): DesignRenderFieldModel = DesignRenderFieldModel(
        name = name,
        type = type,
        nullable = nullable,
        defaultValue = defaultValue,
    )

    private data class NamespaceRenderResult(
        val fields: List<DesignRenderFieldModel>,
        val nestedTypes: List<DesignRenderNestedTypeModel>,
        val imports: List<String>,
    )

    private data class NestedGroup(
        val rootName: String,
        val nestedTypeName: String,
        val fields: MutableList<DesignRenderFieldModel> = mutableListOf(),
    )
}

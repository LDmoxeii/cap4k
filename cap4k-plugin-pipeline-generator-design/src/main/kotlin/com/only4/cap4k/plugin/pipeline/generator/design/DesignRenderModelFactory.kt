package com.only4.cap4k.plugin.pipeline.generator.design

import com.only4.cap4k.plugin.pipeline.api.FieldModel
import com.only4.cap4k.plugin.pipeline.api.RequestModel
import java.util.ArrayDeque

internal object DesignRenderModelFactory {
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
        val requestNamespace = buildNamespace(request.requestFields, "request")
        val responseNamespace = buildNamespace(request.responseFields, "response")
        val importPlan = DesignImportPlanner.plan(
            types = requestNamespace.resolvedTypes + responseNamespace.resolvedTypes,
            innerTypeNames = requestNamespace.nestedTypeNames + responseNamespace.nestedTypeNames,
        )
        val renderedTypes = ArrayDeque(importPlan.renderedTypes)
        val requestFields = renderFields(requestNamespace.fields, renderedTypes)
        val requestNestedTypes = renderNestedTypes(requestNamespace.nestedTypes, renderedTypes)
        val responseFields = renderFields(responseNamespace.fields, renderedTypes)
        val responseNestedTypes = renderNestedTypes(responseNamespace.nestedTypes, renderedTypes)

        return DesignRenderModel(
            packageName = packageName,
            typeName = request.typeName,
            description = request.description,
            aggregateName = request.aggregateName,
            imports = importPlan.imports,
            requestFields = requestFields,
            responseFields = responseFields,
            requestNestedTypes = requestNestedTypes,
            responseNestedTypes = responseNestedTypes,
        )
    }

    private fun buildNamespace(
        fields: List<FieldModel>,
        namespace: String,
    ): NamespaceModel {
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

            val group = nestedGroups.getOrPut(rootName) {
                NestedGroup(rootName = rootName, nestedTypeName = nestedTypeName)
            }
            if (group.nestedTypeName != nestedTypeName) {
                throw IllegalArgumentException("duplicate nested type name in $namespace namespace: $nestedTypeName")
            }

            group.fields += RawFieldModel(
                name = parts[1],
                type = field.type,
                nullable = field.nullable,
                defaultValue = field.defaultValue,
            )
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

        return NamespaceModel(
            fields = directFields.values.map { it.toPreparedField(innerTypeNames = nestedTypeNames) },
            nestedTypes = nestedGroups.values.map { group ->
                PreparedNestedTypeModel(
                    name = group.nestedTypeName,
                    fields = group.fields.map { it.toPreparedField(innerTypeNames = nestedTypeNames) },
                )
            },
            nestedTypeNames = nestedTypeNames,
        )
    }

    private fun renderFields(
        fields: List<PreparedFieldModel>,
        renderedTypes: ArrayDeque<DesignRenderedTypeModel>,
    ): List<DesignRenderFieldModel> {
        return fields.map { field ->
            field.toRenderField(renderedType = renderedTypes.removeFirst().renderedText)
        }
    }

    private fun renderNestedTypes(
        nestedTypes: List<PreparedNestedTypeModel>,
        renderedTypes: ArrayDeque<DesignRenderedTypeModel>,
    ): List<DesignRenderNestedTypeModel> {
        return nestedTypes.map { nestedType ->
            DesignRenderNestedTypeModel(
                name = nestedType.name,
                fields = renderFields(nestedType.fields, renderedTypes),
            )
        }
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

        val arguments = splitGenericArguments(trimmed.substring(genericStart + 1, genericEnd))
        return arguments.size == 1 && simpleTypeName(arguments.single()) == nestedTypeName
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

    private fun simpleTypeName(type: String): String = type.trim().substringAfterLast('.')

    private fun FieldModel.toPreparedField(innerTypeNames: Set<String>): PreparedFieldModel {
        return PreparedFieldModel(
            name = name,
            nullable = nullable,
            defaultValue = defaultValue,
            resolvedType = DesignTypeResolver.resolve(
                type = DesignTypeParser.parse(type),
                innerTypeNames = innerTypeNames,
            ),
        )
    }

    private fun RawFieldModel.toPreparedField(innerTypeNames: Set<String>): PreparedFieldModel {
        return PreparedFieldModel(
            name = name,
            nullable = nullable,
            defaultValue = defaultValue,
            resolvedType = DesignTypeResolver.resolve(
                type = DesignTypeParser.parse(type),
                innerTypeNames = innerTypeNames,
            ),
        )
    }

    private fun PreparedFieldModel.toRenderField(renderedType: String): DesignRenderFieldModel {
        return DesignRenderFieldModel(
            name = name,
            renderedType = renderedType,
            nullable = nullable,
            defaultValue = defaultValue,
        )
    }

    private data class NamespaceModel(
        val fields: List<PreparedFieldModel>,
        val nestedTypes: List<PreparedNestedTypeModel>,
        val nestedTypeNames: Set<String>,
    ) {
        val resolvedTypes: List<DesignResolvedTypeModel>
            get() = fields.map { it.resolvedType } + nestedTypes.flatMap { nestedType ->
                nestedType.fields.map { it.resolvedType }
            }
    }

    private data class PreparedFieldModel(
        val name: String,
        val nullable: Boolean,
        val defaultValue: String?,
        val resolvedType: DesignResolvedTypeModel,
    )

    private data class PreparedNestedTypeModel(
        val name: String,
        val fields: List<PreparedFieldModel>,
    )

    private data class NestedGroup(
        val rootName: String,
        val nestedTypeName: String,
        val fields: MutableList<RawFieldModel> = mutableListOf(),
    )

    private data class RawFieldModel(
        val name: String,
        val type: String,
        val nullable: Boolean,
        val defaultValue: String?,
    )
}

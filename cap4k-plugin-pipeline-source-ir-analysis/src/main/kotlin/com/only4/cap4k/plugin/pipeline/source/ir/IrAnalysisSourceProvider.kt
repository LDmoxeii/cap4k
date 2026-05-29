package com.only4.cap4k.plugin.pipeline.source.ir

import com.google.gson.JsonElement
import com.google.gson.JsonParser
import com.only4.cap4k.plugin.pipeline.api.ArtifactSelectionModel
import com.only4.cap4k.plugin.pipeline.api.DesignElementSnapshot
import com.only4.cap4k.plugin.pipeline.api.DesignFieldSnapshot
import com.only4.cap4k.plugin.pipeline.api.IrAnalysisSnapshot
import com.only4.cap4k.plugin.pipeline.api.IrEdgeSnapshot
import com.only4.cap4k.plugin.pipeline.api.IrNodeSnapshot
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig
import com.only4.cap4k.plugin.pipeline.api.SourceProvider
import java.io.File

class IrAnalysisSourceProvider : SourceProvider {
    override val id: String = "ir-analysis"

    override fun collect(config: ProjectConfig): IrAnalysisSnapshot {
        val inputDirs = (config.sources[id]?.options?.get("inputDirs") as? List<*> ?: emptyList<Any>())
            .map { it.toString().trim() }
            .filter { it.isNotEmpty() }
        require(inputDirs.isNotEmpty()) { "ir-analysis source requires at least one inputDirs entry." }

        val nodesById = linkedMapOf<String, IrNodeSnapshot>()
        val edgeKeys = linkedSetOf<EdgeKey>()
        val designElementKeys = linkedSetOf<DesignElementKey>()
        val designElements = mutableListOf<DesignElementSnapshot>()

        inputDirs.forEach { inputDir ->
            val dir = File(inputDir)
            require(dir.exists() && dir.isDirectory) { "ir-analysis inputDir does not exist or is not a directory: $inputDir" }

            val nodesFile = File(dir, "nodes.json")
            val relsFile = File(dir, "rels.json")
            require(nodesFile.exists() && relsFile.exists()) {
                "ir-analysis inputDir is missing nodes.json or rels.json: $inputDir"
            }

            parseNodes(nodesFile).forEach { node ->
                nodesById.putIfAbsent(node.id, node)
            }
            parseEdges(relsFile).forEach { edge ->
                edgeKeys.add(EdgeKey(edge.fromId, edge.toId, edge.type, edge.label))
            }

            val designElementsFile = File(dir, "design-elements.json")
            if (designElementsFile.exists()) {
                parseDesignElements(designElementsFile).forEach { element ->
                    val key = DesignElementKey(element.tag, element.packageName, element.name)
                    if (designElementKeys.add(key)) {
                        designElements.add(element)
                    }
                }
            }
        }

        return IrAnalysisSnapshot(
            inputDirs = inputDirs,
            nodes = nodesById.values.toList(),
            edges = edgeKeys.map { key ->
                IrEdgeSnapshot(
                    fromId = key.fromId,
                    toId = key.toId,
                    type = key.type,
                    label = key.label,
                )
            },
            designElements = designElements,
        )
    }

    private fun parseNodes(file: File): List<IrNodeSnapshot> {
        val array = file.reader(Charsets.UTF_8).use { JsonParser.parseReader(it).asJsonArray }
        return array.mapNotNull { element ->
            val obj = element.asJsonObjectOrNull() ?: return@mapNotNull null
            val id = obj.stringValue("id").orEmpty().trim()
            if (id.isEmpty()) {
                return@mapNotNull null
            }
            val normalizedName = obj.stringValue("name").orEmpty().trim().ifBlank { shortNameForId(id) }
            val normalizedFullName = obj.stringValue("fullName").orEmpty().trim().ifBlank { id }
            val normalizedType = obj.stringValue("type").orEmpty().trim().ifBlank { "unknown" }
            IrNodeSnapshot(
                id = id,
                name = normalizedName,
                fullName = normalizedFullName,
                type = normalizedType,
            )
        }
    }

    private fun parseDesignElements(file: File): List<DesignElementSnapshot> {
        val array = file.reader(Charsets.UTF_8).use { JsonParser.parseReader(it).asJsonArray }
        return array.mapIndexed { index, element ->
            val obj = element.asJsonObjectOrNull()
                ?: throw IllegalArgumentException("design element at index $index must be an object")
            val tag = obj.stringValue("tag").orEmpty().trim()
            if (tag.isEmpty()) {
                throw IllegalArgumentException("design element at index $index must declare non-blank tag")
            }
            val packageName = obj.stringValue("package").orEmpty().trim()
            val name = obj.stringValue("name").orEmpty().trim()
            if (name.isEmpty()) {
                throw IllegalArgumentException("design element at index $index must declare non-blank name")
            }
            val context = "design element $tag $packageName $name"
            DesignElementSnapshot(
                tag = tag,
                packageName = packageName,
                name = name,
                description = obj.stringValue("description").orEmpty().trim(),
                aggregates = obj.stringList("aggregates"),
                artifacts = parseArtifacts(obj.jsonArrayOrNull("artifacts", context), context),
                persist = obj.booleanValue("persist"),
                eventName = obj.stringValue("eventName"),
                requestFields = parseDesignFields(obj.jsonArrayOrNull("fields", context), context, "fields"),
                responseFields = parseDesignFields(obj.jsonArrayOrNull("resultFields", context), context, "resultFields"),
            )
        }
    }

    private fun parseArtifacts(
        array: com.google.gson.JsonArray?,
        context: String,
    ): List<ArtifactSelectionModel> {
        if (array == null) {
            return emptyList()
        }
        return array.mapIndexed { index, element ->
            val obj = element.asJsonObjectOrNull()
                ?: throw IllegalArgumentException("$context artifacts[$index] must be an object")
            val family = obj.stringValue("family").orEmpty().trim()
            if (family.isEmpty()) {
                throw IllegalArgumentException("$context artifacts[$index] must declare non-blank family")
            }
            ArtifactSelectionModel(
                family = family,
                variant = obj.stringValue("variant").orEmpty().trim(),
            )
        }
    }

    private fun parseDesignFields(
        array: com.google.gson.JsonArray?,
        context: String,
        fieldName: String,
    ): List<DesignFieldSnapshot> {
        if (array == null) {
            return emptyList()
        }
        return array.mapIndexed { index, element ->
            val obj = element.asJsonObjectOrNull()
                ?: throw IllegalArgumentException("$context $fieldName[$index] must be an object")
            val name = obj.stringValue("name").orEmpty().trim()
            if (name.isEmpty()) {
                throw IllegalArgumentException("$context $fieldName[$index] must declare non-blank name")
            }
            val type = obj.stringValue("type").orEmpty().trim()
            if (type.isEmpty()) {
                throw IllegalArgumentException("$context $fieldName[$index] must declare non-blank type")
            }
            DesignFieldSnapshot(
                name = name,
                type = type,
                nullable = obj.booleanValue("nullable") ?: false,
                defaultValue = obj.stringValue("defaultValue"),
            )
        }
    }

    private fun parseEdges(file: File): List<IrEdgeSnapshot> {
        val array = file.reader(Charsets.UTF_8).use { JsonParser.parseReader(it).asJsonArray }
        return array.mapNotNull { element ->
            val obj = element.asJsonObjectOrNull() ?: return@mapNotNull null
            val fromId = obj.stringValue("fromId").orEmpty().trim()
            val toId = obj.stringValue("toId").orEmpty().trim()
            val type = obj.stringValue("type").orEmpty().trim()
            if (fromId.isEmpty() || toId.isEmpty() || type.isEmpty()) {
                return@mapNotNull null
            }
            IrEdgeSnapshot(
                fromId = fromId,
                toId = toId,
                type = type,
                label = obj.stringValue("label"),
            )
        }
    }

    private fun shortNameForId(id: String): String {
        val normalized = id.replace('$', '.')
        val byMethod = normalized.substringAfterLast("::", missingDelimiterValue = normalized)
        return byMethod.substringAfterLast('.')
    }

    private fun JsonElement.asJsonObjectOrNull() = if (isJsonObject) asJsonObject else null

    private fun com.google.gson.JsonObject.stringValue(name: String): String? {
        val element = get(name) ?: return null
        return if (element.isJsonPrimitive) element.asString else null
    }

    private fun com.google.gson.JsonObject.booleanValue(name: String): Boolean? {
        val element = get(name) ?: return null
        return if (element.isJsonPrimitive && element.asJsonPrimitive.isBoolean) element.asBoolean else null
    }

    private fun com.google.gson.JsonObject.jsonArrayOrNull(
        name: String,
        context: String,
    ): com.google.gson.JsonArray? {
        val element = get(name) ?: return null
        if (!element.isJsonArray) {
            throw IllegalArgumentException("$context field '$name' must be an array")
        }
        return element.asJsonArray
    }

    private fun com.google.gson.JsonObject.stringList(name: String): List<String> {
        val element = get(name) ?: return emptyList()
        val array = if (element.isJsonArray) element.asJsonArray else return emptyList()
        return array.mapNotNull { item ->
            if (!item.isJsonPrimitive) {
                return@mapNotNull null
            }
            item.asString.trim().takeIf { it.isNotEmpty() }
        }
    }
}

private data class EdgeKey(
    val fromId: String,
    val toId: String,
    val type: String,
    val label: String?,
)

private data class DesignElementKey(
    val tag: String,
    val packageName: String,
    val name: String,
)

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

    private val removedPublicFields = listOf("desc", "requestFields", "responseFields", "traits", "role", "scope", "entity")

    override fun collect(config: ProjectConfig): IrAnalysisSnapshot {
        val inputDirs = (config.sources[id]?.options?.get("inputDirs") as? List<*> ?: emptyList<Any>())
            .map { it.toString().trim() }
            .filter { it.isNotEmpty() }
        require(inputDirs.isNotEmpty()) { "ir-analysis source requires at least one inputDirs entry." }

        val nodesById = linkedMapOf<String, IrNodeSnapshot>()
        val edgeKeys = linkedSetOf<EdgeKey>()
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
                designElements.addAll(parseDesignElements(designElementsFile))
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
        val array = parseRequiredArray(file, "nodes")
        return array.mapIndexed { index, element ->
            val context = "ir-analysis nodes[${index}]"
            val obj = element.asJsonObjectOrNull()
                ?: throw IllegalArgumentException("$context must be an object")
            val id = obj.requiredString("id", context)
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
        val array = parseRequiredArray(file, "design-elements")
        return array.mapIndexed { index, element ->
            val obj = element.asJsonObjectOrNull()
                ?: throw IllegalArgumentException("design element at index $index must be an object")
            val tag = obj.requiredString("tag", "design element at index $index")
            val packageName = obj.optionalString("package", "design element $tag").orEmpty().trim()
            val name = obj.requiredString("name", "design element at index $index")
            rejectRemovedFields(obj, name)
            val context = "design element $tag $packageName $name"
            DesignElementSnapshot(
                tag = tag,
                packageName = packageName,
                name = name,
                description = obj.optionalString("description", context).orEmpty().trim(),
                aggregates = obj.stringList("aggregates", context),
                artifacts = parseArtifacts(obj.jsonArrayOrNull("artifacts", context), context),
                artifactsDeclared = obj.has("artifacts"),
                persist = obj.optionalBoolean("persist", context),
                eventName = obj.optionalString("eventName", context),
                fields = parseDesignFields(obj.jsonArrayOrNull("fields", context), context, "fields"),
                resultFields = parseDesignFields(obj.jsonArrayOrNull("resultFields", context), context, "resultFields"),
            )
        }
    }

    private fun rejectRemovedFields(obj: com.google.gson.JsonObject, name: String) {
        val removed = removedPublicFields.filter { obj.has(it) }
        require(removed.isEmpty()) {
            "design element $name uses removed fields: ${removed.joinToString(", ")}"
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
            ArtifactSelectionModel(
                family = obj.requiredString("family", "$context artifacts[$index]"),
                variant = obj.optionalString("variant", "$context artifacts[$index]").orEmpty().trim(),
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
            DesignFieldSnapshot(
                name = obj.requiredString("name", "$context $fieldName[$index]"),
                type = obj.requiredString("type", "$context $fieldName[$index]"),
                nullable = obj.optionalBoolean("nullable", "$context $fieldName[$index]") ?: false,
                defaultValue = obj.optionalString("defaultValue", "$context $fieldName[$index]"),
            )
        }
    }

    private fun parseEdges(file: File): List<IrEdgeSnapshot> {
        val array = parseRequiredArray(file, "rels")
        return array.mapIndexed { index, element ->
            val context = "ir-analysis rels[${index}]"
            val obj = element.asJsonObjectOrNull()
                ?: throw IllegalArgumentException("$context must be an object")
            val fromId = obj.requiredString("fromId", context)
            val toId = obj.requiredString("toId", context)
            val type = obj.requiredString("type", context)
            IrEdgeSnapshot(
                fromId = fromId,
                toId = toId,
                type = type,
                label = obj.stringValue("label"),
            )
        }
    }

    private fun parseRequiredArray(file: File, label: String): com.google.gson.JsonArray {
        val root = file.reader(Charsets.UTF_8).use { JsonParser.parseReader(it) }
        require(root.isJsonArray) {
            "ir-analysis $label file ${file.path} root must be an array"
        }
        return root.asJsonArray
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

    private fun com.google.gson.JsonObject.requiredString(
        name: String,
        context: String,
    ): String {
        val value = optionalString(name, context)
        if (value.isNullOrBlank()) {
            throw IllegalArgumentException("$context must declare non-blank $name")
        }
        return value.trim()
    }

    private fun com.google.gson.JsonObject.optionalString(
        name: String,
        context: String,
    ): String? {
        val element = get(name) ?: return null
        if (!element.isJsonPrimitive || !element.asJsonPrimitive.isString) {
            throw IllegalArgumentException("$context field '$name' must be a string")
        }
        return element.asString
    }

    private fun com.google.gson.JsonObject.optionalBoolean(
        name: String,
        context: String,
    ): Boolean? {
        val element = get(name) ?: return null
        if (!element.isJsonPrimitive || !element.asJsonPrimitive.isBoolean) {
            throw IllegalArgumentException("$context field '$name' must be a boolean")
        }
        return element.asBoolean
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

    private fun com.google.gson.JsonObject.stringList(name: String, context: String): List<String> {
        val element = get(name) ?: return emptyList()
        if (!element.isJsonArray) {
            throw IllegalArgumentException("$context field '$name' must be an array")
        }
        return element.asJsonArray.mapIndexed { index, item ->
            if (!item.isJsonPrimitive || !item.asJsonPrimitive.isString) {
                throw IllegalArgumentException("$context $name[$index] must be a non-blank string")
            }
            item.asString.trim().also { value ->
                if (value.isEmpty()) {
                    throw IllegalArgumentException("$context $name[$index] must be a non-blank string")
                }
            }
        }
    }
}

private data class EdgeKey(
    val fromId: String,
    val toId: String,
    val type: String,
    val label: String?,
)


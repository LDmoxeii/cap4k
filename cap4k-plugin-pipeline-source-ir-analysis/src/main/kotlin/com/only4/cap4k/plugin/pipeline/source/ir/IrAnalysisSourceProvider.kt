package com.only4.cap4k.plugin.pipeline.source.ir

import com.google.gson.JsonParser
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
        )
    }

    private fun parseNodes(file: File): List<IrNodeSnapshot> {
        val array = file.reader(Charsets.UTF_8).use { JsonParser.parseReader(it).asJsonArray }
        return array.mapNotNull { element ->
            val obj = element.asJsonObject
            val id = obj["id"]?.asString?.trim().orEmpty()
            if (id.isEmpty()) {
                return@mapNotNull null
            }
            val normalizedName = obj["name"]?.asString?.trim().orEmpty().ifBlank { shortNameForId(id) }
            val normalizedFullName = obj["fullName"]?.asString?.trim().orEmpty().ifBlank { id }
            val normalizedType = obj["type"]?.asString?.trim().orEmpty().ifBlank { "unknown" }
            IrNodeSnapshot(
                id = id,
                name = normalizedName,
                fullName = normalizedFullName,
                type = normalizedType,
            )
        }
    }

    private fun parseEdges(file: File): List<IrEdgeSnapshot> {
        val array = file.reader(Charsets.UTF_8).use { JsonParser.parseReader(it).asJsonArray }
        return array.mapNotNull { element ->
            val obj = element.asJsonObject
            val fromId = obj["fromId"]?.asString?.trim().orEmpty()
            val toId = obj["toId"]?.asString?.trim().orEmpty()
            val type = obj["type"]?.asString?.trim().orEmpty()
            if (fromId.isEmpty() || toId.isEmpty() || type.isEmpty()) {
                return@mapNotNull null
            }
            IrEdgeSnapshot(
                fromId = fromId,
                toId = toId,
                type = type,
                label = obj["label"]?.asString,
            )
        }
    }

    private fun shortNameForId(id: String): String {
        val normalized = id.replace('$', '.')
        val byMethod = normalized.substringAfterLast("::", missingDelimiterValue = normalized)
        return byMethod.substringAfterLast('.')
    }
}

private data class EdgeKey(
    val fromId: String,
    val toId: String,
    val type: String,
    val label: String?,
)

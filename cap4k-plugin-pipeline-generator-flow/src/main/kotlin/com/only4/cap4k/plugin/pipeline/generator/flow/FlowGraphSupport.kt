package com.only4.cap4k.plugin.pipeline.generator.flow

import com.google.gson.GsonBuilder
import com.only4.cap4k.plugin.pipeline.api.AnalysisEdgeModel
import com.only4.cap4k.plugin.pipeline.api.AnalysisGraphModel
import com.only4.cap4k.plugin.pipeline.api.AnalysisNodeModel
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

internal data class PlannedFlowEntry(
    val slug: String,
    val jsonContent: String,
    val mermaidText: String,
    val indexEntry: FlowIndexEntryPayload,
)

internal data class PlannedFlowSet(
    val entries: List<PlannedFlowEntry>,
    val indexJsonContent: String,
)

internal data class FlowIndexEntryPayload(
    val entryId: String,
    val entryType: String,
    val nodeCount: Int,
    val edgeCount: Int,
    val json: String,
    val mermaid: String,
)

private data class FlowNodePayload(
    val id: String,
    val name: String,
    val fullName: String,
    val type: String,
)

private data class FlowEdgePayload(
    val fromId: String,
    val toId: String,
    val type: String,
    val label: String?,
)

private data class FlowEntryPayload(
    val entryId: String,
    val entryType: String,
    val nodeCount: Int,
    val edgeCount: Int,
    val nodes: List<FlowNodePayload>,
    val edges: List<FlowEdgePayload>,
)

private data class FlowIndexPayload(
    val generatedAt: String,
    val inputDirs: List<String>,
    val entryTypes: List<String>,
    val entryTypeCounts: Map<String, Int>,
    val nodeCount: Int,
    val edgeCount: Int,
    val flowCount: Int,
    val flows: List<FlowIndexEntryPayload>,
)

private data class FlowGraph(
    val nodes: List<AnalysisNodeModel>,
    val edges: List<AnalysisEdgeModel>,
)

private data class EdgeKey(
    val fromId: String,
    val toId: String,
    val type: String,
    val label: String?,
)

private val gson = GsonBuilder()
    .setPrettyPrinting()
    .disableHtmlEscaping()
    .create()

private val allowedEdgeTypes = setOf(
    "ControllerMethodToCommand",
    "ControllerMethodToQuery",
    "ControllerMethodToCli",
    "CommandSenderMethodToCommand",
    "QuerySenderMethodToQuery",
    "CliSenderMethodToCli",
    "ValidatorToQuery",
    "CommandToCommandHandler",
    "QueryToQueryHandler",
    "CliToCliHandler",
    "CommandHandlerToEntityMethod",
    "EntityMethodToEntityMethod",
    "EntityMethodToDomainEvent",
    "DomainEventToHandler",
    "DomainEventHandlerToCommand",
    "DomainEventHandlerToQuery",
    "DomainEventHandlerToCli",
    "DomainEventToIntegrationEvent",
    "IntegrationEventToHandler",
    "IntegrationEventHandlerToCommand",
    "IntegrationEventHandlerToQuery",
    "IntegrationEventHandlerToCli",
)

private val entryNodeTypes = setOf(
    "controllermethod",
    "commandsendermethod",
    "querysendermethod",
    "clisendermethod",
    "validator",
    "integrationevent",
)

internal fun buildPlannedFlows(graph: AnalysisGraphModel): PlannedFlowSet {
    val nodesById = linkedMapOf<String, AnalysisNodeModel>()
    graph.nodes.forEach { node ->
        nodesById.putIfAbsent(node.id, node)
    }

    val edges = graph.edges
        .filter { it.type in allowedEdgeTypes }
        .distinctBy { EdgeKey(it.fromId, it.toId, it.type, it.label) }
    val adjacency = edges.groupBy { it.fromId }
    val entryNodes = graph.nodes
        .filter { it.type.lowercase() in entryNodeTypes }
        .sortedBy { it.id }

    val usedSlugs = linkedSetOf<String>()
    val plannedEntries = entryNodes.map { entry ->
        val flowGraph = collectFlow(entry.id, nodesById, adjacency)
        val slug = slugify(entry.id, usedSlugs)
        val entryType = if (entry.id == "<anonymous>") "<anonymous>" else entry.type
        val payload = FlowEntryPayload(
            entryId = entry.id,
            entryType = entryType,
            nodeCount = flowGraph.nodes.size,
            edgeCount = flowGraph.edges.size,
            nodes = flowGraph.nodes.map { FlowNodePayload(it.id, it.name, it.fullName, it.type) },
            edges = flowGraph.edges.map { FlowEdgePayload(it.fromId, it.toId, it.type, it.label) },
        )

        PlannedFlowEntry(
            slug = slug,
            jsonContent = gson.toJson(payload),
            mermaidText = renderMermaid(flowGraph.nodes, flowGraph.edges),
            indexEntry = FlowIndexEntryPayload(
                entryId = payload.entryId,
                entryType = payload.entryType,
                nodeCount = payload.nodeCount,
                edgeCount = payload.edgeCount,
                json = "$slug.json",
                mermaid = "$slug.mmd",
            ),
        )
    }

    val entryTypeCounts = linkedMapOf<String, Int>()
    plannedEntries.forEach { entry ->
        entryTypeCounts[entry.indexEntry.entryType] = (entryTypeCounts[entry.indexEntry.entryType] ?: 0) + 1
    }

    return PlannedFlowSet(
        entries = plannedEntries,
        indexJsonContent = gson.toJson(
            FlowIndexPayload(
                generatedAt = OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.SECONDS).toString(),
                inputDirs = graph.inputDirs,
                entryTypes = entryTypeCounts.keys.sorted(),
                entryTypeCounts = entryTypeCounts.toSortedMap(),
                nodeCount = nodesById.size,
                edgeCount = edges.size,
                flowCount = plannedEntries.size,
                flows = plannedEntries.map { it.indexEntry },
            ),
        ),
    )
}

private fun collectFlow(
    entryId: String,
    nodesById: Map<String, AnalysisNodeModel>,
    adjacency: Map<String, List<AnalysisEdgeModel>>,
): FlowGraph {
    val visitedNodes = linkedSetOf(entryId)
    val visitedEdges = linkedSetOf<EdgeKey>()
    val stack = ArrayDeque<String>()
    stack.add(entryId)

    while (stack.isNotEmpty()) {
        val current = stack.removeLast()
        adjacency[current].orEmpty().forEach { edge ->
            val edgeKey = EdgeKey(edge.fromId, edge.toId, edge.type, edge.label)
            if (!visitedEdges.add(edgeKey)) {
                return@forEach
            }
            if (visitedNodes.add(edge.toId)) {
                stack.add(edge.toId)
            }
        }
    }

    val nodes = visitedNodes.map { nodeId ->
        nodesById[nodeId] ?: AnalysisNodeModel(
            id = nodeId,
            name = shortName(nodeId),
            fullName = nodeId,
            type = "unknown",
        )
    }.sortedBy { it.id }

    val edges = visitedEdges.map { edge ->
        AnalysisEdgeModel(
            fromId = edge.fromId,
            toId = edge.toId,
            type = edge.type,
            label = edge.label,
        )
    }.sortedWith(compareBy<AnalysisEdgeModel> { it.fromId }.thenBy { it.toId }.thenBy { it.type })

    return FlowGraph(nodes = nodes, edges = edges)
}

private fun renderMermaid(nodes: List<AnalysisNodeModel>, edges: List<AnalysisEdgeModel>): String {
    val idMap = linkedMapOf<String, String>()
    val lines = mutableListOf("flowchart TD")

    nodes.forEachIndexed { index, node ->
        val localId = "N${index + 1}"
        idMap[node.id] = localId
        lines.add("  $localId[${sanitize(node.name.ifBlank { node.id })}]")
    }

    edges.forEach { edge ->
        val fromId = idMap[edge.fromId]
        val toId = idMap[edge.toId]
        if (fromId != null && toId != null) {
            lines.add("  $fromId -->|${sanitize(edge.type)}| $toId")
        }
    }

    return lines.joinToString("\n", postfix = "\n")
}

private fun shortName(id: String): String =
    id.substringAfterLast("::", id).substringAfterLast('.')

private fun sanitize(text: String): String =
    text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "'")
        .replace("\n", " ")
        .replace("\r", " ")

private fun slugify(text: String, used: MutableSet<String>): String {
    var slug = text.replace(Regex("[^A-Za-z0-9]+"), "_").trim('_')
    if (slug.isEmpty()) {
        slug = "entry"
    }
    slug = slug.take(80)

    if (used.contains(slug)) {
        val digest = MessageDigest.getInstance("MD5")
            .digest(text.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        slug = "${slug}_${digest.take(8)}"
    }

    used.add(slug)
    return slug
}

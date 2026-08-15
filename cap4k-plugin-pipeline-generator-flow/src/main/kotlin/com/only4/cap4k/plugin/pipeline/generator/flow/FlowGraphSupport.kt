package com.only4.cap4k.plugin.pipeline.generator.flow

import com.only4.cap4k.plugin.pipeline.json.PipelineJson
import com.only4.cap4k.plugin.pipeline.api.AnalysisEdgeModel
import com.only4.cap4k.plugin.pipeline.api.AnalysisGraphModel
import com.only4.cap4k.plugin.pipeline.api.AnalysisNodeModel
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

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

internal data class FlowProjectionEvidence(
    val projectedEdge: AnalysisEdgeModel,
    val rawPath: List<AnalysisEdgeModel>,
)

internal data class CausalProjection(
    val edges: List<AnalysisEdgeModel>,
    val entryNodeIds: Set<String>,
    val evidence: List<FlowProjectionEvidence>,
)

private data class HiddenPathState(
    val nodeId: String,
    val rawPath: List<AnalysisEdgeModel>,
)

private val flowJsonMapper = PipelineJson.newMapper()
private val flowJsonWriter = PipelineJson.prettyWriter(flowJsonMapper)

private const val CommandToCommandHandler = "CommandToCommandHandler"
private const val CommandHandlerToEntityMethod = "CommandHandlerToEntityMethod"
private const val CommandToEntityMethod = "CommandToEntityMethod"
private const val EntityMethodToEntityMethod = "EntityMethodToEntityMethod"
private const val EntityMethodToDomainEvent = "EntityMethodToDomainEvent"
private const val DomainEventToHandler = "DomainEventToHandler"
private const val DomainEventHandlerToCommand = "DomainEventHandlerToCommand"
private const val DomainEventToIntegrationEvent = "DomainEventToIntegrationEvent"
private const val IntegrationEventToHandler = "IntegrationEventToHandler"
private const val IntegrationEventHandlerToCommand = "IntegrationEventHandlerToCommand"

private val rawCausalEdgeTypes = setOf(
    CommandToCommandHandler,
    CommandHandlerToEntityMethod,
    CommandToEntityMethod,
    EntityMethodToEntityMethod,
    EntityMethodToDomainEvent,
    DomainEventToHandler,
    DomainEventHandlerToCommand,
    DomainEventToIntegrationEvent,
    IntegrationEventToHandler,
    IntegrationEventHandlerToCommand,
)

private val visibleBusinessNodeTypes = setOf(
    "command",
    "domainevent",
    "integrationevent",
)

private val hiddenCausalNodeTypes = setOf(
    "commandhandler",
    "domaineventhandler",
    "integrationeventhandler",
    "entitymethod",
)

private val causalEdgeComparator = compareBy<AnalysisEdgeModel> { it.fromId }
    .thenBy { it.toId }
    .thenBy { it.type }
    .thenBy { it.label.orEmpty() }

internal fun buildPlannedFlows(graph: AnalysisGraphModel): PlannedFlowSet {
    val nodesById = linkedMapOf<String, AnalysisNodeModel>()
    graph.nodes
        .sortedBy(AnalysisNodeModel::id)
        .forEach { node ->
            nodesById.putIfAbsent(node.id, node)
        }

    val projection = projectCausalGraph(nodesById, graph.edges)
    val edges = projection.edges
    val adjacency = edges.groupBy { it.fromId }
    val entryNodes = selectRootEntryNodes(nodesById, projection.entryNodeIds, edges)

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
            jsonContent = flowJsonWriter.writeValueAsString(payload),
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
        indexJsonContent = flowJsonWriter.writeValueAsString(
            FlowIndexPayload(
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

private fun selectRootEntryNodes(
    nodesById: Map<String, AnalysisNodeModel>,
    entryNodeIds: Set<String>,
    edges: List<AnalysisEdgeModel>,
): List<AnalysisNodeModel> {
    val nodesWithUpstream = edges
        .map { it.toId }
        .toSet()
    val nodesWithOutgoing = edges
        .map { it.fromId }
        .toSet()

    return entryNodeIds
        .asSequence()
        .filterNot { it in nodesWithUpstream }
        .filter { it in nodesWithOutgoing }
        .map { entryId -> requireNotNull(nodesById[entryId]) }
        .sortedBy { it.id }
        .toList()
}

internal fun projectCausalGraph(
    nodesById: Map<String, AnalysisNodeModel>,
    edges: List<AnalysisEdgeModel>,
): CausalProjection {
    edges.filter(::isPotentialCausalRelationshipType).forEach { edge ->
        require(edge.fromId in nodesById) {
            "Flow causal relationship '${edge.type}' references missing fromId '${edge.fromId}'"
        }
        require(edge.toId in nodesById) {
            "Flow causal relationship '${edge.type}' references missing toId '${edge.toId}'"
        }
    }

    val rawEdges = edges
        .filter { edge -> isCausalEdge(edge, nodesById) }
        .distinctBy { EdgeKey(it.fromId, it.toId, it.type, it.label) }
        .sortedWith(causalEdgeComparator)

    rawEdges.forEach { edge ->
        require(edge.fromId in nodesById) {
            "Flow causal relationship '${edge.type}' references missing fromId '${edge.fromId}'"
        }
        require(edge.toId in nodesById) {
            "Flow causal relationship '${edge.type}' references missing toId '${edge.toId}'"
        }
    }

    val outgoingByNode = rawEdges.groupBy(AnalysisEdgeModel::fromId)
    val entryNodeIds = nodesById.values
        .asSequence()
        .filter { node ->
            node.type.lowercase() == "integrationevent" ||
                isConcreteCommandEntry(node, nodesById, outgoingByNode[node.id].orEmpty())
        }
        .map(AnalysisNodeModel::id)
        .toSortedSet()
    val visibleNodeIds = nodesById.values
        .asSequence()
        .filter { node -> node.id in entryNodeIds || node.type.lowercase() in visibleBusinessNodeTypes }
        .map(AnalysisNodeModel::id)
        .toSet()

    val evidenceByProjectedEdge = linkedMapOf<EdgeKey, FlowProjectionEvidence>()
    visibleNodeIds.sorted().forEach { sourceId ->
        val sourceNode = requireNotNull(nodesById[sourceId])
        val hiddenQueue = ArrayDeque<HiddenPathState>()
        val visitedHiddenNodes = linkedSetOf<String>()

        fun acceptPath(path: List<AnalysisEdgeModel>) {
            val terminalEdge = path.last()
            val targetNode = requireNotNull(nodesById[terminalEdge.toId])
            when {
                targetNode.id in visibleNodeIds -> {
                    val projectedEdge = if (path.size == 1) {
                        terminalEdge
                    } else {
                        AnalysisEdgeModel(
                            fromId = sourceNode.id,
                            toId = targetNode.id,
                            type = projectedEdgeType(sourceNode, targetNode),
                            label = path.asReversed().firstNotNullOfOrNull(AnalysisEdgeModel::label),
                        )
                    }
                    val key = EdgeKey(
                        projectedEdge.fromId,
                        projectedEdge.toId,
                        projectedEdge.type,
                        projectedEdge.label,
                    )
                    evidenceByProjectedEdge.putIfAbsent(
                        key,
                        FlowProjectionEvidence(projectedEdge = projectedEdge, rawPath = path),
                    )
                }

                targetNode.type.lowercase() in hiddenCausalNodeTypes && visitedHiddenNodes.add(targetNode.id) -> {
                    hiddenQueue.addLast(HiddenPathState(targetNode.id, path))
                }
            }
        }

        outgoingByNode[sourceId].orEmpty().forEach { edge -> acceptPath(listOf(edge)) }
        while (hiddenQueue.isNotEmpty()) {
            val state = hiddenQueue.removeFirst()
            outgoingByNode[state.nodeId].orEmpty().forEach { edge ->
                acceptPath(state.rawPath + edge)
            }
        }
    }

    val evidence = evidenceByProjectedEdge.values
        .sortedWith(compareBy<FlowProjectionEvidence> { it.projectedEdge.fromId }
            .thenBy { it.projectedEdge.toId }
            .thenBy { it.projectedEdge.type }
            .thenBy { it.projectedEdge.label.orEmpty() })
    return CausalProjection(
        edges = evidence.map(FlowProjectionEvidence::projectedEdge),
        entryNodeIds = entryNodeIds,
        evidence = evidence,
    )
}

private fun isPotentialCausalRelationshipType(edge: AnalysisEdgeModel): Boolean =
    edge.type in rawCausalEdgeTypes || edge.type.endsWith("ToCommand")

private fun isCausalEdge(
    edge: AnalysisEdgeModel,
    nodesById: Map<String, AnalysisNodeModel>,
): Boolean {
    if (edge.type in rawCausalEdgeTypes) {
        return true
    }
    return isExplicitCommandTriggerRelationship(edge, nodesById)
}

private fun isConcreteCommandEntry(
    node: AnalysisNodeModel,
    nodesById: Map<String, AnalysisNodeModel>,
    outgoing: List<AnalysisEdgeModel>,
): Boolean = isPotentialEntryNode(node) && outgoing.any { edge ->
    isExplicitCommandTriggerRelationship(edge, nodesById, node)
}

private fun isExplicitCommandTriggerRelationship(
    edge: AnalysisEdgeModel,
    nodesById: Map<String, AnalysisNodeModel>,
    sourceNode: AnalysisNodeModel? = nodesById[edge.fromId],
): Boolean {
    if (!edge.type.endsWith("ToCommand")) {
        return false
    }
    val source = sourceNode ?: return false
    val target = nodesById[edge.toId] ?: return false
    if (!isPotentialEntryNode(source) || target.type.lowercase() != "command") {
        return false
    }

    val relationshipPrefix = edge.type.removeSuffix("ToCommand")
    return relationshipPrefix.isNotBlank() &&
        canonicalRole(relationshipPrefix) == canonicalRole(source.type)
}

private fun canonicalRole(value: String): String = value
    .asSequence()
    .filter(Char::isLetterOrDigit)
    .joinToString("")
    .lowercase()

private fun isPotentialEntryNode(node: AnalysisNodeModel): Boolean {
    val type = canonicalRole(node.type)
    return type.isNotBlank() &&
        "sender" !in type &&
        type !in visibleBusinessNodeTypes &&
        type !in hiddenCausalNodeTypes &&
        type !in excludedEntryNodeTypes
}

private val excludedEntryNodeTypes = setOf(
    "aggregate",
    "query",
    "queryhandler",
    "capability",
    "capabilityhandler",
    "validator",
)

private fun projectedEdgeType(
    from: AnalysisNodeModel,
    to: AnalysisNodeModel,
): String = "${projectionRole(from)}To${projectionRole(to)}"

private fun projectionRole(node: AnalysisNodeModel): String =
    when (node.type.lowercase()) {
        "command" -> "Command"
        "domainevent" -> "DomainEvent"
        "integrationevent" -> "IntegrationEvent"
        else -> node.type
            .split(Regex("[^A-Za-z0-9]+"))
            .filter(String::isNotBlank)
            .joinToString("") { token -> token.replaceFirstChar(Char::uppercaseChar) }
            .ifBlank { "Entry" }
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

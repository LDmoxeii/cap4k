package com.only4.cap4k.plugin.pipeline.source.ir

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.only4.cap4k.plugin.pipeline.api.AggregateElementSnapshot
import com.only4.cap4k.plugin.pipeline.api.ArtifactSelectionModel
import com.only4.cap4k.plugin.pipeline.api.DesignElementSnapshot
import com.only4.cap4k.plugin.pipeline.api.AgentSnapshotStatus
import com.only4.cap4k.plugin.pipeline.api.AnalyzerAggregateStructurePartition
import com.only4.cap4k.plugin.pipeline.api.AnalyzerDesignProjectionPartition
import com.only4.cap4k.plugin.pipeline.api.AnalyzerGraphPartition
import com.only4.cap4k.plugin.pipeline.api.AnalyzerPartitionDiagnostic
import com.only4.cap4k.plugin.pipeline.api.AnalyzerSnapshot
import com.only4.cap4k.plugin.pipeline.api.AnalyzerSourceIdentity
import com.only4.cap4k.plugin.pipeline.api.analyzerSourceIdentity
import com.only4.cap4k.plugin.pipeline.api.IrEdgeSnapshot
import com.only4.cap4k.plugin.pipeline.api.IrNodeSnapshot
import com.only4.cap4k.plugin.pipeline.api.PipelineBoundaryAuthorities
import com.only4.cap4k.plugin.pipeline.api.PipelineBoundaryKind
import com.only4.cap4k.plugin.pipeline.api.PipelineCapabilityBoundary
import com.only4.cap4k.plugin.pipeline.api.PipelineCapabilityDescriptor
import com.only4.cap4k.plugin.pipeline.api.PipelineCapabilityKind
import com.only4.cap4k.plugin.pipeline.api.PipelineExecutionLane
import com.only4.cap4k.plugin.pipeline.api.PipelineInputRequirement
import com.only4.cap4k.plugin.pipeline.api.PipelinePublicTasks
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig
import com.only4.cap4k.plugin.pipeline.api.SemanticFieldSnapshot
import com.only4.cap4k.plugin.pipeline.api.SourceProvider
import com.only4.cap4k.plugin.pipeline.json.PipelineJson
import java.io.File
import java.security.MessageDigest

class IrAnalysisSourceProvider : SourceProvider {
    private val objectMapper = PipelineJson.newMapper()
    override val id: String = "ir-analysis"
    override val descriptor: PipelineCapabilityDescriptor = PipelineCapabilityDescriptor.builtIn(
        providerId = id,
        displayName = "IR Analysis Source",
        kind = PipelineCapabilityKind.SOURCE,
        module = "cap4k-plugin-pipeline-source-ir-analysis",
        tacticalCarriers = listOf(
            "Raw Analysis Graph Evidence",
            "Normalized Design Projection Evidence",
            "Aggregate Structure Evidence",
        ),
        executionLanes = listOf(PipelineExecutionLane.ANALYSIS),
        tasks = listOf(PipelinePublicTasks.ANALYSIS_PLAN, PipelinePublicTasks.ANALYSIS_GENERATE),
        inputRequirements = listOf(
            PipelineInputRequirement(
                id = "ir-analysis-input",
                configurationPaths = listOf("sources.ir-analysis.inputDirs"),
            ),
        ),
        boundaries = listOf(
            PipelineCapabilityBoundary(PipelineBoundaryKind.INPUT, PipelineBoundaryAuthorities.PROJECT_INPUT),
            PipelineCapabilityBoundary(PipelineBoundaryKind.GENERATION, PipelineBoundaryAuthorities.PIPELINE_SOURCE),
            PipelineCapabilityBoundary(PipelineBoundaryKind.ANALYZER, PipelineBoundaryAuthorities.ANALYZER_OBSERVATION),
        ),
    )

    private val removedPublicFields = listOf("desc", "requestFields", "responseFields", "traits", "role", "scope", "entity")
    private val supportedAggregateElementTypes = setOf(
        "schema",
        "entity",
        "repository",
        "factory",
        "strong-id",
        "projection",
    )

    override fun localInputPaths(config: ProjectConfig): List<String> =
        (config.sources[id]?.options?.get("inputDirs") as? List<*> ?: emptyList<Any>())
            .map { it.toString().trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .sorted()

    override fun collect(config: ProjectConfig): AnalyzerSnapshot {
        val inputDirs = localInputPaths(config)
        require(inputDirs.isNotEmpty()) { "ir-analysis source requires at least one inputDirs entry." }

        val projectDir = config.sources[id]?.options?.get("projectDir")?.toString()
        val sources = inputDirs.map { inputDir -> analyzerSourceIdentity(inputDir, projectDir) }
        val nodesById = linkedMapOf<String, IrNodeSnapshot>()
        val nodeSources = linkedMapOf<String, String>()
        val edgeKeys = linkedSetOf<EdgeKey>()
        val edgeSources = linkedMapOf<EdgeKey, String>()
        val designElements = mutableListOf<DesignElementSnapshot>()
        val designElementSources = linkedMapOf<String, MutableSet<String>>()
        val aggregateElementsByCarrier = linkedMapOf<String, AggregateElementSnapshot>()
        val graphDiagnostics = mutableListOf<AnalyzerPartitionDiagnostic>()
        val designDiagnostics = mutableListOf<AnalyzerPartitionDiagnostic>()
        val aggregateDiagnostics = mutableListOf<AnalyzerPartitionDiagnostic>()

        sources.forEach { source ->
            val dir = File(source.inputDir)
            if (!dir.exists() || !dir.isDirectory) {
                listOf(
                    AnalyzerPartitionIdAndDiagnostics("graph", graphDiagnostics),
                    AnalyzerPartitionIdAndDiagnostics("design-projection", designDiagnostics),
                    AnalyzerPartitionIdAndDiagnostics("aggregate-structure", aggregateDiagnostics),
                ).forEach { target ->
                    target.diagnostics += diagnostic(
                        partitionId = target.partitionId,
                        source = source,
                        code = "input-dir-unavailable",
                        message = "Analysis input directory is missing or is not a directory.",
                    )
                }
                return@forEach
            }

            val nodesFile = File(dir, "nodes.json")
            val relsFile = File(dir, "rels.json")
            val inputNodes = parsePartitionFile(
                partitionId = "graph",
                source = source,
                file = nodesFile,
                required = true,
                diagnostics = graphDiagnostics,
                parser = ::parseNodes,
            ).orEmpty()
            val inputEdges = parsePartitionFile(
                partitionId = "graph",
                source = source,
                file = relsFile,
                required = true,
                diagnostics = graphDiagnostics,
                parser = ::parseEdges,
            ).orEmpty()

            val designElementsFile = File(dir, "design-elements.json")
            val inputDesignElements = parsePartitionFile(
                partitionId = "design-projection",
                source = source,
                file = designElementsFile,
                required = false,
                diagnostics = designDiagnostics,
                parser = ::parseDesignElements,
            ).orEmpty()

            val aggregateElementsFile = File(dir, "aggregate-elements.json")
            val inputAggregateElements = parsePartitionFile(
                partitionId = "aggregate-structure",
                source = source,
                file = aggregateElementsFile,
                required = true,
                diagnostics = aggregateDiagnostics,
                parser = ::parseAggregateElements,
            ).orEmpty()

            inputNodes.forEach { node ->
                val existing = nodesById[node.id]
                if (existing == null) {
                    nodesById[node.id] = node
                    nodeSources[node.id] = source.id
                } else {
                    if (existing.name != node.name || existing.fullName != node.fullName || existing.type != node.type) {
                        graphDiagnostics += diagnostic(
                            partitionId = "graph",
                            source = source,
                            code = "node-identity-conflict",
                            message = "Node ${node.id} conflicts with source ${nodeSources[node.id]}.",
                        )
                    }
                    val metadataOwners = listOfNotNull(existing.metadataOwner, node.metadataOwner).distinct()
                    if (metadataOwners.size > 1) {
                        graphDiagnostics += diagnostic(
                            partitionId = "graph",
                            source = source,
                            code = "metadata-owner-conflict",
                            message = "Node ${node.id} declares conflicting metadata owners: ${metadataOwners.joinToString()}.",
                        )
                    }
                    nodesById[node.id] = existing.copy(
                        missingMetadata = (existing.missingMetadata + node.missingMetadata).distinct().sorted(),
                        metadataOwner = metadataOwners.singleOrNull(),
                    )
                }
            }
            inputEdges.forEach { edge ->
                val key = EdgeKey(edge.fromId, edge.toId, edge.type, edge.label)
                edgeKeys += key
                edgeSources.putIfAbsent(key, source.id)
            }

            val designCandidates = inputNodes.filter { node ->
                node.type.lowercase() in DRAWING_BOARD_CANDIDATE_NODE_TYPES
            }
            if (!designElementsFile.exists() && designCandidates.isNotEmpty()) {
                designDiagnostics += diagnostic(
                    partitionId = "design-projection",
                    source = source,
                    code = "missing-design-sidecar",
                    message = "design-elements.json is missing while ${designCandidates.size} design candidate(s) were observed.",
                )
            } else if (inputDesignElements.isEmpty() && designCandidates.isNotEmpty()) {
                designDiagnostics += diagnostic(
                    partitionId = "design-projection",
                    source = source,
                    code = "empty-design-projection",
                    message = "Design projection is empty while ${designCandidates.size} design candidate(s) were observed.",
                )
            }
            inputNodes.forEach { node ->
                if (DESIGN_BLOCK_METADATA_FQ in node.missingMetadata) {
                    designDiagnostics += diagnostic(
                        partitionId = "design-projection",
                        source = source,
                        code = "missing-design-metadata",
                        message = "Symbol ${node.metadataOwner ?: node.fullName} is missing $DESIGN_BLOCK_METADATA_FQ. " +
                            "Restore the generated metadata or keep cap4k-analysis-metadata on the owning module compileOnly classpath.",
                    )
                }
                if (AGGREGATE_ELEMENT_METADATA_FQ in node.missingMetadata) {
                    aggregateDiagnostics += diagnostic(
                        partitionId = "aggregate-structure",
                        source = source,
                        code = "missing-aggregate-metadata",
                        message = "Symbol ${node.metadataOwner ?: node.fullName} is missing $AGGREGATE_ELEMENT_METADATA_FQ. " +
                            "Restore the generated metadata or keep cap4k-analysis-metadata on the owning module compileOnly classpath.",
                    )
                }
            }

            inputDesignElements.forEach { designElement ->
                val key = designElementIdentity(designElement)
                val conflictingFields = designElements
                    .filter { existing -> designElementIdentity(existing) == key }
                    .flatMap { existing -> designElementConflictFields(existing, designElement) }
                    .distinct()
                    .sorted()
                if (conflictingFields.isNotEmpty()) {
                    val relatedSources = designElementSources[key].orEmpty().sorted()
                    designDiagnostics += diagnostic(
                        partitionId = "design-projection",
                        source = source,
                        code = "design-block-conflict-${stableCodeSuffix(key)}",
                        message = "Design block $key conflicts on ${conflictingFields.joinToString()} between sources " +
                            "${(relatedSources + source.id).distinct().sorted().joinToString()}.",
                    )
                }
                designElementSources.getOrPut(key) { linkedSetOf() } += source.id
                designElements += designElement
            }
            inputAggregateElements.forEach { aggregateElement ->
                val existing = aggregateElementsByCarrier[aggregateElement.carrierQualifiedName]
                if (existing != null && existing != aggregateElement) {
                    aggregateDiagnostics += diagnostic(
                        partitionId = "aggregate-structure",
                        source = source,
                        code = "carrier-conflict",
                        message = "Aggregate carrier ${aggregateElement.carrierQualifiedName} has conflicting metadata.",
                    )
                } else {
                    aggregateElementsByCarrier.putIfAbsent(aggregateElement.carrierQualifiedName, aggregateElement)
                }
            }
        }

        val nodeIds = nodesById.keys
        edgeKeys.forEach { edge ->
            val missingEndpoints = listOf(edge.fromId, edge.toId).filterNot(nodeIds::contains)
            if (missingEndpoints.isNotEmpty()) {
                val source = sources.firstOrNull { it.id == edgeSources[edge] }
                graphDiagnostics += AnalyzerPartitionDiagnostic(
                    id = diagnosticId("graph", edgeSources[edge] ?: "merged", "missing-edge-endpoint"),
                    sourceId = edgeSources[edge],
                    message = "Relationship ${edge.fromId} -> ${edge.toId} references missing endpoint(s): ${missingEndpoints.joinToString()}.",
                )
            }
        }

        return AnalyzerSnapshot(
            graph = AnalyzerGraphPartition(
                status = partitionStatus(graphDiagnostics),
                sources = sources,
                diagnostics = graphDiagnostics.distinctBy(AnalyzerPartitionDiagnostic::id).sortedBy(AnalyzerPartitionDiagnostic::id),
                nodes = nodesById.values.sortedBy(IrNodeSnapshot::id),
                relationships = edgeKeys.map { edge ->
                    IrEdgeSnapshot(edge.fromId, edge.toId, edge.type, edge.label)
                }.sortedWith(compareBy({ it.fromId }, { it.toId }, { it.type }, { it.label.orEmpty() })),
            ),
            designProjection = AnalyzerDesignProjectionPartition(
                status = partitionStatus(designDiagnostics),
                sources = sources,
                diagnostics = designDiagnostics.distinctBy(AnalyzerPartitionDiagnostic::id).sortedBy(AnalyzerPartitionDiagnostic::id),
                designBlocks = designElements.sortedWith(compareBy({ it.tag }, { it.packageName }, { it.name })),
            ),
            aggregateStructure = AnalyzerAggregateStructurePartition(
                status = partitionStatus(aggregateDiagnostics),
                sources = sources,
                diagnostics = aggregateDiagnostics.distinctBy(AnalyzerPartitionDiagnostic::id).sortedBy(AnalyzerPartitionDiagnostic::id),
                aggregateElements = aggregateElementsByCarrier.values.sortedBy(AggregateElementSnapshot::carrierQualifiedName),
            ),
        )
    }

    private fun <T> parsePartitionFile(
        partitionId: String,
        source: AnalyzerSourceIdentity,
        file: File,
        required: Boolean,
        diagnostics: MutableList<AnalyzerPartitionDiagnostic>,
        parser: (File) -> List<T>,
    ): List<T>? {
        if (!file.exists()) {
            if (required) {
                diagnostics += diagnostic(
                    partitionId = partitionId,
                    source = source,
                    code = "missing-${file.nameWithoutExtension}",
                    message = "Required ${file.name} is missing.",
                )
            }
            return null
        }
        return try {
            parser(file)
        } catch (failure: Exception) {
            diagnostics += diagnostic(
                partitionId = partitionId,
                source = source,
                code = "invalid-${file.nameWithoutExtension}",
                message = failure.message.orEmpty()
                    .replace(source.inputDir, source.id)
                    .ifBlank { "${file.name} is invalid." },
            )
            null
        }
    }

    private fun partitionStatus(diagnostics: List<AnalyzerPartitionDiagnostic>): AgentSnapshotStatus =
        if (diagnostics.isEmpty()) AgentSnapshotStatus.OK else AgentSnapshotStatus.INVALID

    private fun designElementIdentity(element: DesignElementSnapshot): String =
        "${element.tag}|${element.packageName}|${element.name}"

    private fun designElementConflictFields(
        existing: DesignElementSnapshot,
        incoming: DesignElementSnapshot,
    ): List<String> = buildList {
        if (existing.description.isNotBlank() && incoming.description.isNotBlank() &&
            existing.description != incoming.description
        ) add("description")
        if (existing.aggregates.isNotEmpty() && incoming.aggregates.isNotEmpty() &&
            existing.aggregates != incoming.aggregates
        ) add("aggregates")
        if (existing.eventName?.isNotBlank() == true && incoming.eventName?.isNotBlank() == true &&
            existing.eventName != incoming.eventName
        ) add("eventName")
        if (existing.persist != null && incoming.persist != null && existing.persist != incoming.persist) add("persist")
        if (existing.fields.isNotEmpty() && incoming.fields.isNotEmpty() && existing.fields != incoming.fields) add("fields")
        if (existing.resultFields.isNotEmpty() && incoming.resultFields.isNotEmpty() &&
            existing.resultFields != incoming.resultFields
        ) add("resultFields")
        val existingVariants = existing.artifacts.groupBy(ArtifactSelectionModel::family)
        val incomingVariants = incoming.artifacts.groupBy(ArtifactSelectionModel::family)
        existingVariants.keys.intersect(incomingVariants.keys).sorted().forEach { family ->
            val first = existingVariants.getValue(family).map(ArtifactSelectionModel::variant).distinct()
            val second = incomingVariants.getValue(family).map(ArtifactSelectionModel::variant).distinct()
            if (first.isNotEmpty() && second.isNotEmpty() && first != second) add("artifacts.$family")
        }
    }

    private fun stableCodeSuffix(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte) }
        .take(12)

    private fun diagnostic(
        partitionId: String,
        source: AnalyzerSourceIdentity,
        code: String,
        message: String,
    ): AnalyzerPartitionDiagnostic = AnalyzerPartitionDiagnostic(
        id = diagnosticId(partitionId, source.id, code),
        sourceId = source.id,
        message = message,
    )

    private fun diagnosticId(partitionId: String, sourceId: String, code: String): String =
        "analyzer.$partitionId.$sourceId.$code"

    private fun parseNodes(file: File): List<IrNodeSnapshot> {
        val array = parseRequiredArray(file, "nodes")
        return array.mapIndexed { index, element ->
            val context = "ir-analysis nodes[${index}]"
            val obj = element.objectNodeOrNull()
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
                missingMetadata = obj.stringList("missingMetadata", context),
                metadataOwner = obj.stringValue("metadataOwner")?.trim()?.takeIf(String::isNotEmpty),
            )
        }
    }

    private fun parseDesignElements(file: File): List<DesignElementSnapshot> {
        val array = parseRequiredArray(file, "design-elements")
        return array.mapIndexed { index, element ->
            val obj = element.objectNodeOrNull()
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

    private fun parseAggregateElements(file: File): List<AggregateElementSnapshot> {
        val array = parseRequiredArray(file, "aggregate-elements")
        return array.mapIndexed { index, element ->
            val context = "aggregate element at index $index"
            val obj = element.objectNodeOrNull()
                ?: throw IllegalArgumentException("$context must be an object")
            val type = obj.requiredString("type", context)
            require(type in supportedAggregateElementTypes) {
                "$context has unsupported type: $type"
            }
            AggregateElementSnapshot(
                carrierQualifiedName = obj.requiredString("carrierQualifiedName", context),
                aggregate = obj.requiredString("aggregate", context),
                name = obj.optionalString("name", context).orEmpty().trim(),
                packageName = obj.optionalString("packageName", context).orEmpty().trim(),
                description = obj.optionalString("description", context).orEmpty().trim(),
                type = type,
                root = obj.optionalBoolean("root", context) ?: false,
            )
        }
    }

    private fun rejectRemovedFields(obj: ObjectNode, name: String) {
        val removed = removedPublicFields.filter { obj.has(it) }
        require(removed.isEmpty()) {
            "design element $name uses removed fields: ${removed.joinToString(", ")}"
        }
    }

    private fun parseArtifacts(
        array: ArrayNode?,
        context: String,
    ): List<ArtifactSelectionModel> {
        if (array == null) {
            return emptyList()
        }
        return array.mapIndexed { index, element ->
            val obj = element.objectNodeOrNull()
                ?: throw IllegalArgumentException("$context artifacts[$index] must be an object")
            ArtifactSelectionModel(
                family = obj.requiredString("family", "$context artifacts[$index]"),
                variant = obj.optionalString("variant", "$context artifacts[$index]").orEmpty().trim(),
            )
        }
    }

    private fun parseDesignFields(
        array: ArrayNode?,
        context: String,
        fieldName: String,
    ): List<SemanticFieldSnapshot> {
        if (array == null) {
            return emptyList()
        }
        return array.mapIndexed { index, element ->
            val obj = element.objectNodeOrNull()
                ?: throw IllegalArgumentException("$context $fieldName[$index] must be an object")
            require(!obj.has("nullable")) {
                "$context $fieldName[$index] field nullable is removed; encode nullability in type"
            }
            SemanticFieldSnapshot(
                name = obj.requiredString("name", "$context $fieldName[$index]"),
                typeExpression = obj.requiredString("type", "$context $fieldName[$index]"),
                defaultValue = obj.optionalString("defaultValue", "$context $fieldName[$index]"),
                sourcePath = "$context $fieldName[$index]",
            )
        }
    }

    private fun parseEdges(file: File): List<IrEdgeSnapshot> {
        val array = parseRequiredArray(file, "rels")
        return array.mapIndexed { index, element ->
            val context = "ir-analysis rels[${index}]"
            val obj = element.objectNodeOrNull()
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

    private fun parseRequiredArray(file: File, label: String): ArrayNode {
        val root = file.reader(Charsets.UTF_8).use { objectMapper.readTree(it) }
        require(root.isArray) {
            "ir-analysis $label file ${file.path} root must be an array"
        }
        return root as ArrayNode
    }

    private fun shortNameForId(id: String): String {
        val normalized = id.replace('$', '.')
        val byMethod = normalized.substringAfterLast("::", missingDelimiterValue = normalized)
        return byMethod.substringAfterLast('.')
    }

    private companion object {
        const val DESIGN_BLOCK_METADATA_FQ = "com.only4.cap4k.analysis.metadata.DesignBlockMetadata"
        const val AGGREGATE_ELEMENT_METADATA_FQ = "com.only4.cap4k.analysis.metadata.AggregateElementMetadata"
        const val DRAWING_BOARD_CAPABILITY = "Drawing Board"
        const val FLOW_ANALYSIS_CAPABILITY = "Flow Analysis"
        val DRAWING_BOARD_CANDIDATE_NODE_TYPES = setOf(
            "command",
            "commandhandler",
            "query",
            "queryhandler",
            "capability",
            "capabilityhandler",
            "apipayload",
            "domainevent",
            "domaineventhandler",
            "integrationevent",
            "integrationeventhandler",
            "domainservice",
        )
    }

    private fun JsonNode.objectNodeOrNull(): ObjectNode? = if (isObject) this as ObjectNode else null

    private fun ObjectNode.stringValue(name: String): String? {
        val element = get(name) ?: return null
        return if (element.isValueNode && !element.isNull) element.asText() else null
    }

    private fun ObjectNode.booleanValue(name: String): Boolean? {
        val element = get(name) ?: return null
        return if (element.isBoolean) element.booleanValue() else null
    }

    private fun ObjectNode.requiredString(
        name: String,
        context: String,
    ): String {
        val value = optionalString(name, context)
        if (value.isNullOrBlank()) {
            throw IllegalArgumentException("$context must declare non-blank $name")
        }
        return value.trim()
    }

    private fun ObjectNode.optionalString(
        name: String,
        context: String,
    ): String? {
        val element = get(name) ?: return null
        if (!element.isTextual) {
            throw IllegalArgumentException("$context field '$name' must be a string")
        }
        return element.textValue()
    }

    private fun ObjectNode.optionalBoolean(
        name: String,
        context: String,
    ): Boolean? {
        val element = get(name) ?: return null
        if (!element.isBoolean) {
            throw IllegalArgumentException("$context field '$name' must be a boolean")
        }
        return element.booleanValue()
    }

    private fun ObjectNode.jsonArrayOrNull(
        name: String,
        context: String,
    ): ArrayNode? {
        val element = get(name) ?: return null
        if (!element.isArray) {
            throw IllegalArgumentException("$context field '$name' must be an array")
        }
        return element as ArrayNode
    }

    private fun ObjectNode.stringList(name: String, context: String): List<String> {
        val element = get(name) ?: return emptyList()
        if (!element.isArray) {
            throw IllegalArgumentException("$context field '$name' must be an array")
        }
        return (element as ArrayNode).mapIndexed { index, item ->
            if (!item.isTextual) {
                throw IllegalArgumentException("$context $name[$index] must be a non-blank string")
            }
            item.textValue().trim().also { value ->
                if (value.isEmpty()) {
                    throw IllegalArgumentException("$context $name[$index] must be a non-blank string")
                }
            }
        }
    }
}

private data class AnalyzerPartitionIdAndDiagnostics(
    val partitionId: String,
    val diagnostics: MutableList<AnalyzerPartitionDiagnostic>,
)

private data class EdgeKey(
    val fromId: String,
    val toId: String,
    val type: String,
    val label: String?,
)

package com.only4.cap4k.plugin.pipeline.source.ir

import com.google.gson.JsonElement
import com.google.gson.JsonParser
import com.only4.cap4k.plugin.pipeline.api.AggregateElementSnapshot
import com.only4.cap4k.plugin.pipeline.api.ArtifactSelectionModel
import com.only4.cap4k.plugin.pipeline.api.DesignElementSnapshot
import com.only4.cap4k.plugin.pipeline.api.IrAnalysisSnapshot
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
import java.io.File

class IrAnalysisSourceProvider : SourceProvider {
    override val id: String = "ir-analysis"
    override val descriptor: PipelineCapabilityDescriptor = PipelineCapabilityDescriptor.builtIn(
        providerId = id,
        displayName = "IR Analysis Source",
        kind = PipelineCapabilityKind.SOURCE,
        module = "cap4k-plugin-pipeline-source-ir-analysis",
        tacticalCarriers = listOf("Analysis Graph", "Drawing Board Evidence"),
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

    override fun localInputPaths(config: ProjectConfig): List<String> =
        (config.sources[id]?.options?.get("inputDirs") as? List<*> ?: emptyList<Any>())
            .map { it.toString().trim() }
            .filter { it.isNotEmpty() }

    override fun collect(config: ProjectConfig): IrAnalysisSnapshot {
        val inputDirs = localInputPaths(config)
        require(inputDirs.isNotEmpty()) { "ir-analysis source requires at least one inputDirs entry." }

        val nodesById = linkedMapOf<String, IrNodeSnapshot>()
        val edgeKeys = linkedSetOf<EdgeKey>()
        val designElements = mutableListOf<DesignElementSnapshot>()
        val aggregateElementsByCarrier = linkedMapOf<String, AggregateElementSnapshot>()

        inputDirs.forEach { inputDir ->
            val dir = File(inputDir)
            require(dir.exists() && dir.isDirectory) { "ir-analysis inputDir does not exist or is not a directory: $inputDir" }

            val nodesFile = File(dir, "nodes.json")
            val relsFile = File(dir, "rels.json")
            require(nodesFile.exists() && relsFile.exists()) {
                "ir-analysis inputDir is missing nodes.json or rels.json: $inputDir"
            }
            val aggregateElementsFile = File(dir, "aggregate-elements.json")
            require(aggregateElementsFile.exists()) {
                "ir-analysis inputDir is missing aggregate-elements.json: $inputDir"
            }

            val inputNodes = parseNodes(nodesFile)
            val designElementsFile = File(dir, "design-elements.json")
            val inputDesignElements = if (designElementsFile.exists()) {
                parseDesignElements(designElementsFile)
            } else {
                emptyList()
            }
            val inputAggregateElements = parseAggregateElements(aggregateElementsFile)
            requireRequestedAnalysisMetadata(
                config = config,
                nodes = inputNodes,
                designElements = inputDesignElements,
                inputDir = inputDir,
            )

            inputNodes.forEach { node ->
                val existing = nodesById[node.id]
                nodesById[node.id] = if (existing == null) {
                    node
                } else {
                    val metadataOwners = listOfNotNull(existing.metadataOwner, node.metadataOwner).distinct()
                    require(metadataOwners.size <= 1) {
                        "conflicting analysis metadata owner for ${node.id}: ${metadataOwners.joinToString(", ")}"
                    }
                    existing.copy(
                        missingMetadata = (existing.missingMetadata + node.missingMetadata).distinct(),
                        metadataOwner = metadataOwners.singleOrNull(),
                    )
                }
            }
            parseEdges(relsFile).forEach { edge ->
                edgeKeys.add(EdgeKey(edge.fromId, edge.toId, edge.type, edge.label))
            }

            designElements.addAll(inputDesignElements)
            inputAggregateElements.forEach { aggregateElement ->
                val existing = aggregateElementsByCarrier[aggregateElement.carrierQualifiedName]
                require(existing == null || existing == aggregateElement) {
                    "conflicting aggregate element metadata for ${aggregateElement.carrierQualifiedName}"
                }
                aggregateElementsByCarrier.putIfAbsent(aggregateElement.carrierQualifiedName, aggregateElement)
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
            aggregateElements = aggregateElementsByCarrier.values.toList(),
        )
    }

    private fun requireRequestedAnalysisMetadata(
        config: ProjectConfig,
        nodes: Collection<IrNodeSnapshot>,
        designElements: List<DesignElementSnapshot>,
        inputDir: String,
    ) {
        val requestedCapabilities = linkedSetOf<String>()
        val missing = linkedMapOf<Pair<String, String>, LinkedHashSet<String>>()

        if ("drawing-board" in config.generators) {
            requestedCapabilities += DRAWING_BOARD_CAPABILITY
            nodes.forEach { node ->
                if (DESIGN_BLOCK_METADATA_FQ in node.missingMetadata) {
                    missing.getOrPut((node.metadataOwner ?: node.fullName) to DESIGN_BLOCK_METADATA_FQ) { linkedSetOf() }
                        .add(DRAWING_BOARD_CAPABILITY)
                }
            }
            if (designElements.isEmpty()) {
                nodes.asSequence()
                    .filter { node -> node.type.lowercase() in DRAWING_BOARD_CANDIDATE_NODE_TYPES }
                    .forEach { node ->
                        missing.getOrPut((node.metadataOwner ?: node.fullName) to DESIGN_BLOCK_METADATA_FQ) { linkedSetOf() }
                            .add(DRAWING_BOARD_CAPABILITY)
                    }
            }
        }
        if ("flow" in config.generators) {
            requestedCapabilities += FLOW_ANALYSIS_CAPABILITY
            nodes.forEach { node ->
                if (AGGREGATE_ELEMENT_METADATA_FQ in node.missingMetadata) {
                    missing.getOrPut((node.metadataOwner ?: node.fullName) to AGGREGATE_ELEMENT_METADATA_FQ) { linkedSetOf() }
                        .add(FLOW_ANALYSIS_CAPABILITY)
                }
            }
        }

        if (missing.isEmpty()) {
            return
        }

        val details = missing.entries.joinToString(separator = System.lineSeparator()) { (key, capabilities) ->
            val (symbol, metadataFq) = key
            "- symbol: $symbol; missing metadata: $metadataFq; affected capability: ${capabilities.joinToString(", ")}"
        }
        throw IllegalArgumentException(
            buildString {
                appendLine("Cap4k analysis metadata contract violation.")
                appendLine("Analysis input: $inputDir.")
                appendLine(details)
                appendLine("Requested analysis capabilities: ${requestedCapabilities.joinToString(", ")}.")
                append(
                    "Recovery: restore the default ddd-default generator template for each symbol, or add the listed " +
                        "metadata annotation and keep io.github.ldmoxeii:cap4k-analysis-metadata on the owning " +
                        "business module compileOnly classpath. Custom templates that omit analysis metadata explicitly " +
                        "opt out of the affected capability; Cap4k will not emit an apparently complete partial result."
                )
            }
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
                missingMetadata = obj.stringList("missingMetadata", context),
                metadataOwner = obj.stringValue("metadataOwner")?.trim()?.takeIf(String::isNotEmpty),
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

    private fun parseAggregateElements(file: File): List<AggregateElementSnapshot> {
        val array = parseRequiredArray(file, "aggregate-elements")
        return array.mapIndexed { index, element ->
            val context = "aggregate element at index $index"
            val obj = element.asJsonObjectOrNull()
                ?: throw IllegalArgumentException("$context must be an object")
            AggregateElementSnapshot(
                carrierQualifiedName = obj.requiredString("carrierQualifiedName", context),
                aggregate = obj.requiredString("aggregate", context),
                name = obj.optionalString("name", context).orEmpty().trim(),
                packageName = obj.optionalString("packageName", context).orEmpty().trim(),
                description = obj.optionalString("description", context).orEmpty().trim(),
                type = obj.requiredString("type", context),
                root = obj.optionalBoolean("root", context) ?: false,
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
    ): List<SemanticFieldSnapshot> {
        if (array == null) {
            return emptyList()
        }
        return array.mapIndexed { index, element ->
            val obj = element.asJsonObjectOrNull()
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

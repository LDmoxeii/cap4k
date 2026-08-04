package com.only4.cap4k.plugin.pipeline.source.valueobject

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig
import com.only4.cap4k.plugin.pipeline.api.PipelineBoundaryAuthorities
import com.only4.cap4k.plugin.pipeline.api.PipelineBoundaryKind
import com.only4.cap4k.plugin.pipeline.api.PipelineCapabilityBoundary
import com.only4.cap4k.plugin.pipeline.api.PipelineCapabilityDescriptor
import com.only4.cap4k.plugin.pipeline.api.PipelineCapabilityKind
import com.only4.cap4k.plugin.pipeline.api.PipelineExecutionLane
import com.only4.cap4k.plugin.pipeline.api.PipelineInputRequirement
import com.only4.cap4k.plugin.pipeline.api.PipelineInputRequirementMatch
import com.only4.cap4k.plugin.pipeline.api.PipelinePublicTasks
import com.only4.cap4k.plugin.pipeline.api.SemanticFieldSnapshot
import com.only4.cap4k.plugin.pipeline.api.SourceProvider
import com.only4.cap4k.plugin.pipeline.api.ValueObjectDeclarationSnapshot
import com.only4.cap4k.plugin.pipeline.api.ValueObjectManifestSnapshot
import com.only4.cap4k.plugin.pipeline.api.ValueObjectPersistenceSnapshot
import com.only4.cap4k.plugin.pipeline.json.PipelineJson
import java.nio.file.Path

class ValueObjectManifestSourceProvider : SourceProvider {
    private val objectMapper = PipelineJson.newMapper()
    override val id: String = "value-object-manifest"
    override val descriptor: PipelineCapabilityDescriptor = PipelineCapabilityDescriptor.builtIn(
        providerId = id,
        displayName = "Value Object Manifest Source",
        kind = PipelineCapabilityKind.SOURCE,
        module = "cap4k-plugin-pipeline-source-value-object-manifest",
        tacticalCarriers = listOf("Value Object"),
        executionLanes = listOf(PipelineExecutionLane.AUTHORING, PipelineExecutionLane.GENERATED_SOURCE),
        tasks = listOf(PipelinePublicTasks.PLAN, PipelinePublicTasks.GENERATE, PipelinePublicTasks.GENERATE_SOURCES),
        inputRequirements = listOf(
            PipelineInputRequirement(
                id = "value-object-manifest-files",
                configurationPaths = listOf("sources.value-object-manifest.files", "types.valueObjectManifest.files"),
                match = PipelineInputRequirementMatch.ANY,
            ),
        ),
        boundaries = listOf(
            PipelineCapabilityBoundary(PipelineBoundaryKind.INPUT, PipelineBoundaryAuthorities.PROJECT_INPUT),
            PipelineCapabilityBoundary(PipelineBoundaryKind.GENERATION, PipelineBoundaryAuthorities.PIPELINE_SOURCE),
        ),
    )

    override fun collect(config: ProjectConfig): ValueObjectManifestSnapshot {
        val sourceFiles = config.sources[id]
            ?.options
            ?.get("files")
            .asPathList()
        val files = sourceFiles.ifEmpty { config.typeRegistry.valueObjectManifestFiles.map(Path::of) }
        return load(files)
    }

    override fun localInputPaths(config: ProjectConfig): List<String> {
        val sourceFiles = config.sources[id]
            ?.options
            ?.get("files")
            .asPathList()
        return sourceFiles
            .ifEmpty { config.typeRegistry.valueObjectManifestFiles.map(Path::of) }
            .map(Path::toString)
    }

    fun load(files: List<Path>): ValueObjectManifestSnapshot {
        require(files.isNotEmpty()) {
            "types.valueObjectManifest.files must not be empty when valueObjectManifest is configured"
        }
        val declarations = files.flatMap { file -> parseFile(file) }
        validateDuplicateNames(declarations)
        return ValueObjectManifestSnapshot(declarations = declarations)
    }

    private fun parseFile(file: Path): List<ValueObjectDeclarationSnapshot> {
        val definitions = file.toFile().reader(Charsets.UTF_8).use { reader ->
            objectMapper.readTree(reader) as ArrayNode
        }
        return definitions.map { element -> (element as ObjectNode).toValueObject() }
    }

    private fun ObjectNode.toValueObject(): ValueObjectDeclarationSnapshot {
        val name = requiredString("name")
        val removedFields = listOf("scope", "aggregate").filter(::has)
        require(removedFields.isEmpty()) {
            "value object $name fields ${removedFields.joinToString(" and ")} are removed; use aggregates instead"
        }
        require(!has("storage")) {
            "value object $name field storage is removed; use persistence instead"
        }
        val aggregates = optionalStringArray("aggregates")
        require(aggregates.size <= 1) {
            "value object $name may declare at most one aggregate"
        }
        return ValueObjectDeclarationSnapshot(
            name = name,
            packageName = requiredString("package"),
            aggregates = aggregates,
            persistence = parsePersistence(name),
            fields = optionalArray("fields").map { fieldElement ->
                val fieldJson = fieldElement as ObjectNode
                val fieldName = fieldJson.requiredString("name")
                require(!fieldJson.has("nullable")) {
                    "value object $name field $fieldName property nullable is removed; express nullability in type"
                }
                SemanticFieldSnapshot(
                    name = fieldName,
                    typeExpression = fieldJson.requiredString("type"),
                    defaultValue = fieldJson.optionalString("defaultValue"),
                    sourcePath = "$name.fields.$fieldName",
                )
            },
            description = optionalString("description"),
        )
    }

    private fun ObjectNode.parsePersistence(name: String): ValueObjectPersistenceSnapshot? {
        if (!has("persistence")) {
            return null
        }
        val element = get("persistence")
        require(!element.isNull && element.isObject) {
            "value object $name persistence must be an object"
        }
        val persistence = element as ObjectNode
        val kind = persistence.requiredString("kind")
        require(kind == "json") {
            "value object $name persistence.kind is unsupported: $kind"
        }
        val unsupportedOptions = persistence.fieldNames().asSequence().filter { it != "kind" }.sorted().toList()
        require(unsupportedOptions.isEmpty()) {
            "value object $name persistence has unsupported options: ${unsupportedOptions.joinToString(", ")}"
        }
        return ValueObjectPersistenceSnapshot(kind = kind)
    }

    private fun validateDuplicateNames(valueObjects: List<ValueObjectDeclarationSnapshot>) {
        val duplicateSharedName = valueObjects
            .filter { it.aggregates.isEmpty() }
            .groupingBy { it.name }
            .eachCount()
            .entries
            .firstOrNull { it.value > 1 }
            ?.key
        require(duplicateSharedName == null) {
            "duplicate shared value object definition: $duplicateSharedName"
        }

        val duplicateAggregateName = valueObjects
            .mapNotNull { valueObject ->
                valueObject.aggregates.singleOrNull()?.let { owner -> owner to valueObject.name }
            }
            .groupingBy { it }
            .eachCount()
            .entries
            .firstOrNull { it.value > 1 }
            ?.key
        require(duplicateAggregateName == null) {
            "duplicate aggregate value object definition: ${duplicateAggregateName!!.second} in ${duplicateAggregateName.first}"
        }
    }

}

private fun ObjectNode.requiredString(field: String): String {
    require(has(field) && !get(field).isNull) {
        "value object manifest field $field is required"
    }
    return get(field).primitiveString()
}

private fun ObjectNode.optionalString(field: String): String? =
    if (has(field) && !get(field).isNull) get(field).primitiveString() else null

private fun ObjectNode.optionalArray(field: String): List<JsonNode> =
    if (has(field) && !get(field).isNull) (get(field) as ArrayNode).toList() else emptyList()

private fun ObjectNode.optionalStringArray(field: String): List<String> =
    if (has(field) && !get(field).isNull) (get(field) as ArrayNode).map { it.primitiveString() } else emptyList()

private fun Any?.asPathList(): List<Path> =
    when (this) {
        null -> emptyList()
        is Iterable<*> -> mapNotNull { it?.toString()?.let(Path::of) }
        is Array<*> -> mapNotNull { it?.toString()?.let(Path::of) }
        else -> listOf(Path.of(toString()))
    }

private fun JsonNode.primitiveString(): String {
    check(isValueNode && !isNull) { "Expected a JSON primitive" }
    return asText()
}

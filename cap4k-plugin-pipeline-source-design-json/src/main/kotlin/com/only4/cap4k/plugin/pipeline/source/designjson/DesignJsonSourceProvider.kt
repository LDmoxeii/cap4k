package com.only4.cap4k.plugin.pipeline.source.designjson

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.only4.cap4k.plugin.pipeline.api.ArtifactSelectionModel
import com.only4.cap4k.plugin.pipeline.api.DesignSpecEntry
import com.only4.cap4k.plugin.pipeline.api.DesignSpecSnapshot
import com.only4.cap4k.plugin.pipeline.api.PipelineBoundaryAuthorities
import com.only4.cap4k.plugin.pipeline.api.PipelineBoundaryKind
import com.only4.cap4k.plugin.pipeline.api.PipelineCapabilityBoundary
import com.only4.cap4k.plugin.pipeline.api.PipelineCapabilityDescriptor
import com.only4.cap4k.plugin.pipeline.api.PipelineCapabilityKind
import com.only4.cap4k.plugin.pipeline.api.PipelineExecutionLane
import com.only4.cap4k.plugin.pipeline.api.PipelineInputRequirement
import com.only4.cap4k.plugin.pipeline.api.PipelineInputRequirementMatch
import com.only4.cap4k.plugin.pipeline.api.PipelinePublicTasks
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig
import com.only4.cap4k.plugin.pipeline.api.SemanticFieldSnapshot
import com.only4.cap4k.plugin.pipeline.api.SourceProvider
import com.only4.cap4k.plugin.pipeline.json.PipelineJson
import java.io.File

class DesignJsonSourceProvider : SourceProvider {
    private val objectMapper = PipelineJson.newMapper()
    override val id: String = "design-json"
    override val descriptor: PipelineCapabilityDescriptor = PipelineCapabilityDescriptor.builtIn(
        providerId = id,
        displayName = "Design JSON Source",
        kind = PipelineCapabilityKind.SOURCE,
        module = "cap4k-plugin-pipeline-source-design-json",
        tacticalCarriers = listOf(
            "Command",
            "Query",
            "Capability",
            "API Payload",
            "Domain Event",
            "Integration Event",
            "Domain Service",
            "Subscriber",
        ),
        executionLanes = listOf(PipelineExecutionLane.AUTHORING),
        tasks = listOf(PipelinePublicTasks.PLAN, PipelinePublicTasks.GENERATE),
        inputRequirements = listOf(
            PipelineInputRequirement(
                id = "design-json-files",
                configurationPaths = listOf("sources.design-json.files", "sources.design-json.manifestFile"),
                match = PipelineInputRequirementMatch.ANY,
            ),
        ),
        boundaries = listOf(
            PipelineCapabilityBoundary(PipelineBoundaryKind.INPUT, PipelineBoundaryAuthorities.PROJECT_INPUT),
            PipelineCapabilityBoundary(PipelineBoundaryKind.GENERATION, PipelineBoundaryAuthorities.PIPELINE_SOURCE),
        ),
    )

    private val supportedTags = setOf(
        "command",
        "query",
        "capability",
        "api_payload",
        "domain_event",
        "integration_event",
        "domain_service",
    )
    private val removedPublicFields = listOf("desc", "requestFields", "responseFields", "traits", "role", "scope", "entity")
    private val resultFieldTags = setOf("command", "query", "capability", "api_payload")
    private val eventNameTags = setOf("domain_event", "integration_event")
    private val selfToken = Regex("""(?<![A-Za-z0-9_.])self(?![A-Za-z0-9_])""", RegexOption.IGNORE_CASE)

    override fun collect(config: ProjectConfig): DesignSpecSnapshot {
        val options = config.sources[id]?.options ?: emptyMap()
        val entries = resolveFiles(options)
            .flatMap { parseFile(it) }
        return DesignSpecSnapshot(entries = entries)
    }

    override fun localInputPaths(config: ProjectConfig): List<String> {
        val options = config.sources[id]?.options ?: return emptyList()
        val manifestFile = options["manifestFile"]?.toString()?.takeIf { it.isNotBlank() }
        return buildList {
            manifestFile?.let(::add)
            addAll(resolveFiles(options).map(File::getAbsolutePath))
        }.distinct()
    }

    private fun resolveFiles(options: Map<String, Any?>): List<File> {
        if (options.containsKey("manifestFile")) {
            val manifestFileOption = options["manifestFile"]?.toString()
            require(!manifestFileOption.isNullOrBlank()) {
                "design-json source option manifestFile must not be blank"
            }
            val projectDirOption = options["projectDir"]?.toString()
            require(!projectDirOption.isNullOrBlank()) {
                "design-json source option projectDir is required when manifestFile is set"
            }

            val manifestFile = File(manifestFileOption).canonicalFile
            require(manifestFile.exists()) {
                "design manifest file does not exist: ${manifestFile.path}"
            }

            val manifestEntries = manifestFile.reader(Charsets.UTF_8).use { reader ->
                (objectMapper.readTree(reader) as ArrayNode).map { it.primitiveString() }
            }
            require(manifestEntries.isNotEmpty()) {
                "design manifest file must not be empty"
            }

            val projectDir = File(projectDirOption).canonicalFile
            val projectDirPath = projectDir.toPath()
            val files = manifestEntries
                .map { rawEntry ->
                    val entry = rawEntry.trim()
                    require(entry.isNotEmpty()) {
                        "blank design manifest entry"
                    }
                    val resolvedFile = File(projectDir, entry).canonicalFile
                    require(resolvedFile.toPath().startsWith(projectDirPath)) {
                        "design manifest entry escapes projectDir: $entry"
                    }
                    resolvedFile
                }

            val duplicates = files
                .groupingBy { it.path }
                .eachCount()
                .filterValues { it > 1 }
                .keys
            require(duplicates.isEmpty()) {
                "duplicate design manifest entry: ${duplicates.first()}"
            }

            files.forEach { file ->
                require(file.exists()) {
                    "design manifest entry file does not exist: ${file.path}"
                }
            }
            return files
        }

        val files = options["files"] as? List<*> ?: emptyList<Any>()
        return files.map { File(it.toString()) }
    }

    private fun parseFile(file: File): List<DesignSpecEntry> {
        val root = file.reader(Charsets.UTF_8).use { objectMapper.readTree(it) }
        require(root.isArray) {
            "design-json file ${file.path} root must be an array."
        }
        val array = root.asArrayNode()
        return array.mapIndexed { index, element ->
            require(element.isObject) {
                "design-json file ${file.path} design entry[$index] must be an object."
            }
            val obj = element.asObjectNode()
            val rawTag = readRequiredString(obj, "tag", "design entry", trim = false)
            val name = readRequiredString(obj, "name", "design entry")
            val tag = parseTag(rawTag)
            rejectRemovedFields(obj, name)
            val fields = parseFields(obj["fields"], name, "fields")
            val resultFields = parseFields(obj["resultFields"], name, "resultFields")
            val artifacts = parseArtifacts(obj["artifacts"], name)
            val eventName = parseIntegrationEventName(obj, tag, name)
            val persist = parsePersist(obj, tag, name)
            validateDomainServiceFields(tag, name, fields, resultFields)
            validateResultFields(tag, name, resultFields)
            validatePageFields(tag, name, fields, artifacts)
            validatePersistedDomainEventName(tag, name, persist, eventName)
            validateEventName(tag, name, obj)
            validateNoSelfTypes(name, fields + resultFields)
            DesignSpecEntry(
                tag = tag,
                packageName = readPackageName(readOptionalString(obj, "package", "design entry $name"), tag),
                name = name,
                description = readOptionalString(obj, "description", "design entry $name").orEmpty(),
                aggregates = parseStringArray(obj["aggregates"], name, "aggregates"),
                persist = persist,
                artifacts = artifacts,
                fields = fields,
                resultFields = resultFields,
                eventName = eventName,
            )
        }
    }

    private fun parseTag(rawTag: String): String {
        if (rawTag !in supportedTags) {
            throw IllegalArgumentException("Unsupported design tag: $rawTag")
        }
        return rawTag
    }

    private fun rejectRemovedFields(obj: ObjectNode, name: String) {
        val removed = removedPublicFields.filter { obj.has(it) }
        require(removed.isEmpty()) {
            "design entry $name uses removed fields: ${removed.joinToString(", ")}"
        }
    }

    private fun parseIntegrationEventName(obj: ObjectNode, tag: String, name: String): String? {
        if (tag !in eventNameTags) {
            return null
        }
        val eventName = readOptionalString(obj, "eventName", "design entry $name")?.trim()
        require(tag != "integration_event" || !eventName.isNullOrEmpty()) {
            "integration_event $name must declare eventName."
        }
        return eventName
    }

    private fun parsePersist(obj: ObjectNode, tag: String, name: String): Boolean? {
        require(tag == "domain_event" || !obj.has("persist")) {
            "design entry $name cannot declare persist on tag: $tag"
        }
        return readOptionalBoolean(obj, "persist", "design entry $name")
    }

    private fun validateResultFields(
        tag: String,
        name: String,
        resultFields: List<SemanticFieldSnapshot>,
    ) {
        if (tag in resultFieldTags) {
            return
        }
        if (tag == "integration_event") {
            require(resultFields.isEmpty()) {
                "integration_event $name must not declare resultFields."
            }
            return
        }
        require(resultFields.isEmpty()) {
            "design entry $name cannot declare resultFields on tag: $tag"
        }
    }

    private fun validateDomainServiceFields(
        tag: String,
        name: String,
        fields: List<SemanticFieldSnapshot>,
        resultFields: List<SemanticFieldSnapshot>,
    ) {
        if (tag != "domain_service") return
        require(fields.isEmpty() && resultFields.isEmpty()) {
            "domain_service $name is metadata-only and must not declare fields or resultFields."
        }
    }

    private fun validatePageFields(
        tag: String,
        name: String,
        fields: List<SemanticFieldSnapshot>,
        artifacts: List<ArtifactSelectionModel>?,
    ) {
        val pageFamily = when (tag) {
            "query" -> "query"
            "api_payload" -> "api-payload"
            else -> return
        }
        if (artifacts.orEmpty().none { it.family == pageFamily && it.variant == "page" }) return
        val collision = fields.firstOrNull { field ->
            field.name.substringBefore('.').removeSuffix("[]") in PageFieldNames
        } ?: return
        val pageFieldName = collision.name.substringBefore('.').removeSuffix("[]")
        throw IllegalArgumentException(
            "design entry $name page variant derives $pageFieldName; remove the explicit field.",
        )
    }

    private fun validatePersistedDomainEventName(
        tag: String,
        name: String,
        persist: Boolean?,
        eventName: String?,
    ) {
        require(tag != "domain_event" || persist != true || !eventName.isNullOrBlank()) {
            "persisted domain_event $name must declare eventName."
        }
    }

    private fun validateEventName(tag: String, name: String, obj: ObjectNode) {
        require(tag in eventNameTags || !obj.has("eventName")) {
            "design entry $name cannot declare eventName on tag: $tag"
        }
    }

    private fun validateNoSelfTypes(name: String, fields: List<SemanticFieldSnapshot>) {
        fields.firstOrNull { field -> selfToken.containsMatchIn(field.typeExpression) }?.let { field ->
            throw IllegalArgumentException(
                "design entry $name field ${field.name} must use an explicit type name instead of self",
            )
        }
    }

    private fun readPackageName(packageName: String?, tag: String): String {
        if (packageName != null) {
            return packageName
        }
        if (tag == "domain_event") {
            return ""
        }
        error("design entry package is required for tag: $tag")
    }

    private fun parseArtifacts(element: JsonNode?, name: String): List<ArtifactSelectionModel>? {
        if (element == null) {
            return null
        }
        require(element.isArray) {
            "design entry $name artifacts must be an array."
        }
        val array = element.asArrayNode()
        require(array.size() > 0) {
            "design entry $name artifacts must not be empty."
        }
        return array.mapIndexed { index, artifactElement ->
            require(artifactElement.isObject) {
                "design entry $name artifacts[$index] must be an object."
            }
            val artifact = artifactElement.asObjectNode()
            val familyElement = artifact["family"]
            require(familyElement != null && familyElement.isTextual) {
                "design entry $name artifacts[$index] artifact family must be a nonblank string."
            }
            val family = familyElement.asText().trim()
            require(family.isNotEmpty()) {
                "design entry $name artifacts[$index] artifact family must be a nonblank string."
            }
            val variantElement = artifact["variant"]
            require(variantElement == null || variantElement.isTextual) {
                "design entry $name artifacts[$index] artifact variant must be a string."
            }
            val variant = variantElement?.asText()?.trim().orEmpty()
            ArtifactSelectionModel(family = family, variant = variant)
        }
    }

    private fun parseFields(element: JsonNode?, entryName: String, fieldName: String): List<SemanticFieldSnapshot> {
        if (element == null) {
            return emptyList()
        }
        require(element.isArray) {
            "design entry $entryName $fieldName must be an array."
        }
        val array = element.asArrayNode()
        return array.mapIndexed { index, element ->
            require(element.isObject) {
                "design entry $entryName $fieldName[$index] must be an object."
            }
            val field = element.asObjectNode()
            require(!field.has("nullable")) {
                "design entry $entryName $fieldName[$index] field nullable is removed; encode nullability in type"
            }
            val name = readRequiredString(field, "name", "design entry $entryName $fieldName[$index] field")
            SemanticFieldSnapshot(
                name = name,
                typeExpression = readRequiredString(field, "type", "design entry $entryName $fieldName[$index] field"),
                defaultValue = readOptionalString(
                    field,
                    "defaultValue",
                    "design entry $entryName $fieldName[$index] field",
                ),
                sourcePath = "$fieldName.$name",
            )
        }
    }

    private fun parseStringArray(element: JsonNode?, entryName: String, fieldName: String): List<String> {
        if (element == null) {
            return emptyList()
        }
        require(element.isArray) {
            "design entry $entryName $fieldName must be an array."
        }
        val array = element.asArrayNode()
        return array.mapIndexed { index, item ->
            require(item.isTextual) {
                "design entry $entryName $fieldName[$index] must be a nonblank string."
            }
            item.asText().trim().also { value ->
                require(value.isNotEmpty()) {
                    "design entry $entryName $fieldName[$index] must be a nonblank string."
                }
            }
        }
    }

    private fun readRequiredString(
        obj: ObjectNode,
        fieldName: String,
        context: String,
        trim: Boolean = true,
    ): String {
        val value = readOptionalString(obj, fieldName, context)
        require(!value.isNullOrBlank()) {
            "$context $fieldName must be a nonblank string."
        }
        return if (trim) value.trim() else value
    }

    private fun readOptionalString(obj: ObjectNode, fieldName: String, context: String): String? {
        val element = obj[fieldName] ?: return null
        require(element.isTextual) {
            "$context $fieldName must be a nonblank string."
        }
        return element.asText()
    }

    private fun readOptionalBoolean(obj: ObjectNode, fieldName: String, context: String): Boolean? {
        val element = obj[fieldName] ?: return null
        require(element.isBoolean) {
            "$context $fieldName must be a boolean."
        }
        return element.booleanValue()
    }

    private fun JsonNode.primitiveString(): String {
        check(isValueNode && !isNull) { "Expected a JSON primitive" }
        return asText()
    }

    private fun JsonNode.asArrayNode(): ArrayNode = this as ArrayNode

    private fun JsonNode.asObjectNode(): ObjectNode = this as ObjectNode

    private companion object {
        val PageFieldNames = setOf("pageNum", "pageSize")
    }

}

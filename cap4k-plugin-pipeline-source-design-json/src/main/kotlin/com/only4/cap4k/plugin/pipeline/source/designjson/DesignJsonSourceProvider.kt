package com.only4.cap4k.plugin.pipeline.source.designjson

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.only4.cap4k.plugin.pipeline.api.ArtifactSelectionModel
import com.only4.cap4k.plugin.pipeline.api.DesignSpecEntry
import com.only4.cap4k.plugin.pipeline.api.DesignSpecSnapshot
import com.only4.cap4k.plugin.pipeline.api.FieldModel
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig
import com.only4.cap4k.plugin.pipeline.api.SourceProvider
import java.io.File

class DesignJsonSourceProvider : SourceProvider {
    override val id: String = "design-json"

    private val supportedTags = setOf(
        "command",
        "query",
        "client",
        "api_payload",
        "domain_event",
        "integration_event",
        "domain_service",
        "saga",
    )
    private val removedPublicFields = listOf("desc", "requestFields", "responseFields", "traits", "role", "scope")
    private val resultFieldTags = setOf("query", "client", "api_payload")
    private val eventNameTags = setOf("domain_event", "integration_event")
    private val selfToken = Regex("""(?<![A-Za-z0-9_.])self(?![A-Za-z0-9_])""", RegexOption.IGNORE_CASE)

    override fun collect(config: ProjectConfig): DesignSpecSnapshot {
        val options = config.sources[id]?.options ?: emptyMap()
        val entries = resolveFiles(options)
            .flatMap { parseFile(it) }
        return DesignSpecSnapshot(entries = entries)
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
                JsonParser.parseReader(reader).asJsonArray.map { it.asString }
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
        val array = file.reader(Charsets.UTF_8).use { JsonParser.parseReader(it).asJsonArray }
        return array.map { element ->
            val obj = element.asJsonObject
            val rawTag = obj["tag"].asString
            val name = obj["name"].asString
            val tag = parseTag(rawTag)
            rejectRemovedFields(obj, name)
            val fields = parseFields(obj["fields"]?.asJsonArray)
            val resultFields = parseFields(obj["resultFields"]?.asJsonArray)
            val artifacts = parseArtifacts(obj["artifacts"], name)
            val eventName = parseIntegrationEventName(obj, tag, name)
            validateResultFields(tag, name, resultFields)
            validatePersist(tag, name, obj)
            validateEventName(tag, name, obj)
            validateReservedFields(tag, name, fields)
            validateNoSelfTypes(name, fields + resultFields)
            DesignSpecEntry(
                tag = tag,
                packageName = readPackageName(obj["package"]?.asString, tag),
                name = name,
                description = obj["description"]?.asString ?: "",
                aggregates = obj["aggregates"]?.asJsonArray?.map { it.asString } ?: emptyList(),
                persist = obj["persist"]?.asBoolean,
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

    private fun rejectRemovedFields(obj: JsonObject, name: String) {
        val removed = removedPublicFields.filter { obj.has(it) }
        require(removed.isEmpty()) {
            "design entry $name uses removed fields: ${removed.joinToString(", ")}"
        }
    }

    private fun parseIntegrationEventName(obj: JsonObject, tag: String, name: String): String? {
        if (tag !in eventNameTags) {
            return null
        }
        val eventName = obj["eventName"]?.asString?.trim()
        require(tag != "integration_event" || !eventName.isNullOrEmpty()) {
            "integration_event $name must declare eventName."
        }
        return eventName
    }

    private fun validateResultFields(
        tag: String,
        name: String,
        resultFields: List<FieldModel>,
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

    private fun validatePersist(tag: String, name: String, obj: JsonObject) {
        require(tag == "domain_event" || !obj.has("persist")) {
            "design entry $name cannot declare persist on tag: $tag"
        }
    }

    private fun validateEventName(tag: String, name: String, obj: JsonObject) {
        require(tag in eventNameTags || !obj.has("eventName")) {
            "design entry $name cannot declare eventName on tag: $tag"
        }
    }

    private fun validateNoSelfTypes(name: String, fields: List<FieldModel>) {
        fields.firstOrNull { field -> selfToken.containsMatchIn(field.type) }?.let { field ->
            throw IllegalArgumentException(
                "design entry $name field ${field.name} must use an explicit type name instead of self",
            )
        }
    }

    private fun validateReservedFields(tag: String, name: String, requestFields: List<FieldModel>) {
        if (tag != "domain_event") {
            return
        }
        require(requestFields.none { it.name.equals("entity", ignoreCase = true) }) {
            "domain_event $name field 'entity' is reserved and derived from aggregates[0]."
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

    private fun parseArtifacts(element: JsonElement?, name: String): List<ArtifactSelectionModel>? {
        if (element == null) {
            return null
        }
        require(element.isJsonArray) {
            "design entry $name artifacts must be an array."
        }
        val array = element.asJsonArray
        return array.mapIndexed { index, artifactElement ->
            require(artifactElement.isJsonObject) {
                "design entry $name artifacts[$index] must be an object."
            }
            val artifact = artifactElement.asJsonObject
            val familyElement = artifact["family"]
            require(familyElement != null && familyElement.isJsonPrimitive && familyElement.asJsonPrimitive.isString) {
                "design entry $name artifacts[$index] artifact family must be a nonblank string."
            }
            val family = familyElement.asString.trim()
            require(family.isNotEmpty()) {
                "design entry $name artifacts[$index] artifact family must be a nonblank string."
            }
            val variantElement = artifact["variant"]
            require(variantElement == null || variantElement.isJsonPrimitive && variantElement.asJsonPrimitive.isString) {
                "design entry $name artifacts[$index] artifact variant must be a string."
            }
            val variant = variantElement?.asString?.trim().orEmpty()
            ArtifactSelectionModel(family = family, variant = variant)
        }
    }

    private fun parseFields(array: JsonArray?): List<FieldModel> {
        if (array == null) {
            return emptyList()
        }
        return array.map { element ->
            val field = element.asJsonObject
            FieldModel(
                name = field["name"].asString,
                type = field["type"]?.asString ?: "kotlin.String",
                nullable = field["nullable"]?.asBoolean ?: false,
                defaultValue = field["defaultValue"]?.asString,
            )
        }
    }

}

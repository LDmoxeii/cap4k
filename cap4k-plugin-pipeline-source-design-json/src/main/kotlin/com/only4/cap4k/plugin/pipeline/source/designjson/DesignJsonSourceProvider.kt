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
        val root = file.reader(Charsets.UTF_8).use { JsonParser.parseReader(it) }
        require(root.isJsonArray) {
            "design-json file ${file.path} root must be an array."
        }
        val array = root.asJsonArray
        return array.mapIndexed { index, element ->
            require(element.isJsonObject) {
                "design-json file ${file.path} design entry[$index] must be an object."
            }
            val obj = element.asJsonObject
            val rawTag = readRequiredString(obj, "tag", "design entry", trim = false)
            val name = readRequiredString(obj, "name", "design entry")
            val tag = parseTag(rawTag)
            rejectRemovedFields(obj, name)
            val fields = parseFields(obj["fields"], name, "fields")
            val resultFields = parseFields(obj["resultFields"], name, "resultFields")
            val artifacts = parseArtifacts(obj["artifacts"], name)
            val eventName = parseIntegrationEventName(obj, tag, name)
            val persist = parsePersist(obj, tag, name)
            validateResultFields(tag, name, resultFields)
            validateEventName(tag, name, obj)
            validateReservedFields(tag, name, fields)
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
        val eventName = readOptionalString(obj, "eventName", "design entry $name")?.trim()
        require(tag != "integration_event" || !eventName.isNullOrEmpty()) {
            "integration_event $name must declare eventName."
        }
        return eventName
    }

    private fun parsePersist(obj: JsonObject, tag: String, name: String): Boolean? {
        require(tag == "domain_event" || !obj.has("persist")) {
            "design entry $name cannot declare persist on tag: $tag"
        }
        return readOptionalBoolean(obj, "persist", "design entry $name")
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

    private fun validateReservedFields(tag: String, name: String, fields: List<FieldModel>) {
        if (tag != "domain_event") {
            return
        }
        require(fields.none { it.name.equals("entity", ignoreCase = true) }) {
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

    private fun parseFields(element: JsonElement?, entryName: String, fieldName: String): List<FieldModel> {
        if (element == null) {
            return emptyList()
        }
        require(element.isJsonArray) {
            "design entry $entryName $fieldName must be an array."
        }
        val array = element.asJsonArray
        return array.mapIndexed { index, element ->
            require(element.isJsonObject) {
                "design entry $entryName $fieldName[$index] must be an object."
            }
            val field = element.asJsonObject
            FieldModel(
                name = readRequiredString(field, "name", "design entry $entryName $fieldName[$index] field"),
                type = readRequiredString(field, "type", "design entry $entryName $fieldName[$index] field"),
                nullable = readOptionalBoolean(field, "nullable", "design entry $entryName $fieldName[$index] field")
                    ?: false,
                defaultValue = readOptionalString(
                    field,
                    "defaultValue",
                    "design entry $entryName $fieldName[$index] field",
                ),
            )
        }
    }

    private fun parseStringArray(element: JsonElement?, entryName: String, fieldName: String): List<String> {
        if (element == null) {
            return emptyList()
        }
        require(element.isJsonArray) {
            "design entry $entryName $fieldName must be an array."
        }
        val array = element.asJsonArray
        return array.mapIndexed { index, item ->
            require(item.isStringPrimitive()) {
                "design entry $entryName $fieldName[$index] must be a nonblank string."
            }
            item.asString.trim().also { value ->
                require(value.isNotEmpty()) {
                    "design entry $entryName $fieldName[$index] must be a nonblank string."
                }
            }
        }
    }

    private fun readRequiredString(
        obj: JsonObject,
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

    private fun readOptionalString(obj: JsonObject, fieldName: String, context: String): String? {
        val element = obj[fieldName] ?: return null
        require(element.isStringPrimitive()) {
            "$context $fieldName must be a nonblank string."
        }
        return element.asString
    }

    private fun readOptionalBoolean(obj: JsonObject, fieldName: String, context: String): Boolean? {
        val element = obj[fieldName] ?: return null
        require(element.isJsonPrimitive && element.asJsonPrimitive.isBoolean) {
            "$context $fieldName must be a boolean."
        }
        return element.asBoolean
    }

    private fun JsonElement.isStringPrimitive(): Boolean =
        isJsonPrimitive && asJsonPrimitive.isString

}

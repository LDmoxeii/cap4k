package com.only4.cap4k.plugin.pipeline.source.designjson

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.only4.cap4k.plugin.pipeline.api.DesignSpecEntry
import com.only4.cap4k.plugin.pipeline.api.DesignSpecSnapshot
import com.only4.cap4k.plugin.pipeline.api.FieldModel
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig
import com.only4.cap4k.plugin.pipeline.api.RequestTrait
import com.only4.cap4k.plugin.pipeline.api.SourceProvider
import com.only4.cap4k.plugin.pipeline.api.ValidatorParameterModel
import java.io.File
import java.util.Locale

class DesignJsonSourceProvider : SourceProvider {
    override val id: String = "design-json"

    private val supportedTags = setOf(
        "command",
        "query",
        "client",
        "api_payload",
        "domain_event",
        "validator",
    )
    private val requestTraitTags = setOf("query", "api_payload")
    private val selfToken = Regex("""(?<![A-Za-z0-9_.])self(?![A-Za-z0-9_])""", RegexOption.IGNORE_CASE)
    private val supportedValidatorTargets = setOf("CLASS", "FIELD", "VALUE_PARAMETER")
    private val validatorTargetOrder = mapOf("CLASS" to 0, "FIELD" to 1, "VALUE_PARAMETER" to 2)
    private val supportedValidatorValueTypes = setOf("Any", "String", "Long", "Int", "Boolean")
    private val supportedValidatorParameterTypes = setOf("String", "Int", "Long", "Boolean")
    private val reservedValidatorParameterNames = setOf("message", "groups", "payload")
    private val validatorParameterNamePattern = Regex("""[A-Za-z_][A-Za-z0-9_]*""")
    private val kotlinReservedNames = setOf(
        "as",
        "break",
        "catch",
        "class",
        "continue",
        "do",
        "else",
        "false",
        "finally",
        "for",
        "fun",
        "if",
        "import",
        "in",
        "interface",
        "is",
        "null",
        "object",
        "package",
        "return",
        "super",
        "this",
        "throw",
        "true",
        "try",
        "typealias",
        "typeof",
        "val",
        "var",
        "when",
        "while",
    )
    private val intLiteralPattern = Regex("""-?\d+""")
    private val longLiteralPattern = Regex("""-?\d+[lL]?""")

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
            val tag = parseTag(rawTag, name)
            val requestFields = parseFields(obj["requestFields"]?.asJsonArray)
            val responseFields = parseFields(obj["responseFields"]?.asJsonArray)
            val traits = parseTraits(obj, tag, name)
            val validator = tag == "validator"
            val targets = if (validator) parseValidatorTargets(obj, name) else emptyList()
            val valueType = if (validator) parseValidatorValueType(obj, name, targets) else null
            val parameters = if (validator) parseValidatorParameters(obj, name) else emptyList()
            validateReservedFields(tag, name, requestFields)
            validateNoSelfTypes(name, requestFields + responseFields)
            DesignSpecEntry(
                tag = tag,
                packageName = readPackageName(obj["package"]?.asString, tag),
                name = name,
                description = obj["desc"]?.asString ?: "",
                aggregates = obj["aggregates"]?.asJsonArray?.map { it.asString } ?: emptyList(),
                persist = obj["persist"]?.asBoolean,
                traits = traits,
                requestFields = requestFields,
                responseFields = responseFields,
                message = if (validator) obj["message"]?.asString ?: "校验未通过" else null,
                targets = targets,
                valueType = valueType,
                parameters = parameters,
            )
        }
    }

    private fun parseTag(rawTag: String, name: String): String {
        require(rawTag in supportedTags) {
            "unsupported design tag for $name: $rawTag"
        }
        return rawTag
    }

    private fun parseTraits(obj: JsonObject, tag: String, name: String): Set<RequestTrait> {
        val rawTraits = obj["traits"]
            ?.asJsonArray
            ?.map { it.asString.trim() }
            ?.filter { it.isNotEmpty() }
            ?: emptyList()

        require(rawTraits.isEmpty() || tag in requestTraitTags) {
            "design entry $name cannot use request traits on tag: $tag"
        }

        val traits = rawTraits.map { rawTrait ->
            val normalized = rawTrait.uppercase(Locale.ROOT)
            runCatching { RequestTrait.valueOf(normalized) }.getOrElse {
                throw IllegalArgumentException("design entry $name has unsupported trait: $rawTrait")
            }
        }.toSet()

        return traits
    }

    private fun parseValidatorTargets(obj: JsonObject, validatorName: String): List<String> {
        val targets = obj["targets"]
            ?.asJsonArray
            ?.map { it.asString.trim().uppercase(Locale.ROOT) }
            ?.filter { it.isNotEmpty() }
            ?: listOf("FIELD", "VALUE_PARAMETER")
        require(targets.isNotEmpty()) {
            "validator $validatorName must declare at least one target"
        }
        targets.forEach { target ->
            require(target in supportedValidatorTargets) {
                "validator $validatorName has unsupported target: $target"
            }
        }
        return targets
            .distinct()
            .sortedBy { validatorTargetOrder[it] ?: Int.MAX_VALUE }
    }

    private fun parseValidatorValueType(
        obj: JsonObject,
        validatorName: String,
        targets: List<String>,
    ): String {
        val valueType = obj["valueType"]
            ?.asString
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: if ("CLASS" in targets) "Any" else "Long"
        require(valueType in supportedValidatorValueTypes) {
            "validator $validatorName has unsupported valueType: $valueType"
        }
        require("CLASS" !in targets || valueType == "Any") {
            "validator $validatorName cannot target CLASS with valueType: $valueType"
        }
        return valueType
    }

    private fun parseValidatorParameters(obj: JsonObject, validatorName: String): List<ValidatorParameterModel> {
        val array = obj["parameters"]?.asJsonArray ?: return emptyList()
        val names = mutableSetOf<String>()
        return array.map { element ->
            val parameter = element.asJsonObject
            val name = parameter["name"]?.asString?.trim().orEmpty()
            require(name.isNotEmpty()) {
                "validator $validatorName parameter name must not be blank"
            }
            require(name !in reservedValidatorParameterNames) {
                "validator $validatorName parameter name is reserved: $name"
            }
            require(name.isValidKotlinIdentifier()) {
                "validator $validatorName parameter name is not a valid Kotlin identifier: $name"
            }
            require(names.add(name)) {
                "validator $validatorName has duplicate parameter: $name"
            }
            val type = parameter["type"]?.asString?.trim().orEmpty()
            require(type in supportedValidatorParameterTypes) {
                "validator $validatorName parameter $name has unsupported type: $type"
            }
            val nullable = parameter["nullable"]?.asBoolean ?: false
            require(!nullable) {
                "validator $validatorName parameter $name cannot be nullable"
            }
            ValidatorParameterModel(
                name = name,
                type = type,
                nullable = false,
                defaultValue = validateValidatorParameterDefault(
                    validatorName = validatorName,
                    parameterName = name,
                    type = type,
                    defaultValue = parameter["defaultValue"]?.asString,
                ),
            )
        }
    }

    private fun String.isValidKotlinIdentifier(): Boolean =
        validatorParameterNamePattern.matches(this) && this !in kotlinReservedNames

    private fun validateValidatorParameterDefault(
        validatorName: String,
        parameterName: String,
        type: String,
        defaultValue: String?,
    ): String? {
        val raw = defaultValue ?: return null
        if (type == "String") {
            return raw
        }
        val value = raw.trim()
        when (type) {
            "Int" -> require(intLiteralPattern.matches(value) && value.toIntOrNull() != null) {
                "validator $validatorName parameter $parameterName has invalid Int defaultValue: $raw"
            }
            "Long" -> {
                require(longLiteralPattern.matches(value)) {
                    "validator $validatorName parameter $parameterName has invalid Long defaultValue: $raw"
                }
                val numeric = value.removeSuffix("l").removeSuffix("L")
                require(numeric.toLongOrNull() != null) {
                    "validator $validatorName parameter $parameterName has invalid Long defaultValue: $raw"
                }
            }
            "Boolean" -> require(value == "true" || value == "false") {
                "validator $validatorName parameter $parameterName has invalid Boolean defaultValue: $raw"
            }
        }
        return value
    }

    private fun validateNoSelfTypes(name: String, fields: List<FieldModel>) {
        fields.firstOrNull { field -> selfToken.containsMatchIn(field.type) }?.let { field ->
            throw IllegalArgumentException(
                "design entry $name field ${field.name} must use an explicit type name instead of self",
            )
        }
    }

    private fun validateReservedFields(tag: String, name: String, requestFields: List<FieldModel>) {
        if (tag.lowercase(Locale.ROOT) != "domain_event") {
            return
        }
        require(requestFields.none { it.name.equals("entity", ignoreCase = true) }) {
            "domain_event $name request field 'entity' is reserved and derived from aggregates[0]."
        }
    }

    private fun readPackageName(packageName: String?, tag: String): String {
        if (packageName != null) {
            return packageName
        }
        if (tag.lowercase(Locale.ROOT) == "domain_event") {
            return ""
        }
        error("design entry package is required for tag: $tag")
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

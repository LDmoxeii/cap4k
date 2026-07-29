package com.only4.cap4k.plugin.pipeline.source.valueobject

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig
import com.only4.cap4k.plugin.pipeline.api.SemanticFieldSnapshot
import com.only4.cap4k.plugin.pipeline.api.SourceProvider
import com.only4.cap4k.plugin.pipeline.api.ValueObjectDeclarationSnapshot
import com.only4.cap4k.plugin.pipeline.api.ValueObjectManifestSnapshot
import com.only4.cap4k.plugin.pipeline.api.ValueObjectPersistenceSnapshot
import java.nio.file.Path

class ValueObjectManifestSourceProvider : SourceProvider {
    override val id: String = "value-object-manifest"

    override fun collect(config: ProjectConfig): ValueObjectManifestSnapshot {
        val sourceFiles = config.sources[id]
            ?.options
            ?.get("files")
            .asPathList()
        val files = sourceFiles.ifEmpty { config.typeRegistry.valueObjectManifestFiles.map(Path::of) }
        return load(files)
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
            JsonParser.parseReader(reader).asJsonArray
        }
        return definitions.map { element -> element.asJsonObject.toValueObject() }
    }

    private fun JsonObject.toValueObject(): ValueObjectDeclarationSnapshot {
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
                val fieldJson = fieldElement.asJsonObject
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

    private fun JsonObject.parsePersistence(name: String): ValueObjectPersistenceSnapshot? {
        if (!has("persistence")) {
            return null
        }
        val element = get("persistence")
        require(!element.isJsonNull && element.isJsonObject) {
            "value object $name persistence must be an object"
        }
        val persistence = element.asJsonObject
        val kind = persistence.requiredString("kind")
        require(kind == "json") {
            "value object $name persistence.kind is unsupported: $kind"
        }
        val unsupportedOptions = persistence.keySet().filter { it != "kind" }.sorted()
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

private fun JsonObject.requiredString(field: String): String {
    require(has(field) && !get(field).isJsonNull) {
        "value object manifest field $field is required"
    }
    return get(field).asString
}

private fun JsonObject.optionalString(field: String): String? =
    if (has(field) && !get(field).isJsonNull) get(field).asString else null

private fun JsonObject.optionalArray(field: String): List<JsonElement> =
    if (has(field) && !get(field).isJsonNull) getAsJsonArray(field).toList() else emptyList()

private fun JsonObject.optionalStringArray(field: String): List<String> =
    if (has(field) && !get(field).isJsonNull) getAsJsonArray(field).map { it.asString } else emptyList()

private fun Any?.asPathList(): List<Path> =
    when (this) {
        null -> emptyList()
        is Iterable<*> -> mapNotNull { it?.toString()?.let(Path::of) }
        is Array<*> -> mapNotNull { it?.toString()?.let(Path::of) }
        else -> listOf(Path.of(toString()))
    }

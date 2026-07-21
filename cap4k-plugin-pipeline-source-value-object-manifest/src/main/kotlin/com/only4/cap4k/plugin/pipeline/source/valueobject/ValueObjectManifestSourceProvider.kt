package com.only4.cap4k.plugin.pipeline.source.valueobject

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.only4.cap4k.plugin.pipeline.api.FieldModel
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig
import com.only4.cap4k.plugin.pipeline.api.SourceProvider
import com.only4.cap4k.plugin.pipeline.api.ValueObjectManifestSnapshot
import com.only4.cap4k.plugin.pipeline.api.ValueObjectModel
import com.only4.cap4k.plugin.pipeline.api.ValueObjectStorage
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
        val valueObjects = files.flatMap { file -> parseFile(file) }
        validateDuplicateNames(valueObjects)
        return ValueObjectManifestSnapshot(valueObjects = valueObjects)
    }

    private fun parseFile(file: Path): List<ValueObjectModel> {
        val definitions = file.toFile().reader(Charsets.UTF_8).use { reader ->
            JsonParser.parseReader(reader).asJsonArray
        }
        return definitions.map { element -> element.asJsonObject.toValueObject() }
    }

    private fun JsonObject.toValueObject(): ValueObjectModel {
        val name = requiredString("name")
        val removedFields = listOf("scope", "aggregate").filter(::has)
        require(removedFields.isEmpty()) {
            "value object $name fields ${removedFields.joinToString(" and ")} are removed; use aggregates instead"
        }
        val aggregates = optionalStringArray("aggregates")
        require(aggregates.size <= 1) {
            "value object $name may declare at most one aggregate"
        }
        return ValueObjectModel(
            name = name,
            packageName = requiredString("package"),
            aggregates = aggregates,
            storage = valueObjectStorage(optionalString("storage") ?: "json", name),
            fields = optionalArray("fields").map { fieldElement ->
                val fieldJson = fieldElement.asJsonObject
                FieldModel(
                    name = fieldJson.requiredString("name"),
                    type = fieldJson.requiredString("type"),
                    nullable = fieldJson.optionalBoolean("nullable") ?: false,
                    defaultValue = fieldJson.optionalString("defaultValue"),
                )
            },
            description = optionalString("description"),
        )
    }

    private fun validateDuplicateNames(valueObjects: List<ValueObjectModel>) {
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

    private fun valueObjectStorage(value: String, name: String): ValueObjectStorage =
        when (value) {
            "json" -> ValueObjectStorage.JSON
            else -> throw IllegalArgumentException("value object $name storage must be json")
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

private fun JsonObject.optionalBoolean(field: String): Boolean? =
    if (has(field) && !get(field).isJsonNull) get(field).asBoolean else null

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

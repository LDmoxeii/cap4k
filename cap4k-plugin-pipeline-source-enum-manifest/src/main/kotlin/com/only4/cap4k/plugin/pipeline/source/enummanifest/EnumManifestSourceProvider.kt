package com.only4.cap4k.plugin.pipeline.source.enummanifest

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.only4.cap4k.plugin.pipeline.api.EnumItemModel
import com.only4.cap4k.plugin.pipeline.api.EnumManifestSnapshot
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig
import com.only4.cap4k.plugin.pipeline.api.SharedEnumDefinition
import com.only4.cap4k.plugin.pipeline.api.SourceProvider
import java.io.File

class EnumManifestSourceProvider : SourceProvider {
    override val id: String = "enum-manifest"

    override fun collect(config: ProjectConfig): EnumManifestSnapshot {
        val options = config.sources[id]?.options ?: emptyMap()
        val definitions = resolveFiles(options).flatMap(::parseFile)
        val duplicate = definitions
            .groupingBy { it.typeName }
            .eachCount()
            .entries
            .firstOrNull { it.value > 1 }
            ?.key
        require(duplicate == null) {
            "duplicate shared enum definition: $duplicate"
        }
        return EnumManifestSnapshot(definitions = definitions)
    }

    private fun resolveFiles(options: Map<String, Any?>): List<File> {
        val filePaths = options["files"] as? List<*> ?: emptyList<Any?>()
        return filePaths.map { File(it.toString()) }
    }

    private fun parseFile(file: File): List<SharedEnumDefinition> {
        val definitions = file.reader(Charsets.UTF_8).use { reader ->
            JsonParser.parseReader(reader).asJsonArray
        }
        return definitions.map { element ->
            val json = element.asJsonObject
            SharedEnumDefinition(
                typeName = json.requiredString("name"),
                packageName = json.requiredString("package"),
                generateTranslation = json["generateTranslation"]?.asBoolean ?: false,
                items = json.requiredArray("items").map { item ->
                    val itemJson = item.asJsonObject
                    EnumItemModel(
                        value = itemJson.requiredInt("value"),
                        name = itemJson.requiredString("name"),
                        description = itemJson.requiredString("desc"),
                    )
                }
            )
        }
    }
}

private fun JsonObject.requiredString(field: String): String = get(field).asString

private fun JsonObject.requiredInt(field: String): Int = get(field).asInt

private fun JsonObject.requiredArray(field: String) = getAsJsonArray(field)

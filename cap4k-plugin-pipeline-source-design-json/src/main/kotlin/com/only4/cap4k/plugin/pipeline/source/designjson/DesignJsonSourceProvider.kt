package com.only4.cap4k.plugin.pipeline.source.designjson

import com.google.gson.JsonArray
import com.google.gson.JsonParser
import com.only4.cap4k.plugin.pipeline.api.DesignSpecEntry
import com.only4.cap4k.plugin.pipeline.api.DesignSpecSnapshot
import com.only4.cap4k.plugin.pipeline.api.FieldModel
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig
import com.only4.cap4k.plugin.pipeline.api.SourceProvider
import java.io.File

class DesignJsonSourceProvider : SourceProvider {
    override val id: String = "design-json"

    override fun collect(config: ProjectConfig): DesignSpecSnapshot {
        val files = config.sources[id]?.options?.get("files") as? List<*> ?: emptyList<Any>()
        val entries = files
            .map { File(it.toString()) }
            .flatMap { parseFile(it) }
        return DesignSpecSnapshot(entries = entries)
    }

    private fun parseFile(file: File): List<DesignSpecEntry> {
        val array = file.reader(Charsets.UTF_8).use { JsonParser.parseReader(it).asJsonArray }
        return array.map { element ->
            val obj = element.asJsonObject
            DesignSpecEntry(
                tag = obj["tag"].asString,
                packageName = obj["package"].asString,
                name = obj["name"].asString,
                description = obj["desc"]?.asString ?: "",
                aggregates = obj["aggregates"]?.asJsonArray?.map { it.asString } ?: emptyList(),
                requestFields = parseFields(obj["requestFields"]?.asJsonArray),
                responseFields = parseFields(obj["responseFields"]?.asJsonArray),
            )
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

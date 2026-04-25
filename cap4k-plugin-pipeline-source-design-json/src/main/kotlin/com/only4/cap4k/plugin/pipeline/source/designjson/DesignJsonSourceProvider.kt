package com.only4.cap4k.plugin.pipeline.source.designjson

import com.google.gson.JsonArray
import com.google.gson.JsonParser
import com.only4.cap4k.plugin.pipeline.api.DesignSpecEntry
import com.only4.cap4k.plugin.pipeline.api.DesignSpecSnapshot
import com.only4.cap4k.plugin.pipeline.api.FieldModel
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig
import com.only4.cap4k.plugin.pipeline.api.SourceProvider
import java.io.File
import java.util.Locale

class DesignJsonSourceProvider : SourceProvider {
    override val id: String = "design-json"

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
            val tag = obj["tag"].asString
            DesignSpecEntry(
                tag = tag,
                packageName = readPackageName(obj["package"]?.asString, tag),
                name = obj["name"].asString,
                description = obj["desc"]?.asString ?: "",
                aggregates = obj["aggregates"]?.asJsonArray?.map { it.asString } ?: emptyList(),
                persist = obj["persist"]?.asBoolean,
                requestFields = parseFields(obj["requestFields"]?.asJsonArray),
                responseFields = parseFields(obj["responseFields"]?.asJsonArray),
            )
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

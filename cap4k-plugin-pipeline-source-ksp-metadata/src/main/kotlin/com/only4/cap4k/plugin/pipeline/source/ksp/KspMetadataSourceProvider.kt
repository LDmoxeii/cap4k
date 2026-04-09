package com.only4.cap4k.plugin.pipeline.source.ksp

import com.google.gson.JsonParser
import com.only4.cap4k.plugin.pipeline.api.AggregateMetadataRecord
import com.only4.cap4k.plugin.pipeline.api.KspMetadataSnapshot
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig
import com.only4.cap4k.plugin.pipeline.api.SourceProvider
import java.io.File

class KspMetadataSourceProvider : SourceProvider {
    override val id: String = "ksp-metadata"

    override fun collect(config: ProjectConfig): KspMetadataSnapshot {
        val inputDirOption = config.sources[id]?.options?.get("inputDir")?.toString()?.trim()
        require(!inputDirOption.isNullOrEmpty()) { "ksp-metadata source requires a non-blank inputDir option." }

        val inputDir = File(inputDirOption)
        require(inputDir.exists()) { "ksp-metadata inputDir does not exist: ${inputDir.path}" }
        require(inputDir.isDirectory) { "ksp-metadata inputDir is not a directory: ${inputDir.path}" }

        val aggregates = inputDir
            .listFiles()
            ?.filter { file ->
                file.isFile &&
                    file.name.startsWith("aggregate-") &&
                    file.name.endsWith(".json")
            }
            ?.sortedBy { it.name }
            ?.map { parseAggregateFile(it) }
            ?: emptyList()
        return KspMetadataSnapshot(aggregates = aggregates)
    }

    private fun parseAggregateFile(file: File): AggregateMetadataRecord {
        val root = file.reader(Charsets.UTF_8).use { JsonParser.parseReader(it).asJsonObject }
        val aggregateRoot = root.getAsJsonObject("aggregateRoot")
        return AggregateMetadataRecord(
            aggregateName = root.get("aggregateName").asString,
            rootQualifiedName = aggregateRoot.get("qualifiedName").asString,
            rootPackageName = aggregateRoot.get("packageName").asString,
            rootClassName = aggregateRoot.get("className").asString,
        )
    }
}

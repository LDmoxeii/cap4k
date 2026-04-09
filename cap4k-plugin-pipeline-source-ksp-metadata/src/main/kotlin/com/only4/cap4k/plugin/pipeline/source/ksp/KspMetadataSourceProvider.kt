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
        val inputDir = File(config.sources[id]?.options?.get("inputDir").toString())
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

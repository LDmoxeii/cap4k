package com.only4.cap4k.codegen.context.design.builders

import com.google.gson.Gson
import com.only4.cap4k.codegen.context.ContextBuilder
import com.only4.cap4k.codegen.context.design.MutableDesignContext
import com.only4.cap4k.codegen.context.design.models.AggregateInfo
import com.only4.cap4k.codegen.ksp.models.AggregateMetadata
import java.io.File

class KspMetadataContextBuilder(
    private val metadataPath: String,
) : ContextBuilder<MutableDesignContext> {

    override val order: Int = 15

    private val gson = Gson()

    override fun build(context: MutableDesignContext) {
        val dir = File(metadataPath)
        if (!dir.exists() || !dir.isDirectory) {
            return
        }

        val perAggregateFiles =
            dir.listFiles { f -> f.isFile && f.name.startsWith("aggregate-") && f.name.endsWith(".json") }
                ?.toList()
                ?: emptyList()

        if (perAggregateFiles.isEmpty()) return

        val aggregates = perAggregateFiles.mapNotNull { parseSingleAggregateMetadata(it) }
        processAggregates(aggregates, context)
    }

    private fun parseSingleAggregateMetadata(file: File): AggregateMetadata? {
        return try {
            gson.fromJson(file.readText(), AggregateMetadata::class.java)
        } catch (e: Exception) {
            null
        }
    }

    private fun processAggregates(
        aggregates: List<AggregateMetadata>,
        context: MutableDesignContext,
    ) {
        aggregates.forEach { metadata ->
            val aggregateInfo = AggregateInfo.fromKspMetadata(
                metadata = metadata,
                modulePath = context.domainPath
            )

            context.aggregateMap[metadata.aggregateName] = aggregateInfo
        }
    }
}

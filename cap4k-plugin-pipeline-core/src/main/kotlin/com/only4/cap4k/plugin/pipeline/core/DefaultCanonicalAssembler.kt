package com.only4.cap4k.plugin.pipeline.core

import com.only4.cap4k.plugin.pipeline.api.CanonicalModel
import com.only4.cap4k.plugin.pipeline.api.DesignSpecSnapshot
import com.only4.cap4k.plugin.pipeline.api.KspMetadataSnapshot
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig
import com.only4.cap4k.plugin.pipeline.api.RequestKind
import com.only4.cap4k.plugin.pipeline.api.RequestModel
import com.only4.cap4k.plugin.pipeline.api.SourceSnapshot
import java.util.Locale

interface CanonicalAssembler {
    fun assemble(config: ProjectConfig, snapshots: List<SourceSnapshot>): CanonicalModel
}

class DefaultCanonicalAssembler : CanonicalAssembler {
    override fun assemble(config: ProjectConfig, snapshots: List<SourceSnapshot>): CanonicalModel {
        val designSnapshot = snapshots.filterIsInstance<DesignSpecSnapshot>().firstOrNull() ?: return CanonicalModel()

        val aggregateLookup = snapshots
            .filterIsInstance<KspMetadataSnapshot>()
            .flatMap { it.aggregates }
            .associateBy { it.aggregateName }

        val requests = designSnapshot.entries.mapNotNull { entry ->
            val kind = when (entry.tag.lowercase(Locale.ROOT)) {
                "cmd", "command" -> RequestKind.COMMAND
                "qry", "query" -> RequestKind.QUERY
                else -> return@mapNotNull null
            }
            val aggregateName = entry.aggregates.firstOrNull()
            val aggregate = aggregateName?.let { aggregateLookup[it] }

            RequestModel(
                kind = kind,
                packageName = entry.packageName,
                typeName = when (kind) {
                    RequestKind.COMMAND -> "${entry.name}Cmd"
                    RequestKind.QUERY -> "${entry.name}Qry"
                },
                description = entry.description,
                aggregateName = aggregateName,
                aggregatePackageName = aggregate?.rootPackageName,
                requestFields = entry.requestFields,
                responseFields = entry.responseFields,
            )
        }

        return CanonicalModel(requests = requests)
    }
}

package com.only4.cap4k.plugin.pipeline.core

import com.only4.cap4k.plugin.pipeline.api.AnalysisEdgeModel
import com.only4.cap4k.plugin.pipeline.api.AnalysisGraphModel
import com.only4.cap4k.plugin.pipeline.api.AnalysisNodeModel
import com.only4.cap4k.plugin.pipeline.api.CanonicalModel
import com.only4.cap4k.plugin.pipeline.api.DbSchemaSnapshot
import com.only4.cap4k.plugin.pipeline.api.DesignSpecSnapshot
import com.only4.cap4k.plugin.pipeline.api.EntityModel
import com.only4.cap4k.plugin.pipeline.api.FieldModel
import com.only4.cap4k.plugin.pipeline.api.IrAnalysisSnapshot
import com.only4.cap4k.plugin.pipeline.api.KspMetadataSnapshot
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig
import com.only4.cap4k.plugin.pipeline.api.RequestKind
import com.only4.cap4k.plugin.pipeline.api.RequestModel
import com.only4.cap4k.plugin.pipeline.api.RepositoryModel
import com.only4.cap4k.plugin.pipeline.api.SchemaModel
import com.only4.cap4k.plugin.pipeline.api.SourceSnapshot
import java.util.Locale

interface CanonicalAssembler {
    fun assemble(config: ProjectConfig, snapshots: List<SourceSnapshot>): CanonicalModel
}

class DefaultCanonicalAssembler : CanonicalAssembler {
    override fun assemble(config: ProjectConfig, snapshots: List<SourceSnapshot>): CanonicalModel {
        val designSnapshot = snapshots.filterIsInstance<DesignSpecSnapshot>().firstOrNull()
        val dbTables = snapshots
            .filterIsInstance<DbSchemaSnapshot>()
            .flatMap { it.tables }

        val aggregateLookup = snapshots
            .filterIsInstance<KspMetadataSnapshot>()
            .flatMap { it.aggregates }
            .associateBy { it.aggregateName }

        val analysisSnapshot = snapshots.filterIsInstance<IrAnalysisSnapshot>().firstOrNull()

        val requests = designSnapshot?.entries.orEmpty().mapNotNull { entry ->
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

        val aggregateModels = dbTables.map { table ->
            require(table.primaryKey.isNotEmpty()) { "db table ${table.tableName} must define a primary key" }
            require(table.primaryKey.size == 1) { "db table ${table.tableName} must define a single-column primary key" }

            val entityName = AggregateNaming.entityName(table.tableName)
            val schemaName = AggregateNaming.schemaName(table.tableName)
            val repositoryName = AggregateNaming.repositoryName(table.tableName)
            val segment = AggregateNaming.tableSegment(table.tableName)
            val fields = table.columns.map {
                FieldModel(
                    name = it.name,
                    type = it.kotlinType,
                    nullable = it.nullable,
                    defaultValue = it.defaultValue,
                )
            }
            val idField = fields.first { it.name == table.primaryKey.first() }

            Triple(
                SchemaModel(
                    name = schemaName,
                    packageName = "${config.basePackage}.domain._share.meta.$segment",
                    entityName = entityName,
                    comment = table.comment,
                    fields = fields,
                ),
                EntityModel(
                    name = entityName,
                    packageName = "${config.basePackage}.domain.aggregates.$segment",
                    tableName = table.tableName,
                    comment = table.comment,
                    fields = fields,
                    idField = idField,
                ),
                RepositoryModel(
                    name = repositoryName,
                    packageName = "${config.basePackage}.adapter.domain.repositories",
                    entityName = entityName,
                    idType = idField.type,
                ),
            )
        }

        val analysisGraph = analysisSnapshot?.let {
            AnalysisGraphModel(
                inputDirs = it.inputDirs,
                nodes = it.nodes.map { node ->
                    AnalysisNodeModel(
                        id = node.id,
                        name = node.name,
                        fullName = node.fullName,
                        type = node.type,
                    )
                },
                edges = it.edges.map { edge ->
                    AnalysisEdgeModel(
                        fromId = edge.fromId,
                        toId = edge.toId,
                        type = edge.type,
                        label = edge.label,
                    )
                },
            )
        }

        return CanonicalModel(
            requests = requests,
            schemas = aggregateModels.map { it.first },
            entities = aggregateModels.map { it.second },
            repositories = aggregateModels.map { it.third },
            analysisGraph = analysisGraph,
        )
    }
}

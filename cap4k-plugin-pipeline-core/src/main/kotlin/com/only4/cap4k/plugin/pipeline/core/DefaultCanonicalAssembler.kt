package com.only4.cap4k.plugin.pipeline.core

import com.only4.cap4k.plugin.pipeline.api.AnalysisEdgeModel
import com.only4.cap4k.plugin.pipeline.api.AnalysisGraphModel
import com.only4.cap4k.plugin.pipeline.api.AnalysisNodeModel
import com.only4.cap4k.plugin.pipeline.api.AggregateMetadataRecord
import com.only4.cap4k.plugin.pipeline.api.AggregateDiagnostics
import com.only4.cap4k.plugin.pipeline.api.ApiPayloadModel
import com.only4.cap4k.plugin.pipeline.api.CanonicalAssemblyResult
import com.only4.cap4k.plugin.pipeline.api.CanonicalModel
import com.only4.cap4k.plugin.pipeline.api.DomainEventModel
import com.only4.cap4k.plugin.pipeline.api.DesignElementSnapshot
import com.only4.cap4k.plugin.pipeline.api.DesignSpecEntry
import com.only4.cap4k.plugin.pipeline.api.DrawingBoardElementModel
import com.only4.cap4k.plugin.pipeline.api.DrawingBoardFieldModel
import com.only4.cap4k.plugin.pipeline.api.DrawingBoardModel
import com.only4.cap4k.plugin.pipeline.api.DbSchemaSnapshot
import com.only4.cap4k.plugin.pipeline.api.DesignSpecSnapshot
import com.only4.cap4k.plugin.pipeline.api.EntityModel
import com.only4.cap4k.plugin.pipeline.api.EnumManifestSnapshot
import com.only4.cap4k.plugin.pipeline.api.FieldModel
import com.only4.cap4k.plugin.pipeline.api.IrAnalysisSnapshot
import com.only4.cap4k.plugin.pipeline.api.KspMetadataSnapshot
import com.only4.cap4k.plugin.pipeline.api.PipelineDiagnosticsException
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig
import com.only4.cap4k.plugin.pipeline.api.PipelineDiagnostics
import com.only4.cap4k.plugin.pipeline.api.RequestKind
import com.only4.cap4k.plugin.pipeline.api.RequestModel
import com.only4.cap4k.plugin.pipeline.api.RepositoryModel
import com.only4.cap4k.plugin.pipeline.api.SchemaModel
import com.only4.cap4k.plugin.pipeline.api.SourceSnapshot
import com.only4.cap4k.plugin.pipeline.api.UnsupportedAggregateTable
import com.only4.cap4k.plugin.pipeline.api.UnsupportedTablePolicy
import com.only4.cap4k.plugin.pipeline.api.ValidatorModel
import java.util.Locale

interface CanonicalAssembler {
    fun assemble(config: ProjectConfig, snapshots: List<SourceSnapshot>): CanonicalAssemblyResult
}

class DefaultCanonicalAssembler : CanonicalAssembler {
    override fun assemble(config: ProjectConfig, snapshots: List<SourceSnapshot>): CanonicalAssemblyResult {
        val designSnapshot = snapshots.filterIsInstance<DesignSpecSnapshot>().firstOrNull()
        val dbSnapshot = snapshots.filterIsInstance<DbSchemaSnapshot>().firstOrNull()
        val sharedEnums = snapshots.filterIsInstance<EnumManifestSnapshot>().flatMap { it.definitions }

        val aggregateLookup = snapshots
            .filterIsInstance<KspMetadataSnapshot>()
            .flatMap { it.aggregates }
            .associateBy { it.aggregateName }

        val analysisSnapshot = snapshots.filterIsInstance<IrAnalysisSnapshot>().firstOrNull()

        val requests = designSnapshot?.entries.orEmpty().mapNotNull { entry ->
            val kind = when (entry.tag.lowercase(Locale.ROOT)) {
                "cmd", "command" -> RequestKind.COMMAND
                "qry", "query" -> RequestKind.QUERY
                "cli", "client", "clients" -> RequestKind.CLIENT
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
                    RequestKind.CLIENT -> "${entry.name}Cli"
                },
                description = entry.description,
                aggregateName = aggregateName,
                aggregatePackageName = aggregate?.rootPackageName,
                requestFields = entry.requestFields,
                responseFields = entry.responseFields,
            )
        }

        val validators = designSnapshot?.entries.orEmpty()
            .asSequence()
            .filter { entry -> entry.tag.lowercase(Locale.ROOT) == "validator" }
            .map { entry ->
                ValidatorModel(
                    packageName = entry.packageName,
                    typeName = entry.name.normalizeValidatorTypeName(),
                    description = entry.description,
                    valueType = "Long",
                )
            }
            .toList()

        val apiPayloads = designSnapshot?.entries.orEmpty()
            .asSequence()
            .filter { entry -> entry.tag.lowercase(Locale.ROOT) == "api_payload" }
            .map { entry ->
                ApiPayloadModel(
                    packageName = entry.packageName,
                    typeName = entry.name.normalizeUpperCamelTypeName(),
                    description = entry.description,
                    requestFields = entry.requestFields,
                    responseFields = entry.responseFields,
                )
            }
            .toList()

        val domainEvents = designSnapshot?.entries.orEmpty()
            .asSequence()
            .filter { entry -> entry.tag.lowercase(Locale.ROOT) == "domain_event" }
            .map { entry ->
                val aggregateName = resolveDomainEventAggregateName(entry)
                val aggregate = resolveDomainEventAggregateMetadata(entry, aggregateName, aggregateLookup)
                DomainEventModel(
                    packageName = entry.packageName,
                    typeName = entry.name.toDomainEventTypeName(),
                    description = entry.description,
                    aggregateName = aggregateName,
                    aggregatePackageName = aggregate.rootPackageName,
                    persist = entry.persist ?: false,
                    fields = entry.requestFields,
                )
            }
            .toList()

        val aggregatePolicy = config.generators["aggregate"]
            ?.options
            ?.get("unsupportedTablePolicy")
            ?.toString()
            ?.uppercase(Locale.ROOT)
            ?.let(UnsupportedTablePolicy::valueOf)
            ?: UnsupportedTablePolicy.FAIL

        val supportedTables = mutableListOf<com.only4.cap4k.plugin.pipeline.api.DbTableSnapshot>()
        val unsupportedTables = mutableListOf<UnsupportedAggregateTable>()
        dbSnapshot?.tables.orEmpty().forEach { table ->
            val unsupportedReason = when {
                table.primaryKey.isEmpty() -> "missing_primary_key"
                table.primaryKey.size != 1 -> "composite_primary_key"
                else -> null
            }

            if (unsupportedReason == null) {
                supportedTables += table
            } else {
                unsupportedTables += UnsupportedAggregateTable(tableName = table.tableName, reason = unsupportedReason)
            }
        }

        val supportedTableNames = supportedTables.map { it.tableName.lowercase(Locale.ROOT) }.toSet()
        val outOfScopeTableNames = dbSnapshot?.let { snapshot ->
            snapshot.discoveredTables.map { it.lowercase(Locale.ROOT) }.toSet() -
                snapshot.includedTables.map { it.lowercase(Locale.ROOT) }.toSet()
        }.orEmpty()

        if (aggregatePolicy == UnsupportedTablePolicy.FAIL && unsupportedTables.isNotEmpty()) {
            val firstUnsupported = unsupportedTables.first()
            throw PipelineDiagnosticsException(
                message = "db table ${firstUnsupported.tableName} is unsupported for aggregate generation: ${firstUnsupported.reason}",
                diagnostics = requireNotNull(
                    buildDiagnostics(
                        snapshot = dbSnapshot,
                        supportedTables = supportedTables,
                        unsupportedTables = unsupportedTables,
                    )
                ) { "aggregate diagnostics must be available for db unsupported table failures" },
            )
        }

        val aggregateRelations = AggregateRelationInference.fromTables(
            basePackage = config.basePackage,
            tables = supportedTables,
            skippedTableNames = if (aggregatePolicy == UnsupportedTablePolicy.SKIP) {
                unsupportedTables.map { it.tableName.lowercase(Locale.ROOT) }.toSet()
            } else {
                emptySet()
            },
            outOfScopeTableNames = outOfScopeTableNames,
        )

        val aggregateModels = supportedTables.map { table ->

            val entityName = AggregateNaming.entityName(table.tableName)
            val schemaName = AggregateNaming.schemaName(table.tableName)
            val repositoryName = AggregateNaming.repositoryName(table.tableName)
            val segment = AggregateNaming.tableSegment(table.tableName)
            val parentTable = table.parentTable
            val fields = table.columns.map {
                FieldModel(
                    name = it.name,
                    type = it.kotlinType,
                    nullable = it.nullable,
                    defaultValue = it.defaultValue,
                    typeBinding = it.typeBinding,
                    enumItems = it.enumItems,
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
                    uniqueConstraints = table.uniqueConstraints,
                    aggregateRoot = table.aggregateRoot,
                    valueObject = table.valueObject,
                    parentEntityName = when {
                        parentTable == null -> null
                        aggregatePolicy == UnsupportedTablePolicy.SKIP &&
                            parentTable.lowercase(Locale.ROOT) !in supportedTableNames -> null
                        parentTable.lowercase(Locale.ROOT) in outOfScopeTableNames -> null
                        else -> AggregateNaming.entityName(parentTable)
                    },
                ),
                RepositoryModel(
                    name = repositoryName,
                    packageName = "${config.basePackage}.adapter.domain.repositories",
                    entityName = entityName,
                    idType = idField.type,
                ),
            )
        }
        val entities = aggregateModels.map { it.second }
        val aggregateInverseRelations = AggregateInverseRelationInference.infer(
            entities = entities,
            relations = aggregateRelations,
            tables = supportedTables,
        )
        val aggregateEntityJpa = AggregateJpaControlInference.fromModel(
            entities = entities,
            schema = dbSnapshot,
            sharedEnums = sharedEnums,
            basePackage = config.basePackage,
        )
        val aggregatePersistenceFieldControls = AggregatePersistenceFieldBehaviorInference.infer(
            entities = entities,
            schema = dbSnapshot,
        )
        val aggregatePersistenceProviderControls = AggregatePersistenceProviderInference.infer(
            entities = entities,
            tables = supportedTables,
        )
        val aggregateIdGeneratorControls = AggregateIdGeneratorInference.infer(
            entities = entities,
            tables = supportedTables,
        )

        val diagnostics = buildDiagnostics(
            snapshot = dbSnapshot,
            supportedTables = supportedTables,
            unsupportedTables = unsupportedTables,
        )

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

        val drawingBoard = analysisSnapshot
            ?.designElements
            .orEmpty()
            .mapNotNull { it.toDrawingBoardElementOrNull() }
            .fold(linkedMapOf<String, DrawingBoardElementModel>()) { acc, element ->
                val key = drawingBoardElementKey(element)
                acc.putIfAbsent(key, element)
                acc
            }
            .values
            .toList()
            .takeIf { it.isNotEmpty() }
            ?.let { elements ->
                DrawingBoardModel(
                    elements = elements,
                    elementsByTag = elements.groupBy { it.tag },
                )
            }

        return CanonicalAssemblyResult(
            model = CanonicalModel(
                requests = requests,
                validators = validators,
                apiPayloads = apiPayloads,
                domainEvents = domainEvents,
                schemas = aggregateModels.map { it.first },
                entities = entities,
                repositories = aggregateModels.map { it.third },
                analysisGraph = analysisGraph,
                drawingBoard = drawingBoard,
                sharedEnums = sharedEnums,
                aggregateRelations = aggregateRelations,
                aggregateInverseRelations = aggregateInverseRelations,
                aggregateEntityJpa = aggregateEntityJpa,
                aggregatePersistenceFieldControls = aggregatePersistenceFieldControls,
                aggregatePersistenceProviderControls = aggregatePersistenceProviderControls,
                aggregateIdGeneratorControls = aggregateIdGeneratorControls,
            ),
            diagnostics = diagnostics,
        )
    }

    private fun DesignElementSnapshot.toDrawingBoardElementOrNull(): DrawingBoardElementModel? {
        val normalizedTag = tag.lowercase(Locale.ROOT)
        if (normalizedTag !in SupportedDrawingBoardTags) {
            return null
        }

        return DrawingBoardElementModel(
            tag = normalizedTag,
            packageName = packageName,
            name = name,
            description = description,
            aggregates = aggregates,
            entity = entity,
            persist = persist,
            requestFields = requestFields.map { field ->
                DrawingBoardFieldModel(
                    name = field.name,
                    type = field.type,
                    nullable = field.nullable,
                    defaultValue = field.defaultValue,
                )
            },
            responseFields = responseFields.map { field ->
                DrawingBoardFieldModel(
                    name = field.name,
                    type = field.type,
                    nullable = field.nullable,
                    defaultValue = field.defaultValue,
                )
            },
        )
    }

    private fun drawingBoardElementKey(element: DrawingBoardElementModel): String {
        return "${element.tag}|${element.packageName}|${element.name}"
    }

    private fun String.normalizeValidatorTypeName(): String {
        return normalizeUpperCamelTypeName()
    }

    private fun String.normalizeUpperCamelTypeName(): String {
        val parts = trim()
            .split(UpperCamelSplitRegex)
            .filter { it.isNotEmpty() }
        if (parts.isEmpty()) {
            return ""
        }
        return parts.joinToString("") { part ->
            part.lowercase(Locale.ROOT).replaceFirstChar { character ->
                character.titlecase(Locale.ROOT)
            }
        }
    }

    private fun String.toDomainEventTypeName(): String {
        val rawName = trim()
        val candidate = when {
            rawName.endsWith("Evt") || rawName.endsWith("Event") -> rawName
            else -> "${rawName}DomainEvent"
        }
        return candidate.normalizeUpperCamelTypeName()
    }

    private fun resolveDomainEventAggregateName(entry: DesignSpecEntry): String {
        val aggregateCount = entry.aggregates.size
        require(aggregateCount == 1) {
            "domain_event ${entry.name} must declare exactly one aggregate, but found $aggregateCount."
        }
        return entry.aggregates.first()
    }

    private fun resolveDomainEventAggregateMetadata(
        entry: DesignSpecEntry,
        aggregateName: String,
        aggregateLookup: Map<String, AggregateMetadataRecord>,
    ): AggregateMetadataRecord {
        return requireNotNull(aggregateLookup[aggregateName]) {
            "domain_event ${entry.name} references missing aggregate metadata: $aggregateName."
        }
    }

    private fun buildDiagnostics(
        snapshot: DbSchemaSnapshot?,
        supportedTables: List<com.only4.cap4k.plugin.pipeline.api.DbTableSnapshot>,
        unsupportedTables: List<UnsupportedAggregateTable>,
    ): PipelineDiagnostics? {
        if (snapshot == null) {
            return null
        }

        return PipelineDiagnostics(
            aggregate = AggregateDiagnostics(
                discoveredTables = snapshot.discoveredTables,
                includedTables = snapshot.includedTables,
                excludedTables = snapshot.excludedTables,
                supportedTables = supportedTables.map { it.tableName }.sorted(),
                unsupportedTables = unsupportedTables.sortedBy { it.tableName },
            )
        )
    }

    private companion object {
        val SupportedDrawingBoardTags = setOf("cli", "cmd", "qry", "payload", "de")
        val UpperCamelSplitRegex = Regex("(?<=[a-z0-9])(?=[A-Z])|[^A-Za-z0-9]+")
    }
}

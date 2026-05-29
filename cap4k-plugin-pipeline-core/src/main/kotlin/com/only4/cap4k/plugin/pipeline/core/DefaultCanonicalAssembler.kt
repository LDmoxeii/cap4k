package com.only4.cap4k.plugin.pipeline.core

import com.only4.cap4k.plugin.pipeline.api.AnalysisEdgeModel
import com.only4.cap4k.plugin.pipeline.api.AnalysisGraphModel
import com.only4.cap4k.plugin.pipeline.api.AnalysisNodeModel
import com.only4.cap4k.plugin.pipeline.api.AggregateMetadataRecord
import com.only4.cap4k.plugin.pipeline.api.AggregateRef
import com.only4.cap4k.plugin.pipeline.api.AggregateDiagnostics
import com.only4.cap4k.plugin.pipeline.api.ArtifactLayoutResolver
import com.only4.cap4k.plugin.pipeline.api.ApiPayloadModel
import com.only4.cap4k.plugin.pipeline.api.ArtifactSelectionModel
import com.only4.cap4k.plugin.pipeline.api.CanonicalAssemblyResult
import com.only4.cap4k.plugin.pipeline.api.CanonicalModel
import com.only4.cap4k.plugin.pipeline.api.ClientModel
import com.only4.cap4k.plugin.pipeline.api.CommandModel
import com.only4.cap4k.plugin.pipeline.api.CommandVariant
import com.only4.cap4k.plugin.pipeline.api.DesignBlockModel
import com.only4.cap4k.plugin.pipeline.api.DomainEventModel
import com.only4.cap4k.plugin.pipeline.api.DesignElementSnapshot
import com.only4.cap4k.plugin.pipeline.api.DesignSpecEntry
import com.only4.cap4k.plugin.pipeline.api.DrawingBoardElementModel
import com.only4.cap4k.plugin.pipeline.api.DrawingBoardFieldModel
import com.only4.cap4k.plugin.pipeline.api.DrawingBoardModel
import com.only4.cap4k.plugin.pipeline.api.DomainServiceModel
import com.only4.cap4k.plugin.pipeline.api.DbSchemaSnapshot
import com.only4.cap4k.plugin.pipeline.api.DbTableSnapshot
import com.only4.cap4k.plugin.pipeline.api.DesignSpecSnapshot
import com.only4.cap4k.plugin.pipeline.api.EntityModel
import com.only4.cap4k.plugin.pipeline.api.EnumItemModel
import com.only4.cap4k.plugin.pipeline.api.EnumManifestSnapshot
import com.only4.cap4k.plugin.pipeline.api.FieldModel
import com.only4.cap4k.plugin.pipeline.api.IrAnalysisSnapshot
import com.only4.cap4k.plugin.pipeline.api.IntegrationEventModel
import com.only4.cap4k.plugin.pipeline.api.IntegrationEventRole
import com.only4.cap4k.plugin.pipeline.api.PipelineDiagnosticsException
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig
import com.only4.cap4k.plugin.pipeline.api.PipelineDiagnostics
import com.only4.cap4k.plugin.pipeline.api.QueryModel
import com.only4.cap4k.plugin.pipeline.api.RepositoryModel
import com.only4.cap4k.plugin.pipeline.api.SagaModel
import com.only4.cap4k.plugin.pipeline.api.SchemaModel
import com.only4.cap4k.plugin.pipeline.api.SourceSnapshot
import com.only4.cap4k.plugin.pipeline.api.StrongIdKind
import com.only4.cap4k.plugin.pipeline.api.StrongIdModel
import com.only4.cap4k.plugin.pipeline.api.TypeRegistryModel
import com.only4.cap4k.plugin.pipeline.api.UnsupportedAggregateTable
import com.only4.cap4k.plugin.pipeline.api.UnsupportedTablePolicy
import com.only4.cap4k.plugin.pipeline.api.ValueObjectManifestSnapshot
import com.only4.cap4k.plugin.pipeline.api.ownerAggregate
import java.util.Locale

interface CanonicalAssembler {
    fun assemble(config: ProjectConfig, snapshots: List<SourceSnapshot>): CanonicalAssemblyResult
}

class DefaultCanonicalAssembler : CanonicalAssembler {
    override fun assemble(config: ProjectConfig, snapshots: List<SourceSnapshot>): CanonicalAssemblyResult {
        val artifactLayout = ArtifactLayoutResolver(config.basePackage, config.artifactLayout)
        val designSnapshot = snapshots.filterIsInstance<DesignSpecSnapshot>().firstOrNull()
        val dbSnapshot = snapshots.filterIsInstance<DbSchemaSnapshot>().firstOrNull()
        val sharedEnums = snapshots.filterIsInstance<EnumManifestSnapshot>().flatMap { it.definitions }
        val valueObjects = snapshots.filterIsInstance<ValueObjectManifestSnapshot>().flatMap { it.valueObjects }
        val typeRegistry = TypeRegistryModel(config.typeRegistry.entries)
        val designBlocks = designSnapshot?.entries.orEmpty()
            .asSequence()
            .filter { entry -> entry.tag in SupportedDesignBlockTags }
            .map { entry -> entry.toDesignBlockModel() }
            .toList()

        val analysisSnapshot = snapshots.filterIsInstance<IrAnalysisSnapshot>().firstOrNull()

        val apiPayloads = designSnapshot?.entries.orEmpty()
            .asSequence()
            .filter { entry -> entry.tag == "api_payload" }
            .map { entry ->
                ApiPayloadModel(
                    packageName = entry.packageName,
                    typeName = entry.name.normalizeUpperCamelTypeName(),
                    description = entry.description,
                    requestFields = entry.primaryFields(),
                    responseFields = entry.resultPayloadFields(),
                    traits = entry.traits,
                )
            }
            .toList()

        val domainServices = designSnapshot?.entries.orEmpty()
            .asSequence()
            .filter { entry -> entry.tag == "domain_service" }
            .map { entry ->
                DomainServiceModel(
                    name = entry.name,
                    packageName = entry.packageName,
                    description = entry.description,
                    aggregates = entry.aggregates,
                )
            }
            .toList()

        val sagas = designSnapshot?.entries.orEmpty()
            .asSequence()
            .filter { entry -> entry.tag == "saga" }
            .map { entry ->
                SagaModel(
                    name = entry.name,
                    packageName = entry.packageName,
                    description = entry.description,
                    requestFields = entry.primaryFields(),
                    responseFields = entry.resultPayloadFields(),
                )
            }
            .toList()

        val integrationEvents = designSnapshot?.entries.orEmpty()
            .asSequence()
            .filter { entry -> entry.tag == "integration_event" }
            .mapNotNull { entry ->
                val effectiveArtifacts = entry.effectiveArtifacts()
                val integrationEventArtifact = effectiveArtifacts.singleOrNull { it.family == "integration-event" }
                    ?: return@mapNotNull null

                IntegrationEventModel(
                    packageName = entry.packageName,
                    typeName = entry.name.toIntegrationEventTypeName(),
                    description = entry.description,
                    role = entry.integrationEventRole(integrationEventArtifact),
                    eventName = entry.integrationEventName(),
                    fields = entry.integrationEventRequestFields(),
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

        val supportedTables = mutableListOf<DbTableSnapshot>()
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
        val supportedTablesByName = supportedTables.associateBy { it.tableName.lowercase(Locale.ROOT) }
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
            artifactLayout = artifactLayout,
            tables = supportedTables,
            skippedTableNames = if (aggregatePolicy == UnsupportedTablePolicy.SKIP) {
                unsupportedTables.map { it.tableName.lowercase(Locale.ROOT) }.toSet()
            } else {
                emptySet()
            },
            outOfScopeTableNames = outOfScopeTableNames,
        )

        val aggregateRootIdTypesByName = supportedTables
            .mapNotNull { table ->
                generatedAggregateRootStrongIdType(table)
                    ?.let { idType -> AggregateNaming.entityName(table.tableName) to idType }
            }
            .toMap()

        val aggregateModels = supportedTables.map { table ->

            val entityName = AggregateNaming.entityName(table.tableName)
            val schemaName = AggregateNaming.schemaName(table.tableName)
            val repositoryName = AggregateNaming.repositoryName(table.tableName)
            val aggregateOwnerTable = resolveAggregateOwnerTable(table, supportedTablesByName)
            val segment = AggregateNaming.tableSegment(aggregateOwnerTable.tableName)
            val parentTable = table.parentTable
            val generatedRootIdType = generatedAggregateRootStrongIdType(table)
            val fields = table.columns.map {
                val fieldName = lowerCamelIdentifier(it.name)
                val resolvedType = resolveStrongIdFieldType(
                    column = it,
                    aggregateRootIdTypesByName = aggregateRootIdTypesByName,
                ) ?: if (isTablePrimaryKeyColumn(table, it) && generatedRootIdType != null) {
                    generatedRootIdType
                } else {
                    it.kotlinType
                }
                FieldModel(
                    name = fieldName,
                    type = resolvedType,
                    nullable = it.nullable,
                    defaultValue = it.defaultValue,
                    typeBinding = it.typeBinding,
                    enumItems = it.enumItems,
                    columnName = it.name,
                    inherited = it.inherited == true,
                )
            }
            val primaryKeyColumn = table.primaryKey.first()
            val idField = fields.first { (it.columnName ?: it.name).equals(primaryKeyColumn, ignoreCase = true) }

            Triple(
                SchemaModel(
                    name = schemaName,
                    packageName = artifactLayout.aggregateSchemaPackage(segment),
                    entityName = entityName,
                    comment = table.comment,
                    fields = fields,
                ),
                EntityModel(
                    name = entityName,
                    packageName = artifactLayout.aggregateEntityPackage(segment),
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
                if (table.aggregateRoot) {
                    RepositoryModel(
                        name = repositoryName,
                        packageName = artifactLayout.aggregateRepositoryPackage(),
                        entityName = entityName,
                        idType = idField.type,
                    )
                } else {
                    null
                },
            )
        }
        val entities = aggregateModels.map { it.second }
        val strongIds = buildStrongIds(
            config = config,
            entities = entities,
            tables = supportedTables,
        )
        val aggregateEntityMetadata = entities
            .filter { it.aggregateRoot }
            .associateBy(
                keySelector = { it.name },
                valueTransform = { entity ->
                    AggregateMetadataRecord(
                        aggregateName = entity.name,
                        rootQualifiedName = "${entity.packageName}.${entity.name}",
                        rootPackageName = entity.packageName,
                        rootClassName = entity.name,
                    )
                }
            )
        val commands = designSnapshot?.entries.orEmpty()
            .asSequence()
            .filter { entry -> entry.tag == "command" }
            .map { entry ->
                CommandModel(
                    packageName = entry.packageName,
                    typeName = "${entry.name}Cmd",
                    description = entry.description,
                    aggregateRef = entry.requestAggregateRef(aggregateEntityMetadata),
                    requestFields = entry.primaryFields(),
                    responseFields = entry.resultPayloadFields(),
                    variant = CommandVariant.DEFAULT,
                )
            }
            .toList()

        val queries = designSnapshot?.entries.orEmpty()
            .asSequence()
            .filter { entry -> entry.tag == "query" }
            .map { entry ->
                QueryModel(
                    packageName = entry.packageName,
                    typeName = "${entry.name}Qry",
                    description = entry.description,
                    aggregateRef = entry.requestAggregateRef(aggregateEntityMetadata),
                    requestFields = entry.primaryFields(),
                    responseFields = entry.resultPayloadFields(),
                    traits = entry.traits,
                )
            }
            .toList()

        val clients = designSnapshot?.entries.orEmpty()
            .asSequence()
            .filter { entry -> entry.tag == "client" }
            .map { entry ->
                ClientModel(
                    packageName = entry.packageName,
                    typeName = "${entry.name}Cli",
                    description = entry.description,
                    aggregateRef = entry.requestAggregateRef(aggregateEntityMetadata),
                    requestFields = entry.primaryFields(),
                    responseFields = entry.resultPayloadFields(),
                )
            }
            .toList()
        val domainEvents = designSnapshot?.entries.orEmpty()
            .asSequence()
            .filter { entry -> entry.tag == "domain_event" }
            .map { entry ->
                val aggregateName = resolveDomainEventAggregateName(entry)
                val aggregate = resolveDomainEventAggregateMetadata(
                    entry = entry,
                    aggregateName = aggregateName,
                    aggregateEntityMetadata = aggregateEntityMetadata,
                )
                DomainEventModel(
                    packageName = resolveDomainEventPackageKey(aggregate.rootPackageName, config),
                    typeName = entry.name.toDomainEventTypeName(),
                    description = entry.description,
                    aggregateName = aggregateName,
                    aggregatePackageName = aggregate.rootPackageName,
                    persist = entry.persist ?: false,
                    fields = entry.primaryFields().filterNot { it.name.equals("entity", ignoreCase = true) },
                )
            }
            .toList()
        val aggregateInverseRelations = AggregateInverseRelationInference.infer(
            entities = entities,
            relations = aggregateRelations,
            tables = supportedTables,
        )
        val aggregateEntityPackageByName = entities.associateBy(
            keySelector = { it.name },
            valueTransform = { it.packageName },
        )
        validateTypeManifestOwnership(sharedEnums, valueObjects)
        validateDuplicateTypeSimpleNames(
            sharedEnums = sharedEnums
                .filter { it.aggregates.isEmpty() }
                .map { it.typeName },
            localEnums = buildLocalEnumTypeNames(entities, sharedEnums),
            sharedValueObjects = valueObjects
                .filter { it.aggregates.isEmpty() }
                .map { valueObject ->
                    SharedValueObjectTypeName(
                        simpleName = valueObject.name,
                        packageName = valueObject.packageName,
                    )
                },
            localValueObjects = valueObjects
                .filter { it.ownerAggregate != null }
                .map { valueObject ->
                    val ownerAggregate = requireNotNull(valueObject.ownerAggregate)
                    LocalValueObjectTypeName(
                        owner = aggregateEntityPackageByName[ownerAggregate].orEmpty()
                            .ifBlank { ownerAggregate },
                        simpleName = valueObject.name,
                        packageName = valueObject.packageName,
                    )
                },
            typeRegistry = config.typeRegistry.entries.keys,
        )
        val aggregateEntityJpa = AggregateJpaControlInference.fromModel(
            entities = entities,
            schema = dbSnapshot,
            sharedEnums = sharedEnums,
            valueObjects = valueObjects,
            typeRegistry = config.typeRegistry.entries,
            artifactLayout = artifactLayout,
        )
        val aggregatePersistenceFieldControls = AggregatePersistenceFieldBehaviorInference.infer(
            entities = entities,
            schema = dbSnapshot,
        )
        val specialFieldResolution = if (config.isAggregateProjectionOnly()) {
            AggregateSpecialFieldResolutionResult(
                resolvedPolicies = emptyList(),
                idControls = emptyList(),
                providerControls = emptyList(),
            )
        } else {
            AggregateSpecialFieldPolicyResolver.resolve(
                config = config,
                entities = entities,
                tables = supportedTables,
            )
        }
        val aggregatePersistenceProviderControls = specialFieldResolution.providerControls
        val aggregateIdPolicyControls = specialFieldResolution.idControls

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
                designBlocks = designBlocks,
                commands = commands,
                queries = queries,
                clients = clients,
                apiPayloads = apiPayloads,
                domainEvents = domainEvents,
                schemas = aggregateModels.map { it.first },
                entities = entities,
                repositories = aggregateModels.mapNotNull { it.third },
                analysisGraph = analysisGraph,
                drawingBoard = drawingBoard,
                sharedEnums = sharedEnums,
                aggregateRelations = aggregateRelations,
                aggregateInverseRelations = aggregateInverseRelations,
                aggregateEntityJpa = aggregateEntityJpa,
                aggregatePersistenceFieldControls = aggregatePersistenceFieldControls,
                aggregatePersistenceProviderControls = aggregatePersistenceProviderControls,
                aggregateIdPolicyControls = aggregateIdPolicyControls,
                aggregateSpecialFieldResolvedPolicies = specialFieldResolution.resolvedPolicies,
                integrationEvents = integrationEvents,
                strongIds = strongIds,
                valueObjects = valueObjects,
                domainServices = domainServices,
                sagas = sagas,
                typeRegistry = typeRegistry,
            ),
            diagnostics = diagnostics,
        )
    }

    private fun resolveStrongIdFieldType(
        column: com.only4.cap4k.plugin.pipeline.api.DbColumnSnapshot,
        aggregateRootIdTypesByName: Map<String, String>,
    ): String? {
        val refAggregate = column.refAggregate?.takeIf { it.isNotBlank() }
        val refId = column.refId?.takeIf { it.isNotBlank() }
        require(!(refAggregate != null && refId != null)) {
            "conflicting @RefAggregate and @RefId annotations on the same column metadata."
        }
        if (refAggregate != null) {
            return requireNotNull(aggregateRootIdTypesByName[refAggregate]) {
                "@RefAggregate=$refAggregate does not match a generated aggregate root"
            }
        }

        return refId
    }

    private fun generatedAggregateRootStrongIdType(table: DbTableSnapshot): String? {
        if (!table.aggregateRoot) {
            return null
        }

        val primaryKeyColumn = table.primaryKey.singleOrNull() ?: return null
        val idColumn = table.columns.firstOrNull { it.name.equals(primaryKeyColumn, ignoreCase = true) }
            ?: return null
        if (idColumn.generatedValueDeclared || idColumn.generatedValueStrategy != null) {
            return null
        }
        if (!idColumn.refAggregate.isNullOrBlank() || !idColumn.refId.isNullOrBlank()) {
            return null
        }

        return aggregateRootStrongIdTypeName(AggregateNaming.entityName(table.tableName))
    }

    private fun isTablePrimaryKeyColumn(
        table: DbTableSnapshot,
        column: com.only4.cap4k.plugin.pipeline.api.DbColumnSnapshot,
    ): Boolean = table.primaryKey.any { it.equals(column.name, ignoreCase = true) }

    private fun aggregateRootStrongIdTypeName(entityName: String): String = "${entityName}Id"

    private fun buildStrongIds(
        config: ProjectConfig,
        entities: List<EntityModel>,
        tables: List<DbTableSnapshot>,
    ): List<StrongIdModel> {
        val aggregateRootStrongIds = entities
            .asSequence()
            .filter { it.aggregateRoot && it.idField.type == aggregateRootStrongIdTypeName(it.name) }
            .map { entity ->
                StrongIdModel(
                    typeName = entity.idField.type,
                    packageName = entity.packageName,
                    kind = StrongIdKind.AGGREGATE_ROOT,
                    ownerAggregateName = entity.name,
                    ownerAggregatePackageName = entity.packageName,
                )
            }

        val referenceStrongIds = tables
            .asSequence()
            .flatMap { it.columns.asSequence() }
            .onEach { column ->
                require(!(column.refAggregate?.isNotBlank() == true && column.refId?.isNotBlank() == true)) {
                    "conflicting @RefAggregate and @RefId annotations on the same column metadata."
                }
            }
            .mapNotNull { it.refId?.takeIf(String::isNotBlank) }
            .distinct()
            .map { refId ->
                StrongIdModel(
                    typeName = refId,
                    packageName = ArtifactLayoutResolver.joinPackage(config.basePackage, "domain.shared.ids"),
                    kind = StrongIdKind.REFERENCE,
                )
            }

        return (aggregateRootStrongIds + referenceStrongIds)
            .distinctBy { it.packageName to it.typeName }
            .toList()
    }

    private fun resolveAggregateOwnerTable(
        table: DbTableSnapshot,
        tablesByName: Map<String, DbTableSnapshot>,
    ): DbTableSnapshot {
        val visited = mutableSetOf<String>()
        var current = table
        while (true) {
            val currentKey = current.tableName.lowercase(Locale.ROOT)
            if (!visited.add(currentKey)) {
                return table
            }
            if (current.aggregateRoot) {
                return current
            }

            val parentKey = current.parentTable?.lowercase(Locale.ROOT) ?: return current
            current = tablesByName[parentKey] ?: return current
        }
    }

    private fun DesignSpecEntry.requestAggregateRef(
        aggregateLookup: Map<String, AggregateMetadataRecord>,
    ): AggregateRef? {
        val aggregateName = aggregates.firstOrNull() ?: return null
        val aggregate = aggregateLookup[aggregateName] ?: return null
        return AggregateRef(
            name = aggregateName,
            packageName = aggregate.rootPackageName,
        )
    }

    private fun DesignSpecEntry.primaryFields(): List<FieldModel> =
        fields.ifEmpty { requestFields }

    private fun DesignSpecEntry.resultPayloadFields(): List<FieldModel> =
        resultFields.ifEmpty { responseFields }

    private fun DesignSpecEntry.toDesignBlockModel(): DesignBlockModel {
        validateDesignBlockSharedFields()
        val artifactSelections = resolveDesignBlockArtifacts()
        return DesignBlockModel(
            tag = tag,
            packageName = packageName,
            name = name,
            description = description,
            aggregates = aggregates,
            eventName = eventName.orEmpty(),
            persist = persist,
            artifacts = artifactSelections,
            fields = fields,
            resultFields = resultFields,
        )
    }

    private fun DesignSpecEntry.validateDesignBlockSharedFields() {
        require(eventName.isNullOrBlank() || tag in EventNameTags) {
            "design entry $name cannot declare eventName on tag: $tag"
        }
        require(persist == null || tag == "domain_event") {
            "design entry $name cannot declare persist on tag: $tag"
        }
        require(resultFields.isEmpty() || tag in ResultFieldTags) {
            "design entry $name cannot declare resultFields on tag: $tag"
        }
        if (tag == "domain_event") {
            val aggregateCount = aggregates.size
            require(aggregateCount == 1) {
                "domain_event $name must declare exactly one aggregate, but found $aggregateCount."
            }
        }
    }

    private fun DesignSpecEntry.effectiveArtifacts(): List<ArtifactSelectionModel> =
        artifacts ?: defaultArtifactsFor(tag)

    private fun DesignSpecEntry.resolveDesignBlockArtifacts(): List<ArtifactSelectionModel> {
        val artifactSelections = effectiveArtifacts()
        validateArtifactSelections(artifactSelections)
        return artifactSelections
    }

    private fun DesignSpecEntry.validateArtifactSelections(artifacts: List<ArtifactSelectionModel>) {
        artifacts.forEach { artifact ->
            val allowedVariants = SupportedArtifactFamilies[artifact.family]
                ?: throw IllegalArgumentException("unsupported design artifact family on $name: ${artifact.family}")
            if (artifact.family == "integration-event") {
                require(artifact.variant in allowedVariants) {
                    if (artifact.variant.isBlank()) {
                        "design entry $name artifact integration-event must declare variant inbound or outbound"
                    } else {
                        "design entry $name artifact integration-event has unsupported variant: ${artifact.variant}"
                    }
                }
            } else {
                require(artifact.variant in allowedVariants) {
                    "design entry $name artifact ${artifact.family} has unsupported variant: ${artifact.variant}"
                }
            }
        }

        val duplicate = artifacts
            .groupingBy { it.selectionKey() }
            .eachCount()
            .entries
            .firstOrNull { it.value > 1 }
            ?.key
        require(duplicate == null) {
            "design entry $name has duplicate artifact selection: $duplicate"
        }

        VariantFamilies.forEach { family ->
            val selections = artifacts.filter { it.family == family }
            require(selections.size <= 1) {
                "design entry $name has conflicting $family variants"
            }
        }

        val hasSubscriber = artifacts.any { it.family == "integration-subscriber" }
        if (hasSubscriber) {
            require(artifacts.singleOrNull { it.family == "integration-event" }?.variant == "inbound") {
                "integration_event $name integration-subscriber requires integration-event:inbound."
            }
        }
    }

    private fun defaultArtifactsFor(tag: String): List<ArtifactSelectionModel> =
        when (tag) {
            "command" -> listOf(ArtifactSelectionModel("command"))
            "query" -> listOf(ArtifactSelectionModel("query"), ArtifactSelectionModel("query-handler"))
            "client" -> listOf(ArtifactSelectionModel("client"), ArtifactSelectionModel("client-handler"))
            "api_payload" -> listOf(ArtifactSelectionModel("api-payload"))
            "domain_event" -> listOf(ArtifactSelectionModel("domain-event"), ArtifactSelectionModel("domain-subscriber"))
            "integration_event" -> listOf(ArtifactSelectionModel("integration-event", "outbound"))
            "domain_service" -> listOf(ArtifactSelectionModel("domain-service"))
            "saga" -> listOf(ArtifactSelectionModel("saga"))
            else -> error("Unsupported design tag: $tag")
        }

    private fun DesignElementSnapshot.toDrawingBoardElementOrNull(): DrawingBoardElementModel? {
        val normalizedTag = normalizeDrawingBoardTag(tag) ?: return null
        if (normalizedTag !in SupportedDrawingBoardTags) {
            return null
        }
        val normalizedRequestFields = requestFields
            .filterNot { field ->
                normalizedTag == "domain_event" && field.name.equals("entity", ignoreCase = true)
            }

        return DrawingBoardElementModel(
            tag = normalizedTag,
            packageName = packageName,
            name = name,
            description = description,
            aggregates = aggregates,
            artifacts = artifacts,
            entity = if (normalizedTag == "domain_event") null else entity,
            persist = persist,
            traits = traits,
            message = message,
            targets = targets,
            valueType = valueType,
            requestFields = normalizedRequestFields.map { field ->
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
            role = role,
            eventName = eventName,
        )
    }

    private fun normalizeDrawingBoardTag(tag: String): String? =
        when (tag.lowercase(Locale.ROOT)) {
            "command" -> "command"
            "query" -> "query"
            "client" -> "client"
            "api_payload" -> "api_payload"
            "domain_event" -> "domain_event"
            "integration_event" -> "integration_event"
            else -> null
        }

    private fun drawingBoardElementKey(element: DrawingBoardElementModel): String {
        return "${element.tag}|${element.packageName}|${element.name}"
    }

    private fun DesignSpecEntry.integrationEventRole(integrationEventArtifact: ArtifactSelectionModel): IntegrationEventRole {
        return when (integrationEventArtifact.variant) {
            "inbound" -> IntegrationEventRole.INBOUND
            "outbound" -> IntegrationEventRole.OUTBOUND
            else -> throw IllegalArgumentException("integration_event $name must select integration-event variant inbound or outbound.")
        }
    }

    private fun DesignSpecEntry.integrationEventName(): String {
        return eventName?.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("integration_event $name must declare eventName.")
    }

    private fun DesignSpecEntry.integrationEventRequestFields(): List<FieldModel> {
        val fields = primaryFields()
        require(fields.isNotEmpty()) {
            "integration_event $name must declare at least one requestField."
        }
        return fields
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

    private fun String.toIntegrationEventTypeName(): String {
        val rawName = trim()
        val candidate = when {
            rawName.endsWith("Evt") || rawName.endsWith("Event") -> rawName
            else -> "${rawName}IntegrationEvent"
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
        aggregateEntityMetadata: Map<String, AggregateMetadataRecord>,
    ): AggregateMetadataRecord {
        return aggregateEntityMetadata[aggregateName]
            ?: throw IllegalArgumentException("domain_event ${entry.name} references missing aggregate metadata: $aggregateName")
    }

    private fun resolveDomainEventPackageKey(
        aggregateRootPackageName: String,
        config: ProjectConfig,
    ): String {
        val normalizedRootPackage = aggregateRootPackageName.trim('.')
        if (normalizedRootPackage.isBlank()) {
            return ""
        }

        val aggregateRootPrefix = ArtifactLayoutResolver.joinPackage(
            config.basePackage,
            config.artifactLayout.aggregate.packageRoot,
        )
        val packageKey = when {
            aggregateRootPrefix.isNotBlank() && normalizedRootPackage == aggregateRootPrefix -> ""
            aggregateRootPrefix.isNotBlank() && normalizedRootPackage.startsWith("$aggregateRootPrefix.") ->
                normalizedRootPackage.removePrefix("$aggregateRootPrefix.")
            else -> normalizedRootPackage.substringAfterLast('.')
        }
        val aggregateSuffix = config.artifactLayout.aggregate.packageSuffix.trim('.')
        return when {
            aggregateSuffix.isBlank() -> packageKey
            packageKey == aggregateSuffix -> ""
            packageKey.endsWith(".$aggregateSuffix") -> packageKey.removeSuffix(".$aggregateSuffix")
            else -> packageKey
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

    private fun validateTypeManifestOwnership(
        sharedEnums: Iterable<com.only4.cap4k.plugin.pipeline.api.SharedEnumDefinition>,
        valueObjects: Iterable<com.only4.cap4k.plugin.pipeline.api.ValueObjectModel>,
    ) {
        sharedEnums.firstOrNull { it.aggregates.size > 1 }?.let { definition ->
            throw IllegalArgumentException("enum ${definition.typeName} may declare at most one aggregate")
        }
        valueObjects.firstOrNull { it.aggregates.size > 1 }?.let { valueObject ->
            throw IllegalArgumentException("value object ${valueObject.name} may declare at most one aggregate")
        }
    }

    private fun validateDuplicateTypeSimpleNames(
        sharedEnums: Iterable<String>,
        localEnums: Iterable<LocalEnumTypeName>,
        sharedValueObjects: Iterable<SharedValueObjectTypeName>,
        localValueObjects: Iterable<LocalValueObjectTypeName>,
        typeRegistry: Iterable<String>,
    ) {
        val sharedValueObjectDefinitions = sharedValueObjects
            .mapNotNull { it.normalized() }
        sharedValueObjectDefinitions
            .groupBy { it.simpleName }
            .entries
            .firstOrNull { (_, definitions) -> definitions.size > 1 }
            ?.let { (_, definitions) ->
                throw IllegalArgumentException("Ambiguous value object type override: ${definitions.first().simpleName}")
            }
        val sharedValueObjectSimpleNames = sharedValueObjectDefinitions
            .map { it.simpleName }
            .filter { it.isNotEmpty() }
            .toSet()
        val globalCounts = linkedMapOf<String, Int>()
        (sharedEnums + sharedValueObjectDefinitions.map { it.simpleName } + typeRegistry)
            .map { it.substringAfterLast('.').trim() }
            .filter { it.isNotEmpty() }
            .forEach { simpleName -> globalCounts[simpleName] = globalCounts.getOrDefault(simpleName, 0) + 1 }
        globalCounts.entries.firstOrNull { it.value > 1 }?.let { (simpleName, _) ->
            throw IllegalArgumentException("Duplicate type simple name: $simpleName")
        }

        val localEnumDefinitions = localEnums
            .mapNotNull { it.normalized() }
            .distinct()
        localEnumDefinitions
            .groupBy { it.owner to it.simpleName }
            .entries
            .firstOrNull { (_, definitions) -> definitions.map { it.items }.distinct().size > 1 }
            ?.let { (_, definitions) ->
                throw IllegalArgumentException("Duplicate type simple name: ${definitions.first().simpleName}")
            }
        val localValueObjectDefinitions = localValueObjects
            .mapNotNull { it.normalized() }
        localValueObjectDefinitions
            .groupBy { it.owner to it.simpleName }
            .entries
            .firstOrNull { (_, definitions) -> definitions.size > 1 }
            ?.let { (_, definitions) ->
                throw IllegalArgumentException("Ambiguous value object type override: ${definitions.first().simpleName}")
            }
        val distinctLocalValueObjectDefinitions = localValueObjectDefinitions.distinct()
        val localValueObjectKeys = distinctLocalValueObjectDefinitions.map { it.owner to it.simpleName }.toSet()
        localEnumDefinitions
            .firstOrNull { (it.owner to it.simpleName) in localValueObjectKeys }
            ?.let { localEnum ->
                throw IllegalArgumentException("Duplicate type simple name: ${localEnum.simpleName}")
            }
        val localTypeSimpleNames = (
            localEnumDefinitions.map { it.simpleName } +
                distinctLocalValueObjectDefinitions.map { it.simpleName }
            ).toSet()
        globalCounts.keys.firstOrNull { simpleName ->
            simpleName in localTypeSimpleNames && simpleName !in sharedValueObjectSimpleNames
        }?.let { simpleName ->
            throw IllegalArgumentException("Duplicate type simple name: $simpleName")
        }
    }

    private data class LocalEnumTypeName(
        val owner: String,
        val simpleName: String,
        val items: List<EnumItemModel>,
    ) {
        fun normalized(): LocalEnumTypeName? {
            val normalizedSimpleName = simpleName.substringAfterLast('.').trim()
            if (normalizedSimpleName.isEmpty()) {
                return null
            }
            return LocalEnumTypeName(
                owner = owner.trim(),
                simpleName = normalizedSimpleName,
                items = items,
            )
        }
    }

    private data class SharedValueObjectTypeName(
        val simpleName: String,
        val packageName: String,
    ) {
        fun normalized(): SharedValueObjectTypeName? {
            val normalizedSimpleName = simpleName.substringAfterLast('.').trim()
            if (normalizedSimpleName.isEmpty()) {
                return null
            }
            return SharedValueObjectTypeName(
                simpleName = normalizedSimpleName,
                packageName = packageName.trim(),
            )
        }
    }

    private data class LocalValueObjectTypeName(
        val owner: String,
        val simpleName: String,
        val packageName: String,
    ) {
        fun normalized(): LocalValueObjectTypeName? {
            val normalizedSimpleName = simpleName.substringAfterLast('.').trim()
            if (normalizedSimpleName.isEmpty()) {
                return null
            }
            return LocalValueObjectTypeName(
                owner = owner.trim(),
                simpleName = normalizedSimpleName,
                packageName = packageName.trim(),
            )
        }
    }

    private companion object {
        val SupportedDesignBlockTags = setOf(
            "command",
            "query",
            "client",
            "api_payload",
            "domain_event",
            "integration_event",
            "domain_service",
            "saga",
        )
        val ResultFieldTags = setOf("query", "client", "api_payload")
        val EventNameTags = setOf("domain_event", "integration_event")
        val SupportedArtifactFamilies = linkedMapOf(
            "command" to setOf(""),
            "query" to setOf("", "page"),
            "query-handler" to setOf(""),
            "client" to setOf(""),
            "client-handler" to setOf(""),
            "api-payload" to setOf("", "page"),
            "domain-event" to setOf(""),
            "domain-subscriber" to setOf(""),
            "integration-event" to setOf("inbound", "outbound"),
            "integration-subscriber" to setOf(""),
            "domain-service" to setOf(""),
            "saga" to setOf(""),
        )
        val VariantFamilies = setOf("query", "api-payload", "integration-event")

        val SupportedDrawingBoardTags = setOf(
            "command",
            "query",
            "client",
            "api_payload",
            "domain_event",
            "integration_event",
        )
        val UpperCamelSplitRegex = Regex("(?<=[a-z0-9])(?=[A-Z])|[^A-Za-z0-9]+")

        fun lowerCamelIdentifier(value: String): String {
            val parts = value.trim()
                .split(UpperCamelSplitRegex)
                .filter { it.isNotEmpty() }
            if (parts.isEmpty()) return value

            val head = parts.first().lowercase(Locale.ROOT)
            val tail = parts.drop(1).joinToString("") { token ->
                token.lowercase(Locale.ROOT).replaceFirstChar { it.titlecase(Locale.ROOT) }
            }
            return head + tail
        }

        fun ArtifactSelectionModel.selectionKey(): String =
            if (variant.isBlank()) family else "$family:$variant"
    }

    private fun buildLocalEnumTypeNames(
        entities: List<EntityModel>,
        sharedEnums: List<com.only4.cap4k.plugin.pipeline.api.SharedEnumDefinition>,
    ): List<LocalEnumTypeName> {
        val entitiesByKey = entities.associateBy { it.packageName to it.name }
        val entitiesByName = entities.groupBy { it.name }
        val resolving = mutableSetOf<Pair<String, String>>()
        val resolved = linkedMapOf<Pair<String, String>, String>()

        fun aggregateRootName(entity: EntityModel): String {
            val key = entity.packageName to entity.name
            resolved[key]?.let { return it }
            if (!resolving.add(key)) {
                return entity.name
            }
            val parentEntityName = entity.parentEntityName?.takeIf { it.isNotBlank() }
            val rootName = when {
                entity.aggregateRoot -> entity.name
                parentEntityName == null -> entity.name
                else -> {
                    val parent = entitiesByKey[entity.packageName to parentEntityName]
                        ?: entitiesByName[parentEntityName]?.singleOrNull()
                    parent?.let { aggregateRootName(it) } ?: entity.name
                }
            }
            resolving.remove(key)
            resolved[key] = rootName
            return rootName
        }

        val fieldEnums = entities.flatMap { entity ->
            entity.fields.mapNotNull { field ->
                field.typeBinding
                    ?.takeIf { it.isNotBlank() && field.enumItems.isNotEmpty() }
                    ?.let { typeBinding ->
                        LocalEnumTypeName(
                            owner = entity.packageName,
                            simpleName = typeBinding,
                            items = field.enumItems,
                        )
                    }
            }
        }
        val manifestEnums = sharedEnums.flatMap { definition ->
            val ownerAggregateName = definition.aggregates.singleOrNull() ?: return@flatMap emptyList()
            val ownerEntities = entities.filter { entity -> aggregateRootName(entity) == ownerAggregateName }
            if (ownerEntities.isEmpty()) {
                return@flatMap listOf(
                    LocalEnumTypeName(
                        owner = ownerAggregateName,
                        simpleName = definition.typeName,
                        items = definition.items,
                    )
                )
            }
            ownerEntities.map { entity ->
                    LocalEnumTypeName(
                        owner = entity.packageName,
                        simpleName = definition.typeName,
                        items = definition.items,
                    )
            }
        }
        return fieldEnums + manifestEnums
    }

    private fun ProjectConfig.isAggregateProjectionOnly(): Boolean =
        "aggregate-projection" in generators &&
            "aggregate" !in generators
}

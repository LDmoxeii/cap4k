package com.only4.cap4k.plugin.pipeline.core

import com.only4.cap4k.plugin.pipeline.api.AnalysisEdgeModel
import com.only4.cap4k.plugin.pipeline.api.AnalysisGraphModel
import com.only4.cap4k.plugin.pipeline.api.AnalysisNodeModel
import com.only4.cap4k.plugin.pipeline.api.AggregateMetadataRecord
import com.only4.cap4k.plugin.pipeline.api.AggregateCreationGraphModel
import com.only4.cap4k.plugin.pipeline.api.AggregateCreationNodeModel
import com.only4.cap4k.plugin.pipeline.api.AggregateCreationRelationModel
import com.only4.cap4k.plugin.pipeline.api.AggregateRef
import com.only4.cap4k.plugin.pipeline.api.AggregateDiagnostics
import com.only4.cap4k.plugin.pipeline.api.ArtifactLayoutResolver
import com.only4.cap4k.plugin.pipeline.api.ArtifactSelectionModel
import com.only4.cap4k.plugin.pipeline.api.CanonicalAssemblyResult
import com.only4.cap4k.plugin.pipeline.api.CanonicalModel
import com.only4.cap4k.plugin.pipeline.api.CanonicalTypeIdentity
import com.only4.cap4k.plugin.pipeline.api.CanonicalTypeKind
import com.only4.cap4k.plugin.pipeline.api.DesignBlockModel
import com.only4.cap4k.plugin.pipeline.api.DomainEventModel
import com.only4.cap4k.plugin.pipeline.api.DesignElementSnapshot
import com.only4.cap4k.plugin.pipeline.api.DesignSpecEntry
import com.only4.cap4k.plugin.pipeline.api.DrawingBoardElementModel
import com.only4.cap4k.plugin.pipeline.api.DrawingBoardModel
import com.only4.cap4k.plugin.pipeline.api.DomainServiceModel
import com.only4.cap4k.plugin.pipeline.api.DbColumnSnapshot
import com.only4.cap4k.plugin.pipeline.api.DbIdStrategy
import com.only4.cap4k.plugin.pipeline.api.DbSchemaSnapshot
import com.only4.cap4k.plugin.pipeline.api.DbTableSnapshot
import com.only4.cap4k.plugin.pipeline.api.DesignSpecSnapshot
import com.only4.cap4k.plugin.pipeline.api.EntityModel
import com.only4.cap4k.plugin.pipeline.api.EnumItemModel
import com.only4.cap4k.plugin.pipeline.api.EnumManifestSnapshot
import com.only4.cap4k.plugin.pipeline.api.FieldModel
import com.only4.cap4k.plugin.pipeline.api.IrAnalysisSnapshot
import com.only4.cap4k.plugin.pipeline.api.PipelineDiagnosticsException
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig
import com.only4.cap4k.plugin.pipeline.api.PipelineDiagnostics
import com.only4.cap4k.plugin.pipeline.api.RepositoryModel
import com.only4.cap4k.plugin.pipeline.api.SchemaModel
import com.only4.cap4k.plugin.pipeline.api.SourceSnapshot
import com.only4.cap4k.plugin.pipeline.api.StrongIdKind
import com.only4.cap4k.plugin.pipeline.api.StrongIdModel
import com.only4.cap4k.plugin.pipeline.api.JsonValuePersistenceProjection
import com.only4.cap4k.plugin.pipeline.api.OwnedRelationCardinality
import com.only4.cap4k.plugin.pipeline.api.SemanticDefaultExpression
import com.only4.cap4k.plugin.pipeline.api.SemanticFieldSnapshot
import com.only4.cap4k.plugin.pipeline.api.SemanticListTypeRef
import com.only4.cap4k.plugin.pipeline.api.SemanticNamedTypeRef
import com.only4.cap4k.plugin.pipeline.api.SemanticValueDefinition
import com.only4.cap4k.plugin.pipeline.api.SemanticValueField
import com.only4.cap4k.plugin.pipeline.api.SemanticValueRole
import com.only4.cap4k.plugin.pipeline.api.TypeRegistryModel
import com.only4.cap4k.plugin.pipeline.api.UnsupportedAggregateTable
import com.only4.cap4k.plugin.pipeline.api.UnsupportedTablePolicy
import com.only4.cap4k.plugin.pipeline.api.ValueObjectManifestSnapshot
import com.only4.cap4k.plugin.pipeline.api.ValueObjectDeclarationSnapshot
import com.only4.cap4k.plugin.pipeline.api.ValueObjectModel
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
        val valueObjectDeclarations = snapshots.filterIsInstance<ValueObjectManifestSnapshot>().flatMap { it.declarations }
        val typeRegistry = TypeRegistryModel(config.typeRegistry.entries)
        val analysisSnapshot = snapshots.filterIsInstance<IrAnalysisSnapshot>().firstOrNull()
        val designEntries = designSnapshot?.entries.orEmpty()
        designEntries.forEach { entry ->
            require(entry.tag in SupportedDesignBlockTags) {
                "Unsupported design tag: ${entry.tag}"
            }
        }
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

        val generatedOwnStrongIdsByTableName = supportedTables
            .mapNotNull { table ->
                generatedOwnStrongId(table)?.let { strongId ->
                    table.tableName.lowercase(Locale.ROOT) to strongId
                }
            }
            .toMap()
        val aggregateRootIdsByName = supportedTables
            .filter { it.aggregateRoot }
            .mapNotNull { table ->
                generatedOwnStrongIdsByTableName[table.tableName.lowercase(Locale.ROOT)]
                    ?.let { strongId ->
                        val idPackage = artifactLayout.aggregateEntityPackage(
                            AggregateNaming.tableSegment(table.tableName)
                        )
                        AggregateNaming.entityName(table.tableName) to AggregateRootStrongId(
                            qualifiedTypeName = "$idPackage.${strongId.typeName}",
                            generated = strongId,
                        )
                    }
            }
            .toMap()

        val aggregateModels = supportedTables.map { table ->

            val entityName = AggregateNaming.entityName(table.tableName)
            val schemaName = AggregateNaming.schemaName(table.tableName)
            val repositoryName = AggregateNaming.repositoryName(table.tableName)
            val aggregateOwnerTable = resolveAggregateOwnerTable(table, supportedTablesByName)
            val segment = AggregateNaming.tableSegment(aggregateOwnerTable.tableName)
            val parentTable = table.parentTable
            val generatedOwnId = generatedOwnStrongIdsByTableName[table.tableName.lowercase(Locale.ROOT)]
            val fields = table.columns
                .filterNot { it.parentRef }
                .map { column ->
                    val fieldName = lowerCamelIdentifier(column.name)
                    val resolvedType = resolveStrongIdFieldType(
                        tableName = table.tableName,
                        column = column,
                        aggregateRootIdsByName = aggregateRootIdsByName,
                    ) ?: if (isTablePrimaryKeyColumn(table, column) && generatedOwnId != null) {
                        generatedOwnId.typeName
                    } else {
                        column.kotlinType
                    }
                    FieldModel(
                        name = fieldName,
                        type = resolvedType,
                        nullable = column.nullable,
                        defaultValue = column.defaultValue,
                        typeBinding = column.typeBinding,
                        enumItems = column.enumItems,
                        columnName = column.name,
                        managedRole = column.managedRole,
                        inherited = column.inherited == true,
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
            generatedOwnStrongIdsByTableName = generatedOwnStrongIdsByTableName,
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
        val aggregateEntityPackageByName = entities.associateBy(
            keySelector = { it.name },
            valueTransform = { it.packageName },
        )
        validateTypeManifestOwnership(sharedEnums, valueObjectDeclarations)
        val semanticTypeCatalog = buildSemanticTypeCatalog(
            config = config,
            artifactLayout = artifactLayout,
            entities = entities,
            strongIds = strongIds,
            enums = sharedEnums,
            valueObjects = valueObjectDeclarations,
            designEntries = designEntries,
            recoveredDesignElements = analysisSnapshot?.designElements.orEmpty(),
        )
        val semanticCompiler = SemanticValueCompiler(semanticTypeCatalog)
        val valueObjects = valueObjectDeclarations.map { declaration ->
            declaration.toValueObjectModel(semanticCompiler)
        }
        val designBlocks = designEntries
            .asSequence()
            .map { entry ->
                entry.toDesignBlockModel(
                    compiler = semanticCompiler,
                    artifactLayout = artifactLayout,
                    config = config,
                    aggregateEntityMetadata = aggregateEntityMetadata,
                )
            }
            .fold(linkedMapOf<String, DesignBlockModel>()) { acc, block ->
                val key = designBlockKey(block)
                acc[key] = acc[key]?.let { existing -> mergeDesignBlocks(existing, block) } ?: block
                acc
            }
            .values
            .toList()
        val domainEvents = designBlocks
            .asSequence()
            .filter { block -> block.tag == "domain_event" }
            .map { block ->
                val aggregateName = block.aggregates.single()
                val aggregate = requireNotNull(aggregateEntityMetadata[aggregateName]) {
                    "domain_event ${block.name} references missing aggregate metadata: $aggregateName"
                }
                DomainEventModel(
                    packageName = resolveDomainEventPackageKey(aggregate.rootPackageName, config),
                    typeName = block.name.toDomainEventTypeName(),
                    description = block.description,
                    aggregateName = aggregateName,
                    aggregatePackageName = aggregate.rootPackageName,
                    persist = block.persist ?: false,
                    value = block.request,
                )
            }
            .toList()
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
        val aggregateCreationGraphs = buildAggregateCreationGraphs(
            artifactLayout = artifactLayout,
            entities = entities,
            relations = aggregateRelations,
            resolvedPolicies = specialFieldResolution.resolvedPolicies,
            catalog = semanticTypeCatalog,
        )
        validateValueObjectPersistenceProjectionIdentities(
            config = config,
            artifactLayout = artifactLayout,
            entities = entities,
            schemas = aggregateModels.map { it.first },
            repositories = aggregateModels.mapNotNull { it.third },
            strongIds = strongIds,
            enums = sharedEnums,
            valueObjects = valueObjects,
            designBlocks = designBlocks,
            domainEvents = domainEvents,
            aggregateCreationGraphs = aggregateCreationGraphs,
            domainServices = domainServices,
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
            .mapNotNull { element ->
                element.toDrawingBoardElementOrNull(
                    compiler = semanticCompiler,
                    artifactLayout = artifactLayout,
                    config = config,
                    aggregateEntityMetadata = aggregateEntityMetadata,
                )
            }
            .fold(linkedMapOf<String, DrawingBoardElementModel>()) { acc, element ->
                val key = drawingBoardElementKey(element)
                acc[key] = acc[key]?.let { existing -> mergeDrawingBoardElements(existing, element) } ?: element
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
                domainEvents = domainEvents,
                schemas = aggregateModels.map { it.first },
                entities = entities,
                repositories = aggregateModels.mapNotNull { it.third },
                analysisGraph = analysisGraph,
                drawingBoard = drawingBoard,
                sharedEnums = sharedEnums,
                aggregateRelations = aggregateRelations,
                aggregateEntityJpa = aggregateEntityJpa,
                aggregatePersistenceFieldControls = aggregatePersistenceFieldControls,
                aggregatePersistenceProviderControls = aggregatePersistenceProviderControls,
                aggregateIdPolicyControls = aggregateIdPolicyControls,
                aggregateSpecialFieldResolvedPolicies = specialFieldResolution.resolvedPolicies,
                strongIds = strongIds,
                valueObjects = valueObjects,
                aggregateCreationGraphs = aggregateCreationGraphs,
                domainServices = domainServices,
                typeRegistry = typeRegistry,
            ),
            diagnostics = diagnostics,
        )
    }

    private data class GeneratedOwnStrongId(
        val typeName: String,
        val strategy: String,
        val backing: ResolvedStrongIdBacking,
    )

    private data class AggregateRootStrongId(
        val qualifiedTypeName: String,
        val generated: GeneratedOwnStrongId,
    )

    private fun resolveStrongIdFieldType(
        tableName: String,
        column: DbColumnSnapshot,
        aggregateRootIdsByName: Map<String, AggregateRootStrongId>,
    ): String? {
        val refAggregate = column.refAggregate?.takeIf { it.isNotBlank() }
        val refId = column.refId?.takeIf { it.isNotBlank() }
        require(!(refAggregate != null && refId != null)) {
            "conflicting @RefAggregate and @RefId annotations on the same column metadata."
        }
        if (refAggregate != null) {
            val aggregateRootId = requireNotNull(aggregateRootIdsByName[refAggregate]) {
                "@RefAggregate=$refAggregate does not match a generated aggregate root"
            }
            val referenceStrategy = when (aggregateRootId.generated.strategy) {
                "uuid7" -> DbIdStrategy.UUID7
                "snowflake" -> DbIdStrategy.SNOWFLAKE
                else -> error("unsupported generated Strong ID strategy: ${aggregateRootId.generated.strategy}")
            }
            val referenceBacking = AggregateStrongIdBackingResolver.resolve(
                tableName = tableName,
                column = column.copy(idStrategy = referenceStrategy),
            )
            require(referenceBacking.valueType == aggregateRootId.generated.backing.valueType) {
                "aggregate reference $tableName.${column.name} storage ${referenceBacking.valueType} " +
                    "does not match ${aggregateRootId.generated.typeName} backing " +
                    aggregateRootId.generated.backing.valueType
            }
            return aggregateRootId.qualifiedTypeName
        }

        return refId
    }

    private fun generatedOwnStrongId(table: DbTableSnapshot): GeneratedOwnStrongId? {
        val primaryKeyColumn = table.primaryKey.singleOrNull() ?: return null
        val idColumn = table.columns.firstOrNull { it.name.equals(primaryKeyColumn, ignoreCase = true) }
            ?: return null
        val strategy = when (idColumn.idStrategy) {
            DbIdStrategy.UUID7 -> "uuid7"
            DbIdStrategy.SNOWFLAKE -> "snowflake"
            DbIdStrategy.DB_IDENTITY, null -> return null
        }
        require(idColumn.refAggregate.isNullOrBlank() && idColumn.refId.isNullOrBlank()) {
            "primary key ${table.tableName}.${idColumn.name} cannot also be @RefAggregate or @RefId"
        }

        return GeneratedOwnStrongId(
            typeName = ownStrongIdTypeName(AggregateNaming.entityName(table.tableName)),
            strategy = strategy,
            backing = AggregateStrongIdBackingResolver.resolve(table.tableName, idColumn),
        )
    }

    private fun isTablePrimaryKeyColumn(
        table: DbTableSnapshot,
        column: DbColumnSnapshot,
    ): Boolean = table.primaryKey.any { it.equals(column.name, ignoreCase = true) }

    private fun ownStrongIdTypeName(entityName: String): String = "${entityName}Id"

    private fun buildStrongIds(
        config: ProjectConfig,
        entities: List<EntityModel>,
        tables: List<DbTableSnapshot>,
        generatedOwnStrongIdsByTableName: Map<String, GeneratedOwnStrongId>,
    ): List<StrongIdModel> {
        val tableByEntity = tables.associateBy { AggregateNaming.entityName(it.tableName) }
        val ownIds = entities
            .asSequence()
            .mapNotNull { entity ->
                val table = tableByEntity[entity.name] ?: return@mapNotNull null
                val generatedOwnId = generatedOwnStrongIdsByTableName[
                    table.tableName.lowercase(Locale.ROOT)
                ] ?: return@mapNotNull null
                val ownerAggregate = aggregateRootEntityOrSelf(entity, entities)
                StrongIdModel(
                    typeName = entity.idField.type,
                    packageName = entity.packageName,
                    valueType = generatedOwnId.backing.valueType,
                    kind = StrongIdKind.OWN_ID,
                    ownerEntityName = entity.name,
                    ownerEntityPackageName = entity.packageName,
                    ownerAggregateName = ownerAggregate.name,
                    ownerAggregatePackageName = ownerAggregate.packageName,
                    idStrategy = generatedOwnId.strategy,
                    isEmbeddedId = true,
                )
            }

        val referenceStrongIds = buildReferenceStrongIds(config, tables)

        return (ownIds + referenceStrongIds)
            .distinctBy { it.packageName to it.typeName }
            .toList()
    }

    private fun buildReferenceStrongIds(
        config: ProjectConfig,
        tables: List<DbTableSnapshot>,
    ): Sequence<StrongIdModel> =
        tables
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

    private fun aggregateRootEntityOrSelf(entity: EntityModel, entities: List<EntityModel>): EntityModel {
        val entitiesByName = entities.associateBy { it.name }
        val visited = mutableSetOf<String>()
        var current = entity
        while (!current.aggregateRoot) {
            if (!visited.add("${current.packageName}.${current.name}")) {
                return entity
            }
            val parentEntityName = current.parentEntityName ?: return entity
            current = entitiesByName[parentEntityName] ?: return entity
        }
        return current
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

    private fun DesignSpecEntry.toDesignBlockModel(
        compiler: SemanticValueCompiler,
        artifactLayout: ArtifactLayoutResolver,
        config: ProjectConfig,
        aggregateEntityMetadata: Map<String, AggregateMetadataRecord>,
        allowRecoveredDomainEventWithoutAggregateMetadata: Boolean = false,
        allowRecoveredSagaResponse: Boolean = false,
    ): DesignBlockModel {
        validateDesignBlockSharedFields(allowRecoveredSagaResponse)
        val artifactSelections = resolveDesignBlockArtifacts()
        val typeName = when (tag) {
            "command" -> "${name}Cmd"
            "query" -> "${name}Qry"
            "client" -> "${name}Cli"
            "api_payload" -> name.normalizeUpperCamelTypeName()
            "domain_event" -> name.toDomainEventTypeName()
            "integration_event" -> name.toIntegrationEventTypeName()
            else -> name.normalizeUpperCamelTypeName()
        }
        val resolvedPackageName = when (tag) {
            "command" -> artifactLayout.designCommandPackage(packageName)
            "query" -> artifactLayout.designQueryPackage(packageName)
            "client" -> artifactLayout.designClientPackage(packageName)
            "api_payload" -> artifactLayout.designApiPayloadPackage(packageName)
            "domain_event" -> {
                val aggregateName = resolveDomainEventAggregateName(this)
                val aggregate = aggregateEntityMetadata[aggregateName]
                when {
                    aggregate != null -> artifactLayout.designDomainEventPackage(
                        resolveDomainEventPackageKey(aggregate.rootPackageName, config),
                    )
                    allowRecoveredDomainEventWithoutAggregateMetadata ->
                        artifactLayout.designDomainEventPackage(packageName)
                    else -> resolveDomainEventAggregateMetadata(this, aggregateName, aggregateEntityMetadata)
                        .let { resolved ->
                            artifactLayout.designDomainEventPackage(
                                resolveDomainEventPackageKey(resolved.rootPackageName, config),
                            )
                        }
                }
            }
            "integration_event" -> {
                val variant = artifactSelections.singleOrNull { it.family == "integration-event" }?.variant ?: "outbound"
                artifactLayout.designIntegrationEventPackage(variant, packageName)
            }
            "saga" -> artifactLayout.designSagaPackage(packageName)
            "domain_service" -> artifactLayout.designDomainServicePackage(packageName)
            else -> error("Unsupported design tag: $tag")
        }
        val requestIdentity = CanonicalTypeIdentity(
            packageName = resolvedPackageName,
            typePath = if (tag in EventPayloadTags) listOf(typeName) else listOf(typeName, "Request"),
            kind = CanonicalTypeKind.NESTED_VALUE,
            ownerAggregateName = aggregates.singleOrNull(),
        )
        val requestRole = requestRoleFor(tag)
        val requestFields = if (tag == "domain_event") {
            fields.filterNot { it.name.equals("entity", ignoreCase = true) }
        } else {
            fields
        }
        val request = compiler.compile(
            identity = requestIdentity,
            role = requestRole,
            fields = requestFields,
            aggregateContext = aggregates,
        )
        val response = if (tag in ResultFieldTags || (allowRecoveredSagaResponse && tag == "saga")) {
            compiler.compile(
                identity = CanonicalTypeIdentity(
                    packageName = resolvedPackageName,
                    typePath = listOf(typeName, "Response"),
                    kind = CanonicalTypeKind.NESTED_VALUE,
                    ownerAggregateName = aggregates.singleOrNull(),
                ),
                role = responseRoleFor(tag),
                fields = resultFields,
                aggregateContext = aggregates,
                allowPageEnvelope = tag in PageEnvelopeTags,
            )
        } else {
            null
        }
        return DesignBlockModel(
            tag = tag,
            packageName = packageName,
            name = name,
            description = description,
            aggregates = aggregates,
            eventName = eventName.orEmpty(),
            persist = persist,
            artifacts = artifactSelections,
            artifactsDeclared = artifacts != null,
            request = request,
            response = response,
        )
    }

    private fun requestRoleFor(tag: String): SemanticValueRole = when (tag) {
        "command" -> SemanticValueRole.COMMAND_REQUEST
        "query" -> SemanticValueRole.QUERY_REQUEST
        "client" -> SemanticValueRole.CLIENT_REQUEST
        "api_payload" -> SemanticValueRole.API_PAYLOAD_REQUEST
        "domain_event" -> SemanticValueRole.DOMAIN_EVENT
        "integration_event" -> SemanticValueRole.INTEGRATION_EVENT
        "saga" -> SemanticValueRole.SAGA_REQUEST
        "domain_service" -> SemanticValueRole.API_PAYLOAD_REQUEST
        else -> error("Unsupported design tag: $tag")
    }

    private fun responseRoleFor(tag: String): SemanticValueRole = when (tag) {
        "command" -> SemanticValueRole.COMMAND_RESPONSE
        "query" -> SemanticValueRole.QUERY_RESPONSE
        "client" -> SemanticValueRole.CLIENT_RESPONSE
        "api_payload" -> SemanticValueRole.API_PAYLOAD_RESPONSE
        "saga" -> SemanticValueRole.SAGA_RESPONSE
        else -> error("Design tag $tag does not support a response payload")
    }

    private fun DesignSpecEntry.validateDesignBlockSharedFields(allowRecoveredSagaResponse: Boolean = false) {
        require(eventName.isNullOrBlank() || tag in EventNameTags) {
            "design entry $name cannot declare eventName on tag: $tag"
        }
        require(persist == null || tag == "domain_event") {
            "design entry $name cannot declare persist on tag: $tag"
        }
        require(resultFields.isEmpty() || tag in ResultFieldTags || (allowRecoveredSagaResponse && tag == "saga")) {
            "design entry $name cannot declare resultFields on tag: $tag"
        }
        if (tag == "domain_event") {
            val aggregateCount = aggregates.size
            require(aggregateCount == 1) {
                "domain_event $name must declare exactly one aggregate, but found $aggregateCount."
            }
        }
        if (tag == "integration_event" && effectiveArtifacts().any { it.family == "integration-event" }) {
            require(!eventName.isNullOrBlank()) {
                "integration_event $name must declare eventName."
            }
            require(fields.isNotEmpty()) {
                "integration_event $name must declare at least one fields entry."
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
        validateArtifactSelections(
            entryName = name,
            tag = tag,
            artifacts = artifacts,
        )
    }

    private fun validateArtifactSelections(
        entryName: String,
        tag: String,
        artifacts: List<ArtifactSelectionModel>,
    ) {
        artifacts.forEach { artifact ->
            val allowedVariants = SupportedArtifactFamilies[artifact.family]
                ?: throw IllegalArgumentException("unsupported design artifact family on $entryName: ${artifact.family}")
            if (artifact.family == "integration-event") {
                require(artifact.variant in allowedVariants) {
                    if (artifact.variant.isBlank()) {
                        "design entry $entryName artifact integration-event must declare variant inbound or outbound"
                    } else {
                        "design entry $entryName artifact integration-event has unsupported variant: ${artifact.variant}"
                    }
                }
            } else {
                require(artifact.variant in allowedVariants) {
                    "design entry $entryName artifact ${artifact.family} has unsupported variant: ${artifact.variant}"
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
            "design entry $entryName has duplicate artifact selection: $duplicate"
        }

        VariantFamilies.forEach { family ->
            val selections = artifacts.filter { it.family == family }
            require(selections.size <= 1) {
                "design entry $entryName has conflicting $family variants"
            }
        }

        val hasSubscriber = artifacts.any { it.family == "integration-subscriber" }
        if (hasSubscriber) {
            require(tag == "integration_event") {
                "design entry $entryName integration-subscriber is only supported on integration_event"
            }
            require(artifacts.singleOrNull { it.family == "integration-event" }?.variant == "inbound") {
                "integration_event $entryName integration-subscriber requires integration-event:inbound."
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

    private fun DesignElementSnapshot.toDrawingBoardElementOrNull(
        compiler: SemanticValueCompiler,
        artifactLayout: ArtifactLayoutResolver,
        config: ProjectConfig,
        aggregateEntityMetadata: Map<String, AggregateMetadataRecord>,
    ): DrawingBoardElementModel? {
        val normalizedTag = normalizeDrawingBoardTag(tag) ?: return null
        if (normalizedTag !in SupportedDrawingBoardTags) {
            return null
        }
        val recoveredPersist = persist.takeIf { normalizedTag == "domain_event" }
        val recoveredEventName = eventName.takeIf { normalizedTag in EventNameTags }
        val recoveredResultFields = resultFields
            .takeIf { normalizedTag in ResultFieldTags || normalizedTag == "saga" }
            .orEmpty()
        val compiled = DesignSpecEntry(
            tag = normalizedTag,
            packageName = packageName,
            name = name,
            description = description,
            aggregates = aggregates,
            persist = recoveredPersist,
            artifacts = if (artifactsDeclared) artifacts else null,
            fields = fields,
            resultFields = recoveredResultFields,
            eventName = recoveredEventName,
        ).toDesignBlockModel(
            compiler = compiler,
            artifactLayout = artifactLayout,
            config = config,
            aggregateEntityMetadata = aggregateEntityMetadata,
            allowRecoveredDomainEventWithoutAggregateMetadata = true,
            allowRecoveredSagaResponse = true,
        )

        return DrawingBoardElementModel(
            tag = normalizedTag,
            packageName = packageName,
            name = name,
            description = description,
            aggregates = aggregates,
            artifacts = artifacts,
            artifactsDeclared = artifactsDeclared,
            persist = recoveredPersist,
            request = compiled.request,
            response = compiled.response,
            eventName = recoveredEventName,
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
            "domain_service" -> "domain_service"
            "saga" -> "saga"
            else -> null
        }

    private fun drawingBoardElementKey(element: DrawingBoardElementModel): String {
        return "${element.tag}|${element.packageName}|${element.name}"
    }

    private fun designBlockKey(block: DesignBlockModel): String {
        return "${block.tag}|${block.packageName}|${block.name}"
    }

    private fun mergeDesignBlocks(
        existing: DesignBlockModel,
        incoming: DesignBlockModel,
    ): DesignBlockModel {
        val context = "${existing.tag} ${existing.packageName} ${existing.name}"
        require(existing.tag == incoming.tag && existing.packageName == incoming.packageName && existing.name == incoming.name) {
            "cannot merge different design blocks: $context"
        }

        validateArtifactSelections(
            entryName = existing.name,
            tag = existing.tag,
            artifacts = existing.artifacts + incoming.artifacts,
        )
        val artifacts = (existing.artifacts + incoming.artifacts)
            .distinctBy { it.selectionKey() }

        return existing.copy(
            description = mergeStringMetadata(context, "description", existing.description, incoming.description),
            aggregates = mergeListMetadata(context, "aggregates", existing.aggregates, incoming.aggregates),
            eventName = mergeStringMetadata(context, "eventName", existing.eventName, incoming.eventName),
            persist = mergeBooleanMetadata(context, "persist", existing.persist, incoming.persist),
            artifacts = artifacts,
            request = mergeSemanticValueDefinition(context, "fields", existing.request, incoming.request),
            response = when {
                existing.response == null -> incoming.response
                incoming.response == null -> existing.response
                else -> mergeSemanticValueDefinition(
                    context,
                    "resultFields",
                    requireNotNull(existing.response),
                    requireNotNull(incoming.response),
                )
            },
        )
    }

    private fun mergeStringMetadata(
        context: String,
        field: String,
        existing: String,
        incoming: String,
    ): String {
        if (incoming.isBlank()) return existing
        if (existing.isBlank()) return incoming
        require(existing == incoming) { "conflicting design block metadata for $context: $field" }
        return existing
    }

    private fun <T> mergeListMetadata(
        context: String,
        field: String,
        existing: List<T>,
        incoming: List<T>,
    ): List<T> {
        if (incoming.isEmpty()) return existing
        if (existing.isEmpty()) return incoming
        require(existing == incoming) { "conflicting design block metadata for $context: $field" }
        return existing
    }

    private fun mergeBooleanMetadata(
        context: String,
        field: String,
        existing: Boolean?,
        incoming: Boolean?,
    ): Boolean? {
        if (incoming == null) return existing
        if (existing == null) return incoming
        require(existing == incoming) { "conflicting design block metadata for $context: $field" }
        return existing
    }

    private fun mergeSemanticValueDefinition(
        context: String,
        field: String,
        existing: SemanticValueDefinition,
        incoming: SemanticValueDefinition,
    ): SemanticValueDefinition {
        require(existing.identity == incoming.identity && existing.role == incoming.role) {
            "conflicting design block semantic identity for $context: $field"
        }
        return existing.copy(
            fields = mergeListMetadata(context, field, existing.fields, incoming.fields),
            nestedDefinitions = mergeListMetadata(
                context,
                "$field nested definitions",
                existing.nestedDefinitions,
                incoming.nestedDefinitions,
            ),
            envelope = when {
                incoming.envelope == null -> existing.envelope
                existing.envelope == null -> incoming.envelope
                else -> existing.envelope.also {
                    require(existing.envelope == incoming.envelope) {
                        "conflicting design block metadata for $context: $field envelope"
                    }
                }
            },
        )
    }

    private fun mergeDrawingBoardElements(
        existing: DrawingBoardElementModel,
        incoming: DrawingBoardElementModel,
    ): DrawingBoardElementModel {
        val context = "${existing.tag} ${existing.packageName} ${existing.name}"
        require(existing.tag == incoming.tag && existing.packageName == incoming.packageName && existing.name == incoming.name) {
            "cannot merge different drawing-board elements: $context"
        }

        val artifacts = (existing.artifacts + incoming.artifacts)
            .distinctBy { it.selectionKey() }
        validateArtifactSelections(
            entryName = existing.name,
            tag = existing.tag,
            artifacts = artifacts,
        )

        return existing.copy(
            description = mergeStringMetadata(context, "description", existing.description, incoming.description),
            aggregates = mergeListMetadata(context, "aggregates", existing.aggregates, incoming.aggregates),
            artifacts = artifacts,
            artifactsDeclared = existing.artifactsDeclared || incoming.artifactsDeclared,
            persist = mergeBooleanMetadata(context, "persist", existing.persist, incoming.persist),
            request = mergeSemanticValueDefinition(context, "fields", existing.request, incoming.request),
            response = when {
                existing.response == null -> incoming.response
                incoming.response == null -> existing.response
                else -> mergeSemanticValueDefinition(
                    context,
                    "resultFields",
                    requireNotNull(existing.response),
                    requireNotNull(incoming.response),
                )
            },
            eventName = mergeNullableStringMetadata(context, "eventName", existing.eventName, incoming.eventName),
        )
    }

    private fun mergeNullableStringMetadata(
        context: String,
        field: String,
        existing: String?,
        incoming: String?,
    ): String? {
        if (incoming.isNullOrBlank()) return existing
        if (existing.isNullOrBlank()) return incoming
        require(existing == incoming) { "conflicting design block metadata for $context: $field" }
        return existing
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

    private fun buildSemanticTypeCatalog(
        config: ProjectConfig,
        artifactLayout: ArtifactLayoutResolver,
        entities: List<EntityModel>,
        strongIds: List<StrongIdModel>,
        enums: List<com.only4.cap4k.plugin.pipeline.api.SharedEnumDefinition>,
        valueObjects: List<ValueObjectDeclarationSnapshot>,
        designEntries: List<DesignSpecEntry>,
        recoveredDesignElements: List<DesignElementSnapshot>,
    ): CanonicalTypeCatalog {
        val aggregateRootByEntity = buildAggregateRootNameByEntity(entities)
        val identities = mutableListOf<CanonicalTypeIdentity>()
        identities += entities.map { entity ->
            CanonicalTypeIdentity(
                packageName = entity.packageName,
                typePath = listOf(entity.name),
                kind = CanonicalTypeKind.ENTITY,
                ownerAggregateName = aggregateRootByEntity[entityKey(entity)],
            )
        }
        identities += strongIds.map { strongId ->
            CanonicalTypeIdentity(
                packageName = strongId.packageName,
                typePath = listOf(strongId.typeName),
                kind = CanonicalTypeKind.STRONG_ID,
                ownerAggregateName = strongId.ownerAggregateName,
            )
        }
        identities += valueObjects.map { valueObject ->
            CanonicalTypeIdentity(
                packageName = valueObject.packageName,
                typePath = listOf(valueObject.name),
                kind = CanonicalTypeKind.VALUE_OBJECT,
                ownerAggregateName = valueObject.aggregates.singleOrNull(),
            )
        }
        identities += enums.map { definition -> canonicalEnumIdentity(artifactLayout, entities, definition) }
        identities += entities.flatMap { entity ->
            entity.fields
                .filter { it.typeBinding?.isNotBlank() == true && it.enumItems.isNotEmpty() }
                .map { field ->
                    CanonicalTypeIdentity(
                        packageName = artifactLayout.aggregateLocalEnumPackage(entity.packageName),
                        typePath = listOf(requireNotNull(field.typeBinding)),
                        kind = CanonicalTypeKind.ENUM,
                        ownerAggregateName = aggregateRootByEntity[entityKey(entity)],
                    )
                }
        }
        val registryAliases = config.typeRegistry.entries.mapValues { (_, entry) ->
            externalTypeIdentity(entry.fqn)
        }
        identities += registryAliases.values
        val sourceTypeExpressions = buildList {
            addAll(entities.flatMap { entity ->
                entity.fields.map { field -> field.typeBinding?.takeIf(String::isNotBlank) ?: field.type }
            })
            addAll(valueObjects.flatMap { valueObject -> valueObject.fields.map(SemanticFieldSnapshot::typeExpression) })
            addAll(designEntries.flatMap { entry ->
                (entry.fields + entry.resultFields).map(SemanticFieldSnapshot::typeExpression)
            })
            addAll(recoveredDesignElements.flatMap { element ->
                (element.fields + element.resultFields).map(SemanticFieldSnapshot::typeExpression)
            })
        }
        return CanonicalTypeCatalog(
            identities = identities,
            aliases = registryAliases,
            sourceTypeExpressions = sourceTypeExpressions,
        )
    }

    private fun validateValueObjectPersistenceProjectionIdentities(
        config: ProjectConfig,
        artifactLayout: ArtifactLayoutResolver,
        entities: List<EntityModel>,
        schemas: List<SchemaModel>,
        repositories: List<RepositoryModel>,
        strongIds: List<StrongIdModel>,
        enums: List<com.only4.cap4k.plugin.pipeline.api.SharedEnumDefinition>,
        valueObjects: List<ValueObjectModel>,
        designBlocks: List<DesignBlockModel>,
        domainEvents: List<DomainEventModel>,
        aggregateCreationGraphs: List<AggregateCreationGraphModel>,
        domainServices: List<DomainServiceModel>,
    ) {
        val aggregateRootByEntity = buildAggregateRootNameByEntity(entities)
        val canonicalDeclarations = buildList {
            addAll(entities.map { entity ->
                CanonicalTypeIdentity(
                    packageName = entity.packageName,
                    typePath = listOf(entity.name),
                    kind = CanonicalTypeKind.ENTITY,
                    ownerAggregateName = aggregateRootByEntity[entityKey(entity)],
                )
            })
            addAll(strongIds.map { strongId ->
                CanonicalTypeIdentity(
                    packageName = strongId.packageName,
                    typePath = listOf(strongId.typeName),
                    kind = CanonicalTypeKind.STRONG_ID,
                    ownerAggregateName = strongId.ownerAggregateName,
                )
            })
            addAll(enums.map { definition -> canonicalEnumIdentity(artifactLayout, entities, definition) })
            addAll(entities.flatMap { entity ->
                entity.fields
                    .filter { field -> field.typeBinding?.isNotBlank() == true && field.enumItems.isNotEmpty() }
                    .map { field ->
                        CanonicalTypeIdentity(
                            packageName = artifactLayout.aggregateLocalEnumPackage(entity.packageName),
                            typePath = listOf(requireNotNull(field.typeBinding)),
                            kind = CanonicalTypeKind.ENUM,
                            ownerAggregateName = aggregateRootByEntity[entityKey(entity)],
                        )
                    }
            })
            addAll(valueObjects.flatMap { valueObject -> valueObject.definition.declarationIdentities() })
            addAll(designBlocks.flatMap { block ->
                block.request.declarationIdentities() + block.response?.declarationIdentities().orEmpty()
            })
            addAll(domainEvents.flatMap { event -> event.value.declarationIdentities() })
            addAll(aggregateCreationGraphs.flatMap { graph ->
                graph.factoryPayload.declarationIdentities() +
                    graph.ownedNodes.flatMap { node -> node.value.declarationIdentities() }
            })
            addAll(config.typeRegistry.entries.values.map { entry -> externalTypeIdentity(entry.fqn) })
        }
        val artifactDeclarations = buildList {
            addAll(schemas.map { schema -> "${schema.packageName}.${schema.name}" })
            addAll(repositories.map { repository -> "${repository.packageName}.${repository.name}" })
            addAll(domainServices.map { service -> "${service.packageName}.${service.name}" })
            addAll(valueObjects.flatMap { valueObject -> valueObject.definition.artifactDeclarationFqns() })
            addAll(designBlocks.flatMap { block ->
                block.request.artifactDeclarationFqns() + block.response?.artifactDeclarationFqns().orEmpty()
            })
            addAll(domainEvents.flatMap { event -> event.value.artifactDeclarationFqns() })
            addAll(aggregateCreationGraphs.flatMap { graph ->
                graph.factoryPayload.artifactDeclarationFqns() +
                    graph.ownedNodes.flatMap { node -> node.value.artifactDeclarationFqns() }
            })
            if ("aggregate" in config.generators) {
                entities.filter { it.aggregateRoot }.forEach { root ->
                    add("${root.packageName}.${root.name}Behavior")
                    add("${artifactLayout.aggregateFactoryPackage(root.packageName)}.${root.name}Factory")
                }
            }
        }
        ValueObjectPersistenceProjectionIdentityValidator.validate(
            valueObjects = valueObjects,
            canonicalDeclarations = canonicalDeclarations,
            artifactDeclarationFqns = artifactDeclarations,
        )
    }

    private fun canonicalEnumIdentity(
        artifactLayout: ArtifactLayoutResolver,
        entities: List<EntityModel>,
        definition: com.only4.cap4k.plugin.pipeline.api.SharedEnumDefinition,
    ): CanonicalTypeIdentity {
        val ownerAggregate = definition.aggregates.singleOrNull()
        val packageName = if (ownerAggregate == null) {
            definition.packageName.trim().takeIf { '.' in it }
                ?: artifactLayout.aggregateSharedEnumPackage(definition.packageName)
        } else {
            val ownerPackage = entities.firstOrNull { it.aggregateRoot && it.name == ownerAggregate }?.packageName
                ?: definition.packageName.trim().takeIf { '.' in it }
                ?: artifactLayout.aggregateEntityPackage(definition.packageName)
            artifactLayout.aggregateLocalEnumPackage(ownerPackage)
        }
        return CanonicalTypeIdentity(
            packageName = packageName,
            typePath = listOf(definition.typeName),
            kind = CanonicalTypeKind.ENUM,
            ownerAggregateName = ownerAggregate,
        )
    }

    private fun SemanticValueDefinition.declarationIdentities(): List<CanonicalTypeIdentity> = buildList {
        add(identity)
        nestedDefinitions.forEach { nested -> addAll(nested.declarationIdentities()) }
        (envelope as? com.only4.cap4k.plugin.pipeline.api.SemanticValueEnvelope.Page)
            ?.itemDefinition
            ?.let { item -> addAll(item.declarationIdentities()) }
    }

    private fun SemanticValueDefinition.artifactDeclarationFqns(): List<String> =
        declarationIdentities()
            .map { declaration ->
                (listOf(declaration.packageName).filter(String::isNotBlank) + declaration.typePath.first())
                    .joinToString(".")
            }
            .distinct()

    private fun ValueObjectDeclarationSnapshot.toValueObjectModel(
        compiler: SemanticValueCompiler,
    ): ValueObjectModel {
        val identity = CanonicalTypeIdentity(
            packageName = packageName,
            typePath = listOf(name),
            kind = CanonicalTypeKind.VALUE_OBJECT,
            ownerAggregateName = aggregates.singleOrNull(),
        )
        val persistenceProjection = persistence?.let { projection ->
            require(projection.options.isEmpty()) {
                "value object ${identity.fqn} persistence ${projection.kind} has unsupported options: " +
                    projection.options.keys.sorted().joinToString(", ")
            }
            when (projection.kind.lowercase(Locale.ROOT)) {
                "json" -> JsonValuePersistenceProjection(
                    converterClassFqn = "$packageName.${name}JsonAttributeConverter",
                )
                else -> throw IllegalArgumentException(
                    "value object ${identity.fqn} persistence kind is unsupported: ${projection.kind}",
                )
            }
        }
        return ValueObjectModel(
            definition = compiler.compile(
                identity = identity,
                role = SemanticValueRole.VALUE_OBJECT,
                fields = fields,
                aggregateContext = aggregates,
            ),
            aggregates = aggregates,
            persistence = persistenceProjection,
            description = description,
        )
    }

    private fun buildAggregateCreationGraphs(
        artifactLayout: ArtifactLayoutResolver,
        entities: List<EntityModel>,
        relations: List<com.only4.cap4k.plugin.pipeline.api.AggregateRelationModel>,
        resolvedPolicies: List<com.only4.cap4k.plugin.pipeline.api.AggregateSpecialFieldResolvedPolicy>,
        catalog: CanonicalTypeCatalog,
    ): List<AggregateCreationGraphModel> {
        if (resolvedPolicies.isEmpty()) return emptyList()
        val entitiesByFqn = entities.associateBy { "${it.packageName}.${it.name}" }
        val ownedRelations = relations.filter { it.owned }
        AggregateCreationGraphValidator.validate(entities, ownedRelations)
        val targetOwnerRelations = ownedRelations.groupBy { "${it.targetEntityPackageName}.${it.targetEntityName}" }
        val relationsByOwner = ownedRelations.groupBy { "${it.ownerEntityPackageName}.${it.ownerEntityName}" }
        val creationIdentityByEntityFqn = targetOwnerRelations.keys.associateWith { entityFqn ->
            val entity = requireNotNull(entitiesByFqn[entityFqn])
            CanonicalTypeIdentity(
                packageName = entity.packageName,
                typePath = listOf("${entity.name}Creation"),
                kind = CanonicalTypeKind.CREATION_VALUE,
                ownerAggregateName = resolveAggregateRootEntity(entity, entities).name,
            )
        }
        val entityIdentities = entitiesByFqn.mapValues { (_, entity) ->
            CanonicalTypeIdentity(
                packageName = entity.packageName,
                typePath = listOf(entity.name),
                kind = CanonicalTypeKind.ENTITY,
                ownerAggregateName = resolveAggregateRootEntity(entity, entities).name,
            )
        }
        val creationCatalog = catalog.plus(creationIdentityByEntityFqn.values)
        val policiesByEntity = resolvedPolicies.associateBy { "${it.entityPackageName}.${it.entityName}" }

        return entities.filter { it.aggregateRoot }.map { root ->
            val rootFqn = "${root.packageName}.${root.name}"
            val visiting = linkedSetOf<String>()
            val visited = linkedSetOf<String>()
            val orderedChildren = mutableListOf<String>()
            val graphRelations = mutableListOf<AggregateCreationRelationModel>()

            fun traverse(ownerFqn: String, path: List<String>) {
                require(visiting.add(ownerFqn)) {
                    "owned relation cycle detected for aggregate ${root.name}: ${(path + ownerFqn).joinToString(" -> ")}"
                }
                relationsByOwner[ownerFqn].orEmpty().forEach { relation ->
                    val targetFqn = "${relation.targetEntityPackageName}.${relation.targetEntityName}"
                    val cardinality = requireNotNull(relation.ownedCardinality)
                    val creationFieldName = when (cardinality) {
                        OwnedRelationCardinality.ONE -> relation.singleAccessorName ?: relation.fieldName
                        OwnedRelationCardinality.MANY -> relation.fieldName
                    }
                    val relationPath = path + creationFieldName
                    if (targetFqn in visiting) {
                        throw IllegalArgumentException(
                            "owned relation cycle detected for aggregate ${root.name}: ${relationPath.joinToString(".")}",
                        )
                    }
                    val canonicalRelation = AggregateCreationRelationModel(
                        path = relationPath,
                        ownerEntity = requireNotNull(entityIdentities[ownerFqn]),
                        targetEntity = requireNotNull(entityIdentities[targetFqn]),
                        fieldName = creationFieldName,
                        cardinality = cardinality,
                        attachmentAccessorName = when (cardinality) {
                            OwnedRelationCardinality.ONE -> relation.singleAccessorName ?: relation.fieldName
                            OwnedRelationCardinality.MANY -> relation.fieldName
                        },
                    )
                    graphRelations += canonicalRelation
                    traverse(targetFqn, relationPath)
                    if (visited.add(targetFqn)) orderedChildren += targetFqn
                }
                visiting.remove(ownerFqn)
            }
            traverse(rootFqn, emptyList())

            val ownedNodes = orderedChildren.map { entityFqn ->
                val entity = requireNotNull(entitiesByFqn[entityFqn])
                val valueIdentity = requireNotNull(creationIdentityByEntityFqn[entityFqn])
                val directRelations = graphRelations.filter { it.ownerEntity.fqn == entityFqn }
                val definition = compileCreationDefinition(
                    entity = entity,
                    identity = valueIdentity,
                    relations = directRelations,
                    resolvedPolicy = requireNotNull(policiesByEntity[entityFqn]) {
                        "owned creation graph ${root.name} is missing resolved write surface for $entityFqn"
                    },
                    catalog = creationCatalog,
                    creationIdentityByEntityFqn = creationIdentityByEntityFqn,
                    role = SemanticValueRole.OWNED_ENTITY_CREATION,
                )
                AggregateCreationNodeModel(
                    entity = requireNotNull(entityIdentities[entityFqn]),
                    value = definition,
                    constructorFieldNames = scalarCreationFieldNames(entity, policiesByEntity.getValue(entityFqn)),
                    relations = directRelations,
                )
            }
            val rootRelations = graphRelations.filter { it.ownerEntity.fqn == rootFqn }
            val rootDefinition = compileCreationDefinition(
                entity = root,
                identity = CanonicalTypeIdentity(
                    packageName = artifactLayout.aggregateFactoryPackage(root.packageName),
                    typePath = listOf("${root.name}Factory", "Payload"),
                    kind = CanonicalTypeKind.NESTED_VALUE,
                    ownerAggregateName = root.name,
                ),
                relations = rootRelations,
                resolvedPolicy = requireNotNull(policiesByEntity[rootFqn]) {
                    "aggregate creation graph ${root.name} is missing resolved write surface for $rootFqn"
                },
                catalog = creationCatalog,
                creationIdentityByEntityFqn = creationIdentityByEntityFqn,
                role = SemanticValueRole.FACTORY_PAYLOAD,
            )
            AggregateCreationGraphModel(
                rootEntity = requireNotNull(entityIdentities[rootFqn]),
                factoryPayload = rootDefinition,
                rootConstructorFieldNames = scalarCreationFieldNames(root, policiesByEntity.getValue(rootFqn)),
                ownedNodes = ownedNodes,
                relations = graphRelations,
            )
        }
    }

    private fun compileCreationDefinition(
        entity: EntityModel,
        identity: CanonicalTypeIdentity,
        relations: List<AggregateCreationRelationModel>,
        resolvedPolicy: com.only4.cap4k.plugin.pipeline.api.AggregateSpecialFieldResolvedPolicy,
        catalog: CanonicalTypeCatalog,
        creationIdentityByEntityFqn: Map<String, CanonicalTypeIdentity>,
        role: SemanticValueRole,
    ): SemanticValueDefinition {
        val aggregateContext = listOf(requireNotNull(identity.ownerAggregateName))
        val scalarFields = scalarCreationFieldNames(entity, resolvedPolicy).map { fieldName ->
            val field = entity.fields.single { it.name == fieldName }
            val expression = (field.typeBinding?.takeIf { it.isNotBlank() } ?: field.type) +
                if (field.nullable) "?" else ""
            val fieldPath = "${identity.fqn}.${field.name}"
            val type = catalog.resolveExpression(
                expression = expression,
                fieldPath = fieldPath,
                ownerPackageName = entity.packageName,
                aggregateContext = aggregateContext,
            )
            SemanticValueField(
                name = field.name,
                type = type,
                defaultValue = compileEntityFieldDefault(field, type, fieldPath),
                sourcePath = fieldPath,
            )
        }
        val relationFields = relations.map { relation ->
            val targetCreation = requireNotNull(creationIdentityByEntityFqn[relation.targetEntity.fqn]) {
                "owned relation ${relation.path.joinToString(".")} has no creation value identity"
            }
            when (relation.cardinality) {
                OwnedRelationCardinality.ONE -> SemanticValueField(
                    name = relation.fieldName,
                    type = SemanticNamedTypeRef(targetCreation, nullable = true),
                    defaultValue = SemanticDefaultExpression("null", "null"),
                    sourcePath = relation.path.joinToString("."),
                )
                OwnedRelationCardinality.MANY -> SemanticValueField(
                    name = relation.fieldName,
                    type = SemanticListTypeRef(SemanticNamedTypeRef(targetCreation)),
                    defaultValue = SemanticDefaultExpression("emptyList()", "emptyList()"),
                    sourcePath = relation.path.joinToString("."),
                )
            }
        }
        return SemanticValueDefinition(
            identity = identity,
            role = role,
            fields = scalarFields + relationFields,
        )
    }

    private fun compileEntityFieldDefault(
        field: FieldModel,
        type: com.only4.cap4k.plugin.pipeline.api.SemanticTypeRef,
        fieldPath: String,
    ): SemanticDefaultExpression? {
        val source = field.defaultValue ?: return null
        var normalized = source.trim()
        while (normalized.length >= 2 && normalized.first() == '(' && normalized.last() == ')') {
            normalized = normalized.substring(1, normalized.lastIndex).trim()
        }
        if (normalized.equals("null", ignoreCase = true)) {
            return SemanticDefaultCompiler.compile("null", type, fieldPath)
        }
        if (isSqlDefaultExpression(normalized)) {
            return null
        }
        if (field.enumItems.isNotEmpty()) {
            val numeric = unquoteSqlString(normalized)?.toIntOrNull() ?: normalized.toIntOrNull()
            require(numeric != null && field.enumItems.any { it.value == numeric }) {
                "aggregate enum field $fieldPath default $normalized does not match a declared enum value"
            }
            val namedType = type as? SemanticNamedTypeRef
                ?: throw IllegalArgumentException("aggregate enum field $fieldPath does not resolve to a named enum type")
            val expression = "${namedType.symbol.fqn}.valueOfOrNull($numeric)!!"
            return SemanticDefaultExpression(expression, source)
        }
        val projected = when (type) {
            is com.only4.cap4k.plugin.pipeline.api.SemanticBuiltinTypeRef -> when (type.kind) {
                com.only4.cap4k.plugin.pipeline.api.SemanticBuiltinType.STRING ->
                    unquoteSqlString(normalized) ?: normalized
                com.only4.cap4k.plugin.pipeline.api.SemanticBuiltinType.BOOLEAN -> {
                    val booleanValue = unquoteSqlString(normalized) ?: normalized
                    when {
                        booleanValue == "1" || booleanValue.equals("true", ignoreCase = true) -> "true"
                        booleanValue == "0" || booleanValue.equals("false", ignoreCase = true) -> "false"
                        else -> booleanValue
                    }
                }
                else -> unquoteSqlString(normalized) ?: normalized
            }
            else -> normalized
        }
        return SemanticDefaultCompiler.compile(projected, type, fieldPath)
            ?.copy(sourceExpression = source)
    }

    private fun isSqlDefaultExpression(value: String): Boolean {
        if (unquoteSqlString(value) != null) return false
        val upper = value.uppercase(Locale.ROOT)
        return upper in setOf("CURRENT_TIMESTAMP", "CURRENT_DATE", "CURRENT_TIME") ||
            value.matches(Regex("[A-Za-z_][A-Za-z0-9_$]*(\\.[A-Za-z_][A-Za-z0-9_$]*)?\\s*\\(.*\\)")) ||
            value.matches(Regex(".*\\s[+*/%]\\s.*"))
    }

    private fun unquoteSqlString(value: String): String? = when {
        value.length >= 2 && value.first() == '\'' && value.last() == '\'' ->
            value.substring(1, value.lastIndex).replace("''", "'")
        value.length >= 2 && value.first() == '"' && value.last() == '"' ->
            value.substring(1, value.lastIndex).replace("\"\"", "\"")
        else -> null
    }

    private fun scalarCreationFieldNames(
        entity: EntityModel,
        resolvedPolicy: com.only4.cap4k.plugin.pipeline.api.AggregateSpecialFieldResolvedPolicy,
    ): List<String> = resolvedPolicy.writeSurface.createAllowedFields
        .filter { it != entity.idField.name }
        .also { fieldNames ->
            fieldNames.firstOrNull { fieldName -> entity.fields.none { it.name == fieldName } }?.let { missing ->
                throw IllegalArgumentException(
                    "resolved creation write surface ${entity.packageName}.${entity.name} references missing field $missing",
                )
            }
        }

    private fun buildAggregateRootNameByEntity(entities: List<EntityModel>): Map<String, String> =
        entities.associate { entity -> entityKey(entity) to resolveAggregateRootEntity(entity, entities).name }

    private fun resolveAggregateRootEntity(entity: EntityModel, entities: List<EntityModel>): EntityModel {
        val entitiesByName = entities.groupBy { it.name }
        val visited = mutableSetOf<String>()
        var current = entity
        while (!current.aggregateRoot && !current.parentEntityName.isNullOrBlank()) {
            require(visited.add(entityKey(current))) {
                "aggregate parent cycle detected at ${entityKey(current)}"
            }
            current = entitiesByName[requireNotNull(current.parentEntityName)]?.singleOrNull()
                ?: throw IllegalArgumentException(
                    "entity ${entityKey(current)} references ambiguous or missing parent ${current.parentEntityName}",
                )
        }
        return current
    }

    private fun entityKey(entity: EntityModel): String = "${entity.packageName}.${entity.name}"

    private fun externalTypeIdentity(fqn: String): CanonicalTypeIdentity {
        val normalized = fqn.trim('.')
        require('.' in normalized) { "type registry entry must declare an FQN: $fqn" }
        return CanonicalTypeIdentity(
            packageName = normalized.substringBeforeLast('.'),
            typePath = listOf(normalized.substringAfterLast('.')),
            kind = CanonicalTypeKind.EXTERNAL,
        )
    }

    private fun validateTypeManifestOwnership(
        sharedEnums: Iterable<com.only4.cap4k.plugin.pipeline.api.SharedEnumDefinition>,
        valueObjects: Iterable<ValueObjectDeclarationSnapshot>,
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
        val ResultFieldTags = setOf("command", "query", "client", "api_payload")
        val PageEnvelopeTags = setOf("query", "api_payload")
        val EventPayloadTags = setOf("domain_event", "integration_event")
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
            "domain_service",
            "saga",
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

package com.only4.cap4k.plugin.pipeline.api

data class FieldModel(
    val name: String,
    val type: String,
    val nullable: Boolean = false,
    val defaultValue: String? = null,
    val typeBinding: String? = null,
    val enumItems: List<EnumItemModel> = emptyList(),
    val columnName: String? = null,
)

data class ProjectModel(
    val group: String = "",
    val name: String = "",
)

data class AggregateModel(
    val name: String,
    val packageName: String = "",
    val description: String? = null,
)

data class TypeRegistryModel(
    val entries: Map<String, TypeRegistryEntry> = emptyMap(),
) {
    companion object {
        fun empty(): TypeRegistryModel = TypeRegistryModel()
    }
}

data class DbColumnSnapshot(
    val name: String,
    val dbType: String,
    val kotlinType: String,
    val nullable: Boolean,
    val defaultValue: String? = null,
    val comment: String = "",
    val isPrimaryKey: Boolean = false,
    val typeBinding: String? = null,
    val enumItems: List<EnumItemModel> = emptyList(),
    val parentRef: Boolean = false,
    val refAggregate: String? = null,
    val refId: String? = null,
    val managedPolicyKey: String? = null,
    val jdbcType: Int? = null,
    val columnSize: Int? = null,
)

/**
 * Unique-index metadata captured from a source. [complete] is true only when every index part is
 * represented by a physical column; [filterCondition] preserves any conditional uniqueness predicate.
 *
 * The defaulted metadata fields preserve Kotlin source compatibility for in-repository call sites only.
 * No Java constructor or cross-version JVM binary compatibility is guaranteed for compiled addons.
 */
data class UniqueConstraintModel(
    val physicalName: String,
    val columns: List<String>,
    val complete: Boolean = true,
    val filterCondition: String? = null,
)

data class DbTableSnapshot(
    val tableName: String,
    val comment: String,
    val columns: List<DbColumnSnapshot>,
    val primaryKey: List<String>,
    val uniqueConstraints: List<UniqueConstraintModel>,
    val parentTable: String? = null,
    val aggregateRoot: Boolean = true,
)

data class DesignSpecEntry(
    val tag: String,
    val packageName: String,
    val name: String,
    val description: String,
    val aggregates: List<String>,
    val persist: Boolean? = null,
    val artifacts: List<ArtifactSelectionModel>? = null,
    val fields: List<SemanticFieldSnapshot> = emptyList(),
    val resultFields: List<SemanticFieldSnapshot> = emptyList(),
    val eventName: String? = null,
)

data class ArtifactSelectionModel(
    val family: String,
    val variant: String = "",
)

data class DesignBlockModel(
    val tag: String,
    val packageName: String,
    val name: String,
    val description: String = "",
    val aggregates: List<String> = emptyList(),
    val eventName: String = "",
    val persist: Boolean? = null,
    val artifacts: List<ArtifactSelectionModel>,
    val artifactsDeclared: Boolean = true,
    val request: SemanticValueDefinition,
    val response: SemanticValueDefinition? = null,
) {
    val fields: List<SemanticValueField>
        get() = request.fields

    val resultFields: List<SemanticValueField>
        get() = response?.fields.orEmpty()

    val designJsonArtifacts: List<ArtifactSelectionModel>
        get() = normalizedArtifactSelections(artifacts)

    val includeDesignJsonArtifacts: Boolean
        get() = artifactsDeclared &&
            designJsonArtifacts != normalizedArtifactSelections(defaultDrawingBoardArtifactsFor(tag))
}

data class DesignElementSnapshot(
    val tag: String,
    val packageName: String,
    val name: String,
    val description: String,
    val aggregates: List<String> = emptyList(),
    val artifacts: List<ArtifactSelectionModel> = emptyList(),
    val artifactsDeclared: Boolean = artifacts.isNotEmpty(),
    val persist: Boolean? = null,
    val fields: List<SemanticFieldSnapshot> = emptyList(),
    val resultFields: List<SemanticFieldSnapshot> = emptyList(),
    val eventName: String? = null,
)

data class AggregateMetadataRecord(
    val aggregateName: String,
    val rootQualifiedName: String,
    val rootPackageName: String,
    val rootClassName: String,
)

data class IrNodeSnapshot(
    val id: String,
    val name: String,
    val fullName: String,
    val type: String,
)

data class IrEdgeSnapshot(
    val fromId: String,
    val toId: String,
    val type: String,
    val label: String? = null,
)

data class EnumItemModel(
    val value: Int,
    val name: String,
    val description: String,
)

data class SharedEnumDefinition(
    val typeName: String,
    val packageName: String,
    val items: List<EnumItemModel>,
    val aggregates: List<String> = emptyList(),
)

data class ValueObjectModel(
    val definition: SemanticValueDefinition,
    val aggregates: List<String> = emptyList(),
    val persistence: ValuePersistenceProjection? = null,
    val description: String? = null,
) {
    val name: String
        get() = definition.identity.simpleName

    val packageName: String
        get() = definition.identity.packageName

    val fields: List<SemanticValueField>
        get() = definition.fields
}

val ValueObjectModel.ownerAggregate: String?
    get() = aggregates.singleOrNull()

data class DomainServiceModel(
    val name: String,
    val packageName: String,
    val description: String? = null,
    val aggregates: List<String> = emptyList(),
)

sealed interface SourceSnapshot {
    val id: String
}

data class DbSchemaSnapshot(
    override val id: String = "db",
    val tables: List<DbTableSnapshot>,
    val discoveredTables: List<String> = tables.map { it.tableName }.sorted(),
    val includedTables: List<String> = tables.map { it.tableName }.sorted(),
    val excludedTables: List<String> = emptyList(),
) : SourceSnapshot

data class DesignSpecSnapshot(
    override val id: String = "design-json",
    val entries: List<DesignSpecEntry>,
) : SourceSnapshot

data class IrAnalysisSnapshot(
    override val id: String = "ir-analysis",
    val inputDirs: List<String>,
    val nodes: List<IrNodeSnapshot>,
    val edges: List<IrEdgeSnapshot>,
    val designElements: List<DesignElementSnapshot> = emptyList(),
) : SourceSnapshot

data class EnumManifestSnapshot(
    override val id: String = "enum-manifest",
    val definitions: List<SharedEnumDefinition>,
) : SourceSnapshot

data class ValueObjectManifestSnapshot(
    override val id: String = "value-object-manifest",
    val declarations: List<ValueObjectDeclarationSnapshot>,
) : SourceSnapshot

data class SchemaModel(
    val name: String,
    val packageName: String,
    val entityName: String,
    val comment: String,
    val fields: List<FieldModel>,
)

enum class AggregateRelationType {
    MANY_TO_ONE,
    ONE_TO_ONE,
    ONE_TO_MANY,
}

enum class AggregateFetchType {
    LAZY,
    EAGER,
}

enum class AggregateCascadeType {
    PERSIST,
    MERGE,
    REMOVE,
}

enum class OwnedRelationCardinality {
    ONE,
    MANY,
}

enum class OwnedRelationPersistenceShape {
    ONE_TO_MANY_JOIN_COLUMN,
}

/**
 * Canonical aggregate relation metadata.
 *
 * Defaulted owned-relation fields preserve Kotlin source compatibility for in-repository call sites only.
 * No Java constructor or cross-version JVM binary compatibility is guaranteed for compiled addons.
 */
data class AggregateRelationModel(
    val ownerEntityName: String,
    val ownerEntityPackageName: String,
    val fieldName: String,
    val targetEntityName: String,
    val targetEntityPackageName: String,
    val relationType: AggregateRelationType,
    val joinColumn: String,
    val fetchType: AggregateFetchType,
    val nullable: Boolean,
    val cascadeTypes: List<AggregateCascadeType> = emptyList(),
    val orphanRemoval: Boolean = false,
    val joinColumnNullable: Boolean? = null,
    val owned: Boolean = false,
    val parentRefColumn: String? = null,
    val ownedCardinality: OwnedRelationCardinality? = null,
    val persistenceShape: OwnedRelationPersistenceShape? = null,
    val backingCollectionName: String? = null,
    val singleAccessorName: String? = null,
)

data class EntityModel(
    val name: String,
    val packageName: String,
    val tableName: String,
    val comment: String,
    val fields: List<FieldModel>,
    val idField: FieldModel,
    val uniqueConstraints: List<UniqueConstraintModel> = emptyList(),
    val aggregateRoot: Boolean = true,
    val parentEntityName: String? = null,
)

data class AggregateColumnJpaModel(
    val fieldName: String,
    val columnName: String,
    val isId: Boolean,
    val converterTypeFqn: String? = null,
    val converterClassFqn: String? = null,
    val columnLength: Int? = null,
)

data class AggregateEntityJpaModel(
    val entityName: String,
    val entityPackageName: String,
    val entityEnabled: Boolean,
    val tableName: String,
    val columns: List<AggregateColumnJpaModel>,
)

data class AggregatePersistenceFieldControl(
    val entityName: String,
    val entityPackageName: String,
    val fieldName: String,
    val columnName: String,
    val generatedValueStrategy: String? = null,
    val generatedEvents: List<String> = emptyList(),
    val version: Boolean? = null,
    val insertable: Boolean? = null,
    val updatable: Boolean? = null,
)

enum class SoftDeleteTombstoneStrategy {
    SELF_ID,
}

enum class AggregateIdStorageKind {
    INTEGRAL,
    CHARACTER,
    NATIVE_UUID,
}

enum class SoftDeleteActiveSentinel {
    ZERO,
    NIL_UUID,
}

data class AggregateSoftDeletePolicy(
    val fieldName: String,
    val columnName: String,
    val storageKind: AggregateIdStorageKind,
    val activeSentinel: SoftDeleteActiveSentinel,
    val tombstoneStrategy: SoftDeleteTombstoneStrategy,
)

data class AggregatePersistenceProviderControl(
    val entityName: String,
    val entityPackageName: String,
    val tableName: String,
    val softDelete: AggregateSoftDeletePolicy? = null,
    val idFieldName: String,
    val versionFieldName: String? = null,
)

enum class AggregateIdPolicyKind {
    APPLICATION_SIDE,
    DATABASE_SIDE,
}

data class AggregateIdPolicyControl(
    val entityName: String,
    val entityPackageName: String,
    val tableName: String,
    val idFieldName: String,
    val idFieldType: String,
    val strategy: String,
    val kind: AggregateIdPolicyKind,
)

data class RepositoryModel(
    val name: String,
    val packageName: String,
    val entityName: String,
    val idType: String,
)

data class AnalysisNodeModel(
    val id: String,
    val name: String,
    val fullName: String,
    val type: String,
)

data class AnalysisEdgeModel(
    val fromId: String,
    val toId: String,
    val type: String,
    val label: String? = null,
)

data class DrawingBoardElementModel(
    val tag: String,
    val packageName: String,
    val name: String,
    val description: String,
    val aggregates: List<String> = emptyList(),
    val artifacts: List<ArtifactSelectionModel> = emptyList(),
    val artifactsDeclared: Boolean = artifacts.isNotEmpty(),
    val persist: Boolean? = null,
    val request: SemanticValueDefinition,
    val response: SemanticValueDefinition? = null,
    val eventName: String? = null,
) {
    val fields: List<SemanticValueField>
        get() = request.fields

    val resultFields: List<SemanticValueField>
        get() = response?.fields.orEmpty()

    val designJsonArtifacts: List<ArtifactSelectionModel>
        get() = normalizedArtifactSelections(artifacts)

    val includeDesignJsonArtifacts: Boolean
        get() = artifactsDeclared &&
            designJsonArtifacts != normalizedArtifactSelections(defaultDrawingBoardArtifactsFor(tag))

}

private val ArtifactSelectionComparator =
    compareBy<ArtifactSelectionModel> { it.family }.thenBy { it.variant }

private fun normalizedArtifactSelections(
    artifacts: List<ArtifactSelectionModel>,
): List<ArtifactSelectionModel> =
    artifacts.sortedWith(ArtifactSelectionComparator)

private fun defaultDrawingBoardArtifactsFor(tag: String): List<ArtifactSelectionModel> =
    when (tag) {
        "command" -> listOf(ArtifactSelectionModel("command"))
        "query" -> listOf(ArtifactSelectionModel("query"), ArtifactSelectionModel("query-handler"))
        "capability" -> listOf(ArtifactSelectionModel("capability"), ArtifactSelectionModel("capability-handler"))
        "api_payload" -> listOf(ArtifactSelectionModel("api-payload"))
        "domain_event" -> listOf(ArtifactSelectionModel("domain-event"), ArtifactSelectionModel("domain-subscriber"))
        "integration_event" -> listOf(ArtifactSelectionModel("integration-event", "outbound"))
        "domain_service" -> listOf(ArtifactSelectionModel("domain-service"))
        else -> emptyList()
    }

data class DrawingBoardModel(
    val elements: List<DrawingBoardElementModel>,
    val elementsByTag: Map<String, List<DrawingBoardElementModel>> = elements.groupBy { it.tag },
) {
    init {
        require(elementsByTag == elements.groupBy { it.tag }) {
            "elementsByTag must match elements grouped by tag"
        }
    }
}

data class AnalysisGraphModel(
    val inputDirs: List<String>,
    val nodes: List<AnalysisNodeModel>,
    val edges: List<AnalysisEdgeModel>,
)

data class AggregateRef(
    val name: String,
    val packageName: String,
)

enum class UnsupportedTablePolicy {
    FAIL,
    SKIP,
}

data class DomainEventModel(
    val packageName: String,
    val typeName: String,
    val description: String,
    val aggregateName: String,
    val aggregatePackageName: String,
    val persist: Boolean,
    val value: SemanticValueDefinition,
) {
    val fields: List<SemanticValueField>
        get() = value.fields
}

enum class StrongIdKind {
    OWN_ID,
    AGGREGATE_REFERENCE,
    REFERENCE,
}

data class StrongIdModel(
    val typeName: String,
    val packageName: String,
    val valueType: String = "String",
    val kind: StrongIdKind,
    val ownerEntityName: String? = null,
    val ownerEntityPackageName: String? = null,
    val ownerAggregateName: String? = null,
    val ownerAggregatePackageName: String? = null,
    val idStrategy: String? = null,
    val isEmbeddedId: Boolean = false,
)

data class CanonicalModel(
    val project: ProjectModel = ProjectModel(),
    val aggregates: List<AggregateModel> = emptyList(),
    val designBlocks: List<DesignBlockModel> = emptyList(),
    val domainEvents: List<DomainEventModel> = emptyList(),
    val schemas: List<SchemaModel> = emptyList(),
    val entities: List<EntityModel> = emptyList(),
    val repositories: List<RepositoryModel> = emptyList(),
    val analysisGraph: AnalysisGraphModel? = null,
    val drawingBoard: DrawingBoardModel? = null,
    val sharedEnums: List<SharedEnumDefinition> = emptyList(),
    val aggregateRelations: List<AggregateRelationModel> = emptyList(),
    val aggregateEntityJpa: List<AggregateEntityJpaModel> = emptyList(),
    val aggregatePersistenceFieldControls: List<AggregatePersistenceFieldControl> = emptyList(),
    val aggregatePersistenceProviderControls: List<AggregatePersistenceProviderControl> = emptyList(),
    val aggregateIdPolicyControls: List<AggregateIdPolicyControl> = emptyList(),
    val managedFieldPolicies: List<ResolvedManagedEntityPolicy> = emptyList(),
    val strongIds: List<StrongIdModel> = emptyList(),
    val valueObjects: List<ValueObjectModel> = emptyList(),
    val aggregateCreationGraphs: List<AggregateCreationGraphModel> = emptyList(),
    val domainServices: List<DomainServiceModel> = emptyList(),
    val typeRegistry: TypeRegistryModel = TypeRegistryModel.empty(),
)

data class UnsupportedAggregateTable(
    val tableName: String,
    val reason: String,
)

data class AggregateDiagnostics(
    val discoveredTables: List<String>,
    val includedTables: List<String>,
    val excludedTables: List<String>,
    val supportedTables: List<String>,
    val unsupportedTables: List<UnsupportedAggregateTable>,
)

data class PipelineDiagnostics(
    val aggregate: AggregateDiagnostics? = null,
)

class PipelineDiagnosticsException(
    message: String,
    val diagnostics: PipelineDiagnostics,
    cause: Throwable? = null,
) : IllegalArgumentException(message, cause)

data class CanonicalAssemblyResult(
    val model: CanonicalModel,
    val diagnostics: PipelineDiagnostics? = null,
)

enum class ArtifactOutputKind {
    CHECKED_IN_SOURCE,
    GENERATED_SOURCE,
    OUTPUT_ARTIFACT,
}

data class ArtifactPlanItem(
    val generatorId: String,
    val moduleRole: String,
    val templateId: String,
    val outputPath: String,
    val context: Map<String, Any?> = emptyMap(),
    val conflictPolicy: ConflictPolicy,
    val outputKind: ArtifactOutputKind = ArtifactOutputKind.CHECKED_IN_SOURCE,
    val resolvedOutputRoot: String = "",
)

data class ArtifactAddonContext(
    val config: ProjectConfig,
    val model: CanonicalModel,
    val options: Map<String, Any?> = emptyMap(),
)

data class RenderedArtifact(
    val outputPath: String,
    val content: String,
    val conflictPolicy: ConflictPolicy,
    val outputKind: ArtifactOutputKind = ArtifactOutputKind.CHECKED_IN_SOURCE,
    val resolvedOutputRoot: String = "",
)

data class PlanReport(
    val items: List<ArtifactPlanItem>,
    val outcome: PlanOutcome = PlanOutcome.SUCCEEDED,
    val diagnostics: PipelineDiagnostics? = null,
    val managedFieldDefaults: ManagedFieldDefaultsConfig? = null,
    val managedFieldPolicies: List<ResolvedManagedEntityPolicy> = emptyList(),
    val evidence: PlanEvidence? = null,
)

enum class PlanOutcome {
    SUCCEEDED,
    FAILED,
}

data class PipelineResult(
    val planItems: List<ArtifactPlanItem> = emptyList(),
    val renderedArtifacts: List<RenderedArtifact> = emptyList(),
    val writtenPaths: List<String> = emptyList(),
    val warnings: List<String> = emptyList(),
    val managedFieldPolicies: List<ResolvedManagedEntityPolicy> = emptyList(),
    val diagnostics: PipelineDiagnostics? = null,
)

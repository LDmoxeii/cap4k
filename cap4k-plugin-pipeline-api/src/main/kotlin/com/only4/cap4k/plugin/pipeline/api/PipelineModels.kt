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
    val referenceTable: String? = null,
    val explicitRelationType: String? = null,
    val lazy: Boolean? = null,
    val countHint: String? = null,
    val generatedValueDeclared: Boolean = false,
    val generatedValueStrategy: String? = null,
    val deleted: Boolean? = null,
    val version: Boolean? = null,
    val managed: Boolean? = null,
    val exposed: Boolean? = null,
    val insertable: Boolean? = null,
    val updatable: Boolean? = null,
)

data class UniqueConstraintModel(
    val physicalName: String,
    val columns: List<String>,
)

data class DbTableSnapshot(
    val tableName: String,
    val comment: String,
    val columns: List<DbColumnSnapshot>,
    val primaryKey: List<String>,
    val uniqueConstraints: List<UniqueConstraintModel>,
    val parentTable: String? = null,
    val aggregateRoot: Boolean = true,
    val valueObject: Boolean = false,
    val dynamicInsert: Boolean? = null,
    val dynamicUpdate: Boolean? = null,
)

data class DesignSpecEntry(
    val tag: String,
    val packageName: String,
    val name: String,
    val description: String,
    val aggregates: List<String>,
    val persist: Boolean? = null,
    val traits: Set<RequestTrait> = emptySet(),
    val requestFields: List<FieldModel>,
    val responseFields: List<FieldModel>,
    val message: String? = null,
    val targets: List<String> = emptyList(),
    val valueType: String? = null,
    val parameters: List<ValidatorParameterModel> = emptyList(),
)

data class DesignFieldSnapshot(
    val name: String,
    val type: String,
    val nullable: Boolean = false,
    val defaultValue: String? = null,
)

data class ValidatorParameterModel(
    val name: String,
    val type: String,
    val nullable: Boolean = false,
    val defaultValue: String? = null,
)

data class DesignElementSnapshot(
    val tag: String,
    val packageName: String,
    val name: String,
    val description: String,
    val aggregates: List<String> = emptyList(),
    val entity: String? = null,
    val persist: Boolean? = null,
    val traits: Set<RequestTrait> = emptySet(),
    val requestFields: List<DesignFieldSnapshot> = emptyList(),
    val responseFields: List<DesignFieldSnapshot> = emptyList(),
    val message: String? = null,
    val targets: List<String> = emptyList(),
    val valueType: String? = null,
    val parameters: List<ValidatorParameterModel> = emptyList(),
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
    val generateTranslation: Boolean,
    val items: List<EnumItemModel>,
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

data class KspMetadataSnapshot(
    override val id: String = "ksp-metadata",
    val aggregates: List<AggregateMetadataRecord>,
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
)

data class AggregateInverseRelationModel(
    val ownerEntityName: String,
    val ownerEntityPackageName: String,
    val fieldName: String,
    val targetEntityName: String,
    val targetEntityPackageName: String,
    val relationType: AggregateRelationType,
    val joinColumn: String,
    val fetchType: AggregateFetchType,
    val nullable: Boolean = false,
    val insertable: Boolean = false,
    val updatable: Boolean = false,
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
    val valueObject: Boolean = false,
    val parentEntityName: String? = null,
)

data class AggregateColumnJpaModel(
    val fieldName: String,
    val columnName: String,
    val isId: Boolean,
    val converterTypeFqn: String? = null,
    val converterClassFqn: String? = converterTypeFqn?.let { "$it.Converter" },
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
    val version: Boolean? = null,
    val insertable: Boolean? = null,
    val updatable: Boolean? = null,
)

data class AggregatePersistenceProviderControl(
    val entityName: String,
    val entityPackageName: String,
    val tableName: String,
    val dynamicInsert: Boolean? = null,
    val dynamicUpdate: Boolean? = null,
    val softDeleteColumn: String? = null,
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

enum class SpecialFieldSource {
    DB_EXPLICIT,
    DSL_DEFAULT,
    NONE,
}

enum class SpecialFieldWritePolicy {
    READ_WRITE,
    CREATE_ONLY,
    READ_ONLY,
    SYSTEM_TRANSITION_ONLY,
}

data class ResolvedIdPolicy(
    val fieldName: String,
    val columnName: String,
    val strategy: String,
    val kind: AggregateIdPolicyKind,
    val source: SpecialFieldSource,
    val writePolicy: SpecialFieldWritePolicy,
)

data class ResolvedMarkerPolicy(
    val enabled: Boolean,
    val fieldName: String? = null,
    val columnName: String? = null,
    val source: SpecialFieldSource,
    val writePolicy: SpecialFieldWritePolicy = SpecialFieldWritePolicy.READ_WRITE,
)

data class ResolvedManagedFieldPolicy(
    val fieldName: String,
    val columnName: String,
    val writePolicy: SpecialFieldWritePolicy,
    val source: SpecialFieldSource,
)

data class ResolvedWriteSurfacePolicy(
    val createAllowedFields: List<String> = emptyList(),
    val updateAllowedFields: List<String> = emptyList(),
)

data class AggregateSpecialFieldResolvedPolicy(
    val entityName: String,
    val entityPackageName: String,
    val tableName: String,
    val id: ResolvedIdPolicy,
    val deleted: ResolvedMarkerPolicy,
    val version: ResolvedMarkerPolicy,
    val managedFields: List<ResolvedManagedFieldPolicy> = emptyList(),
    val writeSurface: ResolvedWriteSurfacePolicy = ResolvedWriteSurfacePolicy(),
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

data class DrawingBoardFieldModel(
    val name: String,
    val type: String,
    val nullable: Boolean = false,
    val defaultValue: String? = null,
)

data class DrawingBoardElementModel(
    val tag: String,
    val packageName: String,
    val name: String,
    val description: String,
    val aggregates: List<String> = emptyList(),
    val entity: String? = null,
    val persist: Boolean? = null,
    val traits: Set<RequestTrait> = emptySet(),
    val requestFields: List<DrawingBoardFieldModel> = emptyList(),
    val responseFields: List<DrawingBoardFieldModel> = emptyList(),
    val message: String? = null,
    val targets: List<String> = emptyList(),
    val valueType: String? = null,
    val parameters: List<ValidatorParameterModel> = emptyList(),
) {
    val designJsonRequestFields: List<DrawingBoardFieldModel>
        get() = if (tag == "domain_event") {
            requestFields.filterNot { it.name.equals("entity", ignoreCase = true) }
        } else {
            requestFields
        }

    val designJsonTraits: List<String>
        get() = traits.map { it.name.lowercase() }
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

sealed interface DesignInteractionModel {
    val packageName: String
    val typeName: String
    val description: String
    val aggregateRef: AggregateRef?
    val requestFields: List<FieldModel>
    val responseFields: List<FieldModel>
}

enum class CommandVariant {
    DEFAULT,
    VOID,
}

enum class RequestTrait {
    PAGE,
}

data class CommandModel(
    override val packageName: String,
    override val typeName: String,
    override val description: String,
    override val aggregateRef: AggregateRef? = null,
    override val requestFields: List<FieldModel> = emptyList(),
    override val responseFields: List<FieldModel> = emptyList(),
    val variant: CommandVariant,
) : DesignInteractionModel

data class QueryModel(
    override val packageName: String,
    override val typeName: String,
    override val description: String,
    override val aggregateRef: AggregateRef? = null,
    override val requestFields: List<FieldModel> = emptyList(),
    override val responseFields: List<FieldModel> = emptyList(),
    val traits: Set<RequestTrait> = emptySet(),
) : DesignInteractionModel

data class ClientModel(
    override val packageName: String,
    override val typeName: String,
    override val description: String,
    override val aggregateRef: AggregateRef? = null,
    override val requestFields: List<FieldModel> = emptyList(),
    override val responseFields: List<FieldModel> = emptyList(),
) : DesignInteractionModel

enum class UnsupportedTablePolicy {
    FAIL,
    SKIP,
}

data class ValidatorModel(
    val packageName: String,
    val typeName: String,
    val description: String,
    val message: String,
    val targets: List<String>,
    val valueType: String,
    val parameters: List<ValidatorParameterModel> = emptyList(),
)

data class ApiPayloadModel(
    val packageName: String,
    val typeName: String,
    val description: String,
    val requestFields: List<FieldModel> = emptyList(),
    val responseFields: List<FieldModel> = emptyList(),
    val traits: Set<RequestTrait> = emptySet(),
)

data class DomainEventModel(
    val packageName: String,
    val typeName: String,
    val description: String,
    val aggregateName: String,
    val aggregatePackageName: String,
    val persist: Boolean,
    val fields: List<FieldModel> = emptyList(),
)

data class CanonicalModel(
    val commands: List<CommandModel> = emptyList(),
    val queries: List<QueryModel> = emptyList(),
    val clients: List<ClientModel> = emptyList(),
    val validators: List<ValidatorModel> = emptyList(),
    val domainEvents: List<DomainEventModel> = emptyList(),
    val schemas: List<SchemaModel> = emptyList(),
    val entities: List<EntityModel> = emptyList(),
    val repositories: List<RepositoryModel> = emptyList(),
    val analysisGraph: AnalysisGraphModel? = null,
    val drawingBoard: DrawingBoardModel? = null,
    val apiPayloads: List<ApiPayloadModel> = emptyList(),
    val sharedEnums: List<SharedEnumDefinition> = emptyList(),
    val aggregateRelations: List<AggregateRelationModel> = emptyList(),
    val aggregateInverseRelations: List<AggregateInverseRelationModel> = emptyList(),
    val aggregateEntityJpa: List<AggregateEntityJpaModel> = emptyList(),
    val aggregatePersistenceFieldControls: List<AggregatePersistenceFieldControl> = emptyList(),
    val aggregatePersistenceProviderControls: List<AggregatePersistenceProviderControl> = emptyList(),
    val aggregateIdPolicyControls: List<AggregateIdPolicyControl> = emptyList(),
    val aggregateSpecialFieldResolvedPolicies: List<AggregateSpecialFieldResolvedPolicy> = emptyList(),
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

data class RenderedArtifact(
    val outputPath: String,
    val content: String,
    val conflictPolicy: ConflictPolicy,
    val outputKind: ArtifactOutputKind = ArtifactOutputKind.CHECKED_IN_SOURCE,
    val resolvedOutputRoot: String = "",
)

data class PlanReport(
    val items: List<ArtifactPlanItem>,
    val diagnostics: PipelineDiagnostics? = null,
    val aggregateSpecialFieldDefaults: AggregateSpecialFieldDefaultsConfig? = null,
    val aggregateSpecialFieldResolvedPolicies: List<AggregateSpecialFieldResolvedPolicy> = emptyList(),
)

data class PipelineResult(
    val planItems: List<ArtifactPlanItem> = emptyList(),
    val renderedArtifacts: List<RenderedArtifact> = emptyList(),
    val writtenPaths: List<String> = emptyList(),
    val warnings: List<String> = emptyList(),
    val aggregateSpecialFieldResolvedPolicies: List<AggregateSpecialFieldResolvedPolicy> = emptyList(),
    val diagnostics: PipelineDiagnostics? = null,
)

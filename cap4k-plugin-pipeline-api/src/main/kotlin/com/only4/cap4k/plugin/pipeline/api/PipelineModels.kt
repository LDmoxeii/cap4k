package com.only4.cap4k.plugin.pipeline.api

data class FieldModel(
    val name: String,
    val type: String,
    val nullable: Boolean = false,
    val defaultValue: String? = null,
    val typeBinding: String? = null,
    val enumItems: List<EnumItemModel> = emptyList(),
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
    val generatedValueStrategy: String? = null,
    val version: Boolean? = null,
    val insertable: Boolean? = null,
    val updatable: Boolean? = null,
)

data class DbTableSnapshot(
    val tableName: String,
    val comment: String,
    val columns: List<DbColumnSnapshot>,
    val primaryKey: List<String>,
    val uniqueConstraints: List<List<String>>,
    val parentTable: String? = null,
    val aggregateRoot: Boolean = true,
    val valueObject: Boolean = false,
    val dynamicInsert: Boolean? = null,
    val dynamicUpdate: Boolean? = null,
    val softDeleteColumn: String? = null,
)

data class DesignSpecEntry(
    val tag: String,
    val packageName: String,
    val name: String,
    val description: String,
    val aggregates: List<String>,
    val persist: Boolean? = null,
    val requestFields: List<FieldModel>,
    val responseFields: List<FieldModel>,
)

data class DesignFieldSnapshot(
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
    val requestFields: List<DesignFieldSnapshot> = emptyList(),
    val responseFields: List<DesignFieldSnapshot> = emptyList(),
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
)

data class EntityModel(
    val name: String,
    val packageName: String,
    val tableName: String,
    val comment: String,
    val fields: List<FieldModel>,
    val idField: FieldModel,
    val uniqueConstraints: List<List<String>> = emptyList(),
    val aggregateRoot: Boolean = true,
    val valueObject: Boolean = false,
    val parentEntityName: String? = null,
)

data class AggregateColumnJpaModel(
    val fieldName: String,
    val columnName: String,
    val isId: Boolean,
    val converterTypeFqn: String? = null,
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
    val requestFields: List<DrawingBoardFieldModel> = emptyList(),
    val responseFields: List<DrawingBoardFieldModel> = emptyList(),
)

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

enum class RequestKind {
    COMMAND,
    QUERY,
    CLIENT,
}

enum class UnsupportedTablePolicy {
    FAIL,
    SKIP,
}

data class RequestModel(
    val kind: RequestKind,
    val packageName: String,
    val typeName: String,
    val description: String,
    val aggregateName: String? = null,
    val aggregatePackageName: String? = null,
    val requestFields: List<FieldModel> = emptyList(),
    val responseFields: List<FieldModel> = emptyList(),
)

data class ValidatorModel(
    val packageName: String,
    val typeName: String,
    val description: String,
    val valueType: String,
)

data class ApiPayloadModel(
    val packageName: String,
    val typeName: String,
    val description: String,
    val requestFields: List<FieldModel> = emptyList(),
    val responseFields: List<FieldModel> = emptyList(),
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
    val requests: List<RequestModel> = emptyList(),
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
    val aggregateEntityJpa: List<AggregateEntityJpaModel> = emptyList(),
    val aggregatePersistenceFieldControls: List<AggregatePersistenceFieldControl> = emptyList(),
    val aggregatePersistenceProviderControls: List<AggregatePersistenceProviderControl> = emptyList(),
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

data class ArtifactPlanItem(
    val generatorId: String,
    val moduleRole: String,
    val templateId: String,
    val outputPath: String,
    val context: Map<String, Any?> = emptyMap(),
    val conflictPolicy: ConflictPolicy,
)

data class RenderedArtifact(
    val outputPath: String,
    val content: String,
    val conflictPolicy: ConflictPolicy,
)

data class PlanReport(
    val items: List<ArtifactPlanItem>,
    val diagnostics: PipelineDiagnostics? = null,
)

data class PipelineResult(
    val planItems: List<ArtifactPlanItem> = emptyList(),
    val renderedArtifacts: List<RenderedArtifact> = emptyList(),
    val writtenPaths: List<String> = emptyList(),
    val warnings: List<String> = emptyList(),
    val diagnostics: PipelineDiagnostics? = null,
)

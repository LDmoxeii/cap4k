package com.only4.cap4k.plugin.pipeline.generator.aggregate

import com.only4.cap4k.plugin.pipeline.api.ArtifactPlanItem
import com.only4.cap4k.plugin.pipeline.api.AggregateIdPolicyKind
import com.only4.cap4k.plugin.pipeline.api.AggregateRelationType
import com.only4.cap4k.plugin.pipeline.api.ArtifactLayoutResolver
import com.only4.cap4k.plugin.pipeline.api.CanonicalModel
import com.only4.cap4k.plugin.pipeline.api.EntityModel
import com.only4.cap4k.plugin.pipeline.api.FieldModel
import com.only4.cap4k.plugin.pipeline.api.ManagedCreationInputPolicy
import com.only4.cap4k.plugin.pipeline.api.ManagedFieldRole
import com.only4.cap4k.plugin.pipeline.api.ManagedValueAuthority
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig
import com.only4.cap4k.plugin.pipeline.api.StrongIdKind
import com.only4.cap4k.plugin.pipeline.api.StrongIdModel

internal class EntityArtifactPlanner : AggregateArtifactFamilyPlanner {
    override fun plan(config: ProjectConfig, model: CanonicalModel): List<ArtifactPlanItem> {
        val artifactLayout = ArtifactLayoutResolver(config.basePackage, config.artifactLayout)
        val planning = AggregateEnumPlanning.from(model, artifactLayout, config.typeRegistry.entries)
        val defaultProjector = AggregateEntityDefaultProjector()
        val generatedOwnIdsByEntity = GeneratedOwnIdPlanning.from(model).associateBy { it.entityFqn }

        return model.entities.map { entity ->
            val aggregateName = aggregateRootName(entity, model.entities)
            val entityJpa = model.aggregateEntityJpa.singleOrNull {
                it.entityName == entity.name && it.entityPackageName == entity.packageName
            }
            val resolvedPolicy = model.managedFieldPolicies.singleOrNull {
                it.entityName == entity.name && it.entityPackageName == entity.packageName
            }
            val entrustedFields = AggregateEntrustedFieldPlanning.resolve(entity, model)
            val scalarJpaByField = entityJpa?.columns.orEmpty().associateBy { it.fieldName }
            val controlsByField = model.aggregatePersistenceFieldControls
                .filter { it.entityName == entity.name && it.entityPackageName == entity.packageName }
                .associateBy { it.fieldName }
            val managedByField = resolvedPolicy?.fields.orEmpty().associateBy { it.fieldName }
            val idPolicyControl = model.aggregateIdPolicyControls.firstOrNull {
                it.entityName == entity.name && it.entityPackageName == entity.packageName
            }
            val providerControl = model.aggregatePersistenceProviderControls.firstOrNull {
                it.entityName == entity.name && it.entityPackageName == entity.packageName
            }
            val relationPlan = AggregateRelationPlanning.planFor(
                entity = entity,
                relations = model.aggregateRelations,
            )
            val relationJoinColumns = relationPlan.relationFields
                .filter {
                    when (it["relationType"]) {
                        AggregateRelationType.MANY_TO_ONE.name,
                        AggregateRelationType.ONE_TO_ONE.name,
                        -> true
                        else -> false
                    }
                }
                .mapNotNull { it["joinColumn"] as? String }
                .toSet()
            val idColumnName = providerControl?.let { control ->
                requireNotNull(scalarJpaByField[control.idFieldName]) {
                    "missing aggregate JPA metadata for ${entity.packageName}.${entity.name}.${control.idFieldName}"
                }.columnName
            }
            val versionColumnName = providerControl?.versionFieldName?.let { versionFieldName ->
                requireNotNull(scalarJpaByField[versionFieldName]) {
                    "missing aggregate JPA metadata for ${entity.packageName}.${entity.name}.${versionFieldName}"
                }.columnName
            }
            val softDeletePolicy = providerControl?.softDelete
            val softDeleteDialect = softDeletePolicy?.let {
                val jdbcUrl = config.sources["db"]
                    ?.options
                    ?.get("url")
                    ?.toString()
                    .orEmpty()
                AggregateSqlDialectResolver.resolve(jdbcUrl)
            }
            fun jpaIdentifierKotlinStringLiteral(value: String): String {
                val renderedIdentifier = softDeleteDialect
                    ?.let { dialect -> AggregateSoftDeleteRendering.quoteIdentifier(value, dialect) }
                    ?: value
                return renderedIdentifier.toKotlinStringLiteral()
            }
            val renderedSoftDelete = softDeletePolicy?.let { policy ->
                val control = requireNotNull(providerControl)
                val deletedField = requireNotNull(entity.fields.singleOrNull { it.name == policy.fieldName }) {
                    "missing aggregate field ${entity.packageName}.${entity.name}.${policy.fieldName}"
                }
                requireNotNull(scalarJpaByField[policy.fieldName]) {
                    "missing aggregate JPA metadata for ${entity.packageName}.${entity.name}.${policy.fieldName}"
                }
                AggregateSoftDeleteRendering.render(
                    policy = policy,
                    dialect = requireNotNull(softDeleteDialect),
                    tableName = control.tableName,
                    idColumnName = requireNotNull(idColumnName),
                    versionColumnName = versionColumnName,
                    deletedKotlinType = planning.resolveFieldType(entity.packageName, deletedField),
                )
            }
            val renderedRelationFields = relationPlan.relationFields.map { relation ->
                val joinColumn = relation["joinColumn"] as? String
                if (joinColumn == null) {
                    relation
                } else {
                    relation + mapOf(
                        "joinColumnKotlinStringLiteral" to jpaIdentifierKotlinStringLiteral(joinColumn)
                    )
                }
            }
            val systemTransitionFieldNames = resolvedPolicy?.fields.orEmpty()
                .filter { it.role == ManagedFieldRole.SOFT_DELETE }
                .map { it.fieldName }
            systemTransitionFieldNames.forEach { fieldName ->
                require(
                    renderedSoftDelete != null &&
                        softDeletePolicy?.fieldName == fieldName
                ) {
                    "aggregate field ${entity.packageName}.${entity.name}.$fieldName has " +
                        "SYSTEM_TRANSITION_ONLY write policy but no semantic property initializer"
                }
            }
            val softDeleteContext = softDeletePolicy?.let { policy ->
                mapOf(
                    "enabled" to true,
                    "columnName" to policy.columnName,
                    "storageKind" to policy.storageKind.name,
                    "activeSentinel" to policy.activeSentinel.name,
                    "tombstoneStrategy" to policy.tombstoneStrategy.name,
                )
            }
            val softDeleteSql = renderedSoftDelete?.sqlDelete
            val softDeleteWhereClause = renderedSoftDelete?.whereClause
            val fieldContexts = entity.fields
                .mapNotNull { field ->
                    val jpa = requireNotNull(scalarJpaByField[field.name]) {
                        "missing aggregate JPA metadata for ${entity.packageName}.${entity.name}.${field.name}"
                    }
                    if (jpa.columnName in relationJoinColumns) {
                        null
                    } else {
                        val control = controlsByField[field.name]
                        val isSoftDeleteField = softDeletePolicy?.fieldName == field.name
                        val strongId = if (isSoftDeleteField) {
                            null
                        } else {
                            resolveStrongId(model, entity, field)
                        }
                        val fieldType = strongId?.typeName ?: planning.resolveFieldType(entity.packageName, field)
                        val renderedType = if (strongId != null) {
                            AggregateRenderedType(strongId.typeName, listOf(strongId.fqn()))
                        } else {
                            aggregateRenderedType(fieldType)
                        }
                        val typeRef = strongId?.fqn()
                        val embeddedId = strongId != null && isOwnIdField(entity, field, strongId)
                        val generatedOwnId =
                            generatedOwnIdsByEntity["${entity.packageName}.${entity.name}"] != null &&
                                field.name == entity.idField.name
                        val providerAssignedIdentity = entrustedFields.isDatabaseIdentity(field.name)
                        val providerAssignedVersion = entrustedFields.isVersion(field.name)
                        val providerAssigned = providerAssignedIdentity || providerAssignedVersion
                        val idPolicyApplies = jpa.isId && idPolicyControl?.idFieldName == field.name
                        val generatedValueStrategy = if (
                            strongId == null &&
                            idPolicyApplies &&
                            idPolicyControl.kind == AggregateIdPolicyKind.DATABASE_SIDE
                        ) {
                            "IDENTITY"
                        } else {
                            control?.generatedValueStrategy
                        }
                        val defaultValue = if (strongId != null || isSoftDeleteField) {
                            null
                        } else {
                            defaultProjector.project(
                                fieldPath = "${entity.packageName}.${entity.name}.${field.name}",
                                fieldType = fieldType,
                                nullable = field.nullable,
                                rawDefaultValue = field.defaultValue,
                                enumItems = planning.resolveEnumItems(entity.packageName, field),
                            )
                        }
                        val managedField = managedByField[field.name]
                        val providerInitializedManagedField = managedField?.let { policy ->
                            policy.persistence.insert in setOf(
                                ManagedValueAuthority.PERSISTENCE_PROVIDER,
                                ManagedValueAuthority.DATABASE,
                            )
                        } == true
                        val insertable = when {
                            embeddedId -> null
                            control?.insertable != null -> control.insertable
                            control?.updatable != null -> true
                            else -> null
                        }
                        val updatable = when {
                            embeddedId -> null
                            control?.updatable != null -> control.updatable
                            control?.insertable != null -> true
                            else -> null
                        }
                        val constructorIncluded =
                            !generatedOwnId && !providerAssigned && !providerInitializedManagedField &&
                                managedField?.creationInput != ManagedCreationInputPolicy.OMIT
                        val optionalManagedField =
                            managedField?.creationInput == ManagedCreationInputPolicy.OPTIONAL
                        val initializedManagedField = managedField != null &&
                            managedField.creationInput == ManagedCreationInputPolicy.OMIT &&
                            !isSoftDeleteField
                        val propertyNullable = providerAssigned || providerInitializedManagedField ||
                            initializedManagedField || optionalManagedField || field.nullable
                        val propertyInitializer = when {
                            providerAssigned || providerInitializedManagedField || initializedManagedField -> "null"
                            isSoftDeleteField -> requireNotNull(renderedSoftDelete).propertyInitializer
                            else -> field.name
                        }
                        mapOf(
                            "fieldName" to field.name,
                            "fieldType" to fieldType,
                            "name" to field.name,
                            "type" to fieldType,
                            "renderedType" to renderedType.renderedType,
                            "typeImports" to renderedType.imports,
                            "nullable" to field.nullable,
                            "constructorNullable" to (field.nullable || optionalManagedField),
                            "defaultValue" to defaultValue,
                            "propertyNullable" to propertyNullable,
                            "propertyInitializer" to propertyInitializer,
                            "constructorIncluded" to constructorIncluded,
                            "typeRef" to typeRef,
                            "strongId" to (strongId != null),
                            "embeddedId" to embeddedId,
                            "generatedOwnId" to generatedOwnId,
                            "typeBinding" to field.typeBinding,
                            "enumItems" to field.enumItems,
                            "columnName" to jpa.columnName,
                            "columnNameKotlinStringLiteral" to
                                jpaIdentifierKotlinStringLiteral(jpa.columnName),
                            "isId" to jpa.isId,
                            "converterTypeRef" to jpa.converterTypeFqn,
                            "converterClassRef" to jpa.converterClassFqn,
                            "generatedValueStrategy" to generatedValueStrategy,
                            "providerAssignedIdentity" to providerAssignedIdentity,
                            "providerAssignedVersion" to providerAssignedVersion,
                            "providerAssignedManagedField" to providerInitializedManagedField,
                            "generatedEvents" to control?.generatedEvents.orEmpty(),
                            "isVersion" to providerAssignedVersion,
                            "insertable" to insertable,
                            "updatable" to updatable,
                            "attributeOverrideNullable" to field.nullable,
                            "attributeOverrideInsertable" to insertable,
                            "attributeOverrideUpdatable" to when {
                                embeddedId -> false
                                updatable != null -> updatable
                                else -> true
                            },
                            "attributeOverrideLength" to if (strongId?.valueType == "String") jpa.columnLength else null,
                        )
                    }
                }
            val scalarFields = fieldContexts
            validateScalarTypeImportCollisions(entity, scalarFields)
            val scalarTypeImports = scalarFields.flatMap { field ->
                (field["typeImports"] as? List<*>)?.filterIsInstance<String>().orEmpty()
            }
            val scalarImports = relationPlan.imports + scalarTypeImports
            generatedKotlinArtifact(
                config = config,
                artifactLayout = artifactLayout,
                moduleRole = "domain",
                templateId = "aggregate/entity.kt.peb",
                packageName = entity.packageName,
                typeName = entity.name,
                context = mapOf(
                    "packageName" to entity.packageName,
                    "typeName" to entity.name,
                    "comment" to entity.comment,
                    "aggregateElement" to aggregateElementContext(entity, aggregateName),
                    "aggregateName" to aggregateName,
                    "aggregateRoot" to entity.aggregateRoot,
                    "tableName" to entity.tableName,
                    "entityJpa" to mapOf(
                        "entityEnabled" to (entityJpa?.entityEnabled ?: true),
                        "tableName" to (entityJpa?.tableName ?: entity.tableName),
                        "tableNameKotlinStringLiteral" to jpaIdentifierKotlinStringLiteral(
                            entityJpa?.tableName ?: entity.tableName
                        ),
                    ),
                    "idField" to entity.idField,
                    "hasConverterFields" to scalarFields.any { it["converterClassRef"] != null },
                    "hasGeneratedValueFields" to scalarFields.any {
                        it["isId"] == true && it["generatedValueStrategy"] == "IDENTITY"
                    },
                    "hasEmbeddedIdFields" to scalarFields.any { it["embeddedId"] == true },
                    "hasStrongIdFields" to scalarFields.any { it["strongId"] == true },
                    "hasEmbeddedStrongIdFields" to scalarFields.any {
                        it["strongId"] == true && it["embeddedId"] != true
                    },
                    "hasVersionFields" to scalarFields.any { it["isVersion"] == true },
                    "softDelete" to (softDeleteContext ?: mapOf("enabled" to false)),
                    "softDeleteSql" to softDeleteSql,
                    "softDeleteWhereClause" to softDeleteWhereClause,
                    "softDeleteSqlKotlinStringLiteral" to softDeleteSql?.toKotlinStringLiteral(),
                    "softDeleteWhereClauseKotlinStringLiteral" to softDeleteWhereClause?.toKotlinStringLiteral(),
                    "jpaImports" to relationPlan.jpaImports,
                    "imports" to scalarImports.distinct(),
                    "fields" to fieldContexts,
                    "scalarFields" to scalarFields,
                    "constructorFields" to scalarFields.filter { it["constructorIncluded"] == true },
                    "relationFields" to renderedRelationFields,
                ),
            )
        }
    }

    private fun validateScalarTypeImportCollisions(
        entity: EntityModel,
        scalarFields: List<Map<String, Any?>>,
    ) {
        val candidates = scalarFields.mapNotNull { field ->
            val imports = (field["typeImports"] as? List<*>)?.filterIsInstance<String>().orEmpty()
            if (imports.isEmpty()) return@mapNotNull null
            val renderedType = field["renderedType"] as? String ?: return@mapNotNull null
            ScalarImportCandidate(
                fieldName = field["fieldName"] as? String ?: field["name"] as? String ?: "<unknown>",
                simpleName = renderedType.substringBefore("<").substringAfterLast("."),
                imports = imports,
            )
        }

        val collisions = candidates
            .groupBy { it.simpleName }
            .filterValues { group -> group.flatMap { it.imports }.distinct().size > 1 }

        require(collisions.isEmpty()) {
            val simpleNames = collisions.keys.joinToString(", ")
            val details = collisions.entries.joinToString("; ") { (simpleName, group) ->
                val imports = group.flatMap { it.imports }.distinct().joinToString()
                val fields = group.map { it.fieldName }.distinct().joinToString()
                "$simpleName used by [$fields]: $imports"
            }
            val label = if (collisions.size == 1) {
                "ambiguous scalar type name $simpleNames"
            } else {
                "ambiguous scalar type names $simpleNames"
            }
            "$label for ${entity.packageName}.${entity.name}: $details"
        }
    }

    private fun resolveStrongId(
        model: CanonicalModel,
        entity: EntityModel,
        field: FieldModel,
    ): StrongIdModel? {
        val ownId = model.strongIds.firstOrNull {
            it.kind == StrongIdKind.OWN_ID &&
                it.ownerEntityName == entity.name &&
                it.ownerEntityPackageName == entity.packageName &&
                it.typeName == field.type.shortTypeName() &&
                field.name == entity.idField.name
        }
        if (ownId != null) return ownId

        val matches = model.strongIds.filter { strongId ->
            field.type == strongId.typeName || field.type == strongId.fqn()
        }
        val selectedMatches = matches
            .filter { it.kind != StrongIdKind.OWN_ID }
            .takeIf { it.isNotEmpty() }
            ?: matches
        require(selectedMatches.size <= 1) {
            "ambiguous strong id type ${field.type} for ${entity.packageName}.${entity.name}.${field.name}"
        }
        return selectedMatches.singleOrNull()
    }

    private fun isOwnIdField(
        entity: EntityModel,
        field: FieldModel,
        strongId: StrongIdModel,
    ): Boolean =
        field.name == entity.idField.name &&
            strongId.kind == StrongIdKind.OWN_ID &&
            strongId.ownerEntityName == entity.name &&
            strongId.ownerEntityPackageName == entity.packageName

    private fun StrongIdModel.fqn(): String = "${packageName}.${typeName}"

    private fun String.shortTypeName(): String = removeSuffix("?").substringAfterLast('.')

}

private data class ScalarImportCandidate(
    val fieldName: String,
    val simpleName: String,
    val imports: List<String>,
)

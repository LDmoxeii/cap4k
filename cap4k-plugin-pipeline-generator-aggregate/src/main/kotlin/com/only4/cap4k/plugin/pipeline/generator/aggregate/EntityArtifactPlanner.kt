package com.only4.cap4k.plugin.pipeline.generator.aggregate

import com.only4.cap4k.plugin.pipeline.api.ArtifactPlanItem
import com.only4.cap4k.plugin.pipeline.api.AggregateIdPolicyKind
import com.only4.cap4k.plugin.pipeline.api.AggregateRelationType
import com.only4.cap4k.plugin.pipeline.api.ArtifactLayoutResolver
import com.only4.cap4k.plugin.pipeline.api.CanonicalModel
import com.only4.cap4k.plugin.pipeline.api.EntityModel
import com.only4.cap4k.plugin.pipeline.api.FieldModel
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig
import com.only4.cap4k.plugin.pipeline.api.StrongIdKind
import com.only4.cap4k.plugin.pipeline.api.StrongIdModel

internal class EntityArtifactPlanner : AggregateArtifactFamilyPlanner {
    override fun plan(config: ProjectConfig, model: CanonicalModel): List<ArtifactPlanItem> {
        val artifactLayout = ArtifactLayoutResolver(config.basePackage, config.artifactLayout)
        val planning = AggregateEnumPlanning.from(model, artifactLayout, config.typeRegistry.entries)
        val defaultProjector = AggregateEntityDefaultProjector()

        return model.entities.map { entity ->
            val aggregateName = aggregateRootName(entity, model.entities)
            val entityJpa = model.aggregateEntityJpa.singleOrNull {
                it.entityName == entity.name && it.entityPackageName == entity.packageName
            }
            val resolvedPolicy = model.aggregateSpecialFieldResolvedPolicies.singleOrNull {
                it.entityName == entity.name && it.entityPackageName == entity.packageName
            }
            val scalarJpaByField = entityJpa?.columns.orEmpty().associateBy { it.fieldName }
            val controlsByField = model.aggregatePersistenceFieldControls
                .filter { it.entityName == entity.name && it.entityPackageName == entity.packageName }
                .associateBy { it.fieldName }
            val managedByField = resolvedPolicy?.managedFields.orEmpty().associateBy { it.fieldName }
            val idPolicyControl = model.aggregateIdPolicyControls.firstOrNull {
                it.entityName == entity.name && it.entityPackageName == entity.packageName
            }
            val relationPlan = AggregateRelationPlanning.planFor(
                entity = entity,
                relations = model.aggregateRelations,
                inverseRelations = model.aggregateInverseRelations,
            )
            val readOnlyInverseJoinColumns = relationPlan.relationFields
                .filter {
                    it["relationType"] == AggregateRelationType.MANY_TO_ONE.name &&
                        it["readOnly"] == true
                }
                .mapNotNull { it["joinColumn"] as? String }
                .toSet()
            val relationJoinColumns = relationPlan.relationFields
                .filter {
                    when (it["relationType"]) {
                        AggregateRelationType.MANY_TO_ONE.name,
                        AggregateRelationType.ONE_TO_ONE.name,
                        -> it["readOnly"] != true
                        else -> false
                    }
                }
                .mapNotNull { it["joinColumn"] as? String }
                .toSet()
            val fieldContexts = entity.fields
                .mapNotNull { field ->
                    val jpa = requireNotNull(scalarJpaByField[field.name]) {
                        "missing aggregate JPA metadata for ${entity.packageName}.${entity.name}.${field.name}"
                    }
                    if (jpa.columnName in relationJoinColumns) {
                        null
                    } else {
                        val control = controlsByField[field.name]
                        val strongId = resolveStrongId(model, entity, field)
                        val fieldType = strongId?.typeName ?: planning.resolveFieldType(entity.packageName, field)
                        val renderedType = if (strongId != null) {
                            AggregateRenderedType(strongId.typeName, listOf(strongId.fqn()))
                        } else {
                            aggregateRenderedType(fieldType)
                        }
                        val typeRef = strongId?.fqn()
                        val embeddedId = strongId != null && isAggregateRootIdField(entity, field, strongId)
                        val idPolicyApplies = jpa.isId && idPolicyControl?.idFieldName == field.name
                        val applicationSideIdStrategy: String? = null
                        val generatedValueStrategy = if (
                            strongId == null &&
                            idPolicyApplies &&
                            idPolicyControl.kind == AggregateIdPolicyKind.DATABASE_SIDE
                        ) {
                            "IDENTITY"
                        } else {
                            control?.generatedValueStrategy
                        }
                        val isVersionField = when {
                            resolvedPolicy?.version?.enabled == true ->
                                resolvedPolicy.version.fieldName == field.name
                            else -> control?.version == true
                        }
                        val defaultValue = if (strongId != null) {
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
                        val insertable = when {
                            embeddedId -> null
                            jpa.columnName in readOnlyInverseJoinColumns -> false
                            control?.insertable != null -> control.insertable
                            control?.updatable != null -> true
                            applicationSideIdStrategy != null -> true
                            else -> null
                        }
                        val updatable = when {
                            embeddedId -> null
                            jpa.columnName in readOnlyInverseJoinColumns -> false
                            applicationSideIdStrategy != null -> false
                            control?.updatable != null -> control.updatable
                            control?.insertable != null -> true
                            else -> null
                        }
                        val writePolicy = when {
                            jpa.isId && resolvedPolicy != null -> resolvedPolicy.id.writePolicy.name
                            isVersionField && resolvedPolicy != null -> resolvedPolicy.version.writePolicy.name
                            managedByField[field.name] != null -> managedByField.getValue(field.name).writePolicy.name
                            else -> "READ_WRITE"
                        }
                        mapOf(
                            "fieldName" to field.name,
                            "fieldType" to fieldType,
                            "name" to field.name,
                            "type" to fieldType,
                            "renderedType" to renderedType.renderedType,
                            "typeImports" to renderedType.imports,
                            "nullable" to field.nullable,
                            "defaultValue" to defaultValue,
                            "typeRef" to typeRef,
                            "strongId" to (strongId != null),
                            "embeddedId" to embeddedId,
                            "typeBinding" to field.typeBinding,
                            "enumItems" to field.enumItems,
                            "columnName" to jpa.columnName,
                            "isId" to jpa.isId,
                            "converterTypeRef" to jpa.converterTypeFqn,
                            "converterClassRef" to jpa.converterClassFqn,
                            "generatedValueStrategy" to generatedValueStrategy,
                            "applicationSideIdStrategy" to applicationSideIdStrategy,
                            "isVersion" to isVersionField,
                            "writePolicy" to writePolicy,
                            "parentRef" to field.parentRef,
                            "managedRole" to field.managedRole?.name,
                            "managed" to (field.managedRole != null),
                            "inherited" to field.inherited,
                            "structuralParentRef" to field.parentRef,
                            "insertable" to insertable,
                            "updatable" to updatable,
                            "attributeOverrideNullable" to field.nullable,
                            "attributeOverrideInsertable" to insertable,
                            "attributeOverrideUpdatable" to when {
                                embeddedId -> false
                                updatable != null -> updatable
                                else -> true
                            },
                        )
                    }
                }
            val scalarFields = fieldContexts.filterNot { it["inherited"] == true }
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
                    ),
                    "idField" to entity.idField,
                    "hasConverterFields" to scalarFields.any { it["converterClassRef"] != null },
                    "hasGeneratedValueFields" to scalarFields.any {
                        it["isId"] == true && it["generatedValueStrategy"] == "IDENTITY"
                    },
                    "hasApplicationSideIdFields" to scalarFields.any { it["applicationSideIdStrategy"] != null },
                    "hasEmbeddedIdFields" to scalarFields.any { it["embeddedId"] == true },
                    "hasStrongIdFields" to scalarFields.any { it["strongId"] == true },
                    "hasEmbeddedStrongIdFields" to scalarFields.any {
                        it["strongId"] == true && it["embeddedId"] != true
                    },
                    "hasVersionFields" to scalarFields.any { it["isVersion"] == true },
                    "softDeleteSql" to null,
                    "softDeleteWhereClause" to null,
                    "jpaImports" to relationPlan.jpaImports,
                    "imports" to scalarImports.distinct(),
                    "fields" to fieldContexts,
                    "scalarFields" to scalarFields,
                    "relationFields" to relationPlan.relationFields,
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
        val aggregateRootId = model.strongIds.firstOrNull {
            it.kind == StrongIdKind.AGGREGATE_ROOT &&
                it.ownerAggregateName == entity.name &&
                it.ownerAggregatePackageName == entity.packageName &&
                it.typeName == field.type.shortTypeName() &&
                field.name == entity.idField.name
        }
        if (aggregateRootId != null) return aggregateRootId

        val matches = model.strongIds.filter { strongId ->
            field.type == strongId.typeName || field.type == strongId.fqn()
        }
        require(matches.size <= 1) {
            "ambiguous strong id type ${field.type} for ${entity.packageName}.${entity.name}.${field.name}"
        }
        return matches.singleOrNull()
    }

    private fun isAggregateRootIdField(
        entity: EntityModel,
        field: FieldModel,
        strongId: StrongIdModel,
    ): Boolean =
        field.name == entity.idField.name &&
            strongId.kind == StrongIdKind.AGGREGATE_ROOT &&
            strongId.ownerAggregateName == entity.name &&
            strongId.ownerAggregatePackageName == entity.packageName

    private fun StrongIdModel.fqn(): String = "${packageName}.${typeName}"

    private fun String.shortTypeName(): String = removeSuffix("?").substringAfterLast('.')
}

private data class ScalarImportCandidate(
    val fieldName: String,
    val simpleName: String,
    val imports: List<String>,
)

package com.only4.cap4k.plugin.pipeline.generator.aggregate

import com.only4.cap4k.plugin.pipeline.api.ArtifactPlanItem
import com.only4.cap4k.plugin.pipeline.api.AggregateSpecialFieldResolvedPolicy
import com.only4.cap4k.plugin.pipeline.api.ArtifactLayoutResolver
import com.only4.cap4k.plugin.pipeline.api.CanonicalModel
import com.only4.cap4k.plugin.pipeline.api.EntityModel
import com.only4.cap4k.plugin.pipeline.api.FieldModel
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig
import com.only4.cap4k.plugin.pipeline.api.SpecialFieldWritePolicy
import com.only4.cap4k.plugin.pipeline.api.StrongIdKind
import com.only4.cap4k.plugin.pipeline.api.StrongIdModel

internal class FactoryArtifactPlanner : AggregateArtifactFamilyPlanner {
    override fun plan(config: ProjectConfig, model: CanonicalModel): List<ArtifactPlanItem> {
        val artifactLayout = ArtifactLayoutResolver(config.basePackage, config.artifactLayout)
        val derivedTypeReferences = AggregateDerivedTypeReferences.from(model)
        val planning = AggregateEnumPlanning.from(model, artifactLayout, config.typeRegistry.entries)
        val defaultProjector = AggregateEntityDefaultProjector()

        return model.entities.filter { it.aggregateRoot }.map { entity ->
            val entityTypeFqn = derivedTypeReferences.entityFqn(entity)
            val packageName = artifactLayout.aggregateFactoryPackage(entity.packageName)
            val typeName = "${entity.name}Factory"
            val resolvedPolicy = model.aggregateSpecialFieldResolvedPolicies.singleOrNull {
                it.entityName == entity.name && it.entityPackageName == entity.packageName
            }
            val ownStrongId = resolveAggregateRootStrongId(model, entity)
            val ownIdFieldName = ownStrongId?.let { entity.idField.name }
            val ownIdInitializer = ownStrongId?.let { "${it.typeName}.new()" }
            val ownIdTypeRef = ownStrongId?.fqn()
            val payloadFields = resolvedPolicy
                ?.writeSurface
                ?.createAllowedFields
                ?.toSet()
                ?.let { createAllowedFields ->
                    entity.fields
                        .filter { it.name in createAllowedFields }
                        .filterNot { it.name == entity.idField.name }
                        .map { field ->
                            val strongId = resolveStrongId(model, field)
                            val fieldType = strongId?.typeName ?: planning.resolveFieldType(entity.packageName, field)
                            val renderedType = aggregateRenderedTypeWithModelImports(model, fieldType)
                            mapOf(
                                "name" to field.name,
                                "type" to fieldType,
                                "typeName" to fieldType,
                                "renderedType" to renderedType.renderedType,
                                "typeImports" to renderedType.imports,
                                "typeRef" to strongId?.fqn(),
                                "strongId" to (strongId != null),
                                "nullable" to field.nullable,
                            )
                        }
                }
                ?: emptyList()
            val constructorMapping = planConstructorMapping(
                entity = entity,
                model = model,
                planning = planning,
                defaultProjector = defaultProjector,
                resolved = resolvedPolicy != null,
                resolvedPolicy = resolvedPolicy,
                ownStrongId = ownStrongId,
                payloadFields = payloadFields,
            )
            val imports = (
                listOfNotNull(ownIdTypeRef) +
                    payloadFields.flatMap { field ->
                        (field["typeImports"] as? List<*>)?.filterIsInstance<String>().orEmpty()
                    } +
                    payloadFields.mapNotNull { it["typeRef"] as? String }
                ).distinct()

            checkedInKotlinArtifact(
                config = config,
                artifactLayout = artifactLayout,
                moduleRole = "domain",
                templateId = "aggregate/factory.kt.peb",
                packageName = packageName,
                typeName = typeName,
                context = mapOf(
                    "packageName" to packageName,
                    "typeName" to typeName,
                    "aggregateElement" to aggregateElementContext(
                        aggregate = entity.name,
                        name = typeName,
                        packageName = packageName,
                        description = entity.comment,
                        type = "factory",
                    ),
                    "payloadTypeName" to "Payload",
                    "payloadMetadataName" to "${entity.name}Payload",
                    "payloadWriteSurfaceResolved" to (resolvedPolicy != null),
                    "payloadFields" to payloadFields,
                    "constructorMappingResolved" to constructorMapping.resolved,
                    "constructorPayloadFields" to constructorMapping.payloadFields,
                    "ownIdFieldName" to ownIdFieldName,
                    "ownIdInitializer" to ownIdInitializer,
                    "ownIdTypeRef" to ownIdTypeRef,
                    "entityName" to entity.name,
                    "entityTypeFqn" to entityTypeFqn,
                    "aggregateName" to entity.name,
                    "comment" to entity.comment,
                    "imports" to imports,
                ),
            )
        }
    }

    private fun planConstructorMapping(
        entity: EntityModel,
        model: CanonicalModel,
        planning: AggregateEnumPlanning,
        defaultProjector: AggregateEntityDefaultProjector,
        resolved: Boolean,
        resolvedPolicy: AggregateSpecialFieldResolvedPolicy?,
        ownStrongId: StrongIdModel?,
        payloadFields: List<Map<String, Any?>>,
    ): ConstructorMapping {
        if (!resolved) return ConstructorMapping(resolved = false, payloadFields = emptyList())

        val payloadFieldNames = payloadFields.mapNotNull { it["name"] as? String }.toSet()
        val missingRequiredFields = entity.fields
            .filterNot { ownStrongId != null && it.name == entity.idField.name }
            .filterNot { it.name in payloadFieldNames }
            .filterNot { field ->
                hasConstructorDefault(
                    entity = entity,
                    field = field,
                    model = model,
                    planning = planning,
                    defaultProjector = defaultProjector,
                )
            }

        if (missingRequiredFields.isNotEmpty()) {
            val blockingRequiredFields = missingRequiredFields
                .filterNot { field -> canDeferManagedConstructorField(resolvedPolicy, field) }
            if (ownStrongId != null && blockingRequiredFields.isNotEmpty()) {
                val fieldNames = blockingRequiredFields.joinToString(", ") { it.name }
                error(
                    "factory ${entity.packageName}.${entity.name} cannot derive constructor mapping " +
                        "for required fields: $fieldNames"
                )
            }
            return ConstructorMapping(resolved = false, payloadFields = emptyList())
        }

        return ConstructorMapping(resolved = true, payloadFields = payloadFields)
    }

    private fun canDeferManagedConstructorField(
        resolvedPolicy: AggregateSpecialFieldResolvedPolicy?,
        field: FieldModel,
    ): Boolean {
        if (
            resolvedPolicy?.version?.enabled == true &&
            resolvedPolicy.version.fieldName == field.name &&
            resolvedPolicy.version.writePolicy == SpecialFieldWritePolicy.READ_ONLY
        ) {
            return true
        }
        val managedField = resolvedPolicy?.managedFields?.firstOrNull { it.fieldName == field.name } ?: return false
        return managedField.writePolicy == SpecialFieldWritePolicy.READ_ONLY ||
            managedField.writePolicy == SpecialFieldWritePolicy.SYSTEM_TRANSITION_ONLY
    }

    private fun hasConstructorDefault(
        entity: EntityModel,
        field: FieldModel,
        model: CanonicalModel,
        planning: AggregateEnumPlanning,
        defaultProjector: AggregateEntityDefaultProjector,
    ): Boolean {
        if (resolveStrongId(model, field) != null) return false
        val fieldType = planning.resolveFieldType(entity.packageName, field)
        return defaultProjector.project(
            fieldPath = "${entity.packageName}.${entity.name}.${field.name}",
            fieldType = fieldType,
            nullable = field.nullable,
            rawDefaultValue = field.defaultValue,
            enumItems = planning.resolveEnumItems(entity.packageName, field),
        ) != null
    }

    private fun resolveAggregateRootStrongId(
        model: CanonicalModel,
        entity: EntityModel,
    ): StrongIdModel? =
        model.strongIds.singleOrNull {
            it.kind == StrongIdKind.AGGREGATE_ROOT &&
                it.ownerAggregateName == entity.name &&
                it.ownerAggregatePackageName == entity.packageName &&
                it.typeName == entity.idField.type.shortTypeName()
        }

    private fun resolveStrongId(model: CanonicalModel, field: FieldModel): StrongIdModel? {
        val matches = model.strongIds.filter { strongId ->
            field.type == strongId.typeName || field.type == strongId.fqn()
        }
        require(matches.size <= 1) {
            "ambiguous strong id type ${field.type} for factory field ${field.name}"
        }
        return matches.singleOrNull()
    }

    private fun StrongIdModel.fqn(): String = "${packageName}.${typeName}"

    private fun String.shortTypeName(): String = removeSuffix("?").substringAfterLast('.')

    private data class ConstructorMapping(
        val resolved: Boolean,
        val payloadFields: List<Map<String, Any?>>,
    )
}

package com.only4.cap4k.plugin.pipeline.generator.aggregate

import com.only4.cap4k.plugin.pipeline.api.ArtifactLayoutResolver
import com.only4.cap4k.plugin.pipeline.api.ArtifactPlanItem
import com.only4.cap4k.plugin.pipeline.api.CanonicalModel
import com.only4.cap4k.plugin.pipeline.api.EntityModel
import com.only4.cap4k.plugin.pipeline.api.ManagedFieldRole
import com.only4.cap4k.plugin.pipeline.api.ManagedExplicitValuePolicy
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig
import com.only4.cap4k.plugin.pipeline.api.ResolvedManagedFieldPolicy

internal class ManagedFieldCatalogArtifactPlanner : AggregateArtifactFamilyPlanner {
    override fun plan(config: ProjectConfig, model: CanonicalModel): List<ArtifactPlanItem> {
        val policies = model.managedFieldPolicies.filter { it.fields.isNotEmpty() }
        if (policies.isEmpty()) return emptyList()

        val artifactLayout = ArtifactLayoutResolver(config.basePackage, config.artifactLayout)
        val typeCatalog = AggregateEnumPlanning.from(model, artifactLayout, config.typeRegistry.entries)
        val generatedIdsByEntity = GeneratedOwnIdPlanning.from(model).associateBy { it.entityFqn }
        val softDeleteByEntity = model.aggregatePersistenceProviderControls
            .mapNotNull { control ->
                control.softDelete?.let { softDelete ->
                    "${control.entityPackageName}.${control.entityName}" to softDelete
                }
            }
            .toMap()

        val bindings = policies.flatMap { entityPolicy ->
            val entity = requireNotNull(model.entities.singleOrNull {
                it.name == entityPolicy.entityName && it.packageName == entityPolicy.entityPackageName
            }) {
                "missing entity for managed field catalog ${entityPolicy.entityPackageName}.${entityPolicy.entityName}"
            }
            val entityFqn = "${entity.packageName}.${entity.name}"
            entityPolicy.fields.map { fieldPolicy ->
                val field = requireNotNull(entity.fields.singleOrNull { it.name == fieldPolicy.fieldName }) {
                    "missing field for managed field catalog $entityFqn.${fieldPolicy.fieldName}"
                }
                val targetType = resolveTargetType(model, typeCatalog, entity, fieldPolicy)
                val semanticType = if (sameCanonicalType(fieldPolicy.semanticValueType, fieldPolicy.fieldType)) {
                    targetType
                } else {
                    normalizeRuntimeType(fieldPolicy.semanticValueType)
                }
                val runtimeSupport = when (fieldPolicy.role) {
                    ManagedFieldRole.IDENTIFIER -> identifierSupport(
                        fieldPolicy = fieldPolicy,
                        generatedId = generatedIdsByEntity[entityFqn],
                        targetType = targetType,
                    )
                    ManagedFieldRole.SOFT_DELETE -> {
                        val softDelete = requireNotNull(softDeleteByEntity[entityFqn]) {
                            "missing soft-delete provider projection for $entityFqn.${fieldPolicy.fieldName}"
                        }
                        val value = AggregateSoftDeleteRendering
                            .renderPropertyInitializer(softDelete, targetType)
                            .replace("UUID(", "java.util.UUID(")
                        mapOf("kind" to "SOFT_DELETE", "activeSentinelExpression" to value)
                    }
                    else -> forbiddenExplicitValueSupport(fieldPolicy, targetType)
                }
                mapOf(
                    "entityTypeExpression" to "$entityFqn::class",
                    "fieldName" to fieldPolicy.fieldName,
                    "fieldNameKotlinStringLiteral" to fieldPolicy.fieldName.toKotlinStringLiteral(),
                    "persistencePropertyNameKotlinStringLiteral" to fieldPolicy.fieldName.toKotlinStringLiteral(),
                    "columnNameKotlinStringLiteral" to fieldPolicy.columnName.toKotlinStringLiteral(),
                    "targetTypeExpression" to "${normalizeRuntimeType(targetType)}::class",
                    "targetTypeCheck" to normalizeRuntimeType(targetType),
                    "nullable" to fieldPolicy.nullable,
                    "policyKeyKotlinStringLiteral" to fieldPolicy.policyKey.toKotlinStringLiteral(),
                    "role" to fieldPolicy.role.name,
                    "explicitValue" to fieldPolicy.explicitValue.name,
                    "lifecycles" to fieldPolicy.lifecycles.map { it.name }.sorted(),
                    "handlerQualifierKotlinStringLiteral" to fieldPolicy.handlerQualifier?.toKotlinStringLiteral(),
                    "handlerSlotKotlinStringLiteral" to fieldPolicy.handlerSlot?.toKotlinStringLiteral(),
                    "semanticTypeExpression" to "${normalizeRuntimeType(semanticType)}::class",
                    "valueAdapterQualifierKotlinStringLiteral" to
                        fieldPolicy.valueAdapterQualifier?.toKotlinStringLiteral(),
                    "insertAuthority" to fieldPolicy.persistence.insert.name,
                    "updateAuthority" to fieldPolicy.persistence.update.name,
                    "runtimeSupport" to runtimeSupport,
                )
            }
        }.sortedWith(
            compareBy<Map<String, Any?>> { it.getValue("entityTypeExpression") as String }
                .thenBy { it.getValue("fieldNameKotlinStringLiteral") as String }
        )

        val packageName = ArtifactLayoutResolver.joinPackage(config.basePackage, "domain._share.managed")
        val typeName = "ManagedFieldCatalogContribution"
        return listOf(
            generatedKotlinArtifact(
                config = config,
                artifactLayout = artifactLayout,
                moduleRole = "domain",
                packageName = packageName,
                typeName = typeName,
                templateId = "aggregate/managed_field_catalog.kt.peb",
                context = mapOf(
                    "packageName" to packageName,
                    "typeName" to typeName,
                    "beanName" to "$packageName.managedFieldCatalogContribution",
                    "bindings" to bindings,
                ),
            )
        )
    }

    private fun resolveTargetType(
        model: CanonicalModel,
        typeCatalog: AggregateEnumPlanning,
        entity: EntityModel,
        fieldPolicy: ResolvedManagedFieldPolicy,
    ): String {
        val strongId = model.strongIds.singleOrNull { strongId ->
            strongId.ownerEntityName == entity.name &&
                strongId.ownerEntityPackageName == entity.packageName &&
                strongId.typeName == fieldPolicy.fieldType.removeSuffix("?").substringAfterLast('.')
        }
        if (strongId != null) return "${strongId.packageName}.${strongId.typeName}"
        val field = requireNotNull(entity.fields.singleOrNull { it.name == fieldPolicy.fieldName })
        return typeCatalog.resolveFieldType(entity.packageName, field)
    }

    private fun identifierSupport(
        fieldPolicy: ResolvedManagedFieldPolicy,
        generatedId: GeneratedOwnIdDescriptor?,
        targetType: String,
    ): Map<String, Any?>? = when (fieldPolicy.policyKey) {
        "identifier.uuid7", "identifier.snowflake" -> {
            if (generatedId != null) {
                mapOf(
                    "kind" to "APPLICATION_IDENTIFIER",
                    "allocateExpression" to applicationIdentifierAllocation(generatedId),
                    "validateExpression" to "${generatedId.idTypeFqn}.of(value.value)",
                )
            } else {
                val backingType = when (fieldPolicy.fieldType.removeSuffix("?").substringAfterLast('.')) {
                    "UUID" -> "java.util.UUID"
                    "String" -> "String"
                    "Long" -> "Long"
                    else -> error(
                        "unsupported generated identifier backing for ${fieldPolicy.fieldName}[${fieldPolicy.policyKey}]: " +
                            fieldPolicy.fieldType
                    )
                }
                mapOf(
                    "kind" to "APPLICATION_IDENTIFIER",
                    "allocateExpression" to "com.only4.cap4k.ddd.core.Mediator.identifiers.next(" +
                        "${fieldPolicy.policyKey.substringAfter('.').toKotlinStringLiteral()}, $backingType::class)",
                    "validateExpression" to null,
                )
            }
        }
        "identifier.assigned" -> mapOf(
            "kind" to "APPLICATION_IDENTIFIER",
            "allocateExpression" to null,
            "validateExpression" to null,
        )
        "identifier.database-identity" -> forbiddenExplicitValueSupport(fieldPolicy, targetType)
        else -> if (fieldPolicy.lifecycles.any { it.name == "ENTITY_ADMISSION" }) {
            mapOf(
                "kind" to "APPLICATION_IDENTIFIER",
                "allocateExpression" to null,
                "validateExpression" to null,
            )
        } else {
            null
        }
    }

    private fun forbiddenExplicitValueSupport(
        fieldPolicy: ResolvedManagedFieldPolicy,
        targetType: String,
    ): Map<String, Any?>? {
        if (
            fieldPolicy.explicitValue != ManagedExplicitValuePolicy.FORBID ||
            fieldPolicy.lifecycles.any { it.name == "ENTITY_ADMISSION" }
        ) return null
        val normalized = normalizeRuntimeType(targetType)
        val allowsIntegralZero = normalized in setOf("Byte", "Short", "Int", "Long") &&
            fieldPolicy.role in setOf(ManagedFieldRole.IDENTIFIER, ManagedFieldRole.VERSION)
        return mapOf(
            "kind" to "FORBIDDEN_EXPLICIT_VALUE",
            "allowsIntegralZero" to allowsIntegralZero,
        )
    }

    private fun applicationIdentifierAllocation(descriptor: GeneratedOwnIdDescriptor): String {
        val backingType = when (descriptor.backingType) {
            "UUID" -> "java.util.UUID"
            "String", "Long" -> descriptor.backingType
            else -> error(
                "unsupported generated identifier backing for ${descriptor.entityFqn}: ${descriptor.backingType}"
            )
        }
        return "${descriptor.idTypeFqn}.of(" +
            "com.only4.cap4k.ddd.core.Mediator.identifiers.next(" +
            "${descriptor.strategy.toKotlinStringLiteral()}, $backingType::class))"
    }

    private fun normalizeRuntimeType(type: String): String = when (val normalized = type.removeSuffix("?").trim()) {
        "Byte", "kotlin.Byte", "java.lang.Byte" -> "Byte"
        "Short", "kotlin.Short", "java.lang.Short" -> "Short"
        "Int", "kotlin.Int", "Integer", "java.lang.Integer" -> "Int"
        "Long", "kotlin.Long", "java.lang.Long" -> "Long"
        "Float", "kotlin.Float", "java.lang.Float" -> "Float"
        "Double", "kotlin.Double", "java.lang.Double" -> "Double"
        "Boolean", "kotlin.Boolean", "java.lang.Boolean" -> "Boolean"
        "String", "kotlin.String", "java.lang.String" -> "String"
        "UUID" -> "java.util.UUID"
        "Instant" -> "java.time.Instant"
        else -> normalized
    }

    private fun sameCanonicalType(left: String, right: String): Boolean =
        normalizeRuntimeType(left) == normalizeRuntimeType(right)
}

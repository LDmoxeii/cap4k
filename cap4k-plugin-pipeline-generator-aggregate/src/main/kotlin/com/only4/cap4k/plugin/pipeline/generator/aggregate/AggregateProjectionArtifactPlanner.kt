package com.only4.cap4k.plugin.pipeline.generator.aggregate

import com.only4.cap4k.plugin.pipeline.api.ArtifactLayoutResolver
import com.only4.cap4k.plugin.pipeline.api.ArtifactOutputKind
import com.only4.cap4k.plugin.pipeline.api.ArtifactPlanItem
import com.only4.cap4k.plugin.pipeline.api.CanonicalModel
import com.only4.cap4k.plugin.pipeline.api.GeneratorProvider
import com.only4.cap4k.plugin.pipeline.api.ManagedFieldRole
import com.only4.cap4k.plugin.pipeline.api.PipelineBoundaryAuthorities
import com.only4.cap4k.plugin.pipeline.api.PipelineBoundaryKind
import com.only4.cap4k.plugin.pipeline.api.PipelineCapabilityBoundary
import com.only4.cap4k.plugin.pipeline.api.PipelineCapabilityDescriptor
import com.only4.cap4k.plugin.pipeline.api.PipelineCapabilityKind
import com.only4.cap4k.plugin.pipeline.api.PipelineExecutionLane
import com.only4.cap4k.plugin.pipeline.api.PipelineInputRequirement
import com.only4.cap4k.plugin.pipeline.api.PipelineInputSafety
import com.only4.cap4k.plugin.pipeline.api.PipelinePublicTasks
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig

class AggregateProjectionArtifactPlanner : GeneratorProvider {
    override val id: String = "aggregate-projection"
    override val descriptor: PipelineCapabilityDescriptor = PipelineCapabilityDescriptor.builtIn(
        providerId = id,
        displayName = "Aggregate Projection Generator",
        kind = PipelineCapabilityKind.GENERATOR,
        module = "cap4k-plugin-pipeline-generator-aggregate",
        tacticalCarriers = listOf("Read Projection"),
        executionLanes = listOf(PipelineExecutionLane.GENERATED_SOURCE),
        tasks = listOf(PipelinePublicTasks.PLAN, PipelinePublicTasks.GENERATE, PipelinePublicTasks.GENERATE_SOURCES),
        inputRequirements = listOf(
            PipelineInputRequirement(
                id = "aggregate-projection-schema",
                capabilityIds = listOf("pipeline.source.db"),
                safety = PipelineInputSafety.LIVE_EXTERNAL,
            ),
        ),
        outputKinds = listOf(ArtifactOutputKind.GENERATED_SOURCE),
        boundaries = listOf(
            PipelineCapabilityBoundary(PipelineBoundaryKind.GENERATION, PipelineBoundaryAuthorities.PIPELINE_GENERATOR),
        ),
    )

    override fun plan(config: ProjectConfig, model: CanonicalModel): List<ArtifactPlanItem> {
        val artifactLayout = ArtifactLayoutResolver(config.basePackage, config.artifactLayout)
        val enumPlanning = AggregateEnumPlanning.from(model, artifactLayout, config.typeRegistry.entries)

        return model.entities.map { entity ->
            val entityJpa = model.aggregateEntityJpa.singleOrNull {
                it.entityName == entity.name && it.entityPackageName == entity.packageName
            }
            val scalarJpaByField = entityJpa?.columns.orEmpty().associateBy { it.fieldName }
            val controlsByField = model.aggregatePersistenceFieldControls
                .filter { it.entityName == entity.name && it.entityPackageName == entity.packageName }
                .associateBy { it.fieldName }
            val resolvedPolicy = model.managedFieldPolicies.singleOrNull {
                it.entityName == entity.name && it.entityPackageName == entity.packageName
            }
            val relationPlan = AggregateRelationPlanning.planFor(
                entity = entity,
                relations = model.aggregateRelations,
            )
            val relationFields = projectionRelationFields(config, entity.packageName, relationPlan.relationFields)
            val scalarFields = entity.fields.map { field ->
                val jpa = requireNotNull(scalarJpaByField[field.name]) {
                    "missing aggregate JPA metadata for ${entity.packageName}.${entity.name}.${field.name}"
                }
                val control = controlsByField[field.name]
                val fieldType = enumPlanning.resolveFieldType(entity.packageName, field)
                val renderedType = aggregateRenderedTypeWithModelImports(model, fieldType)
                val isVersionField = resolvedPolicy?.fieldByRole(ManagedFieldRole.VERSION)?.fieldName == field.name ||
                    control?.version == true
                mapOf(
                    "fieldName" to field.name,
                    "fieldType" to fieldType,
                    "name" to field.name,
                    "type" to fieldType,
                    "renderedType" to renderedType.renderedType,
                    "typeImports" to renderedType.imports,
                    "nullable" to field.nullable,
                    "defaultValue" to null,
                    "typeBinding" to field.typeBinding,
                    "enumItems" to enumPlanning.resolveEnumItems(entity.packageName, field),
                    "columnName" to jpa.columnName,
                    "isId" to jpa.isId,
                    "isVersion" to isVersionField,
                    "converterTypeRef" to jpa.converterTypeFqn,
                    "converterClassRef" to jpa.converterClassFqn,
                    "insertable" to null,
                    "updatable" to null,
                )
            }
            val scalarImports = scalarFields
                .flatMap { field ->
                    (field["typeImports"] as? List<*>)?.filterIsInstance<String>().orEmpty()
                }
            val packageName = projectionPackageName(config, entity.packageName)
            val typeName = "${entity.name}Projection"

            generatedKotlinArtifact(
                config = config,
                artifactLayout = artifactLayout,
                moduleRole = "adapter",
                packageName = packageName,
                typeName = typeName,
                templateId = "aggregate_projection/entity.kt.peb",
                generatorId = id,
                context = mapOf(
                    "packageName" to packageName,
                    "typeName" to typeName,
                    "aggregateElement" to aggregateElementContext(
                        aggregate = aggregateRootName(entity, model.entities),
                        name = typeName,
                        packageName = packageName,
                        description = entity.comment,
                        type = "projection",
                    ),
                    "sourceTypeName" to entity.name,
                    "sourcePackageName" to entity.packageName,
                    "comment" to entity.comment,
                    "tableName" to entity.tableName,
                    "entityJpa" to mapOf(
                        "entityEnabled" to (entityJpa?.entityEnabled ?: true),
                        "tableName" to (entityJpa?.tableName ?: entity.tableName),
                    ),
                    "idField" to entity.idField,
                    "hasConverterFields" to scalarFields.any { it["converterClassRef"] != null },
                    "hasGeneratedValueFields" to false,
                    "hasVersionFields" to scalarFields.any { it["isVersion"] == true },
                    "dynamicInsert" to false,
                    "dynamicUpdate" to false,
                    "softDeleteSql" to null,
                    "softDeleteWhereClause" to null,
                    "jpaImports" to emptyList<String>(),
                    "imports" to scalarImports.distinct(),
                    "fields" to scalarFields,
                    "scalarFields" to scalarFields,
                    "relationFields" to relationFields,
                    "relations" to relationFields,
                ),
            )
        }
    }

    private fun projectionRelationFields(
        config: ProjectConfig,
        ownerEntityPackage: String,
        relationFields: List<Map<String, Any?>>,
    ): List<Map<String, Any?>> {
        val ownerProjectionPackage = projectionPackageName(config, ownerEntityPackage)
        return relationFields.map { relation ->
            val targetEntityType = requireNotNull(relation["targetType"] as? String) {
                "missing target type in projection relation metadata"
            }
            val targetType = "${targetEntityType}Projection"
            val targetEntityPackage = requireNotNull(relation["targetPackageName"] as? String) {
                "missing target package in projection relation metadata"
            }
            val targetPackage = projectionPackageName(config, targetEntityPackage)
            relation + mapOf(
                "targetType" to targetType,
                "targetTypeRef" to if (targetPackage == ownerProjectionPackage) {
                    targetType
                } else {
                    "$targetPackage.$targetType"
                },
                "targetPackageName" to targetPackage,
            )
        }
    }

    private fun projectionPackageName(config: ProjectConfig, entityPackage: String): String {
        val aggregateRoot = ArtifactLayoutResolver.joinPackage(
            config.basePackage,
            config.artifactLayout.aggregate.packageRoot,
        )
        val normalizedEntityPackage = entityPackage.trim('.')
        val suffix = when {
            normalizedEntityPackage == aggregateRoot -> ""
            normalizedEntityPackage.startsWith("$aggregateRoot.") ->
                normalizedEntityPackage.removePrefix("$aggregateRoot.").trim('.')
            else -> normalizedEntityPackage
        }
        return ArtifactLayoutResolver.joinPackage(
            config.basePackage,
            "adapter.application.projections",
            suffix,
        )
    }
}

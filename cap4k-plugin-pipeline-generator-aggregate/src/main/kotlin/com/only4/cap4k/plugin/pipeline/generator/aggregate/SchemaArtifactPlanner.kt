package com.only4.cap4k.plugin.pipeline.generator.aggregate

import com.only4.cap4k.plugin.pipeline.api.ArtifactPlanItem
import com.only4.cap4k.plugin.pipeline.api.ArtifactLayoutResolver
import com.only4.cap4k.plugin.pipeline.api.CanonicalModel
import com.only4.cap4k.plugin.pipeline.api.EntityModel
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig
import java.nio.file.InvalidPathException
import java.nio.file.Path

internal class SchemaArtifactPlanner : AggregateArtifactFamilyPlanner {
    private companion object {
        const val SCHEMA_RUNTIME_PACKAGE = "com.only4.cap4k.ddd.domain.repo.schema"
    }

    override fun plan(config: ProjectConfig, model: CanonicalModel): List<ArtifactPlanItem> {
        val domainRoot = requireRelativeModule(config, "domain")
        val artifactLayout = ArtifactLayoutResolver(config.basePackage, config.artifactLayout)
        val selection = AggregateArtifactSelection.from(config)
        val derivedTypeReferences = AggregateDerivedTypeReferences.from(model)
        val planning = AggregateEnumPlanning.from(model, artifactLayout, config.typeRegistry)
        val entitiesByName = model.entities
            .groupBy { it.name }

        return model.schemas.map { schema ->
            val entity = requireUniqueSchemaEntity(schema.name, schema.entityName, entitiesByName[schema.entityName].orEmpty())
            val entityTypeFqn = derivedTypeReferences.entityFqn(entity)
            val qEntityTypeFqn = requireNotNull(derivedTypeReferences.qEntityFqn(schema.entityName))
            val aggregateTypeFqn = if (entity.aggregateRoot && selection.wrapperEnabled) {
                buildAggregateWrapperFqn(entity.packageName, entity.name)
            } else {
                ""
            }
            val ownerPackage = entity.packageName
            val fields = schema.fields.map { field ->
                val fieldType = planning.resolveFieldType(ownerPackage, field)
                mapOf(
                    "name" to field.name,
                    "fieldName" to field.name,
                    "columnName" to (field.columnName ?: field.name),
                    "fieldType" to fieldType,
                    "type" to fieldType,
                    "nullable" to field.nullable,
                    "defaultValue" to field.defaultValue,
                    "typeBinding" to field.typeBinding,
                    "enumItems" to field.enumItems,
                    "comment" to "",
                )
            }

            ArtifactPlanItem(
                generatorId = "aggregate",
                moduleRole = "domain",
                templateId = "aggregate/schema.kt.peb",
                outputPath = artifactLayout.kotlinSourcePath(domainRoot, schema.packageName, schema.name),
                context = mapOf(
                    "packageName" to schema.packageName,
                    "typeName" to schema.name,
                    "comment" to schema.comment,
                    "entityName" to schema.entityName,
                    "isAggregateRoot" to entity.aggregateRoot,
                    "schemaRuntimePackage" to SCHEMA_RUNTIME_PACKAGE,
                    "entityTypeFqn" to entityTypeFqn,
                    "qEntityTypeFqn" to qEntityTypeFqn,
                    "aggregateTypeFqn" to aggregateTypeFqn,
                    "wrapperEnabled" to selection.wrapperEnabled,
                    "repositorySupportQuerydsl" to false,
                    "fields" to fields,
                ),
                conflictPolicy = config.templates.conflictPolicy,
            )
        }
    }

    private fun requireUniqueSchemaEntity(
        schemaName: String,
        entityName: String,
        entities: List<EntityModel>,
    ): EntityModel {
        if (entities.size != 1) {
            error("schema $schemaName requires exactly one entity named $entityName, but found ${entities.size}")
        }
        return entities.single()
    }

    private fun buildAggregateWrapperFqn(packageName: String, entityName: String): String =
        if (packageName.isBlank()) {
            "Agg$entityName"
        } else {
            "$packageName.Agg$entityName"
        }
}

internal fun requireRelativeModule(config: ProjectConfig, role: String): String {
    val value = config.modules[role] ?: error("$role module is required")
    if (value.isBlank()) {
        throw IllegalArgumentException("$role module must be a valid relative filesystem path: $value")
    }
    if (value.startsWith(":")) {
        throw IllegalArgumentException("$role module must be a valid relative filesystem path: $value")
    }

    val path = try {
        Path.of(value)
    } catch (ex: InvalidPathException) {
        throw IllegalArgumentException("$role module must be a valid relative filesystem path: $value", ex)
    }

    if (path.isAbsolute) {
        throw IllegalArgumentException("$role module must be a valid relative filesystem path: $value")
    }
    if (path.root != null) {
        throw IllegalArgumentException("$role module must be a valid relative filesystem path: $value")
    }

    val normalized = path.normalize()
    if (normalized.nameCount > 0 && normalized.getName(0).toString() == "..") {
        throw IllegalArgumentException("$role module must be a valid relative filesystem path: $value")
    }

    return value
}

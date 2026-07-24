package com.only4.cap4k.plugin.pipeline.generator.aggregate

import com.only4.cap4k.plugin.pipeline.api.ArtifactPlanItem
import com.only4.cap4k.plugin.pipeline.api.ArtifactLayoutResolver
import com.only4.cap4k.plugin.pipeline.api.AggregateRelationType
import com.only4.cap4k.plugin.pipeline.api.CanonicalModel
import com.only4.cap4k.plugin.pipeline.api.EntityModel
import com.only4.cap4k.plugin.pipeline.api.OwnedRelationCardinality
import com.only4.cap4k.plugin.pipeline.api.OwnedRelationPersistenceShape
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig
import com.only4.cap4k.plugin.pipeline.api.SchemaModel
import java.nio.file.InvalidPathException
import java.nio.file.Path

internal class SchemaArtifactPlanner : AggregateArtifactFamilyPlanner {
    private companion object {
        const val SCHEMA_RUNTIME_PACKAGE = "com.only4.cap4k.ddd.domain.repo.schema"
    }

    override fun plan(config: ProjectConfig, model: CanonicalModel): List<ArtifactPlanItem> {
        val artifactLayout = ArtifactLayoutResolver(config.basePackage, config.artifactLayout)
        val derivedTypeReferences = AggregateDerivedTypeReferences.from(model)
        val planning = AggregateEnumPlanning.from(model, artifactLayout, config.typeRegistry.entries)
        val entitiesByName = model.entities
            .groupBy { it.name }
        val schemasByEntityName = model.schemas
            .groupBy { it.entityName }

        return model.schemas.map { schema ->
            val entity = requireUniqueSchemaEntity(schema.name, schema.entityName, entitiesByName[schema.entityName].orEmpty())
            val aggregateElement = aggregateRootNameOrNull(entity, model.entities)?.let { aggregateName ->
                aggregateElementContext(
                    aggregate = aggregateName,
                    name = schema.name,
                    packageName = schema.packageName,
                    description = schema.comment,
                    type = "schema",
                )
            }
            val entityTypeFqn = derivedTypeReferences.entityFqn(entity)
            val ownerPackage = entity.packageName
            val fields = schema.fields.map { field ->
                val fieldType = planning.resolveFieldType(ownerPackage, field)
                val renderedType = aggregateRenderedTypeWithModelImports(model, fieldType)
                mapOf(
                    "name" to field.name,
                    "fieldName" to field.name,
                    "columnName" to (field.columnName ?: field.name),
                    "fieldType" to fieldType,
                    "type" to fieldType,
                    "renderedType" to renderedType.renderedType,
                    "typeImports" to renderedType.imports,
                    "nullable" to field.nullable,
                    "defaultValue" to field.defaultValue,
                    "typeBinding" to field.typeBinding,
                    "enumItems" to field.enumItems,
                    "comment" to "",
                )
            }
            val imports = fields
                .flatMap { field ->
                    (field["typeImports"] as? List<*>)?.filterIsInstance<String>().orEmpty()
                }
                .distinct()
            val relationJoins = relationJoinsFor(
                schema = schema,
                entity = entity,
                model = model,
                schemasByEntityName = schemasByEntityName,
            )

            generatedKotlinArtifact(
                config = config,
                artifactLayout = artifactLayout,
                moduleRole = "domain",
                templateId = "aggregate/schema.kt.peb",
                packageName = schema.packageName,
                typeName = schema.name,
                context = mapOf(
                    "packageName" to schema.packageName,
                    "typeName" to schema.name,
                    "comment" to schema.comment,
                    "entityName" to schema.entityName,
                    "isAggregateRoot" to entity.aggregateRoot,
                    "schemaRuntimePackage" to SCHEMA_RUNTIME_PACKAGE,
                    "entityTypeFqn" to entityTypeFqn,
                    "imports" to imports,
                    "fields" to fields,
                    "relationJoins" to relationJoins,
                ) + listOfNotNull(aggregateElement?.let { "aggregateElement" to it }),
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

    private fun relationJoinsFor(
        schema: SchemaModel,
        entity: EntityModel,
        model: CanonicalModel,
        schemasByEntityName: Map<String, List<SchemaModel>>,
    ): List<Map<String, Any?>> {
        val relationPlan = AggregateRelationPlanning.planFor(
            entity = entity,
            relations = model.aggregateRelations,
            inverseRelations = emptyList(),
        )

        return relationPlan.relationFields
            .filter(::isSchemaJoinRelation)
            .map { relation ->
                val targetSchema = requireUniqueTargetSchema(
                    ownerSchemaName = schema.name,
                    relation = relation,
                    schemasByEntityName = schemasByEntityName,
                )
                val domainName = relation.requiredString("domainName")
                val persistencePathName = relation.requiredString("persistencePathName")
                val targetEntityName = relation.requiredString("targetType")
                val targetEntityPackageName = relation.requiredString("targetPackageName")
                val ownedCardinality = relation.requiredString("ownedCardinality")
                val relationKind = when (ownedCardinality) {
                    OwnedRelationCardinality.MANY.name -> "OWNED_MANY"
                    OwnedRelationCardinality.ONE.name -> "OWNED_ONE"
                    else -> error(
                        "schema ${schema.name} relation $domainName has unsupported owned cardinality: $ownedCardinality"
                    )
                }
                mapOf(
                    "domainName" to domainName,
                    "persistencePathName" to persistencePathName,
                    "methodName" to "join${domainName.upperCamelIdentifier()}",
                    "relationKind" to relationKind,
                    "targetEntityName" to targetEntityName,
                    "targetEntityTypeFqn" to "$targetEntityPackageName.$targetEntityName",
                    "targetSchemaName" to targetSchema.name,
                    "targetSchemaFqn" to "${targetSchema.packageName}.${targetSchema.name}",
                    "relationFieldType" to when (relationKind) {
                        "OWNED_MANY" -> "RelationCollectionField"
                        "OWNED_ONE" -> "RelationOptionalField"
                        else -> error("schema ${schema.name} relation $domainName has unsupported relation kind: $relationKind")
                    },
                    "nullable" to relation["nullable"],
                    "ownedCardinality" to ownedCardinality,
                    "persistenceShape" to relation.requiredString("persistenceShape"),
                )
            }
    }

    private fun isSchemaJoinRelation(relation: Map<String, Any?>): Boolean =
        relation["owned"] == true &&
            relation["relationType"] == AggregateRelationType.ONE_TO_MANY.name &&
            relation["persistenceShape"] == OwnedRelationPersistenceShape.ONE_TO_MANY_JOIN_COLUMN.name &&
            relation["ownedCardinality"] in setOf(
                OwnedRelationCardinality.MANY.name,
                OwnedRelationCardinality.ONE.name,
            )

    private fun requireUniqueTargetSchema(
        ownerSchemaName: String,
        relation: Map<String, Any?>,
        schemasByEntityName: Map<String, List<SchemaModel>>,
    ): SchemaModel {
        val domainName = relation.requiredString("domainName")
        val targetEntityName = relation.requiredString("targetType")
        val targetEntityPackageName = relation.requiredString("targetPackageName")
        val candidates = schemasByEntityName[targetEntityName].orEmpty()
        if (candidates.size != 1) {
            error(
                "schema $ownerSchemaName relation $domainName requires exactly one target schema for " +
                    "$targetEntityPackageName.$targetEntityName, but found ${candidates.size}"
            )
        }
        return candidates.single()
    }

    private fun Map<String, Any?>.requiredString(key: String): String =
        this[key] as? String ?: error("schema relation render model requires string key: $key")

    private fun String.upperCamelIdentifier(): String =
        if (isEmpty()) this else replaceFirstChar { it.titlecase() }

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

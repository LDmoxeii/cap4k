package com.only4.cap4k.plugin.pipeline.generator.aggregate

import com.only4.cap4k.plugin.pipeline.api.ArtifactPlanItem
import com.only4.cap4k.plugin.pipeline.api.CanonicalModel
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig
import java.nio.file.InvalidPathException
import java.nio.file.Path

internal class SchemaArtifactPlanner : AggregateArtifactFamilyPlanner {
    override fun plan(config: ProjectConfig, model: CanonicalModel): List<ArtifactPlanItem> {
        val domainRoot = requireRelativeModule(config, "domain")
        val derivedTypeReferences = AggregateDerivedTypeReferences.from(model)
        val planning = AggregateEnumPlanning.from(model, config.basePackage, config.typeRegistry)
        val entityOwnerByName = model.entities
            .groupBy { it.name }
            .mapValues { (_, entities) -> entities.singleOrNull()?.packageName }

        return model.schemas.map { schema ->
            val entityTypeFqn = derivedTypeReferences.entityFqn(schema.entityName) ?: ""
            val qEntityTypeFqn = derivedTypeReferences.qEntityFqn(schema.entityName) ?: ""
            val ownerPackage = entityOwnerByName[schema.entityName]
            val fields = schema.fields.map { field ->
                mapOf(
                    "name" to field.name,
                    "type" to planning.resolveFieldType(ownerPackage ?: schema.packageName, field),
                    "nullable" to field.nullable,
                    "defaultValue" to field.defaultValue,
                    "typeBinding" to field.typeBinding,
                    "enumItems" to field.enumItems,
                )
            }

            ArtifactPlanItem(
                generatorId = "aggregate",
                moduleRole = "domain",
                templateId = "aggregate/schema.kt.peb",
                outputPath = "$domainRoot/src/main/kotlin/${schema.packageName.replace(".", "/")}/${schema.name}.kt",
                context = mapOf(
                    "packageName" to schema.packageName,
                    "typeName" to schema.name,
                    "comment" to schema.comment,
                    "entityName" to schema.entityName,
                    "entityTypeFqn" to entityTypeFqn,
                    "qEntityTypeFqn" to qEntityTypeFqn,
                    "fields" to fields,
                ),
                conflictPolicy = config.templates.conflictPolicy,
            )
        }
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

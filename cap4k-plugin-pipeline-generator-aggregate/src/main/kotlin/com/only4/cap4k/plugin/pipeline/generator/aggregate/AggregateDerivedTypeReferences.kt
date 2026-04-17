package com.only4.cap4k.plugin.pipeline.generator.aggregate

import com.only4.cap4k.plugin.pipeline.api.CanonicalModel
import com.only4.cap4k.plugin.pipeline.api.EntityModel

internal class AggregateDerivedTypeReferences private constructor(
    private val entityFqns: Map<String, String?>,
) {
    fun entityFqn(entityName: String): String? = entityFqns[entityName]

    fun entityFqn(entity: EntityModel): String = buildEntityFqn(entity)

    fun qEntityFqn(entityName: String): String? =
        entityFqn(entityName)?.let(::buildQEntityFqn)

    companion object {
        fun from(model: CanonicalModel): AggregateDerivedTypeReferences {
            val entities = model.entities
                .groupBy(EntityModel::name)
                .mapValues { (_, entities) ->
                    entities.singleOrNull()?.let(::buildEntityFqn)
                }
            return AggregateDerivedTypeReferences(entities)
        }

        private fun buildEntityFqn(entity: EntityModel): String = buildEntityFqn(entity.packageName, entity.name)

        private fun buildEntityFqn(packageName: String, entityName: String): String =
            if (packageName.isBlank()) {
                entityName
            } else {
                "$packageName.$entityName"
            }

        private fun buildQEntityFqn(fqcn: String): String {
            val packageName = fqcn.substringBeforeLast('.', missingDelimiterValue = "")
            val simpleName = fqcn.substringAfterLast('.')
            return if (packageName.isBlank()) {
                "Q$simpleName"
            } else {
                "$packageName.Q$simpleName"
            }
        }
    }
}

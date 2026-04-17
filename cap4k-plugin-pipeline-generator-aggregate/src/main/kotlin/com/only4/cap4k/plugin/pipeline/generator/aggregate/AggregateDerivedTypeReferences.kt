package com.only4.cap4k.plugin.pipeline.generator.aggregate

import com.only4.cap4k.plugin.pipeline.api.CanonicalModel

internal class AggregateDerivedTypeReferences private constructor(
    private val entityFqns: Map<String, String>,
) {
    fun entityFqn(entityName: String): String? = entityFqns[entityName]

    fun qEntityFqn(entityName: String): String? =
        entityFqns[entityName]?.let { fqcn ->
            val packageName = fqcn.substringBeforeLast('.', missingDelimiterValue = "")
            val simpleName = fqcn.substringAfterLast('.')
            if (packageName.isBlank()) {
                "Q$simpleName"
            } else {
                "$packageName.Q$simpleName"
            }
        }

    companion object {
        fun from(model: CanonicalModel): AggregateDerivedTypeReferences {
            val entities = linkedMapOf<String, String>()
            model.entities.forEach { entity ->
                entities[entity.name] = "${entity.packageName}.${entity.name}"
            }
            return AggregateDerivedTypeReferences(entities)
        }
    }
}

package com.only4.cap4k.plugin.pipeline.generator.aggregate

import com.only4.cap4k.plugin.pipeline.api.ArtifactLayoutResolver
import com.only4.cap4k.plugin.pipeline.api.ArtifactPlanItem
import com.only4.cap4k.plugin.pipeline.api.CanonicalModel
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig

internal class GeneratedOwnIdArtifactPlanner : AggregateArtifactFamilyPlanner {
    override fun plan(config: ProjectConfig, model: CanonicalModel): List<ArtifactPlanItem> {
        val artifactLayout = ArtifactLayoutResolver(config.basePackage, config.artifactLayout)
        val descriptors = GeneratedOwnIdPlanning.from(model)
        if (descriptors.isEmpty()) return emptyList()

        val accessors = descriptors.map { descriptor ->
            generatedKotlinArtifact(
                config = config,
                artifactLayout = artifactLayout,
                moduleRole = "domain",
                packageName = descriptor.entityPackageName,
                typeName = descriptor.accessorTypeName,
                templateId = "aggregate/generated_own_id_accessor.kt.peb",
                context = mapOf(
                    "packageName" to descriptor.entityPackageName,
                    "typeName" to descriptor.accessorTypeName,
                    "entityName" to descriptor.entityName,
                    "entityFqn" to descriptor.entityFqn,
                    "idFieldName" to descriptor.idFieldName,
                    "idTypeName" to descriptor.idTypeName,
                    "idTypeFqn" to descriptor.idTypeFqn,
                    "label" to "${descriptor.entityName}.${descriptor.idFieldName}",
                    "strategy" to descriptor.strategy,
                    "backingType" to descriptor.backingType,
                    "backingTypeFqn" to if (descriptor.backingType == "UUID") "java.util.UUID" else null,
                ),
            )
        }

        val catalogPackage = ArtifactLayoutResolver.joinPackage(config.basePackage, "domain._share.identity")
        val catalogType = "GeneratedOwnIdCatalogContribution"
        val catalog = generatedKotlinArtifact(
            config = config,
            artifactLayout = artifactLayout,
            moduleRole = "domain",
            packageName = catalogPackage,
            typeName = catalogType,
            templateId = "aggregate/generated_own_id_catalog.kt.peb",
            context = mapOf(
                "packageName" to catalogPackage,
                "typeName" to catalogType,
                "beanName" to "$catalogPackage.generatedOwnIdCatalogContribution",
                "accessors" to descriptors.map {
                    mapOf("fqn" to it.accessorFqn, "entityFqn" to it.entityFqn)
                },
            ),
        )
        return accessors + catalog
    }
}

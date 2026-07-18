package com.only4.cap4k.plugin.pipeline.generator.design

import com.only4.cap4k.plugin.pipeline.api.ArtifactPlanItem
import com.only4.cap4k.plugin.pipeline.api.ArtifactLayoutResolver
import com.only4.cap4k.plugin.pipeline.api.CanonicalModel
import com.only4.cap4k.plugin.pipeline.api.GeneratorProvider
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig

class DesignDomainEventArtifactPlanner : GeneratorProvider {
    override val id: String = "domain-event"

    override fun plan(config: ProjectConfig, model: CanonicalModel): List<ArtifactPlanItem> {
        val blocks = model.designBlocks.filter { block -> block.selects(id) }
        if (blocks.isEmpty()) {
            return emptyList()
        }

        val domainRoot = requireRelativeModuleRoot(config, "domain")
        val artifactLayout = ArtifactLayoutResolver(config.basePackage, config.artifactLayout)

        return blocks.map { block ->
            val aggregate = block.ownerAggregateEntity(model)
            val packageKey = block.domainEventPackageKey(config, model)
            val typeName = block.domainEventTypeName()
            val packageName = artifactLayout.designDomainEventPackage(packageKey)
            val renderModel = DesignPayloadRenderModelFactory.createForDomainEventBlock(
                packageName = packageName,
                block = block,
                aggregate = aggregate,
                symbolRegistry = config.designSymbolRegistry(model),
            )
            ArtifactPlanItem(
                generatorId = id,
                moduleRole = "domain",
                templateId = "design/domain_event.kt.peb",
                outputPath = artifactLayout.kotlinSourcePath(domainRoot, packageName, typeName),
                context = mapOf(
                    "packageName" to renderModel.packageName,
                    "typeName" to renderModel.typeName,
                    "buildingBlock" to block.buildingBlockContext(id),
                    "description" to renderModel.description,
                    "descriptionText" to renderModel.descriptionText,
                    "descriptionCommentText" to renderModel.descriptionCommentText,
                    "descriptionKotlinStringLiteral" to renderModel.descriptionKotlinStringLiteral,
                    "aggregateName" to renderModel.aggregateName,
                    "aggregateType" to "${aggregate.packageName}.${aggregate.name}",
                    "persist" to (block.persist ?: false),
                    "imports" to renderModel.imports,
                    "fields" to renderModel.fields,
                    "nestedTypes" to renderModel.nestedTypes,
                ),
                conflictPolicy = config.templates.conflictPolicy,
            )
        }
    }
}

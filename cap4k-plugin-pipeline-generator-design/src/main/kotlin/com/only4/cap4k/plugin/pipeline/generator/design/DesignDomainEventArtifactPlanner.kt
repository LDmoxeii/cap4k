package com.only4.cap4k.plugin.pipeline.generator.design

import com.only4.cap4k.plugin.pipeline.api.*

class DesignDomainEventArtifactPlanner : GeneratorProvider {
    override val id: String = "domain-event"
    override val descriptor: PipelineCapabilityDescriptor = designDescriptor(
        providerId = id,
        displayName = "Domain Event Generator",
        tacticalCarrier = "Domain Event",
        boundaries = runtimeDesignBoundaries(providerOwned = true),
    )

    override fun plan(config: ProjectConfig, model: CanonicalModel): List<ArtifactPlanItem> {
        model.validateDomainEventPayloads()
        val blocks = model.designBlocks.filter { block -> block.selects(id) }
        if (blocks.isEmpty()) {
            return emptyList()
        }

        val domainRoot = requireRelativeModuleRoot(config, "domain")
        val artifactLayout = ArtifactLayoutResolver(config.basePackage, config.artifactLayout)

        return blocks.map { block ->
            val packageKey = block.domainEventPackageKey(config, model)
            val typeName = block.domainEventTypeName()
            val packageName = artifactLayout.designDomainEventPackage(packageKey)
            val renderModel = DesignPayloadRenderModelFactory.createForDomainEventBlock(
                packageName = packageName,
                block = block,
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
                    "eventName" to block.eventName,
                    "eventNameKotlinStringLiteral" to block.eventName.toKotlinStringLiteral(),
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

package com.only4.cap4k.plugin.pipeline.generator.design

import com.only4.cap4k.plugin.pipeline.api.ArtifactPlanItem
import com.only4.cap4k.plugin.pipeline.api.CanonicalModel
import com.only4.cap4k.plugin.pipeline.api.GeneratorProvider
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig

class DesignDomainEventArtifactPlanner : GeneratorProvider {
    override val id: String = "design-domain-event"

    override fun plan(config: ProjectConfig, model: CanonicalModel): List<ArtifactPlanItem> {
        val domainRoot = requireRelativeModuleRoot(config, "domain")
        val basePath = config.basePackage.replace(".", "/")

        return model.domainEvents.map { event ->
            val packagePath = event.packageName.replace(".", "/")
            val renderModel = DesignRenderModelFactory.createForDomainEvent(
                packageName = "${config.basePackage}.domain.${event.packageName}.events",
                event = event,
                typeRegistry = config.typeRegistry,
            )
            ArtifactPlanItem(
                generatorId = id,
                moduleRole = "domain",
                templateId = "design/domain_event.kt.peb",
                outputPath = "$domainRoot/src/main/kotlin/$basePath/domain/$packagePath/events/${event.typeName}.kt",
                context = mapOf(
                    "packageName" to renderModel.packageName,
                    "typeName" to renderModel.typeName,
                    "description" to renderModel.description,
                    "descriptionText" to renderModel.descriptionText,
                    "descriptionCommentText" to renderModel.descriptionCommentText,
                    "descriptionKotlinStringLiteral" to renderModel.descriptionKotlinStringLiteral,
                    "aggregateName" to renderModel.aggregateName,
                    "aggregateType" to "${event.aggregatePackageName}.${event.aggregateName}",
                    "persist" to event.persist,
                    "imports" to renderModel.imports,
                    "fields" to renderModel.requestFields,
                    "nestedTypes" to renderModel.requestNestedTypes,
                ),
                conflictPolicy = config.templates.conflictPolicy,
            )
        }
    }
}

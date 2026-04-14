package com.only4.cap4k.plugin.pipeline.generator.design

import com.only4.cap4k.plugin.pipeline.api.ArtifactPlanItem
import com.only4.cap4k.plugin.pipeline.api.CanonicalModel
import com.only4.cap4k.plugin.pipeline.api.GeneratorProvider
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig
import com.only4.cap4k.plugin.pipeline.api.RequestKind
import com.only4.cap4k.plugin.pipeline.api.RequestModel

class DesignArtifactPlanner : GeneratorProvider {
    override val id: String = "design"

    override fun plan(config: ProjectConfig, model: CanonicalModel): List<ArtifactPlanItem> {
        val applicationRoot = requireRelativeModuleRoot(config, "application")
        val basePath = config.basePackage.replace(".", "/")
        val plannedEntries = model.requests.withIndex()
            .filter { entry ->
                entry.value.kind == RequestKind.COMMAND || entry.value.kind == RequestKind.QUERY
            }

        return plannedEntries.map { entry ->
            val request = entry.value
            val siblingRequestTypeNames = plannedEntries
                .asSequence()
                .filter { it.index != entry.index && it.value.packageName == request.packageName }
                .map { it.value.typeName }
                .toSet()
            val packagePath = request.packageName.replace(".", "/")
            val subdir = when (request.kind) {
                RequestKind.COMMAND -> "commands"
                RequestKind.QUERY -> "queries"
                RequestKind.CLIENT -> error("client requests must be filtered before planning design artifacts")
            }
            val templateId = resolveTemplateId(request)

            ArtifactPlanItem(
                generatorId = id,
                moduleRole = "application",
                templateId = templateId,
                outputPath = "$applicationRoot/src/main/kotlin/$basePath/application/$subdir/$packagePath/${request.typeName}.kt",
                context = DesignRenderModelFactory.create(
                    packageName = "${config.basePackage}.application.$subdir.${request.packageName}",
                    request = request,
                    typeRegistry = config.typeRegistry,
                    siblingRequestTypeNames = siblingRequestTypeNames,
                ).toContextMap(),
                conflictPolicy = config.templates.conflictPolicy,
            )
        }
    }

    private fun resolveTemplateId(request: RequestModel): String {
        return when (request.kind) {
            RequestKind.COMMAND -> "design/command.kt.peb"
            RequestKind.QUERY -> requireNotNull(DesignQueryVariantResolver.resolve(request)).requestTemplateId
            RequestKind.CLIENT -> error("client requests must be filtered before resolving design template")
        }
    }
}

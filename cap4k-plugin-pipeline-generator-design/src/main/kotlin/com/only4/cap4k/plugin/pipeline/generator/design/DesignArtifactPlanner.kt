package com.only4.cap4k.plugin.pipeline.generator.design

import com.only4.cap4k.plugin.pipeline.api.ArtifactPlanItem
import com.only4.cap4k.plugin.pipeline.api.CanonicalModel
import com.only4.cap4k.plugin.pipeline.api.GeneratorProvider
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig
import com.only4.cap4k.plugin.pipeline.api.RequestKind
import java.nio.file.InvalidPathException
import java.nio.file.Path

class DesignArtifactPlanner : GeneratorProvider {
    override val id: String = "design"

    override fun plan(config: ProjectConfig, model: CanonicalModel): List<ArtifactPlanItem> {
        val applicationRoot = requireApplicationModuleRoot(config)
        val basePath = config.basePackage.replace(".", "/")

        return model.requests.map { request ->
            val packagePath = request.packageName.replace(".", "/")
            val subdir = if (request.kind == RequestKind.COMMAND) "commands" else "queries"
            val templateId = if (request.kind == RequestKind.COMMAND) {
                "design/command.kt.peb"
            } else {
                "design/query.kt.peb"
            }

            ArtifactPlanItem(
                generatorId = id,
                moduleRole = "application",
                templateId = templateId,
                outputPath = "$applicationRoot/src/main/kotlin/$basePath/application/$subdir/$packagePath/${request.typeName}.kt",
                context = DesignRenderModelFactory.create(
                    packageName = "${config.basePackage}.application.$subdir.${request.packageName}",
                    request = request,
                ).toContextMap(),
                conflictPolicy = config.templates.conflictPolicy,
            )
        }
    }

    private fun requireApplicationModuleRoot(config: ProjectConfig): String {
        val applicationRoot = config.modules["application"] ?: error("application module is required")
        if (applicationRoot.isBlank()) {
            throw IllegalArgumentException(
                "application module must be a valid relative filesystem path: $applicationRoot",
            )
        }
        if (applicationRoot.startsWith(":")) {
            throw IllegalArgumentException(
                "application module must be a valid relative filesystem path: $applicationRoot",
            )
        }

        val path = try {
            Path.of(applicationRoot)
        } catch (ex: InvalidPathException) {
            throw IllegalArgumentException(
                "application module must be a valid relative filesystem path: $applicationRoot",
                ex,
            )
        }

        if (path.isAbsolute) {
            throw IllegalArgumentException(
                "application module must be a valid relative filesystem path: $applicationRoot",
            )
        }
        if (path.root != null) {
            throw IllegalArgumentException(
                "application module must be a valid relative filesystem path: $applicationRoot",
            )
        }
        val normalized = path.normalize()
        if (normalized.nameCount > 0 && normalized.getName(0).toString() == "..") {
            throw IllegalArgumentException(
                "application module must be a valid relative filesystem path: $applicationRoot",
            )
        }

        return applicationRoot
    }
}

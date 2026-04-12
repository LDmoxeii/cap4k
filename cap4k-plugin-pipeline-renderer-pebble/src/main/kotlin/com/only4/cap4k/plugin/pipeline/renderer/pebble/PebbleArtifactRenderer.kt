package com.only4.cap4k.plugin.pipeline.renderer.pebble

import com.only4.cap4k.plugin.pipeline.api.ArtifactPlanItem
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig
import com.only4.cap4k.plugin.pipeline.api.RenderedArtifact
import com.only4.cap4k.plugin.pipeline.renderer.api.ArtifactRenderer
import com.only4.cap4k.plugin.pipeline.renderer.api.TemplateResolver
import io.pebbletemplates.pebble.PebbleEngine
import io.pebbletemplates.pebble.loader.StringLoader
import java.io.StringWriter

class PebbleArtifactRenderer(
    private val templateResolver: TemplateResolver,
) : ArtifactRenderer {

    override fun render(planItems: List<ArtifactPlanItem>, config: ProjectConfig): List<RenderedArtifact> =
        planItems.map { item ->
            val templateText = templateResolver.resolve(item.templateId)

            if (isDesignTemplate(item.templateId)) {
                val collector = ExplicitImportCollector()
                val firstPassEngine = newEngine(collector, enableUseHelper = true)
                firstPassEngine.getLiteralTemplate(templateText).evaluate(StringWriter(), item.context)

                val mergedImports = collector.mergedWith(readImports(item.context))
                val secondPassEngine = newEngine(collector, enableUseHelper = true)
                val writer = StringWriter()
                secondPassEngine.getLiteralTemplate(templateText).evaluate(
                    writer,
                    item.context + mapOf("imports" to mergedImports)
                )
                return@map RenderedArtifact(
                    outputPath = item.outputPath,
                    content = writer.toString(),
                    conflictPolicy = item.conflictPolicy
                )
            }

            val engine = newEngine(ExplicitImportCollector(), enableUseHelper = false)
            val template = engine.getLiteralTemplate(templateText)
            val writer = StringWriter()
            template.evaluate(writer, item.context)
            RenderedArtifact(
                outputPath = item.outputPath,
                content = writer.toString(),
                conflictPolicy = item.conflictPolicy
            )
        }

    private fun newEngine(
        explicitImportCollector: ExplicitImportCollector,
        enableUseHelper: Boolean,
    ): PebbleEngine = PebbleEngine.Builder()
        .loader(StringLoader())
        .extension(PipelinePebbleExtension(explicitImportCollector, enableUseHelper))
        .newLineTrimming(false)
        .build()

    private fun isDesignTemplate(templateId: String): Boolean = templateId.startsWith("design/")

    private fun readImports(context: Map<String, Any?>): List<String> = when (val value = context["imports"]) {
        null -> emptyList()
        is List<*> -> value.map { entry ->
            entry as? String ?: throw IllegalArgumentException(
                "imports() requires String entries in the provided list."
            )
        }
        is Map<*, *> -> {
            val imports = value["imports"] ?: return emptyList()
            readImports(mapOf("imports" to imports))
        }
        else -> throw IllegalArgumentException(
            "imports() requires a List<String> or a map exposing imports."
        )
    }
}

package com.only4.cap4k.plugin.pipeline.renderer.pebble

import com.only4.cap4k.plugin.pipeline.api.BootstrapPlanItem
import com.only4.cap4k.plugin.pipeline.api.RenderedArtifact
import com.only4.cap4k.plugin.pipeline.renderer.api.BootstrapRenderer
import com.only4.cap4k.plugin.pipeline.renderer.api.TemplateResolver
import io.pebbletemplates.pebble.PebbleEngine
import io.pebbletemplates.pebble.loader.StringLoader
import java.io.File
import java.io.StringWriter

class PebbleBootstrapRenderer(
    private val templateResolver: TemplateResolver,
) : BootstrapRenderer {
    private val engine = PebbleEngine.Builder()
        .loader(StringLoader())
        .newLineTrimming(false)
        .build()

    override fun render(planItems: List<BootstrapPlanItem>): List<RenderedArtifact> =
        planItems.map { item ->
            val templateId = item.templateId
            val sourcePath = item.sourcePath
            val content = when {
                !templateId.isNullOrBlank() -> renderTemplate(templateId, item.context)
                !sourcePath.isNullOrBlank() -> File(sourcePath).readText()
                else -> error("BootstrapPlanItem requires templateId or sourcePath.")
            }
            RenderedArtifact(item.outputPath, content, item.conflictPolicy)
        }

    private fun renderTemplate(templateId: String, context: Map<String, Any?>): String {
        val template = engine.getLiteralTemplate(templateResolver.resolve(templateId))
        val writer = StringWriter()
        template.evaluate(writer, context)
        return writer.toString()
    }
}

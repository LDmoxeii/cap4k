package com.only4.cap4k.plugin.pipeline.renderer.pebble

import com.only4.cap4k.plugin.pipeline.api.BootstrapPlanItem
import com.only4.cap4k.plugin.pipeline.api.RenderedArtifact
import com.only4.cap4k.plugin.pipeline.renderer.api.BootstrapRenderer
import com.only4.cap4k.plugin.pipeline.renderer.api.TemplateResolver
import io.pebbletemplates.pebble.PebbleEngine
import io.pebbletemplates.pebble.loader.StringLoader
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
            val templateText = templateResolver.resolve(item.sourcePath ?: item.templateId!!)
            val template = engine.getLiteralTemplate(templateText)
            val writer = StringWriter()
            template.evaluate(writer, item.context)
            RenderedArtifact(item.outputPath, writer.toString(), item.conflictPolicy)
        }
}

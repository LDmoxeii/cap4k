package com.only4.cap4k.plugin.pipeline.renderer.pebble

import com.google.gson.Gson
import com.only4.cap4k.plugin.pipeline.api.ArtifactPlanItem
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig
import com.only4.cap4k.plugin.pipeline.api.RenderedArtifact
import com.only4.cap4k.plugin.pipeline.renderer.api.ArtifactRenderer
import com.only4.cap4k.plugin.pipeline.renderer.api.TemplateResolver
import io.pebbletemplates.pebble.PebbleEngine
import io.pebbletemplates.pebble.extension.AbstractExtension
import io.pebbletemplates.pebble.extension.Filter
import io.pebbletemplates.pebble.loader.StringLoader
import io.pebbletemplates.pebble.template.EvaluationContext
import io.pebbletemplates.pebble.template.PebbleTemplate
import java.io.StringWriter

class PebbleArtifactRenderer(
    private val templateResolver: TemplateResolver,
) : ArtifactRenderer {

    private val engine = PebbleEngine.Builder()
        .loader(StringLoader())
        .extension(PipelinePebbleExtension())
        .build()

    override fun render(planItems: List<ArtifactPlanItem>, config: ProjectConfig): List<RenderedArtifact> =
        planItems.map { item ->
            val templateText = templateResolver.resolve(item.templateId)
            val template = engine.getLiteralTemplate(templateText)
            val writer = StringWriter()
            template.evaluate(writer, item.context)
            RenderedArtifact(
                outputPath = item.outputPath,
                content = writer.toString(),
                conflictPolicy = item.conflictPolicy
            )
        }
}

private class PipelinePebbleExtension : AbstractExtension() {
    override fun getFilters(): Map<String, Filter> = mapOf(
        "json" to JsonFilter()
    )
}

private class JsonFilter(
    private val gson: Gson = Gson(),
) : Filter {
    override fun getArgumentNames(): List<String> = emptyList()

    override fun apply(
        input: Any?,
        args: MutableMap<String, Any>?,
        self: PebbleTemplate?,
        context: EvaluationContext?,
        lineNumber: Int,
    ): Any = gson.toJson(input)
}

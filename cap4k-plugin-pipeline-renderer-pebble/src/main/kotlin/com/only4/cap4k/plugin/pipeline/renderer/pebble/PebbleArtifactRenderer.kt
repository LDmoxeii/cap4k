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
    private val sessionState = ThreadLocal<PebbleRenderSession?>()
    private val renderEngine = newEngine()

    override fun render(planItems: List<ArtifactPlanItem>, config: ProjectConfig): List<RenderedArtifact> =
        planItems.map { item ->
            val templateText = templateResolver.resolve(item.templateId)
            renderArtifact(item, templateText)
        }

    private fun renderArtifact(
        item: ArtifactPlanItem,
        templateText: String,
    ): RenderedArtifact {
        val session = PebbleRenderSession()
        sessionState.set(session)

        try {
            val template = renderEngine.getLiteralTemplate(templateText)

            template.evaluate(StringWriter(), item.context)

            session.phase = RenderPhase.RENDERING
            val mergedImports = session.explicitImportCollector.mergedWith(readImports(item.context))
            val writer = StringWriter()
            template.evaluate(
                writer,
                item.context + mapOf("imports" to mergedImports)
            )
            return RenderedArtifact(
                outputPath = item.outputPath,
                content = writer.toString(),
                conflictPolicy = item.conflictPolicy
            )
        } finally {
            sessionState.remove()
        }
    }

    private fun newEngine(): PebbleEngine = PebbleEngine.Builder()
        .loader(StringLoader())
        .extension(PipelinePebbleExtension({ sessionState.get() }, true))
        .newLineTrimming(false)
        .build()

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

internal class PebbleRenderSession(
    val explicitImportCollector: ExplicitImportCollector = ExplicitImportCollector(),
    var phase: RenderPhase = RenderPhase.COLLECTING,
)

internal enum class RenderPhase {
    COLLECTING,
    RENDERING,
}

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
            val renderContext = mergeContextWithCollectedImports(item.context, session.explicitImportCollector)
            val writer = StringWriter()
            template.evaluate(
                writer,
                renderContext
            )
            return RenderedArtifact(
                outputPath = item.outputPath,
                content = sanitizeRenderedContent(item.outputPath, writer.toString()),
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

    private fun mergeContextWithCollectedImports(
        context: Map<String, Any?>,
        collector: ExplicitImportCollector,
    ): Map<String, Any?> {
        if (collector.explicitImports().isEmpty()) {
            return context
        }

        val importsValue = context["imports"]
        val mergedImports = collector.mergedWith(readImportsValue(importsValue))
        val mergedImportsCarrier: Any = when (importsValue) {
            null, is List<*> -> mergedImports
            is Map<*, *> -> LinkedHashMap(importsValue).apply {
                this["imports"] = mergedImports
            }
            else -> throw IllegalArgumentException(
                "imports() requires a List<String> or a map exposing imports."
            )
        }

        return context + mapOf("imports" to mergedImportsCarrier)
    }

    private fun readImportsValue(value: Any?): List<String> = when (value) {
        null -> emptyList()
        is List<*> -> value.map { entry ->
            entry as? String ?: throw IllegalArgumentException(
                "imports() requires String entries in the provided list."
            )
        }
        is Map<*, *> -> {
            val imports = value["imports"] ?: return emptyList()
            readImportsValue(imports)
        }
        else -> throw IllegalArgumentException(
            "imports() requires a List<String> or a map exposing imports."
        )
    }

    private fun sanitizeRenderedContent(outputPath: String, content: String): String {
        val normalized = normalizeLineEndings(content)
        return if (outputPath.endsWith(".kt")) {
            normalizeKotlinArtifact(normalized)
        } else {
            normalized
        }
    }

    private fun normalizeLineEndings(content: String): String = content
        .replace("\r\n", "\n")
        .replace('\r', '\n')

    private fun normalizeKotlinArtifact(content: String): String = content
        .lineSequence()
        .joinToString("\n") { it.trimEnd() }
        .replace(Regex("\n{3,}"), "\n\n")
        .trimEnd('\n') + "\n"
}

internal class PebbleRenderSession(
    val explicitImportCollector: ExplicitImportCollector = ExplicitImportCollector(),
    var phase: RenderPhase = RenderPhase.COLLECTING,
)

internal enum class RenderPhase {
    COLLECTING,
    RENDERING,
}

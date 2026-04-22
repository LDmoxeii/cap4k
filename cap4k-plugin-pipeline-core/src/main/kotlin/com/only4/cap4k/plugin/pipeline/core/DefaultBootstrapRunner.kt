package com.only4.cap4k.plugin.pipeline.core

import com.only4.cap4k.plugin.pipeline.api.BootstrapConfig
import com.only4.cap4k.plugin.pipeline.api.BootstrapPresetProvider
import com.only4.cap4k.plugin.pipeline.api.BootstrapResult
import com.only4.cap4k.plugin.pipeline.api.BootstrapRunner
import com.only4.cap4k.plugin.pipeline.renderer.api.BootstrapRenderer

class DefaultBootstrapRunner(
    private val providers: List<BootstrapPresetProvider>,
    private val renderer: BootstrapRenderer,
    private val exporter: ArtifactExporter,
    private val preRunValidation: (BootstrapConfig) -> Unit = {},
) : BootstrapRunner {
    override fun run(config: BootstrapConfig): BootstrapResult {
        preRunValidation(config)
        val provider = providers.find { it.presetId == config.preset }
            ?: throw IllegalArgumentException("bootstrap preset has no registered bootstrap provider: ${config.preset}")

        val planItems = provider.plan(config)
        val renderedArtifacts = renderer.render(planItems)
        val writtenPaths = exporter.export(renderedArtifacts)
        return BootstrapResult(planItems, renderedArtifacts, writtenPaths)
    }
}

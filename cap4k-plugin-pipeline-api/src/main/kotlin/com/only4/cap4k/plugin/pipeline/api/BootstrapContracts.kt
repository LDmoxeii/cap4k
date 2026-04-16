package com.only4.cap4k.plugin.pipeline.api

interface BootstrapPresetProvider {
    val presetId: String
    fun plan(config: BootstrapConfig): List<BootstrapPlanItem>
}

interface BootstrapRunner {
    fun run(config: BootstrapConfig): BootstrapResult
}

data class BootstrapResult(
    val planItems: List<BootstrapPlanItem>,
    val renderedArtifacts: List<RenderedArtifact>,
    val writtenPaths: List<String>,
)

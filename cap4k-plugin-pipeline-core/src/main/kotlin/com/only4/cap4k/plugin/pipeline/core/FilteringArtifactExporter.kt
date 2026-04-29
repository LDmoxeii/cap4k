package com.only4.cap4k.plugin.pipeline.core

import com.only4.cap4k.plugin.pipeline.api.RenderedArtifact

class FilteringArtifactExporter(
    private val delegate: ArtifactExporter,
    private val include: (RenderedArtifact) -> Boolean,
) : ArtifactExporter {
    override fun export(artifacts: List<RenderedArtifact>): List<String> =
        delegate.export(artifacts.filter(include))
}

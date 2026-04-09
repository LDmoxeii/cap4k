package com.only4.cap4k.plugin.pipeline.core

import com.only4.cap4k.plugin.pipeline.api.RenderedArtifact

interface ArtifactExporter {
    fun export(artifacts: List<RenderedArtifact>): List<String>
}

class NoopArtifactExporter : ArtifactExporter {
    override fun export(artifacts: List<RenderedArtifact>): List<String> = emptyList()
}

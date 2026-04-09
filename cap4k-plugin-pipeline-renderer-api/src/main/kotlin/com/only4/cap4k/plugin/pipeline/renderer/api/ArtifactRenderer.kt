package com.only4.cap4k.plugin.pipeline.renderer.api

import com.only4.cap4k.plugin.pipeline.api.ArtifactPlanItem
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig
import com.only4.cap4k.plugin.pipeline.api.RenderedArtifact

interface ArtifactRenderer {
    fun render(planItems: List<ArtifactPlanItem>, config: ProjectConfig): List<RenderedArtifact>
}

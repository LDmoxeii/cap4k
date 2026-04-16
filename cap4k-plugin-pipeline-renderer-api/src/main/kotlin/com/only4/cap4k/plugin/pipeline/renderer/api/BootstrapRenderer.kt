package com.only4.cap4k.plugin.pipeline.renderer.api

import com.only4.cap4k.plugin.pipeline.api.BootstrapPlanItem
import com.only4.cap4k.plugin.pipeline.api.RenderedArtifact

interface BootstrapRenderer {
    fun render(planItems: List<BootstrapPlanItem>): List<RenderedArtifact>
}

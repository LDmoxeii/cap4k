package com.only4.cap4k.plugin.pipeline.core

import com.only4.cap4k.plugin.pipeline.api.BootstrapConfig
import com.only4.cap4k.plugin.pipeline.api.BootstrapModulesConfig
import com.only4.cap4k.plugin.pipeline.api.BootstrapMode
import com.only4.cap4k.plugin.pipeline.api.BootstrapPlanItem
import com.only4.cap4k.plugin.pipeline.api.BootstrapTemplateConfig
import com.only4.cap4k.plugin.pipeline.api.ConflictPolicy
import com.only4.cap4k.plugin.pipeline.api.RenderedArtifact
import com.only4.cap4k.plugin.pipeline.renderer.api.BootstrapRenderer
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DefaultBootstrapRunnerTest {

    @Test
    fun `run fails when preset has no registered provider`() {
        val config = BootstrapConfig(
            preset = "ddd-multi-module",
            projectName = "demo",
            basePackage = "com.acme.demo",
            modules = BootstrapModulesConfig("demo-domain", "demo-application", "demo-adapter"),
            templates = BootstrapTemplateConfig("ddd-default-bootstrap", emptyList()),
            slots = emptyList(),
            conflictPolicy = ConflictPolicy.FAIL,
            mode = BootstrapMode.IN_PLACE,
            previewDir = null,
        )

        val runner = DefaultBootstrapRunner(
            providers = emptyList(),
            renderer = object : BootstrapRenderer {
                override fun render(planItems: List<BootstrapPlanItem>): List<RenderedArtifact> = emptyList()
            },
            exporter = NoopArtifactExporter(),
        )

        val error = assertThrows(IllegalArgumentException::class.java) {
            runner.run(config)
        }

        assertTrue(error.message!!.contains("no registered bootstrap provider"))
    }
}

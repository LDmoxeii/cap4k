package com.only4.cap4k.plugin.pipeline.core

import com.only4.cap4k.plugin.pipeline.api.BootstrapConfig
import com.only4.cap4k.plugin.pipeline.api.BootstrapModulesConfig
import com.only4.cap4k.plugin.pipeline.api.BootstrapMode
import com.only4.cap4k.plugin.pipeline.api.BootstrapPlanItem
import com.only4.cap4k.plugin.pipeline.api.BootstrapPresetProvider
import com.only4.cap4k.plugin.pipeline.api.BootstrapTemplateConfig
import com.only4.cap4k.plugin.pipeline.api.ConflictPolicy
import com.only4.cap4k.plugin.pipeline.api.RenderedArtifact
import com.only4.cap4k.plugin.pipeline.renderer.api.BootstrapRenderer
import org.junit.jupiter.api.Assertions.assertEquals
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
            modules = BootstrapModulesConfig(
                "demo-domain",
                "demo-application",
                "demo-adapter",
                "demo-start",
            ),
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

    @Test
    fun `run invokes pre run validation before planning`() {
        val config = bootstrapConfig()
        val events = mutableListOf<String>()

        val runner = DefaultBootstrapRunner(
            providers = listOf(provider(events)),
            renderer = object : BootstrapRenderer {
                override fun render(planItems: List<BootstrapPlanItem>): List<RenderedArtifact> {
                    events += "render"
                    return emptyList()
                }
            },
            exporter = object : ArtifactExporter {
                override fun export(artifacts: List<RenderedArtifact>): List<String> {
                    events += "export"
                    return emptyList()
                }
            },
            preRunValidation = {
                events += "validate"
            },
        )

        runner.run(config)

        assertEquals(listOf("validate", "plan", "render", "export"), events)
    }

    @Test
    fun `run stops when pre run validation fails`() {
        val config = bootstrapConfig()
        var providerCalled = false

        val runner = DefaultBootstrapRunner(
            providers = listOf(
                object : BootstrapPresetProvider {
                    override val presetId: String = "ddd-multi-module"

                    override fun plan(config: BootstrapConfig): List<BootstrapPlanItem> {
                        providerCalled = true
                        return emptyList()
                    }
                }
            ),
            renderer = object : BootstrapRenderer {
                override fun render(planItems: List<BootstrapPlanItem>): List<RenderedArtifact> = emptyList()
            },
            exporter = NoopArtifactExporter(),
            preRunValidation = {
                throw IllegalArgumentException("invalid root state")
            },
        )

        val error = assertThrows(IllegalArgumentException::class.java) {
            runner.run(config)
        }

        assertTrue(error.message!!.contains("invalid root state"))
        assertTrue(!providerCalled)
    }

    private fun bootstrapConfig(): BootstrapConfig =
        BootstrapConfig(
            preset = "ddd-multi-module",
            projectName = "demo",
            basePackage = "com.acme.demo",
            modules = BootstrapModulesConfig(
                "demo-domain",
                "demo-application",
                "demo-adapter",
                "demo-start",
            ),
            templates = BootstrapTemplateConfig("ddd-default-bootstrap", emptyList()),
            slots = emptyList(),
            conflictPolicy = ConflictPolicy.FAIL,
            mode = BootstrapMode.IN_PLACE,
            previewDir = null,
        )

    private fun provider(events: MutableList<String>): BootstrapPresetProvider =
        object : BootstrapPresetProvider {
            override val presetId: String = "ddd-multi-module"

            override fun plan(config: BootstrapConfig): List<BootstrapPlanItem> {
                events += "plan"
                assertEquals("demo-start", config.modules.startModuleName)
                return listOf(
                    BootstrapPlanItem(
                        presetId = config.preset,
                        templateId = "bootstrap/root/build.gradle.kts.peb",
                        outputPath = "build.gradle.kts",
                        conflictPolicy = config.conflictPolicy,
                    )
                )
            }
        }
}

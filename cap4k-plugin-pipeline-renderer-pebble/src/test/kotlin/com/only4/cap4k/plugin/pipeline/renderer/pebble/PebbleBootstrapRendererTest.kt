package com.only4.cap4k.plugin.pipeline.renderer.pebble

import com.only4.cap4k.plugin.pipeline.api.BootstrapPlanItem
import com.only4.cap4k.plugin.pipeline.api.ConflictPolicy
import java.nio.file.Files
import kotlin.io.path.writeText
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PebbleBootstrapRendererTest {

    @Test
    fun `render resolves fixed preset template ids through bootstrap preset resolver`() {
        val item = BootstrapPlanItem(
            presetId = "ddd-multi-module",
            templateId = "bootstrap/root/settings.gradle.kts.peb",
            outputPath = "only-danmuku/settings.gradle.kts",
            conflictPolicy = ConflictPolicy.FAIL,
            context = mapOf(
                "projectName" to "only-danmuku",
                "domainModuleName" to "only-danmuku-domain",
                "applicationModuleName" to "only-danmuku-application",
                "adapterModuleName" to "only-danmuku-adapter",
            ),
        )

        val renderer = PebbleBootstrapRenderer(
            PresetTemplateResolver("ddd-default-bootstrap", emptyList())
        )

        val artifact = renderer.render(listOf(item)).single()

        assertTrue(artifact.content.contains("include(\":only-danmuku-domain\")"))
        assertTrue(artifact.content.contains("include(\":only-danmuku-application\")"))
        assertTrue(artifact.content.contains("include(\":only-danmuku-adapter\")"))
    }

    @Test
    fun `render supports slot source files through absolute source path`() {
        val tempFile = Files.createTempFile("bootstrap-slot", ".peb")
        tempFile.writeText("module={{ domainModuleName }}")

        val item = BootstrapPlanItem(
            presetId = "ddd-multi-module",
            sourcePath = tempFile.toString(),
            outputPath = "only-danmuku/README.md",
            conflictPolicy = ConflictPolicy.FAIL,
            slotId = "root",
            context = mapOf("domainModuleName" to "only-danmuku-domain"),
        )

        val renderer = PebbleBootstrapRenderer(
            PresetTemplateResolver("ddd-default-bootstrap", emptyList())
        )

        val artifact = renderer.render(listOf(item)).single()
        assertEquals("module=only-danmuku-domain", artifact.content)
    }

    @Test
    fun `render falls back to template id when source path is blank`() {
        val item = BootstrapPlanItem(
            presetId = "ddd-multi-module",
            templateId = "bootstrap/root/settings.gradle.kts.peb",
            sourcePath = "   ",
            outputPath = "only-danmuku/settings.gradle.kts",
            conflictPolicy = ConflictPolicy.FAIL,
            context = mapOf(
                "projectName" to "only-danmuku",
                "domainModuleName" to "only-danmuku-domain",
                "applicationModuleName" to "only-danmuku-application",
                "adapterModuleName" to "only-danmuku-adapter",
            ),
        )

        val renderer = PebbleBootstrapRenderer(
            PresetTemplateResolver("ddd-default-bootstrap", emptyList())
        )

        val artifact = renderer.render(listOf(item)).single()

        assertTrue(artifact.content.contains("include(\":only-danmuku-domain\")"))
        assertTrue(artifact.content.contains("include(\":only-danmuku-application\")"))
        assertTrue(artifact.content.contains("include(\":only-danmuku-adapter\")"))
    }
}

package com.only4.cap4k.plugin.pipeline.renderer.pebble

import java.nio.file.Files
import kotlin.io.path.writeText
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PresetTemplateResolverTest {

    @Test
    fun `resolve prefers absolute direct file before override and resource templates`() {
        val directFile = Files.createTempFile("bootstrap-direct-template", ".peb")
        directFile.writeText("direct={{ projectName }}")

        val overrideDir = Files.createTempDirectory("bootstrap-override")
        val overrideTemplateDir = Files.createDirectories(overrideDir.resolve("bootstrap/root"))
        overrideTemplateDir.resolve("settings.gradle.kts.peb").writeText("override={{ projectName }}")

        val resolver = PresetTemplateResolver(
            preset = "ddd-default-bootstrap",
            overrideDirs = listOf(overrideDir.toString())
        )

        val resolved = resolver.resolve(directFile.toString())

        assertEquals("direct={{ projectName }}", resolved)
    }
}

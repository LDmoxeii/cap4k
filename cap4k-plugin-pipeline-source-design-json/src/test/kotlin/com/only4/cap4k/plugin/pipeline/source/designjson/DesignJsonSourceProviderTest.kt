package com.only4.cap4k.plugin.pipeline.source.designjson

import com.only4.cap4k.plugin.pipeline.api.ConflictPolicy
import com.only4.cap4k.plugin.pipeline.api.DesignSpecSnapshot
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig
import com.only4.cap4k.plugin.pipeline.api.ProjectLayout
import com.only4.cap4k.plugin.pipeline.api.SourceConfig
import com.only4.cap4k.plugin.pipeline.api.TemplateConfig
import java.io.File
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DesignJsonSourceProviderTest {

    @Test
    fun `loads command and query entries from configured files`() {
        val fixture = File("src/test/resources/fixtures/design/design.json").path
        val config = ProjectConfig(
            basePackage = "com.only4.cap4k",
            layout = ProjectLayout.SINGLE_MODULE,
            modules = emptyMap(),
            sources = mapOf(
                "design-json" to SourceConfig(
                    enabled = true,
                    options = mapOf("files" to listOf(fixture)),
                ),
            ),
            generators = emptyMap(),
            templates = TemplateConfig(
                preset = "default",
                overrideDirs = emptyList(),
                conflictPolicy = ConflictPolicy.SKIP,
            ),
        )

        val provider = DesignJsonSourceProvider()
        val snapshot = provider.collect(config) as DesignSpecSnapshot

        assertEquals(2, snapshot.entries.size)
        assertEquals("cmd", snapshot.entries.first().tag)
        assertEquals("FindOrder", snapshot.entries.last().name)
    }
}

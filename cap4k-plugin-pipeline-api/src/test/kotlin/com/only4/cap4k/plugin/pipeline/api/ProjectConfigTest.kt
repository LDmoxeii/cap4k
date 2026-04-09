package com.only4.cap4k.plugin.pipeline.api

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ProjectConfigTest {

    @Test
    fun `enabled ids and template conflict policy are exposed`() {
        val config = ProjectConfig(
            basePackage = "com.only4.example",
            layout = ProjectLayout.MULTI_MODULE,
            modules = mapOf(
                "api" to ":sample-api",
                "core" to ":sample-core",
            ),
            sources = mapOf(
                "design-json" to SourceConfig(enabled = true),
                "ksp-metadata" to SourceConfig(enabled = false),
            ),
            generators = mapOf(
                "design" to GeneratorConfig(enabled = true),
                "pebble" to GeneratorConfig(enabled = false),
            ),
            templates = TemplateConfig(
                preset = "default",
                overrideDirs = listOf("src/main/templates"),
                conflictPolicy = ConflictPolicy.SKIP,
            ),
        )

        assertEquals(setOf("design-json"), config.enabledSourceIds())
        assertEquals(setOf("design"), config.enabledGeneratorIds())
        assertEquals(":sample-api", config.modules["api"])
        assertEquals(ConflictPolicy.SKIP, config.templates.conflictPolicy)
    }
}

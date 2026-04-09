package com.only4.cap4k.plugin.pipeline.source.ksp

import com.only4.cap4k.plugin.pipeline.api.ConflictPolicy
import com.only4.cap4k.plugin.pipeline.api.KspMetadataSnapshot
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig
import com.only4.cap4k.plugin.pipeline.api.ProjectLayout
import com.only4.cap4k.plugin.pipeline.api.SourceConfig
import com.only4.cap4k.plugin.pipeline.api.TemplateConfig
import java.io.File
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class KspMetadataSourceProviderTest {
    @Test
    fun `reads aggregate root metadata from aggregate files`() {
        val fixtureDir = File("src/test/resources/fixtures/metadata").path
        val config = ProjectConfig(
            basePackage = "com.only4.cap4k",
            layout = ProjectLayout.SINGLE_MODULE,
            modules = emptyMap(),
            sources = mapOf(
                "ksp-metadata" to SourceConfig(
                    enabled = true,
                    options = mapOf("inputDir" to fixtureDir),
                ),
            ),
            generators = emptyMap(),
            templates = TemplateConfig(
                preset = "default",
                overrideDirs = emptyList(),
                conflictPolicy = ConflictPolicy.SKIP,
            ),
        )

        val provider = KspMetadataSourceProvider()
        val snapshot = provider.collect(config) as KspMetadataSnapshot

        assertEquals(1, snapshot.aggregates.size)
        assertEquals("Order", snapshot.aggregates.first().aggregateName)
        assertEquals(
            "com.acme.demo.domain.aggregates.order.Order",
            snapshot.aggregates.first().rootQualifiedName,
        )
    }
}

package com.only4.cap4k.plugin.pipeline.bootstrap

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LegacyArchTemplateMappingTest {

    @Test
    fun `legacy sample separates structural nodes from generator routing`() {
        val sample = LegacyArchTemplateMappingSamples.load("legacy/ddd-multi-module-legacy-sample.json")

        val mapping = LegacyArchTemplateMapper.classify(sample)

        assertTrue(mapping.structuralNodes.any { it.contains("{{ artifactId }}-domain") })
        assertTrue(mapping.fixedTemplateFiles.any { it.endsWith("template/settings.gradle.kts.peb") })
        assertTrue(mapping.routingTags.contains("query"))
        assertTrue(mapping.routingTags.contains("query_handler"))
        assertTrue(mapping.routingTags.contains("domain_event"))
    }
}

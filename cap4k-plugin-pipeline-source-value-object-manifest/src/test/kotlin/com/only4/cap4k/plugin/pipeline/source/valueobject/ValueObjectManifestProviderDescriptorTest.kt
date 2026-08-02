package com.only4.cap4k.plugin.pipeline.source.valueobject

import com.only4.cap4k.plugin.pipeline.api.PipelineInputRequirementMatch
import com.only4.cap4k.plugin.pipeline.api.PipelinePublicTasks
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ValueObjectManifestProviderDescriptorTest {
    @Test
    fun `descriptor exposes both source and type registry input paths`() {
        val descriptor = ValueObjectManifestSourceProvider().descriptor

        assertEquals(PipelineInputRequirementMatch.ANY, descriptor.inputRequirements.single().match)
        assertEquals(PipelinePublicTasks.GENERATE_SOURCES, descriptor.tasks.last())
    }
}

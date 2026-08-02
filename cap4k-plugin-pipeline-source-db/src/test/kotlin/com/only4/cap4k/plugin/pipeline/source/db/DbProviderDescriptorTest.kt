package com.only4.cap4k.plugin.pipeline.source.db

import com.only4.cap4k.plugin.pipeline.api.PipelineCapabilityMetadataLevel
import com.only4.cap4k.plugin.pipeline.api.PipelineInputSafety
import com.only4.cap4k.plugin.pipeline.api.PipelinePublicTasks
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DbProviderDescriptorTest {
    @Test
    fun `descriptor exposes live external schema input and source generation tasks`() {
        val descriptor = DbSchemaSourceProvider().descriptor

        assertEquals(PipelineCapabilityMetadataLevel.COMPLETE, descriptor.metadataLevel)
        assertEquals(PipelineInputSafety.LIVE_EXTERNAL, descriptor.inputRequirements.single().safety)
        assertEquals(
            listOf(PipelinePublicTasks.PLAN, PipelinePublicTasks.GENERATE, PipelinePublicTasks.GENERATE_SOURCES),
            descriptor.tasks,
        )
    }
}

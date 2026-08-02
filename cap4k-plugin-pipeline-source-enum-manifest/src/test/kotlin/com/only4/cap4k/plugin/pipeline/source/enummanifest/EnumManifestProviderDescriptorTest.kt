package com.only4.cap4k.plugin.pipeline.source.enummanifest

import com.only4.cap4k.plugin.pipeline.api.PipelineExecutionLane
import com.only4.cap4k.plugin.pipeline.api.PipelinePublicTasks
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class EnumManifestProviderDescriptorTest {
    @Test
    fun `descriptor exposes generated source lane`() {
        val descriptor = EnumManifestSourceProvider().descriptor

        assertEquals(listOf(PipelineExecutionLane.GENERATED_SOURCE), descriptor.executionLanes)
        assertEquals(PipelinePublicTasks.GENERATE_SOURCES, descriptor.tasks.last())
    }
}

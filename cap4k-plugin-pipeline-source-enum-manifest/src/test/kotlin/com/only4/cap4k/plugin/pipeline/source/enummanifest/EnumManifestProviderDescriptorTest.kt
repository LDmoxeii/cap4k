package com.only4.cap4k.plugin.pipeline.source.enummanifest

import com.only4.cap4k.plugin.pipeline.api.PipelineExecutionLane
import com.only4.cap4k.plugin.pipeline.api.PipelinePublicTasks
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class EnumManifestProviderDescriptorTest {
    @Test
    fun `descriptor exposes authoring lane without generated source task`() {
        val descriptor = EnumManifestSourceProvider().descriptor

        assertEquals(listOf(PipelineExecutionLane.AUTHORING), descriptor.executionLanes)
        assertEquals(listOf(PipelinePublicTasks.PLAN, PipelinePublicTasks.GENERATE), descriptor.tasks)
    }
}

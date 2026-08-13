package com.only4.cap4k.plugin.pipeline.source.ir

import com.only4.cap4k.plugin.pipeline.api.PipelineExecutionLane
import com.only4.cap4k.plugin.pipeline.api.PipelinePublicTasks
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class IrAnalysisProviderDescriptorTest {
    @Test
    fun `descriptor exposes analysis-only task family`() {
        val descriptor = IrAnalysisSourceProvider().descriptor

        assertEquals(
            listOf(
                "Raw Analysis Graph Evidence",
                "Normalized Design Projection Evidence",
                "Aggregate Structure Evidence",
            ),
            descriptor.tacticalCarriers,
        )
        assertEquals(listOf(PipelineExecutionLane.ANALYSIS), descriptor.executionLanes)
        assertEquals(listOf(PipelinePublicTasks.ANALYSIS_PLAN, PipelinePublicTasks.ANALYSIS_GENERATE), descriptor.tasks)
    }
}

package com.only4.cap4k.plugin.pipeline.source.designjson

import com.only4.cap4k.plugin.pipeline.api.PipelineInputRequirementMatch
import com.only4.cap4k.plugin.pipeline.api.PipelinePublicTasks
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DesignJsonProviderDescriptorTest {
    @Test
    fun `descriptor exposes supported tactical carriers and alternative local inputs`() {
        val descriptor = DesignJsonSourceProvider().descriptor

        assertTrue(descriptor.tacticalCarriers.containsAll(listOf("Command", "Domain Event", "Scheduled Reaction")))
        assertEquals(PipelineInputRequirementMatch.ANY, descriptor.inputRequirements.single().match)
        assertEquals(listOf(PipelinePublicTasks.PLAN, PipelinePublicTasks.GENERATE), descriptor.tasks)
    }
}

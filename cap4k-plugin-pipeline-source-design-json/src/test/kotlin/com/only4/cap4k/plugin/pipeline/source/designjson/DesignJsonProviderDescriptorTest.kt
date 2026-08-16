package com.only4.cap4k.plugin.pipeline.source.designjson

import com.only4.cap4k.plugin.pipeline.api.PipelineInputRequirementMatch
import com.only4.cap4k.plugin.pipeline.api.PipelinePublicTasks
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class DesignJsonProviderDescriptorTest {
    @Test
    fun `descriptor exposes supported tactical carriers and alternative local inputs`() {
        val descriptor = DesignJsonSourceProvider().descriptor

        assertEquals(
            listOf(
                "Command",
                "Query",
                "Capability",
                "Endpoint",
                "Domain Event",
                "Integration Event",
                "Domain Service",
                "Subscriber",
            ),
            descriptor.tacticalCarriers,
        )
        assertFalse("Scheduled Reaction" in descriptor.tacticalCarriers)
        assertEquals(PipelineInputRequirementMatch.ANY, descriptor.inputRequirements.single().match)
        assertEquals(listOf(PipelinePublicTasks.PLAN, PipelinePublicTasks.GENERATE), descriptor.tasks)
    }
}

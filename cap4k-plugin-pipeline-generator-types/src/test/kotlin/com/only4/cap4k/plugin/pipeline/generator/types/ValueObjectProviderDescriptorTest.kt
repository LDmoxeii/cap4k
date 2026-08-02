package com.only4.cap4k.plugin.pipeline.generator.types

import com.only4.cap4k.plugin.pipeline.api.ArtifactOutputKind
import com.only4.cap4k.plugin.pipeline.api.PipelinePublicTasks
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ValueObjectProviderDescriptorTest {
    @Test
    fun `descriptor exposes checked-in values and generated converters`() {
        val descriptor = ValueObjectArtifactPlanner().descriptor

        assertEquals(
            listOf(ArtifactOutputKind.CHECKED_IN_SOURCE, ArtifactOutputKind.GENERATED_SOURCE),
            descriptor.outputKinds,
        )
        assertEquals(PipelinePublicTasks.GENERATE_SOURCES, descriptor.tasks.last())
    }
}

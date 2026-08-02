package com.only4.cap4k.plugin.pipeline.generator.aggregate

import com.only4.cap4k.plugin.pipeline.api.ArtifactOutputKind
import com.only4.cap4k.plugin.pipeline.api.PipelineCapabilityMetadataLevel
import com.only4.cap4k.plugin.pipeline.api.PipelinePublicTasks
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AggregateProviderDescriptorTest {
    @Test
    fun `aggregate provider owns complete mixed-output capability metadata`() {
        val descriptor = AggregateArtifactPlanner().descriptor

        assertEquals(PipelineCapabilityMetadataLevel.COMPLETE, descriptor.metadataLevel)
        assertEquals(
            listOf(ArtifactOutputKind.CHECKED_IN_SOURCE, ArtifactOutputKind.GENERATED_SOURCE),
            descriptor.outputKinds,
        )
        assertEquals(PipelinePublicTasks.GENERATE_SOURCES, descriptor.tasks.last())
    }

    @Test
    fun `projection and enum providers declare generated source ownership`() {
        assertEquals(listOf(ArtifactOutputKind.GENERATED_SOURCE), AggregateProjectionArtifactPlanner().descriptor.outputKinds)
        assertEquals(listOf(ArtifactOutputKind.GENERATED_SOURCE), EnumManifestArtifactPlanner().descriptor.outputKinds)
    }
}

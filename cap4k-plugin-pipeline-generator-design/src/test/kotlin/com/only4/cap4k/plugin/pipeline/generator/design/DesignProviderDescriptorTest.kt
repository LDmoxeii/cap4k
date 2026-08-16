package com.only4.cap4k.plugin.pipeline.generator.design

import com.only4.cap4k.plugin.pipeline.api.ArtifactOutputKind
import com.only4.cap4k.plugin.pipeline.api.GeneratorProvider
import com.only4.cap4k.plugin.pipeline.api.PipelineBoundaryKind
import com.only4.cap4k.plugin.pipeline.api.PipelineCapabilityMetadataLevel
import com.only4.cap4k.plugin.pipeline.api.PipelinePublicTasks
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DesignProviderDescriptorTest {
    @Test
    fun `all design providers self-describe checked-in authoring capabilities`() {
        val providers: List<GeneratorProvider> = listOf(
            DesignCommandArtifactPlanner(),
            DesignQueryArtifactPlanner(),
            DesignQueryHandlerArtifactPlanner(),
            DesignCapabilityArtifactPlanner(),
            DesignCapabilityHandlerArtifactPlanner(),
            DesignDomainEventArtifactPlanner(),
            DesignDomainEventHandlerArtifactPlanner(),
            DesignDomainServiceArtifactPlanner(),
            DesignIntegrationEventArtifactPlanner(),
            DesignIntegrationEventSubscriberArtifactPlanner(),
        )

        providers.forEach { provider ->
            assertEquals(PipelineCapabilityMetadataLevel.COMPLETE, provider.descriptor.metadataLevel)
            assertEquals(listOf(ArtifactOutputKind.CHECKED_IN_SOURCE), provider.descriptor.outputKinds)
            assertEquals(listOf(PipelinePublicTasks.PLAN, PipelinePublicTasks.GENERATE), provider.descriptor.tasks)
            assertEquals("pipeline.source.design-json", provider.descriptor.inputRequirements.single().capabilityIds.single())
        }
        assertTrue(
            DesignIntegrationEventArtifactPlanner().descriptor.boundaries.any { it.kind == PipelineBoundaryKind.PROVIDER },
        )
    }
}

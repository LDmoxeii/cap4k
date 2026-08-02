package com.only4.cap4k.plugin.pipeline.api

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class PipelineCapabilityDescriptorTest {

    @Test
    fun `built-in descriptor carries provider-owned catalog metadata`() {
        val descriptor = PipelineCapabilityDescriptor.builtIn(
            providerId = "sample",
            displayName = "Sample Generator",
            kind = PipelineCapabilityKind.GENERATOR,
            module = "cap4k-plugin-pipeline-generator-sample",
            tacticalCarriers = listOf("Sample"),
            executionLanes = listOf(PipelineExecutionLane.AUTHORING),
            tasks = listOf(PipelinePublicTasks.PLAN, PipelinePublicTasks.GENERATE),
            inputRequirements = listOf(
                PipelineInputRequirement(
                    id = "sample-input",
                    configurationPaths = listOf("sources.sample.files"),
                ),
            ),
            outputKinds = listOf(ArtifactOutputKind.CHECKED_IN_SOURCE),
            boundaries = listOf(
                PipelineCapabilityBoundary(
                    PipelineBoundaryKind.GENERATION,
                    PipelineBoundaryAuthorities.PIPELINE_GENERATOR,
                ),
            ),
        )

        assertEquals("pipeline.generator.sample", descriptor.capabilityId)
        assertEquals(PipelineCapabilityMetadataLevel.COMPLETE, descriptor.metadataLevel)
        assertEquals(
            PipelineCapabilityProvenance.builtIn("cap4k-plugin-pipeline-generator-sample"),
            descriptor.provenance,
        )
        assertEquals(listOf(PipelinePublicTasks.PLAN, PipelinePublicTasks.GENERATE), descriptor.tasks)
    }

    @Test
    fun `identity-only descriptor preserves compatibility for extension providers`() {
        val descriptor = PipelineCapabilityDescriptor.identityOnly(
            providerId = "sample-addon",
            kind = PipelineCapabilityKind.ARTIFACT_ADDON,
        )

        assertEquals("pipeline.artifact-addon.sample-addon", descriptor.capabilityId)
        assertEquals(PipelineCapabilityMetadataLevel.IDENTITY_ONLY, descriptor.metadataLevel)
        assertEquals(PipelineCapabilityProvenance.unknown(), descriptor.provenance)
    }

    @Test
    fun `descriptor rejects duplicate or blank public tasks`() {
        assertThrows(IllegalArgumentException::class.java) {
            PipelineCapabilityDescriptor.builtIn(
                providerId = "sample",
                displayName = "Sample",
                kind = PipelineCapabilityKind.SOURCE,
                module = "sample-module",
                executionLanes = listOf(PipelineExecutionLane.AUTHORING),
                tasks = listOf(PipelinePublicTasks.PLAN, PipelinePublicTasks.PLAN),
                boundaries = listOf(
                    PipelineCapabilityBoundary(
                        PipelineBoundaryKind.INPUT,
                        PipelineBoundaryAuthorities.PROJECT_INPUT,
                    ),
                ),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            PipelineCapabilityDescriptor.builtIn(
                providerId = "sample",
                displayName = "Sample",
                kind = PipelineCapabilityKind.SOURCE,
                module = "sample-module",
                executionLanes = listOf(PipelineExecutionLane.AUTHORING),
                tasks = listOf(""),
                boundaries = listOf(
                    PipelineCapabilityBoundary(
                        PipelineBoundaryKind.INPUT,
                        PipelineBoundaryAuthorities.PROJECT_INPUT,
                    ),
                ),
            )
        }
    }

    @Test
    fun `ownership contract keeps template identity with path and policy`() {
        val item = AgentOwnershipItem(
            generatorId = "command",
            moduleRole = "application",
            templateId = "design/command.kt.peb",
            outputPath = "demo-application/src/main/kotlin/SubmitOrderCommand.kt",
            outputKind = ArtifactOutputKind.CHECKED_IN_SOURCE,
            conflictPolicy = ConflictPolicy.SKIP,
            resolvedOutputRoot = "demo-application/src/main/kotlin",
        )

        assertEquals("design/command.kt.peb", item.templateId)
    }
}

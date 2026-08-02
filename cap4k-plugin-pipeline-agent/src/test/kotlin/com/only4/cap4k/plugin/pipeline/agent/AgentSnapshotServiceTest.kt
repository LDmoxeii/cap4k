package com.only4.cap4k.plugin.pipeline.agent

import com.only4.cap4k.plugin.pipeline.api.AgentAnalysisSection
import com.only4.cap4k.plugin.pipeline.api.AgentCapabilityObservation
import com.only4.cap4k.plugin.pipeline.api.AgentCapabilityStatus
import com.only4.cap4k.plugin.pipeline.api.AgentDiagnostic
import com.only4.cap4k.plugin.pipeline.api.AgentDiagnosticLevel
import com.only4.cap4k.plugin.pipeline.api.AgentInputsSection
import com.only4.cap4k.plugin.pipeline.api.AgentOwnershipSection
import com.only4.cap4k.plugin.pipeline.api.AgentProjectSection
import com.only4.cap4k.plugin.pipeline.api.AgentProjectSummary
import com.only4.cap4k.plugin.pipeline.api.AgentRuntimeSection
import com.only4.cap4k.plugin.pipeline.api.AgentSnapshotStatus
import com.only4.cap4k.plugin.pipeline.api.AgentValidationStatus
import com.only4.cap4k.plugin.pipeline.api.PipelineCapabilityDescriptor
import com.only4.cap4k.plugin.pipeline.api.PipelineCapabilityKind
import com.only4.cap4k.plugin.pipeline.api.PipelineCapabilityMetadataLevel
import com.only4.cap4k.plugin.pipeline.api.PipelineCapabilityProvenance
import com.only4.cap4k.plugin.pipeline.api.PipelineExecutionLane
import com.only4.cap4k.plugin.pipeline.api.PipelinePublicTasks
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class AgentSnapshotServiceTest {
    private val service = AgentSnapshotService()

    @Test
    fun `supported catalog is projected only from supplied descriptors`() {
        val descriptor = descriptor(
            capabilityId = "pipeline.generator.sample",
            providerId = "sample",
            tasks = listOf(PipelinePublicTasks.PLAN, PipelinePublicTasks.GENERATE),
        )

        val snapshot = service.assemble(
            request(
                capabilityDescriptors = listOf(descriptor),
                observations = listOf(
                    AgentCapabilityObservation(
                        capabilityId = descriptor.capabilityId,
                        providerId = "sample",
                        configured = true,
                        validation = AgentValidationStatus.VERIFIED,
                    )
                ),
            )
        )

        val supported = snapshot.capabilities.supported.single()
        assertEquals(descriptor.capabilityId, supported.capabilityId)
        assertEquals(descriptor.providerId, supported.providerId)
        assertEquals(listOf(PipelinePublicTasks.PLAN, PipelinePublicTasks.GENERATE), supported.tasks)
        assertEquals(AgentCapabilityStatus.READY, snapshot.capabilities.effective.single().status)
        assertEquals(AgentSnapshotStatus.OK, snapshot.capabilities.status)
    }

    @Test
    fun `identity only descriptor and failed validation aggregate section status`() {
        val identityOnly = descriptor(
            capabilityId = "pipeline.source.extension",
            providerId = "extension",
            metadataLevel = PipelineCapabilityMetadataLevel.IDENTITY_ONLY,
        )
        val partial = service.assemble(request(capabilityDescriptors = listOf(identityOnly)))
        assertEquals(AgentSnapshotStatus.PARTIAL, partial.capabilities.status)

        val invalid = service.assemble(
            request(
                capabilityDescriptors = listOf(identityOnly),
                observations = listOf(
                    AgentCapabilityObservation(
                        capabilityId = identityOnly.capabilityId,
                        providerId = "extension",
                        configured = true,
                        validation = AgentValidationStatus.FAILED,
                        diagnosticIds = listOf("source.invalid"),
                    )
                ),
                diagnostics = listOf(
                    AgentDiagnostic(
                        id = "source.invalid",
                        level = AgentDiagnosticLevel.ERROR,
                        stage = "collect",
                        message = "source invalid",
                    )
                ),
            )
        )
        assertEquals(AgentSnapshotStatus.INVALID, invalid.capabilities.status)
        assertEquals(AgentCapabilityStatus.BLOCKED, invalid.capabilities.effective.single().status)
        assertEquals(AgentSnapshotStatus.INVALID, invalid.diagnostics.status)
    }

    @Test
    fun `unknown observations and duplicate capability identities fail fast`() {
        val descriptor = descriptor("pipeline.generator.sample", "sample")
        assertThrows(IllegalArgumentException::class.java) {
            service.assemble(
                request(
                    capabilityDescriptors = listOf(descriptor),
                    observations = listOf(
                        AgentCapabilityObservation(
                            capabilityId = "pipeline.generator.unknown",
                            providerId = "unknown",
                            configured = true,
                        )
                    ),
                )
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            service.assemble(request(capabilityDescriptors = listOf(descriptor, descriptor)))
        }
    }

    @Test
    fun `observations correlate by capability identity when provider ids are shared`() {
        val source = descriptor("pipeline.source.shared", "shared").copy(
            kind = PipelineCapabilityKind.SOURCE,
        )
        val generator = descriptor("pipeline.generator.shared", "shared")

        val snapshot = service.assemble(
            request(
                capabilityDescriptors = listOf(source, generator),
                observations = listOf(
                    AgentCapabilityObservation(
                        capabilityId = source.capabilityId,
                        providerId = source.providerId,
                        configured = true,
                        validation = AgentValidationStatus.VERIFIED,
                    ),
                    AgentCapabilityObservation(
                        capabilityId = generator.capabilityId,
                        providerId = generator.providerId,
                        configured = false,
                        applicable = false,
                    ),
                ),
            )
        )

        assertEquals(
            AgentCapabilityStatus.READY,
            snapshot.capabilities.effective.single { it.capabilityId == source.capabilityId }.status,
        )
        assertEquals(
            AgentCapabilityStatus.NOT_APPLICABLE,
            snapshot.capabilities.effective.single { it.capabilityId == generator.capabilityId }.status,
        )
    }

    private fun request(
        capabilityDescriptors: List<PipelineCapabilityDescriptor>,
        observations: List<AgentCapabilityObservation> = emptyList(),
        diagnostics: List<AgentDiagnostic> = emptyList(),
    ) = AgentSnapshotRequest(
        project = AgentProjectSection(
            status = AgentSnapshotStatus.OK,
            project = AgentProjectSummary(name = "demo"),
        ),
        capabilityDescriptors = capabilityDescriptors,
        capabilityObservations = observations,
        inputs = AgentInputsSection(status = AgentSnapshotStatus.OK, inputs = emptyList()),
        ownership = AgentOwnershipSection(status = AgentSnapshotStatus.OK, items = emptyList()),
        runtime = AgentRuntimeSection(status = AgentSnapshotStatus.OK),
        analysis = AgentAnalysisSection(
            status = AgentSnapshotStatus.UNAVAILABLE,
            configured = false,
            reason = "IR analysis is not configured.",
        ),
        diagnostics = diagnostics,
    )

    private fun descriptor(
        capabilityId: String,
        providerId: String,
        tasks: List<String> = emptyList(),
        metadataLevel: PipelineCapabilityMetadataLevel = PipelineCapabilityMetadataLevel.COMPLETE,
    ) = PipelineCapabilityDescriptor(
        capabilityId = capabilityId,
        providerId = providerId,
        displayName = providerId,
        kind = PipelineCapabilityKind.GENERATOR,
        provenance = PipelineCapabilityProvenance.builtIn("test-module"),
        executionLanes = listOf(PipelineExecutionLane.AUTHORING),
        tasks = tasks,
        metadataLevel = metadataLevel,
    )
}

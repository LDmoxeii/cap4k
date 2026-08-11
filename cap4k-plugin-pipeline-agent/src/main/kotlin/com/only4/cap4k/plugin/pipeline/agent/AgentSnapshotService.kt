package com.only4.cap4k.plugin.pipeline.agent

import com.only4.cap4k.plugin.pipeline.api.AgentCapabilitiesSection
import com.only4.cap4k.plugin.pipeline.api.AgentCapabilityObservation
import com.only4.cap4k.plugin.pipeline.api.AgentCapabilityStatus
import com.only4.cap4k.plugin.pipeline.api.AgentDiagnostic
import com.only4.cap4k.plugin.pipeline.api.AgentDiagnosticLevel
import com.only4.cap4k.plugin.pipeline.api.AgentDiagnosticsSection
import com.only4.cap4k.plugin.pipeline.api.AgentEffectiveCapability
import com.only4.cap4k.plugin.pipeline.api.AgentSnapshotSections
import com.only4.cap4k.plugin.pipeline.api.AgentSnapshotStatus
import com.only4.cap4k.plugin.pipeline.api.AgentSupportedCapability
import com.only4.cap4k.plugin.pipeline.api.AgentValidationStatus
import com.only4.cap4k.plugin.pipeline.api.PipelineCapabilityDescriptor
import com.only4.cap4k.plugin.pipeline.api.PipelineCapabilityMetadataLevel

data class AgentSnapshotRequest(
    val project: com.only4.cap4k.plugin.pipeline.api.AgentProjectSection,
    val capabilityDescriptors: List<PipelineCapabilityDescriptor>,
    val capabilityObservations: List<AgentCapabilityObservation> = emptyList(),
    val inputs: com.only4.cap4k.plugin.pipeline.api.AgentInputsSection,
    val ownership: com.only4.cap4k.plugin.pipeline.api.AgentOwnershipSection,
    val runtime: com.only4.cap4k.plugin.pipeline.api.AgentRuntimeSection,
    val analysis: com.only4.cap4k.plugin.pipeline.api.AgentAnalysisSection,
    val diagnostics: List<AgentDiagnostic> = emptyList(),
)

/**
 * Builds one coherent Agent API snapshot from adapter-supplied project state.
 *
 * Capability metadata is deliberately accepted only as provider descriptors. The
 * service never contains an independent source/generator catalog, so Gradle, CLI,
 * and future MCP adapters can share the same capability truth.
 */
class AgentSnapshotService {
    fun assemble(request: AgentSnapshotRequest): AgentSnapshotSections {
        val descriptors = normalizeDescriptors(request.capabilityDescriptors)
        val observations = normalizeObservations(request.capabilityObservations, descriptors)
        RetiredRuntimeDescriptorPolicy.requireActive(request.runtime.capabilities, request.runtime.providers)
        val runtimeDiagnostics = RuntimeAgentFactsPolicy.diagnostics(request.runtime)
        val runtime = request.runtime.copy(
            status = if (runtimeDiagnostics.isEmpty()) request.runtime.status else AgentSnapshotStatus.INVALID,
            reason = request.runtime.reason ?: runtimeDiagnostics.takeIf { it.isNotEmpty() }
                ?.let { "The static Runtime fact catalog is invalid." },
        )
        val diagnostics = normalizeDiagnostics(request.diagnostics + runtimeDiagnostics)
        val capabilities = AgentCapabilitiesSection(
            status = capabilitySectionStatus(descriptors, observations),
            supported = descriptors.map(::supportedCapability),
            effective = descriptors.map { descriptor ->
                effectiveCapability(descriptor, observations[descriptor.capabilityId])
            },
            reason = capabilitySectionReason(descriptors, observations),
        )
        val diagnosticsSection = AgentDiagnosticsSection(
            status = diagnosticSectionStatus(diagnostics),
            diagnostics = diagnostics,
        )

        return AgentSnapshotSections(
            project = request.project,
            capabilities = capabilities,
            inputs = request.inputs,
            ownership = request.ownership,
            runtime = runtime,
            analysis = request.analysis,
            diagnostics = diagnosticsSection,
        )
    }

    private fun normalizeDescriptors(
        descriptors: List<PipelineCapabilityDescriptor>,
    ): List<PipelineCapabilityDescriptor> {
        RetiredRuntimeDescriptorPolicy.requireActive(descriptors)
        val duplicateCapabilityId = descriptors
            .groupingBy(PipelineCapabilityDescriptor::capabilityId)
            .eachCount()
            .entries
            .firstOrNull { it.value > 1 }
            ?.key
        require(duplicateCapabilityId == null) {
            "duplicate pipeline capability id: $duplicateCapabilityId"
        }
        return descriptors.sortedWith(
            compareBy<PipelineCapabilityDescriptor> { it.capabilityId }
                .thenBy { it.providerId }
        )
    }

    private fun normalizeObservations(
        observations: List<AgentCapabilityObservation>,
        descriptors: List<PipelineCapabilityDescriptor>,
    ): Map<String, AgentCapabilityObservation> {
        val duplicateCapabilityId = observations
            .groupingBy(AgentCapabilityObservation::capabilityId)
            .eachCount()
            .entries
            .firstOrNull { it.value > 1 }
            ?.key
        require(duplicateCapabilityId == null) {
            "duplicate capability observation for capability: $duplicateCapabilityId"
        }

        val descriptorsByCapabilityId = descriptors.associateBy(PipelineCapabilityDescriptor::capabilityId)
        val unknownCapabilityIds = observations
            .map(AgentCapabilityObservation::capabilityId)
            .filterNot(descriptorsByCapabilityId::containsKey)
            .distinct()
            .sorted()
        require(unknownCapabilityIds.isEmpty()) {
            "capability observations reference unknown capabilities: ${unknownCapabilityIds.joinToString()}"
        }

        val providerMismatches = observations.mapNotNull { observation ->
            val expected = descriptorsByCapabilityId[observation.capabilityId]?.providerId ?: return@mapNotNull null
            if (expected == observation.providerId) null else
                "${observation.capabilityId}: expected $expected, observed ${observation.providerId}"
        }
        require(providerMismatches.isEmpty()) {
            "capability observations reference mismatched providers: ${providerMismatches.joinToString()}"
        }

        return observations.associateBy(AgentCapabilityObservation::capabilityId)
    }

    private fun normalizeDiagnostics(diagnostics: List<AgentDiagnostic>): List<AgentDiagnostic> {
        val duplicateId = diagnostics
            .groupingBy(AgentDiagnostic::id)
            .eachCount()
            .entries
            .firstOrNull { it.value > 1 }
            ?.key
        require(duplicateId == null) { "duplicate agent diagnostic id: $duplicateId" }
        return diagnostics.sortedWith(
            compareBy<AgentDiagnostic> { diagnosticLevelOrder(it.level) }
                .thenBy(AgentDiagnostic::id)
        )
    }

    private fun supportedCapability(descriptor: PipelineCapabilityDescriptor) =
        AgentSupportedCapability(
            capabilityId = descriptor.capabilityId,
            providerId = descriptor.providerId,
            displayName = descriptor.displayName,
            kind = descriptor.kind,
            provenance = descriptor.provenance,
            activation = descriptor.activation,
            tacticalCarriers = descriptor.tacticalCarriers.sorted(),
            executionLanes = descriptor.executionLanes.distinct().sortedBy(Enum<*>::name),
            tasks = descriptor.tasks.distinct(),
            inputRequirements = descriptor.inputRequirements.sortedBy { it.id },
            outputKinds = descriptor.outputKinds.distinct().sortedBy(Enum<*>::name),
            boundaries = descriptor.boundaries.sortedWith(
                compareBy({ it.kind.name }, { it.authority })
            ),
            metadataLevel = descriptor.metadataLevel,
        )

    private fun effectiveCapability(
        descriptor: PipelineCapabilityDescriptor,
        observation: AgentCapabilityObservation?,
    ): AgentEffectiveCapability {
        val normalizedObservation = observation ?: AgentCapabilityObservation(
            capabilityId = descriptor.capabilityId,
            providerId = descriptor.providerId,
            configured = false,
            applicable = false,
        )
        val status = when {
            !normalizedObservation.applicable -> AgentCapabilityStatus.NOT_APPLICABLE
            normalizedObservation.validation == AgentValidationStatus.FAILED -> AgentCapabilityStatus.BLOCKED
            normalizedObservation.configured &&
                normalizedObservation.validation == AgentValidationStatus.VERIFIED -> AgentCapabilityStatus.READY
            normalizedObservation.configured -> AgentCapabilityStatus.CONFIGURED
            else -> AgentCapabilityStatus.NOT_APPLICABLE
        }
        return AgentEffectiveCapability(
            capabilityId = descriptor.capabilityId,
            providerId = descriptor.providerId,
            status = status,
            diagnosticIds = normalizedObservation.diagnosticIds.distinct().sorted(),
            nextActions = normalizedObservation.nextActions.distinct().sorted(),
        )
    }

    private fun capabilitySectionStatus(
        descriptors: List<PipelineCapabilityDescriptor>,
        observations: Map<String, AgentCapabilityObservation>,
    ): AgentSnapshotStatus = when {
        descriptors.isEmpty() -> AgentSnapshotStatus.UNAVAILABLE
        observations.values.any { it.validation == AgentValidationStatus.FAILED } -> AgentSnapshotStatus.INVALID
        descriptors.any { it.metadataLevel == PipelineCapabilityMetadataLevel.IDENTITY_ONLY } -> AgentSnapshotStatus.PARTIAL
        else -> AgentSnapshotStatus.OK
    }

    private fun capabilitySectionReason(
        descriptors: List<PipelineCapabilityDescriptor>,
        observations: Map<String, AgentCapabilityObservation>,
    ): String? = when {
        descriptors.isEmpty() -> "No pipeline capability providers were available."
        observations.values.any { it.validation == AgentValidationStatus.FAILED } ->
            "One or more configured capabilities failed validation."
        descriptors.any { it.metadataLevel == PipelineCapabilityMetadataLevel.IDENTITY_ONLY } ->
            "One or more extension capabilities expose identity-only metadata."
        else -> null
    }

    private fun diagnosticSectionStatus(diagnostics: List<AgentDiagnostic>): AgentSnapshotStatus = when {
        diagnostics.any { it.level == AgentDiagnosticLevel.ERROR } -> AgentSnapshotStatus.INVALID
        diagnostics.any { it.level == AgentDiagnosticLevel.WARNING } -> AgentSnapshotStatus.PARTIAL
        else -> AgentSnapshotStatus.OK
    }

    private fun diagnosticLevelOrder(level: AgentDiagnosticLevel): Int = when (level) {
        AgentDiagnosticLevel.ERROR -> 0
        AgentDiagnosticLevel.WARNING -> 1
        AgentDiagnosticLevel.INFO -> 2
    }
}

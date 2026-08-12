package com.only4.cap4k.plugin.pipeline.agent

import com.only4.cap4k.plugin.pipeline.api.AgentSupportedCapability
import com.only4.cap4k.plugin.pipeline.api.PipelineCapabilityDescriptor

internal object PipelineCapabilityFactProjection {
    fun supported(descriptor: PipelineCapabilityDescriptor) = AgentSupportedCapability(
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
        boundaries = descriptor.boundaries.sortedWith(compareBy({ it.kind.name }, { it.authority })),
        metadataLevel = descriptor.metadataLevel,
    )
}
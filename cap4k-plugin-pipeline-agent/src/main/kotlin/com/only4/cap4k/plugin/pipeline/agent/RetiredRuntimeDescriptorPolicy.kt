package com.only4.cap4k.plugin.pipeline.agent

import com.only4.cap4k.plugin.pipeline.api.PipelineCapabilityDescriptor

internal object RetiredRuntimeDescriptorPolicy {
    private val retiredIdentities = sortedSetOf(
        "console",
        "locker",
        "saga",
        "snowflake",
    )

    fun requireActive(descriptors: List<PipelineCapabilityDescriptor>) {
        val violations = descriptors.mapNotNull(::retiredViolation).sorted()
        require(violations.isEmpty()) {
            "retired runtime capability descriptors are forbidden: ${violations.joinToString()}"
        }
    }

    private fun retiredViolation(descriptor: PipelineCapabilityDescriptor): String? {
        val capabilityMatches = descriptor.capabilityId
            .trim()
            .lowercase()
            .split('.')
            .filter(retiredIdentities::contains)
        val normalizedProviderId = descriptor.providerId.trim().lowercase()
        val providerMatches = listOfNotNull(normalizedProviderId.takeIf(retiredIdentities::contains))
        val matches = (capabilityMatches + providerMatches).distinct().sorted()
        if (matches.isEmpty()) return null

        return "${descriptor.capabilityId} (provider ${descriptor.providerId}; retired ${matches.joinToString("/")})"
    }
}
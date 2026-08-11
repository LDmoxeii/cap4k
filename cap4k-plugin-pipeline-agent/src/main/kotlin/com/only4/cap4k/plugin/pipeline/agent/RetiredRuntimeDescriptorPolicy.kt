package com.only4.cap4k.plugin.pipeline.agent

import com.only4.cap4k.plugin.pipeline.api.AgentRuntimeCapabilityFact
import com.only4.cap4k.plugin.pipeline.api.AgentRuntimeProviderFact
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

    fun requireActive(
        capabilities: List<AgentRuntimeCapabilityFact>,
        providers: List<AgentRuntimeProviderFact>,
    ) {
        val violations = buildList {
            capabilities.mapNotNullTo(this) { fact ->
                retiredCapabilityViolation(fact.capabilityId)
            }
            providers.mapNotNullTo(this) { fact ->
                retiredProviderViolation(fact.providerId)
            }
        }.sorted()
        require(violations.isEmpty()) {
            "retired runtime capability descriptors are forbidden: ${violations.joinToString()}"
        }
    }

    private fun retiredCapabilityViolation(identity: String): String? {
        val matches = identity.trim().lowercase().split('.')
            .filter(retiredIdentities::contains)
            .distinct()
            .sorted()
        if (matches.isEmpty()) return null
        return "$identity (capability; retired ${matches.joinToString("/")})"
    }

    private fun retiredProviderViolation(identity: String): String? {
        val normalized = identity.trim().lowercase()
        if (normalized !in retiredIdentities) return null
        return "$identity (provider; retired $normalized)"
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
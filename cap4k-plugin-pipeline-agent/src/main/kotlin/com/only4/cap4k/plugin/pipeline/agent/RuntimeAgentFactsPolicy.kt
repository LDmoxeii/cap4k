package com.only4.cap4k.plugin.pipeline.agent

import com.only4.cap4k.plugin.pipeline.api.AgentDiagnostic
import com.only4.cap4k.plugin.pipeline.api.AgentDiagnosticLevel
import com.only4.cap4k.plugin.pipeline.api.AgentRuntimeSection

internal object RuntimeAgentFactsPolicy {
    fun diagnostics(section: AgentRuntimeSection): List<AgentDiagnostic> = buildList {
        duplicateIdentities(
            kind = "capability",
            identities = section.capabilities.map { fact -> fact.capabilityId },
        ).forEach(::add)
        duplicateIdentities(
            kind = "provider",
            identities = section.providers.map { fact -> fact.providerId },
        ).forEach(::add)
        section.capabilities.forEach { capability ->
            duplicateIdentities(
                kind = "provider reference",
                identities = capability.providerIds,
                scope = normalize(capability.capabilityId),
            ).forEach(::add)
        }

        val capabilityIds = section.capabilities.map { fact -> normalize(fact.capabilityId) }.toSet()
        val providerCandidatesById = section.providers.groupBy { fact -> normalize(fact.providerId) }
        section.providers
            .filter { fact -> normalize(fact.capabilityId) !in capabilityIds }
            .map { fact -> "provider-capability:${normalize(fact.providerId)}" }
            .distinct()
            .sorted()
            .forEach { key ->
                add(error(key, "A Runtime provider fact references an unknown capability identity."))
            }
        section.capabilities.flatMap { capability ->
            capability.providerIds.map { providerId -> capability to providerId }
        }.filter { (capability, providerId) ->
            val providerCandidates = providerCandidatesById[normalize(providerId)].orEmpty()
            providerCandidates.isEmpty() || providerCandidates.any { provider ->
                normalize(provider.capabilityId) != normalize(capability.capabilityId)
            }
        }.map { (capability, providerId) ->
            "capability-provider:${normalize(capability.capabilityId)}:${normalize(providerId)}"
        }.distinct().sorted().forEach { key ->
            add(error(key, "A Runtime capability fact references an absent or mismatched provider identity."))
        }
    }.sortedBy(AgentDiagnostic::id)

    private fun duplicateIdentities(
        kind: String,
        identities: List<String>,
        scope: String = "",
    ): List<AgentDiagnostic> = identities
        .groupingBy(::normalize)
        .eachCount()
        .filterValues { count -> count > 1 }
        .keys
        .sorted()
        .map { identity ->
            error(
                key = "duplicate-$kind:$scope:$identity",
                message = "Multiple Runtime $kind facts declared one normalized identity.",
            )
        }

    private fun error(key: String, message: String) = AgentDiagnostic(
        id = "runtime-facts-${AgentHashing.sha256(key).take(12)}",
        level = AgentDiagnosticLevel.ERROR,
        stage = "runtime-facts",
        message = message,
        hint = "Fix the built-in Runtime fact catalog before publishing the Agent API snapshot.",
        proves = "The static Runtime fact catalog is internally inconsistent.",
    )

    private fun normalize(identity: String): String = identity.trim().lowercase()
}
package com.only4.cap4k.plugin.pipeline.agent

import com.only4.cap4k.plugin.pipeline.api.AgentEvidenceFreshness
import com.only4.cap4k.plugin.pipeline.api.CAP4K_PLAN_EVIDENCE_SCHEMA
import com.only4.cap4k.plugin.pipeline.api.PlanEvidence

data class AgentFreshnessResult(
    val freshness: AgentEvidenceFreshness,
    val reason: String,
)

object AgentFreshnessEvaluator {
    fun evaluate(
        evidence: PlanEvidence?,
        currentConfigurationIdentity: String?,
        currentLocalInputIdentity: String?,
        containsLiveExternalInput: Boolean,
    ): AgentFreshnessResult {
        if (evidence == null) {
            return AgentFreshnessResult(
                AgentEvidenceFreshness.MISSING,
                "No plan evidence exists; run the relevant plan task explicitly.",
            )
        }
        if (evidence.schema != CAP4K_PLAN_EVIDENCE_SCHEMA) {
            return AgentFreshnessResult(
                AgentEvidenceFreshness.UNKNOWN,
                "Plan evidence uses an unsupported schema; run the relevant plan task explicitly.",
            )
        }
        if (currentConfigurationIdentity.isNullOrBlank()) {
            return AgentFreshnessResult(
                AgentEvidenceFreshness.UNKNOWN,
                "Current project configuration identity is unavailable.",
            )
        }
        if (evidence.configurationIdentity != currentConfigurationIdentity) {
            return AgentFreshnessResult(
                AgentEvidenceFreshness.STALE,
                "Plan evidence was produced for different project configuration.",
            )
        }
        if (evidence.localInputIdentity != currentLocalInputIdentity) {
            return AgentFreshnessResult(
                AgentEvidenceFreshness.STALE,
                "Plan evidence was produced for different local project inputs.",
            )
        }
        if (containsLiveExternalInput || evidence.containsLiveExternalInput) {
            return AgentFreshnessResult(
                AgentEvidenceFreshness.UNKNOWN,
                "Configuration identity cannot prove that a live external source is still current; run the relevant plan task explicitly.",
            )
        }
        return AgentFreshnessResult(
            AgentEvidenceFreshness.FRESH,
            "Plan evidence matches current project configuration and local input identities.",
        )
    }
}

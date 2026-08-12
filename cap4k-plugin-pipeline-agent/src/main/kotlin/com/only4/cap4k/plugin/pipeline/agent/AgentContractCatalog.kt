package com.only4.cap4k.plugin.pipeline.agent

import com.only4.cap4k.plugin.pipeline.api.CAP4K_AGENT_ANALYSIS_SCHEMA
import com.only4.cap4k.plugin.pipeline.api.CAP4K_AGENT_CAPABILITIES_SCHEMA
import com.only4.cap4k.plugin.pipeline.api.CAP4K_AGENT_DIAGNOSTICS_SCHEMA
import com.only4.cap4k.plugin.pipeline.api.CAP4K_AGENT_INPUTS_SCHEMA
import com.only4.cap4k.plugin.pipeline.api.CAP4K_AGENT_OWNERSHIP_SCHEMA
import com.only4.cap4k.plugin.pipeline.api.CAP4K_AGENT_PROJECT_SCHEMA
import com.only4.cap4k.plugin.pipeline.api.CAP4K_AGENT_RUNTIME_SCHEMA

data class AgentSectionContract(
    val id: String,
    val path: String,
    val schema: String,
)

object AgentContractCatalog {
    val PROJECT = AgentSectionContract("project", "project.json", CAP4K_AGENT_PROJECT_SCHEMA)
    val CAPABILITIES = AgentSectionContract("capabilities", "capabilities.json", CAP4K_AGENT_CAPABILITIES_SCHEMA)
    val INPUTS = AgentSectionContract("inputs", "inputs.json", CAP4K_AGENT_INPUTS_SCHEMA)
    val OWNERSHIP = AgentSectionContract("ownership", "ownership.json", CAP4K_AGENT_OWNERSHIP_SCHEMA)
    val RUNTIME = AgentSectionContract("runtime", "runtime.json", CAP4K_AGENT_RUNTIME_SCHEMA)
    val ANALYSIS = AgentSectionContract("analysis", "analysis.json", CAP4K_AGENT_ANALYSIS_SCHEMA)
    val DIAGNOSTICS = AgentSectionContract("diagnostics", "diagnostics.json", CAP4K_AGENT_DIAGNOSTICS_SCHEMA)

    val sections: List<AgentSectionContract> = listOf(
        PROJECT,
        CAPABILITIES,
        INPUTS,
        OWNERSHIP,
        RUNTIME,
        ANALYSIS,
        DIAGNOSTICS,
    )
}
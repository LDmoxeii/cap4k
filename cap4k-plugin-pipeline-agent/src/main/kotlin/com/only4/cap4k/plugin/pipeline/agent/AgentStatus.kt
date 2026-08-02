package com.only4.cap4k.plugin.pipeline.agent

import com.only4.cap4k.plugin.pipeline.api.AgentSnapshotSections
import com.only4.cap4k.plugin.pipeline.api.AgentSnapshotStatus

object AgentSnapshotStatusAggregator {
    fun aggregate(sections: AgentSnapshotSections): AgentSnapshotStatus {
        val statuses = listOf(
            sections.project.status,
            sections.capabilities.status,
            sections.inputs.status,
            sections.ownership.status,
            sections.runtime.status,
            sections.analysis.status,
            sections.diagnostics.status,
        )
        return when {
            AgentSnapshotStatus.INVALID in statuses -> AgentSnapshotStatus.INVALID
            sections.project.status == AgentSnapshotStatus.UNAVAILABLE -> AgentSnapshotStatus.UNAVAILABLE
            statuses.all { it == AgentSnapshotStatus.UNAVAILABLE } -> AgentSnapshotStatus.UNAVAILABLE
            statuses.any { it == AgentSnapshotStatus.PARTIAL || it == AgentSnapshotStatus.UNAVAILABLE } ->
                AgentSnapshotStatus.PARTIAL
            else -> AgentSnapshotStatus.OK
        }
    }
}

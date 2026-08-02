package com.only4.cap4k.plugin.pipeline.agent

import com.google.gson.GsonBuilder
import com.only4.cap4k.plugin.pipeline.api.AgentAnalysisSection
import com.only4.cap4k.plugin.pipeline.api.AgentCapabilitiesSection
import com.only4.cap4k.plugin.pipeline.api.AgentDiagnosticsSection
import com.only4.cap4k.plugin.pipeline.api.AgentEvidenceFreshness
import com.only4.cap4k.plugin.pipeline.api.AgentInputsSection
import com.only4.cap4k.plugin.pipeline.api.AgentOwnershipSection
import com.only4.cap4k.plugin.pipeline.api.AgentProjectSection
import com.only4.cap4k.plugin.pipeline.api.AgentProjectSummary
import com.only4.cap4k.plugin.pipeline.api.AgentRuntimeSection
import com.only4.cap4k.plugin.pipeline.api.AgentSnapshotSections
import com.only4.cap4k.plugin.pipeline.api.AgentSnapshotStatus
import com.only4.cap4k.plugin.pipeline.api.PlanEvidence
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AgentFreshnessStatusTest {
    @Test
    fun `freshness requires matching local evidence and never guesses live external state`() {
        val evidence = PlanEvidence(
            configurationIdentity = "config-a",
            localInputIdentity = "input-a",
        )

        assertEquals(
            AgentEvidenceFreshness.MISSING,
            AgentFreshnessEvaluator.evaluate(null, "config-a", "input-a", false).freshness,
        )
        assertEquals(
            AgentEvidenceFreshness.UNKNOWN,
            AgentFreshnessEvaluator.evaluate(evidence, null, "input-a", false).freshness,
        )
        assertEquals(
            AgentEvidenceFreshness.STALE,
            AgentFreshnessEvaluator.evaluate(evidence, "config-b", "input-a", false).freshness,
        )
        assertEquals(
            AgentEvidenceFreshness.STALE,
            AgentFreshnessEvaluator.evaluate(evidence, "config-a", "input-b", false).freshness,
        )
        assertEquals(
            AgentEvidenceFreshness.UNKNOWN,
            AgentFreshnessEvaluator.evaluate(evidence, "config-a", "input-a", true).freshness,
        )
        assertEquals(
            AgentEvidenceFreshness.FRESH,
            AgentFreshnessEvaluator.evaluate(evidence, "config-a", "input-a", false).freshness,
        )
        val unsupportedSchemaEvidence = GsonBuilder().create().fromJson(
            """{"schema":"cap4k.plan-evidence.v999","configurationIdentity":"config-a","localInputIdentity":"input-a","containsLiveExternalInput":false}""",
            PlanEvidence::class.java,
        )
        assertEquals(
            AgentEvidenceFreshness.UNKNOWN,
            AgentFreshnessEvaluator.evaluate(
                unsupportedSchemaEvidence,
                "config-a",
                "input-a",
                false,
            ).freshness,
        )
    }

    @Test
    fun `snapshot status distinguishes invalid mandatory and optional unavailable sections`() {
        assertEquals(
            AgentSnapshotStatus.OK,
            AgentSnapshotStatusAggregator.aggregate(sections()),
        )
        assertEquals(
            AgentSnapshotStatus.PARTIAL,
            AgentSnapshotStatusAggregator.aggregate(
                sections(analysisStatus = AgentSnapshotStatus.UNAVAILABLE)
            ),
        )
        assertEquals(
            AgentSnapshotStatus.INVALID,
            AgentSnapshotStatusAggregator.aggregate(
                sections(diagnosticsStatus = AgentSnapshotStatus.INVALID)
            ),
        )
        assertEquals(
            AgentSnapshotStatus.UNAVAILABLE,
            AgentSnapshotStatusAggregator.aggregate(
                sections(projectStatus = AgentSnapshotStatus.UNAVAILABLE)
            ),
        )
        assertEquals(
            AgentSnapshotStatus.INVALID,
            AgentSnapshotStatusAggregator.aggregate(
                sections(
                    projectStatus = AgentSnapshotStatus.UNAVAILABLE,
                    diagnosticsStatus = AgentSnapshotStatus.INVALID,
                )
            ),
        )
    }

    private fun sections(
        projectStatus: AgentSnapshotStatus = AgentSnapshotStatus.OK,
        analysisStatus: AgentSnapshotStatus = AgentSnapshotStatus.OK,
        diagnosticsStatus: AgentSnapshotStatus = AgentSnapshotStatus.OK,
    ) = AgentSnapshotSections(
        project = AgentProjectSection(
            status = projectStatus,
            project = AgentProjectSummary(name = "demo"),
        ),
        capabilities = AgentCapabilitiesSection(
            status = AgentSnapshotStatus.OK,
            supported = emptyList(),
            effective = emptyList(),
        ),
        inputs = AgentInputsSection(status = AgentSnapshotStatus.OK, inputs = emptyList()),
        ownership = AgentOwnershipSection(status = AgentSnapshotStatus.OK, items = emptyList()),
        runtime = AgentRuntimeSection(status = AgentSnapshotStatus.OK),
        analysis = AgentAnalysisSection(status = analysisStatus, configured = false),
        diagnostics = AgentDiagnosticsSection(status = diagnosticsStatus, diagnostics = emptyList()),
    )
}

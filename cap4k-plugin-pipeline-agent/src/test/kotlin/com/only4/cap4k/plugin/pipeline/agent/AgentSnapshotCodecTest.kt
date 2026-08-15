package com.only4.cap4k.plugin.pipeline.agent

import com.only4.cap4k.plugin.pipeline.api.AgentAnalysisPartition
import com.only4.cap4k.plugin.pipeline.api.AgentAnalysisPartitionIds
import com.only4.cap4k.plugin.pipeline.api.AgentAnalysisSection
import com.only4.cap4k.plugin.pipeline.api.AgentAnalysisSource
import com.only4.cap4k.plugin.pipeline.api.AgentCapabilitiesSection
import com.only4.cap4k.plugin.pipeline.api.AgentCapabilityStatus
import com.only4.cap4k.plugin.pipeline.api.AgentDiagnostic
import com.only4.cap4k.plugin.pipeline.api.AgentDiagnosticLevel
import com.only4.cap4k.plugin.pipeline.api.AgentDiagnosticsSection
import com.only4.cap4k.plugin.pipeline.api.AgentEffectiveCapability
import com.only4.cap4k.plugin.pipeline.api.AgentEventHandlerAuthoring
import com.only4.cap4k.plugin.pipeline.api.AgentEventHandlerEqualOrder
import com.only4.cap4k.plugin.pipeline.api.AgentEventHandlerExecution
import com.only4.cap4k.plugin.pipeline.api.AgentEventHandlerManagedAsyncCompletion
import com.only4.cap4k.plugin.pipeline.api.AgentEventHandlerManagedAsyncFailure
import com.only4.cap4k.plugin.pipeline.api.AgentEventHandlerReturnType
import com.only4.cap4k.plugin.pipeline.api.AgentEvidence
import com.only4.cap4k.plugin.pipeline.api.AgentEvidenceFreshness
import com.only4.cap4k.plugin.pipeline.api.AgentInput
import com.only4.cap4k.plugin.pipeline.api.AgentInputsSection
import com.only4.cap4k.plugin.pipeline.api.AgentOptionSummary
import com.only4.cap4k.plugin.pipeline.api.AgentOwnershipItem
import com.only4.cap4k.plugin.pipeline.api.AgentOwnershipSection
import com.only4.cap4k.plugin.pipeline.api.AgentProjectModule
import com.only4.cap4k.plugin.pipeline.api.AgentProjectSection
import com.only4.cap4k.plugin.pipeline.api.AgentProjectSummary
import com.only4.cap4k.plugin.pipeline.api.AgentRuntimeExtension
import com.only4.cap4k.plugin.pipeline.api.AgentRuntimeSection
import com.only4.cap4k.plugin.pipeline.api.AgentSnapshotSections
import com.only4.cap4k.plugin.pipeline.api.AgentSnapshotStatus
import com.only4.cap4k.plugin.pipeline.api.AgentSupportedCapability
import com.only4.cap4k.plugin.pipeline.api.ArtifactOutputKind
import com.only4.cap4k.plugin.pipeline.api.ConflictPolicy
import com.only4.cap4k.plugin.pipeline.api.PipelineBoundaryAuthorities
import com.only4.cap4k.plugin.pipeline.api.PipelineBoundaryKind
import com.only4.cap4k.plugin.pipeline.api.PipelineCapabilityBoundary
import com.only4.cap4k.plugin.pipeline.api.PipelineCapabilityKind
import com.only4.cap4k.plugin.pipeline.api.PipelineCapabilityMetadataLevel
import com.only4.cap4k.plugin.pipeline.api.PipelineCapabilityProvenance
import com.only4.cap4k.plugin.pipeline.api.PipelineExecutionLane
import com.only4.cap4k.plugin.pipeline.api.PipelineInputRequirement
import com.only4.cap4k.plugin.pipeline.api.PipelineInputSafety
import com.only4.cap4k.plugin.pipeline.api.PipelinePublicTasks
import com.only4.cap4k.plugin.pipeline.api.ProjectLayout
import com.only4.cap4k.plugin.pipeline.json.PipelineJson
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AgentSnapshotCodecTest {
    private val codec = AgentSnapshotCodec()

    @Test
    fun `encode writes manifest plus seven stable lowercase credential safe sections`() {
        val encoded = codec.encode("2.1.0", sections(reversed = false))

        assertEquals(
            setOf(
                "project.json",
                "capabilities.json",
                "inputs.json",
                "ownership.json",
                "runtime.json",
                "analysis.json",
                "diagnostics.json",
            ),
            encoded.sectionsByPath.keys,
        )
        assertEquals(encoded.sectionsByPath.size + 1, encoded.filesByPath.size)
        assertEquals(AgentSnapshotStatus.PARTIAL, encoded.manifest.status)
        assertEquals(4, encoded.manifest.contractVersion)
        val runtimeReference = encoded.manifest.sections.single { section -> section.id == "runtime" }
        assertEquals(7, runtimeReference.counts["capabilities"])
        assertEquals(3, runtimeReference.counts["providers"])
        assertEquals(1, encoded.manifest.diagnosticCounts.warning)
        assertEquals(listOf("analysis", "diagnostics"), encoded.manifest.recommendedSections)
        assertTrue(encoded.manifest.snapshotId.matches(Regex("[0-9a-f]{64}")))
        assertEquals(
            encoded.manifest.snapshotId,
            AgentHashing.snapshotSha256(encoded.sectionSha256ByPath),
        )
        encoded.sectionsByPath.values.forEach { section ->
            assertEquals(AgentHashing.sha256(section.json), section.sha256)
        }
        assertEquals(AgentHashing.sha256(encoded.manifestJson), encoded.manifestSha256)

        val capabilitiesJson = encoded.sectionJsonByPath.getValue("capabilities.json")
        val capabilitiesTree = PipelineJson.newMapper().readTree(capabilitiesJson)
        assertTrue(capabilitiesTree.findValues("kind").any { it.asText() == "generator" })
        assertTrue(capabilitiesTree.findValues("executionLanes").isNotEmpty())
        assertTrue(capabilitiesTree.findValues("executionLanes").any { lanes ->
            lanes.any { it.asText() == "authoring" }
        })
        assertTrue(capabilitiesJson.indexOf(PipelinePublicTasks.PLAN) < capabilitiesJson.indexOf(PipelinePublicTasks.GENERATE))

        val diagnosticsJson = encoded.sectionJsonByPath.getValue("diagnostics.json")
        assertTrue(diagnosticsJson.contains("<configured>"))
        assertFalse(diagnosticsJson.contains("jdbc:postgresql"))
        assertFalse(diagnosticsJson.contains("hunter2"))
        assertFalse(diagnosticsJson.contains("api-token"))

        val runtimeJson = encoded.sectionJsonByPath.getValue("runtime.json")
        val runtime = codec.fromJson(runtimeJson, AgentRuntimeSection::class.java)
        assertEquals(AgentEventHandlerAuthoring.METHOD_LEVEL_EVENT_LISTENER, runtime.eventHandler.authoring)
        assertEquals(
            AgentEventHandlerExecution.SYNCHRONOUS_SEQUENTIAL_FAIL_FAST,
            runtime.eventHandler.execution,
        )
        assertEquals(AgentEventHandlerEqualOrder.UNSPECIFIED, runtime.eventHandler.ordering.equalValues)
        assertEquals("method", runtime.eventHandler.ordering.target)
        assertTrue(runtime.eventHandler.ordering.lowerValuesFirst)
        assertEquals(AgentEventHandlerReturnType.UNIT_OR_VOID, runtime.eventHandler.returnType)
        assertEquals(
            AgentEventHandlerManagedAsyncCompletion.WAIT_BEFORE_HANDLER_COMPLETION,
            runtime.eventHandler.managedAsyncCompletion.completion,
        )
        assertEquals(
            AgentEventHandlerManagedAsyncFailure.FAIL_HANDLER,
            runtime.eventHandler.managedAsyncCompletion.failure,
        )
        assertEquals(
            listOf(
                "Mediator.capabilities.callAsync",
                "Mediator.endpoints.sendAsync",
                "Mediator.queries.askAsync",
            ),
            runtime.eventHandler.managedAsyncCompletion.trackedOperations,
        )
        assertTrue("transactional_event_listener" in runtime.eventHandler.forbidden)
        assertEquals("cap4k.agent.runtime.v3", runtime.schema)
        assertEquals(
            RuntimeAgentFactsCatalog.capabilities().map { fact -> fact.capabilityId }.sorted(),
            runtime.capabilities.map { fact -> fact.capabilityId },
        )
        assertEquals(
            RuntimeAgentFactsCatalog.providers().map { fact -> fact.providerId },
            runtime.providers.map { fact -> fact.providerId },
        )
        assertTrue(runtime.providers.all { provider -> provider.applicationAssembly.name == "UNKNOWN" })
        assertTrue(runtime.providers.all { provider -> provider.runtimeObservation.name == "NOT_PERFORMED" })
        assertTrue(runtime.providers.all { provider -> provider.operationalState.name == "UNKNOWN" })
        assertTrue(runtime.providers.all { provider -> provider.verification.name == "NOT_PERFORMED" })
        assertFalse(runtimeJson.contains("healthy", ignoreCase = true))
        assertFalse(runtimeJson.contains("success", ignoreCase = true))

        val decodedProject = codec.fromJson(
            encoded.sectionJsonByPath.getValue("project.json"),
            AgentProjectSection::class.java,
        )
        assertEquals(AgentSnapshotStatus.OK, decodedProject.status)
        assertEquals(ProjectLayout.MULTI_MODULE, decodedProject.project.layout)
    }

    @Test
    fun `snapshot hashes and json do not depend on adapter collection order`() {
        val first = codec.encode("2.1.0", sections(reversed = false))
        val reordered = codec.encode("2.1.0", sections(reversed = true))

        assertEquals(first.manifest.snapshotId, reordered.manifest.snapshotId)
        assertEquals(first.filesByPath, reordered.filesByPath)
    }

    @Test
    fun `analysis v2 encodes normalized partition local evidence`() {
        val analysis = AgentAnalysisSection(
            status = AgentSnapshotStatus.PARTIAL,
            configured = true,
            inputDirs = listOf("build\\cap4k-code-analysis", "build/cap4k-code-analysis"),
            partitions = listOf(
                AgentAnalysisPartition(
                    id = AgentAnalysisPartitionIds.GRAPH,
                    requested = true,
                    status = AgentSnapshotStatus.OK,
                    counts = linkedMapOf("relationships" to 2, "nodes" to 3),
                    sources = listOf(
                        AgentAnalysisSource("source-b", "module-b\\build\\analysis"),
                        AgentAnalysisSource("source-a", "module-a/build/analysis"),
                    ),
                    plannedOutputPaths = listOf("analysis\\flows\\index.json", "analysis/flows/index.json"),
                    availableOutputPaths = listOf("analysis\\flows\\index.json"),
                    diagnosticIds = listOf("graph.z", "graph.a", "graph.a"),
                ),
                AgentAnalysisPartition(
                    id = AgentAnalysisPartitionIds.AGGREGATE_STRUCTURE,
                    requested = false,
                    status = AgentSnapshotStatus.INVALID,
                    counts = mapOf("aggregateElements" to 1),
                    sources = listOf(AgentAnalysisSource("source-a", "module-a/build/analysis")),
                ),
                AgentAnalysisPartition(
                    id = AgentAnalysisPartitionIds.DESIGN_PROJECTION,
                    requested = false,
                    status = AgentSnapshotStatus.OK,
                    counts = mapOf("designBlocks" to 4),
                    sources = listOf(AgentAnalysisSource("source-a", "module-a/build/analysis")),
                ),
            ),
        )
        val encoded = codec.encode("2.1.0", sections(reversed = false).copy(analysis = analysis))
        val decoded = codec.fromJson(
            encoded.sectionJsonByPath.getValue("analysis.json"),
            AgentAnalysisSection::class.java,
        )

        assertEquals("cap4k.agent.analysis.v2", decoded.schema)
        assertEquals(
            listOf(
                AgentAnalysisPartitionIds.AGGREGATE_STRUCTURE,
                AgentAnalysisPartitionIds.DESIGN_PROJECTION,
                AgentAnalysisPartitionIds.GRAPH,
            ),
            decoded.partitions.map { it.id },
        )
        val graph = decoded.partitions.single { it.id == AgentAnalysisPartitionIds.GRAPH }
        assertTrue(graph.requested)
        assertEquals(listOf("nodes", "relationships"), graph.counts.keys.toList())
        assertEquals(listOf("source-a", "source-b"), graph.sources.map { it.id })
        assertEquals(listOf("module-a/build/analysis", "module-b/build/analysis"), graph.sources.map { it.path })
        assertEquals(listOf("analysis/flows/index.json"), graph.plannedOutputPaths)
        assertEquals(listOf("analysis/flows/index.json"), graph.availableOutputPaths)
        assertEquals(listOf("graph.a", "graph.z"), graph.diagnosticIds)

        val analysisReference = encoded.manifest.sections.single { section -> section.id == "analysis" }
        assertEquals(3, analysisReference.counts["partitions"])
        assertEquals(3, analysisReference.counts["graph.nodes"])
        assertEquals(2, analysisReference.counts["graph.relationships"])
        assertEquals(4, analysisReference.counts["designProjection.designBlocks"])
        assertEquals(1, analysisReference.counts["aggregateStructure.aggregateElements"])
    }

    private fun sections(reversed: Boolean): AgentSnapshotSections {
        val modules = listOf(
            AgentProjectModule("domain", "demo-domain", ":demo-domain", true),
            AgentProjectModule("application", "demo-application", ":demo-application", true),
        ).ordered(reversed)
        val diagnostics = listOf(
            AgentDiagnostic(
                id = "input.live-unverified",
                level = AgentDiagnosticLevel.WARNING,
                stage = "collect",
                capabilityId = "pipeline.generator.query",
                inputPath = "design/design.json",
                message = "failed jdbc:postgresql://admin:hunter2@localhost/demo?password=hunter2 token=api-token",
                hint = "run cap4kPlan; password=hunter2",
                proves = "current live schema freshness remains unknown",
            )
        )
        val boundary = PipelineCapabilityBoundary(
            PipelineBoundaryKind.GENERATION,
            PipelineBoundaryAuthorities.PIPELINE_GENERATOR,
        )
        return AgentSnapshotSections(
            project = AgentProjectSection(
                status = AgentSnapshotStatus.OK,
                project = AgentProjectSummary(
                    name = "demo",
                    group = "com.acme",
                    version = "1.0.0",
                    basePackage = "com.acme.demo",
                    layout = ProjectLayout.MULTI_MODULE,
                    modules = modules,
                    publicTasks = listOf(PipelinePublicTasks.GENERATE, PipelinePublicTasks.PLAN).ordered(reversed),
                ),
            ),
            capabilities = AgentCapabilitiesSection(
                status = AgentSnapshotStatus.OK,
                supported = listOf(
                    AgentSupportedCapability(
                        capabilityId = "pipeline.generator.query",
                        providerId = "query",
                        displayName = "Query Generator",
                        kind = PipelineCapabilityKind.GENERATOR,
                        provenance = PipelineCapabilityProvenance.builtIn("cap4k-plugin-pipeline-generator-design"),
                        tacticalCarriers = listOf("Query"),
                        executionLanes = listOf(PipelineExecutionLane.AUTHORING),
                        tasks = listOf(PipelinePublicTasks.PLAN, PipelinePublicTasks.GENERATE),
                        inputRequirements = listOf(
                            PipelineInputRequirement(
                                id = "design-json-input",
                                capabilityIds = listOf("pipeline.source.design-json"),
                            )
                        ),
                        outputKinds = listOf(ArtifactOutputKind.CHECKED_IN_SOURCE),
                        boundaries = listOf(boundary),
                        metadataLevel = PipelineCapabilityMetadataLevel.COMPLETE,
                    )
                ),
                effective = listOf(
                    AgentEffectiveCapability(
                        capabilityId = "pipeline.generator.query",
                        providerId = "query",
                        status = AgentCapabilityStatus.READY,
                    )
                ),
            ),
            inputs = AgentInputsSection(
                status = AgentSnapshotStatus.OK,
                inputs = listOf(
                    AgentInput(
                        id = "design-json-files",
                        providerId = "design-json",
                        safety = PipelineInputSafety.LOCAL_PROJECT,
                        configured = true,
                        locations = listOf("design\\design.json", "design/types.json").ordered(reversed),
                        exists = true,
                        readable = true,
                        identity = "input-identity",
                        options = AgentOptionSummary(
                            configuredKeys = listOf("files", "password").ordered(reversed),
                            sensitiveKeys = listOf("password"),
                        ),
                        requiredBy = listOf("pipeline.generator.query"),
                        planTask = PipelinePublicTasks.PLAN,
                    )
                ),
            ),
            ownership = AgentOwnershipSection(
                status = AgentSnapshotStatus.OK,
                items = listOf(
                    AgentOwnershipItem(
                        generatorId = "query",
                        moduleRole = "application",
                        templateId = "design/query.kt.peb",
                        outputPath = "demo-application/src/main/kotlin/com/acme/demo/application/queries/Query.kt",
                        outputKind = ArtifactOutputKind.CHECKED_IN_SOURCE,
                        conflictPolicy = ConflictPolicy.SKIP,
                        resolvedOutputRoot = "",
                    )
                ),
                managedRoots = linkedMapOf(
                    *(if (reversed) {
                        arrayOf("application" to "demo-application", "domain" to "demo-domain")
                    } else {
                        arrayOf("domain" to "demo-domain", "application" to "demo-application")
                    })
                ),
                evidence = listOf(
                    AgentEvidence(
                        kind = "plan",
                        path = "build/cap4k/plan.json",
                        freshness = AgentEvidenceFreshness.UNKNOWN,
                        reason = "live source freshness cannot be proven",
                        nextAction = PipelinePublicTasks.PLAN,
                    )
                ),
            ),
            runtime = AgentRuntimeSection(
                status = AgentSnapshotStatus.OK,
                capabilities = RuntimeAgentFactsCatalog.capabilities().ordered(reversed),
                providers = RuntimeAgentFactsCatalog.providers().ordered(reversed),
                extensions = listOf(
                    AgentRuntimeExtension(
                        id = "sample-extension",
                        displayName = "Sample Extension",
                        spiVersion = 1,
                        contributionIds = listOf("z", "a").ordered(reversed),
                        configuredOptionKeys = listOf("mode", "password").ordered(reversed),
                        sensitiveOptionKeys = listOf("password"),
                    )
                ),
                boundaries = linkedMapOf("query" to listOf(boundary)),
            ),
            analysis = AgentAnalysisSection(
                status = AgentSnapshotStatus.UNAVAILABLE,
                configured = false,
                reason = "IR analysis is not configured.",
            ),
            diagnostics = AgentDiagnosticsSection(
                status = AgentSnapshotStatus.PARTIAL,
                diagnostics = diagnostics.ordered(reversed),
            ),
        )
    }

    private fun <T> List<T>.ordered(reversed: Boolean): List<T> = if (reversed) reversed() else this
}

package com.only4.cap4k.plugin.pipeline.agent

import com.only4.cap4k.plugin.pipeline.api.AgentCapabilityStatus
import com.only4.cap4k.plugin.pipeline.api.AgentEvidenceFreshness
import com.only4.cap4k.plugin.pipeline.api.AgentRuntimeCapabilityFact
import com.only4.cap4k.plugin.pipeline.api.AgentRuntimeOwnership
import com.only4.cap4k.plugin.pipeline.api.AgentRuntimeProviderFact
import com.only4.cap4k.plugin.pipeline.api.AgentSnapshotStatus
import com.only4.cap4k.plugin.pipeline.api.AnalyzerContractCatalog
import com.only4.cap4k.plugin.pipeline.api.PipelineBoundaryAuthorities
import com.only4.cap4k.plugin.pipeline.api.PipelineBoundaryKind
import com.only4.cap4k.plugin.pipeline.api.PipelineCapabilityBoundary
import com.only4.cap4k.plugin.pipeline.api.PipelineCapabilityDescriptor
import com.only4.cap4k.plugin.pipeline.api.PipelineCapabilityKind
import com.only4.cap4k.plugin.pipeline.api.PipelineExecutionLane
import com.only4.cap4k.plugin.pipeline.api.PipelineInputRequirement
import com.only4.cap4k.plugin.pipeline.api.PipelineInputRequirementMatch
import com.only4.cap4k.plugin.pipeline.api.PipelineInputSafety
import com.only4.cap4k.plugin.pipeline.api.PipelinePublicTasks
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CapabilityContractFactsTest {
    @Test
    fun `facts are deterministic and include all current contract surfaces`() {
        val facts = CapabilityContractFactsFactory.derive(
            descriptors = listOf(generatorDescriptor()),
            publicTasks = PipelinePublicTasks.contracts.reversed(),
        )

        assertEquals(PipelinePublicTasks.contracts.sortedBy { it.name }, facts.publicTasks)
        assertEquals(AgentContractCatalog.sections.map { it.id }.sorted(), facts.agentSections.map { it.id })
        assertEquals(CapabilityContractNodes.surfaces, facts.surfaces)
        assertEquals(listOf("pipeline.generator.sample"), facts.pipelineCapabilities.map { it.capabilityId })
        assertEquals("potential-impact", facts.propagationClosureSemantics)
        assertTrue(facts.dependencyEdges.any {
            it.source == "pipeline.generator.sample" &&
                it.target == "task.${PipelinePublicTasks.GENERATE}" &&
                it.relation == CapabilityContractRelation.EXPOSES_TASK &&
                it.condition.applicability == CapabilityContractApplicability.UNCONDITIONAL
        })
    }

    @Test
    fun `input requirement edges retain normalized applicability conditions`() {
        val requirement = PipelineInputRequirement(
            id = "sample-input",
            capabilityIds = listOf("pipeline.source.beta", "pipeline.source.alpha"),
            configurationPaths = listOf("sources.beta", "sources.alpha"),
            match = PipelineInputRequirementMatch.ANY,
            safety = PipelineInputSafety.LIVE_EXTERNAL,
        )
        val facts = CapabilityContractFactsFactory.derive(
            descriptors = listOf(generatorDescriptor().copy(inputRequirements = listOf(requirement))),
            publicTasks = PipelinePublicTasks.contracts,
        )

        val requirementEdges = facts.dependencyEdges.filter { it.relation == CapabilityContractRelation.REQUIRES }
        assertEquals(listOf("pipeline.source.alpha", "pipeline.source.beta"), requirementEdges.map { it.source })
        assertTrue(requirementEdges.all { edge ->
            edge.condition == CapabilityContractCondition(
                applicability = CapabilityContractApplicability.INPUT_REQUIREMENT,
                requirementId = "sample-input",
                capabilityIds = listOf("pipeline.source.alpha", "pipeline.source.beta"),
                configurationPaths = listOf("sources.alpha", "sources.beta"),
                match = PipelineInputRequirementMatch.ANY,
                safety = PipelineInputSafety.LIVE_EXTERNAL,
            )
        })
    }

    @Test
    fun `Agent status vocabulary comes from production enums`() {
        val facts = CapabilityContractFactsFactory.derive(
            descriptors = listOf(generatorDescriptor()),
            publicTasks = PipelinePublicTasks.contracts,
        )

        assertEquals(AgentSnapshotStatus.entries.map { it.name.lowercase() }.sorted(), facts.agentStatusVocabulary.snapshot)
        assertEquals(AgentCapabilityStatus.entries.map { it.name.lowercase() }.sorted(), facts.agentStatusVocabulary.effectiveCapability)
        assertEquals(AgentEvidenceFreshness.entries.map { it.name.lowercase() }.sorted(), facts.agentStatusVocabulary.evidenceFreshness)
        assertEquals(listOf("effective", "supported"), facts.agentCapabilityViews)
    }

    @Test
    fun `path rules reference known graph nodes and preserve first match specificity`() {
        val facts = CapabilityContractFactsFactory.derive(
            descriptors = listOf(generatorDescriptor()),
            publicTasks = PipelinePublicTasks.contracts,
        )

        assertEquals("**/src/test/**", facts.pathRules.first().pattern)
        assertTrue(facts.pathRules.any { it.pattern == "docs/public/**" && it.seedNodes == listOf(CapabilityContractNodes.PUBLIC_DOCS) })
        assertTrue(facts.pathRules.any { it.pattern == "ddd-*/**" && it.seedNodes == listOf(CapabilityContractNodes.RUNTIME) })
    }

    @Test
    fun `Analyzer outputs and propagation come from analysis-lane descriptors`() {
        val descriptor = generatorDescriptor().copy(executionLanes = listOf(PipelineExecutionLane.ANALYSIS))

        val facts = CapabilityContractFactsFactory.derive(
            descriptors = listOf(descriptor),
            publicTasks = PipelinePublicTasks.contracts,
        )

        assertEquals(listOf(descriptor.providerId), facts.analyzerOutputs)
        assertTrue(CapabilityContractNodes.ANALYZER in facts.propagationClosure.getValue(descriptor.capabilityId))
    }

    @Test
    fun `Analyzer partition facts and dependency edges come from the production catalog`() {
        val facts = CapabilityContractFactsFactory.derive(
            descriptors = analyzerDescriptors(),
            publicTasks = PipelinePublicTasks.contracts,
        )

        assertEquals("cap4k.capability-contract-facts.v3", facts.schema)
        assertEquals(AnalyzerContractCatalog.partitions.sortedBy { it.id }, facts.analyzerPartitions)
        AnalyzerContractCatalog.partitions.forEach { partition ->
            assertTrue(facts.dependencyEdges.any { edge ->
                edge.source == partition.sourceCapabilityId &&
                    edge.target == partition.nodeId &&
                    edge.relation == CapabilityContractRelation.PROJECTS_TO
            })
            partition.consumerCapabilityIds.forEach { consumerId ->
                assertTrue(facts.dependencyEdges.any { edge ->
                    edge.source == partition.nodeId &&
                        edge.target == consumerId &&
                        edge.relation == CapabilityContractRelation.REQUIRES
                })
            }
            assertTrue(facts.dependencyEdges.any { edge ->
                edge.source == partition.nodeId &&
                    edge.target == "agent.analysis" &&
                    edge.relation == CapabilityContractRelation.PROJECTS_TO
            })
        }
    }

    @Test
    fun `Analyzer partition identities and graph nodes must be unique`() {
        val duplicateId = AnalyzerContractCatalog.GRAPH.copy(nodeId = "analyzer.partition.graph-copy")
        val duplicateNode = AnalyzerContractCatalog.GRAPH.copy(id = "graphCopy")

        val duplicateIdFailure = assertThrows(IllegalArgumentException::class.java) {
            CapabilityContractFactsFactory.derive(
                descriptors = analyzerDescriptors(),
                publicTasks = PipelinePublicTasks.contracts,
                analyzerPartitions = AnalyzerContractCatalog.partitions + duplicateId,
            )
        }
        val duplicateNodeFailure = assertThrows(IllegalArgumentException::class.java) {
            CapabilityContractFactsFactory.derive(
                descriptors = analyzerDescriptors(),
                publicTasks = PipelinePublicTasks.contracts,
                analyzerPartitions = AnalyzerContractCatalog.partitions + duplicateNode,
            )
        }

        assertTrue(duplicateIdFailure.message.orEmpty().contains("duplicate Analyzer partition id"))
        assertTrue(duplicateNodeFailure.message.orEmpty().contains("duplicate Analyzer partition node"))
    }

    @Test
    fun `runtime propagation closure reaches every downstream capability and projection surface`() {
        val facts = CapabilityContractFactsFactory.derive(
            descriptors = listOf(generatorDescriptor()),
            publicTasks = PipelinePublicTasks.contracts,
        )

        assertEquals(
            listOf(
                CapabilityContractNodes.AGENT_FACTS,
                CapabilityContractNodes.ANALYZER,
                CapabilityContractNodes.GENERATOR,
                CapabilityContractNodes.PUBLIC_DOCS,
                CapabilityContractNodes.SKILL,
            ).sorted(),
            facts.propagationClosure.getValue(CapabilityContractNodes.RUNTIME)
                .filter(CapabilityContractNodes.surfaceNodeIds::contains)
                .sorted(),
        )
    }

    @Test
    fun `Agent section identities and paths must be unique`() {
        val duplicateId = AgentContractCatalog.PROJECT.copy(path = "project-copy.json")
        val duplicatePath = AgentContractCatalog.PROJECT.copy(id = "project-copy")

        val duplicateIdFailure = assertThrows(IllegalArgumentException::class.java) {
            CapabilityContractFactsFactory.derive(
                descriptors = listOf(generatorDescriptor()),
                publicTasks = PipelinePublicTasks.contracts,
                agentSections = AgentContractCatalog.sections + duplicateId,
            )
        }
        val duplicatePathFailure = assertThrows(IllegalArgumentException::class.java) {
            CapabilityContractFactsFactory.derive(
                descriptors = listOf(generatorDescriptor()),
                publicTasks = PipelinePublicTasks.contracts,
                agentSections = AgentContractCatalog.sections + duplicatePath,
            )
        }

        assertTrue(duplicateIdFailure.message.orEmpty().contains("duplicate Agent section id"))
        assertTrue(duplicatePathFailure.message.orEmpty().contains("duplicate Agent section path"))
    }

    @Test
    fun `descriptor tasks must be registered public tasks`() {
        val descriptor = generatorDescriptor().copy(tasks = listOf("cap4kUnknown"))

        val failure = assertThrows(IllegalArgumentException::class.java) {
            CapabilityContractFactsFactory.derive(
                descriptors = listOf(descriptor),
                publicTasks = PipelinePublicTasks.contracts,
            )
        }

        assertTrue(failure.message.orEmpty().contains("cap4kUnknown"))
    }

    @Test
    fun `runtime providers must reference a declared runtime capability`() {
        val capability = AgentRuntimeCapabilityFact(
            capabilityId = "runtime.sample",
            displayName = "Sample",
            ownership = AgentRuntimeOwnership(contractModule = "sample-contract"),
        )
        val provider = AgentRuntimeProviderFact(
            providerId = "sample.provider",
            capabilityId = "runtime.missing",
            displayName = "Missing",
            ownership = AgentRuntimeOwnership(contractModule = "sample-contract"),
        )

        val failure = assertThrows(IllegalArgumentException::class.java) {
            CapabilityContractFactsFactory.derive(
                descriptors = listOf(generatorDescriptor()),
                publicTasks = PipelinePublicTasks.contracts,
                runtimeCapabilities = listOf(capability),
                runtimeProviders = listOf(provider),
            )
        }

        assertTrue(failure.message.orEmpty().contains("runtime.missing"))
    }

    private fun analyzerDescriptors(): List<PipelineCapabilityDescriptor> = listOf(
        PipelineCapabilityDescriptor.builtIn(
            providerId = "ir-analysis",
            displayName = "IR Analysis Source",
            kind = PipelineCapabilityKind.SOURCE,
            module = "cap4k-plugin-pipeline-source-ir-analysis",
            executionLanes = listOf(PipelineExecutionLane.ANALYSIS),
            tasks = listOf(PipelinePublicTasks.ANALYSIS_PLAN, PipelinePublicTasks.ANALYSIS_GENERATE),
            boundaries = emptyList(),
        ),
        PipelineCapabilityDescriptor.builtIn(
            providerId = "flow",
            displayName = "Flow Generator",
            kind = PipelineCapabilityKind.GENERATOR,
            module = "cap4k-plugin-pipeline-generator-flow",
            executionLanes = listOf(PipelineExecutionLane.ANALYSIS),
            tasks = listOf(PipelinePublicTasks.ANALYSIS_PLAN, PipelinePublicTasks.ANALYSIS_GENERATE),
            boundaries = emptyList(),
        ),
        PipelineCapabilityDescriptor.builtIn(
            providerId = "drawing-board",
            displayName = "Drawing Board Generator",
            kind = PipelineCapabilityKind.GENERATOR,
            module = "cap4k-plugin-pipeline-generator-drawing-board",
            executionLanes = listOf(PipelineExecutionLane.ANALYSIS),
            tasks = listOf(PipelinePublicTasks.ANALYSIS_PLAN, PipelinePublicTasks.ANALYSIS_GENERATE),
            boundaries = emptyList(),
        ),
    )

    private fun generatorDescriptor() = PipelineCapabilityDescriptor.builtIn(
        providerId = "sample",
        displayName = "Sample Generator",
        kind = PipelineCapabilityKind.GENERATOR,
        module = "sample-generator",
        executionLanes = listOf(PipelineExecutionLane.AUTHORING),
        tasks = listOf(PipelinePublicTasks.GENERATE),
        boundaries = listOf(
            PipelineCapabilityBoundary(
                kind = PipelineBoundaryKind.GENERATION,
                authority = PipelineBoundaryAuthorities.PIPELINE_GENERATOR,
            )
        ),
    )
}

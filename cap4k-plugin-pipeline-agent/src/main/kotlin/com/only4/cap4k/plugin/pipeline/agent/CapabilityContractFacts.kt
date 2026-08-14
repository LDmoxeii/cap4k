package com.only4.cap4k.plugin.pipeline.agent

import com.only4.cap4k.plugin.pipeline.api.AgentCapabilityStatus
import com.only4.cap4k.plugin.pipeline.api.AgentDiagnosticLevel
import com.only4.cap4k.plugin.pipeline.api.AgentEvidenceFreshness
import com.only4.cap4k.plugin.pipeline.api.AgentRuntimeApplicationAssembly
import com.only4.cap4k.plugin.pipeline.api.AgentRuntimeCapabilityFact
import com.only4.cap4k.plugin.pipeline.api.AgentRuntimeFrameworkSupport
import com.only4.cap4k.plugin.pipeline.api.AgentRuntimeLiveStateSource
import com.only4.cap4k.plugin.pipeline.api.AgentRuntimeObservation
import com.only4.cap4k.plugin.pipeline.api.AgentRuntimeOperationalState
import com.only4.cap4k.plugin.pipeline.api.AgentRuntimeProviderFact
import com.only4.cap4k.plugin.pipeline.api.AgentRuntimeVerification
import com.only4.cap4k.plugin.pipeline.api.AgentSnapshotStatus
import com.only4.cap4k.plugin.pipeline.api.AgentSupportedCapability
import com.only4.cap4k.plugin.pipeline.api.AnalyzerContractCatalog
import com.only4.cap4k.plugin.pipeline.api.AnalyzerPartitionContract
import com.only4.cap4k.plugin.pipeline.api.AgentValidationStatus
import com.only4.cap4k.plugin.pipeline.api.ArtifactOutputKind
import com.only4.cap4k.plugin.pipeline.api.PipelineCapabilityDescriptor
import com.only4.cap4k.plugin.pipeline.api.PipelineCapabilityKind
import com.only4.cap4k.plugin.pipeline.api.PipelineExecutionLane
import com.only4.cap4k.plugin.pipeline.api.PipelineInputRequirement
import com.only4.cap4k.plugin.pipeline.api.PipelineInputRequirementMatch
import com.only4.cap4k.plugin.pipeline.api.PipelineInputSafety
import com.only4.cap4k.plugin.pipeline.api.PipelinePublicTaskContract
import com.only4.cap4k.plugin.pipeline.api.agentContractEnumWireName

const val CAP4K_CAPABILITY_CONTRACT_FACTS_SCHEMA = "cap4k.capability-contract-facts.v3"

enum class CapabilityContractRelation {
    EXPOSES_TASK,
    PROJECTS_TO,
    PROPAGATES_TO,
    PROVIDES,
    REQUIRES,
}

enum class CapabilityContractApplicability {
    UNCONDITIONAL,
    INPUT_REQUIREMENT,
}

data class CapabilityContractCondition(
    val applicability: CapabilityContractApplicability = CapabilityContractApplicability.UNCONDITIONAL,
    val requirementId: String? = null,
    val capabilityIds: List<String> = emptyList(),
    val configurationPaths: List<String> = emptyList(),
    val match: PipelineInputRequirementMatch? = null,
    val safety: PipelineInputSafety? = null,
) {
    companion object {
        fun inputRequirement(requirement: PipelineInputRequirement): CapabilityContractCondition =
            CapabilityContractCondition(
                applicability = CapabilityContractApplicability.INPUT_REQUIREMENT,
                requirementId = requirement.id,
                capabilityIds = requirement.capabilityIds.distinct().sorted(),
                configurationPaths = requirement.configurationPaths.distinct().sorted(),
                match = requirement.match,
                safety = requirement.safety,
            )
    }
}

data class CapabilityContractDependency(
    val source: String,
    val target: String,
    val relation: CapabilityContractRelation,
    val condition: CapabilityContractCondition = CapabilityContractCondition(),
)

data class CapabilityContractSurface(
    val name: String,
    val nodeId: String,
)

data class AgentStatusVocabulary(
    val snapshot: List<String>,
    val effectiveCapability: List<String>,
    val validation: List<String>,
    val evidenceFreshness: List<String>,
    val diagnosticLevel: List<String>,
    val runtimeFrameworkSupport: List<String>,
    val runtimeApplicationAssembly: List<String>,
    val runtimeObservation: List<String>,
    val runtimeOperationalState: List<String>,
    val runtimeVerification: List<String>,
    val runtimeLiveStateSource: List<String>,
)

enum class CapabilityContractPathClassification {
    CONTRACT,
    PROJECTION,
    SUPPORT,
    GOVERNANCE,
}

enum class CapabilityContractPathMatchPolicy {
    FIRST_MATCH,
}

data class CapabilityContractPathRule(
    val pattern: String,
    val seedNodes: List<String>,
    val classification: CapabilityContractPathClassification,
)

data class CapabilityContractFacts(
    val schema: String = CAP4K_CAPABILITY_CONTRACT_FACTS_SCHEMA,
    val surfaces: List<CapabilityContractSurface>,
    val publicTasks: List<PipelinePublicTaskContract>,
    val agentSections: List<AgentSectionContract>,
    val agentCapabilityViews: List<String>,
    val agentStatusVocabulary: AgentStatusVocabulary,
    val outputKinds: List<ArtifactOutputKind>,
    val analyzerOutputs: List<String>,
    val analyzerPartitions: List<AnalyzerPartitionContract>,
    val pipelineCapabilities: List<AgentSupportedCapability>,
    val runtimeCapabilities: List<AgentRuntimeCapabilityFact>,
    val runtimeProviders: List<AgentRuntimeProviderFact>,
    val dependencyEdges: List<CapabilityContractDependency>,
    val propagationClosure: Map<String, List<String>>,
    val propagationClosureSemantics: String,
    val pathMatchPolicy: CapabilityContractPathMatchPolicy,
    val pathRules: List<CapabilityContractPathRule>,
)

object CapabilityContractNodes {
    const val RUNTIME = "surface.runtime"
    const val GENERATOR = "surface.generator"
    const val ANALYZER = "surface.analyzer"
    const val AGENT_FACTS = "projection.agent-facts"
    const val PUBLIC_DOCS = "projection.public-docs"
    const val SKILL = "projection.skill"

    val surfaces = listOf(
        CapabilityContractSurface("Runtime", RUNTIME),
        CapabilityContractSurface("Generator", GENERATOR),
        CapabilityContractSurface("Analyzer", ANALYZER),
        CapabilityContractSurface("AgentFacts", AGENT_FACTS),
        CapabilityContractSurface("Public Docs", PUBLIC_DOCS),
        CapabilityContractSurface("Skill", SKILL),
    )

    val surfaceNodeIds = surfaces.map(CapabilityContractSurface::nodeId)
}

object CapabilityContractPathCatalog {
    val rules = listOf(
        CapabilityContractPathRule("**/src/test/**", emptyList(), CapabilityContractPathClassification.SUPPORT),
        CapabilityContractPathRule("scripts/test-*.ps1", emptyList(), CapabilityContractPathClassification.SUPPORT),
        CapabilityContractPathRule("README.md", listOf(CapabilityContractNodes.PUBLIC_DOCS), CapabilityContractPathClassification.PROJECTION),
        CapabilityContractPathRule("README.en.md", listOf(CapabilityContractNodes.PUBLIC_DOCS), CapabilityContractPathClassification.PROJECTION),
        CapabilityContractPathRule("docs/public/**", listOf(CapabilityContractNodes.PUBLIC_DOCS), CapabilityContractPathClassification.PROJECTION),
        CapabilityContractPathRule("skills/cap4k-authoring/**", listOf(CapabilityContractNodes.SKILL), CapabilityContractPathClassification.PROJECTION),
        CapabilityContractPathRule("cap4k-plugin-pipeline-api/**/AgentContracts.kt", listOf(CapabilityContractNodes.AGENT_FACTS), CapabilityContractPathClassification.CONTRACT),
        CapabilityContractPathRule("cap4k-plugin-pipeline-agent/**", listOf(CapabilityContractNodes.AGENT_FACTS), CapabilityContractPathClassification.CONTRACT),
        CapabilityContractPathRule("cap4k-analysis-metadata/**", listOf(CapabilityContractNodes.ANALYZER), CapabilityContractPathClassification.CONTRACT),
        CapabilityContractPathRule("cap4k-plugin-code-analysis-*/**", listOf(CapabilityContractNodes.ANALYZER), CapabilityContractPathClassification.CONTRACT),
        CapabilityContractPathRule("cap4k-plugin-pipeline-source-ir-analysis/**", listOf(CapabilityContractNodes.ANALYZER), CapabilityContractPathClassification.CONTRACT),
        CapabilityContractPathRule("cap4k-plugin-pipeline-generator-flow/**", listOf(CapabilityContractNodes.ANALYZER), CapabilityContractPathClassification.CONTRACT),
        CapabilityContractPathRule("cap4k-plugin-pipeline-generator-drawing-board/**", listOf(CapabilityContractNodes.ANALYZER), CapabilityContractPathClassification.CONTRACT),
        CapabilityContractPathRule("ddd-*/**", listOf(CapabilityContractNodes.RUNTIME), CapabilityContractPathClassification.CONTRACT),
        CapabilityContractPathRule("cap4k-ddd-*-starter/**", listOf(CapabilityContractNodes.RUNTIME), CapabilityContractPathClassification.CONTRACT),
        CapabilityContractPathRule("cap4k-plugin-pipeline-*/**", listOf(CapabilityContractNodes.GENERATOR), CapabilityContractPathClassification.CONTRACT),
        CapabilityContractPathRule(".github/**", emptyList(), CapabilityContractPathClassification.GOVERNANCE),
        CapabilityContractPathRule(".agents/**", emptyList(), CapabilityContractPathClassification.GOVERNANCE),
        CapabilityContractPathRule("AGENTS.md", emptyList(), CapabilityContractPathClassification.GOVERNANCE),
        CapabilityContractPathRule("scripts/**", emptyList(), CapabilityContractPathClassification.GOVERNANCE),
        CapabilityContractPathRule("docs/comet/**", emptyList(), CapabilityContractPathClassification.GOVERNANCE),
        CapabilityContractPathRule("docs/superpowers/**", emptyList(), CapabilityContractPathClassification.GOVERNANCE),
        CapabilityContractPathRule("buildSrc/**", emptyList(), CapabilityContractPathClassification.SUPPORT),
        CapabilityContractPathRule("gradle/**", emptyList(), CapabilityContractPathClassification.SUPPORT),
        CapabilityContractPathRule("*.gradle.kts", emptyList(), CapabilityContractPathClassification.SUPPORT),
        CapabilityContractPathRule("gradle.properties", emptyList(), CapabilityContractPathClassification.SUPPORT),
        CapabilityContractPathRule("gradlew*", emptyList(), CapabilityContractPathClassification.SUPPORT),
    )
}

object CapabilityContractFactsFactory {
    fun derive(
        descriptors: List<PipelineCapabilityDescriptor>,
        publicTasks: Collection<PipelinePublicTaskContract>,
        agentSections: List<AgentSectionContract> = AgentContractCatalog.sections,
        runtimeCapabilities: List<AgentRuntimeCapabilityFact> = RuntimeAgentFactsCatalog.capabilities(),
        runtimeProviders: List<AgentRuntimeProviderFact> = RuntimeAgentFactsCatalog.providers(),
        pathRules: List<CapabilityContractPathRule> = CapabilityContractPathCatalog.rules,
        analyzerPartitions: List<AnalyzerPartitionContract> = AnalyzerContractCatalog.partitions,
    ): CapabilityContractFacts {
        val normalizedDescriptors = normalizeDescriptors(descriptors)
        val duplicateSectionId = agentSections.groupingBy { it.id }.eachCount().entries
            .firstOrNull { it.value > 1 }
            ?.key
        require(duplicateSectionId == null) { "duplicate Agent section id: $duplicateSectionId" }
        val duplicateSectionPath = agentSections.groupingBy { it.path }.eachCount().entries
            .firstOrNull { it.value > 1 }
            ?.key
        require(duplicateSectionPath == null) { "duplicate Agent section path: $duplicateSectionPath" }

        val normalizedTasks = publicTasks.distinctBy { it.name }.sortedBy { it.name }
        require(normalizedTasks.size == publicTasks.size) { "duplicate public task contract name" }
        val publicTaskNames = normalizedTasks.map(PipelinePublicTaskContract::name)
        val unknownTasks = normalizedDescriptors
            .flatMap(PipelineCapabilityDescriptor::tasks)
            .distinct()
            .filterNot(publicTaskNames::contains)
            .sorted()
        require(unknownTasks.isEmpty()) {
            "pipeline capability descriptors reference unregistered public tasks: ${unknownTasks.joinToString()}"
        }

        val descriptorIds = normalizedDescriptors.mapTo(linkedSetOf()) { it.capabilityId }
        val normalizedAnalyzerPartitions = analyzerPartitions
            .filter { partition ->
                partition.sourceCapabilityId in descriptorIds && partition.consumerCapabilityIds.all(descriptorIds::contains)
            }
            .map { partition ->
                partition.copy(
                    consumerCapabilityIds = partition.consumerCapabilityIds.distinct().sorted(),
                    outputIds = partition.outputIds.distinct().sorted(),
                )
            }
            .sortedBy { it.id }
        val duplicateAnalyzerPartitionId = normalizedAnalyzerPartitions.groupingBy { it.id }.eachCount().entries
            .firstOrNull { it.value > 1 }
            ?.key
        require(duplicateAnalyzerPartitionId == null) { "duplicate Analyzer partition id: $duplicateAnalyzerPartitionId" }
        val duplicateAnalyzerPartitionNode = normalizedAnalyzerPartitions.groupingBy { it.nodeId }.eachCount().entries
            .firstOrNull { it.value > 1 }
            ?.key
        require(duplicateAnalyzerPartitionNode == null) { "duplicate Analyzer partition node: $duplicateAnalyzerPartitionNode" }

        val normalizedRuntimeCapabilities = runtimeCapabilities.sortedBy { it.capabilityId }
        val normalizedRuntimeProviders = runtimeProviders.sortedBy { it.providerId }
        val runtimeCapabilityIds = normalizedRuntimeCapabilities.mapTo(linkedSetOf()) { it.capabilityId }
        val unknownRuntimeCapabilityIds = normalizedRuntimeProviders
            .map { it.capabilityId }
            .filterNot(runtimeCapabilityIds::contains)
            .distinct()
            .sorted()
        require(unknownRuntimeCapabilityIds.isEmpty()) {
            "runtime providers reference unknown capabilities: ${unknownRuntimeCapabilityIds.joinToString()}"
        }

        val graphNodes = buildSet {
            addAll(CapabilityContractNodes.surfaceNodeIds)
            normalizedDescriptors.forEach { descriptor ->
                add(descriptor.capabilityId)
                descriptor.tasks.forEach { add("task.$it") }
            }
            normalizedRuntimeCapabilities.forEach { add(it.capabilityId) }
            normalizedRuntimeProviders.forEach { provider ->
                add(provider.providerId)
                add(provider.capabilityId)
            }
            addAll(listOf("agent.project", "agent.analysis"))
            normalizedAnalyzerPartitions.forEach { add(it.nodeId) }
            agentSections.forEach { add("agent.${it.id}") }
        }
        val unknownPathSeeds = pathRules.flatMap(CapabilityContractPathRule::seedNodes).distinct().filterNot(graphNodes::contains).sorted()
        require(unknownPathSeeds.isEmpty()) { "capability path rules reference unknown graph nodes: ${unknownPathSeeds.joinToString()}" }

        val edges = dependencyEdges(
            descriptors = normalizedDescriptors,
            publicTasks = normalizedTasks,
            agentSections = agentSections,
            runtimeCapabilities = normalizedRuntimeCapabilities,
            runtimeProviders = normalizedRuntimeProviders,
            analyzerPartitions = normalizedAnalyzerPartitions,
        )
        return CapabilityContractFacts(
            surfaces = CapabilityContractNodes.surfaces,
            publicTasks = normalizedTasks,
            agentSections = agentSections.sortedBy { it.id },
            agentCapabilityViews = listOf("effective", "supported"),
            agentStatusVocabulary = agentStatusVocabulary(),
            outputKinds = ArtifactOutputKind.entries.sortedBy(Enum<*>::name),
            analyzerOutputs = normalizedDescriptors
                .filter { PipelineExecutionLane.ANALYSIS in it.executionLanes }
                .filter { it.kind == PipelineCapabilityKind.GENERATOR || it.kind == PipelineCapabilityKind.ARTIFACT_ADDON }
                .map { it.providerId }
                .distinct()
                .sorted(),
            analyzerPartitions = normalizedAnalyzerPartitions,
            pipelineCapabilities = normalizedDescriptors.map(PipelineCapabilityFactProjection::supported),
            runtimeCapabilities = normalizedRuntimeCapabilities,
            runtimeProviders = normalizedRuntimeProviders,
            dependencyEdges = edges,
            propagationClosure = CapabilityContractGraph.transitiveClosure(edges),
            propagationClosureSemantics = "potential-impact",
            pathMatchPolicy = CapabilityContractPathMatchPolicy.FIRST_MATCH,
            pathRules = pathRules,
        )
    }

    private fun agentStatusVocabulary() = AgentStatusVocabulary(
        snapshot = enumWireValues<AgentSnapshotStatus>(),
        effectiveCapability = enumWireValues<AgentCapabilityStatus>(),
        validation = enumWireValues<AgentValidationStatus>(),
        evidenceFreshness = enumWireValues<AgentEvidenceFreshness>(),
        diagnosticLevel = enumWireValues<AgentDiagnosticLevel>(),
        runtimeFrameworkSupport = enumWireValues<AgentRuntimeFrameworkSupport>(),
        runtimeApplicationAssembly = enumWireValues<AgentRuntimeApplicationAssembly>(),
        runtimeObservation = enumWireValues<AgentRuntimeObservation>(),
        runtimeOperationalState = enumWireValues<AgentRuntimeOperationalState>(),
        runtimeVerification = enumWireValues<AgentRuntimeVerification>(),
        runtimeLiveStateSource = enumWireValues<AgentRuntimeLiveStateSource>(),
    )

    private inline fun <reified T : Enum<T>> enumWireValues(): List<String> =
        enumValues<T>().map(::agentContractEnumWireName).sorted()

    private fun normalizeDescriptors(
        descriptors: List<PipelineCapabilityDescriptor>,
    ): List<PipelineCapabilityDescriptor> {
        val duplicate = descriptors.groupingBy { it.capabilityId }.eachCount().entries
            .firstOrNull { it.value > 1 }
            ?.key
        require(duplicate == null) { "duplicate pipeline capability id: $duplicate" }
        return descriptors.sortedWith(compareBy({ it.capabilityId }, { it.providerId }))
    }

    private fun dependencyEdges(
        descriptors: List<PipelineCapabilityDescriptor>,
        publicTasks: List<PipelinePublicTaskContract>,
        agentSections: List<AgentSectionContract>,
        runtimeCapabilities: List<AgentRuntimeCapabilityFact>,
        runtimeProviders: List<AgentRuntimeProviderFact>,
        analyzerPartitions: List<AnalyzerPartitionContract>,
    ): List<CapabilityContractDependency> = buildList {
        addEdge(CapabilityContractNodes.RUNTIME, CapabilityContractNodes.GENERATOR)
        addEdge(CapabilityContractNodes.RUNTIME, CapabilityContractNodes.ANALYZER)
        addEdge(CapabilityContractNodes.GENERATOR, CapabilityContractNodes.ANALYZER)
        addEdge(CapabilityContractNodes.RUNTIME, CapabilityContractNodes.AGENT_FACTS)
        addEdge(CapabilityContractNodes.GENERATOR, CapabilityContractNodes.AGENT_FACTS)
        addEdge(CapabilityContractNodes.ANALYZER, CapabilityContractNodes.AGENT_FACTS)
        addEdge(CapabilityContractNodes.AGENT_FACTS, CapabilityContractNodes.PUBLIC_DOCS)
        addEdge(CapabilityContractNodes.AGENT_FACTS, CapabilityContractNodes.SKILL)

        descriptors.forEach { descriptor ->
            add(CapabilityContractDependency(descriptor.capabilityId, CapabilityContractNodes.AGENT_FACTS, CapabilityContractRelation.PROJECTS_TO))
            descriptor.tasks.forEach { task ->
                add(CapabilityContractDependency(descriptor.capabilityId, "task.$task", CapabilityContractRelation.EXPOSES_TASK))
            }
            descriptor.inputRequirements.forEach { requirement ->
                val condition = CapabilityContractCondition.inputRequirement(requirement)
                requirement.capabilityIds.distinct().sorted().forEach { requiredCapabilityId ->
                    add(CapabilityContractDependency(requiredCapabilityId, descriptor.capabilityId, CapabilityContractRelation.REQUIRES, condition))
                }
            }
            if (descriptor.kind == PipelineCapabilityKind.GENERATOR || descriptor.kind == PipelineCapabilityKind.ARTIFACT_ADDON) {
                addEdge(descriptor.capabilityId, CapabilityContractNodes.ANALYZER)
            }
            if (PipelineExecutionLane.ANALYSIS in descriptor.executionLanes) {
                addEdge(descriptor.capabilityId, CapabilityContractNodes.ANALYZER)
                add(CapabilityContractDependency(descriptor.capabilityId, "agent.analysis", CapabilityContractRelation.PROJECTS_TO))
            }
        }
        analyzerPartitions.forEach { partition ->
            add(CapabilityContractDependency(partition.sourceCapabilityId, partition.nodeId, CapabilityContractRelation.PROJECTS_TO))
            partition.consumerCapabilityIds.forEach { consumerCapabilityId ->
                add(CapabilityContractDependency(partition.nodeId, consumerCapabilityId, CapabilityContractRelation.REQUIRES))
            }
            add(CapabilityContractDependency(partition.nodeId, "agent.analysis", CapabilityContractRelation.PROJECTS_TO))
            addEdge(partition.nodeId, CapabilityContractNodes.ANALYZER)
        }
        runtimeCapabilities.forEach { capability ->
            add(CapabilityContractDependency(capability.capabilityId, CapabilityContractNodes.AGENT_FACTS, CapabilityContractRelation.PROJECTS_TO))
            addEdge(capability.capabilityId, CapabilityContractNodes.GENERATOR)
            addEdge(capability.capabilityId, CapabilityContractNodes.ANALYZER)
        }
        runtimeProviders.forEach { provider ->
            add(CapabilityContractDependency(provider.providerId, provider.capabilityId, CapabilityContractRelation.PROVIDES))
        }
        publicTasks.forEach { task ->
            add(CapabilityContractDependency("task.${task.name}", "agent.project", CapabilityContractRelation.PROJECTS_TO))
        }
        agentSections.forEach { section ->
            add(CapabilityContractDependency("agent.${section.id}", CapabilityContractNodes.PUBLIC_DOCS, CapabilityContractRelation.PROJECTS_TO))
            add(CapabilityContractDependency("agent.${section.id}", CapabilityContractNodes.SKILL, CapabilityContractRelation.PROJECTS_TO))
        }
    }.distinct().sortedWith(
        compareBy(
            { it.source },
            { it.target },
            { it.relation.name },
            { it.condition.applicability.name },
            { it.condition.requirementId.orEmpty() },
            { it.condition.capabilityIds.joinToString("|") },
            { it.condition.configurationPaths.joinToString("|") },
        )
    )

    private fun MutableList<CapabilityContractDependency>.addEdge(source: String, target: String) {
        add(CapabilityContractDependency(source, target, CapabilityContractRelation.PROPAGATES_TO))
    }
}

object CapabilityContractGraph {
    fun transitiveTargets(
        changedNodes: Collection<String>,
        edges: List<CapabilityContractDependency>,
    ): List<String> {
        val adjacency = edges.groupBy { it.source }.mapValues { (_, values) -> values.map { it.target } }
        val visited = linkedSetOf<String>()
        val queue = ArrayDeque(changedNodes.distinct())
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            adjacency[current].orEmpty().sorted().forEach { target ->
                if (target !in changedNodes && visited.add(target)) {
                    queue.addLast(target)
                }
            }
        }
        return visited.sorted()
    }

    fun transitiveClosure(edges: List<CapabilityContractDependency>): Map<String, List<String>> =
        edges.map { it.source }.distinct().sorted().associateWith { source ->
            transitiveTargets(listOf(source), edges)
        }
}

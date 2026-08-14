package com.only4.cap4k.plugin.pipeline.api

const val CAP4K_AGENT_CONTRACT_VERSION: Int = 4
const val CAP4K_AGENT_MANIFEST_SCHEMA: String = "cap4k.agent.manifest.v1"
const val CAP4K_AGENT_PROJECT_SCHEMA: String = "cap4k.agent.project.v1"
const val CAP4K_AGENT_CAPABILITIES_SCHEMA: String = "cap4k.agent.capabilities.v1"
const val CAP4K_AGENT_INPUTS_SCHEMA: String = "cap4k.agent.inputs.v1"
const val CAP4K_AGENT_OWNERSHIP_SCHEMA: String = "cap4k.agent.ownership.v1"
const val CAP4K_AGENT_RUNTIME_SCHEMA: String = "cap4k.agent.runtime.v3"
const val CAP4K_AGENT_ANALYSIS_SCHEMA: String = "cap4k.agent.analysis.v2"
const val CAP4K_AGENT_DIAGNOSTICS_SCHEMA: String = "cap4k.agent.diagnostics.v1"
const val CAP4K_PLAN_EVIDENCE_SCHEMA: String = "cap4k.plan-evidence.v1"

fun agentContractEnumWireName(value: Enum<*>): String = value.name.lowercase()

enum class AgentSnapshotStatus {
    OK,
    PARTIAL,
    INVALID,
    UNAVAILABLE,
}

enum class AgentCapabilityStatus {
    CONFIGURED,
    READY,
    BLOCKED,
    NOT_APPLICABLE,
}

enum class AgentValidationStatus {
    VERIFIED,
    UNKNOWN,
    FAILED,
}

enum class AgentEvidenceFreshness {
    FRESH,
    STALE,
    UNKNOWN,
    MISSING,
}

enum class AgentDiagnosticLevel {
    ERROR,
    WARNING,
    INFO,
}

data class PlanEvidence(
    val schema: String = CAP4K_PLAN_EVIDENCE_SCHEMA,
    val configurationIdentity: String,
    val localInputIdentity: String? = null,
    val containsLiveExternalInput: Boolean = false,
) {
    init {
        require(schema == CAP4K_PLAN_EVIDENCE_SCHEMA) { "unsupported plan evidence schema: $schema" }
        require(configurationIdentity.isNotBlank()) { "plan evidence configuration identity must not be blank" }
        require(localInputIdentity == null || localInputIdentity.isNotBlank()) {
            "plan evidence local input identity must be null or non-blank"
        }
    }
}

data class AgentProjectModule(
    val role: String,
    val path: String,
    val gradleProjectPath: String? = null,
    val exists: Boolean,
)

data class AgentProjectSummary(
    val name: String,
    val path: String = ".",
    val group: String = "",
    val version: String = "",
    val basePackage: String? = null,
    val layout: ProjectLayout? = null,
    val modules: List<AgentProjectModule> = emptyList(),
    val publicTasks: List<String> = emptyList(),
)

data class AgentSectionReference(
    val id: String,
    val path: String,
    val schema: String,
    val status: AgentSnapshotStatus,
    val sha256: String,
    val counts: Map<String, Int> = emptyMap(),
    val reason: String? = null,
)

data class AgentDiagnosticCounts(
    val error: Int = 0,
    val warning: Int = 0,
    val info: Int = 0,
)

data class AgentManifest(
    val schema: String = CAP4K_AGENT_MANIFEST_SCHEMA,
    val contractVersion: Int = CAP4K_AGENT_CONTRACT_VERSION,
    val cap4kVersion: String,
    val snapshotId: String,
    val status: AgentSnapshotStatus,
    val project: AgentProjectSummary,
    val sections: List<AgentSectionReference>,
    val diagnosticCounts: AgentDiagnosticCounts,
    val recommendedSections: List<String> = emptyList(),
)

data class AgentProjectSection(
    val schema: String = CAP4K_AGENT_PROJECT_SCHEMA,
    val status: AgentSnapshotStatus,
    val project: AgentProjectSummary,
    val reason: String? = null,
)

data class AgentSupportedCapability(
    val capabilityId: String,
    val providerId: String,
    val displayName: String,
    val kind: PipelineCapabilityKind,
    val provenance: PipelineCapabilityProvenance,
    val activation: PipelineCapabilityActivation = PipelineCapabilityActivation.EXPLICIT_CONFIGURATION,
    val tacticalCarriers: List<String>,
    val executionLanes: List<PipelineExecutionLane>,
    val tasks: List<String>,
    val inputRequirements: List<PipelineInputRequirement>,
    val outputKinds: List<ArtifactOutputKind>,
    val boundaries: List<PipelineCapabilityBoundary>,
    val metadataLevel: PipelineCapabilityMetadataLevel,
)

data class AgentCapabilityObservation(
    val capabilityId: String,
    val providerId: String,
    val configured: Boolean,
    val applicable: Boolean = configured,
    val validation: AgentValidationStatus = AgentValidationStatus.UNKNOWN,
    val diagnosticIds: List<String> = emptyList(),
    val nextActions: List<String> = emptyList(),
)

data class AgentEffectiveCapability(
    val capabilityId: String,
    val providerId: String,
    val status: AgentCapabilityStatus,
    val diagnosticIds: List<String> = emptyList(),
    val nextActions: List<String> = emptyList(),
)

data class AgentCapabilitiesSection(
    val schema: String = CAP4K_AGENT_CAPABILITIES_SCHEMA,
    val status: AgentSnapshotStatus,
    val supported: List<AgentSupportedCapability>,
    val effective: List<AgentEffectiveCapability>,
    val reason: String? = null,
)

data class AgentOptionSummary(
    val configuredKeys: List<String> = emptyList(),
    val sensitiveKeys: List<String> = emptyList(),
)

data class AgentInput(
    val id: String,
    val providerId: String,
    val safety: PipelineInputSafety,
    val configured: Boolean,
    val locations: List<String> = emptyList(),
    val exists: Boolean? = null,
    val readable: Boolean? = null,
    val identity: String? = null,
    val options: AgentOptionSummary = AgentOptionSummary(),
    val requiredBy: List<String> = emptyList(),
    val planTask: String? = null,
)

data class AgentInputsSection(
    val schema: String = CAP4K_AGENT_INPUTS_SCHEMA,
    val status: AgentSnapshotStatus,
    val inputs: List<AgentInput>,
    val reason: String? = null,
)

data class AgentEvidence(
    val kind: String,
    val path: String,
    val freshness: AgentEvidenceFreshness,
    val currentConfigurationIdentity: String? = null,
    val evidenceConfigurationIdentity: String? = null,
    val currentLocalInputIdentity: String? = null,
    val evidenceLocalInputIdentity: String? = null,
    val reason: String? = null,
    val nextAction: String? = null,
)

data class AgentOwnershipItem(
    val generatorId: String,
    val moduleRole: String,
    val templateId: String,
    val outputPath: String,
    val outputKind: ArtifactOutputKind,
    val conflictPolicy: ConflictPolicy,
    val resolvedOutputRoot: String = "",
)

data class AgentOwnershipSection(
    val schema: String = CAP4K_AGENT_OWNERSHIP_SCHEMA,
    val status: AgentSnapshotStatus,
    val items: List<AgentOwnershipItem>,
    val managedRoots: Map<String, String> = emptyMap(),
    val evidence: List<AgentEvidence> = emptyList(),
    val reason: String? = null,
)

data class AgentRuntimeExtension(
    val id: String,
    val displayName: String,
    val spiVersion: Int,
    val contributionIds: List<String>,
    val configuredOptionKeys: List<String> = emptyList(),
    val sensitiveOptionKeys: List<String> = emptyList(),
)

enum class AgentEventHandlerAuthoring {
    METHOD_LEVEL_EVENT_LISTENER,
}

enum class AgentEventHandlerExecution {
    SYNCHRONOUS_SEQUENTIAL_FAIL_FAST,
}

enum class AgentEventHandlerEqualOrder {
    UNSPECIFIED,
}

enum class AgentEventHandlerReturnType {
    UNIT_OR_VOID,
}

enum class AgentEventHandlerManagedAsyncCompletion {
    WAIT_BEFORE_HANDLER_COMPLETION,
}

enum class AgentEventHandlerManagedAsyncFailure {
    FAIL_HANDLER,
}

data class AgentEventHandlerOrderingContract(
    val annotation: String = "org.springframework.core.annotation.Order",
    val target: String = "method",
    val lowerValuesFirst: Boolean = true,
    val equalValues: AgentEventHandlerEqualOrder = AgentEventHandlerEqualOrder.UNSPECIFIED,
)

data class AgentEventHandlerManagedAsyncContract(
    val trackedOperations: List<String> = listOf(
        "Mediator.queries.askAsync",
        "Mediator.capabilities.callAsync",
    ),
    val completion: AgentEventHandlerManagedAsyncCompletion =
        AgentEventHandlerManagedAsyncCompletion.WAIT_BEFORE_HANDLER_COMPLETION,
    val failure: AgentEventHandlerManagedAsyncFailure = AgentEventHandlerManagedAsyncFailure.FAIL_HANDLER,
)

data class AgentEventHandlerContract(
    val authoring: AgentEventHandlerAuthoring = AgentEventHandlerAuthoring.METHOD_LEVEL_EVENT_LISTENER,
    val eventKinds: List<String> = listOf("domain_event", "integration_event"),
    val execution: AgentEventHandlerExecution = AgentEventHandlerExecution.SYNCHRONOUS_SEQUENTIAL_FAIL_FAST,
    val ordering: AgentEventHandlerOrderingContract = AgentEventHandlerOrderingContract(),
    val supportsCondition: Boolean = true,
    val returnType: AgentEventHandlerReturnType = AgentEventHandlerReturnType.UNIT_OR_VOID,
    val forbidden: List<String> = listOf(
        "async_annotation",
        "suspending_function",
        "transactional_event_listener",
        "default_execution_false",
        "multiple_event_declarations",
        "polymorphic_subscription",
        "non_unit_or_void_return",
    ),
    val managedAsyncCompletion: AgentEventHandlerManagedAsyncContract = AgentEventHandlerManagedAsyncContract(),
)

enum class AgentRuntimeFrameworkSupport {
    SUPPORTED,
}

enum class AgentRuntimeApplicationAssembly {
    UNKNOWN,
}

enum class AgentRuntimeObservation {
    NOT_PERFORMED,
}

enum class AgentRuntimeOperationalState {
    UNKNOWN,
}

enum class AgentRuntimeVerification {
    NOT_PERFORMED,
}

enum class AgentRuntimeLiveStateSource {
    RUNTIME_PROVIDER_STATE_REGISTRY,
}

data class AgentRuntimeOwnership(
    val contractModule: String,
    val implementationModule: String? = null,
    val starterModule: String? = null,
)

data class AgentRuntimeCapabilityFact(
    val capabilityId: String,
    val displayName: String,
    val ownership: AgentRuntimeOwnership,
    val frameworkSupport: AgentRuntimeFrameworkSupport = AgentRuntimeFrameworkSupport.SUPPORTED,
    val applicationAssembly: AgentRuntimeApplicationAssembly = AgentRuntimeApplicationAssembly.UNKNOWN,
    val runtimeObservation: AgentRuntimeObservation = AgentRuntimeObservation.NOT_PERFORMED,
    val verification: AgentRuntimeVerification = AgentRuntimeVerification.NOT_PERFORMED,
    val providerIds: List<String> = emptyList(),
)

data class AgentRuntimeProviderFact(
    val providerId: String,
    val capabilityId: String,
    val displayName: String,
    val ownership: AgentRuntimeOwnership,
    val frameworkSupport: AgentRuntimeFrameworkSupport = AgentRuntimeFrameworkSupport.SUPPORTED,
    val applicationAssembly: AgentRuntimeApplicationAssembly = AgentRuntimeApplicationAssembly.UNKNOWN,
    val runtimeObservation: AgentRuntimeObservation = AgentRuntimeObservation.NOT_PERFORMED,
    val operationalState: AgentRuntimeOperationalState = AgentRuntimeOperationalState.UNKNOWN,
    val verification: AgentRuntimeVerification = AgentRuntimeVerification.NOT_PERFORMED,
    val liveStateSource: AgentRuntimeLiveStateSource =
        AgentRuntimeLiveStateSource.RUNTIME_PROVIDER_STATE_REGISTRY,
)

data class AgentRuntimeSection(
    val schema: String = CAP4K_AGENT_RUNTIME_SCHEMA,
    val status: AgentSnapshotStatus,
    val capabilities: List<AgentRuntimeCapabilityFact> = emptyList(),
    val providers: List<AgentRuntimeProviderFact> = emptyList(),
    val eventHandler: AgentEventHandlerContract = AgentEventHandlerContract(),
    val extensions: List<AgentRuntimeExtension> = emptyList(),
    val boundaries: Map<String, List<PipelineCapabilityBoundary>> = emptyMap(),
    val externalIoSafe: Boolean = true,
    val reason: String? = null,
)

object AgentAnalysisPartitionIds {
    const val GRAPH: String = "graph"
    const val DESIGN_PROJECTION: String = "designProjection"
    const val AGGREGATE_STRUCTURE: String = "aggregateStructure"

    val ALL: List<String> = listOf(GRAPH, DESIGN_PROJECTION, AGGREGATE_STRUCTURE)
}

data class AgentAnalysisSource(
    val id: String,
    val path: String,
) {
    init {
        require(id.isNotBlank()) { "analysis source id must not be blank" }
        require(path.isNotBlank()) { "analysis source path must not be blank" }
    }
}

data class AgentAnalysisPartition(
    val id: String,
    val requested: Boolean,
    val status: AgentSnapshotStatus,
    val counts: Map<String, Int> = emptyMap(),
    val sources: List<AgentAnalysisSource> = emptyList(),
    val freshness: AgentEvidenceFreshness = AgentEvidenceFreshness.UNKNOWN,
    val plannedOutputPaths: List<String> = emptyList(),
    val availableOutputPaths: List<String> = emptyList(),
    val diagnosticIds: List<String> = emptyList(),
    val nextAction: String? = null,
    val reason: String? = null,
) {
    init {
        require(id in AgentAnalysisPartitionIds.ALL) { "unsupported analysis partition id: $id" }
        require(counts.values.all { it >= 0 }) { "analysis partition counts must not be negative" }
    }
}

data class AgentAnalysisSection(
    val schema: String = CAP4K_AGENT_ANALYSIS_SCHEMA,
    val status: AgentSnapshotStatus,
    val configured: Boolean,
    val inputDirs: List<String> = emptyList(),
    val evidence: AgentEvidence? = null,
    val partitions: List<AgentAnalysisPartition> = emptyList(),
    val reason: String? = null,
) {
    init {
        require(schema == CAP4K_AGENT_ANALYSIS_SCHEMA) { "unsupported agent analysis schema: $schema" }
        val ids = partitions.map(AgentAnalysisPartition::id)
        require(ids.distinct().size == ids.size) { "duplicate agent analysis partition id" }
        if (configured) {
            require(ids.toSet() == AgentAnalysisPartitionIds.ALL.toSet()) {
                "configured analysis must expose graph, designProjection, and aggregateStructure partitions"
            }
        }
    }
}

data class AgentDiagnostic(
    val id: String,
    val level: AgentDiagnosticLevel,
    val status: String = "active",
    val stage: String,
    val capabilityId: String? = null,
    val inputPath: String? = null,
    val artifactPath: String? = null,
    val message: String,
    val hint: String? = null,
    val proves: String? = null,
)

data class AgentDiagnosticsSection(
    val schema: String = CAP4K_AGENT_DIAGNOSTICS_SCHEMA,
    val status: AgentSnapshotStatus,
    val diagnostics: List<AgentDiagnostic>,
    val reason: String? = null,
)

data class AgentSnapshotSections(
    val project: AgentProjectSection,
    val capabilities: AgentCapabilitiesSection,
    val inputs: AgentInputsSection,
    val ownership: AgentOwnershipSection,
    val runtime: AgentRuntimeSection,
    val analysis: AgentAnalysisSection,
    val diagnostics: AgentDiagnosticsSection,
)

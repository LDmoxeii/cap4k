package com.only4.cap4k.plugin.pipeline.api

const val CAP4K_AGENT_CONTRACT_VERSION: Int = 1
const val CAP4K_AGENT_MANIFEST_SCHEMA: String = "cap4k.agent.manifest.v1"
const val CAP4K_AGENT_PROJECT_SCHEMA: String = "cap4k.agent.project.v1"
const val CAP4K_AGENT_CAPABILITIES_SCHEMA: String = "cap4k.agent.capabilities.v1"
const val CAP4K_AGENT_INPUTS_SCHEMA: String = "cap4k.agent.inputs.v1"
const val CAP4K_AGENT_OWNERSHIP_SCHEMA: String = "cap4k.agent.ownership.v1"
const val CAP4K_AGENT_RUNTIME_SCHEMA: String = "cap4k.agent.runtime.v1"
const val CAP4K_AGENT_ANALYSIS_SCHEMA: String = "cap4k.agent.analysis.v1"
const val CAP4K_AGENT_DIAGNOSTICS_SCHEMA: String = "cap4k.agent.diagnostics.v1"
const val CAP4K_PLAN_EVIDENCE_SCHEMA: String = "cap4k.plan-evidence.v1"

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

data class AgentRuntimeSection(
    val schema: String = CAP4K_AGENT_RUNTIME_SCHEMA,
    val status: AgentSnapshotStatus,
    val extensions: List<AgentRuntimeExtension> = emptyList(),
    val boundaries: Map<String, List<PipelineCapabilityBoundary>> = emptyMap(),
    val externalIoSafe: Boolean = true,
    val reason: String? = null,
)

data class AgentAnalysisSection(
    val schema: String = CAP4K_AGENT_ANALYSIS_SCHEMA,
    val status: AgentSnapshotStatus,
    val configured: Boolean,
    val inputDirs: List<String> = emptyList(),
    val nodeCount: Int? = null,
    val edgeCount: Int? = null,
    val designElementCount: Int? = null,
    val evidence: AgentEvidence? = null,
    val plannedOutputPaths: List<String> = emptyList(),
    val availableOutputPaths: List<String> = emptyList(),
    val nextAction: String? = null,
    val reason: String? = null,
)

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

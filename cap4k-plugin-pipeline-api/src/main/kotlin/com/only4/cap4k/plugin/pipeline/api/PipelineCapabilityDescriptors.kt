package com.only4.cap4k.plugin.pipeline.api

private val CapabilityIdPattern = Regex("[a-z0-9]+(?:[.-][a-z0-9]+)*")

enum class PipelineCapabilityKind {
    SOURCE,
    GENERATOR,
    ARTIFACT_ADDON,
    MANAGED_FIELD_POLICY,
}

enum class PipelineCapabilityMetadataLevel {
    COMPLETE,
    IDENTITY_ONLY,
}

enum class PipelineCapabilityActivation {
    EXPLICIT_CONFIGURATION,
    INPUT_DRIVEN,
    INSTALLED,
}

enum class PipelineCapabilityProvenanceKind {
    BUILT_IN,
    EXTENSION,
    UNKNOWN,
}

data class PipelineCapabilityProvenance(
    val kind: PipelineCapabilityProvenanceKind,
    val owner: String,
) {
    init {
        require(owner.isNotBlank()) { "pipeline capability provenance owner must not be blank" }
    }

    companion object {
        fun builtIn(module: String): PipelineCapabilityProvenance =
            PipelineCapabilityProvenance(PipelineCapabilityProvenanceKind.BUILT_IN, module)

        fun extension(extensionId: String): PipelineCapabilityProvenance =
            PipelineCapabilityProvenance(PipelineCapabilityProvenanceKind.EXTENSION, extensionId)

        fun unknown(): PipelineCapabilityProvenance =
            PipelineCapabilityProvenance(PipelineCapabilityProvenanceKind.UNKNOWN, "unknown")
    }
}

enum class PipelineExecutionLane {
    AUTHORING,
    GENERATED_SOURCE,
    ANALYSIS,
}

enum class PipelineTaskMutationBoundary {
    BUILD_EVIDENCE_ONLY,
    MANAGED_OUTPUTS,
}

data class PipelinePublicTaskContract(
    val name: String,
    val mutationBoundary: PipelineTaskMutationBoundary,
    val readsLiveExternalInput: Boolean,
)

object PipelinePublicTasks {
    const val PLAN = "cap4kPlan"
    const val GENERATE = "cap4kGenerate"
    const val GENERATE_SOURCES = "cap4kGenerateSources"
    const val ANALYSIS_PLAN = "cap4kAnalysisPlan"
    const val ANALYSIS_GENERATE = "cap4kAnalysisGenerate"
    const val AGENT_SNAPSHOT = "cap4kAgentSnapshot"

    val contracts: List<PipelinePublicTaskContract> = listOf(
        PipelinePublicTaskContract(PLAN, PipelineTaskMutationBoundary.BUILD_EVIDENCE_ONLY, readsLiveExternalInput = true),
        PipelinePublicTaskContract(GENERATE, PipelineTaskMutationBoundary.MANAGED_OUTPUTS, readsLiveExternalInput = true),
        PipelinePublicTaskContract(GENERATE_SOURCES, PipelineTaskMutationBoundary.MANAGED_OUTPUTS, readsLiveExternalInput = true),
        PipelinePublicTaskContract(ANALYSIS_PLAN, PipelineTaskMutationBoundary.BUILD_EVIDENCE_ONLY, readsLiveExternalInput = false),
        PipelinePublicTaskContract(ANALYSIS_GENERATE, PipelineTaskMutationBoundary.MANAGED_OUTPUTS, readsLiveExternalInput = false),
        PipelinePublicTaskContract(AGENT_SNAPSHOT, PipelineTaskMutationBoundary.BUILD_EVIDENCE_ONLY, readsLiveExternalInput = false),
    )

    val all: List<String> = contracts.map(PipelinePublicTaskContract::name)
}

enum class PipelineInputSafety {
    LOCAL_PROJECT,
    LIVE_EXTERNAL,
}

enum class PipelineInputRequirementMatch {
    ALL,
    ANY,
}

data class PipelineInputRequirement(
    val id: String,
    val capabilityIds: List<String> = emptyList(),
    val configurationPaths: List<String> = emptyList(),
    val match: PipelineInputRequirementMatch = PipelineInputRequirementMatch.ALL,
    val safety: PipelineInputSafety = PipelineInputSafety.LOCAL_PROJECT,
) {
    init {
        requireStableCapabilityId(id, "pipeline input requirement id")
        require(capabilityIds.isNotEmpty() || configurationPaths.isNotEmpty()) {
            "pipeline input requirement $id must declare capabilityIds or configurationPaths"
        }
        capabilityIds.forEach { capabilityId ->
            requireStableCapabilityId(capabilityId, "pipeline input requirement capability id")
        }
        require(configurationPaths.none(String::isBlank)) {
            "pipeline input requirement $id contains a blank configuration path"
        }
    }
}

enum class PipelineBoundaryKind {
    INPUT,
    GENERATION,
    HANDWRITTEN,
    RUNTIME,
    PROVIDER,
    ANALYZER,
}

object PipelineBoundaryAuthorities {
    const val PROJECT_INPUT = "project-input"
    const val PIPELINE_SOURCE = "pipeline-source"
    const val PIPELINE_GENERATOR = "pipeline-generator"
    const val PROJECT_HANDWRITTEN = "project-handwritten"
    const val CAP4K_RUNTIME = "cap4k-runtime"
    const val RUNTIME_PROVIDER = "runtime-provider"
    const val ANALYZER_OBSERVATION = "analyzer-observation"
    const val NONE = "none"
}

data class PipelineCapabilityBoundary(
    val kind: PipelineBoundaryKind,
    val authority: String,
) {
    init {
        require(authority.isNotBlank()) { "pipeline capability boundary authority must not be blank" }
    }
}

data class PipelineCapabilityDescriptor(
    val capabilityId: String,
    val providerId: String,
    val displayName: String,
    val kind: PipelineCapabilityKind,
    val provenance: PipelineCapabilityProvenance,
    val activation: PipelineCapabilityActivation = PipelineCapabilityActivation.EXPLICIT_CONFIGURATION,
    val tacticalCarriers: List<String> = emptyList(),
    val executionLanes: List<PipelineExecutionLane> = emptyList(),
    val tasks: List<String> = emptyList(),
    val inputRequirements: List<PipelineInputRequirement> = emptyList(),
    val outputKinds: List<ArtifactOutputKind> = emptyList(),
    val boundaries: List<PipelineCapabilityBoundary> = emptyList(),
    val metadataLevel: PipelineCapabilityMetadataLevel = PipelineCapabilityMetadataLevel.COMPLETE,
) {
    init {
        requireStableCapabilityId(capabilityId, "pipeline capability id")
        requireStableCapabilityId(providerId, "pipeline provider id")
        require(displayName.isNotBlank()) { "pipeline capability $capabilityId display name must not be blank" }
        require(tacticalCarriers.none(String::isBlank)) {
            "pipeline capability $capabilityId contains a blank tactical carrier"
        }
        require(executionLanes.distinct().size == executionLanes.size) {
            "pipeline capability $capabilityId contains duplicate execution lanes"
        }
        require(tasks.none(String::isBlank)) {
            "pipeline capability $capabilityId contains a blank task"
        }
        require(tasks.distinct().size == tasks.size) {
            "pipeline capability $capabilityId contains duplicate tasks"
        }
        require(inputRequirements.map { it.id }.distinct().size == inputRequirements.size) {
            "pipeline capability $capabilityId contains duplicate input requirement ids"
        }
        require(outputKinds.distinct().size == outputKinds.size) {
            "pipeline capability $capabilityId contains duplicate output kinds"
        }
        require(boundaries.map { it.kind to it.authority }.distinct().size == boundaries.size) {
            "pipeline capability $capabilityId contains duplicate boundaries"
        }
    }

    companion object {
        fun builtIn(
            providerId: String,
            displayName: String,
            kind: PipelineCapabilityKind,
            module: String,
            activation: PipelineCapabilityActivation = PipelineCapabilityActivation.EXPLICIT_CONFIGURATION,
            tacticalCarriers: List<String> = emptyList(),
            executionLanes: List<PipelineExecutionLane>,
            tasks: List<String>,
            inputRequirements: List<PipelineInputRequirement> = emptyList(),
            outputKinds: List<ArtifactOutputKind> = emptyList(),
            boundaries: List<PipelineCapabilityBoundary>,
        ): PipelineCapabilityDescriptor =
            PipelineCapabilityDescriptor(
                capabilityId = "pipeline.${kind.name.lowercase().replace('_', '-')}.$providerId",
                providerId = providerId,
                displayName = displayName,
                kind = kind,
                provenance = PipelineCapabilityProvenance.builtIn(module),
                activation = activation,
                tacticalCarriers = tacticalCarriers,
                executionLanes = executionLanes,
                tasks = tasks,
                inputRequirements = inputRequirements,
                outputKinds = outputKinds,
                boundaries = boundaries,
            )

        fun identityOnly(
            providerId: String,
            kind: PipelineCapabilityKind,
            activation: PipelineCapabilityActivation = PipelineCapabilityActivation.EXPLICIT_CONFIGURATION,
        ): PipelineCapabilityDescriptor =
            PipelineCapabilityDescriptor(
                capabilityId = "pipeline.${kind.name.lowercase().replace('_', '-')}.$providerId",
                providerId = providerId,
                displayName = providerId,
                kind = kind,
                provenance = PipelineCapabilityProvenance.unknown(),
                activation = activation,
                metadataLevel = PipelineCapabilityMetadataLevel.IDENTITY_ONLY,
            )
    }
}

private fun requireStableCapabilityId(value: String, label: String) {
    require(CapabilityIdPattern.matches(value)) {
        "$label must use lowercase dot/dash separated segments: $value"
    }
}

package com.only4.cap4k.plugin.pipeline.gradle

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.only4.cap4k.plugin.pipeline.agent.AgentCredentialRedactor
import com.only4.cap4k.plugin.pipeline.agent.AgentFreshnessEvaluator
import com.only4.cap4k.plugin.pipeline.agent.AgentHashing
import com.only4.cap4k.plugin.pipeline.agent.AgentIdentity
import com.only4.cap4k.plugin.pipeline.agent.AgentSnapshotCodec
import com.only4.cap4k.plugin.pipeline.agent.AgentSnapshotRequest
import com.only4.cap4k.plugin.pipeline.agent.AgentSnapshotService
import com.only4.cap4k.plugin.pipeline.agent.RuntimeAgentFactsCatalog
import com.only4.cap4k.plugin.pipeline.api.AgentAnalysisPartition
import com.only4.cap4k.plugin.pipeline.api.AgentAnalysisPartitionIds
import com.only4.cap4k.plugin.pipeline.api.AgentAnalysisSection
import com.only4.cap4k.plugin.pipeline.api.AgentAnalysisSource
import com.only4.cap4k.plugin.pipeline.api.AgentCapabilityObservation
import com.only4.cap4k.plugin.pipeline.api.AgentDiagnostic
import com.only4.cap4k.plugin.pipeline.api.AgentDiagnosticLevel
import com.only4.cap4k.plugin.pipeline.api.AgentEvidence
import com.only4.cap4k.plugin.pipeline.api.AgentEvidenceFreshness
import com.only4.cap4k.plugin.pipeline.api.AgentInput
import com.only4.cap4k.plugin.pipeline.api.AgentInputsSection
import com.only4.cap4k.plugin.pipeline.api.AgentOwnershipItem
import com.only4.cap4k.plugin.pipeline.api.AgentOwnershipSection
import com.only4.cap4k.plugin.pipeline.api.AgentProjectModule
import com.only4.cap4k.plugin.pipeline.api.AgentProjectSection
import com.only4.cap4k.plugin.pipeline.api.AgentProjectSummary
import com.only4.cap4k.plugin.pipeline.api.AgentRuntimeExtension
import com.only4.cap4k.plugin.pipeline.api.AgentRuntimeSection
import com.only4.cap4k.plugin.pipeline.api.AgentSnapshotStatus
import com.only4.cap4k.plugin.pipeline.api.AgentValidationStatus
import com.only4.cap4k.plugin.pipeline.api.ArtifactAddonProvider
import com.only4.cap4k.plugin.pipeline.api.ArtifactOutputKind
import com.only4.cap4k.plugin.pipeline.api.ArtifactPlanItem
import com.only4.cap4k.plugin.pipeline.api.CAP4K_PLAN_EVIDENCE_SCHEMA
import com.only4.cap4k.plugin.pipeline.api.ConflictPolicy
import com.only4.cap4k.plugin.pipeline.api.AnalyzerSnapshot
import com.only4.cap4k.plugin.pipeline.api.analyzerSnapshotStatus
import com.only4.cap4k.plugin.pipeline.api.analyzerSourceIdentity
import com.only4.cap4k.plugin.pipeline.api.ManagedFieldPolicyProvider
import com.only4.cap4k.plugin.pipeline.api.PipelineCapabilityDescriptor
import com.only4.cap4k.plugin.pipeline.api.PipelineCapabilityActivation
import com.only4.cap4k.plugin.pipeline.api.PipelineCapabilityKind
import com.only4.cap4k.plugin.pipeline.api.PipelineCapabilityProvenance
import com.only4.cap4k.plugin.pipeline.api.PipelineInputRequirement
import com.only4.cap4k.plugin.pipeline.api.PipelineInputRequirementMatch
import com.only4.cap4k.plugin.pipeline.api.PipelineInputSafety
import com.only4.cap4k.plugin.pipeline.api.PipelineDiagnostics
import com.only4.cap4k.plugin.pipeline.api.PlanEvidence
import com.only4.cap4k.plugin.pipeline.api.PlanOutcome
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig
import com.only4.cap4k.plugin.pipeline.api.ProjectLayout
import com.only4.cap4k.plugin.pipeline.api.SourceProvider
import com.only4.cap4k.plugin.pipeline.json.PipelineJson
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

@DisableCachingByDefault(because = "Always refreshes a diagnostic snapshot from broad live project state")
abstract class Cap4kAgentSnapshotTask : DefaultTask() {
    init {
        outputs.upToDateWhen { false }
    }

    @get:Internal
    lateinit var extension: Cap4kExtension

    @get:Internal
    lateinit var configFactory: Cap4kProjectConfigFactory

    @get:Classpath
    val pipelineExtensionClasspath: FileCollection
        get() = pipelineExtensionClasspath(project)

    @get:OutputDirectory
    val outputDirectory: File
        get() = project.layout.buildDirectory.dir("cap4k/agent").get().asFile

    @TaskAction
    fun writeSnapshot() {
        val redactor = AgentCredentialRedactor()
        val identity = AgentIdentity(redactor)
        val diagnostics = mutableListOf<AgentDiagnostic>()
        val config = try {
            configFactory.build(project, extension)
        } catch (failure: Exception) {
            diagnostics += diagnostic(
                id = "project-configuration-invalid",
                level = AgentDiagnosticLevel.ERROR,
                stage = "project-configuration",
                message = redactor.redact(failure.message ?: failure.javaClass.simpleName),
                hint = "Fix the cap4k project configuration and run cap4kAgentSnapshot again.",
            )
            null
        }

        val extensionInspection = inspectExtensions(config, redactor, diagnostics)
        val sourceProviders = (builtInAuthoringSourceProviders() + builtInAnalysisSourceProviders())
            .associateBy { provider -> provider.id }
        val generatorProviders = builtInAuthoringGeneratorProviders() + builtInAnalysisGeneratorProviders()
        val descriptors = normalizeCapabilityDescriptors(
            sourceProviders.values.map { provider -> provider.descriptor } +
                generatorProviders.map { provider -> provider.descriptor } +
                extensionInspection.capabilityDescriptors,
            diagnostics,
        )
        val localInputs = config?.let { validConfig ->
            try {
                collectLocalInputs(project, validConfig, sourceProviders)
            } catch (failure: Exception) {
                diagnostics += diagnostic(
                    id = "local-input-inspection-failed",
                    level = AgentDiagnosticLevel.ERROR,
                    stage = "input-inspection",
                    message = redactor.redact(failure.message ?: failure.javaClass.simpleName),
                    hint = "Fix unreadable local project inputs and run cap4kAgentSnapshot again.",
                )
                null
            }
        }
        val projectSection = projectSection(config)
        val inputsSection = inputsSection(
            config,
            descriptors,
            sourceProviders,
            localInputs,
            identity,
            redactor,
            diagnostics,
        )
        val ownership = ownershipSection(config, localInputs, identity, redactor, diagnostics)
        val analysis = analysisSection(config, sourceProviders, localInputs, identity, redactor, diagnostics)
        verifyLocalInputsUnchanged(config, sourceProviders, localInputs, redactor, diagnostics)
        val observations = capabilityObservations(config, descriptors, diagnostics)
        val runtime = runtimeSection(descriptors, extensionInspection)
        val sections = AgentSnapshotService().assemble(
            AgentSnapshotRequest(
                project = projectSection,
                capabilityDescriptors = descriptors,
                capabilityObservations = observations,
                inputs = inputsSection,
                ownership = ownership,
                runtime = runtime,
                analysis = analysis,
                diagnostics = diagnostics,
            )
        )
        val encoded = AgentSnapshotCodec(redactor).encode(
            cap4kVersion = cap4kVersion(),
            sections = sections,
        )
        writeFiles(encoded.filesByPath)

        if (encoded.manifest.status == AgentSnapshotStatus.INVALID ||
            encoded.manifest.status == AgentSnapshotStatus.UNAVAILABLE
        ) {
            throw GradleException(
                "Cap4k Agent API snapshot is ${encoded.manifest.status.name.lowercase()}; " +
                    "see ${outputDirectory.resolve("diagnostics.json").absolutePath}"
            )
        }
    }

    private fun inspectExtensions(
        config: ProjectConfig?,
        redactor: AgentCredentialRedactor,
        diagnostics: MutableList<AgentDiagnostic>,
    ): ExtensionInspection {
        val runtime = try {
            loadPipelineExtensionRuntime(project, config ?: ProjectConfig())
        } catch (failure: Throwable) {
            if (failure is VirtualMachineError || failure is ThreadDeath) {
                throw failure
            }
            diagnostics += diagnostic(
                id = "pipeline-extension-inspection-failed",
                level = AgentDiagnosticLevel.ERROR,
                stage = "pipeline-extension",
                message = redactor.redact(failure.message ?: failure.javaClass.simpleName),
                hint = "Fix the cap4kPipelineExtension classpath or provider configuration.",
            )
            return ExtensionInspection(
                status = AgentSnapshotStatus.INVALID,
                reason = "Pipeline Extension inspection failed.",
                externalIoSafe = false,
            )
        }

        return try {
            val capabilityDescriptors = mutableListOf<PipelineCapabilityDescriptor>()
            for (binding in runtime.artifactAddons) {
                capabilityDescriptors += binding.contribution.descriptor.copy(
                    provenance = PipelineCapabilityProvenance.extension(binding.extensionId)
                )
            }
            for (binding in runtime.managedFieldPolicies) {
                capabilityDescriptors += binding.contribution.descriptor.copy(
                    provenance = PipelineCapabilityProvenance.extension(binding.extensionId)
                )
            }
            val runtimeExtensions = runtime.providers.map { provider ->
                val configuredOptions = config
                    ?.pipelineExtensions
                    ?.get(provider.descriptor.id)
                    ?.contributions
                    .orEmpty()
                    .toSortedMap()
                    .flatMap { (contributionId, contribution) ->
                        contribution.options.map { (key, value) -> "$contributionId.$key" to value }
                    }
                    .toMap()
                val optionSummary = redactor.optionSummary(configuredOptions)
                AgentRuntimeExtension(
                    id = provider.descriptor.id,
                    displayName = provider.descriptor.displayName,
                    spiVersion = provider.descriptor.spiVersion,
                    contributionIds = runtime.contributions
                        .filter { it.extensionId == provider.descriptor.id }
                        .map { PipelineExtensionLoader.contributionId(it.contribution) },
                    configuredOptionKeys = optionSummary.configuredKeys,
                    sensitiveOptionKeys = optionSummary.sensitiveKeys,
                )
            }
            ExtensionInspection(
                capabilityDescriptors = capabilityDescriptors,
                runtimeExtensions = runtimeExtensions,
                status = AgentSnapshotStatus.OK,
            )
        } finally {
            try {
                runtime.close()
            } catch (failure: Throwable) {
                diagnostics += diagnostic(
                    id = "pipeline-extension-close-failed",
                    level = AgentDiagnosticLevel.WARNING,
                    stage = "pipeline-extension",
                    message = redactor.redact(failure.message ?: failure.javaClass.simpleName),
                    hint = "Review the Pipeline Extension classloader close failure.",
                )
            }
        }
    }

    private fun normalizeCapabilityDescriptors(
        descriptors: List<PipelineCapabilityDescriptor>,
        diagnostics: MutableList<AgentDiagnostic>,
    ): List<PipelineCapabilityDescriptor> {
        descriptors.groupBy { descriptor -> descriptor.capabilityId }
            .filterValues { it.size > 1 }
            .keys
            .sorted()
            .forEach { capabilityId ->
                diagnostics += diagnostic(
                    id = "duplicate-capability-${stableSuffix(capabilityId)}",
                    level = AgentDiagnosticLevel.ERROR,
                    stage = "capability-discovery",
                    capabilityId = capabilityId,
                    message = "Multiple providers declared capability identity $capabilityId.",
                    hint = "Give each Pipeline Extension contribution a distinct provider identity.",
                )
            }
        return descriptors.distinctBy { descriptor -> descriptor.capabilityId }
    }

    private fun projectSection(config: ProjectConfig?): AgentProjectSection {
        val modules = if (config != null) {
            config.modules
        } else {
            buildMap<String, String> {
                extension.project.contractModulePath.orNull?.trim()?.takeIf { it.isNotEmpty() }?.let { path ->
                    put("contract", path)
                }
                extension.project.domainModulePath.orNull?.trim()?.takeIf { it.isNotEmpty() }?.let { path ->
                    put("domain", path)
                }
                extension.project.applicationModulePath.orNull?.trim()?.takeIf { it.isNotEmpty() }?.let { path ->
                    put("application", path)
                }
                extension.project.adapterModulePath.orNull?.trim()?.takeIf { it.isNotEmpty() }?.let { path ->
                    put("adapter", path)
                }
            }
        }
        val summary = AgentProjectSummary(
            name = project.rootProject.name,
            path = projectRelativePath(project.rootProject.projectDir),
            group = project.group.toString().takeUnless { it == "unspecified" }.orEmpty(),
            version = project.version.toString().takeUnless { it == "unspecified" }.orEmpty(),
            basePackage = config?.basePackage ?: extension.project.basePackage.orNull?.trim(),
            layout = config?.layout ?: ProjectLayout.MULTI_MODULE,
            modules = modules.map { (role, path) ->
                val directory = project.rootProject.file(path)
                AgentProjectModule(
                    role = role,
                    path = projectRelativePath(directory),
                    gradleProjectPath = project.rootProject.allprojects
                        .firstOrNull { it.projectDir.canonicalFile == directory.canonicalFile }
                        ?.path,
                    exists = directory.isDirectory,
                )
            },
            publicTasks = project.tasks
                .filter { it.group == "cap4k" }
                .map { it.name },
        )
        return AgentProjectSection(
            status = if (config == null) AgentSnapshotStatus.INVALID else AgentSnapshotStatus.OK,
            project = summary,
            reason = if (config == null) "Cap4k project configuration is invalid." else null,
        )
    }

    private fun inputsSection(
        config: ProjectConfig?,
        descriptors: List<PipelineCapabilityDescriptor>,
        sourceProviders: Map<String, SourceProvider>,
        localInputs: CollectedLocalInputs?,
        identity: AgentIdentity,
        redactor: AgentCredentialRedactor,
        diagnostics: MutableList<AgentDiagnostic>,
    ): AgentInputsSection {
        if (config == null) {
            return AgentInputsSection(
                status = AgentSnapshotStatus.UNAVAILABLE,
                inputs = emptyList(),
                reason = "Inputs cannot be normalized until project configuration is valid.",
            )
        }
        var invalid = false
        val sourceDescriptors = descriptors.filter { it.kind == PipelineCapabilityKind.SOURCE }
        val inputs = sourceDescriptors.map { descriptor ->
            val source = config.sources[descriptor.providerId]
            val provider = sourceProviders[descriptor.providerId]
            val configured = source != null
            val safety = descriptor.inputRequirements
                .map { requirement -> requirement.safety }
                .firstOrNull { it == PipelineInputSafety.LIVE_EXTERNAL }
                ?: PipelineInputSafety.LOCAL_PROJECT
            var inspectionFailed = false
            val locations = if (configured && provider != null) {
                try {
                    provider.localInputPaths(config)
                } catch (failure: Exception) {
                    inspectionFailed = true
                    invalid = true
                    diagnostics += diagnostic(
                        id = "input-invalid-${stableSuffix(descriptor.capabilityId)}",
                        level = AgentDiagnosticLevel.ERROR,
                        stage = "input-inspection",
                        capabilityId = descriptor.capabilityId,
                        message = redactor.redact(failure.message ?: failure.javaClass.simpleName),
                        hint = "Fix the configured input before running the relevant plan task.",
                    )
                    emptyList()
                }
            } else {
                emptyList()
            }
            val files = locations.map { location -> project.file(location) }
            val exists = files.takeIf { it.isNotEmpty() }?.all { file -> file.exists() }
            val readable = files.takeIf { it.isNotEmpty() }?.all { file -> file.canRead() }
            if (configured && (exists == false || readable == false)) {
                invalid = true
                diagnostics += diagnostic(
                    id = "input-unavailable-${stableSuffix(descriptor.capabilityId)}",
                    level = AgentDiagnosticLevel.ERROR,
                    stage = "input-inspection",
                    capabilityId = descriptor.capabilityId,
                    inputPath = locations.firstOrNull()?.let { path -> displayPath(path) },
                    message = "One or more configured local inputs are missing or unreadable.",
                    hint = "Fix the configured input paths before running the relevant plan task.",
                )
            }
            if (configured && provider == null) {
                invalid = true
                diagnostics += diagnostic(
                    id = "input-provider-unavailable-${stableSuffix(descriptor.capabilityId)}",
                    level = AgentDiagnosticLevel.ERROR,
                    stage = "input-inspection",
                    capabilityId = descriptor.capabilityId,
                    message = "Configured source has no available provider for read-only inspection.",
                    hint = "Install the source provider that matches the configured capability.",
                )
            } else if (configured && !inspectionFailed && exists != false && readable != false &&
                safety == PipelineInputSafety.LOCAL_PROJECT
            ) {
                try {
                    provider?.collect(config)
                } catch (failure: Exception) {
                    invalid = true
                    diagnostics += diagnostic(
                        id = "input-content-invalid-${stableSuffix(descriptor.capabilityId)}",
                        level = AgentDiagnosticLevel.ERROR,
                        stage = "input-validation",
                        capabilityId = descriptor.capabilityId,
                        inputPath = locations.firstOrNull()?.let { path -> displayPath(path) },
                        message = redactor.redact(failure.message ?: failure.javaClass.simpleName),
                        hint = "Fix the local input content before running the relevant plan task.",
                    )
                }
            }
            val sourceLocalIdentity = localInputs
                ?.bySource
                ?.get(descriptor.providerId)
                ?.takeIf { it.isNotEmpty() }
                ?.let { bytes -> identity.localInputIdentity(bytes) }
            AgentInput(
                id = descriptor.capabilityId,
                providerId = descriptor.providerId,
                safety = safety,
                configured = configured,
                locations = locations.map { location -> displayPath(location) },
                exists = exists,
                readable = readable,
                identity = source?.let {
                    identity.configurationIdentity(
                        mapOf(
                            "options" to it.options,
                            "localInputIdentity" to sourceLocalIdentity,
                        )
                    )
                },
                options = source?.let { redactor.optionSummary(it.options) }
                    ?: com.only4.cap4k.plugin.pipeline.api.AgentOptionSummary(),
                requiredBy = descriptors.filter { candidate ->
                    candidate.inputRequirements.any { descriptor.capabilityId in it.capabilityIds }
                }.map { candidate -> candidate.capabilityId },
                planTask = descriptor.tasks.firstOrNull { it.endsWith("Plan") },
            )
        }
        return AgentInputsSection(
            status = if (invalid) AgentSnapshotStatus.INVALID else AgentSnapshotStatus.OK,
            inputs = inputs,
            reason = if (invalid) "One or more configured local inputs are unavailable." else null,
        )
    }

    private fun capabilityObservations(
        config: ProjectConfig?,
        descriptors: List<PipelineCapabilityDescriptor>,
        diagnostics: List<AgentDiagnostic>,
    ): List<AgentCapabilityObservation> {
        if (config == null) {
            return descriptors.map { descriptor ->
                AgentCapabilityObservation(
                    capabilityId = descriptor.capabilityId,
                    providerId = descriptor.providerId,
                    configured = false,
                    applicable = false,
                    validation = AgentValidationStatus.UNKNOWN,
                    nextActions = descriptor.tasks.take(1),
                )
            }
        }
        val configuredCapabilityIds = linkedSetOf<String>()
        for (descriptor in descriptors) {
            val configured = when (descriptor.kind) {
                PipelineCapabilityKind.SOURCE -> descriptor.providerId in config.sources
                PipelineCapabilityKind.GENERATOR -> when (descriptor.activation) {
                    PipelineCapabilityActivation.EXPLICIT_CONFIGURATION -> descriptor.providerId in config.generators
                    PipelineCapabilityActivation.INPUT_DRIVEN -> false
                    PipelineCapabilityActivation.INSTALLED -> true
                }
                PipelineCapabilityKind.ARTIFACT_ADDON,
                PipelineCapabilityKind.MANAGED_FIELD_POLICY -> when (descriptor.activation) {
                    PipelineCapabilityActivation.INSTALLED -> true
                    PipelineCapabilityActivation.INPUT_DRIVEN -> false
                    PipelineCapabilityActivation.EXPLICIT_CONFIGURATION -> config.pipelineExtensions.values.any { extensionConfig ->
                        descriptor.providerId in extensionConfig.contributions
                    }
                }
            }
            if (configured) {
                configuredCapabilityIds += descriptor.capabilityId
            }
        }
        var changed: Boolean
        do {
            changed = false
            for (descriptor in descriptors) {
                if (descriptor.activation == PipelineCapabilityActivation.INPUT_DRIVEN &&
                    descriptor.capabilityId !in configuredCapabilityIds &&
                    descriptor.inputRequirements.all { requirement ->
                        requirementSatisfied(requirement, configuredCapabilityIds, config)
                    }
                ) {
                    configuredCapabilityIds += descriptor.capabilityId
                    changed = true
                }
            }
        } while (changed)

        return descriptors.map { descriptor ->
            val configured = descriptor.capabilityId in configuredCapabilityIds
            val applicable = descriptor.inputRequirements.all { requirement ->
                requirementSatisfied(requirement, configuredCapabilityIds, config)
            }
            val relatedDiagnostics = diagnostics.filter { it.capabilityId == descriptor.capabilityId }
            val validation = when {
                relatedDiagnostics.any { it.level == AgentDiagnosticLevel.ERROR } -> AgentValidationStatus.FAILED
                configured && applicable && descriptor.inputRequirements.none {
                    it.safety == PipelineInputSafety.LIVE_EXTERNAL
                } -> AgentValidationStatus.VERIFIED
                else -> AgentValidationStatus.UNKNOWN
            }
            AgentCapabilityObservation(
                capabilityId = descriptor.capabilityId,
                providerId = descriptor.providerId,
                configured = configured,
                applicable = applicable && configured,
                validation = validation,
                diagnosticIds = relatedDiagnostics.map { diagnostic -> diagnostic.id },
                nextActions = if (configured && applicable) emptyList() else descriptor.tasks.take(1),
            )
        }
    }

    private fun ownershipSection(
        config: ProjectConfig?,
        localInputs: CollectedLocalInputs?,
        identity: AgentIdentity,
        redactor: AgentCredentialRedactor,
        diagnostics: MutableList<AgentDiagnostic>,
    ): AgentOwnershipSection {
        if (config == null) {
            return AgentOwnershipSection(
                status = AgentSnapshotStatus.UNAVAILABLE,
                items = emptyList(),
                reason = "Ownership cannot be normalized until project configuration is valid.",
            )
        }
        val taskConfig = sourceTaskConfig(config)
        val planFile = project.layout.buildDirectory.file("cap4k/plan.json").get().asFile
        val report = readPlanReport(planFile, redactor, diagnostics)
        appendPlanDiagnostics(report, diagnostics)
        val planFailureReason = report
            ?.takeIf { plan -> plan.outcome != PlanOutcome.SUCCEEDED }
            ?.let { "Saved source plan records a failed planning outcome." }
        if (planFailureReason != null) {
            diagnostics += diagnostic(
                id = "source-plan-failed",
                level = AgentDiagnosticLevel.ERROR,
                stage = "plan",
                artifactPath = projectRelativePath(planFile),
                message = planFailureReason,
                hint = "Fix the reported planning diagnostics and run cap4kPlan again.",
            )
        }
        val currentConfigurationIdentity = identity.configurationIdentity(taskConfig)
        val currentLocalInputIdentity = localInputs
            ?.ordinary
            ?.takeIf { it.isNotEmpty() }
            ?.let { bytes -> identity.localInputIdentity(bytes) }
        val freshness = AgentFreshnessEvaluator.evaluate(
            evidence = report?.evidence,
            currentConfigurationIdentity = currentConfigurationIdentity,
            currentLocalInputIdentity = currentLocalInputIdentity,
            containsLiveExternalInput = "db" in taskConfig.sources,
        )
        val evidence = evidence(
            kind = "source-plan",
            file = planFile,
            freshness = freshness.freshness,
            reason = planFailureReason ?: freshness.reason,
            currentConfigurationIdentity = currentConfigurationIdentity,
            currentLocalInputIdentity = currentLocalInputIdentity,
            planEvidence = report?.evidence,
            planOutcome = report?.outcome,
            nextAction = "cap4kPlan",
        )
        val status = evidenceStatus(report, freshness.freshness)
        var managedRootsStatus = status
        val recordedManagedRoots = try {
            readManagedGeneratedSourceOutputRoots(project.rootProject)
        } catch (failure: Exception) {
            diagnostics += diagnostic(
                id = "generated-source-managed-roots-invalid",
                level = AgentDiagnosticLevel.ERROR,
                stage = "ownership",
                artifactPath = projectRelativePath(generatedSourceManagedRootsStateFile(project.rootProject)),
                message = redactor.redact(failure.message ?: failure.javaClass.simpleName),
                hint = "Fix or remove the invalid generated-source managed-root state before generation.",
            )
            managedRootsStatus = AgentSnapshotStatus.INVALID
            emptyMap()
        }
        val configuredManagedRoots = generatedSourceModuleRoles(taskConfig).associateWith { role ->
            resolvedGeneratedKotlinSourceRoot(project.rootProject, taskConfig, role).orEmpty()
        }.filterValues { path -> path.isNotBlank() }
        return AgentOwnershipSection(
            status = managedRootsStatus,
            items = report?.items.orEmpty().map { item ->
                AgentOwnershipItem(
                    generatorId = item.generatorId,
                    moduleRole = item.moduleRole,
                    templateId = item.templateId,
                    outputPath = item.outputPath,
                    outputKind = item.outputKind,
                    conflictPolicy = item.conflictPolicy,
                    resolvedOutputRoot = item.resolvedOutputRoot,
                )
            },
            managedRoots = recordedManagedRoots + configuredManagedRoots,
            evidence = listOf(evidence),
            reason = when (managedRootsStatus) {
                AgentSnapshotStatus.OK -> null
                AgentSnapshotStatus.INVALID ->
                    planFailureReason ?: "Generated-source managed-root state is invalid."
                else -> freshness.reason
            },
        )
    }

    private fun analysisSection(
        config: ProjectConfig?,
        sourceProviders: Map<String, SourceProvider>,
        localInputs: CollectedLocalInputs?,
        identity: AgentIdentity,
        redactor: AgentCredentialRedactor,
        diagnostics: MutableList<AgentDiagnostic>,
    ): AgentAnalysisSection {
        if (config == null) {
            return AgentAnalysisSection(
                status = AgentSnapshotStatus.UNAVAILABLE,
                configured = false,
                reason = "Analysis cannot be normalized until project configuration is valid.",
            )
        }
        val taskConfig = analysisTaskConfig(config)
        val configured = "ir-analysis" in taskConfig.sources
        if (!configured) {
            return AgentAnalysisSection(
                status = AgentSnapshotStatus.UNAVAILABLE,
                configured = false,
                reason = "sources.irAnalysis.inputDirs is not configured.",
            )
        }
        val sourceProvider = sourceProviders.getValue("ir-analysis")
        val configuredInputDirs = sourceProvider.localInputPaths(taskConfig)
        val fallbackSources = configuredInputDirs.map { path ->
            val source = analyzerSourceIdentity(path, project.rootProject.projectDir.absolutePath)
            AgentAnalysisSource(
                id = source.id,
                path = displayPath(path),
            )
        }
        val planFile = project.layout.buildDirectory.file("cap4k/analysis-plan.json").get().asFile
        val report = readPlanReport(planFile, redactor, diagnostics)
        val planFailureReason = report
            ?.takeIf { plan -> plan.outcome != PlanOutcome.SUCCEEDED }
            ?.let { "Saved analysis plan records a failed planning outcome." }
        if (planFailureReason != null) {
            diagnostics += diagnostic(
                id = "analysis-plan-failed",
                level = AgentDiagnosticLevel.ERROR,
                stage = "analysis-plan",
                artifactPath = projectRelativePath(planFile),
                message = planFailureReason,
                hint = "Fix the reported planning diagnostics and run cap4kAnalysisPlan again.",
            )
        }
        val currentConfigurationIdentity = identity.configurationIdentity(taskConfig)
        val currentLocalInputIdentity = localInputs
            ?.analysis
            ?.takeIf { it.isNotEmpty() }
            ?.let { bytes -> identity.localInputIdentity(bytes) }
        val freshness = AgentFreshnessEvaluator.evaluate(
            evidence = report?.evidence,
            currentConfigurationIdentity = currentConfigurationIdentity,
            currentLocalInputIdentity = currentLocalInputIdentity,
            containsLiveExternalInput = false,
        )
        val evidence = evidence(
            kind = "analysis-plan",
            file = planFile,
            freshness = freshness.freshness,
            reason = planFailureReason ?: freshness.reason,
            currentConfigurationIdentity = currentConfigurationIdentity,
            currentLocalInputIdentity = currentLocalInputIdentity,
            planEvidence = report?.evidence,
            planOutcome = report?.outcome,
            nextAction = "cap4kAnalysisPlan",
        )
        val observation = runCatching {
            sourceProvider.collect(taskConfig) as AnalyzerSnapshot
        }
        val observedInput = observation.getOrNull()
        val collectFailure = observation.exceptionOrNull()
        if (collectFailure != null) {
            AgentAnalysisPartitionIds.ALL.forEach { partitionId ->
                diagnostics += diagnostic(
                    id = "analyzer.${analysisPartitionSlug(partitionId)}.collect-failed",
                    level = AgentDiagnosticLevel.ERROR,
                    stage = "analysis-source",
                    capabilityId = "pipeline.source.ir-analysis",
                    message = redactor.redact(collectFailure.message ?: collectFailure.javaClass.simpleName),
                    hint = "Fix the Analyzer raw evidence for this partition and run cap4kAnalysisPlan again.",
                )
            }
        }

        val plannedByPartition = AgentAnalysisPartitionIds.ALL.associateWith { mutableListOf<String>() }
        val availableByPartition = AgentAnalysisPartitionIds.ALL.associateWith { mutableListOf<String>() }
        report?.items.orEmpty().forEach { item ->
            val partitionId = analysisPartitionId(item.generatorId, item.outputPath) ?: return@forEach
            val outputPath = displayPath(item.outputPath)
            plannedByPartition.getValue(partitionId) += outputPath
            val outputFile = project.file(item.outputPath)
            if (projectOwned(outputFile) && outputFile.isFile && outputFile.lastModified() >= planFile.lastModified()) {
                availableByPartition.getValue(partitionId) += projectRelativePath(outputFile)
            }
        }
        val requestedPartitions = buildSet {
            if ("flow" in taskConfig.generators) add(AgentAnalysisPartitionIds.GRAPH)
            if ("drawing-board" in taskConfig.generators) {
                add(AgentAnalysisPartitionIds.DESIGN_PROJECTION)
                add(AgentAnalysisPartitionIds.AGGREGATE_STRUCTURE)
            }
        }
        val planStatus = evidenceStatus(report, freshness.freshness)
        val sourcePathById = observedInput
            ?.let { snapshot ->
                (snapshot.graph.sources + snapshot.designProjection.sources + snapshot.aggregateStructure.sources)
                    .distinctBy { source -> source.id }
                    .associate { source -> source.id to displayPath(source.inputDir) }
            }
            .orEmpty()
        fun sourcesOf(sources: List<com.only4.cap4k.plugin.pipeline.api.AnalyzerSourceIdentity>?): List<AgentAnalysisSource> =
            sources.orEmpty()
                .map { source -> AgentAnalysisSource(source.id, sourcePathById[source.id] ?: displayPath(source.inputDir)) }
                .distinctBy(AgentAnalysisSource::id)
                .sortedBy(AgentAnalysisSource::id)
                .ifEmpty { fallbackSources }
        fun publishDiagnostics(
            partitionId: String,
            partitionDiagnostics: List<com.only4.cap4k.plugin.pipeline.api.AnalyzerPartitionDiagnostic>,
        ) {
            partitionDiagnostics.forEach { partitionDiagnostic ->
                diagnostics += diagnostic(
                    id = partitionDiagnostic.id,
                    level = AgentDiagnosticLevel.ERROR,
                    stage = "analysis-source",
                    capabilityId = "pipeline.source.ir-analysis",
                    inputPath = partitionDiagnostic.sourceId?.let(sourcePathById::get),
                    message = redactor.redact(partitionDiagnostic.message),
                    hint = "Fix the Analyzer ${analysisPartitionSlug(partitionId)} evidence and run cap4kAnalysisPlan again.",
                )
            }
        }
        observedInput?.let { snapshot ->
            if (AgentAnalysisPartitionIds.GRAPH in requestedPartitions) {
                publishDiagnostics(AgentAnalysisPartitionIds.GRAPH, snapshot.graph.diagnostics)
            }
            if (AgentAnalysisPartitionIds.DESIGN_PROJECTION in requestedPartitions) {
                publishDiagnostics(AgentAnalysisPartitionIds.DESIGN_PROJECTION, snapshot.designProjection.diagnostics)
            }
            if (AgentAnalysisPartitionIds.AGGREGATE_STRUCTURE in requestedPartitions) {
                publishDiagnostics(AgentAnalysisPartitionIds.AGGREGATE_STRUCTURE, snapshot.aggregateStructure.diagnostics)
            }
        }

        fun partition(
            id: String,
            rawStatus: AgentSnapshotStatus,
            counts: Map<String, Int>,
            sources: List<AgentAnalysisSource>,
            diagnosticIds: List<String>,
        ): AgentAnalysisPartition {
            val plannedOutputs = plannedByPartition.getValue(id).distinct().sorted()
            val availableOutputs = availableByPartition.getValue(id).distinct().sorted()
            val requested = id in requestedPartitions
            val status = analysisPartitionStatus(
                rawStatus = rawStatus,
                requested = requested,
                planStatus = planStatus,
                plannedOutputs = plannedOutputs,
                availableOutputs = availableOutputs,
            )
            val outputsAvailable = plannedOutputs.size == availableOutputs.size
            val reason = when {
                !requested -> "This Analyzer partition is not requested by the configured generators."
                rawStatus == AgentSnapshotStatus.INVALID -> "Analyzer raw evidence for " + analysisPartitionSlug(id) + " is invalid."
                planStatus != AgentSnapshotStatus.OK -> planFailureReason ?: freshness.reason
                !outputsAvailable -> "Analysis plan is fresh, but planned " + analysisPartitionSlug(id) + " outputs are missing or older than the plan."
                else -> null
            }
            return AgentAnalysisPartition(
                id = id,
                requested = requested,
                status = status,
                counts = if (requested) counts else counts.mapValues { 0 },
                sources = if (requested) sources else emptyList(),
                freshness = if (requested) freshness.freshness else AgentEvidenceFreshness.UNKNOWN,
                plannedOutputPaths = if (requested) plannedOutputs else emptyList(),
                availableOutputPaths = if (requested) availableOutputs else emptyList(),
                diagnosticIds = if (requested) diagnosticIds.distinct().sorted() else emptyList(),
                nextAction = when {
                    !requested -> null
                    rawStatus == AgentSnapshotStatus.INVALID -> "cap4kAnalysisPlan"
                    planStatus != AgentSnapshotStatus.OK -> "cap4kAnalysisPlan"
                    !outputsAvailable -> "cap4kAnalysisGenerate"
                    else -> null
                },
                reason = reason,
            )
        }

        val fallbackDiagnosticIds = AgentAnalysisPartitionIds.ALL.associateWith { id ->
            if (collectFailure == null) emptyList() else listOf("analyzer.${analysisPartitionSlug(id)}.collect-failed")
        }
        val partitions = listOf(
            partition(
                id = AgentAnalysisPartitionIds.GRAPH,
                rawStatus = observedInput?.graph?.status ?: AgentSnapshotStatus.INVALID,
                counts = mapOf(
                    "nodes" to observedInput?.graph?.nodes.orEmpty().size,
                    "relationships" to observedInput?.graph?.relationships.orEmpty().size,
                ),
                sources = sourcesOf(observedInput?.graph?.sources),
                diagnosticIds = observedInput?.graph?.diagnostics.orEmpty().map { it.id } + fallbackDiagnosticIds.getValue(AgentAnalysisPartitionIds.GRAPH),
            ),
            partition(
                id = AgentAnalysisPartitionIds.DESIGN_PROJECTION,
                rawStatus = observedInput?.designProjection?.status ?: AgentSnapshotStatus.INVALID,
                counts = mapOf("designBlocks" to observedInput?.designProjection?.designBlocks.orEmpty().size),
                sources = sourcesOf(observedInput?.designProjection?.sources),
                diagnosticIds = observedInput?.designProjection?.diagnostics.orEmpty().map { it.id } + fallbackDiagnosticIds.getValue(AgentAnalysisPartitionIds.DESIGN_PROJECTION),
            ),
            partition(
                id = AgentAnalysisPartitionIds.AGGREGATE_STRUCTURE,
                rawStatus = observedInput?.aggregateStructure?.status ?: AgentSnapshotStatus.INVALID,
                counts = mapOf("aggregateElements" to observedInput?.aggregateStructure?.aggregateElements.orEmpty().size),
                sources = sourcesOf(observedInput?.aggregateStructure?.sources),
                diagnosticIds = observedInput?.aggregateStructure?.diagnostics.orEmpty().map { it.id } + fallbackDiagnosticIds.getValue(AgentAnalysisPartitionIds.AGGREGATE_STRUCTURE),
            ),
        )
        val status = analyzerSnapshotStatus(
            partitions.filter(AgentAnalysisPartition::requested).map(AgentAnalysisPartition::status),
        )
        return AgentAnalysisSection(
            status = status,
            configured = true,
            inputDirs = configuredInputDirs.map(::displayPath),
            evidence = evidence,
            partitions = partitions,
            reason = when (status) {
                AgentSnapshotStatus.OK -> null
                AgentSnapshotStatus.INVALID -> "One or more requested Analyzer partitions are invalid."
                AgentSnapshotStatus.PARTIAL -> "One or more requested Analyzer partitions are incomplete or stale."
                AgentSnapshotStatus.UNAVAILABLE -> "No Analyzer partition is requested."
            },
        )
    }

    private fun analysisPartitionId(generatorId: String, outputPath: String): String? = when (generatorId) {
        "flow" -> AgentAnalysisPartitionIds.GRAPH
        "drawing-board" -> if (outputPath.replace('\\', '/').endsWith("/drawing_board_aggregate_elements.json")) {
            AgentAnalysisPartitionIds.AGGREGATE_STRUCTURE
        } else {
            AgentAnalysisPartitionIds.DESIGN_PROJECTION
        }
        else -> null
    }

    private fun analysisPartitionSlug(partitionId: String): String = when (partitionId) {
        AgentAnalysisPartitionIds.GRAPH -> "graph"
        AgentAnalysisPartitionIds.DESIGN_PROJECTION -> "design-projection"
        AgentAnalysisPartitionIds.AGGREGATE_STRUCTURE -> "aggregate-structure"
        else -> error("unsupported Analyzer partition: $partitionId")
    }

    private fun analysisPartitionStatus(
        rawStatus: AgentSnapshotStatus,
        requested: Boolean,
        planStatus: AgentSnapshotStatus,
        plannedOutputs: List<String>,
        availableOutputs: List<String>,
    ): AgentSnapshotStatus = when {
        !requested -> AgentSnapshotStatus.UNAVAILABLE
        rawStatus == AgentSnapshotStatus.INVALID -> AgentSnapshotStatus.INVALID
        rawStatus == AgentSnapshotStatus.UNAVAILABLE -> AgentSnapshotStatus.UNAVAILABLE
        rawStatus == AgentSnapshotStatus.PARTIAL -> AgentSnapshotStatus.PARTIAL
        planStatus == AgentSnapshotStatus.INVALID -> AgentSnapshotStatus.INVALID
        planStatus != AgentSnapshotStatus.OK -> AgentSnapshotStatus.PARTIAL
        plannedOutputs.size != availableOutputs.size -> AgentSnapshotStatus.PARTIAL
        else -> AgentSnapshotStatus.OK
    }

    private fun runtimeSection(
        descriptors: List<PipelineCapabilityDescriptor>,
        extensionInspection: ExtensionInspection,
    ): AgentRuntimeSection = AgentRuntimeSection(
        status = extensionInspection.status,
        capabilities = RuntimeAgentFactsCatalog.capabilities(),
        providers = RuntimeAgentFactsCatalog.providers(),
        extensions = extensionInspection.runtimeExtensions,
        boundaries = descriptors.associate { it.capabilityId to it.boundaries },
        externalIoSafe = extensionInspection.externalIoSafe,
        reason = extensionInspection.reason,
    )

    private fun readPlanReport(
        file: File,
        redactor: AgentCredentialRedactor,
        diagnostics: MutableList<AgentDiagnostic>,
    ): AgentPlanReport? {
        if (!file.isFile) {
            return null
        }
        return try {
            val mapper = PipelineJson.newMapper(includeNulls = true)
            val document = mapper.readTree(file.readText(Charsets.UTF_8))
            validatePlanReport(document)
            mapper.treeToValue(document, AgentPlanReport::class.java)
        } catch (failure: Exception) {
            diagnostics += diagnostic(
                id = "plan-evidence-invalid-${stableSuffix(projectRelativePath(file))}",
                level = AgentDiagnosticLevel.WARNING,
                stage = "plan-evidence",
                artifactPath = displayPath(file.absolutePath),
                message = redactor.redact(failure.message ?: failure.javaClass.simpleName),
                hint = "Run the relevant Cap4k plan task to replace invalid evidence.",
            )
            null
        }
    }

    private fun validatePlanReport(document: JsonNode) {
        require(document.isObject) { "plan report root must be a JSON object" }
        val report = document as ObjectNode
        requiredArray(report, "items", "plan report").forEachIndexed { index, item ->
            require(item.isObject) { "plan report items[$index] must be a JSON object" }
            val artifact = item as ObjectNode
            requiredString(artifact, "generatorId", "plan report items[$index]")
            requiredString(artifact, "moduleRole", "plan report items[$index]")
            requiredString(artifact, "templateId", "plan report items[$index]")
            requiredString(artifact, "outputPath", "plan report items[$index]")
            requiredObject(artifact, "context", "plan report items[$index]")
            requiredEnum(
                artifact,
                "conflictPolicy",
                "plan report items[$index]",
                ConflictPolicy.entries.map(Enum<*>::name).toSet(),
            )
            requiredEnum(
                artifact,
                "outputKind",
                "plan report items[$index]",
                ArtifactOutputKind.entries.map(Enum<*>::name).toSet(),
            )
            requiredString(artifact, "resolvedOutputRoot", "plan report items[$index]")
        }
        requiredEnum(
            report,
            "outcome",
            "plan report",
            PlanOutcome.entries.map(Enum<*>::name).toSet(),
        )

        report.optionalObject("evidence", "plan report")?.let { evidence ->
            val schema = requiredString(evidence, "schema", "plan report evidence")
            require(schema == CAP4K_PLAN_EVIDENCE_SCHEMA) { "plan report evidence schema is unsupported" }
            require(requiredString(evidence, "configurationIdentity", "plan report evidence").isNotBlank()) {
                "plan report evidence configurationIdentity must not be blank"
            }
            evidence["localInputIdentity"]?.takeUnless(JsonNode::isNull)?.let { localIdentity ->
                require(localIdentity.isTextual) {
                    "plan report evidence localInputIdentity must be a string or null"
                }
                require(localIdentity.asText().isNotBlank()) {
                    "plan report evidence localInputIdentity must not be blank"
                }
            }
            requiredBoolean(evidence, "containsLiveExternalInput", "plan report evidence")
        }

        report.optionalObject("diagnostics", "plan report")
            ?.optionalObject("aggregate", "plan report diagnostics")
            ?.let { aggregate ->
                listOf("discoveredTables", "includedTables", "excludedTables", "supportedTables")
                    .forEach { field ->
                        requiredArray(aggregate, field, "plan report aggregate diagnostics")
                            .forEachIndexed { index, value ->
                                require(value.isTextual) {
                                    "plan report aggregate diagnostics $field[$index] must be a string"
                                }
                            }
                    }
                requiredArray(aggregate, "unsupportedTables", "plan report aggregate diagnostics")
                    .forEachIndexed { index, value ->
                        require(value.isObject) {
                            "plan report aggregate diagnostics unsupportedTables[$index] must be a JSON object"
                        }
                        requiredString(
                            value as ObjectNode,
                            "tableName",
                            "plan report aggregate diagnostics unsupportedTables[$index]",
                        )
                        requiredString(
                            value,
                            "reason",
                            "plan report aggregate diagnostics unsupportedTables[$index]",
                        )
                    }
            }
    }

    private fun requiredArray(parent: ObjectNode, field: String, owner: String): ArrayNode {
        val value = parent[field]
        require(value != null && value.isArray) { "$owner $field must be a JSON array" }
        return value as ArrayNode
    }

    private fun requiredObject(parent: ObjectNode, field: String, owner: String): ObjectNode {
        val value = parent[field]
        require(value != null && value.isObject) { "$owner $field must be a JSON object" }
        return value as ObjectNode
    }

    private fun ObjectNode.optionalObject(field: String, owner: String): ObjectNode? {
        val value = this[field] ?: return null
        if (value.isNull) return null
        require(value.isObject) { "$owner $field must be a JSON object or null" }
        return value as ObjectNode
    }

    private fun requiredString(parent: ObjectNode, field: String, owner: String): String {
        val value = parent[field]
        require(value != null && value.isTextual) {
            "$owner $field must be a string"
        }
        return value.asText()
    }

    private fun requiredBoolean(parent: ObjectNode, field: String, owner: String) {
        val value = parent[field]
        require(value != null && value.isBoolean) {
            "$owner $field must be a boolean"
        }
    }

    private fun requiredEnum(
        parent: ObjectNode,
        field: String,
        owner: String,
        allowedValues: Set<String>,
    ) {
        val value = requiredString(parent, field, owner)
        require(value in allowedValues) { "$owner $field contains an unsupported value" }
    }

    private fun appendPlanDiagnostics(report: AgentPlanReport?, diagnostics: MutableList<AgentDiagnostic>) {
        val planFailed = report != null && report.outcome != PlanOutcome.SUCCEEDED
        report?.diagnostics?.aggregate?.unsupportedTables.orEmpty()
            .groupBy { unsupported -> unsupported.tableName }
            .toSortedMap()
            .forEach { (tableName, unsupportedEntries) ->
                val reasons = unsupportedEntries.map { unsupported -> unsupported.reason }.distinct().sorted()
                diagnostics += diagnostic(
                    id = "aggregate-table-unsupported-${stableSuffix(tableName)}",
                    level = if (planFailed) {
                        AgentDiagnosticLevel.ERROR
                    } else {
                        AgentDiagnosticLevel.WARNING
                    },
                    stage = "plan",
                    capabilityId = "pipeline.generator.aggregate",
                    message = "Aggregate table $tableName is unsupported: ${reasons.joinToString("; ")}",
                    hint = "Fix the schema annotations or explicitly exclude the table.",
                    proves = "The saved plan observed an unsupported aggregate table.",
                )
            }
    }

    private fun evidence(
        kind: String,
        file: File,
        freshness: AgentEvidenceFreshness,
        reason: String,
        currentConfigurationIdentity: String,
        currentLocalInputIdentity: String?,
        planEvidence: PlanEvidence?,
        planOutcome: PlanOutcome?,
        nextAction: String,
    ) = AgentEvidence(
        kind = kind,
        path = projectRelativePath(file),
        freshness = freshness,
        currentConfigurationIdentity = currentConfigurationIdentity,
        evidenceConfigurationIdentity = planEvidence?.configurationIdentity,
        currentLocalInputIdentity = currentLocalInputIdentity,
        evidenceLocalInputIdentity = planEvidence?.localInputIdentity,
        reason = reason,
        nextAction = if (freshness == AgentEvidenceFreshness.FRESH && planOutcome == PlanOutcome.SUCCEEDED) {
            null
        } else {
            nextAction
        },
    )

    private fun evidenceStatus(report: AgentPlanReport?, freshness: AgentEvidenceFreshness): AgentSnapshotStatus =
        if (report != null && report.outcome != PlanOutcome.SUCCEEDED) {
            AgentSnapshotStatus.INVALID
        } else if (report != null && freshness == AgentEvidenceFreshness.FRESH) {
            AgentSnapshotStatus.OK
        } else {
            AgentSnapshotStatus.PARTIAL
        }

    private fun requirementSatisfied(
        requirement: PipelineInputRequirement,
        configuredCapabilityIds: Set<String>,
        config: ProjectConfig,
    ): Boolean {
        val values = requirement.capabilityIds.map { capabilityId -> capabilityId in configuredCapabilityIds } +
            requirement.configurationPaths.map { configurationPathPresent(it, config) }
        return when (requirement.match) {
            PipelineInputRequirementMatch.ALL -> values.all { it }
            PipelineInputRequirementMatch.ANY -> values.any { it }
        }
    }

    private fun configurationPathPresent(path: String, config: ProjectConfig): Boolean {
        val segments = path.split('.')
        return when {
            segments.size >= 2 && segments[0] == "sources" -> segments[1] in config.sources
            path == "types.enumManifest.files" -> config.typeRegistry.enumManifestFiles.isNotEmpty()
            path == "types.valueObjectManifest.files" -> config.typeRegistry.valueObjectManifestFiles.isNotEmpty()
            path == "types.registryFile" -> config.typeRegistry.registryFile.isNotBlank()
            path == "project.basePackage" -> config.basePackage.isNotBlank()
            else -> false
        }
    }

    private fun collectLocalInputs(
        project: Project,
        config: ProjectConfig,
        sourceProviders: Map<String, SourceProvider>,
    ): CollectedLocalInputs {
        val bySource = config.sources.mapValues { (sourceId, _) ->
            val locations = try {
                sourceProviders[sourceId]?.localInputPaths(config).orEmpty()
            } catch (_: Exception) {
                emptyList()
            }
            collectInputBytes(project, locations)
        }
        val templateInputs = collectInputBytes(project, config.templates.overrideDirs)
        val registryInputs = config.typeRegistry.registryFile.takeIf { it.isNotBlank() }
            ?.let { collectInputBytes(project, listOf(it)) }
            .orEmpty()
        val ordinarySourceIds = setOf("db", "design-json", "enum-manifest", "value-object-manifest")
        val ordinary = buildMap<String, ByteArray> {
            for ((sourceId, inputs) in bySource) {
                if (sourceId in ordinarySourceIds) {
                    putAll(inputs)
                }
            }
            putAll(templateInputs)
            putAll(registryInputs)
            config.sources["db"]?.options?.get("url")?.toString()?.let { url ->
                dbRunScriptInputFiles(project, url).forEach { file ->
                    for ((path, bytes) in collectInputBytes(project, listOf(file.absolutePath))) {
                        put(path, bytes)
                    }
                }
            }
        }
        val analysis = bySource["ir-analysis"].orEmpty() + templateInputs
        return CollectedLocalInputs(bySource = bySource, ordinary = ordinary, analysis = analysis)
    }

    private fun verifyLocalInputsUnchanged(
        config: ProjectConfig?,
        sourceProviders: Map<String, SourceProvider>,
        baseline: CollectedLocalInputs?,
        redactor: AgentCredentialRedactor,
        diagnostics: MutableList<AgentDiagnostic>,
    ) {
        if (config == null || baseline == null) {
            return
        }
        val current = try {
            collectLocalInputs(project, config, sourceProviders)
        } catch (failure: Exception) {
            diagnostics += diagnostic(
                id = "local-inputs-changed-during-snapshot",
                level = AgentDiagnosticLevel.ERROR,
                stage = "input-inspection",
                message = redactor.redact(
                    failure.message ?: "Local project inputs became unreadable during snapshot generation."
                ),
                hint = "Stop concurrent input edits and run cap4kAgentSnapshot again.",
            )
            return
        }
        if (!sameLocalInputState(baseline, current)) {
            diagnostics += diagnostic(
                id = "local-inputs-changed-during-snapshot",
                level = AgentDiagnosticLevel.ERROR,
                stage = "input-inspection",
                message = "Local project inputs changed while the Agent API snapshot was being assembled.",
                hint = "Stop concurrent input edits and run cap4kAgentSnapshot again.",
            )
        }
    }

    private fun collectInputBytes(project: Project, locations: List<String>): Map<String, ByteArray> = buildMap {
        locations.map { location -> project.file(location) }.forEach { location ->
            val files: Sequence<File> = when {
                location.isFile -> sequenceOf(location)
                location.isDirectory -> location.walkTopDown().filter { file -> file.isFile }
                else -> sequenceOf()
            }
            files.forEach { file ->
                val path = identityPath(file)
                put(path, file.readBytes())
            }
        }
    }

    private fun identityPath(file: File): String {
        val root = project.rootProject.projectDir.canonicalFile.toPath()
        val target = file.canonicalFile.toPath()
        return if (target.startsWith(root)) {
            root.relativize(target).toString().replace('\\', '/')
        } else {
            "external/${stableSuffix(target.toString())}/${file.name}"
        }
    }

    private fun displayPath(path: String): String = projectRelativePath(project.file(path))

    private fun projectRelativePath(file: File): String {
        val root = project.rootProject.projectDir.canonicalFile.toPath()
        val target = file.canonicalFile.toPath()
        return if (target.startsWith(root)) {
            root.relativize(target).toString().replace('\\', '/').ifEmpty { "." }
        } else {
            "external/${stableSuffix(target.toString())}/${file.name}"
        }
    }

    private fun projectOwned(file: File): Boolean {
        val root = project.rootProject.projectDir.canonicalFile.toPath()
        return file.canonicalFile.toPath().startsWith(root)
    }

    private fun writeFiles(filesByPath: Map<String, String>) {
        require(outputDirectory.mkdirs() || outputDirectory.isDirectory) {
            "Failed to create Cap4k Agent API output directory: ${outputDirectory.absolutePath}"
        }
        val outputRoot = outputDirectory.toPath().toAbsolutePath().normalize()
        val targets = filesByPath.mapKeys { (relativePath, _) ->
            val target = outputRoot.resolve(relativePath).normalize()
            require(target.startsWith(outputRoot)) {
                "Agent API output path escapes output directory: $relativePath"
            }
            target
        }
        val manifestTarget = outputRoot.resolve("manifest.json")
        val manifestContent = targets.getValue(manifestTarget)
        Files.deleteIfExists(manifestTarget)
        targets.entries
            .filterNot { (target, _) -> target.fileName.toString() == "manifest.json" }
            .sortedBy { (target, _) -> target.toString() }
            .forEach { (target, content) ->
                writeFileAtomically(target, content)
        }
        removeUnexpectedOutputEntries(outputRoot, targets.keys)
        writeFileAtomically(manifestTarget, manifestContent)
    }

    private fun writeFileAtomically(target: java.nio.file.Path, content: String) {
        Files.createDirectories(target.parent)
        val temporary = target.resolveSibling(".${target.fileName}.tmp")
        Files.writeString(temporary, content, StandardCharsets.UTF_8)
        try {
            Files.move(
                temporary,
                target,
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun removeUnexpectedOutputEntries(
        outputRoot: java.nio.file.Path,
        expectedFiles: Set<java.nio.file.Path>,
    ) {
        Files.walk(outputRoot).use { paths ->
            paths.sorted(Comparator.reverseOrder()).forEach { path ->
                if (path != outputRoot && path !in expectedFiles) {
                    Files.deleteIfExists(path)
                }
            }
        }
    }

    private fun cap4kVersion(): String =
        PipelinePlugin::class.java.`package`.implementationVersion?.takeIf { it.isNotBlank() }
            ?: "development"

    private fun diagnostic(
        id: String,
        level: AgentDiagnosticLevel,
        stage: String,
        message: String,
        capabilityId: String? = null,
        inputPath: String? = null,
        artifactPath: String? = null,
        hint: String? = null,
        proves: String? = null,
    ) = AgentDiagnostic(
        id = id,
        level = level,
        stage = stage,
        capabilityId = capabilityId,
        inputPath = inputPath,
        artifactPath = artifactPath,
        message = message,
        hint = hint,
        proves = proves,
    )

    private fun stableSuffix(value: String): String = AgentHashing.sha256(value).take(12)

    private data class AgentPlanReport(
        val items: List<ArtifactPlanItem>,
        val outcome: PlanOutcome = PlanOutcome.SUCCEEDED,
        val diagnostics: PipelineDiagnostics? = null,
        val evidence: PlanEvidence? = null,
    )

    private data class ExtensionInspection(
        val capabilityDescriptors: List<PipelineCapabilityDescriptor> = emptyList(),
        val runtimeExtensions: List<AgentRuntimeExtension> = emptyList(),
        val status: AgentSnapshotStatus,
        val reason: String? = null,
        val externalIoSafe: Boolean = true,
    )
}

internal data class CollectedLocalInputs(
    val bySource: Map<String, Map<String, ByteArray>>,
    val ordinary: Map<String, ByteArray>,
    val analysis: Map<String, ByteArray>,
)

internal fun sameLocalInputState(
    first: CollectedLocalInputs,
    second: CollectedLocalInputs,
): Boolean {
    fun CollectedLocalInputs.entries(): Map<String, ByteArray> = buildMap {
        bySource.toSortedMap().forEach { (sourceId, inputs) ->
            inputs.toSortedMap().forEach { (path, bytes) ->
                put("source/$sourceId/$path", bytes)
            }
        }
        ordinary.toSortedMap().forEach { (path, bytes) -> put("ordinary/$path", bytes) }
        analysis.toSortedMap().forEach { (path, bytes) -> put("analysis/$path", bytes) }
    }

    val firstEntries = first.entries()
    val secondEntries = second.entries()
    return firstEntries.keys == secondEntries.keys && firstEntries.all { (path, bytes) ->
        bytes.contentEquals(secondEntries.getValue(path))
    }
}

internal fun planEvidence(
    project: Project,
    config: ProjectConfig,
): PlanEvidence {
    val identity = AgentIdentity()
    val localInputs = run {
        val providers = (builtInAuthoringSourceProviders() + builtInAnalysisSourceProviders())
            .associateBy { provider -> provider.id }
        val options = config.sources.keys.flatMap { sourceId ->
            providers[sourceId]?.localInputPaths(config).orEmpty()
        } + config.templates.overrideDirs + listOfNotNull(config.typeRegistry.registryFile.takeIf { it.isNotBlank() }) +
            config.sources["db"]
                ?.options
                ?.get("url")
                ?.toString()
                ?.let { url -> dbRunScriptInputFiles(project, url).map(File::getAbsolutePath) }
                .orEmpty()
        val root = project.rootProject.projectDir.canonicalFile.toPath()
        buildMap<String, ByteArray> {
            options.map { location -> project.file(location) }.forEach { location ->
                val files: Sequence<File> = when {
                    location.isFile -> sequenceOf(location)
                    location.isDirectory -> location.walkTopDown().filter { file -> file.isFile }
                    else -> sequenceOf()
                }
                files.forEach { file ->
                    val target = file.canonicalFile.toPath()
                    val path = if (target.startsWith(root)) {
                        root.relativize(target).toString().replace('\\', '/')
                    } else {
                        "external/${AgentHashing.sha256(target.toString()).take(12)}/${file.name}"
                    }
                    put(path, file.readBytes())
                }
            }
        }
    }
    return PlanEvidence(
        configurationIdentity = identity.configurationIdentity(config),
        localInputIdentity = localInputs.takeIf { it.isNotEmpty() }?.let { bytes -> identity.localInputIdentity(bytes) },
        containsLiveExternalInput = "db" in config.sources,
    )
}

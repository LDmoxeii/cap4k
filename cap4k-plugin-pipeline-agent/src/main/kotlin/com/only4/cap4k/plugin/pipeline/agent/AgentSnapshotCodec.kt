package com.only4.cap4k.plugin.pipeline.agent

import com.only4.cap4k.plugin.pipeline.api.AgentAnalysisSection
import com.only4.cap4k.plugin.pipeline.api.AgentCapabilitiesSection
import com.only4.cap4k.plugin.pipeline.api.AgentDiagnostic
import com.only4.cap4k.plugin.pipeline.api.AgentDiagnosticCounts
import com.only4.cap4k.plugin.pipeline.api.AgentDiagnosticLevel
import com.only4.cap4k.plugin.pipeline.api.AgentDiagnosticsSection
import com.only4.cap4k.plugin.pipeline.api.AgentInputsSection
import com.only4.cap4k.plugin.pipeline.api.AgentManifest
import com.only4.cap4k.plugin.pipeline.api.AgentOwnershipSection
import com.only4.cap4k.plugin.pipeline.api.AgentProjectSection
import com.only4.cap4k.plugin.pipeline.api.AgentProjectSummary
import com.only4.cap4k.plugin.pipeline.api.AgentRuntimeSection
import com.only4.cap4k.plugin.pipeline.api.AgentSectionReference
import com.only4.cap4k.plugin.pipeline.api.AgentSnapshotSections
import com.only4.cap4k.plugin.pipeline.api.AgentSnapshotStatus
import java.lang.reflect.Type

data class AgentEncodedSection(
    val id: String,
    val path: String,
    val schema: String,
    val status: AgentSnapshotStatus,
    val sha256: String,
    val json: String,
    val counts: Map<String, Int>,
    val reason: String?,
)

data class AgentEncodedSnapshot(
    val manifest: AgentManifest,
    val manifestJson: String,
    val manifestSha256: String,
    val sectionsByPath: Map<String, AgentEncodedSection>,
) {
    val sectionJsonByPath: Map<String, String>
        get() = sectionsByPath.mapValues { (_, section) -> section.json }

    val sectionSha256ByPath: Map<String, String>
        get() = sectionsByPath.mapValues { (_, section) -> section.sha256 }

    val filesByPath: Map<String, String>
        get() = linkedMapOf<String, String>().apply {
            sectionsByPath.toSortedMap().forEach { (path, section) -> put(path, section.json) }
            put("manifest.json", manifestJson)
        }
}

class AgentSnapshotCodec(
    private val redactor: AgentCredentialRedactor = AgentCredentialRedactor(),
) {
    private val json = AgentStableJson(redactor)

    fun encode(
        cap4kVersion: String,
        sections: AgentSnapshotSections,
        recommendedSections: List<String>? = null,
    ): AgentEncodedSnapshot {
        require(cap4kVersion.isNotBlank()) { "cap4k version must not be blank" }
        val normalized = normalize(sections)
        val encodedSectionList = sectionDefinitions(normalized)
            .map { definition ->
                val sectionJson = json.toJson(definition.value)
                AgentEncodedSection(
                    id = definition.id,
                    path = definition.path,
                    schema = definition.schema,
                    status = definition.status,
                    sha256 = AgentHashing.sectionSha256(sectionJson),
                    json = sectionJson,
                    counts = definition.counts.toSortedMap(),
                    reason = definition.reason?.let(redactor::redact),
                )
            }
        val encodedSections = encodedSectionList
            .associateBy(AgentEncodedSection::path)
            .toSortedMap()
        val snapshotId = AgentHashing.snapshotSha256(
            encodedSections.mapValues { (_, section) -> section.sha256 }
        )
        val sectionReferences = encodedSectionList
            .map { section ->
                AgentSectionReference(
                    id = section.id,
                    path = section.path,
                    schema = section.schema,
                    status = section.status,
                    sha256 = section.sha256,
                    counts = section.counts,
                    reason = section.reason,
                )
            }
        val knownSectionIds = sectionReferences.mapTo(linkedSetOf(), AgentSectionReference::id)
        val normalizedRecommendedSections = recommendedSections
            ?.distinct()
            ?.sorted()
            ?: deriveRecommendedSections(sectionReferences, normalized.diagnostics)
        require(normalizedRecommendedSections.all(knownSectionIds::contains)) {
            "recommended agent sections must reference known section ids"
        }
        val manifest = AgentManifest(
            cap4kVersion = cap4kVersion,
            snapshotId = snapshotId,
            status = AgentSnapshotStatusAggregator.aggregate(normalized),
            project = normalized.project.project,
            sections = sectionReferences,
            diagnosticCounts = diagnosticCounts(normalized.diagnostics.diagnostics),
            recommendedSections = normalizedRecommendedSections,
        )
        val manifestJson = json.toJson(manifest)
        return AgentEncodedSnapshot(
            manifest = manifest,
            manifestJson = manifestJson,
            manifestSha256 = AgentHashing.sha256(manifestJson),
            sectionsByPath = encodedSections,
        )
    }

    fun toJson(value: Any): String = json.toJson(value)

    fun <T> fromJson(value: String, type: Class<T>): T = json.fromJson(value, type)

    fun <T> fromJson(value: String, type: Type): T = json.fromJson(value, type)

    private fun normalize(sections: AgentSnapshotSections): AgentSnapshotSections = AgentSnapshotSections(
        project = normalizeProject(sections.project),
        capabilities = normalizeCapabilities(sections.capabilities),
        inputs = normalizeInputs(sections.inputs),
        ownership = normalizeOwnership(sections.ownership),
        runtime = normalizeRuntime(sections.runtime),
        analysis = normalizeAnalysis(sections.analysis),
        diagnostics = normalizeDiagnostics(sections.diagnostics),
    )

    private fun normalizeProject(section: AgentProjectSection): AgentProjectSection = section.copy(
        project = section.project.copy(
            path = normalizePath(section.project.path),
            modules = section.project.modules
                .map { module -> module.copy(path = normalizePath(module.path)) }
                .sortedWith(compareBy({ it.role }, { it.path }, { it.gradleProjectPath.orEmpty() })),
            publicTasks = section.project.publicTasks.distinct().sorted(),
        ),
    )

    private fun normalizeCapabilities(section: AgentCapabilitiesSection): AgentCapabilitiesSection = section.copy(
        supported = section.supported
            .map { capability ->
                capability.copy(
                    tacticalCarriers = capability.tacticalCarriers.distinct().sorted(),
                    executionLanes = capability.executionLanes.distinct().sortedBy(Enum<*>::name),
                    tasks = capability.tasks.distinct(),
                    inputRequirements = capability.inputRequirements
                        .map { requirement ->
                            requirement.copy(
                                capabilityIds = requirement.capabilityIds.distinct().sorted(),
                                configurationPaths = requirement.configurationPaths.distinct().sorted(),
                            )
                        }
                        .sortedBy { it.id },
                    outputKinds = capability.outputKinds.distinct().sortedBy(Enum<*>::name),
                    boundaries = capability.boundaries.distinct()
                        .sortedWith(compareBy({ it.kind.name }, { it.authority })),
                )
            }
            .sortedWith(compareBy({ it.capabilityId }, { it.providerId })),
        effective = section.effective
            .map { capability ->
                capability.copy(
                    diagnosticIds = capability.diagnosticIds.distinct().sorted(),
                    nextActions = capability.nextActions.distinct().sorted(),
                )
            }
            .sortedWith(compareBy({ it.capabilityId }, { it.providerId })),
    )

    private fun normalizeInputs(section: AgentInputsSection): AgentInputsSection = section.copy(
        inputs = section.inputs
            .map { input ->
                input.copy(
                    locations = input.locations.distinct().map(::normalizePath).sorted(),
                    options = input.options.copy(
                        configuredKeys = input.options.configuredKeys.distinct().sorted(),
                        sensitiveKeys = input.options.sensitiveKeys.distinct().sorted(),
                    ),
                    requiredBy = input.requiredBy.distinct().sorted(),
                )
            }
            .sortedWith(compareBy({ it.id }, { it.providerId })),
    )

    private fun normalizeOwnership(section: AgentOwnershipSection): AgentOwnershipSection = section.copy(
        items = section.items
            .map { item ->
                item.copy(
                    outputPath = normalizePath(item.outputPath),
                    resolvedOutputRoot = normalizePath(item.resolvedOutputRoot),
                )
            }
            .sortedWith(compareBy({ it.generatorId }, { it.moduleRole }, { it.outputPath })),
        managedRoots = section.managedRoots.toSortedMap()
            .mapValues { (_, path) -> normalizePath(path) },
        evidence = section.evidence
            .map { evidence -> evidence.copy(path = normalizePath(evidence.path)) }
            .sortedWith(compareBy({ it.kind }, { it.path })),
    )

    private fun normalizeRuntime(section: AgentRuntimeSection): AgentRuntimeSection = section.copy(
        capabilities = section.capabilities
            .map { capability ->
                capability.copy(providerIds = capability.providerIds.distinct().sorted())
            }
            .sortedBy { capability -> capability.capabilityId },
        providers = section.providers
            .sortedWith(compareBy({ provider -> provider.providerId }, { provider -> provider.capabilityId })),
        eventHandler = section.eventHandler.copy(
            eventKinds = section.eventHandler.eventKinds.distinct().sorted(),
            forbidden = section.eventHandler.forbidden.distinct().sorted(),
            managedAsyncCompletion = section.eventHandler.managedAsyncCompletion.copy(
                trackedOperations = section.eventHandler.managedAsyncCompletion.trackedOperations.distinct().sorted(),
            ),
        ),
        extensions = section.extensions
            .map { extension ->
                extension.copy(
                    contributionIds = extension.contributionIds.distinct().sorted(),
                    configuredOptionKeys = extension.configuredOptionKeys.distinct().sorted(),
                    sensitiveOptionKeys = extension.sensitiveOptionKeys.distinct().sorted(),
                )
            }
            .sortedBy { it.id },
        boundaries = section.boundaries.toSortedMap()
            .mapValues { (_, boundaries) ->
                boundaries.distinct().sortedWith(compareBy({ it.kind.name }, { it.authority }))
            },
    )

    private fun normalizeAnalysis(section: AgentAnalysisSection): AgentAnalysisSection = section.copy(
        inputDirs = section.inputDirs.distinct().map(::normalizePath).sorted(),
        evidence = section.evidence?.let { evidence -> evidence.copy(path = normalizePath(evidence.path)) },
        partitions = section.partitions
            .map { partition ->
                partition.copy(
                    counts = partition.counts.toSortedMap(),
                    sources = partition.sources
                        .map { source -> source.copy(path = normalizePath(source.path)) }
                        .distinctBy { source -> source.id }
                        .sortedBy { source -> source.id },
                    plannedOutputPaths = partition.plannedOutputPaths.map(::normalizePath).distinct().sorted(),
                    availableOutputPaths = partition.availableOutputPaths.map(::normalizePath).distinct().sorted(),
                    diagnosticIds = partition.diagnosticIds.distinct().sorted(),
                )
            }
            .sortedBy { partition -> partition.id },
    )

    private fun normalizeDiagnostics(section: AgentDiagnosticsSection): AgentDiagnosticsSection = section.copy(
        diagnostics = section.diagnostics
            .map { diagnostic ->
                diagnostic.copy(
                    inputPath = diagnostic.inputPath?.let(::normalizePath),
                    artifactPath = diagnostic.artifactPath?.let(::normalizePath),
                )
            }
            .sortedWith(compareBy({ diagnosticLevelOrder(it.level) }, { it.id })),
    )

    private fun sectionDefinitions(sections: AgentSnapshotSections): List<SectionDefinition> = listOf(
        SectionDefinition(
            id = AgentContractCatalog.PROJECT.id,
            path = AgentContractCatalog.PROJECT.path,
            schema = sections.project.schema,
            status = sections.project.status,
            counts = linkedMapOf(
                "modules" to sections.project.project.modules.size,
                "publicTasks" to sections.project.project.publicTasks.size,
            ),
            reason = sections.project.reason,
            value = sections.project,
        ),
        SectionDefinition(
            id = AgentContractCatalog.CAPABILITIES.id,
            path = AgentContractCatalog.CAPABILITIES.path,
            schema = sections.capabilities.schema,
            status = sections.capabilities.status,
            counts = linkedMapOf(
                "supported" to sections.capabilities.supported.size,
                "effective" to sections.capabilities.effective.size,
            ),
            reason = sections.capabilities.reason,
            value = sections.capabilities,
        ),
        SectionDefinition(
            id = AgentContractCatalog.INPUTS.id,
            path = AgentContractCatalog.INPUTS.path,
            schema = sections.inputs.schema,
            status = sections.inputs.status,
            counts = mapOf("inputs" to sections.inputs.inputs.size),
            reason = sections.inputs.reason,
            value = sections.inputs,
        ),
        SectionDefinition(
            id = AgentContractCatalog.OWNERSHIP.id,
            path = AgentContractCatalog.OWNERSHIP.path,
            schema = sections.ownership.schema,
            status = sections.ownership.status,
            counts = linkedMapOf(
                "items" to sections.ownership.items.size,
                "managedRoots" to sections.ownership.managedRoots.size,
                "evidence" to sections.ownership.evidence.size,
            ),
            reason = sections.ownership.reason,
            value = sections.ownership,
        ),
        SectionDefinition(
            id = AgentContractCatalog.RUNTIME.id,
            path = AgentContractCatalog.RUNTIME.path,
            schema = sections.runtime.schema,
            status = sections.runtime.status,
            counts = linkedMapOf(
                "capabilities" to sections.runtime.capabilities.size,
                "providers" to sections.runtime.providers.size,
                "extensions" to sections.runtime.extensions.size,
                "boundaries" to sections.runtime.boundaries.values.sumOf(List<*>::size),
            ),
            reason = sections.runtime.reason,
            value = sections.runtime,
        ),
        SectionDefinition(
            id = AgentContractCatalog.ANALYSIS.id,
            path = AgentContractCatalog.ANALYSIS.path,
            schema = sections.analysis.schema,
            status = sections.analysis.status,
            counts = buildMap {
                put("inputDirs", sections.analysis.inputDirs.size)
                put("partitions", sections.analysis.partitions.size)
                put("sources", sections.analysis.partitions.flatMap { it.sources }.distinctBy { it.id }.size)
                put("plannedOutputs", sections.analysis.partitions.sumOf { it.plannedOutputPaths.size })
                put("availableOutputs", sections.analysis.partitions.sumOf { it.availableOutputPaths.size })
                sections.analysis.partitions.forEach { partition ->
                    partition.counts.toSortedMap().forEach { (name, value) ->
                        put("${partition.id}.$name", value)
                    }
                }
            },
            reason = sections.analysis.reason,
            value = sections.analysis,
        ),
        SectionDefinition(
            id = AgentContractCatalog.DIAGNOSTICS.id,
            path = AgentContractCatalog.DIAGNOSTICS.path,
            schema = sections.diagnostics.schema,
            status = sections.diagnostics.status,
            counts = linkedMapOf<String, Int>().apply {
                val counts = diagnosticCounts(sections.diagnostics.diagnostics)
                put("diagnostics", sections.diagnostics.diagnostics.size)
                put("error", counts.error)
                put("warning", counts.warning)
                put("info", counts.info)
            },
            reason = sections.diagnostics.reason,
            value = sections.diagnostics,
        ),
    )

    private fun deriveRecommendedSections(
        references: List<AgentSectionReference>,
        diagnostics: AgentDiagnosticsSection,
    ): List<String> = buildSet {
        references.filter { it.status != AgentSnapshotStatus.OK }.forEach { add(it.id) }
        if (diagnostics.diagnostics.isNotEmpty()) {
            add("diagnostics")
        }
    }.sorted()

    private fun diagnosticCounts(diagnostics: List<AgentDiagnostic>) = AgentDiagnosticCounts(
        error = diagnostics.count { it.level == AgentDiagnosticLevel.ERROR },
        warning = diagnostics.count { it.level == AgentDiagnosticLevel.WARNING },
        info = diagnostics.count { it.level == AgentDiagnosticLevel.INFO },
    )

    private fun diagnosticLevelOrder(level: AgentDiagnosticLevel): Int = when (level) {
        AgentDiagnosticLevel.ERROR -> 0
        AgentDiagnosticLevel.WARNING -> 1
        AgentDiagnosticLevel.INFO -> 2
    }

    private fun normalizePath(path: String): String = path.replace('\\', '/')

    private data class SectionDefinition(
        val id: String,
        val path: String,
        val schema: String,
        val status: AgentSnapshotStatus,
        val counts: Map<String, Int>,
        val reason: String?,
        val value: Any,
    )
}

package com.only4.cap4k.plugin.pipeline.core

import com.only4.cap4k.plugin.pipeline.api.ArtifactAddonContext
import com.only4.cap4k.plugin.pipeline.api.ArtifactAddonProvider
import com.only4.cap4k.plugin.pipeline.api.ArtifactPlanItem
import com.only4.cap4k.plugin.pipeline.api.ArtifactOutputKind
import com.only4.cap4k.plugin.pipeline.api.ConflictPolicy
import com.only4.cap4k.plugin.pipeline.api.GeneratorProvider
import com.only4.cap4k.plugin.pipeline.api.PipelineResult
import com.only4.cap4k.plugin.pipeline.api.PipelineContributionBinding
import com.only4.cap4k.plugin.pipeline.api.PipelineRunner
import com.only4.cap4k.plugin.pipeline.api.ManagedFieldPolicyContributionContext
import com.only4.cap4k.plugin.pipeline.api.ManagedFieldPolicyProvider
import com.only4.cap4k.plugin.pipeline.api.ManagedPolicyDefinitionOwner
import com.only4.cap4k.plugin.pipeline.api.OwnedManagedFieldPolicyDefinition
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig
import com.only4.cap4k.plugin.pipeline.api.SourceProvider
import com.only4.cap4k.plugin.pipeline.renderer.api.ArtifactRenderer

class DefaultPipelineRunner(
    private val sources: List<SourceProvider>,
    private val generators: List<GeneratorProvider>,
    private val assembler: CanonicalAssembler,
    private val renderer: ArtifactRenderer,
    private val exporter: ArtifactExporter,
    private val transformPlanItem: (ArtifactPlanItem) -> ArtifactPlanItem = { it },
    private val includePlanItem: (ArtifactPlanItem) -> Boolean = { true },
    private val artifactAddons: List<PipelineContributionBinding<ArtifactAddonProvider>> = emptyList(),
    private val managedFieldPolicies: List<PipelineContributionBinding<ManagedFieldPolicyProvider>> = emptyList(),
) : PipelineRunner {
    private val configKeyRequiredGeneratorIds = setOf("aggregate", "aggregate-projection")

    override fun run(config: ProjectConfig): PipelineResult {
        validateAddonProviders(config)

        val configuredSourceIds = config.sources.keys
        val installedSourceIds = sources.map { it.id }.toSet()
        val missingSourceIds = configuredSourceIds
            .filter { it !in installedSourceIds }
            .sorted()
        require(missingSourceIds.isEmpty()) {
            "configured sources have no registered providers: ${missingSourceIds.joinToString(", ")}"
        }

        val configuredGeneratorIds = config.generators.keys
        val installedGeneratorIds = generators.map { it.id }.toSet()
        val missingGeneratorIds = configuredGeneratorIds
            .filter { it !in installedGeneratorIds }
            .sorted()
        require(missingGeneratorIds.isEmpty()) {
            "configured generators have no registered providers: ${missingGeneratorIds.joinToString(", ")}"
        }

        val snapshots = sources
            .filter { it.id in config.sources }
            .map { it.collect(config) }

        val assembly = assembler.assemble(config, snapshots, managedFieldPolicyDefinitions(config))
        val model = assembly.model

        val builtInPlanItems = generators
            .filter { it.id !in configKeyRequiredGeneratorIds || it.id in config.generators }
            .flatMap { it.plan(config, model) }
            .map { ProvenancedPlanItem(it) }

        val addonPlanItems = artifactAddons.flatMap { binding ->
            val provider = binding.contribution
            val providerOptions = config.pipelineExtensions[binding.extensionId]
                ?.contributions
                ?.get(provider.id)
                ?.options
                .orEmpty()
            try {
                provider.plan(
                    ArtifactAddonContext(
                        config = config,
                        model = model,
                        options = providerOptions,
                    )
                )
            } catch (ex: Exception) {
                throw IllegalStateException(
                    "Addon provider ${provider.id} failed while planning artifacts",
                    ex,
                )
            }.also { items ->
                items.forEach { item -> validateAddonTemplateNamespace(item, provider.id) }
            }.map { item -> ProvenancedPlanItem(item, addonProviderId = provider.id) }
        }

        val planItems = (builtInPlanItems + addonPlanItems)
            .map { item -> item.copy(planItem = transformPlanItem(item.planItem)) }
            .filter { item -> includePlanItem(item.planItem) }
            .onEach { item ->
                item.addonProviderId?.let { providerId ->
                    validateAddonTemplateNamespace(item.planItem, providerId)
                }
            }
            .map { resolveConflictPolicy(it, config) }

        val renderedArtifacts = renderer.render(planItems, config)
        val writtenPaths = exporter.export(renderedArtifacts)

        return PipelineResult(
            planItems = planItems,
            renderedArtifacts = renderedArtifacts,
            writtenPaths = writtenPaths,
            warnings = emptyList(),
            managedFieldPolicies = model.managedFieldPolicies,
            diagnostics = assembly.diagnostics,
        )
    }

    private fun validateAddonProviders(config: ProjectConfig) {
        val duplicate = artifactAddons
            .groupingBy { it.contribution.id }
            .eachCount()
            .entries
            .firstOrNull { it.value > 1 }
            ?.key

        require(duplicate == null) {
            "duplicate artifact addon provider id: $duplicate"
        }

        config.pipelineExtensions.entries
            .firstOrNull { it.key != it.value.id }
            ?.let { (key, extensionConfig) ->
                throw IllegalArgumentException(
                    "Configured pipeline extension key does not match extension id: $key != ${extensionConfig.id}",
                )
            }
        config.pipelineExtensions.values.forEach { extensionConfig ->
            extensionConfig.contributions.entries
                .firstOrNull { it.key != it.value.id }
                ?.let { (key, contributionConfig) ->
                    throw IllegalArgumentException(
                        "Configured pipeline contribution key does not match contribution id in " +
                            "${extensionConfig.id}: $key != ${contributionConfig.id}",
                    )
                }
        }
    }

    private fun managedFieldPolicyDefinitions(config: ProjectConfig): List<OwnedManagedFieldPolicyDefinition> =
        managedFieldPolicies.flatMap { binding ->
            val provider = binding.contribution
            val options = config.pipelineExtensions[binding.extensionId]
                ?.contributions
                ?.get(provider.id)
                ?.options
                .orEmpty()
            val definitions = try {
                provider.definitions(
                    ManagedFieldPolicyContributionContext(
                        config = config,
                        options = options,
                    )
                )
            } catch (failure: Exception) {
                throw IllegalStateException(
                    "Managed Field Policy contribution ${binding.extensionId}/${provider.id} " +
                        "failed while defining policies",
                    failure,
                )
            }
            definitions.map { definition ->
                OwnedManagedFieldPolicyDefinition(
                    definition = definition,
                    owner = ManagedPolicyDefinitionOwner.Extension(binding.extensionId, provider.id),
                )
            }
        }

    private fun validateAddonTemplateNamespace(item: ArtifactPlanItem, providerId: String) {
        require(item.templateId.startsWith("addons/$providerId/")) {
            "Addon $providerId produced template id outside addons/$providerId/: ${item.templateId}"
        }
    }

    private fun resolveConflictPolicy(item: ProvenancedPlanItem, config: ProjectConfig): ArtifactPlanItem {
        val planItem = item.planItem
        val resolvedConflictPolicy = when {
            planItem.outputKind == ArtifactOutputKind.CHECKED_IN_SOURCE -> ConflictPolicy.SKIP
            item.isBuiltInObservationOutput() -> ConflictPolicy.OVERWRITE
            else -> config.templates.templateConflictPolicies[planItem.templateId] ?: planItem.conflictPolicy
        }

        return planItem.copy(conflictPolicy = resolvedConflictPolicy)
    }

    private fun ProvenancedPlanItem.isBuiltInObservationOutput(): Boolean {
        if (addonProviderId != null) {
            return false
        }

        return originalGeneratorId in observationOutputGeneratorIds &&
            originalTemplateId in observationOutputTemplateIds
    }

    private companion object {
        val observationOutputGeneratorIds = setOf("drawing-board", "flow")
        val observationOutputTemplateIds = setOf(
            "drawing-board/document.json.peb",
            "flow/entry.json.peb",
            "flow/entry.mmd.peb",
            "flow/index.json.peb",
        )
    }

    private data class ProvenancedPlanItem(
        val planItem: ArtifactPlanItem,
        val addonProviderId: String? = null,
        val originalGeneratorId: String = planItem.generatorId,
        val originalTemplateId: String = planItem.templateId,
    ) {
        val outputKind: ArtifactOutputKind
            get() = planItem.outputKind
    }
}

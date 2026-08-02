package com.only4.cap4k.plugin.pipeline.gradle

import com.only4.cap4k.plugin.pipeline.api.ArtifactAddonProvider
import com.only4.cap4k.plugin.pipeline.api.PIPELINE_EXTENSION_SPI_VERSION
import com.only4.cap4k.plugin.pipeline.api.ManagedFieldPolicyProvider
import com.only4.cap4k.plugin.pipeline.api.PipelineContribution
import com.only4.cap4k.plugin.pipeline.api.PipelineContributionBinding
import com.only4.cap4k.plugin.pipeline.api.PipelineExtensionProvider
import com.only4.cap4k.plugin.pipeline.agent.AgentHashing
import java.io.File
import java.net.URLClassLoader
import java.util.ServiceLoader

internal data class LoadedPipelineExtensions(
    val providers: List<PipelineExtensionProvider>,
    val contributions: List<PipelineContributionBinding<PipelineContribution>>,
    val artifactAddons: List<PipelineContributionBinding<ArtifactAddonProvider>>,
    val managedFieldPolicies: List<PipelineContributionBinding<ManagedFieldPolicyProvider>>,
)

internal object PipelineExtensionLoader {
    fun classLoader(files: Collection<File>, parent: ClassLoader): URLClassLoader {
        val urls = files.map { it.toURI().toURL() }.toTypedArray()
        return URLClassLoader(urls, parent)
    }

    fun templateClassLoader(provider: ArtifactAddonProvider): URLClassLoader {
        val location = provider.javaClass.protectionDomain?.codeSource?.location
            ?: throw IllegalArgumentException("artifact addon provider ${provider.id} has no code source")
        return URLClassLoader(arrayOf(location), null)
    }

    fun load(classLoader: ClassLoader): LoadedPipelineExtensions {
        val classpathEvidence = resolvedClasspathEvidence(classLoader)
        val providers = try {
            ServiceLoader.load(PipelineExtensionProvider::class.java, classLoader).toList()
        } catch (failure: java.util.ServiceConfigurationError) {
            throw java.util.ServiceConfigurationError(
                "${failure.message}; resolved cap4kPipelineExtension classpath=$classpathEvidence",
                failure,
            )
        }
        require(providers.isNotEmpty()) {
            "No PipelineExtensionProvider was found on the non-empty cap4kPipelineExtension classpath; " +
                "resolved classpath=$classpathEvidence"
        }
        return try {
            validateAndBind(providers)
        } catch (failure: IllegalArgumentException) {
            throw IllegalArgumentException(
                "${failure.message}; resolved cap4kPipelineExtension classpath=$classpathEvidence",
                failure,
            )
        }
    }

    private fun resolvedClasspathEvidence(classLoader: ClassLoader): String =
        (classLoader as? URLClassLoader)
            ?.urLs
            ?.map { url ->
                val fileName = runCatching { File(url.toURI()).name }.getOrElse { "artifact" }
                "$fileName#${AgentHashing.sha256(url.toExternalForm()).take(12)}"
            }
            ?.sorted()
            ?.joinToString(prefix = "[", postfix = "]")
            ?: "[unavailable:${classLoader.javaClass.name}]"

    fun validateAndBind(providers: List<PipelineExtensionProvider>): LoadedPipelineExtensions {
        providers.forEach { provider ->
            val descriptor = provider.descriptor
            require(descriptor.id.isNotBlank() && descriptor.id == descriptor.id.trim()) {
                "pipeline extension id must be non-blank and trimmed"
            }
            require(descriptor.displayName.isNotBlank()) {
                "pipeline extension ${descriptor.id} display name must not be blank"
            }
            require(descriptor.spiVersion == PIPELINE_EXTENSION_SPI_VERSION) {
                "unsupported pipeline extension SPI version for ${descriptor.id}: " +
                    "${descriptor.spiVersion}; expected $PIPELINE_EXTENSION_SPI_VERSION"
            }
        }

        val duplicateExtensionId = providers
            .groupingBy { it.descriptor.id }
            .eachCount()
            .entries
            .firstOrNull { it.value > 1 }
            ?.key
        require(duplicateExtensionId == null) {
            "duplicate pipeline extension id: $duplicateExtensionId"
        }

        val bindings = providers.flatMap { provider ->
            val extensionId = provider.descriptor.id
            val contributionIds = provider.contributions.map { contribution ->
                contributionId(contribution, extensionId)
            }
            val duplicateContributionId = contributionIds
                .groupingBy { it }
                .eachCount()
                .entries
                .firstOrNull { it.value > 1 }
                ?.key
            require(duplicateContributionId == null) {
                "duplicate pipeline contribution id in extension $extensionId: $duplicateContributionId"
            }
            provider.contributions.map { contribution ->
                PipelineContributionBinding(extensionId, contribution)
            }
        }

        val artifactAddons = mutableListOf<PipelineContributionBinding<ArtifactAddonProvider>>()
        val managedFieldPolicies = mutableListOf<PipelineContributionBinding<ManagedFieldPolicyProvider>>()
        bindings.forEach { binding ->
            when (val contribution = binding.contribution) {
                is ArtifactAddonProvider -> artifactAddons +=
                    PipelineContributionBinding(binding.extensionId, contribution)
                is ManagedFieldPolicyProvider -> managedFieldPolicies +=
                    PipelineContributionBinding(binding.extensionId, contribution)
                else -> throw IllegalArgumentException(
                    "unknown pipeline contribution type in extension ${binding.extensionId}: " +
                        contribution.javaClass.name
                )
            }
        }
        val duplicateArtifactAddonId = artifactAddons
            .groupingBy { it.contribution.id }
            .eachCount()
            .entries
            .firstOrNull { it.value > 1 }
            ?.key
        require(duplicateArtifactAddonId == null) {
            "duplicate artifact addon provider id: $duplicateArtifactAddonId"
        }

        return LoadedPipelineExtensions(
            providers = providers,
            contributions = bindings,
            artifactAddons = artifactAddons,
            managedFieldPolicies = managedFieldPolicies,
        )
    }

    internal fun contributionId(
        contribution: PipelineContribution,
        extensionId: String? = null,
    ): String {
        val id = when (contribution) {
            is ArtifactAddonProvider -> contribution.id
            is ManagedFieldPolicyProvider -> contribution.id
            else -> throw IllegalArgumentException(
                "unknown pipeline contribution type" +
                    extensionId?.let { " in extension $it" }.orEmpty() +
                    ": ${contribution.javaClass.name}"
            )
        }
        require(id.isNotBlank() && id == id.trim()) {
            "pipeline contribution id must be non-blank and trimmed: ${contribution.javaClass.name}"
        }
        return id
    }
}

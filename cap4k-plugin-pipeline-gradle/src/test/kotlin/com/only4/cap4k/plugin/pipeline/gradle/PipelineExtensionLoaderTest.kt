package com.only4.cap4k.plugin.pipeline.gradle

import com.only4.cap4k.plugin.pipeline.api.ArtifactAddonContext
import com.only4.cap4k.plugin.pipeline.api.ArtifactAddonProvider
import com.only4.cap4k.plugin.pipeline.api.ArtifactPlanItem
import com.only4.cap4k.plugin.pipeline.api.PIPELINE_EXTENSION_SPI_VERSION
import com.only4.cap4k.plugin.pipeline.api.ManagedFieldPolicyContributionContext
import com.only4.cap4k.plugin.pipeline.api.ManagedFieldPolicyDefinition
import com.only4.cap4k.plugin.pipeline.api.ManagedFieldPolicyProvider
import com.only4.cap4k.plugin.pipeline.api.PipelineContribution
import com.only4.cap4k.plugin.pipeline.api.PipelineExtensionDescriptor
import com.only4.cap4k.plugin.pipeline.api.PipelineExtensionProvider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path
import java.util.ServiceConfigurationError

class PipelineExtensionLoaderTest {

    @Test
    fun `loads extension providers and binds artifact contributions from service loader`(@TempDir serviceRoot: Path) {
        val serviceFile = serviceRoot.resolve(
            "META-INF/services/com.only4.cap4k.plugin.pipeline.api.PipelineExtensionProvider"
        )
        Files.createDirectories(serviceFile.parent)
        Files.writeString(serviceFile, "${ServiceLoadedExtensionProvider::class.java.name}\n")

        URLClassLoader(arrayOf(serviceRoot.toUri().toURL()), javaClass.classLoader).use { classLoader ->
            val loaded = PipelineExtensionLoader.load(classLoader)

            assertEquals(listOf("service-loaded-extension"), loaded.providers.map { it.descriptor.id })
            assertEquals(listOf("service-loaded-addon"), loaded.artifactAddons.map { it.contribution.id })
            assertEquals("service-loaded-extension", loaded.artifactAddons.single().extensionId)
        }
    }

    @Test
    fun `discovers provider from a transitive classpath component`(@TempDir classpathRoot: Path) {
        val directComponent = Files.createDirectories(classpathRoot.resolve("direct-component"))
        val transitiveComponent = Files.createDirectories(classpathRoot.resolve("transitive-component"))
        Files.writeString(directComponent.resolve("direct-marker.txt"), "direct")
        val serviceFile = transitiveComponent.resolve(
            "META-INF/services/com.only4.cap4k.plugin.pipeline.api.PipelineExtensionProvider"
        )
        Files.createDirectories(serviceFile.parent)
        Files.writeString(serviceFile, "${ServiceLoadedExtensionProvider::class.java.name}\n")

        URLClassLoader(
            arrayOf(directComponent.toUri().toURL(), transitiveComponent.toUri().toURL()),
            javaClass.classLoader,
        ).use { classLoader ->
            val loaded = PipelineExtensionLoader.load(classLoader)

            assertEquals(listOf("service-loaded-extension"), loaded.providers.map { it.descriptor.id })
            assertEquals(listOf("service-loaded-addon"), loaded.artifactAddons.map { it.contribution.id })
        }
    }

    @Test
    fun `fails when non-empty extension classpath has no provider`(@TempDir serviceRoot: Path) {
        URLClassLoader(arrayOf(serviceRoot.toUri().toURL()), javaClass.classLoader).use { classLoader ->
            val exception = assertThrows(IllegalArgumentException::class.java) {
                PipelineExtensionLoader.load(classLoader)
            }

            assertEquals(true, exception.message.orEmpty().contains("No PipelineExtensionProvider was found"))
            assertEquals(true, exception.message.orEmpty().contains(serviceRoot.toUri().toURL().toExternalForm()))
        }
    }

    @Test
    fun `surfaces service loader construction failure`(@TempDir serviceRoot: Path) {
        val serviceFile = serviceRoot.resolve(
            "META-INF/services/com.only4.cap4k.plugin.pipeline.api.PipelineExtensionProvider"
        )
        Files.createDirectories(serviceFile.parent)
        Files.writeString(serviceFile, "missing.pipeline.ExtensionProvider\n")

        URLClassLoader(arrayOf(serviceRoot.toUri().toURL()), javaClass.classLoader).use { classLoader ->
            val failure = assertThrows(ServiceConfigurationError::class.java) {
                PipelineExtensionLoader.load(classLoader)
            }
            assertEquals(true, failure.message.orEmpty().contains("missing.pipeline.ExtensionProvider"))
            assertEquals(true, failure.message.orEmpty().contains(serviceRoot.toUri().toURL().toExternalForm()))
        }
    }

    @Test
    fun `classpath loaded provider conflict includes provider and classpath evidence`(@TempDir serviceRoot: Path) {
        val serviceFile = serviceRoot.resolve(
            "META-INF/services/com.only4.cap4k.plugin.pipeline.api.PipelineExtensionProvider"
        )
        Files.createDirectories(serviceFile.parent)
        Files.writeString(
            serviceFile,
            "${ServiceLoadedExtensionProvider::class.java.name}\n${DuplicateServiceLoadedExtensionProvider::class.java.name}\n",
        )

        URLClassLoader(arrayOf(serviceRoot.toUri().toURL()), javaClass.classLoader).use { classLoader ->
            val failure = assertThrows(IllegalArgumentException::class.java) {
                PipelineExtensionLoader.load(classLoader)
            }
            assertEquals(true, failure.message.orEmpty().contains("duplicate pipeline extension id"))
            assertEquals(true, failure.message.orEmpty().contains("service-loaded-extension"))
            assertEquals(true, failure.message.orEmpty().contains(serviceRoot.toUri().toURL().toExternalForm()))
        }
    }

    @Test
    fun `fails when extension ids are duplicated`() {
        val exception = assertThrows(IllegalArgumentException::class.java) {
            PipelineExtensionLoader.validateAndBind(
                listOf(extension("duplicate"), extension("duplicate")),
            )
        }

        assertEquals("duplicate pipeline extension id: duplicate", exception.message)
    }

    @Test
    fun `builds independent typed registries for artifact and managed policy contributions`() {
        val loaded = PipelineExtensionLoader.validateAndBind(
            listOf(
                extension("artifact-only", listOf(addon("sample-addon"))),
                extension("policy-only", listOf(managedPolicy("sample-policy"))),
                extension(
                    "combined",
                    listOf(addon("combined-addon"), managedPolicy("combined-policy")),
                ),
            ),
        )

        assertEquals(listOf("sample-addon", "combined-addon"), loaded.artifactAddons.map { it.contribution.id })
        assertEquals(
            listOf("sample-policy", "combined-policy"),
            loaded.managedFieldPolicies.map { it.contribution.id },
        )
    }

    @Test
    fun `fails when SPI version is unsupported`() {
        val exception = assertThrows(IllegalArgumentException::class.java) {
            PipelineExtensionLoader.validateAndBind(
                listOf(extension("sample", spiVersion = PIPELINE_EXTENSION_SPI_VERSION + 1)),
            )
        }

        assertEquals(
            "unsupported pipeline extension SPI version for sample: 2; expected 1",
            exception.message,
        )
    }

    @Test
    fun `fails when contribution ids are duplicated inside one extension`() {
        val exception = assertThrows(IllegalArgumentException::class.java) {
            PipelineExtensionLoader.validateAndBind(
                listOf(
                    extension(
                        "sample",
                        listOf(addon("duplicate-addon"), addon("duplicate-addon")),
                    ),
                ),
            )
        }

        assertEquals(
            "duplicate pipeline contribution id in extension sample: duplicate-addon",
            exception.message,
        )
    }

    @Test
    fun `fails when artifact addon ids are duplicated across extensions`() {
        val exception = assertThrows(IllegalArgumentException::class.java) {
            PipelineExtensionLoader.validateAndBind(
                listOf(
                    extension("first", listOf(addon("duplicate-addon"))),
                    extension("second", listOf(addon("duplicate-addon"))),
                ),
            )
        }

        assertEquals("duplicate artifact addon provider id: duplicate-addon", exception.message)
    }

    @Test
    fun `fails when contribution type is unknown`() {
        val contribution = object : PipelineContribution {}
        val exception = assertThrows(IllegalArgumentException::class.java) {
            PipelineExtensionLoader.validateAndBind(
                listOf(extension("sample", listOf(contribution))),
            )
        }

        assertEquals(
            "unknown pipeline contribution type in extension sample: ${contribution.javaClass.name}",
            exception.message,
        )
    }

    class ServiceLoadedExtensionProvider : PipelineExtensionProvider {
        override val descriptor: PipelineExtensionDescriptor = PipelineExtensionDescriptor(
            id = "service-loaded-extension",
            spiVersion = PIPELINE_EXTENSION_SPI_VERSION,
        )
        override val contributions: List<PipelineContribution> = listOf(
            addon("service-loaded-addon"),
        )
    }

    class DuplicateServiceLoadedExtensionProvider : PipelineExtensionProvider {
        override val descriptor: PipelineExtensionDescriptor = PipelineExtensionDescriptor(
            id = "service-loaded-extension",
            spiVersion = PIPELINE_EXTENSION_SPI_VERSION,
        )
        override val contributions: List<PipelineContribution> = emptyList()
    }

    companion object {
        private fun extension(
            id: String,
            contributions: List<PipelineContribution> = emptyList(),
            spiVersion: Int = PIPELINE_EXTENSION_SPI_VERSION,
        ): PipelineExtensionProvider = object : PipelineExtensionProvider {
            override val descriptor: PipelineExtensionDescriptor = PipelineExtensionDescriptor(id, spiVersion)
            override val contributions: List<PipelineContribution> = contributions
        }

        private fun addon(id: String): ArtifactAddonProvider = object : ArtifactAddonProvider {
            override val id: String = id

            override fun plan(context: ArtifactAddonContext): List<ArtifactPlanItem> = emptyList()
        }

        private fun managedPolicy(id: String): ManagedFieldPolicyProvider = object : ManagedFieldPolicyProvider {
            override val id: String = id

            override fun definitions(
                context: ManagedFieldPolicyContributionContext,
            ): List<ManagedFieldPolicyDefinition> = emptyList()
        }
    }
}

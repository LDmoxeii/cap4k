package com.only4.cap4k.plugin.pipeline.gradle

import com.only4.cap4k.plugin.pipeline.api.ArtifactAddonContext
import com.only4.cap4k.plugin.pipeline.api.ArtifactAddonProvider
import com.only4.cap4k.plugin.pipeline.api.ArtifactPlanItem
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path

class ArtifactAddonLoaderTest {

    @Test
    fun `loads addon providers from service loader classpath`(@TempDir serviceRoot: Path) {
        val serviceFile = serviceRoot.resolve(
            "META-INF/services/com.only4.cap4k.plugin.pipeline.api.ArtifactAddonProvider"
        )
        Files.createDirectories(serviceFile.parent)
        Files.writeString(serviceFile, "${ServiceLoadedAddonProvider::class.java.name}\n")

        URLClassLoader(arrayOf(serviceRoot.toUri().toURL()), javaClass.classLoader).use { classLoader ->
            val providers = ArtifactAddonLoader.load(classLoader)

            assertEquals(listOf("service-loaded-addon"), providers.map { it.id })
        }
    }

    @Test
    fun `fails when service loader returns duplicate addon ids`() {
        val providers = listOf(
            addonProvider("duplicate-addon"),
            addonProvider("duplicate-addon"),
        )

        val exception = assertThrows(IllegalArgumentException::class.java) {
            ArtifactAddonLoader.validateProviderIds(providers)
        }

        assertEquals("duplicate artifact addon provider id: duplicate-addon", exception.message)
    }

    class ServiceLoadedAddonProvider : ArtifactAddonProvider {
        override val id: String = "service-loaded-addon"

        override fun plan(context: ArtifactAddonContext): List<ArtifactPlanItem> = emptyList()
    }

    private fun addonProvider(id: String): ArtifactAddonProvider =
        object : ArtifactAddonProvider {
            override val id: String = id

            override fun plan(context: ArtifactAddonContext): List<ArtifactPlanItem> = emptyList()
        }
}

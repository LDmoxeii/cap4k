package com.only4.cap4k.plugin.pipeline.gradle

import com.only4.cap4k.plugin.pipeline.api.ArtifactAddonProvider
import java.io.File
import java.net.URLClassLoader
import java.util.ServiceLoader

internal object ArtifactAddonLoader {
    fun classLoader(files: Collection<File>, parent: ClassLoader): URLClassLoader {
        val urls = files.map { it.toURI().toURL() }.toTypedArray()
        return URLClassLoader(urls, parent)
    }

    fun templateClassLoader(provider: ArtifactAddonProvider): URLClassLoader {
        val location = provider.javaClass.protectionDomain?.codeSource?.location
            ?: throw IllegalArgumentException("artifact addon provider ${provider.id} has no code source")
        return URLClassLoader(arrayOf(location), null)
    }

    fun load(classLoader: ClassLoader): List<ArtifactAddonProvider> {
        val providers = ServiceLoader.load(ArtifactAddonProvider::class.java, classLoader).toList()
        validateProviderIds(providers)
        return providers
    }

    fun validateProviderIds(providers: List<ArtifactAddonProvider>) {
        val duplicate = providers
            .groupingBy { it.id }
            .eachCount()
            .entries
            .firstOrNull { it.value > 1 }
            ?.key

        require(duplicate == null) {
            "duplicate artifact addon provider id: $duplicate"
        }
    }
}

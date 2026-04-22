package com.only4.cap4k.plugin.pipeline.core

import com.only4.cap4k.plugin.pipeline.api.BootstrapConfig
import com.only4.cap4k.plugin.pipeline.api.BootstrapMode
import java.nio.file.Files
import java.nio.file.Path

class BootstrapRootStateGuard(
    root: Path,
    private val merger: BootstrapManagedSectionMerger = BootstrapManagedSectionMerger(),
) {
    private val normalizedRoot = root.toAbsolutePath().normalize()

    fun validate(config: BootstrapConfig) {
        validateModulePaths(config)
        if (config.mode == BootstrapMode.PREVIEW_SUBTREE) {
            return
        }

        validateManagedRootFile("build.gradle.kts")
        validateManagedRootFile("settings.gradle.kts")
    }

    private fun validateManagedRootFile(relativePath: String) {
        val file = normalizedRoot.resolve(relativePath).normalize()
        require(Files.isRegularFile(file)) {
            "bootstrap.mode=IN_PLACE requires existing $relativePath at $file"
        }

        val content = Files.readString(file)
        val sectionIds = try {
            merger.validateDocumentStructure(content, "Existing $relativePath")
        } catch (error: IllegalArgumentException) {
            if (error.message?.contains("must contain at least one managed section.") == true) {
                throw IllegalArgumentException(
                    "Existing $relativePath must contain recognized managed markers for section root-host.",
                    error,
                )
            }
            throw error
        }
        require(sectionIds == setOf(ROOT_HOST_SECTION)) {
            "Existing $relativePath must contain exactly the managed section set { root-host }."
        }
    }

    private fun validateModulePaths(config: BootstrapConfig) {
        moduleRoot(config, config.modules.domainModuleName)
        moduleRoot(config, config.modules.applicationModuleName)
        moduleRoot(config, config.modules.adapterModuleName)
    }

    private fun moduleRoot(config: BootstrapConfig, moduleName: String) {
        val outputRoot = when (config.mode) {
            BootstrapMode.IN_PLACE -> normalizedRoot
            BootstrapMode.PREVIEW_SUBTREE -> normalizedRoot.resolve(requireNotNull(config.previewDir)).normalize()
        }
        val modulePath = outputRoot.resolve(moduleName).normalize()
        require(modulePath.startsWith(normalizedRoot)) {
            "bootstrap module path resolves outside project root: $moduleName"
        }
        require(!Files.exists(modulePath) || Files.isDirectory(modulePath)) {
            "bootstrap module path collides with existing non-directory entry: $moduleName"
        }
    }

    private companion object {
        private const val ROOT_HOST_SECTION = "root-host"
    }
}

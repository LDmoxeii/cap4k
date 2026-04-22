package com.only4.cap4k.plugin.pipeline.gradle

import com.only4.cap4k.plugin.pipeline.api.BootstrapConfig
import com.only4.cap4k.plugin.pipeline.api.BootstrapMode
import com.only4.cap4k.plugin.pipeline.api.BootstrapModulesConfig
import com.only4.cap4k.plugin.pipeline.api.BootstrapTemplateConfig
import com.only4.cap4k.plugin.pipeline.api.ConflictPolicy
import org.gradle.api.Project
import org.gradle.api.provider.Property
import java.io.File

class Cap4kBootstrapConfigFactory {

    fun build(project: Project, extension: Cap4kExtension): BootstrapConfig {
        require(extension.bootstrap.enabled.get()) {
            "bootstrap.enabled must be true to run bootstrap tasks."
        }

        val preset = extension.bootstrap.preset.required("bootstrap.preset")
        require(preset == "ddd-multi-module") {
            "unsupported bootstrap preset: $preset"
        }

        val slots = extension.bootstrap.slots.bindings(project)
        slots.forEach { binding ->
            if (binding.role != null) {
                require(binding.role in setOf("domain", "application", "adapter", "start")) {
                    "unsupported bootstrap slot role: ${binding.role}"
                }
            }
        }

        val mode = extension.bootstrap.mode.orNull ?: BootstrapMode.IN_PLACE
        val rawPreviewDir = extension.bootstrap.previewDir.orNull?.trim()
        val previewDir = when (mode) {
            BootstrapMode.PREVIEW_SUBTREE -> {
                require(!rawPreviewDir.isNullOrEmpty()) {
                    "bootstrap.previewDir is required when bootstrap.mode=PREVIEW_SUBTREE."
                }
                require(isSafeRelativePreviewDir(rawPreviewDir)) {
                    "bootstrap.previewDir must be a safe relative path."
                }
                rawPreviewDir
            }

            BootstrapMode.IN_PLACE -> {
                require(rawPreviewDir.isNullOrEmpty()) {
                    "bootstrap.previewDir is only supported when bootstrap.mode=PREVIEW_SUBTREE."
                }
                null
            }
        }

        return BootstrapConfig(
            preset = preset,
            projectName = extension.bootstrap.projectName.required("bootstrap.projectName"),
            basePackage = extension.bootstrap.basePackage.required("bootstrap.basePackage"),
            projectDir = project.projectDir.toPath().toAbsolutePath().normalize().toString(),
            modules = BootstrapModulesConfig(
                domainModuleName = extension.bootstrap.modules.domainModuleName.required("bootstrap.modules.domainModuleName"),
                applicationModuleName = extension.bootstrap.modules.applicationModuleName.required("bootstrap.modules.applicationModuleName"),
                adapterModuleName = extension.bootstrap.modules.adapterModuleName.required("bootstrap.modules.adapterModuleName"),
                startModuleName = extension.bootstrap.modules.startModuleName.required("bootstrap.modules.startModuleName"),
            ),
            templates = BootstrapTemplateConfig(
                preset = extension.bootstrap.templates.preset.orNull?.trim().orEmpty().ifEmpty { "ddd-default-bootstrap" },
                overrideDirs = extension.bootstrap.templates.overrideDirs.files.map(File::getAbsolutePath).sorted(),
            ),
            slots = slots,
            conflictPolicy = ConflictPolicy.valueOf(
                extension.bootstrap.conflictPolicy.orNull?.trim().orEmpty().ifEmpty { "FAIL" }
            ),
            mode = mode,
            previewDir = previewDir,
        )
    }
}

private fun Property<String>.required(path: String): String =
    orNull?.trim()?.takeIf { it.isNotEmpty() } ?: throw IllegalArgumentException("$path is required.")

private fun isSafeRelativePreviewDir(path: String): Boolean {
    if (path.isBlank()) return false
    if (path.startsWith("/") || path.startsWith("\\")) return false
    if (path.contains("\\")) return false
    if (path.contains(":")) return false
    val segments = path.split('/')
    if (segments.any { it.isBlank() || it == "." || it == ".." }) return false
    return true
}

package com.only4.cap4k.plugin.pipeline.gradle

import com.only4.cap4k.plugin.pipeline.api.BootstrapConfig
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
                require(binding.role in setOf("domain", "application", "adapter")) {
                    "unsupported bootstrap slot role: ${binding.role}"
                }
            }
        }

        return BootstrapConfig(
            preset = preset,
            projectName = extension.bootstrap.projectName.required("bootstrap.projectName"),
            basePackage = extension.bootstrap.basePackage.required("bootstrap.basePackage"),
            modules = BootstrapModulesConfig(
                domainModuleName = extension.bootstrap.modules.domainModuleName.required("bootstrap.modules.domainModuleName"),
                applicationModuleName = extension.bootstrap.modules.applicationModuleName.required("bootstrap.modules.applicationModuleName"),
                adapterModuleName = extension.bootstrap.modules.adapterModuleName.required("bootstrap.modules.adapterModuleName"),
            ),
            templates = BootstrapTemplateConfig(
                preset = extension.bootstrap.templates.preset.orNull?.trim().orEmpty().ifEmpty { "ddd-default-bootstrap" },
                overrideDirs = extension.bootstrap.templates.overrideDirs.files.map(File::getAbsolutePath).sorted(),
            ),
            slots = slots,
            conflictPolicy = ConflictPolicy.valueOf(
                extension.bootstrap.conflictPolicy.orNull?.trim().orEmpty().ifEmpty { "FAIL" }
            ),
        )
    }
}

private fun Property<String>.required(path: String): String =
    orNull?.trim()?.takeIf { it.isNotEmpty() } ?: throw IllegalArgumentException("$path is required.")

package com.only4.cap4k.plugin.pipeline.bootstrap

import com.only4.cap4k.plugin.pipeline.api.BootstrapConfig
import com.only4.cap4k.plugin.pipeline.api.BootstrapModulesConfig
import com.only4.cap4k.plugin.pipeline.api.BootstrapSlotBinding
import com.only4.cap4k.plugin.pipeline.api.BootstrapSlotKind
import com.only4.cap4k.plugin.pipeline.api.BootstrapTemplateConfig
import com.only4.cap4k.plugin.pipeline.api.ConflictPolicy
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class DddMultiModuleBootstrapPresetProviderTest {

    private val config = BootstrapConfig(
        preset = "ddd-multi-module",
        projectName = "only-danmuku",
        basePackage = "edu.only4.danmuku",
        modules = BootstrapModulesConfig(
            domainModuleName = "only-danmuku-domain",
            applicationModuleName = "only-danmuku-application",
            adapterModuleName = "only-danmuku-adapter",
        ),
        templates = BootstrapTemplateConfig("ddd-default-bootstrap", emptyList()),
        slots = listOf(
            BootstrapSlotBinding(BootstrapSlotKind.ROOT, sourceDir = "src/test/resources/slots/root"),
            BootstrapSlotBinding(
                BootstrapSlotKind.MODULE_PACKAGE,
                role = "domain",
                sourceDir = "src/test/resources/slots/domain-package"
            ),
        ),
        conflictPolicy = ConflictPolicy.FAIL,
    )

    @Test
    fun `plan emits fixed root and module templates under project name subtree`() {
        val items = DddMultiModuleBootstrapPresetProvider().plan(config)

        assertTrue(items.any { it.templateId == "bootstrap/root/settings.gradle.kts.peb" && it.outputPath == "only-danmuku/settings.gradle.kts" })
        assertTrue(items.any { it.templateId == "bootstrap/root/build.gradle.kts.peb" && it.outputPath == "only-danmuku/build.gradle.kts" })
        assertTrue(items.any { it.templateId == "bootstrap/module/domain-build.gradle.kts.peb" && it.outputPath == "only-danmuku/only-danmuku-domain/build.gradle.kts" })
        assertTrue(items.any { it.templateId == "bootstrap/module/package-marker.kt.peb" && it.outputPath.contains("src/main/kotlin/edu/only4/danmuku/domain") })
    }

    @Test
    fun `plan emits slot items with slot attribution`() {
        val items = DddMultiModuleBootstrapPresetProvider().plan(config)

        assertTrue(items.any { it.slotId == "root" && it.sourcePath!!.endsWith("slots/root/README.md.peb") })
        assertTrue(items.any { it.slotId == "module-package:domain" && it.outputPath.contains("only-danmuku-domain/src/main/kotlin/edu/only4/danmuku") })
    }

    @Test
    fun `plan fails when project name is not a safe path segment`() {
        val invalidConfig = config.copy(projectName = "../shared")

        val error = assertThrows(IllegalArgumentException::class.java) {
            DddMultiModuleBootstrapPresetProvider().plan(invalidConfig)
        }

        assertTrue(error.message!!.contains("bootstrap.projectName"))
    }

    @Test
    fun `plan fails when configured slot source dir does not exist`() {
        val invalidConfig = config.copy(
            slots = listOf(
                BootstrapSlotBinding(
                    kind = BootstrapSlotKind.ROOT,
                    sourceDir = "src/test/resources/slots/not-exists"
                )
            )
        )

        val error = assertThrows(IllegalArgumentException::class.java) {
            DddMultiModuleBootstrapPresetProvider().plan(invalidConfig)
        }

        assertTrue(error.message!!.contains("bootstrap slot sourceDir"))
    }
}

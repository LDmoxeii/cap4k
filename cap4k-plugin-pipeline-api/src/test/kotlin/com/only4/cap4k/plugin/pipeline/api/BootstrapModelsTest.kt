package com.only4.cap4k.plugin.pipeline.api

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BootstrapModelsTest {

    @Test
    fun `bootstrap config includes mode and optional preview directory`() {
        val config = BootstrapConfig(
            preset = "ddd-multi-module",
            projectName = "demo",
            basePackage = "com.acme.demo",
            modules = BootstrapModulesConfig(
                domainModuleName = "demo-domain",
                applicationModuleName = "demo-application",
                adapterModuleName = "demo-adapter",
                startModuleName = "demo-start",
            ),
            templates = BootstrapTemplateConfig(
                preset = "ddd-default-bootstrap",
                overrideDirs = emptyList(),
            ),
            slots = emptyList(),
            conflictPolicy = ConflictPolicy.FAIL,
            mode = BootstrapMode.PREVIEW_SUBTREE,
            previewDir = "preview/demo",
        )

        assertEquals(BootstrapMode.PREVIEW_SUBTREE, config.mode)
        assertEquals("preview/demo", config.previewDir)
    }

    @Test
    fun `bootstrap slot id exposes bounded role-aware naming`() {
        val binding = BootstrapSlotBinding(
            kind = BootstrapSlotKind.MODULE_PACKAGE,
            role = "domain",
            sourceDir = "codegen/bootstrap-slots/domain-package"
        )

        assertEquals("module-package:domain", binding.slotId)
    }

    @Test
    fun `bootstrap plan item requires template id or slot source path`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            BootstrapPlanItem(
                presetId = "ddd-multi-module",
                outputPath = "demo/settings.gradle.kts",
                conflictPolicy = ConflictPolicy.FAIL,
            )
        }

        assertTrue(error.message!!.contains("templateId or sourcePath"))
    }

    @Test
    fun `bootstrap plan item accepts source path without template id`() {
        val item = BootstrapPlanItem(
            presetId = "ddd-multi-module",
            outputPath = "demo/settings.gradle.kts",
            conflictPolicy = ConflictPolicy.FAIL,
            sourcePath = "codegen/bootstrap-slots/root/settings.gradle.kts",
        )

        assertEquals("codegen/bootstrap-slots/root/settings.gradle.kts", item.sourcePath)
    }

    @Test
    fun `bootstrap slot id maps root kind`() {
        val binding = BootstrapSlotBinding(
            kind = BootstrapSlotKind.ROOT,
            sourceDir = "codegen/bootstrap-slots/root"
        )

        assertEquals("root", binding.slotId)
    }

    @Test
    fun `bootstrap slot id maps build logic kind`() {
        val binding = BootstrapSlotBinding(
            kind = BootstrapSlotKind.BUILD_LOGIC,
            sourceDir = "codegen/bootstrap-slots/build-logic"
        )

        assertEquals("build-logic", binding.slotId)
    }

    @Test
    fun `bootstrap slot id maps module root kind`() {
        val binding = BootstrapSlotBinding(
            kind = BootstrapSlotKind.MODULE_ROOT,
            role = "application",
            sourceDir = "codegen/bootstrap-slots/application-root"
        )

        assertEquals("module-root:application", binding.slotId)
    }

    @Test
    fun `bootstrap slot id maps module resources kind`() {
        val binding = BootstrapSlotBinding(
            kind = BootstrapSlotKind.MODULE_RESOURCES,
            role = "adapter",
            sourceDir = "codegen/bootstrap-slots/adapter-resources"
        )

        assertEquals("module-resources:adapter", binding.slotId)
    }
}

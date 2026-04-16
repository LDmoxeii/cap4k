package com.only4.cap4k.plugin.pipeline.api

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BootstrapModelsTest {

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
}

package com.only4.cap4k.plugin.pipeline.bootstrap

import com.only4.cap4k.plugin.pipeline.api.BootstrapConfig
import com.only4.cap4k.plugin.pipeline.api.BootstrapModulesConfig
import com.only4.cap4k.plugin.pipeline.api.BootstrapMode
import com.only4.cap4k.plugin.pipeline.api.BootstrapSlotBinding
import com.only4.cap4k.plugin.pipeline.api.BootstrapSlotKind
import com.only4.cap4k.plugin.pipeline.api.BootstrapTemplateConfig
import com.only4.cap4k.plugin.pipeline.api.ConflictPolicy
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertEquals
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
            startModuleName = "only-danmuku-start",
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
        mode = BootstrapMode.IN_PLACE,
        previewDir = null,
    )

    @Test
    fun `plan emits fixed root and module templates in place by default`() {
        val items = DddMultiModuleBootstrapPresetProvider().plan(config)

        assertTrue(items.any { it.templateId == "bootstrap/root/settings.gradle.kts.peb" && it.outputPath == "settings.gradle.kts" })
        assertTrue(items.any { it.templateId == "bootstrap/root/build.gradle.kts.peb" && it.outputPath == "build.gradle.kts" })
        assertTrue(items.any { it.templateId == "bootstrap/module/domain-build.gradle.kts.peb" && it.outputPath == "only-danmuku-domain/build.gradle.kts" })
    }

    @Test
    fun `plan emits start module build and start application templates`() {
        val items = DddMultiModuleBootstrapPresetProvider().plan(config)

        assertTrue(items.any {
            it.templateId == "bootstrap/module/start-build.gradle.kts.peb" &&
                it.outputPath == "only-danmuku-start/build.gradle.kts"
        })
        assertTrue(items.any {
            it.templateId == "bootstrap/module/start-application.kt.peb" &&
                it.outputPath == "only-danmuku-start/src/main/kotlin/edu/only4/danmuku/StartApplication.kt"
        })
    }

    @Test
    fun `plan no longer emits bootstrap marker templates`() {
        val items = DddMultiModuleBootstrapPresetProvider().plan(config)

        assertTrue(items.none { it.templateId == "bootstrap/module/package-marker.kt.peb" })
    }

    @Test
    fun `plan emits fixed root and module templates under preview subtree when configured`() {
        val previewConfig = config.copy(mode = BootstrapMode.PREVIEW_SUBTREE, previewDir = "bootstrap-preview")
        val items = DddMultiModuleBootstrapPresetProvider().plan(previewConfig)

        assertTrue(items.any {
            it.templateId == "bootstrap/root/settings.gradle.kts.peb" &&
                it.outputPath == "bootstrap-preview/settings.gradle.kts"
        })
        assertTrue(items.any {
            it.templateId == "bootstrap/root/build.gradle.kts.peb" &&
                it.outputPath == "bootstrap-preview/build.gradle.kts"
        })
        assertTrue(items.any {
            it.templateId == "bootstrap/module/domain-build.gradle.kts.peb" &&
                it.outputPath == "bootstrap-preview/only-danmuku-domain/build.gradle.kts"
        })
        assertTrue(items.any {
            it.templateId == "bootstrap/module/start-build.gradle.kts.peb" &&
                it.outputPath == "bootstrap-preview/only-danmuku-start/build.gradle.kts"
        })
        assertTrue(items.any {
            it.templateId == "bootstrap/module/start-application.kt.peb" &&
                it.outputPath == "bootstrap-preview/only-danmuku-start/src/main/kotlin/edu/only4/danmuku/StartApplication.kt"
        })
    }

    @Test
    fun `plan emits slot items with slot attribution rebased in place`() {
        val items = DddMultiModuleBootstrapPresetProvider().plan(config)

        assertTrue(items.any { it.slotId == "root" && it.sourcePath!!.endsWith("slots/root/README.md.peb") })
        assertTrue(items.any {
            it.slotId == "module-package:domain" &&
                it.outputPath == "only-danmuku-domain/src/main/kotlin/edu/only4/danmuku/domain/DomainSlotMarker.kt"
        })
    }

    @Test
    fun `bootstrap context preserves template and slot config with windows safe paths`() {
        val context = bootstrapContext(
            config.copy(
                templates = BootstrapTemplateConfig(
                    preset = "custom-bootstrap-preset",
                    overrideDirs = listOf("C:\\cap4k\\overrides", "D:\\cap4k\\more-overrides"),
                ),
                slots = listOf(
                    BootstrapSlotBinding(BootstrapSlotKind.ROOT, sourceDir = "C:\\cap4k\\slots\\root"),
                    BootstrapSlotBinding(
                        BootstrapSlotKind.MODULE_PACKAGE,
                        role = "domain",
                        sourceDir = "D:\\cap4k\\slots\\domain-package",
                    ),
                ),
            )
        )

        assertEquals("custom-bootstrap-preset", context["templatePreset"])
        assertEquals("only-danmuku-start", context["startModuleName"])
        assertEquals(listOf("C:/cap4k/overrides", "D:/cap4k/more-overrides"), context["templateOverrideDirs"])
        val slotStatements = context["slotStatements"] as List<*>
        assertEquals(
            listOf(
                "root.from(\"C:/cap4k/slots/root\")",
                "modulePackage(\"domain\").from(\"D:/cap4k/slots/domain-package\")",
            ),
            slotStatements,
        )
    }

    @Test
    fun `bootstrap context escapes kotlin dsl path literals for dollar and quote characters`() {
        val context = bootstrapContext(
            config.copy(
                mode = BootstrapMode.PREVIEW_SUBTREE,
                previewDir = "preview\\\$slot\"dir",
                templates = BootstrapTemplateConfig(
                    preset = "custom-bootstrap-preset",
                    overrideDirs = listOf("C:\\cap4k\\\$tmp", "D:\\cap4k\\quote\"dir"),
                ),
                slots = listOf(
                    BootstrapSlotBinding(BootstrapSlotKind.ROOT, sourceDir = "E:\\cap4k\\\$slot-root"),
                    BootstrapSlotBinding(
                        BootstrapSlotKind.MODULE_PACKAGE,
                        role = "domain",
                        sourceDir = "F:\\cap4k\\quote\"slot",
                    ),
                ),
            )
        )

        assertEquals(listOf("C:/cap4k/\\\$tmp", "D:/cap4k/quote\\\"dir"), context["templateOverrideDirs"])
        assertEquals("preview/\\\$slot\\\"dir", context["previewDir"])
        val slotStatements = context["slotStatements"] as List<*>
        assertEquals(
            listOf(
                "root.from(\"E:/cap4k/\\\$slot-root\")",
                "modulePackage(\"domain\").from(\"F:/cap4k/quote\\\"slot\")",
            ),
            slotStatements,
        )
    }

    @Test
    fun `module package output path prepends role package root when slot path is not package-rooted`() {
        val outputPath = resolveSlotOutputPath(
            binding = BootstrapSlotBinding(
                kind = BootstrapSlotKind.MODULE_PACKAGE,
                role = "domain",
                sourceDir = "src/test/resources/slots/domain-package",
            ),
            renderedRelativePath = "SmokeDomainMarker.kt",
            config = config,
        )

        assertEquals(
            "only-danmuku-domain/src/main/kotlin/edu/only4/danmuku/domain/SmokeDomainMarker.kt",
            outputPath
        )
    }

    @Test
    fun `module package output path keeps role package-rooted slot path without double prefix`() {
        val outputPath = resolveSlotOutputPath(
            binding = BootstrapSlotBinding(
                kind = BootstrapSlotKind.MODULE_PACKAGE,
                role = "domain",
                sourceDir = "src/test/resources/slots/domain-package",
            ),
            renderedRelativePath = "edu/only4/danmuku/domain/DomainSlotMarker.kt",
            config = config,
        )

        assertEquals(
            "only-danmuku-domain/src/main/kotlin/edu/only4/danmuku/domain/DomainSlotMarker.kt",
            outputPath
        )
    }

    @Test
    fun `module package output path resolves role specific roots`() {
        val rootByRole = mapOf(
            "domain" to "edu/only4/danmuku/domain",
            "application" to "edu/only4/danmuku/application",
            "adapter" to "edu/only4/danmuku/adapter",
            "start" to "edu/only4/danmuku",
        )

        rootByRole.forEach { (role, packageRoot) ->
            val outputPath = resolveSlotOutputPath(
                binding = BootstrapSlotBinding(
                    kind = BootstrapSlotKind.MODULE_PACKAGE,
                    role = role,
                    sourceDir = "src/test/resources/slots/$role-package",
                ),
                renderedRelativePath = "Smoke.kt",
                config = config,
            )

            assertEquals(
                "${resolveModuleName(role, config)}/src/main/kotlin/$packageRoot/Smoke.kt",
                outputPath
            )
        }
    }

    @Test
    fun `module resources output path resolves module resources root`() {
        val outputPath = resolveSlotOutputPath(
            binding = BootstrapSlotBinding(
                kind = BootstrapSlotKind.MODULE_RESOURCES,
                role = "start",
                sourceDir = "src/test/resources/slots/start-resources",
            ),
            renderedRelativePath = "application.yml",
            config = config,
        )

        assertEquals(
            "only-danmuku-start/src/main/resources/application.yml",
            outputPath
        )
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
    fun `plan fails when start module name is not a safe path segment`() {
        val invalidConfig = config.copy(
            modules = config.modules.copy(startModuleName = "../start-shared")
        )

        val error = assertThrows(IllegalArgumentException::class.java) {
            DddMultiModuleBootstrapPresetProvider().plan(invalidConfig)
        }

        assertTrue(error.message!!.contains("bootstrap.modules.startModuleName"))
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

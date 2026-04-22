package com.only4.cap4k.plugin.pipeline.core

import com.only4.cap4k.plugin.pipeline.api.BootstrapConfig
import com.only4.cap4k.plugin.pipeline.api.BootstrapMode
import com.only4.cap4k.plugin.pipeline.api.BootstrapModulesConfig
import com.only4.cap4k.plugin.pipeline.api.BootstrapTemplateConfig
import com.only4.cap4k.plugin.pipeline.api.ConflictPolicy
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files

class BootstrapRootStateGuardTest {

    @Test
    fun `validate allows preview subtree without existing root host files`() {
        val root = Files.createTempDirectory("bootstrap-root-guard-preview")

        assertDoesNotThrow {
            BootstrapRootStateGuard(root).validate(baseConfig().copy(mode = BootstrapMode.PREVIEW_SUBTREE, previewDir = "bootstrap-preview"))
        }
    }

    @Test
    fun `validate fails in place when root build file is missing`() {
        val root = Files.createTempDirectory("bootstrap-root-guard-missing-build")
        Files.writeString(root.resolve("settings.gradle.kts"), managedSettings())

        val error = assertThrows(IllegalArgumentException::class.java) {
            BootstrapRootStateGuard(root).validate(baseConfig())
        }

        assertTrue(error.message!!.contains("build.gradle.kts"))
    }

    @Test
    fun `validate fails in place when root host markers are missing`() {
        val root = Files.createTempDirectory("bootstrap-root-guard-markers")
        Files.writeString(root.resolve("build.gradle.kts"), "plugins { id(\"com.only4.cap4k.plugin.pipeline\") }")
        Files.writeString(root.resolve("settings.gradle.kts"), "rootProject.name = \"demo\"")

        val error = assertThrows(IllegalArgumentException::class.java) {
            BootstrapRootStateGuard(root).validate(baseConfig())
        }

        assertTrue(error.message!!.contains("root-host"))
    }

    @Test
    fun `validate fails in place when root host markers are malformed`() {
        val root = Files.createTempDirectory("bootstrap-root-guard-malformed-markers")
        Files.writeString(
            root.resolve("build.gradle.kts"),
            """
                // [cap4k-bootstrap:managed-begin:root-host]
                cap4k {
                    bootstrap {
                        enabled.set(true)
                    }
                }
                // [cap4k-bootstrap:managed-end:other-section]
            """.trimIndent()
        )
        Files.writeString(root.resolve("settings.gradle.kts"), managedSettings())

        val error = assertThrows(IllegalArgumentException::class.java) {
            BootstrapRootStateGuard(root).validate(baseConfig())
        }

        assertTrue(error.message!!.contains("mismatched managed end marker"))
    }

    @Test
    fun `validate fails when module path collides with existing file`() {
        val root = Files.createTempDirectory("bootstrap-root-guard-module-collision")
        Files.writeString(root.resolve("build.gradle.kts"), managedBuild())
        Files.writeString(root.resolve("settings.gradle.kts"), managedSettings())
        Files.writeString(root.resolve("demo-domain"), "not a directory")

        val error = assertThrows(IllegalArgumentException::class.java) {
            BootstrapRootStateGuard(root).validate(baseConfig())
        }

        assertTrue(error.message!!.contains("demo-domain"))
    }

    @Test
    fun `validate accepts managed in place host root`() {
        val root = Files.createTempDirectory("bootstrap-root-guard-managed")
        Files.writeString(root.resolve("build.gradle.kts"), managedBuild())
        Files.writeString(root.resolve("settings.gradle.kts"), managedSettings())

        assertDoesNotThrow {
            BootstrapRootStateGuard(root).validate(baseConfig())
        }
    }

    private fun baseConfig(): BootstrapConfig =
        BootstrapConfig(
            preset = "ddd-multi-module",
            projectName = "demo",
            basePackage = "com.acme.demo",
            modules = BootstrapModulesConfig("demo-domain", "demo-application", "demo-adapter"),
            templates = BootstrapTemplateConfig("ddd-default-bootstrap", emptyList()),
            slots = emptyList(),
            conflictPolicy = ConflictPolicy.FAIL,
            mode = BootstrapMode.IN_PLACE,
            previewDir = null,
        )

    private fun managedBuild(): String =
        """
            import com.only4.cap4k.plugin.pipeline.api.BootstrapMode

            plugins {
                id("com.only4.cap4k.plugin.pipeline")
            }

            // [cap4k-bootstrap:managed-begin:root-host]
            cap4k {
                bootstrap {
                    enabled.set(true)
                    preset.set("ddd-multi-module")
                    mode.set(BootstrapMode.IN_PLACE)
                    projectName.set("demo")
                    basePackage.set("com.acme.demo")
                    modules {
                        domainModuleName.set("demo-domain")
                        applicationModuleName.set("demo-application")
                        adapterModuleName.set("demo-adapter")
                    }
                }
            }
            // [cap4k-bootstrap:managed-end:root-host]
        """.trimIndent()

    private fun managedSettings(): String =
        """
            // [cap4k-bootstrap:managed-begin:root-host]
            rootProject.name = "demo"

            include(":demo-domain")
            include(":demo-application")
            include(":demo-adapter")
            // [cap4k-bootstrap:managed-end:root-host]
        """.trimIndent()
}

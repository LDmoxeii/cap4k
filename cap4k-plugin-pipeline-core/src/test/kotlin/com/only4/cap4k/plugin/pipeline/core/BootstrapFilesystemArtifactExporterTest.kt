package com.only4.cap4k.plugin.pipeline.core

import com.only4.cap4k.plugin.pipeline.api.BootstrapConfig
import com.only4.cap4k.plugin.pipeline.api.BootstrapMode
import com.only4.cap4k.plugin.pipeline.api.BootstrapModulesConfig
import com.only4.cap4k.plugin.pipeline.api.BootstrapTemplateConfig
import com.only4.cap4k.plugin.pipeline.api.ConflictPolicy
import com.only4.cap4k.plugin.pipeline.api.RenderedArtifact
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files

class BootstrapFilesystemArtifactExporterTest {

    @Test
    fun `export merges existing managed root files and delegates ordinary files`() {
        val root = Files.createTempDirectory("bootstrap-exporter-in-place")
        val existingRootBuild = root.resolve("build.gradle.kts")
        Files.writeString(
            existingRootBuild,
            """
                plugins {
                    id("custom-root")
                }

                // [cap4k-bootstrap:managed-begin:root-plugins]
                plugins {
                    id("old.plugin")
                }
                // [cap4k-bootstrap:managed-end:root-plugins]

                dependencies {
                    implementation("user:dep:1.0")
                }
            """.trimIndent()
        )

        val exporter = BootstrapFilesystemArtifactExporter(root, inPlaceConfig())
        val writtenPaths = exporter.export(
            listOf(
                RenderedArtifact(
                    outputPath = "build.gradle.kts",
                    content = """
                        // [cap4k-bootstrap:managed-begin:root-plugins]
                        plugins {
                            id("com.only4.cap4k.bootstrap")
                        }
                        // [cap4k-bootstrap:managed-end:root-plugins]
                    """.trimIndent(),
                    conflictPolicy = ConflictPolicy.FAIL,
                ),
                RenderedArtifact(
                    outputPath = "demo-domain/build.gradle.kts",
                    content = "plugins { kotlin(\"jvm\") }",
                    conflictPolicy = ConflictPolicy.FAIL,
                ),
            )
        )

        assertEquals(2, writtenPaths.size)
        assertEquals(
            """
                plugins {
                    id("custom-root")
                }

                // [cap4k-bootstrap:managed-begin:root-plugins]
                plugins {
                    id("com.only4.cap4k.bootstrap")
                }
                // [cap4k-bootstrap:managed-end:root-plugins]

                dependencies {
                    implementation("user:dep:1.0")
                }
            """.trimIndent(),
            Files.readString(existingRootBuild)
        )
        assertEquals("plugins { kotlin(\"jvm\") }", Files.readString(root.resolve("demo-domain/build.gradle.kts")))
    }

    @Test
    fun `export treats preview root files under preview dir as managed`() {
        val root = Files.createTempDirectory("bootstrap-exporter-preview")
        val previewBuild = root.resolve("bootstrap-preview/build.gradle.kts")
        Files.createDirectories(previewBuild.parent)
        Files.writeString(
            previewBuild,
            """
                // [cap4k-bootstrap:managed-begin:root-plugins]
                plugins {
                    id("old.plugin")
                }
                // [cap4k-bootstrap:managed-end:root-plugins]
            """.trimIndent()
        )

        val exporter = BootstrapFilesystemArtifactExporter(
            root,
            inPlaceConfig().copy(mode = BootstrapMode.PREVIEW_SUBTREE, previewDir = "bootstrap-preview")
        )

        exporter.export(
            listOf(
                RenderedArtifact(
                    outputPath = "bootstrap-preview/build.gradle.kts",
                    content = """
                        // [cap4k-bootstrap:managed-begin:root-plugins]
                        plugins {
                            id("com.only4.cap4k.preview")
                        }
                        // [cap4k-bootstrap:managed-end:root-plugins]
                    """.trimIndent(),
                    conflictPolicy = ConflictPolicy.FAIL,
                )
            )
        )

        assertTrue(Files.readString(previewBuild).contains("com.only4.cap4k.preview"))
        assertTrue(!Files.readString(previewBuild).contains("old.plugin"))
    }

    @Test
    fun `export keeps relative path safety for managed root candidates`() {
        val root = Files.createTempDirectory("bootstrap-exporter-safety")
        val exporter = BootstrapFilesystemArtifactExporter(
            root,
            inPlaceConfig().copy(mode = BootstrapMode.PREVIEW_SUBTREE, previewDir = "..")
        )

        val error = assertThrows(IllegalArgumentException::class.java) {
            exporter.export(
                listOf(
                    RenderedArtifact(
                        outputPath = "../build.gradle.kts",
                        content = "plugins {}",
                        conflictPolicy = ConflictPolicy.OVERWRITE,
                    )
                )
            )
        }

        assertTrue(error.message!!.contains("outside export root"))
    }

    private fun inPlaceConfig(): BootstrapConfig =
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
}

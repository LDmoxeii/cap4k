package com.only4.cap4k.plugin.pipeline.source.ksp

import com.only4.cap4k.plugin.pipeline.api.ConflictPolicy
import com.only4.cap4k.plugin.pipeline.api.KspMetadataSnapshot
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig
import com.only4.cap4k.plugin.pipeline.api.ProjectLayout
import com.only4.cap4k.plugin.pipeline.api.SourceConfig
import com.only4.cap4k.plugin.pipeline.api.TemplateConfig
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class KspMetadataSourceProviderTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `reads aggregate root metadata from aggregate files`() {
        val fixtureDir = File("src/test/resources/fixtures/metadata").path
        val config = ProjectConfig(
            basePackage = "com.only4.cap4k",
            layout = ProjectLayout.SINGLE_MODULE,
            modules = emptyMap(),
            sources = mapOf(
                "ksp-metadata" to SourceConfig(
                    enabled = true,
                    options = mapOf("inputDir" to fixtureDir),
                ),
            ),
            generators = emptyMap(),
            templates = TemplateConfig(
                preset = "default",
                overrideDirs = emptyList(),
                conflictPolicy = ConflictPolicy.SKIP,
            ),
        )

        val provider = KspMetadataSourceProvider()
        val snapshot = provider.collect(config) as KspMetadataSnapshot

        assertEquals(1, snapshot.aggregates.size)
        assertEquals("Order", snapshot.aggregates.first().aggregateName)
        assertEquals(
            "com.acme.demo.domain.aggregates.order.Order",
            snapshot.aggregates.first().rootQualifiedName,
        )
        assertEquals(
            "com.acme.demo.domain.aggregates.order",
            snapshot.aggregates.first().rootPackageName,
        )
        assertEquals("Order", snapshot.aggregates.first().rootClassName)
    }

    @Test
    fun `loads only aggregate files and orders by file name`() {
        val alphaFile = tempDir.resolve("aggregate-Alpha.json")
        val betaFile = tempDir.resolve("aggregate-Beta.json")
        val ignoredFile = tempDir.resolve("aggregates-index.json")
        Files.writeString(
            betaFile,
            """
            {
              "aggregateName": "Beta",
              "aggregateRoot": {
                "className": "BetaRoot",
                "qualifiedName": "com.acme.demo.beta.BetaRoot",
                "packageName": "com.acme.demo.beta"
              }
            }
            """.trimIndent(),
            StandardCharsets.UTF_8,
        )
        Files.writeString(
            alphaFile,
            """
            {
              "aggregateName": "Alpha",
              "aggregateRoot": {
                "className": "AlphaRoot",
                "qualifiedName": "com.acme.demo.alpha.AlphaRoot",
                "packageName": "com.acme.demo.alpha"
              }
            }
            """.trimIndent(),
            StandardCharsets.UTF_8,
        )
        Files.writeString(
            ignoredFile,
            """
            {
              "aggregateName": "Ignored",
              "aggregateRoot": {
                "className": "IgnoredRoot",
                "qualifiedName": "com.acme.demo.ignored.IgnoredRoot",
                "packageName": "com.acme.demo.ignored"
              }
            }
            """.trimIndent(),
            StandardCharsets.UTF_8,
        )

        val config = projectConfig(inputDir = tempDir.toString())

        val provider = KspMetadataSourceProvider()
        val snapshot = provider.collect(config) as KspMetadataSnapshot

        assertEquals(2, snapshot.aggregates.size)
        assertEquals("Alpha", snapshot.aggregates[0].aggregateName)
        assertEquals("Beta", snapshot.aggregates[1].aggregateName)
    }

    @Test
    fun `throws when inputDir is missing or invalid`() {
        val provider = KspMetadataSourceProvider()
        val missingOptionConfig = projectConfig(options = emptyMap())
        assertThrows(IllegalArgumentException::class.java) {
            provider.collect(missingOptionConfig)
        }

        val blankConfig = projectConfig(inputDir = "   ")
        assertThrows(IllegalArgumentException::class.java) {
            provider.collect(blankConfig)
        }

        val notFoundConfig = projectConfig(inputDir = tempDir.resolve("not-found").toString())
        assertThrows(IllegalArgumentException::class.java) {
            provider.collect(notFoundConfig)
        }

        val fileConfig = projectConfig(inputDir = Files.createTempFile(tempDir, "single", ".json").toString())
        assertThrows(IllegalArgumentException::class.java) {
            provider.collect(fileConfig)
        }
    }

    private fun projectConfig(
        inputDir: String? = null,
        options: Map<String, Any?> = mapOf("inputDir" to inputDir),
    ): ProjectConfig {
        val resolvedOptions = if (inputDir != null) {
            mapOf("inputDir" to inputDir)
        } else {
            options
        }
        return ProjectConfig(
            basePackage = "com.only4.cap4k",
            layout = ProjectLayout.SINGLE_MODULE,
            modules = emptyMap(),
            sources = mapOf(
                "ksp-metadata" to SourceConfig(
                    enabled = true,
                    options = resolvedOptions,
                ),
            ),
            generators = emptyMap(),
            templates = TemplateConfig(
                preset = "default",
                overrideDirs = emptyList(),
                conflictPolicy = ConflictPolicy.SKIP,
            ),
        )
    }
}

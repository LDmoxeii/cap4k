package com.only4.cap4k.plugin.pipeline.core

import com.only4.cap4k.plugin.pipeline.api.ArtifactOutputKind
import com.only4.cap4k.plugin.pipeline.api.ConflictPolicy
import com.only4.cap4k.plugin.pipeline.api.RenderedArtifact
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import java.nio.file.Files

class FilesystemArtifactExporterTest {

    @Test
    fun `generated source overwrites existing file regardless of artifact conflict policy`() {
        val root = Files.createTempDirectory("cap4k-export-generated-source")
        val target = root.resolve("demo-domain/build/generated/cap4k/main/kotlin/com/acme/demo/Category.kt")
        Files.createDirectories(target.parent)
        Files.writeString(target, "old")

        val written = FilesystemArtifactExporter(root).export(
            listOf(
                RenderedArtifact(
                    outputPath = "demo-domain/build/generated/cap4k/main/kotlin/com/acme/demo/Category.kt",
                    content = "new",
                    conflictPolicy = ConflictPolicy.SKIP,
                    outputKind = ArtifactOutputKind.GENERATED_SOURCE,
                    resolvedOutputRoot = "demo-domain/build/generated/cap4k/main/kotlin",
                )
            )
        )

        assertEquals(listOf(target.toString()), written)
        assertEquals("new", Files.readString(target))
    }

    @Test
    fun `checked in source keeps existing file when conflict policy is skip`() {
        val root = Files.createTempDirectory("cap4k-export-checked-in")
        val target = root.resolve("demo-domain/src/main/kotlin/com/acme/demo/CategoryBehavior.kt")
        Files.createDirectories(target.parent)
        Files.writeString(target, "user")

        val written = FilesystemArtifactExporter(root).export(
            listOf(
                RenderedArtifact(
                    outputPath = "demo-domain/src/main/kotlin/com/acme/demo/CategoryBehavior.kt",
                    content = "generated",
                    conflictPolicy = ConflictPolicy.SKIP,
                    outputKind = ArtifactOutputKind.CHECKED_IN_SOURCE,
                    resolvedOutputRoot = "demo-domain/src/main/kotlin",
                )
            )
        )

        assertEquals(emptyList<String>(), written)
        assertEquals("user", Files.readString(target))
    }

    @Test
    fun `filtering exporter delegates only included artifacts and returns written paths`() {
        val root = Files.createTempDirectory("cap4k-filtering-export")
        val exporter = FilteringArtifactExporter(
            delegate = FilesystemArtifactExporter(root),
            include = { it.outputKind == ArtifactOutputKind.GENERATED_SOURCE },
        )

        val written = exporter.export(
            listOf(
                RenderedArtifact(
                    outputPath = "demo-domain/build/generated/cap4k/main/kotlin/com/acme/demo/Category.kt",
                    content = "class Category",
                    conflictPolicy = ConflictPolicy.OVERWRITE,
                    outputKind = ArtifactOutputKind.GENERATED_SOURCE,
                    resolvedOutputRoot = "demo-domain/build/generated/cap4k/main/kotlin",
                ),
                RenderedArtifact(
                    outputPath = "demo-domain/src/main/kotlin/com/acme/demo/CategoryBehavior.kt",
                    content = "package com.acme.demo",
                    conflictPolicy = ConflictPolicy.SKIP,
                    outputKind = ArtifactOutputKind.CHECKED_IN_SOURCE,
                    resolvedOutputRoot = "demo-domain/src/main/kotlin",
                ),
            )
        )

        assertEquals(1, written.size)
        assertEquals("class Category", Files.readString(root.resolve("demo-domain/build/generated/cap4k/main/kotlin/com/acme/demo/Category.kt")))
        assertFalse(Files.exists(root.resolve("demo-domain/src/main/kotlin/com/acme/demo/CategoryBehavior.kt")))
    }
}

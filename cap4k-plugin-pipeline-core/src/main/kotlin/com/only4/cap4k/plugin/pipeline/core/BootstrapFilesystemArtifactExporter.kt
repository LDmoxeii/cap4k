package com.only4.cap4k.plugin.pipeline.core

import com.only4.cap4k.plugin.pipeline.api.BootstrapConfig
import com.only4.cap4k.plugin.pipeline.api.BootstrapMode
import com.only4.cap4k.plugin.pipeline.api.RenderedArtifact
import java.nio.file.Files
import java.nio.file.Path

class BootstrapFilesystemArtifactExporter(
    private val root: Path,
    private val config: BootstrapConfig,
    private val merger: BootstrapManagedSectionMerger = BootstrapManagedSectionMerger(),
) : ArtifactExporter {
    private val normalizedRoot = root.toAbsolutePath().normalize()
    private val delegate = FilesystemArtifactExporter(normalizedRoot)
    private val managedRootPaths = managedRootPaths(config)

    override fun export(artifacts: List<RenderedArtifact>): List<String> =
        artifacts.mapNotNull { artifact ->
            if (!managedRootPaths.contains(artifact.outputPath)) {
                delegate.export(listOf(artifact)).singleOrNull()
            } else {
                exportManagedRootArtifact(artifact)
            }
        }

    private fun exportManagedRootArtifact(artifact: RenderedArtifact): String {
        val outputPath = resolveOutputPath(artifact.outputPath)
        if (!Files.exists(outputPath)) {
            return delegate.export(listOf(artifact)).single()
        }

        val mergedContent = merger.merge(Files.readString(outputPath), artifact.content)
        val parent = outputPath.parent
        if (parent != null) {
            Files.createDirectories(parent)
        }
        Files.writeString(outputPath, mergedContent)
        return outputPath.toString()
    }

    private fun resolveOutputPath(artifactOutputPath: String): Path {
        if (artifactOutputPath.isBlank()) {
            throw IllegalArgumentException("Artifact output path must not be blank.")
        }

        val outputPath = Path.of(artifactOutputPath)
        if (outputPath.isAbsolute) {
            throw IllegalArgumentException("Artifact output path must be relative: $artifactOutputPath")
        }

        val resolvedPath = normalizedRoot.resolve(outputPath).normalize()
        if (!resolvedPath.startsWith(normalizedRoot)) {
            throw IllegalArgumentException("Artifact output path resolves outside export root: $artifactOutputPath")
        }
        return resolvedPath
    }

    private fun managedRootPaths(config: BootstrapConfig): Set<String> {
        val rootFiles = listOf("build.gradle.kts", "settings.gradle.kts")
        return when (config.mode) {
            BootstrapMode.IN_PLACE -> rootFiles
            BootstrapMode.PREVIEW_SUBTREE -> {
                val previewDir = requireNotNull(config.previewDir) {
                    "bootstrap previewDir is required when bootstrap.mode=PREVIEW_SUBTREE"
                }
                rootFiles.map { "$previewDir/$it" }
            }
        }.toSet()
    }
}

package com.only4.cap4k.plugin.pipeline.core

import com.only4.cap4k.plugin.pipeline.api.RenderedArtifact
import java.nio.file.Files
import java.nio.file.Path

class FilesystemArtifactExporter(private val root: Path) : ArtifactExporter {
    override fun export(artifacts: List<RenderedArtifact>): List<String> {
        val normalizedRoot = root.toAbsolutePath().normalize()
        return artifacts.mapNotNull { artifact ->
            val outputPath = resolveOutputPath(normalizedRoot, artifact.outputPath)
            when (artifact.conflictPolicy) {
                com.only4.cap4k.plugin.pipeline.api.ConflictPolicy.SKIP -> {
                    if (Files.exists(outputPath)) {
                        return@mapNotNull null
                    }
                }
                com.only4.cap4k.plugin.pipeline.api.ConflictPolicy.FAIL -> {
                    if (Files.exists(outputPath)) {
                        throw IllegalStateException("Target already exists: $outputPath")
                    }
                }
                com.only4.cap4k.plugin.pipeline.api.ConflictPolicy.OVERWRITE -> {
                    // default write behavior
                }
            }

            val parent = outputPath.parent
            if (parent != null) {
                Files.createDirectories(parent)
            }
            Files.writeString(outputPath, artifact.content)
            outputPath.toString()
        }
    }

    private fun resolveOutputPath(normalizedRoot: Path, artifactOutputPath: String): Path {
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
}

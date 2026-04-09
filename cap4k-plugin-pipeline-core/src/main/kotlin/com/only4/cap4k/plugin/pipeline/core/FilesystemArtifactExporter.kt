package com.only4.cap4k.plugin.pipeline.core

import com.only4.cap4k.plugin.pipeline.api.RenderedArtifact
import java.nio.file.Files
import java.nio.file.Path

class FilesystemArtifactExporter(private val root: Path) : ArtifactExporter {
    override fun export(artifacts: List<RenderedArtifact>): List<String> {
        return artifacts.map { artifact ->
            val outputPath = root.resolve(artifact.outputPath)
            val parent = outputPath.parent
            if (parent != null) {
                Files.createDirectories(parent)
            }
            Files.writeString(outputPath, artifact.content)
            outputPath.toString()
        }
    }
}

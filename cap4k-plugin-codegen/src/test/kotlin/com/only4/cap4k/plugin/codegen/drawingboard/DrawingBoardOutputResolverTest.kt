package com.only4.cap4k.plugin.codegen.drawingboard

import com.only4.cap4k.plugin.codegen.template.TemplateNode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Files

class DrawingBoardOutputResolverTest {
    @Test
    fun `resolves output file path from template tag`() {
        val root = Files.createTempDirectory("drawing-board-output").toFile()
        val designDir = File(root, "design").apply { mkdirs() }

        val templateNode = TemplateNode().apply {
            type = "file"
            tag = "drawing_board"
            name = "drawing_board.json"
        }

        val outputs = DrawingBoardOutputResolver.resolve(
            tag = "drawing_board",
            baseContext = mapOf(
                "basePackage" to "demo",
                "basePackage__as_path" to "demo"
            ),
            templateParentPath = mapOf("drawing_board" to designDir.absolutePath),
            templateNodes = listOf(templateNode),
            generatorName = "drawing_board"
        )

        assertEquals(1, outputs.size)
        assertEquals(
            File(designDir, "drawing_board.json").canonicalPath,
            outputs.single().canonicalPath
        )
    }
}

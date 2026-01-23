package com.only4.cap4k.plugin.codegen.drawingboard

import com.only4.cap4k.plugin.codegen.generators.drawingboard.DrawingBoardCliGenerator
import com.only4.cap4k.plugin.codegen.pebble.PebbleInitializer
import com.only4.cap4k.plugin.codegen.template.PathNode
import com.only4.cap4k.plugin.codegen.template.TemplateNode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class DrawingBoardTemplateRendererTest {
    @Test
    fun `renders default template with tag-specific file name`() {
        val generator = DrawingBoardCliGenerator()
        val template = generator.getDefaultTemplateNodes().single()

        PebbleInitializer.initPebble()

        val templateRoot = findTemplateRoot()
        PathNode.setDirectory(templateRoot.canonicalPath)
        try {
            val context = mapOf(
                "archTemplateEncoding" to "UTF-8",
                "drawingBoardTag" to "cli",
                "elements" to emptyList<Any>(),
                "elementsByTag" to emptyMap<String, Any>()
            )
            val node = template.resolve(context)
            assertEquals("drawing_board_cli.json", node.name)
            assertTrue(node.data?.startsWith("[") == true)
        } finally {
            PathNode.clearDirectory()
        }
    }

    private fun findTemplateRoot(): File {
        var dir = File(System.getProperty("user.dir")).canonicalFile
        repeat(4) {
            if (File(dir, "template/_tpl/drawing_board.json.peb").exists()) return dir
            dir = dir.parentFile ?: return dir
        }
        error("template/_tpl/drawing_board.json.peb not found from ${System.getProperty("user.dir")}")
    }
}

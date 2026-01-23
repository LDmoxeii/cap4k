package com.only4.cap4k.plugin.codegen.drawingboard

import com.only4.cap4k.plugin.codegen.gradle.GenDrawingBoardTask
import com.only4.cap4k.plugin.codegen.template.PathNode
import com.only4.cap4k.plugin.codegen.template.TemplateNode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DrawingBoardTemplateRendererTest {
    @Test
    fun `renders template node to path node with content`() {
        val tpl = TemplateNode().apply {
            type = "file"
            tag = "drawing_board"
            name = "drawing_board.json"
        }

        val method = GenDrawingBoardTask::class.java.declaredMethods.firstOrNull {
            it.name.startsWith("renderDrawingBoardPathNodes")
        } ?: error("renderDrawingBoardPathNodes not found")

        @Suppress("UNCHECKED_CAST")
        val nodes = method.invoke(
            null,
            listOf(tpl),
            emptyMap<String, Any?>(),
            "drawing_board",
            "[]"
        ) as List<PathNode>

        assertEquals("drawing_board.json", nodes.first().name)
        assertEquals("[]", nodes.first().data)
    }
}

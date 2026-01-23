package com.only4.cap4k.plugin.codegen.drawingboard

import com.only4.cap4k.plugin.codegen.context.drawingboard.DrawingBoardContextBuilder
import com.only4.cap4k.plugin.codegen.context.drawingboard.MutableDrawingBoardContext
import com.only4.cap4k.plugin.codegen.template.TemplateNode
import com.only4.cap4k.plugin.codeanalysis.core.model.DesignElement
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Files

class DrawingBoardContextBuilderTest {
    @Test
    fun `builds elements and groups by tag`() {
        val root = Files.createTempDirectory("drawing-context").toFile()
        val adapter = File(root, "adapter").apply { mkdirs() }
        val application = File(root, "application").apply { mkdirs() }

        val adapterOut = File(adapter, "build/cap4k-code-analysis").apply { mkdirs() }
        File(adapterOut, "design-elements.json").writeText(
            """[
              {"tag":"cmd","package":"a","name":"A","desc":"adapter","requestFields":[],"responseFields":[]}
            ]"""
        )

        val applicationOut = File(application, "build/cap4k-code-analysis").apply { mkdirs() }
        File(applicationOut, "design-elements.json").writeText(
            """[
              {"tag":"cmd","package":"a","name":"A","desc":"application","requestFields":[],"responseFields":[]},
              {"tag":"qry","package":"b","name":"B","desc":"","requestFields":[],"responseFields":[]}
            ]"""
        )

        val ctx = TestDrawingBoardContext(
            adapterPath = adapter.absolutePath,
            applicationPath = application.absolutePath,
            domainPath = ""
        )
        DrawingBoardContextBuilder().build(ctx)

        assertEquals(2, ctx.elements.size)
        val cmd = ctx.elements.first { it.name == "A" }
        assertEquals("adapter", cmd.desc)
        assertEquals(1, ctx.elementsByTag.getValue("cmd").size)
    }

    private class TestDrawingBoardContext(
        override val adapterPath: String,
        override val applicationPath: String,
        override val domainPath: String
    ) : MutableDrawingBoardContext {
        override val elements: MutableList<DesignElement> = mutableListOf()
        override val elementsByTag: MutableMap<String, MutableList<DesignElement>> = mutableMapOf()
        override val baseMap: Map<String, Any?> = emptyMap()
        override val typeMapping: MutableMap<String, String> = mutableMapOf()
        override val templateParentPath: MutableMap<String, String> = mutableMapOf()
        override val templatePackage: MutableMap<String, String> = mutableMapOf()
        override val templateNodeMap: MutableMap<String, MutableList<TemplateNode>> = mutableMapOf()

        override fun MutableMap<String, Any?>.putContext(tag: String, variable: String, value: Any) {}
    }
}

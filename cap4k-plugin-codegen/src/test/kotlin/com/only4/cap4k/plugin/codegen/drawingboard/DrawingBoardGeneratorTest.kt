package com.only4.cap4k.plugin.codegen.drawingboard

import com.only4.cap4k.plugin.codegen.context.drawingboard.DrawingBoardContext
import com.only4.cap4k.plugin.codegen.generators.drawingboard.DrawingBoardCliGenerator
import com.only4.cap4k.plugin.codegen.template.TemplateNode
import com.only4.cap4k.plugin.codeanalysis.core.model.DesignElement
import com.only4.cap4k.plugin.codeanalysis.core.model.DesignField
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DrawingBoardGeneratorTest {
    @Test
    fun `cli generator filters elements and builds context`() {
        val cliElement = DesignElement(
            tag = "cli",
            `package` = "system",
            name = "GetSettings",
            desc = "Get settings",
            aggregates = listOf("Settings", "Audit"),
            requestFields = listOf(DesignField(name = "registerCoinCount", type = "Int"))
        )
        val qryElement = DesignElement(
            tag = "qry",
            `package` = "system",
            name = "GetSettings",
            desc = "Get settings"
        )
        val ctx = TestDrawingBoardContext(
            elements = listOf(cliElement, qryElement),
            elementsByTag = mapOf(
                "cli" to listOf(cliElement),
                "qry" to listOf(qryElement)
            )
        )

        val generator = DrawingBoardCliGenerator()
        val context = with(ctx) { generator.buildContext() }

        assertTrue(with(ctx) { generator.shouldGenerate() })
        assertEquals("drawing_board_cli", generator.generatorName())
        assertEquals("cli", context["drawingBoardTag"])
        @Suppress("UNCHECKED_CAST")
        val elements = context["elements"] as? List<DesignElement>
        assertEquals(listOf(cliElement), elements)
    }

    private class TestDrawingBoardContext(
        override val elements: List<DesignElement>,
        override val elementsByTag: Map<String, List<DesignElement>> = emptyMap()
    ) : DrawingBoardContext {
        override val baseMap: Map<String, Any?> = emptyMap()
        override val adapterPath: String = ""
        override val domainPath: String = ""
        override val applicationPath: String = ""
        override val typeMapping: MutableMap<String, String> = mutableMapOf()
        override val templateParentPath: MutableMap<String, String> = mutableMapOf()
        override val templatePackage: MutableMap<String, String> = mutableMapOf()
        override val templateNodeMap: MutableMap<String, MutableList<TemplateNode>> = mutableMapOf()

        override fun MutableMap<String, Any?>.putContext(tag: String, variable: String, value: Any) {}
    }
}

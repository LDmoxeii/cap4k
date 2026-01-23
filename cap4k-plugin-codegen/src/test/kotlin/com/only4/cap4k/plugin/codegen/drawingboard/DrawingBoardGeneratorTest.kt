package com.only4.cap4k.plugin.codegen.drawingboard

import com.only4.cap4k.plugin.codegen.context.drawingboard.DrawingBoardContext
import com.only4.cap4k.plugin.codegen.generators.drawingboard.DrawingBoardGenerator
import com.only4.cap4k.plugin.codegen.template.TemplateNode
import com.only4.cap4k.plugin.codeanalysis.core.model.DesignElement
import com.only4.cap4k.plugin.codeanalysis.core.model.DesignField
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DrawingBoardGeneratorTest {
    @Test
    fun `builds document with pretty printing and inline field items`() {
        val element = DesignElement(
            tag = "cli",
            `package` = "system",
            name = "GetSettings",
            desc = "Get settings",
            aggregates = listOf("Settings", "Audit"),
            requestFields = listOf(DesignField(name = "registerCoinCount", type = "Int"))
        )
        val ctx = TestDrawingBoardContext(elements = listOf(element))

        val generator = DrawingBoardGenerator()
        val documents = with(ctx) { generator.documents() }

        assertEquals(1, documents.size)
        val json = documents.first().content
        assertTrue(json.contains("\"aggregates\": [\n      \"Settings\""))
        assertTrue(json.contains("{ \"name\": \"registerCoinCount\", \"type\": \"Int\", \"nullable\": false }"))
    }

    private class TestDrawingBoardContext(
        override val elements: List<DesignElement>
    ) : DrawingBoardContext {
        override val elementsByTag: Map<String, List<DesignElement>> = emptyMap()
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

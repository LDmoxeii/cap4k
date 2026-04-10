package com.only4.cap4k.plugin.pipeline.source.ir

import com.only4.cap4k.plugin.pipeline.api.ConflictPolicy
import com.only4.cap4k.plugin.pipeline.api.IrAnalysisSnapshot
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig
import com.only4.cap4k.plugin.pipeline.api.ProjectLayout
import com.only4.cap4k.plugin.pipeline.api.SourceConfig
import com.only4.cap4k.plugin.pipeline.api.TemplateConfig
import java.nio.file.Files
import kotlin.io.path.writeText
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class IrAnalysisSourceProviderTest {

    @Test
    fun `collect merges input dirs normalizes blanks and preserves first node by id`() {
        val dirA = Files.createTempDirectory("cap4k-ir-a")
        val dirB = Files.createTempDirectory("cap4k-ir-b")

        dirA.resolve("nodes.json").writeText(
            """
            [
              {"id":"OrderController::submit","name":"","fullName":"","type":"controllermethod"},
              {"id":"SubmitOrderCmd","name":"SubmitOrderCmd","fullName":"com.acme.demo.SubmitOrderCmd","type":"command"},
              {"id":{"value":"BrokenNode"}},
              null,
              "not-an-object"
            ]
            """.trimIndent()
        )
        dirA.resolve("rels.json").writeText(
            """
            [
              {"fromId":"OrderController::submit","toId":"SubmitOrderCmd","type":"ControllerMethodToCommand"},
              {"fromId":{"value":"bad"},"toId":"SubmitOrderCmd","type":"ControllerMethodToCommand"},
              null,
              "not-an-object"
            ]
            """.trimIndent()
        )
        dirB.resolve("nodes.json").writeText(
            """
            [
              {"id":"OrderController::submit","name":"later-value","fullName":"later-value","type":"controllermethod"},
              {"id":"SubmitOrderHandler","name":"SubmitOrderHandler","fullName":"com.acme.demo.SubmitOrderHandler","type":"commandhandler"},
              {"id":"EmptyTypeNode","name":"EmptyTypeNode","fullName":"com.acme.demo.EmptyTypeNode","type":""}
            ]
            """.trimIndent()
        )
        dirB.resolve("rels.json").writeText(
            """
            [
              {"fromId":"SubmitOrderCmd","toId":"SubmitOrderHandler","type":"CommandToCommandHandler"},
              {"fromId":"SubmitOrderCmd","toId":"SubmitOrderHandler"},
              null,
              "not-an-object"
            ]
            """.trimIndent()
        )

        val snapshot = IrAnalysisSourceProvider().collect(config(dirA.toString(), dirB.toString())) as IrAnalysisSnapshot

        assertEquals(listOf(dirA.toString(), dirB.toString()), snapshot.inputDirs)
        assertEquals(4, snapshot.nodes.size)
        assertEquals("submit", snapshot.nodes.first { it.id == "OrderController::submit" }.name)
        assertEquals("OrderController::submit", snapshot.nodes.first { it.id == "OrderController::submit" }.fullName)
        assertEquals("unknown", snapshot.nodes.first { it.id == "EmptyTypeNode" }.type)
        assertEquals(2, snapshot.edges.size)
        assertEquals("CommandToCommandHandler", snapshot.edges.last().type)
        assertTrue(snapshot.designElements.isEmpty())
    }

    @Test
    fun `collect parses design elements when file exists`() {
        val dir = Files.createTempDirectory("cap4k-ir-design")

        dir.resolve("nodes.json").writeText("""[]""")
        dir.resolve("rels.json").writeText("""[]""")
        dir.resolve("design-elements.json").writeText(
            """
            [
              null,
              "not-an-object",
              {
                "tag": "cmd",
                "package": "orders",
                "name": "SubmitOrder",
                "desc": "submit order",
                "aggregates": ["Order"],
                "requestFields": [
                  {"name": "orderId", "type": "Long", "nullable": false, "defaultValue": "0"},
                  {"name": " ", "type": "String"},
                  null
                ],
                "responseFields": [
                  {"name": "accepted", "type": "Boolean"},
                  {"type": "String"}
                ]
              },
              {
                "tag": "de",
                "package": "",
                "name": "OrderCreated",
                "entity": "Order",
                "persist": true
              },
              {
                "tag": " ",
                "package": "ignored",
                "name": "Ignored",
                "desc": "ignored"
              }
            ]
            """.trimIndent()
        )

        val snapshot = IrAnalysisSourceProvider().collect(config(dir.toString())) as IrAnalysisSnapshot

        assertEquals(2, snapshot.designElements.size)
        assertEquals("cmd", snapshot.designElements.first().tag)
        assertEquals("orders", snapshot.designElements.first().packageName)
        assertEquals("SubmitOrder", snapshot.designElements.first().name)
        assertEquals("submit order", snapshot.designElements.first().description)
        assertEquals(listOf("Order"), snapshot.designElements.first().aggregates)
        assertEquals(1, snapshot.designElements.first().requestFields.size)
        assertEquals("orderId", snapshot.designElements.first().requestFields.first().name)
        assertEquals("Long", snapshot.designElements.first().requestFields.first().type)
        assertEquals(false, snapshot.designElements.first().requestFields.first().nullable)
        assertEquals("0", snapshot.designElements.first().requestFields.first().defaultValue)
        assertEquals(1, snapshot.designElements.first().responseFields.size)
        assertEquals("accepted", snapshot.designElements.first().responseFields.first().name)
        assertEquals("Boolean", snapshot.designElements.first().responseFields.first().type)
        assertEquals("", snapshot.designElements.last().packageName)
        assertEquals("OrderCreated", snapshot.designElements.last().name)
        assertEquals("", snapshot.designElements.last().description)
        assertTrue(snapshot.designElements.last().aggregates.isEmpty())
        assertTrue(snapshot.designElements.last().requestFields.isEmpty())
        assertTrue(snapshot.designElements.last().responseFields.isEmpty())
        assertEquals("Order", snapshot.designElements.last().entity)
        assertEquals(true, snapshot.designElements.last().persist)
    }

    @Test
    fun `collect returns empty design elements when file is absent`() {
        val dir = Files.createTempDirectory("cap4k-ir-no-design")
        dir.resolve("nodes.json").writeText("""[]""")
        dir.resolve("rels.json").writeText("""[]""")

        val snapshot = IrAnalysisSourceProvider().collect(config(dir.toString())) as IrAnalysisSnapshot

        assertTrue(snapshot.designElements.isEmpty())
    }

    @Test
    fun `collect fails clearly when required files are missing`() {
        val dir = Files.createTempDirectory("cap4k-ir-missing")
        dir.resolve("nodes.json").writeText("""[]""")

        val error = assertThrows<IllegalArgumentException> {
            IrAnalysisSourceProvider().collect(config(dir.toString()))
        }

        assertTrue(error.message!!.contains("ir-analysis inputDir is missing nodes.json or rels.json"))
        assertTrue(error.message!!.contains(dir.toString()))
    }

    private fun config(vararg inputDirs: String): ProjectConfig {
        return ProjectConfig(
            basePackage = "com.acme.demo",
            layout = ProjectLayout.MULTI_MODULE,
            modules = emptyMap(),
            sources = mapOf(
                "ir-analysis" to SourceConfig(
                    enabled = true,
                    options = mapOf("inputDirs" to inputDirs.toList()),
                )
            ),
            generators = emptyMap(),
            templates = TemplateConfig("ddd-default", emptyList(), ConflictPolicy.SKIP),
        )
    }
}

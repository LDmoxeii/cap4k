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
                "tag": "command",
                "package": "orders",
                "name": "SubmitOrder",
                "description": "submit order",
                "aggregates": ["Order"],
                "artifacts": [
                  {"family": "command", "variant": "default"},
                  {"family": "command-handler"}
                ],
                "fields": [
                  {"name": "orderId", "type": "Long", "nullable": false, "defaultValue": "0"},
                  {"name": " ", "type": "String"},
                  null
                ],
                "resultFields": [
                  {"name": "accepted", "type": "Boolean"},
                  {"type": "String"}
                ]
              },
              {
                "tag": "domain_event",
                "package": "",
                "name": "OrderCreated",
                "description": "order created domain event",
                "aggregates": ["Order"],
                "persist": true
              },
              {
                "tag": "query",
                "package": "orders",
                "name": "FindOrderPage",
                "description": "find order page",
                "artifacts": [{"family": "query", "variant": "page"}],
                "fields": [],
                "resultFields": []
              },
              {
                "tag": "integration_event",
                "package": "orders.events",
                "name": "OrderCreated",
                "description": "order created integration event",
                "eventName": "order.created",
                "fields": [
                  {"name": "orderId", "type": "Long"}
                ]
              },
              {
                "tag": " ",
                "package": "ignored",
                "name": "Ignored",
                "description": "ignored"
              }
            ]
            """.trimIndent()
        )

        val snapshot = IrAnalysisSourceProvider().collect(config(dir.toString())) as IrAnalysisSnapshot

        assertEquals(4, snapshot.designElements.size)
        assertEquals("command", snapshot.designElements.first().tag)
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
        val pageQuery = snapshot.designElements.single { it.name == "FindOrderPage" }
        assertTrue(pageQuery.traits.isEmpty())
        val integrationEvent = snapshot.designElements.single { it.tag == "integration_event" }
        assertEquals(null, integrationEvent.role)
        assertEquals("order.created", integrationEvent.eventName)
        assertEquals(listOf("orderId"), integrationEvent.requestFields.map { it.name })
        val domainEvent = snapshot.designElements.single { it.tag == "domain_event" }
        assertEquals("", domainEvent.packageName)
        assertEquals("OrderCreated", domainEvent.name)
        assertEquals("order created domain event", domainEvent.description)
        assertEquals(listOf("Order"), domainEvent.aggregates)
        assertTrue(domainEvent.requestFields.isEmpty())
        assertTrue(domainEvent.responseFields.isEmpty())
        assertEquals(null, domainEvent.entity)
        assertEquals(true, domainEvent.persist)
    }

    @Test
    fun `collect skips malformed nested request and response fields`() {
        val dir = Files.createTempDirectory("cap4k-ir-malformed-fields")

        dir.resolve("nodes.json").writeText("""[]""")
        dir.resolve("rels.json").writeText("""[]""")
        dir.resolve("design-elements.json").writeText(
            """
            [
              {
                "tag": "command",
                "package": "orders",
                "name": "SubmitOrder",
                "description": "submit order",
                "fields": { "name": "orderId" },
                "resultFields": "not-an-array"
              }
            ]
            """.trimIndent()
        )

        val snapshot = IrAnalysisSourceProvider().collect(config(dir.toString())) as IrAnalysisSnapshot

        assertEquals(1, snapshot.designElements.size)
        assertTrue(snapshot.designElements.first().requestFields.isEmpty())
        assertTrue(snapshot.designElements.first().responseFields.isEmpty())
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
                    options = mapOf("inputDirs" to inputDirs.toList()),
                )
            ),
            generators = emptyMap(),
            templates = TemplateConfig("ddd-default", emptyList(), ConflictPolicy.SKIP),
        )
    }
}

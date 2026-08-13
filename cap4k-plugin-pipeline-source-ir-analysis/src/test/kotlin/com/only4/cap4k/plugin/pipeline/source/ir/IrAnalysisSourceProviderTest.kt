package com.only4.cap4k.plugin.pipeline.source.ir

import com.only4.cap4k.plugin.pipeline.api.ConflictPolicy
import com.only4.cap4k.plugin.pipeline.api.ArtifactSelectionModel
import com.only4.cap4k.plugin.pipeline.api.IrAnalysisSnapshot
import com.only4.cap4k.plugin.pipeline.api.GeneratorConfig
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig
import com.only4.cap4k.plugin.pipeline.api.ProjectLayout
import com.only4.cap4k.plugin.pipeline.api.SourceConfig
import com.only4.cap4k.plugin.pipeline.api.TemplateConfig
import java.nio.file.Files
import java.nio.file.Path
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
              {"id":"SubmitOrderCmd","name":"SubmitOrderCmd","fullName":"com.acme.demo.SubmitOrderCmd","type":"command"}
            ]
            """.trimIndent()
        )
        dirA.resolve("rels.json").writeText(
            """
            [
              {"fromId":"OrderController::submit","toId":"SubmitOrderCmd","type":"ControllerMethodToCommand"}
            ]
            """.trimIndent()
        )
        dirA.writeEmptyAggregateElements()
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
              {"fromId":"SubmitOrderCmd","toId":"SubmitOrderHandler","type":"CommandToCommandHandler"}
            ]
            """.trimIndent()
        )
        dirB.writeEmptyAggregateElements()

        val snapshot = IrAnalysisSourceProvider().collect(config(dirA.toString(), dirB.toString())) as IrAnalysisSnapshot

        assertEquals(listOf(dirA.toString(), dirB.toString()), snapshot.inputDirs)
        assertEquals(4, snapshot.nodes.size)
        assertEquals("submit", snapshot.nodes.first { it.id == "OrderController::submit" }.name)
        assertEquals("OrderController::submit", snapshot.nodes.first { it.id == "OrderController::submit" }.fullName)
        assertEquals("unknown", snapshot.nodes.first { it.id == "EmptyTypeNode" }.type)
        assertEquals(2, snapshot.edges.size)
        assertEquals("CommandToCommandHandler", snapshot.edges.last().type)
        assertTrue(snapshot.designElements.isEmpty())
        assertTrue(snapshot.aggregateElements.isEmpty())
    }

    @Test
    fun `collect parses repository aggregate structure independently from design elements`() {
        val dir = Files.createTempDirectory("cap4k-ir-aggregate-elements")
        dir.resolve("nodes.json").writeText("[]")
        dir.resolve("rels.json").writeText("[]")
        dir.resolve("design-elements.json").writeText("[]")
        dir.resolve("aggregate-elements.json").writeText(
            """
            [
              {
                "carrierQualifiedName": "com.acme.demo.adapter.domain.repositories.OrderJpaRepositoryAdapter",
                "aggregate": "Order",
                "name": "OrderRepository",
                "packageName": "com.acme.demo.adapter.domain.repositories",
                "description": "Order repository carrier",
                "type": "repository",
                "root": false
              }
            ]
            """.trimIndent(),
        )

        val snapshot = IrAnalysisSourceProvider().collect(config(dir.toString())) as IrAnalysisSnapshot

        assertTrue(snapshot.designElements.isEmpty())
        val repository = snapshot.aggregateElements.single()
        assertEquals(
            "com.acme.demo.adapter.domain.repositories.OrderJpaRepositoryAdapter",
            repository.carrierQualifiedName,
        )
        assertEquals("Order", repository.aggregate)
        assertEquals("OrderRepository", repository.name)
        assertEquals("com.acme.demo.adapter.domain.repositories", repository.packageName)
        assertEquals("Order repository carrier", repository.description)
        assertEquals("repository", repository.type)
        assertEquals(false, repository.root)
    }

    @Test
    fun `collect rejects retired and unknown aggregate structure types`() {
        listOf(
            "specification",
            "unique-query",
            "unique-query-handler",
            "unique-validator",
            "unknown",
        ).forEachIndexed { index, unsupportedType ->
            val dir = Files.createTempDirectory("cap4k-ir-unsupported-aggregate-type-$index")
            dir.resolve("nodes.json").writeText("[]")
            dir.resolve("rels.json").writeText("[]")
            dir.resolve("aggregate-elements.json").writeText(
                """
                [{
                  "carrierQualifiedName": "demo.CategoryCarrier",
                  "aggregate": "Category",
                  "type": "$unsupportedType"
                }]
                """.trimIndent(),
            )

            val error = assertThrows<IllegalArgumentException> {
                IrAnalysisSourceProvider().collect(config(dir.toString()))
            }
            assertEquals("aggregate element at index 0 has unsupported type: $unsupportedType", error.message)
        }
    }
    @Test
    fun `collect fails clearly for malformed graph nodes and rels`() {
        val cases = listOf(
            Triple("""[null]""", """[]""", "ir-analysis nodes[0] must be an object"),
            Triple("""[{"id":{"value":"BrokenNode"}}]""", """[]""", "ir-analysis nodes[0] field 'id' must be a string"),
            Triple("""[]""", """[null]""", "ir-analysis rels[0] must be an object"),
            Triple("""[]""", """[{"toId":"SubmitOrderCmd","type":"ControllerMethodToCommand"}]""", "ir-analysis rels[0] must declare non-blank fromId"),
            Triple("""[]""", """[{"fromId":"OrderController::submit","type":"ControllerMethodToCommand"}]""", "ir-analysis rels[0] must declare non-blank toId"),
            Triple("""[]""", """[{"fromId":"OrderController::submit","toId":"SubmitOrderCmd"}]""", "ir-analysis rels[0] must declare non-blank type"),
        )

        cases.forEachIndexed { index, (nodesJson, relsJson, expectedMessage) ->
            val dir = Files.createTempDirectory("cap4k-ir-malformed-graph-$index")
            dir.resolve("nodes.json").writeText(nodesJson)
            dir.resolve("rels.json").writeText(relsJson)
            dir.writeEmptyAggregateElements()

            val error = assertThrows<IllegalArgumentException> {
                IrAnalysisSourceProvider().collect(config(dir.toString()))
            }

            assertEquals(expectedMessage, error.message)
        }
    }

    @Test
    fun `collect fails clearly when graph file root is not an array`() {
        val cases = listOf(
            Triple("""{}""", """[]""", "ir-analysis nodes file"),
            Triple("""[]""", """{}""", "ir-analysis rels file"),
        )

        cases.forEachIndexed { index, (nodesJson, relsJson, expectedMessageFragment) ->
            val dir = Files.createTempDirectory("cap4k-ir-malformed-root-$index")
            dir.resolve("nodes.json").writeText(nodesJson)
            dir.resolve("rels.json").writeText(relsJson)
            dir.writeEmptyAggregateElements()

            val error = assertThrows<IllegalArgumentException> {
                IrAnalysisSourceProvider().collect(config(dir.toString()))
            }

            assertTrue(error.message!!.contains(expectedMessageFragment))
            assertTrue(error.message!!.contains("root must be an array"))
        }
    }

    @Test
    fun `collect parses design elements when file exists`() {
        val dir = Files.createTempDirectory("cap4k-ir-design")

        dir.resolve("nodes.json").writeText("""[]""")
        dir.resolve("rels.json").writeText("""[]""")
        dir.writeEmptyAggregateElements()
        dir.resolve("design-elements.json").writeText(
            """
            [
              {
                "tag": "command",
                "package": "orders",
                "name": "SubmitOrder",
                "description": "submit order",
                "aggregates": ["Order"],
                "artifacts": [
                  {"family": "command"}
                ],
                "fields": [
                  {"name": "orderId", "type": "Long", "defaultValue": "0"}
                ],
                "resultFields": [
                  {"name": "accepted", "type": "Boolean"}
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
        assertEquals(
            listOf(ArtifactSelectionModel(family = "command")),
            snapshot.designElements.first().artifacts,
        )
        assertEquals(1, snapshot.designElements.first().fields.size)
        assertEquals("orderId", snapshot.designElements.first().fields.first().name)
        assertEquals("Long", snapshot.designElements.first().fields.first().typeExpression)
        assertEquals("0", snapshot.designElements.first().fields.first().defaultValue)
        assertEquals(1, snapshot.designElements.first().resultFields.size)
        assertEquals("accepted", snapshot.designElements.first().resultFields.first().name)
        assertEquals("Boolean", snapshot.designElements.first().resultFields.first().typeExpression)
        val pageQuery = snapshot.designElements.single { it.name == "FindOrderPage" }
        assertEquals(listOf(ArtifactSelectionModel(family = "query", variant = "page")), pageQuery.artifacts)
        val integrationEvent = snapshot.designElements.single { it.tag == "integration_event" }
        assertEquals("order.created", integrationEvent.eventName)
        assertEquals(listOf("orderId"), integrationEvent.fields.map { it.name })
        val domainEvent = snapshot.designElements.single { it.tag == "domain_event" }
        assertEquals("", domainEvent.packageName)
        assertEquals("OrderCreated", domainEvent.name)
        assertEquals("order created domain event", domainEvent.description)
        assertEquals(listOf("Order"), domainEvent.aggregates)
        assertTrue(domainEvent.fields.isEmpty())
        assertTrue(domainEvent.resultFields.isEmpty())
        assertEquals(true, domainEvent.persist)
    }

    @Test
    fun `collect preserves same design block key entries with different artifacts`() {
        val dir = Files.createTempDirectory("cap4k-ir-design-artifacts")

        dir.resolve("nodes.json").writeText("""[]""")
        dir.resolve("rels.json").writeText("""[]""")
        dir.writeEmptyAggregateElements()
        dir.resolve("design-elements.json").writeText(
            """
            [
              {
                "tag": "query",
                "package": "orders.read",
                "name": "FindOrder",
                "description": "find order",
                "aggregates": ["Order"],
                "artifacts": [{"family": "query"}],
                "fields": [{"name": "orderId", "type": "Long"}],
                "resultFields": [{"name": "orderNo", "type": "String"}]
              },
              {
                "tag": "query",
                "package": "orders.read",
                "name": "FindOrder",
                "description": "find order",
                "aggregates": ["Order"],
                "artifacts": [{"family": "query-handler"}]
              }
            ]
            """.trimIndent()
        )

        val snapshot = IrAnalysisSourceProvider().collect(config(dir.toString())) as IrAnalysisSnapshot

        assertEquals(2, snapshot.designElements.size)
        assertEquals(
            listOf(
                listOf(ArtifactSelectionModel(family = "query")),
                listOf(ArtifactSelectionModel(family = "query-handler")),
            ),
            snapshot.designElements.map { it.artifacts },
        )
    }

    @Test
    fun `collect fails clearly for malformed design element shape`() {
        val cases = listOf(
            """{}""" to "ir-analysis design-elements file",
            """[null]""" to "design element at index 0 must be an object",
            """[{"tag":"command","package":"orders","name":" ","description":"submit order"}]""" to
                "design element at index 0 must declare non-blank name",
            """[{"tag":" ","package":"orders","name":"SubmitOrder","description":"submit order"}]""" to
                "design element at index 0 must declare non-blank tag",
            """[{"tag":"command","package":"orders","name":"SubmitOrder","description":"submit order","fields":{"name":"orderId"}}]""" to
                "design element command orders SubmitOrder field 'fields' must be an array",
            """[{"tag":"command","package":"orders","name":"SubmitOrder","description":"submit order","resultFields":"bad"}]""" to
                "design element command orders SubmitOrder field 'resultFields' must be an array",
            """[{"tag":"command","package":"orders","name":"SubmitOrder","description":"submit order","fields":[null]}]""" to
                "design element command orders SubmitOrder fields[0] must be an object",
            """[{"tag":"command","package":"orders","name":"SubmitOrder","description":"submit order","fields":[{"name":" ","type":"Long"}]}]""" to
                "design element command orders SubmitOrder fields[0] must declare non-blank name",
            """[{"tag":"command","package":"orders","name":"SubmitOrder","description":"submit order","fields":[{"name":"orderId","type":" "}]}]""" to
                "design element command orders SubmitOrder fields[0] must declare non-blank type",
            """[{"tag":"command","package":"orders","name":"SubmitOrder","description":"submit order","fields":[{"name":"orderId","type":"Long","nullable":false}]}]""" to
                "design element command orders SubmitOrder fields[0] field nullable is removed; encode nullability in type",
            """[{"tag":"command","package":"orders","name":"SubmitOrder","description":"submit order","artifacts":[null]}]""" to
                "design element command orders SubmitOrder artifacts[0] must be an object",
            """[{"tag":"command","package":"orders","name":"SubmitOrder","description":"submit order","artifacts":[{"variant":"default"}]}]""" to
                "design element command orders SubmitOrder artifacts[0] must declare non-blank family",
        )

        cases.forEachIndexed { index, (json, expectedMessage) ->
            val dir = Files.createTempDirectory("cap4k-ir-malformed-fields-$index")
            dir.resolve("nodes.json").writeText("""[]""")
            dir.resolve("rels.json").writeText("""[]""")
            dir.writeEmptyAggregateElements()
            dir.resolve("design-elements.json").writeText(json)

            val error = assertThrows<IllegalArgumentException> {
                IrAnalysisSourceProvider().collect(config(dir.toString()))
            }

            assertTrue(error.message!!.contains(expectedMessage))
        }
    }

    @Test
    fun `collect rejects removed recovery fields in design elements`() {
        val cases = listOf(
            "desc",
            "requestFields",
            "responseFields",
            "traits",
            "role",
            "scope",
            "entity",
        )

        cases.forEach { field ->
            val dir = Files.createTempDirectory("cap4k-ir-removed-$field")
            dir.resolve("nodes.json").writeText("""[]""")
            dir.resolve("rels.json").writeText("""[]""")
            dir.writeEmptyAggregateElements()
            dir.resolve("design-elements.json").writeText(
                """
                [
                  {
                    "tag": "query",
                    "package": "orders",
                    "name": "FindOrder",
                    "description": "find order",
                    "$field": []
                  }
                ]
                """.trimIndent()
            )

            val error = assertThrows<IllegalArgumentException> {
                IrAnalysisSourceProvider().collect(config(dir.toString()))
            }

            assertEquals("design element FindOrder uses removed fields: $field", error.message)
        }
    }

    @Test
    fun `drawing board fails fast with symbol capability and recovery when design metadata is missing`() {
        val dir = Files.createTempDirectory("cap4k-ir-missing-design-metadata")
        dir.resolve("nodes.json").writeText(
            """
            [
              {
                "id":"demo.application.queries.FindOrderQry.Request",
                "name":"FindOrderQry.Request",
                "fullName":"demo.application.queries.FindOrderQry.Request",
                "type":"query",
                "missingMetadata":["com.only4.cap4k.analysis.metadata.DesignBlockMetadata"],
                "metadataOwner":"demo.application.queries.FindOrderQry"
              }
            ]
            """.trimIndent()
        )
        dir.resolve("rels.json").writeText("""[]""")
        dir.writeEmptyAggregateElements()

        val error = assertThrows<IllegalArgumentException> {
            IrAnalysisSourceProvider().collect(config(dir.toString(), generators = setOf("drawing-board")))
        }

        assertTrue(error.message!!.contains("demo.application.queries.FindOrderQry"))
        assertTrue(!error.message!!.contains("FindOrderQry.Request; missing metadata"))
        assertTrue(error.message!!.contains("DesignBlockMetadata"))
        assertTrue(error.message!!.contains("affected capability: Drawing Board"))
        assertTrue(error.message!!.contains("restore the default ddd-default generator template"))
        assertTrue(error.message!!.contains("compileOnly classpath"))
        assertTrue(error.message!!.contains("will not emit an apparently complete partial result"))
    }

    @Test
    fun `drawing board fails fast when repository aggregate metadata is missing`() {
        val dir = Files.createTempDirectory("cap4k-ir-missing-repository-metadata")
        dir.resolve("nodes.json").writeText(
            """
            [
              {
                "id":"demo.adapter.domain.repositories.OrderJpaRepositoryAdapter",
                "name":"OrderJpaRepositoryAdapter",
                "fullName":"demo.adapter.domain.repositories.OrderJpaRepositoryAdapter",
                "type":"repository",
                "missingMetadata":["com.only4.cap4k.analysis.metadata.AggregateElementMetadata"],
                "metadataOwner":"demo.adapter.domain.repositories.OrderJpaRepositoryAdapter"
              }
            ]
            """.trimIndent()
        )
        dir.resolve("rels.json").writeText("[]")
        dir.writeEmptyAggregateElements()

        val error = assertThrows<IllegalArgumentException> {
            IrAnalysisSourceProvider().collect(config(dir.toString(), generators = setOf("drawing-board")))
        }

        assertTrue(error.message!!.contains("demo.adapter.domain.repositories.OrderJpaRepositoryAdapter"))
        assertTrue(error.message!!.contains("AggregateElementMetadata"))
        assertTrue(error.message!!.contains("affected capability: Drawing Board"))
        assertTrue(!error.message!!.contains("Flow Analysis"))
    }

    @Test
    fun `flow analysis fails fast only for aggregate metadata loss`() {
        val dir = Files.createTempDirectory("cap4k-ir-missing-aggregate-metadata")
        dir.resolve("nodes.json").writeText(
            """
            [
              {
                "id":"demo.domain.aggregates.order.Order",
                "name":"Order",
                "fullName":"demo.domain.aggregates.order.Order",
                "type":"aggregate",
                "missingMetadata":["com.only4.cap4k.analysis.metadata.AggregateElementMetadata"]
              },
              {
                "id":"demo.application.commands.SubmitOrderCmd.Request",
                "name":"SubmitOrderCmd.Request",
                "fullName":"demo.application.commands.SubmitOrderCmd.Request",
                "type":"command",
                "missingMetadata":["com.only4.cap4k.analysis.metadata.DesignBlockMetadata"]
              }
            ]
            """.trimIndent()
        )
        dir.resolve("rels.json").writeText("""[]""")
        dir.writeEmptyAggregateElements()

        val error = assertThrows<IllegalArgumentException> {
            IrAnalysisSourceProvider().collect(config(dir.toString(), generators = setOf("flow")))
        }

        assertTrue(error.message!!.contains("demo.domain.aggregates.order.Order"))
        assertTrue(error.message!!.contains("AggregateElementMetadata"))
        assertTrue(error.message!!.contains("affected capability: Flow Analysis"))
        assertTrue(!error.message!!.contains("SubmitOrderCmd.Request; missing metadata"))
    }

    @Test
    fun `combined analysis request reports every missing metadata owner and affected capability`() {
        val dir = Files.createTempDirectory("cap4k-ir-multiple-metadata-gaps")
        dir.resolve("nodes.json").writeText(
            """
            [
              {
                "id":"demo.FindOrderQry.Request",
                "name":"Request",
                "fullName":"demo.FindOrderQry.Request",
                "type":"query",
                "missingMetadata":["com.only4.cap4k.analysis.metadata.DesignBlockMetadata"],
                "metadataOwner":"demo.FindOrderQry"
              },
              {
                "id":"demo.Order",
                "name":"Order",
                "fullName":"demo.Order",
                "type":"aggregate",
                "missingMetadata":["com.only4.cap4k.analysis.metadata.AggregateElementMetadata"],
                "metadataOwner":"demo.domain.Order"
              }
            ]
            """.trimIndent()
        )
        dir.resolve("rels.json").writeText("[]")
        dir.writeEmptyAggregateElements()

        val error = assertThrows<IllegalArgumentException> {
            IrAnalysisSourceProvider().collect(
                config(dir.toString(), generators = setOf("drawing-board", "flow"))
            )
        }

        assertTrue(error.message!!.contains("demo.FindOrderQry"))
        assertTrue(error.message!!.contains("DesignBlockMetadata"))
        assertTrue(error.message!!.contains("affected capability: Drawing Board"))
        assertTrue(
            error.message!!.contains(
                "- symbol: demo.domain.Order; missing metadata: " +
                    "com.only4.cap4k.analysis.metadata.AggregateElementMetadata; " +
                    "affected capability: Drawing Board, Flow Analysis"
            )
        )
        assertTrue(error.message!!.contains("Requested analysis capabilities: Drawing Board, Flow Analysis"))
    }

    @Test
    fun `drawing board rejects candidate graph when design elements are absent even without legacy completeness fields`() {
        val dir = Files.createTempDirectory("cap4k-ir-legacy-incomplete-drawing")
        dir.resolve("nodes.json").writeText(
            """[{"id":"FindOrderQry.Request","name":"Request","fullName":"demo.FindOrderQry.Request","type":"query"}]"""
        )
        dir.resolve("rels.json").writeText("""[]""")
        dir.writeEmptyAggregateElements()

        val error = assertThrows<IllegalArgumentException> {
            IrAnalysisSourceProvider().collect(config(dir.toString(), generators = setOf("drawing-board")))
        }

        assertTrue(error.message!!.contains("demo.FindOrderQry.Request"))
        assertTrue(error.message!!.contains("Drawing Board"))
    }

    @Test
    fun `drawing board validates metadata completeness independently for every input directory`() {
        val completeDir = Files.createTempDirectory("cap4k-ir-complete-module")
        completeDir.resolve("nodes.json").writeText(
            """[{"id":"CompleteQry.Request","name":"Request","fullName":"demo.CompleteQry.Request","type":"query"}]"""
        )
        completeDir.resolve("rels.json").writeText("[]")
        completeDir.writeEmptyAggregateElements()
        completeDir.resolve("design-elements.json").writeText(
            """
            [
              {
                "tag":"query",
                "package":"complete",
                "name":"Complete",
                "artifacts":[{"family":"query"}],
                "fields":[],
                "resultFields":[]
              }
            ]
            """.trimIndent()
        )

        val incompleteDir = Files.createTempDirectory("cap4k-ir-incomplete-module")
        incompleteDir.resolve("nodes.json").writeText(
            """[{"id":"MissingQry.Request","name":"Request","fullName":"demo.MissingQry.Request","type":"query"}]"""
        )
        incompleteDir.resolve("rels.json").writeText("[]")
        incompleteDir.writeEmptyAggregateElements()

        val error = assertThrows<IllegalArgumentException> {
            IrAnalysisSourceProvider().collect(
                config(
                    completeDir.toString(),
                    incompleteDir.toString(),
                    generators = setOf("drawing-board"),
                )
            )
        }

        assertTrue(error.message!!.contains("Analysis input: $incompleteDir"))
        assertTrue(error.message!!.contains("demo.MissingQry.Request"))
        assertTrue(!error.message!!.contains("demo.CompleteQry.Request; missing metadata"))
    }

    @Test
    fun `drawing board accepts an empty input directory alongside a complete module`() {
        val completeDir = Files.createTempDirectory("cap4k-ir-complete-module")
        completeDir.resolve("nodes.json").writeText(
            """[{"id":"CompleteQry.Request","name":"Request","fullName":"demo.CompleteQry.Request","type":"query"}]"""
        )
        completeDir.resolve("rels.json").writeText("[]")
        completeDir.writeEmptyAggregateElements()
        completeDir.resolve("design-elements.json").writeText(
            """[{"tag":"query","package":"complete","name":"Complete","artifacts":[{"family":"query"}]}]"""
        )
        val emptyDir = Files.createTempDirectory("cap4k-ir-empty-module")
        emptyDir.resolve("nodes.json").writeText("[]")
        emptyDir.resolve("rels.json").writeText("[]")
        emptyDir.writeEmptyAggregateElements()

        val snapshot = IrAnalysisSourceProvider().collect(
            config(completeDir.toString(), emptyDir.toString(), generators = setOf("drawing-board"))
        ) as IrAnalysisSnapshot

        assertEquals(listOf("Complete"), snapshot.designElements.map { it.name })
    }

    @Test
    fun `collect returns empty design elements when file is absent`() {
        val dir = Files.createTempDirectory("cap4k-ir-no-design")
        dir.resolve("nodes.json").writeText("""[]""")
        dir.resolve("rels.json").writeText("""[]""")
        dir.writeEmptyAggregateElements()

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

    @Test
    fun `collect rejects a missing aggregate elements sidecar`() {
        val dir = Files.createTempDirectory("cap4k-ir-missing-aggregate-elements")
        dir.resolve("nodes.json").writeText("[]")
        dir.resolve("rels.json").writeText("[]")

        val error = assertThrows<IllegalArgumentException> {
            IrAnalysisSourceProvider().collect(config(dir.toString()))
        }

        assertEquals("ir-analysis inputDir is missing aggregate-elements.json: $dir", error.message)
    }

    private fun Path.writeEmptyAggregateElements() {
        resolve("aggregate-elements.json").writeText("[]")
    }

    private fun config(
        vararg inputDirs: String,
        generators: Set<String> = emptySet(),
    ): ProjectConfig {
        return ProjectConfig(
            basePackage = "com.acme.demo",
            layout = ProjectLayout.MULTI_MODULE,
            modules = emptyMap(),
            sources = mapOf(
                "ir-analysis" to SourceConfig(
                    options = mapOf("inputDirs" to inputDirs.toList()),
                )
            ),
            generators = generators.associateWith { GeneratorConfig() },
            templates = TemplateConfig("ddd-default", emptyList(), ConflictPolicy.SKIP),
        )
    }
}

package com.only4.cap4k.plugin.pipeline.generator.drawingboard

import com.only4.cap4k.plugin.pipeline.api.ArtifactLayoutConfig
import com.only4.cap4k.plugin.pipeline.api.ArtifactSelectionModel
import com.only4.cap4k.plugin.pipeline.api.CanonicalModel
import com.only4.cap4k.plugin.pipeline.api.ConflictPolicy
import com.only4.cap4k.plugin.pipeline.api.DrawingBoardElementModel
import com.only4.cap4k.plugin.pipeline.api.DrawingBoardFieldModel
import com.only4.cap4k.plugin.pipeline.api.DrawingBoardModel
import com.only4.cap4k.plugin.pipeline.api.GeneratorConfig
import com.only4.cap4k.plugin.pipeline.api.OutputRootLayout
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig
import com.only4.cap4k.plugin.pipeline.api.ProjectLayout
import com.only4.cap4k.plugin.pipeline.api.TemplateConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Path

class DrawingBoardArtifactPlannerTest {

    @Test
    fun `plans one artifact per non empty supported tag group in order`() {
        val planner = DrawingBoardArtifactPlanner()

        val plan = planner.plan(config(), model())

        assertEquals(
            listOf(
                "drawing_board_command",
                "drawing_board_query",
                "drawing_board_client",
                "drawing_board_api_payload",
                "drawing_board_domain_event",
                "drawing_board_integration_event",
            ),
            plan.map { it.outputPath.removePrefix("design/").removeSuffix(".json") }
        )
        assertEquals(
            listOf(
                "drawing-board/document.json.peb",
                "drawing-board/document.json.peb",
                "drawing-board/document.json.peb",
                "drawing-board/document.json.peb",
                "drawing-board/document.json.peb",
                "drawing-board/document.json.peb",
            ),
            plan.map { it.templateId },
        )
        assertEquals("drawing-board", plan.first().generatorId)
        assertEquals("project", plan.first().moduleRole)
        assertEquals("command", plan.first().context["drawingBoardTag"])
        assertEquals("query", plan[1].context["drawingBoardTag"])
        assertEquals("client", plan[2].context["drawingBoardTag"])
        assertEquals("api_payload", plan[3].context["drawingBoardTag"])
        assertEquals("domain_event", plan[4].context["drawingBoardTag"])
        assertEquals("integration_event", plan[5].context["drawingBoardTag"])
    }

    @Test
    fun `plan item context exposes formal drawing block keys and explicit artifact selections`() {
        val planner = DrawingBoardArtifactPlanner()

        val plan = planner.plan(
            config(),
            CanonicalModel(
                drawingBoard = DrawingBoardModel(
                    elements = listOf(
                        DrawingBoardElementModel(
                            tag = "query",
                            packageName = "orders.queries",
                            name = "ReadOrder",
                            description = "read order",
                            artifacts = listOf(
                                ArtifactSelectionModel(family = "query", variant = "page"),
                            ),
                            requestFields = listOf(
                                DrawingBoardFieldModel(
                                    name = "orderId",
                                    type = "Long",
                                ),
                            ),
                            responseFields = listOf(
                                DrawingBoardFieldModel(
                                    name = "status",
                                    type = "String",
                                ),
                            ),
                        ),
                        DrawingBoardElementModel(
                            tag = "integration_event",
                            packageName = "orders.events",
                            name = "OrderCreated",
                            description = "order created",
                            artifacts = listOf(
                                ArtifactSelectionModel(family = "integration-event", variant = "inbound"),
                                ArtifactSelectionModel(family = "integration-subscriber"),
                            ),
                            requestFields = listOf(
                                DrawingBoardFieldModel(
                                    name = "orderId",
                                    type = "Long",
                                ),
                            ),
                            responseFields = emptyList(),
                        ),
                    ),
                ),
            ),
        )

        val queryContext = plan.single { it.outputPath.endsWith("drawing_board_query.json") }.context
        val integrationContext = plan.single { it.outputPath.endsWith("drawing_board_integration_event.json") }.context
        val queryElement = (queryContext["elements"] as List<*>).filterIsInstance<DrawingBoardElementModel>().single()
        val integrationElement = (integrationContext["elements"] as List<*>)
            .filterIsInstance<DrawingBoardElementModel>()
            .single()

        assertEquals("query", queryElement.tag)
        assertEquals("orders.queries", queryElement.packageName)
        assertEquals("ReadOrder", queryElement.name)
        assertEquals("read order", queryElement.description)
        assertEquals(listOf(ArtifactSelectionModel(family = "query", variant = "page")), queryElement.artifacts)
        assertEquals(1, queryElement.requestFields.size)
        assertEquals(1, queryElement.responseFields.size)
        assertEquals(
            listOf(
                ArtifactSelectionModel(family = "integration-event", variant = "inbound"),
                ArtifactSelectionModel(family = "integration-subscriber"),
            ),
            integrationElement.artifacts,
        )
    }

    @Test
    fun `rejects invalid output root values`() {
        val planner = DrawingBoardArtifactPlanner()

        val absolutePath = Path.of("design").toAbsolutePath().toString()
        val absoluteEx = assertThrows(IllegalArgumentException::class.java) {
            planner.plan(config(outputRoot = absolutePath), model())
        }
        assertEquals(
            "drawing-board outputRoot must be a valid relative filesystem path: $absolutePath",
            absoluteEx.message,
        )

        val traversalEx = assertThrows(IllegalArgumentException::class.java) {
            planner.plan(config(outputRoot = "../design"), model())
        }
        assertEquals(
            "drawing-board outputRoot must be a valid relative filesystem path: ../design",
            traversalEx.message,
        )

        val dotEx = assertThrows(IllegalArgumentException::class.java) {
            planner.plan(config(outputRoot = " design"), model())
        }
        assertEquals(
            "drawing-board outputRoot must be a valid relative filesystem path:  design",
            dotEx.message,
        )

        val normalizedBlankEx = assertThrows(IllegalArgumentException::class.java) {
            planner.plan(config(outputRoot = "design/.."), model())
        }
        assertEquals(
            "drawing-board outputRoot must be a valid relative filesystem path: design/..",
            normalizedBlankEx.message,
        )
    }

    @Test
    fun `supports custom relative output root`() {
        val planner = DrawingBoardArtifactPlanner()

        val plan = planner.plan(config(outputRoot = "design/generated"), model())

        assertEquals("design/generated/drawing_board_command.json", plan.first().outputPath)
    }

    @Test
    fun `plans overwrite conflict policy for observation outputs`() {
        val planner = DrawingBoardArtifactPlanner()

        val plan = planner.plan(config(), model())

        assertTrue(plan.isNotEmpty())
        assertTrue(plan.all { it.conflictPolicy == ConflictPolicy.OVERWRITE })
    }

    @Test
    fun `returns empty plan when drawing board slice is missing`() {
        val planner = DrawingBoardArtifactPlanner()

        val plan = planner.plan(config(), CanonicalModel())

        assertTrue(plan.isEmpty())
    }

    private fun config(outputRoot: String = "design"): ProjectConfig =
        ProjectConfig(
            basePackage = "com.acme.demo",
            layout = ProjectLayout.MULTI_MODULE,
            modules = emptyMap(),
            sources = emptyMap(),
            generators = mapOf(
                "drawing-board" to GeneratorConfig(),
            ),
            templates = TemplateConfig("ddd-default", emptyList(), ConflictPolicy.SKIP),
            artifactLayout = ArtifactLayoutConfig(drawingBoard = OutputRootLayout(outputRoot)),
        )

    private fun model(): CanonicalModel =
        CanonicalModel(
            drawingBoard = DrawingBoardModel(
                elements = listOf(
                    DrawingBoardElementModel(
                        tag = "command",
                        packageName = "orders.commands",
                        name = "SubmitOrder",
                        description = "submit order",
                    ),
                    DrawingBoardElementModel(
                        tag = "client",
                        packageName = "ops.cli",
                        name = "FetchStatus",
                        description = "fetch status",
                    ),
                    DrawingBoardElementModel(
                        tag = "query",
                        packageName = "orders.queries",
                        name = "ReadOrder",
                        description = "read order",
                    ),
                    DrawingBoardElementModel(
                        tag = "api_payload",
                        packageName = "orders.payload",
                        name = "OrderPayload",
                        description = "payload",
                    ),
                    DrawingBoardElementModel(
                        tag = "domain_event",
                        packageName = "orders.domain",
                        name = "OrderEntity",
                        description = "domain entity",
                    ),
                    DrawingBoardElementModel(
                        tag = "integration_event",
                        packageName = "orders.events",
                        name = "OrderCreated",
                        description = "order created",
                        role = "inbound",
                        eventName = "order.created",
                    ),
                    DrawingBoardElementModel(
                        tag = "ignored",
                        packageName = "orders.ignored",
                        name = "Ignored",
                        description = "ignored",
                    ),
                ),
            ),
        )
}

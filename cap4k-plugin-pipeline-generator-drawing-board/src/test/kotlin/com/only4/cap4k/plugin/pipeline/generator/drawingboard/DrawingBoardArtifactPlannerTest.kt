package com.only4.cap4k.plugin.pipeline.generator.drawingboard

import com.only4.cap4k.plugin.pipeline.api.ArtifactLayoutConfig
import com.only4.cap4k.plugin.pipeline.api.CanonicalModel
import com.only4.cap4k.plugin.pipeline.api.ConflictPolicy
import com.only4.cap4k.plugin.pipeline.api.DrawingBoardElementModel
import com.only4.cap4k.plugin.pipeline.api.DrawingBoardModel
import com.only4.cap4k.plugin.pipeline.api.GeneratorConfig
import com.only4.cap4k.plugin.pipeline.api.OutputRootLayout
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig
import com.only4.cap4k.plugin.pipeline.api.ProjectLayout
import com.only4.cap4k.plugin.pipeline.api.TemplateConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.nio.file.Path

class DrawingBoardArtifactPlannerTest {

    @Test
    fun `plans one artifact per non empty supported tag group in order`() {
        val planner = DrawingBoardArtifactPlanner()

        val plan = planner.plan(config(), model())

        assertEquals(
            listOf(
                "drawing_board_cli",
                "drawing_board_cmd",
                "drawing_board_qry",
                "drawing_board_payload",
                "drawing_board_de",
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
            ),
            plan.map { it.templateId },
        )
        assertEquals("drawing-board", plan.first().generatorId)
        assertEquals("project", plan.first().moduleRole)
        assertEquals("cli", plan.first().context["drawingBoardTag"])
        assertEquals("cmd", plan[1].context["drawingBoardTag"])
        assertEquals("qry", plan[2].context["drawingBoardTag"])
        assertEquals("payload", plan[3].context["drawingBoardTag"])
        assertEquals("de", plan[4].context["drawingBoardTag"])
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

        assertEquals("design/generated/drawing_board_cli.json", plan.first().outputPath)
    }

    @Test
    fun `fails when drawing board slice is missing`() {
        val planner = DrawingBoardArtifactPlanner()

        val ex = assertThrows(IllegalArgumentException::class.java) {
            planner.plan(config(), CanonicalModel())
        }

        assertEquals(
            "drawing-board generator requires at least one parsed design-elements.json input.",
            ex.message,
        )
    }

    private fun config(outputRoot: String = "design"): ProjectConfig =
        ProjectConfig(
            basePackage = "com.acme.demo",
            layout = ProjectLayout.MULTI_MODULE,
            modules = emptyMap(),
            sources = emptyMap(),
            generators = mapOf(
                "drawing-board" to GeneratorConfig(
                    enabled = true,
                ),
            ),
            templates = TemplateConfig("ddd-default", emptyList(), ConflictPolicy.SKIP),
            artifactLayout = ArtifactLayoutConfig(drawingBoard = OutputRootLayout(outputRoot)),
        )

    private fun model(): CanonicalModel =
        CanonicalModel(
            drawingBoard = DrawingBoardModel(
                elements = listOf(
                    DrawingBoardElementModel(
                        tag = "cmd",
                        packageName = "orders.commands",
                        name = "SubmitOrder",
                        description = "submit order",
                    ),
                    DrawingBoardElementModel(
                        tag = "cli",
                        packageName = "ops.cli",
                        name = "FetchStatus",
                        description = "fetch status",
                    ),
                    DrawingBoardElementModel(
                        tag = "qry",
                        packageName = "orders.queries",
                        name = "ReadOrder",
                        description = "read order",
                    ),
                    DrawingBoardElementModel(
                        tag = "payload",
                        packageName = "orders.payload",
                        name = "OrderPayload",
                        description = "payload",
                    ),
                    DrawingBoardElementModel(
                        tag = "de",
                        packageName = "orders.domain",
                        name = "OrderEntity",
                        description = "domain entity",
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

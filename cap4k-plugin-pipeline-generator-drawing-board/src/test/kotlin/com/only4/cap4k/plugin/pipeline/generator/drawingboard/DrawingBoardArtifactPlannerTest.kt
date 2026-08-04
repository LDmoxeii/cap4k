package com.only4.cap4k.plugin.pipeline.generator.drawingboard

import com.only4.cap4k.plugin.pipeline.api.AnalysisGraphModel
import com.only4.cap4k.plugin.pipeline.api.AnalysisNodeModel
import com.only4.cap4k.plugin.pipeline.api.AggregateElementModel
import com.only4.cap4k.plugin.pipeline.api.ArtifactLayoutConfig
import com.only4.cap4k.plugin.pipeline.api.ArtifactOutputKind
import com.only4.cap4k.plugin.pipeline.api.ArtifactSelectionModel
import com.only4.cap4k.plugin.pipeline.api.CanonicalModel
import com.only4.cap4k.plugin.pipeline.api.CanonicalTypeIdentity
import com.only4.cap4k.plugin.pipeline.api.CanonicalTypeKind
import com.only4.cap4k.plugin.pipeline.api.ConflictPolicy
import com.only4.cap4k.plugin.pipeline.api.DesignBlockModel
import com.only4.cap4k.plugin.pipeline.api.DrawingBoardElementModel
import com.only4.cap4k.plugin.pipeline.api.DrawingBoardModel
import com.only4.cap4k.plugin.pipeline.api.GeneratorConfig
import com.only4.cap4k.plugin.pipeline.api.OutputRootLayout
import com.only4.cap4k.plugin.pipeline.api.PipelineCapabilityActivation
import com.only4.cap4k.plugin.pipeline.api.PipelinePublicTasks
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig
import com.only4.cap4k.plugin.pipeline.api.ProjectLayout
import com.only4.cap4k.plugin.pipeline.api.SemanticBuiltinType
import com.only4.cap4k.plugin.pipeline.api.SemanticBuiltinTypeRef
import com.only4.cap4k.plugin.pipeline.api.SemanticNamedTypeRef
import com.only4.cap4k.plugin.pipeline.api.SemanticValueDefinition
import com.only4.cap4k.plugin.pipeline.api.SemanticValueField
import com.only4.cap4k.plugin.pipeline.api.SemanticValueRole
import com.only4.cap4k.plugin.pipeline.api.TemplateConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
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
                "drawing_board_capability",
                "drawing_board_api_payload",
                "drawing_board_domain_event",
                "drawing_board_integration_event",
                "drawing_board_domain_service",
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
                "drawing-board/document.json.peb",
            ),
            plan.map { it.templateId },
        )
        assertEquals(PipelineCapabilityActivation.EXPLICIT_CONFIGURATION, planner.descriptor.activation)
        assertEquals("drawing-board", plan.first().generatorId)
        assertEquals("project", plan.first().moduleRole)
        assertEquals("command", plan.first().context["drawingBoardTag"])
        assertEquals("query", plan[1].context["drawingBoardTag"])
        assertEquals("capability", plan[2].context["drawingBoardTag"])
        assertEquals("api_payload", plan[3].context["drawingBoardTag"])
        assertEquals("domain_event", plan[4].context["drawingBoardTag"])
        assertEquals("integration_event", plan[5].context["drawingBoardTag"])
        assertEquals("domain_service", plan[6].context["drawingBoardTag"])
        assertTrue(plan.all { it.outputKind == ArtifactOutputKind.OUTPUT_ARTIFACT })
        assertTrue(plan.all { it.resolvedOutputRoot == "design" })
        assertEquals(
            listOf(PipelinePublicTasks.ANALYSIS_PLAN, PipelinePublicTasks.ANALYSIS_GENERATE),
            planner.descriptor.tasks,
        )
    }

    @Test
    fun `rejects missing design metadata before drawing board planning`() {
        val planner = DrawingBoardArtifactPlanner()
        val error = assertThrows(IllegalArgumentException::class.java) {
            planner.plan(
                config(),
                CanonicalModel(
                    analysisGraph = AnalysisGraphModel(
                        inputDirs = listOf("application/build/cap4k-code-analysis"),
                        nodes = listOf(
                            AnalysisNodeModel(
                                id = "demo.FindOrderQry.Request",
                                name = "Request",
                                fullName = "demo.FindOrderQry.Request",
                                type = "query",
                                missingMetadata = listOf("com.only4.cap4k.analysis.metadata.DesignBlockMetadata"),
                                metadataOwner = "demo.FindOrderQry",
                            )
                        ),
                        edges = emptyList(),
                    ),
                    drawingBoard = DrawingBoardModel(emptyList()),
                ),
            )
        }

        assertTrue(error.message!!.contains("demo.FindOrderQry"))
        assertTrue(error.message!!.contains("affected capability: Drawing Board"))
        assertTrue(error.message!!.contains("compileOnly classpath"))
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
                            request = semanticValue(
                                "orders.queries",
                                "ReadOrder.Request",
                                SemanticValueRole.QUERY_REQUEST,
                                fields = listOf(
                                    semanticField("zeta", SemanticBuiltinType.LONG),
                                    semanticField("alpha", SemanticBuiltinType.STRING),
                                ),
                            ),
                            response = semanticValue(
                                "orders.queries",
                                "ReadOrder.Response",
                                SemanticValueRole.QUERY_RESPONSE,
                                fields = listOf(
                                    semanticField("omega", SemanticBuiltinType.STRING),
                                    semanticField("beta", SemanticBuiltinType.BOOLEAN),
                                ),
                            ),
                        ),
                        DrawingBoardElementModel(
                            tag = "integration_event",
                            packageName = "orders.events",
                            name = "OrderCreated",
                            description = "order created",
                            artifacts = listOf(
                                ArtifactSelectionModel(family = "integration-subscriber"),
                                ArtifactSelectionModel(family = "integration-event", variant = "inbound"),
                            ),
                            request = semanticValue(
                                "orders.events",
                                "OrderCreated.Event",
                                SemanticValueRole.INTEGRATION_EVENT,
                                fields = listOf(semanticField("orderId", SemanticBuiltinType.LONG)),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val queryContext = plan.single { it.outputPath.endsWith("drawing_board_query.json") }.context
        val integrationContext = plan.single { it.outputPath.endsWith("drawing_board_integration_event.json") }.context
        val queryElement = (queryContext["elements"] as List<*>).filterIsInstance<DrawingBoardRenderElement>().single()
        val integrationElement = (integrationContext["elements"] as List<*>)
            .filterIsInstance<DrawingBoardRenderElement>()
            .single()

        assertEquals("query", queryElement.tag)
        assertEquals("orders.queries", queryElement.packageName)
        assertEquals("ReadOrder", queryElement.name)
        assertEquals("read order", queryElement.description)
        assertEquals(
            listOf(ArtifactSelectionModel(family = "query", variant = "page")),
            queryElement.designJsonArtifacts,
        )
        assertEquals(listOf("zeta", "alpha"), queryElement.fields.map { it.name })
        assertEquals(listOf("omega", "beta"), queryElement.resultFields.map { it.name })
        assertEquals(
            listOf(
                ArtifactSelectionModel(family = "integration-event", variant = "inbound"),
                ArtifactSelectionModel(family = "integration-subscriber"),
            ),
            integrationElement.designJsonArtifacts,
        )
    }

    @Test
    fun `preserves nested field declaration order while flattening drawing board fields`() {
        val nested = semanticValue(
            packageName = "orders.commands",
            typePath = "SubmitOrder.Request.Details",
            role = SemanticValueRole.VALUE_OBJECT,
            fields = listOf(
                semanticField("second", SemanticBuiltinType.STRING),
                semanticField("first", SemanticBuiltinType.LONG),
            ),
        )
        val request = semanticValue(
            packageName = "orders.commands",
            typePath = "SubmitOrder.Request",
            role = SemanticValueRole.COMMAND_REQUEST,
            fields = listOf(
                semanticField("top", SemanticBuiltinType.STRING),
                SemanticValueField(
                    name = "details",
                    type = SemanticNamedTypeRef(
                        symbol = CanonicalTypeIdentity(
                            packageName = "orders.commands",
                            typePath = listOf("SubmitOrder", "Request", "Details"),
                            kind = CanonicalTypeKind.NESTED_VALUE,
                        ),
                    ),
                ),
                semanticField("last", SemanticBuiltinType.BOOLEAN),
            ),
            nestedDefinitions = listOf(nested),
        )

        val plan = DrawingBoardArtifactPlanner().plan(
            config(),
            CanonicalModel(
                drawingBoard = DrawingBoardModel(
                    elements = listOf(
                        DrawingBoardElementModel(
                            tag = "command",
                            packageName = "orders.commands",
                            name = "SubmitOrder",
                            description = "submit order",
                            request = request,
                        ),
                    ),
                ),
            ),
        )

        val element = (plan.single().context["elements"] as List<*>)
            .filterIsInstance<DrawingBoardRenderElement>()
            .single()
        assertEquals(
            listOf("top", "details", "details.second", "details.first", "last"),
            element.fields.map { it.name },
        )
    }

    @Test
    fun `does not plan from authoring design blocks without analysis drawing board`() {
        val planner = DrawingBoardArtifactPlanner()

        val plan = planner.plan(
            config(),
            CanonicalModel(
                designBlocks = listOf(
                    DesignBlockModel(
                        tag = "query",
                        packageName = "orders.queries",
                        name = "ReadOrder",
                        description = "read order",
                        artifacts = listOf(
                            ArtifactSelectionModel("query"),
                            ArtifactSelectionModel("query-handler"),
                        ),
                        request = semanticValue("orders.queries", "ReadOrderQry.Request", SemanticValueRole.QUERY_REQUEST),
                    ),
                    DesignBlockModel(
                        tag = "domain_service",
                        packageName = "orders.domain",
                        name = "OrderPolicyService",
                        description = "order policy service",
                        artifacts = listOf(ArtifactSelectionModel("domain-service")),
                        request = semanticValue(
                            "orders.domain",
                            "OrderPolicyService.Request",
                            SemanticValueRole.API_PAYLOAD_REQUEST,
                        ),
                    ),
                ),
            ),
        )

        assertTrue(plan.isEmpty())
    }

    @Test
    fun `plans from analysis drawing board when authoring design blocks also exist`() {
        val planner = DrawingBoardArtifactPlanner()

        val plan = planner.plan(
            config(),
            CanonicalModel(
                designBlocks = listOf(
                    DesignBlockModel(
                        tag = "query",
                        packageName = "orders.queries",
                        name = "CanonicalReadOrder",
                        description = "canonical read order",
                        artifacts = listOf(ArtifactSelectionModel("query")),
                        request = semanticValue(
                            "orders.queries",
                            "CanonicalReadOrderQry.Request",
                            SemanticValueRole.QUERY_REQUEST,
                        ),
                    ),
                ),
                drawingBoard = DrawingBoardModel(
                    elements = listOf(
                        DrawingBoardElementModel(
                            tag = "command",
                            packageName = "orders.commands",
                            name = "LegacySubmitOrder",
                            description = "legacy submit order",
                            request = semanticValue(
                                "orders.commands",
                                "LegacySubmitOrder.Request",
                                SemanticValueRole.COMMAND_REQUEST,
                            ),
                        ),
                    ),
                ),
            ),
        )

        assertEquals(listOf("design/drawing_board_command.json"), plan.map { it.outputPath })
        assertEquals(
            listOf("LegacySubmitOrder"),
            (plan.single().context["elements"] as List<*>).filterIsInstance<DrawingBoardRenderElement>().map { it.name },
        )
    }

    @Test
    fun `plans aggregate structure as a separate non design drawing board artifact`() {
        val repository = AggregateElementModel(
            carrierQualifiedName = "com.acme.demo.adapter.domain.repositories.OrderJpaRepositoryAdapter",
            aggregate = "Order",
            name = "OrderRepository",
            packageName = "com.acme.demo.adapter.domain.repositories",
            description = "Order repository carrier",
            type = "repository",
            root = false,
        )

        val plan = DrawingBoardArtifactPlanner().plan(
            config(),
            CanonicalModel(
                drawingBoard = DrawingBoardModel(
                    elements = emptyList(),
                    aggregateElements = listOf(repository),
                ),
            ),
        )

        val artifact = plan.single()
        assertEquals("design/drawing_board_aggregate_elements.json", artifact.outputPath)
        assertEquals("drawing-board/aggregate-elements.json.peb", artifact.templateId)
        assertEquals(listOf(repository), artifact.context["aggregateElements"])
        assertFalse("drawingBoardTag" in artifact.context)
        assertTrue(artifact.context.values.none { value -> value == "repository" })
        assertEquals(ConflictPolicy.OVERWRITE, artifact.conflictPolicy)
        assertEquals(ArtifactOutputKind.OUTPUT_ARTIFACT, artifact.outputKind)
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
    fun `plans domain service file with default artifacts omitted from context`() {
        val planner = DrawingBoardArtifactPlanner()

        val plan = planner.plan(
            config(),
            CanonicalModel(
                drawingBoard = DrawingBoardModel(
                    elements = listOf(
                        DrawingBoardElementModel(
                            tag = "domain_service",
                            packageName = "orders.domain",
                            name = "OrderPolicyService",
                            description = "order policy service",
                            artifacts = listOf(ArtifactSelectionModel("domain-service")),
                            request = semanticValue(
                                "orders.domain",
                                "OrderPolicyService.Request",
                                SemanticValueRole.API_PAYLOAD_REQUEST,
                            ),
                        ),
                    ),
                ),
            ),
        )

        assertEquals(
            listOf(
                "design/drawing_board_domain_service.json",
            ),
            plan.map { it.outputPath },
        )
        val domainService = (plan[0].context["elements"] as List<*>)
            .filterIsInstance<DrawingBoardRenderElement>()
            .single()
        assertFalse(domainService.includeDesignJsonArtifacts)
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

    private fun semanticValue(
        packageName: String,
        typePath: String,
        role: SemanticValueRole,
        fields: List<SemanticValueField> = emptyList(),
        nestedDefinitions: List<SemanticValueDefinition> = emptyList(),
    ): SemanticValueDefinition = SemanticValueDefinition(
        identity = CanonicalTypeIdentity(
            packageName = packageName,
            typePath = typePath.split('.'),
            kind = CanonicalTypeKind.NESTED_VALUE,
        ),
        role = role,
        fields = fields,
        nestedDefinitions = nestedDefinitions,
    )

    private fun semanticField(name: String, kind: SemanticBuiltinType): SemanticValueField =
        SemanticValueField(name = name, type = SemanticBuiltinTypeRef(kind))

    private fun model(): CanonicalModel =
        CanonicalModel(
            drawingBoard = DrawingBoardModel(
                elements = listOf(
                    DrawingBoardElementModel(
                        tag = "command",
                        packageName = "orders.commands",
                        name = "SubmitOrder",
                        description = "submit order",
                        request = semanticValue(
                            "orders.commands",
                            "SubmitOrder.Request",
                            SemanticValueRole.COMMAND_REQUEST,
                        ),
                    ),
                    DrawingBoardElementModel(
                        tag = "capability",
                        packageName = "ops.capabilities",
                        name = "FetchStatus",
                        description = "fetch status",
                        request = semanticValue(
                            "ops.capabilities",
                            "FetchStatus.Request",
                            SemanticValueRole.CAPABILITY_REQUEST,
                        ),
                    ),
                    DrawingBoardElementModel(
                        tag = "query",
                        packageName = "orders.queries",
                        name = "ReadOrder",
                        description = "read order",
                        request = semanticValue(
                            "orders.queries",
                            "ReadOrder.Request",
                            SemanticValueRole.QUERY_REQUEST,
                        ),
                    ),
                    DrawingBoardElementModel(
                        tag = "api_payload",
                        packageName = "orders.payload",
                        name = "OrderPayload",
                        description = "payload",
                        request = semanticValue(
                            "orders.payload",
                            "OrderPayload.Request",
                            SemanticValueRole.API_PAYLOAD_REQUEST,
                        ),
                    ),
                    DrawingBoardElementModel(
                        tag = "domain_event",
                        packageName = "orders.domain",
                        name = "OrderEntity",
                        description = "domain entity",
                        request = semanticValue(
                            "orders.domain",
                            "OrderEntity.Event",
                            SemanticValueRole.DOMAIN_EVENT,
                        ),
                    ),
                    DrawingBoardElementModel(
                        tag = "integration_event",
                        packageName = "orders.events",
                        name = "OrderCreated",
                        description = "order created",
                        artifacts = listOf(ArtifactSelectionModel("integration-event", "inbound")),
                        eventName = "order.created",
                        request = semanticValue(
                            "orders.events",
                            "OrderCreated.Event",
                            SemanticValueRole.INTEGRATION_EVENT,
                        ),
                    ),
                    DrawingBoardElementModel(
                        tag = "domain_service",
                        packageName = "orders.domain",
                        name = "OrderPolicyService",
                        description = "order policy service",
                        request = semanticValue(
                            "orders.domain",
                            "OrderPolicyService.Request",
                            SemanticValueRole.API_PAYLOAD_REQUEST,
                        ),
                    ),
                    DrawingBoardElementModel(
                        tag = "ignored",
                        packageName = "orders.ignored",
                        name = "Ignored",
                        description = "ignored",
                        request = semanticValue(
                            "orders.ignored",
                            "Ignored.Request",
                            SemanticValueRole.API_PAYLOAD_REQUEST,
                        ),
                    ),
                ),
            ),
        )
}

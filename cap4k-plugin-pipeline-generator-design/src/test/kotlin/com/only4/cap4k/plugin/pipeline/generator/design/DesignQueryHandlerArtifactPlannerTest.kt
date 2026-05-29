package com.only4.cap4k.plugin.pipeline.generator.design

import com.only4.cap4k.plugin.pipeline.api.ArtifactLayoutConfig
import com.only4.cap4k.plugin.pipeline.api.ArtifactSelectionModel
import com.only4.cap4k.plugin.pipeline.api.CanonicalModel
import com.only4.cap4k.plugin.pipeline.api.ConflictPolicy
import com.only4.cap4k.plugin.pipeline.api.DesignBlockModel
import com.only4.cap4k.plugin.pipeline.api.FieldModel
import com.only4.cap4k.plugin.pipeline.api.GeneratorConfig
import com.only4.cap4k.plugin.pipeline.api.PackageLayout
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig
import com.only4.cap4k.plugin.pipeline.api.ProjectLayout
import com.only4.cap4k.plugin.pipeline.api.TemplateConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DesignQueryHandlerArtifactPlannerTest {

    @Test
    fun `plans bounded query handlers into adapter module paths`() {
        val planner = DesignQueryHandlerArtifactPlanner()
        assertEquals("query-handler", planner.id)

        val items = planner.plan(
            config = projectConfig(
                modules = mapOf(
                    "application" to "demo-application",
                    "adapter" to "demo-adapter",
                )
            ),
            model = CanonicalModel(
                designBlocks = listOf(
                    queryBlock(
                        name = "FindOrder",
                        description = "find order",
                        resultFields = listOf(
                            FieldModel("responseStatus", "com.bar.Status"),
                            FieldModel("snapshot", "Snapshot", nullable = true),
                            FieldModel("snapshot.updatedAt", "java.time.LocalDateTime"),
                        ),
                    ),
                    queryBlock(
                        name = "FindOrderList",
                        description = "find order list",
                        resultFields = listOf(
                            FieldModel("responseStatus", "com.bar.Status"),
                        ),
                    ),
                    queryBlock(
                        name = "FindOrderPage",
                        description = "find order page",
                        resultFields = listOf(
                            FieldModel("responseStatus", "com.bar.Status"),
                        ),
                    ),
                ),
            ),
        )

        assertEquals(
            listOf(
                "design/query_handler.kt.peb",
                "design/query_handler.kt.peb",
                "design/query_handler.kt.peb",
            ),
            items.map { it.templateId },
        )
        assertEquals(
            "demo-adapter/src/main/kotlin/com/acme/demo/adapter/application/queries/order/read/FindOrderQryHandler.kt",
            items[0].outputPath,
        )
        assertEquals(
            "demo-adapter/src/main/kotlin/com/acme/demo/adapter/application/queries/order/read/FindOrderListQryHandler.kt",
            items[1].outputPath,
        )
        assertEquals(
            "demo-adapter/src/main/kotlin/com/acme/demo/adapter/application/queries/order/read/FindOrderPageQryHandler.kt",
            items[2].outputPath,
        )
        assertEquals("adapter", items[0].moduleRole)
        assertEquals("com.acme.demo.adapter.application.queries.order.read", items[0].context["packageName"])
        assertEquals("FindOrderQryHandler", items[0].context["typeName"])
        assertEquals("FindOrderQry", items[0].context["queryTypeName"])
        assertEquals(
            listOf("com.acme.demo.application.queries.order.read.FindOrderQry"),
            items[0].context["imports"],
        )
        val responseFields = requireNotNull(items[0].context["responseFields"] as? List<*>)
            .map { requireNotNull(it as? DesignQueryHandlerResponseFieldModel) }
        assertEquals(
            listOf(
                DesignQueryHandlerResponseFieldModel("responseStatus"),
                DesignQueryHandlerResponseFieldModel("snapshot"),
            ),
            responseFields,
        )
    }

    @Test
    fun `custom query handler layout imports query from custom query layout`() {
        val planner = DesignQueryHandlerArtifactPlanner()

        val items = planner.plan(
            config = projectConfig(
                modules = mapOf(
                    "application" to "demo-application",
                    "adapter" to "demo-adapter",
                ),
                artifactLayout = ArtifactLayoutConfig(
                    designQuery = PackageLayout("application.readmodels"),
                    designQueryHandler = PackageLayout("adapter.readmodels"),
                ),
            ),
            model = CanonicalModel(
                designBlocks = listOf(
                    queryBlock(
                        name = "FindOrder",
                        description = "find order",
                    ),
                ),
            ),
        )

        val handler = items.single()
        assertEquals(
            "demo-adapter/src/main/kotlin/com/acme/demo/adapter/readmodels/order/read/FindOrderQryHandler.kt",
            handler.outputPath,
        )
        assertEquals("com.acme.demo.adapter.readmodels.order.read", handler.context["packageName"])
        assertEquals(
            listOf("com.acme.demo.application.readmodels.order.read.FindOrderQry"),
            handler.context["imports"],
        )
        assertEquals(
            "com.acme.demo.application.readmodels.order.read.FindOrderQry",
            handler.context["queryTypeFqn"],
        )
    }

    @Test
    fun `keeps handler template selection unaffected by page or list suffixes`() {
        val planner = DesignQueryHandlerArtifactPlanner()

        val items = planner.plan(
            config = projectConfig(
                modules = mapOf(
                    "application" to "demo-application",
                    "adapter" to "demo-adapter",
                )
            ),
            model = CanonicalModel(
                designBlocks = listOf(
                    queryBlock(
                        name = "FindOrderPageable",
                        description = "pageable",
                    ),
                    queryBlock(
                        name = "FindOrderListing",
                        description = "listing",
                    ),
                ),
            ),
        )

        assertTrue(items.all { it.templateId == "design/query_handler.kt.peb" })
    }

    @Test
    fun `explicit page query without query handler does not plan query handler`() {
        val queryItems = DesignQueryArtifactPlanner().plan(
            config = projectConfig(
                modules = mapOf(
                    "application" to "demo-application",
                    "adapter" to "demo-adapter",
                )
            ),
            model = CanonicalModel(
                designBlocks = listOf(
                    queryBlock(
                        name = "FindOrderPage",
                        artifacts = listOf(ArtifactSelectionModel("query", "page")),
                    )
                ),
            ),
        )
        val handlerItems = DesignQueryHandlerArtifactPlanner().plan(
            config = projectConfig(
                modules = mapOf(
                    "application" to "demo-application",
                    "adapter" to "demo-adapter",
                )
            ),
            model = CanonicalModel(
                designBlocks = listOf(
                    queryBlock(
                        name = "FindOrderPage",
                        artifacts = listOf(ArtifactSelectionModel("query", "page")),
                    )
                ),
            ),
        )

        assertEquals(1, queryItems.size)
        assertEquals(true, queryItems.single().context["pageRequest"])
        assertTrue(handlerItems.isEmpty())
    }

    @Test
    fun `fails when adapter module is missing`() {
        val planner = DesignQueryHandlerArtifactPlanner()

        val error = assertThrows(IllegalStateException::class.java) {
            planner.plan(
                config = projectConfig(modules = mapOf("application" to "demo-application")),
                model = CanonicalModel(
                    designBlocks = listOf(queryBlock()),
                ),
            )
        }

        assertEquals("adapter module is required", error.message)
    }

    private fun projectConfig(
        modules: Map<String, String>,
        artifactLayout: ArtifactLayoutConfig = ArtifactLayoutConfig(),
    ) = ProjectConfig(
        basePackage = "com.acme.demo",
        layout = ProjectLayout.MULTI_MODULE,
        modules = modules,
        sources = emptyMap(),
        generators = mapOf("query-handler" to GeneratorConfig()),
        templates = TemplateConfig("ddd-default", emptyList(), ConflictPolicy.SKIP),
        artifactLayout = artifactLayout,
    )

    private fun queryBlock(
        name: String = "FindOrder",
        description: String = "find order",
        resultFields: List<FieldModel> = emptyList(),
        artifacts: List<ArtifactSelectionModel> = listOf(ArtifactSelectionModel("query-handler")),
    ) = DesignBlockModel(
        tag = "query",
        packageName = "order.read",
        name = name,
        description = description,
        aggregates = listOf("Order"),
        artifacts = artifacts,
        resultFields = resultFields,
    )
}

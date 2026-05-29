package com.only4.cap4k.plugin.pipeline.generator.design

import com.only4.cap4k.plugin.pipeline.api.ArtifactSelectionModel
import com.only4.cap4k.plugin.pipeline.api.CanonicalModel
import com.only4.cap4k.plugin.pipeline.api.ConflictPolicy
import com.only4.cap4k.plugin.pipeline.api.DesignBlockModel
import com.only4.cap4k.plugin.pipeline.api.GeneratorConfig
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig
import com.only4.cap4k.plugin.pipeline.api.ProjectLayout
import com.only4.cap4k.plugin.pipeline.api.TemplateConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class DesignQueryArtifactPlannerTest {

    @Test
    fun `designQuery plans all queries with query template`() {
        val planner = DesignQueryArtifactPlanner()
        assertEquals("query", planner.id)

        val items = planner.plan(
            config = projectConfig(modules = mapOf("application" to "demo-application")),
            model = CanonicalModel(
                designBlocks = listOf(
                    queryModel(name = "FindOrder"),
                    queryModel(name = "FindOrderList"),
                    queryModel(name = "FindOrderPage", variant = "page"),
                ),
            ),
        )

        assertEquals(
            listOf("design/query.kt.peb", "design/query.kt.peb", "design/query.kt.peb"),
            items.map { it.templateId },
        )
        assertEquals(
            listOf(
                "demo-application/src/main/kotlin/com/acme/demo/application/queries/order/read/FindOrderQry.kt",
                "demo-application/src/main/kotlin/com/acme/demo/application/queries/order/read/FindOrderListQry.kt",
                "demo-application/src/main/kotlin/com/acme/demo/application/queries/order/read/FindOrderPageQry.kt",
            ),
            items.map { it.outputPath },
        )
        assertEquals(listOf("query", "query", "query"), items.map { it.generatorId })
        assertEquals("com.acme.demo.application.queries.order.read", items[0].context["packageName"])
        assertEquals("FindOrderQry", items[0].context["typeName"])
    }

    @Test
    fun `sets pageRequest context only from page variant`() {
        val planner = DesignQueryArtifactPlanner()

        val items = planner.plan(
            config = projectConfig(modules = mapOf("application" to "demo-application")),
            model = CanonicalModel(
                designBlocks = listOf(
                    queryModel(name = "FindOrder"),
                    queryModel(name = "FindOrderPage", variant = "page"),
                ),
            ),
        )

        assertEquals(
            listOf(false, true),
            items.map { it.context["pageRequest"] },
        )
    }

    @Test
    fun `list and page suffixes do not affect template selection or pageRequest`() {
        val planner = DesignQueryArtifactPlanner()

        val items = planner.plan(
            config = projectConfig(modules = mapOf("application" to "demo-application")),
            model = CanonicalModel(
                designBlocks = listOf(
                    queryModel(name = "FindOrderList"),
                    queryModel(name = "FindOrderPage"),
                ),
            ),
        )

        assertEquals(
            listOf("design/query.kt.peb", "design/query.kt.peb"),
            items.map { it.templateId },
        )
        assertEquals(
            listOf(false, false),
            items.map { it.context["pageRequest"] },
        )
    }

    @Test
    fun `fails when application module is missing`() {
        val planner = DesignQueryArtifactPlanner()

        val error = assertThrows(IllegalStateException::class.java) {
            planner.plan(
                config = projectConfig(modules = emptyMap()),
                model = CanonicalModel(designBlocks = listOf(queryModel())),
            )
        }

        assertEquals("application module is required", error.message)
    }

    private fun queryModel(
        name: String = "FindOrder",
        variant: String = "",
    ) = DesignBlockModel(
        tag = "query",
        packageName = "order.read",
        name = name,
        description = "find order",
        aggregates = listOf("Order"),
        artifacts = listOf(ArtifactSelectionModel("query", variant)),
    )

    private fun projectConfig(modules: Map<String, String>) = ProjectConfig(
        basePackage = "com.acme.demo",
        layout = ProjectLayout.MULTI_MODULE,
        modules = modules,
        sources = emptyMap(),
        generators = mapOf("query" to GeneratorConfig()),
        templates = TemplateConfig("ddd-default", emptyList(), ConflictPolicy.SKIP),
    )
}

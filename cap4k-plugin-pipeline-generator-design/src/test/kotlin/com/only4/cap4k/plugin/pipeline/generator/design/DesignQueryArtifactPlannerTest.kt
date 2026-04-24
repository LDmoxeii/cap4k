package com.only4.cap4k.plugin.pipeline.generator.design

import com.only4.cap4k.plugin.pipeline.api.AggregateRef
import com.only4.cap4k.plugin.pipeline.api.CanonicalModel
import com.only4.cap4k.plugin.pipeline.api.ConflictPolicy
import com.only4.cap4k.plugin.pipeline.api.GeneratorConfig
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig
import com.only4.cap4k.plugin.pipeline.api.ProjectLayout
import com.only4.cap4k.plugin.pipeline.api.QueryModel
import com.only4.cap4k.plugin.pipeline.api.QueryVariant
import com.only4.cap4k.plugin.pipeline.api.TemplateConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class DesignQueryArtifactPlannerTest {

    @Test
    fun `designQuery plans query templates from canonical variants`() {
        val planner = DesignQueryArtifactPlanner()

        val items = planner.plan(
            config = projectConfig(modules = mapOf("application" to "demo-application")),
            model = CanonicalModel(
                queries = listOf(
                    queryModel(typeName = "FindOrderQry", variant = QueryVariant.DEFAULT),
                    queryModel(typeName = "FindOrderListQry", variant = QueryVariant.LIST),
                    queryModel(typeName = "FindOrderPageQry", variant = QueryVariant.PAGE),
                ),
            ),
        )

        assertEquals(
            listOf("design/query.kt.peb", "design/query_list.kt.peb", "design/query_page.kt.peb"),
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
        assertEquals(listOf("design-query", "design-query", "design-query"), items.map { it.generatorId })
        assertEquals("com.acme.demo.application.queries.order.read", items[0].context["packageName"])
        assertEquals("FindOrderQry", items[0].context["typeName"])
    }

    @Test
    fun `does not guess query template from type name`() {
        val planner = DesignQueryArtifactPlanner()

        val items = planner.plan(
            config = projectConfig(modules = mapOf("application" to "demo-application")),
            model = CanonicalModel(
                queries = listOf(
                    queryModel(typeName = "FindOrderPageQry", variant = QueryVariant.DEFAULT),
                    queryModel(typeName = "FindOrderQry", variant = QueryVariant.PAGE),
                ),
            ),
        )

        assertEquals(
            listOf("design/query.kt.peb", "design/query_page.kt.peb"),
            items.map { it.templateId },
        )
    }

    @Test
    fun `fails when application module is missing`() {
        val planner = DesignQueryArtifactPlanner()

        val error = assertThrows(IllegalStateException::class.java) {
            planner.plan(
                config = projectConfig(modules = emptyMap()),
                model = CanonicalModel(queries = listOf(queryModel())),
            )
        }

        assertEquals("application module is required", error.message)
    }

    private fun queryModel(
        typeName: String = "FindOrderQry",
        variant: QueryVariant = QueryVariant.DEFAULT,
    ) = QueryModel(
        packageName = "order.read",
        typeName = typeName,
        description = "find order",
        aggregateRef = AggregateRef("Order", "com.acme.demo.domain.aggregates.order"),
        variant = variant,
    )

    private fun projectConfig(modules: Map<String, String>) = ProjectConfig(
        basePackage = "com.acme.demo",
        layout = ProjectLayout.MULTI_MODULE,
        modules = modules,
        sources = emptyMap(),
        generators = mapOf("design-query" to GeneratorConfig(enabled = true)),
        templates = TemplateConfig("ddd-default", emptyList(), ConflictPolicy.SKIP),
    )
}

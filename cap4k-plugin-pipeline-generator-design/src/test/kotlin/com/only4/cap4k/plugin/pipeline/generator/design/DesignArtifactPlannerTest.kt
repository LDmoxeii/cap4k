package com.only4.cap4k.plugin.pipeline.generator.design

import com.only4.cap4k.plugin.pipeline.api.*
import kotlin.test.Test
import kotlin.test.assertEquals

class DesignArtifactPlannerTest {

    @Test
    fun `plans command and query artifacts into application module paths`() {
        val planner = DesignArtifactPlanner()

        val items = planner.plan(
            config = ProjectConfig(
                basePackage = "com.acme.demo",
                layout = ProjectLayout.MULTI_MODULE,
                modules = mapOf("application" to "demo-application"),
                sources = emptyMap(),
                generators = mapOf("design" to GeneratorConfig(enabled = true)),
                templates = TemplateConfig("ddd-default", emptyList(), ConflictPolicy.SKIP),
            ),
            model = CanonicalModel(
                requests = listOf(
                    RequestModel(
                        kind = RequestKind.COMMAND,
                        packageName = "order.submit",
                        typeName = "SubmitOrderCmd",
                        description = "submit order",
                        aggregateName = "Order",
                        aggregatePackageName = "com.acme.demo.domain.aggregates.order",
                        requestFields = emptyList(),
                        responseFields = emptyList(),
                    ),
                    RequestModel(
                        kind = RequestKind.QUERY,
                        packageName = "order.read",
                        typeName = "FindOrderQry",
                        description = "find order",
                        aggregateName = "Order",
                        aggregatePackageName = "com.acme.demo.domain.aggregates.order",
                        requestFields = emptyList(),
                        responseFields = emptyList(),
                    ),
                ),
            ),
        )

        assertEquals(2, items.size)
        assertEquals("design/command.kt.peb", items.first().templateId)
        assertEquals("design/query.kt.peb", items.last().templateId)
        assertEquals(
            "demo-application/src/main/kotlin/com/acme/demo/application/commands/order/submit/SubmitOrderCmd.kt",
            items.first().outputPath,
        )
    }
}

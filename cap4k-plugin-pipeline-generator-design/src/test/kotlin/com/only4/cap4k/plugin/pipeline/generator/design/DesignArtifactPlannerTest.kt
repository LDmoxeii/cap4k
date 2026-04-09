package com.only4.cap4k.plugin.pipeline.generator.design

import com.only4.cap4k.plugin.pipeline.api.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.nio.file.Path

class DesignArtifactPlannerTest {

    @Test
    fun `plans command and query artifacts into application module paths`() {
        val planner = DesignArtifactPlanner()

        val items = planner.plan(
            config = projectConfig(modules = mapOf("application" to "demo-application")),
            model = canonicalModel(),
        )

        assertEquals(2, items.size)

        val command = items.first()
        assertEquals("design", command.generatorId)
        assertEquals("application", command.moduleRole)
        assertEquals("design/command.kt.peb", command.templateId)
        assertEquals(
            "demo-application/src/main/kotlin/com/acme/demo/application/commands/order/submit/SubmitOrderCmd.kt",
            command.outputPath,
        )
        assertEquals(
            mapOf(
                "packageName" to "com.acme.demo.application.commands.order.submit",
                "typeName" to "SubmitOrderCmd",
                "description" to "submit order",
                "aggregateName" to "Order",
            ),
            command.context,
        )
        assertEquals(ConflictPolicy.SKIP, command.conflictPolicy)

        val query = items.last()
        assertEquals("design", query.generatorId)
        assertEquals("application", query.moduleRole)
        assertEquals("design/query.kt.peb", query.templateId)
        assertEquals(
            "demo-application/src/main/kotlin/com/acme/demo/application/queries/order/read/FindOrderQry.kt",
            query.outputPath,
        )
        assertEquals(
            mapOf(
                "packageName" to "com.acme.demo.application.queries.order.read",
                "typeName" to "FindOrderQry",
                "description" to "find order",
                "aggregateName" to "Order",
            ),
            query.context,
        )
        assertEquals(ConflictPolicy.SKIP, query.conflictPolicy)
    }

    @Test
    fun `fails when application module is missing`() {
        val planner = DesignArtifactPlanner()

        val ex = assertThrows(IllegalStateException::class.java) {
            planner.plan(
                config = projectConfig(modules = emptyMap()),
                model = canonicalModel(),
            )
        }

        assertEquals("application module is required", ex.message)
    }

    @Test
    fun `fails fast when application module uses gradle project path syntax`() {
        val planner = DesignArtifactPlanner()

        assertThrows(IllegalArgumentException::class.java) {
            planner.plan(
                config = projectConfig(modules = mapOf("application" to ":demo-application")),
                model = canonicalModel(),
            )
        }
    }

    @Test
    fun `fails fast when application module path is blank`() {
        val planner = DesignArtifactPlanner()

        assertThrows(IllegalArgumentException::class.java) {
            planner.plan(
                config = projectConfig(modules = mapOf("application" to "")),
                model = canonicalModel(),
            )
        }
    }

    @Test
    fun `fails fast when application module path is absolute`() {
        val planner = DesignArtifactPlanner()
        val absolutePath = Path.of("demo-application").toAbsolutePath().toString()

        assertThrows(IllegalArgumentException::class.java) {
            planner.plan(
                config = projectConfig(modules = mapOf("application" to absolutePath)),
                model = canonicalModel(),
            )
        }
    }

    @Test
    fun `fails fast when application module path is rooted`() {
        val planner = DesignArtifactPlanner()

        assertThrows(IllegalArgumentException::class.java) {
            planner.plan(
                config = projectConfig(modules = mapOf("application" to "/demo-application")),
                model = canonicalModel(),
            )
        }
    }

    @Test
    fun `fails fast when application module path traverses parent`() {
        val planner = DesignArtifactPlanner()

        assertThrows(IllegalArgumentException::class.java) {
            planner.plan(
                config = projectConfig(modules = mapOf("application" to "../demo-application")),
                model = canonicalModel(),
            )
        }
    }

    private fun projectConfig(modules: Map<String, String>) = ProjectConfig(
        basePackage = "com.acme.demo",
        layout = ProjectLayout.MULTI_MODULE,
        modules = modules,
        sources = emptyMap(),
        generators = mapOf("design" to GeneratorConfig(enabled = true)),
        templates = TemplateConfig("ddd-default", emptyList(), ConflictPolicy.SKIP),
    )

    private fun canonicalModel() = CanonicalModel(
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
    )
    }

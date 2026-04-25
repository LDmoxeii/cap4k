package com.only4.cap4k.plugin.pipeline.generator.design

import com.only4.cap4k.plugin.pipeline.api.AggregateRef
import com.only4.cap4k.plugin.pipeline.api.ArtifactLayoutConfig
import com.only4.cap4k.plugin.pipeline.api.CanonicalModel
import com.only4.cap4k.plugin.pipeline.api.CommandModel
import com.only4.cap4k.plugin.pipeline.api.CommandVariant
import com.only4.cap4k.plugin.pipeline.api.ConflictPolicy
import com.only4.cap4k.plugin.pipeline.api.FieldModel
import com.only4.cap4k.plugin.pipeline.api.GeneratorConfig
import com.only4.cap4k.plugin.pipeline.api.PackageLayout
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig
import com.only4.cap4k.plugin.pipeline.api.ProjectLayout
import com.only4.cap4k.plugin.pipeline.api.QueryModel
import com.only4.cap4k.plugin.pipeline.api.QueryVariant
import com.only4.cap4k.plugin.pipeline.api.TemplateConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DesignCommandArtifactPlannerTest {

    @Test
    fun `designCommand plans only command artifacts`() {
        val planner = DesignCommandArtifactPlanner()

        val items = planner.plan(
            config = projectConfig(modules = mapOf("application" to "demo-application")),
            model = CanonicalModel(
                commands = listOf(commandModel()),
                queries = listOf(queryModel()),
            ),
        )

        val command = items.single()
        assertEquals("design-command", command.generatorId)
        assertEquals("application", command.moduleRole)
        assertEquals("design/command.kt.peb", command.templateId)
        assertEquals(
            "demo-application/src/main/kotlin/com/acme/demo/application/commands/order/submit/SubmitOrderCmd.kt",
            command.outputPath,
        )
        assertEquals("com.acme.demo.application.commands.order.submit", command.context["packageName"])
        assertEquals("SubmitOrderCmd", command.context["typeName"])
        assertEquals("submit order", command.context["description"])
        assertEquals("Order", command.context["aggregateName"])
        assertEquals(emptyList<String>(), command.context["imports"])
        assertEquals(emptyList<DesignRenderFieldModel>(), command.context["requestFields"])
        assertEquals(emptyList<DesignRenderFieldModel>(), command.context["responseFields"])
        assertEquals(ConflictPolicy.SKIP, command.conflictPolicy)
    }

    @Test
    fun `designCommand uses custom artifact layout package root`() {
        val planner = DesignCommandArtifactPlanner()

        val items = planner.plan(
            config = projectConfig(
                modules = mapOf("application" to "demo-application"),
                artifactLayout = ArtifactLayoutConfig(
                    designCommand = PackageLayout("application.usecases.commands"),
                ),
            ),
            model = CanonicalModel(
                commands = listOf(
                    commandModel(
                        packageName = "message.create",
                        typeName = "CreateUserMessageCmd",
                    ),
                ),
            ),
        )

        val command = items.single()
        assertEquals(
            "demo-application/src/main/kotlin/com/acme/demo/application/usecases/commands/message/create/CreateUserMessageCmd.kt",
            command.outputPath,
        )
        assertEquals("com.acme.demo.application.usecases.commands.message.create", command.context["packageName"])
    }

    @Test
    fun `uses sibling command names for unresolved type diagnostics`() {
        val planner = DesignCommandArtifactPlanner()

        val error = assertThrows(IllegalArgumentException::class.java) {
            planner.plan(
                config = projectConfig(modules = mapOf("application" to "demo-application")),
                model = CanonicalModel(
                    commands = listOf(
                        commandModel(
                            typeName = "CreateOrderCmd",
                            requestFields = listOf(FieldModel("other", "ArchiveOrderCmd")),
                        ),
                        commandModel(typeName = "ArchiveOrderCmd"),
                    ),
                ),
            )
        }

        assertTrue(error.message.orEmpty().contains("sibling design-entry references are not supported"))
    }

    @Test
    fun `uses sibling query names for unresolved type diagnostics`() {
        val planner = DesignCommandArtifactPlanner()

        val error = assertThrows(IllegalArgumentException::class.java) {
            planner.plan(
                config = projectConfig(modules = mapOf("application" to "demo-application")),
                model = CanonicalModel(
                    commands = listOf(
                        commandModel(
                            typeName = "CreateOrderCmd",
                            requestFields = listOf(FieldModel("other", "FindOrderQry")),
                        ),
                    ),
                    queries = listOf(queryModel(packageName = "order.submit", typeName = "FindOrderQry")),
                ),
            )
        }

        assertTrue(error.message.orEmpty().contains("sibling design-entry references are not supported"))
    }

    @Test
    fun `fails when application module is missing`() {
        val planner = DesignCommandArtifactPlanner()

        val error = assertThrows(IllegalStateException::class.java) {
            planner.plan(
                config = projectConfig(modules = emptyMap()),
                model = CanonicalModel(commands = listOf(commandModel())),
            )
        }

        assertEquals("application module is required", error.message)
    }

    private fun commandModel(
        packageName: String = "order.submit",
        typeName: String = "SubmitOrderCmd",
        requestFields: List<FieldModel> = emptyList(),
    ) = CommandModel(
        packageName = packageName,
        typeName = typeName,
        description = "submit order",
        aggregateRef = AggregateRef("Order", "com.acme.demo.domain.aggregates.order"),
        requestFields = requestFields,
        variant = CommandVariant.DEFAULT,
    )

    private fun queryModel(
        packageName: String = "order.read",
        typeName: String = "FindOrderQry",
    ) = QueryModel(
        packageName = packageName,
        typeName = typeName,
        description = "find order",
        aggregateRef = AggregateRef("Order", "com.acme.demo.domain.aggregates.order"),
        variant = QueryVariant.DEFAULT,
    )

    private fun projectConfig(
        modules: Map<String, String>,
        artifactLayout: ArtifactLayoutConfig = ArtifactLayoutConfig(),
    ) = ProjectConfig(
        basePackage = "com.acme.demo",
        layout = ProjectLayout.MULTI_MODULE,
        modules = modules,
        sources = emptyMap(),
        generators = mapOf("design-command" to GeneratorConfig(enabled = true)),
        templates = TemplateConfig("ddd-default", emptyList(), ConflictPolicy.SKIP),
        artifactLayout = artifactLayout,
    )
}

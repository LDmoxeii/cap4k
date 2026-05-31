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
import com.only4.cap4k.plugin.pipeline.api.StrongIdKind
import com.only4.cap4k.plugin.pipeline.api.StrongIdModel
import com.only4.cap4k.plugin.pipeline.api.TemplateConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DesignCommandArtifactPlannerTest {

    @Test
    fun `designCommand plans only command family blocks`() {
        val planner = DesignCommandArtifactPlanner()
        assertEquals("command", planner.id)

        val items = planner.plan(
            config = projectConfig(modules = mapOf("application" to "demo-application")),
            model = CanonicalModel(
                designBlocks = listOf(
                    commandBlock(),
                    queryBlock(),
                ),
            ),
        )

        val command = items.single()
        assertEquals("command", command.generatorId)
        assertEquals("application", command.moduleRole)
        assertEquals("design/command.kt.peb", command.templateId)
        assertEquals(
            "demo-application/src/main/kotlin/com/acme/demo/application/commands/order/submit/SubmitOrderCmd.kt",
            command.outputPath,
        )
        assertEquals("com.acme.demo.application.commands.order.submit", command.context["packageName"])
        assertEquals("SubmitOrderCmd", command.context["typeName"])
        assertEquals("submit order", command.context["description"])
        assertEquals(null, command.context["aggregateName"])
        assertEquals(emptyList<String>(), command.context["imports"])
        assertEquals(emptyList<DesignRenderFieldModel>(), command.context["fields"])
        assertEquals(emptyList<DesignRenderFieldModel>(), command.context["resultFields"])
        assertEquals(
            mapOf(
                "tag" to "command",
                "tagKotlinStringLiteral" to "\"command\"",
                "name" to "SubmitOrder",
                "nameKotlinStringLiteral" to "\"SubmitOrder\"",
                "packageName" to "order.submit",
                "packageNameKotlinStringLiteral" to "\"order.submit\"",
                "description" to "submit order",
                "descriptionKotlinStringLiteral" to "\"submit order\"",
                "aggregates" to listOf("Order"),
                "aggregateKotlinStringLiterals" to listOf("\"Order\""),
                "eventName" to "",
                "eventNameKotlinStringLiteral" to "\"\"",
                "family" to "command",
                "familyKotlinStringLiteral" to "\"command\"",
                "variant" to "",
                "variantKotlinStringLiteral" to "\"\"",
            ),
            command.context["buildingBlock"],
        )
        assertEquals(ConflictPolicy.SKIP, command.conflictPolicy)
    }

    @Test
    fun `designCommand resolves strong id field imports from canonical model`() {
        val planner = DesignCommandArtifactPlanner()

        val items = planner.plan(
            config = projectConfig(modules = mapOf("application" to "demo-application")),
            model = CanonicalModel(
                designBlocks = listOf(
                    commandBlock(
                        packageName = "content",
                        name = "CreateContent",
                        fields = listOf(FieldModel("authorId", "AuthorId")),
                    )
                ),
                strongIds = listOf(
                    StrongIdModel(
                        typeName = "AuthorId",
                        packageName = "com.acme.demo.domain.shared.ids",
                        kind = StrongIdKind.REFERENCE,
                    )
                ),
            ),
        )

        val command = items.single()

        assertEquals(listOf("com.acme.demo.domain.shared.ids.AuthorId"), command.context["imports"])
        assertEquals(
            listOf(DesignRenderFieldModel(name = "authorId", renderedType = "AuthorId")),
            command.context["fields"],
        )
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
                designBlocks = listOf(
                    commandBlock(
                        packageName = "message.create",
                        name = "CreateUserMessage",
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
                    designBlocks = listOf(
                        commandBlock(
                            name = "CreateOrder",
                            fields = listOf(FieldModel("other", "ArchiveOrderCmd")),
                        ),
                        commandBlock(name = "ArchiveOrder"),
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
                    designBlocks = listOf(
                        commandBlock(
                            name = "CreateOrder",
                            fields = listOf(FieldModel("other", "FindOrderQry")),
                        ),
                        queryBlock(
                            packageName = "order.submit",
                            name = "FindOrder",
                        ),
                    ),
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
                model = CanonicalModel(designBlocks = listOf(commandBlock())),
            )
        }

        assertEquals("application module is required", error.message)
    }

    private fun commandBlock(
        packageName: String = "order.submit",
        name: String = "SubmitOrder",
        fields: List<FieldModel> = emptyList(),
        artifacts: List<ArtifactSelectionModel> = listOf(ArtifactSelectionModel("command")),
    ) = DesignBlockModel(
        tag = "command",
        packageName = packageName,
        name = name,
        description = "submit order",
        aggregates = listOf("Order"),
        artifacts = artifacts,
        fields = fields,
    )

    private fun queryBlock(
        packageName: String = "order.read",
        name: String = "FindOrder",
    ) = DesignBlockModel(
        tag = "query",
        packageName = packageName,
        name = name,
        description = "find order",
        aggregates = listOf("Order"),
        artifacts = listOf(ArtifactSelectionModel("query")),
    )

    private fun projectConfig(
        modules: Map<String, String>,
        artifactLayout: ArtifactLayoutConfig = ArtifactLayoutConfig(),
    ) = ProjectConfig(
        basePackage = "com.acme.demo",
        layout = ProjectLayout.MULTI_MODULE,
        modules = modules,
        sources = emptyMap(),
        generators = mapOf("command" to GeneratorConfig()),
        templates = TemplateConfig("ddd-default", emptyList(), ConflictPolicy.SKIP),
        artifactLayout = artifactLayout,
    )
}

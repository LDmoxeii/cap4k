package com.only4.cap4k.plugin.pipeline.generator.design

import com.only4.cap4k.plugin.pipeline.api.ArtifactLayoutConfig
import com.only4.cap4k.plugin.pipeline.api.ArtifactSelectionModel
import com.only4.cap4k.plugin.pipeline.api.CanonicalModel
import com.only4.cap4k.plugin.pipeline.api.ConflictPolicy
import com.only4.cap4k.plugin.pipeline.api.DesignBlockModel
import com.only4.cap4k.plugin.pipeline.api.EnumItemModel
import com.only4.cap4k.plugin.pipeline.api.FieldModel
import com.only4.cap4k.plugin.pipeline.api.GeneratorConfig
import com.only4.cap4k.plugin.pipeline.api.PackageLayout
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig
import com.only4.cap4k.plugin.pipeline.api.ProjectLayout
import com.only4.cap4k.plugin.pipeline.api.SharedEnumDefinition
import com.only4.cap4k.plugin.pipeline.api.StrongIdKind
import com.only4.cap4k.plugin.pipeline.api.StrongIdModel
import com.only4.cap4k.plugin.pipeline.api.TemplateConfig
import com.only4.cap4k.plugin.pipeline.api.TypeRegistryConfig
import com.only4.cap4k.plugin.pipeline.api.TypeRegistryEntry
import com.only4.cap4k.plugin.pipeline.api.ValueObjectModel
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
    fun `designCommand passes command result fields into render context`() {
        val planner = DesignCommandArtifactPlanner()

        val items = planner.plan(
            config = projectConfig(modules = mapOf("application" to "demo-application")),
            model = CanonicalModel(
                designBlocks = listOf(
                    commandBlock(
                        packageName = "order.submit",
                        name = "SubmitOrder",
                        fields = listOf(FieldModel("orderId", "Long")),
                        resultFields = listOf(
                            FieldModel("receipt", "Receipt", nullable = true),
                            FieldModel("receipt.receiptId", "String"),
                            FieldModel("accepted", "Boolean", defaultValue = "true"),
                        ),
                    )
                ),
            ),
        )

        val command = items.single()

        assertEquals(
            listOf(
                DesignRenderFieldModel(name = "receipt", renderedType = "Receipt?", nullable = true),
                DesignRenderFieldModel(name = "accepted", renderedType = "Boolean", defaultValue = "true"),
            ),
            command.context["resultFields"],
        )
        assertEquals(
            listOf(
                DesignRenderNestedTypeModel(
                    name = "Receipt",
                    fields = listOf(DesignRenderFieldModel(name = "receiptId", renderedType = "String")),
                ),
            ),
            command.context["resultNestedTypes"],
        )
    }

    @Test
    fun `designCommand resolves shared manifest value object field imports`() {
        val planner = DesignCommandArtifactPlanner()

        val items = planner.plan(
            config = projectConfig(modules = mapOf("application" to "demo-application")),
            model = CanonicalModel(
                designBlocks = listOf(
                    commandBlock(
                        packageName = "booking",
                        name = "CreateBooking",
                        fields = listOf(FieldModel("customerRef", "CustomerRef")),
                    )
                ),
                valueObjects = listOf(
                    ValueObjectModel(
                        name = "CustomerRef",
                        packageName = "com.acme.demo.domain.shared.values",
                    )
                ),
            ),
        )

        val command = items.single()

        assertEquals(listOf("com.acme.demo.domain.shared.values.CustomerRef"), command.context["imports"])
        assertEquals(
            listOf(DesignRenderFieldModel(name = "customerRef", renderedType = "CustomerRef")),
            command.context["fields"],
        )
    }

    @Test
    fun `designCommand resolves shared manifest enum field imports including generic arguments`() {
        val planner = DesignCommandArtifactPlanner()
        val enumItems = listOf(EnumItemModel(1, "PASSPORT", "Passport"))

        val items = planner.plan(
            config = projectConfig(modules = mapOf("application" to "demo-application")),
            model = CanonicalModel(
                designBlocks = listOf(
                    commandBlock(
                        packageName = "document",
                        name = "AttachDocument",
                        fields = listOf(
                            FieldModel("documentType", "DocumentType"),
                            FieldModel("documentTypes", "List<DocumentType>"),
                        ),
                    )
                ),
                sharedEnums = listOf(
                    SharedEnumDefinition(
                        typeName = "DocumentType",
                        packageName = "shared",
                        items = enumItems,
                    )
                ),
            ),
        )

        val command = items.single()

        assertEquals(listOf("com.acme.demo.domain.shared.enums.DocumentType"), command.context["imports"])
        assertEquals(
            listOf(
                DesignRenderFieldModel(name = "documentType", renderedType = "DocumentType"),
                DesignRenderFieldModel(name = "documentTypes", renderedType = "List<DocumentType>"),
            ),
            command.context["fields"],
        )
    }

    @Test
    fun `designCommand still resolves explicit type registry field imports`() {
        val planner = DesignCommandArtifactPlanner()

        val items = planner.plan(
            config = projectConfig(
                modules = mapOf("application" to "demo-application"),
                typeRegistry = TypeRegistryConfig(
                    entries = mapOf("ExternalCustomerRef" to TypeRegistryEntry("com.acme.external.ExternalCustomerRef")),
                ),
            ),
            model = CanonicalModel(
                designBlocks = listOf(
                    commandBlock(
                        packageName = "booking",
                        name = "CreateBooking",
                        fields = listOf(FieldModel("customerRef", "ExternalCustomerRef")),
                    )
                ),
            ),
        )

        val command = items.single()

        assertEquals(listOf("com.acme.external.ExternalCustomerRef"), command.context["imports"])
        assertEquals(
            listOf(DesignRenderFieldModel(name = "customerRef", renderedType = "ExternalCustomerRef")),
            command.context["fields"],
        )
    }

    @Test
    fun `designCommand fails ambiguous aggregate-owned value object without aggregate context`() {
        val planner = DesignCommandArtifactPlanner()

        val error = assertThrows(IllegalArgumentException::class.java) {
            planner.plan(
                config = projectConfig(modules = mapOf("application" to "demo-application")),
                model = CanonicalModel(
                    designBlocks = listOf(
                        commandBlock(
                            packageName = "content",
                            name = "PublishContent",
                            aggregates = emptyList(),
                            fields = listOf(FieldModel("snapshot", "Snapshot")),
                        )
                    ),
                    valueObjects = listOf(
                        ValueObjectModel(
                            name = "Snapshot",
                            packageName = "com.acme.demo.domain.aggregates.content.values",
                            aggregates = listOf("Content"),
                        ),
                        ValueObjectModel(
                            name = "Snapshot",
                            packageName = "com.acme.demo.domain.aggregates.review.values",
                            aggregates = listOf("Review"),
                        ),
                    ),
                ),
            )
        }

        val message = error.message.orEmpty()
        assertTrue(message.contains("ambiguous short type: Snapshot"))
        assertTrue(message.contains("com.acme.demo.domain.aggregates.content.values.Snapshot"))
        assertTrue(message.contains("com.acme.demo.domain.aggregates.review.values.Snapshot"))
    }

    @Test
    fun `designCommand resolves matching aggregate-owned value object in single aggregate context`() {
        val planner = DesignCommandArtifactPlanner()

        val items = planner.plan(
            config = projectConfig(modules = mapOf("application" to "demo-application")),
            model = CanonicalModel(
                designBlocks = listOf(
                    commandBlock(
                        packageName = "content",
                        name = "PublishContent",
                        aggregates = listOf("Content"),
                        fields = listOf(FieldModel("snapshot", "Snapshot")),
                    )
                ),
                valueObjects = listOf(
                    ValueObjectModel(
                        name = "Snapshot",
                        packageName = "com.acme.demo.domain.aggregates.content.values",
                        aggregates = listOf("Content"),
                    ),
                    ValueObjectModel(
                        name = "Snapshot",
                        packageName = "com.acme.demo.domain.aggregates.review.values",
                        aggregates = listOf("Review"),
                    ),
                ),
            ),
        )

        val command = items.single()

        assertEquals(listOf("com.acme.demo.domain.aggregates.content.values.Snapshot"), command.context["imports"])
        assertEquals(
            listOf(DesignRenderFieldModel(name = "snapshot", renderedType = "Snapshot")),
            command.context["fields"],
        )
    }

    @Test
    fun `designCommand prefers matching aggregate-owned value object over shared value object`() {
        val planner = DesignCommandArtifactPlanner()

        val items = planner.plan(
            config = projectConfig(modules = mapOf("application" to "demo-application")),
            model = CanonicalModel(
                designBlocks = listOf(
                    commandBlock(
                        packageName = "content",
                        name = "PublishContent",
                        aggregates = listOf("Content"),
                        fields = listOf(FieldModel("snapshot", "Snapshot")),
                    )
                ),
                valueObjects = listOf(
                    ValueObjectModel(
                        name = "Snapshot",
                        packageName = "com.acme.demo.domain.aggregates.content.values",
                        aggregates = listOf("Content"),
                    ),
                    ValueObjectModel(
                        name = "Snapshot",
                        packageName = "com.acme.demo.domain.shared.values",
                    ),
                ),
            ),
        )

        val command = items.single()

        assertEquals(listOf("com.acme.demo.domain.aggregates.content.values.Snapshot"), command.context["imports"])
        assertEquals(
            listOf(DesignRenderFieldModel(name = "snapshot", renderedType = "Snapshot")),
            command.context["fields"],
        )
    }

    @Test
    fun `designCommand fails ambiguous aggregate-owned value object in multi aggregate context`() {
        val planner = DesignCommandArtifactPlanner()

        val error = assertThrows(IllegalArgumentException::class.java) {
            planner.plan(
                config = projectConfig(modules = mapOf("application" to "demo-application")),
                model = CanonicalModel(
                    designBlocks = listOf(
                        commandBlock(
                            packageName = "content.review",
                            name = "ReviewContent",
                            aggregates = listOf("Content", "Review"),
                            fields = listOf(FieldModel("snapshot", "Snapshot")),
                        )
                    ),
                    valueObjects = listOf(
                        ValueObjectModel(
                            name = "Snapshot",
                            packageName = "com.acme.demo.domain.aggregates.content.values",
                            aggregates = listOf("Content"),
                        ),
                        ValueObjectModel(
                            name = "Snapshot",
                            packageName = "com.acme.demo.domain.aggregates.review.values",
                            aggregates = listOf("Review"),
                        ),
                    ),
                ),
            )
        }

        val message = error.message.orEmpty()
        assertTrue(message.contains("ambiguous short type: Snapshot"))
        assertTrue(message.contains("com.acme.demo.domain.aggregates.content.values.Snapshot"))
        assertTrue(message.contains("com.acme.demo.domain.aggregates.review.values.Snapshot"))
    }

    @Test
    fun `designCommand prefers matching aggregate-owned enum over shared enum`() {
        val planner = DesignCommandArtifactPlanner()
        val enumItems = listOf(EnumItemModel(1, "OPEN", "Open"))

        val items = planner.plan(
            config = projectConfig(modules = mapOf("application" to "demo-application")),
            model = CanonicalModel(
                designBlocks = listOf(
                    commandBlock(
                        packageName = "content",
                        name = "PublishContent",
                        aggregates = listOf("Content"),
                        fields = listOf(FieldModel("status", "Status")),
                    )
                ),
                sharedEnums = listOf(
                    SharedEnumDefinition(
                        typeName = "Status",
                        packageName = "content",
                        items = enumItems,
                        aggregates = listOf("Content"),
                    ),
                    SharedEnumDefinition(
                        typeName = "Status",
                        packageName = "shared",
                        items = enumItems,
                    ),
                ),
            ),
        )

        val command = items.single()

        assertEquals(listOf("com.acme.demo.domain.aggregates.content.enums.Status"), command.context["imports"])
        assertEquals(
            listOf(DesignRenderFieldModel(name = "status", renderedType = "Status")),
            command.context["fields"],
        )
    }

    @Test
    fun `designCommand fails ambiguous aggregate-owned enum in multi aggregate context`() {
        val planner = DesignCommandArtifactPlanner()
        val enumItems = listOf(EnumItemModel(1, "OPEN", "Open"))

        val error = assertThrows(IllegalArgumentException::class.java) {
            planner.plan(
                config = projectConfig(modules = mapOf("application" to "demo-application")),
                model = CanonicalModel(
                    designBlocks = listOf(
                        commandBlock(
                            packageName = "content.review",
                            name = "ReviewContent",
                            aggregates = listOf("Content", "Review"),
                            fields = listOf(FieldModel("status", "Status")),
                        )
                    ),
                    sharedEnums = listOf(
                        SharedEnumDefinition(
                            typeName = "Status",
                            packageName = "content",
                            items = enumItems,
                            aggregates = listOf("Content"),
                        ),
                        SharedEnumDefinition(
                            typeName = "Status",
                            packageName = "review",
                            items = enumItems,
                            aggregates = listOf("Review"),
                        ),
                    ),
                ),
            )
        }

        val message = error.message.orEmpty()
        assertTrue(message.contains("ambiguous short type: Status"))
        assertTrue(message.contains("com.acme.demo.domain.aggregates.content.enums.Status"))
        assertTrue(message.contains("com.acme.demo.domain.aggregates.review.enums.Status"))
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
    fun `unresolved short type diagnostic mentions manifest type inputs and external registry`() {
        val planner = DesignCommandArtifactPlanner()

        val error = assertThrows(IllegalArgumentException::class.java) {
            planner.plan(
                config = projectConfig(modules = mapOf("application" to "demo-application")),
                model = CanonicalModel(
                    designBlocks = listOf(
                        commandBlock(
                            name = "CreateBooking",
                            fields = listOf(FieldModel("customerRef", "CustomerRef")),
                        ),
                    ),
                ),
            )
        }

        val message = error.message.orEmpty()
        assertTrue(message.contains("types.enumManifest"))
        assertTrue(message.contains("types.valueObjectManifest"))
        assertTrue(message.contains("types.registryFile"))
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
        aggregates: List<String> = listOf("Order"),
        fields: List<FieldModel> = emptyList(),
        resultFields: List<FieldModel> = emptyList(),
        artifacts: List<ArtifactSelectionModel> = listOf(ArtifactSelectionModel("command")),
    ) = DesignBlockModel(
        tag = "command",
        packageName = packageName,
        name = name,
        description = "submit order",
        aggregates = aggregates,
        artifacts = artifacts,
        fields = fields,
        resultFields = resultFields,
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
        typeRegistry: TypeRegistryConfig = TypeRegistryConfig(),
    ) = ProjectConfig(
        basePackage = "com.acme.demo",
        layout = ProjectLayout.MULTI_MODULE,
        modules = modules,
        typeRegistry = typeRegistry,
        sources = emptyMap(),
        generators = mapOf("command" to GeneratorConfig()),
        templates = TemplateConfig("ddd-default", emptyList(), ConflictPolicy.SKIP),
        artifactLayout = artifactLayout,
    )
}

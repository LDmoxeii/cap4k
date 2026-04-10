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
            "com.acme.demo.application.commands.order.submit",
            command.context["packageName"],
        )
        assertEquals("SubmitOrderCmd", command.context["typeName"])
        assertEquals("submit order", command.context["description"])
        assertEquals("Order", command.context["aggregateName"])
        assertEquals(emptyList<String>(), command.context["imports"])
        assertEquals(emptyList<DesignRenderFieldModel>(), command.context["requestFields"])
        assertEquals(emptyList<DesignRenderFieldModel>(), command.context["responseFields"])
        assertEquals(
            emptyList<DesignRenderNestedTypeModel>(),
            command.context["requestNestedTypes"],
        )
        assertEquals(
            emptyList<DesignRenderNestedTypeModel>(),
            command.context["responseNestedTypes"],
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
            "com.acme.demo.application.queries.order.read",
            query.context["packageName"],
        )
        assertEquals("FindOrderQry", query.context["typeName"])
        assertEquals("find order", query.context["description"])
        assertEquals("Order", query.context["aggregateName"])
        assertEquals(emptyList<String>(), query.context["imports"])
        assertEquals(emptyList<DesignRenderFieldModel>(), query.context["requestFields"])
        assertEquals(emptyList<DesignRenderFieldModel>(), query.context["responseFields"])
        assertEquals(
            emptyList<DesignRenderNestedTypeModel>(),
            query.context["requestNestedTypes"],
        )
        assertEquals(
            emptyList<DesignRenderNestedTypeModel>(),
            query.context["responseNestedTypes"],
        )
        assertEquals(ConflictPolicy.SKIP, query.conflictPolicy)
    }

    @Test
    fun `plans rich design context for nested request and response fields`() {
        val planner = DesignArtifactPlanner()

        val items = planner.plan(
            config = projectConfig(modules = mapOf("application" to "demo-application")),
            model = richCanonicalModel(),
        )

        val command = items.single()

        assertEquals("com.acme.demo.application.commands.order.submit", command.context["packageName"])
        assertEquals("SubmitOrderCmd", command.context["typeName"])
        assertEquals("submit order", command.context["description"])
        assertEquals("Order", command.context["aggregateName"])
        assertEquals(
            listOf(
                "java.time.Instant",
                "java.time.LocalDateTime",
            ),
            command.context["imports"],
        )
        assertEquals(
            listOf(
                DesignRenderFieldModel(name = "id", renderedType = "Long"),
                DesignRenderFieldModel(name = "tags", renderedType = "List<String>"),
                DesignRenderFieldModel(name = "address", renderedType = "Address", nullable = true),
                DesignRenderFieldModel(name = "shippingAddresses", renderedType = "List<Address>"),
                DesignRenderFieldModel(name = "createdAt", renderedType = "LocalDateTime"),
                DesignRenderFieldModel(name = "externalItem", renderedType = "com.foo.Item"),
            ),
            command.context["requestFields"],
        )
        assertEquals(
            listOf(
                DesignRenderNestedTypeModel(
                    name = "Address",
                    fields = listOf(
                        DesignRenderFieldModel(name = "city", renderedType = "String"),
                        DesignRenderFieldModel(name = "zipCode", renderedType = "String"),
                    ),
                ),
            ),
            command.context["requestNestedTypes"],
        )
        assertEquals(
            listOf(
                DesignRenderFieldModel(name = "item", renderedType = "Item", nullable = true),
                DesignRenderFieldModel(name = "relatedItems", renderedType = "List<Item>"),
                DesignRenderFieldModel(name = "updatedAt", renderedType = "Instant", nullable = true),
                DesignRenderFieldModel(name = "scheduledAt", renderedType = "LocalDateTime"),
            ),
            command.context["responseFields"],
        )
        assertEquals(
            listOf(
                DesignRenderNestedTypeModel(
                    name = "Item",
                    fields = listOf(
                        DesignRenderFieldModel(name = "id", renderedType = "Long"),
                    ),
                ),
            ),
            command.context["responseNestedTypes"],
        )
    }

    @Test
    fun `renders colliding fqcn types fully qualified without imports`() {
        val planner = DesignArtifactPlanner()

        val items = planner.plan(
            config = projectConfig(modules = mapOf("application" to "demo-application")),
            model = CanonicalModel(
                requests = listOf(
                    RequestModel(
                        kind = RequestKind.COMMAND,
                        packageName = "order.submit",
                        typeName = "SubmitOrderCmd",
                        description = "submit order",
                        aggregateName = "Order",
                        aggregatePackageName = "com.acme.demo.domain.aggregates.order",
                        requestFields = listOf(
                            FieldModel("requestStatus", "com.foo.Status"),
                        ),
                        responseFields = listOf(
                            FieldModel("responseStatus", "com.bar.Status"),
                        ),
                    ),
                ),
            ),
        )

        val command = items.single()

        assertEquals(
            listOf(
                DesignRenderFieldModel(name = "requestStatus", renderedType = "com.foo.Status"),
            ),
            command.context["requestFields"],
        )
        assertEquals(
            listOf(
                DesignRenderFieldModel(name = "responseStatus", renderedType = "com.bar.Status"),
            ),
            command.context["responseFields"],
        )
        assertEquals(emptyList<String>(), command.context["imports"])
    }

    @Test
    fun `fails fast when nested type names collide inside request namespace`() {
        val planner = DesignArtifactPlanner()

        val ex = assertThrows(IllegalArgumentException::class.java) {
            planner.plan(
                config = projectConfig(modules = mapOf("application" to "demo-application")),
                model = CanonicalModel(
                    requests = listOf(
                        RequestModel(
                            kind = RequestKind.COMMAND,
                            packageName = "order.submit",
                            typeName = "SubmitOrderCmd",
                            description = "submit order",
                            aggregateName = "Order",
                            aggregatePackageName = "com.acme.demo.domain.aggregates.order",
                            requestFields = listOf(
                                FieldModel("address.city", "String"),
                                FieldModel("Address.street", "String"),
                            ),
                            responseFields = emptyList(),
                        ),
                    ),
                ),
            )
        }

        assertEquals("duplicate nested type name in request namespace: Address", ex.message)
    }

    @Test
    fun `fails fast when nested group has no compatible direct root field`() {
        val planner = DesignArtifactPlanner()

        val ex = assertThrows(IllegalArgumentException::class.java) {
            planner.plan(
                config = projectConfig(modules = mapOf("application" to "demo-application")),
                model = CanonicalModel(
                    requests = listOf(
                        RequestModel(
                            kind = RequestKind.COMMAND,
                            packageName = "order.submit",
                            typeName = "SubmitOrderCmd",
                            description = "submit order",
                            aggregateName = "Order",
                            aggregatePackageName = "com.acme.demo.domain.aggregates.order",
                            requestFields = listOf(
                                FieldModel("address.city", "String"),
                                FieldModel("address.zipCode", "String"),
                            ),
                            responseFields = emptyList(),
                        ),
                    ),
                ),
            )
        }

        assertEquals("missing compatible direct root field for nested type Address in request namespace", ex.message)
    }

    @Test
    fun `fails fast when nested group root field type is incompatible`() {
        val planner = DesignArtifactPlanner()

        val ex = assertThrows(IllegalArgumentException::class.java) {
            planner.plan(
                config = projectConfig(modules = mapOf("application" to "demo-application")),
                model = CanonicalModel(
                    requests = listOf(
                        RequestModel(
                            kind = RequestKind.COMMAND,
                            packageName = "order.submit",
                            typeName = "SubmitOrderCmd",
                            description = "submit order",
                            aggregateName = "Order",
                            aggregatePackageName = "com.acme.demo.domain.aggregates.order",
                            requestFields = listOf(
                                FieldModel("address", "String"),
                                FieldModel("address.city", "String"),
                            ),
                            responseFields = emptyList(),
                        ),
                    ),
                ),
            )
        }

        assertEquals("direct root field address in request namespace must point to nested type Address", ex.message)
    }

    @Test
    fun `fails fast when nested group has duplicate direct root declarations`() {
        val planner = DesignArtifactPlanner()

        val ex = assertThrows(IllegalArgumentException::class.java) {
            planner.plan(
                config = projectConfig(modules = mapOf("application" to "demo-application")),
                model = CanonicalModel(
                    requests = listOf(
                        RequestModel(
                            kind = RequestKind.COMMAND,
                            packageName = "order.submit",
                            typeName = "SubmitOrderCmd",
                            description = "submit order",
                            aggregateName = "Order",
                            aggregatePackageName = "com.acme.demo.domain.aggregates.order",
                            requestFields = listOf(
                                FieldModel("address", "Address"),
                                FieldModel("address", "String"),
                                FieldModel("address.city", "String"),
                            ),
                            responseFields = emptyList(),
                        ),
                    ),
                ),
            )
        }

        assertEquals("duplicate direct root declarations for address in request namespace", ex.message)
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

    private fun richCanonicalModel() = CanonicalModel(
        requests = listOf(
            RequestModel(
                kind = RequestKind.COMMAND,
                packageName = "order.submit",
                typeName = "SubmitOrderCmd",
                description = "submit order",
                aggregateName = "Order",
                aggregatePackageName = "com.acme.demo.domain.aggregates.order",
                requestFields = listOf(
                    FieldModel("id", "Long"),
                    FieldModel("tags", "List<String>"),
                    FieldModel("address", "Address", nullable = true),
                    FieldModel("address.city", "String"),
                    FieldModel("address.zipCode", "String"),
                    FieldModel("shippingAddresses", "List<Address>"),
                    FieldModel("createdAt", "java.time.LocalDateTime"),
                    FieldModel("externalItem", "com.foo.Item"),
                ),
                responseFields = listOf(
                    FieldModel("item", "Item", nullable = true),
                    FieldModel("item.id", "Long"),
                    FieldModel("relatedItems", "List<Item>"),
                    FieldModel("updatedAt", "java.time.Instant", nullable = true),
                    FieldModel("scheduledAt", "java.time.LocalDateTime"),
                ),
            ),
        ),
    )
}

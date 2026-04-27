package com.only4.cap4k.plugin.pipeline.generator.design

import com.only4.cap4k.plugin.pipeline.api.ApiPayloadModel
import com.only4.cap4k.plugin.pipeline.api.CanonicalModel
import com.only4.cap4k.plugin.pipeline.api.CommandModel
import com.only4.cap4k.plugin.pipeline.api.CommandVariant
import com.only4.cap4k.plugin.pipeline.api.ConflictPolicy
import com.only4.cap4k.plugin.pipeline.api.FieldModel
import com.only4.cap4k.plugin.pipeline.api.GeneratorConfig
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig
import com.only4.cap4k.plugin.pipeline.api.ProjectLayout
import com.only4.cap4k.plugin.pipeline.api.TemplateConfig
import com.only4.cap4k.plugin.pipeline.api.ValidatorModel
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DesignApiPayloadArtifactPlannerTest {

    @Test
    fun `api payload planner plans adapter payload artifact with nested request and response hierarchy`() {
        val planner = DesignApiPayloadArtifactPlanner()

        val items = planner.plan(
            config = projectConfig(modules = mapOf("adapter" to "demo-adapter")),
            model = CanonicalModel(
                apiPayloads = listOf(
                    ApiPayloadModel(
                        packageName = "account",
                        typeName = "BatchSaveAccountList",
                        description = "batch save account list payload",
                        requestFields = listOf(
                            FieldModel("address", "Address", nullable = true),
                            FieldModel("address.city", "String"),
                            FieldModel("address.zipCode", "String"),
                        ),
                        responseFields = listOf(
                            FieldModel("result", "Result", nullable = true),
                            FieldModel("result.success", "Boolean"),
                        ),
                    ),
                ),
            ),
        )

        val payload = items.single()
        assertEquals("design-api-payload", payload.generatorId)
        assertEquals("design/api_payload.kt.peb", payload.templateId)
        assertEquals(
            "demo-adapter/src/main/kotlin/com/acme/demo/adapter/portal/api/payload/account/BatchSaveAccountList.kt",
            payload.outputPath,
        )
        assertEquals("com.acme.demo.adapter.portal.api.payload.account", payload.context["packageName"])
        assertEquals("BatchSaveAccountList", payload.context["typeName"])
        assertEquals("batch save account list payload", payload.context["description"])
        assertEquals(
            listOf(
                DesignRenderFieldModel(name = "address", renderedType = "Address?", nullable = true),
            ),
            payload.context["requestFields"],
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
            payload.context["requestNestedTypes"],
        )
        assertEquals(
            listOf(
                DesignRenderFieldModel(name = "result", renderedType = "Result?", nullable = true),
            ),
            payload.context["responseFields"],
        )
        assertEquals(
            listOf(
                DesignRenderNestedTypeModel(
                    name = "Result",
                    fields = listOf(
                        DesignRenderFieldModel(name = "success", renderedType = "Boolean"),
                    ),
                ),
            ),
            payload.context["responseNestedTypes"],
        )
    }

    @Test
    fun `api payload planner supports multi-level nested request hierarchy`() {
        val planner = DesignApiPayloadArtifactPlanner()

        val items = planner.plan(
            config = projectConfig(modules = mapOf("adapter" to "demo-adapter")),
            model = CanonicalModel(
                apiPayloads = listOf(
                    ApiPayloadModel(
                        packageName = "video",
                        typeName = "SyncVideoPostProcessStatus",
                        description = "sync video post process status",
                        requestFields = listOf(
                            FieldModel("fileList", "List<FileItem>"),
                            FieldModel("fileList[].fileIndex", "Int"),
                            FieldModel("fileList[].variants", "List<VariantItem>"),
                            FieldModel("fileList[].variants[].quality", "String", defaultValue = ""),
                            FieldModel("fileList[].variants[].width", "Int", defaultValue = "0"),
                        ),
                    ),
                ),
            ),
        )

        val payload = items.single()
        assertEquals(
            listOf(DesignRenderFieldModel(name = "fileList", renderedType = "List<FileItem>")),
            payload.context["requestFields"],
        )
        assertEquals(
            listOf(
                DesignRenderNestedTypeModel(
                    name = "FileItem",
                    fields = listOf(
                        DesignRenderFieldModel(name = "fileIndex", renderedType = "Int"),
                        DesignRenderFieldModel(name = "variants", renderedType = "List<VariantItem>"),
                    ),
                ),
                DesignRenderNestedTypeModel(
                    name = "VariantItem",
                    fields = listOf(
                        DesignRenderFieldModel(name = "quality", renderedType = "String", defaultValue = "\"\""),
                        DesignRenderFieldModel(name = "width", renderedType = "Int", defaultValue = "0"),
                    ),
                ),
            ),
            payload.context["requestNestedTypes"],
        )
    }

    @Test
    fun `api payload planner resolves self as response root type`() {
        val planner = DesignApiPayloadArtifactPlanner()

        val items = planner.plan(
            config = projectConfig(modules = mapOf("adapter" to "demo-adapter")),
            model = CanonicalModel(
                apiPayloads = listOf(
                    ApiPayloadModel(
                        packageName = "category",
                        typeName = "GetCategoryTree",
                        description = "get category tree",
                        responseFields = listOf(
                            FieldModel("categoryId", "Long"),
                            FieldModel("children", "List<self>", nullable = true),
                        ),
                    ),
                ),
            ),
        )

        val payload = items.single()
        assertEquals(
            listOf(
                DesignRenderFieldModel(name = "categoryId", renderedType = "Long"),
                DesignRenderFieldModel(name = "children", renderedType = "List<Response>?", nullable = true),
            ),
            payload.context["responseFields"],
        )
    }

    @Test
    fun `api payload planner supports explicit nested type recursion`() {
        val planner = DesignApiPayloadArtifactPlanner()

        val items = planner.plan(
            config = projectConfig(modules = mapOf("adapter" to "demo-adapter")),
            model = CanonicalModel(
                apiPayloads = listOf(
                    ApiPayloadModel(
                        packageName = "video",
                        typeName = "RecursiveVariantPayload",
                        description = "recursive variant payload",
                        requestFields = listOf(
                            FieldModel("variants", "List<VariantItem>"),
                            FieldModel("variants[].quality", "String"),
                            FieldModel("variants[].children", "List<VariantItem>"),
                        ),
                    ),
                ),
            ),
        )

        val payload = items.single()
        assertEquals(
            listOf(
                DesignRenderNestedTypeModel(
                    name = "VariantItem",
                    fields = listOf(
                        DesignRenderFieldModel(name = "quality", renderedType = "String"),
                        DesignRenderFieldModel(name = "children", renderedType = "List<VariantItem>"),
                    ),
                ),
            ),
            payload.context["requestNestedTypes"],
        )
    }

    @Test
    fun `api payload planner keeps request and response nested item namespaces isolated`() {
        val planner = DesignApiPayloadArtifactPlanner()

        val items = planner.plan(
            config = projectConfig(modules = mapOf("adapter" to "demo-adapter")),
            model = CanonicalModel(
                apiPayloads = listOf(
                    ApiPayloadModel(
                        packageName = "message",
                        typeName = "MessageGroupPayload",
                        description = "message group payload",
                        requestFields = listOf(
                            FieldModel("list", "List<Item>"),
                            FieldModel("list[].requestValue", "String"),
                        ),
                        responseFields = listOf(
                            FieldModel("list", "List<Item>"),
                            FieldModel("list[].messageType", "Int"),
                            FieldModel("list[].count", "Int"),
                        ),
                    ),
                ),
            ),
        )

        val payload = items.single()
        assertEquals(
            listOf(
                DesignRenderNestedTypeModel(
                    name = "Item",
                    fields = listOf(DesignRenderFieldModel(name = "requestValue", renderedType = "String")),
                ),
            ),
            payload.context["requestNestedTypes"],
        )
        assertEquals(
            listOf(
                DesignRenderNestedTypeModel(
                    name = "Item",
                    fields = listOf(
                        DesignRenderFieldModel(name = "messageType", renderedType = "Int"),
                        DesignRenderFieldModel(name = "count", renderedType = "Int"),
                    ),
                ),
            ),
            payload.context["responseNestedTypes"],
        )
    }

    @Test
    fun `api payload planner does not bind request fqcn to response local item`() {
        val planner = DesignApiPayloadArtifactPlanner()

        val items = planner.plan(
            config = projectConfig(modules = mapOf("adapter" to "demo-adapter")),
            model = CanonicalModel(
                apiPayloads = listOf(
                    ApiPayloadModel(
                        packageName = "message",
                        typeName = "ExternalItemPayload",
                        description = "external item payload",
                        requestFields = listOf(
                            FieldModel("externalItem", "com.acme.shared.Item"),
                        ),
                        responseFields = listOf(
                            FieldModel("list", "List<Item>"),
                            FieldModel("list[].id", "Long"),
                        ),
                    ),
                ),
            ),
        )

        val payload = items.single()
        assertEquals(
            listOf(DesignRenderFieldModel(name = "externalItem", renderedType = "Item")),
            payload.context["requestFields"],
        )
        assertEquals(listOf("com.acme.shared.Item"), payload.context["imports"])
        assertEquals(
            listOf(
                DesignRenderNestedTypeModel(
                    name = "Item",
                    fields = listOf(DesignRenderFieldModel(name = "id", renderedType = "Long")),
                ),
            ),
            payload.context["responseNestedTypes"],
        )
    }

    @Test
    fun `api payload planner renders same namespace fqcn when local item collides`() {
        val planner = DesignApiPayloadArtifactPlanner()

        val items = planner.plan(
            config = projectConfig(modules = mapOf("adapter" to "demo-adapter")),
            model = CanonicalModel(
                apiPayloads = listOf(
                    ApiPayloadModel(
                        packageName = "message",
                        typeName = "RequestItemCollisionPayload",
                        description = "request item collision payload",
                        requestFields = listOf(
                            FieldModel("list", "List<Item>"),
                            FieldModel("list[].id", "Long"),
                            FieldModel("externalItem", "com.acme.shared.Item"),
                        ),
                    ),
                ),
            ),
        )

        val payload = items.single()
        assertEquals(
            listOf(
                DesignRenderFieldModel(name = "list", renderedType = "List<Item>"),
                DesignRenderFieldModel(name = "externalItem", renderedType = "com.acme.shared.Item"),
            ),
            payload.context["requestFields"],
        )
        assertEquals(emptyList<String>(), payload.context["imports"])
        assertEquals(
            listOf(
                DesignRenderNestedTypeModel(
                    name = "Item",
                    fields = listOf(DesignRenderFieldModel(name = "id", renderedType = "Long")),
                ),
            ),
            payload.context["requestNestedTypes"],
        )
    }

    @Test
    fun `api payload planner does not resolve request short item from response local item`() {
        val planner = DesignApiPayloadArtifactPlanner()

        val error = assertThrows(IllegalArgumentException::class.java) {
            planner.plan(
                config = projectConfig(modules = mapOf("adapter" to "demo-adapter")),
                model = CanonicalModel(
                    apiPayloads = listOf(
                        ApiPayloadModel(
                            packageName = "message",
                            typeName = "ExternalItemPayload",
                            description = "external item payload",
                            requestFields = listOf(
                                FieldModel("externalItem", "Item"),
                            ),
                            responseFields = listOf(
                                FieldModel("list", "List<Item>"),
                                FieldModel("list[].id", "Long"),
                            ),
                        ),
                    ),
                ),
            )
        }

        assertTrue(error.message.orEmpty().contains("failed to resolve type for field externalItem"))
        assertTrue(error.message.orEmpty().contains("unknown short type: Item"))
    }

    @Test
    fun `api payload planner fails when duplicate leaf path is declared`() {
        val planner = DesignApiPayloadArtifactPlanner()

        val error = assertThrows(IllegalArgumentException::class.java) {
            planner.plan(
                config = projectConfig(modules = mapOf("adapter" to "demo-adapter")),
                model = CanonicalModel(
                    apiPayloads = listOf(
                        ApiPayloadModel(
                            packageName = "account",
                            typeName = "DuplicateAddressPayload",
                            description = "duplicate address payload",
                            requestFields = listOf(
                                FieldModel("address", "Address"),
                                FieldModel("address.city", "String"),
                                FieldModel("address.city", "Int"),
                            ),
                        ),
                    ),
                ),
            )
        }

        assertTrue(error.message.orEmpty().contains("duplicate direct declarations for address.city in request namespace"))
    }

    @Test
    fun `api payload planner ignores non api payload canonical slices`() {
        val planner = DesignApiPayloadArtifactPlanner()

        val items = planner.plan(
            config = projectConfig(modules = mapOf("adapter" to "demo-adapter")),
            model = CanonicalModel(
                commands = listOf(
                    CommandModel(
                        packageName = "order.submit",
                        typeName = "SubmitOrderCmd",
                        description = "submit order",
                        variant = CommandVariant.DEFAULT,
                    ),
                ),
                validators = listOf(
                    ValidatorModel(
                        packageName = "account",
                        typeName = "SaveAccount",
                        description = "save account validator",
                        valueType = "Long",
                    ),
                ),
            ),
        )

        assertTrue(items.isEmpty())
    }

    @Test
    fun `api payload planner fails when adapter module is missing`() {
        val planner = DesignApiPayloadArtifactPlanner()

        val error = assertThrows(IllegalStateException::class.java) {
            planner.plan(
                config = projectConfig(modules = emptyMap()),
                model = CanonicalModel(
                    apiPayloads = listOf(
                        ApiPayloadModel(
                            packageName = "account",
                            typeName = "BatchSaveAccountList",
                            description = "batch save account list payload",
                        ),
                    ),
                ),
            )
        }

        assertEquals("adapter module is required", error.message)
    }

    @Test
    fun `api payload planner fails when nested request group has no compatible direct root field`() {
        val planner = DesignApiPayloadArtifactPlanner()

        val error = assertThrows(IllegalArgumentException::class.java) {
            planner.plan(
                config = projectConfig(modules = mapOf("adapter" to "demo-adapter")),
                model = CanonicalModel(
                    apiPayloads = listOf(
                        ApiPayloadModel(
                            packageName = "account",
                            typeName = "BatchSaveAccountList",
                            description = "batch save account list payload",
                            requestFields = listOf(
                                FieldModel("address.city", "String"),
                                FieldModel("address.zipCode", "String"),
                            ),
                        ),
                    ),
                ),
            )
        }

        assertEquals("missing compatible direct root field for nested type Address in request namespace", error.message)
    }

    @Test
    fun `api payload planner fails when nested request group root field type is incompatible`() {
        val planner = DesignApiPayloadArtifactPlanner()

        val error = assertThrows(IllegalArgumentException::class.java) {
            planner.plan(
                config = projectConfig(modules = mapOf("adapter" to "demo-adapter")),
                model = CanonicalModel(
                    apiPayloads = listOf(
                        ApiPayloadModel(
                            packageName = "account",
                            typeName = "BatchSaveAccountList",
                            description = "batch save account list payload",
                            requestFields = listOf(
                                FieldModel("address", "String"),
                                FieldModel("address.city", "String"),
                            ),
                        ),
                    ),
                ),
            )
        }

        assertEquals("direct root field address in request namespace must point to nested type Address", error.message)
    }

    private fun projectConfig(modules: Map<String, String>) = ProjectConfig(
        basePackage = "com.acme.demo",
        layout = ProjectLayout.MULTI_MODULE,
        modules = modules,
        sources = emptyMap(),
        generators = mapOf("design-api-payload" to GeneratorConfig(enabled = true)),
        templates = TemplateConfig("ddd-default", emptyList(), ConflictPolicy.SKIP),
    )
}

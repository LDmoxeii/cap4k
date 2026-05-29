package com.only4.cap4k.plugin.pipeline.generator.design

import com.only4.cap4k.plugin.pipeline.api.CanonicalModel
import com.only4.cap4k.plugin.pipeline.api.ConflictPolicy
import com.only4.cap4k.plugin.pipeline.api.ArtifactSelectionModel
import com.only4.cap4k.plugin.pipeline.api.DesignBlockModel
import com.only4.cap4k.plugin.pipeline.api.FieldModel
import com.only4.cap4k.plugin.pipeline.api.GeneratorConfig
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig
import com.only4.cap4k.plugin.pipeline.api.ProjectLayout
import com.only4.cap4k.plugin.pipeline.api.TemplateConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DesignApiPayloadArtifactPlannerTest {

    @Test
    fun `api payload planner plans adapter payload artifact with nested request and response hierarchy`() {
        val planner = DesignApiPayloadArtifactPlanner()
        assertEquals("api-payload", planner.id)

        val items = planner.plan(
            config = projectConfig(modules = mapOf("adapter" to "demo-adapter")),
            model = CanonicalModel(
                designBlocks = listOf(
                    apiPayloadBlock(
                        packageName = "account",
                        name = "BatchSaveAccountList",
                        description = "batch save account list payload",
                        fields = listOf(
                            FieldModel("address", "Address", nullable = true),
                            FieldModel("address.city", "String"),
                            FieldModel("address.zipCode", "String"),
                        ),
                        resultFields = listOf(
                            FieldModel("result", "Result", nullable = true),
                            FieldModel("result.success", "Boolean"),
                        ),
                    ),
                ),
            ),
        )

        val payload = items.single()
        assertEquals("api-payload", payload.generatorId)
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
                designBlocks = listOf(
                    apiPayloadBlock(
                        packageName = "video",
                        name = "SyncVideoPostProcessStatus",
                        description = "sync video post process status",
                        fields = listOf(
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
    fun `api payload planner fails when self is used as response root type`() {
        val planner = DesignApiPayloadArtifactPlanner()

        val error = assertThrows(IllegalArgumentException::class.java) {
            planner.plan(
                config = projectConfig(modules = mapOf("adapter" to "demo-adapter")),
                model = CanonicalModel(
                    designBlocks = listOf(
                        apiPayloadBlock(
                            packageName = "category",
                            name = "GetCategoryTree",
                            description = "get category tree",
                            resultFields = listOf(
                                FieldModel("categoryId", "Long"),
                                FieldModel("children", "List<self>", nullable = true),
                            ),
                        ),
                    ),
                ),
            )
        }

        assertTrue(error.message.orEmpty().contains("self"))
    }

    @Test
    fun `api payload planner supports explicit page envelope item paths`() {
        val planner = DesignApiPayloadArtifactPlanner()

        val items = planner.plan(
            config = projectConfig(modules = mapOf("adapter" to "demo-adapter")),
            model = CanonicalModel(
                designBlocks = listOf(
                    apiPayloadBlock(
                        packageName = "order",
                        name = "FindOrderPagePayload",
                        description = "find order page payload",
                        variant = "page",
                        resultFields = listOf(
                            FieldModel("page", "com.only4.cap4k.ddd.core.share.PageData<Item>"),
                            FieldModel("page.list[].orderId", "Long"),
                            FieldModel("page.list[].title", "String"),
                        ),
                    ),
                ),
            ),
        )

        val payload = items.single()
        assertEquals(true, payload.context["pageRequest"])
        assertEquals(
            listOf(DesignRenderFieldModel(name = "page", renderedType = "PageData<Item>")),
            payload.context["responseFields"],
        )
        assertEquals(
            listOf(
                DesignRenderNestedTypeModel(
                    "Item",
                    fields = listOf(
                        DesignRenderFieldModel(name = "orderId", renderedType = "Long"),
                        DesignRenderFieldModel(name = "title", renderedType = "String"),
                    ),
                ),
            ),
            payload.context["responseNestedTypes"],
        )
        assertEquals(listOf("com.only4.cap4k.ddd.core.share.PageData"), payload.context["imports"])
    }

    @Test
    fun `api payload planner rejects page data envelope on non page root field`() {
        val planner = DesignApiPayloadArtifactPlanner()

        val error = assertThrows(IllegalArgumentException::class.java) {
            planner.plan(
                config = projectConfig(modules = mapOf("adapter" to "demo-adapter")),
                model = CanonicalModel(
                    designBlocks = listOf(
                        apiPayloadBlock(
                            packageName = "order",
                            name = "FindOrderResultsPayload",
                            description = "find order results payload",
                            resultFields = listOf(
                                FieldModel("results", "com.only4.cap4k.ddd.core.share.PageData<Item>"),
                                FieldModel("results.list[].id", "Long"),
                            ),
                        ),
                    ),
                ),
            )
        }

        assertEquals(
            "PageData envelope in response namespace is only supported for root field page",
            error.message,
        )
    }

    @Test
    fun `api payload planner rejects standalone page data envelope on non page root field`() {
        val planner = DesignApiPayloadArtifactPlanner()

        val error = assertThrows(IllegalArgumentException::class.java) {
            planner.plan(
                config = projectConfig(modules = mapOf("adapter" to "demo-adapter")),
                model = CanonicalModel(
                    designBlocks = listOf(
                        apiPayloadBlock(
                            packageName = "order",
                            name = "FindOrderResultsPayload",
                            description = "find order results payload",
                            resultFields = listOf(
                                FieldModel("results", "com.only4.cap4k.ddd.core.share.PageData<Item>"),
                            ),
                        ),
                    ),
                ),
            )
        }

        assertEquals(
            "PageData envelope in response namespace is only supported for root field page",
            error.message,
        )
    }

    @Test
    fun `api payload planner rejects standalone page data root field without list item paths`() {
        val planner = DesignApiPayloadArtifactPlanner()

        val error = assertThrows(IllegalArgumentException::class.java) {
            planner.plan(
                config = projectConfig(modules = mapOf("adapter" to "demo-adapter")),
                model = CanonicalModel(
                    designBlocks = listOf(
                        apiPayloadBlock(
                            packageName = "order",
                            name = "FindOrderPagePayload",
                            description = "find order page payload",
                            resultFields = listOf(
                                FieldModel("page", "com.only4.cap4k.ddd.core.share.PageData<Item>"),
                            ),
                        ),
                    ),
                ),
            )
        }

        assertEquals(
            "PageData field page in response namespace must declare nested item fields only under list[]",
            error.message,
        )
    }

    @Test
    fun `api payload planner rejects raw standalone page data root field without list item paths`() {
        val planner = DesignApiPayloadArtifactPlanner()

        val error = assertThrows(IllegalArgumentException::class.java) {
            planner.plan(
                config = projectConfig(modules = mapOf("adapter" to "demo-adapter")),
                model = CanonicalModel(
                    designBlocks = listOf(
                        apiPayloadBlock(
                            packageName = "order",
                            name = "FindOrderPagePayload",
                            description = "find order page payload",
                            resultFields = listOf(
                                FieldModel("page", "com.only4.cap4k.ddd.core.share.PageData"),
                            ),
                        ),
                    ),
                ),
            )
        }

        assertEquals(
            "PageData field page in response namespace must declare nested item fields only under list[]",
            error.message,
        )
    }

    @Test
    fun `api payload planner rejects page data envelope in request namespace`() {
        val planner = DesignApiPayloadArtifactPlanner()

        val error = assertThrows(IllegalArgumentException::class.java) {
            planner.plan(
                config = projectConfig(modules = mapOf("adapter" to "demo-adapter")),
                model = CanonicalModel(
                    designBlocks = listOf(
                        apiPayloadBlock(
                            packageName = "order",
                            name = "FindOrderPagePayload",
                            description = "find order page payload",
                            fields = listOf(
                                FieldModel("page", "com.only4.cap4k.ddd.core.share.PageData<Item>"),
                                FieldModel("page.list[].id", "Long"),
                            ),
                        ),
                    ),
                ),
            )
        }

        assertEquals(
            "PageData envelope in request namespace is only supported in response fields",
            error.message,
        )
    }

    @Test
    fun `api payload planner rejects unsupported page data envelope children`() {
        val planner = DesignApiPayloadArtifactPlanner()

        val error = assertThrows(IllegalArgumentException::class.java) {
            planner.plan(
                config = projectConfig(modules = mapOf("adapter" to "demo-adapter")),
                model = CanonicalModel(
                    designBlocks = listOf(
                        apiPayloadBlock(
                            packageName = "order",
                            name = "FindOrderPagePayload",
                            description = "find order page payload",
                            resultFields = listOf(
                                FieldModel("page", "com.only4.cap4k.ddd.core.share.PageData<Item>"),
                                FieldModel("page.list[].id", "Long"),
                                FieldModel("page.total", "Long"),
                            ),
                        ),
                    ),
                ),
            )
        }

        assertEquals(
            "PageData field page in response namespace must declare nested item fields only under list[]",
            error.message,
        )
    }

    @Test
    fun `api payload planner supports explicit nested type recursion`() {
        val planner = DesignApiPayloadArtifactPlanner()

        val items = planner.plan(
            config = projectConfig(modules = mapOf("adapter" to "demo-adapter")),
            model = CanonicalModel(
                designBlocks = listOf(
                    apiPayloadBlock(
                        packageName = "video",
                        name = "RecursiveVariantPayload",
                        description = "recursive variant payload",
                        fields = listOf(
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
                designBlocks = listOf(
                    apiPayloadBlock(
                        packageName = "message",
                        name = "MessageGroupPayload",
                        description = "message group payload",
                        fields = listOf(
                            FieldModel("list", "List<Item>"),
                            FieldModel("list[].requestValue", "String"),
                        ),
                        resultFields = listOf(
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
                designBlocks = listOf(
                    apiPayloadBlock(
                        packageName = "message",
                        name = "ExternalItemPayload",
                        description = "external item payload",
                        fields = listOf(
                            FieldModel("externalItem", "com.acme.shared.Item"),
                        ),
                        resultFields = listOf(
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
                designBlocks = listOf(
                    apiPayloadBlock(
                        packageName = "message",
                        name = "RequestItemCollisionPayload",
                        description = "request item collision payload",
                        fields = listOf(
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
                    designBlocks = listOf(
                        apiPayloadBlock(
                            packageName = "message",
                            name = "ExternalItemPayload",
                            description = "external item payload",
                            fields = listOf(
                                FieldModel("externalItem", "Item"),
                            ),
                            resultFields = listOf(
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
                    designBlocks = listOf(
                        apiPayloadBlock(
                            packageName = "account",
                            name = "DuplicateAddressPayload",
                            description = "duplicate address payload",
                            fields = listOf(
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
                designBlocks = listOf(
                    designBlock(
                        tag = "command",
                        family = "command",
                        packageName = "order.submit",
                        name = "SubmitOrderCmd",
                        description = "submit order",
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
                    designBlocks = listOf(
                        apiPayloadBlock(
                            packageName = "account",
                            name = "BatchSaveAccountList",
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
                    designBlocks = listOf(
                        apiPayloadBlock(
                            packageName = "account",
                            name = "BatchSaveAccountList",
                            description = "batch save account list payload",
                            fields = listOf(
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
                    designBlocks = listOf(
                        apiPayloadBlock(
                            packageName = "account",
                            name = "BatchSaveAccountList",
                            description = "batch save account list payload",
                            fields = listOf(
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
        generators = mapOf("api-payload" to GeneratorConfig()),
        templates = TemplateConfig("ddd-default", emptyList(), ConflictPolicy.SKIP),
    )

    private fun apiPayloadBlock(
        packageName: String,
        name: String,
        description: String,
        variant: String = "",
        fields: List<FieldModel> = emptyList(),
        resultFields: List<FieldModel> = emptyList(),
    ) = DesignBlockModel(
        tag = "api_payload",
        packageName = packageName,
        name = name,
        description = description,
        artifacts = listOf(ArtifactSelectionModel("api-payload", variant)),
        fields = fields,
        resultFields = resultFields,
    )
}

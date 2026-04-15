package com.only4.cap4k.plugin.pipeline.generator.design

import com.only4.cap4k.plugin.pipeline.api.ApiPayloadModel
import com.only4.cap4k.plugin.pipeline.api.CanonicalModel
import com.only4.cap4k.plugin.pipeline.api.ConflictPolicy
import com.only4.cap4k.plugin.pipeline.api.FieldModel
import com.only4.cap4k.plugin.pipeline.api.GeneratorConfig
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig
import com.only4.cap4k.plugin.pipeline.api.ProjectLayout
import com.only4.cap4k.plugin.pipeline.api.RequestKind
import com.only4.cap4k.plugin.pipeline.api.RequestModel
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
        assertTrue(
            payload.outputPath.endsWith("adapter/portal/api/payload/account/BatchSaveAccountList.kt"),
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
    fun `api payload planner ignores non api payload canonical slices`() {
        val planner = DesignApiPayloadArtifactPlanner()

        val items = planner.plan(
            config = projectConfig(modules = mapOf("adapter" to "demo-adapter")),
            model = CanonicalModel(
                requests = listOf(
                    RequestModel(
                        kind = RequestKind.COMMAND,
                        packageName = "order.submit",
                        typeName = "SubmitOrderCmd",
                        description = "submit order",
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

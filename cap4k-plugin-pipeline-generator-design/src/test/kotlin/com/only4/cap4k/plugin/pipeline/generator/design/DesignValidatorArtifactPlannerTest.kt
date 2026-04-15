package com.only4.cap4k.plugin.pipeline.generator.design

import com.only4.cap4k.plugin.pipeline.api.CanonicalModel
import com.only4.cap4k.plugin.pipeline.api.ConflictPolicy
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

class DesignValidatorArtifactPlannerTest {

    @Test
    fun `plans validator artifacts into application validators path`() {
        val planner = DesignValidatorArtifactPlanner()

        val items = planner.plan(
            config = projectConfig(modules = mapOf("application" to "demo-application")),
            model = CanonicalModel(
                validators = listOf(
                    ValidatorModel(
                        packageName = "authorize",
                        typeName = "IssueToken",
                        description = "issue token validator",
                        valueType = "Long",
                    ),
                ),
            ),
        )

        val validator = items.single()
        assertEquals("design-validator", validator.generatorId)
        assertEquals("design/validator.kt.peb", validator.templateId)
        assertTrue(
            validator.outputPath.endsWith("application/validators/authorize/IssueToken.kt"),
        )
        assertEquals("com.acme.demo.application.validators.authorize", validator.context["packageName"])
        assertEquals("IssueToken", validator.context["typeName"])
        assertEquals("issue token validator", validator.context["description"])
        assertEquals("Long", validator.context["valueType"])
        assertEquals(emptyList<String>(), validator.context["imports"])
    }

    @Test
    fun `ignores non validator canonical slices`() {
        val planner = DesignValidatorArtifactPlanner()

        val items = planner.plan(
            config = projectConfig(modules = mapOf("application" to "demo-application")),
            model = CanonicalModel(
                requests = listOf(
                    RequestModel(
                        kind = RequestKind.COMMAND,
                        packageName = "order.submit",
                        typeName = "SubmitOrderCmd",
                        description = "submit order",
                    ),
                ),
            ),
        )

        assertTrue(items.isEmpty())
    }

    @Test
    fun `validator planner fails when application module is missing`() {
        val planner = DesignValidatorArtifactPlanner()

        val error = assertThrows(IllegalStateException::class.java) {
            planner.plan(
                config = projectConfig(modules = emptyMap()),
                model = CanonicalModel(
                    validators = listOf(
                        ValidatorModel(
                            packageName = "authorize",
                            typeName = "IssueToken",
                            description = "issue token validator",
                            valueType = "Long",
                        ),
                    ),
                ),
            )
        }

        assertEquals("application module is required", error.message)
    }

    private fun projectConfig(modules: Map<String, String>) = ProjectConfig(
        basePackage = "com.acme.demo",
        layout = ProjectLayout.MULTI_MODULE,
        modules = modules,
        sources = emptyMap(),
        generators = mapOf("design-validator" to GeneratorConfig(enabled = true)),
        templates = TemplateConfig("ddd-default", emptyList(), ConflictPolicy.SKIP),
    )
}

package com.only4.cap4k.plugin.pipeline.generator.design

import com.only4.cap4k.plugin.pipeline.api.CanonicalModel
import com.only4.cap4k.plugin.pipeline.api.CommandModel
import com.only4.cap4k.plugin.pipeline.api.CommandVariant
import com.only4.cap4k.plugin.pipeline.api.ConflictPolicy
import com.only4.cap4k.plugin.pipeline.api.GeneratorConfig
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig
import com.only4.cap4k.plugin.pipeline.api.ProjectLayout
import com.only4.cap4k.plugin.pipeline.api.TemplateConfig
import com.only4.cap4k.plugin.pipeline.api.ValidatorParameterModel
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
                        description = "issue */ token validator",
                        message = "token rejected ${'$'}reason",
                        targets = listOf("CLASS"),
                        valueType = "Any",
                        parameters = listOf(
                            ValidatorParameterModel(
                                name = "userIdField",
                                type = "String",
                                defaultValue = "user${'$'}id",
                            )
                        ),
                    ),
                ),
            ),
        )

        val validator = items.single()
        assertEquals("design-validator", validator.generatorId)
        assertEquals("design/validator.kt.peb", validator.templateId)
        assertEquals(
            "demo-application/src/main/kotlin/com/acme/demo/application/validators/authorize/IssueToken.kt",
            validator.outputPath,
        )
        assertEquals("com.acme.demo.application.validators.authorize", validator.context["packageName"])
        assertEquals("IssueToken", validator.context["typeName"])
        assertEquals("issue */ token validator", validator.context["description"])
        assertEquals("issue * / token validator", validator.context["descriptionCommentText"])
        assertEquals("token rejected ${'$'}reason", validator.context["message"])
        assertEquals("\"token rejected \\${'$'}reason\"", validator.context["messageLiteral"])
        assertEquals(listOf("CLASS"), validator.context["targets"])
        assertEquals("Any", validator.context["valueType"])
        val parameter = (validator.context["parameters"] as List<*>).single() as DesignValidatorParameterRenderModel
        assertEquals("userIdField", parameter.name)
        assertEquals("\"user\\${'$'}id\"", parameter.defaultValueLiteral)
        assertEquals(emptyList<String>(), validator.context["imports"])
    }

    @Test
    fun `ignores non validator canonical slices`() {
        val planner = DesignValidatorArtifactPlanner()

        val items = planner.plan(
            config = projectConfig(modules = mapOf("application" to "demo-application")),
            model = CanonicalModel(
                commands = listOf(
                    CommandModel(
                        packageName = "order.submit",
                        typeName = "SubmitOrderCmd",
                        description = "submit order",
                        variant = CommandVariant.DEFAULT,
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
                            message = "校验未通过",
                            targets = listOf("FIELD", "VALUE_PARAMETER"),
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

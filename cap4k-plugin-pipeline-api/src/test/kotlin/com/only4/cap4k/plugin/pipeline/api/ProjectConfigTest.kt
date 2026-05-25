package com.only4.cap4k.plugin.pipeline.api

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ProjectConfigTest {

    @Test
    fun `enabled ids and template conflict policy are exposed`() {
        val config = ProjectConfig(
            basePackage = "com.only4.example",
            layout = ProjectLayout.MULTI_MODULE,
            modules = mapOf(
                "api" to "sample-api",
                "core" to "sample-core",
            ),
            sources = mapOf(
                "design-json" to SourceConfig(enabled = true),
                "ksp-metadata" to SourceConfig(enabled = false),
            ),
            generators = mapOf(
                "design-command" to GeneratorConfig(enabled = true),
                "pebble" to GeneratorConfig(enabled = false),
            ),
            templates = TemplateConfig(
                preset = "default",
                overrideDirs = listOf("src/main/templates"),
                conflictPolicy = ConflictPolicy.SKIP,
                templateConflictPolicies = mapOf(
                    "aggregate/factory.kt.peb" to ConflictPolicy.OVERWRITE,
                ),
            ),
        )

        assertEquals(setOf("design-json"), config.enabledSourceIds())
        assertEquals(setOf("design-command"), config.enabledGeneratorIds())
        assertEquals("sample-api", config.modules["api"])
        assertEquals(ConflictPolicy.SKIP, config.templates.conflictPolicy)
        assertEquals(
            mapOf("aggregate/factory.kt.peb" to ConflictPolicy.OVERWRITE),
            config.templates.templateConflictPolicies,
        )
        assertEquals("domain.aggregates", config.artifactLayout.aggregate.packageRoot)
        assertEquals("flows", config.artifactLayout.flow.outputRoot)
        assertEquals("design", config.artifactLayout.drawingBoard.outputRoot)
    }

    @Test
    fun `project config stores type manifests and addon provider options`() {
        val config = ProjectConfig(
            typeRegistry = TypeRegistryConfig(
                registryFile = "design/type-registry.json",
                enumManifestFiles = listOf("design/enums.json"),
                valueObjectManifestFiles = listOf("design/value-objects.json"),
            ),
            addons = mapOf(
                "only-engine-validator" to AddonProviderConfig(
                    id = "only-engine-validator",
                    options = mapOf("manifestFile" to "validation/validators.json"),
                )
            ),
        )

        assertEquals("design/type-registry.json", config.typeRegistry.registryFile)
        assertEquals(listOf("design/enums.json"), config.typeRegistry.enumManifestFiles)
        assertEquals(listOf("design/value-objects.json"), config.typeRegistry.valueObjectManifestFiles)
        assertEquals("only-engine-validator", config.addons.getValue("only-engine-validator").id)
        assertEquals("validation/validators.json", config.addons.getValue("only-engine-validator").options["manifestFile"])
        assertEquals("design/domain-service", config.artifactLayout.designDomainService.id)
        assertEquals("design/saga-param", config.artifactLayout.designSagaParam.id)
        assertEquals("design/saga-result", config.artifactLayout.designSagaResult.id)
        assertEquals("design/saga-handler", config.artifactLayout.designSagaHandler.id)
        assertEquals("types/value-object", config.artifactLayout.valueObject.id)
    }
}

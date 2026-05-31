package com.only4.cap4k.plugin.pipeline.api

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ProjectConfigTest {

    @Test
    fun `source and generator options are stored without enabled gates`() {
        val config = ProjectConfig(
            basePackage = "com.only4.example",
            layout = ProjectLayout.MULTI_MODULE,
            modules = mapOf(
                "api" to "sample-api",
                "core" to "sample-core",
            ),
            sources = mapOf(
                "design-json" to SourceConfig(
                    options = mapOf("files" to listOf("design/design.json")),
                ),
            ),
            generators = mapOf(
                "aggregate" to GeneratorConfig(
                    options = mapOf("artifact.specification" to false),
                ),
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

        assertEquals(setOf("design-json"), config.sources.keys)
        assertEquals(listOf("design/design.json"), config.sources.getValue("design-json").options["files"])
        assertEquals(setOf("aggregate"), config.generators.keys)
        assertEquals(false, config.generators.getValue("aggregate").options["artifact.specification"])
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
        assertEquals("design/domain_service.kt.peb", config.artifactLayout.designDomainService.id)
        assertEquals("design/saga.kt.peb", config.artifactLayout.designSagaArtifact.id)
        assertEquals("domain.services", config.artifactLayout.designDomainServicePackage.packageRoot)
        assertEquals("application.sagas", config.artifactLayout.designSaga.packageRoot)
        assertEquals("types/value-object", config.artifactLayout.valueObject.id)
    }
}

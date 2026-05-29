package com.only4.cap4k.plugin.pipeline.core

import com.only4.cap4k.plugin.pipeline.api.ArtifactPlanItem
import com.only4.cap4k.plugin.pipeline.api.ArtifactAddonContext
import com.only4.cap4k.plugin.pipeline.api.ArtifactAddonProvider
import com.only4.cap4k.plugin.pipeline.api.ArtifactOutputKind
import com.only4.cap4k.plugin.pipeline.api.CanonicalModel
import com.only4.cap4k.plugin.pipeline.api.CanonicalAssemblyResult
import com.only4.cap4k.plugin.pipeline.api.ConflictPolicy
import com.only4.cap4k.plugin.pipeline.api.DesignSpecSnapshot
import com.only4.cap4k.plugin.pipeline.api.AggregateSpecialFieldDefaultsConfig
import com.only4.cap4k.plugin.pipeline.api.AddonProviderConfig
import com.only4.cap4k.plugin.pipeline.api.DbColumnSnapshot
import com.only4.cap4k.plugin.pipeline.api.DbSchemaSnapshot
import com.only4.cap4k.plugin.pipeline.api.DbTableSnapshot
import com.only4.cap4k.plugin.pipeline.api.GeneratorConfig
import com.only4.cap4k.plugin.pipeline.api.GeneratorProvider
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig
import com.only4.cap4k.plugin.pipeline.api.ProjectLayout
import com.only4.cap4k.plugin.pipeline.api.RenderedArtifact
import com.only4.cap4k.plugin.pipeline.api.SpecialFieldWritePolicy
import com.only4.cap4k.plugin.pipeline.api.SourceConfig
import com.only4.cap4k.plugin.pipeline.api.SourceProvider
import com.only4.cap4k.plugin.pipeline.api.SourceSnapshot
import com.only4.cap4k.plugin.pipeline.api.TemplateConfig
import com.only4.cap4k.plugin.pipeline.renderer.api.ArtifactRenderer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class DefaultPipelineRunnerTest {

    @Test
    fun `addon provider contributes plan item after built-in generator item`() {
        val builtInItem = ArtifactPlanItem(
            generatorId = "design-command",
            moduleRole = "app",
            templateId = "design/command.kt.peb",
            outputPath = "generated/CreateOrderCmd.kt",
            conflictPolicy = ConflictPolicy.SKIP,
        )
        val addonItem = ArtifactPlanItem(
            generatorId = "sample-addon",
            moduleRole = "adapter",
            templateId = "addons/sample-addon/sample.kt.peb",
            outputPath = "generated/SampleAddon.kt",
            conflictPolicy = ConflictPolicy.SKIP,
        )

        val result = runWithCapturedPlanItems(
            plannedItems = listOf(builtInItem),
            addonProviders = listOf(addonProvider("sample-addon", listOf(addonItem))),
            config = configuredConfig(),
        )

        assertEquals(listOf(builtInItem, addonItem), result.rendererReceivedPlanItems)
        assertEquals(listOf(builtInItem, addonItem), result.pipelineResult.planItems)
    }

    @Test
    fun `addon receives assembled canonical model and project config`() {
        val assembledModel = CanonicalModel()
        val config = configuredConfig()
        var receivedContext: ArtifactAddonContext? = null
        val addon = object : ArtifactAddonProvider {
            override val id: String = "sample-addon"

            override fun plan(context: ArtifactAddonContext): List<ArtifactPlanItem> {
                receivedContext = context
                return emptyList()
            }
        }

        runWithCapturedPlanItems(
            plannedItems = emptyList(),
            addonProviders = listOf(addon),
            config = config,
            assembledModel = assembledModel,
        )

        assertEquals(config, receivedContext?.config)
        assertEquals(assembledModel, receivedContext?.model)
        assertEquals(emptyMap<String, Any?>(), receivedContext?.options)
    }

    @Test
    fun `passes provider scoped options to matching addon`() {
        var receivedOptions: Map<String, Any?>? = null
        val addon = object : ArtifactAddonProvider {
            override val id: String = "sample-addon"

            override fun plan(context: ArtifactAddonContext): List<ArtifactPlanItem> {
                receivedOptions = context.options
                return emptyList()
            }
        }

        runWithCapturedPlanItems(
            plannedItems = emptyList(),
            addonProviders = listOf(addon),
            config = configuredConfig(
                addons = mapOf(
                    "sample-addon" to AddonProviderConfig(
                        id = "sample-addon",
                        options = mapOf("enumPackage" to "domain.enums"),
                    ),
                ),
            ),
        )

        assertEquals(mapOf("enumPackage" to "domain.enums"), receivedOptions)
    }

    @Test
    fun `fails when addon config key does not match provider id`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            runWithCapturedPlanItems(
                plannedItems = emptyList(),
                addonProviders = listOf(addonProvider("sample-addon", emptyList())),
                config = configuredConfig(
                    addons = mapOf(
                        "sample-addon" to AddonProviderConfig(
                            id = "other-addon",
                            options = mapOf("enabled" to "true"),
                        ),
                    ),
                ),
            )
        }

        assertEquals(
            "Configured addon provider key does not match provider id: sample-addon != other-addon",
            error.message,
        )
    }

    @Test
    fun `fails when addon options reference unloaded provider`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            runWithCapturedPlanItems(
                plannedItems = emptyList(),
                addonProviders = emptyList(),
                config = configuredConfig(
                    addons = mapOf(
                        "missing-addon" to AddonProviderConfig(
                            id = "missing-addon",
                            options = mapOf("enabled" to "true"),
                        ),
                    ),
                ),
            )
        }

        assertTrue(error.message?.contains("Configured addon provider is not loaded: missing-addon") == true)
    }

    @Test
    fun `rejects addon template id outside provider namespace`() {
        val addonItem = ArtifactPlanItem(
            generatorId = "sample-addon",
            moduleRole = "adapter",
            templateId = "addons/other-addon/sample.kt.peb",
            outputPath = "generated/SampleAddon.kt",
            conflictPolicy = ConflictPolicy.SKIP,
        )

        val error = assertThrows(IllegalArgumentException::class.java) {
            runWithCapturedPlanItems(
                plannedItems = emptyList(),
                addonProviders = listOf(addonProvider("sample-addon", listOf(addonItem))),
                config = configuredConfig(),
            )
        }

        assertTrue(
            error.message?.contains(
                "Addon sample-addon produced template id outside addons/sample-addon/: addons/other-addon/sample.kt.peb",
            ) == true,
        )
    }

    @Test
    fun `wraps addon provider planning exceptions`() {
        val failure = IllegalArgumentException("bad addon state")
        val addon = object : ArtifactAddonProvider {
            override val id: String = "sample-addon"

            override fun plan(context: ArtifactAddonContext): List<ArtifactPlanItem> {
                throw failure
            }
        }

        val error = assertThrows(IllegalStateException::class.java) {
            runWithCapturedPlanItems(
                plannedItems = emptyList(),
                addonProviders = listOf(addon),
                config = configuredConfig(),
            )
        }

        assertTrue(error.message?.contains("Addon provider sample-addon failed while planning artifacts") == true)
        assertEquals(failure, error.cause)
    }

    @Test
    fun `addon plan item passes through transform include and conflict policy`() {
        val includedAddonItem = ArtifactPlanItem(
            generatorId = "sample-addon",
            moduleRole = "adapter",
            templateId = "addons/sample-addon/original.kt.peb",
            outputPath = "generated/SampleAddon.kt",
            conflictPolicy = ConflictPolicy.SKIP,
        )
        val excludedAddonItem = includedAddonItem.copy(outputPath = "generated/ExcludedAddon.kt")
        val transformedIncludedItem = includedAddonItem.copy(
            templateId = "addons/sample-addon/transformed.kt.peb",
            context = mapOf("transformed" to true),
        )
        val expectedResolvedItem = transformedIncludedItem.copy(conflictPolicy = ConflictPolicy.OVERWRITE)

        val result = runWithCapturedPlanItems(
            plannedItems = emptyList(),
            addonProviders = listOf(addonProvider("sample-addon", listOf(includedAddonItem, excludedAddonItem))),
            config = configuredConfig(
                templates = TemplateConfig(
                    preset = "default",
                    overrideDirs = emptyList(),
                    conflictPolicy = ConflictPolicy.SKIP,
                    templateConflictPolicies = mapOf(
                        "addons/sample-addon/transformed.kt.peb" to ConflictPolicy.OVERWRITE,
                    ),
                ),
            ),
            transformPlanItem = {
                it.copy(
                    templateId = "addons/sample-addon/transformed.kt.peb",
                    context = it.context + ("transformed" to true),
                )
            },
            includePlanItem = { it.outputPath == "generated/SampleAddon.kt" },
        )

        assertEquals(listOf(expectedResolvedItem), result.rendererReceivedPlanItems)
        assertEquals(listOf(expectedResolvedItem), result.pipelineResult.planItems)
    }

    @Test
    fun `rejects transformed addon template id outside provider namespace`() {
        val addonItem = ArtifactPlanItem(
            generatorId = "sample-addon",
            moduleRole = "adapter",
            templateId = "addons/sample-addon/original.kt.peb",
            outputPath = "generated/SampleAddon.kt",
            conflictPolicy = ConflictPolicy.SKIP,
        )

        val error = assertThrows(IllegalArgumentException::class.java) {
            runWithCapturedPlanItems(
                plannedItems = emptyList(),
                addonProviders = listOf(addonProvider("sample-addon", listOf(addonItem))),
                config = configuredConfig(),
                transformPlanItem = {
                    it.copy(
                        generatorId = "design-command",
                        templateId = "design/command.kt.peb",
                    )
                },
            )
        }

        assertTrue(
            error.message?.contains(
                "Addon sample-addon produced template id outside addons/sample-addon/: design/command.kt.peb",
            ) == true,
        )
    }

    @Test
    fun `does not validate built-in item as addon item when transformed generator id matches addon provider id`() {
        val builtInItem = ArtifactPlanItem(
            generatorId = "design-command",
            moduleRole = "app",
            templateId = "design/command.kt.peb",
            outputPath = "generated/CreateOrderCmd.kt",
            conflictPolicy = ConflictPolicy.SKIP,
        )
        val transformedBuiltInItem = builtInItem.copy(generatorId = "sample-addon")

        val result = runWithCapturedPlanItems(
            plannedItems = listOf(builtInItem),
            addonProviders = listOf(addonProvider("sample-addon", emptyList())),
            config = configuredConfig(),
            transformPlanItem = {
                if (it.generatorId == "design-command") transformedBuiltInItem else it
            },
        )

        assertEquals(listOf(transformedBuiltInItem), result.rendererReceivedPlanItems)
        assertEquals(listOf(transformedBuiltInItem), result.pipelineResult.planItems)
    }

    @Test
    fun `duplicate addon provider ids fail fast`() {
        val first = addonProvider("duplicate-addon", emptyList())
        val second = addonProvider("duplicate-addon", emptyList())

        val error = assertThrows(IllegalArgumentException::class.java) {
            runWithCapturedPlanItems(
                plannedItems = emptyList(),
                addonProviders = listOf(first, second),
                config = configuredConfig(),
            )
        }

        assertEquals("duplicate artifact addon provider id: duplicate-addon", error.message)
    }

    @Test
    fun `addon ids are not treated as configured generator ids`() {
        val addonItem = ArtifactPlanItem(
            generatorId = "sample-addon",
            moduleRole = "adapter",
            templateId = "addons/sample-addon/sample.kt.peb",
            outputPath = "generated/SampleAddon.kt",
            conflictPolicy = ConflictPolicy.SKIP,
        )

        val result = runWithCapturedPlanItems(
            plannedItems = emptyList(),
            addonProviders = listOf(addonProvider("sample-addon", listOf(addonItem))),
            config = configuredConfig(generators = emptyMap()),
        )

        assertEquals(listOf(addonItem), result.rendererReceivedPlanItems)
        assertEquals(listOf(addonItem), result.pipelineResult.planItems)
    }

    @Test
    fun `template conflict override beats planner default for checked-in source items`() {
        val plannedItem = ArtifactPlanItem(
            generatorId = "design-command",
            moduleRole = "application",
            templateId = "design/command.kt.peb",
            outputPath = "demo-application/src/main/kotlin/com/acme/demo/application/commands/CreateOrderCmd.kt",
            conflictPolicy = ConflictPolicy.SKIP,
        )

        val result = runWithCapturedPlanItems(
            plannedItems = listOf(plannedItem),
            config = configuredConfig(
                templates = TemplateConfig(
                    preset = "default",
                    overrideDirs = emptyList(),
                    conflictPolicy = ConflictPolicy.FAIL,
                    templateConflictPolicies = mapOf(
                        "design/command.kt.peb" to ConflictPolicy.OVERWRITE,
                    ),
                ),
            ),
        )

        val expectedResolvedItem = plannedItem.copy(conflictPolicy = ConflictPolicy.OVERWRITE)

        assertEquals(listOf(expectedResolvedItem), result.rendererReceivedPlanItems)
        assertEquals(listOf(expectedResolvedItem), result.pipelineResult.planItems)
    }

    @Test
    fun `generated source items keep overwrite even when template override conflicts`() {
        val plannedItem = ArtifactPlanItem(
            generatorId = "aggregate",
            moduleRole = "domain",
            templateId = "aggregate/entity.kt.peb",
            outputPath = "demo-domain/build/generated/cap4k/main/kotlin/com/acme/demo/domain/aggregates/order/Order.kt",
            conflictPolicy = ConflictPolicy.SKIP,
            outputKind = ArtifactOutputKind.GENERATED_SOURCE,
        )

        val result = runWithCapturedPlanItems(
            plannedItems = listOf(plannedItem),
            config = configuredConfig(
                generators = mapOf("aggregate" to GeneratorConfig()),
                templates = TemplateConfig(
                    preset = "default",
                    overrideDirs = emptyList(),
                    conflictPolicy = ConflictPolicy.OVERWRITE,
                    templateConflictPolicies = mapOf(
                        "aggregate/entity.kt.peb" to ConflictPolicy.FAIL,
                    ),
                ),
            ),
        )

        val expectedResolvedItem = plannedItem.copy(conflictPolicy = ConflictPolicy.OVERWRITE)

        assertEquals(listOf(expectedResolvedItem), result.rendererReceivedPlanItems)
        assertEquals(listOf(expectedResolvedItem), result.pipelineResult.planItems)
    }

    @Test
    fun `include plan item hook observes pre resolution conflict policy`() {
        val plannedItem = ArtifactPlanItem(
            generatorId = "design-command",
            moduleRole = "application",
            templateId = "design/command.kt.peb",
            outputPath = "demo-application/src/main/kotlin/com/acme/demo/application/commands/CreateOrderCmd.kt",
            conflictPolicy = ConflictPolicy.SKIP,
        )

        val result = runWithCapturedPlanItems(
            plannedItems = listOf(plannedItem),
            config = configuredConfig(
                templates = TemplateConfig(
                    preset = "default",
                    overrideDirs = emptyList(),
                    conflictPolicy = ConflictPolicy.FAIL,
                    templateConflictPolicies = mapOf(
                        "design/command.kt.peb" to ConflictPolicy.OVERWRITE,
                    ),
                ),
            ),
            includePlanItem = { it.conflictPolicy == ConflictPolicy.SKIP },
        )

        val expectedResolvedItem = plannedItem.copy(conflictPolicy = ConflictPolicy.OVERWRITE)

        assertEquals(listOf(expectedResolvedItem), result.rendererReceivedPlanItems)
        assertEquals(listOf(expectedResolvedItem), result.pipelineResult.planItems)
    }

    @Test
    fun `run executes configured providers in order and returns expected pipeline result`() {
        val callOrder = mutableListOf<String>()
        val tempRoot = Files.createTempDirectory("pipeline-runner-test")
        val skippedPath = tempRoot.resolve("generated/Skipped.kt")
        Files.createDirectories(skippedPath.parent)
        Files.writeString(skippedPath, "existing-content")

        val configuredSourceProvider = object : SourceProvider {
            override val id: String = "design-json"

            override fun collect(config: ProjectConfig): SourceSnapshot {
                callOrder += "collect"
                return DesignSpecSnapshot(entries = emptyList())
            }
        }
        val unconfiguredSourceProvider = object : SourceProvider {
            override val id: String = "unconfigured-source"

            override fun collect(config: ProjectConfig): SourceSnapshot {
                callOrder += "collect-unconfigured"
                return DesignSpecSnapshot(id = "unconfigured-source", entries = emptyList())
            }
        }

        val expectedPlanItems = listOf(
            ArtifactPlanItem(
                generatorId = "design-command",
                moduleRole = "app",
                templateId = "template-overwrite",
                outputPath = "generated/Request.kt",
                conflictPolicy = ConflictPolicy.OVERWRITE,
            ),
            ArtifactPlanItem(
                generatorId = "design-command",
                moduleRole = "app",
                templateId = "template-skip",
                outputPath = "generated/Skipped.kt",
                conflictPolicy = ConflictPolicy.SKIP,
            ),
        )

        val configuredGeneratorProvider = object : GeneratorProvider {
            override val id: String = "design-command"

            override fun plan(config: ProjectConfig, model: CanonicalModel): List<ArtifactPlanItem> {
                callOrder += "plan"
                return expectedPlanItems
            }
        }
        val unconfiguredGeneratorProvider = object : GeneratorProvider {
            override val id: String = "unconfigured-generator"

            override fun plan(config: ProjectConfig, model: CanonicalModel): List<ArtifactPlanItem> {
                callOrder += "plan-unconfigured"
                return emptyList()
            }
        }

        val assembler = object : CanonicalAssembler {
            override fun assemble(config: ProjectConfig, snapshots: List<SourceSnapshot>): CanonicalAssemblyResult {
                callOrder += "normalize"
                return CanonicalAssemblyResult(CanonicalModel())
            }
        }

        var rendererReceivedPlanItems: List<ArtifactPlanItem> = emptyList()
        val renderedArtifacts = listOf(
            RenderedArtifact(
                outputPath = "generated/Request.kt",
                content = "class Request",
                conflictPolicy = ConflictPolicy.OVERWRITE,
            ),
            RenderedArtifact(
                outputPath = "generated/Skipped.kt",
                content = "class Skipped",
                conflictPolicy = ConflictPolicy.SKIP,
            ),
        )
        val renderer = object : ArtifactRenderer {
            override fun render(planItems: List<ArtifactPlanItem>, config: ProjectConfig): List<RenderedArtifact> {
                callOrder += "render"
                rendererReceivedPlanItems = planItems
                return renderedArtifacts
            }
        }

        val exporter = FilesystemArtifactExporter(tempRoot)

        val runner = DefaultPipelineRunner(
            sources = listOf(configuredSourceProvider, unconfiguredSourceProvider),
            generators = listOf(configuredGeneratorProvider, unconfiguredGeneratorProvider),
            assembler = assembler,
            renderer = renderer,
            exporter = exporter,
        )

        val result = runner.run(
            ProjectConfig(
                basePackage = "com.only4.cap4k.sample",
                layout = ProjectLayout.SINGLE_MODULE,
                modules = mapOf("app" to "sample-app"),
                sources = mapOf("design-json" to SourceConfig()),
                generators = mapOf("design-command" to GeneratorConfig()),
                templates = TemplateConfig(
                    preset = "default",
                    overrideDirs = emptyList(),
                    conflictPolicy = ConflictPolicy.OVERWRITE,
                ),
            )
        )

        assertEquals(listOf("collect", "normalize", "plan", "render"), callOrder)
        assertEquals(expectedPlanItems, rendererReceivedPlanItems)
        assertEquals(expectedPlanItems, result.planItems)
        assertEquals(renderedArtifacts, result.renderedArtifacts)
        assertEquals(emptyList<String>(), result.warnings)
        assertEquals(null, result.diagnostics)
        assertEquals(1, result.writtenPaths.size)
        val writtenPath = Path.of(result.writtenPaths.first())
        assertTrue(Files.exists(writtenPath))
        assertEquals("class Request", Files.readString(writtenPath))
        assertEquals("existing-content", Files.readString(skippedPath))
    }

    @Test
    fun `run fails fast when configured generator has no registered provider`() {
        val callOrder = mutableListOf<String>()
        val sourceProvider = object : SourceProvider {
            override val id: String = "design-json"

            override fun collect(config: ProjectConfig): SourceSnapshot {
                callOrder += "collect"
                return DesignSpecSnapshot(entries = emptyList())
            }
        }
        val assembler = object : CanonicalAssembler {
            override fun assemble(config: ProjectConfig, snapshots: List<SourceSnapshot>): CanonicalAssemblyResult {
                callOrder += "normalize"
                return CanonicalAssemblyResult(CanonicalModel())
            }
        }
        val renderer = object : ArtifactRenderer {
            override fun render(planItems: List<ArtifactPlanItem>, config: ProjectConfig): List<RenderedArtifact> {
                callOrder += "render"
                return emptyList()
            }
        }

        val runner = DefaultPipelineRunner(
            sources = listOf(sourceProvider),
            generators = emptyList(),
            assembler = assembler,
            renderer = renderer,
            exporter = NoopArtifactExporter(),
        )

        val error = assertThrows(IllegalArgumentException::class.java) {
            runner.run(
                ProjectConfig(
                    basePackage = "com.only4.cap4k.sample",
                    layout = ProjectLayout.SINGLE_MODULE,
                    modules = mapOf("app" to "sample-app"),
                    sources = mapOf("design-json" to SourceConfig()),
                    generators = mapOf("missing-generator" to GeneratorConfig()),
                    templates = TemplateConfig(
                        preset = "default",
                        overrideDirs = emptyList(),
                        conflictPolicy = ConflictPolicy.OVERWRITE,
                    ),
                )
            )
        }

        assertEquals("configured generators have no registered providers: missing-generator", error.message)
        assertEquals(emptyList<String>(), callOrder)
    }

    @Test
    fun `run fails when rendered artifact output path escapes export root`() {
        val runner = runnerWithSingleArtifact(
            RenderedArtifact(
                outputPath = "../outside.kt",
                content = "class Outside",
                conflictPolicy = ConflictPolicy.OVERWRITE,
            )
        )

        val error = assertThrows(IllegalArgumentException::class.java) {
            runner.run(configuredConfig())
        }

        assertTrue(error.message?.contains("outside") == true)
    }

    @Test
    fun `run fails when conflict policy is FAIL and target already exists`() {
        val tempRoot = Files.createTempDirectory("pipeline-runner-fail-test")
        val existingFile = tempRoot.resolve("generated/Existing.kt")
        Files.createDirectories(existingFile.parent)
        Files.writeString(existingFile, "existing")

        val runner = runnerWithSingleArtifact(
            RenderedArtifact(
                outputPath = "generated/Existing.kt",
                content = "class Existing",
                conflictPolicy = ConflictPolicy.FAIL,
            ),
            tempRoot = tempRoot,
        )

        assertThrows(IllegalStateException::class.java) {
            runner.run(configuredConfig())
        }
    }

    @Test
    fun `pipeline result carries aggregate diagnostics and resolved special field policies`() {
        val result = DefaultPipelineRunner(
            sources = listOf(
                object : SourceProvider {
                    override val id: String = "db"

                    override fun collect(config: ProjectConfig): SourceSnapshot =
                        DbSchemaSnapshot(
                            tables = listOf(
                                DbTableSnapshot(
                                    "audit_log",
                                    "",
                                    columns = listOf(
                                        DbColumnSnapshot("tenant_id", "BIGINT", "Long", false, null, "", true),
                                        DbColumnSnapshot("event_id", "VARCHAR", "String", false, null, "", true),
                                    ),
                                    primaryKey = listOf("tenant_id", "event_id"),
                                    uniqueConstraints = emptyList(),
                                ),
                                DbTableSnapshot(
                                    "video_post",
                                    "",
                                    columns = listOf(
                                        DbColumnSnapshot("id", "BIGINT", "Long", false, null, "", true),
                                        DbColumnSnapshot("created_by", "VARCHAR", "String", false),
                                        DbColumnSnapshot("title", "VARCHAR", "String", false),
                                    ),
                                    primaryKey = listOf("id"),
                                    uniqueConstraints = emptyList(),
                                ),
                            ),
                            discoveredTables = listOf("audit_log", "video_post"),
                            includedTables = listOf("audit_log", "video_post"),
                            excludedTables = emptyList(),
                        )
                }
            ),
            generators = listOf(
                object : GeneratorProvider {
                    override val id: String = "aggregate"
                    override fun plan(config: ProjectConfig, model: CanonicalModel): List<ArtifactPlanItem> = emptyList()
                }
            ),
            assembler = DefaultCanonicalAssembler(),
            renderer = object : ArtifactRenderer {
                override fun render(planItems: List<ArtifactPlanItem>, config: ProjectConfig): List<RenderedArtifact> = emptyList()
            },
            exporter = NoopArtifactExporter(),
        ).run(
            ProjectConfig(
                basePackage = "com.acme.demo",
                layout = ProjectLayout.MULTI_MODULE,
                modules = mapOf("domain" to "demo-domain", "adapter" to "demo-adapter"),
                sources = mapOf("db" to SourceConfig()),
                generators = mapOf(
                    "aggregate" to GeneratorConfig(
                        options = mapOf("unsupportedTablePolicy" to "SKIP"),
                    )
                ),
                templates = TemplateConfig("ddd-default", emptyList(), ConflictPolicy.SKIP),
                aggregateSpecialFieldDefaults = AggregateSpecialFieldDefaultsConfig(
                    idDefaultStrategy = "snowflake-long",
                    managedDefaultColumns = listOf("created_by"),
                ),
            )
        )

        val policy = result.aggregateSpecialFieldResolvedPolicies.single()

        assertEquals(listOf("video_post"), result.diagnostics!!.aggregate!!.supportedTables)
        assertEquals("composite_primary_key", result.diagnostics!!.aggregate!!.unsupportedTables.single().reason)
        assertEquals(1, result.aggregateSpecialFieldResolvedPolicies.size)
        assertEquals("video_post", policy.tableName)
        assertEquals(SpecialFieldWritePolicy.CREATE_ONLY, policy.id.writePolicy)
        assertEquals(listOf("id", "created_by"), policy.managedFields.map { it.columnName })
        assertEquals(listOf("id", "title"), policy.writeSurface.createAllowedFields)
        assertEquals(listOf("title"), policy.writeSurface.updateAllowedFields)
    }

    private fun runnerWithSingleArtifact(
        artifact: RenderedArtifact,
        tempRoot: Path = Files.createTempDirectory("pipeline-runner-single-artifact-test"),
    ): DefaultPipelineRunner {
        val sourceProvider = object : SourceProvider {
            override val id: String = "design-json"

            override fun collect(config: ProjectConfig): SourceSnapshot = DesignSpecSnapshot(entries = emptyList())
        }

        val generatorProvider = object : GeneratorProvider {
            override val id: String = "design-command"

            override fun plan(config: ProjectConfig, model: CanonicalModel): List<ArtifactPlanItem> {
                return listOf(
                    ArtifactPlanItem(
                        generatorId = id,
                        moduleRole = "app",
                        templateId = "template-1",
                        outputPath = artifact.outputPath,
                        conflictPolicy = artifact.conflictPolicy,
                    )
                )
            }
        }

        val assembler = object : CanonicalAssembler {
            override fun assemble(config: ProjectConfig, snapshots: List<SourceSnapshot>): CanonicalAssemblyResult =
                CanonicalAssemblyResult(CanonicalModel())
        }

        val renderer = object : ArtifactRenderer {
            override fun render(planItems: List<ArtifactPlanItem>, config: ProjectConfig): List<RenderedArtifact> = listOf(artifact)
        }

        return DefaultPipelineRunner(
            sources = listOf(sourceProvider),
            generators = listOf(generatorProvider),
            assembler = assembler,
            renderer = renderer,
            exporter = FilesystemArtifactExporter(tempRoot),
        )
    }

    private fun runWithCapturedPlanItems(
        plannedItems: List<ArtifactPlanItem>,
        config: ProjectConfig,
        addonProviders: List<ArtifactAddonProvider> = emptyList(),
        assembledModel: CanonicalModel = CanonicalModel(),
        transformPlanItem: (ArtifactPlanItem) -> ArtifactPlanItem = { it },
        includePlanItem: (ArtifactPlanItem) -> Boolean = { true },
    ): CapturedPlanItemsResult {
        val capturedPlanItems = mutableListOf<ArtifactPlanItem>()
        val runner = DefaultPipelineRunner(
            sources = listOf(
                object : SourceProvider {
                    override val id: String = "design-json"

                    override fun collect(config: ProjectConfig): SourceSnapshot =
                        DesignSpecSnapshot(entries = emptyList())
                }
            ),
            generators = listOf(
                object : GeneratorProvider {
                    override val id: String = config.generators.keys.firstOrNull() ?: "design-command"

                    override fun plan(config: ProjectConfig, model: CanonicalModel): List<ArtifactPlanItem> = plannedItems
                }
            ).filter { it.id in config.generators },
            assembler = object : CanonicalAssembler {
                override fun assemble(config: ProjectConfig, snapshots: List<SourceSnapshot>): CanonicalAssemblyResult =
                    CanonicalAssemblyResult(assembledModel)
            },
            renderer = object : ArtifactRenderer {
                override fun render(planItems: List<ArtifactPlanItem>, config: ProjectConfig): List<RenderedArtifact> {
                    capturedPlanItems += planItems
                    return emptyList()
                }
            },
            exporter = NoopArtifactExporter(),
            transformPlanItem = transformPlanItem,
            includePlanItem = includePlanItem,
            addonProviders = addonProviders,
        )

        return CapturedPlanItemsResult(
            pipelineResult = runner.run(config),
            rendererReceivedPlanItems = capturedPlanItems.toList(),
        )
    }

    private fun configuredConfig(
        generators: Map<String, GeneratorConfig> = mapOf("design-command" to GeneratorConfig()),
        templates: TemplateConfig = TemplateConfig(
            preset = "default",
            overrideDirs = emptyList(),
            conflictPolicy = ConflictPolicy.OVERWRITE,
        ),
        addons: Map<String, AddonProviderConfig> = emptyMap(),
    ): ProjectConfig {
        return ProjectConfig(
            basePackage = "com.only4.cap4k.sample",
            layout = ProjectLayout.SINGLE_MODULE,
            modules = mapOf("app" to "sample-app"),
            sources = mapOf("design-json" to SourceConfig()),
            generators = generators,
            templates = templates,
            addons = addons,
        )
    }

    private fun addonProvider(id: String, plannedItems: List<ArtifactPlanItem>): ArtifactAddonProvider {
        return object : ArtifactAddonProvider {
            override val id: String = id

            override fun plan(context: ArtifactAddonContext): List<ArtifactPlanItem> = plannedItems
        }
    }

    private data class CapturedPlanItemsResult(
        val pipelineResult: com.only4.cap4k.plugin.pipeline.api.PipelineResult,
        val rendererReceivedPlanItems: List<ArtifactPlanItem>,
    )
}

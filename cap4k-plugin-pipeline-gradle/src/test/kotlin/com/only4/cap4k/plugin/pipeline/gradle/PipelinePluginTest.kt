package com.only4.cap4k.plugin.pipeline.gradle

import com.only4.cap4k.plugin.pipeline.api.ConflictPolicy
import com.only4.cap4k.plugin.pipeline.api.GeneratorConfig
import com.only4.cap4k.plugin.pipeline.api.ArtifactAddonProvider
import com.only4.cap4k.plugin.pipeline.api.PIPELINE_EXTENSION_SPI_VERSION
import com.only4.cap4k.plugin.pipeline.api.PipelineContribution
import com.only4.cap4k.plugin.pipeline.api.PipelineContributionBinding
import com.only4.cap4k.plugin.pipeline.api.PipelineExtensionDescriptor
import com.only4.cap4k.plugin.pipeline.api.PipelineExtensionProvider
import com.only4.cap4k.plugin.pipeline.api.PipelineContributionConfig
import com.only4.cap4k.plugin.pipeline.api.PipelineExtensionConfig
import com.only4.cap4k.plugin.pipeline.api.PipelineResult
import com.only4.cap4k.plugin.pipeline.api.PipelinePublicTasks
import com.only4.cap4k.plugin.pipeline.api.PipelineTaskMutationBoundary
import com.only4.cap4k.plugin.pipeline.api.PipelineRunner
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig
import com.only4.cap4k.plugin.pipeline.api.ProjectLayout
import com.only4.cap4k.plugin.pipeline.api.SourceConfig
import com.only4.cap4k.plugin.pipeline.api.TemplateConfig
import com.only4.cap4k.plugin.pipeline.api.TypeRegistryConfig
import com.only4.cap4k.plugin.pipeline.api.TypeRegistryEntry
import com.only4.cap4k.plugin.pipeline.generator.design.DesignEndpointArtifactPlanner
import com.only4.cap4k.plugin.pipeline.generator.design.DesignIntegrationEventArtifactPlanner
import com.only4.cap4k.plugin.pipeline.generator.design.DesignIntegrationEventSubscriberArtifactPlanner
import com.only4.cap4k.plugin.pipeline.generator.types.ValueObjectArtifactPlanner
import com.only4.cap4k.plugin.pipeline.renderer.pebble.PresetTemplateResolver
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.Classpath
import org.gradle.work.DisableCachingByDefault
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import java.net.URLClassLoader
import java.nio.file.Path
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream

class PipelinePluginTest {

    @Test
    fun `plugin registers cap4k extension`() {
        val project = ProjectBuilder.builder().build()

        project.pluginManager.apply(PipelinePlugin::class.java)

        val extension = project.extensions.findByName("cap4k")

        assertNotNull(extension)
        assertInstanceOf(Cap4kExtension::class.java, extension)
        assertNull(project.extensions.findByName("cap4kPipeline"))
    }

    @Test
    fun `plugin registers exactly the public cap4k task contract`() {
        val project = ProjectBuilder.builder().build()

        project.pluginManager.apply(PipelinePlugin::class.java)

        val registered = project.tasks
            .filter { it.group == "cap4k" }
            .map { it.name }
            .sorted()

        assertEquals(PipelinePublicTasks.all.sorted(), registered)
        assertEquals(
            mapOf(
                PipelinePublicTasks.PLAN to PipelineTaskMutationBoundary.BUILD_EVIDENCE_ONLY,
                PipelinePublicTasks.GENERATE to PipelineTaskMutationBoundary.MANAGED_OUTPUTS,
                PipelinePublicTasks.GENERATE_SOURCES to PipelineTaskMutationBoundary.MANAGED_OUTPUTS,
                PipelinePublicTasks.ANALYSIS_PLAN to PipelineTaskMutationBoundary.BUILD_EVIDENCE_ONLY,
                PipelinePublicTasks.ANALYSIS_GENERATE to PipelineTaskMutationBoundary.MANAGED_OUTPUTS,
                PipelinePublicTasks.AGENT_SNAPSHOT to PipelineTaskMutationBoundary.BUILD_EVIDENCE_ONLY,
            ),
            PipelinePublicTasks.contracts.associate { it.name to it.mutationBoundary },
        )
    }

    @Test
    fun `plugin registers cap4k pipeline extension configuration without legacy alias`() {
        val project = ProjectBuilder.builder().build()

        project.pluginManager.apply(PipelinePlugin::class.java)

        val configuration = project.configurations.getByName("cap4kPipelineExtension")

        assertTrue(configuration.isCanBeResolved)
        assertFalse(configuration.isCanBeConsumed)
        assertNull(project.configurations.findByName("cap4kAddon"))
    }

    @Test
    fun `extension consuming tasks declare cap4k pipeline extension classpath inputs`() {
        val projectDir = tempProjectDir("pipeline-plugin-addon-classpath-input")
        val addonJar = projectDir.resolve("addon.jar")
        addonJar.writeText("addon")
        val project = ProjectBuilder.builder()
            .withProjectDir(projectDir)
            .build()
        project.pluginManager.apply(PipelinePlugin::class.java)
        project.dependencies.add("cap4kPipelineExtension", project.files(addonJar))

        listOf(
            "cap4kPlan" to Cap4kPlanTask::class.java,
            "cap4kGenerate" to Cap4kGenerateTask::class.java,
            "cap4kGenerateSources" to Cap4kGenerateSourcesTask::class.java,
            "cap4kAnalysisPlan" to Cap4kAnalysisPlanTask::class.java,
            "cap4kAnalysisGenerate" to Cap4kAnalysisGenerateTask::class.java,
            "cap4kAgentSnapshot" to Cap4kAgentSnapshotTask::class.java,
        ).forEach { (taskName, taskType) ->
            val getter = taskType.methods.singleOrNull {
                it.name == "getPipelineExtensionClasspath" && it.parameterCount == 0
            }
            assertNotNull(getter, "$taskName must expose pipelineExtensionClasspath")
            assertNotNull(
                getter!!.getAnnotation(Classpath::class.java),
                "$taskName pipelineExtensionClasspath must be @Classpath",
            )

            val task = project.tasks.named(taskName).get()
            val classpath = getter.invoke(task) as FileCollection
            assertEquals(setOf(addonJar), classpath.files)
        }
    }

    @Test
    fun `pipeline task types declare explicit cacheability contracts`() {
        val taskTypes = setOf(
            Cap4kPlanTask::class.java,
            Cap4kGenerateTask::class.java,
            Cap4kGenerateSourcesTask::class.java,
            Cap4kAnalysisPlanTask::class.java,
            Cap4kAnalysisGenerateTask::class.java,
            Cap4kAgentSnapshotTask::class.java,
        )

        taskTypes.forEach { taskType ->
            assertNotNull(taskType.getAnnotation(DisableCachingByDefault::class.java))
        }
    }

    @Test
    fun `source and analysis runners receive artifact contributions from pipeline extensions`() {
        val projectDir = tempProjectDir("pipeline-plugin-addon-runtime")
        val project = ProjectBuilder.builder()
            .withProjectDir(projectDir)
            .build()
        project.pluginManager.apply(PipelinePlugin::class.java)
        project.dependencies.add("cap4kPipelineExtension", project.files(addonProviderJar(projectDir)))

        val sourceRunner = buildSourceRunner(project, minimalConfig(), exportEnabled = false)
        val analysisRunner = buildAnalysisRunner(project, minimalConfig(), exportEnabled = false)

        assertEquals(
            listOf("plugin-test-addon"),
            addonProviderIds(sourceRunner),
        )
        assertEquals(
            listOf("plugin-test-addon"),
            addonProviderIds(analysisRunner),
        )
    }

    @Test
    fun `closeable pipeline runner closes resources after successful run`() {
        val closed = mutableListOf<String>()
        val runner = CloseablePipelineRunner(
            delegate = object : PipelineRunner {
                override fun run(config: ProjectConfig): PipelineResult = PipelineResult(warnings = listOf("ok"))
            },
            closeables = listOf(AutoCloseable { closed += "closed" }),
        )

        val result = runner.run(minimalConfig())

        assertEquals(listOf("ok"), result.warnings)
        assertEquals(listOf("closed"), closed)
    }

    @Test
    fun `closeable pipeline runner closes resources after failed run`() {
        val closed = mutableListOf<String>()
        val runner = CloseablePipelineRunner(
            delegate = object : PipelineRunner {
                override fun run(config: ProjectConfig): PipelineResult {
                    error("boom")
                }
            },
            closeables = listOf(AutoCloseable { closed += "closed" }),
        )

        val exception = assertThrows(IllegalStateException::class.java) {
            runner.run(minimalConfig())
        }

        assertEquals("boom", exception.message)
        assertEquals(listOf("closed"), closed)
    }

    @Test
    fun `pipeline extension runtime closes classloader when provider loading fails`() {
        val addonFile = tempProjectDir("pipeline-plugin-addon-load-failure").resolve("addon.jar")
        addonFile.writeText("addon")
        val closeFailure = IllegalStateException("close failed")
        val classLoader = CloseTrackingUrlClassLoader(closeFailure)

        val exception = assertThrows(IllegalStateException::class.java) {
            loadPipelineExtensionRuntime(
                files = setOf(addonFile),
                parent = javaClass.classLoader,
                classLoaderFactory = { _, _ -> classLoader },
                extensionLoader = { error("load failed") },
            )
        }

        assertEquals("load failed", exception.message)
        assertTrue(classLoader.closed)
        assertSame(closeFailure, exception.suppressed.single())
    }

    @Test
    fun `pipeline extension runtime exposes template classloader by addon id`() {
        val addonFile = tempProjectDir("pipeline-plugin-addon-template-classloader").resolve("addon.jar")
        addonFile.writeText("addon")
        val classLoader = CloseTrackingUrlClassLoader(closeFailure = null)
        val templateClassLoader = CloseTrackingUrlClassLoader(closeFailure = null)

        val runtime = loadPipelineExtensionRuntime(
            files = setOf(addonFile),
            parent = javaClass.classLoader,
            classLoaderFactory = { _, _ -> classLoader },
            extensionLoader = {
                PipelineExtensionLoader.validateAndBind(listOf(TestPipelinePluginExtensionProvider()))
            },
            templateClassLoaderFactory = { templateClassLoader },
        )

        assertEquals(listOf("plugin-test-addon"), runtime.artifactAddons.map { it.contribution.id })
        assertSame(templateClassLoader, runtime.addonTemplateClassLoaders["plugin-test-addon"])

        runtime.closeables.forEach { it.close() }
    }

    @Test
    fun `pipeline extension runtime rejects configured contribution that was not loaded and closes classloader`() {
        val extensionFile = tempProjectDir("pipeline-extension-missing-contribution").resolve("extension.jar")
        extensionFile.writeText("extension")
        val classLoader = CloseTrackingUrlClassLoader(closeFailure = null)
        val config = minimalConfig().copy(
            pipelineExtensions = mapOf(
                "plugin-test-extension" to PipelineExtensionConfig(
                    id = "plugin-test-extension",
                    contributions = mapOf(
                        "missing" to PipelineContributionConfig(id = "missing"),
                    ),
                ),
            ),
        )

        val exception = assertThrows(IllegalArgumentException::class.java) {
            loadPipelineExtensionRuntime(
                files = setOf(extensionFile),
                parent = javaClass.classLoader,
                config = config,
                classLoaderFactory = { _, _ -> classLoader },
                extensionLoader = {
                    PipelineExtensionLoader.validateAndBind(listOf(TestPipelinePluginExtensionProvider()))
                },
            )
        }

        assertEquals(
            "Configured pipeline contribution is not loaded: plugin-test-extension/missing",
            exception.message,
        )
        assertTrue(classLoader.closed)
    }

    @Test
    fun `addon runtime template classloader does not read resources from unrelated jars`() {
        val projectDir = tempProjectDir("pipeline-plugin-addon-template-isolation")
        val providerJar = addonProviderJar(projectDir, "provider.jar")
        val unrelatedResourceJar = jarWithResources(
            projectDir = projectDir,
            name = "unrelated-resource.jar",
            entries = mapOf("cap4k/addons/plugin-test-addon/sample.kt.peb" to "wrong addon template"),
        )
        val runtime = loadPipelineExtensionRuntime(
            files = setOf(providerJar, unrelatedResourceJar),
            parent = ArtifactAddonProvider::class.java.classLoader,
        )
        val resolver = PresetTemplateResolver(
            preset = "test-preset",
            overrideDirs = emptyList(),
            addonTemplateClassLoaders = runtime.addonTemplateClassLoaders,
        )

        val exception = assertThrows(IllegalArgumentException::class.java) {
            resolver.resolve("addons/plugin-test-addon/sample.kt.peb")
        }

        assertEquals("Addon template not found: cap4k/addons/plugin-test-addon/sample.kt.peb", exception.message)
        runtime.closeables.forEach { it.close() }
    }

    @Test
    fun `plugin wires plan and generate tasks to shared extension and config factory`() {
        val project = ProjectBuilder.builder().build()

        project.pluginManager.apply(PipelinePlugin::class.java)

        val extension = project.extensions.getByType(Cap4kExtension::class.java)
        val planTask = project.tasks.named("cap4kPlan", Cap4kPlanTask::class.java).get()
        val generateTask = project.tasks.named("cap4kGenerate", Cap4kGenerateTask::class.java).get()

        assertSame(extension, readInternalProperty(planTask, "extension"))
        assertSame(extension, readInternalProperty(generateTask, "extension"))

        val planConfigFactory = readInternalProperty(planTask, "configFactory")
        val generateConfigFactory = readInternalProperty(generateTask, "configFactory")

        assertInstanceOf(Cap4kProjectConfigFactory::class.java, planConfigFactory)
        assertSame(planConfigFactory, generateConfigFactory)
    }

    @Test
    fun `pipeline dependency inference is skipped when regular pipeline sources and generators are all disabled`() {
        val project = ProjectBuilder.builder().build()
        project.pluginManager.apply(PipelinePlugin::class.java)
        val extension = project.extensions.getByType(Cap4kExtension::class.java)

        assertFalse(shouldInferPipelineDependencies(extension))
    }

    @Test
    fun `pipeline dependency inference is enabled by input presence or configured generator blocks`() {
        val emptyProject = ProjectBuilder.builder().build()
        emptyProject.pluginManager.apply(PipelinePlugin::class.java)
        val emptyProjectExtension = emptyProject.extensions.getByType(Cap4kExtension::class.java)
        emptyProjectExtension.project {
            basePackage.set("com.acme.demo")
            domainModulePath.set("demo-domain")
        }
        assertTrue(shouldInferPipelineDependencies(emptyProjectExtension))

        val irProject = ProjectBuilder.builder().build()
        irProject.pluginManager.apply(PipelinePlugin::class.java)
        val irExtension = irProject.extensions.getByType(Cap4kExtension::class.java)
        irExtension.sources.irAnalysis.inputDirs.from(irProject.file("build/cap4k-code-analysis"))
        assertTrue(shouldInferPipelineDependencies(irExtension))

        val aggregateProjectionProject = ProjectBuilder.builder().build()
        aggregateProjectionProject.pluginManager.apply(PipelinePlugin::class.java)
        val aggregateProjectionExtension = aggregateProjectionProject.extensions.getByType(Cap4kExtension::class.java)
        aggregateProjectionExtension.generators.aggregateProjection { }
        assertTrue(shouldInferPipelineDependencies(aggregateProjectionExtension))

        val designProject = ProjectBuilder.builder().build()
        designProject.pluginManager.apply(PipelinePlugin::class.java)
        val designExtension = designProject.extensions.getByType(Cap4kExtension::class.java)
        designExtension.sources.designJson.files.from(designProject.file("design/design.json"))
        assertTrue(shouldInferPipelineDependencies(designExtension))
    }

    @Test
    fun `plugin does not register retired bootstrap tasks`() {
        val project = ProjectBuilder.builder().build()
        project.pluginManager.apply("io.github.ldmoxeii.cap4k.pipeline")

        assertNull(project.tasks.findByName("cap4kBootstrapPlan"))
        assertNull(project.tasks.findByName("cap4kBootstrap"))
    }

    @Test
    fun `plugin registers source and analysis task families`() {
        val project = ProjectBuilder.builder().build()
        project.pluginManager.apply("io.github.ldmoxeii.cap4k.pipeline")

        assertNotNull(project.tasks.findByName("cap4kPlan"))
        assertNotNull(project.tasks.findByName("cap4kGenerate"))
        assertNotNull(project.tasks.findByName("cap4kGenerateSources"))
        assertNotNull(project.tasks.findByName("cap4kAnalysisPlan"))
        assertNotNull(project.tasks.findByName("cap4kAnalysisGenerate"))
        assertNotNull(project.tasks.findByName("cap4kAgentSnapshot"))
    }

    @Test
    fun `analysis tasks use dedicated task classes`() {
        val project = ProjectBuilder.builder().build()
        project.pluginManager.apply("io.github.ldmoxeii.cap4k.pipeline")

        assertTrue(project.tasks.named("cap4kAnalysisPlan").get() is Cap4kAnalysisPlanTask)
        assertTrue(project.tasks.named("cap4kAnalysisGenerate").get() is Cap4kAnalysisGenerateTask)
    }

    @Test
    fun `generated source module roles are limited to aggregate generated source families`() {
        assertEquals(
            setOf("domain", "adapter"),
            generatedSourceModuleRoles(
                projectConfig(
                    modules = mapOf("domain" to "demo-domain", "application" to "demo-application", "adapter" to "demo-adapter"),
                    sources = mapOf("db" to SourceConfig()),
                    generators = mapOf("aggregate" to GeneratorConfig()),
                )
            )
        )
        assertEquals(
            emptySet<String>(),
            generatedSourceModuleRoles(
                projectConfig(
                    modules = mapOf("application" to "demo-application"),
                    sources = mapOf("design-json" to SourceConfig()),
                    generators = mapOf("query" to GeneratorConfig()),
                )
            )
        )
        assertEquals(
            setOf("adapter"),
            generatedSourceModuleRoles(
                projectConfig(
                    modules = mapOf("adapter" to "demo-adapter"),
                    sources = mapOf("db" to SourceConfig()),
                    generators = mapOf("aggregate-projection" to GeneratorConfig()),
                )
            )
        )
    }

    @Test
    fun `generated source module roles exclude checked in enum manifest output`() {
        assertEquals(
            emptySet<String>(),
            generatedSourceModuleRoles(
                projectConfig(
                    modules = mapOf("domain" to "demo-domain"),
                    sources = mapOf("enum-manifest" to SourceConfig()),
                    generators = emptyMap(),
                )
            )
        )
    }

    @Test
    fun `generated source module roles include domain for value object manifest`() {
        assertEquals(
            setOf("domain"),
            generatedSourceModuleRoles(
                projectConfig(
                    modules = mapOf("domain" to "demo-domain"),
                    sources = mapOf("value-object-manifest" to SourceConfig()),
                    generators = emptyMap(),
                )
            )
        )
    }

    @Test
    fun `generated source task config keeps only generated source generation inputs`() {
        val config = projectConfig(
            sources = mapOf(
                "db" to SourceConfig(),
                "enum-manifest" to SourceConfig(),
                "value-object-manifest" to SourceConfig(),
                "design-json" to SourceConfig(),
                "ir-analysis" to SourceConfig(),
            ),
            generators = mapOf(
                "aggregate" to GeneratorConfig(),
                "aggregate-projection" to GeneratorConfig(),
                "types-value-object" to GeneratorConfig(),
                "query" to GeneratorConfig(),
                "query-handler" to GeneratorConfig(),
                "integration-event" to GeneratorConfig(),
                "integration-subscriber" to GeneratorConfig(),
                "drawing-board" to GeneratorConfig(),
                "flow" to GeneratorConfig(),
            ),
        )

        val generatedConfig = generatedSourceTaskConfig(config)

        assertEquals(setOf("db", "enum-manifest", "value-object-manifest"), generatedConfig.sources.keys)
        assertEquals(setOf("aggregate", "aggregate-projection", "types-value-object"), generatedConfig.generators.keys)
    }

    @Test
    fun `analysis task config follows built in analysis providers`() {
        val expectedSources = builtInAnalysisSourceProviders().mapTo(linkedSetOf()) { it.id }
        val expectedGenerators = builtInAnalysisGeneratorProviders().mapTo(linkedSetOf()) { it.id }
        val config = projectConfig(
            sources = (expectedSources + "design-json").associateWith { SourceConfig() },
            generators = (expectedGenerators + "query").associateWith { GeneratorConfig() },
        )

        val analysisConfig = analysisTaskConfig(config)

        assertEquals(expectedSources, analysisConfig.sources.keys)
        assertEquals(expectedGenerators, analysisConfig.generators.keys)
    }

    @Test
    fun `generated source cleanup removes only cap4k generated root`() {
        val rootProjectDir = tempProjectDir("pipeline-plugin-generated-source-cleanup-root")
        val rootProject = ProjectBuilder.builder()
            .withProjectDir(rootProjectDir)
            .build()
        ProjectBuilder.builder()
            .withName("demo-domain")
            .withParent(rootProject)
            .withProjectDir(rootProjectDir.resolve("demo-domain"))
            .build()
        val config = projectConfig(
            modules = mapOf("domain" to "demo-domain"),
            sources = mapOf("value-object-manifest" to SourceConfig()),
            generators = emptyMap(),
        )
        val generatedRoot = rootProjectDir.resolve("demo-domain/build/generated/cap4k/main/kotlin")
        val staleConverter = generatedRoot.resolve("com/acme/MoneyJsonAttributeConverter.kt")
        val sibling = rootProjectDir.resolve("demo-domain/build/keep.txt")
        staleConverter.parentFile.mkdirs()
        staleConverter.writeText("stale")
        sibling.parentFile.mkdirs()
        sibling.writeText("keep")

        cleanGeneratedSourceOutputDirectories(rootProject, config)

        assertFalse(generatedRoot.exists())
        assertTrue(sibling.exists())

        ensureGeneratedSourceOutputDirectories(rootProject, config)
        assertTrue(generatedRoot.isDirectory)
    }

    @Test
    fun `generated source cleanup remembers previously managed domain root after source removal`() {
        val rootProjectDir = tempProjectDir("pipeline-plugin-generated-source-managed-history")
        val rootProject = ProjectBuilder.builder()
            .withProjectDir(rootProjectDir)
            .build()
        ProjectBuilder.builder()
            .withName("demo-domain")
            .withParent(rootProject)
            .withProjectDir(rootProjectDir.resolve("demo-domain"))
            .build()
        val enabledConfig = projectConfig(
            modules = mapOf("domain" to "demo-domain"),
            sources = mapOf("value-object-manifest" to SourceConfig()),
            generators = emptyMap(),
        )
        val generatedRoot = rootProjectDir.resolve("demo-domain/build/generated/cap4k/main/kotlin")
        val staleConverter = generatedRoot.resolve("com/acme/MoneyJsonAttributeConverter.kt")
        val sibling = rootProjectDir.resolve("demo-domain/build/keep.txt")
        staleConverter.parentFile.mkdirs()
        staleConverter.writeText("stale")
        sibling.parentFile.mkdirs()
        sibling.writeText("keep")
        recordManagedGeneratedSourceOutputDirectories(rootProject, enabledConfig)

        val removedConfig = projectConfig(
            modules = mapOf("domain" to "demo-domain"),
            sources = emptyMap(),
            generators = emptyMap(),
        )
        assertEquals(
            setOf(generatedRoot.canonicalFile),
            generatedSourceManagedOutputDirectories(rootProject, removedConfig).map { it.canonicalFile }.toSet(),
        )

        cleanGeneratedSourceOutputDirectories(rootProject, removedConfig)
        recordManagedGeneratedSourceOutputDirectories(rootProject, removedConfig)

        assertFalse(generatedRoot.exists())
        assertTrue(sibling.exists())
        assertEquals(emptyList<File>(), generatedSourceManagedOutputDirectories(rootProject, removedConfig))
    }

    @Test
    fun `source task config keeps checked in source generation inputs`() {
        val config = projectConfig(
            sources = mapOf(
                "design-json" to SourceConfig(),
                "value-object-manifest" to SourceConfig(),
                "ir-analysis" to SourceConfig(),
            ),
            generators = mapOf(
                "endpoint" to GeneratorConfig(),
                "integration-event" to GeneratorConfig(),
                "integration-subscriber" to GeneratorConfig(),
                "types-value-object" to GeneratorConfig(),
                "drawing-board" to GeneratorConfig(),
                "flow" to GeneratorConfig(),
            ),
        )

        val sourceConfig = sourceTaskConfig(config)

        assertEquals(setOf("design-json", "value-object-manifest"), sourceConfig.sources.keys)
        assertEquals(
            setOf("endpoint", "integration-event", "integration-subscriber", "types-value-object"),
            sourceConfig.generators.keys,
        )
    }

    @Test
    fun `source runner includes design integration event planners`() {
        val project = ProjectBuilder.builder().build()

        val runner = buildSourceRunner(project, minimalConfig(), exportEnabled = false)

        assertTrue(generatorProviderTypes(runner).contains(DesignEndpointArtifactPlanner::class.java))
        assertTrue(generatorProviderTypes(runner).contains(DesignIntegrationEventArtifactPlanner::class.java))
        assertTrue(generatorProviderTypes(runner).contains(DesignIntegrationEventSubscriberArtifactPlanner::class.java))
        assertTrue(generatorProviderTypes(runner).contains(ValueObjectArtifactPlanner::class.java))
    }

    @Test
    fun `generated kotlin source root is module local`() {
        val config = projectConfig(
            modules = mapOf("domain" to "demo-domain"),
            sources = mapOf("db" to SourceConfig()),
            generators = mapOf("aggregate" to GeneratorConfig()),
        )

        assertEquals(
            "demo-domain/build/generated/cap4k/main/kotlin",
            generatedKotlinSourceRoot(config, "domain"),
        )
    }

    @Test
    fun `cap4kGenerateSources declares generated source output directories`() {
        val rootProjectDir = tempProjectDir("pipeline-plugin-generated-source-outputs")
        val rootProject = ProjectBuilder.builder()
            .withProjectDir(rootProjectDir)
            .build()
        val domainProject = ProjectBuilder.builder()
            .withName("demo-domain")
            .withParent(rootProject)
            .withProjectDir(rootProjectDir.resolve("demo-domain"))
            .build()
        ProjectBuilder.builder()
            .withName("demo-application")
            .withParent(rootProject)
            .withProjectDir(rootProjectDir.resolve("demo-application"))
            .build()
        val adapterProject = ProjectBuilder.builder()
            .withName("demo-adapter")
            .withParent(rootProject)
            .withProjectDir(rootProjectDir.resolve("demo-adapter"))
            .build()
        rootProject.pluginManager.apply(PipelinePlugin::class.java)
        configureValidAggregateGeneration(rootProject.extensions.getByType(Cap4kExtension::class.java))

        val task = rootProject.tasks.named("cap4kGenerateSources", Cap4kGenerateSourcesTask::class.java).get()

        assertEquals(
            setOf(
                domainProject.layout.buildDirectory.dir("generated/cap4k/main/kotlin").get().asFile.canonicalFile,
                adapterProject.layout.buildDirectory.dir("generated/cap4k/main/kotlin").get().asFile.canonicalFile,
            ),
            task.outputs.files.files.map { it.canonicalFile }.toSet(),
        )
    }

    @Test
    fun `cap4kGenerateSources declares adapter output directory when only aggregate projection is enabled`() {
        val rootProjectDir = tempProjectDir("pipeline-plugin-aggregate-projection-generated-source-outputs")
        val rootProject = ProjectBuilder.builder()
            .withProjectDir(rootProjectDir)
            .build()
        val adapterProject = ProjectBuilder.builder()
            .withName("demo-adapter")
            .withParent(rootProject)
            .withProjectDir(rootProjectDir.resolve("demo-adapter"))
            .build()
        rootProject.pluginManager.apply(PipelinePlugin::class.java)
        val extension = rootProject.extensions.getByType(Cap4kExtension::class.java)
        configureValidAggregateProjectionGeneration(extension)

        val task = rootProject.tasks.named("cap4kGenerateSources", Cap4kGenerateSourcesTask::class.java).get()

        assertEquals(
            setOf(adapterProject.layout.buildDirectory.dir("generated/cap4k/main/kotlin").get().asFile.canonicalFile),
            task.outputs.files.files.map { it.canonicalFile }.toSet(),
        )
    }

    @Test
    fun `cap4kGenerateSources declares bounded file inputs`() {
        val rootProjectDir = tempProjectDir("pipeline-plugin-generated-source-inputs")
        val enumManifest = rootProjectDir.resolve("enums.json").apply { writeText("[]") }
        val typeRegistry = rootProjectDir.resolve("types.json").apply { writeText("{}") }
        val schemaFile = rootProjectDir.resolve("schema.sql").apply { writeText("create table demo(id bigint);") }
        val templateOverride = rootProjectDir.resolve("codegen/templates").apply { mkdirs() }
        val templateFile = templateOverride.resolve("aggregate/entity.kt.peb").apply {
            parentFile.mkdirs()
            writeText("template")
        }
        val rootProject = ProjectBuilder.builder()
            .withProjectDir(rootProjectDir)
            .build()
        ProjectBuilder.builder()
            .withName("demo-domain")
            .withParent(rootProject)
            .withProjectDir(rootProjectDir.resolve("demo-domain"))
            .build()
        ProjectBuilder.builder()
            .withName("demo-application")
            .withParent(rootProject)
            .withProjectDir(rootProjectDir.resolve("demo-application"))
            .build()
        ProjectBuilder.builder()
            .withName("demo-adapter")
            .withParent(rootProject)
            .withProjectDir(rootProjectDir.resolve("demo-adapter"))
            .build()
        rootProject.pluginManager.apply(PipelinePlugin::class.java)
        val extension = rootProject.extensions.getByType(Cap4kExtension::class.java)
        configureValidAggregateGeneration(extension)
        extension.sources.db.url.set(
            "jdbc:h2:file:./build/h2/demo;MODE=MySQL;INIT=RUNSCRIPT FROM '${schemaFile.absolutePath.replace("\\", "/")}'"
        )
        extension.types.registryFile.set(typeRegistry.name)
        extension.types.enumManifest.files.from(enumManifest)
        extension.templates.overrideDirs.from(templateOverride)

        val task = rootProject.tasks.named("cap4kGenerateSources", Cap4kGenerateSourcesTask::class.java).get()
        val inputFiles = task.inputs.files.files.map { it.canonicalFile }.toSet()

        assertTrue(inputFiles.contains(enumManifest.canonicalFile))
        assertTrue(inputFiles.contains(typeRegistry.canonicalFile))
        assertTrue(inputFiles.contains(schemaFile.canonicalFile))
        assertTrue(inputFiles.contains(templateFile.canonicalFile))
        assertFalse(inputFiles.contains(rootProjectDir.canonicalFile))
    }

    @Test
    fun `generated source task tracks value object manifest files from source options`() {
        val rootProjectDir = tempProjectDir("pipeline-plugin-generated-source-value-object-input")
        val valueObjectManifest = rootProjectDir.resolve("custom-value-objects.json").apply {
            writeText("[]")
        }
        val rootProject = ProjectBuilder.builder()
            .withProjectDir(rootProjectDir)
            .build()
        rootProject.pluginManager.apply(PipelinePlugin::class.java)
        val extension = rootProject.extensions.getByType(Cap4kExtension::class.java)
        val config = projectConfig(
            modules = emptyMap(),
            sources = mapOf(
                "value-object-manifest" to SourceConfig(
                    options = mapOf("files" to listOf(valueObjectManifest.absolutePath))
                )
            ),
            generators = emptyMap(),
        )

        val inputFiles = generatedSourceTaskInputFiles(rootProject, extension, config)
            .files
            .map { it.canonicalFile }
            .toSet()
        val snapshot = generatedSourceTaskInputSnapshot(rootProject, config)

        assertTrue(inputFiles.contains(valueObjectManifest.canonicalFile))
        assertTrue(snapshot.contains("valueObjectManifest"))
        assertTrue(snapshot.contains(valueObjectManifest.name))
    }

    @Test
    fun `generated source input snapshot hashes db password`() {
        val rootProjectDir = tempProjectDir("pipeline-plugin-generated-source-snapshot")
        val rootProject = ProjectBuilder.builder()
            .withProjectDir(rootProjectDir)
            .build()
        ProjectBuilder.builder()
            .withName("demo-domain")
            .withParent(rootProject)
            .withProjectDir(rootProjectDir.resolve("demo-domain"))
            .build()
        ProjectBuilder.builder()
            .withName("demo-application")
            .withParent(rootProject)
            .withProjectDir(rootProjectDir.resolve("demo-application"))
            .build()
        ProjectBuilder.builder()
            .withName("demo-adapter")
            .withParent(rootProject)
            .withProjectDir(rootProjectDir.resolve("demo-adapter"))
            .build()
        val config = projectConfig(
            modules = mapOf(
                "domain" to "demo-domain",
                "application" to "demo-application",
                "adapter" to "demo-adapter",
            ),
            sources = mapOf(
                "db" to SourceConfig(
                    options = mapOf(
                        "url" to "jdbc:mysql://localhost:3306/demo",
                        "username" to "cap4k",
                        "password" to "secret",
                        "schema" to "public",
                        "includeTables" to listOf("video_post"),
                        "excludeTables" to listOf("audit_log"),
                    ),
                ),
                "enum-manifest" to SourceConfig(
                    options = mapOf("files" to listOf("enums.json")),
                ),
            ),
            generators = mapOf(
                "aggregate" to GeneratorConfig(
                    options = mapOf(
                        "unsupportedTablePolicy" to "FAIL",
                    ),
                ),
                "aggregate-projection" to GeneratorConfig(),
            ),
        ).copy(
            typeRegistry = TypeRegistryConfig(
                entries = mapOf("Money" to TypeRegistryEntry("com.acme.Money")),
            ),
        )

        val snapshot = generatedSourceTaskInputSnapshot(rootProject, config)

        assertFalse(snapshot.contains("secret"))
        assertTrue(snapshot.contains("passwordHash"))
        assertTrue(snapshot.contains("jdbc:mysql://localhost:3306/demo"))
        assertTrue(snapshot.contains("cap4k"))
        assertTrue(snapshot.contains("video_post"))
        assertTrue(snapshot.contains("com.acme.Money"))
        assertTrue(snapshot.contains("aggregateProjection"))
        assertTrue(snapshot.contains("demo-domain/build/generated/cap4k/main/kotlin"))
        assertFalse(snapshot.contains("\"enabled\""))
    }

    @Test
    fun `generated source task detects live db without tracked schema input`() {
        val rootProject = ProjectBuilder.builder()
            .withProjectDir(tempProjectDir("pipeline-plugin-generated-source-live-db"))
            .build()
        val config = projectConfig(
            sources = mapOf(
                "db" to SourceConfig(
                    options = mapOf("url" to "jdbc:mysql://localhost:3306/demo"),
                ),
            ),
            generators = mapOf("aggregate" to GeneratorConfig()),
        )

        assertTrue(generatedSourceTaskHasUntrackedLiveDbInput(rootProject, config))
    }

    @Test
    fun `generated source task treats db runscript as tracked input`() {
        val rootProjectDir = tempProjectDir("pipeline-plugin-generated-source-script-db")
        val schemaFile = rootProjectDir.resolve("schema.sql").apply { writeText("create table demo(id bigint);") }
        val rootProject = ProjectBuilder.builder()
            .withProjectDir(rootProjectDir)
            .build()
        val config = projectConfig(
            sources = mapOf(
                "db" to SourceConfig(
                    options = mapOf(
                        "url" to "jdbc:h2:file:./build/h2/demo;INIT=RUNSCRIPT FROM '${schemaFile.absolutePath.replace("\\", "/")}'"
                    ),
                ),
            ),
            generators = mapOf("aggregate" to GeneratorConfig()),
        )

        assertFalse(generatedSourceTaskHasUntrackedLiveDbInput(rootProject, config))
    }

    @Test
    fun `flow with ir analysis depends on relevant compile task only`() {
        val rootProjectDir = tempProjectDir("pipeline-plugin-flow-root")
        val rootProject = ProjectBuilder.builder()
            .withProjectDir(rootProjectDir)
            .build()
        val analysisProject = ProjectBuilder.builder()
            .withName("analysis")
            .withParent(rootProject)
            .withProjectDir(rootProjectDir.resolve("analysis"))
            .build()
        analysisProject.tasks.register("compileKotlin")
        rootProject.tasks.register("compileKotlin")

        val dependencies = inferDependencies(
            rootProject,
            projectConfig(
                sources = mapOf(
                    "ir-analysis" to SourceConfig(
                        options = mapOf(
                            "inputDirs" to listOf(
                                analysisProject.layout.buildDirectory.dir("cap4k-code-analysis").get().asFile.absolutePath
                            )
                        ),
                    )
                ),
                generators = mapOf("flow" to GeneratorConfig()),
            )
        )

        assertEquals(listOf(":analysis:compileKotlin"), dependencies.map { it.path })
    }

    @Test
    fun `analysis tasks with ir analysis depend on relevant compile task only`() {
        val rootProjectDir = tempProjectDir("pipeline-plugin-analysis-flow-root")
        val rootProject = ProjectBuilder.builder()
            .withProjectDir(rootProjectDir)
            .build()
        val analysisProject = ProjectBuilder.builder()
            .withName("analysis")
            .withParent(rootProject)
            .withProjectDir(rootProjectDir.resolve("analysis"))
            .build()
        analysisProject.tasks.register("compileKotlin")
        rootProject.tasks.register("compileKotlin")

        val dependencies = inferAnalysisDependencies(
            rootProject,
            projectConfig(
                sources = mapOf(
                    "ir-analysis" to SourceConfig(
                        options = mapOf(
                            "inputDirs" to listOf(
                                analysisProject.layout.buildDirectory.dir("cap4k-code-analysis").get().asFile.absolutePath
                            )
                        ),
                    )
                ),
                generators = mapOf("flow" to GeneratorConfig()),
            )
        )

        assertEquals(listOf(":analysis:compileKotlin"), dependencies.map { it.path })
    }

    @Test
    fun `drawing board with ir analysis depends on relevant compile task only`() {
        val projectDir = tempProjectDir("pipeline-plugin-drawing-board")
        val project = ProjectBuilder.builder()
            .withProjectDir(projectDir)
            .build()
        project.tasks.register("compileKotlin")

        val dependencies = inferDependencies(
            project,
            projectConfig(
                sources = mapOf(
                    "ir-analysis" to SourceConfig(
                        options = mapOf(
                            "inputDirs" to listOf(project.layout.buildDirectory.dir("cap4k-code-analysis").get().asFile.absolutePath)
                        ),
                    )
                ),
                generators = mapOf("drawing-board" to GeneratorConfig()),
            )
        )

        assertEquals(listOf(":compileKotlin"), dependencies.map { it.path })
    }

    @Test
    fun `aggregate with db source adds no compile time dependency`() {
        val projectDir = tempProjectDir("pipeline-plugin-aggregate")
        val project = ProjectBuilder.builder()
            .withProjectDir(projectDir)
            .build()
        project.tasks.register("compileKotlin")

        val dependencies = inferDependencies(
            project,
            projectConfig(
                sources = mapOf("db" to SourceConfig()),
                generators = mapOf("aggregate" to GeneratorConfig()),
            )
        )

        assertEquals(emptyList<String>(), dependencies.map { it.path })
    }

    @Test
    fun `analysis metadata is wired only as compileOnly into every generated business module`() {
        val rootProjectDir = tempProjectDir("pipeline-plugin-analysis-metadata-compile-only-root")
        val rootProject = ProjectBuilder.builder()
            .withProjectDir(rootProjectDir)
            .build()
        val modules = listOf("demo-domain", "demo-application", "demo-adapter").associateWith { name ->
            ProjectBuilder.builder()
                .withName(name)
                .withParent(rootProject)
                .withProjectDir(rootProjectDir.resolve(name))
                .build()
                .also { module ->
                    module.pluginManager.apply("java")
                }
        }
        val config = projectConfig(
            modules = mapOf(
                "domain" to "demo-domain",
                "application" to "demo-application",
                "adapter" to "demo-adapter",
            ),
            sources = mapOf("design-json" to SourceConfig()),
            generators = emptyMap(),
        )

        ensureAnalysisMetadataCompileOnlyDependencies(rootProject, config)
        ensureAnalysisMetadataCompileOnlyDependencies(rootProject, config)

        modules.values.forEach { module ->
            val metadataDependencies = module.configurations.getByName("compileOnly").dependencies
                .filter { dependency ->
                    dependency.group == "io.github.ldmoxeii" && dependency.name == "cap4k-analysis-metadata"
                }
            assertEquals(1, metadataDependencies.size)
            assertEquals("development", metadataDependencies.single().version)
            assertTrue(module.configurations.getByName("implementation").dependencies.isEmpty())
            assertTrue(
                module.configurations.getByName("runtimeClasspath").allDependencies.none { dependency ->
                    dependency.group == "io.github.ldmoxeii" && dependency.name == "cap4k-analysis-metadata"
                }
            )
        }
    }

    @Test
    fun `explicit analysis metadata compileOnly dependency is not duplicated`() {
        val rootProjectDir = tempProjectDir("pipeline-plugin-explicit-analysis-metadata-root")
        val rootProject = ProjectBuilder.builder()
            .withProjectDir(rootProjectDir)
            .build()
        val application = ProjectBuilder.builder()
            .withName("application")
            .withParent(rootProject)
            .withProjectDir(rootProjectDir.resolve("application"))
            .build()
            .also { module ->
                module.pluginManager.apply("java")
                module.dependencies.add(
                    "compileOnly",
                    "io.github.ldmoxeii:cap4k-analysis-metadata:999.0.0-explicit",
                )
            }
        val config = projectConfig(
            modules = mapOf("application" to "application"),
            sources = mapOf("design-json" to SourceConfig()),
            generators = emptyMap(),
        )

        ensureAnalysisMetadataCompileOnlyDependencies(rootProject, config)

        val dependencies = application.configurations.getByName("compileOnly").dependencies
            .filter { dependency ->
                dependency.group == "io.github.ldmoxeii" && dependency.name == "cap4k-analysis-metadata"
            }
        assertEquals(1, dependencies.size)
        assertEquals("999.0.0-explicit", dependencies.single().version)
    }

    @Test
    fun `analysis metadata module roles follow metadata bearing generator surfaces`() {
        assertEquals(
            setOf("domain", "adapter"),
            analysisMetadataModuleRoles(
                projectConfig(
                    modules = mapOf("domain" to "domain", "adapter" to "adapter", "application" to "application"),
                    sources = mapOf("db" to SourceConfig()),
                    generators = mapOf("aggregate" to GeneratorConfig()),
                )
            )
        )
        assertEquals(
            setOf("domain"),
            analysisMetadataModuleRoles(
                projectConfig(
                    modules = mapOf("domain" to "domain"),
                    sources = mapOf("enum-manifest" to SourceConfig(), "value-object-manifest" to SourceConfig()),
                    generators = emptyMap(),
                )
            )
        )
    }

    @Test
    fun `aggregate generation wires jakarta persistence api into resolved domain module`() {
        val rootProjectDir = tempProjectDir("pipeline-plugin-aggregate-domain-dependency-root")
        val rootProject = ProjectBuilder.builder()
            .withProjectDir(rootProjectDir)
            .build()
        val domainProject = ProjectBuilder.builder()
            .withName("demo-domain")
            .withParent(rootProject)
            .withProjectDir(rootProjectDir.resolve("demo-domain"))
            .build()
        domainProject.configurations.create("implementation")

        ensureAggregateDomainJpaDependency(
            rootProject,
            projectConfig(
                modules = mapOf("domain" to "demo-domain"),
                sources = mapOf("db" to SourceConfig()),
                generators = mapOf("aggregate" to GeneratorConfig()),
            )
        )

        val implementationDependencies = domainProject.configurations.getByName("implementation").dependencies
        assertTrue(
            implementationDependencies.any { dependency ->
                dependency.group == "jakarta.persistence" && dependency.name == "jakarta.persistence-api"
            }
        )
    }

    @Test
    fun `aggregate generation wires jackson databind into resolved domain module without duplicates`() {
        val rootProjectDir = tempProjectDir("pipeline-plugin-aggregate-domain-jackson-databind-dependency-root")
        val rootProject = ProjectBuilder.builder()
            .withProjectDir(rootProjectDir)
            .build()
        val domainProject = ProjectBuilder.builder()
            .withName("demo-domain")
            .withParent(rootProject)
            .withProjectDir(rootProjectDir.resolve("demo-domain"))
            .build()
        domainProject.configurations.create("implementation")
        val config = projectConfig(
            modules = mapOf("domain" to "demo-domain"),
            sources = mapOf("db" to SourceConfig()),
            generators = mapOf("aggregate" to GeneratorConfig()),
        )

        fun jacksonDatabindDependencyCount(): Int =
            domainProject.configurations.getByName("implementation").dependencies.count { dependency ->
                dependency.group == "com.fasterxml.jackson.core" && dependency.name == "jackson-databind"
            }

        ensureAggregateDomainJpaDependency(rootProject, config)
        assertEquals(1, jacksonDatabindDependencyCount())

        ensureAggregateDomainJpaDependency(rootProject, config)
        assertEquals(1, jacksonDatabindDependencyCount())
    }

    @Test
    fun `value object generation wires json converter dependencies into resolved domain module`() {
        val rootProjectDir = tempProjectDir("pipeline-plugin-value-object-domain-dependency-root")
        val manifest = rootProjectDir.resolve("value-objects.json").apply {
            writeText(
                """
                [
                  {
                    "name": "Money",
                    "package": "com.acme.demo.domain.shared.values",
                    "persistence": { "kind": "json" }
                  }
                ]
                """.trimIndent()
            )
        }
        val rootProject = ProjectBuilder.builder()
            .withProjectDir(rootProjectDir)
            .build()
        val domainProject = ProjectBuilder.builder()
            .withName("demo-domain")
            .withParent(rootProject)
            .withProjectDir(rootProjectDir.resolve("demo-domain"))
            .build()
        domainProject.configurations.create("implementation")

        ensureValueObjectDomainDependencies(
            rootProject,
            projectConfig(
                modules = mapOf("domain" to "demo-domain"),
                sources = mapOf(
                    "value-object-manifest" to SourceConfig(
                        options = mapOf("files" to listOf(manifest.absolutePath))
                    )
                ),
                generators = emptyMap(),
            )
        )

        val implementationDependencies = domainProject.configurations.getByName("implementation").dependencies
        assertTrue(
            implementationDependencies.any { dependency ->
                dependency.group == "jakarta.persistence" && dependency.name == "jakarta.persistence-api"
            }
        )
        assertTrue(
            implementationDependencies.any { dependency ->
                dependency.group == "com.fasterxml.jackson.core" && dependency.name == "jackson-databind"
            }
        )
        assertTrue(
            implementationDependencies.any { dependency ->
                dependency.group == "com.fasterxml.jackson.module" && dependency.name == "jackson-module-kotlin"
            }
        )
    }

    @Test
    fun `value object generation without persistence adds no json converter dependencies`() {
        val rootProjectDir = tempProjectDir("pipeline-plugin-value-object-domain-no-persistence-dependency-root")
        val manifest = rootProjectDir.resolve("value-objects.json").apply {
            writeText(
                """
                [
                  {
                    "name": "Money",
                    "package": "com.acme.demo.domain.shared.values",
                    "fields": [
                      { "name": "amount", "type": "Long" }
                    ]
                  }
                ]
                """.trimIndent()
            )
        }
        val rootProject = ProjectBuilder.builder()
            .withProjectDir(rootProjectDir)
            .build()
        val domainProject = ProjectBuilder.builder()
            .withName("demo-domain")
            .withParent(rootProject)
            .withProjectDir(rootProjectDir.resolve("demo-domain"))
            .build()
        domainProject.configurations.create("implementation")

        ensureValueObjectDomainDependencies(
            rootProject,
            projectConfig(
                modules = mapOf("domain" to "demo-domain"),
                sources = mapOf(
                    "value-object-manifest" to SourceConfig(
                        options = mapOf("files" to listOf(manifest.absolutePath))
                    )
                ),
                generators = emptyMap(),
            )
        )

        val implementationDependencies = domainProject.configurations.getByName("implementation").dependencies
        assertFalse(
            implementationDependencies.any { dependency ->
                dependency.group == "jakarta.persistence" && dependency.name == "jakarta.persistence-api"
            }
        )
        assertFalse(
            implementationDependencies.any { dependency ->
                dependency.group == "com.fasterxml.jackson.core" && dependency.name == "jackson-databind"
            }
        )
        assertFalse(
            implementationDependencies.any { dependency ->
                dependency.group == "com.fasterxml.jackson.module" && dependency.name == "jackson-module-kotlin"
            }
        )
    }

    @Test
    fun `enum manifest generation wires jakarta persistence api into resolved domain module`() {
        val rootProjectDir = tempProjectDir("pipeline-plugin-enum-manifest-domain-dependency-root")
        val rootProject = ProjectBuilder.builder()
            .withProjectDir(rootProjectDir)
            .build()
        val domainProject = ProjectBuilder.builder()
            .withName("demo-domain")
            .withParent(rootProject)
            .withProjectDir(rootProjectDir.resolve("demo-domain"))
            .build()
        domainProject.configurations.create("implementation")

        ensureEnumManifestDomainDependencies(
            rootProject,
            projectConfig(
                modules = mapOf("domain" to "demo-domain"),
                sources = mapOf("enum-manifest" to SourceConfig()),
                generators = emptyMap(),
            )
        )

        val implementationDependencies = domainProject.configurations.getByName("implementation").dependencies
        assertTrue(
            implementationDependencies.any { dependency ->
                dependency.group == "jakarta.persistence" && dependency.name == "jakarta.persistence-api"
            }
        )
    }

    @Test
    fun `value object generation does not duplicate json converter dependencies`() {
        val rootProjectDir = tempProjectDir("pipeline-plugin-value-object-domain-dependency-dedup-root")
        val manifest = rootProjectDir.resolve("value-objects.json").apply {
            writeText(
                """
                [
                  {
                    "name": "Money",
                    "package": "com.acme.demo.domain.shared.values",
                    "persistence": { "kind": "json" }
                  }
                ]
                """.trimIndent()
            )
        }
        val rootProject = ProjectBuilder.builder()
            .withProjectDir(rootProjectDir)
            .build()
        val domainProject = ProjectBuilder.builder()
            .withName("demo-domain")
            .withParent(rootProject)
            .withProjectDir(rootProjectDir.resolve("demo-domain"))
            .build()
        domainProject.configurations.create("implementation")
        domainProject.dependencies.add("implementation", "jakarta.persistence:jakarta.persistence-api:3.1.0")
        domainProject.dependencies.add("implementation", "com.fasterxml.jackson.core:jackson-databind:2.17.2")
        domainProject.dependencies.add("implementation", "com.fasterxml.jackson.module:jackson-module-kotlin:2.17.2")

        ensureValueObjectDomainDependencies(
            rootProject,
            projectConfig(
                modules = mapOf("domain" to "demo-domain"),
                sources = mapOf(
                    "value-object-manifest" to SourceConfig(
                        options = mapOf("files" to listOf(manifest.absolutePath))
                    )
                ),
                generators = emptyMap(),
            )
        )

        val implementationDependencies = domainProject.configurations.getByName("implementation").dependencies
        assertEquals(
            1,
            implementationDependencies.count { dependency ->
                dependency.group == "jakarta.persistence" && dependency.name == "jakarta.persistence-api"
            },
        )
        assertEquals(
            1,
            implementationDependencies.count { dependency ->
                dependency.group == "com.fasterxml.jackson.core" && dependency.name == "jackson-databind"
            },
        )
        assertEquals(
            1,
            implementationDependencies.count { dependency ->
                dependency.group == "com.fasterxml.jackson.module" && dependency.name == "jackson-module-kotlin"
            },
        )
    }

    @Test
    fun `aggregate generation does not duplicate jakarta persistence api dependency`() {
        val rootProjectDir = tempProjectDir("pipeline-plugin-aggregate-domain-dependency-dedup-root")
        val rootProject = ProjectBuilder.builder()
            .withProjectDir(rootProjectDir)
            .build()
        val domainProject = ProjectBuilder.builder()
            .withName("demo-domain")
            .withParent(rootProject)
            .withProjectDir(rootProjectDir.resolve("demo-domain"))
            .build()
        domainProject.configurations.create("implementation")
        domainProject.dependencies.add("implementation", "jakarta.persistence:jakarta.persistence-api:3.1.0")

        ensureAggregateDomainJpaDependency(
            rootProject,
            projectConfig(
                modules = mapOf("domain" to "demo-domain"),
                sources = mapOf("db" to SourceConfig()),
                generators = mapOf("aggregate" to GeneratorConfig()),
            )
        )

        val dependencyCount = domainProject.configurations.getByName("implementation").dependencies.count { dependency ->
            dependency.group == "jakarta.persistence" && dependency.name == "jakarta.persistence-api"
        }
        assertEquals(1, dependencyCount)
    }

    @Test
    fun `enum manifest generation does not duplicate jakarta persistence api dependency`() {
        val rootProjectDir = tempProjectDir("pipeline-plugin-enum-manifest-domain-dependency-dedup-root")
        val rootProject = ProjectBuilder.builder()
            .withProjectDir(rootProjectDir)
            .build()
        val domainProject = ProjectBuilder.builder()
            .withName("demo-domain")
            .withParent(rootProject)
            .withProjectDir(rootProjectDir.resolve("demo-domain"))
            .build()
        domainProject.configurations.create("implementation")
        domainProject.dependencies.add("implementation", "jakarta.persistence:jakarta.persistence-api:3.1.0")

        ensureEnumManifestDomainDependencies(
            rootProject,
            projectConfig(
                modules = mapOf("domain" to "demo-domain"),
                sources = mapOf("enum-manifest" to SourceConfig()),
                generators = emptyMap(),
            )
        )

        val dependencyCount = domainProject.configurations.getByName("implementation").dependencies.count { dependency ->
            dependency.group == "jakarta.persistence" && dependency.name == "jakarta.persistence-api"
        }
        assertEquals(1, dependencyCount)
    }

    @Test
    fun `aggregate projection generation wires jakarta persistence api into resolved adapter module`() {
        val rootProjectDir = tempProjectDir("pipeline-plugin-aggregate-projection-adapter-dependency-root")
        val rootProject = ProjectBuilder.builder()
            .withProjectDir(rootProjectDir)
            .build()
        val adapterProject = ProjectBuilder.builder()
            .withName("demo-adapter")
            .withParent(rootProject)
            .withProjectDir(rootProjectDir.resolve("demo-adapter"))
            .build()
        adapterProject.configurations.create("implementation")

        ensureAggregateProjectionAdapterJpaDependency(
            rootProject,
            projectConfig(
                modules = mapOf("adapter" to "demo-adapter"),
                sources = mapOf("db" to SourceConfig()),
                generators = mapOf("aggregate-projection" to GeneratorConfig()),
            )
        )

        val implementationDependencies = adapterProject.configurations.getByName("implementation").dependencies
        assertTrue(
            implementationDependencies.any { dependency ->
                dependency.group == "jakarta.persistence" && dependency.name == "jakarta.persistence-api"
            }
        )
    }

    @Test
    fun `ir analysis input dir does not match sibling project build dir by string prefix`() {
        val rootProjectDir = tempProjectDir("pipeline-plugin-prefix-root")
        val rootProject = ProjectBuilder.builder()
            .withProjectDir(rootProjectDir)
            .build()
        val appProject = ProjectBuilder.builder()
            .withName("app")
            .withParent(rootProject)
            .withProjectDir(rootProjectDir.resolve("app"))
            .build()
        val appCopyProject = ProjectBuilder.builder()
            .withName("app-copy")
            .withParent(rootProject)
            .withProjectDir(rootProjectDir.resolve("app-copy"))
            .build()
        appProject.layout.buildDirectory.set(rootProjectDir.resolve("shared/build/app"))
        appCopyProject.layout.buildDirectory.set(rootProjectDir.resolve("shared/build/app-copy"))
        appProject.tasks.register("compileKotlin")
        appCopyProject.tasks.register("compileKotlin")

        val dependencies = inferDependencies(
            rootProject,
            projectConfig(
                sources = mapOf(
                    "ir-analysis" to SourceConfig(
                        options = mapOf(
                            "inputDirs" to listOf(
                                appCopyProject.layout.buildDirectory.dir("cap4k-code-analysis").get().asFile.absolutePath
                            )
                        ),
                    )
                ),
                generators = mapOf("flow" to GeneratorConfig()),
            )
        )

        assertEquals(listOf(":app-copy:compileKotlin"), dependencies.map { it.path })
    }

    private fun readInternalProperty(target: Any, name: String): Any? {
        var type: Class<*>? = target.javaClass
        while (type != null) {
            runCatching {
                val field = type.getDeclaredField(name)
                field.isAccessible = true
                return field.get(target)
            }
            type = type.superclass
        }
        throw NoSuchFieldException(name)
    }

    private fun hasInternalProperty(target: Any, name: String): Boolean {
        var type: Class<*>? = target.javaClass
        while (type != null) {
            if (type.declaredFields.any { it.name == name }) {
                return true
            }
            type = type.superclass
        }
        return false
    }

    private fun runnerWithInternalProperty(runner: Any, name: String): Any {
        var current = runner
        while (!hasInternalProperty(current, name)) {
            current = readInternalProperty(current, "delegate") ?: throw NoSuchFieldException(name)
        }
        return current
    }

    private fun addonProviderIds(runner: Any): List<String> {
        val effectiveRunner = runnerWithInternalProperty(runner, "artifactAddons")
        val bindings = readInternalProperty(effectiveRunner, "artifactAddons") as List<*>
        return bindings.map { binding ->
            @Suppress("UNCHECKED_CAST")
            (binding as PipelineContributionBinding<ArtifactAddonProvider>).contribution.id
        }
    }

    private fun generatorProviderTypes(runner: Any): Set<Class<*>> {
        val effectiveRunner = runnerWithInternalProperty(runner, "generators")
        val providers = readInternalProperty(effectiveRunner, "generators") as List<*>
        return providers.map { it!!::class.java }.toSet()
    }

    private fun addonProviderJar(projectDir: File, name: String = "plugin-test-addon.jar"): File {
        val jar = projectDir.resolve(name)
        JarOutputStream(jar.outputStream()).use { output ->
            listOf(
                TestPipelinePluginExtensionProvider::class.java,
                TestPipelinePluginAddonProvider::class.java,
            ).forEach { providerClass ->
                val providerClassPath = providerClass.name.replace('.', '/') + ".class"
                output.putNextEntry(JarEntry(providerClassPath))
                output.write(
                    requireNotNull(providerClass.classLoader.getResourceAsStream(providerClassPath)) {
                        "provider class resource not found: $providerClassPath"
                    }.readBytes()
                )
                output.closeEntry()
            }
            output.putNextEntry(
                JarEntry("META-INF/services/com.only4.cap4k.plugin.pipeline.api.PipelineExtensionProvider")
            )
            output.write(TestPipelinePluginExtensionProvider::class.java.name.toByteArray(Charsets.UTF_8))
            output.closeEntry()
        }
        return jar
    }

    private fun jarWithResources(projectDir: File, name: String, entries: Map<String, String>): File {
        val jar = projectDir.resolve(name)
        JarOutputStream(jar.outputStream()).use { output ->
            entries.forEach { (path, content) ->
                output.putNextEntry(JarEntry(path))
                output.write(content.toByteArray(Charsets.UTF_8))
                output.closeEntry()
            }
        }
        return jar
    }

    private fun minimalConfig(): ProjectConfig =
        ProjectConfig(
            basePackage = "com.acme.demo",
            layout = ProjectLayout.MULTI_MODULE,
            modules = mapOf(
                "domain" to "demo-domain",
                "application" to "demo-application",
                "adapter" to "demo-adapter",
            ),
            sources = emptyMap(),
            generators = emptyMap(),
            templates = TemplateConfig("ddd-default", emptyList(), ConflictPolicy.SKIP),
        )

    private fun projectConfig(
        modules: Map<String, String> = emptyMap(),
        sources: Map<String, SourceConfig>,
        generators: Map<String, GeneratorConfig>,
    ): ProjectConfig =
        ProjectConfig(
            basePackage = "com.acme.demo",
            layout = ProjectLayout.MULTI_MODULE,
            modules = modules,
            sources = sources,
            generators = generators,
            templates = TemplateConfig(
                preset = "ddd-default",
                overrideDirs = emptyList(),
                conflictPolicy = ConflictPolicy.SKIP,
            ),
        )

    private fun configureValidAggregateGeneration(extension: Cap4kExtension) {
        extension.project {
            basePackage.set("com.acme.demo")
            domainModulePath.set("demo-domain")
            applicationModulePath.set("demo-application")
            adapterModulePath.set("demo-adapter")
        }
        extension.sources {
            db {
                enabled.set(true)
                url.set("jdbc:h2:mem:demo")
                username.set("sa")
                password.set("")
            }
        }
        extension.generators {
            aggregate { }
        }
    }

    private fun configureValidAggregateProjectionGeneration(extension: Cap4kExtension) {
        extension.project {
            basePackage.set("com.acme.demo")
            adapterModulePath.set("demo-adapter")
        }
        extension.sources {
            db {
                enabled.set(true)
                url.set("jdbc:h2:mem:demo")
                username.set("sa")
                password.set("")
            }
        }
        extension.generators {
            aggregateProjection { }
        }
    }

    private fun tempProjectDir(prefix: String): File =
        kotlin.io.path.createTempDirectory(prefix).toFile()
}

private class CloseTrackingUrlClassLoader(
    private val closeFailure: RuntimeException?,
) : URLClassLoader(emptyArray(), PipelinePluginTest::class.java.classLoader) {
    var closed: Boolean = false
        private set

    override fun close() {
        closed = true
        closeFailure?.let { throw it }
        super.close()
    }
}

class TestPipelinePluginAddonProvider : com.only4.cap4k.plugin.pipeline.api.ArtifactAddonProvider {
    override val id: String = "plugin-test-addon"

    override fun plan(
        context: com.only4.cap4k.plugin.pipeline.api.ArtifactAddonContext,
    ): List<com.only4.cap4k.plugin.pipeline.api.ArtifactPlanItem> = emptyList()
}

class TestPipelinePluginExtensionProvider : PipelineExtensionProvider {
    override val descriptor: PipelineExtensionDescriptor = PipelineExtensionDescriptor(
        id = "plugin-test-extension",
        spiVersion = PIPELINE_EXTENSION_SPI_VERSION,
    )
    override val contributions: List<PipelineContribution> = listOf(TestPipelinePluginAddonProvider())
}

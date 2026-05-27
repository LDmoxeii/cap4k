package com.only4.cap4k.plugin.pipeline.gradle

import com.only4.cap4k.plugin.pipeline.api.ConflictPolicy
import com.only4.cap4k.plugin.pipeline.api.BootstrapConfig
import com.only4.cap4k.plugin.pipeline.api.BootstrapMode
import com.only4.cap4k.plugin.pipeline.api.BootstrapModulesConfig
import com.only4.cap4k.plugin.pipeline.api.BootstrapTemplateConfig
import com.only4.cap4k.plugin.pipeline.api.GeneratorConfig
import com.only4.cap4k.plugin.pipeline.api.ArtifactAddonProvider
import com.only4.cap4k.plugin.pipeline.api.PipelineResult
import com.only4.cap4k.plugin.pipeline.api.PipelineRunner
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig
import com.only4.cap4k.plugin.pipeline.api.ProjectLayout
import com.only4.cap4k.plugin.pipeline.api.SourceConfig
import com.only4.cap4k.plugin.pipeline.api.TemplateConfig
import com.only4.cap4k.plugin.pipeline.api.TypeRegistryConfig
import com.only4.cap4k.plugin.pipeline.api.TypeRegistryEntry
import com.only4.cap4k.plugin.pipeline.core.BootstrapFilesystemArtifactExporter
import com.only4.cap4k.plugin.pipeline.generator.design.DesignIntegrationEventArtifactPlanner
import com.only4.cap4k.plugin.pipeline.generator.design.DesignIntegrationEventSubscriberArtifactPlanner
import com.only4.cap4k.plugin.pipeline.generator.types.ValueObjectArtifactPlanner
import com.only4.cap4k.plugin.pipeline.renderer.pebble.PebbleBootstrapRenderer
import com.only4.cap4k.plugin.pipeline.renderer.pebble.PresetTemplateResolver
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.Classpath
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
    fun `plugin registers cap4k addon configuration`() {
        val project = ProjectBuilder.builder().build()

        project.pluginManager.apply(PipelinePlugin::class.java)

        val configuration = project.configurations.getByName("cap4kAddon")

        assertTrue(configuration.isCanBeResolved)
        assertFalse(configuration.isCanBeConsumed)
    }

    @Test
    fun `addon consuming tasks declare cap4k addon classpath inputs`() {
        val projectDir = tempProjectDir("pipeline-plugin-addon-classpath-input")
        val addonJar = projectDir.resolve("addon.jar")
        addonJar.writeText("addon")
        val project = ProjectBuilder.builder()
            .withProjectDir(projectDir)
            .build()
        project.pluginManager.apply(PipelinePlugin::class.java)
        project.dependencies.add("cap4kAddon", project.files(addonJar))

        listOf(
            "cap4kPlan" to Cap4kPlanTask::class.java,
            "cap4kGenerate" to Cap4kGenerateTask::class.java,
            "cap4kGenerateSources" to Cap4kGenerateSourcesTask::class.java,
            "cap4kAnalysisPlan" to Cap4kAnalysisPlanTask::class.java,
            "cap4kAnalysisGenerate" to Cap4kAnalysisGenerateTask::class.java,
        ).forEach { (taskName, taskType) ->
            val getter = taskType.methods.singleOrNull {
                it.name == "getAddonClasspath" && it.parameterCount == 0
            }
            assertNotNull(getter, "$taskName must expose addonClasspath")
            assertNotNull(getter!!.getAnnotation(Classpath::class.java), "$taskName addonClasspath must be @Classpath")

            val task = project.tasks.named(taskName).get()
            val classpath = getter.invoke(task) as FileCollection
            assertEquals(setOf(addonJar), classpath.files)
        }
    }

    @Test
    fun `source and analysis runners receive providers loaded from cap4k addon configuration`() {
        val projectDir = tempProjectDir("pipeline-plugin-addon-runtime")
        val project = ProjectBuilder.builder()
            .withProjectDir(projectDir)
            .build()
        project.pluginManager.apply(PipelinePlugin::class.java)
        project.dependencies.add("cap4kAddon", project.files(addonProviderJar(projectDir)))

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
    fun `addon runtime closes classloader when provider loading fails`() {
        val addonFile = tempProjectDir("pipeline-plugin-addon-load-failure").resolve("addon.jar")
        addonFile.writeText("addon")
        val closeFailure = IllegalStateException("close failed")
        val classLoader = CloseTrackingUrlClassLoader(closeFailure)

        val exception = assertThrows(IllegalStateException::class.java) {
            loadArtifactAddonRuntime(
                files = setOf(addonFile),
                parent = javaClass.classLoader,
                classLoaderFactory = { _, _ -> classLoader },
                providerLoader = { error("load failed") },
            )
        }

        assertEquals("load failed", exception.message)
        assertTrue(classLoader.closed)
        assertSame(closeFailure, exception.suppressed.single())
    }

    @Test
    fun `addon runtime exposes template classloader by provider id`() {
        val addonFile = tempProjectDir("pipeline-plugin-addon-template-classloader").resolve("addon.jar")
        addonFile.writeText("addon")
        val classLoader = CloseTrackingUrlClassLoader(closeFailure = null)
        val templateClassLoader = CloseTrackingUrlClassLoader(closeFailure = null)

        val runtime = loadArtifactAddonRuntime(
            files = setOf(addonFile),
            parent = javaClass.classLoader,
            classLoaderFactory = { _, _ -> classLoader },
            providerLoader = { listOf(TestPipelinePluginAddonProvider()) },
            templateClassLoaderFactory = { templateClassLoader },
        )

        assertEquals(listOf("plugin-test-addon"), runtime.providers.map { it.id })
        assertSame(templateClassLoader, runtime.templateClassLoaders["plugin-test-addon"])

        runtime.closeables.forEach { it.close() }
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
        val runtime = loadArtifactAddonRuntime(
            files = setOf(providerJar, unrelatedResourceJar),
            parent = ArtifactAddonProvider::class.java.classLoader,
        )
        val resolver = PresetTemplateResolver(
            preset = "ddd-default-bootstrap",
            overrideDirs = emptyList(),
            addonTemplateClassLoaders = runtime.templateClassLoaders,
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
    fun `pipeline dependency inference is enabled when regular pipeline source or generator is enabled`() {
        val project = ProjectBuilder.builder().build()
        project.pluginManager.apply(PipelinePlugin::class.java)
        val extension = project.extensions.getByType(Cap4kExtension::class.java)

        extension.sources.irAnalysis.enabled.set(true)
        assertTrue(shouldInferPipelineDependencies(extension))

        extension.sources.irAnalysis.enabled.set(false)
        extension.generators.flow.enabled.set(true)
        assertTrue(shouldInferPipelineDependencies(extension))

        extension.generators.flow.enabled.set(false)
        extension.generators.aggregateProjection.enabled.set(true)
        assertTrue(shouldInferPipelineDependencies(extension))

        extension.generators.aggregateProjection.enabled.set(false)
        extension.sources.designJson.enabled.set(true)
        assertTrue(shouldInferPipelineDependencies(extension))
    }

    @Test
    fun `plugin registers bootstrap tasks`() {
        val project = ProjectBuilder.builder().build()
        project.pluginManager.apply("io.github.ldmoxeii.cap4k.pipeline")

        assertNotNull(project.tasks.findByName("cap4kBootstrapPlan"))
        assertNotNull(project.tasks.findByName("cap4kBootstrap"))
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
                    sources = mapOf("db" to SourceConfig(enabled = true)),
                    generators = mapOf(
                        "aggregate" to GeneratorConfig(
                            enabled = true,
                            options = mapOf(
                                "artifact.unique" to false,
                            ),
                        )
                    ),
                )
            )
        )
        assertEquals(
            setOf("domain", "application", "adapter"),
            generatedSourceModuleRoles(
                projectConfig(
                    modules = mapOf("domain" to "demo-domain", "application" to "demo-application", "adapter" to "demo-adapter"),
                    sources = mapOf("db" to SourceConfig(enabled = true)),
                    generators = mapOf(
                        "aggregate" to GeneratorConfig(
                            enabled = true,
                            options = mapOf(
                                "artifact.unique" to true,
                            ),
                        )
                    ),
                )
            )
        )
        assertEquals(
            emptySet<String>(),
            generatedSourceModuleRoles(
                projectConfig(
                    modules = mapOf("application" to "demo-application"),
                    sources = mapOf("design-json" to SourceConfig(enabled = true)),
                    generators = mapOf("design-query" to GeneratorConfig(enabled = true)),
                )
            )
        )
        assertEquals(
            setOf("adapter"),
            generatedSourceModuleRoles(
                projectConfig(
                    modules = mapOf("adapter" to "demo-adapter"),
                    sources = mapOf("db" to SourceConfig(enabled = true)),
                    generators = mapOf("aggregate-projection" to GeneratorConfig(enabled = true)),
                )
            )
        )
    }

    @Test
    fun `generated source task config keeps only generated source generation inputs`() {
        val config = projectConfig(
            sources = mapOf(
                "db" to SourceConfig(enabled = true),
                "enum-manifest" to SourceConfig(enabled = true),
                "design-json" to SourceConfig(enabled = true),
                "ksp-metadata" to SourceConfig(enabled = true),
                "ir-analysis" to SourceConfig(enabled = true),
            ),
            generators = mapOf(
                "aggregate" to GeneratorConfig(enabled = true),
                "aggregate-projection" to GeneratorConfig(enabled = true),
                "design-query" to GeneratorConfig(enabled = true),
                "design-query-handler" to GeneratorConfig(enabled = true),
                "design-integration-event" to GeneratorConfig(enabled = true),
                "design-integration-event-subscriber" to GeneratorConfig(enabled = true),
                "drawing-board" to GeneratorConfig(enabled = true),
                "flow" to GeneratorConfig(enabled = true),
            ),
        )

        val generatedConfig = generatedSourceTaskConfig(config)

        assertEquals(setOf("db", "enum-manifest"), generatedConfig.sources.keys)
        assertEquals(setOf("aggregate", "aggregate-projection"), generatedConfig.generators.keys)
    }

    @Test
    fun `source task config keeps checked in source design integration event generators`() {
        val config = projectConfig(
            sources = mapOf(
                "design-json" to SourceConfig(enabled = true),
                "ksp-metadata" to SourceConfig(enabled = true),
                "value-object-manifest" to SourceConfig(enabled = true),
                "ir-analysis" to SourceConfig(enabled = true),
            ),
            generators = mapOf(
                "design-integration-event" to GeneratorConfig(enabled = true),
                "design-integration-event-subscriber" to GeneratorConfig(enabled = true),
                "types-value-object" to GeneratorConfig(enabled = true),
                "drawing-board" to GeneratorConfig(enabled = true),
                "flow" to GeneratorConfig(enabled = true),
            ),
        )

        val sourceConfig = sourceTaskConfig(config)

        assertEquals(setOf("design-json", "ksp-metadata", "value-object-manifest"), sourceConfig.sources.keys)
        assertEquals(
            setOf("design-integration-event", "design-integration-event-subscriber", "types-value-object"),
            sourceConfig.generators.keys,
        )
    }

    @Test
    fun `source runner includes design integration event planners`() {
        val project = ProjectBuilder.builder().build()

        val runner = buildSourceRunner(project, minimalConfig(), exportEnabled = false)

        assertTrue(generatorProviderTypes(runner).contains(DesignIntegrationEventArtifactPlanner::class.java))
        assertTrue(generatorProviderTypes(runner).contains(DesignIntegrationEventSubscriberArtifactPlanner::class.java))
        assertTrue(generatorProviderTypes(runner).contains(ValueObjectArtifactPlanner::class.java))
    }

    @Test
    fun `generated source dependency inference ignores design ksp metadata`() {
        val rootProjectDir = tempProjectDir("pipeline-plugin-generated-source-ksp-root")
        val rootProject = ProjectBuilder.builder()
            .withProjectDir(rootProjectDir)
            .build()
        val domainProject = ProjectBuilder.builder()
            .withName("domain")
            .withProjectDir(rootProjectDir.resolve("domain"))
            .withParent(rootProject)
            .build()
        domainProject.tasks.register("kspKotlin")

        val config = projectConfig(
            sources = mapOf(
                "db" to SourceConfig(enabled = true),
                "ksp-metadata" to SourceConfig(
                    enabled = true,
                    options = mapOf("inputDir" to domainProject.layout.buildDirectory.dir("generated/ksp/main").get().asFile.absolutePath),
                ),
            ),
            generators = mapOf(
                "aggregate" to GeneratorConfig(enabled = true),
                "design-query" to GeneratorConfig(enabled = true),
            ),
        )

        val dependencies = inferSourceDependencies(rootProject, generatedSourceTaskConfig(config))

        assertEquals(emptyList<String>(), dependencies.map { it.path })
    }

    @Test
    fun `generated kotlin source root is module local`() {
        val config = projectConfig(
            modules = mapOf("domain" to "demo-domain"),
            sources = mapOf("db" to SourceConfig(enabled = true)),
            generators = mapOf("aggregate" to GeneratorConfig(enabled = true)),
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
                    enabled = true,
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
                    enabled = true,
                    options = mapOf("files" to listOf("enums.json")),
                ),
            ),
            generators = mapOf(
                "aggregate" to GeneratorConfig(
                    enabled = true,
                    options = mapOf(
                        "unsupportedTablePolicy" to "FAIL",
                        "artifact.unique" to true,
                    ),
                ),
                "aggregate-projection" to GeneratorConfig(enabled = true),
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
        assertTrue(snapshot.contains("artifact.unique"))
        assertTrue(snapshot.contains("aggregateProjection"))
        assertTrue(snapshot.contains("demo-domain/build/generated/cap4k/main/kotlin"))
    }

    @Test
    fun `generated source task detects live db without tracked schema input`() {
        val rootProject = ProjectBuilder.builder()
            .withProjectDir(tempProjectDir("pipeline-plugin-generated-source-live-db"))
            .build()
        val config = projectConfig(
            sources = mapOf(
                "db" to SourceConfig(
                    enabled = true,
                    options = mapOf("url" to "jdbc:mysql://localhost:3306/demo"),
                ),
            ),
            generators = mapOf("aggregate" to GeneratorConfig(enabled = true)),
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
                    enabled = true,
                    options = mapOf(
                        "url" to "jdbc:h2:file:./build/h2/demo;INIT=RUNSCRIPT FROM '${schemaFile.absolutePath.replace("\\", "/")}'"
                    ),
                ),
            ),
            generators = mapOf("aggregate" to GeneratorConfig(enabled = true)),
        )

        assertFalse(generatedSourceTaskHasUntrackedLiveDbInput(rootProject, config))
    }

    @Test
    fun `build bootstrap runner uses template preset and override dirs from config`() {
        val project = ProjectBuilder.builder().build()
        val absoluteOverrideDir = project.projectDir.resolve("bootstrap-templates").absolutePath
        val config = BootstrapConfig(
            preset = "ddd-multi-module",
            projectName = "demo",
            basePackage = "com.acme.demo",
            modules = BootstrapModulesConfig("demo-domain", "demo-application", "demo-adapter", "demo-start"),
            templates = BootstrapTemplateConfig(
                preset = "custom-bootstrap-preset",
                overrideDirs = listOf(absoluteOverrideDir),
            ),
            slots = emptyList(),
            conflictPolicy = ConflictPolicy.FAIL,
            mode = BootstrapMode.IN_PLACE,
            previewDir = null,
        )

        val runner = buildBootstrapRunner(project, config, exportEnabled = false)
        val renderer = readInternalProperty(runner, "renderer")
        val resolver = readInternalProperty(renderer!!, "templateResolver")

        assertInstanceOf(PebbleBootstrapRenderer::class.java, renderer)
        assertInstanceOf(PresetTemplateResolver::class.java, resolver)
        assertEquals("custom-bootstrap-preset", readInternalProperty(resolver!!, "preset"))
        assertEquals(listOf(absoluteOverrideDir), readInternalProperty(resolver, "overrideDirs"))
    }

    @Test
    fun `build bootstrap runner rebases relative override dirs against bootstrap project dir`() {
        val projectDir = tempProjectDir("pipeline-bootstrap-relative-override")
        val project = ProjectBuilder.builder()
            .withProjectDir(projectDir)
            .build()
        val config = BootstrapConfig(
            preset = "ddd-multi-module",
            projectName = "demo",
            basePackage = "com.acme.demo",
            modules = BootstrapModulesConfig("demo-domain", "demo-application", "demo-adapter", "demo-start"),
            templates = BootstrapTemplateConfig(
                preset = "custom-bootstrap-preset",
                overrideDirs = listOf("templates/bootstrap", "codegen/overrides"),
            ),
            slots = emptyList(),
            conflictPolicy = ConflictPolicy.FAIL,
            mode = BootstrapMode.IN_PLACE,
            previewDir = null,
        )

        val runner = buildBootstrapRunner(project, config, exportEnabled = false)
        val renderer = readInternalProperty(runner, "renderer")
        val resolver = readInternalProperty(renderer!!, "templateResolver")
        val overrideDirs = readInternalProperty(resolver!!, "overrideDirs") as List<String>

        assertEquals(
            listOf(
                projectDir.toPath().resolve("templates/bootstrap").normalize().toFile().canonicalPath,
                projectDir.toPath().resolve("codegen/overrides").normalize().toFile().canonicalPath,
            ),
            overrideDirs.map { File(it).canonicalPath }
        )
    }

    @Test
    fun `build bootstrap runner uses bootstrap filesystem exporter when export is enabled`() {
        val project = ProjectBuilder.builder().build()
        val config = BootstrapConfig(
            preset = "ddd-multi-module",
            projectName = "demo",
            basePackage = "com.acme.demo",
            modules = BootstrapModulesConfig("demo-domain", "demo-application", "demo-adapter", "demo-start"),
            templates = BootstrapTemplateConfig("ddd-default-bootstrap", emptyList()),
            slots = emptyList(),
            conflictPolicy = ConflictPolicy.FAIL,
            mode = BootstrapMode.IN_PLACE,
            previewDir = null,
        )

        val runner = buildBootstrapRunner(project, config, exportEnabled = true)
        val exporter = readInternalProperty(runner, "exporter")

        assertInstanceOf(BootstrapFilesystemArtifactExporter::class.java, exporter)
    }

    @Test
    fun `bootstrap plan task writes bootstrap plan report for valid bootstrap config`() {
        val project = ProjectBuilder.builder()
            .withProjectDir(tempProjectDir("pipeline-bootstrap-plan-task"))
            .build()
        project.pluginManager.apply(PipelinePlugin::class.java)
        configureValidBootstrap(project.extensions.getByType(Cap4kExtension::class.java))
        writeManagedRootHostFiles(project.projectDir)
        val planTask = project.tasks.named("cap4kBootstrapPlan", Cap4kBootstrapPlanTask::class.java).get()

        planTask.runPlan()

        val planFile = project.layout.buildDirectory.file("cap4k/bootstrap-plan.json").get().asFile
        assertTrue(planFile.exists())
        assertTrue(planFile.readText().contains("\"templateId\": \"bootstrap/root/settings.gradle.kts.peb\""))
    }

    @Test
    fun `bootstrap generate task writes bootstrap skeleton files for valid bootstrap config`() {
        val project = ProjectBuilder.builder()
            .withProjectDir(tempProjectDir("pipeline-bootstrap-generate-task"))
            .build()
        project.pluginManager.apply(PipelinePlugin::class.java)
        configureValidBootstrap(project.extensions.getByType(Cap4kExtension::class.java))
        writeManagedRootHostFiles(project.projectDir)
        val generateTask = project.tasks.named("cap4kBootstrap", Cap4kBootstrapTask::class.java).get()

        generateTask.generate()

        assertTrue(project.projectDir.resolve("settings.gradle.kts").exists())
        assertTrue(project.projectDir.resolve("build.gradle.kts").exists())
        assertTrue(project.projectDir.resolve("only-danmuku-domain/build.gradle.kts").exists())
        assertTrue(project.projectDir.resolve("only-danmuku-start/build.gradle.kts").exists())
        assertTrue(
            project.projectDir.resolve("only-danmuku-start/src/main/kotlin/edu/only4/danmuku/StartApplication.kt").exists()
        )
    }

    @Test
    fun `design command with ksp metadata depends on relevant ksp task only`() {
        val rootProjectDir = tempProjectDir("pipeline-plugin-ksp-root")
        val rootProject = ProjectBuilder.builder()
            .withProjectDir(rootProjectDir)
            .build()
        val domainProject = ProjectBuilder.builder()
            .withName("domain")
            .withParent(rootProject)
            .withProjectDir(rootProjectDir.resolve("domain"))
            .build()
        domainProject.tasks.register("kspKotlin")
        rootProject.tasks.register("kspKotlin")

        val dependencies = inferDependencies(
            rootProject,
            projectConfig(
                sources = mapOf(
                    "ksp-metadata" to SourceConfig(
                        enabled = true,
                        options = mapOf("inputDir" to domainProject.layout.buildDirectory.dir("generated/ksp/main").get().asFile.absolutePath),
                    )
                ),
                generators = mapOf("design-command" to GeneratorConfig(enabled = true)),
            )
        )

        assertEquals(listOf(":domain:kspKotlin"), dependencies.map { it.path })
    }

    @Test
    fun `design domain event with ksp metadata depends on relevant ksp task only`() {
        val rootProjectDir = tempProjectDir("pipeline-plugin-ksp-domain-event-root")
        val rootProject = ProjectBuilder.builder()
            .withProjectDir(rootProjectDir)
            .build()
        val domainProject = ProjectBuilder.builder()
            .withName("domain")
            .withParent(rootProject)
            .withProjectDir(rootProjectDir.resolve("domain"))
            .build()
        domainProject.tasks.register("kspKotlin")
        rootProject.tasks.register("kspKotlin")

        val dependencies = inferDependencies(
            rootProject,
            projectConfig(
                sources = mapOf(
                    "ksp-metadata" to SourceConfig(
                        enabled = true,
                        options = mapOf("inputDir" to domainProject.layout.buildDirectory.dir("generated/ksp/main").get().asFile.absolutePath),
                    )
                ),
                generators = mapOf("design-domain-event" to GeneratorConfig(enabled = true)),
            )
        )

        assertEquals(listOf(":domain:kspKotlin"), dependencies.map { it.path })
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
                        enabled = true,
                        options = mapOf(
                            "inputDirs" to listOf(
                                analysisProject.layout.buildDirectory.dir("cap4k-code-analysis").get().asFile.absolutePath
                            )
                        ),
                    )
                ),
                generators = mapOf("flow" to GeneratorConfig(enabled = true)),
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
                        enabled = true,
                        options = mapOf(
                            "inputDirs" to listOf(
                                analysisProject.layout.buildDirectory.dir("cap4k-code-analysis").get().asFile.absolutePath
                            )
                        ),
                    )
                ),
                generators = mapOf("flow" to GeneratorConfig(enabled = true)),
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
                        enabled = true,
                        options = mapOf(
                            "inputDirs" to listOf(project.layout.buildDirectory.dir("cap4k-code-analysis").get().asFile.absolutePath)
                        ),
                    )
                ),
                generators = mapOf("drawing-board" to GeneratorConfig(enabled = true)),
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
        project.tasks.register("kspKotlin")

        val dependencies = inferDependencies(
            project,
            projectConfig(
                sources = mapOf("db" to SourceConfig(enabled = true)),
                generators = mapOf("aggregate" to GeneratorConfig(enabled = true)),
            )
        )

        assertEquals(emptyList<String>(), dependencies.map { it.path })
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
                sources = mapOf("db" to SourceConfig(enabled = true)),
                generators = mapOf("aggregate" to GeneratorConfig(enabled = true)),
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
    fun `value object generation wires json converter dependencies into resolved domain module`() {
        val rootProjectDir = tempProjectDir("pipeline-plugin-value-object-domain-dependency-root")
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
                sources = mapOf("value-object-manifest" to SourceConfig(enabled = true)),
                generators = mapOf("types-value-object" to GeneratorConfig(enabled = true)),
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
    fun `value object generation does not duplicate json converter dependencies`() {
        val rootProjectDir = tempProjectDir("pipeline-plugin-value-object-domain-dependency-dedup-root")
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
                sources = mapOf("value-object-manifest" to SourceConfig(enabled = true)),
                generators = mapOf("types-value-object" to GeneratorConfig(enabled = true)),
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
                sources = mapOf("db" to SourceConfig(enabled = true)),
                generators = mapOf("aggregate" to GeneratorConfig(enabled = true)),
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
                sources = mapOf("db" to SourceConfig(enabled = true)),
                generators = mapOf("aggregate-projection" to GeneratorConfig(enabled = true)),
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
                        enabled = true,
                        options = mapOf(
                            "inputDirs" to listOf(
                                appCopyProject.layout.buildDirectory.dir("cap4k-code-analysis").get().asFile.absolutePath
                            )
                        ),
                    )
                ),
                generators = mapOf("flow" to GeneratorConfig(enabled = true)),
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
        val effectiveRunner = runnerWithInternalProperty(runner, "addonProviders")
        val providers = readInternalProperty(effectiveRunner, "addonProviders") as List<*>
        return providers.map { provider ->
            readInternalProperty(provider!!, "id").toString()
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
            val providerClassPath = TestPipelinePluginAddonProvider::class.java.name.replace('.', '/') + ".class"
            output.putNextEntry(JarEntry(providerClassPath))
            output.write(
                requireNotNull(TestPipelinePluginAddonProvider::class.java.classLoader.getResourceAsStream(providerClassPath)) {
                    "provider class resource not found: $providerClassPath"
                }.readBytes()
            )
            output.closeEntry()
            output.putNextEntry(
                JarEntry("META-INF/services/com.only4.cap4k.plugin.pipeline.api.ArtifactAddonProvider")
            )
            output.write(TestPipelinePluginAddonProvider::class.java.name.toByteArray(Charsets.UTF_8))
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

    private fun configureValidBootstrap(extension: Cap4kExtension) {
        extension.bootstrap {
            enabled.set(true)
            preset.set("ddd-multi-module")
            projectName.set("only-danmuku")
            basePackage.set("edu.only4.danmuku")
            modules {
                domainModuleName.set("only-danmuku-domain")
                applicationModuleName.set("only-danmuku-application")
                adapterModuleName.set("only-danmuku-adapter")
                startModuleName.set("only-danmuku-start")
            }
            templates {
                preset.set("ddd-default-bootstrap")
            }
        }
    }

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
            aggregate {
                enabled.set(true)
            }
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
            aggregateProjection {
                enabled.set(true)
            }
        }
    }

    private fun writeManagedRootHostFiles(projectDir: File) {
        projectDir.resolve("build.gradle.kts").writeText(
            """
                plugins {
                    id("io.github.ldmoxeii.cap4k.pipeline")
                }

                // [cap4k-bootstrap:managed-begin:root-host]
                cap4k {
                    bootstrap {
                        enabled.set(true)
                    }
                }
                // [cap4k-bootstrap:managed-end:root-host]
            """.trimIndent()
        )
        projectDir.resolve("settings.gradle.kts").writeText(
            """
                // [cap4k-bootstrap:managed-begin:root-host]
                rootProject.name = "demo"
                // [cap4k-bootstrap:managed-end:root-host]
            """.trimIndent()
        )
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

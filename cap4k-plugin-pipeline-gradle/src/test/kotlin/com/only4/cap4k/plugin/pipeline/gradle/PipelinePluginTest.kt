package com.only4.cap4k.plugin.pipeline.gradle

import com.only4.cap4k.plugin.pipeline.api.ConflictPolicy
import com.only4.cap4k.plugin.pipeline.api.BootstrapConfig
import com.only4.cap4k.plugin.pipeline.api.BootstrapMode
import com.only4.cap4k.plugin.pipeline.api.BootstrapModulesConfig
import com.only4.cap4k.plugin.pipeline.api.BootstrapTemplateConfig
import com.only4.cap4k.plugin.pipeline.api.GeneratorConfig
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig
import com.only4.cap4k.plugin.pipeline.api.ProjectLayout
import com.only4.cap4k.plugin.pipeline.api.SourceConfig
import com.only4.cap4k.plugin.pipeline.api.TemplateConfig
import com.only4.cap4k.plugin.pipeline.core.BootstrapFilesystemArtifactExporter
import com.only4.cap4k.plugin.pipeline.renderer.pebble.PebbleBootstrapRenderer
import com.only4.cap4k.plugin.pipeline.renderer.pebble.PresetTemplateResolver
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
    }

    @Test
    fun `plugin registers bootstrap tasks`() {
        val project = ProjectBuilder.builder().build()
        project.pluginManager.apply("com.only4.cap4k.plugin.pipeline")

        assertNotNull(project.tasks.findByName("cap4kBootstrapPlan"))
        assertNotNull(project.tasks.findByName("cap4kBootstrap"))
    }

    @Test
    fun `build bootstrap runner uses template preset and override dirs from config`() {
        val project = ProjectBuilder.builder().build()
        val config = BootstrapConfig(
            preset = "ddd-multi-module",
            projectName = "demo",
            basePackage = "com.acme.demo",
            modules = BootstrapModulesConfig("demo-domain", "demo-application", "demo-adapter", "demo-start"),
            templates = BootstrapTemplateConfig(
                preset = "custom-bootstrap-preset",
                overrideDirs = listOf("/tmp/bootstrap-templates"),
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
        assertEquals(listOf("/tmp/bootstrap-templates"), readInternalProperty(resolver, "overrideDirs"))
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
        val generateTask = project.tasks.named("cap4kBootstrap", Cap4kBootstrapTask::class.java).get()

        generateTask.generate()

        assertTrue(project.projectDir.resolve("only-danmuku/settings.gradle.kts").exists())
        assertTrue(project.projectDir.resolve("only-danmuku/build.gradle.kts").exists())
    }

    @Test
    fun `design with ksp metadata depends on relevant ksp task only`() {
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
                generators = mapOf("design" to GeneratorConfig(enabled = true)),
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
            }
            templates {
                preset.set("ddd-default-bootstrap")
            }
        }
    }

    private fun tempProjectDir(prefix: String): File =
        kotlin.io.path.createTempDirectory(prefix).toFile()
}

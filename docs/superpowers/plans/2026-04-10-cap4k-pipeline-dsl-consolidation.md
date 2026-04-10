# Cap4k Pipeline DSL Consolidation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the current flat `cap4kPipeline` Gradle extension with a breaking nested `cap4k` DSL that explicitly enables sources and generators and centralizes conversion into `ProjectConfig`.

**Architecture:** Keep pipeline core unchanged and confine the work to `cap4k-plugin-pipeline-gradle`. Introduce one typed extension root plus one `Cap4kProjectConfigFactory` that owns defaults, validation, and cross-dependency checks, then rewire plugin tasks and fixtures to the new DSL.

**Tech Stack:** Kotlin 2.2, Gradle Kotlin DSL, Gradle API, Gradle TestKit, JUnit 5

---

## File Map

- Delete: `cap4k/cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelineExtension.kt`
- Create: `cap4k/cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kExtension.kt`
- Create: `cap4k/cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kProjectConfigFactory.kt`
- Modify: `cap4k/cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePlugin.kt`
- Modify: `cap4k/cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kPlanTask.kt`
- Modify: `cap4k/cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kGenerateTask.kt`
- Create: `cap4k/cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kProjectConfigFactoryTest.kt`
- Modify: `cap4k/cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginFunctionalTest.kt`
- Modify: `cap4k/cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-sample/build.gradle.kts`
- Modify: `cap4k/cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-sample/build.gradle.kts`
- Modify: `cap4k/cap4k-plugin-pipeline-gradle/src/test/resources/functional/flow-sample/build.gradle.kts`
- Modify: `cap4k/cap4k-plugin-pipeline-gradle/src/test/resources/functional/flow-compile-sample/build.gradle.kts`
- Modify: `cap4k/cap4k-plugin-pipeline-gradle/src/test/resources/functional/drawing-board-sample/build.gradle.kts`

### Task 1: Replace the Flat Extension With a Nested `cap4k` DSL

**Files:**
- Delete: `cap4k/cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelineExtension.kt`
- Create: `cap4k/cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kExtension.kt`
- Create: `cap4k/cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kProjectConfigFactoryTest.kt`

- [ ] **Step 1: Write the failing config-factory tests for the new DSL**

```kotlin
package com.only4.cap4k.plugin.pipeline.gradle

import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class Cap4kProjectConfigFactoryTest {

    @Test
    fun `build config keeps only explicitly enabled blocks`() {
        val project = ProjectBuilder.builder().build()
        project.pluginManager.apply("com.only4.cap4k.plugin.pipeline")
        val extension = project.extensions.getByType(Cap4kExtension::class.java)

        extension.project {
            basePackage.set("com.acme.demo")
            applicationModulePath.set("demo-application")
        }
        extension.sources {
            designJson {
                enabled.set(true)
                files.from(project.file("design/design.json"))
            }
            db {
                enabled.set(false)
                url.set("jdbc:h2:mem:test")
            }
        }
        extension.generators {
            design { enabled.set(true) }
            aggregate { enabled.set(false) }
        }

        val config = Cap4kProjectConfigFactory().build(project, extension)

        assertEquals(setOf("design-json"), config.sources.keys)
        assertEquals(setOf("design"), config.generators.keys)
        assertEquals("ddd-default", config.templates.preset)
        assertEquals("demo-application", config.modules["application"])
        assertFalse(config.sources.containsKey("db"))
    }

    @Test
    fun `disabled blocks do not participate in validation`() {
        val project = ProjectBuilder.builder().build()
        project.pluginManager.apply("com.only4.cap4k.plugin.pipeline")
        val extension = project.extensions.getByType(Cap4kExtension::class.java)

        extension.project {
            basePackage.set("com.acme.demo")
        }
        extension.sources {
            db {
                enabled.set(false)
                url.set("")
                username.set("")
            }
        }
        extension.generators {
            aggregate { enabled.set(false) }
        }

        val config = Cap4kProjectConfigFactory().build(project, extension)

        assertEquals(emptySet<String>(), config.sources.keys)
        assertEquals(emptySet<String>(), config.generators.keys)
    }

    @Test
    fun `enabled aggregate generator requires enabled db source`() {
        val project = ProjectBuilder.builder().build()
        project.pluginManager.apply("com.only4.cap4k.plugin.pipeline")
        val extension = project.extensions.getByType(Cap4kExtension::class.java)

        extension.project {
            basePackage.set("com.acme.demo")
            domainModulePath.set("demo-domain")
            adapterModulePath.set("demo-adapter")
        }
        extension.sources {
            db { enabled.set(false) }
        }
        extension.generators {
            aggregate { enabled.set(true) }
        }

        val error = assertThrows(IllegalArgumentException::class.java) {
            Cap4kProjectConfigFactory().build(project, extension)
        }

        assertEquals("aggregate generator requires enabled db source.", error.message)
    }
}
```

- [ ] **Step 2: Run the targeted tests and confirm they fail**

Run: `./gradlew :cap4k-plugin-pipeline-gradle:test --tests "com.only4.cap4k.plugin.pipeline.gradle.Cap4kProjectConfigFactoryTest" --rerun-tasks`

Expected: compilation fails because `Cap4kExtension` and `Cap4kProjectConfigFactory` do not exist yet.

- [ ] **Step 3: Add the nested extension root and sub-blocks**

```kotlin
package com.only4.cap4k.plugin.pipeline.gradle

import org.gradle.api.Action
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import javax.inject.Inject

open class Cap4kExtension @Inject constructor(objects: ObjectFactory) {
    val project: Cap4kProjectExtension = objects.newInstance(Cap4kProjectExtension::class.java)
    val sources: Cap4kSourcesExtension = objects.newInstance(Cap4kSourcesExtension::class.java)
    val generators: Cap4kGeneratorsExtension = objects.newInstance(Cap4kGeneratorsExtension::class.java)
    val templates: Cap4kTemplatesExtension = objects.newInstance(Cap4kTemplatesExtension::class.java)

    fun project(action: Action<in Cap4kProjectExtension>) = action.execute(project)
    fun sources(action: Action<in Cap4kSourcesExtension>) = action.execute(sources)
    fun generators(action: Action<in Cap4kGeneratorsExtension>) = action.execute(generators)
    fun templates(action: Action<in Cap4kTemplatesExtension>) = action.execute(templates)
}

open class Cap4kProjectExtension @Inject constructor(objects: ObjectFactory) {
    val basePackage: Property<String> = objects.property(String::class.java)
    val applicationModulePath: Property<String> = objects.property(String::class.java)
    val domainModulePath: Property<String> = objects.property(String::class.java)
    val adapterModulePath: Property<String> = objects.property(String::class.java)
}

open class Cap4kSourcesExtension @Inject constructor(objects: ObjectFactory) {
    val designJson: DesignJsonSourceExtension = objects.newInstance(DesignJsonSourceExtension::class.java)
    val kspMetadata: KspMetadataSourceExtension = objects.newInstance(KspMetadataSourceExtension::class.java)
    val db: DbSourceExtension = objects.newInstance(DbSourceExtension::class.java)
    val irAnalysis: IrAnalysisSourceExtension = objects.newInstance(IrAnalysisSourceExtension::class.java)

    fun designJson(action: Action<in DesignJsonSourceExtension>) = action.execute(designJson)
    fun kspMetadata(action: Action<in KspMetadataSourceExtension>) = action.execute(kspMetadata)
    fun db(action: Action<in DbSourceExtension>) = action.execute(db)
    fun irAnalysis(action: Action<in IrAnalysisSourceExtension>) = action.execute(irAnalysis)
}

open class DesignJsonSourceExtension @Inject constructor(objects: ObjectFactory) {
    val enabled: Property<Boolean> = objects.property(Boolean::class.java).convention(false)
    val files: ConfigurableFileCollection = objects.fileCollection()
}

open class KspMetadataSourceExtension @Inject constructor(objects: ObjectFactory) {
    val enabled: Property<Boolean> = objects.property(Boolean::class.java).convention(false)
    val inputDir: Property<String> = objects.property(String::class.java)
}

open class DbSourceExtension @Inject constructor(objects: ObjectFactory) {
    val enabled: Property<Boolean> = objects.property(Boolean::class.java).convention(false)
    val url: Property<String> = objects.property(String::class.java)
    val username: Property<String> = objects.property(String::class.java)
    val password: Property<String> = objects.property(String::class.java)
    val schema: Property<String> = objects.property(String::class.java)
    val includeTables: ListProperty<String> = objects.listProperty(String::class.java)
    val excludeTables: ListProperty<String> = objects.listProperty(String::class.java)
}

open class IrAnalysisSourceExtension @Inject constructor(objects: ObjectFactory) {
    val enabled: Property<Boolean> = objects.property(Boolean::class.java).convention(false)
    val inputDirs: ConfigurableFileCollection = objects.fileCollection()
}

open class Cap4kGeneratorsExtension @Inject constructor(objects: ObjectFactory) {
    val design: DesignGeneratorExtension = objects.newInstance(DesignGeneratorExtension::class.java)
    val aggregate: AggregateGeneratorExtension = objects.newInstance(AggregateGeneratorExtension::class.java)
    val drawingBoard: DrawingBoardGeneratorExtension = objects.newInstance(DrawingBoardGeneratorExtension::class.java)
    val flow: FlowGeneratorExtension = objects.newInstance(FlowGeneratorExtension::class.java)

    fun design(action: Action<in DesignGeneratorExtension>) = action.execute(design)
    fun aggregate(action: Action<in AggregateGeneratorExtension>) = action.execute(aggregate)
    fun drawingBoard(action: Action<in DrawingBoardGeneratorExtension>) = action.execute(drawingBoard)
    fun flow(action: Action<in FlowGeneratorExtension>) = action.execute(flow)
}

open class DesignGeneratorExtension @Inject constructor(objects: ObjectFactory) {
    val enabled: Property<Boolean> = objects.property(Boolean::class.java).convention(false)
}

open class AggregateGeneratorExtension @Inject constructor(objects: ObjectFactory) {
    val enabled: Property<Boolean> = objects.property(Boolean::class.java).convention(false)
}

open class DrawingBoardGeneratorExtension @Inject constructor(objects: ObjectFactory) {
    val enabled: Property<Boolean> = objects.property(Boolean::class.java).convention(false)
    val outputDir: Property<String> = objects.property(String::class.java)
}

open class FlowGeneratorExtension @Inject constructor(objects: ObjectFactory) {
    val enabled: Property<Boolean> = objects.property(Boolean::class.java).convention(false)
    val outputDir: Property<String> = objects.property(String::class.java)
}

open class Cap4kTemplatesExtension @Inject constructor(objects: ObjectFactory) {
    val preset: Property<String> = objects.property(String::class.java).convention("ddd-default")
    val overrideDirs: ConfigurableFileCollection = objects.fileCollection()
    val conflictPolicy: Property<String> = objects.property(String::class.java).convention("SKIP")
}
```

- [ ] **Step 4: Run the targeted tests again**

Run: `./gradlew :cap4k-plugin-pipeline-gradle:test --tests "com.only4.cap4k.plugin.pipeline.gradle.Cap4kProjectConfigFactoryTest" --rerun-tasks`

Expected: compilation still fails because the config factory has not been implemented yet.

- [ ] **Step 5: Commit the extension replacement scaffold**

```bash
git add \
  cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kExtension.kt \
  cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kProjectConfigFactoryTest.kt
git rm cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelineExtension.kt
git commit -m "refactor: add nested cap4k gradle extension"
```

### Task 2: Centralize Validation and `ProjectConfig` Assembly

**Files:**
- Create: `cap4k/cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kProjectConfigFactory.kt`
- Modify: `cap4k/cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kProjectConfigFactoryTest.kt`

- [ ] **Step 1: Extend the tests for defaults, project rules, and generator-source dependencies**

```kotlin
@Test
fun `design generator requires application module path`() {
    val project = ProjectBuilder.builder().build()
    project.pluginManager.apply("com.only4.cap4k.plugin.pipeline")
    val extension = project.extensions.getByType(Cap4kExtension::class.java)

    extension.project {
        basePackage.set("com.acme.demo")
    }
    extension.sources {
        designJson {
            enabled.set(true)
            files.from(project.file("design/design.json"))
        }
    }
    extension.generators {
        design { enabled.set(true) }
    }

    val error = assertThrows(IllegalArgumentException::class.java) {
        Cap4kProjectConfigFactory().build(project, extension)
    }

    assertEquals("design generator requires project.applicationModulePath.", error.message)
}

@Test
fun `flow generator requires output dir and enabled ir analysis source`() {
    val project = ProjectBuilder.builder().build()
    project.pluginManager.apply("com.only4.cap4k.plugin.pipeline")
    val extension = project.extensions.getByType(Cap4kExtension::class.java)

    extension.project {
        basePackage.set("com.acme.demo")
    }
    extension.generators {
        flow { enabled.set(true) }
    }

    val error = assertThrows(IllegalArgumentException::class.java) {
        Cap4kProjectConfigFactory().build(project, extension)
    }

    assertEquals("flow generator requires enabled irAnalysis source.", error.message)
}
```

- [ ] **Step 2: Run the targeted tests and confirm they fail**

Run: `./gradlew :cap4k-plugin-pipeline-gradle:test --tests "com.only4.cap4k.plugin.pipeline.gradle.Cap4kProjectConfigFactoryTest" --rerun-tasks`

Expected: compilation fails because `Cap4kProjectConfigFactory` does not exist.

- [ ] **Step 3: Implement `Cap4kProjectConfigFactory` as the single config entry point**

```kotlin
package com.only4.cap4k.plugin.pipeline.gradle

import com.only4.cap4k.plugin.pipeline.api.ConflictPolicy
import com.only4.cap4k.plugin.pipeline.api.GeneratorConfig
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig
import com.only4.cap4k.plugin.pipeline.api.ProjectLayout
import com.only4.cap4k.plugin.pipeline.api.SourceConfig
import com.only4.cap4k.plugin.pipeline.api.TemplateConfig
import org.gradle.api.Project
import java.io.File

class Cap4kProjectConfigFactory {

    fun build(project: Project, extension: Cap4kExtension): ProjectConfig {
        val basePackage = extension.project.basePackage.required("project.basePackage")
        val modules = buildModules(extension)
        val sources = buildSources(project, extension)
        val generators = buildGenerators(project, extension)
        validateCrossDependencies(extension, sources, generators, modules)

        return ProjectConfig(
            basePackage = basePackage,
            layout = ProjectLayout.MULTI_MODULE,
            modules = modules,
            sources = sources,
            generators = generators,
            templates = TemplateConfig(
                preset = extension.templates.preset.orNull?.trim().orEmpty().ifBlank { "ddd-default" },
                overrideDirs = extension.templates.overrideDirs.files.map(File::getAbsolutePath).sorted(),
                conflictPolicy = ConflictPolicy.valueOf(
                    extension.templates.conflictPolicy.orNull?.trim().orEmpty().ifBlank { "SKIP" }
                ),
            ),
        )
    }

    private fun buildModules(extension: Cap4kExtension): Map<String, String> = buildMap {
        extension.project.applicationModulePath.optional()?.let { put("application", it) }
        extension.project.domainModulePath.optional()?.let { put("domain", it) }
        extension.project.adapterModulePath.optional()?.let { put("adapter", it) }
    }

    private fun buildSources(project: Project, extension: Cap4kExtension): Map<String, SourceConfig> = buildMap {
        if (extension.sources.designJson.enabled.get()) {
            val files = extension.sources.designJson.files.files.map { it.absolutePath }.sorted()
            require(files.isNotEmpty()) { "designJson source requires files." }
            put("design-json", SourceConfig(enabled = true, options = mapOf("files" to files)))
        }
        if (extension.sources.kspMetadata.enabled.get()) {
            put(
                "ksp-metadata",
                SourceConfig(
                    enabled = true,
                    options = mapOf("inputDir" to project.file(extension.sources.kspMetadata.inputDir.required("sources.kspMetadata.inputDir")).absolutePath),
                ),
            )
        }
        if (extension.sources.db.enabled.get()) {
            put(
                "db",
                SourceConfig(
                    enabled = true,
                    options = mapOf(
                        "url" to extension.sources.db.url.required("sources.db.url"),
                        "username" to extension.sources.db.username.required("sources.db.username"),
                        "password" to extension.sources.db.password.required("sources.db.password"),
                        "schema" to extension.sources.db.schema.optional().orEmpty(),
                        "includeTables" to extension.sources.db.includeTables.orNull.orEmpty(),
                        "excludeTables" to extension.sources.db.excludeTables.orNull.orEmpty(),
                    ),
                ),
            )
        }
        if (extension.sources.irAnalysis.enabled.get()) {
            val inputDirs = extension.sources.irAnalysis.inputDirs.files.map { it.absolutePath }.sorted()
            require(inputDirs.isNotEmpty()) { "irAnalysis source requires inputDirs." }
            put("ir-analysis", SourceConfig(enabled = true, options = mapOf("inputDirs" to inputDirs)))
        }
    }

    private fun buildGenerators(project: Project, extension: Cap4kExtension): Map<String, GeneratorConfig> = buildMap {
        if (extension.generators.design.enabled.get()) {
            put("design", GeneratorConfig(enabled = true))
        }
        if (extension.generators.aggregate.enabled.get()) {
            put("aggregate", GeneratorConfig(enabled = true))
        }
        if (extension.generators.flow.enabled.get()) {
            put("flow", GeneratorConfig(enabled = true, options = mapOf("outputDir" to extension.generators.flow.outputDir.required("generators.flow.outputDir"))))
        }
        if (extension.generators.drawingBoard.enabled.get()) {
            put("drawing-board", GeneratorConfig(enabled = true, options = mapOf("outputDir" to extension.generators.drawingBoard.outputDir.required("generators.drawingBoard.outputDir"))))
        }
    }
}
```

- [ ] **Step 4: Run the factory tests**

Run: `./gradlew :cap4k-plugin-pipeline-gradle:test --tests "com.only4.cap4k.plugin.pipeline.gradle.Cap4kProjectConfigFactoryTest" --rerun-tasks`

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit the config factory**

```bash
git add \
  cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kProjectConfigFactory.kt \
  cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kProjectConfigFactoryTest.kt
git commit -m "refactor: centralize cap4k gradle config assembly"
```

### Task 3: Rewire Plugin Tasks and Dependency Inference to the New DSL

**Files:**
- Modify: `cap4k/cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePlugin.kt`
- Modify: `cap4k/cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kPlanTask.kt`
- Modify: `cap4k/cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kGenerateTask.kt`

- [ ] **Step 1: Add the failing plugin-level tests for extension name and explicit task wiring**

```kotlin
@Test
fun `plugin registers cap4k extension instead of cap4kPipeline`() {
    val project = ProjectBuilder.builder().build()

    project.pluginManager.apply("com.only4.cap4k.plugin.pipeline")

    assert(project.extensions.findByName("cap4k") is Cap4kExtension)
    assert(project.extensions.findByName("cap4kPipeline") == null)
}
```

- [ ] **Step 2: Run the targeted tests and confirm they fail**

Run: `./gradlew :cap4k-plugin-pipeline-gradle:test --tests "com.only4.cap4k.plugin.pipeline.gradle.Cap4kProjectConfigFactoryTest" --rerun-tasks`

Expected: failure because the plugin still registers `cap4kPipeline` and task classes still depend on `PipelineExtension`.

- [ ] **Step 3: Rewire the plugin and task classes**

```kotlin
// PipelinePlugin.kt
class PipelinePlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val extension = project.extensions.create("cap4k", Cap4kExtension::class.java)
        val configFactory = Cap4kProjectConfigFactory()

        val planTask = project.tasks.register("cap4kPlan", Cap4kPlanTask::class.java) {
            it.group = "cap4k"
            it.description = "Plans Cap4k pipeline artifacts."
            it.extension = extension
            it.configFactory = configFactory
        }
        val generateTask = project.tasks.register("cap4kGenerate", Cap4kGenerateTask::class.java) {
            it.group = "cap4k"
            it.description = "Generates artifacts from the Cap4k pipeline."
            it.extension = extension
            it.configFactory = configFactory
        }

        project.gradle.projectsEvaluated {
            val config = configFactory.build(project, extension)
            val allProjects = project.rootProject.allprojects

            if ("design" in config.generators && "ksp-metadata" in config.sources) {
                val kspTasks = allProjects.mapNotNull { it.tasks.findByName("kspKotlin") }
                if (kspTasks.isNotEmpty()) {
                    planTask.configure { it.dependsOn(kspTasks) }
                    generateTask.configure { it.dependsOn(kspTasks) }
                }
            }

            if (("flow" in config.generators || "drawing-board" in config.generators) && "ir-analysis" in config.sources) {
                val compileTasks = allProjects.mapNotNull { it.tasks.findByName("compileKotlin") }
                if (compileTasks.isNotEmpty()) {
                    planTask.configure { it.dependsOn(compileTasks) }
                    generateTask.configure { it.dependsOn(compileTasks) }
                }
            }
        }
    }
}

// Cap4kPlanTask.kt / Cap4kGenerateTask.kt
@get:Internal
lateinit var extension: Cap4kExtension

@get:Internal
lateinit var configFactory: Cap4kProjectConfigFactory

val config = configFactory.build(project, extension)
```

- [ ] **Step 4: Run the Gradle module tests**

Run: `./gradlew :cap4k-plugin-pipeline-gradle:test --tests "com.only4.cap4k.plugin.pipeline.gradle.Cap4kProjectConfigFactoryTest" --rerun-tasks`

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit the plugin/task rewiring**

```bash
git add \
  cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePlugin.kt \
  cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kPlanTask.kt \
  cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kGenerateTask.kt
git commit -m "refactor: wire plugin tasks through cap4k config factory"
```

### Task 4: Rewrite Functional Fixtures and Prove the Breaking DSL End-to-End

**Files:**
- Modify: `cap4k/cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginFunctionalTest.kt`
- Modify: `cap4k/cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-sample/build.gradle.kts`
- Modify: `cap4k/cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-sample/build.gradle.kts`
- Modify: `cap4k/cap4k-plugin-pipeline-gradle/src/test/resources/functional/flow-sample/build.gradle.kts`
- Modify: `cap4k/cap4k-plugin-pipeline-gradle/src/test/resources/functional/flow-compile-sample/build.gradle.kts`
- Modify: `cap4k/cap4k-plugin-pipeline-gradle/src/test/resources/functional/drawing-board-sample/build.gradle.kts`

- [ ] **Step 1: Rewrite fixture build scripts to the new nested DSL**

```kotlin
plugins {
    id("com.only4.cap4k.plugin.pipeline")
}

cap4k {
    project {
        basePackage.set("com.acme.demo")
        applicationModulePath.set("demo-application")
        domainModulePath.set("demo-domain")
        adapterModulePath.set("demo-adapter")
    }

    sources {
        designJson {
            enabled.set(true)
            files.from("design/design.json")
        }
        kspMetadata {
            enabled.set(true)
            inputDir.set("domain/build/generated/ksp/main/resources/metadata")
        }
    }

    generators {
        design { enabled.set(true) }
    }

    templates {
        overrideDirs.from("codegen/templates")
    }
}
```

```kotlin
cap4k {
    project {
        basePackage.set("com.acme.demo")
        domainModulePath.set("demo-domain")
        adapterModulePath.set("demo-adapter")
    }
    sources {
        db {
            enabled.set(true)
            url.set("jdbc:h2:file:${layout.projectDirectory.file("schema")}"); username.set("sa"); password.set("")
        }
    }
    generators {
        aggregate { enabled.set(true) }
    }
}
```

- [ ] **Step 2: Update functional tests to mutate the new DSL instead of flat properties**

```kotlin
buildFile.writeText(
    buildFile.readText().replace(
        "        domainModulePath.set(\"demo-domain\")",
        "",
    )
)
```

```kotlin
buildFile.writeText(
    buildFile.readText().replace(
        "            enabled.set(true)",
        "            enabled.set(false)",
    )
)
```

Also update assertions to keep covering:

- design-only generation
- aggregate-only generation
- flow-only generation
- drawing-board generation
- flow compile dependency
- invalid config fail-fast

- [ ] **Step 3: Run the functional tests and confirm they pass with the new DSL**

Run: `./gradlew :cap4k-plugin-pipeline-gradle:test --tests "com.only4.cap4k.plugin.pipeline.gradle.PipelinePluginFunctionalTest" --rerun-tasks`

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Run the full Gradle module verification**

Run: `./gradlew :cap4k-plugin-pipeline-gradle:test --rerun-tasks`

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit the functional migration**

```bash
git add \
  cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginFunctionalTest.kt \
  cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-sample/build.gradle.kts \
  cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-sample/build.gradle.kts \
  cap4k-plugin-pipeline-gradle/src/test/resources/functional/flow-sample/build.gradle.kts \
  cap4k-plugin-pipeline-gradle/src/test/resources/functional/flow-compile-sample/build.gradle.kts \
  cap4k-plugin-pipeline-gradle/src/test/resources/functional/drawing-board-sample/build.gradle.kts
git commit -m "test: migrate pipeline gradle fixtures to cap4k DSL"
```

## Final Verification

- [ ] Run: `./gradlew :cap4k-plugin-pipeline-gradle:test --rerun-tasks`
- [ ] Run: `./gradlew :cap4k-plugin-pipeline-api:test :cap4k-plugin-pipeline-core:test :cap4k-plugin-pipeline-source-design-json:test :cap4k-plugin-pipeline-source-ksp-metadata:test :cap4k-plugin-pipeline-source-db:test :cap4k-plugin-pipeline-source-ir-analysis:test :cap4k-plugin-pipeline-generator-design:test :cap4k-plugin-pipeline-generator-aggregate:test :cap4k-plugin-pipeline-generator-flow:test :cap4k-plugin-pipeline-generator-drawing-board:test :cap4k-plugin-pipeline-renderer-pebble:test :cap4k-plugin-pipeline-gradle:test --rerun-tasks`
- [ ] Expected: all tasks succeed and functional fixtures use only `cap4k { ... }`
- [ ] Commit any last review-driven cleanup with: `git commit -m "refactor: consolidate cap4k gradle DSL"`

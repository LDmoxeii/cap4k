# Cap4k Bootstrap / Arch-Template Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add bootstrap as a separate capability with its own DSL and tasks, implement one `ddd-multi-module` preset, support bounded slot insertion, and prove representative legacy arch-template mapping without reviving the old JSON DSL.

**Architecture:** Bootstrap stays outside the canonical source/generator pipeline. The implementation adds a dedicated bootstrap config model, runner, preset-provider layer, and Gradle tasks while reusing the existing Pebble template resolver and filesystem exporter. Generated files land under a `<projectName>/` subtree inside the invoking project so `cap4kBootstrap` can run from a real Gradle consumer without overwriting the build that invokes it.

**Tech Stack:** Kotlin, Gradle plugin/TestKit, JUnit 5, Pebble renderer, existing `ConflictPolicy`, existing filesystem exporter

---

## Precondition

This plan assumes the roadmap has already been advanced to bootstrap migration:

- [mainline-roadmap.md](../mainline-roadmap.md)
- merged commit: `4ed4a0c` `docs: advance roadmap to bootstrap migration`

Implementation starts from code. The roadmap step for this slice has already been handled.

## File Structure

### Existing files to modify

- Modify: `settings.gradle.kts`
- Modify: `cap4k-plugin-pipeline-gradle/build.gradle.kts`
- Modify: `cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kExtension.kt`
- Modify: `cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePlugin.kt`
- Modify: `cap4k-plugin-pipeline-renderer-pebble/src/main/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PresetTemplateResolver.kt`

### New API and core files

- Create: `cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/BootstrapModels.kt`
- Create: `cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/BootstrapContracts.kt`
- Create: `cap4k-plugin-pipeline-api/src/test/kotlin/com/only4/cap4k/plugin/pipeline/api/BootstrapModelsTest.kt`
- Create: `cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultBootstrapRunner.kt`
- Create: `cap4k-plugin-pipeline-core/src/test/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultBootstrapRunnerTest.kt`

### New bootstrap capability module

- Create: `cap4k-plugin-pipeline-bootstrap/build.gradle.kts`
- Create: `cap4k-plugin-pipeline-bootstrap/src/main/kotlin/com/only4/cap4k/plugin/pipeline/bootstrap/DddMultiModuleBootstrapPresetProvider.kt`
- Create: `cap4k-plugin-pipeline-bootstrap/src/main/kotlin/com/only4/cap4k/plugin/pipeline/bootstrap/BootstrapSlotPlanner.kt`
- Create: `cap4k-plugin-pipeline-bootstrap/src/test/kotlin/com/only4/cap4k/plugin/pipeline/bootstrap/DddMultiModuleBootstrapPresetProviderTest.kt`
- Create: `cap4k-plugin-pipeline-bootstrap/src/test/kotlin/com/only4/cap4k/plugin/pipeline/bootstrap/LegacyArchTemplateMappingTest.kt`
- Create: `cap4k-plugin-pipeline-bootstrap/src/test/resources/legacy/ddd-multi-module-legacy-sample.json`

### New Gradle integration files

- Create: `cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kBootstrapConfigFactory.kt`
- Create: `cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kBootstrapPlanTask.kt`
- Create: `cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kBootstrapTask.kt`
- Create: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kBootstrapConfigFactoryTest.kt`
- Create: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginBootstrapFunctionalTest.kt`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginTest.kt`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/FunctionalFixtureSupport.kt`

### New renderer files and preset assets

- Create: `cap4k-plugin-pipeline-renderer-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/renderer/api/BootstrapRenderer.kt`
- Create: `cap4k-plugin-pipeline-renderer-pebble/src/main/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PebbleBootstrapRenderer.kt`
- Create: `cap4k-plugin-pipeline-renderer-pebble/src/test/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PebbleBootstrapRendererTest.kt`
- Create: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default-bootstrap/bootstrap/root/settings.gradle.kts.peb`
- Create: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default-bootstrap/bootstrap/root/build.gradle.kts.peb`
- Create: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default-bootstrap/bootstrap/module/domain-build.gradle.kts.peb`
- Create: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default-bootstrap/bootstrap/module/application-build.gradle.kts.peb`
- Create: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default-bootstrap/bootstrap/module/adapter-build.gradle.kts.peb`
- Create: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default-bootstrap/bootstrap/module/package-marker.kt.peb`

### New functional fixtures

- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/bootstrap-sample/...`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/bootstrap-override-sample/...`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/bootstrap-invalid-slot-sample/...`

## Task 1: Add Bootstrap API, DSL, and Config Factory

**Files:**
- Modify: `cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kExtension.kt`
- Create: `cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/BootstrapModels.kt`
- Create: `cap4k-plugin-pipeline-api/src/test/kotlin/com/only4/cap4k/plugin/pipeline/api/BootstrapModelsTest.kt`
- Create: `cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kBootstrapConfigFactory.kt`
- Create: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kBootstrapConfigFactoryTest.kt`

- [ ] **Step 1: Write the failing API and config tests**

```kotlin
// cap4k-plugin-pipeline-api/src/test/kotlin/com/only4/cap4k/plugin/pipeline/api/BootstrapModelsTest.kt
class BootstrapModelsTest {

    @Test
    fun `bootstrap slot id exposes bounded role-aware naming`() {
        val binding = BootstrapSlotBinding(
            kind = BootstrapSlotKind.MODULE_PACKAGE,
            role = "domain",
            sourceDir = "codegen/bootstrap-slots/domain-package"
        )

        assertEquals("module-package:domain", binding.slotId)
    }

    @Test
    fun `bootstrap plan item requires template id or slot source path`() {
        val error = assertThrows<IllegalArgumentException> {
            BootstrapPlanItem(
                presetId = "ddd-multi-module",
                outputPath = "demo/settings.gradle.kts",
                conflictPolicy = ConflictPolicy.FAIL,
            )
        }

        assertTrue(error.message!!.contains("templateId or sourcePath"))
    }
}

// cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kBootstrapConfigFactoryTest.kt
class Cap4kBootstrapConfigFactoryTest {

    @Test
    fun `build returns bootstrap config for ddd multi module preset`() {
        val project = ProjectBuilder.builder().build()
        val extension = project.extensions.create("cap4k", Cap4kExtension::class.java)

        extension.bootstrap.enabled.set(true)
        extension.bootstrap.preset.set("ddd-multi-module")
        extension.bootstrap.projectName.set("only-danmuku")
        extension.bootstrap.basePackage.set("edu.only4.danmuku")
        extension.bootstrap.modules.domainModuleName.set("only-danmuku-domain")
        extension.bootstrap.modules.applicationModuleName.set("only-danmuku-application")
        extension.bootstrap.modules.adapterModuleName.set("only-danmuku-adapter")
        extension.bootstrap.slots.root.from("codegen/bootstrap-slots/root")
        extension.bootstrap.slots.modulePackage("domain").from("codegen/bootstrap-slots/domain-package")

        val config = Cap4kBootstrapConfigFactory().build(project, extension)

        assertEquals("ddd-multi-module", config.preset)
        assertEquals("only-danmuku", config.projectName)
        assertEquals("edu.only4.danmuku", config.basePackage)
        assertEquals(ConflictPolicy.FAIL, config.conflictPolicy)
        assertEquals("only-danmuku-domain", config.modules.domainModuleName)
        assertEquals(listOf("root", "module-package:domain"), config.slots.map { it.slotId })
    }

    @Test
    fun `build fails when bootstrap enabled without project name`() {
        val project = ProjectBuilder.builder().build()
        val extension = project.extensions.create("cap4k", Cap4kExtension::class.java)

        extension.bootstrap.enabled.set(true)
        extension.bootstrap.preset.set("ddd-multi-module")
        extension.bootstrap.basePackage.set("edu.only4.danmuku")

        val error = assertThrows<IllegalArgumentException> {
            Cap4kBootstrapConfigFactory().build(project, extension)
        }

        assertTrue(error.message!!.contains("bootstrap.projectName is required"))
    }

    @Test
    fun `build fails when slot role is outside fixed module roles`() {
        val project = ProjectBuilder.builder().build()
        val extension = project.extensions.create("cap4k", Cap4kExtension::class.java)

        extension.bootstrap.enabled.set(true)
        extension.bootstrap.preset.set("ddd-multi-module")
        extension.bootstrap.projectName.set("only-danmuku")
        extension.bootstrap.basePackage.set("edu.only4.danmuku")
        extension.bootstrap.modules.domainModuleName.set("only-danmuku-domain")
        extension.bootstrap.modules.applicationModuleName.set("only-danmuku-application")
        extension.bootstrap.modules.adapterModuleName.set("only-danmuku-adapter")
        extension.bootstrap.slots.moduleRoot("start").from("codegen/bootstrap-slots/start-root")

        val error = assertThrows<IllegalArgumentException> {
            Cap4kBootstrapConfigFactory().build(project, extension)
        }

        assertTrue(error.message!!.contains("unsupported bootstrap slot role"))
    }
}
```

- [ ] **Step 2: Run the targeted tests and verify they fail**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-api:test :cap4k-plugin-pipeline-gradle:test --tests "*BootstrapModelsTest" --tests "*Cap4kBootstrapConfigFactoryTest"
```

Expected:

- `BootstrapModelsTest` fails because `BootstrapSlotBinding` and `BootstrapPlanItem` do not exist.
- `Cap4kBootstrapConfigFactoryTest` fails because `extension.bootstrap` and `Cap4kBootstrapConfigFactory` do not exist.

- [ ] **Step 3: Implement the bootstrap models and DSL**

```kotlin
// cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/BootstrapModels.kt
data class BootstrapConfig(
    val preset: String,
    val projectName: String,
    val basePackage: String,
    val modules: BootstrapModulesConfig,
    val templates: BootstrapTemplateConfig,
    val slots: List<BootstrapSlotBinding>,
    val conflictPolicy: ConflictPolicy,
)

data class BootstrapModulesConfig(
    val domainModuleName: String,
    val applicationModuleName: String,
    val adapterModuleName: String,
)

data class BootstrapTemplateConfig(
    val preset: String,
    val overrideDirs: List<String>,
)

enum class BootstrapSlotKind {
    ROOT,
    BUILD_LOGIC,
    MODULE_ROOT,
    MODULE_PACKAGE,
}

data class BootstrapSlotBinding(
    val kind: BootstrapSlotKind,
    val role: String? = null,
    val sourceDir: String,
) {
    val slotId: String =
        when (kind) {
            BootstrapSlotKind.ROOT -> "root"
            BootstrapSlotKind.BUILD_LOGIC -> "build-logic"
            BootstrapSlotKind.MODULE_ROOT -> "module-root:${requireNotNull(role)}"
            BootstrapSlotKind.MODULE_PACKAGE -> "module-package:${requireNotNull(role)}"
        }
}

data class BootstrapPlanItem(
    val presetId: String,
    val outputPath: String,
    val conflictPolicy: ConflictPolicy,
    val templateId: String? = null,
    val sourcePath: String? = null,
    val slotId: String? = null,
    val context: Map<String, Any?> = emptyMap(),
) {
    init {
        require(!templateId.isNullOrBlank() || !sourcePath.isNullOrBlank()) {
            "BootstrapPlanItem requires templateId or sourcePath."
        }
    }
}

data class BootstrapPlanReport(
    val items: List<BootstrapPlanItem>,
)
```

```kotlin
// cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kExtension.kt
open class Cap4kExtension @Inject constructor(objects: ObjectFactory) {
    val bootstrap: Cap4kBootstrapExtension = objects.newInstance(Cap4kBootstrapExtension::class.java)
    // keep existing project/types/sources/generators/templates

    fun bootstrap(block: Cap4kBootstrapExtension.() -> Unit) {
        bootstrap.block()
    }
}

open class Cap4kBootstrapExtension @Inject constructor(objects: ObjectFactory) {
    val enabled: Property<Boolean> = objects.property(Boolean::class.java).convention(false)
    val preset: Property<String> = objects.property(String::class.java).convention("ddd-multi-module")
    val projectName: Property<String> = objects.property(String::class.java)
    val basePackage: Property<String> = objects.property(String::class.java)
    val modules: Cap4kBootstrapModulesExtension =
        objects.newInstance(Cap4kBootstrapModulesExtension::class.java)
    val templates: Cap4kBootstrapTemplatesExtension =
        objects.newInstance(Cap4kBootstrapTemplatesExtension::class.java)
    val slots: Cap4kBootstrapSlotsExtension =
        objects.newInstance(Cap4kBootstrapSlotsExtension::class.java, objects)
    val conflictPolicy: Property<String> = objects.property(String::class.java).convention("FAIL")
}

open class Cap4kBootstrapModulesExtension @Inject constructor(objects: ObjectFactory) {
    val domainModuleName: Property<String> = objects.property(String::class.java)
    val applicationModuleName: Property<String> = objects.property(String::class.java)
    val adapterModuleName: Property<String> = objects.property(String::class.java)
}

open class Cap4kBootstrapTemplatesExtension @Inject constructor(objects: ObjectFactory) {
    val preset: Property<String> = objects.property(String::class.java).convention("ddd-default-bootstrap")
    val overrideDirs: ConfigurableFileCollection = objects.fileCollection()
}
```

```kotlin
// cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kBootstrapConfigFactory.kt
class Cap4kBootstrapConfigFactory {

    fun build(project: Project, extension: Cap4kExtension): BootstrapConfig {
        require(extension.bootstrap.enabled.get()) {
            "bootstrap.enabled must be true to run bootstrap tasks."
        }

        val preset = extension.bootstrap.preset.required("bootstrap.preset")
        require(preset == "ddd-multi-module") {
            "unsupported bootstrap preset: $preset"
        }

        val slots = extension.bootstrap.slots.bindings(project)
        slots.forEach { binding ->
            if (binding.role != null) {
                require(binding.role in setOf("domain", "application", "adapter")) {
                    "unsupported bootstrap slot role: ${binding.role}"
                }
            }
        }

        return BootstrapConfig(
            preset = preset,
            projectName = extension.bootstrap.projectName.required("bootstrap.projectName"),
            basePackage = extension.bootstrap.basePackage.required("bootstrap.basePackage"),
            modules = BootstrapModulesConfig(
                domainModuleName = extension.bootstrap.modules.domainModuleName.required("bootstrap.modules.domainModuleName"),
                applicationModuleName = extension.bootstrap.modules.applicationModuleName.required("bootstrap.modules.applicationModuleName"),
                adapterModuleName = extension.bootstrap.modules.adapterModuleName.required("bootstrap.modules.adapterModuleName"),
            ),
            templates = BootstrapTemplateConfig(
                preset = extension.bootstrap.templates.preset.orNull?.trim().orEmpty().ifEmpty { "ddd-default-bootstrap" },
                overrideDirs = extension.bootstrap.templates.overrideDirs.files.map(File::getAbsolutePath).sorted(),
            ),
            slots = slots,
            conflictPolicy = ConflictPolicy.valueOf(
                extension.bootstrap.conflictPolicy.orNull?.trim().orEmpty().ifEmpty { "FAIL" }
            ),
        )
    }
}
```

- [ ] **Step 4: Run the targeted tests and verify they pass**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-api:test :cap4k-plugin-pipeline-gradle:test --tests "*BootstrapModelsTest" --tests "*Cap4kBootstrapConfigFactoryTest"
```

Expected:

- `BootstrapModelsTest` passes.
- `Cap4kBootstrapConfigFactoryTest` passes.

- [ ] **Step 5: Commit the API and config slice**

```powershell
git add cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/BootstrapModels.kt `
        cap4k-plugin-pipeline-api/src/test/kotlin/com/only4/cap4k/plugin/pipeline/api/BootstrapModelsTest.kt `
        cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kExtension.kt `
        cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kBootstrapConfigFactory.kt `
        cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kBootstrapConfigFactoryTest.kt
git commit -m "feat: add bootstrap config model"
```

### Task 2: Add Bootstrap Runner, Renderer, and Gradle Tasks

**Files:**
- Create: `cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/BootstrapContracts.kt`
- Create: `cap4k-plugin-pipeline-renderer-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/renderer/api/BootstrapRenderer.kt`
- Create: `cap4k-plugin-pipeline-renderer-pebble/src/main/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PebbleBootstrapRenderer.kt`
- Create: `cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultBootstrapRunner.kt`
- Create: `cap4k-plugin-pipeline-core/src/test/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultBootstrapRunnerTest.kt`
- Create: `cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kBootstrapPlanTask.kt`
- Create: `cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kBootstrapTask.kt`
- Modify: `cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePlugin.kt`
- Modify: `cap4k-plugin-pipeline-gradle/build.gradle.kts`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginTest.kt`

- [ ] **Step 1: Write the failing runner and plugin wiring tests**

```kotlin
// cap4k-plugin-pipeline-core/src/test/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultBootstrapRunnerTest.kt
class DefaultBootstrapRunnerTest {

    @Test
    fun `run fails when preset has no registered provider`() {
        val config = BootstrapConfig(
            preset = "ddd-multi-module",
            projectName = "demo",
            basePackage = "com.acme.demo",
            modules = BootstrapModulesConfig("demo-domain", "demo-application", "demo-adapter"),
            templates = BootstrapTemplateConfig("ddd-default-bootstrap", emptyList()),
            slots = emptyList(),
            conflictPolicy = ConflictPolicy.FAIL,
        )

        val runner = DefaultBootstrapRunner(
            providers = emptyList(),
            renderer = object : BootstrapRenderer {
                override fun render(planItems: List<BootstrapPlanItem>): List<RenderedArtifact> = emptyList()
            },
            exporter = NoopArtifactExporter(),
        )

        val error = assertThrows<IllegalArgumentException> {
            runner.run(config)
        }

        assertTrue(error.message!!.contains("no registered bootstrap provider"))
    }
}

// cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginTest.kt
@Test
fun `plugin registers bootstrap tasks`() {
    val project = ProjectBuilder.builder().build()
    project.pluginManager.apply("com.only4.cap4k.plugin.pipeline")

    assertNotNull(project.tasks.findByName("cap4kBootstrapPlan"))
    assertNotNull(project.tasks.findByName("cap4kBootstrap"))
}
```

- [ ] **Step 2: Run the targeted tests and verify they fail**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-core:test :cap4k-plugin-pipeline-gradle:test --tests "*DefaultBootstrapRunnerTest" --tests "*plugin registers bootstrap tasks*"
```

Expected:

- `DefaultBootstrapRunnerTest` fails because bootstrap runner/contracts do not exist.
- `PipelinePluginTest` fails because bootstrap tasks are not registered.

- [ ] **Step 3: Implement the bootstrap runner, renderer interface, and tasks**

```kotlin
// cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/BootstrapContracts.kt
interface BootstrapPresetProvider {
    val presetId: String
    fun plan(config: BootstrapConfig): List<BootstrapPlanItem>
}

interface BootstrapRunner {
    fun run(config: BootstrapConfig): BootstrapResult
}

data class BootstrapResult(
    val planItems: List<BootstrapPlanItem>,
    val renderedArtifacts: List<RenderedArtifact>,
    val writtenPaths: List<String>,
)
```

```kotlin
// cap4k-plugin-pipeline-renderer-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/renderer/api/BootstrapRenderer.kt
interface BootstrapRenderer {
    fun render(planItems: List<BootstrapPlanItem>): List<RenderedArtifact>
}
```

```kotlin
// cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultBootstrapRunner.kt
class DefaultBootstrapRunner(
    private val providers: List<BootstrapPresetProvider>,
    private val renderer: BootstrapRenderer,
    private val exporter: ArtifactExporter,
) : BootstrapRunner {
    override fun run(config: BootstrapConfig): BootstrapResult {
        val provider = providers.find { it.presetId == config.preset }
            ?: throw IllegalArgumentException("bootstrap preset has no registered provider: ${config.preset}")

        val planItems = provider.plan(config)
        val renderedArtifacts = renderer.render(planItems)
        val writtenPaths = exporter.export(renderedArtifacts)
        return BootstrapResult(planItems, renderedArtifacts, writtenPaths)
    }
}
```

```kotlin
// cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kBootstrapPlanTask.kt
abstract class Cap4kBootstrapPlanTask : DefaultTask() {
    @get:Internal
    lateinit var extension: Cap4kExtension

    @get:Internal
    lateinit var configFactory: Cap4kBootstrapConfigFactory

    @TaskAction
    fun runPlan() {
        val config = configFactory.build(project, extension)
        val outputFile = project.layout.buildDirectory.file("cap4k/bootstrap-plan.json").get().asFile
        outputFile.parentFile.mkdirs()
        val result = buildBootstrapRunner(project, exportEnabled = false).run(config)
        outputFile.writeText(
            GsonBuilder().setPrettyPrinting().serializeNulls().create().toJson(
                BootstrapPlanReport(result.planItems)
            )
        )
    }
}

abstract class Cap4kBootstrapTask : DefaultTask() {
    @get:Internal
    lateinit var extension: Cap4kExtension

    @get:Internal
    lateinit var configFactory: Cap4kBootstrapConfigFactory

    @TaskAction
    fun generate() {
        val config = configFactory.build(project, extension)
        buildBootstrapRunner(project, exportEnabled = true).run(config)
    }
}
```

```kotlin
// cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePlugin.kt
override fun apply(project: Project) {
    val extension = project.extensions.create("cap4k", Cap4kExtension::class.java)
    val configFactory = Cap4kProjectConfigFactory()
    val bootstrapConfigFactory = Cap4kBootstrapConfigFactory()

    project.tasks.register("cap4kBootstrapPlan", Cap4kBootstrapPlanTask::class.java) { task ->
        task.group = "cap4k"
        task.description = "Plans bootstrap skeleton files."
        task.extension = extension
        task.configFactory = bootstrapConfigFactory
    }
    project.tasks.register("cap4kBootstrap", Cap4kBootstrapTask::class.java) { task ->
        task.group = "cap4k"
        task.description = "Generates bootstrap skeleton files."
        task.extension = extension
        task.configFactory = bootstrapConfigFactory
    }

    // keep existing cap4kPlan/cap4kGenerate registration
}
```

- [ ] **Step 4: Run the targeted tests and verify they pass**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-core:test :cap4k-plugin-pipeline-gradle:test --tests "*DefaultBootstrapRunnerTest" --tests "*plugin registers bootstrap tasks*"
```

Expected:

- bootstrap runner tests pass
- plugin task registration tests pass

- [ ] **Step 5: Commit the runner and task wiring**

```powershell
git add cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/BootstrapContracts.kt `
        cap4k-plugin-pipeline-renderer-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/renderer/api/BootstrapRenderer.kt `
        cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultBootstrapRunner.kt `
        cap4k-plugin-pipeline-core/src/test/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultBootstrapRunnerTest.kt `
        cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kBootstrapPlanTask.kt `
        cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kBootstrapTask.kt `
        cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePlugin.kt `
        cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginTest.kt
git commit -m "feat: add bootstrap runner and tasks"
```

### Task 3: Implement the `ddd-multi-module` Preset Provider and Bounded Slot Planning

**Files:**
- Modify: `settings.gradle.kts`
- Modify: `cap4k-plugin-pipeline-gradle/build.gradle.kts`
- Create: `cap4k-plugin-pipeline-bootstrap/build.gradle.kts`
- Create: `cap4k-plugin-pipeline-bootstrap/src/main/kotlin/com/only4/cap4k/plugin/pipeline/bootstrap/DddMultiModuleBootstrapPresetProvider.kt`
- Create: `cap4k-plugin-pipeline-bootstrap/src/main/kotlin/com/only4/cap4k/plugin/pipeline/bootstrap/BootstrapSlotPlanner.kt`
- Create: `cap4k-plugin-pipeline-bootstrap/src/test/kotlin/com/only4/cap4k/plugin/pipeline/bootstrap/DddMultiModuleBootstrapPresetProviderTest.kt`
- Create: `cap4k-plugin-pipeline-bootstrap/src/test/kotlin/com/only4/cap4k/plugin/pipeline/bootstrap/LegacyArchTemplateMappingTest.kt`
- Create: `cap4k-plugin-pipeline-bootstrap/src/test/resources/legacy/ddd-multi-module-legacy-sample.json`

- [ ] **Step 1: Write the failing preset-provider and legacy-mapping tests**

```kotlin
// cap4k-plugin-pipeline-bootstrap/src/test/kotlin/com/only4/cap4k/plugin/pipeline/bootstrap/DddMultiModuleBootstrapPresetProviderTest.kt
class DddMultiModuleBootstrapPresetProviderTest {

    private val config = BootstrapConfig(
        preset = "ddd-multi-module",
        projectName = "only-danmuku",
        basePackage = "edu.only4.danmuku",
        modules = BootstrapModulesConfig(
            domainModuleName = "only-danmuku-domain",
            applicationModuleName = "only-danmuku-application",
            adapterModuleName = "only-danmuku-adapter",
        ),
        templates = BootstrapTemplateConfig("ddd-default-bootstrap", emptyList()),
        slots = listOf(
            BootstrapSlotBinding(BootstrapSlotKind.ROOT, sourceDir = "src/test/resources/slots/root"),
            BootstrapSlotBinding(BootstrapSlotKind.MODULE_PACKAGE, role = "domain", sourceDir = "src/test/resources/slots/domain-package"),
        ),
        conflictPolicy = ConflictPolicy.FAIL,
    )

    @Test
    fun `plan emits fixed root and module templates under project name subtree`() {
        val items = DddMultiModuleBootstrapPresetProvider().plan(config)

        assertTrue(items.any { it.templateId == "bootstrap/root/settings.gradle.kts.peb" && it.outputPath == "only-danmuku/settings.gradle.kts" })
        assertTrue(items.any { it.templateId == "bootstrap/root/build.gradle.kts.peb" && it.outputPath == "only-danmuku/build.gradle.kts" })
        assertTrue(items.any { it.templateId == "bootstrap/module/domain-build.gradle.kts.peb" && it.outputPath == "only-danmuku/only-danmuku-domain/build.gradle.kts" })
        assertTrue(items.any { it.templateId == "bootstrap/module/package-marker.kt.peb" && it.outputPath.contains("src/main/kotlin/edu/only4/danmuku/domain") })
    }

    @Test
    fun `plan emits slot items with slot attribution`() {
        val items = DddMultiModuleBootstrapPresetProvider().plan(config)

        assertTrue(items.any { it.slotId == "root" && it.sourcePath!!.endsWith("slots/root/README.md.peb") })
        assertTrue(items.any { it.slotId == "module-package:domain" && it.outputPath.contains("only-danmuku-domain/src/main/kotlin/edu/only4/danmuku") })
    }
}

// cap4k-plugin-pipeline-bootstrap/src/test/kotlin/com/only4/cap4k/plugin/pipeline/bootstrap/LegacyArchTemplateMappingTest.kt
class LegacyArchTemplateMappingTest {

    @Test
    fun `legacy sample separates structural nodes from generator routing`() {
        val sample = LegacyArchTemplateMappingSamples.load("legacy/ddd-multi-module-legacy-sample.json")

        val mapping = LegacyArchTemplateMapper.classify(sample)

        assertTrue(mapping.structuralNodes.any { it.contains("{{ artifactId }}-domain") })
        assertTrue(mapping.fixedTemplateFiles.any { it.endsWith("template/settings.gradle.kts.peb") })
        assertTrue(mapping.routingTags.contains("query"))
        assertTrue(mapping.routingTags.contains("query_handler"))
        assertTrue(mapping.routingTags.contains("domain_event"))
    }
}
```

- [ ] **Step 2: Run the new module tests and verify they fail**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-bootstrap:test --tests "*DddMultiModuleBootstrapPresetProviderTest" --tests "*LegacyArchTemplateMappingTest"
```

Expected:

- build fails because the new bootstrap module and provider do not exist yet.

- [ ] **Step 3: Implement the preset provider, slot planner, and characterization helper**

```kotlin
// cap4k-plugin-pipeline-bootstrap/src/main/kotlin/com/only4/cap4k/plugin/pipeline/bootstrap/BootstrapSlotPlanner.kt
internal object BootstrapSlotPlanner {

    fun plan(config: BootstrapConfig): List<BootstrapPlanItem> =
        config.slots.flatMap { binding ->
            val root = Path.of(binding.sourceDir)
            if (!Files.exists(root)) return@flatMap emptyList()

            Files.walk(root)
                .filter { Files.isRegularFile(it) }
                .map { source ->
                    val relative = root.relativize(source).invariantSeparatorsPathString
                    val renderedRelative = renderRelativePath(relative, config)
                    BootstrapPlanItem(
                        presetId = config.preset,
                        sourcePath = source.toString(),
                        slotId = binding.slotId,
                        outputPath = resolveSlotOutputPath(binding, renderedRelative, config),
                        conflictPolicy = config.conflictPolicy,
                        context = bootstrapContext(config),
                    )
                }
                .toList()
        }
}
```

```kotlin
// cap4k-plugin-pipeline-bootstrap/src/main/kotlin/com/only4/cap4k/plugin/pipeline/bootstrap/DddMultiModuleBootstrapPresetProvider.kt
class DddMultiModuleBootstrapPresetProvider : BootstrapPresetProvider {
    override val presetId: String = "ddd-multi-module"

    override fun plan(config: BootstrapConfig): List<BootstrapPlanItem> {
        val context = bootstrapContext(config)
        return buildList {
            add(fixed("bootstrap/root/settings.gradle.kts.peb", "${config.projectName}/settings.gradle.kts", config, context))
            add(fixed("bootstrap/root/build.gradle.kts.peb", "${config.projectName}/build.gradle.kts", config, context))
            add(fixed("bootstrap/module/domain-build.gradle.kts.peb", "${config.projectName}/${config.modules.domainModuleName}/build.gradle.kts", config, context + mapOf("moduleRole" to "domain")))
            add(fixed("bootstrap/module/application-build.gradle.kts.peb", "${config.projectName}/${config.modules.applicationModuleName}/build.gradle.kts", config, context + mapOf("moduleRole" to "application")))
            add(fixed("bootstrap/module/adapter-build.gradle.kts.peb", "${config.projectName}/${config.modules.adapterModuleName}/build.gradle.kts", config, context + mapOf("moduleRole" to "adapter")))
            addAll(packageMarkers(config, context))
            addAll(BootstrapSlotPlanner.plan(config))
        }
    }
}
```

- [ ] **Step 4: Run the preset-provider tests and verify they pass**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-bootstrap:test --tests "*DddMultiModuleBootstrapPresetProviderTest" --tests "*LegacyArchTemplateMappingTest"
```

Expected:

- fixed root/module/package-scaffold plan assertions pass
- slot attribution assertions pass
- legacy mapping characterization passes

- [ ] **Step 5: Commit the preset-planning slice**

```powershell
git add settings.gradle.kts `
        cap4k-plugin-pipeline-gradle/build.gradle.kts `
        cap4k-plugin-pipeline-bootstrap/build.gradle.kts `
        cap4k-plugin-pipeline-bootstrap/src/main/kotlin/com/only4/cap4k/plugin/pipeline/bootstrap/DddMultiModuleBootstrapPresetProvider.kt `
        cap4k-plugin-pipeline-bootstrap/src/main/kotlin/com/only4/cap4k/plugin/pipeline/bootstrap/BootstrapSlotPlanner.kt `
        cap4k-plugin-pipeline-bootstrap/src/test/kotlin/com/only4/cap4k/plugin/pipeline/bootstrap/DddMultiModuleBootstrapPresetProviderTest.kt `
        cap4k-plugin-pipeline-bootstrap/src/test/kotlin/com/only4/cap4k/plugin/pipeline/bootstrap/LegacyArchTemplateMappingTest.kt `
        cap4k-plugin-pipeline-bootstrap/src/test/resources/legacy/ddd-multi-module-legacy-sample.json
git commit -m "feat: add bootstrap preset planning"
```

### Task 4: Add Pebble Bootstrap Rendering and Fixed Preset Templates

**Files:**
- Modify: `cap4k-plugin-pipeline-renderer-pebble/src/main/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PresetTemplateResolver.kt`
- Create: `cap4k-plugin-pipeline-renderer-pebble/src/main/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PebbleBootstrapRenderer.kt`
- Create: `cap4k-plugin-pipeline-renderer-pebble/src/test/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PebbleBootstrapRendererTest.kt`
- Create: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default-bootstrap/bootstrap/root/settings.gradle.kts.peb`
- Create: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default-bootstrap/bootstrap/root/build.gradle.kts.peb`
- Create: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default-bootstrap/bootstrap/module/domain-build.gradle.kts.peb`
- Create: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default-bootstrap/bootstrap/module/application-build.gradle.kts.peb`
- Create: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default-bootstrap/bootstrap/module/adapter-build.gradle.kts.peb`
- Create: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default-bootstrap/bootstrap/module/package-marker.kt.peb`

- [ ] **Step 1: Write the failing renderer tests**

```kotlin
class PebbleBootstrapRendererTest {

    @Test
    fun `render resolves fixed preset template ids through bootstrap preset resolver`() {
        val item = BootstrapPlanItem(
            presetId = "ddd-multi-module",
            templateId = "bootstrap/root/settings.gradle.kts.peb",
            outputPath = "only-danmuku/settings.gradle.kts",
            conflictPolicy = ConflictPolicy.FAIL,
            context = mapOf(
                "projectName" to "only-danmuku",
                "domainModuleName" to "only-danmuku-domain",
                "applicationModuleName" to "only-danmuku-application",
                "adapterModuleName" to "only-danmuku-adapter",
            ),
        )

        val renderer = PebbleBootstrapRenderer(
            PresetTemplateResolver("ddd-default-bootstrap", emptyList())
        )

        val artifact = renderer.render(listOf(item)).single()

        assertTrue(artifact.content.contains("include(\":only-danmuku-domain\")"))
        assertTrue(artifact.content.contains("include(\":only-danmuku-application\")"))
        assertTrue(artifact.content.contains("include(\":only-danmuku-adapter\")"))
    }

    @Test
    fun `render supports slot source files through absolute source path`() {
        val tempFile = Files.createTempFile("bootstrap-slot", ".peb")
        tempFile.writeText("module={{ domainModuleName }}")

        val item = BootstrapPlanItem(
            presetId = "ddd-multi-module",
            sourcePath = tempFile.toString(),
            outputPath = "only-danmuku/README.md",
            conflictPolicy = ConflictPolicy.FAIL,
            slotId = "root",
            context = mapOf("domainModuleName" to "only-danmuku-domain"),
        )

        val renderer = PebbleBootstrapRenderer(
            PresetTemplateResolver("ddd-default-bootstrap", emptyList())
        )

        val artifact = renderer.render(listOf(item)).single()
        assertEquals("module=only-danmuku-domain", artifact.content)
    }
}
```

- [ ] **Step 2: Run the renderer tests and verify they fail**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-renderer-pebble:test --tests "*PebbleBootstrapRendererTest"
```

Expected:

- tests fail because `PebbleBootstrapRenderer` and bootstrap preset resources do not exist.

- [ ] **Step 3: Implement renderer support and the fixed preset templates**

```kotlin
// cap4k-plugin-pipeline-renderer-pebble/src/main/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PresetTemplateResolver.kt
override fun resolve(templateId: String): String {
    if (templateId.isNotBlank()) {
        val directFile = File(templateId)
        if (directFile.isAbsolute && directFile.exists()) {
            return directFile.readText()
        }
    }

    for (dir in overrideDirs) {
        val file = File(dir, templateId)
        if (file.exists()) {
            return file.readText()
        }
    }

    val resourcePath = "presets/$preset/$templateId"
    val resource = javaClass.classLoader.getResource(resourcePath)
        ?: error("Template not found: $resourcePath")
    return resource.readText()
}
```

```kotlin
// cap4k-plugin-pipeline-renderer-pebble/src/main/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PebbleBootstrapRenderer.kt
class PebbleBootstrapRenderer(
    private val templateResolver: TemplateResolver,
) : BootstrapRenderer {
    private val engine = PebbleEngine.Builder()
        .loader(StringLoader())
        .newLineTrimming(false)
        .build()

    override fun render(planItems: List<BootstrapPlanItem>): List<RenderedArtifact> =
        planItems.map { item ->
            val templateText = templateResolver.resolve(item.sourcePath ?: item.templateId!!)
            val template = engine.getLiteralTemplate(templateText)
            val writer = StringWriter()
            template.evaluate(writer, item.context)
            RenderedArtifact(item.outputPath, writer.toString(), item.conflictPolicy)
        }
}
```

```kotlin
// cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default-bootstrap/bootstrap/root/settings.gradle.kts.peb
rootProject.name = "{{ projectName }}"

include(":{{ domainModuleName }}")
include(":{{ applicationModuleName }}")
include(":{{ adapterModuleName }}")
```

```kotlin
// cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default-bootstrap/bootstrap/module/package-marker.kt.peb
package {{ packageName }}

object {{ markerName }}
```

- [ ] **Step 4: Run the renderer tests and verify they pass**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-renderer-pebble:test --tests "*PebbleBootstrapRendererTest"
```

Expected:

- fixed preset template rendering passes
- slot-source absolute-path rendering passes

- [ ] **Step 5: Commit the renderer and preset assets**

```powershell
git add cap4k-plugin-pipeline-renderer-pebble/src/main/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PresetTemplateResolver.kt `
        cap4k-plugin-pipeline-renderer-pebble/src/main/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PebbleBootstrapRenderer.kt `
        cap4k-plugin-pipeline-renderer-pebble/src/test/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PebbleBootstrapRendererTest.kt `
        cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default-bootstrap/bootstrap/root/settings.gradle.kts.peb `
        cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default-bootstrap/bootstrap/root/build.gradle.kts.peb `
        cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default-bootstrap/bootstrap/module/domain-build.gradle.kts.peb `
        cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default-bootstrap/bootstrap/module/application-build.gradle.kts.peb `
        cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default-bootstrap/bootstrap/module/adapter-build.gradle.kts.peb `
        cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default-bootstrap/bootstrap/module/package-marker.kt.peb
git commit -m "feat: add bootstrap preset templates"
```

### Task 5: Add Functional Bootstrap Fixtures and End-to-End Validation

**Files:**
- Modify: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/FunctionalFixtureSupport.kt`
- Create: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginBootstrapFunctionalTest.kt`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/bootstrap-sample/build.gradle.kts`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/bootstrap-sample/settings.gradle.kts`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/bootstrap-sample/codegen/bootstrap-slots/...`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/bootstrap-override-sample/...`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/bootstrap-invalid-slot-sample/...`

- [ ] **Step 1: Write the failing functional tests**

```kotlin
class PipelinePluginBootstrapFunctionalTest {

    @OptIn(ExperimentalPathApi::class)
    @Test
    fun `cap4kBootstrapPlan writes bootstrap plan json with fixed files and slots`() {
        val projectDir = Files.createTempDirectory("pipeline-functional-bootstrap-plan")
        FunctionalFixtureSupport.copyFixture(projectDir, "bootstrap-sample")

        val result = FunctionalFixtureSupport.runner(projectDir, "cap4kBootstrapPlan").build()
        val planFile = projectDir.resolve("build/cap4k/bootstrap-plan.json")
        val planContent = planFile.readText()

        assertTrue(result.output.contains("BUILD SUCCESSFUL"))
        assertTrue(planContent.contains("\"templateId\": \"bootstrap/root/settings.gradle.kts.peb\""))
        assertTrue(planContent.contains("\"slotId\": \"root\""))
        assertTrue(planContent.contains("\"slotId\": \"module-package:domain\""))
        assertTrue(planContent.contains("\"outputPath\": \"only-danmuku/settings.gradle.kts\""))
    }

    @OptIn(ExperimentalPathApi::class)
    @Test
    fun `cap4kBootstrap generates representative multi-module skeleton under project name subtree`() {
        val projectDir = Files.createTempDirectory("pipeline-functional-bootstrap-generate")
        FunctionalFixtureSupport.copyFixture(projectDir, "bootstrap-sample")

        val result = FunctionalFixtureSupport.runner(projectDir, "cap4kBootstrap").build()

        val generatedSettings = projectDir.resolve("only-danmuku/settings.gradle.kts")
        val generatedRootBuild = projectDir.resolve("only-danmuku/build.gradle.kts")
        val generatedDomainBuild = projectDir.resolve("only-danmuku/only-danmuku-domain/build.gradle.kts")
        val generatedSlotReadme = projectDir.resolve("only-danmuku/README.md")
        val generatedPackageMarker = projectDir.resolve(
            "only-danmuku/only-danmuku-domain/src/main/kotlin/edu/only4/danmuku/domain/DomainBootstrapMarker.kt"
        )

        assertTrue(result.output.contains("BUILD SUCCESSFUL"))
        assertTrue(generatedSettings.toFile().exists())
        assertTrue(generatedRootBuild.toFile().exists())
        assertTrue(generatedDomainBuild.toFile().exists())
        assertTrue(generatedSlotReadme.toFile().exists())
        assertTrue(generatedPackageMarker.toFile().exists())
    }

    @OptIn(ExperimentalPathApi::class)
    @Test
    fun `cap4kBootstrap supports fixed template override dirs`() {
        val projectDir = Files.createTempDirectory("pipeline-functional-bootstrap-override")
        FunctionalFixtureSupport.copyFixture(projectDir, "bootstrap-override-sample")

        val result = FunctionalFixtureSupport.runner(projectDir, "cap4kBootstrap").build()
        val generatedRootBuild = projectDir.resolve("only-danmuku/build.gradle.kts").readText()

        assertTrue(result.output.contains("BUILD SUCCESSFUL"))
        assertTrue(generatedRootBuild.contains("// override: bootstrap root build template"))
    }

    @OptIn(ExperimentalPathApi::class)
    @Test
    fun `cap4kBootstrapPlan fails for unsupported slot role`() {
        val projectDir = Files.createTempDirectory("pipeline-functional-bootstrap-invalid-role")
        FunctionalFixtureSupport.copyFixture(projectDir, "bootstrap-invalid-slot-sample")

        val result = FunctionalFixtureSupport.runner(projectDir, "cap4kBootstrapPlan").buildAndFail()

        assertTrue(result.output.contains("unsupported bootstrap slot role"))
    }
}
```

- [ ] **Step 2: Run the functional bootstrap tests and verify they fail**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-gradle:test --tests "*PipelinePluginBootstrapFunctionalTest"
```

Expected:

- tests fail because bootstrap fixtures, tasks, and renderer integration are not complete yet.

- [ ] **Step 3: Implement the functional fixtures and wire bootstrap runner into the plugin**

```kotlin
// cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePlugin.kt
internal fun buildBootstrapRunner(project: Project, exportEnabled: Boolean): BootstrapRunner {
    return DefaultBootstrapRunner(
        providers = listOf(DddMultiModuleBootstrapPresetProvider()),
        renderer = PebbleBootstrapRenderer(
            PresetTemplateResolver(
                preset = "ddd-default-bootstrap",
                overrideDirs = emptyList(),
            )
        ),
        exporter = if (exportEnabled) FilesystemArtifactExporter(project.projectDir.toPath()) else NoopArtifactExporter(),
    )
}
```

```kotlin
// cap4k-plugin-pipeline-gradle/src/test/resources/functional/bootstrap-sample/build.gradle.kts
plugins {
    id("com.only4.cap4k.plugin.pipeline")
}

cap4k {
    bootstrap {
        enabled.set(true)
        preset.set("ddd-multi-module")
        projectName.set("only-danmuku")
        basePackage.set("edu.only4.danmuku")
        modules {
            domainModuleName.set("only-danmuku-domain")
            applicationModuleName.set("only-danmuku-application")
            adapterModuleName.set("only-danmuku-adapter")
        }
        slots {
            root.from("codegen/bootstrap-slots/root")
            modulePackage("domain").from("codegen/bootstrap-slots/domain-package")
        }
    }
}
```

```text
# cap4k-plugin-pipeline-gradle/src/test/resources/functional/bootstrap-sample/codegen/bootstrap-slots/root/README.md.peb
# {{ projectName }}
```

```kotlin
// cap4k-plugin-pipeline-gradle/src/test/resources/functional/bootstrap-sample/codegen/bootstrap-slots/domain-package/DomainBootstrapMarker.kt.peb
package {{ basePackage }}.domain

object DomainBootstrapMarker
```

- [ ] **Step 4: Run the functional bootstrap tests and verify they pass**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-gradle:test --tests "*PipelinePluginBootstrapFunctionalTest"
```

Expected:

- bootstrap plan JSON is written under `build/cap4k/bootstrap-plan.json`
- bootstrap generation creates the skeleton under `only-danmuku/`
- template overrides work
- invalid slot role fails fast

- [ ] **Step 5: Commit the functional closure**

```powershell
git add cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePlugin.kt `
        cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/FunctionalFixtureSupport.kt `
        cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginBootstrapFunctionalTest.kt `
        cap4k-plugin-pipeline-gradle/src/test/resources/functional/bootstrap-sample `
        cap4k-plugin-pipeline-gradle/src/test/resources/functional/bootstrap-override-sample `
        cap4k-plugin-pipeline-gradle/src/test/resources/functional/bootstrap-invalid-slot-sample
git commit -m "test: cover bootstrap migration flow"
```

### Task 6: Run Full Regression and Clean Up Contract Gaps

**Files:**
- Modify: whatever files Task 1-5 expose as necessary compile/test follow-on fixes
- Verify: all touched bootstrap files and dependent plugin modules

- [ ] **Step 1: Run the focused bootstrap regression suite**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-api:test `
          :cap4k-plugin-pipeline-core:test `
          :cap4k-plugin-pipeline-bootstrap:test `
          :cap4k-plugin-pipeline-renderer-pebble:test `
          :cap4k-plugin-pipeline-gradle:test
```

Expected:

- all bootstrap-targeted modules pass
- no failing bootstrap functional or unit tests remain

- [ ] **Step 2: Fix direct regressions exposed by the new bootstrap gate**

```kotlin
// Acceptable follow-on fix examples inside this task:
// - incorrect preset output path prefixing under projectName
// - overrideDirs not reaching bootstrap renderer
// - slot relative path normalization producing backslashes on Windows
// - FAIL conflict policy not being preserved on slot-generated files
// - bootstrap plan JSON not serializing slotId/sourcePath
```

Constraint:

- only fix issues directly exposed by bootstrap implementation or its tests
- do not widen scope into design generators, bootstrap parity beyond the first slice, or support-track project variants

- [ ] **Step 3: Run the full pipeline regression after bootstrap lands**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-source-design-json:test `
          :cap4k-plugin-pipeline-core:test `
          :cap4k-plugin-pipeline-generator-design:test `
          :cap4k-plugin-pipeline-renderer-pebble:test `
          :cap4k-plugin-pipeline-gradle:test
```

Expected:

- existing pipeline slices remain green
- bootstrap does not regress design/aggregate/flow task behavior

- [ ] **Step 4: Commit the final bootstrap verification fixes**

```powershell
git add settings.gradle.kts `
        cap4k-plugin-pipeline-api `
        cap4k-plugin-pipeline-bootstrap `
        cap4k-plugin-pipeline-core `
        cap4k-plugin-pipeline-renderer-api `
        cap4k-plugin-pipeline-renderer-pebble `
        cap4k-plugin-pipeline-gradle
git commit -m "fix: harden bootstrap migration flow"
```

- [ ] **Step 5: Final status check**

Run:

```powershell
git status --short --branch
```

Expected:

- working tree clean except for any intentional untracked scratch files that should be deleted before handoff

## Self-Review

### Spec coverage

- Separate bootstrap capability with its own DSL and tasks:
  - Task 1 adds bootstrap DSL/config
  - Task 2 adds `cap4kBootstrapPlan` / `cap4kBootstrap`
- One `ddd-multi-module` preset:
  - Task 3 adds `DddMultiModuleBootstrapPresetProvider`
- Fixed bootstrap template ids and override behavior:
  - Task 4 adds fixed preset templates and renderer behavior
  - Task 5 verifies override dirs end-to-end
- Bounded slots:
  - Task 1 validates slot contract
  - Task 3 plans role-bounded slots
  - Task 5 verifies slot generation
- Representative legacy mapping:
  - Task 3 adds characterization tests and fixture sample
- Separate capability, not widened design generator path:
  - Task 2 adds separate runner/tasks
  - no task modifies canonical assembler or design generators

### Placeholder scan

Manual scan targets:

- placeholder markers such as `TO-DO`, `TB-D`, `implement-later`
- vague directives such as `add-appropriate`, `similar-to`

Expected result:

```powershell
rg -n "TO-DO|TB-D|implement-later|add-appropriate|similar-to" docs/superpowers/plans/2026-04-16-cap4k-bootstrap-arch-template-migration.md
```

Expected output:

- matches, if any, should be limited to this self-review subsection
- no implementation task should contain those placeholder forms

### Type consistency

The plan consistently uses:

- `BootstrapConfig`
- `BootstrapModulesConfig`
- `BootstrapTemplateConfig`
- `BootstrapSlotBinding`
- `BootstrapPlanItem`
- `BootstrapRunner`
- `BootstrapPresetProvider`
- `PebbleBootstrapRenderer`
- `Cap4kBootstrapConfigFactory`
- `cap4kBootstrapPlan`
- `cap4kBootstrap`

No alternate names should be introduced during implementation.

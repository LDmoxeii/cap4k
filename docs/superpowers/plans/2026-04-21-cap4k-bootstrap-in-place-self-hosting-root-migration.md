# Cap4k Bootstrap In-Place Self-Hosting Root Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Change bootstrap so the official default is an in-place, self-hosting root contract with explicit preview mode, mode-aware planning, and rerunnable managed root sections.

**Architecture:** The implementation keeps bootstrap as a separate capability, but replaces the current `projectName`-as-output-prefix behavior with an explicit `BootstrapMode` split. In-place bootstrap will operate only against a recognized marker-carrying host root and will update root `build.gradle.kts` / `settings.gradle.kts` through bootstrap-only managed-section merging; preview mode will continue to emit a separate subtree and remains the teaching/demo escape hatch. Task-level self-hosting verification for generated preview roots will continue to use Gradle TestKit plugin classpath injection; published plugin distribution remains outside this slice.

**Tech Stack:** Kotlin, Gradle plugin/TestKit, JUnit 5, existing bootstrap DSL/tasks, Pebble bootstrap templates, bootstrap-only managed-section merge support

---

## Precondition

This plan implements the spec at:

- [2026-04-21-cap4k-bootstrap-in-place-self-hosting-root-migration-design.md](../specs/2026-04-21-cap4k-bootstrap-in-place-self-hosting-root-migration-design.md)

Important execution constraint:

- because bootstrap is still invoked as a Gradle task, the supported first-run in-place entry for this slice is a recognized minimal host root with valid bootstrap markers
- this plan does **not** introduce an external launcher for truly empty directories
- preview-mode generated-root task verification in tests may use `withPluginClasspath()`; solving plugin publication/distribution ergonomics is a separate concern

## Managed Section Ownership Boundary

This slice intentionally keeps bootstrap-managed ownership narrow for `IN_PLACE` reruns.

Bootstrap-managed sections own only:

- `settings.gradle.kts`: `rootProject.name` and bootstrap-owned fixed-module `include(...)` lines
- `build.gradle.kts`: the bootstrap-owned `cap4k { bootstrap { ... } }` block

The following remain outside bootstrap-managed ownership:

- `plugins { id("com.only4.cap4k.plugin.pipeline") }`
- plugin version and plugin-resolution wiring
- repository declarations, wrapper scaffolding, and other host prerequisites
- `group` / `version`
- `subprojects { ... }` and other organization-wide conventions
- arbitrary user tasks, dependencies, and organization-specific logic

This is not accidental.
Those concerns are either execution-host prerequisites or organization policy, and taking them over would turn this slice into generic Gradle-root migration instead of bounded bootstrap contract correction.

`PREVIEW_SUBTREE` may still render complete root files because bootstrap owns that generated subtree end-to-end.
The narrow ownership rule applies specifically to `IN_PLACE` rewrites of an already existing host root.

## File Structure

### Existing files to modify

- Modify: `cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/BootstrapModels.kt`
- Modify: `cap4k-plugin-pipeline-api/src/test/kotlin/com/only4/cap4k/plugin/pipeline/api/BootstrapModelsTest.kt`
- Modify: `cap4k-plugin-pipeline-bootstrap/src/main/kotlin/com/only4/cap4k/plugin/pipeline/bootstrap/DddMultiModuleBootstrapPresetProvider.kt`
- Modify: `cap4k-plugin-pipeline-bootstrap/src/test/kotlin/com/only4/cap4k/plugin/pipeline/bootstrap/DddMultiModuleBootstrapPresetProviderTest.kt`
- Modify: `cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultBootstrapRunner.kt`
- Modify: `cap4k-plugin-pipeline-core/src/test/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultBootstrapRunnerTest.kt`
- Modify: `cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kExtension.kt`
- Modify: `cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kBootstrapConfigFactory.kt`
- Modify: `cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePlugin.kt`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kBootstrapConfigFactoryTest.kt`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/FunctionalFixtureSupport.kt`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginTest.kt`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginBootstrapFunctionalTest.kt`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginBootstrapGeneratedProjectFunctionalTest.kt`
- Modify: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default-bootstrap/bootstrap/root/build.gradle.kts.peb`
- Modify: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default-bootstrap/bootstrap/root/settings.gradle.kts.peb`
- Modify: `cap4k-plugin-pipeline-gradle/README.md`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/bootstrap-sample/build.gradle.kts`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/bootstrap-sample/settings.gradle.kts`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/bootstrap-override-sample/build.gradle.kts`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/bootstrap-override-sample/settings.gradle.kts`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/bootstrap-generated-project-smoke-sample/build.gradle.kts`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/bootstrap-generated-project-override-sample/build.gradle.kts`

### New source files

- Create: `cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/BootstrapManagedSectionMerger.kt`
- Create: `cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/BootstrapFilesystemArtifactExporter.kt`
- Create: `cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/BootstrapRootStateGuard.kt`

### New test files

- Create: `cap4k-plugin-pipeline-core/src/test/kotlin/com/only4/cap4k/plugin/pipeline/core/BootstrapManagedSectionMergerTest.kt`
- Create: `cap4k-plugin-pipeline-core/src/test/kotlin/com/only4/cap4k/plugin/pipeline/core/BootstrapFilesystemArtifactExporterTest.kt`
- Create: `cap4k-plugin-pipeline-core/src/test/kotlin/com/only4/cap4k/plugin/pipeline/core/BootstrapRootStateGuardTest.kt`
- Create: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginBootstrapInPlaceFunctionalTest.kt`
- Create: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginBootstrapPreviewFunctionalTest.kt`

### New functional fixtures

- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/bootstrap-preview-sample/build.gradle.kts`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/bootstrap-preview-sample/settings.gradle.kts`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/bootstrap-preview-sample/codegen/bootstrap-slots/root/README.md.peb`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/bootstrap-preview-sample/codegen/bootstrap-slots/domain-package/PreviewSlotMarker.kt.peb`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/bootstrap-unmanaged-root-sample/build.gradle.kts`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/bootstrap-unmanaged-root-sample/settings.gradle.kts`

## Task 1: Add Bootstrap Mode and Preview Configuration

**Files:**
- Modify: `cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/BootstrapModels.kt`
- Modify: `cap4k-plugin-pipeline-api/src/test/kotlin/com/only4/cap4k/plugin/pipeline/api/BootstrapModelsTest.kt`
- Modify: `cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kExtension.kt`
- Modify: `cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kBootstrapConfigFactory.kt`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kBootstrapConfigFactoryTest.kt`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginTest.kt`

- [ ] **Step 1: Extend the failing model and config tests**

```kotlin
// cap4k-plugin-pipeline-api/src/test/kotlin/com/only4/cap4k/plugin/pipeline/api/BootstrapModelsTest.kt
@Test
fun `bootstrap config captures in place mode without preview dir`() {
    val config = BootstrapConfig(
        preset = "ddd-multi-module",
        mode = BootstrapMode.IN_PLACE,
        projectName = "only-danmaku",
        previewDir = null,
        basePackage = "edu.only4.danmaku",
        modules = BootstrapModulesConfig("only-danmaku-domain", "only-danmaku-application", "only-danmaku-adapter"),
        templates = BootstrapTemplateConfig("ddd-default-bootstrap", emptyList()),
        slots = emptyList(),
        conflictPolicy = ConflictPolicy.FAIL,
    )

    assertEquals(BootstrapMode.IN_PLACE, config.mode)
    assertEquals(null, config.previewDir)
}

@Test
fun `bootstrap config captures preview subtree mode with explicit preview dir`() {
    val config = BootstrapConfig(
        preset = "ddd-multi-module",
        mode = BootstrapMode.PREVIEW_SUBTREE,
        projectName = "only-danmaku",
        previewDir = "bootstrap-preview",
        basePackage = "edu.only4.danmaku",
        modules = BootstrapModulesConfig("only-danmaku-domain", "only-danmaku-application", "only-danmaku-adapter"),
        templates = BootstrapTemplateConfig("ddd-default-bootstrap", emptyList()),
        slots = emptyList(),
        conflictPolicy = ConflictPolicy.FAIL,
    )

    assertEquals(BootstrapMode.PREVIEW_SUBTREE, config.mode)
    assertEquals("bootstrap-preview", config.previewDir)
}
```

```kotlin
// cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kBootstrapConfigFactoryTest.kt
@Test
fun `build defaults bootstrap mode to in place`() {
    val project = ProjectBuilder.builder().build()
    val extension = project.extensions.create("cap4k", Cap4kExtension::class.java)

    extension.bootstrap {
        enabled.set(true)
        preset.set("ddd-multi-module")
        projectName.set("only-danmaku")
        basePackage.set("edu.only4.danmaku")
        modules {
            domainModuleName.set("only-danmaku-domain")
            applicationModuleName.set("only-danmaku-application")
            adapterModuleName.set("only-danmaku-adapter")
        }
    }

    val config = Cap4kBootstrapConfigFactory().build(project, extension)

    assertEquals(BootstrapMode.IN_PLACE, config.mode)
    assertEquals(null, config.previewDir)
}

@Test
fun `build requires preview dir when mode is preview subtree`() {
    val project = ProjectBuilder.builder().build()
    val extension = project.extensions.create("cap4k", Cap4kExtension::class.java)

    extension.bootstrap {
        enabled.set(true)
        preset.set("ddd-multi-module")
        mode.set(BootstrapMode.PREVIEW_SUBTREE)
        projectName.set("only-danmaku")
        basePackage.set("edu.only4.danmaku")
        modules {
            domainModuleName.set("only-danmaku-domain")
            applicationModuleName.set("only-danmaku-application")
            adapterModuleName.set("only-danmaku-adapter")
        }
    }

    val error = assertThrows(IllegalArgumentException::class.java) {
        Cap4kBootstrapConfigFactory().build(project, extension)
    }

    assertTrue(error.message!!.contains("bootstrap.previewDir is required"))
}

@Test
fun `build rejects preview dir outside preview subtree mode`() {
    val project = ProjectBuilder.builder().build()
    val extension = project.extensions.create("cap4k", Cap4kExtension::class.java)

    extension.bootstrap {
        enabled.set(true)
        preset.set("ddd-multi-module")
        mode.set(BootstrapMode.IN_PLACE)
        previewDir.set("bootstrap-preview")
        projectName.set("only-danmaku")
        basePackage.set("edu.only4.danmaku")
        modules {
            domainModuleName.set("only-danmaku-domain")
            applicationModuleName.set("only-danmaku-application")
            adapterModuleName.set("only-danmaku-adapter")
        }
    }

    val error = assertThrows(IllegalArgumentException::class.java) {
        Cap4kBootstrapConfigFactory().build(project, extension)
    }

    assertTrue(error.message!!.contains("bootstrap.previewDir is only valid"))
}

@Test
fun `build rejects unsafe preview dir`() {
    val project = ProjectBuilder.builder().build()
    val extension = project.extensions.create("cap4k", Cap4kExtension::class.java)

    extension.bootstrap {
        enabled.set(true)
        preset.set("ddd-multi-module")
        mode.set(BootstrapMode.PREVIEW_SUBTREE)
        previewDir.set("../preview")
        projectName.set("only-danmaku")
        basePackage.set("edu.only4.danmaku")
        modules {
            domainModuleName.set("only-danmaku-domain")
            applicationModuleName.set("only-danmaku-application")
            adapterModuleName.set("only-danmaku-adapter")
        }
    }

    val error = assertThrows(IllegalArgumentException::class.java) {
        Cap4kBootstrapConfigFactory().build(project, extension)
    }

    assertTrue(error.message!!.contains("bootstrap.previewDir must be a safe relative path"))
}
```

- [ ] **Step 2: Run the targeted tests and verify they fail**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-api:test :cap4k-plugin-pipeline-gradle:test --tests "*BootstrapModelsTest" --tests "*Cap4kBootstrapConfigFactoryTest" --tests "*PipelinePluginTest"
```

Expected:

- `BootstrapModelsTest` fails because `BootstrapMode`, `mode`, and `previewDir` do not exist
- `Cap4kBootstrapConfigFactoryTest` fails because the extension/config factory do not expose mode-aware bootstrap config yet
- `PipelinePluginTest` fails because bootstrap runner/config helper code still constructs the old `BootstrapConfig` shape

- [ ] **Step 3: Implement mode-aware bootstrap config**

```kotlin
// cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/BootstrapModels.kt
enum class BootstrapMode {
    IN_PLACE,
    PREVIEW_SUBTREE,
}

data class BootstrapConfig(
    val preset: String,
    val mode: BootstrapMode,
    val projectName: String,
    val previewDir: String?,
    val basePackage: String,
    val modules: BootstrapModulesConfig,
    val templates: BootstrapTemplateConfig,
    val slots: List<BootstrapSlotBinding>,
    val conflictPolicy: ConflictPolicy,
)
```

```kotlin
// cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kExtension.kt
open class Cap4kBootstrapExtension @Inject constructor(objects: ObjectFactory) {
    val enabled: Property<Boolean> = objects.property(Boolean::class.java).convention(false)
    val preset: Property<String> = objects.property(String::class.java).convention("ddd-multi-module")
    val mode: Property<BootstrapMode> = objects.property(BootstrapMode::class.java).convention(BootstrapMode.IN_PLACE)
    val previewDir: Property<String> = objects.property(String::class.java)
    val projectName: Property<String> = objects.property(String::class.java)
    val basePackage: Property<String> = objects.property(String::class.java)
    // existing modules/templates/slots/conflictPolicy unchanged
}
```

```kotlin
// cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kBootstrapConfigFactory.kt
class Cap4kBootstrapConfigFactory {

    fun build(project: Project, extension: Cap4kExtension): BootstrapConfig {
        require(extension.bootstrap.enabled.get()) {
            "bootstrap.enabled must be true to run bootstrap tasks."
        }

        val mode = extension.bootstrap.mode.orNull ?: BootstrapMode.IN_PLACE
        val previewDir = extension.bootstrap.previewDir.orNull?.trim()?.takeIf { it.isNotEmpty() }

        require(mode != BootstrapMode.PREVIEW_SUBTREE || !previewDir.isNullOrBlank()) {
            "bootstrap.previewDir is required when bootstrap.mode=PREVIEW_SUBTREE."
        }
        require(mode == BootstrapMode.PREVIEW_SUBTREE || previewDir == null) {
            "bootstrap.previewDir is only valid when bootstrap.mode=PREVIEW_SUBTREE."
        }
        previewDir?.let(::requireSafeRelativeDir)

        return BootstrapConfig(
            preset = extension.bootstrap.preset.required("bootstrap.preset"),
            mode = mode,
            projectName = extension.bootstrap.projectName.required("bootstrap.projectName"),
            previewDir = previewDir,
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
            slots = extension.bootstrap.slots.bindings(project),
            conflictPolicy = ConflictPolicy.valueOf(extension.bootstrap.conflictPolicy.orNull?.trim().orEmpty().ifEmpty { "FAIL" }),
        )
    }
}

private fun requireSafeRelativeDir(value: String) {
    require(!value.startsWith("/") && !value.startsWith("\\") && !Regex("^[A-Za-z]:").containsMatchIn(value)) {
        "bootstrap.previewDir must be a safe relative path: $value"
    }
    require(!value.split('/', '\\').contains("..")) {
        "bootstrap.previewDir must be a safe relative path: $value"
    }
}
```

- [ ] **Step 4: Run the targeted tests again and verify they pass**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-api:test :cap4k-plugin-pipeline-gradle:test --tests "*BootstrapModelsTest" --tests "*Cap4kBootstrapConfigFactoryTest"
```

Expected:

- `BootstrapModelsTest` passes
- `Cap4kBootstrapConfigFactoryTest` passes

- [ ] **Step 5: Commit**

```powershell
git add cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/BootstrapModels.kt `
        cap4k-plugin-pipeline-api/src/test/kotlin/com/only4/cap4k/plugin/pipeline/api/BootstrapModelsTest.kt `
        cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kExtension.kt `
        cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kBootstrapConfigFactory.kt `
        cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kBootstrapConfigFactoryTest.kt
git commit -m "feat: add bootstrap mode-aware config"
```

## Task 2: Rework Bootstrap Planning for In-Place and Preview Modes

**Files:**
- Modify: `cap4k-plugin-pipeline-bootstrap/src/main/kotlin/com/only4/cap4k/plugin/pipeline/bootstrap/DddMultiModuleBootstrapPresetProvider.kt`
- Modify: `cap4k-plugin-pipeline-bootstrap/src/test/kotlin/com/only4/cap4k/plugin/pipeline/bootstrap/DddMultiModuleBootstrapPresetProviderTest.kt`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginBootstrapFunctionalTest.kt`

- [ ] **Step 1: Extend the provider and functional tests for mode-aware output paths**

```kotlin
// cap4k-plugin-pipeline-bootstrap/src/test/kotlin/com/only4/cap4k/plugin/pipeline/bootstrap/DddMultiModuleBootstrapPresetProviderTest.kt
@Test
fun `plan emits fixed root and module templates directly under project root in place mode`() {
    val config = config.copy(mode = BootstrapMode.IN_PLACE, previewDir = null)

    val items = DddMultiModuleBootstrapPresetProvider().plan(config)

    assertTrue(items.any { it.templateId == "bootstrap/root/settings.gradle.kts.peb" && it.outputPath == "settings.gradle.kts" })
    assertTrue(items.any { it.templateId == "bootstrap/root/build.gradle.kts.peb" && it.outputPath == "build.gradle.kts" })
    assertTrue(items.any { it.templateId == "bootstrap/module/domain-build.gradle.kts.peb" && it.outputPath == "only-danmuku-domain/build.gradle.kts" })
}

@Test
fun `plan emits fixed root and module templates under preview subtree when configured`() {
    val config = config.copy(mode = BootstrapMode.PREVIEW_SUBTREE, previewDir = "bootstrap-preview")

    val items = DddMultiModuleBootstrapPresetProvider().plan(config)

    assertTrue(items.any { it.templateId == "bootstrap/root/settings.gradle.kts.peb" && it.outputPath == "bootstrap-preview/settings.gradle.kts" })
    assertTrue(items.any { it.templateId == "bootstrap/root/build.gradle.kts.peb" && it.outputPath == "bootstrap-preview/build.gradle.kts" })
    assertTrue(items.any { it.templateId == "bootstrap/module/domain-build.gradle.kts.peb" && it.outputPath == "bootstrap-preview/only-danmuku-domain/build.gradle.kts" })
}

@Test
fun `module package output path rebases from current root in place mode`() {
    val outputPath = resolveSlotOutputPath(
        binding = BootstrapSlotBinding(BootstrapSlotKind.MODULE_PACKAGE, role = "domain", sourceDir = "src/test/resources/slots/domain-package"),
        renderedRelativePath = "SmokeDomainMarker.kt",
        config = config.copy(mode = BootstrapMode.IN_PLACE, previewDir = null),
    )

    assertEquals(
        "only-danmuku-domain/src/main/kotlin/edu/only4/danmuku/SmokeDomainMarker.kt",
        outputPath
    )
}
```

```kotlin
// cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginBootstrapFunctionalTest.kt
@Test
fun `cap4kBootstrapPlan writes in place bootstrap plan json by default`() {
    val projectDir = Files.createTempDirectory("pipeline-functional-bootstrap-plan")
    FunctionalFixtureSupport.copyFixture(projectDir, "bootstrap-sample")

    val result = FunctionalFixtureSupport.runner(projectDir, "cap4kBootstrapPlan").build()
    val planFile = projectDir.resolve("build/cap4k/bootstrap-plan.json")
    val planContent = planFile.readText()

    assertTrue(result.output.contains("BUILD SUCCESSFUL"))
    assertTrue(planContent.contains("\"outputPath\": \"settings.gradle.kts\""))
    assertTrue(planContent.contains("\"outputPath\": \"only-danmuku-domain/build.gradle.kts\""))
    assertFalse(planContent.contains("\"outputPath\": \"only-danmuku/settings.gradle.kts\""))
}

@Test
fun `cap4kBootstrap generates representative multi module skeleton in place by default`() {
    val projectDir = Files.createTempDirectory("pipeline-functional-bootstrap-generate")
    FunctionalFixtureSupport.copyFixture(projectDir, "bootstrap-sample")

    val result = FunctionalFixtureSupport.runner(projectDir, "cap4kBootstrap").build()

    assertTrue(result.output.contains("BUILD SUCCESSFUL"))
    assertTrue(projectDir.resolve("settings.gradle.kts").toFile().exists())
    assertTrue(projectDir.resolve("build.gradle.kts").toFile().exists())
    assertTrue(projectDir.resolve("only-danmuku-domain/build.gradle.kts").toFile().exists())
    assertTrue(projectDir.resolve("README.md").toFile().exists())
    assertTrue(projectDir.resolve("only-danmuku-domain/src/main/kotlin/edu/only4/danmuku/domain/DomainBootstrapMarker.kt").toFile().exists())
}
```

- [ ] **Step 2: Run the provider and functional tests and verify they fail**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-bootstrap:test :cap4k-plugin-pipeline-gradle:test --tests "*DddMultiModuleBootstrapPresetProviderTest" --tests "*PipelinePluginBootstrapFunctionalTest"
```

Expected:

- provider tests fail because `projectName/` is still hardcoded into output paths
- functional plan test fails because bootstrap still writes plan items under `only-danmuku/`

- [ ] **Step 3: Implement mode-aware output rebasing**

```kotlin
// cap4k-plugin-pipeline-bootstrap/src/main/kotlin/com/only4/cap4k/plugin/pipeline/bootstrap/DddMultiModuleBootstrapPresetProvider.kt
internal fun bootstrapContext(config: BootstrapConfig): Map<String, Any?> =
    mapOf(
        "projectName" to config.projectName,
        "mode" to config.mode.name,
        "previewDir" to config.previewDir,
        "basePackage" to config.basePackage,
        "basePackagePath" to config.basePackage.replace('.', '/'),
        "domainModuleName" to config.modules.domainModuleName,
        "applicationModuleName" to config.modules.applicationModuleName,
        "adapterModuleName" to config.modules.adapterModuleName,
    )

internal fun outputRootPrefix(config: BootstrapConfig): String =
    when (config.mode) {
        BootstrapMode.IN_PLACE -> ""
        BootstrapMode.PREVIEW_SUBTREE -> requireNotNull(config.previewDir)
    }

internal fun rebaseOutput(relativePath: String, config: BootstrapConfig): String {
    val prefix = outputRootPrefix(config)
    return if (prefix.isBlank()) {
        relativePath
    } else {
        "$prefix/$relativePath"
    }
}

class DddMultiModuleBootstrapPresetProvider : BootstrapPresetProvider {
    override val presetId: String = "ddd-multi-module"

    override fun plan(config: BootstrapConfig): List<BootstrapPlanItem> {
        validateBootstrapPathSegments(config)
        val context = bootstrapContext(config)
        return buildList {
            add(fixed("bootstrap/root/settings.gradle.kts.peb", rebaseOutput("settings.gradle.kts", config), config, context))
            add(fixed("bootstrap/root/build.gradle.kts.peb", rebaseOutput("build.gradle.kts", config), config, context))
            add(fixed("bootstrap/module/domain-build.gradle.kts.peb", rebaseOutput("${config.modules.domainModuleName}/build.gradle.kts", config), config, context + mapOf("moduleRole" to "domain")))
            add(fixed("bootstrap/module/application-build.gradle.kts.peb", rebaseOutput("${config.modules.applicationModuleName}/build.gradle.kts", config), config, context + mapOf("moduleRole" to "application")))
            add(fixed("bootstrap/module/adapter-build.gradle.kts.peb", rebaseOutput("${config.modules.adapterModuleName}/build.gradle.kts", config), config, context + mapOf("moduleRole" to "adapter")))
            addAll(packageMarkers(config, context))
            addAll(BootstrapSlotPlanner.plan(config))
        }
    }
}

internal fun resolveSlotOutputPath(
    binding: BootstrapSlotBinding,
    renderedRelativePath: String,
    config: BootstrapConfig,
): String {
    val boundedRelative = normalizeRelativePath(renderedRelativePath)
    val moduleName = binding.role?.let { resolveModuleName(it, config) }
    val relativePath = when (binding.kind) {
        BootstrapSlotKind.ROOT -> boundedRelative
        BootstrapSlotKind.BUILD_LOGIC -> "build-logic/$boundedRelative"
        BootstrapSlotKind.MODULE_ROOT -> "${requireNotNull(moduleName)}/$boundedRelative"
        BootstrapSlotKind.MODULE_PACKAGE -> "${requireNotNull(moduleName)}/src/main/kotlin/${resolveModulePackageRelativePath(boundedRelative, config)}"
    }
    return rebaseOutput(relativePath, config)
}
```

- [ ] **Step 4: Run the provider and functional tests again and verify they pass**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-bootstrap:test :cap4k-plugin-pipeline-gradle:test --tests "*DddMultiModuleBootstrapPresetProviderTest" --tests "*PipelinePluginBootstrapFunctionalTest"
```

Expected:

- provider path assertions pass for both in-place and preview modes
- functional plan test passes and no longer sees `projectName` as an implicit output prefix

- [ ] **Step 5: Commit**

```powershell
git add cap4k-plugin-pipeline-bootstrap/src/main/kotlin/com/only4/cap4k/plugin/pipeline/bootstrap/DddMultiModuleBootstrapPresetProvider.kt `
        cap4k-plugin-pipeline-bootstrap/src/test/kotlin/com/only4/cap4k/plugin/pipeline/bootstrap/DddMultiModuleBootstrapPresetProviderTest.kt `
        cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginBootstrapFunctionalTest.kt
git commit -m "feat: rebase bootstrap outputs by mode"
```

## Task 3: Add Bootstrap-Only Managed Root Merge Support

**Files:**
- Create: `cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/BootstrapManagedSectionMerger.kt`
- Create: `cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/BootstrapFilesystemArtifactExporter.kt`
- Create: `cap4k-plugin-pipeline-core/src/test/kotlin/com/only4/cap4k/plugin/pipeline/core/BootstrapManagedSectionMergerTest.kt`
- Create: `cap4k-plugin-pipeline-core/src/test/kotlin/com/only4/cap4k/plugin/pipeline/core/BootstrapFilesystemArtifactExporterTest.kt`
- Modify: `cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePlugin.kt`

- [ ] **Step 1: Write the failing managed-root merge tests**

```kotlin
// cap4k-plugin-pipeline-core/src/test/kotlin/com/only4/cap4k/plugin/pipeline/core/BootstrapManagedSectionMergerTest.kt
class BootstrapManagedSectionMergerTest {

    @Test
    fun `merge replaces only managed section bodies and preserves surrounding content`() {
        val existing = """
            plugins { id("com.only4.cap4k.plugin.pipeline") }

            // user preface
            // [cap4k-bootstrap:managed-begin:root-host]
            old managed content
            // [cap4k-bootstrap:managed-end:root-host]
            // user suffix
        """.trimIndent()

        val generated = """
            plugins { id("com.only4.cap4k.plugin.pipeline") }

            // [cap4k-bootstrap:managed-begin:root-host]
            new managed content
            // [cap4k-bootstrap:managed-end:root-host]
        """.trimIndent()

        val merged = BootstrapManagedSectionMerger().merge(existing, generated)

        assertTrue(merged.contains("user preface"))
        assertTrue(merged.contains("new managed content"))
        assertTrue(merged.contains("user suffix"))
        assertFalse(merged.contains("old managed content"))
    }

    @Test
    fun `merge fails when existing file has missing managed end marker`() {
        val existing = """
            // [cap4k-bootstrap:managed-begin:root-host]
            broken
        """.trimIndent()

        val generated = """
            // [cap4k-bootstrap:managed-begin:root-host]
            managed
            // [cap4k-bootstrap:managed-end:root-host]
        """.trimIndent()

        val error = assertThrows(IllegalArgumentException::class.java) {
            BootstrapManagedSectionMerger().merge(existing, generated)
        }

        assertTrue(error.message!!.contains("managed section markers"))
    }
}
```

```kotlin
// cap4k-plugin-pipeline-core/src/test/kotlin/com/only4/cap4k/plugin/pipeline/core/BootstrapFilesystemArtifactExporterTest.kt
class BootstrapFilesystemArtifactExporterTest {

    @Test
    fun `export merges managed root files and overwrites regular generated files normally`() {
        val tempRoot = Files.createTempDirectory("bootstrap-exporter")
        val buildFile = tempRoot.resolve("build.gradle.kts")
        Files.writeString(
            buildFile,
            """
            plugins { id("com.only4.cap4k.plugin.pipeline") }
            // [cap4k-bootstrap:managed-begin:root-host]
            old
            // [cap4k-bootstrap:managed-end:root-host]
            // custom footer
            """.trimIndent()
        )

        val exporter = BootstrapFilesystemArtifactExporter(
            root = tempRoot,
            config = BootstrapConfig(
                preset = "ddd-multi-module",
                mode = BootstrapMode.IN_PLACE,
                projectName = "only-danmaku",
                previewDir = null,
                basePackage = "edu.only4.danmaku",
                modules = BootstrapModulesConfig("only-danmaku-domain", "only-danmaku-application", "only-danmaku-adapter"),
                templates = BootstrapTemplateConfig("ddd-default-bootstrap", emptyList()),
                slots = emptyList(),
                conflictPolicy = ConflictPolicy.FAIL,
            ),
        )

        exporter.export(
            listOf(
                RenderedArtifact(
                    templateId = "bootstrap/root/build.gradle.kts.peb",
                    outputPath = "build.gradle.kts",
                    content = """
                        plugins { id("com.only4.cap4k.plugin.pipeline") }
                        // [cap4k-bootstrap:managed-begin:root-host]
                        new
                        // [cap4k-bootstrap:managed-end:root-host]
                    """.trimIndent(),
                    conflictPolicy = ConflictPolicy.FAIL,
                )
            )
        )

        val merged = Files.readString(buildFile)
        assertTrue(merged.contains("new"))
        assertTrue(merged.contains("custom footer"))
    }
}
```

- [ ] **Step 2: Run the managed-root tests and verify they fail**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-core:test --tests "*BootstrapManagedSectionMergerTest" --tests "*BootstrapFilesystemArtifactExporterTest"
```

Expected:

- tests fail because merger/exporter classes do not exist
- bootstrap still uses the generic exporter with file-level conflict behavior only

- [ ] **Step 3: Implement bootstrap-only managed-section merging**

```kotlin
// cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/BootstrapManagedSectionMerger.kt
class BootstrapManagedSectionMerger {
    private val beginRegex = Regex("""// \\[cap4k-bootstrap:managed-begin:([a-zA-Z0-9_-]+)]""")
    private val endRegex = Regex("""// \\[cap4k-bootstrap:managed-end:([a-zA-Z0-9_-]+)]""")
    private val endTemplate = "// [cap4k-bootstrap:managed-end:%s]"

    fun merge(existingContent: String, generatedContent: String): String {
        val generatedSections = extractSections(generatedContent)
        require(generatedSections.isNotEmpty()) { "generated bootstrap root file must contain managed section markers." }

        var merged = existingContent
        generatedSections.forEach { (sectionId, generatedBody) ->
            val existingSection = extractSections(merged)[sectionId]
                ?: throw IllegalArgumentException("managed section markers are missing for section '$sectionId'.")
            val replacement = buildString {
                append("// [cap4k-bootstrap:managed-begin:$sectionId]\n")
                append(generatedBody)
                if (!generatedBody.endsWith("\n")) append('\n')
                append(endTemplate.format(sectionId))
            }
            merged = merged.replace(existingSection.fullBlock, replacement)
        }
        return merged
    }

    private fun extractSections(content: String): Map<String, ManagedSection> {
        val beginMatches = beginRegex.findAll(content).toList()
        val endMatches = endRegex.findAll(content).toList()
        require(beginMatches.size == endMatches.size) {
            "managed section markers are malformed."
        }

        val sections = linkedMapOf<String, ManagedSection>()
        beginMatches.forEach { beginMatch ->
            val sectionId = beginMatch.groupValues[1]
            require(sectionId !in sections) {
                "duplicate managed section markers for '$sectionId'."
            }
            val beginIndex = beginMatch.range.first
            val bodyStart = beginMatch.range.last + 1
            val endMarker = endTemplate.format(sectionId)
            val endIndex = content.indexOf(endMarker, bodyStart)
            require(endIndex >= 0) {
                "managed section markers are malformed for section '$sectionId'."
            }
            val fullBlock = content.substring(beginIndex, endIndex + endMarker.length)
            val body = content.substring(bodyStart, endIndex).trimStart('\r', '\n')
            sections[sectionId] = ManagedSection(sectionId, body, fullBlock)
        }
        return sections
    }

    internal data class ManagedSection(val id: String, val body: String, val fullBlock: String)
}
```

```kotlin
// cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/BootstrapFilesystemArtifactExporter.kt
class BootstrapFilesystemArtifactExporter(
    private val root: Path,
    private val config: BootstrapConfig,
    private val merger: BootstrapManagedSectionMerger = BootstrapManagedSectionMerger(),
) : ArtifactExporter {
    private val delegate = FilesystemArtifactExporter(root)

    override fun export(artifacts: List<RenderedArtifact>): List<String> {
        val normalizedRoot = root.toAbsolutePath().normalize()
        return artifacts.mapNotNull { artifact ->
            if (!isManagedRootFile(artifact.outputPath)) {
                return@mapNotNull delegate.export(listOf(artifact)).singleOrNull()
            }

            val outputPath = normalizedRoot.resolve(artifact.outputPath).normalize()
            require(outputPath.startsWith(normalizedRoot)) {
                "Artifact output path resolves outside export root: ${artifact.outputPath}"
            }
            Files.createDirectories(requireNotNull(outputPath.parent))

            if (isManagedRootFile(artifact.outputPath) && Files.exists(outputPath)) {
                val merged = merger.merge(Files.readString(outputPath), artifact.content)
                Files.writeString(outputPath, merged)
            } else {
                return@mapNotNull delegate.export(listOf(artifact)).singleOrNull()
            }
            outputPath.toString()
        }
    }

    private fun isManagedRootFile(outputPath: String): Boolean {
        val rootPrefix = if (config.mode == BootstrapMode.PREVIEW_SUBTREE) "${config.previewDir}/" else ""
        return outputPath == "${rootPrefix}build.gradle.kts" || outputPath == "${rootPrefix}settings.gradle.kts"
    }
}
```

```kotlin
// cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePlugin.kt
internal fun buildBootstrapRunner(project: Project, config: BootstrapConfig, exportEnabled: Boolean): BootstrapRunner {
    return DefaultBootstrapRunner(
        providers = listOf(DddMultiModuleBootstrapPresetProvider()),
        renderer = PebbleBootstrapRenderer(
            PresetTemplateResolver(
                preset = config.templates.preset,
                overrideDirs = config.templates.overrideDirs,
            )
        ),
        exporter = if (exportEnabled) {
            BootstrapFilesystemArtifactExporter(project.projectDir.toPath(), config)
        } else {
            NoopArtifactExporter()
        },
    )
}
```

- [ ] **Step 4: Run the managed-root tests again and verify they pass**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-core:test --tests "*BootstrapManagedSectionMergerTest" --tests "*BootstrapFilesystemArtifactExporterTest"
```

Expected:

- managed-section merge tests pass
- bootstrap exporter rewrites root files through section merge instead of file-level conflict failure

- [ ] **Step 5: Commit**

```powershell
git add cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/BootstrapManagedSectionMerger.kt `
        cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/BootstrapFilesystemArtifactExporter.kt `
        cap4k-plugin-pipeline-core/src/test/kotlin/com/only4/cap4k/plugin/pipeline/core/BootstrapManagedSectionMergerTest.kt `
        cap4k-plugin-pipeline-core/src/test/kotlin/com/only4/cap4k/plugin/pipeline/core/BootstrapFilesystemArtifactExporterTest.kt `
        cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePlugin.kt
git commit -m "feat: add bootstrap managed root merge support"
```

## Task 4: Guard In-Place Roots and Upgrade the Minimal Host Into a Self-Hosting Root

**Files:**
- Create: `cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/BootstrapRootStateGuard.kt`
- Create: `cap4k-plugin-pipeline-core/src/test/kotlin/com/only4/cap4k/plugin/pipeline/core/BootstrapRootStateGuardTest.kt`
- Modify: `cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultBootstrapRunner.kt`
- Modify: `cap4k-plugin-pipeline-core/src/test/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultBootstrapRunnerTest.kt`
- Modify: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default-bootstrap/bootstrap/root/build.gradle.kts.peb`
- Modify: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default-bootstrap/bootstrap/root/settings.gradle.kts.peb`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/bootstrap-sample/build.gradle.kts`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/bootstrap-sample/settings.gradle.kts`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/bootstrap-unmanaged-root-sample/build.gradle.kts`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/bootstrap-unmanaged-root-sample/settings.gradle.kts`
- Create: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginBootstrapInPlaceFunctionalTest.kt`

- [ ] **Step 1: Write the failing guard and in-place functional tests**

```kotlin
// cap4k-plugin-pipeline-core/src/test/kotlin/com/only4/cap4k/plugin/pipeline/core/BootstrapRootStateGuardTest.kt
class BootstrapRootStateGuardTest {
    private fun inPlaceConfig() = BootstrapConfig(
        preset = "ddd-multi-module",
        mode = BootstrapMode.IN_PLACE,
        projectName = "only-danmaku",
        previewDir = null,
        basePackage = "edu.only4.danmaku",
        modules = BootstrapModulesConfig("only-danmaku-domain", "only-danmaku-application", "only-danmaku-adapter"),
        templates = BootstrapTemplateConfig("ddd-default-bootstrap", emptyList()),
        slots = emptyList(),
        conflictPolicy = ConflictPolicy.FAIL,
    )

    @Test
    fun `validate in place accepts marker carrying host root`() {
        val root = Files.createTempDirectory("bootstrap-guard-valid")
        Files.writeString(
            root.resolve("build.gradle.kts"),
            """
            plugins { id("com.only4.cap4k.plugin.pipeline") }
            // [cap4k-bootstrap:managed-begin:root-host]
            cap4k { }
            // [cap4k-bootstrap:managed-end:root-host]
            """.trimIndent()
        )
        Files.writeString(
            root.resolve("settings.gradle.kts"),
            """
            // [cap4k-bootstrap:managed-begin:root-host]
            rootProject.name = "bootstrap-host"
            // [cap4k-bootstrap:managed-end:root-host]
            """.trimIndent()
        )

        BootstrapRootStateGuard.validate(root, inPlaceConfig())
    }

    @Test
    fun `validate in place rejects unmanaged existing root`() {
        val root = Files.createTempDirectory("bootstrap-guard-invalid")
        Files.writeString(root.resolve("build.gradle.kts"), """plugins { kotlin("jvm") version "2.2.20" }""")
        Files.writeString(root.resolve("settings.gradle.kts"), "rootProject.name = \"foreign-root\"")

        val error = assertThrows(IllegalArgumentException::class.java) {
            BootstrapRootStateGuard.validate(root, inPlaceConfig())
        }

        assertTrue(error.message!!.contains("recognized bootstrap host root"))
    }

    @Test
    fun `validate in place rejects conflicting non directory module path`() {
        val root = Files.createTempDirectory("bootstrap-guard-module-conflict")
        Files.writeString(
            root.resolve("build.gradle.kts"),
            """
            // [cap4k-bootstrap:managed-begin:root-host]
            cap4k { }
            // [cap4k-bootstrap:managed-end:root-host]
            """.trimIndent()
        )
        Files.writeString(
            root.resolve("settings.gradle.kts"),
            """
            // [cap4k-bootstrap:managed-begin:root-host]
            rootProject.name = "bootstrap-host"
            // [cap4k-bootstrap:managed-end:root-host]
            """.trimIndent()
        )
        Files.writeString(root.resolve("only-danmaku-domain"), "not a directory")

        val error = assertThrows(IllegalArgumentException::class.java) {
            BootstrapRootStateGuard.validate(root, inPlaceConfig())
        }

        assertTrue(error.message!!.contains("conflicts with existing non-directory path"))
    }
}
```

```kotlin
// cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginBootstrapInPlaceFunctionalTest.kt
class PipelinePluginBootstrapInPlaceFunctionalTest {

    @OptIn(ExperimentalPathApi::class)
    @Test
    fun `cap4kBootstrap upgrades minimal host root in place and creates modules`() {
        val projectDir = Files.createTempDirectory("bootstrap-in-place")
        FunctionalFixtureSupport.copyFixture(projectDir, "bootstrap-sample")

        val result = FunctionalFixtureSupport.runner(projectDir, "cap4kBootstrap").build()

        assertTrue(result.output.contains("BUILD SUCCESSFUL"))
        assertTrue(projectDir.resolve("only-danmuku-domain/build.gradle.kts").toFile().exists())
        assertTrue(projectDir.resolve("only-danmuku-application/build.gradle.kts").toFile().exists())
        assertTrue(projectDir.resolve("only-danmuku-adapter/build.gradle.kts").toFile().exists())
        assertTrue(projectDir.resolve("settings.gradle.kts").readText().contains("rootProject.name = \"only-danmuku\""))
    }

    @OptIn(ExperimentalPathApi::class)
    @Test
    fun `rerunning in place bootstrap preserves user content outside managed sections`() {
        val projectDir = Files.createTempDirectory("bootstrap-in-place-rerun")
        FunctionalFixtureSupport.copyFixture(projectDir, "bootstrap-sample")

        FunctionalFixtureSupport.runner(projectDir, "cap4kBootstrap").build()
        projectDir.resolve("build.gradle.kts").appendText("\n// user customization survives rerun\n")

        val rerun = FunctionalFixtureSupport.runner(projectDir, "cap4kBootstrap").build()
        val buildContent = projectDir.resolve("build.gradle.kts").readText()

        assertTrue(rerun.output.contains("BUILD SUCCESSFUL"))
        assertTrue(buildContent.contains("// user customization survives rerun"))
    }

    @OptIn(ExperimentalPathApi::class)
    @Test
    fun `cap4kBootstrap fails in place against unmanaged existing root`() {
        val projectDir = Files.createTempDirectory("bootstrap-in-place-unmanaged")
        FunctionalFixtureSupport.copyFixture(projectDir, "bootstrap-unmanaged-root-sample")

        val result = FunctionalFixtureSupport.runner(projectDir, "cap4kBootstrap").buildAndFail()

        assertTrue(result.output.contains("recognized bootstrap host root"))
    }
}
```

- [ ] **Step 2: Run the guard and in-place tests and verify they fail**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-core:test :cap4k-plugin-pipeline-gradle:test --tests "*BootstrapRootStateGuardTest" --tests "*DefaultBootstrapRunnerTest" --tests "*PipelinePluginBootstrapInPlaceFunctionalTest"
```

Expected:

- guard tests fail because root validation does not exist
- in-place functional tests fail because the default fixture and root templates do not yet represent a marker-carrying host root

- [ ] **Step 3: Implement the root guard and self-hosting root templates**

```kotlin
// cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/BootstrapRootStateGuard.kt
object BootstrapRootStateGuard {
    fun validate(projectRoot: Path, config: BootstrapConfig) {
        if (config.mode == BootstrapMode.PREVIEW_SUBTREE) {
            return
        }

        validateManagedRootFile(projectRoot.resolve("build.gradle.kts"), "build.gradle.kts")
        validateManagedRootFile(projectRoot.resolve("settings.gradle.kts"), "settings.gradle.kts")
        listOf(
            config.modules.domainModuleName,
            config.modules.applicationModuleName,
            config.modules.adapterModuleName,
        ).forEach { moduleName ->
            val modulePath = projectRoot.resolve(moduleName)
            require(!Files.exists(modulePath) || Files.isDirectory(modulePath)) {
                "bootstrap.mode=IN_PLACE conflicts with existing non-directory path: $moduleName"
            }
        }
    }

    private fun validateManagedRootFile(path: Path, label: String) {
        require(Files.exists(path)) {
            "bootstrap.mode=IN_PLACE requires a recognized bootstrap host root; missing $label"
        }
        val content = Files.readString(path)
        require(content.contains("// [cap4k-bootstrap:managed-begin:root-host]")) {
            "bootstrap.mode=IN_PLACE requires a recognized bootstrap host root; missing managed markers in $label"
        }
        require(content.contains("// [cap4k-bootstrap:managed-end:root-host]")) {
            "bootstrap.mode=IN_PLACE requires a recognized bootstrap host root; missing managed markers in $label"
        }
    }
}
```

```kotlin
// cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultBootstrapRunner.kt
class DefaultBootstrapRunner(
    private val providers: List<BootstrapPresetProvider>,
    private val renderer: BootstrapRenderer,
    private val exporter: ArtifactExporter,
    private val beforeRun: (BootstrapConfig) -> Unit = {},
) : BootstrapRunner {
    override fun run(config: BootstrapConfig): BootstrapResult {
        beforeRun(config)
        val provider = providers.find { it.presetId == config.preset }
            ?: throw IllegalArgumentException("bootstrap preset has no registered bootstrap provider: ${config.preset}")
        val planItems = provider.plan(config)
        val renderedArtifacts = renderer.render(planItems)
        val writtenPaths = exporter.export(renderedArtifacts)
        return BootstrapResult(planItems, renderedArtifacts, writtenPaths)
    }
}
```

```kotlin
// cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePlugin.kt
internal fun buildBootstrapRunner(project: Project, config: BootstrapConfig, exportEnabled: Boolean): BootstrapRunner {
    return DefaultBootstrapRunner(
        providers = listOf(DddMultiModuleBootstrapPresetProvider()),
        renderer = PebbleBootstrapRenderer(
            PresetTemplateResolver(
                preset = config.templates.preset,
                overrideDirs = config.templates.overrideDirs,
            )
        ),
        exporter = if (exportEnabled) BootstrapFilesystemArtifactExporter(project.projectDir.toPath(), config) else NoopArtifactExporter(),
        beforeRun = { BootstrapRootStateGuard.validate(project.projectDir.toPath(), config) },
    )
}
```

```kotlin
// cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default-bootstrap/bootstrap/root/build.gradle.kts.peb
import com.only4.cap4k.plugin.pipeline.api.BootstrapMode

plugins {
    id("com.only4.cap4k.plugin.pipeline")
}

group = "{{ basePackage }}"
version = "0.1.0-SNAPSHOT"

// [cap4k-bootstrap:managed-begin:root-host]
cap4k {
    bootstrap {
        enabled.set(true)
        preset.set("ddd-multi-module")
        mode.set(BootstrapMode.{{ mode }})
{% if previewDir is not null %}
        previewDir.set("{{ previewDir }}")
{% endif %}
        projectName.set("{{ projectName }}")
        basePackage.set("{{ basePackage }}")
        modules {
            domainModuleName.set("{{ domainModuleName }}")
            applicationModuleName.set("{{ applicationModuleName }}")
            adapterModuleName.set("{{ adapterModuleName }}")
        }
    }
}
// [cap4k-bootstrap:managed-end:root-host]

subprojects {
    group = rootProject.group
    version = rootProject.version
}
```

```kotlin
// cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default-bootstrap/bootstrap/root/settings.gradle.kts.peb
pluginManagement {
    repositories {
        mavenLocal()
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenLocal()
        mavenCentral()
    }
}

// [cap4k-bootstrap:managed-begin:root-host]
rootProject.name = "{{ projectName }}"

include(":{{ domainModuleName }}")
include(":{{ applicationModuleName }}")
include(":{{ adapterModuleName }}")
// [cap4k-bootstrap:managed-end:root-host]
```

```kotlin
// cap4k-plugin-pipeline-gradle/src/test/resources/functional/bootstrap-sample/build.gradle.kts
plugins {
    id("com.only4.cap4k.plugin.pipeline")
}

group = "bootstrap-host"
version = "0.1.0-SNAPSHOT"

// [cap4k-bootstrap:managed-begin:root-host]
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
// [cap4k-bootstrap:managed-end:root-host]
```

```kotlin
// cap4k-plugin-pipeline-gradle/src/test/resources/functional/bootstrap-sample/settings.gradle.kts
// [cap4k-bootstrap:managed-begin:root-host]
rootProject.name = "bootstrap-host"
// [cap4k-bootstrap:managed-end:root-host]
```

```kotlin
// cap4k-plugin-pipeline-gradle/src/test/resources/functional/bootstrap-unmanaged-root-sample/build.gradle.kts
plugins {
    kotlin("jvm") version "2.2.20"
}
```

```kotlin
// cap4k-plugin-pipeline-gradle/src/test/resources/functional/bootstrap-unmanaged-root-sample/settings.gradle.kts
rootProject.name = "foreign-root"
```

- [ ] **Step 4: Run the guard and in-place tests again and verify they pass**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-core:test :cap4k-plugin-pipeline-gradle:test --tests "*BootstrapRootStateGuardTest" --tests "*PipelinePluginBootstrapInPlaceFunctionalTest"
```

Expected:

- in-place bootstrap succeeds against the minimal host sample
- rerun preserves user content outside markers
- unmanaged existing root is rejected
- the default bootstrap runner still fails cleanly when no provider is registered after the config shape change

- [ ] **Step 5: Commit**

```powershell
git add cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/BootstrapRootStateGuard.kt `
        cap4k-plugin-pipeline-core/src/test/kotlin/com/only4/cap4k/plugin/pipeline/core/BootstrapRootStateGuardTest.kt `
        cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultBootstrapRunner.kt `
        cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default-bootstrap/bootstrap/root/build.gradle.kts.peb `
        cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default-bootstrap/bootstrap/root/settings.gradle.kts.peb `
        cap4k-plugin-pipeline-gradle/src/test/resources/functional/bootstrap-sample/build.gradle.kts `
        cap4k-plugin-pipeline-gradle/src/test/resources/functional/bootstrap-sample/settings.gradle.kts `
        cap4k-plugin-pipeline-gradle/src/test/resources/functional/bootstrap-unmanaged-root-sample/build.gradle.kts `
        cap4k-plugin-pipeline-gradle/src/test/resources/functional/bootstrap-unmanaged-root-sample/settings.gradle.kts `
        cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginBootstrapInPlaceFunctionalTest.kt
git commit -m "feat: guard and upgrade in-place bootstrap roots"
```

## Task 5: Preserve Explicit Preview Mode and Verify Generated Preview Roots

**Files:**
- Modify: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/FunctionalFixtureSupport.kt`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginBootstrapFunctionalTest.kt`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginBootstrapGeneratedProjectFunctionalTest.kt`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/bootstrap-override-sample/build.gradle.kts`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/bootstrap-override-sample/settings.gradle.kts`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/bootstrap-generated-project-smoke-sample/build.gradle.kts`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/bootstrap-generated-project-override-sample/build.gradle.kts`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/bootstrap-preview-sample/build.gradle.kts`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/bootstrap-preview-sample/settings.gradle.kts`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/bootstrap-preview-sample/codegen/bootstrap-slots/root/README.md.peb`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/bootstrap-preview-sample/codegen/bootstrap-slots/domain-package/PreviewSlotMarker.kt.peb`
- Create: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginBootstrapPreviewFunctionalTest.kt`

- [ ] **Step 1: Add the failing preview-mode tests**

```kotlin
// cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginBootstrapPreviewFunctionalTest.kt
class PipelinePluginBootstrapPreviewFunctionalTest {

    @OptIn(ExperimentalPathApi::class)
    @Test
    fun `explicit preview mode writes generated project under preview dir while keeping project identity`() {
        val projectDir = Files.createTempDirectory("bootstrap-preview-mode")
        FunctionalFixtureSupport.copyFixture(projectDir, "bootstrap-preview-sample")

        val result = FunctionalFixtureSupport.runner(projectDir, "cap4kBootstrap").build()
        val generatedSettings = projectDir.resolve("bootstrap-preview/settings.gradle.kts").readText()

        assertTrue(result.output.contains("BUILD SUCCESSFUL"))
        assertTrue(projectDir.resolve("bootstrap-preview/build.gradle.kts").toFile().exists())
        assertTrue(projectDir.resolve("bootstrap-preview/only-danmuku-domain/build.gradle.kts").toFile().exists())
        assertTrue(generatedSettings.contains("rootProject.name = \"only-danmuku\""))
        assertFalse(generatedSettings.contains("rootProject.name = \"bootstrap-preview\""))
    }

    @OptIn(ExperimentalPathApi::class)
    @Test
    fun `generated preview root can rerun cap4k bootstrap plan through plugin classpath`() {
        val projectDir = Files.createTempDirectory("bootstrap-preview-rerun")
        FunctionalFixtureSupport.copyFixture(projectDir, "bootstrap-preview-sample")

        FunctionalFixtureSupport.runner(projectDir, "cap4kBootstrap").build()
        val generatedResult = FunctionalFixtureSupport.generatedProjectRunner(
            projectDir,
            generatedDirName = "bootstrap-preview",
            "cap4kBootstrapPlan",
        ).build()

        assertTrue(generatedResult.output.contains("BUILD SUCCESSFUL"))
        assertTrue(projectDir.resolve("bootstrap-preview/build/cap4k/bootstrap-plan.json").toFile().exists())
    }
}
```

- [ ] **Step 2: Run the preview tests and verify they fail**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-gradle:test --tests "*PipelinePluginBootstrapPreviewFunctionalTest"
```

Expected:

- tests fail because no explicit preview fixture exists
- generated project runner still assumes `projectName` equals generated directory name
- generated project runner does not inject plugin classpath for rerun verification
- legacy generated-project smoke tests still assume subtree output is the default contract

- [ ] **Step 3: Add explicit preview fixtures and generated preview runner support**

```kotlin
// cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/FunctionalFixtureSupport.kt
fun generatedProjectRunner(
    fixtureDir: Path,
    generatedDirName: String,
    vararg arguments: String,
): GradleRunner {
    val generatedDir = generatedProjectDir(fixtureDir, generatedDirName)
    return GradleRunner.create()
        .withProjectDir(generatedDir.toFile())
        .withPluginClasspath()
        .withArguments(*arguments)
}

fun generatedProjectDir(fixtureDir: Path, generatedDirName: String): Path {
    val generated = fixtureDir.resolve(generatedDirName)
    require(Files.isDirectory(generated)) {
        "Generated project directory not found: $generated"
    }
    return generated
}
```

```kotlin
// cap4k-plugin-pipeline-gradle/src/test/resources/functional/bootstrap-preview-sample/build.gradle.kts
import com.only4.cap4k.plugin.pipeline.api.BootstrapMode

plugins {
    id("com.only4.cap4k.plugin.pipeline")
}

// [cap4k-bootstrap:managed-begin:root-host]
cap4k {
    bootstrap {
        enabled.set(true)
        preset.set("ddd-multi-module")
        mode.set(BootstrapMode.PREVIEW_SUBTREE)
        previewDir.set("bootstrap-preview")
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
// [cap4k-bootstrap:managed-end:root-host]
```

```kotlin
// cap4k-plugin-pipeline-gradle/src/test/resources/functional/bootstrap-preview-sample/settings.gradle.kts
// [cap4k-bootstrap:managed-begin:root-host]
rootProject.name = "bootstrap-preview-host"
// [cap4k-bootstrap:managed-end:root-host]
```

```text
# cap4k-plugin-pipeline-gradle/src/test/resources/functional/bootstrap-preview-sample/codegen/bootstrap-slots/root/README.md.peb
# {{ projectName }} preview
```

```kotlin
// cap4k-plugin-pipeline-gradle/src/test/resources/functional/bootstrap-preview-sample/codegen/bootstrap-slots/domain-package/PreviewSlotMarker.kt.peb
package {{ basePackage }}.domain

object PreviewSlotMarker
```

```kotlin
// cap4k-plugin-pipeline-gradle/src/test/resources/functional/bootstrap-override-sample/build.gradle.kts
import com.only4.cap4k.plugin.pipeline.api.BootstrapMode

plugins {
    id("com.only4.cap4k.plugin.pipeline")
}

// [cap4k-bootstrap:managed-begin:root-host]
cap4k {
    bootstrap {
        enabled.set(true)
        preset.set("ddd-multi-module")
        mode.set(BootstrapMode.PREVIEW_SUBTREE)
        previewDir.set("bootstrap-preview")
        projectName.set("only-danmuku")
        basePackage.set("edu.only4.danmuku")
        modules {
            domainModuleName.set("only-danmuku-domain")
            applicationModuleName.set("only-danmuku-application")
            adapterModuleName.set("only-danmuku-adapter")
        }
        templates {
            preset.set("ddd-default-bootstrap")
            overrideDirs.from("codegen/bootstrap-templates")
        }
    }
}
// [cap4k-bootstrap:managed-end:root-host]
```

```kotlin
// cap4k-plugin-pipeline-gradle/src/test/resources/functional/bootstrap-override-sample/settings.gradle.kts
// [cap4k-bootstrap:managed-begin:root-host]
rootProject.name = "bootstrap-override-host"
// [cap4k-bootstrap:managed-end:root-host]
```

```kotlin
// cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginBootstrapFunctionalTest.kt
@Test
fun `cap4kBootstrap supports fixed template override dirs`() {
    val projectDir = Files.createTempDirectory("pipeline-functional-bootstrap-override")
    FunctionalFixtureSupport.copyFixture(projectDir, "bootstrap-override-sample")

    val result = FunctionalFixtureSupport.runner(projectDir, "cap4kBootstrap").build()
    val generatedRootBuild = projectDir.resolve("bootstrap-preview/build.gradle.kts").readText()

    assertTrue(result.output.contains("BUILD SUCCESSFUL"))
    assertTrue(generatedRootBuild.contains("// override: bootstrap root build template"))
}
```

Also migrate the legacy generated-project smoke fixtures/tests so they opt into `PREVIEW_SUBTREE` explicitly instead of relying on the old implicit `<projectName>/` subtree default.
Keep the coverage, but make the mode explicit.

- [ ] **Step 4: Run the preview tests plus bootstrap override test and verify they pass**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-gradle:test --tests "*PipelinePluginBootstrapPreviewFunctionalTest" --tests "*PipelinePluginBootstrapFunctionalTest" --tests "*PipelinePluginBootstrapGeneratedProjectFunctionalTest"
```

Expected:

- explicit preview mode still works
- preview output goes to `bootstrap-preview/`
- generated preview root can rerun `cap4kBootstrapPlan`
- template override sample still passes under explicit preview mode
- legacy generated-project smoke coverage now uses explicit preview-mode fixtures rather than an implicit subtree default

- [ ] **Step 5: Commit**

```powershell
git add cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/FunctionalFixtureSupport.kt `
        cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginBootstrapFunctionalTest.kt `
        cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginBootstrapGeneratedProjectFunctionalTest.kt `
        cap4k-plugin-pipeline-gradle/src/test/resources/functional/bootstrap-override-sample/build.gradle.kts `
        cap4k-plugin-pipeline-gradle/src/test/resources/functional/bootstrap-override-sample/settings.gradle.kts `
        cap4k-plugin-pipeline-gradle/src/test/resources/functional/bootstrap-generated-project-smoke-sample/build.gradle.kts `
        cap4k-plugin-pipeline-gradle/src/test/resources/functional/bootstrap-generated-project-override-sample/build.gradle.kts `
        cap4k-plugin-pipeline-gradle/src/test/resources/functional/bootstrap-preview-sample `
        cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginBootstrapPreviewFunctionalTest.kt
git commit -m "test: keep bootstrap preview mode explicit"
```

## Task 6: Run the Full Bootstrap Contract Verification Sweep

**Files:**
- Modify: `cap4k-plugin-pipeline-gradle/README.md`

- [ ] **Step 1: Update README to match the new public bootstrap contract**

Update the bootstrap DSL and output examples so the public docs no longer claim that `projectName` implies the default output subtree.

Required doc changes:

- show `mode = IN_PLACE` as the official default
- show `PREVIEW_SUBTREE` + `previewDir` as the explicit tutorial/demo mode
- change the bootstrap output tree example from `{projectName}/...` to an in-place root layout
- explain that `projectName` controls generated project identity, not filesystem prefixing

- [ ] **Step 2: Run the focused bootstrap verification sweep**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-api:test `
          :cap4k-plugin-pipeline-bootstrap:test `
          :cap4k-plugin-pipeline-core:test `
          :cap4k-plugin-pipeline-gradle:test `
          --tests "*BootstrapModelsTest" `
          --tests "*PipelinePluginTest" `
          --tests "*DddMultiModuleBootstrapPresetProviderTest" `
          --tests "*DefaultBootstrapRunnerTest" `
          --tests "*BootstrapManagedSectionMergerTest" `
          --tests "*BootstrapFilesystemArtifactExporterTest" `
          --tests "*BootstrapRootStateGuardTest" `
          --tests "*PipelinePluginBootstrapFunctionalTest" `
          --tests "*PipelinePluginBootstrapInPlaceFunctionalTest" `
          --tests "*PipelinePluginBootstrapPreviewFunctionalTest" `
          --tests "*PipelinePluginBootstrapGeneratedProjectFunctionalTest"
```

Expected:

- all bootstrap-specific unit and functional tests pass
- no assertions remain that treat `projectName` as the default output prefix
- README examples and DSL reference match the implemented contract

- [ ] **Step 3: Run a second sweep through the public Gradle entry points**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-gradle:test --tests "*cap4kBootstrap*"
```

Expected:

- all bootstrap plan/generate entry-point tests pass
- in-place and preview mode both remain covered through the public plugin tasks

- [ ] **Step 4: Confirm the worktree is clean except for intended changes**

Run:

```powershell
git status --short
```

Expected:

- no unexpected modified files
- only files listed in this plan were touched during the slice

- [ ] **Step 5: Final commit if verification required fixes**

```powershell
git add -A
git commit -m "test: harden bootstrap in-place root contract"
```

Expected:

- if verification did not require follow-up fixes, skip this step because earlier task commits already captured the work

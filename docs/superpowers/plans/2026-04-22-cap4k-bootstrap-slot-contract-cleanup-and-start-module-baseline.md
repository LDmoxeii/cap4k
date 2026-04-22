# Cap4k Bootstrap Slot Contract Cleanup And Start-Module Baseline Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Expand the bootstrap baseline to four default roles (`domain`, `application`, `adapter`, `start`), correct slot semantics, add module-resource slots, and remove default marker noise.

**Architecture:** Push the contract change from the public API and Gradle DSL inward, then rewire preset planning and Pebble templates to emit the new four-module baseline. Lock each behavior with focused unit and functional tests before updating README and the capability matrix so the public contract only moves after code and fixtures prove it.

**Tech Stack:** Kotlin, Gradle TestKit, JUnit 5, Pebble templates, Spring Boot bootstrap templates

---

## File Structure

### API And DSL Surface

- Modify: `cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/BootstrapModels.kt`
  - Add `startModuleName` to `BootstrapModulesConfig`
  - Add `MODULE_RESOURCES` to `BootstrapSlotKind`
  - Add `module-resources:<role>` slot-id mapping
- Modify: `cap4k-plugin-pipeline-api/src/test/kotlin/com/only4/cap4k/plugin/pipeline/api/BootstrapModelsTest.kt`
  - Lock the new model and slot-id contract
- Modify: `cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kExtension.kt`
  - Expose `startModuleName`
  - Expose `moduleResources(role)`
- Modify: `cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kBootstrapConfigFactory.kt`
  - Accept the `start` role
  - Materialize `startModuleName`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kBootstrapConfigFactoryTest.kt`
  - Lock config translation for `start` and `moduleResources`

### Planner And Template Surface

- Modify: `cap4k-plugin-pipeline-bootstrap/src/main/kotlin/com/only4/cap4k/plugin/pipeline/bootstrap/DddMultiModuleBootstrapPresetProvider.kt`
  - Add the `start` role to the preset context
  - Remove marker planning
  - Re-map `modulePackage` to role-specific package roots
  - Add `MODULE_RESOURCES` output-path routing
- Modify: `cap4k-plugin-pipeline-bootstrap/src/test/kotlin/com/only4/cap4k/plugin/pipeline/bootstrap/DddMultiModuleBootstrapPresetProviderTest.kt`
  - Lock start-module fixed outputs, package routing, resource routing, and marker removal
- Modify: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default-bootstrap/bootstrap/root/settings.gradle.kts.peb`
  - Include the configured `start` module in managed settings
- Modify: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default-bootstrap/bootstrap/root/build.gradle.kts.peb`
  - Round-trip `startModuleName`
  - Round-trip `moduleResources(...)`
- Create: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default-bootstrap/bootstrap/module/start-build.gradle.kts.peb`
  - Minimal runnable Spring Boot host module
- Create: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default-bootstrap/bootstrap/module/start-application.kt.peb`
  - Minimal `StartApplication.kt`
- Delete: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default-bootstrap/bootstrap/module/package-marker.kt.peb`
  - No longer used after marker removal

### Core Guard And Functional Surface

- Modify: `cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/BootstrapRootStateGuard.kt`
  - Validate the `start` module path like the other three module roots
- Modify: `cap4k-plugin-pipeline-core/src/test/kotlin/com/only4/cap4k/plugin/pipeline/core/BootstrapRootStateGuardTest.kt`
  - Lock the fourth module path in the guard
- Modify: `cap4k-plugin-pipeline-core/src/test/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultBootstrapRunnerTest.kt`
  - Update fixture config shape to include `startModuleName`
- Modify: `cap4k-plugin-pipeline-core/src/test/kotlin/com/only4/cap4k/plugin/pipeline/core/BootstrapFilesystemArtifactExporterTest.kt`
  - Update bootstrap config shape to include `startModuleName`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginBootstrapFunctionalTest.kt`
  - Lock plan JSON for four modules and `module-resources:start`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginBootstrapInPlaceFunctionalTest.kt`
  - Lock managed root round-trip for `startModuleName` and `moduleResources`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginBootstrapGeneratedProjectFunctionalTest.kt`
  - Verify generated preview project now includes and compiles `start`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginTest.kt`
  - Update bootstrap config shape to include `startModuleName`
- Modify fixture roots under:
  - `cap4k-plugin-pipeline-gradle/src/test/resources/functional/bootstrap-sample`
  - `cap4k-plugin-pipeline-gradle/src/test/resources/functional/bootstrap-preview-sample`
  - `cap4k-plugin-pipeline-gradle/src/test/resources/functional/bootstrap-generated-project-smoke-sample`
  - `cap4k-plugin-pipeline-gradle/src/test/resources/functional/bootstrap-generated-project-override-sample`
  - `cap4k-plugin-pipeline-gradle/src/test/resources/functional/bootstrap-invalid-slot-sample`
  - `cap4k-plugin-pipeline-gradle/src/test/resources/functional/bootstrap-generated-project-invalid-sample`
  - Add `start-package` and `start-resources` slot trees where the test needs visible output
  - Replace old invalid `start` role samples with an actually invalid role such as `portal`

### Public Docs

- Modify: `cap4k-plugin-pipeline-gradle/README.md`
  - Update the bootstrap examples, slot table, generated tree, and managed-root snippets
- Modify: `docs/superpowers/capability-matrix.md`
  - Add `bootstrap.slot_bundle`
  - Add `bootstrap.start_module_baseline`

## Task 1: Extend The Bootstrap API And Gradle DSL

**Files:**
- Modify: `cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/BootstrapModels.kt`
- Modify: `cap4k-plugin-pipeline-api/src/test/kotlin/com/only4/cap4k/plugin/pipeline/api/BootstrapModelsTest.kt`
- Modify: `cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kExtension.kt`
- Modify: `cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kBootstrapConfigFactory.kt`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kBootstrapConfigFactoryTest.kt`

- [ ] **Step 1: Write the failing model/config tests**

```kotlin
@Test
fun `bootstrap config includes start module name`() {
    val config = BootstrapConfig(
        preset = "ddd-multi-module",
        projectName = "demo",
        basePackage = "com.acme.demo",
        modules = BootstrapModulesConfig(
            domainModuleName = "demo-domain",
            applicationModuleName = "demo-application",
            adapterModuleName = "demo-adapter",
            startModuleName = "demo-start",
        ),
        templates = BootstrapTemplateConfig("ddd-default-bootstrap", emptyList()),
        slots = emptyList(),
        conflictPolicy = ConflictPolicy.FAIL,
        mode = BootstrapMode.IN_PLACE,
        previewDir = null,
    )

    assertEquals("demo-start", config.modules.startModuleName)
}

@Test
fun `bootstrap slot id maps module resources kind`() {
    val binding = BootstrapSlotBinding(
        kind = BootstrapSlotKind.MODULE_RESOURCES,
        role = "start",
        sourceDir = "codegen/bootstrap-slots/start-resources",
    )

    assertEquals("module-resources:start", binding.slotId)
}
```

```kotlin
@Test
fun `build accepts start module name and module resources binding`() {
    val project = ProjectBuilder.builder().build()
    val extension = project.extensions.create("cap4k", Cap4kExtension::class.java)

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
        slots {
            moduleResources("start").from("codegen/bootstrap-slots/start-resources")
        }
    }

    val config = Cap4kBootstrapConfigFactory().build(project, extension)

    assertEquals("only-danmuku-start", config.modules.startModuleName)
    assertEquals(listOf("module-resources:start"), config.slots.map { it.slotId })
}
```

- [ ] **Step 2: Run the targeted tests and confirm they fail**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-api:test --tests "*BootstrapModelsTest" :cap4k-plugin-pipeline-gradle:test --tests "*Cap4kBootstrapConfigFactoryTest"
```

Expected:
- compilation fails because `startModuleName`, `MODULE_RESOURCES`, and `moduleResources(...)` do not exist yet

- [ ] **Step 3: Implement the API, DSL, and config translation**

```kotlin
data class BootstrapModulesConfig(
    val domainModuleName: String,
    val applicationModuleName: String,
    val adapterModuleName: String,
    val startModuleName: String,
)

enum class BootstrapSlotKind {
    ROOT,
    BUILD_LOGIC,
    MODULE_ROOT,
    MODULE_PACKAGE,
    MODULE_RESOURCES,
}

val slotId: String =
    when (kind) {
        BootstrapSlotKind.ROOT -> "root"
        BootstrapSlotKind.BUILD_LOGIC -> "build-logic"
        BootstrapSlotKind.MODULE_ROOT -> "module-root:${requireNotNull(role)}"
        BootstrapSlotKind.MODULE_PACKAGE -> "module-package:${requireNotNull(role)}"
        BootstrapSlotKind.MODULE_RESOURCES -> "module-resources:${requireNotNull(role)}"
    }
```

```kotlin
open class Cap4kBootstrapModulesExtension @Inject constructor(objects: ObjectFactory) {
    val domainModuleName: Property<String> = objects.property(String::class.java)
    val applicationModuleName: Property<String> = objects.property(String::class.java)
    val adapterModuleName: Property<String> = objects.property(String::class.java)
    val startModuleName: Property<String> = objects.property(String::class.java)
}

private val moduleResources: MutableMap<String, ConfigurableFileCollection> = linkedMapOf()

fun moduleResources(role: String): ConfigurableFileCollection =
    moduleResources.getOrPut(role) { objects.fileCollection() }

moduleResources.forEach { (role, sourceDirs) ->
    addBindings(project, BootstrapSlotKind.MODULE_RESOURCES, role, sourceDirs)
}
```

```kotlin
require(binding.role in setOf("domain", "application", "adapter", "start")) {
    "unsupported bootstrap slot role: ${binding.role}"
}

modules = BootstrapModulesConfig(
    domainModuleName = extension.bootstrap.modules.domainModuleName.required("bootstrap.modules.domainModuleName"),
    applicationModuleName = extension.bootstrap.modules.applicationModuleName.required("bootstrap.modules.applicationModuleName"),
    adapterModuleName = extension.bootstrap.modules.adapterModuleName.required("bootstrap.modules.adapterModuleName"),
    startModuleName = extension.bootstrap.modules.startModuleName.required("bootstrap.modules.startModuleName"),
)
```

- [ ] **Step 4: Run the targeted tests and confirm they pass**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-api:test --tests "*BootstrapModelsTest" :cap4k-plugin-pipeline-gradle:test --tests "*Cap4kBootstrapConfigFactoryTest"
```

Expected:
- `BUILD SUCCESSFUL`
- `BootstrapModelsTest` and `Cap4kBootstrapConfigFactoryTest` both green

- [ ] **Step 5: Commit**

```powershell
git add cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/BootstrapModels.kt cap4k-plugin-pipeline-api/src/test/kotlin/com/only4/cap4k/plugin/pipeline/api/BootstrapModelsTest.kt cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kExtension.kt cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kBootstrapConfigFactory.kt cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kBootstrapConfigFactoryTest.kt
git commit -m "feat: extend bootstrap api for start and resource slots"
```

## Task 2: Rewire Preset Planning And Slot Routing

**Files:**
- Modify: `cap4k-plugin-pipeline-bootstrap/src/main/kotlin/com/only4/cap4k/plugin/pipeline/bootstrap/DddMultiModuleBootstrapPresetProvider.kt`
- Modify: `cap4k-plugin-pipeline-bootstrap/src/test/kotlin/com/only4/cap4k/plugin/pipeline/bootstrap/DddMultiModuleBootstrapPresetProviderTest.kt`

- [ ] **Step 1: Write the failing planner tests**

```kotlin
@Test
fun `plan emits start module build and start application templates`() {
    val items = DddMultiModuleBootstrapPresetProvider().plan(config)

    assertTrue(items.any {
        it.templateId == "bootstrap/module/start-build.gradle.kts.peb" &&
            it.outputPath == "only-danmuku-start/build.gradle.kts"
    })
    assertTrue(items.any {
        it.templateId == "bootstrap/module/start-application.kt.peb" &&
            it.outputPath == "only-danmuku-start/src/main/kotlin/edu/only4/danmuku/StartApplication.kt"
    })
}

@Test
fun `plan no longer emits bootstrap marker templates`() {
    val items = DddMultiModuleBootstrapPresetProvider().plan(config)

    assertTrue(items.none { it.templateId == "bootstrap/module/package-marker.kt.peb" })
}
```

```kotlin
@Test
fun `module package output path resolves role specific root`() {
    val outputPath = resolveSlotOutputPath(
        binding = BootstrapSlotBinding(
            kind = BootstrapSlotKind.MODULE_PACKAGE,
            role = "adapter",
            sourceDir = "src/test/resources/slots/adapter-package",
        ),
        renderedRelativePath = "config/jimmer/UserMessageExtendScalarProvider.kt",
        config = config,
    )

    assertEquals(
        "only-danmuku-adapter/src/main/kotlin/edu/only4/danmuku/adapter/config/jimmer/UserMessageExtendScalarProvider.kt",
        outputPath,
    )
}

@Test
fun `module resources output path resolves module resources root`() {
    val outputPath = resolveSlotOutputPath(
        binding = BootstrapSlotBinding(
            kind = BootstrapSlotKind.MODULE_RESOURCES,
            role = "start",
            sourceDir = "src/test/resources/slots/start-resources",
        ),
        renderedRelativePath = "application.yml",
        config = config,
    )

    assertEquals("only-danmuku-start/src/main/resources/application.yml", outputPath)
}
```

- [ ] **Step 2: Run the planner tests and confirm they fail**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-bootstrap:test --tests "*DddMultiModuleBootstrapPresetProviderTest"
```

Expected:
- failing assertions because the preset still emits only three modules, still emits marker files, and still maps `modulePackage` to `{basePackage}/...`

- [ ] **Step 3: Implement start-module planning, honest package routing, and resource routing**

```kotlin
private val moduleRoles: Set<String> = setOf("domain", "application", "adapter", "start")

internal fun bootstrapContext(config: BootstrapConfig): Map<String, Any?> =
    mapOf(
        "projectName" to config.projectName,
        "basePackage" to config.basePackage,
        "basePackagePath" to config.basePackagePath(),
        "domainModuleName" to config.modules.domainModuleName,
        "applicationModuleName" to config.modules.applicationModuleName,
        "adapterModuleName" to config.modules.adapterModuleName,
        "startModuleName" to config.modules.startModuleName,
        "templatePreset" to config.templates.preset,
        "templateOverrideDirs" to config.templates.overrideDirs.map(::normalizeDslPathLiteral),
        "slotBindings" to config.slots.map(::toRenderModel),
        "conflictPolicy" to config.conflictPolicy.name,
        "mode" to config.mode.name,
        "previewDir" to config.previewDir?.let(::normalizeDslPathLiteral),
    )
```

```kotlin
add(
    fixed(
        templateId = "bootstrap/module/start-build.gradle.kts.peb",
        outputPath = rebaseOutputPath("${config.modules.startModuleName}/build.gradle.kts", config),
        config = config,
        context = context + mapOf("moduleRole" to "start"),
    )
)
add(
    fixed(
        templateId = "bootstrap/module/start-application.kt.peb",
        outputPath = rebaseOutputPath(
            "${config.modules.startModuleName}/src/main/kotlin/${config.basePackagePath()}/StartApplication.kt",
            config,
        ),
        config = config,
        context = context + mapOf("moduleRole" to "start"),
    )
)
```

```kotlin
internal fun resolveModuleName(role: String, config: BootstrapConfig): String =
    when (role) {
        "domain" -> config.modules.domainModuleName
        "application" -> config.modules.applicationModuleName
        "adapter" -> config.modules.adapterModuleName
        "start" -> config.modules.startModuleName
        else -> throw IllegalArgumentException("unsupported bootstrap slot role: $role")
    }

internal fun validateBootstrapPathSegments(config: BootstrapConfig) {
    requireSafePathSegment("bootstrap.projectName", config.projectName)
    requireSafePathSegment("bootstrap.modules.domainModuleName", config.modules.domainModuleName)
    requireSafePathSegment("bootstrap.modules.applicationModuleName", config.modules.applicationModuleName)
    requireSafePathSegment("bootstrap.modules.adapterModuleName", config.modules.adapterModuleName)
    requireSafePathSegment("bootstrap.modules.startModuleName", config.modules.startModuleName)
}
```

```kotlin
internal fun resolveSlotOutputPath(
    binding: BootstrapSlotBinding,
    renderedRelativePath: String,
    config: BootstrapConfig,
): String {
    val boundedRelative = normalizeRelativePath(renderedRelativePath)
    val moduleName = binding.role?.let { resolveModuleName(it, config) }
    return when (binding.kind) {
        BootstrapSlotKind.ROOT -> rebaseOutputPath(boundedRelative, config)
        BootstrapSlotKind.BUILD_LOGIC -> rebaseOutputPath("build-logic/$boundedRelative", config)
        BootstrapSlotKind.MODULE_ROOT -> rebaseOutputPath("${requireNotNull(moduleName)}/$boundedRelative", config)
        BootstrapSlotKind.MODULE_PACKAGE -> rebaseOutputPath("${requireNotNull(moduleName)}/src/main/kotlin/${resolveRolePackageRoot(binding.role!!, config)}/$boundedRelative", config)
        BootstrapSlotKind.MODULE_RESOURCES -> rebaseOutputPath("${requireNotNull(moduleName)}/src/main/resources/$boundedRelative", config)
    }
}

internal fun resolveRolePackageRoot(role: String, config: BootstrapConfig): String =
    when (role) {
        "domain" -> "${config.basePackagePath()}/domain"
        "application" -> "${config.basePackagePath()}/application"
        "adapter" -> "${config.basePackagePath()}/adapter"
        "start" -> config.basePackagePath()
        else -> throw IllegalArgumentException("unsupported bootstrap slot role: $role")
    }
```

- [ ] **Step 4: Run the planner tests and confirm they pass**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-bootstrap:test --tests "*DddMultiModuleBootstrapPresetProviderTest"
```

Expected:
- `BUILD SUCCESSFUL`
- planner tests prove start outputs, role-shaped package roots, resource slot routing, and marker removal

- [ ] **Step 5: Commit**

```powershell
git add cap4k-plugin-pipeline-bootstrap/src/main/kotlin/com/only4/cap4k/plugin/pipeline/bootstrap/DddMultiModuleBootstrapPresetProvider.kt cap4k-plugin-pipeline-bootstrap/src/test/kotlin/com/only4/cap4k/plugin/pipeline/bootstrap/DddMultiModuleBootstrapPresetProviderTest.kt
git commit -m "feat: rewire bootstrap preset for start module and slot cleanup"
```

## Task 3: Render The New Four-Module Bootstrap Baseline

**Files:**
- Modify: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default-bootstrap/bootstrap/root/settings.gradle.kts.peb`
- Modify: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default-bootstrap/bootstrap/root/build.gradle.kts.peb`
- Create: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default-bootstrap/bootstrap/module/start-build.gradle.kts.peb`
- Create: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default-bootstrap/bootstrap/module/start-application.kt.peb`
- Delete: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default-bootstrap/bootstrap/module/package-marker.kt.peb`
- Modify: `cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/BootstrapRootStateGuard.kt`
- Modify: `cap4k-plugin-pipeline-core/src/test/kotlin/com/only4/cap4k/plugin/pipeline/core/BootstrapRootStateGuardTest.kt`
- Modify: `cap4k-plugin-pipeline-core/src/test/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultBootstrapRunnerTest.kt`
- Modify: `cap4k-plugin-pipeline-core/src/test/kotlin/com/only4/cap4k/plugin/pipeline/core/BootstrapFilesystemArtifactExporterTest.kt`

- [ ] **Step 1: Write the failing core/template tests**

```kotlin
@Test
fun `validate accepts configured start module path inside project root`() {
    val root = Files.createTempDirectory("bootstrap-root-guard")
    Files.writeString(root.resolve("build.gradle.kts"), "// [cap4k-bootstrap:managed-begin:root-host]\ncap4k { bootstrap { enabled.set(true) } }\n// [cap4k-bootstrap:managed-end:root-host]\n")
    Files.writeString(root.resolve("settings.gradle.kts"), "// [cap4k-bootstrap:managed-begin:root-host]\nrootProject.name = \"demo\"\n// [cap4k-bootstrap:managed-end:root-host]\n")

    BootstrapRootStateGuard(root).validate(
        BootstrapConfig(
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
    )
}
```

```kotlin
@Test
fun `default bootstrap runner uses four module config`() {
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

    assertEquals("demo-start", config.modules.startModuleName)
}
```

- [ ] **Step 2: Run the targeted core tests and confirm they fail**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-core:test --tests "*BootstrapRootStateGuardTest" --tests "*DefaultBootstrapRunnerTest" --tests "*BootstrapFilesystemArtifactExporterTest"
```

Expected:
- compilation or assertion failure because the guard and runner tests still use the old three-module shape

- [ ] **Step 3: Implement the four-module templates and core guard updates**

```kotlin
// settings.gradle.kts.peb
// [cap4k-bootstrap:managed-begin:root-host]
rootProject.name = "{{ projectName }}"

include(":{{ domainModuleName }}")
include(":{{ applicationModuleName }}")
include(":{{ adapterModuleName }}")
include(":{{ startModuleName }}")
// [cap4k-bootstrap:managed-end:root-host]
```

```kotlin
// build.gradle.kts.peb managed section excerpt
modules {
    domainModuleName.set("{{ domainModuleName }}")
    applicationModuleName.set("{{ applicationModuleName }}")
    adapterModuleName.set("{{ adapterModuleName }}")
    startModuleName.set("{{ startModuleName }}")
}
slots {
{% if slot.kind == "MODULE_ROOT" %}
    moduleRoot("{{ slot.role }}").from("{{ slot.sourceDir }}")
{% elseif slot.kind == "MODULE_PACKAGE" %}
    modulePackage("{{ slot.role }}").from("{{ slot.sourceDir }}")
{% elseif slot.kind == "MODULE_RESOURCES" %}
    moduleResources("{{ slot.role }}").from("{{ slot.sourceDir }}")
{% endif %}
}
```

```kotlin
// start-build.gradle.kts.peb
plugins {
    kotlin("jvm") version "2.2.20"
    kotlin("plugin.spring") version "2.2.20"
    id("org.springframework.boot") version "3.5.6"
}

group = "{{ basePackage }}"
version = "0.1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation(project(":{{ adapterModuleName }}"))
    implementation(project(":{{ applicationModuleName }}"))
    implementation(project(":{{ domainModuleName }}"))
}

kotlin {
    jvmToolchain(17)
}
```

```kotlin
// start-application.kt.peb
package {{ basePackage }}

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.boot.runApplication
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication(proxyBeanMethods = false)
@EnableScheduling
@EnableJpaRepositories(basePackages = ["{{ basePackage }}.adapter.domain.repositories"])
@EntityScan(basePackages = ["{{ basePackage }}.domain.aggregates"])
class StartApplication

fun main(args: Array<String>) {
    runApplication<StartApplication>(*args)
}
```

```kotlin
private fun validateModulePaths(config: BootstrapConfig) {
    moduleRoot(config, config.modules.domainModuleName)
    moduleRoot(config, config.modules.applicationModuleName)
    moduleRoot(config, config.modules.adapterModuleName)
    moduleRoot(config, config.modules.startModuleName)
}
```

- [ ] **Step 4: Run the targeted core tests and confirm they pass**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-core:test --tests "*BootstrapRootStateGuardTest" --tests "*DefaultBootstrapRunnerTest" --tests "*BootstrapFilesystemArtifactExporterTest"
```

Expected:
- `BUILD SUCCESSFUL`
- guard and runner tests accept the new four-module baseline

- [ ] **Step 5: Commit**

```powershell
git add cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default-bootstrap/bootstrap/root/settings.gradle.kts.peb cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default-bootstrap/bootstrap/root/build.gradle.kts.peb cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default-bootstrap/bootstrap/module/start-build.gradle.kts.peb cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default-bootstrap/bootstrap/module/start-application.kt.peb cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/BootstrapRootStateGuard.kt cap4k-plugin-pipeline-core/src/test/kotlin/com/only4/cap4k/plugin/pipeline/core/BootstrapRootStateGuardTest.kt cap4k-plugin-pipeline-core/src/test/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultBootstrapRunnerTest.kt cap4k-plugin-pipeline-core/src/test/kotlin/com/only4/cap4k/plugin/pipeline/core/BootstrapFilesystemArtifactExporterTest.kt
git add -A cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default-bootstrap/bootstrap/module
git commit -m "feat: render four-module bootstrap baseline"
```

## Task 4: Update Functional Fixtures And End-To-End Bootstrap Verification

**Files:**
- Modify: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginBootstrapFunctionalTest.kt`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginBootstrapInPlaceFunctionalTest.kt`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginBootstrapGeneratedProjectFunctionalTest.kt`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginTest.kt`
- Modify fixture files under:
  - `cap4k-plugin-pipeline-gradle/src/test/resources/functional/bootstrap-sample`
  - `cap4k-plugin-pipeline-gradle/src/test/resources/functional/bootstrap-preview-sample`
  - `cap4k-plugin-pipeline-gradle/src/test/resources/functional/bootstrap-generated-project-smoke-sample`
  - `cap4k-plugin-pipeline-gradle/src/test/resources/functional/bootstrap-generated-project-override-sample`
  - `cap4k-plugin-pipeline-gradle/src/test/resources/functional/bootstrap-invalid-slot-sample`
  - `cap4k-plugin-pipeline-gradle/src/test/resources/functional/bootstrap-generated-project-invalid-sample`

- [ ] **Step 1: Write the failing functional assertions**

```kotlin
assertTrue(planContent.contains("\"outputPath\": \"only-danmuku-start/build.gradle.kts\""))
assertTrue(planContent.contains("\"slotId\": \"module-resources:start\""))
```

```kotlin
assertTrue(projectDir.resolve("only-danmuku-start/build.gradle.kts").toFile().exists())
assertTrue(projectDir.resolve("only-danmuku-start/src/main/resources/application.yml").toFile().exists())
assertTrue(buildFile.contains("startModuleName.set(\"only-danmuku-start\")"))
assertTrue(buildFile.contains("moduleResources(\"start\").from(\"codegen/bootstrap-slots/start-resources\")"))
assertTrue(settingsFile.contains("include(\":only-danmuku-start\")"))
```

```kotlin
val generatedStartMain = fixtureDir.resolve(
    "bootstrap-preview/only-danmuku-start/src/main/kotlin/edu/only4/danmuku/StartApplication.kt"
)
assertTrue(generatedStartMain.toFile().exists())
```

- [ ] **Step 2: Run the functional tests and confirm they fail**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-gradle:test --tests "*PipelinePluginBootstrapFunctionalTest" --tests "*PipelinePluginBootstrapInPlaceFunctionalTest" --tests "*PipelinePluginBootstrapGeneratedProjectFunctionalTest"
```

Expected:
- failing assertions because current fixtures and expectations still assume three modules and no `moduleResources`

- [ ] **Step 3: Update fixtures and functional expectations**

```kotlin
// functional bootstrap fixture build.gradle.kts excerpt
modules {
    domainModuleName.set("only-danmuku-domain")
    applicationModuleName.set("only-danmuku-application")
    adapterModuleName.set("only-danmuku-adapter")
    startModuleName.set("only-danmuku-start")
}
slots {
    root.from("codegen/bootstrap-slots/root")
    modulePackage("domain").from("codegen/bootstrap-slots/domain-package")
    modulePackage("start").from("codegen/bootstrap-slots/start-package")
    moduleResources("start").from("codegen/bootstrap-slots/start-resources")
}
```

```yaml
# codegen/bootstrap-slots/start-resources/application.yml.peb
spring:
  application:
    name: {{ projectName }}
```

```kotlin
// invalid-role fixture excerpt
slots {
    moduleRoot("portal").from("codegen/bootstrap-slots/portal-root")
}
```

```powershell
# generated-project compile command update
./gradlew :cap4k-plugin-pipeline-gradle:test --tests "*generated bootstrap preview project domain application adapter and start modules compile*"
```

- [ ] **Step 4: Run the functional tests and confirm they pass**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-gradle:test --tests "*PipelinePluginBootstrapFunctionalTest" --tests "*PipelinePluginBootstrapInPlaceFunctionalTest" --tests "*PipelinePluginBootstrapGeneratedProjectFunctionalTest"
```

Expected:
- `BUILD SUCCESSFUL`
- plan JSON includes `only-danmuku-start` and `module-resources:start`
- in-place bootstrap round-trips `startModuleName` and `moduleResources("start")`
- generated preview project compiles `:only-danmuku-start:compileKotlin`

- [ ] **Step 5: Commit**

```powershell
git add cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginBootstrapFunctionalTest.kt cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginBootstrapInPlaceFunctionalTest.kt cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginBootstrapGeneratedProjectFunctionalTest.kt cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginTest.kt cap4k-plugin-pipeline-gradle/src/test/resources/functional/bootstrap-sample cap4k-plugin-pipeline-gradle/src/test/resources/functional/bootstrap-preview-sample cap4k-plugin-pipeline-gradle/src/test/resources/functional/bootstrap-generated-project-smoke-sample cap4k-plugin-pipeline-gradle/src/test/resources/functional/bootstrap-generated-project-override-sample cap4k-plugin-pipeline-gradle/src/test/resources/functional/bootstrap-invalid-slot-sample cap4k-plugin-pipeline-gradle/src/test/resources/functional/bootstrap-generated-project-invalid-sample
git commit -m "test: harden bootstrap functional fixtures for start and resource slots"
```

## Task 5: Align README And Capability Matrix With Verified Behavior

**Files:**
- Modify: `cap4k-plugin-pipeline-gradle/README.md`
- Modify: `docs/superpowers/capability-matrix.md`

- [ ] **Step 1: Capture the current doc mismatches**

Run:

```powershell
rg -n "DomainBootstrapMarker|ApplicationBootstrapMarker|AdapterBootstrapMarker|MODULE_PACKAGE|modulePackage\\(\"domain\"\\)|include\\(\":only-danmuku-adapter\"\\)" cap4k-plugin-pipeline-gradle/README.md docs/superpowers/capability-matrix.md
```

Expected:
- matches showing stale three-module trees, old `modulePackage` wording, and no capability rows for slot bundle or start baseline

- [ ] **Step 2: Update README examples, tables, and generated trees**

```markdown
| Slot Kind | DSL | Output Root |
| --- | --- | --- |
| `ROOT` | `slots.root.from(...)` | project root |
| `BUILD_LOGIC` | `slots.buildLogic.from(...)` | `build-logic/` |
| `MODULE_ROOT` | `slots.moduleRoot("start").from(...)` | `{moduleName}/` |
| `MODULE_PACKAGE` | `slots.modulePackage("adapter").from(...)` | `{moduleName}/src/main/kotlin/{basePackage}/adapter/` |
| `MODULE_RESOURCES` | `slots.moduleResources("start").from(...)` | `{moduleName}/src/main/resources/` |
```

```kotlin
modules {
    domainModuleName.set("only-danmuku-domain")
    applicationModuleName.set("only-danmuku-application")
    adapterModuleName.set("only-danmuku-adapter")
    startModuleName.set("only-danmuku-start")
}
slots {
    modulePackage("start").from("bootstrap/slots/start-package")
    moduleResources("start").from("bootstrap/slots/start-resources")
}
```

- [ ] **Step 3: Add capability-matrix rows for the new bootstrap surfaces**

```markdown
| `bootstrap.slot_bundle` | `bootstrap` | `implemented` | Bootstrap exposes bounded `root`, `buildLogic`, `moduleRoot`, role-shaped `modulePackage`, and `moduleResources` slots with managed DSL round-trip. | `unit`, `functional` | `BootstrapModelsTest`; `Cap4kBootstrapConfigFactoryTest`; `DddMultiModuleBootstrapPresetProviderTest`; bootstrap functional fixtures | `yes` | No raw-copy mode and no multi-target routing. |
| `bootstrap.start_module_baseline` | `bootstrap` | `implemented` | Default `ddd-multi-module` bootstrap emits `domain`, `application`, `adapter`, and `start`, where `start` is a minimal Spring Boot + JPA host baseline. | `unit`, `functional`, `project` | `DddMultiModuleBootstrapPresetProviderTest`; `PipelinePluginBootstrapInPlaceFunctionalTest`; `PipelinePluginBootstrapGeneratedProjectFunctionalTest`; `only-danmaku-next` follow-on stage | `yes` | Rich runtime details still belong to verification-project overrides and slots. |
```

- [ ] **Step 4: Run focused regression for docs-referenced behavior**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-bootstrap:test --tests "*DddMultiModuleBootstrapPresetProviderTest" :cap4k-plugin-pipeline-gradle:test --tests "*PipelinePluginBootstrapFunctionalTest" --tests "*PipelinePluginBootstrapInPlaceFunctionalTest" --tests "*PipelinePluginBootstrapGeneratedProjectFunctionalTest"
```

Expected:
- `BUILD SUCCESSFUL`
- README and matrix now describe already-green behavior instead of desired future behavior

- [ ] **Step 5: Commit**

```powershell
git add cap4k-plugin-pipeline-gradle/README.md docs/superpowers/capability-matrix.md
git commit -m "docs: align bootstrap docs with start and slot contract"
```

## Task 6: Run Full Slice Verification And Prepare Handoff

**Files:**
- Modify if needed after failures: any file touched by Tasks 1-5

- [ ] **Step 1: Run the full targeted regression suite**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-api:test --tests "*BootstrapModelsTest" :cap4k-plugin-pipeline-bootstrap:test --tests "*DddMultiModuleBootstrapPresetProviderTest" :cap4k-plugin-pipeline-core:test --tests "*BootstrapRootStateGuardTest" --tests "*DefaultBootstrapRunnerTest" --tests "*BootstrapFilesystemArtifactExporterTest" :cap4k-plugin-pipeline-gradle:test --tests "*Cap4kBootstrapConfigFactoryTest" --tests "*PipelinePluginTest" --tests "*PipelinePluginBootstrapFunctionalTest" --tests "*PipelinePluginBootstrapInPlaceFunctionalTest" --tests "*PipelinePluginBootstrapGeneratedProjectFunctionalTest"
```

Expected:
- every targeted bootstrap API, planner, core, and functional test in this slice passes

- [ ] **Step 2: Fix any regression immediately at the smallest surface**

```text
If the failure is:
- API/model mismatch: fix `BootstrapModels.kt` or `Cap4kExtension.kt`
- routing mismatch: fix `DddMultiModuleBootstrapPresetProvider.kt`
- rerun/build.gradle mismatch: fix `build.gradle.kts.peb`
- generated-project compile mismatch: fix the `start` template or the affected fixture
```

- [ ] **Step 3: Re-run the exact failing command until it is green**

Run the smallest previously failing command, then re-run the full targeted regression suite:

```powershell
./gradlew :cap4k-plugin-pipeline-api:test --tests "*BootstrapModelsTest"
./gradlew :cap4k-plugin-pipeline-bootstrap:test --tests "*DddMultiModuleBootstrapPresetProviderTest"
./gradlew :cap4k-plugin-pipeline-core:test --tests "*BootstrapRootStateGuardTest" --tests "*DefaultBootstrapRunnerTest" --tests "*BootstrapFilesystemArtifactExporterTest"
./gradlew :cap4k-plugin-pipeline-gradle:test --tests "*Cap4kBootstrapConfigFactoryTest" --tests "*PipelinePluginTest" --tests "*PipelinePluginBootstrapFunctionalTest" --tests "*PipelinePluginBootstrapInPlaceFunctionalTest" --tests "*PipelinePluginBootstrapGeneratedProjectFunctionalTest"
```

Expected:
- the once-failing command passes
- the full suite still passes after the fix

- [ ] **Step 4: Commit the final green state**

```powershell
git add cap4k-plugin-pipeline-api cap4k-plugin-pipeline-bootstrap cap4k-plugin-pipeline-core cap4k-plugin-pipeline-gradle cap4k-plugin-pipeline-renderer-pebble docs/superpowers/capability-matrix.md
git commit -m "feat: finish bootstrap slot cleanup and start baseline"
```

- [ ] **Step 5: Record implementation notes for the next slice**

```markdown
- verification projects may now use `moduleResources("start")` plus template overrides to recreate richer old runtime details
- raw-copy slot mode remains intentionally out of scope
- multi-target slot routing remains intentionally out of scope
```

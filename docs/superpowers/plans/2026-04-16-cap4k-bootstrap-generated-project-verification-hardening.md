# Cap4k Bootstrap Generated-Project Verification Hardening Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Verify that the bounded bootstrap capability generates a usable Gradle/Kotlin project subtree by adding generated-project smoke, representative module compile checks, and override/slot usability checks.

**Architecture:** The implementation keeps bootstrap contract semantics unchanged and strengthens only the verification layer. A thin generated-project runner helper will bootstrap a fixture root, switch GradleRunner to the generated `<projectName>/` subtree, and execute real Gradle commands there. Template fixes are allowed only when generated-project verification exposes a concrete usability defect in the existing bounded preset outputs.

**Tech Stack:** Kotlin, Gradle TestKit, JUnit 5, existing bootstrap DSL/tasks, existing Pebble bootstrap renderer and preset assets

---

## Precondition

This plan assumes the roadmap has already been advanced to:

- `bootstrap generated-project verification hardening`

and that the bootstrap capability slice is already merged and present in the repository.

## File Structure

### Existing files to modify

- Modify: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/FunctionalFixtureSupport.kt`
- Modify: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default-bootstrap/bootstrap/root/settings.gradle.kts.peb`
- Modify: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default-bootstrap/bootstrap/root/build.gradle.kts.peb`
- Modify: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default-bootstrap/bootstrap/module/domain-build.gradle.kts.peb`
- Modify: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default-bootstrap/bootstrap/module/application-build.gradle.kts.peb`
- Modify: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default-bootstrap/bootstrap/module/adapter-build.gradle.kts.peb`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginBootstrapFunctionalTest.kt`

### New test files

- Create: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginBootstrapGeneratedProjectFunctionalTest.kt`

### New functional fixtures

- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/bootstrap-generated-project-smoke-sample/build.gradle.kts`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/bootstrap-generated-project-smoke-sample/settings.gradle.kts`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/bootstrap-generated-project-smoke-sample/codegen/bootstrap-slots/root/README.md.peb`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/bootstrap-generated-project-smoke-sample/codegen/bootstrap-slots/domain-package/SmokeDomainMarker.kt.peb`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/bootstrap-generated-project-override-sample/build.gradle.kts`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/bootstrap-generated-project-override-sample/settings.gradle.kts`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/bootstrap-generated-project-override-sample/codegen/bootstrap-slots/root/README.md.peb`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/bootstrap-generated-project-override-sample/codegen/bootstrap-slots/domain-package/OverrideDomainMarker.kt.peb`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/bootstrap-generated-project-override-sample/codegen/bootstrap-templates/bootstrap/root/build.gradle.kts.peb`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/bootstrap-generated-project-invalid-sample/build.gradle.kts`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/bootstrap-generated-project-invalid-sample/settings.gradle.kts`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/bootstrap-generated-project-invalid-sample/codegen/bootstrap-slots/start-root/README.md.peb`

## Task 1: Add Generated-Project Runner Harness and Smoke Tests

**Files:**
- Modify: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/FunctionalFixtureSupport.kt`
- Create: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginBootstrapGeneratedProjectFunctionalTest.kt`

- [ ] **Step 1: Write the failing generated-project smoke tests**

```kotlin
// cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginBootstrapGeneratedProjectFunctionalTest.kt
class PipelinePluginBootstrapGeneratedProjectFunctionalTest {

    @OptIn(ExperimentalPathApi::class)
    @Test
    fun `generated bootstrap project accepts help and tasks commands`() {
        val fixtureDir = Files.createTempDirectory("bootstrap-generated-project-smoke")
        FunctionalFixtureSupport.copyFixture(fixtureDir, "bootstrap-generated-project-smoke-sample")

        val (bootstrapResult, helpResult) = FunctionalFixtureSupport.bootstrapThenRunGeneratedProject(
            fixtureDir,
            projectName = "only-danmuku",
            "help",
        )
        val tasksResult = FunctionalFixtureSupport.generatedProjectRunner(
            fixtureDir,
            projectName = "only-danmuku",
            "tasks",
        ).build()

        assertTrue(bootstrapResult.output.contains("BUILD SUCCESSFUL"))
        assertTrue(helpResult.output.contains("Welcome to Gradle"))
        assertTrue(tasksResult.output.contains("Build tasks"))
    }

    @OptIn(ExperimentalPathApi::class)
    @Test
    fun `generated bootstrap project keeps slot files inside generated subtree`() {
        val fixtureDir = Files.createTempDirectory("bootstrap-generated-project-slot-smoke")
        FunctionalFixtureSupport.copyFixture(fixtureDir, "bootstrap-generated-project-smoke-sample")

        FunctionalFixtureSupport.runner(fixtureDir, "cap4kBootstrap").build()

        val generatedReadme = fixtureDir.resolve("only-danmuku/README.md")
        val generatedMarker = fixtureDir.resolve(
            "only-danmuku/only-danmuku-domain/src/main/kotlin/edu/only4/danmuku/domain/SmokeDomainMarker.kt"
        )

        assertTrue(generatedReadme.toFile().exists())
        assertTrue(generatedMarker.toFile().exists())
    }
}
```

- [ ] **Step 2: Run the new smoke tests and verify they fail**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-gradle:test --tests "*PipelinePluginBootstrapGeneratedProjectFunctionalTest"
```

Expected:

- tests fail because the generated-project helper methods do not exist
- or because the fixture names do not exist yet

- [ ] **Step 3: Add the thin generated-project runner helper**

```kotlin
// cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/FunctionalFixtureSupport.kt
fun bootstrapThenRunGeneratedProject(
    fixtureDir: Path,
    projectName: String,
    vararg arguments: String,
): Pair<BuildResult, BuildResult> {
    require(arguments.isNotEmpty()) {
        "generated project arguments must not be empty"
    }
    val bootstrapResult = runner(fixtureDir, "cap4kBootstrap").build()
    val generatedResult = generatedProjectRunner(fixtureDir, projectName, *arguments).build()
    return bootstrapResult to generatedResult
}

fun generatedProjectRunner(
    fixtureDir: Path,
    projectName: String,
    vararg arguments: String,
): GradleRunner {
    val generatedDir = generatedProjectDir(fixtureDir, projectName)
    return runner(generatedDir, *arguments)
}

fun generatedProjectDir(fixtureDir: Path, projectName: String): Path {
    val generated = fixtureDir.resolve(projectName)
    require(Files.isDirectory(generated)) {
        "Generated project directory not found: $generated"
    }
    return generated
}
```

- [ ] **Step 4: Run the smoke tests again and verify they still fail for missing fixtures**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-gradle:test --tests "*PipelinePluginBootstrapGeneratedProjectFunctionalTest"
```

Expected:

- tests still fail, now because fixture directories are not present yet

- [ ] **Step 5: Commit the harness**

```powershell
git add cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/FunctionalFixtureSupport.kt `
        cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginBootstrapGeneratedProjectFunctionalTest.kt
git commit -m "test: add bootstrap generated-project runner"
```

### Task 2: Add Smoke Fixture and Root-Project Usability Verification

**Files:**
- Modify: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default-bootstrap/bootstrap/root/settings.gradle.kts.peb`
- Modify: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default-bootstrap/bootstrap/root/build.gradle.kts.peb`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/bootstrap-generated-project-smoke-sample/build.gradle.kts`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/bootstrap-generated-project-smoke-sample/settings.gradle.kts`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/bootstrap-generated-project-smoke-sample/codegen/bootstrap-slots/root/README.md.peb`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/bootstrap-generated-project-smoke-sample/codegen/bootstrap-slots/domain-package/SmokeDomainMarker.kt.peb`

- [ ] **Step 1: Add the smoke fixture files**

```kotlin
// cap4k-plugin-pipeline-gradle/src/test/resources/functional/bootstrap-generated-project-smoke-sample/build.gradle.kts
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

```kotlin
// cap4k-plugin-pipeline-gradle/src/test/resources/functional/bootstrap-generated-project-smoke-sample/settings.gradle.kts
rootProject.name = "bootstrap-generated-project-smoke-sample"
```

```text
# cap4k-plugin-pipeline-gradle/src/test/resources/functional/bootstrap-generated-project-smoke-sample/codegen/bootstrap-slots/root/README.md.peb
# {{ projectName }}
```

```kotlin
// cap4k-plugin-pipeline-gradle/src/test/resources/functional/bootstrap-generated-project-smoke-sample/codegen/bootstrap-slots/domain-package/SmokeDomainMarker.kt.peb
package {{ basePackage }}.domain

object SmokeDomainMarker
```

- [ ] **Step 2: Run the smoke tests and verify generated-project Gradle recognition fails before template hardening**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-gradle:test --tests "*PipelinePluginBootstrapGeneratedProjectFunctionalTest.generated bootstrap project accepts help and tasks commands*"
```

Expected:

- `cap4kBootstrap` itself may pass
- generated project `help` or `tasks` fails if current root templates are not yet coherent enough

- [ ] **Step 3: Harden the root bootstrap templates for generated-project recognition**

```kotlin
// cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default-bootstrap/bootstrap/root/settings.gradle.kts.peb
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
    }
}

rootProject.name = "{{ projectName }}"

include(":{{ domainModuleName }}")
include(":{{ applicationModuleName }}")
include(":{{ adapterModuleName }}")
```

```kotlin
// cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default-bootstrap/bootstrap/root/build.gradle.kts.peb
plugins {
    kotlin("jvm") version "2.2.20" apply false
}

group = "{{ basePackage }}"
version = "0.1.0-SNAPSHOT"

subprojects {
    group = rootProject.group
    version = rootProject.version
}
```

- [ ] **Step 4: Run the full smoke test class and verify it passes**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-gradle:test --tests "*PipelinePluginBootstrapGeneratedProjectFunctionalTest"
```

Expected:

- `cap4kBootstrap` succeeds
- generated project `help` succeeds
- generated project `tasks` succeeds
- slot files exist under the generated subtree

- [ ] **Step 5: Commit the smoke verification slice**

```powershell
git add cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default-bootstrap/bootstrap/root/settings.gradle.kts.peb `
        cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default-bootstrap/bootstrap/root/build.gradle.kts.peb `
        cap4k-plugin-pipeline-gradle/src/test/resources/functional/bootstrap-generated-project-smoke-sample `
        cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginBootstrapGeneratedProjectFunctionalTest.kt `
        cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/FunctionalFixtureSupport.kt
git commit -m "test: add bootstrap generated-project smoke gate"
```

### Task 3: Add Module-Level Compile Viability Checks and Harden Module Templates

**Files:**
- Modify: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default-bootstrap/bootstrap/module/domain-build.gradle.kts.peb`
- Modify: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default-bootstrap/bootstrap/module/application-build.gradle.kts.peb`
- Modify: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default-bootstrap/bootstrap/module/adapter-build.gradle.kts.peb`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginBootstrapGeneratedProjectFunctionalTest.kt`

- [ ] **Step 1: Extend the generated-project test with compile viability checks**

```kotlin
// cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginBootstrapGeneratedProjectFunctionalTest.kt
@OptIn(ExperimentalPathApi::class)
@Test
fun `generated bootstrap project domain application and adapter modules compile`() {
    val fixtureDir = Files.createTempDirectory("bootstrap-generated-project-compile")
    FunctionalFixtureSupport.copyFixture(fixtureDir, "bootstrap-generated-project-smoke-sample")

    val (bootstrapResult, domainCompile) = FunctionalFixtureSupport.bootstrapThenRunGeneratedProject(
        fixtureDir,
        projectName = "only-danmuku",
        ":only-danmuku-domain:compileKotlin",
    )
    val applicationCompile = FunctionalFixtureSupport.generatedProjectRunner(
        fixtureDir,
        projectName = "only-danmuku",
        ":only-danmuku-application:compileKotlin",
    ).build()
    val adapterCompile = FunctionalFixtureSupport.generatedProjectRunner(
        fixtureDir,
        projectName = "only-danmuku",
        ":only-danmuku-adapter:compileKotlin",
    ).build()

    assertTrue(bootstrapResult.output.contains("BUILD SUCCESSFUL"))
    assertTrue(domainCompile.output.contains("BUILD SUCCESSFUL"))
    assertTrue(applicationCompile.output.contains("BUILD SUCCESSFUL"))
    assertTrue(adapterCompile.output.contains("BUILD SUCCESSFUL"))
}
```

- [ ] **Step 2: Run the compile-viability test and verify it fails**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-gradle:test --tests "*generated bootstrap project domain application and adapter modules compile*"
```

Expected:

- generated project `help` may already pass
- at least one generated module `compileKotlin` fails because current module build templates are too thin

- [ ] **Step 3: Harden the generated module build templates for minimal Kotlin compilation**

```kotlin
// cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default-bootstrap/bootstrap/module/domain-build.gradle.kts.peb
plugins {
    kotlin("jvm")
}

group = "{{ basePackage }}"
version = "0.1.0-SNAPSHOT"

kotlin {
    jvmToolchain(17)
}
```

```kotlin
// cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default-bootstrap/bootstrap/module/application-build.gradle.kts.peb
plugins {
    kotlin("jvm")
}

group = "{{ basePackage }}"
version = "0.1.0-SNAPSHOT"

dependencies {
    implementation(project(":{{ domainModuleName }}"))
}

kotlin {
    jvmToolchain(17)
}
```

```kotlin
// cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default-bootstrap/bootstrap/module/adapter-build.gradle.kts.peb
plugins {
    kotlin("jvm")
}

group = "{{ basePackage }}"
version = "0.1.0-SNAPSHOT"

dependencies {
    implementation(project(":{{ applicationModuleName }}"))
}

kotlin {
    jvmToolchain(17)
}
```

- [ ] **Step 4: Run the compile-viability test again and verify it passes**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-gradle:test --tests "*PipelinePluginBootstrapGeneratedProjectFunctionalTest"
```

Expected:

- smoke tests still pass
- domain/application/adapter `compileKotlin` all pass against the generated project

- [ ] **Step 5: Commit the module compile hardening**

```powershell
git add cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default-bootstrap/bootstrap/module/domain-build.gradle.kts.peb `
        cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default-bootstrap/bootstrap/module/application-build.gradle.kts.peb `
        cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default-bootstrap/bootstrap/module/adapter-build.gradle.kts.peb `
        cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginBootstrapGeneratedProjectFunctionalTest.kt
git commit -m "fix: harden bootstrap module compile viability"
```

### Task 4: Add Override-And-Slot Integrated Generated-Project Checks

**Files:**
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/bootstrap-generated-project-override-sample/build.gradle.kts`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/bootstrap-generated-project-override-sample/settings.gradle.kts`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/bootstrap-generated-project-override-sample/codegen/bootstrap-slots/root/README.md.peb`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/bootstrap-generated-project-override-sample/codegen/bootstrap-slots/domain-package/OverrideDomainMarker.kt.peb`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/bootstrap-generated-project-override-sample/codegen/bootstrap-templates/bootstrap/root/build.gradle.kts.peb`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/bootstrap-generated-project-invalid-sample/build.gradle.kts`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/bootstrap-generated-project-invalid-sample/settings.gradle.kts`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/bootstrap-generated-project-invalid-sample/codegen/bootstrap-slots/start-root/README.md.peb`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginBootstrapGeneratedProjectFunctionalTest.kt`

- [ ] **Step 1: Extend the functional test with override and invalid-sample checks**

```kotlin
// cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginBootstrapGeneratedProjectFunctionalTest.kt
@OptIn(ExperimentalPathApi::class)
@Test
fun `generated bootstrap project remains usable with fixed template override and slots`() {
    val fixtureDir = Files.createTempDirectory("bootstrap-generated-project-override")
    FunctionalFixtureSupport.copyFixture(fixtureDir, "bootstrap-generated-project-override-sample")

    val (bootstrapResult, helpResult) = FunctionalFixtureSupport.bootstrapThenRunGeneratedProject(
        fixtureDir,
        projectName = "only-danmuku",
        "help",
    )
    val domainCompile = FunctionalFixtureSupport.generatedProjectRunner(
        fixtureDir,
        projectName = "only-danmuku",
        ":only-danmuku-domain:compileKotlin",
    ).build()
    val generatedRootBuild = fixtureDir.resolve("only-danmuku/build.gradle.kts").readText()
    val generatedReadme = fixtureDir.resolve("only-danmuku/README.md").readText()

    assertTrue(bootstrapResult.output.contains("BUILD SUCCESSFUL"))
    assertTrue(helpResult.output.contains("Welcome to Gradle"))
    assertTrue(domainCompile.output.contains("BUILD SUCCESSFUL"))
    assertTrue(generatedRootBuild.contains("// override: bootstrap generated-project hardening"))
    assertTrue(generatedReadme.contains("# only-danmuku"))
}

@OptIn(ExperimentalPathApi::class)
@Test
fun `generated-project verification does not mask invalid bootstrap configuration`() {
    val fixtureDir = Files.createTempDirectory("bootstrap-generated-project-invalid")
    FunctionalFixtureSupport.copyFixture(fixtureDir, "bootstrap-generated-project-invalid-sample")

    val result = FunctionalFixtureSupport.runner(fixtureDir, "cap4kBootstrapPlan").buildAndFail()

    assertTrue(result.output.contains("unsupported bootstrap slot role"))
}
```

- [ ] **Step 2: Add the override and invalid fixture files**

```kotlin
// cap4k-plugin-pipeline-gradle/src/test/resources/functional/bootstrap-generated-project-override-sample/build.gradle.kts
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
        templates {
            preset.set("ddd-default-bootstrap")
            overrideDirs.from("codegen/bootstrap-templates")
        }
        slots {
            root.from("codegen/bootstrap-slots/root")
            modulePackage("domain").from("codegen/bootstrap-slots/domain-package")
        }
    }
}
```

```kotlin
// cap4k-plugin-pipeline-gradle/src/test/resources/functional/bootstrap-generated-project-invalid-sample/build.gradle.kts
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
            moduleRoot("start").from("codegen/bootstrap-slots/start-root")
        }
    }
}
```

```kotlin
// cap4k-plugin-pipeline-gradle/src/test/resources/functional/bootstrap-generated-project-override-sample/codegen/bootstrap-templates/bootstrap/root/build.gradle.kts.peb
plugins {
    kotlin("jvm") version "2.2.20" apply false
}

// override: bootstrap generated-project hardening
group = "{{ basePackage }}"
version = "0.1.0-SNAPSHOT"

subprojects {
    group = rootProject.group
    version = rootProject.version
}
```

- [ ] **Step 3: Run the generated-project test class and verify it passes end-to-end**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-gradle:test --tests "*PipelinePluginBootstrapGeneratedProjectFunctionalTest"
```

Expected:

- smoke checks pass
- module compile checks pass
- override sample passes `help` and representative compile
- invalid sample still fails fast

- [ ] **Step 4: Commit the integrated generated-project checks**

```powershell
git add cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginBootstrapGeneratedProjectFunctionalTest.kt `
        cap4k-plugin-pipeline-gradle/src/test/resources/functional/bootstrap-generated-project-override-sample `
        cap4k-plugin-pipeline-gradle/src/test/resources/functional/bootstrap-generated-project-invalid-sample
git commit -m "test: cover bootstrap generated-project overrides"
```

### Task 5: Run Focused and Broader Regression, Then Clean Up Exposed Gaps

**Files:**
- Modify: only files directly exposed by generated-project verification regressions
- Verify: bootstrap templates, functional harness, bootstrap functional tests, and broader pipeline suites

- [ ] **Step 1: Run the focused generated-project verification suite**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-renderer-pebble:test --tests "*PebbleBootstrapRendererTest" `
          :cap4k-plugin-pipeline-gradle:test --tests "*PipelinePluginBootstrapFunctionalTest" --tests "*PipelinePluginBootstrapGeneratedProjectFunctionalTest"
```

Expected:

- bootstrap renderer tests remain green
- existing bootstrap generate-level functional tests remain green
- new generated-project functional tests remain green

- [ ] **Step 2: Fix only regressions directly exposed by generated-project verification**

```kotlin
// Acceptable follow-on fixes in this task:
// - generated root/module build template content is still too thin for help/tasks/compileKotlin
// - generated project runner helper points at the wrong subtree
// - slot-generated package paths break compileKotlin on Windows path normalization
// - override template path does not reach the generated-project check
// - generated project recognition requires a small settings/build template correction
```

Constraint:

- fix only what the generated-project gate exposes
- do not add new presets or new bootstrap extension points
- do not widen into full build/check/startup validation

- [ ] **Step 3: Run the broader regression suite after the slice lands**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-source-design-json:test `
          :cap4k-plugin-pipeline-core:test `
          :cap4k-plugin-pipeline-generator-design:test `
          :cap4k-plugin-pipeline-renderer-pebble:test `
          :cap4k-plugin-pipeline-gradle:test
```

Expected:

- existing design-generator and bootstrap suites remain green
- generated-project verification hardening does not regress prior bootstrap closure

- [ ] **Step 4: Commit the final hardening fixes if any were required**

```powershell
git add cap4k-plugin-pipeline-renderer-pebble `
        cap4k-plugin-pipeline-gradle
git commit -m "fix: harden bootstrap generated-project verification"
```

Only create this commit if Step 2 actually required code changes.

- [ ] **Step 5: Final status check**

Run:

```powershell
git status --short --branch
```

Expected:

- working tree clean except for intentionally untracked scratch files that should be deleted before handoff

## Self-Review

### Spec coverage

- Generated-project smoke:
  - Task 1 adds generated-project runner support
  - Task 2 verifies generated root `help` and `tasks`
- Module-level compile viability:
  - Task 3 verifies representative module `compileKotlin`
- Override-and-slot integrated check:
  - Task 4 verifies override and slot preservation
- Thin harness rather than new framework:
  - Task 1 only adds focused generated-project helper methods
- Allowed follow-on fixes remain bounded:
  - Task 5 restricts fixes to generated-project verification regressions only

### Placeholder scan

Run:

```powershell
rg -n "TO-DO|TB-D|implement-later|add-appropriate|similar-to" docs/superpowers/plans/2026-04-16-cap4k-bootstrap-generated-project-verification-hardening.md
```

Expected:

- matches, if any, should be limited to this self-review subsection
- no implementation task should contain those placeholder forms

### Type consistency

The plan consistently uses:

- `bootstrapThenRunGeneratedProject`
- `generatedProjectRunner`
- `generatedProjectDir`
- `PipelinePluginBootstrapGeneratedProjectFunctionalTest`
- `bootstrap-generated-project-smoke-sample`
- `bootstrap-generated-project-override-sample`
- `bootstrap-generated-project-invalid-sample`

No alternate helper or fixture naming should be introduced during implementation.

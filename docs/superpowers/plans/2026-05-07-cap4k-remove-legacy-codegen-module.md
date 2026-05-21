# Cap4k Legacy Codegen Module Removal Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove `cap4k-plugin-codegen` from the active repository/build, rewire the one remaining direct dependent module with the minimum non-legacy logic, and update active guidance so the old monolithic generator is no longer treated as an in-repo supported path.

**Architecture:** Keep the cleanup narrow. First add a small test anchor around `cap4k-plugin-code-analysis-flow-export`, then replace its `CodegenExtension` coupling with pipeline-era project-shape discovery, then delete the legacy module from settings and disk, and finally verify root build resolution plus representative affected targets.

**Tech Stack:** Gradle Kotlin DSL, Kotlin/JVM Gradle plugins, Gradle plugin development, JUnit 5, repository docs.

---

## File Map

- Modify: `settings.gradle.kts`
  - Remove `include("cap4k-plugin-codegen")` so the legacy module is no longer part of the active build graph.
- Delete: `cap4k-plugin-codegen/**`
  - Remove the legacy module directory after its last direct dependency is rewired.
- Modify: `cap4k-plugin-code-analysis-flow-export/build.gradle.kts`
  - Remove the direct dependency on `:cap4k-plugin-codegen`.
  - Add the minimal test dependencies needed for a new focused plugin test.
- Modify: `cap4k-plugin-code-analysis-flow-export/src/main/kotlin/com/only4/cap4k/plugin/codeanalysis/flow/Cap4kFlowExportPlugin.kt`
  - Replace `CodegenExtension` usage with pipeline-era project-shape discovery and a local module-path resolver.
- Create: `cap4k-plugin-code-analysis-flow-export/src/test/kotlin/com/only4/cap4k/plugin/codeanalysis/flow/Cap4kFlowExportPluginTest.kt`
  - Lock the new module-path and label-prefix behavior with focused tests.
- Modify: `AGENTS.md`
  - Change the guidance from “legacy module still exists” to “legacy module has been removed; do not reintroduce it”.

### Task 1: Add a focused flow-export test harness for the new project-shape logic

**Files:**
- Modify: `cap4k-plugin-code-analysis-flow-export/build.gradle.kts`
- Create: `cap4k-plugin-code-analysis-flow-export/src/test/kotlin/com/only4/cap4k/plugin/codeanalysis/flow/Cap4kFlowExportPluginTest.kt`
- Test: `:cap4k-plugin-code-analysis-flow-export:test --tests "com.only4.cap4k.plugin.codeanalysis.flow.Cap4kFlowExportPluginTest"`

- [ ] **Step 1: Confirm the current direct legacy dependency and missing test coverage**

Run:

```powershell
rg -n 'implementation\(project\(":cap4k-plugin-codegen"\)\)' cap4k-plugin-code-analysis-flow-export/build.gradle.kts
Get-ChildItem cap4k-plugin-code-analysis-flow-export/src/test -Recurse -File
```

Expected:

- the `rg` command finds the direct `:cap4k-plugin-codegen` dependency
- the test directory is missing or empty enough that there is no existing focused `Cap4kFlowExportPluginTest`

- [ ] **Step 2: Add the minimal test dependencies to `cap4k-plugin-code-analysis-flow-export/build.gradle.kts`**

Change the file to this dependency block:

```kotlin
dependencies {
    implementation(gradleApi())
    implementation(gradleKotlinDsl())
    implementation(libs.jackson.module.kotlin)
    implementation(project(":cap4k-plugin-codegen"))

    testImplementation(gradleTestKit())
    testImplementation(platform(libs.junit.bom))
    testImplementation("org.junit.jupiter:junit-jupiter-api")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
```

Keep the rest of the file unchanged.

- [ ] **Step 3: Write the failing focused test file**

Create `cap4k-plugin-code-analysis-flow-export/src/test/kotlin/com/only4/cap4k/plugin/codeanalysis/flow/Cap4kFlowExportPluginTest.kt`:

```kotlin
package com.only4.cap4k.plugin.codeanalysis.flow

import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class Cap4kFlowExportPluginTest {

    @Test
    fun `resolve module projects uses explicit pipeline module paths`() {
        val root = ProjectBuilder.builder()
            .withName("sample")
            .build()
        val adapter = ProjectBuilder.builder()
            .withName("sample-adapter")
            .withParent(root)
            .build()
        val application = ProjectBuilder.builder()
            .withName("sample-application")
            .withParent(root)
            .build()
        val domain = ProjectBuilder.builder()
            .withName("sample-domain")
            .withParent(root)
            .build()

        val shape = FlowProjectShape(
            basePackage = "com.acme.demo",
            adapterModulePath = "sample-adapter",
            applicationModulePath = "sample-application",
            domainModulePath = "sample-domain",
        )

        assertEquals(
            listOf(adapter.path, application.path, domain.path),
            resolveModuleProjects(root, shape).map { it.path }
        )
    }

    @Test
    fun `resolve module projects falls back to root when no pipeline shape is present`() {
        val root = ProjectBuilder.builder()
            .withName("sample")
            .build()

        assertEquals(listOf(root.path), resolveModuleProjects(root, null).map { it.path })
    }

    @Test
    fun `resolve label prefixes includes pipeline base package and project group`() {
        val root = ProjectBuilder.builder()
            .withName("sample")
            .build()
        root.group = "com.acme"

        val shape = FlowProjectShape(
            basePackage = "com.acme.demo",
            adapterModulePath = null,
            applicationModulePath = null,
            domainModulePath = null,
        )

        assertEquals(
            listOf("com.acme.demo.", "com.acme."),
            resolveLabelPrefixes(root, shape)
        )
    }
}
```

- [ ] **Step 4: Run the focused test and verify it fails for the expected missing symbols**

Run:

```powershell
.\gradlew.bat :cap4k-plugin-code-analysis-flow-export:test --tests "com.only4.cap4k.plugin.codeanalysis.flow.Cap4kFlowExportPluginTest" --no-daemon
```

Expected:

- test compilation fails because `FlowProjectShape` and the new resolver signatures do not exist yet
- failure is about the missing new test anchor, not about unrelated repository problems

- [ ] **Step 5: Commit the test harness setup**

Run:

```bash
git add cap4k-plugin-code-analysis-flow-export/build.gradle.kts cap4k-plugin-code-analysis-flow-export/src/test/kotlin/com/only4/cap4k/plugin/codeanalysis/flow/Cap4kFlowExportPluginTest.kt
git commit -m "test: add flow export project shape coverage"
```

Expected:

- one commit exists containing only the flow-export test harness and failing test

### Task 2: Rewire flow-export off `CodegenExtension` and onto pipeline-era project shape

**Files:**
- Modify: `cap4k-plugin-code-analysis-flow-export/build.gradle.kts`
- Modify: `cap4k-plugin-code-analysis-flow-export/src/main/kotlin/com/only4/cap4k/plugin/codeanalysis/flow/Cap4kFlowExportPlugin.kt`
- Test: `:cap4k-plugin-code-analysis-flow-export:test --tests "com.only4.cap4k.plugin.codeanalysis.flow.Cap4kFlowExportPluginTest"`
- Test: `:cap4k-plugin-code-analysis-flow-export:compileKotlin`

- [ ] **Step 1: Remove the legacy module dependency from the flow-export build file**

Update `cap4k-plugin-code-analysis-flow-export/build.gradle.kts` so the dependencies block remains:

```kotlin
dependencies {
    implementation(gradleApi())
    implementation(gradleKotlinDsl())
    implementation(libs.jackson.module.kotlin)

    testImplementation(gradleTestKit())
    testImplementation(platform(libs.junit.bom))
    testImplementation("org.junit.jupiter:junit-jupiter-api")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
```

The important change is that `implementation(project(":cap4k-plugin-codegen"))` is gone and no replacement project dependency is added.

- [ ] **Step 2: Replace the `CodegenExtension` import and data path with a local project-shape model**

In `Cap4kFlowExportPlugin.kt`, remove:

```kotlin
import com.only4.cap4k.plugin.codegen.gradle.extension.CodegenExtension
```

Add this local model near the resolver helpers:

```kotlin
internal data class FlowProjectShape(
    val basePackage: String?,
    val adapterModulePath: String?,
    val applicationModulePath: String?,
    val domainModulePath: String?,
) {
    fun modulePaths(): List<String> = listOfNotNull(
        adapterModulePath?.trim()?.takeIf { it.isNotEmpty() },
        applicationModulePath?.trim()?.takeIf { it.isNotEmpty() },
        domainModulePath?.trim()?.takeIf { it.isNotEmpty() },
    )
}
```

- [ ] **Step 3: Change the plugin wiring to resolve pipeline project shape instead of `CodegenExtension`**

Replace the two legacy lookup blocks:

```kotlin
val codegenExtension = project.extensions.findByType(CodegenExtension::class.java)
extension.inputDirs.convention(resolveInputDirs(project, codegenExtension))
extension.labelPrefixes.convention(resolveLabelPrefixes(project, codegenExtension))

val moduleProjects = resolveModuleProjects(project, codegenExtension)
```

and:

```kotlin
val codegenExtension = project.extensions.findByType(CodegenExtension::class.java)
val moduleProjects = resolveModuleProjects(project, codegenExtension)
```

with:

```kotlin
val projectShape = resolvePipelineProjectShape(project)
extension.inputDirs.convention(resolveInputDirs(project, projectShape))
extension.labelPrefixes.convention(resolveLabelPrefixes(project, projectShape))

val moduleProjects = resolveModuleProjects(project, projectShape)
```

and:

```kotlin
val projectShape = resolvePipelineProjectShape(project)
val moduleProjects = resolveModuleProjects(project, projectShape)
```

- [ ] **Step 4: Replace the legacy resolver helpers with pipeline-shape-aware helpers**

Replace the old helper signatures and suffix logic:

```kotlin
private fun resolveInputDirs(project: Project, codegenExtension: CodegenExtension?): List<Directory> { ... }
private fun resolveModuleProjects(project: Project, codegenExtension: CodegenExtension?): List<Project> { ... }
private fun resolveLabelPrefixes(project: Project, codegenExtension: CodegenExtension?): List<String> { ... }
private data class ModuleSuffixes(...)
private fun resolveSuffixes(codegenExtension: CodegenExtension?): ModuleSuffixes { ... }
```

with:

```kotlin
internal fun resolveInputDirs(project: Project, projectShape: FlowProjectShape?): List<Directory> {
    val modules = resolveModuleProjects(project, projectShape)
    return modules.map { module ->
        module.layout.buildDirectory.dir("cap4k-code-analysis").get()
    }
}

internal fun resolveModuleProjects(project: Project, projectShape: FlowProjectShape?): List<Project> {
    val root = project.rootProject
    val explicitModules = projectShape
        ?.modulePaths()
        .orEmpty()
        .mapNotNull { modulePath -> resolveModuleProject(root, modulePath) }
        .distinctBy { candidate -> candidate.path }

    return if (explicitModules.isNotEmpty()) explicitModules else listOf(root)
}

internal fun resolveLabelPrefixes(project: Project, projectShape: FlowProjectShape?): List<String> {
    val prefixes = mutableListOf<String>()
    projectShape?.basePackage
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?.let { prefixes.add("$it.") }
    val group = project.group?.toString()?.trim().orEmpty()
    if (group.isNotEmpty() && group != "unspecified") {
        prefixes.add("$group.")
    }
    return prefixes.distinct()
}
```

Delete `ModuleSuffixes` and `resolveSuffixes` completely.

- [ ] **Step 5: Add the local pipeline-extension reader and module-path resolver**

Append these helpers in `Cap4kFlowExportPlugin.kt` below the resolver helpers:

```kotlin
internal fun resolvePipelineProjectShape(project: Project): FlowProjectShape? {
    val extension = project.extensions.findByName("cap4k") ?: return null
    val projectExtension = extension.invokeNoArg("getProject") ?: return null
    return FlowProjectShape(
        basePackage = projectExtension.readGradleStringProperty("getBasePackage"),
        adapterModulePath = projectExtension.readGradleStringProperty("getAdapterModulePath"),
        applicationModulePath = projectExtension.readGradleStringProperty("getApplicationModulePath"),
        domainModulePath = projectExtension.readGradleStringProperty("getDomainModulePath"),
    )
}

private fun resolveModuleProject(rootProject: Project, modulePath: String): Project? {
    val normalizedModulePath = modulePath.trim()
    if (normalizedModulePath.isEmpty()) {
        return null
    }

    val gradleProjectPath = normalizedModulePath.toGradleProjectPath()
    rootProject.findProject(gradleProjectPath)?.let { return it }

    val normalizedRelativePath = normalizedModulePath.trimStart(':')
        .replace(':', '/')
        .replace('\\', '/')
    if (normalizedRelativePath.isEmpty()) {
        return rootProject
    }

    val expectedProjectDir = rootProject.projectDir.toPath().toAbsolutePath().normalize()
        .resolve(normalizedRelativePath)
        .normalize()
    return rootProject.allprojects.firstOrNull { candidate ->
        candidate.projectDir.toPath().toAbsolutePath().normalize() == expectedProjectDir
    }
}

private fun String.toGradleProjectPath(): String {
    val normalized = trim()
    if (normalized.startsWith(":")) {
        return normalized
    }
    val modulePath = normalized.trim('/').replace('\\', '/').replace('/', ':')
    return if (modulePath.isEmpty()) ":" else ":$modulePath"
}

private fun Any.invokeNoArg(methodName: String): Any? =
    javaClass.methods
        .singleOrNull { method -> method.name == methodName && method.parameterCount == 0 }
        ?.invoke(this)

private fun Any.readGradleStringProperty(methodName: String): String? {
    val property = invokeNoArg(methodName) ?: return null
    val value = property.javaClass.methods
        .singleOrNull { method -> method.name == "getOrNull" && method.parameterCount == 0 }
        ?.invoke(property)
        ?.toString()
        ?.trim()
    return value?.takeIf { it.isNotEmpty() }
}
```

- [ ] **Step 6: Run the focused flow-export test and verify it passes**

Run:

```powershell
.\gradlew.bat :cap4k-plugin-code-analysis-flow-export:test --tests "com.only4.cap4k.plugin.codeanalysis.flow.Cap4kFlowExportPluginTest" --no-daemon
```

Expected:

- the focused test class passes
- no compile-time dependency on `CodegenExtension` remains inside the flow-export module

- [ ] **Step 7: Run a focused flow-export compile target**

Run:

```powershell
.\gradlew.bat :cap4k-plugin-code-analysis-flow-export:compileKotlin --no-daemon
```

Expected:

- `BUILD SUCCESSFUL`
- the plugin compiles without `:cap4k-plugin-codegen`

- [ ] **Step 8: Commit the flow-export rewiring**

Run:

```bash
git add cap4k-plugin-code-analysis-flow-export/build.gradle.kts cap4k-plugin-code-analysis-flow-export/src/main/kotlin/com/only4/cap4k/plugin/codeanalysis/flow/Cap4kFlowExportPlugin.kt cap4k-plugin-code-analysis-flow-export/src/test/kotlin/com/only4/cap4k/plugin/codeanalysis/flow/Cap4kFlowExportPluginTest.kt
git commit -m "refactor: remove flow export legacy codegen coupling"
```

Expected:

- one commit exists for the flow-export rewiring only

### Task 3: Remove the legacy module from the active repo and update active guidance

**Files:**
- Modify: `settings.gradle.kts`
- Delete: `cap4k-plugin-codegen/**`
- Modify: `AGENTS.md`
- Test: `rg` checks for active wiring/docs references

- [ ] **Step 1: Confirm the active build and guidance still expose the legacy module**

Run:

```powershell
rg -n 'include\("cap4k-plugin-codegen"\)' settings.gradle.kts
rg -n 'cap4k-plugin-codegen' AGENTS.md
```

Expected:

- `settings.gradle.kts` still includes the module
- `AGENTS.md` still describes it as an in-repo legacy module

- [ ] **Step 2: Remove the module include from `settings.gradle.kts`**

Change this block:

```kotlin
include("cap4k-plugin-code-analysis-flow-export")
include("cap4k-plugin-codegen-ksp")
include("cap4k-plugin-codegen")
include(
    "cap4k-plugin-pipeline-api",
```

to:

```kotlin
include("cap4k-plugin-code-analysis-flow-export")
include("cap4k-plugin-codegen-ksp")
include(
    "cap4k-plugin-pipeline-api",
```

- [ ] **Step 3: Update `AGENTS.md` to reflect removal instead of in-repo legacy maintenance**

Replace:

```markdown
- `cap4k-plugin-codegen` is the legacy plugin and is no longer maintained. Do not add compatibility work, new features, or cleanup churn there unless the user explicitly asks to delete or quarantine it; prefer documenting any residual legacy references as out of scope.
```

with:

```markdown
- the old monolithic generator module `cap4k-plugin-codegen` has been removed from the active repository. Do not reintroduce it or add new compatibility work around that path; mainline generator work belongs to the pipeline plugin family.
```

- [ ] **Step 4: Delete the legacy module directory from the branch**

Run:

```bash
git rm -r cap4k-plugin-codegen
```

Expected:

- every file under `cap4k-plugin-codegen/**` is staged for deletion
- `cap4k-plugin-codegen-ksp` remains untouched

- [ ] **Step 5: Verify active wiring and active-doc references are gone**

Run:

```powershell
rg -n 'include\("cap4k-plugin-codegen"\)|project\(":cap4k-plugin-codegen"\)|import com\.only4\.cap4k\.plugin\.codegen' settings.gradle.kts cap4k-plugin-code-analysis-flow-export
rg -n 'cap4k-plugin-codegen' AGENTS.md
```

Expected:

- the first command finds no remaining active build wiring or code imports tied to `cap4k-plugin-codegen`
- the second command finds exactly one `AGENTS.md` mention, and that mention describes removal plus “do not reintroduce it” rather than presenting it as a supported in-repo path

- [ ] **Step 6: Commit the module removal and guidance update**

Run:

```bash
git add settings.gradle.kts AGENTS.md
git commit -m "refactor: remove legacy codegen module"
```

Expected:

- one commit exists containing the settings cleanup, AGENTS update, and staged deletion of `cap4k-plugin-codegen/**`

### Task 4: Verify root resolution and representative affected targets, then capture the cleanup evidence

**Files:**
- Modify: `docs/superpowers/plans/2026-05-07-cap4k-remove-legacy-codegen-module.md` only if verification notes in the plan itself must be corrected before handoff
- Test: root Gradle resolution plus representative compile/test targets

- [ ] **Step 1: Verify the root build still resolves after removal**

Run:

```powershell
.\gradlew.bat help --no-daemon
```

Expected:

- `BUILD SUCCESSFUL`
- Gradle no longer attempts to configure `:cap4k-plugin-codegen`

- [ ] **Step 2: Verify the affected analysis plugin still compiles**

Run:

```powershell
.\gradlew.bat :cap4k-plugin-code-analysis-flow-export:compileKotlin --no-daemon
```

Expected:

- `BUILD SUCCESSFUL`
- the compile path no longer needs `:cap4k-plugin-codegen`

- [ ] **Step 3: Verify the affected analysis plugin test target still passes**

Run:

```powershell
.\gradlew.bat :cap4k-plugin-code-analysis-flow-export:test --no-daemon
```

Expected:

- `BUILD SUCCESSFUL`
- the new focused test class runs successfully instead of `NO-SOURCE`

- [ ] **Step 4: Verify one representative pipeline-side contract target**

Run:

```powershell
.\gradlew.bat :cap4k-plugin-pipeline-gradle:test --tests "com.only4.cap4k.plugin.pipeline.gradle.Cap4kProjectConfigFactoryTest" --no-daemon
```

Expected:

- `BUILD SUCCESSFUL`
- the pipeline-side project-shape contract used by `flow-export` remains stable

- [ ] **Step 5: Capture final evidence for dependency and doc answers**

Run:

```powershell
rg -n 'include\("cap4k-plugin-codegen"\)|project\(":cap4k-plugin-codegen"\)|import com\.only4\.cap4k\.plugin\.codegen' settings.gradle.kts cap4k-plugin-code-analysis-flow-export
rg -n 'cap4k-plugin-codegen' AGENTS.md README.md README.md docs/public
git status --short
```

Expected:

- the first command finds no active build wiring or code imports to the removed module
- the second command may still find the explicit “removed legacy module” note in `AGENTS.md`, but it must not find any active doc that still presents `cap4k-plugin-codegen` as a supported in-repo path
- `git status --short` is clean after the verification step if no further edits were needed

- [ ] **Step 6: Prepare the final implementation summary for handoff**

The final implementation report must answer explicitly:

```text
Direct dependencies that existed before removal:
- cap4k-plugin-code-analysis-flow-export

Minimum build-graph repair after removal:
- remove include("cap4k-plugin-codegen") from settings.gradle.kts
- remove implementation(project(":cap4k-plugin-codegen")) from cap4k-plugin-code-analysis-flow-export/build.gradle.kts
- replace CodegenExtension reads in Cap4kFlowExportPlugin.kt with pipeline-era project-shape discovery plus local module-path resolution

Active docs updated:
- AGENTS.md

Follow-up issues to split instead of folding into this cleanup:
- cap4k-plugin-codegen-ksp removal, if still desired
- a first-class shared pipeline/analysis config contract, if the reflection-based bridge becomes a maintenance problem
- archival spec/plan text cleanup for historical cap4k-plugin-codegen references
```

- [ ] **Step 7: Commit any final verification-only adjustments if needed**

Run:

```bash
git add settings.gradle.kts AGENTS.md cap4k-plugin-code-analysis-flow-export/build.gradle.kts cap4k-plugin-code-analysis-flow-export/src/main/kotlin/com/only4/cap4k/plugin/codeanalysis/flow/Cap4kFlowExportPlugin.kt cap4k-plugin-code-analysis-flow-export/src/test/kotlin/com/only4/cap4k/plugin/codeanalysis/flow/Cap4kFlowExportPluginTest.kt
git commit -m "test: verify legacy codegen removal cleanup"
```

Expected:

- if no final adjustments were needed, skip this commit
- if verification forced a last small fix, the commit contains only that fix

# cap4k Gradle Plugin Central ID Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Publish `cap4k 0.6.0` with Central-valid Gradle plugin ids and switch `cap4k-reference-content-studio` `master` to consume only public Maven Central artifacts.

**Architecture:** First update `cap4k` so all externally published Gradle plugins use the new `io.github.ldmoxeii.*` ids, the release line publishes their marker artifacts, and the baseline development version becomes `0.6.0-dev`. Then release `v0.6.0` from `publish/maven-central`, and only after that migrate `cap4k-reference-content-studio` to the new plugin id, `io.github.ldmoxeii` library coordinates, Central-only repositories, and a CI path that no longer prebuilds `cap4k`.

**Tech Stack:** Gradle Kotlin DSL, `java-gradle-plugin`, Maven Publish, GitHub Actions, Maven Central, Kotlin/JUnit/TestKit, Spring Boot

---

### Task 1: Update cap4k release version and plugin-marker publication policy

**Files:**
- Modify: `buildSrc/src/main/kotlin/buildsrc/convention/CentralReleaseVersion.kt`
- Modify: `buildSrc/src/test/kotlin/buildsrc/convention/CentralReleaseVersionTest.kt`
- Modify: `buildSrc/src/main/kotlin/buildsrc/convention/CentralPublishTaskPolicy.kt`
- Modify: `buildSrc/src/test/kotlin/buildsrc/convention/CentralPublishTaskPolicyTest.kt`
- Modify: `buildSrc/src/main/kotlin/kotlin-jvm.gradle.kts`

- [ ] **Step 1: Update the release-version tests to describe the new baseline and allowed marker tasks**

```kotlin
// buildSrc/src/test/kotlin/buildsrc/convention/CentralReleaseVersionTest.kt
@Test
fun `uses 0_6_0 dev as baseline version when no release version is provided`() {
    assertEquals("0.6.0-dev", CentralReleaseVersion.resolve(null))
    assertEquals("0.6.0-dev", CentralReleaseVersion.resolve("   "))
}
```

```kotlin
// buildSrc/src/test/kotlin/buildsrc/convention/CentralPublishTaskPolicyTest.kt
@Test
fun `allows pipeline and flow export plugin markers for Central`() {
    assertTrue(
        CentralPublishTaskPolicy.isAllowedPluginMarkerCentralPortalPublishTask(
            "publishCap4kPipelinePluginMarkerMavenPublicationToCentralPortalRepository"
        )
    )
    assertTrue(
        CentralPublishTaskPolicy.isAllowedPluginMarkerCentralPortalPublishTask(
            "publishCap4kFlowExportPluginMarkerMavenPublicationToCentralPortalRepository"
        )
    )
    assertFalse(
        CentralPublishTaskPolicy.isAllowedPluginMarkerCentralPortalPublishTask(
            "publishLegacyPluginMarkerMavenPublicationToCentralPortalRepository"
        )
    )
}
```

- [ ] **Step 2: Run the focused buildSrc tests and verify they fail before implementation**

Run:

```powershell
.\gradlew.bat -p buildSrc test --tests "buildsrc.convention.CentralReleaseVersionTest" --tests "buildsrc.convention.CentralPublishTaskPolicyTest"
```

Expected:
- `CentralReleaseVersionTest` fails because the code still returns `0.5.0-dev`
- `CentralPublishTaskPolicyTest` fails to compile or fails assertions because the new allowlist behavior does not exist yet

- [ ] **Step 3: Implement the new baseline version and plugin-marker allowlist**

```kotlin
// buildSrc/src/main/kotlin/buildsrc/convention/CentralReleaseVersion.kt
internal object CentralReleaseVersion {
    const val groupId = "io.github.ldmoxeii"
    const val baselineVersion = "0.6.0-dev"
    const val releaseVersionProperty = "release.version"
    const val releaseVersionEnvironment = "RELEASE_VERSION"
    // existing validation logic stays unchanged
}
```

```kotlin
// buildSrc/src/main/kotlin/buildsrc/convention/CentralPublishTaskPolicy.kt
internal object CentralPublishTaskPolicy {
    private val allowedPluginMarkerCentralPortalTasks = setOf(
        "publishCap4kPipelinePluginMarkerMavenPublicationToCentralPortalRepository",
        "publishCap4kFlowExportPluginMarkerMavenPublicationToCentralPortalRepository",
    )

    fun isCentralPortalPublishTask(taskName: String): Boolean =
        taskName.endsWith("ToCentralPortalRepository")

    fun isPluginMarkerCentralPortalPublishTask(taskName: String): Boolean =
        taskName.endsWith("PluginMarkerMavenPublicationToCentralPortalRepository")

    fun isAllowedPluginMarkerCentralPortalPublishTask(taskName: String): Boolean =
        taskName in allowedPluginMarkerCentralPortalTasks
}
```

- [ ] **Step 4: Rewire the convention plugin so allowed marker tasks publish and sign in release mode**

```kotlin
// buildSrc/src/main/kotlin/kotlin-jvm.gradle.kts
signing {
    setRequired {
        isCentralRelease && gradle.taskGraph.allTasks.any { task ->
            task is PublishToMavenRepository &&
                CentralPublishTaskPolicy.isCentralPortalPublishTask(task.name) &&
                (
                    !CentralPublishTaskPolicy.isPluginMarkerCentralPortalPublishTask(task.name) ||
                        CentralPublishTaskPolicy.isAllowedPluginMarkerCentralPortalPublishTask(task.name)
                )
        }
    }
    if (!signingKey.isNullOrBlank()) {
        useInMemoryPgpKeys(signingKey, signingPassword)
    }
    sign(publishing.publications)
}

tasks.withType<PublishToMavenRepository>().configureEach {
    if (CentralPublishTaskPolicy.isPluginMarkerCentralPortalPublishTask(name)) {
        enabled =
            isCentralRelease &&
                CentralPublishTaskPolicy.isAllowedPluginMarkerCentralPortalPublishTask(name)
        return@configureEach
    }

    if (CentralPublishTaskPolicy.isCentralPortalPublishTask(name)) {
        enabled = isCentralRelease
    }
}
```

- [ ] **Step 5: Re-run buildSrc tests and a release-shaped dry run**

Run:

```powershell
.\gradlew.bat -p buildSrc test --tests "buildsrc.convention.CentralReleaseVersionTest" --tests "buildsrc.convention.CentralPublishTaskPolicyTest"
.\gradlew.bat publish "-Prelease.version=0.6.0" -m
```

Expected:
- buildSrc tests pass
- dry-run output includes the two new marker publish tasks and no blanket marker suppression for them

- [ ] **Step 6: Commit**

```powershell
git add buildSrc/src/main/kotlin/buildsrc/convention/CentralReleaseVersion.kt buildSrc/src/test/kotlin/buildsrc/convention/CentralReleaseVersionTest.kt buildSrc/src/main/kotlin/buildsrc/convention/CentralPublishTaskPolicy.kt buildSrc/src/test/kotlin/buildsrc/convention/CentralPublishTaskPolicyTest.kt buildSrc/src/main/kotlin/kotlin-jvm.gradle.kts
git commit -m "build: allow central marker publish for renamed plugins"
```

### Task 2: Rename cap4k external Gradle plugins and migrate local fixtures to 0.6.0-dev

**Files:**
- Modify: `cap4k-plugin-pipeline-gradle/build.gradle.kts`
- Modify: `cap4k-plugin-code-analysis-flow-export/build.gradle.kts`
- Modify: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default-bootstrap/bootstrap/root/build.gradle.kts.peb`
- Modify: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default-bootstrap/bootstrap/module/application-build.gradle.kts.peb`
- Modify: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default-bootstrap/bootstrap/module/domain-build.gradle.kts.peb`
- Modify: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default-bootstrap/bootstrap/module/adapter-build.gradle.kts.peb`
- Modify: `cap4k-plugin-pipeline-renderer-pebble/src/test/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PebbleBootstrapRendererTest.kt`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/*/build.gradle.kts` for every file returned by:

```powershell
rg -l 'id\("com\.only4\.cap4k\.plugin\.pipeline"\)' cap4k-plugin-pipeline-gradle/src/test/resources/functional
```

- Modify: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/**/demo-*/build.gradle.kts` files that still pin `io.github.ldmoxeii:*:0.5.0-dev`

- [ ] **Step 1: Change the declared public plugin ids in the two Gradle plugin modules**

```kotlin
// cap4k-plugin-pipeline-gradle/build.gradle.kts
gradlePlugin {
    plugins {
        create("cap4kPipeline") {
            id = "io.github.ldmoxeii.cap4k.pipeline"
            implementationClass = "com.only4.cap4k.plugin.pipeline.gradle.PipelinePlugin"
            displayName = "Cap4k Pipeline Plugin"
            description = "Runs the minimal Cap4k pipeline vertical slice."
        }
    }
}
```

```kotlin
// cap4k-plugin-code-analysis-flow-export/build.gradle.kts
gradlePlugin {
    plugins {
        create("cap4kFlowExport") {
            id = "io.github.ldmoxeii.cap4k.codeanalysis.flow-export"
            implementationClass = "com.only4.cap4k.plugin.codeanalysis.flow.Cap4kFlowExportPlugin"
            displayName = "Cap4k Code Analysis Flow Export Plugin"
            description = "Exports Cap4k processing flows from code analysis metadata."
        }
    }
}
```

- [ ] **Step 2: Make the bootstrap preset and renderer test expect the new plugin id and 0.6.0-dev dependencies**

```kotlin
// cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default-bootstrap/bootstrap/root/build.gradle.kts.peb
plugins {
    id("io.github.ldmoxeii.cap4k.pipeline")
}
```

```kotlin
// cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default-bootstrap/bootstrap/module/domain-build.gradle.kts.peb
dependencies {
    implementation("io.github.ldmoxeii:ddd-core:0.6.0-dev")
    implementation("io.github.ldmoxeii:ddd-domain-repo-jpa:0.6.0-dev")
}
```

```kotlin
// cap4k-plugin-pipeline-renderer-pebble/src/test/kotlin/.../PebbleBootstrapRendererTest.kt
assertTrue(artifact.content.contains("id(\"io.github.ldmoxeii.cap4k.pipeline\")"))
assertTrue(artifact.content.contains("implementation(\"io.github.ldmoxeii:ddd-core:0.6.0-dev\")"))
```

- [ ] **Step 3: Update all functional fixture root builds to apply the new plugin id**

Use this exact replacement in every root sample `build.gradle.kts` returned by the `rg -l` command above:

```kotlin
plugins {
    id("io.github.ldmoxeii.cap4k.pipeline")
}
```

Keep existing imports like `import com.only4.cap4k.plugin.pipeline.api.BootstrapMode` unchanged. The package names are not part of this migration.

- [ ] **Step 4: Update every fixture dependency pinned to 0.5.0-dev to 0.6.0-dev**

Apply this replacement in the preset module templates and the affected fixture module build files:

```kotlin
implementation("io.github.ldmoxeii:ddd-core:0.6.0-dev")
implementation("io.github.ldmoxeii:ddd-domain-repo-jpa:0.6.0-dev")
```

Representative fixture files that must change include:

```text
cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-compile-sample/demo-domain/build.gradle.kts
cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-persistence-compile-sample/demo-domain/build.gradle.kts
cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-provider-persistence-compile-sample/demo-domain/build.gradle.kts
cap4k-plugin-pipeline-gradle/src/test/resources/functional/known-bug-parity-sample/demo-domain/build.gradle.kts
cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-integrated-compile-sample/demo-application/build.gradle.kts
```

Then use search to catch any stragglers:

```powershell
rg -n "0\.5\.0-dev" cap4k-plugin-pipeline-gradle cap4k-plugin-pipeline-renderer-pebble buildSrc/src
```

Expected after edits:
- only historic docs under `docs/superpowers/` may still mention `0.5.0-dev`
- no runtime fixture or preset file should

- [ ] **Step 5: Run the plugin-focused test suites**

Run:

```powershell
.\gradlew.bat :cap4k-plugin-pipeline-renderer-pebble:test --tests "com.only4.cap4k.plugin.pipeline.renderer.pebble.PebbleBootstrapRendererTest"
.\gradlew.bat :cap4k-plugin-code-analysis-flow-export:test --tests "com.only4.cap4k.plugin.codeanalysis.flow.Cap4kFlowExportPluginTest"
.\gradlew.bat :cap4k-plugin-pipeline-gradle:test
```

Expected:
- renderer test passes with the new plugin id and `0.6.0-dev`
- flow-export unit tests pass with the new declared plugin id
- pipeline functional tests pass while applying `io.github.ldmoxeii.cap4k.pipeline`

- [ ] **Step 6: Commit**

```powershell
git add cap4k-plugin-pipeline-gradle/build.gradle.kts cap4k-plugin-code-analysis-flow-export/build.gradle.kts cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default-bootstrap/bootstrap/root/build.gradle.kts.peb cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default-bootstrap/bootstrap/module/application-build.gradle.kts.peb cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default-bootstrap/bootstrap/module/domain-build.gradle.kts.peb cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default-bootstrap/bootstrap/module/adapter-build.gradle.kts.peb cap4k-plugin-pipeline-renderer-pebble/src/test/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PebbleBootstrapRendererTest.kt cap4k-plugin-pipeline-gradle/src/test/resources/functional
git commit -m "build: rename published gradle plugin ids"
```

### Task 3: Verify cap4k 0.6.0 marker publications locally and prepare the Central release

**Files:**
- Modify: `docs/superpowers/analysis/2026-05-14-cap4k-maven-central-release-verification.md`

- [ ] **Step 1: Extend the release verification runbook with the new marker checks**

Add a section like:

```markdown
## 0.6.0 plugin marker verification

Before tagging `v0.6.0`, generate marker publications locally and verify:

- `cap4k-plugin-pipeline-gradle/build/publications/cap4kPipelinePluginMarkerMaven/pom-default.xml`
- `cap4k-plugin-code-analysis-flow-export/build/publications/cap4kFlowExportPluginMarkerMaven/pom-default.xml`

After Central release, confirm:

- `https://repo1.maven.org/maven2/io/github/ldmoxeii/cap4k/pipeline/io.github.ldmoxeii.cap4k.pipeline.gradle.plugin/0.6.0/io.github.ldmoxeii.cap4k.pipeline.gradle.plugin-0.6.0.pom`
- `https://repo1.maven.org/maven2/io/github/ldmoxeii/cap4k/codeanalysis/flow-export/io.github.ldmoxeii.cap4k.codeanalysis.flow-export.gradle.plugin/0.6.0/io.github.ldmoxeii.cap4k.codeanalysis.flow-export.gradle.plugin-0.6.0.pom`
```

- [ ] **Step 2: Generate marker publications and inspect the local POMs**

Run:

```powershell
.\gradlew.bat :cap4k-plugin-pipeline-gradle:generatePomFileForCap4kPipelinePluginMarkerMavenPublication :cap4k-plugin-code-analysis-flow-export:generatePomFileForCap4kFlowExportPluginMarkerMavenPublication "-Prelease.version=0.6.0"
```

Expected files:

```text
cap4k-plugin-pipeline-gradle/build/publications/cap4kPipelinePluginMarkerMaven/pom-default.xml
cap4k-plugin-code-analysis-flow-export/build/publications/cap4kFlowExportPluginMarkerMaven/pom-default.xml
```

Inspect the POMs and verify they declare the new ids:

```xml
<groupId>io.github.ldmoxeii.cap4k.pipeline</groupId>
<artifactId>io.github.ldmoxeii.cap4k.pipeline.gradle.plugin</artifactId>
<version>0.6.0</version>
```

```xml
<groupId>io.github.ldmoxeii.cap4k.codeanalysis.flow-export</groupId>
<artifactId>io.github.ldmoxeii.cap4k.codeanalysis.flow-export.gradle.plugin</artifactId>
<version>0.6.0</version>
```

- [ ] **Step 3: Run a full release-shaped local publish**

Run from the `cap4k` release worktree:

```powershell
.\gradlew.bat check publishToMavenLocal "-Prelease.version=0.6.0"
```

Expected:
- all tests pass
- local Maven contains normal module artifacts and both marker artifacts

Verify representative local paths:

```text
%USERPROFILE%\.m2\repository\io\github\ldmoxeii\cap4k-plugin-pipeline-gradle\0.6.0\
%USERPROFILE%\.m2\repository\io\github\ldmoxeii\cap4k\pipeline\io.github.ldmoxeii.cap4k.pipeline.gradle.plugin\0.6.0\
%USERPROFILE%\.m2\repository\io\github\ldmoxeii\cap4k\codeanalysis\flow-export\io.github.ldmoxeii.cap4k.codeanalysis.flow-export.gradle.plugin\0.6.0\
```

- [ ] **Step 4: Commit the runbook update**

```powershell
git add docs/superpowers/analysis/2026-05-14-cap4k-maven-central-release-verification.md
git commit -m "docs: add 0.6.0 plugin marker verification"
```

- [ ] **Step 5: Merge to `publish/maven-central`, tag `v0.6.0`, and verify the live release**

Run:

```powershell
$verifyBranch = git rev-parse --abbrev-ref HEAD
git -C C:\Users\LD_moxeii\Documents\code\only-workspace\cap4k\.worktrees\publish-maven-central fetch origin --tags --prune
git -C C:\Users\LD_moxeii\Documents\code\only-workspace\cap4k\.worktrees\publish-maven-central merge --ff-only "origin/$verifyBranch"
git -C C:\Users\LD_moxeii\Documents\code\only-workspace\cap4k\.worktrees\publish-maven-central push origin publish/maven-central
git -C C:\Users\LD_moxeii\Documents\code\only-workspace\cap4k\.worktrees\publish-maven-central tag v0.6.0
git -C C:\Users\LD_moxeii\Documents\code\only-workspace\cap4k\.worktrees\publish-maven-central push origin v0.6.0
gh run watch --repo LDmoxeii/cap4k "$(gh run list --repo LDmoxeii/cap4k --workflow 'Maven Central Release' --limit 1 --json databaseId --jq '.[0].databaseId')" --exit-status
```

Expected:
- GitHub release workflow succeeds
- GitHub Release `v0.6.0` exists
- both marker URLs from Step 1 return `HTTP 200`

### Task 4: Switch cap4k-reference-content-studio to Central-only 0.6.0 consumption

**Files:**
- Modify: `build.gradle.kts`
- Modify: `settings.gradle.kts`
- Modify: `buildSrc/settings.gradle.kts`
- Modify: `gradle/libs.versions.toml`
- Modify: `cap4k-reference-content-studio-domain/build.gradle.kts`
- Modify: `cap4k-reference-content-studio-application/build.gradle.kts`
- Modify: `cap4k-reference-content-studio-adapter/build.gradle.kts`

- [ ] **Step 1: Replace the old pipeline plugin application with the new public id**

In `gradle/libs.versions.toml`, add a plugin alias and bump the shared cap4k version:

```toml
[versions]
cap4k-ddd = "0.6.0"

[plugins]
cap4k-pipeline = { id = "io.github.ldmoxeii.cap4k.pipeline", version.ref = "cap4k-ddd" }
```

Then apply it in the root build:

```kotlin
// build.gradle.kts
plugins {
    alias(libs.plugins.cap4k.pipeline)
    base
    alias(libs.plugins.spring.boot) apply false
    alias(libs.plugins.spring.dependency.management) apply false
}
```

- [ ] **Step 2: Remove local and mirror repository assumptions**

```kotlin
// build.gradle.kts
allprojects {
    repositories {
        mavenCentral()
    }
}
```

Delete this old block entirely:

```kotlin
configurations.configureEach {
    resolutionStrategy.cacheChangingModulesFor(0, TimeUnit.SECONDS)
    resolutionStrategy.cacheDynamicVersionsFor(0, TimeUnit.SECONDS)
}
```

Update root and buildSrc settings:

```kotlin
// settings.gradle.kts
pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}
```

```kotlin
// buildSrc/settings.gradle.kts
pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}
```

- [ ] **Step 3: Move all direct framework dependencies to `io.github.ldmoxeii:*:0.6.0`**

```kotlin
// cap4k-reference-content-studio-domain/build.gradle.kts
dependencies {
    implementation("io.github.ldmoxeii:ddd-core:0.6.0")
    implementation("io.github.ldmoxeii:ddd-domain-repo-jpa:0.6.0")
}
```

```kotlin
// cap4k-reference-content-studio-application/build.gradle.kts
dependencies {
    implementation("io.github.ldmoxeii:ddd-core:0.6.0")
    implementation("io.github.ldmoxeii:ddd-domain-repo-jpa:0.6.0")
}
```

```kotlin
// cap4k-reference-content-studio-adapter/build.gradle.kts
dependencies {
    implementation("io.github.ldmoxeii:ddd-core:0.6.0")
    implementation("io.github.ldmoxeii:ddd-domain-repo-jpa:0.6.0")
}
```

Also update the version-catalog-backed libraries:

```toml
cap4k-ddd-starter = { module = "io.github.ldmoxeii:cap4k-ddd-starter", version.ref = "cap4k-ddd" }
ddd-integration-event-http = { module = "io.github.ldmoxeii:ddd-integration-event-http", version.ref = "cap4k-ddd" }
ddd-integration-event-http-jpa = { module = "io.github.ldmoxeii:ddd-integration-event-http-jpa", version.ref = "cap4k-ddd" }
cap4k-plugin-code-analysis-compiler = { module = "io.github.ldmoxeii:cap4k-plugin-code-analysis-compiler", version.ref = "cap4k-ddd" }
cap4k-plugin-code-analysis-core = { module = "io.github.ldmoxeii:cap4k-plugin-code-analysis-core", version.ref = "cap4k-ddd" }
```

- [ ] **Step 4: Run a static grep to prove the old local-only path is gone**

Run:

```powershell
rg -n "mavenLocal|maven\.aliyun\.com|0\.5\.0-SNAPSHOT|com\.only4:" .
```

Expected:
- no matches in source-controlled build files or READMEs

- [ ] **Step 5: Run a clean dependency-refresh build**

Run:

```powershell
.\gradlew.bat --refresh-dependencies clean check
```

Expected:
- the build passes without any prior `cap4k` local publish
- Gradle resolves the new plugin marker and all `io.github.ldmoxeii` dependencies from public repositories

- [ ] **Step 6: Commit**

```powershell
git add build.gradle.kts settings.gradle.kts buildSrc/settings.gradle.kts gradle/libs.versions.toml cap4k-reference-content-studio-domain/build.gradle.kts cap4k-reference-content-studio-application/build.gradle.kts cap4k-reference-content-studio-adapter/build.gradle.kts
git commit -m "build: consume cap4k 0.6.0 from maven central"
```

### Task 5: Remove local-build instructions from cap4k-reference-content-studio docs and CI

**Files:**
- Modify: `.github/workflows/ci.yml`
- Modify: `README.md`
- Modify: `README.zh-CN.md`

- [ ] **Step 1: Simplify CI so it only builds the reference project itself**

Replace the current multi-checkout workflow body with:

```yaml
jobs:
  check:
    runs-on: ubuntu-latest
    steps:
      - name: Checkout reference project
        uses: actions/checkout@v4

      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: "17"

      - name: Set up Gradle
        uses: gradle/actions/setup-gradle@v4

      - name: Make Gradle wrapper executable
        run: chmod +x gradlew

      - name: Check reference project
        run: ./gradlew --refresh-dependencies check
```

Delete these old steps entirely:

```yaml
- name: Checkout cap4k
- name: Publish cap4k snapshots to Maven local
```

- [ ] **Step 2: Rewrite the prerequisites section in both READMEs**

Use this English replacement:

```markdown
## Prerequisites

1. Use JDK 17.
2. No local `cap4k` prepublish is required. This repository resolves the released `cap4k 0.6.0` plugin and libraries from public repositories.
```

Use this Chinese replacement:

```markdown
## 前置条件

1. 使用 JDK 17。
2. 不需要先在本地预发布 `cap4k`。这个仓库会直接从公共仓库解析已发布的 `cap4k 0.6.0` 插件和库。
```

Remove the old snapshot and `mavenLocal()` guidance block completely.

- [ ] **Step 3: Re-run the clean build and CI grep after the docs and workflow edits**

Run:

```powershell
.\gradlew.bat --refresh-dependencies clean check
rg -n "mavenLocal|publishToMavenLocal|0\.5\.0-SNAPSHOT|com\.only4:" README.md README.zh-CN.md .github/workflows/ci.yml
```

Expected:
- build still passes
- grep returns no matches

- [ ] **Step 4: Commit**

```powershell
git add .github/workflows/ci.yml README.md README.zh-CN.md
git commit -m "ci: remove local cap4k prepublish requirement"
```

## Self-Review

- Spec coverage:
  - new `io.github.ldmoxeii.*` plugin ids: Tasks 1-3
  - `0.6.0` release boundary: Tasks 1 and 3
  - marker publication from Central: Tasks 1 and 3
  - `cap4k-reference-content-studio` Central-only consumption: Tasks 4 and 5
  - no local-prepublish requirement: Tasks 4 and 5
- Placeholder scan:
  - no `TODO`, `TBD`, or deferred implementation markers remain
  - every change task names concrete files, commands, or replacement snippets
- Type consistency:
  - pipeline plugin id is consistently `io.github.ldmoxeii.cap4k.pipeline`
  - flow-export plugin id is consistently `io.github.ldmoxeii.cap4k.codeanalysis.flow-export`
  - release version is consistently `0.6.0` / baseline `0.6.0-dev`

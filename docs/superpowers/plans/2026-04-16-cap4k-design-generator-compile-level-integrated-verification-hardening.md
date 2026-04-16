# Cap4k Design Generator Compile-Level / Integrated Verification Hardening Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Raise the migrated design-generator quality gate from plan-and-generate correctness to representative generate-plus-compile correctness across family-level fixtures and one small integrated sample.

**Architecture:** First advance the roadmap so the old design-family migration mainline is explicitly closed and this hardening slice becomes the current target. Then add a shared compile-capable functional harness around Gradle TestKit using compile-specific fixture variants plus one reusable composite-build dependency strategy against the local `cap4k` repo. After the harness exists, upgrade representative family fixtures and add one integrated compile sample so generated code is compiled in the modules that actually receive it.

**Tech Stack:** Kotlin, Gradle, JUnit 5, Gradle TestKit, composite/included builds, Pebble templates

---

## File Structure

- Modify: `docs/superpowers/mainline-roadmap.md`
- Create: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/FunctionalFixtureSupport.kt`
- Create: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginCompileFunctionalTest.kt`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginFunctionalTest.kt`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-compile-sample/build.gradle.kts`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-compile-sample/settings.gradle.kts`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-compile-sample/design/design.json`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-compile-sample/domain/build.gradle.kts`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-compile-sample/demo-application/build.gradle.kts`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-compile-sample/demo-adapter/build.gradle.kts`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-validator-compile-sample/build.gradle.kts`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-validator-compile-sample/settings.gradle.kts`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-validator-compile-sample/design/design.json`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-validator-compile-sample/demo-application/build.gradle.kts`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-api-payload-compile-sample/build.gradle.kts`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-api-payload-compile-sample/settings.gradle.kts`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-api-payload-compile-sample/design/design.json`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-api-payload-compile-sample/demo-adapter/build.gradle.kts`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-domain-event-compile-sample/build.gradle.kts`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-domain-event-compile-sample/settings.gradle.kts`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-domain-event-compile-sample/design/design.json`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-domain-event-compile-sample/demo-domain/build.gradle.kts`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-domain-event-compile-sample/demo-domain/src/main/kotlin/com/acme/demo/domain/order/Order.kt`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-domain-event-compile-sample/demo-application/build.gradle.kts`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-integrated-compile-sample/build.gradle.kts`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-integrated-compile-sample/settings.gradle.kts`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-integrated-compile-sample/design/design.json`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-integrated-compile-sample/demo-domain/build.gradle.kts`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-integrated-compile-sample/demo-domain/src/main/kotlin/com/acme/demo/domain/order/Order.kt`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-integrated-compile-sample/demo-application/build.gradle.kts`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-integrated-compile-sample/demo-adapter/build.gradle.kts`

### Potential Follow-On Fix Targets If Compile Hardening Exposes Direct Contract Gaps

- Modify: `cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kProjectConfigFactory.kt`
- Modify: `cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePlugin.kt`
- Modify: `cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignArtifactPlanner.kt`
- Modify: `cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignClientArtifactPlanner.kt`
- Modify: `cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignClientHandlerArtifactPlanner.kt`
- Modify: `cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignDomainEventArtifactPlanner.kt`
- Modify: `cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignDomainEventHandlerArtifactPlanner.kt`
- Modify: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/design/query.kt.peb`
- Modify: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/design/query_list.kt.peb`
- Modify: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/design/query_page.kt.peb`
- Modify: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/design/query_handler.kt.peb`
- Modify: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/design/query_list_handler.kt.peb`
- Modify: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/design/query_page_handler.kt.peb`
- Modify: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/design/client.kt.peb`
- Modify: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/design/client_handler.kt.peb`
- Modify: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/design/validator.kt.peb`
- Modify: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/design/api_payload.kt.peb`
- Modify: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/design/domain_event.kt.peb`
- Modify: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/design/domain_event_handler.kt.peb`

### Task 1: Advance The Mainline Roadmap Before Code Changes

**Files:**
- Modify: `docs/superpowers/mainline-roadmap.md`

- [ ] **Step 1: Update the roadmap so the just-finished domain-event slice is recorded as completed**

Replace the current `Completed Mainline Slices` Phase B list and `Current Next Mainline Slice` section so the roadmap says:

```md
- design api_payload migration
- design domain_event / domain_event_handler family migration
- design generator compile-level / integrated verification hardening
```

and the new current slice summary says:

```md
## Current Next Mainline Slice

The next mainline slice is:

- design generator compile-level / integrated verification hardening

Scope:

- raise representative design-family verification from generate-only to generate-plus-compile
- add a shared compile-capable functional harness
- add family-level compile verification and one small integrated compile sample
```

- [ ] **Step 2: Verify the roadmap text reflects the new mainline state**

Run:

```powershell
rg -n "design generator compile-level|design domain_event / domain_event_handler family migration|generate-plus-compile" docs/superpowers/mainline-roadmap.md
```

Expected: three matches showing the completed domain-event slice and the new compile-hardening slice.

- [ ] **Step 3: Commit the roadmap advance**

```bash
git add docs/superpowers/mainline-roadmap.md
git commit -m "docs: advance roadmap to compile hardening"
```

### Task 2: Add Shared Compile Fixture Support And One Smoke Compile Path

**Files:**
- Create: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/FunctionalFixtureSupport.kt`
- Create: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginCompileFunctionalTest.kt`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginFunctionalTest.kt`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-compile-sample/build.gradle.kts`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-compile-sample/settings.gradle.kts`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-compile-sample/design/design.json`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-compile-sample/domain/build.gradle.kts`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-compile-sample/demo-application/build.gradle.kts`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-compile-sample/demo-adapter/build.gradle.kts`

- [ ] **Step 1: Add a failing smoke test for generate-then-compile**

Create `PipelinePluginCompileFunctionalTest.kt` with a first smoke test shaped like:

```kotlin
@Test
fun `cap4kGenerate followed by application compileKotlin succeeds for design compile sample`() {
    val projectDir = Files.createTempDirectory("pipeline-functional-design-compile-smoke")
    copyCompileFixture(projectDir, "design-compile-sample")

    val (generateResult, compileResult) = generateThenCompile(
        projectDir,
        ":demo-application:compileKotlin",
    )

    assertTrue(generateResult.output.contains("BUILD SUCCESSFUL"))
    assertTrue(compileResult.output.contains("BUILD SUCCESSFUL"))
}
```

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-gradle:test --tests "*application compileKotlin succeeds for design compile sample*" --rerun-tasks
```

Expected: FAIL because neither the compile fixture helper nor the compile-capable fixture exists yet.

- [ ] **Step 2: Implement one shared compile-fixture utility instead of duplicating GradleRunner setup**

Create `FunctionalFixtureSupport.kt` with these exact entry points:

```kotlin
internal object FunctionalFixtureSupport {
    fun copyFixture(targetDir: Path, fixtureName: String = "design-sample")
    fun copyCompileFixture(targetDir: Path, fixtureName: String)
    fun runner(projectDir: Path, vararg arguments: String): GradleRunner
    fun generateThenCompile(projectDir: Path, vararg compileTasks: String): Pair<BuildResult, BuildResult>
}
```

Implement repository-root discovery defensively:

```kotlin
private fun locateRepositoryRoot(): Path {
    var current = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize()
    while (true) {
        val hasSettings = Files.exists(current.resolve("settings.gradle.kts"))
        val hasDddCore = Files.exists(current.resolve("ddd-core"))
        val hasPipelineGradle = Files.exists(current.resolve("cap4k-plugin-pipeline-gradle"))
        if (hasSettings && hasDddCore && hasPipelineGradle) {
            return current
        }
        current = current.parent ?: error("Unable to locate cap4k repository root")
    }
}
```

`copyCompileFixture(targetDir, fixtureName)` must replace the placeholder:

```kotlin
"__CAP4K_REPO_ROOT__"
```

inside the copied `settings.gradle.kts` with the absolute repository-root path converted to forward slashes.

- [ ] **Step 3: Move the existing raw fixture copy helper onto the shared utility**

Replace the private `copyFixture(targetDir, fixtureName)` at the bottom of `PipelinePluginFunctionalTest.kt` with:

```kotlin
import com.only4.cap4k.plugin.pipeline.gradle.FunctionalFixtureSupport.copyFixture
```

and update any new compile tests to call:

```kotlin
import com.only4.cap4k.plugin.pipeline.gradle.FunctionalFixtureSupport.copyCompileFixture
import com.only4.cap4k.plugin.pipeline.gradle.FunctionalFixtureSupport.generateThenCompile
```

- [ ] **Step 4: Create the first compile-capable fixture and wire composite-build dependency resolution**

Create `design-compile-sample/settings.gradle.kts` as:

```kotlin
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        maven {
            url = uri("https://maven.aliyun.com/repository/public")
        }
    }
}

rootProject.name = "design-compile-sample"
include("domain", "demo-application", "demo-adapter")
includeBuild("__CAP4K_REPO_ROOT__")
```

Create the root `build.gradle.kts` as:

```kotlin
plugins {
    id("com.only4.cap4k.plugin.pipeline")
}

cap4k {
    project {
        basePackage.set("com.acme.demo")
        applicationModulePath.set("demo-application")
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
        design {
            enabled.set(true)
        }
        designQueryHandler {
            enabled.set(true)
        }
        designClient {
            enabled.set(true)
        }
        designClientHandler {
            enabled.set(true)
        }
    }
}
```

Create `demo-application/build.gradle.kts` as:

```kotlin
plugins {
    kotlin("jvm") version "2.2.20"
}

dependencies {
    implementation("com.only4:ddd-core:0.4.2-SNAPSHOT")
}

kotlin {
    jvmToolchain(17)
}
```

Create `demo-adapter/build.gradle.kts` as:

```kotlin
plugins {
    kotlin("jvm") version "2.2.20"
}

dependencies {
    implementation(project(":demo-application"))
    implementation("com.only4:ddd-core:0.4.2-SNAPSHOT")
    implementation("org.springframework:spring-context")
}

kotlin {
    jvmToolchain(17)
}
```

Create `domain/build.gradle.kts` as:

```kotlin
tasks.register("kspKotlin") {
    outputs.file(layout.projectDirectory.file("build/generated/ksp/main/resources/metadata/aggregate-Order.json"))

    doLast {
        val outputFile = layout.projectDirectory
            .file("build/generated/ksp/main/resources/metadata/aggregate-Order.json")
            .asFile
        outputFile.parentFile.mkdirs()
        outputFile.writeText(
            """
            {
              "aggregateName": "Order",
              "aggregateRoot": {
                "className": "Order",
                "qualifiedName": "com.acme.demo.domain.aggregates.order.Order",
                "packageName": "com.acme.demo.domain.aggregates.order"
              }
            }
            """.trimIndent()
        )
    }
}
```

- [ ] **Step 5: Seed the smoke sample with one minimal request/query pair and rerun the smoke test**

Create `design/design.json` with one command entry and one plain query entry shaped like:

```json
[
  {
    "tag": "command",
    "package": "order.submit",
    "name": "SubmitOrder",
    "desc": "submit order",
    "aggregates": ["Order"],
    "requestFields": [
      { "name": "orderId", "type": "Long" }
    ],
    "responseFields": []
  },
  {
    "tag": "query",
    "package": "order.read",
    "name": "FindOrder",
    "desc": "find order",
    "aggregates": ["Order"],
    "requestFields": [
      { "name": "orderId", "type": "Long" }
    ],
    "responseFields": []
  }
]
```

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-gradle:test --tests "*application compileKotlin succeeds for design compile sample*" --rerun-tasks
```

Expected: PASS. The generated application-side Kotlin should compile against `ddd-core` via the shared `includeBuild("__CAP4K_REPO_ROOT__")` mechanism.

- [ ] **Step 6: Commit the shared harness slice**

```bash
git add docs/superpowers/mainline-roadmap.md cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/FunctionalFixtureSupport.kt cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginFunctionalTest.kt cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginCompileFunctionalTest.kt cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-compile-sample/build.gradle.kts cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-compile-sample/settings.gradle.kts cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-compile-sample/design/design.json cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-compile-sample/domain/build.gradle.kts cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-compile-sample/demo-application/build.gradle.kts cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-compile-sample/demo-adapter/build.gradle.kts
git commit -m "test(gradle): add compile fixture harness"
```

### Task 3: Upgrade The Request, Query-Handler, And Client Families To Compile-Level Verification

**Files:**
- Modify: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginCompileFunctionalTest.kt`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-compile-sample/build.gradle.kts`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-compile-sample/design/design.json`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-compile-sample/demo-application/build.gradle.kts`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-compile-sample/demo-adapter/build.gradle.kts`

- [ ] **Step 1: Add failing compile tests for the bounded request-side and handler-side family groups**

Add two focused tests:

```kotlin
@Test
fun `request and query variants compile in the application module`() {
    val projectDir = Files.createTempDirectory("pipeline-functional-design-request-compile")
    copyCompileFixture(projectDir, "design-compile-sample")

    val (generateResult, compileResult) = generateThenCompile(
        projectDir,
        ":demo-application:compileKotlin",
    )

    assertTrue(generateResult.output.contains("BUILD SUCCESSFUL"))
    assertTrue(compileResult.output.contains("BUILD SUCCESSFUL"))
    assertTrue(
        projectDir.resolve(
            "demo-application/src/main/kotlin/com/acme/demo/application/commands/order/submit/SubmitOrderCmd.kt"
        ).toFile().exists()
    )
    assertTrue(
        projectDir.resolve(
            "demo-application/src/main/kotlin/com/acme/demo/application/queries/order/read/FindOrderQry.kt"
        ).toFile().exists()
    )
    assertTrue(
        projectDir.resolve(
            "demo-application/src/main/kotlin/com/acme/demo/application/queries/order/read/FindOrderListQry.kt"
        ).toFile().exists()
    )
    assertTrue(
        projectDir.resolve(
            "demo-application/src/main/kotlin/com/acme/demo/application/queries/order/read/FindOrderPageQry.kt"
        ).toFile().exists()
    )
    assertTrue(
        projectDir.resolve(
            "demo-application/src/main/kotlin/com/acme/demo/application/distributed/clients/authorize/IssueTokenCli.kt"
        ).toFile().exists()
    )
}

@Test
fun `query-handler and client-handler variants compile in the adapter module`() {
    val projectDir = Files.createTempDirectory("pipeline-functional-design-handler-compile")
    copyCompileFixture(projectDir, "design-compile-sample")

    val (generateResult, compileResult) = generateThenCompile(
        projectDir,
        ":demo-adapter:compileKotlin",
    )

    assertTrue(generateResult.output.contains("BUILD SUCCESSFUL"))
    assertTrue(compileResult.output.contains("BUILD SUCCESSFUL"))
    assertTrue(
        projectDir.resolve(
            "demo-adapter/src/main/kotlin/com/acme/demo/adapter/queries/order/read/FindOrderQryHandler.kt"
        ).toFile().exists()
    )
    assertTrue(
        projectDir.resolve(
            "demo-adapter/src/main/kotlin/com/acme/demo/adapter/queries/order/read/FindOrderListQryHandler.kt"
        ).toFile().exists()
    )
    assertTrue(
        projectDir.resolve(
            "demo-adapter/src/main/kotlin/com/acme/demo/adapter/queries/order/read/FindOrderPageQryHandler.kt"
        ).toFile().exists()
    )
    assertTrue(
        projectDir.resolve(
            "demo-adapter/src/main/kotlin/com/acme/demo/adapter/application/distributed/clients/authorize/IssueTokenCliHandler.kt"
        ).toFile().exists()
    )
}
```

The first test must:

- copy `design-compile-sample`
- run `cap4kGenerate`
- run `:demo-application:compileKotlin`
- assert generated files exist:
  - `SubmitOrderCmd.kt`
  - `FindOrderQry.kt`
  - `FindOrderListQry.kt`
  - `FindOrderPageQry.kt`
  - `IssueTokenCli.kt`

The second test must:

- copy `design-compile-sample`
- run `cap4kGenerate`
- run `:demo-adapter:compileKotlin`
- assert generated files exist:
  - `FindOrderQryHandler.kt`
  - `FindOrderListQryHandler.kt`
  - `FindOrderPageQryHandler.kt`
  - `IssueTokenCliHandler.kt`

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-gradle:test --tests "*request and query variants compile in the application module*" --tests "*query-handler and client-handler variants compile in the adapter module*" --rerun-tasks
```

Expected: FAIL because the smoke fixture does not yet exercise the full migrated family set.

- [ ] **Step 2: Expand the compile sample so it covers the complete request-side family surface**

Update `design-compile-sample/design/design.json` so it contains these representative entries:

```json
{
  "tag": "query",
  "package": "order.read",
  "name": "FindOrder",
  "desc": "find order",
  "aggregates": ["Order"],
  "requestFields": [
    { "name": "orderId", "type": "Long" }
  ],
  "responseFields": []
},
{
  "tag": "query",
  "package": "order.read",
  "name": "FindOrderList",
  "desc": "find order list",
  "aggregates": ["Order"],
  "requestFields": [
    { "name": "customerId", "type": "Long" }
  ],
  "responseFields": []
},
{
  "tag": "query",
  "package": "order.read",
  "name": "FindOrderPage",
  "desc": "find order page",
  "aggregates": ["Order"],
  "requestFields": [
    { "name": "keyword", "type": "String" }
  ],
  "responseFields": []
},
{
  "tag": "client",
  "package": "authorize",
  "name": "IssueToken",
  "desc": "issue token client",
  "aggregates": ["Order"],
  "requestFields": [
    { "name": "account", "type": "String" }
  ],
  "responseFields": [
    { "name": "token", "type": "String" }
  ]
}
```

Keep the original `command` entry in the file so this fixture continues to cover the command path too.

- [ ] **Step 3: Finalize module dependencies for the request/client compile fixture**

Keep `demo-application/build.gradle.kts` minimal:

```kotlin
plugins {
    kotlin("jvm") version "2.2.20"
}

dependencies {
    implementation("com.only4:ddd-core:0.4.2-SNAPSHOT")
}

kotlin {
    jvmToolchain(17)
}
```

Keep `demo-adapter/build.gradle.kts` as:

```kotlin
plugins {
    kotlin("jvm") version "2.2.20"
}

dependencies {
    implementation(project(":demo-application"))
    implementation("com.only4:ddd-core:0.4.2-SNAPSHOT")
    implementation("org.springframework:spring-context")
}

kotlin {
    jvmToolchain(17)
}
```

Do not add Spring Boot or unrelated starters here. `spring-context` is enough for `@Service` and `@EventListener` compile visibility.

- [ ] **Step 4: Re-run the request and handler compile tests**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-gradle:test --tests "*request and query variants compile in the application module*" --tests "*query-handler and client-handler variants compile in the adapter module*" --rerun-tasks
```

Expected: PASS. Application-side generated request contracts and adapter-side generated handler contracts should compile in the modules that own them.

- [ ] **Step 5: Add direct compile-visible follow-on fixes only if the new tests expose them**

If either compile test fails, only touch the directly implicated files from the `Potential Follow-On Fix Targets` list.

Examples of acceptable fixes inside this step are:

```kotlin
// Cap4kProjectConfigFactory.kt
if (designQueryHandler.enabled.get() && adapterModulePath.orNull == null) {
    error("project.adapterModulePath is required when designQueryHandler is enabled.")
}
```

```pebble
{# query_handler.kt.peb #}
{{ use("org.springframework.stereotype.Service") -}}
{{ use("com.only4.cap4k.ddd.core.application.query.Query") -}}
```

or:

```kotlin
// DesignClientHandlerArtifactPlanner.kt
imports = listOf("$basePackage.application.distributed.clients.${request.packageName}.${request.typeName}")
```

Rerun the two tests immediately after the smallest direct fix.

- [ ] **Step 6: Commit the request/client compile-hardening slice**

```bash
git add cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginCompileFunctionalTest.kt cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-compile-sample/build.gradle.kts cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-compile-sample/design/design.json cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-compile-sample/demo-application/build.gradle.kts cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-compile-sample/demo-adapter/build.gradle.kts cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kProjectConfigFactory.kt cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePlugin.kt cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignArtifactPlanner.kt cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignClientArtifactPlanner.kt cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignClientHandlerArtifactPlanner.kt cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/design/query.kt.peb cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/design/query_list.kt.peb cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/design/query_page.kt.peb cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/design/query_handler.kt.peb cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/design/query_list_handler.kt.peb cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/design/query_page_handler.kt.peb cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/design/client.kt.peb cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/design/client_handler.kt.peb
git commit -m "test(gradle): compile-check design request families"
```

### Task 4: Add Compile-Level Verification For The Validator Family

**Files:**
- Modify: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginCompileFunctionalTest.kt`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-validator-compile-sample/build.gradle.kts`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-validator-compile-sample/settings.gradle.kts`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-validator-compile-sample/design/design.json`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-validator-compile-sample/demo-application/build.gradle.kts`

- [ ] **Step 1: Add a failing validator compile test**

Add this test:

```kotlin
@Test
fun `validator generation participates in application compileKotlin`() {
    val projectDir = Files.createTempDirectory("pipeline-functional-validator-compile")
    copyCompileFixture(projectDir, "design-validator-compile-sample")

    val (generateResult, compileResult) = generateThenCompile(
        projectDir,
        ":demo-application:compileKotlin",
    )

    val generatedFile = projectDir.resolve(
        "demo-application/src/main/kotlin/com/acme/demo/application/validators/order/OrderIdValid.kt"
    )

    assertTrue(generateResult.output.contains("BUILD SUCCESSFUL"))
    assertTrue(compileResult.output.contains("BUILD SUCCESSFUL"))
    assertTrue(generatedFile.toFile().exists())
}
```

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-gradle:test --tests "*validator generation participates in application compileKotlin*" --rerun-tasks
```

Expected: FAIL because the compile-capable validator fixture does not exist yet.

- [ ] **Step 2: Create the compile-capable validator fixture**

Create `settings.gradle.kts` as:

```kotlin
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

rootProject.name = "design-validator-compile-sample"
include("demo-application")
includeBuild("__CAP4K_REPO_ROOT__")
```

Create the root `build.gradle.kts` as:

```kotlin
plugins {
    id("com.only4.cap4k.plugin.pipeline")
}

cap4k {
    project {
        basePackage.set("com.acme.demo")
        applicationModulePath.set("demo-application")
    }
    sources {
        designJson {
            enabled.set(true)
            files.from("design/design.json")
        }
    }
    generators {
        designValidator {
            enabled.set(true)
        }
    }
}
```

Create `demo-application/build.gradle.kts` as:

```kotlin
plugins {
    kotlin("jvm") version "2.2.20"
}

dependencies {
    implementation("jakarta.validation:jakarta.validation-api:3.0.2")
    implementation("org.jetbrains.kotlin:kotlin-reflect:2.2.20")
}

kotlin {
    jvmToolchain(17)
}
```

- [ ] **Step 3: Seed the validator sample with one representative validator entry**

Create `design/design.json` as:

```json
[
  {
    "tag": "validator",
    "package": "order",
    "name": "OrderIdValid",
    "desc": "order id validator",
    "aggregates": [],
    "requestFields": [],
    "responseFields": []
  }
]
```

The test should continue to expect the generated file at:

```text
demo-application/src/main/kotlin/com/acme/demo/application/validators/order/OrderIdValid.kt
```

- [ ] **Step 4: Run the validator compile test and confirm it passes**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-gradle:test --tests "*validator generation participates in application compileKotlin*" --rerun-tasks
```

Expected: PASS. The generated annotation and nested `ConstraintValidator` should compile with the validation and reflection dependencies above.

- [ ] **Step 5: Apply the smallest direct template fix only if compilation exposes one**

If compilation fails, only touch:

```text
cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/design/validator.kt.peb
```

or:

```text
cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-validator-compile-sample/demo-application/build.gradle.kts
```

Examples of acceptable fixes:

```pebble
{{ use("kotlin.reflect.KClass") -}}
```

or:

```kotlin
implementation("org.jetbrains.kotlin:kotlin-reflect:2.2.20")
```

Rerun the validator compile test immediately after the direct fix.

- [ ] **Step 6: Commit the validator compile-hardening slice**

```bash
git add cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginCompileFunctionalTest.kt cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-validator-compile-sample/build.gradle.kts cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-validator-compile-sample/settings.gradle.kts cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-validator-compile-sample/design/design.json cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-validator-compile-sample/demo-application/build.gradle.kts cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/design/validator.kt.peb
git commit -m "test(gradle): compile-check design validator"
```

### Task 5: Add Compile-Level Verification For The Api-Payload Family

**Files:**
- Modify: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginCompileFunctionalTest.kt`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-api-payload-compile-sample/build.gradle.kts`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-api-payload-compile-sample/settings.gradle.kts`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-api-payload-compile-sample/design/design.json`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-api-payload-compile-sample/demo-adapter/build.gradle.kts`

- [ ] **Step 1: Add a failing api-payload compile test**

Add this test:

```kotlin
@Test
fun `api payload generation participates in adapter compileKotlin`() {
    val projectDir = Files.createTempDirectory("pipeline-functional-api-payload-compile")
    copyCompileFixture(projectDir, "design-api-payload-compile-sample")

    val (generateResult, compileResult) = generateThenCompile(
        projectDir,
        ":demo-adapter:compileKotlin",
    )

    val generatedFile = projectDir.resolve(
        "demo-adapter/src/main/kotlin/com/acme/demo/adapter/portal/api/payload/order/SubmitOrderPayload.kt"
    )

    assertTrue(generateResult.output.contains("BUILD SUCCESSFUL"))
    assertTrue(compileResult.output.contains("BUILD SUCCESSFUL"))
    assertTrue(generatedFile.toFile().exists())
}
```

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-gradle:test --tests "*api payload generation participates in adapter compileKotlin*" --rerun-tasks
```

Expected: FAIL because the compile-capable api-payload fixture does not exist yet.

- [ ] **Step 2: Create the compile-capable api-payload fixture**

Create `settings.gradle.kts` as:

```kotlin
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

rootProject.name = "design-api-payload-compile-sample"
include("demo-adapter")
includeBuild("__CAP4K_REPO_ROOT__")
```

Create the root `build.gradle.kts` as:

```kotlin
plugins {
    id("com.only4.cap4k.plugin.pipeline")
}

cap4k {
    project {
        basePackage.set("com.acme.demo")
        adapterModulePath.set("demo-adapter")
    }
    sources {
        designJson {
            enabled.set(true)
            files.from("design/design.json")
        }
    }
    generators {
        designApiPayload {
            enabled.set(true)
        }
    }
}
```

Create `demo-adapter/build.gradle.kts` as:

```kotlin
plugins {
    kotlin("jvm") version "2.2.20"
}

kotlin {
    jvmToolchain(17)
}
```

- [ ] **Step 3: Seed the fixture with one nested api-payload example so compile hardening covers the new nested-type contract**

Create `design/design.json` as:

```json
[
  {
    "tag": "api_payload",
    "package": "order",
    "name": "SubmitOrderPayload",
    "desc": "submit order payload",
    "aggregates": [],
    "requestFields": [
      { "name": "orderId", "type": "Long" },
      { "name": "address", "type": "Address", "nullable": true },
      { "name": "address.city", "type": "String" }
    ],
    "responseFields": [
      { "name": "receipt", "type": "Receipt", "nullable": true },
      { "name": "receipt.number", "type": "String" }
    ]
  }
]
```

The compile assertion should implicitly verify the new nested-type contract:

- `Request.Address` is nested under `Request`
- `Response.Receipt` is nested under `Response`
- nested types are not flattened at the outer object level

- [ ] **Step 4: Run the api-payload compile test and confirm it passes**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-gradle:test --tests "*api payload generation participates in adapter compileKotlin*" --rerun-tasks
```

Expected: PASS.

- [ ] **Step 5: Apply the smallest direct compile-visible fix only if the test exposes one**

If the compile test fails, only touch:

```text
cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/design/api_payload.kt.peb
```

or the new fixture files in `design-api-payload-compile-sample`.

An acceptable direct template fix would look like:

```pebble
object {{ typeName }} {
    data class Request(
        val orderId: Long,
        val address: Address?,
    ) {
        data class Address(
            val city: String,
        )
    }

    data class Response(
        val receipt: Receipt?,
    ) {
        data class Receipt(
            val number: String,
        )
    }
}
```

Rerun the api-payload compile test immediately after the direct fix.

- [ ] **Step 6: Commit the api-payload compile-hardening slice**

```bash
git add cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginCompileFunctionalTest.kt cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-api-payload-compile-sample/build.gradle.kts cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-api-payload-compile-sample/settings.gradle.kts cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-api-payload-compile-sample/design/design.json cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-api-payload-compile-sample/demo-adapter/build.gradle.kts cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/design/api_payload.kt.peb
git commit -m "test(gradle): compile-check design api payload"
```

### Task 6: Add Compile-Level Verification For The Domain-Event Family

**Files:**
- Modify: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginCompileFunctionalTest.kt`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-domain-event-compile-sample/build.gradle.kts`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-domain-event-compile-sample/settings.gradle.kts`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-domain-event-compile-sample/design/design.json`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-domain-event-compile-sample/demo-domain/build.gradle.kts`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-domain-event-compile-sample/demo-domain/src/main/kotlin/com/acme/demo/domain/order/Order.kt`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-domain-event-compile-sample/demo-application/build.gradle.kts`

- [ ] **Step 1: Add a failing domain-event compile test**

Add this test:

```kotlin
@Test
fun `domain event generation participates in domain and application compileKotlin`() {
    val projectDir = Files.createTempDirectory("pipeline-functional-domain-event-compile")
    copyCompileFixture(projectDir, "design-domain-event-compile-sample")

    val (generateResult, compileResult) = generateThenCompile(
        projectDir,
        ":demo-domain:compileKotlin",
        ":demo-application:compileKotlin",
    )

    val eventFile = projectDir.resolve(
        "demo-domain/src/main/kotlin/com/acme/demo/domain/order/events/OrderCreatedDomainEvent.kt"
    )
    val handlerFile = projectDir.resolve(
        "demo-application/src/main/kotlin/com/acme/demo/application/order/events/OrderCreatedDomainEventSubscriber.kt"
    )

    assertTrue(generateResult.output.contains("BUILD SUCCESSFUL"))
    assertTrue(compileResult.output.contains("BUILD SUCCESSFUL"))
    assertTrue(eventFile.toFile().exists())
    assertTrue(handlerFile.toFile().exists())
}
```

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-gradle:test --tests "*domain event generation participates in domain and application compileKotlin*" --rerun-tasks
```

Expected: FAIL because the compile-capable domain-event fixture does not exist yet.

- [ ] **Step 2: Create the compile-capable domain-event fixture with a real aggregate type**

Create `settings.gradle.kts` as:

```kotlin
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        maven {
            url = uri("https://maven.aliyun.com/repository/public")
        }
    }
}

rootProject.name = "design-domain-event-compile-sample"
include("demo-domain", "demo-application")
includeBuild("__CAP4K_REPO_ROOT__")
```

Create the root `build.gradle.kts` as:

```kotlin
plugins {
    id("com.only4.cap4k.plugin.pipeline")
}

cap4k {
    project {
        basePackage.set("com.acme.demo")
        domainModulePath.set("demo-domain")
        applicationModulePath.set("demo-application")
    }
    sources {
        designJson {
            enabled.set(true)
            files.from("design/design.json")
        }
        kspMetadata {
            enabled.set(true)
            inputDir.set("demo-domain/build/generated/ksp/main/resources/metadata")
        }
    }
    generators {
        designDomainEvent {
            enabled.set(true)
        }
        designDomainEventHandler {
            enabled.set(true)
        }
    }
}
```

Create `demo-domain/src/main/kotlin/com/acme/demo/domain/order/Order.kt` as:

```kotlin
package com.acme.demo.domain.order

class Order
```

- [ ] **Step 3: Add the real compile dependencies and metadata producer**

Create `demo-domain/build.gradle.kts` as:

```kotlin
plugins {
    kotlin("jvm") version "2.2.20"
}

dependencies {
    implementation("com.only4:ddd-core:0.4.2-SNAPSHOT")
}

kotlin {
    jvmToolchain(17)
}

tasks.register("kspKotlin") {
    outputs.file(layout.projectDirectory.file("build/generated/ksp/main/resources/metadata/aggregate-Order.json"))

    doLast {
        val outputFile = layout.projectDirectory
            .file("build/generated/ksp/main/resources/metadata/aggregate-Order.json")
            .asFile
        outputFile.parentFile.mkdirs()
        outputFile.writeText(
            """
            {
              "aggregateName": "Order",
              "aggregateRoot": {
                "className": "Order",
                "qualifiedName": "com.acme.demo.domain.order.Order",
                "packageName": "com.acme.demo.domain.order"
              }
            }
            """.trimIndent()
        )
    }
}
```

Create `demo-application/build.gradle.kts` as:

```kotlin
plugins {
    kotlin("jvm") version "2.2.20"
}

dependencies {
    implementation(project(":demo-domain"))
    implementation("com.only4:ddd-core:0.4.2-SNAPSHOT")
    implementation("org.springframework:spring-context")
}

kotlin {
    jvmToolchain(17)
}
```

- [ ] **Step 4: Seed the domain-event fixture with one representative event entry and rerun the compile test**

Create `design/design.json` as:

```json
[
  {
    "tag": "domain_event",
    "package": "order",
    "name": "OrderCreated",
    "desc": "order created event",
    "aggregates": ["Order"],
    "persist": false,
    "requestFields": [
      { "name": "reason", "type": "String" },
      { "name": "snapshot", "type": "Snapshot", "nullable": true },
      { "name": "snapshot.traceId", "type": "String" }
    ],
    "responseFields": []
  }
]
```

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-gradle:test --tests "*domain event generation participates in domain and application compileKotlin*" --rerun-tasks
```

Expected: PASS. This proves the generated event compiles against a real aggregate class and the generated subscriber compiles against Spring plus the domain module.

- [ ] **Step 5: Apply the smallest direct compile-visible fix only if the test exposes one**

If the compile test fails, only touch the directly implicated files from this set:

```text
cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignDomainEventArtifactPlanner.kt
cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignDomainEventHandlerArtifactPlanner.kt
cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/design/domain_event.kt.peb
cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/design/domain_event_handler.kt.peb
```

Examples of acceptable direct fixes:

```pebble
{{ use("com.only4.cap4k.ddd.core.domain.aggregate.annotation.Aggregate") -}}
{{ use("com.only4.cap4k.ddd.core.domain.event.annotation.DomainEvent") -}}
{{ use(aggregateType) -}}
```

or:

```kotlin
imports = listOf("$basePackage.domain.${event.packageName}.events.${event.typeName}")
```

Rerun the domain-event compile test immediately after the direct fix.

- [ ] **Step 6: Commit the domain-event compile-hardening slice**

```bash
git add cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginCompileFunctionalTest.kt cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-domain-event-compile-sample/build.gradle.kts cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-domain-event-compile-sample/settings.gradle.kts cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-domain-event-compile-sample/design/design.json cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-domain-event-compile-sample/demo-domain/build.gradle.kts cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-domain-event-compile-sample/demo-domain/src/main/kotlin/com/acme/demo/domain/order/Order.kt cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-domain-event-compile-sample/demo-application/build.gradle.kts cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignDomainEventArtifactPlanner.kt cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignDomainEventHandlerArtifactPlanner.kt cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/design/domain_event.kt.peb cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/design/domain_event_handler.kt.peb
git commit -m "test(gradle): compile-check design domain events"
```

### Task 7: Add The Small Integrated Compile Sample And Run Full Regression

**Files:**
- Modify: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginCompileFunctionalTest.kt`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-integrated-compile-sample/build.gradle.kts`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-integrated-compile-sample/settings.gradle.kts`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-integrated-compile-sample/design/design.json`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-integrated-compile-sample/demo-domain/build.gradle.kts`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-integrated-compile-sample/demo-domain/src/main/kotlin/com/acme/demo/domain/order/Order.kt`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-integrated-compile-sample/demo-application/build.gradle.kts`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-integrated-compile-sample/demo-adapter/build.gradle.kts`
- Modify if directly required by compile failures: the exact files listed under `Potential Follow-On Fix Targets`

- [ ] **Step 1: Add a failing integrated compile test that exercises the union of the representative dependency surfaces**

Add this test:

```kotlin
@Test
fun `integrated compile sample keeps migrated design families compile-safe together`() {
    val projectDir = Files.createTempDirectory("pipeline-functional-design-integrated-compile")
    copyCompileFixture(projectDir, "design-integrated-compile-sample")

    val (generateResult, compileResult) = generateThenCompile(
        projectDir,
        ":demo-domain:compileKotlin",
        ":demo-application:compileKotlin",
        ":demo-adapter:compileKotlin",
    )

    assertTrue(generateResult.output.contains("BUILD SUCCESSFUL"))
    assertTrue(compileResult.output.contains("BUILD SUCCESSFUL"))
}
```

Add existence assertions for at least these generated files:

- `demo-application/src/main/kotlin/com/acme/demo/application/queries/order/read/FindOrderQry.kt`
- `demo-adapter/src/main/kotlin/com/acme/demo/adapter/queries/order/read/FindOrderQryHandler.kt`
- `demo-application/src/main/kotlin/com/acme/demo/application/distributed/clients/authorize/IssueTokenCli.kt`
- `demo-adapter/src/main/kotlin/com/acme/demo/adapter/application/distributed/clients/authorize/IssueTokenCliHandler.kt`
- `demo-application/src/main/kotlin/com/acme/demo/application/validators/order/OrderIdValid.kt`
- `demo-adapter/src/main/kotlin/com/acme/demo/adapter/portal/api/payload/order/SubmitOrderPayload.kt`
- `demo-domain/src/main/kotlin/com/acme/demo/domain/order/events/OrderCreatedDomainEvent.kt`
- `demo-application/src/main/kotlin/com/acme/demo/application/order/events/OrderCreatedDomainEventSubscriber.kt`

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-gradle:test --tests "*integrated compile sample keeps migrated design families compile-safe together*" --rerun-tasks
```

Expected: FAIL because the integrated sample does not exist yet.

- [ ] **Step 2: Create the integrated compile sample with one representative entry per migrated family cluster**

Create `settings.gradle.kts` as:

```kotlin
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        maven {
            url = uri("https://maven.aliyun.com/repository/public")
        }
    }
}

rootProject.name = "design-integrated-compile-sample"
include("demo-domain", "demo-application", "demo-adapter")
includeBuild("__CAP4K_REPO_ROOT__")
```

Create the root `build.gradle.kts` as:

```kotlin
plugins {
    id("com.only4.cap4k.plugin.pipeline")
}

cap4k {
    project {
        basePackage.set("com.acme.demo")
        domainModulePath.set("demo-domain")
        applicationModulePath.set("demo-application")
        adapterModulePath.set("demo-adapter")
    }
    sources {
        designJson {
            enabled.set(true)
            files.from("design/design.json")
        }
        kspMetadata {
            enabled.set(true)
            inputDir.set("demo-domain/build/generated/ksp/main/resources/metadata")
        }
    }
    generators {
        design {
            enabled.set(true)
        }
        designQueryHandler {
            enabled.set(true)
        }
        designClient {
            enabled.set(true)
        }
        designClientHandler {
            enabled.set(true)
        }
        designValidator {
            enabled.set(true)
        }
        designApiPayload {
            enabled.set(true)
        }
        designDomainEvent {
            enabled.set(true)
        }
        designDomainEventHandler {
            enabled.set(true)
        }
    }
}
```

Create `demo-domain/src/main/kotlin/com/acme/demo/domain/order/Order.kt` exactly as:

```kotlin
package com.acme.demo.domain.order

class Order
```

- [ ] **Step 3: Give the integrated sample the union of the compile dependencies and metadata prerequisites**

Create `demo-domain/build.gradle.kts` as:

```kotlin
plugins {
    kotlin("jvm") version "2.2.20"
}

dependencies {
    implementation("com.only4:ddd-core:0.4.2-SNAPSHOT")
}

kotlin {
    jvmToolchain(17)
}

tasks.register("kspKotlin") {
    outputs.file(layout.projectDirectory.file("build/generated/ksp/main/resources/metadata/aggregate-Order.json"))

    doLast {
        val outputFile = layout.projectDirectory
            .file("build/generated/ksp/main/resources/metadata/aggregate-Order.json")
            .asFile
        outputFile.parentFile.mkdirs()
        outputFile.writeText(
            """
            {
              "aggregateName": "Order",
              "aggregateRoot": {
                "className": "Order",
                "qualifiedName": "com.acme.demo.domain.order.Order",
                "packageName": "com.acme.demo.domain.order"
              }
            }
            """.trimIndent()
        )
    }
}
```

Create `demo-application/build.gradle.kts` as:

```kotlin
plugins {
    kotlin("jvm") version "2.2.20"
}

dependencies {
    implementation(project(":demo-domain"))
    implementation("com.only4:ddd-core:0.4.2-SNAPSHOT")
    implementation("jakarta.validation:jakarta.validation-api:3.0.2")
    implementation("org.jetbrains.kotlin:kotlin-reflect:2.2.20")
    implementation("org.springframework:spring-context")
}

kotlin {
    jvmToolchain(17)
}
```

Create `demo-adapter/build.gradle.kts` as:

```kotlin
plugins {
    kotlin("jvm") version "2.2.20"
}

dependencies {
    implementation(project(":demo-domain"))
    implementation(project(":demo-application"))
    implementation("com.only4:ddd-core:0.4.2-SNAPSHOT")
    implementation("org.springframework:spring-context")
}

kotlin {
    jvmToolchain(17)
}
```

- [ ] **Step 4: Seed the integrated sample with one representative entry per family cluster and rerun the integrated test**

Create `design/design.json` with this representative set:

```json
[
  {
    "tag": "command",
    "package": "order.submit",
    "name": "SubmitOrder",
    "desc": "submit order",
    "aggregates": ["Order"],
    "requestFields": [
      { "name": "orderId", "type": "Long" }
    ],
    "responseFields": []
  },
  {
    "tag": "query",
    "package": "order.read",
    "name": "FindOrder",
    "desc": "find order",
    "aggregates": ["Order"],
    "requestFields": [
      { "name": "orderId", "type": "Long" }
    ],
    "responseFields": []
  },
  {
    "tag": "client",
    "package": "authorize",
    "name": "IssueToken",
    "desc": "issue token client",
    "aggregates": ["Order"],
    "requestFields": [
      { "name": "account", "type": "String" }
    ],
    "responseFields": [
      { "name": "token", "type": "String" }
    ]
  },
  {
    "tag": "validator",
    "package": "order",
    "name": "OrderIdValid",
    "desc": "order id validator",
    "aggregates": [],
    "requestFields": [],
    "responseFields": []
  },
  {
    "tag": "api_payload",
    "package": "order",
    "name": "SubmitOrderPayload",
    "desc": "submit order payload",
    "aggregates": [],
    "requestFields": [
      { "name": "orderId", "type": "Long" }
    ],
    "responseFields": []
  },
  {
    "tag": "domain_event",
    "package": "order",
    "name": "OrderCreated",
    "desc": "order created event",
    "aggregates": ["Order"],
    "persist": false,
    "requestFields": [
      { "name": "reason", "type": "String" }
    ],
    "responseFields": []
  }
]
```

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-gradle:test --tests "*integrated compile sample keeps migrated design families compile-safe together*" --rerun-tasks
```

Expected: PASS.

- [ ] **Step 5: Run the full regression suite for the hardening slice**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-source-design-json:test :cap4k-plugin-pipeline-core:test :cap4k-plugin-pipeline-generator-design:test :cap4k-plugin-pipeline-renderer-pebble:test :cap4k-plugin-pipeline-gradle:test --rerun-tasks
```

Expected: PASS across all listed modules.

- [ ] **Step 6: Check the working tree, commit the integrated sample, and stop only when the branch is clean except for intentional docs**

Run:

```powershell
git status --short --branch
```

Expected: only the files from this slice are modified.

Then commit:

```bash
git add cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginCompileFunctionalTest.kt cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-integrated-compile-sample/build.gradle.kts cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-integrated-compile-sample/settings.gradle.kts cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-integrated-compile-sample/design/design.json cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-integrated-compile-sample/demo-domain/build.gradle.kts cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-integrated-compile-sample/demo-domain/src/main/kotlin/com/acme/demo/domain/order/Order.kt cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-integrated-compile-sample/demo-application/build.gradle.kts cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-integrated-compile-sample/demo-adapter/build.gradle.kts cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kProjectConfigFactory.kt cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePlugin.kt cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignArtifactPlanner.kt cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignClientArtifactPlanner.kt cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignClientHandlerArtifactPlanner.kt cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignDomainEventArtifactPlanner.kt cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignDomainEventHandlerArtifactPlanner.kt cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/design/query.kt.peb cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/design/query_list.kt.peb cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/design/query_page.kt.peb cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/design/query_handler.kt.peb cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/design/query_list_handler.kt.peb cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/design/query_page_handler.kt.peb cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/design/client.kt.peb cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/design/client_handler.kt.peb cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/design/validator.kt.peb cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/design/api_payload.kt.peb cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/design/domain_event.kt.peb cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/design/domain_event_handler.kt.peb
git commit -m "test(gradle): harden design compile verification"
```

# Cap4k only-danmuku Local Maven Smoke Integration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Publish the current `cap4k` pipeline modules to `mavenLocal()`, temporarily wire `only-danmuku` to the pipeline plugin, run a real-project smoke generation for command/query/query-handler families, and restore the consumer worktree.

**Architecture:** Keep the validation loop narrow and reversible. `cap4k` remains the producer and publishes the Gradle plugin plus all pipeline module dependencies to `mavenLocal()`, while `only-danmuku` acts as a temporary consumer using only `design-json`, `ksp-metadata`, `design`, and `designQueryHandler`. The consumer changes are intentionally ephemeral: overwrite the root build with a smoke-only configuration, generate artifacts into isolated `pipeline.integration` packages, inspect the outputs, then restore the original files.

**Tech Stack:** Gradle, Kotlin DSL, Maven Local, existing `cap4k` pipeline modules, `only-danmuku` KSP metadata output

---

## File Structure

- Verify only: `cap4k/*`
  Responsibility: publish the already-implemented pipeline modules from the current `cap4k/master` workspace into `mavenLocal()`

- Modify temporarily: `only-danmuku/build.gradle.kts`
  Responsibility: replace legacy codegen plugin wiring with a smoke-only `cap4k` pipeline configuration

- Create temporarily: `only-danmuku/iterate/pipeline-smoke/video_post_pipeline_smoke_gen.json`
  Responsibility: feed one command and three query variants against the existing `VideoPost` aggregate

- Inspect only: `only-danmuku/build/cap4k/plan.json`
  Responsibility: verify real-project planner output, template ids, and generated file destinations

- Inspect only:
  - `only-danmuku/only-danmuku-application/src/main/kotlin/edu/only4/danmuku/application/commands/pipeline/integration/video_post/create/CreatePipelineSmokeVideoPostCmd.kt`
  - `only-danmuku/only-danmuku-application/src/main/kotlin/edu/only4/danmuku/application/queries/pipeline/integration/video_post/read/FindPipelineSmokeVideoPostQry.kt`
  - `only-danmuku/only-danmuku-application/src/main/kotlin/edu/only4/danmuku/application/queries/pipeline/integration/video_post/read/FindPipelineSmokeVideoPostListQry.kt`
  - `only-danmuku/only-danmuku-application/src/main/kotlin/edu/only4/danmuku/application/queries/pipeline/integration/video_post/read/FindPipelineSmokeVideoPostPageQry.kt`
  - `only-danmuku/only-danmuku-adapter/src/main/kotlin/edu/only4/danmuku/adapter/queries/pipeline/integration/video_post/read/FindPipelineSmokeVideoPostQryHandler.kt`
  - `only-danmuku/only-danmuku-adapter/src/main/kotlin/edu/only4/danmuku/adapter/queries/pipeline/integration/video_post/read/FindPipelineSmokeVideoPostListQryHandler.kt`
  - `only-danmuku/only-danmuku-adapter/src/main/kotlin/edu/only4/danmuku/adapter/queries/pipeline/integration/video_post/read/FindPipelineSmokeVideoPostPageQryHandler.kt`
  Responsibility: verify the real generated request/query/query-handler family

### Task 1: Publish the Current cap4k Pipeline to `mavenLocal()`

**Files:**
- Verify only: `cap4k`
- Verify only: `%USERPROFILE%\\.m2\\repository\\com\\only4`

- [ ] **Step 1: Re-run the current pipeline regression suite before publishing**

Run from `c:\Users\LD_moxeii\Documents\code\only-workspace\cap4k`:

```powershell
./gradlew :cap4k-plugin-pipeline-generator-design:test :cap4k-plugin-pipeline-renderer-pebble:test :cap4k-plugin-pipeline-gradle:test
```

Expected: `BUILD SUCCESSFUL` with all three module test tasks passing.

- [ ] **Step 2: Publish every pipeline module required by the Gradle plugin to `mavenLocal()`**

Run from `c:\Users\LD_moxeii\Documents\code\only-workspace\cap4k`:

```powershell
./gradlew `
  :cap4k-plugin-pipeline-api:publishToMavenLocal `
  :cap4k-plugin-pipeline-core:publishToMavenLocal `
  :cap4k-plugin-pipeline-renderer-api:publishToMavenLocal `
  :cap4k-plugin-pipeline-renderer-pebble:publishToMavenLocal `
  :cap4k-plugin-pipeline-source-design-json:publishToMavenLocal `
  :cap4k-plugin-pipeline-source-db:publishToMavenLocal `
  :cap4k-plugin-pipeline-source-ksp-metadata:publishToMavenLocal `
  :cap4k-plugin-pipeline-source-ir-analysis:publishToMavenLocal `
  :cap4k-plugin-pipeline-generator-design:publishToMavenLocal `
  :cap4k-plugin-pipeline-generator-aggregate:publishToMavenLocal `
  :cap4k-plugin-pipeline-generator-drawing-board:publishToMavenLocal `
  :cap4k-plugin-pipeline-generator-flow:publishToMavenLocal `
  :cap4k-plugin-pipeline-gradle:publishToMavenLocal
```

Expected: `BUILD SUCCESSFUL` and Maven publications created for all listed modules at version `0.4.2-SNAPSHOT`.

- [ ] **Step 3: Verify the plugin marker and implementation module exist in the local Maven repository**

Run:

```powershell
Test-Path "$env:USERPROFILE\.m2\repository\com\only4\cap4k\plugin\pipeline\com.only4.cap4k.plugin.pipeline.gradle.plugin\0.4.2-SNAPSHOT"
Test-Path "$env:USERPROFILE\.m2\repository\com\only4\cap4k-plugin-pipeline-gradle\0.4.2-SNAPSHOT"
Get-ChildItem "$env:USERPROFILE\.m2\repository\com\only4\cap4k-plugin-pipeline-gradle\0.4.2-SNAPSHOT"
```

Expected:

- the first two `Test-Path` commands return `True`
- the `Get-ChildItem` output lists the published `.jar`, `.pom`, and metadata files for `cap4k-plugin-pipeline-gradle`

### Task 2: Prepare the Temporary only-danmuku Consumer Configuration

**Files:**
- Modify temporarily: `only-danmuku/build.gradle.kts`
- Create temporarily: `only-danmuku/iterate/pipeline-smoke/video_post_pipeline_smoke_gen.json`

- [ ] **Step 1: Confirm the consumer worktree is clean and back up the root build file**

Run from `c:\Users\LD_moxeii\Documents\code\only-workspace\only-danmuku`:

```powershell
git status --short --branch
Copy-Item -LiteralPath .\build.gradle.kts -Destination .\build.gradle.kts.pipeline-smoke.bak
```

Expected:

- `git status --short --branch` prints `## master...origin/master` with no modified files
- `build.gradle.kts.pipeline-smoke.bak` exists in the repository root

- [ ] **Step 2: Overwrite `only-danmuku/build.gradle.kts` with the smoke-only pipeline configuration**

Replace the entire file with:

```kotlin
// [cap4k-ddd-codegen-gradle-plugin:do-not-overwrite]
plugins {
    id("buildsrc.convention.kotlin-jvm")
    id("com.only4.cap4k.plugin.pipeline") version "0.4.2-SNAPSHOT"
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

kotlin {
    jvmToolchain(17)
}

cap4k {
    project {
        basePackage.set("edu.only4.danmuku")
        applicationModulePath.set("only-danmuku-application")
        adapterModulePath.set("only-danmuku-adapter")
    }
    sources {
        designJson {
            enabled.set(true)
            files.from("iterate/pipeline-smoke/video_post_pipeline_smoke_gen.json")
        }
        kspMetadata {
            enabled.set(true)
            inputDir.set("only-danmuku-domain/build/generated/ksp/main/resources/metadata")
        }
    }
    generators {
        design {
            enabled.set(true)
        }
        designQueryHandler {
            enabled.set(true)
        }
    }
}
```

- [ ] **Step 3: Add the smoke design file that exercises command/default-query/list-query/page-query generation**

Create `only-danmuku/iterate/pipeline-smoke/video_post_pipeline_smoke_gen.json` with this content:

```json
[
  {
    "tag": "cmd",
    "package": "pipeline.integration.video_post.create",
    "name": "CreatePipelineSmokeVideoPost",
    "desc": "pipeline smoke command for video post",
    "aggregates": ["VideoPost"],
    "requestFields": [
      { "name": "videoPostId", "type": "Long" },
      { "name": "title", "type": "String" },
      { "name": "operatorId", "type": "Long" }
    ],
    "responseFields": [
      { "name": "accepted", "type": "Boolean" }
    ]
  },
  {
    "tag": "qry",
    "package": "pipeline.integration.video_post.read",
    "name": "FindPipelineSmokeVideoPost",
    "desc": "pipeline smoke default query for video post",
    "aggregates": ["VideoPost"],
    "requestFields": [
      { "name": "videoPostId", "type": "Long" }
    ],
    "responseFields": [
      { "name": "videoPostId", "type": "Long" },
      { "name": "title", "type": "String" }
    ]
  },
  {
    "tag": "qry",
    "package": "pipeline.integration.video_post.read",
    "name": "FindPipelineSmokeVideoPostList",
    "desc": "pipeline smoke list query for video post",
    "aggregates": ["VideoPost"],
    "requestFields": [
      { "name": "authorId", "type": "Long" }
    ],
    "responseFields": [
      { "name": "videoPostId", "type": "Long" },
      { "name": "title", "type": "String" }
    ]
  },
  {
    "tag": "qry",
    "package": "pipeline.integration.video_post.read",
    "name": "FindPipelineSmokeVideoPostPage",
    "desc": "pipeline smoke page query for video post",
    "aggregates": ["VideoPost"],
    "requestFields": [
      { "name": "keyword", "type": "String", "nullable": true }
    ],
    "responseFields": [
      { "name": "videoPostId", "type": "Long" },
      { "name": "title", "type": "String" }
    ]
  }
]
```

- [ ] **Step 4: Verify the temporary consumer state before running Gradle**

Run:

```powershell
git status --short
Test-Path .\iterate\pipeline-smoke\video_post_pipeline_smoke_gen.json
```

Expected:

- `git status --short` shows `M build.gradle.kts`, the backup file, and the new smoke JSON file
- `Test-Path` returns `True`

### Task 3: Run the Real-Project Smoke Validation

**Files:**
- Verify only: `only-danmuku-domain/build/generated/ksp/main/resources/metadata`
- Inspect: `only-danmuku/build/cap4k/plan.json`
- Inspect generated output under `only-danmuku-application` and `only-danmuku-adapter`

- [ ] **Step 1: Check that KSP aggregate metadata is present before planning**

Run from `c:\Users\LD_moxeii\Documents\code\only-workspace\only-danmuku`:

```powershell
Test-Path .\only-danmuku-domain\build\generated\ksp\main\resources\metadata\aggregates-index.json
Get-ChildItem .\only-danmuku-domain\build\generated\ksp\main\resources\metadata | Select-Object -First 10 Name
```

Expected:

- `Test-Path` returns `True`
- the directory listing includes `aggregates-index.json` and aggregate files such as `aggregate-VideoPost.json`

- [ ] **Step 2: Refresh domain KSP metadata only if Step 1 reported `False`**

If Step 1 returned `False`, run:

```powershell
./gradlew :only-danmuku-domain:kspKotlin --rerun-tasks
Test-Path .\only-danmuku-domain\build\generated\ksp\main\resources\metadata\aggregates-index.json
```

Expected:

- `:only-danmuku-domain:kspKotlin` completes successfully
- the second `Test-Path` returns `True`

- [ ] **Step 3: Run `cap4kPlan` and verify the plan report is produced**

Run:

```powershell
./gradlew cap4kPlan --rerun-tasks
Test-Path .\build\cap4k\plan.json
```

Expected:

- Gradle prints `BUILD SUCCESSFUL`
- `Test-Path .\build\cap4k\plan.json` returns `True`

- [ ] **Step 4: Inspect `build/cap4k/plan.json` for the expected query variants and handler outputs**

Run:

```powershell
rg -n "CreatePipelineSmokeVideoPostCmd|FindPipelineSmokeVideoPostQry|FindPipelineSmokeVideoPostListQry|FindPipelineSmokeVideoPostPageQry|design/query.kt.peb|design/query_list.kt.peb|design/query_page.kt.peb|design/query_handler.kt.peb|design/query_list_handler.kt.peb|design/query_page_handler.kt.peb" .\build\cap4k\plan.json
```

Expected:

- the plan report contains all four request type names
- the plan report contains the three bounded query request template ids:
  - `design/query.kt.peb`
  - `design/query_list.kt.peb`
  - `design/query_page.kt.peb`
- the plan report contains the three bounded handler template ids:
  - `design/query_handler.kt.peb`
  - `design/query_list_handler.kt.peb`
  - `design/query_page_handler.kt.peb`

- [ ] **Step 5: Run `cap4kGenerate` and verify generation succeeds**

Run:

```powershell
./gradlew cap4kGenerate --rerun-tasks
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 6: Verify the exact generated file set exists**

Run:

```powershell
@(
  ".\only-danmuku-application\src\main\kotlin\edu\only4\danmuku\application\commands\pipeline\integration\video_post\create\CreatePipelineSmokeVideoPostCmd.kt",
  ".\only-danmuku-application\src\main\kotlin\edu\only4\danmuku\application\queries\pipeline\integration\video_post\read\FindPipelineSmokeVideoPostQry.kt",
  ".\only-danmuku-application\src\main\kotlin\edu\only4\danmuku\application\queries\pipeline\integration\video_post\read\FindPipelineSmokeVideoPostListQry.kt",
  ".\only-danmuku-application\src\main\kotlin\edu\only4\danmuku\application\queries\pipeline\integration\video_post\read\FindPipelineSmokeVideoPostPageQry.kt",
  ".\only-danmuku-adapter\src\main\kotlin\edu\only4\danmuku\adapter\queries\pipeline\integration\video_post\read\FindPipelineSmokeVideoPostQryHandler.kt",
  ".\only-danmuku-adapter\src\main\kotlin\edu\only4\danmuku\adapter\queries\pipeline\integration\video_post\read\FindPipelineSmokeVideoPostListQryHandler.kt",
  ".\only-danmuku-adapter\src\main\kotlin\edu\only4\danmuku\adapter\queries\pipeline\integration\video_post\read\FindPipelineSmokeVideoPostPageQryHandler.kt"
) | ForEach-Object {
  "{0} => {1}" -f $_, (Test-Path $_)
}
```

Expected: every line ends with `=> True`

- [ ] **Step 7: Inspect the generated query and handler families for bounded variant routing**

Run:

```powershell
rg -n "RequestParam<Response>|ListQueryParam<Response>|PageQueryParam<Response>|Query<FindPipelineSmokeVideoPostQry.Request, FindPipelineSmokeVideoPostQry.Response>|ListQuery<FindPipelineSmokeVideoPostListQry.Request, FindPipelineSmokeVideoPostListQry.Response>|PageQuery<FindPipelineSmokeVideoPostPageQry.Request, FindPipelineSmokeVideoPostPageQry.Response>" `
  .\only-danmuku-application\src\main\kotlin\edu\only4\danmuku\application\queries\pipeline\integration\video_post\read `
  .\only-danmuku-adapter\src\main\kotlin\edu\only4\danmuku\adapter\queries\pipeline\integration\video_post\read
```

Expected:

- the default query uses `RequestParam<Response>`
- the list query uses `ListQueryParam<Response>`
- the page query uses `PageQueryParam<Response>`
- the default handler implements `Query<FindPipelineSmokeVideoPostQry.Request, FindPipelineSmokeVideoPostQry.Response>`
- the list handler implements `ListQuery<FindPipelineSmokeVideoPostListQry.Request, FindPipelineSmokeVideoPostListQry.Response>`
- the page handler implements `PageQuery<FindPipelineSmokeVideoPostPageQry.Request, FindPipelineSmokeVideoPostPageQry.Response>`

### Task 4: Restore only-danmuku to Its Original State

**Files:**
- Restore: `only-danmuku/build.gradle.kts`
- Delete temporarily: `only-danmuku/iterate/pipeline-smoke/video_post_pipeline_smoke_gen.json`

- [ ] **Step 1: Restore the original root build file and remove the temporary smoke JSON**

Run from `c:\Users\LD_moxeii\Documents\code\only-workspace\only-danmuku`:

```powershell
Move-Item -LiteralPath .\build.gradle.kts.pipeline-smoke.bak -Destination .\build.gradle.kts -Force
Remove-Item -LiteralPath .\iterate\pipeline-smoke\video_post_pipeline_smoke_gen.json
if (Test-Path .\iterate\pipeline-smoke) {
  if (-not (Get-ChildItem .\iterate\pipeline-smoke)) {
    Remove-Item -LiteralPath .\iterate\pipeline-smoke
  }
}
```

Expected:

- the original root build file is back in place
- the smoke JSON file is deleted
- the temporary `iterate/pipeline-smoke` directory is removed only if empty

- [ ] **Step 2: Verify the consumer worktree is clean again**

Run:

```powershell
git status --short --branch
```

Expected: `## master...origin/master` with no modified or untracked files.

- [ ] **Step 3: Summarize the smoke result and stop without committing consumer changes**

Capture these facts in the final operator summary:

```text
- whether plugin resolution from mavenLocal succeeded
- whether cap4kPlan succeeded
- whether cap4kGenerate succeeded
- which files were generated
- whether any support-track framework issues were uncovered
- confirmation that only-danmuku was restored and left clean
```

Expected: no commit is created in `only-danmuku`; any lasting follow-up work should happen separately in `cap4k`.

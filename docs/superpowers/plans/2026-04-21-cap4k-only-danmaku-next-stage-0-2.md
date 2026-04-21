# Cap4k only-danmaku-next Stage 0-2 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Create the first executable `only-danmaku-next` tutorial tranche as an independent repository with a Stage 0 minimal baseline, a Stage 1 bootstrap walkthrough, and a Stage 2 aggregate-generation walkthrough, each with its own stable commit, annotated tag, and runnable checkpoint.

**Architecture:** Keep `only-danmaku-next` outside the Cap4k repo as a sibling repository at `C:\Users\LD_moxeii\Documents\code\only-workspace\only-danmaku-next`. Use a clean Stage 0 handwritten Kotlin multi-module baseline first, then add Cap4k through a composite-build `includeBuild("../cap4k")` teaching setup for Stage 1 and Stage 2. Stage 1 generates a bounded `bootstrap-preview/` subtree so users can compare bootstrap output with the handwritten root project without self-overwriting the tutorial repository; Stage 2 adds a single DB-backed aggregate slice and commits the generated aggregate artifacts so the tag is inspectable without rerunning generation.

**Tech Stack:** Gradle 8.13 wrapper, Kotlin JVM 2.2.20, composite-build plugin resolution via `includeBuild("../cap4k")`, Cap4k bootstrap + aggregate generators, H2 JDBC schema source, annotated Git tags

---

## Scope Split

The spec covers six tutorial stages, but that is too large for one safe implementation plan. This plan intentionally covers only the first executable tranche:

1. Stage 0: minimal project baseline
2. Stage 1: bootstrap skeleton
3. Stage 2: aggregate generation

Stages 3-5 need a follow-up plan after this repository exists and the first three tags are stable.

## File Structure

### New repository root

- Create: `C:\Users\LD_moxeii\Documents\code\only-workspace\only-danmaku-next\`

### Stage 0 files

- Create: `C:\Users\LD_moxeii\Documents\code\only-workspace\only-danmaku-next\.gitignore`
- Create: `C:\Users\LD_moxeii\Documents\code\only-workspace\only-danmaku-next\gradlew`
- Create: `C:\Users\LD_moxeii\Documents\code\only-workspace\only-danmaku-next\gradlew.bat`
- Create: `C:\Users\LD_moxeii\Documents\code\only-workspace\only-danmaku-next\gradle\wrapper\gradle-wrapper.jar`
- Create: `C:\Users\LD_moxeii\Documents\code\only-workspace\only-danmaku-next\gradle\wrapper\gradle-wrapper.properties`
- Create: `C:\Users\LD_moxeii\Documents\code\only-workspace\only-danmaku-next\settings.gradle.kts`
- Create: `C:\Users\LD_moxeii\Documents\code\only-workspace\only-danmaku-next\build.gradle.kts`
- Create: `C:\Users\LD_moxeii\Documents\code\only-workspace\only-danmaku-next\README.md`
- Create: `C:\Users\LD_moxeii\Documents\code\only-workspace\only-danmaku-next\docs\stages\00-minimal-project.md`
- Create: `C:\Users\LD_moxeii\Documents\code\only-workspace\only-danmaku-next\docs\stages\01-bootstrap-skeleton.md`
- Create: `C:\Users\LD_moxeii\Documents\code\only-workspace\only-danmaku-next\docs\stages\02-aggregate-generation.md`
- Create: `C:\Users\LD_moxeii\Documents\code\only-workspace\only-danmaku-next\docs\stages\03-design-slice.md`
- Create: `C:\Users\LD_moxeii\Documents\code\only-workspace\only-danmaku-next\docs\stages\04-runtime-glue.md`
- Create: `C:\Users\LD_moxeii\Documents\code\only-workspace\only-danmaku-next\docs\stages\05-first-feature-port.md`
- Create: `C:\Users\LD_moxeii\Documents\code\only-workspace\only-danmaku-next\only-danmaku-domain\build.gradle.kts`
- Create: `C:\Users\LD_moxeii\Documents\code\only-workspace\only-danmaku-next\only-danmaku-application\build.gradle.kts`
- Create: `C:\Users\LD_moxeii\Documents\code\only-workspace\only-danmaku-next\only-danmaku-adapter\build.gradle.kts`
- Create: `C:\Users\LD_moxeii\Documents\code\only-workspace\only-danmaku-next\only-danmaku-domain\src\main\kotlin\edu\only4\danmaku\domain\DomainMarker.kt`
- Create: `C:\Users\LD_moxeii\Documents\code\only-workspace\only-danmaku-next\only-danmaku-application\src\main\kotlin\edu\only4\danmaku\application\Stage0PingService.kt`
- Create: `C:\Users\LD_moxeii\Documents\code\only-workspace\only-danmaku-next\only-danmaku-adapter\src\main\kotlin\edu\only4\danmaku\adapter\Stage0PingEndpoint.kt`

### Stage 1 files

- Modify: `C:\Users\LD_moxeii\Documents\code\only-workspace\only-danmaku-next\settings.gradle.kts`
- Modify: `C:\Users\LD_moxeii\Documents\code\only-workspace\only-danmaku-next\build.gradle.kts`
- Modify: `C:\Users\LD_moxeii\Documents\code\only-workspace\only-danmaku-next\README.md`
- Modify: `C:\Users\LD_moxeii\Documents\code\only-workspace\only-danmaku-next\docs\stages\01-bootstrap-skeleton.md`
- Create: `C:\Users\LD_moxeii\Documents\code\only-workspace\only-danmaku-next\codegen\bootstrap-slots\root\README.md.peb`
- Create: `C:\Users\LD_moxeii\Documents\code\only-workspace\only-danmaku-next\codegen\bootstrap-slots\domain-package\BootstrapDomainMarker.kt.peb`
- Create after generation and commit as teaching output: `C:\Users\LD_moxeii\Documents\code\only-workspace\only-danmaku-next\bootstrap-preview\...`

### Stage 2 files

- Modify: `C:\Users\LD_moxeii\Documents\code\only-workspace\only-danmaku-next\build.gradle.kts`
- Modify: `C:\Users\LD_moxeii\Documents\code\only-workspace\only-danmaku-next\README.md`
- Modify: `C:\Users\LD_moxeii\Documents\code\only-workspace\only-danmaku-next\docs\stages\02-aggregate-generation.md`
- Modify: `C:\Users\LD_moxeii\Documents\code\only-workspace\only-danmaku-next\only-danmaku-domain\build.gradle.kts`
- Create: `C:\Users\LD_moxeii\Documents\code\only-workspace\only-danmaku-next\db\schema.sql`
- Create after generation and commit as teaching output:
  - `C:\Users\LD_moxeii\Documents\code\only-workspace\only-danmaku-next\only-danmaku-domain\src\main\kotlin\edu\only4\danmaku\domain\_share\meta\video_post\SVideoPost.kt`
  - `C:\Users\LD_moxeii\Documents\code\only-workspace\only-danmaku-next\only-danmaku-domain\src\main\kotlin\edu\only4\danmaku\domain\aggregates\video_post\VideoPost.kt`
  - `C:\Users\LD_moxeii\Documents\code\only-workspace\only-danmaku-next\only-danmaku-domain\src\main\kotlin\edu\only4\danmaku\domain\aggregates\video_post\AggVideoPost.kt`
  - `C:\Users\LD_moxeii\Documents\code\only-workspace\only-danmaku-next\only-danmaku-domain\src\main\kotlin\edu\only4\danmaku\domain\aggregates\video_post\factory\VideoPostFactory.kt`
  - `C:\Users\LD_moxeii\Documents\code\only-workspace\only-danmaku-next\only-danmaku-domain\src\main\kotlin\edu\only4\danmaku\domain\aggregates\video_post\specification\VideoPostSpecification.kt`
  - `C:\Users\LD_moxeii\Documents\code\only-workspace\only-danmaku-next\only-danmaku-adapter\src\main\kotlin\edu\only4\danmaku\adapter\domain\repositories\VideoPostRepository.kt`

### Responsibilities

- `settings.gradle.kts`
  - define the tutorial repository layout and, from Stage 1 onward, resolve the local Cap4k plugin through `includeBuild("../cap4k")`

- `build.gradle.kts`
  - keep the tutorial repo configuration legible; first as a pure Kotlin baseline, then as a combined bootstrap + aggregate Cap4k consumer

- `README.md`
  - act as the root navigation document with the stage table, ownership model, and exact primary commands

- `docs/stages/*.md`
  - provide the per-stage walkthroughs; early non-implemented stages stay truthful by explicitly saying they are not available at the current tag

- `only-danmaku-*`
  - hold the minimal handwritten project baseline and, later, the generated aggregate artifacts that users are meant to inspect

- `codegen/bootstrap-slots/**`
  - supply the bounded Stage 1 slot example that teaches how bootstrap insertion works without opening the whole preset

- `db/schema.sql`
  - serve as the smallest explicit DB truth for Stage 2 aggregate generation

## Task 1: Create the Stage 0 Minimal Baseline Repository

**Files:**
- Create: `C:\Users\LD_moxeii\Documents\code\only-workspace\only-danmaku-next\...` (all Stage 0 files listed above)

- [ ] **Step 1: Create the repository skeleton, wrapper, and Git repository**

Run:

```powershell
New-Item -ItemType Directory -Force -Path `
  C:\Users\LD_moxeii\Documents\code\only-workspace\only-danmaku-next, `
  C:\Users\LD_moxeii\Documents\code\only-workspace\only-danmaku-next\docs\stages, `
  C:\Users\LD_moxeii\Documents\code\only-workspace\only-danmaku-next\gradle\wrapper, `
  C:\Users\LD_moxeii\Documents\code\only-workspace\only-danmaku-next\only-danmaku-domain\src\main\kotlin\edu\only4\danmaku\domain, `
  C:\Users\LD_moxeii\Documents\code\only-workspace\only-danmaku-next\only-danmaku-application\src\main\kotlin\edu\only4\danmaku\application, `
  C:\Users\LD_moxeii\Documents\code\only-workspace\only-danmaku-next\only-danmaku-adapter\src\main\kotlin\edu\only4\danmaku\adapter | Out-Null

Copy-Item C:\Users\LD_moxeii\Documents\code\only-workspace\cap4k\gradlew C:\Users\LD_moxeii\Documents\code\only-workspace\only-danmaku-next\gradlew
Copy-Item C:\Users\LD_moxeii\Documents\code\only-workspace\cap4k\gradlew.bat C:\Users\LD_moxeii\Documents\code\only-workspace\only-danmaku-next\gradlew.bat
Copy-Item C:\Users\LD_moxeii\Documents\code\only-workspace\cap4k\gradle\wrapper\gradle-wrapper.jar C:\Users\LD_moxeii\Documents\code\only-workspace\only-danmaku-next\gradle\wrapper\gradle-wrapper.jar
Copy-Item C:\Users\LD_moxeii\Documents\code\only-workspace\cap4k\gradle\wrapper\gradle-wrapper.properties C:\Users\LD_moxeii\Documents\code\only-workspace\only-danmaku-next\gradle\wrapper\gradle-wrapper.properties

git init C:\Users\LD_moxeii\Documents\code\only-workspace\only-danmaku-next
```

- [ ] **Step 2: Write the Stage 0 build files, README, and stage documents**

Create `C:\Users\LD_moxeii\Documents\code\only-workspace\only-danmaku-next\.gitignore`:

```gitignore
.gradle/
.kotlin/
build/
**/build/
```

Create `C:\Users\LD_moxeii\Documents\code\only-workspace\only-danmaku-next\settings.gradle.kts`:

```kotlin
rootProject.name = "only-danmaku-next"

include("only-danmaku-domain", "only-danmaku-application", "only-danmaku-adapter")
```

Create `C:\Users\LD_moxeii\Documents\code\only-workspace\only-danmaku-next\build.gradle.kts`:

```kotlin
plugins {
    kotlin("jvm") version "2.2.20" apply false
}

group = "edu.only4"
version = "0.0.1-SNAPSHOT"

allprojects {
    repositories {
        mavenCentral()
        maven {
            url = uri("https://maven.aliyun.com/repository/public")
        }
    }
}
```

Create `C:\Users\LD_moxeii\Documents\code\only-workspace\only-danmaku-next\README.md`:

```md
# only-danmaku-next

`only-danmaku-next` is a staged Cap4k tutorial and consumer project. It is not an in-place migration branch of `only-danmuku`.

## How to use this repository

1. Check out a stage tag.
2. Read the matching file in `docs/stages/`.
3. Run the listed command.
4. Compare generated, handwritten, and copied files.

## Stage Map

| Stage | Tag | Status | Goal | Primary Command |
| --- | --- | --- | --- | --- |
| 0 | `only-danmaku-next-stage-0-minimal` | available | minimal multi-module baseline | `./gradlew build` |
| 1 | `only-danmaku-next-stage-1-bootstrap` | planned | bootstrap skeleton | `./gradlew cap4kBootstrap` |
| 2 | `only-danmaku-next-stage-2-aggregate` | planned | aggregate from DB schema | `./gradlew cap4kGenerate` |
| 3 | `only-danmaku-next-stage-3-design` | planned | design slice | `./gradlew cap4kGenerate` |
| 4 | `only-danmaku-next-stage-4-runtime` | planned | runtime glue | `./gradlew runtimeSmoke` |
| 5 | `only-danmaku-next-stage-5-first-feature` | planned | first real feature port | `./gradlew build` |

## Code Ownership

- Generated by Cap4k: none at this tag.
- Handwritten: everything under `only-danmaku-domain`, `only-danmaku-application`, `only-danmaku-adapter`, and `docs/`.
- Copied from `only-danmuku`: none at this tag.
```

Create `C:\Users\LD_moxeii\Documents\code\only-workspace\only-danmaku-next\docs\stages\00-minimal-project.md`:

````md
# Stage 0: Minimal Project Baseline

## Goal

Establish the smallest understandable Kotlin multi-module baseline before any Cap4k plugin-generated code appears.

## Starting Point

Fresh repository checkout.

## What Changes

- adds a root Gradle build
- adds three modules: domain, application, adapter
- adds one minimal handwritten class per layer

## Generated By Cap4k

None.

## Written By Hand

All files in this stage.

## Copied From only-danmuku

None.

## Commands

```powershell
./gradlew build
```

## Expected Result

Gradle completes successfully and all three modules compile.

## Why This Matters

This stage teaches the project shape before Cap4k generation enters the repository.

## Next Stage

Stage 1 introduces bootstrap as a previewable skeleton generator.
````

Create `C:\Users\LD_moxeii\Documents\code\only-workspace\only-danmaku-next\docs\stages\01-bootstrap-skeleton.md`:

````md
# Stage 1: Bootstrap Skeleton

This stage is not available at tag `only-danmaku-next-stage-0-minimal`.

Check out `only-danmaku-next-stage-1-bootstrap` once it exists.
````

Create `C:\Users\LD_moxeii\Documents\code\only-workspace\only-danmaku-next\docs\stages\02-aggregate-generation.md`:

````md
# Stage 2: Aggregate Generation

This stage is not available at tag `only-danmaku-next-stage-0-minimal`.

Check out `only-danmaku-next-stage-2-aggregate` once it exists.
````

Create `C:\Users\LD_moxeii\Documents\code\only-workspace\only-danmaku-next\docs\stages\03-design-slice.md`:

````md
# Stage 3: Design Slice

This stage is not available at tag `only-danmaku-next-stage-0-minimal`.

Check out `only-danmaku-next-stage-3-design` once it exists.
````

Create `C:\Users\LD_moxeii\Documents\code\only-workspace\only-danmaku-next\docs\stages\04-runtime-glue.md`:

````md
# Stage 4: Runtime Glue

This stage is not available at tag `only-danmaku-next-stage-0-minimal`.

Check out `only-danmaku-next-stage-4-runtime` once it exists.
````

Create `C:\Users\LD_moxeii\Documents\code\only-workspace\only-danmaku-next\docs\stages\05-first-feature-port.md`:

````md
# Stage 5: First Real Feature Port

This stage is not available at tag `only-danmaku-next-stage-0-minimal`.

Check out `only-danmaku-next-stage-5-first-feature` once it exists.
````

- [ ] **Step 3: Write the Stage 0 module build files and handwritten Kotlin sources**

Create `C:\Users\LD_moxeii\Documents\code\only-workspace\only-danmaku-next\only-danmaku-domain\build.gradle.kts`:

```kotlin
plugins {
    kotlin("jvm")
}

kotlin {
    jvmToolchain(17)
}
```

Create `C:\Users\LD_moxeii\Documents\code\only-workspace\only-danmaku-next\only-danmaku-application\build.gradle.kts`:

```kotlin
plugins {
    kotlin("jvm")
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":only-danmaku-domain"))
}
```

Create `C:\Users\LD_moxeii\Documents\code\only-workspace\only-danmaku-next\only-danmaku-adapter\build.gradle.kts`:

```kotlin
plugins {
    kotlin("jvm")
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":only-danmaku-domain"))
    implementation(project(":only-danmaku-application"))
}
```

Create `C:\Users\LD_moxeii\Documents\code\only-workspace\only-danmaku-next\only-danmaku-domain\src\main\kotlin\edu\only4\danmaku\domain\DomainMarker.kt`:

```kotlin
package edu.only4.danmaku.domain

object DomainMarker
```

Create `C:\Users\LD_moxeii\Documents\code\only-workspace\only-danmaku-next\only-danmaku-application\src\main\kotlin\edu\only4\danmaku\application\Stage0PingService.kt`:

```kotlin
package edu.only4.danmaku.application

import edu.only4.danmaku.domain.DomainMarker

class Stage0PingService {
    fun ping(): String = "pong:${DomainMarker::class.simpleName}"
}
```

Create `C:\Users\LD_moxeii\Documents\code\only-workspace\only-danmaku-next\only-danmaku-adapter\src\main\kotlin\edu\only4\danmaku\adapter\Stage0PingEndpoint.kt`:

```kotlin
package edu.only4.danmaku.adapter

import edu.only4.danmaku.application.Stage0PingService

class Stage0PingEndpoint(
    private val pingService: Stage0PingService = Stage0PingService(),
) {
    fun handle(): String = pingService.ping()
}
```

- [ ] **Step 4: Run the Stage 0 checkpoint**

Run:

```powershell
./gradlew build
```

Expected: `BUILD SUCCESSFUL` and all three modules compile with no Cap4k plugin involvement.

- [ ] **Step 5: Commit and tag Stage 0**

Run:

```powershell
git -C C:\Users\LD_moxeii\Documents\code\only-workspace\only-danmaku-next add .
git -C C:\Users\LD_moxeii\Documents\code\only-workspace\only-danmaku-next commit -m "feat: add only-danmaku-next stage 0 baseline"
git -C C:\Users\LD_moxeii\Documents\code\only-workspace\only-danmaku-next tag -a only-danmaku-next-stage-0-minimal -m "Stage 0: minimal project baseline"
```

## Task 2: Add the Stage 1 Bootstrap Skeleton Walkthrough

**Files:**
- Modify: `C:\Users\LD_moxeii\Documents\code\only-workspace\only-danmaku-next\settings.gradle.kts`
- Modify: `C:\Users\LD_moxeii\Documents\code\only-workspace\only-danmaku-next\build.gradle.kts`
- Modify: `C:\Users\LD_moxeii\Documents\code\only-workspace\only-danmaku-next\README.md`
- Modify: `C:\Users\LD_moxeii\Documents\code\only-workspace\only-danmaku-next\docs\stages\01-bootstrap-skeleton.md`
- Create: `C:\Users\LD_moxeii\Documents\code\only-workspace\only-danmaku-next\codegen\bootstrap-slots\root\README.md.peb`
- Create: `C:\Users\LD_moxeii\Documents\code\only-workspace\only-danmaku-next\codegen\bootstrap-slots\domain-package\BootstrapDomainMarker.kt.peb`
- Create after generation and commit: `C:\Users\LD_moxeii\Documents\code\only-workspace\only-danmaku-next\bootstrap-preview\...`

- [ ] **Step 1: Update settings and root build for composite-build Cap4k bootstrap usage**

Replace `C:\Users\LD_moxeii\Documents\code\only-workspace\only-danmaku-next\settings.gradle.kts` with:

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

includeBuild("../cap4k")

rootProject.name = "only-danmaku-next"

include("only-danmaku-domain", "only-danmaku-application", "only-danmaku-adapter")
```

Replace `C:\Users\LD_moxeii\Documents\code\only-workspace\only-danmaku-next\build.gradle.kts` with:

```kotlin
plugins {
    kotlin("jvm") version "2.2.20" apply false
    id("com.only4.cap4k.plugin.pipeline")
}

group = "edu.only4"
version = "0.0.1-SNAPSHOT"

allprojects {
    repositories {
        mavenCentral()
        maven {
            url = uri("https://maven.aliyun.com/repository/public")
        }
    }
}

cap4k {
    bootstrap {
        enabled.set(true)
        preset.set("ddd-multi-module")
        projectName.set("bootstrap-preview")
        basePackage.set("edu.only4.danmaku")
        conflictPolicy.set("OVERWRITE")
        modules {
            domainModuleName.set("only-danmaku-domain")
            applicationModuleName.set("only-danmaku-application")
            adapterModuleName.set("only-danmaku-adapter")
        }
        slots {
            root.from("codegen/bootstrap-slots/root")
            modulePackage("domain").from("codegen/bootstrap-slots/domain-package")
        }
    }
}
```

- [ ] **Step 2: Add the bounded slot templates and Stage 1 teaching docs**

Create `C:\Users\LD_moxeii\Documents\code\only-workspace\only-danmaku-next\codegen\bootstrap-slots\root\README.md.peb`:

```md
# {{ projectName }}

Generated by Cap4k bootstrap for the Stage 1 walkthrough.
```

Create `C:\Users\LD_moxeii\Documents\code\only-workspace\only-danmaku-next\codegen\bootstrap-slots\domain-package\BootstrapDomainMarker.kt.peb`:

```kotlin
package {{ basePackage }}.domain

object BootstrapDomainMarker
```

Replace `C:\Users\LD_moxeii\Documents\code\only-workspace\only-danmaku-next\docs\stages\01-bootstrap-skeleton.md` with:

````md
# Stage 1: Bootstrap Skeleton

## Goal

Introduce Cap4k bootstrap as a project skeleton mechanism without overwriting the tutorial repository root.

## Starting Point

Check out `only-danmaku-next-stage-0-minimal`.

## What Changes

- adds composite-build plugin resolution via `includeBuild("../cap4k")`
- adds a bounded bootstrap configuration
- generates a `bootstrap-preview/` subtree for side-by-side comparison

## Generated By Cap4k

- `bootstrap-preview/settings.gradle.kts`
- `bootstrap-preview/build.gradle.kts`
- `bootstrap-preview/README.md`
- `bootstrap-preview/only-danmaku-domain/**`
- `bootstrap-preview/only-danmaku-application/**`
- `bootstrap-preview/only-danmaku-adapter/**`

## Written By Hand

- root Gradle configuration
- slot templates under `codegen/bootstrap-slots/`
- this stage document

## Copied From only-danmuku

None.

## Commands

```powershell
./gradlew cap4kBootstrapPlan
./gradlew cap4kBootstrap
```

## Expected Result

Cap4k writes a `bootstrap-preview/` subtree that shows the generated multi-module skeleton and the slot-provided marker files.

## Why This Matters

Users can compare handwritten Stage 0 structure with bootstrap output before Cap4k starts generating into the real tutorial modules.

## Next Stage

Stage 2 adds a single DB-backed aggregate slice to the real tutorial modules.
````

Replace the `README.md` stage map and ownership section with:

```md
# only-danmaku-next

`only-danmaku-next` is a staged Cap4k tutorial and consumer project. It is not an in-place migration branch of `only-danmuku`.

## How to use this repository

1. Check out a stage tag.
2. Read the matching file in `docs/stages/`.
3. Run the listed command.
4. Compare generated, handwritten, and copied files.

## Stage Map

| Stage | Tag | Status | Goal | Primary Command |
| --- | --- | --- | --- | --- |
| 0 | `only-danmaku-next-stage-0-minimal` | available | minimal multi-module baseline | `./gradlew build` |
| 1 | `only-danmaku-next-stage-1-bootstrap` | available | bootstrap skeleton | `./gradlew cap4kBootstrap` |
| 2 | `only-danmaku-next-stage-2-aggregate` | planned | aggregate from DB schema | `./gradlew cap4kGenerate` |
| 3 | `only-danmaku-next-stage-3-design` | planned | design slice | `./gradlew cap4kGenerate` |
| 4 | `only-danmaku-next-stage-4-runtime` | planned | runtime glue | `./gradlew runtimeSmoke` |
| 5 | `only-danmaku-next-stage-5-first-feature` | planned | first real feature port | `./gradlew build` |

## Code Ownership

- Generated by Cap4k: everything under `bootstrap-preview/`.
- Handwritten: root Gradle files, `codegen/bootstrap-slots/**`, tutorial docs, and the handwritten Stage 0 modules.
- Copied from `only-danmuku`: none at this tag.
```

- [ ] **Step 3: Run bootstrap plan and generation, then verify the preview subtree exists**

Run:

```powershell
./gradlew cap4kBootstrapPlan
./gradlew cap4kBootstrap

Test-Path .\bootstrap-preview\settings.gradle.kts
Test-Path .\bootstrap-preview\README.md
Test-Path .\bootstrap-preview\only-danmaku-domain\src\main\kotlin\edu\only4\danmaku\domain\BootstrapDomainMarker.kt
```

Expected: both Gradle tasks succeed and all three `Test-Path` checks return `True`.

- [ ] **Step 4: Commit the Stage 1 configuration, docs, and generated preview**

Run:

```powershell
git -C C:\Users\LD_moxeii\Documents\code\only-workspace\only-danmaku-next add settings.gradle.kts build.gradle.kts README.md docs/stages/01-bootstrap-skeleton.md codegen bootstrap-preview
git -C C:\Users\LD_moxeii\Documents\code\only-workspace\only-danmaku-next commit -m "feat: add only-danmaku-next stage 1 bootstrap walkthrough"
git -C C:\Users\LD_moxeii\Documents\code\only-workspace\only-danmaku-next tag -a only-danmaku-next-stage-1-bootstrap -m "Stage 1: bootstrap skeleton"
```

## Task 3: Add the Stage 2 Aggregate Generation Walkthrough

**Files:**
- Modify: `C:\Users\LD_moxeii\Documents\code\only-workspace\only-danmaku-next\build.gradle.kts`
- Modify: `C:\Users\LD_moxeii\Documents\code\only-workspace\only-danmaku-next\README.md`
- Modify: `C:\Users\LD_moxeii\Documents\code\only-workspace\only-danmaku-next\docs\stages\02-aggregate-generation.md`
- Modify: `C:\Users\LD_moxeii\Documents\code\only-workspace\only-danmaku-next\only-danmaku-domain\build.gradle.kts`
- Create: `C:\Users\LD_moxeii\Documents\code\only-workspace\only-danmaku-next\db\schema.sql`
- Create after generation and commit:
  - `C:\Users\LD_moxeii\Documents\code\only-workspace\only-danmaku-next\only-danmaku-domain\src\main\kotlin\edu\only4\danmaku\domain\_share\meta\video_post\SVideoPost.kt`
  - `C:\Users\LD_moxeii\Documents\code\only-workspace\only-danmaku-next\only-danmaku-domain\src\main\kotlin\edu\only4\danmaku\domain\aggregates\video_post\VideoPost.kt`
  - `C:\Users\LD_moxeii\Documents\code\only-workspace\only-danmaku-next\only-danmaku-domain\src\main\kotlin\edu\only4\danmaku\domain\aggregates\video_post\AggVideoPost.kt`
  - `C:\Users\LD_moxeii\Documents\code\only-workspace\only-danmaku-next\only-danmaku-domain\src\main\kotlin\edu\only4\danmaku\domain\aggregates\video_post\factory\VideoPostFactory.kt`
  - `C:\Users\LD_moxeii\Documents\code\only-workspace\only-danmaku-next\only-danmaku-domain\src\main\kotlin\edu\only4\danmaku\domain\aggregates\video_post\specification\VideoPostSpecification.kt`
  - `C:\Users\LD_moxeii\Documents\code\only-workspace\only-danmaku-next\only-danmaku-adapter\src\main\kotlin\edu\only4\danmaku\adapter\domain\repositories\VideoPostRepository.kt`

- [ ] **Step 1: Add db schema, Cap4k aggregate configuration, and domain compile dependencies**

Create `C:\Users\LD_moxeii\Documents\code\only-workspace\only-danmaku-next\db\schema.sql`:

```sql
create table video_post (
    id bigint primary key comment '@GeneratedValue=IDENTITY;',
    title varchar(255) not null,
    published boolean default false
);

comment on table video_post is '@AggregateRoot=true;';
```

Replace `C:\Users\LD_moxeii\Documents\code\only-workspace\only-danmaku-next\only-danmaku-domain\build.gradle.kts` with:

```kotlin
plugins {
    kotlin("jvm")
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation("com.only4:ddd-core:0.4.2-SNAPSHOT")
    implementation("org.springframework:spring-context")
}
```

Replace `C:\Users\LD_moxeii\Documents\code\only-workspace\only-danmaku-next\build.gradle.kts` with:

```kotlin
plugins {
    kotlin("jvm") version "2.2.20" apply false
    id("com.only4.cap4k.plugin.pipeline")
}

group = "edu.only4"
version = "0.0.1-SNAPSHOT"

allprojects {
    repositories {
        mavenCentral()
        maven {
            url = uri("https://maven.aliyun.com/repository/public")
        }
    }
}

val schemaScriptPath = layout.projectDirectory.file("db/schema.sql").asFile.absolutePath.replace("\\", "/")
val dbFilePath = layout.buildDirectory.file("h2/only-danmaku-next").get().asFile.absolutePath.replace("\\", "/")

cap4k {
    project {
        basePackage.set("edu.only4.danmaku")
        domainModulePath.set("only-danmaku-domain")
        applicationModulePath.set("only-danmaku-application")
        adapterModulePath.set("only-danmaku-adapter")
    }

    sources {
        db {
            enabled.set(true)
            url.set(
                "jdbc:h2:file:$dbFilePath;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false;INIT=RUNSCRIPT FROM '$schemaScriptPath'"
            )
            username.set("sa")
            password.set("secret")
            schema.set("PUBLIC")
            includeTables.set(listOf("video_post"))
            excludeTables.set(emptyList())
        }
    }

    generators {
        aggregate {
            enabled.set(true)
        }
    }

    templates {
        conflictPolicy.set("SKIP")
    }

    bootstrap {
        enabled.set(true)
        preset.set("ddd-multi-module")
        projectName.set("bootstrap-preview")
        basePackage.set("edu.only4.danmaku")
        conflictPolicy.set("OVERWRITE")
        modules {
            domainModuleName.set("only-danmaku-domain")
            applicationModuleName.set("only-danmaku-application")
            adapterModuleName.set("only-danmaku-adapter")
        }
        slots {
            root.from("codegen/bootstrap-slots/root")
            modulePackage("domain").from("codegen/bootstrap-slots/domain-package")
        }
    }
}
```

- [ ] **Step 2: Write the Stage 2 walkthrough doc and update the root README**

Replace `C:\Users\LD_moxeii\Documents\code\only-workspace\only-danmaku-next\docs\stages\02-aggregate-generation.md` with:

````md
# Stage 2: Aggregate Generation

## Goal

Introduce the first DB-backed aggregate and show how explicit schema and table comments flow into generated aggregate artifacts.

## Starting Point

Check out `only-danmaku-next-stage-1-bootstrap`.

## What Changes

- adds `db/schema.sql`
- adds Cap4k aggregate generation configuration
- generates aggregate files into the real tutorial modules

## Generated By Cap4k

- `only-danmaku-domain/src/main/kotlin/edu/only4/danmaku/domain/_share/meta/video_post/SVideoPost.kt`
- `only-danmaku-domain/src/main/kotlin/edu/only4/danmaku/domain/aggregates/video_post/VideoPost.kt`
- `only-danmaku-domain/src/main/kotlin/edu/only4/danmaku/domain/aggregates/video_post/AggVideoPost.kt`
- `only-danmaku-domain/src/main/kotlin/edu/only4/danmaku/domain/aggregates/video_post/factory/VideoPostFactory.kt`
- `only-danmaku-domain/src/main/kotlin/edu/only4/danmaku/domain/aggregates/video_post/specification/VideoPostSpecification.kt`
- `only-danmaku-adapter/src/main/kotlin/edu/only4/danmaku/adapter/domain/repositories/VideoPostRepository.kt`

## Written By Hand

- `db/schema.sql`
- root Gradle configuration
- this stage document

## Copied From only-danmuku

None.

## Commands

```powershell
./gradlew cap4kPlan
./gradlew cap4kGenerate
./gradlew :only-danmaku-domain:compileKotlin
```

## Expected Result

Cap4k plans and generates a single `video_post` aggregate family, and the generated domain sources compile.

## Why This Matters

This is the first stage where users see explicit DB truth become real aggregate output in the tutorial modules.

## Next Stage

Stage 3 will add the first design-generation vertical slice.
````

Replace the `README.md` stage map and ownership section with:

```md
# only-danmaku-next

`only-danmaku-next` is a staged Cap4k tutorial and consumer project. It is not an in-place migration branch of `only-danmuku`.

## How to use this repository

1. Check out a stage tag.
2. Read the matching file in `docs/stages/`.
3. Run the listed command.
4. Compare generated, handwritten, and copied files.

## Stage Map

| Stage | Tag | Status | Goal | Primary Command |
| --- | --- | --- | --- | --- |
| 0 | `only-danmaku-next-stage-0-minimal` | available | minimal multi-module baseline | `./gradlew build` |
| 1 | `only-danmaku-next-stage-1-bootstrap` | available | bootstrap skeleton | `./gradlew cap4kBootstrap` |
| 2 | `only-danmaku-next-stage-2-aggregate` | available | aggregate from DB schema | `./gradlew cap4kGenerate` |
| 3 | `only-danmaku-next-stage-3-design` | planned | design slice | `./gradlew cap4kGenerate` |
| 4 | `only-danmaku-next-stage-4-runtime` | planned | runtime glue | `./gradlew runtimeSmoke` |
| 5 | `only-danmaku-next-stage-5-first-feature` | planned | first real feature port | `./gradlew build` |

## Code Ownership

- Generated by Cap4k: `bootstrap-preview/**`, `only-danmaku-domain/src/main/kotlin/edu/only4/danmaku/domain/_share/meta/**`, `only-danmaku-domain/src/main/kotlin/edu/only4/danmaku/domain/aggregates/**`, and `only-danmaku-adapter/src/main/kotlin/edu/only4/danmaku/adapter/domain/repositories/**`.
- Handwritten: root Gradle files, `db/schema.sql`, `codegen/bootstrap-slots/**`, the Stage 0 handwritten sources, and tutorial docs.
- Copied from `only-danmuku`: none at this tag.
```

- [ ] **Step 3: Run aggregate planning, generation, and domain compilation**

Run:

```powershell
./gradlew cap4kPlan
./gradlew cap4kGenerate
./gradlew :only-danmaku-domain:compileKotlin
```

Expected: all three commands succeed, with `cap4kGenerate` writing the aggregate files listed in the Stage 2 doc and `:only-danmaku-domain:compileKotlin` ending with `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit the Stage 2 configuration, docs, and generated aggregate artifacts**

Run:

```powershell
git -C C:\Users\LD_moxeii\Documents\code\only-workspace\only-danmaku-next add build.gradle.kts README.md docs/stages/02-aggregate-generation.md db/schema.sql only-danmaku-domain/build.gradle.kts only-danmaku-domain/src/main/kotlin/edu/only4/danmaku/domain/_share/meta/video_post/SVideoPost.kt only-danmaku-domain/src/main/kotlin/edu/only4/danmaku/domain/aggregates/video_post/VideoPost.kt only-danmaku-domain/src/main/kotlin/edu/only4/danmaku/domain/aggregates/video_post/AggVideoPost.kt only-danmaku-domain/src/main/kotlin/edu/only4/danmaku/domain/aggregates/video_post/factory/VideoPostFactory.kt only-danmaku-domain/src/main/kotlin/edu/only4/danmaku/domain/aggregates/video_post/specification/VideoPostSpecification.kt only-danmaku-adapter/src/main/kotlin/edu/only4/danmaku/adapter/domain/repositories/VideoPostRepository.kt
git -C C:\Users\LD_moxeii\Documents\code\only-workspace\only-danmaku-next commit -m "feat: add only-danmaku-next stage 2 aggregate walkthrough"
git -C C:\Users\LD_moxeii\Documents\code\only-workspace\only-danmaku-next tag -a only-danmaku-next-stage-2-aggregate -m "Stage 2: aggregate generation"
```

## Task 4: Audit the Three Stage Tags and Freeze the Tranche

**Files:**
- No new files unless a real documentation or configuration mismatch is discovered during audit

- [ ] **Step 1: Verify the annotated stage tags exist**

Run:

```powershell
git -C C:\Users\LD_moxeii\Documents\code\only-workspace\only-danmaku-next tag --list "only-danmaku-next-stage-*"
```

Expected:

```text
only-danmaku-next-stage-0-minimal
only-danmaku-next-stage-1-bootstrap
only-danmaku-next-stage-2-aggregate
```

- [ ] **Step 2: Verify the repository is clean at the Stage 2 head**

Run:

```powershell
git -C C:\Users\LD_moxeii\Documents\code\only-workspace\only-danmaku-next status --short
```

Expected: no output.

- [ ] **Step 3: Re-run the tranche checkpoints from the current head**

Run:

```powershell
./gradlew cap4kBootstrapPlan
./gradlew cap4kBootstrap
./gradlew cap4kPlan
./gradlew cap4kGenerate
./gradlew :only-danmaku-domain:compileKotlin
```

Expected: all commands succeed from the current Stage 2 head. `cap4kBootstrap` succeeds because Stage 1 uses `conflictPolicy = OVERWRITE`; `cap4kGenerate` succeeds because Stage 2 uses `templates.conflictPolicy = SKIP`.

- [ ] **Step 4: Fix only real tranche-breaking mismatches if any are found**

If audit exposes a real problem, apply the minimal fix and re-run only the affected checkpoint. Acceptable examples:

```kotlin
includeBuild("../cap4k")
```

or:

```kotlin
conflictPolicy.set("OVERWRITE")
```

Do not widen this tranche to Stage 3 design work, runtime glue, or copied business code.

- [ ] **Step 5: Commit only if an audit fix was required**

Run only if Step 4 changed files:

```powershell
git -C C:\Users\LD_moxeii\Documents\code\only-workspace\only-danmaku-next add <exact files changed>
git -C C:\Users\LD_moxeii\Documents\code\only-workspace\only-danmaku-next commit -m "fix: harden only-danmaku-next stage 0-2 walkthrough"
```

## Spec Coverage Check

- Stage 0 minimal repository with docs, modules, and a stable tag is implemented in Task 1.
- Stage 1 bootstrap preview with bounded slots, generated preview output, and a stable tag is implemented in Task 2.
- Stage 2 aggregate slice with explicit DB truth, generated aggregate artifacts, and a stable tag is implemented in Task 3.
- The hard git rules and stable checkpoint expectations are enforced by the tag/commit steps in Tasks 1-4.
- The generated/handwritten/copied ownership model is implemented in the README and stage docs across Tasks 1-3.
- Later stages are intentionally excluded from this tranche and must be planned separately.

## Placeholder Scan

- No `TODO`, `TBD`, or deferred content placeholders remain in this plan.
- Every code-writing step includes complete file content.
- Every verification step includes an exact command and expected outcome.
- Future stages are not referenced as implementation details; they are only referenced as out-of-scope follow-on work.

## Type Consistency Check

- Repository root path is consistently `C:\Users\LD_moxeii\Documents\code\only-workspace\only-danmaku-next`.
- Module names are consistently `only-danmaku-domain`, `only-danmaku-application`, and `only-danmaku-adapter`.
- Base package is consistently `edu.only4.danmaku`.
- Stage tags consistently use the `only-danmaku-next-stage-N-*` naming convention.
- Stage 1 always generates into `bootstrap-preview/`; Stage 2 always generates aggregate output into the real tutorial modules.

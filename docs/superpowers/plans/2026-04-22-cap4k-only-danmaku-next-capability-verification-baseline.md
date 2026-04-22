# Cap4k only-danmaku-next Capability Verification Baseline Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Establish `cap4k` as the single capability-truth source and realign `only-danmaku-next` into a branch-based capability verification project with a clean `stage/0-minimal` baseline and a clean `stage/1-bootstrap-in-place` baseline.

**Architecture:** The implementation is split across two repositories with a strict ownership boundary. `cap4k` gains the first durable `docs/superpowers/capability-matrix.md` plus a roadmap pointer to that matrix; `only-danmaku-next` is then re-cut into cumulative stage branches where `stage/0-minimal` is a Cap4k host baseline with no active capability loop, and `stage/1-bootstrap-in-place` owns the complete bootstrap in-place loop, including bootstrap DSL input, root slot input, bootstrap execution, generated in-place output, and bilingual verification docs. `main` will mirror the latest completed stable stage instead of carrying mixed future-stage content.

**Tech Stack:** Markdown docs, Git branches, Gradle Kotlin DSL, composite-build plugin resolution via `includeBuild("../cap4k")`, Cap4k bootstrap DSL/tasks, Pebble root-slot template, PowerShell, Git

---

## Precondition

This plan implements the spec at:

- [2026-04-22-cap4k-only-danmaku-next-capability-verification-project-design.md](../specs/2026-04-22-cap4k-only-danmaku-next-capability-verification-project-design.md)

Important execution constraints:

- the plan spans two repositories:
  - `C:\Users\LD_moxeii\Documents\code\only-workspace\cap4k`
  - `C:\Users\LD_moxeii\Documents\code\only-workspace\only-danmaku-next`
- `only-danmaku-next` is already a Git repository with tag-oriented history; this plan preserves that history on an archive branch instead of rewriting it away
- `stage/*` branches are the public stage contract; commits are not treated as end-user checkpoints
- `stage/0-minimal` is a host baseline, not a generator-output stage
- `stage/1-bootstrap-in-place` must stay on the in-place path; it must not commit `bootstrap-preview/`

## File Structure

### `cap4k` files to create or modify

- Create: `docs/superpowers/capability-matrix.md`
- Modify: `docs/superpowers/mainline-roadmap.md`

### `only-danmaku-next` files to modify for `stage/0-minimal`

- Modify: `settings.gradle.kts`
- Modify: `build.gradle.kts`
- Modify: `docs/zh-CN/README.md`
- Modify: `docs/en/README.md`
- Create: `docs/zh-CN/stages/README.md`
- Create: `docs/en/stages/README.md`
- Modify: `docs/zh-CN/stages/00-minimal-project.md`
- Modify: `docs/en/stages/00-minimal-project.md`
- Modify: `only-danmaku-domain/build.gradle.kts`
- Modify: `only-danmaku-application/build.gradle.kts`
- Modify: `only-danmaku-adapter/build.gradle.kts`

### `only-danmaku-next` files to delete from `stage/0-minimal`

- Delete: `bootstrap-preview/`
- Delete: `codegen/`
- Delete: `db/`
- Delete: `docs/zh-CN/stages/01-bootstrap-skeleton.md`
- Delete: `docs/zh-CN/stages/02-aggregate-generation.md`
- Delete: `docs/zh-CN/stages/03-design-slice.md`
- Delete: `docs/zh-CN/stages/04-runtime-glue.md`
- Delete: `docs/zh-CN/stages/05-first-feature-port.md`
- Delete: `docs/en/stages/01-bootstrap-skeleton.md`
- Delete: `docs/en/stages/02-aggregate-generation.md`
- Delete: `docs/en/stages/03-design-slice.md`
- Delete: `docs/en/stages/04-runtime-glue.md`
- Delete: `docs/en/stages/05-first-feature-port.md`
- Delete: `only-danmaku-domain/src/main/kotlin/edu/only4/danmaku/domain/_share/meta/`
- Delete: `only-danmaku-domain/src/main/kotlin/edu/only4/danmaku/domain/aggregates/`
- Delete: `only-danmaku-adapter/src/main/kotlin/edu/only4/danmaku/adapter/domain/repositories/VideoPostRepository.kt`

### `only-danmaku-next` files to create or modify for `stage/1-bootstrap-in-place`

- Modify: `build.gradle.kts`
- Modify: `docs/zh-CN/README.md`
- Modify: `docs/en/README.md`
- Modify: `docs/zh-CN/stages/README.md`
- Modify: `docs/en/stages/README.md`
- Create: `docs/zh-CN/stages/01-bootstrap-skeleton.md`
- Create: `docs/en/stages/01-bootstrap-skeleton.md`
- Create: `codegen/bootstrap-slots/root/BOOTSTRAP_NOTE.md.peb`
- Create after generation: `BOOTSTRAP_NOTE.md`
- Modify after generation: `settings.gradle.kts`
- Modify after generation: `build.gradle.kts`
- Modify after generation as bootstrap-owned output: `only-danmaku-domain/build.gradle.kts`
- Modify after generation as bootstrap-owned output: `only-danmaku-application/build.gradle.kts`
- Modify after generation as bootstrap-owned output: `only-danmaku-adapter/build.gradle.kts`

## Task 1: Establish the Capability Truth Source in `cap4k`

**Files:**
- Create: `C:\Users\LD_moxeii\Documents\code\only-workspace\cap4k\docs\superpowers\capability-matrix.md`
- Modify: `C:\Users\LD_moxeii\Documents\code\only-workspace\cap4k\docs\superpowers\mainline-roadmap.md`

- [ ] **Step 1: Write the first capability matrix baseline**

Create `C:\Users\LD_moxeii\Documents\code\only-workspace\cap4k\docs\superpowers\capability-matrix.md`:

```md
# Cap4k Capability Matrix

## Purpose

This document is the current human-readable truth source for Cap4k capability support.

It records:

- what the framework currently claims to support
- which verification layers prove that support
- whether a capability must also appear in `only-danmaku-next`
- which gaps remain explicitly open

This document is not:

- a roadmap
- a future-wishlist
- a restatement of chat history

## Field Contract

| Field | Meaning |
| --- | --- |
| `capabilityId` | Stable identifier used by specs, plans, and verification docs |
| `family` | Capability family such as `bootstrap` or `aggregate` |
| `status` | One of `implemented`, `partial`, `deferred`, `blocked` |
| `contract` | Current supported boundary, not desired future behavior |
| `verificationLayers` | Current proof layers: `unit`, `compile`, `functional`, `runtime`, `project` |
| `verificationTargets` | Concrete tests, fixtures, or project stages that provide the proof |
| `projectRequired` | Whether `only-danmaku-next` must eventually verify this capability |
| `notesOrGaps` | Current caveats, deferred edges, or missing layers |

## Current Matrix

| capabilityId | family | status | contract | verificationLayers | verificationTargets | projectRequired | notesOrGaps |
| --- | --- | --- | --- | --- | --- | --- | --- |
| `bootstrap.in_place_root` | `bootstrap` | `implemented` | Default bootstrap mode is in-place self-hosting root rewrite against a recognized host root with bounded managed-section ownership. | `unit`, `functional`, `project` | `BootstrapManagedSectionMergerTest`; `BootstrapRootStateGuardTest`; `PipelinePluginBootstrapInPlaceFunctionalTest`; `only-danmaku-next: stage/1-bootstrap-in-place` | `yes` | Truly empty-directory launch is still out of scope. |
| `bootstrap.preview_subtree` | `bootstrap` | `implemented` | Preview mode renders a separate subtree only when explicitly requested with `mode = PREVIEW_SUBTREE` and `previewDir`. | `unit`, `functional` | `Cap4kBootstrapConfigFactoryTest`; `PipelinePluginBootstrapPreviewFunctionalTest`; `PipelinePluginBootstrapGeneratedProjectFunctionalTest` | `no` | First-round verification-project state stays on the in-place path. |
| `aggregate.minimal_baseline` | `aggregate` | `implemented` | DB-backed aggregate generation can emit bounded aggregate and schema-meta outputs for selected tables. | `functional`, `compile` | `PipelinePluginFunctionalTest`; `PipelinePluginCompileFunctionalTest`; aggregate compile fixtures | `yes` | Not yet materialized in the first-round verification-project stages. |
| `aggregate.factory_specification` | `aggregate` | `implemented` | Aggregate factory and specification outputs are supported as bounded optional surfaces under the new pipeline. | `unit`, `functional`, `compile` | `AggregateArtifactPlannerTest`; aggregate functional fixtures; aggregate compile fixtures | `yes` | Verification-project stage not started yet. |
| `aggregate.wrapper` | `aggregate` | `implemented` | Wrapper output is available as a bounded aggregate-side optional surface. | `unit`, `functional`, `compile` | aggregate planner tests; aggregate functional fixtures; aggregate compile fixtures | `yes` | Verification-project stage not started yet. |
| `aggregate.unique_bundle` | `aggregate` | `implemented` | `unique-query`, `unique-query-handler`, and `unique-validator` are treated as one lifecycle-coupled capability bundle. | `unit`, `functional`, `compile` | aggregate planner tests; aggregate functional fixtures; aggregate compile fixtures | `yes` | Should enter the verification project as one stage, not three unrelated stages. |
| `aggregate.enum_translation` | `aggregate` | `implemented` | Domain enum and translation output support bounded aggregate ownership and explicit aggregate-side translation generation. | `unit`, `functional`, `compile` | aggregate planner tests; aggregate functional fixtures; aggregate compile fixtures | `yes` | Shared-domain enum DSL is still a separate design discussion. |
| `aggregate.persistence_controls` | `aggregate` | `implemented` | Aggregate persistence controls cover bounded field-behavior, provider-specific entity behavior, and custom generator output within the accepted contract. | `unit`, `functional`, `compile` | aggregate planner tests; aggregate functional fixtures; aggregate compile fixtures | `yes` | Runtime persistence smoke is the next explicit framework hardening slice. |
| `aggregate.relation_baseline` | `aggregate` | `partial` | Relation support covers bounded one-to-one, many-to-one, and one-to-many semantics plus accepted inverse read-only behavior. | `unit`, `functional`, `compile` | relation planner tests; relation functional fixtures; relation compile fixtures | `yes` | `ManyToMany` and join-table recovery remain deferred. |

## Usage Rules

When a new framework slice lands:

1. update or add the relevant matrix row
2. point to the concrete verification targets
3. decide whether the capability is required in `only-danmaku-next`
4. record the missing layer honestly if project verification has not happened yet
```

- [ ] **Step 2: Point the roadmap at the new matrix and verification-project track**

Add the following section to `C:\Users\LD_moxeii\Documents\code\only-workspace\cap4k\docs\superpowers\mainline-roadmap.md` after `## Current Mainline Contract` and before `## Current Next Mainline Slice`:

```md
## Capability Verification Track

The framework now keeps a separate capability-verification track in addition to the explicit framework mainline slices.

Rules for that track:

- current capability truth lives in [capability-matrix.md](capability-matrix.md)
- `only-danmaku-next` is a capability verification project, not the official beginner tutorial line
- stage branches in `only-danmaku-next` verify selected capability rows from the matrix
- gaps exposed by the verification project must be fed back into `cap4k` capability contracts before they are treated as supported behavior

Traceability:

- [only-danmaku-next capability verification project design](specs/2026-04-22-cap4k-only-danmaku-next-capability-verification-project-design.md)
```

- [ ] **Step 3: Run documentation integrity checks**

Run:

```powershell
git -C C:\Users\LD_moxeii\Documents\code\only-workspace\cap4k diff --check
rg -n "Capability Matrix|only-danmaku-next" C:\Users\LD_moxeii\Documents\code\only-workspace\cap4k\docs\superpowers\capability-matrix.md C:\Users\LD_moxeii\Documents\code\only-workspace\cap4k\docs\superpowers\mainline-roadmap.md
```

Expected:

- `git diff --check` prints nothing
- `rg` prints hits from both files, including the matrix title and the new verification-track section

- [ ] **Step 4: Commit the capability-truth-source baseline**

Run:

```powershell
git -C C:\Users\LD_moxeii\Documents\code\only-workspace\cap4k add docs/superpowers/capability-matrix.md docs/superpowers/mainline-roadmap.md
git -C C:\Users\LD_moxeii\Documents\code\only-workspace\cap4k commit -m "docs(matrix): add capability verification baseline"
```

Expected:

- one docs-only commit in `cap4k`

## Task 2: Cut and Stabilize `only-danmaku-next` `stage/0-minimal`

**Files:**
- Modify: `C:\Users\LD_moxeii\Documents\code\only-workspace\only-danmaku-next\settings.gradle.kts`
- Modify: `C:\Users\LD_moxeii\Documents\code\only-workspace\only-danmaku-next\build.gradle.kts`
- Modify: `C:\Users\LD_moxeii\Documents\code\only-workspace\only-danmaku-next\docs\zh-CN\README.md`
- Modify: `C:\Users\LD_moxeii\Documents\code\only-workspace\only-danmaku-next\docs\en\README.md`
- Create: `C:\Users\LD_moxeii\Documents\code\only-workspace\only-danmaku-next\docs\zh-CN\stages\README.md`
- Create: `C:\Users\LD_moxeii\Documents\code\only-workspace\only-danmaku-next\docs\en\stages\README.md`
- Modify: `C:\Users\LD_moxeii\Documents\code\only-workspace\only-danmaku-next\docs\zh-CN\stages\00-minimal-project.md`
- Modify: `C:\Users\LD_moxeii\Documents\code\only-workspace\only-danmaku-next\docs\en\stages\00-minimal-project.md`
- Modify: `C:\Users\LD_moxeii\Documents\code\only-workspace\only-danmaku-next\only-danmaku-domain\build.gradle.kts`
- Modify: `C:\Users\LD_moxeii\Documents\code\only-workspace\only-danmaku-next\only-danmaku-application\build.gradle.kts`
- Modify: `C:\Users\LD_moxeii\Documents\code\only-workspace\only-danmaku-next\only-danmaku-adapter\build.gradle.kts`
- Delete: all future-stage content listed in `only-danmaku-next files to delete from stage/0-minimal`

- [ ] **Step 1: Preserve the old tag-line state and create the new stage branch**

Run:

```powershell
git -C C:\Users\LD_moxeii\Documents\code\only-workspace\only-danmaku-next branch -m master archive/legacy-stage-tags
git -C C:\Users\LD_moxeii\Documents\code\only-workspace\only-danmaku-next switch -c stage/0-minimal
git -C C:\Users\LD_moxeii\Documents\code\only-workspace\only-danmaku-next branch --list
```

Expected:

- the old history is preserved on `archive/legacy-stage-tags`
- the current working branch is `stage/0-minimal`
- there is no destructive history rewrite

- [ ] **Step 2: Rewrite the stage-0 root files, docs, and module baselines**

Write `C:\Users\LD_moxeii\Documents\code\only-workspace\only-danmaku-next\settings.gradle.kts`:

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

Write `C:\Users\LD_moxeii\Documents\code\only-workspace\only-danmaku-next\build.gradle.kts`:

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
```

Write `C:\Users\LD_moxeii\Documents\code\only-workspace\only-danmaku-next\only-danmaku-domain\build.gradle.kts`:

```kotlin
plugins {
    kotlin("jvm")
}

kotlin {
    jvmToolchain(17)
}
```

Write `C:\Users\LD_moxeii\Documents\code\only-workspace\only-danmaku-next\only-danmaku-application\build.gradle.kts`:

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

Write `C:\Users\LD_moxeii\Documents\code\only-workspace\only-danmaku-next\only-danmaku-adapter\build.gradle.kts`:

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

Write `C:\Users\LD_moxeii\Documents\code\only-workspace\only-danmaku-next\docs\zh-CN\README.md`:

```md
# only-danmaku-next

[English](../en/README.md) | [中文](README.md)

`only-danmaku-next` 是一个 Cap4k 功能验证项目，不是官方低心智负担教程主线。

## 使用方式

1. 切换到某个 `stage/*` 分支。
2. 阅读 `docs/zh-CN/stages/` 中对应的阶段文档。
3. 运行文档列出的命令。
4. 将当前仓库状态与 `cap4k` 的能力矩阵进行对照。

## 真相源

- Cap4k 能力真相源位于 `../../../cap4k/docs/superpowers/capability-matrix.md`
- 当前仓库只验证其中一部分能力，不复制整张矩阵

## 分支地图

| Branch | 状态 | 能力包 | 主要命令 |
| --- | --- | --- | --- |
| `stage/0-minimal` | available | 宿主基线，不验证具体 capabilityId | `./gradlew build` |
| `stage/1-bootstrap-in-place` | planned | `bootstrap.in_place_root` | `./gradlew cap4kBootstrap && ./gradlew build` |
| `main` | planned | 当前最新稳定阶段镜像 | 与对应阶段一致 |
```

Write `C:\Users\LD_moxeii\Documents\code\only-workspace\only-danmaku-next\docs\en\README.md`:

```md
# only-danmaku-next

[English](README.md) | [中文](../zh-CN/README.md)

`only-danmaku-next` is a Cap4k capability verification project, not the official low-cognitive-load tutorial line.

## How To Use This Repository

1. Switch to a `stage/*` branch.
2. Read the matching document in `docs/en/stages/`.
3. Run the listed commands.
4. Compare the repository state against the Cap4k capability matrix.

## Truth Source

- The Cap4k capability truth source lives at `../../../cap4k/docs/superpowers/capability-matrix.md`
- This repository verifies selected capabilities; it does not copy the full matrix

## Branch Map

| Branch | Status | Capability Bundle | Primary Command |
| --- | --- | --- | --- |
| `stage/0-minimal` | available | Host baseline; no concrete `capabilityId` yet | `./gradlew build` |
| `stage/1-bootstrap-in-place` | planned | `bootstrap.in_place_root` | `./gradlew cap4kBootstrap && ./gradlew build` |
| `main` | planned | Mirror of the latest stable completed stage | Same as the mirrored stage |
```

Write `C:\Users\LD_moxeii\Documents\code\only-workspace\only-danmaku-next\docs\zh-CN\stages\README.md`:

```md
# Stages

当前只保留已经真实落地或即将落地的前两个阶段。

| Branch | 文档 | 状态 | 说明 |
| --- | --- | --- | --- |
| `stage/0-minimal` | `00-minimal-project.md` | available | 最小宿主基线 |
| `stage/1-bootstrap-in-place` | `01-bootstrap-skeleton.md` | planned | bootstrap 原地自举基线 |
```

Write `C:\Users\LD_moxeii\Documents\code\only-workspace\only-danmaku-next\docs\en\stages\README.md`:

```md
# Stages

Only the first two real stages are kept in the repository during this first round.

| Branch | Document | Status | Summary |
| --- | --- | --- | --- |
| `stage/0-minimal` | `00-minimal-project.md` | available | Minimal host baseline |
| `stage/1-bootstrap-in-place` | `01-bootstrap-skeleton.md` | planned | Bootstrap in-place baseline |
```

Write `C:\Users\LD_moxeii\Documents\code\only-workspace\only-danmaku-next\docs\zh-CN\stages\00-minimal-project.md`:

```md
# Stage 0: Minimal Host Baseline

## 主能力包

宿主基线。这个阶段不验证具体插件 capability。

## capabilityId

无。它是后续能力阶段的前置状态。

## 起始状态

仓库已经具备根 Gradle 工程和三模块结构。

## 完成后应该看到什么

- 根工程可以执行 `./gradlew build`
- 根工程已经具备解析本地 `../cap4k` 插件的宿主前提
- 还没有任何 `cap4k { ... }` 能力配置
- 还没有 bootstrap、aggregate、design 的生成产物

## 明确不包含

- bootstrap DSL
- `bootstrap-preview/`
- `db/schema.sql`
- aggregate 生成结果
- `stage 1+` 的正文文档
```

Write `C:\Users\LD_moxeii\Documents\code\only-workspace\only-danmaku-next\docs\en\stages\00-minimal-project.md`:

```md
# Stage 0: Minimal Host Baseline

## Capability Bundle

Host baseline only. This stage does not verify a concrete plugin capability yet.

## capabilityId

None. This is a prerequisite state for later capability stages.

## Starting State

The repository already contains the root Gradle host and the three-module layout.

## Repository State After This Stage

- the root project runs `./gradlew build`
- the host already has the prerequisites to resolve the local `../cap4k` plugin
- there is still no `cap4k { ... }` capability configuration
- there are still no bootstrap, aggregate, or design outputs

## Explicitly Not Included

- bootstrap DSL
- `bootstrap-preview/`
- `db/schema.sql`
- aggregate outputs
- stage-1-and-later document bodies
```

- [ ] **Step 3: Remove all future-stage content from the stage-0 branch**

Run:

```powershell
git -C C:\Users\LD_moxeii\Documents\code\only-workspace\only-danmaku-next rm -r --ignore-unmatch bootstrap-preview codegen db
git -C C:\Users\LD_moxeii\Documents\code\only-workspace\only-danmaku-next rm -r --ignore-unmatch docs/zh-CN/stages/01-bootstrap-skeleton.md docs/zh-CN/stages/02-aggregate-generation.md docs/zh-CN/stages/03-design-slice.md docs/zh-CN/stages/04-runtime-glue.md docs/zh-CN/stages/05-first-feature-port.md
git -C C:\Users\LD_moxeii\Documents\code\only-workspace\only-danmaku-next rm -r --ignore-unmatch docs/en/stages/01-bootstrap-skeleton.md docs/en/stages/02-aggregate-generation.md docs/en/stages/03-design-slice.md docs/en/stages/04-runtime-glue.md docs/en/stages/05-first-feature-port.md
git -C C:\Users\LD_moxeii\Documents\code\only-workspace\only-danmaku-next rm -r --ignore-unmatch only-danmaku-domain/src/main/kotlin/edu/only4/danmaku/domain/_share/meta only-danmaku-domain/src/main/kotlin/edu/only4/danmaku/domain/aggregates
git -C C:\Users\LD_moxeii\Documents\code\only-workspace\only-danmaku-next rm --ignore-unmatch only-danmaku-adapter/src/main/kotlin/edu/only4/danmaku/adapter/domain/repositories/VideoPostRepository.kt
```

Expected:

- the branch stops advertising any stage-1-and-later body content
- there is no aggregate output left in the repository state
- there is no `bootstrap-preview/` subtree left in the repository state

- [ ] **Step 4: Verify the stage-0 baseline**

Run:

```powershell
./gradlew build
if (Test-Path .\bootstrap-preview) { throw "bootstrap-preview must not exist on stage/0-minimal" }
if (Test-Path .\db) { throw "db must not exist on stage/0-minimal" }
if (Test-Path .\codegen) { throw "codegen must not exist on stage/0-minimal" }
rg -n "video_post" .\only-danmaku-domain .\only-danmaku-adapter
```

Expected:

- `./gradlew build` is successful
- the three `Test-Path` checks do not throw
- `rg` prints nothing because aggregate-specific output is gone

- [ ] **Step 5: Commit the clean stage-0 baseline**

Run:

```powershell
git -C C:\Users\LD_moxeii\Documents\code\only-workspace\only-danmaku-next add settings.gradle.kts build.gradle.kts docs/zh-CN/README.md docs/en/README.md docs/zh-CN/stages/README.md docs/en/stages/README.md docs/zh-CN/stages/00-minimal-project.md docs/en/stages/00-minimal-project.md only-danmaku-domain/build.gradle.kts only-danmaku-application/build.gradle.kts only-danmaku-adapter/build.gradle.kts
git -C C:\Users\LD_moxeii\Documents\code\only-workspace\only-danmaku-next add -u
git -C C:\Users\LD_moxeii\Documents\code\only-workspace\only-danmaku-next commit -m "docs(verification): reset stage 0 host baseline"
```

Expected:

- `stage/0-minimal` now has one commit that cleanly represents the new host-baseline stage

## Task 3: Materialize `stage/1-bootstrap-in-place` and Align `main`

**Files:**
- Modify: `C:\Users\LD_moxeii\Documents\code\only-workspace\only-danmaku-next\build.gradle.kts`
- Modify: `C:\Users\LD_moxeii\Documents\code\only-workspace\only-danmaku-next\docs\zh-CN\README.md`
- Modify: `C:\Users\LD_moxeii\Documents\code\only-workspace\only-danmaku-next\docs\en\README.md`
- Modify: `C:\Users\LD_moxeii\Documents\code\only-workspace\only-danmaku-next\docs\zh-CN\stages\README.md`
- Modify: `C:\Users\LD_moxeii\Documents\code\only-workspace\only-danmaku-next\docs\en\stages\README.md`
- Create: `C:\Users\LD_moxeii\Documents\code\only-workspace\only-danmaku-next\docs\zh-CN\stages\01-bootstrap-skeleton.md`
- Create: `C:\Users\LD_moxeii\Documents\code\only-workspace\only-danmaku-next\docs\en\stages\01-bootstrap-skeleton.md`
- Create: `C:\Users\LD_moxeii\Documents\code\only-workspace\only-danmaku-next\codegen\bootstrap-slots\root\BOOTSTRAP_NOTE.md.peb`
- Create after generation: `C:\Users\LD_moxeii\Documents\code\only-workspace\only-danmaku-next\BOOTSTRAP_NOTE.md`

- [ ] **Step 1: Create the stage-1 branch**

Run:

```powershell
git -C C:\Users\LD_moxeii\Documents\code\only-workspace\only-danmaku-next switch -c stage/1-bootstrap-in-place
git -C C:\Users\LD_moxeii\Documents\code\only-workspace\only-danmaku-next branch --show-current
```

Expected:

- the current branch is `stage/1-bootstrap-in-place`

- [ ] **Step 2: Add the bootstrap input files and stage-1 verification docs**

Write `C:\Users\LD_moxeii\Documents\code\only-workspace\only-danmaku-next\build.gradle.kts`:

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
        projectName.set("only-danmaku-next")
        basePackage.set("edu.only4.danmaku")
        conflictPolicy.set("OVERWRITE")
        modules {
            domainModuleName.set("only-danmaku-domain")
            applicationModuleName.set("only-danmaku-application")
            adapterModuleName.set("only-danmaku-adapter")
        }
        slots {
            root.from("codegen/bootstrap-slots/root")
        }
    }
}
```

Write `C:\Users\LD_moxeii\Documents\code\only-workspace\only-danmaku-next\codegen\bootstrap-slots\root\BOOTSTRAP_NOTE.md.peb`:

```md
# Bootstrap Note

This file is generated by the bounded `ROOT` bootstrap slot.

- Capability: `bootstrap.in_place_root`
- Source template: `codegen/bootstrap-slots/root/BOOTSTRAP_NOTE.md.peb`
- Purpose: prove that root-level slot output works in the in-place contract without introducing `bootstrap-preview/`
```

Write `C:\Users\LD_moxeii\Documents\code\only-workspace\only-danmaku-next\docs\zh-CN\README.md`:

```md
# only-danmaku-next

[English](../en/README.md) | [中文](README.md)

`only-danmaku-next` 是一个 Cap4k 功能验证项目，不是官方低心智负担教程主线。

## 使用方式

1. 切换到某个 `stage/*` 分支。
2. 阅读 `docs/zh-CN/stages/` 中对应的阶段文档。
3. 运行文档列出的命令。
4. 将当前仓库状态与 `cap4k` 的能力矩阵进行对照。

## 真相源

- Cap4k 能力真相源位于 `../../../cap4k/docs/superpowers/capability-matrix.md`
- 当前 `main` 对齐到最新稳定阶段

## 分支地图

| Branch | 状态 | 能力包 | 主要命令 |
| --- | --- | --- | --- |
| `stage/0-minimal` | available | 宿主基线，不验证具体 capabilityId | `./gradlew build` |
| `stage/1-bootstrap-in-place` | available | `bootstrap.in_place_root` | `./gradlew cap4kBootstrap && ./gradlew build` |
| `main` | available | 当前最新稳定阶段镜像，当前等于 `stage/1-bootstrap-in-place` | 与 `stage/1-bootstrap-in-place` 一致 |
```

Write `C:\Users\LD_moxeii\Documents\code\only-workspace\only-danmaku-next\docs\en\README.md`:

```md
# only-danmaku-next

[English](README.md) | [中文](../zh-CN/README.md)

`only-danmaku-next` is a Cap4k capability verification project, not the official low-cognitive-load tutorial line.

## How To Use This Repository

1. Switch to a `stage/*` branch.
2. Read the matching document in `docs/en/stages/`.
3. Run the listed commands.
4. Compare the repository state against the Cap4k capability matrix.

## Truth Source

- The Cap4k capability truth source lives at `../../../cap4k/docs/superpowers/capability-matrix.md`
- `main` mirrors the latest stable completed stage

## Branch Map

| Branch | Status | Capability Bundle | Primary Command |
| --- | --- | --- | --- |
| `stage/0-minimal` | available | Host baseline; no concrete `capabilityId` yet | `./gradlew build` |
| `stage/1-bootstrap-in-place` | available | `bootstrap.in_place_root` | `./gradlew cap4kBootstrap && ./gradlew build` |
| `main` | available | Mirror of the latest stable completed stage, currently `stage/1-bootstrap-in-place` | Same as `stage/1-bootstrap-in-place` |
```

Write `C:\Users\LD_moxeii\Documents\code\only-workspace\only-danmaku-next\docs\zh-CN\stages\README.md`:

```md
# Stages

当前仓库只保留已经真实落地的前两个阶段。

| Branch | 文档 | 状态 | 说明 |
| --- | --- | --- | --- |
| `stage/0-minimal` | `00-minimal-project.md` | available | 最小宿主基线 |
| `stage/1-bootstrap-in-place` | `01-bootstrap-skeleton.md` | available | bootstrap 原地自举基线 |
```

Write `C:\Users\LD_moxeii\Documents\code\only-workspace\only-danmaku-next\docs\en\stages\README.md`:

```md
# Stages

This repository currently keeps only the first two real stages.

| Branch | Document | Status | Summary |
| --- | --- | --- | --- |
| `stage/0-minimal` | `00-minimal-project.md` | available | Minimal host baseline |
| `stage/1-bootstrap-in-place` | `01-bootstrap-skeleton.md` | available | Bootstrap in-place baseline |
```

Write `C:\Users\LD_moxeii\Documents\code\only-workspace\only-danmaku-next\docs\zh-CN\stages\01-bootstrap-skeleton.md`:

```md
# Stage 1: Bootstrap In-Place Baseline

## 主能力包

bootstrap 原地自举基线。

## capabilityId

- `bootstrap.in_place_root`

## 前置状态

从 `stage/0-minimal` 出发。

## 完成后应该看到什么

- 根工程包含 bootstrap DSL
- 执行 `./gradlew cap4kBootstrap` 后，仓库根目录直接承接 bootstrap 结果
- 三个模块继续存在
- 生成出根级 `BOOTSTRAP_NOTE.md`
- `main` 对齐到这个阶段

## 明确不包含

- `bootstrap-preview/`
- aggregate 输入与输出
- `stage 2+` 的正文文档
```

Write `C:\Users\LD_moxeii\Documents\code\only-workspace\only-danmaku-next\docs\en\stages\01-bootstrap-skeleton.md`:

```md
# Stage 1: Bootstrap In-Place Baseline

## Capability Bundle

Bootstrap in-place baseline.

## capabilityId

- `bootstrap.in_place_root`

## Prior State

Start from `stage/0-minimal`.

## Repository State After This Stage

- the root project contains the bootstrap DSL
- after `./gradlew cap4kBootstrap`, the repository root itself carries the bootstrap result
- the three modules still exist
- the root-level `BOOTSTRAP_NOTE.md` is generated
- `main` is aligned to this stage

## Explicitly Not Included

- `bootstrap-preview/`
- aggregate inputs and outputs
- stage-2-and-later document bodies
```

- [ ] **Step 3: Run bootstrap, verify the in-place output, and keep preview absent**

Run:

```powershell
./gradlew cap4kBootstrap
./gradlew build
if (Test-Path .\bootstrap-preview) { throw "bootstrap-preview must not exist on stage/1-bootstrap-in-place" }
if (-not (Test-Path .\BOOTSTRAP_NOTE.md)) { throw "BOOTSTRAP_NOTE.md must exist on stage/1-bootstrap-in-place" }
rg -n "video_post" .\only-danmaku-domain .\only-danmaku-adapter
Get-Content .\BOOTSTRAP_NOTE.md
```

Expected:

- `cap4kBootstrap` succeeds
- `build` succeeds after generation
- `bootstrap-preview` is still absent
- `BOOTSTRAP_NOTE.md` exists at repository root
- `rg` prints nothing because aggregate-specific output is still absent
- `Get-Content` shows the generated root note from the slot template

- [ ] **Step 4: Commit the bootstrap baseline and align `main`**

Run:

```powershell
git -C C:\Users\LD_moxeii\Documents\code\only-workspace\only-danmaku-next add .
git -C C:\Users\LD_moxeii\Documents\code\only-workspace\only-danmaku-next commit -m "feat(verification): add stage 1 bootstrap baseline"
git -C C:\Users\LD_moxeii\Documents\code\only-workspace\only-danmaku-next branch -f main stage/1-bootstrap-in-place
git -C C:\Users\LD_moxeii\Documents\code\only-workspace\only-danmaku-next switch main
git -C C:\Users\LD_moxeii\Documents\code\only-workspace\only-danmaku-next branch --list
```

Expected:

- there is one stage-1 commit on `stage/1-bootstrap-in-place`
- `main` exists and points at the same tip as `stage/1-bootstrap-in-place`
- `archive/legacy-stage-tags`, `stage/0-minimal`, `stage/1-bootstrap-in-place`, and `main` all exist

## Self-Review Checklist

- [ ] **Spec coverage:** confirm the plan covers the matrix truth source, the verification-project repositioning, the stage-branch model, the `main` alignment, and the removal of future-stage content.
- [ ] **Placeholder scan:** run the following and make sure it prints nothing:

```powershell
$todo = 'TO' + 'DO'
$tbd = 'TB' + 'D'
Select-String -Path docs/superpowers/plans/2026-04-22-cap4k-only-danmaku-next-capability-verification-baseline.md -Pattern $todo, $tbd
```
- [ ] **Type consistency:** verify that the same branch names, capability IDs, and file paths are used consistently throughout the plan.

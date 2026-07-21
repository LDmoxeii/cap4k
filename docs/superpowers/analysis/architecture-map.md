# Architecture Map

## Purpose

本页是 cap4k 当前仓库结构和职责边界的维护地图，面向维护 agent 和人类维护者。它用于快速判断一个变更应该落在哪组模块、哪些事实需要从代码重新验证，以及哪些说明不应混入公开文档。

本页不讲解 DDD 使用方法，也不作为用户教程。面向使用者的概念解释、入门叙事和公开 API 说明属于后续 Phase 2 public docs。

## Current Facts

- Gradle root project name 是 `cap4k`，当前 `settings.gradle.kts` 显式 include 了运行时、pipeline、code analysis、starter/console 等子项目。
- 运行时 tactical framework 行为主要在 `ddd-*` 模块中维护，包括 `ddd-core`、`ddd-domain-*`、`ddd-application-*`、`ddd-distributed-*`、`ddd-integration-*`。这些模块承载运行期 DDD tactical behavior、repository、event、request、saga、locker、snowflake、integration adapter 等能力。
- `cap4k-ddd-starter` 和 `cap4k-ddd-console` 是运行时装配/启动侧模块。`cap4k-ddd-starter` 通过 `cap4k-ddd-starter/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 导入 Spring Boot autoconfiguration entries。
- `cap4k-plugin-pipeline-*` 是 compile-time generation pipeline 相关模块。它们覆盖 API、core runner、bootstrap、renderer、source providers、generators 和 Gradle plugin，不属于运行时 tactical framework。
- `cap4k-plugin-code-analysis-*` 是 code analysis 相关模块。当前目录包括 `cap4k-plugin-code-analysis-core`、`cap4k-plugin-code-analysis-compiler`、`cap4k-plugin-code-analysis-flow-export`，用于分析/导出代码结构事实，不是业务运行时模块。
- generated output 是 pipeline 运行后的产物，不等同于 pipeline 源码，也不等同于 handwritten runtime framework。判断文件所有权时要回到 `plan.json`、`generatorId`、`templateId`、`outputKind`、`resolvedOutputRoot`、`conflictPolicy` 等生成计划字段。
- 文档和技能维护位于 `docs/` 与 `skills/`。本目录 `docs/superpowers/analysis/` 是 analysis/fact index，不是 public docs；`skills/` 是 agent routing/working rules，不是用户手册。
- examples/tests 目前主要以各模块 `src/test/kotlin` 分布存在，仓库根目录扫描没有发现独立 `example`/`examples` 子项目。测试覆盖 runtime、starter、pipeline、code analysis、buildSrc 等模块。
- 当前根目录没有 `build.gradle.kts` 文件；根级 Gradle 入口以 `settings.gradle.kts` 和 `buildSrc/` convention 为主要可见锚点。若旧文档引用 root `build.gradle.kts`，需要先从当前文件树验证。

## Source Anchors

- `settings.gradle.kts`: 子项目 include 列表和 root project name。
- `cap4k-ddd-starter/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`: starter autoconfiguration import list。
- `cap4k-plugin-pipeline-gradle/build.gradle.kts`: Gradle plugin module，包含 `java-gradle-plugin` 和 plugin id `io.github.ldmoxeii.cap4k.pipeline`。
- Representative module roots from `Get-ChildItem -Directory -Filter 'cap4k-*'`:
  - `cap4k-ddd-console`
  - `cap4k-ddd-starter`
  - `cap4k-plugin-code-analysis-compiler`
  - `cap4k-plugin-code-analysis-core`
  - `cap4k-plugin-code-analysis-flow-export`
  - `cap4k-plugin-pipeline-api`
  - `cap4k-plugin-pipeline-bootstrap`
  - `cap4k-plugin-pipeline-core`
  - `cap4k-plugin-pipeline-generator-aggregate`
  - `cap4k-plugin-pipeline-generator-design`
  - `cap4k-plugin-pipeline-generator-drawing-board`
  - `cap4k-plugin-pipeline-generator-flow`
  - `cap4k-plugin-pipeline-generator-types`
  - `cap4k-plugin-pipeline-gradle`
  - `cap4k-plugin-pipeline-renderer-api`
  - `cap4k-plugin-pipeline-renderer-pebble`
  - `cap4k-plugin-pipeline-source-db`
  - `cap4k-plugin-pipeline-source-design-json`
  - `cap4k-plugin-pipeline-source-enum-manifest`
  - `cap4k-plugin-pipeline-source-ir-analysis`
  - `cap4k-plugin-pipeline-source-value-object-manifest`
- Representative runtime roots visible from root directory scan:
  - `ddd-core`
  - `ddd-application-request-jpa`
  - `ddd-distributed-locker-jdbc`
  - `ddd-distributed-saga-jpa`
  - `ddd-distributed-snowflake`
  - `ddd-domain-event-jpa`
  - `ddd-domain-repo-jpa`
  - `ddd-domain-repo-jpa-querydsl`
  - `ddd-integration-event-http`
  - `ddd-integration-event-http-jpa`
  - `ddd-integration-event-rabbitmq`
  - `ddd-integration-event-rocketmq`

## Contracts

- Code wins over this map. If this page disagrees with Kotlin source, Gradle files, Spring resource files, or tests, update this page after verifying the code.
- Runtime tactical framework behavior belongs to `ddd-*`, `cap4k-ddd-starter`, and `cap4k-ddd-console`; compile-time generation behavior belongs to `cap4k-plugin-pipeline-*`; analysis extraction/export behavior belongs to `cap4k-plugin-code-analysis-*`.
- `cap4k-ddd-starter` autoconfiguration must be verified from `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`, not inferred from package names.
- Generated output ownership must be verified from pipeline plan/output contracts, not from physical location alone.
- Analysis maps should stay 70% AI-friendly and 30% human-maintainer-friendly: compact facts, stable anchors, explicit drift checks, and minimal narrative.

## Change Impact

- Runtime behavior changes should update the relevant `ddd-*` module tests and any starter autoconfiguration evidence if wiring changes.
- New pipeline source/generator/renderer/bootstrap behavior should update `cap4k-plugin-pipeline-*` maps and generation verification notes before public docs are changed.
- New code analysis output or compiler extraction behavior should update `cap4k-plugin-code-analysis-*` facts and any downstream pipeline analysis source assumptions.
- Moving a module between responsibility groups requires updating `settings.gradle.kts`, this map, and any routing skills that mention the old ownership boundary.
- Public docs should not copy this page verbatim; public docs need user-facing explanation, while this page should remain a maintenance fact index.

## Verification

Run these commands from the cap4k worktree root:

```powershell
Get-ChildItem -Directory -Filter 'cap4k-*' | Select-Object Name
Get-Content cap4k-ddd-starter/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
```

Additional useful checks:

```powershell
Get-Content -Path settings.gradle.kts -Raw
rg -n "java-gradle-plugin|io.github.ldmoxeii.cap4k.pipeline|plugins \{|id\(" cap4k-plugin-pipeline-gradle/build.gradle.kts settings.gradle.kts
rg --files -g "*Test.kt" -g "*Tests.kt" | Select-Object -First 80
```

## Drift Watch

- If new `cap4k-*` roots appear, classify them into runtime starter/console, pipeline, code analysis, docs/skills, or another explicit group.
- If runtime module names move from `ddd-*` to `cap4k-ddd-*`, update this map and `settings.gradle.kts` anchors together.
- If `AutoConfiguration.imports` is replaced by another Spring Boot registration mechanism, update starter facts and verification commands.
- If examples become first-class modules, stop describing examples/tests as test-only distribution and add source anchors for the new example roots.
- If root `build.gradle.kts` is introduced, add it as a source anchor; currently it is not present in this worktree.

## Not Covered

- DDD concept teaching, aggregate modeling guidance, and user-facing tutorials.
- Full public documentation IA for Phase 2.
- Exhaustive per-class runtime API inventory.
- Generated project authoring rules beyond ownership boundaries.
- Release, publishing, Maven Central, and versioning policy.
# Cap4k Legacy Codegen Module Removal Design

Date: 2026-05-07

Status: Proposed

Scope: remove the legacy `cap4k-plugin-codegen` module from the active Gradle build and in-repo supported paths, while keeping the cleanup narrowly focused on direct build wiring, active guidance, and the minimum compatibility needed for current pipeline-era analysis flow export.

Out of scope: removing `cap4k-plugin-codegen-ksp`, redesigning the new pipeline contract, broad repository modernization, wrapper cleanup, historical archive rewrites, or new compatibility work for the deleted legacy generator path.

## Background

`cap4k` has already moved its mainline generator direction to the pipeline plugin family. The repository guidance reflects that shift:

- the pipeline plugin family is the supported mainline path
- `AGENTS.md` already labels `cap4k-plugin-codegen` as legacy and unmaintained
- issue `#24` explicitly asks to remove the legacy module from the active repository/build rather than keeping it as a live supported path

The remaining problem is that `cap4k-plugin-codegen` is still included in `settings.gradle.kts`, still participates in the Gradle graph, and still leaks one direct build dependency into `cap4k-plugin-code-analysis-flow-export`.

## Problem Statement

Keeping `cap4k-plugin-codegen` as an active included module creates three kinds of ongoing cost:

1. active build graph cost:
   - Gradle still resolves, compiles, and tests the deleted direction
2. active repository positioning cost:
   - the module still looks like a supported in-repo path instead of an explicitly removed one
3. wiring debt:
   - one analysis-side plugin still imports the legacy `CodegenExtension` and cannot compile if the module disappears

The cleanup needs to remove the module without silently turning into a new contract redesign.

## Current Findings

### Direct Module Dependencies

The current direct project dependency on `cap4k-plugin-codegen` is:

- `cap4k-plugin-code-analysis-flow-export`

Evidence:

- `cap4k-plugin-code-analysis-flow-export/build.gradle.kts` has `implementation(project(":cap4k-plugin-codegen"))`
- `Cap4kFlowExportPlugin.kt` imports `com.only4.cap4k.plugin.codegen.gradle.extension.CodegenExtension`

No other active module currently declares a direct `project(":cap4k-plugin-codegen")` dependency.

### What `flow-export` Actually Uses

`cap4k-plugin-code-analysis-flow-export` does not use the legacy generator implementation broadly. It only reads legacy project-shape settings from `CodegenExtension`:

- `basePackage`
- `multiModule`
- `moduleNameSuffix4Adapter`
- `moduleNameSuffix4Domain`
- `moduleNameSuffix4Application`

This is important because the fix should remove this narrow coupling, not preserve the whole legacy module.

### Existing Pipeline-Era Equivalent Information

The pipeline plugin already carries the supported project-shape information in active code:

- `cap4k.project.basePackage`
- `cap4k.project.applicationModulePath`
- `cap4k.project.domainModulePath`
- `cap4k.project.adapterModulePath`
- `ProjectConfig.layout`

This means `flow-export` does not need a new legacy compatibility shell. It can instead read the pipeline extension/config first, which is the current mainline path.

### Active Documentation Footprint

Current high-signal active documentation that still names `cap4k-plugin-codegen` as a live in-repo path is:

- `AGENTS.md`

By contrast:

- current public README and `docs/public/**` do not present `cap4k-plugin-codegen` as an active supported path
- many historical specs/plans still mention it, but those are archive/history references and should not be swept in this issue unless they would become actively misleading

## Goals

1. Remove `cap4k-plugin-codegen` from the active included-module build.
2. Keep the repository build graph valid after removal.
3. Rewire `cap4k-plugin-code-analysis-flow-export` with the minimum non-legacy logic needed to keep it compiling and usable.
4. Update active guidance so the repository no longer presents `cap4k-plugin-codegen` as an in-repo supported path.
5. Leave larger contract or migration questions to explicit follow-up issues.

## Non-Goals

This issue must not:

- remove `cap4k-plugin-codegen-ksp`
- move old generator internals into new modules
- introduce a new shared pipeline/analysis configuration contract beyond what current pipeline code already provides
- rewrite historical specs/plans just because they mention the old module
- add new support for old `codegen { ... }` usage

## Options Considered

### Option 1: Remove the module and make `flow-export` read pipeline project config directly

This option:

- deletes `cap4k-plugin-codegen`
- removes its `settings.gradle.kts` include
- removes the direct project dependency from `cap4k-plugin-code-analysis-flow-export`
- updates `flow-export` to infer module input roots and label prefixes from the current pipeline-era `cap4k { project { ... } }` extension first
- keeps a non-legacy fallback only for cases where the pipeline extension is absent

Pros:

- aligns with the actual supported path
- removes the live legacy module cleanly
- avoids preserving the old extension as a pseudo-contract

Cons:

- requires a small internal refactor inside `flow-export`

### Option 2: Keep a thin legacy shell with only `CodegenExtension`

This option would remove most of `cap4k-plugin-codegen` but retain a tiny module only to host `CodegenExtension`.

Pros:

- smallest short-term code change in `flow-export`

Cons:

- fails the issue intent because the legacy module still remains active
- preserves an obsolete contract instead of removing it
- creates future ambiguity about whether the old generator path is still supported

### Option 3: Introduce a brand-new shared contract module for flow/export project layout

This option would define a new formal contract shared by pipeline and analysis plugins.

Pros:

- could become architecturally cleaner in the long term

Cons:

- expands this cleanup into contract design work
- violates the issue boundary against rewriting the new pipeline contract

## Recommended Design

Adopt Option 1.

The cleanup should remove `cap4k-plugin-codegen` entirely and make `cap4k-plugin-code-analysis-flow-export` consume the already-supported pipeline project shape instead of the legacy `CodegenExtension`.

## Build Wiring Changes

### 1. Remove the legacy module from the included build

Update `settings.gradle.kts` to remove:

- `include("cap4k-plugin-codegen")`

Then delete the repository directory:

- `cap4k-plugin-codegen/**`

### 2. Remove the direct Gradle dependency from `flow-export`

Update `cap4k-plugin-code-analysis-flow-export/build.gradle.kts` to remove:

- `implementation(project(":cap4k-plugin-codegen"))`

No replacement project dependency should be added unless compilation proves one is strictly required.

### 3. Rewire `flow-export` to the active pipeline project shape

Update `Cap4kFlowExportPlugin.kt` so that:

- it no longer imports `CodegenExtension`
- it first checks whether the root project exposes the pipeline `cap4k` extension
- when the pipeline extension is present, it reads:
  - `project.basePackage`
  - `project.applicationModulePath`
  - `project.domainModulePath`
  - `project.adapterModulePath`
- it uses those explicit module paths to resolve module projects for compile/clean dependencies and default IR input dirs

### 4. Keep only a minimal non-legacy fallback

If the pipeline extension is absent, `flow-export` may fall back to behavior that does not depend on legacy codegen classes, for example:

- use explicitly configured `cap4kFlow.inputDirs` if present
- otherwise fall back to root-project analysis output discovery similar to the current last-resort behavior

The fallback must not preserve the old `multiModule` + suffix contract under a new name. If users still need that path, that becomes follow-up work, not hidden compatibility inside this deletion.

## Documentation Changes

### Active docs to update

Update `AGENTS.md` so it no longer describes `cap4k-plugin-codegen` as a legacy module that still exists in-repo. The new guidance should say, in effect:

- the old monolithic generator module has been removed
- do not reintroduce it
- mainline generator work belongs to the pipeline plugin family

### Docs explicitly out of scope for this issue

Do not sweep historical specs/plans that reference `cap4k-plugin-codegen` as past implementation context. Those references are archival unless they continue to instruct active implementation against the deleted module.

## Verification Strategy

The cleanup must provide evidence for two levels:

### Root build still resolves

Run a root-level Gradle command that forces settings/module graph resolution after the delete, such as:

- `.\gradlew.bat help --no-daemon`

This proves the removed module is no longer required by the active included build.

### Representative affected targets still work

Run representative targets centered on the impacted wiring:

- `.\gradlew.bat :cap4k-plugin-code-analysis-flow-export:compileKotlin --no-daemon`
- `.\gradlew.bat :cap4k-plugin-pipeline-gradle:test --tests "com.only4.cap4k.plugin.pipeline.gradle.Cap4kProjectConfigFactoryTest" --no-daemon`

This pair gives focused evidence that:

- the analysis-side plugin still compiles after the legacy dependency is removed
- the pipeline-side project-shape contract used by the cleanup still behaves as expected

## Follow-up Issues That Must Stay Separate

If needed, these should become separate follow-up issues rather than being folded into this cleanup:

1. removing `cap4k-plugin-codegen-ksp`
2. designing a first-class shared contract between pipeline project config and analysis/flow export
3. sweeping archived historical docs/specs/plans for old `cap4k-plugin-codegen` references
4. broader repository legacy cleanup unrelated to this module removal

## Acceptance Mapping

This design satisfies issue `#24` if implementation shows that:

- `cap4k-plugin-codegen` is no longer included in `settings.gradle.kts`
- `cap4k-plugin-codegen/**` is gone from the active repo
- `cap4k-plugin-code-analysis-flow-export` no longer depends on legacy codegen classes
- active guidance no longer presents `cap4k-plugin-codegen` as an in-repo supported path
- root build resolution and representative affected targets pass after removal

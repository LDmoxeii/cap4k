# cap4k Bootstrap, Plugin, And Template Map

Date: 2026-05-11

This file maps the Gradle plugin and template surfaces that a business project author must understand.

## Gradle Tasks

| Task | Purpose | Output / effect |
|---|---|---|
| `cap4kBootstrapPlan` | Preview bootstrap skeleton | `build/cap4k/bootstrap-plan.json` |
| `cap4kBootstrap` | Write bootstrap skeleton | Root/module files, package slots, resource slots |
| `cap4kPlan` | Preview checked-in source generation | `build/cap4k/plan.json` |
| `cap4kGenerate` | Write checked-in generated skeletons | Files under configured project source roots |
| `cap4kGenerateSources` | Generate build-owned Kotlin sources | Module `build/generated/cap4k/main/kotlin`, wired into compile/KSP |
| `cap4kAnalysisPlan` | Preview analysis artifacts | `build/cap4k/analysis-plan.json` |
| `cap4kAnalysisGenerate` | Write analysis artifacts | Flow/drawing-board files under configured output roots |

`cap4kPlan`/`cap4kGenerate` can depend on `kspKotlin` when design generators need aggregate metadata. `cap4kAnalysisPlan`/`cap4kAnalysisGenerate` infer `compileKotlin` dependencies from `sources.irAnalysis.inputDirs`.

## Project And Layout DSL

`project` config defines:

- `basePackage`
- `domainModulePath`
- `applicationModulePath`
- `adapterModulePath`

Default layout:

| Artifact family | Default package / root |
|---|---|
| aggregate entity / behavior / factory / specification | `domain.aggregates` |
| aggregate schema | `domain._share.meta` |
| aggregate repository | `adapter.domain.repositories` |
| shared aggregate enum | `domain.<shared>` under configured suffix |
| aggregate unique query | `application.queries.<unique>` |
| aggregate unique query handler | `adapter.queries.<unique>` |
| aggregate unique validator | `application.validators.<unique>` |
| design command | `application.commands` |
| design query | `application.queries` |
| design client | `application.distributed.clients` |
| design query handler | `adapter.application.queries` |
| design client handler | `adapter.application.distributed.clients` |
| design validator | `application.validators` |
| design api payload | `adapter.portal.api.payload` |
| design domain event | `domain.aggregates.<events>` |
| design domain event handler | `application.subscribers.domain` |
| flow output | `flows` |
| drawing board output | `design` |

## Bootstrap Scope

Bootstrap is a narrow project skeleton tool. It is not a business source generator and not a legacy migration tool.

Current preset:

- `ddd-multi-module`
- domain/application/adapter/start modules
- root `settings.gradle.kts`
- root `build.gradle.kts`
- module `build.gradle.kts`
- start module `StartApplication.kt`
- slots for user extension

Bootstrap modes:

| Mode | Behavior |
|---|---|
| `IN_PLACE` | Writes into the current project; root must already be a recognized managed host with `settings.gradle.kts` and `build.gradle.kts` |
| `PREVIEW_SUBTREE` | Writes under preview dir; bypasses managed-root validation |

## Bootstrap Slots

| Slot kind | Typical output root |
|---|---|
| `root` | project root |
| `buildLogic` | `build-logic/` |
| `moduleRoot(role)` | module root |
| `modulePackage(role)` | module `src/main/kotlin/<role package root>/...` |
| `moduleResources(role)` | module `src/main/resources/` |

Slots are the intended way to add project-specific starter files without replacing core bootstrap templates.

## Template Resolution

For pipeline generation, template resolution order is:

1. absolute `templateId` file if given;
2. project `templates.overrideDirs`;
3. addon resource when `templateId` starts with `addons/<id>/...`;
4. built-in preset resource under `presets/<preset>/<templateId>`.

This means project overrides can replace addon templates by providing the same relative path, for example `addons/only-engine-enum-translation/...`.

Bootstrap has its own template config:

- `bootstrap.templates.preset`
- `bootstrap.templates.overrideDirs`
- bootstrap conflict policy defaults to `FAIL`

Pipeline source-generation template config:

- `templates.preset`
- `templates.overrideDirs`
- global `templates.conflictPolicy`
- per-template `templates.templateConflictPolicies`

## Conflict Policies

| Policy | Meaning |
|---|---|
| `SKIP` | Preserve existing checked-in code |
| `OVERWRITE` | Replace existing generated output |
| `FAIL` | Stop if target exists |

Generated source output uses `OVERWRITE` because it is build-owned. Checked-in author skeletons should normally use `SKIP`, especially behavior, factory, specification, and handlers that become user-maintained.

Per-template policies are keyed by normalized `templateId`. Addon and built-in artifacts go through the same normalization path, so a project author should not need a different mental model for addon-generated files.

## Addon Classpath

The Gradle plugin creates a resolvable `cap4kAddon` configuration. Addon jars are loaded with `ServiceLoader` and can contribute artifact plans after the canonical model is assembled.

Business-project authoring should teach:

- add addon dependency to `cap4kAddon`;
- configure the addon using its documented Gradle extension or project config if provided;
- override addon templates through the normal `templates.overrideDirs`;
- set addon template conflict policies through the same `templates.templateConflictPolicies`.

## Teaching Boundary

The authoring path should show a minimal runnable project first, then explain how each generated surface can be safely customized. It should not ask the user to understand plugin internals unless they are authoring an addon.

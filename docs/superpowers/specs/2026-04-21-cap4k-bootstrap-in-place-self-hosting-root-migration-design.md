# Cap4k Bootstrap In-Place Self-Hosting Root Migration

Date: 2026-04-21
Status: Draft for review

## Summary

`cap4k` bootstrap currently defaults to generating a project skeleton under a `<projectName>/` subtree.
This avoided overwriting the invoking Gradle project, but it also regressed an important old-system property:
after bootstrap, the generated root project no longer acts as the ongoing Cap4k host.

For real projects, that is the wrong default.
It creates an unnecessary extra directory layer, splits the mental model between "the project that runs bootstrap" and "the project produced by bootstrap", and makes bootstrap feel like a preview generator rather than the actual project starting point.

This slice changes the official bootstrap default to:

- `in-place`
- `self-hosting`
- repeatable through bounded managed sections

The current subtree output model is retained only as an explicit preview/tutorial mode.

## Goals

- Change the official bootstrap default from subtree output to in-place output.
- Ensure the generated root project remains a Cap4k host after bootstrap completes.
- Decouple `projectName` from filesystem output prefix behavior.
- Introduce bounded managed sections for root Gradle files so bootstrap can be rerun without whole-file takeover.
- Preserve an explicit preview/tutorial mode for side-by-side generated skeleton inspection.
- Prove the new contract with provider, planner, functional, and task-level verification.

## Non-Goals

- Do not turn bootstrap into a generic Gradle-project merger.
- Do not revive old `archTemplate` JSON as a public runtime DSL.
- Do not implement a framework-wide generic file merge engine.
- Do not broaden bootstrap to auto-take over arbitrary existing Gradle roots.
- Do not mix aggregate parity gaps, schema parity, or unrelated generator fixes into this slice.
- Do not remove preview/subtree output entirely; it remains a supported explicit mode.

## Problem Statement

The current bootstrap contract has two design flaws for real-project usage.

### 1. `projectName` carries two unrelated responsibilities

Today it is used both as:

- the generated `rootProject.name`
- the outer filesystem output prefix

That coupling is what produces the extra nested structure:

- invoking project root
- generated `<projectName>/`
- generated modules under that subtree

These are different concerns and should not be modeled as one field.

### 2. The generated root is consumer-only, not self-hosting

The current bootstrap root templates generate a plain multi-module consumer project.
They do not preserve Cap4k host responsibilities in the generated root.

That means the current implementation can avoid self-overwrite only by generating into a separate subtree.
This is a valid temporary safety mechanism, but it is not a suitable long-term default for real projects.

## Why This Is Not An Optional UX Polish

This is a contract problem, not a naming problem.

The old system proved a more mature behavior:

- a minimal host could run initial generation
- the generated root became the ongoing codegen host
- later runs continued against the generated root itself

The new pipeline does not currently preserve that property.
If `cap4k` wants bootstrap to be the real project starting point rather than a preview helper, it must restore that behavior under the new bounded-contract architecture.

## Design Decision

The official bootstrap default should become:

- `BootstrapMode.IN_PLACE`
- self-hosting generated root
- bounded managed sections in root Gradle files

The current subtree model should remain supported only as:

- `BootstrapMode.PREVIEW_SUBTREE`

This design intentionally does **not** solve the problem by simply deleting the output path prefix.
That would recreate self-overwrite risk without restoring the host responsibilities that made the old model workable.

## Public Contract

Recommended public surface:

```kotlin
cap4k {
    bootstrap {
        enabled.set(true)
        preset.set("ddd-multi-module")

        mode.set(BootstrapMode.IN_PLACE)
        projectName.set("only-danmaku")
        basePackage.set("edu.only4.danmaku")

        previewDir.set("bootstrap-preview")

        modules {
            domainModuleName.set("only-danmaku-domain")
            applicationModuleName.set("only-danmaku-application")
            adapterModuleName.set("only-danmaku-adapter")
        }

        templates {
            preset.set("ddd-default-bootstrap")
            overrideDirs.from("codegen/bootstrap-templates")
        }

        slots {
            root.from("codegen/bootstrap-slots/root")
            buildLogic.from("codegen/bootstrap-slots/build-logic")
            moduleRoot("domain").from("codegen/bootstrap-slots/domain-root")
            moduleRoot("application").from("codegen/bootstrap-slots/application-root")
            moduleRoot("adapter").from("codegen/bootstrap-slots/adapter-root")
            modulePackage("domain").from("codegen/bootstrap-slots/domain-package")
            modulePackage("application").from("codegen/bootstrap-slots/application-package")
            modulePackage("adapter").from("codegen/bootstrap-slots/adapter-package")
        }
    }
}
```

## Contract Semantics

### `projectName`

`projectName` must only control generated project identity, especially:

- `rootProject.name`

It must no longer imply or require an outer output directory prefix.

Example:

- `mode = IN_PLACE`
- `projectName = "only-danmaku"`

should generate:

- `./settings.gradle.kts`
- `./build.gradle.kts`
- `./only-danmaku-domain/...`

with:

```kotlin
rootProject.name = "only-danmaku"
```

not:

- `./only-danmaku/...`

Example preview mode:

- `mode = PREVIEW_SUBTREE`
- `previewDir = "bootstrap-preview"`
- `projectName = "only-danmaku"`

should generate under:

- `./bootstrap-preview/...`

while still rendering:

```kotlin
rootProject.name = "only-danmaku"
```

## Root Ownership Model

This slice should not copy the old whole-file takeover model directly.
The old system was workable, but it mixed framework-managed host behavior and user-managed build behavior too loosely.

The new contract should instead use:

- self-hosting root files
- bounded managed sections
- explicit user-managed areas outside those sections

### Managed Root Files

The managed-section model applies only to bootstrap-owned root files:

- `settings.gradle.kts`
- `build.gradle.kts`

This slice does **not** introduce a generic framework-wide merge policy for arbitrary generated files.

### Managed Section Markers

Bootstrap-owned sections should be wrapped in explicit markers:

```kotlin
// [cap4k-bootstrap:managed-begin:root-host]
...
// [cap4k-bootstrap:managed-end:root-host]
```

The contract should use these marker forms and require:

- explicit begin marker
- explicit end marker
- unique section ids per file
- deterministic rewrite of marked content only

### `settings.gradle.kts` Responsibility Split

Framework-managed content should include only the minimal required bootstrap-owned settings content:

- `rootProject.name`
- bootstrap-generated fixed-module `include(...)` entries
- minimal plugin-management and dependency-resolution wiring required for the generated project to remain a Cap4k host

User-managed content may include:

- additional `include(...)` entries
- extra repositories
- organization-specific settings logic

### `build.gradle.kts` Responsibility Split

Framework-managed content should include only the minimal required bootstrap-owned root build content:

- Cap4k host plugin application
- minimal shared build conventions required by the preset
- baseline `cap4k { ... }` skeleton needed for continued bootstrap and generation usage

User-managed content may include:

- extra plugins
- organization-specific repositories
- custom dependencies
- custom tasks
- additional build logic outside managed sections

### Rewrite Behavior

When bootstrap reruns against an already managed root:

- only content inside managed sections may be rewritten
- content outside managed sections must remain untouched

If marker structure is invalid, bootstrap must fail rather than guess.

## Execution Modes

The first slice should support exactly two filesystem output modes.

### `IN_PLACE`

This is the official default.

Semantics:

- bootstrap outputs into the current invoking project root
- generated root remains a Cap4k host
- fixed module directories are created under the current root

### `PREVIEW_SUBTREE`

This is an explicit opt-in mode for tutorial, demo, and side-by-side preview scenarios.

Semantics:

- bootstrap outputs into `previewDir/`
- generated preview project should remain self-hosting too, but it is not the default project contract
- this mode exists to support teaching and comparison, not to define real-project default behavior

## Supported Root States

`IN_PLACE` must behave deterministically based on the current root state.

### 1. Empty Or Missing Root Files

If the current root does not yet contain `build.gradle.kts` and `settings.gradle.kts`:

- bootstrap should create them directly
- the created root must be self-hosting from the first successful run

### 2. Minimal Bootstrap Host Root

The framework should define a supported minimal host root shape for first-run bootstrap.

This root is intentionally temporary and exists only to start bootstrap safely.
Bootstrap should upgrade that temporary root into the final self-hosting root if and only if the root is explicitly recognized as a supported Cap4k bootstrap host shape.

This recognition must be marker-based or otherwise explicit.
It must not depend on heuristic inspection of arbitrary user files.

### 3. Already Managed Root

If the current root already contains valid managed bootstrap sections:

- bootstrap reruns are allowed
- managed sections should be updated as needed
- user-managed content outside the sections must be preserved

### 4. Unmanaged Existing Root

If the current root already contains a normal Gradle project without recognized bootstrap markers:

- `IN_PLACE` must fail fast
- bootstrap must not attempt heuristic takeover or best-effort merging

The error message should direct the user to one of two supported paths:

- use `PREVIEW_SUBTREE`
- explicitly migrate the root into a supported managed bootstrap host shape

## Explicitly Rejected Behaviors

This slice should reject the following designs:

- parsing arbitrary existing Gradle files and auto-injecting Cap4k blocks
- silently taking over any existing `build.gradle.kts` that "looks simple enough"
- introducing a generic merge engine for arbitrary generated files
- treating tutorial/preview output as the official real-project default

Those directions appear convenient but would materially weaken long-term stability and debuggability.

## Planner And Output Path Contract

Bootstrap planning must reflect the new semantics directly.

### Output Path Rules

When `mode = IN_PLACE`:

- root outputs must be planned as:
  - `settings.gradle.kts`
  - `build.gradle.kts`
- module outputs must be planned directly under the current root, for example:
  - `only-danmaku-domain/build.gradle.kts`
  - `only-danmaku-application/build.gradle.kts`
  - `only-danmaku-adapter/build.gradle.kts`

When `mode = PREVIEW_SUBTREE`:

- root outputs must be planned under `previewDir/`, for example:
  - `bootstrap-preview/settings.gradle.kts`
  - `bootstrap-preview/build.gradle.kts`
- module outputs must be planned under that subtree

### Render Context Rules

`projectName` remains part of template render context because templates still need it for:

- `rootProject.name`
- module dependency wiring
- user-facing project identity

But `projectName` must no longer be reused as an implicit filesystem prefix when `mode = IN_PLACE`.

### Slot Routing Rules

The existing bounded slot model remains valid.
Only the output root changes based on mode.

That means:

- slot categories remain framework-owned
- slot source directories remain user-configurable
- slot output placement is rebased either to current root or to `previewDir/`

## Failure Strategy

Bootstrap must prefer explicit failure over partial success.

The slice should require fail-fast behavior for at least the following cases:

- `IN_PLACE` mode against an unmanaged existing root
- invalid or missing managed section markers in a previously managed root
- duplicate or overlapping managed section ids
- invalid `mode` and `previewDir` combinations
- fixed module path conflicts against non-managed existing content
- plan/execution mismatches where a generated output escapes the configured root target

The system must not silently downgrade to subtree output when `IN_PLACE` fails.
Mode changes must always be explicit user decisions.

## Migration Boundary

This slice is about correcting the bootstrap root contract.
It should remain tightly scoped to that responsibility.

### Included In Scope

- default bootstrap mode changes from subtree output to in-place output
- generated root becomes self-hosting
- `projectName` is decoupled from output prefix semantics
- preview/subtree output becomes explicit opt-in behavior
- root managed-section support is introduced
- bootstrap provider, config, renderer, planner, and Gradle integration are updated to honor the new contract
- tutorial and sample usage is updated where needed to opt into preview mode explicitly

### Excluded From Scope

- generic merge support for non-root generated files
- automatic migration of arbitrary user Gradle roots
- full old `archTemplate` JSON parity
- unrelated aggregate or design-family parity work
- unrelated template formatting or schema-runtime parity issues

This boundary is important.
Without it, bootstrap root-contract correction would turn into an open-ended parity program.

## Validation Strategy

The slice needs layered verification.

### 1. Provider And Config Unit Tests

Validate:

- default mode is `IN_PLACE`
- `projectName` affects project identity but not `IN_PLACE` output prefix
- `PREVIEW_SUBTREE` requires valid `previewDir`
- invalid mode/config combinations fail with clear diagnostics

### 2. Planner Unit Tests

Validate:

- `IN_PLACE` root outputs plan directly to current root paths
- `PREVIEW_SUBTREE` outputs plan under `previewDir/`
- slot rebasing follows mode correctly
- `projectName` appears in render context but not as an implicit in-place output prefix

### 3. Managed Root Rewrite Tests

Validate:

- rerunning bootstrap updates only marked managed sections
- user content outside managed sections survives reruns
- missing, duplicated, or malformed markers fail deterministically

### 4. Functional TestKit Tests

Validate:

- bootstrap can initialize an empty root into a self-hosting project
- bootstrap can upgrade a recognized minimal host root into the final managed self-hosting root
- bootstrap reruns succeed against an already managed root
- bootstrap fails against an unmanaged existing root in `IN_PLACE`
- bootstrap still succeeds in explicit preview mode

### 5. Task-Level Verification

Validate:

- the in-place generated root can still execute `cap4kBootstrapPlan`
- the in-place generated root can still execute `cap4kBootstrap`
- at least one sample proves that the generated root is not a one-shot skeleton but an ongoing Cap4k host

## Success Criteria

This slice is complete only when all of the following are true:

- official default bootstrap mode is `IN_PLACE`
- `projectName` only controls generated project identity and no longer forces an outer output directory
- generated in-place roots remain Cap4k hosts after bootstrap
- `PREVIEW_SUBTREE` remains supported as an explicit opt-in mode
- rerunning bootstrap updates only managed root sections and preserves user-managed content
- unmanaged existing roots are rejected rather than heuristically merged
- plan and execution tests prove the new output-path contract
- tutorial or staged-preview consumers continue to work by explicitly opting into preview mode

## Recommended Follow-On Work

If this slice lands successfully, the next work should be limited to:

- implementation plan for this contract
- tutorial project adjustments to use explicit preview mode where needed
- optional later evaluation of whether fixed module build files also need managed-section treatment

It should not automatically trigger broader bootstrap parity expansion.

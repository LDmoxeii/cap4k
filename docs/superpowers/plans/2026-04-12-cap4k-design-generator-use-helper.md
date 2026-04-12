# Cap4k Design Generator `use()` Helper Implementation Plan

Date: 2026-04-12
Spec: `docs/superpowers/specs/2026-04-12-cap4k-design-generator-use-helper-design.md`

## Goal

Implement a narrow `use()` helper for pipeline design templates that:

- accepts only explicit FQN string imports
- records explicit imports during a single artifact render
- merges those imports with the existing generator-provided import list
- keeps `type()` and generator-side import resolution unchanged

## Scope

In scope:

- `cap4k-plugin-pipeline-renderer-pebble`
- renderer/helper tests
- design-template-level renderer verification

Out of scope:

- generator-side import logic
- aggregate/flow/drawing-board templates
- legacy runtime resurrection
- short-name or wildcard support

## Implementation Steps

### 1. Add a per-render explicit import collector

Update the Pebble renderer path so each rendered artifact gets an isolated explicit import collector.

Expected implementation shape:

- create a small collector object or mutable set scoped to one render call
- pass it into the Pebble extension/helper registration path
- discard it after rendering completes

Constraints:

- no global mutable registry
- no cross-artifact leakage
- no renderer API expansion outside this module unless strictly needed

### 2. Implement `use()` in the Pebble extension

Extend the renderer Pebble helper layer with a `use()` helper that:

- requires exactly one argument
- requires that argument to be a string
- validates that the string is a legal FQN
- appends it to the current render collector
- returns an empty string

Validation rules:

- reject short names
- reject wildcard imports
- reject malformed FQNs

Failure messages must clearly identify `use()` as the failing helper.

### 3. Merge computed imports with explicit imports

After template execution, merge:

- render-model imports already provided in context
- explicit imports recorded by `use()`

Rules:

- deduplicate identical FQNs
- preserve the existing normalization behavior of `imports()`
- detect simple-name collisions between computed and explicit imports
- fail fast on conflict

This step must not alter `renderedType` output.

### 4. Keep `imports()` as the only import output path

Do not make `use()` emit import lines directly.

The final template contract remains:

- `use()` registers explicit imports
- `imports(imports)` emits the merged normalized list

If the current renderer implementation needs a small internal context update so `imports()` can see explicit imports, do that inside the renderer module without widening the public contract.

### 5. Add renderer tests

Add focused tests for:

- `use("java.time.LocalDateTime")` success
- duplicate `use()` FQNs dedupe correctly
- merge of computed imports with explicit imports
- missing arg failure
- non-string arg failure
- short-name failure
- wildcard failure
- invalid FQN failure
- collision with computed import failure
- collision between explicit imports failure

These should live beside existing `type()` / `imports()` helper tests.

### 6. Add design-template verification

Add one design-template-level renderer test proving:

- `use()` can be placed at template top
- imports still come out through the existing `imports(imports)` loop
- `type(field)` output remains unchanged

Do not convert the default design templates to use `use()` in this slice.

### 7. Verify end-to-end module targets

Run at minimum:

```powershell
./gradlew :cap4k-plugin-pipeline-renderer-pebble:test --rerun-tasks
./gradlew :cap4k-plugin-pipeline-generator-design:test --rerun-tasks
./gradlew :cap4k-plugin-pipeline-gradle:test --rerun-tasks
```

The generator and Gradle modules are included as regression checks even though the main logic stays in the renderer.

## Files Likely To Change

- `cap4k-plugin-pipeline-renderer-pebble/src/main/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PipelinePebbleExtension.kt`
- `cap4k-plugin-pipeline-renderer-pebble/src/main/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PebbleArtifactRenderer.kt`
- `cap4k-plugin-pipeline-renderer-pebble/src/test/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PebbleArtifactRendererTest.kt`

Potentially:

- a small internal helper file in the same renderer module for FQN validation or explicit import collection

## Risks

### 1. Import merging happens at the wrong phase

If explicit imports are merged too early or too late, `imports(imports)` may not see them consistently.

Mitigation:

- keep one clear post-template merge point inside the renderer
- add tests that assert final import output, not just collector contents

### 2. State leakage across artifact renders

If the collector is not truly per-render, imports can bleed between artifacts.

Mitigation:

- instantiate a fresh collector inside each render call
- add a test rendering multiple artifacts with different explicit imports

### 3. Collision handling becomes inconsistent with current import rules

Mitigation:

- route all final import output through the same normalized import path
- fail fast instead of trying to auto-resolve collisions

## Completion Criteria

This slice is complete when:

- `use()` exists and is restricted to explicit FQN string imports
- explicit imports are scoped to a single artifact render
- merged import output is deduplicated and conflict-safe
- `type()` behavior is unchanged
- renderer tests cover success and failure behavior
- regression verification passes for renderer, generator, and Gradle modules

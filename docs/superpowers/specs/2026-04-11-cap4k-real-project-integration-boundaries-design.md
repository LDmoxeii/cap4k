# Cap4k Real Project Integration Boundary Consolidation

## Summary

This design addresses two boundary problems exposed by real integration in `only-danmuku`.

The first problem is aggregate generation failure on unsupported database tables.

The current aggregate minimal slice only supports single-column primary keys. In a real schema, unrelated tables such as archive or infra tables can still block the whole aggregate pipeline if they appear in the DB snapshot.

The second problem is design input ambiguity.

The current `design-json` source accepts an arbitrary file list from Gradle. In practice, that means a consuming project can silently decide "active" design inputs by ad hoc `fileTree` rules. This is too loose for long-term real-project usage.

This slice does not expand aggregate capability to composite primary keys yet. It instead makes the integration boundary explicit, diagnosable, and safe to evolve.

## Why This Slice

The main pipeline architecture is already in place:

- pipeline core
- DB aggregate minimal slice
- IR flow slice
- drawing-board slice
- repository DSL consolidation
- design generator quality upgrades

Real integration has now revealed a more immediate bottleneck:

- the pipeline can run, but project-side boundaries are still too implicit

Two specific examples came out of the `only-danmuku` integration run:

1. aggregate generation failed on a table with unsupported primary-key shape
2. design generation had no stable notion of which design files are truly active

These are not architecture problems anymore. They are integration-boundary problems. If left unresolved, they will keep making real-project adoption fragile even while the internal generator quality improves.

## Goals

- Keep aggregate minimal slice behavior explicit and predictable
- Prevent unrelated unsupported DB tables from blocking intended aggregate generation
- Add diagnostics so users can see which DB tables were included, excluded, skipped, or unsupported
- Introduce a stable source of truth for active design input files
- Stop relying on ad hoc Gradle `fileTree` rules as the long-term definition of active design specs
- Preserve current pipeline stage ordering and ownership boundaries

## Non-Goals

- Implement composite-primary-key aggregate generation
- Introduce `@EmbeddedId` or composite repository identity support
- Redesign aggregate templates for multi-key entities
- Change the design generator runtime or template helper layer
- Move design spec discovery into pipeline core
- Replace Gradle DSL with a separate external configuration system

## Scope Decision

Three approaches were considered.

1. expand aggregate generation to support composite primary keys immediately, and keep design input discovery flexible
2. keep aggregate capability unchanged, but make unsupported-table behavior configurable and design-input truth explicit
3. require consuming projects to manually pre-filter both DB tables and design files outside the pipeline

The recommended choice is option 2.

Reasons:

- it solves the real integration bottlenecks with bounded scope
- it does not freeze a weak "just filter harder" workflow into project builds
- it avoids prematurely widening aggregate minimal into a much larger ORM/modeling feature
- it creates explicit contracts that later capability upgrades can build on

## Problem 1: Aggregate Unsupported-Table Boundary

### Current Behavior

`DefaultCanonicalAssembler` currently requires:

- a table must define a primary key
- the primary key must contain exactly one column

If any collected DB table violates that rule, aggregate generation fails for the whole run.

This is correct for the current aggregate minimal capability, but too blunt for real integration because unrelated tables can still block the whole generator.

### Decision

Keep aggregate minimal capability unchanged:

- single-column primary key remains the only supported aggregate identity shape

But introduce an explicit unsupported-table policy for aggregate generation:

- `FAIL`
- `SKIP`

Recommended default:

- `FAIL`

Reason:

- default behavior should remain strict
- existing safety expectations should not silently weaken

`SKIP` is intended for real-project adoption and transition periods where the user wants aggregate generation only for supported tables without being blocked by unrelated schema areas.

### Behavior Rules

When aggregate generation is enabled:

- tables with no primary key are unsupported
- tables with composite primary keys are unsupported

If policy is `FAIL`:

- the first unsupported included table fails the run

If policy is `SKIP`:

- unsupported tables are excluded from canonical aggregate assembly
- supported tables continue through aggregate generation

### Filtering Semantics

Filtering should stay source-driven, not generator-driven.

The DB source still determines the candidate table set using:

- `includeTables`
- `excludeTables`

The aggregate unsupported-table policy only applies after that source-level filtering.

That means:

- if `includeTables` is set, only included tables are considered for support checks
- unrelated tables outside the include set must not block aggregate generation

This preserves a clean responsibility split:

- source decides the candidate schema set
- canonical assembly decides whether a candidate is supported by aggregate minimal

### Diagnostics

This slice should add an aggregate-oriented diagnostics report in the plan/result layer.

It should show:

- discovered table count
- included tables
- excluded tables
- supported tables
- unsupported tables
- unsupported reason per table

Unsupported reasons must be explicit, for example:

- `missing_primary_key`
- `composite_primary_key`

This diagnostics output is required even when policy is `FAIL`, because the failure should tell the user exactly what boundary was hit.

## Problem 2: Design Input Source Of Truth

### Current Behavior

The `design-json` source currently consumes an explicit file list coming from Gradle DSL.

This is technically correct, but in real projects it usually degrades into ad hoc scanning such as:

- `fileTree("iterate") { include("**/*_gen.json") ... }`

That makes the meaning of "active design input" unstable:

- archive files can be accidentally included
- deprecated files can be accidentally included
- active files are defined only by build-script heuristics

### Decision

Introduce an explicit manifest-based source of truth for active design inputs.

Recommended new `designJson` configuration support:

- `manifestFile`
- keep `files` as a temporary migration fallback

Priority:

1. `manifestFile` if provided
2. direct `files` list only when manifest is absent

This makes manifest the long-term truth source without breaking immediate migration.

### Manifest Shape

The manifest should be intentionally simple.

Recommended format:

- JSON array of relative file paths

Example:

```json
[
  "iterate/active/video_encrypt/video_encrypt_gen.json",
  "iterate/active/video_storage/video_storage_gen.json"
]
```

Paths should be interpreted relative to the consuming project root.

This format is preferred because:

- it is minimal
- it is easy to generate or maintain
- it does not require introducing a richer schema prematurely

### Validation Rules

If `manifestFile` is used:

- the manifest file must exist
- the manifest must not be empty
- every listed file must exist
- duplicate path entries fail fast

If direct `files` are used without manifest:

- current validation remains
- but documentation should make clear this is a migration path, not the preferred long-term contract

### Project Structure Policy

This slice does not require immediate physical relocation of existing design files.

That is deliberate.

A real project like `only-danmuku` already has historical files spread across:

- active-looking directories
- archived directories
- deprecated directories

Forcing a directory migration in the same slice would enlarge scope unnecessarily.

Manifest-first design allows the project to define active inputs immediately without moving files yet.

Directory cleanup can happen later as a separate repository hygiene step.

## Gradle DSL Impact

The DSL should evolve narrowly.

### Aggregate

Add aggregate generator options for unsupported-table handling.

Recommended shape:

```kotlin
cap4k {
  generators {
    aggregate {
      enabled.set(true)
      unsupportedTablePolicy.set("FAIL")
    }
  }
}
```

Allowed values:

- `FAIL`
- `SKIP`

### Design JSON

Add manifest support under `sources.designJson`.

Recommended shape:

```kotlin
cap4k {
  sources {
    designJson {
      enabled.set(true)
      manifestFile.set("iterate/design-manifest.json")
    }
  }
}
```

Direct `files.from(...)` remains supported during transition, but manifest becomes the preferred documented form.

## Module Boundaries

This slice should touch:

- `cap4k-plugin-pipeline-gradle`
  - DSL extension additions
  - config factory mapping
  - validation

- `cap4k-plugin-pipeline-source-design-json`
  - manifest reading and validation

- `cap4k-plugin-pipeline-core`
  - aggregate unsupported-table policy handling
  - diagnostics wiring

Tests should be added in:

- `Cap4kProjectConfigFactoryTest`
- `DesignJsonSourceProviderTest`
- `DefaultCanonicalAssemblerTest`
- `PipelinePluginFunctionalTest`

This slice should not require changes to:

- aggregate templates themselves
- flow generator
- drawing-board generator
- design template helper layer

## Error Handling

### Aggregate Unsupported Tables

If policy is `FAIL`:

- error message must include table name and reason

If policy is `SKIP`:

- no run failure from unsupported tables
- skipped-table diagnostics must still be emitted

### Design Manifest

Fail fast when:

- manifest file is missing
- manifest JSON is malformed
- manifest is empty
- a listed file does not exist
- duplicate entries appear

Error messages must point to the manifest path and the offending entry where applicable.

## Testing

This slice requires:

- unit tests for manifest parsing and validation
- unit tests for aggregate unsupported-table policy behavior
- unit tests for filtered-table support-check interaction
- functional tests showing:
  - aggregate `FAIL`
  - aggregate `SKIP`
  - manifest-driven design source
  - file-list fallback still working

At least one functional test should prove:

- an unsupported composite-PK table does not block aggregate generation when policy is `SKIP`
- a supported explicitly included table still generates artifacts in the same run

## Rollout

Recommended rollout order:

1. add DSL/config model support
2. add manifest reading in design-json source
3. add unsupported-table policy in canonical assembly
4. add diagnostics
5. add functional tests

After this slice, real-project integration should have:

- a stable truth source for active design inputs
- a safe and explicit boundary for unsupported DB tables

That provides a cleaner base before continuing the next design-generator quality slice.

# Cap4k Generate Entrypoint Source/Analysis Separation Design

Date: 2026-04-23
Status: Draft for review

## Summary

This slice fixes the current entrypoint boundary problem in `cap4kGenerate`.

Today the main pipeline entrypoint still mixes two different kinds of work:

- source-generation work
  - `design`
  - `designClient`
  - `designValidator`
  - `designApiPayload`
  - `designDomainEvent`
  - `aggregate`
- analysis-export work
  - `flow`
  - `drawingBoard`

That mix creates two contract problems:

1. `designDomainEvent` currently requires external `kspMetadata`, even though the current run can already know the aggregate package root through canonical aggregate data.
2. `flow` and `drawingBoard` currently live under the main plan/generate entrypoints even though they actually depend on explicit `irAnalysis` inputs and represent analysis export, not source generation.

The next iteration should separate those responsibilities.

After this slice:

- `cap4kPlan` and `cap4kGenerate` become source-generation entrypoints only
- `designDomainEvent` becomes aggregate-first and uses `kspMetadata` only as fallback
- `flow` and `drawingBoard` move out of the main source-generation entrypoints
- new analysis entrypoints own `irAnalysis`-driven export:
  - `cap4kAnalysisPlan`
  - `cap4kAnalysisGenerate`

This is a contract-cleanup slice.

It is not a full runner rewrite.

It is not an internal IR producer slice.

It is not a redesign of the whole pipeline dependency graph.

## Why This Slice Exists

The current README dependency table looks like one generator graph, but the runtime contract is not that clean.

Two real examples show the problem.

### `designDomainEvent`

`designDomainEvent` currently declares:

- `designJson` source
- `kspMetadata` source

But what it really needs is much narrower:

- the aggregate name
- the aggregate package root

That information is already derivable from the current run when aggregate canonical data exists.

So the hard requirement on external `kspMetadata` is stronger than it needs to be.

### `flow` / `drawingBoard`

`flow` and `drawingBoard` consume `irAnalysis`.

That is a legitimate use case.

But it is a different use case from source generation.

Those generators do not produce project source code.
They export analysis artifacts from compiler-produced graph snapshots.

Keeping them inside the main source-generation entrypoints makes the main contract impure:

- users see one generate entrypoint
- but part of that entrypoint actually assumes external analysis snapshots already exist

That is exactly the kind of hidden precondition this slice removes.

## Goals

### Primary Goal

Make the main `cap4kPlan` / `cap4kGenerate` contract honest:

- source generation should not silently depend on pre-existing external analysis snapshots
- one-shot source generation should be internally consistent

### Concrete Goals

- keep `designDomainEvent` in the main source-generation lane
- make `designDomainEvent` resolve aggregate metadata from the current run first
- keep `kspMetadata` only as compatibility fallback for `designDomainEvent`
- remove `flow` and `drawingBoard` from the main source-generation lane
- introduce dedicated analysis entrypoints for `irAnalysis`-driven export
- preserve the old multi-module analysis input pattern:
  - one analysis directory per module
  - merge in memory
  - do not require a physical root-level merged snapshot directory

## Non-Goals

This slice will not:

- introduce an internal `irAnalysis` producer inside the new pipeline
- make `flow` or `drawingBoard` work without `irAnalysis`
- remove the `kspMetadata` source entirely
- redesign `RequestModel`, `enum-manifest`, or the design generator family naming
- rewrite `flow` / `drawingBoard` generator internals beyond their task-entry contract
- merge analysis export back into the source-generation entrypoint

## Current State

### `designDomainEvent`

Current hard dependency validation says:

- `designDomainEvent` requires enabled `designJson`
- `designDomainEvent` requires enabled `kspMetadata`

That behavior is enforced in:
- [Cap4kProjectConfigFactory.kt](C:/Users/LD_moxeii/Documents/code/only-workspace/cap4k/cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kProjectConfigFactory.kt:378)

Current canonical assembly resolves domain-event aggregates only through `kspMetadata`:
- [DefaultCanonicalAssembler.kt](C:/Users/LD_moxeii/Documents/code/only-workspace/cap4k/cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssembler.kt:107)
- [DefaultCanonicalAssembler.kt](C:/Users/LD_moxeii/Documents/code/only-workspace/cap4k/cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssembler.kt:403)

### `flow` / `drawingBoard`

Current hard dependency validation says:

- `flow` requires enabled `irAnalysis`
- `drawingBoard` requires enabled `irAnalysis`

That behavior is enforced in:
- [Cap4kProjectConfigFactory.kt](C:/Users/LD_moxeii/Documents/code/only-workspace/cap4k/cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kProjectConfigFactory.kt:390)

The current plugin already has one important compatibility behavior:

- if `ir-analysis.inputDirs` points under some subproject `build/`
- the task graph auto-infers `dependsOn(":subproject:compileKotlin")`

That behavior exists today in:
- [PipelinePlugin.kt](C:/Users/LD_moxeii/Documents/code/only-workspace/cap4k/cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePlugin.kt:149)

That means the current system already knows how to bridge analysis input directories back to compile tasks.

What is wrong is not the inference itself.

What is wrong is that the inference currently lives under the main source-generation entrypoint contract.

## Approaches Considered

### Option 1: Keep the current entrypoints and only clarify the docs

This would mean:

- keep `designDomainEvent -> kspMetadata`
- keep `flow` / `drawingBoard` inside `cap4kPlan` / `cap4kGenerate`
- explain more clearly that some generators really require pre-existing snapshot inputs

Pros:

- smallest immediate code change

Cons:

- does not solve the hidden-precondition problem
- keeps the main entrypoint contract impure
- does not satisfy the requirement that one-shot generation should be internally consistent

This option is rejected.

### Option 2: Keep one main entrypoint but make it internally multi-phase for both source and analysis

This would mean:

- `cap4kGenerate` internally orchestrates source generation and analysis export in phases
- the task becomes responsible for all consistency guarantees

Pros:

- one user-facing entrypoint
- potentially strongest eventual consistency story

Cons:

- much larger scope
- forces `flow` / `drawingBoard` and `designDomainEvent` into the same redesign
- requires introducing an internal analysis-production phase that does not exist today

This option is valid in theory, but it is too large for the current slice.

### Option 3: Separate source-generation and analysis-generation entrypoints, and fix `designDomainEvent` independently

This means:

- keep source generation in `cap4kPlan` / `cap4kGenerate`
- move analysis export to:
  - `cap4kAnalysisPlan`
  - `cap4kAnalysisGenerate`
- make `designDomainEvent` aggregate-first inside the main source-generation lane
- keep `kspMetadata` as fallback only

Pros:

- fixes the contract impurity directly
- matches the actual use-case split
- preserves current `irAnalysis` input model without pretending it belongs to source generation
- keeps the slice bounded

Cons:

- introduces two analysis-specific tasks
- requires README and validation refactor

### Recommendation

Implement Option 3.

This is the smallest design that fully matches the user requirement:

- main generation entrypoints stop hiding pre-existing analysis snapshots
- `designDomainEvent` becomes same-run consistent where current aggregate data exists
- analysis export keeps explicit `irAnalysis` semantics, but moves to the correct task family

## Detailed Design

## Part A: Main Source-Generation Entrypoints

`cap4kPlan` and `cap4kGenerate` remain the main source-generation entrypoints.

After this slice, they are responsible for source-generation families only.

That includes:

- `design`
- `designQueryHandler`
- `designClient`
- `designClientHandler`
- `designValidator`
- `designApiPayload`
- `designDomainEvent`
- `designDomainEventHandler`
- `aggregate`

That does not include:

- `flow`
- `drawingBoard`

This is an explicit contract change.

The point is not that `flow` and `drawingBoard` are unsupported.

The point is that they belong to analysis export, not to source generation.

### Required Task-Level Behavior

When `cap4kPlan` or `cap4kGenerate` builds the execution config, the task must only pass source-generation generators into the runner.

It must not include analysis generators.

This means:

- `flow` and `drawingBoard` are not planned
- `flow` and `drawingBoard` are not exported
- `irAnalysis` does not participate in the main source-generation task family

### Validation Implication

The current hard validation:

- `flow generator requires enabled irAnalysis source`
- `drawingBoard generator requires enabled irAnalysis source`

must no longer be part of the validation path for the main source-generation task family.

Those checks belong to the new analysis task family instead.

## Part B: `designDomainEvent` Aggregate-First Resolution

`designDomainEvent` stays in the main source-generation lane.

But its aggregate-resolution contract changes.

### Required Resolution Order

When canonical assembly resolves a `domain_event` entry, aggregate metadata must be resolved in this order:

1. current-run canonical aggregate metadata
2. fallback `kspMetadata`
3. fail clearly if neither can resolve the aggregate

### Current-Run Aggregate Metadata Source

The current run already has canonical aggregate-root information when DB-driven aggregate canonical data exists.

At minimum, the current run can already provide:

- aggregate name
- aggregate package name

Those are exactly the fields `designDomainEvent` currently needs:

- [DesignDomainEventArtifactPlanner.kt](C:/Users/LD_moxeii/Documents/code/only-workspace/cap4k/cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignDomainEventArtifactPlanner.kt:34)
- [DesignRenderModelFactory.kt](C:/Users/LD_moxeii/Documents/code/only-workspace/cap4k/cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignRenderModelFactory.kt:75)

This slice does not require KSP-level type metadata.

It only requires aggregate-name and aggregate-package resolution.

### Fallback Behavior

If current-run canonical aggregate metadata does not contain the requested aggregate, canonical assembly may fall back to `KspMetadataSnapshot`.

This fallback is kept for compatibility.

The fallback exists to support cases such as:

- a project wants domain-event generation
- aggregate metadata is available from external KSP output
- current-run canonical aggregate data is not available

### Validation Change

Because `kspMetadata` becomes fallback instead of primary requirement:

- `designDomainEvent` must continue to require `designJson`
- `designDomainEvent` must no longer hard-require `kspMetadata`

The failure should move from config-validation time to canonical-resolution time:

- if aggregate metadata is available from current-run canonical aggregates, proceed
- else if available from `kspMetadata`, proceed
- else fail with a clear aggregate-resolution error

## Part C: Analysis Entrypoints

Introduce a separate analysis task family:

- `cap4kAnalysisPlan`
- `cap4kAnalysisGenerate`

These tasks own analysis export only.

The initial supported generators are:

- `flow`
- `drawingBoard`

No other generators move in this slice.

### Analysis Task Contract

The analysis task family is explicitly allowed to depend on `irAnalysis`.

That is the correct place for this dependency.

The analysis task family does not pretend that `irAnalysis` is optional or derivable from source generation.

Instead, it makes the contract explicit:

- analysis export consumes analysis inputs
- source generation does not

### Analysis Plan Output

`cap4kAnalysisPlan` must write its report to a distinct output path:

- `build/cap4k/analysis-plan.json`

This avoids collisions with the existing source-generation plan report:

- `build/cap4k/plan.json`

### Input Model

The analysis task family must preserve the old plugin's multi-module input model.

That means:

- each module may produce its own `build/cap4k-code-analysis`
- analysis tasks consume a list of input directories
- analysis data is merged in memory during canonical/generator planning
- there is no requirement to first write a physically merged root-level analysis snapshot directory

This point is important and repeated on purpose:

- do not add a new mandatory root `build/...` merged analysis directory
- keep multi-input-dir merge semantics

## Part D: Automatic Compile Inference for Analysis Tasks

The new analysis tasks should preserve the current automatic task inference behavior.

If an `irAnalysis` input directory falls under some subproject's `build/` directory, the analysis task family should automatically infer:

- `dependsOn(":subproject:compileKotlin")`

This behavior should apply to:

- `cap4kAnalysisPlan`
- `cap4kAnalysisGenerate`

It should not depend on users remembering to run compile manually first.

At the same time, this behavior must remain path-sensitive rather than unconditional.

That means:

- if `inputDirs` point to external directories outside Gradle build directories, the analysis task family should not infer compile tasks
- those external inputs remain valid explicit snapshot inputs

This preserves both use cases:

- local project analysis directories
- external precomputed analysis snapshots

## Part E: README and Public Contract Changes

The README must stop presenting one mixed generator dependency story.

Instead it should clearly separate:

1. source-generation entrypoints
2. analysis entrypoints

### Source Generation Section

The source-generation section should document:

- `cap4kPlan`
- `cap4kGenerate`
- their in-scope generators
- `designDomainEvent` aggregate-first + `kspMetadata` fallback contract

### Analysis Section

The analysis section should document:

- `cap4kAnalysisPlan`
- `cap4kAnalysisGenerate`
- `flow`
- `drawingBoard`
- explicit `irAnalysis` input requirements
- automatic `compileKotlin` inference when inputs point under module `build/`

### Dependency Table Change

The current single dependency table should no longer imply that `flow` and `drawingBoard` belong to the same entrypoint family as source generation.

Either:

- split the dependency table into two tables

or

- keep one table but add an explicit task-family column

The important point is not formatting.

The important point is that the public contract must stop implying that `flow` and `drawingBoard` are first-class main-entrypoint generators.

## Testing Strategy

Testing must lock the new task-family boundary and the new fallback semantics.

### `designDomainEvent` Tests

Add or update tests proving:

1. `designDomainEvent` succeeds when current-run canonical aggregate metadata is available and `kspMetadata` is disabled
2. `designDomainEvent` falls back to `kspMetadata` when current-run aggregate metadata is absent
3. `designDomainEvent` fails clearly when neither current-run aggregate metadata nor `kspMetadata` can resolve the aggregate
4. config validation no longer rejects `designDomainEvent` solely because `kspMetadata` is disabled

### Analysis Task Tests

Add or update tests proving:

1. `cap4kAnalysisPlan` and `cap4kAnalysisGenerate` are registered
2. they only include `flow` / `drawingBoard` generators
3. they infer `compileKotlin` dependencies from `irAnalysis.inputDirs` under module `build/`
4. they do not require a physically merged root analysis directory
5. they write plan output to `build/cap4k/analysis-plan.json`

### Main Entry Task Tests

Add or update tests proving:

1. `cap4kPlan` and `cap4kGenerate` no longer run `flow` / `drawingBoard`
2. `irAnalysis` no longer acts as a required dependency for the main source-generation entrypoint family

## Risks

### Risk 1: Accidental Generator Silencing

If task-family filtering is implemented poorly, enabled `flow` / `drawingBoard` generators might silently disappear without clear analysis entrypoint documentation.

Mitigation:

- update README in the same slice
- add explicit task registration tests
- ensure analysis generators are still reachable through dedicated tasks

### Risk 2: Over-coupling `designDomainEvent` to aggregate-generator enablement

The new aggregate-first behavior should not require the aggregate generator itself to run.

What it really needs is current-run canonical aggregate metadata.

Mitigation:

- phrase the implementation and tests around canonical aggregate metadata, not around the aggregate generator flag

### Risk 3: Scope drift into internal analysis production

The temptation will be to also build an internal `irAnalysis` producer so that analysis export becomes same-run end-to-end.

That is a valid future direction.

It is not required for this slice.

Mitigation:

- keep `irAnalysis` explicit in the new analysis task family
- do not reopen compiler-plugin production flow here

## Acceptance Criteria

This slice is complete only when all of the following are true:

- `cap4kPlan` and `cap4kGenerate` are source-generation entrypoints only
- `flow` and `drawingBoard` no longer belong to the main source-generation entrypoint family
- `cap4kAnalysisPlan` and `cap4kAnalysisGenerate` exist and own `flow` / `drawingBoard`
- `designDomainEvent` resolves aggregate metadata from the current run first
- `designDomainEvent` uses `kspMetadata` only as fallback
- `designDomainEvent` no longer hard-requires enabled `kspMetadata` at config-validation time
- analysis tasks preserve multi-module `inputDirs` semantics
- analysis tasks infer `compileKotlin` when inputs point under module `build/`
- README reflects the task-family split and the new `designDomainEvent` fallback contract

## Final Scope Reminder

This slice intentionally solves the fourth problem only.

It does not solve:

- `RequestModel` abstraction cleanup
- source family renaming
- `enum-manifest` source consolidation
- internal `irAnalysis` production inside the pipeline

It only corrects the entrypoint contract so that:

- main source generation is not polluted by analysis-export prerequisites
- `designDomainEvent` does not unnecessarily block on external KSP metadata
- analysis export gets its own honest task family

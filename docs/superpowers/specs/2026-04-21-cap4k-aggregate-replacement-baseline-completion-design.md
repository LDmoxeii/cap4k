## Cap4k Aggregate Replacement Baseline Completion Design

Date: 2026-04-21

## Purpose

This document defines the aggregate-side replacement baseline required for `cap4k-plugin-pipeline-*` to remain credible as:

- the future default aggregate generator path
- the basis for `only-danmaku-next` tutorial continuation
- a bounded replacement baseline for old aggregate codegen behavior

This slice exists because the aggregate line is no longer missing broad architecture, but it still has a few concrete baseline gaps that make generated output look incomplete:

- `schema` is still a minimal constant object rather than the old query DSL family
- `repository` is still an empty interface rather than the old Spring Data + adapter baseline
- aggregate family output cannot yet be selected at capability level
- renderer verification still lacks a consistent formatting gate for aggregate templates

The goal of this slice is to close those baseline gaps once, explicitly, without reopening unrelated exploratory parity tracks.

## Problem Statement

The bounded aggregate mainline has already completed major family migrations around:

- factory / specification
- wrapper
- unique constraint families
- enum / enum translation
- bounded relation semantics
- bounded persistence control

However, the current aggregate baseline is still not tutorial-grade or replacement-grade for two reasons.

First, some already-registered families remain materially below old-codegen baseline:

- `schema` currently renders as a minimal constant holder
- `repository` currently renders as an empty interface

Second, users cannot yet express the minimum aggregate-side output policy expected from a real framework:

- some families should be fixed baseline output
- some families should be optional
- grouped capabilities such as `unique-*` should share one lifecycle
- `cap4kPlan` must still reflect the true final output set

Without this slice, aggregate output remains partially migrated rather than coherently usable.

## Goals

- complete the aggregate-side replacement baseline needed for tutorial and framework credibility
- restore full baseline `schema` capability, while moving shared schema runtime into framework-owned runtime code rather than regenerating project-local `Schema.kt`
- restore full baseline `repository` capability
- add capability-level aggregate output selection with stable defaults
- add aggregate template formatting verification so whitespace regressions stop slipping through
- keep `cap4kPlan` and `cap4kGenerate` semantically aligned

## Non-Goals

- do not reopen bootstrap redesign; that remains its own slice
- do not merge aggregate persistence runtime smoke verification into this slice
- do not reactivate `ManyToMany`, `@JoinTable`, or `mappedBy`
- do not introduce in-file user-code merge / marker regeneration
- do not regenerate old `_share/meta/Schema.kt` into each project as source
- do not expose all internal aggregate planners as public user-facing DSL toggles
- do not solve every old-codegen difference under a vague "full parity" label

## Current Aggregate Family Inventory

The current pipeline aggregate generator is one public generator, `aggregate`, with twelve internal family planners:

1. `schema`
2. `entity`
3. `repository`
4. `factory`
5. `specification`
6. `wrapper`
7. `unique-query`
8. `unique-query-handler`
9. `unique-validator`
10. `shared-enum`
11. `local-enum`
12. `enum-translation`

This slice keeps that internal planner decomposition, but it does not expose all twelve families as independent public booleans.

## Replacement Baseline Contract

The replacement baseline is split into fixed baseline output and optional aggregate capabilities.

### Fixed Baseline Output

When `aggregate` generation is enabled, these capabilities remain fixed baseline output:

- `entity`
- `schema`
- `repository`
- `enum`
  - `shared-enum`
  - `local-enum`

These are fixed because they define the minimum aggregate-side generated surface needed for a coherent tutorial and a minimally credible replacement baseline.

### Optional Aggregate Capabilities

These capabilities become explicit aggregate-side optional outputs:

- `factory`
- `specification`
- `wrapper`
- `unique`
  - controls `unique-query`
  - controls `unique-query-handler`
  - controls `unique-validator`
- `enumTranslation`

The user-facing model is capability-level, not internal-planner-level.

`unique` is one public capability because its three families form one lifecycle and should not be configured independently.

`enum` itself is not optional. Only `enumTranslation` is optional.

## Defaults

Because the pipeline aggregate line is still in an early framework stage and should optimize for minimum usable output rather than hypothetical compatibility, all optional aggregate capabilities default to `false`:

- `factory = false`
- `specification = false`
- `wrapper = false`
- `unique = false`
- `enumTranslation = false`

This keeps the default aggregate baseline intentionally small and makes every additional family an explicit user choice.

## Dependency Rules

The slice introduces the following bounded dependency rules:

- `wrapper = true` requires `factory = true`
- `unique = false` disables all three `unique-*` families together
- `enumTranslation = false` prevents translation output even if enum source entries request translation

The `wrapper -> factory` dependency is required because the current wrapper contract depends on `Factory.Payload`.

No silent auto-enabling is allowed. Invalid combinations must fail fast during configuration translation or generator dependency validation.

## Schema Baseline Completion

`schema` remains a fixed baseline family, but its implementation must be upgraded from the current constant-object placeholder to the old baseline query DSL surface.

The restored baseline includes:

- `PROPERTY_NAMES`
- `props`
- `specify(...)`
- `subquery(...)`
- `predicateById(...)`
- `predicateByIds(...)`
- `predicate(...)`
- field accessors
- relation field accessors when current canonical relation data supports them
- helper entry points such as `all`, `any`, `allNotNull`, `anyNotNull`, `not`, and `spec`
- bounded Querydsl-related surface when the current bounded pipeline contract supports it

Shared schema runtime types move into framework-owned runtime code instead of project-local generated source. This includes the old shared schema infrastructure such as:

- `SchemaSpecification`
- `SubqueryConfigure`
- `ExpressionBuilder`
- `PredicateBuilder`
- `OrderBuilder`
- `Field`
- `JoinType`

This slice does not restore `_share/meta/Schema.kt` as generated project source.

### Schema and Wrapper Interaction

`schema` is fixed baseline output, but some old schema API surface depends on generated aggregate wrapper types such as `AggXxx`.

Therefore:

- when `wrapper = true`, schema may expose wrapper-dependent aggregate-specific API
- when `wrapper = false`, schema must not render wrapper-dependent API or references to `AggXxx`

This rule preserves a coherent default baseline while still allowing richer wrapper-aware schema output when users opt in.

## Repository Baseline Completion

`repository` remains a fixed baseline family and must be upgraded from the current empty interface placeholder to the old baseline repository surface.

The restored baseline includes:

- Spring Data repository interface shape
- `JpaRepository<Aggregate, Id>`
- `JpaSpecificationExecutor<Aggregate>`
- generated adapter based on `AbstractJpaRepository`
- aggregate repository annotation baseline such as `@Aggregate(... TYPE_REPOSITORY ...)`

If the bounded replacement baseline later chooses to support richer repository branches such as Querydsl-specific repository output, that support must remain bounded to the same explicit replacement baseline contract rather than silently broadening aggregate semantics.

## Aggregate Output Selection DSL

Aggregate output selection belongs under `generators.aggregate`, not under sources, templates, or exporter configuration.

The public DSL shape should expose capability-level output toggles, for example:

```kotlin
generators {
    aggregate {
        enabled.set(true)
        unsupportedTablePolicy.set("FAIL")

        artifacts {
            factory.set(false)
            specification.set(false)
            wrapper.set(false)
            unique.set(false)
            enumTranslation.set(false)
        }
    }
}
```

The public DSL must not expose all twelve internal family planners as independent booleans.

The fixed baseline families remain implicit and always planned when aggregate generation is enabled:

- `entity`
- `schema`
- `repository`
- `enum`

The optional capabilities are the only user-configurable aggregate output toggles:

- `factory`
- `specification`
- `wrapper`
- `unique`
- `enumTranslation`

## Planning and Internal Architecture

Artifact selection is generation strategy, not source semantics. Therefore it must not be stored in `CanonicalModel`.

This slice keeps the current separation:

- source and canonical stages describe aggregate facts
- generator planning decides which aggregate artifact families become output

### Internal Model

`Cap4kProjectConfigFactory` should translate aggregate artifact selection into stable aggregate generator options.

Inside `cap4k-plugin-pipeline-generator-aggregate`, the generator should parse those options once into a typed internal selection model, for example:

- `factoryEnabled`
- `specificationEnabled`
- `wrapperEnabled`
- `uniqueEnabled`
- `enumTranslationEnabled`

The typed selection model is internal to aggregate generation and should not become a new canonical-level public model.

### Planner Consumption Rules

Planner behavior should follow these rules:

- `EntityArtifactPlanner` always participates
- `SchemaArtifactPlanner` always participates
- `RepositoryArtifactPlanner` always participates
- `SharedEnumArtifactPlanner` always participates
- `LocalEnumArtifactPlanner` always participates
- `FactoryArtifactPlanner` participates only when `factory = true`
- `SpecificationArtifactPlanner` participates only when `specification = true`
- `AggregateWrapperArtifactPlanner` participates only when `wrapper = true`
- `UniqueQueryArtifactPlanner`, `UniqueQueryHandlerArtifactPlanner`, and `UniqueValidatorArtifactPlanner` participate only when `unique = true`
- `EnumTranslationArtifactPlanner` participates only when `enumTranslation = true`

The aggregate generator remains a single public generator. Optional capability selection only changes which artifact plan items it emits.

### Plan Semantics

`cap4kPlan` must continue to enumerate the true final artifact set.

Therefore:

- grouped public capabilities may map to multiple internal planners
- but plan output must still list each final artifact separately

Example:

- `unique = true` is one user-facing capability
- plan output still contains three separate artifact items if all three unique files are produced

The slice must not introduce "logical plan item" shortcuts that hide multiple real files behind one synthetic entry.

### Exporter Boundary

Artifact selection must stop at planning.

This slice explicitly rejects:

- planning files and then suppressing them only during export
- using template overrides to simulate disabled families
- making exporter behavior depend on aggregate family semantics

If a capability is disabled, the corresponding files must not enter the plan at all.

## Formatting Verification Hardening

This slice adds aggregate-specific renderer verification for whitespace stability.

The goal is not to create a universal pretty-printer or enforce cosmetic perfection across the whole repository. The goal is to establish a bounded formatting floor for aggregate templates so regressions such as stray indentation and unstable blank lines stop passing unnoticed.

At minimum, renderer verification must cover representative aggregate templates, including:

- `schema`
- `repository`
- `entity`
- `factory`
- `specification`
- `wrapper`
- `unique-*`

Verification should assert full-file or near-full-file structure where practical, with explicit checks for:

- stable import spacing
- stable class-body indentation
- no accidental double blank lines
- no broken inline whitespace around declarations

## Validation Strategy

This slice requires validation at five levels.

### 1. Config and Dependency Tests

Tests must verify:

- optional aggregate capabilities default to `false`
- `wrapper = true` with `factory = false` fails fast
- `unique` controls all three unique families together
- `enumTranslation = false` suppresses translation output even when enum source entries request translation

### 2. Planner Tests

Tests must verify:

- fixed baseline families still enter the plan
- disabled optional capabilities do not enter the plan
- `unique = true` yields three real artifact plan items, not one synthetic grouped item
- schema output planning adapts correctly when wrapper-dependent surface must be excluded

### 3. Renderer Tests

Tests must verify:

- schema baseline content is no longer a constant-object placeholder
- repository baseline content is no longer an empty interface
- representative aggregate templates respect the new formatting floor

### 4. Functional Tests

Fixture-based tests must verify:

- default minimal aggregate configuration generates only fixed baseline output plus enum baseline output
- enabling optional capabilities expands the generated output set correctly
- disabled capabilities do not silently write files
- `cap4kPlan` and `cap4kGenerate` remain consistent on aggregate output selection

### 5. Compile Verification

Compile-level verification must cover at least:

- a minimal aggregate configuration with optional capabilities left off
- a richer aggregate configuration with optional capabilities enabled
- schema output with `wrapper = false` does not reference missing wrapper classes
- repository baseline output compiles
- grouped unique output compiles as a coherent set when enabled

## Acceptance Criteria

This slice is complete only when all of the following are true:

1. aggregate fixed baseline output is explicitly defined as `entity`, `schema`, `repository`, and `enum`
2. aggregate optional capabilities are explicitly defined as `factory`, `specification`, `wrapper`, `unique`, and `enumTranslation`
3. all optional aggregate capabilities default to `false`
4. `unique` is one public capability but still expands to real per-file plan items
5. `wrapper = true` requires `factory = true`
6. schema output does not reference wrapper types when wrapper output is disabled
7. repository output reaches the old baseline repository structure rather than an empty interface
8. schema output reaches the old baseline query DSL structure rather than a constant placeholder object
9. aggregate renderer tests establish a stable formatting floor for representative aggregate templates
10. functional and compile verification prove both the default minimal configuration and the opt-in richer configuration work coherently

## Explicitly Deferred

The following remain outside this slice even though they exist in broader old-codegen parity discussions:

- `ManyToMany`
- `@JoinTable`
- `mappedBy`
- broader relation re-architecture
- in-file user-code preservation / merge
- regeneration of project-local shared `Schema.kt`
- bootstrap contract redesign
- aggregate persistence runtime smoke verification

These items must not be silently folded into this replacement-baseline slice.

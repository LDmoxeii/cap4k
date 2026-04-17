# Cap4k Aggregate Enum / Enum-Translation Parity

Date: 2026-04-17
Status: Draft for review

## Summary

The cross-generator reference boundary line is now complete through:

- aggregate factory / specification parity
- aggregate wrapper parity
- aggregate unique-constraint family parity

The next explicit framework slice should be:

- `aggregate enum / enum-translation parity`

This slice is still aggregate-side parity work, but it introduces one important refinement:

- shared enum ownership cannot be modeled safely as a generator side effect

So this slice should keep enum and enum-translation generation inside the existing `aggregate` generator, while adding a new bounded source-owned enum manifest contract for domain-shared enums.

The first slice should support two enum ownership modes:

- shared domain enums defined in an explicit enum manifest
- aggregate-local enums defined on DB columns through `@E + @T`

It should also keep `@T` as a general type-binding annotation rather than narrowing it to enum-only semantics.

## Goals

- Extend the pipeline with bounded enum and enum-translation parity for aggregate work.
- Distinguish shared enum ownership from aggregate-local enum ownership.
- Keep shared enums source-owned rather than generator-owned.
- Preserve `@T` as a general type-binding annotation that can bind:
  - shared enums
  - aggregate-local enums
  - other pre-existing strong types
- Use `@E + @T` as the only trigger for aggregate-local enum generation.
- Prevent repeated enum generation when a column only references a shared enum by `@T`.
- Keep enum binding deterministic and fail-fast on ambiguity.
- Add planner, renderer, functional, and bounded compile verification for representative shared and local enum flows.

## Non-Goals

- Do not reopen relation parity, JPA annotation parity, or full aggregate source-semantic recovery.
- Do not restore mutable shared runtime `typeMapping` as a global coordination mechanism.
- Do not silently guess between shared enum, local enum, and general type bindings.
- Do not make `@T` imply enum generation by itself.
- Do not restore every old enum-related task option in this first slice.
- Do not widen this slice into a general-purpose schema annotation framework.

## Current Context

Old aggregate codegen already contains two relevant generators:

- `EnumGenerator`
- `EnumTranslationGenerator`

Old codegen also already treats `@T` and `@E` as different concepts:

- `@T` is a general type binding via `SqlSchemaUtils.getType(...)`
- `@E` is enum-definition presence via `SqlSchemaUtils.hasEnum(...)`

The old enum context builder only collects enum definitions when `@E` exists on a column.

That means the stable parity direction is not:

- make `@T` an enum-generation switch

but instead:

- keep `@T` as type binding
- keep `@E` as enum-definition presence
- use the combination of both to decide whether a local enum should be generated

At the same time, the new framework now needs a second ownership mode that old aggregate codegen did not model explicitly:

- shared domain enums used by multiple aggregates

Those cannot be modeled safely as DB-owned local definitions.

## Why Shared Enums Must Be Source-Owned

The current pipeline is fixed-stage:

1. collect
2. assemble
3. plan
4. render
5. export

Generators plan from the canonical model. They do not feed generated files back into later planning.

Therefore, a shared enum cannot safely be implemented as:

- one generator emits enum files first
- aggregate planning later discovers those generated files

That would violate the stage model and create timing-dependent behavior.

The only stable design is:

- shared enum definitions are collected as source input
- they enter canonical or enrich-time registries before planning starts
- aggregate planning resolves `@T` against that shared enum registry before any file generation happens

This is what allows a same-run scenario to work safely:

- define shared enum `A` in a manifest
- add a new aggregate column with `@T=A`
- run the pipeline once
- both the shared enum artifact and the aggregate artifact plan from the same canonical truth

Under this model, the aggregate cannot "miss" the newly introduced shared enum in the same run.

## Design Decision

This slice should use a split-ownership model.

### Shared Enum Ownership

Shared enums should be:

- defined by a dedicated enum manifest or enum DSL source
- visible in canonical or enrich-time enum registries before planning
- generated once at a shared domain location

### Aggregate-Local Enum Ownership

Aggregate-local enums should be:

- defined on DB columns
- generated only when the same column contains both `@E` and `@T`
- scoped to the current aggregate

This gives the framework a clean rule:

- shared enum = source-owned
- local enum = DB-owned

and prevents accidental repetition of the same enum through two unrelated ownership mechanisms.

## Public Contract

This slice needs a bounded public source-surface expansion.

That is intentional and justified.

If shared enums are source-owned, the framework needs a source contract to ingest them.

Recommended shape:

- keep generator id = `aggregate`
- add one new source block for shared enum manifest input

This means the roadmap wording "no public DSL expansion" must be interpreted more narrowly here:

- no new public generator DSL
- one new bounded source DSL is required

## Shared Enum Source Contract

The first slice should add a dedicated shared enum source, for example:

- `enumManifest`

The exact DSL name can be finalized in implementation planning, but its responsibility should be narrow:

- load shared enum definitions
- validate uniqueness and schema shape
- expose them as source-owned enum definitions

The first slice should support only the minimum manifest data needed for parity:

- enum type name
- package or ownership scope
- enum items with:
  - value
  - name
  - description
- whether translation is generated

It should not introduce:

- arbitrary scripting
- custom code hooks
- dynamic runtime resolution

## Annotation Semantics

This slice should formalize the DB-column annotation semantics as follows.

### `@T`

`@T` remains a general type-binding annotation.

It may bind a column to:

- a shared enum
- an aggregate-local enum
- another existing strong type such as a JSON-bound value type

By itself, `@T` must not trigger enum generation.

### `@E`

`@E` means enum-definition data exists on the column.

By itself, `@E` is incomplete and should fail.

### Allowed Combinations

- `@T` only:
  - bind to an existing type
  - do not generate a local enum

- `@E + @T`:
  - define and generate an aggregate-local enum

- `@E` only:
  - fail-fast

This is the key stability rule for preventing duplicate enum generation.

## `@T` Resolution Order

The first slice should use a fixed resolution order.

Recommended order:

1. explicit FQN in `@T`
2. aggregate-local enum definition from the same column when `@E + @T` is present
3. shared enum registry from the enum manifest source
4. existing general type registry
5. fail-fast if unresolved

This order keeps ownership explicit and prevents hidden fallback behavior.

## Conflict Rules

This slice should fail fast on ambiguity.

### Invalid or Ambiguous Cases

- `@E` without `@T`
- `@T` simple name matching both a shared enum and a non-enum general type
- `@E + @T=A` on a column while a shared enum `A` is also visible and the binding is not explicit enough
- multiple local enum definitions with the same type name but different enum item sets inside one aggregate

### Allowed Cases

- multiple aggregates define their own local enum named `A` as long as FQNs differ
- multiple columns in one aggregate reuse the same local enum type only if their definitions are identical

No silent precedence guessing should be allowed in these conflict cases.

## Translation Ownership

Enum-translation ownership must follow enum ownership.

- shared enum -> shared translation
- aggregate-local enum -> aggregate-local translation

This slice should not allow translation ownership to drift away from the owning enum.

That means:

- no shared enum with local translation
- no local enum with shared translation

## Same-Run Binding Contract

The framework should explicitly support this same-run scenario:

1. a new shared enum is added to the enum manifest
2. a new aggregate column is added with `@T` referencing that shared enum
3. one pipeline run plans both the shared enum artifacts and the aggregate artifacts

This should work because the binding happens before planning, using source-owned shared enum data.

The implementation must not depend on:

- first generating enum source files
- then re-reading them

to make aggregate references succeed.

## Planner Boundary

This slice should keep enum and enum-translation generation inside the existing `aggregate` generator.

That means:

- no new top-level `enum` generator
- enum and translation planners live alongside other aggregate family planners

The planners should consume:

- aggregate-local enum definitions derived from DB column annotations
- shared enum definitions resolved from the new enum manifest source

The exact canonical structure can remain bounded.

The first slice does not need a broad new framework-wide runtime map.

If helper structures are needed, they should be:

- deterministic
- read-only
- planner-owned

## First-Slice Boundaries

The first slice should include:

- shared enum manifest ingestion
- aggregate-local enum generation via `@E + @T`
- bounded enum-translation generation
- `@T` type-binding resolution across shared enums, local enums, and general types
- fail-fast conflict detection
- representative same-run binding verification

The first slice should not include:

- full recovery of every old enum task option
- broad schema-driven enum inference outside the explicit manifest or explicit annotations
- relation/JPA parity
- general aggregate source-semantic recovery

## Validation Strategy

This slice should validate at four levels.

### 1. Source / Canonical

Validation should prove:

- shared enum manifest entries are collected correctly
- duplicate shared enum definitions fail
- shared enum definitions are visible before planning
- `@E + @T` local enum definitions are recognized correctly
- `@T`-only bindings do not trigger local enum generation

### 2. Planner / Unit

Planner coverage should lock:

- shared enum planning
- local enum planning
- translation ownership following enum ownership
- `@T` resolution order
- conflict and ambiguity fail-fast behavior

### 3. Renderer

Renderer coverage should prove:

- enum preset fallback works
- enum-translation preset fallback works
- shared and local enum outputs remain bounded and compile-safe

### 4. Functional / Compile

Functional or compile verification should prove:

- same-run shared enum definition plus aggregate reference works
- `@T`-only shared enum reference does not generate a duplicate local enum
- `@E + @T` generates a local enum
- raw DB ownership and shared manifest ownership remain distinct

## Success Criteria

This slice is successful when:

- shared enums are source-owned through a dedicated manifest input
- aggregate-local enum generation requires `@E + @T`
- `@T` remains a general type-binding annotation
- `@T`-only references do not create duplicate aggregate-local enums
- same-run shared enum definition and aggregate reference works without generator-to-generator feedback
- enum-translation ownership follows enum ownership
- ambiguity and incomplete definitions fail fast instead of guessing

## Recommended Next Step

After this spec is approved, the implementation plan should stay focused on:

1. introducing the bounded shared enum source contract
2. defining the `@T` resolution order and conflict rules
3. implementing aggregate-local enum generation via `@E + @T`
4. wiring translation ownership to enum ownership
5. proving same-run shared enum binding and duplicate-prevention behavior

That keeps the slice large enough to solve the real ownership problem, but still bounded enough to remain framework-stable.

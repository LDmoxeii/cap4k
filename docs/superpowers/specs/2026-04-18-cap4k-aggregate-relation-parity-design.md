# Cap4k Aggregate Relation Parity

Date: 2026-04-18
Status: Draft for review

## Summary

The cross-generator reference boundary line is now complete through:

- aggregate factory / specification parity
- aggregate wrapper parity
- aggregate unique-constraint family parity
- aggregate enum / enum-translation parity

The next explicit framework slice should be:

- `aggregate relation parity`

This slice should not try to reopen full aggregate parity or JPA fine-grained control in one step.

Instead, it should establish a bounded first relation slice that:

- extends the existing `db` source with relation annotation carriage
- introduces a bounded aggregate relation model before planning
- lets `EntityArtifactPlanner` and `aggregate/entity.kt.peb` render representative relation fields and annotations

The first slice should cover the most stable old relation semantics:

- child-parent aggregate relations via `@Parent`
- column-level explicit `@Reference`
- explicit `@Relation=ManyToOne`
- explicit `@Relation=OneToOne`
- bounded `@Lazy`

The first slice should explicitly not include:

- `ManyToMany`
- `*ManyToMany`
- `generateParent`-style inverse navigation strategy
- JPA annotation fine-grained control parity

## Goals

- Extend the current `db` source with bounded relation metadata carriage.
- Add a minimal aggregate relation canonical slice that is consumed before planner execution.
- Keep relation ownership inside the existing aggregate line instead of creating a second truth source.
- Render representative relation fields and JPA relation annotations through `aggregate/entity.kt.peb`.
- Keep the slice compile-verifiable through bounded functional and compile fixtures.

## Non-Goals

- Do not add a standalone `relations` source.
- Do not implement `ManyToMany` or inverse-navigation parity in this first slice.
- Do not reopen JPA annotation fine-grained control parity.
- Do not reopen user-code preservation parity.
- Do not widen this slice into full aggregate semantic recovery.
- Do not change public generator DSL; this remains inside the existing `aggregate` generator.

## Current Context

Old aggregate codegen relation support is split across three layers:

1. annotation carriage from DB comments
2. relation inference in `RelationContextBuilder`
3. relation rendering in `EntityGenerator`

The old code recognizes:

- table-level:
  - `@Parent` / `@P`
  - `@AggregateRoot` / `@Root` / `@R`
  - `@ValueObject` / `@VO`
- column-level:
  - `@Reference` / `@Ref`
  - `@Relation` / `@Rel`
  - `@Lazy` / `@L`
  - `@Count` / `@C`

The old `RelationContextBuilder` resolves:

- internal child-parent relations
- explicit `ManyToOne`
- explicit `OneToOne`
- join-table-driven `ManyToMany`
- inverse navigation forms marked with `*`

The new pipeline currently has none of that relation structure:

- `DbTableSnapshot` has no parent or aggregate-level relation metadata
- `DbColumnSnapshot` has no reference or relation fields
- `CanonicalModel` has no aggregate relation model
- `EntityArtifactPlanner` only receives scalar field context

This means the next stable slice is not "JPA detail parity". It is:

- relation metadata carriage
- bounded relation modeling
- entity-side relation rendering

## Why Relation Metadata Should Stay in the Existing DB Source

Shared enums required a separate source because they are source-owned shared assets.

Relations are different.

The old system already treats relation metadata as part of the DB schema contract:

- table comment annotations
- column comment annotations

So the stable truth source for this slice remains:

- the existing `db` source

Creating a second `relations` source in the first slice would introduce two competing truth sources:

- DB comment annotations
- relation source files

That would make parity harder, not easier.

Therefore this slice should:

- extend `DbSchemaSourceProvider`
- extend `DbTableSnapshot`
- extend `DbColumnSnapshot`

and keep relation annotation carriage there.

## Design Decision

This slice should be a bounded first relation slice.

### Source Layer

The `db` source should carry raw relation metadata from comment annotations.

It should not try to fully interpret every relation form inside the source provider.

### Canonical Layer

The assembler should create a bounded aggregate relation model from those raw source annotations.

This keeps:

- inference before planning
- canonical truth immutable after assembly
- planner logic simpler

### Planner Layer

The first consumer should be:

- `EntityArtifactPlanner`

No new generator family is required.

This is still part of the existing `aggregate` generator.

## Source Contract

This slice should extend the existing snapshots with bounded relation metadata carriage.

### Table-Level Carriage

`DbTableSnapshot` should gain bounded relation-related fields, for example:

- `parentTable`
- `aggregateRoot`
- `valueObject`

These fields only carry normalized annotation meaning. They do not yet encode final rendered JPA semantics.

### Column-Level Carriage

`DbColumnSnapshot` should gain bounded relation-related fields, for example:

- `referenceTable`
- `explicitRelationType`
- `lazy`
- `countHint`

These fields should be derived from existing old annotation aliases:

- `@Reference` / `@Ref`
- `@Relation` / `@Rel`
- `@Lazy` / `@L`
- `@Count` / `@C`

### Validation

The source layer should fail fast on malformed relation annotation inputs rather than silently guessing.

This slice does not need full old annotation parity, but it does need bounded correctness.

## Canonical Model Shape

This slice should not overload `FieldModel` with full relation semantics.

`FieldModel` is already reused across multiple lines:

- design requests
- validators
- api payloads
- aggregate scalar fields

Turning it into a full relation carrier would blur responsibilities.

Instead, this slice should add a bounded aggregate-side canonical slice, for example:

```kotlin
data class AggregateRelationModel(
    val ownerEntityName: String,
    val ownerEntityPackageName: String,
    val fieldName: String,
    val targetEntityName: String,
    val targetEntityPackageName: String?,
    val relationType: AggregateRelationType,
    val joinColumn: String,
    val fetchType: AggregateFetchType,
)
```

and:

```kotlin
data class CanonicalModel(
    ...
    val aggregateRelations: List<AggregateRelationModel> = emptyList(),
)
```

### Entity-Level Aggregate Metadata

This slice may also extend `EntityModel` with the minimum table-level aggregate semantics needed for relation reasoning:

- `aggregateRoot`
- `valueObject`
- `parentEntityName`

That is still entity-owned metadata and does not pollute generic field shape.

## First-Slice Supported Relation Types

This slice should support only:

- `MANY_TO_ONE`
- `ONE_TO_ONE`
- `ONE_TO_MANY`

It should explicitly not support:

- `MANY_TO_MANY`
- inverse-star relation forms such as `*ManyToMany`

This is a deliberate scope boundary.

## Inference Rules

The assembler or aggregate relation helper should apply only bounded, explicit rules.

### Child-Parent

If a table is marked with `@Parent`, the first slice should support:

- parent-side `OneToMany`

This is the most stable old relation rule and the most useful bounded parity case.

This first slice should not automatically generate child-side inverse navigation unless it is explicitly declared.

### Reference Without Explicit Type

If a column has `@Reference` but no explicit `@Relation`, the first slice should treat it as:

- `ManyToOne`

This matches the old default behavior and remains easy to explain.

### Explicit OneToOne

If a column explicitly declares:

- `@Relation=OneToOne`

the first slice should produce:

- `ONE_TO_ONE`

No implicit one-to-one inference should be attempted.

### Lazy Hint

If a relation column carries:

- `@Lazy`

the first slice should map it to:

- bounded fetch-type selection only

It should not expand into broader JPA tuning behavior.

## Rendering Boundary

This slice should not create a new relation template family.

The primary renderer consumer should remain:

- `aggregate/entity.kt.peb`

That template should be enriched with bounded relation context in addition to scalar field context.

Recommended shape:

- scalar fields remain separate
- relation fields are rendered through a distinct `relationFields` collection

This keeps relation output explicit and avoids destabilizing existing scalar-field rendering.

## Planner Boundary

The first slice should modify:

- `EntityArtifactPlanner`

It should not require changes to:

- factory planner
- specification planner
- wrapper planner
- unique family planners
- enum family planners

Those lines can stay untouched until a later aggregate parity slice proves a reason to extend them.

## Validation Strategy

This slice should validate at four levels.

### 1. Source / Parser

Tests should prove:

- table-level `@Parent`
- column-level `@Reference`
- explicit `@Relation`
- bounded `@Lazy`

are parsed and carried correctly from DB comments.

### 2. Canonical / Assembler

Tests should prove:

- child-parent becomes parent-side `OneToMany`
- `@Reference` without explicit type becomes `ManyToOne`
- explicit `OneToOne` becomes `ONE_TO_ONE`
- unsupported relation forms fail fast or remain explicitly unsupported

### 3. Planner / Renderer

Tests should prove:

- `EntityArtifactPlanner` carries relation fields to render context
- `aggregate/entity.kt.peb` renders representative relation fields and annotations
- scalar field rendering remains stable

### 4. Functional / Compile

Tests should prove:

- `cap4kPlan` and `cap4kGenerate` produce representative relation-aware entity output
- generated entity source participates in compile
- bounded child-parent and explicit relation cases compile in a representative fixture

## Success Criteria

This slice is successful when:

- the `db` source carries bounded relation annotations
- canonical assembly produces a bounded aggregate relation model
- `EntityArtifactPlanner` consumes relation data before rendering
- `aggregate/entity.kt.peb` renders representative relation output
- parent-child and explicit single-column relation cases are covered end to end
- no `ManyToMany` or inverse-navigation complexity is silently introduced

## Recommended Next Step

After this spec is approved, the implementation plan should stay focused on:

1. bounded relation metadata carriage in the existing `db` source
2. a small aggregate relation canonical model
3. entity planner and template consumption
4. representative functional and compile verification

That keeps the slice large enough to close the real relation gap, but still bounded enough to preserve framework stability.

# Cap4k Aggregate Inverse Relation Read-Only Parity

Date: 2026-04-20
Status: Draft for review

## Summary

The bounded aggregate relation line is now complete through:

- aggregate relation parity
- aggregate relation-side JPA control parity

The roadmap has now advanced to:

- `aggregate advanced relation forms parity`

That roadmap label should not be implemented as one large relation rewrite.

The first explicit slice inside that line should be:

- `aggregate inverse relation read-only parity`

This slice should establish bounded support for old `*ManyToOne`-style inverse navigation by:

- deriving inverse parent navigation from already-known parent-child relation truth
- keeping the derivation inside the existing aggregate relation line
- rendering a read-only `MANY_TO_ONE` field with:
  - `insertable = false`
  - `updatable = false`

This slice should explicitly not include:

- `mappedBy`
- `@JoinTable`
- `ManyToMany`
- `*ManyToMany`
- relation ownership redesign
- new source syntax or relation-side runtime configuration

## Goals

- Start the advanced relation line with the smallest old relation form that is still practically useful.
- Reuse the current parent-child relation line rather than introducing a second truth source.
- Keep inverse/read-only relation semantics aggregate-owned and planner-driven.
- Allow scalar FK fields and inverse navigation fields to coexist without reopening owner-side mapping.
- Keep the slice compile-verifiable and bounded to aggregate entity output.

## Non-Goals

- Do not introduce new DB comment annotations for inverse relations.
- Do not reopen relation inference rules from scratch.
- Do not add `mappedBy`, `@JoinTable`, or any `ManyToMany` behavior.
- Do not widen this slice into relation ownership redesign, relation re-architecture, or full advanced relation parity.
- Do not broaden into provider-specific relation behavior or user-code preservation.

## Current Context

Old aggregate codegen supports a second relation family beyond the first bounded pipeline relation line.

The relevant old form is:

- `*ManyToOne`

In the old path:

- `RelationContextBuilder` derives `*ManyToOne` from parent-child aggregate structure when parent navigation generation is enabled
- `EntityGenerator` renders that inverse field as:
  - `@ManyToOne(fetch = ...)`
  - `@JoinColumn(..., nullable = false, insertable = false, updatable = false)`

This old form is materially different from the current bounded pipeline relation line:

- it is derived rather than directly source-declared
- it is read-only
- it exists alongside the scalar FK field rather than replacing it

The current pipeline already covers:

- parent-child `ONE_TO_MANY`
- explicit/default `MANY_TO_ONE`
- explicit `ONE_TO_ONE`
- bounded relation-side JPA control for those forms

But it still does not cover:

- derived inverse/read-only parent navigation on the child side

That makes the next stable slice:

- inverse/read-only relation parity

not:

- `ManyToMany`
- `mappedBy`
- `@JoinTable`

## Why This Slice Should Precede `ManyToMany`

The old relation line still contains two broad families of "advanced" forms:

1. inverse/read-only parent navigation:
   - `*ManyToOne`
2. join-table and inverse collection forms:
   - `ManyToMany`
   - `*ManyToMany`

The first family is smaller, more local, and already aligned with the current bounded aggregate relation line.

The second family would immediately require:

- join-table metadata
- explicit owner/inverse topology
- `mappedBy`
- `@JoinTable`
- more invasive relation ownership design

So the stable order is:

1. inverse/read-only `*ManyToOne`
2. only later, if still needed, evaluate whether `ManyToMany` is worth a separate slice

## Design Decision

This slice should be the first bounded implementation step of the roadmap's advanced relation forms line.

### Scope Boundary

This slice should cover:

- derived inverse/read-only `MANY_TO_ONE` fields for parent-child aggregate relations
- planner and renderer support for:
  - `readOnly = true`
  - `insertable = false`
  - `updatable = false`
- coexistence of:
  - scalar FK field
  - inverse navigation field

This slice should not cover:

- `mappedBy`
- `@JoinTable`
- `ManyToMany`
- `*ManyToMany`
- explicit source-declared inverse annotations
- optional parent navigation redesign

### Layering

The design should stay split into four bounded responsibilities:

1. existing parent-child source and canonical truth
2. bounded inverse relation derivation
3. planner-owned render mapping
4. template-owned mechanical emission

Policy decisions should happen before rendering.

Templates should not infer whether a relation is inverse or read-only.

## Source Contract

This slice should not add new source syntax.

It should continue to rely on the existing relation input already carried through:

- `DbTableSnapshot.parentTable`
- parent anchor columns already recognized by the current relation line

That means:

- no new table comment annotation
- no new column comment annotation
- no new `relations { ... }` DSL

The source truth is still:

- parent-child aggregate structure
- already-known child anchor column

## Canonical Model Shape

This slice should not overload the existing `aggregateRelations` list with a second ownership flavor.

The current `AggregateRelationModel` represents the bounded relation forms already supported by the mainline:

- `ONE_TO_MANY`
- `MANY_TO_ONE`
- `ONE_TO_ONE`

Inverse/read-only parent navigation has a different role:

- it is derived
- it is read-only
- it is non-owner-side navigation

So this slice should add a separate bounded aggregate canonical slice, for example:

```kotlin
data class AggregateInverseRelationModel(
    val ownerEntityName: String,
    val ownerEntityPackageName: String,
    val fieldName: String,
    val targetEntityName: String,
    val targetEntityPackageName: String,
    val relationType: AggregateRelationType,
    val joinColumn: String,
    val fetchType: AggregateFetchType,
    val nullable: Boolean = false,
    val insertable: Boolean = false,
    val updatable: Boolean = false,
)
```

And `CanonicalModel` should gain:

```kotlin
val aggregateInverseRelations: List<AggregateInverseRelationModel> = emptyList()
```

The key point is not the exact property names. The key point is:

- inverse/read-only relation truth stays separate from owner-side relation truth
- the new model remains bounded to one relation form
- this slice does not introduce generalized advanced relation ownership metadata

## Canonical Derivation Rules

Inverse/read-only relations should be derived only from already-known parent-child relation truth.

### Allowed Derivation

This slice may derive an inverse relation only when all of the following are true:

1. the child table has `parentTable`
2. the current bounded relation line can already resolve the parent-child anchor join column
3. the child entity does not already have an owner-side relation to the same parent using the same join column
4. the derived inverse field name does not collide with:
   - a scalar field name
   - an existing owner-side relation field name
   - another derived inverse relation field name

### Forbidden Derivation

This slice should not derive inverse/read-only relations from:

- explicit external references between aggregate roots
- explicit `ONE_TO_ONE`
- any future `ManyToMany` topology

This first slice is only about:

- child-side inverse navigation back to the already-known parent aggregate

### Relation Shape

The derived inverse relation should have a fixed bounded shape:

- `relationType = MANY_TO_ONE`
- `nullable = false`
- `insertable = false`
- `updatable = false`

For fetch type, this slice should stay conservative and reuse the current bounded parent-child line:

- derive `fetchType = LAZY`

This keeps the slice stable and avoids reopening fetch-policy recovery.

### Naming Rule

The derived inverse field name should be:

- `lowerCamel(parentEntityName)`

For example:

- parent entity: `VideoPost`
- child entity: `VideoPostItem`
- inverse field: `videoPost`

This slice should not attempt:

- alias recovery
- custom inverse naming
- new naming DSL

## Planner and Render Model

`AggregateRelationPlanning` and `EntityArtifactPlanner` should continue to own render-ready relation context.

This slice should not introduce a second entity template or a special inverse-relation template.

Instead, the planner should merge the new inverse relation slice into relation render context with bounded flags such as:

- `readOnly`
- `insertable`
- `updatable`

One acceptable render shape is:

```kotlin
mapOf(
    "name" to ...,
    "relationType" to "MANY_TO_ONE",
    "targetTypeRef" to ...,
    "joinColumn" to ...,
    "fetchType" to "LAZY",
    "nullable" to false,
    "readOnly" to true,
    "insertable" to false,
    "updatable" to false,
)
```

The important point is:

- planner-owned truth decides whether this is inverse/read-only
- renderer only emits the provided bounded contract

### Collision and Suppression Rules

The planner should enforce:

- scalar FK field and inverse navigation field may coexist if their names differ
- explicit owner-side child relation to the same parent suppresses inverse derivation
- name collision with existing scalar or relation fields must fail fast

This prevents the slice from silently creating duplicate join-column ownership.

## Template Contract

This slice should still only change:

- `aggregate/entity.kt.peb`

It should not add new templates.

The template should gain one bounded branch:

- `MANY_TO_ONE` with `readOnly = true`

That branch should mechanically emit:

```kotlin
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "...", nullable = false, insertable = false, updatable = false)
lateinit var parent: ParentEntity
```

The template should not:

- infer whether the relation is inverse
- infer whether the join column is writable
- output `mappedBy`
- output `@JoinTable`
- output any `ManyToMany` annotation

## Validation Strategy

This slice should be validated in four layers.

### 1. Canonical and Inference Tests

Add tests that prove:

- parent-child relation truth can derive inverse/read-only `MANY_TO_ONE`
- child entities with existing explicit owner-side parent relation do not get a duplicate inverse relation
- inverse naming is deterministic
- field collisions fail fast

### 2. Planner Tests

Add tests that prove inverse relations are surfaced to the render model with:

- `readOnly = true`
- `insertable = false`
- `updatable = false`
- `nullable = false`
- `fetchType = LAZY`

And also prove existing owner-side relation render output is not regressed.

### 3. Renderer Tests

Add renderer coverage that proves generated entity output contains:

- `@JoinColumn(..., insertable = false, updatable = false)`

and still does not contain:

- `mappedBy =`
- `@JoinTable`
- `ManyToMany`

### 4. Functional and Compile Tests

Add a representative aggregate relation fixture that proves:

1. parent-child entities generate successfully
2. the child entity contains:
   - scalar FK field
   - inverse/read-only navigation field
3. compile verification succeeds
4. when the child already has an explicit owner-side relation, the inverse/read-only field is not duplicated

## Non-Goals and Deferrals

This slice intentionally does not include:

- `mappedBy`
- `@JoinTable`
- `ManyToMany`
- `*ManyToMany`
- relation ownership redesign
- new source syntax for inverse navigation
- full advanced relation parity

Those remain later decisions, and `ManyToMany` may remain deferred indefinitely if it does not justify its complexity.

## Success Criteria

This slice is complete when:

1. aggregate canonical assembly can derive bounded inverse/read-only `MANY_TO_ONE` relations from existing parent-child truth
2. planner output surfaces that bounded inverse relation with explicit read-only join-column flags
3. `aggregate/entity.kt.peb` renders `insertable = false, updatable = false` for the inverse relation form
4. representative functional and compile tests pass
5. no `mappedBy`, `@JoinTable`, or `ManyToMany` behavior is introduced by this slice

## Why This Slice Is the Right Next Step

The current relation line has already established:

- source carriage
- bounded canonical inference
- bounded relation-side JPA control

The most practical remaining old relation gap is no longer `ManyToMany`.

It is:

- inverse/read-only parent navigation

That makes this slice the smallest next step that still moves aggregate parity forward without breaking the bounded mainline.

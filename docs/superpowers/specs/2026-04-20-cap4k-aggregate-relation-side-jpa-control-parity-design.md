# Cap4k Aggregate Relation-Side JPA Control Parity

Date: 2026-04-20
Status: Draft for review

## Summary

The bounded aggregate relation line is now complete through:

- relation metadata carriage on the existing `db` source line
- bounded canonical relation inference
- planner and renderer consumption for:
  - `MANY_TO_ONE`
  - `ONE_TO_ONE`
  - `ONE_TO_MANY`
- representative functional and compile verification

The next explicit framework slice should be:

- `aggregate relation-side JPA control parity`

This slice should not reopen relation inference, relation ownership redesign, or advanced relation forms.

Instead, it should establish a bounded relation-side JPA control layer for aggregate entity output by:

- extending the existing aggregate relation canonical model with only the relation-side control metadata the current line can safely support
- letting aggregate relation planning derive render-ready relation control from existing canonical truth
- enriching `aggregate/entity.kt.peb` with mechanical emission of bounded relation-side JPA annotation parameters

This slice should cover:

- bounded `ONE_TO_MANY` control:
  - `cascade = [CascadeType.ALL]`
  - `orphanRemoval = true`
  - `@JoinColumn(nullable = false)`
- bounded `MANY_TO_ONE` join-column nullability control
- bounded `ONE_TO_ONE` join-column nullability control

This slice should explicitly not cover:

- `mappedBy`
- `@JoinTable`
- `ManyToMany`
- inverse/read-only relation-side `insertable = false, updatable = false`
- relation re-architecture or source-semantic recovery

## Goals

- Add bounded relation-side JPA control parity on top of the now-stable aggregate relation line.
- Keep relation-side control metadata inside the existing aggregate relation source and canonical path.
- Preserve the current aggregate relation line as a bounded model rather than turning it into a general-purpose relation ownership framework.
- Let `AggregateRelationPlanning`, `EntityArtifactPlanner`, and `aggregate/entity.kt.peb` render bounded relation-side controls without reopening relation inference itself.
- Keep the slice compile-verifiable and bounded to aggregate entity relation output.

## Non-Goals

- Do not add a standalone relation-side control source.
- Do not introduce a new public generator DSL or a general-purpose runtime relation configuration block.
- Do not reopen relation inference rules, ownership redesign, or inverse navigation strategy.
- Do not restore `ManyToMany`, `mappedBy`, or `@JoinTable` behavior in this slice.
- Do not widen into provider-specific relation behavior, user-code preservation, or full aggregate semantic recovery.

## Current Context

Old aggregate codegen entity output includes a second layer of relation-side control beyond basic relation presence.

For example:

- `OneToMany` collection relations are emitted with:
  - `cascade = [CascadeType.ALL]`
  - `orphanRemoval = true`
  - `@JoinColumn(..., nullable = false)`
- direct `ManyToOne` and `OneToOne` relations are emitted with explicit `nullable = false`
- more advanced forms such as `*ManyToOne`, `ManyToMany`, and `*ManyToMany` carry additional ownership-aware behavior including read-only join columns, `mappedBy`, `@JoinTable`, and provider-specific fetch controls

The current pipeline deliberately stopped at the first relation parity slice:

- relation metadata carriage on `DbTableSnapshot` / `DbColumnSnapshot`
- bounded `AggregateRelationModel`
- planner and renderer support for the three bounded relation types:
  - `MANY_TO_ONE`
  - `ONE_TO_ONE`
  - `ONE_TO_MANY`

The current aggregate entity preset already emits:

- `@ManyToOne(fetch = FetchType....)`
- `@OneToOne(fetch = FetchType....)`
- `@OneToMany(fetch = FetchType....)`
- `@JoinColumn(name = "...")`

But it still lacks the bounded relation-side JPA control layer that old codegen applies to those same relation forms.

That means the next stable slice is not "full relation control parity". It is:

- bounded relation-side JPA control parity

## Why This Slice Should Stay on the Existing Aggregate Relation Line

Truth for these controls still belongs to the aggregate-side relation line. It should continue to flow through:

- `DbSchemaSourceProvider`
- aggregate canonical assembly
- `AggregateRelationPlanning`
- `EntityArtifactPlanner`
- `aggregate/entity.kt.peb`

Adding a separate relation-control source or public `relations { ... }` DSL in this slice would create a second truth source before the current bounded relation contract is mature.

Therefore this slice should:

- extend the existing aggregate relation canonical line
- keep relation-side JPA control aggregate-owned
- avoid introducing a new user-facing source type

## Design Decision

This slice should be a bounded relation-side JPA annotation policy layer.

### Scope Boundary

This slice should cover:

- `ONE_TO_MANY` control:
  - `cascadeAll`
  - `orphanRemoval`
  - `joinColumnNullable = false`
- `MANY_TO_ONE` join-column nullability control
- `ONE_TO_ONE` join-column nullability control

It should not cover:

- `mappedBy`
- `@JoinTable`
- `ManyToMany`
- inverse read-only join-column control
- broader ownership semantics

### Layering

The design should stay split into four bounded responsibilities:

1. bounded canonical relation control metadata
2. relation-planning derivation
3. planner-owned render mapping
4. template-owned mechanical emission

Inference and policy decisions should happen before rendering.

Templates should not decide ownership policy.

## Canonical Model Shape

This slice should not add a new relation-control source model. It should extend the current bounded aggregate relation canonical model.

The existing `AggregateRelationModel` already carries:

- owner entity identity
- target entity identity
- relation type
- join column
- fetch type
- relation nullability

This slice should extend that same model with only the relation-side JPA control fields the current bounded line can safely support.

One acceptable shape is:

```kotlin
data class AggregateRelationModel(
    val ownerEntityName: String,
    val ownerEntityPackageName: String,
    val fieldName: String,
    val targetEntityName: String,
    val targetEntityPackageName: String,
    val relationType: AggregateRelationType,
    val joinColumn: String,
    val fetchType: AggregateFetchType,
    val nullable: Boolean,
    val cascadeAll: Boolean = false,
    val orphanRemoval: Boolean = false,
    val joinColumnNullable: Boolean? = null,
)
```

The important point is not the exact property names. The important point is:

- relation-side control remains attached to the relation model
- the added fields stay bounded to current relation types
- no generalized ownership metadata is introduced for unsupported relation forms

### Why Not Add `mappedBy` or `joinTable` Here

The current canonical line does not yet carry stable support for:

- inverse ownership
- join-table ownership
- many-to-many topology

Adding:

- `mappedBy`
- `joinTable`
- inverse join column metadata

to this slice would effectively reopen relation ownership design rather than extending current bounded relation control.

That belongs to a later slice, not this one.

## Canonical Derivation Rules

This slice may use bounded canonical derivation from already-known relation truth.

### Allowed Derivation

The relation-side control layer may derive:

- for `ONE_TO_MANY`
  - `cascadeAll = true`
  - `orphanRemoval = true`
  - `joinColumnNullable = false`

- for `MANY_TO_ONE`
  - `cascadeAll = false`
  - `orphanRemoval = false`
  - `joinColumnNullable = relation.nullable`

- for `ONE_TO_ONE`
  - `cascadeAll = false`
  - `orphanRemoval = false`
  - `joinColumnNullable = relation.nullable`

This is acceptable because:

- relation type is already canonical truth
- relation nullable is already canonical truth
- the new fields are a bounded relation-side annotation policy derived from those truths

### Not Allowed

This slice should not:

- derive control for unsupported relation types
- infer `mappedBy`
- infer `joinTable`
- infer inverse/read-only ownership semantics
- silently upgrade current relation types into broader ownership models

## Relation Planning Contract

`AggregateRelationPlanning` should become the primary place where relation-side JPA control is prepared for rendering.

It already owns:

- relation field render shape
- target type resolution
- relation-side JPA imports

This slice should extend that same role rather than splitting relation control across multiple ad hoc helpers.

One acceptable relation render shape is:

```kotlin
mapOf(
    "name" to relation.fieldName,
    "targetType" to relation.targetEntityName,
    "targetTypeRef" to targetTypeRef,
    "targetPackageName" to relation.targetEntityPackageName,
    "relationType" to relation.relationType.name,
    "fetchType" to relation.fetchType.name,
    "joinColumn" to relation.joinColumn,
    "nullable" to relation.nullable,
    "cascadeAll" to relation.cascadeAll,
    "orphanRemoval" to relation.orphanRemoval,
    "joinColumnNullable" to relation.joinColumnNullable,
)
```

The exact map keys may vary, but the render contract must expose:

- whether cascade-all applies
- whether orphan-removal applies
- which nullable value should be written into `@JoinColumn`

### Import Gating

`AggregateRelationPlanning` should add:

- `jakarta.persistence.CascadeType`

only when at least one relation render item actually requires it.

It should not broaden JPA imports for unsupported relation controls.

## Entity Planner Contract

`EntityArtifactPlanner` should continue to consume the output of `AggregateRelationPlanning` as the single relation render input for entity templates.

This slice should not move relation-side annotation policy into the entity template itself.

The planner's role here remains:

- pass through the enriched `relationFields`
- pass through the relation-related JPA imports
- preserve the existing scalar field filtering and relation join-column coordination

The planner should not add a separate second relation-control pipeline.

## Template Contract

This slice should continue to use:

- `aggregate/entity.kt.peb`

It should not add a separate relation-control template family.

The template should mechanically emit only what relation planning already resolved.

### Emission Rules

The bounded template contract should be:

1. for `ONE_TO_MANY`
   - emit `cascade = [CascadeType.ALL]`
   - emit `orphanRemoval = true`
   - emit `@JoinColumn(name = "...", nullable = false)`

2. for `MANY_TO_ONE`
   - keep the current `@ManyToOne(fetch = FetchType....)`
   - emit `@JoinColumn(name = "...", nullable = <joinColumnNullable>)`

3. for `ONE_TO_ONE`
   - keep the current `@OneToOne(fetch = FetchType....)`
   - emit `@JoinColumn(name = "...", nullable = <joinColumnNullable>)`

The template should not:

- infer cascade policy
- infer orphan-removal
- infer nullable policy
- synthesize unsupported relation annotations

### Import Rules

This slice should add:

- `jakarta.persistence.CascadeType`

only when at least one `ONE_TO_MANY` relation requires cascade-all rendering.

No additional imports for:

- `mappedBy`
- `JoinTable`
- many-to-many controls

should appear in this slice.

## Validation Strategy

This slice should be locked through four layers.

### 1. Canonical / Relation Planning Unit

Tests should verify:

- `ONE_TO_MANY` is enriched with:
  - `cascadeAll = true`
  - `orphanRemoval = true`
  - `joinColumnNullable = false`
- `MANY_TO_ONE` and `ONE_TO_ONE` preserve bounded nullable behavior through `joinColumnNullable`
- unsupported relation types still fail fast

### 2. Planner

Tests should verify:

- `EntityArtifactPlanner` exposes enriched `relationFields`
- `jpaImports` includes `CascadeType` only when required
- existing scalar field filtering and relation join-column filtering remain intact

### 3. Renderer

Tests should verify:

- `ONE_TO_MANY` renders:
  - `cascade = [CascadeType.ALL]`
  - `orphanRemoval = true`
  - `nullable = false`
- `MANY_TO_ONE` and `ONE_TO_ONE` render explicit `nullable = true/false`
- no `ONE_TO_MANY` means no `CascadeType` import
- no unsupported relation-side controls are emitted

### 4. Functional / Compile

This slice should reuse the existing bounded aggregate relation verification path whenever practical.

Representative verification should cover at least:

1. parent-child `ONE_TO_MANY`
   - generated code includes cascade-all
   - generated code includes orphan-removal
   - generated code includes `nullable = false`

2. direct `MANY_TO_ONE` or `ONE_TO_ONE`
   - generated code includes `@JoinColumn(nullable = true/false)` consistent with canonical relation nullability

Compile verification should prove:

- generated relation entity output still participates in module compilation
- the additional relation-side JPA parameters do not break existing compile closure

## Recommended Fixture Shape

This slice should prefer reusing the existing relation functional and compile fixtures rather than creating a broader new integration sample.

If new fixture coverage is required, it should remain bounded to:

- one parent-child aggregate example
- one nullable or non-nullable direct relation example

The slice does not require a new many-to-many or ownership-heavy fixture.

## Explicit Scope Guards

This slice should fail fast or remain unsupported when asked to do more than its bounded contract.

Examples:

- `mappedBy`
- `@JoinTable`
- `ManyToMany`
- inverse read-only relation-side `insertable/updatable = false`
- broader ownership recovery

The slice should not silently accept broader intent and partially render it.

## Out of Scope for This Slice

This slice explicitly does not include:

- `mappedBy`
- `@JoinTable`
- `ManyToMany`
- inverse/read-only join-column control
- relation inference rewrite
- ownership redesign
- provider-specific fetch tuning expansion
- user-code preservation or merge behavior
- full old-codegen aggregate parity

## Expected Outcome

After this slice:

- aggregate relation output can express the first bounded relation-side JPA control layer on top of the current relation model
- `ONE_TO_MANY` collection relations gain bounded cascade and orphan-removal behavior
- direct `MANY_TO_ONE` and `ONE_TO_ONE` relations gain explicit join-column nullability output
- the pipeline continues to avoid many-to-many recovery and broader ownership redesign until a later explicit slice

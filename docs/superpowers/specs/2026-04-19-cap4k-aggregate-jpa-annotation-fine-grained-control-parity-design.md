# Cap4k Aggregate JPA Annotation Fine-Grained Control Parity

Date: 2026-04-19
Status: Draft for review

## Summary

The bounded aggregate relation line is now complete through:

- relation annotation carriage in the existing `db` source
- bounded aggregate relation canonical inference
- entity-side relation rendering
- representative functional and compile verification

The next explicit framework slice should be:

- `aggregate JPA annotation fine-grained control parity`

This slice should not reopen relation inference or full aggregate semantic recovery.

Instead, it should establish a bounded Jakarta-level JPA baseline for aggregate entity output by:

- extending the existing aggregate-side source and canonical line with minimal JPA-relevant metadata
- letting `EntityArtifactPlanner` consume that metadata without polluting generic `FieldModel`
- enriching `aggregate/entity.kt.peb` with explicit entity/table/column-level JPA annotations

This first JPA-control slice should cover:

- `@Entity`
- `@Table(name = "...")`
- `@Id`
- scalar-field `@Column(name = "...")`
- enum-backed scalar-field `@Convert(converter = Xxx.Converter::class)`

This first slice should explicitly not cover:

- Hibernate-specific controls such as `@DynamicInsert`, `@DynamicUpdate`, `@SQLDelete`, `@Where`, `@GenericGenerator`
- id-generation recovery such as `@GeneratedValue`
- version-field recovery such as `@Version`
- scalar-field insert/update fine control such as `insertable = false`, `updatable = false`
- relation-side JPA control such as `cascade`, `orphanRemoval`, `mappedBy`, `@JoinTable`

## Goals

- Add bounded entity/table/column JPA control parity on top of the now-stable aggregate relation line.
- Keep JPA control metadata inside the existing aggregate source and canonical path.
- Preserve `FieldModel` as a scalar field carrier instead of turning it into a JPA-aware dump model.
- Let `EntityArtifactPlanner` and `aggregate/entity.kt.peb` render a bounded Jakarta baseline without reopening relation semantics.
- Keep the slice compile-verifiable and bounded to aggregate entity output.

## Non-Goals

- Do not add a standalone `jpa` source.
- Do not introduce a new public generator DSL or a general-purpose runtime JPA configuration block.
- Do not reopen relation inference or relation-side JPA ownership decisions.
- Do not restore Hibernate-specific controls in this first slice.
- Do not widen into user-code preservation, soft-delete parity, id-generation parity, or full aggregate semantic recovery.

## Current Context

Old aggregate codegen entity output mixes three different categories of behavior:

1. entity/table JPA baseline
2. scalar-column JPA control
3. relation-side and provider-specific behavior

The old entity generator and template currently cover, among others:

- `@Entity`
- `@Table`
- `@Id`
- `@Column`
- `@Convert`
- `@Version`
- `@GeneratedValue`
- `@GenericGenerator`
- `@DynamicInsert`
- `@DynamicUpdate`
- `@SQLDelete`
- `@Where`
- relation-side `cascade`, `mappedBy`, `@JoinTable`, and fetch/cascade combinations

The new pipeline already has a bounded aggregate line with:

- stable db source carriage
- stable aggregate-side enum ownership
- stable aggregate-side type-reference derivation
- stable first-slice relation parity

But it still lacks a bounded, explicit JPA-control layer for aggregate entities. Today:

- entity rendering knows scalar fields and relation fields
- relation rendering emits first-slice relation annotations
- scalar/entity-level JPA controls are still minimal and incomplete

That means the next stable slice is not "full persistence parity". It is:

- bounded entity/table/column JPA annotation control parity

## Why This Slice Should Stay on the Existing Aggregate Source Line

JPA control truth in the old system does not come from a separate user-authored JPA source file.

It comes from:

- db schema facts
- db comment annotation semantics
- aggregate-side enum ownership and field typing

So this slice should continue to use the existing aggregate line:

- `DbSchemaSourceProvider`
- aggregate canonical assembly
- `EntityArtifactPlanner`
- `aggregate/entity.kt.peb`

Adding a separate JPA source or public `jpa { ... }` DSL in this first slice would create a second truth source before the first bounded contract is stable.

Therefore this slice should:

- extend the existing aggregate source/canonical line
- keep JPA control metadata aggregate-owned
- avoid introducing a new user-facing source type

## Design Decision

This slice should be a bounded Jakarta baseline.

### Scope Boundary

This first slice should cover:

- entity-level JPA activation
- table-name annotation output
- scalar-column annotation output
- enum-converter annotation output

It should not cover:

- provider-specific behavior
- relation-side control expansion
- id-generation recovery
- version/soft-delete/read-only recovery

### Layering

The design should stay split into four bounded responsibilities:

1. source carriage
2. canonical aggregate JPA metadata
3. planner-owned render mapping
4. template-owned mechanical emission

Inference and policy decisions should happen before rendering.

Templates should not guess.

## Source Contract

This slice should continue to hang off the existing `db` source.

It should extend bounded source carriage only where needed for this JPA baseline.

### Table-Level Carriage

The source layer should provide the minimum table-level facts needed for entity/table JPA output, for example:

- stable table name
- any already-supported aggregate/entity enablement signal that is truly source-owned

This slice should not invent new table-level JPA config just to mimic old behavior.

### Column-Level Carriage

The source layer should provide the minimum scalar-column facts needed for this baseline, for example:

- stable physical column name
- stable id-column truth
- stable type-binding or enum-ownership linkage already available on the aggregate line

This slice should not introduce new first-slice carriage for:

- version columns
- generated-value strategy
- read-only or insert/update suppression
- provider-specific behaviors

### Converter Eligibility

Converter eligibility should not be inferred from arbitrary `typeBinding`.

It should only come from types already owned by stable aggregate-side enum contracts:

- shared enum definitions
- aggregate-local enum definitions

That means:

- shared enum fields may be converter-backed
- aggregate-local enum fields may be converter-backed
- ordinary typed fields resolved through `@T` are not automatically converter-backed in this slice

## Canonical Model Shape

This slice should not overload `FieldModel`.

`FieldModel` is still shared across multiple non-JPA lines:

- design requests
- validators
- api payloads
- events
- aggregate scalar fields

Turning it into a broad JPA-aware structure would pollute unrelated lines.

Instead, this slice should add bounded aggregate-owned JPA metadata.

One acceptable shape is a pair of small aggregate-side metadata carriers, for example:

```kotlin
data class AggregateEntityJpaModel(
    val entityEnabled: Boolean,
    val tableName: String,
)

data class AggregateColumnJpaModel(
    val fieldName: String,
    val columnName: String,
    val isId: Boolean,
    val converterTypeFqn: String? = null,
)
```

and aggregate-owned attachment from the canonical model or aggregate entity render model path.

The important constraint is not the exact type name. The important constraint is:

- entity/table/column JPA metadata remains aggregate-owned
- generic `FieldModel` remains scalar-only

## Planner Contract

This slice should primarily change:

- `EntityArtifactPlanner`

No other aggregate family should be forced to consume this metadata in the first slice.

That keeps the work bounded to aggregate entity rendering rather than turning it into a full aggregate JPA sweep.

`EntityArtifactPlanner` should:

- map entity/table JPA metadata into explicit render context
- map scalar-field JPA metadata into explicit render context
- map enum-converter eligibility into explicit render context
- continue to keep relation rendering isolated to the already-stable relation path

The planner should not:

- rediscover whether a field is enum-backed by guessing from arbitrary type names
- infer provider-specific annotations
- infer relation-side control

### Minimum Render Context

The planner should expose enough explicit data for mechanical template output, for example:

- entity:
  - `entityEnabled`
  - `tableName`
- scalar fields:
  - `columnName`
  - `isId`
  - `converterTypeRef`

The precise key names may differ, but the template should not need to reverse-engineer the policy.

## Template Contract

This slice should continue to modify only:

- `aggregate/entity.kt.peb`

It should not introduce a new JPA-specific template family.

The entity template should mechanically emit:

- `@Entity`
- `@Table(name = "...")`
- `@Id`
- `@Column(name = "...")`
- `@Convert(converter = Xxx.Converter::class)` when `converterTypeRef` is present

Relation fields should keep the existing first-slice relation behavior.

This slice should not add:

- `@GeneratedValue`
- `@Version`
- `@DynamicInsert`
- `@DynamicUpdate`
- `@SQLDelete`
- `@Where`
- `cascade`
- `orphanRemoval`
- `mappedBy`
- `@JoinTable`

## Verification Strategy

This slice should use four layers of verification.

### 1. Source / Parser Tests

Verify:

- bounded JPA-relevant carriage reaches the source snapshot
- unsupported first-slice control is not silently half-carried
- enum-converter eligibility only emerges from stable enum ownership paths

### 2. Canonical / Assembler Tests

Verify:

- entity/table JPA metadata enters aggregate-owned canonical state
- scalar-field JPA metadata is attached correctly
- enum-backed fields get converter type information only when ownership is stable
- ordinary typed fields do not accidentally gain converter metadata

### 3. Planner / Renderer Tests

Verify:

- `EntityArtifactPlanner` emits the bounded JPA metadata explicitly
- `aggregate/entity.kt.peb` renders:
  - `@Entity`
  - `@Table`
  - `@Id`
  - `@Column`
  - `@Convert`
- relation rendering is not regressed

### 4. Functional / Compile Tests

Verify with one representative aggregate fixture that:

- generated entity output includes the Jakarta baseline annotations
- enum-backed fields get converter annotations
- generated source still participates in compile verification

If the existing aggregate relation fixture is the cheapest place to lock this, reuse it. A separate fixture is not required if the same bounded outcome can be verified there.

## Why Hibernate-Specific Control Is Deferred

This slice should explicitly defer Hibernate-specific control.

### Deferred Controls

Deferred from this first slice:

- `@DynamicInsert`
- `@DynamicUpdate`
- `@SQLDelete`
- `@Where`
- `@GenericGenerator`

### Reason

These are not just extra annotations.

They depend on deeper, currently unstable aggregate semantics such as:

- soft-delete contract recovery
- version-field recovery
- id-generation recovery
- provider-specific persistence behavior

If these are included now, the slice stops being "bounded JPA annotation parity" and becomes:

- JPA + Hibernate provider behavior parity
- source-semantic recovery
- aggregate behavior recovery

That is too large for the next stable mainline slice.

Therefore the first slice should stay Jakarta-baseline-only.

## Implementation Shape

The implementation should stay bounded to:

- aggregate-side source carriage extensions already justified by the current source line
- aggregate-owned canonical JPA metadata
- `EntityArtifactPlanner`
- `aggregate/entity.kt.peb`
- targeted source / canonical / planner / renderer / functional tests

It should not require:

- a new public DSL
- a new source type
- a new generator family
- a relation re-architecture

## Acceptance Criteria

- bounded entity/table/column JPA control metadata is carried through the existing aggregate source and canonical line
- `FieldModel` remains scalar-only
- `EntityArtifactPlanner` exposes explicit bounded JPA render context
- `aggregate/entity.kt.peb` renders Jakarta baseline annotations for entity/table/scalar-column output
- enum-backed scalar fields get `@Convert` only from stable shared/local enum ownership
- relation-side control remains unchanged from the existing bounded relation slice
- Hibernate-specific control remains explicitly out of scope
- representative functional and compile verification passes

## Decision

The next mainline slice should be:

- `aggregate JPA annotation fine-grained control parity`

The first implementation of that slice should be constrained to:

- entity/table/column Jakarta baseline annotations only

It should deliberately defer:

- Hibernate-specific annotation parity
- relation-side JPA control parity
- id-generation, version, and soft-delete semantic recovery

This keeps the next step compatible with the current mainline rule:

- bounded, explicit, planner-owned framework contracts first
- broader semantic recovery only after the bounded contract is stable

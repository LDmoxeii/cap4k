# Cap4k Aggregate Provider-Specific Persistence Parity

Date: 2026-04-19
Status: Draft for review

## Summary

The bounded aggregate persistence-control line is now complete through:

- entity/table/column Jakarta baseline JPA output
- explicit scalar-field persistence behavior parity for:
  - `@GeneratedValue(strategy = GenerationType.IDENTITY)`
  - `@Version`
  - scalar `@Column(insertable = ..., updatable = ...)`
- representative planner, renderer, functional, and compile verification

The next explicit framework slice should be:

- `aggregate provider-specific persistence parity`

This slice should not reopen relation inference, relation-side JPA control, id-generator semantics, or broad source-semantic recovery.

Instead, it should establish a bounded aggregate-owned provider-specific persistence layer for entity output by:

- extending the existing `db` source carriage with explicit entity-level provider-specific metadata only
- mapping that metadata into bounded aggregate-owned canonical provider controls
- letting `EntityArtifactPlanner` join those controls into render-ready entity context
- enriching `aggregate/entity.kt.peb` with mechanical emission of bounded provider-specific annotations

This slice should cover:

- bounded entity-level `@DynamicInsert`
- bounded entity-level `@DynamicUpdate`
- bounded soft-delete provider annotations:
  - `@SQLDelete`
  - `@Where`

This slice should explicitly not cover:

- `@GenericGenerator`
- relation-side provider-specific behavior
- `ManyToMany`
- custom soft-delete SQL templates or custom delete-marker semantics
- implicit recovery from legacy deleted-field naming conventions

## Goals

- Add bounded provider-specific persistence parity on top of the now-stable aggregate relation, Jakarta baseline JPA, and persistence field-behavior line.
- Keep provider-specific metadata inside the existing aggregate source and canonical path.
- Preserve shared field and entity models as general aggregate carriers instead of turning them into provider-specific dump models.
- Let `EntityArtifactPlanner` and `aggregate/entity.kt.peb` render bounded provider-specific controls without reopening relation ownership or id-generator semantics.
- Keep the slice compile-verifiable and bounded to aggregate entity output.

## Non-Goals

- Do not add a standalone persistence or Hibernate source.
- Do not introduce a new public generator DSL or a general-purpose runtime provider-specific configuration block.
- Do not restore implicit legacy behavior such as deleted-field inference from conventional names.
- Do not restore `@GenericGenerator` or custom id-generator strategy parity in this slice.
- Do not widen into relation-side provider-specific behavior, user-code preservation, or full aggregate semantic recovery.

## Current Context

Old aggregate codegen entity output mixes multiple persistence-related behavior categories:

1. entity/table Jakarta baseline
2. scalar-field persistence behavior
3. provider-specific Hibernate behavior
4. relation-side persistence behavior

The previous mainline slices deliberately stopped at the first two categories plus bounded enum converter output. They explicitly did not recover:

- `@DynamicInsert`
- `@DynamicUpdate`
- `@SQLDelete`
- `@Where`
- `@GenericGenerator`
- relation-side provider-specific behavior

The old entity generator does cover these provider-specific concerns, but it does so together with legacy deleted-field conventions and id-generator behavior. The new pipeline should not reactivate that entire bundle in this slice.

The new pipeline already has a bounded aggregate line with:

- stable `db` source carriage
- stable aggregate-side enum ownership and converter eligibility
- stable aggregate-side type-reference derivation
- stable first-slice relation parity
- stable first-slice entity/table/column Jakarta baseline JPA parity
- stable explicit persistence field-behavior parity

But it still lacks a bounded, explicit provider-specific persistence layer for aggregate entities.

That means the next stable slice is not "full provider parity". It is:

- bounded explicit provider-specific persistence parity

## Why This Slice Should Stay on the Existing Aggregate Source Line

Truth for these controls still belongs to the aggregate-side persistence line. It should continue to flow through:

- `DbSchemaSourceProvider`
- aggregate canonical assembly
- `EntityArtifactPlanner`
- `aggregate/entity.kt.peb`

Adding a separate provider-specific source or public `hibernate { ... }` DSL in this slice would create a second truth source before the first bounded contract is stable.

Therefore this slice should:

- extend the existing aggregate source and canonical line
- keep provider-specific persistence metadata aggregate-owned
- avoid introducing a new user-facing source type

## Design Decision

This slice should be a bounded explicit provider-specific entity-control layer.

### Scope Boundary

This slice should cover:

- explicit table-level carriage and rendering for `@DynamicInsert`
- explicit table-level carriage and rendering for `@DynamicUpdate`
- explicit soft-delete column carriage with bounded `@SQLDelete` and `@Where` composition

It should not cover:

- `@GenericGenerator`
- relation-side provider-specific behavior
- custom soft-delete SQL authoring
- implicit deleted-field inference
- arbitrary provider-specific annotation surface

### Layering

The design should stay split into four bounded responsibilities:

1. source carriage
2. canonical aggregate provider-control metadata
3. planner-owned render mapping and SQL composition
4. template-owned mechanical emission

Inference and policy decisions should happen before rendering.

Templates should not guess or compose SQL strings.

## Source Contract

This slice should continue to hang off the existing `db` source.

It should extend bounded source carriage only where needed for explicit provider-specific entity truth.

### Table-Level Carriage

The source layer should provide the minimum explicit entity-level facts needed for this slice.

One acceptable shape is:

```kotlin
data class DbTableSnapshot(
    val tableName: String,
    val comment: String,
    val columns: List<DbColumnSnapshot>,
    val primaryKey: List<String>,
    val uniqueConstraints: List<List<String>>,
    val parentTable: String? = null,
    val aggregateRoot: Boolean? = null,
    val valueObject: Boolean? = null,
    val dynamicInsert: Boolean? = null,
    val dynamicUpdate: Boolean? = null,
    val softDeleteColumn: String? = null,
)
```

Semantics should be:

- `dynamicInsert = null` means source did not request explicit `@DynamicInsert`
- `dynamicUpdate = null` means source did not request explicit `@DynamicUpdate`
- `softDeleteColumn = null` means source did not request bounded provider-specific soft-delete output

This slice should not infer any of these values from legacy conventions when source does not provide them.

### Explicit Annotation Semantics

This slice should support a minimal explicit annotation surface carried by the existing db comment-annotation line.

One acceptable first-slice contract is:

- `@DynamicInsert=true`
- `@DynamicUpdate=true`
- `@SoftDeleteColumn=deleted`

Equivalent parsing shapes are acceptable if they preserve the same meaning, but the semantics must remain:

- explicit source truth only
- no fallback inference when missing

This slice should not add a wide alias system or a large new annotation vocabulary.

### Soft-Delete Semantics

`softDeleteColumn` should represent the explicit table-local delete marker column used by this slice.

This first slice should fix the generated provider-specific behavior to the bounded default:

- `@SQLDelete` updates the marker column to `1`
- `@Where` filters rows with marker column equal to `0`

This slice should not support:

- custom deleted values
- custom alive values
- custom clause syntax
- custom SQL templates

## Canonical Model Shape

This slice should not overload `EntityModel` or `FieldModel` with provider-specific detail.

Instead, it should add bounded aggregate-owned provider-control metadata.

One acceptable shape is:

```kotlin
data class AggregatePersistenceProviderControl(
    val entityName: String,
    val entityPackageName: String,
    val tableName: String,
    val dynamicInsert: Boolean? = null,
    val dynamicUpdate: Boolean? = null,
    val softDeleteColumn: String? = null,
    val idFieldName: String,
    val versionFieldName: String? = null,
)
```

And `CanonicalModel` should carry:

```kotlin
val aggregatePersistenceProviderControls: List<AggregatePersistenceProviderControl> = emptyList()
```

This keeps:

- aggregate entity structure in `EntityModel`
- provider-specific persistence truth in aggregate-owned canonical metadata
- planner composition explicit and testable

## Assembler Rules

Assembler behavior in this slice should remain bounded.

### Mechanical Mapping

Assembler should map:

- `DbTableSnapshot.dynamicInsert`
- `DbTableSnapshot.dynamicUpdate`
- `DbTableSnapshot.softDeleteColumn`

into aggregate-owned provider controls.

It should also join existing explicit truth already available in the aggregate line:

- resolved entity identity
- table name
- explicit id field
- explicit version field, if one exists

### Allowed Composition

This slice may perform bounded canonical composition, but not unbounded legacy recovery.

Allowed:

- if `softDeleteColumn` is explicit and entity has a version field, carry both truths forward
- if `softDeleteColumn` is explicit and entity has no version field, carry only the soft-delete column and id field

Not allowed:

- infer `softDeleteColumn` from conventional names such as `deleted`, `is_deleted`, or similar
- infer version fields from conventional names
- infer provider-specific controls from general read-only or date-column rules

## Planner Contract

`EntityArtifactPlanner` should join `aggregatePersistenceProviderControls` by:

- `entityName`
- `entityPackageName`

Planner should then produce render-ready entity context.

One acceptable render contract is:

- `dynamicInsert`
- `dynamicUpdate`
- `softDeleteSql`
- `softDeleteWhereClause`

Planner should be responsible for composing `softDeleteSql` and `softDeleteWhereClause`.

Template code should not rebuild SQL from raw fields.

### SQL Composition Rules

If `softDeleteColumn` is present, planner should compose:

- `softDeleteWhereClause = "\"<softDeleteColumn>\" = 0"`

And for `softDeleteSql`:

- with explicit version field:
  - `"update \"<tableName>\" set \"<softDeleteColumn>\" = 1 where \"<idColumn>\" = ? and \"<versionColumn>\" = ?"`
- without explicit version field:
  - `"update \"<tableName>\" set \"<softDeleteColumn>\" = 1 where \"<idColumn>\" = ?"`

This slice should not support custom SQL composition strategies.

## Template Contract

This slice should continue to use:

- `aggregate/entity.kt.peb`

It should not add a separate provider-specific template family.

The template should mechanically emit only what planner already resolved.

### Emission Rules

The bounded template contract should be:

1. emit `@DynamicInsert` only when `dynamicInsert == true`
2. emit `@DynamicUpdate` only when `dynamicUpdate == true`
3. emit `@SQLDelete(sql = "...")` only when `softDeleteSql != null`
4. emit `@Where(clause = "...")` only when `softDeleteWhereClause != null`

`false` and `null` should not produce provider-specific output.

Template logic should not:

- infer missing controls
- synthesize SQL
- inspect raw source annotations
- re-open old deleted-field heuristics

## Validation Strategy

This slice should be locked through four layers.

### 1. Source / Parser

Tests should verify:

- explicit `@DynamicInsert=true` carriage
- explicit `@DynamicUpdate=true` carriage
- explicit `@SoftDeleteColumn=...` carriage
- missing annotations remain `null`
- malformed boolean payloads fail fast

### 2. Canonical / Assembler

Tests should verify:

- provider controls are created only from explicit source truth
- control identity includes entity name and package
- soft-delete control joins correctly with explicit id and version truth
- version-aware and non-version-aware soft-delete control remain distinct

### 3. Planner / Renderer

Tests should verify:

- `EntityArtifactPlanner` exposes `dynamicInsert`, `dynamicUpdate`, `softDeleteSql`, and `softDeleteWhereClause`
- `aggregate/entity.kt.peb` emits `@DynamicInsert` and `@DynamicUpdate` only when explicitly enabled
- `aggregate/entity.kt.peb` emits `@SQLDelete` and `@Where` only when planner provides them
- previously completed Jakarta baseline and field-behavior output remain intact

### 4. Functional / Compile

Representative fixtures should cover at least:

1. entity with explicit `dynamicInsert` and `dynamicUpdate`
2. entity with explicit `softDeleteColumn` and explicit version field
3. entity with explicit `softDeleteColumn` and no version field

Functional assertions should prove generated output contains the expected provider-specific annotations.

Compile verification should prove:

- generated aggregate entity output still participates in module compilation
- provider-specific imports resolve successfully in the compile fixture

## Recommended Fixture Shape

One representative fixture can cover the first two scenarios if it includes:

- one aggregate root with `dynamicInsert` and `dynamicUpdate`
- one aggregate root with explicit `softDeleteColumn`
- one versioned entity and one non-versioned entity where needed

The fixture should remain bounded. This slice does not require a new broad integration sample.

## Explicit Scope Guards

This slice should fail fast or remain unsupported when asked to do more than its bounded contract.

Examples:

- unsupported provider-specific annotations beyond the first bounded set
- implicit deleted-field inference requests
- custom SQL-delete authoring requests
- relation-side provider-specific behaviors
- id-generator/provider behavior such as `@GenericGenerator`

The slice should not silently accept broader intent and then partially drop it.

## Out of Scope for This Slice

This slice explicitly does not include:

- `@GenericGenerator`
- custom generator names or strategies
- `@Fetch` or other relation-side provider behavior
- `cascade`, `orphanRemoval`, `mappedBy`, `@JoinTable`
- `ManyToMany`
- custom soft-delete marker values or custom alive predicates
- relation-side ownership recovery
- user-code preservation or merge behavior
- full old-codegen aggregate parity

## Expected Outcome

After this slice:

- aggregate entity output can express the first bounded provider-specific entity controls through explicit source truth
- provider-specific controls remain aggregate-owned and compile-verifiable
- the pipeline continues to avoid implicit legacy convention recovery
- the remaining aggregate persistence work is narrowed to later slices such as relation-side JPA control, broader provider-specific behavior, or other explicitly reactivated parity gaps

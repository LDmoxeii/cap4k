# Cap4k Aggregate Persistence Field-Behavior Parity

Date: 2026-04-19
Status: Draft for review

## Summary

The bounded aggregate persistence-control line is now complete through:

- entity/table Jakarta baseline JPA output
- scalar-column baseline `@Column(name = "...")`
- enum-backed scalar-field `@Convert(converter = Xxx.Converter::class)`
- representative planner, renderer, functional, and compile verification

The next explicit framework slice should be:

- `aggregate persistence field-behavior parity`

This slice should not reopen relation inference, provider-specific persistence behavior, or broad source-semantic recovery.

Instead, it should establish a bounded aggregate-owned persistence field-behavior layer for entity output by:

- extending the existing `db` source carriage with explicit field-behavior metadata only
- mapping that metadata into bounded aggregate-owned canonical persistence controls
- letting `EntityArtifactPlanner` join those controls into scalar render context
- enriching `aggregate/entity.kt.peb` with explicit `@GeneratedValue`, `@Version`, and scalar `insertable/updatable` emission

This slice should cover:

- bounded id-generation recovery through explicit `@GeneratedValue(strategy = GenerationType.IDENTITY)`
- bounded version-field recovery through explicit `@Version`
- bounded scalar `@Column(insertable = ..., updatable = ...)` control

This slice should explicitly not cover:

- implicit recovery based on DB naming conventions or legacy heuristics
- Hibernate-specific controls such as `@DynamicInsert`, `@DynamicUpdate`, `@SQLDelete`, `@Where`, `@GenericGenerator`
- relation-side persistence control such as relation `insertable/updatable`, `cascade`, `orphanRemoval`, `mappedBy`, `@JoinTable`
- arbitrary generated-value strategies beyond the first bounded supported set

## Goals

- Add bounded persistence field-behavior parity on top of the now-stable aggregate relation and first-slice JPA baseline line.
- Keep persistence field-behavior metadata inside the existing aggregate source and canonical path.
- Preserve `FieldModel` as a scalar field carrier instead of turning it into a persistence-control dump model.
- Let `EntityArtifactPlanner` and `aggregate/entity.kt.peb` render bounded field-behavior controls without reopening relation semantics or provider-specific behavior.
- Keep the slice compile-verifiable and bounded to aggregate entity output.

## Non-Goals

- Do not add a standalone persistence or JPA source.
- Do not introduce a new public generator DSL or a general-purpose runtime persistence configuration block.
- Do not restore implicit legacy behavior such as id-generation inference from single-primary-key tables, version-field inference from conventional column names, or insert/update inference from date-column conventions.
- Do not restore Hibernate-specific controls in this slice.
- Do not widen into relation-side persistence control, user-code preservation, or full aggregate semantic recovery.

## Current Context

Old aggregate codegen entity output mixes multiple persistence-related behavior categories:

1. entity/table Jakarta baseline
2. scalar-field persistence behavior
3. relation-side persistence behavior
4. provider-specific Hibernate behavior

The previous mainline slice deliberately stopped at the first category plus bounded enum converter output. It explicitly did not recover:

- `@GeneratedValue`
- `@Version`
- scalar `insertable/updatable`
- provider-specific controls
- relation-side persistence control

The old entity generator does cover these field-behavior concerns, but it does so partly through implicit legacy heuristics. Examples include:

- default id-generation handling for certain id shapes
- version-field recognition through legacy conventions
- `insertable/updatable` suppression derived from legacy date/read-only semantics

The new pipeline should not reactivate those heuristics in this slice.

The new pipeline already has a bounded aggregate line with:

- stable `db` source carriage
- stable aggregate-side enum ownership and converter eligibility
- stable aggregate-side type-reference derivation
- stable first-slice relation parity
- stable first-slice entity/table/column Jakarta baseline JPA parity

But it still lacks a bounded, explicit persistence field-behavior layer for aggregate entities.

That means the next stable slice is not "full persistence parity". It is:

- bounded explicit persistence field-behavior parity

## Why This Slice Should Stay on the Existing Aggregate Source Line

Truth for these controls still belongs to the aggregate-side persistence line. It should continue to flow through:

- `DbSchemaSourceProvider`
- aggregate canonical assembly
- `EntityArtifactPlanner`
- `aggregate/entity.kt.peb`

Adding a separate persistence source or public `persistence { ... }` DSL in this slice would create a second truth source before the first bounded contract is stable.

Therefore this slice should:

- extend the existing aggregate source and canonical line
- keep persistence field-behavior metadata aggregate-owned
- avoid introducing a new user-facing source type

## Design Decision

This slice should be a bounded explicit field-behavior layer.

### Scope Boundary

This slice should cover:

- explicit id-generation strategy carriage and rendering for the first supported strategy set
- explicit version-field carriage and rendering
- explicit scalar insert/update control carriage and rendering

It should not cover:

- implicit behavior recovery
- provider-specific persistence behavior
- relation-side persistence behavior
- arbitrary generated-value strategy surface

### Layering

The design should stay split into four bounded responsibilities:

1. source carriage
2. canonical aggregate persistence-control metadata
3. planner-owned render mapping
4. template-owned mechanical emission

Inference and policy decisions should happen before rendering.

Templates should not guess.

## Source Contract

This slice should continue to hang off the existing `db` source.

It should extend bounded source carriage only where needed for explicit field-behavior truth.

### Column-Level Carriage

The source layer should provide the minimum explicit scalar-column facts needed for this slice.

One acceptable shape is:

```kotlin
data class DbColumnSnapshot(
    val name: String,
    val dbType: String,
    val kotlinType: String,
    val nullable: Boolean,
    val defaultValue: String? = null,
    val comment: String = "",
    val isPrimaryKey: Boolean = false,
    val typeBinding: String? = null,
    val enumItems: List<EnumItemModel> = emptyList(),
    val referenceTable: String? = null,
    val explicitRelationType: String? = null,
    val lazy: Boolean? = null,
    val countHint: String? = null,
    val generatedValueStrategy: String? = null,
    val version: Boolean = false,
    val insertable: Boolean? = null,
    val updatable: Boolean? = null,
)
```

Semantics should be:

- `generatedValueStrategy = null` means no explicit generated-value control is requested
- `version = false` means the field is not explicitly marked as a version field
- `insertable = null` means source did not request explicit insertability control
- `updatable = null` means source did not request explicit updatability control

This slice should not infer any of these values from legacy conventions when source does not provide them.

### Explicit Annotation Semantics

This slice should support a minimal explicit annotation surface carried by the existing db comment-annotation line.

One acceptable first-slice contract is:

- `@GeneratedValue=IDENTITY`
- `@Version=true`
- `@Insertable=false`
- `@Updatable=false`

Equivalent parsing shapes are acceptable if they preserve the same meaning, but the semantics must remain:

- explicit source truth only
- no fallback inference when missing

This slice should not add a wide alias system or a large new annotation vocabulary.

### Supported Generated-Value Strategy Set

This slice should support only a bounded first strategy set.

Recommended first-slice support:

- `IDENTITY`

If source requests an unsupported strategy in this slice, the pipeline should fail fast with an explicit error rather than silently dropping the request or guessing alternate behavior.

## Canonical Model Shape

This slice should not overload `FieldModel`.

`FieldModel` is still shared across multiple non-persistence lines:

- design requests
- validators
- api payloads
- events
- aggregate scalar fields

Turning it into a broad persistence-control structure would pollute unrelated lines.

Instead, this slice should add bounded aggregate-owned persistence-control metadata.

One acceptable shape is:

```kotlin
data class AggregatePersistenceFieldControl(
    val entityName: String,
    val fieldName: String,
    val columnName: String,
    val generatedValueStrategy: String? = null,
    val version: Boolean = false,
    val insertable: Boolean? = null,
    val updatable: Boolean? = null,
)
```

And `CanonicalModel` should carry:

```kotlin
val aggregatePersistenceFieldControls: List<AggregatePersistenceFieldControl> = emptyList()
```

This keeps:

- scalar field structure in `FieldModel`
- persistence-control truth in aggregate-owned canonical metadata
- aggregate-side planner joins explicit and testable

## Assembler Rules

Assembler behavior in this slice should be mechanical.

It should:

- read explicit `generatedValueStrategy`
- read explicit `version`
- read explicit `insertable`
- read explicit `updatable`
- map those values into `AggregatePersistenceFieldControl`

It should not:

- infer id generation from id shape
- infer version fields from conventional names
- infer insert/update control from legacy date-column heuristics
- infer provider-specific behavior

This slice should preserve the earlier mainline rule:

- when source does not say it, the pipeline does not guess it

## Planner Contract

This slice should remain inside the existing aggregate generator and continue to center on `EntityArtifactPlanner`.

`EntityArtifactPlanner` should:

- consume the existing scalar field render inputs
- join `AggregatePersistenceFieldControl` by entity and field identity
- produce a bounded persistence-aware scalar render context

One acceptable render shape is to enrich scalar field context with:

- `generatedValueStrategy`
- `isVersion`
- `insertable`
- `updatable`

Planner behavior should stay mechanical and explicit.

In particular:

- `@GeneratedValue` should only be emitted when `generatedValueStrategy` is present and the field is the id field
- `@Version` should only be emitted when `isVersion` is true
- if either `insertable` or `updatable` is explicitly controlled, planner should provide final concrete boolean values for both sides

That last rule matters because templates should not guess defaults.

For example:

- if source only says `insertable = false`
- planner should still pass `insertable = false` and `updatable = true`

instead of forcing the template to invent the missing side.

## Renderer Contract

This slice should continue to use the existing:

- `aggregate/entity.kt.peb`

It should not add a separate persistence-specific template family.

### Mechanical Emission Rules

The template should emit:

1. `@GeneratedValue`
- only when planner provides a supported `generatedValueStrategy`
- first slice should render:
  - `@GeneratedValue(strategy = GenerationType.IDENTITY)`

2. `@Version`
- only when planner provides `isVersion = true`

3. `@Column`
- keep the current compact form when no explicit insert/update control is present
- expand to the explicit parameter form when either `insertable` or `updatable` is controlled

The renderer should not:

- guess unsupported generation strategies
- infer missing insert/update values
- recover provider-specific or relation-side behavior

### Imports

This slice should add only the bounded Jakarta imports needed by the supported output, such as:

- `jakarta.persistence.GeneratedValue`
- `jakarta.persistence.GenerationType`
- `jakarta.persistence.Version`

Those imports should appear only when their corresponding annotations are actually emitted.

## Validation Strategy

This slice should be validated in four layers.

### 1. Source / Parser

Lock explicit source carriage for:

- `@GeneratedValue=IDENTITY`
- `@Version=true`
- `@Insertable=false`
- `@Updatable=false`

Also lock fail-fast behavior for unsupported generated-value strategies in this slice.

### 2. Canonical / Assembler

Lock that:

- explicit column-level metadata maps into `AggregatePersistenceFieldControl`
- missing source truth does not create controls
- assembler remains mechanical and does not recover legacy implicit conventions

### 3. Planner / Renderer

Lock that:

- id fields with supported generated-value strategy render `@GeneratedValue(strategy = GenerationType.IDENTITY)`
- explicit version fields render `@Version`
- scalar fields with explicit insert/update controls render expanded `@Column(...)`
- ordinary scalar fields keep the simpler current output
- unsupported strategy requests fail before rendering

### 4. Functional / Compile

Use a representative aggregate fixture that includes:

- one identity-generated id field
- one explicit version field
- one scalar field with `insertable = false`
- one scalar field with `updatable = false`

Functional verification should prove:

- `cap4kPlan` exposes the expected aggregate entity template usage
- `cap4kGenerate` emits the expected annotations
- generated entity output participates in compile verification

## Explicit Non-Goals for This Slice

This slice should not:

- infer `@GeneratedValue` from single-primary-key tables
- infer `@Version` from column naming conventions
- infer insert/update suppression from date-column conventions, read-only conventions, or legacy auto-time semantics
- support `@GenericGenerator`
- support Hibernate/provider-specific persistence annotations
- support relation-side `insertable/updatable = false`
- reopen relation ownership, relation-side control, or broader source-semantic recovery

## Recommended Outcome

When this slice is complete, the aggregate entity line should have:

- explicit source-owned field-behavior truth
- aggregate-owned canonical persistence-control metadata
- planner-owned deterministic render mapping
- bounded entity template emission for `@GeneratedValue`, `@Version`, and scalar insert/update control
- representative functional and compile verification

But it should still intentionally leave for later slices:

- provider-specific persistence behavior
- relation-side persistence control
- broader aggregate semantic recovery

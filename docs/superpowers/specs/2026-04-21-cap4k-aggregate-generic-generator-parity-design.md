# Cap4k Aggregate Generic-Generator Parity

Date: 2026-04-21
Status: Draft for review

## Summary

The bounded aggregate persistence-control line is now complete through:

- entity/table/column Jakarta baseline JPA output
- explicit scalar-field persistence behavior parity for:
  - `@GeneratedValue(strategy = GenerationType.IDENTITY)`
  - `@Version`
  - scalar `@Column(insertable = ..., updatable = ...)`
- bounded provider-specific entity behavior parity for:
  - `@DynamicInsert`
  - `@DynamicUpdate`
  - `@SQLDelete`
  - `@Where`
- representative planner, renderer, functional, and compile verification

The next explicit framework slice should be:

- `aggregate generic-generator parity`

This slice should not reopen relation ownership, provider-specific soft-delete behavior, broad id-generation redesign, or source-semantic recovery.

Instead, it should establish a bounded aggregate-owned custom id-generator layer for entity output by:

- extending the existing `db` source carriage with explicit table-level `entityIdGenerator` truth only
- mapping that truth into bounded aggregate-owned canonical id-generator controls
- letting `EntityArtifactPlanner` join those controls into id-field render context
- enriching `aggregate/entity.kt.peb` with mechanical emission of bounded `@GeneratedValue(generator = "...")` and `@GenericGenerator(...)`

This slice should cover:

- explicit table-level custom id-generator truth
- bounded id-field output for:
  - `@GeneratedValue(generator = "...")`
  - `@GenericGenerator(name = "...", strategy = "...")`
- continued fallback to the already-stable `IDENTITY` path when no explicit custom generator truth is present

This slice should explicitly not cover:

- `SEQUENCE`
- `TABLE`
- generator parameter maps
- separate generator `name` vs `strategy` configuration
- public DSL for provider-specific id generation
- relation-side behavior or broader provider-specific recovery

## Goals

- Add bounded custom id-generator parity on top of the now-stable aggregate persistence and relation line.
- Keep custom id-generator metadata inside the existing aggregate source and canonical path.
- Preserve shared field and entity models as general aggregate carriers instead of turning them into id-generation dump models.
- Let `EntityArtifactPlanner` and `aggregate/entity.kt.peb` render bounded custom generator controls without reopening full id-generation semantics.
- Keep the slice compile-verifiable and bounded to aggregate entity output.

## Non-Goals

- Do not add a standalone id-generation or provider source.
- Do not introduce a new public generator DSL or a general-purpose runtime id-generator configuration block.
- Do not restore implicit legacy behavior such as inferring custom generators from table names, field names, or old task-level defaults.
- Do not widen into `SEQUENCE`, `TABLE`, composite-id generator support, generator params, user-code preservation, or full aggregate semantic recovery.

## Current Context

Old aggregate entity generation contains two distinct id-generation paths:

1. the bounded default path:
   - `@GeneratedValue(strategy = GenerationType.IDENTITY)`
2. the explicit custom path:
   - `@GeneratedValue(generator = "snowflakeIdGenerator")`
   - `@GenericGenerator(name = "snowflakeIdGenerator", strategy = "snowflakeIdGenerator")`

The previous mainline slices deliberately recovered only the first path. They explicitly did not recover:

- `@GenericGenerator`
- custom generator-backed `@GeneratedValue(generator = "...")`
- broader custom id-generation semantics

The old entity generator does support a bounded custom generator path, but it does so inside a much larger legacy persistence bundle. The new pipeline should not reactivate that entire bundle in this slice.

The new pipeline already has a bounded aggregate line with:

- stable `db` source carriage
- stable aggregate-side enum ownership and converter eligibility
- stable aggregate-side type-reference derivation
- stable relation parity through inverse/read-only `*ManyToOne`
- stable explicit persistence field-behavior parity
- stable provider-specific persistence parity

But it still lacks a bounded, explicit custom id-generator layer for aggregate entities.

That means the next stable slice is not "full id-generation parity". It is:

- bounded explicit custom generator parity

## Why This Slice Should Stay on the Existing Aggregate Source Line

Truth for custom id-generator behavior still belongs to the aggregate-side persistence line. It should continue to flow through:

- `DbSchemaSourceProvider`
- aggregate canonical assembly
- `EntityArtifactPlanner`
- `aggregate/entity.kt.peb`

Adding a separate id-generator source or public `persistence { idGenerator(...) }` DSL in this slice would create a second truth source before the first bounded contract is stable.

Therefore this slice should:

- extend the existing aggregate source and canonical line
- keep custom id-generator metadata aggregate-owned
- avoid introducing a new user-facing source type

## Design Decision

This slice should be a bounded explicit custom id-generator layer.

### Scope Boundary

This slice should cover:

- explicit table-level `entityIdGenerator` truth
- bounded aggregate-owned canonical controls for custom id-generator output
- planner-level expansion into render-ready id-field generator keys
- mechanical renderer output for `@GeneratedValue(generator = "...")` plus `@GenericGenerator(...)`
- fallback to the existing `IDENTITY` path when explicit custom generator truth is absent

This slice should not cover:

- `SEQUENCE`
- `TABLE`
- `@SequenceGenerator`
- custom generator parameter bags
- separate `name` and `strategy` source inputs
- composite-key generator semantics
- value-object generator semantics

## Source Contract

This slice should stay on the existing `db` source line and add only one bounded table-level carriage field:

```kotlin
data class DbTableSnapshot(
    ...
    val entityIdGenerator: String? = null,
)
```

The first bounded source truth should be explicit table-level annotation carriage only:

- `@IdGenerator=snowflakeIdGenerator`

Semantics:

- `null` means source provided no custom generator truth
- non-blank string means source explicitly wants custom generator-backed id output
- blank or whitespace-only generator ids should fail fast during parsing or canonical validation

This slice should not introduce:

- alias-heavy annotation vocabulary
- split `name` and `strategy` inputs
- generator params
- implicit inference from legacy task defaults or naming conventions

## Aggregate-Owned Canonical Metadata

This slice should not push custom id-generator behavior into shared `FieldModel` or `EntityModel`.

It should add a bounded aggregate-owned control slice instead:

```kotlin
data class AggregateIdGeneratorControl(
    val entityName: String,
    val entityPackageName: String,
    val tableName: String,
    val idFieldName: String,
    val entityIdGenerator: String,
)
```

`CanonicalModel` should add:

```kotlin
val aggregateIdGeneratorControls: List<AggregateIdGeneratorControl> = emptyList()
```

This keeps custom id-generator semantics:

- aggregate-owned
- explicit
- planner-consumable
- isolated from unrelated design and shared field lines

## Canonical Assembly Rules

Canonical assembly in this slice should remain mechanical, with only bounded eligibility gates.

It should create `AggregateIdGeneratorControl` only when all of the following are true:

1. the table/entity has explicit non-blank `entityIdGenerator` truth
2. the entity has a single primary-key field
3. the entity is not a value object

Canonical assembly should explicitly not create control when:

- the entity has no explicit generator truth
- the entity is a value object
- the entity has composite ids

The assembler should fail fast for:

- blank or whitespace-only `entityIdGenerator`
- ambiguous entity/id-field resolution if source truth points at a table that does not assemble into a bounded aggregate entity shape

The assembler should not:

- infer a generator id
- derive generator name from table/entity names
- invent fallback custom-generator behavior

## Planner Contract

`EntityArtifactPlanner` should join `AggregateIdGeneratorControl` onto the existing id-field render context.

The first bounded planner output should expand one explicit source string into three render-ready keys:

- `field.generatedValueGenerator`
- `field.genericGeneratorName`
- `field.genericGeneratorStrategy`

In the first slice, all three keys should carry the same string value from `entityIdGenerator`.

Example:

- source truth: `snowflakeIdGenerator`
- planner output:
  - `generatedValueGenerator = "snowflakeIdGenerator"`
  - `genericGeneratorName = "snowflakeIdGenerator"`
  - `genericGeneratorStrategy = "snowflakeIdGenerator"`

This may look redundant, but it is intentional. It keeps template behavior mechanical and avoids copying binding rules into the renderer.

The planner must also preserve the already-stable identity path.

That means id-field planning should become a bounded two-way split:

1. custom generator path
   - `generatedValueGenerator != null`
   - `genericGeneratorName != null`
   - `genericGeneratorStrategy != null`
   - `generatedValueStrategy` should not render `IDENTITY`
2. default identity path
   - `generatedValueGenerator == null`
   - no generic-generator keys
   - existing `generatedValueStrategy == "IDENTITY"` behavior remains unchanged

These paths must be mutually exclusive. This slice must not allow the same id field to render:

- `@GeneratedValue(strategy = GenerationType.IDENTITY)`
- and `@GeneratedValue(generator = "...")`

at the same time.

## Template Contract

This slice should continue to modify only:

- `aggregate/entity.kt.peb`

It should not add a dedicated id-generator template or a separate aggregate family template.

### Render Keys

The template should consume only the planner-provided render-ready keys:

- `field.generatedValueGenerator`
- `field.genericGeneratorName`
- `field.genericGeneratorStrategy`

### Output Rules

For id fields, template behavior should be:

1. If `field.generatedValueGenerator != null`
   - emit:
     - `@GeneratedValue(generator = "...")`
     - `@GenericGenerator(name = "...", strategy = "...")`
2. Else if `field.generatedValueStrategy == "IDENTITY"`
   - emit:
     - `@GeneratedValue(strategy = GenerationType.IDENTITY)`
3. Else
   - emit no generated-value annotation beyond the already-supported non-generated cases

The template must not perform any generator inference.

It must not:

- derive `name` from `strategy`
- derive `strategy` from `name`
- guess fallback custom generators
- mix custom-generator and identity annotations on the same field

### Import Rules

This slice should add bounded provider import support for:

- `org.hibernate.annotations.GenericGenerator`

Import emission should remain conditional. The import should appear only when the rendered entity contains at least one id field on the custom generator path.

The slice should preserve existing `GeneratedValue` and `GenerationType` import behavior for the identity path.

## Validation Strategy

This slice should be validated across four layers.

### 1. Source / Parser Tests

Lock:

- explicit table-level `@IdGenerator=snowflakeIdGenerator` carriage enters `DbTableSnapshot.entityIdGenerator`
- omitted annotation keeps `entityIdGenerator == null`
- blank or whitespace-only values fail fast

### 2. Canonical / Assembler Tests

Lock:

- single-primary-key non-value-object entity with explicit generator truth produces `AggregateIdGeneratorControl`
- entity without explicit truth does not produce control
- value object with explicit generator truth does not produce control
- composite-id entity with explicit generator truth fails fast or is rejected by bounded validation

The preferred bounded behavior is fail-fast, not silent partial support.

### 3. Planner / Renderer Tests

Lock:

- custom-generator id field renders:
  - `@GeneratedValue(generator = "...")`
  - `@GenericGenerator(name = "...", strategy = "...")`
- custom-generator path does not also render `IDENTITY`
- default identity path still renders:
  - `@GeneratedValue(strategy = GenerationType.IDENTITY)`
- default identity path does not render `@GenericGenerator`
- `org.hibernate.annotations.GenericGenerator` import appears only when needed

The current negative assertions that forbid all `@GenericGenerator` output must be narrowed.

After this slice:

- default fixtures should still assert absence of `@GenericGenerator`
- dedicated custom-generator fixtures should assert presence of `@GenericGenerator`

### 4. Functional / Compile Tests

Representative functional coverage should include at least:

1. custom generator entity
   - explicit `@IdGenerator=snowflakeIdGenerator`
   - single id
   - non-value-object
   - generated entity contains:
     - `@GeneratedValue(generator = "snowflakeIdGenerator")`
     - `@GenericGenerator(name = "snowflakeIdGenerator", strategy = "snowflakeIdGenerator")`
2. default identity entity
   - no explicit `@IdGenerator`
   - generated entity still contains:
     - `@GeneratedValue(strategy = GenerationType.IDENTITY)`
   - generated entity does not contain `@GenericGenerator`

Compile verification should prove:

- the custom-generator path compiles
- the default identity path still compiles
- both paths can coexist within the same representative fixture without import or annotation collisions

## Failure Boundaries

This slice should fail fast for unsupported or ambiguous input rather than silently approximating behavior.

Fail-fast cases should include:

- blank `entityIdGenerator`
- composite-id entity with explicit custom generator truth
- any future source shape that attempts to provide split generator `name` / `strategy` data in this bounded slice

This slice should not silently downgrade explicit custom-generator truth back to `IDENTITY`.

If source explicitly asks for a bounded custom generator path and the entity is not eligible, the pipeline should stop with a clear message.

## Implementation Notes

This slice is intentionally narrow because old `@GenericGenerator` behavior sits at the boundary between:

- id-generation semantics
- provider-specific persistence behavior

The previous slice recovered provider-specific entity annotations while explicitly excluding `@GenericGenerator` because it would have reopened id-generation semantics.

This slice now recovers only the smallest stable custom-generator behavior still missing from that line:

- one explicit source string
- one bounded aggregate-owned canonical control
- one mutually-exclusive planner split between custom generator and identity
- one mechanical template emission path

That is enough to close the old bounded custom-generator gap without turning this into a full id-generation redesign.

## Explicit Non-Restoration

This slice must not restore:

- `SEQUENCE`
- `TABLE`
- `@SequenceGenerator`
- generator parameter maps
- provider-specific custom-generator tuning beyond `name == strategy == entityIdGenerator`
- public DSL for configuring generator strategies
- any implicit legacy generator inference

These concerns belong to later work, if they are ever justified at all.

## Acceptance Criteria

This slice is complete when all of the following are true:

- the `db` source can carry explicit bounded table-level `entityIdGenerator` truth
- aggregate canonical assembly produces bounded `AggregateIdGeneratorControl`
- `EntityArtifactPlanner` exposes mutually-exclusive custom-generator vs identity render paths
- `aggregate/entity.kt.peb` renders bounded `@GeneratedValue(generator = "...")` and `@GenericGenerator(...)`
- representative renderer, functional, and compile tests cover both the custom-generator path and the preserved identity path

## Expected Outcome

After this slice, the aggregate persistence line should be complete through:

- Jakarta baseline entity/table/column JPA parity
- bounded scalar persistence field-behavior parity
- bounded provider-specific entity persistence parity
- bounded custom id-generator parity

At that point, the remaining aggregate persistence debt should be smaller and more clearly separated from this line, rather than still mixing:

- custom generator semantics
- relation-side ownership
- advanced relation forms
- many-to-many support

into one unresolved bucket.

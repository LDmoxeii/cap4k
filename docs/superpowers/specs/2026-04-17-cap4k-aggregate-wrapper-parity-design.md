# Cap4k Aggregate Wrapper Parity

Date: 2026-04-17
Status: Draft for review

## Summary

The bounded bootstrap line is complete, the first cross-generator reference boundary line is complete, and the first aggregate-side consumer line is now complete through factory/specification parity.

The next explicit framework slice should be:

- `aggregate wrapper parity`

This slice should keep working inside the existing `aggregate` generator.

It should add the old aggregate wrapper family as a bounded parity step that consumes:

- the now-stable aggregate derived type-reference boundary
- the newly landed aggregate factory parity

It should not restore old source semantics such as:

- `generateAggregate`
- `aggregateTypeTemplate`

and it should not widen into full aggregate-side parity.

## Goals

- Extend the existing pipeline `aggregate` generator with bounded wrapper parity.
- Preserve old wrapper default naming and default package placement.
- Reuse the unified aggregate derived type-reference boundary instead of reintroducing ad hoc string guessing or mutable `typeMapping`.
- Consume the already-landed aggregate factory parity through planner-owned wrapper context.
- Add planner, renderer, and bounded functional or compile verification for representative wrapper output.

## Non-Goals

- Do not restore `generateAggregate`.
- Do not restore `aggregateTypeTemplate`.
- Do not add a new public DSL such as `aggregateWrapper`.
- Do not redesign aggregate-side package layout.
- Do not expand into unique-query parity, unique-validator parity, relation parity, or JPA annotation parity.
- Do not widen aggregate template behavior toward design-style helper authority.

## Current Context

Old codegen already has an aggregate wrapper family through `AggregateWrapperGenerator`.

That generator currently depends on:

- the aggregate root entity type
- the aggregate factory type
- the identity type
- the wrapper type name

The old default naming is:

- `Agg<Entity>`

Example:

- `VideoPost` -> `AggVideoPost`

The old template shape is relatively small:

- wrapper class extends `Aggregate.Default<Entity>`
- constructor accepts `Factory.Payload?`
- nested `Id` type extends `Aggregate.Id.Default<Wrapper, IdType>`

That means this slice is a better next step than jumping straight to:

- unique-query parity
- unique-validator parity
- relation/JPA parity

because wrapper parity is still a narrow aggregate-side domain family with mostly stable inputs.

## Why This Slice Exists

The repository now has:

- aggregate factory parity
- aggregate specification parity
- a unified aggregate derived-reference rule that is collision-safe for duplicate simple names

That makes wrapper parity the first remaining aggregate-side family that can be added without reopening:

- source semantic recovery
- custom naming DSL
- global runtime registration

It also provides a natural consumer of factory parity, which helps prove the aggregate-side parity line is internally coherent rather than a collection of disconnected template additions.

## Design Decision

The repository should implement wrapper parity by extending the existing `aggregate` generator.

It should not introduce:

- a new public generator
- a new source block
- a wrapper-specific naming DSL

Recommended shape:

- keep generator id = `aggregate`
- add one new aggregate family planner
- add one new aggregate preset template
- derive wrapper type names and factory references deterministically from current entity and factory conventions

This keeps the slice aligned with the same aggregate-side strategy already used for:

- schema
- entity
- repository
- factory
- specification

## Public Contract

This slice should not add any new user-facing DSL.

Users should continue enabling wrapper generation only by enabling the existing aggregate generator.

The contract change is internal but durable:

- the `aggregate` generator now includes wrapper parity

## Canonical Boundary

This slice should not extend `CanonicalModel` with a new wrapper model.

It should consume:

- `CanonicalModel.entities`

and derive wrapper artifacts directly from entity models plus deterministic factory naming.

It should also avoid expanding source semantics.

It should not restore:

- `generateAggregate`
- `aggregateTypeTemplate`

Those remain deferred aggregate-side parity questions, not part of this bounded slice.

## Naming And Layout Contract

This slice should preserve old default wrapper naming.

For each entity:

- wrapper type name = `Agg<Entity>`

Examples:

- `VideoPost` -> `AggVideoPost`
- `Order` -> `AggOrder`

This slice should not expose wrapper naming customization.

Default package layout should also stay aligned with old default placement:

- wrapper package = `<entity package>`

Default output path:

- `.../<entity package path>/Agg<Entity>.kt`

Example:

- `demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/AggVideoPost.kt`

This avoids turning wrapper parity into an aggregate-side layout redesign slice.

## Planner Contract

The aggregate generator should gain one additional family planner:

- `AggregateWrapperArtifactPlanner`

It should be added to the existing `AggregateArtifactPlanner` delegate list alongside:

- schema
- entity
- repository
- factory
- specification

The wrapper planner should consume `CanonicalModel.entities` and emit domain-module artifacts.

### Wrapper Planner Context

The wrapper planner should provide at least:

- `packageName`
- `typeName`
- `entityName`
- `entityTypeFqn`
- `factoryTypeName`
- `factoryTypeFqn`
- `idType`
- `comment`

Recommended deterministic rules:

- `typeName = "Agg${entity.name}"`
- `factoryTypeName = "${entity.name}Factory"`
- `factoryTypeFqn = "${entity.packageName}.factory.${entity.name}Factory"` when package is non-blank
- `factoryTypeFqn = "${entity.name}Factory"` when package is blank
- `idType = entity.idField.type`

### Type-Reference Rule

This slice should keep all three wrapper-critical type references planner-owned and deterministic:

- `entityTypeFqn`
- `factoryTypeFqn`
- `idType`

The wrapper template should consume these values directly.

It should not guess:

- where the factory lives
- what the wrapper type should be called
- what the identity type should be

inside Pebble.

## Template Contract

This slice should add one bounded preset template under the existing aggregate preset:

- `ddd-default/aggregate/wrapper.kt.peb`

Aggregate templates must continue following the current aggregate renderer boundary:

- no `use()` helper
- no design-template helper contract migration
- explicit imports only where needed

### Wrapper Template Shape

The wrapper template should preserve the old class shape closely:

- `class Agg<Entity>(payload: <Entity>Factory.Payload? = null) : Aggregate.Default<Entity>(payload)`
- `val id by lazy { root.id }`
- nested `class Id(key: <IdType>) : Aggregate.Id.Default<Agg<Entity>, <IdType>>(key)`

This first wrapper slice should remain compile-safe and intentionally minimal.

It should not add:

- richer aggregate behavior methods
- wrapper-specific custom lifecycle logic
- custom naming hooks

The point is bounded parity, not a new aggregate abstraction layer.

## Validation Strategy

This slice should validate at four levels.

### 1. Planner / Unit

Planner coverage should lock:

- wrapper output path
- preserved package placement
- deterministic wrapper type naming
- deterministic factory type naming
- planner-owned `entityTypeFqn`
- planner-owned `factoryTypeFqn`
- planner-owned `idType`

It should also verify that duplicate simple entity names do not reintroduce ambiguous wrapper references.

### 2. Renderer

Renderer coverage should prove:

- preset fallback works for `aggregate/wrapper.kt.peb`
- output contains the expected aggregate wrapper inheritance contract
- explicit import rendering works without aggregate-side `use()` helper support
- wrapper output consumes `factoryTypeFqn` rather than guessing factory package in the template

### 3. Functional

Functional coverage should reuse the existing aggregate sample and extend it to include:

- `cap4kPlan` contains `aggregate/wrapper.kt.peb`
- `cap4kGenerate` writes `Agg<Entity>.kt` into the expected domain package
- representative wrapper content matches the bounded parity contract

### 4. Compile-Level Check

This slice should extend the current aggregate compile fixture rather than inventing a second compile harness.

The compile gate should prove that generated wrapper, factory, and entity types can participate together in:

- `:demo-domain:compileKotlin`

The target remains bounded:

- compile-safe representative wrapper parity
- not full aggregate parity

## Success Criteria

This slice is successful when:

- the `aggregate` generator plans wrapper artifacts without adding new public DSL
- wrapper naming is fixed to the old default `Agg<Entity>` rule
- wrapper planner context uses deterministic entity/factory references instead of mutable runtime `typeMapping`
- the default aggregate preset contains a compile-safe wrapper template
- planner, renderer, functional, and compile verification lock wrapper parity in place
- aggregate-side parity continues to progress without reopening source semantic recovery

## Explicit Non-Goals Reaffirmed

This slice must not quietly drift into:

- `generateAggregate` recovery
- `aggregateTypeTemplate` recovery
- aggregate wrapper naming customization
- unique-query parity
- unique-validator parity
- relation/JPA parity
- aggregate-side layout redesign

## Recommended Next Step

After this spec is approved, the implementation plan should stay focused on:

1. adding the wrapper planner
2. adding the wrapper preset template
3. consuming deterministic factory/entity/id references through planner context
4. extending aggregate functional and compile closure to include the wrapper family

That keeps the slice narrow enough to implement while continuing the aggregate-side parity line on stable terms.

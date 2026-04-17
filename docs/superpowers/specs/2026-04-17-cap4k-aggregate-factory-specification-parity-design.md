# Cap4k Aggregate Factory / Specification Parity

Date: 2026-04-17
Status: Draft for review

## Summary

The design-generator quality line is complete, the bounded bootstrap line is complete, and the first cross-generator reference boundary slice is complete through immutable derived type references.

The next explicit framework slice should be:

- `aggregate factory / specification parity`

This slice should not reopen full aggregate-side parity.

It should consume the new deterministic aggregate type-reference boundary and extend the existing `aggregate` generator with two additional bounded families:

- `aggregate/factory.kt.peb`
- `aggregate/specification.kt.peb`

The slice should preserve old family naming and package layout, but it should not restore old mutable `typeMapping`, old source-side `generateFactory` / `generateSpecification` flags, or old aggregate-side open-ended parity behavior.

## Goals

- Extend the existing pipeline `aggregate` generator with bounded factory and specification parity.
- Consume the newly introduced immutable aggregate derived type references through planner-owned context.
- Preserve old family naming and package layout for factory and specification outputs.
- Keep the implementation inside the existing aggregate generator instead of adding new public DSL.
- Add planner, renderer, and functional coverage that locks the boundary in place.
- Reuse compile-level verification infrastructure where it provides a bounded aggregate-side compile check.

## Non-Goals

- Do not restore old `generateFactory` or `generateSpecification` source semantics.
- Do not restore table-comment or annotation-driven factory/specification gating.
- Do not expand this slice into aggregate wrapper parity, unique-query parity, or unique-validator parity.
- Do not reopen relation parity, JPA annotation parity, or user-code-preservation parity.
- Do not redesign aggregate-side package layout or rename the families.
- Do not reintroduce mutable shared runtime `typeMapping`.
- Do not add a new aggregate-specific public generator DSL such as `aggregateFactory` or `aggregateSpecification`.

## Current Context

Old codegen already defines both families:

- `FactoryGenerator`
- `SpecificationGenerator`

They are aggregate-root-oriented, domain-side outputs with deterministic naming:

- `<Entity>Factory`
- `<Entity>Specification`

They also depend on entity type resolution that old codegen currently obtains from shared mutable `typeMapping`.

The current pipeline aggregate generator only plans:

- `aggregate/schema.kt.peb`
- `aggregate/entity.kt.peb`
- `aggregate/repository.kt.peb`

The previous `cross-generator type-reference parity` slice added a first immutable derived-reference layer on the aggregate side and surfaced:

- `entityTypeFqn`
- `qEntityTypeFqn`

into schema planner context.

That means the next bounded step is not a new global registry.

It is to consume that boundary in the first remaining aggregate-side parity families that still need explicit entity-type references.

## Why This Slice Exists

The previous slice settled the rule:

- explicit FQN
- project registry
- deterministic derivation
- never mutable shared runtime registration by default

This slice applies that rule to the first aggregate-side parity targets that still matter:

- factory generation
- specification generation

Without this slice, the repository would have a type-reference boundary with no real aggregate-side consumer except schema context.

That would leave the parity line half-settled.

## Design Decision

The repository should implement factory/specification parity by extending the existing `aggregate` generator.

It should not introduce:

- a new public DSL
- a new aggregate sub-pipeline
- a shared mutable generator registry

Recommended shape:

- keep generator id = `aggregate`
- add two aggregate family planners
- add two aggregate preset templates
- derive output names and package names deterministically from `CanonicalModel.entities`

This keeps the slice aligned with the current aggregate generator boundary instead of splitting domain-side aggregate families into extra public toggles with weak justification.

## Public Contract

This slice should not add new user-facing DSL.

Users should continue enabling the existing aggregate generator through the current pipeline configuration.

The contract change is internal but durable:

- the `aggregate` generator now includes factory parity
- the `aggregate` generator now includes specification parity

This remains bounded by the existing aggregate module requirements.

## Canonical Boundary

This slice should not extend `CanonicalModel` with new `FactoryModel` or `SpecificationModel` types.

It should consume the existing:

- `CanonicalModel.entities`

and derive factory/specification artifacts directly from entity models.

The stable inputs are already present:

- `EntityModel.name`
- `EntityModel.packageName`
- `EntityModel.comment`
- deterministic aggregate-side derived entity references

This slice should also avoid expanding source semantics.

It should not restore:

- `generateFactory`
- `generateSpecification`
- table-level factory/specification hints

Those belong to later aggregate-side parity decisions, not this first bounded parity slice.

## Naming And Layout Contract

This slice should preserve old family naming and package layout.

For each entity:

- factory type name = `<Entity>Factory`
- specification type name = `<Entity>Specification`

Default package layout:

- factory package = `<entity package>.factory`
- specification package = `<entity package>.specification`

Default output paths:

- `.../<entity package path>/factory/<Entity>Factory.kt`
- `.../<entity package path>/specification/<Entity>Specification.kt`

The slice should not use this parity work as a reason to flatten or redesign aggregate-side package structure.

## Planner Contract

The aggregate generator should gain two additional family planners:

- `FactoryArtifactPlanner`
- `SpecificationArtifactPlanner`

They should be added to the existing `AggregateArtifactPlanner` delegate list alongside:

- schema
- entity
- repository

They should both consume `CanonicalModel.entities` and emit domain-module artifacts.

### Factory Planner Context

The factory planner should provide at least:

- `packageName`
- `typeName`
- `payloadTypeName`
- `entityName`
- `entityTypeFqn`
- `aggregateName`
- `comment`

`aggregateName` should remain deterministic and framework-owned, derived from the current entity package / aggregate naming rules rather than from restored source-side flags.

### Specification Planner Context

The specification planner should provide at least:

- `packageName`
- `typeName`
- `entityName`
- `entityTypeFqn`
- `aggregateName`
- `comment`

### Type-Reference Rule

This slice should consume the derived aggregate type-reference boundary, but only as far as factory/specification parity actually needs it.

That means:

- `entityTypeFqn` should become a real consumed planner input for factory/specification templates
- `qEntityTypeFqn` should remain out of scope unless a concrete template requirement appears

This slice should not widen the aggregate reference layer beyond what these two families need.

## Template Contract

This slice should add two bounded preset templates under the existing aggregate preset:

- `ddd-default/aggregate/factory.kt.peb`
- `ddd-default/aggregate/specification.kt.peb`

Aggregate templates must continue following the current aggregate renderer boundary:

- no `use()` helper
- no design-template helper contract migration
- explicit import statements only where needed

### Factory Template Shape

The factory template should preserve the old class shape closely:

- `@Service`
- `@Aggregate(... type = Aggregate.TYPE_FACTORY ...)`
- `class <Entity>Factory : AggregateFactory<<Entity>Factory.Payload, <Entity>>`
- nested `Payload : AggregatePayload<Entity>`

This first parity slice should keep the nested payload contract minimal and compileable.

It should not try to infer business payload fields from schema/entity metadata.

The payload may remain a small fixed minimal contract, as long as:

- the generated code compiles
- the class shape matches old family intent
- later slices remain free to refine payload semantics separately

### Specification Template Shape

The specification template should preserve the old class shape closely:

- `@Service`
- `@Aggregate(... type = Aggregate.TYPE_SPECIFICATION ...)`
- `class <Entity>Specification : Specification<Entity>`
- `override fun specify(entity: Entity): Result = Result.pass()`

This first slice should prioritize compileable and deterministic parity over richer specification behavior.

## Validation Strategy

This slice should validate at four levels.

### 1. Planner / Unit

Planner coverage should lock:

- factory output path
- specification output path
- preserved package layout
- deterministic type names
- `payloadTypeName`
- `entityTypeFqn` presence in factory/specification context
- no accidental leakage of unrelated derived-reference keys into other aggregate families

The previous schema-only boundary tests should be updated to the new boundary:

- schema, factory, and specification may consume the derived entity reference where needed
- entity and repository should still not gain unrelated derived-reference context

### 2. Renderer

Renderer coverage should prove:

- preset fallback works for both new aggregate templates
- factory output contains the expected aggregate annotations and aggregate-factory contract
- specification output contains the expected aggregate annotations and specification contract
- explicit import rendering works without aggregate-side `use()` helper support

### 3. Functional

Functional coverage should reuse the existing aggregate sample and extend its closure to include:

- `cap4kPlan` contains the two new template ids
- `cap4kGenerate` writes factory/specification files into the expected domain packages
- representative generated content matches the bounded parity contract

### 4. Compile-Level Check

This work should add one representative aggregate-side compile verification by reusing the existing compile harness.

If the current aggregate sample cannot absorb that check directly, the implementation should create a compile-capable derivative fixture instead of opening a new verification-infrastructure slice.

The target is not a full aggregate parity build.

The target is only to prove that a generated factory/specification pair can participate in Kotlin compilation with the existing aggregate-side dependency surface.

## Success Criteria

This slice is successful when:

- the `aggregate` generator plans factory and specification artifacts without adding new public DSL
- the new planners consume deterministic entity type references instead of mutable runtime `typeMapping`
- old naming and package layout are preserved for both families
- both new templates exist in the default aggregate preset and render compileable minimal parity output
- planner, renderer, and functional coverage lock the new boundary in place
- the previous type-reference parity slice now has a real aggregate-side consumer beyond schema context

## Explicit Non-Goals Reaffirmed

This slice must not quietly drift into:

- aggregate wrapper parity
- unique-query parity
- unique-validator parity
- relation/JPA parity
- aggregate source semantic recovery
- aggregate layout redesign
- design-style template-helper expansion into aggregate templates

## Recommended Next Step

After this spec is approved, the implementation plan should stay focused on:

1. extending the aggregate planner with factory/specification families
2. adding the two preset templates
3. wiring `entityTypeFqn` into real aggregate-side consumers
4. locking the new boundary with planner, renderer, functional, and bounded compile verification

That keeps the slice narrow enough to implement while still making visible parity progress on the aggregate side.

# Cap4k Aggregate Create-Surface Write-Surface Slice and Factory Payload Metadata Design

Date: 2026-05-06

Status: Proposed

Scope: close `#12` and make bounded aggregate-side progress on `#5` inside:

- `cap4k-plugin-pipeline-core`
- `cap4k-plugin-pipeline-api`
- `cap4k-plugin-pipeline-generator-aggregate`
- `cap4k-plugin-pipeline-renderer-pebble`

Out of scope:

- global artifact-level conflict policy (`#11`)
- repository backend redesign
- wrapper cleanup or wrapper deletion
- legacy plugin cleanup
- new DSL surfaces
- new JPA lifecycle mutability inference from `writePolicy`

## Backlog Source

This design is the bounded follow-up for:

- `#5` special-fields: enforce managed write-surface in generated create and update inputs
- `#12` generator: preserve semantic aggregate name for nested factory payload metadata

## Issue Positioning

`#12` is fully in scope for this slice.

`#5` is only partially in scope for this slice.

Reason:

- inside the scoped aggregate generator modules, the only generated write-facing payload that currently exists is the nested payload in `<Aggregate>Factory`
- `AggregateWrapperArtifactPlanner` forwards `<Factory>.Payload` but does not generate an independent write contract
- there is no generated aggregate update payload family in `cap4k-plugin-pipeline-generator-aggregate` today

So this slice can tighten aggregate create-side write-surface consumption, but it cannot by itself close the full backlog promise of `#5`.

## Problem

Cap4k already resolves special-field governance into canonical output:

- `managedFields`
- per-field `writePolicy`
- aggregate `writeSurface`

But the aggregate generator line does not yet close that contract on the only generated aggregate write payload that currently exists.

Current state:

1. `AggregateSpecialFieldPolicyResolver` resolves `writeSurface.createAllowedFields` and `writeSurface.updateAllowedFields`.
2. `CanonicalModel.aggregateSpecialFieldResolvedPolicies` carries those values across the pipeline.
3. `EntityArtifactPlanner` consumes resolved field policies only as field metadata for entity rendering.
4. `FactoryArtifactPlanner` does not consume `writeSurface`.
5. `aggregate/factory.kt.peb` still hardcodes a minimal nested `Payload` contract and sets nested payload metadata `name = "Payload"`.

This leaves two gaps in the current aggregate family:

1. generated create input on the aggregate line does not materially consume `writeSurface`
2. nested factory payload metadata loses the semantic aggregate payload name and collapses to the nested class simple name

## Current Consumption Audit

Within the scoped modules, the current generator-side consumers are:

### Canonical

- `cap4k-plugin-pipeline-core`
  - resolves `managedFields`
  - resolves per-field `writePolicy`
  - resolves `writeSurface.createAllowedFields`
  - resolves `writeSurface.updateAllowedFields`

### Planner / Generator

- `EntityArtifactPlanner`
  - consumes resolved field policies to expose field-level `writePolicy`
  - does not consume `writeSurface.createAllowedFields`
  - does not consume `writeSurface.updateAllowedFields`
- `FactoryArtifactPlanner`
  - does not consume `writeSurface`
  - emits only fixed metadata keys such as `payloadTypeName`
- `AggregateWrapperArtifactPlanner`
  - does not consume `writeSurface`
  - does not generate an independent create or update input

### Renderer

- `aggregate/entity.kt.peb`
  - renders field metadata already supplied by `EntityArtifactPlanner`
  - does not infer write-surface filtering itself
- `aggregate/factory.kt.peb`
  - currently emits a fixed nested `Payload`
  - currently emits `@Aggregate(name = "{{ payloadTypeName }}", ...)`

## Goals

This slice should do only two things:

1. make the aggregate family's current generated create payload consume resolved managed write-surface
2. preserve semantic aggregate payload metadata name while keeping the nested Kotlin type named `Payload`

## Non-Goals

This slice must not:

- claim to close `#5` by itself
- introduce a generated update payload family
- reinterpret `writePolicy` as JPA `insertable` / `updatable` lifecycle control
- invent new special-field categories beyond current canonical vocabulary
- redesign aggregate factory behavior
- widen wrapper responsibilities

## Core Decisions

### 1. In-scope generated aggregate write-facing surface means factory payload only

Inside the scoped aggregate generator family, the only generated write-facing payload is the nested payload inside `<Aggregate>Factory`.

Current code facts:

- `FactoryArtifactPlanner` emits `aggregate/factory.kt.peb`
- `aggregate/factory.kt.peb` defines `data class Payload(...) : AggregatePayload<Entity>`
- `AggregateWrapperArtifactPlanner` emits a constructor parameter typed as `<Factory>.Payload?`, which reuses the same payload instead of defining another write contract
- no aggregate planner or renderer currently emits a distinct update payload or update command/request contract in these modules

This slice therefore tightens the aggregate-side create surface on:

- `Factory.Payload`

It does not claim that entity primary constructors or wrappers are the authoritative user write contract, and it does not claim full `#5` closure.

### 2. `Factory.Payload` should consume `writeSurface.createAllowedFields`

`Factory.Payload` should be derived from canonical entity fields, but only through the already-resolved write-surface boundary.

Rule:

- payload fields = `entity.fields` filtered by `resolvedPolicy.writeSurface.createAllowedFields`

Additional rules:

- preserve the original canonical entity field order
- preserve current canonical field shape as far as planner/template needs it
- if no resolved special-field policy exists for an entity, preserve the existing minimal payload contract instead of inventing fallback governance

This keeps the change bounded:

- entities with resolved managed policy gain governed payload fields
- entities without resolved policy keep current minimal behavior

### 3. No JPA mutability inference from `writePolicy` in this slice

`writePolicy` is a user write-surface contract, not a complete ORM lifecycle contract.

This slice must not translate:

- `READ_ONLY`
- `CREATE_ONLY`
- `SYSTEM_TRANSITION_ONLY`

directly into `@Column(insertable = ..., updatable = ...)`.

Entity generation remains unchanged except for continuing to expose existing field metadata already produced by the planner.

### 4. Semantic payload metadata name must be separate from Kotlin nested type name

The generated nested Kotlin type should remain:

- `Payload`

But its aggregate metadata `name` should preserve the semantic aggregate payload name, for example:

- nested class name: `Payload`
- metadata name: `VideoPostPayload`

This separates:

- practical nested class naming for current generated Kotlin source
- stable semantic metadata naming for downstream analysis and future metadata consumers

## Detailed Design

### A) Planner changes in `FactoryArtifactPlanner`

`FactoryArtifactPlanner` should:

1. resolve the current entity's `AggregateSpecialFieldResolvedPolicy`
2. read `writeSurface.createAllowedFields`
3. when resolved policy exists, project payload fields from `entity.fields`
4. when resolved policy does not exist, preserve the current minimal payload contract
5. emit a dedicated semantic payload metadata name

Recommended context additions:

- `payloadFields`
- `payloadMetadataName`

Existing keys should remain:

- `packageName`
- `typeName`
- `payloadTypeName`
- `entityName`
- `entityTypeFqn`
- `aggregateName`
- `comment`

`payloadMetadataName` rule:

- `payloadMetadataName = "${entity.name}Payload"`

### B) Template changes in `aggregate/factory.kt.peb`

The factory template should stop hardcoding:

- `val name: String`
- `name = "{{ payloadTypeName }}"`

New behavior:

1. render payload properties from `payloadFields` when present
2. continue rendering a compileable minimal payload when `payloadFields` is absent or empty because no resolved policy was available
3. use `payloadMetadataName` for nested payload `@Aggregate(name = ...)`

The nested Kotlin class name remains:

- `data class Payload(...)`

The metadata becomes:

- `@Aggregate(name = "{{ payloadMetadataName }}", type = Aggregate.TYPE_FACTORY_PAYLOAD, ...)`

### C) Canonical behavior intentionally unchanged

No changes are required to:

- `managedFields` resolution rules
- `writeSurface` computation rules
- `AggregateSpecialFieldResolvedPolicy` shape

Current canonical contract is already sufficient for this bounded slice.

## Compatibility Expectations

### Safe changes

- `Factory.Payload` field set becomes narrower for entities with resolved managed write-surface
- nested payload metadata name changes from `Payload` to semantic aggregate payload name

### Intended behavior change

Managed fields that are excluded from `createAllowedFields` must no longer appear in generated `Factory.Payload`.

This is the desired contract tightening for the aggregate create-side slice of `#5`.

### Expected non-change

- nested Kotlin type name remains `Payload`
- wrapper constructor type remains `<Factory>.Payload`
- entity `@Column(insertable/updatable)` output does not change because of special-field `writePolicy`

### Downstream metadata risk

Current KSP and code-analysis consumers use aggregate payload classification primarily from:

- `aggregate`
- `type`
- nested class identity / class path

not from the nested payload metadata `name`.

So changing metadata `name` back to semantic aggregate payload name is expected to be compatible with current downstream analysis.

## Verification

This slice is complete when all are true:

1. planner coverage proves `FactoryArtifactPlanner` filters payload fields by `writeSurface.createAllowedFields`
2. planner coverage proves payload metadata name remains semantic, for example `VideoPostPayload`
3. renderer coverage proves generated nested payload omits managed fields excluded from create write-surface
4. renderer coverage proves generated nested payload still uses class name `Payload`
5. renderer coverage proves nested payload annotation metadata uses semantic name instead of plain `Payload`
6. no entity-template regression is introduced around current field metadata rendering

This verification closes `#12` and validates bounded progress on `#5`, but it does not by itself satisfy the full issue-level acceptance of generated create and update surfaces across the broader product.

## Residual Risks

1. Earlier aggregate factory parity intentionally kept payload shape minimal and non-derived. This slice reopens that boundary only for entities that already have resolved managed write-surface, and only to the extent required for `#5`.
2. Entities without resolved policy intentionally keep the old minimal payload contract. That preserves backwards behavior, but also means this slice only hardens aggregate create payloads where canonical special-field resolution is actually available.
3. Entity-side JPA mutability concerns remain unresolved by design. They require a separate explicit lifecycle policy slice rather than overloading `writePolicy`.
4. `#5` remains open after this slice unless another generator family adds or wires remaining write-facing create/update surfaces to `writeSurface`.

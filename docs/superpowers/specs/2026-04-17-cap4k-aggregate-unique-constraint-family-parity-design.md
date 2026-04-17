# Cap4k Aggregate Unique-Constraint Family Parity

Date: 2026-04-17
Status: Draft for review

## Summary

The bounded bootstrap line is complete.

The cross-generator reference boundary line is now complete through:

- aggregate factory / specification parity
- aggregate wrapper parity

The next explicit framework slice should be:

- `aggregate unique-constraint family parity`

This slice should stay inside the existing `aggregate` generator.

It should migrate the old aggregate-side bounded family consisting of:

- `unique_query`
- `unique_query_handler`
- `unique_validator`

It should reuse:

- the now-stable aggregate derived type-reference boundary
- the aggregate-side planner, renderer, functional, and compile verification pattern already established through factory / specification and wrapper parity

It should not reopen:

- relation parity
- JPA annotation parity
- source semantic recovery
- mutable shared runtime type maps

## Goals

- Extend the existing pipeline `aggregate` generator with bounded unique-constraint family parity.
- Preserve the old family relationship between unique query, unique query handler, and unique validator.
- Keep all naming and type selection deterministic and planner-owned.
- Introduce one shared aggregate-internal unique-constraint planning layer rather than three separate naming and selection implementations.
- Add planner, renderer, functional, and bounded compile verification for representative unique-constraint output.

## Non-Goals

- Do not add a new public DSL.
- Do not split this family into a new top-level generator.
- Do not restore old `constraintName`-driven suffix customization in this slice.
- Do not restore old `deletedField` filtering in this slice.
- Do not restore old `_share.model` layout as a new framework contract.
- Do not widen this slice into enum / translation parity.
- Do not widen this slice into relation parity, JPA annotation parity, or user-code-preservation parity.
- Do not reintroduce mutable shared runtime `typeMapping`-style coordination between generators.

## Current Context

Old codegen still contains three tightly related aggregate-side generators:

- `UniqueQueryGenerator`
- `UniqueQueryHandlerGenerator`
- `UniqueValidatorGenerator`

These generators share the same logical input:

- aggregate root entity identity
- unique constraints
- primary key type
- derived suffix naming

They also share the same family closure:

- query contract
- query handler
- validator contract

Current pipeline already has a stable input foothold for this slice because `DbTableSnapshot` exposes:

- `columns`
- `primaryKey`
- `uniqueConstraints`

This means the next bounded parity step can start from existing DB-source and aggregate canonical input, instead of first reopening the source architecture.

## Why This Slice Exists

After wrapper parity, the next smallest coherent aggregate-side family is the unique-constraint family.

It is smaller and more bounded than:

- enum / translation parity
- relation parity
- JPA annotation parity

because it can be driven by current DB aggregate input plus deterministic naming rules.

It also benefits directly from the work already completed in earlier slices:

- unified aggregate derived type references
- aggregate factory parity
- aggregate wrapper parity
- aggregate compile-level verification

That makes it a better next step than jumping to broader aggregate parity or infrastructure-heavy exploratory gaps.

## Design Decision

This slice should extend the existing `aggregate` generator rather than creating a new generator.

Recommended shape:

- keep generator id = `aggregate`
- add one aggregate-internal unique-constraint planning helper
- add three bounded aggregate family planners
- add three aggregate preset templates
- verify the family as a group through shared representative fixtures

This keeps the slice aligned with the strategy already used for aggregate parity:

- schema
- entity
- repository
- factory
- specification
- wrapper

## Public Contract

This slice should not add any new user-facing DSL.

Users should continue enabling this work only by enabling the existing aggregate generator.

The public contract change is internal but durable:

- the `aggregate` generator now includes bounded unique-constraint family parity

## Canonical Boundary

This slice should not add a new top-level canonical model such as:

- `UniqueConstraintModel`
- `UniqueQueryModel`
- `UniqueValidatorModel`

Instead, it should consume the current aggregate-side canonical boundary and derive unique-constraint planning data inside the aggregate generator.

That means:

- no new framework-wide canonical slice
- no new shared mutable runtime map
- no new public planner protocol

If extra structure is needed, it should be aggregate-internal and read-only, for example:

- `AggregateUniqueConstraintPlanningInput`
- `AggregateUniqueConstraintSelection`

These helper types should exist only to keep the aggregate generator deterministic and coherent.

## Shared Planning Layer

The core of this slice should be a single shared aggregate-internal helper that:

- reads unique constraints from current aggregate input
- derives one deterministic suffix per selected unique constraint
- derives request property names
- derives id type and exclude-id parameter name
- derives all three family type names from the same selected constraint

It should be consumed by:

- `UniqueQueryArtifactPlanner`
- `UniqueQueryHandlerArtifactPlanner`
- `UniqueValidatorArtifactPlanner`

This helper is the stability boundary for the whole slice.

Without it, the repository would risk duplicating:

- suffix derivation
- id-type derivation
- field-name normalization
- query / handler / validator naming coordination

three times.

## First-Slice Naming Rules

This first slice should preserve the old default family naming:

- `Unique${Entity}${Suffix}Qry`
- `Unique${Entity}${Suffix}QryHandler`
- `Unique${Entity}${Suffix}`

Examples:

- `UniqueVideoPostCodeQry`
- `UniqueVideoPostCodeQryHandler`
- `UniqueVideoPostCode`

All three names must come from the same derived suffix result.

This slice should not introduce naming customization.

## Suffix Derivation Boundary

Old codegen supports more than one suffix source:

- explicit `constraintName` conventions such as `uk_v_xxx`
- fallback to joined field names
- filtering via `deletedField`

This first slice should stay bounded.

Recommended rule:

- derive suffix from unique-constraint column names only
- normalize to stable upper-camel suffix
- do not depend on external `constraintName` semantics
- do not depend on `deletedField`

That keeps the slice grounded in inputs the current pipeline already owns.

If later parity work needs `constraintName` or `deletedField`, that should be a follow-up slice with its own explicit source and config discussion.

## Output Roles And Layout

The three families should remain owned by the `aggregate` generator but emit into their old module roles:

- `unique_query` -> `application`
- `unique_query_handler` -> `adapter`
- `unique_validator` -> `application`

This is intentional.

The aggregate generator is the semantic owner of the family, but the generated files still belong in application or adapter roles because that matches the old family shape.

This slice should not redesign package layout.

It should preserve the old default family direction:

- aggregate-oriented query contract
- corresponding handler
- corresponding validator

## Template Contract

This slice should add three fixed template ids under the aggregate preset:

- `aggregate/unique_query.kt.peb`
- `aggregate/unique_query_handler.kt.peb`
- `aggregate/unique_validator.kt.peb`

These should live in the existing aggregate preset:

- `ddd-default/aggregate/...`

Aggregate template rules stay unchanged:

- no `use()` helper
- explicit imports only
- planner-owned type refs

This slice should not migrate aggregate templates toward the design helper contract.

## Family-Specific Contract

### Unique Query

The first bounded unique query contract should preserve:

- request properties derived from unique-constraint columns
- an additional `exclude${Entity}Id` parameter
- use of the entity primary-key type for the exclude-id parameter

It should remain an aggregate-oriented query contract and keep the old family naming.

### Unique Query Handler

The first bounded unique query handler contract should preserve:

- handler naming derived from the query type
- adapter-side placement
- explicit coupling to the generated unique query type

It should not restore the old `_share.model` framework contract.

If some derived support model is needed, it should stay bounded to this slice and not silently turn `_share.model` into a new general framework layout rule.

### Unique Validator

The first bounded unique validator contract should preserve:

- validator naming that shares the same suffix as the corresponding unique query
- application-side placement
- explicit coupling to the corresponding unique query type
- use of the entity id exclusion parameter shape

It should not reopen the older validator configuration surface in this slice.

## Type-Reference Rule

This slice must continue following the current type-reference discipline:

- explicit FQN first
- stable aggregate-derived reference next
- deterministic planner-owned derivation next
- no mutable shared runtime map

That means the unique-constraint family should consume planner-owned type references for:

- query type
- handler type
- validator type
- entity type
- id type

and should not guess these inside Pebble templates.

## Validation Strategy

This slice should validate at four levels.

### 1. Planner / Unit

Planner coverage should lock:

- deterministic suffix derivation from unique-constraint columns
- consistent naming across query / handler / validator for the same selected constraint
- output module role and output path
- derived id type and exclude-id parameter naming
- planner-owned cross-family type references

### 2. Renderer

Renderer coverage should prove:

- preset fallback works for all three aggregate unique-constraint templates
- rendered query, handler, and validator class shapes are compile-safe
- aggregate templates continue avoiding `use()`
- explicit imports remain sufficient

### 3. Functional

Functional coverage should prove:

- `cap4kPlan` includes all three template ids
- `cap4kGenerate` writes all three representative files
- a representative aggregate fixture exercises the whole family closure

This should reuse current aggregate fixture style rather than invent a separate framework.

### 4. Compile-Level Check

This slice should extend existing compile-capable aggregate verification rather than invent a new compile harness.

The compile gate should prove that representative:

- unique query
- unique query handler
- unique validator

can participate in compilation in the appropriate application and adapter modules.

The target remains bounded:

- compile-safe representative unique-constraint family parity
- not full aggregate parity

## Success Criteria

This slice is successful when:

- the `aggregate` generator plans bounded unique-constraint family artifacts without adding new public DSL
- query, handler, and validator naming are derived from one shared deterministic selection layer
- the aggregate preset contains three compile-safe unique-constraint templates
- planner, renderer, functional, and compile verification lock the new family in place
- this slice does not reopen `constraintName`, `deletedField`, `_share.model`, relation parity, or JPA parity


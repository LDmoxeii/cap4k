# Cap4k Database Special-Field Declaration Contract Unification Design

Date: 2026-05-03

Status: Proposed

Scope: unify declaration and resolution contract for aggregate special fields (ID generation, soft-delete, optimistic-lock version) under one model with project-level defaults and DB local overrides, and expose resolved policy in `cap4kPlan`.

Out of scope: repository backend replacement, frontend TypeScript generation, read/write association-scope separation, inverse-navigation fetch policy, legacy compatibility.

## Problem

Current special-field declaration contracts are inconsistent:

- ID generation policy is primarily DSL-driven.
- soft-delete is table-comment driven via `@SoftDeleteColumn=...`.
- optimistic-lock version is column-comment driven via `@Version=true`.

This increases user mental load and makes policy review in `cap4kPlan` incomplete, because only parts of final behavior are visible.

## Goal

Define one declaration contract:

- DSL provides project-level global fallback defaults only.
- DB annotations provide entity/field-level local overrides.
- ID, soft-delete, and version use one resolution model.
- `cap4kPlan` exposes per-entity resolved policy for review.
- invalid or ambiguous declarations fail fast with actionable diagnostics.

## Non-goals

This slice must not:

- preserve `@SoftDeleteColumn=...` or `@Version=true` compatibility.
- preserve aggregate-level or entity-level ID override DSL surface.
- reopen generator/runtime tracks outside special-field contract unification.
- introduce migration tooling for existing schemas.

## Design Decisions

1. DSL only carries project-level defaults.
2. DB local declarations are column-level annotations.
3. Resolution is entity-scoped; no aggregate-level uniformity constraint is enforced.
4. Missing default soft-delete/version column on an entity is valid and means "feature disabled for that entity".

## Project DSL Contract

Special-field defaults are global fallback values only.

Conceptual DSL shape:

```kotlin
cap4k {
    generators {
        aggregate {
            specialFields {
                idDefaultStrategy.set("uuid7")
                deletedDefaultColumn.set("deleted")
                versionDefaultColumn.set("version")
            }
        }
    }
}
```

Rules:

- no aggregate-level override entry.
- no entity-level override entry.
- `idDefaultStrategy` defaults to `uuid7`.
- `deletedDefaultColumn` and `versionDefaultColumn` are optional fallback names.
- blank `deletedDefaultColumn` or `versionDefaultColumn` means "do not apply name fallback globally".

## DB Annotation Contract (Column-level)

### ID generation

Use one column annotation family:

- `@GeneratedValue`
- `@GeneratedValue=<strategy>`

Supported strategies in this slice:

- `uuid7`
- `snowflake-long`
- `identity` (alias: `database-identity`)

Semantics:

- `@GeneratedValue=<strategy>`: explicit entity-local override.
- `@GeneratedValue` (without value): explicit local declaration that still resolves to the project default ID strategy.
- no application-side vs database-side split at annotation vocabulary layer; generation kind is resolved from strategy.

### Soft-delete

Use marker annotation on one column:

- `@Deleted`

### Optimistic lock version

Use marker annotation on one column:

- `@Version`

Marker-style semantics:

- presence means enabled.
- boolean forms like `@Version=true` are not supported.

## Unified Resolution Model

Resolve policy per entity:

- `resolvedIdPolicy`
- `resolvedSoftDeletePolicy`
- `resolvedVersionPolicy`

Each resolved policy records:

- selected field/column (if enabled)
- selected strategy/kind (ID only)
- source (`DB_EXPLICIT`, `DSL_DEFAULT`, `NONE`)

### Resolution precedence

`DB_EXPLICIT` > `DSL_DEFAULT` > `NONE`

For each entity, each feature is resolved independently under that precedence.

### ID resolution

1. Determine entity ID field from supported single-column primary key model.
2. Read ID column `@GeneratedValue` annotation:
   - explicit strategy present: use it.
   - annotation without strategy: use DSL `idDefaultStrategy`.
3. If no ID annotation: use DSL `idDefaultStrategy`.
4. Map strategy to generation kind:
   - `identity`/`database-identity` -> `DATABASE_SIDE`
   - others -> `APPLICATION_SIDE`
5. Validate strategy/type compatibility.

### soft-delete resolution

1. Count `@Deleted` columns for the entity:
   - one: use it (`DB_EXPLICIT`).
   - more than one: fail fast.
2. If none:
   - if DSL `deletedDefaultColumn` is blank -> disabled (`NONE`).
   - else if matching column exists -> enable with that column (`DSL_DEFAULT`).
   - else -> disabled (`NONE`).

### version resolution

1. Count `@Version` columns for the entity:
   - one: use it (`DB_EXPLICIT`).
   - more than one: fail fast.
2. If none:
   - if DSL `versionDefaultColumn` is blank -> disabled (`NONE`).
   - else if matching column exists -> enable with that column (`DSL_DEFAULT`).
   - else -> disabled (`NONE`).

## Aggregate-level ID Uniformity Decision

This slice intentionally does not enforce one ID strategy per aggregate.

Decision:

- mixed ID strategies across entities in the same aggregate are supported.
- resolution is entity-scoped and field-scoped.
- fail-fast remains type/annotation correctness based, not aggregate uniformity based.

Rationale:

- current runtime ID assignment is annotation/field-driven and does not require aggregate-wide strategy identity.
- existing generation and compile functional tests already cover mixed application-side and identity usage in one project.
- forcing aggregate-wide uniformity now would reduce local override utility without demonstrated runtime requirement.

## Fail-fast Matrix

The resolver must fail fast for these scenarios:

1. unknown ID strategy value in `@GeneratedValue=<strategy>` or DSL default.
2. resolved ID strategy incompatible with generated ID field type.
3. `identity` strategy resolved for non-numeric ID field.
4. `@GeneratedValue` appears on non-ID column.
5. multiple `@Deleted` columns in one entity.
6. multiple `@Version` columns in one entity.
7. marker annotations provided with unsupported explicit values:
   - `@Deleted=...`
   - `@Version=...`
8. unsupported legacy annotations:
   - table-level `@SoftDeleteColumn=...`
   - column-level `@Version=true|false`
   - legacy ID table comment annotations (`@IdGenerator`, `@IG`)

Non-failure by design:

- DSL default deleted/version column name not found on an entity.
- entity without soft-delete and/or version requirement.

## `cap4kPlan` Resolved Policy Exposure

`cap4kPlan` must expose final resolved special-field policy per entity, not only raw DSL defaults.

Conceptual plan payload:

```json
{
  "specialFieldResolvedPolicies": [
    {
      "entityPackageName": "com.acme.demo.domain.aggregates.video",
      "entityName": "VideoFile",
      "tableName": "video_file",
      "id": {
        "fieldName": "id",
        "columnName": "id",
        "strategy": "snowflake-long",
        "kind": "APPLICATION_SIDE",
        "source": "DB_EXPLICIT"
      },
      "deleted": {
        "enabled": false,
        "fieldName": null,
        "columnName": null,
        "source": "NONE"
      },
      "version": {
        "enabled": true,
        "fieldName": "version",
        "columnName": "version",
        "source": "DSL_DEFAULT"
      }
    }
  ]
}
```

Required properties:

- ID strategy and kind are explicit for each entity.
- soft-delete/version enabled state is explicit for each entity.
- each decision has provenance (`DB_EXPLICIT` / `DSL_DEFAULT` / `NONE`).

## Public Surface Pruning

This slice reduces DSL public surface:

- remove aggregate-level ID override API from public DSL.
- remove entity-level ID override API from public DSL.
- keep only project-level defaults for special fields.

This is intentional to keep one regular default+override model where overrides live in DB annotations.

## Migration and Breaking Behavior

No legacy compatibility is required.

Required breaking behavior:

- old table-level soft-delete declarations fail with actionable errors.
- old boolean value version declarations fail with actionable errors.
- old aggregate/entity ID override DSL entries fail with actionable errors.

Projects must migrate to:

- project-level DSL defaults
- column-level `@GeneratedValue`, `@Deleted`, `@Version` declarations

## Test Strategy

Add/adjust tests at four layers:

1. DB annotation parser tests:
   - marker parsing and invalid valued-marker rejection
   - legacy annotation rejection
2. canonical/core resolver tests:
   - per-entity resolved policy precedence
   - missing default deleted/version column does not fail
   - mixed-ID strategies across same aggregate entity set resolves successfully
3. generator/render tests:
   - resolved policy correctly drives entity annotations and provider controls
4. Gradle functional/compile tests:
   - `cap4kPlan` includes resolved policy structure
   - mixed strategy project remains generation/compile safe

## Acceptance Criteria

This slice is complete when:

1. ID/soft-delete/version declarations follow one unified contract.
2. DSL only provides project-level fallback defaults.
3. DB column-level markers provide local overrides.
4. missing deleted/version default columns on some entities do not fail.
5. fail-fast diagnostics cover ambiguous/invalid declarations.
6. mixed ID strategies across entities are supported when type-valid.
7. `cap4kPlan` exposes per-entity resolved special-field policy with provenance.

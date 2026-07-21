# cap4k Soft Delete Discriminator Policy Design

Date: 2026-07-20

## Context

cap4k currently treats soft delete as a single persistence-provider column. That column is then expanded directly into generated Hibernate annotations:

- active row filter: `deleted = 0`
- delete SQL assignment: `deleted = 1`

This works for a simple flag model, but it is too weak for schemas that use soft-delete columns inside unique constraints. In the intended practice, active rows use `deleted = 0`, and deleting a row writes the row's own id into `deleted`. This supports constraints such as `unique(parent_id, deleted)`:

- active rows: only one row can exist for `(parent_id, 0)`
- deleted history rows: each deleted row has `(parent_id, self_id)`, so historical rows do not collide

The current `deleted = 1` assignment cannot support that pattern because all deleted rows under the same unique business key collide on the same tombstone value.

## Current Evidence

Current pipeline evidence:

- `AggregatePersistenceProviderControl` only carries `softDeleteColumn`.
- `EntityArtifactPlanner` builds `softDeleteWhereClause` as `<column> = 0`.
- `EntityArtifactPlanner` builds delete SQL as `set <column> = 1`.
- `AggregateSpecialFieldPolicyResolver` marks deleted fields as `SYSTEM_TRANSITION_ONLY`, so factory/create/update surfaces should not receive them.
- `AggregateUniqueConstraintPlanning` already treats the soft delete column as a control field and removes it from unique-query request fields.

Relevant files:

- `cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModels.kt`
- `cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/AggregateSpecialFieldPolicyResolver.kt`
- `cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/AggregatePersistenceProviderInference.kt`
- `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/EntityArtifactPlanner.kt`
- `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateUniqueConstraintPlanning.kt`
- `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/entity.kt.peb`

## Goals

1. Model soft delete as a lifecycle discriminator, not as a boolean flag.
2. Make `active = 0` an explicit contract.
3. Make deleted rows write a tombstone value that can coexist with unique constraints.
4. Keep deleted fields system-managed: factory and application create/update payloads must not accept them.
5. Let unique-constraint logic consume the same resolved soft-delete semantics.
6. Keep the default generated JPA/Hibernate behavior coherent without requiring user template overrides for the common self-id strategy.

## Non Goals

- This spec does not redesign DB custom annotations. The annotation contract is handled separately.
- This spec does not change value-object persistence.
- This spec does not design hard-delete behavior.
- This spec does not require supporting multiple soft-delete columns per table.
- This spec does not require supporting timestamp, uuid, expression, or custom SQL strategies in the first implementation.
- This spec does not infer owned parent-child cardinality. That is covered by `2026-07-20-cap4k-owned-relation-cardinality-from-unique-constraints-design.md`.

## Recommended Contract

Introduce a resolved soft-delete policy in the canonical/generator model:

```kotlin
data class AggregateSoftDeletePolicy(
    val fieldName: String,
    val columnName: String,
    val activeValue: String,
    val tombstoneStrategy: SoftDeleteTombstoneStrategy,
    val activePredicateSql: String,
    val deleteAssignmentSql: String,
)

enum class SoftDeleteTombstoneStrategy {
    SELF_ID,
}
```

The first supported policy is:

```text
activeValue = 0
tombstoneStrategy = SELF_ID
activePredicateSql = <deleted_column> = 0
deleteAssignmentSql = <deleted_column> = <id_column>
```

Generated delete SQL should become:

```sql
update <table>
set <deleted_column> = <id_column>
where <id_column> = ?
```

With optimistic locking:

```sql
update <table>
set <deleted_column> = <id_column>
where <id_column> = ? and <version_column> = ?
```

The generated active filter remains:

```sql
<deleted_column> = 0
```

The first implementation should support numeric self-id discriminators only. That matches the intended practice:
numeric id, numeric deleted column, active sentinel `0`, and deleted tombstone value equal to the row id.

## Model Changes

Replace or extend `AggregatePersistenceProviderControl.softDeleteColumn` with a richer policy:

```kotlin
data class AggregatePersistenceProviderControl(
    val entityName: String,
    val entityPackageName: String,
    val tableName: String,
    val dynamicInsert: Boolean? = null,
    val dynamicUpdate: Boolean? = null,
    val softDelete: AggregateSoftDeletePolicy? = null,
    val idFieldName: String,
    val versionFieldName: String? = null,
)
```

If destructive changes are acceptable, prefer replacing `softDeleteColumn` outright. Keeping both fields would create two sources of truth.

The resolved marker policy should continue to expose deleted as a managed field with `SYSTEM_TRANSITION_ONLY`. The new policy should be an additional persistence/runtime rendering contract, not a factory input contract.

## Validation Rules

The pipeline must fail fast when:

1. More than one deleted field is resolved for an entity.
2. `SELF_ID` is selected but the deleted column cannot store the id column value.
3. `SELF_ID` is selected but the id column cannot be resolved.
4. The deleted column is nullable and the active value is expected to be `0`.
5. The deleted column has no default value or generated default compatible with `0`.

Type compatibility should be conservative:

- `Long`/`Int` id requires a numeric deleted column wide enough for the id.
- strong-id fields compare by their underlying DB column type, not by the wrapper type name.
- string/uuid self-id soft delete is out of scope for the first implementation because `activeValue = 0` is a numeric discriminator contract.

## Unique Constraints

Soft-delete discriminator columns are control fields in unique constraints. They should not become request fields in generated unique queries.

For a constraint such as:

```sql
unique(parent_id, deleted)
```

the generated unique request should include `parentId` only. The deleted discriminator is supplied by the active-row filter, not by user input.

Existing unique-query planning already filters the soft-delete column. The implementation should keep that behavior, but source it from `AggregateSoftDeletePolicy` rather than a raw `softDeleteColumn` string.

## Downstream Relation Input

Owned relation cardinality is handled by `2026-07-20-cap4k-owned-relation-cardinality-from-unique-constraints-design.md`.

This soft-delete iteration only guarantees that a deleted discriminator is resolved as a first-class policy instead of a raw column string. Later relation inference may use that resolved policy to identify `@Managed=deleted` as a cardinality-neutral column when evaluating active-row uniqueness.

## Template Behavior

Default entity template behavior should render:

- `@SQLDelete(sql = "update ... set deleted = id where id = ?")`
- `@SQLDelete(sql = "update ... set deleted = id where id = ? and version = ?")` when optimistic locking is enabled
- `@Where(clause = "deleted = 0")`

Identifier quoting must continue to use the current quote-style resolver. The delete assignment must quote both the deleted column and id column.

The template context should expose the policy, not only final strings, so template overrides can choose a different rendering if needed:

```text
softDelete.enabled
softDelete.columnName
softDelete.activeValue
softDelete.tombstoneStrategy
softDelete.activePredicateSql
softDelete.deleteAssignmentSql
```

The default template consumes planner-produced Kotlin string literals for `softDeleteSql` and `softDeleteWhereClause`, while structured policy fields remain available for overrides.

## Factory And Write Surface

Deleted fields remain system-managed:

- excluded from create payloads
- excluded from update payloads
- excluded from default factory constructor mapping
- available in entity/schema/projection metadata as ordinary persisted fields

This preserves the current `SYSTEM_TRANSITION_ONLY` intent while making the transition value explicit.

## Migration From Current Behavior

Because compatibility is not required, the default should change from flag semantics to self-id semantics:

```text
old delete assignment: deleted = 1
new delete assignment: deleted = id
```

The active predicate remains:

```text
deleted = 0
```

Tests and docs that assert `deleted = 1` should be updated to assert `deleted = id`.

## Resolved Decisions

1. The active default is enforced through schema validation only. Generated constructors do not initialize deleted fields to `0` when the DB default is absent.
2. `SELF_ID` requires assignability, not exact DB type equality. Numeric widening from id to deleted is accepted; narrowing, non-numeric, or unproven mappings are rejected.
3. Aggregate projections do not inherit the active filter in this iteration. Projection filtering remains a read-model decision for a later design.

## Implementation Notes

Likely implementation areas:

1. Extend API model with `AggregateSoftDeletePolicy`.
2. Change special-field/persistence-provider inference to resolve `softDelete` instead of a raw column.
3. Change entity artifact planner to build active predicate and delete assignment from the policy.
4. Change entity template context to expose the policy and render self-id delete SQL.
5. Change unique-constraint planning to read soft-delete control columns from the policy.
6. Update tests covering soft-delete SQL and unique-control-field filtering.

## Acceptance Criteria

1. A table with id `id` and deleted discriminator `deleted` generates SQL equivalent to `set deleted = id`.
2. A versioned table generates SQL equivalent to `set deleted = id where id = ? and version = ?`.
3. Active filter generation remains `deleted = 0`.
4. Unique queries for `unique(code, deleted)` request only `code`.
5. Deleted fields remain outside factory/create/update payloads.
6. The resolved soft-delete policy is exposed clearly enough for the owned-cardinality iteration to consume later.

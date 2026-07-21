# cap4k Owned Relation Cardinality From Unique Constraints Design

Date: 2026-07-20

## Context

cap4k currently models table-level `@Parent` owned child relations as parent-side `ONE_TO_MANY`. That is safe for persistence because the child table stores the parent reference column, and the parent can own the child rows through a join column.

Some schemas, however, use a child table for an aggregate part that is business-wise one-to-one with the parent. The storage shape still uses a child table, but database constraints express that each parent can have at most one active child row.

Older discussions included an `@One` style marker, but that marker did not mean a physical JPA `OneToOne`. It meant "the aggregate has one child concept, while persistence may still use `OneToMany` to avoid storage-shape changes." This spec keeps that idea as inferred metadata rather than as a DB annotation.

This spec depends on two preceding iterations:

- `2026-07-20-cap4k-db-custom-annotation-contract-redesign.md`
- `2026-07-20-cap4k-soft-delete-discriminator-policy-design.md`

## Current Evidence

Current pipeline evidence:

- `AggregateRelationInference` creates parent-child relations from tables with `parentTable != null`.
- The current parent-child relation type is always `AggregateRelationType.ONE_TO_MANY`.
- Current owned parent binding requires an explicit `@ParentRef` column and no longer falls back to a conventional `<parent_table>_id` column.
- `DbSchemaSourceProvider` already reads unique indexes into `UniqueConstraintModel` through `DatabaseMetaData.getIndexInfo`.
- `DbTableSnapshot` carries `primaryKey` and `uniqueConstraints`.
- The default aggregate entity template can render `ONE_TO_MANY` through `@OneToMany @JoinColumn`.
- The default aggregate entity template can also render `ONE_TO_ONE`, but that branch is better suited to owner-side reference columns than to child-table parent references.

## Goals

1. Infer owned child cardinality from schema constraints instead of DB relation annotations.
2. Use explicit `@ParentRef` as the parent identity binding column.
3. Avoid physical database foreign-key metadata and constraints.
4. Treat `@Managed=scope` and `@Managed=deleted` as cardinality-neutral when they participate in unique constraints.
5. Keep `@Managed=system` and `@Managed=version` out of cardinality-neutral filtering.
6. Keep owned relation persistence collection-backed in the first implementation.
7. Expose `ownedCardinality=ONE|MANY` to canonical/generator/template contexts.
8. For `ownedCardinality=ONE`, hide the collection-backed persistence detail behind an entity-level single-child accessor/mutator.
9. Keep factory behavior deliberate for child constructor mapping and one-child payloads.

## Non Goals

- Do not redesign the DB custom annotation allow-list.
- Do not redesign soft-delete tombstone SQL.
- Do not introduce `@One`, `@Count`, `@Relation`, or `@ParentCardinality`.
- Do not depend on database foreign-key metadata.
- Do not generate physical owned `@OneToOne` mappings in the first implementation.
- Do not put the single-child accessor/mutator in checked-in `*Behavior.kt`; it belongs in generated entity code.
- Do not decide read-model weak-reference generation.
- Do not change value-object persistence.

## Required Inputs

This iteration consumes inputs established by prior specs:

| Input | Source |
|---|---|
| child ownership | table comment `@Parent=<table>` |
| parent identity binding | exactly one child column comment `@ParentRef` |
| scope discriminator | child column comment `@Managed=scope` |
| soft-delete discriminator | child column comment `@Managed=deleted` and resolved soft-delete policy |
| primary key | JDBC table primary-key metadata |
| unique constraints | JDBC unique index metadata copied into `UniqueConstraintModel` |

Physical DB foreign keys are not inputs.

There is no fallback to `<parent_table>_id`. A child table with `@Parent` and no `@ParentRef` must already fail in the DB annotation iteration.

## Cardinality Model

Add an internal owned cardinality concept:

```kotlin
enum class OwnedRelationCardinality {
    ONE,
    MANY,
}
```

This cardinality describes active aggregate structure:

- `ONE`: one parent can have at most one active child row of this child table.
- `MANY`: one parent can have zero or more active child rows.

It is not the same as the default persistence mapping. The first implementation should keep the owned relation persistence shape collection-backed even when `ownedCardinality=ONE`.

## Inference Algorithm

For each owned child table:

```text
parentRef = the single @ParentRef column
scopeColumns = columns marked @Managed=scope
deletedColumn = column marked @Managed=deleted, if present
neutralColumns = {parentRef} + scopeColumns + {deletedColumn if present}

if primaryKey == [parentRef]:
    ownedCardinality = ONE
else if exists unique constraint U where:
    U contains parentRef
    and U minus neutralColumns is empty
    and all scope/deleted columns in U are non-null:
        ownedCardinality = ONE
else:
    ownedCardinality = MANY
```

Column comparison must be case-insensitive and use physical column names.

`U minus neutralColumns` means set-like subtraction after column-name normalization. Constraint column order must not matter for cardinality.

Only `@Managed=scope` and `@Managed=deleted` are neutral:

- `@Managed=scope` is neutral because it is a system-supplied scope/partition discriminator.
- `@Managed=deleted` is neutral because active-row uniqueness is scoped by the soft-delete discriminator.
- `@Managed=system` is not neutral.
- `@Managed=version` is not neutral.

The non-null rule is conservative. A nullable scope or delete discriminator in a unique constraint can behave differently across databases and should not prove one-child active cardinality.

## Examples

| Child constraint | Managed roles | Result | Reason |
|---|---|---|---|
| `primary key (parent_id)` | `parent_id=@ParentRef` | `ONE` | parent identity is the child identity |
| `unique(parent_id)` | `parent_id=@ParentRef` | `ONE` | direct active uniqueness |
| `unique(parent_id, deleted)` | `deleted=@Managed=deleted` | `ONE` | soft-delete discriminator is neutral |
| `unique(tenant_id, parent_id)` | `tenant_id=@Managed=scope` | `ONE` | tenant scope is neutral |
| `unique(tenant_id, parent_id, deleted)` | `tenant_id=@Managed=scope`, `deleted=@Managed=deleted` | `ONE` | only parent, scope, and delete discriminator remain |
| `unique(parent_id, code)` | none | `MANY` | `code` is child business cardinality |
| `unique(tenant_id, parent_id, code, deleted)` | `tenant_id=@Managed=scope`, `deleted=@Managed=deleted` | `MANY` | `code` remains after neutral filtering |
| `unique(parent_id, version)` | `version=@Managed=version` | `MANY` | version is not neutral |
| `unique(owner_id, parent_id, deleted)` | `deleted=@Managed=deleted` only | `MANY` | `owner_id` is not neutral unless explicitly `@Managed=scope` |

## Persistence Shape

Default owned relation persistence should remain:

```text
persistenceShape = ONE_TO_MANY_JOIN_COLUMN
```

For both `ownedCardinality=ONE` and `ownedCardinality=MANY`, the first implementation should keep parent-side generated persistence collection-backed through `@OneToMany @JoinColumn`.

Reasons:

- The parent does not physically store a child reference column.
- The child table stores the parent reference column.
- The current `ONE_TO_ONE` template branch is more suitable for owner-side reference columns.
- Keeping the same persistence shape avoids conflating business cardinality with JPA mapping shape.

For `ownedCardinality=ONE`, the generated entity should still persist through a collection-backed relation, but the public domain-facing API should expose a nullable single-child property. The backing collection should not be the preferred public API for one-child owned relations.

Conceptual Kotlin shape:

```kotlin
@field:OneToMany(fetch = FetchType.LAZY, cascade = [CascadeType.PERSIST, CascadeType.MERGE, CascadeType.REMOVE], orphanRemoval = true)
@field:JoinColumn(name = "video_post_id", nullable = false)
private val videoFiles: MutableList<VideoFile> = mutableListOf()

@get:Transient
var videoFile: VideoFile?
    get() = when (videoFiles.size) {
        0 -> null
        1 -> videoFiles[0]
        else -> error("owned relation VideoPost.videoFile expected at most one VideoFile but found ${videoFiles.size}")
    }
    set(value) {
        videoFiles.clear()
        if (value != null) {
            videoFiles.add(value)
        }
    }
```

The getter must fail fast if the backing collection contains more than one loaded child. The setter uses replace semantics: `null` clears the collection, and a non-null child clears the collection before adding that child.

## Factory And Template Context

Relation planning should expose enough metadata for templates to make a deliberate choice:

```text
relation.owned = true
relation.parentRefColumn = <column>
relation.ownedCardinality = ONE | MANY
relation.persistenceShape = ONE_TO_MANY_JOIN_COLUMN
relation.backingCollectionName = <plural relation field>
relation.singleAccessorName = <singular child property>
```

Factory planning should distinguish:

- structural parent reference fields
- managed fields
- inherited fields
- ordinary child payload fields
- unresolved constructor inputs

The default template should render a hidden collection backing field plus a public single-child computed property for `ownedCardinality=ONE`. `ownedCardinality=MANY` should keep the current public `MutableList<Child>` relation property. If a default factory cannot safely construct a child because structural or managed fields are not resolvable, it should fail fast or render an intentional construction stub rather than silently postponing the error to persistence.

## Model Changes

Likely model changes:

- Add owned-cardinality metadata to `AggregateRelationModel`, or introduce a dedicated owned-relation metadata model keyed by owner entity, target entity, and join column.
- Keep `relationType` / persistence shape separate from `ownedCardinality`.
- Add generator relation context fields for the hidden backing collection name and public single-child property name.
- Add collision validation so generated single-child property names cannot collide with scalar fields or other relation fields.
- Preserve existing `uniqueConstraints` on table and entity models.
- Use resolved managed-role metadata, not legacy booleans, when identifying scope and deleted columns.
- Use resolved soft-delete policy when available so `@Managed=deleted` and provider controls do not drift.

## Validation Strategy

Focused tests should cover:

1. `@Parent` child with no `@ParentRef` fails in the annotation contract iteration.
2. `@Parent` child with multiple `@ParentRef` columns fails in the annotation contract iteration.
3. `unique(parent_id)` infers `ONE`.
4. `primary key(parent_id)` infers `ONE`.
5. `unique(parent_id, deleted)` infers `ONE` only when `deleted` is `@Managed=deleted`.
6. `unique(tenant_id, parent_id)` infers `ONE` only when `tenant_id` is `@Managed=scope`.
7. `unique(tenant_id, parent_id, deleted)` infers `ONE` only when both neutral roles are declared.
8. `unique(parent_id, code)` infers `MANY`.
9. `unique(tenant_id, parent_id, code, deleted)` infers `MANY`.
10. `unique(parent_id, version)` infers `MANY`.
11. Nullable `scope` or `deleted` columns in the unique constraint do not prove `ONE`.
12. Generated relation template context exposes `ownedCardinality` without switching default owned persistence to physical `@OneToOne`.
13. `ownedCardinality=ONE` renders a private collection-backed persistence field and a public nullable single-child computed property.
14. The single-child getter fails fast when the backing collection contains more than one loaded child.
15. The single-child setter replaces the backing collection contents.

Focused test candidates:

```powershell
./gradlew.bat :cap4k-plugin-pipeline-core:test --tests "com.only4.cap4k.plugin.pipeline.core.DefaultCanonicalAssemblerTest"
./gradlew.bat :cap4k-plugin-pipeline-generator-aggregate:test
./gradlew.bat :cap4k-plugin-pipeline-gradle:test --tests "com.only4.cap4k.plugin.pipeline.gradle.PipelinePluginFunctionalTest"
```

Run tests only in an implementation turn where command side effects are acceptable.

## Acceptance Criteria

1. Owned cardinality inference uses only `@ParentRef`, primary key metadata, unique constraints, and resolved managed roles.
2. Owned cardinality inference does not use physical DB foreign-key metadata.
3. Owned cardinality inference does not fall back to `<parent_table>_id`.
4. `primary key(parent_ref)` infers `ownedCardinality=ONE`.
5. `unique(parent_ref)` infers `ownedCardinality=ONE`.
6. `unique(parent_ref, deleted)` infers `ownedCardinality=ONE` only when `deleted` is `@Managed=deleted` and non-null.
7. `unique(scope, parent_ref)` infers `ownedCardinality=ONE` only when `scope` is `@Managed=scope` and non-null.
8. `unique(scope, parent_ref, deleted)` infers `ownedCardinality=ONE` only when `scope` is `@Managed=scope`, `deleted` is `@Managed=deleted`, and both are non-null.
9. `unique(parent_ref, code)` infers `ownedCardinality=MANY`.
10. `unique(scope, parent_ref, code, deleted)` infers `ownedCardinality=MANY`.
11. `@Managed=system` and `@Managed=version` are not cardinality-neutral.
12. Default owned relation persistence remains collection-backed in the first implementation.
13. Template context exposes `ownedCardinality` separately from persistence shape.
14. `ownedCardinality=ONE` generated entity API exposes a nullable single-child property while hiding the collection backing field from normal public use.
15. The single-child getter fails fast if more than one child is present in the backing collection.
16. The single-child setter clears the backing collection and then adds the non-null assigned child.
17. Factory/template contexts can distinguish structural parent refs, managed fields, inherited fields, and unresolved constructor inputs.

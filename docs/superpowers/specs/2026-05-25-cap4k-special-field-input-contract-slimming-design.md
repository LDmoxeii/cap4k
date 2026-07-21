# Cap4k Special Field Input Contract Slimming Design

Date: 2026-05-25

## Context

Cap4k's DB input contract has accumulated several overlapping column annotations:

- legacy ID generation strategies such as `@GeneratedValue=uuid7` and `@GeneratedValue=snowflake-long`
- JPA mutability controls `@Insertable` and `@Updatable`
- `@Exposed`, which reverses a global managed-field default
- public docs that still describe `@Id` as a column-comment input even though the generator currently uses JDBC primary-key metadata as the ID source of truth

At the same time, the generator lacks one practical field-level capability: a DB column may exist and still be intentionally declared by an inherited superclass rather than by the generated entity class. This is especially useful for audit base classes.

## Goal

Slim the DB column annotation contract to the field semantics cap4k should own, add a field-level inherited-column marker, and remove stale or redundant annotations from docs and parser behavior.

## Non-Goals

- Do not introduce fine-grained audit field categories such as create-audit or update-audit.
- Do not map write-surface policy to JPA `insertable` or `updatable` lifecycle behavior.
- Do not remove schema facts from canonical planning just because a generated entity omits a property.
- Do not redesign `writeSurface`, `managedFields`, unique planning, relation planning, or Strong ID generation.

## Decisions

### ID Source

Database primary-key metadata is the aggregate/entity ID source of truth.

Public docs should stop advertising `@Id` as a DB column annotation. Existing implementation should not add an `@Id` parser path just to match stale docs.

### Removed Column Annotations

These annotations should be rejected by the DB column parser with actionable errors:

- `@GeneratedValue=uuid7`
- `@GeneratedValue=snowflake-long`
- `@GeneratedValue` marker with no value
- `@Insertable=true|false`
- `@Updatable=true|false`
- `@Exposed`

`@GeneratedValue=identity` and `@GeneratedValue=database-identity` remain supported as the explicit database-side ID-generation compatibility path.

### New Column Annotation

Add `@Inherited` as a marker-only DB column annotation.

Meaning:

- The column remains part of DB snapshot and canonical schema facts.
- The field remains visible to special-field policy resolution, unique planning, relation planning, and diagnostics.
- The default aggregate entity artifact omits the scalar property for that column.
- The omission is only for default entity source generation; authors can use template override or a base class to declare the field.

Invalid forms:

- `@Inherited=true`
- `@Inherited=false`
- any other explicit value

### Managed Fields

`@Managed` remains the write-surface marker for system-managed fields. It should not imply inherited declaration.

Audit base-class scenarios should use both concepts when needed:

- `@Managed` or `managedDefaultColumns` to exclude the field from generated write inputs
- `@Inherited` to omit the field from generated entity source

### Documentation Contract

Public authoring docs should present the recommended column contract as:

- primary key metadata for identity
- `@RefId` for current-context reference identities
- `@RefAggregate` for same-context aggregate references
- `@GeneratedValue=identity` / `@GeneratedValue=database-identity` only for database identity compatibility
- `@Deleted`
- `@Version`
- `@Managed`
- `@Inherited`
- `@Type` / `@T`
- `@Enum` / `@E`
- relation annotations

Docs should stop presenting removed annotations as supported inputs.

## Expected Behavior

Given a DB table with an audit column:

```sql
create table content (
  id varchar(36) primary key,
  title varchar(100) not null,
  created_at timestamp not null comment '@Managed;@Inherited;'
);
```

The canonical model still contains `createdAt`, and resolved special-field policy can still treat it as managed.

The default generated entity constructor and body should include `id` and `title`, but not `createdAt`.

The generated factory payload should already omit `createdAt` through the managed write-surface path.

## Compatibility

This is an intentional input-contract cleanup. Projects using removed annotations must migrate:

- `@GeneratedValue=uuid7` or `@GeneratedValue=snowflake-long`: remove the annotation and use the default primary-key / Strong ID path.
- marker `@GeneratedValue`: replace with `@GeneratedValue=identity` only when the database generates the ID.
- `@Insertable` / `@Updatable`: use template overrides for JPA-specific mappings.
- `@Exposed`: remove the field from global `managedDefaultColumns` or avoid broad managed defaults when local opt-out is needed.
- `@Id`: remove from docs and examples; rely on actual DB primary key metadata.

## Verification

Focused verification should include:

- parser tests for `@Inherited`
- parser rejection tests for removed annotations
- source-provider propagation of inherited metadata into `DbColumnSnapshot`
- canonical preservation of inherited fields
- aggregate entity planner/template omission of inherited scalar fields
- documentation scans proving removed annotations and stale `@Id` guidance are gone from active public authoring docs

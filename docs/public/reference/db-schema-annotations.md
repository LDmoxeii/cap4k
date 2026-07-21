# DB Schema Annotations

DB/schema comments are generator input facts for persistence and aggregate structure. The generator reads these annotations from DDL or database metadata.

DB table comments describe table ownership and exclusion. Value Object inputs use `types.valueObjectManifest`; enum inputs use `types.enumManifest`.

The DB comment contract is a strict, exact-name allow-list. Unsupported annotation names fail parsing; aliases are not normalized.

## Table Comment Annotations

| Annotation | Meaning |
| --- | --- |
| `@Parent=<table>` | Marks the table as an owned child entity table of another table. |
| `@Ignore` | Excludes the table from generation. |

## Column Comment Annotations

| Annotation | Meaning |
| --- | --- |
| `@ParentRef` | Marks the child-table column that binds an owned table to its parent table. |
| `@Type=<TypeName>` | Binds the generated field to an enum or value-object manifest type. |
| `@RefAggregate=<AggregateName>` | References another aggregate by aggregate name. |
| `@RefId=<TypeName>` | Marks an external reference identity type in the current context. |
| `@IdStrategy=db_identity` | Marks explicit database identity semantics on a primary-key column. |
| `@Managed=system` | Marks a framework-managed system field. |
| `@Managed=scope` | Marks a framework-managed scope field. |
| `@Managed=deleted` | Marks a framework-managed self-id soft-delete discriminator. Active rows use `0`; deleted rows store the row id. |
| `@Managed=version` | Marks the optimistic-lock version column. |
| `@Inherited` | Marks a managed field that is inherited by the generated concrete entity. |

## Rules

- Unsupported table annotations fail table comment parsing.
- Unsupported column annotations fail column comment parsing.
- `@Parent` requires a nonblank table value.
- `@Ignore`, `@ParentRef`, and `@Inherited` do not take explicit values.
- A table with `@Parent=<table>` must declare exactly one `@ParentRef` column.
- `@ParentRef` is valid only on child tables with `@Parent=<table>`.
- `@ParentRef` cannot combine with `@RefAggregate`, `@RefId`, or `@IdStrategy`.
- `@RefAggregate` and `@RefId` cannot be declared on the same column.
- `@IdStrategy` currently supports only `db_identity` and is valid only on a primary-key column.
- `@Managed` supports only `system`, `scope`, `deleted`, and `version`.
- `@Inherited` is valid only with `@Managed=system`, `@Managed=scope`, `@Managed=deleted`, or `@Managed=version`.
- `@Managed=deleted` requires a numeric, non-null column with a DB default compatible with `0`.
- For the `SELF_ID` tombstone strategy, the deleted column must be wide enough to store the id column value.
- Generated constructors may mirror an actual DB default discovered from schema, but must not synthesize `deleted = 0` when the DB default is absent.
- Aggregate projections do not inherit the active soft-delete filter in this iteration.

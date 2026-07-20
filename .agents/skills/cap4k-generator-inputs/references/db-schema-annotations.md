# DB Schema Annotations

Use these exact annotations in DB/schema DDL comments when the schema is the selected generator input surface.

The DB comment contract is a strict allow-list. Do not use aliases or removed compatibility names; unsupported names fail parsing.

## Table Annotations

- `@Parent=<table>`
- `@Ignore`

## Column Annotations

- `@ParentRef`
- `@Type=<TypeName>`
- `@RefAggregate=<AggregateName>`
- `@RefId=<TypeName>`
- `@IdStrategy=db_identity`
- `@Managed=system`
- `@Managed=scope`
- `@Managed=deleted`
- `@Managed=version`
- `@Inherited`

## Rules

- Unsupported table annotations fail table comment parsing.
- Unsupported column annotations fail column comment parsing.
- `@Parent` requires a nonblank table value.
- `@Ignore`, `@ParentRef`, and `@Inherited` do not take explicit values.
- A table with `@Parent=<table>` must declare exactly one `@ParentRef` column.
- `@ParentRef` is valid only on child tables with `@Parent=<table>`.
- `@ParentRef` cannot combine with `@RefAggregate`, `@RefId`, or `@IdStrategy`.
- `@IdStrategy` currently supports only `db_identity` and is valid only on a primary-key column.
- `@Managed` supports only `system`, `scope`, `deleted`, and `version`.
- `@Inherited` is valid only with `@Managed=system`, `@Managed=scope`, `@Managed=deleted`, or `@Managed=version`.

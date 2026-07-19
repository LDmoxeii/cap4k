# DB Schema Annotations

Use these annotations in DB/schema DDL comments when the schema is the selected generator input surface.

DB table comments describe table ownership, aggregate-root metadata, exclusion, and JPA provider controls. Value Object inputs use `types.valueObjectManifest`; they are not DB table annotations.

## Table Annotations

- `@Parent=<table>` / `@P=<table>`
- `@AggregateRoot=<bool>` / `@Root=<bool>` / `@R=<bool>`
- `@Ignore` / `@I`
- `@DynamicInsert=<bool>`
- `@DynamicUpdate=<bool>`

## Column Annotations

- `@T=<TypeName>` / `@TYPE=<TypeName>`
- `@E=<items>` / `@ENUM=<items>`
- `@RefId=<TypeName>`
- `@Deleted`
- `@Version`
- `@GeneratedValue=identity`
- `@GeneratedValue=database-identity`
- `@Managed`
- `@Inherited`
- `@Reference=<table>` / `@Ref=<table>`
- `@Relation=<type>` / `@Rel=<type>`
- `@Lazy=<bool>` / `@L=<bool>`
- `@Count=<value>` / `@C=<value>`
- `@RefAggregate=<AggregateName>`

## Rules

- Unsupported table annotations fail table comment parsing.
- Presence annotations do not take explicit values.
- Boolean values are strict lowercase `true` or `false`.
- `@Parent` / `@P` cannot combine with `@AggregateRoot=true`, `@Root=true`, or `@R=true`.
- `@E` / `@ENUM` requires `@T` / `@TYPE`.
- `@Relation` / `@Rel`, `@Lazy` / `@L`, and `@Count` / `@C` require `@Reference` or `@Ref` on the same column.
- `@Relation` / `@Rel` supports `MANY_TO_ONE`, `ONE_TO_ONE`, `1:1`, `*:1`, `MANYTOONE`, and `ONETOONE`.
- `@RefAggregate` conflicts with `@Reference` / `@Ref`.
- `@RefAggregate` conflicts with `@RefId`.

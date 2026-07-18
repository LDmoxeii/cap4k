# DB Schema Annotations

Use these annotations in DB/schema DDL comments when the schema is the selected generator input surface.

## Table Annotations

- `@Parent=<table>` / `@P=<table>`
- `@AggregateRoot=<bool>` / `@Root=<bool>` / `@R=<bool>`
- `@ValueObject` / `@VO`
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

- Presence annotations do not take explicit values.
- Boolean values are strict lowercase `true` or `false`.
- `@Parent` / `@P` cannot combine with `@AggregateRoot=true`, `@Root=true`, or `@R=true`.
- `@E` / `@ENUM` requires `@T` / `@TYPE`.
- `@Relation` / `@Rel`, `@Lazy` / `@L`, and `@Count` / `@C` require `@Reference` or `@Ref` on the same column.
- `@Relation` / `@Rel` supports `MANY_TO_ONE`, `ONE_TO_ONE`, `1:1`, `*:1`, `MANYTOONE`, and `ONETOONE`.
- `@RefAggregate` conflicts with `@Reference` / `@Ref`.
- `@RefAggregate` conflicts with `@RefId`.

## Rejected Annotations

These annotations are outside the supported input set:

- Table: `@IdGenerator` / `@IG`
- Table: `@SoftDeleteColumn`
- Column: `@Exposed`
- Column: `@Insertable`
- Column: `@Updatable`

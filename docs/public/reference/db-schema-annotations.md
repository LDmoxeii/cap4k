# DB Schema Annotations

DB/schema comments are generator input facts for persistence and aggregate structure. The generator reads these annotations from DDL or database metadata.

DB table comments describe table ownership, aggregate-root metadata, exclusion, and JPA provider controls. Value Object inputs use `types.valueObjectManifest`; they are not DB table annotations.

## Table Comment Annotations

| Annotation | Meaning |
| --- | --- |
| `@Parent=<table>` / `@P=<table>` | Marks the table as an owned child entity table of another table. |
| `@AggregateRoot=<bool>` / `@Root=<bool>` / `@R=<bool>` | Explicitly marks aggregate-root state. |
| `@Ignore` / `@I` | Excludes the table from generation. |
| `@DynamicInsert=<bool>` | Declares dynamic insert metadata. |
| `@DynamicUpdate=<bool>` | Declares dynamic update metadata. |

## Column Comment Annotations

| Annotation | Meaning |
| --- | --- |
| `@T=<TypeName>` / `@TYPE=<TypeName>` | Overrides or binds the generated field type. |
| `@E=<items>` / `@ENUM=<items>` | Declares enum items; requires `@T` / `@TYPE`. |
| `@RefId=<TypeName>` | Marks an external reference identity type in the current context. |
| `@Deleted` | Marks a soft-delete column. |
| `@Version` | Marks an optimistic-lock version column. |
| `@GeneratedValue=identity` / `@GeneratedValue=database-identity` | Marks explicit database identity semantics. |
| `@Managed` | Marks framework-managed field metadata. |
| `@Inherited` | Marks inherited field metadata. |
| `@Reference=<table>` / `@Ref=<table>` | Specifies the referenced table for relation metadata. |
| `@Relation=<type>` / `@Rel=<type>` | Specifies relation type. |
| `@Lazy=<bool>` / `@L=<bool>` | Marks lazy relation metadata. |
| `@Count=<value>` / `@C=<value>` | Marks relation count metadata. |
| `@RefAggregate=<AggregateName>` | References another aggregate by aggregate name. |

## Rules

- Unsupported table annotations fail table comment parsing.
- `@Parent` / `@P` cannot combine with aggregate-root true.
- Presence annotations do not take explicit values.
- Boolean values use strict lowercase `true` or `false`.
- `@E` / `@ENUM` requires `@T` / `@TYPE`.
- `@Relation` / `@Rel`, `@Lazy` / `@L`, and `@Count` / `@C` require `@Reference` or `@Ref` on the same column.
- `@Relation` / `@Rel` supports `MANY_TO_ONE`, `ONE_TO_ONE`, `1:1`, `*:1`, `MANYTOONE`, and `ONETOONE`.
- `@RefAggregate` conflicts with `@Reference` / `@Ref`.
- `@RefAggregate` conflicts with `@RefId`.

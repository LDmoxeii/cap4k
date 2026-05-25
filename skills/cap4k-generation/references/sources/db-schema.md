# DB Schema

DDL annotations describe aggregate/entity storage input. They do not declare command/query/event contracts, and relation or field-mapping facts do not become standalone output families.

## Table Annotations

- `@Parent=<table>` / `@P=<table>` links a table to its parent table. Explicit value required. Use it to place an entity or child carrier under an aggregate tree, not to imply runtime ownership checks by itself.
- `@AggregateRoot=true|false` / `@Root=true|false` / `@R=true|false` marks whether the table is the aggregate root when present. Marker form without `=true|false` is invalid. Use it to disambiguate aggregate boundaries; do not treat omission as a place to invent a second root.
- `@ValueObject` / `@VO` is marker-only. Use it for table-backed value-object storage. Common misuse: adding `=true` or using it for the normal inline/custom-type path that should instead use `@T` plus `types.registryFile`.
- `@Ignore` / `@I` is marker-only. Use it to remove a table from generation input. Common misuse: passing an explicit value.
- `@DynamicInsert=true|false` and `@DynamicUpdate=true|false` are persistence tuning hints, not aggregate-shape contracts.

## Column Annotations

- Database primary-key metadata is the ID source of truth. Aggregate-root primary keys generate Strong ID types by default; do not add an `@Id` column-comment marker.
- `@Type=<TypeName>` / `@T=<TypeName>` binds a field to a named custom type. The meaningful authoring form is an explicit type name; blank or marker-only forms are ignored. Use it with enum manifest or `types.registryFile`; do not rely on marker-only `@T`.
- `@Enum=<...>` / `@E=<...>` marks enum-backed storage. The meaningful authoring form is an explicit enum payload; blank or marker-only forms are ignored, and explicit enum payload still requires `@T`. Use it only when the type name is already declared; common misuse is `@Enum=<...>` without a matching `@T`.
- `@RefId=<TypeName>` maps an external concept into a current-context identity name such as `AuthorId`.
- `@RefAggregate=<AggregateName>` marks a same-context aggregate reference and resolves to the referenced aggregate ID type.
- `@GeneratedValue=identity` / `@GeneratedValue=database-identity` are compatibility input for explicit database identity generation semantics. Marker-only `@GeneratedValue`, `@GeneratedValue=uuid7`, and `@GeneratedValue=snowflake-long` are rejected.
- `@Deleted`, `@Version`, and `@Managed` are marker-only and reject explicit values. Use them for supported column roles only.
- `@Inherited` is marker-only. It keeps the column in canonical facts but omits the default generated entity field, so a mapped superclass or template override must declare the field mapping. If used on an ID column, that superclass or override must supply the ID mapping too.
- `@Exposed`, `@Insertable`, and `@Updatable` are rejected. Use narrower managed defaults or template overrides instead.
- `@SoftDeleteColumn` is legacy and rejected. Use `@Deleted` instead.

## Relation Annotations

- `@Reference=<table>` / `@Ref=<table>` points to the referenced table. Explicit value required. It identifies the carrier side of a DB relation; it does not create an independent plan family.
- `@Relation=ManyToOne|OneToOne|*:1|1:1` / `@Rel=...` declares the relation kind. Explicit value required and `@Reference` is required.
- `@Lazy=true|false` / `@L=true|false` is an explicit relation-loading hint. Explicit value required and `@Reference` is required.
- `@Count=<hint>` / `@C=<hint>` is an explicit counted-relation hint. Explicit value required and `@Reference` is required.
- many-to-many is unsupported.

## Practical Boundaries

- unique constraints are the source for unique helpers, so names must be stable and business-meaningful.
- JSON-backed or inline value carriers normally use `@T=<TypeName>` plus `types.registryFile`.
- Relation and field-mapping facts stay inside aggregate/entity generation input; missing aggregate-family output after these facts exist is generation drift, not a modeling gap.

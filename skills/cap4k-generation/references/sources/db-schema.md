# DB Schema

Table annotations:

- `@Parent` / `@P=<table>`
- `@AggregateRoot` / `@Root` / `@R=true|false`
- `@ValueObject` / `@VO`
- `@Ignore` / `@I`
- `@DynamicInsert=true|false`
- `@DynamicUpdate=true|false`

Column annotations:

- `@Type` / `@T=<TypeName>`
- `@Enum` / `@E=...`
- `@GeneratedValue`
- `@GeneratedValue=uuid7`
- `@GeneratedValue=snowflake-long`
- `@GeneratedValue=identity`
- `@Deleted`
- `@Version`
- `@Managed`
- `@Exposed`
- `@Insertable=true|false`
- `@Updatable=true|false`

Relation annotations:

- `@Reference` / `@Ref=<table>`
- `@Relation` / `@Rel=ManyToOne|OneToOne|*:1|1:1`
- `@Lazy` / `@L=true|false`
- `@Count` / `@C=<hint>`

Rules:

- `@Relation`, `@Lazy`, and `@Count` require `@Reference`.
- `@Enum` requires `@Type` / `@T`.
- `@Managed` and `@Exposed` are mutually exclusive.
- many-to-many is unsupported.
- unique constraints are the source for unique helpers; names must be stable and business-meaningful.
- JSON-backed or inline value carriers normally use `@T=<TypeName>` plus `types.registryFile`.
- `@VO` is for table-backed value-object storage, not the default value-object path.

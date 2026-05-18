# DB Schema

Table annotations:

- `@Parent=<table>` or `@P=<table>`
- `@AggregateRoot=true|false` or `@Root=true|false` or `@R=true|false`
- `@ValueObject` / `@VO`
- `@Ignore` / `@I`
- `@DynamicInsert=true|false`
- `@DynamicUpdate=true|false`

Column annotations:

- `@Type=<TypeName>` or `@T=<TypeName>`
- `@Enum=...` or `@E=...`
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

- `@Reference=<table>` or `@Ref=<table>`
- `@Relation=ManyToOne|OneToOne|*:1|1:1` or `@Rel=ManyToOne|OneToOne|*:1|1:1`
- `@Lazy=true|false` or `@L=true|false`
- `@Count=<hint>` or `@C=<hint>`

Rules:

- `@Relation`, `@Lazy`, and `@Count` require `@Reference`.
- `@Enum=...` / `@E=...` requires `@Type=<TypeName>` / `@T=<TypeName>`.
- `@Managed` and `@Exposed` are mutually exclusive.
- many-to-many is unsupported.
- unique constraints are the source for unique helpers; names must be stable and business-meaningful.
- JSON-backed or inline value carriers normally use `@T=<TypeName>` plus `types.registryFile`.
- `@VO` is for table-backed value-object storage, not the default value-object path.

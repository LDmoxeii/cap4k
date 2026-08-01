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
| `@Managed=<policy-key>` | Selects one exact infrastructure-owned field policy. |

Managed policy keys are case-sensitive lowercase kebab-case segments separated by dots. They match:

```text
[a-z][a-z0-9-]*(\.[a-z][a-z0-9-]*)*
```

Standard keys include:

| Policy key | Meaning |
| --- | --- |
| `identifier.uuid7` | Application UUID7 identifier; preserve a valid explicit value or allocate at aggregate admission. |
| `identifier.snowflake` | Application Snowflake identifier; preserve a valid explicit value or allocate at aggregate admission. |
| `identifier.assigned` | Caller-assigned identifier required at aggregate admission. |
| `identifier.database-identity` | Database-assigned identity, unavailable until provider persistence. |
| `version` | Hibernate optimistic-lock version. |
| `soft-delete` | Generated active sentinel plus provider-owned tombstone transition. |
| `database.generated-on-insert` | Database-generated INSERT value. |
| `database.generated-always` | Database-generated INSERT and UPDATE value. |
| `scope.tenant` | Admission-time tenant context binding and equality validation. |
| `initialization.request-context` | Project-defined admission-time value from execution context. |
| `enrichment.audit-time.created-at` | Persistence-enriched creation time. |
| `enrichment.audit-time.updated-at` | Persistence-enriched update time. |
| `enrichment.audit-actor.created-by` | Persistence-enriched creation actor. |
| `enrichment.audit-actor.updated-by` | Persistence-enriched update actor. |

Pipeline Extensions may define additional exact policy keys. The DB parser validates key syntax; canonical resolution validates that an installed definition owns every selected key.

## Rules

- Unsupported table or column annotations fail comment parsing.
- `@Parent` requires a nonblank table value.
- `@Ignore` and `@ParentRef` do not take explicit values.
- `@Managed` requires one nonblank exact policy key and may appear only once on a column.
- A table with `@Parent=<table>` must declare exactly one `@ParentRef` column.
- `@ParentRef` is valid only on child tables with `@Parent=<table>`.
- `@ParentRef` is structural owned-relation metadata. It does not generate a child parent-ID scalar or inverse parent navigation by default.
- An owned child must declare an independent primary key. Using its `@ParentRef` column as the primary key is rejected.
- Parent-owned persistence mappings and Schema joins continue to use the physical `@ParentRef` column.
- `@ParentRef` cannot combine with `@RefAggregate` or `@RefId`.
- `@RefAggregate` and `@RefId` cannot be declared on the same column.
- An `identifier.*` policy is valid only on the single physical primary-key column.
- A physical primary key resolves an explicit identifier policy first, then `managedFields.identifierDefaultPolicy`; unresolved or composite physical identifiers fail.
- Managed policy selection does not erase physical JDBC type, nullability, default, generated-value, unique-constraint, or relation evidence.
- Field declaration placement is user/template code shape. DB comments do not require a Cap4k base class or decide whether a Kotlin field is inherited.
- Default Factory input exposure is separate from constructor freedom and JPA column participation.
- Application identifiers are initialized no later than root or owned-child admission; loaded entities are never reinitialized.
- Audit enrichment applies only after candidate change recognition. Reading a clean aggregate does not update audit fields or emit audit-only SQL.
- `scope.tenant` binds and validates a value. It does not automatically install Hibernate filters, add query predicates, or route schemas/databases.
- For the `SELF_ID` tombstone strategy, the soft-delete column must be wide enough to store the identifier and its generated active sentinel participates in INSERT.
- Aggregate projections do not inherit the active soft-delete filter in this iteration.

## Configuration Defaults

Repeated columns may select exact policy keys through Gradle configuration:

```kotlin
managedFields {
    identifierDefaultPolicy.set("identifier.uuid7")
    columnPolicyDefaults.put("created_at", "enrichment.audit-time.created-at")
    columnPolicyDefaults.put("updated_at", "enrichment.audit-time.updated-at")
    columnPolicyDefaults.put("tenant_id", "scope.tenant")
}
```

Resolution precedence is explicit column policy, exact column default, then identifier default where applicable. The canonical plan records selection provenance separately from the built-in or Pipeline Extension definition owner.

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
- `@Managed=<policy-key>`

Managed policy keys match `[a-z][a-z0-9-]*(\.[a-z][a-z0-9-]*)*`. Standard keys are:

- `identifier.uuid7`
- `identifier.snowflake`
- `identifier.assigned`
- `identifier.database-identity`
- `version`
- `soft-delete`
- `database.generated-on-insert`
- `database.generated-always`
- `scope.tenant`
- `initialization.request-context`
- `enrichment.audit-time.created-at`
- `enrichment.audit-time.updated-at`
- `enrichment.audit-actor.created-by`
- `enrichment.audit-actor.updated-by`

Pipeline Extensions may contribute additional exact keys. Syntax acceptance does not prove that a policy definition is installed; canonical resolution owns that check.

## Rules

- Unsupported table and column annotations fail parsing.
- `@Parent` requires a nonblank table value.
- `@Ignore` and `@ParentRef` do not take explicit values.
- `@Managed` requires one exact nonblank policy key and may appear only once per column.
- A table with `@Parent=<table>` must declare exactly one `@ParentRef` column.
- `@ParentRef` is valid only on child tables with `@Parent=<table>`.
- `@ParentRef` cannot combine with `@RefAggregate` or `@RefId`.
- `@RefAggregate` and `@RefId` cannot be declared on the same column.
- `identifier.*` is valid only on the single physical primary key. Explicit policy wins, then the configured identifier default.
- Managed policy selection does not replace physical schema facts.
- Default Factory exposure, constructor shape, runtime value authority, and JPA INSERT/UPDATE participation are separate resolved decisions.
- Application identifiers and context-bound values are initialized at root or owned-child admission, not first allocated during UoW flush.
- Audit enrichment applies only to persistence candidates; a clean read does not become dirty for audit alone.
- Tenant field binding is not automatic query filtering or database routing.
- DB comments do not dictate field inheritance or require an audit base class.

# Cap4k Aggregate Unique Family Naming Contract Design

Date: 2026-05-03

Status: Implemented

Completion note: Implemented by the aggregate unique family naming contract slice. The pipeline now preserves physical DB unique names, carries named unique constraints into canonical aggregate entities, resolves `uk` / `uk_v_<fragment>` families through the shared aggregate unique planner, filters soft-delete and optimistic-lock version control fields from generated unique APIs, and exposes resolved unique metadata in `cap4kPlan`.

## Problem

The pipeline aggregate unique family currently derives generated type names from unique constraint columns only.

That produces technically valid but semantically noisy names when a database unique constraint contains non-business control fields:

```text
UniqueCategoryCodeDeleted
UniqueCategoryCodeDeletedQry
UniqueCategoryCodeDeletedQryHandler
```

The core issue is not only the `Deleted` suffix. The current source model drops the physical unique index or constraint name after JDBC introspection, so the aggregate unique planner cannot use DDL naming as the user's intended business name.

The current behavior also diverges from the structure-first direction of cap4k:

- users already express uniqueness in DDL
- forcing users to repeat unique family names in Gradle DSL would split the source of truth
- generated query, query handler, and validator names must stay aligned
- a table may have multiple unique constraints, so the naming contract must support multiple generated unique families per entity without collision

## Goal

Provide a stable aggregate unique family naming contract based primarily on database unique constraint names.

The first implementation slice should:

- preserve unique constraint physical names from DB source collection
- normalize portable physical names such as `category_uk_v_code` into logical names such as `uk_v_code`
- support two user-facing naming forms:
  - `Unique<Entity><Fragment>`
  - `Unique<Entity>`
- exclude soft-delete and optimistic-lock version columns from fallback business-name suffixes
- fail fast on generated type-name collisions
- keep query, query handler, and validator families aligned
- avoid a Gradle DSL naming override in the first slice

## Non-goals

This slice must not:

- introduce a DSL-first unique naming system
- add a family-level conflict policy system
- redesign unique validator logic
- change query handler persistence implementation
- change unique SQL semantics or database indexes
- implement database migration tooling
- change the legacy `cap4k-plugin-codegen`
- solve every database-specific metadata edge case beyond MySQL, H2, and PostgreSQL-compatible behavior

## Current State

`DbSchemaSourceProvider` currently calls JDBC `DatabaseMetaData.getIndexInfo(...)` and reads `INDEX_NAME`, `COLUMN_NAME`, and `ORDINAL_POSITION`.

However, the pipeline API model stores unique constraints as:

```kotlin
val uniqueConstraints: List<List<String>>
```

This preserves ordered columns but drops the physical name. Once that happens, later stages can only generate suffixes from fields.

`AggregateUniqueConstraintPlanning` currently derives:

```text
Unique<Entity><ConcatenatedSelectedFields>
Unique<Entity><ConcatenatedSelectedFields>Qry
Unique<Entity><ConcatenatedSelectedFields>QryHandler
```

It has no visibility into:

- the physical unique constraint or index name
- whether a column is only the configured soft-delete column
- whether a column is an optimistic-lock version column
- final generated-name collisions across multiple unique constraints

## Naming Contract

The supported physical unique names are:

```text
uk
uk_v_<fragment>
<table>_uk
<table>_uk_v_<fragment>
```

The logical normalization is:

```text
<table>_uk              -> uk
<table>_uk_v_<fragment> -> uk_v_<fragment>
uk                      -> uk
uk_v_<fragment>         -> uk_v_<fragment>
```

`<table>_` prefix removal is allowed only when it exactly matches the current table name. Matching should be case-insensitive because JDBC metadata casing differs across databases and drivers.

Examples:

```text
category_uk_v_code -> uk_v_code -> UniqueCategoryCode
uk_v_code          -> uk_v_code -> UniqueCategoryCode
category_uk        -> uk        -> UniqueCategory
uk                 -> uk        -> UniqueCategory
```

`uk` means "the default unique family for this entity". It generates no suffix.

`uk_v_<fragment>` means "use `<fragment>` as the unique family suffix". The fragment is converted to UpperCamelCase.

Examples:

```text
uk_v_message_key -> MessageKey
uk_v_upload_id   -> UploadId
uk_v_email       -> Email
```

## Fallback Naming

If the normalized physical name does not match `uk` or `uk_v_<fragment>`, the planner falls back to selected business fields.

Fallback suffix derivation must:

- resolve unique constraint columns to entity fields using existing column and field-name matching rules
- exclude the table's configured soft-delete column
- exclude the table's explicit optimistic-lock version column
- preserve unique constraint column order for remaining business fields
- convert the remaining field names to UpperCamelCase and concatenate them

Example:

```text
UNIQUE (code, deleted)
softDeleteColumn = deleted
fallback suffix = Code
```

Example:

```text
UNIQUE (message_key, version)
versionFieldName = version
fallback suffix = MessageKey
```

If every column is filtered out and the physical name was not an explicit `uk`, generation must fail fast. A non-explicit constraint whose only columns are control fields does not carry enough business meaning to produce a stable unique family name.

## Request Field Contract

Naming exclusion is not the same thing as query input exclusion.

The first slice should use the same filtered business fields for generated `Request` properties:

- soft-delete columns should not appear in generated unique query or validator request properties
- optimistic-lock version columns should not appear in generated unique query or validator request properties
- handler templates may apply soft-delete filtering internally when needed by the selected persistence technology

This keeps generated APIs aligned with business uniqueness rather than database control columns.

## Model Changes

The source model needs to preserve the physical unique name.

The current model:

```kotlin
val uniqueConstraints: List<List<String>>
```

should become a named constraint model, for example:

```kotlin
data class DbUniqueConstraintSnapshot(
    val physicalName: String,
    val columns: List<String>,
)
```

`DbTableSnapshot` should carry:

```kotlin
val uniqueConstraints: List<DbUniqueConstraintSnapshot>
```

The name should be `physicalName` or `sourceName`, not `constraintName`, because JDBC `getIndexInfo(...)` returns `INDEX_NAME`. Some databases expose a unique constraint and its backing index differently. The aggregate unique planner only needs a stable source name for generated type naming.

Canonical `EntityModel` should also carry the named unique constraints instead of flattening them back to column lists. Do not preserve the physical name in source only and drop it again in canonical assembly.

## Planner Behavior

The aggregate unique planner should resolve one `AggregateUniqueConstraintSelection` per named unique constraint.

Each selection should contain the final generated names:

```text
validatorTypeName
queryTypeName
queryHandlerTypeName
```

All aggregate unique artifacts must use the same resolved unique family name:

```text
Unique<Entity><Suffix>
Unique<Entity><Suffix>Qry
Unique<Entity><Suffix>QryHandler
```

The resolved suffix is selected in this order:

1. If normalized name is `uk`, use an empty suffix.
2. If normalized name is `uk_v_<fragment>`, use `UpperCamel(fragment)`.
3. Otherwise, use fallback business-field suffix.

The planner must expose enough context for `cap4kPlan` to show what happened:

```text
physicalName
normalizedName
resolvedSuffix
selectedBusinessFields
filteredControlFields
```

This is important because generated names are source-driven and users need to review them before generation.

## Conflict Rules

The planner must fail fast for ambiguous or colliding output.

Required failures:

- the same entity has more than one unique constraint resolving to empty suffix
- two unique constraints in the same project produce the same validator type FQN
- two unique constraints in the same project produce the same query type FQN
- two unique constraints in the same project produce the same query handler type FQN
- fallback naming filters out all columns for a non-explicit `uk`
- a unique constraint references a column that cannot be resolved to an entity field

The planner must not auto-append numeric suffixes such as `UniqueCategoryCode2`. Automatic disambiguation would hide a schema/design problem and create unstable generated APIs.

## Database Portability

The naming contract must tolerate different physical naming scopes across MySQL, H2, and PostgreSQL.

The design assumption is:

- MySQL projects often reuse short unique key names such as `uk_v_email` across tables.
- H2 requires globally unique constraint names within the schema in practical dogfood usage, so test material may need names like `customer_profile_uk_v_email`.
- PostgreSQL metadata should not be treated as globally unique by constraint name alone; table/schema identity must remain part of the source identity.

Therefore:

- `physicalName` is metadata, not a global identity.
- source and canonical models must identify unique constraints by table plus physical name plus columns.
- logical naming may strip the exact current table prefix.
- table prefix stripping is only a portability normalization step; the stripped prefix must not leak into generated Kotlin type names.

## DSL Decision

Do not add a Gradle DSL naming override in the first slice.

Reasoning:

- the DDL already declares uniqueness
- duplicating unique family names in Gradle would split user intent across two files
- users should not need to write schema first and then mirror naming in plugin config
- the naming contract can be expressed by stable DB names and source metadata

A future DSL escape hatch may be reconsidered only if real evidence shows JDBC metadata cannot provide stable names in a supported database.

If such an escape hatch is ever added, it should be artifact-plan-visible and should key by stable table plus physical unique name or column set. It should not become the default path.

## Soft-delete and Version Fields

Soft-delete and optimistic-lock version fields are database control fields.

They may participate in database uniqueness for physical correctness, but they should not participate in generated business names or generated request payloads by default.

The fields are resolved from existing pipeline metadata:

- soft-delete field from table-level `softDeleteColumn`
- version field from explicit column-level `@Version=true`, carried into `versionFieldName`

This slice should not introduce new soft-delete or version-field declaration semantics. A separate backlog item already tracks database special-field declaration contract unification.

## Migration Impact

For `only-danmuku-zero`, this contract should allow generated names such as:

```text
category_uk_v_code -> UniqueCategoryCode
user_uk_v_email    -> UniqueUserEmail
user_uk_v_phone    -> UniqueUserPhone
video_post_processing_uk -> UniqueVideoPostProcessing
```

It should also remove the generated `Deleted` suffix from unique families when `deleted` is the configured soft-delete column.

After implementation, dogfood cleanup should remove migrated hand-written duplicate unique query, query handler, and validator files that were kept only because current generated names were noisy or mismatched.

## Tests

Implementation should include tests at these levels:

- DB source provider preserves physical unique names and ordered columns.
- H2-style `<table>_uk_v_<fragment>` normalizes to `uk_v_<fragment>`.
- MySQL-style `uk_v_<fragment>` works without table prefix.
- `uk` and `<table>_uk` generate empty suffix.
- soft-delete columns are filtered from fallback names and request properties.
- explicit version columns are filtered from fallback names and request properties.
- duplicate empty-suffix unique constraints fail fast.
- duplicate final generated type names fail fast.
- fallback with only filtered control fields fails fast unless the normalized name is explicit `uk`.
- `cap4kPlan` exposes enough context to review physical name, normalized name, resolved suffix, selected business fields, and filtered control fields.

## Acceptance Criteria

The slice is complete when:

- unique physical names survive DB source collection and canonical assembly
- aggregate unique generated names follow `uk` / `uk_v_<fragment>` contract
- `<table>_` portability prefixes do not appear in generated Kotlin type names
- soft-delete and version fields are excluded from fallback business names and request payloads
- query, query handler, and validator artifacts use one shared resolved unique family name
- ambiguous or colliding generated names fail fast
- existing aggregate unique generation still works when no special physical name is provided
- relevant pipeline API/core/source/generator/renderer/Gradle functional tests pass

## Open Decisions

No open user-facing decisions remain for the first slice.

The only deferred decision is whether a future DSL escape hatch is needed. It is intentionally not part of this design.


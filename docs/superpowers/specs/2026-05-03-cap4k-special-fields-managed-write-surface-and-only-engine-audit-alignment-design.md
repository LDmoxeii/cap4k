# Cap4k Special-Fields Managed Write-Surface and only-engine Audit Alignment Design

Date: 2026-05-03

Status: Proposed

Scope: define one cross-repository contract that keeps existing cap4k special-field declaration behavior, adds managed write-surface resolution for non-business/system fields, and introduces only-engine runtime audit autofill with Sa-Token bridge so generated entities no longer depend on audited base-class inheritance.

Out of scope: repository backend replacement, frontend TypeScript generation, read/write association separation, inverse-navigation fetch policy, legacy compatibility.

## Background and Dependency

This is a follow-up integration spec.

The executed spec below stays unchanged:

- `cap4k/docs/superpowers/specs/2026-05-03-cap4k-database-special-field-declaration-contract-unification-design.md`

This new spec does not rewrite that file. It extends the delivered direction with runtime audit and write-surface alignment across:

- `cap4k`
- `only-engine`
- `only-danmuku-zero`

## Problem

Current state still has a split between compile-time declaration and runtime behavior:

1. cap4k special-field contract already unifies `id` / `@Deleted` / `@Version` declaration direction.
2. audit fields (`create*` / `update*`) are still handled by domain base-class inheritance in `only-danmuku-zero` (`AuditedEntity` / `AuditedFieldsEntity` + `AuditSupport.register(...)` bridge).
3. user-write exposure control for special/system fields is not expressed as one explicit cross-repo contract.
4. only-engine has no dedicated reusable audit module; Sa-Token integration is available, but not exposed as a standard audit operator SPI.

This keeps migration cost high and prevents a clean "column contract first, runtime plug-in second" model.

## Goal

Define one explicit contract in one spec:

1. Keep DSL as project-level fallback only.
2. Keep DB column annotation as entity/field override only.
3. Add managed write-surface semantics so non-business/system fields are hidden from user write paths by default.
4. Add only-engine pluggable audit module (`engine-audit`) for runtime autofill of `create*` / `update*`.
5. Add engine-satoken default operator provider bridge for audit module.
6. Remove `only-danmuku-zero` dependency on audited entity base class inheritance while keeping audit columns.

## Non-goals

This slice must not:

- modify or re-open the already executed spec file content.
- keep legacy comment styles such as `@SoftDeleteColumn=...` or `@Version=true`.
- introduce aggregate-level ID uniformity enforcement.
- introduce full audit history/event log storage.
- force projects to use Sa-Token (Sa-Token is one provider implementation, not the only one).

## Core Decisions

1. `specialFields` is the unified surface for special/system field declaration and write-surface governance.
2. `id`, `@Deleted`, and `@Version` fields are protected managed fields by definition.
3. additional system-managed fields are resolved by:
   - project default names in DSL (`managedDefaultColumns`)
   - DB column markers (`@Managed`, `@Exposed`)
4. "managed" means user write-surface governance, not "JPA column must be insertable/updatable false".
5. application-side ID remains create-time assignable (`CREATE_ONLY` semantics for user write surface).
6. runtime audit value population is moved out of domain base class and into only-engine pluggable module.
7. entity resolution remains entity-scoped; no aggregate-level strict uniformity rule is added.

## Unified Contract Surface

### A) cap4k DSL (project fallback only)

Conceptual shape:

```kotlin
cap4k {
    generators {
        aggregate {
            specialFields {
                idDefaultStrategy.set("uuid7")
                deletedDefaultColumn.set("deleted")
                versionDefaultColumn.set("version")
                managedDefaultColumns.set(
                    listOf(
                        "create_user_id",
                        "create_by",
                        "create_time",
                        "update_user_id",
                        "update_by",
                        "update_time",
                    )
                )
            }
        }
    }
}
```

Rules:

- no aggregate-level special-field override DSL.
- no entity-level special-field override DSL.
- missing managed default columns on an entity are non-failure by design.
- missing deleted/version default columns on an entity are non-failure by design.

### B) DB column markers (local override)

Allowed markers:

- `@GeneratedValue`
- `@GeneratedValue=<strategy>`
- `@Deleted`
- `@Version`
- `@Managed`
- `@Exposed`

Rules:

- all above are column-level only.
- valued boolean forms like `@Version=true` are invalid.
- `@Managed` and `@Exposed` are mutually exclusive on the same column.
- `@Exposed` cannot downgrade protected fields (`id`, resolved deleted, resolved version).

## Managed Write-Surface Semantics

### Policy vocabulary

- `READ_WRITE`
- `CREATE_ONLY`
- `READ_ONLY`
- `SYSTEM_TRANSITION_ONLY`

### Default mapping

- application-side ID (`uuid7`, `snowflake-long`): `CREATE_ONLY`
- database identity ID (`identity`, `database-identity`): `READ_ONLY`
- version field: `READ_ONLY`
- deleted field: `SYSTEM_TRANSITION_ONLY`
- resolved managed audit/system fields: `READ_ONLY` (unless explicitly `@Exposed` and not protected)

### Important clarification: ID is managed but not always read-only

ID is always managed in governance terms, but application-side ID remains create-writable:

- create path: writable
- update path: non-writable

This preserves existing pre-assigned application ID behavior.

## cap4kPlan Required Exposure

`cap4kPlan` must expose per-entity resolved output including:

- resolved ID strategy/kind/source/writePolicy
- resolved deleted marker source/writePolicy
- resolved version marker source/writePolicy
- resolved managed fields list (`fieldName`, `columnName`, `source`, `writePolicy`)
- resolved write surface (`createAllowedFields`, `updateAllowedFields`)

Purpose:

- make review explicit before generation/use.
- allow business teams to verify protected field behavior early.

## only-engine `engine-audit` Module Design

### Module position

Add new module:

- `only-engine/engine-audit`

and include in:

- `only-engine/settings.gradle.kts`

### Responsibility

Provide runtime audit autofill via JPA/Hibernate lifecycle callbacks and SPI.

The module should:

1. discover current operator identity through SPI.
2. fill `create*` on insert when null.
3. always update `update*` on update.
4. support project-level configurable column/field names with sane defaults.
5. avoid forcing domain entity inheritance from audit base classes.

### SPI contract

In `engine-spi`, add operator provider SPI (name can be finalized in implementation, contract intent is fixed):

```kotlin
interface AuditOperatorProvider {
    fun currentOperatorId(): Any?
    fun currentOperatorName(): String?
}
```

Guidance:

- `Any?` for ID keeps compatibility with UUID/Long/String projects.
- implementations should return null when unauthenticated/background context.

### AutoConfiguration contract

In `engine-audit`, add:

- `AuditAutoConfiguration`
- `AuditProperties`
- lifecycle callback component(s)

Boot auto registration file:

- `engine-audit/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`

Properties prefix:

- `only.engine.audit`

Suggested defaults:

- enable: `true`
- createUserIdField: `createUserId`
- createByField: `createBy`
- createTimeField: `createTime`
- updateUserIdField: `updateUserId`
- updateByField: `updateBy`
- updateTimeField: `updateTime`
- epoch time unit default: seconds (to align current only-danmuku-zero data shape)

Runtime behavior:

- pre-persist:
  - set create* if null
  - set update* if null
- pre-update:
  - overwrite update*
- if field does not exist on entity: ignore silently
- if provider returns null: keep nullable audit fields as null

## `engine-satoken` Default Audit SPI Provider

Add default `AuditOperatorProvider` implementation in `engine-satoken`, guarded by conditional bean creation:

- class example: `SaTokenAuditOperatorProvider`
- `@ConditionalOnMissingBean(AuditOperatorProvider::class)`
- obtains id/name from current login context (existing `LoginHelper` / Sa-Token session data)

Result:

- projects using Sa-Token get audit SPI implementation automatically.
- non-Sa-Token projects can provide custom SPI bean.

## only-danmuku-zero Migration Contract

### Current baseline (to be replaced)

Current project uses:

- domain base classes (`AuditedEntity`, `AuditedFieldsEntity`)
- domain-side static bridge (`AuditSupport.register(...)`) from adapter config

### Target state

1. entity classes keep audit columns as regular fields (no audit base-class inheritance dependency).
2. runtime audit fill responsibility moves to only-engine `engine-audit`.
3. adapter-level manual bridge wiring (`JpaAuditingConfig` that registers domain `AuditSupport`) is removed.
4. special field management remains in cap4k declaration/resolution (`id`, `@Deleted`, `@Version`, managed audit columns).

### Compatibility expectations

- generated entities still include audit columns when schema contains them.
- user write surfaces (command/update payload shaping) exclude managed fields by resolved policy.
- runtime persistence still gets create/update audit values via engine module.

## Failure and Guardrails

Fail-fast cases required in this slice:

1. invalid special-field marker values (`@Version=...`, `@Deleted=...`, `@Managed=...`, `@Exposed=...`).
2. multiple deleted markers in one entity.
3. multiple version markers in one entity.
4. ID strategy incompatible with ID type.
5. `@GeneratedValue` on non-ID column.
6. `@Managed` + `@Exposed` on same column.
7. `@Exposed` applied to protected special fields (`id`, resolved deleted, resolved version).

Runtime non-failure expectations:

- configured managed default column missing on one entity: ignore for that entity.
- configured deleted/version default column missing on one entity: feature disabled for that entity.
- unauthenticated context with nullable audit fields: no exception required.

## Implementation Boundaries

This spec intentionally splits responsibilities:

- cap4k: declaration parsing, resolution, plan visibility, generation write-surface decisions
- only-engine: runtime audit value population via SPI + auto configuration
- only-danmuku-zero: consumer migration away from audited base inheritance

No module should take over another module's ownership boundary.

## Acceptance Criteria

This slice is complete when all are true:

1. existing executed cap4k special-field spec file remains unchanged.
2. cap4k supports managed defaults + DB column managed markers with explicit resolved write policy output.
3. cap4k `cap4kPlan` exposes resolved managed fields and create/update write-surface.
4. only-engine adds reusable `engine-audit` module with SPI and auto configuration.
5. engine-satoken provides default `AuditOperatorProvider` implementation.
6. only-danmuku-zero no longer depends on `AuditedEntity` / `AuditedFieldsEntity` inheritance for audit autofill.
7. only-danmuku-zero audit columns are still persisted correctly during create/update flows.

## Verification Matrix

### A) cap4k

- parser tests for `@Managed` / `@Exposed` and fail-fast.
- resolver tests for write policies (`CREATE_ONLY`, `READ_ONLY`, `SYSTEM_TRANSITION_ONLY`).
- plan JSON tests for resolved managed/write-surface output.
- compile-functional tests for generated entity/payload behavior.

### B) only-engine

- `engine-audit` unit tests for lifecycle fill behavior (insert/update, null provider, missing field).
- Spring Boot auto-configuration tests for bean registration conditions.
- SPI override tests (`@ConditionalOnMissingBean` behavior).
- engine-satoken integration test proving default audit provider wiring.

### C) only-danmuku-zero

- compile passes without audited base inheritance dependency.
- integration tests for create/update audit field persistence.
- smoke verification for soft-delete/version/ID semantics unchanged.

## Rollout Sequence

Recommended order:

1. cap4k managed write-surface model and plan exposure
2. only-engine `engine-audit` + SPI
3. engine-satoken default audit SPI provider
4. only-danmuku-zero migration and verification

This order minimizes migration risk and keeps each step observable.

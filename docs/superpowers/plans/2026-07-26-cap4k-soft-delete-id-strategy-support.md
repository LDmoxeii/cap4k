# cap4k Soft Delete Existing IdStrategy Support Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` (recommended in one session) or `superpowers:executing-plans` (for a separate execution session) to implement this plan task by task.

**Goal:** Extend aggregate soft delete from numeric identity IDs to the approved identity, Snowflake, and UUID7 storage matrix while keeping application-side IDs strongly typed, keeping `deleted` as raw physical storage, and proving the generated SQL against real H2 and PostgreSQL.

**Architecture:** Pipeline core owns one shared physical-storage catalog and emits a dialect-free semantic `AggregateSoftDeletePolicy`. The aggregate generator resolves a bounded SQL dialect, quotes exact physical identifiers, renders SQL/Kotlin sentinel values, and removes the system-transition `deleted` field from constructors. The Pebble template consumes final generator products. Functional compilation and real-database suites close the generator/runtime loop without changing UoW ID completion or owned-child lifecycle behavior.

**Tech Stack:** Kotlin 2.2.20, Gradle, JUnit 5, JDBC metadata, H2 2.3.232, PostgreSQL JDBC 42.7.2, Hibernate/JPA, Pebble templates, Gradle TestKit, kotlin-compile-testing, GitHub Actions.

## Reader Contract

This plan is self-contained for an implementation agent with no chat history. Before changing code, read:

- `AGENTS.md`
- `docs/superpowers/specs/2026-07-26-cap4k-soft-delete-id-strategy-support-design.md` in full
- every current source/test/template file named in the task being executed

Execute tasks in order. For every task, add the RED test first, run the exact focused command, observe the stated failure, make only the minimum production or fixture change, and rerun the same command to GREEN. Do not introduce compatibility code to keep later modules compiling between the intentional breaking API cut and their scheduled migration.

Every PowerShell native command in this plan must fail the step immediately when its exit code is non-zero. At the start of each PowerShell process, define and use these wrappers; raw native command lines shown later are shorthand for `Invoke-CheckedNative { & <command> }` and must not be pasted as an unchecked multi-command block:

```powershell
$ErrorActionPreference = "Stop"

function Invoke-CheckedNative {
    param([Parameter(Mandatory)][scriptblock]$Command)

    & $Command
    $exitCode = $LASTEXITCODE
    if ($exitCode -ne 0) {
        throw "Native command failed with exit code $exitCode: $Command"
    }
}

function Assert-NoNativeMatches {
    param(
        [Parameter(Mandatory)][scriptblock]$Command,
        [Parameter(Mandatory)][string]$FailureMessage
    )

    $output = & $Command
    $exitCode = $LASTEXITCODE
    if ($exitCode -eq 0) {
        $output | Write-Output
        throw $FailureMessage
    }
    if ($exitCode -ne 1) {
        throw "Search command failed with exit code $exitCode: $Command"
    }
}
```

Use `Assert-NoNativeMatches` only when ripgrep exit code `1` means the desired “no match” result. Do not enable a shell-wide native-command error preference around a negative `rg`, because it would turn the expected exit code `1` into an exception before the assertion can classify it.

## Global Constraints

- Work only in `C:/Users/LD_moxeii/Documents/code/only-workspace/cap4k/.worktrees/soft-delete-id-strategy-plan` on branch `plan/soft-delete-id-strategy-support` starting from HEAD `502ce172cf5925099c3332ce129039a5b02e5353`.
- Treat `docs/superpowers/specs/2026-07-26-cap4k-soft-delete-id-strategy-support-design.md` and this plan as intentionally untracked and read-only throughout implementation. Never stage or commit either file.
- Do not edit `docs/superpowers/specs/2026-07-24-cap4k-strong-id-create-time-injection-design.md` or `docs/superpowers/plans/2026-07-24-cap4k-strong-id-create-time-injection.md` (the completed Phase 4 design/plan), or any historical spec/plan.
- Do not modify UoW create-time ID injection, root enrollment, owned-child reconciliation, graph traversal, or owned-child factory/persist boundaries.
- Do not modify `ddd-distributed-snowflake`, `BuiltInIdentifierStrategies.SNOWFLAKE`, `SnowflakeIdentifierStrategy`, or reintroduce `ApplicationSideId`.
- Preserve strategy vocabulary exactly: `identity`, `uuid7`, and `snowflake`. Never add `snowflake-long`, an alias, or a compatibility spelling. A test/table/property name containing `snowflakeLong` or `snowflake_long` is only a physical/example name.
- Preserve both current Snowflake backings: `Long`/integral with `ZERO`, and `String`/character with `ZERO`.
- Aggregate factory-construction evidence applies only to Snowflake Long, Snowflake String, UUID7 String, and UUID7 native UUID. Identity integral is verified through entity construction, raw ZERO-initialized deleted state, write-surface exclusion, versioned SQL rendering/execution, and the real H2 lifecycle.
- The existing database-identity factory `constructorMappingResolved=false` / `TODO("Implement aggregate construction")` gap is outside this iteration. Do not modify `FactoryArtifactPlanner` to resolve it, and do not count factory-file existence, payload compilation, or compilation of the `TODO` body as construction evidence.
- For UUID7 and Snowflake, the generated entity `id` remains its entity-specific Strong ID value object. `deleted` remains `Byte`, `Short`, `Int`, `Long`, `String`, or `UUID` according to physical backing and must never be wrapped as that Strong ID.
- Keep SQL construction, identifier quoting, SQL literals, and Kotlin initializer rendering in `cap4k-plugin-pipeline-generator-aggregate`. Core may publish only storage/sentinel/tombstone semantics.
- Delete the old `AggregateSoftDeletePolicy` fields outright. Do not add deprecated properties, secondary/compatibility constructors, adapters, aliases, implicit conversions, or fallback maps.
- Delete the missing/unknown database `DOUBLE_QUOTE` fallback. An entity without soft delete remains dialect-independent; an entity with soft delete must fail for missing or unsupported JDBC URL.
- Do not invent a general SQL parser/evaluator. Default normalization is the finite grammar in the approved spec. An unrecognized expression is a validation error, never a request to evaluate it or a reason to use the raw text.
- Do not add an owned-child factory, public child persist API, generated create/update input, or new “owned-child spec” artifact. In the current generator, the relevant existing child-domain input carrier is the non-root entity constructor context; prove `deleted` is absent there and in root factory payload/mapping.
- No task may fix the two known master-baseline failures listed under “Known Baseline”. If a focused soft-delete test fails, diagnose it; if only those two full-check failures recur unchanged, report them as baseline evidence.
- Use `./gradlew` in CI/Linux and `./gradlew.bat` or `.\gradlew.bat` on Windows. Commands below use the Windows form for local execution and name the required final `./gradlew check` explicitly.

## Known Baseline

On unmodified master, `gradlew check` has already been observed to fail in these existing tests:

- `PipelinePluginBootstrapGeneratedProjectFunctionalTest generated bootstrap preview project domain application and adapter modules compile`
- `PipelinePluginBootstrapGeneratedProjectFunctionalTest generated bootstrap preview project remains usable with fixed template override and slots`

They are not caused by this feature. Do not edit `PipelinePluginBootstrapGeneratedProjectFunctionalTest`, bootstrap preview fixtures, bootstrap templates, or related CI logic to make them pass. At final verification, preserve the exact failure names/output and distinguish them from any new failure.

## Resolved Factory Evidence Decision

The acceptance scope is fixed and implementation may proceed. Real aggregate factory construction is required for exactly four application-side ID combinations: Snowflake Long, Snowflake String, UUID7 String, and UUID7 native UUID.

Identity integral remains fully in scope for generated entity construction without deleted, raw integral `ZERO` initialization, deleted exclusion from every user write surface, versioned SQLDelete/Where generation and execution, and the real H2 create/query/delete lifecycle. Its existing unresolved generated factory body is an independent capability gap and is neither changed nor counted as evidence in this plan.

---

### Task 0: Guard the worktree and capture a reproducible baseline

**Files:**

- Read: `AGENTS.md`
- Read: `docs/superpowers/specs/2026-07-26-cap4k-soft-delete-id-strategy-support-design.md`
- Read: `docs/superpowers/plans/2026-07-26-cap4k-soft-delete-id-strategy-support.md`
- Do not modify any file in this task.

**Step 1: Verify repository identity and dirtiness**

Run:

```powershell
$branch = & git rev-parse --abbrev-ref HEAD
if ($LASTEXITCODE -ne 0) { throw "Unable to resolve branch" }
$head = & git rev-parse HEAD
if ($LASTEXITCODE -ne 0) { throw "Unable to resolve HEAD" }
$status = @(& git status --short --untracked-files=all | Sort-Object)
if ($LASTEXITCODE -ne 0) { throw "Unable to inspect worktree status" }
$expectedStatus = @(
    "?? docs/superpowers/plans/2026-07-26-cap4k-soft-delete-id-strategy-support.md",
    "?? docs/superpowers/specs/2026-07-26-cap4k-soft-delete-id-strategy-support-design.md"
) | Sort-Object
if (Compare-Object $expectedStatus $status) {
    throw "Unexpected worktree state. Expected only the read-only plan and spec as untracked files."
}
$planBlobHashBefore = & git hash-object docs/superpowers/plans/2026-07-26-cap4k-soft-delete-id-strategy-support.md
if ($LASTEXITCODE -ne 0) { throw "Unable to hash implementation plan" }
$specBlobHashBefore = & git hash-object docs/superpowers/specs/2026-07-26-cap4k-soft-delete-id-strategy-support-design.md
if ($LASTEXITCODE -ne 0) { throw "Unable to hash design spec" }
if ($branch -ne "plan/soft-delete-id-strategy-support") { throw "Unexpected branch: $branch" }
if ($head -ne "502ce172cf5925099c3332ce129039a5b02e5353") { throw "Unexpected HEAD: $head" }
if ($specBlobHashBefore -ne "3a52a448b287b2965b6248bee625ccf9538f6c3e") {
    throw "Approved design spec changed before implementation: $specBlobHashBefore"
}
"BRANCH=$branch"
"HEAD=$head"
"PLAN_BLOB_HASH_BEFORE=$planBlobHashBefore"
"SPEC_BLOB_HASH_BEFORE=$specBlobHashBefore"
```

Expected:

- branch is `plan/soft-delete-id-strategy-support`;
- HEAD is `502ce172cf5925099c3332ce129039a5b02e5353`;
- the read-only plan and approved design spec are the only pre-existing untracked files;
- the design spec starting blob hash is `3a52a448b287b2965b6248bee625ccf9538f6c3e`;
- the plan and spec starting hashes are copied verbatim into the implementation session notes as `PLAN_BLOB_HASH_BEFORE` and `SPEC_BLOB_HASH_BEFORE` for Task 12 comparison. The plan hash is captured dynamically because a file cannot embed a stable hash of its own complete contents.

Stop if branch/HEAD differs, either read-only document is missing, the spec hash differs before implementation, either document is already staged/tracked, or unrelated changes are present.

**Step 2: Run focused baseline tests before edits**

Run:

```powershell
Invoke-CheckedNative {
    & .\gradlew.bat :cap4k-plugin-pipeline-api:test :cap4k-plugin-pipeline-source-db:test :cap4k-plugin-pipeline-core:test :cap4k-plugin-pipeline-generator-aggregate:test :cap4k-plugin-pipeline-renderer-pebble:test --console=plain
}
```

Expected: the focused module baseline is green. If it is not, record the exact pre-existing failure and do not repair it as part of this feature.

**Step 3: Record the protected scope in the implementation notes**

Before Task 1, copy the Global Constraints, Known Baseline, `PLAN_BLOB_HASH_BEFORE`, and `SPEC_BLOB_HASH_BEFORE` into the implementation session notes. This is a guard against document drift and against drifting into Phase 4/UoW/Snowflake infrastructure while debugging later tests.

**Step 4: Commit**

No commit; this task is read-only.

---

### Task 1: Add the public semantic vocabulary without changing the policy shape yet

**Files:**

- Modify: `cap4k-plugin-pipeline-api/src/test/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModelsTest.kt`
- Modify: `cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModels.kt`

**Target types:** `AggregateIdStorageKind`, `SoftDeleteActiveSentinel`

**Step 1: Write the RED API test**

Add a test that asserts the exact enum constants and order:

```kotlin
assertEquals(
    listOf("INTEGRAL", "CHARACTER", "NATIVE_UUID"),
    AggregateIdStorageKind.entries.map { it.name },
)
assertEquals(
    listOf("ZERO", "NIL_UUID"),
    SoftDeleteActiveSentinel.entries.map { it.name },
)
```

Do not change `AggregateSoftDeletePolicy` in this task.

**Step 2: Run the test to verify RED**

```powershell
.\gradlew.bat :cap4k-plugin-pipeline-api:test --tests "com.only4.cap4k.plugin.pipeline.api.PipelineModelsTest" --console=plain
```

Expected: Kotlin test compilation fails with unresolved references to the two enums.

**Step 3: Add the minimum API implementation**

In `PipelineModels.kt`, next to `SoftDeleteTombstoneStrategy`/`AggregateSoftDeletePolicy`, add exactly:

```kotlin
enum class AggregateIdStorageKind {
    INTEGRAL,
    CHARACTER,
    NATIVE_UUID,
}

enum class SoftDeleteActiveSentinel {
    ZERO,
    NIL_UUID,
}
```

Do not add `UNKNOWN`, dialect concepts, SQL strings, helper methods, or compatibility aliases.

**Step 4: Run the focused API test to verify GREEN**

Run the command from Step 2.

Expected: `PipelineModelsTest` passes.

**Step 5: Commit**

```powershell
git add cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModels.kt cap4k-plugin-pipeline-api/src/test/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModelsTest.kt
git commit -m "feat(pipeline): add soft delete storage semantics"
```

---

### Task 2: Introduce the shared AggregateIdStorageCatalog

**Files:**

- Create: `cap4k-plugin-pipeline-core/src/test/kotlin/com/only4/cap4k/plugin/pipeline/core/AggregateIdStorageCatalogTest.kt`
- Create: `cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/AggregateIdStorageCatalog.kt`

**Target types/functions:**

- `internal sealed interface ResolvedAggregateIdStorage`
- `ResolvedAggregateIdStorage.Integral(bits, unsigned, kotlinType)`
- `ResolvedAggregateIdStorage.Character(capacity, kotlinType)`
- `ResolvedAggregateIdStorage.NativeUuid(kotlinType)`
- `internal object AggregateIdStorageCatalog.resolve(tableName, column)`

**Step 1: Write the RED catalog matrix**

Create table-driven tests that call `AggregateIdStorageCatalog.resolve("sample", column)` and assert:

- `TINYINT`, `SMALLINT`, `MEDIUMINT`, `INT`/`INTEGER`, `BIGINT` classify as 8/16/24/32/64 bits;
- `INT(11) UNSIGNED` and other case/spacing variants retain `unsigned = true`;
- integral Kotlin family accepts only `Byte`/`kotlin.Byte`, `Short`/`kotlin.Short`, `Int`/`kotlin.Int`, and `Long`/`kotlin.Long`;
- JDBC `CHAR`, `VARCHAR`, `LONGVARCHAR`, `NCHAR`, `NVARCHAR`, and `LONGNVARCHAR` plus `String`/`kotlin.String` classify as `Character` and retain positive `columnSize` exactly;
- `jdbcType = Types.OTHER` or `Types.BINARY`, `dbType = uuid` case-insensitively, and `UUID`/`java.util.UUID` classify as `NativeUuid`;
- MySQL `BINARY(16)`, `DECIMAL`, `NUMERIC`, floating point, arbitrary `OTHER`, missing `jdbcType`, missing/non-positive character `columnSize`, and physical/Kotlin family contradictions fail with the `table.column` path and evidence in the message.

Use real `java.sql.Types` constants. Do not reuse or copy private sets from the current Strong ID resolver.

**Step 2: Run the test to verify RED**

```powershell
.\gradlew.bat :cap4k-plugin-pipeline-core:test --tests "com.only4.cap4k.plugin.pipeline.core.AggregateIdStorageCatalogTest" --console=plain
```

Expected: test compilation fails because the catalog and resolved storage types do not exist.

**Step 3: Implement the minimum catalog**

Implement one internal object, not an interface/implementation pair and not a service-loader extension. Use JDBC family as primary evidence, parse vendor `dbType` only for the finite integral names/width/`UNSIGNED` flag and exact `uuid`, and require Kotlin family agreement.

The integral parser must accept only:

```text
TINYINT, SMALLINT, MEDIUMINT, INT, INTEGER, BIGINT
optional display width
optional UNSIGNED
```

The catalog returns no `UNKNOWN`. Every missing/contradictory/unsupported case throws `IllegalArgumentException` with the physical path and the rejected `jdbcType`, `dbType`, `kotlinType`, and `columnSize` evidence.

**Step 4: Run the catalog test to verify GREEN**

Run the command from Step 2.

Expected: all catalog tests pass.

**Step 5: Run the complete core suite**

```powershell
.\gradlew.bat :cap4k-plugin-pipeline-core:test --console=plain
```

Expected: the existing core suite remains green; no consumer has been migrated yet.

**Step 6: Commit**

```powershell
git add cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/AggregateIdStorageCatalog.kt cap4k-plugin-pipeline-core/src/test/kotlin/com/only4/cap4k/plugin/pipeline/core/AggregateIdStorageCatalogTest.kt
git commit -m "refactor(pipeline): centralize aggregate id storage evidence"
```

---

### Task 3: Migrate Strong ID backing resolution to the shared catalog

**Files:**

- Modify: `cap4k-plugin-pipeline-core/src/test/kotlin/com/only4/cap4k/plugin/pipeline/core/AggregateStrongIdBackingResolverTest.kt`
- Modify: `cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/AggregateStrongIdBackingResolver.kt`

**Target function:** `AggregateStrongIdBackingResolver.resolve(tableName, idColumn, strategy)`

**Step 1: Extend the RED regression matrix**

Keep the current UUID7 `String`/native `UUID` and Snowflake `String`/`Long` success tests. Add assertions that:

- Snowflake `Long` succeeds only for catalog `Integral(bits = 64, unsigned = false)` with `Long`/`kotlin.Long`;
- Snowflake rejects unsigned `BIGINT`, non-64-bit integral storage, and `UUID` backing;
- UUID7 character still requires capacity at least 36;
- Snowflake character still requires capacity at least 19;
- UUID7 rejects integral; Snowflake rejects native UUID;
- errors still name the strategy and `table.column`.

Make one new test expect a shared-catalog diagnostic (for example, missing `jdbcType`) that the current local heuristics cannot produce.

**Step 2: Run the test to verify RED**

```powershell
.\gradlew.bat :cap4k-plugin-pipeline-core:test --tests "com.only4.cap4k.plugin.pipeline.core.AggregateStrongIdBackingResolverTest" --console=plain
```

Expected: the new shared-evidence/unsigned test fails against the current resolver.

**Step 3: Replace local classification with the catalog**

Call `AggregateIdStorageCatalog.resolve` once and keep only strategy-specific selection in `AggregateStrongIdBackingResolver`:

- UUID7: `Character(capacity >= 36)` -> `String`; `NativeUuid` -> `UUID`;
- Snowflake: `Character(capacity >= 19)` -> `String`; signed 64-bit `Integral` with `Long` backing -> `Long`.

In the same edit, delete the resolver's private character JDBC type set and native UUID classifier. Do not leave copied/parallel classifications “temporarily”; this is the required migration point that prevents Strong ID and soft delete from owning duplicate storage taxonomies.

Do not touch `GeneratedOwnIdPlanning`, `BuiltInIdentifierStrategies.SNOWFLAKE`, `SnowflakeIdentifierStrategy`, or any UoW code.

**Step 4: Run focused and complete core verification**

```powershell
Invoke-CheckedNative {
    & .\gradlew.bat :cap4k-plugin-pipeline-core:test --tests "com.only4.cap4k.plugin.pipeline.core.AggregateStrongIdBackingResolverTest" --console=plain
}
Invoke-CheckedNative {
    & .\gradlew.bat :cap4k-plugin-pipeline-core:test --console=plain
}
Assert-NoNativeMatches -Command {
    & rg -n "characterJdbcTypes|isNativeUuid" cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/AggregateStrongIdBackingResolver.kt
} -FailureMessage "AggregateStrongIdBackingResolver still owns duplicate character/native UUID classification"
Invoke-CheckedNative {
    & rg -n "NUMERIC_DB_TYPE_REGEX" cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/AggregateSoftDeletePolicyResolver.kt
}
```

Expected:

- focused and full core tests pass;
- `AggregateStrongIdBackingResolver.kt` no longer contains a character/native storage catalog;
- `NUMERIC_DB_TYPE_REGEX` may still exist only in the not-yet-migrated soft-delete resolver until Task 5.

**Step 5: Commit**

```powershell
git add cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/AggregateStrongIdBackingResolver.kt cap4k-plugin-pipeline-core/src/test/kotlin/com/only4/cap4k/plugin/pipeline/core/AggregateStrongIdBackingResolverTest.kt
git commit -m "refactor(pipeline): resolve strong id backing from shared catalog"
```

---

### Task 4: Implement finite default-sentinel normalization

**Files:**

- Create: `cap4k-plugin-pipeline-core/src/test/kotlin/com/only4/cap4k/plugin/pipeline/core/SoftDeleteDefaultNormalizerTest.kt`
- Create: `cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/SoftDeleteDefaultNormalizer.kt`

**Target types/functions:**

```kotlin
internal object SoftDeleteDefaultNormalizer {
    fun normalize(
        rawDefaultValue: String,
        storageKind: AggregateIdStorageKind,
    ): SoftDeleteActiveSentinel?
}
```

Return the existing public semantic enum directly: `ZERO`, `NIL_UUID`, or `null`. `null` means “not one of the two supported semantic sentinels”; callers must reject it. It is not permission to use the raw value or try another parser. Do not create an internal enum that duplicates `SoftDeleteActiveSentinel`.

**Step 1: Write the RED bounded grammar tests**

Table-drive exact successes:

- H2 2.3.232 forms: `0`, `'00000000-0000-0000-0000-000000000000'` for character, and the same quoted value for native UUID;
- whitespace and repeatedly balanced outer parentheses, including nested parentheses around a supported literal;
- one SQL single-quoted literal, plus a doubled-quote case proving embedded quote content is parsed and rejected rather than truncated;
- `UUID '00000000-0000-0000-0000-000000000000'`;
- `CAST(... AS UUID)`, integral casts such as `CAST(0 AS BIGINT)`, and character casts such as `CAST('0' AS CHARACTER VARYING)` only when target family agrees;
- PostgreSQL postfix casts `0::bigint`, `'0'::character varying`, and `'00000000-0000-0000-0000-000000000000'::uuid` only when target family agrees.

Table-drive exact rejections:

- `NULL`, `''`, `1`, `gen_random_uuid()`, `uuid_nil()`, `current_timestamp`;
- unbalanced parentheses/quotes;
- multiple or nested arbitrary casts outside the finite grammar;
- a cast target from a different storage family;
- unsupported vendor type names;
- any expression with operators, concatenation, function calls, or trailing text.

**Step 2: Run the test to verify RED**

```powershell
.\gradlew.bat :cap4k-plugin-pipeline-core:test --tests "com.only4.cap4k.plugin.pipeline.core.SoftDeleteDefaultNormalizerTest" --console=plain
```

Expected: test compilation fails because the normalizer does not exist.

**Step 3: Implement only the approved grammar**

Implement small deterministic scanners/helpers that understand balanced parentheses and SQL single quotes. Apply transformations in this order:

1. trim;
2. repeatedly strip only a balanced outer pair that encloses the whole expression;
3. unwrap at most one PostgreSQL postfix cast found outside quotes/parentheses;
4. unwrap at most one standard `CAST(<value> AS <target>)` or one `UUID <literal>` wrapper;
5. unwrap one SQL single-quoted literal and replace doubled single quotes;
6. return `ZERO` only for exact `0`, or `NIL_UUID` only for the canonical 8-4-4-4-12 nil UUID text (hex comparison may be case-insensitive, but non-canonical UUID spellings are rejected).

Keep explicit target-family allowlists bounded to the catalog vocabulary and approved examples: integral `TINYINT`/`SMALLINT`/`MEDIUMINT`/`INT`/`INTEGER`/`BIGINT`; character `CHAR`/`CHARACTER`/`CHARACTER VARYING`/`VARCHAR`/`LONGVARCHAR`/`NCHAR`/`NVARCHAR`/`LONGNVARCHAR`; native UUID `UUID`. Do not add database connections, expression evaluation, generic tokenization, exception-catching fallback, double-quoted string literals, or “best effort” raw comparisons.

**Step 4: Run focused and full core tests**

```powershell
Invoke-CheckedNative {
    & .\gradlew.bat :cap4k-plugin-pipeline-core:test --tests "com.only4.cap4k.plugin.pipeline.core.SoftDeleteDefaultNormalizerTest" --console=plain
}
Invoke-CheckedNative {
    & .\gradlew.bat :cap4k-plugin-pipeline-core:test --console=plain
}
```

Expected: all tests pass.

**Step 5: Commit**

```powershell
git add cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/SoftDeleteDefaultNormalizer.kt cap4k-plugin-pipeline-core/src/test/kotlin/com/only4/cap4k/plugin/pipeline/core/SoftDeleteDefaultNormalizerTest.kt
git commit -m "feat(pipeline): normalize soft delete sentinels explicitly"
```

---

### Task 5: Make AggregateSoftDeletePolicy semantic and migrate the core resolver atomically

This is the deliberate breaking API cut. API and core change together so their focused suites are green; generator/renderer consumers are migrated in Tasks 6–7. Do not add a temporary old-policy constructor or property to keep those later modules compiling.

**Files:**

- Modify: `cap4k-plugin-pipeline-api/src/test/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModelsTest.kt`
- Modify: `cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModels.kt`
- Modify: `cap4k-plugin-pipeline-core/src/test/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssemblerTest.kt`
- Modify: `cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/AggregateSoftDeletePolicyResolver.kt`
- Verify only: `cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/AggregatePersistenceProviderInference.kt`
- Verify only: `cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/AggregateSpecialFieldPolicyResolver.kt`

**Target types/functions:**

- `AggregateSoftDeletePolicy`
- `AggregateSoftDeletePolicyResolver.resolve`
- integral assignability helper local to the resolver or `ResolvedAggregateIdStorage.Integral`

**Step 1: Write the RED API shape test**

Replace the current old-field policy assertion with construction/assertions for exactly:

```kotlin
AggregateSoftDeletePolicy(
    fieldName = "deleted",
    columnName = "deleted",
    storageKind = AggregateIdStorageKind.CHARACTER,
    activeSentinel = SoftDeleteActiveSentinel.NIL_UUID,
    tombstoneStrategy = SoftDeleteTombstoneStrategy.SELF_ID,
)
```

Also assert via `AggregateSoftDeletePolicy::class.java.declaredFields.map { it.name }` that the domain fields contain `fieldName`, `columnName`, `storageKind`, `activeSentinel`, `tombstoneStrategy` and do not contain `activeValue`, `activePredicateSql`, or `deleteAssignmentSql`.

**Step 2: Write the RED core success/rejection matrix**

In `DefaultCanonicalAssemblerTest`, replace old SQL-field assertions and add separate named tests for:

Success:

1. identity/`DB_IDENTITY`, integral ID/deleted, `ZERO`;
2. Snowflake `Long`, signed `BIGINT` ID/deleted, `ZERO`;
3. Snowflake `String`, capacity-compatible character ID/deleted, `ZERO`;
4. UUID7 `String`, capacity-compatible character ID/deleted, `NIL_UUID`;
5. UUID7 native `UUID`, native UUID ID/deleted, `NIL_UUID`.

For cases 2–5, assert the canonical entity's ID is still represented by its `StrongIdModel` backing (`Long`, `String`, `String`, `UUID`) while the soft-delete policy publishes only the raw storage kind/sentinel.

Rejection:

- deleted is nullable;
- default is missing;
- default is `1`, `uuid_nil()`, `gen_random_uuid()`, or another wrong/unsupported expression;
- cross-storage ID/deleted pairs in every direction;
- character deleted capacity smaller than ID capacity;
- integral deleted range too small;
- signed ID into same-width unsigned deleted;
- unsigned ID into same-width signed deleted;
- unsupported ID storage or unsupported deleted storage;
- Kotlin/JDBC storage contradiction.

Use exact actual-driver default strings in success data: H2 `0`, H2 quoted nil UUID, PostgreSQL `0::bigint`, PostgreSQL quoted text casts, PostgreSQL `'00000000-0000-0000-0000-000000000000'::uuid`, standard `CAST`, typed `UUID` literal, and nested balanced parentheses.

**Step 3: Run the tests to verify RED**

```powershell
.\gradlew.bat :cap4k-plugin-pipeline-api:test --tests "com.only4.cap4k.plugin.pipeline.api.PipelineModelsTest" --console=plain
.\gradlew.bat :cap4k-plugin-pipeline-core:test --tests "com.only4.cap4k.plugin.pipeline.core.DefaultCanonicalAssemblerTest" --console=plain
```

Expected:

- the API test fails because the new policy constructor does not exist and old fields still do;
- the core tests fail because the resolver is numeric-only and publishes SQL strings.

**Step 4: Replace the API model with no compatibility surface**

Change `AggregateSoftDeletePolicy` to exactly:

```kotlin
data class AggregateSoftDeletePolicy(
    val fieldName: String,
    val columnName: String,
    val storageKind: AggregateIdStorageKind,
    val activeSentinel: SoftDeleteActiveSentinel,
    val tombstoneStrategy: SoftDeleteTombstoneStrategy,
)
```

Delete `activeValue`, `activePredicateSql`, and `deleteAssignmentSql`. Do not add deprecated replacements, default values that preserve old call sites, secondary constructors, aliases, extension properties, or adapters.

**Step 5: Rewrite AggregateSoftDeletePolicyResolver around the shared catalog**

Implement the approved order:

1. return `null` if the resolved deleted marker is disabled;
2. locate exact physical ID/deleted columns;
3. resolve both with `AggregateIdStorageCatalog`;
4. read `resolvedPolicy.id.strategy` and accept only `identity`, `uuid7`, `snowflake`;
5. select `ZERO` for identity/Snowflake or `NIL_UUID` for UUID7;
6. validate strategy/storage support;
7. validate same-kind SELF_ID assignment and capacity/range;
8. validate deleted is non-null;
9. call `SoftDeleteDefaultNormalizer.normalize` and require the expected sentinel;
10. publish the five-field semantic policy.

Apply the exact integral rule from the spec:

```kotlin
when {
    source.unsigned == target.unsigned -> target.bits >= source.bits
    !source.unsigned && target.unsigned -> false
    else -> target.bits > source.bits
}
```

Strategy constraints remain separate from catalog classification:

- identity follows the existing identity strategy support and requires integral SELF_ID storage;
- Snowflake `Long` requires signed 64-bit integral ID and compatible integral deleted;
- Snowflake `String` requires character/character with deleted capacity at least ID capacity;
- UUID7 `String` requires character/character with deleted capacity at least ID capacity;
- UUID7 `UUID` requires native/native.

Error messages must name the `table.column`, both resolved storage descriptors, strategy, and rejected evidence. Never silently disable soft delete or change strategy/sentinel/storage.

In the same edit, delete `ActiveValue`, `numericCapacity`, `NumericCapacity`, `isDefaultZero`, and `NUMERIC_DB_TYPE_REGEX`. Soft delete must not retain a second physical classifier after this task.

**Step 6: Verify write-surface behavior without changing it**

Read `AggregateSpecialFieldPolicyResolver` and retain the existing `SYSTEM_TRANSITION_ONLY` classification and exclusion from `createAllowedFields`/`updateAllowedFields`. Read `AggregatePersistenceProviderInference` and leave its control flow unchanged except for compiling against the new policy type. Do not “solve” constructor leakage in core; it belongs to Task 6 generator planning.

**Step 7: Run focused API/core verification**

```powershell
Invoke-CheckedNative {
    & .\gradlew.bat :cap4k-plugin-pipeline-api:test --tests "com.only4.cap4k.plugin.pipeline.api.PipelineModelsTest" --console=plain
}
Invoke-CheckedNative {
    & .\gradlew.bat :cap4k-plugin-pipeline-core:test --tests "com.only4.cap4k.plugin.pipeline.core.SoftDeleteDefaultNormalizerTest" --tests "com.only4.cap4k.plugin.pipeline.core.AggregateIdStorageCatalogTest" --tests "com.only4.cap4k.plugin.pipeline.core.AggregateStrongIdBackingResolverTest" --tests "com.only4.cap4k.plugin.pipeline.core.DefaultCanonicalAssemblerTest" --console=plain
}
Invoke-CheckedNative {
    & .\gradlew.bat :cap4k-plugin-pipeline-api:test :cap4k-plugin-pipeline-core:test --console=plain
}
Assert-NoNativeMatches -Command {
    & rg -n "activeValue|activePredicateSql|deleteAssignmentSql|NUMERIC_DB_TYPE_REGEX|numericCapacity" cap4k-plugin-pipeline-api/src/main cap4k-plugin-pipeline-core/src/main
} -FailureMessage "Removed policy fields or duplicate numeric classification remain in API/core production code"
```

Expected:

- API and core suites pass;
- the final `rg` returns no production match;
- generator/renderer may not compile until Tasks 6–7 because the old API has intentionally been removed. Proceed directly; do not add a bridge.

**Step 8: Commit**

```powershell
git add cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModels.kt cap4k-plugin-pipeline-api/src/test/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModelsTest.kt cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/AggregateSoftDeletePolicyResolver.kt cap4k-plugin-pipeline-core/src/test/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssemblerTest.kt
git commit -m "feat(pipeline): resolve semantic soft delete policy by id strategy"
```

---

### Task 6: Move dialect and SQL/Kotlin rendering into the aggregate generator

**Files:**

- Create: `cap4k-plugin-pipeline-generator-aggregate/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateSoftDeleteRenderingTest.kt`
- Create: `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateSqlDialect.kt`
- Create: `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateSoftDeleteRendering.kt`
- Modify: `cap4k-plugin-pipeline-generator-aggregate/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateArtifactPlannerTest.kt`
- Modify: `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/EntityArtifactPlanner.kt`
- Verify only: `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/FactoryArtifactPlanner.kt`

**Target types/functions:**

```kotlin
internal enum class AggregateSqlDialect { MYSQL, MARIADB, H2, H2_MYSQL, POSTGRESQL }

internal object AggregateSqlDialectResolver {
    fun resolve(jdbcUrl: String): AggregateSqlDialect
}

internal data class RenderedAggregateSoftDelete(
    val activeSqlLiteral: String,
    val propertyInitializer: String,
    val whereClause: String,
    val sqlDelete: String,
)

internal object AggregateSoftDeleteRendering {
    fun render(
        policy: AggregateSoftDeletePolicy,
        dialect: AggregateSqlDialect,
        tableName: String,
        idColumnName: String,
        versionColumnName: String?,
        deletedKotlinType: String,
    ): RenderedAggregateSoftDelete
}
```

Private signatures may be smaller, but keep one generator-owned dialect model and one generator-owned semantic-to-rendered conversion. Do not move either into API/core.

**Step 1: Write RED dialect and rendering tests**

In the new test file, cover:

- URL resolution case-insensitively for `jdbc:mysql:`, `jdbc:mariadb:`, standard `jdbc:h2:`, `jdbc:h2:...;MODE=MySQL;...`, and `jdbc:postgresql:`;
- missing/blank and `jdbc:oracle:thin:` rejection with the supported list;
- MySQL/MariaDB/H2-MySQL backticks, standard H2/PostgreSQL double quotes;
- embedded quote escaping: ``a`b`` -> `` `a``b` `` and `a"b` -> `"a""b"`;
- active SQL literals: integral ZERO `0`, character ZERO `'0'`, character NIL UUID quoted text, native UUID NIL UUID explicit `CAST(... AS UUID)`;
- native UUID allowed only for H2, H2-MySQL, PostgreSQL;
- Kotlin initializers: `Byte`/`Short`/`Int` -> `0`, `Long` -> `0L`, String ZERO -> `"0"`, String NIL -> canonical nil string, UUID NIL -> `UUID(0L, 0L)`;
- unsupported storage/sentinel/Kotlin combinations fail rather than cast/convert;
- versionless and versioned SQLDelete use ID then version placeholder order.

**Step 2: Write RED planner migration tests**

Update soft-delete cases in `AggregateArtifactPlannerTest` to construct the new semantic policy and assert:

- `softDelete` context contains only `enabled`, `columnName`, `storageKind`, `activeSentinel`, `tombstoneStrategy`;
- final `softDeleteWhereClause`, `softDeleteSql`, and their Kotlin string literals remain separate generator products;
- MySQL, MariaDB, H2, H2 MySQL mode, and PostgreSQL produce exact physical quoting;
- missing/unknown URL fails only when an entity has a soft-delete policy;
- a model without soft delete plans successfully with missing/unknown DB URL;
- exact physical table/ID/deleted/version case is preserved;
- `deleted` stays in `scalarFields` with raw `Long`, `String`, or `UUID`, never the own Strong ID type;
- `deleted` is absent from `constructorFields` for both aggregate roots and non-root owned entities;
- normal Strong ID ID fields remain strongly typed and generated-own-ID behavior remains unchanged;
- root factory `payloadFields`, `constructorPayloadFields`, and `constructorUnresolvedFields` exclude `deleted` when factory generation is enabled.

For “owned-child specs”, use the current non-root entity's `constructorFields` as the existing child construction contract. Do not add a new specification/factory artifact for children.

**Step 3: Run generator tests to verify RED**

```powershell
.\gradlew.bat :cap4k-plugin-pipeline-generator-aggregate:test --tests "com.only4.cap4k.plugin.pipeline.generator.aggregate.AggregateSoftDeleteRenderingTest" --tests "com.only4.cap4k.plugin.pipeline.generator.aggregate.AggregateArtifactPlannerTest" --console=plain
```

Expected: compilation/tests fail because old policy fields are referenced, the dialect/rendering types do not exist, constructor fields still include `deleted`, and missing/unknown URLs still fall back to double quotes.

**Step 4: Implement exact dialect resolution**

Resolve URL case-insensitively. For H2, detect `MODE=MySQL` as a semicolon-delimited URL setting, not an arbitrary substring. Throw for blank/missing or unsupported URL only when the caller actually has a soft-delete policy.

Delete `IdentifierQuoteStyle`, `resolveIdentifierQuoteStyle`, and the default `DOUBLE_QUOTE` branch from `EntityArtifactPlanner`. There is no fallback enum value.

**Step 5: Implement generator-owned rendering**

In `AggregateSoftDeleteRendering`, quote the exact physical names and render the approved matrix. Build raw `whereClause` and `sqlDelete`; `EntityArtifactPlanner` then passes each through the existing Kotlin string-literal renderer exactly once.

Do not accept native UUID for MySQL/MariaDB. Do not use an implicit string literal for native UUID. Do not build SQL from strings stored in core policy.

**Step 6: Migrate EntityArtifactPlanner and constructor eligibility**

In `EntityArtifactPlanner`:

1. find semantic policy and physical ID/deleted/version fields;
2. resolve dialect lazily only when policy exists;
3. call the generator rendering helper;
4. publish the new five-key semantic context and separate final SQL products;
5. add a scalar-field context key `propertyInitializer`;
6. use semantic rendering for the resolved deleted field's initializer, not `AggregateEntityDefaultProjector` and not raw DB default text;
7. for ordinary scalar fields set `propertyInitializer` to the constructor parameter name/current projected expression;
8. remove every resolved `SYSTEM_TRANSITION_ONLY` field from `constructorFields`;
9. if any future `SYSTEM_TRANSITION_ONLY` field has no defined initializer, fail planning with its path instead of inventing one.

Keep `deleted` in `scalarFields` and keep its existing JPA insertable/updatable mapping. Do not change factory production code unless a RED assertion proves the current write-surface mapping still leaks `deleted`; if such a failure occurs, make the smallest mapping fix and do not add a child factory/public persist API.

**Step 7: Run focused and full generator verification**

```powershell
Invoke-CheckedNative {
    & .\gradlew.bat :cap4k-plugin-pipeline-generator-aggregate:test --tests "com.only4.cap4k.plugin.pipeline.generator.aggregate.AggregateSoftDeleteRenderingTest" --tests "com.only4.cap4k.plugin.pipeline.generator.aggregate.AggregateArtifactPlannerTest" --console=plain
}
Invoke-CheckedNative {
    & .\gradlew.bat :cap4k-plugin-pipeline-generator-aggregate:test --console=plain
}
Assert-NoNativeMatches -Command {
    & rg -n "IdentifierQuoteStyle|DOUBLE_QUOTE|activeValue|activePredicateSql|deleteAssignmentSql" cap4k-plugin-pipeline-generator-aggregate/src/main
} -FailureMessage "Removed policy fields or identifier fallback remain in aggregate generator production code"
```

Expected:

- all generator tests pass;
- the final `rg` returns no production match;
- Snowflake String/Long and UUID7 String/native Strong ID artifact tests still pass.

**Step 8: Commit**

```powershell
git add cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateSqlDialect.kt cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateSoftDeleteRendering.kt cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/EntityArtifactPlanner.kt cap4k-plugin-pipeline-generator-aggregate/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateSoftDeleteRenderingTest.kt cap4k-plugin-pipeline-generator-aggregate/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateArtifactPlannerTest.kt
git commit -m "feat(pipeline): render soft delete by aggregate sql dialect"
```

---

### Task 7: Migrate the default entity template and compile its new constructor/property contract

**Files:**

- Modify: `cap4k-plugin-pipeline-renderer-pebble/src/test/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PebbleArtifactRendererTest.kt`
- Modify: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/entity.kt.peb`

**Target template context:** `constructorFields`, `scalarFields[*].propertyInitializer`, new semantic `softDelete`, final SQL string literals.

**Step 1: Write RED renderer assertions**

Replace old `activeValue`/SQL-in-policy context with the new semantic context and generator products. Add rendered-source/compile tests for:

- identity `Long` deleted property: `var deleted: Long = 0L`, absent from constructor;
- Snowflake String: Strong ID `id`, raw `String` deleted initialized to `"0"`;
- UUID7 String: Strong ID `id`, raw `String` deleted initialized to nil UUID text;
- UUID7 native: Strong ID `id`, raw `UUID` deleted initialized to `UUID(0L, 0L)` with `java.util.UUID` imported exactly once;
- `@SQLDelete` and `@Where` consume the final generator Kotlin string literals;
- each application-side-ID factory renders a real entity-construction body, contains no `TODO("Implement aggregate construction")`, and compiles without a deleted argument;
- no rendered constructor, payload, or property type refers to the deleted field's own Strong ID type.

Use kotlin-compile-testing already configured in this module. Do not add a compatibility template branch for the old context.

**Step 2: Run the renderer test to verify RED**

```powershell
.\gradlew.bat :cap4k-plugin-pipeline-renderer-pebble:test --tests "com.only4.cap4k.plugin.pipeline.renderer.pebble.PebbleArtifactRendererTest" --console=plain
```

Expected: rendered Kotlin compilation fails because `deleted` has been removed from `constructorFields` but the template still emits `var deleted = deleted`. Existing renderer assertions that construct the old policy/context also fail until replaced; the template itself must not gain a compatibility branch.

**Step 3: Make the minimum template change**

Keep generated-own-ID properties as `lateinit`. For every other scalar property, render:

```pebble
= {{ field.propertyInitializer | raw }}
```

instead of `= {{ field.name }}`. The planner already supplies constructor parameter names for ordinary fields and semantic active sentinels for `deleted`.

Do not put storage/sentinel branching or SQL rendering into Pebble. Do not add a hidden compatibility constructor or a template fallback when `propertyInitializer` is absent; planner validation must guarantee it.

**Step 4: Run focused and cross-module verification**

```powershell
Invoke-CheckedNative {
    & .\gradlew.bat :cap4k-plugin-pipeline-renderer-pebble:test --tests "com.only4.cap4k.plugin.pipeline.renderer.pebble.PebbleArtifactRendererTest" --console=plain
}
Invoke-CheckedNative {
    & .\gradlew.bat :cap4k-plugin-pipeline-generator-aggregate:test :cap4k-plugin-pipeline-renderer-pebble:test --console=plain
}
Assert-NoNativeMatches -Command {
    & rg -n "activeValue|activePredicateSql|deleteAssignmentSql" cap4k-plugin-pipeline-renderer-pebble/src/main
} -FailureMessage "Removed policy fields remain in the current Pebble renderer/template production surface"
```

Expected: renderer and generator suites pass and the final negative scan proves the current renderer/template production surface contains none of the removed API fields. Tests may name them only in explicit negative assertions.

**Step 5: Commit**

```powershell
git add cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/entity.kt.peb cap4k-plugin-pipeline-renderer-pebble/src/test/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PebbleArtifactRendererTest.kt
git commit -m "feat(pipeline): initialize soft delete outside entity constructors"
```

---

### Task 8: Lock actual H2 metadata forms into source tests

**Files:**

- Modify: `cap4k-plugin-pipeline-source-db/src/test/kotlin/com/only4/cap4k/plugin/pipeline/source/db/DbSchemaSourceProviderTest.kt`
- Create: `cap4k-plugin-pipeline-source-db/src/test/resources/soft-delete-h2-standard.sql`
- Create: `cap4k-plugin-pipeline-source-db/src/test/resources/soft-delete-h2-mysql.sql`
- Verify only: `cap4k-plugin-pipeline-source-db/src/main/kotlin/com/only4/cap4k/plugin/pipeline/source/db/DbSchemaSourceProvider.kt`
- Verify only: `cap4k-plugin-pipeline-source-db/src/main/kotlin/com/only4/cap4k/plugin/pipeline/source/db/JdbcTypeMapper.kt`

**Target evidence:** real H2 2.3.232 `COLUMN_DEF`, JDBC family, Kotlin type, capacity, and exact physical-name propagation.

**Step 1: Write the RED metadata-to-contract test**

Add one H2 standard-mode test and one H2 MySQL-mode test whose JDBC `INIT=RUNSCRIPT` URLs refer to the two not-yet-created resource files. The scripts will create real tables containing:

- `BIGINT DEFAULT 0`;
- `VARCHAR(36) DEFAULT '00000000-0000-0000-0000-000000000000'`;
- `UUID DEFAULT '00000000-0000-0000-0000-000000000000'`;
- one unquoted table/columns whose metadata normalizes to uppercase;
- one quoted mixed-case table/columns.

Assert exact `DbColumnSnapshot` evidence: `name`, `dbType`, `jdbcType`, `kotlinType`, `columnSize`, `nullable`, `defaultValue`. Include the actual defaults in the failure message so a future H2 upgrade reveals changed metadata wrappers.

**Step 2: Run the focused source test**

```powershell
.\gradlew.bat :cap4k-plugin-pipeline-source-db:test --tests "com.only4.cap4k.plugin.pipeline.source.db.DbSchemaSourceProviderTest" --console=plain
```

Expected RED: both tests fail because the referenced SQL resources do not exist yet. This is the test-first boundary for the metadata fixture; do not add production code before observing it.

**Step 3: Add the minimum real-H2 metadata fixtures**

Create the two SQL resources with only the tables/defaults/case variants listed in Step 1. Rerun the focused test. If it now passes, make no production source edit.

If and only if the test proves that current code drops an existing JDBC value required by the spec, make the smallest fix in `DbSchemaSourceProvider` or `JdbcTypeMapper`. Preserve the existing native UUID rule: JDBC `OTHER` or `BINARY` is UUID only when vendor type name is exactly `uuid`; do not infer MySQL `BINARY(16)`.

Do not normalize SQL defaults in the source provider. It must continue to publish raw driver evidence; semantic normalization belongs to core.

**Step 4: Run source and core tests**

```powershell
Invoke-CheckedNative {
    & .\gradlew.bat :cap4k-plugin-pipeline-source-db:test --tests "com.only4.cap4k.plugin.pipeline.source.db.DbSchemaSourceProviderTest" --console=plain
}
Invoke-CheckedNative {
    & .\gradlew.bat :cap4k-plugin-pipeline-source-db:test :cap4k-plugin-pipeline-core:test --console=plain
}
```

Expected: all pass.

**Step 5: Commit**

Commit the test and only a production source fix actually proven necessary:

```powershell
git add cap4k-plugin-pipeline-source-db/src/test/kotlin/com/only4/cap4k/plugin/pipeline/source/db/DbSchemaSourceProviderTest.kt cap4k-plugin-pipeline-source-db/src/test/resources/soft-delete-h2-standard.sql cap4k-plugin-pipeline-source-db/src/test/resources/soft-delete-h2-mysql.sql
git add cap4k-plugin-pipeline-source-db/src/main/kotlin/com/only4/cap4k/plugin/pipeline/source/db/DbSchemaSourceProvider.kt cap4k-plugin-pipeline-source-db/src/main/kotlin/com/only4/cap4k/plugin/pipeline/source/db/JdbcTypeMapper.kt
git commit -m "test(pipeline): lock h2 soft delete metadata evidence"
```

Before committing, unstage unchanged production files. Never create a no-op production diff merely because they are listed above.

---

### Task 9: Expand functional generation and compilation to the five supported combinations

**Fixed acceptance scope:** real factory-construction evidence is required for the four application-side ID combinations only. Identity factory construction is explicitly outside this iteration.

Identity integral acceptance is exactly:

- the generated entity can be constructed without passing `deleted`;
- `deleted` uses the raw integral Kotlin type and a `ZERO` initializer;
- `deleted` is absent from the entity constructor and every user write surface;
- versioned `SQLDelete` and `Where` are generated correctly; and
- Task 10 proves the real H2 create/query/delete lifecycle, including active sentinel, query filtering, physical-row retention, and `deleted = id`.

Do not modify `FactoryArtifactPlanner` to make database-identity constructor mapping resolve. Its current `constructorMappingResolved = false` and generated `TODO("Implement aggregate construction")` body are an independent capability gap. Factory-file existence, payload compilation, or compilation of that `TODO` body is not factory-construction evidence.

**Files:**

- Modify: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginFunctionalTest.kt`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginCompileFunctionalTest.kt`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-provider-persistence-sample/build.gradle.kts`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-provider-persistence-sample/schema.sql`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-provider-persistence-compile-sample/build.gradle.kts`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-provider-persistence-compile-sample/schema.sql`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-provider-persistence-compile-sample/demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/AggregateProviderPersistenceCompileSmoke.kt`

**Target fixtures/tests:**

- `aggregate-provider-persistence-sample`
- `aggregate-provider-persistence-compile-sample`
- `PipelinePluginFunctionalTest.aggregate provider specific persistence generation renders bounded controls`
- `PipelinePluginCompileFunctionalTest.aggregate provider specific persistence generation participates in domain compileKotlin`

Keep the two current test method names so the exact focused commands below remain valid.

**Step 1: Write RED functional assertions before changing fixtures**

Update both tests to require generated artifacts for five aggregate-root tables:

| Fixture table | ID strategy/backing | Deleted backing/default | Version |
|---|---|---|---|
| `video_post` | identity / integral `Long` | `BIGINT DEFAULT 0` | versioned |
| `snowflake_long_record` | snowflake / `Long` | `BIGINT DEFAULT 0` | versionless |
| `snowflake_string_record` | snowflake / `String(19)` | `VARCHAR(19) DEFAULT '0'` | versionless |
| `uuid_string_record` | uuid7 / `String(36)` | `VARCHAR(36) DEFAULT nil UUID` | versionless |
| `uuid_native_record` | uuid7 / native `UUID` | `UUID DEFAULT nil UUID` | versionless |

Assert exact generated source evidence:

- identity entity has `@GeneratedValue(strategy = GenerationType.IDENTITY)`, integral raw deleted initializer, versioned `@SQLDelete`, and `@Where`;
- four application-side entities use entity-specific `@EmbeddedId` Strong ID types and never primitive ID constructor parameters;
- Snowflake `Long` and Snowflake `String` both generate Strong IDs, generated-own-ID accessors, and entries in `GeneratedOwnIdCatalogContribution`;
- UUID7 `String` and UUID7 native `UUID` both generate Strong IDs/accessors/catalog entries;
- raw deleted property types/initializers are `Long = 0L`, `String = "0"`, `String = nil`, and `UUID = UUID(0L, 0L)` as appropriate;
- `deleted` is absent from every entity constructor and every generated factory payload/call;
- the Snowflake Long, Snowflake String, UUID7 String, and UUID7 native factory contexts all have `constructorMappingResolved = true`;
- those four rendered factory sources call their entity constructors and do not contain `TODO("Implement aggregate construction")`;
- the identity entity is not counted as factory-construction evidence, and its existing unresolved factory body remains unchanged;
- SQLDelete/Where use backticks because these fixtures run H2 MySQL mode;
- versioned SQL uses ID then version placeholders, versionless SQL uses only ID;
- no generated source contains `ApplicationSideId`, `snowflake-long`, or a deleted property typed as an entity Strong ID.

For the compile test, assert all five entities, all four generated-own-ID accessor files, `GeneratedOwnIdCatalogContribution.kt`, and the four application-side factory files exist before invoking compilation. If a generated identity factory file also exists because factory generation is enabled globally, file existence or compilation of its `TODO` body must not be reported as aggregate-construction evidence.

**Step 2: Run the two tests to verify RED**

```powershell
Invoke-CheckedNative {
    & .\gradlew.bat :cap4k-plugin-pipeline-gradle:test --tests "com.only4.cap4k.plugin.pipeline.gradle.PipelinePluginFunctionalTest.aggregate provider specific persistence generation renders bounded controls" --tests "com.only4.cap4k.plugin.pipeline.gradle.PipelinePluginCompileFunctionalTest.aggregate provider specific persistence generation participates in domain compileKotlin" --console=plain
}
```

Expected: tests fail because each fixture currently contains only `video_post`/`audit_log`, factory generation is disabled, and the four application-side combinations/artifacts are absent.

**Step 3: Expand both schema fixtures minimally**

Keep H2 MySQL-mode URLs and replace the two-table schema with the five-table matrix above. Use column comments with the existing exact vocabulary:

```sql
comment '@IdStrategy=db_identity;'
comment '@IdStrategy=snowflake;'
comment '@IdStrategy=uuid7;'
comment '@Managed=deleted;'
comment '@Managed=version;'
```

Update `includeTables` to all five physical names. In both fixture `build.gradle.kts` files, enable:

```kotlin
generators {
    aggregate {
        artifacts {
            factory.set(true)
        }
    }
}
```

Do not configure a `snowflake-long` strategy and do not alter current Snowflake generator/runtime classes.

**Step 4: Update the compile smoke to exercise factory signatures**

Expand `AggregateProviderPersistenceCompileSmoke.kt` to import all five generated entities plus the four application-side factories. For each application-side factory, instantiate its payload without `deleted` and call `factory.create(payload)` so compilation proves the real constructor call, not merely payload availability. The smoke must reference the Strong ID/entity types and generated-own-ID catalog/accessor types so visibility/import mismatches fail compilation.

Do not call the identity factory or count its payload/file/compilation as construction evidence. Assert the four application-side rendered factory sources contain the entity-constructor call and no `TODO("Implement aggregate construction")` before running compileKotlin.

Do not manually pass application-side IDs or deleted sentinels to factory payloads. Do not call a child factory or public child persist API.

**Step 5: Run RED/GREEN functional generation and compile verification**

```powershell
Invoke-CheckedNative {
    & .\gradlew.bat :cap4k-plugin-pipeline-gradle:test --tests "com.only4.cap4k.plugin.pipeline.gradle.PipelinePluginFunctionalTest.aggregate provider specific persistence generation renders bounded controls" --tests "com.only4.cap4k.plugin.pipeline.gradle.PipelinePluginCompileFunctionalTest.aggregate provider specific persistence generation participates in domain compileKotlin" --console=plain
}
```

Expected:

- `cap4kGenerate` succeeds for the generation fixture;
- `:demo-domain:compileKotlin` succeeds for the compile fixture;
- all assertions for five ID/storage combinations, constructors, raw deleted properties, SQL annotations, accessors/catalog, and four real application-side factory constructions pass;
- no application-side factory body contains the unresolved `TODO` fallback;
- identity is not claimed as factory-construction evidence.

Then run all related functional classes:

```powershell
Invoke-CheckedNative {
    & .\gradlew.bat :cap4k-plugin-pipeline-gradle:test --tests "com.only4.cap4k.plugin.pipeline.gradle.PipelinePluginFunctionalTest" --tests "com.only4.cap4k.plugin.pipeline.gradle.PipelinePluginCompileFunctionalTest" --console=plain
}
```

Expected: both classes pass. Do not run or fix bootstrap preview tests in this task.

**Step 6: Commit**

```powershell
git add cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginFunctionalTest.kt cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginCompileFunctionalTest.kt cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-provider-persistence-sample cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-provider-persistence-compile-sample
git commit -m "test(pipeline): compile soft delete id strategy matrix"
```

---

### Task 10: Prove the complete H2 soft-delete lifecycle

**Files:**

- Create: `cap4k-ddd-starter/src/test/kotlin/com/only4/cap4k/ddd/runtime/softdelete/standard/SoftDeleteIdentityH2RuntimeTest.kt`
- Create: `cap4k-ddd-starter/src/test/kotlin/com/only4/cap4k/ddd/runtime/softdelete/mixedcase/SoftDeleteUuidStringH2RuntimeTest.kt`
- Create: `cap4k-ddd-starter/src/test/kotlin/com/only4/cap4k/ddd/runtime/softdelete/mysql/SoftDeleteH2MySqlRuntimeTest.kt`
- Verify only: `cap4k-ddd-starter/src/test/kotlin/com/only4/cap4k/ddd/runtime/strongid/StrongIdUowRuntimeTest.kt`
- Verify only: `cap4k-ddd-starter/src/test/kotlin/com/only4/cap4k/ddd/runtime/strongid/StrongIdJpaRuntimeTest.kt`
- No production starter file may be modified.

Each new test uses an isolated package, its own `@DataJpaTest` application/entity scan/repository scan, and local generated-shape fixtures. This follows the existing starter isolation pattern and prevents repository/entity collisions.

**Step 1: Write the RED standard-H2 versioned identity lifecycle**

Create a test-local entity/repository with:

- unquoted physical names that H2 metadata/JDBC exposes uppercase;
- identity `Long` ID;
- `@Version` field;
- mapped `deleted: Long = 0L` absent from the constructor;
- double-quoted versioned `@SQLDelete` and double-quoted `@Where` exactly matching the generator contract.

Test sequence:

1. construct without `deleted`;
2. save/flush and assert generated ID and active sentinel `0` via direct JDBC;
3. remove/flush/clear;
4. assert repository/Hibernate active query returns no entity;
5. direct JDBC asserts one physical row remains and `deleted = id`;
6. assert the versioned SQL path succeeded (no placeholder-order exception).

Run before adding the local fixture implementation:

```powershell
.\gradlew.bat :cap4k-ddd-starter:test --tests "com.only4.cap4k.ddd.runtime.softdelete.standard.SoftDeleteIdentityH2RuntimeTest" --console=plain
```

Expected: test compilation fails until the test-local entity/repository/config are completed; with old constructor/delete behavior it must not pass the lifecycle assertions.

**Step 2: Add the minimum standard-H2 test fixture and make it GREEN**

Add only test-local JPA code/annotations needed by the test. Do not add production helpers.

Run the command from Step 1. Expected: pass.

**Step 3: Write the RED quoted mixed-case UUID7 String lifecycle**

Create a separate package/test with:

- `DATABASE_TO_UPPER=false`, standard H2 mode (not MySQL mode and not H2 PostgreSQL mode);
- quoted mixed-case table/ID/deleted names;
- UUID7 `String` Strong ID `@EmbeddedId`;
- raw `deleted: String = canonicalNilUuid` absent from constructor/factory payload;
- versionless double-quoted SQLDelete/Where;
- a test-local generated-own-ID accessor/catalog plus `DefaultAggregateFactorySupervisor`/`JpaUnitOfWork` setup modeled on `StrongIdUowRuntimeTest`, without changing that production or fixture code.

Test sequence must assert the factory supervisor returns the entity with an assigned Strong ID before `save`, while `deleted` already equals the nil sentinel. Then persist/flush, delete/flush, prove Hibernate filtering, physical row retention, and raw `deleted == id.value` through direct JDBC.

Run:

```powershell
.\gradlew.bat :cap4k-ddd-starter:test --tests "com.only4.cap4k.ddd.runtime.softdelete.mixedcase.SoftDeleteUuidStringH2RuntimeTest" --console=plain
```

Expected RED before completing the local accessor/UoW/entity fixture; GREEN after the minimum test-only implementation.

**Step 4: Write the RED H2 MySQL-mode Snowflake Long, Snowflake String, and native UUID lifecycle**

Create one isolated MySQL-mode test application containing three versionless entities:

- Snowflake `Long` Strong ID with raw `deleted: Long = 0L`;
- Snowflake `String` Strong ID with raw `deleted: String = "0"`;
- UUID7 native `UUID` Strong ID with raw `deleted: UUID = UUID(0L, 0L)`.

Use backtick physical identifiers and exact generated SQL shapes. Provide test-local generated-own-ID accessors/catalog and create all three entities through `DefaultAggregateFactorySupervisor`; assert each Strong ID is assigned before save and each deleted sentinel is already initialized. For each entity, run create/flush, sentinel JDBC check, remove/flush, filtered repository check, row-retention check, and `deleted == id.value` check. The Snowflake Long case must prove that a `StrongId<Long>` used as `@EmbeddedId` binds correctly to Hibernate's SQLDelete ID placeholder and that the physical `BIGINT deleted = id` assignment executes. The native test must execute the explicit `CAST('00000000-0000-0000-0000-000000000000' AS UUID)` active predicate through Hibernate/JDBC, not merely compare a generated string.

Run:

```powershell
Invoke-CheckedNative {
    & .\gradlew.bat :cap4k-ddd-starter:test --tests "com.only4.cap4k.ddd.runtime.softdelete.mysql.SoftDeleteH2MySqlRuntimeTest" --console=plain
}
```

Expected RED before local fixtures are complete; GREEN after the minimum test-only fixtures.

**Step 5: Run the complete H2 evidence set**

```powershell
Invoke-CheckedNative {
    & .\gradlew.bat :cap4k-ddd-starter:test --tests "com.only4.cap4k.ddd.runtime.softdelete.*" --tests "com.only4.cap4k.ddd.runtime.strongid.StrongIdUowRuntimeTest.factory supervisor returns an id ready root graph before save" --tests "com.only4.cap4k.ddd.runtime.strongid.StrongIdJpaRuntimeTest" --console=plain
}
```

Expected:

- create, active sentinel, query filter, SQLDelete, physical row retention, and `deleted = id` all execute against real H2;
- versioned and versionless paths pass;
- standard uppercase, quoted mixed case, and H2 MySQL-mode identifier behavior pass;
- identity integral ZERO, Snowflake Long integral ZERO, Snowflake String character ZERO, UUID7 character NIL UUID, and UUID7 native UUID NIL UUID execute;
- Snowflake Long proves real `StrongId<Long>` SQLDelete placeholder binding and `deleted = id.value`, not only general JDBC mapping;
- existing Strong ID/UoW allocation tests remain unchanged and green.

If Hibernate placeholder order, Strong ID physical binding, native UUID CAST, or constructor removal fails at runtime, stop and return to the design's Rollback Triggers. Do not add a cast/fallback/converter or change UoW behavior to force the test green.

**Step 6: Commit**

```powershell
git add cap4k-ddd-starter/src/test/kotlin/com/only4/cap4k/ddd/runtime/softdelete
git commit -m "test(starter): prove h2 soft delete lifecycle matrix"
```

---

### Task 11: Add mandatory real PostgreSQL evidence and CI provisioning

**Files:**

- Modify: `cap4k-plugin-pipeline-gradle/build.gradle.kts`
- Create: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PostgreSqlSoftDeleteIntegrationTest.kt`
- Modify: `.github/workflows/ci.yml`

**Environment contract:**

- `CAP4K_TEST_POSTGRES_URL`
- `CAP4K_TEST_POSTGRES_USER`
- `CAP4K_TEST_POSTGRES_PASSWORD`

Local behavior: if all three variables are absent, skip with a JUnit assumption and state that real PostgreSQL evidence did not run. If any are partially configured, fail with the missing names. If `CI=true`, absence of any variable is a hard test failure. H2 `MODE=PostgreSQL` is forbidden in this test.

**Step 1: Write the RED integration test before adding the driver**

The test must:

1. enforce the environment contract above;
2. connect to the real URL and create a unique temporary schema;
3. create a quoted mixed-case table such as `"PgUuidRecord"` with native UUID `"Id"`, native UUID `"Deleted" NOT NULL DEFAULT '00000000-0000-0000-0000-000000000000'`, a normal field, and column comments `@IdStrategy=uuid7;` / `@Managed=deleted;`;
4. configure `ProjectConfig.sources["db"]` with the real PostgreSQL URL, credentials, temporary schema, and exact include table;
5. run `DbSchemaSourceProvider.collect`, then `DefaultCanonicalAssembler.assemble`, then `AggregateArtifactPlanner.plan`, then `PebbleArtifactRenderer(PresetTemplateResolver("ddd-default", emptyList())).render`;
6. assert source metadata is `jdbcType OTHER or BINARY`, `dbType uuid`, Kotlin `UUID`, and the actual PostgreSQL `COLUMN_DEF` wrapper normalizes to `NIL_UUID`;
7. assert canonical policy is `NATIVE_UUID` + `NIL_UUID` and contains no SQL;
8. assert rendered source keeps an entity-specific Strong ID for `id`, raw `UUID` for `deleted`, `UUID(0L, 0L)` initializer, no deleted constructor argument, and double-quoted PostgreSQL SQL;
9. set the JDBC connection schema, insert an active row, and execute the planner's actual `softDeleteWhereClause`/`softDeleteSql` strings with a prepared ID parameter;
10. prove active count is 1 before delete and 0 after, one physical row remains, and physical deleted equals ID;
11. drop the temporary schema in `finally`.

Run with a real local PostgreSQL environment while the PostgreSQL driver is not yet declared:

```powershell
$env:CAP4K_TEST_POSTGRES_URL = "jdbc:postgresql://localhost:5432/cap4k_test"
$env:CAP4K_TEST_POSTGRES_USER = "cap4k"
$env:CAP4K_TEST_POSTGRES_PASSWORD = "cap4k"
.\gradlew.bat :cap4k-plugin-pipeline-gradle:test --tests "com.only4.cap4k.plugin.pipeline.gradle.PostgreSqlSoftDeleteIntegrationTest" --console=plain
```

Expected RED: `No suitable driver`/PostgreSQL driver class unavailable. If no real local PostgreSQL is available, do not claim RED/GREEN database evidence locally; rely on the mandatory CI run added below and report local skip explicitly.

**Step 2: Add the minimum test dependency and run real PostgreSQL GREEN**

Add only:

```kotlin
testImplementation(libs.postgresql)
```

to `cap4k-plugin-pipeline-gradle/build.gradle.kts`. Do not make PostgreSQL a production plugin dependency unless current production classloading proves that is already required; `DbSchemaSourceProvider` loads the driver present on the test/runtime classpath.

Rerun the command from Step 1 against PostgreSQL. Expected: the complete metadata → core → generator → renderer → SQL execution test passes. If it fails because the driver returns a new default wrapper, capture the exact value in the failing assertion and add only the smallest explicit normalizer branch from Task 4. If PostgreSQL rejects the explicit UUID CAST or SQL shape, stop at design review; do not add an implicit/fallback path.

**Step 3: Provision PostgreSQL in GitHub Actions**

In the existing required `check` job in `.github/workflows/ci.yml`, add:

```yaml
services:
  postgres:
    image: postgres:16-alpine
    env:
      POSTGRES_DB: cap4k_test
      POSTGRES_USER: cap4k
      POSTGRES_PASSWORD: cap4k
    ports:
      - 5432:5432
    options: >-
      --health-cmd="pg_isready -U cap4k -d cap4k_test"
      --health-interval=10s
      --health-timeout=5s
      --health-retries=10
env:
  CAP4K_TEST_POSTGRES_URL: jdbc:postgresql://localhost:5432/cap4k_test
  CAP4K_TEST_POSTGRES_USER: cap4k
  CAP4K_TEST_POSTGRES_PASSWORD: cap4k
```

Merge the job-level `env` with any existing environment rather than replacing it. Keep the existing required `./gradlew check` step; do not create an optional PostgreSQL job whose skip could make support appear verified.

**Step 4: Verify local gating behavior**

Run once without the three variables and without `CI=true`:

```powershell
Remove-Item Env:CAP4K_TEST_POSTGRES_URL -ErrorAction SilentlyContinue
Remove-Item Env:CAP4K_TEST_POSTGRES_USER -ErrorAction SilentlyContinue
Remove-Item Env:CAP4K_TEST_POSTGRES_PASSWORD -ErrorAction SilentlyContinue
.\gradlew.bat :cap4k-plugin-pipeline-gradle:test --tests "com.only4.cap4k.plugin.pipeline.gradle.PostgreSqlSoftDeleteIntegrationTest" --console=plain
```

Expected: exactly one explicit assumption skip, not a pass claim.

Then prove CI cannot silently skip:

```powershell
$env:CI = "true"
.\gradlew.bat :cap4k-plugin-pipeline-gradle:test --tests "com.only4.cap4k.plugin.pipeline.gradle.PostgreSqlSoftDeleteIntegrationTest" --console=plain
Remove-Item Env:CI
```

Expected: hard failure naming the three required variables. Finally rerun with the real variables and expect pass.

**Step 5: Commit**

```powershell
git add cap4k-plugin-pipeline-gradle/build.gradle.kts cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PostgreSqlSoftDeleteIntegrationTest.kt .github/workflows/ci.yml
git commit -m "test(pipeline): require real postgresql soft delete evidence"
```

---

### Task 12: Run focused verification, full check, and scope audit

**Files:**

- Verify all files changed by Tasks 1–11.
- Do not modify production/test/CI in this task unless a new feature regression is proven. Never repair the known bootstrap baseline failures here.

**Step 1: Search for forbidden compatibility and scope drift**

Run:

```powershell
Assert-NoNativeMatches -Command {
    & rg -n -g "**/src/main/**" "activeValue|activePredicateSql|deleteAssignmentSql|snowflake-long|ApplicationSideId|IdentifierQuoteStyle|DOUBLE_QUOTE" cap4k-plugin-pipeline-api cap4k-plugin-pipeline-core cap4k-plugin-pipeline-generator-aggregate cap4k-plugin-pipeline-renderer-pebble cap4k-plugin-pipeline-gradle cap4k-ddd-starter
} -FailureMessage "Forbidden compatibility symbols remain in production/template sources"
Invoke-CheckedNative {
    & git diff --name-only 502ce172cf5925099c3332ce129039a5b02e5353..HEAD
}
```

Expected:

- the negative scan is non-empty only on failure and checks real module paths through `**/src/main/**`; no current production/template consumer retains the removed policy fields or fallback (negative assertions in tests may name removed symbols);
- no new `snowflake-long`/`ApplicationSideId` implementation appears (historical docs outside the changed scope are not edited);
- changed files are limited to API/core/generator/template/source-db tests/functional fixtures/starter runtime tests/PostgreSQL test dependency/CI named by this plan;
- no Phase 4 spec/plan, UoW production, owned-child lifecycle production, Snowflake infrastructure, or unrelated file appears.

**Step 2: Run the focused module suite**

```powershell
Invoke-CheckedNative {
    & .\gradlew.bat :cap4k-plugin-pipeline-api:test :cap4k-plugin-pipeline-source-db:test :cap4k-plugin-pipeline-core:test :cap4k-plugin-pipeline-generator-aggregate:test :cap4k-plugin-pipeline-renderer-pebble:test --console=plain
}
Invoke-CheckedNative {
    & .\gradlew.bat :cap4k-plugin-pipeline-gradle:test --tests "com.only4.cap4k.plugin.pipeline.gradle.PipelinePluginFunctionalTest" --tests "com.only4.cap4k.plugin.pipeline.gradle.PipelinePluginCompileFunctionalTest" --tests "com.only4.cap4k.plugin.pipeline.gradle.PostgreSqlSoftDeleteIntegrationTest" --console=plain
}
Invoke-CheckedNative {
    & .\gradlew.bat :cap4k-ddd-starter:test --tests "com.only4.cap4k.ddd.runtime.softdelete.*" --tests "com.only4.cap4k.ddd.runtime.strongid.StrongIdUowRuntimeTest.factory supervisor returns an id ready root graph before save" --tests "com.only4.cap4k.ddd.runtime.strongid.StrongIdJpaRuntimeTest" --console=plain
}
```

Expected: all focused tests pass. The evidence summary must state whether the PostgreSQL test executed or was locally skipped. A skip is not PostgreSQL support evidence; obtain a green mandatory CI run before declaring the feature complete.

**Step 3: Run the required full build**

In the CI/Linux evidence environment with PostgreSQL variables present:

```bash
./gradlew check
```

Windows local equivalent:

```powershell
Invoke-CheckedNative { & .\gradlew.bat check --console=plain }
```

Expected target: full green, including real PostgreSQL. If the only failures are the two exact `PipelinePluginBootstrapGeneratedProjectFunctionalTest` baseline cases recorded above and their output matches the pre-change baseline, record them as known baseline and do not edit their code/fixtures. Any other failure is a feature regression and blocks completion.

**Step 4: Verify the complete committed diff, remaining worktree diff, read-only document hashes, and status**

Restore `$planBlobHashBefore` and `$specBlobHashBefore` in this PowerShell process from the exact values recorded in Task 0 implementation notes, then run:

```powershell
Invoke-CheckedNative { & git diff --check 502ce172cf5925099c3332ce129039a5b02e5353..HEAD }
Invoke-CheckedNative { & git diff --cached --check }
Invoke-CheckedNative { & git diff --check }
$planBlobHashAfter = & git hash-object docs/superpowers/plans/2026-07-26-cap4k-soft-delete-id-strategy-support.md
if ($LASTEXITCODE -ne 0) { throw "Unable to hash implementation plan" }
$specBlobHashAfter = & git hash-object docs/superpowers/specs/2026-07-26-cap4k-soft-delete-id-strategy-support-design.md
if ($LASTEXITCODE -ne 0) { throw "Unable to hash design spec" }
if ($planBlobHashAfter -ne $planBlobHashBefore) { throw "Read-only implementation plan changed during execution" }
if ($specBlobHashAfter -ne $specBlobHashBefore) { throw "Read-only design spec changed during execution" }
$status = @(& git status --short --untracked-files=all | Sort-Object)
if ($LASTEXITCODE -ne 0) { throw "Unable to inspect final worktree status" }
$expectedStatus = @(
    "?? docs/superpowers/plans/2026-07-26-cap4k-soft-delete-id-strategy-support.md",
    "?? docs/superpowers/specs/2026-07-26-cap4k-soft-delete-id-strategy-support-design.md"
) | Sort-Object
if (Compare-Object $expectedStatus $status) {
    $status | Write-Output
    throw "Final status must contain only the original read-only untracked plan and spec"
}
```

Expected:

- `git diff --check 502ce172...HEAD` checks whitespace across every committed implementation change from the locked base;
- cached and working-tree `git diff --check` commands also pass, so no final staged/unstaged whitespace defect is hidden;
- plan/spec hashes equal the Task 0 values, and the approved design spec still equals `3a52a448b287b2965b6248bee625ccf9538f6c3e`;
- status contains exactly the original read-only untracked plan and spec; every implementation change is committed;
- no generated build output is staged.

**Step 5: Produce the implementation evidence summary**

Report:

- focused command results;
- `./gradlew check` result, explicitly separating the two known baseline failures if they recur;
- whether real PostgreSQL executed and passed (CI URL redacted) or was skipped locally;
- H2 cases executed: standard uppercase, quoted mixed case, MySQL mode, versioned/versionless, identity integral, Snowflake Long, Snowflake String, UUID7 String, and native UUID;
- the resolved identity-factory evidence decision and proof that no application-side factory construction passed through the template `TODO` fallback;
- the final changed-file list;
- confirmation that Snowflake Long and String remain, Strong IDs remain value objects, deleted is raw storage/out of constructors, core contains no SQL, the unknown-dialect fallback is gone, and Phase 4/UoW/owned-child/Snowflake infrastructure remained untouched.

**Step 6: Final commit only if the execution workflow requires one**

If Tasks 1–11 were committed separately and Task 12 made no file changes, do not create an empty commit. If a narrowly proven feature regression required a permitted file change, rerun all Task 12 checks and commit that fix with a scoped message.

---

## Current-Code Evidence Checked While Authoring This Plan

The plan above was derived from the checked-out HEAD, not from the design headings alone. The author inspected these current facts:

- `cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModels.kt` currently defines `DbIdStrategy.DB_IDENTITY`, `UUID7`, and `SNOWFLAKE`; `AggregateSoftDeletePolicy` still contains `activeValue`, `activePredicateSql`, and `deleteAssignmentSql`; `ResolvedIdPolicy` carries the canonical strategy string; deleted managed fields can be `SYSTEM_TRANSITION_ONLY`.
- `cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/AggregateStrongIdBackingResolver.kt` currently owns duplicate character/native UUID classification and already supports UUID7 String/native UUID plus Snowflake String/Long.
- `cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/AggregateSoftDeletePolicyResolver.kt` currently owns a second numeric regex/capacity model, accepts only default zero, and emits SQL-bearing policy fields.
- `cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/AggregateSpecialFieldPolicyResolver.kt` already classifies deleted as `SYSTEM_TRANSITION_ONLY`, registers it as managed, and excludes it from create/update write surfaces. That behavior should be reused, not redesigned.
- `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/GeneratedOwnIdPlanning.kt` already selects UUID7/Snowflake application-side IDs with String/UUID/Long backings. It is outside this feature's production change set.
- `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/EntityArtifactPlanner.kt` currently resolves quote style eagerly, renders old policy SQL, includes every scalar except generated own ID in `constructorFields`, and falls back to double quotes for missing/unknown URLs.
- `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/FactoryArtifactPlanner.kt` already derives payloads from the resolved create write surface and can defer `SYSTEM_TRANSITION_ONLY` managed fields. Current database-identity coverage records `constructorMappingResolved = false`; `factory.kt.peb` renders `TODO("Implement aggregate construction")` for that state, so successful compilation is not real identity factory-construction evidence.
- `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/SpecificationArtifactPlanner.kt` currently generates only root specification skeletons and no owned-child input spec. The plan therefore tests the existing non-root constructor carrier and does not invent a new artifact.
- `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/entity.kt.peb` currently initializes ordinary scalar properties from same-named constructor parameters, which is the concrete deleted-constructor leak.
- `cap4k-plugin-pipeline-source-db/src/main/kotlin/com/only4/cap4k/plugin/pipeline/source/db/DbSchemaSourceProvider.kt` already preserves JDBC type, vendor type, column size, raw `COLUMN_DEF`, and exact discovered table/column names; it recognizes the PostgreSQL driver and uses schema scope for PostgreSQL/H2.
- `cap4k-plugin-pipeline-source-db/src/main/kotlin/com/only4/cap4k/plugin/pipeline/source/db/JdbcTypeMapper.kt` maps exact vendor `uuid` on `OTHER`/`BINARY` to `UUID`, maps `BIGINT` to `Long`, integer families to `Int`, and character families to `String`.
- `cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-provider-persistence-compile-sample` currently contains only identity `video_post`/`audit_log`, runs H2 MySQL mode, has factory generation disabled, and compiles generated entities from the domain module.
- `cap4k-plugin-pipeline-gradle/build.gradle.kts` already depends on pipeline core/generator/renderer/source-db and H2, but lacks PostgreSQL test dependency. The version catalog already provides PostgreSQL 42.7.2.
- `.github/workflows/ci.yml` currently has one required `check` job and no database service/environment, so mandatory real PostgreSQL evidence must be wired into that job.
- `cap4k-ddd-starter/src/test/kotlin/com/only4/cap4k/ddd/runtime/strongid/StrongIdJpaRuntimeTest.kt` already proves String/UUID/Long Strong ID JDBC backing; `StrongIdUowRuntimeTest.kt` already proves generated root/child IDs exist before save. New H2 soft-delete tests should follow their isolated `@DataJpaTest` pattern without changing UoW production behavior.

## Spec Gaps

No blocking Spec Gap remains. The design decision is now explicit: real aggregate factory-construction evidence applies only to Snowflake Long, Snowflake String, UUID7 String, and UUID7 native UUID. Identity integral uses the fixed entity/write-surface/SQL/H2 acceptance listed in Task 9.

The existing database-identity factory `constructorMappingResolved = false` / `TODO("Implement aggregate construction")` behavior remains a documented, independent capability gap outside this iteration. It does not block this plan, must not be changed through `FactoryArtifactPlanner`, and must not be counted as construction evidence through file existence, payload compilation, or compilation of the `TODO` body.

The approved design and current code determine the supported storage matrix, semantic API break, catalog consumer order, finite default grammar, generator dialect behavior, constructor/write-surface rule, H2 lifecycle evidence, real PostgreSQL requirement, and factory-evidence scope. This plan is ready for implementation from Task 0.

Two implementation details are intentionally fixed by this plan without expanding product semantics:

- The PostgreSQL environment variable names are `CAP4K_TEST_POSTGRES_URL`, `CAP4K_TEST_POSTGRES_USER`, and `CAP4K_TEST_POSTGRES_PASSWORD`; they merely operationalize the spec's required CI service/environment and do not alter runtime API.
- Because the current generator has no owned-child input-spec artifact, “owned-child spec excludes deleted” is verified at the existing non-root entity constructor context. The plan explicitly forbids creating a new child factory/spec/public persist boundary.

If implementation reveals a rollback trigger from the approved design—Hibernate SQLDelete placeholder order, Strong ID binding incompatibility, explicit native UUID CAST rejection, a required converter, or constructor removal preventing Hibernate/factory compilation—stop and return to design review. Do not invent a fallback or mark the plan complete.

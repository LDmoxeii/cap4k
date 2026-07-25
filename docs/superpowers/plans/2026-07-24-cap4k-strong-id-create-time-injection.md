# cap4k Strong ID Create-Time Injection Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Complete Phase 4 so every supported application-side entity ID is a storage-nearest Strong ID, generated roots and owned children receive IDs at official lifecycle entry points, pending child entries reconcile into their pending top-level roots, and the active `@ApplicationSideId` and `snowflake-long` contracts are deleted.

**Architecture:** JDBC metadata resolves the backing primitive before code generation. Generated Strong IDs own value semantics only. Generated typed accessors allocate through `Mediator.identifiers`, one generated module catalog contributes those accessors, and a framework registry is the only runtime lookup mechanism. Generated owned-relation code invokes accessors before relation mutation; JPA UoW completes bounded owned graphs through the registry and reconciles duplicate child entries into roots before persistence sets are constructed.

**Tech Stack:** Kotlin, Gradle, JDBC `DatabaseMetaData`, Jakarta Persistence/Hibernate, Spring Boot auto-configuration, Jackson, Pebble, JUnit 5, MockK, Kotlin compile-testing.

**Design spec:** `docs/superpowers/specs/2026-07-24-cap4k-strong-id-create-time-injection-design.md`

## Global Constraints

- Read the complete design spec before Task 1. This plan is executable detail; the spec remains the semantic authority.
- Run every command from the repository root. Do not install dependencies.
- Keep each commit limited to the task that names it. Do not stage unrelated dirty files.
- Do not modify Soft Delete policy resolution, SQL rendering, sentinels, or Soft Delete tests in Phase 4.
- Do not modify completed Phase 3.75 documents.
- Do not rewrite historical `docs/superpowers/**` files to remove old vocabulary. Active scans intentionally exclude historical docs.
- Do not preserve `@ApplicationSideId`, `snowflake-long`, Strong ID `new()`, reflection, converters, guessed JDBC evidence, or compatibility aliases as fallback.
- Do not add root/child/provenance annotations, markers, public child factories, or a public child-persistence contract.
- Do not add `Mediator`, `UnitOfWork`, `Repository`, `EntityManager`, `IdentifierGenerator`, or registry dependencies to `OwnedEntityList`.
- Do not broaden JPA owned traversal to inverse, weak, or arbitrary relations.
- All new generated artifacts use `ArtifactOutputKind.GENERATED_SOURCE` and `ConflictPolicy.OVERWRITE`; they have no handwritten slots.
- Preserve plain database-identity behavior and existing identifier strategies used by `Mediator.identifiers`.
- If current source materially differs from the evidence below, update the spec and this plan before changing production code.

## Fixed Phase 4 Matrix And Recognition Boundary

| Strategy | JDBC/storage evidence | Strong ID backing | Required capacity | JPA representation | JSON representation |
|---|---|---|---|---|---|
| UUID7 / String | character JDBC type mapped to Kotlin `String` | `String` | at least 36 | direct embeddable column with physical character length | scalar string |
| UUID7 / UUID | `OTHER` or `BINARY`, DB type name `uuid`, mapped to `java.util.UUID` | `UUID` | not applicable | direct native UUID embeddable column | scalar string |
| SNOWFLAKE / String | character JDBC type mapped to Kotlin `String` | `String` | at least 19 | direct embeddable column with physical character length | scalar string; numeric token rejected |
| SNOWFLAKE / Long | `BIGINT` mapped to Kotlin `Long` | `Long` | not applicable | direct BIGINT embeddable column | scalar string; numeric token rejected |

- A handwritten application-side entity receives framework allocation only when it supplies a Strong ID, typed accessor, and catalog contribution. An unregistered entity is never inferred by annotation, field name, ID value, reflection, or JPA metadata.
- A plain database-identity JPA entity remains provider-managed.
- An isolated caller-declared `persist(entity, CREATE)` with no provable pending owner remains a top-level entry. Phase 4 adds no marker merely to reject that call.

## Current Evidence And File Responsibilities

| Area | Current evidence | Phase 4 responsibility |
|---|---|---|
| Pipeline snapshot | `PipelineModels.kt` has `DbIdStrategy.DB_IDENTITY/UUID7`; `DbColumnSnapshot` lacks JDBC type/size | retain `DATA_TYPE` and `COLUMN_SIZE`; add canonical `SNOWFLAKE` |
| ID policy | `AggregateIdPolicyResolver.kt` recognizes `snowflake-long` and validates primitive entity ID types | recognize only `identity`, `uuid7`, `snowflake`; let backing resolver validate application-side storage |
| Canonical Strong ID | `DefaultCanonicalAssembler.kt` creates own Strong IDs only for UUID7 String storage | create UUID7 String/UUID and Snowflake String/Long own Strong IDs from strict evidence |
| Strong ID runtime | `StrongId` exposes `String`; `StrongIds` allocates UUID7 | generic value contract and validation only |
| Strong ID generation | `strong_id.kt.peb` hardcodes String/UUID7/length 36 and optional `new()` | render four value/storage combinations, scalar-string JSON, no allocation |
| Entity/factory generation | entity constructor contains every scalar field; factory inserts `OwnId.new()` | application-side own ID is `lateinit`, absent from constructor and payload |
| Lifecycle SPI | no generated typed own-ID accessor/catalog/registry exists | add typed accessor, module catalog, registry, assignment helper |
| Owned relation | `OwnedEntityList.add/replace` mutates directly | add a narrow infrastructure-free pre-mutation hook |
| JPA completion | `JpaGeneratedStrongIdSupport` scans `@EmbeddedId`, `StrongId`, companion `new()` | registry lookup only, no reflection fallback |
| Legacy runtime | `JpaApplicationSideIdSupport` and `@ApplicationSideId` remain active | delete annotation, helper, branches, tests, and fixtures |
| Pending children | `JpaUnitOfWork` rejects a pending child reachable from a pending root | reconcile to the unique outermost pending root before persistence sets |
| Compiler inference | IR compiler infers generated entities from `@ApplicationSideId` | remove inference and retain explicit design/`@AggregateElement` evidence |

Primary ownership:

- `cap4k-plugin-pipeline-api`: snapshot and canonical model fields only.
- `cap4k-plugin-pipeline-source-db`: raw JDBC evidence and DB comment strategy parsing.
- `cap4k-plugin-pipeline-core`: storage-nearest resolution and canonical/JPA projection.
- `ddd-core`: Strong ID value contract, generated own-ID SPI, relation carrier hook.
- `ddd-domain-repo-jpa`: registry-only graph completion and pending-entry reconciliation.
- `cap4k-ddd-starter`: identifier strategy assembly, generated catalog collection, registry/UoW wiring, runtime integration tests.
- `cap4k-plugin-pipeline-generator-aggregate`: artifact selection and render context.
- `cap4k-plugin-pipeline-renderer-pebble`: generated Kotlin shape and compile tests.
- `cap4k-plugin-code-analysis-compiler`: removal of annotation-based inference only.

## Task Dependency Map

```text
1 strategy vocabulary -> 2 JDBC evidence -> 3 backing resolver -> 4 canonical integration -> 5 JPA length
6 StrongId core -> 7 StrongId planning -> 8 StrongId rendering -> 9 JPA/JSON runtime
10 assignment helper -> 11 accessor/catalog/registry -> 12 starter registry
10 -> 13 OwnedEntityList hook
11 + 12 -> 14 registry-only JPA completion -> 15 UoW/starter registry wiring -> 16 legacy runtime deletion
16 -> 17 UoW lifecycle timing -> 18 reconciliation mechanics -> 19 ownership edge cases
4 + 7 + 11 -> 20 accessor artifact planning -> 21 accessor/catalog rendering
8 + 20 + 21 -> 22 entity/factory constructor migration -> 23 factory readiness
13 + 21 + 22 -> 24 owned-relation injection
16 + 22 -> 25 compiler/dead-context cleanup
9 + 19 + 21 + 23 + 24 + 25 -> 26 generated-consumer/runtime matrix -> 27 final audit
```

Tasks sharing a production file are sequential even when their conceptual branches are independent. A subagent may implement only one task at a time and must return the exact commit hash, commands, and output summary before the next task touching those files starts.

---

## Task 1: Normalize The Active Strategy Vocabulary

**Depends on:** none

**Files:**

- Modify: `cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModels.kt`
- Modify: `cap4k-plugin-pipeline-source-db/src/main/kotlin/com/only4/cap4k/plugin/pipeline/source/db/DbColumnAnnotationParser.kt`
- Modify: `cap4k-plugin-pipeline-source-db/src/test/kotlin/com/only4/cap4k/plugin/pipeline/source/db/DbColumnAnnotationParserTest.kt`
- Modify: `cap4k-ddd-starter/src/test/kotlin/com/only4/cap4k/ddd/domain/id/IdPolicyAutoConfigurationTest.kt`

**Interfaces:**

- Consumes: DB comment `@IdStrategy=<token>`.
- Produces: `DbIdStrategy.DB_IDENTITY`, `DbIdStrategy.UUID7`, or `DbIdStrategy.SNOWFLAKE`.
- Must not produce: an alias, a legacy-name rejection branch, or a `snowflake-long` test fixture.

- [ ] **Step 1: Add the failing canonical-token parser test.**

Add these methods to `DbColumnAnnotationParserTest`:

```kotlin
@Test
fun `parses canonical snowflake id strategy`() {
    val metadata = DbColumnAnnotationParser.parse("primary key @IdStrategy=snowflake;")

    assertEquals(DbIdStrategy.SNOWFLAKE, metadata.idStrategy)
    assertEquals("primary key", metadata.cleanedComment)
}

@Test
fun `unsupported annotation diagnostics list canonical id strategies`() {
    val error = assertThrows(IllegalArgumentException::class.java) {
        DbColumnAnnotationParser.parse("@Unknown=value;")
    }

    assertTrue(error.message!!.contains("@IdStrategy=db_identity|uuid7|snowflake"))
}
```

- [ ] **Step 2: Run the focused RED test.**

```powershell
.\gradlew.bat :cap4k-plugin-pipeline-source-db:test --tests "com.only4.cap4k.plugin.pipeline.source.db.DbColumnAnnotationParserTest" --no-daemon
```

Expected: FAIL because `DbIdStrategy.SNOWFLAKE` does not exist and the parser rejects `snowflake`.

- [ ] **Step 3: Add the enum constant and canonical parser branch.**

Replace the enum with:

```kotlin
enum class DbIdStrategy {
    DB_IDENTITY,
    UUID7,
    SNOWFLAKE,
}
```

Change the parser diagnostic and strategy branch to:

```kotlin
"@ParentRef, @Type, @RefAggregate, @RefId, @IdStrategy=db_identity|uuid7|snowflake, " +
    "@Managed=system|scope|deleted|version, @Inherited."
```

```kotlin
return when (rawValue.trim().lowercase()) {
    "db_identity" -> DbIdStrategy.DB_IDENTITY
    "uuid7" -> DbIdStrategy.UUID7
    "snowflake" -> DbIdStrategy.SNOWFLAKE
    else -> throw IllegalArgumentException("unsupported @IdStrategy value: $rawValue")
}
```

- [ ] **Step 4: Delete the active test whose only purpose is the old name.**

Delete `snowflake long legacy name is not registered` from `IdPolicyAutoConfigurationTest`. Do not replace it with a dynamically assembled legacy token. Existing tests for the canonical `BuiltInIdentifierStrategies.SNOWFLAKE` remain.

- [ ] **Step 5: Run GREEN tests and the active vocabulary scan.**

```powershell
.\gradlew.bat :cap4k-plugin-pipeline-api:test :cap4k-plugin-pipeline-source-db:test --no-daemon
.\gradlew.bat :cap4k-ddd-starter:test --tests "com.only4.cap4k.ddd.domain.id.IdPolicyAutoConfigurationTest" --no-daemon
rg -n 'snowflake-long|SNOWFLAKE_LONG' cap4k-plugin-pipeline-api/src cap4k-plugin-pipeline-source-db/src cap4k-ddd-starter/src
```

Expected: tests PASS; scan has no matches. Historical docs are outside this task.

- [ ] **Step 6: Commit Task 1 only.**

```powershell
git add cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModels.kt cap4k-plugin-pipeline-source-db/src/main/kotlin/com/only4/cap4k/plugin/pipeline/source/db/DbColumnAnnotationParser.kt cap4k-plugin-pipeline-source-db/src/test/kotlin/com/only4/cap4k/plugin/pipeline/source/db/DbColumnAnnotationParserTest.kt cap4k-ddd-starter/src/test/kotlin/com/only4/cap4k/ddd/domain/id/IdPolicyAutoConfigurationTest.kt
git commit -m "feat: normalize snowflake id strategy vocabulary"
```

## Task 2: Retain JDBC Type And Capacity Evidence

**Depends on:** Task 1

**Files:**

- Modify: `cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModels.kt`
- Modify: `cap4k-plugin-pipeline-source-db/src/main/kotlin/com/only4/cap4k/plugin/pipeline/source/db/DbSchemaSourceProvider.kt`
- Modify: `cap4k-plugin-pipeline-source-db/src/test/kotlin/com/only4/cap4k/plugin/pipeline/source/db/DbSchemaSourceProviderTest.kt`

**Interfaces:**

- Consumes: JDBC `DATA_TYPE`, `TYPE_NAME`, and `COLUMN_SIZE`.
- Produces: nullable/defaulted `DbColumnSnapshot.jdbcType` and `columnSize`.
- Preserves: positional/source construction of existing snapshots through trailing defaults.

- [ ] **Step 1: Add an H2 metadata evidence test.**

Add the required imports:

```kotlin
import java.sql.Types
```

Add this complete test to `DbSchemaSourceProviderTest`:

```kotlin
@Test
fun `collect retains jdbc type name and column capacity`() {
    val url = "jdbc:h2:mem:cap4k-strong-id-metadata;DB_CLOSE_DELAY=-1"
    DriverManager.getConnection(url, "sa", "").use { connection ->
        connection.createStatement().use { statement ->
            statement.execute(
                """
                create table strong_id_evidence (
                    uuid_text varchar(36) not null,
                    uuid_native uuid not null,
                    snowflake_text varchar(19) not null,
                    snowflake_long bigint not null,
                    primary key (uuid_text)
                )
                """.trimIndent()
            )
        }
    }

    val snapshot = DbSchemaSourceProvider().collect(
        ProjectConfig(
            basePackage = "com.acme.demo",
            layout = ProjectLayout.MULTI_MODULE,
            modules = emptyMap(),
            sources = mapOf(
                "db" to SourceConfig(
                    options = mapOf(
                        "url" to url,
                        "username" to "sa",
                        "password" to "",
                        "schema" to "PUBLIC",
                        "includeTables" to listOf("strong_id_evidence"),
                        "excludeTables" to emptyList<String>(),
                    )
                )
            ),
            generators = emptyMap(),
            templates = TemplateConfig("ddd-default", emptyList(), ConflictPolicy.SKIP),
        )
    ) as DbSchemaSnapshot

    val columns = snapshot.tables.single().columns.associateBy { it.name.lowercase() }
    assertEquals(Types.CHAR == columns.getValue("uuid_text").jdbcType || Types.VARCHAR == columns.getValue("uuid_text").jdbcType, true)
    assertEquals(36, columns.getValue("uuid_text").columnSize)
    assertEquals("UUID", columns.getValue("uuid_native").dbType.uppercase())
    assertTrue(columns.getValue("uuid_native").jdbcType in setOf(Types.OTHER, Types.BINARY))
    assertEquals(19, columns.getValue("snowflake_text").columnSize)
    assertEquals(Types.BIGINT, columns.getValue("snowflake_long").jdbcType)
}
```

- [ ] **Step 2: Run the focused RED test.**

```powershell
.\gradlew.bat :cap4k-plugin-pipeline-source-db:test --tests "com.only4.cap4k.plugin.pipeline.source.db.DbSchemaSourceProviderTest.collect retains jdbc type name and column capacity" --no-daemon
```

Expected: test compilation FAILS because the snapshot properties are absent.

- [ ] **Step 3: Append source-compatible snapshot fields.**

Append these parameters after `inherited` in `DbColumnSnapshot`:

```kotlin
val jdbcType: Int? = null,
val columnSize: Int? = null,
```

Do not add these fields to `FieldModel` or `StrongIdModel`.

- [ ] **Step 4: Capture the JDBC values without inventing zero.**

Inside the `getColumns` loop, read the values before constructing the snapshot:

```kotlin
val jdbcType = rows.getInt("DATA_TYPE").let { value ->
    if (rows.wasNull()) null else value
}
val columnSize = rows.getInt("COLUMN_SIZE").let { value ->
    if (rows.wasNull()) null else value
}
```

Then use the retained value for mapping and append both fields:

```kotlin
kotlinType = jdbcType?.let { JdbcTypeMapper.toKotlinType(it, typeName) }
    ?: error("missing DATA_TYPE for $tableName.$name"),
// existing snapshot arguments remain unchanged
jdbcType = jdbcType,
columnSize = columnSize,
```

- [ ] **Step 5: Run GREEN tests.**

```powershell
.\gradlew.bat :cap4k-plugin-pipeline-api:test :cap4k-plugin-pipeline-source-db:test --no-daemon
```

Expected: PASS. A null field is tolerated only while snapshots are transported; Task 3 rejects missing evidence for application-side Strong IDs.

- [ ] **Step 6: Commit Task 2 only.**

```powershell
git add cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModels.kt cap4k-plugin-pipeline-source-db/src/main/kotlin/com/only4/cap4k/plugin/pipeline/source/db/DbSchemaSourceProvider.kt cap4k-plugin-pipeline-source-db/src/test/kotlin/com/only4/cap4k/plugin/pipeline/source/db/DbSchemaSourceProviderTest.kt
git commit -m "feat: retain jdbc strong id metadata"
```

## Task 3: Implement The Strict Storage-Nearest Backing Resolver

**Depends on:** Task 2

**Files:**

- Create: `cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/AggregateStrongIdBackingResolver.kt`
- Create: `cap4k-plugin-pipeline-core/src/test/kotlin/com/only4/cap4k/plugin/pipeline/core/AggregateStrongIdBackingResolverTest.kt`
- Modify: `cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/AggregatePersistenceFieldBehaviorInference.kt`
- Modify: `cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/AggregateSpecialFieldPolicyResolver.kt`

**Interfaces:**

- Consumes: `DbColumnSnapshot.idStrategy`, `jdbcType`, `dbType`, `kotlinType`, `columnSize`.
- Produces: `ResolvedStrongIdBacking(valueType, columnLength)` and compile-safe canonical Snowflake propagation through existing exhaustive consumers.
- Rejects: missing evidence, insufficient character capacity, cross-storage mapping, unsupported numeric types, and false native-UUID evidence.

- [ ] **Step 1: Create the failing matrix test.**

Create `AggregateStrongIdBackingResolverTest.kt` with this complete content:

```kotlin
package com.only4.cap4k.plugin.pipeline.core

import com.only4.cap4k.plugin.pipeline.api.DbColumnSnapshot
import com.only4.cap4k.plugin.pipeline.api.DbIdStrategy
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.sql.Types

class AggregateStrongIdBackingResolverTest {
    @Test
    fun `resolves the four supported storage nearest combinations`() {
        assertEquals(
            ResolvedStrongIdBacking("String", 36),
            resolve(DbIdStrategy.UUID7, Types.VARCHAR, "VARCHAR", "String", 36),
        )
        assertEquals(
            ResolvedStrongIdBacking("UUID", null),
            resolve(DbIdStrategy.UUID7, Types.OTHER, "UUID", "java.util.UUID", 16),
        )
        assertEquals(
            ResolvedStrongIdBacking("String", 19),
            resolve(DbIdStrategy.SNOWFLAKE, Types.VARCHAR, "VARCHAR", "String", 19),
        )
        assertEquals(
            ResolvedStrongIdBacking("Long", null),
            resolve(DbIdStrategy.SNOWFLAKE, Types.BIGINT, "BIGINT", "Long", 64),
        )
    }

    @Test
    fun `rejects missing capacity for character storage`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            resolve(DbIdStrategy.UUID7, Types.VARCHAR, "VARCHAR", "String", null)
        }
        assertTrue(error.message!!.contains("columnSize"))
    }

    @Test
    fun `rejects undersized character storage`() {
        assertThrows(IllegalArgumentException::class.java) {
            resolve(DbIdStrategy.UUID7, Types.VARCHAR, "VARCHAR", "String", 35)
        }
        assertThrows(IllegalArgumentException::class.java) {
            resolve(DbIdStrategy.SNOWFLAKE, Types.VARCHAR, "VARCHAR", "String", 18)
        }
    }

    @Test
    fun `rejects crossed and guessed storage`() {
        val unsupported = listOf(
            UnsupportedCase(DbIdStrategy.UUID7, Types.BIGINT, "BIGINT", "Long", 64),
            UnsupportedCase(DbIdStrategy.UUID7, Types.OTHER, "jsonb", "String", 36),
            UnsupportedCase(DbIdStrategy.SNOWFLAKE, Types.INTEGER, "INTEGER", "Int", 32),
            UnsupportedCase(DbIdStrategy.SNOWFLAKE, Types.NUMERIC, "NUMERIC", "java.math.BigDecimal", 19),
            UnsupportedCase(DbIdStrategy.SNOWFLAKE, Types.OTHER, "UUID", "java.util.UUID", 16),
        )

        unsupported.forEach { case ->
            assertThrows(IllegalArgumentException::class.java) {
                resolve(
                    strategy = case.strategy,
                    jdbcType = case.jdbcType,
                    dbType = case.dbType,
                    kotlinType = case.kotlinType,
                    columnSize = case.columnSize,
                )
            }
        }
    }

    private data class UnsupportedCase(
        val strategy: DbIdStrategy,
        val jdbcType: Int,
        val dbType: String,
        val kotlinType: String,
        val columnSize: Int,
    )

    private fun resolve(
        strategy: DbIdStrategy,
        jdbcType: Int?,
        dbType: String,
        kotlinType: String,
        columnSize: Int?,
    ): ResolvedStrongIdBacking = AggregateStrongIdBackingResolver.resolve(
        tableName = "orders",
        column = DbColumnSnapshot(
            name = "id",
            dbType = dbType,
            kotlinType = kotlinType,
            nullable = false,
            isPrimaryKey = true,
            idStrategy = strategy,
            jdbcType = jdbcType,
            columnSize = columnSize,
        ),
    )
}
```

- [ ] **Step 2: Run the focused RED test.**

```powershell
.\gradlew.bat :cap4k-plugin-pipeline-core:test --tests "com.only4.cap4k.plugin.pipeline.core.AggregateStrongIdBackingResolverTest" --no-daemon
```

Expected: on the first run after Task 1, production compilation FAILS because existing exhaustive `DbIdStrategy` consumers do not yet cover `SNOWFLAKE`. After Step 3, rerun and confirm compilation advances to the intended test failure because the resolver and result do not exist.

- [ ] **Step 3: Propagate the canonical enum through existing exhaustive consumers.**

Make only these final-semantics branches:

```kotlin
// AggregatePersistenceFieldBehaviorInference.toPersistenceStrategy
DbIdStrategy.UUID7, DbIdStrategy.SNOWFLAKE -> null
```

```kotlin
// AggregateSpecialFieldPolicyResolver.resolvePolicy
DbIdStrategy.SNOWFLAKE -> "snowflake"
```

```kotlin
// AggregateSpecialFieldPolicyResolver.validateExplicitIdStrategyType
DbIdStrategy.SNOWFLAKE -> Unit
```

Do not add `else`. Snowflake is application-side, so it has no provider `generatedValueStrategy`; strict backing validation belongs to the new resolver and its Task 4 integration. Rerun the RED command and confirm the remaining failure is now the missing resolver/result.

- [ ] **Step 4: Create the strict resolver.**

Create `AggregateStrongIdBackingResolver.kt` with this complete content:

```kotlin
package com.only4.cap4k.plugin.pipeline.core

import com.only4.cap4k.plugin.pipeline.api.DbColumnSnapshot
import com.only4.cap4k.plugin.pipeline.api.DbIdStrategy
import java.sql.Types
import java.util.Locale

internal data class ResolvedStrongIdBacking(
    val valueType: String,
    val columnLength: Int?,
)

internal object AggregateStrongIdBackingResolver {
    private val characterJdbcTypes = setOf(
        Types.CHAR,
        Types.VARCHAR,
        Types.LONGVARCHAR,
        Types.NCHAR,
        Types.NVARCHAR,
        Types.LONGNVARCHAR,
    )

    fun resolve(tableName: String, column: DbColumnSnapshot): ResolvedStrongIdBacking {
        val strategy = requireNotNull(column.idStrategy) {
            "missing application-side ID strategy for $tableName.${column.name}"
        }
        val jdbcType = requireNotNull(column.jdbcType) {
            "missing jdbcType for application-side ID $tableName.${column.name}"
        }
        val path = "$tableName.${column.name}"

        return when (strategy) {
            DbIdStrategy.UUID7 -> resolveUuid7(path, column, jdbcType)
            DbIdStrategy.SNOWFLAKE -> resolveSnowflake(path, column, jdbcType)
            DbIdStrategy.DB_IDENTITY -> error("database identity $path does not have an application-side Strong ID backing")
        }
    }

    private fun resolveUuid7(
        path: String,
        column: DbColumnSnapshot,
        jdbcType: Int,
    ): ResolvedStrongIdBacking = when {
        jdbcType in characterJdbcTypes -> {
            require(column.kotlinType == "String" || column.kotlinType == "kotlin.String") {
                "uuid7 character storage $path must map to String, got ${column.kotlinType}"
            }
            val size = requireNotNull(column.columnSize) {
                "uuid7 character storage $path requires columnSize"
            }
            require(size >= 36) { "uuid7 character storage $path requires capacity >= 36, got $size" }
            ResolvedStrongIdBacking("String", size)
        }
        isNativeUuid(column, jdbcType) -> {
            require(column.kotlinType == "UUID" || column.kotlinType == "java.util.UUID") {
                "native UUID storage $path must map to UUID, got ${column.kotlinType}"
            }
            ResolvedStrongIdBacking("UUID", null)
        }
        else -> unsupported(path, column)
    }

    private fun resolveSnowflake(
        path: String,
        column: DbColumnSnapshot,
        jdbcType: Int,
    ): ResolvedStrongIdBacking = when {
        jdbcType in characterJdbcTypes -> {
            require(column.kotlinType == "String" || column.kotlinType == "kotlin.String") {
                "snowflake character storage $path must map to String, got ${column.kotlinType}"
            }
            val size = requireNotNull(column.columnSize) {
                "snowflake character storage $path requires columnSize"
            }
            require(size >= 19) { "snowflake character storage $path requires capacity >= 19, got $size" }
            ResolvedStrongIdBacking("String", size)
        }
        jdbcType == Types.BIGINT -> {
            require(column.kotlinType == "Long" || column.kotlinType == "kotlin.Long") {
                "snowflake BIGINT storage $path must map to Long, got ${column.kotlinType}"
            }
            ResolvedStrongIdBacking("Long", null)
        }
        else -> unsupported(path, column)
    }

    private fun isNativeUuid(column: DbColumnSnapshot, jdbcType: Int): Boolean =
        jdbcType in setOf(Types.OTHER, Types.BINARY) &&
            column.dbType.trim().lowercase(Locale.ROOT) == "uuid"

    private fun unsupported(path: String, column: DbColumnSnapshot): Nothing =
        throw IllegalArgumentException(
            "unsupported ${column.idStrategy} storage for $path: " +
                "jdbcType=${column.jdbcType}, dbType=${column.dbType}, " +
                "kotlinType=${column.kotlinType}, columnSize=${column.columnSize}"
        )
}
```

- [ ] **Step 5: Run GREEN tests.**

```powershell
.\gradlew.bat :cap4k-plugin-pipeline-core:test --tests "com.only4.cap4k.plugin.pipeline.core.AggregateStrongIdBackingResolverTest" --no-daemon
```

Expected: PASS. If H2 reports a native UUID JDBC type outside `OTHER/BINARY`, stop and record the exact metadata; do not broaden the resolver without revising the matrix.

- [ ] **Step 6: Commit Task 3 only.**

```powershell
git add docs/superpowers/plans/2026-07-24-cap4k-strong-id-create-time-injection.md cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/AggregateStrongIdBackingResolver.kt cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/AggregatePersistenceFieldBehaviorInference.kt cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/AggregateSpecialFieldPolicyResolver.kt cap4k-plugin-pipeline-core/src/test/kotlin/com/only4/cap4k/plugin/pipeline/core/AggregateStrongIdBackingResolverTest.kt
git commit -m "feat: resolve storage nearest strong id backing"
```

## Task 4: Integrate Backing Resolution Into Canonical Assembly

**Depends on:** Task 3

**Files:**

- Modify: `cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/AggregateIdPolicyResolver.kt`
- Modify: `cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/AggregateSpecialFieldPolicyResolver.kt`
- Modify: `cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssembler.kt`
- Modify: `cap4k-plugin-pipeline-core/src/test/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssemblerTest.kt`
- Modify: `cap4k-plugin-pipeline-core/src/test/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultPipelineRunnerTest.kt`

**Interfaces:**

- Consumes: strict backing result from Task 3 and canonical entity ownership.
- Produces: own `StrongIdModel` with `valueType`, canonical `idStrategy`, owner fields, and `isEmbeddedId=true`.
- Preserves: standalone `@RefId` UUID7 String semantics and database-identity primitives.

- [ ] **Step 1: Add canonical matrix tests.**

Add four tests using the existing `table`, `column`, and `assemble` test helpers. Each test must assert the entire tuple, not only the generated type name:

```kotlin
private fun assertOwnStrongId(
    model: CanonicalModel,
    expectedValueType: String,
    expectedStrategy: String,
) {
    val strongId = model.strongIds.single { it.kind == StrongIdKind.OWN_ID }
    assertEquals("OrderId", strongId.typeName)
    assertEquals(expectedValueType, strongId.valueType)
    assertEquals(expectedStrategy, strongId.idStrategy)
    assertEquals("Order", strongId.ownerEntityName)
    assertEquals("Order", strongId.ownerAggregateName)
    assertTrue(strongId.isEmbeddedId)
}
```

Use these exact evidence rows across the four tests:

```kotlin
DbColumnSnapshot("id", "VARCHAR", "String", false, isPrimaryKey = true, idStrategy = DbIdStrategy.UUID7, jdbcType = Types.VARCHAR, columnSize = 36)
DbColumnSnapshot("id", "UUID", "java.util.UUID", false, isPrimaryKey = true, idStrategy = DbIdStrategy.UUID7, jdbcType = Types.OTHER, columnSize = 16)
DbColumnSnapshot("id", "VARCHAR", "String", false, isPrimaryKey = true, idStrategy = DbIdStrategy.SNOWFLAKE, jdbcType = Types.VARCHAR, columnSize = 19)
DbColumnSnapshot("id", "BIGINT", "Long", false, isPrimaryKey = true, idStrategy = DbIdStrategy.SNOWFLAKE, jdbcType = Types.BIGINT, columnSize = 64)
```

Also add rejection tests for missing JDBC evidence and a reference column whose physical storage does not match its aggregate root Strong ID backing.

- [ ] **Step 2: Run the focused RED tests.**

```powershell
.\gradlew.bat :cap4k-plugin-pipeline-core:test --tests "com.only4.cap4k.plugin.pipeline.core.DefaultCanonicalAssemblerTest" --no-daemon
```

Expected: UUID7/String may pass; UUID native and both Snowflake cases FAIL, and current policy still emits `snowflake-long`.

- [ ] **Step 3: Replace primitive application-side policy validation.**

In `AggregateIdPolicyResolver`, replace the constants and application-side branch with:

```kotlin
private const val UUID7 = "uuid7"
private const val SNOWFLAKE = "snowflake"
private const val IDENTITY = "identity"
private const val DATABASE_IDENTITY = "database-identity"
```

```kotlin
fun resolveKind(strategy: String): AggregateIdPolicyKind =
    when (normalizeStrategy(strategy)) {
        IDENTITY -> AggregateIdPolicyKind.DATABASE_SIDE
        UUID7, SNOWFLAKE -> AggregateIdPolicyKind.APPLICATION_SIDE
        else -> throw IllegalArgumentException("unknown ID strategy: ${normalizeStrategy(strategy)}")
    }
```

Retain primitive type validation only for database identity:

```kotlin
fun validateType(config: ProjectConfig, entity: EntityModel, strategy: String) {
    val normalizedStrategy = normalizeStrategy(strategy)
    if (normalizedStrategy != IDENTITY) return
    require(entity.idField.type in DatabaseIdentityTypes) {
        "ID strategy $normalizedStrategy cannot be applied to aggregate ${entityKey(config, entity)} " +
            "id field ${entity.idField.name}: generated ID type is ${entity.idField.type}"
    }
}
```

In `AggregateSpecialFieldPolicyResolver`, update the missing-strategy diagnostic to list `snowflake`, and remove primitive application-side validation. Its final branch is:

```kotlin
when (idColumn.idStrategy) {
    DbIdStrategy.DB_IDENTITY -> AggregateIdPolicyResolver.validateType(
        config = config,
        entity = entity,
        strategy = strategy,
    )
    DbIdStrategy.UUID7, DbIdStrategy.SNOWFLAKE, null -> Unit
}
```

Replace every active `snowflake-long` fixture token in `DefaultCanonicalAssemblerTest` and `DefaultPipelineRunnerTest` with `snowflake`. Do not retain a negative compatibility fixture.

- [ ] **Step 4: Centralize own-ID eligibility and backing in the assembler.**

Add this helper inside `DefaultCanonicalAssembler`. Resolve every supported table once into a table-name keyed catalog; use that same catalog for entity field projection, aggregate-reference validation metadata, and `buildStrongIds`:

```kotlin
private data class GeneratedOwnStrongId(
    val typeName: String,
    val strategy: String,
    val backing: ResolvedStrongIdBacking,
)

private fun generatedOwnStrongId(table: DbTableSnapshot): GeneratedOwnStrongId? {
    val primaryKeyColumn = table.primaryKey.singleOrNull() ?: return null
    val idColumn = table.columns.firstOrNull { it.name.equals(primaryKeyColumn, ignoreCase = true) }
        ?: return null
    val strategy = when (idColumn.idStrategy) {
        DbIdStrategy.UUID7 -> "uuid7"
        DbIdStrategy.SNOWFLAKE -> "snowflake"
        DbIdStrategy.DB_IDENTITY, null -> return null
    }
    require(idColumn.refAggregate.isNullOrBlank() && idColumn.refId.isNullOrBlank()) {
        "primary key ${table.tableName}.${idColumn.name} cannot also be @RefAggregate or @RefId"
    }
    return GeneratedOwnStrongId(
        typeName = ownStrongIdTypeName(AggregateNaming.entityName(table.tableName)),
        strategy = strategy,
        backing = AggregateStrongIdBackingResolver.resolve(table.tableName, idColumn),
    )
}
```

Build the catalog once before projecting entity fields:

```kotlin
val generatedOwnStrongIdsByTableName = supportedTables
    .mapNotNull { table ->
        generatedOwnStrongId(table)?.let { strongId ->
            table.tableName.lowercase(Locale.ROOT) to strongId
        }
    }
    .toMap()
```

Replace the own-ID `StrongIdModel` construction with:

```kotlin
val resolved = generatedOwnStrongIdsByTableName[
    table.tableName.lowercase(Locale.ROOT)
] ?: return@mapNotNull null
val ownerAggregate = aggregateRootEntityOrSelf(entity, entities)
StrongIdModel(
    typeName = entity.idField.type,
    packageName = entity.packageName,
    valueType = resolved.backing.valueType,
    kind = StrongIdKind.OWN_ID,
    ownerEntityName = entity.name,
    ownerEntityPackageName = entity.packageName,
    ownerAggregateName = ownerAggregate.name,
    ownerAggregatePackageName = ownerAggregate.packageName,
    idStrategy = resolved.strategy,
    canGenerateNew = true,
    isEmbeddedId = true,
)
```

`canGenerateNew` remains temporarily in this task so the generator stays compiling; Task 7 deletes it after selection moves to explicit eligibility.

- [ ] **Step 5: Enforce direct aggregate-reference storage compatibility.**

When `@RefAggregate` resolves a target own Strong ID, compare the reference column with the target backing by resolving the same strategy against the reference column. Require equality of `valueType`; for String also require sufficient capacity. Use this error shape:

```kotlin
require(referenceBacking.valueType == targetStrongId.valueType) {
    "aggregate reference ${table.tableName}.${column.name} storage ${referenceBacking.valueType} " +
        "does not match ${targetStrongId.typeName} backing ${targetStrongId.valueType}"
}
```

Do not add a converter or cross-mapping flag.

- [ ] **Step 6: Run GREEN tests and scan active core vocabulary.**

```powershell
.\gradlew.bat :cap4k-plugin-pipeline-core:test --no-daemon
rg -n 'snowflake-long|SNOWFLAKE_LONG' cap4k-plugin-pipeline-core/src
```

Expected: tests PASS; scan has no matches.

- [ ] **Step 7: Commit Task 4 only.**

```powershell
git add cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/AggregateIdPolicyResolver.kt cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/AggregateSpecialFieldPolicyResolver.kt cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssembler.kt cap4k-plugin-pipeline-core/src/test/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssemblerTest.kt cap4k-plugin-pipeline-core/src/test/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultPipelineRunnerTest.kt docs/superpowers/plans/2026-07-24-cap4k-strong-id-create-time-injection.md
git commit -m "feat: assemble storage nearest strong ids"
```

## Task 5: Project Character Column Length Into JPA Metadata

**Depends on:** Task 4

**Files:**

- Modify: `cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModels.kt`
- Modify: `cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/AggregateJpaControlInference.kt`
- Modify: `cap4k-plugin-pipeline-core/src/test/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultPipelineRunnerTest.kt`
- Modify: `docs/superpowers/plans/2026-07-24-cap4k-strong-id-create-time-injection.md` (source-evidence correction only)

**Interfaces:**

- Consumes: `DbColumnSnapshot.jdbcType` and `columnSize` before the domain field type is replaced by a generated Strong ID.
- Produces: semantic `AggregateColumnJpaModel.columnLength` for physical character columns only.
- Must not expose: raw JDBC type codes in generator context.

- [ ] **Step 1: Add a failing projection assertion.**

Extend the existing pipeline fixture with one UUID7 `VARCHAR(40)` ID and one native UUID ID with `columnSize=16`. `PipelineResult` does not expose the canonical model, so capture the `CanonicalModel` received by the fixture's aggregate `GeneratorProvider`, then assert:

```kotlin
val aggregateEntityJpa = requireNotNull(capturedModel).aggregateEntityJpa
val textId = aggregateEntityJpa
    .single { it.entityName == "VideoPost" }
    .columns.single { it.isId }
val nativeId = aggregateEntityJpa
    .single { it.entityName == "AuditRecord" }
    .columns.single { it.isId }

assertEquals(40, textId.columnLength)
assertEquals(null, nativeId.columnLength)
```

- [ ] **Step 2: Run the focused RED test.**

```powershell
.\gradlew.bat :cap4k-plugin-pipeline-core:test --tests "com.only4.cap4k.plugin.pipeline.core.DefaultPipelineRunnerTest" --no-daemon
```

Expected: test compilation FAILS because `columnLength` is absent.

- [ ] **Step 3: Add the semantic field and project it.**

Append the defaulted field:

```kotlin
data class AggregateColumnJpaModel(
    val fieldName: String,
    val columnName: String,
    val isId: Boolean,
    val converterTypeFqn: String? = null,
    val converterClassFqn: String? = converterTypeFqn?.let { "$it.Converter" },
    val columnLength: Int? = null,
)
```

Import `java.sql.Types`, add the physical character-type set to `AggregateJpaControlInference`, and append the projection argument:

```kotlin
private val characterJdbcTypes = setOf(
    Types.CHAR,
    Types.VARCHAR,
    Types.LONGVARCHAR,
    Types.NCHAR,
    Types.NVARCHAR,
    Types.LONGNVARCHAR,
)

// Inside AggregateColumnJpaModel construction:
columnLength = column.columnSize.takeIf { column.jdbcType in characterJdbcTypes },
```

Do not test `field.type` here: after canonical assembly it may already be `VideoPostId`, so it no longer reveals the physical backing. This projection carries database capacity, not a fallback. Task 9 emits it only when the resolved Strong ID backing is `String`.

- [ ] **Step 4: Run GREEN tests.**

```powershell
.\gradlew.bat :cap4k-plugin-pipeline-api:test :cap4k-plugin-pipeline-core:test --no-daemon
```

Expected: PASS.

- [ ] **Step 5: Commit Task 5 only.**

```powershell
git add cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModels.kt cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/AggregateJpaControlInference.kt cap4k-plugin-pipeline-core/src/test/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultPipelineRunnerTest.kt docs/superpowers/plans/2026-07-24-cap4k-strong-id-create-time-injection.md
git commit -m "feat: project strong id column length"
```

## Task 6: Generalize The Strong ID Value Contract

**Depends on:** Task 5

**Files:**

- Modify: `ddd-core/build.gradle.kts`
- Modify: `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/id/StrongId.kt`
- Modify: `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/id/StrongIds.kt`
- Replace tests in: `ddd-core/src/test/kotlin/com/only4/cap4k/ddd/core/domain/id/StrongIdsTest.kt`
- Mechanically update raw implementors in: `ddd-domain-repo-jpa/src/test/kotlin/com/only4/cap4k/ddd/application/JpaUnitOfWorkTest.kt`
- Mechanically update raw implementors in: `cap4k-ddd-starter/src/test/kotlin/com/only4/cap4k/ddd/runtime/StrongIdJacksonRuntimeTest.kt`
- Mechanically update raw implementors in: `cap4k-ddd-starter/src/test/kotlin/com/only4/cap4k/ddd/runtime/strongid/StrongIdJpaRuntimeTest.kt`
- Modify: `docs/superpowers/plans/2026-07-24-cap4k-strong-id-create-time-injection.md` (source-evidence correction only)

**Interfaces:**

- Consumes: already allocated String, UUID, or Long values.
- Produces: validated/canonical backing values.
- Must not produce: new identifiers or infrastructure calls.

- [ ] **Step 1: Replace allocation-oriented tests with the validation matrix.**

Replace `StrongIdsTest.kt` with:

```kotlin
package com.only4.cap4k.ddd.core.domain.id

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

class StrongIdsTest {
    private val uuid7Text = "019c0000-0000-7000-8000-000000000001"

    @Test
    fun `uuid7 accepts canonical String and UUID backings`() {
        val uuid = UUID.fromString(uuid7Text)

        assertEquals(uuid7Text, StrongIds.requireUuidV7(uuid7Text, "OrderId"))
        assertEquals(uuid, StrongIds.requireUuidV7(uuid, "OrderId"))
    }

    @Test
    fun `uuid7 rejects non canonical or non v7 values`() {
        listOf(
            "",
            " $uuid7Text",
            uuid7Text.uppercase(),
            "00000000-0000-0000-0000-000000000000",
            "019c0000-0000-6000-8000-000000000001",
            "019c0000-0000-7000-0000-000000000001",
            "not-a-uuid",
        ).forEach { value ->
            val error = assertThrows(IllegalArgumentException::class.java) {
                StrongIds.requireUuidV7(value, "OrderId")
            }
            assertTrue(error.message!!.contains("OrderId must be a UUIDv7 value"))
        }
    }

    @Test
    fun `uuid7 UUID overload rejects wrong version and variant`() {
        listOf(
            UUID(0L, 0L),
            UUID.fromString("019c0000-0000-6000-8000-000000000001"),
            UUID.fromString("019c0000-0000-7000-0000-000000000001"),
        ).forEach { value ->
            assertThrows(IllegalArgumentException::class.java) {
                StrongIds.requireUuidV7(value, "OrderId")
            }
        }
    }

    @Test
    fun `snowflake accepts canonical String and Long backings`() {
        assertEquals("1", StrongIds.requireSnowflake("1", "OrderId"))
        assertEquals("9223372036854775807", StrongIds.requireSnowflake("9223372036854775807", "OrderId"))
        assertEquals(1L, StrongIds.requireSnowflake(1L, "OrderId"))
        assertEquals(Long.MAX_VALUE, StrongIds.requireSnowflake(Long.MAX_VALUE, "OrderId"))
    }

    @Test
    fun `snowflake String rejects non canonical and overflowing values`() {
        listOf(
            "",
            "0",
            "-1",
            "+1",
            "01",
            " 1",
            "1 ",
            "1.0",
            "9223372036854775808",
            "12345678901234567890",
        ).forEach { value ->
            val error = assertThrows(IllegalArgumentException::class.java) {
                StrongIds.requireSnowflake(value, "OrderId")
            }
            assertTrue(error.message!!.contains("OrderId must be a positive canonical Snowflake value"))
        }
    }

    @Test
    fun `snowflake Long rejects zero and negative values`() {
        listOf(0L, -1L, Long.MIN_VALUE).forEach { value ->
            assertThrows(IllegalArgumentException::class.java) {
                StrongIds.requireSnowflake(value, "OrderId")
            }
        }
    }
}
```

- [ ] **Step 2: Run the focused RED test.**

```powershell
.\gradlew.bat :ddd-core:test --tests "com.only4.cap4k.ddd.core.domain.id.StrongIdsTest" --no-daemon
```

Expected: test compilation FAILS because UUID and Snowflake overloads are absent.

- [ ] **Step 3: Generalize `StrongId` and replace `StrongIds` with validation-only code.**

Replace `StrongId.kt` with:

```kotlin
package com.only4.cap4k.ddd.core.domain.id

interface StrongId<out V : Any> {
    val value: V
}
```

Replace `StrongIds.kt` with:

```kotlin
package com.only4.cap4k.ddd.core.domain.id

import java.util.UUID

object StrongIds {
    private val canonicalSnowflake = Regex("^[1-9][0-9]{0,18}$")

    fun requireUuidV7(value: String, typeName: String): String {
        val uuid = runCatching { UUID.fromString(value) }.getOrNull()
        require(
            value == value.trim() &&
                value == value.lowercase() &&
                uuid != null &&
                uuid.toString() == value
        ) { "$typeName must be a UUIDv7 value: $value" }
        requireUuidV7(uuid, typeName)
        return value
    }

    fun requireUuidV7(value: UUID, typeName: String): UUID {
        require(value != UUID(0L, 0L) && value.version() == 7 && value.variant() == 2) {
            "$typeName must be a UUIDv7 value: $value"
        }
        return value
    }

    fun requireSnowflake(value: String, typeName: String): String {
        require(canonicalSnowflake.matches(value) && value.toLongOrNull()?.let { it > 0L } == true) {
            "$typeName must be a positive canonical Snowflake value: $value"
        }
        return value
    }

    fun requireSnowflake(value: Long, typeName: String): Long {
        require(value > 0L) {
            "$typeName must be a positive canonical Snowflake value: $value"
        }
        return value
    }
}
```

This intentionally deletes `UuidCreator` and `newUuidV7String()` from `StrongIds`. Remove the now-unused `uuid-creator` dependency from `ddd-core/build.gradle.kts`; do not touch the starter or JPA module dependencies because those modules still have real allocation implementations.

- [ ] **Step 4: Add explicit type arguments to existing test fixtures.**

Apply only these mechanical changes; allocation removal from fixtures belongs to Tasks 8, 9, and 16:

Apply these exact supertype-clause replacements:

```text
TestStrongEntityId: `: StrongId, Serializable` -> `: StrongId<String>, Serializable`
StrongContentId: `: StrongId, Serializable` -> `: StrongId<String>, Serializable`
StrongAuthorId: `: StrongId, Serializable` -> `: StrongId<String>, Serializable`
StrongMediaProcessingTaskId: `: StrongId, Serializable` -> `: StrongId<String>, Serializable`
StrongContentItemId: `: StrongId, Serializable` -> `: StrongId<String>, Serializable`
```

Do not use star projections in an implementor.

- [ ] **Step 5: Run core tests and compile dependent fixtures.**

```powershell
.\gradlew.bat :ddd-core:test :ddd-domain-repo-jpa:compileTestKotlin :cap4k-ddd-starter:compileTestKotlin --no-daemon
```

Expected: PASS only after any direct fixture calls to removed allocation are temporarily changed to fixed valid UUIDv7 literals. Do not add an allocation helper back to `StrongIds`.

- [ ] **Step 6: Scan and commit.**

```powershell
rg -n 'interface StrongId\s*\{|:\s*StrongId([, ]|$)|newUuidV7String' ddd-core/src ddd-domain-repo-jpa/src cap4k-ddd-starter/src
rg -n 'UuidCreator|uuid-creator' ddd-core
```

Expected: no raw implementors, no allocation helper, and no UUID allocation dependency in `ddd-core`. UUID allocation in starter and JPA modules remains in scope for their existing infrastructure strategies.

```powershell
git add ddd-core/build.gradle.kts ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/id/StrongId.kt ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/id/StrongIds.kt ddd-core/src/test/kotlin/com/only4/cap4k/ddd/core/domain/id/StrongIdsTest.kt ddd-domain-repo-jpa/src/test/kotlin/com/only4/cap4k/ddd/application/JpaUnitOfWorkTest.kt cap4k-ddd-starter/src/test/kotlin/com/only4/cap4k/ddd/runtime/StrongIdJacksonRuntimeTest.kt cap4k-ddd-starter/src/test/kotlin/com/only4/cap4k/ddd/runtime/strongid/StrongIdJpaRuntimeTest.kt docs/superpowers/plans/2026-07-24-cap4k-strong-id-create-time-injection.md
git commit -m "refactor: make strong ids generic validation values"
```

## Task 7: Make Strong ID Planning Backing-Driven

**Depends on:** Task 6

**Files:**

- Modify: `cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModels.kt`
- Modify: `cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssembler.kt`
- Modify: `cap4k-plugin-pipeline-core/src/test/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssemblerTest.kt`
- Modify: `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/StrongIdArtifactPlanner.kt`
- Modify: `cap4k-plugin-pipeline-generator-aggregate/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateArtifactPlannerTest.kt`
- Modify: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/strong_id.kt.peb` (remove the obsolete conditional allocation block only; Task 8 replaces the template)
- Modify: `cap4k-plugin-pipeline-renderer-pebble/src/test/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PebbleArtifactRendererTest.kt`
- Modify: `docs/superpowers/plans/2026-07-24-cap4k-strong-id-create-time-injection.md` (source-evidence correction only)

**Interfaces:**

- Consumes: canonical `StrongIdModel.valueType`, `kind`, and `idStrategy`.
- Produces: renderer context `valueType`, `validationKind`, `stringBacked`, `uuidBacked`, `longBacked`.
- Deletes: `StrongIdModel.canGenerateNew` and every context/assertion derived from it.

- [ ] **Step 1: Add failing planner assertions for all backing branches.**

For four own `StrongIdModel` inputs, assert these exact context tuples:

```kotlin
assertEquals("String", uuidText.context["valueType"])
assertEquals("UUID7", uuidText.context["validationKind"])
assertEquals(true, uuidText.context["stringBacked"])

assertEquals("UUID", uuidNative.context["valueType"])
assertEquals(true, uuidNative.context["uuidBacked"])

assertEquals("String", snowflakeText.context["valueType"])
assertEquals("SNOWFLAKE", snowflakeText.context["validationKind"])

assertEquals("Long", snowflakeLong.context["valueType"])
assertEquals(true, snowflakeLong.context["longBacked"])
```

Assert `canGenerateNew` is absent from every artifact context.

- [ ] **Step 2: Run the planner RED test.**

```powershell
.\gradlew.bat :cap4k-plugin-pipeline-generator-aggregate:test --tests "com.only4.cap4k.plugin.pipeline.generator.aggregate.AggregateArtifactPlannerTest" --no-daemon
```

Expected: FAIL because the planner still emits `canGenerateNew` and omits backing context.

- [ ] **Step 3: Delete `canGenerateNew` from the canonical model and all construction sites.**

The final model is:

```kotlin
data class StrongIdModel(
    val typeName: String,
    val packageName: String,
    val valueType: String = "String",
    val kind: StrongIdKind,
    val ownerEntityName: String? = null,
    val ownerEntityPackageName: String? = null,
    val ownerAggregateName: String? = null,
    val ownerAggregatePackageName: String? = null,
    val idStrategy: String? = null,
    val isEmbeddedId: Boolean = false,
)
```

Remove every named argument and assertion for `canGenerateNew`; do not replace it with another allocation boolean. The current Pebble template is also a consumer: remove its `{% if canGenerateNew %}` / `new()` block and the two old renderer-test context entries/assertions now. Task 8 replaces the remaining validation-only transitional template with the four final backing variants.

- [ ] **Step 4: Replace `StrongIdArtifactPlanner` with backing-driven context.**

Use this complete implementation:

```kotlin
package com.only4.cap4k.plugin.pipeline.generator.aggregate

import com.only4.cap4k.plugin.pipeline.api.ArtifactLayoutResolver
import com.only4.cap4k.plugin.pipeline.api.ArtifactPlanItem
import com.only4.cap4k.plugin.pipeline.api.CanonicalModel
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig

internal class StrongIdArtifactPlanner : AggregateArtifactFamilyPlanner {
    override fun plan(config: ProjectConfig, model: CanonicalModel): List<ArtifactPlanItem> {
        val artifactLayout = ArtifactLayoutResolver(config.basePackage, config.artifactLayout)
        return model.strongIds.map { strongId ->
            val validationKind = when (strongId.idStrategy) {
                "snowflake" -> "SNOWFLAKE"
                null, "uuid7" -> "UUID7"
                else -> error("unsupported Strong ID strategy ${strongId.idStrategy} for ${strongId.packageName}.${strongId.typeName}")
            }
            require(strongId.valueType in setOf("String", "UUID", "Long")) {
                "unsupported Strong ID backing ${strongId.valueType} for ${strongId.packageName}.${strongId.typeName}"
            }
            generatedKotlinArtifact(
                config = config,
                artifactLayout = artifactLayout,
                moduleRole = "domain",
                templateId = "aggregate/strong_id.kt.peb",
                packageName = strongId.packageName,
                typeName = strongId.typeName,
                context = mapOf(
                    "packageName" to strongId.packageName,
                    "typeName" to strongId.typeName,
                    "aggregateElement" to strongIdAggregateElementContext(strongId),
                    "kind" to strongId.kind.name,
                    "valueType" to strongId.valueType,
                    "validationKind" to validationKind,
                    "stringBacked" to (strongId.valueType == "String"),
                    "uuidBacked" to (strongId.valueType == "UUID"),
                    "longBacked" to (strongId.valueType == "Long"),
                ),
            )
        }
    }
}
```

- [ ] **Step 5: Run GREEN tests and scan removed context.**

```powershell
.\gradlew.bat :cap4k-plugin-pipeline-api:test :cap4k-plugin-pipeline-core:test :cap4k-plugin-pipeline-generator-aggregate:test :cap4k-plugin-pipeline-renderer-pebble:test --no-daemon
rg -n 'canGenerateNew' cap4k-plugin-pipeline-api/src cap4k-plugin-pipeline-core/src cap4k-plugin-pipeline-generator-aggregate/src cap4k-plugin-pipeline-renderer-pebble/src
```

Expected: tests PASS; scan has no matches.

- [ ] **Step 6: Commit Task 7 only.**

```powershell
git add cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModels.kt cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssembler.kt cap4k-plugin-pipeline-core/src/test/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssemblerTest.kt cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/StrongIdArtifactPlanner.kt cap4k-plugin-pipeline-generator-aggregate/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateArtifactPlannerTest.kt cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/strong_id.kt.peb cap4k-plugin-pipeline-renderer-pebble/src/test/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PebbleArtifactRendererTest.kt docs/superpowers/plans/2026-07-24-cap4k-strong-id-create-time-injection.md
git commit -m "refactor: plan strong ids from resolved backing"
```

## Task 8: Render Four Strong ID Value Variants And Strict JSON

**Depends on:** Task 7

**Files:**

- Replace: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/strong_id.kt.peb`
- Modify: `cap4k-plugin-pipeline-renderer-pebble/src/test/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PebbleArtifactRendererTest.kt`

**Interfaces:**

- Consumes: Task 7 backing and validation flags.
- Produces: `StrongId<String|UUID|Long>`, `of(backing)`, `parse(String)`, scalar-string JSON.
- Must not produce: `new()`, a JPA converter, a hardcoded column length, or numeric Snowflake JSON.

- [ ] **Step 1: Add exact rendered-shape tests.**

Render each context and assert:

```kotlin
assertTrue(uuidText.contains("StrongId<String>"))
assertTrue(uuidText.contains("fun of(value: String): OrderId"))
assertTrue(uuidNative.contains("StrongId<UUID>"))
assertTrue(uuidNative.contains("fun of(value: UUID): OrderId"))
assertTrue(snowflakeText.contains("StrongIds.requireSnowflake(value, \"OrderId\")"))
assertTrue(snowflakeLong.contains("override var value: Long = 0L"))
assertTrue(snowflakeLong.contains("fun jsonValue(): String = value.toString()"))
listOf(uuidText, uuidNative, snowflakeText, snowflakeLong).forEach { source ->
    assertFalse(source.contains("fun new("))
    assertFalse(source.contains("AttributeConverter"))
    assertFalse(source.contains("length ="))
    assertTrue(source.contains("value.isTextual"))
}
```

Add compile-testing sources for `StrongId`, `StrongIds`, Jackson annotations/databind, and Jakarta persistence using the test's existing compile helper. Compile all four rendered classes together to catch overload/import collisions.

- [ ] **Step 2: Run the renderer RED test.**

```powershell
.\gradlew.bat :cap4k-plugin-pipeline-renderer-pebble:test --tests "com.only4.cap4k.plugin.pipeline.renderer.pebble.PebbleArtifactRendererTest" --no-daemon
```

Expected: FAIL against the String-only template.

- [ ] **Step 3: Replace the template with explicit backing branches.**

Replace the entire template with the following content:

```pebble
package {{ packageName }}

{% if aggregateElement is defined -%}
{{ use("com.only4.cap4k.ddd.core.annotation.AggregateElement") -}}
{% endif -%}
{{ use("com.fasterxml.jackson.annotation.JsonCreator") -}}
{{ use("com.fasterxml.jackson.annotation.JsonValue") -}}
{{ use("com.fasterxml.jackson.databind.JsonNode") -}}
{{ use("com.only4.cap4k.ddd.core.domain.id.StrongId") -}}
{{ use("com.only4.cap4k.ddd.core.domain.id.StrongIds") -}}
{{ use("jakarta.persistence.Column") -}}
{{ use("jakarta.persistence.Embeddable") -}}
{{ use("java.io.Serializable") -}}
{% if uuidBacked -%}
{{ use("java.util.UUID") -}}
{% endif -%}
{% for import in imports(imports) -%}
import {{ import }}
{% endfor %}

{% if aggregateElement is defined -%}
@AggregateElement(
    aggregate = {{ aggregateElement.aggregateKotlinStringLiteral | raw }},
    name = {{ aggregateElement.nameKotlinStringLiteral | raw }},
    packageName = {{ aggregateElement.packageNameKotlinStringLiteral | raw }},
    description = {{ aggregateElement.descriptionKotlinStringLiteral | raw }},
    type = {{ aggregateElement.typeKotlinStringLiteral | raw }},
    root = {{ aggregateElement.root }}
)
{% endif -%}
@Embeddable
class {{ typeName }} protected constructor() : StrongId<{{ valueType }}>, Serializable {
    @Column(name = "value", nullable = false, updatable = false)
{% if longBacked -%}
    override var value: Long = 0L
        protected set
{% else -%}
    override lateinit var value: {{ valueType }}
        protected set
{% endif %}

    private constructor(value: {{ valueType }}) : this() {
        this.value = value
    }

    @JsonValue
    fun jsonValue(): String = value.toString()

    override fun toString(): String = value.toString()

    companion object {
{% if validationKind == "UUID7" and stringBacked -%}
        fun of(value: String): {{ typeName }} =
            {{ typeName }}(StrongIds.requireUuidV7(value, "{{ typeName }}"))

        fun parse(value: String): {{ typeName }} = of(value)
{% elseif validationKind == "UUID7" and uuidBacked -%}
        fun of(value: UUID): {{ typeName }} =
            {{ typeName }}(StrongIds.requireUuidV7(value, "{{ typeName }}"))

        fun parse(value: String): {{ typeName }} =
            of(UUID.fromString(StrongIds.requireUuidV7(value, "{{ typeName }}")))
{% elseif validationKind == "SNOWFLAKE" and stringBacked -%}
        fun of(value: String): {{ typeName }} =
            {{ typeName }}(StrongIds.requireSnowflake(value, "{{ typeName }}"))

        fun parse(value: String): {{ typeName }} = of(value)
{% elseif validationKind == "SNOWFLAKE" and longBacked -%}
        fun of(value: Long): {{ typeName }} =
            {{ typeName }}(StrongIds.requireSnowflake(value, "{{ typeName }}"))

        fun parse(value: String): {{ typeName }} =
            of(StrongIds.requireSnowflake(value, "{{ typeName }}").toLong())
{% endif %}

        @JvmStatic
        @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
        fun fromJson(value: JsonNode): {{ typeName }} {
            require(value.isTextual) { "{{ typeName }} JSON value must be a string" }
            return parse(value.textValue())
        }
    }

    override fun equals(other: Any?): Boolean =
        this === other || (other is {{ typeName }} && value == other.value)

    override fun hashCode(): Int = value.hashCode()
}
```

Do not use `JsonNode.asText()` because it would coerce numeric tokens.

- [ ] **Step 4: Run GREEN renderer and compile tests.**

```powershell
.\gradlew.bat :cap4k-plugin-pipeline-renderer-pebble:test --tests "com.only4.cap4k.plugin.pipeline.renderer.pebble.PebbleArtifactRendererTest" --no-daemon
```

Expected: PASS. The numeric-token rejection is proven against real Jackson in Task 9.

- [ ] **Step 5: Scan and commit.**

```powershell
rg -n 'fun new\(|newUuidV7String|AttributeConverter|length = 36' cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/strong_id.kt.peb
```

Expected: no matches.

```powershell
git add cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/strong_id.kt.peb cap4k-plugin-pipeline-renderer-pebble/src/test/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PebbleArtifactRendererTest.kt
git commit -m "feat: render storage nearest strong ids"
```

## Task 9: Render Direct JPA Overrides And Prove Runtime JSON/JPA

**Depends on:** Task 8

**Files:**

- Modify: `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/EntityArtifactPlanner.kt`
- Modify: `cap4k-plugin-pipeline-generator-aggregate/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateArtifactPlannerTest.kt`
- Modify: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/entity.kt.peb`
- Modify: `cap4k-plugin-pipeline-renderer-pebble/src/test/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PebbleArtifactRendererTest.kt`
- Replace fixture matrix in: `cap4k-ddd-starter/src/test/kotlin/com/only4/cap4k/ddd/runtime/StrongIdJacksonRuntimeTest.kt`
- Extend: `cap4k-ddd-starter/src/test/kotlin/com/only4/cap4k/ddd/runtime/strongid/StrongIdJpaRuntimeTest.kt`

**Interfaces:**

- Consumes: `AggregateColumnJpaModel.columnLength` and Strong ID backing.
- Produces: String-only `length`, direct UUID/Long mappings, and scalar JSON.
- Rollback trigger: Hibernate requires a converter for a supported matrix cell.

- [ ] **Step 1: Add planner and renderer tests for authoritative entity overrides.**

Assert the field context carries:

```kotlin
"attributeOverrideLength" to if (strongId?.valueType == "String") jpa.columnLength else null
```

Render one field for each backing and assert:

```kotlin
assertTrue(uuidTextEntity.contains("updatable = false, length = 40"))
assertFalse(uuidNativeEntity.contains("length ="))
assertFalse(snowflakeLongEntity.contains("length ="))
assertTrue(snowflakeTextEntity.contains("length = 24"))
```

- [ ] **Step 2: Run generator/renderer RED tests.**

```powershell
.\gradlew.bat :cap4k-plugin-pipeline-generator-aggregate:test :cap4k-plugin-pipeline-renderer-pebble:test --no-daemon
```

Expected: FAIL because both Strong ID branches hardcode `length = 36`.

- [ ] **Step 3: Add the length context and conditional template fragment.**

Append to each scalar field context:

```kotlin
"attributeOverrideLength" to if (strongId?.valueType == "String") jpa.columnLength else null,
```

Replace both Strong ID `Column` annotation fragments with this exact argument order:

```pebble
Column(name = "{{ field.columnName }}", nullable = {{ field.attributeOverrideNullable }}, {% if field.attributeOverrideInsertable is not null %}insertable = {{ field.attributeOverrideInsertable }}, {% endif %}updatable = {{ field.attributeOverrideUpdatable }}{% if field.attributeOverrideLength is not null %}, length = {{ field.attributeOverrideLength }}{% endif %})
```

Do not change value-object converter branches.

- [ ] **Step 4: Replace Jackson fixtures with the four generated semantic shapes.**

Use fixed values so this task tests value transport, not allocation:

```kotlin
private const val UUID7_TEXT = "019c0000-0000-7000-8000-000000000001"
private const val SNOWFLAKE_TEXT = "7288198123456789012"

data class Payload(
    val uuidText: UuidTextId,
    val uuidNative: UuidNativeId,
    val snowflakeText: SnowflakeTextId,
    val snowflakeLong: SnowflakeLongId,
)
```

Implement each fixture with the exact Task 8 generated shape. Add tests that serialize all four values as strings, deserialize them, reject object form, and reject:

```kotlin
objectMapper.readValue(
    """{"snowflakeLong":7288198123456789012}""",
    SnowflakeLongPayload::class.java,
)
```

The numeric-token assertion must fail through `JsonNode.isTextual`, not through overflow.

- [ ] **Step 5: Extend the JPA fixture to all four direct mappings.**

Add four embeddables and one entity/table with:

```kotlin
@EmbeddedId
@AttributeOverride(name = "value", column = Column(name = "id", updatable = false, length = 36))
open lateinit var id: UuidTextId

@Embedded
@AttributeOverride(name = "value", column = Column(name = "native_uuid", updatable = false))
open lateinit var nativeUuid: UuidNativeId

@Embedded
@AttributeOverride(name = "value", column = Column(name = "snowflake_text", updatable = false, length = 19))
open lateinit var snowflakeText: SnowflakeTextId

@Embedded
@AttributeOverride(name = "value", column = Column(name = "snowflake_long", updatable = false))
open lateinit var snowflakeLong: SnowflakeLongId
```

Persist, flush, clear, reload, and query raw JDBC values as `String`, `UUID`, `String`, and `Long`. No fixture may declare `AttributeConverter`.

- [ ] **Step 6: Run runtime GREEN tests.**

```powershell
.\gradlew.bat :cap4k-plugin-pipeline-generator-aggregate:test :cap4k-plugin-pipeline-renderer-pebble:test --no-daemon
.\gradlew.bat :cap4k-ddd-starter:test --tests "com.only4.cap4k.ddd.runtime.StrongIdJacksonRuntimeTest" --tests "com.only4.cap4k.ddd.runtime.strongid.StrongIdJpaRuntimeTest" --no-daemon
```

Expected: PASS. If direct native UUID or BIGINT embedding requires a converter, stop and revise the design rather than adding one.

- [ ] **Step 7: Commit Task 9 only.**

```powershell
git add cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/EntityArtifactPlanner.kt cap4k-plugin-pipeline-generator-aggregate/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateArtifactPlannerTest.kt cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/entity.kt.peb cap4k-plugin-pipeline-renderer-pebble/src/test/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PebbleArtifactRendererTest.kt cap4k-ddd-starter/src/test/kotlin/com/only4/cap4k/ddd/runtime/StrongIdJacksonRuntimeTest.kt cap4k-ddd-starter/src/test/kotlin/com/only4/cap4k/ddd/runtime/strongid/StrongIdJpaRuntimeTest.kt
git commit -m "feat: persist and serialize strong id backings directly"
```

## Task 10: Add Idempotent Generated Own-ID Assignment

**Depends on:** Task 6

**Files:**

- Create: `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/id/GeneratedOwnId.kt`
- Create: `ddd-core/src/test/kotlin/com/only4/cap4k/ddd/core/domain/id/GeneratedOwnIdTest.kt`

**Interfaces:**

- Consumes: typed current/read, assign, and next functions.
- Produces: the existing or newly assigned typed ID.
- Catches: only `UninitializedPropertyAccessException` in `readInitializedOrNull`.

- [ ] **Step 1: Create the failing assignment tests.**

Create `GeneratedOwnIdTest.kt`:

```kotlin
package com.only4.cap4k.ddd.core.domain.id

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class GeneratedOwnIdTest {
    @Test
    fun `preserves an existing id without allocation or assignment`() {
        val existing = Any()
        var nextCalls = 0
        var assignCalls = 0

        val result = GeneratedOwnId.assignIfMissing(
            current = { existing },
            assign = { assignCalls++ },
            next = { nextCalls++; Any() },
            label = "Order.id",
        )

        assertSame(existing, result)
        assertEquals(0, nextCalls)
        assertEquals(0, assignCalls)
    }

    @Test
    fun `allocates assigns and reads back exactly once when missing`() {
        val generated = Any()
        var current: Any? = null
        var nextCalls = 0
        var assignCalls = 0

        val result = GeneratedOwnId.assignIfMissing(
            current = { current },
            assign = { current = it; assignCalls++ },
            next = { nextCalls++; generated },
            label = "Order.id",
        )

        assertSame(generated, result)
        assertEquals(1, nextCalls)
        assertEquals(1, assignCalls)
    }

    @Test
    fun `fails with label when assignment does not stick`() {
        val error = assertThrows(IllegalStateException::class.java) {
            GeneratedOwnId.assignIfMissing(
                current = { null },
                assign = {},
                next = { Any() },
                label = "Order.id",
            )
        }
        assertEquals("generated own ID assignment failed: Order.id", error.message)
    }

    @Test
    fun `readInitializedOrNull catches only lateinit access`() {
        class Holder { lateinit var value: String }
        val holder = Holder()

        assertEquals(null, readInitializedOrNull { holder.value })
        assertThrows(IllegalArgumentException::class.java) {
            readInitializedOrNull<String> { throw IllegalArgumentException("boom") }
        }
    }
}
```

- [ ] **Step 2: Run the RED test.**

```powershell
.\gradlew.bat :ddd-core:test --tests "com.only4.cap4k.ddd.core.domain.id.GeneratedOwnIdTest" --no-daemon
```

Expected: test compilation FAILS because the helper is absent.

- [ ] **Step 3: Create the complete assignment helper.**

```kotlin
package com.only4.cap4k.ddd.core.domain.id

object GeneratedOwnId {
    fun <ID : Any> assignIfMissing(
        current: () -> ID?,
        assign: (ID) -> Unit,
        next: () -> ID,
        label: String,
    ): ID {
        current()?.let { return it }
        val generated = next()
        assign(generated)
        return current() ?: error("generated own ID assignment failed: $label")
    }
}

inline fun <ID : Any> readInitializedOrNull(read: () -> ID): ID? =
    try {
        read()
    } catch (_: UninitializedPropertyAccessException) {
        null
    }
```

- [ ] **Step 4: Run GREEN tests and commit.**

```powershell
.\gradlew.bat :ddd-core:test --tests "com.only4.cap4k.ddd.core.domain.id.GeneratedOwnIdTest" --no-daemon
git add ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/id/GeneratedOwnId.kt ddd-core/src/test/kotlin/com/only4/cap4k/ddd/core/domain/id/GeneratedOwnIdTest.kt
git commit -m "feat: add idempotent generated id assignment"
```

## Task 11: Add Typed Accessor Catalog And Registry Contracts

**Depends on:** Task 10

**Files:**

- Create: `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/id/GeneratedOwnIdAccessor.kt`
- Create: `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/id/GeneratedOwnIdCatalog.kt`
- Create: `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/id/GeneratedOwnIdRegistry.kt`
- Create: `ddd-core/src/test/kotlin/com/only4/cap4k/ddd/core/domain/id/GeneratedOwnIdRegistryTest.kt`

**Interfaces:**

- Consumes: generated typed accessors grouped in catalogs.
- Produces: exact persistent-class lookup as `GeneratedOwnIdAccessor<Any, Any>?`.
- Owns: the only unchecked cast in generated own-ID infrastructure.

- [ ] **Step 1: Create the failing registry tests.**

Create `GeneratedOwnIdRegistryTest.kt`:

```kotlin
package com.only4.cap4k.ddd.core.domain.id

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.reflect.KClass

class GeneratedOwnIdRegistryTest {
    @Test
    fun `empty catalogs create an empty registry`() {
        assertNull(MapBackedGeneratedOwnIdRegistry(emptyList()).accessorFor(Entity::class))
    }

    @Test
    fun `registry flattens catalogs and returns exact accessor`() {
        val first = accessor(Entity::class, "Entity.id")
        val second = accessor(OtherEntity::class, "OtherEntity.id")
        val registry = MapBackedGeneratedOwnIdRegistry(
            listOf(catalog(first), catalog(second))
        )

        assertSame(first, registry.accessorFor(Entity::class))
        assertSame(second, registry.accessorFor(OtherEntity::class))
        assertNull(registry.accessorFor(UnknownEntity::class))
    }

    @Test
    fun `duplicate entity accessors fail immediately with labels`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            MapBackedGeneratedOwnIdRegistry(
                listOf(
                    catalog(accessor(Entity::class, "first")),
                    catalog(accessor(Entity::class, "second")),
                )
            )
        }

        assertTrue(error.message!!.contains(Entity::class.qualifiedName!!))
        assertTrue(error.message!!.contains("first"))
        assertTrue(error.message!!.contains("second"))
    }

    @Test
    fun `accessor default assignment delegates to shared helper`() {
        val accessor = accessor(Entity::class, "Entity.id")
        val entity = Entity()

        assertEquals("ID-1", accessor.assignIfMissing(entity))
        assertEquals("ID-1", accessor.assignIfMissing(entity))
        assertEquals(1, entity.assignments)
    }

    private fun <E : Any> accessor(type: KClass<E>, label: String) =
        object : GeneratedOwnIdAccessor<E, String> {
            override val entityType: KClass<E> = type
            override val label: String = label
            override fun current(entity: E): String? = (entity as? Entity)?.id ?: (entity as? OtherEntity)?.id
            override fun assign(entity: E, id: String) {
                when (entity) {
                    is Entity -> { entity.id = id; entity.assignments++ }
                    is OtherEntity -> entity.id = id
                }
            }
            override fun next(): String = "ID-1"
        }

    private fun catalog(accessor: GeneratedOwnIdAccessor<*, *>) =
        object : GeneratedOwnIdCatalog {
            override val accessors: List<GeneratedOwnIdAccessor<*, *>> = listOf(accessor)
        }

    private class Entity(var id: String? = null, var assignments: Int = 0)
    private class OtherEntity(var id: String? = null)
    private class UnknownEntity
}
```

- [ ] **Step 2: Run the RED test.**

```powershell
.\gradlew.bat :ddd-core:test --tests "com.only4.cap4k.ddd.core.domain.id.GeneratedOwnIdRegistryTest" --no-daemon
```

Expected: test compilation FAILS because all three contracts are absent.

- [ ] **Step 3: Create the typed accessor.**

```kotlin
package com.only4.cap4k.ddd.core.domain.id

import kotlin.reflect.KClass

interface GeneratedOwnIdAccessor<E : Any, ID : Any> {
    val entityType: KClass<E>
    val label: String

    fun current(entity: E): ID?
    fun assign(entity: E, id: ID)
    fun next(): ID

    fun assignIfMissing(entity: E): ID =
        GeneratedOwnId.assignIfMissing(
            current = { current(entity) },
            assign = { assign(entity, it) },
            next = ::next,
            label = label,
        )
}
```

- [ ] **Step 4: Create the catalog and registry.**

`GeneratedOwnIdCatalog.kt`:

```kotlin
package com.only4.cap4k.ddd.core.domain.id

interface GeneratedOwnIdCatalog {
    val accessors: List<GeneratedOwnIdAccessor<*, *>>
}
```

`GeneratedOwnIdRegistry.kt`:

```kotlin
package com.only4.cap4k.ddd.core.domain.id

import kotlin.reflect.KClass

interface GeneratedOwnIdRegistry {
    fun accessorFor(entityType: KClass<*>): GeneratedOwnIdAccessor<Any, Any>?
}

class MapBackedGeneratedOwnIdRegistry(
    catalogs: Iterable<GeneratedOwnIdCatalog>,
) : GeneratedOwnIdRegistry {
    private val accessorsByEntityType: Map<KClass<*>, GeneratedOwnIdAccessor<*, *>> =
        catalogs.flatMap { it.accessors }.fold(linkedMapOf()) { result, accessor ->
            val previous = result.putIfAbsent(accessor.entityType, accessor)
            require(previous == null) {
                "duplicate generated own ID accessor for ${accessor.entityType.qualifiedName}: " +
                    "${previous?.label} and ${accessor.label}"
            }
            result
        }

    @Suppress("UNCHECKED_CAST")
    override fun accessorFor(entityType: KClass<*>): GeneratedOwnIdAccessor<Any, Any>? =
        accessorsByEntityType[entityType] as GeneratedOwnIdAccessor<Any, Any>?
}
```

- [ ] **Step 5: Run GREEN tests and verify cast ownership.**

```powershell
.\gradlew.bat :ddd-core:test --tests "com.only4.cap4k.ddd.core.domain.id.GeneratedOwnIdTest" --tests "com.only4.cap4k.ddd.core.domain.id.GeneratedOwnIdRegistryTest" --no-daemon
rg -n 'UNCHECKED_CAST' ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/id/GeneratedOwnId*.kt
```

Expected: tests PASS; exactly one match in `MapBackedGeneratedOwnIdRegistry.accessorFor`.

- [ ] **Step 6: Commit Task 11 only.**

```powershell
git add ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/id/GeneratedOwnIdAccessor.kt ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/id/GeneratedOwnIdCatalog.kt ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/id/GeneratedOwnIdRegistry.kt ddd-core/src/test/kotlin/com/only4/cap4k/ddd/core/domain/id/GeneratedOwnIdRegistryTest.kt
git commit -m "feat: add generated own id registry contracts"
```

## Task 12: Assemble The Default Generated Own-ID Registry

**Depends on:** Task 11

**Files:**

- Modify: `cap4k-ddd-starter/src/main/kotlin/com/only4/cap4k/ddd/domain/id/IdPolicyAutoConfiguration.kt`
- Modify: `cap4k-ddd-starter/src/test/kotlin/com/only4/cap4k/ddd/domain/id/IdPolicyAutoConfigurationTest.kt`

**Interfaces:**

- Consumes: zero or more Spring beans implementing `GeneratedOwnIdCatalog`.
- Produces: one default `GeneratedOwnIdRegistry` unless the application provides one.
- Preserves: UUID7/Snowflake strategy registry and `IdentifierGenerator` beans.

- [ ] **Step 1: Add context tests for empty, collected, duplicate, and user registry cases.**

Add imports for `GeneratedOwnIdAccessor`, `GeneratedOwnIdCatalog`, `GeneratedOwnIdRegistry`, `KClass`, `assertNull`, and `assertSame`, then add these tests using the existing `ApplicationContextRunner`:

```kotlin
@Test
fun `generated own id registry is empty without catalogs`() {
    contextRunner.run { context ->
        assertNull(context.getBean(GeneratedOwnIdRegistry::class.java).accessorFor(TestEntity::class))
    }
}

@Test
fun `generated own id registry collects catalogs`() {
    ApplicationContextRunner()
        .withBean("testCatalog", GeneratedOwnIdCatalog::class.java, { catalog(TestEntityAccessor) })
        .withUserConfiguration(IdPolicyAutoConfiguration::class.java)
        .run { context ->
            assertSame(
                TestEntityAccessor,
                context.getBean(GeneratedOwnIdRegistry::class.java).accessorFor(TestEntity::class),
            )
        }
}

@Test
fun `application registry replaces the default`() {
    val custom = object : GeneratedOwnIdRegistry {
        override fun accessorFor(entityType: KClass<*>) = null
    }
    ApplicationContextRunner()
        .withBean(GeneratedOwnIdRegistry::class.java, { custom })
        .withUserConfiguration(IdPolicyAutoConfiguration::class.java)
        .run { context -> assertSame(custom, context.getBean(GeneratedOwnIdRegistry::class.java)) }
}

@Test
fun `duplicate catalog accessors fail application startup`() {
    ApplicationContextRunner()
        .withBean("firstCatalog", GeneratedOwnIdCatalog::class.java, { catalog(TestEntityAccessor) })
        .withBean("secondCatalog", GeneratedOwnIdCatalog::class.java, { catalog(TestEntityAccessor) })
        .withUserConfiguration(IdPolicyAutoConfiguration::class.java)
        .run { context ->
            assertNotNull(context.startupFailure)
            assertTrue(context.startupFailure!!.stackTraceToString().contains("duplicate generated own ID accessor"))
        }
}
```

Add these complete private fixtures to the test class:

```kotlin
private class TestEntity(var id: String? = null)

private object TestEntityAccessor : GeneratedOwnIdAccessor<TestEntity, String> {
    override val entityType: KClass<TestEntity> = TestEntity::class
    override val label: String = "TestEntity.id"
    override fun current(entity: TestEntity): String? = entity.id
    override fun assign(entity: TestEntity, id: String) {
        entity.id = id
    }
    override fun next(): String = "ID-1"
}

private fun catalog(vararg accessors: GeneratedOwnIdAccessor<*, *>): GeneratedOwnIdCatalog =
    object : GeneratedOwnIdCatalog {
        override val accessors: List<GeneratedOwnIdAccessor<*, *>> = accessors.toList()
    }
```

- [ ] **Step 2: Run RED context tests.**

```powershell
.\gradlew.bat :cap4k-ddd-starter:test --tests "com.only4.cap4k.ddd.domain.id.IdPolicyAutoConfigurationTest" --no-daemon
```

Expected: FAIL because no registry bean exists.

- [ ] **Step 3: Add the default bean without scanning.**

Add imports for the three generated own-ID contracts and this bean method:

```kotlin
@Bean
@ConditionalOnMissingBean(GeneratedOwnIdRegistry::class)
fun generatedOwnIdRegistry(catalogs: List<GeneratedOwnIdCatalog>): GeneratedOwnIdRegistry =
    MapBackedGeneratedOwnIdRegistry(catalogs)
```

Do not inject `IdentifierGenerator` into the registry and do not classpath-scan accessor objects.

- [ ] **Step 4: Run GREEN context tests and commit.**

```powershell
.\gradlew.bat :cap4k-ddd-starter:test --tests "com.only4.cap4k.ddd.domain.id.IdPolicyAutoConfigurationTest" --no-daemon
git add cap4k-ddd-starter/src/main/kotlin/com/only4/cap4k/ddd/domain/id/IdPolicyAutoConfiguration.kt cap4k-ddd-starter/src/test/kotlin/com/only4/cap4k/ddd/domain/id/IdPolicyAutoConfigurationTest.kt
git commit -m "feat: assemble generated own id catalogs"
```

## Task 13: Add The Infrastructure-Free Owned Relation Hook

**Depends on:** Task 10

**Files:**

- Modify: `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/aggregate/OwnedEntityList.kt`
- Modify: `ddd-core/src/test/kotlin/com/only4/cap4k/ddd/core/domain/aggregate/OwnedEntityListTest.kt`

**Interfaces:**

- Consumes: an optional typed `prepare: (E) -> Unit` supplied by generated code.
- Produces: prepare-before-add and prepare-before-replace ordering.
- Preserves: read-only `List` exposure, removal behavior, cardinality checks, and infrastructure independence.

- [ ] **Step 1: Add failing prepare-order and failure-atomicity tests.**

Add these tests:

```kotlin
@Test
fun `add prepares before mutating delegate`() {
    val events = mutableListOf<String>()
    val delegate = object : ArrayList<TestChild>() {
        override fun add(element: TestChild): Boolean {
            events += "add"
            return super.add(element)
        }
    }
    val children = OwnedEntityList.of(delegate, TestChild::class, "Parent.children") {
        events += "prepare"
    }

    children.add(TestChild("new"))

    assertEquals(listOf("prepare", "add"), events)
}

@Test
fun `failed add preparation leaves delegate unchanged`() {
    val old = TestChild("old")
    val delegate = mutableListOf(old)
    val children = OwnedEntityList.of(delegate, TestChild::class, "Parent.children") {
        error("allocation failed")
    }

    assertThrows(IllegalStateException::class.java) { children.add(TestChild("new")) }
    assertEquals(listOf(old), delegate)
}

@Test
fun `failed replace preparation preserves old child`() {
    val old = TestChild("old")
    val delegate = mutableListOf(old)
    val children = OwnedEntityList.of(delegate, TestChild::class, "Parent.child") {
        error("allocation failed")
    }

    assertThrows(IllegalStateException::class.java) { children.replace(TestChild("new")) }
    assertEquals(listOf(old), delegate)
}

@Test
fun `replace null does not prepare`() {
    var prepareCalls = 0
    val delegate = mutableListOf(TestChild("old"))
    val children = OwnedEntityList.of(delegate, TestChild::class, "Parent.child") {
        prepareCalls++
    }

    children.replace(null)

    assertEquals(0, prepareCalls)
    assertTrue(delegate.isEmpty())
}
```

- [ ] **Step 2: Run the RED test.**

```powershell
.\gradlew.bat :ddd-core:test --tests "com.only4.cap4k.ddd.core.domain.aggregate.OwnedEntityListTest" --no-daemon
```

Expected: test compilation FAILS because `of` does not accept the hook.

- [ ] **Step 3: Replace `OwnedEntityList` with the narrow hook implementation.**

```kotlin
package com.only4.cap4k.ddd.core.domain.aggregate

import java.util.Collections
import kotlin.reflect.KClass

open class OwnedEntityList<E : Any> protected constructor(
    private val delegate: MutableList<E>,
    private val entityType: KClass<E>,
    private val path: String,
) : List<E> by Collections.unmodifiableList(delegate) {

    protected open fun prepareEntry(entity: E) = Unit

    fun add(entity: E): Boolean {
        prepareEntry(entity)
        return delegate.add(entity)
    }

    fun remove(entity: E): Boolean = delegate.remove(entity)

    fun singleOrNull(): E? {
        check(delegate.size <= 1) {
            "owned relation $path expected at most one ${entityType.simpleName} but found ${delegate.size}"
        }
        return delegate.singleOrNull()
    }

    fun replace(value: E?) {
        check(delegate.size <= 1) {
            "owned relation $path expected at most one ${entityType.simpleName} but found ${delegate.size}"
        }
        if (value != null) prepareEntry(value)
        delegate.clear()
        if (value != null) delegate.add(value)
    }

    companion object {
        fun <E : Any> of(
            delegate: MutableList<E>,
            entityType: KClass<E>,
            path: String,
        ): OwnedEntityList<E> = object : OwnedEntityList<E>(delegate, entityType, path) {}

        fun <E : Any> of(
            delegate: MutableList<E>,
            entityType: KClass<E>,
            path: String,
            prepare: (E) -> Unit,
        ): OwnedEntityList<E> = object : OwnedEntityList<E>(delegate, entityType, path) {
            override fun prepareEntry(entity: E) = prepare(entity)
        }
    }
}
```

The protected hook is the generated-code extension point required by the spec. The overload merely adapts a relation-local typed lambda to that hook; `OwnedEntityList` still does not know how an ID is allocated.

- [ ] **Step 4: Run GREEN tests and boundary scan.**

```powershell
.\gradlew.bat :ddd-core:test --tests "com.only4.cap4k.ddd.core.domain.aggregate.OwnedEntityListTest" --no-daemon
rg -n 'Mediator|UnitOfWork|Repository|EntityManager|IdentifierGenerator|GeneratedOwnIdRegistry' ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/aggregate/OwnedEntityList.kt
```

Expected: tests PASS; scan has no matches.

- [ ] **Step 5: Commit Task 13 only.**

```powershell
git add ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/aggregate/OwnedEntityList.kt ddd-core/src/test/kotlin/com/only4/cap4k/ddd/core/domain/aggregate/OwnedEntityListTest.kt
git commit -m "feat: prepare owned children before relation mutation"
```

## Task 14: Replace Reflective JPA Completion With Registry Lookup

**Depends on:** Tasks 11 and 12

**Files:**

- Replace: `ddd-domain-repo-jpa/src/main/kotlin/com/only4/cap4k/ddd/application/JpaGeneratedStrongIdSupport.kt`
- Create: `ddd-domain-repo-jpa/src/test/kotlin/com/only4/cap4k/ddd/application/JpaGeneratedStrongIdSupportTest.kt`

**Interfaces:**

- Consumes: `GeneratedOwnIdRegistry`, bounded `JpaGeneratedOwnedRelationTraversal`, and repository baseline.
- Produces: idempotent CREATE graph completion and new-child completion for EXISTING roots.
- Must not inspect: fields, `@EmbeddedId`, `StrongId`, companions, or methods.

- [ ] **Step 1: Add registry-only completion tests.**

Create `JpaGeneratedStrongIdSupportTest.kt` with generated-style root/child accessors and these cases:

```kotlin
@Test
fun `CREATE completes root and reachable child through registered accessors`() {
    val root = Root().also { it.children += Child() }
    val support = support(RootAccessor, ChildAccessor)

    support.completeCreate(root, JpaGeneratedOwnedRelationTraversal())

    assertEquals("ROOT-1", root.id)
    assertEquals("CHILD-1", root.children.single().id)
}

@Test
fun `CREATE preserves preassigned ids`() {
    val root = Root().also { it.id = "ROOT-99" }

    support(RootAccessor).completeCreate(root, JpaGeneratedOwnedRelationTraversal())

    assertEquals("ROOT-99", root.id)
    assertEquals(0, RootAccessor.nextCalls)
}

@Test
fun `unregistered entity is ignored without reflection`() {
    val entity = UnregisteredEntity()

    support().completeCreate(entity, JpaGeneratedOwnedRelationTraversal())

    assertEquals(null, entity.id)
}
```

Define `Root.children` with exactly the supported traversal annotations:

```kotlin
@field:OneToMany(cascade = [CascadeType.PERSIST, CascadeType.MERGE], orphanRemoval = true)
@field:JoinColumn(name = "root_id")
val children: MutableList<Child> = mutableListOf()
```

The test helper constructs `MapBackedGeneratedOwnIdRegistry(listOf(catalog))`; it must not expose a companion `new()` on any ID type.

- [ ] **Step 2: Run the RED test.**

```powershell
.\gradlew.bat :ddd-domain-repo-jpa:test --tests "com.only4.cap4k.ddd.application.JpaGeneratedStrongIdSupportTest" --no-daemon
```

Expected: test compilation FAILS because `JpaGeneratedStrongIdSupport` accepts no registry; reflective behavior cannot complete String fixture fields.

- [ ] **Step 3: Replace the production helper with registry-only code.**

```kotlin
package com.only4.cap4k.ddd.application

import com.only4.cap4k.ddd.core.domain.id.GeneratedOwnIdAccessor
import com.only4.cap4k.ddd.core.domain.id.GeneratedOwnIdRegistry
import org.hibernate.Hibernate

internal class JpaGeneratedStrongIdSupport(
    private val registry: GeneratedOwnIdRegistry,
) {
    fun completeCreate(root: Any, traversal: JpaGeneratedOwnedRelationTraversal) {
        traversal.reachableOwnedEntities(root).forEach(::assignIfRegistered)
    }

    fun completeExisting(
        root: Any,
        traversal: JpaGeneratedOwnedRelationTraversal,
        baseline: JpaRepositoryObservationBaseline,
    ) {
        val reachable = traversal.reachableOwnedEntities(root)
        val traversalRoot = reachable.firstOrNull() ?: root
        validateExistingRoot(traversalRoot)
        validateObservedIdentities(reachable, baseline)
        reachable.asSequence()
            .filterNot { it === traversalRoot }
            .filterNot { baseline.isObservedObject(it) }
            .forEach(::assignIfRegistered)
    }

    private fun assignIfRegistered(entity: Any) {
        accessorFor(entity)?.assignIfMissing(entity)
    }

    private fun validateExistingRoot(root: Any) {
        accessorFor(root)?.let { accessor ->
            check(accessor.current(root) != null) {
                "Existing-intent root ${Hibernate.getClassLazy(root).name} has missing generated own ID"
            }
        }
    }

    private fun validateObservedIdentities(
        reachable: Iterable<Any>,
        baseline: JpaRepositoryObservationBaseline,
    ) {
        reachable.filter { baseline.isObservedObject(it) }.forEach { entity ->
            accessorFor(entity)?.let { accessor ->
                val current = accessor.current(entity)
                check(current != null) {
                    "Observed existing entity ${Hibernate.getClassLazy(entity).name} has missing generated own ID"
                }
                baseline.identityFor(entity)?.let { observed ->
                    check(current == observed.id) {
                        "Observed existing entity ${observed.entityType.name} changed identity " +
                            "from ${observed.id} to $current"
                    }
                }
            }
        }
    }

    private fun accessorFor(entity: Any): GeneratedOwnIdAccessor<Any, Any>? =
        registry.accessorFor(Hibernate.getClassLazy(entity).kotlin)
}
```

- [ ] **Step 4: Add EXISTING baseline tests.**

Prove all three exact branches:

```kotlin
// observed root and child keep their IDs
support.completeExisting(root, traversal, baseline)
assertEquals("ROOT-99", root.id)
assertEquals("CHILD-99", observedChild.id)

// a reachable unobserved child receives an ID
assertEquals("CHILD-1", newChild.id)

// changing an observed child ID fails before persistence
assertThrows(IllegalStateException::class.java) {
    support.completeExisting(rootWithChangedChild, traversal, baseline)
}
```

- [ ] **Step 5: Run GREEN tests and reflection scan.**

```powershell
.\gradlew.bat :ddd-domain-repo-jpa:test --tests "com.only4.cap4k.ddd.application.JpaGeneratedStrongIdSupportTest" --no-daemon
rg -n 'java\.lang\.reflect|EmbeddedId|StrongId::class|getField\("Companion"\)|method\.name == "new"' ddd-domain-repo-jpa/src/main/kotlin/com/only4/cap4k/ddd/application/JpaGeneratedStrongIdSupport.kt
```

Expected: tests PASS; scan has no matches.

- [ ] **Step 6: Commit Task 14 only.**

```powershell
git add ddd-domain-repo-jpa/src/main/kotlin/com/only4/cap4k/ddd/application/JpaGeneratedStrongIdSupport.kt ddd-domain-repo-jpa/src/test/kotlin/com/only4/cap4k/ddd/application/JpaGeneratedStrongIdSupportTest.kt
git commit -m "refactor: complete generated ids through registry"
```

## Task 15: Cut UoW And Starter Wiring Over To The Registry

**Depends on:** Task 14

**Files:**

- Modify: `ddd-domain-repo-jpa/src/main/kotlin/com/only4/cap4k/ddd/application/JpaUnitOfWork.kt`
- Modify: `ddd-domain-repo-jpa/src/test/kotlin/com/only4/cap4k/ddd/application/JpaUnitOfWorkTest.kt`
- Modify: `cap4k-ddd-starter/src/main/kotlin/com/only4/cap4k/ddd/domain/repo/JpaRepositoryAutoConfiguration.kt`
- Modify: `cap4k-ddd-starter/src/test/kotlin/com/only4/cap4k/ddd/runtime/AggregateJpaRuntimeDefectReproductionTest.kt`

**Interfaces:**

- Consumes: `GeneratedOwnIdRegistry` rather than `IdentifierStrategyRegistry`.
- Produces: registry-only completion at `persist` enrollment and save backstop.
- Preserves: provider `EntityInformation`, observation baseline, database identity refresh, interceptor/listener order.

- [ ] **Step 1: Change the UoW test harness to a generated registry.**

Replace identifier strategy imports/constructor parameters in `JpaUnitOfWorkTest` with:

```kotlin
generatedOwnIdRegistry: GeneratedOwnIdRegistry = MapBackedGeneratedOwnIdRegistry(emptyList()),
```

and pass it to `JpaUnitOfWork`. In `setUp`, build one catalog containing `StrongRootEntityAccessor` and `StrongChildEntityAccessor`; these accessors use fixed sequential valid UUIDv7 values and `readInitializedOrNull` for current values.

- [ ] **Step 2: Run the UoW RED tests.**

```powershell
.\gradlew.bat :ddd-domain-repo-jpa:test --tests "com.only4.cap4k.ddd.application.JpaUnitOfWorkTest" --no-daemon
```

Expected: test compilation FAILS because the UoW still accepts `IdentifierStrategyRegistry` and constructs reflective support.

- [ ] **Step 3: Replace the constructor dependency and completion fields.**

Use these imports and constructor tail:

```kotlin
import com.only4.cap4k.ddd.core.domain.id.GeneratedOwnIdRegistry
import com.only4.cap4k.ddd.core.domain.id.MapBackedGeneratedOwnIdRegistry
```

```kotlin
open class JpaUnitOfWork(
    private val uowInterceptors: List<UnitOfWorkInterceptor>,
    private val persistListenerManager: PersistListenerManager,
    private val supportEntityInlinePersistListener: Boolean,
    generatedOwnIdRegistry: GeneratedOwnIdRegistry = MapBackedGeneratedOwnIdRegistry(emptyList()),
) : UnitOfWork, JpaRepositoryObservationRecorder {
```

Delete the redundant three-argument secondary constructor; trailing default preserves Kotlin call sites. Replace both support fields with:

```kotlin
private val generatedStrongIdSupport = JpaGeneratedStrongIdSupport(generatedOwnIdRegistry)
private val ownedRelationTraversal = JpaGeneratedOwnedRelationTraversal()
```

- [ ] **Step 4: Remove annotation branches from UoW methods.**

The final completion function is:

```kotlin
private fun completeIdsForEntry(entry: UnitOfWorkEntry) {
    when (entry.kind) {
        UnitOfWorkEntryKind.CREATE ->
            generatedStrongIdSupport.completeCreate(entry.entity, ownedRelationTraversal)
        UnitOfWorkEntryKind.EXISTING -> {
            validateExistingEvidence(entry.entity)
            validateObservedIdentityConsistency(entry.entity)
            generatedStrongIdSupport.completeExisting(
                root = entry.entity,
                traversal = ownedRelationTraversal,
                baseline = repositoryObservationBaseline,
            )
        }
        UnitOfWorkEntryKind.REMOVE -> Unit
    }
}
```

Replace `identityOf` with provider-only identity:

```kotlin
private fun identityOf(entity: Any): EntityIdentity? {
    val entityClass = persistentEntityClass(entity)
    val entityInformation = getEntityInformation(entityClass)
    if (entityInformation.isNew(entity)) return null
    val id = entityInformation.getId(entity) ?: return null
    return EntityIdentity(entityClass, id)
}
```

Replace `applyCreate` prelude with:

```kotlin
private fun applyCreate(entity: Any, results: FlushResult) {
    val entityClass = persistentEntityClass(entity)
    val refreshRequired = getEntityInformation(entityClass).isNew(entity)
    if (!entityManager.contains(entity)) entityManager.persist(entity)
    if (refreshRequired) results.refreshList.add(entity)
    results.created.add(entity)
    results.needsFlush = true
}
```

Delete `validateCreateApplicationSideId`. Replace `validateExistingRootIdentified` with its existing provider-only tail:

```kotlin
private fun validateExistingRootIdentified(entity: Any) {
    val entityClass = persistentEntityClass(entity)
    check(!getEntityInformation(entityClass).isNew(entity)) {
        "Existing-intent entity appears new: ${entity.javaClass.name}"
    }
}
```

- [ ] **Step 5: Replace starter UoW injection.**

In `JpaRepositoryAutoConfiguration`, inject `GeneratedOwnIdRegistry` and call:

```kotlin
JpaUnitOfWork(
    unitOfWorkInterceptors,
    persistListenerManager,
    jpaUnitOfWorkProperties.supportEntityInlinePersistListener,
    generatedOwnIdRegistry,
)
```

Do not remove `IdentifierStrategyRegistry` or `IdentifierGenerator` from `IdPolicyAutoConfiguration`; generated accessors use them through `Mediator.identifiers`.

- [ ] **Step 6: Run GREEN tests and dependency scans.**

```powershell
.\gradlew.bat :ddd-domain-repo-jpa:test :cap4k-ddd-starter:test --no-daemon
rg -n 'IdentifierStrategyRegistry|JpaApplicationSideIdSupport|applicationSideIdSupport' ddd-domain-repo-jpa/src/main/kotlin/com/only4/cap4k/ddd/application/JpaUnitOfWork.kt cap4k-ddd-starter/src/main/kotlin/com/only4/cap4k/ddd/domain/repo/JpaRepositoryAutoConfiguration.kt
```

Expected: tests PASS; scan has no matches.

- [ ] **Step 7: Commit Task 15 only.**

```powershell
git add ddd-domain-repo-jpa/src/main/kotlin/com/only4/cap4k/ddd/application/JpaUnitOfWork.kt ddd-domain-repo-jpa/src/test/kotlin/com/only4/cap4k/ddd/application/JpaUnitOfWorkTest.kt cap4k-ddd-starter/src/main/kotlin/com/only4/cap4k/ddd/domain/repo/JpaRepositoryAutoConfiguration.kt cap4k-ddd-starter/src/test/kotlin/com/only4/cap4k/ddd/runtime/AggregateJpaRuntimeDefectReproductionTest.kt
git commit -m "refactor: wire jpa uow to generated id registry"
```

## Task 16: Delete The Legacy Application-Side Annotation Runtime

**Depends on:** Task 15

**Files:**

- Delete: `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/id/ApplicationSideId.kt`
- Delete: `ddd-domain-repo-jpa/src/main/kotlin/com/only4/cap4k/ddd/application/JpaApplicationSideIdSupport.kt`
- Delete: `ddd-domain-repo-jpa/src/test/kotlin/com/only4/cap4k/ddd/application/JpaApplicationSideIdSupportTest.kt`
- Delete: `cap4k-ddd-starter/src/test/kotlin/com/only4/cap4k/ddd/runtime/ApplicationSideIdJpaRuntimeTest.kt`
- Delete: `cap4k-ddd-starter/src/test/kotlin/com/only4/cap4k/test/runtime/appsideid/ApplicationSideIdRuntimeFixtures.kt`
- Modify: `ddd-core/src/test/kotlin/com/only4/cap4k/ddd/core/domain/id/IdPolicyCoreTest.kt`
- Modify: `ddd-domain-repo-jpa/src/test/kotlin/com/only4/cap4k/ddd/application/JpaUnitOfWorkTest.kt`

**Interfaces:**

- Deletes: the complete annotation-based entity-ID contract.
- Preserves: `IdentifierStrategy`, `IdentifierStrategyRegistry`, `IdentifierGenerator`, built-in strategies, and their tests.
- Migration rule: useful behavior tests move to generated-style accessor fixtures; annotation fixtures are not retained.

- [ ] **Step 1: Move the useful preassignment assertions before deleting fixtures.**

Replace `ApplicationSideLongEntity` tests in `JpaUnitOfWorkTest` with `StrongRootEntity` plus its generated accessor. Keep these behavior assertions:

```kotlin
// CREATE with a preassigned Strong ID preserves it and never checks database existence.
// CREATE assignment is visible to beforeTransaction interceptors.
// observed EXISTING with Strong ID merges without reporting a clean update.
```

Do not add a generic annotation replacement.

- [ ] **Step 2: Delete the named production and runtime fixture files.**

Use `apply_patch` with one explicit `*** Delete File` entry for each path below; do not delete a directory or use a wildcard:

```text
ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/id/ApplicationSideId.kt
ddd-domain-repo-jpa/src/main/kotlin/com/only4/cap4k/ddd/application/JpaApplicationSideIdSupport.kt
ddd-domain-repo-jpa/src/test/kotlin/com/only4/cap4k/ddd/application/JpaApplicationSideIdSupportTest.kt
cap4k-ddd-starter/src/test/kotlin/com/only4/cap4k/ddd/runtime/ApplicationSideIdJpaRuntimeTest.kt
cap4k-ddd-starter/src/test/kotlin/com/only4/cap4k/test/runtime/appsideid/ApplicationSideIdRuntimeFixtures.kt
```

- [ ] **Step 3: Remove only the annotation test from `IdPolicyCoreTest`.**

Delete `application side annotation still exposes strategy name`, `AnnotatedEntity`, and the now-unused `UUID` import. Keep all registry/generator/capability tests unchanged.

- [ ] **Step 4: Run focused module tests and zero-residue scan.**

```powershell
.\gradlew.bat :ddd-core:test :ddd-domain-repo-jpa:test :cap4k-ddd-starter:test --no-daemon
rg -n 'ApplicationSideId|JpaApplicationSideIdSupport' ddd-core/src ddd-domain-repo-jpa/src cap4k-ddd-starter/src
```

Expected: tests PASS; scan has no matches.

- [ ] **Step 5: Commit deletions and migrated tests.**

```powershell
git add -A ddd-core/src ddd-domain-repo-jpa/src cap4k-ddd-starter/src
git commit -m "refactor: delete legacy application side id runtime"
```

Before committing, run `git diff --cached --stat` and confirm no unrelated file under those module trees was staged.

## Task 17: Lock The Registry-Only UoW Lifecycle Timing

**Depends on:** Task 16

**Files:**

- Modify: `ddd-domain-repo-jpa/src/test/kotlin/com/only4/cap4k/ddd/application/JpaUnitOfWorkTest.kt`
- Modify: `cap4k-ddd-starter/src/test/kotlin/com/only4/cap4k/ddd/runtime/strongid/StrongIdUowRuntimeTest.kt`

**Interfaces:**

- Consumes: generated-style root/child accessors and catalog-backed registry.
- Produces: ID-ready guarantees at `persist` enrollment, interceptor boundaries, and final save.
- Preserves: observed IDs and database-identity provider timing.

- [ ] **Step 1: Add or rename focused UoW tests to express the final contract.**

The test class must contain and prove all cases below:

```kotlin
@Test fun `CREATE persist completes registered root before returning`()
@Test fun `CREATE persist completes every reachable registered child before returning`()
@Test fun `CREATE persist preserves preassigned registered ids`()
@Test fun `EXISTING persist preserves observed root and child ids`()
@Test fun `EXISTING persist completes only newly reachable registered children`()
@Test fun `completion is idempotent across persist and save`()
@Test fun `unregistered database identity entity remains provider managed`()
```

For idempotence, expose `nextCalls` on fixture accessors and assert one call per initially missing entity after `persist` plus `save`.

- [ ] **Step 2: Run the focused tests.**

```powershell
.\gradlew.bat :ddd-domain-repo-jpa:test --tests "com.only4.cap4k.ddd.application.JpaUnitOfWorkTest" --no-daemon
```

Expected: PASS. Any failure here belongs to Task 15's registry cutover; do not begin reconciliation until it passes.

- [ ] **Step 3: Add starter runtime persistence/reload evidence.**

In `StrongIdUowRuntimeTest`, use generated-style accessors/catalogs and assert:

```kotlin
unitOfWork.persist(root, PersistIntent.CREATE)
assertTrue(root.hasAssignedId())
assertTrue(root.children.all { it.hasAssignedId() })

unitOfWork.save()
entityManager.clear()
val loaded = repository.findById(root.id).orElseThrow()
assertEquals(root.id, loaded.id)
assertEquals(root.children.map { it.id }, loaded.children.map { it.id })
```

No test fixture may expose `StrongId.new()`.

- [ ] **Step 4: Run GREEN runtime tests and commit.**

```powershell
.\gradlew.bat :ddd-domain-repo-jpa:test --no-daemon
.\gradlew.bat :cap4k-ddd-starter:test --tests "com.only4.cap4k.ddd.runtime.strongid.StrongIdUowRuntimeTest" --no-daemon
git add ddd-domain-repo-jpa/src/test/kotlin/com/only4/cap4k/ddd/application/JpaUnitOfWorkTest.kt cap4k-ddd-starter/src/test/kotlin/com/only4/cap4k/ddd/runtime/strongid/StrongIdUowRuntimeTest.kt
git commit -m "test: lock generated id uow timing"
```

## Task 18: Reconcile Pending Children Before Persistence Sets

**Depends on:** Task 17

**Files:**

- Modify: `ddd-domain-repo-jpa/src/main/kotlin/com/only4/cap4k/ddd/application/JpaUnitOfWork.kt`
- Modify: `ddd-domain-repo-jpa/src/test/kotlin/com/only4/cap4k/ddd/application/JpaUnitOfWorkTest.kt`

**Interfaces:**

- Consumes: drained pending entries and existing bounded owned traversal.
- Produces: an ordered list containing top-level pending entries only.
- Ordering guarantee: reconciliation occurs before processing, interceptor, persisted, removed, and JPA application sets are constructed.

- [ ] **Step 1: Change the existing root-first failure test into a RED convergence test.**

Replace `save rejects a pending owned child that is also reachable from a pending root` with:

```kotlin
@Test
fun `root first pending child is absorbed into the root entry`() {
    val root = StrongRootEntity()
    val child = StrongChildEntity()
    root.children += child

    jpaUnitOfWork.persist(root, PersistIntent.CREATE)
    jpaUnitOfWork.persist(child, PersistIntent.CREATE)
    jpaUnitOfWork.save()

    verify { entityManager.persist(root) }
    verify(exactly = 0) { entityManager.persist(child) }
    verify {
        interceptor1.beforeTransaction(
            match<Set<Any>> { it.size == 1 && it.single() === root },
            emptySet(),
        )
    }
    verify(exactly = 0) { persistListenerManager.onChange(child, any()) }
    assertTrue(child.hasAssignedId())
}
```

- [ ] **Step 2: Add the child-first RED test.**

```kotlin
@Test
fun `child first registration converges to the same root only entry`() {
    val root = StrongRootEntity()
    val child = StrongChildEntity()
    root.children += child

    jpaUnitOfWork.persist(child, PersistIntent.CREATE)
    jpaUnitOfWork.persist(root, PersistIntent.CREATE)
    jpaUnitOfWork.save()

    verify { entityManager.persist(root) }
    verify(exactly = 0) { entityManager.persist(child) }
    verify {
        interceptor1.beforeTransaction(
            match<Set<Any>> { it.size == 1 && it.single() === root },
            emptySet(),
        )
    }
}
```

- [ ] **Step 3: Run RED tests.**

```powershell
.\gradlew.bat :ddd-domain-repo-jpa:test --tests "com.only4.cap4k.ddd.application.JpaUnitOfWorkTest.root first pending child is absorbed into the root entry" --tests "com.only4.cap4k.ddd.application.JpaUnitOfWorkTest.child first registration converges to the same root only entry" --no-daemon
```

Expected: both FAIL through the current `validatePendingOwnedChildConflicts` path.

- [ ] **Step 4: Add index-based ownership analysis.**

Add these private helpers to `JpaUnitOfWork`:

```kotlin
private data class PendingOwnership(
    val ownersByChildIndex: Map<Int, Set<Int>>,
    val reachableByOwnerIndex: Map<Int, List<Any>>,
)

private fun analyzePendingOwnership(entries: List<UnitOfWorkEntry>): PendingOwnership {
    val activeIndexes = entries.indices.filter { index ->
        entries[index].kind == UnitOfWorkEntryKind.CREATE ||
            entries[index].kind == UnitOfWorkEntryKind.EXISTING
    }
    val reachableByOwner = activeIndexes.associateWith { index ->
        ownedRelationTraversal.reachableOwnedEntities(entries[index].entity)
    }
    val ownersByChild = linkedMapOf<Int, LinkedHashSet<Int>>()

    activeIndexes.forEach { ownerIndex ->
        val descendants = reachableByOwner.getValue(ownerIndex).drop(1)
        activeIndexes.filter { it != ownerIndex }.forEach { childIndex ->
            if (descendants.any { samePersistentEntity(it, entries[childIndex].entity) }) {
                ownersByChild.getOrPut(childIndex, ::linkedSetOf).add(ownerIndex)
            }
        }
    }
    return PendingOwnership(ownersByChild, reachableByOwner)
}

private fun outermostOwners(
    ownerIndexes: Set<Int>,
    entries: List<UnitOfWorkEntry>,
    reachableByOwnerIndex: Map<Int, List<Any>>,
): List<Int> = ownerIndexes.filter { candidateIndex ->
    ownerIndexes.none { otherIndex ->
        otherIndex != candidateIndex &&
            reachableByOwnerIndex.getValue(otherIndex).drop(1).any {
                samePersistentEntity(it, entries[candidateIndex].entity)
            }
    }
}
```

Indexes avoid user-defined entity `equals/hashCode` affecting pending ownership.

- [ ] **Step 5: Replace conflict failure with reconciliation.**

Add:

```kotlin
private fun reconcilePendingOwnedChildren(entries: List<UnitOfWorkEntry>): List<UnitOfWorkEntry> {
    val ownership = analyzePendingOwnership(entries)
    val absorbedIndexes = linkedSetOf<Int>()

    ownership.ownersByChildIndex.forEach { (childIndex, ownerIndexes) ->
        val outermost = outermostOwners(
            ownerIndexes = ownerIndexes,
            entries = entries,
            reachableByOwnerIndex = ownership.reachableByOwnerIndex,
        )
        check(outermost.size == 1) {
            val childType = persistentEntityClass(entries[childIndex].entity).name
            val roots = outermost.joinToString { persistentEntityClass(entries[it].entity).name }
            "pending owned child $childType is reachable from multiple unrelated pending roots: $roots"
        }
        absorbedIndexes += childIndex
    }

    return entries.filterIndexed { index, _ -> index !in absorbedIndexes }
}
```

At the start of `save`, replace the current first lines with:

```kotlin
val currentProcessedEntitySet = InsertionOrderedIdentitySet<Any>()
val drainedEntries = pendingEntriesThreadLocal.get().drain()
val pendingEntries = reconcilePendingOwnedChildren(drainedEntries)
pendingEntries.forEach { pushProcessingEntity(it.entity, currentProcessedEntitySet) }
```

Construct `persistEntitySet` and `deleteEntitySet` only after this block. Remove the pre-transaction call to `validatePendingOwnedChildConflicts`.

- [ ] **Step 6: Retain late interceptor protection without late reconciliation.**

Rename the old validator to `validateNoLatePendingOwnedChildEntries` and call it only after `preInTransaction`. At that point sets were already exposed, so a relation mutation that newly makes one pending root reachable from another is rejected; it is not silently reconciled after interceptor input construction.

- [ ] **Step 7: Run GREEN UoW tests and commit.**

```powershell
.\gradlew.bat :ddd-domain-repo-jpa:test --tests "com.only4.cap4k.ddd.application.JpaUnitOfWorkTest" --no-daemon
git add ddd-domain-repo-jpa/src/main/kotlin/com/only4/cap4k/ddd/application/JpaUnitOfWork.kt ddd-domain-repo-jpa/src/test/kotlin/com/only4/cap4k/ddd/application/JpaUnitOfWorkTest.kt
git commit -m "feat: reconcile pending owned children into roots"
```

## Task 19: Prove Nested Ambiguous Observed And Remove Boundaries

**Depends on:** Task 18

**Files:**

- Modify: `ddd-domain-repo-jpa/src/test/kotlin/com/only4/cap4k/ddd/application/JpaUnitOfWorkTest.kt`
- Modify only if a test exposes a defect: `ddd-domain-repo-jpa/src/main/kotlin/com/only4/cap4k/ddd/application/JpaUnitOfWork.kt`

**Interfaces:**

- Consumes: Task 18 ownership analysis.
- Produces: deterministic boundary behavior without new metadata.
- Must preserve: isolated caller-declared CREATE as a top-level entry when no pending owner can be proven.

- [ ] **Step 1: Add a nested outermost-root test.**

Use root -> child -> grandchild supported owning relations. Register all three in reverse order and assert only the outer root is passed to interceptors/JPA; both descendants have IDs and no independent persist/merge calls.

```kotlin
jpaUnitOfWork.persist(grandchild, PersistIntent.CREATE)
jpaUnitOfWork.persist(child, PersistIntent.CREATE)
jpaUnitOfWork.persist(root, PersistIntent.CREATE)
jpaUnitOfWork.save()

verify { entityManager.persist(root) }
verify(exactly = 0) { entityManager.persist(child) }
verify(exactly = 0) { entityManager.persist(grandchild) }
```

- [ ] **Step 2: Add a deterministic unrelated-roots failure test.**

Attach the same child instance to two unrelated pending roots, register child and both roots, then assert:

```kotlin
val error = assertThrows(IllegalStateException::class.java) { jpaUnitOfWork.save() }
assertTrue(error.message!!.contains("multiple unrelated pending roots"))
assertTrue(error.message!!.contains(StrongChildEntity::class.java.name))
verify(exactly = 0) { entityManager.persist(any()) }
verify(exactly = 0) { entityManager.flush() }
```

Repeat with reversed root registration order and assert the same failure category. The listed root order follows pending insertion order.

- [ ] **Step 3: Keep the isolated CREATE boundary explicit.**

Add:

```kotlin
@Test
fun `isolated CREATE with no pending owner remains caller declared top level`() {
    val child = StrongChildEntity()

    jpaUnitOfWork.persist(child, PersistIntent.CREATE)
    jpaUnitOfWork.save()

    verify { entityManager.persist(child) }
}
```

Do not add a static child marker to reject it.

- [ ] **Step 4: Preserve observed-child and remove rejection.**

Keep and rerun the existing tests:

```kotlin
persistShouldRejectRepositoryObservedOwnedChild()
removeShouldRejectRepositoryObservedOwnedChild()
```

Add a pending-root direct-remove test when no baseline exists. Before applying REMOVE entries, use Task 18 ownership analysis to reject a REMOVE entity reachable from an active pending root with this message category:

```text
UnitOfWork.remove cannot register an owned child while its aggregate root is pending
```

Add this complete validation and call it in `reconcilePendingOwnedChildren` before filtering absorbed active entries:

```kotlin
private fun validateNoPendingOwnedChildRemoval(
    entries: List<UnitOfWorkEntry>,
    reachableByOwnerIndex: Map<Int, List<Any>>,
) {
    val removeIndexes = entries.indices.filter { entries[it].kind == UnitOfWorkEntryKind.REMOVE }
    removeIndexes.forEach { removeIndex ->
        val owners = reachableByOwnerIndex.filterValues { reachable ->
            reachable.drop(1).any { samePersistentEntity(it, entries[removeIndex].entity) }
        }.keys
        check(owners.isEmpty()) {
            "UnitOfWork.remove cannot register an owned child while its aggregate root is pending: " +
                persistentEntityClass(entries[removeIndex].entity).name
        }
    }
}
```

Invoke it with `ownership.reachableByOwnerIndex`. Do not mutate the root relation and do not turn child removal into root removal.

- [ ] **Step 5: Preserve late interceptor mutation rejection.**

Retain `saveShouldRejectPendingStandaloneChildAddedToRootByPreInTransaction`. Its expected message changes from the old public-child wording to:

```text
pending ownership changed after UnitOfWork interceptor input was constructed
```

This is the only post-set-construction conflict path.

- [ ] **Step 6: Run the full JPA module and commit.**

```powershell
.\gradlew.bat :ddd-domain-repo-jpa:test --no-daemon
git add ddd-domain-repo-jpa/src/main/kotlin/com/only4/cap4k/ddd/application/JpaUnitOfWork.kt ddd-domain-repo-jpa/src/test/kotlin/com/only4/cap4k/ddd/application/JpaUnitOfWorkTest.kt
git commit -m "test: lock pending owned child boundaries"
```

## Task 20: Plan One Typed Accessor Per Eligible Entity And One Module Catalog

**Depends on:** Tasks 4, 7, and 11

**Files:**

- Create: `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/GeneratedOwnIdPlanning.kt`
- Create: `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/GeneratedOwnIdArtifactPlanner.kt`
- Modify: `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateArtifactPlanner.kt`
- Modify: `cap4k-plugin-pipeline-generator-aggregate/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateArtifactPlannerTest.kt`

**Interfaces:**

- Consumes: complete application-side own `StrongIdModel` owner/backing metadata.
- Produces: one accessor artifact per eligible entity and zero/one catalog artifact per generation target.
- Excludes: database identity, aggregate references, standalone references, composite IDs, incomplete owner metadata.

- [ ] **Step 1: Add artifact selection and ownership tests.**

Create a model containing UUID7 String/UUID and Snowflake String/Long own IDs, one database-identity entity, one aggregate reference, and one standalone reference. Assert:

```kotlin
val accessors = plan.filter { it.templateId == "aggregate/generated_own_id_accessor.kt.peb" }
val catalogs = plan.filter { it.templateId == "aggregate/generated_own_id_catalog.kt.peb" }

assertEquals(4, accessors.size)
assertEquals(1, catalogs.size)
assertEquals(
    listOf("LineGeneratedOwnIdAccessor", "OrderGeneratedOwnIdAccessor", "PaymentGeneratedOwnIdAccessor", "ShipmentGeneratedOwnIdAccessor"),
    accessors.map { it.context.getValue("typeName") as String }.sorted(),
)
assertEquals("GeneratedOwnIdCatalogContribution", catalogs.single().context["typeName"])
assertEquals(ConflictPolicy.OVERWRITE, catalogs.single().conflictPolicy)
assertEquals(ArtifactOutputKind.GENERATED_SOURCE, catalogs.single().outputKind)
assertEquals("orderId", accessors.single { it.context["entityName"] == "Order" }.context["idFieldName"])
```

Assert an empty eligible set emits no catalog. Assert incomplete owner metadata fails with the Strong ID FQN in the message rather than silently omitting it.

- [ ] **Step 2: Run the planner RED test.**

```powershell
.\gradlew.bat :cap4k-plugin-pipeline-generator-aggregate:test --tests "com.only4.cap4k.plugin.pipeline.generator.aggregate.AggregateArtifactPlannerTest" --no-daemon
```

Expected: FAIL because no accessor/catalog planner exists.

- [ ] **Step 3: Create the shared eligibility descriptor.**

Create `GeneratedOwnIdPlanning.kt`:

```kotlin
package com.only4.cap4k.plugin.pipeline.generator.aggregate

import com.only4.cap4k.plugin.pipeline.api.CanonicalModel
import com.only4.cap4k.plugin.pipeline.api.StrongIdKind

internal data class GeneratedOwnIdDescriptor(
    val entityName: String,
    val entityPackageName: String,
    val idFieldName: String,
    val idTypeName: String,
    val idTypeFqn: String,
    val strategy: String,
    val backingType: String,
    val accessorTypeName: String,
) {
    val entityFqn: String = "$entityPackageName.$entityName"
    val accessorFqn: String = "$entityPackageName.$accessorTypeName"
}

internal object GeneratedOwnIdPlanning {
    fun from(model: CanonicalModel): List<GeneratedOwnIdDescriptor> =
        model.strongIds.asSequence()
            .filter { it.kind == StrongIdKind.OWN_ID }
            .filter { it.idStrategy in setOf("uuid7", "snowflake") }
            .filter { it.isEmbeddedId }
            .map { strongId ->
                require(strongId.valueType in setOf("String", "UUID", "Long")) {
                    "unsupported generated own ID backing for ${strongId.packageName}.${strongId.typeName}: ${strongId.valueType}"
                }
                val entityName = requireNotNull(strongId.ownerEntityName) {
                    "missing owner entity for ${strongId.packageName}.${strongId.typeName}"
                }
                val entityPackage = requireNotNull(strongId.ownerEntityPackageName) {
                    "missing owner entity package for ${strongId.packageName}.${strongId.typeName}"
                }
                val entity = requireNotNull(model.entities.singleOrNull {
                    it.name == entityName && it.packageName == entityPackage
                }) {
                    "missing owner entity model for ${strongId.packageName}.${strongId.typeName}"
                }
                require(entity.idField.type.removeSuffix("?").substringAfterLast('.') == strongId.typeName) {
                    "owner ID field ${entity.packageName}.${entity.name}.${entity.idField.name} " +
                        "does not use ${strongId.typeName}"
                }
                requireNotNull(strongId.ownerAggregateName) {
                    "missing owner aggregate for ${strongId.packageName}.${strongId.typeName}"
                }
                requireNotNull(strongId.ownerAggregatePackageName) {
                    "missing owner aggregate package for ${strongId.packageName}.${strongId.typeName}"
                }
                GeneratedOwnIdDescriptor(
                    entityName = entityName,
                    entityPackageName = entityPackage,
                    idFieldName = entity.idField.name,
                    idTypeName = strongId.typeName,
                    idTypeFqn = "${strongId.packageName}.${strongId.typeName}",
                    strategy = requireNotNull(strongId.idStrategy),
                    backingType = strongId.valueType,
                    accessorTypeName = "${entityName}GeneratedOwnIdAccessor",
                )
            }
            .sortedBy { it.entityFqn }
            .toList()
}
```

- [ ] **Step 4: Create the artifact planner.**

Create `GeneratedOwnIdArtifactPlanner.kt`:

```kotlin
package com.only4.cap4k.plugin.pipeline.generator.aggregate

import com.only4.cap4k.plugin.pipeline.api.ArtifactLayoutResolver
import com.only4.cap4k.plugin.pipeline.api.ArtifactPlanItem
import com.only4.cap4k.plugin.pipeline.api.CanonicalModel
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig

internal class GeneratedOwnIdArtifactPlanner : AggregateArtifactFamilyPlanner {
    override fun plan(config: ProjectConfig, model: CanonicalModel): List<ArtifactPlanItem> {
        val artifactLayout = ArtifactLayoutResolver(config.basePackage, config.artifactLayout)
        val descriptors = GeneratedOwnIdPlanning.from(model)
        if (descriptors.isEmpty()) return emptyList()

        val accessors = descriptors.map { descriptor ->
            generatedKotlinArtifact(
                config = config,
                artifactLayout = artifactLayout,
                moduleRole = "domain",
                packageName = descriptor.entityPackageName,
                typeName = descriptor.accessorTypeName,
                templateId = "aggregate/generated_own_id_accessor.kt.peb",
                context = mapOf(
                    "packageName" to descriptor.entityPackageName,
                    "typeName" to descriptor.accessorTypeName,
                    "entityName" to descriptor.entityName,
                    "entityFqn" to descriptor.entityFqn,
                    "idFieldName" to descriptor.idFieldName,
                    "idTypeName" to descriptor.idTypeName,
                    "idTypeFqn" to descriptor.idTypeFqn,
                    "label" to "${descriptor.entityName}.${descriptor.idFieldName}",
                    "strategy" to descriptor.strategy,
                    "backingType" to descriptor.backingType,
                    "backingTypeFqn" to if (descriptor.backingType == "UUID") "java.util.UUID" else null,
                ),
            )
        }

        val catalogPackage = ArtifactLayoutResolver.joinPackage(config.basePackage, "domain._share.identity")
        val catalogType = "GeneratedOwnIdCatalogContribution"
        val catalog = generatedKotlinArtifact(
            config = config,
            artifactLayout = artifactLayout,
            moduleRole = "domain",
            packageName = catalogPackage,
            typeName = catalogType,
            templateId = "aggregate/generated_own_id_catalog.kt.peb",
            context = mapOf(
                "packageName" to catalogPackage,
                "typeName" to catalogType,
                "beanName" to "$catalogPackage.generatedOwnIdCatalogContribution",
                "accessors" to descriptors.map {
                    mapOf("fqn" to it.accessorFqn, "entityFqn" to it.entityFqn)
                },
            ),
        )
        return accessors + catalog
    }
}
```

- [ ] **Step 5: Register the planner once.**

Add `GeneratedOwnIdArtifactPlanner()` immediately after `StrongIdArtifactPlanner()` in `AggregateArtifactPlanner.delegates`. Do not gate it on the checked-in factory selection flag; accessors/catalog are generated lifecycle infrastructure.

- [ ] **Step 6: Run GREEN tests and verify deterministic artifact metadata.**

```powershell
.\gradlew.bat :cap4k-plugin-pipeline-generator-aggregate:test --no-daemon
```

Expected: PASS. Accessor output paths are entity-package generated paths; catalog output is exactly `<basePackage>/domain/_share/identity/GeneratedOwnIdCatalogContribution.kt` under the generated domain source root.

- [ ] **Step 7: Commit Task 20 only.**

```powershell
git add cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/GeneratedOwnIdPlanning.kt cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/GeneratedOwnIdArtifactPlanner.kt cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateArtifactPlanner.kt cap4k-plugin-pipeline-generator-aggregate/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateArtifactPlannerTest.kt
git commit -m "feat: plan generated own id accessors and catalog"
```

## Task 21: Render Typed Accessors And The Module Catalog

**Depends on:** Task 20

**Files:**

- Create: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/generated_own_id_accessor.kt.peb`
- Create: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/generated_own_id_catalog.kt.peb`
- Modify: `cap4k-plugin-pipeline-renderer-pebble/src/test/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PebbleArtifactRendererTest.kt`

**Interfaces:**

- Consumes: Task 20 exact context.
- Produces: same-package accessor objects and one Spring catalog contribution without `Impl` suffix.
- Allocation call: `EntityId.of(Mediator.identifiers.next("strategy", BackingType::class))`.

- [ ] **Step 1: Add renderer tests for all backing types and catalog collisions.**

Assert exact source lines:

```kotlin
assertTrue(uuidAccessor.contains("Mediator.identifiers.next(\"uuid7\", UUID::class)"))
assertTrue(uuidTextAccessor.contains("Mediator.identifiers.next(\"uuid7\", String::class)"))
assertTrue(snowflakeLongAccessor.contains("Mediator.identifiers.next(\"snowflake\", Long::class)"))
assertTrue(snowflakeTextAccessor.contains("Mediator.identifiers.next(\"snowflake\", String::class)"))
assertTrue(uuidTextAccessor.contains("readInitializedOrNull { entity.orderId }"))
assertTrue(uuidTextAccessor.contains("entity.orderId = id"))
assertTrue(catalog.contains("class GeneratedOwnIdCatalogContribution : GeneratedOwnIdCatalog"))
assertFalse(catalog.contains("GeneratedOwnIdCatalogContributionImpl"))
assertTrue(catalog.contains("@Component(\"com.acme.demo.domain._share.identity.generatedOwnIdCatalogContribution\")"))
```

Use two entities with the same simple name in different packages and assert the catalog emits their accessor FQNs, avoiding import ambiguity.

- [ ] **Step 2: Run the renderer RED test.**

```powershell
.\gradlew.bat :cap4k-plugin-pipeline-renderer-pebble:test --tests "com.only4.cap4k.plugin.pipeline.renderer.pebble.PebbleArtifactRendererTest" --no-daemon
```

Expected: FAIL because both templates are absent.

- [ ] **Step 3: Create the accessor template.**

```pebble
package {{ packageName }}

{{ use("com.only4.cap4k.ddd.core.Mediator") -}}
{{ use("com.only4.cap4k.ddd.core.domain.id.GeneratedOwnIdAccessor") -}}
{{ use("com.only4.cap4k.ddd.core.domain.id.readInitializedOrNull") -}}
{{ use(entityFqn) -}}
{{ use(idTypeFqn) -}}
{% if backingTypeFqn is not null -%}
{{ use(backingTypeFqn) -}}
{% endif -%}
{% for import in imports(imports) -%}
import {{ import }}
{% endfor %}

object {{ typeName }} : GeneratedOwnIdAccessor<{{ entityName }}, {{ idTypeName }}> {
    override val entityType = {{ entityName }}::class
    override val label: String = "{{ label }}"

    override fun current(entity: {{ entityName }}): {{ idTypeName }}? =
        readInitializedOrNull { entity.{{ idFieldName }} }

    override fun assign(entity: {{ entityName }}, id: {{ idTypeName }}) {
        entity.{{ idFieldName }} = id
    }

    override fun next(): {{ idTypeName }} =
        {{ idTypeName }}.of(
            Mediator.identifiers.next("{{ strategy }}", {{ backingType }}::class)
        )
}
```

The accessor is not a Spring bean and has no reflection metadata.

- [ ] **Step 4: Create the catalog template.**

```pebble
package {{ packageName }}

{{ use("com.only4.cap4k.ddd.core.domain.id.GeneratedOwnIdAccessor") -}}
{{ use("com.only4.cap4k.ddd.core.domain.id.GeneratedOwnIdCatalog") -}}
{{ use("org.springframework.stereotype.Component") -}}
{% for import in imports(imports) -%}
import {{ import }}
{% endfor %}

@Component("{{ beanName }}")
class {{ typeName }} : GeneratedOwnIdCatalog {
    override val accessors: List<GeneratedOwnIdAccessor<*, *>> = listOf(
{% for accessor in accessors -%}
        {{ accessor.fqn }}{% if not loop.last %},{% endif %}
{% endfor -%}
    )
}
```

- [ ] **Step 5: Compile rendered accessors/catalog together.**

Use the renderer test's `KotlinCompilation` helper with `inheritClassPath = true`. Compile four accessors, their entity/Strong ID fixtures, and one catalog in one invocation. Assert `KotlinCompilation.ExitCode.OK`; this specifically proves same-package `internal set` access and catalog FQN syntax.

- [ ] **Step 6: Run GREEN tests and scan forbidden shapes.**

```powershell
.\gradlew.bat :cap4k-plugin-pipeline-renderer-pebble:test --no-daemon
rg -n 'Impl|java\.lang\.reflect|@Component' cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/generated_own_id_accessor.kt.peb
```

Expected: tests PASS; accessor scan has no matches. The catalog is expected to contain `@Component`.

- [ ] **Step 7: Commit Task 21 only.**

```powershell
git add cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/generated_own_id_accessor.kt.peb cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/generated_own_id_catalog.kt.peb cap4k-plugin-pipeline-renderer-pebble/src/test/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PebbleArtifactRendererTest.kt
git commit -m "feat: render generated own id infrastructure"
```

## Task 22: Remove Application-Side Own IDs From Constructors And Factory Payloads

**Depends on:** Tasks 8, 20, and 21

**Files:**

- Modify: `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/EntityArtifactPlanner.kt`
- Modify: `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/FactoryArtifactPlanner.kt`
- Modify: `cap4k-plugin-pipeline-generator-aggregate/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateArtifactPlannerTest.kt`
- Modify: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/entity.kt.peb`
- Modify: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/factory.kt.peb`
- Modify: `cap4k-plugin-pipeline-renderer-pebble/src/test/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PebbleArtifactRendererTest.kt`

**Interfaces:**

- Consumes: `GeneratedOwnIdPlanning` eligibility.
- Produces: ID-less application-side entity constructors/payloads and `lateinit` own-ID properties.
- Preserves: database identity constructor behavior and business/reference Strong ID inputs.

- [ ] **Step 1: Add planner assertions for root and owned-child entities.**

For UUID7 and Snowflake entities assert:

```kotlin
val entityContext = entityArtifact.context
val constructorFields = entityContext["constructorFields"] as List<Map<String, Any?>>
val scalarFields = entityContext["scalarFields"] as List<Map<String, Any?>>
assertFalse(constructorFields.any { it["name"] == "id" })
assertEquals(true, scalarFields.single { it["name"] == "id" }["generatedOwnId"])

val factoryContext = factoryArtifact.context
assertFalse((factoryContext["payloadFields"] as List<Map<String, Any?>>).any { it["name"] == "id" })
assertFalse(factoryContext.containsKey("ownIdInitializer"))
assertFalse(factoryContext.containsKey("ownIdFieldName"))
assertFalse(factoryContext.containsKey("ownIdTypeRef"))
```

Add a database identity case and assert its constructor/property context is unchanged.

- [ ] **Step 2: Run generator RED tests.**

```powershell
.\gradlew.bat :cap4k-plugin-pipeline-generator-aggregate:test --tests "com.only4.cap4k.plugin.pipeline.generator.aggregate.AggregateArtifactPlannerTest" --no-daemon
```

Expected: FAIL because entity constructors still include every scalar and factories emit `OwnId.new()`.

- [ ] **Step 3: Add explicit entity constructor-field context.**

At planner entry, index descriptors:

```kotlin
val generatedOwnIdsByEntity = GeneratedOwnIdPlanning.from(model).associateBy { it.entityFqn }
```

For each scalar field compute:

```kotlin
val generatedOwnId =
    generatedOwnIdsByEntity["${entity.packageName}.${entity.name}"] != null &&
        field.name == entity.idField.name
```

Put `"generatedOwnId" to generatedOwnId` into the field map. Publish:

```kotlin
"constructorFields" to scalarFields.filterNot { it["generatedOwnId"] == true },
```

- [ ] **Step 4: Change only constructor/property rendering for generated own IDs.**

The class constructor loops over `constructorFields`, not `scalarFields`. In the scalar property block use:

```pebble
{% if field.generatedOwnId %}    lateinit var {{ field.name }}: {{ type(field) | raw }}
        internal set
{% else %}    var {{ field.name }}: {{ type(field) | raw }}{% if field.nullable %}?{% endif %} = {{ field.name }}
        internal set
{% endif %}
```

Keep all JPA annotations before this branch. Long-backed entity IDs are Strong ID objects and therefore also use `lateinit`; only the embeddable inner `Long` uses `0L`.

- [ ] **Step 5: Delete factory allocation contexts and rendering.**

Keep `resolveOwnStrongId` only for excluding the ID from required constructor mapping. Delete `ownIdFieldName`, `ownIdInitializer`, `ownIdTypeRef`, their import contribution, and context keys. Delete this block from `factory.kt.peb`:

```pebble
{% if ownIdInitializer is not null -%}
{{ "            " }}{{ ownIdFieldName }} = {{ ownIdInitializer }}{% if constructorPayloadFields|length > 0 %},{% endif %}
{% endif -%}
```

The generated constructor call contains only `constructorPayloadFields`.

- [ ] **Step 6: Run renderer compile tests.**

```powershell
.\gradlew.bat :cap4k-plugin-pipeline-generator-aggregate:test :cap4k-plugin-pipeline-renderer-pebble:test --no-daemon
```

Expected: PASS. Rendered application-side entities contain `lateinit var id`; rendered factories contain neither an ID argument nor allocation call.

- [ ] **Step 7: Scan and commit.**

```powershell
rg -n 'ownIdInitializer|ownIdFieldName|ownIdTypeRef|\.new\(\)' cap4k-plugin-pipeline-generator-aggregate/src cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/factory.kt.peb
```

Expected: no matches for the deleted contexts or Strong ID allocation.

```powershell
git add cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/EntityArtifactPlanner.kt cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/FactoryArtifactPlanner.kt cap4k-plugin-pipeline-generator-aggregate/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateArtifactPlannerTest.kt cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/entity.kt.peb cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/factory.kt.peb cap4k-plugin-pipeline-renderer-pebble/src/test/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PebbleArtifactRendererTest.kt
git commit -m "refactor: remove generated own ids from construction"
```

## Task 23: Lock Aggregate Factory Return-Time ID Readiness

**Depends on:** Tasks 17 and 22

**Files:**

- Modify: `ddd-core/src/test/kotlin/com/only4/cap4k/ddd/core/domain/aggregate/impl/DefaultAggregateFactorySupervisorTest.kt`

**Interfaces:**

- Consumes: an aggregate factory returning an unassigned graph and UoW CREATE enrollment.
- Produces: observable proof that `create` returns after `persist` with `PersistIntent.CREATE` completes.
- Production expectation: `DefaultAggregateFactorySupervisor` already has the correct order; change it only if the test disproves that evidence.

- [ ] **Step 1: Add the return-time readiness test.**

Add these private nested fixtures to the test class:

```kotlin
private data class ReadyChild(var id: String? = null)
private data class ReadyRoot(
    var id: String? = null,
    val children: MutableList<ReadyChild>,
)
private data class ReadyPayload(val childCount: Int) : AggregatePayload<ReadyRoot>

private class ReadyAggregateFactory : AggregateFactory<ReadyPayload, ReadyRoot> {
    override fun create(entityPayload: ReadyPayload): ReadyRoot =
        ReadyRoot(children = MutableList(entityPayload.childCount) { ReadyChild() })
}
```

Then add this test:

```kotlin
@Test
fun `create returns only after UoW makes the aggregate graph id ready`() {
    val uow = mockk<UnitOfWork>()
    every { uow.persist(any(), PersistIntent.CREATE) } answers {
        firstArg<ReadyRoot>().also { root ->
            root.id = "ROOT-1"
            root.children.forEachIndexed { index, child -> child.id = "CHILD-${index + 1}" }
        }
        Unit
    }
    val supervisor = DefaultAggregateFactorySupervisor(listOf(ReadyAggregateFactory()), uow)

    val result = supervisor.create(ReadyPayload(2))

    assertEquals("ROOT-1", result.id)
    assertEquals(listOf("CHILD-1", "CHILD-2"), result.children.map { it.id })
    verify(exactly = 1) { uow.persist(result, PersistIntent.CREATE) }
}
```

- [ ] **Step 2: Run the focused test.**

```powershell
.\gradlew.bat :ddd-core:test --tests "com.only4.cap4k.ddd.core.domain.aggregate.impl.DefaultAggregateFactorySupervisorTest.create returns only after UoW makes the aggregate graph id ready" --no-daemon
```

Expected: PASS against current production order. A failure means the supervisor contract changed and must be restored to factory -> UoW CREATE -> return.

- [ ] **Step 3: Commit the regression evidence.**

```powershell
git add ddd-core/src/test/kotlin/com/only4/cap4k/ddd/core/domain/aggregate/impl/DefaultAggregateFactorySupervisorTest.kt
git commit -m "test: lock factory id readiness timing"
```

## Task 24: Inject Owned Child IDs Before Relation Mutation

**Depends on:** Tasks 13, 21, and 22

**Files:**

- Modify: `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateRelationPlanning.kt`
- Modify: `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/EntityArtifactPlanner.kt`
- Modify: `cap4k-plugin-pipeline-generator-aggregate/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateArtifactPlannerTest.kt`
- Modify: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/entity.kt.peb`
- Modify: `cap4k-plugin-pipeline-renderer-pebble/src/test/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PebbleArtifactRendererTest.kt`
- Modify: `cap4k-ddd-starter/src/test/kotlin/com/only4/cap4k/ddd/runtime/OwnedEntityListJpaRuntimeTest.kt`

**Interfaces:**

- Consumes: owning relation target and Task 20 descriptor index.
- Produces: direct typed accessor call passed to Task 13's hook.
- Excludes: database identity, inverse, non-owned, many-to-one, aggregate reference, and unregistered target relations.

- [ ] **Step 1: Add positive and negative relation-context tests.**

For eligible owned-many and owned-one assert:

```kotlin
assertEquals(
    "com.acme.demo.domain.aggregates.order.OrderLineGeneratedOwnIdAccessor",
    relation["generatedOwnIdAccessorFqn"],
)
```

For each excluded relation category assert the key is null. Include an owned database-identity child so ownership alone cannot activate the hook.

- [ ] **Step 2: Run planner RED tests.**

```powershell
.\gradlew.bat :cap4k-plugin-pipeline-generator-aggregate:test --tests "com.only4.cap4k.plugin.pipeline.generator.aggregate.AggregateArtifactPlannerTest" --no-daemon
```

Expected: FAIL because relation planning has no descriptor input or hook context.

- [ ] **Step 3: Pass the descriptor index into relation planning.**

Change `planFor` to accept:

```kotlin
generatedOwnIdsByEntity: Map<String, GeneratedOwnIdDescriptor>,
```

Call it from `EntityArtifactPlanner` with:

```kotlin
val generatedOwnIdsByEntity = GeneratedOwnIdPlanning.from(model).associateBy { it.entityFqn }
```

For each owning relation compute:

```kotlin
val generatedOwnIdAccessorFqn = if (relation.owned) {
    generatedOwnIdsByEntity["${relation.targetEntityPackageName}.${relation.targetEntityName}"]?.accessorFqn
} else {
    null
}
```

Add it to the owner relation field map. Inverse relation maps always use null.

- [ ] **Step 4: Render the hook for owned-many and owned-one facades.**

For every generated `OwnedEntityList.of` call, append this conditional argument:

```pebble
{% if relation.generatedOwnIdAccessorFqn is not null %}, { entity ->
                {{ relation.generatedOwnIdAccessorFqn }}.assignIfMissing(entity)
            }{% endif %}
```

Apply the same argument in the owned-one getter and setter `replace` path. The lambda is evaluated by `OwnedEntityList` before mutation, so a thrown allocator preserves the old relation.

- [ ] **Step 5: Add rendered compile and runtime timing tests.**

Compile an entity with owned-one and owned-many targets plus generated accessors. In `OwnedEntityListJpaRuntimeTest`, assert:

```kotlin
val child = parent.addChild("line")
assertTrue(child.hasAssignedId())

val replacement = Child.unassigned("replacement")
parent.primaryChild = replacement
assertTrue(replacement.hasAssignedId())
```

Inject a failing fixture accessor and assert the backing collection/old owned-one value is unchanged. Assert preassigned IDs are preserved.

- [ ] **Step 6: Run GREEN tests and boundary scan.**

```powershell
.\gradlew.bat :ddd-core:test --tests "com.only4.cap4k.ddd.core.domain.aggregate.OwnedEntityListTest" --no-daemon
.\gradlew.bat :cap4k-plugin-pipeline-generator-aggregate:test :cap4k-plugin-pipeline-renderer-pebble:test --no-daemon
.\gradlew.bat :cap4k-ddd-starter:test --tests "com.only4.cap4k.ddd.runtime.OwnedEntityListJpaRuntimeTest" --no-daemon
rg -n 'Mediator|UnitOfWork|Repository|EntityManager|IdentifierGenerator|GeneratedOwnIdRegistry' ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/aggregate/OwnedEntityList.kt
```

Expected: tests PASS; boundary scan has no matches.

- [ ] **Step 7: Commit Task 24 only.**

```powershell
git add cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateRelationPlanning.kt cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/EntityArtifactPlanner.kt cap4k-plugin-pipeline-generator-aggregate/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateArtifactPlannerTest.kt cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/entity.kt.peb cap4k-plugin-pipeline-renderer-pebble/src/test/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PebbleArtifactRendererTest.kt cap4k-ddd-starter/src/test/kotlin/com/only4/cap4k/ddd/runtime/OwnedEntityListJpaRuntimeTest.kt
git commit -m "feat: assign child ids before owned relation mutation"
```

## Task 25: Delete Compiler Inference And Dead Generator Context

**Depends on:** Tasks 16 and 22

**Files:**

- Modify: `cap4k-plugin-code-analysis-compiler/src/main/kotlin/com/only4/cap4k/plugin/codeanalysis/compiler/Cap4kIrGenerationExtension.kt`
- Modify: `cap4k-plugin-code-analysis-compiler/src/test/kotlin/com/only4/cap4k/plugin/codeanalysis/compiler/AnalysisOutputCorrectnessTest.kt`
- Modify: `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/EntityArtifactPlanner.kt`
- Modify: `cap4k-plugin-pipeline-generator-aggregate/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateArtifactPlannerTest.kt`
- Modify: `cap4k-plugin-pipeline-renderer-pebble/src/test/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PebbleArtifactRendererTest.kt`

**Interfaces:**

- Deletes: annotation-based implicit aggregate inference and null/false compatibility context.
- Preserves: explicit design index and `@AggregateElement` inference.
- Negative rule: aggregate-like package plus ID-shaped field is not domain-entity evidence.

- [ ] **Step 1: Add a compiler negative test before production deletion.**

Compile a class in `demo.domain.aggregates.category` with `@Entity`, `@EmbeddedId`, and an `id` field but no `@AggregateElement`. Invoke a top-level extension from a handler and assert no `AggregateToEntityMethod` or `CommandHandlerToEntityMethod` edge treats that class as an aggregate entity.

- [ ] **Step 2: Remove annotation inference from the compiler.**

Delete:

```kotlin
private val applicationSideIdAnn = FqName("com.only4.cap4k.ddd.core.domain.id.ApplicationSideId")
```

Change aggregate lookup to:

```kotlin
return aggregateInfoCache.getOrPut(fq) {
    index.aggregateInfoByClass[fq]
        ?: readAggregateElementInfo(aggregateElementAnn)
}
```

Delete `inferGeneratedEntityAggregateInfo` and `hasApplicationSideIdMember` completely.

- [ ] **Step 3: Delete synthetic annotation sources and implicit-inference assertions.**

Remove both synthetic `SourceFile.kotlin` fixture blocks named `ApplicationSideId.kt`. Delete tests named `top level behavior on generated style entity keeps exact domain event edge` and `command handler calling cross module top level behavior extension on generated style entity emits exact entity method edges`; current source evidence shows both depend on implicit annotation inference. Retain the two explicit `@AggregateElement` variants.

- [ ] **Step 4: Delete dead entity planner context.**

Delete `applicationSideIdStrategy`, its insertable/updatable branches, the field key, and root `hasApplicationSideIdFields`. Remove those keys from renderer test fixtures rather than retaining null/false values. The generated own-ID field behavior now uses `generatedOwnId` from Task 22.

- [ ] **Step 5: Run GREEN tests and zero-residue scan.**

```powershell
.\gradlew.bat :cap4k-plugin-code-analysis-compiler:test :cap4k-plugin-pipeline-generator-aggregate:test :cap4k-plugin-pipeline-renderer-pebble:test --no-daemon
rg -n 'ApplicationSideId|applicationSideIdAnn|inferGeneratedEntityAggregateInfo|hasApplicationSideIdMember|applicationSideIdStrategy|hasApplicationSideIdFields' cap4k-plugin-code-analysis-compiler/src cap4k-plugin-pipeline-generator-aggregate/src cap4k-plugin-pipeline-renderer-pebble/src
```

Expected: tests PASS; scan has no matches.

- [ ] **Step 6: Commit Task 25 only.**

```powershell
git add cap4k-plugin-code-analysis-compiler/src/main/kotlin/com/only4/cap4k/plugin/codeanalysis/compiler/Cap4kIrGenerationExtension.kt cap4k-plugin-code-analysis-compiler/src/test/kotlin/com/only4/cap4k/plugin/codeanalysis/compiler/AnalysisOutputCorrectnessTest.kt cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/EntityArtifactPlanner.kt cap4k-plugin-pipeline-generator-aggregate/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateArtifactPlannerTest.kt cap4k-plugin-pipeline-renderer-pebble/src/test/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PebbleArtifactRendererTest.kt
git commit -m "refactor: remove legacy id inference context"
```

## Task 26: Compile And Run The Cross-Layer Generated Consumer Matrix

**Depends on:** Tasks 9, 19, 21, 23, 24, and 25

**Files:**

- Modify: `cap4k-plugin-pipeline-renderer-pebble/src/test/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PebbleArtifactRendererTest.kt`
- Modify: `cap4k-ddd-starter/src/test/kotlin/com/only4/cap4k/ddd/runtime/StrongIdJacksonRuntimeTest.kt`
- Modify: `cap4k-ddd-starter/src/test/kotlin/com/only4/cap4k/ddd/runtime/strongid/StrongIdJpaRuntimeTest.kt`
- Modify: `cap4k-ddd-starter/src/test/kotlin/com/only4/cap4k/ddd/runtime/strongid/StrongIdUowRuntimeTest.kt`
- Modify: `cap4k-ddd-starter/src/test/kotlin/com/only4/cap4k/ddd/runtime/OwnedEntityListJpaRuntimeTest.kt`

**Interfaces:**

- Consumes: final planned/rendered artifacts and real framework APIs.
- Produces: compile-time and runtime proof across all four matrix cells and both ID entry points.
- Must not use: drifting local stubs for framework Strong ID/accessor/catalog APIs when `inheritClassPath` can use the real classes.

- [ ] **Step 1: Build one generated-consumer compile fixture.**

Render and compile in one `KotlinCompilation` invocation:

```text
OrderId              uuid7 / String
PaymentId            uuid7 / UUID
OrderLineId          snowflake / Long
ShipmentId           snowflake / String
Order                 application-side root, no ID constructor parameter
OrderLine             owned child, no ID constructor parameter
OrderFactory          no ID payload/allocation
4 generated accessors
1 GeneratedOwnIdCatalogContribution across multiple aggregate packages
owned-many and owned-one relation hook calls
```

Use:

```kotlin
val result = KotlinCompilation().apply {
    sources = renderedSources.mapIndexed { index, source ->
        SourceFile.kotlin("Generated$index.kt", source)
    }
    inheritClassPath = true
    messageOutputStream = System.out
}.compile()

assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
```

Assert the compiled catalog class can be loaded by its exact FQN and its simple name has no `Impl` suffix.

- [ ] **Step 2: Run the compile fixture alone.**

```powershell
.\gradlew.bat :cap4k-plugin-pipeline-renderer-pebble:test --tests "com.only4.cap4k.plugin.pipeline.renderer.pebble.PebbleArtifactRendererTest.generated strong id consumer matrix compiles" --no-daemon
```

Expected: PASS only when template imports, internal setters, accessor generics, catalog FQNs, and factory constructor calls agree.

- [ ] **Step 3: Run the JSON/JPA matrix.**

```powershell
.\gradlew.bat :cap4k-ddd-starter:test --tests "com.only4.cap4k.ddd.runtime.StrongIdJacksonRuntimeTest" --tests "com.only4.cap4k.ddd.runtime.strongid.StrongIdJpaRuntimeTest" --no-daemon
```

Expected: all IDs serialize as strings; numeric Snowflake JSON is rejected; direct character/native UUID/BIGINT mappings persist and reload without converters.

- [ ] **Step 4: Run lifecycle entry-point tests.**

```powershell
.\gradlew.bat :cap4k-ddd-starter:test --tests "com.only4.cap4k.ddd.runtime.strongid.StrongIdUowRuntimeTest" --tests "com.only4.cap4k.ddd.runtime.OwnedEntityListJpaRuntimeTest" --no-daemon
```

Expected: factory/UoW root graphs are ID-ready before return, aggregate relation methods expose child IDs before return, and final save is idempotent.

- [ ] **Step 5: Run pending reconciliation tests with runtime fixtures.**

Exercise root-first, child-first, nested, unrelated-root ambiguity, isolated CREATE, observed child rejection, and direct remove rejection. Assert root-only interceptor/listener/JPA claims for reconciled cases.

```powershell
.\gradlew.bat :ddd-domain-repo-jpa:test --tests "com.only4.cap4k.ddd.application.JpaUnitOfWorkTest" --no-daemon
```

Expected: PASS with one top-level entry for every attached child case, two entries rejected for unrelated-root ambiguity, and the isolated CREATE retained as a top-level entry.

- [ ] **Step 6: Commit only integration evidence added by Task 26.**

```powershell
git add cap4k-plugin-pipeline-renderer-pebble/src/test/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PebbleArtifactRendererTest.kt cap4k-ddd-starter/src/test/kotlin/com/only4/cap4k/ddd/runtime/StrongIdJacksonRuntimeTest.kt cap4k-ddd-starter/src/test/kotlin/com/only4/cap4k/ddd/runtime/strongid/StrongIdJpaRuntimeTest.kt cap4k-ddd-starter/src/test/kotlin/com/only4/cap4k/ddd/runtime/strongid/StrongIdUowRuntimeTest.kt cap4k-ddd-starter/src/test/kotlin/com/only4/cap4k/ddd/runtime/OwnedEntityListJpaRuntimeTest.kt
git commit -m "test: verify generated strong id lifecycle matrix"
```

## Task 27: Run The Final Audit And Record Handoff Evidence

**Depends on:** Tasks 1-26

**Files:**

- Modify: root `AGENTS.md` only if its active identity guidance still describes the removed annotation/name.
- Inspect: all Task 1-26 files and worktree status.
- Do not modify: historical `docs/superpowers/**`, Phase 3.75 docs, or Soft Delete files.

**Interfaces:**

- Consumes: completed task commits and verification output.
- Produces: reproducible completion evidence and an explicit untouched-scope statement.
- Completion rule: no unexecuted check may be reported as passing.

- [ ] **Step 1: Update active root guidance if needed.**

The final active statement must say, in substance:

```text
Application-side entity IDs are generated Strong IDs. Supported strategies are uuid7 and snowflake.
Backing type follows JDBC storage. Generated typed accessors allocate IDs; generated catalogs feed the runtime registry.
```

Do not add compatibility notes for deleted contracts and do not modify skill architecture instructions.

- [ ] **Step 2: Run all focused module tests.**

```powershell
.\gradlew.bat :cap4k-plugin-pipeline-api:test :cap4k-plugin-pipeline-source-db:test :cap4k-plugin-pipeline-core:test :cap4k-plugin-pipeline-generator-aggregate:test :cap4k-plugin-pipeline-renderer-pebble:test :cap4k-plugin-code-analysis-compiler:test :ddd-core:test :ddd-domain-repo-jpa:test :cap4k-ddd-starter:test --no-daemon
```

Expected: PASS.

- [ ] **Step 3: Compile the owned main modules independently.**

```powershell
.\gradlew.bat :cap4k-plugin-pipeline-api:compileKotlin :cap4k-plugin-pipeline-source-db:compileKotlin :cap4k-plugin-pipeline-core:compileKotlin :cap4k-plugin-pipeline-generator-aggregate:compileKotlin :cap4k-plugin-pipeline-renderer-pebble:compileKotlin :cap4k-plugin-code-analysis-compiler:compileKotlin :ddd-core:compileKotlin :ddd-domain-repo-jpa:compileKotlin :cap4k-ddd-starter:compileKotlin --no-daemon
```

Expected: PASS.

- [ ] **Step 4: Run the active zero-residue vocabulary scan.**

```powershell
rg -n 'ApplicationSideId|JpaApplicationSideIdSupport|applicationSideIdStrategy|hasApplicationSideIdFields|snowflake-long|SNOWFLAKE_LONG' `
  ddd-core/src ddd-domain-repo-jpa/src cap4k-ddd-starter/src `
  cap4k-plugin-pipeline-api/src cap4k-plugin-pipeline-source-db/src cap4k-plugin-pipeline-core/src `
  cap4k-plugin-pipeline-generator-aggregate/src cap4k-plugin-pipeline-renderer-pebble/src `
  cap4k-plugin-code-analysis-compiler/src AGENTS.md
```

Expected: no matches. Historical specs/plans are intentionally excluded.

- [ ] **Step 5: Run reflection allocation converter and boundary scans.**

```powershell
rg -n 'getAnnotation\(EmbeddedId|StrongId::class\.java\.isAssignableFrom|getField\("Companion"\)|method\.name == "new"' ddd-domain-repo-jpa/src/main/kotlin
rg -n 'fun new\(|newUuidV7String' ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/id cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate
rg -n 'AttributeConverter' cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/strong_id.kt.peb
rg -n 'Mediator|UnitOfWork|Repository|EntityManager|IdentifierGenerator|GeneratedOwnIdRegistry' ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/aggregate/OwnedEntityList.kt
```

Expected: no matches. Do not remove unrelated value-object converter support from other templates.

- [ ] **Step 6: Verify one-cast and catalog naming invariants.**

```powershell
rg -n 'UNCHECKED_CAST' ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/id/GeneratedOwnId*.kt
rg -n 'GeneratedOwnIdCatalogContributionImpl|class .*Impl' cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/generated_own_id_catalog.kt.peb cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/GeneratedOwnIdArtifactPlanner.kt
```

Expected: exactly one unchecked cast in `MapBackedGeneratedOwnIdRegistry`; no `Impl` matches.

- [ ] **Step 7: Inspect scope against the preexisting dirty worktree.**

```powershell
git status --short
git diff --stat
git diff -- ddd-core ddd-domain-repo-jpa cap4k-ddd-starter cap4k-plugin-pipeline-api cap4k-plugin-pipeline-source-db cap4k-plugin-pipeline-core cap4k-plugin-pipeline-generator-aggregate cap4k-plugin-pipeline-renderer-pebble cap4k-plugin-code-analysis-compiler AGENTS.md
```

Confirm:

- no Soft Delete production/test file changed;
- no completed Phase 3.75 doc changed;
- the preexisting dirty identity roadmap/Phase 3/Phase 3.75 docs were not reverted;
- every deleted legacy file is named in Task 16;
- no compatibility fallback or temporary adapter remains.

- [ ] **Step 8: Record the implementation handoff.**

The final report must include:

```text
Implemented task numbers and commit hashes:
Focused test commands and outcomes:
Main compile command and outcome:
Generated-consumer compile fixture outcome:
Static scan commands and outcomes:
Deleted files:
Skipped checks with reason:
Rollback triggers encountered:
Preexisting changes preserved:
Soft Delete: unchanged; separate follow-up.
```

Do not create a final squashed commit unless the user explicitly requests integration.

## Spec Coverage Matrix

| Spec requirement | Owning tasks | Required evidence |
|---|---|---|
| canonical `snowflake` and old name deletion | 1, 4, 25, 27 | parser/core/compiler scans |
| JDBC type/capacity retention | 2 | H2 source metadata test |
| strict storage-nearest matrix | 3-5 | resolver/canonical/JPA projection tests |
| generic Strong ID value semantics | 6-9 | core, render compile, Jackson, JPA |
| no Strong ID allocation responsibility | 6-8, 22, 27 | allocation scans |
| typed accessor/assignment helper | 10-11, 20-21 | core and generated compile tests |
| one module catalog, no `Impl` | 12, 20-21, 27 | context, renderer, naming scan |
| registry-only UoW completion | 14-17, 27 | JPA tests and reflection scan |
| old annotation deleted | 15-16, 25, 27 | file deletion and active scan |
| root-first/child-first reconciliation | 18 | UoW convergence tests |
| outermost/ambiguous/observed/remove boundaries | 19 | UoW edge-case tests |
| own ID absent from constructor/payload | 22 | planner/renderer compile tests |
| factory graph ID-ready before return | 17, 23, 26 | supervisor and runtime tests |
| child ID before relation mutation return | 13, 24, 26 | carrier/render/runtime tests |
| plain DB identity remains provider-managed | 4, 15, 17, 22 | canonical/UoW/generator tests |
| compiler uses explicit evidence | 25 | compiler negative/explicit tests |
| Soft Delete excluded | 27 | scoped diff review |

## Rollback Triggers

Stop the owning task and revise the spec plus affected plan tasks if any of these occur:

- real PostgreSQL/H2 native UUID metadata cannot be distinguished by the agreed JDBC type/name evidence;
- direct UUID or Long embeddable mapping requires a converter;
- Jackson cannot enforce string-only Snowflake input with the generated static `JsonNode` creator;
- generated same-package accessors cannot call the `internal set` ID property across the actual generated source/module layout;
- Spring cannot collect a generated catalog without turning accessor objects into beans;
- Hibernate proxy normalization cannot find an exact registry accessor without reflective member discovery;
- existing bounded traversal cannot determine outermost pending ownership for the required root-first/child-first/nested cases;
- reconciliation must occur after interceptor sets are already observable;
- generated owned relation code cannot prepare before mutation while keeping `OwnedEntityList` infrastructure-free.

On a trigger:

1. Capture the exact metadata, generated source, compile error, or runtime exception.
2. Stop the task; do not add a converter, reflection, guessed default, alias, annotation, or static marker.
3. Update the design spec decision and its rejected alternatives.
4. Update every affected task, interface, test, and scan in this plan.
5. Resume only after design review.

## Agent Handoff Contract

For subagent-driven execution:

- Give one task to one implementation agent. Include the full task text and the design spec path.
- Do not run two tasks concurrently when they touch the same production or test file.
- Require RED evidence before production changes and GREEN evidence before accepting the task.
- Require a separate review agent to compare the task diff against its `Interfaces`, global constraints, and spec coverage row.
- Return commit hash, exact commands, outcomes, changed files, and discovered deviations to the coordinator.
- The coordinator updates checkboxes only after reviewing that evidence.
- After any context compaction, resume from the first unchecked task and reread its dependency commits plus this plan's Global Constraints.

For inline execution, follow the same task boundaries and commits. Do not collapse several tasks into one unreviewed change because the current agent has more context.

## Final Behavioral Boundary

```kotlin
val payload = CreateOrder.Payload(customerId = customerId)
val order = Mediator.factories.create(payload)
val orderId = order.id

val line = order.addLine("SKU-1")
val lineId = line.id

Mediator.uow.save()
```

The caller supplies no primary key. The Strong ID value type locates no infrastructure. Generated relation code and UoW official entry points provide ID readiness. Final persistence remains root-oriented, and no compatibility alias, annotation discovery, reflection fallback, converter, or invented child marker participates.

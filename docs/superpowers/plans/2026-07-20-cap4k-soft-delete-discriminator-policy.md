# cap4k Soft Delete Discriminator Policy Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the post-PR-119 soft-delete placeholder state with a first-class self-id soft-delete discriminator policy that renders default Hibernate aggregate entity filtering and delete SQL.

**Architecture:** The canonical model carries semantic soft-delete policy data derived from `@Managed=deleted`, not a raw `softDeleteColumn` string. Core validation proves `SELF_ID` assignability from DB storage range and signedness, and aggregate generation projects that semantic policy into quote-style-aware `@SQLDelete` and `@Where` template context. Unique planning hides the discriminator only when `providerControl.softDelete` exists, while projection filtering remains explicitly out of scope.

**Tech Stack:** Kotlin, Gradle multi-module tests, JUnit 5, Pebble templates, JDBC metadata snapshots, Hibernate annotations in generated Kotlin entities.

## Global Constraints

- DB comment annotation redesign from PR #119 is the active input contract; do not reintroduce `@Deleted`, `@Version`, `@SoftDeleteColumn`, `@DynamicInsert`, `@DynamicUpdate`, `@One`, `@Count`, or old relation aliases.
- Soft delete is enabled only by a resolved deleted marker column from `@Managed=deleted` or the existing deleted default-column resolver.
- The first supported tombstone strategy is `SELF_ID` only.
- The active sentinel is the string value `0` only.
- Deleted rows must use `deleted_column = id_column`, not `deleted_column = 1`.
- Q1 decision: generated constructors may mirror an actual DB default discovered from schema, but must not synthesize `deleted = 0` when the DB default is absent.
- Q2 decision: require conservative DB-storage-range assignability with signedness checks; proven widening is allowed, while narrowing, unsafe signedness changes, and unproven types fail fast.
- Q3 decision: aggregate projections do not inherit active filters in this iteration; projection filtering remains a future read-model decision.
- Keep deleted fields system-managed through `SYSTEM_TRANSITION_ONLY`; factory/create/update payloads must not accept deleted fields.
- Replace canonical/provider `softDeleteColumn` with `softDelete: AggregateSoftDeletePolicy?`; do not keep both as active sources of truth.
- Default aggregate entity templates may consume derived `softDeleteSql` and `softDeleteWhereClause`, but must also expose a structured `softDelete` context.
- Avoid installing dependencies; use existing Gradle wrapper and focused tests.

---

## File Structure

- Modify `cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModels.kt`
  - Add `SoftDeleteTombstoneStrategy`.
  - Add `AggregateSoftDeletePolicy`.
  - Replace `AggregatePersistenceProviderControl.softDeleteColumn` with `softDelete`.
- Modify `cap4k-plugin-pipeline-api/src/test/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModelsTest.kt`
  - Lock the public model contract for the new policy.
- Create `cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/AggregateSoftDeletePolicyResolver.kt`
  - Convert resolved marker policy plus table metadata into `AggregateSoftDeletePolicy`.
  - Validate nullable/default/type compatibility for `SELF_ID`.
- Modify `cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/AggregatePersistenceProviderInference.kt`
  - Produce provider controls when either version or soft delete exists.
  - Attach `softDelete` instead of `softDeleteColumn`.
- Modify `cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/AggregateSpecialFieldPolicyResolver.kt`
  - Call the updated provider inference signature.
  - Preserve deleted marker write policy and managed-field behavior.
- Modify `cap4k-plugin-pipeline-core/src/test/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssemblerTest.kt`
  - Cover policy resolution, fail-fast validation, write-surface preservation, and provider-control migration.
- Modify `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/EntityArtifactPlanner.kt`
  - Project semantic `softDelete` into quote-style-aware render context and full delete SQL.
- Modify `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateUniqueConstraintPlanning.kt`
  - Filter unique-query request fields from `providerControl.softDelete?.columnName`.
- Modify `cap4k-plugin-pipeline-generator-aggregate/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateArtifactPlannerTest.kt`
  - Replace old "leaves soft delete sql unset" assertions with self-id SQL and policy-context assertions.
- Modify `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/entity.kt.peb`
  - Restore conditional Hibernate `@SQLDelete` and `@Where` rendering from structured soft-delete context.
- Modify `cap4k-plugin-pipeline-renderer-pebble/src/test/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PebbleArtifactRendererTest.kt`
  - Replace "ignores soft delete sql context" with "renders structured soft delete context".
- Modify `cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-provider-persistence-sample/schema.sql`
  - Use `deleted bigint not null default 0 comment '@Managed=deleted;'` for bigint ids.
- Modify `cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-provider-persistence-compile-sample/schema.sql`
  - Keep the compile fixture aligned with the generation fixture.
- Modify `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginFunctionalTest.kt`
  - Assert generated aggregate entities contain self-id soft-delete annotations.
- Modify `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginCompileFunctionalTest.kt`
  - Assert compile fixture generated code contains self-id soft-delete annotations and still compiles.
- Modify `docs/public/reference/db-schema-annotations.md`
  - Document that `@Managed=deleted` participates in self-id soft-delete policy and requires non-null numeric default `0`.
- Modify `docs/superpowers/specs/2026-07-20-cap4k-soft-delete-discriminator-policy-design.md`
  - Replace the three open questions with the resolved decisions recorded above.

### Task 1: API Soft Delete Policy Contract

**Files:**
- Modify: `cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModels.kt`
- Modify: `cap4k-plugin-pipeline-api/src/test/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModelsTest.kt`

**Interfaces:**
- Consumes: Existing `AggregatePersistenceProviderControl`, `CanonicalModel.aggregatePersistenceProviderControls`, and `DbManagedRole.DELETED`.
- Produces:
  - `enum class SoftDeleteTombstoneStrategy { SELF_ID }`
  - `data class AggregateSoftDeletePolicy(val fieldName: String, val columnName: String, val activeValue: String, val tombstoneStrategy: SoftDeleteTombstoneStrategy, val activePredicateSql: String, val deleteAssignmentSql: String)`
  - `AggregatePersistenceProviderControl.softDelete: AggregateSoftDeletePolicy?`

- [ ] **Step 1: Write the failing API model test**

Append this test to `PipelineModelsTest` before the final closing brace:

```kotlin
    @Test
    fun `aggregate persistence provider control carries semantic soft delete policy`() {
        val softDelete = AggregateSoftDeletePolicy(
            fieldName = "deleted",
            columnName = "deleted",
            activeValue = "0",
            tombstoneStrategy = SoftDeleteTombstoneStrategy.SELF_ID,
            activePredicateSql = "\"deleted\" = 0",
            deleteAssignmentSql = "\"deleted\" = \"id\"",
        )
        val control = AggregatePersistenceProviderControl(
            entityName = "VideoPost",
            entityPackageName = "com.acme.demo.domain.aggregates.video_post",
            tableName = "video_post",
            softDelete = softDelete,
            idFieldName = "id",
            versionFieldName = "version",
        )

        assertEquals(softDelete, control.softDelete)
        assertEquals(SoftDeleteTombstoneStrategy.SELF_ID, control.softDelete?.tombstoneStrategy)
        assertEquals("\"deleted\" = \"id\"", control.softDelete?.deleteAssignmentSql)
    }
```

- [ ] **Step 2: Run the API test and verify it fails for missing symbols**

Run:

```powershell
./gradlew.bat :cap4k-plugin-pipeline-api:test --tests "com.only4.cap4k.plugin.pipeline.api.PipelineModelsTest.aggregate persistence provider control carries semantic soft delete policy" --console=plain
```

Expected: FAIL to compile with unresolved references for `AggregateSoftDeletePolicy`, `SoftDeleteTombstoneStrategy`, and/or unknown named argument `softDelete`.

- [ ] **Step 3: Add the soft-delete API model**

In `PipelineModels.kt`, insert this enum and data class immediately before `data class AggregatePersistenceProviderControl`:

```kotlin
enum class SoftDeleteTombstoneStrategy {
    SELF_ID,
}

data class AggregateSoftDeletePolicy(
    val fieldName: String,
    val columnName: String,
    val activeValue: String,
    val tombstoneStrategy: SoftDeleteTombstoneStrategy,
    val activePredicateSql: String,
    val deleteAssignmentSql: String,
)
```

Then replace the existing provider control:

```kotlin
data class AggregatePersistenceProviderControl(
    val entityName: String,
    val entityPackageName: String,
    val tableName: String,
    val softDeleteColumn: String? = null,
    val idFieldName: String,
    val versionFieldName: String? = null,
)
```

with:

```kotlin
data class AggregatePersistenceProviderControl(
    val entityName: String,
    val entityPackageName: String,
    val tableName: String,
    val softDelete: AggregateSoftDeletePolicy? = null,
    val idFieldName: String,
    val versionFieldName: String? = null,
)
```

- [ ] **Step 4: Run the API test and verify the new contract passes**

Run:

```powershell
./gradlew.bat :cap4k-plugin-pipeline-api:test --tests "com.only4.cap4k.plugin.pipeline.api.PipelineModelsTest" --console=plain
```

Expected: PASS for `PipelineModelsTest`. Other modules are not compiled by this command yet, so references to `softDeleteColumn` outside the API module are handled in later tasks.

- [ ] **Step 5: Commit Task 1**

```powershell
git add cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModels.kt cap4k-plugin-pipeline-api/src/test/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModelsTest.kt
git commit -m "feat: add soft delete policy model"
```

### Task 2: Core Soft Delete Policy Resolution

**Files:**
- Create: `cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/AggregateSoftDeletePolicyResolver.kt`
- Modify: `cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/AggregatePersistenceProviderInference.kt`
- Modify: `cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/AggregateSpecialFieldPolicyResolver.kt`
- Modify: `cap4k-plugin-pipeline-core/src/test/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssemblerTest.kt`

**Interfaces:**
- Consumes:
  - `AggregateSpecialFieldResolvedPolicy.deleted`
  - `AggregateSpecialFieldResolvedPolicy.id`
  - `DbTableSnapshot.columns`
  - `AggregatePersistenceProviderControl.softDelete` from Task 1
- Produces:
  - `AggregateSoftDeletePolicyResolver.resolve(table: DbTableSnapshot, resolvedPolicy: AggregateSpecialFieldResolvedPolicy): AggregateSoftDeletePolicy?`
  - Provider controls that are emitted when `version.enabled` or `softDelete != null`.
  - Canonical `AggregateSoftDeletePolicy` with unquoted semantic fragments:
    - `activePredicateSql = "deleted = 0"`
    - `deleteAssignmentSql = "deleted = id"`

- [ ] **Step 1: Extend the core test helper to carry DB defaults**

In `DefaultCanonicalAssemblerTest`, change the private `column` helper signature by adding `defaultValue: String? = null` after `nullable: Boolean`, then pass it to `DbColumnSnapshot`.

Change this helper:

```kotlin
    private fun column(
        name: String,
        dbType: String,
        kotlinType: String,
        nullable: Boolean,
        primaryKey: Boolean = false,
```

to:

```kotlin
    private fun column(
        name: String,
        dbType: String,
        kotlinType: String,
        nullable: Boolean,
        defaultValue: String? = null,
        primaryKey: Boolean = false,
```

Then add `defaultValue = defaultValue,` to the `DbColumnSnapshot(` call:

```kotlin
    ): DbColumnSnapshot = DbColumnSnapshot(
        name = name,
        dbType = dbType,
        kotlinType = kotlinType,
        nullable = nullable,
        defaultValue = defaultValue,
        isPrimaryKey = primaryKey,
```

- [ ] **Step 2: Write failing policy-resolution tests**

Add these imports to `DefaultCanonicalAssemblerTest` if they are not already present:

```kotlin
import com.only4.cap4k.plugin.pipeline.api.SoftDeleteTombstoneStrategy
import org.junit.jupiter.api.Assertions.assertNotNull
```

Add these tests near the existing deleted/version special-field tests:

```kotlin
    @Test
    fun `deleted marker resolves self id soft delete policy and provider control without version`() {
        val result = assembleAggregate(
            config = projectConfigWithSpecialFieldDefaults(
                idDefaultStrategy = "identity",
                deletedDefaultColumn = "",
            ),
            tables = listOf(
                table(
                    name = "video_post",
                    columns = listOf(
                        column(
                            name = "id",
                            dbType = "BIGINT",
                            kotlinType = "Long",
                            nullable = false,
                            primaryKey = true,
                            idStrategy = DbIdStrategy.DB_IDENTITY,
                        ),
                        column(
                            name = "deleted",
                            dbType = "BIGINT",
                            kotlinType = "Long",
                            nullable = false,
                            defaultValue = "0",
                            managedRole = DbManagedRole.DELETED,
                        ),
                        column("title", "VARCHAR", "String", false),
                    ),
                    primaryKey = listOf("id"),
                    aggregateRoot = true,
                )
            )
        )

        val resolved = result.model.aggregateSpecialFieldResolvedPolicies.single()
        val control = result.model.aggregatePersistenceProviderControls.single()
        val softDelete = assertNotNull(control.softDelete)

        assertEquals(true, resolved.deleted.enabled)
        assertEquals("deleted", resolved.deleted.fieldName)
        assertEquals(SpecialFieldWritePolicy.SYSTEM_TRANSITION_ONLY, resolved.deleted.writePolicy)
        assertEquals(listOf("title"), resolved.writeSurface.createAllowedFields)
        assertEquals(listOf("title"), resolved.writeSurface.updateAllowedFields)
        assertEquals("id", control.idFieldName)
        assertEquals(null, control.versionFieldName)
        assertEquals("deleted", softDelete.fieldName)
        assertEquals("deleted", softDelete.columnName)
        assertEquals("0", softDelete.activeValue)
        assertEquals(SoftDeleteTombstoneStrategy.SELF_ID, softDelete.tombstoneStrategy)
        assertEquals("deleted = 0", softDelete.activePredicateSql)
        assertEquals("deleted = id", softDelete.deleteAssignmentSql)
    }

    @Test
    fun `soft delete policy requires deleted column default zero`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            assembleAggregate(
                config = projectConfigWithSpecialFieldDefaults(idDefaultStrategy = "identity"),
                tables = listOf(
                    table(
                        name = "video_post",
                        columns = listOf(
                            column(
                                name = "id",
                                dbType = "BIGINT",
                                kotlinType = "Long",
                                nullable = false,
                                primaryKey = true,
                                idStrategy = DbIdStrategy.DB_IDENTITY,
                            ),
                            column(
                                name = "deleted",
                                dbType = "BIGINT",
                                kotlinType = "Long",
                                nullable = false,
                                managedRole = DbManagedRole.DELETED,
                            ),
                        ),
                        primaryKey = listOf("id"),
                        aggregateRoot = true,
                    )
                )
            )
        }

        assertEquals("soft delete column video_post.deleted must declare default 0 for active value 0", error.message)
    }

    @Test
    fun `soft delete policy rejects nullable deleted column`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            assembleAggregate(
                config = projectConfigWithSpecialFieldDefaults(idDefaultStrategy = "identity"),
                tables = listOf(
                    table(
                        name = "video_post",
                        columns = listOf(
                            column(
                                name = "id",
                                dbType = "BIGINT",
                                kotlinType = "Long",
                                nullable = false,
                                primaryKey = true,
                                idStrategy = DbIdStrategy.DB_IDENTITY,
                            ),
                            column(
                                name = "deleted",
                                dbType = "BIGINT",
                                kotlinType = "Long",
                                nullable = true,
                                defaultValue = "0",
                                managedRole = DbManagedRole.DELETED,
                            ),
                        ),
                        primaryKey = listOf("id"),
                        aggregateRoot = true,
                    )
                )
            )
        }

        assertEquals("soft delete column video_post.deleted must be non-null for active value 0", error.message)
    }

    @Test
    fun `soft delete policy rejects id values wider than deleted discriminator`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            assembleAggregate(
                config = projectConfigWithSpecialFieldDefaults(idDefaultStrategy = "identity"),
                tables = listOf(
                    table(
                        name = "video_post",
                        columns = listOf(
                            column(
                                name = "id",
                                dbType = "BIGINT",
                                kotlinType = "Long",
                                nullable = false,
                                primaryKey = true,
                                idStrategy = DbIdStrategy.DB_IDENTITY,
                            ),
                            column(
                                name = "deleted",
                                dbType = "INT",
                                kotlinType = "Int",
                                nullable = false,
                                defaultValue = "0",
                                managedRole = DbManagedRole.DELETED,
                            ),
                        ),
                        primaryKey = listOf("id"),
                        aggregateRoot = true,
                    )
                )
            )
        }

        assertEquals("soft delete column video_post.deleted cannot store id column id value for SELF_ID tombstone strategy", error.message)
    }
```

- [ ] **Step 3: Run the core tests and verify they fail before implementation**

Run:

```powershell
./gradlew.bat :cap4k-plugin-pipeline-core:test --tests "com.only4.cap4k.plugin.pipeline.core.DefaultCanonicalAssemblerTest" --console=plain
```

Expected: FAIL because `AggregatePersistenceProviderControl.softDelete` is not populated, `AggregateSoftDeletePolicyResolver` does not exist, and old tests still expect empty provider controls when only deleted is present.

- [ ] **Step 4: Add the resolver implementation**

Create `AggregateSoftDeletePolicyResolver.kt` with this content:

```kotlin
package com.only4.cap4k.plugin.pipeline.core

import com.only4.cap4k.plugin.pipeline.api.AggregateSoftDeletePolicy
import com.only4.cap4k.plugin.pipeline.api.AggregateSpecialFieldResolvedPolicy
import com.only4.cap4k.plugin.pipeline.api.DbColumnSnapshot
import com.only4.cap4k.plugin.pipeline.api.DbTableSnapshot
import com.only4.cap4k.plugin.pipeline.api.SoftDeleteTombstoneStrategy
import java.util.Locale

internal object AggregateSoftDeletePolicyResolver {
    private const val ActiveValue = "0"

    fun resolve(
        table: DbTableSnapshot,
        resolvedPolicy: AggregateSpecialFieldResolvedPolicy,
    ): AggregateSoftDeletePolicy? {
        val deleted = resolvedPolicy.deleted.takeIf { it.enabled } ?: return null
        val deletedColumnName = requireNotNull(deleted.columnName) {
            "missing soft delete column for table ${table.tableName}"
        }
        val deletedFieldName = requireNotNull(deleted.fieldName) {
            "missing soft delete field for table ${table.tableName}"
        }
        val idColumn = table.columns.firstOrNull {
            it.name.equals(resolvedPolicy.id.columnName, ignoreCase = true)
        } ?: throw IllegalArgumentException(
            "missing id column ${resolvedPolicy.id.columnName} for soft delete table ${table.tableName}"
        )
        val deletedColumn = table.columns.firstOrNull {
            it.name.equals(deletedColumnName, ignoreCase = true)
        } ?: throw IllegalArgumentException(
            "missing soft delete column $deletedColumnName for table ${table.tableName}"
        )

        validateDeletedColumn(table, deletedColumn)
        validateSelfIdAssignable(table, idColumn, deletedColumn)

        return AggregateSoftDeletePolicy(
            fieldName = deletedFieldName,
            columnName = deletedColumn.name,
            activeValue = ActiveValue,
            tombstoneStrategy = SoftDeleteTombstoneStrategy.SELF_ID,
            activePredicateSql = "${deletedColumn.name} = $ActiveValue",
            deleteAssignmentSql = "${deletedColumn.name} = ${idColumn.name}",
        )
    }

    private fun validateDeletedColumn(table: DbTableSnapshot, deletedColumn: DbColumnSnapshot) {
        require(!deletedColumn.nullable) {
            "soft delete column ${table.tableName}.${deletedColumn.name} must be non-null for active value 0"
        }
        require(isDefaultZero(deletedColumn.defaultValue)) {
            "soft delete column ${table.tableName}.${deletedColumn.name} must declare default 0 for active value 0"
        }
    }

    private fun validateSelfIdAssignable(
        table: DbTableSnapshot,
        idColumn: DbColumnSnapshot,
        deletedColumn: DbColumnSnapshot,
    ) {
        val idCapacity = numericCapacity(idColumn.dbType)
        val deletedCapacity = numericCapacity(deletedColumn.dbType)
        require(idCapacity != null && deletedCapacity != null && deletedCapacity.canStore(idCapacity)) {
            "soft delete column ${table.tableName}.${deletedColumn.name} cannot store id column ${idColumn.name} value for SELF_ID tombstone strategy"
        }
    }

    private fun numericCapacity(dbType: String): NumericCapacity? {
        val match = NUMERIC_DB_TYPE_REGEX.matchEntire(dbType.trim().uppercase(Locale.ROOT)) ?: return null
        val bits = when (match.groupValues[1]) {
            "TINYINT" -> 8
            "SMALLINT" -> 16
            "MEDIUMINT" -> 24
            "INT", "INTEGER" -> 32
            "BIGINT" -> 64
            else -> null
        }
        return bits?.let { NumericCapacity(it, match.groupValues[2].isNotEmpty()) }
    }

    private fun isDefaultZero(defaultValue: String?): Boolean {
        var value = defaultValue?.trim() ?: return false
        while (value.length >= 2 && value.first() == '(' && value.last() == ')') {
            value = value.substring(1, value.lastIndex).trim()
        }
        value = value.removeSurrounding("'").removeSurrounding("\"")
        return value == ActiveValue
    }

    private data class NumericCapacity(val bits: Int, val unsigned: Boolean) {
        fun canStore(source: NumericCapacity): Boolean = when {
            source.unsigned == unsigned -> bits >= source.bits
            !source.unsigned && unsigned -> false
            else -> bits > source.bits
        }
    }

    private val NUMERIC_DB_TYPE_REGEX = Regex(
        "^(TINYINT|SMALLINT|MEDIUMINT|INT|INTEGER|BIGINT)\\s*(?:\\(\\s*\\d+\\s*\\))?\\s*(UNSIGNED)?\\s*$"
    )
}
```

- [ ] **Step 5: Update provider inference to emit semantic soft-delete controls**

Replace `AggregatePersistenceProviderInference.kt` with this implementation:

```kotlin
package com.only4.cap4k.plugin.pipeline.core

import com.only4.cap4k.plugin.pipeline.api.AggregatePersistenceProviderControl
import com.only4.cap4k.plugin.pipeline.api.AggregateSpecialFieldResolvedPolicy
import com.only4.cap4k.plugin.pipeline.api.DbTableSnapshot
import java.util.Locale

internal object AggregatePersistenceProviderInference {
    fun infer(
        tables: List<DbTableSnapshot>,
        resolvedPolicies: List<AggregateSpecialFieldResolvedPolicy>,
    ): List<AggregatePersistenceProviderControl> {
        val tableByName = tables.associateBy { it.tableName.lowercase(Locale.ROOT) }

        return resolvedPolicies.mapNotNull { policy ->
            val table = tableByName[policy.tableName.lowercase(Locale.ROOT)]
                ?: return@mapNotNull null
            val versionFieldName = if (policy.version.enabled) policy.version.fieldName else null
            val softDelete = AggregateSoftDeletePolicyResolver.resolve(
                table = table,
                resolvedPolicy = policy,
            )
            if (versionFieldName == null && softDelete == null) {
                return@mapNotNull null
            }

            AggregatePersistenceProviderControl(
                entityName = policy.entityName,
                entityPackageName = policy.entityPackageName,
                tableName = policy.tableName,
                softDelete = softDelete,
                idFieldName = policy.id.fieldName,
                versionFieldName = versionFieldName,
            )
        }
    }
}
```

`AggregateSpecialFieldPolicyResolver` already calls `AggregatePersistenceProviderInference.infer(tables, resolvedPolicies)`, so no behavior change is required there unless imports or formatting need cleanup after compilation.

- [ ] **Step 6: Update existing deleted-marker tests for the stricter default contract**

In existing tests that declare `DbManagedRole.DELETED` and expect successful assembly, make the deleted column non-null, numeric-assignable, and `defaultValue = "0"`.

Change this block in `explicit deleted marker overrides DSL default column name`:

```kotlin
column(name = "is_deleted", dbType = "INT", kotlinType = "Int", nullable = false, managedRole = DbManagedRole.DELETED),
column(name = "deleted", dbType = "INT", kotlinType = "Int", nullable = false),
```

to:

```kotlin
column(name = "is_deleted", dbType = "BIGINT", kotlinType = "Long", nullable = false, defaultValue = "0", managedRole = DbManagedRole.DELETED),
column(name = "deleted", dbType = "BIGINT", kotlinType = "Long", nullable = false),
```

Then replace the old assertion:

```kotlin
assertTrue(result.model.aggregatePersistenceProviderControls.isEmpty())
```

with:

```kotlin
assertEquals("is_deleted", result.model.aggregatePersistenceProviderControls.single().softDelete?.columnName)
```

- [ ] **Step 7: Run the core tests and verify policy resolution passes**

Run:

```powershell
./gradlew.bat :cap4k-plugin-pipeline-core:test --tests "com.only4.cap4k.plugin.pipeline.core.DefaultCanonicalAssemblerTest" --console=plain
```

Expected: PASS for `DefaultCanonicalAssemblerTest`.

- [ ] **Step 8: Commit Task 2**

```powershell
git add cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/AggregateSoftDeletePolicyResolver.kt cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/AggregatePersistenceProviderInference.kt cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/AggregateSpecialFieldPolicyResolver.kt cap4k-plugin-pipeline-core/src/test/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssemblerTest.kt
git commit -m "feat: resolve self id soft delete policy"
```

### Task 3: Aggregate Generator Planning For Soft Delete

**Files:**
- Modify: `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/EntityArtifactPlanner.kt`
- Modify: `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateUniqueConstraintPlanning.kt`
- Modify: `cap4k-plugin-pipeline-generator-aggregate/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateArtifactPlannerTest.kt`

**Interfaces:**
- Consumes:
  - `AggregatePersistenceProviderControl.softDelete`
  - `AggregateSoftDeletePolicy.columnName`
  - `AggregateSoftDeletePolicy.activeValue`
  - `AggregatePersistenceProviderControl.idFieldName`
  - `AggregatePersistenceProviderControl.versionFieldName`
- Produces generator context:
  - `softDeleteSql: String?`
  - `softDeleteWhereClause: String?`
  - `softDeleteSqlKotlinStringLiteral: String?`
  - `softDeleteWhereClauseKotlinStringLiteral: String?`
  - `softDelete: Map<String, Any?>` with `enabled`, `columnName`, `activeValue`, `tombstoneStrategy`, `activePredicateSql`, and `deleteAssignmentSql`.

- [ ] **Step 1: Replace old planner tests with self-id SQL expectations**

In `AggregateArtifactPlannerTest`, replace `entity planner leaves soft delete sql unset` with:

```kotlin
    @Test
    fun `entity planner renders self id soft delete sql`() {
        val entity = EntityModel(
            name = "VideoPost",
            packageName = "com.acme.demo.domain.aggregates.video_post",
            tableName = "video_post",
            comment = "video post",
            fields = listOf(
                FieldModel("id", "Long", columnName = "id"),
                FieldModel("version", "Long", columnName = "version"),
                FieldModel("deleted", "Long", columnName = "deleted", managedRole = DbManagedRole.DELETED),
            ),
            idField = FieldModel("id", "Long", columnName = "id"),
        )
        val artifact = AggregateArtifactPlanner().plan(
            aggregateConfig(),
            CanonicalModel(
                entities = listOf(entity),
                aggregateEntityJpa = listOf(defaultAggregateEntityJpa(entity)),
                aggregatePersistenceProviderControls = listOf(
                    AggregatePersistenceProviderControl(
                        entityName = "VideoPost",
                        entityPackageName = "com.acme.demo.domain.aggregates.video_post",
                        tableName = "video_post",
                        softDelete = AggregateSoftDeletePolicy(
                            fieldName = "deleted",
                            columnName = "deleted",
                            activeValue = "0",
                            tombstoneStrategy = SoftDeleteTombstoneStrategy.SELF_ID,
                            activePredicateSql = "deleted = 0",
                            deleteAssignmentSql = "deleted = id",
                        ),
                        idFieldName = "id",
                        versionFieldName = "version",
                    )
                ),
            )
        ).single { it.templateId == "aggregate/entity.kt.peb" }

        @Suppress("UNCHECKED_CAST")
        val softDelete = artifact.context["softDelete"] as Map<String, Any?>

        assertEquals("update \"video_post\" set \"deleted\" = \"id\" where \"id\" = ? and \"version\" = ?", artifact.context["softDeleteSql"])
        assertEquals("\"deleted\" = 0", artifact.context["softDeleteWhereClause"])
        assertEquals((artifact.context["softDeleteSql"] as String).toKotlinStringLiteral(), artifact.context["softDeleteSqlKotlinStringLiteral"])
        assertEquals((artifact.context["softDeleteWhereClause"] as String).toKotlinStringLiteral(), artifact.context["softDeleteWhereClauseKotlinStringLiteral"])
        assertEquals(true, softDelete["enabled"])
        assertEquals("deleted", softDelete["columnName"])
        assertEquals("0", softDelete["activeValue"])
        assertEquals("SELF_ID", softDelete["tombstoneStrategy"])
        assertEquals("\"deleted\" = 0", softDelete["activePredicateSql"])
        assertEquals("\"deleted\" = \"id\"", softDelete["deleteAssignmentSql"])
    }
```

Replace `entity planner leaves versionless soft delete sql unset with physical id column only` with:

```kotlin
    @Test
    fun `entity planner renders versionless self id soft delete sql`() {
        val entity = EntityModel(
            name = "AuditLog",
            packageName = "com.acme.demo.domain.aggregates.audit_log",
            tableName = "audit_log",
            comment = "audit log",
            fields = listOf(
                FieldModel("id", "Long", columnName = "id"),
                FieldModel("deleted", "Long", columnName = "deleted", managedRole = DbManagedRole.DELETED),
                FieldModel("content", "String", columnName = "content"),
            ),
            idField = FieldModel("id", "Long", columnName = "id"),
        )
        val artifact = AggregateArtifactPlanner().plan(
            aggregateConfig(),
            CanonicalModel(
                entities = listOf(entity),
                aggregateEntityJpa = listOf(defaultAggregateEntityJpa(entity)),
                aggregatePersistenceProviderControls = listOf(
                    AggregatePersistenceProviderControl(
                        entityName = "AuditLog",
                        entityPackageName = "com.acme.demo.domain.aggregates.audit_log",
                        tableName = "audit_log",
                        softDelete = AggregateSoftDeletePolicy(
                            fieldName = "deleted",
                            columnName = "deleted",
                            activeValue = "0",
                            tombstoneStrategy = SoftDeleteTombstoneStrategy.SELF_ID,
                            activePredicateSql = "deleted = 0",
                            deleteAssignmentSql = "deleted = id",
                        ),
                        idFieldName = "id",
                    )
                ),
            )
        ).single { it.templateId == "aggregate/entity.kt.peb" }

        assertEquals("update \"audit_log\" set \"deleted\" = \"id\" where \"id\" = ?", artifact.context["softDeleteSql"])
        assertEquals("\"deleted\" = 0", artifact.context["softDeleteWhereClause"])
    }
```

Replace `entity planner leaves mysql soft delete sql unset` with a MySQL quote-style assertion:

```kotlin
    @Test
    fun `entity planner renders mysql self id soft delete sql with backticks`() {
        val entity = EntityModel(
            name = "VideoPost",
            packageName = "com.acme.demo.domain.aggregates.video_post",
            tableName = "video_post",
            comment = "video post",
            fields = listOf(
                FieldModel("postId", "Long", columnName = "post_id"),
                FieldModel("deleted", "Long", columnName = "deleted", managedRole = DbManagedRole.DELETED),
            ),
            idField = FieldModel("postId", "Long", columnName = "post_id"),
        )
        val artifact = AggregateArtifactPlanner().plan(
            aggregateConfig(
                sources = mapOf(
                    "db" to SourceConfig(
                        options = mapOf("url" to "jdbc:h2:mem:test;MODE=MySQL;DATABASE_TO_UPPER=false")
                    )
                )
            ),
            CanonicalModel(
                entities = listOf(entity),
                aggregateEntityJpa = listOf(defaultAggregateEntityJpa(entity)),
                aggregatePersistenceProviderControls = listOf(
                    AggregatePersistenceProviderControl(
                        entityName = "VideoPost",
                        entityPackageName = "com.acme.demo.domain.aggregates.video_post",
                        tableName = "video_post",
                        softDelete = AggregateSoftDeletePolicy(
                            fieldName = "deleted",
                            columnName = "deleted",
                            activeValue = "0",
                            tombstoneStrategy = SoftDeleteTombstoneStrategy.SELF_ID,
                            activePredicateSql = "deleted = 0",
                            deleteAssignmentSql = "deleted = post_id",
                        ),
                        idFieldName = "postId",
                    )
                ),
            )
        ).single { it.templateId == "aggregate/entity.kt.peb" }

        assertEquals("update `video_post` set `deleted` = `post_id` where `post_id` = ?", artifact.context["softDeleteSql"])
        assertEquals("`deleted` = 0", artifact.context["softDeleteWhereClause"])
    }
```

If imports are missing, add:

```kotlin
import com.only4.cap4k.plugin.pipeline.api.AggregateSoftDeletePolicy
import com.only4.cap4k.plugin.pipeline.api.DbManagedRole
import com.only4.cap4k.plugin.pipeline.api.SoftDeleteTombstoneStrategy
import com.only4.cap4k.plugin.pipeline.api.SourceConfig
```

- [ ] **Step 2: Update unique planning tests to use `softDelete`**

In unique-planning tests, replace provider-control construction like:

```kotlin
AggregatePersistenceProviderControl(
    entityName = "Category",
    entityPackageName = "com.acme.demo.domain.aggregates.category",
    tableName = "category",
    softDeleteColumn = "deleted",
    idFieldName = "id",
)
```

with:

```kotlin
AggregatePersistenceProviderControl(
    entityName = "Category",
    entityPackageName = "com.acme.demo.domain.aggregates.category",
    tableName = "category",
    softDelete = AggregateSoftDeletePolicy(
        fieldName = "deleted",
        columnName = "deleted",
        activeValue = "0",
        tombstoneStrategy = SoftDeleteTombstoneStrategy.SELF_ID,
        activePredicateSql = "deleted = 0",
        deleteAssignmentSql = "deleted = id",
    ),
    idFieldName = "id",
)
```

Keep the existing assertion in `unique planning uses table prefixed uk_v fragment and filters soft delete fields`:

```kotlin
assertEquals(listOf("deleted"), selection.filteredControlFields.map { it.name })
```

This assertion must continue to pass, now through `providerControl.softDelete?.columnName`.

- [ ] **Step 3: Run generator tests and verify they fail before implementation**

Run:

```powershell
./gradlew.bat :cap4k-plugin-pipeline-generator-aggregate:test --tests "com.only4.cap4k.plugin.pipeline.generator.aggregate.AggregateArtifactPlannerTest" --console=plain
```

Expected: FAIL because `EntityArtifactPlanner` still emits `softDeleteSql = null`, `softDeleteWhereClause = null`, and `AggregateUniqueConstraintPlanning` still references removed `softDeleteColumn`.

- [ ] **Step 4: Update `EntityArtifactPlanner` to build structured soft-delete context**

Add `import java.util.Locale` to `EntityArtifactPlanner.kt`.

Inside `plan`, after `idPolicyControl`, restore provider-control lookup:

```kotlin
            val providerControl = model.aggregatePersistenceProviderControls.firstOrNull {
                it.entityName == entity.name && it.entityPackageName == entity.packageName
            }
```

Near the top of `plan`, after `val defaultProjector = AggregateEntityDefaultProjector()`, add:

```kotlin
        val identifierQuoteStyle = resolveIdentifierQuoteStyle(config)
```

Before `val fieldContexts = entity.fields`, add:

```kotlin
            val idColumnName = providerControl?.let { control ->
                requireNotNull(scalarJpaByField[control.idFieldName]) {
                    "missing aggregate JPA metadata for ${entity.packageName}.${entity.name}.${control.idFieldName}"
                }.columnName
            }
            val versionColumnName = providerControl?.versionFieldName?.let { versionFieldName ->
                requireNotNull(scalarJpaByField[versionFieldName]) {
                    "missing aggregate JPA metadata for ${entity.packageName}.${entity.name}.${versionFieldName}"
                }.columnName
            }
            val softDeleteContext = providerControl?.softDelete?.let { policy ->
                val quotedDeletedColumn = quoteIdentifier(policy.columnName, identifierQuoteStyle)
                val quotedIdColumn = quoteIdentifier(requireNotNull(idColumnName), identifierQuoteStyle)
                val activePredicateSql = "$quotedDeletedColumn = ${policy.activeValue}"
                val deleteAssignmentSql = "$quotedDeletedColumn = $quotedIdColumn"
                mapOf(
                    "enabled" to true,
                    "columnName" to policy.columnName,
                    "activeValue" to policy.activeValue,
                    "tombstoneStrategy" to policy.tombstoneStrategy.name,
                    "activePredicateSql" to activePredicateSql,
                    "deleteAssignmentSql" to deleteAssignmentSql,
                )
            }
            val softDeleteSql = softDeleteContext?.let { context ->
                val control = requireNotNull(providerControl)
                buildSoftDeleteSql(
                    tableName = control.tableName,
                    deleteAssignmentSql = requireNotNull(context["deleteAssignmentSql"] as? String),
                    idColumnName = requireNotNull(idColumnName),
                    versionColumnName = versionColumnName,
                    identifierQuoteStyle = identifierQuoteStyle,
                )
            }
            val softDeleteWhereClause = softDeleteContext?.get("activePredicateSql") as? String
```

In the artifact context map, replace:

```kotlin
                    "softDeleteSql" to null,
                    "softDeleteWhereClause" to null,
```

with:

```kotlin
                    "softDelete" to (softDeleteContext ?: mapOf("enabled" to false)),
                    "softDeleteSql" to softDeleteSql,
                    "softDeleteWhereClause" to softDeleteWhereClause,
                    "softDeleteSqlKotlinStringLiteral" to softDeleteSql?.toKotlinStringLiteral(),
                    "softDeleteWhereClauseKotlinStringLiteral" to softDeleteWhereClause?.toKotlinStringLiteral(),
```

At the bottom of `EntityArtifactPlanner.kt`, before the closing brace of `EntityArtifactPlanner`, add these helper methods and enum:

```kotlin
    private fun buildSoftDeleteSql(
        tableName: String,
        deleteAssignmentSql: String,
        idColumnName: String,
        versionColumnName: String?,
        identifierQuoteStyle: IdentifierQuoteStyle,
    ): String {
        val quotedTable = quoteIdentifier(tableName, identifierQuoteStyle)
        val quotedIdColumn = quoteIdentifier(idColumnName, identifierQuoteStyle)
        return if (versionColumnName != null) {
            val quotedVersionColumn = quoteIdentifier(versionColumnName, identifierQuoteStyle)
            "update $quotedTable set $deleteAssignmentSql where $quotedIdColumn = ? and $quotedVersionColumn = ?"
        } else {
            "update $quotedTable set $deleteAssignmentSql where $quotedIdColumn = ?"
        }
    }

    private fun resolveIdentifierQuoteStyle(config: ProjectConfig): IdentifierQuoteStyle {
        val dbUrl = config.sources["db"]
            ?.options
            ?.get("url")
            ?.toString()
            ?.lowercase(Locale.ROOT)
            ?: return IdentifierQuoteStyle.DOUBLE_QUOTE

        return when {
            dbUrl.startsWith("jdbc:mysql:") -> IdentifierQuoteStyle.BACKTICK
            dbUrl.startsWith("jdbc:mariadb:") -> IdentifierQuoteStyle.BACKTICK
            dbUrl.startsWith("jdbc:h2:") && dbUrl.contains("mode=mysql") -> IdentifierQuoteStyle.BACKTICK
            else -> IdentifierQuoteStyle.DOUBLE_QUOTE
        }
    }

    private fun quoteIdentifier(value: String, style: IdentifierQuoteStyle): String =
        when (style) {
            IdentifierQuoteStyle.DOUBLE_QUOTE -> "\"${value.replace("\"", "\"\"")}\""
            IdentifierQuoteStyle.BACKTICK -> "`${value.replace("`", "``")}`"
        }

    private enum class IdentifierQuoteStyle {
        DOUBLE_QUOTE,
        BACKTICK,
    }
```

- [ ] **Step 5: Update unique planning to consume semantic policy**

In `AggregateUniqueConstraintPlanning.controlColumnNames`, replace:

```kotlin
        (resolvedPolicy?.deleted?.takeIf { it.enabled }?.columnName ?: providerControl?.softDeleteColumn)
            ?.lowercase(Locale.ROOT)
            ?.let(::add)
```

with:

```kotlin
        providerControl?.softDelete?.columnName
            ?.lowercase(Locale.ROOT)
            ?.let(::add)
```

Add a regression test proving that a resolved deleted marker without `providerControl.softDelete` remains a normal unique-query request field. Keep resolved-policy fallback behavior for version columns unchanged.

Then run this scan:

```powershell
rg -n "softDeleteColumn" cap4k-plugin-pipeline-api cap4k-plugin-pipeline-core cap4k-plugin-pipeline-generator-aggregate --glob "!**/build/**"
```

Expected: no active source hits. Test fixture or historical docs hits are addressed in later tasks only if they are active current-contract docs.

- [ ] **Step 6: Run generator tests and verify planning passes**

Run:

```powershell
./gradlew.bat :cap4k-plugin-pipeline-generator-aggregate:test --tests "com.only4.cap4k.plugin.pipeline.generator.aggregate.AggregateArtifactPlannerTest" --console=plain
```

Expected: PASS for `AggregateArtifactPlannerTest`.

- [ ] **Step 7: Commit Task 3**

```powershell
git add cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/EntityArtifactPlanner.kt cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateUniqueConstraintPlanning.kt cap4k-plugin-pipeline-generator-aggregate/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateArtifactPlannerTest.kt
git commit -m "feat: plan aggregate soft delete rendering"
```

### Task 4: Pebble Aggregate Entity Rendering

**Files:**
- Modify: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/entity.kt.peb`
- Modify: `cap4k-plugin-pipeline-renderer-pebble/src/test/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PebbleArtifactRendererTest.kt`

**Interfaces:**
- Consumes:
  - `softDelete.enabled`
  - `softDeleteSqlKotlinStringLiteral`
  - `softDeleteWhereClauseKotlinStringLiteral`
- Emits Hibernate annotations only when `softDelete is defined and softDelete.enabled`.
- Does not emit `@DynamicInsert` or `@DynamicUpdate`.

- [ ] **Step 1: Replace the renderer regression test**

In `PebbleArtifactRendererTest`, replace `aggregate entity template ignores soft delete sql context` with:

```kotlin
    @Test
    fun `aggregate entity template renders structured soft delete context`() {
        val overrideDir = Files.createTempDirectory("cap4k-override-empty-aggregate-provider-persistence")
        val renderer = PebbleArtifactRenderer(
            templateResolver = PresetTemplateResolver(
                preset = "ddd-default",
                overrideDirs = listOf(overrideDir.toString())
            )
        )

        val rendered = renderer.render(
            planItems = listOf(
                ArtifactPlanItem(
                    generatorId = "aggregate",
                    moduleRole = "domain",
                    templateId = "aggregate/entity.kt.peb",
                    outputPath = "demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/VideoPost.kt",
                    context = mapOf(
                        "packageName" to "com.acme.demo.domain.aggregates.video_post",
                        "typeName" to "VideoPost",
                        "comment" to "video post",
                        "aggregateName" to "VideoPost",
                        "aggregateRoot" to true,
                        "entityJpa" to mapOf(
                            "entityEnabled" to true,
                            "tableName" to "video_post",
                        ),
                        "hasConverterFields" to false,
                        "hasGeneratedValueFields" to false,
                        "hasVersionFields" to false,
                        "softDelete" to mapOf(
                            "enabled" to true,
                            "columnName" to "deleted",
                            "activeValue" to "0",
                            "tombstoneStrategy" to "SELF_ID",
                            "activePredicateSql" to "\"deleted\" = 0",
                            "deleteAssignmentSql" to "\"deleted\" = \"id\"",
                        ),
                        "softDeleteSql" to "update \"video_post\" set \"deleted\" = \"id\" where \"id\" = ? and \"version\" = ?",
                        "softDeleteWhereClause" to "\"deleted\" = 0",
                        "softDeleteSqlKotlinStringLiteral" to "\"update \\\"video_post\\\" set \\\"deleted\\\" = \\\"id\\\" where \\\"id\\\" = ? and \\\"version\\\" = ?\"",
                        "softDeleteWhereClauseKotlinStringLiteral" to "\"\\\"deleted\\\" = 0\"",
                        "scalarFields" to emptyList<Map<String, Any?>>(),
                        "fields" to emptyList<Map<String, Any?>>(),
                        "relationFields" to emptyList<Map<String, Any?>>(),
                        "imports" to emptyList<String>(),
                        "jpaImports" to emptyList<String>(),
                    ),
                    conflictPolicy = ConflictPolicy.SKIP,
                )
            ),
            config = ProjectConfig(
                basePackage = "com.acme.demo",
                layout = ProjectLayout.MULTI_MODULE,
                modules = emptyMap(),
                sources = emptyMap(),
                generators = emptyMap(),
                templates = TemplateConfig(
                    preset = "ddd-default",
                    overrideDirs = listOf(overrideDir.toString()),
                    conflictPolicy = ConflictPolicy.SKIP
                )
            )
        )

        val content = rendered.single().content

        assertTrue(content.contains("import org.hibernate.annotations.SQLDelete"))
        assertTrue(content.contains("import org.hibernate.annotations.Where"))
        assertTrue(content.contains("""@SQLDelete(sql = "update \"video_post\" set \"deleted\" = \"id\" where \"id\" = ? and \"version\" = ?")"""))
        assertTrue(content.contains("""@Where(clause = "\"deleted\" = 0")"""))
        assertFalse(content.contains("import org.hibernate.annotations.DynamicInsert"))
        assertFalse(content.contains("import org.hibernate.annotations.DynamicUpdate"))
        assertFalse(content.contains("@DynamicInsert"))
        assertFalse(content.contains("@DynamicUpdate"))
    }
```

Keep `aggregate entity template keeps bounded imports and plain column when persistence controls are absent` unchanged except any necessary assertion message updates. It must still prove no Hibernate soft-delete imports appear when the context is absent.

- [ ] **Step 2: Run renderer test and verify failure first**

Run:

```powershell
./gradlew.bat :cap4k-plugin-pipeline-renderer-pebble:test --tests "com.only4.cap4k.plugin.pipeline.renderer.pebble.PebbleArtifactRendererTest.aggregate entity template renders structured soft delete context" --console=plain
```

Expected: FAIL because the template still ignores soft-delete context.

- [ ] **Step 3: Add gated Hibernate imports**

In `entity.kt.peb`, after `{{ use("jakarta.persistence.Table") -}}`, add:

```pebble
{% if softDelete is defined and softDelete.enabled -%}
{{ use("org.hibernate.annotations.SQLDelete") -}}
{{ use("org.hibernate.annotations.Where") -}}
{% endif -%}
```

Do not add `DynamicInsert` or `DynamicUpdate`.

- [ ] **Step 4: Add gated class-level annotations**

In `entity.kt.peb`, immediately after:

```pebble
{% if entityJpa.entityEnabled %}@Entity
@Table(name = "{{ entityJpa.tableName }}")
{% endif -%}
```

change the block to:

```pebble
{% if entityJpa.entityEnabled %}@Entity
@Table(name = "{{ entityJpa.tableName }}")
{% if softDelete is defined and softDelete.enabled %}@SQLDelete(sql = {{ softDeleteSqlKotlinStringLiteral | raw }})
@Where(clause = {{ softDeleteWhereClauseKotlinStringLiteral | raw }})
{% endif -%}
{% endif -%}
```

The annotation values must use the planner-produced Kotlin string literals. Do not render `softDeleteSql` or `softDeleteWhereClause` directly inside quotes, because quoted SQL contains identifier quotes that would otherwise generate invalid Kotlin.

- [ ] **Step 5: Run renderer tests and verify pass**

Run:

```powershell
./gradlew.bat :cap4k-plugin-pipeline-renderer-pebble:test --tests "com.only4.cap4k.plugin.pipeline.renderer.pebble.PebbleArtifactRendererTest" --console=plain
```

Expected: PASS for `PebbleArtifactRendererTest`.

- [ ] **Step 6: Commit Task 4**

```powershell
git add cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/entity.kt.peb cap4k-plugin-pipeline-renderer-pebble/src/test/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PebbleArtifactRendererTest.kt
git commit -m "feat: render aggregate soft delete annotations"
```

### Task 5: Functional Fixtures, Documentation, And Final Verification

**Files:**
- Modify: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-provider-persistence-sample/schema.sql`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-provider-persistence-compile-sample/schema.sql`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginFunctionalTest.kt`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginCompileFunctionalTest.kt`
- Modify: `docs/public/reference/db-schema-annotations.md`
- Modify: `docs/superpowers/specs/2026-07-20-cap4k-soft-delete-discriminator-policy-design.md`

**Interfaces:**
- Functional DB fixtures must satisfy the same active-default and assignability validation as production schemas.
- Public docs must describe `@Managed=deleted` as the self-id soft-delete discriminator contract, not a generic deleted flag.
- The spec must record the three resolved decisions from this planning discussion.

- [ ] **Step 1: Make provider-persistence fixtures schema-valid**

In both fixture SQL files, change each deleted column from:

```sql
deleted int not null comment '@Managed=deleted;',
```

to:

```sql
deleted bigint not null default 0 comment '@Managed=deleted;',
```

Files:

```text
cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-provider-persistence-sample/schema.sql
cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-provider-persistence-compile-sample/schema.sql
```

In `PipelinePluginFunctionalTest.aggregate provider persistence generation fails fast when uuid7 is applied to Long id`, update the inline audit-log schema in the same way:

```sql
deleted bigint not null default 0 comment '@Managed=deleted;',
```

Rationale: id is `bigint`, so `deleted int` is a narrowing tombstone column and must fail the new `SELF_ID` assignability rule.

In both native UUID id functional tests, replace only the first `@Managed=deleted;` marker in the copied fixture after converting `video_post.id` to UUID. Assert the generated UUID `VideoPost.kt` contains neither `@SQLDelete` nor `@Where`; those tests cover native UUID generation, not numeric `SELF_ID` soft delete.

- [ ] **Step 2: Update generation functional assertions**

In `PipelinePluginFunctionalTest.aggregate provider specific persistence generation renders bounded controls`, replace the old absence assertions:

```kotlin
assertFalse(generatedVideoPost.contains("@SQLDelete"))
assertFalse(generatedVideoPost.contains("@Where"))
assertFalse(generatedAuditLog.contains("@SQLDelete"))
assertFalse(generatedAuditLog.contains("@Where"))
```

with:

```kotlin
assertTrue(generatedVideoPost.contains("import org.hibernate.annotations.SQLDelete"))
assertTrue(generatedVideoPost.contains("import org.hibernate.annotations.Where"))
assertTrue(generatedVideoPost.contains("""@SQLDelete(sql = "update `video_post` set `deleted` = `id` where `id` = ? and `version` = ?")"""))
assertTrue(generatedVideoPost.contains("""@Where(clause = "`deleted` = 0")"""))
assertTrue(generatedAuditLog.contains("import org.hibernate.annotations.SQLDelete"))
assertTrue(generatedAuditLog.contains("import org.hibernate.annotations.Where"))
assertTrue(generatedAuditLog.contains("""@SQLDelete(sql = "update `audit_log` set `deleted` = `id` where `id` = ?")"""))
assertTrue(generatedAuditLog.contains("""@Where(clause = "`deleted` = 0")"""))
```

Keep these existing negative assertions:

```kotlin
assertFalse(generatedVideoPost.contains("@DynamicInsert"))
assertFalse(generatedVideoPost.contains("@DynamicUpdate"))
assertFalse(generatedVideoPost.contains("@GenericGenerator"))
assertFalse(generatedAuditLog.contains("@DynamicInsert"))
assertFalse(generatedAuditLog.contains("@DynamicUpdate"))
assertFalse(generatedAuditLog.contains("@GenericGenerator"))
```

- [ ] **Step 3: Update compile functional assertions**

In `PipelinePluginCompileFunctionalTest.aggregate provider specific persistence generation participates in domain compileKotlin`, replace the old absence assertions:

```kotlin
assertFalse(generatedVideoPost.contains("@SQLDelete"))
assertFalse(generatedVideoPost.contains("@Where"))
assertFalse(generatedAuditLog.contains("@SQLDelete"))
assertFalse(generatedAuditLog.contains("@Where"))
```

with the same positive assertions from Step 2.

Keep compile success assertions:

```kotlin
assertEquals(TaskOutcome.SUCCESS, compileResult.task(":cap4kGenerateSources")?.outcome)
assertTrue(compileResult.output.contains("BUILD SUCCESSFUL"))
```

- [ ] **Step 4: Run Gradle functional tests**

Run:

```powershell
./gradlew.bat :cap4k-plugin-pipeline-gradle:test --tests "com.only4.cap4k.plugin.pipeline.gradle.PipelinePluginFunctionalTest.aggregate provider specific persistence generation renders bounded controls" --tests "com.only4.cap4k.plugin.pipeline.gradle.PipelinePluginFunctionalTest.aggregate provider persistence generation fails fast when uuid7 is applied to Long id" --tests "com.only4.cap4k.plugin.pipeline.gradle.PipelinePluginCompileFunctionalTest.aggregate provider specific persistence generation participates in domain compileKotlin" --console=plain
```

Expected: PASS for these focused Gradle functional tests.

- [ ] **Step 5: Update public DB annotation docs**

In `docs/public/reference/db-schema-annotations.md`, replace the `@Managed=deleted` table entry:

```markdown
| `@Managed=deleted` | Marks a framework-managed deleted field. |
```

with:

```markdown
| `@Managed=deleted` | Marks a framework-managed self-id soft-delete discriminator. Active rows use `0`; deleted rows store the row id. |
```

Under `## Rules`, add:

```markdown
- `@Managed=deleted` requires a numeric, non-null column with a DB default compatible with `0`.
- For the `SELF_ID` tombstone strategy, the deleted column must be wide enough to store the id column value.
- Generated constructors may mirror an actual DB default discovered from schema, but must not synthesize `deleted = 0` when the DB default is absent.
- Aggregate projections do not inherit the active soft-delete filter in this iteration.
```

- [ ] **Step 6: Update the moved soft-delete spec decisions**

In `docs/superpowers/specs/2026-07-20-cap4k-soft-delete-discriminator-policy-design.md`, replace `## Open Questions` with:

```markdown
## Resolved Decisions

1. Generated constructors may mirror an actual DB default discovered from schema, but must not synthesize `deleted = 0` when the DB default is absent.
2. `SELF_ID` requires DB-storage-range assignability with conservative signedness checks. Proven widening is accepted; narrowing, unsafe signedness changes, non-numeric, and unproven mappings are rejected.
3. Aggregate projections do not inherit the active filter in this iteration. Projection filtering remains a read-model decision for a later design.
```

Also update the earlier validation bullet:

```markdown
5. The deleted column has no default value or generated default compatible with `0`, unless the generated constructor/template assigns `0` explicitly.
```

to:

```markdown
5. The deleted column has no default value or generated default compatible with `0`.
```

And update the template-context paragraph:

```markdown
The default template may still consume only `activePredicateSql` and `deleteAssignmentSql` at first.
```

to:

```markdown
The default template consumes planner-produced Kotlin string literals for `softDeleteSql` and `softDeleteWhereClause`, while structured policy fields remain available for overrides.
```

- [ ] **Step 7: Run focused module verification**

Run the focused checks from earlier tasks:

```powershell
./gradlew.bat :cap4k-plugin-pipeline-api:test --tests "com.only4.cap4k.plugin.pipeline.api.PipelineModelsTest" --console=plain
./gradlew.bat :cap4k-plugin-pipeline-core:test --tests "com.only4.cap4k.plugin.pipeline.core.DefaultCanonicalAssemblerTest" --console=plain
./gradlew.bat :cap4k-plugin-pipeline-generator-aggregate:test --tests "com.only4.cap4k.plugin.pipeline.generator.aggregate.AggregateArtifactPlannerTest" --console=plain
./gradlew.bat :cap4k-plugin-pipeline-renderer-pebble:test --tests "com.only4.cap4k.plugin.pipeline.renderer.pebble.PebbleArtifactRendererTest" --console=plain
./gradlew.bat :cap4k-plugin-pipeline-gradle:test --tests "com.only4.cap4k.plugin.pipeline.gradle.PipelinePluginFunctionalTest.aggregate provider specific persistence generation renders bounded controls" --tests "com.only4.cap4k.plugin.pipeline.gradle.PipelinePluginFunctionalTest.aggregate provider persistence generation fails fast when uuid7 is applied to Long id" --tests "com.only4.cap4k.plugin.pipeline.gradle.PipelinePluginCompileFunctionalTest.aggregate provider specific persistence generation participates in domain compileKotlin" --console=plain
```

Expected: all commands PASS.

- [ ] **Step 8: Run repository-level static validation**

Run:

```powershell
python scripts/validate-cap4k-generator-inputs.py
git diff --check
rg -n "softDeleteColumn" cap4k-plugin-pipeline-api cap4k-plugin-pipeline-core cap4k-plugin-pipeline-generator-aggregate cap4k-plugin-pipeline-renderer-pebble cap4k-plugin-pipeline-gradle --glob "!**/build/**"
```

Expected:
- `validate-cap4k-generator-inputs.py` passes.
- `git diff --check` has no whitespace errors.
- `softDeleteColumn` has no active source hits.

- [ ] **Step 9: Optional full regression**

If focused checks pass and time allows, run:

```powershell
./gradlew.bat --rerun-tasks test --console=plain
```

Record whether this full regression was run. Do not claim it passed if it was skipped.

- [ ] **Step 10: Commit Task 5**

```powershell
git add cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-provider-persistence-sample/schema.sql cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-provider-persistence-compile-sample/schema.sql cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginFunctionalTest.kt cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginCompileFunctionalTest.kt docs/public/reference/db-schema-annotations.md docs/superpowers/specs/2026-07-20-cap4k-soft-delete-discriminator-policy-design.md
git commit -m "test: cover self id soft delete generation"
```

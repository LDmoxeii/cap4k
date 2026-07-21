# cap4k Owned Relation Cardinality From Unique Constraints Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Infer owned child cardinality from primary-key and unique-constraint metadata, keep owned persistence collection-backed, and generate an entity-level nullable single-child accessor/mutator for `ownedCardinality=ONE`.

**Architecture:** PR #119 already supplies a strict DB annotation contract with explicit `@ParentRef` and `DbManagedRole`; PR #126 already supplies semantic `AggregateSoftDeletePolicy` while keeping `@Managed=deleted` available as column metadata. This plan adds owned-cardinality metadata to the canonical relation model, resolves it during owned parent-child relation inference, projects it into aggregate entity render context, and changes only the default entity template branch for owned one-child relations. Persistence shape remains `@OneToMany + @JoinColumn + MutableList` through a private backing collection.

**Tech Stack:** Kotlin, Gradle multi-module tests, JUnit 5, Pebble templates, JDBC metadata snapshots, Hibernate/Jakarta persistence annotations in generated Kotlin entities.

## Global Constraints

- PR #119 is the active DB input contract: table annotations are only `@Parent=<table>` and `@Ignore`; column annotations include `@ParentRef`, `@Managed=scope`, `@Managed=deleted`, `@Managed=system`, and `@Managed=version`.
- PR #119 removed fallback owned binding to `<parent_table>_id`; owned child tables with `@Parent` must fail fast unless exactly one column has `@ParentRef`.
- PR #126 is the active soft-delete policy contract: `AggregatePersistenceProviderControl.softDelete` replaces `softDeleteColumn`, and `@Managed=deleted` resolves to a semantic `SELF_ID` policy only when soft delete is provider-enabled.
- Owned cardinality inference must not use physical database foreign-key metadata.
- Do not introduce `@One`, `@Count`, `@Relation`, `@ParentCardinality`, or any removed annotation alias.
- `@Managed=scope` and `@Managed=deleted` are cardinality-neutral only when they are explicitly declared and the participating unique-constraint columns are non-null.
- `@Managed=system` and `@Managed=version` are not cardinality-neutral.
- In the first implementation, both `ownedCardinality=ONE` and `ownedCardinality=MANY` keep `relationType = ONE_TO_MANY` and `persistenceShape = ONE_TO_MANY_JOIN_COLUMN`.
- For `ownedCardinality=ONE`, the generated entity exposes a nullable single-child property in generated entity code, not in checked-in `*Behavior.kt`.
- The one-child getter must fail fast when the backing collection has more than one loaded child; the setter must replace the backing collection contents.
- Avoid installing dependencies; use the existing Gradle wrapper and focused tests.

---

## File Structure

- Modify `cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModels.kt`
  - Add `OwnedRelationCardinality`.
  - Add `OwnedRelationPersistenceShape`.
  - Extend `AggregateRelationModel` with owned metadata and generated API names using default values for compatibility.
- Modify `cap4k-plugin-pipeline-api/src/test/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModelsTest.kt`
  - Lock the public canonical relation contract.
- Create `cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/OwnedRelationCardinalityInference.kt`
  - Infer `ONE` or `MANY` from one `@ParentRef`, primary key, unique constraints, and managed-role metadata.
- Create `cap4k-plugin-pipeline-core/src/test/kotlin/com/only4/cap4k/plugin/pipeline/core/OwnedRelationCardinalityInferenceTest.kt`
  - Cover the PK, unique, scope/deleted, business-column, nullable, system, and version cases directly.
- Modify `cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/AggregateRelationInference.kt`
  - Wire cardinality into owned relation creation.
  - Derive both collection and single-child field names from the parent/child table-name stem.
  - Fail fast on generated single-accessor name collisions.
- Modify `cap4k-plugin-pipeline-core/src/test/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssemblerTest.kt`
  - Prove canonical assembly exposes owned cardinality while keeping `ONE_TO_MANY` persistence.
- Modify `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateRelationPlanning.kt`
  - Project owned metadata into `relationFields`.
  - Add `jakarta.persistence.Transient` import when a one-child owned relation is present.
- Modify `cap4k-plugin-pipeline-generator-aggregate/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateArtifactPlannerTest.kt`
  - Assert relation context contains `owned`, `parentRefColumn`, `ownedCardinality`, `persistenceShape`, `backingCollectionName`, and `singleAccessorName`.
- Modify `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/entity.kt.peb`
  - Render private collection-backed persistence plus public computed property for `ownedCardinality=ONE`.
  - Keep the existing public `MutableList` rendering for `ownedCardinality=MANY`.
- Modify `cap4k-plugin-pipeline-renderer-pebble/src/test/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PebbleArtifactRendererTest.kt`
  - Assert generated Kotlin contains the hidden backing collection, `@get:Transient`, fail-fast getter, and replace setter.
- Modify `cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-relation-sample/schema.sql`
  - Add a one-child owned fixture table using `unique(video_post_id)`.
- Modify `cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-relation-compile-sample/schema.sql`
  - Add the same compile fixture table.
- Modify `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginFunctionalTest.kt`
  - Assert generated relation sample output differentiates MANY (`items`) and ONE (`file`).
- Modify `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginCompileFunctionalTest.kt`
  - Assert customized behavior code can compile against `items.add(...)` and `file = ...`.

### Task 1: API Model And Cardinality Inference Unit

**Files:**
- Modify: `cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModels.kt`
- Modify: `cap4k-plugin-pipeline-api/src/test/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModelsTest.kt`
- Create: `cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/OwnedRelationCardinalityInference.kt`
- Create: `cap4k-plugin-pipeline-core/src/test/kotlin/com/only4/cap4k/plugin/pipeline/core/OwnedRelationCardinalityInferenceTest.kt`

**Interfaces:**
- Consumes:
  - `OwnedParentBinding(childTable: DbTableSnapshot, parentTable: String, parentRefColumn: DbColumnSnapshot)`
  - `DbTableSnapshot.primaryKey`
  - `DbTableSnapshot.uniqueConstraints`
  - `DbColumnSnapshot.parentRef`
  - `DbColumnSnapshot.managedRole`
  - `DbColumnSnapshot.nullable`
- Produces:
  - `enum class OwnedRelationCardinality { ONE, MANY }`
  - `enum class OwnedRelationPersistenceShape { ONE_TO_MANY_JOIN_COLUMN }`
  - `AggregateRelationModel.owned: Boolean`
  - `AggregateRelationModel.parentRefColumn: String?`
  - `AggregateRelationModel.ownedCardinality: OwnedRelationCardinality?`
  - `AggregateRelationModel.persistenceShape: OwnedRelationPersistenceShape?`
  - `AggregateRelationModel.backingCollectionName: String?`
  - `AggregateRelationModel.singleAccessorName: String?`
  - `OwnedRelationCardinalityInference.infer(binding: OwnedParentBinding): OwnedRelationCardinality`

- [ ] **Step 1: Write the failing API model test**

Append this test to `PipelineModelsTest` before the final closing brace:

```kotlin
    @Test
    fun `aggregate relation model carries owned cardinality separately from persistence type`() {
        val relation = AggregateRelationModel(
            ownerEntityName = "VideoPost",
            ownerEntityPackageName = "com.acme.demo.domain.aggregates.video_post",
            fieldName = "files",
            targetEntityName = "VideoPostFile",
            targetEntityPackageName = "com.acme.demo.domain.aggregates.video_post",
            relationType = AggregateRelationType.ONE_TO_MANY,
            joinColumn = "video_post_id",
            fetchType = AggregateFetchType.LAZY,
            nullable = false,
            owned = true,
            parentRefColumn = "video_post_id",
            ownedCardinality = OwnedRelationCardinality.ONE,
            persistenceShape = OwnedRelationPersistenceShape.ONE_TO_MANY_JOIN_COLUMN,
            backingCollectionName = "files",
            singleAccessorName = "file",
        )

        assertEquals(AggregateRelationType.ONE_TO_MANY, relation.relationType)
        assertTrue(relation.owned)
        assertEquals("video_post_id", relation.parentRefColumn)
        assertEquals(OwnedRelationCardinality.ONE, relation.ownedCardinality)
        assertEquals(OwnedRelationPersistenceShape.ONE_TO_MANY_JOIN_COLUMN, relation.persistenceShape)
        assertEquals("files", relation.backingCollectionName)
        assertEquals("file", relation.singleAccessorName)
    }
```

- [ ] **Step 2: Run the API test and verify it fails for missing symbols**

Run:

```powershell
./gradlew.bat :cap4k-plugin-pipeline-api:test --tests "com.only4.cap4k.plugin.pipeline.api.PipelineModelsTest.aggregate relation model carries owned cardinality separately from persistence type" --console=plain
```

Expected: FAIL to compile with unresolved references for `OwnedRelationCardinality`, `OwnedRelationPersistenceShape`, and/or unknown named arguments on `AggregateRelationModel`.

- [ ] **Step 3: Add the API model fields**

In `PipelineModels.kt`, insert these enums immediately before `data class AggregateRelationModel`:

```kotlin
enum class OwnedRelationCardinality {
    ONE,
    MANY,
}

enum class OwnedRelationPersistenceShape {
    ONE_TO_MANY_JOIN_COLUMN,
}
```

Then replace the current `AggregateRelationModel` declaration with this version:

```kotlin
data class AggregateRelationModel(
    val ownerEntityName: String,
    val ownerEntityPackageName: String,
    val fieldName: String,
    val targetEntityName: String,
    val targetEntityPackageName: String,
    val relationType: AggregateRelationType,
    val joinColumn: String,
    val fetchType: AggregateFetchType,
    val nullable: Boolean,
    val cascadeTypes: List<AggregateCascadeType> = emptyList(),
    val orphanRemoval: Boolean = false,
    val joinColumnNullable: Boolean? = null,
    val owned: Boolean = false,
    val parentRefColumn: String? = null,
    val ownedCardinality: OwnedRelationCardinality? = null,
    val persistenceShape: OwnedRelationPersistenceShape? = null,
    val backingCollectionName: String? = null,
    val singleAccessorName: String? = null,
)
```

- [ ] **Step 4: Run the API tests and verify the model contract passes**

Run:

```powershell
./gradlew.bat :cap4k-plugin-pipeline-api:test --tests "com.only4.cap4k.plugin.pipeline.api.PipelineModelsTest" --console=plain
```

Expected: PASS for `PipelineModelsTest`.

- [ ] **Step 5: Write focused cardinality inference tests**

Create `OwnedRelationCardinalityInferenceTest.kt` with this content:

```kotlin
package com.only4.cap4k.plugin.pipeline.core

import com.only4.cap4k.plugin.pipeline.api.DbColumnSnapshot
import com.only4.cap4k.plugin.pipeline.api.DbManagedRole
import com.only4.cap4k.plugin.pipeline.api.DbTableSnapshot
import com.only4.cap4k.plugin.pipeline.api.OwnedRelationCardinality
import com.only4.cap4k.plugin.pipeline.api.UniqueConstraintModel
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class OwnedRelationCardinalityInferenceTest {
    @Test
    fun `primary key parent ref infers one`() {
        val binding = binding(
            columns = listOf(parentRef("video_post_id")),
            primaryKey = listOf("VIDEO_POST_ID"),
        )

        assertEquals(OwnedRelationCardinality.ONE, OwnedRelationCardinalityInference.infer(binding))
    }

    @Test
    fun `unique parent ref infers one independent of case`() {
        val binding = binding(
            columns = listOf(
                id(),
                parentRef("video_post_id"),
            ),
            uniqueConstraints = listOf(unique("uk_parent", "VIDEO_POST_ID")),
        )

        assertEquals(OwnedRelationCardinality.ONE, OwnedRelationCardinalityInference.infer(binding))
    }

    @Test
    fun `unique parent ref plus non null deleted discriminator infers one`() {
        val binding = binding(
            columns = listOf(
                id(),
                parentRef("video_post_id"),
                column("deleted", managedRole = DbManagedRole.DELETED),
            ),
            uniqueConstraints = listOf(unique("uk_parent_deleted", "deleted", "video_post_id")),
        )

        assertEquals(OwnedRelationCardinality.ONE, OwnedRelationCardinalityInference.infer(binding))
    }

    @Test
    fun `unique parent ref plus non null scope discriminator infers one`() {
        val binding = binding(
            columns = listOf(
                id(),
                column("tenant_id", managedRole = DbManagedRole.SCOPE),
                parentRef("video_post_id"),
            ),
            uniqueConstraints = listOf(unique("uk_scope_parent", "tenant_id", "video_post_id")),
        )

        assertEquals(OwnedRelationCardinality.ONE, OwnedRelationCardinalityInference.infer(binding))
    }

    @Test
    fun `unique parent ref plus scope and deleted infers one only when both roles are declared and non null`() {
        val binding = binding(
            columns = listOf(
                id(),
                column("tenant_id", managedRole = DbManagedRole.SCOPE),
                parentRef("video_post_id"),
                column("deleted", managedRole = DbManagedRole.DELETED),
            ),
            uniqueConstraints = listOf(unique("uk_scope_parent_deleted", "deleted", "video_post_id", "tenant_id")),
        )

        assertEquals(OwnedRelationCardinality.ONE, OwnedRelationCardinalityInference.infer(binding))
    }

    @Test
    fun `unique parent ref plus business column infers many`() {
        val binding = binding(
            columns = listOf(
                id(),
                parentRef("video_post_id"),
                column("code"),
            ),
            uniqueConstraints = listOf(unique("uk_parent_code", "video_post_id", "code")),
        )

        assertEquals(OwnedRelationCardinality.MANY, OwnedRelationCardinalityInference.infer(binding))
    }

    @Test
    fun `unique parent ref plus version infers many`() {
        val binding = binding(
            columns = listOf(
                id(),
                parentRef("video_post_id"),
                column("version", managedRole = DbManagedRole.VERSION),
            ),
            uniqueConstraints = listOf(unique("uk_parent_version", "video_post_id", "version")),
        )

        assertEquals(OwnedRelationCardinality.MANY, OwnedRelationCardinalityInference.infer(binding))
    }

    @Test
    fun `unique parent ref plus system field infers many`() {
        val binding = binding(
            columns = listOf(
                id(),
                parentRef("video_post_id"),
                column("created_by", managedRole = DbManagedRole.SYSTEM),
            ),
            uniqueConstraints = listOf(unique("uk_parent_created_by", "video_post_id", "created_by")),
        )

        assertEquals(OwnedRelationCardinality.MANY, OwnedRelationCardinalityInference.infer(binding))
    }

    @Test
    fun `nullable scope or deleted columns do not prove one`() {
        val nullableScope = binding(
            columns = listOf(
                id(),
                column("tenant_id", nullable = true, managedRole = DbManagedRole.SCOPE),
                parentRef("video_post_id"),
            ),
            uniqueConstraints = listOf(unique("uk_scope_parent", "tenant_id", "video_post_id")),
        )
        val nullableDeleted = binding(
            columns = listOf(
                id(),
                parentRef("video_post_id"),
                column("deleted", nullable = true, managedRole = DbManagedRole.DELETED),
            ),
            uniqueConstraints = listOf(unique("uk_parent_deleted", "video_post_id", "deleted")),
        )

        assertEquals(OwnedRelationCardinality.MANY, OwnedRelationCardinalityInference.infer(nullableScope))
        assertEquals(OwnedRelationCardinality.MANY, OwnedRelationCardinalityInference.infer(nullableDeleted))
    }

    @Test
    fun `unique without parent ref infers many`() {
        val binding = binding(
            columns = listOf(
                id(),
                parentRef("video_post_id"),
                column("code"),
            ),
            uniqueConstraints = listOf(unique("uk_code", "code")),
        )

        assertEquals(OwnedRelationCardinality.MANY, OwnedRelationCardinalityInference.infer(binding))
    }

    private fun binding(
        columns: List<DbColumnSnapshot>,
        primaryKey: List<String> = listOf("id"),
        uniqueConstraints: List<UniqueConstraintModel> = emptyList(),
    ): OwnedParentBinding {
        val child = DbTableSnapshot(
            tableName = "video_post_file",
            comment = "",
            columns = columns,
            primaryKey = primaryKey,
            uniqueConstraints = uniqueConstraints,
            parentTable = "video_post",
            aggregateRoot = false,
        )
        return OwnedParentBinding(
            childTable = child,
            parentTable = "video_post",
            parentRefColumn = columns.single { it.parentRef },
        )
    }

    private fun id(): DbColumnSnapshot = column("id", primaryKey = true)

    private fun parentRef(name: String): DbColumnSnapshot = column(name, parentRef = true)

    private fun column(
        name: String,
        nullable: Boolean = false,
        primaryKey: Boolean = false,
        parentRef: Boolean = false,
        managedRole: DbManagedRole? = null,
    ): DbColumnSnapshot = DbColumnSnapshot(
        name = name,
        dbType = "BIGINT",
        kotlinType = "Long",
        nullable = nullable,
        isPrimaryKey = primaryKey,
        parentRef = parentRef,
        managedRole = managedRole,
    )

    private fun unique(name: String, vararg columns: String): UniqueConstraintModel =
        UniqueConstraintModel(physicalName = name, columns = columns.toList())
}
```

- [ ] **Step 6: Run the new core test and verify it fails for missing inferencer**

Run:

```powershell
./gradlew.bat :cap4k-plugin-pipeline-core:test --tests "com.only4.cap4k.plugin.pipeline.core.OwnedRelationCardinalityInferenceTest" --console=plain
```

Expected: FAIL to compile with unresolved reference `OwnedRelationCardinalityInference`.

- [ ] **Step 7: Implement the inferencer**

Create `OwnedRelationCardinalityInference.kt` with this content:

```kotlin
package com.only4.cap4k.plugin.pipeline.core

import com.only4.cap4k.plugin.pipeline.api.DbManagedRole
import com.only4.cap4k.plugin.pipeline.api.OwnedRelationCardinality
import java.util.Locale

internal object OwnedRelationCardinalityInference {
    fun infer(binding: OwnedParentBinding): OwnedRelationCardinality {
        val child = binding.childTable
        val parentRefKey = columnKey(binding.parentRefColumn.name)

        if (child.primaryKey.map(::columnKey) == listOf(parentRefKey)) {
            return OwnedRelationCardinality.ONE
        }

        val columnsByKey = child.columns.associateBy { columnKey(it.name) }
        val scopeColumnKeys = child.columns
            .filter { it.managedRole == DbManagedRole.SCOPE }
            .mapTo(mutableSetOf()) { columnKey(it.name) }
        val deletedColumnKeys = child.columns
            .filter { it.managedRole == DbManagedRole.DELETED }
            .mapTo(mutableSetOf()) { columnKey(it.name) }
        val neutralColumnKeys = buildSet {
            add(parentRefKey)
            addAll(scopeColumnKeys)
            addAll(deletedColumnKeys)
        }

        val hasOneProvingUniqueConstraint = child.uniqueConstraints.any { constraint ->
            val constraintColumnKeys = constraint.columns.mapTo(linkedSetOf(), ::columnKey)
            parentRefKey in constraintColumnKeys &&
                constraintColumnKeys.minus(neutralColumnKeys).isEmpty() &&
                constraintColumnKeys
                    .filter { it in scopeColumnKeys || it in deletedColumnKeys }
                    .all { columnKey -> columnsByKey[columnKey]?.nullable == false }
        }

        return if (hasOneProvingUniqueConstraint) {
            OwnedRelationCardinality.ONE
        } else {
            OwnedRelationCardinality.MANY
        }
    }

    private fun columnKey(columnName: String): String = columnName.lowercase(Locale.ROOT)
}
```

- [ ] **Step 8: Run the focused API and inferencer tests**

Run:

```powershell
./gradlew.bat :cap4k-plugin-pipeline-api:test --tests "com.only4.cap4k.plugin.pipeline.api.PipelineModelsTest" --console=plain
./gradlew.bat :cap4k-plugin-pipeline-core:test --tests "com.only4.cap4k.plugin.pipeline.core.OwnedRelationCardinalityInferenceTest" --console=plain
```

Expected: both commands PASS.

- [ ] **Step 9: Commit Task 1**

```powershell
git add cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModels.kt cap4k-plugin-pipeline-api/src/test/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModelsTest.kt cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/OwnedRelationCardinalityInference.kt cap4k-plugin-pipeline-core/src/test/kotlin/com/only4/cap4k/plugin/pipeline/core/OwnedRelationCardinalityInferenceTest.kt
git commit -m "feat: infer owned relation cardinality"
```
### Task 2: Canonical Owned Relation Wiring

**Files:**
- Modify: `cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/AggregateRelationInference.kt`
- Modify: `cap4k-plugin-pipeline-core/src/test/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssemblerTest.kt`

**Interfaces:**
- Consumes:
  - `OwnedRelationCardinalityInference.infer(binding: OwnedParentBinding): OwnedRelationCardinality` from Task 1
  - `OwnedRelationPersistenceShape.ONE_TO_MANY_JOIN_COLUMN` from Task 1
- Produces:
  - Owned parent-child relations where:
    - `relationType == AggregateRelationType.ONE_TO_MANY`
    - `owned == true`
    - `parentRefColumn == joinColumn`
    - `ownedCardinality == ONE | MANY`
    - `persistenceShape == ONE_TO_MANY_JOIN_COLUMN`
    - `backingCollectionName == fieldName`
    - `singleAccessorName` is set only when `ownedCardinality == ONE`

- [ ] **Step 1: Write canonical assembly tests for ONE and MANY**

In `DefaultCanonicalAssemblerTest`, add these imports if missing:

```kotlin
import com.only4.cap4k.plugin.pipeline.api.OwnedRelationCardinality
import com.only4.cap4k.plugin.pipeline.api.OwnedRelationPersistenceShape
```

Add these tests near the current owned parent binding tests:

```kotlin
    @Test
    fun `owned relation unique parent ref infers one while keeping one to many persistence type`() {
        val result = assembleAggregate(
            aggregateProjectConfig(),
            listOf(
                table(
                    name = "video_post",
                    columns = listOf(
                        column("id", "BIGINT", "Long", false, primaryKey = true),
                    ),
                    primaryKey = listOf("id"),
                ),
                table(
                    name = "video_post_file",
                    parentTable = "video_post",
                    columns = listOf(
                        column("id", "BIGINT", "Long", false, primaryKey = true),
                        column("video_post_id", "BIGINT", "Long", false, parentRef = true),
                        column("storage_key", "VARCHAR", "String", false),
                    ),
                    primaryKey = listOf("id"),
                    uniqueConstraints = listOf(
                        UniqueConstraintModel(
                            physicalName = "uk_video_post_file_parent",
                            columns = listOf("video_post_id"),
                        )
                    ),
                    aggregateRoot = false,
                ),
            ),
        )

        val relation = result.model.aggregateRelations.single()

        assertEquals(AggregateRelationType.ONE_TO_MANY, relation.relationType)
        assertEquals("files", relation.fieldName)
        assertEquals("video_post_id", relation.joinColumn)
        assertEquals(true, relation.owned)
        assertEquals("video_post_id", relation.parentRefColumn)
        assertEquals(OwnedRelationCardinality.ONE, relation.ownedCardinality)
        assertEquals(OwnedRelationPersistenceShape.ONE_TO_MANY_JOIN_COLUMN, relation.persistenceShape)
        assertEquals("files", relation.backingCollectionName)
        assertEquals("file", relation.singleAccessorName)
    }

    @Test
    fun `owned relation unique parent ref plus business column infers many`() {
        val result = assembleAggregate(
            aggregateProjectConfig(),
            listOf(
                table(
                    name = "video_post",
                    columns = listOf(column("id", "BIGINT", "Long", false, primaryKey = true)),
                    primaryKey = listOf("id"),
                ),
                table(
                    name = "video_post_file",
                    parentTable = "video_post",
                    columns = listOf(
                        column("id", "BIGINT", "Long", false, primaryKey = true),
                        column("video_post_id", "BIGINT", "Long", false, parentRef = true),
                        column("storage_key", "VARCHAR", "String", false),
                    ),
                    primaryKey = listOf("id"),
                    uniqueConstraints = listOf(
                        UniqueConstraintModel(
                            physicalName = "uk_video_post_file_parent_storage",
                            columns = listOf("video_post_id", "storage_key"),
                        )
                    ),
                    aggregateRoot = false,
                ),
            ),
        )

        val relation = result.model.aggregateRelations.single()

        assertEquals(AggregateRelationType.ONE_TO_MANY, relation.relationType)
        assertEquals(true, relation.owned)
        assertEquals(OwnedRelationCardinality.MANY, relation.ownedCardinality)
        assertEquals(OwnedRelationPersistenceShape.ONE_TO_MANY_JOIN_COLUMN, relation.persistenceShape)
        assertEquals("files", relation.backingCollectionName)
        assertEquals(null, relation.singleAccessorName)
    }
```

- [ ] **Step 2: Write collision tests for generated single-child accessor names**

Add these tests near the relation field collision tests:

```kotlin
    @Test
    fun `owned one relation single accessor cannot collide with scalar field`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            assembleAggregate(
                aggregateProjectConfig(),
                listOf(
                    table(
                        name = "video_post",
                        columns = listOf(
                            column("id", "BIGINT", "Long", false, primaryKey = true),
                            column("file", "VARCHAR", "String", false),
                        ),
                        primaryKey = listOf("id"),
                    ),
                    table(
                        name = "video_post_file",
                        parentTable = "video_post",
                        columns = listOf(
                            column("id", "BIGINT", "Long", false, primaryKey = true),
                            column("video_post_id", "BIGINT", "Long", false, parentRef = true),
                        ),
                        primaryKey = listOf("id"),
                        uniqueConstraints = listOf(uniqueConstraint("uk_file_parent", "video_post_id")),
                        aggregateRoot = false,
                    ),
                ),
            )
        }

        assertEquals(
            "owned one relation single accessor collides with scalar field: VideoPost.file -> VideoPostFile",
            error.message,
        )
    }

    @Test
    fun `owned one relation single accessor cannot collide with its backing relation field`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            assembleAggregate(
                aggregateProjectConfig(),
                listOf(
                    table(
                        name = "video_post",
                        columns = listOf(column("id", "BIGINT", "Long", false, primaryKey = true)),
                        primaryKey = listOf("id"),
                    ),
                    table(
                        name = "video_post_fish",
                        parentTable = "video_post",
                        columns = listOf(
                            column("id", "BIGINT", "Long", false, primaryKey = true),
                            column("video_post_id", "BIGINT", "Long", false, parentRef = true),
                        ),
                        primaryKey = listOf("id"),
                        uniqueConstraints = listOf(uniqueConstraint("uk_fish_parent", "video_post_id")),
                        aggregateRoot = false,
                    ),
                ),
            )
        }

        assertEquals(
            "owned one relation single accessor collides with relation field: VideoPost.fish -> VideoPostFish",
            error.message,
        )
    }
```

If `DefaultCanonicalAssemblerTest` does not already have a `uniqueConstraint` helper, add this helper near the other private helpers:

```kotlin
    private fun uniqueConstraint(physicalName: String, vararg columns: String): UniqueConstraintModel =
        UniqueConstraintModel(
            physicalName = physicalName,
            columns = columns.toList(),
        )
```

- [ ] **Step 3: Run the canonical tests and verify they fail before wiring**

Run:

```powershell
./gradlew.bat :cap4k-plugin-pipeline-core:test --tests "com.only4.cap4k.plugin.pipeline.core.DefaultCanonicalAssemblerTest.owned relation unique parent ref infers one while keeping one to many persistence type" --tests "com.only4.cap4k.plugin.pipeline.core.DefaultCanonicalAssemblerTest.owned relation unique parent ref plus business column infers many" --tests "com.only4.cap4k.plugin.pipeline.core.DefaultCanonicalAssemblerTest.owned one relation single accessor cannot collide with scalar field" --tests "com.only4.cap4k.plugin.pipeline.core.DefaultCanonicalAssemblerTest.owned one relation single accessor cannot collide with its backing relation field" --console=plain
```

Expected: FAIL because `AggregateRelationInference` does not populate owned-cardinality metadata or single-accessor collision validation yet.

- [ ] **Step 4: Update relation field-name derivation and owned relation construction**

In `AggregateRelationInference.kt`, add imports:

```kotlin
import com.only4.cap4k.plugin.pipeline.api.OwnedRelationCardinality
import com.only4.cap4k.plugin.pipeline.api.OwnedRelationPersistenceShape
```

Add this private data class near the existing private data classes:

```kotlin
    private data class OwnedRelationFieldNames(
        val collectionName: String,
        val singleName: String,
    )
```

Replace the current `parentChildRelations` mapping with this version:

```kotlin
        val parentChildRelations = parentBindings
            .map { binding ->
                val child = binding.childTable
                val parentTable = binding.parentTable
                val parentKey = tableKey(parentTable)
                val resolvedParent = requireNotNull(entityLookup[parentKey]) {
                    "unknown parent table: ${child.parentTable}"
                }
                val target = requireNotNull(entityLookup[tableKey(child.tableName)]) {
                    "unknown child table: ${child.tableName}"
                }
                val cardinality = OwnedRelationCardinalityInference.infer(binding)
                val fieldNames = parentChildFieldNames(parentTable, child.tableName)
                AggregateRelationModel(
                    ownerEntityName = resolvedParent.entityName,
                    ownerEntityPackageName = resolvedParent.packageName,
                    fieldName = fieldNames.collectionName,
                    targetEntityName = target.entityName,
                    targetEntityPackageName = target.packageName,
                    relationType = AggregateRelationType.ONE_TO_MANY,
                    joinColumn = binding.parentRefColumn.name,
                    fetchType = AggregateFetchType.LAZY,
                    nullable = false,
                    cascadeTypes = listOf(
                        AggregateCascadeType.PERSIST,
                        AggregateCascadeType.MERGE,
                        AggregateCascadeType.REMOVE,
                    ),
                    orphanRemoval = true,
                    joinColumnNullable = false,
                    owned = true,
                    parentRefColumn = binding.parentRefColumn.name,
                    ownedCardinality = cardinality,
                    persistenceShape = OwnedRelationPersistenceShape.ONE_TO_MANY_JOIN_COLUMN,
                    backingCollectionName = fieldNames.collectionName,
                    singleAccessorName = if (cardinality == OwnedRelationCardinality.ONE) fieldNames.singleName else null,
                )
            }
```

Replace `private fun parentChildFieldName(...)` with this version:

```kotlin
    private fun parentChildFieldNames(parentTableName: String, childTableName: String): OwnedRelationFieldNames {
        val parentTokens = tableNameTokens(parentTableName)
        val childTokens = tableNameTokens(childTableName)
        val stemTokens = if (
            childTokens.size > parentTokens.size &&
            childTokens.take(parentTokens.size) == parentTokens
        ) {
            childTokens.drop(parentTokens.size)
        } else {
            childTokens
        }
        val nonEmptyStemTokens = stemTokens.ifEmpty { childTokens }
        val singleName = tokensToLowerCamel(nonEmptyStemTokens)
        val collectionName = tokensToLowerCamel(
            nonEmptyStemTokens.dropLast(1) + RelationInflector.pluralizeStable(nonEmptyStemTokens.last())
        )
        return OwnedRelationFieldNames(
            collectionName = collectionName,
            singleName = singleName,
        )
    }
```

Then insert this validation before the final `return relations`:

```kotlin
        validateOwnedOneSingleAccessorCollisions(
            relations = relations,
            scalarFieldsByEntity = scalarFieldsByEntity,
        )
```

Add this helper near the other private helpers:

```kotlin
    private fun validateOwnedOneSingleAccessorCollisions(
        relations: List<AggregateRelationModel>,
        scalarFieldsByEntity: Map<String, ScalarFields>,
    ) {
        val relationFieldNamesByOwner = relations
            .groupBy { it.ownerEntityName }
            .mapValues { (_, ownerRelations) -> ownerRelations.map { it.fieldName }.toSet() }

        val duplicateSingleAccessor = relations
            .filter { it.ownedCardinality == OwnedRelationCardinality.ONE }
            .mapNotNull { relation -> relation.singleAccessorName?.let { relation to it } }
            .groupBy { (relation, singleAccessorName) -> relation.ownerEntityName to singleAccessorName }
            .entries
            .firstOrNull { (_, candidates) -> candidates.size > 1 }
        if (duplicateSingleAccessor != null) {
            val (_, candidates) = duplicateSingleAccessor
            val first = candidates.first().first
            val accessor = candidates.first().second
            val targets = candidates.joinToString(", ") { it.first.targetEntityName }
            throw IllegalArgumentException(
                "owned one relation single accessor collision: ${first.ownerEntityName}.$accessor -> $targets"
            )
        }

        relations
            .filter { it.ownedCardinality == OwnedRelationCardinality.ONE }
            .forEach { relation ->
                val singleAccessorName = relation.singleAccessorName ?: return@forEach
                val scalarFields = scalarFieldsByEntity.getValue(relation.ownerEntityName)
                if (singleAccessorName in scalarFields.columnNamesByFieldName.keys) {
                    throw IllegalArgumentException(
                        "owned one relation single accessor collides with scalar field: " +
                            "${relation.ownerEntityName}.$singleAccessorName -> ${relation.targetEntityName}"
                    )
                }
                if (singleAccessorName in relationFieldNamesByOwner.getValue(relation.ownerEntityName)) {
                    throw IllegalArgumentException(
                        "owned one relation single accessor collides with relation field: " +
                            "${relation.ownerEntityName}.$singleAccessorName -> ${relation.targetEntityName}"
                    )
                }
            }
    }
```

- [ ] **Step 5: Run core relation tests**

Run:

```powershell
./gradlew.bat :cap4k-plugin-pipeline-core:test --tests "com.only4.cap4k.plugin.pipeline.core.OwnedRelationCardinalityInferenceTest" --tests "com.only4.cap4k.plugin.pipeline.core.DefaultCanonicalAssemblerTest" --console=plain
```

Expected: PASS for `OwnedRelationCardinalityInferenceTest` and `DefaultCanonicalAssemblerTest`.

- [ ] **Step 6: Commit Task 2**

```powershell
git add cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/AggregateRelationInference.kt cap4k-plugin-pipeline-core/src/test/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssemblerTest.kt
git commit -m "feat: carry owned cardinality through canonical relations"
```

### Task 3: Aggregate Generator Relation Context

**Files:**
- Modify: `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateRelationPlanning.kt`
- Modify: `cap4k-plugin-pipeline-generator-aggregate/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateArtifactPlannerTest.kt`

**Interfaces:**
- Consumes `AggregateRelationModel` fields from Task 1 and Task 2.
- Produces relation context fields:
  - `owned: Boolean`
  - `parentRefColumn: String?`
  - `ownedCardinality: String?`
  - `persistenceShape: String?`
  - `backingCollectionName: String?`
  - `singleAccessorName: String?`
- Produces `jpaImports` containing `jakarta.persistence.Transient` only when at least one owner relation has `ownedCardinality=ONE`.

- [ ] **Step 1: Write the generator context test**

In `AggregateArtifactPlannerTest`, add imports if missing:

```kotlin
import com.only4.cap4k.plugin.pipeline.api.OwnedRelationCardinality
import com.only4.cap4k.plugin.pipeline.api.OwnedRelationPersistenceShape
```

Add this test near `entity planner keeps one to many owner relation as collection field context`:

```kotlin
    @Test
    fun `entity planner exposes owned one relation metadata while keeping one to many relation type`() {
        val entity = EntityModel(
            name = "VideoPost",
            packageName = "com.acme.demo.domain.aggregates.video_post",
            tableName = "video_post",
            comment = "video post",
            fields = listOf(
                FieldModel("id", "Long", columnName = "id"),
                FieldModel("title", "String", columnName = "title"),
            ),
            idField = FieldModel("id", "Long", columnName = "id"),
        )
        val plan = AggregateArtifactPlanner().plan(
            aggregateConfig(),
            CanonicalModel(
                entities = listOf(entity),
                aggregateEntityJpa = listOf(defaultAggregateEntityJpa(entity)),
                aggregateRelations = listOf(
                    AggregateRelationModel(
                        ownerEntityName = "VideoPost",
                        ownerEntityPackageName = "com.acme.demo.domain.aggregates.video_post",
                        fieldName = "files",
                        targetEntityName = "VideoPostFile",
                        targetEntityPackageName = "com.acme.demo.domain.aggregates.video_post",
                        relationType = AggregateRelationType.ONE_TO_MANY,
                        joinColumn = "video_post_id",
                        fetchType = AggregateFetchType.LAZY,
                        nullable = false,
                        cascadeTypes = listOf(AggregateCascadeType.PERSIST, AggregateCascadeType.MERGE, AggregateCascadeType.REMOVE),
                        orphanRemoval = true,
                        joinColumnNullable = false,
                        owned = true,
                        parentRefColumn = "video_post_id",
                        ownedCardinality = OwnedRelationCardinality.ONE,
                        persistenceShape = OwnedRelationPersistenceShape.ONE_TO_MANY_JOIN_COLUMN,
                        backingCollectionName = "files",
                        singleAccessorName = "file",
                    )
                )
            )
        )

        val entityItem = plan.single { it.templateId == "aggregate/entity.kt.peb" }
        @Suppress("UNCHECKED_CAST")
        val relationFields = entityItem.context["relationFields"] as List<Map<String, Any?>>
        @Suppress("UNCHECKED_CAST")
        val jpaImports = entityItem.context["jpaImports"] as List<String>
        val relation = relationFields.single()

        assertEquals("ONE_TO_MANY", relation["relationType"])
        assertEquals(true, relation["owned"])
        assertEquals("video_post_id", relation["parentRefColumn"])
        assertEquals("ONE", relation["ownedCardinality"])
        assertEquals("ONE_TO_MANY_JOIN_COLUMN", relation["persistenceShape"])
        assertEquals("files", relation["backingCollectionName"])
        assertEquals("file", relation["singleAccessorName"])
        assertTrue(jpaImports.contains("jakarta.persistence.OneToMany"))
        assertTrue(jpaImports.contains("jakarta.persistence.JoinColumn"))
        assertTrue(jpaImports.contains("jakarta.persistence.Transient"))
        assertFalse(jpaImports.contains("jakarta.persistence.OneToOne"))
    }
```

- [ ] **Step 2: Write the MANY context regression test**

Add this test near the previous one:

```kotlin
    @Test
    fun `entity planner keeps owned many relation public collection metadata`() {
        val entity = EntityModel(
            name = "VideoPost",
            packageName = "com.acme.demo.domain.aggregates.video_post",
            tableName = "video_post",
            comment = "video post",
            fields = listOf(FieldModel("id", "Long", columnName = "id")),
            idField = FieldModel("id", "Long", columnName = "id"),
        )
        val plan = AggregateArtifactPlanner().plan(
            aggregateConfig(),
            CanonicalModel(
                entities = listOf(entity),
                aggregateEntityJpa = listOf(defaultAggregateEntityJpa(entity)),
                aggregateRelations = listOf(
                    AggregateRelationModel(
                        ownerEntityName = "VideoPost",
                        ownerEntityPackageName = "com.acme.demo.domain.aggregates.video_post",
                        fieldName = "items",
                        targetEntityName = "VideoPostItem",
                        targetEntityPackageName = "com.acme.demo.domain.aggregates.video_post",
                        relationType = AggregateRelationType.ONE_TO_MANY,
                        joinColumn = "video_post_id",
                        fetchType = AggregateFetchType.LAZY,
                        nullable = false,
                        owned = true,
                        parentRefColumn = "video_post_id",
                        ownedCardinality = OwnedRelationCardinality.MANY,
                        persistenceShape = OwnedRelationPersistenceShape.ONE_TO_MANY_JOIN_COLUMN,
                        backingCollectionName = "items",
                    )
                )
            )
        )

        val entityItem = plan.single { it.templateId == "aggregate/entity.kt.peb" }
        @Suppress("UNCHECKED_CAST")
        val relationFields = entityItem.context["relationFields"] as List<Map<String, Any?>>
        @Suppress("UNCHECKED_CAST")
        val jpaImports = entityItem.context["jpaImports"] as List<String>
        val relation = relationFields.single()

        assertEquals(true, relation["owned"])
        assertEquals("MANY", relation["ownedCardinality"])
        assertEquals("items", relation["backingCollectionName"])
        assertEquals(null, relation["singleAccessorName"])
        assertFalse(jpaImports.contains("jakarta.persistence.Transient"))
    }
```

- [ ] **Step 3: Run generator context tests and verify they fail before projection**

Run:

```powershell
./gradlew.bat :cap4k-plugin-pipeline-generator-aggregate:test --tests "com.only4.cap4k.plugin.pipeline.generator.aggregate.AggregateArtifactPlannerTest.entity planner exposes owned one relation metadata while keeping one to many relation type" --tests "com.only4.cap4k.plugin.pipeline.generator.aggregate.AggregateArtifactPlannerTest.entity planner keeps owned many relation public collection metadata" --console=plain
```

Expected: FAIL because `AggregateRelationPlanning` has not projected the new relation metadata and `Transient` import yet.

- [ ] **Step 4: Project owned metadata in relation planning**

In `AggregateRelationPlanning.kt`, add this import:

```kotlin
import com.only4.cap4k.plugin.pipeline.api.OwnedRelationCardinality
```

In the `ownerRelationFields` map, add these keys:

```kotlin
                "owned" to relation.owned,
                "parentRefColumn" to relation.parentRefColumn,
                "ownedCardinality" to relation.ownedCardinality?.name,
                "persistenceShape" to relation.persistenceShape?.name,
                "backingCollectionName" to relation.backingCollectionName,
                "singleAccessorName" to relation.singleAccessorName,
```

In the `inverseRelationFields` map, add neutral defaults so templates can safely read the same keys:

```kotlin
                "owned" to false,
                "parentRefColumn" to null,
                "ownedCardinality" to null,
                "persistenceShape" to null,
                "backingCollectionName" to null,
                "singleAccessorName" to null,
```

Before building `jpaImports`, add:

```kotlin
        val hasOwnedOneRelations = entityRelations.any {
            it.ownedCardinality == OwnedRelationCardinality.ONE
        }
```

Then extend `jpaImports`:

```kotlin
            if (hasOwnedOneRelations) {
                add("jakarta.persistence.Transient")
            }
```

The final `jpaImports` block should still add `OneToMany` for `AggregateRelationType.ONE_TO_MANY`; it must not add `OneToOne` for owned one-cardinality relations.

- [ ] **Step 5: Run aggregate generator planner tests**

Run:

```powershell
./gradlew.bat :cap4k-plugin-pipeline-generator-aggregate:test --tests "com.only4.cap4k.plugin.pipeline.generator.aggregate.AggregateArtifactPlannerTest" --console=plain
```

Expected: PASS for `AggregateArtifactPlannerTest`.

- [ ] **Step 6: Commit Task 3**

```powershell
git add cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateRelationPlanning.kt cap4k-plugin-pipeline-generator-aggregate/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateArtifactPlannerTest.kt
git commit -m "feat: expose owned relation render metadata"
```

### Task 4: Pebble Entity Rendering For Owned ONE

**Files:**
- Modify: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/entity.kt.peb`
- Modify: `cap4k-plugin-pipeline-renderer-pebble/src/test/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PebbleArtifactRendererTest.kt`

**Interfaces:**
- Consumes relation context from Task 3:
  - `relation.relationType == "ONE_TO_MANY"`
  - `relation.owned == true`
  - `relation.ownedCardinality == "ONE"`
  - `relation.backingCollectionName`
  - `relation.singleAccessorName`
- Emits for owned ONE:
  - `private val <backingCollectionName>: MutableList<Target> = mutableListOf()`
  - `@get:Transient var <singleAccessorName>: Target?`
  - getter returning null for empty, one child for size 1, and `error(...)` for size > 1
  - setter replacing the backing collection
- Emits for owned MANY:
  - Current public `val <name>: MutableList<Target> = mutableListOf()` relation shape remains unchanged.

- [ ] **Step 1: Write renderer test for owned ONE generated entity API**

In `PebbleArtifactRendererTest`, add this test near existing aggregate entity relation rendering tests:

```kotlin
    @Test
    fun `aggregate entity template renders owned one as hidden collection plus transient single property`() {
        val content = renderTemplate(
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
                "hasApplicationSideIdFields" to false,
                "hasEmbeddedIdFields" to false,
                "hasStrongIdFields" to false,
                "hasEmbeddedStrongIdFields" to false,
                "hasVersionFields" to false,
                "softDelete" to mapOf("enabled" to false),
                "softDeleteSql" to null,
                "softDeleteWhereClause" to null,
                "softDeleteSqlKotlinStringLiteral" to null,
                "softDeleteWhereClauseKotlinStringLiteral" to null,
                "jpaImports" to listOf(
                    "jakarta.persistence.FetchType",
                    "jakarta.persistence.JoinColumn",
                    "jakarta.persistence.CascadeType",
                    "jakarta.persistence.OneToMany",
                    "jakarta.persistence.Transient",
                ),
                "imports" to listOf("com.acme.demo.domain.aggregates.video_post.VideoPostFile"),
                "idField" to mapOf("name" to "id", "type" to "Long"),
                "fields" to listOf(
                    mapOf(
                        "name" to "id",
                        "fieldName" to "id",
                        "fieldType" to "Long",
                        "renderedType" to "Long",
                        "nullable" to false,
                        "defaultValue" to null,
                        "columnName" to "id",
                        "isId" to true,
                        "embeddedId" to false,
                        "strongId" to false,
                        "isVersion" to false,
                        "converterClassRef" to null,
                    ),
                ),
                "scalarFields" to listOf(
                    mapOf(
                        "name" to "id",
                        "fieldName" to "id",
                        "fieldType" to "Long",
                        "renderedType" to "Long",
                        "nullable" to false,
                        "defaultValue" to null,
                        "columnName" to "id",
                        "isId" to true,
                        "embeddedId" to false,
                        "strongId" to false,
                        "isVersion" to false,
                        "converterClassRef" to null,
                    ),
                ),
                "relationFields" to listOf(
                    mapOf(
                        "name" to "files",
                        "targetType" to "VideoPostFile",
                        "targetTypeRef" to "VideoPostFile",
                        "targetPackageName" to "com.acme.demo.domain.aggregates.video_post",
                        "relationType" to "ONE_TO_MANY",
                        "fetchType" to "LAZY",
                        "joinColumn" to "video_post_id",
                        "nullable" to false,
                        "cascadeTypes" to listOf("PERSIST", "MERGE", "REMOVE"),
                        "orphanRemoval" to true,
                        "joinColumnNullable" to false,
                        "owned" to true,
                        "parentRefColumn" to "video_post_id",
                        "ownedCardinality" to "ONE",
                        "persistenceShape" to "ONE_TO_MANY_JOIN_COLUMN",
                        "backingCollectionName" to "files",
                        "singleAccessorName" to "file",
                    )
                ),
            ),
        )

        assertReadableKotlin(content)
        assertTrue(content.contains("import jakarta.persistence.Transient"))
        assertTrue(content.contains("@OneToMany(fetch = FetchType.LAZY, cascade = [CascadeType.PERSIST, CascadeType.MERGE, CascadeType.REMOVE], orphanRemoval = true)"))
        assertTrue(content.contains("@JoinColumn(name = \"video_post_id\", nullable = false)"))
        assertTrue(content.contains("private val files: MutableList<VideoPostFile> = mutableListOf()"))
        assertFalse(content.normalizedLineEndings().contains("\n    val files: MutableList<VideoPostFile> = mutableListOf()"))
        assertTrue(content.contains("@get:Transient"))
        assertTrue(content.contains("var file: VideoPostFile?"))
        assertTrue(content.contains("get() = when (files.size)"))
        assertTrue(content.contains("0 -> null"))
        assertTrue(content.contains("1 -> files[0]"))
        assertTrue(content.contains("else -> error(\"owned relation VideoPost.file expected at most one VideoPostFile but found \" + files.size)"))
        assertTrue(content.contains("set(value)"))
        assertTrue(content.contains("files.clear()"))
        assertTrue(content.contains("files.add(value)"))
    }
```

- [ ] **Step 2: Write renderer regression test for owned MANY keeping public collection**

Add this test near the previous one:

```kotlin
    @Test
    fun `aggregate entity template keeps owned many as public mutable list`() {
        val content = renderTemplate(
            templateId = "aggregate/entity.kt.peb",
            outputPath = "demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/VideoPost.kt",
            context = mapOf(
                "packageName" to "com.acme.demo.domain.aggregates.video_post",
                "typeName" to "VideoPost",
                "comment" to "video post",
                "aggregateName" to "VideoPost",
                "aggregateRoot" to true,
                "entityJpa" to mapOf("entityEnabled" to true, "tableName" to "video_post"),
                "hasConverterFields" to false,
                "hasGeneratedValueFields" to false,
                "hasApplicationSideIdFields" to false,
                "hasEmbeddedIdFields" to false,
                "hasStrongIdFields" to false,
                "hasEmbeddedStrongIdFields" to false,
                "hasVersionFields" to false,
                "softDelete" to mapOf("enabled" to false),
                "softDeleteSql" to null,
                "softDeleteWhereClause" to null,
                "softDeleteSqlKotlinStringLiteral" to null,
                "softDeleteWhereClauseKotlinStringLiteral" to null,
                "jpaImports" to listOf(
                    "jakarta.persistence.FetchType",
                    "jakarta.persistence.JoinColumn",
                    "jakarta.persistence.CascadeType",
                    "jakarta.persistence.OneToMany",
                ),
                "imports" to listOf("com.acme.demo.domain.aggregates.video_post.VideoPostItem"),
                "idField" to mapOf("name" to "id", "type" to "Long"),
                "fields" to emptyList<Map<String, Any?>>(),
                "scalarFields" to emptyList<Map<String, Any?>>(),
                "relationFields" to listOf(
                    mapOf(
                        "name" to "items",
                        "targetType" to "VideoPostItem",
                        "targetTypeRef" to "VideoPostItem",
                        "targetPackageName" to "com.acme.demo.domain.aggregates.video_post",
                        "relationType" to "ONE_TO_MANY",
                        "fetchType" to "LAZY",
                        "joinColumn" to "video_post_id",
                        "nullable" to false,
                        "cascadeTypes" to listOf("PERSIST", "MERGE", "REMOVE"),
                        "orphanRemoval" to true,
                        "joinColumnNullable" to false,
                        "owned" to true,
                        "parentRefColumn" to "video_post_id",
                        "ownedCardinality" to "MANY",
                        "persistenceShape" to "ONE_TO_MANY_JOIN_COLUMN",
                        "backingCollectionName" to "items",
                        "singleAccessorName" to null,
                    )
                ),
            ),
        )

        assertReadableKotlin(content)
        assertFalse(content.contains("import jakarta.persistence.Transient"))
        assertTrue(content.contains("val items: MutableList<VideoPostItem> = mutableListOf()"))
        assertFalse(content.contains("private val items: MutableList<VideoPostItem>"))
        assertFalse(content.contains("@get:Transient"))
        assertFalse(content.contains("var item: VideoPostItem?"))
    }
```

- [ ] **Step 3: Run renderer tests and verify owned ONE fails before template change**

Run:

```powershell
./gradlew.bat :cap4k-plugin-pipeline-renderer-pebble:test --tests "com.only4.cap4k.plugin.pipeline.renderer.pebble.PebbleArtifactRendererTest.aggregate entity template renders owned one as hidden collection plus transient single property" --tests "com.only4.cap4k.plugin.pipeline.renderer.pebble.PebbleArtifactRendererTest.aggregate entity template keeps owned many as public mutable list" --console=plain
```

Expected: owned ONE test FAILS because the template still renders the public `MutableList`; owned MANY may PASS.

- [ ] **Step 4: Update the ONE_TO_MANY branch in the entity template**

In `entity.kt.peb`, replace the current `ONE_TO_MANY` branch:

```pebble
{% elseif relation.relationType == "ONE_TO_MANY" %}    @OneToMany(fetch = FetchType.{{ relation.fetchType }}{% if relation.cascadeTypes|length > 0 %}, cascade = [{% for cascadeType in relation.cascadeTypes %}CascadeType.{{ cascadeType }}{% if not loop.last %}, {% endif %}{% endfor %}]{% endif %}, orphanRemoval = {{ relation.orphanRemoval }})
    @JoinColumn(name = "{{ relation.joinColumn }}", nullable = {{ relation.joinColumnNullable }})
    val {{ relation.name }}: MutableList<{{ relation.targetTypeRef }}> = mutableListOf()
{% endif %}
```

with this branch:

```pebble
{% elseif relation.relationType == "ONE_TO_MANY" %}    @OneToMany(fetch = FetchType.{{ relation.fetchType }}{% if relation.cascadeTypes|length > 0 %}, cascade = [{% for cascadeType in relation.cascadeTypes %}CascadeType.{{ cascadeType }}{% if not loop.last %}, {% endif %}{% endfor %}]{% endif %}, orphanRemoval = {{ relation.orphanRemoval }})
    @JoinColumn(name = "{{ relation.joinColumn }}", nullable = {{ relation.joinColumnNullable }})
{% if relation.ownedCardinality == "ONE" %}    private val {{ relation.backingCollectionName }}: MutableList<{{ relation.targetTypeRef }}> = mutableListOf()

    @get:Transient
    var {{ relation.singleAccessorName }}: {{ relation.targetTypeRef }}?
        get() = when ({{ relation.backingCollectionName }}.size) {
            0 -> null
            1 -> {{ relation.backingCollectionName }}[0]
            else -> error("owned relation {{ typeName }}.{{ relation.singleAccessorName }} expected at most one {{ relation.targetType }} but found " + {{ relation.backingCollectionName }}.size)
        }
        set(value) {
            {{ relation.backingCollectionName }}.clear()
            if (value != null) {
                {{ relation.backingCollectionName }}.add(value)
            }
        }
{% else %}    val {{ relation.name }}: MutableList<{{ relation.targetTypeRef }}> = mutableListOf()
{% endif %}{% endif %}
```

Do not change the `MANY_TO_ONE` or `ONE_TO_ONE` branches in this task.

- [ ] **Step 5: Run renderer tests**

Run:

```powershell
./gradlew.bat :cap4k-plugin-pipeline-renderer-pebble:test --tests "com.only4.cap4k.plugin.pipeline.renderer.pebble.PebbleArtifactRendererTest" --console=plain
```

Expected: PASS for `PebbleArtifactRendererTest`.

- [ ] **Step 6: Commit Task 4**

```powershell
git add cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/entity.kt.peb cap4k-plugin-pipeline-renderer-pebble/src/test/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PebbleArtifactRendererTest.kt
git commit -m "feat: render owned one entity accessor"
```

### Task 5: Functional Fixtures, Compile Smoke, And Final Verification

**Files:**
- Modify: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-relation-sample/schema.sql`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-relation-compile-sample/schema.sql`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginFunctionalTest.kt`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginCompileFunctionalTest.kt`

**Interfaces:**
- The existing `video_post_item` fixture remains `ownedCardinality=MANY` because it has an independent primary key and no parent-ref unique constraint.
- New `video_post_file` fixture is `ownedCardinality=ONE` because `video_post_id` is the explicit `@ParentRef` and has a unique constraint.
- Generated `VideoPost.items` remains a public `MutableList<VideoPostItem>`.
- Generated `VideoPost.file` becomes a public nullable `VideoPostFile?` property backed by private `files: MutableList<VideoPostFile>`.

- [ ] **Step 1: Add a one-child owned table to generation fixture schema**

In `aggregate-relation-sample/schema.sql`, after `create table video_post_item (...)`, add:

```sql
create table video_post_file (
    id bigint primary key,
    video_post_id bigint not null comment '@ParentRef;',
    storage_key varchar(128) not null,
    constraint uk_video_post_file_parent unique (video_post_id)
);
```

Then add the table comment after the existing `comment on table video_post_item` line:

```sql
comment on table video_post_file is '@Parent=video_post;';
```

- [ ] **Step 2: Add the same one-child owned table to compile fixture schema**

In `aggregate-relation-compile-sample/schema.sql`, after `create table video_post_item (...)`, add the same table:

```sql
create table video_post_file (
    id bigint primary key,
    video_post_id bigint not null comment '@ParentRef;',
    storage_key varchar(128) not null,
    constraint uk_video_post_file_parent unique (video_post_id)
);
```

Then add:

```sql
comment on table video_post_file is '@Parent=video_post;';
```

- [ ] **Step 3: Update aggregate relation generation functional assertions**

In `PipelinePluginFunctionalTest.cap4kGenerate aligns owned direct parent bindings with scalar fk and read only inverse relation`, add a file read for `VideoPostFile.kt` after the current `childEntityFile` declaration:

```kotlin
        val oneChildEntityFile = projectDir.resolve(
            generatedSource("demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/VideoPostFile.kt")
        )
```

Add content loading:

```kotlin
        val oneChildEntityContent = oneChildEntityFile.readText()
```

Add file existence assertion:

```kotlin
        assertTrue(oneChildEntityFile.toFile().exists())
```

Keep the existing MANY assertion:

```kotlin
        assertTrue(rootEntityContent.contains("val items: MutableList<VideoPostItem> = mutableListOf()"))
```

Then add ONE assertions:

```kotlin
        assertTrue(rootEntityContent.contains("import jakarta.persistence.Transient"))
        assertTrue(rootEntityContent.contains("private val files: MutableList<VideoPostFile> = mutableListOf()"))
        assertFalse(rootEntityContent.replace("\r\n", "\n").contains("\n    val files: MutableList<VideoPostFile> = mutableListOf()"))
        assertTrue(rootEntityContent.contains("@get:Transient"))
        assertTrue(rootEntityContent.contains("var file: VideoPostFile?"))
        assertTrue(rootEntityContent.contains("get() = when (files.size)"))
        assertTrue(rootEntityContent.contains("else -> error(\"owned relation VideoPost.file expected at most one VideoPostFile but found \" + files.size)"))
        assertTrue(rootEntityContent.contains("set(value)"))
        assertTrue(rootEntityContent.contains("files.clear()"))
        assertTrue(rootEntityContent.contains("files.add(value)"))
        assertTrue(oneChildEntityContent.contains("@Column(name = \"video_post_id\", insertable = false, updatable = false)"))
        assertTrue(oneChildEntityContent.contains("var videoPostId: Long = videoPostId"))
```

- [ ] **Step 4: Update aggregate relation compile assertions**

In `PipelinePluginCompileFunctionalTest.aggregate relation generation keeps owned direct parent bindings scalar plus read only inverse relation`, add generated file reads for `VideoPostFile.kt`:

```kotlin
        val generatedOneChildEntity = projectDir.resolve(
            generatedSource("demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/VideoPostFile.kt")
        ).readText()
```

Add generated file existence:

```kotlin
            generatedSource("demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/VideoPostFile.kt"),
```

Add assertions:

```kotlin
        assertTrue(generatedRootEntity.contains("private val files: MutableList<VideoPostFile> = mutableListOf()"))
        assertTrue(generatedRootEntity.contains("var file: VideoPostFile?"))
        assertTrue(generatedRootEntity.contains("@get:Transient"))
        assertFalse(generatedRootEntity.replace("\r\n", "\n").contains("\n    val files: MutableList<VideoPostFile> = mutableListOf()"))
        assertTrue(generatedOneChildEntity.contains("@Column(name = \"video_post_id\", insertable = false, updatable = false)"))
        assertTrue(generatedOneChildEntity.contains("var videoPostId: Long = videoPostId"))
```

In `aggregate behavior source compiles against generated entities when module build dir is customized`, extend the generated behavior source from:

```kotlin
            fun VideoPost.attachForCompile(item: VideoPostItem) {
                this.items.add(item)
            }
```

to:

```kotlin
            fun VideoPost.attachForCompile(item: VideoPostItem) {
                this.items.add(item)
            }

            fun VideoPost.replaceFileForCompile(file: VideoPostFile?) {
                this.file = file
            }
```

Also add generated file existence for the customized build dir:

```kotlin
            "demo-domain/out/build/generated/cap4k/main/kotlin/com/acme/demo/domain/aggregates/video_post/VideoPostFile.kt",
```

- [ ] **Step 5: Run focused functional tests**

Run:

```powershell
./gradlew.bat :cap4k-plugin-pipeline-gradle:test --tests "com.only4.cap4k.plugin.pipeline.gradle.PipelinePluginFunctionalTest.cap4kGenerate aligns owned direct parent bindings with scalar fk and read only inverse relation" --tests "com.only4.cap4k.plugin.pipeline.gradle.PipelinePluginCompileFunctionalTest.aggregate relation generation keeps owned direct parent bindings scalar plus read only inverse relation" --tests "com.only4.cap4k.plugin.pipeline.gradle.PipelinePluginCompileFunctionalTest.aggregate behavior source compiles against generated entities when module build dir is customized" --console=plain
```

Expected: PASS for the focused Gradle functional tests.

- [ ] **Step 6: Run focused module verification**

Run:

```powershell
./gradlew.bat :cap4k-plugin-pipeline-api:test --tests "com.only4.cap4k.plugin.pipeline.api.PipelineModelsTest" --console=plain
./gradlew.bat :cap4k-plugin-pipeline-core:test --tests "com.only4.cap4k.plugin.pipeline.core.OwnedRelationCardinalityInferenceTest" --tests "com.only4.cap4k.plugin.pipeline.core.DefaultCanonicalAssemblerTest" --console=plain
./gradlew.bat :cap4k-plugin-pipeline-generator-aggregate:test --tests "com.only4.cap4k.plugin.pipeline.generator.aggregate.AggregateArtifactPlannerTest" --console=plain
./gradlew.bat :cap4k-plugin-pipeline-renderer-pebble:test --tests "com.only4.cap4k.plugin.pipeline.renderer.pebble.PebbleArtifactRendererTest" --console=plain
./gradlew.bat :cap4k-plugin-pipeline-gradle:test --tests "com.only4.cap4k.plugin.pipeline.gradle.PipelinePluginFunctionalTest.cap4kGenerate aligns owned direct parent bindings with scalar fk and read only inverse relation" --tests "com.only4.cap4k.plugin.pipeline.gradle.PipelinePluginCompileFunctionalTest.aggregate relation generation keeps owned direct parent bindings scalar plus read only inverse relation" --tests "com.only4.cap4k.plugin.pipeline.gradle.PipelinePluginCompileFunctionalTest.aggregate behavior source compiles against generated entities when module build dir is customized" --console=plain
```

Expected: all commands PASS.

- [ ] **Step 7: Run repository-level static validation**

Run:

```powershell
python scripts/validate-cap4k-generator-inputs.py
git diff --check
rg -n "softDeleteColumn" cap4k-plugin-pipeline-api cap4k-plugin-pipeline-core cap4k-plugin-pipeline-generator-aggregate cap4k-plugin-pipeline-renderer-pebble cap4k-plugin-pipeline-gradle --glob "!**/build/**"
rg -n "ownedCardinality|OwnedRelationCardinality|ONE_TO_MANY_JOIN_COLUMN|@get:Transient" cap4k-plugin-pipeline-api cap4k-plugin-pipeline-core cap4k-plugin-pipeline-generator-aggregate cap4k-plugin-pipeline-renderer-pebble cap4k-plugin-pipeline-gradle --glob "!**/build/**"
```

Expected:
- `validate-cap4k-generator-inputs.py` prints `OK: no issues found.`
- `git diff --check` is clean.
- `softDeleteColumn` has no active source hits.
- The owned-cardinality scan shows only the intentional model, inference, planner, template, and test hits.

- [ ] **Step 8: Optional full regression**

If focused checks pass and time allows, run:

```powershell
./gradlew.bat --rerun-tasks test --console=plain
```

Record whether this full regression was run. Do not claim it passed if it was skipped.

- [ ] **Step 9: Commit Task 5**

```powershell
git add cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-relation-sample/schema.sql cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-relation-compile-sample/schema.sql cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginFunctionalTest.kt cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginCompileFunctionalTest.kt
git commit -m "test: cover owned one relation generation"
```

## Self-Review Checklist

**Spec Coverage**

- Acceptance criteria 1-3 are covered by Task 1 and Task 2: inference uses `@ParentRef`, PK metadata, unique constraints, and managed roles; physical FKs and `<parent_table>_id` fallback stay out of the algorithm.
- Acceptance criteria 4-11 are covered by `OwnedRelationCardinalityInferenceTest`: PK parent-ref, direct unique parent-ref, deleted/scope neutral columns, business columns, version/system fields, and nullable neutral columns.
- Acceptance criteria 12-13 are covered by Task 2 and Task 3: canonical `relationType` remains `ONE_TO_MANY`, and `persistenceShape`/`ownedCardinality` are separate context fields.
- Acceptance criteria 14-16 are covered by Task 4 and Task 5: generated entity code hides the backing collection for `ownedCardinality=ONE`, exposes a nullable property, fails fast in the getter, and replaces collection contents in the setter.
- Acceptance criterion 17 is preserved from PR #119 and reinforced by Task 3: generator contexts already expose `parentRef`, `managedRole`, `structuralParentRef`, and unresolved constructor fields; this plan does not move one-child accessors into checked-in `*Behavior.kt`.

**Known Boundaries**

- This plan does not add a physical `@OneToOne` owned mapping. The `ONE_TO_ONE` template branch remains unchanged.
- This plan does not redesign weak-reference/read-model generation.
- This plan does not change value-object persistence.
- This plan does not introduce a new DB annotation for cardinality.
- The implementation should retain existing disabled legacy tests as-is unless a touched assertion must be adjusted for compilation.

**Implementation Order**

- Task 1 is the smallest API/inference unit and should be reviewed before canonical wiring.
- Task 2 makes canonical relations carry the metadata while preserving current persistence relation type.
- Task 3 projects the model into template context without changing rendered output.
- Task 4 changes rendering and verifies the exact generated Kotlin shape.
- Task 5 proves the behavior through Gradle functional generation and compile smoke.

**Execution Handoff**

Plan complete and saved to `docs/superpowers/plans/2026-07-21-cap4k-owned-relation-cardinality-from-unique-constraints.md`.

Two execution options:

**1. Subagent-Driven (recommended)** - Dispatch a fresh subagent per task, review between tasks, fast iteration.

**2. Inline Execution** - Execute tasks in this session using `superpowers:executing-plans`, batch execution with checkpoints.

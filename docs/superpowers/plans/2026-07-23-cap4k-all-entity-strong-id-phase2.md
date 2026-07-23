# cap4k All-Entity Strong ID Phase 2 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement cap4k identity roadmap Phase 2 by generalizing generated Strong ID own IDs to every generated entity and replacing pre-flush update intent with existing-baseline enrollment plus dirty-only update listener classification.

**Architecture:** Treat generated own IDs, reference IDs, and parent references as separate canonical facts before rendering. Keep `ddd-core` limited to the public UoW intent vocabulary, keep generator identity decisions in the pipeline modules, and keep repository observation baselines, generated owned-relation traversal, Strong ID completion, and Hibernate dirty inspection inside `ddd-domain-repo-jpa`.

**Tech Stack:** Kotlin/JVM, Gradle, Spring Data JPA, Hibernate ORM 6.4.10.Final, Pebble templates, JUnit 5, MockK, H2 runtime fixtures.

## Global Constraints

- Source spec: `docs/superpowers/specs/2026-07-22-cap4k-all-entity-strong-id-design.md`.
- Phase 1 baseline: `PersistIntent.CREATE/UPDATE` exists, factory-created aggregates already register `CREATE`, and `persistIfNotExist(...)` has already been removed.
- Phase 2 intentionally replaces public pre-flush `PersistIntent.UPDATE` with `PersistIntent.EXISTING`; do not keep `UPDATE` as an alias.
- `PersistType.UPDATE` remains a post-flush listener/result classification only.
- Every generated primary-key column must explicitly declare identity strategy in DB/schema metadata.
- Phase 2 implementation mainline supports String-backed UUIDv7 Strong IDs only; UUID-backed and Long-backed Strong IDs remain future expansion points.
- Database-side IDs remain primitive/provider-generated and must not generate Strong ID wrappers.
- Parent references, `@RefAggregate`, and `@RefId` are not own IDs and must not gain create helpers.
- Composite primary keys remain unsupported.
- Generated owned relation traversal is limited to generated `@OneToMany + @JoinColumn + cascade + orphanRemoval=true` write-model relations.
- Core modules must not depend on JPA or Hibernate dirty-checking APIs.
- No mediator-facing manual ID allocation is added in this phase.
- Do not reintroduce removed DB annotations such as `@Reference`.
- Implement one task at a time, run that task's focused verification, and commit before starting the next task.
- Baseline note: `./gradlew.bat test --continue` was attempted in this worktree and timed out after 10 minutes without useful failure output; use focused module tests for each task and record any broader-test timeout separately.

---

## Durable Execution Protocol

- Worktree: `C:/Users/LD_moxeii/Documents/code/only-workspace/cap4k/.worktrees/cap4k-all-entity-strong-id-plan`.
- Branch: `plan/cap4k-all-entity-strong-id`.
- Before each task, run `git status --short --branch` and confirm the branch is still `plan/cap4k-all-entity-strong-id`.
- After each task, run the listed focused tests, inspect `git diff --check`, then commit only the files touched by that task.
- If a task grows beyond one reviewable change, stop after the current green checkpoint and split the remaining work into a new task section in this plan.
- If context is compacted or the session is interrupted, resume from the last completed checkbox and the latest commit on this branch.

## File Structure

- Modify: `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/application/PersistIntent.kt` - public UoW input vocabulary.
- Modify: `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/application/UnitOfWork.kt` - default intent documentation and signature default.
- Modify: `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/impl/DefaultMediator.kt` - forwarding remains unchanged except enum imports/tests.
- Modify: `ddd-core/src/test/kotlin/com/only4/cap4k/ddd/core/impl/DefaultMediatorTest.kt` - default and explicit intent coverage.
- Modify: `ddd-core/src/test/kotlin/com/only4/cap4k/ddd/core/domain/aggregate/impl/DefaultAggregateFactorySupervisorTest.kt` - factory `CREATE` coverage should remain.
- Modify: `ddd-domain-repo-jpa/src/main/kotlin/com/only4/cap4k/ddd/domain/repo/impl/DefaultRepositorySupervisor.kt` - repository observation and `EXISTING` enrollment.
- Modify: `ddd-domain-repo-jpa/src/test/kotlin/com/only4/cap4k/ddd/domain/repo/impl/DefaultRepositorySupervisorTest.kt` - repository observation/enrollment coverage.
- Modify: `ddd-domain-repo-jpa/src/main/kotlin/com/only4/cap4k/ddd/application/JpaUnitOfWork.kt` - pending entry merge, baseline promotion, ID completion timing, save flow, dirty-only listener classification.
- Create: `ddd-domain-repo-jpa/src/main/kotlin/com/only4/cap4k/ddd/application/JpaRepositoryObservationRecorder.kt` - small JPA-module contract used by repository supervisor and implemented by `JpaUnitOfWork`.
- Create: `ddd-domain-repo-jpa/src/main/kotlin/com/only4/cap4k/ddd/application/JpaRepositoryObservationBaseline.kt` - object-identity and persistent-identity baseline registry.
- Create: `ddd-domain-repo-jpa/src/main/kotlin/com/only4/cap4k/ddd/application/JpaGeneratedOwnedRelationTraversal.kt` - generated owned relation traversal helper.
- Create: `ddd-domain-repo-jpa/src/main/kotlin/com/only4/cap4k/ddd/application/JpaGeneratedStrongIdSupport.kt` - reflection support for generated Strong ID `@EmbeddedId` completion.
- Modify: `ddd-domain-repo-jpa/src/main/kotlin/com/only4/cap4k/ddd/application/JpaApplicationSideIdSupport.kt` - keep compatibility annotation support; narrow traversal handoff where needed.
- Modify: `ddd-domain-repo-jpa/src/test/kotlin/com/only4/cap4k/ddd/application/JpaUnitOfWorkTest.kt` - UoW baseline, conflict, completion, dirty listener coverage.
- Modify: `ddd-domain-repo-jpa/src/test/kotlin/com/only4/cap4k/ddd/application/JpaApplicationSideIdSupportTest.kt` - compatibility traversal coverage.
- Modify: `cap4k-ddd-starter/src/test/kotlin/com/only4/cap4k/ddd/runtime/strongid/StrongIdJpaRuntimeTest.kt` - Strong ID JPA root/child fixture coverage.
- Modify: `cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModels.kt` - `DbIdStrategy`, `StrongIdKind`, and `StrongIdModel` metadata.
- Modify: `cap4k-plugin-pipeline-api/src/test/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModelsTest.kt` - model default/strategy coverage.
- Modify: `cap4k-plugin-pipeline-source-db/src/main/kotlin/com/only4/cap4k/plugin/pipeline/source/db/DbColumnAnnotationParser.kt` - parse `@IdStrategy=uuid7` and updated diagnostics.
- Modify: `cap4k-plugin-pipeline-source-db/src/main/kotlin/com/only4/cap4k/plugin/pipeline/source/db/DbSchemaSourceProvider.kt` - validate ID strategy only on primary keys.
- Modify: `cap4k-plugin-pipeline-source-db/src/test/kotlin/com/only4/cap4k/plugin/pipeline/source/db/DbColumnAnnotationParserTest.kt` - parser tests.
- Modify: `cap4k-plugin-pipeline-source-db/src/test/kotlin/com/only4/cap4k/plugin/pipeline/source/db/DbSchemaSourceProviderTest.kt` - DB-source validation tests.
- Modify: `cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssembler.kt` - own-ID Strong ID inference and reference separation.
- Modify: `cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/AggregateSpecialFieldPolicyResolver.kt` - explicit ID strategy policy resolution.
- Modify: `cap4k-plugin-pipeline-core/src/test/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssemblerTest.kt` - canonical own/reference/parent-ref coverage.
- Modify: `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/StrongIdArtifactPlanner.kt` - `canGenerateNew` from own-ID metadata.
- Modify: `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/EntityArtifactPlanner.kt` - `@EmbeddedId` for any own-ID Strong ID.
- Modify: `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/FactoryArtifactPlanner.kt` - root own-ID lookup through generalized metadata.
- Modify: `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/RepositoryArtifactPlanner.kt` - repository ID type lookup through generalized metadata.
- Modify: `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateElementContext.kt` - Strong ID aggregate-element context no longer root-only.
- Modify: `cap4k-plugin-pipeline-generator-aggregate/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateArtifactPlannerTest.kt` - planner context coverage.
- Modify: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/entity.kt.peb` - confirm existing template supports generalized `embeddedId`.
- Modify: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/strong_id.kt.peb` - confirm `new()` remains controlled by `canGenerateNew`.
- Modify: `cap4k-plugin-pipeline-renderer-pebble/src/test/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PebbleArtifactRendererTest.kt` - template rendering tests.

### Task 1: Rename Public UoW Input Intent To EXISTING

**Files:**
- Modify: `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/application/PersistIntent.kt`
- Modify: `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/application/UnitOfWork.kt`
- Modify: `ddd-core/src/test/kotlin/com/only4/cap4k/ddd/core/impl/DefaultMediatorTest.kt`
- Modify: `ddd-domain-repo-jpa/src/main/kotlin/com/only4/cap4k/ddd/domain/repo/impl/DefaultRepositorySupervisor.kt`
- Modify: `ddd-domain-repo-jpa/src/test/kotlin/com/only4/cap4k/ddd/domain/repo/impl/DefaultRepositorySupervisorTest.kt`

**Interfaces:**
- Consumes: current `UnitOfWork.persist(entity: Any, intent: PersistIntent = PersistIntent.UPDATE)`.
- Produces: `UnitOfWork.persist(entity: Any, intent: PersistIntent = PersistIntent.EXISTING)` and `PersistIntent.CREATE/EXISTING`.

- [ ] **Step 1: Write failing core API tests**

In `ddd-core/src/test/kotlin/com/only4/cap4k/ddd/core/impl/DefaultMediatorTest.kt`, update the recording helper and add this default-intent assertion:

```kotlin
@Test
fun `default mediator persist forwards existing intent`() {
    val entity = Any()
    val unitOfWork = RecordingUnitOfWork()
    UnitOfWorkSupport.configure(unitOfWork)

    DefaultMediator().persist(entity)

    assertSame(entity, unitOfWork.persistedEntity)
    assertEquals(PersistIntent.EXISTING, unitOfWork.persistedIntent)
}
```

Keep the existing explicit-create forwarding test but update imports and expected enum values to `PersistIntent.CREATE`.

- [ ] **Step 2: Run core tests to verify failure**

Run:

```powershell
.\gradlew.bat :ddd-core:test --tests "com.only4.cap4k.ddd.core.impl.DefaultMediatorTest" --console=plain
```

Expected: FAIL because `PersistIntent.EXISTING` does not exist.

- [ ] **Step 3: Change the public enum and default**

Replace `PersistIntent.kt` with:

```kotlin
package com.only4.cap4k.ddd.core.application

enum class PersistIntent {
    CREATE,
    EXISTING,
}
```

Update `UnitOfWork.kt` signature and comment:

```kotlin
/**
 * 提交实体持久化意图。
 *
 * 默认意图为 EXISTING。工厂创建的新聚合应显式传入 PersistIntent.CREATE。
 *
 * @param entity 需要持久化的实体对象
 * @param intent 持久化意图
 * @throws IllegalArgumentException 当实体对象无效时
 */
fun persist(entity: Any, intent: PersistIntent = PersistIntent.EXISTING)
```

- [ ] **Step 4: Update repository supervisor enrollment names**

In `DefaultRepositorySupervisor.kt`, replace `registerUpdate` with:

```kotlin
private fun registerExisting(entity: Any) {
    unitOfWork.persist(entity, PersistIntent.EXISTING)
}
```

Replace every `::registerUpdate` call in that file with `::registerExisting`.

- [ ] **Step 5: Update focused tests**

In `DefaultRepositorySupervisorTest.kt`, replace expectations such as:

```kotlin
verify { mockUnitOfWork.persist(expectedEntity, PersistIntent.UPDATE) }
```

with:

```kotlin
verify { mockUnitOfWork.persist(expectedEntity, PersistIntent.EXISTING) }
```

Search must return no test or main references to `PersistIntent.UPDATE`.

- [ ] **Step 6: Run focused verification**

Run:

```powershell
rg -n "PersistIntent\.UPDATE|UnitOfWorkIntent\.UPDATE" ddd-core ddd-domain-repo-jpa
.\gradlew.bat :ddd-core:test --tests "com.only4.cap4k.ddd.core.impl.DefaultMediatorTest" --console=plain
.\gradlew.bat :ddd-domain-repo-jpa:test --tests "com.only4.cap4k.ddd.domain.repo.impl.DefaultRepositorySupervisorTest" --console=plain
```

Expected: `rg` finds only `UnitOfWorkIntent.UPDATE` until Task 6 removes the internal runtime name; both test commands PASS.

- [ ] **Step 7: Commit**

```powershell
git add ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/application/PersistIntent.kt `
        ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/application/UnitOfWork.kt `
        ddd-core/src/test/kotlin/com/only4/cap4k/ddd/core/impl/DefaultMediatorTest.kt `
        ddd-domain-repo-jpa/src/main/kotlin/com/only4/cap4k/ddd/domain/repo/impl/DefaultRepositorySupervisor.kt `
        ddd-domain-repo-jpa/src/test/kotlin/com/only4/cap4k/ddd/domain/repo/impl/DefaultRepositorySupervisorTest.kt
git commit -m "feat: rename uow existing intent"
```

### Task 2: Add Explicit DB Identity Strategy Input

**Files:**
- Modify: `cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModels.kt`
- Modify: `cap4k-plugin-pipeline-api/src/test/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModelsTest.kt`
- Modify: `cap4k-plugin-pipeline-source-db/src/main/kotlin/com/only4/cap4k/plugin/pipeline/source/db/DbColumnAnnotationParser.kt`
- Modify: `cap4k-plugin-pipeline-source-db/src/main/kotlin/com/only4/cap4k/plugin/pipeline/source/db/DbSchemaSourceProvider.kt`
- Modify: `cap4k-plugin-pipeline-source-db/src/test/kotlin/com/only4/cap4k/plugin/pipeline/source/db/DbColumnAnnotationParserTest.kt`
- Modify: `cap4k-plugin-pipeline-source-db/src/test/kotlin/com/only4/cap4k/plugin/pipeline/source/db/DbSchemaSourceProviderTest.kt`

**Interfaces:**
- Consumes: `DbColumnSnapshot.idStrategy: DbIdStrategy?`.
- Produces: `DbIdStrategy.DB_IDENTITY` and `DbIdStrategy.UUID7`, where `UUID7` is the only phase-2 application-side DB/schema strategy.

- [ ] **Step 1: Write failing API and parser tests**

In `PipelineModelsTest.kt`, add:

```kotlin
@Test
fun `db id strategy carries uuid7 application side strategy`() {
    val column = DbColumnSnapshot(
        name = "id",
        dbType = "varchar(36)",
        kotlinType = "String",
        nullable = false,
        isPrimaryKey = true,
        idStrategy = DbIdStrategy.UUID7,
    )

    assertEquals(DbIdStrategy.UUID7, column.idStrategy)
}
```

In `DbColumnAnnotationParserTest.kt`, add:

```kotlin
@Test
fun `column parser accepts uuid7 id strategy`() {
    val metadata = DbColumnAnnotationParser.parse("@IdStrategy=uuid7;")

    assertEquals(DbIdStrategy.UUID7, metadata.idStrategy)
}
```

Add a rejection test that keeps unsupported strategy diagnostics clear:

```kotlin
@Test
fun `column parser rejects unsupported application side id strategy`() {
    val error = assertThrows<IllegalArgumentException> {
        DbColumnAnnotationParser.parse("@IdStrategy=snowflake-long;")
    }

    assertEquals("unsupported @IdStrategy value: snowflake-long", error.message)
}
```

- [ ] **Step 2: Run parser tests to verify failure**

Run:

```powershell
.\gradlew.bat :cap4k-plugin-pipeline-api:test --tests "com.only4.cap4k.plugin.pipeline.api.PipelineModelsTest" --console=plain
.\gradlew.bat :cap4k-plugin-pipeline-source-db:test --tests "com.only4.cap4k.plugin.pipeline.source.db.DbColumnAnnotationParserTest" --console=plain
```

Expected: FAIL because `DbIdStrategy.UUID7` is missing and the parser allow-list still mentions only `db_identity`.

- [ ] **Step 3: Add enum value and parser mapping**

Update `DbIdStrategy` in `PipelineModels.kt`:

```kotlin
enum class DbIdStrategy {
    DB_IDENTITY,
    UUID7,
}
```

Update the parser's supported annotation text to include both values:

```kotlin
"@IdStrategy=db_identity|uuid7"
```

Update `resolveIdStrategy(...)` mapping:

```kotlin
private fun resolveIdStrategy(annotations: Map<String, String?>): DbIdStrategy? {
    val rawValue = annotations["IdStrategy"] ?: return null
    return when (rawValue.trim().lowercase()) {
        "db_identity" -> DbIdStrategy.DB_IDENTITY
        "uuid7" -> DbIdStrategy.UUID7
        else -> throw IllegalArgumentException("unsupported @IdStrategy value: $rawValue")
    }
}
```

- [ ] **Step 4: Keep primary-key-only validation**

In `DbSchemaSourceProvider.kt`, keep the validation broad across any `idStrategy`:

```kotlin
require(table.columns.filter { it.idStrategy != null }.all { it.isPrimaryKey }) {
    "@IdStrategy is valid only on a primary-key column"
}
```

Update existing tests that assert the old message `@IdStrategy=db_identity is valid only on a primary-key column` to the new exact message.

- [ ] **Step 5: Run focused verification**

Run:

```powershell
.\gradlew.bat :cap4k-plugin-pipeline-api:test --tests "com.only4.cap4k.plugin.pipeline.api.PipelineModelsTest" --console=plain
.\gradlew.bat :cap4k-plugin-pipeline-source-db:test --tests "com.only4.cap4k.plugin.pipeline.source.db.DbColumnAnnotationParserTest" --tests "com.only4.cap4k.plugin.pipeline.source.db.DbSchemaSourceProviderTest" --console=plain
```

Expected: PASS with parser diagnostics updated to mention `@IdStrategy=db_identity|uuid7`.

- [ ] **Step 6: Commit**

```powershell
git add cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModels.kt `
        cap4k-plugin-pipeline-api/src/test/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModelsTest.kt `
        cap4k-plugin-pipeline-source-db/src/main/kotlin/com/only4/cap4k/plugin/pipeline/source/db/DbColumnAnnotationParser.kt `
        cap4k-plugin-pipeline-source-db/src/main/kotlin/com/only4/cap4k/plugin/pipeline/source/db/DbSchemaSourceProvider.kt `
        cap4k-plugin-pipeline-source-db/src/test/kotlin/com/only4/cap4k/plugin/pipeline/source/db/DbColumnAnnotationParserTest.kt `
        cap4k-plugin-pipeline-source-db/src/test/kotlin/com/only4/cap4k/plugin/pipeline/source/db/DbSchemaSourceProviderTest.kt
git commit -m "feat: add uuid7 db id strategy input"
```

### Task 3: Generalize Strong ID Canonical Metadata To Own IDs

**Files:**
- Modify: `cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModels.kt`
- Modify: `cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssembler.kt`
- Modify: `cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/AggregateSpecialFieldPolicyResolver.kt`
- Modify: `cap4k-plugin-pipeline-core/src/test/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssemblerTest.kt`

**Interfaces:**
- Consumes: `DbIdStrategy.UUID7` from Task 2.
- Produces: `StrongIdKind.OWN_ID`, `StrongIdModel.ownerEntityName`, `StrongIdModel.ownerEntityPackageName`, `StrongIdModel.idStrategy`, `StrongIdModel.canGenerateNew`, and `StrongIdModel.isEmbeddedId`.

- [ ] **Step 1: Write failing canonical tests for root and child own IDs**

In `DefaultCanonicalAssemblerTest.kt`, add a test with one root table and one owned child table:

```kotlin
@Test
fun `uuid7 root and owned child primary keys become own strong ids`() {
    val result = DefaultCanonicalAssembler().assemble(
        projectConfig(),
        listOf(
            DbSchemaSnapshot(
                tables = listOf(
                    table(
                        "order",
                        aggregateRoot = true,
                        columns = listOf(
                            column("id", "VARCHAR(36)", "String", false, primaryKey = true, idStrategy = DbIdStrategy.UUID7),
                            column("title", "VARCHAR(64)", "String", false),
                        ),
                        primaryKey = listOf("id"),
                    ),
                    table(
                        "order_line",
                        parentTable = "order",
                        columns = listOf(
                            column("id", "VARCHAR(36)", "String", false, primaryKey = true, idStrategy = DbIdStrategy.UUID7),
                            column("order_id", "VARCHAR(36)", "String", false, parentRef = true),
                            column("sku", "VARCHAR(64)", "String", false),
                        ),
                        primaryKey = listOf("id"),
                    ),
                )
            )
        )
    )

    val orderId = result.model.strongIds.single { it.typeName == "OrderId" }
    assertEquals(StrongIdKind.OWN_ID, orderId.kind)
    assertEquals("Order", orderId.ownerEntityName)
    assertEquals("uuid7", orderId.idStrategy)
    assertEquals(true, orderId.canGenerateNew)
    assertEquals(true, orderId.isEmbeddedId)

    val lineId = result.model.strongIds.single { it.typeName == "OrderLineId" }
    assertEquals(StrongIdKind.OWN_ID, lineId.kind)
    assertEquals("OrderLine", lineId.ownerEntityName)
    assertEquals("Order", lineId.ownerAggregateName)
    assertEquals("uuid7", lineId.idStrategy)
    assertEquals(true, lineId.canGenerateNew)
    assertEquals(true, lineId.isEmbeddedId)

    val line = result.model.entities.single { it.name == "OrderLine" }
    assertEquals("OrderLineId", line.idField.type)
    assertEquals("String", line.fields.single { it.name == "orderId" }.type)
}
```

Add a database-identity guard:

```kotlin
@Test
fun `db identity primary key stays primitive and does not emit own strong id`() {
    val result = DefaultCanonicalAssembler().assemble(
        projectConfig(),
        listOf(
            DbSchemaSnapshot(
                tables = listOf(
                    table(
                        "invoice",
                        aggregateRoot = true,
                        columns = listOf(
                            column("id", "BIGINT", "Long", false, primaryKey = true, idStrategy = DbIdStrategy.DB_IDENTITY),
                            column("title", "VARCHAR(64)", "String", false),
                        ),
                        primaryKey = listOf("id"),
                    )
                )
            )
        )
    )

    assertEquals("Long", result.model.entities.single().idField.type)
    assertTrue(result.model.strongIds.none { it.typeName == "InvoiceId" })
}
```

- [ ] **Step 2: Run canonical test to verify failure**

Run:

```powershell
.\gradlew.bat :cap4k-plugin-pipeline-core:test --tests "com.only4.cap4k.plugin.pipeline.core.DefaultCanonicalAssemblerTest.uuid7 root and owned child primary keys become own strong ids" --tests "com.only4.cap4k.plugin.pipeline.core.DefaultCanonicalAssemblerTest.db identity primary key stays primitive and does not emit own strong id" --console=plain
```

Expected: FAIL because `StrongIdKind.OWN_ID` and the new `StrongIdModel` fields do not exist.

- [ ] **Step 3: Extend the Strong ID model**

Update `PipelineModels.kt`:

```kotlin
enum class StrongIdKind {
    OWN_ID,
    AGGREGATE_REFERENCE,
    REFERENCE,
}

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
    val canGenerateNew: Boolean = false,
    val isEmbeddedId: Boolean = false,
)
```

Update tests and hand-built `StrongIdModel(...)` call sites by replacing root own IDs with `StrongIdKind.OWN_ID`, adding the owner entity fields, and setting `canGenerateNew = true` for own IDs only.

- [ ] **Step 4: Resolve own-ID field types from explicit strategy**

In `DefaultCanonicalAssembler.kt`, replace `generatedAggregateRootStrongIdType(table)` with:

```kotlin
private fun generatedOwnStrongIdType(table: DbTableSnapshot): String? {
    val primaryKeyColumn = table.primaryKey.singleOrNull() ?: return null
    val idColumn = table.columns.firstOrNull { it.name.equals(primaryKeyColumn, ignoreCase = true) }
        ?: return null
    if (idColumn.idStrategy != DbIdStrategy.UUID7) return null
    require(idColumn.kotlinType == "String") {
        "@IdStrategy=uuid7 currently requires String physical ID column on table ${table.tableName}.${idColumn.name}"
    }
    require(idColumn.refAggregate.isNullOrBlank() && idColumn.refId.isNullOrBlank()) {
        "primary key ${table.tableName}.${idColumn.name} cannot also be @RefAggregate or @RefId"
    }
    return aggregateRootStrongIdTypeName(AggregateNaming.entityName(table.tableName))
}
```

Keep the helper name `aggregateRootStrongIdTypeName(...)` only if it remains a pure naming helper; otherwise rename it to:

```kotlin
private fun ownStrongIdTypeName(entityName: String): String = "${entityName}Id"
```

- [ ] **Step 5: Build own Strong IDs for every generated entity**

Update the canonical assembly flow so the `fields` type resolution uses `generatedOwnStrongIdType(table)` for any table primary key, not only aggregate roots. Then update `buildStrongIds(...)` to emit own-ID metadata:

```kotlin
private fun buildStrongIds(
    config: ProjectConfig,
    entities: List<EntityModel>,
    tables: List<DbTableSnapshot>,
): List<StrongIdModel> {
    val tableByEntity = tables.associateBy { AggregateNaming.entityName(it.tableName) }
    val ownIds = entities.mapNotNull { entity ->
        val table = tableByEntity[entity.name] ?: return@mapNotNull null
        val idColumn = table.columns.firstOrNull { column ->
            table.primaryKey.any { it.equals(column.name, ignoreCase = true) }
        } ?: return@mapNotNull null
        if (idColumn.idStrategy != DbIdStrategy.UUID7) return@mapNotNull null
        StrongIdModel(
            typeName = entity.idField.type,
            packageName = entity.packageName,
            valueType = "String",
            kind = StrongIdKind.OWN_ID,
            ownerEntityName = entity.name,
            ownerEntityPackageName = entity.packageName,
            ownerAggregateName = aggregateRootNameOrSelf(entity, entities),
            ownerAggregatePackageName = aggregateRootPackageNameOrSelf(entity, entities),
            idStrategy = "uuid7",
            canGenerateNew = true,
            isEmbeddedId = true,
        )
    }
    val referenceStrongIds = buildReferenceStrongIds(config, tables)
    return (ownIds + referenceStrongIds).distinctBy { it.packageName to it.typeName }
}
```

Add private helpers that follow the existing `parentEntityName` chain and return the root owner for children:

```kotlin
private fun aggregateRootNameOrSelf(entity: EntityModel, entities: List<EntityModel>): String =
    aggregateRootNameOrNull(entity, entities) ?: entity.name

private fun aggregateRootPackageNameOrSelf(entity: EntityModel, entities: List<EntityModel>): String =
    entities.firstOrNull { it.name == aggregateRootNameOrSelf(entity, entities) && it.aggregateRoot }?.packageName
        ?: entity.packageName
```

If these helpers duplicate an existing helper in `cap4k-plugin-pipeline-generator-aggregate`, keep a local core helper instead of making core depend on a generator module.

- [ ] **Step 6: Update policy resolution to require explicit PK strategy**

In `AggregateSpecialFieldPolicyResolver.resolvePolicy(...)`, replace implicit UUID7/default root behavior with explicit source rules:

```kotlin
val idStrategy = when (idColumn.idStrategy) {
    DbIdStrategy.UUID7 -> "uuid7"
    DbIdStrategy.DB_IDENTITY -> "identity"
    null -> throw IllegalArgumentException("primary key ${table.tableName}.${idColumn.name} must declare @IdStrategy=uuid7 or @IdStrategy=db_identity")
}
val idSource = SpecialFieldSource.DB_EXPLICIT
```

Remove `isGeneratedAggregateRootStrongId(...)` as a policy source. Keep config defaults only where older non-DB design sources still call `AggregateIdPolicyResolver` directly; DB/schema canonical assembly must use explicit PK strategy.

- [ ] **Step 7: Run focused verification**

Run:

```powershell
.\gradlew.bat :cap4k-plugin-pipeline-core:test --tests "com.only4.cap4k.plugin.pipeline.core.DefaultCanonicalAssemblerTest" --console=plain
rg -n "AGGREGATE_ROOT|isGeneratedAggregateRootStrongId|generatedAggregateRootStrongIdType" cap4k-plugin-pipeline-api/src/main/kotlin cap4k-plugin-pipeline-core/src/main/kotlin cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin
```

Expected: core tests PASS; `rg` shows no production dependency on root-only Strong ID semantics.

- [ ] **Step 8: Commit**

```powershell
git add cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModels.kt `
        cap4k-plugin-pipeline-api/src/test/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModelsTest.kt `
        cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssembler.kt `
        cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/AggregateSpecialFieldPolicyResolver.kt `
        cap4k-plugin-pipeline-core/src/test/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssemblerTest.kt
git commit -m "feat: model strong ids for all own ids"
```

### Task 4: Generalize Aggregate Strong ID Planning And Rendering

**Files:**
- Modify: `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/StrongIdArtifactPlanner.kt`
- Modify: `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/EntityArtifactPlanner.kt`
- Modify: `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/FactoryArtifactPlanner.kt`
- Modify: `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/RepositoryArtifactPlanner.kt`
- Modify: `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateElementContext.kt`
- Modify: `cap4k-plugin-pipeline-generator-aggregate/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateArtifactPlannerTest.kt`
- Modify: `cap4k-plugin-pipeline-renderer-pebble/src/test/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PebbleArtifactRendererTest.kt`

**Interfaces:**
- Consumes: Task 3 `StrongIdModel.kind == StrongIdKind.OWN_ID` and `StrongIdModel.canGenerateNew`.
- Produces: generated artifacts and template contexts that render root and child own IDs as `@EmbeddedId`, while reference IDs remain `@Embedded`/non-generating.

- [ ] **Step 1: Write failing planner test for child own Strong ID artifact**

In `AggregateArtifactPlannerTest.kt`, add a focused model with root and child own IDs:

```kotlin
@Test
fun `aggregate planner emits own strong id artifacts for roots and owned children`() {
    val model = CanonicalModel(
        project = ProjectModel(name = "demo"),
        aggregates = listOf(AggregateModel(name = "Order", packageName = "com.demo.domain.order")),
        entities = listOf(
            EntityModel(
                name = "Order",
                packageName = "com.demo.domain.order",
                tableName = "orders",
                comment = "",
                aggregateRoot = true,
                fields = listOf(FieldModel("id", "OrderId", nullable = false, columnName = "id")),
                idField = FieldModel("id", "OrderId", nullable = false, columnName = "id"),
            ),
            EntityModel(
                name = "OrderLine",
                packageName = "com.demo.domain.order",
                tableName = "order_line",
                comment = "",
                aggregateRoot = false,
                parentEntityName = "Order",
                fields = listOf(
                    FieldModel("id", "OrderLineId", nullable = false, columnName = "id"),
                    FieldModel("orderId", "String", nullable = false, columnName = "order_id", parentRef = true),
                ),
                idField = FieldModel("id", "OrderLineId", nullable = false, columnName = "id"),
            ),
        ),
        strongIds = listOf(
            StrongIdModel(
                typeName = "OrderId",
                packageName = "com.demo.domain.order",
                kind = StrongIdKind.OWN_ID,
                ownerEntityName = "Order",
                ownerEntityPackageName = "com.demo.domain.order",
                ownerAggregateName = "Order",
                ownerAggregatePackageName = "com.demo.domain.order",
                idStrategy = "uuid7",
                canGenerateNew = true,
                isEmbeddedId = true,
            ),
            StrongIdModel(
                typeName = "OrderLineId",
                packageName = "com.demo.domain.order",
                kind = StrongIdKind.OWN_ID,
                ownerEntityName = "OrderLine",
                ownerEntityPackageName = "com.demo.domain.order",
                ownerAggregateName = "Order",
                ownerAggregatePackageName = "com.demo.domain.order",
                idStrategy = "uuid7",
                canGenerateNew = true,
                isEmbeddedId = true,
            ),
        ),
    )

    val artifacts = AggregateArtifactPlanner().plan(ProjectConfig(basePackage = "com.demo"), model)

    val childId = artifacts.single { it.typeName == "OrderLineId" }
    assertEquals("aggregate/strong_id.kt.peb", childId.templateId)
    assertEquals(true, childId.context["canGenerateNew"])
    assertEquals("OWN_ID", childId.context["kind"])
}
```

- [ ] **Step 2: Write failing entity planner assertion for child `@EmbeddedId`**

Extend an existing entity planner test or add:

```kotlin
@Test
fun `entity planner marks owned child own strong id as embedded id`() {
    val entityArtifact = planSingleEntityArtifactForOrderLineWithUuid7OwnId()
    val idField = (entityArtifact.context["scalarFields"] as List<Map<String, Any?>>)
        .single { it["name"] == "id" }

    assertEquals(true, idField["strongId"])
    assertEquals(true, idField["embeddedId"])
    assertEquals("OrderLineId", idField["type"])
}
```

Implement `planSingleEntityArtifactForOrderLineWithUuid7OwnId()` as a private test helper in the same test class using the same `CanonicalModel` shape from Step 1.

- [ ] **Step 3: Run generator tests to verify failure**

Run:

```powershell
.\gradlew.bat :cap4k-plugin-pipeline-generator-aggregate:test --tests "com.only4.cap4k.plugin.pipeline.generator.aggregate.AggregateArtifactPlannerTest" --console=plain
```

Expected: FAIL on `AGGREGATE_ROOT` references and root-only embedded ID checks.

- [ ] **Step 4: Update Strong ID artifact planner**

In `StrongIdArtifactPlanner.kt`, remove the `StrongIdKind` import if it is only used for root checks, and change context production to:

```kotlin
"kind" to strongId.kind.name,
"canGenerateNew" to strongId.canGenerateNew,
```

No template change is required for `strong_id.kt.peb` unless tests expose an import or whitespace regression; `new()` already depends on `canGenerateNew`.

- [ ] **Step 5: Update aggregate-element context**

In `AggregateElementContext.kt`, update `strongIdAggregateElementContext(...)`:

```kotlin
internal fun strongIdAggregateElementContext(strongId: StrongIdModel): Map<String, Any?> =
    aggregateElementContext(
        aggregate = when (strongId.kind) {
            StrongIdKind.OWN_ID,
            StrongIdKind.AGGREGATE_REFERENCE,
            -> strongId.ownerAggregateName.orEmpty()
            StrongIdKind.REFERENCE -> ""
        },
        name = strongId.typeName,
        packageName = strongId.packageName,
        description = "",
        type = "strong-id",
        root = strongId.kind == StrongIdKind.OWN_ID &&
            strongId.ownerEntityName == strongId.ownerAggregateName &&
            strongId.ownerEntityPackageName == strongId.ownerAggregatePackageName,
    )
```

- [ ] **Step 6: Update entity strong ID resolution**

In `EntityArtifactPlanner.kt`, replace root-only resolution with owner-field resolution:

```kotlin
private fun resolveStrongId(
    model: CanonicalModel,
    entity: EntityModel,
    field: FieldModel,
): StrongIdModel? {
    val ownId = model.strongIds.firstOrNull {
        it.kind == StrongIdKind.OWN_ID &&
            it.ownerEntityName == entity.name &&
            it.ownerEntityPackageName == entity.packageName &&
            it.typeName == field.type.shortTypeName() &&
            field.name == entity.idField.name
    }
    if (ownId != null) return ownId

    val matches = model.strongIds.filter { strongId ->
        strongId.kind != StrongIdKind.OWN_ID &&
            (field.type == strongId.typeName || field.type == strongId.fqn())
    }
    require(matches.size <= 1) {
        "ambiguous strong id type ${field.type} for ${entity.packageName}.${entity.name}.${field.name}"
    }
    return matches.singleOrNull()
}

private fun isOwnIdField(
    entity: EntityModel,
    field: FieldModel,
    strongId: StrongIdModel,
): Boolean =
    field.name == entity.idField.name &&
        strongId.kind == StrongIdKind.OWN_ID &&
        strongId.ownerEntityName == entity.name &&
        strongId.ownerEntityPackageName == entity.packageName
```

Update the `embeddedId` calculation to:

```kotlin
val embeddedId = strongId != null && isOwnIdField(entity, field, strongId)
```

- [ ] **Step 7: Update factory and repository root ID lookup**

In `FactoryArtifactPlanner.kt` and `RepositoryArtifactPlanner.kt`, replace `resolveAggregateRootStrongId(...)` with:

```kotlin
private fun resolveOwnStrongId(
    model: CanonicalModel,
    entity: EntityModel,
): StrongIdModel? =
    model.strongIds.singleOrNull {
        it.kind == StrongIdKind.OWN_ID &&
            it.ownerEntityName == entity.name &&
            it.ownerEntityPackageName == entity.packageName &&
            it.typeName == entity.idField.type.shortTypeName()
    }
```

Keep these planners using root entities for factory/repository artifacts; this task generalizes type lookup, not repository generation for child entities.

- [ ] **Step 8: Update renderer tests**

In `PebbleArtifactRendererTest.kt`, add or update a context test that renders an owned child entity with:

```kotlin
"hasEmbeddedIdFields" to true,
"hasStrongIdFields" to true,
"scalarFields" to listOf(
    mapOf(
        "name" to "id",
        "type" to "OrderLineId",
        "nullable" to false,
        "strongId" to true,
        "embeddedId" to true,
        "columnName" to "id",
        "attributeOverrideNullable" to false,
        "attributeOverrideUpdatable" to false,
        "attributeOverrideInsertable" to null,
    )
)
```

Assert the content contains:

```kotlin
assertTrue(content.contains("@EmbeddedId"))
assertTrue(content.contains("var id: OrderLineId = id"))
assertFalse(content.contains("ApplicationSideId"))
```

- [ ] **Step 9: Run focused verification**

Run:

```powershell
.\gradlew.bat :cap4k-plugin-pipeline-generator-aggregate:test --tests "com.only4.cap4k.plugin.pipeline.generator.aggregate.AggregateArtifactPlannerTest" --console=plain
.\gradlew.bat :cap4k-plugin-pipeline-renderer-pebble:test --tests "com.only4.cap4k.plugin.pipeline.renderer.pebble.PebbleArtifactRendererTest.aggregate strong id template renders embeddable validated wrapper" --tests "com.only4.cap4k.plugin.pipeline.renderer.pebble.PebbleArtifactRendererTest.reference strong id template renders parse without new factory" --tests "com.only4.cap4k.plugin.pipeline.renderer.pebble.PebbleArtifactRendererTest.aggregate entity template renders aggregate root strong id as embedded id" --console=plain
rg -n "StrongIdKind\.AGGREGATE_ROOT|canGenerateNew.*AGGREGATE_ROOT|isAggregateRootIdField" cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin cap4k-plugin-pipeline-renderer-pebble/src/main
```

Expected: tests PASS; `rg` finds no production root-only Strong ID gates.

- [ ] **Step 10: Commit**

```powershell
git add cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/StrongIdArtifactPlanner.kt `
        cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/EntityArtifactPlanner.kt `
        cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/FactoryArtifactPlanner.kt `
        cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/RepositoryArtifactPlanner.kt `
        cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateElementContext.kt `
        cap4k-plugin-pipeline-generator-aggregate/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateArtifactPlannerTest.kt `
        cap4k-plugin-pipeline-renderer-pebble/src/test/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PebbleArtifactRendererTest.kt
git commit -m "feat: render strong ids for entity own ids"
```

### Task 5: Record Repository Observation Baselines For Every Load

**Files:**
- Create: `ddd-domain-repo-jpa/src/main/kotlin/com/only4/cap4k/ddd/application/JpaRepositoryObservationRecorder.kt`
- Create: `ddd-domain-repo-jpa/src/main/kotlin/com/only4/cap4k/ddd/application/JpaRepositoryObservationBaseline.kt`
- Create: `ddd-domain-repo-jpa/src/main/kotlin/com/only4/cap4k/ddd/application/JpaGeneratedOwnedRelationTraversal.kt`
- Modify: `ddd-domain-repo-jpa/src/main/kotlin/com/only4/cap4k/ddd/application/JpaUnitOfWork.kt`
- Modify: `ddd-domain-repo-jpa/src/main/kotlin/com/only4/cap4k/ddd/domain/repo/impl/DefaultRepositorySupervisor.kt`
- Modify: `ddd-domain-repo-jpa/src/test/kotlin/com/only4/cap4k/ddd/domain/repo/impl/DefaultRepositorySupervisorTest.kt`
- Modify: `ddd-domain-repo-jpa/src/test/kotlin/com/only4/cap4k/ddd/application/JpaUnitOfWorkTest.kt`

**Interfaces:**
- Consumes: `DefaultRepositorySupervisor` receives a `UnitOfWork` that is a concrete `JpaUnitOfWork` in starter wiring.
- Produces: `JpaRepositoryObservationRecorder.observeRepositoryLoad(root: Any, loadPlan: AggregateLoadPlan)` and baseline registry methods used by later UoW save flow.

- [ ] **Step 1: Write failing repository supervisor tests**

In `DefaultRepositorySupervisorTest.kt`, define a recorder mock:

```kotlin
private lateinit var observationRecorder: JpaRepositoryObservationRecorder
```

Initialize it in `setUp()`:

```kotlin
observationRecorder = mockk(relaxed = true)
supervisor = DefaultRepositorySupervisor(
    repositories = listOf(testRepository, anotherRepository, entityRepository),
    unitOfWork = mockUnitOfWork,
    observationRecorder = observationRecorder,
)
```

Add coverage for `persist=false`:

```kotlin
@Test
fun `find observes repository baseline even when persist false`() {
    val expectedEntity = TestEntity("id-1")
    every { testRepository.find(any(), any<Collection<OrderInfo>>(), false, AggregateLoadPlan.WHOLE_AGGREGATE) } returns listOf(expectedEntity)

    val result = supervisor.find(TestPredicate(), emptyList(), persist = false)

    assertEquals(listOf(expectedEntity), result)
    verify { observationRecorder.observeRepositoryLoad(expectedEntity, AggregateLoadPlan.WHOLE_AGGREGATE) }
    verify(exactly = 0) { mockUnitOfWork.persist(any(), any()) }
}
```

Update existing `persist=true` tests so they assert both observation and `EXISTING` enrollment:

```kotlin
verify { observationRecorder.observeRepositoryLoad(expectedEntity, AggregateLoadPlan.WHOLE_AGGREGATE) }
verify { mockUnitOfWork.persist(expectedEntity, PersistIntent.EXISTING) }
```

- [ ] **Step 2: Run tests to verify failure**

Run:

```powershell
.\gradlew.bat :ddd-domain-repo-jpa:test --tests "com.only4.cap4k.ddd.domain.repo.impl.DefaultRepositorySupervisorTest" --console=plain
```

Expected: FAIL because `JpaRepositoryObservationRecorder` and the new constructor parameter do not exist.

- [ ] **Step 3: Add the recorder contract**

Create `JpaRepositoryObservationRecorder.kt`:

```kotlin
package com.only4.cap4k.ddd.application

import com.only4.cap4k.ddd.core.domain.repo.AggregateLoadPlan

interface JpaRepositoryObservationRecorder {
    fun observeRepositoryLoad(root: Any, loadPlan: AggregateLoadPlan)
}
```

This contract stays in `ddd-domain-repo-jpa`, not `ddd-core`, because repository observation baseline mechanics are JPA runtime details.

- [ ] **Step 4: Add generated owned relation traversal helper**

Create `JpaGeneratedOwnedRelationTraversal.kt`:

```kotlin
package com.only4.cap4k.ddd.application

import jakarta.persistence.CascadeType
import jakarta.persistence.JoinColumn
import jakarta.persistence.OneToMany
import org.hibernate.Hibernate
import java.lang.reflect.Field
import java.util.Collections
import java.util.IdentityHashMap

internal class JpaGeneratedOwnedRelationTraversal {
    fun reachableOwnedEntities(root: Any): List<Any> {
        val visited = Collections.newSetFromMap(IdentityHashMap<Any, Boolean>())
        val result = mutableListOf<Any>()
        visit(root, visited, result)
        return result
    }

    private fun visit(entity: Any, visited: MutableSet<Any>, result: MutableList<Any>) {
        if (!visited.add(entity)) return
        result += entity
        persistentFields(Hibernate.getClassLazy(entity)).forEach { field ->
            val oneToMany = field.getAnnotation(OneToMany::class.java) ?: return@forEach
            if (oneToMany.mappedBy.isNotBlank()) return@forEach
            if (field.getAnnotation(JoinColumn::class.java) == null) return@forEach
            val cascades = oneToMany.cascade.toSet()
            if (CascadeType.PERSIST !in cascades || CascadeType.MERGE !in cascades) return@forEach
            if (!oneToMany.orphanRemoval) return@forEach
            field.isAccessible = true
            val value = field.get(entity) ?: return@forEach
            if (!Hibernate.isInitialized(value)) return@forEach
            if (value is Iterable<*>) {
                value.filterNotNull().forEach { visit(it, visited, result) }
            }
        }
    }

    private fun persistentFields(type: Class<*>): Sequence<Field> =
        generateSequence(type) { current -> current.superclass?.takeIf { it != Any::class.java } }
            .flatMap { it.declaredFields.asSequence() }
}
```

This helper deliberately excludes `ManyToOne`, `OneToOne`, `mappedBy`, non-join-column collections, and uninitialized lazy collections.

- [ ] **Step 5: Add baseline registry**

Create `JpaRepositoryObservationBaseline.kt`:

```kotlin
package com.only4.cap4k.ddd.application

import org.springframework.data.repository.core.EntityInformation
import java.util.LinkedHashSet

internal data class JpaObservedIdentity(
    val entityType: Class<*>,
    val id: Any,
)

internal data class JpaObservedEntity(
    val entity: Any,
    val identity: JpaObservedIdentity?,
)

internal class JpaRepositoryObservationBaseline {
    private val observedByRoot = LinkedHashMap<ObjectIdentityKey, LinkedHashSet<JpaObservedEntity>>()
    private val rootKeyByObservedObject = LinkedHashMap<ObjectIdentityKey, ObjectIdentityKey>()
    private val observedIdentities = LinkedHashSet<JpaObservedIdentity>()

    fun record(root: Any, entries: List<JpaObservedEntity>) {
        val rootKey = ObjectIdentityKey(root)
        val bucket = observedByRoot.getOrPut(rootKey) { LinkedHashSet() }
        entries.forEach { entry ->
            bucket += entry
            rootKeyByObservedObject[ObjectIdentityKey(entry.entity)] = rootKey
            entry.identity?.let { observedIdentities += it }
        }
    }

    fun entriesFor(root: Any): Set<JpaObservedEntity> =
        observedByRoot[rootKeyByObservedObject[ObjectIdentityKey(root)] ?: ObjectIdentityKey(root)].orEmpty()

    fun containsIdentity(identity: JpaObservedIdentity): Boolean =
        identity in observedIdentities

    fun isObservedObject(entity: Any): Boolean =
        rootKeyByObservedObject.containsKey(ObjectIdentityKey(entity))

    fun clear() {
        observedByRoot.clear()
        rootKeyByObservedObject.clear()
        observedIdentities.clear()
    }
}
```

Use the existing private `ObjectIdentityKey` in `JpaUnitOfWork.kt`; if file visibility blocks reuse, move `ObjectIdentityKey` into a new internal file `JpaObjectIdentityKey.kt` with the same reference-equality implementation.

- [ ] **Step 6: Implement observation in `JpaUnitOfWork`**

Make `JpaUnitOfWork` implement `JpaRepositoryObservationRecorder`:

```kotlin
open class JpaUnitOfWork(
    ...
) : UnitOfWork, JpaRepositoryObservationRecorder {
```

Add fields:

```kotlin
private val ownedRelationTraversal = JpaGeneratedOwnedRelationTraversal()
private val repositoryObservationBaseline = JpaRepositoryObservationBaseline()
```

Add recorder implementation:

```kotlin
override fun observeRepositoryLoad(root: Any, loadPlan: AggregateLoadPlan) {
    val observed = ownedRelationTraversal.reachableOwnedEntities(root)
        .map { entity -> JpaObservedEntity(entity, observedIdentityOf(entity)) }
    repositoryObservationBaseline.record(root, observed)
}

private fun observedIdentityOf(entity: Any): JpaObservedIdentity? {
    val entityClass = persistentEntityClass(entity)
    val entityInformation = getEntityInformation(entityClass)
    if (entityInformation.isNew(entity)) return null
    val id = entityInformation.getId(entity) ?: return null
    return JpaObservedIdentity(entityClass, id)
}
```

This task records identity baselines only; it does not yet change save classification.

- [ ] **Step 7: Wire observation in repository supervisor**

Update the constructor:

```kotlin
class DefaultRepositorySupervisor(
    private val repositories: List<Repository<*>>,
    private val unitOfWork: UnitOfWork,
    private val observationRecorder: JpaRepositoryObservationRecorder? =
        unitOfWork as? JpaRepositoryObservationRecorder,
) : RepositorySupervisor {
```

Add helpers:

```kotlin
private fun observe(entity: Any, loadPlan: AggregateLoadPlan) {
    observationRecorder?.observeRepositoryLoad(entity, loadPlan)
}

private fun enrollExisting(entity: Any) {
    unitOfWork.persist(entity, PersistIntent.EXISTING)
}
```

Update every read path to observe before optional enrollment:

```kotlin
.also { entities ->
    entities.forEach { observe(it, loadPlan) }
    if (persist) entities.forEach(::enrollExisting)
}
```

For `findOne` and `findFirst`, use:

```kotlin
?.also { entity ->
    observe(entity, loadPlan)
    if (persist) enrollExisting(entity)
}
```

- [ ] **Step 8: Run focused verification**

Run:

```powershell
.\gradlew.bat :ddd-domain-repo-jpa:test --tests "com.only4.cap4k.ddd.domain.repo.impl.DefaultRepositorySupervisorTest" --tests "com.only4.cap4k.ddd.application.JpaUnitOfWorkTest" --console=plain
```

Expected: repository observation tests PASS; existing `JpaUnitOfWorkTest` still PASS because save behavior is not changed yet.

- [ ] **Step 9: Commit**

```powershell
git add ddd-domain-repo-jpa/src/main/kotlin/com/only4/cap4k/ddd/application/JpaRepositoryObservationRecorder.kt `
        ddd-domain-repo-jpa/src/main/kotlin/com/only4/cap4k/ddd/application/JpaRepositoryObservationBaseline.kt `
        ddd-domain-repo-jpa/src/main/kotlin/com/only4/cap4k/ddd/application/JpaGeneratedOwnedRelationTraversal.kt `
        ddd-domain-repo-jpa/src/main/kotlin/com/only4/cap4k/ddd/application/JpaUnitOfWork.kt `
        ddd-domain-repo-jpa/src/main/kotlin/com/only4/cap4k/ddd/domain/repo/impl/DefaultRepositorySupervisor.kt `
        ddd-domain-repo-jpa/src/test/kotlin/com/only4/cap4k/ddd/domain/repo/impl/DefaultRepositorySupervisorTest.kt `
        ddd-domain-repo-jpa/src/test/kotlin/com/only4/cap4k/ddd/application/JpaUnitOfWorkTest.kt
git commit -m "feat: record repository observation baselines"
```

### Task 6: Convert JpaUnitOfWork Pending State To CREATE/EXISTING/REMOVE

**Files:**
- Modify: `ddd-domain-repo-jpa/src/main/kotlin/com/only4/cap4k/ddd/application/JpaUnitOfWork.kt`
- Modify: `ddd-domain-repo-jpa/src/test/kotlin/com/only4/cap4k/ddd/application/JpaUnitOfWorkTest.kt`

**Interfaces:**
- Consumes: Task 1 `PersistIntent.CREATE/EXISTING`.
- Consumes: Task 5 `JpaRepositoryObservationBaseline`.
- Produces: internal `UnitOfWorkEntryKind.CREATE/EXISTING/REMOVE` and same-instance merge rules aligned with the Phase 2 spec.

- [ ] **Step 1: Write failing pending-state tests**

In `JpaUnitOfWorkTest.kt`, rename the existing update-intent test and update its expectation so default persist is conceptually existing:

```kotlin
@Test
@DisplayName("default persist enrolls an existing detached entity and reports update only after later dirty task")
fun defaultPersistShouldEnrollDetachedExistingEntity() {
    val entity = TestEntity(1L, "existing")
    every { mockEntityInfo.isNew(entity) } returns false
    every { mockEntityInfo.getId(entity) } returns 1L
    every { entityManager.contains(entity) } returns false

    jpaUnitOfWork.persist(entity)
    jpaUnitOfWork.save()

    verify { entityManager.merge(entity) }
    verify(exactly = 0) { entityManager.persist(entity) }
}
```

Add same-instance Phase 2 conflict coverage:

```kotlin
@Test
@DisplayName("CREATE followed by default EXISTING remains CREATE")
fun createThenDefaultExistingShouldRemainCreate() {
    val entity = TestEntity(null, "new")
    every { mockEntityInfo.isNew(entity) } returns true

    jpaUnitOfWork.persist(entity, PersistIntent.CREATE)
    jpaUnitOfWork.persist(entity)
    jpaUnitOfWork.save()

    verify { entityManager.persist(entity) }
    verify(exactly = 0) { entityManager.merge(entity) }
    verify { persistListenerManager.onChange(entity, PersistType.CREATE) }
}

@Test
@DisplayName("EXISTING followed by CREATE should fail fast")
fun existingThenCreateShouldFailFast() {
    val entity = TestEntity(1L, "existing")
    every { mockEntityInfo.isNew(entity) } returns false
    every { mockEntityInfo.getId(entity) } returns 1L

    jpaUnitOfWork.persist(entity)

    val error = assertThrows(IllegalStateException::class.java) {
        jpaUnitOfWork.persist(entity, PersistIntent.CREATE)
    }

    assertEquals("UoW intent conflict: EXISTING cannot become CREATE for the same instance", error.message)
}
```

- [ ] **Step 2: Run UoW tests to verify failure**

Run:

```powershell
.\gradlew.bat :ddd-domain-repo-jpa:test --tests "com.only4.cap4k.ddd.application.JpaUnitOfWorkTest" --console=plain
```

Expected: FAIL because internal `UnitOfWorkIntent.UPDATE` still exists and conflict messages still say `UPDATE`.

- [ ] **Step 3: Rename internal intent and merge rules**

In `JpaUnitOfWork.kt`, replace the internal enum and data class names:

```kotlin
private enum class UnitOfWorkEntryKind {
    CREATE,
    EXISTING,
    REMOVE,
}

private data class UnitOfWorkEntry(
    val entity: Any,
    val kind: UnitOfWorkEntryKind,
)
```

Update the `PersistIntent` mapping:

```kotlin
private fun PersistIntent.toUnitOfWorkEntryKind(): UnitOfWorkEntryKind = when (this) {
    PersistIntent.CREATE -> UnitOfWorkEntryKind.CREATE
    PersistIntent.EXISTING -> UnitOfWorkEntryKind.EXISTING
}
```

Update `PendingChangeSet` to `PendingEntrySet` with these merge rules:

```kotlin
fun persist(entity: Any, intent: PersistIntent): UnitOfWorkEntry {
    val key = ObjectIdentityKey(entity)
    val next = intent.toUnitOfWorkEntryKind()
    val current = entries[key]
    val merged = when (current?.kind) {
        null -> UnitOfWorkEntry(entity, next)
        UnitOfWorkEntryKind.CREATE -> UnitOfWorkEntry(entity, UnitOfWorkEntryKind.CREATE)
        UnitOfWorkEntryKind.EXISTING -> when (next) {
            UnitOfWorkEntryKind.EXISTING -> UnitOfWorkEntry(entity, UnitOfWorkEntryKind.EXISTING)
            UnitOfWorkEntryKind.CREATE -> error("UoW intent conflict: EXISTING cannot become CREATE for the same instance")
            UnitOfWorkEntryKind.REMOVE -> error("persist cannot register REMOVE intent")
        }
        UnitOfWorkEntryKind.REMOVE ->
            error("UoW intent conflict: REMOVE cannot become ${next.name} for the same instance")
    }
    entries[key] = merged
    return merged
}

fun remove(entity: Any) {
    val key = ObjectIdentityKey(entity)
    val current = entries[key]
    when (current?.kind) {
        null -> entries[key] = UnitOfWorkEntry(entity, UnitOfWorkEntryKind.REMOVE)
        UnitOfWorkEntryKind.CREATE -> entries.remove(key)
        UnitOfWorkEntryKind.EXISTING -> entries[key] = UnitOfWorkEntry(entity, UnitOfWorkEntryKind.REMOVE)
        UnitOfWorkEntryKind.REMOVE -> Unit
    }
}
```

Returning the merged entry is required by Task 7 so every `persist(...)` call can rerun idempotent ID completion with the active kind.

- [ ] **Step 4: Update save buckets and method names**

Replace all `PendingChange` usages with `UnitOfWorkEntry`. Replace filters:

```kotlin
val persistEntitySet = pendingEntries
    .filter { it.kind == UnitOfWorkEntryKind.CREATE || it.kind == UnitOfWorkEntryKind.EXISTING }
    .mapTo(InsertionOrderedIdentitySet()) { it.entity }
val deleteEntitySet = pendingEntries
    .filter { it.kind == UnitOfWorkEntryKind.REMOVE }
    .mapTo(InsertionOrderedIdentitySet()) { it.entity }
```

Replace `applyUpdate(...)` with `applyExisting(...)`:

```kotlin
private fun applyExisting(entity: Any, results: FlushResult) {
    validateExistingRootIdentified(entity)
    if (!entityManager.contains(entity)) {
        entityManager.merge(entity)
    }
    results.existing.add(entity)
    results.needsFlush = true
}
```

Keep `FlushResult.updated` out of this task if Task 8 will compute dirty entities; if a temporary field is needed, name it `existing`:

```kotlin
private data class FlushResult(
    val created: InsertionOrderedIdentitySet<Any> = InsertionOrderedIdentitySet(),
    val existing: InsertionOrderedIdentitySet<Any> = InsertionOrderedIdentitySet(),
    val deleted: InsertionOrderedIdentitySet<Any> = InsertionOrderedIdentitySet(),
    val refreshList: MutableList<Any> = mutableListOf(),
    var needsFlush: Boolean = false,
)
```

Do not emit `PersistType.UPDATE` from `existing` in this task. Keep a failing or disabled-by-name assertion out of the code; Task 8 adds dirty classification.

- [ ] **Step 5: Clear observation baseline on reset and save completion**

Add to `JpaUnitOfWork.reset()` only if the baseline registry is in a companion thread-local. If the registry is an instance field, clear it in the `finally` block of `save(...)`:

```kotlin
finally {
    repositoryObservationBaseline.clear()
    popProcessingEntities(currentProcessedEntitySet)
}
```

If baseline state is thread-local, use:

```kotlin
repositoryObservationBaselineThreadLocal.remove()
```

The final implementation must not leak baselines across requests.

- [ ] **Step 6: Run focused verification**

Run:

```powershell
rg -n "UnitOfWorkIntent|PendingChange|PersistIntent\.UPDATE|UPDATE cannot become CREATE|Update-intent" ddd-domain-repo-jpa/src/main/kotlin ddd-domain-repo-jpa/src/test/kotlin ddd-core/src/main/kotlin ddd-core/src/test/kotlin
.\gradlew.bat :ddd-domain-repo-jpa:test --tests "com.only4.cap4k.ddd.application.JpaUnitOfWorkTest" --console=plain
```

Expected: `rg` finds no stale public or internal update-intent vocabulary except `PersistType.UPDATE`; tests PASS after expectations are adjusted to Phase 2 listener behavior.

- [ ] **Step 7: Commit**

```powershell
git add ddd-domain-repo-jpa/src/main/kotlin/com/only4/cap4k/ddd/application/JpaUnitOfWork.kt `
        ddd-domain-repo-jpa/src/test/kotlin/com/only4/cap4k/ddd/application/JpaUnitOfWorkTest.kt
git commit -m "feat: enroll existing entities in uow"
```

### Task 7: Complete Generated Strong Own IDs Idempotently

**Files:**
- Create: `ddd-domain-repo-jpa/src/main/kotlin/com/only4/cap4k/ddd/application/JpaGeneratedStrongIdSupport.kt`
- Modify: `ddd-domain-repo-jpa/src/main/kotlin/com/only4/cap4k/ddd/application/JpaUnitOfWork.kt`
- Modify: `ddd-domain-repo-jpa/src/main/kotlin/com/only4/cap4k/ddd/application/JpaApplicationSideIdSupport.kt`
- Modify: `ddd-domain-repo-jpa/src/test/kotlin/com/only4/cap4k/ddd/application/JpaUnitOfWorkTest.kt`
- Modify: `ddd-domain-repo-jpa/src/test/kotlin/com/only4/cap4k/ddd/application/JpaApplicationSideIdSupportTest.kt`

**Interfaces:**
- Consumes: Task 6 `UnitOfWorkEntryKind`.
- Produces: intent-time and pre-flush ID completion for generated `@EmbeddedId` Strong IDs and compatibility `@ApplicationSideId` fields.

- [ ] **Step 1: Write failing Strong ID completion tests**

In `JpaUnitOfWorkTest.kt`, add local Strong ID fixture classes:

```kotlin
@Embeddable
class TestStrongEntityId protected constructor() : StrongId, Serializable {
    @Column(name = "value", nullable = false, updatable = false, length = 36)
    override lateinit var value: String
        protected set

    constructor(value: String) : this() {
        this.value = value
    }

    companion object {
        fun new(): TestStrongEntityId = TestStrongEntityId("018f0000-0000-7000-8000-000000000001")
    }
}

@jakarta.persistence.Entity
class StrongRootEntity {
    @EmbeddedId
    lateinit var id: TestStrongEntityId

    @OneToMany(cascade = [CascadeType.PERSIST, CascadeType.MERGE], orphanRemoval = true)
    @JoinColumn(name = "root_id", nullable = false)
    val children: MutableList<StrongChildEntity> = mutableListOf()
}

@jakarta.persistence.Entity
class StrongChildEntity {
    @EmbeddedId
    lateinit var id: TestStrongEntityId
}
```

Add create-time assertion:

```kotlin
@Test
@DisplayName("CREATE persist assigns generated strong root id before save")
fun createPersistShouldAssignGeneratedStrongRootIdBeforeSave() {
    val entity = StrongRootEntity()

    jpaUnitOfWork.persist(entity, PersistIntent.CREATE)

    assertEquals("018f0000-0000-7000-8000-000000000001", entity.id.value)
}
```

Add existing-child assertion:

```kotlin
@Test
@DisplayName("EXISTING persist fills new owned child strong id without replacing root id")
fun existingPersistShouldFillNewOwnedChildStrongIdWithoutReplacingRootId() {
    val root = StrongRootEntity()
    root.id = TestStrongEntityId("018f0000-0000-7000-8000-000000000099")
    val child = StrongChildEntity()
    root.children += child
    every { mockEntityInfo.isNew(root) } returns false
    every { mockEntityInfo.getId(root) } returns root.id

    jpaUnitOfWork.observeRepositoryLoad(root, AggregateLoadPlan.WHOLE_AGGREGATE)
    jpaUnitOfWork.persist(root)

    assertEquals("018f0000-0000-7000-8000-000000000099", root.id.value)
    assertEquals("018f0000-0000-7000-8000-000000000001", child.id.value)
}
```

- [ ] **Step 2: Run UoW tests to verify failure**

Run:

```powershell
.\gradlew.bat :ddd-domain-repo-jpa:test --tests "com.only4.cap4k.ddd.application.JpaUnitOfWorkTest.createPersistShouldAssignGeneratedStrongRootIdBeforeSave" --tests "com.only4.cap4k.ddd.application.JpaUnitOfWorkTest.existingPersistShouldFillNewOwnedChildStrongIdWithoutReplacingRootId" --console=plain
```

Expected: FAIL because `JpaGeneratedStrongIdSupport` does not exist and `persist(...)` does not assign generated Strong IDs.

- [ ] **Step 3: Create generated Strong ID support**

Create `JpaGeneratedStrongIdSupport.kt`:

```kotlin
package com.only4.cap4k.ddd.application

import com.only4.cap4k.ddd.core.domain.id.StrongId
import jakarta.persistence.EmbeddedId
import org.hibernate.Hibernate
import java.lang.reflect.Field

internal class JpaGeneratedStrongIdSupport {
    fun completeCreate(root: Any, traversal: JpaGeneratedOwnedRelationTraversal) {
        traversal.reachableOwnedEntities(root).forEach(::completeMissingOwnStrongId)
    }

    fun completeExisting(
        root: Any,
        traversal: JpaGeneratedOwnedRelationTraversal,
        baseline: JpaRepositoryObservationBaseline,
    ) {
        traversal.reachableOwnedEntities(root)
            .filterNot { baseline.isObservedObject(it) }
            .forEach(::completeMissingOwnStrongId)
        validateObservedStrongIds(root, traversal, baseline)
    }

    private fun completeMissingOwnStrongId(entity: Any) {
        ownStrongIdField(entity)?.let { field ->
            field.isAccessible = true
            if (field.get(entity) == null) {
                field.set(entity, newStrongId(field.type))
            }
        }
    }

    private fun validateObservedStrongIds(
        root: Any,
        traversal: JpaGeneratedOwnedRelationTraversal,
        baseline: JpaRepositoryObservationBaseline,
    ) {
        traversal.reachableOwnedEntities(root)
            .filter { baseline.isObservedObject(it) }
            .forEach { entity ->
                ownStrongIdField(entity)?.let { field ->
                    field.isAccessible = true
                    check(field.get(entity) != null) {
                        "Observed existing entity ${Hibernate.getClassLazy(entity).name}.${field.name} has missing Strong ID"
                    }
                }
            }
    }

    private fun ownStrongIdField(entity: Any): Field? =
        persistentFields(Hibernate.getClassLazy(entity)).firstOrNull { field ->
            field.getAnnotation(EmbeddedId::class.java) != null &&
                StrongId::class.java.isAssignableFrom(field.type)
        }

    private fun newStrongId(type: Class<*>): Any {
        val companion = type.getField("Companion").get(null)
        val newMethod = companion.javaClass.methods.firstOrNull { it.name == "new" && it.parameterCount == 0 }
            ?: error("Generated Strong ID ${type.name} must expose companion new() for own ID completion")
        return newMethod.invoke(companion)
    }

    private fun persistentFields(type: Class<*>): Sequence<Field> =
        generateSequence(type) { current -> current.superclass?.takeIf { it != Any::class.java } }
            .flatMap { it.declaredFields.asSequence() }
}
```

This support does not use `@Embedded` fields, so reference Strong IDs are not generated.

- [ ] **Step 4: Run completion on every persist and pre-flush**

In `JpaUnitOfWork`, add:

```kotlin
private val generatedStrongIdSupport = JpaGeneratedStrongIdSupport()
```

Update `persist(...)`:

```kotlin
override fun persist(entity: Any, intent: PersistIntent) {
    val entry = pendingEntriesThreadLocal.get().persist(entity, intent)
    completeIdsForEntry(entry)
}
```

Add:

```kotlin
private fun completeIdsForEntry(entry: UnitOfWorkEntry) {
    when (entry.kind) {
        UnitOfWorkEntryKind.CREATE -> {
            applicationSideIdSupport.assignMissingIds(entry.entity)
            generatedStrongIdSupport.completeCreate(entry.entity, ownedRelationTraversal)
        }
        UnitOfWorkEntryKind.EXISTING -> {
            applicationSideIdSupport.assignMissingIdsToOwnedRelations(entry.entity)
            generatedStrongIdSupport.completeExisting(entry.entity, ownedRelationTraversal, repositoryObservationBaseline)
        }
        UnitOfWorkEntryKind.REMOVE -> Unit
    }
}

private fun prepareApplicationSideIds(entries: List<UnitOfWorkEntry>) {
    entries.forEach(::completeIdsForEntry)
}
```

Keep `prepareApplicationSideIds(...)` before `beforeTransaction(...)` so IDs are visible to interceptors.

- [ ] **Step 5: Preserve compatibility annotation behavior**

Do not remove existing `JpaApplicationSideIdSupport.assignMissingIds(...)` tests. Add one regression in `JpaApplicationSideIdSupportTest.kt`:

```kotlin
@Test
fun `application side compatibility support does not require strong id companion`() {
    val root = RootEntity()

    support.assignMissingIds(root)

    assertEquals(UUID(1L, 2L), root.id)
}
```

This makes the compatibility path explicit after generated Strong ID support is introduced.

- [ ] **Step 6: Run focused verification**

Run:

```powershell
.\gradlew.bat :ddd-domain-repo-jpa:test --tests "com.only4.cap4k.ddd.application.JpaUnitOfWorkTest" --tests "com.only4.cap4k.ddd.application.JpaApplicationSideIdSupportTest" --console=plain
```

Expected: PASS; `CREATE` intent assigns Strong root ID immediately after `persist(...)`; repeated `persist(...)` calls do not overwrite existing IDs.

- [ ] **Step 7: Commit**

```powershell
git add ddd-domain-repo-jpa/src/main/kotlin/com/only4/cap4k/ddd/application/JpaGeneratedStrongIdSupport.kt `
        ddd-domain-repo-jpa/src/main/kotlin/com/only4/cap4k/ddd/application/JpaUnitOfWork.kt `
        ddd-domain-repo-jpa/src/main/kotlin/com/only4/cap4k/ddd/application/JpaApplicationSideIdSupport.kt `
        ddd-domain-repo-jpa/src/test/kotlin/com/only4/cap4k/ddd/application/JpaUnitOfWorkTest.kt `
        ddd-domain-repo-jpa/src/test/kotlin/com/only4/cap4k/ddd/application/JpaApplicationSideIdSupportTest.kt
git commit -m "feat: complete generated strong ids in uow"
```

### Task 8: Emit UPDATE Listener Events Only For Dirty Existing Entities

**Files:**
- Create: `ddd-domain-repo-jpa/src/main/kotlin/com/only4/cap4k/ddd/application/JpaHibernateDirtyInspector.kt`
- Modify: `ddd-domain-repo-jpa/src/main/kotlin/com/only4/cap4k/ddd/application/JpaUnitOfWork.kt`
- Modify: `ddd-domain-repo-jpa/src/test/kotlin/com/only4/cap4k/ddd/application/JpaUnitOfWorkTest.kt`

**Interfaces:**
- Consumes: Task 6 `FlushResult.existing`.
- Produces: dirty-only `PersistType.UPDATE` listener classification for `EXISTING` entries.

- [ ] **Step 1: Write failing dirty-classification tests**

In `JpaUnitOfWorkTest.kt`, create a test seam by making dirty inspection injectable:

```kotlin
class TestableJpaUnitOfWork(
    uowInterceptors: List<UnitOfWorkInterceptor>,
    persistListenerManager: PersistListenerManager,
    supportEntityInlinePersistListener: Boolean,
    idStrategyRegistry: IdStrategyRegistry = MapBackedIdStrategyRegistry(emptyList()),
    private val dirtyExistingEntities: Set<Any> = emptySet(),
) : JpaUnitOfWork(
    uowInterceptors,
    persistListenerManager,
    supportEntityInlinePersistListener,
    idStrategyRegistry
) {
    fun setTestEntityManager(em: EntityManager) {
        this.entityManager = em
    }

    override fun dirtyExistingEntities(existingEntities: Set<Any>): Set<Any> =
        existingEntities.filterTo(LinkedHashSet()) { it in dirtyExistingEntities }
}
```

Add clean existing test:

```kotlin
@Test
@DisplayName("clean existing entity does not emit update listener")
fun cleanExistingEntityShouldNotEmitUpdateListener() {
    val entity = TestEntity(1L, "clean")
    every { mockEntityInfo.isNew(entity) } returns false
    every { mockEntityInfo.getId(entity) } returns 1L
    every { entityManager.contains(entity) } returns true

    jpaUnitOfWork.persist(entity)
    jpaUnitOfWork.save()

    verify(exactly = 0) { persistListenerManager.onChange(entity, PersistType.UPDATE) }
}
```

Add dirty existing test:

```kotlin
@Test
@DisplayName("dirty existing entity emits update listener")
fun dirtyExistingEntityShouldEmitUpdateListener() {
    val entity = TestEntity(1L, "dirty")
    jpaUnitOfWork = TestableJpaUnitOfWork(
        uowInterceptors = uowInterceptors,
        persistListenerManager = persistListenerManager,
        supportEntityInlinePersistListener = true,
        idStrategyRegistry = MapBackedIdStrategyRegistry(listOf(FixedLongStrategy())),
        dirtyExistingEntities = setOf(entity),
    )
    jpaUnitOfWork.setTestEntityManager(entityManager)
    JpaUnitOfWork.fixAopWrapper(jpaUnitOfWork)
    every { mockEntityInfo.isNew(entity) } returns false
    every { mockEntityInfo.getId(entity) } returns 1L
    every { entityManager.contains(entity) } returns true

    jpaUnitOfWork.persist(entity)
    jpaUnitOfWork.save()

    verify { persistListenerManager.onChange(entity, PersistType.UPDATE) }
}
```

- [ ] **Step 2: Run tests to verify failure**

Run:

```powershell
.\gradlew.bat :ddd-domain-repo-jpa:test --tests "com.only4.cap4k.ddd.application.JpaUnitOfWorkTest.cleanExistingEntityShouldNotEmitUpdateListener" --tests "com.only4.cap4k.ddd.application.JpaUnitOfWorkTest.dirtyExistingEntityShouldEmitUpdateListener" --console=plain
```

Expected: FAIL because `dirtyExistingEntities(...)` seam does not exist and existing entries still map directly to update listener output.

- [ ] **Step 3: Add Hibernate dirty inspector**

Create `JpaHibernateDirtyInspector.kt`:

```kotlin
package com.only4.cap4k.ddd.application

import jakarta.persistence.EntityManager
import org.hibernate.engine.spi.SharedSessionContractImplementor
import org.hibernate.engine.spi.Status
import java.util.LinkedHashSet

internal class JpaHibernateDirtyInspector(
    private val entityManager: EntityManager,
) {
    fun dirtyManagedEntities(candidates: Iterable<Any>): Set<Any> {
        val session = entityManager.unwrap(SharedSessionContractImplementor::class.java)
        return candidates.filterTo(LinkedHashSet()) { entity ->
            isDirty(session, entity)
        }
    }

    private fun isDirty(session: SharedSessionContractImplementor, entity: Any): Boolean {
        val entry = session.persistenceContextInternal.getEntry(entity) ?: return true
        if (entry.status != Status.MANAGED) return false
        val loadedState = entry.loadedState ?: return true
        val persister = entry.persister
        val currentState = persister.getValues(entity)
        return persister.findDirty(currentState, loadedState, entity, session)?.isNotEmpty() == true
    }
}
```

This uses Hibernate SPI only inside `ddd-domain-repo-jpa`; if Kotlin property access does not compile against 6.4.10.Final, use the equivalent Java getters from the same types without changing the algorithm:

```kotlin
val entry = session.persistenceContextInternal.getEntry(entity)
val loadedState = entry.loadedState
val currentState = entry.persister.getValues(entity)
```

- [ ] **Step 4: Add protected dirty seam and listener classification**

In `JpaUnitOfWork.kt`, add:

```kotlin
protected open fun dirtyExistingEntities(existingEntities: Set<Any>): Set<Any> =
    JpaHibernateDirtyInspector(entityManager).dirtyManagedEntities(existingEntities)
```

After applying create/existing/remove operations and before `entityManager.flush()`, compute:

```kotlin
val dirtyExisting = dirtyExistingEntities(results.existing)
```

Then call:

```kotlin
if (results.needsFlush) {
    entityManager.flush()
    results.refreshList.forEach { entityManager.refresh(it) }
    onEntitiesFlushed(results.created, dirtyExisting, results.deleted)
}
```

Do not call `onEntitiesFlushed(results.created, results.existing, results.deleted)`.

- [ ] **Step 5: Preserve created and deleted listener behavior**

Run or update existing tests so these remain true:

```kotlin
verify { persistListenerManager.onChange(entity, PersistType.CREATE) }
verify { persistListenerManager.onChange(entity, PersistType.DELETE) }
```

No test should expect `PersistType.UPDATE` merely because `persist(entity)` was called.

- [ ] **Step 6: Run focused verification**

Run:

```powershell
.\gradlew.bat :ddd-domain-repo-jpa:test --tests "com.only4.cap4k.ddd.application.JpaUnitOfWorkTest" --console=plain
rg -n "onEntitiesFlushed\\(results\\.created, results\\.existing|PersistType\\.UPDATE" ddd-domain-repo-jpa/src/main/kotlin ddd-domain-repo-jpa/src/test/kotlin
```

Expected: tests PASS; production code has exactly one route where `PersistType.UPDATE` is emitted, through `dirtyExisting`.

- [ ] **Step 7: Commit**

```powershell
git add ddd-domain-repo-jpa/src/main/kotlin/com/only4/cap4k/ddd/application/JpaHibernateDirtyInspector.kt `
        ddd-domain-repo-jpa/src/main/kotlin/com/only4/cap4k/ddd/application/JpaUnitOfWork.kt `
        ddd-domain-repo-jpa/src/test/kotlin/com/only4/cap4k/ddd/application/JpaUnitOfWorkTest.kt
git commit -m "feat: emit update listeners for dirty existing entities"
```

### Task 9: Add Runtime Fixtures For Root And Owned Child Strong IDs

**Files:**
- Modify: `cap4k-ddd-starter/src/test/kotlin/com/only4/cap4k/ddd/runtime/strongid/StrongIdJpaRuntimeTest.kt`
- Create: `cap4k-ddd-starter/src/test/kotlin/com/only4/cap4k/ddd/runtime/strongid/StrongIdUowRuntimeTest.kt`

**Interfaces:**
- Consumes: Task 4 generated JPA shape for own Strong IDs.
- Consumes: Task 7 UoW generated Strong ID completion.
- Consumes: Task 8 dirty-only listener classification.
- Produces: focused H2 runtime evidence for child `@EmbeddedId`, parent FK storage, create-time root ID completion, pre-flush child ID completion, and clean-load no-update audit behavior.

- [ ] **Step 1: Extend existing Strong ID JPA fixture**

In `StrongIdJpaRuntimeTest.kt`, add child ID and child entity:

```kotlin
@Embeddable
class StrongContentItemId protected constructor() : StrongId, Serializable {
    @Column(name = "value", nullable = false, updatable = false, length = 36)
    override lateinit var value: String
        protected set

    constructor(value: String) : this() {
        this.value = StrongIds.requireUuidV7(value, "StrongContentItemId")
    }

    companion object {
        fun new(): StrongContentItemId = StrongContentItemId(StrongIds.newUuidV7String())
        fun parse(value: String): StrongContentItemId = StrongContentItemId(value)
    }

    override fun equals(other: Any?): Boolean =
        this === other || (other is StrongContentItemId && value == other.value)

    override fun hashCode(): Int = value.hashCode()
    override fun toString(): String = value
}

@Entity
@Table(name = "`strong_content_item`")
open class StrongContentItem protected constructor() {
    @EmbeddedId
    @AttributeOverride(name = "value", column = Column(name = "`id`", nullable = false, updatable = false, length = 36))
    open lateinit var id: StrongContentItemId
        protected set

    @Column(name = "`label`", nullable = false)
    open lateinit var label: String
        protected set

    constructor(id: StrongContentItemId, label: String) : this() {
        this.id = id
        this.label = label
    }
}
```

Add an owned collection to `StrongContent`:

```kotlin
@OneToMany(cascade = [CascadeType.PERSIST, CascadeType.MERGE, CascadeType.REMOVE], orphanRemoval = true)
@JoinColumn(name = "`content_id`", nullable = false)
open val items: MutableList<StrongContentItem> = mutableListOf()

fun hasAssignedId(): Boolean = this::id.isInitialized
```

Update imports to include `CascadeType`, `JoinColumn`, and `OneToMany`.

- [ ] **Step 2: Add child persistence assertion**

In `StrongIdJpaRuntimeTest.kt`, add:

```kotlin
@Test
fun `hibernate persists owned child by strong id and parent fk storage`() {
    val contentId = StrongContentId.new()
    val itemId = StrongContentItemId.new()
    val content = StrongContent(
        id = contentId,
        title = "content-with-item",
        authorId = StrongAuthorId.new(),
        mediaProcessingTaskId = null,
    )
    content.items += StrongContentItem(itemId, "chapter-1")

    repository.saveAndFlush(content)

    val persistedItemId = jdbcTemplate.queryForObject(
        """select "id" from "strong_content_item" where "label" = ?""",
        String::class.java,
        "chapter-1",
    )
    val persistedParentId = jdbcTemplate.queryForObject(
        """select "content_id" from "strong_content_item" where "label" = ?""",
        String::class.java,
        "chapter-1",
    )

    assertEquals(itemId.value, persistedItemId)
    assertEquals(contentId.value, persistedParentId)
}
```

- [ ] **Step 3: Create UoW runtime fixture**

Create `StrongIdUowRuntimeTest.kt` with a `@DataJpaTest` fixture that wires `JpaUnitOfWork` directly:

```kotlin
package com.only4.cap4k.ddd.runtime.strongid

import com.only4.cap4k.ddd.application.JpaUnitOfWork
import com.only4.cap4k.ddd.core.application.PersistIntent
import com.only4.cap4k.ddd.core.application.UnitOfWorkInterceptor
import com.only4.cap4k.ddd.core.domain.repo.AggregateLoadPlan
import com.only4.cap4k.ddd.core.domain.repo.PersistListenerManager
import com.only4.cap4k.ddd.core.domain.repo.PersistType
import io.mockk.clearMocks
import io.mockk.mockk
import io.mockk.verify
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.config.EnableJpaRepositories

@DataJpaTest(
    properties = [
        "spring.datasource.url=jdbc:h2:mem:strong-id-uow-runtime;MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.open-in-view=false",
        "spring.jpa.show-sql=false",
        "logging.level.org.hibernate=WARN",
    ]
)
@Import(StrongIdUowRuntimeTest.TestConfig::class)
class StrongIdUowRuntimeTest {
    @Autowired lateinit var entityManager: EntityManager
    @Autowired lateinit var repository: StrongIdJpaRepository
    @Autowired lateinit var persistListenerManager: PersistListenerManager
    @Autowired lateinit var unitOfWork: JpaUnitOfWork

    @BeforeEach
    fun reset() {
        JpaUnitOfWork.reset()
        JpaUnitOfWork.fixAopWrapper(unitOfWork)
        clearMocks(persistListenerManager)
    }

    @Test
    fun `create intent completes root strong id before save`() {
        val content = StrongContent.unassigned("uow-create")

        unitOfWork.persist(content, PersistIntent.CREATE)

        assertTrue(content.hasAssignedId())
        unitOfWork.save()
        assertTrue(repository.findById(content.id).isPresent)
    }

    @Test
    fun `existing enrollment completes new owned child before flush`() {
        val content = repository.saveAndFlush(
            StrongContent(
                id = StrongContentId.new(),
                title = "existing-root",
                authorId = StrongAuthorId.new(),
                mediaProcessingTaskId = null,
            )
        )
        entityManager.clear()
        val loaded = repository.findById(content.id).orElseThrow()
        unitOfWork.observeRepositoryLoad(loaded, AggregateLoadPlan.WHOLE_AGGREGATE)
        loaded.items += StrongContentItem.unassigned("new-child")

        unitOfWork.persist(loaded)
        unitOfWork.save()

        assertTrue(loaded.items.single().hasAssignedId())
    }

    @Test
    fun `clean existing enrollment does not emit update listener`() {
        val content = repository.saveAndFlush(
            StrongContent(
                id = StrongContentId.new(),
                title = "clean-root",
                authorId = StrongAuthorId.new(),
                mediaProcessingTaskId = null,
            )
        )
        entityManager.clear()
        val loaded = repository.findById(content.id).orElseThrow()
        unitOfWork.observeRepositoryLoad(loaded, AggregateLoadPlan.WHOLE_AGGREGATE)

        unitOfWork.persist(loaded)
        unitOfWork.save()

        verify(exactly = 0) { persistListenerManager.onChange(loaded, PersistType.UPDATE) }
    }

    @SpringBootApplication
    @EntityScan(basePackageClasses = [StrongContent::class])
    @EnableJpaRepositories(basePackageClasses = [StrongIdJpaRepository::class])
    class TestApplication

    class TestConfig {
        @Bean
        fun persistListenerManager(): PersistListenerManager = mockk(relaxed = true)

        @Bean
        fun jpaUnitOfWork(persistListenerManager: PersistListenerManager): JpaUnitOfWork =
            JpaUnitOfWork(emptyList<UnitOfWorkInterceptor>(), persistListenerManager, true)
    }
}
```

Add helper constructors/methods to the local fixture entities in `StrongIdJpaRuntimeTest.kt`:

```kotlin
companion object {
    fun unassigned(title: String): StrongContent =
        StrongContent().also { it.title = title; it.authorId = StrongAuthorId.new() }
}
```

and:

```kotlin
companion object {
    fun unassigned(label: String): StrongContentItem =
        StrongContentItem().also { it.label = label }
}

fun hasAssignedId(): Boolean = this::id.isInitialized
```

Make protected no-arg constructors accessible to the same package where the tests need direct unassigned construction.

- [ ] **Step 4: Run focused runtime verification**

Run:

```powershell
.\gradlew.bat :cap4k-ddd-starter:test --tests "com.only4.cap4k.ddd.runtime.strongid.StrongIdJpaRuntimeTest" --tests "com.only4.cap4k.ddd.runtime.strongid.StrongIdUowRuntimeTest" --console=plain
```

Expected: PASS. If full `:cap4k-ddd-starter:test` still fails outside these classes, record it as known starter fixture debt unless the failure appears in one of these two focused strong-id tests.

- [ ] **Step 5: Commit**

```powershell
git add cap4k-ddd-starter/src/test/kotlin/com/only4/cap4k/ddd/runtime/strongid/StrongIdJpaRuntimeTest.kt `
        cap4k-ddd-starter/src/test/kotlin/com/only4/cap4k/ddd/runtime/strongid/StrongIdUowRuntimeTest.kt
git commit -m "test: cover strong id owned entity runtime"
```

### Task 10: Final Verification And Documentation Update

**Files:**
- Modify: `docs/superpowers/specs/2026-07-22-cap4k-all-entity-strong-id-design.md`
- Modify: `docs/superpowers/specs/2026-07-22-cap4k-identity-roadmap-design.md`
- Modify: `docs/superpowers/plans/2026-07-23-cap4k-all-entity-strong-id-phase2.md`

**Interfaces:**
- Consumes: all previous task commits.
- Produces: final static and focused-runtime evidence summary without overstating full verification.

- [ ] **Step 1: Static drift scan**

Run:

```powershell
rg -n "PersistIntent\.UPDATE|UnitOfWorkIntent\.UPDATE|AGGREGATE_ROOT|isGeneratedAggregateRootStrongId|generatedAggregateRootStrongIdType|persistIfNotExist|@Reference" ddd-core ddd-domain-repo-jpa cap4k-plugin-pipeline-api cap4k-plugin-pipeline-core cap4k-plugin-pipeline-generator-aggregate cap4k-plugin-pipeline-renderer-pebble cap4k-plugin-pipeline-source-db
```

Expected: no production references to removed input intent/root-only Strong ID gates; no `persistIfNotExist`; no generated identity inference from removed `@Reference`.

- [ ] **Step 2: Focused module verification**

Run:

```powershell
.\gradlew.bat :ddd-core:test `
    :ddd-domain-repo-jpa:test `
    :cap4k-plugin-pipeline-api:test `
    :cap4k-plugin-pipeline-source-db:test `
    :cap4k-plugin-pipeline-core:test `
    :cap4k-plugin-pipeline-generator-aggregate:test `
    :cap4k-plugin-pipeline-renderer-pebble:test `
    --console=plain
```

Expected: PASS for all modules listed.

- [ ] **Step 3: Focused runtime verification**

Run:

```powershell
.\gradlew.bat :cap4k-ddd-starter:test --tests "com.only4.cap4k.ddd.runtime.strongid.StrongIdJpaRuntimeTest" --tests "com.only4.cap4k.ddd.runtime.strongid.StrongIdUowRuntimeTest" --console=plain
```

Expected: PASS for strong-id runtime fixtures.

- [ ] **Step 4: Optional broad verification**

Run when time allows:

```powershell
.\gradlew.bat test --continue --console=plain
```

Expected: PASS or known unrelated starter fixture failures documented with exact test names. Do not claim full verification if this command times out or fails.

- [ ] **Step 5: Update docs**

In `2026-07-22-cap4k-all-entity-strong-id-design.md`, add an implementation evidence section:

```markdown
## implementationEvidence

Status: implemented on the current implementation branch after focused verification.

Evidence:

- `PersistIntent` exposes `CREATE` and `EXISTING`; `PersistType.UPDATE` remains listener output only.
- DB/schema `@IdStrategy=uuid7` generates own Strong IDs for aggregate roots and owned child entities.
- `@IdStrategy=db_identity` keeps primitive provider-generated primary keys.
- Repository reads record observation baselines for `persist=false` and `persist=true`.
- `persist=true` promotes the observed graph as `EXISTING` without implying dirty update.
- Generated Strong ID own IDs are completed idempotently for `CREATE` roots and new owned children under `EXISTING` roots.
- Update listeners run only for Hibernate-dirty existing entities.

Verification:

- Focused module verification command from Task 10 Step 2 completed with PASS.
- Strong ID runtime verification command from Task 10 Step 3 completed with PASS.
- Broad verification command from Task 10 Step 4 completed with PASS, or this section records the exact timeout/failure command and class names before the implementation is handed off.
```

In `2026-07-22-cap4k-identity-roadmap-design.md`, update Phase 2 status from "Spec exists" to the actual result only after the implementation branch is merged or accepted by the maintainer.

- [ ] **Step 6: Final diff review**

Run:

```powershell
git diff --check
git status --short
git diff --stat HEAD
```

Expected: `git diff --check` reports no whitespace errors; `git status --short` lists only intended files; `git diff --stat HEAD` is explainable by the task list above.

- [ ] **Step 7: Commit**

```powershell
git add docs/superpowers/specs/2026-07-22-cap4k-all-entity-strong-id-design.md `
        docs/superpowers/specs/2026-07-22-cap4k-identity-roadmap-design.md `
        docs/superpowers/plans/2026-07-23-cap4k-all-entity-strong-id-phase2.md
git commit -m "docs: record all entity strong id evidence"
```

## Self-Review Checklist

- Spec coverage: Tasks 2-4 cover generator input, canonical Strong ID metadata, owned child own IDs, reference separation, database identity primitive fallback, and template output. Tasks 5-8 cover repository observation baseline, `EXISTING` enrollment, ID completion timing, and dirty-only update classification. Task 9 covers focused runtime fixtures. Task 10 covers final evidence and roadmap status.
- Scope protection: no task adds mediator ID allocation, non-Strong-ID create-time injection, composite PK support, broad handwritten JPA graph traversal, `mappedBy` write-model support, or core Hibernate dependency.
- Strong ID kind consistency: `OWN_ID` is the single generating kind; `REFERENCE` and `AGGREGATE_REFERENCE` do not receive `new()`.
- UoW vocabulary consistency: `CREATE` and `EXISTING` are pre-flush input intents; `CREATE`, `UPDATE`, and `DELETE` remain listener result classifications through `PersistType`.
- Baseline consistency: repository observation is not a write registration; `persist=true` observes first and then enrolls existing.
- Verification claim limit: this plan requires focused module/runtime tests before implementation success claims; full `gradlew test` may be reported only with actual output.

## Execution Handoff

Plan complete and saved to `docs/superpowers/plans/2026-07-23-cap4k-all-entity-strong-id-phase2.md`.

Two execution options:

1. **Subagent-Driven (recommended)** - dispatch a fresh subagent per task, review between tasks, fast iteration.
2. **Inline Execution** - execute tasks in this session using `superpowers:executing-plans`, batch execution with checkpoints.

Recommended choice for this plan: **Subagent-Driven**, because generator metadata, template rendering, JPA UoW runtime, and Spring runtime fixtures are separable review gates.

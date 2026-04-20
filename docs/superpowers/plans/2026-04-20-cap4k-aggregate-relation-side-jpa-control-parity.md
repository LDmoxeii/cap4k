# Cap4k Aggregate Relation-Side JPA Control Parity Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add bounded relation-side JPA control parity for the existing aggregate relation line so generated entity output emits `ONE_TO_MANY` cascade/orphan-removal behavior and explicit join-column nullability for `MANY_TO_ONE` and `ONE_TO_ONE` without reopening `mappedBy`, `@JoinTable`, `ManyToMany`, or relation ownership redesign.

**Architecture:** Keep the work on the existing aggregate relation path. Extend `AggregateRelationModel` with three bounded control fields, derive them inside `AggregateRelationInference` from already-stable relation truth, let `AggregateRelationPlanning` expose render-ready relation control and import gating, and keep `aggregate/entity.kt.peb` mechanical. Verification stays layered: canonical inference, planner, renderer, then existing functional and compile fixtures.

**Tech Stack:** Kotlin, JUnit 5, Gradle TestKit, Pebble preset rendering, existing aggregate db source/canonical relation line

---

## File Structure

### Existing files to modify

- Modify: `cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModels.kt`
- Modify: `cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/AggregateRelationInference.kt`
- Modify: `cap4k-plugin-pipeline-core/src/test/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssemblerTest.kt`
- Modify: `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateRelationPlanning.kt`
- Modify: `cap4k-plugin-pipeline-generator-aggregate/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateArtifactPlannerTest.kt`
- Modify: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/entity.kt.peb`
- Modify: `cap4k-plugin-pipeline-renderer-pebble/src/test/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PebbleArtifactRendererTest.kt`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginFunctionalTest.kt`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginCompileFunctionalTest.kt`

### Existing fixtures to reuse

- Reuse: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-relation-sample/schema.sql`
- Reuse: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-relation-compile-sample/schema.sql`

### Responsibilities

- `PipelineModels.kt`
  - carry only the bounded relation-side control metadata that the current relation line can safely support

- `AggregateRelationInference.kt`
  - derive `cascadeAll`, `orphanRemoval`, and `joinColumnNullable` from existing relation truth without reopening ownership design

- `AggregateRelationPlanning.kt`
  - convert bounded canonical relation control into render-ready relation fields and gated JPA imports

- `aggregate/entity.kt.peb`
  - emit bounded `cascade`, `orphanRemoval`, and `nullable` parameters mechanically, not by inference

- existing functional and compile tests
  - prove the current aggregate relation fixtures now render and compile with the extra relation-side JPA control layer

## Task 1: Extend Canonical Relation Model with Bounded Control Fields

**Files:**
- Modify: `cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModels.kt`
- Modify: `cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/AggregateRelationInference.kt`
- Modify: `cap4k-plugin-pipeline-core/src/test/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssemblerTest.kt`

- [ ] **Step 1: Write the failing canonical inference tests**

Add to `DefaultCanonicalAssemblerTest.kt` cases equivalent to:

```kotlin
@Test
fun `assembler enriches bounded one to many relation controls`() {
    val assembler = DefaultCanonicalAssembler()

    val model = assembler.assemble(
        config = baseConfig(),
        snapshots = listOf(
            DbSchemaSnapshot(
                tables = listOf(
                    DbTableSnapshot(
                        tableName = "video_post",
                        comment = "@AggregateRoot=true;",
                        columns = listOf(
                            DbColumnSnapshot("id", "BIGINT", "Long", false, isPrimaryKey = true),
                            DbColumnSnapshot("title", "VARCHAR", "String", false),
                        ),
                        primaryKey = listOf("id"),
                        uniqueConstraints = emptyList(),
                    ),
                    DbTableSnapshot(
                        tableName = "video_post_item",
                        comment = "@Parent=video_post;@VO;",
                        columns = listOf(
                            DbColumnSnapshot("id", "BIGINT", "Long", false, isPrimaryKey = true),
                            DbColumnSnapshot(
                                "video_post_id",
                                "BIGINT",
                                "Long",
                                false,
                                referenceTable = "video_post",
                                lazy = true,
                            ),
                            DbColumnSnapshot("label", "VARCHAR", "String", false),
                        ),
                        primaryKey = listOf("id"),
                        uniqueConstraints = emptyList(),
                        parentTable = "video_post",
                        aggregateRoot = false,
                        valueObject = true,
                    ),
                )
            )
        ),
    ).model

    val relation = model.aggregateRelations.single { it.relationType == AggregateRelationType.ONE_TO_MANY }

    assertEquals("items", relation.fieldName)
    assertEquals(true, relation.cascadeAll)
    assertEquals(true, relation.orphanRemoval)
    assertEquals(false, relation.joinColumnNullable)
}

@Test
fun `assembler preserves bounded join column nullability for direct relations`() {
    val assembler = DefaultCanonicalAssembler()

    val model = assembler.assemble(
        config = baseConfig(),
        snapshots = listOf(
            DbSchemaSnapshot(
                tables = listOf(
                    DbTableSnapshot(
                        tableName = "video_post",
                        comment = "@AggregateRoot=true;",
                        columns = listOf(
                            DbColumnSnapshot("id", "BIGINT", "Long", false, isPrimaryKey = true),
                            DbColumnSnapshot(
                                "author_id",
                                "BIGINT",
                                "Long",
                                false,
                                referenceTable = "user_profile",
                                explicitRelationType = "MANY_TO_ONE",
                                lazy = true,
                            ),
                            DbColumnSnapshot(
                                "cover_profile_id",
                                "BIGINT",
                                "Long",
                                true,
                                referenceTable = "user_profile",
                                explicitRelationType = "ONE_TO_ONE",
                            ),
                        ),
                        primaryKey = listOf("id"),
                        uniqueConstraints = emptyList(),
                    ),
                    DbTableSnapshot(
                        tableName = "user_profile",
                        comment = "@AggregateRoot=true;",
                        columns = listOf(
                            DbColumnSnapshot("id", "BIGINT", "Long", false, isPrimaryKey = true),
                        ),
                        primaryKey = listOf("id"),
                        uniqueConstraints = emptyList(),
                    ),
                )
            )
        ),
    ).model

    val author = model.aggregateRelations.single { it.fieldName == "author" }
    val coverProfile = model.aggregateRelations.single { it.fieldName == "coverProfile" }

    assertEquals(false, author.cascadeAll)
    assertEquals(false, author.orphanRemoval)
    assertEquals(false, author.joinColumnNullable)
    assertEquals(false, author.nullable)

    assertEquals(false, coverProfile.cascadeAll)
    assertEquals(false, coverProfile.orphanRemoval)
    assertEquals(true, coverProfile.joinColumnNullable)
    assertEquals(true, coverProfile.nullable)
}
```

- [ ] **Step 2: Run the core relation tests to verify they fail**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-core:test --tests "com.only4.cap4k.plugin.pipeline.core.DefaultCanonicalAssemblerTest"
```

Expected: FAIL because `AggregateRelationModel` and relation inference do not yet expose relation-side control fields.

- [ ] **Step 3: Add bounded canonical relation control fields and inference**

Extend `PipelineModels.kt`:

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
    val cascadeAll: Boolean = false,
    val orphanRemoval: Boolean = false,
    val joinColumnNullable: Boolean? = null,
)
```

Then update `AggregateRelationInference.kt` so the parent-child branch derives bounded collection controls:

```kotlin
AggregateRelationModel(
    ownerEntityName = resolvedParent.entityName,
    ownerEntityPackageName = resolvedParent.packageName,
    fieldName = parentChildFieldName(parentTable, child.tableName),
    targetEntityName = target.entityName,
    targetEntityPackageName = target.packageName,
    relationType = AggregateRelationType.ONE_TO_MANY,
    joinColumn = joinColumn,
    fetchType = AggregateFetchType.LAZY,
    nullable = false,
    cascadeAll = true,
    orphanRemoval = true,
    joinColumnNullable = false,
)
```

and the explicit relation branch keeps the slice bounded:

```kotlin
AggregateRelationModel(
    ownerEntityName = owner.entityName,
    ownerEntityPackageName = owner.packageName,
    fieldName = relationFieldName(column.name, resolvedTarget.entityName),
    targetEntityName = resolvedTarget.entityName,
    targetEntityPackageName = resolvedTarget.packageName,
    relationType = relationType,
    joinColumn = column.name,
    fetchType = if (column.lazy == true) AggregateFetchType.LAZY else AggregateFetchType.EAGER,
    nullable = column.nullable,
    cascadeAll = false,
    orphanRemoval = false,
    joinColumnNullable = column.nullable,
)
```

- [ ] **Step 4: Run the core relation tests to verify they pass**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-core:test --tests "com.only4.cap4k.plugin.pipeline.core.DefaultCanonicalAssemblerTest"
```

Expected: PASS with bounded control fields present only on currently supported relation forms.

- [ ] **Step 5: Commit**

```bash
git add cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModels.kt cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/AggregateRelationInference.kt cap4k-plugin-pipeline-core/src/test/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssemblerTest.kt
git commit -m "feat: add bounded aggregate relation controls"
```

## Task 2: Expose Bounded Relation-Side Control Through Aggregate Relation Planning

**Files:**
- Modify: `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateRelationPlanning.kt`
- Modify: `cap4k-plugin-pipeline-generator-aggregate/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateArtifactPlannerTest.kt`

- [ ] **Step 1: Write the failing planner tests**

Add to `AggregateArtifactPlannerTest.kt` cases equivalent to:

```kotlin
@Test
fun `entity planner exposes bounded relation side control for collection and direct relations`() {
    val entity = EntityModel(
        name = "VideoPost",
        packageName = "com.acme.demo.domain.aggregates.video_post",
        tableName = "video_post",
        comment = "video post",
        fields = listOf(
            FieldModel("id", "Long"),
            FieldModel("title", "String"),
        ),
        idField = FieldModel("id", "Long"),
    )

    val entityItem = AggregateArtifactPlanner().plan(
        aggregateConfig(),
        CanonicalModel(
            entities = listOf(entity),
            aggregateEntityJpa = listOf(defaultAggregateEntityJpa(entity)),
            aggregateRelations = listOf(
                AggregateRelationModel(
                    ownerEntityName = entity.name,
                    ownerEntityPackageName = entity.packageName,
                    fieldName = "author",
                    targetEntityName = "UserProfile",
                    targetEntityPackageName = "com.acme.demo.domain.identity.user",
                    relationType = AggregateRelationType.MANY_TO_ONE,
                    joinColumn = "author_id",
                    fetchType = AggregateFetchType.LAZY,
                    nullable = false,
                    joinColumnNullable = false,
                ),
                AggregateRelationModel(
                    ownerEntityName = entity.name,
                    ownerEntityPackageName = entity.packageName,
                    fieldName = "coverProfile",
                    targetEntityName = "UserProfile",
                    targetEntityPackageName = "com.acme.demo.domain.identity.user",
                    relationType = AggregateRelationType.ONE_TO_ONE,
                    joinColumn = "cover_profile_id",
                    fetchType = AggregateFetchType.EAGER,
                    nullable = true,
                    joinColumnNullable = true,
                ),
                AggregateRelationModel(
                    ownerEntityName = entity.name,
                    ownerEntityPackageName = entity.packageName,
                    fieldName = "items",
                    targetEntityName = "VideoPostItem",
                    targetEntityPackageName = "com.acme.demo.domain.aggregates.video_post_item",
                    relationType = AggregateRelationType.ONE_TO_MANY,
                    joinColumn = "video_post_id",
                    fetchType = AggregateFetchType.LAZY,
                    nullable = false,
                    cascadeAll = true,
                    orphanRemoval = true,
                    joinColumnNullable = false,
                ),
            ),
        )
    ).single { it.templateId == "aggregate/entity.kt.peb" }

    @Suppress("UNCHECKED_CAST")
    val relationFields = entityItem.context["relationFields"] as List<Map<String, Any?>>
    @Suppress("UNCHECKED_CAST")
    val jpaImports = entityItem.context["jpaImports"] as List<String>

    assertEquals(false, relationFields.single { it["name"] == "author" }["joinColumnNullable"])
    assertEquals(true, relationFields.single { it["name"] == "coverProfile" }["joinColumnNullable"])
    assertEquals(true, relationFields.single { it["name"] == "items" }["cascadeAll"])
    assertEquals(true, relationFields.single { it["name"] == "items" }["orphanRemoval"])
    assertEquals(false, relationFields.single { it["name"] == "items" }["joinColumnNullable"])
    assertTrue(jpaImports.contains("jakarta.persistence.CascadeType"))
}

@Test
fun `entity planner does not import cascade type when no one to many control is present`() {
    val entity = EntityModel(
        name = "VideoPost",
        packageName = "com.acme.demo.domain.aggregates.video_post",
        tableName = "video_post",
        comment = "video post",
        fields = listOf(FieldModel("id", "Long")),
        idField = FieldModel("id", "Long"),
    )

    val entityItem = AggregateArtifactPlanner().plan(
        aggregateConfig(),
        CanonicalModel(
            entities = listOf(entity),
            aggregateEntityJpa = listOf(defaultAggregateEntityJpa(entity)),
            aggregateRelations = listOf(
                AggregateRelationModel(
                    ownerEntityName = entity.name,
                    ownerEntityPackageName = entity.packageName,
                    fieldName = "author",
                    targetEntityName = "UserProfile",
                    targetEntityPackageName = "com.acme.demo.domain.identity.user",
                    relationType = AggregateRelationType.MANY_TO_ONE,
                    joinColumn = "author_id",
                    fetchType = AggregateFetchType.LAZY,
                    nullable = false,
                    joinColumnNullable = false,
                ),
            ),
        )
    ).single { it.templateId == "aggregate/entity.kt.peb" }

    @Suppress("UNCHECKED_CAST")
    val jpaImports = entityItem.context["jpaImports"] as List<String>

    assertFalse(jpaImports.contains("jakarta.persistence.CascadeType"))
}
```

- [ ] **Step 2: Run the aggregate planner tests to verify they fail**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-generator-aggregate:test --tests "com.only4.cap4k.plugin.pipeline.generator.aggregate.AggregateArtifactPlannerTest"
```

Expected: FAIL because `AggregateRelationPlanning` does not yet expose `cascadeAll`, `orphanRemoval`, `joinColumnNullable`, or `CascadeType` import gating.

- [ ] **Step 3: Extend relation planning with bounded render control**

Update `AggregateRelationPlanning.kt` so each relation render item includes the new control fields:

```kotlin
mapOf(
    "name" to relation.fieldName,
    "targetType" to relation.targetEntityName,
    "targetTypeRef" to targetTypeRef,
    "targetPackageName" to relation.targetEntityPackageName,
    "relationType" to relation.relationType.name,
    "fetchType" to relation.fetchType.name,
    "joinColumn" to relation.joinColumn,
    "nullable" to relation.nullable,
    "cascadeAll" to relation.cascadeAll,
    "orphanRemoval" to relation.orphanRemoval,
    "joinColumnNullable" to relation.joinColumnNullable,
)
```

and gate imports exactly where the slice needs them:

```kotlin
val relationControlsRequireCascadeType = entityRelations.any { it.cascadeAll }

val jpaImports = buildList {
    if (relationTypes.isNotEmpty()) {
        add("jakarta.persistence.FetchType")
        add("jakarta.persistence.JoinColumn")
    }
    if (relationControlsRequireCascadeType) {
        add("jakarta.persistence.CascadeType")
    }
    if (AggregateRelationType.MANY_TO_ONE in relationTypes) {
        add("jakarta.persistence.ManyToOne")
    }
    if (AggregateRelationType.ONE_TO_ONE in relationTypes) {
        add("jakarta.persistence.OneToOne")
    }
    if (AggregateRelationType.ONE_TO_MANY in relationTypes) {
        add("jakarta.persistence.OneToMany")
    }
}
```

Do not add any new relation types, ownership flags, or `mappedBy`/`JoinTable` metadata in this task.

- [ ] **Step 4: Run the aggregate planner tests to verify they pass**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-generator-aggregate:test --tests "com.only4.cap4k.plugin.pipeline.generator.aggregate.AggregateArtifactPlannerTest"
```

Expected: PASS with enriched `relationFields` and `CascadeType` imported only when at least one relation actually needs collection cascade rendering.

- [ ] **Step 5: Commit**

```bash
git add cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateRelationPlanning.kt cap4k-plugin-pipeline-generator-aggregate/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateArtifactPlannerTest.kt
git commit -m "feat: expose bounded relation side controls"
```

## Task 3: Render Bounded Relation-Side JPA Controls in the Aggregate Entity Preset

**Files:**
- Modify: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/entity.kt.peb`
- Modify: `cap4k-plugin-pipeline-renderer-pebble/src/test/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PebbleArtifactRendererTest.kt`

- [ ] **Step 1: Write the failing renderer tests**

Add to `PebbleArtifactRendererTest.kt` a relation-side control case equivalent to:

```kotlin
@Test
fun `aggregate entity preset renders bounded relation side JPA controls`() {
    val overrideDir = Files.createTempDirectory("cap4k-override-empty-aggregate-relation-side-jpa")
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
                    "entityJpa" to mapOf(
                        "entityEnabled" to true,
                        "tableName" to "video_post",
                    ),
                    "hasConverterFields" to false,
                    "hasGeneratedValueFields" to false,
                    "hasVersionFields" to false,
                    "dynamicInsert" to false,
                    "dynamicUpdate" to false,
                    "softDeleteSql" to null,
                    "softDeleteWhereClause" to null,
                    "jpaImports" to listOf(
                        "jakarta.persistence.CascadeType",
                        "jakarta.persistence.FetchType",
                        "jakarta.persistence.JoinColumn",
                        "jakarta.persistence.ManyToOne",
                        "jakarta.persistence.OneToMany",
                        "jakarta.persistence.OneToOne",
                    ),
                    "imports" to listOf(
                        "com.acme.demo.domain.identity.user.UserProfile",
                        "com.acme.demo.domain.aggregates.video_post_item.VideoPostItem",
                    ),
                    "scalarFields" to listOf(
                        mapOf(
                            "name" to "id",
                            "type" to "Long",
                            "nullable" to false,
                            "columnName" to "id",
                            "isId" to true,
                            "converterTypeRef" to null,
                        ),
                    ),
                    "fields" to listOf(
                        mapOf("name" to "id", "type" to "Long", "nullable" to false),
                    ),
                    "relationFields" to listOf(
                        mapOf(
                            "name" to "author",
                            "targetType" to "UserProfile",
                            "targetTypeRef" to "UserProfile",
                            "targetPackageName" to "com.acme.demo.domain.identity.user",
                            "relationType" to "MANY_TO_ONE",
                            "fetchType" to "LAZY",
                            "joinColumn" to "author_id",
                            "nullable" to false,
                            "joinColumnNullable" to false,
                            "cascadeAll" to false,
                            "orphanRemoval" to false,
                        ),
                        mapOf(
                            "name" to "coverProfile",
                            "targetType" to "UserProfile",
                            "targetTypeRef" to "UserProfile",
                            "targetPackageName" to "com.acme.demo.domain.identity.user",
                            "relationType" to "ONE_TO_ONE",
                            "fetchType" to "EAGER",
                            "joinColumn" to "cover_profile_id",
                            "nullable" to true,
                            "joinColumnNullable" to true,
                            "cascadeAll" to false,
                            "orphanRemoval" to false,
                        ),
                        mapOf(
                            "name" to "items",
                            "targetType" to "VideoPostItem",
                            "targetTypeRef" to "VideoPostItem",
                            "targetPackageName" to "com.acme.demo.domain.aggregates.video_post_item",
                            "relationType" to "ONE_TO_MANY",
                            "fetchType" to "LAZY",
                            "joinColumn" to "video_post_id",
                            "nullable" to false,
                            "joinColumnNullable" to false,
                            "cascadeAll" to true,
                            "orphanRemoval" to true,
                        ),
                    ),
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
                conflictPolicy = ConflictPolicy.SKIP,
            )
        )
    )

    val content = rendered.single().content

    assertTrue(content.contains("import jakarta.persistence.CascadeType"))
    assertTrue(content.contains("@ManyToOne(fetch = FetchType.LAZY)"))
    assertTrue(content.contains("@JoinColumn(name = \"author_id\", nullable = false)"))
    assertTrue(content.contains("@OneToOne(fetch = FetchType.EAGER)"))
    assertTrue(content.contains("@JoinColumn(name = \"cover_profile_id\", nullable = true)"))
    assertTrue(content.contains("@OneToMany(fetch = FetchType.LAZY, cascade = [CascadeType.ALL], orphanRemoval = true)"))
    assertTrue(content.contains("@JoinColumn(name = \"video_post_id\", nullable = false)"))
    assertFalse(content.contains("mappedBy ="))
}
```

Also add a no-collection-control case equivalent to:

```kotlin
@Test
fun `aggregate entity preset omits cascade type import when no one to many control is present`() {
    val overrideDir = Files.createTempDirectory("cap4k-override-empty-aggregate-relation-side-jpa-no-cascade")
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
                    "entityJpa" to mapOf("entityEnabled" to true, "tableName" to "video_post"),
                    "hasConverterFields" to false,
                    "hasGeneratedValueFields" to false,
                    "hasVersionFields" to false,
                    "dynamicInsert" to false,
                    "dynamicUpdate" to false,
                    "softDeleteSql" to null,
                    "softDeleteWhereClause" to null,
                    "jpaImports" to listOf(
                        "jakarta.persistence.FetchType",
                        "jakarta.persistence.JoinColumn",
                        "jakarta.persistence.ManyToOne",
                        "jakarta.persistence.OneToOne",
                    ),
                    "imports" to listOf("com.acme.demo.domain.identity.user.UserProfile"),
                    "scalarFields" to listOf(
                        mapOf(
                            "name" to "id",
                            "type" to "Long",
                            "nullable" to false,
                            "columnName" to "id",
                            "isId" to true,
                            "converterTypeRef" to null,
                        ),
                    ),
                    "fields" to listOf(mapOf("name" to "id", "type" to "Long", "nullable" to false)),
                    "relationFields" to listOf(
                        mapOf(
                            "name" to "author",
                            "targetType" to "UserProfile",
                            "targetTypeRef" to "UserProfile",
                            "targetPackageName" to "com.acme.demo.domain.identity.user",
                            "relationType" to "MANY_TO_ONE",
                            "fetchType" to "LAZY",
                            "joinColumn" to "author_id",
                            "nullable" to false,
                            "joinColumnNullable" to false,
                            "cascadeAll" to false,
                            "orphanRemoval" to false,
                        ),
                        mapOf(
                            "name" to "coverProfile",
                            "targetType" to "UserProfile",
                            "targetTypeRef" to "UserProfile",
                            "targetPackageName" to "com.acme.demo.domain.identity.user",
                            "relationType" to "ONE_TO_ONE",
                            "fetchType" to "EAGER",
                            "joinColumn" to "cover_profile_id",
                            "nullable" to true,
                            "joinColumnNullable" to true,
                            "cascadeAll" to false,
                            "orphanRemoval" to false,
                        ),
                    ),
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
                conflictPolicy = ConflictPolicy.SKIP,
            )
        )
    )

    val content = rendered.single().content

    assertFalse(content.contains("import jakarta.persistence.CascadeType"))
}
```

- [ ] **Step 2: Run the renderer tests to verify they fail**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-renderer-pebble:test --tests "com.only4.cap4k.plugin.pipeline.renderer.pebble.PebbleArtifactRendererTest"
```

Expected: FAIL because `aggregate/entity.kt.peb` does not yet render the bounded relation-side control parameters.

- [ ] **Step 3: Update the aggregate entity preset to emit bounded control mechanically**

Update `entity.kt.peb` so direct relations emit explicit join-column nullability:

```pebble
{% if relation.relationType == "MANY_TO_ONE" %}
    @ManyToOne(fetch = FetchType.{{ relation.fetchType }})
    @JoinColumn(name = "{{ relation.joinColumn }}", nullable = {{ relation.joinColumnNullable }})
    {% if relation.nullable %}var {{ relation.name }}: {{ relation.targetTypeRef }}? = null{% else %}lateinit var {{ relation.name }}: {{ relation.targetTypeRef }}{% endif %}
{% elseif relation.relationType == "ONE_TO_ONE" %}
    @OneToOne(fetch = FetchType.{{ relation.fetchType }})
    @JoinColumn(name = "{{ relation.joinColumn }}", nullable = {{ relation.joinColumnNullable }})
    {% if relation.nullable %}var {{ relation.name }}: {{ relation.targetTypeRef }}? = null{% else %}lateinit var {{ relation.name }}: {{ relation.targetTypeRef }}{% endif %}
{% elseif relation.relationType == "ONE_TO_MANY" %}
    @OneToMany(fetch = FetchType.{{ relation.fetchType }}, cascade = [CascadeType.ALL], orphanRemoval = true)
    @JoinColumn(name = "{{ relation.joinColumn }}", nullable = {{ relation.joinColumnNullable }})
    var {{ relation.name }}: List<{{ relation.targetTypeRef }}> = emptyList()
{% endif %}
```

Keep the template bounded:

- no `mappedBy`
- no `@JoinTable`
- no `ManyToMany`
- no inverse read-only `insertable/updatable = false`

- [ ] **Step 4: Run the renderer tests to verify they pass**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-renderer-pebble:test --tests "com.only4.cap4k.plugin.pipeline.renderer.pebble.PebbleArtifactRendererTest"
```

Expected: PASS with bounded relation-side control rendered and no unsupported ownership annotations emitted.

- [ ] **Step 5: Commit**

```bash
git add cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/entity.kt.peb cap4k-plugin-pipeline-renderer-pebble/src/test/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PebbleArtifactRendererTest.kt
git commit -m "feat: render bounded relation side jpa control"
```

## Task 4: Reuse Existing Relation Fixtures for Functional and Compile Verification

**Files:**
- Modify: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginFunctionalTest.kt`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginCompileFunctionalTest.kt`

- [ ] **Step 1: Write the failing functional and compile assertions against the existing relation fixtures**

Extend `PipelinePluginFunctionalTest.kt` in the existing `cap4kGenerate emits representative aggregate relation artifacts` case with assertions equivalent to:

```kotlin
assertTrue(rootEntityContent.contains("import jakarta.persistence.CascadeType"))
assertTrue(rootEntityContent.contains("@ManyToOne(fetch = FetchType.LAZY)"))
assertTrue(rootEntityContent.contains("@JoinColumn(name = \"author_id\", nullable = false)"))
assertTrue(rootEntityContent.contains("@OneToOne(fetch = FetchType.EAGER)"))
assertTrue(rootEntityContent.contains("@JoinColumn(name = \"cover_profile_id\", nullable = true)"))
assertTrue(
    rootEntityContent.contains(
        "@OneToMany(fetch = FetchType.LAZY, cascade = [CascadeType.ALL], orphanRemoval = true)"
    )
)
assertTrue(rootEntityContent.contains("@JoinColumn(name = \"video_post_id\", nullable = false)"))
assertFalse(rootEntityContent.contains("mappedBy ="))
```

Extend `PipelinePluginCompileFunctionalTest.kt` in the existing `aggregate relation generation participates in domain compileKotlin` case with assertions equivalent to:

```kotlin
val generatedRootEntity = projectDir.resolve(
    "demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/VideoPost.kt"
).readText()
val generatedChildEntity = projectDir.resolve(
    "demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post_item/VideoPostItem.kt"
).readText()

assertTrue(generatedRootEntity.contains("import jakarta.persistence.CascadeType"))
assertTrue(
    generatedRootEntity.contains(
        "@OneToMany(fetch = FetchType.LAZY, cascade = [CascadeType.ALL], orphanRemoval = true)"
    )
)
assertTrue(generatedRootEntity.contains("@JoinColumn(name = \"author_id\", nullable = false)"))
assertTrue(generatedRootEntity.contains("@JoinColumn(name = \"cover_profile_id\", nullable = true)"))
assertTrue(generatedChildEntity.contains("@JoinColumn(name = \"video_post_id\", nullable = false)"))
assertFalse(generatedRootEntity.contains("@JoinTable"))
```

- [ ] **Step 2: Run the focused gradle functional tests to verify they fail**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-gradle:test --tests "com.only4.cap4k.plugin.pipeline.gradle.PipelinePluginFunctionalTest" --tests "com.only4.cap4k.plugin.pipeline.gradle.PipelinePluginCompileFunctionalTest"
```

Expected: FAIL because generated aggregate relation output does not yet include the bounded relation-side JPA control layer.

- [ ] **Step 3: Keep fixture scope bounded and update only test expectations if no fixture change is required**

Confirm that the existing fixtures already cover the slice:

- `aggregate-relation-sample/schema.sql`
  - `video_post.author_id` gives non-null `MANY_TO_ONE`
  - `video_post.cover_profile_id` gives nullable `ONE_TO_ONE`
  - `video_post_item` under `@Parent=video_post` gives bounded `ONE_TO_MANY`
- `aggregate-relation-compile-sample/schema.sql`
  - mirrors the same bounded cases for compile verification

Do not add new many-to-many or ownership-heavy fixtures in this task. If a test-only fixture adjustment is required, keep it bounded to the existing three relation forms.

- [ ] **Step 4: Run the focused gradle functional tests to verify they pass**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-gradle:test --tests "com.only4.cap4k.plugin.pipeline.gradle.PipelinePluginFunctionalTest" --tests "com.only4.cap4k.plugin.pipeline.gradle.PipelinePluginCompileFunctionalTest"
```

Expected: PASS with the current relation fixtures proving generation and compile closure for:

- `ONE_TO_MANY` cascade/orphan-removal/nullable=false
- `MANY_TO_ONE` join-column nullability
- `ONE_TO_ONE` join-column nullability

- [ ] **Step 5: Commit**

```bash
git add cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginFunctionalTest.kt cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginCompileFunctionalTest.kt
git commit -m "test: cover bounded relation side jpa control"
```

## Task 5: Run Focused Regression and Verify Scope Guards

**Files:**
- Modify only if a real regression is found while running verification

- [ ] **Step 1: Run the focused regression suite**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-core:test :cap4k-plugin-pipeline-generator-aggregate:test :cap4k-plugin-pipeline-renderer-pebble:test :cap4k-plugin-pipeline-gradle:test
```

Expected: PASS across canonical inference, aggregate planner, renderer, and functional/compile verification.

- [ ] **Step 2: Verify bounded output and absence of forbidden drift**

Inspect representative generated output and assert:

```text
present:
- @OneToMany(fetch = FetchType.LAZY, cascade = [CascadeType.ALL], orphanRemoval = true)
- @JoinColumn(name = "video_post_id", nullable = false)
- @JoinColumn(name = "author_id", nullable = false)
- @JoinColumn(name = "cover_profile_id", nullable = true)

absent:
- mappedBy =
- @JoinTable
- ManyToMany
- insertable = false
- updatable = false
```

Use a direct check such as:

```powershell
Select-String -Path .\cap4k-plugin-pipeline-gradle\src\test\resources\functional\aggregate-relation-compile-sample\demo-domain\src\main\kotlin\com\acme\demo\domain\aggregates\video_post\VideoPost.kt -Pattern "@OneToMany(fetch = FetchType.LAZY, cascade = [CascadeType.ALL], orphanRemoval = true)","@JoinColumn(name = \"author_id\", nullable = false)","@JoinColumn(name = \"cover_profile_id\", nullable = true)","mappedBy","JoinTable","ManyToMany","insertable = false","updatable = false"
```

Expected: bounded relation-side controls found, unsupported ownership features absent.

- [ ] **Step 3: Fix only real regressions if any are found**

If verification exposes a real issue, apply the smallest bounded fix. Examples of acceptable fixes:

```kotlin
// Example: only import CascadeType when at least one current relation uses cascade-all rendering
val relationControlsRequireCascadeType = entityRelations.any { it.cascadeAll }
```

```pebble
{# Example: keep one-to-many ownership bounded and mechanical #}
@OneToMany(fetch = FetchType.{{ relation.fetchType }}, cascade = [CascadeType.ALL], orphanRemoval = true)
```

Do not widen the slice into:

- `mappedBy`
- `@JoinTable`
- `ManyToMany`
- inverse read-only join-column control
- relation ownership redesign

- [ ] **Step 4: Re-run the focused regression if any fix was needed**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-core:test :cap4k-plugin-pipeline-generator-aggregate:test :cap4k-plugin-pipeline-renderer-pebble:test :cap4k-plugin-pipeline-gradle:test
```

Expected: PASS after the minimal bounded fix.

- [ ] **Step 5: Commit only if a verification fix was required**

```bash
git add cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModels.kt cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/AggregateRelationInference.kt cap4k-plugin-pipeline-core/src/test/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssemblerTest.kt cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateRelationPlanning.kt cap4k-plugin-pipeline-generator-aggregate/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateArtifactPlannerTest.kt cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/entity.kt.peb cap4k-plugin-pipeline-renderer-pebble/src/test/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PebbleArtifactRendererTest.kt cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginFunctionalTest.kt cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginCompileFunctionalTest.kt
git commit -m "fix: harden bounded relation side jpa control"
```

## Spec Coverage Check

- Bounded canonical relation control fields are implemented in Task 1.
- Relation-planning derivation and import gating are implemented in Task 2.
- Mechanical template emission for `cascade`, `orphanRemoval`, and join-column nullability is implemented in Task 3.
- Functional and compile verification on the existing relation fixtures is implemented in Task 4.
- Focused regression and explicit scope-guard verification are implemented in Task 5.

## Placeholder Scan

- No `TODO`, `TBD`, or deferred implementation placeholders remain.
- Every code-changing step includes concrete code or exact commands.
- Every verification step has an explicit expected outcome.

## Type Consistency Check

- Canonical relation control uses `cascadeAll`, `orphanRemoval`, and `joinColumnNullable` consistently across Tasks 1-5.
- Relation planner render items expose `joinColumnNullable`, `cascadeAll`, and `orphanRemoval` consistently across Tasks 2-4.
- Template and functional verification use the same bounded output contract:
  - `@OneToMany(..., cascade = [CascadeType.ALL], orphanRemoval = true)`
  - `@JoinColumn(name = "...", nullable = true/false)`

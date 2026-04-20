# Cap4k Aggregate Inverse Relation Read-Only Parity Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add bounded inverse/read-only parent navigation parity so parent-child aggregate generation can derive old `*ManyToOne`-style child navigation with `insertable = false` and `updatable = false` without reopening `mappedBy`, `@JoinTable`, or `ManyToMany`.

**Architecture:** Keep this slice on the existing aggregate relation line. Add a separate canonical slice for inverse/read-only relations, derive it only from already-known parent-child truth, merge it into aggregate relation planning with explicit `readOnly` and join-column mutability flags, and keep `aggregate/entity.kt.peb` mechanical. Reuse existing aggregate relation fixtures and mutate schema text in tests rather than adding a new source syntax or a new runtime DSL.

**Tech Stack:** Kotlin, JUnit 5, Gradle TestKit, Pebble preset rendering, existing aggregate db source and canonical relation pipeline

---

## File Structure

### New files

- Create: `cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/AggregateInverseRelationInference.kt`

### Existing files to modify

- Modify: `cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModels.kt`
- Modify: `cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssembler.kt`
- Modify: `cap4k-plugin-pipeline-core/src/test/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssemblerTest.kt`
- Modify: `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateRelationPlanning.kt`
- Modify: `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/EntityArtifactPlanner.kt`
- Modify: `cap4k-plugin-pipeline-generator-aggregate/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateArtifactPlannerTest.kt`
- Modify: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/entity.kt.peb`
- Modify: `cap4k-plugin-pipeline-renderer-pebble/src/test/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PebbleArtifactRendererTest.kt`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginFunctionalTest.kt`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginCompileFunctionalTest.kt`

### Existing fixtures to reuse

- Reuse and mutate inside tests: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-relation-sample/schema.sql`
- Reuse and mutate inside tests: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-relation-compile-sample/schema.sql`

### Responsibilities

- `PipelineModels.kt`
  - define a bounded aggregate-owned canonical slice for inverse/read-only relations and expose it on `CanonicalModel`

- `AggregateInverseRelationInference.kt`
  - derive inverse/read-only parent navigation from existing parent-child relation truth only

- `DefaultCanonicalAssembler.kt`
  - assemble the new canonical slice after entities and owner-side relations already exist

- `AggregateRelationPlanning.kt`
  - merge owner-side relations and inverse/read-only relations into one render contract without losing scalar FK fields

- `EntityArtifactPlanner.kt`
  - preserve scalar join columns for inverse/read-only relations while still excluding owner-side relation-backed scalar columns

- `aggregate/entity.kt.peb`
  - emit `insertable = false, updatable = false` mechanically when planner-provided relation flags require it

- functional and compile tests
  - prove the derived inverse relation works when the child anchor column stays scalar
  - prove explicit owner-side child relations still suppress duplicate inverse derivation

## Task 1: Add Bounded Canonical Inverse Relation Derivation

**Files:**
- Create: `cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/AggregateInverseRelationInference.kt`
- Modify: `cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModels.kt`
- Modify: `cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssembler.kt`
- Modify: `cap4k-plugin-pipeline-core/src/test/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssemblerTest.kt`

- [ ] **Step 1: Write the failing canonical tests**

Add to `DefaultCanonicalAssemblerTest.kt`:

```kotlin
@Test
fun `assembler derives inverse read only parent relation from parent child truth`() {
    val assembler = DefaultCanonicalAssembler()

    val result = assembler.assemble(
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
                            DbColumnSnapshot("video_post_id", "BIGINT", "Long", false),
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
    )

    val inverse = result.model.aggregateInverseRelations.single()

    assertEquals("VideoPostItem", inverse.ownerEntityName)
    assertEquals("videoPost", inverse.fieldName)
    assertEquals("VideoPost", inverse.targetEntityName)
    assertEquals(AggregateRelationType.MANY_TO_ONE, inverse.relationType)
    assertEquals("video_post_id", inverse.joinColumn)
    assertEquals(AggregateFetchType.LAZY, inverse.fetchType)
    assertEquals(false, inverse.nullable)
    assertEquals(false, inverse.insertable)
    assertEquals(false, inverse.updatable)
}

@Test
fun `assembler suppresses inverse read only relation when explicit owner side relation already exists`() {
    val assembler = DefaultCanonicalAssembler()

    val result = assembler.assemble(
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
                                explicitRelationType = "MANY_TO_ONE",
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
    )

    assertTrue(result.model.aggregateInverseRelations.isEmpty())
    assertEquals(
        1,
        result.model.aggregateRelations.count {
            it.ownerEntityName == "VideoPostItem" &&
                it.targetEntityName == "VideoPost" &&
                it.relationType == AggregateRelationType.MANY_TO_ONE
        }
    )
}
```

- [ ] **Step 2: Run the canonical assembler tests to verify they fail**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-core:test --tests "com.only4.cap4k.plugin.pipeline.core.DefaultCanonicalAssemblerTest"
```

Expected: FAIL because `CanonicalModel` and the core assembler do not yet expose `aggregateInverseRelations`.

- [ ] **Step 3: Implement the bounded inverse canonical slice**

Extend `PipelineModels.kt`:

```kotlin
data class AggregateInverseRelationModel(
    val ownerEntityName: String,
    val ownerEntityPackageName: String,
    val fieldName: String,
    val targetEntityName: String,
    val targetEntityPackageName: String,
    val relationType: AggregateRelationType,
    val joinColumn: String,
    val fetchType: AggregateFetchType,
    val nullable: Boolean = false,
    val insertable: Boolean = false,
    val updatable: Boolean = false,
)

data class CanonicalModel(
    val requests: List<RequestModel> = emptyList(),
    val validators: List<ValidatorModel> = emptyList(),
    val domainEvents: List<DomainEventModel> = emptyList(),
    val schemas: List<SchemaModel> = emptyList(),
    val entities: List<EntityModel> = emptyList(),
    val repositories: List<RepositoryModel> = emptyList(),
    val analysisGraph: AnalysisGraphModel? = null,
    val drawingBoard: DrawingBoardModel? = null,
    val apiPayloads: List<ApiPayloadModel> = emptyList(),
    val sharedEnums: List<SharedEnumDefinition> = emptyList(),
    val aggregateRelations: List<AggregateRelationModel> = emptyList(),
    val aggregateInverseRelations: List<AggregateInverseRelationModel> = emptyList(),
    val aggregateEntityJpa: List<AggregateEntityJpaModel> = emptyList(),
    val aggregatePersistenceFieldControls: List<AggregatePersistenceFieldControl> = emptyList(),
    val aggregatePersistenceProviderControls: List<AggregatePersistenceProviderControl> = emptyList(),
)
```

Create `AggregateInverseRelationInference.kt`:

```kotlin
internal object AggregateInverseRelationInference {
    fun infer(
        entities: List<EntityModel>,
        relations: List<AggregateRelationModel>,
    ): List<AggregateInverseRelationModel> {
        val entityByKey = entities.associateBy { it.packageName to it.name }
        val scalarFieldsByEntity = entities.associate { entity ->
            (entity.packageName to entity.name) to entity.fields.map { it.name }.toSet()
        }
        val ownerRelationsByEntity = relations.groupBy { it.ownerEntityPackageName to it.ownerEntityName }
        val collisions = mutableSetOf<String>()

        return relations
            .filter { it.relationType == AggregateRelationType.ONE_TO_MANY }
            .mapNotNull { relation ->
                val childKey = relation.targetEntityPackageName to relation.targetEntityName
                val childEntity = entityByKey[childKey] ?: return@mapNotNull null
                val hasOwnerSideRelation = ownerRelationsByEntity[childKey].orEmpty().any {
                    it.targetEntityName == relation.ownerEntityName &&
                        it.targetEntityPackageName == relation.ownerEntityPackageName &&
                        it.joinColumn == relation.joinColumn &&
                        it.relationType in setOf(AggregateRelationType.MANY_TO_ONE, AggregateRelationType.ONE_TO_ONE)
                }
                if (hasOwnerSideRelation) return@mapNotNull null

                val fieldName = relation.ownerEntityName.replaceFirstChar { it.lowercase() }
                require(fieldName !in scalarFieldsByEntity.getValue(childKey)) {
                    "aggregate inverse relation field collides with scalar field: ${childEntity.packageName}.${childEntity.name}.$fieldName"
                }
                require(collisions.add("${childEntity.packageName}.${childEntity.name}.$fieldName")) {
                    "aggregate inverse relation field collision: ${childEntity.packageName}.${childEntity.name}.$fieldName"
                }

                AggregateInverseRelationModel(
                    ownerEntityName = childEntity.name,
                    ownerEntityPackageName = childEntity.packageName,
                    fieldName = fieldName,
                    targetEntityName = relation.ownerEntityName,
                    targetEntityPackageName = relation.ownerEntityPackageName,
                    relationType = AggregateRelationType.MANY_TO_ONE,
                    joinColumn = relation.joinColumn,
                    fetchType = AggregateFetchType.LAZY,
                    nullable = false,
                    insertable = false,
                    updatable = false,
                )
            }
    }
}
```

Update `DefaultCanonicalAssembler.kt` to infer and surface the new slice:

```kotlin
val aggregateInverseRelations = AggregateInverseRelationInference.infer(
    entities = entities,
    relations = aggregateRelations,
)

return CanonicalAssemblyResult(
    model = CanonicalModel(
        requests = requests,
        validators = validators,
        domainEvents = domainEvents,
        schemas = aggregateModels.map { it.first },
        entities = entities,
        repositories = aggregateModels.map { it.third },
        analysisGraph = analysisGraph,
        drawingBoard = drawingBoard,
        apiPayloads = apiPayloads,
        sharedEnums = sharedEnums,
        aggregateRelations = aggregateRelations,
        aggregateInverseRelations = aggregateInverseRelations,
        aggregateEntityJpa = aggregateEntityJpa,
        aggregatePersistenceFieldControls = aggregatePersistenceFieldControls,
        aggregatePersistenceProviderControls = aggregatePersistenceProviderControls,
    ),
    diagnostics = diagnostics,
)
```

- [ ] **Step 4: Run the canonical assembler tests to verify they pass**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-core:test --tests "com.only4.cap4k.plugin.pipeline.core.DefaultCanonicalAssemblerTest"
```

Expected: PASS with derived inverse/read-only parent navigation present only when the child does not already own the same relation.

- [ ] **Step 5: Commit**

```bash
git add cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModels.kt cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/AggregateInverseRelationInference.kt cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssembler.kt cap4k-plugin-pipeline-core/src/test/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssemblerTest.kt
git commit -m "feat: derive aggregate inverse relations"
```

## Task 2: Merge Inverse Relations into Planner Output Without Dropping Scalar FK Fields

**Files:**
- Modify: `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateRelationPlanning.kt`
- Modify: `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/EntityArtifactPlanner.kt`
- Modify: `cap4k-plugin-pipeline-generator-aggregate/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateArtifactPlannerTest.kt`

- [ ] **Step 1: Write the failing planner tests**

Add to `AggregateArtifactPlannerTest.kt`:

```kotlin
@Test
fun `entity planner keeps scalar foreign key and adds inverse read only relation`() {
    val entity = EntityModel(
        name = "VideoPostItem",
        packageName = "com.acme.demo.domain.aggregates.video_post_item",
        tableName = "video_post_item",
        comment = "video post item",
        fields = listOf(
            FieldModel("id", "Long"),
            FieldModel("video_post_id", "Long"),
            FieldModel("label", "String"),
        ),
        idField = FieldModel("id", "Long"),
        aggregateRoot = false,
        valueObject = true,
        parentEntityName = "VideoPost",
    )

    val plan = AggregateArtifactPlanner().plan(
        aggregateConfig(),
        CanonicalModel(
            entities = listOf(entity),
            aggregateEntityJpa = listOf(defaultAggregateEntityJpa(entity)),
            aggregateInverseRelations = listOf(
                AggregateInverseRelationModel(
                    ownerEntityName = "VideoPostItem",
                    ownerEntityPackageName = "com.acme.demo.domain.aggregates.video_post_item",
                    fieldName = "videoPost",
                    targetEntityName = "VideoPost",
                    targetEntityPackageName = "com.acme.demo.domain.aggregates.video_post",
                    relationType = AggregateRelationType.MANY_TO_ONE,
                    joinColumn = "video_post_id",
                    fetchType = AggregateFetchType.LAZY,
                    nullable = false,
                    insertable = false,
                    updatable = false,
                )
            ),
        )
    )

    val entityItem = plan.single { it.outputPath.endsWith("/VideoPostItem.kt") }
    @Suppress("UNCHECKED_CAST")
    val scalarFields = entityItem.context["scalarFields"] as List<Map<String, Any?>>
    @Suppress("UNCHECKED_CAST")
    val relationFields = entityItem.context["relationFields"] as List<Map<String, Any?>>
    @Suppress("UNCHECKED_CAST")
    val jpaImports = entityItem.context["jpaImports"] as List<String>

    assertEquals(listOf("id", "video_post_id", "label"), scalarFields.map { it["name"] })
    assertEquals(listOf("videoPost"), relationFields.map { it["name"] })
    assertEquals(true, relationFields.single()["readOnly"])
    assertEquals(false, relationFields.single()["insertable"])
    assertEquals(false, relationFields.single()["updatable"])
    assertEquals("video_post_id", relationFields.single()["joinColumn"])
    assertTrue(jpaImports.contains("jakarta.persistence.ManyToOne"))
    assertTrue(jpaImports.contains("jakarta.persistence.JoinColumn"))
}

@Test
fun `entity planner still drops scalar field for owner side relation join columns`() {
    val entity = EntityModel(
        name = "VideoPost",
        packageName = "com.acme.demo.domain.aggregates.video_post",
        tableName = "video_post",
        comment = "video post",
        fields = listOf(
            FieldModel("id", "Long"),
            FieldModel("author_id", "Long"),
            FieldModel("title", "String"),
        ),
        idField = FieldModel("id", "Long"),
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
                    fieldName = "author",
                    targetEntityName = "UserProfile",
                    targetEntityPackageName = "com.acme.demo.domain.identity.user",
                    relationType = AggregateRelationType.MANY_TO_ONE,
                    joinColumn = "author_id",
                    fetchType = AggregateFetchType.LAZY,
                    nullable = false,
                )
            ),
        )
    )

    val entityItem = plan.single { it.outputPath.endsWith("/VideoPost.kt") }
    @Suppress("UNCHECKED_CAST")
    val scalarFields = entityItem.context["scalarFields"] as List<Map<String, Any?>>

    assertEquals(listOf("id", "title"), scalarFields.map { it["name"] })
}
```

- [ ] **Step 2: Run the planner tests to verify they fail**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-generator-aggregate:test --tests "com.only4.cap4k.plugin.pipeline.generator.aggregate.AggregateArtifactPlannerTest"
```

Expected: FAIL because relation planning does not yet accept inverse relations and `EntityArtifactPlanner` currently removes any scalar field whose column matches a relation join column.

- [ ] **Step 3: Implement bounded planner integration**

Update `AggregateRelationPlanning.kt` so it accepts both owner-side and inverse relations:

```kotlin
internal object AggregateRelationPlanning {
    fun planFor(
        entity: EntityModel,
        relations: List<AggregateRelationModel>,
        inverseRelations: List<AggregateInverseRelationModel>,
    ): AggregateRelationRenderPlan {
        val entityRelations = relations
            .filter { it.ownerEntityName == entity.name && it.ownerEntityPackageName == entity.packageName }
        val entityInverseRelations = inverseRelations
            .filter { it.ownerEntityName == entity.name && it.ownerEntityPackageName == entity.packageName }
        val targetPackagesByType = (entityRelations.map {
            Triple(it.targetEntityName, it.targetEntityPackageName, false)
        } + entityInverseRelations.map {
            Triple(it.targetEntityName, it.targetEntityPackageName, true)
        })
            .groupBy { it.first }
            .mapValues { (_, triples) -> triples.map { it.second }.distinct() }

        val relationFields = buildList {
            addAll(entityRelations.map { relation ->
                val targetTypeRef = when {
                    relation.targetEntityPackageName == entity.packageName -> relation.targetEntityName
                    targetPackagesByType.getValue(relation.targetEntityName).size == 1 -> relation.targetEntityName
                    else -> "${relation.targetEntityPackageName}.${relation.targetEntityName}"
                }
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
                    "readOnly" to false,
                    "insertable" to null,
                    "updatable" to null,
                )
            })
            addAll(entityInverseRelations.map { relation ->
                val targetTypeRef = when {
                    relation.targetEntityPackageName == entity.packageName -> relation.targetEntityName
                    targetPackagesByType.getValue(relation.targetEntityName).size == 1 -> relation.targetEntityName
                    else -> "${relation.targetEntityPackageName}.${relation.targetEntityName}"
                }
                mapOf(
                    "name" to relation.fieldName,
                    "targetType" to relation.targetEntityName,
                    "targetTypeRef" to targetTypeRef,
                    "targetPackageName" to relation.targetEntityPackageName,
                    "relationType" to relation.relationType.name,
                    "fetchType" to relation.fetchType.name,
                    "joinColumn" to relation.joinColumn,
                    "nullable" to relation.nullable,
                    "cascadeAll" to false,
                    "orphanRemoval" to false,
                    "joinColumnNullable" to relation.nullable,
                    "readOnly" to true,
                    "insertable" to relation.insertable,
                    "updatable" to relation.updatable,
                )
            })
        }
        val imports = (entityRelations.map {
            Triple(it.targetEntityName, it.targetEntityPackageName, false)
        } + entityInverseRelations.map {
            Triple(it.targetEntityName, it.targetEntityPackageName, true)
        })
            .mapNotNull { (targetType, targetPackageName, _) ->
                val targetPackages = targetPackagesByType.getValue(targetType)
                if (targetPackageName != entity.packageName && targetPackages.size == 1) {
                    "$targetPackageName.$targetType"
                } else {
                    null
                }
            }
            .distinct()
        val relationTypes = (entityRelations.map { it.relationType } + entityInverseRelations.map { it.relationType }).toSet()
        val hasCascadeAll = entityRelations.any { it.cascadeAll }
        val jpaImports = buildList {
            if (relationTypes.isNotEmpty()) {
                add("jakarta.persistence.FetchType")
                add("jakarta.persistence.JoinColumn")
                if (hasCascadeAll) {
                    add("jakarta.persistence.CascadeType")
                }
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

        return AggregateRelationRenderPlan(
            relationFields = relationFields,
            imports = imports,
            jpaImports = jpaImports,
        )
    }
}
```

Update `EntityArtifactPlanner.kt` so inverse/read-only relation join columns do not suppress scalar fields:

```kotlin
val relationPlan = AggregateRelationPlanning.planFor(
    entity = entity,
    relations = model.aggregateRelations,
    inverseRelations = model.aggregateInverseRelations,
)

val relationJoinColumns = relationPlan.relationFields
    .filter {
        when (it["relationType"]) {
            AggregateRelationType.MANY_TO_ONE.name,
            AggregateRelationType.ONE_TO_ONE.name,
            -> it["readOnly"] != true
            else -> false
        }
    }
    .mapNotNull { it["joinColumn"] as? String }
    .toSet()
```

- [ ] **Step 4: Run the planner tests to verify they pass**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-generator-aggregate:test --tests "com.only4.cap4k.plugin.pipeline.generator.aggregate.AggregateArtifactPlannerTest"
```

Expected: PASS with owner-side join-column filtering unchanged and inverse/read-only relations preserved as extra relation fields.

- [ ] **Step 5: Commit**

```bash
git add cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateRelationPlanning.kt cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/EntityArtifactPlanner.kt cap4k-plugin-pipeline-generator-aggregate/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateArtifactPlannerTest.kt
git commit -m "feat: plan inverse read only relations"
```

## Task 3: Render and Verify Inverse Read-Only Relations End-to-End

**Files:**
- Modify: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/entity.kt.peb`
- Modify: `cap4k-plugin-pipeline-renderer-pebble/src/test/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PebbleArtifactRendererTest.kt`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginFunctionalTest.kt`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginCompileFunctionalTest.kt`

- [ ] **Step 1: Write the failing renderer, functional, and compile tests**

Add to `PebbleArtifactRendererTest.kt`:

```kotlin
@Test
fun `aggregate entity template renders inverse read only many to one relation`() {
    val overrideDir = Files.createTempDirectory("cap4k-override-empty-aggregate-inverse-relation")
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
                outputPath = "demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post_item/VideoPostItem.kt",
                context = mapOf(
                    "packageName" to "com.acme.demo.domain.aggregates.video_post_item",
                    "typeName" to "VideoPostItem",
                    "comment" to "video post item",
                    "entityJpa" to mapOf("entityEnabled" to true, "tableName" to "video_post_item"),
                    "hasConverterFields" to false,
                    "hasGeneratedValueFields" to false,
                    "hasVersionFields" to false,
                    "dynamicInsert" to false,
                    "dynamicUpdate" to false,
                    "softDeleteSql" to null,
                    "softDeleteWhereClause" to null,
                    "scalarFields" to listOf(
                        mapOf("name" to "id", "type" to "Long", "nullable" to false, "columnName" to "id", "isId" to true),
                        mapOf("name" to "video_post_id", "type" to "Long", "nullable" to false, "columnName" to "video_post_id"),
                    ),
                    "fields" to listOf(
                        mapOf("name" to "id", "type" to "Long"),
                        mapOf("name" to "video_post_id", "type" to "Long"),
                    ),
                    "relationFields" to listOf(
                        mapOf(
                            "name" to "videoPost",
                            "targetType" to "VideoPost",
                            "targetTypeRef" to "com.acme.demo.domain.aggregates.video_post.VideoPost",
                            "targetPackageName" to "com.acme.demo.domain.aggregates.video_post",
                            "relationType" to "MANY_TO_ONE",
                            "fetchType" to "LAZY",
                            "joinColumn" to "video_post_id",
                            "nullable" to false,
                            "readOnly" to true,
                            "insertable" to false,
                            "updatable" to false,
                        )
                    ),
                    "imports" to listOf("com.acme.demo.domain.aggregates.video_post.VideoPost"),
                    "jpaImports" to listOf("jakarta.persistence.FetchType", "jakarta.persistence.JoinColumn", "jakarta.persistence.ManyToOne"),
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
            templates = TemplateConfig(preset = "ddd-default", overrideDirs = listOf(overrideDir.toString()), conflictPolicy = ConflictPolicy.SKIP)
        )
    )

    val content = rendered.single().content
    assertTrue(content.contains("@JoinColumn(name = \"video_post_id\", nullable = false, insertable = false, updatable = false)"))
    assertFalse(content.contains("mappedBy ="))
    assertFalse(content.contains("@JoinTable"))
    assertFalse(content.contains("ManyToMany"))
}
```

Add to `PipelinePluginFunctionalTest.kt`:

```kotlin
@Test
fun `cap4kGenerate derives inverse read only parent relation when child anchor stays scalar`() {
    val projectDir = Files.createTempDirectory("pipeline-functional-aggregate-inverse-relation")
    copyFixture(projectDir, "aggregate-relation-sample")
    val schemaFile = projectDir.resolve("schema.sql")
    schemaFile.writeText(
        schemaFile.readText().replace(
            "video_post_id bigint not null comment '@Reference=video_post;@Lazy=true;',",
            "video_post_id bigint not null,"
        )
    )

    val result = GradleRunner.create()
        .withProjectDir(projectDir.toFile())
        .withPluginClasspath()
        .withArguments("cap4kGenerate")
        .build()

    val childEntity = projectDir.resolve(
        "demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post_item/VideoPostItem.kt"
    ).readText()

    assertTrue(result.output.contains("BUILD SUCCESSFUL"))
    assertTrue(childEntity.contains("val video_post_id: Long"))
    assertTrue(childEntity.contains("@JoinColumn(name = \"video_post_id\", nullable = false, insertable = false, updatable = false)"))
    assertTrue(childEntity.contains("lateinit var videoPost: VideoPost"))
    assertFalse(childEntity.contains("mappedBy ="))
    assertFalse(childEntity.contains("@JoinTable"))
}
```

Add to `PipelinePluginCompileFunctionalTest.kt`:

```kotlin
@Test
fun `aggregate inverse read only relation participates in domain compileKotlin`() {
    val projectDir = Files.createTempDirectory("pipeline-functional-aggregate-inverse-relation-compile")
    FunctionalFixtureSupport.copyCompileFixture(projectDir, "aggregate-relation-compile-sample")
    val schemaFile = projectDir.resolve("schema.sql")
    schemaFile.writeText(
        schemaFile.readText().replace(
            "video_post_id bigint not null comment '@Reference=video_post;@Lazy=true;',",
            "video_post_id bigint not null,"
        )
    )

    val beforeGenerateCompileResult = FunctionalFixtureSupport
        .runner(projectDir, ":demo-domain:compileKotlin")
        .buildAndFail()
    assertEquals(TaskOutcome.FAILED, beforeGenerateCompileResult.task(":demo-domain:compileKotlin")?.outcome)

    val (generateResult, compileResult) = FunctionalFixtureSupport.generateThenCompile(
        projectDir,
        ":demo-domain:compileKotlin"
    )
    val generatedChildEntity = projectDir.resolve(
        "demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post_item/VideoPostItem.kt"
    ).readText()

    assertTrue(generatedChildEntity.contains("val video_post_id: Long"))
    assertTrue(generatedChildEntity.contains("@JoinColumn(name = \"video_post_id\", nullable = false, insertable = false, updatable = false)"))
    assertTrue(generateResult.output.contains("BUILD SUCCESSFUL"))
    assertTrue(compileResult.output.contains("BUILD SUCCESSFUL"))
}
```

- [ ] **Step 2: Run the renderer and Gradle functional tests to verify they fail**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-renderer-pebble:test --tests "com.only4.cap4k.plugin.pipeline.renderer.pebble.PebbleArtifactRendererTest" :cap4k-plugin-pipeline-gradle:test --tests "com.only4.cap4k.plugin.pipeline.gradle.PipelinePluginFunctionalTest" --tests "com.only4.cap4k.plugin.pipeline.gradle.PipelinePluginCompileFunctionalTest"
```

Expected: FAIL because the entity template still renders inverse/read-only `MANY_TO_ONE` as an ordinary relation and therefore omits `insertable = false, updatable = false`.

- [ ] **Step 3: Implement mechanical renderer support**

Update the `MANY_TO_ONE` branch in `aggregate/entity.kt.peb`:

```pebble
{% if relation.relationType == "MANY_TO_ONE" %}
    @ManyToOne(fetch = FetchType.{{ relation.fetchType }})
    @JoinColumn(
        name = "{{ relation.joinColumn }}",
        nullable = {% if relation.joinColumnNullable is not null %}{{ relation.joinColumnNullable }}{% elseif relation.nullable is not null %}{{ relation.nullable }}{% else %}false{% endif %}{% if relation.insertable is not null %},
        insertable = {{ relation.insertable }}{% endif %}{% if relation.updatable is not null %},
        updatable = {{ relation.updatable }}{% endif %}
    )
    {% if relation.nullable %}var {{ relation.name }}: {{ relation.targetTypeRef }}? = null{% else %}lateinit var {{ relation.name }}: {{ relation.targetTypeRef }}{% endif %}
{% endif %}
```

Keep the rest of the template bounded:

- no `mappedBy`
- no `@JoinTable`
- no `ManyToMany`

Also update the existing aggregate relation compile test in `PipelinePluginCompileFunctionalTest.kt` so the explicit-owner-side fixture remains protected:

```kotlin
assertFalse(generatedChildEntity.contains("insertable = false"))
assertFalse(generatedChildEntity.contains("updatable = false"))
```

- [ ] **Step 4: Run renderer, functional, compile, and focused regression suites**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-renderer-pebble:test --tests "com.only4.cap4k.plugin.pipeline.renderer.pebble.PebbleArtifactRendererTest" :cap4k-plugin-pipeline-gradle:test --tests "com.only4.cap4k.plugin.pipeline.gradle.PipelinePluginFunctionalTest" --tests "com.only4.cap4k.plugin.pipeline.gradle.PipelinePluginCompileFunctionalTest" :cap4k-plugin-pipeline-core:test :cap4k-plugin-pipeline-generator-aggregate:test
```

Expected: PASS, with:

- canonical derivation green
- planner tests green
- renderer tests green
- generated child entity showing both scalar FK field and inverse/read-only navigation
- explicit owner-side relation fixture still not rendering `insertable = false, updatable = false`

- [ ] **Step 5: Commit**

```bash
git add cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/entity.kt.peb cap4k-plugin-pipeline-renderer-pebble/src/test/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PebbleArtifactRendererTest.kt cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginFunctionalTest.kt cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginCompileFunctionalTest.kt
git commit -m "feat: render inverse read only relations"
```

## Plan Self-Review

- Spec coverage:
  - bounded inverse/read-only canonical slice: Task 1
  - planner-owned render contract and scalar-FK coexistence: Task 2
  - renderer, functional, and compile closure: Task 3
- Placeholder scan:
  - no unfinished markers or deferred implementation language remain in task steps
- Type consistency:
  - the plan uses one bounded canonical name throughout: `AggregateInverseRelationModel`
  - planner and template both use the same render keys: `readOnly`, `insertable`, `updatable`
  - test commands target current module and test class names in this repository

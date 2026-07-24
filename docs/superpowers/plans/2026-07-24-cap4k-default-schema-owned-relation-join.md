# cap4k Default Schema Owned Relation Join Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Restore generated default schema query APIs for aggregate-owned child relations without changing repository, Unit of Work, identity, or entity mutation contracts.

**Architecture:** Add two small JPA Criteria relation field helpers in `ddd-domain-repo-jpa`, extend aggregate schema planning to emit `relationJoins` from canonical owned relation metadata, and render schema relation fields/join methods from that model. Join caching stays query-local inside each generated schema instance, while public schema names use domain relation names and internal Criteria paths use normalized persistence backing names.

**Tech Stack:** Kotlin 2.2.20, Gradle, Pebble templates, Spring Data JPA `Specification`, Jakarta Persistence Criteria API, JUnit 5, MockK.

## Global Constraints

- Source spec copied into this worktree: `docs/superpowers/specs/2026-07-23-cap4k-default-schema-owned-relation-join-design.md`.
- This phase is Phase 3.625 and must remain separate from Phase 3.5 entity/`OwnedEntityList` work.
- Generate schema join APIs only for `model.aggregateRelations` where owner entity/package match the schema entity, `owned == true`, `relationType == ONE_TO_MANY`, `persistenceShape == ONE_TO_MANY_JOIN_COLUMN`, `ownedCardinality` is `MANY` or `ONE`, and a target schema exists unambiguously.
- Do not generate joins for ordinary JPA relations, inverse relations, reference ID fields, `@RefAggregate`, `@RefId`, weak reference IDs, arbitrary string paths, or cross-aggregate joins.
- Public schema API names must use `domainName`, for example `items`, `file`, `joinItems`, `joinFile`, and `relations.file`.
- Internal Criteria path strings must use normalized `persistencePathName`, for example `_items` and `_files`; public names must not expose backing names.
- Owned-many relation fields support only `isEmpty()` and `isNotEmpty()` through `RelationCollectionField<E>`.
- Owned-one relation fields support only `isNull()` and `isNotNull()` through `RelationOptionalField<E>`, backed by collection emptiness.
- `joinXxx()` defaults to `JoinType.INNER`; `joinXxx(joinType: JoinType)` accepts the cap4k schema `JoinType` enum.
- Repeated joins on the same schema instance and relation path with the same join type reuse the same join/schema wrapper; conflicting join types fail fast.
- Preserve current repository interfaces, repository adapter inheritance, Spring Data JPA `Specification` flow, Unit of Work, Strong ID generation, and PR #132 lifecycle traversal helpers.
- Add distinct only as an explicit aggregate-root schema overload: `predicate(distinct: Boolean, builder: PredicateBuilder<SRoot>)`.

---

## File Map

- Create: `ddd-domain-repo-jpa/src/main/kotlin/com/only4/cap4k/ddd/domain/repo/schema/RelationCollectionField.kt`
  - Owns collection-relation existence predicates for generated owned-many schema fields.
- Create: `ddd-domain-repo-jpa/src/main/kotlin/com/only4/cap4k/ddd/domain/repo/schema/RelationOptionalField.kt`
  - Owns nullable-owned-one existence predicates, implemented through backing collection emptiness.
- Create: `ddd-domain-repo-jpa/src/test/kotlin/com/only4/cap4k/ddd/domain/repo/schema/RelationFieldTest.kt`
  - Focused runtime helper tests using MockK Criteria API mocks.
- Modify: `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/SchemaArtifactPlanner.kt`
  - Emits `relationJoins` render model entries by reusing `AggregateRelationPlanning.planFor` with inverse relations omitted.
- Modify: `cap4k-plugin-pipeline-generator-aggregate/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateArtifactPlannerTest.kt`
  - Adds planner tests for owned-many, owned-one, unsupported relations, and missing target schema diagnostics.
- Modify: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/schema.kt.peb`
  - Renders relation fields, relation constants, target-schema join methods, query-local join cache, `From<*, E>` schema roots, and distinct-friendly aggregate predicates.
- Modify: `cap4k-plugin-pipeline-renderer-pebble/src/test/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PebbleArtifactRendererTest.kt`
  - Adds renderer assertions for generated relation fields, imports, constants, join methods, distinct overload, and backing-name privacy.
- Modify: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginCompileFunctionalTest.kt`
  - Adds compile functional coverage for owned-many join, owned-one join, chained owned join, and public API names.

## Task 1: Runtime Relation Field Helpers

**Files:**
- Create: `ddd-domain-repo-jpa/src/test/kotlin/com/only4/cap4k/ddd/domain/repo/schema/RelationFieldTest.kt`
- Create: `ddd-domain-repo-jpa/src/main/kotlin/com/only4/cap4k/ddd/domain/repo/schema/RelationCollectionField.kt`
- Create: `ddd-domain-repo-jpa/src/main/kotlin/com/only4/cap4k/ddd/domain/repo/schema/RelationOptionalField.kt`

**Interfaces:**
- Consumes: Jakarta `Expression<Collection<E>>`, `CriteriaBuilder`, and `Predicate`.
- Produces: `RelationCollectionField<E>.isEmpty(): Predicate`, `RelationCollectionField<E>.isNotEmpty(): Predicate`, `RelationOptionalField<E>.isNull(): Predicate`, `RelationOptionalField<E>.isNotNull(): Predicate`.

- [ ] **Step 1: Write the failing runtime helper test**

Create `ddd-domain-repo-jpa/src/test/kotlin/com/only4/cap4k/ddd/domain/repo/schema/RelationFieldTest.kt` with this full content:

```kotlin
package com.only4.cap4k.ddd.domain.repo.schema

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jakarta.persistence.criteria.CriteriaBuilder
import jakarta.persistence.criteria.Expression
import jakarta.persistence.criteria.Predicate
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class RelationFieldTest {

    @Test
    fun `collection relation field delegates emptiness predicates to criteria builder`() {
        val path = mockk<Expression<Collection<OwnedChild>>>()
        val criteriaBuilder = mockk<CriteriaBuilder>()
        val emptyPredicate = mockk<Predicate>()
        val notEmptyPredicate = mockk<Predicate>()
        every { criteriaBuilder.isEmpty(path) } returns emptyPredicate
        every { criteriaBuilder.isNotEmpty(path) } returns notEmptyPredicate

        val field = RelationCollectionField<OwnedChild>(path, criteriaBuilder)

        assertSame(emptyPredicate, field.isEmpty())
        assertSame(notEmptyPredicate, field.isNotEmpty())
        verify(exactly = 1) { criteriaBuilder.isEmpty(path) }
        verify(exactly = 1) { criteriaBuilder.isNotEmpty(path) }
    }

    @Test
    fun `optional relation field uses backing collection emptiness for nullability`() {
        val path = mockk<Expression<Collection<OwnedChild>>>()
        val criteriaBuilder = mockk<CriteriaBuilder>()
        val nullPredicate = mockk<Predicate>()
        val notNullPredicate = mockk<Predicate>()
        every { criteriaBuilder.isEmpty(path) } returns nullPredicate
        every { criteriaBuilder.isNotEmpty(path) } returns notNullPredicate

        val field = RelationOptionalField<OwnedChild>(path, criteriaBuilder)

        assertSame(nullPredicate, field.isNull())
        assertSame(notNullPredicate, field.isNotNull())
        verify(exactly = 1) { criteriaBuilder.isEmpty(path) }
        verify(exactly = 1) { criteriaBuilder.isNotEmpty(path) }
    }

    private class OwnedChild
}
```

- [ ] **Step 2: Run the focused helper test and verify it fails**

Run:

```powershell
./gradlew :ddd-domain-repo-jpa:test --tests "com.only4.cap4k.ddd.domain.repo.schema.RelationFieldTest"
```

Expected: FAIL because `RelationCollectionField` and `RelationOptionalField` do not exist.

- [ ] **Step 3: Add `RelationCollectionField`**

Create `ddd-domain-repo-jpa/src/main/kotlin/com/only4/cap4k/ddd/domain/repo/schema/RelationCollectionField.kt` with this full content:

```kotlin
package com.only4.cap4k.ddd.domain.repo.schema

import jakarta.persistence.criteria.CriteriaBuilder
import jakarta.persistence.criteria.Expression
import jakarta.persistence.criteria.Predicate

class RelationCollectionField<E>(
    private val path: Expression<Collection<E>>,
    private val criteriaBuilder: CriteriaBuilder,
) {
    fun isEmpty(): Predicate = criteriaBuilder.isEmpty(path)

    fun isNotEmpty(): Predicate = criteriaBuilder.isNotEmpty(path)
}
```

- [ ] **Step 4: Add `RelationOptionalField`**

Create `ddd-domain-repo-jpa/src/main/kotlin/com/only4/cap4k/ddd/domain/repo/schema/RelationOptionalField.kt` with this full content:

```kotlin
package com.only4.cap4k.ddd.domain.repo.schema

import jakarta.persistence.criteria.CriteriaBuilder
import jakarta.persistence.criteria.Expression
import jakarta.persistence.criteria.Predicate

class RelationOptionalField<E>(
    private val backingCollectionPath: Expression<Collection<E>>,
    private val criteriaBuilder: CriteriaBuilder,
) {
    fun isNull(): Predicate = criteriaBuilder.isEmpty(backingCollectionPath)

    fun isNotNull(): Predicate = criteriaBuilder.isNotEmpty(backingCollectionPath)
}
```

- [ ] **Step 5: Run the focused helper test and verify it passes**

Run:

```powershell
./gradlew :ddd-domain-repo-jpa:test --tests "com.only4.cap4k.ddd.domain.repo.schema.RelationFieldTest"
```

Expected: PASS.

- [ ] **Step 6: Commit runtime helpers**

Run:

```powershell
git add ddd-domain-repo-jpa/src/main/kotlin/com/only4/cap4k/ddd/domain/repo/schema/RelationCollectionField.kt ddd-domain-repo-jpa/src/main/kotlin/com/only4/cap4k/ddd/domain/repo/schema/RelationOptionalField.kt ddd-domain-repo-jpa/src/test/kotlin/com/only4/cap4k/ddd/domain/repo/schema/RelationFieldTest.kt
git commit -m "feat: add schema relation field helpers"
```
## Task 2: Schema Planner Relation Join Render Model

**Files:**
- Modify: `cap4k-plugin-pipeline-generator-aggregate/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateArtifactPlannerTest.kt`
- Modify: `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/SchemaArtifactPlanner.kt`

**Interfaces:**
- Consumes: `CanonicalModel.schemas`, `CanonicalModel.entities`, `CanonicalModel.aggregateRelations`, and normalized owner-side relation facts from `AggregateRelationPlanning.planFor(entity, relations, emptyList())`.
- Produces: schema template context key `relationJoins: List<Map<String, Any?>>` where each map exposes `domainName`, `persistencePathName`, `methodName`, `relationKind`, `targetEntityName`, `targetEntityTypeFqn`, `targetSchemaName`, `targetSchemaFqn`, `relationFieldType`, `nullable`, `ownedCardinality`, and `persistenceShape`.

- [ ] **Step 1: Add planner tests for owned relation joins**

Append these tests inside `AggregateArtifactPlannerTest` before the existing `schema planner fails fast when schema entity is ambiguous` test:

```kotlin
    @Test
    fun `schema planner emits relation joins for owned many and owned one relations`() {
        val rootEntity = EntityModel(
            name = "VideoPost",
            packageName = "com.acme.demo.domain.aggregates.video_post",
            tableName = "video_post",
            comment = "video post",
            fields = listOf(FieldModel("id", "Long", columnName = "id")),
            idField = FieldModel("id", "Long", columnName = "id"),
            aggregateRoot = true,
        )
        val itemEntity = EntityModel(
            name = "VideoPostItem",
            packageName = "com.acme.demo.domain.aggregates.video_post",
            tableName = "video_post_item",
            comment = "video post item",
            fields = listOf(FieldModel("id", "Long", columnName = "id")),
            idField = FieldModel("id", "Long", columnName = "id"),
            aggregateRoot = false,
            parentEntityName = "VideoPost",
        )
        val fileEntity = EntityModel(
            name = "VideoPostFile",
            packageName = "com.acme.demo.domain.aggregates.video_post",
            tableName = "video_post_file",
            comment = "video post file",
            fields = listOf(FieldModel("id", "Long", columnName = "id")),
            idField = FieldModel("id", "Long", columnName = "id"),
            aggregateRoot = false,
            parentEntityName = "VideoPost",
        )
        val plan = AggregateArtifactPlanner().plan(
            aggregateConfig(),
            CanonicalModel(
                entities = listOf(rootEntity, itemEntity, fileEntity),
                schemas = listOf(
                    SchemaModel(
                        name = "SVideoPost",
                        packageName = "com.acme.demo.domain._share.meta.video_post",
                        entityName = "VideoPost",
                        comment = "video post schema",
                        fields = rootEntity.fields,
                    ),
                    SchemaModel(
                        name = "SVideoPostItem",
                        packageName = "com.acme.demo.domain._share.meta.video_post",
                        entityName = "VideoPostItem",
                        comment = "video post item schema",
                        fields = itemEntity.fields,
                    ),
                    SchemaModel(
                        name = "SVideoPostFile",
                        packageName = "com.acme.demo.domain._share.meta.video_post",
                        entityName = "VideoPostFile",
                        comment = "video post file schema",
                        fields = fileEntity.fields,
                    ),
                ),
                aggregateEntityJpa = listOf(
                    defaultAggregateEntityJpa(rootEntity),
                    defaultAggregateEntityJpa(itemEntity),
                    defaultAggregateEntityJpa(fileEntity),
                ),
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
                    ),
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
                        owned = true,
                        parentRefColumn = "video_post_id",
                        ownedCardinality = OwnedRelationCardinality.ONE,
                        persistenceShape = OwnedRelationPersistenceShape.ONE_TO_MANY_JOIN_COLUMN,
                        backingCollectionName = "files",
                        singleAccessorName = "file",
                    ),
                ),
            )
        )

        val schema = plan.single {
            it.templateId == "aggregate/schema.kt.peb" && it.context["typeName"] == "SVideoPost"
        }
        @Suppress("UNCHECKED_CAST")
        val relationJoins = schema.context.getValue("relationJoins") as List<Map<String, Any?>>
        val items = relationJoins.single { it["domainName"] == "items" }
        val file = relationJoins.single { it["domainName"] == "file" }

        assertEquals(listOf("items", "file"), relationJoins.map { it["domainName"] })
        assertAll(
            {
                assertEquals("_items", items["persistencePathName"])
                assertEquals("joinItems", items["methodName"])
                assertEquals("OWNED_MANY", items["relationKind"])
                assertEquals("VideoPostItem", items["targetEntityName"])
                assertEquals("com.acme.demo.domain.aggregates.video_post.VideoPostItem", items["targetEntityTypeFqn"])
                assertEquals("SVideoPostItem", items["targetSchemaName"])
                assertEquals("com.acme.demo.domain._share.meta.video_post.SVideoPostItem", items["targetSchemaFqn"])
                assertEquals("RelationCollectionField", items["relationFieldType"])
                assertEquals("MANY", items["ownedCardinality"])
                assertEquals("ONE_TO_MANY_JOIN_COLUMN", items["persistenceShape"])
            },
            {
                assertEquals("_files", file["persistencePathName"])
                assertEquals("joinFile", file["methodName"])
                assertEquals("OWNED_ONE", file["relationKind"])
                assertEquals("VideoPostFile", file["targetEntityName"])
                assertEquals("com.acme.demo.domain.aggregates.video_post.VideoPostFile", file["targetEntityTypeFqn"])
                assertEquals("SVideoPostFile", file["targetSchemaName"])
                assertEquals("com.acme.demo.domain._share.meta.video_post.SVideoPostFile", file["targetSchemaFqn"])
                assertEquals("RelationOptionalField", file["relationFieldType"])
                assertEquals("ONE", file["ownedCardinality"])
                assertEquals("ONE_TO_MANY_JOIN_COLUMN", file["persistenceShape"])
            },
        )
    }

    @Test
    fun `schema planner omits ordinary and inverse relations from relation joins`() {
        val rootEntity = EntityModel(
            name = "VideoPost",
            packageName = "com.acme.demo.domain.aggregates.video_post",
            tableName = "video_post",
            comment = "video post",
            fields = listOf(FieldModel("id", "Long", columnName = "id")),
            idField = FieldModel("id", "Long", columnName = "id"),
        )
        val profileEntity = EntityModel(
            name = "UserProfile",
            packageName = "com.acme.demo.domain.aggregates.user_profile",
            tableName = "user_profile",
            comment = "user profile",
            fields = listOf(FieldModel("id", "Long", columnName = "id")),
            idField = FieldModel("id", "Long", columnName = "id"),
        )
        val plan = AggregateArtifactPlanner().plan(
            aggregateConfig(),
            CanonicalModel(
                entities = listOf(rootEntity, profileEntity),
                schemas = listOf(
                    SchemaModel(
                        name = "SVideoPost",
                        packageName = "com.acme.demo.domain._share.meta.video_post",
                        entityName = "VideoPost",
                        comment = "video post schema",
                        fields = rootEntity.fields,
                    ),
                    SchemaModel(
                        name = "SUserProfile",
                        packageName = "com.acme.demo.domain._share.meta.user_profile",
                        entityName = "UserProfile",
                        comment = "user profile schema",
                        fields = profileEntity.fields,
                    ),
                ),
                aggregateEntityJpa = listOf(defaultAggregateEntityJpa(rootEntity), defaultAggregateEntityJpa(profileEntity)),
                aggregateRelations = listOf(
                    AggregateRelationModel(
                        ownerEntityName = "VideoPost",
                        ownerEntityPackageName = "com.acme.demo.domain.aggregates.video_post",
                        fieldName = "author",
                        targetEntityName = "UserProfile",
                        targetEntityPackageName = "com.acme.demo.domain.aggregates.user_profile",
                        relationType = AggregateRelationType.MANY_TO_ONE,
                        joinColumn = "author_id",
                        fetchType = AggregateFetchType.LAZY,
                        nullable = false,
                    ),
                ),
                aggregateInverseRelations = listOf(
                    AggregateInverseRelationModel(
                        ownerEntityName = "VideoPost",
                        ownerEntityPackageName = "com.acme.demo.domain.aggregates.video_post",
                        fieldName = "readOnlyProfile",
                        targetEntityName = "UserProfile",
                        targetEntityPackageName = "com.acme.demo.domain.aggregates.user_profile",
                        relationType = AggregateRelationType.MANY_TO_ONE,
                        joinColumn = "author_id",
                        fetchType = AggregateFetchType.LAZY,
                    ),
                ),
            )
        )

        val schema = plan.single {
            it.templateId == "aggregate/schema.kt.peb" && it.context["typeName"] == "SVideoPost"
        }
        @Suppress("UNCHECKED_CAST")
        val relationJoins = schema.context.getValue("relationJoins") as List<Map<String, Any?>>

        assertEquals(emptyList<Map<String, Any?>>(), relationJoins)
    }

    @Test
    fun `schema planner fails fast when an eligible owned relation target schema is missing`() {
        val rootEntity = EntityModel(
            name = "VideoPost",
            packageName = "com.acme.demo.domain.aggregates.video_post",
            tableName = "video_post",
            comment = "video post",
            fields = listOf(FieldModel("id", "Long", columnName = "id")),
            idField = FieldModel("id", "Long", columnName = "id"),
        )
        val itemEntity = EntityModel(
            name = "VideoPostItem",
            packageName = "com.acme.demo.domain.aggregates.video_post",
            tableName = "video_post_item",
            comment = "video post item",
            fields = listOf(FieldModel("id", "Long", columnName = "id")),
            idField = FieldModel("id", "Long", columnName = "id"),
            aggregateRoot = false,
            parentEntityName = "VideoPost",
        )
        val model = CanonicalModel(
            entities = listOf(rootEntity, itemEntity),
            schemas = listOf(
                SchemaModel(
                    name = "SVideoPost",
                    packageName = "com.acme.demo.domain._share.meta.video_post",
                    entityName = "VideoPost",
                    comment = "video post schema",
                    fields = rootEntity.fields,
                ),
            ),
            aggregateEntityJpa = listOf(defaultAggregateEntityJpa(rootEntity), defaultAggregateEntityJpa(itemEntity)),
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
                ),
            ),
        )

        val ex = assertThrows(IllegalStateException::class.java) {
            AggregateArtifactPlanner().plan(aggregateConfig(), model)
        }

        assertEquals(
            "schema SVideoPost relation items requires exactly one target schema for com.acme.demo.domain.aggregates.video_post.VideoPostItem, but found 0",
            ex.message,
        )
    }
```

- [ ] **Step 2: Run planner tests and verify the new tests fail**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-generator-aggregate:test --tests "com.only4.cap4k.plugin.pipeline.generator.aggregate.AggregateArtifactPlannerTest"
```

Expected: FAIL because schema contexts do not contain `relationJoins` and the missing-target diagnostic is not implemented.

- [ ] **Step 3: Add relation join planning imports**

In `SchemaArtifactPlanner.kt`, add these imports with the existing API imports:

```kotlin
import com.only4.cap4k.plugin.pipeline.api.AggregateRelationType
import com.only4.cap4k.plugin.pipeline.api.OwnedRelationCardinality
import com.only4.cap4k.plugin.pipeline.api.OwnedRelationPersistenceShape
import com.only4.cap4k.plugin.pipeline.api.SchemaModel
```

- [ ] **Step 4: Add schema grouping and pass `relationJoins` into the template context**

Inside `plan`, after `entitiesByName`, add:

```kotlin
        val schemasByEntityName = model.schemas
            .groupBy { it.entityName }
```

Inside the `model.schemas.map { schema ->` block, after `imports`, add:

```kotlin
            val relationJoins = relationJoinsFor(
                schema = schema,
                entity = entity,
                model = model,
                schemasByEntityName = schemasByEntityName,
            )
```

Inside the `context = mapOf(...)` block, add:

```kotlin
                    "relationJoins" to relationJoins,
```

- [ ] **Step 5: Add the planner helper methods**

Add these private methods inside `SchemaArtifactPlanner`, below `requireUniqueSchemaEntity`:

```kotlin
    private fun relationJoinsFor(
        schema: SchemaModel,
        entity: EntityModel,
        model: CanonicalModel,
        schemasByEntityName: Map<String, List<SchemaModel>>,
    ): List<Map<String, Any?>> {
        val relationPlan = AggregateRelationPlanning.planFor(
            entity = entity,
            relations = model.aggregateRelations,
            inverseRelations = emptyList(),
        )

        return relationPlan.relationFields
            .filter(::isSchemaJoinRelation)
            .map { relation ->
                val targetSchema = requireUniqueTargetSchema(
                    ownerSchemaName = schema.name,
                    relation = relation,
                    schemasByEntityName = schemasByEntityName,
                )
                val domainName = relation.requiredString("domainName")
                val persistencePathName = relation.requiredString("persistencePathName")
                val targetEntityName = relation.requiredString("targetType")
                val targetEntityPackageName = relation.requiredString("targetPackageName")
                val ownedCardinality = relation.requiredString("ownedCardinality")
                val relationKind = when (ownedCardinality) {
                    OwnedRelationCardinality.MANY.name -> "OWNED_MANY"
                    OwnedRelationCardinality.ONE.name -> "OWNED_ONE"
                    else -> error(
                        "schema ${schema.name} relation $domainName has unsupported owned cardinality: $ownedCardinality"
                    )
                }
                mapOf(
                    "domainName" to domainName,
                    "persistencePathName" to persistencePathName,
                    "methodName" to "join${domainName.upperCamelIdentifier()}",
                    "relationKind" to relationKind,
                    "targetEntityName" to targetEntityName,
                    "targetEntityTypeFqn" to "$targetEntityPackageName.$targetEntityName",
                    "targetSchemaName" to targetSchema.name,
                    "targetSchemaFqn" to "${targetSchema.packageName}.${targetSchema.name}",
                    "relationFieldType" to when (relationKind) {
                        "OWNED_MANY" -> "RelationCollectionField"
                        "OWNED_ONE" -> "RelationOptionalField"
                        else -> error("schema ${schema.name} relation $domainName has unsupported relation kind: $relationKind")
                    },
                    "nullable" to relation["nullable"],
                    "ownedCardinality" to ownedCardinality,
                    "persistenceShape" to relation.requiredString("persistenceShape"),
                )
            }
    }

    private fun isSchemaJoinRelation(relation: Map<String, Any?>): Boolean =
        relation["owned"] == true &&
            relation["relationType"] == AggregateRelationType.ONE_TO_MANY.name &&
            relation["persistenceShape"] == OwnedRelationPersistenceShape.ONE_TO_MANY_JOIN_COLUMN.name &&
            relation["ownedCardinality"] in setOf(
                OwnedRelationCardinality.MANY.name,
                OwnedRelationCardinality.ONE.name,
            )

    private fun requireUniqueTargetSchema(
        ownerSchemaName: String,
        relation: Map<String, Any?>,
        schemasByEntityName: Map<String, List<SchemaModel>>,
    ): SchemaModel {
        val domainName = relation.requiredString("domainName")
        val targetEntityName = relation.requiredString("targetType")
        val targetEntityPackageName = relation.requiredString("targetPackageName")
        val candidates = schemasByEntityName[targetEntityName].orEmpty()
        if (candidates.size != 1) {
            error(
                "schema $ownerSchemaName relation $domainName requires exactly one target schema for " +
                    "$targetEntityPackageName.$targetEntityName, but found ${candidates.size}"
            )
        }
        return candidates.single()
    }

    private fun Map<String, Any?>.requiredString(key: String): String =
        this[key] as? String ?: error("schema relation render model requires string key: $key")

    private fun String.upperCamelIdentifier(): String =
        if (isEmpty()) this else replaceFirstChar { it.titlecase() }
```

- [ ] **Step 6: Run planner tests and verify they pass**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-generator-aggregate:test --tests "com.only4.cap4k.plugin.pipeline.generator.aggregate.AggregateArtifactPlannerTest"
```

Expected: PASS.

- [ ] **Step 7: Commit planner render model**

Run:

```powershell
git add cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/SchemaArtifactPlanner.kt cap4k-plugin-pipeline-generator-aggregate/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateArtifactPlannerTest.kt
git commit -m "feat: plan schema owned relation joins"
```
## Task 3: Renderer Tests For Schema Relation Join API

**Files:**
- Modify: `cap4k-plugin-pipeline-renderer-pebble/src/test/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PebbleArtifactRendererTest.kt`

**Interfaces:**
- Consumes: `relationJoins` entries produced by Task 2.
- Produces: assertions that `schema.kt.peb` renders relation fields, relation constants, join methods, query-local join cache, `From<*, E>` constructor roots, and aggregate-root distinct predicate overloads.

- [ ] **Step 1: Add root schema renderer coverage**

Append this test inside `PebbleArtifactRendererTest`, near the existing aggregate schema renderer tests:

```kotlin
    @Test
    fun `aggregate schema template renders owned relation fields joins constants and distinct predicate`() {
        val content = renderTemplate(
            templateId = "aggregate/schema.kt.peb",
            outputPath = "demo-domain/src/main/kotlin/com/acme/demo/domain/_share/meta/video_post/SVideoPost.kt",
            context = mapOf(
                "packageName" to "com.acme.demo.domain._share.meta.video_post",
                "typeName" to "SVideoPost",
                "entityName" to "VideoPost",
                "schemaRuntimePackage" to "com.only4.cap4k.ddd.domain.repo.schema",
                "entityTypeFqn" to "com.acme.demo.domain.aggregates.video_post.VideoPost",
                "isAggregateRoot" to true,
                "imports" to emptyList<String>(),
                "fields" to listOf(
                    mapOf(
                        "name" to "title",
                        "fieldName" to "title",
                        "columnName" to "title",
                        "fieldType" to "String",
                        "type" to "String",
                        "renderedType" to "String",
                        "comment" to "title",
                    )
                ),
                "relationJoins" to listOf(
                    mapOf(
                        "domainName" to "items",
                        "persistencePathName" to "_items",
                        "methodName" to "joinItems",
                        "relationKind" to "OWNED_MANY",
                        "targetEntityName" to "VideoPostItem",
                        "targetEntityTypeFqn" to "com.acme.demo.domain.aggregates.video_post.VideoPostItem",
                        "targetSchemaName" to "SVideoPostItem",
                        "targetSchemaFqn" to "com.acme.demo.domain._share.meta.video_post.SVideoPostItem",
                        "relationFieldType" to "RelationCollectionField",
                        "nullable" to false,
                        "ownedCardinality" to "MANY",
                        "persistenceShape" to "ONE_TO_MANY_JOIN_COLUMN",
                    ),
                    mapOf(
                        "domainName" to "file",
                        "persistencePathName" to "_files",
                        "methodName" to "joinFile",
                        "relationKind" to "OWNED_ONE",
                        "targetEntityName" to "VideoPostFile",
                        "targetEntityTypeFqn" to "com.acme.demo.domain.aggregates.video_post.VideoPostFile",
                        "targetSchemaName" to "SVideoPostFile",
                        "targetSchemaFqn" to "com.acme.demo.domain._share.meta.video_post.SVideoPostFile",
                        "relationFieldType" to "RelationOptionalField",
                        "nullable" to false,
                        "ownedCardinality" to "ONE",
                        "persistenceShape" to "ONE_TO_MANY_JOIN_COLUMN",
                    ),
                ),
            ),
        )

        assertReadableKotlin(content)
        assertTrue(content.contains("import jakarta.persistence.criteria.From"))
        assertTrue(content.contains("import jakarta.persistence.criteria.Join"))
        assertTrue(content.contains("import com.only4.cap4k.ddd.domain.repo.schema.JoinType"))
        assertTrue(content.contains("import com.only4.cap4k.ddd.domain.repo.schema.RelationCollectionField"))
        assertTrue(content.contains("import com.only4.cap4k.ddd.domain.repo.schema.RelationOptionalField"))
        assertTrue(content.contains("import com.acme.demo.domain.aggregates.video_post.VideoPost"))
        assertTrue(content.contains("import com.acme.demo.domain.aggregates.video_post.VideoPostItem"))
        assertTrue(content.contains("import com.acme.demo.domain.aggregates.video_post.VideoPostFile"))
        assertTrue(content.contains("import com.acme.demo.domain._share.meta.video_post.SVideoPostItem"))
        assertTrue(content.contains("import com.acme.demo.domain._share.meta.video_post.SVideoPostFile"))
        assertTrue(content.contains("private val root: From<*, VideoPost>"))
        assertTrue(content.contains("val title: Field<String>"))
        assertTrue(content.contains("class PROPERTY_NAMES"))
        assertTrue(content.contains("val title = \"title\""))
        assertTrue(content.contains("class RELATION_NAMES"))
        assertTrue(content.contains("val items = \"items\""))
        assertTrue(content.contains("val file = \"file\""))
        assertTrue(content.contains("val props = PROPERTY_NAMES()"))
        assertTrue(content.contains("val relations = RELATION_NAMES()"))
        assertTrue(content.contains("fun predicate(builder: PredicateBuilder<SVideoPost>): JpaPredicate<VideoPost>"))
        assertTrue(content.contains("return predicate(false, builder)"))
        assertTrue(content.contains("fun predicate(distinct: Boolean, builder: PredicateBuilder<SVideoPost>): JpaPredicate<VideoPost>"))
        assertTrue(content.contains("return JpaPredicate.bySpecification(VideoPost::class.java, specify(builder, distinct))"))
        assertTrue(content.contains("val items: RelationCollectionField<VideoPostItem>"))
        assertTrue(content.contains("RelationCollectionField(root.get<Collection<VideoPostItem>>(\"_items\"), criteriaBuilder)"))
        assertTrue(content.contains("val file: RelationOptionalField<VideoPostFile>"))
        assertTrue(content.contains("RelationOptionalField(root.get<Collection<VideoPostFile>>(\"_files\"), criteriaBuilder)"))
        assertTrue(content.contains("fun joinItems(): SVideoPostItem = joinItems(JoinType.INNER)"))
        assertTrue(content.contains("fun joinItems(joinType: JoinType): SVideoPostItem"))
        assertTrue(content.contains("val join = _join<VideoPostItem>(\"items\", \"_items\", joinType)"))
        assertTrue(content.contains("SVideoPostItem(join, criteriaBuilder)"))
        assertTrue(content.contains("fun joinFile(): SVideoPostFile = joinFile(JoinType.INNER)"))
        assertTrue(content.contains("fun joinFile(joinType: JoinType): SVideoPostFile"))
        assertTrue(content.contains("val join = _join<VideoPostFile>(\"file\", \"_files\", joinType)"))
        assertTrue(content.contains("SVideoPostFile(join, criteriaBuilder)"))
        assertTrue(content.contains("private data class JoinCacheKey"))
        assertTrue(content.contains("private val joinTypesByPath = mutableMapOf<JoinCacheKey, JoinType>()"))
        assertTrue(content.contains("root.join<VideoPost, T>(persistencePathName, joinType.toJpaJoinType())"))
        assertTrue(content.contains("schema relation $" + "domainName is already joined as $" + "existingType"))
        assertFalse(content.contains("val _items: RelationCollectionField"))
        assertFalse(content.contains("val _files: RelationOptionalField"))
        assertFalse(content.contains("fun join_items"))
        assertFalse(content.contains("fun join_files"))
        assertFalse(content.contains("val _items = \"_items\""))
        assertFalse(content.contains("val _files = \"_files\""))
    }
```

- [ ] **Step 2: Add child schema renderer coverage for chained joins**

Append this test inside `PebbleArtifactRendererTest` near the root schema renderer test:

```kotlin
    @Test
    fun `aggregate child schema template renders chained owned joins without aggregate root predicates`() {
        val content = renderTemplate(
            templateId = "aggregate/schema.kt.peb",
            outputPath = "demo-domain/src/main/kotlin/com/acme/demo/domain/_share/meta/video_post/SVideoPostItem.kt",
            context = mapOf(
                "packageName" to "com.acme.demo.domain._share.meta.video_post",
                "typeName" to "SVideoPostItem",
                "entityName" to "VideoPostItem",
                "schemaRuntimePackage" to "com.only4.cap4k.ddd.domain.repo.schema",
                "entityTypeFqn" to "com.acme.demo.domain.aggregates.video_post.VideoPostItem",
                "isAggregateRoot" to false,
                "imports" to emptyList<String>(),
                "fields" to listOf(
                    mapOf(
                        "name" to "label",
                        "fieldName" to "label",
                        "columnName" to "label",
                        "fieldType" to "String",
                        "type" to "String",
                        "renderedType" to "String",
                        "comment" to "label",
                    )
                ),
                "relationJoins" to listOf(
                    mapOf(
                        "domainName" to "adjustments",
                        "persistencePathName" to "_adjustments",
                        "methodName" to "joinAdjustments",
                        "relationKind" to "OWNED_MANY",
                        "targetEntityName" to "VideoPostItemAdjustment",
                        "targetEntityTypeFqn" to "com.acme.demo.domain.aggregates.video_post.VideoPostItemAdjustment",
                        "targetSchemaName" to "SVideoPostItemAdjustment",
                        "targetSchemaFqn" to "com.acme.demo.domain._share.meta.video_post.SVideoPostItemAdjustment",
                        "relationFieldType" to "RelationCollectionField",
                        "nullable" to false,
                        "ownedCardinality" to "MANY",
                        "persistenceShape" to "ONE_TO_MANY_JOIN_COLUMN",
                    ),
                ),
            ),
        )

        assertReadableKotlin(content)
        assertTrue(content.contains("private val root: From<*, VideoPostItem>"))
        assertTrue(content.contains("val label: Field<String>"))
        assertTrue(content.contains("val adjustments: RelationCollectionField<VideoPostItemAdjustment>"))
        assertTrue(content.contains("fun joinAdjustments(): SVideoPostItemAdjustment = joinAdjustments(JoinType.INNER)"))
        assertTrue(content.contains("fun joinAdjustments(joinType: JoinType): SVideoPostItemAdjustment"))
        assertTrue(content.contains("SVideoPostItemAdjustment(join, criteriaBuilder)"))
        assertFalse(content.contains("fun predicateById("))
        assertFalse(content.contains("fun predicate(builder: PredicateBuilder<SVideoPostItem>): JpaPredicate<VideoPostItem>"))
    }
```

- [ ] **Step 3: Run renderer tests and verify the new tests fail**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-renderer-pebble:test --tests "com.only4.cap4k.plugin.pipeline.renderer.pebble.PebbleArtifactRendererTest"
```

Expected: FAIL because the schema template does not render relation helpers yet and still uses `Path<E>` for the constructor root.

- [ ] **Step 4: Commit the failing renderer tests only after confirming the expected failure is understood**

Do not commit a failing test batch unless the team accepts red commits. If commits must stay green, defer this commit until Task 4 Step 7.

## Task 4: Schema Template Relation Fields, Joins, Cache, And Distinct Predicate

**Files:**
- Modify: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/schema.kt.peb`
- Modify: `cap4k-plugin-pipeline-renderer-pebble/src/test/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PebbleArtifactRendererTest.kt`

**Interfaces:**
- Consumes: scalar `fields`, `relationJoins`, `entityTypeFqn`, `schemaRuntimePackage`, and `isAggregateRoot` from `SchemaArtifactPlanner`.
- Produces: generated schema classes with `From<*, E>` root, scalar `Field<T>` fields, relation existence fields, `relations` constants, `joinXxx()` methods, conflict-aware query-local join cache, and `predicate(distinct: Boolean, builder)` for aggregate roots.

- [ ] **Step 1: Replace the schema template import header**

In `schema.kt.peb`, replace the current import/use header through the `imports(imports)` loop with this block:

```pebble
package {{ packageName }}

{% if aggregateElement is defined -%}
{{ use("com.only4.cap4k.ddd.core.annotation.AggregateElement") -}}
{% endif -%}
{{ use("org.springframework.data.jpa.domain.Specification") -}}
{{ use("jakarta.persistence.criteria.CriteriaBuilder") -}}
{{ use("jakarta.persistence.criteria.CriteriaQuery") -}}
{{ use("jakarta.persistence.criteria.From") -}}
{{ use("jakarta.persistence.criteria.Path") -}}
{{ use("jakarta.persistence.criteria.Predicate") -}}
{{ use("jakarta.persistence.criteria.Subquery") -}}
{{ use(schemaRuntimePackage ~ ".SchemaSpecification") -}}
{{ use(schemaRuntimePackage ~ ".PredicateBuilder") -}}
{{ use(schemaRuntimePackage ~ ".OrderBuilder") -}}
{{ use(schemaRuntimePackage ~ ".ExpressionBuilder") -}}
{{ use(schemaRuntimePackage ~ ".SubqueryConfigure") -}}
{{ use(schemaRuntimePackage ~ ".Field") -}}
{%- if relationJoins is defined and relationJoins|length > 0 %}
{{ use("jakarta.persistence.criteria.Join") -}}
{{ use(schemaRuntimePackage ~ ".JoinType") -}}
{{ use(schemaRuntimePackage ~ ".RelationCollectionField") -}}
{{ use(schemaRuntimePackage ~ ".RelationOptionalField") -}}
{% for relation in relationJoins -%}
{{ use(relation.targetEntityTypeFqn) -}}
{{ use(relation.targetSchemaFqn) -}}
{% endfor -%}
{%- endif %}
{%- if entityTypeFqn %}
{{ use(entityTypeFqn) -}}
{%- endif %}
{%- if isAggregateRoot %}
{{ use("com.only4.cap4k.ddd.domain.repo.JpaPredicate") -}}
{%- endif %}
{% for import in imports(imports) -%}
import {{ import }}
{% endfor %}
```

This imports `From` unconditionally because a schema with no child relation can still be returned from another schema's join and must accept a `Join` root.

- [ ] **Step 2: Change the schema root constructor type**

Replace the class constructor with this block:

```pebble
class {{ typeName }}(
    private val root: From<*, {{ entityName }}>,
    private val criteriaBuilder: CriteriaBuilder,
) {
```

Keep `_root(): Path<{{ entityName }}> = root` later in the file; `From` is a `Path`, so existing callers keep path-style access.

- [ ] **Step 3: Render relation name constants separately from scalar props**

Replace the `PROPERTY_NAMES`/`companion object` opening area with this block:

```pebble
    class PROPERTY_NAMES {
{% for field in fields %}
        val {{ field.fieldName }} = "{{ field.fieldName }}"
{% endfor %}
    }
{%- if relationJoins is defined and relationJoins|length > 0 %}

    class RELATION_NAMES {
{% for relation in relationJoins %}
        val {{ relation.domainName }} = "{{ relation.domainName }}"
{% endfor %}
    }
{%- endif %}

    companion object {

        val props = PROPERTY_NAMES()
{%- if relationJoins is defined and relationJoins|length > 0 %}

        val relations = RELATION_NAMES()
{%- endif %}
```

Do not add relation names to `PROPERTY_NAMES`.

- [ ] **Step 4: Add aggregate-root distinct predicate overload**

Inside the existing `{% if isAggregateRoot %}` companion block, replace the current aggregate-root `predicate(builder)` implementation with these two overloads while keeping `predicateById`, `predicateByIds`, and `predicate(specifier)` unchanged:

```pebble
        @JvmStatic
        fun predicate(builder: PredicateBuilder<{{ typeName }}>): JpaPredicate<{{ entityName }}> {
            return predicate(false, builder)
        }

        @JvmStatic
        fun predicate(distinct: Boolean, builder: PredicateBuilder<{{ typeName }}>): JpaPredicate<{{ entityName }}> {
            return JpaPredicate.bySpecification({{ entityName }}::class.java, specify(builder, distinct))
        }
```

The no-distinct overload remains source-compatible and delegates to `distinct = false`.

- [ ] **Step 5: Render relation fields after scalar fields**

After the existing scalar field loop, add this relation field loop:

```pebble
{%- if relationJoins is defined and relationJoins|length > 0 %}
{% for relation in relationJoins %}
{%- if relation.relationKind == "OWNED_MANY" %}
    val {{ relation.domainName }}: RelationCollectionField<{{ relation.targetEntityName }}> by lazy {
        RelationCollectionField(root.get<Collection<{{ relation.targetEntityName }}>>("{{ relation.persistencePathName }}"), criteriaBuilder)
    }
{%- elseif relation.relationKind == "OWNED_ONE" %}
    val {{ relation.domainName }}: RelationOptionalField<{{ relation.targetEntityName }}> by lazy {
        RelationOptionalField(root.get<Collection<{{ relation.targetEntityName }}>>("{{ relation.persistencePathName }}"), criteriaBuilder)
    }
{%- endif %}

{% endfor %}
{%- endif %}
```

The generated public property name is `domainName`; the internal `root.get` string is `persistencePathName`.

- [ ] **Step 6: Render join cache and join methods**

After relation fields and before `fun all(...)`, add this block:

```pebble
{%- if relationJoins is defined and relationJoins|length > 0 %}
    private data class JoinCacheKey(
        val domainName: String,
        val persistencePathName: String,
    )

    private val joinTypesByPath = mutableMapOf<JoinCacheKey, JoinType>()
    private val joinsByPath = mutableMapOf<JoinCacheKey, Join<*, *>>()
    private val schemasByPath = mutableMapOf<Pair<JoinCacheKey, JoinType>, Any>()

    @Suppress("UNCHECKED_CAST")
    private fun <T> _join(
        domainName: String,
        persistencePathName: String,
        joinType: JoinType,
    ): Join<*, T> {
        val key = JoinCacheKey(domainName, persistencePathName)
        val existingType = joinTypesByPath[key]
        if (existingType != null && existingType != joinType) {
            error(
                "schema relation $domainName is already joined as $existingType; " +
                    "cannot join it again as $joinType in the same schema instance"
            )
        }
        joinTypesByPath[key] = joinType
        return joinsByPath.getOrPut(key) {
            root.join<{{ entityName }}, T>(persistencePathName, joinType.toJpaJoinType())
        } as Join<*, T>
    }

    @Suppress("UNCHECKED_CAST")
    private fun <S : Any> _joinedSchema(
        domainName: String,
        persistencePathName: String,
        joinType: JoinType,
        factory: () -> S,
    ): S {
        val key = JoinCacheKey(domainName, persistencePathName) to joinType
        return schemasByPath.getOrPut(key) { factory() } as S
    }

{% for relation in relationJoins %}
    fun {{ relation.methodName }}(): {{ relation.targetSchemaName }} = {{ relation.methodName }}(JoinType.INNER)

    fun {{ relation.methodName }}(joinType: JoinType): {{ relation.targetSchemaName }} {
        val join = _join<{{ relation.targetEntityName }}>("{{ relation.domainName }}", "{{ relation.persistencePathName }}", joinType)
        return _joinedSchema("{{ relation.domainName }}", "{{ relation.persistencePathName }}", joinType) {
            {{ relation.targetSchemaName }}(join, criteriaBuilder)
        }
    }

{% endfor %}
{%- endif %}
```

The cache is an instance field on the generated schema class. It is not static and is not shared across query roots.

- [ ] **Step 7: Run renderer tests and verify they pass**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-renderer-pebble:test --tests "com.only4.cap4k.plugin.pipeline.renderer.pebble.PebbleArtifactRendererTest"
```

Expected: PASS.

- [ ] **Step 8: Commit schema template rendering**

Run:

```powershell
git add cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/schema.kt.peb cap4k-plugin-pipeline-renderer-pebble/src/test/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PebbleArtifactRendererTest.kt
git commit -m "feat: render schema owned relation joins"
```
## Task 5: Compile Functional Coverage For Owned Relation Query API

**Files:**
- Modify: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginCompileFunctionalTest.kt`

**Interfaces:**
- Consumes: generated schemas from Task 4 and the existing `aggregate-relation-compile-sample` fixture.
- Produces: a domain compile test that exercises `SVideoPost.predicate(distinct = true)`, owned-many relation existence, owned-one relation nullability, explicit left join, and chained owned join from `SVideoPostItem` to `SVideoPostItemAdjustment`.

- [ ] **Step 1: Add compile functional test**

Append this test after `aggregate relation generation keeps owned direct parent bindings scalar plus read only inverse relation` in `PipelinePluginCompileFunctionalTest.kt`:

```kotlin
    @Test
    fun `aggregate schema owned relation joins compile for owned many owned one and chained children`() {
        val projectDir = Files.createTempDirectory("pipeline-functional-aggregate-schema-relation-compile")
        FunctionalFixtureSupport.copyCompileFixture(projectDir, "aggregate-relation-compile-sample")
        val buildFile = projectDir.resolve("build.gradle.kts")
        buildFile.writeText(
            buildFile.readText().replace(
                """includeTables.set(listOf("video_post", "video_post_item", "video_post_file", "user_profile", "content", "media_processing_task"))""",
                """includeTables.set(listOf("video_post", "video_post_item", "video_post_file", "video_post_item_adjustment", "user_profile", "content", "media_processing_task"))""",
            )
        )
        val schemaFile = projectDir.resolve("schema.sql")
        schemaFile.writeText(
            schemaFile.readText() +
                """

                create table video_post_item_adjustment (
                    id bigint primary key comment '@IdStrategy=db_identity;',
                    video_post_item_id bigint not null comment '@ParentRef;',
                    reason varchar(64) not null
                );

                comment on table video_post_item_adjustment is '@Parent=video_post_item;';
                """.trimIndent()
        )
        val smokeFile = projectDir.resolve(
            "demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/SchemaRelationCompileSmoke.kt"
        )
        smokeFile.writeText(
            """
            package com.acme.demo.domain.aggregates.video_post

            import com.acme.demo.domain._share.meta.video_post.SVideoPost
            import com.only4.cap4k.ddd.domain.repo.schema.JoinType

            class SchemaRelationCompileSmoke {
                fun compileOwnedRelationQueries(label: String, storageKey: String, reason: String) {
                    SVideoPost.predicate(distinct = true) { post ->
                        val item = post.joinItems()
                        val file = post.joinFile(JoinType.LEFT)
                        val adjustment = item.joinAdjustments()

                        post.all(
                            post.items.isNotEmpty(),
                            post.file.isNotNull(),
                            item.label eq label,
                            file.storageKey eq storageKey,
                            adjustment.reason eq reason,
                        )
                    }
                }
            }
            """.trimIndent()
        )

        val compileResult = FunctionalFixtureSupport
            .runner(projectDir, ":demo-domain:compileKotlin")
            .build()
        val rootSchema = projectDir.resolve(
            generatedSource("demo-domain/src/main/kotlin/com/acme/demo/domain/_share/meta/video_post/SVideoPost.kt")
        ).readText()
        val itemSchema = projectDir.resolve(
            generatedSource("demo-domain/src/main/kotlin/com/acme/demo/domain/_share/meta/video_post/SVideoPostItem.kt")
        ).readText()

        assertEquals(TaskOutcome.SUCCESS, compileResult.task(":cap4kGenerateSources")?.outcome)
        assertTrue(compileResult.output.contains("BUILD SUCCESSFUL"))
        assertTrue(rootSchema.contains("fun predicate(distinct: Boolean, builder: PredicateBuilder<SVideoPost>): JpaPredicate<VideoPost>"))
        assertTrue(rootSchema.contains("val items: RelationCollectionField<VideoPostItem>"))
        assertTrue(rootSchema.contains("val file: RelationOptionalField<VideoPostFile>"))
        assertTrue(rootSchema.contains("fun joinItems(): SVideoPostItem = joinItems(JoinType.INNER)"))
        assertTrue(rootSchema.contains("fun joinFile(joinType: JoinType): SVideoPostFile"))
        assertTrue(rootSchema.contains("root.join<VideoPost, T>(persistencePathName, joinType.toJpaJoinType())"))
        assertTrue(itemSchema.contains("root.join<VideoPostItem, T>(persistencePathName, joinType.toJpaJoinType())"))
        assertTrue(itemSchema.contains("fun joinAdjustments(): SVideoPostItemAdjustment = joinAdjustments(JoinType.INNER)"))
        assertFalse(rootSchema.contains("val _items: RelationCollectionField"))
        assertFalse(rootSchema.contains("val _files: RelationOptionalField"))
        assertFalse(rootSchema.contains("fun join_items"))
        assertFalse(rootSchema.contains("fun join_files"))
    }
```

- [ ] **Step 2: Run compile functional test and verify it fails before Tasks 1-4 are implemented**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-gradle:test --tests "com.only4.cap4k.plugin.pipeline.gradle.PipelinePluginCompileFunctionalTest"
```

Expected before implementation: FAIL because generated schemas do not have relation helpers.

- [ ] **Step 3: Run compile functional test after Tasks 1-4 are complete**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-gradle:test --tests "com.only4.cap4k.plugin.pipeline.gradle.PipelinePluginCompileFunctionalTest"
```

Expected after implementation: PASS.

- [ ] **Step 4: Commit compile functional coverage**

Run:

```powershell
git add cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginCompileFunctionalTest.kt
git commit -m "test: compile schema owned relation joins"
```

## Task 6: Final Verification And Static Contract Audit

**Files:**
- Verify: `ddd-domain-repo-jpa/src/main/kotlin/com/only4/cap4k/ddd/domain/repo/schema/RelationCollectionField.kt`
- Verify: `ddd-domain-repo-jpa/src/main/kotlin/com/only4/cap4k/ddd/domain/repo/schema/RelationOptionalField.kt`
- Verify: `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/SchemaArtifactPlanner.kt`
- Verify: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/schema.kt.peb`
- Verify: focused tests from Tasks 1-5.

**Interfaces:**
- Consumes: implemented code and generated renderer/planner/functional test evidence.
- Produces: final evidence that schema owned relation joins work without repository, UoW, ID, or public backing-name regressions.

- [ ] **Step 1: Run runtime helper tests**

Run:

```powershell
./gradlew :ddd-domain-repo-jpa:test --tests "com.only4.cap4k.ddd.domain.repo.schema.*"
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: Run aggregate planner tests**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-generator-aggregate:test --tests "com.only4.cap4k.plugin.pipeline.generator.aggregate.AggregateArtifactPlannerTest"
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Run renderer tests**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-renderer-pebble:test --tests "com.only4.cap4k.plugin.pipeline.renderer.pebble.PebbleArtifactRendererTest"
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Run compile functional tests**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-gradle:test --tests "com.only4.cap4k.plugin.pipeline.gradle.PipelinePluginCompileFunctionalTest"
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Search for public backing-name leaks in template output assertions**

Run:

```powershell
rg -n "relations\._|fun join_|val _items: Relation|val _files: Relation" cap4k-plugin-pipeline-renderer-pebble/src/test/kotlin cap4k-plugin-pipeline-gradle/src/test/kotlin
```

Expected: only negative assertions with `assertFalse`, or no matches.

- [ ] **Step 6: Search for forbidden production boundary changes**

Run:

```powershell
git diff --name-only HEAD -- ddd-core ddd-domain-repo-jpa cap4k-plugin-pipeline-generator-aggregate cap4k-plugin-pipeline-renderer-pebble cap4k-plugin-pipeline-gradle
```

Expected: changed production files are limited to the schema helper package, `SchemaArtifactPlanner.kt`, and `schema.kt.peb`; changed test files are limited to the focused planner, renderer, schema helper, and compile functional tests.

- [ ] **Step 7: Confirm repository and UoW APIs were not edited**

Run:

```powershell
git diff --name-only HEAD -- ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/repo ddd-domain-repo-jpa/src/main/kotlin/com/only4/cap4k/ddd/domain/repo ddd-domain-repo-jpa/src/main/kotlin/com/only4/cap4k/ddd/application/JpaUnitOfWork.kt ddd-domain-repo-jpa/src/main/kotlin/com/only4/cap4k/ddd/application/JpaGeneratedOwnedRelationTraversal.kt
```

Expected: no repository interface files, no `JpaUnitOfWork.kt`, and no `JpaGeneratedOwnedRelationTraversal.kt` unless the only repo-path production changes are the new files under `ddd-domain-repo-jpa/src/main/kotlin/com/only4/cap4k/ddd/domain/repo/schema`.

- [ ] **Step 8: Run final status check**

Run:

```powershell
git status --short
```

Expected: only intentional committed work remains clean. If a no-commit workflow is used, all uncommitted files match the task file map.

## Rollback Triggers

Stop implementation and revise the design if any of these conditions appear:

- JPA Criteria cannot join the private backing collection fields generated by Phase 3.5 without exposing those names publicly.
- `From<*, E>` schema constructor roots break scalar schema usage in a way not repairable inside `schema.kt.peb`.
- Owned-one nullability cannot be represented through backing collection emptiness.
- Join caching requires static, global, or cross-query state.
- Chained joins require enabling ordinary, inverse, reference ID, or cross-aggregate relation joins.
- Repository public interfaces or repository adapter inheritance must change.
- UoW, lifecycle classification, or ID generation must change.
- Generated public schema APIs expose `_items`, `_files`, or another private backing name.

## Self-Review Notes

- Problem statement coverage: Tasks 2-5 restore generated schema relation query capability for owned-many, owned-one, and chained owned joins.
- Public/private name split coverage: Task 2 consumes normalized `domainName` and `persistencePathName`; Tasks 3-5 assert public names and internal backing path strings separately.
- Runtime helper coverage: Task 1 adds dedicated relation field types instead of expanding scalar `Field<T>`.
- Join cache coverage: Task 4 renders per-schema-instance maps and conflict diagnostics; Task 3 asserts the generated cache shape.
- Repository boundary coverage: Task 6 checks repository and UoW files remain unchanged.
- Verification coverage: focused runtime, planner, renderer, compile functional, static search, and final status checks are listed with exact commands.

## Execution Handoff

Plan complete when saved to `docs/superpowers/plans/2026-07-24-cap4k-default-schema-owned-relation-join.md`.

Two execution options:

1. Subagent-Driven (recommended) - dispatch a fresh subagent per task and review between tasks.
2. Inline Execution - execute tasks in this session using `superpowers:executing-plans` with checkpoints.
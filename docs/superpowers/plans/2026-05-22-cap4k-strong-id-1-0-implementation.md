# Cap4k Strong ID 1.0 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make String UUIDv7 Strong ID the default generated aggregate-root identity model, with same-context aggregate references, cross-context reference IDs, persistence, JSON/MVC boundary support, and synchronized docs/skills.

**Architecture:** Add Strong ID metadata to the canonical model, parse `@RefAggregate` and `@RefId` from DB comments, generate ID type artifacts and Strong ID field types, and make JPA/Jackson/Spring binding work through generated or framework-owned adapters. Aggregate creation allocates only the aggregate root's own ID in cap4k `AggregateFactory<Payload, Aggregate>` style; UoW save-time ID assignment leaves the default generated path.

**Tech Stack:** Kotlin, Gradle, JUnit 5, Spring Boot, Spring Data JPA, Hibernate, Jackson, Pebble templates, cap4k pipeline generator.

---

## Scope Rules

This plan implements the approved design in `docs/superpowers/specs/2026-05-22-cap4k-strong-id-1-0-design.md`.

Do not broaden this implementation into QueryDSL deletion or Snowflake module deletion. Those are slimming tasks after Strong ID stabilizes. Do remove Snowflake/primitive ID strategy exposure from the generated default path when it directly conflicts with Strong ID.

This plan intentionally uses hard gates. The first JPA hard gate showed that `@JvmInline value class` plus `@Convert` can insert but fails Spring Data `findById(ContentId)`. The implementation route is therefore single-column `@Embeddable` Strong ID plus `@EmbeddedId`. If that route fails later, stop and revise the plan instead of silently falling back to primitive `String id`.

## File Structure

Create:

- `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/id/StrongId.kt`  
  Marker interface for generated Strong ID types.

- `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/id/StrongIds.kt`  
  UUIDv7 generation and validation helper. Keep current UUIDv7 implementation or `uuid-creator`; Kotlin 2.3 is not required.

- `ddd-core/src/test/kotlin/com/only4/cap4k/ddd/core/domain/id/StrongIdsTest.kt`  
  Focused validation tests.

- `cap4k-ddd-starter/src/test/kotlin/com/only4/cap4k/ddd/runtime/StrongIdJpaRuntimeTest.kt`  
  Hard-gate runtime proof for `@EmbeddedId` Strong ID persistence.

- `cap4k-ddd-starter/src/test/kotlin/com/only4/cap4k/ddd/runtime/StrongIdJacksonRuntimeTest.kt`  
  JSON serialization/deserialization proof.

- `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/StrongIdArtifactPlanner.kt`  
  Plans generated Strong ID type files.

- `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/strong_id.kt.peb`  
  Renders generated Strong ID types.

Modify:

- `cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModels.kt`  
  Add Strong ID metadata and DB snapshot fields.

- `cap4k-plugin-pipeline-source-db/src/main/kotlin/com/only4/cap4k/plugin/pipeline/source/db/DbColumnAnnotationParser.kt`  
  Parse `@RefId`.

- `cap4k-plugin-pipeline-source-db/src/main/kotlin/com/only4/cap4k/plugin/pipeline/source/db/DbRelationAnnotationParser.kt`  
  Parse `@RefAggregate` without treating it as JPA relationship.

- `cap4k-plugin-pipeline-source-db/src/main/kotlin/com/only4/cap4k/plugin/pipeline/source/db/DbSchemaSourceProvider.kt`  
  Populate new DB snapshot fields.

- `cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssembler.kt`  
  Resolve Strong ID metadata and field type replacements.

- `cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/AggregateSpecialFieldPolicyResolver.kt`  
  Make aggregate root ID policy Strong ID by default.

- `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateArtifactPlanner.kt`  
  Include `StrongIdArtifactPlanner`.

- `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/EntityArtifactPlanner.kt`  
  Use Strong ID field types, remove UUID sentinel default path, remove default `@ApplicationSideId`.

- `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/FactoryArtifactPlanner.kt`  
  Exclude aggregate root ID from creation payload and add factory context for `ContentId.new()`.

- `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/RepositoryArtifactPlanner.kt`  
  Use Strong ID as repository ID type.

- `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/entity.kt.peb`  
  Render Strong ID imports and converters without `@ApplicationSideId`.

- `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/factory.kt.peb`  
  Generate cap4k-style factory skeleton that creates own aggregate ID.

- `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/repository.kt.peb`  
  Render repository ID type imports.

- `docs/superpowers/analysis/`, `docs/public/authoring/`, `skills/`  
  Align guidance after code behavior is proven.

## Task 1: Strong ID Core Helper

**Files:**

- Create: `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/id/StrongId.kt`
- Create: `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/id/StrongIds.kt`
- Create: `ddd-core/src/test/kotlin/com/only4/cap4k/ddd/core/domain/id/StrongIdsTest.kt`

- [ ] **Step 1: Write failing validation tests**

Create `ddd-core/src/test/kotlin/com/only4/cap4k/ddd/core/domain/id/StrongIdsTest.kt`:

```kotlin
package com.only4.cap4k.ddd.core.domain.id

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class StrongIdsTest {
    @Test
    fun `newUuidV7String returns canonical uuid v7`() {
        val value = StrongIds.newUuidV7String()

        assertEquals(value.lowercase(), value)
        assertEquals(36, value.length)
        assertEquals('7', value[14])
        assertEquals(value, StrongIds.requireUuidV7(value, "ContentId"))
    }

    @Test
    fun `newUuidV7String returns different values`() {
        assertNotEquals(StrongIds.newUuidV7String(), StrongIds.newUuidV7String())
    }

    @Test
    fun `requireUuidV7 rejects invalid values`() {
        val invalidValues = listOf(
            "",
            " ",
            "not-a-uuid",
            "00000000-0000-0000-0000-000000000000",
            "550e8400-e29b-41d4-a716-446655440000",
        )

        invalidValues.forEach { value ->
            val error = assertThrows(IllegalArgumentException::class.java) {
                StrongIds.requireUuidV7(value, "ContentId")
            }
            assertTrue(error.message!!.contains("ContentId must be a UUIDv7 value"))
        }
    }
}
```

- [ ] **Step 2: Run core tests and verify RED**

Run:

```powershell
.\gradlew.bat :ddd-core:test --tests "com.only4.cap4k.ddd.core.domain.id.StrongIdsTest" --rerun-tasks
```

Expected: FAIL because `StrongIds` does not exist.

- [ ] **Step 3: Add Strong ID core contracts**

Create `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/id/StrongId.kt`:

```kotlin
package com.only4.cap4k.ddd.core.domain.id

interface StrongId {
    val value: String
}
```

Create `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/id/StrongIds.kt`:

```kotlin
package com.only4.cap4k.ddd.core.domain.id

import com.github.f4b6a3.uuid.UuidCreator
import java.util.UUID

object StrongIds {
    fun newUuidV7String(): String = UuidCreator.getTimeOrderedEpoch().toString()

    fun requireUuidV7(value: String, typeName: String): String {
        val normalized = value.trim().lowercase()
        require(normalized == value) {
            "$typeName must be a UUIDv7 value: $value"
        }
        val uuid = runCatching { UUID.fromString(normalized) }.getOrNull()
        require(uuid != null && uuid.version() == 7 && uuid != UUID(0L, 0L)) {
            "$typeName must be a UUIDv7 value: $value"
        }
        return normalized
    }
}
```

If `ddd-core` does not already have access to `uuid-creator`, add the dependency to `ddd-core/build.gradle.kts`. Do not upgrade Kotlin in this task.

- [ ] **Step 4: Run core tests and verify GREEN**

Run:

```powershell
.\gradlew.bat :ddd-core:test --tests "com.only4.cap4k.ddd.core.domain.id.StrongIdsTest" --rerun-tasks
```

Expected: PASS.

- [ ] **Step 5: Commit**

```powershell
git add ddd-core/build.gradle.kts ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/id/StrongId.kt ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/id/StrongIds.kt ddd-core/src/test/kotlin/com/only4/cap4k/ddd/core/domain/id/StrongIdsTest.kt
git commit -m "feat: add strong id core helpers"
```

## Task 2: JPA And Jackson Hard Gates

**Files:**

- Create: `cap4k-ddd-starter/src/test/kotlin/com/only4/cap4k/ddd/runtime/StrongIdJpaRuntimeTest.kt`
- Create: `cap4k-ddd-starter/src/test/kotlin/com/only4/cap4k/ddd/runtime/StrongIdJacksonRuntimeTest.kt`

- [ ] **Step 1: Write JPA hard-gate test**

Create `cap4k-ddd-starter/src/test/kotlin/com/only4/cap4k/ddd/runtime/StrongIdJpaRuntimeTest.kt`:

```kotlin
package com.only4.cap4k.ddd.runtime

import com.only4.cap4k.ddd.core.domain.id.StrongId
import com.only4.cap4k.ddd.core.domain.id.StrongIds
import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import jakarta.persistence.EmbeddedId
import jakarta.persistence.Entity
import jakarta.persistence.Table
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.io.Serializable

@DataJpaTest
class StrongIdJpaRuntimeTest(
    private val repository: StrongIdJpaRepository,
    private val jdbcTemplate: JdbcTemplate,
) {
    @Test
    fun `hibernate persists and loads aggregate with strong id field`() {
        val id = StrongContentId.new()
        repository.save(StrongContent(id = id, title = "content"))

        val loaded = repository.findById(id).orElseThrow()
        val persistedId = jdbcTemplate.queryForObject(
            """select "value" from "strong_content" where "title" = ?""",
            String::class.java,
            "content",
        )

        assertEquals(id, loaded.id)
        assertEquals(id.value, persistedId)
        assertEquals("content", loaded.title)
    }

    @Embeddable
    class StrongContentId protected constructor() : StrongId, Serializable {
        @Column(name = "value", nullable = false, updatable = false, length = 36)
        override lateinit var value: String
            protected set

        constructor(value: String) : this() {
            this.value = StrongIds.requireUuidV7(value, "StrongContentId")
        }

        companion object {
            fun new(): StrongContentId = StrongContentId(StrongIds.newUuidV7String())
            fun parse(value: String): StrongContentId = StrongContentId(value)
        }

        override fun equals(other: Any?): Boolean =
            this === other || (other is StrongContentId && value == other.value)

        override fun hashCode(): Int = value.hashCode()
        override fun toString(): String = value
    }

    @Entity
    @Table(name = "strong_content")
    class StrongContent(
        id: StrongContentId,
        title: String,
    ) {
        @EmbeddedId
        var id: StrongContentId = id
            internal set

        @Column(name = "title", nullable = false)
        var title: String = title
            internal set
    }

    @Repository
    interface StrongIdJpaRepository : JpaRepository<StrongContent, StrongContentId>

    @SpringBootApplication
    class TestApplication
}
```

- [ ] **Step 2: Run JPA hard gate**

Run:

```powershell
.\gradlew.bat :cap4k-ddd-starter:test --tests "com.only4.cap4k.ddd.runtime.StrongIdJpaRuntimeTest" --rerun-tasks
```

Expected: PASS. This proves the `@Embeddable/@EmbeddedId` route. If it fails, stop and revise the plan. Do not continue with primitive `String id`.

- [ ] **Step 3: Write Jackson hard-gate test**

Create `cap4k-ddd-starter/src/test/kotlin/com/only4/cap4k/ddd/runtime/StrongIdJacksonRuntimeTest.kt`:

```kotlin
package com.only4.cap4k.ddd.runtime

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.only4.cap4k.ddd.core.domain.id.StrongId
import com.only4.cap4k.ddd.core.domain.id.StrongIds
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.test.context.SpringBootTest
import java.io.Serializable

@SpringBootTest(classes = [StrongIdJacksonRuntimeTest.TestApplication::class])
class StrongIdJacksonRuntimeTest(
    @Autowired private val objectMapper: ObjectMapper,
) {
    @Test
    fun `jackson serializes strong id as string`() {
        val id = StrongContentId.new()

        val json = objectMapper.writeValueAsString(Payload(id))

        assertEquals("""{"id":"$id"}""", json)
    }

    @Test
    fun `jackson deserializes strong id through validation`() {
        val id = StrongContentId.new()

        val payload = objectMapper.readValue<Payload>("""{"id":"$id"}""")

        assertEquals(id, payload.id)
    }

    @Test
    fun `jackson rejects non uuid v7 strong id`() {
        assertThrows(Exception::class.java) {
            objectMapper.readValue<Payload>("""{"id":"550e8400-e29b-41d4-a716-446655440000"}""")
        }
    }

    data class Payload(val id: StrongContentId)

    @Embeddable
    class StrongContentId protected constructor() : StrongId, Serializable {
        @Column(name = "value", nullable = false, updatable = false, length = 36)
        override lateinit var value: String
            protected set

        @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
        constructor(value: String) : this() {
            this.value = StrongIds.requireUuidV7(value, "StrongContentId")
        }

        @JsonValue
        fun jsonValue(): String = value

        companion object {
            fun new(): StrongContentId = StrongContentId(StrongIds.newUuidV7String())
        }

        override fun equals(other: Any?): Boolean =
            this === other || (other is StrongContentId && value == other.value)

        override fun hashCode(): Int = value.hashCode()
        override fun toString(): String = value
    }

    @SpringBootApplication
    class TestApplication
}
```

- [ ] **Step 4: Run Jackson hard gate**

Run:

```powershell
.\gradlew.bat :cap4k-ddd-starter:test --tests "com.only4.cap4k.ddd.runtime.StrongIdJacksonRuntimeTest" --rerun-tasks
```

Expected: PASS. If this fails due to Jackson constructor/value annotation limitations, revise the plan to register a framework-owned generic converter. Do not continue with object-shaped JSON.

- [ ] **Step 5: Commit**

```powershell
git add cap4k-ddd-starter/src/test/kotlin/com/only4/cap4k/ddd/runtime/StrongIdJpaRuntimeTest.kt cap4k-ddd-starter/src/test/kotlin/com/only4/cap4k/ddd/runtime/StrongIdJacksonRuntimeTest.kt
git commit -m "test: prove strong id runtime boundaries"
```

## Task 3: DB Annotation Parsing For `@RefAggregate` And `@RefId`

**Files:**

- Modify: `cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModels.kt`
- Modify: `cap4k-plugin-pipeline-source-db/src/main/kotlin/com/only4/cap4k/plugin/pipeline/source/db/DbColumnAnnotationParser.kt`
- Modify: `cap4k-plugin-pipeline-source-db/src/main/kotlin/com/only4/cap4k/plugin/pipeline/source/db/DbRelationAnnotationParser.kt`
- Modify: `cap4k-plugin-pipeline-source-db/src/main/kotlin/com/only4/cap4k/plugin/pipeline/source/db/DbSchemaSourceProvider.kt`
- Test: `cap4k-plugin-pipeline-source-db/src/test/kotlin/com/only4/cap4k/plugin/pipeline/source/db/DbColumnAnnotationParserTest.kt`
- Test: `cap4k-plugin-pipeline-source-db/src/test/kotlin/com/only4/cap4k/plugin/pipeline/source/db/DbRelationAnnotationParserTest.kt`
- Test: `cap4k-plugin-pipeline-source-db/src/test/kotlin/com/only4/cap4k/plugin/pipeline/source/db/DbSchemaSourceProviderTest.kt`

- [ ] **Step 1: Write parser tests**

Add tests:

```kotlin
@Test
fun `parser extracts reference strong id from column comment`() {
    val metadata = DbColumnAnnotationParser.parse("@RefId=AuthorId;")

    assertEquals("AuthorId", metadata.refId)
}

@Test
fun `parser rejects blank reference strong id`() {
    val error = assertThrows(IllegalArgumentException::class.java) {
        DbColumnAnnotationParser.parse("@RefId=;")
    }

    assertEquals("blank @RefId value is not allowed.", error.message)
}
```

Add relation parser tests:

```kotlin
@Test
fun `parser extracts same context aggregate reference`() {
    val metadata = DbRelationAnnotationParser().parseColumn("@RefAggregate=MediaProcessingTask;")

    assertEquals("MediaProcessingTask", metadata.refAggregate)
    assertEquals(null, metadata.referenceTable)
}

@Test
fun `parser rejects conflicting ref aggregate and jpa reference`() {
    val error = assertThrows(IllegalArgumentException::class.java) {
        DbRelationAnnotationParser().parseColumn("@RefAggregate=MediaProcessingTask;@Reference=media_processing_task;")
    }

    assertEquals("conflicting @RefAggregate and @Reference/@Ref annotations on the same column comment.", error.message)
}
```

- [ ] **Step 2: Run parser tests and verify RED**

Run:

```powershell
.\gradlew.bat :cap4k-plugin-pipeline-source-db:test --tests "*DbColumnAnnotationParserTest" --tests "*DbRelationAnnotationParserTest" --rerun-tasks
```

Expected: FAIL because `refId` and `refAggregate` do not exist.

- [ ] **Step 3: Add snapshot fields and parser implementation**

In `PipelineModels.kt`, add to `DbColumnSnapshot`:

```kotlin
val refAggregate: String? = null,
val refId: String? = null,
```

In `DbColumnAnnotationParser.kt`, add `refId` to `DbColumnAnnotationParseResult` and parse `@RefId`:

```kotlin
val refId = resolveAnnotationValue(
    annotations = annotations,
    primaryName = "REFID",
    displayName = "RefId",
)
```

Reject blank values with:

```kotlin
require(value.isNotBlank()) { "blank @RefId value is not allowed." }
```

In `DbRelationAnnotationParser.kt`, add `refAggregate` to column metadata and parse `@RefAggregate`. Require it does not coexist with `@Reference` or `@Ref`.

In `DbSchemaSourceProvider.kt`, set `DbColumnSnapshot.refAggregate` and `DbColumnSnapshot.refId` from parser metadata.

- [ ] **Step 4: Run source DB tests and verify GREEN**

Run:

```powershell
.\gradlew.bat :cap4k-plugin-pipeline-source-db:test --rerun-tasks
```

Expected: PASS.

- [ ] **Step 5: Commit**

```powershell
git add cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModels.kt cap4k-plugin-pipeline-source-db/src/main/kotlin/com/only4/cap4k/plugin/pipeline/source/db cap4k-plugin-pipeline-source-db/src/test/kotlin/com/only4/cap4k/plugin/pipeline/source/db
git commit -m "feat: parse strong id database annotations"
```

## Task 4: Canonical Strong ID Metadata

**Files:**

- Modify: `cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModels.kt`
- Modify: `cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssembler.kt`
- Modify: `cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/AggregateSpecialFieldPolicyResolver.kt`
- Test: `cap4k-plugin-pipeline-core/src/test/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssemblerTest.kt`

- [ ] **Step 1: Write canonical metadata tests**

Add tests to `DefaultCanonicalAssemblerTest.kt`:

```kotlin
@Test
fun `aggregate root id defaults to strong id metadata`() {
    val result = assembleCanonicalModel(
        dbSchema(
            tables = listOf(
                table(
                    name = "content",
                    comment = "@AggregateRoot=true;",
                    columns = listOf(
                        column("id", kotlinType = "String", primaryKey = true),
                        column("title", kotlinType = "String"),
                    ),
                    primaryKey = listOf("id"),
                )
            )
        )
    )

    val strongId = result.model.strongIds.single { it.typeName == "ContentId" }
    assertEquals("com.acme.demo.domain.aggregates.content", strongId.packageName)
    assertEquals("Content", strongId.ownerAggregateName)
    assertEquals("AGGREGATE_ROOT", strongId.kind.name)
}

@Test
fun `ref aggregate resolves to referenced aggregate id type`() {
    val result = assembleCanonicalModel(
        dbSchema(
            tables = listOf(
                table(
                    name = "media_processing_task",
                    comment = "@AggregateRoot=true;",
                    columns = listOf(column("id", kotlinType = "String", primaryKey = true)),
                    primaryKey = listOf("id"),
                ),
                table(
                    name = "content",
                    comment = "@AggregateRoot=true;",
                    columns = listOf(
                        column("id", kotlinType = "String", primaryKey = true),
                        column("media_processing_task_id", kotlinType = "String", refAggregate = "MediaProcessingTask", nullable = true),
                    ),
                    primaryKey = listOf("id"),
                )
            )
        )
    )

    val content = result.model.entities.single { it.name == "Content" }
    assertEquals("MediaProcessingTaskId", content.fields.single { it.name == "mediaProcessingTaskId" }.type)
}

@Test
fun `ref id creates shared reference strong id without aggregate`() {
    val result = assembleCanonicalModel(
        dbSchema(
            tables = listOf(
                table(
                    name = "content",
                    comment = "@AggregateRoot=true;",
                    columns = listOf(
                        column("id", kotlinType = "String", primaryKey = true),
                        column("author_id", kotlinType = "String", refId = "AuthorId"),
                    ),
                    primaryKey = listOf("id"),
                )
            )
        )
    )

    val authorId = result.model.strongIds.single { it.typeName == "AuthorId" }
    assertEquals("com.acme.demo.domain.shared.ids", authorId.packageName)
    assertEquals("REFERENCE", authorId.kind.name)
    assertTrue(result.model.entities.none { it.name == "Author" })
}
```

Use existing helper builders in `DefaultCanonicalAssemblerTest.kt`; if the names differ, adapt the test to the existing local helper names without changing the assertions.

- [ ] **Step 2: Run canonical tests and verify RED**

Run:

```powershell
.\gradlew.bat :cap4k-plugin-pipeline-core:test --tests "com.only4.cap4k.plugin.pipeline.core.DefaultCanonicalAssemblerTest" --rerun-tasks
```

Expected: FAIL because `strongIds`, `refAggregate`, and `refId` canonical behavior do not exist.

- [ ] **Step 3: Add canonical model types**

In `PipelineModels.kt`, add:

```kotlin
enum class StrongIdKind {
    AGGREGATE_ROOT,
    AGGREGATE_REFERENCE,
    REFERENCE,
}

data class StrongIdModel(
    val typeName: String,
    val packageName: String,
    val valueType: String = "String",
    val kind: StrongIdKind,
    val ownerAggregateName: String? = null,
    val ownerAggregatePackageName: String? = null,
)
```

Add to `CanonicalModel`:

```kotlin
val strongIds: List<StrongIdModel> = emptyList(),
```

- [ ] **Step 4: Implement canonical resolution**

In `DefaultCanonicalAssembler.kt`, after entities are built and before final model creation:

```kotlin
val aggregateRootStrongIds = entities
    .filter { it.aggregateRoot }
    .map { entity ->
        StrongIdModel(
            typeName = "${entity.name}Id",
            packageName = entity.packageName,
            kind = StrongIdKind.AGGREGATE_ROOT,
            ownerAggregateName = entity.name,
            ownerAggregatePackageName = entity.packageName,
        )
    }
```

Resolve `@RefAggregate` by looking up the named aggregate root and replacing the field type with `${AggregateName}Id`.

Resolve `@RefId` by creating:

```kotlin
StrongIdModel(
    typeName = refId,
    packageName = "${config.basePackage}.domain.shared.ids",
    kind = StrongIdKind.REFERENCE,
)
```

Fail fast when `@RefAggregate` names no generated aggregate:

```kotlin
requireNotNull(targetAggregate) {
    "@RefAggregate=$refAggregate does not match a generated aggregate root"
}
```

- [ ] **Step 5: Simplify default ID policy**

In `AggregateSpecialFieldPolicyResolver.kt`, remove default strategy branching from the generated default path. Aggregate root IDs should be `CREATE_ONLY` Strong IDs. Do not emit `AggregateIdPolicyControl` for primitive `uuid7` by default.

Keep old strategy parsing only if needed by existing tests during transition, but no generated default should create `@ApplicationSideId`.

- [ ] **Step 6: Run canonical tests and verify GREEN**

Run:

```powershell
.\gradlew.bat :cap4k-plugin-pipeline-core:test --tests "com.only4.cap4k.plugin.pipeline.core.DefaultCanonicalAssemblerTest" --rerun-tasks
```

Expected: PASS after updating old assertions that expected default `uuid7` primitive policy.

- [ ] **Step 7: Commit**

```powershell
git add cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModels.kt cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core cap4k-plugin-pipeline-core/src/test/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssemblerTest.kt
git commit -m "feat: model strong ids in canonical aggregate metadata"
```

## Task 5: Generate Strong ID Type Artifacts

**Files:**

- Create: `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/StrongIdArtifactPlanner.kt`
- Modify: `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateArtifactPlanner.kt`
- Create: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/strong_id.kt.peb`
- Test: `cap4k-plugin-pipeline-generator-aggregate/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateArtifactPlannerTest.kt`
- Test: `cap4k-plugin-pipeline-renderer-pebble/src/test/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PebbleArtifactRendererTest.kt`

- [ ] **Step 1: Write artifact planner test**

Add test:

```kotlin
@Test
fun `planner emits aggregate and reference strong id artifacts`() {
    val model = canonicalModel(
        strongIds = listOf(
            StrongIdModel(
                typeName = "ContentId",
                packageName = "com.acme.demo.domain.aggregates.content",
                kind = StrongIdKind.AGGREGATE_ROOT,
                ownerAggregateName = "Content",
                ownerAggregatePackageName = "com.acme.demo.domain.aggregates.content",
            ),
            StrongIdModel(
                typeName = "AuthorId",
                packageName = "com.acme.demo.domain.shared.ids",
                kind = StrongIdKind.REFERENCE,
            ),
        )
    )

    val plan = AggregateArtifactPlanner().plan(projectConfig(), model)

    assertTrue(plan.any { it.templateId == "aggregate/strong_id.kt.peb" && it.typeName == "ContentId" })
    assertTrue(plan.any { it.templateId == "aggregate/strong_id.kt.peb" && it.typeName == "AuthorId" })
}
```

- [ ] **Step 2: Run generator test and verify RED**

Run:

```powershell
.\gradlew.bat :cap4k-plugin-pipeline-generator-aggregate:test --tests "*AggregateArtifactPlannerTest" --rerun-tasks
```

Expected: FAIL because `StrongIdArtifactPlanner` is not registered.

- [ ] **Step 3: Add StrongIdArtifactPlanner**

Create `StrongIdArtifactPlanner.kt`:

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
                ),
            )
        }
    }
}
```

Register it in `AggregateArtifactPlanner`.

- [ ] **Step 4: Add Strong ID template**

Create `strong_id.kt.peb`:

```kotlin
package {{ packageName }}

{{ use("com.fasterxml.jackson.annotation.JsonCreator") -}}
{{ use("com.fasterxml.jackson.annotation.JsonValue") -}}
{{ use("com.only4.cap4k.ddd.core.domain.id.StrongId") -}}
{{ use("com.only4.cap4k.ddd.core.domain.id.StrongIds") -}}
{{ use("java.io.Serializable") -}}
{% for import in imports(imports) -%}
import {{ import }}
{% endfor %}

{{ use("jakarta.persistence.Column") -}}
{{ use("jakarta.persistence.Embeddable") -}}
@Embeddable
class {{ typeName }} protected constructor() : StrongId, Serializable {
    @Column(name = "value", nullable = false, updatable = false, length = 36)
    override lateinit var value: String
        protected set

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    constructor(value: String) : this() {
        this.value = StrongIds.requireUuidV7(value, "{{ typeName }}")
    }

    @JsonValue
    fun jsonValue(): String = value

    override fun toString(): String = value

    companion object {
        fun parse(value: String): {{ typeName }} = {{ typeName }}(value)
        fun new(): {{ typeName }} = {{ typeName }}(StrongIds.newUuidV7String())
    }

    override fun equals(other: Any?): Boolean =
        this === other || (other is {{ typeName }} && value == other.value)

    override fun hashCode(): Int = value.hashCode()
}
```

- [ ] **Step 5: Add renderer test**

Add a Pebble renderer test asserting the output contains:

```kotlin
@Embeddable
class ContentId protected constructor() : StrongId, Serializable
```

And:

```kotlin
this.value = StrongIds.requireUuidV7(value, "ContentId")
```

- [ ] **Step 6: Run generator and renderer tests**

Run:

```powershell
.\gradlew.bat :cap4k-plugin-pipeline-generator-aggregate:test :cap4k-plugin-pipeline-renderer-pebble:test --rerun-tasks
```

Expected: PASS.

- [ ] **Step 7: Commit**

```powershell
git add cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate cap4k-plugin-pipeline-generator-aggregate/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateArtifactPlannerTest.kt cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/strong_id.kt.peb cap4k-plugin-pipeline-renderer-pebble/src/test/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PebbleArtifactRendererTest.kt
git commit -m "feat: generate strong id type artifacts"
```

## Task 6: Render Aggregate Entity, Repository, And Factory With Strong IDs

**Files:**

- Modify: `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/EntityArtifactPlanner.kt`
- Modify: `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/FactoryArtifactPlanner.kt`
- Modify: `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/RepositoryArtifactPlanner.kt`
- Modify: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/entity.kt.peb`
- Modify: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/factory.kt.peb`
- Modify: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/repository.kt.peb`
- Test: `cap4k-plugin-pipeline-generator-aggregate/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateArtifactPlannerTest.kt`
- Test: `cap4k-plugin-pipeline-renderer-pebble/src/test/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PebbleArtifactRendererTest.kt`

- [ ] **Step 1: Write entity/factory/repository planner tests**

Add tests asserting:

```kotlin
assertEquals("ContentId", idField["type"])
assertEquals(null, idField["defaultValue"])
assertEquals(null, idField["applicationSideIdStrategy"])
assertEquals("com.acme.demo.domain.aggregates.content.ContentId", idField["typeRef"])
assertEquals(true, idField["embeddedId"])
```

For factory payload:

```kotlin
assertFalse(payloadFields.any { it["name"] == "id" })
assertEquals("ContentId.new()", factoryContext["idInitializer"])
```

For repository:

```kotlin
assertEquals("ContentId", repository.context["idType"])
assertEquals("com.acme.demo.domain.aggregates.content.ContentId", repository.context["idTypeFqn"])
```

- [ ] **Step 2: Run aggregate generator tests and verify RED**

Run:

```powershell
.\gradlew.bat :cap4k-plugin-pipeline-generator-aggregate:test --tests "*AggregateArtifactPlannerTest" --rerun-tasks
```

Expected: FAIL because entity/repository/factory still use primitive ID policy.

- [ ] **Step 3: Update EntityArtifactPlanner**

Remove `uuid7SentinelDefault`.

When a field has Strong ID metadata, set:

```kotlin
"type" to strongId.typeName,
"fieldType" to strongId.typeName,
"typeRef" to "${strongId.packageName}.${strongId.typeName}",
"defaultValue" to null,
"applicationSideIdStrategy" to null,
"strongId" to true,
```

Add Strong ID imports to the `imports` context. Keep `@Column(length = 36)` for Strong ID fields if the template supports length; otherwise keep normal `@Column` and leave length as a later formatting detail only after tests pass.

- [ ] **Step 4: Update FactoryArtifactPlanner**

Exclude the aggregate root ID from `payloadFields`.

Add context:

```kotlin
"ownIdFieldName" to entity.idField.name,
"ownIdInitializer" to "${entity.name}Id.new()",
"ownIdTypeRef" to "${entity.packageName}.${entity.name}Id",
```

Do not generate `AuthorId.new()` or `MediaProcessingTaskId.new()` for reference fields.

- [ ] **Step 5: Update RepositoryArtifactPlanner**

Use `${entity.name}Id` and its FQN for repository ID type.

- [ ] **Step 6: Update templates**

In `entity.kt.peb`, remove the `ApplicationSideId` import block and annotation rendering from the default path. Render Strong ID aggregate IDs with `@EmbeddedId`, not `@Id @Column`.

In `factory.kt.peb`, render a compile-safe constructor mapping when the entity constructor fields are known:

```kotlin
override fun create(entityPayload: Payload): {{ entityName }} =
    {{ entityName }}(
        {{ ownIdFieldName }} = {{ ownIdInitializer }},
{% for field in constructorPayloadFields %}
        {{ field.name }} = entityPayload.{{ field.name }}{% if not loop.last %},{% endif %}
{% endfor %}
    )
```

If an aggregate has fields that cannot be mapped from payload metadata, fail planning with a clear generator error instead of rendering an uncompilable factory.

In `repository.kt.peb`, import `idTypeFqn` when present.

- [ ] **Step 7: Run renderer tests and verify GREEN**

Run:

```powershell
.\gradlew.bat :cap4k-plugin-pipeline-generator-aggregate:test :cap4k-plugin-pipeline-renderer-pebble:test --rerun-tasks
```

Expected: PASS after updating old tests that asserted `@field:ApplicationSideId(strategy = "uuid7")` and `UUID(0L, 0L)`.

- [ ] **Step 8: Commit**

```powershell
git add cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate cap4k-plugin-pipeline-generator-aggregate/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateArtifactPlannerTest.kt cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate cap4k-plugin-pipeline-renderer-pebble/src/test/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PebbleArtifactRendererTest.kt
git commit -m "feat: render aggregates with strong ids"
```

## Task 7: Design Artifact Type Support

**Files:**

- Modify: design artifact planners/templates under `cap4k-plugin-pipeline-generator-*` and `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/design/`
- Test: `cap4k-plugin-pipeline-renderer-pebble/src/test/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PebbleArtifactRendererTest.kt`
- Test: `cap4k-plugin-pipeline-core/src/test/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssemblerTest.kt`

- [ ] **Step 1: Write design field type resolution tests**

Add tests for generated/authored design fields:

```kotlin
val command = result.model.designElements.single { it.name == "CreateContentCommand" }
assertEquals("AuthorId", command.requestFields.single { it.name == "authorId" }.type)
```

Add renderer assertion:

```kotlin
assertTrue(content.contains("import com.acme.demo.domain.shared.ids.AuthorId"))
assertTrue(content.contains("val authorId: AuthorId"))
```

- [ ] **Step 2: Run tests and verify RED**

Run:

```powershell
.\gradlew.bat :cap4k-plugin-pipeline-core:test :cap4k-plugin-pipeline-renderer-pebble:test --rerun-tasks
```

Expected: FAIL where Strong ID FQN resolution is missing.

- [ ] **Step 3: Implement type registry integration**

When canonical Strong IDs are known, register them as resolvable type bindings so design artifacts can refer to:

```text
ContentId
MediaProcessingTaskId
AuthorId
```

without creating new commands/events automatically.

Ensure template contexts receive imports for Strong ID field types in commands, queries, domain events, integration events, DTOs, validators, handlers, subscribers, and adapters that already exist in the plan.

- [ ] **Step 4: Run tests and verify GREEN**

Run:

```powershell
.\gradlew.bat :cap4k-plugin-pipeline-core:test :cap4k-plugin-pipeline-renderer-pebble:test --rerun-tasks
```

Expected: PASS.

- [ ] **Step 5: Commit**

```powershell
git add cap4k-plugin-pipeline-core cap4k-plugin-pipeline-generator-* cap4k-plugin-pipeline-renderer-pebble
git commit -m "feat: support strong ids in design artifacts"
```

## Task 8: Remove Default Save-Time ID Assignment From Generated Path

**Files:**

- Modify: `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/id/ApplicationSideId.kt`
- Modify: `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/id/IdStrategy.kt`
- Modify: `ddd-core/src/main/kotlin/com/only4/cap4k/ddd/core/domain/id/IdAllocator.kt`
- Modify: `ddd-domain-repo-jpa/src/main/kotlin/com/only4/cap4k/ddd/application/JpaApplicationSideIdSupport.kt`
- Modify: `cap4k-ddd-starter/src/main/kotlin/com/only4/cap4k/ddd/domain/id/IdPolicyAutoConfiguration.kt`
- Modify tests that only assert the old generated default.

- [ ] **Step 1: Search old default usages**

Run:

```powershell
rg -n "ApplicationSideId|IdStrategy|IdAllocator|Uuid7IdStrategy|SnowflakeLongIdStrategy|UUID\\(0L, 0L\\)|snowflake-long|idDefaultStrategy" ddd-core ddd-domain-repo-jpa cap4k-ddd-starter cap4k-plugin-pipeline-* docs skills
```

Expected: list old default-path references to migrate or delete.

- [ ] **Step 2: Delete only default-path dependency**

Keep runtime code temporarily if tests still cover legacy fixtures, but remove all generated-default references:

- generated templates must not import `ApplicationSideId`;
- generated entity fields must not use UUID nil sentinel;
- factory payloads must not require aggregate root ID;
- docs/skills must not teach save-time ID assignment.

If no non-generated supported path remains for `ApplicationSideId`, delete it and related strategy classes in this task. If deletion causes unrelated legacy tests to fail, mark those tests for update in this task rather than restoring the generated default path.

- [ ] **Step 3: Run focused old-default grep**

Run:

```powershell
rg -n "UUID\\(0L, 0L\\)|@field:ApplicationSideId|ApplicationSideId\\(strategy = \"uuid7\"\\)|idDefaultStrategy\\.set\\(\"snowflake-long\"\\)" cap4k-plugin-pipeline-* docs/public/authoring skills
```

Expected: no matches in generated default docs/templates/tests. Matches in historical specs under `docs/superpowers/plans` are acceptable if they are not current guidance.

- [ ] **Step 4: Run focused runtime tests**

Run:

```powershell
.\gradlew.bat :ddd-core:test :ddd-domain-repo-jpa:test :cap4k-ddd-starter:test --tests "*StrongId*" --rerun-tasks
```

Expected: PASS for Strong ID tests. Existing broad starter fixture debt must not be debugged in this task unless it affects Strong ID tests.

- [ ] **Step 5: Commit**

```powershell
git add ddd-core ddd-domain-repo-jpa cap4k-ddd-starter cap4k-plugin-pipeline-* docs skills
git commit -m "refactor: remove generated save-time id assignment path"
```

## Task 9: Generated Compile Sample And Reference Alignment

**Files:**

- Modify: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-compile-sample/`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-relation-compile-sample/`
- Modify: relevant generator functional tests
- Modify reference generated example files only if the reference project is intentionally part of this PR.

- [ ] **Step 1: Update functional sample schema comments**

Use:

```sql
comment on table content is '@AggregateRoot=true;';
comment on column content.id is '@Id;';
comment on column content.author_id is '@RefId=AuthorId;';
comment on column content.media_processing_task_id is '@RefAggregate=MediaProcessingTask;';
```

Remove sample `idDefaultStrategy.set("snowflake-long")` and primitive UUID assumptions.

- [ ] **Step 2: Run functional generation tests and verify RED/GREEN**

Run:

```powershell
.\gradlew.bat :cap4k-plugin-pipeline-gradle:test --rerun-tasks
```

Expected: PASS after sample updates. If first run fails, update generated expected assertions to Strong ID output and rerun until PASS.

- [ ] **Step 3: Commit**

```powershell
git add cap4k-plugin-pipeline-gradle
git commit -m "test: align generation samples with strong ids"
```

## Task 10: Documentation, Skills, And Analysis Sync

**Files:**

- Modify: `docs/public/authoring/advanced/strong-id.md`
- Modify: `docs/public/authoring/generator/input-sources.md`
- Modify: `docs/public/authoring/examples/`
- Modify: `docs/superpowers/analysis/`
- Modify: `skills/cap4k-modeling/`
- Modify: `skills/cap4k-generation/`
- Modify: `skills/cap4k-implementation/`
- Modify: `skills/cap4k-verification/`
- Modify: `skills/cap4k-generated-output-review/`

- [ ] **Step 1: Update public authoring guidance**

Document these examples:

```sql
comment on table content is '@AggregateRoot=true;';
comment on column content.id is '@Id;';
comment on column content.author_id is '@RefId=AuthorId;';
comment on column content.media_processing_task_id is '@RefAggregate=MediaProcessingTask;';
```

And generated meaning:

```kotlin
class Content(
    val id: ContentId,
    val authorId: AuthorId,
    val mediaProcessingTaskId: MediaProcessingTaskId?,
)
```

- [ ] **Step 2: Remove old default guidance**

Run:

```powershell
rg -n "@GeneratedValue=uuid7|snowflake-long|UUID\\(0L, 0L\\)|ApplicationSideId|idDefaultStrategy" docs/public/authoring skills docs/superpowers/analysis
```

Update every current guidance hit so it no longer presents those as the default path. Historical specs/plans are outside this step.

- [ ] **Step 3: Update skills**

Rules to add:

```text
Generated aggregate root IDs are Strong ID types by default.
Use @RefAggregate for same-context aggregate references.
Use @RefId for current-context reference identities that map external concepts.
Do not ask users to generate IDs in UoW save-time paths.
Do not model cross-context UserId directly inside content if the local language is AuthorId.
```

- [ ] **Step 4: Run docs grep verification**

Run:

```powershell
rg -n "@GeneratedValue=uuid7|snowflake-long|UUID\\(0L, 0L\\)|ApplicationSideId|idDefaultStrategy" docs/public/authoring skills docs/superpowers/analysis
```

Expected: no current-guidance hits. If a hit remains as a warning or legacy note, it must explicitly say it is not the 1.0 default path.

- [ ] **Step 5: Commit**

```powershell
git add docs/public/authoring docs/superpowers/analysis skills
git commit -m "docs: align guidance with strong id default"
```

## Task 11: Final Verification And PR Prep

**Files:**

- No planned source edits unless verification exposes defects.

- [ ] **Step 1: Run focused module tests**

Run:

```powershell
.\gradlew.bat :ddd-core:test :cap4k-plugin-pipeline-source-db:test :cap4k-plugin-pipeline-core:test :cap4k-plugin-pipeline-generator-aggregate:test :cap4k-plugin-pipeline-renderer-pebble:test :cap4k-plugin-pipeline-gradle:test --rerun-tasks
```

Expected: PASS.

- [ ] **Step 2: Run Strong ID runtime tests**

Run:

```powershell
.\gradlew.bat :cap4k-ddd-starter:test --tests "*StrongId*" --rerun-tasks
```

Expected: PASS.

- [ ] **Step 3: Run final grep audit**

Run:

```powershell
rg -n "UUID\\(0L, 0L\\)|@field:ApplicationSideId|ApplicationSideId\\(strategy = \"uuid7\"\\)|idDefaultStrategy\\.set\\(\"snowflake-long\"\\)|@GeneratedValue=uuid7" cap4k-plugin-pipeline-* docs/public/authoring skills docs/superpowers/analysis
```

Expected: no default-path hits.

- [ ] **Step 4: Check git status**

Run:

```powershell
git status --short --branch
git -C C:\Users\LD_moxeii\Documents\code\only-workspace\cap4k status --short --branch
```

Expected: implementation worktree clean after commits; `cap4k/master` clean.

- [ ] **Step 5: Push branch and open PR**

Run:

```powershell
git push -u origin feature/strong-id-1-0
gh pr create --base master --head feature/strong-id-1-0 --title "feat: make strong id the default identity model" --body "Implements Strong ID 1.0 default identity model from docs/superpowers/specs/2026-05-22-cap4k-strong-id-1-0-design.md."
```

Expected: PR opened against `master`, not `publish/maven-central`.

## Self-Review

Spec coverage:

- Core Strong ID helper: Task 1.
- JPA/Jackson runtime hard gates: Task 2.
- `@RefAggregate` / `@RefId` DB annotations: Task 3.
- Canonical metadata and default policy: Task 4.
- Strong ID file generation and package placement: Task 5.
- Entity/repository/factory rendering: Task 6.
- Commands/events/DTO type support without over-generation: Task 7.
- Save-time ID assignment removal from generated default path: Task 8.
- Compile samples/reference alignment: Task 9.
- Docs/analysis/skills sync: Task 10.
- Verification and PR prep: Task 11.

Placeholder scan:

- No incomplete marker wording is used as an implementation requirement.
- Factory rendering must either produce a compile-safe constructor mapping or fail planning with a clear error.

Type consistency:

- Generated aggregate ID examples use `ContentId`.
- Same-context reference examples use `MediaProcessingTaskId`.
- Cross-context reference examples use `AuthorId`.
- Default backing value remains `String` UUIDv7.

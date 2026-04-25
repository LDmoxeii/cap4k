# Cap4k Generate Known Bug Parity Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the `only-danmaku-next` stage-2 generated classes stop exhibiting the known generator bugs found during manual review: weak repository/schema output, wrong unique-validation semantics, and unreadable design-template whitespace.

**Architecture:** Preserve the new pipeline entrypoint and provider layout, but raise the default `ddd-default` generator contract to match the old plugin's practical semantics. The fix is split into canonical model correctness, aggregate-family parity, design-family formatting/request contract, and end-to-end verification against an `only-danmaku-next`-style fixture.

**Tech Stack:** Kotlin, Gradle plugin code, Pebble templates, JUnit 5, Gradle TestKit, H2-backed db source

---

## Spec

### Known Bugs To Eliminate

The following generated files from `only-danmaku-next` are the review baseline for this iteration:

- `only-danmaku-adapter/src/main/kotlin/edu/only4/danmaku/adapter/domain/repositories/UserMessageRepository.kt`
- `only-danmaku-adapter/src/main/kotlin/edu/only4/danmaku/adapter/portal/api/payload/message/CreateUserMessagePayload.kt`
- `only-danmaku-adapter/src/main/kotlin/edu/only4/danmaku/adapter/queries/user_message/unique/UniqueUserMessageMessageKeyQryHandler.kt`
- `only-danmaku-application/src/main/kotlin/edu/only4/danmaku/application/commands/message/create/CreateUserMessageCmd.kt`
- `only-danmaku-application/src/main/kotlin/edu/only4/danmaku/application/distributed/clients/message/delivery/PublishUserMessageCli.kt`
- `only-danmaku-application/src/main/kotlin/edu/only4/danmaku/application/queries/message/read/FindUserMessageQry.kt`
- `only-danmaku-application/src/main/kotlin/edu/only4/danmaku/application/validators/user_message/unique/UniqueUserMessageMessageKey.kt`
- `only-danmaku-domain/src/main/kotlin/edu/only4/danmaku/domain/_share/meta/user_message/SUserMessage.kt`

After this iteration:

- `UserMessageRepository` must no longer be an empty interface. It must extend `JpaRepository<UserMessage, Long>` and `JpaSpecificationExecutor<UserMessage>`, be annotated with `@Repository`, and expose the nested `UserMessageJpaRepositoryAdapter` annotated as a Cap4k aggregate repository.
- `SUserMessage` must no longer be a constants-only object. It must be a criteria schema class with `PROPERTY_NAMES`, `props`, `specify(...)`, `predicateById(...)`, `predicate(...)`, typed `Field<T>` members, and access to the shared schema-base DSL.
- The shared schema-base DSL must be generated once into `domain._share.meta.Schema.kt`. It must provide `SchemaSpecification`, `PredicateBuilder`, `OrderBuilder`, `ExpressionBuilder`, `SubqueryConfigure`, `Field<T>`, `and(...)`, `or(...)`, and predicate helpers needed by `SUserMessage`.
- DB-derived Kotlin property names must be lower-camel logical names, not raw snake_case column names. For `user_message`, generated Kotlin fields must include `messageKey` and `roomId`, while JPA `@Column(name = "...")` still uses `message_key` and `room_id`.
- `UniqueUserMessageMessageKey` must be a class-level business-input validator, not a validator tied to `UniqueUserMessageMessageKeyQry.Request`. It must implement `ConstraintValidator<UniqueUserMessageMessageKey, Any>`, read configured property names via Kotlin reflection, send `UniqueUserMessageMessageKeyQry.Request` through `Mediator.queries.send(...)`, and return `!result.exists`.
- `UniqueUserMessageMessageKeyQryHandler` must perform a real uniqueness lookup. For the default JPA baseline, it must inject `UserMessageRepository` and call `repository.exists(SUserMessage.specify { ... })`. It must not return a hard-coded `exists = false`.
- `CreateUserMessageCmd`, `PublishUserMessageCli`, `FindUserMessageQry`, and `CreateUserMessagePayload` must not contain repeated blank lines or excessive empty lines between members. The generated shape must be readable without manual formatting.
- `CreateUserMessageCmd.Request`, `PublishUserMessageCli.Request`, and `FindUserMessageQry.Request` must implement `RequestParam<Response>` consistently where request dispatch through Cap4k is expected.
- `CreateUserMessagePayload` must keep nested payload types readable. For the known sample it must produce `Body` and `Receipt` nested data classes without blank-line explosions or broken indentation.
- The final `only-danmaku-next` verification must run `cap4kPlan cap4kGenerate build` and leave the working tree clean after committing generated changes.

### Explicit Boundaries

- Do not introduce a Jimmer-only default. The old plugin's `unique_query_handler.kt.peb` used Jimmer in the `only-danmuku` project, but Jimmer is not part of the unified baseline yet. The new default unique handler must use JPA repository + schema DSL so `only-danmaku-next` remains a minimal bootstrap/codegen verification project.
- Do not move `flow` or `drawingBoard` back under `cap4kGenerate`. This plan only touches source generation quality for aggregate and design families.
- Do not change the source-layer contract. `designJson` stays a single source. `db` stays a single source. This iteration fixes canonical transformation and renderer output.
- Do not add compatibility aliases for old generator ids. The pipeline is still pre-public-use for this path; keep the current normalized provider ids.

---

## File Map

### Canonical Model And Assembly

- Modify: `cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModels.kt`
- Modify: `cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssembler.kt`
- Modify: `cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/AggregateJpaControlInference.kt`
- Modify: `cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/AggregatePersistenceFieldBehaviorInference.kt`
- Modify: `cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/AggregatePersistenceProviderInference.kt`
- Modify: `cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/AggregateIdGeneratorInference.kt`
- Test: `cap4k-plugin-pipeline-core/src/test/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssemblerTest.kt`

### Aggregate Planner And Templates

- Modify: `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateArtifactPlanner.kt`
- Create: `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/SchemaBaseArtifactPlanner.kt`
- Modify: `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/SchemaArtifactPlanner.kt`
- Modify: `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/RepositoryArtifactPlanner.kt`
- Modify: `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateUniqueConstraintPlanning.kt`
- Modify: `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/UniqueValidatorArtifactPlanner.kt`
- Modify: `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/UniqueQueryHandlerArtifactPlanner.kt`
- Modify: `cap4k-plugin-pipeline-generator-aggregate/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateArtifactPlannerTest.kt`
- Create: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/schema_base.kt.peb`
- Modify: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/schema.kt.peb`
- Modify: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/repository.kt.peb`
- Modify: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/unique_validator.kt.peb`
- Modify: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/unique_query_handler.kt.peb`
- Test: `cap4k-plugin-pipeline-renderer-pebble/src/test/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PebbleArtifactRendererTest.kt`

### Design Templates

- Modify: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/design/command.kt.peb`
- Modify: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/design/query.kt.peb`
- Modify: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/design/query_list.kt.peb`
- Modify: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/design/query_page.kt.peb`
- Modify: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/design/client.kt.peb`
- Modify: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/design/api_payload.kt.peb`
- Test: `cap4k-plugin-pipeline-renderer-pebble/src/test/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PebbleArtifactRendererTest.kt`

### Bootstrap / Compile Dependencies

- Modify: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default-bootstrap/bootstrap/module/domain-build.gradle.kts.peb`
- Modify: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default-bootstrap/bootstrap/module/adapter-build.gradle.kts.peb`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-compile-sample/demo-domain/build.gradle.kts`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/aggregate-compile-sample/demo-adapter/build.gradle.kts`

### Functional Verification

- Modify: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginFunctionalTest.kt`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/known-bug-parity-sample/settings.gradle.kts`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/known-bug-parity-sample/build.gradle.kts`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/known-bug-parity-sample/demo-domain/build.gradle.kts`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/known-bug-parity-sample/demo-application/build.gradle.kts`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/known-bug-parity-sample/demo-adapter/build.gradle.kts`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/known-bug-parity-sample/design/design.json`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/known-bug-parity-sample/schema.sql`

---

## Task 1: Canonical DB Field Naming Contract

**Files:**
- Modify: `cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModels.kt`
- Modify: `cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssembler.kt`
- Modify: `cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/AggregateJpaControlInference.kt`
- Modify: `cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/AggregatePersistenceFieldBehaviorInference.kt`
- Modify: `cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/AggregatePersistenceProviderInference.kt`
- Modify: `cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/AggregateIdGeneratorInference.kt`
- Test: `cap4k-plugin-pipeline-core/src/test/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssemblerTest.kt`

- [ ] **Step 1: Write the failing canonical test**

Add this test to `DefaultCanonicalAssemblerTest`:

```kotlin
@Test
fun `db columns become lower camel kotlin fields while JPA metadata keeps original column names`() {
    val assembler = DefaultCanonicalAssembler()

    val model = assembler.assemble(
        config = baseConfig(),
        snapshots = listOf(
            DbSchemaSnapshot(
                tables = listOf(
                    DbTableSnapshot(
                        tableName = "user_message",
                        comment = "user message",
                        primaryKey = listOf("id"),
                        uniqueConstraints = listOf(listOf("message_key")),
                        columns = listOf(
                            DbColumnSnapshot("id", "bigint", "Long", false, isPrimaryKey = true),
                            DbColumnSnapshot("message_key", "varchar", "String", false),
                            DbColumnSnapshot("room_id", "varchar", "String", false),
                            DbColumnSnapshot("published", "boolean", "Boolean", true),
                        ),
                    )
                )
            )
        ),
    ).model

    val entity = model.entities.single()
    assertEquals(listOf("id", "messageKey", "roomId", "published"), entity.fields.map { it.name })
    assertEquals("messageKey", entity.fields.single { it.columnName == "message_key" }.name)
    assertEquals("roomId", entity.fields.single { it.columnName == "room_id" }.name)
    assertEquals("id", entity.idField.name)
    assertEquals("id", entity.idField.columnName)

    val jpa = model.aggregateEntityJpa.single()
    assertEquals(
        listOf("id" to "id", "messageKey" to "message_key", "roomId" to "room_id", "published" to "published"),
        jpa.columns.map { it.fieldName to it.columnName },
    )
}
```

- [ ] **Step 2: Run the red test**

Run:

```powershell
.\gradlew.bat --no-daemon :cap4k-plugin-pipeline-core:test --tests "*DefaultCanonicalAssemblerTest.db columns become lower camel kotlin fields while JPA metadata keeps original column names"
```

Expected: FAIL because `FieldModel` has no `columnName` and current fields use raw `message_key` names.

- [ ] **Step 3: Add a source-column property to FieldModel**

Change `FieldModel` in `PipelineModels.kt` to:

```kotlin
data class FieldModel(
    val name: String,
    val type: String,
    val nullable: Boolean = false,
    val defaultValue: String? = null,
    val typeBinding: String? = null,
    val enumItems: List<EnumItemModel> = emptyList(),
    val columnName: String? = null,
)
```

- [ ] **Step 4: Convert DB column names to lowerCamel in canonical assembly**

In `DefaultCanonicalAssembler`, add a private helper near the companion object:

```kotlin
private fun lowerCamelIdentifier(value: String): String {
    val parts = value.trim()
        .split(Regex("[^A-Za-z0-9]+"))
        .filter { it.isNotEmpty() }
    if (parts.isEmpty()) return value

    val head = parts.first().lowercase(Locale.ROOT)
    val tail = parts.drop(1).joinToString("") { token ->
        token.lowercase(Locale.ROOT).replaceFirstChar { it.titlecase(Locale.ROOT) }
    }
    return head + tail
}
```

Change DB field assembly from `name = it.name` to:

```kotlin
name = lowerCamelIdentifier(it.name),
columnName = it.name,
```

Change primary-key lookup from:

```kotlin
val idField = fields.first { it.name == table.primaryKey.first() }
```

to:

```kotlin
val primaryKeyColumn = table.primaryKey.first()
val idField = fields.first { (it.columnName ?: it.name).equals(primaryKeyColumn, ignoreCase = true) }
```

- [ ] **Step 5: Update aggregate inference to resolve by columnName**

In every aggregate inference class listed in this task, replace raw field-name matching against `DbColumnSnapshot.name` with:

```kotlin
val fieldColumnName = field.columnName ?: field.name
```

Then use `fieldColumnName.lowercase(Locale.ROOT)` when looking up db column metadata. Keep emitted control `fieldName` values as the logical Kotlin property name.

- [ ] **Step 6: Run the canonical tests**

Run:

```powershell
.\gradlew.bat --no-daemon :cap4k-plugin-pipeline-core:test --tests "*DefaultCanonicalAssemblerTest"
```

Expected: PASS.

- [ ] **Step 7: Commit**

```powershell
git add cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModels.kt cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core cap4k-plugin-pipeline-core/src/test/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssemblerTest.kt
git commit -m "fix: preserve db column names behind camel case fields"
```

---

## Task 2: Aggregate Repository And Schema Parity

**Files:**
- Modify: `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateArtifactPlanner.kt`
- Create: `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/SchemaBaseArtifactPlanner.kt`
- Modify: `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/SchemaArtifactPlanner.kt`
- Modify: `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/RepositoryArtifactPlanner.kt`
- Modify: `cap4k-plugin-pipeline-generator-aggregate/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateArtifactPlannerTest.kt`
- Create: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/schema_base.kt.peb`
- Modify: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/schema.kt.peb`
- Modify: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/repository.kt.peb`
- Test: `cap4k-plugin-pipeline-renderer-pebble/src/test/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PebbleArtifactRendererTest.kt`

- [ ] **Step 1: Write failing planner tests for schema base and repository context**

Add tests to `AggregateArtifactPlannerTest`:

```kotlin
@Test
fun `aggregate planner emits schema base before entity schemas`() {
    val entity = EntityModel(
        name = "UserMessage",
        packageName = "com.acme.demo.domain.aggregates.user_message",
        tableName = "user_message",
        comment = "user message",
        fields = listOf(FieldModel("id", "Long", columnName = "id")),
        idField = FieldModel("id", "Long", columnName = "id"),
    )

    val plan = AggregateArtifactPlanner().plan(
        aggregateConfig(),
        CanonicalModel(
            entities = listOf(entity),
            schemas = listOf(
                SchemaModel(
                    name = "SUserMessage",
                    packageName = "com.acme.demo.domain._share.meta.user_message",
                    entityName = "UserMessage",
                    comment = "user message",
                    fields = entity.fields,
                )
            ),
            aggregateEntityJpa = listOf(defaultAggregateEntityJpa(entity)),
        ),
    )

    val schemaBase = plan.single { it.templateId == "aggregate/schema_base.kt.peb" }
    val schema = plan.single { it.templateId == "aggregate/schema.kt.peb" }

    assertEquals("demo-domain/src/main/kotlin/com/acme/demo/domain/_share/meta/Schema.kt", schemaBase.outputPath)
    assertEquals("com.acme.demo.domain._share.meta", schemaBase.context["packageName"])
    assertEquals("com.acme.demo.domain._share.meta", schema.context["schemaBasePackage"])
    assertTrue(plan.indexOf(schemaBase) < plan.indexOf(schema))
}

@Test
fun `repository planner exposes JPA repository adapter render model`() {
    val plan = AggregateArtifactPlanner().plan(
        aggregateConfig(),
        CanonicalModel(
            repositories = listOf(
                RepositoryModel(
                    name = "UserMessageRepository",
                    packageName = "com.acme.demo.adapter.domain.repositories",
                    entityName = "UserMessage",
                    idType = "Long",
                )
            ),
            entities = listOf(
                EntityModel(
                    name = "UserMessage",
                    packageName = "com.acme.demo.domain.aggregates.user_message",
                    tableName = "user_message",
                    comment = "user message",
                    fields = listOf(FieldModel("id", "Long", columnName = "id")),
                    idField = FieldModel("id", "Long", columnName = "id"),
                )
            ),
        ),
    )

    val repository = plan.single { it.templateId == "aggregate/repository.kt.peb" }

    assertEquals("UserMessageRepository", repository.context["typeName"])
    assertEquals("UserMessage", repository.context["entityName"])
    assertEquals("com.acme.demo.domain.aggregates.user_message.UserMessage", repository.context["entityTypeFqn"])
    assertEquals("Long", repository.context["idType"])
    assertEquals(false, repository.context["supportQuerydsl"])
}
```

- [ ] **Step 2: Run the red planner tests**

Run:

```powershell
.\gradlew.bat --no-daemon :cap4k-plugin-pipeline-generator-aggregate:test --tests "*AggregateArtifactPlannerTest.aggregate planner emits schema base before entity schemas" --tests "*AggregateArtifactPlannerTest.repository planner exposes JPA repository adapter render model"
```

Expected: FAIL because schema base is not planned and repository context lacks `entityTypeFqn` / `supportQuerydsl`.

- [ ] **Step 3: Implement SchemaBaseArtifactPlanner**

Create `SchemaBaseArtifactPlanner.kt`:

```kotlin
package com.only4.cap4k.plugin.pipeline.generator.aggregate

import com.only4.cap4k.plugin.pipeline.api.ArtifactPlanItem
import com.only4.cap4k.plugin.pipeline.api.CanonicalModel
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig

internal class SchemaBaseArtifactPlanner : AggregateArtifactFamilyPlanner {
    override fun plan(config: ProjectConfig, model: CanonicalModel): List<ArtifactPlanItem> {
        if (model.schemas.isEmpty()) return emptyList()

        val domainRoot = requireRelativeModule(config, "domain")
        val packageName = "${config.basePackage}.domain._share.meta"

        return listOf(
            ArtifactPlanItem(
                generatorId = "aggregate",
                moduleRole = "domain",
                templateId = "aggregate/schema_base.kt.peb",
                outputPath = "$domainRoot/src/main/kotlin/${packageName.replace(".", "/")}/Schema.kt",
                context = mapOf(
                    "packageName" to packageName,
                    "typeName" to "Schema",
                ),
                conflictPolicy = config.templates.conflictPolicy,
            )
        )
    }
}
```

Register it first in `AggregateArtifactPlanner.delegates`.

- [ ] **Step 4: Expand SchemaArtifactPlanner context**

Add these context values for `aggregate/schema.kt.peb`:

```kotlin
"schemaBasePackage" to "${config.basePackage}.domain._share.meta",
"entityTypeFqn" to entityTypeFqn,
"aggregateTypeFqn" to derivedTypeReferences.wrapperFqn(schema.entityName).orEmpty(),
"isAggregateRoot" to true,
"generateAggregate" to true,
"repositorySupportQuerydsl" to false,
```

For fields, emit both logical property name and source column name:

```kotlin
"name" to field.name,
"fieldName" to field.name,
"columnName" to (field.columnName ?: field.name),
"fieldType" to planning.resolveFieldType(ownerPackage ?: schema.packageName, field),
"type" to planning.resolveFieldType(ownerPackage ?: schema.packageName, field),
```

- [ ] **Step 5: Expand RepositoryArtifactPlanner context**

Resolve the entity package from `model.entities` and add:

```kotlin
"entityTypeFqn" to "${entity.packageName}.${repository.entityName}",
"aggregateName" to repository.entityName,
"supportQuerydsl" to false,
```

- [ ] **Step 6: Port repository and schema templates**

Use the old plugin templates as the source of truth:

- Source: `cap4k-plugin-codegen/src/main/resources/templates/repository.kt.peb`
- Source: `cap4k-plugin-codegen/src/main/resources/templates/schema.kt.peb`
- Source: `cap4k-plugin-codegen/src/main/resources/templates/schema_base.kt.peb`

Adapt variable names to new context:

- Old `basePackage + templatePackage + package` becomes `packageName`.
- Old `Repository` becomes `typeName`.
- Old `Aggregate` becomes `entityName`.
- Old `AggregateType` becomes `entityTypeFqn`.
- Old `IdentityType` becomes `idType`.
- Old `supportQuerydsl` stays `supportQuerydsl`.
- Old `Schema` becomes `typeName`.
- Old `Entity` becomes `entityName`.
- Old `fields` entries use `fieldName` and `fieldType`.

Keep imports expressed through `use(...)` plus `{% for import in imports(imports) %}` as required by the current template import contract.

- [ ] **Step 7: Write renderer regression tests**

Add tests to `PebbleArtifactRendererTest` that render repository and schema directly. Required assertions:

```kotlin
assertTrue(repositoryContent.contains("@Repository"))
assertTrue(repositoryContent.contains("interface UserMessageRepository : JpaRepository<UserMessage, Long>, JpaSpecificationExecutor<UserMessage>"))
assertTrue(repositoryContent.contains("class UserMessageJpaRepositoryAdapter("))
assertTrue(repositoryContent.contains("AbstractJpaRepository<UserMessage, Long>"))

assertTrue(schemaBaseContent.contains("fun interface SchemaSpecification<E, S>"))
assertTrue(schemaBaseContent.contains("class Field<T>"))
assertTrue(schemaContent.contains("class SUserMessage("))
assertTrue(schemaContent.contains("fun specify(builder: PredicateBuilder<SUserMessage>): Specification<UserMessage>"))
assertTrue(schemaContent.contains("fun predicateById(id: Any): AggregatePredicate<AggUserMessage, UserMessage>"))
assertTrue(schemaContent.contains("val messageKey: Field<String>"))
assertFalse(schemaContent.contains("val message_key"))
```

- [ ] **Step 8: Run aggregate planner and renderer tests**

Run:

```powershell
.\gradlew.bat --no-daemon :cap4k-plugin-pipeline-generator-aggregate:test :cap4k-plugin-pipeline-renderer-pebble:test --tests "*AggregateArtifactPlannerTest" --tests "*PebbleArtifactRendererTest"
```

Expected: PASS.

- [ ] **Step 9: Commit**

```powershell
git add cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate cap4k-plugin-pipeline-generator-aggregate/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateArtifactPlannerTest.kt cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate cap4k-plugin-pipeline-renderer-pebble/src/test/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PebbleArtifactRendererTest.kt
git commit -m "fix: restore aggregate repository and schema defaults"
```

---

## Task 3: Unique Constraint Validator And Handler Semantics

**Files:**
- Modify: `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateUniqueConstraintPlanning.kt`
- Modify: `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/UniqueValidatorArtifactPlanner.kt`
- Modify: `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/UniqueQueryHandlerArtifactPlanner.kt`
- Modify: `cap4k-plugin-pipeline-generator-aggregate/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateArtifactPlannerTest.kt`
- Modify: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/unique_validator.kt.peb`
- Modify: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/unique_query_handler.kt.peb`
- Test: `cap4k-plugin-pipeline-renderer-pebble/src/test/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PebbleArtifactRendererTest.kt`

- [ ] **Step 1: Write failing planner tests for unique context**

Add this test to `AggregateArtifactPlannerTest`:

```kotlin
@Test
fun `unique planners expose business validator and repository backed handler context`() {
    val entity = EntityModel(
        name = "UserMessage",
        packageName = "com.acme.demo.domain.aggregates.user_message",
        tableName = "user_message",
        comment = "user message",
        fields = listOf(
            FieldModel("id", "Long", columnName = "id"),
            FieldModel("messageKey", "String", columnName = "message_key"),
        ),
        idField = FieldModel("id", "Long", columnName = "id"),
        uniqueConstraints = listOf(listOf("message_key")),
    )

    val plan = AggregateArtifactPlanner().plan(
        aggregateConfig(),
        CanonicalModel(
            entities = listOf(entity),
            schemas = listOf(
                SchemaModel(
                    name = "SUserMessage",
                    packageName = "com.acme.demo.domain._share.meta.user_message",
                    entityName = "UserMessage",
                    comment = "user message",
                    fields = entity.fields,
                )
            ),
            repositories = listOf(
                RepositoryModel(
                    name = "UserMessageRepository",
                    packageName = "com.acme.demo.adapter.domain.repositories",
                    entityName = "UserMessage",
                    idType = "Long",
                )
            ),
            aggregateEntityJpa = listOf(defaultAggregateEntityJpa(entity)),
        ),
    )

    val validator = plan.single { it.templateId == "aggregate/unique_validator.kt.peb" }
    @Suppress("UNCHECKED_CAST")
    val requestProps = validator.context["requestProps"] as List<Map<String, Any?>>
    assertEquals("UniqueUserMessageMessageKey", validator.context["typeName"])
    assertEquals("UniqueUserMessageMessageKeyQry", validator.context["queryTypeName"])
    assertEquals("messageKey", requestProps.single()["name"])
    assertEquals("messageKeyField", requestProps.single()["param"])
    assertEquals("messageKeyProperty", requestProps.single()["varName"])
    assertEquals("userMessageIdField", validator.context["entityIdParam"])
    assertEquals("userMessageId", validator.context["entityIdDefault"])
    assertEquals("userMessageIdProperty", validator.context["entityIdVar"])

    val handler = plan.single { it.templateId == "aggregate/unique_query_handler.kt.peb" }
    assertEquals("UserMessageRepository", handler.context["repositoryTypeName"])
    assertEquals("com.acme.demo.adapter.domain.repositories.UserMessageRepository", handler.context["repositoryTypeFqn"])
    assertEquals("SUserMessage", handler.context["schemaTypeName"])
    assertEquals("com.acme.demo.domain._share.meta.user_message.SUserMessage", handler.context["schemaTypeFqn"])
    assertEquals("id", handler.context["idPropName"])
}
```

- [ ] **Step 2: Run the red planner test**

Run:

```powershell
.\gradlew.bat --no-daemon :cap4k-plugin-pipeline-generator-aggregate:test --tests "*AggregateArtifactPlannerTest.unique planners expose business validator and repository backed handler context"
```

Expected: FAIL because current unique context does not include repository/schema handler metadata and validator context is query-request based.

- [ ] **Step 3: Resolve unique fields by columnName**

In `AggregateUniqueConstraintPlanning.selectConstraintFields`, support both logical field names and source column names:

```kotlin
val fieldsByColumnName = entity.fields
    .filter { it.columnName != null }
    .associateBy { requireNotNull(it.columnName).lowercase(Locale.ROOT) }
```

Resolve each unique constraint column by:

```kotlin
fieldsByName[column.lowercase(Locale.ROOT)]
    ?: fieldsByNormalizedName[uniqueLowerCamel(column).lowercase(Locale.ROOT)]
    ?: fieldsByColumnName[column.lowercase(Locale.ROOT)]
```

When copying the field for the request prop, keep `name = field.name` so the generated request uses `messageKey`, not `message_key`.

- [ ] **Step 4: Expand unique validator context**

In `UniqueValidatorArtifactPlanner`, derive:

```kotlin
val entityCamel = entity.name.replaceFirstChar { it.lowercase() }
```

Add to context:

```kotlin
"requestProps" to selection.requestProps.map { field ->
    mapOf(
        "name" to field.name,
        "type" to field.type.removeSuffix("?"),
        "isString" to (field.type.removeSuffix("?").substringAfterLast(".") == "String"),
        "param" to "${field.name}Field",
        "varName" to "${field.name}Property",
    )
},
"fieldParams" to selection.requestProps.map { field ->
    mapOf(
        "param" to "${field.name}Field",
        "default" to field.name,
    )
},
"entityIdParam" to "${entityCamel}IdField",
"entityIdDefault" to "${entityCamel}Id",
"entityIdVar" to "${entityCamel}IdProperty",
```

- [ ] **Step 5: Expand unique query handler context**

In `UniqueQueryHandlerArtifactPlanner`, resolve repository and schema models:

```kotlin
val repository = model.repositories.single { it.entityName == entity.name }
val schema = model.schemas.single { it.entityName == entity.name }
```

Add to context:

```kotlin
"repositoryTypeName" to repository.name,
"repositoryTypeFqn" to "${repository.packageName}.${repository.name}",
"schemaTypeName" to schema.name,
"schemaTypeFqn" to "${schema.packageName}.${schema.name}",
"entityTypeName" to entity.name,
"entityTypeFqn" to "${entity.packageName}.${entity.name}",
"whereProps" to selection.requestProps.map { it.name },
"idPropName" to entity.idField.name,
```

- [ ] **Step 6: Rewrite unique validator template**

Update `aggregate/unique_validator.kt.peb` so it has these required features:

```kotlin
{{ use("com.only4.cap4k.ddd.core.Mediator") -}}
{{ use("kotlin.reflect.full.memberProperties") -}}

class Validator : ConstraintValidator<{{ typeName }}, Any> {
    ...
    override fun isValid(value: Any?, context: ConstraintValidatorContext): Boolean {
        if (value == null) return true
        val props = value::class.memberProperties.associateBy { it.name }
        ...
        val result = runCatching {
            Mediator.queries.send(
                {{ queryTypeName }}.Request(
                    ...
                )
            )
        }.getOrNull() ?: return false
        return !result.exists
    }
}
```

The rendered `ConstraintValidator` target must be `Any`, not `{{ queryTypeName }}.Request`.

- [ ] **Step 7: Rewrite unique query handler template to use repository exists**

Update `aggregate/unique_query_handler.kt.peb` to generate:

```kotlin
@Service
class {{ typeName }}(
    private val repository: {{ repositoryTypeName }},
) : Query<{{ queryTypeName }}.Request, {{ queryTypeName }}.Response> {

    override fun exec(request: {{ queryTypeName }}.Request): {{ queryTypeName }}.Response {
        val exists = repository.exists(
            {{ schemaTypeName }}.specify { schema ->
                schema.all(
                    schema.{{ whereProps[0] }} eq request.{{ whereProps[0] }},
                    schema.{{ idPropName }} `neq?` request.{{ excludeIdParamName }} ?: schema.{{ idPropName }}.isNotNull(),
                )
            }
        )

        return {{ queryTypeName }}.Response(
            exists = exists
        )
    }
}
```

For multiple unique fields, emit one predicate per `whereProps` item. Keep the optional id-exclusion predicate nullable-safe.

- [ ] **Step 8: Write renderer tests for unique semantics**

Add assertions to `PebbleArtifactRendererTest`:

```kotlin
assertTrue(validatorContent.contains("ConstraintValidator<UniqueUserMessageMessageKey, Any>"))
assertTrue(validatorContent.contains("value::class.memberProperties.associateBy"))
assertTrue(validatorContent.contains("Mediator.queries.send("))
assertTrue(validatorContent.contains("return !result.exists"))
assertFalse(validatorContent.contains("ConstraintValidator<UniqueUserMessageMessageKey, UniqueUserMessageMessageKeyQry.Request>"))

assertTrue(handlerContent.contains("private val repository: UserMessageRepository"))
assertTrue(handlerContent.contains("val exists = repository.exists("))
assertTrue(handlerContent.contains("SUserMessage.specify"))
assertTrue(handlerContent.contains("schema.messageKey eq request.messageKey"))
assertFalse(handlerContent.contains("exists = false"))
```

- [ ] **Step 9: Run unique tests**

Run:

```powershell
.\gradlew.bat --no-daemon :cap4k-plugin-pipeline-generator-aggregate:test :cap4k-plugin-pipeline-renderer-pebble:test --tests "*AggregateArtifactPlannerTest.unique planners expose business validator and repository backed handler context" --tests "*PebbleArtifactRendererTest"
```

Expected: PASS.

- [ ] **Step 10: Commit**

```powershell
git add cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate cap4k-plugin-pipeline-generator-aggregate/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateArtifactPlannerTest.kt cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate cap4k-plugin-pipeline-renderer-pebble/src/test/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PebbleArtifactRendererTest.kt
git commit -m "fix: restore unique constraint validator semantics"
```

---

## Task 4: Design Template Whitespace And Request Contracts

**Files:**
- Modify: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/design/command.kt.peb`
- Modify: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/design/query.kt.peb`
- Modify: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/design/query_list.kt.peb`
- Modify: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/design/query_page.kt.peb`
- Modify: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/design/client.kt.peb`
- Modify: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/design/api_payload.kt.peb`
- Test: `cap4k-plugin-pipeline-renderer-pebble/src/test/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PebbleArtifactRendererTest.kt`

- [ ] **Step 1: Write failing renderer tests for design templates**

Add a helper to `PebbleArtifactRendererTest`:

```kotlin
private fun assertReadableKotlin(content: String) {
    assertFalse(Regex("""(?m)[ \t]+$""").containsMatchIn(content), "Generated Kotlin must not contain trailing whitespace.")
    assertFalse(Regex("""\n{3,}""").containsMatchIn(content), "Generated Kotlin must not contain three or more consecutive newlines.")
}
```

Add a test that renders `design/query.kt.peb`, `design/command.kt.peb`, `design/client.kt.peb`, and `design/api_payload.kt.peb` with the `only-danmaku-next` fields:

```kotlin
@Test
fun `design templates render readable request response classes`() {
    val renderer = PebbleArtifactRenderer(templateResolver = PresetTemplateResolver("ddd-default", emptyList()))
    val config = ProjectConfig(
        basePackage = "edu.only4.danmaku",
        layout = ProjectLayout.MULTI_MODULE,
        modules = emptyMap(),
        sources = emptyMap(),
        generators = emptyMap(),
        templates = TemplateConfig("ddd-default", emptyList(), ConflictPolicy.OVERWRITE),
    )
    val commonFields = mapOf(
        "imports" to emptyList<String>(),
        "requestFields" to listOf(mapOf("name" to "messageKey", "renderedType" to "String", "defaultValue" to null)),
        "requestNestedTypes" to emptyList<Map<String, Any?>>(),
        "responseFields" to listOf(mapOf("name" to "content", "renderedType" to "String", "defaultValue" to null)),
        "responseNestedTypes" to emptyList<Map<String, Any?>>(),
    )

    val rendered = renderer.render(
        planItems = listOf(
            ArtifactPlanItem("design-query", "application", "design/query.kt.peb", "FindUserMessageQry.kt", mapOf("packageName" to "edu.only4.danmaku.application.queries.message.read", "typeName" to "FindUserMessageQry") + commonFields, ConflictPolicy.OVERWRITE),
            ArtifactPlanItem("design-command", "application", "design/command.kt.peb", "CreateUserMessageCmd.kt", mapOf("packageName" to "edu.only4.danmaku.application.commands.message.create", "typeName" to "CreateUserMessageCmd") + commonFields, ConflictPolicy.OVERWRITE),
            ArtifactPlanItem("design-client", "application", "design/client.kt.peb", "PublishUserMessageCli.kt", mapOf("packageName" to "edu.only4.danmaku.application.distributed.clients.message.delivery", "typeName" to "PublishUserMessageCli") + commonFields, ConflictPolicy.OVERWRITE),
            ArtifactPlanItem("design-api-payload", "adapter", "design/api_payload.kt.peb", "CreateUserMessagePayload.kt", mapOf(
                "packageName" to "edu.only4.danmaku.adapter.portal.api.payload.message",
                "typeName" to "CreateUserMessagePayload",
                "imports" to emptyList<String>(),
                "requestFields" to listOf(
                    mapOf("name" to "messageKey", "renderedType" to "String", "defaultValue" to null),
                    mapOf("name" to "body", "renderedType" to "Body?", "defaultValue" to null),
                ),
                "requestNestedTypes" to listOf(mapOf("name" to "Body", "fields" to listOf(mapOf("name" to "content", "renderedType" to "String", "defaultValue" to null)))),
                "responseFields" to listOf(mapOf("name" to "receipt", "renderedType" to "Receipt?", "defaultValue" to null)),
                "responseNestedTypes" to listOf(mapOf("name" to "Receipt", "fields" to listOf(mapOf("name" to "messageKey", "renderedType" to "String", "defaultValue" to null)))),
            ), ConflictPolicy.OVERWRITE),
        ),
        config = config,
    )

    rendered.forEach { assertReadableKotlin(it.content) }
    assertTrue(rendered.single { it.outputPath == "FindUserMessageQry.kt" }.content.contains(") : RequestParam<Response>"))
    assertTrue(rendered.single { it.outputPath == "CreateUserMessageCmd.kt" }.content.contains(") : RequestParam<Response>"))
    assertTrue(rendered.single { it.outputPath == "PublishUserMessageCli.kt" }.content.contains(") : RequestParam<Response>"))
    assertTrue(rendered.single { it.outputPath == "CreateUserMessagePayload.kt" }.content.contains("data class Body("))
    assertTrue(rendered.single { it.outputPath == "CreateUserMessagePayload.kt" }.content.contains("data class Receipt("))
}
```

- [ ] **Step 2: Run the red renderer test**

Run:

```powershell
.\gradlew.bat --no-daemon :cap4k-plugin-pipeline-renderer-pebble:test --tests "*PebbleArtifactRendererTest.design templates render readable request response classes"
```

Expected: FAIL because current design templates emit repeated blank lines and command request does not implement `RequestParam<Response>`.

- [ ] **Step 3: Rewrite design templates with whitespace control**

For each design template, use this structure style:

```peb
package {{ packageName }}
{% for import in imports(imports) %}
import {{ import }}
{% endfor %}
{% if needsRequestParam %}
{{ use("com.only4.cap4k.ddd.core.application.RequestParam") -}}
{% endif %}

object {{ typeName }} {
{% if requestFields.size > 0 %}
    data class Request(
{% for field in requestFields %}
        val {{ field.name }}: {{ type(field) | raw }}{% if field.defaultValue %} = {{ field.defaultValue | raw }}{% endif %}{% if not loop.last %},{% endif %}
{% endfor %}
    ) : RequestParam<Response>
{% else %}
    class Request : RequestParam<Response>
{% endif %}

{% if responseFields.size > 0 %}
    data class Response(
{% for field in responseFields %}
        val {{ field.name }}: {{ type(field) | raw }}{% if field.defaultValue %} = {{ field.defaultValue | raw }}{% endif %}{% if not loop.last %},{% endif %}
{% endfor %}
    )
{% else %}
    class Response
{% endif %}
}
```

Do not literally add `needsRequestParam`; use direct `use(...)` at the top of command/query/client/list/page templates. Keep `api_payload` without `RequestParam`.

- [ ] **Step 4: Keep nested payload types object-scoped**

In `api_payload.kt.peb`, render `requestNestedTypes` and `responseNestedTypes` as object-level nested classes after `Request` and `Response`, not buried inside `Request` / `Response`. The known generated file must contain:

```kotlin
object CreateUserMessagePayload {
    data class Request(
        val messageKey: String,
        val body: Body?
    )

    data class Response(
        val receipt: Receipt?
    )

    data class Body(
        val content: String
    )

    data class Receipt(
        val messageKey: String
    )
}
```

- [ ] **Step 5: Run renderer tests**

Run:

```powershell
.\gradlew.bat --no-daemon :cap4k-plugin-pipeline-renderer-pebble:test --tests "*PebbleArtifactRendererTest"
```

Expected: PASS.

- [ ] **Step 6: Commit**

```powershell
git add cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/design cap4k-plugin-pipeline-renderer-pebble/src/test/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PebbleArtifactRendererTest.kt
git commit -m "fix: clean design template output"
```

---

## Task 5: Compile Dependencies And Known-Bug Functional Fixture

**Files:**
- Modify: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default-bootstrap/bootstrap/module/domain-build.gradle.kts.peb`
- Modify: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default-bootstrap/bootstrap/module/adapter-build.gradle.kts.peb`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginFunctionalTest.kt`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/known-bug-parity-sample/settings.gradle.kts`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/known-bug-parity-sample/build.gradle.kts`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/known-bug-parity-sample/demo-domain/build.gradle.kts`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/known-bug-parity-sample/demo-application/build.gradle.kts`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/known-bug-parity-sample/demo-adapter/build.gradle.kts`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/known-bug-parity-sample/design/design.json`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/known-bug-parity-sample/schema.sql`

- [ ] **Step 1: Update bootstrap module dependency templates**

Add to `domain-build.gradle.kts.peb` dependencies:

```kotlin
dependencies {
    implementation("com.only4:ddd-core:0.5.0-SNAPSHOT")
    implementation("com.only4:ddd-domain-repo-jpa:0.5.0-SNAPSHOT")
    implementation("org.springframework:spring-context")
    implementation("org.springframework.data:spring-data-jpa")
    implementation("org.hibernate.orm:hibernate-core")
}
```

Add to `adapter-build.gradle.kts.peb` dependencies:

```kotlin
dependencies {
    implementation(project(":{{ domainModuleName }}"))
    implementation(project(":{{ applicationModuleName }}"))
    implementation("com.only4:ddd-core:0.5.0-SNAPSHOT")
    implementation("com.only4:ddd-domain-repo-jpa:0.5.0-SNAPSHOT")
    implementation("org.springframework:spring-context")
    implementation("org.springframework.data:spring-data-jpa")
}
```

- [ ] **Step 2: Create the known-bug functional fixture**

Create `known-bug-parity-sample/schema.sql`:

```sql
create table if not exists user_message (
    id bigint primary key,
    message_key varchar(128) not null unique,
    room_id varchar(64) not null,
    content varchar(255) not null,
    published boolean default false
);
```

Create `known-bug-parity-sample/design/design.json` with the same entries as `only-danmaku-next/codegen/design/design.json`.

Create Gradle fixture files using the current aggregate/design fixtures as the base, but enable these generators:

```kotlin
generators {
    aggregate {
        enabled.set(true)
    }
    designCommand {
        enabled.set(true)
    }
    designQuery {
        enabled.set(true)
    }
    designQueryHandler {
        enabled.set(true)
    }
    designClient {
        enabled.set(true)
    }
    designClientHandler {
        enabled.set(true)
    }
    designValidator {
        enabled.set(true)
    }
    designApiPayload {
        enabled.set(true)
    }
    designDomainEvent {
        enabled.set(true)
    }
    designDomainEventHandler {
        enabled.set(true)
    }
}
```

- [ ] **Step 3: Write the functional test**

Add a test to `PipelinePluginFunctionalTest`:

```kotlin
@OptIn(ExperimentalPathApi::class)
@Test
fun `cap4kGenerate removes known only danmaku next generator bugs`() {
    val projectDir = Files.createTempDirectory("pipeline-known-bug-parity")
    FunctionalFixtureSupport.copyFixture(projectDir, "known-bug-parity-sample")

    val result = GradleRunner.create()
        .withProjectDir(projectDir.toFile())
        .withPluginClasspath()
        .withArguments("cap4kGenerate", "build")
        .build()

    assertTrue(result.output.contains("BUILD SUCCESSFUL"))

    val repository = projectDir.resolve("demo-adapter/src/main/kotlin/com/acme/demo/adapter/domain/repositories/UserMessageRepository.kt").readText()
    val schema = projectDir.resolve("demo-domain/src/main/kotlin/com/acme/demo/domain/_share/meta/user_message/SUserMessage.kt").readText()
    val schemaBase = projectDir.resolve("demo-domain/src/main/kotlin/com/acme/demo/domain/_share/meta/Schema.kt").readText()
    val uniqueValidator = projectDir.resolve("demo-application/src/main/kotlin/com/acme/demo/application/validators/user_message/unique/UniqueUserMessageMessageKey.kt").readText()
    val uniqueHandler = projectDir.resolve("demo-adapter/src/main/kotlin/com/acme/demo/adapter/queries/user_message/unique/UniqueUserMessageMessageKeyQryHandler.kt").readText()
    val query = projectDir.resolve("demo-application/src/main/kotlin/com/acme/demo/application/queries/message/read/FindUserMessageQry.kt").readText()
    val command = projectDir.resolve("demo-application/src/main/kotlin/com/acme/demo/application/commands/message/create/CreateUserMessageCmd.kt").readText()
    val client = projectDir.resolve("demo-application/src/main/kotlin/com/acme/demo/application/distributed/clients/message/delivery/PublishUserMessageCli.kt").readText()
    val payload = projectDir.resolve("demo-adapter/src/main/kotlin/com/acme/demo/adapter/portal/api/payload/message/CreateUserMessagePayload.kt").readText()
    val entity = projectDir.resolve("demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/user_message/UserMessage.kt").readText()

    listOf(repository, schema, schemaBase, uniqueValidator, uniqueHandler, query, command, client, payload, entity).forEach { content ->
        assertFalse(Regex("""(?m)[ \t]+$""").containsMatchIn(content))
        assertFalse(Regex("""\n{3,}""").containsMatchIn(content))
    }

    assertTrue(entity.contains("val messageKey: String"))
    assertTrue(entity.contains("@Column(name = \"message_key\")"))
    assertFalse(entity.contains("val message_key"))

    assertTrue(repository.contains("interface UserMessageRepository : JpaRepository<UserMessage, Long>, JpaSpecificationExecutor<UserMessage>"))
    assertTrue(repository.contains("class UserMessageJpaRepositoryAdapter("))

    assertTrue(schemaBase.contains("fun interface SchemaSpecification<E, S>"))
    assertTrue(schemaBase.contains("class Field<T>"))
    assertTrue(schema.contains("class SUserMessage("))
    assertTrue(schema.contains("fun specify(builder: PredicateBuilder<SUserMessage>): Specification<UserMessage>"))
    assertTrue(schema.contains("val messageKey: Field<String>"))
    assertFalse(schema.contains("val message_key"))

    assertTrue(uniqueValidator.contains("ConstraintValidator<UniqueUserMessageMessageKey, Any>"))
    assertTrue(uniqueValidator.contains("Mediator.queries.send("))
    assertTrue(uniqueValidator.contains("return !result.exists"))
    assertFalse(uniqueValidator.contains("ConstraintValidator<UniqueUserMessageMessageKey, UniqueUserMessageMessageKeyQry.Request>"))

    assertTrue(uniqueHandler.contains("private val repository: UserMessageRepository"))
    assertTrue(uniqueHandler.contains("repository.exists("))
    assertTrue(uniqueHandler.contains("SUserMessage.specify"))
    assertFalse(uniqueHandler.contains("exists = false"))

    assertTrue(query.contains(") : RequestParam<Response>"))
    assertTrue(command.contains(") : RequestParam<Response>"))
    assertTrue(client.contains(") : RequestParam<Response>"))
    assertTrue(payload.contains("data class Body("))
    assertTrue(payload.contains("data class Receipt("))
}
```

- [ ] **Step 4: Run the functional test red**

Run before implementation if Task 5 is started independently:

```powershell
.\gradlew.bat --no-daemon :cap4k-plugin-pipeline-gradle:test --tests "*PipelinePluginFunctionalTest.cap4kGenerate removes known only danmaku next generator bugs"
```

Expected before Tasks 1-4: FAIL. Expected after Tasks 1-4 and fixture dependency updates: PASS.

- [ ] **Step 5: Run compile-focused functional tests**

Run:

```powershell
.\gradlew.bat --no-daemon :cap4k-plugin-pipeline-gradle:test --tests "*PipelinePluginFunctionalTest.cap4kGenerate removes known only danmaku next generator bugs" --tests "*PipelinePluginFunctionalTest.cap4kGenerate renders command and query files from repository config" --tests "*PipelinePluginFunctionalTest.cap4kGenerate renders aggregate files from db source"
```

Expected: PASS.

- [ ] **Step 6: Commit**

```powershell
git add cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default-bootstrap/bootstrap/module cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginFunctionalTest.kt cap4k-plugin-pipeline-gradle/src/test/resources/functional/known-bug-parity-sample
git commit -m "test: cover known generate parity bugs"
```

---

## Task 6: Full Verification, Publish, And only-danmaku-next Refresh

**Files:**
- Modify after generation: `C:\Users\LD_moxeii\Documents\code\only-workspace\only-danmaku-next`
- No new cap4k production files unless previous tasks expose a compile-only gap.

- [ ] **Step 1: Run focused cap4k tests**

Run:

```powershell
.\gradlew.bat --no-daemon :cap4k-plugin-pipeline-core:test :cap4k-plugin-pipeline-generator-aggregate:test :cap4k-plugin-pipeline-renderer-pebble:test :cap4k-plugin-pipeline-gradle:test --tests "*DefaultCanonicalAssemblerTest" --tests "*AggregateArtifactPlannerTest" --tests "*PebbleArtifactRendererTest" --tests "*PipelinePluginFunctionalTest.cap4kGenerate removes known only danmaku next generator bugs"
```

Expected: PASS.

- [ ] **Step 2: Run the relevant compile test suite**

Run:

```powershell
.\gradlew.bat --no-daemon :cap4k-plugin-pipeline-gradle:test --tests "*PipelinePluginCompileFunctionalTest" --tests "*PipelinePluginFunctionalTest"
```

Expected: PASS. If this takes longer than the default shell timeout, rerun with a 25-minute timeout in the agent tool.

- [ ] **Step 3: Run git diff whitespace check**

Run:

```powershell
git diff --check
```

Expected: no output.

- [ ] **Step 4: Publish cap4k snapshot**

Run:

```powershell
.\gradlew.bat --no-daemon publish
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Refresh only-danmaku-next**

In `C:\Users\LD_moxeii\Documents\code\only-workspace\only-danmaku-next`, run:

```powershell
.\gradlew.bat --refresh-dependencies --no-configuration-cache --no-build-cache cap4kPlan cap4kGenerate build
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Verify known files manually through grep**

Run from `only-danmaku-next`:

```powershell
rg -n "interface UserMessageRepository : JpaRepository|class UserMessageJpaRepositoryAdapter|class SUserMessage\\(|fun specify\\(|val messageKey: Field<String>|ConstraintValidator<UniqueUserMessageMessageKey, Any>|Mediator\\.queries\\.send|repository\\.exists\\(|exists = false|val message_key|\\n\\n\\n" only-danmaku-domain only-danmaku-application only-danmaku-adapter
```

Expected:

- Must include repository JPA inheritance and adapter.
- Must include `SUserMessage` schema DSL.
- Must include unique validator business-object contract.
- Must include repository-backed unique handler.
- Must not include `exists = false`.
- Must not include `val message_key`.

- [ ] **Step 7: Commit generated only-danmaku-next refresh**

Run:

```powershell
git status --short --branch
git diff --check
git add -A
git diff --cached --check
git commit -m "fix: refresh generated code after parity fixes"
```

Expected: working tree clean after commit.

- [ ] **Step 8: Commit any remaining cap4k verification docs or fixture adjustments**

Run in `cap4k`:

```powershell
git status --short --branch
git diff --check
```

If there are uncommitted cap4k changes from verification-only fixture adjustments, commit them with:

```powershell
git add -A
git diff --cached --check
git commit -m "test: finalize generate parity verification"
```

Expected: cap4k working tree clean.

---

## Review Checklist

- `UserMessageRepository` is no longer a marker interface.
- `SUserMessage` is a schema DSL class, not an object of constants.
- `Schema.kt` is generated once under `domain._share.meta`.
- Entity fields use lowerCamel property names and keep db column names in `@Column`.
- Unique validator targets `Any`, reads business object fields, calls `Mediator.queries.send`, and returns inverse existence.
- Unique query handler performs real repository-backed existence lookup.
- Design output has no repeated blank-line explosions.
- Command/query/client request classes implement `RequestParam<Response>`.
- The known `only-danmaku-next` files regenerate cleanly and compile.
- `git diff --check` passes in both `cap4k` and `only-danmaku-next`.

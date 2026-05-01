# Aggregate Entity Default Projection Implementation Plan

Status: implemented. This is now a historical execution plan; do not treat unchecked boxes as remaining work without re-reviewing current `master`.

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Harden aggregate entity constructor default generation so defaults are projected only from explicit, stable inputs.

**Architecture:** Keep default semantics in Kotlin planner code, not Pebble templates. Add a focused aggregate default projector that receives field type, nullability, raw DB default, and known enum items, then returns a Kotlin-ready constructor default or fails for contradictory enum/scalar defaults.

**Tech Stack:** Kotlin, JUnit 5, Gradle module `:cap4k-plugin-pipeline-generator-aggregate`, existing pipeline API models.

---

## Reference Documents

- Spec: `docs/superpowers/specs/2026-05-01-cap4k-aggregate-entity-default-projection-design.md`
- Roadmap: `docs/superpowers/mainline-roadmap.md`

## File Structure

- Modify: `cap4k-plugin-pipeline-generator-aggregate/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateArtifactPlannerTest.kt`
  - Add planner tests for scalar defaults, nullable priority, unsupported SQL defaults, and enum default diagnostics.
- Modify: `cap4k-plugin-pipeline-generator-aggregate/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateEnumPlanningTest.kt`
  - Add tests for resolving known enum item metadata from shared and local enum sources.
- Create: `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateEntityDefaultProjector.kt`
  - Own all aggregate entity constructor default projection rules.
- Modify: `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateEnumPlanning.kt`
  - Preserve enum item metadata so enum default validation can be deterministic.
- Modify: `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/EntityArtifactPlanner.kt`
  - Replace private default helper functions with `AggregateEntityDefaultProjector`.

No changes should be made to:

- `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/entity.kt.peb`
- `cap4k-plugin-pipeline-source-db`
- `cap4k-plugin-pipeline-core`
- `cap4k-plugin-codegen`
- analysis modules
- bootstrap modules

The source DB provider already carries `COLUMN_DEF` into `DbColumnSnapshot.defaultValue`, and canonical assembly already copies that into `FieldModel.defaultValue`.

## Implementation Notes

The planner currently has private helpers in `EntityArtifactPlanner.kt`:

```kotlin
private fun kotlinConstructorDefaultValue(rawDefaultValue: String?, fieldType: String): String?
```

Those helpers should move into the new projector and be extended. The Pebble template already renders `field.defaultValue` as raw Kotlin, so the template should remain a dumb renderer.

---

### Task 1: Add failing scalar and nullable default projection tests

**Files:**
- Modify: `cap4k-plugin-pipeline-generator-aggregate/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateArtifactPlannerTest.kt`

- [ ] **Step 1: Add scalar default test**

Insert this test after the existing test named `entity planner normalizes database defaults into Kotlin constructor defaults`:

```kotlin
@Test
fun `entity planner projects only explicit scalar defaults and nullable null`() {
    val entity = EntityModel(
        name = "VideoPost",
        packageName = "com.acme.demo.domain.aggregates.video_post",
        tableName = "video_post",
        comment = "video post",
        fields = listOf(
            FieldModel("id", "Long"),
            FieldModel("customerId", "Long"),
            FieldModel("title", "String"),
            FieldModel("description", "String", nullable = true),
            FieldModel("deleted", "Long", defaultValue = "0"),
            FieldModel("sort", "Int", defaultValue = "'1'"),
            FieldModel("enabled", "Boolean", defaultValue = "1"),
            FieldModel("score", "Float", defaultValue = "(.5)"),
            FieldModel("ratio", "Double", defaultValue = "(1.)"),
            FieldModel("displayName", "String", nullable = true, defaultValue = "''"),
            FieldModel("createdAt", "String", defaultValue = "CURRENT_TIMESTAMP"),
        ),
        idField = FieldModel("id", "Long"),
    )

    val plan = AggregateArtifactPlanner().plan(
        aggregateConfig(),
        CanonicalModel(
            entities = listOf(entity),
            aggregateEntityJpa = listOf(defaultAggregateEntityJpa(entity)),
        )
    )

    val entityArtifact = plan.single { it.outputPath.endsWith("/VideoPost.kt") }
    @Suppress("UNCHECKED_CAST")
    val scalarFields = entityArtifact.context["scalarFields"] as List<Map<String, Any?>>

    assertEquals(null, scalarFields.single { it["name"] == "id" }["defaultValue"])
    assertEquals(null, scalarFields.single { it["name"] == "customerId" }["defaultValue"])
    assertEquals(null, scalarFields.single { it["name"] == "title" }["defaultValue"])
    assertEquals("null", scalarFields.single { it["name"] == "description" }["defaultValue"])
    assertEquals("0L", scalarFields.single { it["name"] == "deleted" }["defaultValue"])
    assertEquals("1", scalarFields.single { it["name"] == "sort" }["defaultValue"])
    assertEquals("true", scalarFields.single { it["name"] == "enabled" }["defaultValue"])
    assertEquals("0.5f", scalarFields.single { it["name"] == "score" }["defaultValue"])
    assertEquals("1.0", scalarFields.single { it["name"] == "ratio" }["defaultValue"])
    assertEquals("\"\"", scalarFields.single { it["name"] == "displayName" }["defaultValue"])
    assertEquals(null, scalarFields.single { it["name"] == "createdAt" }["defaultValue"])
}
```

- [ ] **Step 2: Run the focused test and verify it fails**

Run:

```powershell
.\gradlew.bat :cap4k-plugin-pipeline-generator-aggregate:test --tests "com.only4.cap4k.plugin.pipeline.generator.aggregate.AggregateArtifactPlannerTest.entity planner projects only explicit scalar defaults and nullable null"
```

Expected: FAIL. Current behavior returns `null` for nullable fields without DB defaults, returns `0` instead of `0L` for `Long`, and does not normalize quoted numeric defaults for `Int`.

- [ ] **Step 3: Commit the failing test**

```powershell
git add cap4k-plugin-pipeline-generator-aggregate/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateArtifactPlannerTest.kt
git commit -m "test: cover aggregate entity scalar default projection"
```

---

### Task 2: Add scalar projector and wire it into entity planning

**Files:**
- Create: `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateEntityDefaultProjector.kt`
- Modify: `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/EntityArtifactPlanner.kt`
- Modify: `cap4k-plugin-pipeline-generator-aggregate/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateArtifactPlannerTest.kt`

- [ ] **Step 1: Create the projector with scalar and nullable rules**

Create `AggregateEntityDefaultProjector.kt`:

```kotlin
package com.only4.cap4k.plugin.pipeline.generator.aggregate

import com.only4.cap4k.plugin.pipeline.api.EnumItemModel

internal class AggregateEntityDefaultProjector {
    fun project(
        fieldPath: String,
        fieldType: String,
        nullable: Boolean,
        rawDefaultValue: String?,
        enumItems: List<EnumItemModel>,
    ): String? {
        val normalized = rawDefaultValue?.let(::normalizeDefaultLiteral)
        if (normalized == null) return if (nullable) "null" else null
        if (normalized.equals("null", ignoreCase = true)) {
            require(nullable) { "database default NULL cannot be projected to non-null aggregate field $fieldPath" }
            return "null"
        }
        if (isSqlExpression(normalized)) return null
        if (enumItems.isNotEmpty()) return projectEnumDefault(fieldPath, fieldType, normalized, enumItems)
        return projectScalarDefault(fieldPath, fieldType, normalized)
    }

    private fun projectEnumDefault(
        fieldPath: String,
        fieldType: String,
        normalized: String,
        enumItems: List<EnumItemModel>,
    ): String {
        val numericValue = normalized.unquoteSqlString().unwrapEnumValueOrNull()
            ?: normalized.unwrapEnumValueOrNull()
            ?: throw IllegalArgumentException(
                "aggregate enum field $fieldPath default $normalized is not numeric; enum defaults must use numeric values"
            )
        require(enumItems.any { it.value == numericValue }) {
            "aggregate enum field $fieldPath default $numericValue does not match any enum item value"
        }
        return "$fieldType.valueOf($numericValue)"
    }

    private fun projectScalarDefault(fieldPath: String, fieldType: String, normalized: String): String? {
        val shortType = fieldType.substringAfterLast('.').removeSuffix("?")
        return when (shortType) {
            "Boolean" -> when {
                normalized.equals("true", ignoreCase = true) || normalized == "1" -> "true"
                normalized.equals("false", ignoreCase = true) || normalized == "0" -> "false"
                else -> throw IllegalArgumentException(
                    "aggregate field $fieldPath default $normalized cannot be projected to Boolean"
                )
            }
            "String" -> normalized.unquoteSqlString()?.let { quoteKotlinString(it) }
            "Byte", "Short", "Int" -> normalized.unquoteSqlString().unwrapIntegerLiteralOrNull()
                ?: normalized.unwrapIntegerLiteralOrNull()
                ?: throw IllegalArgumentException(
                    "aggregate field $fieldPath default $normalized cannot be projected to $shortType"
                )
            "Long" -> {
                val value = normalized.unquoteSqlString().unwrapIntegerLiteralOrNull()
                    ?: normalized.unwrapIntegerLiteralOrNull()
                    ?: throw IllegalArgumentException(
                        "aggregate field $fieldPath default $normalized cannot be projected to Long"
                    )
                "${value}L"
            }
            "Float" -> {
                val rawValue = normalized.unquoteSqlString() ?: normalized
                normalizedFloatingLiteral(rawValue, suffix = "f")
                    ?: throw IllegalArgumentException(
                        "aggregate field $fieldPath default $normalized cannot be projected to Float"
                    )
            }
            "Double" -> {
                val rawValue = normalized.unquoteSqlString() ?: normalized
                normalizedFloatingLiteral(rawValue)
                    ?: throw IllegalArgumentException(
                        "aggregate field $fieldPath default $normalized cannot be projected to Double"
                    )
            }
            else -> null
        }
    }

    private fun normalizeDefaultLiteral(rawValue: String): String {
        var value = rawValue.trim()
        while (value.length >= 2 && value.first() == '(' && value.last() == ')') {
            value = value.substring(1, value.lastIndex).trim()
        }
        return value
    }

    private fun isSqlExpression(value: String): Boolean {
        val upper = value.uppercase()
        return upper in setOf("CURRENT_TIMESTAMP", "CURRENT_DATE", "CURRENT_TIME") || upper.endsWith("()")
    }

    private fun String?.unwrapIntegerLiteralOrNull(): String? =
        this?.takeIf { it.matches(Regex("""[-+]?\d+""")) }

    private fun String?.unwrapEnumValueOrNull(): Int? =
        this?.takeIf { it.matches(Regex("""[-+]?\d+""")) }?.toInt()

    private fun normalizedFloatingLiteral(rawValue: String, suffix: String? = null): String? {
        val value = if (suffix != null && rawValue.endsWith(suffix, ignoreCase = true)) {
            rawValue.dropLast(suffix.length)
        } else {
            rawValue
        }
        if (!value.matches(Regex("""[-+]?(\d+(\.\d*)?|\.\d+)"""))) return null
        val sign = value.takeIf { it.startsWith("-") || it.startsWith("+") }?.take(1).orEmpty()
        val unsigned = value.removePrefix("-").removePrefix("+")
        val withLeadingDigit = if (unsigned.startsWith(".")) "0$unsigned" else unsigned
        val normalized = if (withLeadingDigit.endsWith(".")) "${withLeadingDigit}0" else withLeadingDigit
        return sign + normalized + (suffix ?: "")
    }

    private fun String.unquoteSqlString(): String? =
        when {
            length >= 2 && first() == '\'' && last() == '\'' -> substring(1, lastIndex).replace("''", "'")
            length >= 2 && first() == '"' && last() == '"' -> substring(1, lastIndex).replace("\"\"", "\"")
            else -> null
        }

    private fun quoteKotlinString(value: String): String =
        buildString {
            append('"')
            value.forEach { char ->
                when (char) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> append(char)
                }
            }
            append('"')
        }
}
```

- [ ] **Step 2: Wire the scalar projector into `EntityArtifactPlanner`**

Add this after `AggregateEnumPlanning.from(...)`:

```kotlin
val defaultProjector = AggregateEntityDefaultProjector()
```

Replace:

```kotlin
"defaultValue" to kotlinConstructorDefaultValue(field.defaultValue, fieldType),
```

with:

```kotlin
"defaultValue" to defaultProjector.project(
    fieldPath = "${entity.packageName}.${entity.name}.${field.name}",
    fieldType = fieldType,
    nullable = field.nullable,
    rawDefaultValue = field.defaultValue,
    enumItems = emptyList(),
),
```

Delete these private helpers from `EntityArtifactPlanner.kt`:

```kotlin
private fun kotlinConstructorDefaultValue(rawDefaultValue: String?, fieldType: String): String?
private fun normalizedFloatingLiteral(rawValue: String, suffix: String? = null): String?
private fun String.trimSurroundingParentheses(): String
private fun String.unquoteSqlString(): String?
private fun quoteKotlinString(value: String): String
```

- [ ] **Step 3: Replace the old incompatible Long behavior test**

In `entity planner normalizes database defaults into Kotlin constructor defaults`, remove `FieldModel("priority", "Long", defaultValue = "1.0")` and remove its assertion. Add this test:

```kotlin
@Test
fun `entity planner fails when scalar database default cannot be projected`() {
    val entity = EntityModel(
        name = "VideoPost",
        packageName = "com.acme.demo.domain.aggregates.video_post",
        tableName = "video_post",
        comment = "video post",
        fields = listOf(
            FieldModel("id", "Long"),
            FieldModel("priority", "Long", defaultValue = "1.0"),
        ),
        idField = FieldModel("id", "Long"),
    )

    val error = assertThrows(IllegalArgumentException::class.java) {
        AggregateArtifactPlanner().plan(
            aggregateConfig(),
            CanonicalModel(
                entities = listOf(entity),
                aggregateEntityJpa = listOf(defaultAggregateEntityJpa(entity)),
            )
        )
    }

    assertEquals(
        "aggregate field com.acme.demo.domain.aggregates.video_post.VideoPost.priority default 1.0 cannot be projected to Long",
        error.message,
    )
}
```

- [ ] **Step 4: Run aggregate planner tests**

Run:

```powershell
.\gradlew.bat :cap4k-plugin-pipeline-generator-aggregate:test --tests "com.only4.cap4k.plugin.pipeline.generator.aggregate.AggregateArtifactPlannerTest"
```

Expected: PASS.

- [ ] **Step 5: Commit scalar projection implementation**

```powershell
git add cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateEntityDefaultProjector.kt cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/EntityArtifactPlanner.kt cap4k-plugin-pipeline-generator-aggregate/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateArtifactPlannerTest.kt
git commit -m "feat: project aggregate scalar entity defaults"
```

---

### Task 3: Preserve enum item metadata in aggregate enum planning

**Files:**
- Modify: `cap4k-plugin-pipeline-generator-aggregate/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateEnumPlanningTest.kt`
- Modify: `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateEnumPlanning.kt`

- [ ] **Step 1: Add failing enum metadata lookup tests**

Add these tests to `AggregateEnumPlanningTest.kt`:

```kotlin
@Test
fun `resolves local enum items for aggregate field`() {
    val enumItems = listOf(EnumItemModel(0, "HIDDEN", "Hidden"), EnumItemModel(1, "PUBLIC", "Public"))
    val entity = EntityModel(
        name = "VideoPost",
        packageName = "com.acme.demo.domain.aggregates.video_post",
        tableName = "video_post",
        comment = "",
        fields = listOf(FieldModel("visibility", "Int", typeBinding = "Visibility", enumItems = enumItems)),
        idField = FieldModel("id", "Long"),
    )
    val planning = AggregateEnumPlanning.from(
        CanonicalModel(entities = listOf(entity)),
        basePackage = "com.acme.demo",
        typeRegistry = emptyMap(),
    )

    assertEquals(enumItems, planning.resolveEnumItems(entity.packageName, entity.fields.single()))
}

@Test
fun `resolves shared enum items for aggregate field`() {
    val enumItems = listOf(EnumItemModel(0, "DRAFT", "Draft"), EnumItemModel(1, "PUBLISHED", "Published"))
    val entity = EntityModel(
        name = "VideoPost",
        packageName = "com.acme.demo.domain.aggregates.video_post",
        tableName = "video_post",
        comment = "",
        fields = listOf(FieldModel("status", "Int", typeBinding = "Status")),
        idField = FieldModel("id", "Long"),
    )
    val planning = AggregateEnumPlanning.from(
        CanonicalModel(
            sharedEnums = listOf(
                SharedEnumDefinition(
                    typeName = "Status",
                    packageName = "shared",
                    generateTranslation = true,
                    items = enumItems,
                )
            ),
            entities = listOf(entity),
        ),
        basePackage = "com.acme.demo",
        typeRegistry = emptyMap(),
    )

    assertEquals(enumItems, planning.resolveEnumItems(entity.packageName, entity.fields.single()))
}
```

- [ ] **Step 2: Run enum planning tests and verify failure**

Run:

```powershell
.\gradlew.bat :cap4k-plugin-pipeline-generator-aggregate:test --tests "com.only4.cap4k.plugin.pipeline.generator.aggregate.AggregateEnumPlanningTest"
```

Expected: FAIL because `AggregateEnumPlanning` does not expose `resolveEnumItems`.

- [ ] **Step 3: Extend `AggregateEnumPlanning` to retain enum items**

Change the constructor to include enum item maps:

```kotlin
internal class AggregateEnumPlanning private constructor(
    private val sharedEnumFqns: Map<String, String>,
    private val sharedEnumItems: Map<String, List<EnumItemModel>>,
    private val localEnumFqns: Map<LocalEnumOwnerKey, String>,
    private val localEnumItems: Map<LocalEnumOwnerKey, List<EnumItemModel>>,
    private val typeRegistry: Map<String, TypeRegistryEntry>,
)
```

Add this method:

```kotlin
fun resolveEnumItems(ownerPackageName: String?, field: FieldModel): List<EnumItemModel> {
    if (field.enumItems.isNotEmpty()) return field.enumItems
    val typeBinding = field.typeBinding?.takeIf { it.isNotBlank() } ?: return emptyList()
    if (ownerPackageName != null) {
        localEnumItems[LocalEnumOwnerKey(ownerPackageName = ownerPackageName, typeBinding = typeBinding)]?.let {
            return it
        }
    }
    return sharedEnumItems[typeBinding].orEmpty()
}
```

In `from(...)`, build `sharedEnumItems`, `localEnumFqns`, and `localEnumItems`:

```kotlin
val sharedEnumFqns = buildSharedEnumFqns(model.sharedEnums, artifactLayout)
val sharedEnumItems = buildSharedEnumItems(model.sharedEnums)
val localEnumDefinitions = buildLocalEnumDefinitions(model.entities, artifactLayout)
val localEnumFqns = localEnumDefinitions.mapValues { (_, definition) -> definition.fqn }
val localEnumItems = localEnumDefinitions.mapValues { (_, definition) -> definition.enumItems }
```

Add:

```kotlin
private fun buildSharedEnumItems(definitions: List<SharedEnumDefinition>): Map<String, List<EnumItemModel>> {
    val grouped = definitions.groupBy { it.typeName.trim() }
    grouped.entries.firstOrNull { it.value.size > 1 }?.key?.let { duplicated ->
        throw IllegalArgumentException("duplicate shared enum definition: $duplicated")
    }
    return grouped.mapValues { (_, values) -> values.single().items }
}
```

Rename `buildLocalEnumFqns` to `buildLocalEnumDefinitions` and return definitions:

```kotlin
private fun buildLocalEnumDefinitions(
    entities: List<EntityModel>,
    artifactLayout: ArtifactLayoutResolver,
): Map<LocalEnumOwnerKey, LocalEnumDefinition> {
    val grouped = entities
        .flatMap { entity ->
            entity.fields.mapNotNull { field ->
                val typeBinding = field.typeBinding?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                if (field.enumItems.isEmpty()) return@mapNotNull null
                LocalEnumOwnerKey(entity.packageName, typeBinding) to LocalEnumDefinition(
                    fqn = buildLocalEnumFqn(entity, typeBinding, artifactLayout),
                    enumItems = field.enumItems,
                )
            }
        }
        .groupBy({ it.first }, { it.second })

    grouped.entries.firstOrNull { entry ->
        entry.value.map { it.enumItems }.distinct().size > 1
    }?.let { entry ->
        throw IllegalArgumentException("conflicting local enum definition for ${entry.value.first().fqn}")
    }
    return grouped.mapValues { (_, values) -> values.first() }
}
```

Update the constructor return:

```kotlin
return AggregateEnumPlanning(
    sharedEnumFqns = sharedEnumFqns,
    sharedEnumItems = sharedEnumItems,
    localEnumFqns = localEnumFqns,
    localEnumItems = localEnumItems,
    typeRegistry = typeRegistry,
)
```

- [ ] **Step 4: Run enum planning tests**

Run:

```powershell
.\gradlew.bat :cap4k-plugin-pipeline-generator-aggregate:test --tests "com.only4.cap4k.plugin.pipeline.generator.aggregate.AggregateEnumPlanningTest"
```

Expected: PASS.

- [ ] **Step 5: Commit enum metadata lookup**

```powershell
git add cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateEnumPlanning.kt cap4k-plugin-pipeline-generator-aggregate/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateEnumPlanningTest.kt
git commit -m "feat: expose aggregate enum items for default projection"
```

---

### Task 4: Project enum database defaults and fail invalid enum defaults

**Files:**
- Modify: `cap4k-plugin-pipeline-generator-aggregate/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateArtifactPlannerTest.kt`
- Modify: `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/EntityArtifactPlanner.kt`

- [ ] **Step 1: Add failing enum default planner tests**

Add these tests to `AggregateArtifactPlannerTest.kt`:

```kotlin
@Test
fun `entity planner projects numeric enum database defaults`() {
    val visibilityItems = listOf(EnumItemModel(0, "HIDDEN", "Hidden"), EnumItemModel(1, "PUBLIC", "Public"))
    val statusItems = listOf(EnumItemModel(0, "DRAFT", "Draft"), EnumItemModel(1, "PUBLISHED", "Published"))
    val entity = EntityModel(
        name = "VideoPost",
        packageName = "com.acme.demo.domain.aggregates.video_post",
        tableName = "video_post",
        comment = "video post",
        fields = listOf(
            FieldModel("id", "Long"),
            FieldModel("visibility", "Int", defaultValue = "'1'", typeBinding = "Visibility", enumItems = visibilityItems),
            FieldModel("status", "Int", defaultValue = "0", typeBinding = "Status"),
        ),
        idField = FieldModel("id", "Long"),
    )

    val plan = AggregateArtifactPlanner().plan(
        aggregateConfig(),
        CanonicalModel(
            sharedEnums = listOf(SharedEnumDefinition("Status", "shared", true, statusItems)),
            entities = listOf(entity),
            aggregateEntityJpa = listOf(defaultAggregateEntityJpa(entity)),
        )
    )

    val entityArtifact = plan.single { it.outputPath.endsWith("/VideoPost.kt") }
    @Suppress("UNCHECKED_CAST")
    val scalarFields = entityArtifact.context["scalarFields"] as List<Map<String, Any?>>

    assertEquals(
        "com.acme.demo.domain.aggregates.video_post.enums.Visibility.valueOf(1)",
        scalarFields.single { it["name"] == "visibility" }["defaultValue"],
    )
    assertEquals(
        "com.acme.demo.domain.shared.enums.Status.valueOf(0)",
        scalarFields.single { it["name"] == "status" }["defaultValue"],
    )
}

@Test
fun `entity planner fails when enum database default is not numeric`() {
    val entity = EntityModel(
        name = "VideoPost",
        packageName = "com.acme.demo.domain.aggregates.video_post",
        tableName = "video_post",
        comment = "video post",
        fields = listOf(
            FieldModel("id", "Long"),
            FieldModel("visibility", "Int", defaultValue = "'PUBLIC'", typeBinding = "Visibility", enumItems = listOf(EnumItemModel(1, "PUBLIC", "Public"))),
        ),
        idField = FieldModel("id", "Long"),
    )

    val error = assertThrows(IllegalArgumentException::class.java) {
        AggregateArtifactPlanner().plan(
            aggregateConfig(),
            CanonicalModel(entities = listOf(entity), aggregateEntityJpa = listOf(defaultAggregateEntityJpa(entity))),
        )
    }

    assertEquals(
        "aggregate enum field com.acme.demo.domain.aggregates.video_post.VideoPost.visibility default 'PUBLIC' is not numeric; enum defaults must use numeric values",
        error.message,
    )
}

@Test
fun `entity planner fails when enum database default value is not declared`() {
    val entity = EntityModel(
        name = "VideoPost",
        packageName = "com.acme.demo.domain.aggregates.video_post",
        tableName = "video_post",
        comment = "video post",
        fields = listOf(
            FieldModel("id", "Long"),
            FieldModel("visibility", "Int", defaultValue = "9", typeBinding = "Visibility", enumItems = listOf(EnumItemModel(1, "PUBLIC", "Public"))),
        ),
        idField = FieldModel("id", "Long"),
    )

    val error = assertThrows(IllegalArgumentException::class.java) {
        AggregateArtifactPlanner().plan(
            aggregateConfig(),
            CanonicalModel(entities = listOf(entity), aggregateEntityJpa = listOf(defaultAggregateEntityJpa(entity))),
        )
    }

    assertEquals(
        "aggregate enum field com.acme.demo.domain.aggregates.video_post.VideoPost.visibility default 9 does not match any enum item value",
        error.message,
    )
}
```

- [ ] **Step 2: Run enum default tests and verify failure**

Run:

```powershell
.\gradlew.bat :cap4k-plugin-pipeline-generator-aggregate:test --tests "com.only4.cap4k.plugin.pipeline.generator.aggregate.AggregateArtifactPlannerTest.entity planner projects numeric enum database defaults" --tests "com.only4.cap4k.plugin.pipeline.generator.aggregate.AggregateArtifactPlannerTest.entity planner fails when enum database default is not numeric" --tests "com.only4.cap4k.plugin.pipeline.generator.aggregate.AggregateArtifactPlannerTest.entity planner fails when enum database default value is not declared"
```

Expected: FAIL. The planner still passes `emptyList()` for enum items.

- [ ] **Step 3: Pass enum items into the projector**

In `EntityArtifactPlanner.kt`, replace `enumItems = emptyList()` with:

```kotlin
enumItems = planning.resolveEnumItems(entity.packageName, field),
```

The full default assignment should be:

```kotlin
"defaultValue" to defaultProjector.project(
    fieldPath = "${entity.packageName}.${entity.name}.${field.name}",
    fieldType = fieldType,
    nullable = field.nullable,
    rawDefaultValue = field.defaultValue,
    enumItems = planning.resolveEnumItems(entity.packageName, field),
),
```

- [ ] **Step 4: Run enum default tests**

Run:

```powershell
.\gradlew.bat :cap4k-plugin-pipeline-generator-aggregate:test --tests "com.only4.cap4k.plugin.pipeline.generator.aggregate.AggregateArtifactPlannerTest.entity planner projects numeric enum database defaults" --tests "com.only4.cap4k.plugin.pipeline.generator.aggregate.AggregateArtifactPlannerTest.entity planner fails when enum database default is not numeric" --tests "com.only4.cap4k.plugin.pipeline.generator.aggregate.AggregateArtifactPlannerTest.entity planner fails when enum database default value is not declared"
```

Expected: PASS.

- [ ] **Step 5: Commit enum default projection**

```powershell
git add cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/EntityArtifactPlanner.kt cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateEntityDefaultProjector.kt cap4k-plugin-pipeline-generator-aggregate/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateArtifactPlannerTest.kt
git commit -m "feat: project aggregate enum entity defaults"
```

---

### Task 5: Verify rendering boundary and run focused module checks

**Files:**
- Modify only if a regression is discovered: `cap4k-plugin-pipeline-renderer-pebble/src/test/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PebbleArtifactRendererTest.kt`
- Do not modify: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/entity.kt.peb`

- [ ] **Step 1: Run the aggregate generator module tests**

Run:

```powershell
.\gradlew.bat :cap4k-plugin-pipeline-generator-aggregate:test
```

Expected: PASS.

- [ ] **Step 2: Run the aggregate entity renderer tests**

Run:

```powershell
.\gradlew.bat :cap4k-plugin-pipeline-renderer-pebble:test --tests "com.only4.cap4k.plugin.pipeline.renderer.pebble.PebbleArtifactRendererTest.*aggregate entity*"
```

Expected: PASS. The entity template already renders `field.defaultValue` as raw Kotlin; no template change should be needed.

- [ ] **Step 3: If the renderer wildcard command does not match tests, run the renderer module test**

Run only if Step 2 fails because no tests matched:

```powershell
.\gradlew.bat :cap4k-plugin-pipeline-renderer-pebble:test
```

Expected: PASS.

- [ ] **Step 4: Confirm the implementation did not touch excluded modules**

Run after Task 4 commit:

```powershell
git diff --name-only HEAD~4..HEAD
```

Expected output should only include files under:

```text
cap4k-plugin-pipeline-generator-aggregate/
```

If the output includes renderer templates, source-db, core, analysis, bootstrap, or legacy codegen files, remove those changes before continuing.

- [ ] **Step 5: Commit verification-only test adjustment if needed**

Run this only if Step 2 required adding or adjusting a renderer test:

```powershell
git add cap4k-plugin-pipeline-renderer-pebble/src/test/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PebbleArtifactRendererTest.kt
git commit -m "test: preserve aggregate entity default rendering"
```

If no renderer test changes were needed, skip this commit step.

---

### Task 6: Dogfood smoke check in `only-danmuku-zero`

**Files:**
- Do not edit `only-danmuku-zero` unless the user explicitly asks to update the dogfood project.

- [ ] **Step 1: Publish the updated cap4k snapshot only after generator tests pass**

Run from `C:\Users\LD_moxeii\Documents\code\only-workspace\cap4k`:

```powershell
.\gradlew.bat publish
```

Expected: PASS. Existing Gradle publication warnings about duplicate plugin publication coordinates are not part of this slice.

- [ ] **Step 2: Regenerate only-danmuku-zero only if the user wants the dogfood workspace updated**

Run from `C:\Users\LD_moxeii\Documents\code\only-workspace\only-danmuku-zero` only after user approval:

```powershell
.\gradlew.bat cap4kGenerate
```

Expected: generated aggregate entity constructor defaults now follow the explicit-input rules.

- [ ] **Step 3: Inspect one representative generated entity**

Open:

```text
only-danmuku-zero/only-danmuku-domain/build/generated/cap4k/main/kotlin/edu/only4/danmuku/domain/aggregates/customer_video_series/CustomerVideoSeries.kt
```

Expected:

- nullable fields without DB defaults have `= null`
- fields with DB defaults have projected Kotlin defaults
- fields without DB defaults remain constructor-required
- enum defaults are `EnumType.valueOf(number)` only when the DB default is numeric

- [ ] **Step 4: Do not commit dogfood output automatically**

If the generated output looks correct, ask the user whether to commit the dogfood regeneration. Do not include dogfood output in the cap4k implementation commits.

---

## Final Verification

Run from `C:\Users\LD_moxeii\Documents\code\only-workspace\cap4k`:

```powershell
.\gradlew.bat :cap4k-plugin-pipeline-generator-aggregate:test
```

Expected: PASS.

Run:

```powershell
.\gradlew.bat :cap4k-plugin-pipeline-renderer-pebble:test --tests "com.only4.cap4k.plugin.pipeline.renderer.pebble.PebbleArtifactRendererTest.*aggregate entity*"
```

Expected: PASS, or run the full renderer module test if Gradle reports no matching tests.

Run:

```powershell
git status --short
```

Expected: clean working tree after implementation commits, unless the user approved dogfood regeneration in `only-danmuku-zero`.

## Success Criteria

- Non-null scalar fields without DB defaults do not get primitive fallback defaults.
- Nullable fields without DB defaults get `null`.
- Nullable fields with DB defaults use the DB default instead of `null`.
- `Long` DB defaults render with `L`.
- Quoted numeric DB defaults work for numeric scalar fields.
- SQL function defaults are not converted into Kotlin approximations.
- Known enum defaults accept numeric values and quoted numeric values.
- Known enum defaults reject enum-name strings.
- Known enum defaults reject numeric values missing from the enum manifest.
- No bootstrap, analysis, source-db, core, renderer template, or legacy codegen behavior is changed.

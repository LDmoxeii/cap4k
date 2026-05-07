# Cap4k Aggregate Create-Surface Write-Surface Slice and Factory Payload Metadata Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Tighten the aggregate family's current generated create payload by making `Factory.Payload` consume resolved managed create write-surface, and preserve semantic nested payload metadata names without widening scope to JPA mutability inference or wrapper redesign. This plan fully closes `#12` and delivers bounded aggregate-side progress on `#5`, but does not claim full `#5` closure.

**Architecture:** Keep canonical resolution unchanged. Add one bounded planner-side projection in `FactoryArtifactPlanner` that reads `AggregateSpecialFieldResolvedPolicy.writeSurface.createAllowedFields`, passes render-ready payload context into `aggregate/factory.kt.peb`, and falls back to the old minimal payload contract only when no resolved policy exists. Separately, split semantic payload metadata naming from the nested Kotlin class name by introducing a dedicated `payloadMetadataName` context key while keeping the nested class name `Payload`. The aggregate generator currently has no distinct generated update payload surface, so this plan intentionally hardens the only existing aggregate write payload instead of inventing a new update family.

**Tech Stack:** Kotlin, JUnit 5, Pebble templates, Gradle module tests in `cap4k-plugin-pipeline-generator-aggregate` and `cap4k-plugin-pipeline-renderer-pebble`.

**Issue Positioning:** `#12` is fully addressed here. `#5` remains open after this plan unless another slice wires remaining generated create/update write surfaces outside the current aggregate factory payload.

---

### Task 1: Lock The Planner Contract For Managed Create Write-Surface

**Files:**
- Modify: `cap4k-plugin-pipeline-generator-aggregate/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateArtifactPlannerTest.kt`

- [ ] **Step 1: Add a failing planner test for write-surface-filtered payload fields**

Add a test near the existing factory planner coverage:

```kotlin
@Test
fun `factory planner filters payload fields by create write surface and preserves semantic payload metadata name`() {
    val entity = EntityModel(
        name = "VideoPost",
        packageName = "com.acme.demo.domain.aggregates.video_post",
        tableName = "video_post",
        comment = "video post",
        fields = listOf(
            FieldModel("id", "Long", columnName = "id"),
            FieldModel("title", "String", columnName = "title"),
            FieldModel("createdBy", "String", columnName = "created_by"),
        ),
        idField = FieldModel("id", "Long", columnName = "id"),
    )
    val model = CanonicalModel(
        entities = listOf(entity),
        aggregateSpecialFieldResolvedPolicies = listOf(
            AggregateSpecialFieldResolvedPolicy(
                entityName = "VideoPost",
                entityPackageName = "com.acme.demo.domain.aggregates.video_post",
                tableName = "video_post",
                id = ResolvedIdPolicy(
                    fieldName = "id",
                    columnName = "id",
                    strategy = "uuid7",
                    kind = AggregateIdPolicyKind.APPLICATION_SIDE,
                    source = SpecialFieldSource.DSL_DEFAULT,
                    writePolicy = SpecialFieldWritePolicy.CREATE_ONLY,
                ),
                deleted = ResolvedMarkerPolicy(enabled = false, source = SpecialFieldSource.NONE),
                version = ResolvedMarkerPolicy(enabled = false, source = SpecialFieldSource.NONE),
                managedFields = listOf(
                    ResolvedManagedFieldPolicy(
                        fieldName = "createdBy",
                        columnName = "created_by",
                        writePolicy = SpecialFieldWritePolicy.READ_ONLY,
                        source = SpecialFieldSource.DSL_DEFAULT,
                    )
                ),
                writeSurface = ResolvedWriteSurfacePolicy(
                    createAllowedFields = listOf("id", "title"),
                    updateAllowedFields = listOf("title"),
                ),
            )
        ),
    )

    val item = AggregateArtifactPlanner().plan(aggregateConfig(), model)
        .single { it.templateId == "aggregate/factory.kt.peb" }
    val context = item.context

    @Suppress("UNCHECKED_CAST")
    val payloadFields = context.getValue("payloadFields") as List<Map<String, Any?>>

    assertEquals(true, context["payloadWriteSurfaceResolved"])
    assertEquals("Payload", context["payloadTypeName"])
    assertEquals("VideoPostPayload", context["payloadMetadataName"])
    assertEquals(listOf("id", "title"), payloadFields.map { it["name"] })
    assertEquals(listOf("Long", "String"), payloadFields.map { it["type"] })
}
```

- [ ] **Step 2: Add a failing planner test for the legacy fallback path**

Add a second test immediately after the one above:

```kotlin
@Test
fun `factory planner keeps legacy minimal payload contract when special field policy is absent`() {
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

    val item = AggregateArtifactPlanner().plan(
        aggregateConfig(),
        CanonicalModel(entities = listOf(entity)),
    ).single { it.templateId == "aggregate/factory.kt.peb" }

    assertEquals(false, item.context["payloadWriteSurfaceResolved"])
    assertEquals("VideoPostPayload", item.context["payloadMetadataName"])
    assertEquals(emptyList<Map<String, Any?>>(), item.context["payloadFields"])
}
```

- [ ] **Step 3: Run the planner test class and verify these tests fail**

Run from `C:\Users\LD_moxeii\Documents\code\only-workspace\cap4k`:

```powershell
.\gradlew.bat :cap4k-plugin-pipeline-generator-aggregate:test --tests "com.only4.cap4k.plugin.pipeline.generator.aggregate.AggregateArtifactPlannerTest"
```

Expected: FAIL because `FactoryArtifactPlanner` does not yet emit `payloadWriteSurfaceResolved`, `payloadFields`, or `payloadMetadataName`.

- [ ] **Step 4: Commit the failing planner contract test**

```bash
git add cap4k-plugin-pipeline-generator-aggregate/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateArtifactPlannerTest.kt
git commit -m "test: lock aggregate factory payload write surface planner contract"
```

### Task 2: Implement Planner-Side Payload Projection Without Touching Canonical Rules

**Files:**
- Modify: `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/FactoryArtifactPlanner.kt`
- Modify: `cap4k-plugin-pipeline-generator-aggregate/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateArtifactPlannerTest.kt`

- [ ] **Step 1: Add the minimal planner implementation**

Update `FactoryArtifactPlanner.plan(...)` so it resolves payload fields only when a policy exists:

```kotlin
override fun plan(config: ProjectConfig, model: CanonicalModel): List<ArtifactPlanItem> {
    val artifactLayout = ArtifactLayoutResolver(config.basePackage, config.artifactLayout)
    val derivedTypeReferences = AggregateDerivedTypeReferences.from(model)
    val enumPlanning = AggregateEnumPlanning.from(model, artifactLayout, config.typeRegistry)

    return model.entities.filter { it.aggregateRoot }.map { entity ->
        val resolvedPolicy = model.aggregateSpecialFieldResolvedPolicies.singleOrNull {
            it.entityName == entity.name && it.entityPackageName == entity.packageName
        }
        val payloadFields = resolvedPolicy?.writeSurface?.createAllowedFields
            ?.toSet()
            ?.let { allowed ->
                entity.fields
                    .filter { it.name in allowed }
                    .map { field ->
                        mapOf(
                            "name" to field.name,
                            "type" to enumPlanning.resolveFieldType(entity.packageName, field),
                            "nullable" to field.nullable,
                        )
                    }
            }
            .orEmpty()

        checkedInKotlinArtifact(
            config = config,
            artifactLayout = artifactLayout,
            moduleRole = "domain",
            templateId = "aggregate/factory.kt.peb",
            packageName = artifactLayout.aggregateFactoryPackage(entity.packageName),
            typeName = "${entity.name}Factory",
            context = mapOf(
                "packageName" to artifactLayout.aggregateFactoryPackage(entity.packageName),
                "typeName" to "${entity.name}Factory",
                "payloadTypeName" to "Payload",
                "payloadMetadataName" to "${entity.name}Payload",
                "payloadWriteSurfaceResolved" to (resolvedPolicy != null),
                "payloadFields" to payloadFields,
                "entityName" to entity.name,
                "entityTypeFqn" to derivedTypeReferences.entityFqn(entity),
                "aggregateName" to entity.name,
                "comment" to entity.comment,
            ),
        )
    }
}
```

- [ ] **Step 2: Re-run the planner test class and verify it passes**

Run:

```powershell
.\gradlew.bat :cap4k-plugin-pipeline-generator-aggregate:test --tests "com.only4.cap4k.plugin.pipeline.generator.aggregate.AggregateArtifactPlannerTest"
```

Expected: PASS, including the two new factory payload planner tests.

- [ ] **Step 3: Commit the planner implementation**

```bash
git add cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/FactoryArtifactPlanner.kt cap4k-plugin-pipeline-generator-aggregate/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateArtifactPlannerTest.kt
git commit -m "feat: make aggregate factory planner consume create write surface"
```

### Task 3: Render The New Payload Contract And Preserve Semantic Metadata Name

**Files:**
- Modify: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/factory.kt.peb`
- Modify: `cap4k-plugin-pipeline-renderer-pebble/src/test/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PebbleArtifactRendererTest.kt`

- [ ] **Step 1: Add a failing renderer test for semantic payload metadata and filtered fields**

Add a focused renderer test near the current aggregate factory template assertions:

```kotlin
@Test
fun `aggregate factory template renders semantic payload metadata name and filtered payload fields`() {
    val content = renderTemplate(
        templateId = "aggregate/factory.kt.peb",
        outputPath = "demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/factory/VideoPostFactory.kt",
        context = mapOf(
            "packageName" to "com.acme.demo.domain.aggregates.video_post.factory",
            "typeName" to "VideoPostFactory",
            "payloadTypeName" to "Payload",
            "payloadMetadataName" to "VideoPostPayload",
            "payloadWriteSurfaceResolved" to true,
            "payloadFields" to listOf(
                mapOf("name" to "id", "type" to "Long", "nullable" to false),
                mapOf("name" to "title", "type" to "String", "nullable" to false),
            ),
            "entityName" to "VideoPost",
            "entityTypeFqn" to "com.acme.demo.domain.aggregates.video_post.VideoPost",
            "aggregateName" to "VideoPost",
            "comment" to "video post",
            "imports" to emptyList<String>(),
        ),
    )

    assertTrue(content.contains("name = \"VideoPostPayload\""))
    assertTrue(content.contains("data class Payload("))
    assertTrue(content.contains("val id: Long"))
    assertTrue(content.contains("val title: String"))
    assertFalse(content.contains("val name: String"))
}
```

- [ ] **Step 2: Add a failing renderer test for the no-policy fallback**

Add this second test immediately after the first:

```kotlin
@Test
fun `aggregate factory template keeps legacy minimal payload when write surface is unresolved`() {
    val content = renderTemplate(
        templateId = "aggregate/factory.kt.peb",
        outputPath = "demo-domain/src/main/kotlin/com/acme/demo/domain/aggregates/video_post/factory/VideoPostFactory.kt",
        context = mapOf(
            "packageName" to "com.acme.demo.domain.aggregates.video_post.factory",
            "typeName" to "VideoPostFactory",
            "payloadTypeName" to "Payload",
            "payloadMetadataName" to "VideoPostPayload",
            "payloadWriteSurfaceResolved" to false,
            "payloadFields" to emptyList<Map<String, Any?>>(),
            "entityName" to "VideoPost",
            "entityTypeFqn" to "com.acme.demo.domain.aggregates.video_post.VideoPost",
            "aggregateName" to "VideoPost",
            "comment" to "video post",
            "imports" to emptyList<String>(),
        ),
    )

    assertTrue(content.contains("name = \"VideoPostPayload\""))
    assertTrue(content.contains("val name: String"))
}
```

- [ ] **Step 3: Run the renderer test class and verify these tests fail**

Run:

```powershell
.\gradlew.bat :cap4k-plugin-pipeline-renderer-pebble:test --tests "com.only4.cap4k.plugin.pipeline.renderer.pebble.PebbleArtifactRendererTest"
```

Expected: FAIL because the template still hardcodes `name = "{{ payloadTypeName }}"` and `val name: String`.

- [ ] **Step 4: Update the template to branch on resolved write-surface**

Replace the nested payload block in `aggregate/factory.kt.peb` with:

```pebble
    @Aggregate(
        aggregate = "{{ aggregateName }}",
        name = "{{ payloadMetadataName }}",
        type = Aggregate.TYPE_FACTORY_PAYLOAD,
        description = ""
    )
    data class {{ payloadTypeName }}(
{% if payloadWriteSurfaceResolved %}
{% for field in payloadFields -%}
{{ "        " }}val {{ field.name }}: {{ field.type }}{% if field.nullable %}?{% endif %}{% if not loop.last %},{% endif %}
{% endfor -%}
{% else %}
        val name: String
{% endif %}
    ) : AggregatePayload<{{ entityName }}>
```

- [ ] **Step 5: Re-run the renderer test class and verify it passes**

Run:

```powershell
.\gradlew.bat :cap4k-plugin-pipeline-renderer-pebble:test --tests "com.only4.cap4k.plugin.pipeline.renderer.pebble.PebbleArtifactRendererTest"
```

Expected: PASS, including semantic payload metadata naming and no-policy fallback coverage.

- [ ] **Step 6: Run the two targeted module suites together**

Run:

```powershell
.\gradlew.bat :cap4k-plugin-pipeline-generator-aggregate:test
.\gradlew.bat :cap4k-plugin-pipeline-renderer-pebble:test
```

Expected: both module suites PASS with no aggregate planner or renderer regression.

- [ ] **Step 7: Commit the template and renderer coverage**

```bash
git add cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/aggregate/factory.kt.peb cap4k-plugin-pipeline-renderer-pebble/src/test/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PebbleArtifactRendererTest.kt
git commit -m "feat: preserve aggregate payload metadata name in factory template"
```

## Self-Review

### 1. Spec coverage

- `Factory.Payload` consumes `writeSurface.createAllowedFields`: Task 1-3.
- semantic nested payload metadata name is preserved: Task 1 and Task 3.
- entity JPA mutability inference remains out of scope: no task widens entity `@Column` inference.
- full `#5` closure remains out of scope: no task invents an aggregate update payload or claims broader write-surface coverage than the current aggregate factory contract.

No approved requirement is missing from the plan.

### 2. Placeholder scan

- No `TODO`, `TBD`, or deferred “implement later” wording remains.
- Each task names exact files, concrete test cases, exact commands, and expected outcomes.

### 3. Type and naming consistency

- The plan uses one consistent context contract:
  - `payloadTypeName`
  - `payloadMetadataName`
  - `payloadWriteSurfaceResolved`
  - `payloadFields`
- The plan keeps nested Kotlin class name `Payload` and semantic metadata name `<Aggregate>Payload` separate everywhere.

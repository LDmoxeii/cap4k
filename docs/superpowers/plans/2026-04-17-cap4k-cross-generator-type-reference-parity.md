# Cap4k Cross-Generator Type-Reference Parity Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Audit old `typeMapping` usage, lock the current pipeline's type-reference boundary in tests, and introduce the first minimal deterministic derived-reference utility without restoring mutable shared runtime state.

**Architecture:** This slice does not attempt full aggregate parity. It first records the old `typeMapping` usage categories as a durable audit artifact, then strengthens current design-side boundary tests, and finally adds a small aggregate-local derived-reference utility for convention-owned cases such as `Q<Entity>`. The new utility stays generator-local and immutable; no global mutable registry is introduced.

**Tech Stack:** Kotlin, JUnit 5, existing pipeline generator modules, Markdown docs

---

## File Structure

### New files

- Create: `docs/design/pipeline-migration/type-reference-audit.md`
- Create: `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateDerivedTypeReferences.kt`
- Create: `cap4k-plugin-pipeline-generator-aggregate/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateDerivedTypeReferencesTest.kt`

### Existing files to modify

- Modify: `cap4k-plugin-pipeline-generator-design/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignTypeResolverTest.kt`
- Modify: `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/SchemaArtifactPlanner.kt`
- Modify: `cap4k-plugin-pipeline-generator-aggregate/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateArtifactPlannerTest.kt`

### Responsibilities

- `docs/design/pipeline-migration/type-reference-audit.md`
  - durable audit of old `typeMapping` usage categories with concrete source references
  - records which usages are already covered, convention-derived, or deferred

- `AggregateDerivedTypeReferences.kt`
  - smallest aggregate-local derived-reference helper
  - deterministic lookups only
  - no mutation, no execution-order coupling

- `AggregateDerivedTypeReferencesTest.kt`
  - unit coverage for deterministic derivation rules
  - proves no double meaning between explicit entity FQNs and derived companion FQNs

- `DesignTypeResolverTest.kt`
  - locks current design-side resolution order
  - proves design-side does not need a new shared map for already-covered cases

- `SchemaArtifactPlanner.kt`
  - first production use of derived references
  - enriches schema artifact context with deterministic aggregate-side companion references for future parity consumers

- `AggregateArtifactPlannerTest.kt`
  - planner-level proof that new derived fields appear in artifact context without changing output-path behavior

## Task 1: Record the Old `typeMapping` Audit as a Durable Artifact

**Files:**
- Create: `docs/design/pipeline-migration/type-reference-audit.md`

- [ ] **Step 1: Write the audit document with a fixed category structure**

Create `docs/design/pipeline-migration/type-reference-audit.md` with this content:

```md
# Type-Reference Audit

Date: 2026-04-17
Status: Draft

## Purpose

This document records the first active audit of old-codegen `typeMapping` usage for the `cross-generator type-reference parity` slice.

It classifies old usage sites into:

- already covered by current pipeline behavior
- deterministic derived-reference candidates
- deferred shared-runtime-state cases

## Categories

### 1. Already Covered by Current Pipeline Behavior

These are short-name or explicit-FQN cases already handled by:

- `ProjectConfig.typeRegistry`
- `DesignSymbolRegistry`
- explicit FQN precedence

Representative old references:

- `cap4k-plugin-codegen/src/main/kotlin/com/only4/cap4k/plugin/codegen/context/design/builders/TypeMappingBuilder.kt`
- `cap4k-plugin-codegen/src/main/kotlin/com/only4/cap4k/plugin/codegen/imports/TypeResolver.kt`

### 2. Deterministic Derived-Reference Candidates

These are convention-owned names that can be derived without runtime mutation:

- `Q<Entity>`
- generated peer names whose package and simple-name convention are stable

Representative old references:

- `cap4k-plugin-codegen/src/main/kotlin/com/only4/cap4k/plugin/codegen/generators/aggregate/SchemaGenerator.kt`
- `cap4k-plugin-codegen/src/main/kotlin/com/only4/cap4k/plugin/codegen/generators/aggregate/EntityGenerator.kt`

### 3. Deferred Shared-Runtime-State Cases

These are still tied to old execution-order registration and are not part of this first slice.

Representative old references:

- `cap4k-plugin-codegen/src/main/kotlin/com/only4/cap4k/plugin/codegen/generators/aggregate/FactoryGenerator.kt`
- `cap4k-plugin-codegen/src/main/kotlin/com/only4/cap4k/plugin/codegen/generators/aggregate/SpecificationGenerator.kt`
- `cap4k-plugin-codegen/src/main/kotlin/com/only4/cap4k/plugin/codegen/generators/aggregate/UniqueQueryGenerator.kt`
- `cap4k-plugin-codegen/src/main/kotlin/com/only4/cap4k/plugin/codegen/generators/aggregate/UniqueValidatorGenerator.kt`
- `cap4k-plugin-codegen/src/main/kotlin/com/only4/cap4k/plugin/codegen/generators/aggregate/AggregateWrapperGenerator.kt`

## First-Slice Conclusion

The first active parity slice should:

- reuse current registry behavior where already sufficient
- add only deterministic derived references
- defer all cases that still require shared mutable runtime registration
```

- [ ] **Step 2: Verify the audit file exists and contains the three categories**

Run:

```powershell
Get-Content docs/design/pipeline-migration/type-reference-audit.md
```

Expected:

- file exists
- contains the three sections:
  - `Already Covered by Current Pipeline Behavior`
  - `Deterministic Derived-Reference Candidates`
  - `Deferred Shared-Runtime-State Cases`

- [ ] **Step 3: Commit the audit artifact**

```powershell
git add docs/design/pipeline-migration/type-reference-audit.md
git commit -m "docs: add type reference audit"
```

## Task 2: Strengthen Design-Side Boundary Characterization Without Adding New Mechanisms

**Files:**
- Modify: `cap4k-plugin-pipeline-generator-design/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignTypeResolverTest.kt`

- [ ] **Step 1: Add failing characterization tests for the resolution order contract**

Add these tests to `DesignTypeResolverTest.kt`:

```kotlin
@Test
fun `explicit fqcn wins over project registry entry with same simple name`() {
    val plan = plan(
        types = listOf("Status", "com.foo.Status"),
        registry = registryOf(SymbolIdentity("com.acme.registry", "Status", source = "project-type-registry")),
    )

    assertEquals("Status", plan.renderedTypes[0].renderedText)
    assertEquals("Status", plan.renderedTypes[1].renderedText)
    assertEquals(listOf("com.foo.Status"), plan.imports)
    assertFalse(plan.renderedTypes[0].qualifiedFallback)
    assertFalse(plan.renderedTypes[1].qualifiedFallback)
}

@Test
fun `project registry remains a short-name fallback and does not invent derived companions`() {
    val ex = assertThrows(IllegalArgumentException::class.java) {
        plan(
            type = "QVideoPost",
            registry = registryOf(SymbolIdentity("com.acme.demo.domain.video", "VideoPost", source = "project-type-registry")),
        )
    }

    assertTrue(ex.message!!.contains("QVideoPost"))
    assertTrue(ex.message!!.contains("unknown"))
}
```

- [ ] **Step 2: Run the targeted design-side resolver tests**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-generator-design:test --tests "*DesignTypeResolverTest"
```

Expected:

- tests may fail if `SymbolIdentity` constructor usage or current helper assumptions need adjustment
- existing resolution-order tests should still be the baseline

- [ ] **Step 3: Adjust the tests to match current constructor and helper contracts, then make them pass**

Use the existing `SymbolIdentity` call pattern already present in this file. The final tests should compile in the same style as:

```kotlin
registryOf(SymbolIdentity("com.acme.demo.domain.types", "UserId"))
```

If `source` is not an exposed constructor parameter, keep the test constructor aligned with the existing test style and rely on the actual default semantics already used by the production code.

- [ ] **Step 4: Re-run the targeted test class**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-generator-design:test --tests "*DesignTypeResolverTest"
```

Expected:

- all tests pass
- the new characterization tests prove no new shared design-side mechanism is required for already-covered cases

- [ ] **Step 5: Commit the design-side characterization**

```powershell
git add cap4k-plugin-pipeline-generator-design/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignTypeResolverTest.kt
git commit -m "test: lock type reference resolution order"
```

## Task 3: Add the Minimal Aggregate-Local Derived Reference Utility

**Files:**
- Create: `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateDerivedTypeReferences.kt`
- Create: `cap4k-plugin-pipeline-generator-aggregate/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateDerivedTypeReferencesTest.kt`

- [ ] **Step 1: Write failing unit tests for deterministic aggregate-side derivation**

Create `AggregateDerivedTypeReferencesTest.kt` with this initial content:

```kotlin
package com.only4.cap4k.plugin.pipeline.generator.aggregate

import com.only4.cap4k.plugin.pipeline.api.CanonicalModel
import com.only4.cap4k.plugin.pipeline.api.EntityModel
import com.only4.cap4k.plugin.pipeline.api.FieldModel
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class AggregateDerivedTypeReferencesTest {

    @Test
    fun `derives entity and q entity fqcn from canonical entity model`() {
        val references = AggregateDerivedTypeReferences.from(
            CanonicalModel(
                entities = listOf(
                    EntityModel(
                        name = "VideoPost",
                        packageName = "com.acme.demo.domain.aggregates.video_post",
                        tableName = "video_post",
                        comment = "Video post entity",
                        fields = listOf(FieldModel("id", "Long")),
                        idField = FieldModel("id", "Long"),
                    )
                )
            )
        )

        assertEquals(
            "com.acme.demo.domain.aggregates.video_post.VideoPost",
            references.entityFqn("VideoPost"),
        )
        assertEquals(
            "com.acme.demo.domain.aggregates.video_post.QVideoPost",
            references.qEntityFqn("VideoPost"),
        )
    }

    @Test
    fun `returns null when entity name is unknown`() {
        val references = AggregateDerivedTypeReferences.from(CanonicalModel())

        assertNull(references.entityFqn("MissingEntity"))
        assertNull(references.qEntityFqn("MissingEntity"))
    }

    @Test
    fun `does not require runtime mutation to answer repeated lookups`() {
        val references = AggregateDerivedTypeReferences.from(
            CanonicalModel(
                entities = listOf(
                    EntityModel(
                        name = "VideoPost",
                        packageName = "com.acme.demo.domain.aggregates.video_post",
                        tableName = "video_post",
                        comment = "Video post entity",
                        fields = listOf(FieldModel("id", "Long")),
                        idField = FieldModel("id", "Long"),
                    )
                )
            )
        )

        assertEquals(references.entityFqn("VideoPost"), references.entityFqn("VideoPost"))
        assertEquals(references.qEntityFqn("VideoPost"), references.qEntityFqn("VideoPost"))
    }
}
```

- [ ] **Step 2: Run the new aggregate derived-reference tests and verify they fail**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-generator-aggregate:test --tests "*AggregateDerivedTypeReferencesTest"
```

Expected:

- FAIL because `AggregateDerivedTypeReferences` does not exist yet

- [ ] **Step 3: Implement the minimal derived-reference utility**

Create `AggregateDerivedTypeReferences.kt` with this implementation:

```kotlin
package com.only4.cap4k.plugin.pipeline.generator.aggregate

import com.only4.cap4k.plugin.pipeline.api.CanonicalModel

internal class AggregateDerivedTypeReferences private constructor(
    private val entityFqns: Map<String, String>,
) {
    fun entityFqn(entityName: String): String? = entityFqns[entityName]

    fun qEntityFqn(entityName: String): String? =
        entityFqns[entityName]?.let { fqcn ->
            val packageName = fqcn.substringBeforeLast('.', missingDelimiterValue = "")
            val simpleName = fqcn.substringAfterLast('.')
            if (packageName.isBlank()) {
                "Q$simpleName"
            } else {
                "$packageName.Q$simpleName"
            }
        }

    companion object {
        fun from(model: CanonicalModel): AggregateDerivedTypeReferences {
            val entities = linkedMapOf<String, String>()
            model.entities.forEach { entity ->
                entities[entity.name] = "${entity.packageName}.${entity.name}"
            }
            return AggregateDerivedTypeReferences(entities)
        }
    }
}
```

- [ ] **Step 4: Re-run the aggregate derived-reference tests**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-generator-aggregate:test --tests "*AggregateDerivedTypeReferencesTest"
```

Expected:

- PASS

- [ ] **Step 5: Commit the derived-reference utility**

```powershell
git add cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateDerivedTypeReferences.kt `
        cap4k-plugin-pipeline-generator-aggregate/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateDerivedTypeReferencesTest.kt
git commit -m "feat: add aggregate derived type references"
```

## Task 4: Use Derived References in Aggregate Planner Context Without Changing Aggregate Contract Breadth

**Files:**
- Modify: `cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/SchemaArtifactPlanner.kt`
- Modify: `cap4k-plugin-pipeline-generator-aggregate/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateArtifactPlannerTest.kt`

- [ ] **Step 1: Add failing planner assertions for derived schema context**

Extend `AggregateArtifactPlannerTest.kt` inside `plans schema entity and repository artifacts into domain and adapter modules` with these assertions:

```kotlin
assertEquals(
    "com.acme.demo.domain.aggregates.video_post.VideoPost",
    planItems.first { it.templateId == "aggregate/schema.kt.peb" }.context["entityTypeFqn"],
)
assertEquals(
    "com.acme.demo.domain.aggregates.video_post.QVideoPost",
    planItems.first { it.templateId == "aggregate/schema.kt.peb" }.context["qEntityTypeFqn"],
)
```

Also add a new test:

```kotlin
@Test
fun `schema planner leaves derived entity references blank when canonical entity is missing`() {
    val config = aggregateConfig()
    val model = CanonicalModel(
        schemas = listOf(
            SchemaModel(
                name = "SVideoPost",
                packageName = "com.acme.demo.domain._share.meta.video_post",
                entityName = "VideoPost",
                comment = "Video post schema",
                fields = listOf(FieldModel("id", "Long")),
            )
        ),
    )

    val schema = AggregateArtifactPlanner().plan(config, model)
        .first { it.templateId == "aggregate/schema.kt.peb" }

    assertEquals("", schema.context["entityTypeFqn"])
    assertEquals("", schema.context["qEntityTypeFqn"])
}
```

- [ ] **Step 2: Run the aggregate planner test class and verify it fails**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-generator-aggregate:test --tests "*AggregateArtifactPlannerTest"
```

Expected:

- FAIL because `SchemaArtifactPlanner` does not yet publish derived type-reference context

- [ ] **Step 3: Implement planner-owned derived context population**

Update `SchemaArtifactPlanner.kt` to use the new helper:

```kotlin
internal class SchemaArtifactPlanner : AggregateArtifactFamilyPlanner {
    override fun plan(config: ProjectConfig, model: CanonicalModel): List<ArtifactPlanItem> {
        val domainRoot = requireRelativeModule(config, "domain")
        val derivedReferences = AggregateDerivedTypeReferences.from(model)

        return model.schemas.map { schema ->
            ArtifactPlanItem(
                generatorId = "aggregate",
                moduleRole = "domain",
                templateId = "aggregate/schema.kt.peb",
                outputPath = "$domainRoot/src/main/kotlin/${schema.packageName.replace(".", "/")}/${schema.name}.kt",
                context = mapOf(
                    "packageName" to schema.packageName,
                    "typeName" to schema.name,
                    "comment" to schema.comment,
                    "entityName" to schema.entityName,
                    "entityTypeFqn" to derivedReferences.entityFqn(schema.entityName).orEmpty(),
                    "qEntityTypeFqn" to derivedReferences.qEntityFqn(schema.entityName).orEmpty(),
                    "fields" to schema.fields,
                ),
                conflictPolicy = config.templates.conflictPolicy,
            )
        }
    }
}
```

- [ ] **Step 4: Re-run aggregate planner tests**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-generator-aggregate:test --tests "*AggregateArtifactPlannerTest"
```

Expected:

- PASS
- existing output-path assertions still pass
- new context-field assertions pass

- [ ] **Step 5: Commit the first production use of derived references**

```powershell
git add cap4k-plugin-pipeline-generator-aggregate/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/SchemaArtifactPlanner.kt `
        cap4k-plugin-pipeline-generator-aggregate/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/aggregate/AggregateArtifactPlannerTest.kt
git commit -m "feat: surface derived aggregate type references"
```

## Task 5: Full Slice Regression and Final Audit Coherence Check

**Files:**
- Verify: `docs/design/pipeline-migration/type-reference-audit.md`
- Verify: `cap4k-plugin-pipeline-generator-design/.../DesignTypeResolverTest.kt`
- Verify: `cap4k-plugin-pipeline-generator-aggregate/...`

- [ ] **Step 1: Run the focused suites for this slice**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-generator-design:test --tests "*DesignTypeResolverTest" `
          :cap4k-plugin-pipeline-generator-aggregate:test --tests "*AggregateDerivedTypeReferencesTest" --tests "*AggregateArtifactPlannerTest"
```

Expected:

- design resolution-order tests pass
- aggregate derived-reference tests pass
- aggregate planner tests pass

- [ ] **Step 2: Run the broader regression suite touched by this slice**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-api:test `
          :cap4k-plugin-pipeline-core:test `
          :cap4k-plugin-pipeline-generator-design:test `
          :cap4k-plugin-pipeline-generator-aggregate:test `
          :cap4k-plugin-pipeline-renderer-pebble:test `
          :cap4k-plugin-pipeline-gradle:test
```

Expected:

- PASS
- no regression to current design-side resolution behavior
- no regression to current aggregate minimal slice

- [ ] **Step 3: Re-read the audit document and check it matches the landed code**

Run:

```powershell
Get-Content docs/design/pipeline-migration/type-reference-audit.md
```

Expected:

- the audit still says:
  - current registry behavior is reused
  - deterministic derivation is added only for convention-owned cases
  - deferred shared-runtime-state cases remain deferred

- [ ] **Step 4: Final status check**

Run:

```powershell
git status --short --branch
```

Expected:

- working tree clean

## Self-Review

### Spec coverage

- Old `typeMapping` usage is audited:
  - Task 1
- Current design-side behavior is explicitly classified as already covered:
  - Task 2
- A minimal deterministic derived-reference mechanism is introduced:
  - Task 3
- The mechanism is placed near planner-owned context construction:
  - Task 4
- No mutable shared runtime map is restored:
  - Tasks 2 through 5 keep all work local and immutable

### Placeholder scan

Run:

```powershell
rg -n "TBD|TODO|implement-later|add-appropriate|similar-to" docs/superpowers/plans/2026-04-17-cap4k-cross-generator-type-reference-parity.md
```

Expected:

- no matches

### Type consistency

This plan consistently uses:

- `AggregateDerivedTypeReferences`
- `entityFqn`
- `qEntityFqn`
- `entityTypeFqn`
- `qEntityTypeFqn`
- `type-reference-audit.md`

Do not rename these during implementation unless the entire plan is updated consistently.

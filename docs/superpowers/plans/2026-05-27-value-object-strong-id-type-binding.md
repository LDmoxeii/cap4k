# Value Object Strong ID Type Binding Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make value-object generation resolve `CanonicalModel.strongIds` so manifest-managed JSON value objects no longer need duplicate `types.registryFile` entries for cap4k-owned Strong IDs.

**Architecture:** Add focused generator tests around `ValueObjectArtifactPlanner` type binding, then centralize the type-binding composition used by value-object generation. The helper composes explicit registry entries, manifest-managed value objects, and canonical Strong IDs in that order, so canonical Strong IDs win over same-name registry entries.

**Tech Stack:** Kotlin, JUnit 5, Gradle, cap4k pipeline generator modules.

---

## File Map

- Modify: `cap4k-plugin-pipeline-generator-types/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/types/ValueObjectArtifactPlannerTest.kt`
  - Adds regression tests for aggregate-root Strong ID binding, `@RefId` Strong ID binding, explicit external registry binding, and same-name precedence.
- Modify: `cap4k-plugin-pipeline-generator-types/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/types/ValueObjectArtifactPlanner.kt`
  - Updates value-object type registry composition to include canonical Strong IDs.
  - Keeps the local helper small and private unless cross-module reuse becomes necessary during implementation.
- Optional Modify: `cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignTypeRegistryBindings.kt`
  - Only change this if a shared helper is placed where both generator modules can consume it without adding awkward dependencies. Otherwise leave design generator behavior unchanged.
- Optional downstream verification edit, not part of the cap4k PR unless explicitly requested: `../cap4k-reference-content-studio/design/types.json`
  - Temporarily remove cap4k-owned Strong ID entries to prove the released/local cap4k fix works.

---

### Task 1: Add Failing Value-Object Strong ID Binding Tests

**Files:**
- Modify: `cap4k-plugin-pipeline-generator-types/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/types/ValueObjectArtifactPlannerTest.kt`

- [ ] **Step 1: Add Strong ID imports to the test file**

Add these imports near the existing `com.only4.cap4k.plugin.pipeline.api.*` imports:

```kotlin
import com.only4.cap4k.plugin.pipeline.api.StrongIdKind
import com.only4.cap4k.plugin.pipeline.api.StrongIdModel
```

- [ ] **Step 2: Add aggregate-root Strong ID test**

Insert this test after `manifest managed value object fields resolve imports from canonical value objects`:

```kotlin
    @Test
    fun `manifest managed value object fields resolve aggregate root strong ids`() {
        val snapshot = ValueObjectArtifactPlanner().plan(
            config(),
            CanonicalModel(
                strongIds = listOf(
                    StrongIdModel(
                        typeName = "ContentId",
                        packageName = "com.acme.demo.domain.aggregates.content",
                        kind = StrongIdKind.AGGREGATE_ROOT,
                        ownerAggregateName = "Content",
                        ownerAggregatePackageName = "com.acme.demo.domain.aggregates.content",
                    ),
                ),
                valueObjects = listOf(
                    ValueObjectModel(
                        name = "ContentSnapshot",
                        packageName = "com.acme.demo.domain.aggregates.audit.values",
                        scope = ValueObjectScope.AGGREGATE,
                        aggregate = "Audit",
                        fields = listOf(FieldModel("contentId", "ContentId")),
                    ),
                ),
            ),
        ).single()

        assertEquals(listOf("com.acme.demo.domain.aggregates.content.ContentId"), snapshot.context["imports"])

        val fields = snapshot.context["fields"] as List<*>
        assertEquals(
            mapOf("name" to "contentId", "type" to "ContentId", "renderedType" to "ContentId", "nullable" to false),
            fields.single(),
        )
    }
```

- [ ] **Step 3: Add reference Strong ID test**

Insert this test after the aggregate-root test:

```kotlin
    @Test
    fun `manifest managed value object fields resolve reference strong ids`() {
        val snapshot = ValueObjectArtifactPlanner().plan(
            config(),
            CanonicalModel(
                strongIds = listOf(
                    StrongIdModel(
                        typeName = "ReviewerId",
                        packageName = "com.acme.demo.domain.shared.ids",
                        kind = StrongIdKind.REFERENCE,
                    ),
                ),
                valueObjects = listOf(
                    ValueObjectModel(
                        name = "ReviewSnapshot",
                        packageName = "com.acme.demo.domain.aggregates.review.values",
                        scope = ValueObjectScope.AGGREGATE,
                        aggregate = "Review",
                        fields = listOf(FieldModel("reviewerId", "ReviewerId")),
                    ),
                ),
            ),
        ).single()

        assertEquals(listOf("com.acme.demo.domain.shared.ids.ReviewerId"), snapshot.context["imports"])

        val fields = snapshot.context["fields"] as List<*>
        assertEquals(
            mapOf("name" to "reviewerId", "type" to "ReviewerId", "renderedType" to "ReviewerId", "nullable" to false),
            fields.single(),
        )
    }
```

- [ ] **Step 4: Add canonical Strong ID precedence test**

Insert this test after the reference Strong ID test:

```kotlin
    @Test
    fun `canonical strong ids override same name registry entries`() {
        val snapshot = ValueObjectArtifactPlanner().plan(
            config(
                typeRegistry = TypeRegistryConfig(
                    entries = mapOf(
                        "ContentId" to TypeRegistryEntry("com.acme.external.ContentId"),
                    ),
                ),
            ),
            CanonicalModel(
                strongIds = listOf(
                    StrongIdModel(
                        typeName = "ContentId",
                        packageName = "com.acme.demo.domain.aggregates.content",
                        kind = StrongIdKind.AGGREGATE_ROOT,
                        ownerAggregateName = "Content",
                        ownerAggregatePackageName = "com.acme.demo.domain.aggregates.content",
                    ),
                ),
                valueObjects = listOf(
                    ValueObjectModel(
                        name = "ContentSnapshot",
                        packageName = "com.acme.demo.domain.aggregates.audit.values",
                        scope = ValueObjectScope.AGGREGATE,
                        aggregate = "Audit",
                        fields = listOf(FieldModel("contentId", "ContentId")),
                    ),
                ),
            ),
        ).single()

        assertEquals(listOf("com.acme.demo.domain.aggregates.content.ContentId"), snapshot.context["imports"])
    }
```

- [ ] **Step 5: Keep explicit external registry behavior covered**

The existing test `plans checked in json value object under domain module using declared package` already verifies that `CurrencyCode` from `TypeRegistryConfig` imports `com.acme.demo.domain.shared.types.CurrencyCode`. Leave that assertion in place:

```kotlin
        assertEquals(
            listOf("com.acme.demo.domain.shared.types.CurrencyCode", "java.math.BigDecimal"),
            item.context["imports"],
        )
```

- [ ] **Step 6: Extend the `config` helper to allow registry override**

Replace the helper at the bottom of the test file with this version:

```kotlin
    private fun config(
        modules: Map<String, String> = mapOf("domain" to "demo-domain"),
        typeRegistry: TypeRegistryConfig = TypeRegistryConfig(
            entries = mapOf(
                "CurrencyCode" to TypeRegistryEntry("com.acme.demo.domain.shared.types.CurrencyCode"),
            ),
        ),
    ): ProjectConfig =
        ProjectConfig(
            basePackage = "com.acme.demo",
            layout = ProjectLayout.MULTI_MODULE,
            modules = modules,
            generators = mapOf("types-value-object" to GeneratorConfig(enabled = true)),
            templates = TemplateConfig("ddd-default", emptyList(), ConflictPolicy.SKIP),
            typeRegistry = typeRegistry,
        )
```

- [ ] **Step 7: Run the focused test and confirm failure**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-generator-types:test --tests "com.only4.cap4k.plugin.pipeline.generator.types.ValueObjectArtifactPlannerTest"
```

Expected: FAIL. The new Strong ID tests should fail because `ValueObjectArtifactPlanner` still does not merge `CanonicalModel.strongIds` into its type registry, so the imports for `ContentId` and `ReviewerId` are empty or incorrect.

- [ ] **Step 8: Commit the failing tests**

```powershell
git add cap4k-plugin-pipeline-generator-types/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/types/ValueObjectArtifactPlannerTest.kt
git commit -m "test: cover value object strong id type binding"
```

---

### Task 2: Implement Canonical Strong ID Binding For Value Objects

**Files:**
- Modify: `cap4k-plugin-pipeline-generator-types/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/types/ValueObjectArtifactPlanner.kt`

- [ ] **Step 1: Replace local type registry composition**

In `ValueObjectArtifactPlanner.plan`, replace:

```kotlin
        val typeRegistry = config.typeRegistryFqns() + model.manifestValueObjectTypeLookup()
```

with:

```kotlin
        val typeRegistry = config.valueObjectTypeRegistryFqns(model)
```

- [ ] **Step 2: Add the value-object type registry helper**

Replace the existing `manifestValueObjectTypeLookup` helper at the bottom of `ValueObjectArtifactPlanner.kt` with this code:

```kotlin
private fun ProjectConfig.valueObjectTypeRegistryFqns(model: CanonicalModel): Map<String, String> =
    typeRegistryFqns() +
        model.manifestValueObjectTypeLookup() +
        model.strongIdTypeLookup()

private fun CanonicalModel.manifestValueObjectTypeLookup(): Map<String, String> =
    valueObjects
        .groupBy { it.name }
        .filterValues { matches -> matches.size == 1 }
        .mapValues { (_, matches) ->
            val valueObject = matches.single()
            "${valueObject.packageName}.${valueObject.name}"
        }

private fun CanonicalModel.strongIdTypeLookup(): Map<String, String> =
    strongIds
        .groupBy { it.typeName }
        .filterValues { matches -> matches.size == 1 }
        .mapValues { (_, matches) ->
            val strongId = matches.single()
            "${strongId.packageName}.${strongId.typeName}"
        }
```

This intentionally uses the same `+` precedence style as the design generator. Since `strongIdTypeLookup()` is last, canonical Strong IDs override same-name explicit registry entries and manifest value-object names.

- [ ] **Step 3: Run the focused tests**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-generator-types:test --tests "com.only4.cap4k.plugin.pipeline.generator.types.ValueObjectArtifactPlannerTest"
```

Expected: PASS.

- [ ] **Step 4: Run related design generator tests to catch behavior drift**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-generator-design:test --tests "*DesignCommandArtifactPlannerTest" --tests "*DesignSagaArtifactPlannerTest"
```

Expected: PASS. These tests should be unaffected because the design generator binding behavior has not changed.

- [ ] **Step 5: Commit the implementation**

```powershell
git add cap4k-plugin-pipeline-generator-types/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/types/ValueObjectArtifactPlanner.kt
git commit -m "fix: bind strong ids in value object generator"
```

---

### Task 3: Verify Dogfood Scenario Against Reference Project

**Files:**
- Temporarily modify outside this cap4k branch for verification only: `../cap4k-reference-content-studio/design/types.json`
- Do not commit downstream changes unless the user explicitly asks for a reference-project PR after cap4k implementation is merged or locally published.

- [ ] **Step 1: Publish cap4k locally from the implementation branch**

Run from the cap4k worktree:

```powershell
./gradlew publishToMavenLocal
```

Expected: SUCCESS. This makes the changed plugin available to `cap4k-reference-content-studio` if its plugin resolution is configured to consume `mavenLocal()` or an included local version.

- [ ] **Step 2: Temporarily remove cap4k-owned Strong ID registry entries**

In `../cap4k-reference-content-studio/design/types.json`, temporarily remove these entries:

```json
"ContentId": {
  "fqn": "com.only4.cap4k.reference.contentstudio.domain.aggregates.content.ContentId"
},
"MediaProcessingTaskId": {
  "fqn": "com.only4.cap4k.reference.contentstudio.domain.aggregates.media_processing_task.MediaProcessingTaskId"
},
"PaidPublicationTaskId": {
  "fqn": "com.only4.cap4k.reference.contentstudio.domain.aggregates.paid_publication_task.PaidPublicationTaskId"
},
"ReviewerId": {
  "fqn": "com.only4.cap4k.reference.contentstudio.domain.shared.ids.ReviewerId"
}
```

If the file becomes empty, replace it with:

```json
{}
```

- [ ] **Step 3: Regenerate value-object output in the reference project**

Run from `../cap4k-reference-content-studio`:

```powershell
./gradlew cap4kGenerate
```

Expected: SUCCESS. The generated `MediaProcessingResultSnapshot` should still import and render `MediaProcessingTaskId` and `ContentId` correctly.

- [ ] **Step 4: Inspect the generated value object**

Check:

```powershell
rg -n "import .*ContentId|import .*MediaProcessingTaskId|val contentId|val mediaProcessingTaskId" cap4k-reference-content-studio-domain/src/main/kotlin
```

Expected: imports point to:

```text
com.only4.cap4k.reference.contentstudio.domain.aggregates.content.ContentId
com.only4.cap4k.reference.contentstudio.domain.aggregates.media_processing_task.MediaProcessingTaskId
```

and fields render as:

```kotlin
val mediaProcessingTaskId: MediaProcessingTaskId
val contentId: ContentId
```

- [ ] **Step 5: Restore or keep downstream edit based on user direction**

If the user wants only cap4k implementation in this PR, restore the temporary reference-project edit:

```powershell
git -C ../cap4k-reference-content-studio diff -- design/types.json
```

Then use a non-destructive editor or `apply_patch` to restore `design/types.json` to its prior content.

If the user wants a follow-up reference-project PR after cap4k is merged/released, leave the downstream cleanup for that PR and document the verification result in #93.

- [ ] **Step 6: Commit no cap4k changes for dogfood verification**

No cap4k commit is expected in this task unless verification exposes a missing test or implementation bug. If verification finds a bug, add a focused failing test in Task 1 style, fix it in Task 2 style, and commit the additional test/fix before continuing.

---

### Task 4: Final Verification And PR Update

**Files:**
- Modify only if needed: `docs/superpowers/plans/2026-05-27-value-object-strong-id-type-binding.md`
- GitHub issue/PR metadata for #93 and the implementation PR.

- [ ] **Step 1: Run the full affected module tests**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-generator-types:test :cap4k-plugin-pipeline-generator-design:test
```

Expected: SUCCESS.

- [ ] **Step 2: Check git status**

Run:

```powershell
git status --short --branch
```

Expected: clean working tree on the implementation branch, except for intentional committed changes.

- [ ] **Step 3: Push the implementation branch**

Run:

```powershell
git push
```

Expected: branch updates successfully.

- [ ] **Step 4: Update #93 lifecycle**

After the implementation PR is opened, update #93 with:

```markdown
Implementation PR opened:

- PR: link to the implementation pull request opened from the implementation branch
- Tests: `./gradlew :cap4k-plugin-pipeline-generator-types:test :cap4k-plugin-pipeline-generator-design:test`
- Dogfood verification: record whether `cap4k-reference-content-studio` generation was run, and record whether `design/types.json` cleanup was only temporary verification or will be handled in a downstream PR

Remaining:

- implementation merged
- released if required
- downstream verified if required
```

Do not close #93 until implementation is merged, release status is clear, and downstream verification is complete.

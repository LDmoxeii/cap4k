# Cap4k Template-Level Conflict Policy Override Implementation Plan

> **For agentic workers:** Follow the approved spec exactly. Keep scope in `cap4k-plugin-pipeline-api`, `cap4k-plugin-pipeline-core`, `cap4k-plugin-pipeline-gradle`, and only touch planner sites if the shared planning layer proves insufficient.

**Goal:** Support template-level conflict policy overrides keyed by `templateId`, keep generated-source ownership unchanged, and make final resolved per-item conflict policy visible through existing `ArtifactPlanItem.conflictPolicy` in `cap4kPlan`.

**Architecture:** Add a direct template-level override map to `TemplateConfig`, map it from the Gradle `templates` DSL, and resolve effective conflict policy centrally in `DefaultPipelineRunner` after planners emit raw `ArtifactPlanItem`s. Preserve current planner defaults and generated-source overwrite behavior. Use `cap4kPlan` item `conflictPolicy` as the stable resolved output field rather than introducing a second resolved-policy field.

**Tech Stack:** Kotlin, Gradle plugin DSL/config factories, JUnit 5 module tests, Gradle functional tests.

**Issue Positioning:** This plan answers `#11` as a template-level override keyed by `templateId`, which is the current stable pipeline identity in this repository. It does not widen into per-output-path selection, broad template override redesign, family-level override systems, or generator ownership changes.

---

### Task 1: Lock API And Config Contract For Template-Level Overrides

**Files:**
- Modify: `cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/ProjectConfig.kt`
- Modify: `cap4k-plugin-pipeline-api/src/test/kotlin/com/only4/cap4k/plugin/pipeline/api/ProjectConfigTest.kt`
- Modify: `cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kExtension.kt`
- Modify: `cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kProjectConfigFactory.kt`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kProjectConfigFactoryTest.kt`

- [ ] **Step 1: Add failing API/config tests**

Add or extend tests to prove:

1. `TemplateConfig` carries `templateConflictPolicies`
2. Gradle `templates` DSL exposes an empty default map
3. config factory trims and maps:

```kotlin
templates {
    templateConflictPolicies.put(" aggregate/factory.kt.peb ", " overwrite ")
    templateConflictPolicies.put("aggregate/behavior.kt.peb", "FAIL")
}
```

into:

```kotlin
mapOf(
    "aggregate/factory.kt.peb" to ConflictPolicy.OVERWRITE,
    "aggregate/behavior.kt.peb" to ConflictPolicy.FAIL,
)
```

4. blank template ids fail fast with a clear error

- [ ] **Step 2: Run targeted API and Gradle config tests and verify they fail**

Run:

```powershell
.\gradlew.bat :cap4k-plugin-pipeline-api:test --tests "com.only4.cap4k.plugin.pipeline.api.ProjectConfigTest"
.\gradlew.bat :cap4k-plugin-pipeline-gradle:test --tests "com.only4.cap4k.plugin.pipeline.gradle.Cap4kProjectConfigFactoryTest"
```

Expected: FAIL before implementation because `TemplateConfig` and Gradle DSL do not yet support template-level policy maps.

- [ ] **Step 3: Implement the API and config mapping**

Implement:

- `TemplateConfig.templateConflictPolicies: Map<String, ConflictPolicy> = emptyMap()`
- `Cap4kTemplatesExtension.templateConflictPolicies`
- config-factory normalization and parsing

Keep the model intentionally direct:

- raw `templateId` key
- raw `ConflictPolicy` value

- [ ] **Step 4: Re-run targeted tests and verify they pass**

Run the same two commands again.

Expected: PASS.

---

### Task 2: Resolve Effective Conflict Policy In The Shared Planning Layer

**Files:**
- Modify: `cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultPipelineRunner.kt`
- Modify: `cap4k-plugin-pipeline-core/src/test/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultPipelineRunnerTest.kt`

- [ ] **Step 1: Add failing planning-layer regression tests**

Add tests that use `DefaultPipelineRunner` with a fake generator and prove:

1. checked-in template override beats planner default

Example shape:

```kotlin
ArtifactPlanItem(
    generatorId = "design-command",
    moduleRole = "application",
    templateId = "design/command.kt.peb",
    outputPath = "demo-application/src/main/kotlin/com/acme/demo/application/commands/CreateOrderCmd.kt",
    conflictPolicy = ConflictPolicy.SKIP,
)
```

with:

```kotlin
templates = TemplateConfig(
    preset = "ddd-default",
    overrideDirs = emptyList(),
    conflictPolicy = ConflictPolicy.SKIP,
    templateConflictPolicies = mapOf("design/command.kt.peb" to ConflictPolicy.OVERWRITE),
)
```

Expected resolved item policy:

- `ConflictPolicy.OVERWRITE`

2. generated-source items stay `OVERWRITE` even when targeted with a conflicting template override

Example:

```kotlin
ArtifactPlanItem(
    generatorId = "aggregate",
    moduleRole = "domain",
    templateId = "aggregate/entity.kt.peb",
    outputPath = "demo-domain/build/generated/cap4k/main/kotlin/com/acme/demo/domain/aggregates/order/Order.kt",
    conflictPolicy = ConflictPolicy.OVERWRITE,
    outputKind = ArtifactOutputKind.GENERATED_SOURCE,
)
```

with:

```kotlin
templateConflictPolicies = mapOf("aggregate/entity.kt.peb" to ConflictPolicy.FAIL)
```

Expected resolved item policy:

- still `ConflictPolicy.OVERWRITE`

Assert both:

- returned `PipelineResult.planItems`
- renderer-received plan items

- [ ] **Step 2: Run targeted core test and verify it fails**

Run:

```powershell
.\gradlew.bat :cap4k-plugin-pipeline-core:test --tests "com.only4.cap4k.plugin.pipeline.core.DefaultPipelineRunnerTest"
```

Expected: FAIL before resolution logic exists.

- [ ] **Step 3: Implement shared resolution in `DefaultPipelineRunner`**

Add a runner-local resolver with this precedence:

1. `GENERATED_SOURCE -> OVERWRITE`
2. `config.templates.templateConflictPolicies[templateId]`
3. planner-emitted `item.conflictPolicy`

Apply it after planner emission and before render/export.

- [ ] **Step 4: Re-run targeted core test and verify it passes**

Run the same command again.

Expected: PASS.

---

### Task 3: Prove End-To-End `cap4kPlan` Output On Mixed Surfaces

**Files:**
- Modify: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginFunctionalTest.kt`

- [ ] **Step 1: Add a failing functional regression test for mixed surfaces**

Use an existing aggregate fixture that plans both:

- generated-source aggregate entity
- checked-in behavior
- checked-in factory or specification

Patch its `build.gradle.kts` inside the functional test to add:

```kotlin
templates {
    templateConflictPolicies.put("aggregate/factory.kt.peb", "OVERWRITE")
    templateConflictPolicies.put("aggregate/behavior.kt.peb", "FAIL")
    templateConflictPolicies.put("aggregate/entity.kt.peb", "FAIL")
}
```

Then assert from `build/cap4k/plan.json`:

- `aggregate/factory.kt.peb` resolves to `OVERWRITE`
- `aggregate/behavior.kt.peb` resolves to `FAIL`
- `aggregate/entity.kt.peb` still resolves to `OVERWRITE`

This proves predictability across mixed checked-in and generated-source surfaces.

- [ ] **Step 2: Run the targeted functional test and verify it fails**

Run:

```powershell
.\gradlew.bat :cap4k-plugin-pipeline-gradle:test --tests "com.only4.cap4k.plugin.pipeline.gradle.PipelinePluginFunctionalTest.*conflict*"
```

If the exact test filter becomes awkward, use the full class:

```powershell
.\gradlew.bat :cap4k-plugin-pipeline-gradle:test --tests "com.only4.cap4k.plugin.pipeline.gradle.PipelinePluginFunctionalTest"
```

Expected: FAIL before runner resolution and config mapping are complete.

- [ ] **Step 3: Re-run the targeted functional test and verify it passes**

Run the same command after implementation.

Expected: PASS.

---

### Task 4: Regress Relevant Modules And Verify No Scope Creep

**Files:**
- No new owned files expected beyond Task 1-3

- [ ] **Step 1: Run the minimal regression command set**

Run:

```powershell
.\gradlew.bat :cap4k-plugin-pipeline-api:test
.\gradlew.bat :cap4k-plugin-pipeline-core:test
.\gradlew.bat :cap4k-plugin-pipeline-gradle:test
```

Expected: PASS.

- [ ] **Step 2: Verify changed file set stays in approved scope**

Expected changed code files should be limited to:

- API config model
- Gradle extension/config factory
- shared planning runner
- functional and module tests

Planner-specific production files should remain untouched unless implementation proves the shared resolution layer is insufficient.

## Self-Review

### 1. Scope check

- broad template override redesign: not planned
- family-level override system: not planned
- relation-level override system: not planned
- generator ownership redesign: not planned
- wrapper cleanup: not planned
- legacy codegen work: not planned

### 2. Contract check

- `ArtifactPlanItem.conflictPolicy` remains the stable resolved output field
- generated-source items remain `OVERWRITE`
- non-generated items can be overridden by `templateId`
- planner defaults remain the fallback when no template override exists

### 3. Future strengthening intentionally left out

Still future work after this slice:

- per-output-path or per-entity-instance overrides
- family aliases instead of raw `templateId` keys
- diagnostics/warnings for ignored generated-source overrides
- separate plan-item metadata describing whether a resolved policy came from global default, template override, or generated-source fixed behavior

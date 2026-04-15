# Cap4k Design Api Payload Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add bounded `api_payload` pipeline support so standard `api_payload` design entries generate helper-first payload objects into the adapter module under `adapter.portal.api.payload`, using the new nested-type hierarchy contract.

**Architecture:** Add a dedicated canonical api-payload slice, expose a standalone Gradle generator `designApiPayload`, and implement an adapter-side planner with one bounded template id. Keep the migration intentionally narrow: only standard `api_payload` tags are accepted, output always lands in `adapter.portal.api.payload`, and nested types follow the current pipeline contract by rendering under `Request` and `Response` instead of flat under the outer object.

**Tech Stack:** Kotlin, Gradle, JUnit 5, Gradle TestKit, Pebble templates

---

## File Structure

- Modify: `cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModels.kt`
- Modify: `cap4k-plugin-pipeline-api/src/test/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModelsTest.kt`
- Modify: `cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssembler.kt`
- Modify: `cap4k-plugin-pipeline-core/src/test/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssemblerTest.kt`
- Modify: `cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kExtension.kt`
- Modify: `cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kProjectConfigFactory.kt`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kProjectConfigFactoryTest.kt`
- Create: `cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignApiPayloadArtifactPlanner.kt`
- Create: `cap4k-plugin-pipeline-generator-design/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignApiPayloadArtifactPlannerTest.kt`
- Modify: `cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignRenderModelFactory.kt`
- Modify: `cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePlugin.kt`
- Create: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/design/api_payload.kt.peb`
- Modify: `cap4k-plugin-pipeline-renderer-pebble/src/test/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PebbleArtifactRendererTest.kt`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-api-payload-sample/build.gradle.kts`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-api-payload-sample/settings.gradle.kts`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-api-payload-sample/design/design.json`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-api-payload-sample/codegen/templates/design/api_payload.kt.peb`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginFunctionalTest.kt`

### Task 1: Add Canonical And Gradle Support For Api Payload Family

**Files:**
- Modify: `cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModels.kt`
- Modify: `cap4k-plugin-pipeline-api/src/test/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModelsTest.kt`
- Modify: `cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssembler.kt`
- Modify: `cap4k-plugin-pipeline-core/src/test/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssemblerTest.kt`
- Modify: `cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kExtension.kt`
- Modify: `cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kProjectConfigFactory.kt`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kProjectConfigFactoryTest.kt`

- [ ] **Step 1: Add failing API and canonical tests for api-payload slice**

Add tests that prove:

- `CanonicalModel` keeps a dedicated `apiPayloads` slice alongside `requests`, `validators`, and aggregate slices
- `api_payload` entries assemble into `apiPayloads`
- old aliases `payload`, `request_payload`, `req_payload`, `request`, and `req` are skipped in this slice
- api-payload naming does not append a suffix and still normalizes `batchSaveAccountList` into `BatchSaveAccountList`
- request and response fields are preserved on `ApiPayloadModel`
- request assembly remains unchanged when api-payload entries are present

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-api:test :cap4k-plugin-pipeline-core:test --tests "*payload*" --rerun-tasks
```

Expected: FAIL because `ApiPayloadModel` and `CanonicalModel.apiPayloads` do not exist yet.

- [ ] **Step 2: Implement minimal canonical api-payload support**

Extend the API model with:

```kotlin
data class ApiPayloadModel(
    val packageName: String,
    val typeName: String,
    val description: String,
    val requestFields: List<FieldModel> = emptyList(),
    val responseFields: List<FieldModel> = emptyList(),
)
```

and extend `CanonicalModel` with:

```kotlin
val apiPayloads: List<ApiPayloadModel> = emptyList()
```

Update `DefaultCanonicalAssembler` so:

- only `entry.tag.lowercase(Locale.ROOT) == "api_payload"` is accepted for api-payload assembly
- `typeName` is normalized to old-compatible UpperCamel form without adding a suffix
- `requestFields` and `responseFields` are copied into `model.apiPayloads`
- api-payload entries are placed in `model.apiPayloads`
- non-api-payload entries keep flowing through the existing request and validator assembly unchanged

- [ ] **Step 3: Add failing config tests for `designApiPayload`**

Add tests that prove:

- `designApiPayload.enabled` defaults to `false`
- enabling it wires generator id `design-api-payload`
- `designApiPayload` requires `project.adapterModulePath`
- `designApiPayload` requires enabled `designJson`

Use these exact failure messages:

```kotlin
"project.adapterModulePath is required when designApiPayload is enabled."
"designApiPayload generator requires enabled designJson source."
```

- [ ] **Step 4: Implement Gradle DSL and config wiring**

Add generator extension and DSL block:

```kotlin
val designApiPayload: DesignApiPayloadGeneratorExtension
fun designApiPayload(block: DesignApiPayloadGeneratorExtension.() -> Unit)
```

Wire generator id in `Cap4kProjectConfigFactory`:

```kotlin
"design-api-payload"
```

and add only these dependency rules:

- `designApiPayload` requires enabled `designJson`
- `designApiPayload` requires `project.adapterModulePath`

- [ ] **Step 5: Run focused API, core, and config tests**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-api:test :cap4k-plugin-pipeline-core:test :cap4k-plugin-pipeline-gradle:test --tests "*payload*" --rerun-tasks
```

Expected: PASS for the new api-payload model, canonical assembly, and config tests.

- [ ] **Step 6: Commit the canonical/DSL slice**

```bash
git add cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModels.kt cap4k-plugin-pipeline-api/src/test/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModelsTest.kt cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssembler.kt cap4k-plugin-pipeline-core/src/test/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssemblerTest.kt cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kExtension.kt cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kProjectConfigFactory.kt cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kProjectConfigFactoryTest.kt
git commit -m "feat: add design api payload config"
```

### Task 2: Add Api Payload Planner And Nested Hierarchy Support

**Files:**
- Create: `cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignApiPayloadArtifactPlanner.kt`
- Create: `cap4k-plugin-pipeline-generator-design/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignApiPayloadArtifactPlannerTest.kt`
- Modify: `cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignRenderModelFactory.kt`
- Modify: `cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePlugin.kt`

- [ ] **Step 1: Add failing planner tests for api-payload path and nested hierarchy**

Add tests that assert:

- `generatorId == "design-api-payload"`
- `templateId == "design/api_payload.kt.peb"`
- output path ends with:
  - `adapter/portal/api/payload/account/BatchSaveAccountList.kt`
- context includes:
  - `packageName = "com.acme.demo.adapter.portal.api.payload.account"`
  - `typeName = "BatchSaveAccountList"`
  - `description = "batch save account list payload"`
- request nested types render as `requestNestedTypes`
- response nested types render as `responseNestedTypes`
- nested type names stay out of the outer object level

Use one representative payload with these fields:

```kotlin
requestFields = listOf(
    FieldModel("address", "Address", nullable = true),
    FieldModel("address.city", "String"),
    FieldModel("address.zipCode", "String"),
)
responseFields = listOf(
    FieldModel("result", "Result", nullable = true),
    FieldModel("result.success", "Boolean"),
)
```

Add planner boundary tests that also prove:

- non-api-payload canonical slices are ignored
- missing adapter module fails clearly
- nested group without a compatible direct root field fails
- nested group with an incompatible direct root field fails

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-generator-design:test --tests "*api payload*" --rerun-tasks
```

Expected: FAIL because the planner does not exist yet and `DesignRenderModelFactory` has no api-payload entry point.

- [ ] **Step 2: Add minimal api-payload render-model creation on top of the current nested-type contract**

Extend `DesignRenderModelFactory` with a dedicated entry point:

```kotlin
fun createForApiPayload(
    packageName: String,
    payload: ApiPayloadModel,
    typeRegistry: Map<String, String> = emptyMap(),
): DesignRenderModel
```

Implement it so it reuses the current namespace preparation rules:

- request fields build `requestFields` and `requestNestedTypes`
- response fields build `responseFields` and `responseNestedTypes`
- nested field paths must have exactly one level
- direct root field compatibility is enforced exactly as in the current request-family contract

Do not introduce a flat `nestedTypes` list for api payloads.

- [ ] **Step 3: Implement the api-payload planner and provider registration**

Create `DesignApiPayloadArtifactPlanner` so it:

- reads `model.apiPayloads`
- emits `design/api_payload.kt.peb`
- writes to:
  - `<adapterRoot>/src/main/kotlin/<base>/adapter/portal/api/payload/<package>/<TypeName>.kt`
- sets package name to:
  - `<basePackage>.adapter.portal.api.payload.<package>`
- uses `DesignRenderModelFactory.createForApiPayload(...)`

Register `DesignApiPayloadArtifactPlanner()` in `PipelinePlugin.buildRunner(...)` beside the other bounded design-family planners.

- [ ] **Step 4: Re-run focused planner tests and compile verification**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-generator-design:test --tests "*api payload*" --rerun-tasks
./gradlew :cap4k-plugin-pipeline-gradle:compileKotlin --rerun-tasks
```

Expected: PASS.

- [ ] **Step 5: Commit the planner slice**

```bash
git add cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignApiPayloadArtifactPlanner.kt cap4k-plugin-pipeline-generator-design/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignApiPayloadArtifactPlannerTest.kt cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignRenderModelFactory.kt cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePlugin.kt
git commit -m "feat: add design api payload planner"
```

### Task 3: Add Helper-First Api Payload Template And Functional Coverage

**Files:**
- Create: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/design/api_payload.kt.peb`
- Modify: `cap4k-plugin-pipeline-renderer-pebble/src/test/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PebbleArtifactRendererTest.kt`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-api-payload-sample/build.gradle.kts`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-api-payload-sample/settings.gradle.kts`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-api-payload-sample/design/design.json`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-api-payload-sample/codegen/templates/design/api_payload.kt.peb`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginFunctionalTest.kt`

- [ ] **Step 1: Add failing renderer tests for the api-payload contract**

Add renderer tests that prove:

- `design/api_payload.kt.peb` renders the outer payload object
- `Request` and `Response` render with helper-driven `imports(imports)`
- request nested types render inside `Request`
- response nested types render inside `Response`
- nested types do not render flat at the outer object level
- default values render from Kotlin-ready `defaultValue`
- override template resolution works for `design/api_payload.kt.peb`

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-renderer-pebble:test --tests "*api payload*" --rerun-tasks
```

Expected: FAIL because the preset template does not exist yet.

- [ ] **Step 2: Implement the bounded api-payload preset**

Create `design/api_payload.kt.peb` so it keeps the old outer shape:

```pebble
package {{ packageName }}
{% for import in imports(imports) %}
import {{ import }}
{% endfor %}

object {{ typeName }} {
```

and adopts the new nested hierarchy:

- `requestNestedTypes` render only inside `Request`
- `responseNestedTypes` render only inside `Response`
- no flat `nestedTypes` block at the outer object level

The generated artifact must retain:

- outer `object {{ typeName }}`
- nested `Request`
- nested `Response`
- Kotlin-ready defaults on request, response, and nested fields

- [ ] **Step 3: Add failing functional tests against an isolated api-payload fixture**

Add functional tests that prove:

- `cap4kPlan` emits `design/api_payload.kt.peb`
- `cap4kGenerate` writes a payload file under `adapter/portal/api/payload`
- override template replacement works for `design/api_payload.kt.peb`
- invalid config fails when:
  - `designApiPayload` lacks `adapterModulePath`
  - `designApiPayload` is enabled without enabled `designJson`

- [ ] **Step 4: Create the isolated api-payload fixture**

Create `design-api-payload-sample/build.gradle.kts` with:

```kotlin
plugins {
    id("com.only4.cap4k.plugin.pipeline")
}

cap4k {
    project {
        basePackage.set("com.acme.demo")
        adapterModulePath.set("demo-adapter")
    }
    sources {
        designJson {
            enabled.set(true)
            files.from("design/design.json")
        }
    }
    generators {
        designApiPayload {
            enabled.set(true)
        }
    }
}
```

Create `design-api-payload-sample/design/design.json` with one representative entry that proves the new nested hierarchy:

```json
[
  {
    "tag": "api_payload",
    "package": "account",
    "name": "BatchSaveAccountList",
    "desc": "batch save account list payload",
    "aggregates": [],
    "requestFields": [
      { "name": "address", "type": "Address", "nullable": true },
      { "name": "address.city", "type": "String" },
      { "name": "address.zipCode", "type": "String", "defaultValue": "000000" }
    ],
    "responseFields": [
      { "name": "result", "type": "Result", "nullable": true },
      { "name": "result.success", "type": "Boolean", "defaultValue": "true" }
    ]
  }
]
```

Create override template `codegen/templates/design/api_payload.kt.peb` containing the exact marker:

```pebble
// override: representative api payload migration template
```

- [ ] **Step 5: Run renderer and functional verification**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-renderer-pebble:test --tests "*api payload*" --rerun-tasks
./gradlew :cap4k-plugin-pipeline-gradle:test --tests "*api payload*" --rerun-tasks
```

Expected: PASS with the api-payload preset resolved from `ddd-default`, the isolated fixture generating files under `adapter/portal/api/payload`, nested request and response types rendered under their owning containers, override replacement taking effect, and invalid config failures surfacing the expected messages.

- [ ] **Step 6: Commit the template/functional slice**

```bash
git add cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/design/api_payload.kt.peb cap4k-plugin-pipeline-renderer-pebble/src/test/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PebbleArtifactRendererTest.kt cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-api-payload-sample/build.gradle.kts cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-api-payload-sample/settings.gradle.kts cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-api-payload-sample/design/design.json cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-api-payload-sample/codegen/templates/design/api_payload.kt.peb cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginFunctionalTest.kt
git commit -m "test: cover design api payload migration flow"
```

### Task 4: Run Full Verification For The Slice

**Files:**
- Verify only: `cap4k-plugin-pipeline-api`
- Verify only: `cap4k-plugin-pipeline-core`
- Verify only: `cap4k-plugin-pipeline-generator-design`
- Verify only: `cap4k-plugin-pipeline-renderer-pebble`
- Verify only: `cap4k-plugin-pipeline-gradle`

- [ ] **Step 1: Run full module tests**

```powershell
./gradlew :cap4k-plugin-pipeline-api:test
./gradlew :cap4k-plugin-pipeline-core:test
./gradlew :cap4k-plugin-pipeline-generator-design:test
./gradlew :cap4k-plugin-pipeline-renderer-pebble:test
./gradlew :cap4k-plugin-pipeline-gradle:test
```

Expected: all PASS.

- [ ] **Step 2: Run combined verification**

```powershell
./gradlew :cap4k-plugin-pipeline-api:test :cap4k-plugin-pipeline-core:test :cap4k-plugin-pipeline-generator-design:test :cap4k-plugin-pipeline-renderer-pebble:test :cap4k-plugin-pipeline-gradle:test
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Confirm branch cleanliness and landed commits**

```powershell
git status --short --branch
git log --oneline --max-count=8
```

Expected:

- clean working tree
- recent commits covering:
  - canonical api-payload family support
  - api-payload DSL/config
  - api-payload planner/provider registration
  - api-payload preset template
  - api-payload renderer and functional migration coverage

## Self-Review

- Spec coverage: the plan covers canonical/model changes, Gradle DSL, adapter-side planner output, new nested hierarchy enforcement, helper-first template migration, and functional validation.
- Placeholder scan: no `TBD`, `TODO`, or unresolved “similar to previous task” references remain.
- Type consistency: the plan uses one generator name pair consistently:
  - public DSL: `designApiPayload`
  - internal generator id: `design-api-payload`

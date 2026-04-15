# Cap4k Design Validator Family Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add bounded `validator` pipeline support so standard `validator` design entries generate helper-first annotation validators into the application module under `application.validators`.

**Architecture:** Add a dedicated canonical validator slice, expose a standalone Gradle generator `designValidator`, and implement a validator planner with one bounded template id. Keep the migration intentionally narrow: only standard `validator` tags are accepted, output always lands in `application.validators`, and `valueType` remains fixed to `Long` for this slice.

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
- Create: `cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignValidatorRenderModels.kt`
- Create: `cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignValidatorArtifactPlanner.kt`
- Create: `cap4k-plugin-pipeline-generator-design/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignValidatorArtifactPlannerTest.kt`
- Modify: `cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePlugin.kt`
- Create: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/design/validator.kt.peb`
- Modify: `cap4k-plugin-pipeline-renderer-pebble/src/test/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PebbleArtifactRendererTest.kt`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-validator-sample/build.gradle.kts`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-validator-sample/design/design.json`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-validator-sample/codegen/templates/design/validator.kt.peb`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginFunctionalTest.kt`

### Task 1: Add Canonical And Gradle Support For Validator Family

**Files:**
- Modify: `cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModels.kt`
- Modify: `cap4k-plugin-pipeline-api/src/test/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModelsTest.kt`
- Modify: `cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssembler.kt`
- Modify: `cap4k-plugin-pipeline-core/src/test/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssemblerTest.kt`
- Modify: `cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kExtension.kt`
- Modify: `cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kProjectConfigFactory.kt`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kProjectConfigFactoryTest.kt`

- [ ] **Step 1: Add failing API and canonical tests for validator slice**

Add tests that prove:

- `CanonicalModel` keeps a dedicated `validators` slice alongside `requests`
- `validator` entries assemble into `validators`
- old aliases `validators`, `validater`, and `validate` are skipped in this slice
- validator naming does not append a suffix and still normalizes `issueToken` into `IssueToken`
- validator `valueType` is fixed to `Long`
- request assembly remains unchanged when validator entries are present

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-api:test :cap4k-plugin-pipeline-core:test --tests "*validator*" --rerun-tasks
```

Expected: FAIL because `ValidatorModel` and `CanonicalModel.validators` do not exist yet.

- [ ] **Step 2: Implement minimal canonical validator support**

Extend the API model with:

```kotlin
data class ValidatorModel(
    val packageName: String,
    val typeName: String,
    val description: String,
    val valueType: String,
)
```

and extend `CanonicalModel` with:

```kotlin
val validators: List<ValidatorModel> = emptyList()
```

Update `DefaultCanonicalAssembler` so:

- only `entry.tag.lowercase(Locale.ROOT) == "validator"` is accepted for validator assembly
- `typeName` is normalized to old-compatible UpperCamel form without adding a suffix
- `valueType = "Long"`
- validator entries are placed in `model.validators`
- non-validator entries keep flowing through the existing request assembly unchanged

- [ ] **Step 3: Add failing config tests for `designValidator`**

Add tests that prove:

- `designValidator.enabled` defaults to `false`
- enabling it wires generator id `design-validator`
- `designValidator` requires `project.applicationModulePath`
- `designValidator` requires enabled `designJson`

Use these exact failure messages:

```kotlin
"project.applicationModulePath is required when designValidator is enabled."
"designValidator generator requires enabled designJson source."
```

- [ ] **Step 4: Implement Gradle DSL and config wiring**

Add generator extension and DSL block:

```kotlin
val designValidator: DesignValidatorGeneratorExtension
fun designValidator(block: DesignValidatorGeneratorExtension.() -> Unit)
```

Wire generator id in `Cap4kProjectConfigFactory`:

```kotlin
"design-validator"
```

and add only these dependency rules:

- `designValidator` requires enabled `designJson`
- `designValidator` requires `project.applicationModulePath`

- [ ] **Step 5: Run focused API, core, and config tests**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-api:test :cap4k-plugin-pipeline-core:test :cap4k-plugin-pipeline-gradle:test --tests "*validator*" --rerun-tasks
```

Expected: PASS for the new validator model, canonical assembly, and config tests.

- [ ] **Step 6: Commit the canonical/DSL slice**

```bash
git add cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModels.kt cap4k-plugin-pipeline-api/src/test/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModelsTest.kt cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssembler.kt cap4k-plugin-pipeline-core/src/test/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssemblerTest.kt cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kExtension.kt cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kProjectConfigFactory.kt cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kProjectConfigFactoryTest.kt
git commit -m "feat: add design validator family config"
```

### Task 2: Add Validator Planner And Provider Registration

**Files:**
- Create: `cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignValidatorRenderModels.kt`
- Create: `cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignValidatorArtifactPlanner.kt`
- Create: `cap4k-plugin-pipeline-generator-design/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignValidatorArtifactPlannerTest.kt`
- Modify: `cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePlugin.kt`

- [ ] **Step 1: Add failing planner tests**

Add tests that assert:

- `generatorId == "design-validator"`
- `templateId == "design/validator.kt.peb"`
- output path ends with:
  - `application/validators/authorize/IssueToken.kt`
- context includes:
  - `packageName = "com.acme.demo.application.validators.authorize"`
  - `typeName = "IssueToken"`
  - `description = "issue token validator"`
  - `valueType = "Long"`
  - `imports = emptyList<String>()`

Add planner boundary tests that also prove:

- non-validator canonical slices are ignored
- missing application module fails clearly

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-generator-design:test --tests "*validator artifacts into application validators path*" --tests "*fails when application module is missing for validator planner*" --rerun-tasks
```

Expected: FAIL because the planner and render-model files do not exist yet.

- [ ] **Step 2: Implement the validator planner and render model**

Create a dedicated render model:

```kotlin
internal data class DesignValidatorRenderModel(
    val packageName: String,
    val typeName: String,
    val description: String,
    val valueType: String,
    val imports: List<String>,
)
```

Implement `DesignValidatorArtifactPlanner` so it:

- reads `model.validators`
- emits `design/validator.kt.peb`
- writes to:
  - `<applicationRoot>/src/main/kotlin/<base>/application/validators/<package>/<TypeName>.kt`
- sets package name to:
  - `<basePackage>.application.validators.<package>`
- fixes `valueType` to `Long`

- [ ] **Step 3: Register provider in `PipelinePlugin`**

Add `DesignValidatorArtifactPlanner()` to `buildRunner(...)` beside the existing design-family planners so `design-validator` no longer trips the runner fail-fast invariant.

- [ ] **Step 4: Re-run focused planner tests and compile verification**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-generator-design:test --tests "*validator artifacts into application validators path*" --tests "*fails when application module is missing for validator planner*" --rerun-tasks
./gradlew :cap4k-plugin-pipeline-gradle:compileKotlin --rerun-tasks
```

Expected: PASS.

- [ ] **Step 5: Commit the planner slice**

```bash
git add cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignValidatorRenderModels.kt cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignValidatorArtifactPlanner.kt cap4k-plugin-pipeline-generator-design/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignValidatorArtifactPlannerTest.kt cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePlugin.kt
git commit -m "feat: add design validator planner"
```

### Task 3: Add Helper-First Validator Template And Functional Coverage

**Files:**
- Create: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/design/validator.kt.peb`
- Modify: `cap4k-plugin-pipeline-renderer-pebble/src/test/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PebbleArtifactRendererTest.kt`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-validator-sample/build.gradle.kts`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-validator-sample/design/design.json`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-validator-sample/codegen/templates/design/validator.kt.peb`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginFunctionalTest.kt`

- [ ] **Step 1: Add failing renderer tests for the validator contract**

Add renderer tests that prove:

- `design/validator.kt.peb` renders `@Constraint`
- the generated annotation includes `message`, `groups`, and `payload`
- the nested validator implements `ConstraintValidator<IssueToken, Long>`
- helper-driven imports render through `imports(imports)`
- override template resolution works for `design/validator.kt.peb`

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-renderer-pebble:test --tests "*validator*" --rerun-tasks
```

Expected: FAIL because the preset template does not exist yet.

- [ ] **Step 2: Implement the bounded validator preset**

Create `design/validator.kt.peb` with the helper-first contract:

```pebble
{{ use("jakarta.validation.Constraint") -}}
{{ use("jakarta.validation.ConstraintValidator") -}}
{{ use("jakarta.validation.ConstraintValidatorContext") -}}
{{ use("jakarta.validation.Payload") -}}
{{ use("kotlin.reflect.KClass") -}}
package {{ packageName }}
{% for import in imports(imports) %}
import {{ import }}
{% endfor %}
```

The generated artifact must retain:

- `annotation class {{ typeName }}`
- `message`, `groups`, `payload`
- nested `class Validator : ConstraintValidator<{{ typeName }}, {{ valueType }}>`
- `isValid(...): Boolean = true`

- [ ] **Step 3: Add failing functional tests against an isolated validator fixture**

Add functional tests that prove:

- `cap4kPlan` emits `design/validator.kt.peb`
- `cap4kGenerate` writes a validator file under `application/validators`
- override template replacement works for `design/validator.kt.peb`
- invalid config fails when:
  - `designValidator` lacks `applicationModulePath`
  - `designValidator` is enabled without enabled `designJson`

- [ ] **Step 4: Create the isolated validator fixture**

Create `design-validator-sample/build.gradle.kts` with:

```kotlin
plugins {
    id("com.only4.cap4k.plugin.pipeline")
}

cap4k {
    project {
        basePackage.set("com.acme.demo")
        applicationModulePath.set("demo-application")
    }
    sources {
        designJson {
            enabled.set(true)
            files.from("design/design.json")
        }
    }
    generators {
        designValidator {
            enabled.set(true)
        }
    }
}
```

Create `design-validator-sample/design/design.json` with one representative validator entry:

```json
[
  {
    "tag": "validator",
    "package": "authorize",
    "name": "IssueToken",
    "desc": "issue token validator",
    "aggregates": [],
    "requestFields": [],
    "responseFields": []
  }
]
```

Create override template `codegen/templates/design/validator.kt.peb` containing the exact marker:

```pebble
// override: representative validator migration template
```

- [ ] **Step 5: Run renderer and functional verification**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-renderer-pebble:test --tests "*validator*" --rerun-tasks
./gradlew :cap4k-plugin-pipeline-gradle:test --tests "*validator*" --rerun-tasks
```

Expected: PASS with the validator preset resolved from `ddd-default`, the isolated validator fixture generating files under `application/validators`, override replacement taking effect, and invalid config failures surfacing the expected messages.

- [ ] **Step 6: Commit the template/functional slice**

```bash
git add cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/design/validator.kt.peb cap4k-plugin-pipeline-renderer-pebble/src/test/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PebbleArtifactRendererTest.kt cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-validator-sample/build.gradle.kts cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-validator-sample/design/design.json cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-validator-sample/codegen/templates/design/validator.kt.peb cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginFunctionalTest.kt
git commit -m "test: cover design validator migration flow"
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
  - canonical validator family support
  - validator DSL/config
  - validator planner/provider registration
  - validator preset template
  - validator renderer and functional migration coverage

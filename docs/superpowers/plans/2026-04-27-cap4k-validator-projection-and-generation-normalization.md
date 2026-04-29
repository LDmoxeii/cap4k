# Cap4k Validator Projection And Generation Normalization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

> **Execution status:** Implemented. This document is a historical execution plan only; unchecked checklist items below are not current TODOs. Use `docs/superpowers/mainline-roadmap.md` for current status and next work.

**Goal:** Make ordinary validator design input, generated validator skeletons, analysis design projection, and drawing-board validator JSON use one stable contract.

**Architecture:** Implement one shared validator contract through the existing pipeline stages: `designJson -> CanonicalModel.validators -> designValidator -> generated Kotlin`, then make `analysis-compiler -> design-elements.json -> source-ir-analysis -> drawing-board` emit the same contract. Keep `nodes.json`, `rels.json`, flow traversal, and analysis task separation unchanged.

**Tech Stack:** Kotlin, Gradle, JUnit 5, Gradle TestKit, Pebble templates, Gson, Kotlin compiler IR plugin.

## Freshness Review Against 2026-04-28 `master`

Current `master` already includes some nearby changes that this plan must preserve:

- `DesignSpecEntry` now carries `traits: Set<RequestTrait> = emptySet()` for query/api-payload contracts. Any validator model expansion must keep that field and must not regress page-trait parsing or canonical assembly.
- `DesignJsonSourceProvider` already accepts canonical `validator` tags and rejects legacy design tag aliases. This plan should add validator structural fields to that path, not re-open alias parsing.
- `DefaultCanonicalAssembler` already recognizes canonical `validator` as a drawing-board tag, but does not preserve validator structural fields yet. The drawing-board work should extend the existing canonical-only path instead of restoring old analysis tag normalization in the assembler.
- `DefaultCanonicalAssemblerTest` already has a drawing-board validator smoke assertion. Update it to assert `message`, `targets`, `valueType`, and `parameters` rather than adding a duplicate validator-only case.
- Pre-existing working-tree edits in `AGENTS.md` and `docs/superpowers/mainline-roadmap.md` are handoff documentation changes. Do not include `AGENTS.md` in validator implementation commits. Only update `mainline-roadmap.md` at the final documentation step and preserve the existing handoff edits.

Plan deltas:

- The specs remain current. No spec edit is required before implementation.
- The implementation plan is refreshed by this section and by preserving `traits` in the Task 1 `DesignSpecEntry` snippet below.
- Execute the plan task-by-task with focused red/green tests, but treat already-implemented partial support as context to extend rather than undo.
- Keep commits scoped by task when practical; if local verification cadence makes fewer commits more practical, still keep the diff grouped by the plan tasks and never commit unrelated pre-existing changes.

---

## Source Specs And Constraints

This plan combines three documents:

- `docs/superpowers/specs/2026-04-27-cap4k-analysis-design-projection-normalization-design.md`
- `docs/superpowers/specs/2026-04-27-cap4k-validator-generation-capability-expansion-design.md`
- `docs/design/ir-analysis/current-state-analysis.md`

Non-negotiable constraints:

- Do not rewrite `nodes.json`.
- Do not rewrite `rels.json`.
- Do not restructure `FlowArtifactPlanner`.
- Do not move `flow` or `drawing-board` back under `cap4kGenerate`.
- Do not generate validator business logic.
- Do not auto-attach validators to request classes or request fields.
- Do not support `ConstraintValidator<Annotation, ConcreteRequest.Request>` as ordinary validator input.
- Do not merge aggregate unique validators into ordinary `designValidator`.

Important implementation narrowing:

- The API model may carry `nullable` on validator parameters for consistency with other small design records.
- The first ordinary validator generator must reject `nullable = true` for custom annotation parameters because Kotlin annotation constructor parameters do not support nullable value types as a stable contract.

## File Map

Shared API and models:

- Modify `cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModels.kt`
- Modify `cap4k-plugin-code-analysis-core/src/main/kotlin/com/only4/cap4k/plugin/codeanalysis/core/model/DesignElement.kt`

Design JSON source and canonical assembly:

- Modify `cap4k-plugin-pipeline-source-design-json/src/main/kotlin/com/only4/cap4k/plugin/pipeline/source/designjson/DesignJsonSourceProvider.kt`
- Modify `cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssembler.kt`

Validator generation:

- Modify `cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignValidatorRenderModels.kt`
- Modify `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/design/validator.kt.peb`

Analysis projection:

- Modify `cap4k-plugin-code-analysis-compiler/src/main/kotlin/com/only4/cap4k/plugin/codeanalysis/compiler/DesignElementCollector.kt`
- Modify `cap4k-plugin-code-analysis-compiler/src/main/kotlin/com/only4/cap4k/plugin/codeanalysis/compiler/DesignElementJsonWriter.kt`
- Modify `cap4k-plugin-code-analysis-compiler/src/main/kotlin/com/only4/cap4k/plugin/codeanalysis/compiler/Cap4kOptions.kt`
- Modify `cap4k-plugin-code-analysis-core/src/main/kotlin/com/only4/cap4k/plugin/codeanalysis/core/config/OptionsKeys.kt`
- Modify `cap4k-plugin-pipeline-source-ir-analysis/src/main/kotlin/com/only4/cap4k/plugin/pipeline/source/ir/IrAnalysisSourceProvider.kt`

Drawing-board:

- Modify `cap4k-plugin-pipeline-generator-drawing-board/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/drawingboard/DrawingBoardArtifactPlanner.kt`
- Modify `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/drawing-board/document.json.peb`

Tests:

- Modify `cap4k-plugin-pipeline-source-design-json/src/test/kotlin/com/only4/cap4k/plugin/pipeline/source/designjson/DesignJsonSourceProviderTest.kt`
- Modify or create tests in `cap4k-plugin-pipeline-core/src/test/kotlin/com/only4/cap4k/plugin/pipeline/core/`
- Modify `cap4k-plugin-pipeline-generator-design/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignValidatorArtifactPlannerTest.kt`
- Modify `cap4k-plugin-code-analysis-compiler/src/test/kotlin/com/only4/cap4k/plugin/codeanalysis/compiler/DesignElementExtractionTest.kt`
- Modify `cap4k-plugin-code-analysis-compiler/src/test/kotlin/com/only4/cap4k/plugin/codeanalysis/compiler/DesignElementJsonWriterTest.kt`
- Modify `cap4k-plugin-pipeline-source-ir-analysis/src/test/kotlin/com/only4/cap4k/plugin/pipeline/source/ir/IrAnalysisSourceProviderTest.kt`
- Modify `cap4k-plugin-pipeline-generator-drawing-board/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/drawingboard/DrawingBoardArtifactPlannerTest.kt`
- Modify `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginFunctionalTest.kt`
- Create fixture `cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-validator-expanded-sample/`
- Create fixture `cap4k-plugin-pipeline-gradle/src/test/resources/functional/analysis-validator-roundtrip-sample/`

Docs:

- Modify `cap4k-plugin-pipeline-gradle/README.md` after behavior is verified.
- Modify `docs/superpowers/mainline-roadmap.md` only after implementation is complete.

---

### Task 1: Shared Validator Contract Models

**Files:**

- Modify: `cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModels.kt`
- Modify: `cap4k-plugin-code-analysis-core/src/main/kotlin/com/only4/cap4k/plugin/codeanalysis/core/model/DesignElement.kt`
- Test: compile command in Step 5

- [ ] **Step 1: Add shared validator parameter model in pipeline API**

In `PipelineModels.kt`, add this model near `DesignFieldSnapshot` and before `DesignElementSnapshot`:

```kotlin
data class ValidatorParameterModel(
    val name: String,
    val type: String,
    val nullable: Boolean = false,
    val defaultValue: String? = null,
)
```

- [ ] **Step 2: Expand design source entry and analysis projection snapshot**

In `PipelineModels.kt`, update `DesignSpecEntry` to carry validator-only structural fields:

```kotlin
data class DesignSpecEntry(
    val tag: String,
    val packageName: String,
    val name: String,
    val description: String,
    val aggregates: List<String>,
    val persist: Boolean? = null,
    val traits: Set<RequestTrait> = emptySet(),
    val requestFields: List<FieldModel>,
    val responseFields: List<FieldModel>,
    val message: String? = null,
    val targets: List<String> = emptyList(),
    val valueType: String? = null,
    val parameters: List<ValidatorParameterModel> = emptyList(),
)
```

In the same file, update `DesignElementSnapshot`:

```kotlin
data class DesignElementSnapshot(
    val tag: String,
    val packageName: String,
    val name: String,
    val description: String,
    val aggregates: List<String> = emptyList(),
    val entity: String? = null,
    val persist: Boolean? = null,
    val requestFields: List<DesignFieldSnapshot> = emptyList(),
    val responseFields: List<DesignFieldSnapshot> = emptyList(),
    val message: String? = null,
    val targets: List<String> = emptyList(),
    val valueType: String? = null,
    val parameters: List<ValidatorParameterModel> = emptyList(),
)
```

- [ ] **Step 3: Expand canonical validator and drawing-board models**

In `PipelineModels.kt`, replace `ValidatorModel` with:

```kotlin
data class ValidatorModel(
    val packageName: String,
    val typeName: String,
    val description: String,
    val message: String,
    val targets: List<String>,
    val valueType: String,
    val parameters: List<ValidatorParameterModel> = emptyList(),
)
```

Update `DrawingBoardElementModel`:

```kotlin
data class DrawingBoardElementModel(
    val tag: String,
    val packageName: String,
    val name: String,
    val description: String,
    val aggregates: List<String> = emptyList(),
    val entity: String? = null,
    val persist: Boolean? = null,
    val requestFields: List<DrawingBoardFieldModel> = emptyList(),
    val responseFields: List<DrawingBoardFieldModel> = emptyList(),
    val message: String? = null,
    val targets: List<String> = emptyList(),
    val valueType: String? = null,
    val parameters: List<ValidatorParameterModel> = emptyList(),
) {
    val designJsonRequestFields: List<DrawingBoardFieldModel>
        get() = if (tag == "domain_event") {
            requestFields.filterNot { it.name.equals("entity", ignoreCase = true) }
        } else {
            requestFields
        }
}
```

- [ ] **Step 4: Expand analysis-core design projection model**

In `cap4k-plugin-code-analysis-core/src/main/kotlin/com/only4/cap4k/plugin/codeanalysis/core/model/DesignElement.kt`, replace the file with:

```kotlin
package com.only4.cap4k.plugin.codeanalysis.core.model

data class DesignElement(
    val tag: String,
    val `package`: String,
    val name: String,
    val desc: String,
    val aggregates: List<String> = emptyList(),
    val entity: String? = null,
    val persist: Boolean? = null,
    val requestFields: List<DesignField> = emptyList(),
    val responseFields: List<DesignField> = emptyList(),
    val message: String? = null,
    val targets: List<String> = emptyList(),
    val valueType: String? = null,
    val parameters: List<DesignParameter> = emptyList(),
)

data class DesignField(
    val name: String,
    val type: String,
    val nullable: Boolean = false,
    val defaultValue: String? = null,
)

data class DesignParameter(
    val name: String,
    val type: String,
    val nullable: Boolean = false,
    val defaultValue: String? = null,
)
```

- [ ] **Step 5: Run compile to expose call-site breakage**

Run:

```powershell
.\gradlew.bat :cap4k-plugin-pipeline-api:compileKotlin :cap4k-plugin-code-analysis-core:compileKotlin
```

Expected: PASS. If this fails, fix only constructor arguments required by the new non-default `ValidatorModel.message`, `ValidatorModel.targets`, or model import changes.

- [ ] **Step 6: Commit shared model changes**

Run:

```powershell
git add cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModels.kt `
        cap4k-plugin-code-analysis-core/src/main/kotlin/com/only4/cap4k/plugin/codeanalysis/core/model/DesignElement.kt
git commit -m "feat: expand validator contract models"
```

---

### Task 2: Design JSON Validator Parsing And Validation

**Files:**

- Modify: `cap4k-plugin-pipeline-source-design-json/src/main/kotlin/com/only4/cap4k/plugin/pipeline/source/designjson/DesignJsonSourceProvider.kt`
- Modify: `cap4k-plugin-pipeline-source-design-json/src/test/kotlin/com/only4/cap4k/plugin/pipeline/source/designjson/DesignJsonSourceProviderTest.kt`

- [ ] **Step 1: Add failing parser test for expanded validator fields**

Append this test to `DesignJsonSourceProviderTest`:

```kotlin
@Test
fun `reads expanded validator structural fields`() {
    val tempFile = tempDir.resolve("validator-expanded.json")
    val content = """
        [
          {
            "tag": "validator",
            "package": "danmuku",
            "name": "DanmukuDeletePermission",
            "desc": "delete permission",
            "message": "no permission",
            "targets": ["CLASS"],
            "valueType": "Any",
            "parameters": [
              { "name": "danmukuIdField", "type": "String", "defaultValue": "danmukuId" },
              { "name": "operatorIdField", "type": "String", "defaultValue": "operatorId" }
            ]
          }
        ]
    """.trimIndent()
    Files.writeString(tempFile, content, StandardCharsets.UTF_8)

    val snapshot = DesignJsonSourceProvider().collect(configFor(tempFile.toString())) as DesignSpecSnapshot
    val entry = snapshot.entries.single()

    assertEquals("validator", entry.tag)
    assertEquals("danmuku", entry.packageName)
    assertEquals("DanmukuDeletePermission", entry.name)
    assertEquals("delete permission", entry.description)
    assertEquals("no permission", entry.message)
    assertEquals(listOf("CLASS"), entry.targets)
    assertEquals("Any", entry.valueType)
    assertEquals(2, entry.parameters.size)
    assertEquals("danmukuIdField", entry.parameters.first().name)
    assertEquals("String", entry.parameters.first().type)
    assertEquals(false, entry.parameters.first().nullable)
    assertEquals("danmukuId", entry.parameters.first().defaultValue)
}
```

Add this private helper near the bottom of the same test class:

```kotlin
private fun configFor(vararg files: String): ProjectConfig =
    ProjectConfig(
        basePackage = "com.only4.cap4k",
        layout = ProjectLayout.SINGLE_MODULE,
        modules = emptyMap(),
        sources = mapOf(
            "design-json" to SourceConfig(
                enabled = true,
                options = mapOf("files" to files.toList()),
            ),
        ),
        generators = emptyMap(),
        templates = TemplateConfig(
            preset = "default",
            overrideDirs = emptyList(),
            conflictPolicy = ConflictPolicy.SKIP,
        ),
    )
```

- [ ] **Step 2: Add failing parser tests for defaults and validation**

Append these tests to `DesignJsonSourceProviderTest`:

```kotlin
@Test
fun `defaults validator message targets and value type`() {
    val tempFile = tempDir.resolve("validator-defaults.json")
    Files.writeString(
        tempFile,
        """
            [
              {
                "tag": "validator",
                "package": "category",
                "name": "CategoryMustExist",
                "desc": "category must exist"
              },
              {
                "tag": "validator",
                "package": "danmuku",
                "name": "DanmukuDeletePermission",
                "desc": "delete permission",
                "targets": ["CLASS"]
              }
            ]
        """.trimIndent(),
        StandardCharsets.UTF_8,
    )

    val entries = (DesignJsonSourceProvider().collect(configFor(tempFile.toString())) as DesignSpecSnapshot).entries

    assertEquals("校验未通过", entries.first().message)
    assertEquals(listOf("FIELD", "VALUE_PARAMETER"), entries.first().targets)
    assertEquals("Long", entries.first().valueType)
    assertEquals("Any", entries.last().valueType)
}

@Test
fun `rejects invalid validator target`() {
    val tempFile = tempDir.resolve("validator-invalid-target.json")
    Files.writeString(
        tempFile,
        """
            [
              {
                "tag": "validator",
                "package": "category",
                "name": "CategoryMustExist",
                "desc": "category must exist",
                "targets": ["METHOD"]
              }
            ]
        """.trimIndent(),
        StandardCharsets.UTF_8,
    )

    val error = assertThrows(IllegalArgumentException::class.java) {
        DesignJsonSourceProvider().collect(configFor(tempFile.toString()))
    }

    assertEquals("validator CategoryMustExist has unsupported target: METHOD", error.message)
}

@Test
fun `rejects invalid validator value type`() {
    val tempFile = tempDir.resolve("validator-invalid-type.json")
    Files.writeString(
        tempFile,
        """
            [
              {
                "tag": "validator",
                "package": "video",
                "name": "VideoDeletePermission",
                "desc": "video delete permission",
                "targets": ["CLASS"],
                "valueType": "DeleteVideoPostCmd.Request"
              }
            ]
        """.trimIndent(),
        StandardCharsets.UTF_8,
    )

    val error = assertThrows(IllegalArgumentException::class.java) {
        DesignJsonSourceProvider().collect(configFor(tempFile.toString()))
    }

    assertEquals(
        "validator VideoDeletePermission has unsupported valueType: DeleteVideoPostCmd.Request",
        error.message,
    )
}

@Test
fun `rejects reserved or nullable validator parameters`() {
    val tempFile = tempDir.resolve("validator-invalid-parameters.json")
    Files.writeString(
        tempFile,
        """
            [
              {
                "tag": "validator",
                "package": "danmuku",
                "name": "DanmukuDeletePermission",
                "desc": "delete permission",
                "targets": ["CLASS"],
                "parameters": [
                  { "name": "message", "type": "String" }
                ]
              }
            ]
        """.trimIndent(),
        StandardCharsets.UTF_8,
    )

    val error = assertThrows(IllegalArgumentException::class.java) {
        DesignJsonSourceProvider().collect(configFor(tempFile.toString()))
    }

    assertEquals("validator DanmukuDeletePermission parameter name is reserved: message", error.message)
}
```

- [ ] **Step 3: Run source design-json tests and verify they fail**

Run:

```powershell
.\gradlew.bat :cap4k-plugin-pipeline-source-design-json:test --tests "com.only4.cap4k.plugin.pipeline.source.designjson.DesignJsonSourceProviderTest"
```

Expected: FAIL because `DesignSpecEntry` is not populated with validator `message`, `targets`, `valueType`, or `parameters` yet.

- [ ] **Step 4: Implement validator parsing helpers**

In `DesignJsonSourceProvider.kt`, import these types:

```kotlin
import com.google.gson.JsonObject
import com.only4.cap4k.plugin.pipeline.api.ValidatorParameterModel
```

Add these constants inside `DesignJsonSourceProvider`:

```kotlin
private val supportedValidatorTargets = setOf("CLASS", "FIELD", "VALUE_PARAMETER")
private val supportedValidatorValueTypes = setOf("Any", "String", "Long", "Int", "Boolean")
private val supportedValidatorParameterTypes = setOf("String", "Int", "Long", "Boolean")
private val reservedValidatorParameterNames = setOf("message", "groups", "payload")
```

In `parseFile`, replace the current `DesignSpecEntry(...)` construction with this sequence:

```kotlin
val validator = tag.lowercase(Locale.ROOT) == "validator"
val targets = if (validator) parseValidatorTargets(obj, name) else emptyList()
val valueType = if (validator) parseValidatorValueType(obj, name, targets) else null
val parameters = if (validator) parseValidatorParameters(obj, name) else emptyList()
DesignSpecEntry(
    tag = tag,
    packageName = readPackageName(obj["package"]?.asString, tag),
    name = name,
    description = obj["desc"]?.asString ?: "",
    aggregates = obj["aggregates"]?.asJsonArray?.map { it.asString } ?: emptyList(),
    persist = obj["persist"]?.asBoolean,
    requestFields = requestFields,
    responseFields = parseFields(obj["responseFields"]?.asJsonArray),
    message = if (validator) obj["message"]?.asString ?: "校验未通过" else null,
    targets = targets,
    valueType = valueType,
    parameters = parameters,
)
```

Add these helper methods in the same class:

```kotlin
private fun parseValidatorTargets(obj: JsonObject, validatorName: String): List<String> {
    val targets = obj["targets"]
        ?.asJsonArray
        ?.map { it.asString.trim().uppercase(Locale.ROOT) }
        ?.filter { it.isNotEmpty() }
        ?: listOf("FIELD", "VALUE_PARAMETER")
    require(targets.isNotEmpty()) {
        "validator $validatorName must declare at least one target"
    }
    targets.forEach { target ->
        require(target in supportedValidatorTargets) {
            "validator $validatorName has unsupported target: $target"
        }
    }
    return targets.distinct()
}

private fun parseValidatorValueType(obj: JsonObject, validatorName: String, targets: List<String>): String {
    val explicit = obj["valueType"]?.asString?.trim()
    val valueType = explicit?.takeIf { it.isNotEmpty() }
        ?: if (targets == listOf("CLASS")) "Any" else "Long"
    require(valueType in supportedValidatorValueTypes) {
        "validator $validatorName has unsupported valueType: $valueType"
    }
    return valueType
}

private fun parseValidatorParameters(obj: JsonObject, validatorName: String): List<ValidatorParameterModel> {
    val array = obj["parameters"]?.asJsonArray ?: return emptyList()
    val names = linkedSetOf<String>()
    return array.map { element ->
        val parameter = element.asJsonObject
        val name = parameter["name"]?.asString?.trim().orEmpty()
        require(name.isNotEmpty()) {
            "validator $validatorName parameter name must not be blank"
        }
        require(name !in reservedValidatorParameterNames) {
            "validator $validatorName parameter name is reserved: $name"
        }
        require(names.add(name)) {
            "validator $validatorName has duplicate parameter: $name"
        }
        val type = parameter["type"]?.asString?.trim()?.ifEmpty { null } ?: "String"
        require(type in supportedValidatorParameterTypes) {
            "validator $validatorName parameter $name has unsupported type: $type"
        }
        val nullable = parameter["nullable"]?.asBoolean ?: false
        require(!nullable) {
            "validator $validatorName parameter $name must not be nullable"
        }
        ValidatorParameterModel(
            name = name,
            type = type,
            nullable = false,
            defaultValue = parameter["defaultValue"]?.asString,
        )
    }
}
```

- [ ] **Step 5: Run source design-json tests**

Run:

```powershell
.\gradlew.bat :cap4k-plugin-pipeline-source-design-json:test --tests "com.only4.cap4k.plugin.pipeline.source.designjson.DesignJsonSourceProviderTest"
```

Expected: PASS.

- [ ] **Step 6: Commit design-json parsing**

Run:

```powershell
git add cap4k-plugin-pipeline-source-design-json/src/main/kotlin/com/only4/cap4k/plugin/pipeline/source/designjson/DesignJsonSourceProvider.kt `
        cap4k-plugin-pipeline-source-design-json/src/test/kotlin/com/only4/cap4k/plugin/pipeline/source/designjson/DesignJsonSourceProviderTest.kt
git commit -m "feat: parse expanded validator design input"
```

---

### Task 3: Canonical Validator Assembly And Template Rendering

**Files:**

- Modify: `cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssembler.kt`
- Modify: `cap4k-plugin-pipeline-core/src/test/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssemblerTest.kt`
- Modify: `cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignValidatorRenderModels.kt`
- Modify: `cap4k-plugin-pipeline-generator-design/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignValidatorArtifactPlannerTest.kt`
- Modify: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/design/validator.kt.peb`

- [ ] **Step 1: Add failing canonical test for expanded validator model**

In `DefaultCanonicalAssemblerTest.kt`, add this import:

```kotlin
import com.only4.cap4k.plugin.pipeline.api.ValidatorParameterModel
```

Replace the existing test named `validator entries assemble into validators slice with normalized naming and fixed value type` with:

```kotlin
@Test
fun `validator entries assemble into validators slice with expanded contract`() {
    val assembler = DefaultCanonicalAssembler()

    val model = assembler.assemble(
        config = baseConfig(),
        snapshots = listOf(
            DesignSpecSnapshot(
                entries = listOf(
                    DesignSpecEntry(
                        tag = "validator",
                        packageName = "auth.validator",
                        name = "issue_token",
                        description = "issue token validator",
                        aggregates = emptyList(),
                        requestFields = emptyList(),
                        responseFields = emptyList(),
                        message = "issue token denied",
                        targets = listOf("VALUE_PARAMETER", "FIELD"),
                        valueType = "String",
                        parameters = listOf(
                            ValidatorParameterModel(
                                name = "scopeField",
                                type = "String",
                                defaultValue = "scope",
                            ),
                        ),
                    ),
                    DesignSpecEntry(
                        tag = "validator",
                        packageName = "auth.validator",
                        name = "deletePermission",
                        description = "delete permission",
                        aggregates = emptyList(),
                        requestFields = emptyList(),
                        responseFields = emptyList(),
                        message = "delete denied",
                        targets = listOf("CLASS"),
                        valueType = "Any",
                    ),
                    DesignSpecEntry(
                        tag = "validators",
                        packageName = "auth.validator",
                        name = "pluralAlias",
                        description = "legacy alias",
                        aggregates = emptyList(),
                        requestFields = emptyList(),
                        responseFields = emptyList(),
                    ),
                )
            ),
        ),
    ).model

    assertEquals(2, model.validators.size)
    assertEquals(listOf("IssueToken", "DeletePermission"), model.validators.map { it.typeName })
    assertEquals(listOf("issue token denied", "delete denied"), model.validators.map { it.message })
    assertEquals(listOf(listOf("FIELD", "VALUE_PARAMETER"), listOf("CLASS")), model.validators.map { it.targets })
    assertEquals(listOf("String", "Any"), model.validators.map { it.valueType })
    assertEquals(listOf("scopeField"), model.validators.first().parameters.map { it.name })
}
```

- [ ] **Step 2: Run canonical test and verify it fails**

Run:

```powershell
.\gradlew.bat :cap4k-plugin-pipeline-core:test --tests "com.only4.cap4k.plugin.pipeline.core.DefaultCanonicalAssemblerTest"
```

Expected: FAIL because canonical assembly still constructs `ValidatorModel` with fixed `valueType = "Long"` and does not provide `message`, `targets`, or `parameters`.

- [ ] **Step 3: Implement canonical validator normalization**

In `DefaultCanonicalAssembler.kt`, replace the validator assembly block with:

```kotlin
val validators = designSnapshot?.entries.orEmpty()
    .asSequence()
    .filter { entry -> entry.tag.lowercase(Locale.ROOT) == "validator" }
    .map { entry ->
        val targets = normalizeValidatorTargets(entry.targets)
        ValidatorModel(
            packageName = entry.packageName,
            typeName = entry.name.normalizeValidatorTypeName(),
            description = entry.description,
            message = entry.message ?: "校验未通过",
            targets = targets,
            valueType = entry.valueType ?: defaultValidatorValueType(targets),
            parameters = entry.parameters,
        )
    }
    .toList()
```

Add these helpers near `normalizeValidatorTypeName()`:

```kotlin
private fun normalizeValidatorTargets(targets: List<String>): List<String> {
    val effectiveTargets = targets.ifEmpty { listOf("FIELD", "VALUE_PARAMETER") }
    return effectiveTargets
        .map { it.trim().uppercase(Locale.ROOT) }
        .filter { it.isNotEmpty() }
        .distinct()
        .sortedBy { target -> ValidatorTargetOrder[target] ?: Int.MAX_VALUE }
}

private fun defaultValidatorValueType(targets: List<String>): String {
    return if (targets == listOf("CLASS")) "Any" else "Long"
}
```

Add this constant in the companion object:

```kotlin
val ValidatorTargetOrder = mapOf(
    "CLASS" to 0,
    "FIELD" to 1,
    "VALUE_PARAMETER" to 2,
)
```

- [ ] **Step 4: Run canonical test and verify it passes**

Run:

```powershell
.\gradlew.bat :cap4k-plugin-pipeline-core:test --tests "com.only4.cap4k.plugin.pipeline.core.DefaultCanonicalAssemblerTest"
```

Expected: PASS.

- [ ] **Step 5: Add failing planner/render model test**

In `DesignValidatorArtifactPlannerTest.kt`, add this import:

```kotlin
import com.only4.cap4k.plugin.pipeline.api.ValidatorParameterModel
```

In `plans validator artifacts into application validators path`, replace the `ValidatorModel(...)` construction with:

```kotlin
ValidatorModel(
    packageName = "authorize",
    typeName = "IssueToken",
    description = "issue */ token validator",
    message = "issue token denied",
    targets = listOf("CLASS"),
    valueType = "Any",
    parameters = listOf(
        ValidatorParameterModel(
            name = "accountIdField",
            type = "String",
            defaultValue = "accountId",
        ),
    ),
)
```

Add these assertions after the existing `valueType` assertion:

```kotlin
assertEquals("issue token denied", validator.context["message"])
assertEquals("\"issue token denied\"", validator.context["messageLiteral"])
assertEquals(listOf("AnnotationTarget.CLASS"), validator.context["targetExpressions"])
val parameters = validator.context["parameters"] as List<*>
assertEquals(1, parameters.size)
assertTrue(parameters.single().toString().contains("accountIdField"))
```

Update the missing-module test `ValidatorModel(...)` to include:

```kotlin
message = "issue token denied",
targets = listOf("FIELD", "VALUE_PARAMETER"),
```

- [ ] **Step 6: Run planner test and verify it fails**

Run:

```powershell
.\gradlew.bat :cap4k-plugin-pipeline-generator-design:test --tests "com.only4.cap4k.plugin.pipeline.generator.design.DesignValidatorArtifactPlannerTest"
```

Expected: FAIL because `DesignValidatorRenderModel` does not expose `message`, `messageLiteral`, `targetExpressions`, or parameter render records.

- [ ] **Step 7: Expand validator render model**

In `DesignValidatorRenderModels.kt`, add:

```kotlin
import com.only4.cap4k.plugin.pipeline.api.ValidatorParameterModel
```

Replace `DesignValidatorRenderModel` with:

```kotlin
internal data class DesignValidatorRenderModel(
    val packageName: String,
    val typeName: String,
    val description: String,
    val descriptionCommentText: String,
    val message: String,
    val messageLiteral: String,
    val targetExpressions: List<String>,
    val valueType: String,
    val parameters: List<DesignValidatorParameterRenderModel>,
    val imports: List<String>,
) {
    fun toContextMap(): Map<String, Any?> = mapOf(
        "packageName" to packageName,
        "typeName" to typeName,
        "description" to description,
        "descriptionCommentText" to descriptionCommentText,
        "message" to message,
        "messageLiteral" to messageLiteral,
        "targetExpressions" to targetExpressions,
        "valueType" to valueType,
        "parameters" to parameters,
        "imports" to imports,
    )
}

internal data class DesignValidatorParameterRenderModel(
    val name: String,
    val type: String,
    val defaultLiteral: String?,
)
```

Replace `DesignValidatorRenderModelFactory.create(...)` body with:

```kotlin
return DesignValidatorRenderModel(
    packageName = packageName,
    typeName = validator.typeName,
    description = validator.description,
    descriptionCommentText = validator.description.toKDocCommentText(),
    message = validator.message,
    messageLiteral = kotlinStringLiteral(validator.message),
    targetExpressions = validator.targets.map { target -> "AnnotationTarget.$target" },
    valueType = validator.valueType,
    parameters = validator.parameters.map { it.toRenderModel() },
    imports = emptyList(),
)
```

Add these helper functions below the factory object:

```kotlin
private fun ValidatorParameterModel.toRenderModel(): DesignValidatorParameterRenderModel =
    DesignValidatorParameterRenderModel(
        name = name,
        type = type,
        defaultLiteral = defaultValue?.let { renderDefaultLiteral(type, it) },
    )

private fun renderDefaultLiteral(type: String, value: String): String =
    when (type) {
        "String" -> kotlinStringLiteral(value)
        "Long" -> if (value.endsWith("L")) value else "${value}L"
        "Int", "Boolean" -> value
        else -> error("unsupported validator parameter type for rendering: $type")
    }

private fun kotlinStringLiteral(value: String): String =
    "\"" + value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n") + "\""
```

- [ ] **Step 8: Update default Pebble validator template**

Replace `validator.kt.peb` with:

```peb
{{ use("jakarta.validation.Constraint") -}}
{{ use("jakarta.validation.ConstraintValidator") -}}
{{ use("jakarta.validation.ConstraintValidatorContext") -}}
{{ use("jakarta.validation.Payload") -}}
{{ use("kotlin.reflect.KClass") -}}
package {{ packageName }}
{% for import in imports(imports) %}
import {{ import }}
{% endfor %}

/**
 * {{ descriptionCommentText | raw }}
 */
@Target({% for target in targetExpressions %}{{ target }}{% if not loop.last %}, {% endif %}{% endfor %})
@Retention(AnnotationRetention.RUNTIME)
@Constraint(validatedBy = [{{ typeName }}.Validator::class])
@MustBeDocumented
annotation class {{ typeName }}(
    val message: String = {{ messageLiteral | raw }},
    val groups: Array<KClass<*>> = [],
    val payload: Array<KClass<out Payload>> = [],
{%- for parameter in parameters %}
    val {{ parameter.name }}: {{ parameter.type }}{% if parameter.defaultLiteral is not null %} = {{ parameter.defaultLiteral | raw }}{% endif %},
{%- endfor %}
) {
    class Validator : ConstraintValidator<{{ typeName }}, {{ valueType }}> {
        override fun isValid(value: {{ valueType }}?, context: ConstraintValidatorContext): Boolean = true
    }
}
```

- [ ] **Step 9: Run planner test again**

Run:

```powershell
.\gradlew.bat :cap4k-plugin-pipeline-generator-design:test --tests "com.only4.cap4k.plugin.pipeline.generator.design.DesignValidatorArtifactPlannerTest"
```

Expected: PASS.

- [ ] **Step 10: Commit canonical and render changes**

Run these shorter commands instead of one long `git add`:

```powershell
git add cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssembler.kt
git add cap4k-plugin-pipeline-core/src/test/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssemblerTest.kt
git add cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignValidatorRenderModels.kt
git add cap4k-plugin-pipeline-generator-design/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignValidatorArtifactPlannerTest.kt
git add cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/design/validator.kt.peb
git commit -m "feat: render expanded validator skeletons"
```

---

### Task 4: Functional Compile Coverage For Expanded Validators

**Files:**

- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-validator-expanded-sample/`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginFunctionalTest.kt`

- [ ] **Step 1: Create expanded validator compile fixture from the existing compile fixture**

Run this short PowerShell command:

```powershell
Copy-Item -Recurse cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-validator-compile-sample cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-validator-expanded-sample
```

Replace `cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-validator-expanded-sample/design/design.json` with:

```json
[
  {
    "tag": "validator",
    "package": "order",
    "name": "OrderIdValid",
    "desc": "order id validator",
    "message": "订单不存在",
    "targets": ["FIELD", "VALUE_PARAMETER"],
    "valueType": "Long",
    "parameters": []
  },
  {
    "tag": "validator",
    "package": "permission",
    "name": "DeletePermission",
    "desc": "delete permission validator",
    "message": "无删除权限",
    "targets": ["CLASS"],
    "valueType": "Any",
    "parameters": [
      {
        "name": "resourceIdField",
        "type": "String",
        "defaultValue": "resourceId"
      },
      {
        "name": "operatorIdField",
        "type": "String",
        "defaultValue": "operatorId"
      }
    ]
  }
]
```

- [ ] **Step 2: Add functional test for generated validator compilation**

Append this test near the existing validator functional tests in `PipelinePluginFunctionalTest.kt`:

```kotlin
@OptIn(ExperimentalPathApi::class)
@Test
fun `cap4kGenerate expanded validator flow compiles field and class validators`() {
    val projectDir = Files.createTempDirectory("pipeline-functional-design-validator-expanded")
    copyFixture(projectDir, "design-validator-expanded-sample")

    val generateResult = GradleRunner.create()
        .withProjectDir(projectDir.toFile())
        .withPluginClasspath()
        .withArguments("cap4kGenerate")
        .build()

    val compileResult = GradleRunner.create()
        .withProjectDir(projectDir.toFile())
        .withPluginClasspath()
        .withArguments(":demo-application:compileKotlin")
        .build()

    val fieldValidator = projectDir.resolve(
        "demo-application/src/main/kotlin/com/acme/demo/application/validators/order/OrderIdValid.kt"
    )
    val classValidator = projectDir.resolve(
        "demo-application/src/main/kotlin/com/acme/demo/application/validators/permission/DeletePermission.kt"
    )
    val classValidatorContent = classValidator.readText()

    assertTrue(generateResult.output.contains("BUILD SUCCESSFUL"))
    assertTrue(compileResult.output.contains("BUILD SUCCESSFUL"))
    assertTrue(fieldValidator.readText().contains("@Target(AnnotationTarget.FIELD, AnnotationTarget.VALUE_PARAMETER)"))
    assertTrue(fieldValidator.readText().contains("ConstraintValidator<OrderIdValid, Long>"))
    assertTrue(classValidatorContent.contains("@Target(AnnotationTarget.CLASS)"))
    assertTrue(classValidatorContent.contains("ConstraintValidator<DeletePermission, Any>"))
    assertTrue(classValidatorContent.contains("val resourceIdField: String = \"resourceId\""))
    assertTrue(classValidatorContent.contains("val operatorIdField: String = \"operatorId\""))
    assertTrue(classValidatorContent.contains("override fun isValid(value: Any?, context: ConstraintValidatorContext): Boolean = true"))
}
```

- [ ] **Step 3: Run only the new functional test**

Run with a long execution timeout in the shell tool, because Gradle functional tests may legitimately take minutes:

```powershell
.\gradlew.bat :cap4k-plugin-pipeline-gradle:test --tests "com.only4.cap4k.plugin.pipeline.gradle.PipelinePluginFunctionalTest.cap4kGenerate expanded validator flow compiles field and class validators"
```

Expected: PASS. If the process is run through an agent tool, set the tool timeout to at least `1500000` milliseconds.

- [ ] **Step 4: Commit expanded validator functional coverage**

Run:

```powershell
git add cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-validator-expanded-sample
git add cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginFunctionalTest.kt
git commit -m "test: cover expanded validator generation"
```

---

### Task 5: Analysis Design Projection Tag Normalization And JSON Writer

**Files:**

- Modify: `cap4k-plugin-code-analysis-compiler/src/main/kotlin/com/only4/cap4k/plugin/codeanalysis/compiler/DesignElementCollector.kt`
- Modify: `cap4k-plugin-code-analysis-compiler/src/main/kotlin/com/only4/cap4k/plugin/codeanalysis/compiler/DesignElementJsonWriter.kt`
- Modify: `cap4k-plugin-code-analysis-compiler/src/test/kotlin/com/only4/cap4k/plugin/codeanalysis/compiler/DesignElementExtractionTest.kt`
- Modify: `cap4k-plugin-code-analysis-compiler/src/test/kotlin/com/only4/cap4k/plugin/codeanalysis/compiler/DesignElementJsonWriterTest.kt`

- [ ] **Step 1: Update existing extraction test to expect new design tags**

In `DesignElementExtractionTest.kt`, inside `emits design-elements json from request and payload`, replace these assertions:

```kotlin
assertTrue(json.contains("\"tag\":\"cmd\""))
assertTrue(json.contains("\"tag\":\"qry\""))
assertTrue(json.contains("\"tag\":\"cli\""))
assertTrue(json.contains("\"tag\":\"de\""))
val autoLogin = findObject(objects, "qry", "AutoLogin")
val fireAndForget = findObject(objects, "cmd", "FireAndForget")
val topCmd = findObject(objects, "cmd", "Top")
val topQry = findObject(objects, "qry", "Top")
val topCli = findObject(objects, "cli", "Top")
val userCreated = findObject(objects, "de", "UserCreated")
```

with:

```kotlin
assertTrue(json.contains("\"tag\":\"command\""))
assertTrue(json.contains("\"tag\":\"query\""))
assertTrue(json.contains("\"tag\":\"client\""))
assertTrue(json.contains("\"tag\":\"api_payload\""))
assertTrue(json.contains("\"tag\":\"domain_event\""))
val autoLogin = findObject(objects, "query", "AutoLogin")
val fireAndForget = findObject(objects, "command", "FireAndForget")
val topCmd = findObject(objects, "command", "Top")
val topQry = findObject(objects, "query", "Top")
val topCli = findObject(objects, "client", "Top")
val userCreated = findObject(objects, "domain_event", "UserCreated")
```

- [ ] **Step 2: Add failing writer test for validator fields**

In `DesignElementJsonWriterTest.kt`, add this import:

```kotlin
import com.only4.cap4k.plugin.codeanalysis.core.model.DesignParameter
```

Append this test:

```kotlin
@Test
fun `serializes validator projection fields`() {
    val elements = listOf(
        DesignElement(
            tag = "validator",
            `package` = "danmuku",
            name = "DanmukuDeletePermission",
            desc = "delete permission",
            message = "无删除权限",
            targets = listOf("CLASS"),
            valueType = "Any",
            parameters = listOf(
                DesignParameter(
                    name = "danmukuIdField",
                    type = "String",
                    nullable = false,
                    defaultValue = "danmukuId",
                ),
            ),
        )
    )

    val json = DesignElementJsonWriter().write(elements)

    assertTrue(json.contains("\"tag\":\"validator\""))
    assertTrue(json.contains("\"message\":\"无删除权限\""))
    assertTrue(json.contains("\"targets\":[\"CLASS\"]"))
    assertTrue(json.contains("\"valueType\":\"Any\""))
    assertTrue(json.contains("\"parameters\":[{\"name\":\"danmukuIdField\""))
    assertTrue(json.contains("\"defaultValue\":\"danmukuId\""))
}
```

- [ ] **Step 3: Run compiler tests and verify they fail**

Run:

```powershell
.\gradlew.bat :cap4k-plugin-code-analysis-compiler:test --tests "com.only4.cap4k.plugin.codeanalysis.compiler.DesignElementExtractionTest" --tests "com.only4.cap4k.plugin.codeanalysis.compiler.DesignElementJsonWriterTest"
```

Expected: FAIL because the collector still emits old tags and the writer does not serialize validator fields.

- [ ] **Step 4: Normalize collector tags to new design vocabulary**

In `DesignElementCollector.kt`, change `collectPayloadElement` to emit:

```kotlin
tag = "api_payload",
```

Change `collectDomainEventElement` to emit:

```kotlin
tag = "domain_event",
```

Change the `RequestKind` enum to:

```kotlin
private enum class RequestKind(
    val tag: String,
    val packageMarker: String
) {
    COMMAND("command", ".commands"),
    QUERY("query", ".queries"),
    CLI("client", ".distributed.clients")
}
```

- [ ] **Step 5: Serialize validator-only projection fields**

In `DesignElementJsonWriter.kt`, add this import:

```kotlin
import com.only4.cap4k.plugin.codeanalysis.core.model.DesignParameter
```

After the existing `persist` serialization block, add:

```kotlin
element.message?.let { value ->
    append(",\"message\":\"").append(escape(value)).append("\"")
}
if (element.targets.isNotEmpty()) {
    append(",\"targets\":")
    appendStringList(element.targets)
}
element.valueType?.let { value ->
    append(",\"valueType\":\"").append(escape(value)).append("\"")
}
if (element.parameters.isNotEmpty()) {
    append(",\"parameters\":")
    appendParameterList(element.parameters)
}
```

Add this helper below `appendFieldList`:

```kotlin
private fun StringBuilder.appendParameterList(parameters: List<DesignParameter>) {
    append('[')
    var firstParameter = true
    parameters.forEach { parameter ->
        if (!firstParameter) append(',') else firstParameter = false
        append("{\"name\":\"").append(escape(parameter.name)).append("\",")
        append("\"type\":\"").append(escape(parameter.type)).append("\",")
        append("\"nullable\":").append(parameter.nullable)
        val defaultValue = parameter.defaultValue
        if (defaultValue != null) {
            append(",\"defaultValue\":\"").append(escape(defaultValue)).append("\"")
        }
        append('}')
    }
    append(']')
}
```

- [ ] **Step 6: Run compiler projection tests**

Run:

```powershell
.\gradlew.bat :cap4k-plugin-code-analysis-compiler:test --tests "com.only4.cap4k.plugin.codeanalysis.compiler.DesignElementExtractionTest" --tests "com.only4.cap4k.plugin.codeanalysis.compiler.DesignElementJsonWriterTest"
```

Expected: PASS.

- [ ] **Step 7: Commit tag normalization and writer shape**

Run:

```powershell
git add cap4k-plugin-code-analysis-compiler/src/main/kotlin/com/only4/cap4k/plugin/codeanalysis/compiler/DesignElementCollector.kt
git add cap4k-plugin-code-analysis-compiler/src/main/kotlin/com/only4/cap4k/plugin/codeanalysis/compiler/DesignElementJsonWriter.kt
git add cap4k-plugin-code-analysis-compiler/src/test/kotlin/com/only4/cap4k/plugin/codeanalysis/compiler/DesignElementExtractionTest.kt
git add cap4k-plugin-code-analysis-compiler/src/test/kotlin/com/only4/cap4k/plugin/codeanalysis/compiler/DesignElementJsonWriterTest.kt
git commit -m "feat: normalize analysis design projection tags"
```

---

### Task 6: Analysis Compiler Ordinary Validator Projection

**Files:**

- Modify: `cap4k-plugin-code-analysis-core/src/main/kotlin/com/only4/cap4k/plugin/codeanalysis/core/config/OptionsKeys.kt`
- Modify: `cap4k-plugin-code-analysis-compiler/src/main/kotlin/com/only4/cap4k/plugin/codeanalysis/compiler/Cap4kOptions.kt`
- Modify: `cap4k-plugin-code-analysis-compiler/src/main/kotlin/com/only4/cap4k/plugin/codeanalysis/compiler/DesignElementCollector.kt`
- Modify: `cap4k-plugin-code-analysis-compiler/src/test/kotlin/com/only4/cap4k/plugin/codeanalysis/compiler/DesignElementExtractionTest.kt`

- [ ] **Step 1: Add failing compiler extraction test for ordinary validators**

Append this test to `DesignElementExtractionTest.kt`:

```kotlin
@Test
fun `emits supported ordinary validators and skips unique or concrete request validators`() {
    val sources = listOf(
        SourceFile.kotlin(
            "ValidationStubs.kt",
            """
                package jakarta.validation
                import kotlin.reflect.KClass
                annotation class Constraint(val validatedBy: Array<KClass<*>>)
                interface ConstraintValidator<A : Annotation, T> {
                    fun isValid(value: T?, context: ConstraintValidatorContext): Boolean
                }
                interface ConstraintValidatorContext
                interface Payload
            """.trimIndent()
        ),
        SourceFile.kotlin(
            "CategoryMustExist.kt",
            """
                package demo.application.validators.category
                import jakarta.validation.Constraint
                import jakarta.validation.ConstraintValidator
                import jakarta.validation.ConstraintValidatorContext
                import jakarta.validation.Payload
                import kotlin.reflect.KClass

                @Target(AnnotationTarget.FIELD, AnnotationTarget.VALUE_PARAMETER)
                @Retention(AnnotationRetention.RUNTIME)
                @Constraint(validatedBy = [CategoryMustExist.Validator::class])
                annotation class CategoryMustExist(
                    val message: String = "分类不存在",
                    val groups: Array<KClass<*>> = [],
                    val payload: Array<KClass<out Payload>> = [],
                ) {
                    class Validator : ConstraintValidator<CategoryMustExist, Long> {
                        override fun isValid(value: Long?, context: ConstraintValidatorContext): Boolean = true
                    }
                }
            """.trimIndent()
        ),
        SourceFile.kotlin(
            "DanmukuDeletePermission.kt",
            """
                package demo.application.validators.danmuku
                import jakarta.validation.Constraint
                import jakarta.validation.ConstraintValidator
                import jakarta.validation.ConstraintValidatorContext
                import jakarta.validation.Payload
                import kotlin.reflect.KClass

                @Target(AnnotationTarget.CLASS)
                @Retention(AnnotationRetention.RUNTIME)
                @Constraint(validatedBy = [DanmukuDeletePermission.Validator::class])
                annotation class DanmukuDeletePermission(
                    val message: String = "无删除权限",
                    val groups: Array<KClass<*>> = [],
                    val payload: Array<KClass<out Payload>> = [],
                    val danmukuIdField: String = "danmukuId",
                    val operatorIdField: String = "operatorId",
                ) {
                    class Validator : ConstraintValidator<DanmukuDeletePermission, Any> {
                        override fun isValid(value: Any?, context: ConstraintValidatorContext): Boolean = true
                    }
                }
            """.trimIndent()
        ),
        SourceFile.kotlin(
            "UniqueUserMessageMessageKey.kt",
            """
                package demo.application.validators.user_message.unique
                import jakarta.validation.Constraint
                import jakarta.validation.ConstraintValidator
                import jakarta.validation.ConstraintValidatorContext
                import jakarta.validation.Payload
                import kotlin.reflect.KClass

                @Target(AnnotationTarget.CLASS)
                @Retention(AnnotationRetention.RUNTIME)
                @Constraint(validatedBy = [UniqueUserMessageMessageKey.Validator::class])
                annotation class UniqueUserMessageMessageKey(
                    val message: String = "重复",
                    val groups: Array<KClass<*>> = [],
                    val payload: Array<KClass<out Payload>> = [],
                ) {
                    class Validator : ConstraintValidator<UniqueUserMessageMessageKey, Any> {
                        override fun isValid(value: Any?, context: ConstraintValidatorContext): Boolean = true
                    }
                }
            """.trimIndent()
        ),
        SourceFile.kotlin(
            "VideoDeletePermission.kt",
            """
                package demo.application.validators.video
                import jakarta.validation.Constraint
                import jakarta.validation.ConstraintValidator
                import jakarta.validation.ConstraintValidatorContext
                import jakarta.validation.Payload
                import kotlin.reflect.KClass

                object DeleteVideoPostCmd {
                    data class Request(val videoId: Long)
                }

                @Target(AnnotationTarget.CLASS)
                @Retention(AnnotationRetention.RUNTIME)
                @Constraint(validatedBy = [VideoDeletePermission.Validator::class])
                annotation class VideoDeletePermission(
                    val message: String = "无删除权限",
                    val groups: Array<KClass<*>> = [],
                    val payload: Array<KClass<out Payload>> = [],
                ) {
                    class Validator : ConstraintValidator<VideoDeletePermission, DeleteVideoPostCmd.Request> {
                        override fun isValid(value: DeleteVideoPostCmd.Request?, context: ConstraintValidatorContext): Boolean = true
                    }
                }
            """.trimIndent()
        )
    )

    val outputDir = compileWithCap4kPlugin(sources)
    val json = outputDir.resolve("design-elements.json").toFile().readText()
    val objects = extractTopLevelObjects(json)
    val category = findObject(objects, "validator", "CategoryMustExist")
    val danmuku = findObject(objects, "validator", "DanmukuDeletePermission")

    assertTrue(category.contains("\"package\":\"category\""))
    assertTrue(category.contains("\"message\":\"分类不存在\""))
    assertTrue(category.contains("\"targets\":[\"FIELD\",\"VALUE_PARAMETER\"]"))
    assertTrue(category.contains("\"valueType\":\"Long\""))
    assertTrue(danmuku.contains("\"package\":\"danmuku\""))
    assertTrue(danmuku.contains("\"message\":\"无删除权限\""))
    assertTrue(danmuku.contains("\"targets\":[\"CLASS\"]"))
    assertTrue(danmuku.contains("\"valueType\":\"Any\""))
    assertTrue(danmuku.contains("\"name\":\"danmukuIdField\""))
    assertTrue(danmuku.contains("\"defaultValue\":\"danmukuId\""))
    assertFalse(json.contains("UniqueUserMessageMessageKey"))
    assertFalse(json.contains("VideoDeletePermission"))
}
```

- [ ] **Step 2: Run compiler extraction test and verify it fails**

Run:

```powershell
.\gradlew.bat :cap4k-plugin-code-analysis-compiler:test --tests "com.only4.cap4k.plugin.codeanalysis.compiler.DesignElementExtractionTest"
```

Expected: FAIL because validator annotation classes are not collected.

- [ ] **Step 3: Add configurable Bean Validation option keys**

In `OptionsKeys.kt`, add:

```kotlin
const val CONSTRAINT_ANNOTATION_FQ = "cap4k.codeanalysis.constraintAnnotationFq"
const val CONSTRAINT_VALIDATOR_FQ = "cap4k.codeanalysis.constraintValidatorFq"
```

In `Cap4kOptions.kt`, add constructor fields after `requestParamFq`:

```kotlin
val constraintAnnFq: String = "jakarta.validation.Constraint",
val constraintValidatorFq: String = "jakarta.validation.ConstraintValidator",
```

In `fromSystemProperties()`, add:

```kotlin
constraintAnnFq = System.getProperty(OptionsKeys.CONSTRAINT_ANNOTATION_FQ)
    ?: "jakarta.validation.Constraint",
constraintValidatorFq = System.getProperty(OptionsKeys.CONSTRAINT_VALIDATOR_FQ)
    ?: "jakarta.validation.ConstraintValidator",
```

- [ ] **Step 4: Add validator projection imports and constants**

In `DesignElementCollector.kt`, add imports:

```kotlin
import com.only4.cap4k.plugin.codeanalysis.core.model.DesignParameter
import org.jetbrains.kotlin.ir.expressions.IrGetEnumValue
import org.jetbrains.kotlin.ir.expressions.IrVararg
```

Add fields near the existing annotation FQ fields:

```kotlin
private val constraintAnnFq = FqName(options.constraintAnnFq)
private val constraintValidatorFq = FqName(options.constraintValidatorFq)
```

Add constants near the file-level constants:

```kotlin
private val SupportedValidatorTargets = setOf("CLASS", "FIELD", "VALUE_PARAMETER")
private val SupportedValidatorValueTypes = setOf("Any", "String", "Long", "Int", "Boolean")
private val SupportedValidatorParameterTypes = setOf("String", "Int", "Long", "Boolean")
private val StandardValidatorParameterNames = setOf("message", "groups", "payload")
```

- [ ] **Step 5: Detect validator annotation classes**

In `visitClass`, add this branch after the domain-event branch:

```kotlin
declaration.kind == ClassKind.ANNOTATION_CLASS && declaration.hasAnnotation(constraintAnnFq) ->
    collectValidatorElement(declaration, fqcn)
```

Add this collector method:

```kotlin
private fun collectValidatorElement(declaration: IrClass, fqcn: String) {
    if (isAggregateUniqueValidator(fqcn)) return
    val valueType = resolveConstraintValidatorValueType(declaration) ?: return
    if (valueType !in SupportedValidatorValueTypes) return
    val targets = readAnnotationTargets(declaration)
        .filter { it in SupportedValidatorTargets }
        .distinct()
        .sortedBy { target -> ValidatorTargetOrder[target] ?: Int.MAX_VALUE }
    if (targets.isEmpty()) return
    val parameters = collectValidatorParameters(declaration) ?: return

    addElement(
        DesignElement(
            tag = "validator",
            `package` = extractPackage(fqcn, ".application.validators"),
            name = declaration.name.asString(),
            desc = "",
            message = readAnnotationConstructorDefault(declaration, "message") ?: "校验未通过",
            targets = targets,
            valueType = valueType,
            parameters = parameters,
        )
    )
}
```

Add this package filter:

```kotlin
private fun isAggregateUniqueValidator(fqcn: String): Boolean {
    val packageName = fqcn.substringBeforeLast(".", "")
    return packageName.contains(".application.validators.") &&
        (packageName.endsWith(".unique") || packageName.contains(".unique."))
}
```

- [ ] **Step 6: Resolve validator generic type and supported parameters**

Add these helpers in `DesignElementCollector.kt`:

```kotlin
private fun resolveConstraintValidatorValueType(annotationClass: IrClass): String? {
    val nestedValidator = findNestedClass(annotationClass, "Validator") ?: return null
    val annotationFqcn = annotationClass.fqNameWhenAvailable?.asString() ?: return null
    val matchingSuperType = nestedValidator.superTypes
        .mapNotNull { it as? IrSimpleType }
        .firstOrNull { type ->
            val owner = type.classifier?.owner as? IrClass ?: return@firstOrNull false
            owner.fqNameWhenAvailable == constraintValidatorFq
        } ?: return null
    val arguments = matchingSuperType.arguments
    if (arguments.size < 2) return null
    val annotationType = arguments[0].typeOrNull ?: return null
    val valueType = arguments[1].typeOrNull ?: return null
    if (typeFormatter.format(annotationType) != annotationFqcn.substringAfterLast('.')) return null
    return typeFormatter.format(valueType).removeSuffix("?")
}

private fun collectValidatorParameters(annotationClass: IrClass): List<DesignParameter>? {
    val ctor = annotationClass.primaryConstructor ?: return emptyList()
    val parameters = mutableListOf<DesignParameter>()
    ctor.valueParameters.forEach { parameter ->
        val name = parameter.name.asString()
        if (name in StandardValidatorParameterNames) return@forEach
        val type = typeFormatter.format(parameter.type).removeSuffix("?")
        if (type !in SupportedValidatorParameterTypes) return null
        parameters += DesignParameter(
            name = name,
            type = type,
            nullable = parameter.type.isNullable(),
            defaultValue = resolveDefaultValue(parameter),
        )
    }
    return parameters
}
```

If the Kotlin compiler version does not expose `typeOrNull` on type arguments directly, add this local helper and use it in the function above:

```kotlin
private val org.jetbrains.kotlin.ir.types.IrTypeArgument.typeOrNull: IrType?
    get() = (this as? org.jetbrains.kotlin.ir.types.IrTypeProjection)?.type
```

- [ ] **Step 7: Read annotation targets and message default**

Add these helpers in `DesignElementCollector.kt`:

```kotlin
private fun readAnnotationTargets(annotationClass: IrClass): List<String> {
    val targetAnnotation = annotationClass.annotations.firstOrNull {
        it.symbol.owner.parentAsClass.fqNameWhenAvailable?.asString() == "kotlin.annotation.Target"
    } ?: return emptyList()
    return targetAnnotation.getEnumVarargArg("allowedTargets")
}

private fun readAnnotationConstructorDefault(annotationClass: IrClass, parameterName: String): String? {
    val ctor = annotationClass.primaryConstructor ?: return null
    return ctor.valueParameters
        .firstOrNull { it.name.asString() == parameterName }
        ?.let { resolveDefaultValue(it) }
}

private fun IrConstructorCall.getEnumVarargArg(name: String): List<String> {
    val idx = symbol.owner.valueParameterIndex(name)
    if (idx < 0) return emptyList()
    val arg = getValueArgument(idx) ?: return emptyList()
    return when (arg) {
        is IrVararg -> arg.elements.mapNotNull { element ->
            (element as? IrGetEnumValue)?.symbol?.owner?.name?.asString()
        }
        is IrGetEnumValue -> listOf(arg.symbol.owner.name.asString())
        else -> emptyList()
    }
}
```

Add this constant map near `SupportedValidatorTargets`:

```kotlin
private val ValidatorTargetOrder = mapOf(
    "CLASS" to 0,
    "FIELD" to 1,
    "VALUE_PARAMETER" to 2,
)
```

- [ ] **Step 8: Run compiler extraction test**

Run:

```powershell
.\gradlew.bat :cap4k-plugin-code-analysis-compiler:test --tests "com.only4.cap4k.plugin.codeanalysis.compiler.DesignElementExtractionTest"
```

Expected: PASS.

- [ ] **Step 9: Commit validator projection**

Run:

```powershell
git add cap4k-plugin-code-analysis-core/src/main/kotlin/com/only4/cap4k/plugin/codeanalysis/core/config/OptionsKeys.kt
git add cap4k-plugin-code-analysis-compiler/src/main/kotlin/com/only4/cap4k/plugin/codeanalysis/compiler/Cap4kOptions.kt
git add cap4k-plugin-code-analysis-compiler/src/main/kotlin/com/only4/cap4k/plugin/codeanalysis/compiler/DesignElementCollector.kt
git add cap4k-plugin-code-analysis-compiler/src/test/kotlin/com/only4/cap4k/plugin/codeanalysis/compiler/DesignElementExtractionTest.kt
git commit -m "feat: project ordinary validators from analysis"
```

---

### Task 7: Preserve Validator Projection Through IR Source And Drawing-Board

**Files:**

- Modify: `cap4k-plugin-pipeline-source-ir-analysis/src/main/kotlin/com/only4/cap4k/plugin/pipeline/source/ir/IrAnalysisSourceProvider.kt`
- Modify: `cap4k-plugin-pipeline-source-ir-analysis/src/test/kotlin/com/only4/cap4k/plugin/pipeline/source/ir/IrAnalysisSourceProviderTest.kt`
- Modify: `cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssembler.kt`
- Modify: `cap4k-plugin-pipeline-core/src/test/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssemblerTest.kt`
- Modify: `cap4k-plugin-pipeline-generator-drawing-board/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/drawingboard/DrawingBoardArtifactPlanner.kt`
- Modify: `cap4k-plugin-pipeline-generator-drawing-board/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/drawingboard/DrawingBoardArtifactPlannerTest.kt`
- Modify: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/drawing-board/document.json.peb`

- [ ] **Step 1: Add failing source-ir parser assertions for validator fields**

In `IrAnalysisSourceProviderTest.kt`, add this import:

```kotlin
import com.only4.cap4k.plugin.pipeline.api.ValidatorParameterModel
```

Inside `collect parses design elements when file exists`, add this object to `design-elements.json` before the blank tag object:

```json
{
  "tag": "validator",
  "package": "danmuku",
  "name": "DanmukuDeletePermission",
  "desc": "delete permission",
  "message": "无删除权限",
  "targets": ["CLASS"],
  "valueType": "Any",
  "parameters": [
    {
      "name": "danmukuIdField",
      "type": "String",
      "nullable": false,
      "defaultValue": "danmukuId"
    }
  ]
}
```

Change the size assertion from:

```kotlin
assertEquals(2, snapshot.designElements.size)
```

to:

```kotlin
assertEquals(3, snapshot.designElements.size)
```

Add these assertions after the domain-event assertions:

```kotlin
val validator = snapshot.designElements.single { it.tag == "validator" }
assertEquals("danmuku", validator.packageName)
assertEquals("DanmukuDeletePermission", validator.name)
assertEquals("无删除权限", validator.message)
assertEquals(listOf("CLASS"), validator.targets)
assertEquals("Any", validator.valueType)
assertEquals(
    listOf(
        ValidatorParameterModel(
            name = "danmukuIdField",
            type = "String",
            nullable = false,
            defaultValue = "danmukuId",
        )
    ),
    validator.parameters,
)
```

- [ ] **Step 2: Run source-ir test and verify it fails**

Run:

```powershell
.\gradlew.bat :cap4k-plugin-pipeline-source-ir-analysis:test --tests "com.only4.cap4k.plugin.pipeline.source.ir.IrAnalysisSourceProviderTest"
```

Expected: FAIL because `IrAnalysisSourceProvider` does not parse validator fields yet.

- [ ] **Step 3: Parse validator fields and parameters in source-ir**

In `IrAnalysisSourceProvider.kt`, add:

```kotlin
import com.only4.cap4k.plugin.pipeline.api.ValidatorParameterModel
```

In `parseDesignElements`, add these constructor arguments:

```kotlin
message = obj.stringValue("message"),
targets = obj.stringList("targets"),
valueType = obj.stringValue("valueType"),
parameters = parseValidatorParameters(obj.jsonArrayOrEmpty("parameters")),
```

Add this helper below `parseDesignFields`:

```kotlin
private fun parseValidatorParameters(array: com.google.gson.JsonArray?): List<ValidatorParameterModel> {
    if (array == null) {
        return emptyList()
    }
    return array.mapNotNull { element ->
        val obj = element.asJsonObjectOrNull() ?: return@mapNotNull null
        val name = obj.stringValue("name").orEmpty().trim()
        if (name.isEmpty()) {
            return@mapNotNull null
        }
        ValidatorParameterModel(
            name = name,
            type = obj.stringValue("type").orEmpty().trim(),
            nullable = obj.booleanValue("nullable") ?: false,
            defaultValue = obj.stringValue("defaultValue"),
        )
    }
}
```

- [ ] **Step 4: Run source-ir test**

Run:

```powershell
.\gradlew.bat :cap4k-plugin-pipeline-source-ir-analysis:test --tests "com.only4.cap4k.plugin.pipeline.source.ir.IrAnalysisSourceProviderTest"
```

Expected: PASS.

- [ ] **Step 5: Add failing canonical drawing-board test for validator elements**

In `DefaultCanonicalAssemblerTest.kt`, inside `assembles drawing board as generate ready design json contract`, add this `DesignElementSnapshot` to the snapshot list:

```kotlin
DesignElementSnapshot(
    tag = "validator",
    packageName = "danmuku",
    name = "DanmukuDeletePermission",
    description = "delete permission",
    message = "无删除权限",
    targets = listOf("CLASS"),
    valueType = "Any",
    parameters = listOf(
        ValidatorParameterModel(
            name = "danmukuIdField",
            type = "String",
            defaultValue = "danmukuId",
        )
    ),
),
```

Change:

```kotlin
assertEquals(5, drawingBoard!!.elements.size)
```

to:

```kotlin
assertEquals(6, drawingBoard!!.elements.size)
```

Change the expected keys to:

```kotlin
assertEquals(
    listOf("command", "client", "query", "api_payload", "domain_event", "validator"),
    drawingBoard.elementsByTag.keys.toList(),
)
```

Add:

```kotlin
val validator = drawingBoard.elementsByTag.getValue("validator").single()
assertEquals("无删除权限", validator.message)
assertEquals(listOf("CLASS"), validator.targets)
assertEquals("Any", validator.valueType)
assertEquals("danmukuIdField", validator.parameters.single().name)
```

- [ ] **Step 6: Preserve validator fields in canonical drawing-board assembly**

In `DefaultCanonicalAssembler.toDrawingBoardElementOrNull()`, add these arguments to `DrawingBoardElementModel(...)`:

```kotlin
message = message,
targets = targets,
valueType = valueType,
parameters = parameters,
```

In `normalizeDrawingBoardTag`, add:

```kotlin
"validator" -> "validator"
```

In `SupportedDrawingBoardTags`, add `"validator"`:

```kotlin
val SupportedDrawingBoardTags = setOf("command", "query", "client", "api_payload", "domain_event", "validator")
```

- [ ] **Step 7: Run canonical test**

Run:

```powershell
.\gradlew.bat :cap4k-plugin-pipeline-core:test --tests "com.only4.cap4k.plugin.pipeline.core.DefaultCanonicalAssemblerTest"
```

Expected: PASS.

- [ ] **Step 8: Add failing drawing-board planner test for validator output**

In `DrawingBoardArtifactPlannerTest.kt`, add this import:

```kotlin
import com.only4.cap4k.plugin.pipeline.api.ValidatorParameterModel
```

Update `plans one artifact per non empty supported tag group in order` expected output names to:

```kotlin
listOf(
    "drawing_board_command",
    "drawing_board_query",
    "drawing_board_client",
    "drawing_board_api_payload",
    "drawing_board_domain_event",
    "drawing_board_validator",
)
```

Update expected template list to six copies of `"drawing-board/document.json.peb"`.

Add:

```kotlin
assertEquals("validator", plan[5].context["drawingBoardTag"])
```

Add this element to `model()`:

```kotlin
DrawingBoardElementModel(
    tag = "validator",
    packageName = "danmuku",
    name = "DanmukuDeletePermission",
    description = "delete permission",
    message = "无删除权限",
    targets = listOf("CLASS"),
    valueType = "Any",
    parameters = listOf(
        ValidatorParameterModel(
            name = "danmukuIdField",
            type = "String",
            defaultValue = "danmukuId",
        )
    ),
),
```

- [ ] **Step 9: Add validator to drawing-board planner and template**

In `DrawingBoardArtifactPlanner.kt`, change:

```kotlin
val supportedTags = listOf("command", "query", "client", "api_payload", "domain_event")
```

to:

```kotlin
val supportedTags = listOf("command", "query", "client", "api_payload", "domain_event", "validator")
```

In `document.json.peb`, render validator fields after `persist` and before `requestFields`:

```peb
{%- if element.message is not null %},
    "message": {{ element.message | json | raw }}
{%- endif %}
{%- if element.targets|length > 0 %},
    "targets": [{% for target in element.targets %}{{ target | json | raw }}{% if not loop.last %}, {% endif %}{% endfor %}]
{%- endif %}
{%- if element.valueType is not null %},
    "valueType": {{ element.valueType | json | raw }}
{%- endif %}
{%- if element.parameters|length > 0 %},
    "parameters": [{% for parameter in element.parameters %}
      {
        "name": {{ parameter.name | json | raw }},
        "type": {{ parameter.type | json | raw }},
        "nullable": {{ parameter.nullable }}{% if parameter.defaultValue is not null %},
        "defaultValue": {{ parameter.defaultValue | json | raw }}{% endif %}
      }{% if not loop.last %}, {% endif %}{% endfor %}
    ]
{%- endif %},
```

- [ ] **Step 10: Run drawing-board planner test**

Run:

```powershell
.\gradlew.bat :cap4k-plugin-pipeline-generator-drawing-board:test --tests "com.only4.cap4k.plugin.pipeline.generator.drawingboard.DrawingBoardArtifactPlannerTest"
```

Expected: PASS.

- [ ] **Step 11: Commit IR source and drawing-board preservation**

Run:

```powershell
git add cap4k-plugin-pipeline-source-ir-analysis/src/main/kotlin/com/only4/cap4k/plugin/pipeline/source/ir/IrAnalysisSourceProvider.kt
git add cap4k-plugin-pipeline-source-ir-analysis/src/test/kotlin/com/only4/cap4k/plugin/pipeline/source/ir/IrAnalysisSourceProviderTest.kt
git add cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssembler.kt
git add cap4k-plugin-pipeline-core/src/test/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssemblerTest.kt
git add cap4k-plugin-pipeline-generator-drawing-board/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/drawingboard/DrawingBoardArtifactPlanner.kt
git add cap4k-plugin-pipeline-generator-drawing-board/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/drawingboard/DrawingBoardArtifactPlannerTest.kt
git add cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/drawing-board/document.json.peb
git commit -m "feat: preserve validators in drawing board"
```

---

### Task 8: Analysis Drawing-Board To Generate Round-Trip Functional Coverage

**Files:**

- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/analysis-validator-roundtrip-sample/`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginFunctionalTest.kt`

- [ ] **Step 1: Create round-trip functional fixture directory**

Create these files:

```text
cap4k-plugin-pipeline-gradle/src/test/resources/functional/analysis-validator-roundtrip-sample/build.gradle.kts
cap4k-plugin-pipeline-gradle/src/test/resources/functional/analysis-validator-roundtrip-sample/settings.gradle.kts
cap4k-plugin-pipeline-gradle/src/test/resources/functional/analysis-validator-roundtrip-sample/demo-application/build.gradle.kts
cap4k-plugin-pipeline-gradle/src/test/resources/functional/analysis-validator-roundtrip-sample/demo-application/src/main/kotlin/com/acme/demo/application/smoke/CompileSmoke.kt
cap4k-plugin-pipeline-gradle/src/test/resources/functional/analysis-validator-roundtrip-sample/design/.gitkeep
```

Use this `settings.gradle.kts`:

```kotlin
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

includeBuild("__CAP4K_REPO_ROOT__")

rootProject.name = "analysis-validator-roundtrip-sample"
include("demo-application")
```

Use this `demo-application/build.gradle.kts`:

```kotlin
plugins {
    kotlin("jvm") version "2.2.20"
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation("jakarta.validation:jakarta.validation-api:3.0.2")
    implementation("org.jetbrains.kotlin:kotlin-reflect:2.2.20")
}
```

Use this `CompileSmoke.kt`:

```kotlin
package com.acme.demo.application.smoke

object CompileSmoke
```

- [ ] **Step 2: Add fixture root build script**

Use this `build.gradle.kts`:

```kotlin
plugins {
    id("com.only4.cap4k.plugin.pipeline")
}

val analysisDir = layout.buildDirectory.dir("cap4k-code-analysis")

tasks.register("compileKotlin") {
    outputs.dir(analysisDir)
    doLast {
        val outputDir = analysisDir.get().asFile
        outputDir.mkdirs()
        outputDir.resolve("nodes.json").writeText("""[]""")
        outputDir.resolve("rels.json").writeText("""[]""")
        outputDir.resolve("design-elements.json").writeText(
            """
            [
              {
                "tag": "validator",
                "package": "danmuku",
                "name": "DanmukuDeletePermission",
                "desc": "delete permission",
                "message": "无删除权限",
                "targets": ["CLASS"],
                "valueType": "Any",
                "parameters": [
                  {
                    "name": "danmukuIdField",
                    "type": "String",
                    "nullable": false,
                    "defaultValue": "danmukuId"
                  }
                ]
              }
            ]
            """.trimIndent()
        )
    }
}

cap4k {
    project {
        basePackage.set("com.acme.demo")
        applicationModulePath.set("demo-application")
    }
    sources {
        irAnalysis {
            enabled.set(true)
            inputDirs.from(analysisDir)
        }
        designJson {
            enabled.set(true)
            files.from("design/drawing_board_validator.json")
        }
    }
    generators {
        drawingBoard {
            enabled.set(true)
        }
        designValidator {
            enabled.set(true)
        }
    }
}
```

- [ ] **Step 3: Add functional round-trip test**

Append this test near the other analysis drawing-board functional tests in `PipelinePluginFunctionalTest.kt`:

```kotlin
@OptIn(ExperimentalPathApi::class)
@Test
fun `cap4kAnalysisGenerate validator drawing board can feed cap4kGenerate`() {
    val projectDir = Files.createTempDirectory("pipeline-functional-analysis-validator-roundtrip")
    copyFixture(projectDir, "analysis-validator-roundtrip-sample")

    val analysisResult = GradleRunner.create()
        .withProjectDir(projectDir.toFile())
        .withPluginClasspath()
        .withArguments("cap4kAnalysisGenerate")
        .build()

    val drawingBoardValidator = projectDir.resolve("design/drawing_board_validator.json")
    val drawingBoardContent = drawingBoardValidator.readText()

    val generateResult = GradleRunner.create()
        .withProjectDir(projectDir.toFile())
        .withPluginClasspath()
        .withArguments("cap4kGenerate")
        .build()

    val compileResult = GradleRunner.create()
        .withProjectDir(projectDir.toFile())
        .withPluginClasspath()
        .withArguments(":demo-application:compileKotlin")
        .build()

    val generatedValidator = projectDir.resolve(
        "demo-application/src/main/kotlin/com/acme/demo/application/validators/danmuku/DanmukuDeletePermission.kt"
    )
    val generatedContent = generatedValidator.readText()

    assertTrue(analysisResult.output.contains("BUILD SUCCESSFUL"))
    assertTrue(drawingBoardValidator.toFile().exists())
    assertTrue(drawingBoardContent.contains("\"tag\": \"validator\""))
    assertTrue(drawingBoardContent.contains("\"targets\": [\"CLASS\"]"))
    assertTrue(drawingBoardContent.contains("\"valueType\": \"Any\""))
    assertTrue(generateResult.output.contains("BUILD SUCCESSFUL"))
    assertTrue(compileResult.output.contains("BUILD SUCCESSFUL"))
    assertTrue(generatedContent.contains("annotation class DanmukuDeletePermission"))
    assertTrue(generatedContent.contains("ConstraintValidator<DanmukuDeletePermission, Any>"))
    assertTrue(generatedContent.contains("val danmukuIdField: String = \"danmukuId\""))
}
```

- [ ] **Step 4: Run only the round-trip functional test**

Run with a long execution timeout in the shell tool:

```powershell
.\gradlew.bat :cap4k-plugin-pipeline-gradle:test --tests "com.only4.cap4k.plugin.pipeline.gradle.PipelinePluginFunctionalTest.cap4kAnalysisGenerate validator drawing board can feed cap4kGenerate"
```

Expected: PASS. If this command is run through an agent tool, set the tool timeout to at least `1500000` milliseconds.

- [ ] **Step 5: Commit round-trip fixture**

Run:

```powershell
git add cap4k-plugin-pipeline-gradle/src/test/resources/functional/analysis-validator-roundtrip-sample
git add cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginFunctionalTest.kt
git commit -m "test: cover validator drawing board round trip"
```

---

### Task 9: Documentation And Final Verification

**Files:**

- Modify: `cap4k-plugin-pipeline-gradle/README.md`
- Modify: `docs/superpowers/mainline-roadmap.md`

- [ ] **Step 1: Update README validator capability text**

In `cap4k-plugin-pipeline-gradle/README.md`, update the `designValidator` section to document this design shape:

```json
{
  "tag": "validator",
  "package": "danmuku",
  "name": "DanmukuDeletePermission",
  "desc": "校验弹幕删除权限",
  "message": "无权限删除该弹幕",
  "targets": ["CLASS"],
  "valueType": "Any",
  "parameters": [
    {
      "name": "danmukuIdField",
      "type": "String",
      "defaultValue": "danmukuId"
    }
  ]
}
```

State these rules in prose:

```text
Ordinary validators generate annotation skeletons only. The generated Validator body returns true.
Supported targets are CLASS, FIELD, and VALUE_PARAMETER.
Supported valueType values are Any, String, Long, Int, and Boolean.
Custom annotation parameters support String, Int, Long, and Boolean.
message, groups, and payload are standard Bean Validation fields and must not be declared as custom parameters.
Aggregate unique validators are generated by the aggregate family and are not ordinary design validators.
```

- [ ] **Step 2: Update README analysis drawing-board text**

In the analysis/drawing-board section, add:

```text
cap4kAnalysisGenerate can emit drawing_board_validator.json when supported ordinary Bean Validation annotations are present in analysis input.
The emitted validator file uses the same designJson contract accepted by cap4kGenerate.
nodes.json and rels.json remain graph artifacts; design-elements.json is the design projection artifact.
```

- [ ] **Step 3: Update roadmap status**

In `docs/superpowers/mainline-roadmap.md`, mark these items as implemented or link to the implementation commits:

```text
- Validator generation capability expansion
- Analysis design projection normalization
- drawing_board_validator.json round-trip
```

Do not mark broader `irAnalysis` restructuring as complete.

- [ ] **Step 4: Run targeted module tests**

Run short module tests as separate commands to avoid Windows command-line length limits:

```powershell
.\gradlew.bat :cap4k-plugin-pipeline-source-design-json:test
.\gradlew.bat :cap4k-plugin-pipeline-core:test
.\gradlew.bat :cap4k-plugin-pipeline-generator-design:test
.\gradlew.bat :cap4k-plugin-code-analysis-compiler:test
.\gradlew.bat :cap4k-plugin-pipeline-source-ir-analysis:test
.\gradlew.bat :cap4k-plugin-pipeline-generator-drawing-board:test
```

Expected: each command reports `BUILD SUCCESSFUL`.

- [ ] **Step 5: Run Gradle functional tests with long timeout**

Run:

```powershell
.\gradlew.bat :cap4k-plugin-pipeline-gradle:test --tests "com.only4.cap4k.plugin.pipeline.gradle.PipelinePluginFunctionalTest"
```

Expected: PASS. If this command is run through an agent tool, set timeout to at least `1500000` milliseconds.

- [ ] **Step 6: Verify no old analysis tags are newly asserted as expected output**

Run:

```powershell
rg '"tag":"(cmd|qry|cli|payload|de)"' cap4k-plugin-code-analysis-compiler/src/test cap4k-plugin-pipeline-gradle/src/test/resources/functional
```

Expected: no matches in new or updated assertions. Existing historical fixture files may remain only if a test explicitly verifies old input compatibility.

- [ ] **Step 7: Verify plan constraints stayed intact**

Run:

```powershell
git diff --check
git status --short
```

Expected: no whitespace errors. `git status --short` should show only the intentional documentation edits before the final commit.

- [ ] **Step 8: Commit docs and final verification updates**

Run:

```powershell
git add cap4k-plugin-pipeline-gradle/README.md
git add docs/superpowers/mainline-roadmap.md
git commit -m "docs: document validator projection normalization"
```

# Cap4k Design Generator Default Value Upgrade Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add generator-owned default-value formatting so explicit `defaultValue` in design input renders into valid Kotlin field defaults for generated `Cmd/Qry` source.

**Architecture:** Keep all default-value validation and normalization inside `cap4k-plugin-pipeline-generator-design`, then let Pebble templates emit `= ...` without making any syntax decisions. Reuse the existing render model contract and existing design generator/renderer/Gradle functional test layers instead of touching pipeline core.

**Tech Stack:** Kotlin, Gradle TestKit, JUnit 5, Pebble templates, existing cap4k pipeline design generator modules.

---

## File Map

- Create: `cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DefaultValueFormatter.kt`
  - Small generator-local formatter for explicit design-field defaults.
- Create: `cap4k-plugin-pipeline-generator-design/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DefaultValueFormatterTest.kt`
  - Unit tests for supported default categories and failure paths.
- Modify: `cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignRenderModelFactory.kt`
  - Call the formatter and store Kotlin-ready default expressions in the render model.
- Modify: `cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignRenderModels.kt`
  - Clarify that `defaultValue` is a Kotlin-ready expression.
- Modify: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/design/command.kt.peb`
  - Emit field default values in generated command source.
- Modify: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/design/query.kt.peb`
  - Emit field default values in generated query source.
- Modify: `cap4k-plugin-pipeline-renderer-pebble/src/test/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PebbleArtifactRendererTest.kt`
  - Verify template output includes `= ...` when `defaultValue` exists.
- Modify: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginFunctionalTest.kt`
  - Add end-to-end assertions for supported defaults and invalid defaults.
- Modify: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-sample/design/design.json`
  - Add supported defaults to the existing happy-path fixture.
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-default-value-invalid-sample/build.gradle.kts`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-default-value-invalid-sample/settings.gradle.kts`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-default-value-invalid-sample/design/design.json`
  - Dedicated failing fixture for invalid default values.

## Task 1: Add Generator-Level Default Value Tests

**Files:**
- Create: `cap4k-plugin-pipeline-generator-design/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DefaultValueFormatterTest.kt`

- [ ] **Step 1: Write the failing formatter tests**

```kotlin
package com.only4.cap4k.plugin.pipeline.generator.design

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class DefaultValueFormatterTest {

    @Test
    fun `quotes raw string defaults`() {
        val actual = DefaultValueFormatter.format(
            rawDefaultValue = "demo",
            renderedType = "String",
            nullable = false,
            fieldName = "title",
        )

        assertEquals("\"demo\"", actual)
    }

    @Test
    fun `keeps quoted string defaults unchanged`() {
        val actual = DefaultValueFormatter.format(
            rawDefaultValue = "\"demo\"",
            renderedType = "String",
            nullable = false,
            fieldName = "title",
        )

        assertEquals("\"demo\"", actual)
    }

    @Test
    fun `adds long suffix when needed`() {
        val actual = DefaultValueFormatter.format(
            rawDefaultValue = "1",
            renderedType = "Long",
            nullable = false,
            fieldName = "retryCount",
        )

        assertEquals("1L", actual)
    }

    @Test
    fun `preserves explicit constant expressions`() {
        val actual = DefaultValueFormatter.format(
            rawDefaultValue = "VideoStatus.PROCESSING",
            renderedType = "VideoStatus",
            nullable = false,
            fieldName = "status",
        )

        assertEquals("VideoStatus.PROCESSING", actual)
    }

    @Test
    fun `preserves supported empty collection expressions`() {
        val actual = DefaultValueFormatter.format(
            rawDefaultValue = "emptyList()",
            renderedType = "List<String>",
            nullable = false,
            fieldName = "tags",
        )

        assertEquals("emptyList()", actual)
    }

    @Test
    fun `rejects null default for non-nullable field`() {
        val ex = assertThrows(IllegalArgumentException::class.java) {
            DefaultValueFormatter.format(
                rawDefaultValue = "null",
                renderedType = "String",
                nullable = false,
                fieldName = "title",
            )
        }

        assertEquals(
            "invalid default value for field title: null is only allowed for nullable fields",
            ex.message,
        )
    }

    @Test
    fun `rejects invalid boolean literal`() {
        val ex = assertThrows(IllegalArgumentException::class.java) {
            DefaultValueFormatter.format(
                rawDefaultValue = "yes",
                renderedType = "Boolean",
                nullable = false,
                fieldName = "enabled",
            )
        }

        assertEquals(
            "invalid default value for field enabled: Boolean defaults must be true or false",
            ex.message,
        )
    }
}
```

- [ ] **Step 2: Run the formatter tests to verify they fail**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-generator-design:test --tests "com.only4.cap4k.plugin.pipeline.generator.design.DefaultValueFormatterTest" --rerun-tasks
```

Expected: FAIL with `Unresolved reference: DefaultValueFormatter`.

- [ ] **Step 3: Add minimal formatter implementation**

```kotlin
package com.only4.cap4k.plugin.pipeline.generator.design

internal object DefaultValueFormatter {
    fun format(
        rawDefaultValue: String?,
        renderedType: String,
        nullable: Boolean,
        fieldName: String,
    ): String? {
        val value = rawDefaultValue?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (value == "null") {
            require(nullable) {
                "invalid default value for field $fieldName: null is only allowed for nullable fields"
            }
            return "null"
        }

        return when (renderedType.removeSuffix("?")) {
            "String" -> normalizeString(value)
            "Long" -> normalizeLong(value, fieldName)
            "Boolean" -> normalizeBoolean(value, fieldName)
            else -> normalizeFallback(value)
        }
    }

    private fun normalizeString(value: String): String =
        if ((value.startsWith("\"") && value.endsWith("\"")) || (value.startsWith("'") && value.endsWith("'"))) {
            value
        } else {
            "\"$value\""
        }

    private fun normalizeLong(value: String, fieldName: String): String {
        require(value.matches(Regex("-?\\d+[lL]?"))) {
            "invalid default value for field $fieldName: $value is not a valid Long literal"
        }
        return if (value.endsWith("L") || value.endsWith("l")) value.replace('l', 'L') else "${value}L"
    }

    private fun normalizeBoolean(value: String, fieldName: String): String {
        require(value == "true" || value == "false") {
            "invalid default value for field $fieldName: Boolean defaults must be true or false"
        }
        return value
    }

    private fun normalizeFallback(value: String): String {
        return when {
            value in setOf("emptyList()", "emptySet()", "mutableListOf()", "mutableSetOf()") -> value
            value.matches(Regex("-?\\d+(\\.\\d+)?[fF]?$")) -> value
            value.contains('.') || value.contains("::") -> value
            else -> throw IllegalArgumentException("unsupported default value expression: $value")
        }
    }
}
```

- [ ] **Step 4: Run the formatter tests to verify they pass**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-generator-design:test --tests "com.only4.cap4k.plugin.pipeline.generator.design.DefaultValueFormatterTest" --rerun-tasks
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DefaultValueFormatter.kt cap4k-plugin-pipeline-generator-design/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DefaultValueFormatterTest.kt
git commit -m "feat: add design default value formatter"
```

## Task 2: Wire Formatted Defaults Into The Design Render Model

**Files:**
- Modify: `cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignRenderModelFactory.kt`
- Modify: `cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignRenderModels.kt`
- Modify: `cap4k-plugin-pipeline-generator-design/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignArtifactPlannerTest.kt`

- [ ] **Step 1: Add a failing planner-level test for formatted defaults**

```kotlin
@Test
fun `planner carries kotlin-ready default values into template context`() {
    val planner = DesignArtifactPlanner()
    val request = RequestModel(
        packageName = "video_post",
        typeName = "UpdateVideoPostCmd",
        kind = "command",
        description = "update video post",
        aggregateName = null,
        aggregatePackageName = null,
        requestFields = listOf(
            FieldModel(name = "title", type = "String", nullable = false, defaultValue = "demo"),
            FieldModel(name = "retryCount", type = "Long", nullable = false, defaultValue = "1"),
        ),
        responseFields = emptyList(),
    )

    val plan = planner.plan(
        CanonicalModel(requests = listOf(request)),
        ProjectConfig(
            project = ProjectSettings(
                basePackage = "com.example.demo",
                modules = mapOf("application" to "app"),
            ),
        ),
    )

    val context = plan.single().context
    val requestFields = context["requestFields"] as List<Map<String, Any?>>
    assertEquals("\"demo\"", requestFields[0]["defaultValue"])
    assertEquals("1L", requestFields[1]["defaultValue"])
}
```

- [ ] **Step 2: Run the targeted planner test and verify it fails**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-generator-design:test --tests "com.only4.cap4k.plugin.pipeline.generator.design.DesignArtifactPlannerTest" --rerun-tasks
```

Expected: FAIL because `defaultValue` is still raw or absent in the rendered context.

- [ ] **Step 3: Update render-model construction to format defaults before templating**

```kotlin
private fun PreparedFieldModel.toRenderField(renderedType: String): DesignRenderFieldModel {
    return DesignRenderFieldModel(
        name = name,
        renderedType = renderedType,
        nullable = nullable,
        defaultValue = DefaultValueFormatter.format(
            rawDefaultValue = defaultValue,
            renderedType = renderedType,
            nullable = nullable,
            fieldName = sourceName,
        ),
    )
}
```

Also add a short semantic comment in `DesignRenderModels.kt`:

```kotlin
// Kotlin-ready right-hand-side expression, not raw design input.
val defaultValue: String? = null,
```

- [ ] **Step 4: Re-run the planner and formatter tests**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-generator-design:test --tests "com.only4.cap4k.plugin.pipeline.generator.design.DefaultValueFormatterTest" --tests "com.only4.cap4k.plugin.pipeline.generator.design.DesignArtifactPlannerTest" --rerun-tasks
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignRenderModelFactory.kt cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignRenderModels.kt cap4k-plugin-pipeline-generator-design/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignArtifactPlannerTest.kt
git commit -m "feat: format design defaults before rendering"
```

## Task 3: Emit Default Values In The Default Pebble Templates

**Files:**
- Modify: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/design/command.kt.peb`
- Modify: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/design/query.kt.peb`
- Modify: `cap4k-plugin-pipeline-renderer-pebble/src/test/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PebbleArtifactRendererTest.kt`

- [ ] **Step 1: Add failing renderer tests for default-value emission**

```kotlin
@Test
fun `renders field default values in design templates`() {
    val rendered = renderer.render(
        templateRef = TemplateRef("design/command.kt.peb"),
        context = mapOf(
            "packageName" to "com.example.demo",
            "typeName" to "UpdateVideoPostCmd",
            "imports" to emptyList<String>(),
            "requestFields" to listOf(
                mapOf("name" to "title", "renderedType" to "String", "defaultValue" to "\"demo\""),
                mapOf("name" to "retryCount", "renderedType" to "Long", "defaultValue" to "1L"),
            ),
            "responseFields" to emptyList<Map<String, Any?>>(),
            "requestNestedTypes" to emptyList<Map<String, Any?>>(),
            "responseNestedTypes" to emptyList<Map<String, Any?>>(),
        ),
    )

    assertTrue(rendered.content.contains("val title: String = \"demo\""))
    assertTrue(rendered.content.contains("val retryCount: Long = 1L"))
}
```

- [ ] **Step 2: Run the renderer test and verify it fails**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-renderer-pebble:test --tests "com.only4.cap4k.plugin.pipeline.renderer.pebble.PebbleArtifactRendererTest" --rerun-tasks
```

Expected: FAIL because the default design templates still omit `= ...`.

- [ ] **Step 3: Update the default design templates**

```pebble
val {{ field.name }}: {{ type(field) | raw }}{% if field.defaultValue %} = {{ field.defaultValue | raw }}{% endif %}{% if not loop.last %},{% endif %}
```

Apply the same change in:

- `design/command.kt.peb`
- `design/query.kt.peb`

- [ ] **Step 4: Re-run renderer tests**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-renderer-pebble:test --tests "com.only4.cap4k.plugin.pipeline.renderer.pebble.PebbleArtifactRendererTest" --rerun-tasks
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/design/command.kt.peb cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/design/query.kt.peb cap4k-plugin-pipeline-renderer-pebble/src/test/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PebbleArtifactRendererTest.kt
git commit -m "feat: render default values in design templates"
```

## Task 4: Add End-To-End Functional Coverage For Supported And Invalid Defaults

**Files:**
- Modify: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginFunctionalTest.kt`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-sample/design/design.json`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-default-value-invalid-sample/build.gradle.kts`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-default-value-invalid-sample/settings.gradle.kts`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-default-value-invalid-sample/design/design.json`

- [ ] **Step 1: Add a failing functional test for valid default values**

```kotlin
@Test
fun `cap4kGenerate renders explicit default values in generated design source`() {
    val projectDir = copyFixture("design-sample")

    val result = gradleRunner(projectDir, "cap4kGenerate").build()

    assertTrue(result.output.contains("BUILD SUCCESSFUL"))
    val generated = projectDir.resolve(
        "demo-application/src/main/kotlin/com/acme/demo/application/commands/order/submit/SubmitOrderCmd.kt",
    ).readText()
    assertTrue(generated.contains("val title: String = \"demo\""))
    assertTrue(generated.contains("val retryCount: Long = 1L"))
    assertTrue(generated.contains("val enabled: Boolean = true"))
}
```

- [ ] **Step 2: Add a failing functional test for invalid defaults**

```kotlin
@Test
fun `cap4kGenerate fails fast for invalid design default value`() {
    val projectDir = copyFixture("design-default-value-invalid-sample")

    val result = gradleRunner(projectDir, "cap4kGenerate").buildAndFail()

    assertTrue(result.output.contains("invalid default value for field enabled"))
}
```

- [ ] **Step 3: Update and add fixtures**

Use `design-sample/design/design.json` to extend the existing `SubmitOrder` command with supported defaults such as:

```json
[
  {
    "tag": "cmd",
    "package": "order.submit",
    "name": "SubmitOrder",
    "requestFields": [
      { "name": "orderId", "type": "Long", "defaultValue": "1" },
      { "name": "tags", "type": "List<String>", "defaultValue": "emptyList()" },
      { "name": "createdAt", "type": "java.time.LocalDateTime", "defaultValue": "java.time.LocalDateTime.MIN" },
      { "name": "title", "type": "String", "defaultValue": "demo" },
      { "name": "enabled", "type": "Boolean", "defaultValue": "true" }
    ],
    "responseFields": []
  }
]
```

Create `design-default-value-invalid-sample/design/design.json` with:

```json
[
  {
    "tag": "cmd",
    "package": "video_post",
    "name": "InvalidVideoPost",
    "requestFields": [
      { "name": "enabled", "type": "Boolean", "defaultValue": "yes" }
    ],
    "responseFields": []
  }
]
```

Re-use the existing minimal `build.gradle.kts` / `settings.gradle.kts` shape from `design-sample`.

- [ ] **Step 4: Run the functional test suite**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-gradle:test --tests "com.only4.cap4k.plugin.pipeline.gradle.PipelinePluginFunctionalTest" --rerun-tasks
```

Expected: PASS, including one successful generation fixture and one failing fixture assertion.

- [ ] **Step 5: Commit**

```bash
git add cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginFunctionalTest.kt cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-sample/design/design.json cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-default-value-invalid-sample
git commit -m "test: cover design default values end to end"
```

## Task 5: Final Verification And Branch Closeout

**Files:**
- Modify: none
- Test: `cap4k-plugin-pipeline-generator-design`
- Test: `cap4k-plugin-pipeline-renderer-pebble`
- Test: `cap4k-plugin-pipeline-gradle`

- [ ] **Step 1: Run the focused verification suite**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-generator-design:test :cap4k-plugin-pipeline-renderer-pebble:test :cap4k-plugin-pipeline-gradle:test --rerun-tasks
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 2: Inspect generated diff**

Run:

```powershell
git diff -- cap4k-plugin-pipeline-generator-design cap4k-plugin-pipeline-renderer-pebble cap4k-plugin-pipeline-gradle
```

Expected: Diff only covers default-value formatter, render-model wiring, templates, and functional fixtures.

- [ ] **Step 3: Run a concise status check**

Run:

```powershell
git status --short
```

Expected: Only intended tracked changes remain before final branch integration.

- [ ] **Step 4: Commit final feature branch state**

```bash
git add cap4k-plugin-pipeline-generator-design cap4k-plugin-pipeline-renderer-pebble cap4k-plugin-pipeline-gradle
git commit -m "feat: support explicit default values in design generation"
```

- [ ] **Step 5: Request completion handling**

Use `superpowers:finishing-a-development-branch` and present merge / PR / keep-branch options after verification passes.

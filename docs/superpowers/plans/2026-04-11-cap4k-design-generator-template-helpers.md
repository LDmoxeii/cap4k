# Cap4k Design Generator Template Helpers Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add thin Pebble `type()` and `imports()` helpers for design templates without moving type or import decision-making out of the generator.

**Architecture:** Keep all real type/import decisions in `cap4k-plugin-pipeline-generator-design`, and add a read-only helper layer in `cap4k-plugin-pipeline-renderer-pebble`. Migrate only the default design templates and helper-oriented functional coverage so generated output stays stable while the template API becomes cleaner.

**Tech Stack:** Kotlin, Gradle, Pebble 3.2.4, JUnit 5, Gradle TestKit

---

### Task 1: Add Pebble Helper Functions In Renderer

**Files:**
- Create: `cap4k-plugin-pipeline-renderer-pebble/src/main/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PipelinePebbleExtension.kt`
- Modify: `cap4k-plugin-pipeline-renderer-pebble/src/main/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PebbleArtifactRenderer.kt`
- Modify: `cap4k-plugin-pipeline-renderer-pebble/src/test/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PebbleArtifactRendererTest.kt`

- [ ] **Step 1: Write failing renderer tests for helper success and misuse**

Add focused tests to `PebbleArtifactRendererTest.kt` that render literal helper-based templates through `PebbleArtifactRenderer`.

```kotlin
@Test
fun `type helper reads renderedType from map and string input`() {
    val overrideDir = Files.createTempDirectory("cap4k-override-helper-type")
    Files.createDirectories(overrideDir.resolve("design"))
    overrideDir.resolve("design/query.kt.peb").writeText(
        """
        package {{ packageName }}
        {{ type(field) | raw }}
        {{ type("String") }}
        """.trimIndent()
    )

    val rendered = rendererFor(overrideDir).render(
        planItems = listOf(
            ArtifactPlanItem(
                generatorId = "design",
                moduleRole = "application",
                templateId = "design/query.kt.peb",
                outputPath = "demo-application/src/main/kotlin/com/acme/demo/application/queries/FindOrderQry.kt",
                context = mapOf(
                    "packageName" to "com.acme.demo.application.queries",
                    "field" to mapOf("renderedType" to "List<com.foo.Status?>")
                ),
                conflictPolicy = ConflictPolicy.SKIP
            )
        ),
        config = defaultProjectConfig(overrideDir)
    )

    val content = rendered.single().content
    assertTrue(content.contains("List<com.foo.Status?>"))
    assertTrue(content.contains("String"))
}

@Test
fun `imports helper preserves order and removes duplicates`() {
    val overrideDir = Files.createTempDirectory("cap4k-override-helper-imports")
    Files.createDirectories(overrideDir.resolve("design"))
    overrideDir.resolve("design/query.kt.peb").writeText(
        """
        {% for import in imports(imports) %}
        import {{ import }}
        {% endfor %}
        """.trimIndent()
    )

    val rendered = rendererFor(overrideDir).render(
        planItems = listOf(
            ArtifactPlanItem(
                generatorId = "design",
                moduleRole = "application",
                templateId = "design/query.kt.peb",
                outputPath = "demo-application/src/main/kotlin/com/acme/demo/application/queries/FindOrderQry.kt",
                context = mapOf(
                    "imports" to listOf(
                        "java.time.LocalDateTime",
                        "java.util.UUID",
                        "java.time.LocalDateTime"
                    )
                ),
                conflictPolicy = ConflictPolicy.SKIP
            )
        ),
        config = defaultProjectConfig(overrideDir)
    )

    assertEquals(
        """
        import java.time.LocalDateTime
        import java.util.UUID
        """.trimIndent(),
        rendered.single().content.trim()
    )
}

@Test
fun `type helper fails fast on unsupported input`() {
    val overrideDir = Files.createTempDirectory("cap4k-override-helper-type-invalid")
    Files.createDirectories(overrideDir.resolve("design"))
    overrideDir.resolve("design/query.kt.peb").writeText("""{{ type(badValue) }}""")

    val ex = assertThrows<IllegalArgumentException> {
        rendererFor(overrideDir).render(
            planItems = listOf(
                ArtifactPlanItem(
                    generatorId = "design",
                    moduleRole = "application",
                    templateId = "design/query.kt.peb",
                    outputPath = "demo.kt",
                    context = mapOf("badValue" to mapOf("name" to "missingRenderedType")),
                    conflictPolicy = ConflictPolicy.SKIP
                )
            ),
            config = defaultProjectConfig(overrideDir)
        )
    }

    assertTrue(ex.message!!.contains("type()"))
}
```

- [ ] **Step 2: Run renderer tests to verify they fail**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-renderer-pebble:test --tests "com.only4.cap4k.plugin.pipeline.renderer.pebble.PebbleArtifactRendererTest" --rerun-tasks
```

Expected: FAIL because `PipelinePebbleExtension` only exposes the `json` filter and Pebble cannot resolve `type()` / `imports()`.

- [ ] **Step 3: Implement `type()` and `imports()` as thin Pebble functions**

Move the extension out of `PebbleArtifactRenderer.kt` into a dedicated file and register helper functions through `getFunctions()`.

`PipelinePebbleExtension.kt`

```kotlin
package com.only4.cap4k.plugin.pipeline.renderer.pebble

import com.google.gson.Gson
import io.pebbletemplates.pebble.extension.AbstractExtension
import io.pebbletemplates.pebble.extension.Filter
import io.pebbletemplates.pebble.extension.Function
import io.pebbletemplates.pebble.template.EvaluationContext
import io.pebbletemplates.pebble.template.PebbleTemplate
import kotlin.reflect.full.memberProperties

internal class PipelinePebbleExtension : AbstractExtension() {
    override fun getFilters(): Map<String, Filter> = mapOf(
        "json" to JsonFilter(),
    )

    override fun getFunctions(): Map<String, Function> = mapOf(
        "type" to TypeFunction(),
        "imports" to ImportsFunction(),
    )
}

private class TypeFunction : Function {
    override fun getArgumentNames(): List<String> = listOf("value")

    override fun execute(
        args: Map<String, Any>,
        self: PebbleTemplate,
        context: EvaluationContext,
        lineNumber: Int,
    ): Any {
        val value = args["value"] ?: error("type() requires a non-null value.")
        return extractRenderedType(value)
            ?: error("type() requires a String or a field-like value exposing renderedType.")
    }

    private fun extractRenderedType(value: Any): String? = when (value) {
        is String -> value
        is Map<*, *> -> value["renderedType"] as? String
        else -> value::class.memberProperties
            .firstOrNull { it.name == "renderedType" }
            ?.getter
            ?.call(value) as? String
    }
}

private class ImportsFunction : Function {
    override fun getArgumentNames(): List<String> = listOf("value")

    override fun execute(
        args: Map<String, Any>,
        self: PebbleTemplate,
        context: EvaluationContext,
        lineNumber: Int,
    ): Any {
        val value = args["value"] ?: return emptyList<String>()
        return normalizeImports(extractImports(value))
    }

    private fun extractImports(value: Any): List<String> = when (value) {
        is Iterable<*> -> value.map { it as? String ?: error("imports() only accepts String entries.") }
        is Map<*, *> -> extractImports(value["imports"] ?: emptyList<String>())
        else -> error("imports() requires a List<String> or a map exposing imports.")
    }

    private fun normalizeImports(imports: List<String>): List<String> =
        LinkedHashSet(imports.map { it.trim() }.filter { it.isNotEmpty() }).toList()
}

private class JsonFilter(
    private val gson: Gson = Gson(),
) : Filter {
    override fun getArgumentNames(): List<String> = emptyList()

    override fun apply(
        input: Any?,
        args: MutableMap<String, Any>?,
        self: PebbleTemplate?,
        context: EvaluationContext?,
        lineNumber: Int,
    ): Any = gson.toJson(input)
}
```

Trim `PebbleArtifactRenderer.kt` back to engine construction:

```kotlin
private val engine = PebbleEngine.Builder()
    .loader(StringLoader())
    .extension(PipelinePebbleExtension())
    .build()
```

- [ ] **Step 4: Run renderer tests to verify helper behavior passes**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-renderer-pebble:test --tests "com.only4.cap4k.plugin.pipeline.renderer.pebble.PebbleArtifactRendererTest" --rerun-tasks
```

Expected: PASS, including new helper-behavior and misuse tests.

- [ ] **Step 5: Commit renderer helper foundation**

Run:

```powershell
git add cap4k-plugin-pipeline-renderer-pebble/src/main/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PebbleArtifactRenderer.kt cap4k-plugin-pipeline-renderer-pebble/src/main/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PipelinePebbleExtension.kt cap4k-plugin-pipeline-renderer-pebble/src/test/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PebbleArtifactRendererTest.kt
git commit -m "feat: add pebble design template helpers"
```

### Task 2: Migrate Default Design Templates To Helper API

**Files:**
- Modify: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/design/command.kt.peb`
- Modify: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/design/query.kt.peb`
- Modify: `cap4k-plugin-pipeline-renderer-pebble/src/test/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PebbleArtifactRendererTest.kt`

- [ ] **Step 1: Add failing regression assertions around helper-backed default templates**

Extend the existing rich-template regression in `PebbleArtifactRendererTest.kt` so it proves the migrated default templates still emit the same imports and type strings.

```kotlin
assertTrue(content.contains("import java.time.LocalDateTime"))
assertTrue(content.contains("import java.util.UUID"))
assertTrue(content.contains("val address: Address?"))
assertTrue(content.contains("val requestStatus: com.foo.Status"))
assertTrue(content.contains("val responseStatus: com.bar.Status"))
assertFalse(content.contains("val address: Address??"))
```

Also add one assertion that relies on the helper path rather than raw field property access:

```kotlin
assertFalse(content.contains("renderedType"))
```

- [ ] **Step 2: Run the focused renderer regression test**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-renderer-pebble:test --tests "com.only4.cap4k.plugin.pipeline.renderer.pebble.PebbleArtifactRendererTest.falls back to preset design templates and renders imports rendered types and nested types" --rerun-tasks
```

Expected: FAIL after test tightening, or PASS before migration but with no helper coverage yet. Either way, continue immediately to template migration and use this test as the regression guard.

- [ ] **Step 3: Replace raw property access in default design templates with helper calls**

Update `command.kt.peb` and `query.kt.peb` so only helper-backed rendering remains.

`command.kt.peb`

```pebble
package {{ packageName }}
{% for import in imports(imports) %}
import {{ import }}
{% endfor %}

object {{ typeName }} {
{% if requestFields.size > 0 %}
{% if requestNestedTypes.size > 0 %}
    data class Request(
{% for field in requestFields %}
        val {{ field.name }}: {{ type(field) | raw }}{% if not loop.last %},{% endif %}
{% endfor %}
    ) {
{% for nestedType in requestNestedTypes %}

        data class {{ nestedType.name }}(
{% for field in nestedType.fields %}
            val {{ field.name }}: {{ type(field) | raw }}{% if not loop.last %},{% endif %}
{% endfor %}
        )
{% endfor %}
    }
{% else %}
    data class Request(
{% for field in requestFields %}
        val {{ field.name }}: {{ type(field) | raw }}{% if not loop.last %},{% endif %}
{% endfor %}
    )
{% endif %}
{% else %}
    data object Request
{% endif %}

{% if responseFields.size > 0 %}
{% if responseNestedTypes.size > 0 %}
    data class Response(
{% for field in responseFields %}
        val {{ field.name }}: {{ type(field) | raw }}{% if not loop.last %},{% endif %}
{% endfor %}
    ) {
{% for nestedType in responseNestedTypes %}

        data class {{ nestedType.name }}(
{% for field in nestedType.fields %}
            val {{ field.name }}: {{ type(field) | raw }}{% if not loop.last %},{% endif %}
{% endfor %}
        )
{% endfor %}
    }
{% else %}
    data class Response(
{% for field in responseFields %}
        val {{ field.name }}: {{ type(field) | raw }}{% if not loop.last %},{% endif %}
{% endfor %}
    )
{% endif %}
{% else %}
    data object Response
{% endif %}
}
```

Make the same substitution in `query.kt.peb`.

- [ ] **Step 4: Re-run renderer tests for full default-template coverage**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-renderer-pebble:test --rerun-tasks
```

Expected: PASS, with helper tests and default-template regression tests all green.

- [ ] **Step 5: Commit the template migration**

Run:

```powershell
git add cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/design/command.kt.peb cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/design/query.kt.peb cap4k-plugin-pipeline-renderer-pebble/src/test/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PebbleArtifactRendererTest.kt
git commit -m "refactor: migrate design templates to pebble helpers"
```

### Task 3: Add Functional Coverage For Helper-Backed Templates

**Files:**
- Modify: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginFunctionalTest.kt`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-sample/build.gradle.kts`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-sample/codegen/templates/design/command.kt.peb`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-sample/codegen/templates/design/query.kt.peb`

- [ ] **Step 1: Write a failing functional test for override templates using helper API**

Add a new functional test that turns on repository override templates and asserts generated output still includes the helper-resolved imports and rendered types.

```kotlin
@OptIn(ExperimentalPathApi::class)
@Test
fun `cap4kGenerate supports helper based override design templates`() {
    val projectDir = Files.createTempDirectory("pipeline-functional-design-helper-override")
    copyFixture(projectDir, "design-sample")

    val buildFile = projectDir.resolve("build.gradle.kts")
    buildFile.writeText(
        buildFile.readText().replace(
            "    }\n}",
            """
                }
                templates {
                    overrideDirs.from("codegen/templates")
                }
            }
            """.trimIndent()
        )
    )

    val result = GradleRunner.create()
        .withProjectDir(projectDir.toFile())
        .withPluginClasspath()
        .withArguments("cap4kGenerate")
        .build()

    val commandFile = projectDir.resolve(
        "demo-application/src/main/kotlin/com/acme/demo/application/commands/order/submit/SubmitOrderCmd.kt"
    )

    assertTrue(result.output.contains("BUILD SUCCESSFUL"))
    assertTrue(commandFile.readText().contains("object SubmitOrderCmd"))
    assertTrue(commandFile.readText().contains("import java.time.LocalDateTime"))
    assertTrue(commandFile.readText().contains("val requestStatus: com.foo.Status"))
}
```

- [ ] **Step 2: Run the new functional test to verify override templates fail before fixture migration**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-gradle:test --tests "com.only4.cap4k.plugin.pipeline.gradle.PipelinePluginFunctionalTest.cap4kGenerate supports helper based override design templates" --rerun-tasks
```

Expected: FAIL because the fixture override templates still use the old trivial `class {{ typeName }}` form and do not exercise helper-backed shape.

- [ ] **Step 3: Migrate the functional fixture override templates to helper API**

Enable the override template directory in the fixture and replace the trivial override files with helper-backed templates matching the default design layout.

`build.gradle.kts`

```kotlin
    templates {
        overrideDirs.from("codegen/templates")
    }
```

`codegen/templates/design/command.kt.peb`

```pebble
package {{ packageName }}
{% for import in imports(imports) %}
import {{ import }}
{% endfor %}

object {{ typeName }} {
    data class Request(
{% for field in requestFields %}
        val {{ field.name }}: {{ type(field) | raw }}{% if not loop.last %},{% endif %}
{% endfor %}
    )

    data class Response(
{% for field in responseFields %}
        val {{ field.name }}: {{ type(field) | raw }}{% if not loop.last %},{% endif %}
{% endfor %}
    )
}
```

Apply the same helper style to `codegen/templates/design/query.kt.peb`.

- [ ] **Step 4: Run full functional verification**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-gradle:test --tests "com.only4.cap4k.plugin.pipeline.gradle.PipelinePluginFunctionalTest.cap4kGenerate renders command and query files from repository config" --tests "com.only4.cap4k.plugin.pipeline.gradle.PipelinePluginFunctionalTest.cap4kGenerate supports helper based override design templates" --rerun-tasks
```

Then run the end-to-end targeted suite:

```powershell
./gradlew :cap4k-plugin-pipeline-generator-design:test :cap4k-plugin-pipeline-renderer-pebble:test :cap4k-plugin-pipeline-gradle:test --rerun-tasks
```

Expected: PASS for both commands.

- [ ] **Step 5: Commit helper functional coverage**

Run:

```powershell
git add cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginFunctionalTest.kt cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-sample/build.gradle.kts cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-sample/codegen/templates/design/command.kt.peb cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-sample/codegen/templates/design/query.kt.peb
git commit -m "test: cover helper based design template overrides"
```

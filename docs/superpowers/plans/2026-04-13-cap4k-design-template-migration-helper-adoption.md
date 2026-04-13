# Cap4k Design Template Migration / Helper Adoption Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Lock the pipeline design-template surface into a stable helper-first migration contract by upgrading the override fixture, adding renderer-level regression coverage, and verifying that `type()`, `imports()`, `use()`, and `defaultValue` compose correctly end to end.

**Architecture:** The implementation stays in test resources and test code. The default preset templates already match the intended helper-first contract, so this slice mostly hardens that contract through migration-style override templates and regression tests instead of changing generator or renderer semantics. Functional coverage proves the fixture a real project would migrate toward, while renderer coverage isolates the helper contract from Gradle orchestration.

**Tech Stack:** Kotlin, JUnit 5, Gradle TestKit, Pebble templates, existing `cap4k` pipeline renderer/Gradle plugin test fixtures

---

## File Structure

- Modify: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-sample/codegen/templates/design/command.kt.peb`
  Responsibility: migration-friendly override command template using `use()`, `imports()`, `type()`, and `field.defaultValue`

- Modify: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-sample/codegen/templates/design/query.kt.peb`
  Responsibility: migration-friendly override query template using the same helper-first contract as the command template

- Modify: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginFunctionalTest.kt`
  Responsibility: rename and expand the override-template functional test so it asserts the migration contract rather than only basic helper availability

- Modify: `cap4k-plugin-pipeline-renderer-pebble/src/test/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PebbleArtifactRendererTest.kt`
  Responsibility: add a focused renderer regression test for migration-style template composition without relying on Gradle TestKit

- Verify only: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/design/command.kt.peb`
  Responsibility: reference template that already models the intended helper-first contract

- Verify only: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/design/query.kt.peb`
  Responsibility: reference template that already models the intended helper-first contract

### Task 1: Upgrade the Functional Override Fixture to the Migration Contract

**Files:**
- Modify: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-sample/codegen/templates/design/command.kt.peb`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-sample/codegen/templates/design/query.kt.peb`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginFunctionalTest.kt`
- Test: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginFunctionalTest.kt`

- [x] **Step 1: Expand the functional test so it fails on the current helper-only fixture**

```kotlin
@OptIn(ExperimentalPathApi::class)
@Test
fun `cap4kGenerate supports migration friendly override design templates`() {
    val projectDir = Files.createTempDirectory("pipeline-functional-design-migration-override")
    copyFixture(projectDir)

    val buildFile = projectDir.resolve("build.gradle.kts")
    val buildFileContent = buildFile.readText().replace("\r\n", "\n")
    buildFile.writeText(
        buildFileContent.replace(
            """
            |        design {
            |            enabled.set(true)
            |        }
            """.trimMargin(),
            """
            |        design {
            |            enabled.set(true)
            |        }
            |        templates {
            |            overrideDirs.from("codegen/templates")
            |        }
            """.trimMargin()
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
    val queryFile = projectDir.resolve(
        "demo-application/src/main/kotlin/com/acme/demo/application/queries/order/read/FindOrderQry.kt"
    )
    val commandContent = commandFile.readText()
    val queryContent = queryFile.readText()

    assertTrue(result.output.contains("BUILD SUCCESSFUL"))
    assertTrue(commandContent.contains("// override: migration-friendly design template"))
    assertTrue(queryContent.contains("// override: migration-friendly design template"))
    assertTrue(commandContent.contains("import java.io.Serializable"))
    assertTrue(queryContent.contains("import java.io.Serializable"))
    assertTrue(commandContent.contains("object SubmitOrderCmd : Serializable"))
    assertTrue(queryContent.contains("object FindOrderQry : Serializable"))
    assertTrue(commandContent.contains("val orderId: Long = 1L"))
    assertTrue(commandContent.contains("val title: String = \"demo\""))
    assertTrue(commandContent.contains("val createdAt: LocalDateTime = java.time.LocalDateTime.MIN"))
    assertTrue(queryContent.contains("val lookupId: UUID"))
    assertTrue(queryContent.contains("val responseStatus: com.bar.Status"))
}
```

- [x] **Step 2: Run the functional test and confirm it fails before the fixture is upgraded**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-gradle:test --tests "*cap4kGenerate supports migration friendly override design templates" --rerun-tasks
```

Expected: FAIL with one or more missing assertions for `java.io.Serializable` imports or missing `= ...` default-value output in the generated override files.

- [x] **Step 3: Upgrade the override command template to the migration-friendly helper contract**

Replace the existing command override template with:

```pebble
{{ use("java.io.Serializable") -}}
package {{ packageName }}
// override: migration-friendly design template
{% for import in imports(imports) %}
import {{ import }}
{% endfor %}

object {{ typeName }} : Serializable {
{% if requestFields|length > 0 %}
    data class Request(
{% for field in requestFields %}
        val {{ field.name }}: {{ type(field) | raw }}{% if field.defaultValue %} = {{ field.defaultValue | raw }}{% endif %}{% if not loop.last %},{% endif %}
{% endfor %}
    ) : Serializable {
{% else %}
    class Request : Serializable {
{% endif %}
{% for nestedType in requestNestedTypes %}

        data class {{ nestedType.name }}(
{% for field in nestedType.fields %}
            val {{ field.name }}: {{ type(field) | raw }}{% if field.defaultValue %} = {{ field.defaultValue | raw }}{% endif %}{% if not loop.last %},{% endif %}
{% endfor %}
        ) : Serializable
{% endfor %}
    }

{% if responseFields|length > 0 %}
    data class Response(
{% for field in responseFields %}
        val {{ field.name }}: {{ type(field) | raw }}{% if field.defaultValue %} = {{ field.defaultValue | raw }}{% endif %}{% if not loop.last %},{% endif %}
{% endfor %}
    ) : Serializable {
{% else %}
    class Response : Serializable {
{% endif %}
{% for nestedType in responseNestedTypes %}

        data class {{ nestedType.name }}(
{% for field in nestedType.fields %}
            val {{ field.name }}: {{ type(field) | raw }}{% if field.defaultValue %} = {{ field.defaultValue | raw }}{% endif %}{% if not loop.last %},{% endif %}
{% endfor %}
        ) : Serializable
{% endfor %}
    }
}
```

- [x] **Step 4: Apply the same migration contract to the override query template**

Replace the existing query override template with:

```pebble
{{ use("java.io.Serializable") -}}
package {{ packageName }}
// override: migration-friendly design template
{% for import in imports(imports) %}
import {{ import }}
{% endfor %}

object {{ typeName }} : Serializable {
{% if requestFields|length > 0 %}
    data class Request(
{% for field in requestFields %}
        val {{ field.name }}: {{ type(field) | raw }}{% if field.defaultValue %} = {{ field.defaultValue | raw }}{% endif %}{% if not loop.last %},{% endif %}
{% endfor %}
    ) : Serializable {
{% else %}
    class Request : Serializable {
{% endif %}
{% for nestedType in requestNestedTypes %}

        data class {{ nestedType.name }}(
{% for field in nestedType.fields %}
            val {{ field.name }}: {{ type(field) | raw }}{% if field.defaultValue %} = {{ field.defaultValue | raw }}{% endif %}{% if not loop.last %},{% endif %}
{% endfor %}
        ) : Serializable
{% endfor %}
    }

{% if responseFields|length > 0 %}
    data class Response(
{% for field in responseFields %}
        val {{ field.name }}: {{ type(field) | raw }}{% if field.defaultValue %} = {{ field.defaultValue | raw }}{% endif %}{% if not loop.last %},{% endif %}
{% endfor %}
    ) : Serializable {
{% else %}
    class Response : Serializable {
{% endif %}
{% for nestedType in responseNestedTypes %}

        data class {{ nestedType.name }}(
{% for field in nestedType.fields %}
            val {{ field.name }}: {{ type(field) | raw }}{% if field.defaultValue %} = {{ field.defaultValue | raw }}{% endif %}{% if not loop.last %},{% endif %}
{% endfor %}
        ) : Serializable
{% endfor %}
    }
}
```

- [x] **Step 5: Re-run the functional test and confirm the migration fixture passes**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-gradle:test --tests "*cap4kGenerate supports migration friendly override design templates" --rerun-tasks
```

Expected: PASS with generated override files containing `import java.io.Serializable`, marker-interface usage on the generated types, preserved collision-safe fully qualified status types, and rendered default values.

- [x] **Step 6: Commit the fixture and functional test upgrade**

```bash
git add cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-sample/codegen/templates/design/command.kt.peb cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-sample/codegen/templates/design/query.kt.peb cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginFunctionalTest.kt
git commit -m "test: upgrade design migration override fixture"
```

### Task 2: Add Focused Renderer Regression Coverage for the Migration Contract

**Files:**
- Modify: `cap4k-plugin-pipeline-renderer-pebble/src/test/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PebbleArtifactRendererTest.kt`
- Test: `cap4k-plugin-pipeline-renderer-pebble/src/test/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PebbleArtifactRendererTest.kt`

- [x] **Step 1: Add a renderer-level regression test that composes `use()`, `imports()`, `type()`, and `defaultValue` in one override template**

Add a test like:

```kotlin
@Test
fun `migration style override template composes helper contract`() {
    val overrideDir = Files.createTempDirectory("cap4k-override-migration-contract")
    val overrideDesignDir = Files.createDirectories(overrideDir.resolve("design"))
    overrideDesignDir.resolve("command.kt.peb").writeText(
        """
        {{ use("java.io.Serializable") -}}
        package {{ packageName }}
        {% for import in imports(imports) %}
        import {{ import }}
        {% endfor %}

        object {{ typeName }} : Serializable {
            data class Request(
        {% for field in requestFields %}
                val {{ field.name }}: {{ type(field) | raw }}{% if field.defaultValue %} = {{ field.defaultValue | raw }}{% endif %}{% if not loop.last %},{% endif %}
        {% endfor %}
            ) : Serializable

            data class Response(
        {% for field in responseFields %}
                val {{ field.name }}: {{ type(field) | raw }}{% if field.defaultValue %} = {{ field.defaultValue | raw }}{% endif %}{% if not loop.last %},{% endif %}
        {% endfor %}
            ) : Serializable
        }
        """.trimIndent()
    )

    val rendered = PebbleArtifactRenderer(
        templateResolver = PresetTemplateResolver(
            preset = "ddd-default",
            overrideDirs = listOf(overrideDir.toString())
        )
    ).render(
        planItems = listOf(
            ArtifactPlanItem(
                generatorId = "design",
                moduleRole = "application",
                templateId = "design/command.kt.peb",
                outputPath = "demo-application/src/main/kotlin/com/acme/demo/application/commands/order/submit/SubmitOrderCmd.kt",
                context = mapOf(
                    "packageName" to "com.acme.demo.application.commands.order.submit",
                    "typeName" to "SubmitOrderCmd",
                    "imports" to listOf("java.time.LocalDateTime", "java.util.UUID"),
                    "requestFields" to listOf(
                        mapOf("name" to "orderId", "renderedType" to "Long", "nullable" to false, "defaultValue" to "1L"),
                        mapOf("name" to "createdAt", "renderedType" to "LocalDateTime", "nullable" to false, "defaultValue" to "java.time.LocalDateTime.MIN"),
                        mapOf("name" to "trackingId", "renderedType" to "UUID", "nullable" to false, "defaultValue" to null),
                        mapOf("name" to "requestStatus", "renderedType" to "com.foo.Status", "nullable" to false, "defaultValue" to null),
                    ),
                    "responseFields" to listOf(
                        mapOf("name" to "accepted", "renderedType" to "Boolean", "nullable" to false, "defaultValue" to "true"),
                        mapOf("name" to "responseStatus", "renderedType" to "com.bar.Status", "nullable" to false, "defaultValue" to null),
                    ),
                ),
                conflictPolicy = ConflictPolicy.SKIP
            )
        ),
        config = ProjectConfig(
            basePackage = "com.acme.demo",
            layout = ProjectLayout.MULTI_MODULE,
            modules = emptyMap(),
            sources = emptyMap(),
            generators = emptyMap(),
            templates = TemplateConfig(
                preset = "ddd-default",
                overrideDirs = listOf(overrideDir.toString()),
                conflictPolicy = ConflictPolicy.SKIP
            )
        )
    )

    val content = rendered.single().content
    assertTrue(content.contains("import java.io.Serializable"))
    assertTrue(content.contains("import java.time.LocalDateTime"))
    assertTrue(content.contains("import java.util.UUID"))
    assertTrue(content.contains("object SubmitOrderCmd : Serializable"))
    assertTrue(content.contains("val orderId: Long = 1L"))
    assertTrue(content.contains("val createdAt: LocalDateTime = java.time.LocalDateTime.MIN"))
    assertTrue(content.contains("val trackingId: UUID"))
    assertTrue(content.contains("val requestStatus: com.foo.Status"))
    assertTrue(content.contains("val responseStatus: com.bar.Status"))
}
```

- [x] **Step 2: Run the focused renderer test to lock in the contract without Gradle TestKit**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-renderer-pebble:test --tests "*migration style override template composes helper contract" --rerun-tasks
```

Expected: PASS, proving the renderer already supports the migration contract and the new regression test protects it from future drift.

- [x] **Step 3: Commit the renderer regression coverage**

```bash
git add cap4k-plugin-pipeline-renderer-pebble/src/test/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PebbleArtifactRendererTest.kt
git commit -m "test: lock migration design template contract"
```

### Task 3: Run the Regression Sweep for the Slice

**Files:**
- Test: `cap4k-plugin-pipeline-renderer-pebble/src/test/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PebbleArtifactRendererTest.kt`
- Test: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginFunctionalTest.kt`

- [x] **Step 1: Run the renderer module test suite**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-renderer-pebble:test --rerun-tasks
```

Expected: PASS.

- [x] **Step 2: Run the Gradle functional test suite**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-gradle:test --rerun-tasks
```

Expected: PASS.

- [x] **Step 3: Run the generator-design regression suite that still feeds the functional path**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-generator-design:test --rerun-tasks
```

Expected: PASS.

- [x] **Step 4: Capture the final diff for review**

Run:

```bash
git status --short
git diff --stat
```

Expected: only the migration-fixture templates, functional test, and renderer regression test remain in the final diff.

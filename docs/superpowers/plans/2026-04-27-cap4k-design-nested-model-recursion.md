# Cap4k Design Nested Model Recursion Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add design generator support for multi-level nested payloads, `self` root recursion, and explicit nested type recursion while keeping canonical `FieldModel` flat.

**Architecture:** Keep source and canonical layers unchanged. Replace the current one-level grouping inside `DesignPayloadRenderModelFactory` with an internal namespace path tree that emits the existing render model shape. Validate behavior through generator unit tests, Gradle compile-level functional tests, then update `only-danmuku-zero` normalization.

**Tech Stack:** Kotlin, Gradle TestKit, JUnit 5, Pebble templates, PowerShell dogfood scripts.

---

## File Map

- Modify `cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignPayloadRenderModelFactory.kt`
  - Owns design payload namespace shaping, nested path parsing, `self` resolution, local nested type resolution, and render model creation.
- Modify `cap4k-plugin-pipeline-generator-design/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignApiPayloadArtifactPlannerTest.kt`
  - Adds focused unit tests for multi-level nested payloads, `self`, local nested type recursion, and invalid path shapes.
- Modify `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginCompileFunctionalTest.kt`
  - Adds compile-level verification that generated Kotlin is valid.
- Create `cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-nested-recursion-compile-sample/**`
  - Minimal Gradle fixture for compile-level nested recursion generation.
- Modify `../only-danmuku-zero/docs/dogfood/normalize-design-input.ps1`
  - Stops skipping multi-level nested paths and normalizes old root recursion references to `self`.
- Modify `../only-danmuku-zero/codegen/design/skipped-design.json`
  - Expected to shrink after normalization rerun.
- Modify `../only-danmuku-zero/codegen/design/design.json`
  - Expected to include entries previously skipped only for nested model support.
- Modify `../only-danmuku-zero/docs/dogfood/cap4k-pipeline-issue-backlog.md`
  - Updates nested model issue status after verification.

## Task 1: Add Unit Tests For New Nested Semantics

**Files:**
- Modify: `cap4k-plugin-pipeline-generator-design/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignApiPayloadArtifactPlannerTest.kt`

- [ ] **Step 1: Add failing unit test for multi-level request nested fields**

Append this test near the existing nested payload test:

```kotlin
@Test
fun `api payload planner supports multi-level nested request hierarchy`() {
    val planner = DesignApiPayloadArtifactPlanner()

    val items = planner.plan(
        config = projectConfig(modules = mapOf("adapter" to "demo-adapter")),
        model = CanonicalModel(
            apiPayloads = listOf(
                ApiPayloadModel(
                    packageName = "video",
                    typeName = "SyncVideoPostProcessStatus",
                    description = "sync video post process status",
                    requestFields = listOf(
                        FieldModel("fileList", "List<FileItem>"),
                        FieldModel("fileList[].fileIndex", "Int"),
                        FieldModel("fileList[].variants", "List<VariantItem>"),
                        FieldModel("fileList[].variants[].quality", "String", defaultValue = ""),
                        FieldModel("fileList[].variants[].width", "Int", defaultValue = "0"),
                    ),
                ),
            ),
        ),
    )

    val payload = items.single()
    assertEquals(
        listOf(DesignRenderFieldModel(name = "fileList", renderedType = "List<FileItem>")),
        payload.context["requestFields"],
    )
    assertEquals(
        listOf(
            DesignRenderNestedTypeModel(
                name = "FileItem",
                fields = listOf(
                    DesignRenderFieldModel(name = "fileIndex", renderedType = "Int"),
                    DesignRenderFieldModel(name = "variants", renderedType = "List<VariantItem>"),
                ),
            ),
            DesignRenderNestedTypeModel(
                name = "VariantItem",
                fields = listOf(
                    DesignRenderFieldModel(name = "quality", renderedType = "String", defaultValue = "\"\""),
                    DesignRenderFieldModel(name = "width", renderedType = "Int", defaultValue = "0"),
                ),
            ),
        ),
        payload.context["requestNestedTypes"],
    )
}
```

- [ ] **Step 2: Run the new test and verify it fails**

Run:

```powershell
.\gradlew.bat --no-configuration-cache --no-build-cache :cap4k-plugin-pipeline-generator-design:test --tests "com.only4.cap4k.plugin.pipeline.generator.design.DesignApiPayloadArtifactPlannerTest.api payload planner supports multi-level nested request hierarchy"
```

Expected: FAIL with the current one-level error:

```text
nested field paths must have exactly one level in request namespace: fileList[].variants[].quality
```

- [ ] **Step 3: Add failing unit test for `self` response recursion**

Add:

```kotlin
@Test
fun `api payload planner resolves self as response root type`() {
    val planner = DesignApiPayloadArtifactPlanner()

    val items = planner.plan(
        config = projectConfig(modules = mapOf("adapter" to "demo-adapter")),
        model = CanonicalModel(
            apiPayloads = listOf(
                ApiPayloadModel(
                    packageName = "category",
                    typeName = "GetCategoryTree",
                    description = "get category tree",
                    responseFields = listOf(
                        FieldModel("categoryId", "Long"),
                        FieldModel("children", "List<self>", nullable = true),
                    ),
                ),
            ),
        ),
    )

    val payload = items.single()
    assertEquals(
        listOf(
            DesignRenderFieldModel(name = "categoryId", renderedType = "Long"),
            DesignRenderFieldModel(name = "children", renderedType = "List<Response>?", nullable = true),
        ),
        payload.context["responseFields"],
    )
}
```

- [ ] **Step 4: Run the `self` test and verify it fails**

Run:

```powershell
.\gradlew.bat --no-configuration-cache --no-build-cache :cap4k-plugin-pipeline-generator-design:test --tests "com.only4.cap4k.plugin.pipeline.generator.design.DesignApiPayloadArtifactPlannerTest.api payload planner resolves self as response root type"
```

Expected: FAIL with unknown short type `self`.

- [ ] **Step 5: Add failing unit test for explicit nested type recursion**

Add:

```kotlin
@Test
fun `api payload planner supports explicit nested type recursion`() {
    val planner = DesignApiPayloadArtifactPlanner()

    val items = planner.plan(
        config = projectConfig(modules = mapOf("adapter" to "demo-adapter")),
        model = CanonicalModel(
            apiPayloads = listOf(
                ApiPayloadModel(
                    packageName = "video",
                    typeName = "RecursiveVariantPayload",
                    description = "recursive variant payload",
                    requestFields = listOf(
                        FieldModel("variants", "List<VariantItem>"),
                        FieldModel("variants[].quality", "String"),
                        FieldModel("variants[].children", "List<VariantItem>"),
                    ),
                ),
            ),
        ),
    )

    val payload = items.single()
    assertEquals(
        listOf(
            DesignRenderNestedTypeModel(
                name = "VariantItem",
                fields = listOf(
                    DesignRenderFieldModel(name = "quality", renderedType = "String"),
                    DesignRenderFieldModel(name = "children", renderedType = "List<VariantItem>"),
                ),
            ),
        ),
        payload.context["requestNestedTypes"],
    )
}
```

- [ ] **Step 6: Run the explicit nested recursion test and verify it fails**

Run:

```powershell
.\gradlew.bat --no-configuration-cache --no-build-cache :cap4k-plugin-pipeline-generator-design:test --tests "com.only4.cap4k.plugin.pipeline.generator.design.DesignApiPayloadArtifactPlannerTest.api payload planner supports explicit nested type recursion"
```

Expected: FAIL if `variants[]` is not parsed or if local nested type references are not resolved correctly.

- [ ] **Step 7: Add failing unit test for namespace isolation and local `Item`**

Add:

```kotlin
@Test
fun `api payload planner keeps request and response nested item namespaces isolated`() {
    val planner = DesignApiPayloadArtifactPlanner()

    val items = planner.plan(
        config = projectConfig(modules = mapOf("adapter" to "demo-adapter")),
        model = CanonicalModel(
            apiPayloads = listOf(
                ApiPayloadModel(
                    packageName = "message",
                    typeName = "MessageGroupPayload",
                    description = "message group payload",
                    requestFields = listOf(
                        FieldModel("list", "List<Item>"),
                        FieldModel("list[].requestValue", "String"),
                    ),
                    responseFields = listOf(
                        FieldModel("list", "List<Item>"),
                        FieldModel("list[].messageType", "Int"),
                        FieldModel("list[].count", "Int"),
                    ),
                ),
            ),
        ),
    )

    val payload = items.single()
    assertEquals(
        listOf(
            DesignRenderNestedTypeModel(
                name = "Item",
                fields = listOf(DesignRenderFieldModel(name = "requestValue", renderedType = "String")),
            ),
        ),
        payload.context["requestNestedTypes"],
    )
    assertEquals(
        listOf(
            DesignRenderNestedTypeModel(
                name = "Item",
                fields = listOf(
                    DesignRenderFieldModel(name = "messageType", renderedType = "Int"),
                    DesignRenderFieldModel(name = "count", renderedType = "Int"),
                ),
            ),
        ),
        payload.context["responseNestedTypes"],
    )
}
```

- [ ] **Step 8: Run all new unit tests and verify they fail before implementation**

Run:

```powershell
.\gradlew.bat --no-configuration-cache --no-build-cache :cap4k-plugin-pipeline-generator-design:test --tests "com.only4.cap4k.plugin.pipeline.generator.design.DesignApiPayloadArtifactPlannerTest"
```

Expected: existing tests pass; new tests fail for unsupported multi-level path, unsupported `[]`, or unresolved `self`.

- [ ] **Step 9: Commit only the failing tests**

Commit after verifying red:

```powershell
git add cap4k-plugin-pipeline-generator-design/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignApiPayloadArtifactPlannerTest.kt
git commit -m "Test design nested model recursion"
```

## Task 2: Implement Internal Payload Path Tree

**Files:**
- Modify: `cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignPayloadRenderModelFactory.kt`

- [ ] **Step 1: Replace one-level nested grouping with path segment parsing**

In `DesignPayloadRenderModelFactory`, add internal path structures near the current private data classes:

```kotlin
private data class FieldPathSegment(
    val name: String,
    val list: Boolean,
)

private data class PayloadPathNode(
    val name: String,
    val children: LinkedHashMap<String, PayloadPathNode> = linkedMapOf(),
    var explicitField: FieldModel? = null,
    var list: Boolean = false,
)
```

Add parser:

```kotlin
private fun parseFieldPath(path: String): List<FieldPathSegment> {
    val parts = path.trim().split('.')
    require(parts.isNotEmpty() && parts.none { it.isBlank() }) {
        "blank or malformed nested field path: $path"
    }
    return parts.map { rawPart ->
        val list = rawPart.endsWith("[]")
        val name = rawPart.removeSuffix("[]")
        require(name.isNotBlank()) {
            "blank or malformed nested field path: $path"
        }
        FieldPathSegment(name = name, list = list)
    }
}
```

- [ ] **Step 2: Build a tree for each namespace**

Replace the loop inside `buildNamespace()` with tree construction:

```kotlin
val root = PayloadPathNode(name = "__root__")
fields.forEach { field ->
    val segments = parseFieldPath(field.name)
    var current = root
    segments.forEach { segment ->
        val child = current.children.getOrPut(segment.name) { PayloadPathNode(name = segment.name) }
        if (segment.list) {
            child.list = true
        }
        current = child
    }
    if (current.explicitField == null) {
        current.explicitField = field.copy(name = current.name)
    }
    if (isCollectionType(field.type)) {
        current.list = true
    }
}
```

Keep insertion order by using `LinkedHashMap`.

- [ ] **Step 3: Derive nested type names from direct container declarations**

Add helper:

```kotlin
private fun nestedTypeNameFor(node: PayloadPathNode): String {
    val explicitType = node.explicitField?.type?.trim().orEmpty()
    if (explicitType.isNotBlank()) {
        val genericArguments = parseTopLevelGenericArguments(explicitType)
        val candidate = genericArguments.singleOrNull() ?: explicitType
        val simple = candidate.removePrefix("out ")
            .removePrefix("in ")
            .removeSuffix("?")
            .trim()
            .substringAfterLast('.')
        if (simple.isNotBlank()) {
            return simple
        }
    }
    return toNestedTypeName(node.name)
}
```

If a node has children but no direct declaration, throw:

```kotlin
throw IllegalArgumentException(
    "missing compatible direct root field for nested type ${toNestedTypeName(node.name)} in $namespace namespace",
)
```

Use existing error text for one-level compatibility where possible.

- [ ] **Step 4: Emit direct fields and nested types from the tree**

For root direct fields, each `root.children` node becomes one prepared direct field.

For nested types, visit every node with children in deterministic encounter order:

```kotlin
val nestedTypeNames = linkedSetOf<String>()
val nestedTypes = mutableListOf<PreparedNestedTypeModel>()

fun collectNested(node: PayloadPathNode) {
    node.children.values.forEach { child ->
        if (child.children.isNotEmpty()) {
            val nestedTypeName = nestedTypeNameFor(child)
            if (!nestedTypeNames.add(nestedTypeName)) {
                throw IllegalArgumentException("duplicate nested type name in $namespace namespace: $nestedTypeName")
            }
            nestedTypes += PreparedNestedTypeModel(
                name = nestedTypeName,
                fields = child.children.values.map { grandChild ->
                    grandChild.toPreparedNestedField(namespace, nestedTypeNames)
                },
            )
            collectNested(child)
        }
    }
}
```

The implementation may need two passes:

1. first collect all nested type names
2. then prepare fields using the complete nested type name set

This prevents `FileItem` fields from failing when they reference `VariantItem` declared later.

- [ ] **Step 5: Add `self` as namespace root type**

Extend `buildNamespace()` so it knows the namespace root type:

```kotlin
private fun buildNamespace(
    fields: List<FieldModel>,
    namespace: String,
    rootTypeName: String,
): NamespaceModel
```

Call it as:

```kotlin
val requestNamespace = buildNamespace(interaction.requestFields, "request", "Request")
val responseNamespace = buildNamespace(interaction.responseFields, "response", "Response")
```

For domain event fields, do not introduce `self`:

```kotlin
val requestNamespace = buildNamespace(event.fields, "request", rootTypeName = "")
```

When resolving a type token, map token `self` to `rootTypeName`. If `rootTypeName` is blank and `self` is used, throw:

```text
self is not supported in request namespace
```

- [ ] **Step 6: Add local nested type names to type resolution before external symbols**

Keep using `DesignTypeParser`, `DesignTypeResolver`, and `ImportResolver`, but ensure local nested type names are in `innerTypeNames` before validation and rendering.

Expected behavior:

```kotlin
FieldModel("variants[].children", "List<VariantItem>")
```

renders as:

```text
List<VariantItem>
```

and does not add an import for `VariantItem`.

- [ ] **Step 7: Run Task 1 tests and verify they pass**

Run:

```powershell
.\gradlew.bat --no-configuration-cache --no-build-cache :cap4k-plugin-pipeline-generator-design:test --tests "com.only4.cap4k.plugin.pipeline.generator.design.DesignApiPayloadArtifactPlannerTest"
```

Expected: all `DesignApiPayloadArtifactPlannerTest` tests pass.

- [ ] **Step 8: Run full generator-design tests**

Run:

```powershell
.\gradlew.bat --no-configuration-cache --no-build-cache :cap4k-plugin-pipeline-generator-design:test
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 9: Commit implementation**

Commit:

```powershell
git add cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignPayloadRenderModelFactory.kt
git commit -m "Support design nested payload recursion"
```

## Task 3: Add Compile-Level Functional Fixture

**Files:**
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-nested-recursion-compile-sample/settings.gradle.kts`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-nested-recursion-compile-sample/build.gradle.kts`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-nested-recursion-compile-sample/demo-adapter/build.gradle.kts`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-nested-recursion-compile-sample/design/design.json`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginCompileFunctionalTest.kt`

- [ ] **Step 1: Create minimal functional fixture settings**

Create `settings.gradle.kts`:

```kotlin
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
    }
}

rootProject.name = "design-nested-recursion-compile-sample"
include("demo-adapter")
```

- [ ] **Step 2: Create root build file**

Create `build.gradle.kts`:

```kotlin
plugins {
    id("com.only4.cap4k.pipeline")
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
    templates {
        conflictPolicy.set("OVERWRITE")
    }
}
```

- [ ] **Step 3: Create adapter build file**

Create `demo-adapter/build.gradle.kts`:

```kotlin
plugins {
    kotlin("jvm") version "2.1.20"
}

repositories {
    mavenCentral()
}
```

- [ ] **Step 4: Create design fixture**

Create `design/design.json`:

```json
[
  {
    "tag": "api_payload",
    "package": "video",
    "name": "SyncVideoPostProcessStatus",
    "desc": "sync video post process status",
    "aggregates": [],
    "requestFields": [
      { "name": "fileList", "type": "List<FileItem>", "nullable": false },
      { "name": "fileList[].fileIndex", "type": "Int", "nullable": false },
      { "name": "fileList[].variants", "type": "List<VariantItem>", "nullable": false },
      { "name": "fileList[].variants[].quality", "type": "String", "nullable": false, "defaultValue": "" },
      { "name": "fileList[].variants[].width", "type": "Int", "nullable": false, "defaultValue": "0" },
      { "name": "fileList[].variants[].children", "type": "List<VariantItem>", "nullable": false }
    ],
    "responseFields": [
      { "name": "categoryId", "type": "Long", "nullable": false },
      { "name": "children", "type": "List<self>", "nullable": true },
      { "name": "list", "type": "List<Item>", "nullable": false },
      { "name": "list[].messageType", "type": "Int", "nullable": false },
      { "name": "list[].count", "type": "Int", "nullable": false }
    ]
  }
]
```

- [ ] **Step 5: Add failing compile functional test**

Add this test to `PipelinePluginCompileFunctionalTest`:

```kotlin
@OptIn(ExperimentalPathApi::class)
@Test
fun `nested recursive design payload generation participates in adapter compileKotlin`() {
    val projectDir = Files.createTempDirectory("pipeline-functional-design-nested-recursion-compile")
    copyFixture(projectDir, "design-nested-recursion-compile-sample")

    val generateResult = GradleRunner.create()
        .withProjectDir(projectDir.toFile())
        .withPluginClasspath()
        .withArguments("cap4kGenerate")
        .build()

    val compileResult = GradleRunner.create()
        .withProjectDir(projectDir.toFile())
        .withPluginClasspath()
        .withArguments(":demo-adapter:compileKotlin")
        .build()

    val payloadFile = projectDir.resolve(
        "demo-adapter/src/main/kotlin/com/acme/demo/adapter/portal/api/payload/video/SyncVideoPostProcessStatus.kt",
    )
    val content = payloadFile.readText().replace("\r\n", "\n")

    assertTrue(generateResult.output.contains("BUILD SUCCESSFUL"))
    assertTrue(compileResult.output.contains("BUILD SUCCESSFUL"))
    assertTrue(content.contains("val fileList: List<FileItem>"))
    assertTrue(content.contains("data class FileItem("))
    assertTrue(content.contains("val variants: List<VariantItem>"))
    assertTrue(content.contains("data class VariantItem("))
    assertTrue(content.contains("val children: List<VariantItem>"))
    assertTrue(content.contains("val children: List<Response>?"))
    assertTrue(content.contains("data class Item("))
}
```

If `ExperimentalPathApi` is already imported in the file, reuse the existing import.

- [ ] **Step 6: Run the functional test and verify it fails before implementation if Task 2 was not applied**

Run:

```powershell
.\gradlew.bat --no-configuration-cache --no-build-cache :cap4k-plugin-pipeline-gradle:test --tests "com.only4.cap4k.plugin.pipeline.gradle.PipelinePluginCompileFunctionalTest.nested recursive design payload generation participates in adapter compileKotlin"
```

Expected before Task 2: FAIL at `cap4kGenerate`.

Expected after Task 2: PASS.

- [ ] **Step 7: Run targeted Gradle plugin functional test after Task 2 implementation**

Run:

```powershell
.\gradlew.bat --no-configuration-cache --no-build-cache :cap4k-plugin-pipeline-gradle:test --tests "com.only4.cap4k.plugin.pipeline.gradle.PipelinePluginCompileFunctionalTest.nested recursive design payload generation participates in adapter compileKotlin"
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 8: Commit functional fixture and test**

Commit:

```powershell
git add cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginCompileFunctionalTest.kt cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-nested-recursion-compile-sample
git commit -m "Verify nested design payload compilation"
```

## Task 4: Update Zero Dogfood Normalization

**Files:**
- Modify: `../only-danmuku-zero/docs/dogfood/normalize-design-input.ps1`
- Modify generated output: `../only-danmuku-zero/codegen/design/design.json`
- Modify generated output: `../only-danmuku-zero/codegen/design/skipped-design.json`
- Modify: `../only-danmuku-zero/docs/dogfood/cap4k-pipeline-issue-backlog.md`

- [ ] **Step 1: Remove skip for multi-level nested paths**

In `Normalize-NestedNamespace`, remove this failure branch:

```powershell
if ($parts.Count -ne 2) {
    return "unsupported multi-level nested field in ${namespace}: $name"
}
```

Replace root grouping with direct container checks for every prefix path that has children.

Implementation shape:

```powershell
$containerPaths = [ordered]@{}
foreach ($field in $fields) {
    $name = [string] $field.name
    if (-not $name.Contains(".")) {
        continue
    }
    $parts = $name.Split(".")
    for ($i = 0; $i -lt ($parts.Count - 1); $i++) {
        $containerName = ($parts[0..$i] -join ".")
        if (-not $containerPaths.Contains($containerName)) {
            $containerPaths[$containerName] = $true
        }
    }
}
```

For each `$containerName`, require one direct field with that normalized name.

- [ ] **Step 2: Preserve `[]` normalization**

Keep this behavior:

```powershell
$field.name = ([string] $field.name).Replace("[]", "")
```

This means raw `fileList[].variants[].quality` becomes pipeline input `fileList.variants.quality`.

- [ ] **Step 3: Stop skipping `Response` root recursion**

Replace the current `Response` skip:

```powershell
return "unsupported self-recursive Response type in ${namespace}: $($field.name)"
```

with normalization:

```powershell
$field.type = [regex]::Replace(
    [string] $field.type,
    "(?<![A-Za-z0-9_.])Response(?![A-Za-z0-9_])",
    "self"
)
```

Apply this only for root namespace recursion fields, where the referenced type is the generated root type.

- [ ] **Step 4: Stop skipping local `Item` when it has child fields**

Remove unconditional `Item` skip.

Keep `List<Item>` valid when a direct container and child fields define local `Item`:

```json
{ "name": "list", "type": "List<Item>" },
{ "name": "list[].messageType", "type": "Int" }
```

If `Item` appears without any child fields defining it, convert it to `self` only for known tree fields such as `children`.

Use explicit field-name check:

```powershell
if ($field.name -eq "children" -and ([string] $field.type) -match "(?<![A-Za-z0-9_.])Item(?![A-Za-z0-9_])") {
    $field.type = [regex]::Replace([string] $field.type, "(?<![A-Za-z0-9_.])Item(?![A-Za-z0-9_])", "self")
}
```

Do not add a general alias system.

- [ ] **Step 5: Rerun zero normalization**

Run from `only-danmuku-zero`:

```powershell
pwsh -NoProfile -ExecutionPolicy Bypass -File .\docs\dogfood\normalize-design-input.ps1
```

Expected output:

```text
Wrote <larger-count> standardized design entries to ...\codegen\design\design.json
Skipped <smaller-count> unsupported design entries to ...\codegen\design\skipped-design.json
```

The exact counts may change, but the two previously multi-level-only entries should no longer be skipped:

- `command video_post.SyncVideoPostProcessStatus`
- `api_payload account.batchSaveAccountList`

Entries using root recursion should also move out of skipped if converted to `self`:

- `query category.GetCategoryTree`
- `query video_comment.VideoCommentPage`

Local `Item` list entry should move out of skipped:

- `query message.GetNoReadMessageCountGroup`

- [ ] **Step 6: Run zero cap4kPlan**

Run from `only-danmuku-zero`:

```powershell
.\gradlew.bat --refresh-dependencies --no-configuration-cache --no-build-cache cap4kPlan
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Run zero cap4kGenerate**

Run:

```powershell
.\gradlew.bat --refresh-dependencies --no-configuration-cache --no-build-cache cap4kGenerate
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 8: Update backlog status**

Update `only-danmuku-zero/docs/dogfood/cap4k-pipeline-issue-backlog.md` nested model issue:

```markdown
状态：

已在 cap4k design generator 中支持多层 nested payload、`self` 根递归、显式 nested type 递归。zero 标准化脚本不再因这些能力跳过对应 design entry。
```

If some entries remain skipped for non-nested reasons, list the remaining reason explicitly.

- [ ] **Step 9: Commit zero normalization updates**

Commit from `only-danmuku-zero`:

```powershell
git add docs/dogfood/normalize-design-input.ps1 codegen/design/design.json codegen/design/skipped-design.json docs/dogfood/cap4k-pipeline-issue-backlog.md
git commit -m "Normalize nested recursive design inputs"
```

## Task 5: Full Verification And Publish Readiness

**Files:**
- No code files expected unless verification reveals a defect.

- [ ] **Step 1: Run generator-design tests**

Run from `cap4k`:

```powershell
.\gradlew.bat --no-configuration-cache --no-build-cache :cap4k-plugin-pipeline-generator-design:test
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: Run Gradle plugin tests**

Run with long timeout:

```powershell
.\gradlew.bat --no-configuration-cache --no-build-cache :cap4k-plugin-pipeline-gradle:test
```

Expected: BUILD SUCCESSFUL. This may take around 25 minutes on Windows.

- [ ] **Step 3: Run diff check**

Run:

```powershell
git diff --check
```

Expected: no whitespace errors. Windows LF/CRLF warnings are acceptable if no error lines are reported.

- [ ] **Step 4: Inspect cap4k status**

Run:

```powershell
git status --short
```

Expected: clean after commits.

- [ ] **Step 5: Inspect zero status**

Run from `only-danmuku-zero`:

```powershell
git status --short
```

Expected: clean after zero commit.

- [ ] **Step 6: Decide publish**

If user asks to publish after verification, run from `cap4k`:

```powershell
.\gradlew.bat --no-configuration-cache --no-build-cache publish
```

Expected: BUILD SUCCESSFUL.

Do not publish unless requested.

## Self-Review Checklist

- Spec requirement: multi-level nested fields -> Task 1, Task 2, Task 3.
- Spec requirement: `self` root recursion -> Task 1, Task 2, Task 3, Task 4.
- Spec requirement: explicit nested type recursion -> Task 1, Task 2, Task 3.
- Spec requirement: namespace isolation -> Task 1, Task 2.
- Spec requirement: deterministic ordering -> Task 2 and Task 3 generated content assertions.
- Spec requirement: clear invalid shape errors -> Task 1 and Task 2.
- Spec requirement: zero dogfood skipped entries -> Task 4.
- Non-goal: no aggregate/db/analysis changes -> File Map and Task scope exclude those modules.

## Execution Options

Plan complete and saved to `docs/superpowers/plans/2026-04-27-cap4k-design-nested-model-recursion.md`.

Two execution options:

1. **Subagent-Driven (recommended)** - Dispatch a fresh subagent per task, review between tasks, fast iteration.
2. **Inline Execution** - Execute tasks in this session using executing-plans, batch execution with checkpoints.

Which approach?

# Drawing Board Export Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add a code-analysis export pipeline that generates a merged `drawing_board.json` at `design/drawing_board.json` using a new arch-template tag.

**Architecture:** Extend the IR compiler plugin to emit `design-elements.json` per module, then add a new Gradle plugin to merge those into `drawing_board.json` using arch-template resolution (via `GenArchTask` helpers). Update the arch template to include a `drawing_board` node.

**Tech Stack:** Kotlin 2.2, Gradle plugins, Kotlin IR compiler plugin, Jackson (merge task), Gson/Pebble (existing), JUnit 5.

---

### Task 1: Test Infrastructure for Compiler Plugin

**Files:**
- Modify: `cap4k-plugin-code-analysis-compiler/build.gradle.kts`
- Create: `cap4k-plugin-code-analysis-compiler/src/test/kotlin/com/only4/cap4k/plugin/codeanalysis/compiler/TestCompileHelper.kt`
- Test: `cap4k-plugin-code-analysis-compiler/src/test/kotlin/com/only4/cap4k/plugin/codeanalysis/compiler/TestCompileHelperTest.kt`

**Step 1: Write the failing test (smoke harness)**

```kotlin
package com.only4.cap4k.plugin.codeanalysis.compiler

import com.tschuchort.compiletesting.SourceFile
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TestCompileHelperTest {
    @Test
    fun `compile helper emits output dir`() {
        val outputDir = compileWithCap4kPlugin(
            listOf(
                SourceFile.kotlin(
                    "Ping.kt",
                    "package demo; class Ping : com.only4.cap4k.ddd.core.application.RequestParam"
                )
            )
        )
        assertTrue(outputDir.resolve("nodes.json").toFile().exists())
    }
}
```

**Step 2: Run test to verify it fails**

Run: `.\gradlew :cap4k-plugin-code-analysis-compiler:test --tests "com.only4.cap4k.plugin.codeanalysis.compiler.TestCompileHelperTest"`  
Expected: FAIL (helper missing / no test deps)

**Step 3: Add test dependencies**

Update `cap4k-plugin-code-analysis-compiler/build.gradle.kts`:

```kotlin
dependencies {
    implementation(project(":cap4k-plugin-code-analysis-core"))
    compileOnly("org.jetbrains.kotlin:kotlin-compiler-embeddable:2.2.20")

    testImplementation(platform(libs.junit.bom))
    testImplementation("org.junit.jupiter:junit-jupiter-api")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation(libs.kotlin.compile.testing)
}
```

**Step 4: Add compile helper (minimal)**

Create `TestCompileHelper.kt`:

```kotlin
package com.only4.cap4k.plugin.codeanalysis.compiler

import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import java.io.File
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertEquals

fun compileWithCap4kPlugin(sources: List<SourceFile>): Path {
    val compilation = KotlinCompilation().apply {
        this.sources = sources
        inheritClassPath = true
        compilerPluginRegistrars = listOf(Cap4kCodeAnalysisCompilerRegistrar())
        messageOutputStream = System.out
    }
    val result = compilation.compile()
    assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)
    return File(compilation.workingDir, "build/cap4k-code-analysis").toPath()
}
```

**Step 5: Run test to verify it passes**

Run: `.\gradlew :cap4k-plugin-code-analysis-compiler:test --tests "com.only4.cap4k.plugin.codeanalysis.compiler.TestCompileHelperTest"`  
Expected: PASS

**Step 6: Commit**

```bash
git add cap4k-plugin-code-analysis-compiler/build.gradle.kts \
        cap4k-plugin-code-analysis-compiler/src/test/kotlin/com/only4/cap4k/plugin/codeanalysis/compiler/TestCompileHelper.kt \
        cap4k-plugin-code-analysis-compiler/src/test/kotlin/com/only4/cap4k/plugin/codeanalysis/compiler/TestCompileHelperTest.kt
git commit -m "test: add compile helper for code analysis compiler"
```

---

### Task 2: Add Design Models + JSON Writer (Core + Compiler)

**Files:**
- Create: `cap4k-plugin-code-analysis-core/src/main/kotlin/com/only4/cap4k/plugin/codeanalysis/core/model/DesignElement.kt`
- Create: `cap4k-plugin-code-analysis-compiler/src/main/kotlin/com/only4/cap4k/plugin/codeanalysis/compiler/DesignElementJsonWriter.kt`
- Test: `cap4k-plugin-code-analysis-compiler/src/test/kotlin/com/only4/cap4k/plugin/codeanalysis/compiler/DesignElementJsonWriterTest.kt`

**Step 1: Write the failing test**

```kotlin
@Test
fun `serializes design elements with fields and defaults`() {
    val elements = listOf(
        DesignElement(
            tag = "payload",
            `package` = "account",
            name = "batchSaveAccountList",
            desc = "",
            aggregates = emptyList(),
            entity = null,
            persist = null,
            requestFields = listOf(
                DesignField("globalId", "String", false, "0"),
                DesignField("account.accountNumber", "String", false, null)
            ),
            responseFields = listOf(DesignField("result", "Boolean", false, null))
        )
    )

    val json = DesignElementJsonWriter().write(elements)
    assertTrue(json.contains("\"name\":\"batchSaveAccountList\""))
    assertTrue(json.contains("\"defaultValue\":\"0\""))
    assertTrue(json.contains("\"account.accountNumber\""))
}
```

**Step 2: Run test to verify it fails**

Run: `.\gradlew :cap4k-plugin-code-analysis-compiler:test --tests "com.only4.cap4k.plugin.codeanalysis.compiler.DesignElementJsonWriterTest"`  
Expected: FAIL (missing writer / incorrect output)

**Step 3: Write minimal implementation**

- Add `DesignElement` + `DesignField` data classes in core.
- Add `DesignElementJsonWriter` with a stub `write()` returning `"[]"`.

**Step 4: Run test to verify it fails**

Run: `.\gradlew :cap4k-plugin-code-analysis-compiler:test --tests "com.only4.cap4k.plugin.codeanalysis.compiler.DesignElementJsonWriterTest"`  
Expected: FAIL (assertions not met)

**Step 5: Implement JSON writer**

- Build JSON in a `StringBuilder`, escape strings, include optional fields only when present.

**Step 6: Run test to verify it passes**

Run: `.\gradlew :cap4k-plugin-code-analysis-compiler:test --tests "com.only4.cap4k.plugin.codeanalysis.compiler.DesignElementJsonWriterTest"`  
Expected: PASS

**Step 7: Commit**

```bash
git add cap4k-plugin-code-analysis-core/src/main/kotlin/com/only4/cap4k/plugin/codeanalysis/core/model/DesignElement.kt \
        cap4k-plugin-code-analysis-compiler/src/main/kotlin/com/only4/cap4k/plugin/codeanalysis/compiler/DesignElementJsonWriter.kt \
        cap4k-plugin-code-analysis-compiler/src/test/kotlin/com/only4/cap4k/plugin/codeanalysis/compiler/DesignElementJsonWriterTest.kt
git commit -m "feat: add design element model and json writer"
```

---

### Task 3: Extract Design Elements in Compiler (IR)

**Files:**
- Modify: `cap4k-plugin-code-analysis-compiler/src/main/kotlin/com/only4/cap4k/plugin/codeanalysis/compiler/Cap4kIrGenerationExtension.kt`
- Create: `cap4k-plugin-code-analysis-compiler/src/main/kotlin/com/only4/cap4k/plugin/codeanalysis/compiler/DesignElementCollector.kt`
- Create: `cap4k-plugin-code-analysis-compiler/src/main/kotlin/com/only4/cap4k/plugin/codeanalysis/compiler/IrTypeFormatter.kt`
- Test: `cap4k-plugin-code-analysis-compiler/src/test/kotlin/com/only4/cap4k/plugin/codeanalysis/compiler/DesignElementExtractionTest.kt`

**Step 1: Write the failing test**

```kotlin
@Test
fun `emits design-elements json from request and payload`() {
    val sources = listOf(
        SourceFile.kotlin("IssueTokenCmd.kt", """
            package demo.application.commands.authorize
            class IssueTokenCmd : com.only4.cap4k.ddd.core.application.RequestParam {
                data class Request(val userId: Long, val note: String = "x")
                data class Response(val token: String)
            }
        """.trimIndent()),
        SourceFile.kotlin("BatchSaveAccountList.kt", """
            package demo.adapter.portal.api.payload.account
            object BatchSaveAccountList {
                data class Request(val globalId: String, val account: AccountInfo)
                data class Item(val result: Boolean)
                data class AccountInfo(val accountNumber: String)
            }
        """.trimIndent())
    )

    val outputDir = compileWithCap4kPlugin(sources)
    val json = outputDir.resolve("design-elements.json").readText()
    assertTrue(json.contains("\"tag\":\"cmd\""))
    assertTrue(json.contains("\"name\":\"IssueToken\""))
    assertTrue(json.contains("\"account.accountNumber\""))
}
```

**Step 2: Run test to verify it fails**

Run: `.\gradlew :cap4k-plugin-code-analysis-compiler:test --tests "com.only4.cap4k.plugin.codeanalysis.compiler.DesignElementExtractionTest"`  
Expected: FAIL (missing output / missing data)

**Step 3: Write minimal implementation**

- Implement `DesignElementCollector`:
  - Identify request classes (Cmd/Qry/Cli) via existing GraphCollector maps.
  - Identify payload objects in `adapter.portal.api.payload`.
  - Identify domain events via `@DomainEvent` or aggregate metadata.
  - Extract `Request` + `Response`/`Item` constructor params.
  - Flatten nested classes into `field.sub` or `list[].sub`.
  - Derive `name` (Cmd/Qry/Cli without suffix, payload lowerCamel), `package` from fqcn segments.
  - Fill `aggregates` via relationships (request -> handler -> aggregate).
- Update `Cap4kIrGenerationExtension.generate` to write `design-elements.json` using `DesignElementJsonWriter`.

**Step 4: Run test to verify it passes**

Run: `.\gradlew :cap4k-plugin-code-analysis-compiler:test --tests "com.only4.cap4k.plugin.codeanalysis.compiler.DesignElementExtractionTest"`  
Expected: PASS

**Step 5: Commit**

```bash
git add cap4k-plugin-code-analysis-compiler/src/main/kotlin/com/only4/cap4k/plugin/codeanalysis/compiler/Cap4kIrGenerationExtension.kt \
        cap4k-plugin-code-analysis-compiler/src/main/kotlin/com/only4/cap4k/plugin/codeanalysis/compiler/DesignElementCollector.kt \
        cap4k-plugin-code-analysis-compiler/src/main/kotlin/com/only4/cap4k/plugin/codeanalysis/compiler/IrTypeFormatter.kt \
        cap4k-plugin-code-analysis-compiler/src/test/kotlin/com/only4/cap4k/plugin/codeanalysis/compiler/DesignElementExtractionTest.kt
git commit -m "feat: emit design-elements json from compiler analysis"
```

---

### Task 4: Arch Template Helper for Output Path (Codegen)

**Files:**
- Modify: `cap4k-plugin-codegen/src/main/kotlin/com/only4/cap4k/plugin/codegen/gradle/GenArchTask.kt`
- Create: `cap4k-plugin-codegen/src/main/kotlin/com/only4/cap4k/plugin/codegen/gradle/ArchTemplateLocator.kt`
- Test: `cap4k-plugin-codegen/src/test/kotlin/com/only4/cap4k/plugin/codegen/gradle/ArchTemplateLocatorTest.kt`

**Step 1: Write the failing test**

```kotlin
@Test
fun `resolves drawing_board output path from arch template`() {
    val templatePath = tempArchTemplateWithDrawingBoard()
    val locator = ArchTemplateLocator(templatePath, "UTF-8")
    val node = locator.findByTag("drawing_board").single()
    assertTrue(node.name!!.endsWith("design${File.separator}drawing_board.json"))
}
```

**Step 2: Run test to verify it fails**

Run: `.\gradlew :cap4k-plugin-codegen:test --tests "com.only4.cap4k.plugin.codegen.gradle.ArchTemplateLocatorTest"`  
Expected: FAIL (locator missing)

**Step 3: Write minimal implementation**

- Add `ArchTemplateLocator` to load a template using the same logic as `GenArchTask`.
- Update `GenArchTask` to use the new helper (so the new flow is “依托 GenArchTask”).

**Step 4: Run test to verify it passes**

Run: `.\gradlew :cap4k-plugin-codegen:test --tests "com.only4.cap4k.plugin.codegen.gradle.ArchTemplateLocatorTest"`  
Expected: PASS

**Step 5: Commit**

```bash
git add cap4k-plugin-codegen/src/main/kotlin/com/only4/cap4k/plugin/codegen/gradle/GenArchTask.kt \
        cap4k-plugin-codegen/src/main/kotlin/com/only4/cap4k/plugin/codegen/gradle/ArchTemplateLocator.kt \
        cap4k-plugin-codegen/src/test/kotlin/com/only4/cap4k/plugin/codegen/gradle/ArchTemplateLocatorTest.kt
git commit -m "feat: add arch template locator for drawing board output"
```

---

### Task 5: Add Drawing Board Export Task in Codegen

**Files:**
- Modify: `cap4k-plugin-codegen/build.gradle.kts`
- Modify: `cap4k-plugin-codegen/src/main/kotlin/com/only4/cap4k/plugin/codegen/gradle/CodegenPlugin.kt`
- Create: `cap4k-plugin-codegen/src/main/kotlin/com/only4/cap4k/plugin/codegen/gradle/DrawingBoardExtension.kt`
- Create: `cap4k-plugin-codegen/src/main/kotlin/com/only4/cap4k/plugin/codegen/gradle/DrawingBoardExportTask.kt`
- Create: `cap4k-plugin-codegen/src/main/kotlin/com/only4/cap4k/plugin/codegen/gradle/DrawingBoardMerger.kt`
- Test: `cap4k-plugin-codegen/src/test/kotlin/com/only4/cap4k/plugin/codegen/gradle/DrawingBoardMergerTest.kt`

**Step 1: Write the failing test**

```kotlin
package com.only4.cap4k.plugin.codegen.gradle

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files

class DrawingBoardMergerTest {
    @Test
    fun `merges design elements and writes drawing_board json`() {
        val inputDir1 = Files.createTempDirectory("db1")
        val inputDir2 = Files.createTempDirectory("db2")
        inputDir1.resolve("design-elements.json").toFile()
            .writeText("""[{"tag":"cmd","package":"auth","name":"IssueToken"}]""")
        inputDir2.resolve("design-elements.json").toFile()
            .writeText("""[{"tag":"payload","package":"account","name":"batchSaveAccountList"}]""")

        val output = Files.createTempFile("drawing_board", ".json")
        DrawingBoardMerger.merge(listOf(inputDir1, inputDir2), output)

        val json = output.toFile().readText()
        assertTrue(json.contains("\"IssueToken\""))
        assertTrue(json.contains("\"batchSaveAccountList\""))
    }
}
```

**Step 2: Run test to verify it fails**

Run: `.\gradlew :cap4k-plugin-codegen:test --tests "com.only4.cap4k.plugin.codegen.gradle.DrawingBoardMergerTest"`  
Expected: FAIL (missing merger / output missing)

**Step 3: Write minimal implementation**

- Add `DrawingBoardMerger.merge()` stub that writes `[]`.
- Add `implementation(project(":cap4k-plugin-code-analysis-core"))` to `cap4k-plugin-codegen/build.gradle.kts`.
- Create `DrawingBoardExtension` with:
  - `inputDirs: ListProperty<Directory>`
  - `outputTag: Property<String>` (default `drawing_board`)
- Add `DrawingBoardExportTask` that:
  - Resolves input dirs (default from module build dirs, override if set).
  - Uses `ArchTemplateLocator` + `CodegenExtension` to resolve output file path.
  - Calls `DrawingBoardMerger.merge(...)`.
- Wire `cap4kDrawingBoard` task + extension in `CodegenPlugin.kt`.

**Step 4: Run test to verify it fails**

Run: `.\gradlew :cap4k-plugin-codegen:test --tests "com.only4.cap4k.plugin.codegen.gradle.DrawingBoardMergerTest"`  
Expected: FAIL (stub output)

**Step 5: Implement merge logic**

- Parse each `design-elements.json` via Gson into `List<DesignElement>`.
- Merge by `(tag, package, name)` with first-win semantics.
- Write pretty JSON to the output file.

**Step 6: Run test to verify it passes**

Run: `.\gradlew :cap4k-plugin-codegen:test --tests "com.only4.cap4k.plugin.codegen.gradle.DrawingBoardMergerTest"`  
Expected: PASS

**Step 7: Commit**

```bash
git add cap4k-plugin-codegen/build.gradle.kts \
        cap4k-plugin-codegen/src/main/kotlin/com/only4/cap4k/plugin/codegen/gradle/CodegenPlugin.kt \
        cap4k-plugin-codegen/src/main/kotlin/com/only4/cap4k/plugin/codegen/gradle/DrawingBoardExtension.kt \
        cap4k-plugin-codegen/src/main/kotlin/com/only4/cap4k/plugin/codegen/gradle/DrawingBoardExportTask.kt \
        cap4k-plugin-codegen/src/main/kotlin/com/only4/cap4k/plugin/codegen/gradle/DrawingBoardMerger.kt \
        cap4k-plugin-codegen/src/test/kotlin/com/only4/cap4k/plugin/codegen/gradle/DrawingBoardMergerTest.kt
git commit -m "feat: add drawing board export task to codegen"
```

---

### Task 6: Wire Output Path + Templates

**Files:**
- Modify: `cap4k/cap4k-ddd-codegen-template-multi-nested.json`
- Modify: `only-danmuku/cap4k-ddd-codegen-template-multi-nested.json`

**Step 1: Write the failing test**

```kotlin
@Test
fun `template includes drawing_board tag`() {
    val template = File("cap4k-ddd-codegen-template-multi-nested.json").readText()
    assertTrue(template.contains("\"tag\": \"drawing_board\""))
}
```

**Step 2: Run test to verify it fails**

Run: `.\gradlew :cap4k-plugin-codegen:test --tests "com.only4.cap4k.plugin.codegen.gradle.ArchTemplateLocatorTest"`  
Expected: FAIL (tag missing)

**Step 3: Write minimal implementation**

- Add a template node:
  - `tag: "drawing_board"`
  - `type: "file"`
  - `name: "drawing_board.json"`
  - under `design/` directory.

**Step 4: Run test to verify it passes**

Run: `.\gradlew :cap4k-plugin-codegen:test --tests "com.only4.cap4k.plugin.codegen.gradle.ArchTemplateLocatorTest"`  
Expected: PASS

**Step 5: Commit**

```bash
git add cap4k-ddd-codegen-template-multi-nested.json
git commit -m "feat: add drawing_board template node"
```

---

### Task 7: Optional Wiring in only-danmuku

**Files:**
- Modify: `only-danmuku/build.gradle.kts`

**Step 1: Update build config**

```kotlin
cap4kDrawingBoard {
    outputTag.set("drawing_board")
}
```

**Step 2: Commit**

```bash
git add only-danmuku/build.gradle.kts
git commit -m "chore: enable drawing board export"
```

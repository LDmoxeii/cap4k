# Drawing Board JSON Post-Process Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Keep Gson pretty printing for `drawing_board.json` while inlining `requestFields`/`responseFields` array items, and route final writes through template `PathNode` + `forceRender` with a context builder for future tag-based output splitting.

**Architecture:** Build a `DrawingBoardContext` with `elements` and `elementsByTag` from `design-elements.json`. Serialize via Gson pretty printing, then post-process only the target arrays to inline objects. Use the drawing_board template node to resolve output path and pass the JSON string to `forceRender` (no `.json.peb` loops).

**Tech Stack:** Kotlin, Gson, Gradle tasks.

### Task 1: JSON writer formatting (TDD)

**Files:**
- Create/Modify: `cap4k/cap4k-plugin-codegen/src/test/kotlin/com/only4/cap4k/plugin/codegen/drawingboard/DrawingBoardJsonWriterTest.kt`
- Modify: `cap4k/cap4k-plugin-codegen/src/main/kotlin/com/only4/cap4k/plugin/codegen/drawingboard/DrawingBoardJsonWriter.kt`

**Step 1: Write the failing test**

```kotlin
@Test
fun `keeps pretty printing for aggregates while inlining field arrays`() {
    val element = DesignElement(
        tag = "cli",
        `package` = "system",
        name = "GetSettings",
        desc = "Get settings",
        aggregates = listOf("Settings", "Audit"),
        requestFields = listOf(DesignField(name = "registerCoinCount", type = "Int"))
    )
    val output = DrawingBoardJsonWriter().write(listOf(element))
    assertTrue(output.contains("\"aggregates\": [\n      \"Settings\""))
    assertTrue(output.contains("{ \"name\": \"registerCoinCount\", \"type\": \"Int\", \"nullable\": false }"))
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew :cap4k-plugin-codegen:test --tests "com.only4.cap4k.plugin.codegen.drawingboard.DrawingBoardJsonWriterTest"`

Expected: FAIL because `aggregates` is compact (not pretty).

**Step 3: Write minimal implementation**

- Use `GsonBuilder().setPrettyPrinting()` for the base JSON.
- Post-process the resulting string to inline objects inside `requestFields`/`responseFields` arrays only.

**Step 4: Run test to verify it passes**

Run: `./gradlew :cap4k-plugin-codegen:test --tests "com.only4.cap4k.plugin.codegen.drawingboard.DrawingBoardJsonWriterTest"`

Expected: PASS.

**Step 5: Commit**

```
git add cap4k/cap4k-plugin-codegen/src/main/kotlin/com/only4/cap4k/plugin/codegen/drawingboard/DrawingBoardJsonWriter.kt \
        cap4k/cap4k-plugin-codegen/src/test/kotlin/com/only4/cap4k/plugin/codegen/drawingboard/DrawingBoardJsonWriterTest.kt
git commit -m "test: cover drawing board JSON post-processing"
```

### Task 2: Drawing board context builder (TDD)

**Files:**
- Create: `cap4k/cap4k-plugin-codegen/src/main/kotlin/com/only4/cap4k/plugin/codegen/context/drawingboard/DrawingBoardContext.kt`
- Create: `cap4k/cap4k-plugin-codegen/src/main/kotlin/com/only4/cap4k/plugin/codegen/context/drawingboard/DrawingBoardContextBuilder.kt`
- Create: `cap4k/cap4k-plugin-codegen/src/test/kotlin/com/only4/cap4k/plugin/codegen/drawingboard/DrawingBoardContextBuilderTest.kt`

**Step 1: Write the failing test**

```kotlin
@Test
fun `builds elements and groups by tag`() {
    val root = Files.createTempDirectory("drawing-context").toFile()
    val outDir = File(root, "adapter/build/cap4k-code-analysis").apply { mkdirs() }
    File(outDir, "design-elements.json").writeText(
        """[
          {"tag":"cmd","package":"a","name":"A","desc":"","requestFields":[],"responseFields":[]},
          {"tag":"qry","package":"b","name":"B","desc":"","requestFields":[],"responseFields":[]}
        ]"""
    )
    val ctx = MutableDrawingBoardContext()
    DrawingBoardContextBuilder(listOf(File(root, "adapter"))).build(ctx)
    assertEquals(2, ctx.elements.size)
    assertEquals(1, ctx.elementsByTag.getValue("cmd").size)
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew :cap4k-plugin-codegen:test --tests "com.only4.cap4k.plugin.codegen.drawingboard.DrawingBoardContextBuilderTest"`

Expected: FAIL (classes not found).

**Step 3: Write minimal implementation**

- `MutableDrawingBoardContext` with `elements` and `elementsByTag`.
- Builder uses `DrawingBoardCollector` and `groupBy { it.tag }`.

**Step 4: Run test to verify it passes**

Run: `./gradlew :cap4k-plugin-codegen:test --tests "com.only4.cap4k.plugin.codegen.drawingboard.DrawingBoardContextBuilderTest"`

Expected: PASS.

**Step 5: Commit**

```
git add cap4k/cap4k-plugin-codegen/src/main/kotlin/com/only4/cap4k/plugin/codegen/context/drawingboard \
        cap4k/cap4k-plugin-codegen/src/test/kotlin/com/only4/cap4k/plugin/codegen/drawingboard/DrawingBoardContextBuilderTest.kt
git commit -m "test: add drawing board context builder"
```

### Task 3: Template-node PathNode rendering (TDD)

**Files:**
- Create: `cap4k/cap4k-plugin-codegen/src/main/kotlin/com/only4/cap4k/plugin/codegen/drawingboard/DrawingBoardTemplateRenderer.kt`
- Create: `cap4k/cap4k-plugin-codegen/src/test/kotlin/com/only4/cap4k/plugin/codegen/drawingboard/DrawingBoardTemplateRendererTest.kt`

**Step 1: Write the failing test**

```kotlin
@Test
fun `renders template node to path node with content`() {
    val tpl = TemplateNode().apply {
        type = "file"
        tag = "drawing_board"
        name = "drawing_board.json"
    }
    var rendered: Pair<PathNode, String>? = null
    DrawingBoardTemplateRenderer().render(
        templateNodes = listOf(tpl),
        parentPath = "/tmp",
        baseContext = emptyMap(),
        generatorName = "drawing_board",
        content = "[]"
    ) { node, parent -> rendered = node to parent }
    assertEquals("drawing_board.json", rendered!!.first.name)
    assertEquals("[]", rendered!!.first.data)
    assertEquals("/tmp", rendered!!.second)
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew :cap4k-plugin-codegen:test --tests "com.only4.cap4k.plugin.codegen.drawingboard.DrawingBoardTemplateRendererTest"`

Expected: FAIL (class not found).

**Step 3: Write minimal implementation**

- Use `TemplateNode.mergeAndSelect(...)` and `deepCopy().resolve(baseContext)` to build `PathNode`.
- Set `data = content`, `format = "raw"` before invoking render callback.

**Step 4: Run test to verify it passes**

Run: `./gradlew :cap4k-plugin-codegen:test --tests "com.only4.cap4k.plugin.codegen.drawingboard.DrawingBoardTemplateRendererTest"`

Expected: PASS.

**Step 5: Commit**

```
git add cap4k/cap4k-plugin-codegen/src/main/kotlin/com/only4/cap4k/plugin/codegen/drawingboard/DrawingBoardTemplateRenderer.kt \
        cap4k/cap4k-plugin-codegen/src/test/kotlin/com/only4/cap4k/plugin/codegen/drawingboard/DrawingBoardTemplateRendererTest.kt
git commit -m "test: add drawing board template renderer"
```

### Task 4: Wire GenDrawingBoardTask to context + renderer

**Files:**
- Modify: `cap4k/cap4k-plugin-codegen/src/main/kotlin/com/only4/cap4k/plugin/codegen/gradle/GenDrawingBoardTask.kt`
- Modify: `cap4k/cap4k-plugin-codegen/src/main/kotlin/com/only4/cap4k/plugin/codegen/drawingboard/DrawingBoardJsonWriter.kt`

**Step 1: Wire task**
- Build `DrawingBoardContext` via the builder.
- Serialize with `DrawingBoardJsonWriter`.
- Resolve template nodes and call `forceRender(pathNode, parentPath)` using the new renderer.

**Step 2: Run writer + context + renderer tests**

Run:
```
./gradlew :cap4k-plugin-codegen:test --tests "com.only4.cap4k.plugin.codegen.drawingboard.*"
```

Expected: PASS.

**Step 3: Commit**

```
git add cap4k/cap4k-plugin-codegen/src/main/kotlin/com/only4/cap4k/plugin/codegen/gradle/GenDrawingBoardTask.kt \
        cap4k/cap4k-plugin-codegen/src/main/kotlin/com/only4/cap4k/plugin/codegen/drawingboard/DrawingBoardJsonWriter.kt
git commit -m "feat: render drawing board via template path node"
```

### Task 5: Local verification

**Step 1: Publish plugin locally**

Run: `./gradlew --% :cap4k-plugin-codegen:publishToMavenLocal -Dmaven.repo.local=D:\Program\m2repository\.m2\repository`

Expected: SUCCESS (warnings about duplicate plugin publications are acceptable).

**Step 2: Regenerate drawing_board.json in only-danmuku**

Run: `./gradlew --% cap4kGenDrawingBoard -Dmaven.repo.local=D:\Program\m2repository\.m2\repository`

Expected: SUCCESS and `only-danmuku/design/drawing_board.json` updated with inline field objects.

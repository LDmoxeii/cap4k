# Cap4k Design Generator Minimal Usable Upgrade Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Upgrade the pipeline design generator from empty shell `Cmd/Qry` outputs into minimally usable Kotlin object-scoped request/response types with fields, nested inner types, and a safe first-pass type/import layer.

**Architecture:** Keep the pipeline stages and canonical model stable. Add generator-local render models and field-tree parsing inside `cap4k-plugin-pipeline-generator-design`, then extend the default Pebble `command/query` templates to render `object XxxCmd/XxxQry` with inner `Request` and `Response` data classes. Keep import behavior deliberately conservative: use explicit identity when available, never guess on ambiguous short names.

**Tech Stack:** Kotlin 2.2, Gradle Kotlin DSL, JUnit 5, Pebble

---

## File Map

- Modify: `cap4k/cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignArtifactPlanner.kt`
- Create: `cap4k/cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignRenderModels.kt`
- Create: `cap4k/cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignRenderModelFactory.kt`
- Modify: `cap4k/cap4k-plugin-pipeline-generator-design/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignArtifactPlannerTest.kt`
- Modify: `cap4k/cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/design/command.kt.peb`
- Modify: `cap4k/cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/design/query.kt.peb`
- Modify: `cap4k/cap4k-plugin-pipeline-renderer-pebble/src/test/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PebbleArtifactRendererTest.kt`
- Modify: `cap4k/cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginFunctionalTest.kt`
- Modify: `cap4k/cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-sample/build.gradle.kts`

### Task 1: Define Generator-Local Render Models and Red Tests

**Files:**
- Create: `cap4k/cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignRenderModels.kt`
- Create: `cap4k/cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignRenderModelFactory.kt`
- Modify: `cap4k/cap4k-plugin-pipeline-generator-design/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignArtifactPlannerTest.kt`

- [ ] **Step 1: Rewrite planner tests around minimally usable output context**

Add failing coverage for:
- command/query plan items still land in the same package/module paths
- planner context now includes:
  - `imports`
  - `requestFields`
  - `responseFields`
  - `requestNestedTypes`
  - `responseNestedTypes`
- nested request/response structures are rendered as inner namespaces rather than flattened top-level names
- identical nested type names within one `Request` or `Response` namespace fail fast

Use a canonical request fixture that includes:
- scalar fields
- nullable fields
- one-level collection fields
- nested request object fields
- nested response object fields

- [ ] **Step 2: Run the targeted planner test and confirm it fails**

Run:
`./gradlew :cap4k-plugin-pipeline-generator-design:test --tests "com.only4.cap4k.plugin.pipeline.generator.design.DesignArtifactPlannerTest" --rerun-tasks`

Expected:
- existing planner compiles against old context only
- new assertions fail because the render model layer does not exist yet

- [ ] **Step 3: Introduce generator-local render model types**

Add:
- `DesignFieldRenderModel`
- `DesignTypeRenderModel`
- `DesignRequestRenderModel`

Rules:
- keep them inside `generator-design`
- do not add them to pipeline API
- keep imports as a precomputed `List<String>`

- [ ] **Step 4: Implement a render-model factory**

Add `DesignRenderModelFactory` responsible for:
- turning `RequestModel` into `DesignRequestRenderModel`
- splitting request and response trees
- deriving nested inner type declarations
- enforcing namespace-local duplicate nested type detection
- producing minimal import lists without guessing on ambiguous short names

The first implementation only needs to support:
- Kotlin scalar types
- explicit nullable types
- one-layer collections
- explicit fully qualified external types
- nested inner object types under `Request` or `Response`

- [ ] **Step 5: Re-run the targeted planner test**

Run:
`./gradlew :cap4k-plugin-pipeline-generator-design:test --tests "com.only4.cap4k.plugin.pipeline.generator.design.DesignArtifactPlannerTest" --rerun-tasks`

Expected:
- tests still fail in rendering assertions because the templates remain empty-shell templates

### Task 2: Rework `DesignArtifactPlanner` to Use the Render Model

**Files:**
- Modify: `cap4k/cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignArtifactPlanner.kt`
- Modify: `cap4k/cap4k-plugin-pipeline-generator-design/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignArtifactPlannerTest.kt`

- [ ] **Step 1: Update the planner to build rich template context**

Keep existing responsibilities unchanged:
- application module validation
- package path calculation
- command/query template selection

Replace the old context map with one derived from `DesignRenderModelFactory`, including:
- `packageName`
- `typeName`
- `description`
- `aggregateName`
- `imports`
- `requestFields`
- `responseFields`
- `requestNestedTypes`
- `responseNestedTypes`

- [ ] **Step 2: Keep output-path behavior unchanged**

The slice must not change:
- output directories
- package naming conventions
- command/query file names
- conflict policy handling

- [ ] **Step 3: Re-run the full generator-design test task**

Run:
`./gradlew :cap4k-plugin-pipeline-generator-design:test --rerun-tasks`

Expected:
- generator module tests pass or only template-rendering expectations remain failing

### Task 3: Upgrade Default Command/Query Templates to Object-Scoped Output

**Files:**
- Modify: `cap4k/cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/design/command.kt.peb`
- Modify: `cap4k/cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/design/query.kt.peb`
- Modify: `cap4k/cap4k-plugin-pipeline-renderer-pebble/src/test/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PebbleArtifactRendererTest.kt`

- [ ] **Step 1: Add failing renderer coverage for minimally usable design output**

Extend renderer tests to assert:
- top-level output is `object XxxCmd` or `object XxxQry`
- `Request` and `Response` are inner `data class` declarations
- nested types render inside `Request` and `Response`
- imports render above the object when present
- empty request/response lists still produce stable Kotlin output

- [ ] **Step 2: Run the targeted renderer tests and confirm they fail**

Run:
`./gradlew :cap4k-plugin-pipeline-renderer-pebble:test --tests "com.only4.cap4k.plugin.pipeline.renderer.pebble.PebbleArtifactRendererTest" --rerun-tasks`

Expected:
- tests fail because current templates still only render `class {{ typeName }}`

- [ ] **Step 3: Rewrite the default design templates**

Update both templates to render:
- package declaration
- deterministic import block when `imports` is not empty
- `object {{ typeName }}`
- inner `data class Request`
- inner `data class Response`
- nested inner types declared inside the relevant namespace

Keep template logic simple:
- loops only
- no smart type inference inside Pebble
- no `use()` or custom template functions in this slice

- [ ] **Step 4: Re-run the targeted renderer tests**

Run:
`./gradlew :cap4k-plugin-pipeline-renderer-pebble:test --tests "com.only4.cap4k.plugin.pipeline.renderer.pebble.PebbleArtifactRendererTest" --rerun-tasks`

Expected:
- `BUILD SUCCESSFUL`

### Task 4: Update Functional Coverage Around the New Design Output

**Files:**
- Modify: `cap4k/cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginFunctionalTest.kt`
- Modify: `cap4k/cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-sample/build.gradle.kts`

- [ ] **Step 1: Add or update a design functional test**

Assert that `cap4kGenerate` produces:
- an object-scoped command file
- an object-scoped query file
- nested request/response types when fixture fields require them

Keep the fixture on the new `cap4k { ... }` DSL.

- [ ] **Step 2: Keep functional scope minimal**

Do not expand this slice into:
- handler generation
- client generation
- aggregate/flow/drawing-board integration changes

Only update the design fixture enough to validate the richer output contract.

- [ ] **Step 3: Run the targeted functional test**

Run:
`./gradlew :cap4k-plugin-pipeline-gradle:test --tests "com.only4.cap4k.plugin.pipeline.gradle.PipelinePluginFunctionalTest" --rerun-tasks`

Expected:
- `BUILD SUCCESSFUL`

### Task 5: Full Verification and Commit

**Files:**
- Modify only the files listed above

- [ ] **Step 1: Run focused verification**

Run:
`./gradlew :cap4k-plugin-pipeline-generator-design:test :cap4k-plugin-pipeline-renderer-pebble:test :cap4k-plugin-pipeline-gradle:test --rerun-tasks`

Expected:
- `BUILD SUCCESSFUL`

- [ ] **Step 2: Run a narrow end-to-end verification**

Run:
`./gradlew :cap4k-plugin-pipeline-gradle:test --tests "com.only4.cap4k.plugin.pipeline.gradle.PipelinePluginFunctionalTest" --rerun-tasks`

Expected:
- generated fixture outputs match the new object-scoped shape

- [ ] **Step 3: Review diffs for scope control**

Confirm the final diff does not include:
- pipeline API/core changes unrelated to design rendering
- new public DSL changes
- legacy template runtime resurrection

- [ ] **Step 4: Commit the slice**

```bash
git add \
  cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignArtifactPlanner.kt \
  cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignRenderModels.kt \
  cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignRenderModelFactory.kt \
  cap4k-plugin-pipeline-generator-design/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignArtifactPlannerTest.kt \
  cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/design/command.kt.peb \
  cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/design/query.kt.peb \
  cap4k-plugin-pipeline-renderer-pebble/src/test/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PebbleArtifactRendererTest.kt \
  cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginFunctionalTest.kt \
  cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-sample/build.gradle.kts \
  docs/superpowers/plans/2026-04-10-cap4k-design-generator-minimal-usable.md
git commit -m "feat: improve minimal usable design generation"
```

## Notes

- Keep command length small when writing or updating large files. Prefer multiple smaller patches over one large patch.
- Do not pull the legacy `use()/imports()/type()` runtime back in during this slice.
- If type identity is ambiguous, prefer FQN rendering or fail-fast. Never guess by short class name alone.

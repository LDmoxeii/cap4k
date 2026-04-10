# Cap4k Design Generator Type And Import Resolution Upgrade Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a generator-local type parsing, identity resolution, and import planning layer so design generation renders correct field types and avoids wrong auto-import decisions under same-name collisions.

**Architecture:** Keep all changes inside `cap4k-plugin-pipeline-generator-design` plus the Pebble templates and existing tests that consume its output. Replace string-oriented type rewriting with a narrow internal pipeline: parse raw type text into structured refs, resolve each ref to a safe identity class, compute an import plan from those identities, and expose only rendered text plus import lists to the templates.

**Tech Stack:** Kotlin 2.2, Gradle Kotlin DSL, JUnit 5, Pebble

---

## File Map

- Modify: `cap4k/cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignRenderModels.kt`
- Modify: `cap4k/cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignRenderModelFactory.kt`
- Create: `cap4k/cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignTypeModels.kt`
- Create: `cap4k/cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignTypeParser.kt`
- Create: `cap4k/cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignTypeResolver.kt`
- Create: `cap4k/cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignImportPlanner.kt`
- Modify: `cap4k/cap4k-plugin-pipeline-generator-design/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignArtifactPlannerTest.kt`
- Create: `cap4k/cap4k-plugin-pipeline-generator-design/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignTypeParserTest.kt`
- Create: `cap4k/cap4k-plugin-pipeline-generator-design/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignTypeResolverTest.kt`
- Modify: `cap4k/cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/design/command.kt.peb`
- Modify: `cap4k/cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/design/query.kt.peb`
- Modify: `cap4k/cap4k-plugin-pipeline-renderer-pebble/src/test/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PebbleArtifactRendererTest.kt`
- Modify: `cap4k/cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginFunctionalTest.kt`
- Modify: `cap4k/cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-sample/build.gradle.kts`

### Task 1: Add Type Parser Coverage And Minimal Parser

**Files:**
- Create: `cap4k/cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignTypeModels.kt`
- Create: `cap4k/cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignTypeParser.kt`
- Create: `cap4k/cap4k-plugin-pipeline-generator-design/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignTypeParserTest.kt`

- [ ] **Step 1: Write failing parser tests**

Cover these cases:
- `String`
- `String?`
- `List<String>`
- `Map<String, Long>`
- `List<com.foo.Status?>`
- malformed `List<String`
- malformed `Map<String,,Long>`

Assert parsed output preserves:
- root token
- nullability
- child argument order
- nested generic structure

- [ ] **Step 2: Run parser tests and verify failure**

Run:
`./gradlew :cap4k-plugin-pipeline-generator-design:test --tests "com.only4.cap4k.plugin.pipeline.generator.design.DesignTypeParserTest" --rerun-tasks`

Expected:
- test compilation or runtime failure because parser/model files do not exist yet

- [ ] **Step 3: Add parsed type model**

Create a narrow internal model with:
- raw token text
- nullable flag
- generic children

Keep it generator-local and do not expose it through pipeline API.

- [ ] **Step 4: Implement minimal parser**

Support:
- nullable suffix on any node
- recursive generic arguments
- explicit FQCN tokens
- unresolved short names

Fail fast on:
- mismatched angle brackets
- empty generic arguments
- trailing commas

- [ ] **Step 5: Re-run parser tests**

Run:
`./gradlew :cap4k-plugin-pipeline-generator-design:test --tests "com.only4.cap4k.plugin.pipeline.generator.design.DesignTypeParserTest" --rerun-tasks`

Expected:
- `BUILD SUCCESSFUL`

### Task 2: Add Resolution And Import-Planning Coverage

**Files:**
- Create: `cap4k/cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignTypeResolver.kt`
- Create: `cap4k/cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignImportPlanner.kt`
- Create: `cap4k/cap4k-plugin-pipeline-generator-design/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignTypeResolverTest.kt`

- [ ] **Step 1: Write failing resolver tests**

Cover these cases:
- built-in type stays unqualified and unimported
- inner type wins over external same-name type
- unique FQCN imports and renders short name
- colliding FQCN names render qualified names and produce no imports
- unresolved short name stays unresolved and unimported
- recursive generics preserve qualified fallback inside children

- [ ] **Step 2: Run resolver tests and verify failure**

Run:
`./gradlew :cap4k-plugin-pipeline-generator-design:test --tests "com.only4.cap4k.plugin.pipeline.generator.design.DesignTypeResolverTest" --rerun-tasks`

Expected:
- failures because resolver/import planner do not exist yet

- [ ] **Step 3: Implement resolved and rendered type models**

Represent at least:
- built-in scalar/collection
- inner type
- explicit FQCN
- unresolved short name

Expose enough output to drive:
- final rendered type text
- import candidate collection
- qualified fallback detection

- [ ] **Step 4: Implement resolver and import planner**

Rules:
- built-ins never import
- inner types never import
- explicit FQCN imports only when their simple name is unique
- external FQCN colliding with another external simple name stays qualified
- external FQCN colliding with an inner type also stays qualified
- unresolved short names stay raw and never import

- [ ] **Step 5: Re-run resolver tests**

Run:
`./gradlew :cap4k-plugin-pipeline-generator-design:test --tests "com.only4.cap4k.plugin.pipeline.generator.design.DesignTypeResolverTest" --rerun-tasks`

Expected:
- `BUILD SUCCESSFUL`

### Task 3: Integrate Type Resolution Into Design Render Models

**Files:**
- Modify: `cap4k/cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignRenderModels.kt`
- Modify: `cap4k/cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignRenderModelFactory.kt`
- Modify: `cap4k/cap4k-plugin-pipeline-generator-design/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignArtifactPlannerTest.kt`

- [ ] **Step 1: Rewrite planner/render-model tests around rendered types**

Update expectations so field models assert:
- `renderedType`
- import list correctness
- FQCN collision fallback
- inner nested types keep short names

Keep existing path/package assertions intact.

- [ ] **Step 2: Run planner tests and verify failure**

Run:
`./gradlew :cap4k-plugin-pipeline-generator-design:test --tests "com.only4.cap4k.plugin.pipeline.generator.design.DesignArtifactPlannerTest" --rerun-tasks`

Expected:
- failures because current render model still exposes old string-only field type behavior

- [ ] **Step 3: Update render models and factory**

Adjust generator-local models so fields expose rendered type text instead of rebuilding it in the template.

Factory responsibilities:
- parse raw field types
- resolve identities with namespace-aware inner-type knowledge
- compute import plan across request and response namespaces together
- push precomputed imports plus rendered field types into the context map

- [ ] **Step 4: Keep existing behavior stable outside type/import handling**

Do not change:
- file naming
- package layout
- command/query template selection
- output path rules
- conflict policy behavior

- [ ] **Step 5: Re-run the full generator-design test task**

Run:
`./gradlew :cap4k-plugin-pipeline-generator-design:test --rerun-tasks`

Expected:
- `BUILD SUCCESSFUL` or only renderer/functional expectations remain failing

### Task 4: Update Pebble Templates And Functional Coverage

**Files:**
- Modify: `cap4k/cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/design/command.kt.peb`
- Modify: `cap4k/cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/design/query.kt.peb`
- Modify: `cap4k/cap4k-plugin-pipeline-renderer-pebble/src/test/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PebbleArtifactRendererTest.kt`
- Modify: `cap4k/cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginFunctionalTest.kt`
- Modify: `cap4k/cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-sample/build.gradle.kts`

- [ ] **Step 1: Write failing renderer and functional assertions**

Add assertions for:
- `imports` are emitted from the precomputed list
- fields read `renderedType` rather than reconstructing raw type strings
- collision cases keep qualified names in generated Kotlin
- nested inner types still render under `Request` and `Response`

- [ ] **Step 2: Run renderer and functional tests and verify failure**

Run:
`./gradlew :cap4k-plugin-pipeline-renderer-pebble:test --tests "com.only4.cap4k.plugin.pipeline.renderer.pebble.PebbleArtifactRendererTest" :cap4k-plugin-pipeline-gradle:test --tests "com.only4.cap4k.plugin.pipeline.gradle.PipelinePluginFunctionalTest" --rerun-tasks`

Expected:
- renderer and fixture assertions fail because templates still read the old field property shape

- [ ] **Step 3: Update the default design templates**

Keep templates simple:
- output `imports`
- output `field.renderedType`
- do not add helper-driven inference
- do not reintroduce `use()`

- [ ] **Step 4: Update the design functional fixture**

Ensure the fixture includes at least:
- one explicit FQCN that can import safely
- one same-simple-name collision case that must stay qualified
- one nested inner type reference

Keep the fixture on the current `cap4k { ... }` DSL.

- [ ] **Step 5: Re-run renderer and functional tests**

Run:
`./gradlew :cap4k-plugin-pipeline-renderer-pebble:test --tests "com.only4.cap4k.plugin.pipeline.renderer.pebble.PebbleArtifactRendererTest" :cap4k-plugin-pipeline-gradle:test --tests "com.only4.cap4k.plugin.pipeline.gradle.PipelinePluginFunctionalTest" --rerun-tasks`

Expected:
- `BUILD SUCCESSFUL`

### Task 5: Full Verification And Commit

**Files:**
- Modify only the files listed above

- [ ] **Step 1: Run focused verification**

Run:
`./gradlew :cap4k-plugin-pipeline-generator-design:test :cap4k-plugin-pipeline-renderer-pebble:test :cap4k-plugin-pipeline-gradle:test --rerun-tasks`

Expected:
- `BUILD SUCCESSFUL`

- [ ] **Step 2: Review final diff for scope control**

Confirm the diff does not include:
- pipeline API changes
- pipeline core changes
- new Gradle DSL changes
- legacy `use()` runtime resurrection

- [ ] **Step 3: Commit the slice**

```bash
git add \
  cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignRenderModels.kt \
  cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignRenderModelFactory.kt \
  cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignTypeModels.kt \
  cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignTypeParser.kt \
  cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignTypeResolver.kt \
  cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignImportPlanner.kt \
  cap4k-plugin-pipeline-generator-design/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignArtifactPlannerTest.kt \
  cap4k-plugin-pipeline-generator-design/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignTypeParserTest.kt \
  cap4k-plugin-pipeline-generator-design/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignTypeResolverTest.kt \
  cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/design/command.kt.peb \
  cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/design/query.kt.peb \
  cap4k-plugin-pipeline-renderer-pebble/src/test/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PebbleArtifactRendererTest.kt \
  cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginFunctionalTest.kt \
  cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-sample/build.gradle.kts \
  docs/superpowers/plans/2026-04-10-cap4k-design-generator-type-import-resolution.md
git commit -m "feat: improve design type and import resolution"
```

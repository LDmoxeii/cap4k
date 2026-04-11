# Cap4k Design Generator Auto-Import Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add conservative, identity-based auto-import to the pipeline design generator so imports are never decided by class-name guessing.

**Architecture:** Keep all import decisions inside `cap4k-plugin-pipeline-generator-design`. Introduce a controlled symbol registry, a resolver that produces `imports` and `renderedType`, and fail-fast behavior for ambiguous or unknown short names. Templates and renderer remain consumers of resolved output only.

**Tech Stack:** Kotlin, Gradle, JUnit 5, Pebble 3.2.4, Gradle TestKit

---

### Task 1: Introduce Symbol Identity And Registry Model

**Files:**
- Create: `cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/types/SymbolIdentity.kt`
- Create: `cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/types/DesignSymbolRegistry.kt`
- Modify: `cap4k-plugin-pipeline-generator-design/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignTypeRenderModelFactoryTest.kt`

- [ ] **Step 1: Add failing tests for registry identity behavior**

Add focused tests that prove the registry keeps full identity and supports multiple candidates per simple name.

Coverage should include:

- registering one symbol and resolving it by simple name
- registering two symbols with the same simple name and observing two candidates
- preserving package identity for each candidate

Example assertions:

```kotlin
val registry = DesignSymbolRegistry(
    listOf(
        SymbolIdentity("com.foo", "Status"),
        SymbolIdentity("com.bar", "Status"),
    )
)

assertEquals(2, registry.findBySimpleName("Status").size)
assertTrue(registry.findBySimpleName("Status").any { it.packageName == "com.foo" })
assertTrue(registry.findBySimpleName("Status").any { it.packageName == "com.bar" })
```

- [ ] **Step 2: Run focused generator tests to verify they fail**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-generator-design:test --tests "com.only4.cap4k.plugin.pipeline.generator.design.DesignTypeRenderModelFactoryTest" --rerun-tasks
```

Expected: FAIL because symbol identity and registry classes do not exist yet.

- [ ] **Step 3: Implement `SymbolIdentity` and `DesignSymbolRegistry`**

`SymbolIdentity` should at minimum contain:

- `packageName`
- `typeName`
- optional `moduleRole`
- optional `source`

`DesignSymbolRegistry` should:

- index by simple name
- retain multiple candidates per simple name
- expose lookup helpers such as `findBySimpleName(simpleName: String): List<SymbolIdentity>`

Implementation rule:

- never collapse multiple same-name symbols into one winner

- [ ] **Step 4: Re-run focused generator tests**

Run the same test command from Step 2.

Expected: PASS for the new identity-model behavior.

- [ ] **Step 5: Commit symbol identity foundation**

Run:

```powershell
git add cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/types/SymbolIdentity.kt cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/types/DesignSymbolRegistry.kt cap4k-plugin-pipeline-generator-design/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignTypeRenderModelFactoryTest.kt
git commit -m "feat: add design symbol identity registry"
```

### Task 2: Add Auto-Import Resolver And Decision Rules

**Files:**
- Create: `cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/types/ImportResolver.kt`
- Create: `cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/types/ImportResolutionResult.kt`
- Modify: `cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/types/DesignTypeRenderModelFactory.kt`
- Modify: `cap4k-plugin-pipeline-generator-design/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignTypeRenderModelFactoryTest.kt`

- [ ] **Step 1: Add failing tests for import-resolution rules**

Extend generator tests to cover:

- explicit FQN imported when unique
- explicit FQN preserved when conflicting
- inner type wins over external same-name type
- unique registry hit imports short name
- ambiguous short name fails fast
- unknown short name fails fast

Example expectations:

```kotlin
assertEquals(listOf("java.time.LocalDateTime"), result.imports)
assertEquals("LocalDateTime", result.renderedType)

assertEquals(emptyList<String>(), result.imports)
assertEquals("com.foo.Status", result.renderedType)
```

Failure-path assertions should check message content for:

- field name
- raw type text
- ambiguity or unknown-symbol reason

- [ ] **Step 2: Run focused generator tests to verify failure**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-generator-design:test --tests "com.only4.cap4k.plugin.pipeline.generator.design.DesignTypeRenderModelFactoryTest" --rerun-tasks
```

Expected: FAIL because the resolver and fail-fast behavior are not implemented yet.

- [ ] **Step 3: Implement `ImportResolver` and integrate it into render-model construction**

`ImportResolver` should follow the approved order:

1. built-ins and standard collections: no import
2. current generated-unit inner types: no import
3. explicit FQNs:
   - unique simple name -> import and shorten
   - conflicting simple name -> keep FQN
4. unique registry hit for short name -> import and shorten
5. ambiguous short name -> fail fast
6. unknown short name -> fail fast

Implementation requirements:

- keep inner-type precedence above external same-name symbols
- treat symbol identity as the truth source
- never guess from simple name alone
- keep generic recursion intact

`ImportResolutionResult` should expose at minimum:

- `renderedType`
- `imports`
- optional diagnostics metadata if useful internally

- [ ] **Step 4: Re-run focused generator tests**

Run the same command from Step 2.

Expected: PASS for all new resolution cases.

- [ ] **Step 5: Commit import-resolution core**

Run:

```powershell
git add cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/types/ImportResolver.kt cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/types/ImportResolutionResult.kt cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/types/DesignTypeRenderModelFactory.kt cap4k-plugin-pipeline-generator-design/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignTypeRenderModelFactoryTest.kt
git commit -m "feat: add conservative auto import resolution"
```

### Task 3: Wire Registry And Resolver Into Design Artifact Planning

**Files:**
- Modify: `cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignArtifactPlanner.kt`
- Modify: `cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/types/DesignTypeRenderModelFactory.kt`
- Modify: `cap4k-plugin-pipeline-generator-design/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignArtifactPlannerTest.kt`

- [ ] **Step 1: Add failing planner tests for symbol-aware import output**

Add tests that prove planner contexts now contain:

- safe import lists for unique external types
- FQN-preserved rendered types for conflicts
- fail-fast behavior for ambiguous short names in full planning flow

Example assertions:

```kotlin
assertEquals(listOf("java.time.LocalDateTime"), context["imports"])
assertTrue((context["requestFields"] as List<Map<String, Any>>)
    .any { it["name"] == "createdAt" && it["renderedType"] == "LocalDateTime" })
```

Add one failure test:

```kotlin
val ex = assertThrows<IllegalArgumentException> {
    planner.plan(...)
}
assertTrue(ex.message!!.contains("Status"))
assertTrue(ex.message!!.contains("ambiguous"))
```

- [ ] **Step 2: Run focused planner tests**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-generator-design:test --tests "com.only4.cap4k.plugin.pipeline.generator.design.DesignArtifactPlannerTest" --rerun-tasks
```

Expected: FAIL because planner wiring is not complete yet.

- [ ] **Step 3: Build the controlled registry from planner-known symbols and use it during planning**

Planner integration should:

- register inner generated-unit symbols
- register explicit FQNs from parsed fields as identities
- register pipeline-known symbols when complete identity is available
- pass registry plus inner-type set into the resolver

Do not:

- scan source directories
- touch Gradle APIs
- let templates participate in resolution

- [ ] **Step 4: Re-run focused planner tests**

Run the same command from Step 2.

Expected: PASS for symbol-aware planner output and failure cases.

- [ ] **Step 5: Commit planner integration**

Run:

```powershell
git add cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignArtifactPlanner.kt cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/types/DesignTypeRenderModelFactory.kt cap4k-plugin-pipeline-generator-design/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignArtifactPlannerTest.kt
git commit -m "feat: wire symbol aware imports into design planning"
```

### Task 4: Add Functional Coverage For Auto-Import Success And Failure

**Files:**
- Modify: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginFunctionalTest.kt`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-sample/iterate/design_gen.json`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-sample/codegen/templates/design/command.kt.peb`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-sample/codegen/templates/design/query.kt.peb`

- [ ] **Step 1: Add failing functional tests for safe import and ambiguity failure**

Add one success-path test and one failure-path test.

Success-path should assert:

- imported `LocalDateTime` or another safe external type appears in generated output
- conflicting external type remains FQN
- inner nested type is rendered without import

Failure-path should assert:

- build fails when a short-name type is ambiguous or unknown
- output contains a clear diagnostic mentioning the field and type

Example success assertions:

```kotlin
assertTrue(content.contains("import java.time.LocalDateTime"))
assertTrue(content.contains("val createdAt: LocalDateTime"))
assertTrue(content.contains("val requestStatus: com.foo.Status"))
assertTrue(content.contains("val address: Address?"))
```

Example failure assertion:

```kotlin
assertTrue(result.output.contains("ambiguous"))
assertTrue(result.output.contains("Status"))
```

- [ ] **Step 2: Run focused functional tests to verify failure**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-gradle:test --tests "com.only4.cap4k.plugin.pipeline.gradle.PipelinePluginFunctionalTest.cap4kGenerate renders symbol aware imports for design templates" --tests "com.only4.cap4k.plugin.pipeline.gradle.PipelinePluginFunctionalTest.cap4kGenerate fails on ambiguous short type" --rerun-tasks
```

Expected: FAIL because fixtures and functional coverage are not aligned yet.

- [ ] **Step 3: Update functional fixtures to exercise new import behavior**

Adjust the design fixture so it contains:

- a unique explicit external type suitable for safe import
- a conflict case that must remain fully qualified
- an inner nested type
- a dedicated ambiguous or unknown short-name case for failure coverage

Keep the fixture narrow and deterministic.

- [ ] **Step 4: Run end-to-end targeted verification**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-generator-design:test :cap4k-plugin-pipeline-renderer-pebble:test :cap4k-plugin-pipeline-gradle:test --rerun-tasks
```

Expected: PASS.

- [ ] **Step 5: Commit functional coverage**

Run:

```powershell
git add cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginFunctionalTest.kt cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-sample/iterate/design_gen.json cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-sample/codegen/templates/design/command.kt.peb cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-sample/codegen/templates/design/query.kt.peb
git commit -m "test: cover design auto import resolution"
```

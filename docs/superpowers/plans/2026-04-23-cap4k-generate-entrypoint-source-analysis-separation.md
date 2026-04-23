# Cap4k Generate Entrypoint Source/Analysis Separation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Split source-generation and analysis-export task families, make `designDomainEvent` aggregate-first with `kspMetadata` fallback, and keep `flow`/`drawingBoard` out of the main `cap4kPlan` / `cap4kGenerate` contract.

**Architecture:** Keep the existing pipeline kernel intact and solve the problem at the task-family boundary. The main entrypoints continue to build a `ProjectConfig`, but they only execute source-generation generators. A new analysis task family executes `flow` and `drawingBoard` against explicit `irAnalysis` inputs, preserving the old multi-module `inputDirs` contract and automatic `compileKotlin` inference. `designDomainEvent` is fixed inside canonical assembly by resolving aggregate metadata from current-run aggregate canonical data first and falling back to `kspMetadata` only when needed.

**Tech Stack:** Gradle plugin tasks, Kotlin, existing pipeline API/core/gradle modules, TestKit functional tests, Gson plan reports.

---

### Task 1: Lock the New Public Contract with Failing Gradle-Plugin Tests

**Files:**
- Modify: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginTest.kt`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kProjectConfigFactoryTest.kt`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginFunctionalTest.kt`

- [ ] **Step 1: Write the failing unit test for analysis task registration**

Add a new test in `PipelinePluginTest.kt` asserting that `cap4kAnalysisPlan` and `cap4kAnalysisGenerate` are registered and that `cap4kPlan` / `cap4kGenerate` still exist.

```kotlin
@Test
fun `plugin registers source and analysis task families`() {
    val project = ProjectBuilder.builder().build()
    project.pluginManager.apply("com.only4.cap4k.plugin.pipeline")

    assertNotNull(project.tasks.findByName("cap4kPlan"))
    assertNotNull(project.tasks.findByName("cap4kGenerate"))
    assertNotNull(project.tasks.findByName("cap4kAnalysisPlan"))
    assertNotNull(project.tasks.findByName("cap4kAnalysisGenerate"))
}
```

- [ ] **Step 2: Run the single test to verify it fails**

Run: `./gradlew :cap4k-plugin-pipeline-gradle:test --tests "com.only4.cap4k.plugin.pipeline.gradle.PipelinePluginTest.plugin registers source and analysis task families"`

Expected: FAIL because `cap4kAnalysisPlan` / `cap4kAnalysisGenerate` do not exist yet.

- [ ] **Step 3: Write the failing config-factory test for domain-event fallback semantics**

Add a new test in `Cap4kProjectConfigFactoryTest.kt` proving that `designDomainEvent` no longer hard-requires enabled `kspMetadata`.

```kotlin
@Test
fun `design domain event generator does not require enabled ksp metadata source`() {
    val project = ProjectBuilder.builder().build()
    project.pluginManager.apply("com.only4.cap4k.plugin.pipeline")
    val extension = project.extensions.getByType(Cap4kExtension::class.java)

    extension.project.basePackage.set("com.acme.demo")
    extension.project.domainModulePath.set("demo-domain")
    extension.sources.designJson.enabled.set(true)
    extension.generators.designDomainEvent.enabled.set(true)
    extension.sources.kspMetadata.enabled.set(false)

    val config = Cap4kProjectConfigFactory().build(project, extension)
    assertTrue(config.generators.containsKey("design-domain-event"))
    assertFalse(config.sources.containsKey("ksp-metadata"))
}
```

- [ ] **Step 4: Run the single config-factory test to verify it fails**

Run: `./gradlew :cap4k-plugin-pipeline-gradle:test --tests "com.only4.cap4k.plugin.pipeline.gradle.Cap4kProjectConfigFactoryTest.design domain event generator does not require enabled ksp metadata source"`

Expected: FAIL because validation currently throws `designDomainEvent generator requires enabled kspMetadata source.`

- [ ] **Step 5: Write the failing functional tests for analysis task family**

Add two new tests in `PipelinePluginFunctionalTest.kt`:

1. `cap4kAnalysisPlan and cap4kAnalysisGenerate produce flow artifacts from ir analysis fixture`
2. `cap4kPlan and cap4kGenerate ignore flow and drawing board generators`

Use the existing `flow-sample` and `drawing-board-sample` fixtures as a starting point.

```kotlin
@Test
fun `cap4kAnalysisPlan and cap4kAnalysisGenerate produce flow artifacts from ir analysis fixture`() {
    val projectDir = Files.createTempDirectory("pipeline-functional-analysis-flow")
    copyFixture(projectDir, "flow-sample")

    val planResult = GradleRunner.create()
        .withProjectDir(projectDir.toFile())
        .withPluginClasspath()
        .withArguments("cap4kAnalysisPlan", "cap4kAnalysisGenerate")
        .build()

    assertEquals(TaskOutcome.SUCCESS, planResult.task(":cap4kAnalysisGenerate")?.outcome)
    assertTrue(projectDir.resolve("build/cap4k/analysis-plan.json").toFile().exists())
    assertTrue(projectDir.resolve("flows/index.json").toFile().exists())
}
```

```kotlin
@Test
fun `cap4kPlan and cap4kGenerate ignore flow and drawing board generators`() {
    val projectDir = Files.createTempDirectory("pipeline-functional-main-ignores-analysis")
    copyFixture(projectDir, "flow-sample")

    val result = GradleRunner.create()
        .withProjectDir(projectDir.toFile())
        .withPluginClasspath()
        .withArguments("cap4kPlan", "cap4kGenerate")
        .build()

    assertEquals(TaskOutcome.SUCCESS, result.task(":cap4kGenerate")?.outcome)
    assertFalse(projectDir.resolve("flows/index.json").toFile().exists())
    assertFalse(projectDir.resolve("build/cap4k/analysis-plan.json").toFile().exists())
}
```

- [ ] **Step 6: Run the two functional tests to verify they fail**

Run: `./gradlew :cap4k-plugin-pipeline-gradle:test --tests "*cap4kAnalysisPlan and cap4kAnalysisGenerate produce flow artifacts from ir analysis fixture" --tests "*cap4kPlan and cap4kGenerate ignore flow and drawing board generators"`

Expected:
- first test FAILS because analysis tasks do not exist
- second test FAILS because `cap4kPlan` / `cap4kGenerate` still produce flow artifacts

- [ ] **Step 7: Commit the failing test baseline**

```bash
git add cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginTest.kt \
        cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kProjectConfigFactoryTest.kt \
        cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginFunctionalTest.kt
git commit -m "test: lock source and analysis task family boundary"
```

### Task 2: Add Analysis Plan/Generate Tasks and Task-Family Registration

**Files:**
- Create: `cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kAnalysisPlanTask.kt`
- Create: `cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kAnalysisGenerateTask.kt`
- Modify: `cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePlugin.kt`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginTest.kt`

- [ ] **Step 1: Write the failing task classes skeleton in tests first**

Extend `PipelinePluginTest.kt` to assert the concrete task types:

```kotlin
@Test
fun `analysis tasks use dedicated task classes`() {
    val project = ProjectBuilder.builder().build()
    project.pluginManager.apply("com.only4.cap4k.plugin.pipeline")

    assertTrue(project.tasks.named("cap4kAnalysisPlan").get() is Cap4kAnalysisPlanTask)
    assertTrue(project.tasks.named("cap4kAnalysisGenerate").get() is Cap4kAnalysisGenerateTask)
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :cap4k-plugin-pipeline-gradle:test --tests "com.only4.cap4k.plugin.pipeline.gradle.PipelinePluginTest.analysis tasks use dedicated task classes"`

Expected: FAIL because the task classes do not exist yet.

- [ ] **Step 3: Add `Cap4kAnalysisPlanTask`**

Create `Cap4kAnalysisPlanTask.kt` mirroring `Cap4kPlanTask`, but write to `build/cap4k/analysis-plan.json` and call the analysis runner factory.

```kotlin
abstract class Cap4kAnalysisPlanTask : DefaultTask() {
    @get:Internal
    lateinit var extension: Cap4kExtension

    @get:Internal
    lateinit var configFactory: Cap4kProjectConfigFactory

    @TaskAction
    fun runPlan() {
        val config = configFactory.build(project, extension)
        val outputFile = project.layout.buildDirectory.file("cap4k/analysis-plan.json").get().asFile
        outputFile.parentFile.mkdirs()
        val result = buildAnalysisRunner(project, config, exportEnabled = false).run(config)
        outputFile.writeText(
            GsonBuilder()
                .setPrettyPrinting()
                .serializeNulls()
                .create()
                .toJson(PlanReport(items = result.planItems, diagnostics = result.diagnostics))
        )
    }
}
```

- [ ] **Step 4: Add `Cap4kAnalysisGenerateTask`**

Create `Cap4kAnalysisGenerateTask.kt` as the generate companion.

```kotlin
abstract class Cap4kAnalysisGenerateTask : DefaultTask() {
    @get:Internal
    lateinit var extension: Cap4kExtension

    @get:Internal
    lateinit var configFactory: Cap4kProjectConfigFactory

    @TaskAction
    fun generate() {
        val config = configFactory.build(project, extension)
        buildAnalysisRunner(project, config, exportEnabled = true).run(config)
    }
}
```

- [ ] **Step 5: Register the new task family in `PipelinePlugin`**

Modify `PipelinePlugin.kt` to register the analysis tasks without removing the existing main task family.

```kotlin
val analysisPlanTask = project.tasks.register("cap4kAnalysisPlan", Cap4kAnalysisPlanTask::class.java) { task ->
    task.group = "cap4k"
    task.description = "Plans Cap4k analysis export artifacts."
    task.extension = extension
    task.configFactory = configFactory
}
val analysisGenerateTask = project.tasks.register("cap4kAnalysisGenerate", Cap4kAnalysisGenerateTask::class.java) { task ->
    task.group = "cap4k"
    task.description = "Generates artifacts from analysis snapshots."
    task.extension = extension
    task.configFactory = configFactory
}
```

- [ ] **Step 6: Run the registration tests to verify they pass**

Run: `./gradlew :cap4k-plugin-pipeline-gradle:test --tests "com.only4.cap4k.plugin.pipeline.gradle.PipelinePluginTest.plugin registers source and analysis task families" --tests "com.only4.cap4k.plugin.pipeline.gradle.PipelinePluginTest.analysis tasks use dedicated task classes"`

Expected: PASS

- [ ] **Step 7: Commit the new task-family registration**

```bash
git add cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kAnalysisPlanTask.kt \
        cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kAnalysisGenerateTask.kt \
        cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePlugin.kt \
        cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginTest.kt
git commit -m "feat: register dedicated analysis task family"
```

### Task 3: Split Runner Construction and Dependency Inference by Task Family

**Files:**
- Modify: `cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePlugin.kt`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginTest.kt`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginFunctionalTest.kt`

- [ ] **Step 1: Write the failing unit test for analysis compile inference**

Add a `PipelinePluginTest.kt` case that mirrors the current flow inference test, but targets `cap4kAnalysisPlan` / `cap4kAnalysisGenerate`.

```kotlin
@Test
fun `analysis tasks with ir analysis depend on relevant compile task only`() {
    val rootProjectDir = tempProjectDir("pipeline-plugin-analysis-root")
    val root = ProjectBuilder.builder().withProjectDir(rootProjectDir.toFile()).build()
    val analysisProject = ProjectBuilder.builder().withName("app").withParent(root).withProjectDir(rootProjectDir.resolve("app").toFile()).build()
    analysisProject.tasks.register("compileKotlin")
    root.pluginManager.apply("com.only4.cap4k.plugin.pipeline")

    val config = ProjectConfig(
        basePackage = "com.acme.demo",
        modules = emptyMap(),
        sources = mapOf(
            "ir-analysis" to SourceConfig(
                enabled = true,
                options = mapOf("inputDirs" to listOf(analysisProject.layout.buildDirectory.dir("cap4k-code-analysis").get().asFile.absolutePath))
            )
        ),
        generators = mapOf("flow" to GeneratorConfig(enabled = true))
    )

    val dependencies = inferDependencies(root, config)
    assertEquals(listOf("compileKotlin"), dependencies.map { it.name })
}
```

- [ ] **Step 2: Run the test to verify it currently fails or is not yet task-family aware**

Run: `./gradlew :cap4k-plugin-pipeline-gradle:test --tests "com.only4.cap4k.plugin.pipeline.gradle.PipelinePluginTest.analysis tasks with ir analysis depend on relevant compile task only"`

Expected: FAIL or pass for generic inference but without any analysis-task usage path yet. Keep the test in place as the contract anchor.

- [ ] **Step 3: Introduce explicit task-family runner builders**

Modify `PipelinePlugin.kt` so the existing `buildRunner(...)` becomes source-generation-only and add `buildAnalysisRunner(...)` for analysis generators only.

```kotlin
internal fun buildSourceRunner(project: Project, config: ProjectConfig, exportEnabled: Boolean): PipelineRunner {
    return DefaultPipelineRunner(
        sources = listOf(
            DbSchemaSourceProvider(),
            EnumManifestSourceProvider(),
            DesignJsonSourceProvider(),
            KspMetadataSourceProvider(),
        ),
        generators = listOf(
            DesignArtifactPlanner(),
            DesignQueryHandlerArtifactPlanner(),
            DesignClientArtifactPlanner(),
            DesignClientHandlerArtifactPlanner(),
            DesignValidatorArtifactPlanner(),
            DesignApiPayloadArtifactPlanner(),
            DesignDomainEventArtifactPlanner(),
            DesignDomainEventHandlerArtifactPlanner(),
            AggregateArtifactPlanner(),
        ),
        assembler = DefaultCanonicalAssembler(),
        renderer = PebbleArtifactRenderer(PresetTemplateResolver(config.templates.preset, config.templates.overrideDirs)),
        exporter = if (exportEnabled) FilesystemArtifactExporter(project.projectDir.toPath()) else NoopArtifactExporter(),
    )
}
```

```kotlin
internal fun buildAnalysisRunner(project: Project, config: ProjectConfig, exportEnabled: Boolean): PipelineRunner {
    return DefaultPipelineRunner(
        sources = listOf(IrAnalysisSourceProvider()),
        generators = listOf(
            DrawingBoardArtifactPlanner(),
            FlowArtifactPlanner(),
        ),
        assembler = DefaultCanonicalAssembler(),
        renderer = PebbleArtifactRenderer(PresetTemplateResolver(config.templates.preset, config.templates.overrideDirs)),
        exporter = if (exportEnabled) FilesystemArtifactExporter(project.projectDir.toPath()) else NoopArtifactExporter(),
    )
}
```

- [ ] **Step 4: Rewire existing tasks to use task-family-appropriate builders**

Update:
- `Cap4kPlanTask` to call `buildSourceRunner(...)`
- `Cap4kGenerateTask` to call `buildSourceRunner(...)`
- new analysis tasks to call `buildAnalysisRunner(...)`

```kotlin
val result = buildSourceRunner(project, config, exportEnabled = false).run(config)
```

```kotlin
buildSourceRunner(project, config, exportEnabled = true).run(config)
```

- [ ] **Step 5: Split dependency inference application by task family**

Keep current inference logic, but wire it to the correct tasks:

- KSP inference only attaches to `cap4kPlan` / `cap4kGenerate`
- `irAnalysis` compile inference only attaches to `cap4kAnalysisPlan` / `cap4kAnalysisGenerate`

```kotlin
val inferredSourceDependencies = inferSourceDependencies(project, config)
if (inferredSourceDependencies.isNotEmpty()) {
    planTask.configure { it.dependsOn(inferredSourceDependencies) }
    generateTask.configure { it.dependsOn(inferredSourceDependencies) }
}

val inferredAnalysisDependencies = inferAnalysisDependencies(project, config)
if (inferredAnalysisDependencies.isNotEmpty()) {
    analysisPlanTask.configure { it.dependsOn(inferredAnalysisDependencies) }
    analysisGenerateTask.configure { it.dependsOn(inferredAnalysisDependencies) }
}
```

- [ ] **Step 6: Run focused unit and functional tests**

Run:

```bash
./gradlew :cap4k-plugin-pipeline-gradle:test \
  --tests "com.only4.cap4k.plugin.pipeline.gradle.PipelinePluginTest.design domain event with ksp metadata depends on relevant ksp task only" \
  --tests "com.only4.cap4k.plugin.pipeline.gradle.PipelinePluginTest.flow with ir analysis depends on relevant compile task only" \
  --tests "com.only4.cap4k.plugin.pipeline.gradle.PipelinePluginTest.analysis tasks with ir analysis depend on relevant compile task only" \
  --tests "*cap4kAnalysisPlan and cap4kAnalysisGenerate produce flow artifacts from ir analysis fixture" \
  --tests "*cap4kPlan and cap4kGenerate ignore flow and drawing board generators"
```

Expected: PASS

- [ ] **Step 7: Commit the task-family runner split**

```bash
git add cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePlugin.kt \
        cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kPlanTask.kt \
        cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kGenerateTask.kt \
        cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kAnalysisPlanTask.kt \
        cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kAnalysisGenerateTask.kt \
        cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginTest.kt \
        cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginFunctionalTest.kt
git commit -m "feat: split source and analysis task execution lanes"
```

### Task 4: Remove Hard `kspMetadata` Requirement and Implement Aggregate-First Domain-Event Resolution

**Files:**
- Modify: `cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kProjectConfigFactory.kt`
- Modify: `cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssembler.kt`
- Modify: `cap4k-plugin-pipeline-core/src/test/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssemblerTest.kt`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kProjectConfigFactoryTest.kt`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginFunctionalTest.kt`

- [ ] **Step 1: Write the failing canonical-assembler tests for aggregate-first fallback order**

Add tests in `DefaultCanonicalAssemblerTest.kt`:

1. domain event resolves aggregate metadata from canonical aggregate entities when `KspMetadataSnapshot` is absent
2. domain event falls back to `KspMetadataSnapshot` when canonical aggregate entities are absent

```kotlin
@Test
fun `domain event resolves aggregate package from canonical aggregate entities before ksp metadata`() {
    val model = DefaultCanonicalAssembler().assemble(
        config = projectConfigWithDesignDomainEvent(),
        snapshots = listOf(
            designJsonDomainEventSnapshot(aggregate = "Order"),
            dbSnapshotForOrderAggregate(),
        )
    )

    assertEquals("Order", model.domainEvents.single().aggregateName)
    assertEquals("com.acme.demo.domain.aggregates.order", model.domainEvents.single().aggregatePackageName)
}
```

- [ ] **Step 2: Run the assembler tests to verify they fail**

Run: `./gradlew :cap4k-plugin-pipeline-core:test --tests "*domain event resolves aggregate package from canonical aggregate entities before ksp metadata*" --tests "*domain event falls back to ksp metadata when canonical aggregate data is absent*"`

Expected: FAIL because canonical assembly still resolves only from `kspMetadata`.

- [ ] **Step 3: Remove the hard config-validation requirement on `kspMetadata`**

Delete the current validation branch from `Cap4kProjectConfigFactory.validateGeneratorDependencies(...)`:

```kotlin
if (generators.designDomainEventEnabled && !sources.kspMetadataEnabled) {
    throw IllegalArgumentException("designDomainEvent generator requires enabled kspMetadata source.")
}
```

Update the corresponding config-factory test expectations so `designJson` remains required, but `kspMetadata` no longer is.

- [ ] **Step 4: Add current-run aggregate metadata lookup in `DefaultCanonicalAssembler`**

Refactor `DefaultCanonicalAssembler.kt` so aggregate-backed metadata is available before domain-event assembly.

Use a small local helper map built from canonical aggregate entities:

```kotlin
val aggregateEntityMetadata = entities
    .filter { it.aggregateRoot }
    .associateBy(
        keySelector = { it.name },
        valueTransform = { entity ->
            AggregateMetadataRecord(
                aggregateName = entity.name,
                rootQualifiedName = "${entity.packageName}.${entity.name}",
                rootPackageName = entity.packageName,
                rootClassName = entity.name,
            )
        }
    )
```

Then change domain-event resolution to:

```kotlin
val aggregate = aggregateEntityMetadata[aggregateName]
    ?: aggregateLookup[aggregateName]
    ?: throw IllegalArgumentException(
        "domain_event ${entry.name} references missing aggregate metadata: $aggregateName"
    )
```

If necessary, reorder canonical assembly so aggregate-backed entities are computed before `domainEvents`.

- [ ] **Step 5: Run focused tests for config factory and assembler**

Run:

```bash
./gradlew :cap4k-plugin-pipeline-core:test \
  --tests "com.only4.cap4k.plugin.pipeline.core.DefaultCanonicalAssemblerTest.*domain event*" \
  :cap4k-plugin-pipeline-gradle:test \
  --tests "com.only4.cap4k.plugin.pipeline.gradle.Cap4kProjectConfigFactoryTest.design domain event generator does not require enabled ksp metadata source"
```

Expected: PASS

- [ ] **Step 6: Update functional tests for the new fallback contract**

Replace the existing functional test that expects:

- `designDomainEvent generator requires enabled kspMetadata source.`

with two new functional cases:

1. `cap4kPlan domain event flow succeeds without ksp metadata when aggregate source data exists`
2. `cap4kPlan domain event flow still fails clearly when neither aggregate data nor ksp metadata can resolve aggregate`

Use an aggregate-enabled fixture for the first case and the existing domain-event-only fixture for the second.

```kotlin
assertTrue(result.output.contains("domain_event OrderCreated references missing aggregate metadata: Order"))
```

- [ ] **Step 7: Commit the domain-event fallback behavior**

```bash
git add cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kProjectConfigFactory.kt \
        cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssembler.kt \
        cap4k-plugin-pipeline-core/src/test/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssemblerTest.kt \
        cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kProjectConfigFactoryTest.kt \
        cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginFunctionalTest.kt
git commit -m "feat: make domain event aggregate resolution aggregate-first"
```

### Task 5: Move Flow/DrawingBoard Functional Coverage to Analysis Tasks and Update README

**Files:**
- Modify: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginFunctionalTest.kt`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginCompileFunctionalTest.kt`
- Modify: `cap4k-plugin-pipeline-gradle/README.md`

- [ ] **Step 1: Rewrite the flow/drawing-board functional commands to use analysis tasks**

Update the existing functional tests around:

- `cap4kPlan and cap4kGenerate produce flow artifacts from ir analysis fixture`
- `cap4kPlan and cap4kGenerate produce drawing board artifacts from ir analysis fixture`
- `cap4kPlan depends on compileKotlin when flow input is produced during compilation`
- `wrapper task depending on cap4kGenerate still infers compileKotlin dependency`

to use:

- `cap4kAnalysisPlan`
- `cap4kAnalysisGenerate`

Example change:

```kotlin
val result = GradleRunner.create()
    .withProjectDir(projectDir.toFile())
    .withPluginClasspath()
    .withArguments("cap4kAnalysisPlan", "cap4kAnalysisGenerate")
    .build()

assertTrue(projectDir.resolve("build/cap4k/analysis-plan.json").toFile().exists())
assertTrue(projectDir.resolve("flows/index.json").toFile().exists())
```

- [ ] **Step 2: Update compile-level functional coverage**

If any compile functional test implicitly assumes analysis export is part of `cap4kGenerate`, rewrite it to explicitly invoke the analysis task family.

Example:

```kotlin
val analysisResult = fixture.runner(projectDir, "cap4kAnalysisGenerate").build()
assertEquals(TaskOutcome.SUCCESS, analysisResult.task(":cap4kAnalysisGenerate")?.outcome)
```

- [ ] **Step 3: Rewrite the README task and dependency sections**

Update `README.md` so it no longer presents a single mixed generator family.

At minimum:

1. Replace the current generator dependency table with either two tables or one table with a task-family column.
2. Document:
   - `cap4kPlan` / `cap4kGenerate` as source-generation tasks
   - `cap4kAnalysisPlan` / `cap4kAnalysisGenerate` as analysis-export tasks
3. Change `designDomainEvent` docs to:
   - requires `designJson`
   - resolves aggregate metadata from current-run aggregate data first
   - uses `kspMetadata` as compatibility fallback

Use wording like:

```md
- `cap4kPlan` / `cap4kGenerate`: source-generation entrypoints
- `cap4kAnalysisPlan` / `cap4kAnalysisGenerate`: analysis-export entrypoints for `flow` and `drawingBoard`
```

```md
| `designDomainEvent` | `designJson` source; aggregate metadata resolves from current-run aggregate canonical data first, then `kspMetadata` fallback |
| `flow` | analysis task family + `irAnalysis` source |
| `drawingBoard` | analysis task family + `irAnalysis` source |
```

- [ ] **Step 4: Run focused functional and compile-level tests**

Run:

```bash
./gradlew :cap4k-plugin-pipeline-gradle:test \
  --tests "*cap4kAnalysisPlan and cap4kAnalysisGenerate produce flow artifacts from ir analysis fixture" \
  --tests "*cap4kAnalysisPlan and cap4kAnalysisGenerate produce drawing board artifacts from ir analysis fixture" \
  --tests "*cap4kAnalysisPlan depends on compileKotlin when flow input is produced during compilation" \
  --tests "*wrapper task depending on cap4kAnalysisGenerate still infers compileKotlin dependency" \
  --tests "*cap4kPlan and cap4kGenerate ignore flow and drawing board generators"
```

Expected: PASS

- [ ] **Step 5: Run the broader gradle-plugin regression slice**

Run:

```bash
./gradlew :cap4k-plugin-pipeline-gradle:test \
  --tests "com.only4.cap4k.plugin.pipeline.gradle.PipelinePluginTest" \
  --tests "com.only4.cap4k.plugin.pipeline.gradle.Cap4kProjectConfigFactoryTest" \
  --tests "com.only4.cap4k.plugin.pipeline.gradle.PipelinePluginFunctionalTest"
```

Expected: PASS

- [ ] **Step 6: Commit the README and functional contract alignment**

```bash
git add cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginFunctionalTest.kt \
        cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginCompileFunctionalTest.kt \
        cap4k-plugin-pipeline-gradle/README.md
git commit -m "docs: separate source and analysis entrypoint contract"
```

### Task 6: Final Verification and Branch Hygiene

**Files:**
- Modify: none expected
- Verify: full touched surface only

- [ ] **Step 1: Run the core and Gradle plugin verification set**

Run:

```bash
./gradlew \
  :cap4k-plugin-pipeline-core:test \
  :cap4k-plugin-pipeline-generator-design:test \
  :cap4k-plugin-pipeline-generator-flow:test \
  :cap4k-plugin-pipeline-generator-drawing-board:test \
  :cap4k-plugin-pipeline-gradle:test
```

Expected: PASS

- [ ] **Step 2: Run whitespace and worktree sanity checks**

Run:

```bash
git diff --check
git status --short
```

Expected:
- `git diff --check` prints nothing
- `git status --short` only shows intentional final changes before commit, then clean after commit

- [ ] **Step 3: Commit final stabilization if needed**

If any final doc/test normalization changes were needed:

```bash
git add -A
git commit -m "test: finalize source and analysis entrypoint separation"
```

- [ ] **Step 4: Record verification commands in the final handoff**

Include these exact commands in the completion summary:

```bash
./gradlew :cap4k-plugin-pipeline-core:test :cap4k-plugin-pipeline-generator-design:test :cap4k-plugin-pipeline-generator-flow:test :cap4k-plugin-pipeline-generator-drawing-board:test :cap4k-plugin-pipeline-gradle:test
git diff --check
```

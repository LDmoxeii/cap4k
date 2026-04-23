# Design Family Canonical + Provider Normalization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Normalize the `cmd/qry/cli` design family in one slice by replacing `RequestModel + RequestKind` with typed canonical models and renaming the public design request-family providers to honest final ids.

**Architecture:** Keep `designJson` unchanged at the source layer. Move `query` variant resolution into canonical assembly, introduce explicit canonical collections (`commands`, `queries`, `clients`), then split generator entrypoints and Gradle DSL/provider ids to match that canonical truth. Shared payload render machinery stays single-source; only the coarse semantic entry model is removed.

**Tech Stack:** Kotlin, Gradle plugin code, Pebble renderer presets, JUnit 5

---

## File Map

### API / Canonical model files

- Modify: `cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModels.kt`
- Modify: `cap4k-plugin-pipeline-api/src/test/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModelsTest.kt`

### Canonical assembly files

- Modify: `cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssembler.kt`
- Modify: `cap4k-plugin-pipeline-core/src/test/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssemblerTest.kt`

### Design generator files

- Delete: `cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignArtifactPlanner.kt`
- Delete: `cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignQueryVariantResolver.kt`
- Create: `cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignCommandArtifactPlanner.kt`
- Create: `cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignQueryArtifactPlanner.kt`
- Modify or Move: `cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignRenderModelFactory.kt`
- Modify: `cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignClientArtifactPlanner.kt`
- Modify: `cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignQueryHandlerArtifactPlanner.kt`
- Modify: `cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignClientHandlerArtifactPlanner.kt`
- Modify: `cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignQueryHandlerRenderModels.kt`
- Modify: `cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignClientHandlerRenderModels.kt`
- Delete: `cap4k-plugin-pipeline-generator-design/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignArtifactPlannerTest.kt`
- Create: `cap4k-plugin-pipeline-generator-design/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignCommandArtifactPlannerTest.kt`
- Create: `cap4k-plugin-pipeline-generator-design/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignQueryArtifactPlannerTest.kt`
- Modify: `cap4k-plugin-pipeline-generator-design/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignClientArtifactPlannerTest.kt`
- Modify: `cap4k-plugin-pipeline-generator-design/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignQueryHandlerArtifactPlannerTest.kt`
- Modify: `cap4k-plugin-pipeline-generator-design/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignClientHandlerArtifactPlannerTest.kt`
- Modify: `cap4k-plugin-pipeline-generator-design/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignValidatorArtifactPlannerTest.kt`
- Modify: `cap4k-plugin-pipeline-generator-design/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignApiPayloadArtifactPlannerTest.kt`

### Gradle / public contract files

- Modify: `cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kExtension.kt`
- Modify: `cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kProjectConfigFactory.kt`
- Modify: `cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePlugin.kt`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kProjectConfigFactoryTest.kt`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginTest.kt`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginFunctionalTest.kt`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginCompileFunctionalTest.kt`
- Modify fixtures:
  - `cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-sample/build.gradle.kts`
  - `cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-compile-sample/build.gradle.kts`
  - `cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-integrated-compile-sample/build.gradle.kts`
  - `cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-default-value-invalid-sample/build.gradle.kts`
  - `cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-type-registry-sample/build.gradle.kts`
  - `cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-manifest-sample/build.gradle.kts`

### Renderer / contract tests

- Modify: `cap4k-plugin-pipeline-renderer-pebble/src/test/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PebbleArtifactRendererTest.kt`
- Modify: `cap4k-plugin-pipeline-core/src/test/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultPipelineRunnerTest.kt`

### Documentation

- Modify: `cap4k-plugin-pipeline-gradle/README.md`

---

### Task 1: Lock the new canonical contract with failing tests

**Files:**
- Modify: `cap4k-plugin-pipeline-api/src/test/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModelsTest.kt`
- Modify: `cap4k-plugin-pipeline-core/src/test/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssemblerTest.kt`

- [ ] **Step 1: Add API-level tests for the new canonical shapes**

Add tests that assert the new model surface exists and the old mixed `requests` collection is gone. Use concrete model construction so later refactors cannot keep `RequestModel` around silently.

```kotlin
@Test
fun `canonical model stores commands queries and clients separately`() {
    val model = CanonicalModel(
        commands = listOf(
            CommandModel(
                packageName = "orders",
                typeName = "CreateOrderCmd",
                description = "create order",
                aggregateRef = AggregateRef(name = "Order", packageName = "com.acme.demo.domain.aggregates.order"),
                requestFields = listOf(FieldModel(name = "id", type = "Long")),
                responseFields = emptyList(),
                variant = CommandVariant.DEFAULT,
            )
        ),
        queries = listOf(
            QueryModel(
                packageName = "orders",
                typeName = "ListOrderQry",
                description = "list order",
                aggregateRef = AggregateRef(name = "Order", packageName = "com.acme.demo.domain.aggregates.order"),
                requestFields = emptyList(),
                responseFields = emptyList(),
                variant = QueryVariant.LIST,
            )
        ),
        clients = listOf(
            ClientModel(
                packageName = "remote",
                typeName = "SyncStockCli",
                description = "sync stock",
                aggregateRef = null,
                requestFields = emptyList(),
                responseFields = emptyList(),
            )
        ),
    )

    assertEquals(1, model.commands.size)
    assertEquals(1, model.queries.size)
    assertEquals(1, model.clients.size)
}
```

- [ ] **Step 2: Add canonical assembler tests for `cmd/qry/cli` separation and query variants**

Extend `DefaultCanonicalAssemblerTest.kt` with a focused test that feeds three design entries and asserts they land in typed canonical collections with the right variants.

```kotlin
@Test
fun `assembler splits cmd qry cli into typed canonical collections`() {
    val result = assembler.assemble(
        config = projectConfig(),
        snapshots = listOf(
            DesignSpecSnapshot(
                entries = listOf(
                    designEntry(tag = "cmd", packageName = "order", name = "CreateOrder"),
                    designEntry(tag = "qry", packageName = "order", name = "ListOrder"),
                    designEntry(tag = "cli", packageName = "remote", name = "SyncStock"),
                )
            )
        )
    )

    assertEquals(listOf("CreateOrderCmd"), result.model.commands.map { it.typeName })
    assertEquals(listOf("ListOrderQry"), result.model.queries.map { it.typeName })
    assertEquals(listOf("SyncStockCli"), result.model.clients.map { it.typeName })
    assertEquals(QueryVariant.LIST, result.model.queries.single().variant)
    assertEquals(CommandVariant.DEFAULT, result.model.commands.single().variant)
}
```

- [ ] **Step 3: Add a dedicated query variant test**

Also add one focused variant test so later renames do not break canonical query classification silently.

```kotlin
@Test
fun `assembler resolves page list and default query variants canonically`() {
    val result = assembler.assemble(
        config = projectConfig(),
        snapshots = listOf(
            DesignSpecSnapshot(
                entries = listOf(
                    designEntry(tag = "qry", packageName = "order", name = "FindOrder"),
                    designEntry(tag = "qry", packageName = "order", name = "ListOrder"),
                    designEntry(tag = "qry", packageName = "order", name = "PageOrder"),
                )
            )
        )
    )

    assertEquals(
        listOf(QueryVariant.DEFAULT, QueryVariant.LIST, QueryVariant.PAGE),
        result.model.queries.map { it.variant },
    )
}
```

- [ ] **Step 4: Run the focused tests and confirm they fail**

Run:

```bash
./gradlew :cap4k-plugin-pipeline-api:test --tests "*PipelineModelsTest" :cap4k-plugin-pipeline-core:test --tests "*DefaultCanonicalAssemblerTest"
```

Expected:
- test compile or runtime failure because `CommandModel`, `QueryModel`, `ClientModel`, `AggregateRef`, `QueryVariant`, and `CommandVariant` do not exist yet
- failures mentioning `CanonicalModel.requests` or `RequestModel` still being the active contract

- [ ] **Step 5: Commit the failing test scaffold**

```bash
git add cap4k-plugin-pipeline-api/src/test/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModelsTest.kt cap4k-plugin-pipeline-core/src/test/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssemblerTest.kt
git commit -m "test: lock typed design canonical contract"
```

### Task 2: Replace `RequestModel + RequestKind` with typed canonical models

**Files:**
- Modify: `cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModels.kt`
- Modify: `cap4k-plugin-pipeline-api/src/test/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModelsTest.kt`
- Modify: `cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssembler.kt`
- Modify: `cap4k-plugin-pipeline-core/src/test/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssemblerTest.kt`

- [ ] **Step 1: Replace the public API types**

Edit `PipelineModels.kt` so the old request contract disappears and the new canonical request-family models become the only public truth.

Key code to add:

```kotlin
data class AggregateRef(
    val name: String,
    val packageName: String,
)

sealed interface DesignInteractionModel {
    val packageName: String
    val typeName: String
    val description: String
    val aggregateRef: AggregateRef?
    val requestFields: List<FieldModel>
    val responseFields: List<FieldModel>
}

enum class CommandVariant {
    DEFAULT,
    VOID,
}

enum class QueryVariant {
    DEFAULT,
    LIST,
    PAGE,
}

data class CommandModel(
    override val packageName: String,
    override val typeName: String,
    override val description: String,
    override val aggregateRef: AggregateRef?,
    override val requestFields: List<FieldModel> = emptyList(),
    override val responseFields: List<FieldModel> = emptyList(),
    val variant: CommandVariant,
) : DesignInteractionModel
```

```kotlin
data class QueryModel(
    override val packageName: String,
    override val typeName: String,
    override val description: String,
    override val aggregateRef: AggregateRef?,
    override val requestFields: List<FieldModel> = emptyList(),
    override val responseFields: List<FieldModel> = emptyList(),
    val variant: QueryVariant,
) : DesignInteractionModel

data class ClientModel(
    override val packageName: String,
    override val typeName: String,
    override val description: String,
    override val aggregateRef: AggregateRef?,
    override val requestFields: List<FieldModel> = emptyList(),
    override val responseFields: List<FieldModel> = emptyList(),
) : DesignInteractionModel
```

Update `CanonicalModel` to:

```kotlin
data class CanonicalModel(
    val commands: List<CommandModel> = emptyList(),
    val queries: List<QueryModel> = emptyList(),
    val clients: List<ClientModel> = emptyList(),
    val validators: List<ValidatorModel> = emptyList(),
    val domainEvents: List<DomainEventModel> = emptyList(),
    ...
)
```

Delete:

```kotlin
enum class RequestKind ...
data class RequestModel ...
val requests: List<RequestModel>
```

- [ ] **Step 2: Move `cmd/qry/cli` semantic resolution into `DefaultCanonicalAssembler`**

Replace the current `requests = ...` block with separate `commands`, `queries`, and `clients` assembly.

Use helpers so the semantic decisions live in one place:

```kotlin
private fun toCommandModel(entry: DesignSpecEntry, aggregateRef: AggregateRef?): CommandModel =
    CommandModel(
        packageName = entry.packageName,
        typeName = "${entry.name}Cmd",
        description = entry.description,
        aggregateRef = aggregateRef,
        requestFields = entry.requestFields,
        responseFields = entry.responseFields,
        variant = CommandVariant.DEFAULT,
    )

private fun toQueryModel(entry: DesignSpecEntry, aggregateRef: AggregateRef?): QueryModel =
    QueryModel(
        packageName = entry.packageName,
        typeName = "${entry.name}Qry",
        description = entry.description,
        aggregateRef = aggregateRef,
        requestFields = entry.requestFields,
        responseFields = entry.responseFields,
        variant = resolveQueryVariant(entry.name),
    )

private fun toClientModel(entry: DesignSpecEntry, aggregateRef: AggregateRef?): ClientModel =
    ClientModel(
        packageName = entry.packageName,
        typeName = "${entry.name}Cli",
        description = entry.description,
        aggregateRef = aggregateRef,
        requestFields = entry.requestFields,
        responseFields = entry.responseFields,
    )
```

Add:

```kotlin
private fun resolveQueryVariant(name: String): QueryVariant =
    when {
        name.endsWith("Page", ignoreCase = false) -> QueryVariant.PAGE
        name.endsWith("List", ignoreCase = false) -> QueryVariant.LIST
        else -> QueryVariant.DEFAULT
    }
```

Use one `AggregateRef` conversion helper:

```kotlin
private fun AggregateMetadataRecord.toAggregateRef(): AggregateRef =
    AggregateRef(name = aggregateName, packageName = rootPackageName)
```

- [ ] **Step 3: Update the tests to the new API**

Replace old test data construction:

```kotlin
RequestModel(
    kind = RequestKind.COMMAND,
    ...
)
```

with:

```kotlin
CommandModel(
    packageName = "orders",
    typeName = "CreateOrderCmd",
    description = "create order",
    aggregateRef = AggregateRef("Order", "com.acme.demo.domain.aggregates.order"),
    requestFields = listOf(FieldModel("id", "Long")),
    responseFields = emptyList(),
    variant = CommandVariant.DEFAULT,
)
```

and replace old assertions:

```kotlin
assertEquals(RequestKind.QUERY, model.requests.single().kind)
```

with:

```kotlin
assertEquals(QueryVariant.PAGE, model.queries.single().variant)
assertEquals("PageOrderQry", model.queries.single().typeName)
```

- [ ] **Step 4: Run the canonical/API tests again**

Run:

```bash
./gradlew :cap4k-plugin-pipeline-api:test --tests "*PipelineModelsTest" :cap4k-plugin-pipeline-core:test --tests "*DefaultCanonicalAssemblerTest"
```

Expected:
- PASS

- [ ] **Step 5: Commit the API + assembler rewrite**

```bash
git add cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModels.kt cap4k-plugin-pipeline-api/src/test/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModelsTest.kt cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssembler.kt cap4k-plugin-pipeline-core/src/test/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssemblerTest.kt
git commit -m "refactor: introduce typed design canonical models"
```

### Task 3: Split the design request-family planners around typed canonical inputs

**Files:**
- Delete: `cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignArtifactPlanner.kt`
- Delete: `cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignQueryVariantResolver.kt`
- Create: `cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignCommandArtifactPlanner.kt`
- Create: `cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignQueryArtifactPlanner.kt`
- Modify or Move: `cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignRenderModelFactory.kt`
- Modify: `cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignClientArtifactPlanner.kt`
- Modify: `cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignQueryHandlerArtifactPlanner.kt`
- Modify: `cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignClientHandlerArtifactPlanner.kt`
- Modify: `cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignQueryHandlerRenderModels.kt`
- Modify: `cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignClientHandlerRenderModels.kt`
- Delete: `cap4k-plugin-pipeline-generator-design/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignArtifactPlannerTest.kt`
- Create: `cap4k-plugin-pipeline-generator-design/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignCommandArtifactPlannerTest.kt`
- Create: `cap4k-plugin-pipeline-generator-design/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignQueryArtifactPlannerTest.kt`
- Modify: `cap4k-plugin-pipeline-generator-design/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignClientArtifactPlannerTest.kt`
- Modify: `cap4k-plugin-pipeline-generator-design/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignQueryHandlerArtifactPlannerTest.kt`
- Modify: `cap4k-plugin-pipeline-generator-design/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignClientHandlerArtifactPlannerTest.kt`
- Modify: `cap4k-plugin-pipeline-generator-design/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignValidatorArtifactPlannerTest.kt`
- Modify: `cap4k-plugin-pipeline-generator-design/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignApiPayloadArtifactPlannerTest.kt`

- [ ] **Step 1: Add failing planner tests for the new provider split**

Create focused command/query tests instead of keeping one mixed `DesignArtifactPlannerTest.kt`.

Command test skeleton:

```kotlin
@Test
fun `designCommand plans only command artifacts`() {
    val planner = DesignCommandArtifactPlanner()
    val plan = planner.plan(
        config = projectConfig(generators = mapOf("design-command" to GeneratorConfig(enabled = true))),
        model = CanonicalModel(
            commands = listOf(commandModel(typeName = "CreateOrderCmd")),
            queries = listOf(queryModel(typeName = "ListOrderQry", variant = QueryVariant.LIST)),
            clients = listOf(clientModel(typeName = "SyncStockCli")),
        ),
    )

    assertEquals(1, plan.size)
    assertEquals("design-command", plan.single().generatorId)
    assertTrue(plan.single().outputPath.contains("/application/commands/"))
    assertEquals("design/command.kt.peb", plan.single().templateId)
}
```

Query test skeleton:

```kotlin
@Test
fun `designQuery plans page and list query templates from canonical variant`() {
    val planner = DesignQueryArtifactPlanner()
    val plan = planner.plan(
        config = projectConfig(generators = mapOf("design-query" to GeneratorConfig(enabled = true))),
        model = CanonicalModel(
            queries = listOf(
                queryModel(typeName = "FindOrderQry", variant = QueryVariant.DEFAULT),
                queryModel(typeName = "ListOrderQry", variant = QueryVariant.LIST),
                queryModel(typeName = "PageOrderQry", variant = QueryVariant.PAGE),
            )
        ),
    )

    assertEquals(
        listOf("design/query.kt.peb", "design/query_list.kt.peb", "design/query_page.kt.peb"),
        plan.map { it.templateId },
    )
}
```

- [ ] **Step 2: Replace `DesignArtifactPlanner` with command/query-specific planners**

Create `DesignCommandArtifactPlanner.kt`:

```kotlin
class DesignCommandArtifactPlanner : GeneratorProvider {
    override val id: String = "design-command"

    override fun plan(config: ProjectConfig, model: CanonicalModel): List<ArtifactPlanItem> {
        val applicationRoot = requireRelativeModuleRoot(config, "application")
        val basePath = config.basePackage.replace(".", "/")
        return model.commands.map { command ->
            val packagePath = command.packageName.replace(".", "/")
            ArtifactPlanItem(
                generatorId = id,
                moduleRole = "application",
                templateId = "design/command.kt.peb",
                outputPath = "$applicationRoot/src/main/kotlin/$basePath/application/commands/$packagePath/${command.typeName}.kt",
                context = DesignPayloadRenderModelFactory.create(
                    packageName = "${config.basePackage}.application.commands.${command.packageName}",
                    interaction = command,
                    typeRegistry = config.typeRegistry,
                    siblingTypeNames = model.commands.asSequence()
                        .filter { it !== command && it.packageName == command.packageName }
                        .map { it.typeName }
                        .toSet(),
                ).toContextMap(),
                conflictPolicy = config.templates.conflictPolicy,
            )
        }
    }
}
```

Create `DesignQueryArtifactPlanner.kt`:

```kotlin
class DesignQueryArtifactPlanner : GeneratorProvider {
    override val id: String = "design-query"

    override fun plan(config: ProjectConfig, model: CanonicalModel): List<ArtifactPlanItem> {
        val applicationRoot = requireRelativeModuleRoot(config, "application")
        val basePath = config.basePackage.replace(".", "/")
        return model.queries.map { query ->
            val packagePath = query.packageName.replace(".", "/")
            ArtifactPlanItem(
                generatorId = id,
                moduleRole = "application",
                templateId = when (query.variant) {
                    QueryVariant.DEFAULT -> "design/query.kt.peb"
                    QueryVariant.LIST -> "design/query_list.kt.peb"
                    QueryVariant.PAGE -> "design/query_page.kt.peb"
                },
                outputPath = "$applicationRoot/src/main/kotlin/$basePath/application/queries/$packagePath/${query.typeName}.kt",
                context = DesignPayloadRenderModelFactory.create(
                    packageName = "${config.basePackage}.application.queries.${query.packageName}",
                    interaction = query,
                    typeRegistry = config.typeRegistry,
                    siblingTypeNames = model.queries.asSequence()
                        .filter { it !== query && it.packageName == query.packageName }
                        .map { it.typeName }
                        .toSet(),
                ).toContextMap(),
                conflictPolicy = config.templates.conflictPolicy,
            )
        }
    }
}
```

- [ ] **Step 3: Rename and narrow the shared render-model factory**

Move `DesignRenderModelFactory.kt` to `DesignPayloadRenderModelFactory.kt` and change the main entry so it consumes the shared payload interface instead of `RequestModel`.

Key signature:

```kotlin
internal object DesignPayloadRenderModelFactory {
    fun create(
        packageName: String,
        interaction: DesignInteractionModel,
        typeRegistry: Map<String, String> = emptyMap(),
        siblingTypeNames: Set<String> = emptySet(),
    ): DesignRenderModel {
        val requestNamespace = buildNamespace(interaction.requestFields, "request")
        val responseNamespace = buildNamespace(interaction.responseFields, "response")
        return createRenderModel(
            packageName = packageName,
            typeName = interaction.typeName,
            description = interaction.description,
            aggregateName = interaction.aggregateRef?.name,
            aggregatePackageName = interaction.aggregateRef?.packageName,
            requestNamespace = requestNamespace,
            responseNamespace = responseNamespace,
            typeRegistry = typeRegistry,
            siblingRequestTypeNames = siblingTypeNames,
        )
    }
}
```

Keep `createForApiPayload` and `createForDomainEvent` intact; only update them to call the renamed object.

- [ ] **Step 4: Update client and handler planners to consume typed canonical collections directly**

Replace patterns like:

```kotlin
model.requests.filter { it.kind == RequestKind.CLIENT }
```

with:

```kotlin
model.clients
```

and:

```kotlin
model.requests.filter { it.kind == RequestKind.QUERY }
```

with:

```kotlin
model.queries
```

Then update render-model helpers:

```kotlin
internal object DesignQueryHandlerRenderModels {
    fun create(basePackage: String, query: QueryModel): DesignQueryHandlerRenderModel { ... }
}

internal object DesignClientHandlerRenderModels {
    fun create(basePackage: String, client: ClientModel): DesignClientHandlerRenderModel { ... }
}
```

- [ ] **Step 5: Remove generator-time query guessing**

Delete `DesignQueryVariantResolver.kt` and remove every call site. The only template selection source for query requests must now be `QueryModel.variant`.

- [ ] **Step 6: Run the focused generator tests**

Run:

```bash
./gradlew :cap4k-plugin-pipeline-generator-design:test --tests "*DesignCommandArtifactPlannerTest" --tests "*DesignQueryArtifactPlannerTest" --tests "*DesignClientArtifactPlannerTest" --tests "*DesignQueryHandlerArtifactPlannerTest" --tests "*DesignClientHandlerArtifactPlannerTest" --tests "*DesignValidatorArtifactPlannerTest" --tests "*DesignApiPayloadArtifactPlannerTest"
```

Expected:
- PASS

- [ ] **Step 7: Commit the generator split**

```bash
git add cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design cap4k-plugin-pipeline-generator-design/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/design
git commit -m "refactor: split design request-family generators"
```

### Task 4: Rename the public DSL/provider contract and update fixtures, renderer tests, and README

**Files:**
- Modify: `cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kExtension.kt`
- Modify: `cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kProjectConfigFactory.kt`
- Modify: `cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePlugin.kt`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kProjectConfigFactoryTest.kt`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginTest.kt`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginFunctionalTest.kt`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginCompileFunctionalTest.kt`
- Modify fixtures:
  - `cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-sample/build.gradle.kts`
  - `cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-compile-sample/build.gradle.kts`
  - `cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-integrated-compile-sample/build.gradle.kts`
  - `cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-default-value-invalid-sample/build.gradle.kts`
  - `cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-type-registry-sample/build.gradle.kts`
  - `cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-manifest-sample/build.gradle.kts`
- Modify: `cap4k-plugin-pipeline-renderer-pebble/src/test/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PebbleArtifactRendererTest.kt`
- Modify: `cap4k-plugin-pipeline-core/src/test/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultPipelineRunnerTest.kt`
- Modify: `cap4k-plugin-pipeline-gradle/README.md`

- [ ] **Step 1: Rename the Gradle DSL blocks to final public names**

Update `Cap4kExtension.kt` so the generator DSL becomes:

```kotlin
open class Cap4kGeneratorsExtension @Inject constructor(objects: ObjectFactory) {
    val designCommand: DesignCommandGeneratorExtension =
        objects.newInstance(DesignCommandGeneratorExtension::class.java)
    val designQuery: DesignQueryGeneratorExtension =
        objects.newInstance(DesignQueryGeneratorExtension::class.java)
    val designQueryHandler: DesignQueryHandlerGeneratorExtension =
        objects.newInstance(DesignQueryHandlerGeneratorExtension::class.java)
    val designClient: DesignClientGeneratorExtension =
        objects.newInstance(DesignClientGeneratorExtension::class.java)
    val designClientHandler: DesignClientHandlerGeneratorExtension =
        objects.newInstance(DesignClientHandlerGeneratorExtension::class.java)
    ...
}
```

and the block functions become:

```kotlin
fun designCommand(block: DesignCommandGeneratorExtension.() -> Unit) { designCommand.block() }
fun designQuery(block: DesignQueryGeneratorExtension.() -> Unit) { designQuery.block() }
fun designQueryHandler(block: DesignQueryHandlerGeneratorExtension.() -> Unit) { designQueryHandler.block() }
fun designClient(block: DesignClientGeneratorExtension.() -> Unit) { designClient.block() }
fun designClientHandler(block: DesignClientHandlerGeneratorExtension.() -> Unit) { designClientHandler.block() }
```

Do not keep `fun design(...)` on the final branch.

- [ ] **Step 2: Rename generator-state/config wiring**

Update `Cap4kProjectConfigFactory.kt` so:

- `designEnabled` becomes `designCommandEnabled` plus `designQueryEnabled`
- `buildGenerators(...)` emits:
  - `"design-command"`
  - `"design-query"`
  - `"design-query-handler"`
  - `"design-client"`
  - `"design-client-handler"`
- dependency rules become:

```kotlin
if (generators.designCommandEnabled && !sources.designJsonEnabled) {
    throw IllegalArgumentException("designCommand generator requires enabled designJson source.")
}
if (generators.designQueryEnabled && !sources.designJsonEnabled) {
    throw IllegalArgumentException("designQuery generator requires enabled designJson source.")
}
if (generators.designQueryHandlerEnabled && !generators.designQueryEnabled) {
    throw IllegalArgumentException("designQueryHandler generator requires enabled designQuery generator.")
}
if (generators.designClientHandlerEnabled && !generators.designClientEnabled) {
    throw IllegalArgumentException("designClientHandler generator requires enabled designClient generator.")
}
```

Update module validation in the same style:
- `designCommand` -> application module required
- `designQuery` -> application module required
- `designQueryHandler` -> adapter module required
- `designClient` -> application module required
- `designClientHandler` -> adapter module required

- [ ] **Step 3: Update provider registration and dependency inference**

Change `PipelinePlugin.kt` to instantiate the new planners and stop registering the removed ones.

Key changes:

```kotlin
generators = listOf(
    DesignCommandArtifactPlanner(),
    DesignQueryArtifactPlanner(),
    DesignQueryHandlerArtifactPlanner(),
    DesignClientArtifactPlanner(),
    DesignClientHandlerArtifactPlanner(),
    ...
)
```

Update `hasEnabledRegularGenerator(...)` to watch:

```kotlin
extension.generators.designCommand.enabled,
extension.generators.designQuery.enabled,
extension.generators.designQueryHandler.enabled,
extension.generators.designClient.enabled,
extension.generators.designClientHandler.enabled,
```

Update any remaining `design`-specific dependency inference to check the new ids. Since `designDomainEvent` stays separate, do not widen unrelated rules.

- [ ] **Step 4: Rewrite the functional fixtures and contract tests**

Change sample DSL blocks from:

```kotlin
generators {
    design { enabled.set(true) }
    designQueryHandler { enabled.set(true) }
    designClient { enabled.set(true) }
    designClientHandler { enabled.set(true) }
}
```

to:

```kotlin
generators {
    designCommand { enabled.set(true) }
    designQuery { enabled.set(true) }
    designQueryHandler { enabled.set(true) }
    designClient { enabled.set(true) }
    designClientHandler { enabled.set(true) }
}
```

Then update tests to assert the final ids:

```kotlin
assertEquals(
    setOf("design-command", "design-query", "design-query-handler", "design-client", "design-client-handler"),
    config.enabledGeneratorIds(),
)
```

Also update renderer/core tests that hard-code `generatorId = "design"` or `generatorId = "design-client"`:

```kotlin
generatorId = "design-command"
generatorId = "design-query"
generatorId = "design-client"
generatorId = "design-query-handler"
generatorId = "design-client-handler"
```

- [ ] **Step 5: Rewrite the README contract in the same slice**

Update `cap4k-plugin-pipeline-gradle/README.md` so it no longer documents:

- `RequestModel`
- `RequestKind`
- `design`
- `design-client` as an awkward sibling of `design`

The canonical model snippet must become:

```kotlin
data class CanonicalModel(
    val commands: List<CommandModel> = emptyList(),
    val queries: List<QueryModel> = emptyList(),
    val clients: List<ClientModel> = emptyList(),
    ...
)
```

The provider tables and examples must use:

- `designCommand`
- `designQuery`
- `designClient`
- `designQueryHandler`
- `designClientHandler`

- [ ] **Step 6: Run Gradle/plugin/renderer tests**

Run:

```bash
./gradlew :cap4k-plugin-pipeline-gradle:test --tests "*Cap4kProjectConfigFactoryTest" --tests "*PipelinePluginTest" --tests "*PipelinePluginFunctionalTest" --tests "*PipelinePluginCompileFunctionalTest" :cap4k-plugin-pipeline-renderer-pebble:test --tests "*PebbleArtifactRendererTest" :cap4k-plugin-pipeline-core:test --tests "*DefaultPipelineRunnerTest"
```

Expected:
- PASS

- [ ] **Step 7: Commit the public contract rename**

```bash
git add cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle cap4k-plugin-pipeline-gradle/src/test/resources/functional cap4k-plugin-pipeline-renderer-pebble/src/test/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PebbleArtifactRendererTest.kt cap4k-plugin-pipeline-core/src/test/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultPipelineRunnerTest.kt cap4k-plugin-pipeline-gradle/README.md
git commit -m "refactor: rename design request-family providers"
```

### Task 5: Remove any temporary compatibility bridge and run final regression

**Files:**
- Modify: any files still carrying temporary aliases or bridge code from Tasks 2-4
- Verify: all touched files from Tasks 1-4

- [ ] **Step 1: Remove branch-only aliases if any were introduced**

Search for temporary compatibility code and delete it before final verification. Explicitly remove:

- old Gradle DSL accessors such as `design { ... }`
- old generator ids such as `design` and `design-client`
- adapter shims that map old ids to new ids
- any compatibility comments that mention “temporary alias”

Run:

```bash
rg -n -F "\"design\"" cap4k-plugin-pipeline-gradle cap4k-plugin-pipeline-generator-design cap4k-plugin-pipeline-renderer-pebble cap4k-plugin-pipeline-core
rg -n -F "\"design-client\"" cap4k-plugin-pipeline-gradle cap4k-plugin-pipeline-generator-design cap4k-plugin-pipeline-renderer-pebble cap4k-plugin-pipeline-core
rg -n -F "RequestKind" cap4k-plugin-pipeline-api cap4k-plugin-pipeline-core cap4k-plugin-pipeline-generator-design
rg -n -F "RequestModel" cap4k-plugin-pipeline-api cap4k-plugin-pipeline-core cap4k-plugin-pipeline-generator-design
```

Expected:
- no hits in production code for removed public contract symbols

- [ ] **Step 2: Run the targeted full regression for this slice**

Run:

```bash
./gradlew :cap4k-plugin-pipeline-api:test --tests "*PipelineModelsTest" :cap4k-plugin-pipeline-core:test --tests "*DefaultCanonicalAssemblerTest" --tests "*DefaultPipelineRunnerTest" :cap4k-plugin-pipeline-generator-design:test :cap4k-plugin-pipeline-renderer-pebble:test --tests "*PebbleArtifactRendererTest" :cap4k-plugin-pipeline-gradle:test --tests "*Cap4kProjectConfigFactoryTest" --tests "*PipelinePluginTest" --tests "*PipelinePluginFunctionalTest" --tests "*PipelinePluginCompileFunctionalTest"
```

Expected:
- PASS

- [ ] **Step 3: Run repository hygiene checks**

Run:

```bash
git diff --check
```

Expected:
- no whitespace or merge-marker errors

- [ ] **Step 4: Commit the cleanup/final contract state**

```bash
git add cap4k-plugin-pipeline-api cap4k-plugin-pipeline-core cap4k-plugin-pipeline-generator-design cap4k-plugin-pipeline-gradle cap4k-plugin-pipeline-renderer-pebble
git commit -m "test: finalize design family normalization"
```

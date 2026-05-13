# Cap4k Design Integration Event Generation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add design-driven integration event contract and inbound subscriber generation, with matching canonical, Gradle, drawing-board, and analysis support.

**Architecture:** Extend the existing design JSON source into a new canonical `IntegrationEventModel`, then add two design generator families that render contracts and inbound `@EventListener` subscribers. Keep runtime transport unchanged; update analysis only so generated integration events and drawing-board exports are visible downstream.

**Tech Stack:** Kotlin, Gradle plugin TestKit, Gson, Pebble templates, JUnit 5, cap4k pipeline API/core/generator modules, cap4k code-analysis compiler.

---

## File Structure

Modify pipeline API models:

- `cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModels.kt`
  - Add `IntegrationEventRole`, `IntegrationEventModel`, `CanonicalModel.integrationEvents`, `DrawingBoardElementModel.role`, and `DrawingBoardElementModel.eventName`.
- `cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/ProjectConfig.kt`
  - Add layout entries for `designIntegrationEvent` and `designIntegrationEventSubscriber`.
- `cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/ArtifactLayoutResolver.kt`
  - Add package resolver methods and validation entries for integration event layouts.

Modify design JSON source and canonical assembly:

- `cap4k-plugin-pipeline-source-design-json/src/main/kotlin/com/only4/cap4k/plugin/pipeline/source/designjson/DesignJsonSourceProvider.kt`
  - Accept `integration_event`, parse `role` and `eventName`, reject invalid integration event shapes.
- `cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssembler.kt`
  - Assemble `IntegrationEventModel`; include `integration_event` in drawing-board normalization.

Modify generator DSL/config:

- `cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kExtension.kt`
  - Add layout and generator extension blocks.
- `cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kProjectConfigFactory.kt`
  - Build/validate the two new generators and layouts.
- `cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePlugin.kt`
  - Include new generator IDs where generated source task filtering and runner registration need it, if the generator registry is explicit.

Create design generator planners/render models:

- `cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignIntegrationEventArtifactPlanner.kt`
- `cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignIntegrationEventSubscriberArtifactPlanner.kt`
- `cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignIntegrationEventRenderModels.kt`
- `cap4k-plugin-pipeline-generator-design/src/main/resources/META-INF/services/com.only4.cap4k.plugin.pipeline.api.GeneratorProvider`
  - Add planner class names to the service provider file.

Create Pebble templates:

- `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/design/integration_event.kt.peb`
- `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/design/integration_event_subscriber.kt.peb`

Modify drawing-board:

- `cap4k-plugin-pipeline-generator-drawing-board/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/drawingboard/DrawingBoardArtifactPlanner.kt`
  - Add `integration_event` to supported tag order.

Modify code analysis:

- `cap4k-plugin-code-analysis-core/src/main/kotlin/com/only4/cap4k/plugin/codeanalysis/core/model/DesignElement.kt`
  - Add nullable `role` and `eventName`.
- `cap4k-plugin-code-analysis-compiler/src/main/kotlin/com/only4/cap4k/plugin/codeanalysis/compiler/DesignElementCollector.kt`
  - Extract generated `@IntegrationEvent` classes only when role package can be inferred.
- `cap4k-plugin-code-analysis-compiler/src/main/kotlin/com/only4/cap4k/plugin/codeanalysis/compiler/DesignElementJsonWriter.kt`
  - Write `role` and `eventName`.

Modify documentation:

- `docs/public/reference/generator-dsl.zh-CN.md`
- `docs/public/authoring/generator/input-sources.zh-CN.md`
- `docs/public/authoring/tactical-model.zh-CN.md`

Tests:

- `cap4k-plugin-pipeline-source-design-json/src/test/kotlin/com/only4/cap4k/plugin/pipeline/source/designjson/DesignJsonSourceProviderTest.kt`
- `cap4k-plugin-pipeline-core/src/test/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssemblerTest.kt`
- New tests under `cap4k-plugin-pipeline-generator-design/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/`
- `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginTest.kt`
- `cap4k-plugin-pipeline-generator-drawing-board/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/drawingboard/DrawingBoardArtifactPlannerTest.kt`
- `cap4k-plugin-code-analysis-compiler/src/test/kotlin/com/only4/cap4k/plugin/codeanalysis/compiler/DesignElementJsonWriterTest.kt`
- `cap4k-plugin-code-analysis-compiler/src/test/kotlin/com/only4/cap4k/plugin/codeanalysis/compiler/DesignElementExtractionTest.kt`

---

### Task 1: API Models And Layout Surface

**Files:**
- Modify: `cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModels.kt`
- Modify: `cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/ProjectConfig.kt`
- Modify: `cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/ArtifactLayoutResolver.kt`

- [ ] **Step 1: Add API model tests through existing compiler checks**

There is no dedicated API model test file for this exact surface. Use downstream failing tests in Tasks 2 and 3 to drive this change. Before editing, run:

```powershell
./gradlew :cap4k-plugin-pipeline-api:test
```

Expected: PASS before changes.

- [ ] **Step 2: Add integration event canonical types**

In `PipelineModels.kt`, add these types near `DomainEventModel`:

```kotlin
enum class IntegrationEventRole {
    INBOUND,
    OUTBOUND,
}

data class IntegrationEventModel(
    val packageName: String,
    val typeName: String,
    val description: String,
    val role: IntegrationEventRole,
    val eventName: String,
    val fields: List<FieldModel> = emptyList(),
)
```

Then add this property to `CanonicalModel`:

```kotlin
val integrationEvents: List<IntegrationEventModel> = emptyList(),
```

Place it next to `domainEvents` so design families stay grouped.

- [ ] **Step 3: Extend drawing-board model**

In `DrawingBoardElementModel`, add:

```kotlin
val role: String? = null,
val eventName: String? = null,
```

Place these near `persist` because they are element-level metadata.

- [ ] **Step 4: Add layout config**

In `ArtifactLayoutConfig`, add:

```kotlin
val designIntegrationEvent: PackageLayout = PackageLayout(
    packageRoot = "application.subscribers.integration",
    packageSuffix = "",
),
val designIntegrationEventSubscriber: PackageLayout = PackageLayout(
    packageRoot = "application.subscribers.integration",
    packageSuffix = "",
),
```

Place them after domain event handler layout.

- [ ] **Step 5: Add layout resolver methods**

In `ArtifactLayoutResolver`, add:

```kotlin
fun designIntegrationEventPackage(role: String, designPackage: String): String =
    packageFromLayout(artifactLayout.designIntegrationEvent, joinPackage(role, designPackage))

fun designIntegrationEventSubscriberPackage(role: String, designPackage: String): String =
    packageFromLayout(artifactLayout.designIntegrationEventSubscriber, joinPackage(role, designPackage))
```

Also add both layouts to `packageLayouts()`:

```kotlin
"designIntegrationEvent" to artifactLayout.designIntegrationEvent,
"designIntegrationEventSubscriber" to artifactLayout.designIntegrationEventSubscriber,
```

- [ ] **Step 6: Run API tests**

```powershell
./gradlew :cap4k-plugin-pipeline-api:test
```

Expected: PASS.

- [ ] **Step 7: Commit**

```powershell
git add cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModels.kt cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/ProjectConfig.kt cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/ArtifactLayoutResolver.kt
git commit -m "feat: add integration event canonical models"
```

---

### Task 2: Design JSON Parsing

**Files:**
- Modify: `cap4k-plugin-pipeline-source-design-json/src/main/kotlin/com/only4/cap4k/plugin/pipeline/source/designjson/DesignJsonSourceProvider.kt`
- Test: `cap4k-plugin-pipeline-source-design-json/src/test/kotlin/com/only4/cap4k/plugin/pipeline/source/designjson/DesignJsonSourceProviderTest.kt`
- Modify: `cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModels.kt` for `DesignSpecEntry` fields

- [ ] **Step 1: Write failing parser tests**

Add tests to `DesignJsonSourceProviderTest.kt`:

```kotlin
@Test
fun `reads integration event role and event name`() {
    val tempFile = tempDir.resolve("integration-events.json")
    Files.writeString(
        tempFile,
        """
        [
          {
            "tag": "integration_event",
            "package": "media.processing",
            "name": "MediaProcessingCallbackIntegrationEvent",
            "desc": "media processing callback",
            "role": "inbound",
            "eventName": "cap4k.reference.contentstudio.media-processing.succeeded",
            "requestFields": [
              { "name": "externalTaskId", "type": "String" },
              { "name": "completedAt", "type": "java.time.LocalDateTime" }
            ],
            "responseFields": []
          }
        ]
        """.trimIndent()
    )

    val snapshot = provider.collect(configWithFiles(tempFile))

    val entry = snapshot.entries.single()
    assertEquals("integration_event", entry.tag)
    assertEquals("media.processing", entry.packageName)
    assertEquals("MediaProcessingCallbackIntegrationEvent", entry.name)
    assertEquals("inbound", entry.role)
    assertEquals("cap4k.reference.contentstudio.media-processing.succeeded", entry.eventName)
    assertEquals(listOf("externalTaskId", "completedAt"), entry.requestFields.map { it.name })
}

@Test
fun `rejects integration event without role`() {
    val tempFile = tempDir.resolve("integration-event-missing-role.json")
    Files.writeString(
        tempFile,
        """
        [
          {
            "tag": "integration_event",
            "package": "content",
            "name": "ContentPublishedIntegrationEvent",
            "eventName": "cap4k.reference.contentstudio.content.published",
            "requestFields": [],
            "responseFields": []
          }
        ]
        """.trimIndent()
    )

    val error = assertThrows<IllegalArgumentException> {
        provider.collect(configWithFiles(tempFile))
    }

    assertEquals("integration_event ContentPublishedIntegrationEvent must declare role inbound or outbound.", error.message)
}

@Test
fun `rejects integration event with unsupported role`() {
    val tempFile = tempDir.resolve("integration-event-bad-role.json")
    Files.writeString(
        tempFile,
        """
        [
          {
            "tag": "integration_event",
            "package": "content",
            "name": "ContentPublishedIntegrationEvent",
            "role": "producer",
            "eventName": "cap4k.reference.contentstudio.content.published",
            "requestFields": [],
            "responseFields": []
          }
        ]
        """.trimIndent()
    )

    val error = assertThrows<IllegalArgumentException> {
        provider.collect(configWithFiles(tempFile))
    }

    assertEquals("integration_event ContentPublishedIntegrationEvent has unsupported role: producer", error.message)
}

@Test
fun `rejects integration event without event name`() {
    val tempFile = tempDir.resolve("integration-event-missing-event-name.json")
    Files.writeString(
        tempFile,
        """
        [
          {
            "tag": "integration_event",
            "package": "content",
            "name": "ContentPublishedIntegrationEvent",
            "role": "outbound",
            "requestFields": [],
            "responseFields": []
          }
        ]
        """.trimIndent()
    )

    val error = assertThrows<IllegalArgumentException> {
        provider.collect(configWithFiles(tempFile))
    }

    assertEquals("integration_event ContentPublishedIntegrationEvent must declare eventName.", error.message)
}

@Test
fun `rejects integration event response fields`() {
    val tempFile = tempDir.resolve("integration-event-response-fields.json")
    Files.writeString(
        tempFile,
        """
        [
          {
            "tag": "integration_event",
            "package": "content",
            "name": "ContentPublishedIntegrationEvent",
            "role": "outbound",
            "eventName": "cap4k.reference.contentstudio.content.published",
            "requestFields": [],
            "responseFields": [
              { "name": "accepted", "type": "Boolean" }
            ]
          }
        ]
        """.trimIndent()
    )

    val error = assertThrows<IllegalArgumentException> {
        provider.collect(configWithFiles(tempFile))
    }

    assertEquals("integration_event ContentPublishedIntegrationEvent must not declare responseFields.", error.message)
}
```

If `configWithFiles` or `tempDir` have different helper names in the existing test class, use the existing helper names and preserve the assertions.

- [ ] **Step 2: Run tests to verify failure**

```powershell
./gradlew :cap4k-plugin-pipeline-source-design-json:test --tests "*DesignJsonSourceProviderTest"
```

Expected: FAIL because `integration_event`, `role`, and `eventName` are not supported yet.

- [ ] **Step 3: Extend `DesignSpecEntry`**

In `PipelineModels.kt`, add nullable fields to `DesignSpecEntry`:

```kotlin
val role: String? = null,
val eventName: String? = null,
```

Place them after `persist` so design-level metadata stays grouped.

- [ ] **Step 4: Parse integration event fields**

In `DesignJsonSourceProvider.kt`:

Add `"integration_event"` to `supportedTags`.

Add helpers:

```kotlin
private val supportedIntegrationEventRoles = setOf("inbound", "outbound")

private fun parseIntegrationEventRole(obj: JsonObject, name: String): String? {
    val role = obj["role"]?.asString?.trim()?.lowercase(Locale.ROOT)
    require(!role.isNullOrBlank()) {
        "integration_event $name must declare role inbound or outbound."
    }
    require(role in supportedIntegrationEventRoles) {
        "integration_event $name has unsupported role: $role"
    }
    return role
}

private fun parseIntegrationEventName(obj: JsonObject, name: String): String? {
    val eventName = obj["eventName"]?.asString?.trim()
    require(!eventName.isNullOrBlank()) {
        "integration_event $name must declare eventName."
    }
    return eventName
}

private fun validateIntegrationEventFields(tag: String, name: String, responseFields: List<FieldModel>) {
    if (tag == "integration_event") {
        require(responseFields.isEmpty()) {
            "integration_event $name must not declare responseFields."
        }
    }
}
```

In `parseFile`, compute:

```kotlin
val integrationEvent = tag == "integration_event"
val role = if (integrationEvent) parseIntegrationEventRole(obj, name) else null
val eventName = if (integrationEvent) parseIntegrationEventName(obj, name) else null
validateIntegrationEventFields(tag, name, responseFields)
```

Pass `role = role` and `eventName = eventName` to `DesignSpecEntry`.

- [ ] **Step 5: Run parser tests**

```powershell
./gradlew :cap4k-plugin-pipeline-source-design-json:test --tests "*DesignJsonSourceProviderTest"
```

Expected: PASS.

- [ ] **Step 6: Commit**

```powershell
git add cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModels.kt cap4k-plugin-pipeline-source-design-json/src/main/kotlin/com/only4/cap4k/plugin/pipeline/source/designjson/DesignJsonSourceProvider.kt cap4k-plugin-pipeline-source-design-json/src/test/kotlin/com/only4/cap4k/plugin/pipeline/source/designjson/DesignJsonSourceProviderTest.kt
git commit -m "feat: parse design integration events"
```

---

### Task 3: Canonical Assembly And Drawing-Board Model

**Files:**
- Modify: `cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssembler.kt`
- Test: `cap4k-plugin-pipeline-core/src/test/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssemblerTest.kt`

- [ ] **Step 1: Write failing canonical tests**

Add tests:

```kotlin
@Test
fun `assembler carries integration events into canonical model`() {
    val snapshot = DesignSpecSnapshot(
        entries = listOf(
            DesignSpecEntry(
                tag = "integration_event",
                packageName = "media.processing",
                name = "MediaProcessingCallbackIntegrationEvent",
                description = "media processing callback",
                role = "inbound",
                eventName = "cap4k.reference.contentstudio.media-processing.succeeded",
                requestFields = listOf(
                    FieldModel("externalTaskId", "String"),
                    FieldModel("completedAt", "java.time.LocalDateTime"),
                ),
            ),
            DesignSpecEntry(
                tag = "integration_event",
                packageName = "content",
                name = "ContentPublishedIntegrationEvent",
                description = "content published",
                role = "outbound",
                eventName = "cap4k.reference.contentstudio.content.published",
                requestFields = listOf(FieldModel("contentId", "java.util.UUID")),
            ),
        )
    )

    val result = assembler.assemble(
        PipelineSourceSnapshots(designSpec = snapshot),
        ProjectConfig(basePackage = "com.acme", layout = ProjectLayout.MULTI_MODULE, modules = emptyMap(), sources = emptyMap(), generators = emptyMap(), templates = TemplateConfig("ddd-default", emptyList(), ConflictPolicy.SKIP)),
    )

    assertEquals(listOf(IntegrationEventRole.INBOUND, IntegrationEventRole.OUTBOUND), result.model.integrationEvents.map { it.role })
    assertEquals(
        listOf(
            "cap4k.reference.contentstudio.media-processing.succeeded",
            "cap4k.reference.contentstudio.content.published",
        ),
        result.model.integrationEvents.map { it.eventName },
    )
}

@Test
fun `drawing board accepts integration event elements`() {
    val analysisSnapshot = IrAnalysisSnapshot(
        inputDirs = emptyList(),
        nodes = emptyList(),
        edges = emptyList(),
        designElements = listOf(
            DesignElementSnapshot(
                tag = "integration_event",
                packageName = "media.processing",
                name = "MediaProcessingCallbackIntegrationEvent",
                description = "media processing callback",
                role = "inbound",
                eventName = "cap4k.reference.contentstudio.media-processing.succeeded",
                requestFields = listOf(DesignFieldSnapshot("externalTaskId", "String")),
                responseFields = emptyList(),
            )
        )
    )

    val result = assembler.assemble(
        PipelineSourceSnapshots(analysis = analysisSnapshot),
        ProjectConfig(basePackage = "com.acme", layout = ProjectLayout.MULTI_MODULE, modules = emptyMap(), sources = emptyMap(), generators = emptyMap(), templates = TemplateConfig("ddd-default", emptyList(), ConflictPolicy.SKIP)),
    )

    val element = result.model.drawingBoard!!.elements.single()
    assertEquals("integration_event", element.tag)
    assertEquals("inbound", element.role)
    assertEquals("cap4k.reference.contentstudio.media-processing.succeeded", element.eventName)
}
```

Adjust constructor names if the existing test fixtures use helper methods; keep the same assertions.

- [ ] **Step 2: Run tests to verify failure**

```powershell
./gradlew :cap4k-plugin-pipeline-core:test --tests "*DefaultCanonicalAssemblerTest"
```

Expected: FAIL because `integrationEvents` is empty and drawing-board filters out `integration_event`.

- [ ] **Step 3: Assemble integration events**

In `DefaultCanonicalAssembler.kt`, add import/use for `IntegrationEventModel` and `IntegrationEventRole`.

Build:

```kotlin
val integrationEvents = designSpec?.entries
    .orEmpty()
    .filter { it.tag == "integration_event" }
    .map { entry ->
        IntegrationEventModel(
            packageName = entry.packageName,
            typeName = entry.name.normalizeUpperCamelTypeName(),
            description = entry.description,
            role = when (entry.role) {
                "inbound" -> IntegrationEventRole.INBOUND
                "outbound" -> IntegrationEventRole.OUTBOUND
                else -> throw IllegalArgumentException("integration_event ${entry.name} must declare role inbound or outbound.")
            },
            eventName = entry.eventName ?: throw IllegalArgumentException("integration_event ${entry.name} must declare eventName."),
            fields = entry.requestFields,
        )
    }
```

Add `integrationEvents = integrationEvents` to `CanonicalModel`.

- [ ] **Step 4: Update drawing-board normalization**

Add `integration_event` to supported drawing-board tags and `normalizeDrawingBoardTag`.

When mapping to `DrawingBoardElementModel`, pass:

```kotlin
role = it.role,
eventName = it.eventName,
```

- [ ] **Step 5: Run core tests**

```powershell
./gradlew :cap4k-plugin-pipeline-core:test --tests "*DefaultCanonicalAssemblerTest"
```

Expected: PASS.

- [ ] **Step 6: Commit**

```powershell
git add cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssembler.kt cap4k-plugin-pipeline-core/src/test/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssemblerTest.kt
git commit -m "feat: assemble integration event models"
```

---

### Task 4: Design Generator Planners And Templates

**Files:**
- Create: `cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignIntegrationEventRenderModels.kt`
- Create: `cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignIntegrationEventArtifactPlanner.kt`
- Create: `cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignIntegrationEventSubscriberArtifactPlanner.kt`
- Modify: `cap4k-plugin-pipeline-generator-design/src/main/resources/META-INF/services/com.only4.cap4k.plugin.pipeline.api.GeneratorProvider`
- Create: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/design/integration_event.kt.peb`
- Create: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/design/integration_event_subscriber.kt.peb`
- Test: `cap4k-plugin-pipeline-generator-design/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignIntegrationEventArtifactPlannerTest.kt`
- Test: `cap4k-plugin-pipeline-generator-design/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignIntegrationEventSubscriberArtifactPlannerTest.kt`

- [ ] **Step 1: Write failing planner tests**

Create `DesignIntegrationEventArtifactPlannerTest.kt`:

```kotlin
class DesignIntegrationEventArtifactPlannerTest {
    private val planner = DesignIntegrationEventArtifactPlanner()

    @Test
    fun `plans inbound and outbound integration event contracts under role packages`() {
        val config = ProjectConfig(
            basePackage = "com.acme",
            layout = ProjectLayout.MULTI_MODULE,
            modules = mapOf("application" to "demo-application"),
            sources = emptyMap(),
            generators = emptyMap(),
            templates = TemplateConfig("ddd-default", emptyList(), ConflictPolicy.SKIP),
        )
        val model = CanonicalModel(
            integrationEvents = listOf(
                IntegrationEventModel(
                    packageName = "media.processing",
                    typeName = "MediaProcessingCallbackIntegrationEvent",
                    description = "media processing callback",
                    role = IntegrationEventRole.INBOUND,
                    eventName = "cap4k.reference.contentstudio.media-processing.succeeded",
                    fields = listOf(FieldModel("externalTaskId", "String")),
                ),
                IntegrationEventModel(
                    packageName = "content",
                    typeName = "ContentPublishedIntegrationEvent",
                    description = "content published",
                    role = IntegrationEventRole.OUTBOUND,
                    eventName = "cap4k.reference.contentstudio.content.published",
                    fields = listOf(FieldModel("contentId", "java.util.UUID")),
                ),
            )
        )

        val items = planner.plan(config, model)

        assertEquals(
            listOf(
                "demo-application/src/main/kotlin/com/acme/application/subscribers/integration/inbound/media/processing/MediaProcessingCallbackIntegrationEvent.kt",
                "demo-application/src/main/kotlin/com/acme/application/subscribers/integration/outbound/content/ContentPublishedIntegrationEvent.kt",
            ),
            items.map { it.outputPath },
        )
        assertEquals(listOf("design/integration_event.kt.peb", "design/integration_event.kt.peb"), items.map { it.templateId })
        assertEquals(listOf("inbound", "outbound"), items.map { it.context["role"] })
    }
}
```

Create `DesignIntegrationEventSubscriberArtifactPlannerTest.kt`:

```kotlin
class DesignIntegrationEventSubscriberArtifactPlannerTest {
    private val planner = DesignIntegrationEventSubscriberArtifactPlanner()

    @Test
    fun `plans subscribers only for inbound integration events`() {
        val config = ProjectConfig(
            basePackage = "com.acme",
            layout = ProjectLayout.MULTI_MODULE,
            modules = mapOf("application" to "demo-application"),
            sources = emptyMap(),
            generators = emptyMap(),
            templates = TemplateConfig("ddd-default", emptyList(), ConflictPolicy.SKIP),
        )
        val model = CanonicalModel(
            integrationEvents = listOf(
                IntegrationEventModel(
                    packageName = "media.processing",
                    typeName = "MediaProcessingCallbackIntegrationEvent",
                    description = "media processing callback",
                    role = IntegrationEventRole.INBOUND,
                    eventName = "cap4k.reference.contentstudio.media-processing.succeeded",
                ),
                IntegrationEventModel(
                    packageName = "content",
                    typeName = "ContentPublishedIntegrationEvent",
                    description = "content published",
                    role = IntegrationEventRole.OUTBOUND,
                    eventName = "cap4k.reference.contentstudio.content.published",
                ),
            )
        )

        val items = planner.plan(config, model)

        assertEquals(1, items.size)
        assertEquals(
            "demo-application/src/main/kotlin/com/acme/application/subscribers/integration/inbound/media/processing/MediaProcessingCallbackIntegrationEventSubscriber.kt",
            items.single().outputPath,
        )
        assertEquals("design/integration_event_subscriber.kt.peb", items.single().templateId)
    }
}
```

- [ ] **Step 2: Run planner tests to verify failure**

```powershell
./gradlew :cap4k-plugin-pipeline-generator-design:test --tests "*DesignIntegrationEvent*"
```

Expected: FAIL because classes do not exist.

- [ ] **Step 3: Add render model factory**

Create `DesignIntegrationEventRenderModels.kt`:

```kotlin
package com.only4.cap4k.plugin.pipeline.generator.design

import com.only4.cap4k.plugin.pipeline.api.IntegrationEventModel
import com.only4.cap4k.plugin.pipeline.api.IntegrationEventRole

data class DesignIntegrationEventSubscriberRenderModel(
    val packageName: String,
    val typeName: String,
    val eventType: String,
    val eventShortType: String,
) {
    fun toContextMap(): Map<String, Any?> = mapOf(
        "packageName" to packageName,
        "typeName" to typeName,
        "eventType" to eventType,
        "eventShortType" to eventShortType,
    )
}

fun IntegrationEventRole.packageSegment(): String =
    name.lowercase()
```

Use `DesignPayloadRenderModelFactory.createForDomainEvent` as a pattern for contract rendering. Add a new factory function if current field rendering needs an `IntegrationEventModel` overload.

- [ ] **Step 4: Add contract planner**

Create `DesignIntegrationEventArtifactPlanner.kt`:

```kotlin
package com.only4.cap4k.plugin.pipeline.generator.design

import com.only4.cap4k.plugin.pipeline.api.ArtifactLayoutResolver
import com.only4.cap4k.plugin.pipeline.api.ArtifactPlanItem
import com.only4.cap4k.plugin.pipeline.api.CanonicalModel
import com.only4.cap4k.plugin.pipeline.api.GeneratorProvider
import com.only4.cap4k.plugin.pipeline.api.IntegrationEventRole
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig

class DesignIntegrationEventArtifactPlanner : GeneratorProvider {
    override val id: String = "design-integration-event"

    override fun plan(config: ProjectConfig, model: CanonicalModel): List<ArtifactPlanItem> {
        val applicationRoot = requireRelativeModuleRoot(config, "application")
        val artifactLayout = ArtifactLayoutResolver(config.basePackage, config.artifactLayout)

        return model.integrationEvents.map { event ->
            val roleSegment = event.role.packageSegment()
            val packageName = artifactLayout.designIntegrationEventPackage(roleSegment, event.packageName)
            val renderModel = DesignPayloadRenderModelFactory.createForIntegrationEvent(
                packageName = packageName,
                event = event,
                typeRegistry = config.typeRegistryFqns(),
            )
            ArtifactPlanItem(
                generatorId = id,
                moduleRole = "application",
                templateId = "design/integration_event.kt.peb",
                outputPath = artifactLayout.kotlinSourcePath(applicationRoot, packageName, event.typeName),
                context = mapOf(
                    "packageName" to renderModel.packageName,
                    "typeName" to renderModel.typeName,
                    "description" to renderModel.description,
                    "descriptionText" to renderModel.descriptionText,
                    "descriptionCommentText" to renderModel.descriptionCommentText,
                    "descriptionKotlinStringLiteral" to renderModel.descriptionKotlinStringLiteral,
                    "role" to roleSegment,
                    "eventName" to event.eventName,
                    "inbound" to (event.role == IntegrationEventRole.INBOUND),
                    "outbound" to (event.role == IntegrationEventRole.OUTBOUND),
                    "imports" to renderModel.imports,
                    "fields" to renderModel.requestFields,
                    "nestedTypes" to renderModel.requestNestedTypes,
                ),
                conflictPolicy = config.templates.conflictPolicy,
            )
        }
    }
}
```

- [ ] **Step 5: Add subscriber planner**

Create `DesignIntegrationEventSubscriberArtifactPlanner.kt`:

```kotlin
package com.only4.cap4k.plugin.pipeline.generator.design

import com.only4.cap4k.plugin.pipeline.api.ArtifactLayoutResolver
import com.only4.cap4k.plugin.pipeline.api.ArtifactPlanItem
import com.only4.cap4k.plugin.pipeline.api.CanonicalModel
import com.only4.cap4k.plugin.pipeline.api.GeneratorProvider
import com.only4.cap4k.plugin.pipeline.api.IntegrationEventRole
import com.only4.cap4k.plugin.pipeline.api.ProjectConfig

class DesignIntegrationEventSubscriberArtifactPlanner : GeneratorProvider {
    override val id: String = "design-integration-event-subscriber"

    override fun plan(config: ProjectConfig, model: CanonicalModel): List<ArtifactPlanItem> {
        val applicationRoot = requireRelativeModuleRoot(config, "application")
        val artifactLayout = ArtifactLayoutResolver(config.basePackage, config.artifactLayout)

        return model.integrationEvents
            .filter { it.role == IntegrationEventRole.INBOUND }
            .map { event ->
                val roleSegment = event.role.packageSegment()
                val packageName = artifactLayout.designIntegrationEventSubscriberPackage(roleSegment, event.packageName)
                val eventPackageName = artifactLayout.designIntegrationEventPackage(roleSegment, event.packageName)
                val eventType = "$eventPackageName.${event.typeName}"
                ArtifactPlanItem(
                    generatorId = id,
                    moduleRole = "application",
                    templateId = "design/integration_event_subscriber.kt.peb",
                    outputPath = artifactLayout.kotlinSourcePath(applicationRoot, packageName, "${event.typeName}Subscriber"),
                    context = DesignIntegrationEventSubscriberRenderModel(
                        packageName = packageName,
                        typeName = "${event.typeName}Subscriber",
                        eventType = eventType,
                        eventShortType = event.typeName,
                    ).toContextMap(),
                    conflictPolicy = config.templates.conflictPolicy,
                )
            }
    }
}
```

- [ ] **Step 6: Add payload render overload**

In `DesignPayloadRenderModelFactory.kt`, add an overload:

```kotlin
fun createForIntegrationEvent(
    packageName: String,
    event: IntegrationEventModel,
    typeRegistry: Map<String, String> = emptyMap(),
): DesignPayloadRenderModel =
    create(
        packageName = packageName,
        typeName = event.typeName,
        description = event.description,
        requestFields = event.fields,
        responseFields = emptyList(),
        pageRequest = false,
        typeRegistry = typeRegistry,
    )
```

Use the actual private/public `create` signature from the file; keep behavior identical to domain event field rendering but without aggregate fields.

- [ ] **Step 7: Register planners**

Append these lines to the generator provider service file:

```text
com.only4.cap4k.plugin.pipeline.generator.design.DesignIntegrationEventArtifactPlanner
com.only4.cap4k.plugin.pipeline.generator.design.DesignIntegrationEventSubscriberArtifactPlanner
```

- [ ] **Step 8: Add templates**

Create `integration_event.kt.peb`:

```twig
package {{ packageName }}

import com.only4.cap4k.ddd.core.application.event.annotation.IntegrationEvent
{% if outbound %}
import com.only4.cap4k.ddd.core.application.event.annotation.IntegrationEvent.Companion.NONE_SUBSCRIBER
{% endif %}
{% for import in imports %}
import {{ import }}
{% endfor %}

{% if descriptionCommentText %}
/**
 * {{ descriptionCommentText }}
 */
{% endif %}
@IntegrationEvent(
    value = {{ typeName }}.EVENT_NAME,
    subscriber = {% if inbound %}{{ typeName }}.SUBSCRIBER_NAME{% else %}NONE_SUBSCRIBER{% endif %},
)
data class {{ typeName }}(
{% for field in fields %}
    val {{ field.name }}: {{ field.renderedType }}{% if not loop.last %},{% endif %}
{% endfor %}
) {
    companion object {
        const val EVENT_NAME = "{{ eventName }}"
{% if inbound %}
        const val SUBSCRIBER_NAME = "\${spring.application.name:}"
{% endif %}
    }
}
```

Create `integration_event_subscriber.kt.peb`:

```twig
package {{ packageName }}

import {{ eventType }}
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Service

@Service
class {{ typeName }} {

    @EventListener({{ eventShortType }}::class)
    fun on(event: {{ eventShortType }}) {
    }
}
```

If existing template style includes description blocks or suppressions, follow it while preserving `@EventListener`.

- [ ] **Step 9: Run planner tests**

```powershell
./gradlew :cap4k-plugin-pipeline-generator-design:test --tests "*DesignIntegrationEvent*"
```

Expected: PASS.

- [ ] **Step 10: Commit**

```powershell
git add cap4k-plugin-pipeline-generator-design cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/design/integration_event.kt.peb cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/design/integration_event_subscriber.kt.peb
git commit -m "feat: generate design integration events"
```

---

### Task 5: Gradle DSL And Project Config

**Files:**
- Modify: `cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kExtension.kt`
- Modify: `cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kProjectConfigFactory.kt`
- Test: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kProjectConfigFactoryTest.kt`
- Test: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginTest.kt`

- [ ] **Step 1: Write config factory tests**

Add tests to `Cap4kProjectConfigFactoryTest.kt`:

```kotlin
@Test
fun `build includes design integration event generator`() {
    val project = ProjectBuilder.builder().build()
    val extension = project.extensions.create("cap4k", Cap4kExtension::class.java)
    extension.project.basePackage.set("com.acme")
    extension.project.applicationModulePath.set("demo-application")
    extension.sources.designJson.enabled.set(true)
    extension.sources.designJson.files.from(project.file("design/design.json"))
    extension.generators.designIntegrationEvent.enabled.set(true)

    val config = Cap4kProjectConfigFactory().build(project, extension)

    assertTrue(config.generators.containsKey("design-integration-event"))
    assertEquals("demo-application", config.modules["application"])
    assertEquals("application.subscribers.integration", config.artifactLayout.designIntegrationEvent.packageRoot)
}

@Test
fun `design integration event subscriber requires design integration event`() {
    val project = ProjectBuilder.builder().build()
    val extension = project.extensions.create("cap4k", Cap4kExtension::class.java)
    extension.project.basePackage.set("com.acme")
    extension.project.applicationModulePath.set("demo-application")
    extension.sources.designJson.enabled.set(true)
    extension.sources.designJson.files.from(project.file("design/design.json"))
    extension.generators.designIntegrationEventSubscriber.enabled.set(true)

    val error = assertThrows<IllegalArgumentException> {
        Cap4kProjectConfigFactory().build(project, extension)
    }

    assertEquals("designIntegrationEventSubscriber generator requires enabled designIntegrationEvent generator.", error.message)
}
```

- [ ] **Step 2: Run tests to verify failure**

```powershell
./gradlew :cap4k-plugin-pipeline-gradle:test --tests "*Cap4kProjectConfigFactoryTest"
```

Expected: FAIL because extension properties do not exist.

- [ ] **Step 3: Add extension blocks**

In `Cap4kLayoutExtension`, add:

```kotlin
val designIntegrationEvent: PackageLayoutExtension = objects.newInstance(PackageLayoutExtension::class.java)
    .convention("application.subscribers.integration")
val designIntegrationEventSubscriber: PackageLayoutExtension = objects.newInstance(PackageLayoutExtension::class.java)
    .convention("application.subscribers.integration")
```

Add functions:

```kotlin
fun designIntegrationEvent(block: PackageLayoutExtension.() -> Unit) {
    designIntegrationEvent.block()
}

fun designIntegrationEventSubscriber(block: PackageLayoutExtension.() -> Unit) {
    designIntegrationEventSubscriber.block()
}
```

In `Cap4kGeneratorsExtension`, add:

```kotlin
val designIntegrationEvent: DesignIntegrationEventGeneratorExtension =
    objects.newInstance(DesignIntegrationEventGeneratorExtension::class.java)
val designIntegrationEventSubscriber: DesignIntegrationEventSubscriberGeneratorExtension =
    objects.newInstance(DesignIntegrationEventSubscriberGeneratorExtension::class.java)
```

Add block functions and classes:

```kotlin
open class DesignIntegrationEventGeneratorExtension @Inject constructor(objects: ObjectFactory) {
    val enabled: Property<Boolean> = objects.property(Boolean::class.java).convention(false)
}

open class DesignIntegrationEventSubscriberGeneratorExtension @Inject constructor(objects: ObjectFactory) {
    val enabled: Property<Boolean> = objects.property(Boolean::class.java).convention(false)
}
```

- [ ] **Step 4: Build project config**

In `GeneratorStates`, add booleans for both new generators.

In `validateProjectRules`, require `project.applicationModulePath` when either new generator is enabled.

In `buildModules`, put `"application"` for either generator.

In `buildGenerators`, add:

```kotlin
if (states.designIntegrationEventEnabled) {
    put("design-integration-event", GeneratorConfig(enabled = true))
}
if (states.designIntegrationEventSubscriberEnabled) {
    put("design-integration-event-subscriber", GeneratorConfig(enabled = true))
}
```

In `validateGeneratorDependencies`, add:

```kotlin
if (generators.designIntegrationEventEnabled && !sources.designJsonEnabled) {
    throw IllegalArgumentException("designIntegrationEvent generator requires enabled designJson source.")
}
if (generators.designIntegrationEventSubscriberEnabled && !generators.designIntegrationEventEnabled) {
    throw IllegalArgumentException("designIntegrationEventSubscriber generator requires enabled designIntegrationEvent generator.")
}
```

In `buildArtifactLayout`, pass both layouts into `ArtifactLayoutConfig`.

- [ ] **Step 5: Run Gradle plugin tests**

```powershell
./gradlew :cap4k-plugin-pipeline-gradle:test --tests "*Cap4kProjectConfigFactoryTest" --tests "*PipelinePluginTest"
```

Expected: PASS.

- [ ] **Step 6: Commit**

```powershell
git add cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kExtension.kt cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kProjectConfigFactory.kt cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kProjectConfigFactoryTest.kt cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginTest.kt
git commit -m "feat: expose integration event generator DSL"
```

---

### Task 6: Drawing-Board Generation

**Files:**
- Modify: `cap4k-plugin-pipeline-generator-drawing-board/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/drawingboard/DrawingBoardArtifactPlanner.kt`
- Test: `cap4k-plugin-pipeline-generator-drawing-board/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/drawingboard/DrawingBoardArtifactPlannerTest.kt`

- [ ] **Step 1: Add failing drawing-board test**

Update the existing supported tag order test to include:

```kotlin
DrawingBoardElementModel(
    tag = "integration_event",
    packageName = "media.processing",
    name = "MediaProcessingCallbackIntegrationEvent",
    description = "media processing callback",
    role = "inbound",
    eventName = "cap4k.reference.contentstudio.media-processing.succeeded",
)
```

Assert that planned output paths include:

```kotlin
"design/drawing_board_integration_event.json"
```

- [ ] **Step 2: Run test to verify failure**

```powershell
./gradlew :cap4k-plugin-pipeline-generator-drawing-board:test --tests "*DrawingBoardArtifactPlannerTest"
```

Expected: FAIL because `integration_event` is not a supported drawing-board tag.

- [ ] **Step 3: Add supported tag**

In `DrawingBoardArtifactPlanner.kt`, add `"integration_event"` to the supported tag list after `"domain_event"` or immediately before `"validator"`.

- [ ] **Step 4: Run test**

```powershell
./gradlew :cap4k-plugin-pipeline-generator-drawing-board:test --tests "*DrawingBoardArtifactPlannerTest"
```

Expected: PASS.

- [ ] **Step 5: Commit**

```powershell
git add cap4k-plugin-pipeline-generator-drawing-board/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/drawingboard/DrawingBoardArtifactPlanner.kt cap4k-plugin-pipeline-generator-drawing-board/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/drawingboard/DrawingBoardArtifactPlannerTest.kt
git commit -m "feat: export integration event drawing board"
```

---

### Task 7: Code Analysis Design Elements

**Files:**
- Modify: `cap4k-plugin-code-analysis-core/src/main/kotlin/com/only4/cap4k/plugin/codeanalysis/core/model/DesignElement.kt`
- Modify: `cap4k-plugin-code-analysis-compiler/src/main/kotlin/com/only4/cap4k/plugin/codeanalysis/compiler/DesignElementCollector.kt`
- Modify: `cap4k-plugin-code-analysis-compiler/src/main/kotlin/com/only4/cap4k/plugin/codeanalysis/compiler/DesignElementJsonWriter.kt`
- Test: `cap4k-plugin-code-analysis-compiler/src/test/kotlin/com/only4/cap4k/plugin/codeanalysis/compiler/DesignElementJsonWriterTest.kt`
- Test: `cap4k-plugin-code-analysis-compiler/src/test/kotlin/com/only4/cap4k/plugin/codeanalysis/compiler/DesignElementExtractionTest.kt`

- [ ] **Step 1: Add writer test**

In `DesignElementJsonWriterTest.kt`, add:

```kotlin
@Test
fun `writes integration event role and event name`() {
    val json = DesignElementJsonWriter().write(
        listOf(
            DesignElement(
                tag = "integration_event",
                `package` = "media.processing",
                name = "MediaProcessingCallbackIntegrationEvent",
                desc = "media processing callback",
                role = "inbound",
                eventName = "cap4k.reference.contentstudio.media-processing.succeeded",
                requestFields = listOf(DesignField("externalTaskId", "String")),
                responseFields = emptyList(),
            )
        )
    )

    assertTrue(json.contains(""""tag":"integration_event""""))
    assertTrue(json.contains(""""role":"inbound""""))
    assertTrue(json.contains(""""eventName":"cap4k.reference.contentstudio.media-processing.succeeded""""))
}
```

- [ ] **Step 2: Add extraction test**

In `DesignElementExtractionTest.kt`, add a fixture source for `IntegrationEvent.kt` if not already present in the test fixture:

```kotlin
package com.only4.cap4k.ddd.core.application.event.annotation

annotation class IntegrationEvent(val value: String = "", val subscriber: String = "[none]")
```

Add a sample class:

```kotlin
package com.acme.application.subscribers.integration.inbound.media.processing

import com.only4.cap4k.ddd.core.application.event.annotation.IntegrationEvent
import java.time.LocalDateTime

@IntegrationEvent(
    value = MediaProcessingCallbackIntegrationEvent.EVENT_NAME,
    subscriber = MediaProcessingCallbackIntegrationEvent.SUBSCRIBER_NAME,
)
data class MediaProcessingCallbackIntegrationEvent(
    val externalTaskId: String,
    val completedAt: LocalDateTime,
) {
    companion object {
        const val EVENT_NAME = "cap4k.reference.contentstudio.media-processing.succeeded"
        const val SUBSCRIBER_NAME = "\${spring.application.name:}"
    }
}
```

Assert that `design-elements.json` contains:

```kotlin
val event = findObject(objects, "integration_event", "MediaProcessingCallbackIntegrationEvent")
assertTrue(event.contains(""""package":"media.processing""""))
assertTrue(event.contains(""""role":"inbound""""))
assertTrue(event.contains(""""eventName":"cap4k.reference.contentstudio.media-processing.succeeded""""))
assertTrue(event.contains(""""name":"externalTaskId","type":"String""""))
```

- [ ] **Step 3: Run tests to verify failure**

```powershell
./gradlew :cap4k-plugin-code-analysis-compiler:test --tests "*DesignElementJsonWriterTest" --tests "*DesignElementExtractionTest"
```

Expected: FAIL because `DesignElement` lacks fields and collector ignores `@IntegrationEvent`.

- [ ] **Step 4: Extend `DesignElement`**

Add:

```kotlin
val role: String? = null,
val eventName: String? = null,
```

- [ ] **Step 5: Write `role` and `eventName`**

In `DesignElementJsonWriter`, after `persist`, add:

```kotlin
element.role?.let { value ->
    append(",\"role\":\"").append(escape(value)).append("\"")
}
element.eventName?.let { value ->
    append(",\"eventName\":\"").append(escape(value)).append("\"")
}
```

- [ ] **Step 6: Collect integration events**

In `DesignElementCollector`, add:

```kotlin
private val integrationEventAnnFq = FqName(options.integrationEventAnnFq)
```

In `visitClass`, add before validator handling:

```kotlin
declaration.hasAnnotation(integrationEventAnnFq) ->
    collectIntegrationEventElement(declaration, fqcn)
```

Add:

```kotlin
private fun collectIntegrationEventElement(declaration: IrClass, fqcn: String) {
    val role = inferIntegrationEventRole(fqcn) ?: return
    val pkg = extractPackage(
        fqcn,
        listOf(".application.subscribers.integration.$role"),
        null,
    )
    val ann = declaration.annotations.firstOrNull {
        it.symbol.owner.parentAsClass.fqNameWhenAvailable == integrationEventAnnFq
    } ?: return
    val eventName = resolveIntegrationEventName(declaration, ann) ?: return
    val nestedTypes = collectNestedTypes(declaration)
    val requestFields = collectFields(
        declaration,
        nestedTypes,
        DefaultValueContext("integration_event ${declaration.name.asString()} request field"),
    )
    addElement(
        DesignElement(
            tag = "integration_event",
            `package` = pkg,
            name = declaration.name.asString(),
            desc = "",
            role = role,
            eventName = eventName,
            requestFields = requestFields,
            responseFields = emptyList(),
        )
    )
}

private fun inferIntegrationEventRole(fqcn: String): String? {
    val pkg = fqcn.substringBeforeLast(".", "")
    return when {
        ".application.subscribers.integration.inbound." in pkg -> "inbound"
        ".application.subscribers.integration.outbound." in pkg -> "outbound"
        else -> null
    }
}
```

For `resolveIntegrationEventName`, first support direct string annotation values. Then support generated companion constants by reading `EVENT_NAME` from the event class companion object. Implement this by scanning nested object declarations for a property named `EVENT_NAME` with an `IrConst<String>` initializer. If this is too invasive, adjust the generated template in Task 4 to pass the literal `eventName` directly into `@IntegrationEvent(value = "...")`; then this resolver only needs `ann.getStringArg("value")`.

Preferred implementation for simpler analysis:

```kotlin
private fun IrClass.readIntegrationEventName(integrationEventAnn: FqName): String? {
    val ann = annotations.firstOrNull { it.symbol.owner.parentAsClass.fqNameWhenAvailable == integrationEventAnn }
        ?: return null
    return ann.getStringArg("value")?.takeIf { it.isNotBlank() }
}
```

If using this simpler method, update the integration event template to render:

```kotlin
value = "{{ eventName }}",
```

while still keeping `EVENT_NAME` in the companion for user code.

- [ ] **Step 7: Run analysis tests**

```powershell
./gradlew :cap4k-plugin-code-analysis-compiler:test --tests "*DesignElementJsonWriterTest" --tests "*DesignElementExtractionTest"
```

Expected: PASS.

- [ ] **Step 8: Commit**

```powershell
git add cap4k-plugin-code-analysis-core/src/main/kotlin/com/only4/cap4k/plugin/codeanalysis/core/model/DesignElement.kt cap4k-plugin-code-analysis-compiler/src/main/kotlin/com/only4/cap4k/plugin/codeanalysis/compiler/DesignElementCollector.kt cap4k-plugin-code-analysis-compiler/src/main/kotlin/com/only4/cap4k/plugin/codeanalysis/compiler/DesignElementJsonWriter.kt cap4k-plugin-code-analysis-compiler/src/test/kotlin/com/only4/cap4k/plugin/codeanalysis/compiler/DesignElementJsonWriterTest.kt cap4k-plugin-code-analysis-compiler/src/test/kotlin/com/only4/cap4k/plugin/codeanalysis/compiler/DesignElementExtractionTest.kt cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/design/integration_event.kt.peb
git commit -m "feat: analyze design integration events"
```

---

### Task 8: Functional Generation Fixture

**Files:**
- Create or modify fixture under `cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-integration-event-sample/`
- Test: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginTest.kt`

- [ ] **Step 1: Add functional sample**

Create `settings.gradle.kts`:

```kotlin
pluginManagement {
    repositories {
        mavenLocal()
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenLocal()
        mavenCentral()
    }
}

rootProject.name = "design-integration-event-sample"
include("sample-application")
```

Create root `build.gradle.kts` using the same plugin fixture conventions as `design-domain-event-sample`, with:

```kotlin
cap4k {
    project {
        basePackage.set("com.acme")
        applicationModulePath.set("sample-application")
    }
    sources {
        designJson {
            enabled.set(true)
            files.from(file("design/design.json"))
        }
    }
    generators {
        designIntegrationEvent {
            enabled.set(true)
        }
        designIntegrationEventSubscriber {
            enabled.set(true)
        }
    }
}
```

Create `sample-application/build.gradle.kts` with Kotlin JVM and dependencies matching the compile sample:

```kotlin
plugins {
    kotlin("jvm")
}

dependencies {
    implementation("com.only4:ddd-core:0.5.0-SNAPSHOT")
    implementation("org.springframework:spring-context")
}
```

Create `design/design.json`:

```json
[
  {
    "tag": "integration_event",
    "package": "media.processing",
    "name": "MediaProcessingCallbackIntegrationEvent",
    "desc": "media processing callback",
    "role": "inbound",
    "eventName": "cap4k.reference.contentstudio.media-processing.succeeded",
    "requestFields": [
      { "name": "externalTaskId", "type": "String" },
      { "name": "completedAt", "type": "java.time.LocalDateTime" }
    ],
    "responseFields": []
  },
  {
    "tag": "integration_event",
    "package": "content",
    "name": "ContentPublishedIntegrationEvent",
    "desc": "content published",
    "role": "outbound",
    "eventName": "cap4k.reference.contentstudio.content.published",
    "requestFields": [
      { "name": "contentId", "type": "java.util.UUID" }
    ],
    "responseFields": []
  }
]
```

- [ ] **Step 2: Add functional test**

In `PipelinePluginTest.kt`, add a TestKit test following existing design fixture tests:

```kotlin
@Test
fun `cap4kGenerate writes design integration event contracts and inbound subscriber`() {
    val result = gradleRunner("design-integration-event-sample", "cap4kGenerate").build()

    assertTrue(result.output.contains("BUILD SUCCESSFUL"))
    assertTrue(
        fixtureFile(
            "design-integration-event-sample",
            "sample-application/src/main/kotlin/com/acme/application/subscribers/integration/inbound/media/processing/MediaProcessingCallbackIntegrationEvent.kt"
        ).exists()
    )
    assertTrue(
        fixtureFile(
            "design-integration-event-sample",
            "sample-application/src/main/kotlin/com/acme/application/subscribers/integration/inbound/media/processing/MediaProcessingCallbackIntegrationEventSubscriber.kt"
        ).exists()
    )
    assertTrue(
        fixtureFile(
            "design-integration-event-sample",
            "sample-application/src/main/kotlin/com/acme/application/subscribers/integration/outbound/content/ContentPublishedIntegrationEvent.kt"
        ).exists()
    )
    assertFalse(
        fixtureFile(
            "design-integration-event-sample",
            "sample-application/src/main/kotlin/com/acme/application/subscribers/integration/outbound/content/ContentPublishedIntegrationEventSubscriber.kt"
        ).exists()
    )
}
```

Use the actual fixture helper names in `PipelinePluginTest.kt`.

- [ ] **Step 3: Run functional test**

```powershell
./gradlew :cap4k-plugin-pipeline-gradle:test --tests "*PipelinePluginTest*design integration event*"
```

Expected: PASS.

- [ ] **Step 4: Commit**

```powershell
git add cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-integration-event-sample cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginTest.kt
git commit -m "test: cover integration event generation"
```

---

### Task 9: Documentation Updates

**Files:**
- Modify: `docs/public/reference/generator-dsl.zh-CN.md`
- Modify: `docs/public/authoring/generator/input-sources.zh-CN.md`
- Modify: `docs/public/authoring/tactical-model.zh-CN.md`

- [ ] **Step 1: Update generator DSL reference**

In `generator-dsl.zh-CN.md`, add the two generator families to the design family table:

```markdown
| `designIntegrationEvent` | 设计驱动集成事件契约 | `cap4kPlan` / `cap4kGenerate` |
| `designIntegrationEventSubscriber` | 设计驱动 inbound 集成事件订阅者 | `cap4kPlan` / `cap4kGenerate` |
```

Add layout entries:

```markdown
| `designIntegrationEvent` | `application.subscribers.integration` | 生成器会自动追加 `inbound` 或 `outbound` |
| `designIntegrationEventSubscriber` | `application.subscribers.integration` | 仅对 inbound 事件生成订阅者，不追加 role/design package |
```

- [ ] **Step 2: Update input source docs**

In `input-sources.zh-CN.md`, add:

```markdown
`integration_event` 用于集成事件契约。它必须声明 `role` 和 `eventName`：

- `role = "inbound"` 表示本服务消费外部事件，会生成事件契约和 `@EventListener` 订阅者；
- `role = "outbound"` 表示本服务生产对外事件，只生成事件契约；
- `eventName` 会写入 `@IntegrationEvent.value`；
- 不在 design JSON 中声明 subscriber，消费者身份由运行时应用名提供。
```

Include the JSON sample from the spec.

- [ ] **Step 3: Update tactical model docs**

In `tactical-model.zh-CN.md`, clarify:

```markdown
集成事件订阅者的标准作者入口是 Spring `@EventListener` 方法。`EventSubscriber<T>` 是运行时 SPI，不是生成器默认产物。生成器会把 inbound 集成事件契约放到 `application.subscribers.integration.inbound`，把 outbound 集成事件契约放到 `application.subscribers.integration.outbound`；inbound subscriber 骨架直接放到 `application.subscribers.integration`。
```

- [ ] **Step 4: Commit**

```powershell
git add docs/public/reference/generator-dsl.zh-CN.md docs/public/authoring/generator/input-sources.zh-CN.md docs/public/authoring/tactical-model.zh-CN.md
git commit -m "docs: document integration event generation"
```

---

### Task 10: Final Verification

**Files:**
- No planned code edits.

- [ ] **Step 1: Run focused module tests**

```powershell
./gradlew :cap4k-plugin-pipeline-api:test :cap4k-plugin-pipeline-source-design-json:test :cap4k-plugin-pipeline-core:test :cap4k-plugin-pipeline-generator-design:test :cap4k-plugin-pipeline-generator-drawing-board:test :cap4k-plugin-code-analysis-compiler:test :cap4k-plugin-pipeline-gradle:test
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: Run full build if focused tests pass**

```powershell
./gradlew build
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Inspect generated plan fixture output if available**

Run the functional fixture plan:

```powershell
./gradlew :cap4k-plugin-pipeline-gradle:test --tests "*PipelinePluginTest*design integration event*"
```

Expected: PASS and generated files prove:

- inbound contract exists;
- inbound subscriber exists;
- outbound contract exists;
- outbound subscriber does not exist.

- [ ] **Step 4: Final status**

```powershell
git status --short
```

Expected: no uncommitted files except intentional generated fixture artifacts ignored by `.gitignore`.

---

## Self-Review Notes

Spec coverage:

- Design JSON `integration_event`, required `role`, required `eventName`: Task 2.
- Canonical model support: Tasks 1 and 3.
- Inbound/outbound package split: Tasks 1 and 4.
- Inbound subscriber only, using `@EventListener`: Task 4.
- Gradle DSL and layout exposure: Task 5.
- Drawing-board support: Tasks 3 and 6.
- Analysis design-element support: Task 7.
- Functional generation proof: Task 8.
- Public docs: Task 9.
- Verification: Task 10.

Important implementation choice:

- For analysis simplicity, prefer rendering the literal event name in `@IntegrationEvent(value = "...")` while also keeping `EVENT_NAME` in the companion. This avoids teaching the IR collector to resolve companion constants and keeps the design-element extractor deterministic.

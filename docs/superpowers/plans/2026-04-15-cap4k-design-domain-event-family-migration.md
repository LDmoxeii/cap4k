# Cap4k Design Domain Event Family Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add bounded pipeline support for `domain_event` and `domain_event_handler` so standard design entries generate helper-first event classes into the domain module and Spring subscriber handlers into the application module.

**Architecture:** Extend the design-json source and canonical model with a dedicated `domainEvents` slice, then implement two bounded planners: one for domain-side event artifacts and one for application-side subscriber artifacts. Keep the migration intentionally narrow: only standard `domain_event` tags are accepted, event naming remains old-compatible, `persist` defaults to `false`, the synthetic `entity` field is render-time only, and handler generation is gated behind `designDomainEvent`.

**Tech Stack:** Kotlin, Gradle, JUnit 5, Gradle TestKit, Pebble templates

---

## File Structure

- Modify: `cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModels.kt`
- Modify: `cap4k-plugin-pipeline-source-design-json/src/main/kotlin/com/only4/cap4k/plugin/pipeline/source/designjson/DesignJsonSourceProvider.kt`
- Modify: `cap4k-plugin-pipeline-source-design-json/src/test/kotlin/com/only4/cap4k/plugin/pipeline/source/designjson/DesignJsonSourceProviderTest.kt`
- Modify: `cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssembler.kt`
- Modify: `cap4k-plugin-pipeline-core/src/test/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssemblerTest.kt`
- Modify: `cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kExtension.kt`
- Modify: `cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kProjectConfigFactory.kt`
- Modify: `cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePlugin.kt`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kProjectConfigFactoryTest.kt`
- Create: `cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignDomainEventArtifactPlanner.kt`
- Create: `cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignDomainEventHandlerArtifactPlanner.kt`
- Create: `cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignDomainEventHandlerRenderModels.kt`
- Modify: `cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignRenderModelFactory.kt`
- Create: `cap4k-plugin-pipeline-generator-design/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignDomainEventArtifactPlannerTest.kt`
- Create: `cap4k-plugin-pipeline-generator-design/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignDomainEventHandlerArtifactPlannerTest.kt`
- Create: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/design/domain_event.kt.peb`
- Create: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/design/domain_event_handler.kt.peb`
- Modify: `cap4k-plugin-pipeline-renderer-pebble/src/test/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PebbleArtifactRendererTest.kt`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-domain-event-sample/build.gradle.kts`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-domain-event-sample/settings.gradle.kts`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-domain-event-sample/design/design.json`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-domain-event-sample/codegen/templates/design/domain_event.kt.peb`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-domain-event-sample/codegen/templates/design/domain_event_handler.kt.peb`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginFunctionalTest.kt`

### Task 1: Add Source, Canonical, And Gradle Support For Domain Event Family

**Files:**
- Modify: `cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModels.kt`
- Modify: `cap4k-plugin-pipeline-source-design-json/src/main/kotlin/com/only4/cap4k/plugin/pipeline/source/designjson/DesignJsonSourceProvider.kt`
- Modify: `cap4k-plugin-pipeline-source-design-json/src/test/kotlin/com/only4/cap4k/plugin/pipeline/source/designjson/DesignJsonSourceProviderTest.kt`
- Modify: `cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssembler.kt`
- Modify: `cap4k-plugin-pipeline-core/src/test/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssemblerTest.kt`
- Modify: `cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kExtension.kt`
- Modify: `cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kProjectConfigFactory.kt`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kProjectConfigFactoryTest.kt`

- [ ] **Step 1: Add failing source and canonical tests for `domain_event` parsing and assembly**

Add tests that prove:

- `DesignJsonSourceProvider` reads optional `"persist": true|false` into `DesignSpecEntry.persist`
- `CanonicalModel` gains `domainEvents`
- only standard `domain_event` tags assemble into `domainEvents`
- event naming stays old-compatible:
  - `OrderCreated` -> `OrderCreatedDomainEvent`
  - `OrderCreatedEvt` -> `OrderCreatedEvt`
  - `OrderCreatedEvent` -> `OrderCreatedEvent`
- omitted `persist` defaults to `false`
- aggregate metadata resolves into `aggregateName` and `aggregatePackageName`
- canonical `fields` does not contain synthetic `entity`

Use a representative design entry shaped like:

```json
{
  "tag": "domain_event",
  "package": "order",
  "name": "OrderCreated",
  "desc": "order created event",
  "aggregates": ["Order"],
  "persist": true,
  "requestFields": [
    { "name": "reason", "type": "String" },
    { "name": "snapshot", "type": "Snapshot", "nullable": true },
    { "name": "snapshot.traceId", "type": "UUID" }
  ],
  "responseFields": []
}
```

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-source-design-json:test :cap4k-plugin-pipeline-core:test --tests "*domain*event*" --rerun-tasks
```

Expected: FAIL because `DesignSpecEntry.persist` and `CanonicalModel.domainEvents` do not exist yet.

- [ ] **Step 2: Implement minimal source and canonical support**

Extend `PipelineModels.kt` with:

```kotlin
data class DesignSpecEntry(
    val tag: String,
    val packageName: String,
    val name: String,
    val description: String,
    val aggregates: List<String>,
    val persist: Boolean? = null,
    val requestFields: List<FieldModel>,
    val responseFields: List<FieldModel>,
)

data class DomainEventModel(
    val packageName: String,
    val typeName: String,
    val description: String,
    val aggregateName: String,
    val aggregatePackageName: String,
    val persist: Boolean,
    val fields: List<FieldModel> = emptyList(),
)
```

and extend `CanonicalModel` with:

```kotlin
val domainEvents: List<DomainEventModel> = emptyList()
```

Update `DesignJsonSourceProvider` so it reads:

```kotlin
persist = obj["persist"]?.asBoolean
```

Update `DefaultCanonicalAssembler` so `domain_event` entries:

- require one resolved aggregate
- assemble into `model.domainEvents`
- derive old-compatible `typeName`
- copy `requestFields` into `fields`
- default missing `persist` to `false`
- do not inject `entity` into canonical fields

- [ ] **Step 3: Add failing Gradle config tests for `designDomainEvent` and `designDomainEventHandler`**

Add tests that prove:

- both generator extensions default to `enabled = false`
- `designDomainEvent` wires generator id `design-domain-event`
- `designDomainEventHandler` wires generator id `design-domain-event-handler`
- `designDomainEvent` requires `project.domainModulePath`
- `designDomainEventHandler` requires `project.applicationModulePath`
- `designDomainEvent` requires enabled `designJson`
- `designDomainEventHandler` requires enabled `designDomainEvent`

Use these exact failure messages:

```kotlin
"project.domainModulePath is required when designDomainEvent is enabled."
"project.applicationModulePath is required when designDomainEventHandler is enabled."
"designDomainEvent generator requires enabled designJson source."
"designDomainEventHandler generator requires enabled designDomainEvent generator."
```

- [ ] **Step 4: Implement DSL and config wiring**

Add generator extensions and DSL blocks:

```kotlin
val designDomainEvent: DesignDomainEventGeneratorExtension
val designDomainEventHandler: DesignDomainEventHandlerGeneratorExtension

fun designDomainEvent(block: DesignDomainEventGeneratorExtension.() -> Unit)
fun designDomainEventHandler(block: DesignDomainEventHandlerGeneratorExtension.() -> Unit)
```

Wire generator ids in `Cap4kProjectConfigFactory`:

```kotlin
"design-domain-event"
"design-domain-event-handler"
```

and add only these dependency rules:

- `designDomainEvent` requires enabled `designJson`
- `designDomainEventHandler` requires enabled `designDomainEvent`
- `designDomainEvent` requires `domainModulePath`
- `designDomainEventHandler` requires `applicationModulePath`

- [ ] **Step 5: Run focused source, canonical, and config tests**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-source-design-json:test :cap4k-plugin-pipeline-core:test :cap4k-plugin-pipeline-gradle:test --tests "*domain*event*" --rerun-tasks
```

Expected: PASS for source parsing, canonical assembly, and config validation.

- [ ] **Step 6: Commit the source/canonical/config slice**

```bash
git add cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModels.kt cap4k-plugin-pipeline-source-design-json/src/main/kotlin/com/only4/cap4k/plugin/pipeline/source/designjson/DesignJsonSourceProvider.kt cap4k-plugin-pipeline-source-design-json/src/test/kotlin/com/only4/cap4k/plugin/pipeline/source/designjson/DesignJsonSourceProviderTest.kt cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssembler.kt cap4k-plugin-pipeline-core/src/test/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssemblerTest.kt cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kExtension.kt cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kProjectConfigFactory.kt cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kProjectConfigFactoryTest.kt
git commit -m "feat: add design domain event config"
```

### Task 2: Add Event And Subscriber Planners

**Files:**
- Create: `cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignDomainEventArtifactPlanner.kt`
- Create: `cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignDomainEventHandlerArtifactPlanner.kt`
- Create: `cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignDomainEventHandlerRenderModels.kt`
- Modify: `cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignRenderModelFactory.kt`
- Create: `cap4k-plugin-pipeline-generator-design/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignDomainEventArtifactPlannerTest.kt`
- Create: `cap4k-plugin-pipeline-generator-design/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignDomainEventHandlerArtifactPlannerTest.kt`
- Modify: `cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePlugin.kt`

- [ ] **Step 1: Add failing planner tests for event-side and handler-side output**

Add tests that assert:

- event planner:
  - `generatorId == "design-domain-event"`
  - `templateId == "design/domain_event.kt.peb"`
  - output path ends with `domain/order/events/OrderCreatedDomainEvent.kt`
  - package is `com.acme.demo.domain.order.events`
  - `persist` is forwarded
  - synthetic `entity` is not part of `fields`
  - nested types come from the current one-level pipeline contract
- handler planner:
  - `generatorId == "design-domain-event-handler"`
  - `templateId == "design/domain_event_handler.kt.peb"`
  - output path ends with `application/order/events/OrderCreatedDomainEventSubscriber.kt`
  - package is `com.acme.demo.application.order.events`
  - type name is `OrderCreatedDomainEventSubscriber`
  - imports include the generated event FQN

Use a representative canonical model like:

```kotlin
DomainEventModel(
    packageName = "order",
    typeName = "OrderCreatedDomainEvent",
    description = "order created event",
    aggregateName = "Order",
    aggregatePackageName = "com.acme.demo.domain.order",
    persist = false,
    fields = listOf(
        FieldModel("reason", "String"),
        FieldModel("snapshot", "Snapshot", nullable = true),
        FieldModel("snapshot.traceId", "UUID"),
    ),
)
```

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-generator-design:test --tests "*DomainEventArtifactPlannerTest" --tests "*DomainEventHandlerArtifactPlannerTest" --rerun-tasks
```

Expected: FAIL because the planners do not exist yet.

- [ ] **Step 2: Add render-model support for event-side and handler-side contracts**

Extend `DesignRenderModelFactory` with:

```kotlin
fun createForDomainEvent(
    packageName: String,
    event: DomainEventModel,
    typeRegistry: Map<String, String> = emptyMap(),
): DesignRenderModel
```

Implement it so it:

- reuses the current one-level nested-type namespace logic
- maps `event.fields` into the request namespace only
- keeps response namespace empty
- resolves aggregate imports through `aggregateName` and `aggregatePackageName`
- never injects synthetic `entity` into `fields`

Create `DesignDomainEventHandlerRenderModels.kt` with:

```kotlin
internal data class DesignDomainEventHandlerRenderModel(
    val packageName: String,
    val typeName: String,
    val domainEventTypeName: String,
    val imports: List<String>,
)
```

and make its factory import:

```kotlin
"$basePackage.domain.${event.packageName}.events.${event.typeName}"
```

- [ ] **Step 3: Implement planners and register providers**

Create `DesignDomainEventArtifactPlanner` so it:

- reads `model.domainEvents`
- emits `design/domain_event.kt.peb`
- writes to:
  - `<domainRoot>/src/main/kotlin/<base>/domain/<package>/events/<TypeName>.kt`
- sets package to:
  - `<basePackage>.domain.<package>.events`
- uses `DesignRenderModelFactory.createForDomainEvent(...)`

Create `DesignDomainEventHandlerArtifactPlanner` so it:

- reads `model.domainEvents`
- emits `design/domain_event_handler.kt.peb`
- writes to:
  - `<applicationRoot>/src/main/kotlin/<base>/application/<package>/events/<TypeName>Subscriber.kt`
- sets package to:
  - `<basePackage>.application.<package>.events`
- uses `DesignDomainEventHandlerRenderModelFactory.create(...)`

Register both planners in `PipelinePlugin.buildRunner(...)`.

- [ ] **Step 4: Run focused planner and compile verification**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-generator-design:test --tests "*DomainEvent*" --rerun-tasks
./gradlew :cap4k-plugin-pipeline-gradle:compileKotlin --rerun-tasks
```

Expected: PASS.

- [ ] **Step 5: Commit the planner slice**

```bash
git add cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignDomainEventArtifactPlanner.kt cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignDomainEventHandlerArtifactPlanner.kt cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignDomainEventHandlerRenderModels.kt cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignRenderModelFactory.kt cap4k-plugin-pipeline-generator-design/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignDomainEventArtifactPlannerTest.kt cap4k-plugin-pipeline-generator-design/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignDomainEventHandlerArtifactPlannerTest.kt cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePlugin.kt
git commit -m "feat: add design domain event planners"
```

### Task 3: Add Helper-First Templates And Functional Coverage

**Files:**
- Create: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/design/domain_event.kt.peb`
- Create: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/design/domain_event_handler.kt.peb`
- Modify: `cap4k-plugin-pipeline-renderer-pebble/src/test/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PebbleArtifactRendererTest.kt`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-domain-event-sample/build.gradle.kts`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-domain-event-sample/settings.gradle.kts`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-domain-event-sample/design/design.json`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-domain-event-sample/codegen/templates/design/domain_event.kt.peb`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-domain-event-sample/codegen/templates/design/domain_event_handler.kt.peb`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginFunctionalTest.kt`

- [ ] **Step 1: Add failing renderer tests for domain-event templates**

Add renderer tests that prove:

- `design/domain_event.kt.peb` renders:
  - `@DomainEvent`
  - `@Aggregate`
  - `class OrderCreatedDomainEvent(`
  - first field `val entity: Order`
  - dynamic aggregate import from `use(aggregateType)`
- `design/domain_event_handler.kt.peb` renders:
  - `@Service`
  - `@EventListener(OrderCreatedDomainEvent::class)`
  - `class OrderCreatedDomainEventSubscriber`
  - import of `com.acme.demo.domain.order.events.OrderCreatedDomainEvent`
- override template resolution works for both template ids

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-renderer-pebble:test --tests "*domain event*" --rerun-tasks
```

Expected: FAIL because the preset templates do not exist yet.

- [ ] **Step 2: Implement the bounded preset templates**

Create `design/domain_event.kt.peb` with the fixed helper contract:

```pebble
package {{ packageName }}
{{ use("com.only4.cap4k.ddd.core.domain.aggregate.annotation.Aggregate") -}}
{{ use("com.only4.cap4k.ddd.core.domain.event.annotation.DomainEvent") -}}
{{ use(aggregateType) -}}
{{ imports(imports) }}
```

and preserve:

- `@DomainEvent(persist = {{ persist }})`
- `@Aggregate(...)`
- `class {{ typeName }}(`
- first field `val entity: {{ aggregateName }}`
- nested event types rendered under the event class body

Create `design/domain_event_handler.kt.peb` with:

```pebble
package {{ packageName }}
{{ use("org.springframework.context.event.EventListener") -}}
{{ use("org.springframework.stereotype.Service") -}}
{{ imports(imports) }}
```

and preserve:

- `@Service`
- `@EventListener({{ domainEventTypeName }}::class)`
- `fun on(event: {{ domainEventTypeName }})`

- [ ] **Step 3: Add failing functional tests against an isolated domain-event fixture**

Add functional tests that prove:

- `cap4kPlan` emits both:
  - `design/domain_event.kt.peb`
  - `design/domain_event_handler.kt.peb`
- `cap4kGenerate` writes:
  - `demo-domain/.../events/OrderCreatedDomainEvent.kt`
  - `demo-application/.../events/OrderCreatedDomainEventSubscriber.kt`
- override template replacement works for both template ids
- invalid config fails when:
  - `designDomainEvent` lacks `domainModulePath`
  - `designDomainEventHandler` lacks `applicationModulePath`
  - `designDomainEvent` is disabled while `designDomainEventHandler` is enabled
  - `designJson` is disabled

- [ ] **Step 4: Create the isolated domain-event fixture**

Create `design-domain-event-sample/build.gradle.kts` with:

```kotlin
plugins {
    id("com.only4.cap4k.plugin.pipeline")
}

cap4k {
    project {
        basePackage.set("com.acme.demo")
        domainModulePath.set("demo-domain")
        applicationModulePath.set("demo-application")
    }
    sources {
        designJson {
            enabled.set(true)
            files.from("design/design.json")
        }
    }
    generators {
        designDomainEvent {
            enabled.set(true)
        }
        designDomainEventHandler {
            enabled.set(true)
        }
    }
}
```

Create `design-domain-event-sample/design/design.json` with one representative entry:

```json
[
  {
    "tag": "domain_event",
    "package": "order",
    "name": "OrderCreated",
    "desc": "order created event",
    "aggregates": ["Order"],
    "persist": true,
    "requestFields": [
      { "name": "reason", "type": "String" },
      { "name": "snapshot", "type": "Snapshot", "nullable": true },
      { "name": "snapshot.traceId", "type": "UUID" }
    ],
    "responseFields": []
  }
]
```

Create override templates containing these exact markers:

```pebble
// override: representative domain event migration template
// override: representative domain event handler migration template
```

- [ ] **Step 5: Run renderer and functional verification**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-renderer-pebble:test --tests "*domain event*" --rerun-tasks
./gradlew :cap4k-plugin-pipeline-gradle:test --tests "*domain event*" --rerun-tasks
```

Expected: PASS with both bounded templates resolved from `ddd-default`, both generated files written into their target module folders, override markers taking effect, and invalid config failures surfacing the expected messages.

- [ ] **Step 6: Commit the template and functional slice**

```bash
git add cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/design/domain_event.kt.peb cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/design/domain_event_handler.kt.peb cap4k-plugin-pipeline-renderer-pebble/src/test/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PebbleArtifactRendererTest.kt cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-domain-event-sample/build.gradle.kts cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-domain-event-sample/settings.gradle.kts cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-domain-event-sample/design/design.json cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-domain-event-sample/codegen/templates/design/domain_event.kt.peb cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-domain-event-sample/codegen/templates/design/domain_event_handler.kt.peb cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginFunctionalTest.kt
git commit -m "test: cover design domain event migration flow"
```

### Task 4: Run Full Verification For The Slice

**Files:**
- Verify only: `cap4k-plugin-pipeline-source-design-json`
- Verify only: `cap4k-plugin-pipeline-core`
- Verify only: `cap4k-plugin-pipeline-generator-design`
- Verify only: `cap4k-plugin-pipeline-renderer-pebble`
- Verify only: `cap4k-plugin-pipeline-gradle`

- [ ] **Step 1: Run module-level test suites**

```powershell
./gradlew :cap4k-plugin-pipeline-source-design-json:test
./gradlew :cap4k-plugin-pipeline-core:test
./gradlew :cap4k-plugin-pipeline-generator-design:test
./gradlew :cap4k-plugin-pipeline-renderer-pebble:test
./gradlew :cap4k-plugin-pipeline-gradle:test
```

Expected: all PASS.

- [ ] **Step 2: Run combined verification**

```powershell
./gradlew :cap4k-plugin-pipeline-source-design-json:test :cap4k-plugin-pipeline-core:test :cap4k-plugin-pipeline-generator-design:test :cap4k-plugin-pipeline-renderer-pebble:test :cap4k-plugin-pipeline-gradle:test
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Confirm branch cleanliness and landed commits**

```powershell
git status --short --branch
git log --oneline --max-count=10
```

Expected:

- clean working tree
- recent commits covering:
  - source/canonical domain-event support
  - domain-event DSL/config
  - domain-event planners
  - domain-event templates
  - domain-event renderer and functional coverage

## Self-Review

- Spec coverage: the plan covers source parsing, canonical assembly, Gradle DSL and dependency gating, event-side and handler-side planners, helper-first templates, override behavior, and functional regression coverage.
- Placeholder scan: no unresolved placeholder markers remain.
- Type consistency: the plan uses one public/internal naming pair consistently:
  - public DSL: `designDomainEvent`, `designDomainEventHandler`
  - internal generator ids: `design-domain-event`, `design-domain-event-handler`

# Cap4k Design Client Family Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add bounded `client / client_handler` pipeline support so old `cli/client/clients` entries generate `*Cli` contracts into the application module and `*CliHandler` stubs into the adapter module.

**Architecture:** Extend the existing request-family model with `RequestKind.CLIENT`, add explicit Gradle generators `designClient` and `designClientHandler`, and implement separate request-side and handler-side planners with fixed template ids. Follow the same migration discipline used for `query / query-handler`: helper-first templates, bounded override points, and planner/renderer/functional regression coverage.

**Tech Stack:** Kotlin, Gradle, JUnit 5, Gradle TestKit, Pebble templates

---

## File Structure

- Modify: `cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModels.kt`
- Modify: `cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssembler.kt`
- Modify: `cap4k-plugin-pipeline-core/src/test/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssemblerTest.kt`
- Modify: `cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kExtension.kt`
- Modify: `cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kProjectConfigFactory.kt`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kProjectConfigFactoryTest.kt`
- Create: `cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignClientArtifactPlanner.kt`
- Create: `cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignClientHandlerRenderModels.kt`
- Create: `cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignClientHandlerArtifactPlanner.kt`
- Create: `cap4k-plugin-pipeline-generator-design/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignClientArtifactPlannerTest.kt`
- Create: `cap4k-plugin-pipeline-generator-design/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignClientHandlerArtifactPlannerTest.kt`
- Modify: `cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePlugin.kt`
- Create: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/design/client.kt.peb`
- Create: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/design/client_handler.kt.peb`
- Modify: `cap4k-plugin-pipeline-renderer-pebble/src/test/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PebbleArtifactRendererTest.kt`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-sample/build.gradle.kts`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-sample/design/design.json`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-sample/codegen/templates/design/client.kt.peb`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-sample/codegen/templates/design/client_handler.kt.peb`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginFunctionalTest.kt`

### Task 1: Add Canonical And Gradle Support For Client Family

**Files:**
- Modify: `cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModels.kt`
- Modify: `cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssembler.kt`
- Modify: `cap4k-plugin-pipeline-core/src/test/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssemblerTest.kt`
- Modify: `cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kExtension.kt`
- Modify: `cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kProjectConfigFactory.kt`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kProjectConfigFactoryTest.kt`

- [ ] **Step 1: Add failing canonical tests for `CLIENT` mapping**

Add tests in `DefaultCanonicalAssemblerTest.kt` that prove:

- `cli`, `client`, and `clients` map into `RequestKind.CLIENT`
- `IssueToken` becomes `IssueTokenCli`
- command and query naming remain unchanged when client family is added
- the request order and existing aggregate wiring stay unchanged after adding the client family

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-core:test --tests "*client design tags map into CLIENT request kind*" --tests "*client naming keeps command and query mappings unchanged*" --rerun-tasks
```

Expected: FAIL because `RequestKind.CLIENT` does not exist yet.

- [ ] **Step 2: Implement minimal canonical support**

Change `RequestKind` to:

```kotlin
enum class RequestKind {
    COMMAND,
    QUERY,
    CLIENT,
}
```

Change `DefaultCanonicalAssembler` request mapping so:

```kotlin
"cmd", "command" -> RequestKind.COMMAND
"qry", "query" -> RequestKind.QUERY
"cli", "client", "clients" -> RequestKind.CLIENT
```

and:

```kotlin
RequestKind.COMMAND -> "${entry.name}Cmd"
RequestKind.QUERY -> "${entry.name}Qry"
RequestKind.CLIENT -> "${entry.name}Cli"
```

- [ ] **Step 3: Add failing config tests for `designClient` and `designClientHandler`**

Add tests that prove:

- `designClient.enabled` and `designClientHandler.enabled` default to `false`
- enabling both wires generator ids `design-client` and `design-client-handler`
- `designClient` requires `project.applicationModulePath`
- `designClientHandler` requires enabled `designClient`

Use these exact failure messages:

```kotlin
"project.applicationModulePath is required when designClient is enabled."
"project.adapterModulePath is required when designClientHandler is enabled."
"designClient generator requires enabled designJson source."
"designClientHandler generator requires enabled designClient generator."
```

- [ ] **Step 4: Implement the Gradle DSL and config wiring**

Add generator extensions:

```kotlin
val designClient: DesignClientGeneratorExtension
val designClientHandler: DesignClientHandlerGeneratorExtension
```

Add DSL blocks:

```kotlin
fun designClient(block: DesignClientGeneratorExtension.() -> Unit)
fun designClientHandler(block: DesignClientHandlerGeneratorExtension.() -> Unit)
```

Wire generator ids in `Cap4kProjectConfigFactory`:

```kotlin
"design-client"
"design-client-handler"
```

with the dependency rules from Step 3.

- [ ] **Step 5: Run focused API and config tests**

Run:

```powershell
./gradlew :cap4k-plugin-pipeline-core:test :cap4k-plugin-pipeline-gradle:test --tests "*client*" --rerun-tasks
```

Expected: PASS for the new client-family canonical and config tests, including the real `DefaultCanonicalAssembler` tag-to-kind and naming assertions.

- [ ] **Step 6: Commit the canonical/DSL slice**

```bash
git add cap4k-plugin-pipeline-api/src/main/kotlin/com/only4/cap4k/plugin/pipeline/api/PipelineModels.kt cap4k-plugin-pipeline-core/src/main/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssembler.kt cap4k-plugin-pipeline-core/src/test/kotlin/com/only4/cap4k/plugin/pipeline/core/DefaultCanonicalAssemblerTest.kt cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kExtension.kt cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kProjectConfigFactory.kt cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kProjectConfigFactoryTest.kt
git commit -m "feat: add design client family config"
```

### Task 2: Add Client And Client-Handler Planners

**Files:**
- Create: `cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignClientArtifactPlanner.kt`
- Create: `cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignClientHandlerRenderModels.kt`
- Create: `cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignClientHandlerArtifactPlanner.kt`
- Create: `cap4k-plugin-pipeline-generator-design/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignClientArtifactPlannerTest.kt`
- Create: `cap4k-plugin-pipeline-generator-design/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignClientHandlerArtifactPlannerTest.kt`
- Modify: `cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePlugin.kt`

- [ ] **Step 1: Add failing planner tests**

Request-side test should assert:

- `generatorId == "design-client"`
- `templateId == "design/client.kt.peb"`
- output path ends with:
  - `application/distributed/clients/authorize/IssueTokenCli.kt`

Handler-side test should assert:

- `generatorId == "design-client-handler"`
- `templateId == "design/client_handler.kt.peb"`
- output path ends with:
  - `adapter/application/distributed/clients/authorize/IssueTokenCliHandler.kt`
- context includes:
  - `packageName = "com.acme.demo.adapter.application.distributed.clients.authorize"`
  - `typeName = "IssueTokenCliHandler"`
  - `clientTypeName = "IssueTokenCli"`
  - import for `com.acme.demo.application.distributed.clients.authorize.IssueTokenCli`

Request-side test should also assert context includes:

- `packageName = "com.acme.demo.application.distributed.clients.authorize"`

- [ ] **Step 2: Run focused planner tests and confirm they fail**

```powershell
./gradlew :cap4k-plugin-pipeline-generator-design:test --tests "*client artifacts into application distributed clients path" --tests "*client handler artifacts into adapter distributed clients path" --rerun-tasks
```

Expected: FAIL because planners do not exist yet.

- [ ] **Step 3: Implement the planners and register them**

`DesignClientArtifactPlanner` should:

- filter `RequestKind.CLIENT`
- emit `design/client.kt.peb`
- write to:
  - `<applicationRoot>/src/main/kotlin/<base>/application/distributed/clients/<package>/<TypeName>.kt`
- set render package to:
  - `<basePackage>.application.distributed.clients.<package>`

`DesignClientHandlerArtifactPlanner` should:

- filter `RequestKind.CLIENT`
- emit `design/client_handler.kt.peb`
- write to:
  - `<adapterRoot>/src/main/kotlin/<base>/adapter/application/distributed/clients/<package>/<TypeName>Handler.kt`
- set render package to:
  - `<basePackage>.adapter.application.distributed.clients.<package>`

Register both in `PipelinePlugin.kt` beside the existing design-family planners.

- [ ] **Step 4: Re-run focused planner tests and confirm they pass**

```powershell
./gradlew :cap4k-plugin-pipeline-generator-design:test --tests "*client artifacts into application distributed clients path" --tests "*client handler artifacts into adapter distributed clients path" --rerun-tasks
```

Expected: PASS.

- [ ] **Step 5: Commit the planner slice**

```bash
git add cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignClientArtifactPlanner.kt cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignClientHandlerRenderModels.kt cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignClientHandlerArtifactPlanner.kt cap4k-plugin-pipeline-generator-design/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignClientArtifactPlannerTest.kt cap4k-plugin-pipeline-generator-design/src/test/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignClientHandlerArtifactPlannerTest.kt cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePlugin.kt
git commit -m "feat: add design client planners"
```

### Task 3: Add Helper-First Client Templates And Functional Coverage

**Files:**
- Create: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/design/client.kt.peb`
- Create: `cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/design/client_handler.kt.peb`
- Modify: `cap4k-plugin-pipeline-renderer-pebble/src/test/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PebbleArtifactRendererTest.kt`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-sample/build.gradle.kts`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-sample/design/design.json`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-sample/codegen/templates/design/client.kt.peb`
- Create: `cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-sample/codegen/templates/design/client_handler.kt.peb`
- Modify: `cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginFunctionalTest.kt`

- [ ] **Step 1: Add failing renderer tests**

Add tests that prove:

- `design/client.kt.peb` renders `RequestParam<Response>`
- `design/client.kt.peb` emits helper-driven imports through `imports(imports)`
- `design/client.kt.peb` renders request/response field types through `type()`
- `design/client.kt.peb` preserves Kotlin-ready `defaultValue` expressions in generated fields
- `design/client_handler.kt.peb` renders `RequestHandler<IssueTokenCli.Request, IssueTokenCli.Response>`
- handler template imports the generated `IssueTokenCli` from the render-model import list
- empty response cases remain valid for both request-side and handler-side output

- [ ] **Step 2: Implement bounded preset templates**

`client.kt.peb` must use:

```pebble
{{ use("com.only4.cap4k.ddd.core.application.RequestParam") -}}
package {{ packageName }}
{% for import in imports(imports) %}
import {{ import }}
{% endfor %}
```

and generate:

```kotlin
object {{ typeName }} {
    data class Request(
        val account: {{ type(field) | raw }} = {{ field.defaultValue | raw }}
    ) : RequestParam<Response>
    data class Response(
        val token: {{ type(field) | raw }} = {{ field.defaultValue | raw }}
    )
}
```

The real template should keep the old-family object contract and use loops over `requestFields`, `responseFields`, `requestNestedTypes`, and `responseNestedTypes`, not hard-coded field names.

`client_handler.kt.peb` must use:

```pebble
{{ use("org.springframework.stereotype.Service") -}}
{{ use("com.only4.cap4k.ddd.core.application.RequestHandler") -}}
package {{ packageName }}
{% for import in imports(imports) %}
import {{ import }}
{% endfor %}
```

and generate:

```kotlin
@Service
class {{ typeName }} : RequestHandler<{{ clientTypeName }}.Request, {{ clientTypeName }}.Response>
```

The real handler template must import the generated `*Cli` type through helper-driven imports, not inline fully-qualified references.

- [ ] **Step 3: Add failing functional tests**

Add functional tests that prove:

- `cap4kPlan` includes `design/client.kt.peb` and `design/client_handler.kt.peb`
- `cap4kGenerate` writes:
  - `IssueTokenCli.kt`
  - `IssueTokenCliHandler.kt`
- override templates replace both bounded ids
- invalid config fails when:
  - `designClient` lacks `applicationModulePath`
  - `designClientHandler` is enabled without `designClient`
- the fixture build enables both generators explicitly and wires both module paths through `cap4k { generators { ... } }`

- [ ] **Step 4: Update the fixture and override templates**

Update `design-sample/build.gradle.kts` so the functional fixture enables the bounded pair explicitly:

```kotlin
cap4k {
    project {
        basePackage.set("com.only4.demo")
        applicationModulePath.set("demo-application")
        adapterModulePath.set("demo-adapter")
    }
    sources {
        designJson {
            enabled.set(true)
            files.from("design/design.json")
        }
    }
    generators {
        designClient {
            enabled.set(true)
        }
        designClientHandler {
            enabled.set(true)
        }
    }
    templates {
        overrideDirs.from("codegen/templates")
    }
}
```

Update `design-sample/design/design.json` with one representative entry:

```json
{
  "tag": "cli",
  "package": "authorize",
  "name": "IssueToken",
  "desc": "issue token client",
  "aggregates": ["Order"],
  "requestFields": [
    { "name": "account", "type": "String" }
  ],
  "responseFields": [
    { "name": "token", "type": "String" }
  ]
}
```

Add override templates containing marker comments:

```pebble
// override: representative client migration template
// override: representative client handler migration template
```

The fixture should stay minimal: one bounded client request, request fields, response fields, and one handler.

- [ ] **Step 5: Run renderer and functional tests**

```powershell
./gradlew :cap4k-plugin-pipeline-renderer-pebble:test --tests "*client*" --rerun-tasks
./gradlew :cap4k-plugin-pipeline-gradle:test --tests "*client family*" --rerun-tasks
```

Expected: PASS with generated files under distributed-client paths and override templates taking effect.

- [ ] **Step 6: Commit the template/functional slice**

```bash
git add cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/design/client.kt.peb cap4k-plugin-pipeline-renderer-pebble/src/main/resources/presets/ddd-default/design/client_handler.kt.peb cap4k-plugin-pipeline-renderer-pebble/src/test/kotlin/com/only4/cap4k/plugin/pipeline/renderer/pebble/PebbleArtifactRendererTest.kt cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-sample/build.gradle.kts cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-sample/design/design.json cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-sample/codegen/templates/design/client.kt.peb cap4k-plugin-pipeline-gradle/src/test/resources/functional/design-sample/codegen/templates/design/client_handler.kt.peb cap4k-plugin-pipeline-gradle/src/test/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePluginFunctionalTest.kt
git commit -m "test: cover design client migration flow"
```

### Task 4: Run Full Verification For The Slice

**Files:**
- Verify only: `cap4k-plugin-pipeline-api`
- Verify only: `cap4k-plugin-pipeline-core`
- Verify only: `cap4k-plugin-pipeline-generator-design`
- Verify only: `cap4k-plugin-pipeline-renderer-pebble`
- Verify only: `cap4k-plugin-pipeline-gradle`

- [ ] **Step 1: Run full module tests**

```powershell
./gradlew :cap4k-plugin-pipeline-api:test
./gradlew :cap4k-plugin-pipeline-core:test
./gradlew :cap4k-plugin-pipeline-generator-design:test
./gradlew :cap4k-plugin-pipeline-renderer-pebble:test
./gradlew :cap4k-plugin-pipeline-gradle:test
```

Expected: all PASS.

- [ ] **Step 2: Run combined verification**

```powershell
./gradlew :cap4k-plugin-pipeline-api:test :cap4k-plugin-pipeline-core:test :cap4k-plugin-pipeline-generator-design:test :cap4k-plugin-pipeline-renderer-pebble:test :cap4k-plugin-pipeline-gradle:test
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Confirm branch cleanliness and landed commits**

```powershell
git status --short --branch
git log --oneline --max-count=8
```

Expected:

- clean working tree
- recent commits covering:
  - canonical client family support
  - client-family DSL/config
  - client-family planners
  - client-family templates
  - functional migration coverage

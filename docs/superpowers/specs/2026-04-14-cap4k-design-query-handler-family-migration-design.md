# Cap4k Design Query-Handler Family Migration Design

Date: 2026-04-14
Status: Draft for review

## Summary

The representative request-template migration slice is now complete for:

- `design/command.kt.peb`
- `design/query.kt.peb`
- `design/query_list.kt.peb`
- `design/query_page.kt.peb`

That work gave the pipeline a stable landing zone for the old request-side design family.

Bootstrap or arch-template migration is intentionally deferred onto its own track.

The next practical mainline step is to migrate the old query-handler family into the pipeline in a way that:

- preserves old migration value
- keeps template routing framework-owned
- does not make the existing `design` generator absorb adapter-side responsibilities implicitly

The key design decision is:

- add a separate bounded generator for query handlers
- keep it inside the current design-generator implementation module
- expose it as a separate public generator contract

For this slice, the new pipeline contract should add:

- public DSL block: `generators.designQueryHandler`
- generator id: `design-query-handler`
- bounded template ids:
  - `design/query_handler.kt.peb`
  - `design/query_list_handler.kt.peb`
  - `design/query_page_handler.kt.peb`

The variant-selection rule should remain Kotlin-owned and conservative:

- `*PageQry` -> `design/query_page_handler.kt.peb`
- `*ListQry` -> `design/query_list_handler.kt.peb`
- other query requests -> `design/query_handler.kt.peb`

This is an internal framework rule, not a revived user-configurable template-routing DSL.

## Why This Slice

The old `cap4k-plugin-codegen` design path did not stop at request objects.

It also generated an adapter-side query-handler family:

- `query_handler.kt.peb`
- `query_list_handler.kt.peb`
- `query_page_handler.kt.peb`

That behavior currently exists in:

- [QueryHandlerGenerator.kt](../../../cap4k-plugin-codegen/src/main/kotlin/com/only4/cap4k/plugin/codegen/generators/design/QueryHandlerGenerator.kt)

The old generator also routed those templates by query-name pattern and emitted adapter-side service stubs that implement:

- `Query<Request, Response>`
- `ListQuery<Request, Response>`
- `PageQuery<Request, Response>`

The current pipeline still lacks that family entirely.

Today the pipeline can claim:

- query request families have a stable migration landing zone
- query request override templates can be migrated realistically

But it still cannot claim:

- old query-handler families have a stable migration landing zone
- adapter-side query-handler override templates have a bounded pipeline equivalent

This slice closes that gap without jumping into bootstrap migration or broader old-codegen parity work.

## Goals

- Give the old query-handler family a stable pipeline landing zone
- Keep the routing contract bounded and framework-owned
- Keep request-side `design` generation and adapter-side query-handler generation separately controllable
- Require adapter module configuration only when the handler family is enabled
- Preserve migration value for default, list, and page query-handler variants
- Keep template override customization compatible with `overrideDirs`
- Add renderer and functional fixtures that prove end-to-end request-plus-handler migration for the query family

## Non-Goals

This slice will not:

- implement bootstrap or arch-template migration
- reopen pipeline stage order or generator-core architecture
- widen `use()` beyond thin explicit-import support inside design templates
- add sibling design-entry type support
- add a public `queryVariant` field to the canonical model
- migrate `client_handler`, `domain_event_handler`, `validator`, `api_payload`, or other old design families
- add command handlers or other new artifact families
- restore regex or pattern-based user-configurable template routing
- require a new Gradle subproject for this slice

## Current State

### Old Generator

The old query-handler generator produces one handler per query design entry and routes among three templates:

- `templates/query_handler.kt.peb`
- `templates/query_list_handler.kt.peb`
- `templates/query_page_handler.kt.peb`

That routing currently lives in:

- [QueryHandlerGenerator.kt](../../../cap4k-plugin-codegen/src/main/kotlin/com/only4/cap4k/plugin/codegen/generators/design/QueryHandlerGenerator.kt:50)

The old templates are adapter-side service stubs that reference the generated query request type and implement different query interfaces:

- [query_handler.kt.peb](../../../cap4k-plugin-codegen/src/main/resources/templates/query_handler.kt.peb)
- [query_list_handler.kt.peb](../../../cap4k-plugin-codegen/src/main/resources/templates/query_list_handler.kt.peb)
- [query_page_handler.kt.peb](../../../cap4k-plugin-codegen/src/main/resources/templates/query_page_handler.kt.peb)

The old behavior is important because it means the historical migration target is not only request objects.

It is the request-plus-handler query family.

### Current Pipeline

The current pipeline runner registers these generators:

- `design`
- `aggregate`
- `drawing-board`
- `flow`

That registration currently lives in:

- [PipelinePlugin.kt](../../../cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePlugin.kt:107)

The current design planner only emits application-side request artifacts:

- `design/command.kt.peb`
- `design/query.kt.peb`
- `design/query_list.kt.peb`
- `design/query_page.kt.peb`

That planning currently lives in:

- [DesignArtifactPlanner.kt](../../../cap4k-plugin-pipeline-generator-design/src/main/kotlin/com/only4/cap4k/plugin/pipeline/generator/design/DesignArtifactPlanner.kt)

The current Gradle config requires:

- `project.applicationModulePath` when `design` is enabled
- `project.adapterModulePath` only when `aggregate` is enabled

That validation currently lives in:

- [Cap4kProjectConfigFactory.kt](../../../cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/Cap4kProjectConfigFactory.kt:49)

So the current pipeline has no bounded way to express adapter-side query-handler migration.

## Scope Decision

Three approaches were considered.

### Option 1: Expand the existing `design` generator to also emit query handlers

Keep one public generator and let `design` generate both application requests and adapter query handlers.

Pros:

- smallest public surface change
- reuses existing design module and config

Cons:

- makes `design` broader and less legible
- turns an application-side generator into a mixed application-plus-adapter generator
- makes `adapterModulePath` validation harder to explain cleanly

### Option 2: Add a separate bounded public generator for query handlers

Expose a new generator contract for the query-handler family while keeping the implementation inside the existing design-generator module.

Pros:

- preserves a clean public responsibility boundary
- keeps request and handler families independently controllable
- keeps adapter-module requirements local to the handler family
- matches the repo rule that users enable or disable generators rather than inject custom behavior

Cons:

- adds one new generator block to the DSL
- adds one more registered planner to the pipeline runner

### Option 3: Put handlers into `application` temporarily

Generate query handlers under the application module as a transitional migration step.

Pros:

- avoids introducing adapter requirements immediately

Cons:

- breaks historical structure
- produces a knowingly unstable landing zone
- guarantees future churn when handlers are moved back to adapter

### Recommendation

Implement Option 2.

This is the smallest slice that preserves migration value while keeping framework boundaries understandable.

## Public Contract Decision

The new public contract for this slice is:

- add `generators.designQueryHandler`
- register generator id `design-query-handler`
- keep template override contract on `overrideDirs`
- keep template selection inside Kotlin planner code

Recommended DSL shape:

```kotlin
cap4k {
    project {
        applicationModulePath.set("demo-application")
        adapterModulePath.set("demo-adapter")
    }
    generators {
        design {
            enabled.set(true)
        }
        designQueryHandler {
            enabled.set(true)
        }
    }
}
```

The important contract rules are:

- `designQueryHandler` requires `design` to be enabled
- `designQueryHandler` requires `project.adapterModulePath`
- `design` alone still only requires `project.applicationModulePath`
- users may override only the bounded handler template ids
- users may not provide custom template-routing rules

This keeps the public model consistent with the current pipeline philosophy:

- users enable generator capabilities
- framework code owns planning rules

## Template Contract Decision

The bounded handler template family becomes:

- `design/query_handler.kt.peb`
- `design/query_list_handler.kt.peb`
- `design/query_page_handler.kt.peb`

The override contract remains:

```kotlin
templates {
    overrideDirs.from("codegen/templates")
}
```

That means a project may override these files directly:

- `codegen/templates/design/query_handler.kt.peb`
- `codegen/templates/design/query_list_handler.kt.peb`
- `codegen/templates/design/query_page_handler.kt.peb`

without receiving any user-owned mechanism for changing which template id gets selected.

## Variant Selection Decision

This slice should reuse the same conservative suffix semantics already approved for the request-side query family.

The rule is:

- if `typeName` ends with `PageQry`, use `design/query_page_handler.kt.peb`
- else if `typeName` ends with `ListQry`, use `design/query_list_handler.kt.peb`
- else use `design/query_handler.kt.peb`

This rule is:

- framework-owned
- bounded
- internal

It is not:

- regex exposed to users
- configurable in templates
- a public source-model field

To avoid drift between request and handler planning, the design-generator implementation should introduce a small internal shared resolver or enum for query-family variants.

That internal helper is allowed because:

- it does not change the public canonical model
- it makes request-family and handler-family routing stay aligned

## Packaging And Output Decision

The handler family should generate into the adapter module.

The output path shape should be:

```text
<adapterModulePath>/src/main/kotlin/<basePackage path>/adapter/queries/<request.packageName path>/<RequestTypeName>Handler.kt
```

Example:

```text
demo-adapter/src/main/kotlin/com/acme/demo/adapter/queries/order/read/FindOrderListQryHandler.kt
```

The generated package should be:

```text
<basePackage>.adapter.queries.<request.packageName>
```

The generated class name should be:

```text
<RequestTypeName>Handler
```

This keeps the adapter-side family explicit and avoids muddying the application-side request package.

## Request Dependency Decision

The query-handler family depends on the application-side query family.

Each generated handler references the generated request object:

- `FindOrderQry.Request`
- `FindOrderListQry.Request`
- `FindOrderPageQry.Request`

So this slice should make the dependency explicit:

- `designQueryHandler` requires `design`

This is better than allowing handler generation alone, because allowing it alone would produce a default contract that often references types the pipeline did not generate.

If a future slice wants standalone handler generation against pre-existing request types, that would require a separate compatibility design.

It is not part of this slice.

## Template Behavior Decision

The new handler presets should be helper-first templates, not byte-for-byte copies of old templates.

They should preserve the important migration semantics:

### `design/query_handler.kt.peb`

- imports `Service`
- imports `Query`
- imports the generated application query type explicitly
- implements `Query<{{ Query }}.Request, {{ Query }}.Response>`

### `design/query_list_handler.kt.peb`

- imports `Service`
- imports `ListQuery`
- imports the generated application query type explicitly
- implements `ListQuery<{{ Query }}.Request, {{ Query }}.Response>`

### `design/query_page_handler.kt.peb`

- imports `Service`
- imports `PageQuery`
- imports the generated application query type explicitly
- implements `PageQuery<{{ Query }}.Request, {{ Query }}.Response>`

All three should continue to use:

- `use()` for explicit framework-owned imports
- `imports()` for final import emission
- render-model response fields for stub response construction

## Internal Implementation Decision

This slice should not create a new Gradle subproject.

Instead it should:

- keep implementation in `cap4k-plugin-pipeline-generator-design`
- add a second generator provider inside that module for `design-query-handler`
- register that provider in [PipelinePlugin.kt](../../../cap4k-plugin-pipeline-gradle/src/main/kotlin/com/only4/cap4k/plugin/pipeline/gradle/PipelinePlugin.kt)

The implementation should be split into small pieces:

1. Gradle and config wiring
   - new DSL block
   - validation rules
   - module-map changes

2. Handler planner
   - adapter output-path computation
   - internal variant resolution
   - render-context assembly

3. Renderer presets
   - three new preset templates
   - renderer tests

4. Functional migration fixtures
   - adapter module sample
   - plan assertions
   - generate assertions
   - override assertions

## Verification Strategy

Verification should happen at three levels.

### Planner Tests

Add tests that prove:

- default query request -> `design/query_handler.kt.peb`
- list query request -> `design/query_list_handler.kt.peb`
- page query request -> `design/query_page_handler.kt.peb`
- non-suffix names are not misclassified
- output paths land under the adapter module

### Renderer Tests

Add tests that prove:

- each handler preset emits the correct query interface type
- helper-first imports remain stable
- list/page variants keep their bounded interface contracts

### Functional Tests

Add tests that prove:

- `cap4kPlan` includes handler artifacts when `designQueryHandler` is enabled
- `cap4kGenerate` renders request artifacts plus adapter-side handler artifacts
- override directories can replace all three handler templates end to end
- configuration fails clearly when `designQueryHandler` is enabled without `adapterModulePath`
- configuration fails clearly when `designQueryHandler` is enabled while `design` is disabled

## Risks

### Risk 1: The handler slice makes the design pipeline too broad

Mitigation:

- keep a separate public generator id
- keep request-side and handler-side generation independently controllable
- keep adapter validation local to `designQueryHandler`

### Risk 2: Request and handler variant routing drift apart

Mitigation:

- use one internal bounded query-variant resolver inside the design-generator module
- test request and handler planners against the same suffix cases

### Risk 3: This slice accidentally turns into general adapter-side migration

Mitigation:

- limit scope to query handlers only
- explicitly exclude client handlers, domain-event handlers, validators, and payload families

### Risk 4: Projects enable handler generation without the request family

Mitigation:

- make `designQueryHandler` depend on `design`
- fail in configuration rather than generating broken default references

## Expected Outcome

After this slice:

- the original mainline can say the old query request-plus-handler family has a stable pipeline landing zone
- adapter-side query-handler overrides have bounded template ids in the new contract
- request-family and handler-family query variation stays framework-owned rather than DSL-owned
- bootstrap migration remains separate
- future mainline decisions can choose between broader handler-family migration or later explicit semantics, without losing the bounded query-family contract

# Cap4k Design Client Family Migration

Date: 2026-04-14
Status: Draft for review

## Summary

The next original-mainline slice continues bounded old design-family migration after the completed representative request migration and query-handler family migration.

This slice migrates the old `client` and `client_handler` design families into the pipeline as explicit bounded request and handler families, without reopening generator-core architecture or turning project-specific layout details into user-programmable DSL.

## Goals

- Add explicit pipeline support for old `client` design entries.
- Generate request-side `*Cli` contracts into the application module.
- Generate handler-side `*CliHandler` stubs into the adapter module.
- Preserve old-family semantics:
  - `client` remains a request contract family
  - `client_handler` remains a `RequestHandler<Request, Response>` family
- Keep migration bounded through fixed template ids and helper-first templates.
- Prove the family with planner, renderer, and functional coverage.

## Non-Goals

- Do not reopen generator-core or pipeline-stage architecture.
- Do not widen `use()` beyond design templates.
- Do not merge `client` into `query` or `command` semantics.
- Do not add list/page-style client variants.
- Do not migrate `validator`, `api_payload`, `domain_event_handler`, or other families in this slice.
- Do not mix bootstrap / arch-template migration into this work.
- Do not turn real-project support-track findings into public framework rules unless explicitly approved.

## Current Context

The old codegen system already treats `client` as a distinct design family:

- design tags `cli`, `client`, and `clients` normalize into the same family
- request-side output becomes `*Cli`
- handler-side output becomes `*CliHandler`
- request-side templates use `RequestParam<Response>`
- handler-side templates implement `RequestHandler<Request, Response>`

The new pipeline currently has no equivalent client family:

- `design-json` canonical assembly only maps entries into `COMMAND` or `QUERY`
- the Gradle pipeline DSL only exposes `design`, `designQueryHandler`, `aggregate`, `drawingBoard`, and `flow`
- bounded query-family migration is already complete and established the pattern of:
  - explicit generator ids
  - bounded template ids
  - helper-first templates
  - adapter/application split by planner ownership

This slice should follow that pattern rather than inventing a new migration mechanism.

## Design Decision

The client migration should use an explicit bounded family with separate request-side and handler-side generators.

The public surface should add:

- `designClient`
- `designClientHandler`

The internal generator ids should be:

- `design-client`
- `design-client-handler`

This is preferred over folding client generation into the existing `design` generator because:

- `client` is not a query variant
- its default output layout differs from command/query
- a separate generator boundary keeps `design` from becoming an unbounded catch-all
- it mirrors the already-landed `design` / `designQueryHandler` split

## Source And Canonical Model

This slice should extend the existing request-family canonical model instead of introducing a new top-level client model.

`design-json` should accept the old aliases:

- `cli`
- `client`
- `clients`

Canonical assembly should map them into a new request family:

- `RequestKind.CLIENT`

The existing `RequestModel` remains sufficient:

- `packageName`
- `typeName`
- `description`
- `aggregateName`
- `requestFields`
- `responseFields`

The client naming rule should remain old-compatible and explicit:

- `IssueToken` -> `IssueTokenCli`
- `RefreshLoginSession` -> `RefreshLoginSessionCli`

No new public design-json fields are required in this slice.

## Public Gradle DSL

The Gradle DSL should expose two explicit generators:

```kotlin
cap4k {
    project {
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
}
```

Dependency rules:

- `designClient` requires:
  - enabled `designJson`
  - `project.applicationModulePath`
- `designClientHandler` requires:
  - enabled `designClient`
  - `project.adapterModulePath`

This slice should not force unrelated module paths when only one side is enabled.

## Request-Side Planner

Add a dedicated request-side planner for client contracts:

- class: `DesignClientArtifactPlanner`
- generator id: `design-client`

Responsibilities:

- filter canonical requests where `kind == CLIENT`
- emit bounded template id:
  - `design/client.kt.peb`
- write to application module paths:
  - `.../src/main/kotlin/<base>/application/distributed/clients/<package>/<TypeName>.kt`
- render package names under:
  - `<basePackage>.application.distributed.clients.<package>`

This planner should not be folded into the existing command/query planner because the output layout and family semantics differ.

## Handler-Side Planner

Add a dedicated handler-side planner for client handlers:

- class: `DesignClientHandlerArtifactPlanner`
- generator id: `design-client-handler`

Responsibilities:

- filter canonical requests where `kind == CLIENT`
- emit bounded template id:
  - `design/client_handler.kt.peb`
- write to adapter module paths:
  - `.../src/main/kotlin/<base>/adapter/application/distributed/clients/<package>/<TypeName>Handler.kt`
- render package names under:
  - `<basePackage>.adapter.application.distributed.clients.<package>`

The default handler layout deliberately follows the old family’s distributed-client shape rather than the query-handler layout.

## Template Contract

This slice introduces exactly two bounded preset templates:

- `design/client.kt.peb`
- `design/client_handler.kt.peb`

The request-side client template should preserve old-family contract shape while using the new helper-first contract:

- `use("com.only4.cap4k.ddd.core.application.RequestParam")`
- `imports()` for generated import lines
- `type()` for rendered field types
- Kotlin-ready `defaultValue`

The handler-side template should preserve:

- `use("org.springframework.stereotype.Service")`
- `use("com.only4.cap4k.ddd.core.application.RequestHandler")`
- explicit import of the generated `*Cli` type through helper-driven imports

The handler contract remains:

- `RequestHandler<{{ Client }}.Request, {{ Client }}.Response>`

No user-programmable routing DSL is introduced.

## Override Contract

Template override remains bounded and unchanged in mechanism:

```kotlin
templates {
    overrideDirs.from("codegen/templates")
}
```

User overrides may replace only:

- `design/client.kt.peb`
- `design/client_handler.kt.peb`

This slice does not introduce pattern-based family routing or arbitrary template selection rules.

## Validation Strategy

Validation should cover three levels.

### Planner / Unit

Add planner-level tests that verify:

- `cli`, `client`, and `clients` design tags all normalize into `RequestKind.CLIENT`
- `name` becomes `*Cli`
- request-side client artifacts land under application distributed-client paths
- handler-side client artifacts land under adapter application distributed-client paths
- handler generation is rejected when `designClientHandler` is enabled without `designClient`

### Renderer

Add renderer regression coverage that verifies:

- `design/client.kt.peb` renders `RequestParam<Response>`
- `design/client_handler.kt.peb` renders `RequestHandler<Request, Response>`
- helper-driven imports are emitted through `imports()`
- empty response cases remain valid

### Functional

Add a representative functional fixture that:

- enables both `designClient` and `designClientHandler`
- supplies at least one client design entry
- verifies generated files such as:
  - `IssueTokenCli.kt`
  - `IssueTokenCliHandler.kt`
- verifies override templates can replace both bounded template ids

## Recommended Fixture Shape

The first representative fixture should stay minimal:

- one client request
- request fields
- response fields
- one bounded handler

This slice does not need list/page-like client variants, aggregate-heavy payloads, or project-specific distributed-client conventions to prove the migration contract.

## Non-Default Follow-Up

After this slice, likely adjacent design-family migration candidates include:

- `validator`
- `api_payload`

Those remain separate follow-up slices and should not be mixed into this implementation.

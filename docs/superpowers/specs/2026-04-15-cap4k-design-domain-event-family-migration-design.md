# Cap4k Design Domain Event Family Migration

Date: 2026-04-15
Status: Draft for review

## Summary

The next original-mainline slice continues bounded old design-family migration after the completed `api_payload` migration.

This slice migrates the old `domain_event` and `domain_event_handler` families into the pipeline as bounded event-side and handler-side families, without reopening generator-core boundaries or redefining the old handler contract.

## Goals

- Add explicit pipeline support for the old `domain_event` design family.
- Generate domain-event artifacts into the domain module.
- Generate domain-event-handler artifacts into the application module.
- Preserve old-family semantics:
  - event package families continue to end in `events`
  - event handler names continue to use `Subscriber`
  - handler contract remains Spring `@Service` + `@EventListener`
- Keep migration bounded through fixed generator ids and fixed template ids.
- Prove the family with planner, renderer, and functional coverage.

## Non-Goals

- Do not reopen generator-core or pipeline-stage architecture.
- Do not widen `use()` beyond design templates.
- Do not rename `Subscriber` handlers or redesign the old event contract in this slice.
- Do not move domain-event handlers into adapter or any new module role.
- Do not merge `domain_event` back into aggregate generation.
- Do not mix bootstrap, support-track integration work, or exploratory parity backlog items into this slice.

## Current Context

The old codegen system already treats `domain_event` and `domain_event_handler` as a paired design family:

- event-side output is generated from `DomainEventDesign`
- event naming is old-compatible:
  - names ending with `Evt` or `Event` keep that ending
  - other names gain `DomainEvent`
- event artifacts are generated into the domain module
- handler artifacts are generated into the application module
- handler names use `{{ DomainEvent }}Subscriber`
- handler templates use Spring `@Service` and `@EventListener`

The new pipeline currently has no equivalent domain-event family.

The design-generator mainline already established a migration pattern through:

- bounded generator ids
- bounded template ids
- helper-first template contracts
- planner ownership of output paths
- regression coverage at planner, renderer, and functional levels

The domain-event migration should follow that pattern instead of folding event generation into aggregate generation or inventing a new runtime contract.

## Design Decision

This slice should use explicit bounded event-side and handler-side generators rather than folding them into existing aggregate or design request generators.

The public surface should add:

- `designDomainEvent`
- `designDomainEventHandler`

The internal generator ids should be:

- `design-domain-event`
- `design-domain-event-handler`

This is preferred because:

- `domain_event` is a distinct old design family
- event-side and handler-side output live in different module roles
- keeping them separate preserves planner ownership clarity
- it mirrors the already-landed split patterns such as `designQueryHandler`, `designClient`, and `designApiPayload`

## Source And Canonical Model

This slice should add a dedicated canonical domain-event slice instead of forcing domain events into `RequestModel` or aggregate canonical models.

The canonical model should gain:

- `domainEvents: List<DomainEventModel>`

`DomainEventModel` should be minimal and contain only what this slice needs:

- `packageName`
- `typeName`
- `description`
- `aggregateName`
- `aggregatePackageName`
- `persist`
- `fields`

This slice should continue to use `FieldModel` for event fields so it can reuse the current field parsing, type resolution, nested-type handling, and default-value pipeline.

The source contract should remain narrow:

- only standard `tag == "domain_event"` is accepted in this slice

No additional alias support is introduced in this work.

The event naming rule should remain old-compatible:

- `OrderCreated` -> `OrderCreatedDomainEvent`
- `OrderCreatedEvt` -> `OrderCreatedEvt`
- `OrderCreatedEvent` -> `OrderCreatedEvent`

Handler naming remains derived from the event type name:

- `{{ DomainEventTypeName }}Subscriber`

## Public Gradle DSL

The Gradle DSL should expose two explicit generators:

```kotlin
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

Dependency rules:

- `designDomainEvent` requires enabled `designJson`
- `designDomainEvent` requires `project.domainModulePath`
- `designDomainEventHandler` requires enabled `designDomainEvent`
- `designDomainEventHandler` requires `project.applicationModulePath`

This slice should not require adapter module paths.

## Event-Side Planner

Add a dedicated planner for domain-event artifacts:

- class: `DesignDomainEventArtifactPlanner`
- generator id: `design-domain-event`

Responsibilities:

- read canonical `domainEvents`
- emit bounded template id:
  - `design/domain_event.kt.peb`
- write to domain module paths:
  - `.../src/main/kotlin/<base>/domain/<package>/events/<TypeName>.kt`
- render package names under:
  - `<basePackage>.domain.<package>.events`

The planner should provide render context with:

- `packageName`
- `typeName`
- `description`
- `aggregateName`
- `aggregateType`
- `persist`
- `imports`
- `fields`
- `nestedTypes`

The event-side planner should use the current pipeline nested-type contract for event fields rather than reviving the old flat nested-type resolver behavior.

## Handler-Side Planner

Add a dedicated planner for domain-event-handler artifacts:

- class: `DesignDomainEventHandlerArtifactPlanner`
- generator id: `design-domain-event-handler`

Responsibilities:

- read canonical `domainEvents`
- emit bounded template id:
  - `design/domain_event_handler.kt.peb`
- write to application module paths:
  - `.../src/main/kotlin/<base>/application/<package>/events/<TypeName>Subscriber.kt`
- render package names under:
  - `<basePackage>.application.<package>.events`

The planner should provide render context with:

- `packageName`
- `typeName`
- `domainEventTypeName`
- `domainEventType`
- `aggregateName`
- `description`
- `imports`

The handler naming contract remains:

- `{{ DomainEventTypeName }}Subscriber`

The old codegen implementation contains a minor internal inconsistency between handler package context and resolved full name.
This slice intentionally standardizes the pipeline contract on application-side `.../events` packages.

## Template Contract

This slice introduces exactly two bounded preset templates:

- `design/domain_event.kt.peb`
- `design/domain_event_handler.kt.peb`

The event-side template should preserve old-family shape while using the helper-first contract:

- `use("com.only4.cap4k.ddd.core.domain.aggregate.annotation.Aggregate")`
- `use("com.only4.cap4k.ddd.core.domain.event.annotation.DomainEvent")`
- explicit helper-driven import for aggregate type when needed
- `imports(imports)` for import rendering
- `type(field)` and Kotlin-ready `defaultValue`

The event-side contract should preserve:

- `@DomainEvent(persist = {{ persist }})`
- `@Aggregate(...)`
- ordinary `class {{ typeName }}(...)`
- first field:
  - `val entity: {{ Aggregate }}`

If event fields produce nested types, they should follow the current pipeline nested-type contract rather than the old free-form resolver:

- nested field paths must have exactly one level
- nested classes render under the event class body
- no old recursive `[]` path expansion is reintroduced in this slice

The handler-side template should preserve:

- `use("org.springframework.context.event.EventListener")`
- `use("org.springframework.stereotype.Service")`
- helper-driven explicit import of the generated domain event type through `imports(imports)`

The handler contract remains:

- `@Service`
- `@EventListener({{ domainEventTypeName }}::class)`
- `fun on(event: {{ domainEventTypeName }})`

No new event bus abstraction or distributed-event semantics are introduced.

## Override Contract

Template override remains bounded and unchanged in mechanism:

```kotlin
templates {
    overrideDirs.from("codegen/templates")
}
```

User overrides may replace only:

- `design/domain_event.kt.peb`
- `design/domain_event_handler.kt.peb`

This slice does not introduce pattern routing, alias routing, or runtime handler hooks.

## Validation Strategy

Validation should cover three levels.

### Planner / Unit

Add planner-level tests that verify:

- only `domain_event` entries land in canonical `domainEvents`
- old-compatible event naming rules are preserved
- event-side artifacts land under domain `.../events`
- handler-side artifacts land under application `.../events`
- handler names end in `Subscriber`
- handler generation is rejected when `designDomainEventHandler` is enabled without `designDomainEvent`
- enabling `designDomainEvent` without `domainModulePath` fails during configuration
- enabling `designDomainEventHandler` without `applicationModulePath` fails during configuration
- enabling either generator without enabled `designJson` fails during configuration

### Renderer

Add renderer regression coverage that verifies:

- `design/domain_event.kt.peb` renders `@DomainEvent` and `@Aggregate`
- the event class keeps `entity: Aggregate` as the first field
- `design/domain_event_handler.kt.peb` renders `@Service` and `@EventListener`
- helper-driven imports are emitted through `imports(imports)`
- override template resolution works for both bounded template ids

### Functional

Add a representative functional fixture that:

- enables both `designDomainEvent` and `designDomainEventHandler`
- supplies at least one `domain_event` design entry
- verifies generated files such as:
  - `OrderCreatedDomainEvent.kt`
  - `OrderCreatedDomainEventSubscriber.kt`
- verifies event-side output lands in the domain module
- verifies handler-side output lands in the application module
- verifies override templates can replace both bounded template ids
- verifies invalid configuration failures for the required module paths and generator dependencies

## Recommended Fixture Shape

The first representative fixture should stay minimal:

- one `domain_event` entry
- one aggregate reference
- one event-side file
- one handler-side file
- one or two event fields, with at most one nested event type if needed

This slice does not need distributed-event behavior, MQ semantics, or aggregate-side event parity expansion to prove the migration contract.

## Non-Default Follow-Up

After this slice, likely adjacent design-family migration candidates include whatever bounded design family remains after domain-event migration.

Possible later follow-up slices may revisit:

- whether old event aliases deserve explicit migration support
- whether event nested-type support should expand beyond the current one-level pipeline contract
- whether Spring listener handlers should evolve toward a different bounded contract

Those remain separate follow-up slices and should not be mixed into this implementation.

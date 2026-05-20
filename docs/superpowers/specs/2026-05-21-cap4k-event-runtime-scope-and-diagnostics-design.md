# Cap4k Event Runtime Scope And Diagnostics Design

## Goal

Stabilize cap4k event runtime semantics around explicit event flow, scoped attachment, reliable derived integration events, and actionable listener diagnostics.

The design makes the normal authoring model intentionally narrow:

1. Aggregate behavior records domain facts.
2. Explicit event listeners react to those facts.
3. Listeners route state changes into zero-trust commands.
4. Listeners attach outbound integration events through `Mediator.events.attach(...)`.
5. Runtime scopes decide when attached events are released or discarded.

## Background

Review of `cap4k-reference-content-studio` and cap4k event runtime exposed two related issues:

- A persisted domain event listener can attach an outbound integration event, but the current integration event release path is tied to UoW interception. Persisted domain events are dispatched after the original UoW release window, so listener-attached integration events may remain unreleased.
- Multi-listener failures are hard to diagnose. When one listener fails among several reactions to the same event, the exception does not reliably identify the event, listener class, listener method, or request being sent.

The same review also showed that declaration-driven shortcuts are premature for the current framework stage:

- `AutoAttach` turns persistence changes into domain facts outside aggregate behavior.
- `AutoRequest` turns event contracts into hidden request-routing rules.
- `AutoRelease` hides outbound integration event publication behind annotation scanning and converter creation.
- `IntegrationEventSupervisor.publish(payload)` gives business code a direct-publication path that competes with scoped `attach(...)`.

These capabilities increase hidden flow while the runtime still needs stronger scope, failure, and diagnostic semantics.

## Related Tracking Issues

- Multi-listener failure diagnostics: <https://github.com/LDmoxeii/cap4k/issues/56>
- Releasing integration events attached from persisted domain event subscribers: <https://github.com/LDmoxeii/cap4k/issues/61>

## Scope

This change covers runtime, tests, source skills, public authoring docs, and superpowers analysis docs.

Runtime scope:

- `ddd-core` event supervisors, event subscriber manager, event publisher, and related tests.
- `ddd-core` request supervisor diagnostics when requests are sent from event listeners.
- `ddd-core` public event annotations and public event supervisor APIs.
- `cap4k-ddd-starter` auto-configuration only if runtime wiring changes are required.

Documentation scope:

- `docs/superpowers/analysis/`.
- `docs/public/authoring/`.
- `skills/`.

Generator scope:

- Remove generation, fixtures, or sample assumptions that still teach `AutoAttach`, `AutoRequest`, `AutoRelease`, or `Mediator.events.publish(payload)`.

## Non-Goals

- Do not add per-listener event delivery records.
- Do not guarantee ordering between multiple listeners.
- Do not introduce a new process-manager layer.
- Do not preserve source or binary compatibility for removed auto annotations.
- Do not keep a deprecated compatibility mode for the removed public event routing APIs.
- Do not redesign HTTP, RabbitMQ, or RocketMQ transport adapters.
- Do not make request scope automatically publish events at request end.

## Terminology Decisions

### Domain Event

A domain event is a meaningful business fact recorded by domain behavior. It is not a persistence lifecycle event and not a technical continuation step.

### Integration Event

An integration event is a cross-boundary fact. Outbound integration events should be derived from domain facts or explicit application process results, then attached to the current reliable runtime boundary.

### Attach

`attach(...)` records an event payload in the current runtime scope. It does not mean immediate delivery. Runtime decides when to persist and release the attached event.

### Release

`release(...)` converts attached event payloads in a specific scope and boundary into persisted event records, then hands them to the event delivery pipeline at the correct transaction phase.

### Discard

`discard(...)` removes attached event payloads in a failed scope. Failed listener dispatch must not leak or later publish integration events attached by that failed dispatch.

### Publish

`EventPublisher.publish(EventRecord)` remains the internal delivery operation for persisted event records.

Public business-facing `IntegrationEventSupervisor.publish(payload, ...)` is removed. If an immediate out-of-context event API is ever needed again, it should be introduced later under an explicit advanced name such as `publishImmediately(...)`.

## Design Decisions

### Explicit Event Flow Only

Remove declaration-driven automatic event routing from stable runtime:

- Delete `AutoAttach`.
- Delete `AutoRequest` and `AutoRequests`.
- Delete `AutoRelease` and `AutoReleases`.
- Delete `DefaultPersistListenerManager` scanning and conversion logic for `AutoAttach`.
- Delete `DefaultEventSubscriberManager` scanning and conversion logic for `AutoRequest` and `AutoRelease`.

The replacement is explicit listener code:

```kotlin
@EventListener
fun handleContentPublicationReady(event: ContentPublicationReadyDomainEvent) {
    Mediator.cmd.send(ReleasePaidContentCommand.from(event))
}
```

```kotlin
@EventListener
fun publishContentPublicationReady(event: ContentPublicationReadyDomainEvent) {
    Mediator.events.attach(ContentPublicationReadyIntegrationEvent.from(event))
}
```

This makes business reactions visible to review, analysis, diagnostics, and tests.

### Public Integration Event API

The business-facing event API should be narrowed:

- Keep `Mediator.events.attach(...)`.
- Keep `IntegrationEventSupervisor.attach(...)`.
- Keep `IntegrationEventManager.release()` for runtime use.
- Remove `IntegrationEventSupervisor.publish(payload, ...)`.
- Remove `Mediator.events.publish(payload, ...)` forwarding.
- Keep `EventPublisher.publish(EventRecord)` as internal persisted-record delivery.

Business code should not choose between `attach` and `publish`. The stable model is attach-only for outbound integration events.

### Event Runtime Context

Introduce an event runtime context with a ThreadLocal stack of scopes.

The context must separate data by boundary:

- request scope;
- domain dispatch scope;
- ambient compatibility scope for non-request runtime paths;
- diagnostic context for current event, listener, and request execution.

The context should support:

- push scope;
- pop scope;
- release current or selected scope;
- discard current or selected scope;
- clear all scopes;
- current diagnostic view.

The data model must not force domain and integration events into the same attachment shape:

- Domain event attachments remain entity-bound because UoW release needs persisted entities.
- Integration event attachments are scope-bound and not entity-bound.
- Schedule time belongs on the attachment entry, not in a global map keyed by payload.

Attachment entries should preserve identity and order. Do not use `MutableSet<Any>` as the core storage, because two equal data-class events can be different business emissions and should not be silently deduplicated.

### Request Scope

`RequestSupervisor.internalSend(...)` should push a request scope around handler execution.

Request scope is not an automatic publish boundary. It is a containment and diagnostics boundary:

- command execution attaches events into the request scope;
- UoW interceptors release the relevant attachments during `Mediator.uow.save()`;
- request scope close should detect unreleased attachments, discard them, and report a warning or error suitable for tests.

This prevents missing `Mediator.uow.save()` from silently leaking attached events.

### UoW Release Boundaries

UoW release remains explicit through existing interceptors:

- `DomainEventUnitOfWorkInterceptor.postEntitiesPersisted(...)` releases domain events for persisted entities.
- `IntegrationEventUnitOfWorkInterceptor.postInTransaction(...)` releases integration events attached in the current UoW/request scope.

Do not move these releases to request completion. Request completion is too broad and would weaken the meaning of UoW as the write persistence boundary.

### Domain Dispatch Scope

`DefaultEventPublisher.internalPublish4DomainEvent(...)` must push a domain dispatch scope before dispatching subscribers.

Target flow:

```text
publish domain EventRecord
  -> push DOMAIN_DISPATCH scope
  -> dispatch all subscribers
  -> if dispatch failed:
       discard DOMAIN_DISPATCH scope
       do not mark domain EventRecord delivered
       throw event dispatch exception
  -> if dispatch succeeded:
       persist integration EventRecords attached in DOMAIN_DISPATCH scope
       mark domain EventRecord delivered
       persist domain delivery state
       publish committed integration marker after commit or fallback
  -> pop DOMAIN_DISPATCH scope
```

Derived integration event records should be persisted before the domain event record is marked delivered. If derived integration event persistence fails, the domain event must not be marked delivered.

This favors duplicate-safe retry over lost outbound facts.

### Listener Failure Semantics

cap4k remains event-level for domain event delivery. It does not track per-listener delivery state.

When one listener fails:

- continue invoking other listeners only if current dispatch policy remains "collect all failures";
- collect all failures;
- discard integration events directly attached in the failed domain dispatch scope;
- throw a diagnostic exception;
- leave the domain event eligible for retry when it is persisted.

Some successful listeners may already have sent commands that committed changes before another listener failed. This is accepted under the current model and must be handled by zero-trust idempotent commands on retry.

Per-listener delivery state would be a separate future design and is outside this scope.

### Spring Event Listener Diagnostics

Diagnostics must identify the actual Spring `@EventListener` method, not only the cap4k bridge subscriber.

`DefaultEventSubscriberManager` currently bridges domain and integration payloads into Spring by registering a subscriber that calls `applicationEventPublisher.publishEvent(event)`. Wrapping only that bridge sees too little context.

Use Spring event listener extension points to wrap actual listener invocation. Spring exposes `EventListenerFactory.createApplicationListener(beanName, type, method)` to create listeners for annotated methods, and `ApplicationListenerMethodAdapter` delegates to the listener method. A cap4k listener adapter or factory should record listener metadata and wrap failures with cap4k diagnostics.

Required diagnostic fields:

- event record id when available;
- event payload class;
- event type name;
- listener bean name;
- listener class;
- listener method;
- request param class when a request is sent from the listener;
- request handler class when available;
- original cause chain.

Programmatic `EventSubscriber<T>` failures should use the same diagnostic exception shape.

### Request Diagnostics From Listeners

When `Mediator.cmd`, `Mediator.qry`, or `Mediator.requests` is called inside an event listener and fails, the exception context should include:

- current event diagnostic context;
- request param class;
- resolved request handler class when available;
- whether the request was synchronous, scheduled, delayed, or retry/resume driven when known.

This satisfies the #56 requirement to distinguish one failed business reaction from another listener on the same event.

### Spring Listener Return Values

cap4k should not teach or rely on Spring `@EventListener` return-value event publication.

Listener methods should return `Unit` and publish follow-up work explicitly through:

- `Mediator.cmd.send(...)`;
- `Mediator.qry.send(...)` or `Mediator.requests.send(...)` for read/other request boundaries;
- `Mediator.events.attach(...)` for outbound integration events.

If feasible, startup validation should reject `@EventListener` methods with non-`Unit` return types in scanned cap4k event listener packages. If full rejection is too invasive, diagnostics and skills must still explicitly prohibit this pattern.

### Supplier Attach Semantics

Supplier overloads must not attach the lambda object as payload.

Supplier payloads should be represented as lazy attachment entries and evaluated during release. Annotation validation should apply to the produced event payload.

This applies to both domain and integration event attachment APIs.

## Documentation Synchronization

This change has a mandatory drift-control requirement. Runtime code is not complete until all three documentation surfaces are synchronized.

### Analysis Docs

Update `docs/superpowers/analysis/` to reflect real runtime behavior after the change.

Required updates:

- Remove `AutoAttach`, `AutoRequest`, and `AutoRelease` from runtime flow maps.
- Remove `IntegrationEventSupervisor.publish` from public capability lists.
- Show integration events as attach-only from business code.
- Add scoped domain dispatch semantics for listener-derived integration events.
- Add listener diagnostics behavior for multi-listener failures.
- Clarify that Spring `@EventListener` receives domain/integration payloads through the cap4k event subscriber bridge.

Primary target:

- `docs/superpowers/analysis/2026-05-11-cap4k-runtime-support-and-integration-map.md`.

### Public Authoring Docs

Update `docs/public/authoring/` so project authors learn only the supported stable model.

Required updates:

- `Mediator.events` should say attach-only for business-facing integration event publication.
- Domain events should be described as emitted from aggregate behavior, not persistence lifecycle auto-attachment.
- Application event listeners should be explicit named reaction methods.
- Listeners should return `Unit`.
- Listener-side follow-up should be explicit command/request calls or `Mediator.events.attach(...)`.
- Remove or avoid any public guidance that implies `AutoAttach`, `AutoRequest`, `AutoRelease`, or `publish(payload)`.

Likely targets:

- `docs/public/authoring/tactical-model.md`.
- `docs/public/authoring/domain.md`.
- `docs/public/authoring/application.md`.
- Event-related example pages if they mention the removed API or old flow.

### Skills

Update source skills under `skills/`. Installed copies outside the repository are deployment artifacts and are not the source of truth.

Required updates:

- `skills/cap4k-modeling`: domain events come from domain behavior; no persistence-triggered auto facts.
- `skills/cap4k-implementation`: listener reactions must be explicit, named, and route writes through zero-trust commands.
- `skills/cap4k-service-integration`: outbound integration events use `Mediator.events.attach(...)`; no direct `publish(payload)`.
- `skills/cap4k-generation`: generators must not emit Auto annotations or teach automatic routing.
- `skills/cap4k-verification`: verification must scan for removed annotations and public `publish(payload)` usage.

Skills must keep rules on the normal activation path. Do not bury this only in references.

## Testing Requirements

Runtime tests should cover:

- integration events attached inside a persisted domain event listener are persisted and released after successful domain dispatch;
- integration events attached inside a failing domain dispatch are discarded;
- equal data-class integration event payloads attached twice are not silently deduplicated unless explicitly detached;
- nested request or command execution does not release or discard outer-scope attachments;
- missing UoW release leaves request-scope attachments detectable at request-scope close;
- supplier attach overload evaluates the supplier payload at release and validates the produced event annotation;
- `IntegrationEventSupervisor.publish(payload)` and `Mediator.events.publish(payload)` no longer compile or no longer exist;
- `AutoAttach`, `AutoRequest`, and `AutoRelease` no longer participate in runtime scanning;
- programmatic `EventSubscriber<T>` failure diagnostics include event and subscriber metadata;
- Spring `@EventListener` failure diagnostics include listener bean, class, method, event payload class, and original cause;
- request failures inside listeners include request param and handler metadata;
- listener return-value event publication is rejected or explicitly covered by validation/documentation tests.

Documentation tests or verification scripts should cover:

- no source skill mentions `AutoAttach`, `AutoRequest`, or `AutoRelease`;
- no public authoring doc recommends `Mediator.events.publish(payload)` or `IntegrationEventSupervisor.publish(payload)`;
- analysis runtime map no longer lists removed automatic routing capabilities;
- skills and public docs both state `Mediator.events.attach(...)` as the business-facing outbound integration event path.

## Acceptance Criteria

The change is complete when:

- business-facing integration event publication is attach-only;
- `AutoAttach`, `AutoRequest`, `AutoRequests`, `AutoRelease`, and `AutoReleases` are removed from stable runtime;
- domain and integration event attachments are scope-aware;
- domain dispatch releases listener-derived integration events on success;
- domain dispatch discards listener-derived integration events on failure;
- derived integration event records are persisted before the source domain event is marked delivered;
- multi-listener failures identify the failed listener class and method;
- request failures from event listeners identify the request param and handler when available;
- listener return-value event publication is prohibited by validation or documented as unsupported;
- runtime tests cover successful release, failure discard, nested scopes, and diagnostics;
- `docs/superpowers/analysis/`, `docs/public/authoring/`, and `skills/` are updated in the same implementation PR;
- issue #56 and issue #61 link this spec and remain open until implementation, release, and downstream verification are complete.

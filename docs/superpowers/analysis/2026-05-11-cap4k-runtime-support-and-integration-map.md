# cap4k Runtime Support And Integration Map

Date: 2026-05-11

This file maps the runtime support that generated or hand-written business code relies on.

## Starter Auto-Configuration

`cap4k-ddd-starter` imports these runtime families through Spring Boot auto-configuration:

| Family | Main capability |
|---|---|
| Mediator | Configures the global `Mediator` and Spring application context |
| Request | Request supervisor, request records, scheduling/compensation/archive |
| JPA repository / UoW | Repository supervisor, aggregate supervisor, factory supervisor, UoW, persist listeners, specifications |
| Domain event | Domain event supervisor, event records, event publisher, event subscribers, event schedule service |
| Integration event | Integration event supervisor, UoW interceptor, dispatch-scope release, HTTP/RabbitMQ/RocketMQ adapters |
| Domain service | Domain service supervisor |
| ID policy | UUID7 and optional snowflake-long strategies |
| JDBC locker | Distributed lock implementation and reentrant aspect |
| Saga | Saga supervisor and schedule service |
| Arch info | Optional `/cap4k/arch-info` endpoint |
| Web context cleanup | Clears domain/UoW/shared event runtime context after web request completion |

Business authoring should show which capability is being used instead of treating generated code as plain Spring CRUD.

## Main Runtime Switches

| Property | Default / behavior |
|---|---|
| `cap4k.ddd.application.jpa-uow.supportEntityInlinePersistListener` | `true`; enables `onCreate`/`onUpdate`/`onDelete` inline listener support |
| `cap4k.ddd.application.jpa-uow.supportValueObjectExistsCheckOnSave` | `true` |
| `cap4k.ddd.application.jpa-uow.retrieveCountWarnThreshold` | `3000` |
| `cap4k.ddd.domain.event.eventScanPackage` | empty by default; scan range for domain and integration events |
| `cap4k.ddd.domain.event.publisherThreadPoolSize` | `4` |
| `cap4k.ddd.integration.event.http.publishThreadPoolSize` | `4` |
| `cap4k.ddd.arch-info.enabled` | disabled unless explicitly true |

Schedule properties exist for request/domain-event/saga compensation and archive jobs. Authoring examples should keep defaults unless they demonstrate scheduling.

## Request Flow

`RequestSupervisor` supports:

- `send`;
- `async`;
- `schedule`;
- `delay`;
- `result`.

Commands, queries, and client/cli requests all ride on request params and handlers. The framework records requests for scheduled/async compensation through JPA request records.

```mermaid
flowchart TD
    Caller[Controller / Subscriber / Job / Command] --> Mediator[Mediator.cmd/qry/requests]
    Mediator --> Supervisor[DefaultRequestSupervisor]
    Supervisor --> Handler[RequestHandler bean]
    Handler --> Result[Response / side effect]
    Supervisor --> Record[(Request record)]
    Scheduler[Request schedule service] --> Record
    Scheduler --> Supervisor
```

## Saga Runtime Flow

`SagaSupervisor` supports:

- `send`;
- `async`;
- `schedule`;
- `delay`;
- `result`.

Saga execution is persisted in `__saga` and `__saga_process` when the JPA Saga module is used. The runtime is now a compensation-oriented persisted replay/retry/recovery coordinator. It is not a first-class "wait for external callback and resume at this step" workflow state machine.

```mermaid
flowchart TD
    Caller[Command / Job / System operation] --> SagaSupervisor[SagaSupervisor]
    SagaSupervisor --> SagaRecord[(Saga record)]
    SagaSupervisor --> Handler[SagaHandler]
    Handler --> Process[execProcess / execCompensableProcess]
    Process --> RequestSupervisor[RequestSupervisor]
    RequestSupervisor --> RequestHandler[Command / Query / Request handler]
    Process --> SagaProcess[(Saga process record)]
    Handler --> Compensation[requestCompensation code reason]
    Compensation --> SagaRecord
    Schedule[JpaSagaScheduleService] --> SagaManager[SagaManager.resume]
    Console[/cap4k/console/saga/retry] --> SagaManagerRetry[SagaManager.retry]
    Operator[/operator compensation/] --> SagaManagerComp[SagaManager.requestCompensation]
    SagaManager --> SagaSupervisor
    SagaManagerRetry --> SagaSupervisor
    SagaManagerComp --> SagaSupervisor
```

Runtime semantics confirmed from `DefaultSagaSupervisor` and the JPA Saga implementation:

- `send` creates a saga record and executes the handler immediately.
- `schedule` creates a saga record and schedules execution; records scheduled within the local threshold are marked executing immediately.
- `async` is the `SagaSupervisor` default `schedule(request, LocalDateTime.now())` path and returns the saga ID.
- `delay` is the `SagaSupervisor` default `schedule(request, LocalDateTime.now().plus(delay))` path and returns the saga ID.
- `SagaHandler.execProcess(processCode, request)` executes a child request through `RequestSupervisor`.
- `SagaHandler.execCompensableProcess(processCode, request, compensationCode, compensationRequest)` executes a forward child request and, on forward success, persists the final reverse compensation request for that process.
- `SagaHandler.requestCompensation(code, reason)` records explicit compensation intent, stops the forward path, and enters compensation immediately in the current Saga execution.
- an `EXECUTED` saga process is skipped on replay and its cached result is returned.
- if a saga process throws, the process and saga are recorded as `EXCEPTION`.
- `retry(uuid)` loads the persisted saga and re-enters execution directly through the retry path `internalSend(param, saga)`, so already executed process codes are skipped during the replayed handler run.
- scheduled saga resume scans runnable persisted sagas by `nextTryTime` and calls `SagaManager.resume`.
- `resume(saga, minNextTryTime)` first advances saga timing/state with `beginSaga(...)`, saves the saga, validates it, and only reschedules execution when `saga.isExecuting` is still true.
- if the saga is no longer runnable after `resume` advances state, `resume` stops after persisting state and does not re-enter the handler.
- `/cap4k/console/saga/retry?uuid=...` calls `SagaManager.retry(uuid)` for manual/operator retry.
- operator-driven compensation uses `SagaManager.requestCompensation(sagaId, code, reason)` and the same persisted state machine.

Runtime consequences of this implementation:

- retry restarts the saga handler from the persisted saga param rather than resuming from a separate step pointer.
- scheduled resume advances retry timing/state first and may stop without re-entering the handler when the saga is no longer executing.
- scheduled resume may continue forward replay or compensation replay depending on the persisted Saga state, including `COMPENSATION_REQUESTED` and `COMPENSATING`.
- successful `execProcess` calls are sticky by `processCode`; later retries return the cached result instead of re-sending the child request.
- successful `execCompensableProcess(...)` calls also persist the final compensation request at forward success time, so compensation does not need to reconstruct reverse inputs later.
- failed `execProcess` / forward execution persists exception state and rethrows; the next direct retry can run the handler again, while scheduled resume only does so when the saga remains executing after its state update.
- forward retry exhaustion does not automatically trigger compensation; the runtime keeps exhaustion / manual-repair semantics separate from compensation intent.
- compensation is triggered by explicit business or operator intent, not by exception taxonomy guessing or retry exhaustion.
- whole-saga recovery is exposed through scheduled `resume`, manual `retry(uuid)`, and operator-triggered `requestCompensation(...)`; there is no separate callback-step API in this runtime surface.

## Unit Of Work Flow

```mermaid
flowchart TD
    Command[Command handler] --> Change[Domain behavior changes aggregate]
    Command --> Save[Mediator.uow.save]
    Save --> Before[UnitOfWorkInterceptor.beforeTransaction]
    Before --> Tx[Transaction]
    Tx --> Specs[Specifications]
    Tx --> Persist[Persist/remove entities]
    Persist --> Lifecycle[Persist listeners and lifecycle hooks]
    Lifecycle --> Events[Attached domain/integration events]
    Tx --> After[UnitOfWorkInterceptor after phases]
```

Key points:

- factories enlist new aggregate roots into UoW;
- command handlers should not rely on manual transaction annotations as the primary write boundary;
- specifications and event publication are connected to UoW execution.

## Domain Event Flow

Domain events are domain facts emitted from aggregate behavior. They are recorded/published by the domain event supervisor and consumed through event subscribers.

```mermaid
flowchart TD
    Aggregate[Aggregate behavior] --> Register[registerDomainEvent / attach]
    Register --> Uow[Mediator.uow.save]
    Uow --> DomainSupervisor[DomainEventSupervisor]
    DomainSupervisor --> EventRecord[(Event record)]
    DomainSupervisor --> Publisher[EventPublisher]
    Publisher --> SpringEvent[ApplicationEventPublisher]
    SpringEvent --> Subscriber[application.subscribers.domain]
    Subscriber --> FollowUp[Mediator.cmd/qry/requests]
```

Domain event subscribers perform follow-up orchestration explicitly. They can send commands, queries, or generic requests through `Mediator.cmd`, `Mediator.qry`, and `Mediator.requests`, and they can attach cross-boundary integration events through `Mediator.events.attach(...)`.

Domain event dispatch owns a `DOMAIN_DISPATCH` runtime scope. Integration events attached by listeners during that scope are released by the runtime after all domain-event listeners finish. Business listeners should not branch with `if` logic to directly send integration messages; the current capability is explicit attachment plus runtime release.

## Integration Event Flow

Integration events represent cross-boundary messages. Business code contributes them by attaching event payloads through `Mediator.events.attach(...)`; dispatch to HTTP/RabbitMQ/RocketMQ publishers is a runtime concern after attachment is released.

```mermaid
flowchart TD
    Producer[Business code] --> Attach[Mediator.events.attach]
    Attach --> Scope[Event runtime attachment scope]
    Scope --> Release[Runtime release]
    Release --> IntegrationSupervisor[IntegrationEventSupervisor]
    IntegrationSupervisor --> EventRecord[(Event record)]
    EventRecord --> EventPublisher[DefaultEventPublisher]
    EventPublisher --> Adapter{Integration adapter}
    Adapter --> Http[HTTP callbacks]
    Adapter --> Rabbit[RabbitMQ]
    Adapter --> Rocket[RocketMQ]
    Http --> ConsumerEndpoint[/cap4k/integration-event/http/consume]
    ConsumerEndpoint --> SubscriberManager[EventSubscriberManager]
    SubscriberManager --> Subscriber[application.subscribers.integration]
    Subscriber --> FollowUp[Mediator.cmd/qry/requests]
```

Core annotations/classes:

- `@IntegrationEvent(value = "...", subscriber = "...")`;
- `IntegrationEventSupervisor.attach`;
- `EventSubscriber<Event>`;
- `IntegrationEventPublisher`;
- `IntegrationEventInterceptorManager`.

Business-facing authoring should stop at `Mediator.events.attach(...)`. Lower-level supervisor access, event-record persistence, and transport publication are framework/runtime responsibilities, not a normal application coding pattern.

## Event Runtime Scope And Diagnostics

Domain and integration event attachments now share one event runtime context with three scope types:

- `AMBIENT` scope holds attachments created outside an active request or event dispatch scope.
- `REQUEST` scope wraps request handler execution and is restored to the outer scope after the request completes.
- `DOMAIN_DISPATCH` scope wraps domain-event listener dispatch.

Attachment boundaries:

- domain event attachments are keyed by aggregate/entity identity and are released when the UoW/domain event supervisor releases events for persisted entities;
- integration event attachments are accumulated in the current event runtime scope and released through the integration event manager;
- when a persisted domain event dispatch succeeds, listener-attached integration events are released before the source domain event is marked delivered and before domain delivery is ended;
- when listener dispatch fails, unreleased listener-scope attachments are discarded and the persisted domain event remains undelivered and retryable;
- when integration event release fails, source domain event delivery is not ended and the source event is not marked delivered, so the persisted domain event remains retryable; derived integration records already persisted during release depend on transaction and repository behavior rather than being uniformly discarded;
- when an ambient scope completes with no remaining attachments, the runtime pops it; failures discard attachments from the failed scope instead of leaking them.

Diagnostics:

- programmatic `EventSubscriber` dispatch collects multi-listener failures instead of stopping at the first listener; `EventDispatchException` carries the event payload class and one `EventDispatchDiagnostic` scope snapshot, while each `EventSubscriberFailure` carries subscriber class and cause;
- Spring `@EventListener` invocation is wrapped by the cap4k adapter, so invocation diagnostics include listener bean, class, method, payload class, cause, and the same scope snapshot;
- `RequestDispatchException` diagnostics include request param class, handler class, cause, and a diagnostic snapshot; when the request is sent from an event listener, the nested request scope can inherit listener metadata so the failing command/query can still point back to the listener that sent it;
- diagnostics report failures without swallowing them, so event-level delivery and retry semantics remain controlled by the domain event dispatch result;
- listener return-value publication is rejected by the diagnostics adapter because cap4k does not support Spring's return-value event publication path.

Shared runtime reset:

- domain and integration event supervisors both reset through the shared `EventRuntimeContextManager`;
- reset clears all domain and integration attachments in the thread-local scope stack and removes the stack;
- reset is a cleanup/testing boundary, not a business-event delivery mechanism.

Runtime bridge:

- `EventSubscriberManager` scans integration event classes and registers an in-process subscriber that republishes the payload through Spring `ApplicationEventPublisher`;
- generated inbound subscriber skeletons use Spring `@EventListener`, so HTTP/Rabbit/Rocket consumption reaches `application.subscribers.integration` through this bridge;
- handwritten subscribers can still implement `EventSubscriber<Event>` directly when a project wants the lower-level runtime contract.

HTTP adapter endpoints:

| Endpoint | Purpose |
|---|---|
| `/cap4k/integration-event/http/consume?event=...&uuid=...` | Consume inbound event payload |
| `/cap4k/integration-event/http/subscribe?event=...&subscriber=...` | Register subscriber callback |
| `/cap4k/integration-event/http/unsubscribe?event=...&subscriber=...` | Remove subscriber |
| `/cap4k/integration-event/http/events` | List events |
| `/cap4k/integration-event/http/subscribers?event=...` | List subscribers |

If `ddd-integration-event-http-jpa` is present, HTTP subscriber registration is backed by a JPA table. Local examples need only the minimal compatible DDL, adapted to H2 when H2 is used.

## Callback Main Path Example

For a media-processing callback simulation:

1. External boundary posts an integration event payload to `/cap4k/integration-event/http/consume`.
2. HTTP adapter deserializes and dispatches the integration event.
3. `application.subscribers.integration` subscriber receives the event.
4. Subscriber sends a command such as `MarkMediaProcessingSucceededCmd`.
5. Command handler loads and changes the aggregate, then calls `Mediator.uow.save()`.
6. Aggregate emits a domain event such as media-processing succeeded.
7. Domain subscriber performs follow-up orchestration, such as sending `PublishContentCmd`.

This is not pretending an external callback is a domain event. The external input remains an integration event, then application logic translates it into domain state changes.

## Event Contract Sharing

Cross-service integration event classes can be shared by copy/paste or by generation from a shared design contract. In cap4k, the stronger long-term fit is to generate event contracts from design so each service owns its local code while sharing the same event shape.

Package-name consistency may be useful for exact serializer compatibility, but the authoring rule should be explicit per transport. The stable contract is event name, schema, and serialization behavior, not a blanket dependency on another service's application module.

## Framework Tables

Runtime families with JPA persistence need corresponding schema:

- request records;
- event records;
- saga records if saga is used;
- integration event HTTP subscriber registry when HTTP-JPA adapter is used;
- distributed locker when JDBC locker is used.

Example projects should include only the required subset. For H2 local examples, SQL should be translated to H2-compatible syntax while preserving required columns and semantics.

## Interceptor Extension Points

Business/framework extension points include:

- `RequestInterceptor`;
- `UnitOfWorkInterceptor`;
- `EventInterceptor`;
- `EventMessageInterceptor`;
- `IntegrationEventInterceptor`;
- `PersistListener`;
- `Specification`.

Authoring should mention these as extension points, but not force first-version examples to demonstrate all of them.

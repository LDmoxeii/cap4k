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
| Integration event | Integration event supervisor, UoW interceptor, HTTP/RabbitMQ/RocketMQ adapters |
| Domain service | Domain service supervisor |
| ID policy | UUID7 and optional snowflake-long strategies |
| JDBC locker | Distributed lock implementation and reentrant aspect |
| Saga | Saga supervisor and schedule service |
| Arch info | Optional `/cap4k/arch-info` endpoint |
| Web context cleanup | Clears domain/UoW/event context after web request completion |

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

`@AutoRequest` can convert a domain event to a request and send it through the request supervisor. `@AutoRelease` can convert a domain event to an integration event.

## Integration Event Flow

Integration events represent cross-boundary messages. They can be attached to UoW or published directly through the integration event supervisor.

```mermaid
flowchart TD
    Producer[Business code / AutoRelease] --> Attach[Mediator.events.attach or publish]
    Attach --> IntegrationSupervisor[IntegrationEventSupervisor]
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
- `IntegrationEventSupervisor.publish`;
- `EventSubscriber<Event>`;
- `IntegrationEventPublisher`;
- `IntegrationEventInterceptorManager`.

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

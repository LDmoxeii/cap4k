# Runtime Tactical Contract

## Mediator Surface

Use static `Mediator.*` access in business code:

- `Mediator.cmd` / `Mediator.commands` for commands.
- `Mediator.qry` / `Mediator.queries` for queries.
- `Mediator.requests` / `Mediator.req` for generic request dispatch, including client/cli conventions.
- `Mediator.repositories` / `Mediator.repo` for aggregate loading.
- `Mediator.factories` / `Mediator.fac` for aggregate-root creation.
- `Mediator.services` / `Mediator.svc` for domain services.
- `Mediator.uow` for unit-of-work persistence.
- `Mediator.events` for domain/integration event publication or attachment.

## Repositories And Factories

- Command handlers load aggregate roots through repositories.
- Only aggregate roots should have factories.
- Factories create aggregate roots from payloads and enlist them in the UoW.
- Query handlers may wrap repository/JPA read access when read behavior belongs behind a request boundary.

## Domain Services And Specifications

- Use domain services for business decisions crossing aggregate boundaries or not naturally owned by one aggregate.
- Application code orchestrates loading and persistence; domain services make the domain decision.
- Specifications validate aggregate state before or inside UoW execution when the project intentionally uses them.

## Unit Of Work And Lifecycle

- Use `Mediator.uow.save()` as the canonical write persistence boundary.
- UoW handles persist/remove intentions, specifications, lifecycle listeners, attached events, and transaction execution.
- Persist aggregate roots, not aggregate-owned JSON-backed or inline value objects. A composite value such as `MediaProcessingResultSnapshot` is saved through its owning aggregate field.
- Only use table-backed value-object persistence when the model intentionally chose that heavier carrier; do not infer it from the existence of a value object.
- Aggregate behavior may define `onCreate`, `onUpdate`, and `onDelete`; document lifecycle recognition limits when they matter.

## Events

- Domain events are domain facts emitted by aggregate behavior and consumed by application domain subscribers.
- Integration events are cross-boundary messages published or consumed through integration adapters.
- Integration subscribers translate external input into application commands, queries, or requests instead of pretending callbacks are domain events.

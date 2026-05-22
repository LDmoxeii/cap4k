# Mediator And UoW Rules

- Use static `Mediator.*` surfaces in business code.
- Use `Mediator.repositories` for aggregate-root loading in write flows.
  Repository reads are detached by default; only the aggregate that will later be mutated and flushed through `Mediator.uow.save()` should use `persist = true`.
- Use `Mediator.factories` for aggregate-root creation.
- Use `Mediator.services` for domain services.
- Use `Mediator.requests` for external capability clients.
- Use `Mediator.cmd` and `Mediator.qry` for command/query boundaries.
- Use `Mediator.events.attach(...)` to attach outbound integration events to the current work unit.
- Use `Mediator.uow.save()` as the normal write persistence boundary.
- Do not inject handlers directly to bypass the request supervisor path.
- Do not call client first from an entry implementation and then command second to patch internal state.
- Do not bypass `Mediator.events.attach(...)` with direct outbound event sending or lower-level supervisor calls.

### Repository Access Boundary

- Repository access is governed by read/write boundaries, not by a blanket "commands only" rule.
- Command handlers are aggregate write boundaries. They may load and write aggregate roots through repositories.
- Command handlers should treat default repository reads as read-only unless they explicitly opt the target aggregate into `persist = true`.
- Query handlers are read boundaries. They may use repositories, JPA, projections, or read-model infrastructure in read-only mode.
- Domain event listeners, external fact entries, open host service entries, controllers, jobs, client handlers, and Saga coordinators must not directly mutate aggregates or call write repositories.
- Flow-routing reads outside a command should normally go through a query instead of ad hoc repository access.
- If a non-command component appears to need write repository access, route the write through a command or explicitly document why the component is itself a write boundary.

### UoW Persistence Boundary

- `Mediator.uow.save(...)` belongs inside an explicit write boundary.
- A command may read multiple aggregates or read facts for zero-trust validation.
- Only one aggregate root may enter the persistence write boundary in one command path.
- Non-target aggregate reads must stay read-only and must not share write responsibility.
- Persist aggregate roots only. Child entities, value objects, inline values, and JSON-backed values are persisted through their aggregate root.
- Do not save child entities independently to bypass aggregate invariants.
- Do not call `Mediator.uow.save(...)` from listeners, jobs, controllers, open host service entries, external fact entries, or client handlers unless that component has been deliberately modeled as the write boundary.

### Integration Event Boundary

- Attach outbound integration events only in domain-event subscribers or explicit application processes that are reacting to an internal fact.
- Aggregates must record domain facts, not cross-service event names, schemas, subscribers, or transport choices.
- Adapter entries, external fact entries, open host services, controllers, jobs, and ordinary boundary code must not decide outbound integration events.
- Inbound integration events are external facts. Translate state-advancing inputs into internal commands before changing state.
- `@EventListener` remains an application implementation technique for reacting to internal facts; it does not turn external facts into domain events.

# Mediator And UoW Rules

- Use static `Mediator.*` surfaces in business code.
- Use `Mediator.repositories` for aggregate-root loading in write flows.
- Use `Mediator.factories` for aggregate-root creation.
- Use `Mediator.services` for domain services.
- Use `Mediator.requests` for external capability clients.
- Use `Mediator.cmd` and `Mediator.qry` for command/query boundaries.
- Use `Mediator.uow.save()` as the normal write persistence boundary.
- Do not inject handlers directly to bypass the request supervisor path.
- Do not call client first from an entry implementation and then command second to patch internal state.

### Repository Access Boundary

- Repository access is governed by read/write boundaries, not by a blanket "commands only" rule.
- Command handlers are aggregate write boundaries. They may load and write aggregate roots through repositories.
- Query handlers are read boundaries. They may use repositories, JPA, projections, or read-model infrastructure in read-only mode.
- Domain event listeners, external fact entries, open host service entries, controllers, jobs, client handlers, and Saga coordinators must not directly mutate aggregates or call write repositories.
- Flow-routing reads outside a command should normally go through a query instead of ad hoc repository access.
- If a non-command component appears to need write repository access, route the write through a command or explicitly document why the component is itself a write boundary.

### UoW Persistence Boundary

- `Mediator.uow.save(...)` belongs inside an explicit write boundary.
- Persist aggregate roots only. Child entities, value objects, inline values, and JSON-backed values are persisted through their aggregate root.
- Do not save child entities independently to bypass aggregate invariants.
- Do not call `Mediator.uow.save(...)` from listeners, jobs, controllers, open host service entries, external fact entries, or client handlers unless that component has been deliberately modeled as the write boundary.

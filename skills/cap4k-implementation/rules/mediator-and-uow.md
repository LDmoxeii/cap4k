# Mediator And UoW Rules

- Use static `Mediator.*` surfaces in business code.
- Use `Mediator.repositories` for aggregate-root loading in write flows.
- Use `Mediator.factories` for aggregate-root creation.
- Use `Mediator.services` for domain services.
- Use `Mediator.requests` for external capability clients.
- Use `Mediator.cmd` and `Mediator.qry` for command/query boundaries.
- Use `Mediator.uow.save()` as the normal write persistence boundary.
- UoW saves aggregate roots only.
- Persist child entities, value objects, inline values, and JSON-backed values through the aggregate root.
- Do not inject handlers directly to bypass the request supervisor path.
- Do not call client first from an entry implementation and then command second to patch internal state.

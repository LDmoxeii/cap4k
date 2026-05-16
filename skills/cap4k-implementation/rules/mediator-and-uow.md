# Mediator And UoW Rules

- Use static `Mediator.*` surfaces in business code.
- Use `Mediator.repositories` for aggregate loading in write flows.
- Use `Mediator.factories` for aggregate-root creation.
- Use `Mediator.services` for domain services.
- Use `Mediator.uow.save()` as the normal write persistence boundary.
- Use `Mediator.cmd`, `Mediator.qry`, and `Mediator.requests` for orchestration across request boundaries.
- Do not inject handlers directly to bypass the request supervisor path.

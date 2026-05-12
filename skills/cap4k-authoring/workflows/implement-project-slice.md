# Implement Project Slice

1. Start from the agreed domain model, DDL, design JSON, or technical方案.
2. Add or update focused tests first when behavior changes and a practical test seam exists.
3. Classify value concepts before implementation: enum, strong ID, primitive value, composite JSON-backed/inline value, or table-backed `@VO`.
4. Put model behavior in domain aggregates, entities, factories, domain services, specifications, and domain events.
5. Put request contracts, command handling, validators, subscribers, and process intent in application.
6. Put HTTP controllers, persistence adapters, query handlers, client/cli handlers, and external bridges in adapter.
7. Use `Mediator.repositories`, `Mediator.factories`, `Mediator.services`, and `Mediator.uow` in command write flows; save aggregate-owned values through the aggregate root.
8. Use `Mediator.cmd`, `Mediator.qry`, and `Mediator.requests` for orchestration across request boundaries.
9. Model integration events as cross-boundary messages and translate inbound events to commands or process steps.
10. Run compile/tests, then analysis if requested or affected.
11. Report changed files, commands, outcomes, and any human audit decisions still open.

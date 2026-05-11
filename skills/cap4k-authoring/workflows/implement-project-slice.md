# Implement Project Slice

1. Start from the agreed domain model, DDL, design JSON, or technical方案.
2. Add or update focused tests first when behavior changes and a practical test seam exists.
3. Put model behavior in domain aggregates, entities, factories, domain services, specifications, and domain events.
4. Put request contracts, command handling, validators, subscribers, and process intent in application.
5. Put HTTP controllers, persistence adapters, query handlers, client/cli handlers, and external bridges in adapter.
6. Use `Mediator.repositories`, `Mediator.factories`, `Mediator.services`, and `Mediator.uow` in command write flows.
7. Use `Mediator.cmd`, `Mediator.qry`, and `Mediator.requests` for orchestration across request boundaries.
8. Model integration events as cross-boundary messages and translate inbound events to commands or process steps.
9. Run compile/tests, then analysis if requested or affected.
10. Report changed files, commands, outcomes, and any human audit decisions still open.

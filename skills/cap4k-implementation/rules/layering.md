# Layering Rules

- Domain owns aggregates, entities, value concepts, invariants, domain events, domain services, factories, and specifications.
- Application owns commands, command handlers, validators, subscribers, jobs, and process orchestration.
- Adapter owns HTTP controllers, persistence adapters, query handlers, client/cli handlers, and external protocol mapping.
- Query handlers and client/cli handlers are adapter-side physical handlers by default.
- Controllers and jobs must not become direct write-persistence surfaces.

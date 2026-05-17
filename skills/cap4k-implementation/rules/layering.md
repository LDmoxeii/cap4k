# Layering Rules

- Domain owns aggregates, entities, value objects, invariants, domain events, domain services, factories, and specifications.
- Application owns commands, command handlers, validators, subscribers, internal triggers, and process orchestration.
- Adapter owns Open Host Service implementations, external fact entry implementations, persistence adapters, query handlers, client handlers, and external protocol mapping.
- Query handlers and client handlers are adapter-side physical handlers by default.
- Open Host Service entries, external fact entries, and internal triggers must not become direct write-persistence surfaces.

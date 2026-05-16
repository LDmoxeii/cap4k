# Tactical Modeling Rules

- Aggregates own write invariants and state transitions.
- Entities belong inside aggregate consistency boundaries.
- Value concepts may be primitive wrappers, inline values, JSON-backed values, or table-backed `@VO`; choose the domain concept before choosing the persistence carrier.
- Domain services are handwritten when a domain decision crosses aggregate boundaries or does not naturally belong to one aggregate.
- Specifications are handwritten validation policies used only when the project intentionally demonstrates or enforces that concept.
- Domain events are emitted by domain behavior and handled by application subscribers.
- Integration events represent external or cross-service facts. Design JSON supports `integration_event` with `role`, `eventName`, at least one request field, and no response fields.
- Do not model external callbacks as domain events. Translate external input into commands or process steps at the application boundary.

# Tactical Modeling Rules

- Aggregates own write invariants and state transitions.
- Entities belong inside aggregate consistency boundaries.
- Value objects express business value semantics before persistence carrier choices.
- Domain services should only be modeled when a domain decision crosses aggregate boundaries or does not naturally belong to one aggregate.
- Specifications model validation policies only when the project intentionally demonstrates or enforces that concept.
- Domain events express meaningful domain facts from domain behavior; synchronous or asynchronous handling is a delivery/runtime choice.
- External interaction must be classified as external capability client, Open Host Service, external fact entry, or internal fact publication before generation.
- Do not model external callbacks as domain events.

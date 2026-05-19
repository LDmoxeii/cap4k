# Tactical Modeling Rules

- Aggregates own write invariants and state transitions.
- Entities belong inside aggregate consistency boundaries.
- Value objects express business value semantics before persistence carrier choices.
- Domain services should only be modeled when a domain decision crosses aggregate boundaries or does not naturally belong to one aggregate.
- Specifications model validation policies only when the project intentionally demonstrates or enforces that concept.
- Advanced concepts must pass the shared advanced-mode gate before they become the default model shape.
- Domain events express meaningful domain facts from domain behavior; synchronous or asynchronous handling is a delivery/runtime choice.
- External interaction must be classified as external capability client, Open Host Service, external fact entry, or internal fact publication before generation.
- Do not model external callbacks as domain events.

### Domain Event Payload Boundary

- Domain events describe business facts, not technical continuation steps. Do not create "command completed" events merely to continue a process.
- Generated domain events may carry the aggregate snapshot. Do not fight that generator contract in business projects.
- Add event fields only when the aggregate snapshot cannot express the fact clearly: added child items, removed child items, deltas, before/after values, or computed fact results.
- Do not expose non-aggregate-root technical or persistence IDs as standalone public identities in domain events, outbound integration events, or open host write contracts.
- Read models may expose aggregate-scoped child keys for UI display, diffing, and selection.
- Commands that target child elements must include the aggregate root identity plus a child key, then validate child membership inside the command.

### Event-Driven Continuation

- Prefer fact-driven continuation: command mutates an aggregate, aggregate behavior records a meaningful domain fact, and independent listeners react to that fact.
- Each listener routes writes into zero-trust commands. The command must re-load the write target and validate its own preconditions.
- Repeated delivery should converge through idempotent command behavior and explicit no-op results.
- Use Saga only for persisted long-running coordination, retry, recovery, compensation, or cross-time waiting.

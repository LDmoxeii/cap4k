# Advanced Mode Gates

- Use advanced concepts only after the default aggregate, command, event, and query path is insufficient.
- Domain Service is for domain decisions that do not naturally belong to one aggregate or value object.
- Saga is for persisted long-running coordination, retry, recovery, compensation, or cross-time waiting.
- Read-only weak references support navigation or type expression without introducing writable cross-aggregate object graphs.
- Strong ID is engineering reinforcement, not a substitute for aggregate, command, and naming boundaries.
- Value Object modeling starts from business value semantics, then chooses primitive, inline, JSON-backed, or table-backed persistence.

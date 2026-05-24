# Concept Selection Gates

- Use Value Object, Domain Service, Saga, Read-only Weak Reference, or Strong ID when the concept matches the domain problem; do not introduce a concept just because the framework can name it.
- Domain Service is for domain decisions that do not naturally belong to one aggregate or value object.
- Saga is for persisted long-running coordination, retry, recovery, or compensation when the current runtime contract is sufficient.
- Do not treat Saga as a default waiting-style or callback-step resume workflow engine.
- Read-only weak references support navigation or type expression without introducing writable cross-aggregate object graphs.
- Strong ID is engineering reinforcement, not a substitute for aggregate, command, and naming boundaries.
- Value Object modeling starts from business value semantics, then chooses primitive, inline, JSON-backed, or table-backed persistence.

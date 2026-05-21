# Default Path And Write Boundaries

- One command path may persist only one aggregate root.
- Commands may read multiple aggregates or read facts for zero-trust validation, but those reads must not become shared write ownership.
- State-changing controllers, subscribers, jobs, external fact entries, and Open Host Service entries route into commands.
- Aggregate roots own write invariants and emit meaningful domain facts.
- Domain events describe business facts, not technical continuation steps.
- Callback and polling entries must converge to the same internal command semantics when they represent the same business fact.
- Query paths observe. They do not repair or mutate the write model.

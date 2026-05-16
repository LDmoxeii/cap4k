# Clarify Domain Design

1. Confirm the business outcome, actors, commands, read needs, external systems, and important failure paths.
2. Derive candidate aggregates, entities, value concepts, invariants, domain events, domain services, and integration events.
3. Separate decisions from implementation guesses; ask the human for missing domain choices.
4. Draft the DDL contract when DB-first generation is likely, including aggregate-root markers, parent/child relations, enum annotations, custom `@T` type bindings, table-backed `@VO` only when intended, generated IDs, versions, soft delete, managed fields, and uniqueness.
5. Draft design JSON when use-case/interface generation is useful: command, query, client, api_payload, domain_event, and validator.
6. Use `integration_event` for explicit cross-boundary event contracts when needed; note unsupported design tags explicitly: first-class `value_object` and `domain_service`.
7. For complex flows, write a user-readable technical方案 before code that names commands, queries, subscribers, handlers, events, repositories, factories, UoW, tests, and analysis outputs.
8. End with an implementation/generation plan the human can audit before files are changed.

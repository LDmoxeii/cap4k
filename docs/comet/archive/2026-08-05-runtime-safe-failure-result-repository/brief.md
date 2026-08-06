# Outcome

Reliable Command and Event failures expose only structured, safe diagnostic facts, while obsolete reliable-result polling and archival surfaces are removed.

# Scope

- Introduce one shared safe-failure fact contract for reliable Command and Event records.
- Persist only controlled failure type/summary, attempt/timing, correlation identifier, and terminal/retryable classification.
- Make record diagnostics and logs safe by construction: no serialized command/event payload, persisted entity state, raw exception message, or stack trace.
- Remove reliable Command result polling APIs and the Command/Event archive entities, repositories, schema, scheduling, configuration, and documentation paths.
- Preserve the current retry scan path until the dedicated reliable JPA substrate/state-machine slices replace it.

# Non-goals

- No new result API, generic job history, compatibility archive, or migration bridge.
- No retry-policy snapshot, claim/lease, provider composition, or broader state-machine redesign.
- No changes to reliable event payload rules or transport behavior.

# Acceptance examples

- A handler exception containing payload JSON or credential-like text produces a failure fact whose stored and logged representation contains neither the raw exception text nor business payload.
- Retryable and terminal failures retain enough safe facts to identify record correlation, attempt number, occurrence time, failure category, and retryability/terminality.
- Reliable Command completion cannot be polled through `CommandSupervisor.result` or a record result accessor.
- Command/Event archive tables, entities, repositories, jobs, and archive bypass methods have no static references after the change.
- Existing retry scanning remains available and focused Command/Event persistence tests pass.

# Constraints and invariants

- Breaking removal is intentional; there are no external users and no compatibility requirement.
- Raw business payload JSON, persisted entity state, secrets, raw exception messages, and stack traces must not enter failure facts or diagnostic `toString` output.
- Completion remains a reliable state-machine concern; this slice must not create another completion store.
- `getByNextTryTime` is not an archival/result surface and remains until its owning state-machine slice.

# Decisions

- Use a shared framework-owned structured failure-fact value rather than persisting `Throwable` text.
- Use controlled summaries instead of attempting best-effort redaction of arbitrary exception messages.
- Remove archive/result surfaces directly with no compatibility aliases or database migration bridge.
- Limit state-machine edits to those strictly required to remove result/archive bypasses.

# Open questions

- Confirmed on 2026-08-05: implement the published `runtime-safe-failure-result-repository` contract with the scope and exclusions above.

# Verification expectations

- Focused unit and starter tests cover retryable and terminal safe facts, correlation/attempt/timing, and absence of raw payload, exception message, stack trace, and secrets.
- Static searches prove deleted result/archive symbols, tables, configuration, and documentation references are absent.
- Relevant Gradle module tests and the repository-required `check` path pass, or any unrun check is recorded honestly.

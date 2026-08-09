# Outcome

Add the Runtime-owned retention and cleanup boundary for reliable Command and Event records.
Terminal records can be removed only through an explicit, bounded JPA cleanup operation that
re-evaluates durable state at deletion time and returns safe counts. Cleanup must preserve the
at-least-once execution state machine and must never become an acknowledgement or retry path.

# Scope

- Add a separate terminalization timestamp to Command and Event persistence carriers.
- Populate that timestamp on successful acknowledgement and terminal failure/expiry transitions.
- Add one shared retention-policy/result value contract in `ddd-core`.
- Add private Command/Event JPA cleanup operations with state, service, lease, timestamp, and
  batch guards.
- Keep cleanup invocation explicit in this slice; do not add an automatic scheduled deletion loop.
- Add focused repository, substrate, and JPA integration evidence for both record types.

# Non-goals

- No manual redrive implementation.
- No automatic infinite retry, generic scheduler, task, job, or archive framework.
- No deletion of `INIT`, `EXECUTING`/`DELIVERING`, `EXCEPTION`, or redrive-eligible records.
- No transport, Integration Event envelope, provider, or Analyzer work.
- Do not reuse `expireAt` as a retention timestamp; it remains the execution deadline.

# Acceptance examples

- An old successful Command/Event whose terminal retention cutoff has passed is deleted.
- An old terminal expired or exhausted Command/Event whose cutoff has passed is deleted.
- A terminal record newer than its policy cutoff remains present.
- A retryable `EXCEPTION` record remains present even when its failure facts are old.
- A redrive-eligible or cancelled record remains present unless a future policy explicitly owns it.
- A record with a live lease is never deleted, even if its state or timestamp appears eligible.
- A cleanup call deletes at most its requested batch size and reports safe examined/deleted counts.
- Repeating the same cleanup call is idempotent and does not delete a second time.
- A record belonging to another service is not deleted by the current service cleanup.
- A concurrent state change wins over cleanup because the final delete rechecks state, lease, and
  cutoff conditions.

# Constraints and invariants

- Command and Event use symmetric cleanup semantics while retaining their own state enums and
  JPA carriers.
- Terminalization time is distinct from execution `expireAt` and is nullable for non-terminal
  records.
- Only successful, expired, and exhausted states are cleanup-owned in this slice:
  `EXECUTED`/`DELIVERED`, `EXPIRED`, and `EXHAUSTED`.
- Cleanup requires a null lease at delete time and cannot mutate a record before deleting it.
- Retry policy snapshots and safe failure facts remain unchanged by cleanup.
- Cleanup is a bounded Runtime operation, not a public generic persistence API.
- Breaking iteration is allowed; no compatibility bridge or migration shim is required.

# Decisions

- Cleanup is explicitly invoked by the private Runtime/JPA cleanup operation. Automatic scheduled
  deletion is deferred so this slice cannot silently erase records without an operator or later
  Runtime coordinator choosing the policy and timing.
- Retention policy supplies separate durations for successful, failed/exhausted, and expired
  records plus a positive batch limit. A policy is required at invocation time; no hidden global
  retention default is introduced.
- The delete operation returns only safe aggregate facts (`examined`, `deleted`) and does not
  expose payloads, stack traces, or business data.

# Open questions

None.

# Verification expectations

- Run focused Command/Event repository and substrate tests for state, cutoff, lease, service, and
  batch guards.
- Run real H2/JPA integration tests proving terminal timestamp population and idempotent bounded
  cleanup for both carriers.
- Run static checks for stale result/archive/retention bypass paths and schema/entity alignment.
- Record skipped external-database checks honestly if no live MySQL/PostgreSQL environment exists.

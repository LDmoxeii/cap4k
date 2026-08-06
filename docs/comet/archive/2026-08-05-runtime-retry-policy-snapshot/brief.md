# Outcome

Reliable Command and Event records capture the effective retry policy once when the record is created, persist that immutable snapshot, and use it for every later retry decision.

# Scope

- Add one shared retry-policy snapshot contract in `ddd-core`.
- Capture the effective retry limit, delay/backoff schedule, retryable classification, and policy version for reliable Commands and Events.
- Persist the snapshot in active and archived JPA records and copy it during archival.
- Calculate later retry times only from the stored snapshot.
- Add focused persistence, mutation, and payload-confidentiality tests.

# Non-goals

- Redesign reliable Command/Event state machines.
- Add claim/lease, manual redrive, retention, transport, or generic scheduling capabilities.
- Add configurable exception classifiers; the current runtime retries any handler exception.
- Change existing attempt-count semantics or retry defaults.
- Implement the separate safe-failure diagnostics cleanup slice.

# Acceptance examples

- A record created with retry limit 3 and delays 1/5/10 minutes still uses those values after persistence and archival.
- If the current handler annotation changes after a record is created, redelivery of the existing record still follows the stored policy while a new record captures the new policy.
- Command/Event snapshot persistence and diagnostics never contain the serialized business payload.

# Constraints and invariants

- Existing default delays remain attempts 1-10: 1 minute, 11-20: 5 minutes, 21+: 10 minutes.
- Custom retry intervals keep repeating their final configured value.
- Reliable Event persisted-entity payload rejection remains unchanged.
- Retry decisions remain owned by the reliable state machine.
- Backward compatibility is not required; persisted snapshot columns may be mandatory.

# Decisions

- The snapshot has explicit policy version `1` and retryable classification `ANY_EXCEPTION`, matching current runtime semantics without opening a new configuration surface.
- The delay curve is materialized in the snapshot at record creation, so later annotation or default changes cannot rewrite history.
- Existing `tryTimes` and absolute `expireAt` state-machine fields remain in place; the snapshot records retry decision inputs rather than replacing the state machine.
- The published canonical specification and the user's instruction to implement this already-confirmed Runtime slice authorize Build; no new product decision is introduced.

# Open questions

None.

# Verification expectations

- Focused unit tests for snapshot capture and deterministic delay lookup.
- Command/Event JPA tests for active-to-archive round trips and configuration-mutation immunity.
- Static checks for SQL/schema alignment and absence of raw payload in snapshot serialization.
- Relevant Gradle module tests and `git diff --check` pass.

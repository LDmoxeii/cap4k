# Outcome

Add a private Runtime-owned Manual Redrive capability for persisted reliable Command and Event
records. An operator must be able to explicitly request a bounded new delivery attempt without
restoring the removed `CommandManager.retry` API, a generic task scheduler, or a transport-specific
redrive surface.

# Scope

- Add one explicit redrive contract shared by reliable Command and Event JPA carriers.
- Require a record identifier plus an idempotent operator request token.
- Apply a token/version/state-fenced durable transition before waking the existing Command worker
  or Event coordinator.
- Preserve the immutable retry-policy snapshot and prior safe failure facts.
- Clear stale delivery ownership (`deliveryToken` and `leaseUntil`) before the new attempt becomes
  claimable.
- Cover allowed and denied states, duplicate requests, stale ownership/version, subsequent claim,
  and successful acknowledgement.

# Non-goals

- No automatic infinite retry or background redrive loop.
- No public generic scheduler/task API.
- No transport-specific redrive endpoint.
- No restoration of `CommandManager.retry`, result polling, archive records, Locker bypasses, or a
  second reliable state machine.
- No mutation of the persisted retry-policy snapshot or raw business payload/failure diagnostics.

# Acceptance examples

- A permitted failed Command/Event record is reset atomically and becomes claimable by the existing
  reliable worker/coordinator.
- A duplicate request with the same record identifier and request token is idempotent and does not
  create a second reset or attempt.
- A stale state/version or an active leased record is rejected with zero durable write effect.
- Executed/Delivered, Cancelled, and non-eligible terminal records are rejected deterministically.
- A successful post-redrive worker attempt acknowledges through the existing delivery-token and
  live-lease fencing.

# Constraints and invariants

- Reliable delivery remains at-least-once.
- Claim, redrive, lease, acknowledgement, and failure transitions remain Runtime-owned and private.
- The existing JPA CAS boundary is the only authority for ownership/state mutation.
- Redrive must not publish or invoke a handler directly; it only makes one existing record eligible
  for the normal worker path and requests a wake-up.
- Prior failure facts remain available for diagnostics; no raw exception payload or business payload
  is added to the redrive contract.
- Command and Event semantics stay symmetric.

# Decisions

- Use one implementation shape for Command and Event, with carrier-specific state names hidden
  behind private JPA services.
- Use an opaque operator request token for idempotency; it is distinct from the delivery ownership
  token and is not persisted as a business payload.
- Reuse the stored retry-policy snapshot for the new attempt; current annotations/configuration do
  not rewrite historical records.
- Wake the existing `JpaReliableCommandWorker` or `JpaEventScheduleService` only after the durable
  reset succeeds.
- Redrive is allowed for `EXCEPTION` and `EXHAUSTED` records only when their existing `expireAt`
  is still in the future. `EXPIRED`, active (`EXECUTING`/`DELIVERING`), successful
  (`EXECUTED`/`DELIVERED`), and `CANCEL` records are denied deterministically.
- A successful redrive resets `triedTimes` to `0`, sets the record to `INIT`, clears ownership,
  and makes it due immediately. The stored `retryPolicy` and original `expireAt` remain unchanged;
  manual redrive never extends the record lifetime or changes the retry budget snapshot.

# Open questions

- None.

# Verification expectations

- Focused unit/CAS tests for both JPA repositories and substrates/services.
- Real H2/JPA integration tests for allowed states, denied states, duplicate request tokens,
  stale-version/state fencing, active lease rejection, subsequent claim, and acknowledgement.
- `git diff --check`, focused Gradle tests, and `comet native check runtime-manual-redrive`.

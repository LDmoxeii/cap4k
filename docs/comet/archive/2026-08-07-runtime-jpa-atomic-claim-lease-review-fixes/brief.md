# Outcome

Correct the reliable JPA substrate semantics identified during the PR #170 review, while keeping Command and Event as one ownership/state contract. The result must be safe to consume from the downstream Command/Event state-machine changes and must be demonstrated against the production initialization paths, not only synthetic rows.

# Scope

- Make the persisted attempt budget explicit and symmetric. `retryLimit` is the maximum total execution-attempt count; a claim must never issue an owner for an already exhausted persisted snapshot.
- Preserve the real production baseline: Command's first execution is counted by its existing begin path and Event's first direct publication is represented by its existing initialized delivery attempt. Tests must exercise those paths instead of resetting Event to zero.
- Resolve the interaction between record expiry and lease ownership so an in-flight row cannot become a permanent `EXECUTING`/`DELIVERING` orphan.
- Enforce strict, bytewise delivery-token equality in MySQL and align all runtime-controlled time columns with millisecond precision.
- Strengthen real JPA evidence for deterministic same-candidate CAS contention, isolated stale-owner fencing, expiry/lease sequences, terminal-state zero-write behavior, and SQL resource projection.
- Keep the Command and Event substrate contracts mirrored, with separate carrier/repository projections allowed only where the persistence model requires them.

# Non-goals

- No manual redrive, retention/cleanup, Integration Event envelope, or transport provider work.
- No public Scheduler/Job/Task API and no compatibility layer.
- No broad rewrite of the existing Command/Event state machines; downstream branches will consume this substrate.
- No changes to the framework capability audit branch or to master during this change.

# Acceptance examples

- Two independent transactions reading the same due candidate can race the claim CAS; exactly one receives a token and the losing transaction performs zero writes.
- A token generated for one claim cannot be substituted by a different token, including a case-only variation when persisted in MySQL.
- `renew`, `acknowledge`, and failure/retry transitions are token-bound and lease-fenced; an expired or replaced owner cannot mutate any field.
- A lease that expires before the record deadline can be reclaimed; a record that reaches its expiry deadline converges to a terminal state and is never reclaimed again.
- A valid owner that crosses the record deadline follows the selected expiry contract and leaves no unrecoverable in-flight row.
- Command and Event use the saved `retry_policy` snapshot, execute the same total-attempt budget, and reject the next claim after exhaustion.
- Claim, renewal, acknowledgement, and failure transitions update their ownership/state metadata atomically and roll back as one unit.
- `command.sql` and `event.sql` agree with the JPA carriers for names, nullability, token comparison semantics, and millisecond time precision.

# Constraints and invariants

- The substrate is private Runtime infrastructure, not a general-purpose task framework.
- Ownership is represented by an opaque, database-round-trippable token and a lease; every mutating transition must validate the current token and live lease in its conditional write.
- Retry policy is captured at record creation and must not be recomputed from mutable annotations or legacy fields.
- Failure persistence contains only safe failure facts; no business payload or exception stack trace may be written.
- Terminal and cancelled records cannot be claimed or revived and must not be modified by rejected owner operations.
- Claim is one transactional/statement boundary, and state, attempt, token, lease, version, and scheduling metadata change together.
- The implementation may break internal structure because this project has no external compatibility requirement.

# Decisions

- The attempt correction is semantic, not cosmetic: production initialization and substrate claim must describe one total-attempt budget on both sides, and over-budget candidates must not receive a new owner.
- The strict-token and millisecond-precision corrections are mandatory persistence semantics, not configurable provider behavior.
- A shared ownership/state contract is required even when Command and Event retain separate JPA entities, repositories, SQL files, and integration fixtures.
- Verification must include a real MySQL-compatible storage check or an equivalent schema/DDL assertion that proves binary token comparison and `datetime(3)` precision; H2-only green tests are insufficient evidence.
- `expireAt` is an admission/retry deadline. It blocks new claims and reclaims after expiry, while a current owner with a live token-bound lease may extend that lease, acknowledge successfully, or transition failure to terminal `EXPIRED`. If that owner loses its lease after record expiry, an atomic terminalization path must clear ownership and leave the record terminal rather than orphaned in-flight.

# Open questions

- None. The expiry contract was confirmed as admission/retry deadline semantics.

# Verification expectations

- Run focused Command and Event JPA tests plus the affected core and starter tests.
- Build a deterministic two-transaction race barrier that proves both workers observed the same candidate before the CAS.
- Exercise production-like Command and Event initialization, retry limits 1 and 3, post-budget claim rejection, and persisted retry-policy snapshots.
- Exercise lease expiry before and after record expiry, valid-owner completion/failure across the chosen boundary, replacement-token fencing, and terminal/cancelled zero-write checks.
- Validate SQL resource fields against entity mappings, including bytewise token equality and millisecond timestamps, and report any database-specific behavior not covered by the available test engine.

# Outcome

Introduce one private JPA execution substrate shared by reliable Command and Event records. A worker can atomically claim one eligible record, receive an opaque delivery token, renew its lease, and acknowledge or transition failure/retry only while presenting that token. An expired lease becomes claimable by another worker without reviving terminal or cancelled records.

# Scope

- Add the shared semantic contract and JPA carrier/projection fields required for ownership token and lease expiry.
- Implement atomic claim and token-bound renew, acknowledge, failure/retry transitions for Command and Event persistence.
- Preserve the retry-policy snapshot captured at record creation and persist only safe `failure_facts`.
- Add real JPA integration coverage in the Command and Event starter owners using independent transactions and concurrent workers.
- Keep existing scheduling/transport wiring intact until the downstream Command/Event state-machine and transport changes consume this substrate.

# Non-goals

- No public Scheduler, Job, Task, or generic execution framework.
- No manual redrive, retention/cleanup, Integration Event envelope, or transport implementation.
- No broad Locker/legacy scheduling removal in this change.
- No compatibility layer or preservation of the old read-then-save claim path.
- No claim that H2/MySQL-mode tests prove every production database dialect; dialect gaps will be reported.

# Acceptance examples

- Two workers claim the same eligible Command or Event concurrently; exactly one transaction succeeds and the other observes no ownership.
- A claimed record returns a non-empty opaque token. A different token cannot renew, acknowledge, or write failure/retry state.
- The owner can renew before lease expiry; renewal after expiry is rejected. Another worker can claim after expiry.
- Terminal (`EXECUTED`/`DELIVERED`, `EXHAUSTED`, `EXPIRED`) and `CANCEL` records cannot be claimed or revived.
- A claim and its state/attempt/token/lease changes commit atomically; a failed transaction leaves the prior durable state unchanged.
- Retry transitions use the persisted retry-policy snapshot and update only safe structured failure facts, never business payload or exception stack traces.

# Constraints and invariants

- Claim eligibility is evaluated at the database boundary against service, state, retry time, expiry, and lease ownership/expiry predicates.
- A successful claim changes claimable state to the in-flight state, records attempt timing/count, lease expiry, and a newly generated opaque token in one transaction/statement boundary.
- Every owner-sensitive operation is token-bound; token mismatch has zero write effect.
- Renewal is bounded by the same token and cannot revive terminal/cancelled records or an already expired lease.
- Command and Event may use separate JPA carriers and SQL projections, but their ownership/state-machine semantics are one private contract.
- The carrier stores no delivery payload or exception stack trace as part of ownership state; existing payload and safe failure-facts boundaries remain authoritative.

# Decisions

- Use the numeric JPA row id plus token for internal ownership addressing; UUID lookup remains a public record lookup concern.
- Keep lease timestamps supplied by the runtime/application clock so tests and dialects do not depend on vendor-specific date arithmetic.
- Place integration tests in `cap4k-ddd-command-jpa-starter` and `cap4k-ddd-domain-event-jpa-starter`, where Spring Boot, JPA scanning, and H2 are already owned.
- Treat H2 in MySQL mode as the baseline executable database and document PostgreSQL/real-MySQL differences rather than widening this slice into a dialect abstraction.
- Do not introduce a new public module or generic task abstraction; substrate APIs remain private runtime infrastructure.
- User confirmed this Shape contract on 2026-08-07, including lease-expiry invalidation for renew, acknowledge, and failure/retry transitions.

# Open questions

- No unresolved product questions.

# Verification expectations

- Run focused real JPA integration tests for both record types, including two independent concurrent claimers, token mismatch, renewal before/after expiry, process-loss recovery, terminal/cancelled rejection, and atomic rollback/write visibility.
- Verify command.sql and event.sql columns/projections against the carriers and claim predicates.
- Run the relevant core, Command JPA, Event JPA, Command JPA starter, and Event JPA starter test tasks.
- Report any untested database-specific behavior (especially MySQL partition DDL and PostgreSQL syntax) explicitly in Verify evidence.

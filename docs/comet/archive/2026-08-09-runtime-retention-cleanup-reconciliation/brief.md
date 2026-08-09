# Outcome

Reconcile reliable retention cleanup with the Manual Redrive contract merged in PR #174 so old records are removed only when Runtime can prove they are no longer redrive-eligible.

# Scope

- Rebase PR #173 onto `origin/master` at `214d22fa` and preserve both retention and redrive persistence contracts.
- Reconcile Command and Event carriers, repositories, execution substrates, generated SQL, and focused JPA tests.
- Keep cleanup bounded, service-scoped, lease-safe, state-specific, and guarded by a final conditional delete.
- Replace the canonical `runtime-retention-cleanup` target specification through this new reconciliation change.

# Non-goals

- No automatic cleanup scheduler, generic task framework, payload archive, or result polling.
- No compatibility layer for the pre-redrive retention behavior.
- No changes to the archived `2026-08-09-runtime-retention-cleanup` change.
- No extension of `expireAt`, retry-policy mutation, or alternative redrive path.

# Acceptance examples

- An old `EXECUTED`/`DELIVERED` record past the successful cutoff is cleanup-eligible.
- An old `EXPIRED` record past the expired cutoff is cleanup-eligible.
- An old `EXHAUSTED` record with `expireAt > now` remains durable and can then be manually redriven.
- An old `EXHAUSTED` record with `expireAt <= now` and a terminal time past the exhausted cutoff is cleanup-eligible.
- A candidate id read before a concurrent redrive is not deleted because the final delete repeats state, service, lease, cutoff, and exhausted-expiry predicates.
- `EXCEPTION`, `CANCEL`, `INIT`, `EXECUTING`, and `DELIVERING` are never deleted by cleanup.
- Command and Event entity/SQL contracts contain both `redrive_request_token` and `terminalized_at`.

# Constraints and invariants

- Manual Redrive remains a versioned, state-bound, service-bound CAS that rejects expired records and active leases.
- A successful redrive preserves the retry snapshot and original `expireAt`, clears delivery ownership and `terminalizedAt`, resets attempts, and stores the request token.
- Candidate selection and final deletion use the same eligibility contract; cleanup never wins over a concurrent redrive or state transition.
- Cleanup deletes no more than the requested positive batch limit and returns only aggregate counts.
- `terminalizedAt` records Runtime-owned terminalization time; it is not inferred from creation or expiry time.

# Decisions

- Successful states use the successful retention cutoff.
- `EXPIRED` uses the expired retention cutoff.
- `EXHAUSTED` uses the exhausted retention cutoff and additionally requires `expireAt <= now`.
- `EXCEPTION` and `CANCEL` remain outside cleanup ownership even when old.
- The repository query and final delete both repeat the EXHAUSTED `expireAt <= now` guard.
- The user's explicit repair contract is the confirmed shared understanding for this reconciliation change.

# Open questions

None.

# Verification expectations

- Run focused Command and Event retention cleanup integration tests.
- Run Command and Event atomic claim/manual redrive integration tests.
- Verify entity/JPA/generated SQL parity for `redrive_request_token` and `terminalized_at`.
- Preserve existing bounded, idempotent, lease, service-isolation, CAS, version, and `expireAt` tests.
- Run `git diff --check`, `comet native check`, and the repository-required CI-equivalent Gradle checks.

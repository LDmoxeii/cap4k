# Runtime Retention Cleanup

## Outcome

Reliable Command and Event retention cleanup removes only old durable terminal records that Runtime owns and can prove are no longer eligible for Manual Redrive. Cleanup remains a private, explicitly invoked JPA operation rather than a public scheduler or generic task framework.

## Persistence contract

- Command and Event carriers persist `terminalizedAt` as `terminalized_at datetime(3)` and the Manual Redrive idempotency token as `redrive_request_token varchar(128)`.
- Generated `command.sql` and `event.sql` contain both columns together with the existing delivery token, lease, version, retry snapshot, failure facts, and `expireAt` fields.
- Success acknowledgement, expiry terminalization, exhausted terminalization, and non-retryable failure transitions persist `terminalizedAt` inside the same fenced update that changes state.
- Retryable `EXCEPTION` transitions do not acquire a terminal timestamp.
- A successful Manual Redrive clears `terminalizedAt`, clears delivery ownership, resets attempts, preserves the immutable retry-policy snapshot and original `expireAt`, and persists `redrive_request_token` in the same versioned CAS update.

## Cleanup API contract

- Cleanup accepts an explicit service identity, current time, and `ReliableRetentionPolicy` with separate successful, exhausted, and expired durations plus a positive batch limit.
- Cleanup selects only record ids and returns only safe aggregate `examined` and `deleted` counts.
- Cleanup is bounded by the requested batch limit and idempotent across repeated calls.
- Cleanup performs a bounded candidate-id query followed by one conditional delete.
- Candidate selection and final deletion repeat the same service, state, null-lease, terminal cutoff, and EXHAUSTED expiry predicates.
- Cleanup never acknowledges, retries, redrives, extends expiry, changes state, or exposes payload/failure content.

## State eligibility

| Record | State | Additional eligibility |
| --- | --- | --- |
| Command | `EXECUTED` | `terminalizedAt <= successfulCutoff` |
| Event | `DELIVERED` | `terminalizedAt <= successfulCutoff` |
| Command/Event | `EXPIRED` | `terminalizedAt <= expiredCutoff` |
| Command/Event | `EXHAUSTED` | `expireAt <= now` and `terminalizedAt <= exhaustedCutoff` |

The following states are never deleted by cleanup: `EXCEPTION`, `CANCEL`, `INIT`, `EXECUTING`, and `DELIVERING`.

## Manual Redrive composition

- Manual Redrive remains allowed only for `EXCEPTION` and `EXHAUSTED` records with `expireAt > now`, matching service and expected state/version, and no active lease.
- Because cleanup requires `expireAt <= now` for `EXHAUSTED`, a redrive-eligible exhausted record is never a valid cleanup candidate.
- If cleanup holds a stale candidate id and the record is concurrently changed and successfully redriven, the final delete observes the current state/lease/cutoff/expiry values and affects zero rows.
- Manual Redrive CAS, lease, version, retry snapshot, failure facts, and original `expireAt` semantics remain authoritative; cleanup introduces no bypass.

## Scenarios

### Successful record past cutoff

- **WHEN** an `EXECUTED` Command or `DELIVERED` Event has a terminalization timestamp older than the successful retention cutoff
- **THEN** one cleanup call may delete it subject to matching service, null lease, final recheck, and batch bounds

### Expired record past cutoff

- **WHEN** an `EXPIRED` record has a terminalization timestamp older than the expired cutoff
- **THEN** cleanup may delete it under the same service, lease, final-recheck, and batch guards

### Redrive-eligible exhausted record

- **WHEN** an old `EXHAUSTED` record has `expireAt > now`
- **THEN** candidate selection excludes it, cleanup reports no deletion for it, and Manual Redrive can still transition it through the existing state/version/lease CAS

### Exhausted record past lifetime and cutoff

- **WHEN** an `EXHAUSTED` record has both `expireAt <= now` and `terminalizedAt <= exhaustedCutoff`
- **THEN** cleanup may delete it because Manual Redrive can no longer accept it

### Concurrent redrive after candidate read

- **WHEN** cleanup has read a candidate id and a state transition plus Manual Redrive wins before the delete
- **THEN** the final delete affects zero rows and the redriven `INIT` record remains durable

### Ineligible states

- **WHEN** an `EXCEPTION`, `CANCEL`, `INIT`, `EXECUTING`, or `DELIVERING` record is older than every retention duration
- **THEN** cleanup still leaves it durable

### Live ownership or service mismatch

- **WHEN** a record has a non-null lease or belongs to another service
- **THEN** both candidate selection and final deletion exclude it

### Bounded repeated cleanup

- **WHEN** more eligible records exist than the batch limit and cleanup is invoked repeatedly
- **THEN** each call deletes no more than the limit and already deleted records produce no further effect

## Non-goals

- No automatic cleanup scheduler or retention daemon.
- No generic Job, Task, Saga, Locker, or result-repository abstraction.
- No deletion of retryable failures or cancellation history.
- No payload archive, raw exception persistence, result polling, transport behavior, or broker-specific contract.
- No compatibility bridge for the previous cleanup eligibility rule.

## Verification

Focused real-JPA verification MUST cover Command and Event for:

- `redrive_request_token` and `terminalized_at` entity/JPA/generated SQL alignment;
- terminal timestamp population on success, expiry, and exhaustion;
- future-expiry exhausted retention followed by successful Manual Redrive;
- past-expiry exhausted deletion after the exhausted cutoff;
- stale candidate followed by a winning redrive and a zero-row final delete;
- successful and expired cutoffs;
- `EXCEPTION`, `CANCEL`, `INIT`, owned-state, live-lease, and other-service exclusion;
- bounded batches and repeated cleanup idempotency;
- preservation of Manual Redrive CAS, version, lease, retry snapshot, failure facts, and `expireAt` behavior.
